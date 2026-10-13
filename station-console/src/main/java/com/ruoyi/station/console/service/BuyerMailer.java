package com.ruoyi.station.console.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteOrder;
import com.ruoyi.station.console.domain.SiteOrderItem;

/**
 * 发给<b>买家</b>的三封信：邮箱验证、重置密码、<b>下单确认</b>。
 *
 * <h3>⚠ 这三封分两类，处理方式完全不同</h3>
 * <table border="1">
 *   <tr><th></th><th>验证 / 重置</th><th>下单确认</th></tr>
 *   <tr><td>是什么</td><td><b>流程本身</b>：收不到就注册不完、密码找不回</td>
 *       <td><b>通知</b>：收不到也不影响那张单，它已经落库了</td></tr>
 *   <tr><td>怎么发</td><td>同步，调用方等结果</td>
 *       <td>事务提交之后扔进线程池，<b>异常自己吞掉</b></td></tr>
 *   <tr><td>没配 SMTP</td><td>整件事直接报错</td><td>静默跳过</td></tr>
 * </table>
 * <p>把下单确认做成同步的话，SMTP 超时会让买家那个提交请求一直转圈，
 * 而他的单其实早就下好了；抛异常的话更糟——{@code submit} 在事务里，
 * 一封信发不出去会把<b>整张订单回滚掉</b>。</p>
 *
 * <h3>⚠ 和 {@link InquiryNotifier} 的区别：收件人是谁</h3>
 * <p>那个类发给<b>站点自己人</b>（{@code u_s_siteinfo.notifyemail}），这个类发给
 * <b>买家本人</b>（他刚填的那个邮箱）。一张单落库会同时触发两边各一封：
 * 商家收到「有新订单」，买家收到「我们收到了」。</p>
 *
 * <h3>为什么验证信和重置信必须同步发、发不出去要报错</h3>
 * <p>它们<b>是流程本身，不是通知</b>：验证信收不到就注册不完（没验证的账号不许登录），
 * 重置信收不到就找不回密码。这时候「静默跳过 + 返回成功」是最坏的结果——
 * 买家会一直等一封永远不会到的信，而日志里只有一行 debug。</p>
 *
 * <p>所以：SMTP 没配 → 注册和找回密码<b>直接报错</b>，一句人话告诉买家联系站长；
 * 启动时也在日志里喊一嗓子（{@link #reportConfiguration}），装机的人一眼能看到。</p>
 *
 * <p>同步发的代价是买家点「注册」要多等一次 SMTP 往返（几百毫秒到两三秒）。
 * 那是值得的：他需要知道信到底发出去了没有。异步的话，界面只能说
 * 「我们大概发了一封信」。</p>
 *
 * <h3>那个线程池是干什么的</h3>
 * <p>只给<b>非关键</b>的买家提醒用。2026-09-10 起它有第一个用户了：下单确认信
 * （{@link #sendOrderReceiptAfterCommit}）。两封关键信不走它，理由见上表。</p>
 */
@Component
public class BuyerMailer
{
    private static final Logger log = LoggerFactory.getLogger(BuyerMailer.class);

    private static final String NL = "\r\n";

    /** 发信次数计数（按邮箱）。⚠ 三封信共用这一份额度，见 {@link #withinDailyQuota} */
    private static final String MAIL_COUNT_KEY = "station:buyer:mail:";

    /** 没配邮件时为 null（Spring Boot 不创建这个 Bean），见类注释 */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    /**
     * 发件人。和 {@link InquiryNotifier} 读的是同两项配置。
     *
     * <p>⚠ 回退必须在代码里做，不能写成 {@code ${station.notify-from:${spring.mail.username:}}}——
     * 占位符的默认值只在属性<b>缺失</b>时生效，而 {@code station.notify-from} 在
     * application.yml 里写的是 {@code ${MAIL_USER:}}，环境变量没设时它是<b>空串</b>，
     * 属性存在，默认值那一段永远轮不到。这个坑 ERP 那边踩过一次。</p>
     */
    @Value("${station.notify-from:}")
    private String notifyFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable r)
        {
            Thread thread = new Thread(r, "buyer-mailer");
            thread.setDaemon(true);
            return thread;
        }
    });

    /**
     * 启动时说清楚买家登录能不能用。
     *
     * <p>没有 SMTP 的部署里，「注册」这个按钮是点了必报错的——那不是一个能靠界面
     * 看出来的状态，所以必须在启动日志里说。</p>
     */
    @PostConstruct
    public void reportConfiguration()
    {
        if (mailSender == null)
        {
            log.warn("前台买家登录：**不可用**。没配 SMTP（spring.mail.host），"
                    + "注册和找回密码会直接报错——那两步都要发信。"
                    + "只想让买家匿名下单的话，这是正常状态，不用管");
            return;
        }
        if (sender() == null)
        {
            log.error("前台买家登录：配了 SMTP 但没有发件人，注册和找回密码都会失败。"
                    + "配 station.notify-from 或 spring.mail.username");
            return;
        }
        log.info("前台买家登录：可用，验证信和重置信的发件人 {}", sender());
    }

    @PreDestroy
    public void shutdown()
    {
        executor.shutdown();
    }

    /** 邮件功能到底能不能用。注册/找回密码之前先问它，问不到就别写库 */
    public boolean available()
    {
        return mailSender != null && sender() != null;
    }

    /**
     * 验证邮箱那封。
     *
     * @param link 完整的验证链接，由调用方拼（要带上站点域名，见 {@code BuyerAuthService.link}）
     */
    public void sendVerify(SiteInfo site, String to, String name, String link, int validHours, String lang)
    {
        String siteName = siteName(site);
        String l = BuyerMailCopy.resolve(site, lang);
        StringBuilder text = new StringBuilder();
        text.append(greeting(l, name)).append(NL).append(NL);
        text.append(BuyerMailCopy.t(l, "verifyIntro", "site", siteName)).append(NL).append(NL);
        text.append(link).append(NL).append(NL);
        text.append(BuyerMailCopy.t(l, "verifyExpires", "n", String.valueOf(validHours))).append(NL);
        // ⚠ 这一句不能省：收到这封信的人可能压根没注册过（有人拿他的邮箱注册）。
        //   告诉他「不用管」，比让他以为账号被盗要好
        text.append(BuyerMailCopy.t(l, "verifyIgnore")).append(NL);
        text.append(NL).append(BuyerMailCopy.t(l, "signoff", "site", siteName)).append(NL);

        send(to, BuyerMailCopy.t(l, "verifySubject", "site", siteName), text.toString(), "邮箱验证");
    }

    /**
     * 重置密码那封。
     *
     * <p>⚠ 这封信也会发给「邮箱在库里但从没注册过」的人——因为找回密码接口
     * 不告诉调用方邮箱存不存在（防枚举）。所以正文要能同时读通两种情况。</p>
     */
    public void sendReset(SiteInfo site, String to, String name, String link, int validHours, String lang)
    {
        String siteName = siteName(site);
        String l = BuyerMailCopy.resolve(site, lang);
        StringBuilder text = new StringBuilder();
        text.append(greeting(l, name)).append(NL).append(NL);
        text.append(BuyerMailCopy.t(l, "resetIntro", "site", siteName)).append(NL).append(NL);
        text.append(link).append(NL).append(NL);
        text.append(BuyerMailCopy.t(l, "resetExpires", "n", String.valueOf(validHours))).append(NL);
        text.append(BuyerMailCopy.t(l, "resetIgnore")).append(NL);
        text.append(NL).append(BuyerMailCopy.t(l, "signoff", "site", siteName)).append(NL);

        send(to, BuyerMailCopy.t(l, "resetSubject", "site", siteName), text.toString(), "重置密码");
    }

    /**
     * 下单确认信。<b>事务提交之后异步发，发不出去只写日志。</b>
     *
     * <p>这封信是<b>游客下单唯一的跨设备凭证</b>。站上不要求登录就能下单
     * （{@code SiteOrderService#submit}），代价是那份回执只存在他当时那台浏览器的
     * localStorage 里——换台电脑、清一次浏览器数据，那张单他就再也找不到了。
     * 信里有单号，配上 {@code POST /openapi/site/order/lookup}（凭单号 + 下单邮箱查）
     * 才算把这条路补完整。</p>
     *
     * <h3>⚠ 三件事一件都不能少</h3>
     * <ol>
     *   <li><b>挂在 {@code afterCommit} 上</b>：在事务里发的话，SMTP 一超时就把
     *       整张订单回滚掉——买家看到「提交失败」，而商家那边其实收到过一次又没了；</li>
     *   <li><b>扔进线程池</b>：同步发会让买家那个提交请求多等一次 SMTP 往返，
     *       而他的单早就下好了，没有任何理由让他等；</li>
     *   <li><b>异常全吞</b>：这个方法跑在线程池里，漏出去的异常就是一条没人看的堆栈。</li>
     * </ol>
     *
     * <p>⚠ 没配 SMTP 时<b>静默跳过</b>，不报错。这一点和验证信/重置信正好相反——
     * 那两封是流程本身，这一封只是通知，为它挡下单是本末倒置。</p>
     *
     * @param site  站点。用它取站名，以及拼「查看这张单」的链接（域名没登记就不放链接）
     * @param order 已经落好的订单。语言取 {@code order.getLang()}（下单时前台传的，
     *              {@code SiteOrderService#submit} 已经收敛成站点开了的某一种）
     * @param items 信里那份明细。<b>不是</b> {@code order.getItems()}：库里的快照按站点
     *              默认语言存，这里传的是 {@code SiteOrderService#localizedItems}
     *              按买家语言换过名字的那份（2026-09-22 起）
     */
    public void sendOrderReceiptAfterCommit(SiteInfo site, SiteOrder order, List<SiteOrderItem> items)
    {
        String to = trimToNull(order == null ? null : order.getEmail());
        if (to == null)
        {
            // 没填邮箱的单也是能下的（后端只要求联系人和国家），那就没人可发
            log.debug("订单 {} 没有邮箱，不发确认信", order == null ? "?" : order.getOrderCode());
            return;
        }
        if (!available())
        {
            // 没配 SMTP 是一种正常部署状态（类注释里说过），debug 就够了
            log.debug("没配 SMTP，跳过订单 {} 的确认信", order.getOrderCode());
            return;
        }
        String lang = BuyerMailCopy.resolve(site, order.getLang());
        String subject = BuyerMailCopy.t(lang, "orderSubject",
                "ref", nvl(order.getOrderCode()), "site", siteName(site));
        String body = orderReceiptBody(site, order, items, lang);

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            // 没有事务的话（被别处直接调用）没什么可等的，直接排队
            queueReceipt(order.getOrderCode(), to, subject, body);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                queueReceipt(order.getOrderCode(), to, subject, body);
            }
        });
    }

    /**
     * 扣一次发信配额，然后排进线程池。
     *
     * <p>⚠ 配额<b>在这里</b>扣，不在 {@link #sendOrderReceiptAfterCommit} 里扣。那边是在
     * 事务提交<b>之前</b>：那次提交要是回滚了（库里报错、限流在更外层拦下），
     * 单根本没落库，而买家的当日额度已经被扣掉一格了。重试几次之后，
     * 他那张真正成功的单反而收不到确认信——而日志里只有一行「今天收到的邮件已经超额」，
     * 看不出是被几次失败的提交吃掉的。</p>
     *
     * <p>⚠ 这里和里面那一层都不许把异常漏出去：整条路跑在事务回调和线程池上，
     * 漏出去的异常要么影响提交后的收尾，要么变成一条没人看的堆栈。</p>
     */
    private void queueReceipt(String orderCode, String to, String subject, String body)
    {
        if (!withinDailyQuota(to.toLowerCase()))
        {
            log.warn("订单 {} 的确认信没发：{} 今天收到的邮件已经超额", orderCode, mask(to));
            return;
        }
        try
        {
            executor.execute(() -> sendQuietly(to, subject, body, "订单 " + nvl(orderCode) + " 确认"));
        }
        catch (RuntimeException e)
        {
            // 线程池已经关了（正在停服）。单已经落库，到此为止
            log.error("订单 {} 的确认信没能排进线程池：{}", orderCode, e.getMessage());
        }
    }

    /**
     * 这个邮箱今天还能不能再收一封我们发的信。
     *
     * <p>不限的话，任何人都能拿别人的邮箱反复点「忘记密码」、或者反复提交订单，
     * 把那个人的收件箱刷满；而且我们的发件账号会先被 SMTP 服务商限流，
     * 那时候连正常的验证信也发不出去。</p>
     *
     * <h3>⚠ 三封信共用<b>同一份</b>额度</h3>
     * <p>各记各的话，同一个受害者一天能收到 3 倍的信，而「每天最多几封」这个
     * 配置项的字面意思就不成立了。所以计数在这里，不在调用方。</p>
     *
     * <h3>⚠ 超额之后怎么办由调用方决定</h3>
     * <p>这个方法只回真假，不抛异常——两个调用方要做的事是相反的：
     * {@code BuyerAuthService.countMail} 超了要报错（注册这件事没做成），
     * {@link #sendOrderReceiptAfterCommit} 超了只是不发（单已经下好了）。</p>
     *
     * <p>⚠ 计数失败（Redis 抖了）一律<b>放行</b>：丢一封验证信 = 一个买家注册不了，
     * 比多发几封信严重。这和 {@code OrderLookupRateLimiter} 的取舍相反，
     * 那边失败要拒绝——那个限流是访问控制，这个只是防骚扰。</p>
     *
     * @param email <b>规范化之后</b>的邮箱（去空格、小写）。大小写不统一的话，
     *              换个大小写就是另一份额度
     */
    public boolean withinDailyQuota(String email)
    {
        int limit = properties.getBuyer().getMailPerDay();
        if (limit <= 0)
        {
            // 配成 0 或负数 = 不限。只在本地调试时这么配
            return true;
        }
        long slot = System.currentTimeMillis() / 86400000L;
        String key = MAIL_COUNT_KEY + slot + ":" + email;
        try
        {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L)
            {
                // ⚠ 只在第一次设过期，否则持续发信会把过期时间一直往后推、键永不过期
                redis.expire(key, Duration.ofDays(2));
            }
            if (count != null && count > limit)
            {
                log.warn("买家邮件限流：{} 今天已经发了 {} 封，拒绝", mask(email), count - 1);
                return false;
            }
            return true;
        }
        catch (Exception e)
        {
            log.warn("买家邮件限流计数失败，本次放行：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 确认信正文。
     *
     * <p>正文按买家下单时的语言写（2026-09-22 起，{@code u_s_order.lang}），
     * 八份模板在 {@link BuyerMailCopy}。商品名用传进来的 items（已按那种语言换过），
     * 单位走 {@link BuyerMailCopy#unit}。</p>
     *
     * <h3>⚠ 有两句话不能删</h3>
     * <ul>
     *   <li>「本站不收款」：站上的结账页收的是询价单，不是付款。买家收到一封
     *       带金额的「订单确认」，最容易产生的误解就是「我是不是已经买了/该付钱了」；</li>
     *   <li>「金额是按标价估的，运费另算」：同一个理由。这两句在结账页上也各有一句，
     *       信里再说一遍是因为很多人下完单就再也不回那个页面了。</li>
     * </ul>
     */
    private String orderReceiptBody(SiteInfo site, SiteOrder order, List<SiteOrderItem> items, String lang)
    {
        String siteName = siteName(site);
        StringBuilder text = new StringBuilder();

        text.append(greeting(lang, order.getContactName())).append(NL).append(NL);
        text.append(BuyerMailCopy.t(lang, "orderThanks")).append(NL).append(NL);
        text.append(BuyerMailCopy.t(lang, "orderRef", "ref", nvl(order.getOrderCode()))).append(NL).append(NL);

        if (items != null && !items.isEmpty())
        {
            text.append(BuyerMailCopy.t(lang, "orderItems")).append(NL);
            for (SiteOrderItem item : items)
            {
                text.append("  - ").append(nvl(item.getGoodsName()));
                if (trimToNull(item.getComboName()) != null)
                {
                    // 型号要写出来：同一个商品的两个型号在单子上是两行，
                    // 不写明的话买家看到的是两行一模一样的东西
                    text.append(" (").append(item.getComboName()).append(')');
                }
                text.append(" x ").append(plain(item.getQty()));
                String unit = BuyerMailCopy.unit(lang, item.getUnit());
                if (!unit.isEmpty())
                {
                    text.append(' ').append(unit);
                }
                text.append(NL);
            }
            text.append(NL);
        }

        text.append(BuyerMailCopy.t(lang, "orderTotal",
                "currency", nvl(order.getCurrency()), "amount", plain(order.getSubtotal()))).append(NL);
        if (order.getInquireOnly() != null && order.getInquireOnly() > 0)
        {
            text.append(BuyerMailCopy.t(lang, "orderInquire", "n", String.valueOf(order.getInquireOnly())))
                .append(NL);
        }
        text.append(BuyerMailCopy.t(lang, "orderNoPayment")).append(NL).append(NL);

        // ⚠ 域名没登记就没有链接，但信照发：单号本身已经够商家查了。
        //   这里和验证信/重置信相反，那两封没有链接等于没发，见 SiteInfo#publicBase()
        String base = site == null ? null : site.publicBase();
        if (base != null)
        {
            text.append(BuyerMailCopy.t(lang, "orderOpen")).append(NL);
            // 链接带语言前缀：买家点开落在他下单时那种语言的回执页上
            text.append(base)
                .append(BuyerMailCopy.langPath(site, lang, "/order-received/" + nvl(order.getOrderCode())))
                .append(NL);
            text.append(BuyerMailCopy.t(lang, "orderOtherDevice")).append(NL).append(NL);
        }

        text.append(BuyerMailCopy.t(lang, "orderReply")).append(NL);
        text.append(NL).append(BuyerMailCopy.t(lang, "signoff", "site", siteName)).append(NL);
        return text.toString();
    }

    /**
     * 发一封「发不出去也不要紧」的信。异常全吞，只写日志。
     *
     * <p>⚠ 和 {@link #send} 是两个方法，别合并：那个发不出去要抛，
     * 因为调用方拿它决定「这次注册算不算成功」。</p>
     */
    private void sendQuietly(String to, String subject, String body, String what)
    {
        try
        {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(sender());
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            log.info("{}信已发往 {}", what, mask(to));
        }
        catch (Exception e)
        {
            log.error("{}信发往 {} 失败：{}", what, mask(to), e.getMessage(), e);
        }
    }

    private static String siteName(SiteInfo site)
    {
        String name = site == null ? null : trimToNull(site.getSiteName());
        return name == null ? "" : name;
    }

    private static String nvl(String value)
    {
        return value == null ? "" : value;
    }

    /**
     * 金额/数量去掉尾巴上的零：{@code 4260.0000} → {@code 4260}。
     *
     * <p>⚠ 用 {@code toPlainString()} 而不是 {@code toString()}——后者对小数位多的值
     * 会输出科学计数法（{@code 1E+3}），出现在报价里像是出了故障。</p>
     */
    private static String plain(BigDecimal value)
    {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 真正发。<b>同步</b>，失败抛 {@link ServiceException}。
     *
     * <p>抛出去是有意的：调用方要靠它决定「这次注册算不算成功」。
     * 吞掉异常会让买家收到一个成功提示，然后永远等那封信。</p>
     */
    private void send(String to, String subject, String body, String what)
    {
        if (!available())
        {
            // 走到这里说明调用方没先问 available()。也当成错误处理，别让它静默过去
            throw BuyerText.error("mail.unavailable", 503);
        }
        try
        {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(sender());
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            log.info("{}信已发往 {}", what, mask(to));
        }
        catch (Exception e)
        {
            // ⚠ 日志里记具体原因（认证失败？被限流？域名被拒？），但抛给买家的是一句人话：
            //   SMTP 的报错对他没有任何用处，而且那些消息里常常带着我们的发件账号
            log.error("{}信发往 {} 失败：{}", what, mask(to), e.getMessage(), e);
            throw BuyerText.error("mail.failed", 502);
        }
    }

    /** 发件人：优先用显式配的，没有就用 SMTP 账号本身。两个都空返回 null */
    private String sender()
    {
        String explicit = trimToNull(notifyFrom);
        return explicit != null ? explicit : trimToNull(mailUsername);
    }

    /** 抬头按买家的语言；没留名字就用不带名字的那句 */
    private String greeting(String lang, String name)
    {
        String who = trimToNull(name);
        return who == null
                ? BuyerMailCopy.t(lang, "hello")
                : BuyerMailCopy.t(lang, "helloName", "name", who);
    }

    /** 日志里的邮箱打码：{@code zhang***@example.com}。日志会被很多人看到，客户邮箱是客户资产 */
    private static String mask(String email)
    {
        if (email == null)
        {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1)
        {
            return "***";
        }
        int keep = Math.min(3, at);
        return email.substring(0, keep) + "***" + email.substring(at);
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

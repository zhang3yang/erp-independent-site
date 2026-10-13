package com.ruoyi.station.console.service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.station.console.config.ConsoleProperties;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.domain.SiteOrder;
import com.ruoyi.station.console.domain.SiteOrderItem;
import jakarta.annotation.PreDestroy;

/**
 * 询盘通知：来了一条询盘，给站点的通知邮箱发一封。
 *
 * <h3>为什么值得做</h3>
 * <p>询盘是展示型外贸站最重要的产出。在此之前一条询盘进来只做一次 insert，
 * 运营不主动打开管理台就发现不了——外贸询盘隔天回复基本等于丢单。</p>
 *
 * <h3>两条硬约束</h3>
 * <ol>
 *   <li><b>发信绝不能让询盘回滚。</b>{@code submitInquiry} 是 {@code @Transactional} 的，
 *       在事务里直接发信的话，SMTP 超时抛出去就会把已经写好的询盘一起回滚掉。所以挂在
 *       {@code afterCommit} 上：<b>先落库，再发信</b>。丢一封通知可以，丢一条询盘不行。</li>
 *   <li><b>发信不能拖慢访客那个请求。</b>{@code afterCommit} 跑在原线程上，SMTP 握手几百毫秒
 *       起步，连不上时要等到超时。所以再往线程池里扔一次，访客侧立刻返回。</li>
 * </ol>
 *
 * <h3>没配也能跑</h3>
 * <p>没配 {@code spring.mail.host} 时 Spring Boot 不创建 {@link JavaMailSender}，这里注入到
 * null，整条链路静默跳过。站点没填通知邮箱同理。两种情况都只记日志，不抛异常——
 * <b>通知是增值功能，它没配好不该让访客提交不了询盘。</b></p>
 *
 * <h3>⚠ 和 ERP 那版的两处差异</h3>
 * <ol>
 *   <li><b>线程池是自己的。</b>ERP 那版复用 {@code ThreadPoolConfig} 里那个
 *       {@code scheduledExecutorService}——那个 Bean 在 {@code ruoyi-framework} 里，
 *       这个工程不依赖 ERP 的任何一个包。这里自己开一个单线程池：
 *       通知是低频的（一天几十封），一个线程串着发绰绰有余，而且串行发还顺带避免了
 *       同时开几十个 SMTP 连接被服务商限流。</li>
 *   <li><b>收件人从 {@code u_s_siteinfo.notifyemail} 读</b>，不是 {@code u_s_site}——
 *       那张表留在 ERP 当登记表了（docs/独立站与ERP的边界.md §5.2）。</li>
 * </ol>
 */
@Component
public class InquiryNotifier
{
    private static final Logger log = LoggerFactory.getLogger(InquiryNotifier.class);

    private static final String NL = "\r\n";

    /** 没配邮件时为 null，见类注释 */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private ConsoleProperties consoleProperties;

    /**
     * 发件人。留空则回退到 {@code spring.mail.username}，见 {@link #sender()}。
     *
     * <p>⚠ 回退<b>必须在代码里做</b>，不能写成
     * {@code @Value("${station.notify-from:${spring.mail.username:}}")}。
     * 占位符的默认值只在属性<b>缺失</b>时生效，而 application.yml 里这一项写的是
     * {@code ${MAIL_USER:}}——环境变量没设时它是<b>空串</b>，属性存在，
     * 默认值那一段永远轮不到。这个坑 ERP 那边踩过一次。</p>
     */
    @Value("${station.notify-from:}")
    private String notifyFrom;

    /** 回退用的发件人，见上 */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    /**
     * 发信线程池。
     *
     * <p>单线程 + 守护线程：进程要退出时不因为它挂着而等。
     * 队列里没发出去的通知会丢——这是可接受的，询盘本身已经落库了。</p>
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable r)
        {
            Thread thread = new Thread(r, "inquiry-notifier");
            thread.setDaemon(true);
            return thread;
        }
    });

    /**
     * 启动时把配置状态喊一嗓子。
     *
     * <p>这套东西没配好的表现是「询盘来了但没收到邮件」——而询盘本身是正常入库的，
     * 界面上一切正常，光看现象根本分不清是「没配」「配错了」还是「站点没填收件人」。
     * 启动日志里有这一行，运维一眼就知道当前在哪种状态。</p>
     */
    @PostConstruct
    public void reportConfiguration()
    {
        if (mailSender == null)
        {
            // 没配是正常降级，不是错误：不想发通知的部署就该是这个状态
            log.info("询盘通知：未配置 SMTP（spring.mail.host），收到询盘只入库不发信");
            return;
        }
        String sender = sender();
        if (sender == null)
        {
            log.error("询盘通知：配了 SMTP 但没有发件人，一封都发不出去。"
                    + "配 station.notify-from 或 spring.mail.username");
            return;
        }
        log.info("询盘通知：已启用，发件人 {}。收件人在站点档案的「询盘通知邮箱」"
                + "（u_s_siteinfo.notifyemail）上填，留空则不发", sender);
    }

    @PreDestroy
    public void shutdown()
    {
        executor.shutdown();
    }

    /**
     * 登记一封通知，等事务提交后再发。
     *
     * <p>这个方法自己不发信、不抛异常，可以在事务里放心调。</p>
     */
    public void notifyAfterCommit(SiteInfo site, SiteInquiry inquiry)
    {
        notifyAfterCommit(site, "询盘 " + nvl(inquiry.getInquiryCode()),
                subject(site, inquiry), body(site, inquiry), inquiry.getEmail());
    }

    /**
     * 新订单的通知。
     *
     * <p>⚠ <b>这个重载不是可选的锦上添花。</b>结账页原来是把清单当成一条**询盘**提交的，
     * 所以商家一直靠上面那个方法收到通知。改成提交真实订单之后，如果这里不发信，
     * 表现是「买家提交成功、订单在管理台里躺着、而商家完全不知道」——
     * 比改造之前更糟。</p>
     */
    public void notifyAfterCommit(SiteInfo site, SiteOrder order)
    {
        notifyAfterCommit(site, "订单 " + nvl(order.getOrderCode()),
                orderSubject(site, order), orderBody(site, order), order.getEmail());
    }

    /**
     * 通用的通知入口。三件事的顺序和取舍见类注释，两个重载共用这一份：
     * 先判有没有收件人和 SMTP，再挂到事务提交之后，最后扔进单线程池。
     *
     * @param what    出现在日志里的东西，比如「询盘 XP…」。只用于日志
     * @param replyTo 客户的邮箱，放进 replyTo。运营点「回复」就直接回给客户
     */
    private void notifyAfterCommit(SiteInfo site, String what,
            String subject, String body, String replyTo)
    {
        List<String> to = recipients(site);
        if (to.isEmpty())
        {
            // 没填通知邮箱是正常状态：有的站就是不想收。debug 级别，别刷 info 日志
            log.debug("没配通知邮箱，跳过 {} 的通知", what);
            return;
        }
        if (mailSender == null)
        {
            log.warn("配了通知邮箱，但系统没配 SMTP（spring.mail.host），{} 的通知发不出去。"
                    + "数据本身已经存好了，在管理台里能看到", what);
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            // 没有事务的话（比如被别处直接调用），没什么可等的，直接排队发
            submit(what, subject, body, replyTo, to);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                submit(what, subject, body, replyTo, to);
            }
        });
    }

    // ---- 内部 ----

    private void submit(String what, String subject, String body, String replyTo, List<String> to)
    {
        try
        {
            executor.execute(() -> send(what, subject, body, replyTo, to));
        }
        catch (RuntimeException e)
        {
            // 线程池已经关了。数据已经落库，这里到此为止
            log.error("{} 的通知没能排进线程池：{}", what, e.getMessage());
        }
    }

    private void send(String what, String subject, String body, String replyTo, List<String> to)
    {
        try
        {
            String sender = sender();
            if (sender == null)
            {
                log.warn("通知没有发件人（station.notify-from 和 spring.mail.username "
                        + "都是空的），{} 没发", what);
                return;
            }

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(sender);
            mail.setTo(to.toArray(new String[0]));
            // 客户邮箱放 replyTo：运营在邮件客户端里点「回复」就直接回给客户，不用再复制粘贴地址。
            // 发件人不能设成客户邮箱——那是伪造发件人，会被判垃圾邮件
            if (trimToNull(replyTo) != null)
            {
                mail.setReplyTo(replyTo);
            }
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            log.info("{} 已通知 {} 个邮箱", what, to.size());
        }
        catch (Exception e)
        {
            // 到这里数据早就落库了，发不出去只是少一封提醒。整个方法跑在线程池里，
            // 异常必须自己吞掉，否则会变成线程池里一条没人看的堆栈
            log.error("{} 通知发送失败：{}", what, e.getMessage(), e);
        }
    }

    /** 发件人：优先用显式配的，没有就用 SMTP 账号本身。两个都空返回 null */
    private String sender()
    {
        String explicit = trimToNull(notifyFrom);
        return explicit != null ? explicit : trimToNull(mailUsername);
    }

    private String orderSubject(SiteInfo site, SiteOrder order)
    {
        StringBuilder text = new StringBuilder("【新订单】").append(site.getSiteName());
        text.append(" - ").append(nvl(order.getOrderCode()));
        String country = trimToNull(order.getCountry());
        if (country != null)
        {
            // 目的国放进主题：运营在手机上扫一眼收件箱就知道这单值不值得马上处理
            text.append(" / ").append(country);
        }
        return text.toString();
    }

    /**
     * 订单通知的正文。
     *
     * <p>把清单逐行列出来——这封信的作用是让运营<b>不打开管理台就能判断要不要马上报价</b>。
     * 只给一个单号和一句「有新订单」的话，他还得登录才知道客户要什么。</p>
     *
     * <p>⚠ 面议行写「面议」而不是 0.00：写 0 会被当成已经报过价了。</p>
     */
    private String orderBody(SiteInfo site, SiteOrder order)
    {
        StringBuilder text = new StringBuilder();
        text.append("站点：").append(site.getSiteName()).append(NL);
        text.append("订单号：").append(nvl(order.getOrderCode())).append(NL);
        text.append(NL);

        text.append("---- 客户 ----").append(NL);
        append(text, "联系人", order.getContactName());
        append(text, "公司", order.getCompany());
        append(text, "邮箱", order.getEmail());
        append(text, "电话", order.getPhone());
        append(text, "客户编码", order.getBuyerCode());
        text.append(NL);

        text.append("---- 目的地 ----").append(NL);
        StringBuilder addr = new StringBuilder();
        for (String part : new String[] { order.getAddrLine1(), order.getAddrLine2(),
                order.getCity(), order.getRegion(), order.getPostal(), order.getCountry() })
        {
            if (trimToNull(part) != null)
            {
                if (addr.length() > 0) addr.append(", ");
                addr.append(part.trim());
            }
        }
        append(text, "地址", addr.toString());
        append(text, "运输倾向", shipLabel(order.getShippingPref()));
        append(text, "付款倾向", payLabel(order.getPaymentPref()));
        text.append(NL);

        text.append("---- 清单 ----").append(NL);
        int no = 1;
        for (SiteOrderItem item : order.getItems())
        {
            text.append("  ").append(no++).append(". ")
                .append(nvl(item.getGoodsCode())).append(' ')
                .append(nvl(item.getGoodsName()));
            // ⚠ 型号要写出来：单价是按型号取的（repriceLines），不写的话商家看到的是
            //   一个基础款的名字配着另一个价钱，得自己去管理台点开订单才知道客户选的是哪款。
            //   2026-09-11 之前这一行不存在，原因是型号压根没落库（SiteOrderItemMapper
            //   的 insertBatch 漏了 combocode/comboname 两列），印不出来。
            if (trimToNull(item.getComboName()) != null)
            {
                text.append("（").append(item.getComboName()).append("）");
            }
            text.append(" x ").append(plain(item.getQty()));
            if (trimToNull(item.getUnit()) != null)
            {
                text.append(' ').append(item.getUnit());
            }
            if (item.isInquireOnly())
            {
                text.append("  （面议）");
            }
            else
            {
                text.append("  @ ").append(plain(item.getPrice()))
                    .append(" = ").append(nvl(item.getCurrency())).append(' ')
                    .append(plain(item.getLineTotal()));
            }
            text.append(NL);
        }
        text.append(NL);
        text.append("小计（仅已标价的行）：").append(nvl(order.getCurrency())).append(' ')
            .append(plain(order.getSubtotal())).append(NL);
        if (order.getInquireOnly() != null && order.getInquireOnly() > 0)
        {
            text.append("另有 ").append(order.getInquireOnly()).append(" 项价格面议，未计入").append(NL);
        }
        text.append("⚠ 不含运费和税费。实际报价由你回给客户的报价单决定。").append(NL);

        // 从引导式下单来的单：客户逐项选了什么（订单行只有商品和数量，logo、包装、认证这些都在这里）
        List<Map<String, String>> wizard = OrderWizardService.snapshotRows(order.getWizardJson(), true);
        if (!wizard.isEmpty())
        {
            text.append(NL).append("---- 引导下单配置 ----").append(NL);
            String lastGroup = null;
            for (Map<String, String> row : wizard)
            {
                if (!row.get("group").equals(lastGroup))
                {
                    lastGroup = row.get("group");
                    text.append("【").append(lastGroup).append("】").append(NL);
                }
                text.append("  ").append(row.get("field")).append("：").append(row.get("value")).append(NL);
            }
        }

        if (trimToNull(order.getBuyerNote()) != null)
        {
            text.append(NL).append("---- 买家说明 ----").append(NL);
            text.append(order.getBuyerNote()).append(NL);
        }

        String consoleUrl = consoleProperties.consoleHomeUrl();
        if (trimToNull(consoleUrl) != null)
        {
            text.append(NL).append("去处理：").append(consoleUrl).append("/orders").append(NL);
        }
        return text.toString();
    }

    /** decimal(18,4) 直接 toString 是 `600.0000`，邮件里看着像出错了 */
    private String plain(java.math.BigDecimal value)
    {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * ⚠ 这两组 id 的定义在三个地方，加一种要同步改：
     * 前台 {@code station-site/app/lib/shop.ts} 的 SHIPPING_PREFS / PAYMENT_PREFS（买家看的多语文案）、
     * 管理台 {@code Orders.vue} 的 shipLabel / payLabel（运营看的中文）、以及这里（邮件里的中文）。
     */
    private String shipLabel(String pref)
    {
        if (pref == null) return null;
        return switch (pref)
        {
            case "sea" -> "海运";
            case "air" -> "空运";
            case "express" -> "国际特快";
            case "agent" -> "买家自己的货代";
            default -> pref;
        };
    }

    private String payLabel(String pref)
    {
        if (pref == null) return null;
        return switch (pref)
        {
            case "tt" -> "电汇 T/T";
            case "lc" -> "信用证 L/C";
            case "paypal" -> "PayPal";
            case "other" -> "待商议";
            default -> pref;
        };
    }

    private String subject(SiteInfo site, SiteInquiry inquiry)
    {
        StringBuilder text = new StringBuilder("【新询盘】").append(site.getSiteName());
        String product = trimToNull(inquiry.getProductTitle());
        if (product != null)
        {
            text.append(" - ").append(product);
        }
        return text.toString();
    }

    private String body(SiteInfo site, SiteInquiry inquiry)
    {
        StringBuilder text = new StringBuilder();
        text.append("站点：").append(site.getSiteName()).append(NL);
        text.append("单号：").append(nvl(inquiry.getInquiryCode())).append(NL);
        text.append(NL);
        text.append("姓名：").append(nvl(inquiry.getName())).append(NL);
        text.append("邮箱：").append(nvl(inquiry.getEmail())).append(NL);
        append(text, "公司：", inquiry.getCompany());
        append(text, "电话：", inquiry.getPhone());
        append(text, "商品：", inquiry.getProductTitle());
        append(text, "商品编码：", inquiry.getGoodsCode());
        append(text, "来源页面：", inquiry.getPageUrl());
        text.append(NL);
        text.append("留言：").append(NL);
        text.append(nvl(inquiry.getMessage())).append(NL);

        // ⚠ 这里放的是**本站管理台**的深链，但它只对「已经登进去的人」有用。
        //
        // 管理台没有登录页（见 SessionController）：没有会话的人点这个链接，
        // 落到的是 /login 那个空页面——一个白屏，看不出该干什么。
        // 所以后面必须跟一句「怎么进」。
        //
        // 收件人一定有 ERP 账号：从 ERP 点「进入管理」是唯一的入口，没有 ERP 账号的人
        // 本来就进不了这个后台。（这句话在 §6a 有登录页的那一版里不成立，
        // 那时候这里的注释写的是「放 ERP 地址才是错的，收件人可能根本没有 ERP 账号」。）
        String home = consoleProperties.consoleHomeUrl();
        if (!home.isEmpty())
        {
            text.append(NL);
            text.append("去处理：").append(home).append("/inquiry").append(NL);
            String erp = consoleProperties.erpHomeUrl();
            text.append("（打开是空白页说明还没登录：先去众联 ERP")
                .append(erp.isEmpty() ? "" : "（" + erp + "）")
                .append("的「独立站管理」点「进入管理」，再点上面这个链接）").append(NL);
        }
        text.append(NL);
        text.append("——本邮件由系统自动发送。直接回复即可回到客户邮箱。").append(NL);
        return text.toString();
    }

    /** 逗号或分号分隔的收件人，去空去重，顺手挡掉明显不是邮箱的串 */
    private List<String> recipients(SiteInfo site)
    {
        List<String> result = new ArrayList<>();
        String raw = trimToNull(site.getNotifyEmail());
        if (raw == null)
        {
            return result;
        }
        for (String item : raw.split("[,;]"))
        {
            String mail = trimToNull(item);
            if (mail == null)
            {
                continue;
            }
            mail = mail.toLowerCase(Locale.ROOT);
            if (!mail.matches("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$"))
            {
                log.warn("通知邮箱里有一项不像邮箱，已跳过：{}", mail);
                continue;
            }
            if (!result.contains(mail))
            {
                result.add(mail);
            }
        }
        return result;
    }

    private void append(StringBuilder text, String label, String value)
    {
        String item = trimToNull(value);
        if (item != null)
        {
            text.append(label).append(item).append(NL);
        }
    }

    private static String nvl(String value)
    {
        return value == null ? "" : value;
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

package com.ruoyi.station.console.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteBuyer;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteBuyerMapper;

/**
 * 前台买家的注册 / 登录 / 邮箱验证 / 找回密码。
 *
 * <h3>⚠ 这是<b>买家</b>的登录，和管理台没有任何关系</h3>
 * <p>管理台没有登录页，唯一入口是从 ERP 点「进入管理」免密进来
 * （{@code SessionController}、docs/独立站与ERP的边界.md §6d）。<b>那一条不受这个类影响</b>：
 * 这里动的是 {@code u_s_buyer}（客户），那边是 {@code u_s_user}（自己人），两张表两拨人。</p>
 *
 * <p>买家为什么必须有本站密码：让客户为了看自己的订单去登一下 ERP 是荒谬的——
 * 他压根不该有 ERP 账号。</p>
 *
 * <h3>账号是「认领」出来的，不是「创建」出来的</h3>
 * <p>{@code u_s_buyer} 里绝大多数行是发询盘/下单时长出来的，没有密码。
 * 注册做的事是给<b>已有的那一行</b>设上密码：</p>
 * <pre>
 * 注册 email=a@b.com
 *   ├─ 库里没有这一行  → 建一行（{@code SiteBuyerService.resolveByEmail}）+ 设密码 + 发验证信
 *   ├─ 有行、没密码    → **认领**：设密码 + 发验证信。他之前的询盘和订单立刻可见
 *   └─ 有行、有密码    → 见下面「不告诉调用方邮箱存不存在」
 * </pre>
 *
 * <h3>三条安全上的硬规矩，改这个类之前先读</h3>
 * <ol>
 *   <li><b>不告诉调用方一个邮箱有没有注册过。</b>注册、找回密码两个接口，
 *       无论邮箱是什么状态都回同一句话。否则这两个公开接口就是一个
 *       「查这个邮箱是不是这家的客户」的探测器——对做外贸的站来说，
 *       客户名单本身就是要保护的东西。</li>
 *   <li><b>登录失败一律同一句话、同样的耗时。</b>「邮箱不存在」和「密码不对」分开说，
 *       等于把上面那条从注册接口挪到登录接口。耗时也要拉平，见 {@link #login}。</li>
 *   <li><b>没验证邮箱不许登录。</b>不验证的话，任何人都能拿别人的邮箱注册，
 *       而那个邮箱的主人从此注册不了（唯一键撞上），并且他之前发的询盘会落进
 *       一个别人能登进去的账号里。</li>
 * </ol>
 *
 * <h3>令牌为什么在 Redis 而不在表里</h3>
 * <p>验证链接 24 小时、重置链接 1 小时，都是短命的东西。存表就要自己写清理任务，
 * 而忘了清理的表会一直长；Redis 的 TTL 是免费的。一次性消费是「取出并删掉」的语义，
 * 和跳转令牌防重放（{@code JumpTokenVerifier.consumeOnce}）是同一套做法，
 * 但<b>实现用的是 Lua 而不是 {@code GETDEL}</b>，理由见 {@link #CONSUME_ONCE}。
 * 代价是 Redis 被清空时未点击的链接失效——重新发一封就行。</p>
 */
@Service
public class BuyerAuthService
{
    private static final Logger log = LoggerFactory.getLogger(BuyerAuthService.class);

    /** 验证邮箱的令牌 */
    private static final String VERIFY_KEY = "station:buyer:verify:";
    /** 重置密码的令牌 */
    private static final String RESET_KEY = "station:buyer:reset:";
    /** 登录尝试计数（按 IP） */
    private static final String LOGIN_COUNT_KEY = "station:buyer:login:";

    /**
     * 用户不存在时用来比对的假哈希。
     *
     * <p>一个合法的 BCrypt 串，永远匹配不上任何密码。存在的意义是让
     * 「邮箱不存在」和「密码错」两条路径都真的跑一次 BCrypt（约 50~100ms）——
     * 不跑的话，两者的响应时间差得出「这个邮箱是不是你们的客户」。</p>
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /** 令牌 32 字节 = 256 位。它就是凭据本身，按「不可穷举」取长度 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * BCrypt，默认 10 轮（一次验证约 50~100ms）。
     *
     * <p>调高会让登录变慢而收益有限，调低就失去了慢哈希的意义。
     * ⚠ 不用 MD5/SHA1：买家用的多半是他到处都在用的那个密码，
     * 一旦库被拖走，快哈希等于明文。</p>
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private SiteBuyerMapper buyerMapper;

    @Autowired
    private SiteBuyerService buyerService;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private BuyerMailer mailer;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StationProperties properties;

    // ==================== 注册 ====================

    /**
     * 注册（= 认领一个邮箱）。
     *
     * <p><b>返回 void，而且无论邮箱什么状态都不抛异常</b>（除了参数不合法和发信失败）。
     * 调用方对买家只说一句：「验证邮件已发出，请查收」。理由见类注释第 1 条。</p>
     *
     * <p>已经注册过的邮箱走的是「发一封提醒信」那一支——收信人如果就是本人，
     * 他会看到一句「你已经有账号了，直接登录或重置密码」；如果不是本人，
     * 那这封信也没泄露任何东西。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String rawPassword, String name, String company,
            String phone, String clientIp, String lang)
    {
        String mail = normalizeEmail(email);
        requirePassword(rawPassword);
        // ⚠ 先确认邮件能发出去再写库。倒过来的话，SMTP 没配的部署里会留下一堆
        //   设了密码但永远收不到验证信、因此永远登不进来的账号
        requireMailer();

        SiteInfo site = siteInfoService.require();
        SiteBuyer existing = buyerMapper.selectForAuth(mail);

        if (existing != null && existing.isRegistered())
        {
            // 已经有账号了。**不改任何东西**，也不告诉调用方——
            // 否则这个接口就是一个「这个邮箱注册过没有」的查询器
            log.info("买家注册：邮箱已有账号，发提醒信不改密码，IP={}", clientIp);
            countMail(mail);
            mailer.sendReset(site, mail, existing.getName(), resetLink(site, newResetToken(mail), lang),
                    properties.getBuyer().getResetHours(), lang);
            return;
        }

        // 没有这一行就建一个（和发询盘时长出来的走同一个方法，保证客户编码、
        // 计数、活跃时间那些字段的处理只有一份实现）
        //
        // ⚠ country 传 null：注册表单不收国家。resolveByEmail 里那些字段全走
        //   coalesce(新, 旧)，传 null 就是「这一次没填，别把上一次填过的抹掉」
        SiteBuyer buyer = existing != null ? existing
                : buyerService.resolveByEmail(mail, name, company, phone, null);

        countMail(mail);
        buyerMapper.updatePassword(buyer.getBuyerCode(), passwordEncoder.encode(rawPassword), false);
        String token = newVerifyToken(buyer.getBuyerCode());
        mailer.sendVerify(site, mail, name != null ? name : buyer.getName(),
                verifyLink(site, token, lang), properties.getBuyer().getVerifyHours(), lang);

        log.info("买家注册：{} 已设密码，等邮箱验证。IP={}", buyer.getBuyerCode(), clientIp);
    }

    /**
     * 点了验证链接。
     *
     * @return 验证通过的买家；令牌无效或过期抛 {@link ServiceException}
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteBuyer verifyEmail(String token)
    {
        String buyerCode = consumeOnce(VERIFY_KEY, token);
        if (buyerCode == null)
        {
            throw BuyerText.error("auth.verifyLinkInvalid", 400);
        }
        SiteBuyer buyer = buyerMapper.selectByCode(buyerCode);
        if (buyer == null)
        {
            throw BuyerText.error("auth.accountMissing", 400);
        }
        buyerMapper.markVerified(buyerCode);
        log.info("买家 {} 邮箱验证通过", buyerCode);
        return buyer;
    }

    /** 再发一次验证信。和注册一样：不告诉调用方邮箱的状态 */
    public void resendVerify(String email, String lang)
    {
        String mail = normalizeEmail(email);
        requireMailer();
        SiteInfo site = siteInfoService.require();
        SiteBuyer buyer = buyerMapper.selectForAuth(mail);
        // 只对「注册过但还没验证」的发。其余情况静默返回——同上，不泄露状态
        if (buyer != null && buyer.isRegistered() && !buyer.isVerified())
        {
            countMail(mail);
            mailer.sendVerify(site, mail, buyer.getName(),
                    verifyLink(site, newVerifyToken(buyer.getBuyerCode()), lang),
                    properties.getBuyer().getVerifyHours(), lang);
        }
    }

    // ==================== 登录 ====================

    /**
     * 登录。成功返回买家，失败抛 {@link ServiceException}。
     *
     * <p><b>「邮箱没注册」和「密码不对」报同一句话，而且都走一遍 BCrypt</b>——
     * 前者直接返回的话，两条路径的耗时差得出「这个邮箱是不是这家的客户」，
     * 而对外贸站来说客户名单本身就是要保护的（类注释第 2 条）。</p>
     *
     * <p>另外三种情况<b>可以</b>说清楚，因为它们都要求密码先对上，
     * 也就是说话的对象已经证明了自己是账号主人：邮箱没验证、账号被停用。</p>
     */
    public SiteBuyer login(String email, String rawPassword, String clientIp, String lang)
    {
        if (!tryLogin(clientIp))
        {
            throw BuyerText.error("auth.tooManyAttempts", 429);
        }

        String mail = normalizeEmail(email);
        String password = rawPassword == null ? "" : rawPassword;
        SiteBuyer buyer = buyerMapper.selectForAuth(mail);

        String hash = buyer == null || !buyer.isRegistered() ? DUMMY_HASH : buyer.getPasswordHash();
        boolean matched = passwordEncoder.matches(password, hash);

        if (buyer == null || !buyer.isRegistered() || !matched)
        {
            // ⚠ 日志里也别把邮箱原样写进去（客户资产），只记 IP 和一个打过码的邮箱
            log.warn("买家登录失败：邮箱或密码不正确，{} IP={}", maskEmail(mail), clientIp);
            throw BuyerText.error("auth.badCredentials", 401);
        }
        if (buyer.isDisabled())
        {
            throw BuyerText.error("auth.disabled", 403);
        }
        if (!buyer.isVerified())
        {
            // 说清楚，并且顺手再发一封——他多半就是没收到那封信
            resendVerify(mail, lang);
            throw BuyerText.error("auth.unverified", 403);
        }

        buyerMapper.touchLogin(buyer.getBuyerCode(), clientIp);
        log.info("买家登录：{} IP={}", buyer.getBuyerCode(), clientIp);
        return buyer;
    }

    // ==================== 找回密码 ====================

    /**
     * 忘记密码：发一封重置信。
     *
     * <p><b>无论邮箱存不存在、注册过没有，都返回成功</b>（类注释第 1 条）。
     * 只有「确实注册过」的那一支会真的发信。</p>
     */
    public void requestReset(String email, String clientIp, String lang)
    {
        String mail = normalizeEmail(email);
        requireMailer();
        SiteInfo site = siteInfoService.require();
        SiteBuyer buyer = buyerMapper.selectForAuth(mail);

        if (buyer == null || !buyer.isRegistered())
        {
            // 静默。⚠ 别在这里返回或抛任何能区分开的东西
            log.info("买家找回密码：邮箱没有账号，不发信。{} IP={}", maskEmail(mail), clientIp);
            return;
        }
        countMail(mail);
        mailer.sendReset(site, mail, buyer.getName(), resetLink(site, newResetToken(mail), lang),
                properties.getBuyer().getResetHours(), lang);
        log.info("买家找回密码：重置信已发出 {} IP={}", buyer.getBuyerCode(), clientIp);
    }

    /**
     * 用重置令牌设新密码。
     *
     * <p>成功后<b>顺手把邮箱标成已验证</b>：他能收到这封信，就证明这个邮箱是他的。
     * 一个「密码重置成功但因为没验证还是登不进去」的状态没有任何意义。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteBuyer resetPassword(String token, String newPassword)
    {
        requirePassword(newPassword);
        String mail = consumeOnce(RESET_KEY, token);
        if (mail == null)
        {
            throw BuyerText.error("auth.resetLinkInvalid", 400);
        }
        SiteBuyer buyer = buyerMapper.selectForAuth(mail);
        if (buyer == null)
        {
            throw BuyerText.error("auth.accountMissing", 400);
        }
        buyerMapper.updatePassword(buyer.getBuyerCode(),
                passwordEncoder.encode(newPassword), true);
        log.info("买家 {} 重置了密码", buyer.getBuyerCode());
        return buyer;
    }

    /**
     * 改自己的密码。要先验旧密码——会话被盗时，改密码是攻击者最想做的第一件事。
     *
     * <p>调用方负责在改完之后把会话作废（{@code BuyerSession.end}）：
     * 如果这次改密码是因为怀疑密码泄露了，留着旧会话等于让攻击者继续待在里面。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String buyerCode, String oldPassword, String newPassword)
    {
        requirePassword(newPassword);
        SiteBuyer buyer = buyerMapper.selectForAuthByCode(buyerCode);
        if (buyer == null || !buyer.isRegistered()
                || !passwordEncoder.matches(oldPassword == null ? "" : oldPassword,
                        buyer.getPasswordHash()))
        {
            throw BuyerText.error("auth.oldPasswordWrong", 400);
        }
        buyerMapper.updatePassword(buyerCode, passwordEncoder.encode(newPassword), true);
        log.info("买家 {} 改了密码", buyerCode);
    }

    // ==================== 令牌 ====================

    /** 新的验证令牌，值是买家编码 */
    private String newVerifyToken(String buyerCode)
    {
        String token = randomToken();
        redis.opsForValue().set(VERIFY_KEY + token, buyerCode,
                Duration.ofHours(Math.max(1, properties.getBuyer().getVerifyHours())));
        return token;
    }

    /**
     * 新的重置令牌，值是<b>邮箱</b>而不是买家编码。
     *
     * <p>为什么不一样：重置是「忘记密码」那条路，那时候手上只有邮箱；
     * 而且 {@code requestReset} 对「没注册」的邮箱也可能被调到，那时候没有买家编码。</p>
     */
    private String newResetToken(String email)
    {
        String token = randomToken();
        redis.opsForValue().set(RESET_KEY + token, email,
                Duration.ofHours(Math.max(1, properties.getBuyer().getResetHours())));
        return token;
    }

    /**
     * 一次性消费用的脚本：取出来，有值就顺手删掉。
     *
     * <h3>⚠ 为什么不用 {@code opsForValue().getAndDelete()}</h3>
     * <p>那个方法发的是 {@code GETDEL}，而 <b>{@code GETDEL} 是 Redis 6.2 才有的命令</b>。
     * 老一点的服务端（Windows 上那个 microsoftarchive/redis 分支停在 5.0，很多开发机装的就是它）
     * 回的是 {@code ERR unknown command 'getdel'}，被下面的 catch 吞掉变成 null——
     * 于是每一个验证链接、每一个重置链接都报「无效或已过期」，而令牌明明好好地躺在 Redis 里、
     * TTL 还剩二十几个小时。这个故障看起来完全像是令牌没写进去，排查方向会整个跑偏。</p>
     *
     * <p>换成 Lua 之后同样是原子的（Redis 执行脚本时是单线程、整段跑完才处理下一个命令），
     * 而 {@code EVAL} 从 2.6 就有了。<b>不要图省事改回「先 get 再 delete」</b>——
     * 那样两个并发请求会双双拿到同一个令牌，一次性就没了。重置链接被转发过、
     * 或者邮件客户端预取过链接的场景下，这一条是真会发生的。</p>
     */
    private static final RedisScript<String> CONSUME_ONCE = new DefaultRedisScript<>(
            "local v = redis.call('get', KEYS[1]) "
            + "if v then redis.call('del', KEYS[1]) end "
            + "return v", String.class);

    /**
     * 一次性消费一个令牌：取出并立刻删掉。见 {@link #CONSUME_ONCE}。
     *
     * @return 令牌里存的值；令牌不存在（无效/过期/用过了）返回 null
     */
    private String consumeOnce(String prefix, String token)
    {
        String value = token == null ? null : token.trim();
        // 长度上限：令牌是固定长度的，收到一段几 MB 的串直接拒，别拿它去查 Redis
        if (value == null || value.isEmpty() || value.length() > 128)
        {
            return null;
        }
        try
        {
            return redis.execute(CONSUME_ONCE, List.of(prefix + value));
        }
        catch (Exception e)
        {
            // Redis 不可用时一律当成无效。认证上拿不准必须拒绝
            log.warn("消费买家令牌失败：{}", e.getMessage());
            return null;
        }
    }

    private static String randomToken()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ==================== 链接 ====================

    /**
     * 验证链接。
     *
     * <p>⚠ 域名从<b>本站档案</b>（{@code u_s_siteinfo.domain}）来，不从请求头来。
     * 用 {@code Host} 头拼的话，任何人都能发一个 {@code Host: evil.com} 的注册请求，
     * 让我们给受害者发一封链接指向他站的邮件——那叫 Host header injection，
     * 而且这封信是**从我们的域名发出去的**，钓鱼效果拉满。</p>
     *
     * <p>⚠ 拼出来的是 {@code https://<主域>/verify-email?…}，而 {@code /verify-email}
     * 这一页实际挂在 {@code buyer.} 子域上（前台分了两个 host，见
     * {@code deploy/nginx/tongling.conf}）。<b>这是对的，不要改成在这里拼 buyer.</b>：
     * nginx 上有一条把主域的账号页 302 到 {@code buyer.} 的规则，query 一起带过去。
     * 让后端也记一份 {@code buyer.} 的地址，就等于同一件事在两处配置里各存一份，
     * 迟早对不上；而现在这一侧只认主域，分界线挪动时后端一个字都不用改。</p>
     */
    private String verifyLink(SiteInfo site, String token, String lang)
    {
        // 链接带语言前缀（/ja/verify-email），买家点开落在他注册时那种语言的页面上
        return siteBase(site) + BuyerMailCopy.langPath(site, lang, "/verify-email?token=" + token);
    }

    private String resetLink(SiteInfo site, String token, String lang)
    {
        return siteBase(site) + BuyerMailCopy.langPath(site, lang, "/reset-password?token=" + token);
    }

    /**
     * 拼链接用的站点根地址。规范化那一步在 {@link SiteInfo#publicBase()}，这里只管
     * 「拿不到就报错」——<b>这两封信没有链接就等于没发</b>，所以不能静默过去。
     *
     * <p>⚠ 下单确认信那边是反的（拿不到链接照常发信），见 {@link SiteInfo#publicBase()}
     * 的注释。别把这两处的处理方式对齐。</p>
     */
    private String siteBase(SiteInfo site)
    {
        String base = site == null ? null : site.publicBase();
        if (base == null)
        {
            // 没登记域名就拼不出链接，而发一封链接是 http://null/... 的信毫无意义。
            // 这是部署没做完，报清楚让装机的人去填
            throw BuyerText.error("auth.noDomain", 500);
        }
        return base;
    }

    // ==================== 限流 ====================

    /**
     * 这个 IP 还能不能再试一次登录。<b>有副作用</b>：返回 true 的同时计数已经加上去了。
     *
     * <p>⚠ 和 {@link InquiryRateLimiter} 相反：那边 Redis 挂了要<b>放行</b>
     * （丢一条真询盘 = 丢一个客户），这里 Redis 挂了也放行——但理由不同：
     * 挡不住撞库总比让所有真买家都登不进去好，而且密码本身还在挡着。
     * <b>会话读取那一侧是拒绝</b>（{@code BuyerSession.current}），
     * 那里放行才是真的危险。</p>
     */
    private boolean tryLogin(String clientIp)
    {
        int limit = properties.getBuyer().getLoginRatePerMinute();
        if (limit <= 0)
        {
            return true;
        }
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        long slot = System.currentTimeMillis() / 60000;
        String key = LOGIN_COUNT_KEY + slot + ":" + ip;
        try
        {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L)
            {
                // 只在第一次设过期：每次都设会把过期时间一直往后推，键永不过期
                redis.expire(key, Duration.ofMinutes(2));
            }
            return count == null || count <= limit;
        }
        catch (Exception e)
        {
            log.warn("买家登录限流计数失败，本次放行：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 给这个邮箱记一次发信，超了就拒。
     *
     * <p>不限的话，任何人都能拿别人的邮箱反复点「忘记密码」，把那个人的收件箱刷满；
     * 而且我们的发件账号会先被 SMTP 服务商限流，那时候连正常的验证信也发不出去。</p>
     *
     * <p>⚠ 这个方法<b>抛异常</b>而不是返回 false：调用方那几处都是「发不出去就该整件事失败」，
     * 返回布尔值会让某个分支忘了判。</p>
     */
    private void countMail(String email)
    {
        // 计数本身在 BuyerMailer 里（下单确认信也要用同一份额度，不能各记各的），
        // 这里只决定「超了怎么办」：注册和找回密码超了就是这件事做不成
        if (!mailer.withinDailyQuota(email))
        {
            throw BuyerText.error("auth.mailQuota", 429);
        }
    }

    // ==================== 校验 ====================

    /**
     * 邮箱规范化：去空格 + 全小写。
     *
     * <p>⚠ 必须全小写，因为 {@code uk_buyer_email} 是唯一键，而
     * {@code utf8mb4_0900_ai_ci} 排序规则本身不区分大小写——不统一的话，
     * 库里查得到但代码里的字符串比较对不上，表现是各种「时好时坏」。</p>
     */
    private static String normalizeEmail(String email)
    {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        // 不做正则「完整校验」：RFC 5322 的地址语法比大多数人写的正则宽得多，
        // 严格的正则只会把合法地址挡在外面。真正的验证就是那封发过去的信
        if (value.isEmpty() || value.length() > 150 || !value.contains("@")
                || value.startsWith("@") || value.endsWith("@") || value.contains(" "))
        {
            throw BuyerText.error("auth.badEmail", 400);
        }
        return value;
    }

    private void requirePassword(String rawPassword)
    {
        int min = properties.getBuyer().getPasswordMinLength();
        String value = rawPassword == null ? "" : rawPassword;
        if (value.length() < min)
        {
            throw BuyerText.error("auth.passwordShort", 400, min);
        }
        // 上限是防 BCrypt 的已知行为：它只取前 72 字节，更长的部分被静默忽略。
        // 那本身不算漏洞，但「密码后半段随便打都能登进去」会让人以为系统坏了
        if (value.length() > 72)
        {
            throw BuyerText.error("auth.passwordLong", 400);
        }
    }

    /** 邮件功能没配好时，注册和找回密码要当场失败，不能写一半 */
    private void requireMailer()
    {
        if (!mailer.available())
        {
            throw BuyerText.error("auth.noMailer", 503);
        }
    }

    /** 日志里的邮箱打码。日志会被很多人看到，客户邮箱是客户资产 */
    private static String maskEmail(String email)
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
        return email.substring(0, Math.min(3, at)) + "***" + email.substring(at);
    }
}

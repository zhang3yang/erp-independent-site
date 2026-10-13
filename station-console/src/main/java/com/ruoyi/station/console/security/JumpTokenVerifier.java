package com.ruoyi.station.console.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteInfoService;

/**
 * 从 ERP 跳过来的令牌验签。<b>纯本地计算，不回调 ERP。</b>
 *
 * <h3>它替代了什么</h3>
 * <p>旧协议是「一次性不透明票 + 服务端兑换」：URL 上放一串随机数，管理台后端拿着
 * {@code clientSecret} 回调 ERP 那个票据兑换接口，把票换成身份。</p>
 *
 * <p><b>那第③步就是一条后端互通</b>，而且是最硬的一条：它要求管理台能连到 ERP、
 * 要求两边共享一个 {@code clientSecret}、要求 ERP 常年为境外服务器开一个入口。
 * docs/独立站与ERP的边界.md 要点 1 直接否掉它。</p>
 *
 * <h3>新协议</h3>
 * <pre>
 * payload = base64url(json)   sig = base64url(HMAC-SHA256(payload, 本站跳转密钥))
 * token   = payload + "." + sig
 * </pre>
 *
 * <p>ERP 用<b>这个站登记的密钥</b>签，站点用<b>自己那份</b>验。两份是同一个值，
 * 但从不在网络上传输——所以这一步是纯本地计算，ERP 那边不需要在线（§4.2 第③步）。</p>
 *
 * <h3>⚠ 它比旧协议弱在哪（明说）</h3>
 * <p>旧方案里，令牌落到日志上也没用——兑换要 {@code clientSecret}。
 * 新方案里，<b>60 秒内拿到令牌原文的人可以直接用它</b>。
 * 这是「后端不许互通」的代价，不是疏忽（§4.4）。</p>
 *
 * <p>一期靠四条把窗口压到可接受：</p>
 * <ol>
 *   <li>令牌走 <b>POST 表单</b>，正常路径下不出现在任何 URL、日志、{@code Referer} 里；</li>
 *   <li>有效期 60 秒，本侧再夹一道 {@code station.jump.max-age-seconds}；</li>
 *   <li>{@code jti} 一次性，用 Redis 的 {@code SETNX} 语义消费——抢在用户前面用掉会让
 *       <b>真正的用户进不去</b>，这是一个会被立刻发现的失败，不是静默劫持；</li>
 *   <li>令牌只对<b>签它的那一个站</b>有效，密钥一站一份，泄一个不影响别的站。</li>
 * </ol>
 *
 * <p><b>必须守住的两条</b>（§4.4）：{@code /sso} 只接受 POST（在 Controller 里）；
 * 签名用定长比较，不要 {@code equals} 短路（在这里）。</p>
 */
@Component
public class JumpTokenVerifier
{
    private static final Logger log = LoggerFactory.getLogger(JumpTokenVerifier.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String HMAC_ALG = "HmacSHA256";

    /** 只认这个版本。将来改结构时靠它区分，不要靠「字段在不在」猜 */
    private static final int SUPPORTED_VERSION = 1;

    /** jti 在 Redis 里的键前缀 */
    private static final String JTI_KEY = "station:console:jti:";

    /**
     * 令牌长度上限。
     *
     * <p>正常令牌几百字节。收到一段几 MB 的串时，直接拒比先 base64 解码再说便宜得多——
     * 而且它会被当成 Redis 的 key 去查一次。</p>
     */
    private static final int MAX_TOKEN_LENGTH = 4096;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private StationProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 验签并返回令牌里的身份。
     *
     * @return 验通了的身份
     * @throws ServiceException 任何一步不通过。
     *         <b>失败原因刻意不细分</b>——「签名不对」「过期了」「用过了」对调用方来说
     *         处置方式是同一个：回 ERP 重新点一次。分开说只会方便探测。
     */
    public JumpToken verify(String token)
    {
        String value = token == null ? null : token.trim();
        if (value == null || value.isEmpty() || value.length() > MAX_TOKEN_LENGTH)
        {
            throw reject("令牌为空或格式不正确");
        }

        int dot = value.indexOf('.');
        // lastIndexOf 也要查一遍：两个点的串（比如有人拿 JWT 来试）应当直接拒，
        // 而不是把中间那段当 payload 解出来
        if (dot <= 0 || dot != value.lastIndexOf('.') || dot == value.length() - 1)
        {
            throw reject("令牌格式不正确");
        }
        String payloadPart = value.substring(0, dot);
        String signaturePart = value.substring(dot + 1);

        SiteInfo site = siteInfoService.require();
        String secret = resolveSecret(site);

        // ---- ① 验签。在解析 payload **之前**做 ----
        //
        // 顺序不能反：先解析再验签的话，一段没签名的攻击载荷也会被 Jackson 解一遍。
        // 验签是纯字节运算，先做它，解析器只处理已经证明是我们自己签的内容。
        byte[] expected = hmac(payloadPart, secret);
        byte[] actual;
        try
        {
            actual = Base64.getUrlDecoder().decode(signaturePart);
        }
        catch (IllegalArgumentException e)
        {
            throw reject("令牌签名不是合法的 base64url");
        }
        // ⚠ MessageDigest.isEqual 是定长比较，不因为「前几位对上了」而变慢。
        //   换成 Arrays.equals 或者 String.equals 就给了逐字节试出签名的机会
        if (!MessageDigest.isEqual(expected, actual))
        {
            throw reject("令牌签名不匹配");
        }

        // ---- ② 解析 payload ----
        JsonNode payload;
        try
        {
            byte[] raw = Base64.getUrlDecoder().decode(payloadPart);
            payload = JSON.readTree(raw);
        }
        catch (Exception e)
        {
            throw reject("令牌内容无法解析");
        }

        int ver = payload.path("ver").asInt(0);
        if (ver != SUPPORTED_VERSION)
        {
            throw reject("令牌版本不支持：" + ver);
        }

        // ---- ③ 站点必须对得上 ----
        //
        // 这一条是「两个站互不可见」的落点（§13 第 4 条）：拿 A 站的令牌来 POST B 站，
        // 上面的签名就过不了（密钥不同）。这里再确认一次 site 声明——
        // 万一哪天两个站被配了同一个密钥，这一句还能拦住。
        String site1 = payload.path("site").asText(null);
        if (site1 == null || !site1.equals(site.getSiteCode()))
        {
            log.warn("跳转令牌里的站点是 {}，本站是 {}，拒绝", site1, site.getSiteCode());
            throw reject("令牌不是发给本站的");
        }

        // ---- ④ 时效 ----
        long now = System.currentTimeMillis() / 1000;
        long exp = payload.path("exp").asLong(0);
        long iat = payload.path("iat").asLong(0);
        if (exp <= 0 || now > exp)
        {
            throw reject("令牌已过期");
        }
        // 本侧再夹一道：ERP 那边把有效期配错成一天的话，这里仍然只认 120 秒内签发的。
        // iat 为 0（老格式/被裁掉）时跳过这一条，exp 那一关还在
        int maxAge = properties.getJump().getMaxAgeSeconds();
        if (iat > 0 && now - iat > maxAge)
        {
            throw reject("令牌签发时间距今超过 " + maxAge + " 秒");
        }
        // 时钟偏差：允许 ERP 那台比本机快一点。超过 60 秒就是真的没对时了
        if (iat > 0 && iat - now > 60)
        {
            log.warn("跳转令牌的签发时间比本机时间早 {} 秒，两台机器该对时了", iat - now);
            throw reject("令牌签发时间异常");
        }

        // ---- ⑤ jti 一次性 ----
        String jti = payload.path("jti").asText(null);
        if (jti == null || jti.isEmpty())
        {
            throw reject("令牌缺少 jti");
        }
        consumeOnce(jti, exp - now);

        JumpToken result = new JumpToken();
        result.setSiteCode(site1);
        result.setUser(payload.path("user").asText(null));
        result.setName(payload.path("name").asText(null));
        result.setJti(jti);
        if (result.getUser() == null || result.getUser().isEmpty())
        {
            throw reject("令牌里没有用户标识");
        }
        // 入场用途：只认白名单。不认识的值直接拒，而不是当成普通进入放行——
        // 否则 ERP 那边加了新用途、这边没跟上时，人会被静默带进一个权限不对的会话
        String scope = payload.path("scope").asText(null);
        if (scope != null && !JumpToken.SCOPE_USERS.equals(scope))
        {
            throw reject("不认识的入场用途：" + scope);
        }
        result.setScope(scope);
        return result;
    }

    /**
     * 消费 jti，保证一张令牌只能用一次。
     *
     * <p>{@code setIfAbsent} 就是 Redis 的 {@code SET key value NX EX ttl}——
     * <b>写入和判存在是同一个命令</b>。写成「先 get 再 set」的话，
     * 两个并发请求会双双看到「没用过」，一次性就没了。</p>
     *
     * <p>TTL 只需要活到令牌过期为止：过了 {@code exp}，第④步会先把它拒掉，
     * 这条记录再留着也没用。给足一点余量防时钟抖动。</p>
     */
    private void consumeOnce(String jti, long remainingSeconds)
    {
        long ttl = Math.max(remainingSeconds, 1) + 60;
        Boolean first = redis.opsForValue().setIfAbsent(JTI_KEY + jti, "1", Duration.ofSeconds(ttl));
        if (!Boolean.TRUE.equals(first))
        {
            // 要么是用户点了两次（浏览器重发表单），要么是有人截到令牌抢先用了。
            // 两种都表现为「进不去」，而后一种是会被立刻发现的——这正是设计意图（§4.4 第 3 条）
            log.warn("跳转令牌被重复使用，jti={}", jti);
            throw reject("这个令牌已经用过了");
        }
    }

    /**
     * 本站的验签密钥：库里那份优先，配置文件那份兜底。
     *
     * <p>库优先是因为它和 ERP 登记的那份是同一个值、由建站流程一起写入；
     * 配置文件那份是给「库里还没填」时的临时手段。两个都空就一律拒——
     * <b>不能放行</b>：没有密钥意味着任何人构造一个签名都能进。</p>
     */
    private String resolveSecret(SiteInfo site)
    {
        String fromDb = site.getConsoleSecret();
        if (fromDb != null && !fromDb.isBlank())
        {
            return fromDb;
        }
        String fromConfig = properties.getJump().getSecret();
        if (fromConfig != null && !fromConfig.isBlank())
        {
            log.warn("跳转密钥来自配置文件而不是 u_s_siteinfo.consolesecret。"
                    + "正式环境应当填进库里——它是站点档案的一部分，和 ERP 登记的那份必须一致");
            return fromConfig;
        }
        // 默认关闭：漏配的后果是「进不去」，而不是「门大开着」
        log.error("本站没有配置跳转密钥（u_s_siteinfo.consolesecret 和 station.jump.secret 都是空的），"
                + "从 ERP「进入管理」一律拒绝");
        throw reject("本站未启用 ERP 跳转");
    }

    private byte[] hmac(String payload, String secret)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            // 算法一定存在、密钥一定非空（resolveSecret 保证了），走不到这里
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    /**
     * 统一的拒绝。
     *
     * <p>参数里那句话<b>只进日志，不给用户看</b>——调用方（SsoController）会把它换成
     * 一句统一的「令牌无效或已过期」，后面跟那一页固定的操作路径
     * 「回到众联 ERP 的独立站管理页面，重新点【进入管理】」。区分开只会方便探测。</p>
     */
    private ServiceException reject(String reason)
    {
        log.warn("跳转令牌被拒：{}", reason);
        return new ServiceException(reason, 401);
    }
}

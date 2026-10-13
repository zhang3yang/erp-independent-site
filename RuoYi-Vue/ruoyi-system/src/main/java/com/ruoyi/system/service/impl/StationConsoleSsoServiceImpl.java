package com.ruoyi.system.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.config.OssProperties;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.sign.AesUtils;
import com.ruoyi.system.config.StationConsoleProperties;
import com.ruoyi.system.domain.StationConsoleJump;
import com.ruoyi.system.domain.StationSite;
import com.ruoyi.system.mapper.StationSiteMapper;
import com.ruoyi.system.service.IStationConsoleSsoService;

/**
 * 跳转令牌的签发。ERP 侧就这一个类和独立站有关系。
 *
 * <p>协议见 docs/独立站与ERP的边界.md §4.2/§4.3，验签那一半在站点后端的
 * {@code JumpTokenVerifier} 里。</p>
 */
@Service
public class StationConsoleSsoServiceImpl implements IStationConsoleSsoService
{
    private static final Logger log = LoggerFactory.getLogger(StationConsoleSsoServiceImpl.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String HMAC_ALG = "HmacSHA256";

    /** 令牌结构版本。站点侧只认这个值，改结构时两边一起动 */
    private static final int TOKEN_VERSION = 1;

    /** 签发方标记。一期只有一个签发方，留着是为了将来真有第二个时不用改结构 */
    private static final String ISSUER = "erp";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StationSiteMapper siteMapper;

    @Autowired
    private StationConsoleProperties properties;

    /** 只用来取加密主密钥（{@code ruoyi.oss.config-secret}），和对象存储本身无关 */
    @Autowired
    private OssProperties ossProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StationConsoleJump issueJump(String shopCode, String siteCode, LoginUser loginUser,
            String clientIp, String scope)
    {
        requireShop(shopCode);
        // 用途只认白名单里的值。站点那边同样只认这几个，对不上的令牌直接拒
        if (scope != null && !SCOPE_USERS.equals(scope))
        {
            throw new ServiceException("不认识的跳转用途：" + scope);
        }
        // selectByCode 带着 shopCode 一起查：别家的站点编码在这里查出来就是 null。
        // siteCode 从前端来、可以被人手改，这一句就是挡它的地方
        StationSite site = siteMapper.selectByCode(shopCode, trimToNull(siteCode));
        if (site == null)
        {
            throw new ServiceException("站点不存在");
        }
        if (StationSite.isDisabled(site.getStatus()))
        {
            throw new ServiceException("站点「" + site.getSiteName() + "」已停用，如需继续维护请先改回启用");
        }

        // ⚠ 这里不再有「没配就回退 ERP 内置页」那个分支（§4.5）：内置页已经删了。
        //   没登记域名 = 进不去，说清楚原因，让人去把域名填上。
        String baseUrl = site.consoleBaseUrl();
        if (baseUrl == null)
        {
            throw new ServiceException("站点「" + site.getSiteName()
                    + "」还没登记主域名或管理后台域名，跳转地址拼不出来。先在站点档案里补上");
        }

        String secret = decryptSecret(shopCode, site);
        int ttl = properties.getEffectiveTokenTtl();

        String jti = randomToken(16);
        long now = System.currentTimeMillis() / 1000;
        String token = sign(site, loginUser, jti, now, now + ttl, secret, scope);

        // ⚠ 打点打在这里，不是「进成功了」那一刻——新协议下 ERP 根本不知道后续
        //   （验签在站点侧、不回调）。语义因此从「进成功了」变成「点了进入管理」，
        //   列注释和 StationSite.lastAccessTime 上都写了这一条。
        siteMapper.touchAccessTime(shopCode, site.getSiteCode());

        StationConsoleJump jump = new StationConsoleJump();
        jump.setAction(baseUrl + properties.getNormalizedSsoPath());
        jump.setToken(token);
        jump.setExpiresIn(ttl);
        // 日志里记 jti 的前几位，不记令牌本身——令牌进了日志文件就等于被留存了半年，
        // 而它在 60 秒内是可以直接用的
        jump.setJti(jti.substring(0, Math.min(8, jti.length())));
        jump.setSiteName(site.getSiteName());

        log.info("独立站跳转令牌已签发：jti={} 商户={} 站点={} 用户={} 用途={} 来源IP={} 有效期={}s",
                jump.getJti(), shopCode, site.getSiteCode(),
                loginUser == null ? null : loginUser.getUsername(), scope == null ? "进入管理" : scope, clientIp, ttl);
        return jump;
    }

    // ==================== 签名 ====================

    /**
     * 签一个自包含令牌。
     *
     * <pre>
     * payload = base64url(json)   sig = base64url(HMAC-SHA256(payload, 站点跳转密钥))
     * token   = payload + "." + sig
     * </pre>
     *
     * <h3>⚠ payload 里没有 perms，将来也不该加</h3>
     * <p>§4.3 明写这是有意的：</p>
     *
     * <blockquote>
     * 权限归独立站自己那套。ERP 只说「这是谁」，不说「他能干什么」——后者由独立站的
     * 角色配置决定。<b>把 ERP 的权限码塞进来，等于两边的权限模型又绑在一起了。</b>
     * </blockquote>
     *
     * <p>所以这个方法只从 {@code loginUser} 上取登录名和昵称，
     * {@code loginUser.getPermissions()} 一个都不看。</p>
     */
    private String sign(StationSite site, LoginUser loginUser, String jti, long iat, long exp,
            String secret, String scope)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ver", TOKEN_VERSION);
        payload.put("iss", ISSUER);
        // 站点侧会拿它和自己档案里的 sitecode 比对，不一致直接拒（§4.3）
        payload.put("site", site.getSiteCode());
        payload.put("user", loginUser == null ? null : loginUser.getUsername());
        payload.put("name", loginUser == null || loginUser.getUser() == null
                ? null : loginUser.getUser().getNickName());
        payload.put("iat", iat);
        payload.put("exp", exp);
        payload.put("jti", jti);
        // 入场用途（见 IStationConsoleSsoService.SCOPE_USERS）。普通进入不写这个键，
        // 老版本站点不认识它也无妨：多一个字段不影响验签，只是不会开用户管理
        if (scope != null)
        {
            payload.put("scope", scope);
        }

        byte[] json;
        try
        {
            json = JSON.writeValueAsBytes(payload);
        }
        catch (Exception e)
        {
            throw new ServiceException("跳转令牌生成失败");
        }
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(encoded, secret));
        return encoded + "." + signature;
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
            // 算法一定存在、密钥一定非空（decryptSecret 保证了），走不到这里
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    /**
     * 取这个站的跳转密钥明文。
     *
     * <p>走 {@link StationSiteMapper#selectSecret}，不是 {@code selectByCode}——
     * 后者的结果会被序列化给前端，这一条的结果只在服务端存活几毫秒。</p>
     *
     * <p>没配密钥时拒绝，<b>不给任何兜底</b>：这是「默认关闭」——
     * 漏配的后果是功能不可用，而不是门大开着。</p>
     */
    private String decryptSecret(String shopCode, StationSite site)
    {
        String encrypted = siteMapper.selectSecret(shopCode, site.getSiteCode());
        if (isBlank(encrypted))
        {
            throw new ServiceException("站点「" + site.getSiteName()
                    + "」还没有跳转密钥。在站点档案里点「重置跳转密钥」生成一份，"
                    + "并把它填进那台站点服务器的 u_s_siteinfo.consolesecret", HttpStatus.FORBIDDEN);
        }
        String masterKey = ossProperties.getConfigSecret();
        if (isBlank(masterKey))
        {
            throw new ServiceException("还没配置加密主密钥（ruoyi.oss.config-secret），"
                    + "跳转密钥解不开。用环境变量 OSS_CONFIG_SECRET 注入", HttpStatus.FORBIDDEN);
        }
        try
        {
            return AesUtils.decrypt(encrypted, masterKey);
        }
        catch (IllegalStateException e)
        {
            // 主密钥被换过，或者密文被改过。这两种都得人工处理——
            // 重置一份新密钥，然后同步到那台站点服务器上
            log.error("站点 {} 的跳转密钥解不开，可能是主密钥变更了", site.getSiteCode(), e);
            throw new ServiceException("站点「" + site.getSiteName()
                    + "」的跳转密钥解不开（主密钥可能已变更）。重置一份新密钥，"
                    + "并同步到那台站点服务器", HttpStatus.FORBIDDEN);
        }
    }

    /** 生成 URL-safe 的随机串。不带 padding，省得 = 号在传输里还要转义 */
    private static String randomToken(int bytes)
    {
        byte[] raw = new byte[bytes];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private void requireShop(String shopCode)
    {
        if (isBlank(shopCode))
        {
            throw new ServiceException("当前账号没有商家上下文，无法操作独立站");
        }
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }
}

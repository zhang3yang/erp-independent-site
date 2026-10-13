package com.ruoyi.web.controller.station;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.constant.TenantPermissionPrefixes;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.system.domain.StationConsoleJump;
import com.ruoyi.system.domain.StationSite;
import com.ruoyi.system.domain.TenantShop;
import com.ruoyi.system.domain.model.StationLoginBody;
import com.ruoyi.system.service.IStationConsoleSsoService;
import com.ruoyi.system.service.IStationSiteService;
import com.ruoyi.system.service.ITenantShopService;

/**
 * 独立站专用登录：账号密码换站点跳转令牌，<b>不建 ERP 会话</b>。
 *
 * <p>给「一商家一站、在 ERP 里只有独立站管理权限」的外部客户用（首例：法国商家 esvafit）。
 * 他们在 {@code /store-login} 输账号密码，这里校验完直接签站点 {@code /sso} 的令牌，
 * 前端表单 POST 过去，页面就变成站点后台——全程不进 ERP 的任何页面，浏览器里也不留 ERP token。
 * 设计见 docs/superpowers/specs/2026-09-22-store-login-page-design.md。</p>
 *
 * <h3>这是个免验证码的公开接口，所以：</h3>
 * <ul>
 *   <li>按 IP 限流（一分钟 10 次）；密码错 N 次锁账号那道闸在 {@code SysPasswordService}，照样生效；</li>
 *   <li>认证失败一律同一句话，不透露账号存不存在；</li>
 *   <li>「不是店铺管理账号」也一律同一句话，不区分是权限多了、商家多了还是站点没配好；</li>
 *   <li>直达判定复用 {@link IStationSiteService#resolveDirectConsoleSite}，不复制第二份规则。</li>
 * </ul>
 *
 * <p>提示全是英文：这个接口只给外部客户用，ERP 其他接口的中文不动。</p>
 */
@Anonymous
@RestController
public class StationLoginController
{
    private static final Logger log = LoggerFactory.getLogger(StationLoginController.class);

    private static final String MSG_BAD_CREDENTIALS = "Incorrect username or password.";
    private static final String MSG_NOT_STORE_ACCOUNT = "This is not a store administrator account.";
    private static final String MSG_CONSOLE_UNAVAILABLE = "Store console is unavailable. Please contact the administrator.";

    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ITenantShopService tenantShopService;

    @Autowired
    private IStationSiteService stationSiteService;

    @Autowired
    private IStationConsoleSsoService consoleSsoService;

    @PostMapping("/station/login")
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public AjaxResult login(@RequestBody StationLoginBody body)
    {
        String username = body == null ? null : StringUtils.trim(body.getUsername());
        String password = body == null ? null : body.getPassword();

        LoginUser loginUser;
        try
        {
            // 前置校验、密码校验、锁定、登录日志都在里面；只是不发 token
            loginUser = loginService.authenticate(username, password);
        }
        catch (RuntimeException e)
        {
            // 具体原因已经进了登录日志（sys_logininfor），对外只说一句
            log.info("独立站登录页认证失败：user={} ip={} reason={}", username, IpUtils.getIpAddr(), e.getMessage());
            return AjaxResult.error(MSG_BAD_CREDENTIALS);
        }

        String userCode = loginUser.getUserCode();
        if (SecurityUtils.isAdmin(userCode))
        {
            return AjaxResult.error(MSG_NOT_STORE_ACCOUNT);
        }
        List<TenantShop> shops = tenantShopService.selectShopsByAccount(userCode);
        if (shops == null || shops.size() != 1)
        {
            log.info("独立站登录页拒绝：user={} 所属商家数={}", username, shops == null ? 0 : shops.size());
            return AjaxResult.error(MSG_NOT_STORE_ACCOUNT);
        }
        TenantShop shop = shops.get(0);
        // 和 TenantShopController.select 同口径：只算租户域的权限
        Set<String> tenantPermissions = tenantShopService.selectPermissionsByMember(shop.getShopCode(), userCode).stream()
                .filter(TenantPermissionPrefixes::isTenantPermission)
                .collect(Collectors.toSet());
        StationSite site = stationSiteService.resolveDirectConsoleSite(shop.getShopCode(), tenantPermissions, false);
        if (site == null)
        {
            log.info("独立站登录页拒绝：user={} shop={} 不满足直达条件（权限={}）", username, shop.getShopCode(), tenantPermissions);
            return AjaxResult.error(MSG_NOT_STORE_ACCOUNT);
        }

        StationConsoleJump jump;
        try
        {
            jump = consoleSsoService.issueJump(shop.getShopCode(), site.getSiteCode(), loginUser, IpUtils.getIpAddr());
        }
        catch (RuntimeException e)
        {
            log.warn("独立站登录页签令牌失败：user={} site={} reason={}", username, site.getSiteCode(), e.getMessage());
            return AjaxResult.error(MSG_CONSOLE_UNAVAILABLE);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("action", jump.getAction());
        ajax.put("token", jump.getToken());
        ajax.put("expiresIn", jump.getExpiresIn());
        ajax.put("siteName", jump.getSiteName());
        return ajax;
    }
}

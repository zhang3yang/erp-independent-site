package com.ruoyi.web.controller.system;

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginBody;
import com.ruoyi.common.core.domain.model.SmsCodeBody;
import com.ruoyi.common.core.domain.model.SmsLoginBody;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ITenantShopService;

/**
 * 登录验证
 * 
 * @author ruoyi
 */
@RestController
public class SysLoginController
{
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ITenantShopService tenantShopService;

    /**
     * 登录方法
     * 
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    @PostMapping("/login/sms/code")
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public AjaxResult sendSmsCode(@RequestBody SmsCodeBody body)
    {
        loginService.sendSmsLoginCode(body == null ? null : body.getPhonenumber(),
                body == null ? null : body.getPlatcode());
        AjaxResult ajax = AjaxResult.success("验证码已发送");
        ajax.put("expireSeconds", 300);
        ajax.put("retryAfterSeconds", 60);
        return ajax;
    }

    @PostMapping("/login/sms")
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    public AjaxResult smsLogin(@RequestBody SmsLoginBody body)
    {
        String token = loginService.loginBySms(body == null ? null : body.getPhonenumber(),
                body == null ? null : body.getCode());
        AjaxResult ajax = AjaxResult.success();
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        // 角色集合
        boolean shopWasSelected = StringUtils.isNotEmpty(loginUser.getShopCode());
        boolean tenantSelected = shopWasSelected && tenantShopService.selectShopByAccountAndCode(
                loginUser.getUserCode(), loginUser.getShopCode()) != null;
        if (shopWasSelected && !tenantSelected)
        {
            loginUser.setShopCode(null);
            loginUser.setShopName(null);
            loginUser.setMemberUserCode(null);
            loginUser.setDeptId(user.getDeptId());
            loginUser.setTenantRoleList(List.of());
            loginUser.setTenantRoles(Set.of());
            loginUser.setTenantPermissions(Set.of());
        }
        // 权限集合
        Set<String> permissions = tenantSelected
                ? (SecurityUtils.isAdmin(loginUser.getUserCode())
                        ? tenantShopService.selectPermissionsByShop(loginUser.getShopCode())
                        : tenantShopService.selectPermissionsByMember(loginUser.getShopCode(), loginUser.getUserCode())).stream()
                        .filter(com.ruoyi.common.constant.TenantPermissionPrefixes::isTenantPermission)
                        .collect(java.util.stream.Collectors.toSet())
                : permissionService.getMenuPermission(user);
        Set<String> roles = tenantSelected ? loginUser.getTenantRoles() : permissionService.getRolePermission(user);
        if (!permissions.equals(loginUser.getPermissions()))
        {
            loginUser.setPermissions(permissions);
        }
        if (tenantSelected && !permissions.equals(loginUser.getTenantPermissions()))
        {
            loginUser.setTenantPermissions(permissions);
        }
        tokenService.refreshToken(loginUser);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("platformAdmin", SecurityUtils.isAdmin(loginUser.getUserCode()));
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        ajax.put("tenantSelected", tenantSelected);
        ajax.put("shopCode", loginUser.getShopCode());
        ajax.put("shopName", loginUser.getShopName());
        ajax.put("memberUserCode", loginUser.getMemberUserCode());
        ajax.put("tenantRoles", loginUser.getTenantRoles());
        ajax.put("tenantPermissions", loginUser.getTenantPermissions());
        ajax.put("pwdChrtype", getSysAccountChrtype());
        ajax.put("isDefaultModifyPwd", initPasswordIsModify(user.getPwdUpdateDate()));
        ajax.put("isPasswordExpired", passwordIsExpiration(user.getPwdUpdateDate()));
        return ajax;
    }

    /**
     * 获取路由信息
     * 
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        List<SysMenu> menus;
        if (StringUtils.isNotEmpty(loginUser.getShopCode()))
        {
            if (tenantShopService.selectShopByAccountAndCode(loginUser.getUserCode(), loginUser.getShopCode()) == null)
            {
                return AjaxResult.error("当前商家已停用或账号已离开该商家");
            }
            menus = SecurityUtils.isAdmin(loginUser.getUserCode())
                    ? tenantShopService.selectMenuTreeByShop(loginUser.getShopCode())
                    : tenantShopService.selectMenuTreeByMember(loginUser.getShopCode(), loginUser.getUserCode());
            menus = menuService.buildMenuTree(menus);
        }
        else
        {
            menus = menuService.selectMenuTreeByUserCode(loginUser.getUserCode());
        }
        return AjaxResult.success(menuService.buildMenus(menus));
    }

    // 获取用户密码自定义配置规则
    public String getSysAccountChrtype()
    {
        return Convert.toStr(configService.selectConfigByKey("sys.account.chrtype"), "0");
    }

    // 检查初始密码是否提醒修改
    public boolean initPasswordIsModify(Date pwdUpdateDate)
    {
        Integer initPasswordModify = Convert.toInt(configService.selectConfigByKey("sys.account.initPasswordModify"));
        return initPasswordModify != null && initPasswordModify == 1 && pwdUpdateDate == null;
    }

    // 检查密码是否过期
    public boolean passwordIsExpiration(Date pwdUpdateDate)
    {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0)
        {
            if (StringUtils.isNull(pwdUpdateDate))
            {
                // 如果从未修改过初始密码，直接提醒过期
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }
}

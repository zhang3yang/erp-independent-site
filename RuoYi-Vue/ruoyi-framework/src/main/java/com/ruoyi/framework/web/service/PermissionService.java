package com.ruoyi.framework.web.service;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.TenantPermissionPrefixes;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.security.context.PermissionContextHolder;
import com.ruoyi.system.service.ITenantShopService;

/**
 * RuoYi首创 自定义权限实现，ss取自SpringSecurity首字母
 * 
 * @author ruoyi
 */
@Service("ss")
public class PermissionService
{
    @Autowired
    private ITenantShopService tenantShopService;

    /**
     * 验证用户是否具备某权限
     * 
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public boolean hasPermi(String permission)
    {
        if (StringUtils.isEmpty(permission))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Set<String> permissions = effectivePermissions(loginUser);
        if (CollectionUtils.isEmpty(permissions))
        {
            return false;
        }
        PermissionContextHolder.setContext(permission);
        return hasPermissions(permissions, permission);
    }

    /**
     * 验证用户是否不具备某权限，与 hasPermi逻辑相反
     *
     * @param permission 权限字符串
     * @return 用户是否不具备某权限
     */
    public boolean lacksPermi(String permission)
    {
        return hasPermi(permission) != true;
    }

    /**
     * 验证是否已经选择商家。
     */
    public boolean hasTenant()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return StringUtils.isNotNull(loginUser)
                && StringUtils.isNotEmpty(loginUser.getShopCode())
                && tenantShopService.selectShopByAccountAndCode(
                        loginUser.getUserCode(), loginUser.getShopCode()) != null;
    }

    /**
     * Tenant users use their login shop; platform super administrators must select
     * an active target shop for the current business request.
     */
    public boolean hasBusinessShop()
    {
        if (hasTenant())
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser) || !SecurityUtils.isAdmin(loginUser.getUserCode())
                || StringUtils.isNotEmpty(loginUser.getShopCode()))
        {
            return false;
        }
        String shopCode = StringUtils.trim(com.ruoyi.common.utils.ServletUtils.getRequest()
                .getHeader(SecurityUtils.BUSINESS_SHOP_HEADER));
        if (StringUtils.isEmpty(shopCode) || !shopCode.matches("[A-Za-z0-9_-]{1,50}"))
        {
            return false;
        }
        com.ruoyi.system.domain.TenantShop shop = tenantShopService.selectShopByCode(shopCode.toUpperCase());
        return shop != null && Integer.valueOf(1).equals(shop.getStatus());
    }

    /**
     * 验证当前商家是否具备某个功能权限。
     */
    public boolean hasTenantPermi(String permission)
    {
        if (StringUtils.isEmpty(permission))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Set<String> permissions = effectivePermissions(loginUser);
        return StringUtils.isNotNull(loginUser)
                && !CollectionUtils.isEmpty(permissions)
                && hasPermissions(permissions, permission);
    }

    /**
     * 验证当前商家是否具备某个角色。
     */
    public boolean hasTenantRole(String role)
    {
        if (StringUtils.isEmpty(role))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (!hasActiveTenant(loginUser))
        {
            return false;
        }
        return tenantShopService.selectRolesByMember(loginUser.getShopCode(), loginUser.getUserCode()).stream()
                .anyMatch(sysRole -> StringUtils.equals(sysRole.getRoleKey(), StringUtils.trim(role)));
    }

    /**
     * Only the global super administrator may use platform-only tenant administration APIs.
     */
    public boolean isPlatformAdmin()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return StringUtils.isNotNull(loginUser)
                && StringUtils.isEmpty(loginUser.getShopCode())
                && SecurityUtils.isAdmin(loginUser.getUserCode());
    }

    /**
     * The global super administrator remains a platform operator after entering a shop.
     */
    public boolean isSuperAdmin()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return StringUtils.isNotNull(loginUser) && SecurityUtils.isAdmin(loginUser.getUserCode());
    }

    /**
     * 验证用户是否具有以下任意一个权限
     *
     * @param permissions 以 PERMISSION_DELIMITER 为分隔符的权限列表
     * @return 用户是否具有以下任意一个权限
     */
    public boolean hasAnyPermi(String permissions)
    {
        if (StringUtils.isEmpty(permissions))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Set<String> authorities = effectivePermissions(loginUser);
        if (CollectionUtils.isEmpty(authorities))
        {
            return false;
        }
        PermissionContextHolder.setContext(permissions);
        for (String permission : permissions.split(Constants.PERMISSION_DELIMITER))
        {
            if (permission != null && hasPermissions(authorities, permission))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断用户是否拥有某个角色
     * 
     * @param role 角色字符串
     * @return 用户是否具备某角色
     */
    public boolean hasRole(String role)
    {
        if (StringUtils.isEmpty(role))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser))
        {
            return false;
        }
        if (StringUtils.isNotEmpty(loginUser.getShopCode()) && !hasActiveTenant(loginUser))
        {
            return false;
        }
        Iterable<SysRole> effectiveRoles = StringUtils.isNotEmpty(loginUser.getShopCode())
                ? tenantShopService.selectRolesByMember(loginUser.getShopCode(), loginUser.getUserCode())
                : loginUser.getUser().getRoles();
        if (effectiveRoles == null)
        {
            return false;
        }
        for (SysRole sysRole : effectiveRoles)
        {
            String roleKey = sysRole.getRoleKey();
            String requestedRole = StringUtils.trim(role);
            if (StringUtils.equals(roleKey, requestedRole)
                    && (!Constants.SUPER_ADMIN.equals(requestedRole)
                            || StringUtils.isEmpty(loginUser.getShopCode())))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证用户是否不具备某角色，与 isRole逻辑相反。
     *
     * @param role 角色名称
     * @return 用户是否不具备某角色
     */
    public boolean lacksRole(String role)
    {
        return hasRole(role) != true;
    }

    /**
     * 验证用户是否具有以下任意一个角色
     *
     * @param roles 以 ROLE_DELIMITER 为分隔符的角色列表
     * @return 用户是否具有以下任意一个角色
     */
    public boolean hasAnyRoles(String roles)
    {
        if (StringUtils.isEmpty(roles))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser))
        {
            return false;
        }
        for (String role : roles.split(Constants.ROLE_DELIMITER))
        {
            if (hasRole(role))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否包含权限
     * 
     * @param permissions 权限列表
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    private boolean hasPermissions(Set<String> permissions, String permission)
    {
        return permissions.contains(Constants.ALL_PERMISSION) || permissions.contains(StringUtils.trim(permission));
    }

    private Set<String> effectivePermissions(LoginUser loginUser)
    {
        if (StringUtils.isNull(loginUser))
        {
            return Set.of();
        }
        // 平台超级管理员在未切换登录商家时，业务页面通过 X-Shop-Code
        // 临时指定管理商家，权限必须按该商家的启用菜单计算。
        if (StringUtils.isEmpty(loginUser.getShopCode()) && SecurityUtils.isAdmin(loginUser.getUserCode()))
        {
            String headerShopCode = StringUtils.trim(com.ruoyi.common.utils.ServletUtils.getRequest()
                    .getHeader(SecurityUtils.BUSINESS_SHOP_HEADER));
            if (StringUtils.isNotEmpty(headerShopCode) && headerShopCode.matches("[A-Za-z0-9_-]{1,50}"))
            {
                String shopCode = headerShopCode.toUpperCase();
                com.ruoyi.system.domain.TenantShop shop = tenantShopService.selectShopByCode(shopCode);
                if (shop != null && Integer.valueOf(1).equals(shop.getStatus()))
                {
                    Set<String> permissions = tenantShopService.selectPermissionsByShop(shopCode).stream()
                            .filter(TenantPermissionPrefixes::isTenantPermission)
                            .collect(Collectors.toSet());
                    loginUser.setTenantPermissions(permissions);
                    return permissions;
                }
            }
        }
        if (StringUtils.isNotEmpty(loginUser.getShopCode()))
        {
            if (!hasActiveTenant(loginUser))
            {
                loginUser.setPermissions(Set.of());
                loginUser.setTenantPermissions(Set.of());
                return Set.of();
            }
            Set<String> permissions = (SecurityUtils.isAdmin(loginUser.getUserCode())
                    ? tenantShopService.selectPermissionsByShop(loginUser.getShopCode())
                    : tenantShopService.selectPermissionsByMember(loginUser.getShopCode(), loginUser.getUserCode())).stream()
                    .filter(TenantPermissionPrefixes::isTenantPermission)
                    .collect(Collectors.toSet());
            // 平台管理员（super admin）在商家内仍然保留系统管理权限
            if (SecurityUtils.isAdmin(loginUser.getUserCode()))
            {
                Set<String> systemPermissions = loginUser.getPermissions().stream()
                        .filter(permission -> permission != null && permission.startsWith("system:"))
                        .collect(Collectors.toSet());
                permissions.addAll(systemPermissions);
            }
            loginUser.setPermissions(permissions);
            loginUser.setTenantPermissions(permissions);
            return permissions;
        }
        return loginUser.getPermissions();
    }

    private boolean hasActiveTenant(LoginUser loginUser)
    {
        return StringUtils.isNotNull(loginUser)
                && StringUtils.isNotEmpty(loginUser.getShopCode())
                && tenantShopService.selectShopByAccountAndCode(
                        loginUser.getUserCode(), loginUser.getShopCode()) != null;
    }
}

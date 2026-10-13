package com.ruoyi.web.controller.system;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.TenantShop;
import com.ruoyi.system.domain.model.TenantSelectBody;
import com.ruoyi.system.service.ITenantShopService;

/**
 * 登录后的商家查询与选择。
 */
@RestController
@RequestMapping("/tenant")
public class TenantShopController extends BaseController
{
    @Autowired
    private ITenantShopService tenantShopService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysPermissionService permissionService;

    @GetMapping("/shops")
    public AjaxResult shops()
    {
        LoginUser loginUser = getLoginUser();
        List<TenantShop> shops = tenantShopService.selectShopsByAccount(loginUser.getUserCode());

        AjaxResult ajax = AjaxResult.success();
        ajax.put("shops", shops);
        ajax.put("selectedShopCode", loginUser.getShopCode());
        ajax.put("selectedShopName", loginUser.getShopName());
        return ajax;
    }

    @PostMapping("/platform")
    @PreAuthorize("@ss.isSuperAdmin()")
    public AjaxResult platform()
    {
        LoginUser loginUser = getLoginUser();
        loginUser.setShopCode(null);
        loginUser.setShopName(null);
        loginUser.setMemberUserCode(null);
        loginUser.setDeptId(loginUser.getUser().getDeptId());
        loginUser.setTenantRoleList(List.of());
        loginUser.setTenantRoles(Set.of());
        loginUser.setTenantPermissions(Set.of());
        loginUser.setPermissions(permissionService.getMenuPermission(loginUser.getUser()));
        tokenService.setLoginUser(loginUser);
        return AjaxResult.success();
    }

    @PostMapping("/select")
    public AjaxResult select(@Validated @RequestBody TenantSelectBody body)
    {
        LoginUser loginUser = getLoginUser();
        TenantShop shop = tenantShopService.selectShopByAccountAndCode(loginUser.getUserCode(), body.getShopCode());
        if (shop == null)
        {
            return AjaxResult.error("当前账号不属于该商家或商家已停用");
        }

        loginUser.setShopCode(shop.getShopCode());
        loginUser.setShopName(shop.getShopName());
        loginUser.setMemberUserCode(shop.getMemberUserCode());
        loginUser.setDeptId(shop.getDeptId());
        loginUser.setTenantRoleList(tenantShopService.selectRolesByMember(shop.getShopCode(), loginUser.getUserCode()));
        loginUser.setTenantRoles(tenantShopService.selectRoleCodesByMember(shop.getShopCode(), loginUser.getUserCode()));
        Set<String> tenantPermissions = (SecurityUtils.isAdmin(loginUser.getUserCode())
                ? tenantShopService.selectPermissionsByShop(shop.getShopCode())
                : tenantShopService.selectPermissionsByMember(shop.getShopCode(), loginUser.getUserCode())).stream()
                .filter(com.ruoyi.common.constant.TenantPermissionPrefixes::isTenantPermission)
                .collect(Collectors.toSet());
        loginUser.setPermissions(tenantPermissions);
        loginUser.setTenantPermissions(tenantPermissions);
        tokenService.setLoginUser(loginUser);

        AjaxResult ajax = AjaxResult.success("已进入" + shop.getShopName());
        ajax.put("shop", shop);
        ajax.put("tenantRoles", loginUser.getTenantRoles());
        ajax.put("tenantPermissions", loginUser.getTenantPermissions());
        // 不再做「登录直达站点管理台」：从 ERP 登录页进来的就进 ERP。
        // 外部客户直达站点后台走 POST /station/login（StationLoginController）。
        return ajax;
    }
}

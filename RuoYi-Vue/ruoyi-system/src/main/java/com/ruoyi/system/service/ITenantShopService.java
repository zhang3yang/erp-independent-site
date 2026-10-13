package com.ruoyi.system.service;

import java.util.List;
import java.util.Set;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.system.domain.TenantShop;
import com.ruoyi.system.domain.TenantShopAdmin;
import com.ruoyi.system.domain.model.TenantAdminAccountBody;
import com.ruoyi.system.domain.model.TenantShopBody;

/**
 * 登录账号与商家关系服务。
 */
public interface ITenantShopService
{
    public List<TenantShop> selectShopsByAccount(String userCode);

    public TenantShop selectShopByAccountAndCode(String userCode, String shopCode);

    public List<SysRole> selectRolesByMember(String shopCode, String userCode);

    public boolean isShopAdmin(String shopCode, String userCode);

    public Set<String> selectRoleCodesByMember(String shopCode, String userCode);

    public Set<String> selectPermissionsByMember(String shopCode, String userCode);

    public Set<String> selectPermissionsByShop(String shopCode);

    public Set<String> selectFunCodesByMember(String shopCode, String userCode);

    public Set<String> selectFunCodesByShop(String shopCode);

    public List<SysMenu> selectMenuTreeByMember(String shopCode, String userCode);

    public List<SysMenu> selectMenuTreeByShop(String shopCode);

    public List<TenantShopAdmin> selectShopAdminRoles();

    public TenantShopAdmin selectShopAdminRole(String roleCode);

    public List<SysMenu> selectTenantAssignableMenus();

    public int updateShopAdminPermissions(String roleCode, String[] funCodes);

    public int saveShopAdminAccount(String roleCode, TenantAdminAccountBody body, String operator);

    public TenantShop selectShopByCode(String shopCode);

    /** 比 {@link #selectShopByCode} 多带管理台地址，给商家编辑表单回显用 */
    public TenantShop selectShopDetailByCode(String shopCode);

    public int createShop(TenantShopBody body, String operatorUserCode, String operator);

    public int updateShop(String shopCode, TenantShopBody body, String operator);

    public int deleteShop(String shopCode, String operator);
}

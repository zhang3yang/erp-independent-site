package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.system.domain.TenantShop;
import com.ruoyi.system.domain.TenantShopAdmin;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.model.TenantShopBody;

/**
 * 登录账号与商家关系数据层。
 */
public interface TenantShopMapper
{
    public List<TenantShop> selectShopsByAccount(@Param("userCode") String userCode);

    public TenantShop selectShopByAccountAndCode(@Param("userCode") String userCode,
            @Param("shopCode") String shopCode);

    public List<SysRole> selectRolesByMember(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode);

    public int countShopAdminMembership(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode);

    public List<String> selectRoleCodesByMember(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode);

    public List<String> selectPermissionsByMember(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode);

    public List<String> selectPermissionsByShop(@Param("shopCode") String shopCode);

    public List<String> selectFunCodesByMember(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode);

    public List<String> selectFunCodesByShop(@Param("shopCode") String shopCode);

    public List<SysMenu> selectMenuTreeByMember(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode);

    public List<SysMenu> selectMenuTreeByShop(@Param("shopCode") String shopCode);

    public List<TenantShopAdmin> selectShopAdminRoles();

    public TenantShopAdmin selectShopAdminRoleByCode(@Param("roleCode") String roleCode);

    public List<String> selectShopAdminFunCodes(@Param("roleCode") String roleCode);

    public List<SysMenu> selectTenantAssignableMenus();

    public int deleteShopAdminRoleMenus(@Param("roleCode") String roleCode);

    public int batchInsertShopAdminRoleMenus(@Param("roleCode") String roleCode,
            @Param("funCodes") String[] funCodes);

    public int deleteShopBusinessMenus(@Param("shopCode") String shopCode);

    public int insertShopBusinessMenus(@Param("shopCode") String shopCode,
            @Param("funCodes") String[] funCodes, @Param("operator") String operator);

    public Long selectShopRootDeptId(@Param("shopCode") String shopCode);

    public int upsertShopAdminMembership(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode, @Param("deptId") Long deptId,
            @Param("operator") String operator);

    public int demoteOtherShopOwners(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode, @Param("operator") String operator);

    public int deleteOtherShopAdminRoleMembers(@Param("shopCode") String shopCode,
            @Param("roleCode") String roleCode, @Param("userCode") String userCode);

    public int insertShopAdminRoleMember(@Param("shopCode") String shopCode,
            @Param("roleCode") String roleCode, @Param("userCode") String userCode,
            @Param("operator") String operator);

    public int updateShopAdminUser(SysUser user);

    public TenantShop selectShopByCode(@Param("shopCode") String shopCode);

    /** 比 {@link #selectShopByCode} 多带 console_url，给商家编辑表单回显用 */
    public TenantShop selectShopDetailByCode(@Param("shopCode") String shopCode);

    /** 只取商家档案上的管理台地址，给「进入管理」定跳转地址用 */
    public String selectShopConsoleUrl(@Param("shopCode") String shopCode);

    public int insertShop(TenantShopBody shop);

    public int updateShop(@Param("shopCode") String shopCode, @Param("shop") TenantShopBody shop);

    public int insertShopRootDept(@Param("shopCode") String shopCode, @Param("deptName") String deptName,
            @Param("operator") String operator);

    public int insertBuiltInAdminRole(@Param("roleCode") String roleCode, @Param("shopCode") String shopCode,
            @Param("roleKey") String roleKey, @Param("operator") String operator);

    public int provisionAllTenantMenus(@Param("shopCode") String shopCode,
            @Param("operator") String operator);

    public int insertDefaultAdminRoleMenus(@Param("roleCode") String roleCode);

    public int insertSuperAdminMembership(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode, @Param("deptId") Long deptId,
            @Param("operator") String operator);

    public int insertSuperAdminRole(@Param("shopCode") String shopCode,
            @Param("userCode") String userCode, @Param("roleCode") String roleCode,
            @Param("operator") String operator);

    public int deleteShopUserPosts(@Param("shopCode") String shopCode);
    public int deleteShopUserRoles(@Param("shopCode") String shopCode);
    public int deleteShopUsers(@Param("shopCode") String shopCode);
    public int deleteShopMenus(@Param("shopCode") String shopCode);
    public int deleteShopRoleMenus(@Param("shopCode") String shopCode);
    public int deleteShopRoleDepts(@Param("shopCode") String shopCode);
    public int deleteShopRoles(@Param("shopCode") String shopCode);
    public int deleteShopPosts(@Param("shopCode") String shopCode);
    public int deleteShopDepts(@Param("shopCode") String shopCode);
    public int deleteLegacyShopRoleFunctions(@Param("shopCode") String shopCode);
    public int deleteLegacyShopStaffRoles(@Param("shopCode") String shopCode);
    public int deleteLegacyShopStaff(@Param("shopCode") String shopCode);
    public int deleteLegacyShopRoles(@Param("shopCode") String shopCode);
    public int deleteLegacyShopOrganizations(@Param("shopCode") String shopCode);
    public int deleteShopMigrationIssues(@Param("shopCode") String shopCode);
    public int markShopDeleted(@Param("shopCode") String shopCode);
}

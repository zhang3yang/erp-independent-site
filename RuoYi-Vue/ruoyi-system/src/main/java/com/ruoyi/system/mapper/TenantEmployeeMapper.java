package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.system.domain.TenantDepartment;
import com.ruoyi.system.domain.TenantEmployee;
import com.ruoyi.system.domain.TenantRole;

/**
 * Tenant-scoped employee management persistence.
 */
public interface TenantEmployeeMapper
{
    public List<SysDept> selectDeptList(@Param("shopCode") String shopCode, @Param("dept") SysDept dept);

    public List<TenantDepartment> selectPlatformDeptList(@Param("query") TenantDepartment query);

    public SysDept selectDeptById(@Param("shopCode") String shopCode, @Param("deptId") Long deptId);

    public int checkDeptNameUnique(@Param("shopCode") String shopCode, @Param("parentId") Long parentId,
            @Param("deptName") String deptName, @Param("excludeDeptId") Long excludeDeptId);

    public int insertDept(@Param("shopCode") String shopCode, @Param("dept") SysDept dept);

    public int updateDept(@Param("shopCode") String shopCode, @Param("dept") SysDept dept);

    public int updateDeptChildren(@Param("shopCode") String shopCode, @Param("depts") List<SysDept> depts);

    public int hasChildDept(@Param("shopCode") String shopCode, @Param("deptId") Long deptId);

    public int countDeptMembers(@Param("shopCode") String shopCode, @Param("deptId") Long deptId);

    public int deleteDept(@Param("shopCode") String shopCode, @Param("deptId") Long deptId,
            @Param("updateBy") String updateBy);

    public List<TenantEmployee> selectEmployeeList(@Param("shopCode") String shopCode,
            @Param("employee") TenantEmployee employee,
            @Param("excludeUserCodes") java.util.Collection<String> excludeUserCodes);

    public List<TenantEmployee> selectPlatformEmployeeList(@Param("employee") TenantEmployee employee,
            @Param("excludeUserCodes") java.util.Collection<String> excludeUserCodes);

    public TenantEmployee selectEmployeeById(@Param("shopCode") String shopCode, @Param("userCode") String userCode);

    public String selectGlobalUserCodeByUserName(@Param("userName") String userName);

    public int insertGlobalUser(TenantEmployee employee);

    public int updateGlobalUserPhonenumber(@Param("userCode") String userCode, @Param("phonenumber") String phonenumber,
            @Param("updateBy") String updateBy);

    public int countActiveMembership(@Param("shopCode") String shopCode, @Param("userCode") String userCode);

    public int checkJobNoUnique(@Param("shopCode") String shopCode, @Param("jobNo") String jobNo,
            @Param("excludeUserCode") String excludeUserCode);

    public int insertMembership(@Param("shopCode") String shopCode, @Param("employee") TenantEmployee employee);

    public int restoreMembership(@Param("shopCode") String shopCode, @Param("employee") TenantEmployee employee);

    public int updateMembership(@Param("shopCode") String shopCode, @Param("employee") TenantEmployee employee);

    public int updateMembershipSelf(@Param("shopCode") String shopCode, @Param("userCode") String userCode,
            @Param("jobNo") String jobNo, @Param("updateBy") String updateBy);

    public int updateGlobalUserProfile(@Param("userCode") String userCode, @Param("nickName") String nickName,
            @Param("phonenumber") String phonenumber, @Param("updateBy") String updateBy);

    public int deleteMembership(@Param("shopCode") String shopCode, @Param("userCode") String userCode);

    public int deleteMemberPosts(@Param("shopCode") String shopCode, @Param("userCode") String userCode);

    public int deleteMemberRoles(@Param("shopCode") String shopCode, @Param("userCode") String userCode);

    public int countActiveMembershipsByUserCode(@Param("userCode") String userCode);

    public int countPlatformBindingsByUserCode(@Param("userCode") String userCode);

    public int deleteAllMemberPostsByUserCode(@Param("userCode") String userCode);

    public int deleteAllMemberRolesByUserCode(@Param("userCode") String userCode);

    public int deleteAllMembershipsByUserCode(@Param("userCode") String userCode);

    public int deleteNoticeReadsByUserCode(@Param("userCode") String userCode);

    public int deleteGlobalUserByCode(@Param("userCode") String userCode);

    public int batchInsertMemberRoles(@Param("shopCode") String shopCode, @Param("userCode") String userCode,
            @Param("roleCodes") String[] roleCodes);

    public List<String> selectMemberRoleCodes(@Param("shopCode") String shopCode, @Param("userCode") String userCode);

    public List<TenantRole> selectRoleList(@Param("shopCode") String shopCode, @Param("role") TenantRole role);

    public TenantRole selectRoleByCode(@Param("shopCode") String shopCode, @Param("roleCode") String roleCode);

    public int checkRoleNameUnique(@Param("shopCode") String shopCode, @Param("roleName") String roleName,
            @Param("excludeRoleCode") String excludeRoleCode);

    public int checkRoleKeyUnique(@Param("shopCode") String shopCode, @Param("roleKey") String roleKey,
            @Param("excludeRoleCode") String excludeRoleCode);

    public int insertRole(@Param("shopCode") String shopCode, @Param("role") TenantRole role);

    public int updateRole(@Param("shopCode") String shopCode, @Param("role") TenantRole role);

    public int countRoleMembers(@Param("shopCode") String shopCode, @Param("roleCode") String roleCode);

    public int deleteRole(@Param("shopCode") String shopCode, @Param("roleCode") String roleCode,
            @Param("updateBy") String updateBy);

    public int deleteRoleMenusScoped(@Param("shopCode") String shopCode, @Param("roleCode") String roleCode,
            @Param("includeSystem") boolean includeSystem);

    public int batchInsertRoleMenusScoped(@Param("shopCode") String shopCode, @Param("roleCode") String roleCode,
            @Param("funCodes") String[] funCodes, @Param("includeSystem") boolean includeSystem);

    public List<String> selectRoleFunCodes(@Param("shopCode") String shopCode, @Param("roleCode") String roleCode);

    public List<SysMenu> selectMenusByFunCodes(@Param("funCodes") String[] funCodes);
}

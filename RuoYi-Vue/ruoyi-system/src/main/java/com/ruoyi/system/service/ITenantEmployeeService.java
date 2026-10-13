package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.domain.TenantDepartment;
import com.ruoyi.system.domain.TenantEmployee;
import com.ruoyi.system.domain.TenantRole;
import com.ruoyi.system.domain.model.TenantEmployeeCreateResult;

/**
 * Employee, department and role management inside the selected shop.
 */
public interface ITenantEmployeeService
{
    public List<SysDept> selectDeptTree(String shopCode, SysDept dept);

    public List<TenantDepartment> selectPlatformDeptList(TenantDepartment query);

    public SysDept selectDeptById(String shopCode, Long deptId);

    public int insertDept(String shopCode, SysDept dept, String username);

    public int updateDept(String shopCode, SysDept dept, String username);

    public int deleteDept(String shopCode, Long deptId, String username);

    public List<TenantEmployee> selectEmployeeList(String shopCode, TenantEmployee employee);

    public List<TenantEmployee> selectPlatformEmployeeList(TenantEmployee employee);

    public TenantEmployee selectEmployeeById(String shopCode, String userCode);

    public TenantEmployeeCreateResult insertEmployee(String shopCode, TenantEmployee employee, String username);

    public int updateEmployee(String shopCode, TenantEmployee employee, String username);

    public int updateMyProfile(String shopCode, TenantEmployee employee, String username);

    public int deleteEmployees(String shopCode, String[] userCodes);

    public List<TenantRole> selectRoleList(String shopCode, TenantRole role);

    public List<TenantRole> selectAssignableRoles(String shopCode, String actorUserCode);

    public TenantRole selectRoleByCode(String shopCode, String roleCode);

    public int insertRole(String shopCode, String actorUserCode, TenantRole role, String username);

    public int updateRole(String shopCode, String actorUserCode, TenantRole role, String username);

    public int deleteRoles(String shopCode, String[] roleCodes, String username);

    public List<TreeSelect> selectAssignableMenuTree(String shopCode, String actorUserCode, boolean superAdmin);

    public List<String> selectRoleFunCodes(String shopCode, String roleCode);

    public int updateRolePermissions(String shopCode, String actorUserCode, String roleCode, String[] funCodes,
            boolean superAdmin);
}

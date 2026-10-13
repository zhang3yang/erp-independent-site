package com.ruoyi.web.controller.system;

import java.util.List;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.model.SmsCodeBody;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.sms.SmsVerificationService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.TenantEmployee;
import com.ruoyi.system.domain.TenantRole;
import com.ruoyi.system.domain.model.TenantRolePermissionBody;
import com.ruoyi.system.domain.model.TenantEmployeeCreateResult;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ITenantEmployeeService;
import com.ruoyi.system.service.ITenantShopService;

/**
 * Employee, department, and role management inside the selected shop.
 */
@RestController
@RequestMapping("/tenant/employee-management")
@PreAuthorize("@ss.hasTenant()")
public class TenantEmployeeController extends BaseController
{
    @Autowired
    private ITenantEmployeeService tenantEmployeeService;

    @Autowired
    private ITenantShopService tenantShopService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SmsVerificationService smsVerificationService;

    @PreAuthorize("@ss.hasPermi('tenant:employee:dept:list')")
    @GetMapping("/dept/tree")
    public AjaxResult deptTree(SysDept dept)
    {
        return success(tenantEmployeeService.selectDeptTree(getShopCode(), dept));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:dept:list')")
    @GetMapping("/dept/{deptId}")
    public AjaxResult getDept(@PathVariable Long deptId)
    {
        return success(tenantEmployeeService.selectDeptById(getShopCode(), deptId));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:dept:add')")
    @Log(title = "商家部门管理", businessType = BusinessType.INSERT)
    @PostMapping("/dept")
    public AjaxResult addDept(@Validated @RequestBody SysDept dept)
    {
        return toAjax(tenantEmployeeService.insertDept(getShopCode(), dept, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:dept:edit')")
    @Log(title = "商家部门管理", businessType = BusinessType.UPDATE)
    @PutMapping("/dept")
    public AjaxResult updateDept(@Validated @RequestBody SysDept dept)
    {
        return toAjax(tenantEmployeeService.updateDept(getShopCode(), dept, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:dept:remove')")
    @Log(title = "商家部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/dept/{deptId}")
    public AjaxResult deleteDept(@PathVariable Long deptId)
    {
        return toAjax(tenantEmployeeService.deleteDept(getShopCode(), deptId, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:user:list')")
    @GetMapping("/employee/list")
    public TableDataInfo employeeList(TenantEmployee employee)
    {
        startPage();
        return getDataTable(tenantEmployeeService.selectEmployeeList(getShopCode(), employee));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:user:query')")
    @GetMapping("/employee/{userCode}")
    public AjaxResult getEmployee(@PathVariable String userCode)
    {
        TenantEmployee employee = tenantEmployeeService.selectEmployeeById(getShopCode(), userCode);
        AjaxResult ajax = success(employee);
        ajax.put("roleCodes", employee.getRoleCodes());
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:user:add')")
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/employee/sms/code")
    public AjaxResult sendEmployeeSmsCode(@RequestBody SmsCodeBody body)
    {
        smsVerificationService.sendEmployeeAddCode(body == null ? null : body.getPhonenumber(),
                body == null ? null : body.getPlatcode());
        AjaxResult ajax = AjaxResult.success("验证码已发送");
        ajax.put("expireSeconds", 300);
        ajax.put("retryAfterSeconds", 60);
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:user:add')")
    @Log(title = "商家员工管理", businessType = BusinessType.INSERT, excludeParamNames = { "smsCode", "password" })
    @PostMapping("/employee")
    public AjaxResult addEmployee(@RequestBody TenantEmployee employee)
    {
        TenantEmployeeCreateResult result = tenantEmployeeService.insertEmployee(
                getShopCode(), employee, getUsername());
        AjaxResult ajax = AjaxResult.success("员工新增成功");
        ajax.put("userCode", result.getUserCode());
        ajax.put("userName", result.getUserName());
        ajax.put("accountCreated", result.isAccountCreated());
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:user:edit')")
    @Log(title = "商家员工管理", businessType = BusinessType.UPDATE)
    @PutMapping("/employee")
    public AjaxResult updateEmployee(@RequestBody TenantEmployee employee)
    {
        return toAjax(tenantEmployeeService.updateEmployee(getShopCode(), employee, getUsername()));
    }

    @Log(title = "员工个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/employee/me")
    public AjaxResult updateMyProfile(@RequestBody TenantEmployee employee)
    {
        return toAjax(tenantEmployeeService.updateMyProfile(getShopCode(), employee, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:user:remove')")
    @Log(title = "商家员工管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/employee/{userCodes}")
    public AjaxResult deleteEmployees(@PathVariable String[] userCodes)
    {
        return toAjax(tenantEmployeeService.deleteEmployees(getShopCode(), userCodes));
    }

    @PreAuthorize("@ss.hasAnyPermi('tenant:employee:role:list,tenant:employee:user:add,tenant:employee:user:edit')")
    @GetMapping("/role/list")
    public TableDataInfo roleList(TenantRole role)
    {
        return getDataTable(tenantEmployeeService.selectRoleList(getShopCode(), role));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:role:query')")
    @GetMapping("/role/{roleCode}")
    public AjaxResult getRole(@PathVariable String roleCode)
    {
        return success(tenantEmployeeService.selectRoleByCode(getShopCode(), roleCode));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:role:add')")
    @Log(title = "商家角色管理", businessType = BusinessType.INSERT)
    @PostMapping("/role")
    public AjaxResult addRole(@Validated @RequestBody TenantRole role)
    {
        tenantEmployeeService.insertRole(getShopCode(), getUserCode(), role, getUsername());
        return success(role);
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:role:edit')")
    @Log(title = "商家角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/role")
    public AjaxResult updateRole(@Validated @RequestBody TenantRole role)
    {
        return toAjax(tenantEmployeeService.updateRole(
                getShopCode(), getUserCode(), role, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:role:remove')")
    @Log(title = "商家角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/role/{roleCodes}")
    public AjaxResult deleteRoles(@PathVariable String[] roleCodes)
    {
        return toAjax(tenantEmployeeService.deleteRoles(getShopCode(), roleCodes, getUsername()));
    }

    @PreAuthorize("@ss.hasAnyPermi('tenant:employee:role:query,tenant:employee:role:list') or @ss.isSuperAdmin()")
    @GetMapping({ "/role/permission-tree", "/role/permission-tree/{roleCode}" })
    public AjaxResult permissionTree(@PathVariable(required = false) String roleCode)
    {
        List<TreeSelect> menus = tenantEmployeeService.selectAssignableMenuTree(
                getShopCode(), getUserCode(), SecurityUtils.isAdmin(getUserCode()));
        AjaxResult ajax = AjaxResult.success();
        ajax.put("menus", menus);
        ajax.put("checkedKeys", roleCode == null
                ? List.of() : tenantEmployeeService.selectRoleFunCodes(getShopCode(), roleCode));
        return ajax;
    }

    @PreAuthorize("@ss.hasAnyPermi('tenant:employee:role:query,tenant:employee:role:list') or @ss.isSuperAdmin()")
    @GetMapping("/role/{roleCode}/built-in-permission-tree")
    public AjaxResult builtInPermissionTree(@PathVariable String roleCode)
    {
        validateCurrentShopBuiltInRole(roleCode);
        List<TreeSelect> menus = menuService.buildMenuTreeSelect(tenantShopService.selectTenantAssignableMenus());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("menus", menus);
        ajax.put("checkedKeys", Arrays.asList(tenantShopService.selectShopAdminRole(roleCode).getFunCodes()));
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('tenant:employee:role:editPermission') or @ss.isSuperAdmin()")
    @Log(title = "商家角色权限", businessType = BusinessType.GRANT)
    @PutMapping("/role/{roleCode}/permissions")
    public AjaxResult updateRolePermissions(@PathVariable String roleCode,
            @RequestBody TenantRolePermissionBody body)
    {
        return toAjax(tenantEmployeeService.updateRolePermissions(
                getShopCode(), getUserCode(), roleCode, body.getFunCodes(), SecurityUtils.isAdmin(getUserCode())));
    }

    @PreAuthorize("@ss.isSuperAdmin()")
    @Log(title = "商家管理员权限", businessType = BusinessType.GRANT)
    @PutMapping("/role/{roleCode}/built-in-permissions")
    public AjaxResult updateBuiltInRolePermissions(@PathVariable String roleCode,
            @RequestBody TenantRolePermissionBody body)
    {
        validateCurrentShopBuiltInRole(roleCode);
        return toAjax(tenantShopService.updateShopAdminPermissions(roleCode, body.getFunCodes()));
    }

    private void validateCurrentShopBuiltInRole(String roleCode)
    {
        TenantRole role = tenantEmployeeService.selectRoleByCode(getShopCode(), roleCode);
        if (!role.isSystemRole())
        {
            throw new com.ruoyi.common.exception.ServiceException("只能在这里配置内置商家管理员权限");
        }
    }
}

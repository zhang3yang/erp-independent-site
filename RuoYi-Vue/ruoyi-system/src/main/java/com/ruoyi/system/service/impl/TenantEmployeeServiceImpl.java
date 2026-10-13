package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.sms.SmsVerificationService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.TenantEmployee;
import com.ruoyi.system.domain.TenantDepartment;
import com.ruoyi.system.domain.TenantRole;
import com.ruoyi.system.domain.model.TenantEmployeeCreateResult;
import com.ruoyi.system.mapper.TenantEmployeeMapper;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.ITenantEmployeeService;
import com.ruoyi.system.service.ITenantShopService;

/**
 * Tenant-scoped employee management implementation.
 */
@Service
public class TenantEmployeeServiceImpl implements ITenantEmployeeService
{
    @Autowired
    private TenantEmployeeMapper mapper;

    @Autowired
    private RoleCodeGenerator roleCodeGenerator;

    @Autowired
    private ITenantShopService tenantShopService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SmsVerificationService smsVerificationService;

    @Override
    public List<SysDept> selectDeptTree(String shopCode, SysDept dept)
    {
        requireShop(shopCode);
        return buildDeptTree(mapper.selectDeptList(shopCode, dept));
    }

    @Override
    public List<TenantDepartment> selectPlatformDeptList(TenantDepartment query)
    {
        return mapper.selectPlatformDeptList(query);
    }

    @Override
    public SysDept selectDeptById(String shopCode, Long deptId)
    {
        requireShop(shopCode);
        SysDept dept = mapper.selectDeptById(shopCode, deptId);
        if (dept == null)
        {
            throw new ServiceException("Department does not exist in the current shop");
        }
        return dept;
    }

    @Override
    @Transactional
    public int insertDept(String shopCode, SysDept dept, String username)
    {
        requireShop(shopCode);
        if (dept.getParentId() == null)
        {
            dept.setParentId(0L);
        }
        if (dept.getParentId() == 0L)
        {
            dept.setAncestors("0");
        }
        else
        {
            SysDept parent = selectDeptById(shopCode, dept.getParentId());
            if (UserConstants.DEPT_DISABLE.equals(parent.getStatus()))
            {
                throw new ServiceException("Disabled department cannot receive child departments");
            }
            dept.setAncestors(parent.getAncestors() + "," + parent.getDeptId());
        }
        checkDeptNameUnique(shopCode, dept, null);
        dept.setStatus(StringUtils.defaultIfEmpty(dept.getStatus(), UserConstants.DEPT_NORMAL));
        dept.setCreateBy(username);
        return mapper.insertDept(shopCode, dept);
    }

    @Override
    @Transactional
    public int updateDept(String shopCode, SysDept dept, String username)
    {
        requireShop(shopCode);
        SysDept existing = selectDeptById(shopCode, dept.getDeptId());
        if (dept.getParentId() == null)
        {
            dept.setParentId(existing.getParentId());
        }
        if (dept.getDeptId().equals(dept.getParentId()))
        {
            throw new ServiceException("A department cannot be its own parent");
        }
        checkDeptNameUnique(shopCode, dept, dept.getDeptId());

        String newAncestors = "0";
        if (dept.getParentId() != 0L)
        {
            SysDept parent = selectDeptById(shopCode, dept.getParentId());
            if (StringUtils.split(parent.getAncestors(), ",") != null
                    && Arrays.asList(StringUtils.split(parent.getAncestors(), ",")).contains(String.valueOf(dept.getDeptId())))
            {
                throw new ServiceException("A department cannot be moved below its descendant");
            }
            newAncestors = parent.getAncestors() + "," + parent.getDeptId();
        }
        if (!StringUtils.equals(existing.getAncestors(), newAncestors))
        {
            List<SysDept> all = mapper.selectDeptList(shopCode, new SysDept());
            List<SysDept> children = all.stream()
                    .filter(item -> containsAncestor(item.getAncestors(), dept.getDeptId()))
                    .collect(Collectors.toList());
            for (SysDept child : children)
            {
                child.setAncestors(child.getAncestors().replaceFirst(
                        java.util.regex.Pattern.quote(existing.getAncestors()),
                        java.util.regex.Matcher.quoteReplacement(newAncestors)));
            }
            if (!children.isEmpty())
            {
                mapper.updateDeptChildren(shopCode, children);
            }
        }
        dept.setAncestors(newAncestors);
        dept.setStatus(StringUtils.defaultIfEmpty(dept.getStatus(), existing.getStatus()));
        dept.setUpdateBy(username);
        return mapper.updateDept(shopCode, dept);
    }

    @Override
    public int deleteDept(String shopCode, Long deptId, String username)
    {
        selectDeptById(shopCode, deptId);
        if (mapper.hasChildDept(shopCode, deptId) > 0)
        {
            throw new ServiceException("Child departments exist and must be removed first");
        }
        if (mapper.countDeptMembers(shopCode, deptId) > 0)
        {
            throw new ServiceException("Employees exist in this department");
        }
        return mapper.deleteDept(shopCode, deptId, username);
    }

    @Override
    public List<TenantEmployee> selectEmployeeList(String shopCode, TenantEmployee employee)
    {
        requireShop(shopCode);
        // 停用、已删除的员工以及平台超级管理员都不出现在员工列表中
        return mapper.selectEmployeeList(shopCode, employee, RuoYiConfig.getSuperAdminCodes());
    }

    @Override
    public List<TenantEmployee> selectPlatformEmployeeList(TenantEmployee employee)
    {
        // 与商家侧一致：停用、已删除的员工以及平台超级管理员都不出现在列表中
        return mapper.selectPlatformEmployeeList(employee, RuoYiConfig.getSuperAdminCodes());
    }

    @Override
    public TenantEmployee selectEmployeeById(String shopCode, String userCode)
    {
        requireShop(shopCode);
        TenantEmployee employee = mapper.selectEmployeeById(shopCode, userCode);
        if (employee == null)
        {
            throw new ServiceException("Employee does not exist in the current shop");
        }
        List<String> roleCodes = mapper.selectMemberRoleCodes(shopCode, userCode);
        employee.setRoleCodes(roleCodes.toArray(new String[0]));
        return employee;
    }

    @Override
    @Transactional
    public TenantEmployeeCreateResult insertEmployee(String shopCode, TenantEmployee employee, String username)
    {
        requireShop(shopCode);
        if (StringUtils.isEmpty(employee.getNickName()))
        {
            throw new ServiceException("Employee name is required");
        }
        if (employee.getNickName().length() > 30)
        {
            throw new ServiceException("Employee name cannot exceed 30 characters");
        }
        String phonenumber = normalizePhone(employee.getPhonenumber());
        validatePhoneRequired(phonenumber);
        employee.setPhonenumber(phonenumber);
        if (StringUtils.isNotEmpty(employee.getJobNo()) && employee.getJobNo().length() > 50)
        {
            throw new ServiceException("Employee number cannot exceed 50 characters");
        }
        requireDept(shopCode, employee.getDeptId());
        SysUser existingUser = userService.selectEmployeeAccountByPhonenumber(phonenumber);
        boolean accountCreated = existingUser == null;
        String globalUserCode;
        // 只有仍在职（未删除且启用）的员工才算重复；停用或已删除的旧关系会在下面被重新启用
        if (!accountCreated && mapper.countActiveMembership(shopCode, existingUser.getUserCode()) > 0)
        {
            throw new ServiceException("This account is already an employee of the current shop");
        }
        // 重新启用旧成员时，工号可能仍挂在他自己名下，不应算作冲突
        checkJobNo(shopCode, employee.getJobNo(), accountCreated ? null : existingUser.getUserCode());
        if (!accountCreated && !UserConstants.NORMAL.equals(existingUser.getStatus()))
        {
            throw new ServiceException("The account associated with this mobile number is disabled");
        }
        smsVerificationService.verifyEmployeeAddCode(phonenumber, employee.getSmsCode());
        if (accountCreated)
        {
            String initialPassword = employee.getPassword();
            if (StringUtils.isEmpty(initialPassword))
            {
                initialPassword = configService.selectConfigByKey("sys.user.initPassword");
            }
            if (StringUtils.isEmpty(initialPassword))
            {
                throw new ServiceException("System initial employee password is not configured");
            }
            if (initialPassword.length() < UserConstants.PASSWORD_MIN_LENGTH
                    || initialPassword.length() > UserConstants.PASSWORD_MAX_LENGTH)
            {
                throw new ServiceException("Password length must be between "
                        + UserConstants.PASSWORD_MIN_LENGTH + " and "
                        + UserConstants.PASSWORD_MAX_LENGTH + " characters");
            }
            SysUser newUser = new SysUser();
            globalUserCode = userService.generateUserCode();
            newUser.setUserCode(globalUserCode);
            newUser.setUserName(globalUserCode);
            newUser.setNickName(employee.getNickName());
            newUser.setEmail(employee.getEmail());
            newUser.setPhonenumber(employee.getPhonenumber());
            newUser.setSex(employee.getSex());
            newUser.setAvatar(employee.getAvatar());
            newUser.setPassword(SecurityUtils.encryptPassword(initialPassword));
            newUser.setStatus(UserConstants.NORMAL);
            newUser.setCreateBy(username);
            userService.insertUser(newUser);
        }
        else
        {
            globalUserCode = existingUser.getUserCode();
            SysUser accountUser = userService.selectUserByCode(globalUserCode);
            if (accountUser == null)
            {
                throw new ServiceException("The existing account is invalid");
            }
            if (StringUtils.isNotEmpty(accountUser.getPhonenumber())
                    && !phonenumber.equals(accountUser.getPhonenumber()))
            {
                throw new ServiceException("This account already has a different mobile number");
            }
        }
        employee.setUserCode(globalUserCode);
        if (!accountCreated && StringUtils.isEmpty(existingUser.getPhonenumber()))
        {
            mapper.updateGlobalUserPhonenumber(globalUserCode, phonenumber, username);
        }
        validateAssignedRoles(shopCode, SecurityUtils.getUserCode(), employee.getRoleCodes());
        // 新增的员工一律为启用状态，已存在的停用/已删除关系也会被恢复成启用
        employee.setStatus(UserConstants.NORMAL);
        employee.setCreateBy(username);
        employee.setUpdateBy(username);
        int rows = mapper.restoreMembership(shopCode, employee);
        if (rows == 0)
        {
            rows = mapper.insertMembership(shopCode, employee);
        }
        replaceMemberRoles(shopCode, globalUserCode, employee.getRoleCodes());
        return new TenantEmployeeCreateResult(rows, globalUserCode, accountCreated ? globalUserCode : existingUser.getUserName(), accountCreated);
    }

    @Override
    @Transactional
    public int updateEmployee(String shopCode, TenantEmployee employee, String username)
    {
        if (employee.getUserCode() == null)
        {
            throw new ServiceException("Employee ID is required");
        }
        if (StringUtils.isNotEmpty(employee.getJobNo()) && employee.getJobNo().length() > 50)
        {
            throw new ServiceException("Employee number cannot exceed 50 characters");
        }
        TenantEmployee existing = selectEmployeeById(shopCode, employee.getUserCode());
        if (Boolean.TRUE.equals(existing.getOwner()) || employee.getUserCode().equals(SecurityUtils.getUserCode()))
        {
            throw new ServiceException("The shop owner or current operator cannot change their own tenant membership");
        }
        validateTargetEmployeeRoles(shopCode, SecurityUtils.getUserCode(), existing.getRoleCodes());
        requireDept(shopCode, employee.getDeptId());
        checkJobNo(shopCode, employee.getJobNo(), employee.getUserCode());
        validateAssignedRoles(shopCode, SecurityUtils.getUserCode(), employee.getRoleCodes());
        // 姓名、手机号和员工状态不允许在这里修改，一律沿用原值
        employee.setStatus(existing.getStatus());
        employee.setUpdateBy(username);
        int rows = mapper.updateMembership(shopCode, employee);
        replaceMemberRoles(shopCode, employee.getUserCode(), employee.getRoleCodes());
        return rows;
    }

    @Override
    @Transactional
    public int updateMyProfile(String shopCode, TenantEmployee employee, String username)
    {
        requireShop(shopCode);
        String userCode = SecurityUtils.getUserCode();
        TenantEmployee existing = selectEmployeeById(shopCode, userCode);
        if (StringUtils.isEmpty(employee.getNickName()))
        {
            throw new ServiceException("Employee name is required");
        }
        if (employee.getNickName().length() > 30)
        {
            throw new ServiceException("Employee name cannot exceed 30 characters");
        }
        String phonenumber = normalizePhone(employee.getPhonenumber());
        validatePhoneRequired(phonenumber);
        if (StringUtils.isNotEmpty(employee.getJobNo()) && employee.getJobNo().length() > 50)
        {
            throw new ServiceException("Employee number cannot exceed 50 characters");
        }
        checkJobNo(shopCode, employee.getJobNo(), userCode);
        SysUser globalUser = new SysUser();
        globalUser.setUserCode(userCode);
        globalUser.setPhonenumber(phonenumber);
        if (!userService.checkPhoneUnique(globalUser))
        {
            throw new ServiceException("Mobile number already exists");
        }
        mapper.updateGlobalUserProfile(userCode, employee.getNickName(), phonenumber, username);
        return mapper.updateMembershipSelf(shopCode, userCode, employee.getJobNo(), username);
    }

    @Override
    @Transactional
    public int deleteEmployees(String shopCode, String[] userCodes)
    {
        requireShop(shopCode);
        for (String userCode : userCodes)
        {
            TenantEmployee employee = selectEmployeeById(shopCode, userCode);
            if (Boolean.TRUE.equals(employee.getAdministrator()))
            {
                throw new ServiceException("绑定商家管理员角色的员工不能删除");
            }
        }

        int rows = 0;
        for (String userCode : userCodes)
        {
            mapper.deleteMemberPosts(shopCode, userCode);
            mapper.deleteMemberRoles(shopCode, userCode);
            rows += mapper.deleteMembership(shopCode, userCode);
            deleteGlobalUserIfOrphan(userCode);
        }
        return rows;
    }

    @Override
    public List<TenantRole> selectRoleList(String shopCode, TenantRole role)
    {
        requireShop(shopCode);
        Set<String> actorMenus = actorFunCodes(shopCode, SecurityUtils.getUserCode());
        return mapper.selectRoleList(shopCode, role).stream()
                .filter(item -> isRoleWithinActorScope(shopCode, actorMenus, item.getRoleCode()))
                .collect(Collectors.toList());
    }

    @Override
    public List<TenantRole> selectAssignableRoles(String shopCode, String actorUserCode)
    {
        Set<String> assignableMenus = actorFunCodes(shopCode, actorUserCode);
        return mapper.selectRoleList(shopCode, new TenantRole()).stream()
                .filter(role -> !role.isSystemRole())
                .filter(role -> isRoleWithinActorScope(shopCode, assignableMenus, role.getRoleCode()))
                .collect(Collectors.toList());
    }

    @Override
    public TenantRole selectRoleByCode(String shopCode, String roleCode)
    {
        requireShop(shopCode);
        TenantRole role = mapper.selectRoleByCode(shopCode, roleCode);
        if (role == null)
        {
            throw new ServiceException("Role does not exist in the current shop");
        }
        if (!SecurityUtils.isAdmin(SecurityUtils.getUserCode()))
        {
            ensureRoleWithinActorScope(shopCode, SecurityUtils.getUserCode(), role.getRoleCode());
        }
        role.setFunCodes(mapper.selectRoleFunCodes(shopCode, roleCode).toArray(new String[0]));
        return role;
    }

    @Override
    @Transactional
    public int insertRole(String shopCode, String actorUserCode, TenantRole role, String username)
    {
        requireShop(shopCode);
        checkRoleUnique(shopCode, role, null);
        validateMenuSubset(shopCode, actorUserCode, role.getFunCodes());
        role.setRoleCode(roleCodeGenerator.generate());
        role.setDataScope(Constants.Dept.DATA_SCOPE_ALL);
        role.setStatus(StringUtils.defaultIfEmpty(role.getStatus(), UserConstants.ROLE_NORMAL));
        role.setCreateBy(username);
        int rows = mapper.insertRole(shopCode, role);
        replaceRoleMenus(shopCode, role.getRoleCode(), role.getFunCodes());
        return rows;
    }

    @Override
    @Transactional
    public int updateRole(String shopCode, String actorUserCode, TenantRole role, String username)
    {
        TenantRole existing = selectRoleByCode(shopCode, role.getRoleCode());
        ensureEditableRole(existing);
        ensureRoleWithinActorScope(shopCode, actorUserCode, role.getRoleCode());
        checkRoleUnique(shopCode, role, role.getRoleCode());
        role.setUpdateBy(username);
        int rows = mapper.updateRole(shopCode, role);
        if (role.getFunCodes() != null)
        {
            validateMenuSubset(shopCode, actorUserCode, role.getFunCodes());
            replaceRoleMenus(shopCode, role.getRoleCode(), role.getFunCodes());
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteRoles(String shopCode, String[] roleCodes, String username)
    {
        int rows = 0;
        for (String roleCode : roleCodes)
        {
            TenantRole role = selectRoleByCode(shopCode, roleCode);
            ensureEditableRole(role);
            ensureRoleWithinActorScope(shopCode, SecurityUtils.getUserCode(), roleCode);
            if (mapper.countRoleMembers(shopCode, roleCode) > 0)
            {
                throw new ServiceException("Role is assigned to employees and cannot be deleted: " + role.getRoleName());
            }
            mapper.deleteRoleMenusScoped(shopCode, roleCode, false);
            rows += mapper.deleteRole(shopCode, roleCode, username);
        }
        return rows;
    }

    @Override
    public List<TreeSelect> selectAssignableMenuTree(String shopCode, String actorUserCode, boolean superAdmin)
    {
        // 商家有的权限全部列出来，勾没勾才决定角色能不能用。
        // 这里<b>不再</b>按 perms 前缀筛掉采购：树上不出现就等于没有授权入口，
        // 商家想建「采购员」这种角色时会发现连勾都没得勾。能不能授出去由下面两道关管：
        // 非超管受 validateMenuSubset 限制（只能授出自己有的），超管受「必须属于本商家」限制。
        String[] ids = (superAdmin ? tenantShopService.selectFunCodesByShop(shopCode)
                : tenantShopService.selectFunCodesByMember(shopCode, actorUserCode)).toArray(new String[0]);
        if (ids.length == 0)
        {
            return new ArrayList<>();
        }
        return menuService.buildMenuTreeSelect(mapper.selectMenusByFunCodes(ids));
    }

    @Override
    public List<String> selectRoleFunCodes(String shopCode, String roleCode)
    {
        selectRoleByCode(shopCode, roleCode);
        ensureRoleWithinActorScope(shopCode, SecurityUtils.getUserCode(), roleCode);
        return mapper.selectRoleFunCodes(shopCode, roleCode);
    }

    @Override
    @Transactional
    public int updateRolePermissions(String shopCode, String actorUserCode, String roleCode, String[] funCodes,
            boolean superAdmin)
    {
        TenantRole role = selectRoleByCode(shopCode, roleCode);
        if (role.isSystemRole())
        {
            throw new ServiceException("The built-in shop administrator role must be configured on the platform shop permission page");
        }
        if (!superAdmin)
        {
            ensureRoleWithinActorScope(shopCode, actorUserCode, roleCode);
            validateMenuSubset(shopCode, actorUserCode, funCodes);
        }
        else
        {
            // 超管不受「只能授出自己有的」限制，但授出去的必须是本商家开通了的菜单
            if (!tenantShopService.selectFunCodesByShop(shopCode).containsAll(
                    funCodes == null ? List.of() : Arrays.asList(funCodes)))
            {
                throw new ServiceException("Role permissions must belong to the current shop");
            }
        }
        return replaceRoleMenus(shopCode, roleCode, funCodes, superAdmin);
    }

    private List<SysDept> buildDeptTree(List<SysDept> depts)
    {
        Map<Long, SysDept> nodes = new HashMap<>();
        for (SysDept dept : depts)
        {
            dept.setChildren(new ArrayList<>());
            nodes.put(dept.getDeptId(), dept);
        }
        List<SysDept> roots = new ArrayList<>();
        for (SysDept dept : depts)
        {
            SysDept parent = nodes.get(dept.getParentId());
            if (parent == null)
            {
                roots.add(dept);
            }
            else
            {
                parent.getChildren().add(dept);
            }
        }
        return roots;
    }

    private void checkDeptNameUnique(String shopCode, SysDept dept, Long excludeDeptId)
    {
        if (mapper.checkDeptNameUnique(shopCode, dept.getParentId(), dept.getDeptName(), excludeDeptId) > 0)
        {
            throw new ServiceException("Department name already exists under the selected parent");
        }
    }

    private boolean containsAncestor(String ancestors, Long deptId)
    {
        return StringUtils.isNotEmpty(ancestors)
                && Arrays.asList(ancestors.split(",")).contains(String.valueOf(deptId));
    }

    private void requireDept(String shopCode, Long deptId)
    {
        if (deptId == null || mapper.selectDeptById(shopCode, deptId) == null)
        {
            throw new ServiceException("Department does not exist in the current shop");
        }
    }

    private void checkJobNo(String shopCode, String jobNo, String excludeUserCode)
    {
        if (StringUtils.isNotEmpty(jobNo) && mapper.checkJobNoUnique(shopCode, jobNo, excludeUserCode) > 0)
        {
            throw new ServiceException("Employee number already exists in the current shop");
        }
    }

    private void validateAssignedRoles(String shopCode, String actorUserCode, String[] roleCodes)
    {
        if (StringUtils.isEmpty(roleCodes))
        {
            return;
        }
        Set<String> assignableRoleCodes = selectAssignableRoles(shopCode, actorUserCode).stream()
                .map(TenantRole::getRoleCode).collect(Collectors.toSet());
        if (!assignableRoleCodes.containsAll(Arrays.asList(roleCodes)))
        {
            throw new ServiceException("One or more roles cannot be assigned by the current operator");
        }
    }

    private void validateTargetEmployeeRoles(String shopCode, String actorUserCode, String[] targetRoleCodes)
    {
        if (StringUtils.isEmpty(targetRoleCodes))
        {
            return;
        }
        Set<String> manageableRoleCodes = selectAssignableRoles(shopCode, actorUserCode).stream()
                .map(TenantRole::getRoleCode).collect(Collectors.toSet());
        if (!manageableRoleCodes.containsAll(Arrays.asList(targetRoleCodes)))
        {
            throw new ServiceException("The target employee has roles outside the current operator permission scope");
        }
    }

    private int replaceMemberRoles(String shopCode, String userCode, String[] roleCodes)
    {
        mapper.deleteMemberRoles(shopCode, userCode);
        return StringUtils.isEmpty(roleCodes) ? 1 : mapper.batchInsertMemberRoles(shopCode, userCode, roleCodes);
    }

    private void deleteGlobalUserIfOrphan(String userCode)
    {
        if (userCode == null || SecurityUtils.isAdmin(userCode) || mapper.countActiveMembershipsByUserCode(userCode) > 0
                || mapper.countPlatformBindingsByUserCode(userCode) > 0)
        {
            return;
        }
        mapper.deleteAllMemberPostsByUserCode(userCode);
        mapper.deleteAllMemberRolesByUserCode(userCode);
        mapper.deleteAllMembershipsByUserCode(userCode);
        mapper.deleteNoticeReadsByUserCode(userCode);
        mapper.deleteGlobalUserByCode(userCode);
    }

    private void checkRoleUnique(String shopCode, TenantRole role, String excludeRoleCode)
    {
        if (StringUtils.equalsIgnoreCase(Constants.SUPER_ADMIN, StringUtils.trim(role.getRoleKey())))
        {
            throw new ServiceException("Role key is reserved by the platform");
        }
        if (mapper.checkRoleNameUnique(shopCode, role.getRoleName(), excludeRoleCode) > 0)
        {
            throw new ServiceException("Role name already exists in the current shop");
        }
        if (mapper.checkRoleKeyUnique(shopCode, role.getRoleKey(), excludeRoleCode) > 0)
        {
            throw new ServiceException("Role key already exists in the current shop");
        }
    }

    private void validateMenuSubset(String shopCode, String actorUserCode, String[] funCodes)
    {
        if (StringUtils.isEmpty(funCodes))
        {
            return;
        }
        // 授权范围只有一条规则：不能授出自己都没有的权限。
        // 曾经这里还挡过「采购权限只能给内置商家管理员」，已经去掉——
        // 商家要建采购员这类角色，权限树上必须有采购这个入口，否则连勾都没得勾。
        Set<String> actorMenus = actorFunCodes(shopCode, actorUserCode);
        if (!actorMenus.containsAll(Arrays.asList(funCodes)))
        {
            throw new ServiceException("Role permissions must be a subset of the current operator permissions");
        }
    }

    private int replaceRoleMenus(String shopCode, String roleCode, String[] funCodes)
    {
        return replaceRoleMenus(shopCode, roleCode, funCodes, false);
    }

    private int replaceRoleMenus(String shopCode, String roleCode, String[] funCodes, boolean includeSystem)
    {
        mapper.deleteRoleMenusScoped(shopCode, roleCode, includeSystem);
        return StringUtils.isEmpty(funCodes) ? 1
                : mapper.batchInsertRoleMenusScoped(shopCode, roleCode, funCodes, includeSystem);
    }

    private void ensureEditableRole(TenantRole role)
    {
        if (role.isSystemRole())
        {
            throw new ServiceException("The built-in shop administrator role cannot be changed");
        }
    }

    private boolean isRoleWithinActorScope(String shopCode, Set<String> actorMenus, String roleCode)
    {
        return actorMenus.containsAll(mapper.selectRoleFunCodes(shopCode, roleCode));
    }

    private void ensureRoleWithinActorScope(String shopCode, String actorUserCode, String roleCode)
    {
        Set<String> actorMenus = actorFunCodes(shopCode, actorUserCode);
        if (!isRoleWithinActorScope(shopCode, actorMenus, roleCode))
        {
            throw new ServiceException("The target role exceeds the current operator permission scope");
        }
    }

    private Set<String> actorFunCodes(String shopCode, String actorUserCode)
    {
        return SecurityUtils.isAdmin(actorUserCode)
                ? tenantShopService.selectFunCodesByShop(shopCode)
                : tenantShopService.selectFunCodesByMember(shopCode, actorUserCode);
    }

    private void requireShop(String shopCode)
    {
        if (StringUtils.isEmpty(shopCode))
        {
            throw new ServiceException("Please select a shop first");
        }
    }

    private String normalizePhone(String phonenumber)
    {
        return StringUtils.trim(phonenumber);
    }

    private void validatePhoneRequired(String phonenumber)
    {
        if (phonenumber == null || !Pattern.matches("^1[3-9]\\d{9}$", phonenumber))
        {
            throw new ServiceException("Mobile number must be an 11-digit mainland China mobile number");
        }
    }
}

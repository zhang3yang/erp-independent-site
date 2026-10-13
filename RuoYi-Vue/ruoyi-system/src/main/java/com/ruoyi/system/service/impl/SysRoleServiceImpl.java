package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.system.domain.SysRoleDept;
import com.ruoyi.system.domain.SysRoleMenu;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysRoleDeptMapper;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysRoleMenuMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysRoleService;

/**
 * 角色 业务层处理
 * 
 * @author ruoyi
 */
@Service
public class SysRoleServiceImpl implements ISysRoleService
{
    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleDeptMapper roleDeptMapper;

    @Autowired
    private RoleCodeGenerator roleCodeGenerator;

    /**
     * 根据条件分页查询角色数据
     * 
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<SysRole> selectRoleList(SysRole role)
    {
        return roleMapper.selectRoleList(role);
    }

    /**
     * 根据用户编码查询角色
     * 
     * @param userCode 用户编码
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRolesByUserCode(String userCode)
    {
        List<SysRole> userRoles = roleMapper.selectRolePermissionByUserCode(userCode);
        List<SysRole> roles = selectRoleAll();
        for (SysRole role : roles)
        {
            for (SysRole userRole : userRoles)
            {
                if (StringUtils.equals(role.getRoleCode(), userRole.getRoleCode()))
                {
                    role.setFlag(true);
                    break;
                }
            }
        }
        return roles;
    }

    /**
     * 根据用户编码查询权限
     * 
     * @param userCode 用户编码
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserCode(String userCode)
    {
        List<SysRole> perms = roleMapper.selectRolePermissionByUserCode(userCode);
        Set<String> permsSet = new HashSet<>();
        for (SysRole perm : perms)
        {
            if (StringUtils.isNotNull(perm))
            {
                permsSet.addAll(Arrays.asList(perm.getRoleKey().trim().split(",")));
            }
        }
        return permsSet;
    }

    /**
     * 查询所有角色
     * 
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRoleAll()
    {
        return SpringUtils.getAopProxy(this).selectRoleList(new SysRole());
    }

    /**
     * 根据用户编码获取角色选择框列表
     * 
     * @param userCode 用户编码
     * @return 选中角色ID列表
     */
    @Override
    public List<String> selectRoleListByUserCode(String userCode)
    {
        return roleMapper.selectRoleListByUserCode(userCode);
    }

    /**
     * 通过角色ID查询角色
     * 
     * @param roleCode 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRole selectRoleByCode(String roleCode)
    {
        return roleMapper.selectRoleByCode(roleCode);
    }

    /**
     * 校验角色名称是否唯一
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRole role)
    {
        String roleCode = StringUtils.isNull(role.getRoleCode()) ? "" : role.getRoleCode();
        SysRole info = roleMapper.checkRoleNameUnique(role.getRoleName());
        if (StringUtils.isNotNull(info) && !StringUtils.equals(info.getRoleCode(), roleCode))
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验角色权限是否唯一
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleKeyUnique(SysRole role)
    {
        String roleCode = StringUtils.isNull(role.getRoleCode()) ? "" : role.getRoleCode();
        SysRole info = roleMapper.checkRoleKeyUnique(role.getRoleKey());
        if (StringUtils.isNotNull(info) && !StringUtils.equals(info.getRoleCode(), roleCode))
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验角色是否允许操作
     * 
     * @param role 角色信息
     */
    @Override
    public void checkRoleAllowed(SysRole role)
    {
        if (StringUtils.isNotNull(role.getRoleCode()) && role.isAdmin())
        {
            throw new ServiceException("不允许操作超级管理员角色");
        }
    }

    /**
     * 校验角色是否有数据权限
     * 
     * @param roleCodes 角色id
     */
    @Override
    public void checkRoleDataScope(String... roleCodes)
    {
        if (StringUtils.isEmpty(roleCodes))
        {
            return;
        }
        long distinctRoleCount = Arrays.stream(roleCodes).filter(java.util.Objects::nonNull).distinct().count();
        if (distinctRoleCount != roleCodes.length || roleMapper.countPlatformRoles(roleCodes) != roleCodes.length)
        {
            throw new ServiceException("平台用户只能操作平台角色");
        }
        if (!SecurityUtils.isAdmin())
        {
            for (String roleCode : roleCodes)
            {
                SysRole role = new SysRole();
                role.setRoleCode(roleCode);
                List<SysRole> roles = SpringUtils.getAopProxy(this).selectRoleList(role);
                if (StringUtils.isEmpty(roles))
                {
                    throw new ServiceException("没有权限访问角色数据！");
                }
            }
        }
    }

    /**
     * 通过角色ID查询角色使用数量
     * 
     * @param roleCode 角色ID
     * @return 结果
     */
    @Override
    public int countUserRoleByRoleCode(String roleCode)
    {
        return userRoleMapper.countUserRoleByRoleCode(roleCode);
    }

    /**
     * 新增保存角色信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertRole(SysRole role)
    {
        checkPlatformMenus(role.getFunCodes());
        role.setRoleCode(roleCodeGenerator.generate());
        // 新增角色信息
        roleMapper.insertRole(role);
        return insertRoleMenu(role);
    }

    /**
     * 修改保存角色信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateRole(SysRole role)
    {
        checkPlatformRole(role.getRoleCode());
        checkPlatformMenus(role.getFunCodes());
        // 修改角色信息
        int rows = roleMapper.updateRole(role);
        if (rows == 0)
        {
            throw new ServiceException("平台角色不存在或已停用");
        }
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleCode(role.getRoleCode());
        insertRoleMenu(role);
        return rows;
    }

    /**
     * 修改角色状态
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public int updateRoleStatus(SysRole role)
    {
        checkPlatformRole(role.getRoleCode());
        return roleMapper.updateRole(role);
    }

    /**
     * 修改数据权限信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional
    public int authDataScope(SysRole role)
    {
        checkPlatformRole(role.getRoleCode());
        // 修改角色信息
        int rows = roleMapper.updateRole(role);
        if (rows == 0)
        {
            throw new ServiceException("平台角色不存在或已停用");
        }
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleCode(role.getRoleCode());
        // 新增角色和部门信息（数据权限）
        insertRoleDept(role);
        return rows;
    }

    /**
     * 新增角色菜单信息
     * 
     * @param role 角色对象
     */
    public int insertRoleMenu(SysRole role)
    {
        int rows = 1;
        // 新增用户与角色管理
        List<SysRoleMenu> list = new ArrayList<SysRoleMenu>();
        for (String funCode : role.getFunCodes())
        {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleCode(role.getRoleCode());
            rm.setFunCode(funCode);
            list.add(rm);
        }
        if (list.size() > 0)
        {
            rows = roleMenuMapper.batchRoleMenu(list);
        }
        return rows;
    }

    /**
     * 新增角色部门信息(数据权限)
     *
     * @param role 角色对象
     */
    public int insertRoleDept(SysRole role)
    {
        int rows = 1;
        // 新增角色与部门（数据权限）管理
        List<SysRoleDept> list = new ArrayList<SysRoleDept>();
        for (Long deptId : role.getDeptIds())
        {
            SysRoleDept rd = new SysRoleDept();
            rd.setRoleCode(role.getRoleCode());
            rd.setDeptId(deptId);
            list.add(rd);
        }
        if (list.size() > 0)
        {
            rows = roleDeptMapper.batchRoleDept(list);
        }
        return rows;
    }

    /**
     * 通过角色ID删除角色
     * 
     * @param roleCode 角色ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteRoleByCode(String roleCode)
    {
        checkPlatformRole(roleCode);
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleCode(roleCode);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleCode(roleCode);
        return roleMapper.deleteRoleByCode(roleCode);
    }

    /**
     * 批量删除角色信息
     * 
     * @param roleCodes 需要删除的角色ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteRoleByCodes(String[] roleCodes)
    {
        for (String roleCode : roleCodes)
        {
            checkRoleAllowed(new SysRole(roleCode));
            checkRoleDataScope(roleCode);
            SysRole role = selectRoleByCode(roleCode);
            if (countUserRoleByRoleCode(roleCode) > 0)
            {
                throw new ServiceException(String.format("%1$s已分配,不能删除", role.getRoleName()));
            }
        }
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenu(roleCodes);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDept(roleCodes);
        return roleMapper.deleteRoleByCodes(roleCodes);
    }

    /**
     * 取消授权用户角色
     * 
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    @Override
    public int deleteAuthUser(SysUserRole userRole)
    {
        checkPlatformRole(userRole.getRoleCode());
        return userRoleMapper.deleteUserRoleInfo(userRole);
    }

    /**
     * 批量取消授权用户角色
     * 
     * @param roleCode 角色ID
     * @param userCodes 需要取消授权的用户数据ID
     * @return 结果
     */
    @Override
    public int deleteAuthUsers(String roleCode, String[] userCodes)
    {
        checkPlatformRole(roleCode);
        return userRoleMapper.deleteUserRoleInfos(roleCode, userCodes);
    }

    /**
     * 批量选择授权用户角色
     * 
     * @param roleCode 角色ID
     * @param userCodes 需要授权的用户数据ID
     * @return 结果
     */
    @Override
    public int insertAuthUsers(String roleCode, String[] userCodes)
    {
        checkPlatformRole(roleCode);
        // 新增用户与角色管理
        List<SysUserRole> list = new ArrayList<SysUserRole>();
        for (String userCode : userCodes)
        {
            SysUserRole ur = new SysUserRole();
            ur.setUserCode(userCode);
            ur.setRoleCode(roleCode);
            list.add(ur);
        }
        return userRoleMapper.batchUserRole(list);
    }

    private SysRole checkPlatformRole(String roleCode)
    {
        if (StringUtils.isNull(roleCode))
        {
            throw new ServiceException("平台角色不能为空");
        }
        SysRole role = roleMapper.selectRoleByCode(roleCode);
        if (StringUtils.isNull(role))
        {
            throw new ServiceException("平台角色不存在或无权操作");
        }
        return role;
    }

    private void checkPlatformMenus(String[] funCodes)
    {
        if (StringUtils.isEmpty(funCodes))
        {
            return;
        }
        long distinctMenuCount = Arrays.stream(funCodes).filter(java.util.Objects::nonNull).distinct().count();
        if (distinctMenuCount != funCodes.length || roleMapper.countPlatformMenus(funCodes) != funCodes.length)
        {
            throw new ServiceException("平台角色只能分配平台菜单");
        }
    }
}

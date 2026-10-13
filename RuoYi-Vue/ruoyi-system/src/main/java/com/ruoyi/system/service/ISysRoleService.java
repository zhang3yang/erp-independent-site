package com.ruoyi.system.service;

import java.util.List;
import java.util.Set;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.system.domain.SysUserRole;

/**
 * 角色业务层
 * 
 * @author ruoyi
 */
public interface ISysRoleService
{
    /**
     * 根据条件分页查询角色数据
     * 
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    public List<SysRole> selectRoleList(SysRole role);

    /**
     * 根据用户编码查询角色列表
     * 
     * @param userCode 用户编码
     * @return 角色列表
     */
    public List<SysRole> selectRolesByUserCode(String userCode);

    /**
     * 根据用户编码查询角色权限
     * 
     * @param userCode 用户编码
     * @return 权限列表
     */
    public Set<String> selectRolePermissionByUserCode(String userCode);

    /**
     * 查询所有角色
     * 
     * @return 角色列表
     */
    public List<SysRole> selectRoleAll();

    /**
     * 根据用户编码获取角色选择框列表
     * 
     * @param userCode 用户编码
     * @return 选中角色ID列表
     */
    public List<String> selectRoleListByUserCode(String userCode);

    /**
     * 通过角色ID查询角色
     * 
     * @param roleCode 角色ID
     * @return 角色对象信息
     */
    public SysRole selectRoleByCode(String roleCode);

    /**
     * 校验角色名称是否唯一
     * 
     * @param role 角色信息
     * @return 结果
     */
    public boolean checkRoleNameUnique(SysRole role);

    /**
     * 校验角色权限是否唯一
     * 
     * @param role 角色信息
     * @return 结果
     */
    public boolean checkRoleKeyUnique(SysRole role);

    /**
     * 校验角色是否允许操作
     * 
     * @param role 角色信息
     */
    public void checkRoleAllowed(SysRole role);

    /**
     * 校验角色是否有数据权限
     * 
     * @param roleCodes 角色id
     */
    public void checkRoleDataScope(String... roleCodes);

    /**
     * 通过角色ID查询角色使用数量
     * 
     * @param roleCode 角色ID
     * @return 结果
     */
    public int countUserRoleByRoleCode(String roleCode);

    /**
     * 新增保存角色信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    public int insertRole(SysRole role);

    /**
     * 修改保存角色信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    public int updateRole(SysRole role);

    /**
     * 修改角色状态
     * 
     * @param role 角色信息
     * @return 结果
     */
    public int updateRoleStatus(SysRole role);

    /**
     * 修改数据权限信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    public int authDataScope(SysRole role);

    /**
     * 通过角色ID删除角色
     * 
     * @param roleCode 角色ID
     * @return 结果
     */
    public int deleteRoleByCode(String roleCode);

    /**
     * 批量删除角色信息
     * 
     * @param roleCodes 需要删除的角色ID
     * @return 结果
     */
    public int deleteRoleByCodes(String[] roleCodes);

    /**
     * 取消授权用户角色
     * 
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    public int deleteAuthUser(SysUserRole userRole);

    /**
     * 批量取消授权用户角色
     * 
     * @param roleCode 角色ID
     * @param userCodes 需要取消授权的用户数据ID
     * @return 结果
     */
    public int deleteAuthUsers(String roleCode, String[] userCodes);

    /**
     * 批量选择授权用户角色
     * 
     * @param roleCode 角色ID
     * @param userCodes 需要删除的用户数据ID
     * @return 结果
     */
    public int insertAuthUsers(String roleCode, String[] userCodes);
}

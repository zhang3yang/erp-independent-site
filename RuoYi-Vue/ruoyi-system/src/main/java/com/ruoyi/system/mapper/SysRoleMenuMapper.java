package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.SysRoleMenu;

/**
 * 角色与菜单关联表 数据层
 * 
 * @author ruoyi
 */
public interface SysRoleMenuMapper
{
    /**
     * 查询菜单使用数量
     * 
     * @param funCode 功能编码
     * @return 结果
     */
    public int checkMenuExistRole(String funCode);

    /**
     * 通过角色ID删除角色和菜单关联
     * 
     * @param roleCode 角色ID
     * @return 结果
     */
    public int deleteRoleMenuByRoleCode(String roleCode);

    /**
     * 批量删除角色菜单关联信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteRoleMenu(String[] ids);

    /**
     * 批量新增角色菜单信息
     * 
     * @param roleMenuList 角色菜单列表
     * @return 结果
     */
    public int batchRoleMenu(List<SysRoleMenu> roleMenuList);
}

package com.ruoyi.system.service;

import java.util.List;
import java.util.Set;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.system.domain.vo.RouterVo;

/**
 * 菜单 业务层
 * 
 * @author ruoyi
 */
public interface ISysMenuService
{
    /**
     * 根据用户查询系统菜单列表
     * 
     * @param userCode 用户编码
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuList(String userCode);

    /**
     * 根据用户查询系统菜单列表
     * 
     * @param menu 菜单信息
     * @param userCode 用户编码
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuList(SysMenu menu, String userCode);

    /**
     * 根据用户编码查询权限
     * 
     * @param userCode 用户编码
     * @return 权限列表
     */
    public Set<String> selectMenuPermsByUserCode(String userCode);

    /**
     * 根据角色ID查询权限
     * 
     * @param roleCode 角色ID
     * @return 权限列表
     */
    public Set<String> selectMenuPermsByRoleCode(String roleCode);

    /**
     * 根据用户编码查询菜单树信息
     * 
     * @param userCode 用户编码
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuTreeByUserCode(String userCode);

    /**
     * 根据角色ID查询菜单树信息
     * 
     * @param roleCode 角色ID
     * @return 选中菜单列表
     */
    public List<String> selectMenuListByRoleCode(String roleCode);

    /**
     * 构建前端路由所需要的菜单
     * 
     * @param menus 菜单列表
     * @return 路由列表
     */
    public List<RouterVo> buildMenus(List<SysMenu> menus);

    /**
     * 构建前端所需要树结构
     * 
     * @param menus 菜单列表
     * @return 树结构列表
     */
    public List<SysMenu> buildMenuTree(List<SysMenu> menus);

    /**
     * 构建前端所需要下拉树结构
     * 
     * @param menus 菜单列表
     * @return 下拉树结构列表
     */
    public List<TreeSelect> buildMenuTreeSelect(List<SysMenu> menus);

    /**
     * 根据功能编码查询信息
     * 
     * @param funCode 功能编码
     * @return 菜单信息
     */
    public SysMenu selectMenuByFunCode(String funCode);

    /**
     * 是否存在菜单子节点
     * 
     * @param funCode 功能编码
     * @return 结果 true 存在 false 不存在
     */
    public boolean hasChildByFunCode(String funCode);

    /**
     * 查询菜单是否存在角色
     * 
     * @param funCode 功能编码
     * @return 结果 true 存在 false 不存在
     */
    public boolean checkMenuExistRole(String funCode);

    /**
     * 新增保存菜单信息
     * 
     * @param menu 菜单信息
     * @return 结果
     */
    public int insertMenu(SysMenu menu);

    /**
     * 修改保存菜单信息
     * 
     * @param menu 菜单信息
     * @return 结果
     */
    public int updateMenu(SysMenu menu);

    /**
     * 保存菜单排序
     * 
     * @param funCodes 功能编码
     * @param orderNums 排序ID
     */
    public void updateMenuSort(String[] funCodes, String[] orderNums);

    /**
     * 删除菜单管理信息
     * 
     * @param funCode 功能编码
     * @return 结果
     */
    public int deleteMenuByFunCode(String funCode);

    /**
     * 校验菜单名称是否唯一
     * 
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean checkMenuNameUnique(SysMenu menu);

    /**
     * 校验路由组合是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean checkRouteConfigUnique(SysMenu menu);
}

package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.common.core.domain.entity.SysMenu;

/**
 * 菜单表 数据层
 *
 * @author ruoyi
 */
public interface SysMenuMapper
{
    /**
     * 查询系统菜单列表
     *
     * @param menu 菜单信息
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuList(SysMenu menu);

    /**
     * 根据用户所有权限
     *
     * @return 权限列表
     */
    public List<String> selectMenuPerms();

    /**
     * 根据用户查询系统菜单列表
     *
     * @param menu 菜单信息
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuListByUserCode(SysMenu menu);

    /**
     * 根据角色ID查询权限
     * 
     * @param roleCode 角色ID
     * @return 权限列表
     */
    public List<String> selectMenuPermsByRoleCode(String roleCode);

    /**
     * 根据用户编码查询权限
     *
     * @param userCode 用户编码
     * @return 权限列表
     */
    public List<String> selectMenuPermsByUserCode(String userCode);

    /**
     * 查询全部菜单
     *
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuTreeAll();

    /**
     * 根据用户编码查询菜单
     *
     * @param userCode 用户编码
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuTreeByUserCode(String userCode);

    /**
     * 根据角色ID查询菜单树信息
     * 
     * @param roleCode 角色ID
     * @param menuCheckStrictly 菜单树选择项是否关联显示
     * @return 选中菜单列表
     */
    public List<String> selectMenuListByRoleCode(@Param("roleCode") String roleCode, @Param("menuCheckStrictly") boolean menuCheckStrictly);

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
     * @return 结果
     */
    public int hasChildByFunCode(String funCode);

    /**
     * 新增菜单信息
     *
     * @param menu 菜单信息
     * @return 结果
     */
    public int insertMenu(SysMenu menu);

    /**
     * 修改菜单信息
     *
     * @param menu 菜单信息
     * @return 结果
     */
    public int updateMenu(SysMenu menu);

    /**
     * 保存菜单排序
     * 
     * @param menu 菜单信息
     */
    public void updateMenuSort(SysMenu menu);

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
     * @param menuName 菜单名称
     * @param parentId 父功能编码
     * @return 结果
     */
    public SysMenu checkMenuNameUnique(@Param("menuName") String menuName, @Param("parentId") String parentId);

    /**
     * 根据路由路径或名称查询菜单信息（用于唯一性校验）
     *
     * @param path 路由地址
     * @param routeName 路由名称
     * @return 匹配的菜单列表
     */
    public List<SysMenu> selectMenusByPathOrRouteName(@Param("path") String path, @Param("routeName") String routeName);
}

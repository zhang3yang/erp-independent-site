package com.ruoyi.common.constant;

/**
 * 商家（租户）业务权限的前缀白名单。
 *
 * <p>平台超管以 X-Shop-Code 代管商家、或用户切换到某个商家时，权限集要按该商家的启用菜单
 * 重算，并且只保留业务模块的权限——系统管理类权限不随商家走。判断「是不是业务权限」
 * 靠的就是这份前缀名单。</p>
 *
 * <p><b>新增业务模块必须在这里加前缀</b>，否则该模块所有接口对商家一律 403：
 * 权限串虽然在 sys_menu 里，但会被这层过滤丢掉。</p>
 *
 * <p>这份名单原先散在 4 个文件 5 处硬编码里（PermissionService 两处、SysLoginController、
 * TenantShopController、TenantShopServiceImpl），加订单模块时漏改一处就会出现
 * 「菜单有、接口 403」的怪现象，所以收敛到这里。</p>
 */
public final class TenantPermissionPrefixes
{
    /**
     * 商家业务权限前缀。
     *
     * <p>{@code tenant:} 是商家自身的管理权限（员工、角色等），其余是各业务模块。</p>
     *
     * <p>{@code price:} 是个例外：它一个页面都没有，只是挂在隐藏菜单「价格可见性」下的
     * 两个纯权限点（{@code price:cost} / {@code price:sale}），管的是金额字段在接口出口
     * 遮不遮。它必须在这份名单里，否则商家侧的权限集会把它过滤掉，
     * 结果是所有人都看不到价格。见 {@code docs/金额按岗位可见.md}。</p>
     *
     * <p>{@code dashboard:} 同理，也是一个没有页面的纯权限点（{@code dashboard:overview:query}），
     * 管的是首页那块经营看板出不出来。</p>
     */
    public static final String[] ALL = {
        "tenant:", "purchase:", "inventory:", "production:", "finance:", "order:", "site:", "price:",
        "dashboard:"
    };

    /**
     * 业务模块前缀，不含 {@code tenant:}。
     *
     * <p>用于判断某个菜单是不是「可以授权给商家的业务菜单」——商家自身的管理菜单
     * 由系统固定下发，不走授权。</p>
     */
    public static final String[] BUSINESS_ONLY = {
        "purchase:", "inventory:", "production:", "finance:", "order:", "site:", "price:",
        "dashboard:"
    };

    private TenantPermissionPrefixes()
    {
    }

    /** 是否属于商家业务权限（含 tenant: 前缀）。 */
    public static boolean isTenantPermission(String permission)
    {
        return matches(permission, ALL);
    }

    private static boolean matches(String permission, String[] prefixes)
    {
        if (permission == null)
        {
            return false;
        }
        for (String prefix : prefixes)
        {
            if (permission.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }
}

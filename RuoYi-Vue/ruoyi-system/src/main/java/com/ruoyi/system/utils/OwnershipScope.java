package com.ruoyi.system.utils;

import java.util.Set;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 横向权限（数据行级）：没有「查看全部」权限的人，只看得到归属于自己的单据。
 *
 * <p>见 {@code docs/订单只看自己的单（业务员权限）.md}。销售订单、发货批次、销售退货、销售补发
 * 四个页面各有一个「查看全部」权限点，判权规则完全一样，收在这里一份，
 * 免得同一套微妙的默认值抄四遍还抄歪。</p>
 *
 * <h3>⚠ 两个默认值，方向相反，都不是随手定的</h3>
 *
 * <ul>
 *   <li><b>没有登录上下文 → 放行。</b>这些方法都挂在
 *       {@code @PreAuthorize("@ss.hasBusinessShop() and ...")} 后面，而
 *       {@code hasBusinessShop()} 自己就会取登录人、取不到直接抛——真实请求根本走不到
 *       没有登录上下文的那一步。剩下会走到的只有单元测试和内部调用，把它们筛成
 *       「一条都看不到」是拿业务中断换一个不存在的安全。</li>
 *   <li><b>有登录上下文但取不到账号 → 拒绝。</b>判权代码的失败方式必须是「拒绝」，
 *       不能是 NPE 崩成 500，更不能是放行。</li>
 * </ul>
 *
 * <h3>⚠ 只管「自己人看自己的单」，不要拿它挡跨模块调用</h3>
 *
 * <p>仓库发货、财务回款、生产完工都要按单号取业务员的单据，那些岗位手里没有也不该有
 * 这些「查看全部」权限。<b>在跨模块的取数路径上调用本类，就是直接把发货和回款搞停。</b>
 * 加守卫之前先确认这条路只有本模块的控制器在走。</p>
 *
 * @author wczl
 */
public final class OwnershipScope
{
    private OwnershipScope()
    {
    }

    /**
     * 当前登录人能不能看全部（跳过归属过滤）。
     *
     * <p>平台超管一律放行——他不做业务，只观察和维护。</p>
     *
     * @param viewAllPermissions 「查看全部」权限点，例如 {@code order:sales:all}；
     *                           传多个时任意一个命中即可
     */
    public static boolean canViewAll(String... viewAllPermissions)
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || SecurityUtils.isAdmin(loginUser.getUserCode()))
            {
                return true;
            }
            Set<String> permissions = loginUser.getPermissions();
            if (permissions == null)
            {
                return false;
            }
            // 任意一个命中就算能看全部：发货批次那边有两个权限都代表「看全部」——
            // 专门的 order:shipbatch:all，以及「能进仓库发货页」本身（见 ShipBatchServiceImpl）
            for (String permission : viewAllPermissions)
            {
                if (SecurityUtils.hasPermi(permissions, permission))
                {
                    return true;
                }
            }
            return false;
        }
        catch (Exception e)
        {
            // 没有登录上下文：内部调用 / 定时任务 / 单元测试，放行
            return true;
        }
    }

    /**
     * 归属过滤要钉死的登录账号；能看全部时返回 {@code null}（表示不加条件）。
     *
     * <p>取不到登录账号时返回空串：它匹配不到任何一条记录，等于「一条都看不到」，
     * 是这里该有的失败方式。</p>
     */
    public static String ownerFilter(String... viewAllPermissions)
    {
        if (canViewAll(viewAllPermissions))
        {
            return null;
        }
        String me = currentLogin();
        return me == null ? "" : me;
    }

    /**
     * 这条记录不归自己就直接拒绝。
     *
     * @param ownerLogin         记录上记着的归属登录账号（建单时写下的那个）
     * @param docLabel           报错文案里的单据名，例如「发货批次」
     * @param docCode            报错文案里的单号
     * @param viewAllPermissions 「查看全部」权限点，传多个时任意一个命中即可
     */
    public static void requireOwn(String ownerLogin, String docLabel, String docCode,
            String... viewAllPermissions)
    {
        if (canViewAll(viewAllPermissions))
        {
            return;
        }
        String me = currentLogin();
        if (me == null || !me.equals(ownerLogin))
        {
            throw new ServiceException(docLabel + " " + docCode
                    + " 不归属于你，你只能查看和处理自己创建的单据");
        }
    }

    /** 当前登录账号；取不到返回 {@code null}。 */
    private static String currentLogin()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}

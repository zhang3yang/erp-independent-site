package com.ruoyi.common.utils;

import java.util.Set;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.PriceScope;

/**
 * 金额可见性判权。序列化出口和改单守卫共用这一份，别再写第二处。
 *
 * <p>见 {@code docs/金额按岗位可见.md}。</p>
 *
 * <h3>⚠ 读和写对「没有登录上下文」的默认值是相反的</h3>
 *
 * <p>定时任务、内部调用、单元测试都跑在没有登录上下文的路径上：</p>
 * <ul>
 *   <li><b>读</b>（{@link #isVisible}）：一律遮蔽。宁可少显示一次，也不能把价格漏出去；</li>
 *   <li><b>写</b>（{@link #isMaskedOnWrite}）：一律放行。压根没有要防的人，
 *       把内部调用的写入静默丢掉，是拿数据错乱换一个不存在的安全。</li>
 * </ul>
 *
 * <p>这两个默认值曾经共用过一套判断，后果是采购发货单的运费在单元测试里被当场清零。
 * <b>改这个类之前先想清楚你改的是哪一侧。</b></p>
 *
 * @author wczl
 */
public class PriceMaskUtils
{
    private PriceMaskUtils()
    {
    }

    /**
     * 当前登录人能否看到该侧金额。<b>没有登录上下文时返回 {@code false}（遮蔽）。</b>
     *
     * @param scope 字段归属侧；{@link PriceScope#AUTO} 表示运行时没能判出口径，
     *              按最严的走 —— 两个权限都有才给看
     */
    public static boolean isVisible(PriceScope scope)
    {
        if (scope == null || scope == PriceScope.AUTO)
        {
            return isVisible(PriceScope.COST) && isVisible(PriceScope.SALE);
        }
        Set<String> permissions = currentPermissions();
        if (permissions == null)
        {
            return false;
        }
        return isSuperAdmin() || SecurityUtils.hasPermi(permissions, scope.getPermission());
    }

    /** 当前登录人能否看到采购与成本价。 */
    public static boolean isCostVisible()
    {
        return isVisible(PriceScope.COST);
    }

    /** 当前登录人能否看到销售价。 */
    public static boolean isSaleVisible()
    {
        return isVisible(PriceScope.SALE);
    }

    /**
     * 改单时该侧金额是否该忽略前端回传的值、沿用库里原值。
     *
     * <p><b>没有登录上下文时返回 {@code false}（照收前端的值）</b> —— 与 {@link #isVisible}
     * 的默认值相反，理由见类注释。</p>
     *
     * @param scope 字段归属侧
     * @return {@code true} 表示当前用户看不到这个字段，回传的值不可信
     */
    public static boolean isMaskedOnWrite(PriceScope scope)
    {
        if (currentPermissions() == null)
        {
            // 没有登录上下文：内部调用 / 定时任务 / 单元测试，照收
            return false;
        }
        return !isVisible(scope);
    }

    /** 改单时采购与成本价是否不可信。 */
    public static boolean isCostMaskedOnWrite()
    {
        return isMaskedOnWrite(PriceScope.COST);
    }

    /** 改单时销售价是否不可信。 */
    public static boolean isSaleMaskedOnWrite()
    {
        return isMaskedOnWrite(PriceScope.SALE);
    }

    /**
     * 无权查看时返回库里的原值，有权查看时返回前端传来的值。
     *
     * <p>改单守卫的常用写法：{@code order.setUnitPrice(PriceMaskUtils.keepIfMasked(
     * PriceScope.COST, body.getUnitPrice(), existing.getUnitPrice()));}</p>
     *
     * <p>注意它只管「看不见的不许改」。前端传 {@code null} 是不是该沿用原值，
     * 是各入口自己的既有语义，本方法不替它们决定。</p>
     *
     * @param scope    字段归属侧
     * @param incoming 前端回传的值
     * @param existing 库里的原值
     */
    public static <T> T keepIfMasked(PriceScope scope, T incoming, T existing)
    {
        return isMaskedOnWrite(scope) ? existing : incoming;
    }

    /**
     * 取当前登录人的权限集；没有登录上下文时返回 {@code null}。
     *
     * <p>{@code null}（没登录）和空集合（登录了但什么权限都没有）是两回事，
     * 写侧靠这个区别决定放行还是拦截，所以这里不能把 {@code null} 折叠成空集。</p>
     */
    private static Set<String> currentPermissions()
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null)
            {
                return null;
            }
            Set<String> permissions = loginUser.getPermissions();
            return permissions == null ? Set.of() : permissions;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 平台超管不受这两个权限约束。
     *
     * <p>不能只靠权限集里的 {@code *:*:*}：超管进入某个商家之后，权限集会被
     * {@code PermissionService.effectivePermissions} 换成该商家的业务权限，
     * {@code *:*:*} 就不在里面了。</p>
     */
    private static boolean isSuperAdmin()
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            return loginUser != null && SecurityUtils.isAdmin(loginUser.getUserCode());
        }
        catch (Exception e)
        {
            return false;
        }
    }
}

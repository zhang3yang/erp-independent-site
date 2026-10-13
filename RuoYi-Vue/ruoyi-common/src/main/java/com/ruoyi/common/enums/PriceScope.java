package com.ruoyi.common.enums;

/**
 * 金额字段的归属侧，决定看这个字段需要哪个权限点。
 *
 * <p>见 {@code docs/金额按岗位可见.md}。只有两个权限点，一一对应四种岗位：
 * 勾 sale 是销售、勾 cost 是采购、都勾是财务、都不勾是仓库。</p>
 *
 * @author wczl
 */
public enum PriceScope
{
    /** 采购价与库存成本。付出去的钱。 */
    COST("price:cost"),

    /** 销售价。收进来的钱。 */
    SALE("price:sale"),

    /**
     * 同一列两种口径，运行时由所在对象自己判断（见 {@link PriceScopeAware}）。
     *
     * <p>眼下只有 {@code u_f_docmain.totalamount} 用得上：销售出库单存销售额，
     * 其余单据存成本额。判不出来时按最严的走 —— 两个权限都有才给看。</p>
     */
    AUTO(null);

    private final String permission;

    PriceScope(String permission)
    {
        this.permission = permission;
    }

    /**
     * 查看该侧金额所需的权限标识；{@link #AUTO} 没有固定权限，返回 {@code null}。
     */
    public String getPermission()
    {
        return permission;
    }
}

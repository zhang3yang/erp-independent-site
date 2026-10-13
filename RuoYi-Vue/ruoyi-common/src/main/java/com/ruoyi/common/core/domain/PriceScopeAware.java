package com.ruoyi.common.core.domain;

import com.ruoyi.common.enums.PriceScope;

/**
 * 由领域对象自己说明 {@link PriceScope#AUTO} 字段这一次算哪一侧。
 *
 * <p>只有「同一列两种口径」的字段需要实现它 —— 静态注解标不出来的那种。
 * 当前全系统只有 {@code DocumentMain.totalAmount} 一个：销售出库单存的是销售额，
 * 其余单据存的是成本额。</p>
 *
 * @author wczl
 */
public interface PriceScopeAware
{
    /**
     * 返回该字段本次的归属侧。
     *
     * @param fieldName 字段名（同一个类上有多个 AUTO 字段时用来区分）
     * @return {@link PriceScope#COST} 或 {@link PriceScope#SALE}；判不出来返回
     *         {@code null}，调用方会按最严的走（两个权限都有才给看）
     */
    PriceScope resolvePriceScope(String fieldName);
}

package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.Buyer;
import com.ruoyi.system.domain.Buyer2;

/**
 * 销售客户 Service。主客户与二级客户放在同一个 Service 里，
 * 是为了让级联删除、归属校验这些跨两张表的规则收在一处。
 */
public interface IBuyerService
{
    /** 条件分页查询，query.shopCode 必填 */
    List<Buyer> selectBuyerList(Buyer query);

    /** 查单个客户，附带跨月统计出来的历史订单数 */
    Buyer selectBuyerByCode(String shopCode, String buyerCode);

    /** 订单页客户选择器，keyword 为空时返回最近建的若干个启用客户 */
    List<Buyer> selectSelectorList(String shopCode, String keyword);

    /** 新增客户，编码由服务端生成，前端传的 buyerCode 一律忽略 */
    Buyer insertBuyer(Buyer buyer);

    int updateBuyer(Buyer buyer);

    /** 删除客户，同事务级联删除名下二级客户。有历史订单的客户不允许删，只能停用。 */
    int deleteBuyerByCodes(String shopCode, String[] buyerCodes);

    // ---- 二级客户 ----

    /** 某个主客户名下的二级客户列表，query.buyerCode 必填 */
    List<Buyer2> selectBuyer2List(Buyer2 query);

    /** 订单页二级客户下拉：指定主客户名下启用中的二级客户 */
    List<Buyer2> selectBuyer2Options(String shopCode, String buyerCode);

    /** 新增二级客户，编码由服务端生成；所属主客户必须存在 */
    Buyer2 insertBuyer2(Buyer2 buyer2);

    int updateBuyer2(Buyer2 buyer2);

    int deleteBuyer2ByCodes(String shopCode, String[] buyer2Codes);

    /**
     * 取二级客户快照源。订单落快照时调。
     *
     * <p>除了「存在且属于本商户」，还要校验它确实挂在 {@code buyerCode} 名下——
     * 少了这一条，前端就能把 A 客户的订单配上 B 客户的门店。</p>
     */
    Buyer2 requireBuyer2(String shopCode, String buyerCode, String buyer2Code);

    /**
     * 取客户快照源。订单落快照时调，查不到直接抛异常。
     *
     * <p>单独开这个方法而不是让订单侧自己调 mapper，是为了把「客户必须存在且属于本商户」
     * 这条校验收在一处：漏掉它就能靠前端传个别人商户的 buyercode 把客户信息串过去。</p>
     */
    Buyer requireBuyer(String shopCode, String buyerCode);
}

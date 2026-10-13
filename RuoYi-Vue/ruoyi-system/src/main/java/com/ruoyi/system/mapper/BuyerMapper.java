package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.Buyer;

/**
 * 销售客户 Mapper。对应表 sdsshop.u_m_buyer。
 *
 * <p>所有方法都带 shopCode：客户是租户数据，漏掉租户列就会跨商户串数据。</p>
 */
public interface BuyerMapper
{
    /** 条件分页查询。query 上的 shopCode 必填，其余字段为空即不参与过滤。 */
    List<Buyer> selectList(@Param("query") Buyer query);

    /** 按编码查单个客户 */
    Buyer selectByCode(@Param("shopCode") String shopCode, @Param("buyerCode") String buyerCode);

    /**
     * 按手机号查客户，用于唯一性冲突时反查占用者。
     *
     * <p>mobile 传 null 直接返回 null——「没填手机号」不构成占用，
     * 不能让两个都没填手机号的客户互相认成重复。</p>
     */
    Buyer selectByMobile(@Param("shopCode") String shopCode, @Param("mobile") String mobile);

    /** 订单页选择器用：只查启用的客户，按关键字模糊匹配简称/全称/手机号/编码 */
    List<Buyer> selectSelectorList(@Param("shopCode") String shopCode, @Param("keyword") String keyword,
            @Param("limit") int limit);

    int insert(Buyer buyer);

    int update(Buyer buyer);

    int deleteByCodes(@Param("shopCode") String shopCode, @Param("buyerCodes") String[] buyerCodes);

    /**
     * 统计某张订单月表里该客户的订单数。
     *
     * <p>tableSuffix 是拼进表名的，调用方必须先用 {@code MonthTableSuffix.require} 校验，
     * 直接把前端传的值拼进来就是 SQL 注入。</p>
     */
    int countOrdersInMonth(@Param("shopCode") String shopCode, @Param("buyerCode") String buyerCode,
            @Param("tableSuffix") String tableSuffix);
}

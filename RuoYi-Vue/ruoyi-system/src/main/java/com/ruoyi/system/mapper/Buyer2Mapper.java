package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.Buyer2;

/**
 * 二级客户 Mapper。对应表 sdsshop.u_m_buyer2。
 */
public interface Buyer2Mapper
{
    /** 条件分页查询，query 上 shopCode 与 buyerCode 都必填——二级客户不脱离主客户单独列 */
    List<Buyer2> selectList(@Param("query") Buyer2 query);

    Buyer2 selectByCode(@Param("shopCode") String shopCode, @Param("buyer2Code") String buyer2Code);

    /** 订单页二级客户下拉：只返回指定主客户名下启用中的 */
    List<Buyer2> selectOptions(@Param("shopCode") String shopCode, @Param("buyerCode") String buyerCode);

    int insert(Buyer2 buyer2);

    int update(Buyer2 buyer2);

    int deleteByCodes(@Param("shopCode") String shopCode, @Param("buyer2Codes") String[] buyer2Codes);

    /** 主客户删除时级联清理名下二级客户 */
    int deleteByBuyerCodes(@Param("shopCode") String shopCode, @Param("buyerCodes") String[] buyerCodes);
}

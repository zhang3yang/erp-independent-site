package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.GoodsPartner;

/**
 * 商品供货关系Mapper接口。
 * 对应表：sdsshop.u_m_goods_partner
 */
public interface GoodsPartnerMapper
{
    /**
     * 按商品查历史供应商，用于推荐。
     *
     * <p>按「最近一次入库日期倒序、次数倒序」返回，调用方取第一条就是推荐值。
     * 停用的供应商也返回（历史事实不会因为停用而消失），由 activeFlag 区分。</p>
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码集合，空集合返回空列表
     * @return 供货关系集合
     */
    public List<GoodsPartner> selectByGoodsCodes(@Param("shopCode") String shopCode,
            @Param("goodsCodes") Collection<String> goodsCodes);

    /**
     * 累积一条供货关系：已存在则次数 +1 并按需刷新最近一次入库信息。
     *
     * <p>补录的历史单（单据日期早于已记录的 lastdocdate）只加次数，不覆盖 last*，
     * 否则「最近一次」会被一张旧单顶掉。</p>
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @param partnerCode 供应商代码
     * @param partnerName 供应商名称
     * @param docCode 本次入库单号
     * @param docDate 本次入库单据日期
     * @param costPrice 本次入库成本单价
     * @return 结果
     */
    public int accumulate(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode,
            @Param("partnerCode") String partnerCode,
            @Param("partnerName") String partnerName,
            @Param("docCode") String docCode,
            @Param("docDate") Date docDate,
            @Param("costPrice") BigDecimal costPrice);
}

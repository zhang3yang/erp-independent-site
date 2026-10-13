package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.DocumentDetail;

/**
 * 单据明细Mapper接口
 */
public interface DocumentDetailMapper
{
    /**
     * 根据单据代码查询明细列表
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @return 明细集合
     */
    public List<DocumentDetail> selectDetailsByDocCode(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 新增明细
     *
     * @param detail 明细信息
     * @return 结果
     */
    public int insertDetail(DocumentDetail detail);

    /**
     * 批量新增明细
     *
     * @param details 明细列表
     * @param tableSuffix 月表后缀
     * @return 结果
     */
    public int batchInsertDetail(@Param("details") List<DocumentDetail> details,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 根据单据代码删除所有明细
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @return 结果
     */
    public int deleteDetailsByDocCode(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("tableSuffix") String tableSuffix);

    /** 全部库存单据月表后缀 */
    public List<String> selectDocumentTableSuffixes();

    /**
     * 指定发货批次生成的销售出库单已发数量与实际成本，按商品与仓库聚合。
     *
     * <p>只统计已审核的单据：待审核的出库单还没扣库存，退它没有意义。
     * 出库单的来源单号存的是批次号，所以入参是批次号列表而不是订单号。</p>
     */
    public List<com.ruoyi.system.domain.vo.SalesReturnableItem> selectShippedQtyByShipCodes(
            @Param("shopCode") String shopCode, @Param("shipCodes") List<String> shipCodes,
            @Param("tableSuffix") String tableSuffix);

    /** 某销售订单在指定月表内已退货的数量，按商品与仓库聚合，含待审核 */
    public List<com.ruoyi.system.domain.vo.SalesReturnableItem> selectReturnedQtyByOrder(
            @Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 某采购单某商品还挂着的「待审核」入库数量，跨月表汇总。
     *
     * @param tableSuffixes 要扫的月表后缀，必须全部通过 yyyyMM 校验（会拼进表名）。空集合不要调用。
     */
    public BigDecimal selectPendingInboundQty(@Param("shopCode") String shopCode,
            @Param("purchaseCode") String purchaseCode,
            @Param("goodsCode") String goodsCode,
            @Param("tableSuffixes") List<String> tableSuffixes);

    /** 更新审核后确定的实际成本。 */
    public int updateDetailCost(@Param("shopCode") String shopCode,
            @Param("detailCode") String detailCode,
            @Param("costPrice") BigDecimal costPrice,
            @Param("costAmount") BigDecimal costAmount,
            @Param("tableSuffix") String tableSuffix);

    /** 销售出库审核后，把未改价的行的销售单价对齐到实际出库成本。 */
    public int updateDetailSalePrice(@Param("shopCode") String shopCode,
            @Param("detailCode") String detailCode,
            @Param("salePrice") BigDecimal salePrice,
            @Param("saleAmount") BigDecimal saleAmount,
            @Param("tableSuffix") String tableSuffix);
}

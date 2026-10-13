package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ShipBatchItem;

/**
 * 发货批次明细 Mapper。明细跟随所属批次的月份。
 */
public interface ShipBatchItemMapper
{
    List<ShipBatchItem> selectByShipCode(@Param("shopCode") String shopCode,
            @Param("shipCode") String shipCode, @Param("tableSuffix") String tableSuffix);

    /**
     * 指定的这批订单上「已排进未完成批次」的数量合计，按订单明细汇总。
     *
     * <p>已完成批次的量已经进了订单明细的 shippedqty，再算一次会重复；
     * 已取消的批次不占量。所以统计除这两者之外的全部中间态——到「已完成」为止
     * 这条链一次都没碰过库存，shippedqty 也没动。</p>
     *
     * <p>一次收一批订单号：发货下拉要对整月的可发货订单算可发量，
     * 逐单查会把月表数量乘上订单数量。orderCodes 由调用方保证非空。</p>
     */
    List<ShipBatchItem> selectPendingQtyByOrders(@Param("shopCode") String shopCode,
            @Param("orderCodes") List<String> orderCodes, @Param("tableSuffix") String tableSuffix);

    /**
     * 单张订单上、限定状态的批次已排的量，按订单明细汇总，可排除某张批次自己。
     *
     * <p>付款额度校验专用：那里要的是「已经把已收金额占掉」的那几格，
     * 与 {@link #selectPendingQtyByOrders} 的「全部中间态」口径不同，所以单开一条。</p>
     *
     * @param excludeShipCode 要排除的批次号，校验时传自己；不排除传 null
     */
    List<ShipBatchItem> selectQtyByOrderInStatuses(@Param("shopCode") String shopCode,
            @Param("orderCode") String orderCode, @Param("statuses") List<String> statuses,
            @Param("excludeShipCode") String excludeShipCode, @Param("tableSuffix") String tableSuffix);

    int batchInsert(@Param("items") List<ShipBatchItem> items, @Param("tableSuffix") String tableSuffix);

    /**
     * 改单行数量。录入快递单号时业务员按实际备货量往下调走这条。
     *
     * <p>不走「整批删了重插」：那会重新生成明细代码，而序列号 / 出库批次的选择和
     * 库存流水的追溯都要靠原来的明细代码对上。</p>
     */
    int updateQty(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("tableSuffix") String tableSuffix, @Param("qty") BigDecimal qty);

    /** 整行不发了（本次发货数量填 0）就删掉这一行，它占的订单量随之放回去。 */
    int deleteByDetailCode(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("tableSuffix") String tableSuffix);

    int deleteByShipCode(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix);

    /** 本批次发货总量，供单头展示。 */
    BigDecimal sumQtyByShipCode(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix);
}

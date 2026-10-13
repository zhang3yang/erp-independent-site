package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ProductionOrder;

/** 生产工单主表 Mapper。 */
public interface ProductionOrderMapper
{
    /**
     * 给工单行加排他锁，锁持有到事务结束。
     *
     * <p>执行类操作（领料/完工/退料/关闭/调成本）都先锁再读：状态判断是在内存里做的，
     * 不锁的话两个并发请求会同时读到「生产中」，各自开出一套库存单据。返回值只用来确认
     * 行还在，存在性由后面的 select 负责，这里不做判断。</p>
     */
    String lockProductionOrder(@Param("shopCode") String shopCode,
            @Param("productionCode") String productionCode);

    ProductionOrder selectProductionOrderById(@Param("shopCode") String shopCode, @Param("id") Long id);
    ProductionOrder selectProductionOrderByCode(@Param("shopCode") String shopCode,
            @Param("productionCode") String productionCode);
    List<ProductionOrder> selectProductionOrderList(ProductionOrder order);
    int insertProductionOrder(ProductionOrder order);
    int updateProductionOrder(ProductionOrder order);
    int updateStatus(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode,
            @Param("status") String status, @Param("updateUser") String updateUser);
    int deleteProductionOrder(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode);

    /**
     * 完工回写。{@code expectStatus} 是乐观兜底：状态已经不是「生产中」时更新 0 行，
     * 由 Service 抛错回滚，防止重复提交开出两套消耗单和成品入库单。
     */
    int updateExecution(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode,
            @Param("expectStatus") String expectStatus,
            @Param("status") String status, @Param("actualQty") java.math.BigDecimal actualQty,
            @Param("actualDate") java.util.Date actualDate, @Param("materialCost") java.math.BigDecimal materialCost,
            @Param("totalCost") java.math.BigDecimal totalCost, @Param("unitCost") java.math.BigDecimal unitCost,
            @Param("processingCost") java.math.BigDecimal processingCost,
            @Param("freightCost") java.math.BigDecimal freightCost,
            @Param("otherCost") java.math.BigDecimal otherCost,
            @Param("updateUser") String updateUser);

    /**
     * 只更新成本，不碰状态、实际产出和完工日期。
     *
     * <p>完工后补录费用时用。物料成本不在参数里——它只能由生产消耗（SCXH）单据产生，
     * 事后调整费用改不了已经消耗掉的料。</p>
     */
    int updateCost(@Param("shopCode") String shopCode, @Param("productionCode") String productionCode,
            @Param("totalCost") java.math.BigDecimal totalCost, @Param("unitCost") java.math.BigDecimal unitCost,
            @Param("processingCost") java.math.BigDecimal processingCost,
            @Param("freightCost") java.math.BigDecimal freightCost,
            @Param("otherCost") java.math.BigDecimal otherCost,
            @Param("updateUser") String updateUser);

    /**
     * 某张销售订单关联的全部生产工单，不分状态，按建单时间倒序。供订单详情页追溯。
     *
     * <p>按 orderallocation / sourceordercodes 匹配，不按订单明细上的 sourcecode——
     * 那个字段是单值的，换过工单只留最后一张，关闭工单时还会被清空。</p>
     */
    List<ProductionOrder> selectByOrderCode(@Param("shopCode") String shopCode,
            @Param("orderCode") String orderCode);

    /**
     * 还没走到终态、且产出分配给了某张销售订单的工单数。
     *
     * <p>销售订单撤审要用：分配里存的是订单<b>明细代码</b>，撤审后改单会把明细整体重建、
     * 换一批明细代码，那些分配就指向不存在的行了，工单完工时会静默认领不到。</p>
     */
    int countOpenAllocatedToOrder(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode);
}

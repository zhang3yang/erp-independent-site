package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.ProductionLog;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.ProductionExecutionRequest;

/** 生产工单基础服务。 */
public interface IProductionOrderService
{
    ProductionOrder selectProductionOrderById(String shopCode, Long id);
    ProductionOrder selectProductionOrderByCode(String shopCode, String productionCode);
    List<ProductionOrder> selectProductionOrderList(ProductionOrder order);
    int insertProductionOrder(ProductionOrder order);
    int updateProductionOrder(ProductionOrder order);
    /** 删除工单。只有待生产工单能删，领过料的工单已经动过库存，只能走关闭。 */
    int deleteProductionOrder(String shopCode, String productionCode);

    /**
     * 关闭工单，工单的唯一结案动作。
     *
     * <p>已完工关闭是正常收尾；待生产 / 待领料 / 生产中关闭是「这批不做了」的终止，
     * 必须给 {@code reason}，原因落进生产日志。两种情况都要求车间已清空（料退干净），
     * 关闭后会松开还没做完的来源订单明细。</p>
     *
     * @param reason 终止原因，已完工关闭时可以为空
     */
    int closeProductionOrder(String shopCode, String productionCode, String reason, String updateUser);
    int pickProductionOrder(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser);
    int completeProductionOrder(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser);
    int returnProductionMaterial(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser);
    /**
     * 完工后补录 / 修改加工费、运费、其他费用，重算总成本与单位成本。
     *
     * <p>发票晚到是常态，工单一完工就锁死费用不现实。只放行「已完工且未关闭」的工单，
     * 关闭即成本定案。</p>
     *
     * <p><b>不同步库存成本</b>：成品已经按调整前的单位成本入过库，这里只改工单和产出明细，
     * 库存里那批成品的成本不动。差额会写进生产日志，不会默默发生。</p>
     */
    int adjustProductionCost(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser);
    List<ProductionLog> selectProductionLogList(String shopCode, String productionCode,
            String operation, String tableSuffix);
}

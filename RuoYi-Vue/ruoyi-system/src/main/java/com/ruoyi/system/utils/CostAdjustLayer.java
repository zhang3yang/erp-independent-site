package com.ruoyi.system.utils;

import com.ruoyi.system.domain.InventoryGoods;

/**
 * 成本调整落在哪一层。
 *
 * <p>出库时成本从哪儿取，调整就得改哪儿，否则改完账面变了、下一次出库的成本却照旧：</p>
 * <ul>
 *   <li>先进先出、个别计价的批次商品 —— 出库按批次单价，改 {@link #BATCH}；</li>
 *   <li>个别计价的序列号商品 —— 出库按每个序列号的单件成本，改 {@link #SERIAL}；</li>
 *   <li>其余（移动平均，以及序列号商品配先进先出）—— 出库按库存汇总的均价，改 {@link #STOCK}。</li>
 * </ul>
 *
 * <p>三种情况库存汇总的总成本都要跟着加减，这一点由过账统一处理。</p>
 */
public enum CostAdjustLayer
{
    STOCK, BATCH, SERIAL;

    public static CostAdjustLayer of(InventoryGoods goods)
    {
        String strategy = goods == null || goods.getBatchStrategy() == null
                ? "none" : goods.getBatchStrategy().trim().toLowerCase();
        String costing = goods == null || goods.getCostCalculation() == null
                ? "moving_avg" : goods.getCostCalculation().trim().toLowerCase();
        if ("batch".equals(strategy) && ("fifo".equals(costing) || "individual".equals(costing)))
        {
            return BATCH;
        }
        if ("serial".equals(strategy) && "individual".equals(costing))
        {
            return SERIAL;
        }
        return STOCK;
    }
}

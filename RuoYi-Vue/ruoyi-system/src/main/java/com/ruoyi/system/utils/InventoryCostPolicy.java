package com.ruoyi.system.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryGoods;

/**
 * 商品实物追踪方式与成本计算方法的对应规则。
 *
 * <p>批次策略和成本计算方法一一对应，选定批次策略后成本计算方法就定死了：
 * 无批次 → 移动加权平均，批次管理 → 先进先出，序列号管理 → 个别计价。
 * 成本计算方法不再由用户单独选择，一律由批次策略推导。</p>
 */
public final class InventoryCostPolicy
{
    public static final String TRACKING_NONE = "none";
    public static final String TRACKING_BATCH = "batch";
    public static final String TRACKING_SERIAL = "serial";
    public static final String MOVING_AVERAGE = "moving_avg";
    public static final String FIFO = "fifo";
    public static final String INDIVIDUAL = "individual";

    private static final Map<String, String> COST_BY_TRACKING;

    static
    {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put(TRACKING_NONE, MOVING_AVERAGE);
        mapping.put(TRACKING_BATCH, FIFO);
        mapping.put(TRACKING_SERIAL, INDIVIDUAL);
        COST_BY_TRACKING = Collections.unmodifiableMap(mapping);
    }

    private InventoryCostPolicy()
    {
    }

    public static void normalizeAndValidate(InventoryGoods goods)
    {
        if (goods == null)
        {
            throw new ServiceException("商品信息不能为空");
        }
        String tracking = normalize(goods.getBatchStrategy(), TRACKING_NONE);
        if (!COST_BY_TRACKING.containsKey(tracking))
        {
            throw new ServiceException("商品批次策略无效：" + tracking);
        }
        // 成本计算方法由批次策略唯一决定，前端传什么都以推导结果为准。
        goods.setBatchStrategy(tracking);
        goods.setCostCalculation(COST_BY_TRACKING.get(tracking));
    }

    /** 批次策略对应的成本计算方法；策略非法时返回移动加权平均兜底。 */
    public static String costCalculationFor(String batchStrategy)
    {
        String tracking = normalize(batchStrategy, TRACKING_NONE);
        String calculation = COST_BY_TRACKING.get(tracking);
        return calculation == null ? MOVING_AVERAGE : calculation;
    }

    public static String normalize(String value, String defaultValue)
    {
        return value == null || value.trim().isEmpty()
                ? defaultValue : value.trim().toLowerCase();
    }
}

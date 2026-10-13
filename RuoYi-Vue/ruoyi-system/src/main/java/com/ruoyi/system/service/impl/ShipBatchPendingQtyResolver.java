package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.ShipBatchItem;
import com.ruoyi.system.mapper.ShipBatchItemMapper;
import com.ruoyi.system.mapper.ShipBatchMapper;
import com.ruoyi.system.utils.MonthTableSuffix;

/**
 * 「已排进未完成批次的量」查询，按订单明细汇总。
 *
 * <p>订单服务算可发量要用它，发货批次服务校验能不能排下去也要用它。
 * 单独拆成组件是为了避开循环依赖：{@code ShipBatchServiceImpl} 已经注入了
 * {@code ISalesOrderService}，订单服务再反向注入发货批次服务就成环了。</p>
 *
 * <p>只统计中间态（待确认 / 已提交仓库 / 已打印备货单 / 备货完成 / 已录入快递单号 /
 * 已打印快递单号）：只有「已完成」的量进了订单明细的 shippedqty，再算一次就重复；
 * 已取消的批次不占量。</p>
 *
 * <p><b>成本提示</b>：每次调用都要枚举一遍发货批次月表（information_schema）再逐月查询，
 * 所以「一批订单」必须走 {@link #resolveByOrders}，不能在订单循环里调
 * {@link #resolve}——那样月表数会乘上订单数。</p>
 */
@Component
public class ShipBatchPendingQtyResolver
{
    @Autowired private ShipBatchMapper batchMapper;
    @Autowired private ShipBatchItemMapper batchItemMapper;

    /**
     * 单张订单的占用量，可排除某个批次自身。
     *
     * @param excludeShipCode 要排除的批次号，改单时传自己，否则会把自己算成别人的占用
     * @return key 为订单明细代码
     */
    public Map<String, BigDecimal> resolve(String shopCode, String orderCode, String excludeShipCode)
    {
        // 一张订单的批次按各自的 shipdate 落表，可能散在多个月份
        List<String> suffixes = MonthTableSuffix.validDescending(batchMapper.selectShipBatchTableSuffixes());
        Map<String, BigDecimal> result = accumulate(shopCode, Collections.singletonList(orderCode), suffixes);
        if (excludeShipCode != null && !excludeShipCode.trim().isEmpty())
        {
            for (String suffix : suffixes)
            {
                for (ShipBatchItem row : batchItemMapper.selectByShipCode(shopCode, excludeShipCode, suffix))
                {
                    result.merge(row.getOrderDetailCode(), nvl(row.getQty()).negate(), BigDecimal::add);
                }
            }
        }
        return result;
    }

    /**
     * 单张订单上、限定状态的批次已排的量，按订单明细汇总，可排除某张批次自己。
     *
     * <p>与 {@link #resolve} 的区别只有状态范围：那个数的是「全部中间态」，用来算可排量；
     * 这个由调用方指定，付款额度校验拿它数「已经把已收金额占掉」的那几格。</p>
     *
     * @param statuses        要统计的批次状态，为空时返回空 map，不发任何查询
     * @param excludeShipCode 要排除的批次号，校验时传自己；不排除传 null
     * @return key 为订单明细代码
     */
    public Map<String, BigDecimal> resolveInStatuses(String shopCode, String orderCode,
            List<String> statuses, String excludeShipCode)
    {
        Map<String, BigDecimal> result = new HashMap<>();
        if (statuses == null || statuses.isEmpty()) return result;
        // 一张订单的批次按各自的 shipdate 落表，可能散在多个月份
        for (String suffix : MonthTableSuffix.validDescending(batchMapper.selectShipBatchTableSuffixes()))
        {
            for (ShipBatchItem row : batchItemMapper.selectQtyByOrderInStatuses(
                    shopCode, orderCode, statuses, excludeShipCode, suffix))
            {
                result.merge(row.getOrderDetailCode(), nvl(row.getQty()), BigDecimal::add);
            }
        }
        return result;
    }

    /**
     * 一批订单的占用量，一次算完。
     *
     * <p>明细代码全局唯一，所以多张订单的结果可以并进同一个扁平 map，
     * 调用方按明细代码取即可，不需要再按订单分层。</p>
     *
     * @return key 为订单明细代码；orderCodes 为空时返回空 map，不发任何查询
     */
    public Map<String, BigDecimal> resolveByOrders(String shopCode, Collection<String> orderCodes)
    {
        if (orderCodes == null || orderCodes.isEmpty()) return new HashMap<>();
        // 去重后再进 in：同一个订单号重复传会让 sum 被算两遍
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(orderCodes));
        return accumulate(shopCode, codes,
                MonthTableSuffix.validDescending(batchMapper.selectShipBatchTableSuffixes()));
    }

    private Map<String, BigDecimal> accumulate(String shopCode, List<String> orderCodes, List<String> suffixes)
    {
        Map<String, BigDecimal> result = new HashMap<>();
        for (String suffix : suffixes)
        {
            for (ShipBatchItem row : batchItemMapper.selectPendingQtyByOrders(shopCode, orderCodes, suffix))
            {
                result.merge(row.getOrderDetailCode(), nvl(row.getQty()), BigDecimal::add);
            }
        }
        return result;
    }

    private static BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }
}

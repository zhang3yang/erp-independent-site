package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.vo.SalesProfitReport;
import com.ruoyi.system.domain.vo.SalesProfitReport.MissingRate;
import com.ruoyi.system.domain.vo.SalesProfitReport.Row;
import com.ruoyi.system.domain.vo.SalesProfitSource;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.mapper.SalesProfitMapper;
import com.ruoyi.system.mapper.ShipBatchMapper;
import com.ruoyi.system.service.IExchangeRateService;
import com.ruoyi.system.service.IExchangeRateService.RateBook;
import com.ruoyi.system.service.ISalesProfitService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.OwnershipScope;

/**
 * 销售利润报表
 *
 * <p><b>口径</b>（改之前先想清楚，报表的数一旦被拿去和财务对账，口径就不能随便动）：</p>
 * <ul>
 *   <li><b>按发货确认</b>：统计的是区间内「已审核的出库单」，日期取出库单的单据日期，
 *       也就是发货批次完成那天。下了单没发货的不算收入。</li>
 *   <li><b>收入</b> = 发货数量 × 订单单价。不用出库单上的售价：没改过价的出库行审核时
 *       会被对齐成成本价（见 InventoryDocumentPostingService.syncSalePriceWithActualCost）。
 *       订单上找不到这个商品时才退回用出库单售价。</li>
 *   <li><b>成本</b> = 出库单过账时写回的实际成本（FIFO / 个别计价 / 均价按商品档案）。
 *       订单明细上的 costprice 从来没被写过，一直是 0，不能用。</li>
 *   <li><b>退货</b>：已审核的销售退货入库，按原单价冲收入、按退回的成本冲成本，记在退货那天。</li>
 *   <li><b>补发</b>：只有成本没有收入，记在原订单上，单列「其中补发成本」。</li>
 *   <li><b>运费</b>：订单上向客户收的运费按「本次发货货款 / 订单货款」分摊到每次发货；
 *       发货批次上付给承运商的运费记在那个批次的出库那天。退货不冲运费。</li>
 *   <li><b>汇率</b>：外币收入按发货（或退货）当天生效的汇率折人民币，见 IExchangeRateService.RateBook。
 *       那天之前没录过汇率的，整笔不算进报表，列在 missingRates 里——宁可少算也不能拿 1:1 糊弄。</li>
 *   <li><b>权限</b>：没有 order:sales:all 的业务员只看得到自己名下的订单，和订单列表一致。</li>
 * </ul>
 */
@Service
public class SalesProfitServiceImpl implements ISalesProfitService
{
    private static final Set<String> DIMENSIONS = new HashSet<>(
            Arrays.asList("customer", "staff", "country", "goods", "store", "month", "order"));

    /** IN 列表一次最多塞多少个，免得 SQL 太长 */
    private static final int CHUNK = 500;

    @Autowired
    private SalesProfitMapper profitMapper;

    @Autowired
    private DocumentDetailMapper documentDetailMapper;

    @Autowired
    private SalesOrderMapper orderMapper;

    @Autowired
    private ShipBatchMapper shipBatchMapper;

    @Autowired
    private IExchangeRateService rateService;

    @Autowired
    private StaffNameResolver staffNameResolver;

    /** 一笔发生额：一行出库 / 退货 / 补发明细折算后的结果 */
    private static class Fact
    {
        SalesProfitSource order;
        String goodsCode;
        String goodsName;
        Date date;
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal revenueOriginal = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal reshipCost = BigDecimal.ZERO;
        BigDecimal freightIncome = BigDecimal.ZERO;
        BigDecimal freightCost = BigDecimal.ZERO;
    }

    @Override
    public SalesProfitReport report(String shopCode, Date begin, Date end, String dimension,
            String filterDim, String filterKey)
    {
        if (begin == null || end == null)
        {
            throw new ServiceException("请选择发货日期区间");
        }
        if (end.before(begin))
        {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        String dim = dimension == null || dimension.trim().isEmpty() ? "customer" : dimension.trim();
        if (!DIMENSIONS.contains(dim) || (filterDim != null && !filterDim.isEmpty() && !DIMENSIONS.contains(filterDim)))
        {
            throw new ServiceException("汇总维度只能是客户、业务员、国家、商品、平台店铺、月份、订单");
        }

        SalesProfitReport report = new SalesProfitReport();
        report.setBegin(begin);
        report.setEnd(end);
        report.setDimension(dim);

        // 1. 区间内的单据明细
        List<SalesProfitSource> lines = new ArrayList<>();
        Date endExclusive = addDays(end, 1);
        for (String suffix : MonthTableSuffix.queryRange(documentDetailMapper.selectDocumentTableSuffixes(), begin, end))
        {
            lines.addAll(profitMapper.selectDocumentLines(shopCode, suffix, begin, endExclusive));
        }
        if (lines.isEmpty())
        {
            return report;
        }

        // 2. 单据 → 订单：发货批次、补发单、退货直接带订单号
        Set<String> shipCodes = new LinkedHashSet<>();
        Set<String> reshipCodes = new LinkedHashSet<>();
        for (SalesProfitSource line : lines)
        {
            if (InventoryDocumentTypes.SHIP_BATCH_SOURCE.equals(line.getSourceDocType()))
            {
                shipCodes.add(line.getSourceDocCode());
            }
            else if (InventoryDocumentTypes.SALES_RESHIP_SOURCE.equals(line.getSourceDocType()))
            {
                reshipCodes.add(line.getSourceDocCode());
            }
        }
        Map<String, SalesProfitSource> batches = new HashMap<>();
        for (String suffix : MonthTableSuffix.validDescending(shipBatchMapper.selectShipBatchTableSuffixes()))
        {
            for (List<String> chunk : chunks(shipCodes))
            {
                for (SalesProfitSource batch : profitMapper.selectShipBatches(shopCode, suffix, chunk))
                {
                    batches.put(batch.getShipCode(), batch);
                }
            }
        }
        Map<String, String> reshipOrders = new HashMap<>();
        for (List<String> chunk : chunks(reshipCodes))
        {
            for (SalesProfitSource reship : profitMapper.selectReships(shopCode, chunk))
            {
                reshipOrders.put(reship.getReshipCode(), reship.getOrderCode());
            }
        }

        Set<String> orderCodes = new LinkedHashSet<>();
        for (SalesProfitSource line : lines)
        {
            String orderCode = orderCodeOf(line, batches, reshipOrders);
            if (orderCode != null)
            {
                orderCodes.add(orderCode);
            }
        }

        // 3. 订单头和订单明细单价（订单按下单月分表，和出库月份不一定一致，只能每张月表都查）
        Map<String, SalesProfitSource> orders = new HashMap<>();
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String suffix : MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes()))
        {
            for (List<String> chunk : chunks(orderCodes))
            {
                for (SalesProfitSource order : profitMapper.selectOrders(shopCode, suffix, chunk))
                {
                    orders.put(order.getOrderCode(), order);
                }
                for (SalesProfitSource orderLine : profitMapper.selectOrderLines(shopCode, suffix, chunk))
                {
                    prices.put(orderLine.getOrderCode() + "|" + orderLine.getGoodsCode(), orderLine.getPrice());
                }
            }
        }

        // 4. 逐行折算成发生额
        String owner = OwnershipScope.ownerFilter(SalesOrderServiceImpl.PERMI_VIEW_ALL);
        RateBook rates = rateService.loadRateBook(shopCode);
        Map<String, MissingRate> missing = new LinkedHashMap<>();
        Set<String> freightCharged = new HashSet<>();
        Set<String> freightIncomeOnce = new HashSet<>();
        List<Fact> facts = new ArrayList<>();
        int unmatched = 0;
        int zeroCost = 0;
        for (SalesProfitSource line : lines)
        {
            SalesProfitSource order = orders.get(orderCodeOf(line, batches, reshipOrders));
            if (order == null)
            {
                unmatched++;
                continue;
            }
            if (owner != null && !owner.equals(order.getStaffCode()))
            {
                continue;
            }
            BigDecimal rate = rates.rateOn(order.getCurrency(), line.getDocDate());
            if (rate == null)
            {
                MissingRate m = missing.computeIfAbsent(order.getCurrency(), k -> {
                    MissingRate r = new MissingRate();
                    r.setCurrency(k);
                    return r;
                });
                m.orderCodes().add(order.getOrderCode());
                m.setLineCount(m.getLineCount() + 1);
                if (m.getEarliestDate() == null || line.getDocDate().before(m.getEarliestDate()))
                {
                    m.setEarliestDate(line.getDocDate());
                }
                continue;
            }

            Fact fact = new Fact();
            fact.order = order;
            fact.goodsCode = line.getGoodsCode();
            fact.goodsName = line.getGoodsName();
            fact.date = line.getDocDate();
            BigDecimal qty = zero(line.getQty());
            BigDecimal cost = zero(line.getCostAmount());
            BigDecimal price = prices.get(order.getOrderCode() + "|" + line.getGoodsCode());

            if (InventoryDocumentTypes.SHIP_BATCH_SOURCE.equals(line.getSourceDocType()))
            {
                fact.qty = qty;
                fact.revenueOriginal = price != null ? price.multiply(qty) : zero(line.getSaleAmount());
                fact.cost = cost;
                if (cost.signum() == 0)
                {
                    zeroCost++;
                }
                // 收客户的运费按发货进度摊；订单货款为 0 的，整笔记在第一次发货上
                BigDecimal orderFreight = zero(order.getOrderFreight());
                BigDecimal goodsAmount = zero(order.getGoodsAmount());
                if (orderFreight.signum() != 0)
                {
                    if (goodsAmount.signum() > 0)
                    {
                        fact.freightIncome = orderFreight.multiply(fact.revenueOriginal)
                                .divide(goodsAmount, 6, RoundingMode.HALF_UP).multiply(rate);
                    }
                    else if (freightIncomeOnce.add(order.getOrderCode()))
                    {
                        fact.freightIncome = orderFreight.multiply(rate);
                    }
                }
                // 付承运商的运费一个批次只记一次
                SalesProfitSource batch = batches.get(line.getSourceDocCode());
                if (batch != null && freightCharged.add(batch.getShipCode()))
                {
                    fact.freightCost = zero(batch.getShipFreight());
                }
            }
            else if (InventoryDocumentTypes.SALES_RESHIP_SOURCE.equals(line.getSourceDocType()))
            {
                fact.cost = cost;
                fact.reshipCost = cost;
            }
            else
            {
                // 销售退货：数量、收入、成本全部冲回
                fact.qty = qty.negate();
                fact.revenueOriginal = (price != null ? price : BigDecimal.ZERO).multiply(qty).negate();
                fact.cost = cost.negate();
            }
            fact.revenue = fact.revenueOriginal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            fact.freightIncome = fact.freightIncome.setScale(2, RoundingMode.HALF_UP);
            facts.add(fact);
        }
        report.setUnmatchedLines(unmatched);
        report.setZeroCostLines(zeroCost);
        report.setMissingRates(new ArrayList<>(missing.values()));

        // 5. 下钻过滤 + 汇总
        Map<String, String> staffNames = staffNames(facts);
        if (filterDim != null && !filterDim.isEmpty() && filterKey != null)
        {
            facts.removeIf(f -> !filterKey.equals(keyOf(filterDim, f)));
        }
        Map<String, Row> rows = new LinkedHashMap<>();
        for (Fact fact : facts)
        {
            String key = keyOf(dim, fact);
            Row row = rows.computeIfAbsent(key, k -> newRow(dim, k, fact, staffNames));
            add(row, fact);
            if ("order".equals(dim))
            {
                row.setRevenueOriginal(zero(row.getRevenueOriginal()).add(fact.revenueOriginal));
            }
            add(report.getSummary(), fact);
        }
        List<Row> result = new ArrayList<>(rows.values());
        for (Row row : result)
        {
            row.setOrderCount(row.orderCodes().size());
        }
        report.getSummary().setOrderCount(report.getSummary().orderCodes().size());
        report.getSummary().setLabel("合计");
        if ("month".equals(dim))
        {
            result.sort(Comparator.comparing(Row::getKey));
        }
        else
        {
            result.sort(Comparator.comparing(Row::getGrossProfit).reversed());
        }
        report.setRows(result);
        return report;
    }

    private String orderCodeOf(SalesProfitSource line, Map<String, SalesProfitSource> batches,
            Map<String, String> reshipOrders)
    {
        if (InventoryDocumentTypes.SHIP_BATCH_SOURCE.equals(line.getSourceDocType()))
        {
            SalesProfitSource batch = batches.get(line.getSourceDocCode());
            return batch == null ? null : batch.getOrderCode();
        }
        if (InventoryDocumentTypes.SALES_RESHIP_SOURCE.equals(line.getSourceDocType()))
        {
            return reshipOrders.get(line.getSourceDocCode());
        }
        return line.getSourceDocCode();
    }

    private String keyOf(String dim, Fact f)
    {
        SalesProfitSource o = f.order;
        switch (dim)
        {
            case "customer":
                return nvl(o.getBuyerCode(), nvl(o.getBuyerUser(), ""));
            case "staff":
                return nvl(o.getStaffCode(), "");
            case "country":
                return nvl(o.getCountry(), "");
            case "goods":
                return f.goodsCode;
            case "store":
                return nvl(o.getPlatCode(), "") + "|" + nvl(o.getStoreCode(), "");
            case "month":
                return new SimpleDateFormat("yyyy-MM").format(f.date);
            default:
                return o.getOrderCode();
        }
    }

    private Row newRow(String dim, String key, Fact f, Map<String, String> staffNames)
    {
        SalesProfitSource o = f.order;
        Row row = new Row();
        row.setKey(key);
        switch (dim)
        {
            case "customer":
                row.setLabel(nvl(o.getBuyerName(), nvl(o.getBuyerUser(), "未填客户")));
                break;
            case "staff":
                row.setLabel(key.isEmpty() ? "未指定业务员" : staffNames.getOrDefault(key, key));
                break;
            case "country":
                String label = key.isEmpty() ? null : DictUtils.getDictLabel("order_country", key);
                row.setLabel(key.isEmpty() ? "未填国家" : nvl(label, key));
                break;
            case "goods":
                row.setLabel(nvl(f.goodsName, key));
                row.setSubLabel(key);
                break;
            case "store":
                row.setLabel(nvl(o.getPlatName(), "未填平台") + " / " + nvl(o.getStoreName(), "未填店铺"));
                break;
            case "month":
                row.setLabel(key);
                break;
            default:
                row.setLabel(key);
                row.setSubLabel(nvl(o.getBuyerName(), o.getBuyerUser()));
                row.setCurrency(nvl(o.getCurrency(), IExchangeRateService.BASE_CURRENCY));
        }
        return row;
    }

    private void add(Row row, Fact f)
    {
        row.orderCodes().add(f.order.getOrderCode());
        row.setQty(row.getQty().add(f.qty));
        row.setRevenue(row.getRevenue().add(f.revenue));
        row.setCost(row.getCost().add(f.cost));
        row.setReshipCost(row.getReshipCost().add(f.reshipCost));
        row.setFreightIncome(row.getFreightIncome().add(f.freightIncome));
        row.setFreightCost(row.getFreightCost().add(f.freightCost));
    }

    private Map<String, String> staffNames(List<Fact> facts)
    {
        Set<String> logins = new LinkedHashSet<>();
        for (Fact fact : facts)
        {
            logins.add(fact.order.getStaffCode());
        }
        return staffNameResolver.nickNamesByLogin(logins);
    }

    private static List<List<String>> chunks(Collection<String> codes)
    {
        List<List<String>> result = new ArrayList<>();
        List<String> all = new ArrayList<>(codes);
        for (int from = 0; from < all.size(); from += CHUNK)
        {
            result.add(all.subList(from, Math.min(all.size(), from + CHUNK)));
        }
        return result;
    }

    private static Date addDays(Date date, int days)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    private static BigDecimal zero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nvl(String value, String fallback)
    {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}

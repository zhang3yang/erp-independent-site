package com.ruoyi.web.controller.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.ShipBatch;
import com.ruoyi.system.service.IShipBatchService;
import com.ruoyi.system.utils.MonthTableSuffix;

/**
 * 发货批次 Controller。批次按计划发货日期分月表，与订单的月份无关。
 */
@RestController
@RequestMapping("/order/shipbatch")
public class ShipBatchController extends BaseController
{
    @Autowired private IShipBatchService shipBatchService;

    /**
     * 两个页面共用这一条：「订单管理 → 发货批次」（业务员排产）和
     * 「库存管理 → 仓库发货管理」（仓库干活，带 {@code warehouseView=true} 多筛一道状态）。
     *
     * <p>所以两个页面各自的菜单权限都要放行：业务员那边是 {@code order:shipbatch:list}，
     * 仓库那边是 {@code order:shipbatch:warehouse}。后者同时意味着「看得到全部批次」，
     * 理由见 {@code ShipBatchServiceImpl.PERMI_WAREHOUSE}。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasAnyPermi('order:shipbatch:list,order:shipbatch:warehouse')")
    @GetMapping("/list")
    public TableDataInfo list(ShipBatch query, @RequestParam(required = false) String tableSuffix)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        String suffix = MonthTableSuffix.isValid(tableSuffix) ? tableSuffix : MonthTableSuffix.current();
        return getDataTable(shipBatchService.selectBatchList(query, suffix));
    }

    /** 某订单的全部批次，跨月汇总——一张订单的批次可以落在不同月份。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:list')")
    @GetMapping("/byOrder/{orderCode}")
    public AjaxResult byOrder(@PathVariable("orderCode") String orderCode)
    {
        return success(shipBatchService.selectBatchesByOrder(SecurityUtils.getBusinessShopCode(), orderCode));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:query')")
    @GetMapping("/{shipCode}")
    public AjaxResult getInfo(@PathVariable("shipCode") String shipCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        ShipBatch batch = MonthTableSuffix.isValid(tableSuffix)
                ? shipBatchService.selectBatchDetail(shopCode, shipCode, tableSuffix)
                : shipBatchService.findBatchAcrossMonths(shopCode, shipCode);
        return batch == null ? error("发货批次不存在") : success(batch);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:add')")
    @Log(title = "发货批次", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ShipBatch batch)
    {
        ShipBatch created = shipBatchService.createBatch(SecurityUtils.getBusinessShopCode(), batch,
                SecurityUtils.getUsername());
        return success("发货批次创建成功：" + created.getShipCode());
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:edit')")
    @Log(title = "发货批次", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ShipBatch batch, @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(shipBatchService.updatePending(shopCode, batch,
                resolveSuffix(shopCode, batch.getShipCode(), tableSuffix), SecurityUtils.getUsername()));
    }

    /**
     * 确认并提交仓库：待确认 → 已提交仓库。
     *
     * <p>这是<b>订单侧</b>的动作，业务员点完批次就交给仓库了：仓库发货管理从这一刻起
     * 才看得到这张批次，而订单侧从这一刻起只剩查看，改不了也取消不了。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:confirm')")
    @Log(title = "发货批次提交仓库", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/confirm")
    public AjaxResult confirm(@PathVariable("shipCode") String shipCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(shipBatchService.confirmBatch(shopCode, shipCode,
                resolveSuffix(shopCode, shipCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /**
     * 打印备货单：已提交仓库 → 已打印备货单。仓库拿到批次后的第一步，照着单子去拣货。
     *
     * <p>权限 {@code order:shipbatch:print} 是仓库侧的打印动作专用的，
     * 下面的「打印快递单号」也用它——两件事都是「仓库把纸打出来」。</p>
     *
     * <p>原先这里沿用的是 {@code order:shipbatch:confirm}，那是业务员「提交仓库」的权限串，
     * 两个岗位共用一个开关，系统分不出谁该点哪个按钮。拆出来之后 confirm 就纯属销售侧了。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:print')")
    @Log(title = "发货批次打印备货单", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/print")
    public AjaxResult print(@PathVariable("shipCode") String shipCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(shipBatchService.printPickList(shopCode, shipCode,
                resolveSuffix(shopCode, shipCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /**
     * 备货完成：已打印备货单 → 备货完成。<b>不扣库存，货还在仓库里</b>。
     *
     * <p>库管员在弹窗里传备货照片，传完点这个按钮把球交给业务员。
     * {@code photos} 是逗号分隔的 URL 串（前端 ImageUpload 的 v-model 格式），
     * <b>至少要有一张</b>——业务员在录快递单号之前，只能靠这组照片确认货备成什么样。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:picking')")
    @Log(title = "发货批次备货完成", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/picking")
    public AjaxResult picking(@PathVariable("shipCode") String shipCode,
            @RequestBody(required = false) Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, shipCode, stringOf(params, "tableSuffix"));
        return toAjax(shipBatchService.completePicking(shopCode, shipCode, suffix,
                stringOf(params, "photos"), SecurityUtils.getUsername()));
    }

    /**
     * 补传 / 替换备货照片，<b>不改状态</b>。只认「已打印备货单 / 备货完成」两格。
     *
     * <p>存在的理由：照片传了一半网断了、或者点完备货完成才发现漏拍一张，
     * 没有这个口子就只能让仓库驳回整张批次重走一遍。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:picking')")
    @Log(title = "发货批次备货照片", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/photos")
    public AjaxResult photos(@PathVariable("shipCode") String shipCode,
            @RequestBody(required = false) Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, shipCode, stringOf(params, "tableSuffix"));
        return toAjax(shipBatchService.savePickPhotos(shopCode, shipCode, suffix,
                stringOf(params, "photos"), SecurityUtils.getUsername()));
    }

    /**
     * 「录入快递单号」弹窗要的数据：各行计划量与单价，加上这张订单的付款额度。
     *
     * <p>单开一个接口是因为算已占额度要枚举发货批次月表，比普通详情贵；
     * 而这些数字只有开那个弹窗时才用得上。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:logistics')")
    @GetMapping("/{shipCode}/logisticsInfo")
    public AjaxResult logisticsInfo(@PathVariable("shipCode") String shipCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return success(shipBatchService.loadLogisticsInfo(shopCode, shipCode,
                resolveSuffix(shopCode, shipCode, tableSuffix)));
    }

    /**
     * 录入快递单号：备货完成 → 已录入快递单号。<b>不扣库存，货也还在仓库里</b>。
     *
     * <p>业务员的活：填快递单号、承运商，并按仓库实际备好的货敲定每行的本次发货数量
     * （{@code qtys}，批次明细代码 → 数量，<b>只能往下调</b>；填 0 的行整行不发）。
     * 这一步<b>卡客户的付款额度</b>——整单累计的发货货值不能超过已收金额，
     * 口径见 {@code IShipBatchService#enterLogistics}。</p>
     *
     * <p>库存扣减在 {@code /complete}——那一步才对应承运商把货取走。录单到承运商上门
     * 之间还有变数（装错货、贴错面单、客户改主意），而扣库存不可逆，所以拆开走，
     * 中间这段时间批次随时可以 {@code /reject} 驳回。</p>
     *
     * <p>{@code carrier} 与 {@code logisticsNo} <b>都必填</b>（来源订单是「上门提货」的除外），
     * 不传且批次上原本也没有就会被拒。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:logistics')")
    @Log(title = "发货批次录入快递单号", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/logistics")
    public AjaxResult logistics(@PathVariable("shipCode") String shipCode,
            @RequestBody(required = false) Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, shipCode, stringOf(params, "tableSuffix"));
        return toAjax(shipBatchService.enterLogistics(shopCode, shipCode, suffix,
                stringOf(params, "logisticsNo"), stringOf(params, "carrier"),
                qtysOf(params), SecurityUtils.getUsername()));
    }

    /**
     * 打印快递单号：已录入快递单号 → 已打印快递单号。仓库把面单打出来贴箱子上。
     * <b>仍然不扣库存</b>，货还在仓库里等承运商。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:print')")
    @Log(title = "发货批次打印快递单号", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/printLabel")
    public AjaxResult printLabel(@PathVariable("shipCode") String shipCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(shipBatchService.printExpressLabel(shopCode, shipCode,
                resolveSuffix(shopCode, shipCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /**
     * 驳回：中间五格（已提交仓库 / 已打印备货单 / 备货完成 / 已录入快递单号 /
     * 已打印快递单号）→ 待确认。货还没被承运商取走就都能驳。
     *
     * <p>仓库的后悔药：装箱时才发现装错货、面单贴错、客户临时改主意，这些都发生在
     * 承运商上门之前，而那时批次可能已经推到「已发货」了。驳回把它整个退回业务员手里
     * ——退到「待确认」而不是停在原地，是因为批次的修改入口只认这一个状态。</p>
     *
     * <p>驳回原因必填，会落到批次上给业务员看。权限沿用 {@code order:shipbatch:ship}：
     * 能点「完成」把货放出门的人，就该能把批次拉回来。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:ship')")
    @Log(title = "发货批次驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/reject")
    public AjaxResult reject(@PathVariable("shipCode") String shipCode,
            @RequestBody(required = false) Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, shipCode, stringOf(params, "tableSuffix"));
        shipBatchService.rejectBatch(shopCode, shipCode, suffix, stringOf(params, "reason"),
                SecurityUtils.getUsername());
        return success("已驳回，批次退回「待确认」，请到「订单管理 → 发货批次」处理");
    }

    /**
     * 完成：已打印快递单号 → 已完成。<b>库存在这一步才真正扣，对应现实中承运商把货取走那一刻。</b>
     * 建销售出库单 → 同事务审核过账 → 回写订单已发量与进度，三步一个事务。
     *
     * <p>请求体里的 {@code serials}（明细代码 → 序列号数组）和 {@code batchCodes}
     * （明细代码 → 出库批次号）是给序列号管理、个别计价商品用的，其它商品不用传。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:ship')")
    @Log(title = "发货批次完成", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/complete")
    public AjaxResult complete(@PathVariable("shipCode") String shipCode,
            @RequestBody(required = false) Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, shipCode, stringOf(params, "tableSuffix"));
        String docCode = shipBatchService.completeBatch(shopCode, shipCode, suffix,
                serialsOf(params), batchCodesOf(params), SecurityUtils.getUsername());
        return success("已完成，库存已扣减，出库单：" + docCode);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:cancel')")
    @Log(title = "发货批次取消", businessType = BusinessType.UPDATE)
    @PostMapping("/{shipCode}/cancel")
    public AjaxResult cancel(@PathVariable("shipCode") String shipCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(shipBatchService.cancelBatch(shopCode, shipCode,
                resolveSuffix(shopCode, shipCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /**
     * 请求体里的序列号：{@code {"明细代码": ["SN1","SN2"]}}。
     *
     * <p>前端传什么形状都不该让接口 500，所以逐层判类型，认不出来的一律当没传——
     * 真缺了序列号，销售出库单会在过账前拦下来并说清是哪个商品缺几个。</p>
     */
    private Map<String, List<String>> serialsOf(Map<String, Object> params)
    {
        Object raw = params == null ? null : params.get("serials");
        if (!(raw instanceof Map)) return Collections.emptyMap();
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (entry.getKey() == null || !(entry.getValue() instanceof List)) continue;
            List<String> values = new ArrayList<>();
            for (Object value : (List<?>) entry.getValue())
            {
                String text = value == null ? "" : String.valueOf(value).trim();
                if (!text.isEmpty()) values.add(text);
            }
            if (!values.isEmpty()) result.put(String.valueOf(entry.getKey()), values);
        }
        return result;
    }

    /**
     * 请求体里的本次发货数量：{@code {"批次明细代码": 8}}。
     *
     * <p>和 {@link #serialsOf} 同一个路子，逐层判类型，认不出来的一律当没传——
     * 前端传什么形状都不该让接口 500。没传的行由服务层按批次原本排的计划量发。</p>
     *
     * <p>数值一律经 {@code new BigDecimal(String)} 转，<b>不走 doubleValue()</b>：
     * JSON 里的 8.1 变成 double 再转 BigDecimal 会得到 8.0999999999999996，
     * 拿它去和计划量比大小会莫名其妙地判成「超了」。</p>
     */
    private Map<String, BigDecimal> qtysOf(Map<String, Object> params)
    {
        Object raw = params == null ? null : params.get("qtys");
        if (!(raw instanceof Map)) return Collections.emptyMap();
        Map<String, BigDecimal> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String text = String.valueOf(entry.getValue()).trim();
            if (text.isEmpty()) continue;
            try
            {
                result.put(String.valueOf(entry.getKey()), new BigDecimal(text));
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("本次发货数量'" + text + "'不是合法数字");
            }
        }
        return result;
    }

    /** 请求体里的出库批次：{@code {"明细代码": "批次号"}}。只有个别计价的批次管理商品要传。 */
    private Map<String, String> batchCodesOf(Map<String, Object> params)
    {
        Object raw = params == null ? null : params.get("batchCodes");
        if (!(raw instanceof Map)) return Collections.emptyMap();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String value = String.valueOf(entry.getValue()).trim();
            if (!value.isEmpty()) result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    /** 前端给了后缀就用，没给就跨月找。列表返回的每一行都带 tableSuffix，应原样回传。 */
    private String resolveSuffix(String shopCode, String shipCode, String tableSuffix)
    {
        if (MonthTableSuffix.isValid(tableSuffix)) return tableSuffix;
        ShipBatch batch = shipBatchService.findBatchAcrossMonths(shopCode, shipCode);
        if (batch == null) throw new ServiceException("发货批次不存在");
        return batch.getTableSuffix();
    }

    private String stringOf(Map<String, Object> params, String key)
    {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

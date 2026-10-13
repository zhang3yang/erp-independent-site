package com.ruoyi.web.controller.purchase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.PurchaseOrderItem;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.service.IPurchaseOrderService;
import com.ruoyi.system.service.IPurchaseReceiveService;

/**
 * 采购订单Controller
 */
@RestController
@RequestMapping("/purchase/order")
public class PurchaseOrderController extends BaseController
{
    @Autowired
    private IPurchaseOrderService purchaseOrderService;

    @Autowired
    private IPurchaseReceiveService purchaseReceiveService;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    /**
     * 查询采购订单列表
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(PurchaseOrder purchaseOrder)
    {
        startPage();
        // 设置商家编码
        purchaseOrder.setShopCode(SecurityUtils.getBusinessShopCode());
        List<PurchaseOrder> list = purchaseOrderService.selectPurchaseOrderList(purchaseOrder);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:list')")
    @GetMapping("/available")
    public AjaxResult available()
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        PurchaseOrder approved = new PurchaseOrder();
        approved.setShopCode(shopCode);
        approved.setStatus("approved");
        List<PurchaseOrder> result = new ArrayList<>(purchaseOrderService.selectPurchaseOrderList(approved));
        PurchaseOrder partial = new PurchaseOrder();
        partial.setShopCode(shopCode);
        partial.setStatus("partial_received");
        result.addAll(purchaseOrderService.selectPurchaseOrderList(partial));
        return success(filterFullyShipped(shopCode, result));
    }

    /**
     * 还要付款的采购单，供财务建采购付款流水时选单。
     *
     * <p>权限用 finance:transaction:add：调用方是财务的建单弹窗，财务未必有采购模块的列表权限。</p>
     *
     * <p>不分页。原先前端是拉 /list 的第一页 200 条再自己过滤，超过 200 张采购单的商家
     * 从第 201 张起压根进不了下拉，且没有任何提示。筛选下沉到 SQL 后没有这个上限。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:add')")
    @GetMapping("/payable")
    public AjaxResult payable(@RequestParam(required = false) String includeCode)
    {
        return success(purchaseOrderService.selectPayableOrders(
                SecurityUtils.getBusinessShopCode(), includeCode));
    }

    /**
     * 可退款的采购单，供财务建采购退款流水时选单。
     *
     * <p>与 /payable 是<b>相反</b>的集合：那边列还没付满的，这边列付过钱的（已付货款 &gt; 0）。
     * 已取消的采购单也要列出来——取消后把预付款要回来，正是采购退款最常见的场景。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:add')")
    @GetMapping("/refundable")
    public AjaxResult refundable(@RequestParam(required = false) String includeCode)
    {
        return success(purchaseOrderService.selectRefundableOrders(
                SecurityUtils.getBusinessShopCode(), includeCode));
    }

    /**
     * 查询采购订单明细。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:query')")
    @GetMapping("/items")
    public AjaxResult getItems(String purchaseCode)
    {
        if (purchaseCode == null || purchaseCode.trim().isEmpty())
        {
            return error("采购单号不能为空");
        }

        String shopCode = SecurityUtils.getBusinessShopCode();
        PurchaseOrder order = purchaseOrderService.selectPurchaseOrderByCode(shopCode, purchaseCode.trim());
        if (order == null)
        {
            return error("采购订单不存在");
        }
        fillProcessedQty(shopCode, order.getItems());
        return success(order.getItems());
    }

    /**
     * 回填「已处理数量」= 已到货 + 在途。
     *
     * <p>已到货（receivedqty）只在入库单审核通过时才加，光看它会让刚登记完发货的货
     * 看起来还没动过，同一批货能被重复登记发货。在途量补上「待入库」和「入库中」两段。</p>
     */
    private void fillProcessedQty(String shopCode, List<PurchaseOrderItem> items)
    {
        if (items == null || items.isEmpty()) return;
        List<String> detailCodes = new ArrayList<>();
        for (PurchaseOrderItem item : items)
        {
            if (item.getDetailCode() != null) detailCodes.add(item.getDetailCode());
        }
        Map<String, BigDecimal> pending = purchaseReceiveService.selectPendingQtyByDetails(shopCode, detailCodes);
        for (PurchaseOrderItem item : items)
        {
            item.setProcessedQty(nvl(item.getReceivedQty())
                    .add(nvl(pending.get(item.getDetailCode()))));
        }
    }

    /**
     * 把已经全部发完的订单从发货下拉里去掉：每条明细的剩余可发量都是 0 的订单，
     * 采购员选进来只会看到一排 0，没有意义。
     *
     * <p>状态过滤挡不住这种单子——采购订单要等入库单审核通过才会变成「已完成」，
     * 货登记发货之后、入库员处理之前，订单一直停在「已审核 / 部分到货」。</p>
     */
    private List<PurchaseOrder> filterFullyShipped(String shopCode, List<PurchaseOrder> orders)
    {
        if (orders.isEmpty()) return orders;
        List<String> purchaseCodes = new ArrayList<>();
        for (PurchaseOrder order : orders) purchaseCodes.add(order.getPurchaseCode());

        Map<String, List<PurchaseOrderItem>> itemsByOrder = new LinkedHashMap<>();
        List<String> detailCodes = new ArrayList<>();
        for (PurchaseOrderItem item : purchaseOrderItemMapper.selectItemsByPurchaseCodes(shopCode, purchaseCodes))
        {
            itemsByOrder.computeIfAbsent(item.getPurchaseCode(), k -> new ArrayList<>()).add(item);
            if (item.getDetailCode() != null) detailCodes.add(item.getDetailCode());
        }
        Map<String, BigDecimal> pending = purchaseReceiveService.selectPendingQtyByDetails(shopCode, detailCodes);

        List<PurchaseOrder> shippable = new ArrayList<>();
        for (PurchaseOrder order : orders)
        {
            List<PurchaseOrderItem> items = itemsByOrder.get(order.getPurchaseCode());
            // 明细查不到就保留，宁可多列一张也不要因为数据异常让采购员发不了货
            if (items == null || items.isEmpty() || hasRemaining(items, pending)) shippable.add(order);
        }
        return shippable;
    }

    /** 是否还有任意一条明细可以继续发货 */
    private boolean hasRemaining(List<PurchaseOrderItem> items, Map<String, BigDecimal> pending)
    {
        for (PurchaseOrderItem item : items)
        {
            BigDecimal processed = nvl(item.getReceivedQty()).add(nvl(pending.get(item.getDetailCode())));
            if (nvl(item.getQty()).subtract(processed).signum() > 0) return true;
        }
        return false;
    }

    private static BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 获取采购订单详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        PurchaseOrder order = purchaseOrderService.selectPurchaseOrderById(SecurityUtils.getBusinessShopCode(), id);
        if (order == null)
        {
            return error("采购订单不存在");
        }
        return success(order);
    }

    /**
     * 新增采购订单
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:add')")
    @Log(title = "采购订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody PurchaseOrder purchaseOrder)
    {
        // 设置商家编码和创建人
        purchaseOrder.setShopCode(SecurityUtils.getBusinessShopCode());
        purchaseOrder.setCreateUser(SecurityUtils.getUsername());

        return toAjax(purchaseOrderService.insertPurchaseOrder(purchaseOrder));
    }

    /**
     * 修改采购订单
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:edit')")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody PurchaseOrder purchaseOrder)
    {
        // 校验商家权限
        PurchaseOrder existing = purchaseOrderService.selectPurchaseOrderById(
                SecurityUtils.getBusinessShopCode(), purchaseOrder.getId());
        if (existing == null)
        {
            return error("采购订单不存在");
        }
        // 设置商家编码和更新人
        purchaseOrder.setShopCode(SecurityUtils.getBusinessShopCode());
        purchaseOrder.setUpdateUser(SecurityUtils.getUsername());

        return toAjax(purchaseOrderService.updatePurchaseOrder(purchaseOrder));
    }

    /**
     * 删除采购订单
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:remove')")
    @Log(title = "采购订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(purchaseOrderService.deletePurchaseOrderByIds(SecurityUtils.getBusinessShopCode(), ids));
    }

    /**
     * 提交采购订单审核
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:submit')")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{purchaseCode}/submit")
    public AjaxResult submit(@PathVariable("purchaseCode") String purchaseCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String username = SecurityUtils.getUsername();
        return toAjax(purchaseOrderService.submitPurchaseOrder(shopCode, purchaseCode, username));
    }

    /**
     * 审核采购订单
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:approve')")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{purchaseCode}/approve")
    public AjaxResult approve(@PathVariable("purchaseCode") String purchaseCode,
            @RequestBody(required = false) java.util.Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String username = SecurityUtils.getUsername();

        // 默认审核通过，可以通过参数控制驳回
        boolean approved = true;
        if (params != null && params.containsKey("approved"))
        {
            approved = Boolean.TRUE.equals(params.get("approved"));
        }
        // 驳回原因由服务层校验必填，这里只负责取值
        Object reason = params == null ? null : params.get("rejectReason");

        return toAjax(purchaseOrderService.approvePurchaseOrder(shopCode, purchaseCode, approved,
                reason == null ? null : String.valueOf(reason), username));
    }

    /**
     * 取消采购订单
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:edit')")
    @Log(title = "采购订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{purchaseCode}/cancel")
    public AjaxResult cancel(@PathVariable("purchaseCode") String purchaseCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String username = SecurityUtils.getUsername();
        return toAjax(purchaseOrderService.cancelPurchaseOrder(shopCode, purchaseCode, username));
    }

    /**
     * 录入物流信息。供应商发货后由采购员填写，库管员据此创建到货单。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:logistics')")
    @Log(title = "采购订单物流", businessType = BusinessType.UPDATE)
    @PostMapping("/{purchaseCode}/logistics")
    public AjaxResult logistics(@PathVariable("purchaseCode") String purchaseCode,
            @RequestBody PurchaseOrder command)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String username = SecurityUtils.getUsername();
        return toAjax(purchaseOrderService.updateLogistics(shopCode, purchaseCode,
                command.getLogisticsNo(), command.getCarrier(), username));
    }
}

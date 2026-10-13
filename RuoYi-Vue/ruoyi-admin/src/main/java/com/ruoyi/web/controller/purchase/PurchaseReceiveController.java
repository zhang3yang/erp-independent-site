package com.ruoyi.web.controller.purchase;

import java.util.List;
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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.PurchaseReceive;
import com.ruoyi.system.domain.vo.PurchaseShortageItem;
import com.ruoyi.system.service.IPurchaseReceiveService;

/**
 * 采购发货（原「采购到货与质检」）。
 *
 * <p>采购员在这里登记供应商发了什么、发了多少（预计到货数量）。
 * 签收、实际到货数量、批次序列号、库存过账都在库存模块的
 * 「入库信息 → 采购入库」，不在本控制器。</p>
 */
@RestController
@RequestMapping("/purchase/receive")
public class PurchaseReceiveController extends BaseController
{
    @Autowired private IPurchaseReceiveService service;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:list')")
    @GetMapping("/list")
    public TableDataInfo list(PurchaseReceive query, @RequestParam String tableSuffix)
    {
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        startPage();
        return getDataTable(service.selectReceiveList(query, tableSuffix));
    }

    /**
     * 可供入库的发货单，跨全部月表查询。
     *
     * <p>给库存模块的采购入库页选单用——入库员并不知道发货单落在哪个月，
     * 所以这里不要求传 tableSuffix。权限挂 {@code purchase:receive:list}，
     * 因为返回的是发货单数据。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:list')")
    @GetMapping("/pending")
    public AjaxResult pending()
    {
        return success(service.selectPendingShipments(SecurityUtils.getBusinessShopCode()));
    }

    /**
     * 某采购订单的短装汇总，跨全部月表统计。
     *
     * <p>决议 A1-2：短装差额本身已经留在采购剩余可发量里，这个接口只是把
     * 「哪张发货单、哪个商品、短了多少」列出来供采购员追货。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:order:query')")
    @GetMapping("/shortage")
    public AjaxResult shortage(@RequestParam String purchaseCode)
    {
        List<PurchaseShortageItem> items = service.selectShortageItems(
                SecurityUtils.getBusinessShopCode(), purchaseCode);
        return success(items);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:query')")
    @GetMapping("/{receiveCode}")
    public AjaxResult detail(@PathVariable String receiveCode, @RequestParam String tableSuffix)
    {
        PurchaseReceive result = service.selectReceiveDetail(
                SecurityUtils.getBusinessShopCode(), receiveCode, tableSuffix);
        return result == null ? error("发货单不存在") : success(result);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:add')")
    @Log(title = "采购发货", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PurchaseReceive command)
    {
        return success(service.createReceive(
                SecurityUtils.getBusinessShopCode(), command, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:edit')")
    @Log(title = "采购发货", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestParam String tableSuffix, @RequestBody PurchaseReceive command)
    {
        return toAjax(service.updateDraft(
                SecurityUtils.getBusinessShopCode(), command, tableSuffix, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('purchase:receive:cancel')")
    @Log(title = "采购发货", businessType = BusinessType.DELETE)
    @PostMapping("/{receiveCode}/cancel")
    public AjaxResult cancel(@PathVariable String receiveCode, @RequestParam String tableSuffix)
    {
        return toAjax(service.cancelReceive(
                SecurityUtils.getBusinessShopCode(), receiveCode, tableSuffix, SecurityUtils.getUsername()));
    }
}

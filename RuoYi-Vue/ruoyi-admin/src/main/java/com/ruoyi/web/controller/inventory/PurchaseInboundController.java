package com.ruoyi.web.controller.inventory;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.service.IPurchaseInboundService;
import com.ruoyi.system.service.IPurchaseInboundService.InboundItem;
import com.ruoyi.system.service.impl.DocumentServiceImpl;

/**
 * 采购入库Controller
 */
@RestController
@RequestMapping("/inventory/purchase-inbound")
public class PurchaseInboundController extends BaseController
{
    @Autowired
    private IPurchaseInboundService purchaseInboundService;

    @Autowired
    private DocumentServiceImpl documentServiceImpl;

    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "入库信息管理", businessType = BusinessType.INSERT)
    @PostMapping("/direct")
    public AjaxResult createDirectInbound(@RequestBody DocumentMain document)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        try
        {
            DocumentMain inbound = purchaseInboundService.createDirectInbound(
                    shopCode, document, SecurityUtils.getUsername());
            return success("入库单创建成功：" + inbound.getDocCode());
        }
        catch (Exception e) { return error(e.getMessage()); }
    }

    /**
     * 从采购订单生成入库单
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "入库信息管理", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult createInbound(@RequestParam("purchaseCode") String purchaseCode,
            @RequestBody List<InboundItem> inboundItems)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        try
        {
            DocumentMain inbound = purchaseInboundService.createInboundFromPurchase(
                    shopCode, purchaseCode, inboundItems, SecurityUtils.getUsername());
            return success("入库单创建成功：" + inbound.getDocCode());
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }

    /**
     * 由采购发货单创建采购入库单。
     *
     * <p>入库员在「入库信息 → 采购入库」发起：选一张待入库的发货单整单入库，
     * 填实际到货数量、签收人、签收时间、仓库、批次序列号。
     * 生成的入库单是待审核状态，审核通过才真正过账。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:inbound:add')")
    @Log(title = "采购入库", businessType = BusinessType.INSERT)
    @PostMapping("/from-shipment")
    public AjaxResult createFromShipment(@RequestBody IPurchaseInboundService.ShipmentInboundCommand command)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        try
        {
            DocumentMain inbound = purchaseInboundService.createInboundFromShipment(
                    shopCode, command, SecurityUtils.getUsername());
            return success(inbound);
        }
        catch (Exception e) { return error(e.getMessage()); }
    }

    /**
     * 审核采购入库单     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "入库信息管理", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm")
    public AjaxResult confirmInbound(@RequestParam("docCode") String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix,
            @RequestParam("approved") boolean approved)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String confirmUser = SecurityUtils.getUsername();
        tableSuffix = documentServiceImpl.validateTableSuffix(tableSuffix);
        try
        {
            int result = purchaseInboundService.confirmPurchaseInbound(shopCode, docCode, tableSuffix, approved, confirmUser);
            return toAjax(result);
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }
}

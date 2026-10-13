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
import com.ruoyi.system.service.IOutboundService;
import com.ruoyi.system.service.IOutboundService.OutboundItem;
import com.ruoyi.system.service.impl.DocumentServiceImpl;

/**
 * 出库Controller
 */
@RestController
@RequestMapping("/inventory/outbound")
public class OutboundController extends BaseController
{
    @Autowired
    private IOutboundService outboundService;

    @Autowired
    private DocumentServiceImpl documentServiceImpl;

    /**
     * 创建出库单     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "出库单管理", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult createOutbound(@RequestParam("docType") String docType,
            @RequestParam("fromWareCode") String fromWareCode,
            @RequestParam(value = "fromQualityFlag", required = false) Integer fromQualityFlag,
            @RequestParam(value = "fromOnhandFlag", required = false) Integer fromOnhandFlag,
            @RequestParam(value = "partnerCode", required = false) String partnerCode,
            @RequestParam(value = "partnerName", required = false) String partnerName,
            @RequestParam(value = "sourceDocCode", required = false) String sourceDocCode,
            @RequestParam(value = "sourceDocType", required = false) String sourceDocType,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestBody List<OutboundItem> outboundItems)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String createUser = SecurityUtils.getUsername();
        // 出库默认从可用库存扣；前端要出不良品或被占用的货才需要显式传三维状态
        StockState fromState = fromQualityFlag == null && fromOnhandFlag == null
                ? StockState.available() : StockState.of(fromQualityFlag, fromOnhandFlag);
        try
        {
            DocumentMain outbound = outboundService.createOutbound(shopCode, docType, fromWareCode, fromState,
                    partnerCode, partnerName, sourceDocCode, sourceDocType, outboundItems, remark, createUser);
            return success("出库单创建成功：" + outbound.getDocCode());
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }

    /**
     * 审核出库单     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "出库单管理", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm")
    public AjaxResult confirmOutbound(@RequestParam("docCode") String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix,
            @RequestParam("approved") boolean approved)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String confirmUser = SecurityUtils.getUsername();
        tableSuffix = documentServiceImpl.validateTableSuffix(tableSuffix);
        try
        {
            int result = outboundService.confirmOutbound(shopCode, docCode, tableSuffix, approved, confirmUser);
            return toAjax(result);
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }
}


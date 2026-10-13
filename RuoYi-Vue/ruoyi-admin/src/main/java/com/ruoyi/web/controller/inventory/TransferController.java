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
import com.ruoyi.system.service.ITransferService;
import com.ruoyi.system.service.ITransferService.TransferItem;
import com.ruoyi.system.service.impl.DocumentServiceImpl;

/**
 * 调拨Controller
 */
@RestController
@RequestMapping("/inventory/transfer")
public class TransferController extends BaseController
{
    @Autowired
    private ITransferService transferService;

    @Autowired
    private DocumentServiceImpl documentServiceImpl;

    /**
     * 创建调拨单     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "调拨单管理", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult createTransfer(@RequestParam("fromWareCode") String fromWareCode,
            @RequestParam(value = "fromQualityFlag", required = false) Integer fromQualityFlag,
            @RequestParam(value = "fromOnhandFlag", required = false) Integer fromOnhandFlag,
            @RequestParam("toWareCode") String toWareCode,
            @RequestParam(value = "toQualityFlag", required = false) Integer toQualityFlag,
            @RequestParam(value = "toOnhandFlag", required = false) Integer toOnhandFlag,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestBody List<TransferItem> transferItems)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String createUser = SecurityUtils.getUsername();
        // 调拨只搬仓库不改状态，前端不传时按可用库存处理
        StockState fromState = fromQualityFlag == null && fromOnhandFlag == null
                ? StockState.available() : StockState.of(fromQualityFlag, fromOnhandFlag);
        StockState toState = toQualityFlag == null && toOnhandFlag == null
                ? fromState : StockState.of(toQualityFlag, toOnhandFlag);
        try
        {
            DocumentMain transfer = transferService.createTransfer(shopCode, fromWareCode, fromState,
                    toWareCode, toState, transferItems, remark, createUser);
            return success("调拨单创建成功：" + transfer.getDocCode());
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }

    /**
     * 审核调拨单     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "调拨单管理", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm")
    public AjaxResult confirmTransfer(@RequestParam("docCode") String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix,
            @RequestParam("approved") boolean approved)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String confirmUser = SecurityUtils.getUsername();
        tableSuffix = documentServiceImpl.validateTableSuffix(tableSuffix);
        try
        {
            int result = transferService.confirmTransfer(shopCode, docCode, tableSuffix, approved, confirmUser);
            return toAjax(result);
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }
}


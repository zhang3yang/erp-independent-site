package com.ruoyi.web.controller.inventory;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.InventoryLedger;
import com.ruoyi.system.service.IInventoryLedgerService;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.impl.DocumentServiceImpl;

/**
 * 库存流水Controller
 */
@RestController
@RequestMapping("/inventory/ledger")
public class InventoryLedgerController extends BaseController
{
    @Autowired
    private IInventoryLedgerService ledgerService;

    @Autowired
    private IDocumentService documentService;

    @Autowired
    private DocumentServiceImpl documentServiceImpl;

    /**
     * 查询库存流水列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventoryLedger ledger)
    {
        startPage();
        ledger.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventoryLedger> list = ledgerService.selectLedgerList(ledger);
        return getDataTable(list);
    }

    /**
     * 获取库存流水详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{ledgerCode}")
    public AjaxResult getInfo(@PathVariable("ledgerCode") String ledgerCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        tableSuffix = documentServiceImpl.validateTableSuffix(tableSuffix);
        InventoryLedger ledger = ledgerService.selectLedgerByCode(shopCode, ledgerCode, tableSuffix);
        if (ledger == null)
        {
            return error("库存流水不存在");
        }
        return success(ledger);
    }
}


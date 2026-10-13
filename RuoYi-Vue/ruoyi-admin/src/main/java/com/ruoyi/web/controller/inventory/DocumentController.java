package com.ruoyi.web.controller.inventory;

import java.util.List;
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
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IPurchaseInboundService;
import com.ruoyi.system.service.impl.InventoryDocumentTableRouter;

/**
 * 单据Controller
 */
@RestController
@RequestMapping("/inventory/document")
public class DocumentController extends BaseController
{
    @Autowired
    private IDocumentService documentService;

    @Autowired
    private InventoryDocumentTableRouter tableRouter;

    @Autowired
    private IPurchaseInboundService purchaseInboundService;

    /**
     * 查询单据列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(DocumentMain document)
    {
        startPage();
        document.setShopCode(SecurityUtils.getBusinessShopCode());
        List<DocumentMain> list = documentService.selectDocumentList(document);
        return getDataTable(list);
    }

    /**
     * 获取单据详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{docCode}")
    public AjaxResult getInfo(@PathVariable("docCode") String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        tableSuffix = tableRouter.validate(tableSuffix);
        DocumentMain document = documentService.selectDocumentByCode(shopCode, docCode, tableSuffix);
        if (document == null)
        {
            return error("单据不存在");
        }
        return success(document);
    }

    /**
     * 新增单据
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存单据管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DocumentMain document)
    {
        document.setShopCode(SecurityUtils.getBusinessShopCode());
        document.setCreateUser(SecurityUtils.getUsername());
        // 主单号、明细号均由服务端 Redis 规则生成，忽略客户端传入的主单号。
        document.setDocCode(null);
        document.setIdempotencyKey(requestIdempotencyKey());
        return toAjax(documentService.insertDocument(document));
    }

    private String requestIdempotencyKey()
    {
        return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() == null
                ? null
                : ((org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                        .getRequest().getHeader("Idempotency-Key");
    }

    /**
     * 修改单据
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存单据管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DocumentMain document)
    {
        document.setShopCode(SecurityUtils.getBusinessShopCode());
        return toAjax(documentService.updateDocument(document));
    }

    /**
     * 删除单据
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存单据管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{docCode}")
    public AjaxResult remove(@PathVariable("docCode") String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        tableSuffix = tableRouter.validate(tableSuffix);
        return toAjax(documentService.deleteDocument(shopCode, docCode, tableSuffix));
    }

    /**
     * 审核单据
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存单据管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{docCode}/confirm")
    public AjaxResult confirm(@PathVariable("docCode") String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix,
            @RequestParam("approved") boolean approved)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String confirmUser = SecurityUtils.getUsername();
        tableSuffix = tableRouter.validate(tableSuffix);
        DocumentMain document = documentService.selectDocumentByCode(shopCode, docCode, tableSuffix);
        // 成本调整审核要 inventory:costadjust:approve 和 price:cost，通用接口只校验了商家身份
        if (document != null && InventoryDocumentTypes.COST_ADJUST.equals(document.getDocType()))
        {
            return error("成本调整单请到「成本调整」页面审核");
        }
        if (document != null && InventoryDocumentTypes.PURCHASE_INBOUND.equals(document.getDocType()))
        {
            return toAjax(purchaseInboundService.confirmPurchaseInbound(
                    shopCode, docCode, tableSuffix, approved, confirmUser));
        }
        return toAjax(documentService.confirmDocument(shopCode, docCode, tableSuffix, approved, confirmUser));
    }
}


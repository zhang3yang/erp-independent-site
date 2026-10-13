package com.ruoyi.web.controller.inventory;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.ruoyi.system.service.IInventoryCostAdjustService;
import com.ruoyi.system.service.IInventoryCostAdjustService.CreateCommand;

/**
 * 成本调整Controller
 *
 * <p>数量不动、只改库存成本。建单、审核、预览都额外要求 price:cost——
 * 看不到成本的人没法判断该调多少，也不该能改它。</p>
 */
@RestController
@RequestMapping("/inventory/costadjust")
public class InventoryCostAdjustController extends BaseController
{
    @Autowired
    private IInventoryCostAdjustService costAdjustService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:list')")
    @GetMapping("/list")
    public TableDataInfo list(DocumentMain query)
    {
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        startPage();
        return getDataTable(costAdjustService.selectCostAdjustList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:list')")
    @GetMapping("/{docCode}")
    public AjaxResult getInfo(@PathVariable String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        return success(costAdjustService.selectCostAdjustDetail(SecurityUtils.getBusinessShopCode(), docCode, tableSuffix));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:add') and @ss.hasPermi('price:cost')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam("wareCode") String wareCode, @RequestParam("goodsCode") String goodsCode,
            @RequestParam(value = "batchCode", required = false) String batchCode,
            @RequestParam(value = "serialNos", required = false) List<String> serialNos)
    {
        return success(costAdjustService.preview(SecurityUtils.getBusinessShopCode(), wareCode, goodsCode,
                batchCode, serialNos));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:add') and @ss.hasPermi('price:cost')")
    @Log(title = "成本调整", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CreateCommand command)
    {
        DocumentMain document = costAdjustService.createCostAdjust(SecurityUtils.getBusinessShopCode(), command,
                SecurityUtils.getUsername());
        AjaxResult result = success("成本调整单创建成功：" + document.getDocCode());
        result.put("docCode", document.getDocCode());
        result.put("tableSuffix", document.getTableSuffix());
        return result;
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:approve') and @ss.hasPermi('price:cost')")
    @Log(title = "成本调整", businessType = BusinessType.UPDATE)
    @PostMapping("/{docCode}/approve")
    public AjaxResult approve(@PathVariable String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        costAdjustService.approve(SecurityUtils.getBusinessShopCode(), docCode, tableSuffix, SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:approve')")
    @Log(title = "成本调整", businessType = BusinessType.UPDATE)
    @PostMapping("/{docCode}/reject")
    public AjaxResult reject(@PathVariable String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        costAdjustService.reject(SecurityUtils.getBusinessShopCode(), docCode, tableSuffix, SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:costadjust:add')")
    @Log(title = "成本调整", businessType = BusinessType.DELETE)
    @DeleteMapping("/{docCode}")
    public AjaxResult remove(@PathVariable String docCode,
            @RequestParam(value = "tableSuffix", required = false) String tableSuffix)
    {
        costAdjustService.delete(SecurityUtils.getBusinessShopCode(), docCode, tableSuffix);
        return success();
    }
}

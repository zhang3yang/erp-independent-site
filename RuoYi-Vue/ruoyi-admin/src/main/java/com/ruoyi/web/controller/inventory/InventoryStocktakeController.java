package com.ruoyi.web.controller.inventory;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.InventoryStocktake;
import com.ruoyi.system.service.IInventoryStocktakeService;
import com.ruoyi.system.service.IInventoryStocktakeService.CountLine;
import com.ruoyi.system.service.IInventoryStocktakeService.CreateCommand;

/**
 * 库存盘点Controller
 *
 * <p>新建即快照账面、冻结单上商品 → 录实盘数 → 提交 → 审核（生成盘盈入库/盘亏出库并过账）。
 * 录入类操作要 inventory:stocktake:add，审核和驳回要 inventory:stocktake:approve。</p>
 */
@RestController
@RequestMapping("/inventory/stocktake")
public class InventoryStocktakeController extends BaseController
{
    @Autowired
    private IInventoryStocktakeService stocktakeService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:list')")
    @GetMapping("/list")
    public TableDataInfo list(InventoryStocktake query)
    {
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        startPage();
        return getDataTable(stocktakeService.selectStocktakeList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:query')")
    @GetMapping("/{stocktakeCode}")
    public AjaxResult getInfo(@PathVariable String stocktakeCode)
    {
        return success(stocktakeService.selectStocktakeDetail(SecurityUtils.getBusinessShopCode(), stocktakeCode));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:add')")
    @Log(title = "库存盘点", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CreateCommand command)
    {
        InventoryStocktake stocktake = stocktakeService.createStocktake(SecurityUtils.getBusinessShopCode(),
                command, SecurityUtils.getUsername());
        AjaxResult result = success("盘点单创建成功：" + stocktake.getStocktakeCode());
        result.put("stocktakeCode", stocktake.getStocktakeCode());
        return result;
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:add')")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PutMapping("/{stocktakeCode}/counts")
    public AjaxResult saveCounts(@PathVariable String stocktakeCode, @RequestBody List<CountLine> lines)
    {
        stocktakeService.saveCounts(SecurityUtils.getBusinessShopCode(), stocktakeCode, lines);
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:add')")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/{stocktakeCode}/items")
    @SuppressWarnings("unchecked")
    public AjaxResult addItems(@PathVariable String stocktakeCode, @RequestBody Map<String, Object> body)
    {
        List<String> goodsCodes = (List<String>) body.get("goodsCodes");
        Object quality = body.get("qualityFlag");
        Integer qualityFlag = quality == null ? null : Integer.valueOf(quality.toString());
        stocktakeService.addItems(SecurityUtils.getBusinessShopCode(), stocktakeCode, goodsCodes, qualityFlag);
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:add')")
    @Log(title = "库存盘点", businessType = BusinessType.DELETE)
    @DeleteMapping("/{stocktakeCode}/items/{itemId}")
    public AjaxResult removeItem(@PathVariable String stocktakeCode, @PathVariable Long itemId)
    {
        stocktakeService.removeItem(SecurityUtils.getBusinessShopCode(), stocktakeCode, itemId);
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:add')")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/{stocktakeCode}/submit")
    public AjaxResult submit(@PathVariable String stocktakeCode)
    {
        stocktakeService.submit(SecurityUtils.getBusinessShopCode(), stocktakeCode, SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:add')")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/{stocktakeCode}/cancel")
    public AjaxResult cancel(@PathVariable String stocktakeCode)
    {
        stocktakeService.cancel(SecurityUtils.getBusinessShopCode(), stocktakeCode, SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:approve')")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/{stocktakeCode}/reject")
    public AjaxResult reject(@PathVariable String stocktakeCode, @RequestBody Map<String, String> body)
    {
        stocktakeService.reject(SecurityUtils.getBusinessShopCode(), stocktakeCode,
                body == null ? null : body.get("reason"), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:stocktake:approve')")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/{stocktakeCode}/approve")
    public AjaxResult approve(@PathVariable String stocktakeCode)
    {
        return success(stocktakeService.approve(SecurityUtils.getBusinessShopCode(), stocktakeCode,
                SecurityUtils.getUsername()));
    }
}

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
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.service.IInventoryWarehouseService;

/**
 * 仓库Controller
 */
@RestController
@RequestMapping("/inventory/warehouses")
public class InventoryWarehouseController extends BaseController
{
    @Autowired
    private IInventoryWarehouseService warehouseService;

    /**
     * 查询仓库列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventoryWarehouse warehouse)
    {
        startPage();
        // 设置商家编码
        warehouse.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventoryWarehouse> list = warehouseService.selectWarehouseList(warehouse);
        return getDataTable(list);
    }

    /**
     * 获取仓库详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{wareCode}")
    public AjaxResult getInfo(@PathVariable("wareCode") String wareCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventoryWarehouse warehouse = warehouseService.selectWarehouseByCode(shopCode, wareCode);
        if (warehouse == null)
        {
            return error("仓库不存在");
        }
        return success(warehouse);
    }

    /**
     * 新增仓库
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "仓库管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventoryWarehouse warehouse)
    {
        // 设置商家编码
        warehouse.setShopCode(SecurityUtils.getBusinessShopCode());

        return toAjax(warehouseService.insertWarehouse(warehouse));
    }

    /**
     * 修改仓库
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "仓库管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventoryWarehouse warehouse)
    {
        // 设置商家编码
        warehouse.setShopCode(SecurityUtils.getBusinessShopCode());

        return toAjax(warehouseService.updateWarehouse(warehouse));
    }

    /**
     * 删除仓库
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "仓库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{wareCodes}")
    public AjaxResult remove(@PathVariable String[] wareCodes)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(warehouseService.deleteWarehouseByCodes(shopCode, wareCodes));
    }
}


package com.ruoyi.web.controller.order;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.OrderPlatform;
import com.ruoyi.system.domain.OrderPlatformStore;
import com.ruoyi.system.service.IOrderPlatformService;
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

/** 平台及其销售店铺维护。 */
@RestController
@RequestMapping("/order/platform")
public class OrderPlatformController extends BaseController
{
    @Autowired private IOrderPlatformService service;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:list')")
    @GetMapping("/list")
    public TableDataInfo list(OrderPlatform query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(service.selectPlatformList(query));
    }

    /** 销售订单下拉使用，不分页，只返回启用项。 */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/options")
    public AjaxResult options()
    {
        OrderPlatform query = new OrderPlatform();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        query.setIsActive(1);
        return success(service.selectPlatformList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:add')")
    @Log(title="平台维护", businessType=BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody OrderPlatform platform)
    {
        platform.setShopCode(SecurityUtils.getBusinessShopCode());
        platform.setCreateUser(SecurityUtils.getUsername());
        return toAjax(service.insertPlatform(platform));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:edit')")
    @Log(title="平台维护", businessType=BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody OrderPlatform platform)
    {
        platform.setShopCode(SecurityUtils.getBusinessShopCode());
        platform.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(service.updatePlatform(platform));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:remove')")
    @Log(title="平台维护", businessType=BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(service.deletePlatform(SecurityUtils.getBusinessShopCode(), id));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:list')")
    @GetMapping("/stores")
    public TableDataInfo stores(OrderPlatformStore query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(service.selectStoreList(query));
    }

    /** 销售订单店铺下拉，按平台联动。 */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/store-options")
    public AjaxResult storeOptions(String platformCode)
    {
        OrderPlatformStore query = new OrderPlatformStore();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        query.setPlatformCode(platformCode);
        query.setIsActive(1);
        return success(service.selectStoreList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:add')")
    @Log(title="平台店铺维护", businessType=BusinessType.INSERT)
    @PostMapping("/store")
    public AjaxResult addStore(@RequestBody OrderPlatformStore store)
    {
        store.setShopCode(SecurityUtils.getBusinessShopCode());
        store.setCreateUser(SecurityUtils.getUsername());
        return toAjax(service.insertStore(store));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:edit')")
    @Log(title="平台店铺维护", businessType=BusinessType.UPDATE)
    @PutMapping("/store")
    public AjaxResult editStore(@RequestBody OrderPlatformStore store)
    {
        store.setShopCode(SecurityUtils.getBusinessShopCode());
        store.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(service.updateStore(store));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:platform:remove')")
    @Log(title="平台店铺维护", businessType=BusinessType.DELETE)
    @DeleteMapping("/store/{id}")
    public AjaxResult removeStore(@PathVariable Long id)
    {
        return toAjax(service.deleteStore(SecurityUtils.getBusinessShopCode(), id));
    }
}

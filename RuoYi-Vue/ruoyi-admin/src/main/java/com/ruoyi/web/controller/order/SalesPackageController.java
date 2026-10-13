package com.ruoyi.web.controller.order;

import java.util.List;
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
import com.ruoyi.system.domain.SalesPackage;
import com.ruoyi.system.service.ISalesPackageService;

/**
 * 销售套餐维护。套餐是一组商品的组合标签，下单时一键把它包含的商品铺进订单明细。
 */
@RestController
@RequestMapping("/order/package")
public class SalesPackageController extends BaseController
{
    @Autowired
    private ISalesPackageService service;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:package:list')")
    @GetMapping("/list")
    public TableDataInfo list(SalesPackage query)
    {
        startPage();
        return getDataTable(service.selectPackageList(SecurityUtils.getBusinessShopCode(), query));
    }

    /**
     * 下单时可选的套餐，含明细，只列启用且有商品的。
     *
     * <p>权限用 {@code order:sales:add}：调用方是销售订单的建单弹窗，
     * 业务员未必有套餐维护权限——他只是用，不该要求他能改。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:add')")
    @GetMapping("/active")
    public AjaxResult active()
    {
        List<SalesPackage> packages = service.selectActivePackages(SecurityUtils.getBusinessShopCode());
        return success(packages);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:package:query')")
    @GetMapping("/{packageCode}")
    public AjaxResult detail(@PathVariable("packageCode") String packageCode)
    {
        return success(service.selectPackageDetail(SecurityUtils.getBusinessShopCode(), packageCode));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:package:add')")
    @Log(title = "销售套餐", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SalesPackage command)
    {
        SalesPackage created = service.createPackage(
                SecurityUtils.getBusinessShopCode(), command, SecurityUtils.getUsername());
        return AjaxResult.success("套餐已创建：" + created.getPackageCode(), created);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:package:edit')")
    @Log(title = "销售套餐", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SalesPackage command)
    {
        return toAjax(service.updatePackage(
                SecurityUtils.getBusinessShopCode(), command, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:package:remove')")
    @Log(title = "销售套餐", businessType = BusinessType.DELETE)
    @DeleteMapping("/{packageCode}")
    public AjaxResult remove(@PathVariable("packageCode") String packageCode)
    {
        return toAjax(service.deletePackage(SecurityUtils.getBusinessShopCode(), packageCode));
    }
}

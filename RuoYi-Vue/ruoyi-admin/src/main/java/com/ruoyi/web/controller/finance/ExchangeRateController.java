package com.ruoyi.web.controller.finance;

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
import com.ruoyi.system.domain.ExchangeRate;
import com.ruoyi.system.service.IExchangeRateService;

/**
 * 汇率设置Controller：外币折人民币，按生效日期记，销售利润报表用它折算。
 */
@RestController
@RequestMapping("/finance/rate")
public class ExchangeRateController extends BaseController
{
    @Autowired
    private IExchangeRateService rateService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:rate:list')")
    @GetMapping("/list")
    public TableDataInfo list(ExchangeRate query)
    {
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        startPage();
        return getDataTable(rateService.selectRateList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:rate:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        ExchangeRate rate = rateService.selectRateById(SecurityUtils.getBusinessShopCode(), id);
        return rate == null ? error("汇率记录不存在") : success(rate);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:rate:edit')")
    @Log(title = "汇率设置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ExchangeRate rate)
    {
        rate.setShopCode(SecurityUtils.getBusinessShopCode());
        rate.setCreateUser(SecurityUtils.getUsername());
        return toAjax(rateService.insertRate(rate));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:rate:edit')")
    @Log(title = "汇率设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ExchangeRate rate)
    {
        rate.setShopCode(SecurityUtils.getBusinessShopCode());
        return toAjax(rateService.updateRate(rate));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:rate:edit')")
    @Log(title = "汇率设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(rateService.deleteRateById(SecurityUtils.getBusinessShopCode(), id));
    }
}

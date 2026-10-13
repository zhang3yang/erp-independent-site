package com.ruoyi.web.controller.inventory;

import java.util.List;
import java.util.Map;
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
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.InventoryWarningRecord;
import com.ruoyi.system.domain.InventoryWarningRule;
import com.ruoyi.system.domain.model.InventoryWarningHandleBody;
import com.ruoyi.system.service.IInventoryWarningService;

@RestController
@RequestMapping("/inventory/warning")
public class InventoryWarningController extends BaseController
{
    @Autowired
    private IInventoryWarningService warningService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:rule:query')")
    @GetMapping("/rule/list")
    public TableDataInfo ruleList(InventoryWarningRule rule)
    {
        startPage();
        rule.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(warningService.selectRuleList(rule));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:rule:query')")
    @GetMapping("/rule/{ruleId}")
    public AjaxResult ruleInfo(@PathVariable Long ruleId)
    {
        InventoryWarningRule rule = warningService.selectRuleById(SecurityUtils.getBusinessShopCode(), ruleId);
        return rule == null ? error("预警规则不存在") : success(rule);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:rule:add')")
    @Log(title = "库存预警规则", businessType = BusinessType.INSERT)
    @PostMapping("/rule")
    public AjaxResult addRule(@Validated @RequestBody InventoryWarningRule rule)
    {
        rule.setShopCode(SecurityUtils.getBusinessShopCode());
        rule.setCreateBy(SecurityUtils.getUsername());
        return toAjax(warningService.insertRule(rule));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:rule:edit')")
    @Log(title = "库存预警规则", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult editRule(@Validated @RequestBody InventoryWarningRule rule)
    {
        rule.setShopCode(SecurityUtils.getBusinessShopCode());
        rule.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(warningService.updateRule(rule));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:rule:remove')")
    @Log(title = "库存预警规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/rule/{ruleIds}")
    public AjaxResult removeRule(@PathVariable Long[] ruleIds)
    {
        return toAjax(warningService.deleteRuleByIds(SecurityUtils.getBusinessShopCode(), ruleIds));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:record:query')")
    @GetMapping("/record/list")
    public TableDataInfo recordList(InventoryWarningRecord record)
    {
        startPage();
        record.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(warningService.selectRecordList(record));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:record:query')")
    @GetMapping("/record/{recordId}")
    public AjaxResult recordInfo(@PathVariable Long recordId)
    {
        InventoryWarningRecord record = warningService.selectRecordById(SecurityUtils.getBusinessShopCode(), recordId);
        return record == null ? error("预警记录不存在") : success(record);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:record:handle')")
    @Log(title = "库存预警记录", businessType = BusinessType.UPDATE)
    @PostMapping("/record/{recordId}/acknowledge")
    public AjaxResult acknowledge(@PathVariable Long recordId,
            @Validated @RequestBody(required = false) InventoryWarningHandleBody body)
    {
        return toAjax(warningService.acknowledgeRecord(SecurityUtils.getBusinessShopCode(), recordId,
                SecurityUtils.getUsername(), body == null ? null : body.getRemark()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:record:handle')")
    @Log(title = "库存预警记录", businessType = BusinessType.UPDATE)
    @PostMapping("/record/{recordId}/close")
    public AjaxResult close(@PathVariable Long recordId,
            @Validated @RequestBody(required = false) InventoryWarningHandleBody body)
    {
        return toAjax(warningService.closeRecord(SecurityUtils.getBusinessShopCode(), recordId,
                SecurityUtils.getUsername(), body == null ? null : body.getRemark()));
    }

    @PreAuthorize("@ss.hasPermi('inventory:warning:record:query')")
    @GetMapping("/count")
    public AjaxResult count()
    {
        String shopCode = SecurityUtils.getLoginUser().getShopCode();
        if (SecurityUtils.isAdmin() && StringUtils.isEmpty(shopCode))
        {
            String selectedShopCode = StringUtils.trim(ServletUtils.getRequest()
                    .getHeader(SecurityUtils.BUSINESS_SHOP_HEADER));
            shopCode = StringUtils.isEmpty(selectedShopCode) ? null : selectedShopCode.toUpperCase();
        }
        else if (StringUtils.isEmpty(shopCode))
        {
            shopCode = SecurityUtils.getBusinessShopCode();
        }
        int count = warningService.countActiveRecords(shopCode);
        return success(count);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:warning:check')")
    @PostMapping("/check")
    public AjaxResult check()
    {
        return success(warningService.checkShopWarnings(SecurityUtils.getBusinessShopCode()));
    }

    @PreAuthorize("@ss.isPlatformAdmin() and @ss.hasPermi('inventory:warning:check')")
    @PostMapping("/check-all")
    public AjaxResult checkAll()
    {
        return success(warningService.checkAllWarnings());
    }
}

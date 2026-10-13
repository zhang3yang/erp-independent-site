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
import com.ruoyi.system.domain.FinanceCategory;
import com.ruoyi.system.service.IFinanceCategoryService;

/**
 * 收支分类 Controller（说明书 5.5 接口9、接口10）
 */
@RestController
@RequestMapping("/finance/category")
public class FinanceCategoryController extends BaseController
{
    @Autowired
    private IFinanceCategoryService categoryService;

    /**
     * 查询收支分类
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:category:list')")
    @GetMapping("/list")
    public TableDataInfo list(FinanceCategory category)
    {
        startPage();
        category.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(categoryService.selectCategoryList(category));
    }

    /**
     * 启用中的分类，供资金流水下拉选择。不分页，可按收支方向过滤。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/options")
    public AjaxResult options(String type)
    {
        FinanceCategory query = new FinanceCategory();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        query.setIsActive(1);
        query.setType(type);
        return success(categoryService.selectCategoryList(query));
    }

    /**
     * 新增收支分类
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:category:add')")
    @Log(title = "收支分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody FinanceCategory category)
    {
        category.setShopCode(SecurityUtils.getBusinessShopCode());
        category.setCreateUser(SecurityUtils.getUsername());
        return toAjax(categoryService.insertCategory(category));
    }

    /**
     * 修改收支分类
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:category:edit')")
    @Log(title = "收支分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody FinanceCategory category)
    {
        category.setShopCode(SecurityUtils.getBusinessShopCode());
        category.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 删除收支分类
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:category:remove')")
    @Log(title = "收支分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(categoryService.deleteCategoryByIds(SecurityUtils.getBusinessShopCode(), ids));
    }
}

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.InventoryGoodsCategory;
import com.ruoyi.system.service.IInventoryGoodsCategoryService;

/**
 * 商品分类Controller
 *
 * <p>接口与「系统管理 → 部门管理」一一对应：列表、排除子节点的列表、详情、
 * 新增、修改、保存排序、删除。</p>
 */
@RestController
@RequestMapping("/inventory/goodsCategory")
public class InventoryGoodsCategoryController extends BaseController
{
    @Autowired
    private IInventoryGoodsCategoryService categoryService;

    /**
     * 获取商品分类列表。树形表格不分页，返回平铺列表由前端拼树。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String status)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return success(categoryService.selectCategoryList(shopCode, categoryName, status));
    }

    /**
     * 查询商品分类列表（排除节点）。修改时用来选上级分类，
     * 自己和自己的下级都要排掉，否则会把子树挂到自己下面。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list/exclude/{categoryCode}")
    public AjaxResult excludeChild(@PathVariable(value = "categoryCode") String categoryCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return success(categoryService.selectCategoryListExcludeChild(shopCode, categoryCode));
    }

    /**
     * 商品分类树，供商品页的分类下拉使用
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/tree")
    public AjaxResult tree(@RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String status)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        List<InventoryGoodsCategory> tree = categoryService.selectCategoryTree(shopCode, categoryName, status);
        return success(tree);
    }

    /**
     * 根据分类编码获取详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping(value = "/{categoryCode}")
    public AjaxResult getInfo(@PathVariable String categoryCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventoryGoodsCategory category = categoryService.selectCategoryByCode(shopCode, categoryCode);
        if (category == null)
        {
            return error("商品分类不存在");
        }
        return success(category);
    }

    /**
     * 新增商品分类
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "商品分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody InventoryGoodsCategory category)
    {
        category.setShopCode(SecurityUtils.getBusinessShopCode());
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("新增分类'" + category.getCategoryName() + "'失败，分类名称已存在");
        }
        category.setCreateUser(SecurityUtils.getUsername());
        return toAjax(categoryService.insertCategory(category));
    }

    /**
     * 修改商品分类
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "商品分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody InventoryGoodsCategory category)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        category.setShopCode(shopCode);
        String categoryCode = category.getCategoryCode();
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("修改分类'" + category.getCategoryName() + "'失败，分类名称已存在");
        }
        else if (StringUtils.equals(categoryCode, category.getParentCode()))
        {
            return error("修改分类'" + category.getCategoryName() + "'失败，上级分类不能是自己");
        }
        else if (StringUtils.equals("1", category.getStatus())
                && categoryService.selectNormalChildrenCount(shopCode, categoryCode) > 0)
        {
            return error("该分类包含未停用的子分类！");
        }
        category.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 保存商品分类排序
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "保存商品分类排序", businessType = BusinessType.UPDATE)
    @PutMapping("/updateSort")
    public AjaxResult updateSort(@RequestBody Map<String, String> params)
    {
        String[] categoryCodes = StringUtils.split(params.get("categoryCodes"), ",");
        String[] orderNums = StringUtils.split(params.get("orderNums"), ",");
        categoryService.updateCategorySort(SecurityUtils.getBusinessShopCode(), categoryCodes, orderNums);
        return success();
    }

    /**
     * 删除商品分类
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "商品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryCode}")
    public AjaxResult remove(@PathVariable String categoryCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        if (categoryService.hasChildByCategoryCode(shopCode, categoryCode))
        {
            return warn("存在下级分类,不允许删除");
        }
        if (categoryService.checkCategoryExistGoods(shopCode, categoryCode))
        {
            return warn("分类存在商品,不允许删除");
        }
        return toAjax(categoryService.deleteCategoryByCode(shopCode, categoryCode));
    }
}

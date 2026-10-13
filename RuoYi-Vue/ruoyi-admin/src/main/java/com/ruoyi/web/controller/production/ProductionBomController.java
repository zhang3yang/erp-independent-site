package com.ruoyi.web.controller.production;

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
import com.ruoyi.system.domain.ProductionBom;
import com.ruoyi.system.service.IProductionBomService;

/** 产品配方（BOM）主数据接口。 */
@RestController
@RequestMapping("/production/bom")
public class ProductionBomController extends BaseController
{
    @Autowired
    private IProductionBomService productionBomService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:bom:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProductionBom query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(productionBomService.selectBomList(query));
    }

    /**
     * 已经有配方的成品编码。新建配方选成品时用它把这些成品锁掉——一个成品只能有一套配方。
     *
     * <p>不分页：列表页那一页只能排掉当前页，翻页后照样能选到已有配方的成品，
     * 保存时才被拒。只返回一列编码，全量也不重。</p>
     *
     * <p>路径放在 {@code /{bomCode}} 之前，否则会被它当成一个叫 products 的配方号吃掉。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:bom:list')")
    @GetMapping("/products")
    public AjaxResult productCodes()
    {
        return success(productionBomService.selectBomProductCodes(SecurityUtils.getBusinessShopCode()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:bom:list')")
    @GetMapping("/{bomCode}")
    public AjaxResult getInfo(@PathVariable String bomCode)
    {
        ProductionBom bom = productionBomService.selectBomByCode(SecurityUtils.getBusinessShopCode(), bomCode);
        return bom == null ? error("配方不存在") : success(bom);
    }

    /**
     * 按产出商品取启用中的配方，建生产工单时自动带出用。
     *
     * <p>没有配方、或配方已停用都返回空数据而不是报错——工单可以不用配方，
     * 手工录物料照样能建，这里报错会把正常流程堵死。</p>
     *
     * <p>权限跟着工单新增走：能建工单就能带出配方。挂 bom:list 会让只有工单权限、
     * 没有配方菜单的人建单时带不出料。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:add')")
    @GetMapping("/product/{productCode}")
    public AjaxResult getByProduct(@PathVariable String productCode)
    {
        return success(productionBomService.selectEnabledBomByProduct(
                SecurityUtils.getBusinessShopCode(), productCode));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:bom:add')")
    @Log(title = "产品配方", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProductionBom bom)
    {
        bom.setShopCode(SecurityUtils.getBusinessShopCode());
        bom.setCreateUser(SecurityUtils.getUsername());
        return toAjax(productionBomService.insertBom(bom));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:bom:edit')")
    @Log(title = "产品配方", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProductionBom bom)
    {
        bom.setShopCode(SecurityUtils.getBusinessShopCode());
        bom.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(productionBomService.updateBom(bom));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:bom:remove')")
    @Log(title = "产品配方", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bomCode}")
    public AjaxResult remove(@PathVariable String bomCode)
    {
        return toAjax(productionBomService.deleteBom(SecurityUtils.getBusinessShopCode(), bomCode));
    }
}

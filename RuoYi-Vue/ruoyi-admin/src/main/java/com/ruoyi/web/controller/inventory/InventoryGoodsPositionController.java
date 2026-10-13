package com.ruoyi.web.controller.inventory;

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
import com.ruoyi.system.domain.InventoryGoodsPosition;
import com.ruoyi.system.service.IInventoryGoodsPositionService;

/**
 * 商品位置Controller
 *
 * <p>库管员登记某个商品在某个仓库里放在哪，位置是一段自由文本。</p>
 */
@RestController
@RequestMapping("/inventory/goodsPosition")
public class InventoryGoodsPositionController extends BaseController
{
    @Autowired
    private IInventoryGoodsPositionService positionService;

    /**
     * 查询商品位置列表
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:goodsposition:list')")
    @GetMapping("/list")
    public TableDataInfo list(InventoryGoodsPosition position)
    {
        startPage();
        position.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(positionService.selectPositionList(position));
    }

    /**
     * 获取商品位置详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:goodsposition:query')")
    @GetMapping("/{positionId}")
    public AjaxResult getInfo(@PathVariable Long positionId)
    {
        InventoryGoodsPosition position = positionService
                .selectPositionById(SecurityUtils.getBusinessShopCode(), positionId);
        return position == null ? error("商品位置不存在") : success(position);
    }

    /**
     * 新增商品位置
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:goodsposition:add')")
    @Log(title = "商品位置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventoryGoodsPosition position)
    {
        position.setShopCode(SecurityUtils.getBusinessShopCode());
        position.setCreateUser(SecurityUtils.getUsername());
        return toAjax(positionService.insertPosition(position));
    }

    /**
     * 修改商品位置
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:goodsposition:edit')")
    @Log(title = "商品位置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventoryGoodsPosition position)
    {
        position.setShopCode(SecurityUtils.getBusinessShopCode());
        return toAjax(positionService.updatePosition(position));
    }

    /**
     * 删除商品位置
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('inventory:goodsposition:remove')")
    @Log(title = "商品位置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{positionIds}")
    public AjaxResult remove(@PathVariable Long[] positionIds)
    {
        return toAjax(positionService.deletePositionByIds(SecurityUtils.getBusinessShopCode(), positionIds));
    }
}

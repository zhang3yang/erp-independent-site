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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.service.IInventoryGoodsService;
import com.ruoyi.common.utils.file.OssStorageService;
import com.ruoyi.common.utils.file.OssUploadResult;

/**
 * 商品Controller
 */
@RestController
@RequestMapping("/inventory/goods")
public class InventoryGoodsController extends BaseController
{
    @Autowired
    private IInventoryGoodsService goodsService;

    @Autowired
    private OssStorageService ossStorageService;

    @PreAuthorize("@ss.hasBusinessShop()")
    @PostMapping("/images")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file) throws Exception
    {
        if (!ossStorageService.isEnabled())
        {
            String fileName = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, MimeTypeUtils.IMAGE_EXTENSION, true);
            AjaxResult local = success();
            local.put("url", fileName);
            local.put("fileName", fileName);
            return local;
        }
        OssUploadResult result = ossStorageService.uploadImage(file, "goods/" + SecurityUtils.getBusinessShopCode());
        AjaxResult ajax = success();
        ajax.put("url", result.getUrl());
        ajax.put("fileName", result.getUrl());
        ajax.put("thumbUrl", result.getThumbUrl());
        return ajax;
    }

    /**
     * 查询商品列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventoryGoods goods)
    {
        // 设置商家编码
        goods.setShopCode(SecurityUtils.getBusinessShopCode());
        // 分类展开要查分类表，必须放在 startPage() 之前，否则分页参数会被这次查询用掉
        goodsService.applyCategoryScope(goods);
        startPage();
        List<InventoryGoods> list = goodsService.selectGoodsList(goods);
        return getDataTable(list);
    }

    /**
     * 查询商品分类树：一级为商品分类，二级为该分类下的规格型号。
     * activeFlag 传 1 时只统计启用商品，保证节点上的数量和列表条数对得上。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/categoryTree")
    public AjaxResult categoryTree(@RequestParam(required = false) Integer activeFlag)
    {
        return success(goodsService.selectGoodsCategoryTree(SecurityUtils.getBusinessShopCode(), activeFlag));
    }

    /**
     * 按分类树节点查询商品列表。
     * category 不传表示不限分类，传父分类会连同下级分类一起查，
     * spec 不传表示该分类下的全部型号，传 __NONE__ 则表示查「未分类 / 无型号」的商品。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/categoryList")
    public TableDataInfo categoryList(@RequestParam(required = false) String category,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer activeFlag)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        // 分类展开要查分类表，必须放在 startPage() 之前，否则分页参数会被这次查询用掉
        List<String> categoryNames = goodsService.expandCategoryNames(shopCode, category);
        startPage();
        List<InventoryGoods> list = goodsService.selectGoodsByCategoryNode(
                shopCode, category, categoryNames, spec, goodsCode, goodsName, keyword, activeFlag);
        return getDataTable(list);
    }

    /**
     * 获取商品详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{goodsCode}")
    public AjaxResult getInfo(@PathVariable("goodsCode") String goodsCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventoryGoods goods = goodsService.selectGoodsByCode(shopCode, goodsCode);
        if (goods == null)
        {
            return error("商品不存在");
        }
        return success(goods);
    }

    /**
     * 新增商品
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "商品管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventoryGoods goods)
    {
        // 设置商家编码和创建人
        goods.setShopCode(SecurityUtils.getBusinessShopCode());
        goods.setCreateUser(SecurityUtils.getUsername());

        return toAjax(goodsService.insertGoods(goods));
    }

    /**
     * 修改商品
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "商品管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventoryGoods goods)
    {
        // 设置商家编码和更新人
        goods.setShopCode(SecurityUtils.getBusinessShopCode());
        goods.setUpdateUser(SecurityUtils.getUsername());

        return toAjax(goodsService.updateGoods(goods));
    }

    /**
     * 删除商品
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "商品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{goodsCodes}")
    public AjaxResult remove(@PathVariable String[] goodsCodes)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(goodsService.deleteGoodsByCodes(shopCode, goodsCodes));
    }
}


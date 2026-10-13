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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.InventoryPartner;
import com.ruoyi.system.service.IInventoryPartnerService;

/**
 * 供应商/客户Controller
 */
@RestController
@RequestMapping("/inventory/partners")
public class InventoryPartnerController extends BaseController
{
    @Autowired
    private IInventoryPartnerService partnerService;

    /**
     * 查询供应商/客户列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventoryPartner partner)
    {
        startPage();
        // 设置商家编码
        partner.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventoryPartner> list = partnerService.selectPartnerList(partner);
        return getDataTable(list);
    }

    /**
     * 按商品查历史供应商，给供应商选择做推荐。
     *
     * <p>数据来自 sdsshop.u_m_goods_partner —— 已审核采购入库单累积出来的供货关系。
     * 第一次入库的商品查不到任何记录，属于正常情况，由界面提示自己选。</p>
     *
     * <p>一次带上单据里的全部商品编码，不要一行一个请求。</p>
     *
     * @param goodsCodes 商品代码，逗号分隔
     * @return 供货关系集合，按「最近一次入库日期倒序」排好
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/recommend")
    public AjaxResult recommend(@RequestParam(value = "goodsCodes", required = false) String goodsCodes)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return success(partnerService.selectGoodsPartners(shopCode, goodsCodes));
    }

    /**
     * 获取供应商/客户详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{partnerCode}")
    public AjaxResult getInfo(@PathVariable("partnerCode") String partnerCode)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventoryPartner partner = partnerService.selectPartnerByCode(shopCode, partnerCode);
        if (partner == null)
        {
            return error("供应商不存在");
        }
        return success(partner);
    }

    /**
     * 新增供应商/客户
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "供应商管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventoryPartner partner)
    {
        // 设置商家编码和创建人
        partner.setShopCode(SecurityUtils.getBusinessShopCode());
        partner.setCreateUser(SecurityUtils.getUsername());

        return toAjax(partnerService.insertPartner(partner));
    }

    /**
     * 修改供应商/客户
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "供应商管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventoryPartner partner)
    {
        // 设置商家编码和更新人
        partner.setShopCode(SecurityUtils.getBusinessShopCode());
        partner.setUpdateUser(SecurityUtils.getUsername());

        return toAjax(partnerService.updatePartner(partner));
    }

    /**
     * 删除供应商/客户
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "供应商管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{partnerCodes}")
    public AjaxResult remove(@PathVariable String[] partnerCodes)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(partnerService.deletePartnerByCodes(shopCode, partnerCodes));
    }
}


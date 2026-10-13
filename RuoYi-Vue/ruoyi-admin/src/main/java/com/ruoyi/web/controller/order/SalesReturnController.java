package com.ruoyi.web.controller.order;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.vo.SalesReturnableItem;
import com.ruoyi.system.service.ISalesReturnService;

/**
 * 销售退货。客户退回已发出的货，走 XSTHRK 入库单还货还成本。
 *
 * <p>与采购退货 {@code /purchase/return} 对称。退货事实落在库存单据上，
 * 通过来源单号关联回销售订单，订单侧不新增业务表。</p>
 *
 * <p>建单与入库分两步：{@code POST /} 建待提交单，{@code POST /{docCode}/submit}
 * 才审核过账真正入库，与销售补发的「建单 → 整单发货」一致。</p>
 */
@RestController
@RequestMapping("/order/salesreturn")
public class SalesReturnController extends BaseController
{
    @Autowired
    private ISalesReturnService service;

    /** 退货记录列表，按月表查，可按退货单号、订单号与状态过滤 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:list')")
    @GetMapping("/list")
    public TableDataInfo list(String docCode, String orderCode, String confirmState, String tableSuffix)
    {
        startPage();
        List<DocumentMain> list = service.selectReturnList(
                SecurityUtils.getBusinessShopCode(), docCode, orderCode, confirmState, tableSuffix);
        return getDataTable(list);
    }

    /**
     * 全部月份里还没提交的退货单。
     *
     * <p>列表按月表查，月份一翻上个月的待提交单就看不见了，而它一直占着可退数量。
     * 页面用这个接口做提醒，不分页——待提交单本来就该尽快清掉，不该攒成一页。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:list')")
    @GetMapping("/pending")
    public AjaxResult pending()
    {
        return success(service.selectPendingReturns(SecurityUtils.getBusinessShopCode()));
    }

    /** 退货单详情，含明细行 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:query')")
    @GetMapping("/{docCode}")
    public AjaxResult detail(@PathVariable("docCode") String docCode, String tableSuffix)
    {
        return success(service.selectReturnDetail(
                SecurityUtils.getBusinessShopCode(), docCode, tableSuffix));
    }

    /** 某销售订单的可退商品，按商品与发货仓库聚合 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:query')")
    @GetMapping("/returnable")
    public AjaxResult returnable(@RequestParam String orderCode)
    {
        List<SalesReturnableItem> items = service.selectReturnableItems(
                SecurityUtils.getBusinessShopCode(), orderCode);
        return success(items);
    }

    /** 新建销售退货单。只建单，货还没回库，要再点「提交退货」才入库。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:add')")
    @Log(title = "销售退货", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ISalesReturnService.ReturnCommand command)
    {
        DocumentMain document = service.createReturn(
                SecurityUtils.getBusinessShopCode(), command, SecurityUtils.getUsername());
        return AjaxResult.success("退货单创建成功：" + document.getDocCode() + "，提交后货才入库", document);
    }

    /** 提交退货。审核该单，货入库并写库存流水，只能提交一次。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:submit')")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PostMapping("/{docCode}/submit")
    public AjaxResult submit(@PathVariable("docCode") String docCode, String tableSuffix)
    {
        DocumentMain document = service.submitReturn(
                SecurityUtils.getBusinessShopCode(), docCode, tableSuffix, SecurityUtils.getUsername());
        return AjaxResult.success("退货已提交入库：" + document.getDocCode(), document);
    }

    /** 删除还没提交的退货单，把占住的可退数量放回去 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreturn:remove')")
    @Log(title = "销售退货", businessType = BusinessType.DELETE)
    @DeleteMapping("/{docCode}")
    public AjaxResult remove(@PathVariable("docCode") String docCode, String tableSuffix)
    {
        service.deleteReturn(SecurityUtils.getBusinessShopCode(), docCode, tableSuffix);
        return success("退货单已删除");
    }
}

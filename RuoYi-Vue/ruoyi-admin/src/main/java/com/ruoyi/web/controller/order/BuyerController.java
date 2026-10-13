package com.ruoyi.web.controller.order;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
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
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.Buyer;
import com.ruoyi.system.domain.Buyer2;
import com.ruoyi.system.service.IBuyerService;

/**
 * 客户信息 Controller。
 *
 * <p>权限串与 sql/buyer_module_permission_20260824.sql 里的 fn8050~fn8055 一一对应，
 * 改一边必须改另一边，否则接口 403。</p>
 */
@RestController
@RequestMapping("/order/buyer")
public class BuyerController extends BaseController
{
    @Autowired
    private IBuyerService buyerService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:list')")
    @GetMapping("/list")
    public TableDataInfo list(Buyer query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(buyerService.selectBuyerList(query));
    }

    /**
     * 销售订单页的客户选择器。
     *
     * <p>权限挂在 {@code order:sales:add} 上而不是 {@code order:buyer:list}：
     * 能建单的业务员就该能选客户，不必再单独授一遍客户管理权限。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasAnyPermi('order:sales:add,order:buyer:list')")
    @GetMapping("/selector")
    public AjaxResult selector(@RequestParam(required = false) String keyword)
    {
        return success(buyerService.selectSelectorList(SecurityUtils.getBusinessShopCode(), keyword));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:query')")
    @GetMapping("/{buyerCode}")
    public AjaxResult getInfo(@PathVariable("buyerCode") String buyerCode)
    {
        Buyer buyer = buyerService.selectBuyerByCode(SecurityUtils.getBusinessShopCode(), buyerCode);
        if (buyer == null)
        {
            return error("客户不存在");
        }
        return success(buyer);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:add')")
    @Log(title = "客户信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Buyer buyer)
    {
        buyer.setShopCode(SecurityUtils.getBusinessShopCode());
        buyer.setCreateUser(SecurityUtils.getUsername());
        // 归属业务员＝建档人，取登录态，覆盖前端传的任何值。
        // 前端那个框是 disabled 的，但 disabled 只挡得住页面上的手。
        buyer.setStaffCode(SecurityUtils.getUsername());
        buyer.setStaffName(currentStaffName());
        Buyer created = buyerService.insertBuyer(buyer);
        return success("客户创建成功：" + created.getBuyerCode());
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:edit')")
    @Log(title = "客户信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Buyer buyer)
    {
        buyer.setShopCode(SecurityUtils.getBusinessShopCode());
        buyer.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(buyerService.updateBuyer(buyer));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:remove')")
    @Log(title = "客户信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{buyerCodes}")
    public AjaxResult remove(@PathVariable String[] buyerCodes)
    {
        return toAjax(buyerService.deleteBuyerByCodes(SecurityUtils.getBusinessShopCode(), buyerCodes));
    }

    /**
     * 当前登录人的显示名：有昵称用昵称，没有就退回登录账号。
     *
     * <p>存的是名称快照，之后这个人改了昵称，已归属的客户上仍是当时的名字——
     * 与订单存客户快照是同一个道理。</p>
     */
    private String currentStaffName()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (user != null && user.getNickName() != null && !user.getNickName().trim().isEmpty())
        {
            return user.getNickName().trim();
        }
        return SecurityUtils.getUsername();
    }

    // ------------------------------------------------------------------
    // 二级客户。挂在 /order/buyer 下用子路径，与 OrderPlatformController 把
    // 平台和店铺放在同一个 Controller 的做法一致；权限也复用 order:buyer:*，
    // 不单独发一套按钮权限。
    // ------------------------------------------------------------------

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:list')")
    @GetMapping("/subs")
    public TableDataInfo subList(Buyer2 query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(buyerService.selectBuyer2List(query));
    }

    /** 订单页二级客户下拉：选定主客户后才有内容 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasAnyPermi('order:sales:add,order:buyer:list')")
    @GetMapping("/sub-options")
    public AjaxResult subOptions(@RequestParam(required = false) String buyerCode)
    {
        return success(buyerService.selectBuyer2Options(SecurityUtils.getBusinessShopCode(), buyerCode));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:add')")
    @Log(title = "二级客户", businessType = BusinessType.INSERT)
    @PostMapping("/sub")
    public AjaxResult subAdd(@RequestBody Buyer2 buyer2)
    {
        buyer2.setShopCode(SecurityUtils.getBusinessShopCode());
        buyer2.setCreateUser(SecurityUtils.getUsername());
        Buyer2 created = buyerService.insertBuyer2(buyer2);
        return success("二级客户创建成功：" + created.getBuyer2Code());
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:edit')")
    @Log(title = "二级客户", businessType = BusinessType.UPDATE)
    @PutMapping("/sub")
    public AjaxResult subEdit(@RequestBody Buyer2 buyer2)
    {
        buyer2.setShopCode(SecurityUtils.getBusinessShopCode());
        buyer2.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(buyerService.updateBuyer2(buyer2));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:remove')")
    @Log(title = "二级客户", businessType = BusinessType.DELETE)
    @DeleteMapping("/sub/{buyer2Codes}")
    public AjaxResult subRemove(@PathVariable String[] buyer2Codes)
    {
        return toAjax(buyerService.deleteBuyer2ByCodes(SecurityUtils.getBusinessShopCode(), buyer2Codes));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:buyer:export')")
    @Log(title = "客户信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Buyer query)
    {
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        List<Buyer> list = buyerService.selectBuyerList(query);
        ExcelUtil<Buyer> util = new ExcelUtil<Buyer>(Buyer.class);
        util.exportExcel(response, list, "客户信息");
    }
}

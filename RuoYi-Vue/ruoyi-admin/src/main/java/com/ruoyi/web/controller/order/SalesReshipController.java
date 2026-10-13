package com.ruoyi.web.controller.order;

import java.util.Map;
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
import com.ruoyi.system.domain.SalesReship;
import com.ruoyi.system.service.ISalesReshipService;
import com.ruoyi.system.utils.MonthTableSuffix;

@RestController
@RequestMapping("/order/salesreship")
public class SalesReshipController extends BaseController
{
    @Autowired private ISalesReshipService service;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreship:list')")
    @GetMapping("/list")
    public TableDataInfo list(SalesReship query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(service.selectList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreship:query')")
    @GetMapping("/{reshipCode}")
    public AjaxResult detail(@PathVariable String reshipCode)
    {
        SalesReship reship = service.selectDetail(SecurityUtils.getBusinessShopCode(), reshipCode);
        return reship == null ? error("销售补发单不存在") : success(reship);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreship:add')")
    @GetMapping("/eligible")
    public AjaxResult eligible(@RequestParam(required = false) String tableSuffix)
    {
        String suffix = MonthTableSuffix.isValid(tableSuffix) ? tableSuffix : MonthTableSuffix.current();
        return success(service.selectEligibleOrders(SecurityUtils.getBusinessShopCode(), suffix));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreship:add')")
    @Log(title = "销售补发", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SalesReship command)
    {
        SalesReship created = service.create(SecurityUtils.getBusinessShopCode(), command, SecurityUtils.getUsername());
        return AjaxResult.success("销售补发单创建成功：" + created.getReshipCode(), created);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreship:ship')")
    @Log(title = "销售补发发货", businessType = BusinessType.UPDATE)
    @PostMapping("/{reshipCode}/ship")
    public AjaxResult ship(@PathVariable String reshipCode, @RequestBody(required = false) Map<String, Object> params)
    {
        String docCode = service.ship(SecurityUtils.getBusinessShopCode(), reshipCode,
                stringOf(params, "carrier"), stringOf(params, "logisticsNo"), SecurityUtils.getUsername());
        return success("补发完成，出库单：" + docCode);
    }

    /**
     * 删除还没发货的补发单。
     *
     * <p>补发单不能改，建错了就靠删掉重建；一张原订单只能有一张补发单是唯一索引管的，
     * 不删干净那张原订单就再也补发不了。已发货的删不掉。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:salesreship:remove')")
    @Log(title = "销售补发", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reshipCode}")
    public AjaxResult remove(@PathVariable String reshipCode)
    {
        service.delete(SecurityUtils.getBusinessShopCode(), reshipCode);
        return success("补发单已删除，该原订单可以重新创建补发单");
    }

    private String stringOf(Map<String, Object> params, String key)
    {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

package com.ruoyi.web.controller.production;

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
import com.ruoyi.system.domain.ProductionLog;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.ProductionExecutionRequest;
import com.ruoyi.system.service.IProductionOrderService;

/** 生产工单基础接口。 */
@RestController
@RequestMapping("/production/order")
public class ProductionOrderController extends BaseController
{
    @Autowired
    private IProductionOrderService productionOrderService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProductionOrder order)
    {
        startPage();
        order.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(productionOrderService.selectProductionOrderList(order));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:list')")
    @GetMapping("/{productionCode}")
    public AjaxResult getInfo(@PathVariable String productionCode)
    {
        ProductionOrder order = productionOrderService.selectProductionOrderByCode(
                SecurityUtils.getBusinessShopCode(), productionCode);
        return order == null ? error("生产工单不存在") : success(order);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:add')")
    @Log(title = "生产工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProductionOrder order)
    {
        order.setShopCode(SecurityUtils.getBusinessShopCode());
        order.setCreateUser(SecurityUtils.getUsername());
        return toAjax(productionOrderService.insertProductionOrder(order));
    }

    /** 设计说明书约定的创建工单路径，与 POST /production/order 兼容。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:add')")
    @Log(title = "生产工单", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult create(@Validated @RequestBody ProductionOrder order)
    {
        return add(order);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:add')")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProductionOrder order)
    {
        order.setShopCode(SecurityUtils.getBusinessShopCode());
        order.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(productionOrderService.updateProductionOrder(order));
    }

    /** 删除工单。只有待生产工单能删，已开工的走关闭。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:remove')")
    @Log(title = "生产工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{productionCode}")
    public AjaxResult remove(@PathVariable String productionCode)
    {
        return toAjax(productionOrderService.deleteProductionOrder(
                SecurityUtils.getBusinessShopCode(), productionCode));
    }

    /**
     * 关闭工单。已完工关闭是正常结案；待生产 / 待领料 / 生产中关闭是终止「这批不做了」，
     * 必须带 {@code reason}。两种都要求车间已清空。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:close')")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{productionCode}/close")
    public AjaxResult close(@PathVariable String productionCode,
            @RequestParam(required = false) String reason)
    {
        return toAjax(productionOrderService.closeProductionOrder(
                SecurityUtils.getBusinessShopCode(), productionCode, reason, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:pick')")
    @PostMapping("/{productionCode}/pick")
    public AjaxResult pick(@PathVariable String productionCode, @RequestBody(required = false) ProductionExecutionRequest request)
    {
        return toAjax(productionOrderService.pickProductionOrder(
                SecurityUtils.getBusinessShopCode(), productionCode, request, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:complete')")
    @PostMapping("/{productionCode}/complete")
    public AjaxResult complete(@PathVariable String productionCode, @RequestBody(required = false) ProductionExecutionRequest request)
    {
        return toAjax(productionOrderService.completeProductionOrder(
                SecurityUtils.getBusinessShopCode(), productionCode, request, SecurityUtils.getUsername()));
    }

    /**
     * 完工后补录 / 修改加工费、运费、其他费用，重算总成本与单位成本。
     *
     * <p>复用完工权限：谁能结算成本，谁就能修正成本，不为此单开一个新权限串——
     * 新增权限要改菜单 SQL 并在所有环境重跑，代价与收益不匹配。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:complete')")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{productionCode}/cost")
    public AjaxResult adjustCost(@PathVariable String productionCode, @RequestBody(required = false) ProductionExecutionRequest request)
    {
        return toAjax(productionOrderService.adjustProductionCost(
                SecurityUtils.getBusinessShopCode(), productionCode, request, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:return')")
    @PostMapping("/{productionCode}/return")
    public AjaxResult returnMaterial(@PathVariable String productionCode, @RequestBody(required = false) ProductionExecutionRequest request)
    {
        return toAjax(productionOrderService.returnProductionMaterial(
                SecurityUtils.getBusinessShopCode(), productionCode, request, SecurityUtils.getUsername()));
    }

    /**
     * 查工单执行日志。不传 tableSuffix 就跨所有月表查，工单跨月时才看得全。
     *
     * <p>不分页：一张工单的日志就几条，而 PageHelper 只会作用在跨月循环里的第一张月表上，
     * 分页反而把合并结果切坏。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:list')")
    @GetMapping("/{productionCode}/logs")
    public TableDataInfo logs(@PathVariable String productionCode,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String tableSuffix)
    {
        List<ProductionLog> logs = productionOrderService.selectProductionLogList(
                SecurityUtils.getBusinessShopCode(), productionCode, operation, tableSuffix);
        return getDataTable(logs);
    }
}

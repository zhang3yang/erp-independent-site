package com.ruoyi.web.controller.order;

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
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.utils.MonthTableSuffix;

/**
 * 销售订单 Controller。
 *
 * <p>订单按 createtime 分月表，列表查询必须带月份；单号查询可以不带，
 * 服务层会跨月找。</p>
 */
@RestController
@RequestMapping("/order/sales")
public class SalesOrderController extends BaseController
{
    @Autowired private ISalesOrderService orderService;

    /**
     * 订单列表。tableSuffix 不传默认当前月——分月表没有「查全部」这种查法，
     * 跨月只能一个月一个月翻。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:list')")
    @GetMapping("/list")
    public TableDataInfo list(SalesOrder query, @RequestParam(required = false) String tableSuffix)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        String suffix = MonthTableSuffix.isValid(tableSuffix) ? tableSuffix : MonthTableSuffix.current();
        return getDataTable(orderService.selectOrderList(query, suffix));
    }

    /**
     * 可发货订单，供发货批次页建单时选单。只列还有量能排的。
     *
     * <p>权限用 shipbatch:list 而不是 sales:list：调用方是发货批次页，
     * 只能发货、不能看订单列表的仓管员也要用得了。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:shipbatch:list')")
    @GetMapping("/shippable")
    public AjaxResult shippable(@RequestParam(required = false) String tableSuffix)
    {
        String suffix = MonthTableSuffix.isValid(tableSuffix) ? tableSuffix : MonthTableSuffix.current();
        return success(orderService.selectShippableOrders(SecurityUtils.getBusinessShopCode(), suffix));
    }

    /**
     * 可回款订单，供财务建客户回款流水时选单（V2.0 §5.4 流程2）。
     *
     * <p>权限用 finance:transaction:add：唯一的调用方是资金流水的建单弹窗。
     * 认领已经搬到销售订单详情里做（业务员站在自己的单上挑打款），不再从这里选订单，
     * 见 docs/superpowers/specs/2026-09-20-order-side-receipt-claim-design.md。</p>
     *
     * <p>不传 tableSuffix 表示<b>全部月份</b>，不是当月：订单与回款流水常常不在同一个月，
     * 8 月下的单 9 月才到账是常态，只给当月会让财务在下拉里找不到欠款单。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:add')")
    @GetMapping("/receivable")
    public AjaxResult receivable(@RequestParam(required = false) String tableSuffix)
    {
        return success(orderService.selectReceivableOrders(SecurityUtils.getBusinessShopCode(), tableSuffix));
    }

    /**
     * 可退款订单，供财务建销售退款流水时选单。
     *
     * <p>与 /receivable 是<b>相反</b>的集合：那边列还没收满的，这边列收过钱的（已收金额 &gt; 0）。
     * 已取消的订单也要列出来——取消后把客户的钱退回去正是退款最常见的场景。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:add')")
    @GetMapping("/refundable")
    public AjaxResult refundable(@RequestParam(required = false) String tableSuffix)
    {
        return success(orderService.selectRefundableOrders(SecurityUtils.getBusinessShopCode(), tableSuffix));
    }

    /**
     * 可排产的订单明细，供生产按订单建工单时选单（V2.0 §4.4 工单来源 order）。
     *
     * <p>列的是<b>还没发完</b>的明细，不要求有缺口。原先叫 /shortage、只列缺口大于 0 的行，
     * 即库存占不上的部分才准排产；定制品的场景不成立——客户要的规格库存备货满足不了，
     * 哪怕现货已经占上也只能专门生产。</p>
     *
     * <p>权限用 production:order:add：调用方是生产工单的建单弹窗，计划员未必有订单列表权限，
     * 与上面 /receivable 借用财务权限同一个道理。</p>
     *
     * <p>不传 tableSuffix 表示<b>全部月份</b>，不是当月：上月下的单这月才排产是常态。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('production:order:add')")
    @GetMapping("/production-candidates")
    public AjaxResult productionCandidates(@RequestParam(required = false) String tableSuffix,
            @RequestParam(required = false) String goodsCode)
    {
        return success(orderService.selectProductionCandidates(
                SecurityUtils.getBusinessShopCode(), tableSuffix, goodsCode));
    }

    /** 订单详情。不带 tableSuffix 时跨月查找。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:query')")
    @GetMapping("/{orderCode}")
    public AjaxResult getInfo(@PathVariable("orderCode") String orderCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        SalesOrder order = MonthTableSuffix.isValid(tableSuffix)
                ? orderService.selectOrderDetail(shopCode, orderCode, tableSuffix)
                : orderService.findOrderAcrossMonths(shopCode, orderCode);
        return order == null ? error("订单不存在") : success(order);
    }

    /**
     * 这张订单关联的生产工单，不分状态，按建单时间倒序。
     *
     * <p>接口放在订单这边而不是生产那边：调用方是订单详情页，权限跟着
     * {@code order:sales:query} 走，只看订单的人不必额外要生产权限。</p>
     *
     * <p>按工单的产出分配反查，不按订单明细上的 sourcecode——那个字段是单值的，
     * 换过工单只留最后一张，关闭工单时还会被清空，拿它追溯会丢历史。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:query')")
    @GetMapping("/{orderCode}/productions")
    public AjaxResult productions(@PathVariable("orderCode") String orderCode)
    {
        return success(orderService.selectProductionsByOrder(SecurityUtils.getBusinessShopCode(), orderCode));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:add')")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SalesOrder order)
    {
        SalesOrder created = orderService.createOrder(SecurityUtils.getBusinessShopCode(), order,
                SecurityUtils.getUsername());
        // 把建出来的单带回去：新建弹窗上的「提交审核」要拿 orderCode 和落表月份接着调提交，
        // 从提示语里抠单号太脆。data 是新增的，原来的提示语一个字没动
        return AjaxResult.success("订单创建成功：" + created.getOrderCode(), created);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:edit')")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SalesOrder order, @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, order.getOrderCode(), tableSuffix);
        return toAjax(orderService.updateDraft(shopCode, order, suffix, SecurityUtils.getUsername()));
    }

    /**
     * 建单并直接提交审核：一步落「待审核」，不经过草稿。校验与建单相同，任一步不过整单不落库。
     *
     * <p>权限要同时有 add 和 submit：这颗按钮等于把两个动作合成一步，缺哪个都不该出现。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:add') and @ss.hasPermi('order:sales:submit')")
    @Log(title = "销售订单建单并提交", businessType = BusinessType.INSERT)
    @PostMapping("/submit-new")
    public AjaxResult addAndSubmit(@RequestBody SalesOrder order)
    {
        SalesOrder created = orderService.createAndSubmit(SecurityUtils.getBusinessShopCode(), order,
                SecurityUtils.getUsername());
        return AjaxResult.success("订单已创建并提交审核：" + created.getOrderCode(), created);
    }

    /** 改单并重新提交：草稿 / 已驳回 → 待审核，一个事务。权限同上，edit 加 submit。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:edit') and @ss.hasPermi('order:sales:submit')")
    @Log(title = "销售订单修改并提交", businessType = BusinessType.UPDATE)
    @PutMapping("/submit")
    public AjaxResult editAndSubmit(@RequestBody SalesOrder order, @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, order.getOrderCode(), tableSuffix);
        return toAjax(orderService.updateAndSubmit(shopCode, order, suffix, SecurityUtils.getUsername()));
    }

    /**
     * 提交审核：草稿 / 已驳回 → 待审核。提交不预占库存。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:submit')")
    @Log(title = "销售订单提交", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/submit")
    public AjaxResult submit(@PathVariable("orderCode") String orderCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(orderService.submitOrder(shopCode, orderCode,
                resolveSuffix(shopCode, orderCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /**
     * 审核驳回：待审核 → 已驳回，原因必填。
     *
     * <p>权限与「审核通过」同一个 {@code order:sales:confirm}：审得了就驳得了，
     * 拆成两个权限只会出现「能通过但不能驳回」这种没意义的组合。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:confirm')")
    @Log(title = "销售订单驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/reject")
    public AjaxResult reject(@PathVariable("orderCode") String orderCode,
            @RequestBody Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, orderCode, stringOf(params, "tableSuffix"));
        return toAjax(orderService.rejectOrder(shopCode, orderCode, suffix,
                stringOf(params, "reason"), SecurityUtils.getUsername()));
    }

    /**
     * 撤回：待审核 → 草稿。提交人自己把还没审的单收回来改。
     *
     * <p>权限跟着 {@code order:sales:submit} 走，不跟 confirm：这是提交人的动作，
     * 能提交就该能收回；挂到审核权限上等于业务员撤不了自己的单。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:submit')")
    @Log(title = "销售订单撤回", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/withdraw")
    public AjaxResult withdraw(@PathVariable("orderCode") String orderCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(orderService.withdrawOrder(shopCode, orderCode,
                resolveSuffix(shopCode, orderCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /** 撤审：已备货/备货中 → 草稿。要求未发过货、无在途批次、无在制工单。不占库存，没有要释放的东西。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:confirm')")
    @Log(title = "销售订单撤审", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/unconfirm")
    public AjaxResult unconfirm(@PathVariable("orderCode") String orderCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(orderService.unconfirmOrder(shopCode, orderCode,
                resolveSuffix(shopCode, orderCode, tableSuffix), SecurityUtils.getUsername()));
    }

    /** 强制关闭：发了一部分不再发了，原因必填，释放剩余预占。 */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:close')")
    @Log(title = "销售订单关闭", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/close")
    public AjaxResult close(@PathVariable("orderCode") String orderCode,
            @RequestBody Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, orderCode, stringOf(params, "tableSuffix"));
        return toAjax(orderService.closeOrder(shopCode, orderCode, suffix,
                stringOf(params, "reason"), SecurityUtils.getUsername()));
    }

    /**
     * 审核通过。只接受待审核的单，<b>不占用任何库存</b>。
     *
     * <p>原先这里要传预占仓库。取消预占后不再需要——库存到发货那一刻才由出库单锁定扣减。
     * 请求体里多传的 warehouseCode 会被忽略，老前端不会因此报错。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:confirm')")
    @Log(title = "销售订单确认", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/confirm")
    public AjaxResult confirm(@PathVariable("orderCode") String orderCode,
            @RequestBody(required = false) Map<String, Object> params)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        String suffix = resolveSuffix(shopCode, orderCode, stringOf(params, "tableSuffix"));
        SalesOrder order = orderService.confirmOrder(shopCode, orderCode, suffix, SecurityUtils.getUsername());
        return success(order);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:cancel')")
    @Log(title = "销售订单取消", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderCode}/cancel")
    public AjaxResult cancel(@PathVariable("orderCode") String orderCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(orderService.cancelOrder(shopCode, orderCode,
                resolveSuffix(shopCode, orderCode, tableSuffix), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:sales:remove')")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderCode}")
    public AjaxResult remove(@PathVariable("orderCode") String orderCode,
            @RequestParam(required = false) String tableSuffix)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        return toAjax(orderService.deleteDraft(shopCode, orderCode, resolveSuffix(shopCode, orderCode, tableSuffix)));
    }

    // 这里原先有 POST /{orderCode}/source「登记备货来源」。备货来源这个概念整个去掉了：
    // 订单只管卖了什么、发了多少，货从哪来是备货侧的事。明细上仍留一个 sourcecode 记
    // 「正在负责它的生产工单」，但那只由生产模块在建/改/关工单时回写，没有人工入口。

    /*
     * 这里<b>故意没有</b>回款登记接口。订单的已收金额只有一个来源：财务模块的销售回款流水
     * 走完「认领绑定订单 → 终审通过」后回写（V2.0 §5.4 流程2），与采购单的已付货款同一口径。
     * 曾经开过一个 POST /{orderCode}/payment 让业务员手填，结果是订单被填成「超额收款」
     * 却找不到任何一笔对应的流水，财务再想认领时该订单已被「已收满」规则挡在下拉外面。
     * 需要改已收金额，请去财务模块补录流水，不要在这里加回来。
     */

    /**
     * 月表后缀：前端给了就用给的，没给就跨月找出来。
     *
     * <p>跨月找要扫 information_schema 再逐月查，比直接给贵得多，所以列表接口
     * 返回的每一行都带着 tableSuffix，前端后续操作应原样回传。</p>
     */
    private String resolveSuffix(String shopCode, String orderCode, String tableSuffix)
    {
        if (MonthTableSuffix.isValid(tableSuffix)) return tableSuffix;
        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, orderCode);
        if (order == null) throw new com.ruoyi.common.exception.ServiceException("订单不存在");
        return order.getTableSuffix();
    }

    private String stringOf(Map<String, Object> params, String key)
    {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : String.valueOf(value);
    }

}

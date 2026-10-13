package com.ruoyi.web.controller.finance;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.service.IFinanceTransactionService;

/**
 * 资金流水 Controller（说明书 5.5 接口1~7）
 */
@RestController
@RequestMapping("/finance/transaction")
public class FinanceTransactionController extends BaseController
{
    @Autowired
    private IFinanceTransactionService transactionService;

    /**
     * 接口7：查询流水列表。按交易时间或创建时间查询，跨月自动合并。
     *
     * <p>不能用 PageHelper 的 startPage：一次查询要扫多张月表，PageHelper 只会作用在第一条
     * SQL 上，分页结果会是错的。服务层把月表 union all 起来，让数据库排好序后只返回这一页。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:list')")
    @GetMapping("/list")
    public TableDataInfo list(FinanceTransaction query)
    {
        query.setShopCode(SecurityUtils.getBusinessShopCode());

        PageDomain page = TableSupport.buildPageRequest();
        // getPageDomain() 对两个参数都给了默认值（1 / 10），拿不到 null；
        // 调用方不传分页参数时按第一页 10 条走，与改造前一致
        IFinanceTransactionService.TransactionPage result = transactionService.selectTransactionPage(
                query, page.getPageNum(), page.getPageSize());

        TableDataInfo data = new TableDataInfo(result.getRows(), (int) result.getTotal());
        // TableDataInfo 的两参构造只塞 rows 和 total，code 会留在默认值 0。
        // 前端 request.js 里 `res.data.code || 200` 把 0 当成 200 蒙混过去了，
        // 但凡是严格按 code == 200 判断的调用方都会当成失败，这里手工补齐。
        data.setCode(HttpStatus.SUCCESS);
        data.setMsg("查询成功");
        return data;
    }

    /**
     * 流水详情
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:query')")
    @GetMapping("/{transactionCode}")
    public AjaxResult getInfo(@PathVariable("transactionCode") String transactionCode,
            @RequestParam("tableSuffix") String tableSuffix)
    {
        FinanceTransaction transaction = transactionService.selectTransactionByCode(
                SecurityUtils.getBusinessShopCode(), transactionCode, tableSuffix);
        if (transaction == null)
        {
            return error("资金流水不存在");
        }
        return success(transaction);
    }

    /**
     * 接口1：创建资金流水
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:add')")
    @Log(title = "资金流水", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody FinanceTransaction transaction)
    {
        transaction.setShopCode(SecurityUtils.getBusinessShopCode());
        return success(transactionService.createTransaction(transaction, SecurityUtils.getUsername()));
    }

    /**
     * 修改草稿或已驳回的流水
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:edit')")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody FinanceTransaction transaction)
    {
        transaction.setShopCode(SecurityUtils.getBusinessShopCode());
        return toAjax(transactionService.updateTransaction(transaction, SecurityUtils.getUsername()));
    }

    /**
     * 接口2：提交审核（流程2 里叫「提交认领」）
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:submit')")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PostMapping("/{transactionCode}/submit")
    public AjaxResult submit(@PathVariable("transactionCode") String transactionCode,
            @RequestParam("tableSuffix") String tableSuffix)
    {
        return toAjax(transactionService.submitTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, tableSuffix, SecurityUtils.getUsername()));
    }

    /**
     * 接口3：审核流水（通过 / 驳回）
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:audit')")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PostMapping("/{transactionCode}/audit")
    public AjaxResult audit(@PathVariable("transactionCode") String transactionCode,
            @RequestBody Map<String, Object> params)
    {
        boolean approved = params == null || !params.containsKey("approved")
                || Boolean.TRUE.equals(params.get("approved"));
        Object reason = params == null ? null : params.get("rejectReason");
        Object suffix = params == null ? null : params.get("tableSuffix");
        return toAjax(transactionService.auditTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, suffix == null ? null : String.valueOf(suffix), approved,
                reason == null ? null : String.valueOf(reason), SecurityUtils.getUsername()));
    }

    /**
     * 接口4：财务执行打款
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:execute')")
    @Log(title = "资金流水执行打款", businessType = BusinessType.UPDATE)
    @PostMapping("/{transactionCode}/execute")
    public AjaxResult execute(@PathVariable("transactionCode") String transactionCode,
            @RequestBody ExecuteCommand command)
    {
        return toAjax(transactionService.executeTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, command.getTableSuffix(), command.getAccountCode(),
                command.getTradeTime(), command.getAttachment(), SecurityUtils.getUsername()));
    }

    /**
     * 接口5：业务员认领并绑定订单
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:claim')")
    @Log(title = "资金流水认领", businessType = BusinessType.UPDATE)
    @PostMapping("/{transactionCode}/claim")
    public AjaxResult claim(@PathVariable("transactionCode") String transactionCode,
            @RequestBody Map<String, Object> params)
    {
        Object allow = params == null ? null : params.get("allowOverpay");
        return toAjax(transactionService.claimTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, stringOf(params, "tableSuffix"), stringOf(params, "bizCode"),
                Boolean.TRUE.equals(allow) || "true".equals(String.valueOf(allow)),
                SecurityUtils.getUsername()));
    }

    private String stringOf(Map<String, Object> params, String key)
    {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 撤回认领：已审核 → 待审核，清掉订单号。认领人本人或财务审核人才能撤，由服务层判。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:claim')")
    @Log(title = "资金流水撤回认领", businessType = BusinessType.UPDATE)
    @PostMapping("/{transactionCode}/unclaim")
    public AjaxResult unclaim(@PathVariable("transactionCode") String transactionCode,
            @RequestParam("tableSuffix") String tableSuffix)
    {
        return toAjax(transactionService.unclaimTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, tableSuffix, SecurityUtils.getUsername()));
    }

    /**
     * 待认领的客户回款，供销售订单详情的「认领打款」弹窗选流水。
     *
     * <p>只列与订单同币种的：系统不做汇率换算，认到别的币种的订单上等于把两种钱加到一起。
     * 权限与认领同一个，能认领的人才需要看这份列表。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:claim')")
    @GetMapping("/unclaimed")
    public AjaxResult unclaimed(@RequestParam("currency") String currency)
    {
        return success(transactionService.selectUnclaimedReceipts(
                SecurityUtils.getBusinessShopCode(), currency));
    }

    /**
     * 接口6：完成流水（非业务类）
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:audit')")
    @Log(title = "资金流水完成", businessType = BusinessType.UPDATE)
    @PostMapping("/{transactionCode}/complete")
    public AjaxResult complete(@PathVariable("transactionCode") String transactionCode,
            @RequestParam("tableSuffix") String tableSuffix)
    {
        return toAjax(transactionService.completeTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, tableSuffix, SecurityUtils.getUsername()));
    }

    /**
     * 删除草稿或已驳回的流水
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:transaction:remove')")
    @Log(title = "资金流水", businessType = BusinessType.DELETE)
    @DeleteMapping("/{transactionCode}")
    public AjaxResult remove(@PathVariable("transactionCode") String transactionCode,
            @RequestParam("tableSuffix") String tableSuffix)
    {
        return toAjax(transactionService.deleteTransaction(SecurityUtils.getBusinessShopCode(),
                transactionCode, tableSuffix));
    }

    /**
     * 说明书 6.4 对外接口：付款进度 / 回款进度查询。
     * 采购模块和订单模块按业务单据号查已完成的收付款合计。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/progress")
    public AjaxResult progress(@RequestParam("bizType") String bizType,
            @RequestParam("bizCode") String bizCode)
    {
        return success(transactionService.sumCompletedAmount(
                SecurityUtils.getBusinessShopCode(), bizType, bizCode));
    }

    /**
     * 说明书 6.4 对外接口：某张业务单据下的收付款流水明细（含在途）。
     *
     * <p>权限只要 hasBusinessShop()，与 /progress 一致：调用方是订单详情和采购单详情，
     * 看单的业务员、采购员未必有 finance:transaction:list。这里只按单号取自己那张单的流水，
     * 拿不到别的单据的资金数据。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/biz")
    public AjaxResult biz(@RequestParam("bizType") String bizType,
            @RequestParam("bizCode") String bizCode)
    {
        return success(transactionService.selectByBusinessDocument(
                SecurityUtils.getBusinessShopCode(), bizType, bizCode));
    }

    /**
     * 执行打款的入参。交易时间要按 yyyy-MM-dd HH:mm:ss 反序列化，
     * 用独立的命令对象接收，避免把整个流水对象暴露成可写入口。
     */
    public static class ExecuteCommand
    {
        private String tableSuffix;

        private String accountCode;

        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date tradeTime;

        private String attachment;

        public String getTableSuffix() { return tableSuffix; }

        public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }

        public String getAccountCode() { return accountCode; }

        public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

        public Date getTradeTime() { return tradeTime; }

        public void setTradeTime(Date tradeTime) { this.tradeTime = tradeTime; }

        public String getAttachment() { return attachment; }

        public void setAttachment(String attachment) { this.attachment = attachment; }
    }
}

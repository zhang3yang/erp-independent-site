package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.core.domain.PriceScopeAware;
import com.ruoyi.common.enums.PriceScope;

/**
 * 资金流水表（说明书 5.3.2 u_f_transaction）
 *
 * <p>按月分表，表名 {@code u_f_transaction_yyyyMM}，落在哪张表由 createtime 决定，
 * {@link #tableSuffix} 是路由用的非数据库字段。</p>
 *
 * <p>状态流转（说明书 5.4）：
 * <pre>
 *   草稿 ──提交──▶ 待审核 ──审核通过──▶ 已审核 ──执行打款──▶ 已完成   （流程1 支出）
 *                          └──驳回────▶ 已驳回 ──修改──▶ 草稿
 *   草稿 ──提交认领──▶ 待审核 ──认领绑定──▶ 已审核 ──审核──▶ 已完成   （流程2 收入）
 *   草稿 ──提交──▶ 待审核 ──审核通过──▶ 已完成                       （流程3 非业务）
 * </pre>
 * 说明书 5.3.2 的枚举漏了「已审核」，但 5.4 流程1 明确用到，按流程图补齐。</p>
 */
public class FinanceTransaction implements Serializable, PriceScopeAware
{
    private static final long serialVersionUID = 1L;

    /** 收入 */
    public static final String TYPE_INCOME = "收入";

    /** 支出 */
    public static final String TYPE_EXPENSE = "支出";

    /** 草稿 */
    public static final String STATUS_DRAFT = "草稿";

    /** 待审核 */
    public static final String STATUS_SUBMITTED = "待审核";

    /** 已审核 */
    public static final String STATUS_AUDITED = "已审核";

    /** 已完成 */
    public static final String STATUS_COMPLETED = "已完成";

    /** 已驳回 */
    public static final String STATUS_REJECTED = "已驳回";

    /** 业务类型：采购 */
    public static final String BIZ_PURCHASE = "采购";

    /** 业务类型：销售 */
    public static final String BIZ_SALE = "销售";

    /** 业务类型：其他（非业务记录） */
    public static final String BIZ_OTHER = "其他";

    /** 主键ID */
    private Long id;

    /** 商家编码 */
    private String shopCode;

    /** 月表后缀 yyyyMM，非数据库字段，用于定位流水所在月表 */
    private String tableSuffix;

    /** 流水代码 */
    private String transactionCode;

    /** 我方账户代码 */
    private String accountCode;

    /** 我方账户简称（冗余） */
    private String accountName;

    /** 收入/支出 */
    private String transType;

    /** 收支分类代码 */
    private String categoryCode;

    /** 收支分类名称（冗余快照，分类改名后历史流水仍显示当时的名称） */
    private String categoryName;

    /** 变动金额：收入为正，支出为负 */
    @PriceField(PriceScope.AUTO)
    private BigDecimal amount;

    /** 执行完成后账户余额 */
    @PriceField(PriceScope.AUTO)
    private BigDecimal balance;

    /** 币种快照，完成入账时取账户币种 */
    private String currency;

    /** 对方名称 */
    private String partyName;

    /** 对方文本信息 */
    private String partyText;

    /** 对方图片信息 */
    private String partyImage;

    /** 资金实际交易时间（排序对账依据） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date tradeTime;

    /** 采购/销售/其他（费用、工资仅作为历史数据保留） */
    private String bizType;

    /** 业务单据号 */
    private String bizCode;

    /** 业务摘要 */
    private String bizSummary;

    /** 草稿/待审核/已审核/已完成/已驳回 */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 审核人 */
    private String auditUser;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date auditTime;

    /** 完成人 */
    private String completeUser;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date completeTime;

    /** 凭证附件路径 */
    private String attachment;

    /** 备注 */
    private String remark;

    /** 创建人昵称，仅查询返回 */
    private String createUserName;

    /** 审核人昵称，仅查询返回 */
    private String auditUserName;

    /** 完成人昵称，仅查询返回 */
    private String completeUserName;

    // ---------- 查询条件，非数据库字段 ----------

    /** 交易时间起，查询用 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date tradeTimeBegin;

    /** 交易时间止，查询用 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date tradeTimeEnd;

    /** 创建时间起，查询用 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTimeBegin;

    /** 创建时间止，查询用 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTimeEnd;

    /**
     * 允许超额付款/收款，非数据库字段，只在建单与改单的入参里出现。
     *
     * <p>默认 false：本笔金额超过采购单的待付时后端拒绝，由用户在界面上确认后再带 true 重试。
     * 与销售侧认领接口的 allowOverpay 是同一个口径，只是那边走接口参数、这边走单据字段——
     * 采购付款是在建单时选的采购单，确认动作也就落在建单这一步。</p>
     */
    private Boolean allowOverpay;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }

    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getTableSuffix() { return tableSuffix; }

    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }

    public String getTransactionCode() { return transactionCode; }

    public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }

    public String getAccountCode() { return accountCode; }

    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getAccountName() { return accountName; }

    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getTransType() { return transType; }

    public void setTransType(String transType) { this.transType = transType; }

    public String getCategoryCode() { return categoryCode; }

    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }

    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getAmount() { return amount; }

    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalance() { return balance; }

    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }

    public void setCurrency(String currency) { this.currency = currency; }

    public String getPartyName() { return partyName; }

    public void setPartyName(String partyName) { this.partyName = partyName; }

    public String getPartyText() { return partyText; }

    public void setPartyText(String partyText) { this.partyText = partyText; }

    public String getPartyImage() { return partyImage; }

    public void setPartyImage(String partyImage) { this.partyImage = partyImage; }

    public Date getTradeTime() { return tradeTime; }

    public void setTradeTime(Date tradeTime) { this.tradeTime = tradeTime; }

    public String getBizType() { return bizType; }

    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getBizCode() { return bizCode; }

    public void setBizCode(String bizCode) { this.bizCode = bizCode; }

    public String getBizSummary() { return bizSummary; }

    public void setBizSummary(String bizSummary) { this.bizSummary = bizSummary; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getRejectReason() { return rejectReason; }

    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public String getCreateUser() { return createUser; }

    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }

    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getAuditUser() { return auditUser; }

    public void setAuditUser(String auditUser) { this.auditUser = auditUser; }

    public Date getAuditTime() { return auditTime; }

    public void setAuditTime(Date auditTime) { this.auditTime = auditTime; }

    public String getCompleteUser() { return completeUser; }

    public void setCompleteUser(String completeUser) { this.completeUser = completeUser; }

    public Date getCompleteTime() { return completeTime; }

    public void setCompleteTime(Date completeTime) { this.completeTime = completeTime; }

    public String getAttachment() { return attachment; }

    public void setAttachment(String attachment) { this.attachment = attachment; }

    public String getRemark() { return remark; }

    public void setRemark(String remark) { this.remark = remark; }

    public String getCreateUserName() { return createUserName; }

    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }

    public String getAuditUserName() { return auditUserName; }

    public void setAuditUserName(String auditUserName) { this.auditUserName = auditUserName; }

    public String getCompleteUserName() { return completeUserName; }

    public void setCompleteUserName(String completeUserName) { this.completeUserName = completeUserName; }

    public Date getTradeTimeBegin() { return tradeTimeBegin; }

    public void setTradeTimeBegin(Date tradeTimeBegin) { this.tradeTimeBegin = tradeTimeBegin; }

    public Date getTradeTimeEnd() { return tradeTimeEnd; }

    public void setTradeTimeEnd(Date tradeTimeEnd) { this.tradeTimeEnd = tradeTimeEnd; }

    public Boolean getAllowOverpay() { return allowOverpay; }

    public void setAllowOverpay(Boolean allowOverpay) { this.allowOverpay = allowOverpay; }

    public Date getCreateTimeBegin() { return createTimeBegin; }

    public void setCreateTimeBegin(Date createTimeBegin) { this.createTimeBegin = createTimeBegin; }

    public Date getCreateTimeEnd() { return createTimeEnd; }

    public void setCreateTimeEnd(Date createTimeEnd) { this.createTimeEnd = createTimeEnd; }

    /**
     * 资金流水的金额算哪一侧，按业务类型分。
     *
     * <p>它不能像财务模块的其余部分那样「靠 finance:* 管着就行」：
     * {@code /finance/transaction/biz} 是<b>故意</b>只按 {@code hasBusinessShop()} 放行的，
     * 好让没有 finance 权限的业务员、采购员在自己的单据详情里看到款走到哪一步了
     * （见 {@code FinanceTransactionController.biz} 的注释）。所以采购付款的金额会经这个口子
     * 流到采购订单详情页上，必须按成本侧遮。</p>
     *
     * <ul>
     *   <li>采购付款 → 成本侧；销售回款 → 销售侧；</li>
     *   <li>「其他」这类非业务流水、以及 {@code balance} 这种账户余额，说不清归哪边，
     *       返回 {@code null} 按最严的走：两个权限都有才给看。</li>
     * </ul>
     */
    @Override
    public PriceScope resolvePriceScope(String fieldName)
    {
        if (!"amount".equals(fieldName))
        {
            return null;
        }
        if (BIZ_PURCHASE.equals(bizType))
        {
            return PriceScope.COST;
        }
        return BIZ_SALE.equals(bizType) ? PriceScope.SALE : null;
    }
}

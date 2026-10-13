package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.core.domain.PriceScopeAware;
import com.ruoyi.common.enums.PriceScope;
import com.ruoyi.system.constant.InventoryDocumentTypes;

/**
 * 单据主表实体类
 * 对应表：sdsorder.u_f_docmain_{yyyyMM}
 */
public class DocumentMain implements Serializable, PriceScopeAware
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 单据代码 */
    private String docCode;

    /** 单据类型 */
    private String docType;

    /** 查询时允许的单据类型，逗号分隔 */
    private String docTypes;

    /** 单据类型名称 */
    private String docTypeName;

    /** 单据日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date docDate;

    /** 来源仓库代码 */
    private String fromWareCode;

    /** 来源仓库名称 */
    private String fromWareName;

    /** 目标仓库代码 */
    private String toWareCode;

    /** 目标仓库名称 */
    private String toWareName;

    /** 来源良品状态：1-良品，0-不良品 */
    private Integer fromQualityFlag;

    /** 来源在库状态：1-在库，0-不在库 */
    private Integer fromOnhandFlag;


    /** 目标良品状态：1-良品，0-不良品 */
    private Integer toQualityFlag;

    /** 目标在库状态：1-在库，0-不在库 */
    private Integer toOnhandFlag;


    /** 往来单位代码 */
    private String partnerCode;

    /** 往来单位名称 */
    private String partnerName;

    /**
     * 商品内容摘要，形如「可乐等3种」。
     *
     * <p>由明细整体重算（{@link com.ruoyi.system.utils.GoodsSummaryBuilder}），
     * 只给列表页看，不参与任何计算。</p>
     */
    private String goodsSummary;

    /** 创建人 */
    private String createUser;

    /** 经手人 */
    private String handler;

    /** 业务发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date handlerTime;

    /** 审核状态 */
    private String confirmState;

    /** 审核人 */
    private String confirmUser;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmTime;

    /** 来源单据代码 */
    private String sourceDocCode;

    /** 来源单据类型 */
    private String sourceDocType;

    /** 来源单据类型名称 */
    private String sourceDocTypeName;

    /** 总数量 */
    private BigDecimal totalQty;

    /** 总金额 */
    @PriceField(PriceScope.AUTO)
    private BigDecimal totalAmount;

    /**
     * 查询用：把单据限定在这个创建人名下。<b>不是数据库列</b>。
     *
     * <p>横向权限，目前只有销售退货在用（{@code SalesReturnServiceImpl}）。
     * ⚠ 这个字段挂在<b>共享</b>的 DocumentMain 上，而入库、出库、调拨、生产的查询
     * 走的是同一条 mapper 语句——只有需要限归属的那条服务方法才给它赋值，
     * 别在共享路径上设它，否则库存那几个页面会跟着一起被筛。</p>
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String ownerScope;

    /** 备注 */
    private String remark;

    /** 月表后缀（如 202607） */
    private String tableSuffix;

    /**
     * 列表查询用的单据日期区间（非数据库字段）。
     *
     * <p>单据按 docdate 分月表，所以这个区间同时决定两件事：扫哪几张月表，以及月表内部
     * 再按 docdate 过滤。不填就查全部现存月表——跨月之后用户最常抱怨的就是「上个月的单
     * 不见了」，默认查当月比列表长一点更让人困惑。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date docDateBegin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date docDateEnd;

    /**
     * 客户端幂等键。该字段只从 Idempotency-Key 请求头读取，不落入业务 JSON 之外的数据库字段映射。
     */
    private String idempotencyKey;

    /** 单据明细列表 */
    /** 创建人昵称（非数据库字段，查询时按用户账号关联 sys_user 带出，供前端展示） */
    private String createUserName;

    public String getCreateUserName()
    {
        return createUserName;
    }

    public void setCreateUserName(String createUserName)
    {
        this.createUserName = createUserName;
    }

    /** 审核人昵称（非数据库字段，查询时按用户账号关联 sys_user 带出，供前端展示） */
    private String confirmUserName;

    public String getConfirmUserName()
    {
        return confirmUserName;
    }

    public void setConfirmUserName(String confirmUserName)
    {
        this.confirmUserName = confirmUserName;
    }

    private List<DocumentDetail> details;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getDocTypes() { return docTypes; }
    public void setDocTypes(String docTypes) { this.docTypes = docTypes; }

    public String getDocTypeName() { return docTypeName; }
    public void setDocTypeName(String docTypeName) { this.docTypeName = docTypeName; }

    public Date getDocDate() { return docDate; }
    public void setDocDate(Date docDate) { this.docDate = docDate; }

    public Date getDocDateBegin() { return docDateBegin; }
    public void setDocDateBegin(Date docDateBegin) { this.docDateBegin = docDateBegin; }

    public Date getDocDateEnd() { return docDateEnd; }
    public void setDocDateEnd(Date docDateEnd) { this.docDateEnd = docDateEnd; }

    public String getFromWareCode() { return fromWareCode; }
    public void setFromWareCode(String fromWareCode) { this.fromWareCode = fromWareCode; }

    public String getFromWareName() { return fromWareName; }
    public void setFromWareName(String fromWareName) { this.fromWareName = fromWareName; }

    public String getToWareCode() { return toWareCode; }
    public void setToWareCode(String toWareCode) { this.toWareCode = toWareCode; }

    public String getToWareName() { return toWareName; }
    public void setToWareName(String toWareName) { this.toWareName = toWareName; }

    public Integer getFromQualityFlag() { return fromQualityFlag; }
    public void setFromQualityFlag(Integer fromQualityFlag) { this.fromQualityFlag = fromQualityFlag; }

    public Integer getFromOnhandFlag() { return fromOnhandFlag; }
    public void setFromOnhandFlag(Integer fromOnhandFlag) { this.fromOnhandFlag = fromOnhandFlag; }


    public Integer getToQualityFlag() { return toQualityFlag; }
    public void setToQualityFlag(Integer toQualityFlag) { this.toQualityFlag = toQualityFlag; }

    public Integer getToOnhandFlag() { return toOnhandFlag; }
    public void setToOnhandFlag(Integer toOnhandFlag) { this.toOnhandFlag = toOnhandFlag; }


    /** 来源三维状态；三个维度都没填时返回 null，表示该单据不涉及来源状态 */
    public StockState getFromState()
    {
        if (fromQualityFlag == null && fromOnhandFlag == null)
        {
            return null;
        }
        return StockState.of(fromQualityFlag, fromOnhandFlag);
    }

    /** 目标三维状态；三个维度都没填时返回 null，表示该单据不涉及目标状态 */
    public StockState getToState()
    {
        if (toQualityFlag == null && toOnhandFlag == null)
        {
            return null;
        }
        return StockState.of(toQualityFlag, toOnhandFlag);
    }

    /** 用三维状态一次性设置来源状态 */
    public void setFromState(StockState state)
    {
        this.fromQualityFlag = state == null ? null : state.getQualityFlag();
        this.fromOnhandFlag = state == null ? null : state.getOnhandFlag();
    }

    /** 用三维状态一次性设置目标状态 */
    public void setToState(StockState state)
    {
        this.toQualityFlag = state == null ? null : state.getQualityFlag();
        this.toOnhandFlag = state == null ? null : state.getOnhandFlag();
    }

    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getGoodsSummary() { return goodsSummary; }
    public void setGoodsSummary(String goodsSummary) { this.goodsSummary = goodsSummary; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }

    public Date getHandlerTime() { return handlerTime; }
    public void setHandlerTime(Date handlerTime) { this.handlerTime = handlerTime; }

    public String getConfirmState() { return confirmState; }
    public void setConfirmState(String confirmState) { this.confirmState = confirmState; }

    public String getConfirmUser() { return confirmUser; }
    public void setConfirmUser(String confirmUser) { this.confirmUser = confirmUser; }

    public Date getConfirmTime() { return confirmTime; }
    public void setConfirmTime(Date confirmTime) { this.confirmTime = confirmTime; }

    public String getSourceDocCode() { return sourceDocCode; }
    public void setSourceDocCode(String sourceDocCode) { this.sourceDocCode = sourceDocCode; }

    public String getSourceDocType() { return sourceDocType; }
    public void setSourceDocType(String sourceDocType) { this.sourceDocType = sourceDocType; }

    public String getSourceDocTypeName() { return sourceDocTypeName; }
    public void setSourceDocTypeName(String sourceDocTypeName) { this.sourceDocTypeName = sourceDocTypeName; }

    public BigDecimal getTotalQty() { return totalQty; }
    public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getOwnerScope() { return ownerScope; }
    public void setOwnerScope(String ownerScope) { this.ownerScope = ownerScope; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public List<DocumentDetail> getDetails() { return details; }
    public void setDetails(List<DocumentDetail> details) { this.details = details; }

    /**
     * {@code totalAmount} 这一列两种口径：销售出库单存的是销售额，其余单据存的是成本额
     * （见 {@code DocumentServiceImpl.isSalesOutbound}），所以它标的是
     * {@link PriceScope#AUTO}，由本方法在序列化时回答算哪一侧。
     *
     * <p>{@code docType} 为空时返回 {@code null}（查询条件对象、还没定类型的草稿），
     * 调用方会按最严的走：两个权限都有才给看。</p>
     */
    @Override
    public PriceScope resolvePriceScope(String fieldName)
    {
        if (docType == null)
        {
            return null;
        }
        return InventoryDocumentTypes.SALES_OUTBOUND.equals(docType) ? PriceScope.SALE : PriceScope.COST;
    }
}

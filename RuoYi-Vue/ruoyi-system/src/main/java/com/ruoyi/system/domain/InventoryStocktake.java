package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 库存盘点单
 * 对应表：sdsshop.u_m_stocktake
 *
 * <p>流程：建单即快照账面 → 盘点中（录实盘数，可反复保存）→ 提交 → 待审核
 * → 审核通过：差异生成盘盈入库 PYRK / 盘亏出库 PKCK 并当场过账 → 已完成。
 * 待审核可驳回回到盘点中；盘点中、待审核都可以作废。</p>
 *
 * <p>「盘点中」「待审核」两个状态下，单上的商品在这个仓库里不能过账任何库存单据，
 * 见 StocktakeFreezeGuard。</p>
 */
public class InventoryStocktake implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_COUNTING = "盘点中";
    public static final String STATUS_SUBMITTED = "待审核";
    public static final String STATUS_DONE = "已完成";
    public static final String STATUS_CANCELLED = "已作废";

    public static final String SCOPE_ALL = "all";
    public static final String SCOPE_GOODS = "goods";

    private Long id;

    private String shopCode;

    private String stocktakeCode;

    private String wareCode;

    private String wareName;

    /** all 全仓 / goods 指定商品 */
    private String scope;

    private String status;

    private Integer lineCount;

    private Integer diffCount;

    private BigDecimal gainQty;

    @PriceField(PriceScope.COST)
    private BigDecimal gainAmount;

    private BigDecimal lossQty;

    @PriceField(PriceScope.COST)
    private BigDecimal lossAmount;

    /** 审核生成的调整单号，逗号分隔 */
    private String adjustDocCodes;

    /** 调整单所在月表后缀，前端跳单据详情要用 */
    private String adjustTableSuffix;

    private String remark;

    private String rejectReason;

    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private String submitUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    private String confirmUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 创建人昵称，列表展示用 */
    private String createUserName;

    /** 审核人昵称，列表展示用 */
    private String confirmUserName;

    /** 明细，详情接口才带 */
    private List<InventoryStocktakeItem> items;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getStocktakeCode()
    {
        return stocktakeCode;
    }

    public void setStocktakeCode(String stocktakeCode)
    {
        this.stocktakeCode = stocktakeCode;
    }

    public String getWareCode()
    {
        return wareCode;
    }

    public void setWareCode(String wareCode)
    {
        this.wareCode = wareCode;
    }

    public String getWareName()
    {
        return wareName;
    }

    public void setWareName(String wareName)
    {
        this.wareName = wareName;
    }

    public String getScope()
    {
        return scope;
    }

    public void setScope(String scope)
    {
        this.scope = scope;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getLineCount()
    {
        return lineCount;
    }

    public void setLineCount(Integer lineCount)
    {
        this.lineCount = lineCount;
    }

    public Integer getDiffCount()
    {
        return diffCount;
    }

    public void setDiffCount(Integer diffCount)
    {
        this.diffCount = diffCount;
    }

    public BigDecimal getGainQty()
    {
        return gainQty;
    }

    public void setGainQty(BigDecimal gainQty)
    {
        this.gainQty = gainQty;
    }

    public BigDecimal getGainAmount()
    {
        return gainAmount;
    }

    public void setGainAmount(BigDecimal gainAmount)
    {
        this.gainAmount = gainAmount;
    }

    public BigDecimal getLossQty()
    {
        return lossQty;
    }

    public void setLossQty(BigDecimal lossQty)
    {
        this.lossQty = lossQty;
    }

    public BigDecimal getLossAmount()
    {
        return lossAmount;
    }

    public void setLossAmount(BigDecimal lossAmount)
    {
        this.lossAmount = lossAmount;
    }

    public String getAdjustDocCodes()
    {
        return adjustDocCodes;
    }

    public void setAdjustDocCodes(String adjustDocCodes)
    {
        this.adjustDocCodes = adjustDocCodes;
    }

    public String getAdjustTableSuffix()
    {
        return adjustTableSuffix;
    }

    public void setAdjustTableSuffix(String adjustTableSuffix)
    {
        this.adjustTableSuffix = adjustTableSuffix;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getCreateUser()
    {
        return createUser;
    }

    public void setCreateUser(String createUser)
    {
        this.createUser = createUser;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getSubmitUser()
    {
        return submitUser;
    }

    public void setSubmitUser(String submitUser)
    {
        this.submitUser = submitUser;
    }

    public Date getSubmitTime()
    {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime)
    {
        this.submitTime = submitTime;
    }

    public String getConfirmUser()
    {
        return confirmUser;
    }

    public void setConfirmUser(String confirmUser)
    {
        this.confirmUser = confirmUser;
    }

    public Date getConfirmTime()
    {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime)
    {
        this.confirmTime = confirmTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getCreateUserName()
    {
        return createUserName;
    }

    public void setCreateUserName(String createUserName)
    {
        this.createUserName = createUserName;
    }

    public String getConfirmUserName()
    {
        return confirmUserName;
    }

    public void setConfirmUserName(String confirmUserName)
    {
        this.confirmUserName = confirmUserName;
    }

    public List<InventoryStocktakeItem> getItems()
    {
        return items;
    }

    public void setItems(List<InventoryStocktakeItem> items)
    {
        this.items = items;
    }
}

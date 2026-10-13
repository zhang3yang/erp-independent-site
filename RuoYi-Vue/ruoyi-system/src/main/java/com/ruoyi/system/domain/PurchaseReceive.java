package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

public class PurchaseReceive implements Serializable
{
    private Long id;
    private String shopCode;
    private String receiveCode;
    private String purchaseCode;
    /**
     * 发货日期。采购员登记，决定本单落哪张月表；签收时间在发货阶段为空，不能再拿它定表。
     *
     * <p>只到天，必须单独声明格式：全局 Jackson 配置是 {@code yyyy-MM-dd HH:mm:ss}，
     * 前端日期选择器传的是 {@code 2026-08-17}，不标注会反序列化失败。
     * 与 PurchaseOrder 的 purchaseDate / expectDate 处理一致。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date sendDate;
    private String logisticsNo;
    private String carrier;
    private String freightType;
    /**
     * 建议入库仓库。一张发货单只入一个仓（决议 B6-1），所以放在单头而不是明细行上。
     *
     * <p><b>不落库</b>：{@code u_p_receive} 没有这一列。写入时铺到全部明细行的
     * warehousecode，读取时从明细行反推。最终入库仓库仍以入库员在采购入库时选的为准。</p>
     */
    private String warehouseCode;
    /** 建议入库仓库名称，不落库，查询时由明细行带出 */
    private String warehouseName;
    /** 预估运费。采购员登记发货时按供应商报价填。 */
    @PriceField(PriceScope.COST)
    private BigDecimal estFreightAmount;
    /** 实付运费。入库员签收时填，一张发货单分多次入库时累加。 */
    @PriceField(PriceScope.COST)
    private BigDecimal freightAmount;
    /** 预计到货总数量 */
    private BigDecimal totalQty;
    /**
     * 实际到货总数量，不落库，列表查询时由明细行汇总带出。
     * 还没入库的行 actualqty 为 NULL，不计入合计。
     */
    private BigDecimal totalActualQty;

    /**
     * 已被入库单占用的明细行数（actualqty 非空），查询时现算，不落库。
     *
     * <p>大于 0 说明入库单已经建出来了，整单只读。单头状态这时还是「待入库」
     * （货没过账就还没进仓库），光看状态区分不出来，界面靠这个字段决定要不要显示修改/取消。</p>
     */
    private Integer processingLines;
    /** 签收时间。入库员填；多次入库时记最后一次的值。 */
    private Date receiveTime;
    /** 签收人。入库员填；多次入库时记最后一次的值。 */
    private String receiveUser;
    private String status;
    private String remark;
    private String createUser;
    private Date createTime;
    private String updateUser;
    private Date updateTime;
    private String tableSuffix;
    /** 签收人、创建人昵称（非数据库字段，查询时按用户账号关联 sys_user 带出，供前端展示） */
    private String receiveUserName;
    private String createUserName;
    private List<PurchaseReceiveItem> items;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getShopCode(){return shopCode;} public void setShopCode(String v){shopCode=v;}
    public String getReceiveCode(){return receiveCode;} public void setReceiveCode(String v){receiveCode=v;}
    public String getPurchaseCode(){return purchaseCode;} public void setPurchaseCode(String v){purchaseCode=v;}
    public String getLogisticsNo(){return logisticsNo;} public void setLogisticsNo(String v){logisticsNo=v;}
    public String getCarrier(){return carrier;} public void setCarrier(String v){carrier=v;}
    public String getFreightType(){return freightType;} public void setFreightType(String v){freightType=v;}
    public Date getSendDate(){return sendDate;} public void setSendDate(Date v){sendDate=v;}
    public String getWarehouseCode(){return warehouseCode;} public void setWarehouseCode(String v){warehouseCode=v;}
    public String getWarehouseName(){return warehouseName;} public void setWarehouseName(String v){warehouseName=v;}
    public BigDecimal getEstFreightAmount(){return estFreightAmount;} public void setEstFreightAmount(BigDecimal v){estFreightAmount=v;}
    public BigDecimal getFreightAmount(){return freightAmount;} public void setFreightAmount(BigDecimal v){freightAmount=v;}
    public BigDecimal getTotalQty(){return totalQty;} public void setTotalQty(BigDecimal v){totalQty=v;}
    public BigDecimal getTotalActualQty(){return totalActualQty;} public void setTotalActualQty(BigDecimal v){totalActualQty=v;}
    public Integer getProcessingLines(){return processingLines;} public void setProcessingLines(Integer v){processingLines=v;}
    public Date getReceiveTime(){return receiveTime;} public void setReceiveTime(Date v){receiveTime=v;}
    public String getReceiveUser(){return receiveUser;} public void setReceiveUser(String v){receiveUser=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
    public String getCreateUser(){return createUser;} public void setCreateUser(String v){createUser=v;}
    public Date getCreateTime(){return createTime;} public void setCreateTime(Date v){createTime=v;}
    public String getUpdateUser(){return updateUser;} public void setUpdateUser(String v){updateUser=v;}
    public Date getUpdateTime(){return updateTime;} public void setUpdateTime(Date v){updateTime=v;}
    public String getTableSuffix(){return tableSuffix;} public void setTableSuffix(String v){tableSuffix=v;}
    public String getReceiveUserName(){return receiveUserName;} public void setReceiveUserName(String v){receiveUserName=v;}
    public String getCreateUserName(){return createUserName;} public void setCreateUserName(String v){createUserName=v;}
    public List<PurchaseReceiveItem> getItems(){return items;} public void setItems(List<PurchaseReceiveItem> v){items=v;}
}

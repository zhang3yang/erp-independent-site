package com.ruoyi.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;

/**
 * 发货批次主表 u_f_shipbatch_{yyyyMM}。
 *
 * <p>落表月份取 {@code shipdate}（计划发货日期）所在月，<b>不是</b>订单的 createtime——
 * 一张订单的多个批次可以跨月，按订单查全部批次必须跨月表扫。</p>
 */
public class ShipBatch implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 待确认：业务员刚建，还没交给仓库。可改可取消，仓库看不见。 */
    public static final String STATUS_PENDING = "待确认";
    /** 已提交仓库：业务员点了确认，批次交给仓库。仓库发货管理从这一刻起才看得到。 */
    public static final String STATUS_SUBMITTED = "已提交仓库";
    /** 已打印备货单：仓库打完备货单，开始按单拣货。 */
    public static final String STATUS_PICK_PRINTED = "已打印备货单";
    /**
     * 备货完成：货已拣好装箱，库管员传了备货照片。<b>库存一分没扣</b>。
     *
     * <p>这一格是交接点：在此之前批次归仓库，在此之后轮到业务员录快递单号。
     * 照片是业务员在录单前唯一能看到的备货凭证，所以至少要有一张，见
     * {@code IShipBatchService#completePicking}。</p>
     */
    public static final String STATUS_PICKED = "备货完成";
    /**
     * 已录入快递单号：业务员填了快递单号、承运商与本次实发数量，付款额度也校验过了。
     * <b>货还在仓库里、库存也一分没扣</b>。
     *
     * <p>这一格是「等仓库打面单」的等待期，不是「货已经出门」。真正出门是「完成」那一格。</p>
     */
    public static final String STATUS_LOGISTICS_ENTERED = "已录入快递单号";
    /**
     * 已打印快递单号：仓库把面单打出来贴在箱子上了，在等承运商上门。<b>库存仍然一分没扣</b>。
     *
     * <p>装错箱、贴错单、客户临时改主意都还可能发生在这段时间里，所以这一格<b>可以驳回</b>
     * ——退回「待确认」重排。真正出门是下一格「完成」。</p>
     */
    public static final String STATUS_LABEL_PRINTED = "已打印快递单号";
    /**
     * 已完成：承运商已把货取走，销售出库单已过账，库存已扣、订单已发量已回写。终态。
     *
     * <p>这一刻对应现实中<b>货真的离开仓库</b>，所以是不可逆的分界线：之前怎么改都行，
     * 之后只能走销售退货。</p>
     */
    public static final String STATUS_COMPLETED = "已完成";
    /** 已取消：终态。 */
    public static final String STATUS_CANCELLED = "已取消";

    private Long id;
    private String shopCode;
    private String shipCode;
    private String orderCode;

    /**
     * 计划发货日期，决定本批次落哪张月表。
     *
     * <p>只到天，必须单独标注格式：全局 Jackson 是 {@code yyyy-MM-dd HH:mm:ss}，
     * 前端日期选择器传 {@code 2026-08-20}，不标注会反序列化失败。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date shipDate;
    /** 实际发货时间，发货动作执行时回填 */
    private Date actualShipTime;

    private String warehouseCode;
    private String warehouseName;
    private String carrier;
    private String logisticsNo;
    /** 本批次实付运费。方向：付出去，与订单主表的同名字段（向客户收）相反。 */
    @PriceField(PriceScope.COST)
    private BigDecimal freightAmount;
    @PriceField(PriceScope.COST)
    private BigDecimal freightPaid;
    private String freightPayStatus;
    private String payUser;
    private Date payTime;

    private String status;
    private String createUser;
    private Date createTime;
    private String confirmUser;
    private Date confirmTime;
    /**
     * 发货人与发货时间，在「完成」那一步回填——那一刻才是货真的离开仓库。
     *
     * <p>旧流程写在「已发货」那一格，而那一格只表示装了箱在等承运商，货还在仓库里；
     * 新流程把它挪到终态，和 {@link #actualShipTime} 一起落，三列口径才一致。</p>
     */
    private String shipUser;
    private Date shipTime;

    /**
     * 备货照片，逗号分隔的 URL 列表。
     *
     * <p>格式跟着前端 ImageUpload 组件的 v-model 约定走（多张用逗号拼），与资金流水的
     * {@code attachment} 一致，不另起一张附件表：一张批次的照片就几张，拆表换来的是
     * 每次查批次都多一次 join。</p>
     *
     * <p><b>备货照片和产品照片不分类别</b>，一个上传框、一列存完。业务上这两个词指的
     * 是同一件事——「货备成什么样」，硬拆两列只会逼库管员纠结某张照片该传进哪一栏。</p>
     */
    private String pickPhotos;
    /** 备货完成的经手人（库管员）与时间。 */
    private String pickUser;
    private Date pickTime;
    /** 录入快递单号的经手人（业务员）与时间。 */
    private String logisticsUser;
    private Date logisticsTime;

    /**
     * 最近一次被仓库驳回的经手人、时间与原因。
     *
     * <p>驳回把批次退回「待确认」，业务员得知道是为什么退的才改得动，所以原因必填、
     * 单独落列——挤进 {@link #remark} 会把业务员自己写的备注冲掉。</p>
     *
     * <p>三列在下一次驳回时整体覆盖，<b>只留最近一次</b>：完整的驳回历史在
     * {@code sys_oper_log} 里，批次上要的只是「这张单现在为什么回来了」。
     * 批次重新走完流程后这三列不清空，留作痕迹。</p>
     */
    private String rejectUser;
    private Date rejectTime;
    private String rejectReason;

    private Date updateTime;
    private String remark;

    private List<ShipBatchItem> items = new ArrayList<>();

    /** 本批次发货生成的库存出库单号（CK 开头），发货后回填，不落库，由明细行带出。 */
    private String outboundDocCode;

    /** 本批次所在的月表后缀，不落库 */
    private String tableSuffix;

    /**
     * 查询参数：只看仓库该看到的批次，不落库。
     *
     * <p>仓库发货管理传 true，列表就只返回「已提交仓库」及其之后的状态——
     * 「待确认」是业务员自己还在改的排产计划，没提交给仓库之前仓库不该看见，
     * 「已取消」的批次也只可能在提交之前被取消。见 ShipBatchMapper.xml 的 selectList。</p>
     */
    private Boolean warehouseView;

    /**
     * 查询用：把批次限定在这个创建人名下。<b>不是数据库列</b>。
     *
     * <p>横向权限——没有 {@code order:shipbatch:all} 的人只看得到自己建的批次。
     * 一律由服务层 {@code selectBatchList} 说了算，前端传什么都会被覆盖。</p>
     */
    @JsonIgnore
    private String ownerScope;

    /**
     * 展示用：来源销售订单的币种（ISO 4217），读单据时从订单带出来，不落库。
     *
     * <p>注意 {@link #freightAmount} 不是这个币种：那是本批次<b>付给承运商</b>的运费，
     * 与订单卖给客户用什么币种无关，页面上按本位币显示。带出订单币种只是让操作人
     * 知道这张批次服务的是哪种币的订单。</p>
     */
    private String orderCurrency;

    /**
     * 展示用：来源销售订单所在的月表后缀，读单据时从订单带出来，不落库。
     *
     * <p><b>不要和 {@link #tableSuffix} 混用。</b>那个是<b>批次自己</b>按计划发货日期落的月，
     * 订单按 createtime 落月，两者经常不是同一个月——月底下单、次月发货是常态。</p>
     *
     * <p>改单时前端要按订单所在月份去查可发货订单才能拿到各行的可发量；没有这个字段
     * 就只能猜当前月，一猜错整张批次的可发量全变成 0，界面上表现为数量只能减不能加。</p>
     */
    private String orderTableSuffix;

    /**
     * 来源订单的国内收货方式（取值见 {@code SalesOrder.RECEIVE_TYPE_*}），读单张批次时
     * 从订单带出来，不落库。
     *
     * <p>发货那一步要用它决定承运商与物流单号是不是必填：「上门提货」是客户自己开车来仓库拉，
     * 既没有承运商也没有面单，硬要求只会逼人往框里敲「无」——假数据比空值更糟，
     * 填过之后就分不清「本来就没有」和「忘了填」。其余两种收货方式都得有。</p>
     *
     * <p>只在详情接口里带，列表不带：列表一屏几十行，每行回查一次订单就是 N+1，
     * 理由同 {@link #orderCurrency}。</p>
     */
    private String orderReceiveType;

    /** 展示用：创建人昵称 */
    private String createUserName;
    /** 展示用：本批次商品总数量，查询时聚合 */
    private BigDecimal totalQty;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShipCode() { return shipCode; }
    public void setShipCode(String shipCode) { this.shipCode = shipCode; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Date getShipDate() { return shipDate; }
    public void setShipDate(Date shipDate) { this.shipDate = shipDate; }

    public Date getActualShipTime() { return actualShipTime; }
    public void setActualShipTime(Date actualShipTime) { this.actualShipTime = actualShipTime; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }

    public BigDecimal getFreightAmount() { return freightAmount; }
    public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }

    public BigDecimal getFreightPaid() { return freightPaid; }
    public void setFreightPaid(BigDecimal freightPaid) { this.freightPaid = freightPaid; }

    public String getFreightPayStatus() { return freightPayStatus; }
    public void setFreightPayStatus(String freightPayStatus) { this.freightPayStatus = freightPayStatus; }

    public String getPayUser() { return payUser; }
    public void setPayUser(String payUser) { this.payUser = payUser; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getConfirmUser() { return confirmUser; }
    public void setConfirmUser(String confirmUser) { this.confirmUser = confirmUser; }

    public Date getConfirmTime() { return confirmTime; }
    public void setConfirmTime(Date confirmTime) { this.confirmTime = confirmTime; }

    public String getShipUser() { return shipUser; }
    public void setShipUser(String shipUser) { this.shipUser = shipUser; }

    public Date getShipTime() { return shipTime; }
    public void setShipTime(Date shipTime) { this.shipTime = shipTime; }

    public String getPickPhotos() { return pickPhotos; }
    public void setPickPhotos(String pickPhotos) { this.pickPhotos = pickPhotos; }

    public String getPickUser() { return pickUser; }
    public void setPickUser(String pickUser) { this.pickUser = pickUser; }

    public Date getPickTime() { return pickTime; }
    public void setPickTime(Date pickTime) { this.pickTime = pickTime; }

    public String getLogisticsUser() { return logisticsUser; }
    public void setLogisticsUser(String logisticsUser) { this.logisticsUser = logisticsUser; }

    public Date getLogisticsTime() { return logisticsTime; }
    public void setLogisticsTime(Date logisticsTime) { this.logisticsTime = logisticsTime; }

    public String getRejectUser() { return rejectUser; }
    public void setRejectUser(String rejectUser) { this.rejectUser = rejectUser; }

    public Date getRejectTime() { return rejectTime; }
    public void setRejectTime(Date rejectTime) { this.rejectTime = rejectTime; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<ShipBatchItem> getItems() { return items; }
    public void setItems(List<ShipBatchItem> items) { this.items = items; }

    public String getOutboundDocCode() { return outboundDocCode; }
    public void setOutboundDocCode(String outboundDocCode) { this.outboundDocCode = outboundDocCode; }

    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }

    public String getOwnerScope() { return ownerScope; }
    public void setOwnerScope(String ownerScope) { this.ownerScope = ownerScope; }

    public Boolean getWarehouseView() { return warehouseView; }
    public void setWarehouseView(Boolean warehouseView) { this.warehouseView = warehouseView; }

    public String getOrderCurrency() { return orderCurrency; }
    public void setOrderCurrency(String orderCurrency) { this.orderCurrency = orderCurrency; }

    public String getOrderTableSuffix() { return orderTableSuffix; }
    public void setOrderTableSuffix(String orderTableSuffix) { this.orderTableSuffix = orderTableSuffix; }

    public String getOrderReceiveType() { return orderReceiveType; }
    public void setOrderReceiveType(String orderReceiveType) { this.orderReceiveType = orderReceiveType; }

    public String getCreateUserName() { return createUserName; }
    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }

    public BigDecimal getTotalQty() { return totalQty; }
    public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }
}

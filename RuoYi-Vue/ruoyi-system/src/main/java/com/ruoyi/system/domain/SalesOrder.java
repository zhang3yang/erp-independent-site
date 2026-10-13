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
 * 销售订单主表 u_o_order_{yyyyMM}。
 *
 * <p>类名带 Sales 前缀是为了跟采购的 {@link PurchaseOrder} 区分开——两边都叫「订单」，
 * 光看 Order 无法判断是买进还是卖出，而 {@code paidamount} 这类字段在两边方向正好相反。</p>
 *
 * <p>落表月份取 {@code createtime} 所在月，建单后不再搬表。</p>
 */
public class SalesOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 履约状态：草稿。可改可删。 */
    public static final String STATE_DRAFT = "草稿";
    /**
     * 履约状态：待审核。已提交等审核。
     *
     * <p>整条链一件货都不占：库存到发货那一刻才由出库单锁定扣减。</p>
     */
    public static final String STATE_PENDING = "待审核";
    /** 履约状态：已驳回。审核不通过，可改单后重新提交，改单期间同样不占库存。 */
    public static final String STATE_REJECTED = "已驳回";
    /**
     * 履约状态：已备货。审核通过，且每条明细的货都有着落，可以直接去建发货批次。
     *
     * <p>「有着落」分两路判：定制件看它的<b>专属批次</b>够不够，普通件看仓库可用库存够不够。
     * 见 {@code SalesOrderServiceImpl.isAwaitingSupply}。</p>
     *
     * <p>注意这只是审核那一刻的快照，不是承诺——订单不占库存，那批货随时可能被
     * 别的单先发走。真正能不能发要到出库单锁定扣减时才定。</p>
     */
    public static final String STATE_READY = "已备货";
    /** 履约状态：备货中。有明细的货还没着落：定制件的专属批次没备齐，或者现货不够。 */
    public static final String STATE_PREPARING = "备货中";
    /** 履约状态：发货中。已发出一部分，未发完。 */
    public static final String STATE_SHIPPING = "发货中";
    /** 履约状态：已完成。全部发完。 */
    public static final String STATE_FINISHED = "已完成";
    /** 履约状态：已取消。一件未发就作废。 */
    public static final String STATE_CANCELLED = "已取消";
    /**
     * 履约状态：已关闭。发了一部分但剩下的不再发了，人工终止，必须填原因。
     *
     * <p>与已取消的区别是<b>有没有发出去过货</b>：一件没发用取消，发过就只能关闭——
     * 已发的部分是既成事实，要退得走销售退货。</p>
     */
    public static final String STATE_CLOSED = "已关闭";

    /**
     * 还能排发货批次、还能发货的三个履约状态。
     *
     * <p>用白名单而不是「排除草稿和已取消」：待审核与已驳回还没过审，替它们发货等于
     * 绕过审核；已关闭是人工终止，剩余部分不再发；已完成已经发满。
     * 建批次、确认、备货、发货四处都按这张表判断，缺一处就能从那个口子把货发出去。</p>
     *
     * <p>备货中也在里面：它只说明「审核时看着货还没凑齐」，不是禁令。中途有货进来
     * 就能发，能不能发以出库单锁定为准。</p>
     */
    private static final String[] SHIPPABLE_STATES = { STATE_READY, STATE_PREPARING, STATE_SHIPPING };

    /** 订单是否处在「已过审、还能发货」的区间。 */
    public static boolean isShippableState(String state)
    {
        for (String allowed : SHIPPABLE_STATES)
        {
            if (allowed.equals(state)) return true;
        }
        return false;
    }

    /**
     * 国内收货方式：货代地址。货交给货代，由货代出口。
     *
     * <p>只有这一种方式谈得上唛头（{@link #markNeeded}）——货代要靠唛头认箱。</p>
     */
    public static final String RECEIVE_TYPE_FORWARDER = "货代地址";
    /** 国内收货方式：客户地址。发到客户自己在国内的地址。 */
    public static final String RECEIVE_TYPE_BUYER = "客户地址";
    /**
     * 国内收货方式：上门提货。客户自己来仓库拉。
     *
     * <p>这一种<b>不要求收件地址</b>：货不出仓，填地址没有意义。
     * 见 {@code SalesOrderServiceImpl.validateHeader}。</p>
     */
    public static final String RECEIVE_TYPE_PICKUP = "上门提货";

    private static final String[] RECEIVE_TYPES =
            { RECEIVE_TYPE_FORWARDER, RECEIVE_TYPE_BUYER, RECEIVE_TYPE_PICKUP };

    /** 是不是三种合法的国内收货方式之一。null 和空串都算不是。 */
    public static boolean isValidReceiveType(String type)
    {
        for (String allowed : RECEIVE_TYPES)
        {
            if (allowed.equals(type)) return true;
        }
        return false;
    }

    private Long id;
    private String shopCode;
    private String orderCode;

    // ---- 订单基础信息 ----
    private String platCode;
    private String platName;
    private String shopStoreCode;
    private String shopStoreName;
    /**
     * 下单人：<b>建单那一刻的登录账号</b>，见 {@code SalesOrderServiceImpl.createOrder}。
     * 不是客户表上那个「归属业务员」，那个跟着客户走、会随客户转手变。
     */
    private String staffCode;

    /**
     * 下单人姓名。<b>读的时候现查 sys_user.nick_name 填进来</b>，不是库里 staffname 那一列
     * ——那列是建表时留的冗余位，历来没写过值，写了也会在账号改名后漂移。
     *
     * <p>查询时这个字段是「按业务员名称搜」的入口，服务层会把它换成 {@link #staffCodes}。</p>
     */
    private String staffName;

    /**
     * 查询用：把归属业务员限定在这一批登录账号里。<b>不是数据库列</b>，也不出现在接口返回里。
     *
     * <p>按「业务员名称」搜索时，服务层先拿姓名去 sys_user 反查出一批账号填进来，再用它去
     * 月表里筛（{@code idx_order_shop_staff} 正好覆盖）。这个字段一律由服务层说了算，
     * 前端传什么都会被覆盖。</p>
     *
     * <p>为什么不直接 join sys_user 按姓名筛：{@code SalesOrderMapper.xml} 的
     * {@code columns} 片段被 7 条语句共用，列名都没带表前缀，而 u_o_order 和 sys_user
     * <b>都有 remark 列</b>，一 join 就是 ambiguous column，得把 7 条语句全改成带别名。
     * 换成两步查，代价只是多一次 sys_user 查询。</p>
     */
    @JsonIgnore
    private List<String> staffCodes;
    private String buyerShop;
    private String buyerUser;

    // ---- 客户快照 ----
    // 建单时从 u_m_buyer 拷进来，之后不再跟客户档案联动：客户改名换头像不能倒着改历史单。
    // 与上面的 buyerShop / buyerUser 分工：那两个留给没建档的散客手填，
    // 这几个只在业务员选了客户档案时才有值，互不覆盖。
    /** 客户编码，关联 sdsshop.u_m_buyer.buyercode */
    private String buyerCode;
    /** 建单当时的客户名称（简称优先，见 Buyer#resolveDisplayName） */
    private String buyerName;
    /** 建单当时的客户头像 */
    private String buyerImage;
    /** 建单当时的客户店铺图 */
    private String shopImage;
    /** 二级客户编码，关联 sdsshop.u_m_buyer2.buyer2code；必须挂在 buyerCode 名下 */
    private String buyer2Code;
    /** 建单当时的二级客户名称 */
    private String buyer2Name;
    /** 建单当时的二级客户联系人 */
    private String buyer2User;
    /** 建单当时的二级客户头像 */
    private String buyer2Image;

    // ---- 出口信息 ----
    // 客户在国外，下面那套国内结构化收件地址装不下。两段地址各管一头，别混用：
    // 这里是货最终要到的地方，receive* 是国内这一程发到哪。
    /** 客户国家，ISO 3166-1 alpha-2 两位代码，取值见字典 {@code order_country} */
    private String buyerCountry;
    /**
     * 客户地址（国外）。
     *
     * <p>整段自由文本，不结构化：各国的行政区划层级对不上，硬拆成省市区只会拆错。</p>
     */
    private String buyerAddress;

    // ---- 平台信息 ----
    private String platOrderId;
    private String platBuyerId;
    private String platOrderUrl;
    private Date platOrderTime;
    private String platOrderLeave;

    // ---- 金额信息 ----
    @PriceField(PriceScope.SALE)
    private BigDecimal goodsAmount;
    /** 运费金额。方向：向客户收取，与发货批次的同名字段（发货实付）相反。 */
    @PriceField(PriceScope.SALE)
    private BigDecimal freightAmount;
    @PriceField(PriceScope.SALE)
    private BigDecimal totalAmount;
    /** 已收到金额。方向：收进来，与采购 {@code u_p_purchase.paidamount}（付出去）相反。 */
    @PriceField(PriceScope.SALE)
    private BigDecimal paidAmount;
    private String currency;

    // ---- 状态信息 ----
    /** 履约状态，取值见本类 STATE_* 常量。支付状态在 {@link #payStatus}。 */
    private String orderState;
    /** 支付状态：未支付/部分支付/已支付/超额支付，由 paidAmount 与 totalAmount 派生。 */
    private String payStatus;
    /**
     * 回款状态：未收款/部分收款/已收款/超额收款。
     *
     * <p>与 {@link #payStatus} 同源（都由 paidAmount 派生），留两列是因为两边各有契约：
     * 订单设计文档要 payStatus 与采购的 paystatus 对齐，V2.0 §5.4 流程2 要求财务回款
     * 完成后回写 receivestatus。同源派生避免双写漂移。</p>
     */
    private String receiveStatus;
    /** 发货比例（%）＝Σ已发货数量 / Σ订单数量 × 100 */
    private BigDecimal sendPercent;

    // ---- 收货信息 ----
    private String receivePerson;
    private String receiveAddress;
    private String receiveTelephone;
    private String receiveZipCode;
    /** 国内收货方式，取值见本类 RECEIVE_TYPE_* 常量 */
    private String receiveType;
    /**
     * 要不要唛头。
     *
     * <p>只有走货代（{@link #RECEIVE_TYPE_FORWARDER}）才谈得上，换成别的收货方式时
     * 服务层会把它和 {@link #markFile} 一起清掉，免得留下一个「不走货代却要唛头」的单。</p>
     */
    private Boolean markNeeded;
    /** 唛头文件的相对路径，{@link #markNeeded} 为真时才有值 */
    private String markFile;

    // ---- 时间信息 ----
    private Date createTime;
    private Date payTime;
    private Date updateTime;

    // ---- 其他 ----
    private String goodsInfo;

    /**
     * 商品内容摘要，形如「可乐等3种」。
     *
     * <p>与上面的 {@code goodsInfo}（历史遗留的自由文本「商品详情」）不是一回事：
     * 这一列由明细整体重算（{@link com.ruoyi.system.utils.GoodsSummaryBuilder}），
     * 只给列表页看，不参与任何计算。</p>
     */
    private String goodsSummary;
    private String remark;

    /** 订单资料：与客户来回确认、给生产做参考的文档，逗号分隔的相对路径。选填 */
    private String orderFiles;

    /**
     * 商品描述清单落库用的 JSON 文本，对应 {@code descitems} 列。
     *
     * <p>不出现在接口里：接口进出一律走 {@link #descItems}，两边同时给出去只会让
     * 前端不知道该信哪一个。服务层负责两者之间的转换。</p>
     */
    @JsonIgnore
    private String descItemsJson;

    /**
     * 商品描述清单。销售自由填写的名称/数量/单价，只作记录，
     * <b>不参与货款合计</b>——货款只按 {@link #items}（货品明细）算。
     */
    private List<SalesOrderDescItem> descItems = new ArrayList<>();

    /** 驳回原因，审核不通过时必填。改单重新提交后保留，作为这一轮改了什么的依据。 */
    private String rejectReason;
    /** 关闭原因，强制关闭时必填。 */
    private String closeReason;

    /** 明细行，不落主表 */
    private List<SalesOrderItem> items = new ArrayList<>();

    /**
     * 本单所在的月表后缀，不落库。跨月定位后回填，前端改单时原样回传，
     * 避免服务端再扫一遍所有月表。
     */
    private String tableSuffix;

    /**
     * 退款窗口截止时刻，不落库。只有「可退款订单」下拉返回的行才带值，
     * 供前端显示这张单还剩多久能退。口径见 RefundWindowResolver。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date refundDeadline;

    /** 查询用：创建时间范围起点 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginTime;
    /** 查询用：创建时间范围终点 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getPlatCode() { return platCode; }
    public void setPlatCode(String platCode) { this.platCode = platCode; }

    public String getPlatName() { return platName; }
    public void setPlatName(String platName) { this.platName = platName; }

    public String getShopStoreCode() { return shopStoreCode; }
    public void setShopStoreCode(String shopStoreCode) { this.shopStoreCode = shopStoreCode; }

    public String getShopStoreName() { return shopStoreName; }
    public void setShopStoreName(String shopStoreName) { this.shopStoreName = shopStoreName; }

    public String getStaffCode() { return staffCode; }
    public void setStaffCode(String staffCode) { this.staffCode = staffCode; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public List<String> getStaffCodes() { return staffCodes; }
    public void setStaffCodes(List<String> staffCodes) { this.staffCodes = staffCodes; }

    public String getBuyerShop() { return buyerShop; }
    public void setBuyerShop(String buyerShop) { this.buyerShop = buyerShop; }

    public String getBuyerUser() { return buyerUser; }
    public void setBuyerUser(String buyerUser) { this.buyerUser = buyerUser; }

    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBuyerImage() { return buyerImage; }
    public void setBuyerImage(String buyerImage) { this.buyerImage = buyerImage; }

    public String getShopImage() { return shopImage; }
    public void setShopImage(String shopImage) { this.shopImage = shopImage; }

    public String getBuyer2Code() { return buyer2Code; }
    public void setBuyer2Code(String buyer2Code) { this.buyer2Code = buyer2Code; }

    public String getBuyer2Name() { return buyer2Name; }
    public void setBuyer2Name(String buyer2Name) { this.buyer2Name = buyer2Name; }

    public String getBuyer2User() { return buyer2User; }
    public void setBuyer2User(String buyer2User) { this.buyer2User = buyer2User; }

    public String getBuyer2Image() { return buyer2Image; }
    public void setBuyer2Image(String buyer2Image) { this.buyer2Image = buyer2Image; }

    public String getBuyerCountry() { return buyerCountry; }
    public void setBuyerCountry(String buyerCountry) { this.buyerCountry = buyerCountry; }

    public String getBuyerAddress() { return buyerAddress; }
    public void setBuyerAddress(String buyerAddress) { this.buyerAddress = buyerAddress; }

    public String getPlatOrderId() { return platOrderId; }
    public void setPlatOrderId(String platOrderId) { this.platOrderId = platOrderId; }

    public String getPlatBuyerId() { return platBuyerId; }
    public void setPlatBuyerId(String platBuyerId) { this.platBuyerId = platBuyerId; }

    public String getPlatOrderUrl() { return platOrderUrl; }
    public void setPlatOrderUrl(String platOrderUrl) { this.platOrderUrl = platOrderUrl; }

    public Date getPlatOrderTime() { return platOrderTime; }
    public void setPlatOrderTime(Date platOrderTime) { this.platOrderTime = platOrderTime; }

    public String getPlatOrderLeave() { return platOrderLeave; }
    public void setPlatOrderLeave(String platOrderLeave) { this.platOrderLeave = platOrderLeave; }

    public BigDecimal getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(BigDecimal goodsAmount) { this.goodsAmount = goodsAmount; }

    public BigDecimal getFreightAmount() { return freightAmount; }
    public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getOrderState() { return orderState; }
    public void setOrderState(String orderState) { this.orderState = orderState; }

    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }

    public String getReceiveStatus() { return receiveStatus; }
    public void setReceiveStatus(String receiveStatus) { this.receiveStatus = receiveStatus; }

    public BigDecimal getSendPercent() { return sendPercent; }
    public void setSendPercent(BigDecimal sendPercent) { this.sendPercent = sendPercent; }

    public String getReceivePerson() { return receivePerson; }
    public void setReceivePerson(String receivePerson) { this.receivePerson = receivePerson; }

    public String getReceiveAddress() { return receiveAddress; }
    public void setReceiveAddress(String receiveAddress) { this.receiveAddress = receiveAddress; }

    public String getReceiveTelephone() { return receiveTelephone; }
    public void setReceiveTelephone(String receiveTelephone) { this.receiveTelephone = receiveTelephone; }

    public String getReceiveZipCode() { return receiveZipCode; }
    public void setReceiveZipCode(String receiveZipCode) { this.receiveZipCode = receiveZipCode; }

    public String getReceiveType() { return receiveType; }
    public void setReceiveType(String receiveType) { this.receiveType = receiveType; }

    public Boolean getMarkNeeded() { return markNeeded; }
    public void setMarkNeeded(Boolean markNeeded) { this.markNeeded = markNeeded; }

    public String getMarkFile() { return markFile; }
    public void setMarkFile(String markFile) { this.markFile = markFile; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getGoodsInfo() { return goodsInfo; }
    public void setGoodsInfo(String goodsInfo) { this.goodsInfo = goodsInfo; }

    public String getGoodsSummary() { return goodsSummary; }
    public void setGoodsSummary(String goodsSummary) { this.goodsSummary = goodsSummary; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getOrderFiles() { return orderFiles; }
    public void setOrderFiles(String orderFiles) { this.orderFiles = orderFiles; }

    public String getDescItemsJson() { return descItemsJson; }
    public void setDescItemsJson(String descItemsJson) { this.descItemsJson = descItemsJson; }

    public List<SalesOrderDescItem> getDescItems() { return descItems; }
    public void setDescItems(List<SalesOrderDescItem> descItems) { this.descItems = descItems; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

    public List<SalesOrderItem> getItems() { return items; }
    public void setItems(List<SalesOrderItem> items) { this.items = items; }

    public String getTableSuffix() { return tableSuffix; }
    public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }

    public Date getRefundDeadline() { return refundDeadline; }
    public void setRefundDeadline(Date refundDeadline) { this.refundDeadline = refundDeadline; }

    public Date getBeginTime() { return beginTime; }
    public void setBeginTime(Date beginTime) { this.beginTime = beginTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
}

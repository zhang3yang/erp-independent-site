package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 站点订单。对应表 {@code u_s_order}。
 *
 * <p>买家在前台把询价清单填完提交，落进<b>本站的库</b>，由站点运营在管理台里跟进。</p>
 *
 * <h3>⚠ 和 ERP 的订单没有任何隶属关系</h3>
 * <p>docs/独立站与ERP的边界.md 要点 4。这个类里没有一个字段指向 ERP 的订单、客户或商品，
 * 也没有「同步状态」——一期的同步方式是<b>人工在 ERP 里录一张</b>（§8：不做，也不预埋接口）。
 * 想加一个 {@code erpOrderCode} 之前，先读那一节。</p>
 *
 * <h3>它不是「已付款的订单」</h3>
 * <p>站上不收款（前台 {@code station-site/app/lib/shop.ts} 的头注释讲了为什么）。
 * 一张单进来时是<b>待确认</b>：买家说了要什么、发到哪、倾向怎么运怎么付；
 * 价格、运费、条款由商家回一份报价之后才算数。所以：</p>
 * <ul>
 *   <li>{@link #subtotal} 是<b>按标价估的</b>，不含运费，也不含价格面议的行；</li>
 *   <li>{@link #inquireOnly} 是面议行数，&gt;0 时上面那个金额不是全部；</li>
 *   <li>{@link #shippingPref} / {@link #paymentPref} 是<b>倾向</b>，不是定下来的条款。</li>
 * </ul>
 *
 * <h3>时间轴为什么是四个时间列，不是一张事件表</h3>
 * <p>前台那边订单有一个 {@code events[]}，直觉是建 {@code u_s_orderevent}。没建：
 * 四个阶段是固定的，四个时间列就能完整表达，而一张事件表会立刻引出
 * 「要不要记操作人」「能不能回退」这些没有需求的问题。理由全文见建表脚本
 * {@code sql/station/migration/2026-09-04-order-and-buyer.sql} 的文件头。</p>
 */
public class SiteOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 状态词表。和前台 {@code shop.ts} 的 {@code OrderStatus} 一一对应：
     * submitted / confirmed / shipped / delivered / cancelled。
     *
     * <p>库里存中文：管理台只有中文界面，存英文的话它要为了显示再翻一遍；
     * 而前台是多语的，它本来就要按站点语言翻一遍，多翻一次不亏。</p>
     */
    public static final String STATUS_SUBMITTED = "待确认";
    public static final String STATUS_CONFIRMED = "已确认";
    public static final String STATUS_SHIPPED = "已发货";
    public static final String STATUS_FINISHED = "已完成";
    public static final String STATUS_CANCELLED = "已取消";

    /** 前台自助提交 */
    public static final String SOURCE_SITE = "site";
    /** 管理台手工录（买家发邮件来、电话来的单子） */
    public static final String SOURCE_CONSOLE = "console";

    private Long id;
    private String shopCode;
    private String siteCode;
    private String orderCode;

    private String status;
    private String source;
    /** 买家下单时浏览的语言（en/zh/ja…）。回执邮件按它写；老单为 null，按站点默认语言处理 */
    private String lang;

    private String currency;
    /** 有标价那部分的小计。⚠ 不含运费、不含面议行 */
    private BigDecimal subtotal;
    /** 价格面议的行数。>0 时 subtotal 不是这张单的全部金额 */
    private Integer inquireOnly;

    private String contactName;
    private String email;
    private String company;
    private String phone;

    private String addrLine1;
    private String addrLine2;
    private String city;
    private String region;
    private String postal;
    /** 国家。唯一必填的地址字段——估运费最少要它 */
    private String country;

    /** 运输倾向：sea/air/express/agent */
    private String shippingPref;
    /** 付款倾向：tt/lc/paypal/other */
    private String paymentPref;
    /** 买家补充说明 */
    private String buyerNote;
    /**
     * 引导式下单的选配快照（JSON 数组，见 {@code OrderWizardService.Resolved#snapshotJson}）。
     * 普通购物车下的单为 null。<b>只在详情查询里读</b>（mediumtext，列表不带）
     */
    private String wizardJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date shipTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date finishTime;

    private String buyerCode;
    private String clientIp;
    private String handleUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date handleTime;
    /** 内部备注，买家看不到 */
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /**
     * 订单行。
     *
     * <p>列表查询<b>不填</b>它（一次查一百张单、每张再查一次明细就是 N+1）；
     * 详情查询才填。前端拿到列表时这里是空数组，不是 null——
     * 空数组能直接 {@code v-for}，null 要先判一次。</p>
     */
    private List<SiteOrderItem> items = new ArrayList<>();

    /** 总件数。列表页显示「3 项 / 共 1500 米」用 */
    public int itemCount()
    {
        return items == null ? 0 : items.size();
    }

    /** 已取消的单不能再推进状态 */
    public boolean isCancelled()
    {
        return STATUS_CANCELLED.equals(status);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public Integer getInquireOnly() { return inquireOnly; }
    public void setInquireOnly(Integer inquireOnly) { this.inquireOnly = inquireOnly; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddrLine1() { return addrLine1; }
    public void setAddrLine1(String addrLine1) { this.addrLine1 = addrLine1; }

    public String getAddrLine2() { return addrLine2; }
    public void setAddrLine2(String addrLine2) { this.addrLine2 = addrLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getPostal() { return postal; }
    public void setPostal(String postal) { this.postal = postal; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getShippingPref() { return shippingPref; }
    public void setShippingPref(String shippingPref) { this.shippingPref = shippingPref; }

    public String getPaymentPref() { return paymentPref; }
    public void setPaymentPref(String paymentPref) { this.paymentPref = paymentPref; }

    public String getBuyerNote() { return buyerNote; }
    public void setBuyerNote(String buyerNote) { this.buyerNote = buyerNote; }

    public String getWizardJson() { return wizardJson; }
    public void setWizardJson(String wizardJson) { this.wizardJson = wizardJson; }

    public Date getConfirmTime() { return confirmTime; }
    public void setConfirmTime(Date confirmTime) { this.confirmTime = confirmTime; }

    public Date getShipTime() { return shipTime; }
    public void setShipTime(Date shipTime) { this.shipTime = shipTime; }

    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }

    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getHandleUser() { return handleUser; }
    public void setHandleUser(String handleUser) { this.handleUser = handleUser; }

    public Date getHandleTime() { return handleTime; }
    public void setHandleTime(Date handleTime) { this.handleTime = handleTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public List<SiteOrderItem> getItems() { return items; }
    public void setItems(List<SiteOrderItem> items)
    {
        this.items = items == null ? new ArrayList<>() : items;
    }
}

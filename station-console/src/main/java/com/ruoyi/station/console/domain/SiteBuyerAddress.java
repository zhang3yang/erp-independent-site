package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;

/**
 * 买家收货地址簿的一条。对应表 {@code u_s_buyeraddr}。
 *
 * <h3>⚠ 字段名和 {@link SiteOrder} 的目的地那一组一一对应</h3>
 * <p>{@code contactName / phone / addrLine1 / addrLine2 / city / region / postal / country}
 * ——地址簿选一条 → 填进结账表单 → 落进 {@code u_s_order} 的同名列。两边对齐，
 * 中间就不需要一层映射，也就没有「line1 抄成 addrline1」这类错。
 * 加字段时<b>同时看那边</b>，别让两张表漂开。</p>
 *
 * <h3>⚠ 它不是订单地址</h3>
 * <p>订单上的收货地是<b>下单当时的快照</b>，和这张表没有外键、也不 join。
 * 买家事后改地址簿，历史订单的收货地不能跟着变——那批货已经按旧地址发出去了。
 * 和 {@link SiteOrderItem} 存商品快照是同一条理由。</p>
 *
 * <h3>⚠ 对外用自增 id，所以每一条 SQL 都要带 buyercode</h3>
 * <p>这张表不发业务码（理由见建表脚本的文件头），于是 id 是连号可猜的。
 * 读、改、删、设默认——<b>每一条的 where 里都必须有 {@code buyercode}</b>，
 * 而那个值只从买家会话取，绝不从参数取。少一处就是「把 id 改个数字就能读到
 * 别人的收货地址和电话」。</p>
 */
public class SiteBuyerAddress implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 【遗留列】和站点码。
     *
     * <p>⚠ 两个都 {@code @JsonIgnore}：这个对象<b>只</b>下发给前台买家，而他既用不上
     * 商户码也用不上站点码。下发出去的字段，早晚会有人把它当参数传回来。</p>
     */
    @JsonIgnore
    private String shopCode;
    @JsonIgnore
    private String siteCode;

    /**
     * 属于哪个买家。
     *
     * <p>⚠ 下发给前台的 JSON 里<b>不带它</b>：买家自己的编码对他没有任何用处，
     * 而它是这张表的归属键——出现在前端就迟早会有人把它当成参数传回来，
     * 那正是这个接口最不能接受的事（见类注释第三条）。</p>
     */
    @JsonIgnore
    private String buyerCode;

    /** 买家给这条地址起的名字，如 Warehouse / Head office。选填 */
    private String label;

    /** 收件人。⚠ 和 {@code SiteOrder.contactName} 同名同宽 */
    private String contactName;
    /** 收件电话 / WhatsApp。选填——强制填会挡住只想问价的人 */
    private String phone;

    private String addrLine1;
    private String addrLine2;
    private String city;
    private String region;
    private String postal;
    /** 国家。唯一必填的地址字段，和 {@code u_s_order} 一致——估运费最少要它 */
    private String country;

    /** 是不是默认地址。⚠ 一人一条靠服务层的事务保证，不靠唯一索引 */
    private Boolean isDefault;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 默认地址没有？null 当成 false——历史行没有这一列的值 */
    @JsonIgnore
    public boolean isMarkedDefault()
    {
        return Boolean.TRUE.equals(isDefault);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

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

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}

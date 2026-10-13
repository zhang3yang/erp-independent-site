package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;

/**
 * 销售客户实体类。
 * 对应表：sdsshop.u_m_buyer
 *
 * <p>与 {@link InventoryPartner} 的分工：InventoryPartner 是采购侧的供应商/往来单位，
 * 面向应付账务；本类是销售侧的客户，面向订单。两张表互不迁移，
 * 客户日后发展成正式往来单位时用 {@link #partnerCode} 弱关联过去，不建外键。</p>
 *
 * <p>订单只存客户<b>快照</b>（见 {@code SalesOrder} 的 buyerCode/buyerName 等字段），
 * 历史订单一律不 join 本表：客户改名换头像不能倒着改历史单。</p>
 */
public class Buyer implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 客户类型：个人。一期只有这一种。 */
    public static final String TYPE_PERSONAL = "1";
    /** 客户类型：商家。二期开放，一期不写入。 */
    public static final String TYPE_MERCHANT = "2";

    /** 商家编码（租户列） */
    private String shopCode;

    /** 客户编码，KH + Base10 流水，业务主键 */
    @Excel(name = "客户编码")
    private String buyerCode;

    /** 客户头像 */
    private String imageUrl;

    /** 客户店铺图 */
    private String shopImage;

    /** 买家商家代码。二期商家客户用，一期恒 null。 */
    private String buyerShop;

    /** 买家用户代码，与 u_o_order_.buyeruser 同口径 */
    @Excel(name = "买家账号")
    private String buyerUser;

    /** 客户类型，取值见 TYPE_* 常量 */
    @Excel(name = "客户类型", readConverterExp = "1=个人,2=商家")
    private String buyerType;

    /** 客户简称。列表展示和订单快照优先取它。 */
    @Excel(name = "客户简称")
    private String buyerSimple;

    /** 客户全称 */
    @Excel(name = "客户全称")
    private String buyerFull;

    /** 归属业务员代码 */
    @Excel(name = "业务员代码")
    private String staffCode;

    /** 归属业务员名称（冗余） */
    @Excel(name = "业务员")
    private String staffName;

    /** 联系人 */
    @Excel(name = "联系人")
    private String contactPerson;

    /**
     * 手机号，商户内唯一。
     *
     * <p>没填时必须是 null 而不是空串：唯一索引允许多个 null，空串却只能有一条，
     * 存空串会让第二个没填手机号的客户直接撞唯一键。归一化在 BuyerServiceImpl 里做。</p>
     */
    @Excel(name = "手机号")
    private String mobile;

    /** 固定电话 */
    @Excel(name = "固定电话")
    private String telephone;

    /** 地址 */
    @Excel(name = "地址")
    private String address;

    /** 客户星级 0-5 */
    @Excel(name = "星级")
    private Integer starRating;

    /** 弱关联 u_m_partner.partnercode，客户转正式往来单位后回填，不建外键 */
    private String partnerCode;

    /** 二期客户账号用，绑定 sys_user.user_code；一期恒 null */
    private String sysUserCode;

    /** 启用标记：1-启用 0-停用 */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private Integer activeFlag;

    /** 备注 */
    @Excel(name = "备注")
    private String remark;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    @Excel(name = "创建时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ---- 非数据库字段 ----

    /**
     * 该客户的历史订单数。
     *
     * <p><b>只在查单个客户详情时统计，列表不带</b>：订单按月分表，数一个客户的订单要把所有
     * 月表都查一遍，列表一页 10 行就是 10×月表数 次查询，页面会被拖垮。</p>
     */
    private Integer orderCount;

    /**
     * 名下二级客户数。
     *
     * <p>列表查询用相关子查询带出来——u_m_buyer2 上有 (shop_code, buyercode) 索引，
     * 跟 orderCount 那种要扫月表的不是一回事，可以放心放在列表里。</p>
     */
    private Integer buyer2Count;

    /** 详情接口返回的二级客户明细，列表不带 */
    private java.util.List<Buyer2> children;

    /**
     * 订单快照用的客户名称：简称优先，简称为空退全称，都为空退联系人。
     *
     * <p>放在实体上而不是各处自己拼，是为了让「订单上显示的客户名」只有一种算法。</p>
     */
    public String resolveDisplayName()
    {
        if (buyerSimple != null && !buyerSimple.trim().isEmpty()) return buyerSimple.trim();
        if (buyerFull != null && !buyerFull.trim().isEmpty()) return buyerFull.trim();
        if (contactPerson != null && !contactPerson.trim().isEmpty()) return contactPerson.trim();
        return buyerCode;
    }

    public String getShopCode() { return shopCode; }

    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getBuyerCode() { return buyerCode; }

    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getImageUrl() { return imageUrl; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getShopImage() { return shopImage; }

    public void setShopImage(String shopImage) { this.shopImage = shopImage; }

    public String getBuyerShop() { return buyerShop; }

    public void setBuyerShop(String buyerShop)
    { this.buyerShop = buyerShop; }

    public String getBuyerUser()
    { return buyerUser; }

    public void setBuyerUser(String buyerUser) { this.buyerUser = buyerUser; }

    public String getBuyerType() { return buyerType; }

    public void setBuyerType(String buyerType) { this.buyerType = buyerType; }

    public String getBuyerSimple() { return buyerSimple; }

    public void setBuyerSimple(String buyerSimple) { this.buyerSimple = buyerSimple; }

    public String getBuyerFull() { return buyerFull; }

    public void setBuyerFull(String buyerFull) { this.buyerFull = buyerFull; }

    public String getStaffCode() { return staffCode; }

    public void setStaffCode(String staffCode) { this.staffCode = staffCode; }

    public String getStaffName() { return staffName; }

    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getContactPerson() { return contactPerson; }

    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getMobile() { return mobile; }

    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getTelephone() { return telephone; }

    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getAddress() { return address; }

    public void setAddress(String address) { this.address = address; }

    public Integer getStarRating() { return starRating; }

    public void setStarRating(Integer starRating) { this.starRating = starRating; }

    public String getPartnerCode() { return partnerCode; }

    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }

    public String getSysUserCode() { return sysUserCode; }

    public void setSysUserCode(String sysUserCode) { this.sysUserCode = sysUserCode; }

    public Integer getActiveFlag() { return activeFlag; }

    public void setActiveFlag(Integer activeFlag) { this.activeFlag = activeFlag; }

    public String getRemark() { return remark; }

    public void setRemark(String remark) { this.remark = remark; }

    public String getCreateUser() { return createUser; }

    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }

    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }

    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }

    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Integer getOrderCount() { return orderCount; }

    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public Integer getBuyer2Count() { return buyer2Count; }

    public void setBuyer2Count(Integer buyer2Count) { this.buyer2Count = buyer2Count; }

    public java.util.List<Buyer2> getChildren() { return children; }

    public void setChildren(java.util.List<Buyer2> children) { this.children = children; }
}

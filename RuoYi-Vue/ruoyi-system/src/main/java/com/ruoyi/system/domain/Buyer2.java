package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 二级客户实体类。
 * 对应表：sdsshop.u_m_buyer2
 *
 * <p>一个主客户（{@link Buyer}）挂多个二级客户：连锁客户的各个门店、经销商的下线、
 * 同一家公司的多个收货联系人。订单上主客户和二级客户各存一份快照。</p>
 *
 * <p>不建外键，级联删除在 BuyerServiceImpl 里跟主客户同事务处理，
 * 与项目其它主子表（平台/店铺、订单/明细）一致。</p>
 */
public class Buyer2 implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码（租户列） */
    private String shopCode;

    /** 二级客户编码，KHMX + Base10 流水，业务主键 */
    private String buyer2Code;

    /** 所属主客户编码 */
    private String buyerCode;

    /** 二级客户头像 */
    private String imageUrl;

    /** 二级客户名称，订单快照取它 */
    private String buyer2Name;

    /** 二级客户联系人 */
    private String buyer2User;

    /** 联系电话。子表不做唯一约束：同一主客户下多个门店留同一个店长电话是正常的。 */
    private String mobile;

    /** 地址 */
    private String address;

    /** 启用标记：1-启用 0-停用 */
    private Integer activeFlag;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 订单快照用的名称：名称为空退联系人，再退编码。与 {@link Buyer#resolveDisplayName} 同一套思路。 */
    public String resolveDisplayName()
    {
        if (buyer2Name != null && !buyer2Name.trim().isEmpty()) return buyer2Name.trim();
        if (buyer2User != null && !buyer2User.trim().isEmpty()) return buyer2User.trim();
        return buyer2Code;
    }

    public String getShopCode() { return shopCode; }

    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getBuyer2Code() { return buyer2Code; }

    public void setBuyer2Code(String buyer2Code) { this.buyer2Code = buyer2Code; }

    public String getBuyerCode() { return buyerCode; }

    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getImageUrl() { return imageUrl; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getBuyer2Name() { return buyer2Name; }

    public void setBuyer2Name(String buyer2Name) { this.buyer2Name = buyer2Name; }

    public String getBuyer2User() { return buyer2User; }

    public void setBuyer2User(String buyer2User) { this.buyer2User = buyer2User; }

    public String getMobile() { return mobile; }

    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getAddress() { return address; }

    public void setAddress(String address) { this.address = address; }

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
}

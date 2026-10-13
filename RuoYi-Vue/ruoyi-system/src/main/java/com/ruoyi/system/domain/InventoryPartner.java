package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 供应商/客户实体类
 * 对应表：sdsshop.u_m_partner
 */
public class InventoryPartner implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 单位代码 */
    private String partnerCode;

    /** 单位名称 */
    private String partnerName;

    /** 类型：supplier/customer/department */
    private String partnerType;

    /** 地址 */
    private String address;

    /** 联系人 */
    private String contactPerson;

    /** 手机号 */
    private String mobile;

    /** 电话 */
    private String telephone;

    /** 是否启用 */
    private Integer activeFlag;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    /** 更新时间 */
    private Date updateTime;

    /**
     * 查询用：按单位代码或名称模糊匹配，不是表上的列。
     *
     * <p>供应商选择弹窗要能同时按编码和名称搜，而 partnerCode 是精确匹配、
     * partnerName 只匹配名称，两个条件是 AND 关系，凑不出「编码或名称」。</p>
     */
    private String keyword;

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getPartnerCode()
    {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode)
    {
        this.partnerCode = partnerCode;
    }

    public String getPartnerName()
    {
        return partnerName;
    }

    public void setPartnerName(String partnerName)
    {
        this.partnerName = partnerName;
    }

    public String getPartnerType()
    {
        return partnerType;
    }

    public void setPartnerType(String partnerType)
    {
        this.partnerType = partnerType;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getContactPerson()
    {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson)
    {
        this.contactPerson = contactPerson;
    }

    public String getMobile()
    {
        return mobile;
    }

    public void setMobile(String mobile)
    {
        this.mobile = mobile;
    }

    public String getTelephone()
    {
        return telephone;
    }

    public void setTelephone(String telephone)
    {
        this.telephone = telephone;
    }

    public Integer getActiveFlag()
    {
        return activeFlag;
    }

    public void setActiveFlag(Integer activeFlag)
    {
        this.activeFlag = activeFlag;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
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

    public String getUpdateUser()
    {
        return updateUser;
    }

    public void setUpdateUser(String updateUser)
    {
        this.updateUser = updateUser;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }
}

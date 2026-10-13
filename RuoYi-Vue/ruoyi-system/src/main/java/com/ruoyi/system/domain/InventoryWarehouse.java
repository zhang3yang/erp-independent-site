package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 仓库实体类
 * 对应表：sdsshop.u_m_warehouse
 */
public class InventoryWarehouse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商家编码 */
    private String shopCode;

    /** 仓库代码 */
    private String wareCode;

    /** 仓库名称 */
    private String wareName;

    /** 上级仓库代码 */
    private String parentCode;

    /** 仓库类型：实体仓/虚拟仓 */
    private String wareType;

    /** 仓库地址 */
    private String address;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 是否启用 */
    private Integer activeFlag;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
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

    public String getParentCode()
    {
        return parentCode;
    }

    public void setParentCode(String parentCode)
    {
        this.parentCode = parentCode;
    }

    public String getWareType()
    {
        return wareType;
    }

    public void setWareType(String wareType)
    {
        this.wareType = wareType;
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

    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
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

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}

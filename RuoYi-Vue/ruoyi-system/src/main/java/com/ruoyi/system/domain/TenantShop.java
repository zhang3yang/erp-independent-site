package com.ruoyi.system.domain;

import java.io.Serializable;

/**
 * 当前账号可进入的商家信息。
 */
public class TenantShop implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String shopCode;
    private String shopName;
    private String province;
    private String city;
    private String district;
    private String address;
    private String telephone;
    private String description;
    private String imageUrl;
    /**
     * 这个商家专用的独立站管理台地址（https://...）。
     *
     * <p>留空时用全局 {@code ruoyi.station-console.base-url}；站点档案上的「管理台地址」
     * 优先级比这里更高。只允许 http/https——这个值会被前端赋给 {@code window.location.href}。</p>
     */
    private String consoleUrl;
    private Integer status;
    private String memberUserCode;
    private Long deptId;
    private String jobNo;
    private String jobName;
    private String roleCodes;
    private String roleNames;

    public String getConsoleUrl()
    {
        return consoleUrl;
    }

    public void setConsoleUrl(String consoleUrl)
    {
        this.consoleUrl = consoleUrl;
    }

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getShopName()
    {
        return shopName;
    }

    public void setShopName(String shopName)
    {
        this.shopName = shopName;
    }

    public String getProvince()
    {
        return province;
    }

    public void setProvince(String province)
    {
        this.province = province;
    }

    public String getCity()
    {
        return city;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getDistrict()
    {
        return district;
    }

    public void setDistrict(String district)
    {
        this.district = district;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getTelephone()
    {
        return telephone;
    }

    public void setTelephone(String telephone)
    {
        this.telephone = telephone;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public String getMemberUserCode()
    {
        return memberUserCode;
    }

    public void setMemberUserCode(String memberUserCode)
    {
        this.memberUserCode = memberUserCode;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getJobNo()
    {
        return jobNo;
    }

    public void setJobNo(String jobNo)
    {
        this.jobNo = jobNo;
    }

    public String getJobName()
    {
        return jobName;
    }

    public void setJobName(String jobName)
    {
        this.jobName = jobName;
    }

    public String getRoleCodes()
    {
        return roleCodes;
    }

    public void setRoleCodes(String roleCodes)
    {
        this.roleCodes = roleCodes;
    }

    public String getRoleNames()
    {
        return roleNames;
    }

    public void setRoleNames(String roleNames)
    {
        this.roleNames = roleNames;
    }
}

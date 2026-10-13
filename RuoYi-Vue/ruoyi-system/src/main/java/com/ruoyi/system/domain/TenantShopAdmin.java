package com.ruoyi.system.domain;

import java.io.Serializable;

/**
 * Platform view of a shop and its immutable built-in administrator role.
 */
public class TenantShopAdmin implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String shopCode;
    private String shopName;
    private Integer shopStatus;
    private String roleCode;
    private String roleName;
    private String roleKey;
    private String roleStatus;
    private String[] funCodes;
    private String adminUserCode;
    private String adminUserName;
    private String adminNickName;
    private String adminPhonenumber;
    private String adminStatus;

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

    public Integer getShopStatus()
    {
        return shopStatus;
    }

    public void setShopStatus(Integer shopStatus)
    {
        this.shopStatus = shopStatus;
    }

    public String getRoleCode()
    {
        return roleCode;
    }

    public void setRoleCode(String roleCode)
    {
        this.roleCode = roleCode;
    }

    public String getRoleName()
    {
        return roleName;
    }

    public void setRoleName(String roleName)
    {
        this.roleName = roleName;
    }

    public String getRoleKey()
    {
        return roleKey;
    }

    public void setRoleKey(String roleKey)
    {
        this.roleKey = roleKey;
    }

    public String getRoleStatus()
    {
        return roleStatus;
    }

    public void setRoleStatus(String roleStatus)
    {
        this.roleStatus = roleStatus;
    }

    public String[] getFunCodes()
    {
        return funCodes;
    }

    public void setFunCodes(String[] funCodes)
    {
        this.funCodes = funCodes;
    }

    public String getAdminUserCode()
    {
        return adminUserCode;
    }

    public void setAdminUserCode(String adminUserCode)
    {
        this.adminUserCode = adminUserCode;
    }

    public String getAdminUserName()
    {
        return adminUserName;
    }

    public void setAdminUserName(String adminUserName)
    {
        this.adminUserName = adminUserName;
    }

    public String getAdminNickName()
    {
        return adminNickName;
    }

    public void setAdminNickName(String adminNickName)
    {
        this.adminNickName = adminNickName;
    }

    public String getAdminPhonenumber()
    {
        return adminPhonenumber;
    }

    public void setAdminPhonenumber(String adminPhonenumber)
    {
        this.adminPhonenumber = adminPhonenumber;
    }

    public String getAdminStatus()
    {
        return adminStatus;
    }

    public void setAdminStatus(String adminStatus)
    {
        this.adminStatus = adminStatus;
    }
}

package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.entity.SysRole;

/**
 * RuoYi role enriched with shop ownership metadata.
 */
public class TenantRole extends SysRole
{
    private static final long serialVersionUID = 1L;

    private String shopCode;
    private String roleType;
    private Boolean systemFlag;

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getRoleType()
    {
        return roleType;
    }

    public void setRoleType(String roleType)
    {
        this.roleType = roleType;
    }

    public Boolean getSystemFlag()
    {
        return systemFlag;
    }

    public void setSystemFlag(Boolean systemFlag)
    {
        this.systemFlag = systemFlag;
    }

    public boolean isSystemRole()
    {
        return Boolean.TRUE.equals(systemFlag);
    }
}

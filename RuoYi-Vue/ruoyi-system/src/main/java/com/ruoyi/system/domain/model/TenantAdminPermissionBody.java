package com.ruoyi.system.domain.model;

/**
 * Platform request for replacing one built-in shop administrator permission set.
 */
public class TenantAdminPermissionBody
{
    private String[] funCodes;

    public String[] getFunCodes()
    {
        return funCodes;
    }

    public void setFunCodes(String[] funCodes)
    {
        this.funCodes = funCodes;
    }
}

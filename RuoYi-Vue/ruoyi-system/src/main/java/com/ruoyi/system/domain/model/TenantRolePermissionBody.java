package com.ruoyi.system.domain.model;

import jakarta.validation.constraints.NotNull;

/**
 * Menu assignment payload for a tenant role.
 */
public class TenantRolePermissionBody
{
    @NotNull(message = "Function codes cannot be null")
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

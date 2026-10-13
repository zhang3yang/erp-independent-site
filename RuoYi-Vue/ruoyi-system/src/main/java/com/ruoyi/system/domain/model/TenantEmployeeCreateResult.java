package com.ruoyi.system.domain.model;

/** Result of creating a tenant employee membership. */
public class TenantEmployeeCreateResult
{
    private final int rows;
    private final String userCode;
    private final String userName;
    private final boolean accountCreated;

    public TenantEmployeeCreateResult(int rows, String userCode, String userName, boolean accountCreated)
    {
        this.rows = rows;
        this.userCode = userCode;
        this.userName = userName;
        this.accountCreated = accountCreated;
    }

    public int getRows()
    {
        return rows;
    }

    public String getUserCode()
    {
        return userCode;
    }

    public String getUserName()
    {
        return userName;
    }

    public boolean isAccountCreated()
    {
        return accountCreated;
    }
}

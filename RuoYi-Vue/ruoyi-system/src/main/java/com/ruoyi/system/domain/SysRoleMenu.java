package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 角色和菜单关联 sys_role_menu
 * 
 * @author ruoyi
 */
public class SysRoleMenu
{
    /** 角色ID */
    private String roleCode;
    
    /** 功能编码 */
    private String funCode;

    public String getRoleCode()
    {
        return roleCode;
    }

    public void setRoleCode(String roleCode)
    {
        this.roleCode = roleCode;
    }

    public String getFunCode()
    {
        return funCode;
    }

    public void setFunCode(String funCode)
    {
        this.funCode = funCode;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("roleCode", getRoleCode())
            .append("funCode", getFunCode())
            .toString();
    }
}

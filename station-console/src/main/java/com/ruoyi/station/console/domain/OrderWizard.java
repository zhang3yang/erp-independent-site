package com.ruoyi.station.console.domain;

import java.io.Serializable;

/**
 * 引导式下单的向导定义。对应表 {@code u_s_orderwizard}，<b>一个站最多一行</b>。
 *
 * <p>{@link #schemaJson} 是整份向导（分组 → 字段 → 选项），格式由
 * {@code OrderWizardService.validateSchema} 把关，设计见
 * docs/superpowers/specs/2026-09-28-order-wizard-design.md。</p>
 *
 * <p>表里没有这一行 = 向导从没配过，按「没开」处理，和 {@code enabled = 0} 一样。</p>
 */
public class OrderWizard implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String siteCode;
    /** 前台开关：1 开 / 0 关 */
    private Integer enabled;
    private String schemaJson;
    private String updateUser;
    private String updateTime;

    public boolean isOn()
    {
        return enabled != null && enabled == 1;
    }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}

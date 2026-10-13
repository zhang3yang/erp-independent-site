package com.ruoyi.system.domain.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 选择当前商家的请求参数。
 */
public class TenantSelectBody
{
    @NotBlank(message = "商家代码不能为空")
    private String shopCode;

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }
}

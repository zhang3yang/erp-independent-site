package com.ruoyi.common.core.domain.model;

/**
 * 手机验证码登录请求。
 */
public class SmsLoginBody
{
    private String phonenumber;
    private String code;

    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }
}

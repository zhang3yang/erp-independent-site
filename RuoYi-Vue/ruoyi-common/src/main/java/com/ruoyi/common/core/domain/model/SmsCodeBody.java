package com.ruoyi.common.core.domain.model;

/**
 * 手机验证码发送请求。
 */
public class SmsCodeBody
{
    private String phonenumber;
    private String platcode;

    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }

    public String getPlatcode()
    {
        return platcode;
    }

    public void setPlatcode(String platcode)
    {
        this.platcode = platcode;
    }
}

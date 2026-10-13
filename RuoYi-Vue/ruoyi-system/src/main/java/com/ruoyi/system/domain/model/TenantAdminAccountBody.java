package com.ruoyi.system.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Platform request for creating or maintaining a shop administrator account.
 */
public class TenantAdminAccountBody
{
    @Size(max = 20, message = "Login account cannot exceed 20 characters")
    private String userName;

    @NotBlank(message = "Administrator name is required")
    @Size(max = 30, message = "Administrator name cannot exceed 30 characters")
    private String nickName;

    @jakarta.validation.constraints.NotBlank(message = "Mobile number is required")
    @jakarta.validation.constraints.Pattern(regexp = "^1[3-9]\\d{9}$", message = "Mobile number must be an 11-digit mainland China mobile number")
    private String phonenumber;

    // Existing global accounts can be reused by another shop; the service
    // requires a password only when a new global account is created.
    @Size(min = 0, max = 20, message = "Password cannot exceed 20 characters")
    private String password;

    private String status;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "短信验证码不能为空")
    private String smsCode;

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSmsCode()
    {
        return smsCode;
    }

    public void setSmsCode(String smsCode)
    {
        this.smsCode = smsCode;
    }
}

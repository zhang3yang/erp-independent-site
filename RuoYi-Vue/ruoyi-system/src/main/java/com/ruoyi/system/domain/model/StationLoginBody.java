package com.ruoyi.system.domain.model;

/**
 * 独立站专用登录页的请求体：只有账号和密码，没有验证码。
 *
 * <p>见 docs/superpowers/specs/2026-09-22-store-login-page-design.md。</p>
 */
public class StationLoginBody
{
    private String username;

    private String password;

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}

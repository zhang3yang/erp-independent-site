package com.ruoyi.system.domain;

import java.io.Serializable;

/**
 * 「进入管理」这一次点击的结果，返回给 ERP 前端。
 *
 * <p>替代原来的 {@code StationConsoleEntry}。两个东西的形状差别就是新旧协议的差别
 * （docs/独立站与ERP的边界.md §4.2）：</p>
 *
 * <table border="1">
 *   <tr><th></th><th>旧（票据兑换）</th><th>新（自包含令牌）</th></tr>
 *   <tr><td>前端拿到</td><td>{@code redirectUrl}（票拼在 {@code ?t=} 上）</td>
 *       <td>{@code action} + {@code token}</td></tr>
 *   <tr><td>前端怎么用</td><td>{@code location.href = redirectUrl}</td>
 *       <td>造一个隐藏 form，POST 提交</td></tr>
 *   <tr><td>令牌在哪</td><td><b>URL 上</b></td><td><b>请求体里</b></td></tr>
 * </table>
 *
 * <h3>为什么令牌必须走 POST</h3>
 * <p>§4.2：自包含令牌只要进了 URL，就会进浏览器历史、进书签、进 nginx access log、
 * 进任何外链的 {@code Referer}——<b>而它本身就是凭据</b>，不像旧方案那样
 * 「捡到也得有 clientSecret 才换得动」。POST 表单让它只出现在请求体里，
 * 这是 SAML 的 HTTP-POST binding 用了二十年的办法。</p>
 *
 * <h3>没有 external 那个分叉了</h3>
 * <p>旧的这个类有一个 {@code external} 布尔：没配外部管理台时返回 false，
 * 前端回退到 ERP 内置的 {@code /station-console/:siteCode} 页面。</p>
 *
 * <p><b>那个回退删掉了</b>（§4.5）：内置页本身就要删。没登记域名的站，
 * 「进入管理」按钮就该是灰的，而不是跳到一个 ERP 自己实现的替身。
 * 所以这个类只有一种形态——发得出令牌，或者接口直接报错。</p>
 */
public class StationConsoleJump implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 表单的 action：{@code https://console.<主域名>/sso} */
    private String action;

    /**
     * 自包含签名令牌，{@code payload.sig} 两段。
     *
     * <p>⚠ 它<b>是</b>凭据：60 秒内拿到原文的人可以直接用它。
     * 所以前端必须用隐藏表单 POST 提交，绝不能拼进 URL、绝不能写进 localStorage、
     * 也绝不能打进任何日志。</p>
     */
    private String token;

    /** 令牌剩余有效秒数，前端用来提示「链接已过期请重新点击」 */
    private Integer expiresIn;

    /** 令牌的短标识（jti 的前几位），和服务端日志对得上，排查时报给运维用 */
    private String jti;

    /** 站点名，前端跳转前的提示语要用 */
    private String siteName;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Integer getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Integer expiresIn) { this.expiresIn = expiresIn; }

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
}

package com.ruoyi.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 从 ERP 跳进独立站管理台的配置。对应 {@code ruoyi.station-console.*}。
 *
 * <h3>⚠ 这个类瘦了一大圈，删掉的东西比留下的多</h3>
 * <p>依据 docs/独立站与ERP的边界.md §4.5：</p>
 *
 * <table border="1">
 *   <tr><th>删掉的</th><th>为什么</th></tr>
 *   <tr><td>{@code clientId} / {@code clientSecret}</td>
 *       <td>票据兑换那条回调整个删了。那对凭据的存在本身就是「两边后端要能互通」的证据</td></tr>
 *   <tr><td>{@code baseUrl}（全局管理台地址）</td>
 *       <td>地址现在一站一份，从站点档案的域名派生。<b>全局兜底反而会让 A 站的人跳到 B 站</b></td></tr>
 *   <tr><td>{@code isEnabled()} / 回退逻辑</td>
 *       <td>「没配就回退 ERP 内置页」那个分支删了——内置页本身就要删（§10.1）。
 *           没登记域名的站，「进入管理」按钮就该是灰的</td></tr>
 * </table>
 *
 * <p>剩下的两项都是协议参数，和「跳到哪个站」无关，所以留在全局配置里是合适的。</p>
 */
@Component
@ConfigurationProperties(prefix = "ruoyi.station-console")
public class StationConsoleProperties
{
    /**
     * 令牌有效期的下限 / 上限（秒）。
     *
     * <p>上限从旧版的 300 降到 120。旧协议里令牌落到日志上也没用（兑换要密钥），
     * 300 秒无所谓；新协议里<b>令牌本身就是凭据</b>，有效期直接等于重放窗口，
     * 所以要压。站点侧还会再夹一道（{@code station.jump.max-age-seconds}，默认 120）——
     * 这边配大了那边也不认。</p>
     */
    private static final int TTL_MIN = 10;
    private static final int TTL_MAX = 120;

    /**
     * 令牌有效期（秒）。§4.4 定的是 60。
     *
     * <p><b>不要为了「用户点慢了」调大它。</b>整个流程是「点按钮 → 表单自动提交」，
     * 中间没有人工步骤，60 秒是给网络慢留的余量，不是给人留的。</p>
     */
    private int tokenTtl = 60;

    /**
     * 站点侧接收令牌的路径。ERP 把它拼在 {@code https://console.<主域名>} 后面当表单的 action。
     *
     * <p>站点侧那个端点<b>只接受 POST</b>（§4.4 必须守住的两条之一）。</p>
     */
    private String ssoPath = "/sso";

    /** 夹到 [10, 120] 之间。配错了不报错，按边界处理——但日志里会看到实际用的值 */
    public int getEffectiveTokenTtl()
    {
        return Math.max(TTL_MIN, Math.min(TTL_MAX, tokenTtl));
    }

    /** 保证以 / 开头、不以 / 结尾，拼地址时就不用担心少一个或多一个斜杠 */
    public String getNormalizedSsoPath()
    {
        String path = ssoPath == null || ssoPath.trim().isEmpty() ? "/sso" : ssoPath.trim();
        if (!path.startsWith("/"))
        {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    public int getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(int tokenTtl) { this.tokenTtl = tokenTtl; }

    public String getSsoPath() { return ssoPath; }
    public void setSsoPath(String ssoPath) { this.ssoPath = ssoPath; }
}

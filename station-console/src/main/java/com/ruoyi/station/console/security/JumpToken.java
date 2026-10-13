package com.ruoyi.station.console.security;

/**
 * 从 ERP 跳过来的令牌里那点内容。验签通过之后的产物。
 *
 * <p>对应 docs/独立站与ERP的边界.md §4.3 的 payload：</p>
 * <pre>
 * {
 *   "ver":  1,
 *   "iss":  "erp",
 *   "site": "DLZ1001",              // 必须和本站档案里的站点编码一致，不一致直接拒
 *   "user": "zhangsan",             // ERP 的登录账号，只作为**身份标识**
 *   "name": "张三",
 *   "iat":  1756180000,
 *   "exp":  1756180060,             // 60 秒
 *   "jti":  "9f2c…"                 // 一次性，本侧存 Redis 防重放
 * }
 * </pre>
 *
 * <h3>⚠ 这里<b>没有</b> perms，将来也不该加</h3>
 * <p>这是有意的（§4.3）：权限归独立站自己那套。ERP 只说「这是谁」，不说「他能干什么」——
 * 后者由本站的 {@code u_s_role} 决定。把 ERP 的权限码塞进来，
 * 等于两边的权限模型又绑在一起了，而那正是这次拆分要断掉的东西。</p>
 *
 * <p>{@code iss} 也不在这个类里：它是给「将来可能有多个签发方」留的标记位，
 * 一期只有一个签发方，读它没有意义。真到了那天，判定逻辑要连着密钥选择一起改。</p>
 */
public class JumpToken
{
    /** 站点编码。已经和本站档案比对过，一致才会走到这里 */
    private String siteCode;
    /** ERP 的登录账号。只作为在 u_s_user 里找人的标识 */
    private String user;
    /** ERP 上的显示名。只在首次落本站账号时用一下 */
    private String name;
    /** 一次性标记，已经在 Redis 里消费掉了。留着只为写日志 */
    private String jti;

    /**
     * 入场用途。null = 普通的「进入管理」；{@link #SCOPE_USERS} = 从 ERP「独立站管理」的
     * 「站点用户」按钮进来，管理本站用户（2026-09-24）。
     */
    private String scope;

    /** 管理本站用户。ERP 那边的 IStationConsoleSsoService.SCOPE_USERS 是同一个值 */
    public static final String SCOPE_USERS = "users";

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public String getScope() { return scope; }

    public void setScope(String scope) { this.scope = scope; }
}

package com.ruoyi.station.console.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次进入管理台的身份。
 *
 * <h3>⚠ 它的来源变了</h3>
 * <p>以前这份东西是<b>ERP 兑换票据时返回的</b>，原样存进会话——商户、站点、用户、权限
 * 全是 ERP 说了算。那条路没有了（docs/独立站与ERP的边界.md 要点 1）。</p>
 *
 * <p>现在它由本站自己拼：</p>
 * <ul>
 *   <li>站点信息 ← {@code u_s_siteinfo}（本站档案）；</li>
 *   <li>用户和权限 ← {@code u_s_user} + {@code u_s_role}（本站的用户体系）。</li>
 * </ul>
 *
 * <p>拼它的只有一个地方：{@code SsoController}——从 ERP 免密进入是本站唯一的入场路径。
 * （有一版还有本地登录页，那一版撤掉了，见 {@code SessionController}。）
 * 不管人是谁，权限一律由本站的角色决定。</p>
 *
 * <h3>perms 现在是授权依据了</h3>
 * <p>以前这里写着「{@code perms} 只用来决定界面上显示不显示某个按钮，绝不能拿来当授权依据——
 * 真正的授权发生在 ERP 侧」。<b>那句话现在不成立</b>：ERP 不参与了，
 * 本站就是唯一的权威，{@link AuthInterceptor} 拿这份 perms 做真正的判定。</p>
 *
 * <p>代价是它有会话期的滞后：站长在「用户管理」里收了某人的权限，那个人已经建立的会话
 * 里这份还是旧的。这是有意的取舍——每个请求现查一遍角色权限，
 * 换来的是每个请求多两次数据库查询，而管理台的用户是个位数、权限变更是月级的事。
 * 真要立刻生效，站长把那个人停用即可（停用是在免密进入时判的，
 * 而且可以顺手让他的会话在下一次 {@code /api/me} 时失效）。</p>
 *
 * <p>会话存在 Redis 里，所以必须 {@link Serializable}。字段类型都保持简单
 * （String / List&lt;String&gt;），跨版本反序列化才不容易出事——发版那一刻正在用的人
 * 不该被一个序列化异常挡在外面。</p>
 */
public class ConsoleIdentity implements Serializable
{
    private static final long serialVersionUID = 2L;

    /**
     * 从 ERP 免密进来的。<b>现在只有这一个值。</b>
     *
     * <p>以前还有一个 {@code VIA_LOGIN = "login"}（本地登录页进来的），
     * 随账号密码登录一起删了。Redis 里可能还躺着 {@code via=login} 的旧会话，
     * 那只是一个字符串，界面上不显示它，不用管。</p>
     */
    public static final String VIA_ERP = "erp";

    // ---- 用户，来自 u_s_user / u_s_role ----

    private Long userId;
    private String userName;
    private String nickName;
    private String roleCode;
    private List<String> perms = new ArrayList<>();
    /** 这次是怎么进来的。只有 {@link #VIA_ERP} 一个值了，留着是为了旧会话能反序列化 */
    private String via;

    // ---- 站点，来自 u_s_siteinfo ----

    private String siteCode;
    private String siteName;
    private String langs;
    private String currency;

    /**
     * 商户码。
     *
     * <p>【遗留】一个部署一个站，这个值恒定，界面上一处都没用到。
     * 留在这里只为让写入路径拿得到它（主键里有这一列）——但那条路径读的是
     * {@code SiteInfoService}，不是会话。<b>不下发给前端。</b></p>
     */
    private String shopCode;

    /**
     * 这个会话能不能管本站用户（2026-09-24）。
     *
     * <p>只有从 ERP「独立站管理」的「站点用户」按钮进来的会话是 true（令牌带 scope=users，
     * 见 SsoController）。用户管理从管理台菜单里拿掉了——那一页全是 ERP 登录名、
     * 「ERP 免密」来源，不该给海外客户看——所以用户接口认的是这个标记，<b>不认角色权限</b>：
     * 站长角色、CONSOLE_GRANT_ALL_PERMS 的 * 都开不了它。</p>
     *
     * <p>⚠ 加字段不改 serialVersionUID：会话在 Redis 里是 JDK 序列化的，老会话反序列化回来
     * 这个字段是 false，正是想要的（老会话都是普通进入）。改了版本号反而会让所有在线的人
     * 反序列化失败、被踢出去。</p>
     */
    private boolean userAdmin;

    /** 显示用的人名：昵称优先，没有就用登录名 */
    public String displayName()
    {
        return nickName != null && !nickName.isBlank() ? nickName : userName;
    }

    /** 这个站开了哪几种语言。管理台的多语言文案页签按它来铺 */
    public List<String> langList()
    {
        if (langs == null || langs.isBlank())
        {
            return List.of("en");
        }
        List<String> result = new ArrayList<>();
        for (String part : langs.split("[,，\\s]+"))
        {
            if (!part.isBlank())
            {
                result.add(part.trim().toLowerCase());
            }
        }
        return result.isEmpty() ? List.of("en") : Collections.unmodifiableList(result);
    }

    /** 有没有这个权限。角色是 {@code *} 时全放行 */
    public boolean hasPerm(String perm)
    {
        return perms.contains("*") || perms.contains(perm);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public List<String> getPerms() { return perms; }
    public void setPerms(List<String> perms) { this.perms = perms == null ? new ArrayList<>() : perms; }

    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getLangs() { return langs; }
    public void setLangs(String langs) { this.langs = langs; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public boolean isUserAdmin() { return userAdmin; }

    public void setUserAdmin(boolean userAdmin) { this.userAdmin = userAdmin; }
}

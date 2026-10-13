package com.ruoyi.station.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理台自身的配置。对应 {@code console.*}。
 *
 * @param homeUrl    本站管理台的对外地址（{@code https://console.<主域名>}）。
 *                   询盘通知邮件里那个「去处理」链接用它拼。留空则邮件里不放链接。
 * @param erpHomeUrl ERP 的首页地址，只用来在顶栏放一个「回 ERP」的链接。
 *                   留空则不显示这个链接。
 *
 *                   <p>⚠ <b>它只是一个显示用的链接，不是一个会被后端调用的地址。</b>
 *                   docs/独立站与ERP的边界.md §13 第 5 条要求 ERP 侧不存在任何主动连
 *                   独立站的代码；反过来这一侧同理——这个值绝不能出现在任何
 *                   {@code RestTemplate} / {@code WebClient} 的调用里。想加「顺便去 ERP
 *                   查一下」之前，先读 pom.xml 顶部那段。</p>
 *
 *                   <p><b>⚠ 这个链接该配上，别留空。</b>管理台没有登录页
 *                   （{@code SessionController}）——退出或掉线之后，要再进来<b>只能</b>
 *                   回 ERP 点一次「进入管理」。留空的话顶栏那个链接不显示，
 *                   人得自己知道 ERP 的地址在哪。</p>
 *
 *                   <p>（有一版管理台有自己的登录页，那时候这个值确实只是个便利链接。
 *                   §6a 那一版撤掉了，它又变回「回得去的那条路」。）</p>
 *
 * @param grantAllPerms <b>进来即全权</b>。为 {@code true} 时，任何一个从 ERP 免密进来的人
 *                   都拿到 {@code *}，{@code u_s_user.rolecode} 和 {@code u_s_role.perms}
 *                   不再参与判定。
 *
 *                   <p><b>默认 true</b>，这是一个明确的产品决定，不是疏忽：现在这个站的
 *                   管理台只有站方自己人用，按角色卡权限带来的全是摩擦——最典型的一次是
 *                   从 ERP 免密进来的人默认拿 {@code operator}，而那个角色刻意不含
 *                   {@code site:station:edit}，于是首页装修和站点设置的保存按钮直接不见了，
 *                   看起来像功能没做。</p>
 *
 *                   <p><b>角色那套东西一行都没删</b>：{@code u_s_role} 两条出厂角色、
 *                   每个接口的 {@link com.ruoyi.station.console.security.RequirePerm}、
 *                   前端的 {@code v-hasPermi} 全都在。把这一项配成 {@code false}
 *                   就整套恢复——将来把管理台开给商家自己的运营用时（§6b 说的
 *                   「之后这个人的权限由站长在本站调整」），就是把它关掉。</p>
 *
 *                   <p>⚠ 它<b>只放开权限，不放开认证</b>。没有会话照样 401，
 *                   {@code AuthInterceptor} 那道会话检查和「没挂 @RequirePerm 一律拒绝」
 *                   都还在。它改的只是「登录之后你是谁」这一层。</p>
 *
 * @param usersMenu  管理台左边菜单里要不要有「用户管理」。<b>默认 false</b>。
 *
 *                   <p>2026-09-24 用户管理从菜单拿掉了（那一页全是 ERP 登录名，不给海外客户看），
 *                   唯一入口是 ERP「站点用户」按钮。但那是<b>法国站</b>的需要：统领这种
 *                   站方自己人在用的站，员工管理要留在菜单里。一个部署一个站，所以按部署开关。</p>
 *
 *                   <p>为 true 时，从 ERP「进入管理」进来、且角色有 {@code site:user:list} 的会话
 *                   也打上 {@code userAdmin}（见 {@code SsoController#grantsUserAdmin}），
 *                   用户接口那道闸（{@code requireUserAdmin}）一行不改。</p>
 */
@ConfigurationProperties(prefix = "console")
public record ConsoleProperties(String homeUrl, String erpHomeUrl, Boolean grantAllPerms, Boolean usersMenu)
{
    public ConsoleProperties
    {
        homeUrl = normalize(homeUrl, "console.home-url");
        erpHomeUrl = normalize(erpHomeUrl, "console.erp-home-url");
        // ⚠ 用包装类型 Boolean 而不是 boolean：record 没法给缺失的属性一个默认值，
        //   而基本类型在属性缺失时会被绑成 false——那正好是和意图相反的那个值。
        //   包装类型能区分「没配」（null）和「配成了 false」。
        grantAllPerms = grantAllPerms == null || grantAllPerms;
        usersMenu = Boolean.TRUE.equals(usersMenu);
    }

    /** 菜单里有没有「用户管理」。见 record 头上那段说明 */
    public boolean isUsersMenu()
    {
        return Boolean.TRUE.equals(usersMenu);
    }

    /** 登录即全权。见 record 头上那段说明 */
    public boolean isGrantAllPerms()
    {
        return Boolean.TRUE.equals(grantAllPerms);
    }

    /** 去掉末尾斜杠的管理台地址，拼深链时用。没配返回空串 */
    public String consoleHomeUrl()
    {
        return homeUrl;
    }

    /**
     * 只认 http/https，并去掉末尾斜杠。
     *
     * <p>这两个值都会被拼进页面上的跳转链接，{@code javascript:} 开头的串放进来
     * 就是一次存储型 XSS。它们来自配置文件而不是用户输入，但配置文件是能被
     * 部署脚本、环境变量写进来的，校验一次不亏。</p>
     */
    private static String normalize(String url, String name)
    {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty())
        {
            return "";
        }
        String lower = value.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://"))
        {
            throw new IllegalArgumentException(name + " 必须以 http:// 或 https:// 开头");
        }
        while (value.endsWith("/"))
        {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}

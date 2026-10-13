package com.ruoyi.system.service;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.system.domain.StationConsoleJump;

/**
 * 从 ERP 跳进独立站管理台。<b>这是 ERP 和独立站之间唯一的一条线。</b>
 *
 * <p>docs/独立站与ERP的边界.md §4。全景图里三条箭头，只有这一条是实线：</p>
 * <pre>
 *   登录跳转    ERP → 独立站   ✅ 一期做
 *   订单拉取    独立站 → ERP   ❌ 不做（§8）
 *   生产进度同步 ERP → 独立站   ❌ 不做（§8）
 * </pre>
 *
 * <h3>⚠ 协议换了，{@code redeem} 删掉了</h3>
 * <p>旧协议是「一次性不透明票 + 服务端兑换」：ERP 发一张随机票存 Redis，
 * 管理台后端拿着 {@code clientSecret} 回调 ERP 把票换成身份。</p>
 *
 * <p><b>那个回调就是一条后端互通</b>，而且是最硬的一条（§4.1）：它要求管理台能连到
 * ERP 的地址、要求两边共享一个 {@code clientSecret}、要求 ERP 常年为境外服务器
 * 开一个入口。要点 1「独立站与 ERP 的后端没有任何互通」直接否掉它。</p>
 *
 * <p>新协议是<b>自包含签名令牌 + POST 表单</b>：ERP 用这个站登记的密钥签一个
 * HMAC-SHA256 令牌（60 秒），前端造隐藏表单 POST 到站点的 {@code /sso}，
 * 站点用自己那份密钥<b>纯本地验签</b>——ERP 那边不需要在线。</p>
 *
 * <h3>它比旧协议弱在哪（明说）</h3>
 * <p>旧方案里令牌落到日志上也没用（兑换要密钥）；新方案里 60 秒内拿到令牌原文的人
 * 可以直接用它。<b>这是「后端不许互通」的代价，不是疏忽</b>（§4.4）。
 * 压制手段是四条：走 POST 不进 URL、60 秒有效期、jti 一次性、一站一密钥。</p>
 */
public interface IStationConsoleSsoService
{
    /**
     * 签一张进管理台的跳转令牌。
     *
     * <p>校验：站点存在 / 属于本商户 / 未停用 / 登记了管理后台域名 / 配了跳转密钥。
     * 任何一条不过就抛异常——<b>不再有「回退到 ERP 内置页」那个分支</b>（§4.5），
     * 内置页本身已经删了。</p>
     *
     * <p>顺手在 {@code u_s_site.lastaccesstime} 上打一次点。⚠ 语义是<b>「点了进入管理」</b>，
     * 不是「进成功了」——新协议下 ERP 不知道后续，验签在站点侧。</p>
     *
     * @param clientIp 点击方的 IP，只记进日志，不用于拦截
     */
    default StationConsoleJump issueJump(String shopCode, String siteCode, LoginUser loginUser, String clientIp)
    {
        return issueJump(shopCode, siteCode, loginUser, clientIp, null);
    }

    /**
     * 令牌的入场用途：管理本站用户（2026-09-24）。
     *
     * <p>站点的「用户管理」从管理台菜单里拿掉了——那一页满是 ERP 登录名、「ERP 免密」来源，
     * 不该给海外客户看。入口挪到 ERP「独立站管理」的「站点用户」按钮，令牌上带这个 scope，
     * 站点只给带它进来的会话开用户接口。</p>
     *
     * <p>⚠ 这<b>不是</b>把 ERP 的权限码塞进令牌（StationConsoleSsoServiceImpl.sign 上那条禁令）：
     * 它不描述这个人在站点里能干什么，只描述「这一次是从哪个按钮进来的」。
     * 「谁能管站点用户」这件事由此归 ERP 的 {@code site:station:edit} 决定——这是有意的取舍，
     * 站点自己的角色表不再管它。</p>
     */
    String SCOPE_USERS = "users";

    /**
     * 同上，多一个入场用途。
     *
     * @param scope {@code null} = 普通的「进入管理」；{@link #SCOPE_USERS} = 管理站点用户。
     *              <b>只有 ERP 员工那条路（StationSiteController）能传非 null</b>，
     *              外部账号的 /station/login 永远走上面那个四参数版本。
     */
    StationConsoleJump issueJump(String shopCode, String siteCode, LoginUser loginUser, String clientIp,
            String scope);
}

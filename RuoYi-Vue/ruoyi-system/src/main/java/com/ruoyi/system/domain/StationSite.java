package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

/**
 * 独立站<b>登记</b>档案。对应表 {@code sdsshop.u_s_site}。
 *
 * <h3>⚠ 这张表的性质变了：从「站点的全部」变成「一张登记表」</h3>
 * <p>依据 docs/独立站与ERP的边界.md §7：</p>
 *
 * <blockquote>
 * ERP 的「独立站管理」是一张<b>登记表</b>，不是一个后台入口。它只回答
 * 「我们有哪些站、在哪、怎么进」，<b>不含站里的任何业务数据</b>。
 * </blockquote>
 *
 * <p>所以这个类上不再有 {@code contentJson}（首页装修）、{@code langs}、{@code currency}、
 * {@code notifyEmail}——那些是站里的业务数据，搬到站点服务器的
 * 站点服务器那个库的 {@code u_s_siteinfo} 去了（§7.2）。</p>
 *
 * <h3>库也换了，现在是 {@code sdsshop}</h3>
 * <p>独立站那个库整个搬到站点服务器上去了（§5.2），ERP 这边不再有它。
 * 这张表留下来，进 {@code sdsshop}——它是按 {@code shop_code} 分租户的商家资产，
 * 和 {@code u_m_buyer} / {@code u_m_waregoods} 是同一类东西。</p>
 *
 * <p>更实际的理由是：ERP 侧要是留一个同名的空库，
 * 下一个人看到它还在，会以为独立站的数据仍然在这边。</p>
 *
 * <h3>摘掉的三列（§7.2）</h3>
 * <ul>
 *   <li>{@code sitekey} —— 站点前端拿它调 ERP 的开放接口限定数据范围用。
 *       现在前台取自己的后端、一个部署一个站，没有范围要限定；</li>
 *   <li>{@code apienabled} —— 同上，开放接口不再由 ERP 提供；</li>
 *   <li>{@code adminuser} —— 早就标了废弃，借这次一起清。</li>
 * </ul>
 *
 * <h3>{@code status} 是唯一有意保留两份的列</h3>
 * <p>ERP 这一份管「这个站还在不在服务、<b>还发不发跳转令牌</b>」；
 * 站点那一份（{@code u_s_siteinfo.status}）管「前台还开不开」。
 * <b>两个决定，不是一个值的副本</b>——所以不需要同步，也不该同步。</p>
 */
public class StationSite implements Serializable
{
    private static final long serialVersionUID = 2L;

    /** 启用：还在服务，能发跳转令牌 */
    public static final String STATUS_ENABLED = "启用";
    /** 停用：站关了或不再续费，留档不删。发令牌会被拒 */
    public static final String STATUS_DISABLED = "停用";

    /** 升级前的三态遗留值 */
    private static final String LEGACY_BUILDING = "建设中";
    private static final String LEGACY_RUNNING = "运行中";
    private static final String LEGACY_DISABLED = "已停用";

    /**
     * 状态归一化：把遗留值折进「启用 / 停用」两态。
     *
     * <p>状态本来是「建设中 / 运行中 / 已停用」三态，现在界面上只留启用和停用。
     * 库里的历史值由 {@code sql/station_site_status_20260826.sql} 一次性改写，但接口是能直接写的，
     * 老调用方仍可能把三态里的值传上来，所以写入前在这里折一次：建设中、运行中都算启用，
     * 已停用算停用。空值按启用处理——建站时不填状态，本意就是这个站要用。</p>
     */
    public static String normalizeStatus(String status)
    {
        String value = status == null ? "" : status.trim();
        if (value.isEmpty() || LEGACY_BUILDING.equals(value) || LEGACY_RUNNING.equals(value))
        {
            return STATUS_ENABLED;
        }
        if (LEGACY_DISABLED.equals(value))
        {
            return STATUS_DISABLED;
        }
        return value;
    }

    /**
     * 这个状态算不算停用。
     *
     * <p>凡是拿状态挡门的地方都走这里，别直接 {@code STATUS_DISABLED.equals}：
     * 迁移脚本还没跑的库里，停用的站存的仍是「已停用」，漏认一个值就等于把门开着。</p>
     */
    public static boolean isDisabled(String status)
    {
        return STATUS_DISABLED.equals(normalizeStatus(status));
    }

    private String shopCode;
    private String siteCode;
    private String siteName;

    // ==================== 域名（要点 7 明列的五个） ====================

    /**
     * 主域名，如 {@code kangchen.com}。商户内唯一，无值一律存 null 不存空串。
     *
     * <p>下面四个子域默认由它派生（{@code data.} / {@code file.} / {@code console.} /
     * {@code buyer.} 前缀），每一个又都能单独覆盖——约定能覆盖绝大多数站，
     * 但「以后每个客户带自己的品牌域名」的场景里，客户不一定照我们的约定命名（§7.1）。</p>
     */
    private String domain;

    /** 后端服务域名。留空则取 {@code data.<主域名>}，见 {@link #resolvedDataDomain()} */
    private String dataDomain;

    /** 文件域名。留空则取 {@code file.<主域名>} */
    private String fileDomain;

    /**
     * 管理后台域名。留空则取 {@code console.<主域名>}。
     *
     * <p><b>跳转地址由它拼</b>（§7.1）。这一列替代了原来的 {@code consoleurl}
     * 和全局配置 {@code ruoyi.station-console.base-url}——§4.5：</p>
     *
     * <blockquote>
     * {@code STATION_CONSOLE_BASE_URL} 这个<b>全局</b>管理台地址删掉——地址现在一站一份，
     * 从站点档案的域名派生，<b>全局兜底反而会让 A 站的人跳到 B 站</b>。
     * </blockquote>
     */
    private String consoleDomain;

    /** 买家后台域名。留空则取 {@code buyer.<主域名>}。一期只登记，买家中心不实现（§11） */
    private String buyerDomain;

    /**
     * 站点服务器的公网 IP，多个用逗号分隔。
     *
     * <p><b>ERP 不会去连它。</b>它是给运维找机器用的登记信息（要点 7 明列）。</p>
     *
     * <p>⚠ §13 第 5 条：ERP 侧不存在任何主动连独立站的代码。这个字段
     * <b>绝不能出现在任何 {@code RestTemplate} / {@code WebClient} 的调用里</b>。</p>
     */
    private String serverIp;

    // ==================== 跳转密钥 ====================

    /**
     * 这个站的跳转令牌签名密钥。<b>密文存</b>。
     *
     * <p>ERP 用它签令牌，站点用自己那份（{@code u_s_siteinfo.consolesecret}）验签。
     * <b>两边是同一个值，但从不在网络上传输</b>——这就是「后端不互通」还能免密跳转的原理。</p>
     *
     * <p>一站一份：泄一个只影响那一个站，拿 A 站的令牌去 B 站验不过（§4.4 第 4 条）。</p>
     *
     * <p>加密照抄现成的那一套：{@code s_m_shop_storage.access_key_secret} 用
     * {@code OSS_CONFIG_SECRET} 主密钥 + {@code AesUtils}（AES/GCM）。
     * <b>不要再发明第二种加密方式</b>——两套加密就有两个主密钥要轮换，
     * 而第二套一定是那个没人记得怎么轮换的（§7.1）。</p>
     *
     * <p><b>{@code @JsonIgnore} 不能去掉。</b>站点列表接口会把这个对象序列化给 ERP 前端，
     * 密钥跟着下发到浏览器，等于任何一个能看见站点列表的员工都能伪造跳转令牌，
     * 以任意身份进任意一个站的后台。</p>
     */
    @JsonIgnore
    private String consoleSecret;

    /**
     * 给前端看的：密钥配过没有。密钥本身永远不下发。
     *
     * <p>⚠ 这是一个<b>由 SQL 现算出来的列</b>（{@code StationSiteMapper.xml} 里那个
     * {@code secretFlag} 片段），不是从 {@link #consoleSecret} 推出来的。</p>
     *
     * <p>写成 {@code isSecretConfigured(){ return consoleSecret != null; }} 是不行的：
     * 列表和详情的 SQL <b>根本不查 consolesecret 那一列</b>（有意的，见 mapper 里的注释），
     * 那个方法会恒返回 false，界面上永远显示「未配置」。</p>
     */
    private Boolean secretConfigured;

    // ==================== 管理信息 ====================

    private String ownerName;
    /** 域名/建站服务到期日，用于到期提醒；null 表示不提醒 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expireDate;
    private String status;

    /**
     * 最后一次点「进入管理」的时间。
     *
     * <p>⚠ <b>语义变了，别当活跃度指标用。</b>§4.5：</p>
     *
     * <blockquote>
     * ERP 的 {@code lastaccesstime} 改成<b>发令牌时</b>打点。语义从「进成功了」
     * 变成「点了进入管理」，差别得写在列注释里，不然日后有人拿它当活跃度指标会算错。
     * </blockquote>
     *
     * <p>旧协议里这个点打在「票据兑换成功」那一刻——那时候 ERP 知道人真的进去了。
     * 新协议下 ERP 发完令牌就不知道后续了（验签在站点侧、不回调），
     * 只能打在发令牌这一步。所以它统计的是<b>点击次数</b>，
     * 不是成功进入次数：令牌过期了、被拒了，这里照样有记录。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastAccessTime;

    private Integer sortNum;
    private String remark;
    private String createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private String updateUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /**
     * 距到期还有多少天，查询时由数据库现算，不落库。
     *
     * <p>负数表示已经过期，null 表示没填到期日。落库的话每天都得跑一遍刷新，
     * 而它只是个展示值，现算最省事。</p>
     */
    private Integer expireDays;

    // ==================== 子域派生 ====================

    /** 后端服务域名：登记了就用登记的，没登记按约定拼 */
    public String resolvedDataDomain()
    {
        return subDomain(dataDomain, "data");
    }

    /** 文件域名 */
    public String resolvedFileDomain()
    {
        return subDomain(fileDomain, "file");
    }

    /** 管理后台域名。跳转地址由它拼，见 {@link #consoleBaseUrl()} */
    public String resolvedConsoleDomain()
    {
        return subDomain(consoleDomain, "console");
    }

    /**
     * <b>买家中心</b>的域名（我的订单、账号资料、地址簿管理）。
     * 留空则取 {@code buyer.<主域名>}。
     *
     * <h3>⚠ 这不是前台</h3>
     * <p>前台（商城）在<b>主域</b>上，取法见 {@link #getFrontUrl()}。
     * 买家中心只有 {@code /account} 和 {@code /orders} 两支；登录、注册、找回密码、
     * 重置密码、邮箱验证<b>都在主域</b>（判据是「不登录就看不到」，
     * 见 {@code station-site/shared/utils/buyerPaths.ts}）。</p>
     *
     * <p>这一列的说明反复过三次，写清楚免得第四次：</p>
     * <ol>
     *   <li><b>设计期</b>：前台挂主域，{@code buyer.} 留给以后的买家中心——就是现在这样。</li>
     *   <li><b>2026-09-07</b>：改成「前台域名」。因为实际部署时主域在 nginx 上
     *       连 server 块都没有，前台落在了 {@code buyer.} 子域上，买家中心是它下面的
     *       几个页面而不是另一个站。</li>
     *   <li><b>2026-09-08</b>：改回买家中心域名。用户要求「登录的人和浏览的人不在
     *       同一个域名上」，于是前台搬回主域，{@code buyer.} 只留买家中心。
     *       主域可以对外服务（备案覆盖到，当天确认过）。</li>
     * </ol>
     *
     * <p>⚠ 2026-09-10 复原过一次：这段说明曾经把上面第二版和第三版<b>两份叠在一起</b>，
     * 一句说「当前生效的是第二版、前台仍在 buyer. 上」，下一句说「前台挂主域，就是现在
     * 这样」。那是合并 {@code fdae0b80} 解冲突时把 {@code f94cace8}（阶段三）按另一侧
     * 覆盖掉留下的，{@link #getFrontUrl()} 的方法体当时也一起被退回了。
     * 判断哪一版是对的只看线上：主域返回 200 的商城、{@code buyer.} 上的公开页 301
     * 回主域，就是第三版。</p>
     *
     * <p>设计见 {@code docs/设计稿/2026-09-08-独立站域名拆分.md}。</p>
     */
    public String resolvedBuyerDomain()
    {
        return subDomain(buyerDomain, "buyer");
    }

    /**
     * <b>前台地址</b>，站点登记列表上那个链接和「复制地址」用它。
     *
     * <p>没有域名可用时返回 null——调用方据此把链接和按钮收掉。</p>
     *
     * <h3>前台在主域上</h3>
     * <p>商城（首页、商品、购物车、下单、登录注册）在主域，
     * 买家中心（我的订单、账号资料、地址簿）在 {@link #resolvedBuyerDomain()}。</p>
     *
     * <p>⚠ 2026-09-07 到 2026-09-08 之间这里返回的是 {@code buyer.} 子域，
     * 因为那段时间前台确实部署在那上面（主域在 nginx 上没有 server 块，
     * 443 落到兜底块被 444 掉，而浏览器在断连之前已经拿到了 {@code buyer.} 那张证书，
     * 于是页面能出来、地址栏一片红，看着像证书配错了其实是地址不对）。
     * 现在主域自己有 server 块和证书了，那个绕法作废。
     * 见 {@code docs/设计稿/2026-09-08-独立站域名拆分.md}。</p>
     *
     * <p>⚠ <b>别再改回 {@link #resolvedBuyerDomain()}。</b>这件事被退回过一次：
     * 阶段三的修复 {@code f94cace8} 是 HEAD 的祖先，却在合并 {@code fdae0b80} 里被按
     * 另一侧解掉了，方法体退回 {@code buyer.} 子域、上面那段列注释还叠了两份互相打脸的
     * 说明（2026-09-10 复原）。会有这种事是因为「前台在哪个域」在三天里翻了两次，
     * 而两条分支各自记了一版。<b>判据只看线上</b>：主域返回 200 的商城、{@code buyer.}
     * 上的公开页 301 回主域，前台就在主域。</p>
     *
     * <p>⚠ 这个方法<b>随 ERP 发版就生效</b>，不像 nginx / 环境变量 / SQL 那几处要人
     * 主动去做。所以它和独立站那边的切换必须同向：独立站已经切完了
     * （{@code NUXT_PUBLIC_SITE_URL} 是主域），这里返回主域才是对的。</p>
     *
     * <p>和 {@link #consoleBaseUrl()} 一样对回环地址降级到 http，理由见那边。
     * 区别是这个要序列化给前端（列表页要拿它做 href），所以<b>不加</b>
     * {@code @JsonIgnore}：让浏览器侧自己拼的话，就得把 {@link #stripScheme} 那套
     * 截断规则在 JS 里再写一遍，而那套规则是有安全用途的（见它的注释）。</p>
     */
    public String getFrontUrl()
    {
        // ⚠ stripScheme 上来就 value.trim()，传 null 进去是 NPE，先挡一道
        String root = trimToNull(domain);
        if (root == null)
        {
            return null;
        }
        // 只填了个 "https://" 这种，抠完是空串，同样当没填
        String host = trimToNull(stripScheme(root));
        if (host == null)
        {
            return null;
        }
        return (isLoopback(host) ? "http://" : "https://") + host;
    }

    /**
     * 管理台的基地址，跳转表单的 {@code action} 由它拼。
     *
     * <p>没有域名可用时返回 null——调用方据此把「进入管理」按钮置灰。§4.5：</p>
     *
     * <blockquote>
     * ERP 侧那个「外部管理台没配就回退到 ERP 内置页」的分支删掉——内置页本身就要删。
     * <b>没登记域名的站，「进入管理」按钮就该是灰的</b>，
     * 而不是跳到一个 ERP 自己实现的替身。
     * </blockquote>
     *
     * <h3>协议：回环地址走 http，其余一律 https</h3>
     * <p>这里原来恒定拼 {@code https://}。但写入侧
     * （{@code StationSiteServiceImpl.requireHostLike}）的注释明确预期了
     * 「本地开发会填 {@code 127.0.0.1:5174}」，字符白名单也为端口放行了冒号——
     * 也就是说填本机地址是支持的用法，只有这里没跟上：拼出
     * {@code https://localhost:5174/sso} 打到 vite dev server 上必然失败，
     * 而失败信息是握手层面的，看不出是协议拼错了。</p>
     *
     * <p>只对回环地址降级，不做成配置项：</p>
     * <ul>
     *   <li>回环流量不出本机，没有中间人可言，令牌进不了网络；</li>
     *   <li>做成 {@code scheme} 配置项的话，它迟早会在某台生产机上被设成 http，
     *       而那时令牌是<b>明文过网</b>的——60 秒内谁捡到都能直接进后台（§4.4）。
     *       一个不可能配错的判断胜过一个可以配错的开关。</li>
     * </ul>
     */
    @JsonIgnore
    public String consoleBaseUrl()
    {
        String host = resolvedConsoleDomain();
        if (host == null)
        {
            return null;
        }
        return (isLoopback(host) ? "http://" : "https://") + host;
    }

    /**
     * 这个「主机[:端口]」是不是本机回环地址。
     *
     * <p>认这几种：{@code localhost}、{@code *.localhost}（RFC 6761 保留，
     * 解析必然指向本机）、{@code 127.0.0.0/8} 里的任意地址、IPv6 的 {@code ::1}
     * （裸写和 {@code [::1]} 两种形式）。</p>
     *
     * <p>⚠ 不认内网段（{@code 192.168.*}、{@code 10.*}）——那些是真的过网的，
     * 该走 https。这里放宽的边界就到「流量不出本机」为止。</p>
     */
    private static boolean isLoopback(String hostPort)
    {
        String host = hostPort.trim().toLowerCase(Locale.ROOT);
        // IPv6 字面量是 [::1]:5174 这种形式，先把方括号里的取出来
        if (host.startsWith("["))
        {
            int close = host.indexOf(']');
            host = close < 0 ? host.substring(1) : host.substring(1, close);
        }
        else
        {
            // IPv4 / 域名：第一个冒号之后是端口。
            // 裸写的 IPv6（::1）本身含冒号，所以只在「只有一个冒号」时才当端口切
            int colon = host.indexOf(':');
            if (colon >= 0 && colon == host.lastIndexOf(':'))
            {
                host = host.substring(0, colon);
            }
        }
        if (host.equals("localhost") || host.endsWith(".localhost"))
        {
            return true;
        }
        if (host.equals("::1"))
        {
            return true;
        }
        // 127.0.0.0/8：127.0.0.1 是常见的那个，但 127.x.x.x 全段都是回环
        return host.startsWith("127.") && host.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
    }

    /**
     * 子域派生：覆盖值优先，否则拼前缀。
     *
     * <p>覆盖值允许写成完整域名（{@code admin.client.com}），也允许带着协议前缀
     * 被人贴进来——后者剥掉，因为这一列存的是<b>域名</b>不是 URL。
     * 不这么剥的话，拼出来的会是 {@code https://https://admin.client.com}。</p>
     */
    private String subDomain(String override, String prefix)
    {
        String value = trimToNull(override);
        if (value != null)
        {
            return stripScheme(value);
        }
        String root = trimToNull(domain);
        return root == null ? null : prefix + "." + stripScheme(root);
    }

    /**
     * 从一个可能写歪了的值里抠出主机名。
     *
     * <h3>⚠ 为什么不能只剥协议头</h3>
     * <p>这几列存的是<b>域名</b>，但它们的历史来源是老的 {@code consoleurl}——那一列是
     * 「管理台地址」，人往里填过各种东西。搬迁时真实数据里有这两条：</p>
     * <pre>
     *   101.200.135.96
     *   sale.alibaba.com/p/dm2vajxkf/index.html?joinSource=gw_bing_pp001_pc&amp;origin=87006
     * </pre>
     *
     * <p>只剥协议头的话，第二条会原样留下，{@link #consoleBaseUrl()} 拼出来就是
     * {@code https://sale.alibaba.com/p/dm2vajxkf/index.html?...}——而「进入管理」是
     * <b>把跳转令牌 POST 到那个地址</b>。也就是说，一条填错的历史数据会让令牌被发到
     * 第三方服务器上去。令牌 60 秒内可以直接用（§4.4），这是真的会出事。</p>
     *
     * <p>所以这里在第一个 {@code /}、{@code ?}、{@code #} 处截断，只留主机（含端口）。
     * 写入侧的 {@code StationSiteServiceImpl.normalizeDomain} 也会截一次并且校验字符集——
     * <b>两侧都做是有意的</b>：库里那批历史脏值不会经过写入侧。</p>
     *
     * <p>顺带剥掉 {@code user:pass@} 那种带凭据的前缀：{@code evil.com@real.com} 这种串
     * 在浏览器眼里主机是 {@code real.com}，但肉眼读起来像 evil.com，是经典的钓鱼写法。
     * 这一列不该出现凭据，直接丢掉 {@code @} 之前的部分。</p>
     */
    private static String stripScheme(String value)
    {
        String result = value.trim();
        String lower = result.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://"))
        {
            result = result.substring(8);
        }
        else if (lower.startsWith("http://"))
        {
            result = result.substring(7);
        }
        // 凭据前缀：取最后一个 @ 之后的部分
        int at = result.lastIndexOf('@');
        if (at >= 0)
        {
            result = result.substring(at + 1);
        }
        // 路径 / 查询串 / 锚点：一律截掉，这一列只放主机
        for (char sep : new char[] { '/', '?', '#', '\\' })
        {
            int cut = result.indexOf(sep);
            if (cut >= 0)
            {
                result = result.substring(0, cut);
            }
        }
        return result.trim();
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    // ==================== getter / setter ====================

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDataDomain() { return dataDomain; }
    public void setDataDomain(String dataDomain) { this.dataDomain = dataDomain; }

    public String getFileDomain() { return fileDomain; }
    public void setFileDomain(String fileDomain) { this.fileDomain = fileDomain; }

    public String getConsoleDomain() { return consoleDomain; }
    public void setConsoleDomain(String consoleDomain) { this.consoleDomain = consoleDomain; }

    public String getBuyerDomain() { return buyerDomain; }
    public void setBuyerDomain(String buyerDomain) { this.buyerDomain = buyerDomain; }

    public String getServerIp() { return serverIp; }
    public void setServerIp(String serverIp) { this.serverIp = serverIp; }

    public String getConsoleSecret() { return consoleSecret; }
    public void setConsoleSecret(String consoleSecret) { this.consoleSecret = consoleSecret; }

    public Boolean getSecretConfigured() { return secretConfigured; }
    public void setSecretConfigured(Boolean secretConfigured) { this.secretConfigured = secretConfigured; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public Date getExpireDate() { return expireDate; }
    public void setExpireDate(Date expireDate) { this.expireDate = expireDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getLastAccessTime() { return lastAccessTime; }
    public void setLastAccessTime(Date lastAccessTime) { this.lastAccessTime = lastAccessTime; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Integer getExpireDays() { return expireDays; }
    public void setExpireDays(Integer expireDays) { this.expireDays = expireDays; }
}

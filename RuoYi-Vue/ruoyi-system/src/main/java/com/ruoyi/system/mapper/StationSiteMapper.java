package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.StationSite;

/**
 * 独立站登记表 Mapper。对应表 {@code sdsshop.u_s_site}。
 *
 * <p>⚠ 库换成了 {@code sdsshop}——独立站原来那个库整个搬到站点服务器上去了
 * （docs/独立站与ERP的边界.md §5.2）。XML 里那 9 处库限定名是搬库那一步在
 * ERP 侧唯一的代码改动，漏一处就是运行期一句「Unknown database」。</p>
 *
 * <p>所有方法都带 shopCode：站点是租户数据，漏掉租户列就会跨商户串数据。</p>
 *
 * <h3>删掉的两个方法</h3>
 * <ul>
 *   <li>{@code selectByKey(siteKey)} —— 开放接口按 sitekey 找站点用的。
 *       开放接口整个搬到站点后端了，{@code sitekey} 那一列也删了（§7.2）；</li>
 *   <li>{@code updateContent(...)} —— 首页装修。那是站里的业务数据，
 *       搬到 {@code u_s_siteinfo.contentjson} 去了（§7.2）。</li>
 * </ul>
 */
public interface StationSiteMapper
{
    /** 条件分页查询。query 上的 shopCode 必填，其余字段为空即不参与过滤。 */
    List<StationSite> selectList(@Param("query") StationSite query);

    /** 按编码查单个站点。<b>不含密钥</b>，见 {@link #selectSecret} */
    StationSite selectByCode(@Param("shopCode") String shopCode, @Param("siteCode") String siteCode);

    /**
     * 单独取跳转密钥的密文。
     *
     * <p><b>只有签跳转令牌那一条路径能调</b>（{@code StationConsoleSsoServiceImpl}）。
     * 和 {@link #selectByCode} 分开是有意的：那一条的结果会被直接序列化给前端，
     * 这一条的结果只在服务端存活几毫秒——两条路径分开，
     * 「密钥会不会跟着响应体出去」这个问题就有一个一眼能看完的答案。</p>
     */
    String selectSecret(@Param("shopCode") String shopCode, @Param("siteCode") String siteCode);

    /**
     * 按域名反查站点，用于唯一键冲突时说清是被谁占了。
     *
     * <p>domain 传 null 直接返回 null——「没填域名」不构成占用，
     * 否则两个都没填域名的站会互相认成重复。</p>
     */
    StationSite selectByDomain(@Param("shopCode") String shopCode, @Param("domain") String domain);

    int insert(StationSite site);

    int update(StationSite site);

    /**
     * 换跳转密钥。
     *
     * <p>和 {@link #update} 分开，因为它是一个有后果的动作：换完之后<b>必须同步改
     * 站点服务器上的 {@code u_s_siteinfo.consolesecret}</b>，否则那个站的
     * 「进入管理」立刻失效。放在同一条 update 里的话，改个站点名就可能顺手换了密钥。</p>
     */
    int rotateSecret(@Param("shopCode") String shopCode, @Param("siteCode") String siteCode,
            @Param("consoleSecret") String consoleSecret, @Param("updateUser") String updateUser);

    /**
     * 记一次「点了进入管理」。只动 lastaccesstime，不碰 updateuser/updatetime。
     *
     * <p>⚠ 语义变了（§4.5）：以前打在「票据兑换成功」那一刻，现在打在<b>发令牌</b>这一步。
     * 详见 {@link StationSite#getLastAccessTime()} 的注释。</p>
     */
    int touchAccessTime(@Param("shopCode") String shopCode, @Param("siteCode") String siteCode);

    int deleteByCodes(@Param("shopCode") String shopCode, @Param("siteCodes") String[] siteCodes);
}

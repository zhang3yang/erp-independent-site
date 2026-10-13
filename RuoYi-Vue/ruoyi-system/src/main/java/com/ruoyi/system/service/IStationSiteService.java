package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.StationSite;

/**
 * 独立站<b>登记</b> Service。
 *
 * <p>docs/独立站与ERP的边界.md §7：ERP 的「独立站管理」是一张登记表，
 * 不是一个后台入口。它只回答「我们有哪些站、在哪、怎么进」。</p>
 *
 * <h3>删掉的两个方法</h3>
 * <ul>
 *   <li>{@code enterConsole(...)} —— 进 ERP 内置管理台的那道闸。内置管理台整个删了（§10.1），
 *       打点挪到了发跳转令牌那一步；</li>
 *   <li>{@code saveContent(...)} —— 首页装修。搬到站点后端了（§7.2）。</li>
 * </ul>
 */
public interface IStationSiteService
{
    /** 条件分页查询，query.shopCode 必填 */
    List<StationSite> selectSiteList(StationSite query);

    /** 查单个站点。<b>不含跳转密钥</b> */
    StationSite selectSiteByCode(String shopCode, String siteCode);

    /**
     * 新增站点，编码由服务端生成，前端传的 siteCode 一律忽略。
     *
     * <p>建站时<b>自动生成一份跳转密钥</b>并返回明文——这是它唯一一次以明文形式出现，
     * 库里存的是密文。装站点服务器时要把这个值填进那边的
     * {@code u_s_siteinfo.consolesecret}，两边必须一致。</p>
     *
     * @return 新建的站点，{@code consoleSecret} 上带着<b>明文</b>密钥（仅这一次）
     */
    StationSite insertSite(StationSite site);

    int updateSite(StationSite site);

    /**
     * 换一份跳转密钥。
     *
     * <p>⚠ 换完之后<b>必须同步改站点服务器上的 {@code u_s_siteinfo.consolesecret}</b>，
     * 否则那个站的「进入管理」立刻失效——而前台和本地登录不受影响，
     * 所以这个故障看起来会像「只有跳转坏了」。</p>
     *
     * @return 新密钥的<b>明文</b>，只在这次返回里出现
     */
    String rotateSecret(String shopCode, String siteCode, String updateUser);

    /** 删除站点。登记表没有下游依赖，直接删 */
    int deleteSiteByCodes(String shopCode, String[] siteCodes);

    /**
     * 登录直达判定：这个人选完商家之后，能不能跳过工作台直接进站点管理台。
     *
     * <p>三个条件同时成立才返回那个站，否则返回 {@code null}
     * （docs/superpowers/specs/2026-09-21-erp-direct-console-login-design.md）：</p>
     * <ol>
     *   <li>不是平台超级管理员；</li>
     *   <li>租户权限非空、含 {@code site:station:list}、且<b>每一条</b>都以
     *       {@code site:station:} 开头——即他在 ERP 里除了「独立站管理」没有别的菜单。
     *       业务员再怎么只管一个站也带订单、库存权限，不会命中；</li>
     *   <li>该商家名下<b>恰好一个</b>可跳转的站（未停用、算得出管理台地址、密钥已配）。</li>
     * </ol>
     *
     * <p>这里只回答「直达哪个站」，<b>不签令牌</b>——签发仍走 console-jump 那条接口，
     * 它有权限守卫、操作日志和 lastaccesstime，不该为了少一次请求复制一份。</p>
     *
     * @param tenantPermissions 选完商家后这个人的租户权限集合（{@code LoginUser#getTenantPermissions()}）
     * @param platformAdmin     是否平台超级管理员
     */
    StationSite resolveDirectConsoleSite(String shopCode, java.util.Set<String> tenantPermissions, boolean platformAdmin);
}

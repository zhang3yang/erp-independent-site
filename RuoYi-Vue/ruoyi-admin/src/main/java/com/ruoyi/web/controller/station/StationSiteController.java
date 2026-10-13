package com.ruoyi.web.controller.station;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.system.domain.StationSite;
import com.ruoyi.system.service.IStationConsoleSsoService;
import com.ruoyi.system.service.IStationSiteService;

/**
 * 独立站管理。<b>这是一张登记表，不是一个后台入口。</b>
 *
 * <p>docs/独立站与ERP的边界.md §7：它只回答「我们有哪些站、在哪、怎么进」，
 * 不含站里的任何业务数据。商品、询盘、装修、文件都在各自那台站点服务器上。</p>
 *
 * <h3>⚠ 删掉的两个端点</h3>
 * <ul>
 *   <li>{@code POST /{siteCode}/enter} —— 进 ERP 内置管理台的那道闸。
 *       内置管理台整个删了（§10.1：{@code views/site/console/} 三个 vue 和
 *       {@code /station-console/:siteCode} 那整块路由）；</li>
 *   <li>{@code PUT /{siteCode}/content} —— 首页装修。搬到站点后端了（§7.2），
 *       现在在那边的管理台里改。</li>
 * </ul>
 *
 * <h3>换掉的一个端点</h3>
 * <p>{@code POST /{siteCode}/console-ticket}（发一次性票）→
 * {@code POST /{siteCode}/console-jump}（签自包含令牌）。协议换了，
 * 理由见 {@link IStationConsoleSsoService} 的类注释。</p>
 *
 * <p>权限串与 {@code sql/01_schema_and_init.sql} 里的 fn9001~fn9005 一一对应，
 * 改一边必须改另一边，否则接口 403。</p>
 */
@RestController
@RequestMapping("/site/station")
public class StationSiteController extends BaseController
{
    @Autowired
    private IStationSiteService siteService;

    @Autowired
    private IStationConsoleSsoService consoleSsoService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:list')")
    @GetMapping("/list")
    public TableDataInfo list(StationSite query)
    {
        startPage();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(siteService.selectSiteList(query));
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:query')")
    @GetMapping("/{siteCode}")
    public AjaxResult getInfo(@PathVariable("siteCode") String siteCode)
    {
        StationSite site = siteService.selectSiteByCode(SecurityUtils.getBusinessShopCode(), siteCode);
        return site == null ? error("站点不存在") : success(site);
    }

    /**
     * 新建站点。
     *
     * <p>⚠ 返回体里带着<b>明文跳转密钥</b>（{@code data.consoleSecret}），
     * 这是它唯一一次离开服务端——装那台站点服务器时要把它填进
     * {@code u_s_siteinfo.consolesecret}。</p>
     *
     * <p>{@code isSaveResponseData = false} 因此不能去掉：{@code @Log} 默认会把响应整个
     * 写进 {@code sys_oper_log.json_result}，而那张表是永久留存的。
     * 跳转密钥明文落进操作日志，等于任何一个能查操作日志的人都能伪造进任意站点的令牌。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:add')")
    @Log(title = "独立站登记", businessType = BusinessType.INSERT, isSaveResponseData = false)
    @PostMapping
    public AjaxResult add(@RequestBody StationSite site)
    {
        site.setShopCode(SecurityUtils.getBusinessShopCode());
        site.setCreateUser(SecurityUtils.getUsername());
        StationSite created = siteService.insertSite(site);
        return AjaxResult.success("站点登记成功：" + created.getSiteCode(), created);
    }

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:edit')")
    @Log(title = "独立站登记", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody StationSite site)
    {
        site.setShopCode(SecurityUtils.getBusinessShopCode());
        site.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(siteService.updateSite(site));
    }

    /**
     * 重置跳转密钥。
     *
     * <p>⚠ <b>换完之后必须同步改那台站点服务器上的 {@code u_s_siteinfo.consolesecret}</b>，
     * 否则那个站的「进入管理」立刻失效——而前台和站点本地登录都不受影响，
     * 所以这个故障看起来会像「只有跳转坏了」，很容易查错方向。</p>
     *
     * <p>{@code isSaveResponseData = false}：响应体里是明文密钥，理由同 {@link #add}。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:edit')")
    @Log(title = "重置独立站跳转密钥", businessType = BusinessType.UPDATE, isSaveResponseData = false)
    @PostMapping("/{siteCode}/secret")
    public AjaxResult rotateSecret(@PathVariable("siteCode") String siteCode)
    {
        String secret = siteService.rotateSecret(SecurityUtils.getBusinessShopCode(), siteCode,
                SecurityUtils.getUsername());
        return AjaxResult.success("已生成新密钥，请立刻填进那台站点服务器的 u_s_siteinfo.consolesecret", secret);
    }

    /**
     * 签一张进站点管理台的跳转令牌。
     *
     * <p>前端拿到 {@code {action, token}} 之后，造一个隐藏 form（{@code method=POST}、
     * {@code target=_blank}）提交过去。<b>不要拼进 URL</b>——令牌本身就是凭据，
     * 进了 URL 就会进浏览器历史、书签、nginx 日志、Referer（§4.2）。</p>
     *
     * <p>权限用 {@code site:station:list}：能看见登记表的人就该进得去。
     * 进去之后能干什么，由<b>站点那边自己的角色配置</b>说了算——
     * 令牌里没有权限码，这是有意的（§4.3）。</p>
     *
     * <p>它是 POST 只因为要打一次访问时间，不是表单提交。前端豁免了防重复提交——
     * 同一个站「进去→返回→再进去」是常态，被当成重复提交拦下来就进不去了。</p>
     *
     * <p><b>{@code isSaveResponseData = false} 不能去掉。</b>响应体里就是那个令牌，
     * 而 {@code sys_oper_log.json_result} 是永久留存的。令牌只活 60 秒、用一次就废，
     * 落进日志表的危害有限，但这套设计的全部前提就是「令牌不落任何长期存储」，
     * 破一次口子，下一个人就会觉得再破一次也没关系。请求参数照常记录：
     * 那里只有 siteCode，正是审计要的东西。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:list')")
    @Log(title = "进入独立站管理台", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @PostMapping("/{siteCode}/console-jump")
    public AjaxResult consoleJump(@PathVariable("siteCode") String siteCode)
    {
        return success(consoleSsoService.issueJump(SecurityUtils.getBusinessShopCode(), siteCode,
                SecurityUtils.getLoginUser(), IpUtils.getIpAddr()));
    }

    /**
     * 管理站点用户：签一张带 {@code scope=users} 的跳转令牌（2026-09-24）。
     *
     * <p>站点管理台的「用户管理」菜单拿掉了（那一页全是 ERP 登录名和「ERP 免密」，
     * 不该给海外客户看），唯一入口挪到这里。站点只给带这个用途进来的会话开用户接口，
     * 所以「谁能调站点里的角色、停用人」由这里的 {@code site:station:edit} 决定。</p>
     *
     * <p>和 console-jump 一样不存响应体（里面是令牌），理由见上面那个方法。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:edit')")
    @Log(title = "管理独立站用户", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @PostMapping("/{siteCode}/users-jump")
    public AjaxResult usersJump(@PathVariable("siteCode") String siteCode)
    {
        return success(consoleSsoService.issueJump(SecurityUtils.getBusinessShopCode(), siteCode,
                SecurityUtils.getLoginUser(), IpUtils.getIpAddr(), IStationConsoleSsoService.SCOPE_USERS));
    }

    /**
     * 删除登记。
     *
     * <p>⚠ 删的只是 ERP 这条登记记录。<b>那台站点服务器上的库、商品、询盘都还在</b>——
     * 要真正下线一个站，是去关那台机器。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('site:station:remove')")
    @Log(title = "独立站登记", businessType = BusinessType.DELETE)
    @DeleteMapping("/{siteCodes}")
    public AjaxResult remove(@PathVariable("siteCodes") String[] siteCodes)
    {
        return toAjax(siteService.deleteSiteByCodes(SecurityUtils.getBusinessShopCode(), siteCodes));
    }
}

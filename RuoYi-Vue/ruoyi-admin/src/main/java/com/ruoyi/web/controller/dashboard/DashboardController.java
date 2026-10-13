package com.ruoyi.web.controller.dashboard;

import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.vo.DashboardScope;
import com.ruoyi.system.domain.vo.DashboardSummary;
import com.ruoyi.system.service.IDashboardService;

/**
 * 首页经营看板。
 *
 * <p>单独开一个包而不是挂在 inventory 或 finance 下：看板的数据横跨库存、单据、订单、
 * 采购、生产和财务，放进任何一个业务包都会让人以为它只统计那一块。</p>
 *
 * <p>⚠ 权限只写 {@code @ss.hasPermi}，<b>不加</b> {@code @ss.hasBusinessShop()}。
 * 首页是所有登录用户进来的第一屏，接口一旦抛异常，整个首页就会弹一个红条。
 * 商家上下文缺失在这里是正常状态（管理员还没选商家），不是错误，所以
 * {@link #currentShopCode()} 把它降级成空数据。没有权限的用户拿到的是 403，
 * 由前端 {@code .catch} 静默吞掉，对齐库存预警角标已有的做法。</p>
 *
 * <p>{@code dashboard:overview:query} 管的只是「看不看得见看板这块地方」。看板上每一块
 * 数据各自属于哪个模块、要不要额外的权限，由 {@link DashboardScope} 决定——
 * 那份判断只有一处，控制器不重复写。</p>
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController extends BaseController
{
    @Autowired
    private IDashboardService dashboardService;

    @PreAuthorize("@ss.hasPermi('dashboard:overview:query')")
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        String shopCode = currentShopCode();
        return shopCode == null
                ? success(new DashboardSummary())
                : success(dashboardService.selectSummary(shopCode, DashboardScope.current()));
    }

    /**
     * 趋势数据。
     *
     * @param months 回看月数，服务层会夹到 1..24
     */
    @PreAuthorize("@ss.hasPermi('dashboard:overview:query')")
    @GetMapping("/trend")
    public AjaxResult trend(@RequestParam(defaultValue = "6") int months)
    {
        String shopCode = currentShopCode();
        return shopCode == null
                ? success(Collections.emptyList())
                : success(dashboardService.selectTrend(shopCode, months, DashboardScope.current()));
    }

    /**
     * 当前商家，解析不出来时返回 null。
     *
     * <p>这里捕获异常而不是自己判一遍 shopCode，是因为
     * {@link SecurityUtils#getBusinessShopCode()} 里还有管理员走请求头、以及那个头的
     * 格式校验。照抄一份就等于多了一处会和它走偏的逻辑；用 try 把「取不到」这一种
     * 情况降级，解析规则始终只有那一份。</p>
     */
    private String currentShopCode()
    {
        try
        {
            return SecurityUtils.getBusinessShopCode();
        }
        catch (ServiceException e)
        {
            return null;
        }
    }
}

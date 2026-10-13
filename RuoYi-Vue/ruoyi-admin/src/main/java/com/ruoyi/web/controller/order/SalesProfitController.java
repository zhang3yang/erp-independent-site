package com.ruoyi.web.controller.order;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISalesProfitService;

/**
 * 销售利润报表Controller
 *
 * <p>利润 = 售价 − 成本，所以除了菜单权限，还要同时有 price:sale 和 price:cost。
 * 口径见 SalesProfitServiceImpl 类注释。</p>
 */
@RestController
@RequestMapping("/order/profit")
public class SalesProfitController extends BaseController
{
    @Autowired
    private ISalesProfitService profitService;

    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('order:profit:list') "
            + "and @ss.hasPermi('price:sale') and @ss.hasPermi('price:cost')")
    @GetMapping("/report")
    public AjaxResult report(@RequestParam("begin") @DateTimeFormat(pattern = "yyyy-MM-dd") Date begin,
            @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd") Date end,
            @RequestParam(value = "dimension", required = false) String dimension,
            @RequestParam(value = "filterDim", required = false) String filterDim,
            @RequestParam(value = "filterKey", required = false) String filterKey)
    {
        return success(profitService.report(SecurityUtils.getBusinessShopCode(), begin, end, dimension,
                filterDim, filterKey));
    }
}

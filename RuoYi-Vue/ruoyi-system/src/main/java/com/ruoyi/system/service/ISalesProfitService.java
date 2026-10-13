package com.ruoyi.system.service;

import java.util.Date;
import com.ruoyi.system.domain.vo.SalesProfitReport;

/**
 * 销售利润报表Service接口
 */
public interface ISalesProfitService
{
    /**
     * @param begin 发货日期起（含）
     * @param end 发货日期止（含）
     * @param dimension 按什么汇总：customer / staff / country / goods / store / month / order
     * @param filterDim 下钻：只看某个维度等于 filterKey 的数据，可空
     * @param filterKey 下钻的取值
     */
    SalesProfitReport report(String shopCode, Date begin, Date end, String dimension, String filterDim, String filterKey);
}

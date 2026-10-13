package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.ExchangeRate;

/**
 * 汇率Service接口
 */
public interface IExchangeRateService
{
    /** 本位币，所有成本都按它记账 */
    String BASE_CURRENCY = "CNY";

    public List<ExchangeRate> selectRateList(ExchangeRate query);

    public ExchangeRate selectRateById(String shopCode, Long id);

    public int insertRate(ExchangeRate rate);

    public int updateRate(ExchangeRate rate);

    public int deleteRateById(String shopCode, Long id);

    /** 一次把商家的全部汇率读进内存，报表逐笔折算时不再查库 */
    public RateBook loadRateBook(String shopCode);

    /**
     * 某个商家的全部汇率，按币种、生效日期排好。
     */
    class RateBook
    {
        private final Map<String, List<ExchangeRate>> byCurrency = new HashMap<>();

        public RateBook(List<ExchangeRate> rates)
        {
            for (ExchangeRate rate : rates)
            {
                byCurrency.computeIfAbsent(rate.getCurrency(), k -> new ArrayList<>()).add(rate);
            }
            for (List<ExchangeRate> list : byCurrency.values())
            {
                list.sort((a, b) -> a.getEffectiveDate().compareTo(b.getEffectiveDate()));
            }
        }

        /**
         * 某天 1 单位外币值多少人民币：取生效日期不晚于这天的最后一条。
         *
         * @return 本位币恒为 1；这天之前从没录过汇率时返回 null，调用方要把这笔单独拎出来，不能当 1 算
         */
        public BigDecimal rateOn(String currency, Date day)
        {
            if (currency == null || BASE_CURRENCY.equalsIgnoreCase(currency))
            {
                return BigDecimal.ONE;
            }
            List<ExchangeRate> list = byCurrency.get(currency.toUpperCase());
            if (list == null || day == null)
            {
                return null;
            }
            BigDecimal found = null;
            for (ExchangeRate rate : list)
            {
                if (rate.getEffectiveDate().after(day))
                {
                    break;
                }
                found = rate.getRate();
            }
            return found;
        }
    }
}

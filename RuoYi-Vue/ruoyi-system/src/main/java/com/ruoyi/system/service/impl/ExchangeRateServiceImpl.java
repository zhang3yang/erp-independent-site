package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ExchangeRate;
import com.ruoyi.system.mapper.ExchangeRateMapper;
import com.ruoyi.system.service.IExchangeRateService;

/**
 * 汇率Service业务层处理
 *
 * <p>一条汇率只说「从哪天起 1 外币 = 多少人民币」。改汇率不是改旧的那条，而是新加一条
 * 更晚生效的——旧的留着，之前发出去的单照旧按旧汇率算利润，报表前后才对得上。
 * 录错了可以直接改或删。</p>
 */
@Service
public class ExchangeRateServiceImpl implements IExchangeRateService
{
    @Autowired
    private ExchangeRateMapper rateMapper;

    @Autowired
    private StaffNameResolver staffNameResolver;

    @Override
    public List<ExchangeRate> selectRateList(ExchangeRate query)
    {
        List<ExchangeRate> list = rateMapper.selectRateList(query);
        if (list != null && !list.isEmpty())
        {
            List<String> logins = new ArrayList<>();
            for (ExchangeRate rate : list)
            {
                logins.add(rate.getCreateUser());
            }
            Map<String, String> names = staffNameResolver.nickNamesByLogin(logins);
            for (ExchangeRate rate : list)
            {
                String login = rate.getCreateUser();
                rate.setCreateUserName(login == null ? null : names.getOrDefault(login, login));
            }
        }
        return list;
    }

    @Override
    public ExchangeRate selectRateById(String shopCode, Long id)
    {
        return rateMapper.selectRateById(shopCode, id);
    }

    @Override
    public int insertRate(ExchangeRate rate)
    {
        normalize(rate);
        try
        {
            return rateMapper.insertRate(rate);
        }
        catch (DuplicateKeyException e)
        {
            throw duplicate(rate);
        }
    }

    @Override
    public int updateRate(ExchangeRate rate)
    {
        if (rate.getId() == null || rateMapper.selectRateById(rate.getShopCode(), rate.getId()) == null)
        {
            throw new ServiceException("汇率记录不存在，请刷新后重试");
        }
        normalize(rate);
        try
        {
            return rateMapper.updateRate(rate);
        }
        catch (DuplicateKeyException e)
        {
            throw duplicate(rate);
        }
    }

    @Override
    public int deleteRateById(String shopCode, Long id)
    {
        return rateMapper.deleteRateById(shopCode, id);
    }

    @Override
    public RateBook loadRateBook(String shopCode)
    {
        ExchangeRate query = new ExchangeRate();
        query.setShopCode(shopCode);
        return new RateBook(rateMapper.selectRateList(query));
    }

    private void normalize(ExchangeRate rate)
    {
        String currency = CurrencySupport.require(rate.getCurrency());
        if (BASE_CURRENCY.equals(currency))
        {
            throw new ServiceException("人民币是本位币，不用录汇率");
        }
        rate.setCurrency(currency);
        if (rate.getRate() == null || rate.getRate().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("汇率必须大于 0，填 1 " + currency + " 等于多少人民币");
        }
        if (rate.getEffectiveDate() == null)
        {
            throw new ServiceException("请选择生效日期");
        }
        if (rate.getRemark() != null && rate.getRemark().trim().isEmpty())
        {
            rate.setRemark(null);
        }
    }

    private ServiceException duplicate(ExchangeRate rate)
    {
        return new ServiceException(rate.getCurrency() + " 在这一天已经有一条汇率了，请直接修改那一条");
    }
}

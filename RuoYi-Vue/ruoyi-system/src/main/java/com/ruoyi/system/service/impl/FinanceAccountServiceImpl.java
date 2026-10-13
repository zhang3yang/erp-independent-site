package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.FinanceAccount;
import com.ruoyi.system.mapper.FinanceAccountMapper;
import com.ruoyi.system.mapper.FinanceTransactionMapper;
import com.ruoyi.system.service.IFinanceAccountService;

/**
 * 账户信息服务实现
 */
@Service
public class FinanceAccountServiceImpl implements IFinanceAccountService
{
    @Autowired
    private FinanceAccountMapper accountMapper;

    @Autowired
    private FinanceTransactionMapper transactionMapper;

    @Override
    public List<FinanceAccount> selectAccountList(FinanceAccount account)
    {
        return accountMapper.selectAccountList(account);
    }

    @Override
    public FinanceAccount selectAccountByCode(String shopCode, String accountCode)
    {
        if (StringUtils.isEmpty(accountCode))
        {
            return null;
        }
        return accountMapper.selectAccountByCode(shopCode, accountCode.trim());
    }

    @Override
    public int insertAccount(FinanceAccount account)
    {
        normalize(account);
        if (accountMapper.checkAccountCodeUnique(account.getShopCode(), account.getAccountCode()) > 0)
        {
            throw new ServiceException("账户代码 " + account.getAccountCode() + " 已存在");
        }
        // 余额一律从 0 开始，由资金流水驱动。开户时的期初余额请录一笔「其他收入」流水，
        // 这样账面余额的每一分钱都能在流水里找到出处。
        account.setCurrentBalance(BigDecimal.ZERO);
        return accountMapper.insertAccount(account);
    }

    @Override
    public int updateAccount(FinanceAccount account)
    {
        normalize(account);
        FinanceAccount existing = accountMapper.selectAccountById(account.getShopCode(), account.getId());
        if (existing == null)
        {
            throw new ServiceException("账户不存在");
        }
        // 账户代码是流水表里的引用键，改了会让历史流水指向不存在的账户
        if (!existing.getAccountCode().equals(account.getAccountCode()))
        {
            throw new ServiceException("账户代码不能修改；如需调整请停用后新建账户");
        }
        if (!CurrencySupport.require(existing.getCurrency()).equals(account.getCurrency())
                && ((existing.getCurrentBalance() != null
                    && existing.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0)
                    || hasTransaction(account.getShopCode(), existing.getAccountCode())))
        {
            throw new ServiceException("账户已有余额或资金流水，币种不能修改；请停用后新建账户");
        }
        return accountMapper.updateAccount(account);
    }

    @Override
    public int deleteAccountByIds(String shopCode, Long[] ids)
    {
        for (Long id : ids)
        {
            FinanceAccount existing = accountMapper.selectAccountById(shopCode, id);
            if (existing == null)
            {
                throw new ServiceException("账户不存在");
            }
            if (existing.getCurrentBalance() != null
                    && existing.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0)
            {
                throw new ServiceException("账户「" + existing.getAccountName()
                        + "」余额不为 0，不能删除；如需停用请把状态改为停用");
            }
            if (hasTransaction(shopCode, existing.getAccountCode()))
            {
                throw new ServiceException("账户「" + existing.getAccountName()
                        + "」已产生资金流水，不能删除；如需停用请把状态改为停用");
            }
        }
        return accountMapper.deleteAccountByIds(shopCode, ids);
    }

    /** 账户下是否已有任意月份的流水。删除前的兜底，防止历史流水指向不存在的账户。 */
    private boolean hasTransaction(String shopCode, String accountCode)
    {
        for (String suffix : FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes()))
        {
            if (transactionMapper.countByAccount(shopCode, accountCode, suffix) > 0)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 入参归一：去首尾空白 + 必填 + 长度上限。
     * 长度上限对应 sdsshop.u_f_account 各列的 varchar 宽度。
     */
    private void normalize(FinanceAccount account)
    {
        account.setAccountCode(FinanceText.require(account.getAccountCode(), "账户代码", 50));
        account.setAccountName(FinanceText.require(account.getAccountName(), "账户简称", 50));
        account.setAccountFullName(FinanceText.require(account.getAccountFullName(), "账户全称", 100));
        account.setAccountNo(FinanceText.require(account.getAccountNo(), "银行账号", 50));
        account.setBankName(FinanceText.require(account.getBankName(), "开户行名称", 100));
        account.setCurrency(CurrencySupport.require(account.getCurrency()));
        account.setRemark(FinanceText.optional(account.getRemark(), "备注", 500));
        if (account.getIsActive() == null)
        {
            account.setIsActive(1);
        }
    }
}

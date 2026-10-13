package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.FinanceAccount;

/**
 * 账户信息服务（说明书 5.1 核心职责：账户信息管理）
 */
public interface IFinanceAccountService
{
    List<FinanceAccount> selectAccountList(FinanceAccount account);

    FinanceAccount selectAccountByCode(String shopCode, String accountCode);

    int insertAccount(FinanceAccount account);

    int updateAccount(FinanceAccount account);

    int deleteAccountByIds(String shopCode, Long[] ids);
}

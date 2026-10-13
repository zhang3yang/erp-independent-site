package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.FinanceAccount;

/**
 * 账户信息 Mapper
 */
public interface FinanceAccountMapper
{
    List<FinanceAccount> selectAccountList(FinanceAccount account);

    FinanceAccount selectAccountByCode(@Param("shopCode") String shopCode,
            @Param("accountCode") String accountCode);

    FinanceAccount selectAccountById(@Param("shopCode") String shopCode, @Param("id") Long id);

    int checkAccountCodeUnique(@Param("shopCode") String shopCode,
            @Param("accountCode") String accountCode);

    int insertAccount(FinanceAccount account);

    /** 通用更新。不含 currentbalance，余额只能走 increaseBalance。 */
    int updateAccount(FinanceAccount account);

    int deleteAccountByIds(@Param("shopCode") String shopCode, @Param("ids") Long[] ids);

    /**
     * 账户余额自增。amount 收入为正、支出为负，直接相加即可。
     *
     * <p>用 SQL 内自增而不是「读出来算完再写回」，同一账户并发完成多笔流水时不会丢数。</p>
     */
    int increaseBalance(@Param("shopCode") String shopCode,
            @Param("accountCode") String accountCode, @Param("amount") BigDecimal amount);
}

package com.ruoyi.system.service.impl;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.FinanceCategory;
import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.mapper.FinanceCategoryMapper;
import com.ruoyi.system.mapper.FinanceTransactionMapper;
import com.ruoyi.system.service.IFinanceCategoryService;

/**
 * 收支分类服务实现
 */
@Service
public class FinanceCategoryServiceImpl implements IFinanceCategoryService
{
    /** 收支分类的业务类型，与资金流水的 BIZ_TYPES 一一对应。 */
    private static final List<String> BIZ_TYPES = Arrays.asList("采购", "销售", "其他");

    @Autowired
    private FinanceCategoryMapper categoryMapper;

    @Autowired
    private FinanceTransactionMapper transactionMapper;

    @Override
    public List<FinanceCategory> selectCategoryList(FinanceCategory category)
    {
        return categoryMapper.selectCategoryList(category);
    }

    @Override
    public FinanceCategory selectCategoryByCode(String shopCode, String categoryCode)
    {
        if (StringUtils.isEmpty(categoryCode))
        {
            return null;
        }
        return categoryMapper.selectCategoryByCode(shopCode, categoryCode.trim());
    }

    @Override
    public int insertCategory(FinanceCategory category)
    {
        normalize(category);
        if (categoryMapper.checkCategoryCodeUnique(category.getShopCode(), category.getCategoryCode()) > 0)
        {
            throw new ServiceException("分类代码 " + category.getCategoryCode() + " 已存在");
        }
        return categoryMapper.insertCategory(category);
    }

    @Override
    public int updateCategory(FinanceCategory category)
    {
        category.setCategoryCode(FinanceText.require(category.getCategoryCode(), "分类代码", 30));
        FinanceCategory existing = categoryMapper.selectCategoryByCode(
                category.getShopCode(), category.getCategoryCode());
        if (existing == null || !existing.getId().equals(category.getId()))
        {
            throw new ServiceException("分类不存在；分类代码不能修改，如需调整请停用后新建");
        }
        normalize(category);
        // 收支方向不能改：已完成的流水按方向决定了余额的加减，改了会让历史流水的方向和分类对不上
        if (!existing.getType().equals(category.getType()))
        {
            throw new ServiceException("分类的收支方向不能修改；如需调整请停用后新建分类");
        }
        return categoryMapper.updateCategory(category);
    }

    @Override
    public int deleteCategoryByIds(String shopCode, Long[] ids)
    {
        for (Long id : ids)
        {
            FinanceCategory existing = findById(shopCode, id);
            if (existing == null)
            {
                throw new ServiceException("分类不存在");
            }
            if (FinanceCategory.isReservedOtherCode(existing.getCategoryCode()))
            {
                throw new ServiceException("系统分类「" + existing.getCategoryName() + "」不能删除");
            }
            if (hasTransaction(shopCode, existing.getCategoryCode()))
            {
                throw new ServiceException("分类「" + existing.getCategoryName()
                        + "」已被资金流水引用，不能删除；如需停用请把状态改为停用");
            }
        }
        return categoryMapper.deleteCategoryByIds(shopCode, ids);
    }

    private FinanceCategory findById(String shopCode, Long id)
    {
        FinanceCategory query = new FinanceCategory();
        query.setShopCode(shopCode);
        for (FinanceCategory item : categoryMapper.selectCategoryList(query))
        {
            if (item.getId().equals(id))
            {
                return item;
            }
        }
        return null;
    }

    private boolean hasTransaction(String shopCode, String categoryCode)
    {
        for (String suffix : FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes()))
        {
            if (transactionMapper.countByCategory(shopCode, categoryCode, suffix) > 0)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 入参归一：去首尾空白 + 必填 + 长度上限。
     * 长度上限对应 sdsorder.u_f_accountcategory 各列的 varchar 宽度。
     */
    private void normalize(FinanceCategory category)
    {
        category.setCategoryCode(FinanceText.require(category.getCategoryCode(), "分类代码", 30));
        category.setCategoryName(FinanceText.require(category.getCategoryName(), "分类名称", 50));
        String type = FinanceText.require(category.getType(), "收支方向", 10);
        if (!FinanceTransaction.TYPE_INCOME.equals(type) && !FinanceTransaction.TYPE_EXPENSE.equals(type))
        {
            throw new ServiceException("收支方向只能是「收入」或「支出」");
        }
        category.setType(type);

        String bizType = FinanceText.require(category.getBizType(), "业务类型", 10);
        if (!BIZ_TYPES.contains(bizType))
        {
            throw new ServiceException("业务类型只能是：" + String.join("、", BIZ_TYPES));
        }
        category.setBizType(bizType);

        category.setRemark(FinanceText.optional(category.getRemark(), "备注", 200));

        if (category.getIsActive() == null)
        {
            category.setIsActive(1);
        }

        // 「其他」是系统固定的一收一支两条分类：不能自建第三条，也不能改名、改方向或停用。
        // 再按分类代码兜一道，是防止把 QT_ZC/QT_SR 挪到别的业务类型下绕开这里的限制。
        if (FinanceTransaction.BIZ_OTHER.equals(bizType)
                || FinanceCategory.isReservedOtherCode(category.getCategoryCode()))
        {
            checkReservedOther(category);
        }
    }

    /**
     * 校验业务类型「其他」的两条系统分类。
     *
     * <p>分类代码、名称、收支方向、业务类型和启用状态都是固定的，只有备注能改：其他这类
     * 非业务流水不按用途铺分类，用途写在流水的业务摘要或备注里。</p>
     */
    private void checkReservedOther(FinanceCategory category)
    {
        String code = category.getCategoryCode();
        if (!FinanceCategory.isReservedOtherCode(code))
        {
            throw new ServiceException("业务类型「其他」只能使用系统分类 "
                    + FinanceCategory.OTHER_EXPENSE_CODE + "（" + FinanceCategory.OTHER_EXPENSE_NAME + "）和 "
                    + FinanceCategory.OTHER_INCOME_CODE + "（" + FinanceCategory.OTHER_INCOME_NAME + "）");
        }
        String expectedName = FinanceCategory.reservedOtherName(code);
        String expectedType = FinanceCategory.reservedOtherType(code);
        if (!expectedName.equals(category.getCategoryName())
                || !expectedType.equals(category.getType())
                || !FinanceTransaction.BIZ_OTHER.equals(category.getBizType())
                || !Integer.valueOf(1).equals(category.getIsActive()))
        {
            throw new ServiceException("系统分类 " + code + " 必须保持为启用的「" + expectedName
                    + "」" + expectedType + "分类，只能修改备注");
        }
    }
}

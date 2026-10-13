package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.FinanceCategory;

/**
 * 收支分类服务（说明书 5.1 核心职责：收支分类管理）
 */
public interface IFinanceCategoryService
{
    List<FinanceCategory> selectCategoryList(FinanceCategory category);

    FinanceCategory selectCategoryByCode(String shopCode, String categoryCode);

    int insertCategory(FinanceCategory category);

    int updateCategory(FinanceCategory category);

    int deleteCategoryByIds(String shopCode, Long[] ids);
}

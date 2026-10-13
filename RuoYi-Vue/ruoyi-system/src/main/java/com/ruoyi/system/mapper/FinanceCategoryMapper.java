package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.FinanceCategory;

/**
 * 收支分类 Mapper
 */
public interface FinanceCategoryMapper
{
    List<FinanceCategory> selectCategoryList(FinanceCategory category);

    FinanceCategory selectCategoryByCode(@Param("shopCode") String shopCode,
            @Param("categoryCode") String categoryCode);

    int checkCategoryCodeUnique(@Param("shopCode") String shopCode,
            @Param("categoryCode") String categoryCode);

    int insertCategory(FinanceCategory category);

    int updateCategory(FinanceCategory category);

    int deleteCategoryByIds(@Param("shopCode") String shopCode, @Param("ids") Long[] ids);
}

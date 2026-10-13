package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.FinanceCategory;
import com.ruoyi.system.mapper.FinanceCategoryMapper;
import com.ruoyi.system.mapper.FinanceTransactionMapper;

@ExtendWith(MockitoExtension.class)
class FinanceCategoryServiceImplTest
{
    private static final String SHOP = "sp001";

    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private FinanceTransactionMapper transactionMapper;

    @InjectMocks private FinanceCategoryServiceImpl service;

    private FinanceCategory existing;

    @BeforeEach
    void setUp()
    {
        existing = otherCategory();
        lenient().when(categoryMapper.selectCategoryByCode(SHOP, "QT_ZC")).thenReturn(existing);
    }

    @Test
    void update_系统其他分类可以修改备注()
    {
        FinanceCategory update = otherCategory();
        update.setRemark("日常杂项统一记录");

        service.updateCategory(update);

        verify(categoryMapper).updateCategory(update);
    }

    @Test
    void update_系统其他分类不能改名()
    {
        FinanceCategory update = otherCategory();
        update.setCategoryName("杂项");

        assertThrows(ServiceException.class, () -> service.updateCategory(update));

        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    void update_系统其他分类不能改业务类型()
    {
        FinanceCategory update = otherCategory();
        update.setBizType("采购");

        assertThrows(ServiceException.class, () -> service.updateCategory(update));

        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    void update_系统其他分类不能停用()
    {
        FinanceCategory update = otherCategory();
        update.setIsActive(0);

        assertThrows(ServiceException.class, () -> service.updateCategory(update));

        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    void update_系统其他收入分类可以修改备注()
    {
        FinanceCategory income = otherIncomeCategory();
        when(categoryMapper.selectCategoryByCode(SHOP, "QT_SR")).thenReturn(income);

        FinanceCategory update = otherIncomeCategory();
        update.setRemark("利息、赔款、退税等非业务收入");

        service.updateCategory(update);

        verify(categoryMapper).updateCategory(update);
    }

    @Test
    void update_系统其他收入分类不能停用()
    {
        FinanceCategory income = otherIncomeCategory();
        when(categoryMapper.selectCategoryByCode(SHOP, "QT_SR")).thenReturn(income);

        FinanceCategory update = otherIncomeCategory();
        update.setIsActive(0);

        assertThrows(ServiceException.class, () -> service.updateCategory(update));

        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    void insert_其他业务不能自建第三条分类()
    {
        FinanceCategory category = otherCategory(null, "QT_CL", "差旅", "支出");

        assertThrows(ServiceException.class, () -> service.insertCategory(category));

        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    void insert_已删除的费用和工资业务类型被拒绝()
    {
        FinanceCategory fee = otherCategory(null, "FY_BG", "办公费用", "支出");
        fee.setBizType("费用");
        assertThrows(ServiceException.class, () -> service.insertCategory(fee));

        FinanceCategory salary = otherCategory(null, "GZ_XC", "员工薪酬", "支出");
        salary.setBizType("工资");
        assertThrows(ServiceException.class, () -> service.insertCategory(salary));

        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    void delete_系统其他分类不能删除()
    {
        when(categoryMapper.selectCategoryList(any())).thenReturn(Arrays.asList(existing));

        assertThrows(ServiceException.class,
                () -> service.deleteCategoryByIds(SHOP, new Long[] { existing.getId() }));

        verify(transactionMapper, never()).selectTransactionTableSuffixes();
        verify(categoryMapper, never()).deleteCategoryByIds(any(), any());
    }

    private FinanceCategory otherCategory()
    {
        return otherCategory(1L, "QT_ZC", "其他支出", "支出");
    }

    private FinanceCategory otherIncomeCategory()
    {
        return otherCategory(2L, "QT_SR", "其他收入", "收入");
    }

    private FinanceCategory otherCategory(Long id, String code, String name, String type)
    {
        FinanceCategory category = new FinanceCategory();
        category.setId(id);
        category.setShopCode(SHOP);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setType(type);
        category.setBizType("其他");
        category.setIsActive(1);
        return category;
    }
}

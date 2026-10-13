package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.InventoryGoodsCategory;

/**
 * 商品分类Service接口
 *
 * <p>页面与功能对齐「系统管理 → 部门管理」，方法命名也一一对应。</p>
 */
public interface IInventoryGoodsCategoryService
{
    /** 分类树，children 已经拼好，供商品页的分类下拉使用 */
    public List<InventoryGoodsCategory> selectCategoryTree(String shopCode, String categoryName, String status);

    /** 平铺的分类列表，分类维护页拿去自己拼树 */
    public List<InventoryGoodsCategory> selectCategoryList(String shopCode, String categoryName, String status);

    /** 平铺的分类列表，排除自己和自己的所有下级，供「上级分类」下拉使用 */
    public List<InventoryGoodsCategory> selectCategoryListExcludeChild(String shopCode, String categoryCode);

    public InventoryGoodsCategory selectCategoryByCode(String shopCode, String categoryCode);

    /** 该分类下处于正常状态的下级分类数，停用前要看 */
    public int selectNormalChildrenCount(String shopCode, String categoryCode);

    /** 是否存在下级分类 */
    public boolean hasChildByCategoryCode(String shopCode, String categoryCode);

    /** 该分类下是否还挂着商品 */
    public boolean checkCategoryExistGoods(String shopCode, String categoryCode);

    /** 分类名称是否唯一，true 表示可用 */
    public boolean checkCategoryNameUnique(InventoryGoodsCategory category);

    public int insertCategory(InventoryGoodsCategory category);

    public int updateCategory(InventoryGoodsCategory category);

    /** 批量保存显示排序 */
    public void updateCategorySort(String shopCode, String[] categoryCodes, String[] orderNums);

    public int deleteCategoryByCode(String shopCode, String categoryCode);
}

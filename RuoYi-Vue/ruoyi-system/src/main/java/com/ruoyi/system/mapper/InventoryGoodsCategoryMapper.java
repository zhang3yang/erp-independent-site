package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryGoodsCategory;

/**
 * 商品分类Mapper接口
 */
public interface InventoryGoodsCategoryMapper
{
    /** 查询商家下的全部分类，按上级和排序返回，便于前端拼树 */
    public List<InventoryGoodsCategory> selectCategoryList(@Param("shopCode") String shopCode,
            @Param("categoryName") String categoryName, @Param("status") String status);

    public InventoryGoodsCategory selectCategoryByCode(@Param("shopCode") String shopCode,
            @Param("categoryCode") String categoryCode);

    /** 按名称查分类，商品保存时校验分类是否存在、是否已停用 */
    public InventoryGoodsCategory selectCategoryByName(@Param("shopCode") String shopCode,
            @Param("categoryName") String categoryName);

    /** 名称是否已被别的分类占用，excludeCode 用于修改时排除自己 */
    public int checkCategoryNameUnique(@Param("shopCode") String shopCode,
            @Param("categoryName") String categoryName, @Param("excludeCode") String excludeCode);

    public int countChildren(@Param("shopCode") String shopCode, @Param("parentCode") String parentCode);

    /** 启用分类时，把它的所有上级分类一并启用 */
    public int updateCategoryStatusNormal(@Param("shopCode") String shopCode,
            @Param("categoryCodes") List<String> categoryCodes);

    /** 保存排序，只改显示排序，不动其它字段 */
    public int updateCategorySort(@Param("shopCode") String shopCode,
            @Param("categoryCode") String categoryCode, @Param("orderNum") Integer orderNum);

    /** 该分类名下还挂着多少商品，删除和改名时都要看 */
    public int countGoodsByCategoryName(@Param("shopCode") String shopCode,
            @Param("categoryName") String categoryName);

    /** 分类改名后，把商品上的分类名一起改掉 */
    public int updateGoodsCategoryName(@Param("shopCode") String shopCode,
            @Param("oldName") String oldName, @Param("newName") String newName);

    /** 当前最大的分类编码，用于生成下一个编码 */
    public String selectMaxCategoryCode(@Param("shopCode") String shopCode);

    public int insertCategory(InventoryGoodsCategory category);

    public int updateCategory(InventoryGoodsCategory category);

    public int deleteCategoryByCode(@Param("shopCode") String shopCode,
            @Param("categoryCode") String categoryCode);
}

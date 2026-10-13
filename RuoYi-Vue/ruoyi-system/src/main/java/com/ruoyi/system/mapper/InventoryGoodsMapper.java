package com.ruoyi.system.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.vo.GoodsCategoryNode;

/**
 * 商品Mapper接口
 */
public interface InventoryGoodsMapper
{
    /**
     * 查询商品
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 商品
     */
    public InventoryGoods selectGoodsByCode(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode);

    /**
     * 查询商品列表
     *
     * @param goods 商品查询条件
     * @return 商品集合
     */
    public List<InventoryGoods> selectGoodsList(InventoryGoods goods);

    /**
     * 按「分类 + 型号」统计商品数量，分类/型号为空的统计为空串
     *
     * @param shopCode 商家编码
     * @param activeFlag 启用状态，null 表示不限
     * @return 统计结果，只填充 category、spec、goodsCount
     */
    public List<GoodsCategoryNode> selectGoodsCategoryStat(@Param("shopCode") String shopCode,
            @Param("activeFlag") Integer activeFlag);

    /**
     * 按分类树节点查询商品列表
     *
     * @param shopCode 商家编码
     * @param category 分类，null 表示不限分类，{@link GoodsCategoryNode#NONE} 表示未分类
     * @param categoryNames 分类展开后的名称集合（自身 + 全部下级分类），为空时按 category 精确匹配
     * @param spec 型号，null 表示不限型号，{@link GoodsCategoryNode#NONE} 表示无型号
     * @param goodsCode 商品编码，模糊匹配
     * @param goodsName 商品名称，模糊匹配
     * @param keyword 关键字，同时模糊匹配商品编码和名称
     * @param activeFlag 启用状态
     * @return 商品集合
     */
    public List<InventoryGoods> selectGoodsByCategoryNode(@Param("shopCode") String shopCode,
            @Param("category") String category,
            @Param("categoryNames") List<String> categoryNames,
            @Param("spec") String spec,
            @Param("goodsCode") String goodsCode,
            @Param("goodsName") String goodsName,
            @Param("keyword") String keyword,
            @Param("activeFlag") Integer activeFlag);

    /**
     * 新增商品
     *
     * @param goods 商品信息
     * @return 结果
     */
    public int insertGoods(InventoryGoods goods);

    /**
     * 修改商品
     *
     * @param goods 商品信息
     * @return 结果
     */
    public int updateGoods(InventoryGoods goods);

    /**
     * 删除商品
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 结果
     */
    public int deleteGoodsByCode(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode);

    /**
     * 批量删除商品
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码数组
     * @return 结果
     */
    public int deleteGoodsByCodes(@Param("shopCode") String shopCode,
            @Param("goodsCodes") String[] goodsCodes);

    /**
     * 把商品标记为「已被单据使用」，标记后关键属性不再允许修改
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码集合
     * @return 本次新标记的商品数
     */
    public int markGoodsUsed(@Param("shopCode") String shopCode,
            @Param("goodsCodes") Collection<String> goodsCodes);

    /**
     * 校验商品代码是否唯一
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 结果
     */
    public int checkGoodsCodeUnique(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode);
}

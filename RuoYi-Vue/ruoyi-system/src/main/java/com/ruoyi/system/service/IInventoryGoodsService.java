package com.ruoyi.system.service;

import java.util.Collection;
import java.util.List;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.vo.GoodsCategoryNode;

/**
 * 商品Service接口
 */
public interface IInventoryGoodsService
{
    /**
     * 查询商品
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 商品
     */
    public InventoryGoods selectGoodsByCode(String shopCode, String goodsCode);

    /**
     * 查询商品列表
     *
     * @param goods 商品查询条件
     * @return 商品集合
     */
    public List<InventoryGoods> selectGoodsList(InventoryGoods goods);

    /**
     * 按父分类查询时，把条件里的分类展开成「自身 + 全部下级分类」。
     * 内部会查分类表，必须在 startPage() 之前调用，否则会把分页参数用掉。
     *
     * @param goods 商品查询条件，就地改写
     */
    public void applyCategoryScope(InventoryGoods goods);

    /**
     * 把分类展开成「自身 + 全部下级分类」的名称集合。
     * 内部会查分类表，必须在 startPage() 之前调用，否则会把分页参数用掉。
     *
     * @param shopCode 商家编码
     * @param category 分类名称，空或分类表里查不到时返回 null，调用方按原值精确匹配
     * @return 分类名称集合
     */
    public List<String> expandCategoryNames(String shopCode, String category);

    /**
     * 查询商品分类树：一级为商品分类，二级为该分类下的规格型号
     *
     * @param shopCode 商家编码
     * @param activeFlag 启用状态，null 表示不限
     * @return 分类树
     */
    public List<GoodsCategoryNode> selectGoodsCategoryTree(String shopCode, Integer activeFlag);

    /**
     * 按分类树节点查询商品列表
     *
     * @param shopCode 商家编码
     * @param category 分类，null 表示不限分类，{@link GoodsCategoryNode#NONE} 表示未分类
     * @param categoryNames 分类展开后的名称集合，见 {@link #expandCategoryNames(String, String)}，
     *                      为空时按 category 精确匹配
     * @param spec 型号，null 表示不限型号，{@link GoodsCategoryNode#NONE} 表示无型号
     * @param goodsCode 商品编码，模糊匹配
     * @param goodsName 商品名称，模糊匹配
     * @param keyword 关键字，同时模糊匹配商品编码和名称
     * @param activeFlag 启用状态
     * @return 商品集合
     */
    public List<InventoryGoods> selectGoodsByCategoryNode(String shopCode, String category,
            List<String> categoryNames, String spec,
            String goodsCode, String goodsName, String keyword, Integer activeFlag);

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
     * 批量删除商品
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码数组
     * @return 结果
     */
    public int deleteGoodsByCodes(String shopCode, String[] goodsCodes);

    /**
     * 把商品标记为「已被单据使用」。标记后名称、规格型号、计量单位、商品类型、
     * 批次策略、成本计算方法不再允许修改。
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码集合
     * @return 本次新标记的商品数
     */
    public int markGoodsUsed(String shopCode, Collection<String> goodsCodes);

    /**
     * 校验商品代码是否唯一
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 结果
     */
    public boolean checkGoodsCodeUnique(String shopCode, String goodsCode);
}

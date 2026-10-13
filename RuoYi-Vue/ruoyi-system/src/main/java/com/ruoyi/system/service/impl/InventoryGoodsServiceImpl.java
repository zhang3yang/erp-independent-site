package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.PriceMaskUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryGoodsCategory;
import com.ruoyi.system.domain.vo.GoodsCategoryNode;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.mapper.InventoryGoodsCategoryMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.ProductionBomItemMapper;
import com.ruoyi.system.mapper.ProductionBomMapper;
import com.ruoyi.system.mapper.ProductionMaterialMapper;
import com.ruoyi.system.mapper.ProductionOrderDetailMapper;
import com.ruoyi.system.service.IInventoryGoodsService;
import com.ruoyi.system.utils.InventoryCostPolicy;

/**
 * 商品Service业务层处理
 */
@Service
public class InventoryGoodsServiceImpl implements IInventoryGoodsService
{
    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryGoodsCategoryMapper categoryMapper;

    /**
     * 配方和生产工单也会引用商品，但它们都不产生库存和单据，
     * {@link #isGoodsUsed} 的两条判据都查不到，只能单独查一遍。
     */
    @Autowired
    private ProductionBomMapper bomMapper;

    @Autowired
    private ProductionBomItemMapper bomItemMapper;

    @Autowired
    private ProductionOrderDetailMapper productionDetailMapper;

    @Autowired
    private ProductionMaterialMapper productionMaterialMapper;

    @Autowired
    private StaffNameResolver staffNameResolver;

    /**
     * 查询商品
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 商品
     */
    @Override
    public InventoryGoods selectGoodsByCode(String shopCode, String goodsCode)
    {
        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
        if (goods != null)
        {
            goods.setCreateUserName(staffNameResolver.nickNameOrLogin(goods.getCreateUser()));
        }
        return goods;
    }

    /**
     * 查询商品列表
     *
     * @param goods 商品查询条件
     * @return 商品集合
     */
    @Override
    public List<InventoryGoods> selectGoodsList(InventoryGoods goods)
    {
        return goodsMapper.selectGoodsList(goods);
    }

    /**
     * 按父分类查询时，把条件里的分类展开成「自身 + 全部下级分类」。
     *
     * <p>要在 startPage() 之前调用：这里会先查分类表，跑在 startPage() 之后会把分页参数用掉，
     * 商品列表就变成不分页了。</p>
     */
    @Override
    public void applyCategoryScope(InventoryGoods goods)
    {
        goods.setCategoryNames(expandCategoryNames(goods.getShopCode(), goods.getCategory()));
    }

    /**
     * 把查询条件里的分类展开成「自身 + 全部下级分类」的名称集合。
     *
     * <p>分类是一棵树、商品挂在具体的分类名上，按父分类查询时如果只匹配父分类自己，
     * 挂在子分类下的商品就查不出来。分类表里查不到这个名称（比如历史自由文本分类）时
     * 返回 null，走原来的精确匹配。</p>
     */
    @Override
    public List<String> expandCategoryNames(String shopCode, String category)
    {
        String name = StringUtils.trimToEmpty(category);
        if (StringUtils.isEmpty(name))
        {
            return null;
        }
        InventoryGoodsCategory root = categoryMapper.selectCategoryByName(shopCode, name);
        if (root == null)
        {
            return null;
        }
        Map<String, List<InventoryGoodsCategory>> childrenOf = new LinkedHashMap<String, List<InventoryGoodsCategory>>();
        for (InventoryGoodsCategory item : categoryMapper.selectCategoryList(shopCode, null, null))
        {
            List<InventoryGoodsCategory> siblings = childrenOf.get(item.getParentCode());
            if (siblings == null)
            {
                siblings = new ArrayList<InventoryGoodsCategory>();
                childrenOf.put(item.getParentCode(), siblings);
            }
            siblings.add(item);
        }
        List<String> names = new ArrayList<String>();
        List<InventoryGoodsCategory> pending = new ArrayList<InventoryGoodsCategory>();
        pending.add(root);
        // 广度优先展开，用已访问的编码兜底，防止历史脏数据成环时死循环
        Set<String> visited = new LinkedHashSet<String>();
        while (!pending.isEmpty())
        {
            InventoryGoodsCategory current = pending.remove(0);
            if (!visited.add(current.getCategoryCode()))
            {
                continue;
            }
            names.add(current.getCategoryName());
            List<InventoryGoodsCategory> children = childrenOf.get(current.getCategoryCode());
            if (children != null)
            {
                pending.addAll(children);
            }
        }
        return names;
    }

    /**
     * 查询商品分类树：一级为商品分类，二级为该分类下的规格型号
     *
     * @param shopCode 商家编码
     * @return 分类树
     */
    @Override
    public List<GoodsCategoryNode> selectGoodsCategoryTree(String shopCode, Integer activeFlag)
    {
        List<GoodsCategoryNode> stats = goodsMapper.selectGoodsCategoryStat(shopCode, activeFlag);
        Map<String, GoodsCategoryNode> categoryNodes = new LinkedHashMap<String, GoodsCategoryNode>();
        for (GoodsCategoryNode stat : stats)
        {
            String category = normalize(stat.getCategory());
            String spec = normalize(stat.getSpec());
            GoodsCategoryNode parent = categoryNodes.get(category);
            if (parent == null)
            {
                parent = GoodsCategoryNode.ofCategory(category);
                categoryNodes.put(category, parent);
            }
            parent.setGoodsCount(parent.getGoodsCount() + stat.getGoodsCount());
            parent.getChildren().add(GoodsCategoryNode.ofSpec(category, spec, stat.getGoodsCount()));
        }

        // SQL 已按名称排序，这里只把「未分类 / 无型号」挪到末尾，排序稳定不影响其余顺序
        List<GoodsCategoryNode> tree = new ArrayList<GoodsCategoryNode>(categoryNodes.values());
        tree.sort(Comparator.comparingInt(node -> GoodsCategoryNode.NONE.equals(node.getCategory()) ? 1 : 0));
        for (GoodsCategoryNode node : tree)
        {
            node.getChildren().sort(Comparator.comparingInt(child -> GoodsCategoryNode.NONE.equals(child.getSpec()) ? 1 : 0));
        }
        return tree;
    }

    /**
     * 按分类树节点查询商品列表
     */
    @Override
    public List<InventoryGoods> selectGoodsByCategoryNode(String shopCode, String category,
            List<String> categoryNames, String spec,
            String goodsCode, String goodsName, String keyword, Integer activeFlag)
    {
        return goodsMapper.selectGoodsByCategoryNode(shopCode, category, categoryNames, spec,
                goodsCode, goodsName, keyword, activeFlag);
    }

    /**
     * 空的分类 / 型号统一用占位值表示，前端才能把「未分类」和「不限分类」区分开
     */
    private String normalize(String value)
    {
        return StringUtils.isEmpty(value) ? GoodsCategoryNode.NONE : value;
    }

    /**
     * 新增商品
     *
     * @param goods 商品信息
     * @return 结果
     */
    @Override
    public int insertGoods(InventoryGoods goods)
    {
        // 校验商品代码是否唯一
        if (!checkGoodsCodeUnique(goods.getShopCode(), goods.getGoodsCode()))
        {
            throw new ServiceException("商品代码'" + goods.getGoodsCode() + "'已存在");
        }

        // 设置默认值
        if (goods.getActiveFlag() == null)
        {
            goods.setActiveFlag(1);
        }
        InventoryCostPolicy.normalizeAndValidate(goods);
        validatePriceAndStockThresholds(goods);
        validateCategory(goods, null);

        return goodsMapper.insertGoods(goods);
    }

    /**
     * 修改商品
     *
     * @param goods 商品信息
     * @return 结果
     */
    @Override
    public int updateGoods(InventoryGoods goods)
    {
        // 校验商品是否存在
        InventoryGoods existing = goodsMapper.selectGoodsByCode(goods.getShopCode(), goods.getGoodsCode());
        if (existing == null)
        {
            throw new ServiceException("商品不存在");
        }

        // 改单守卫：没有 price:sale 的人拿到的参考价是 null，而 updateGoods 那条 SQL
        // 无条件写 standardprice，校验又放过 null——改个图片、改个库存预警量就会把
        // 参考价悄悄抹掉，还没有任何报错。见 docs/金额按岗位可见.md 第 6.1 节。
        if (PriceMaskUtils.isSaleMaskedOnWrite())
        {
            goods.setStandardPrice(existing.getStandardPrice());
        }

        InventoryCostPolicy.normalizeAndValidate(goods);
        validatePriceAndStockThresholds(goods);
        validateCategory(goods, existing.getCategory());
        if (isGoodsUsed(existing))
        {
            assertLockedFieldsUnchanged(existing, goods);
        }

        return goodsMapper.updateGoods(goods);
    }

    /**
     * 商品是否已经被业务使用。
     *
     * <p>usedflag 由单据保存时置位，覆盖「建过单据但还没审核」的情况；再兜底查一次库存，
     * 防止 usedflag 尚未回填的历史数据漏判。</p>
     */
    /**
     * 配方和生产工单引用中的商品不能删。
     *
     * <p>这两处都不产生单据也不产生库存，{@link #isGoodsUsed} 查不到——一个只录了配方、
     * 还没进过货的物料因此能被删掉，留下一张指向不存在商品的配方或工单，一直要到领料
     * 过账时才报「商品不存在」。提示里说清是被谁引用着，用户才知道该去哪儿解绑。</p>
     */
    private void assertNotUsedByProduction(String shopCode, String goodsCode)
    {
        List<String> holders = new ArrayList<String>();
        if (bomMapper.selectBomByProduct(shopCode, goodsCode) != null)
        {
            holders.add("它自己的产品配方");
        }
        int bomItems = bomItemMapper.countByGoodsCode(shopCode, goodsCode);
        if (bomItems > 0)
        {
            holders.add(bomItems + " 条配方物料");
        }
        int outputs = productionDetailMapper.countByProductCode(shopCode, goodsCode);
        if (outputs > 0)
        {
            holders.add(outputs + " 条生产工单产出");
        }
        int materials = productionMaterialMapper.countByGoodsCode(shopCode, goodsCode);
        if (materials > 0)
        {
            holders.add(materials + " 条生产工单物料");
        }
        if (!holders.isEmpty())
        {
            throw new ServiceException("商品'" + goodsCode + "'还被" + String.join("、", holders)
                    + "引用，不能删除；请先在生产管理里解除引用，或把商品状态改为「停用」");
        }
    }

    private boolean isGoodsUsed(InventoryGoods existing)
    {
        if (Integer.valueOf(1).equals(existing.getUsedFlag()))
        {
            return true;
        }
        InventoryStock stockQuery = new InventoryStock();
        stockQuery.setShopCode(existing.getShopCode());
        stockQuery.setGoodsCode(existing.getGoodsCode());
        List<InventoryStock> stockList = stockMapper.selectStockList(stockQuery);
        return stockList != null && !stockList.isEmpty();
    }

    /**
     * 商品被使用后，这些字段已经被单据明细和库存成本引用，改了会让历史单据对不上账，
     * 一律不允许修改。分类、参考价、预警量、图片、备注、启用状态等不影响已有账目，仍可改。
     *
     * <p>商品编码是主键，updateGoods 用它定位记录，本身就改不了。</p>
     */
    private void assertLockedFieldsUnchanged(InventoryGoods existing, InventoryGoods goods)
    {
        List<String> changed = new ArrayList<String>();
        if (isChanged(existing.getGoodsName(), goods.getGoodsName()))
        {
            changed.add("商品名称");
        }
        if (isChanged(existing.getSpec(), goods.getSpec()))
        {
            changed.add("规格型号");
        }
        if (isChanged(existing.getUnit(), goods.getUnit()))
        {
            changed.add("计量单位");
        }
        if (isChanged(existing.getGoodsType(), goods.getGoodsType()))
        {
            changed.add("商品类型");
        }

        // 成本计算方法由批次策略一一对应推导，不是独立字段，所以只锁批次策略。
        // 批次策略没变时，成本计算方法只可能是历史遗留数据被归一到策略对应的算法，放行。
        String existingTracking = InventoryCostPolicy.normalize(existing.getBatchStrategy(),
                InventoryCostPolicy.TRACKING_NONE);
        if (!existingTracking.equals(goods.getBatchStrategy()))
        {
            changed.add("批次策略");
        }

        if (!changed.isEmpty())
        {
            throw new ServiceException("商品已产生入库单或库存记录，不能再修改"
                    + String.join("、", changed) + "；如确需调整请新建商品");
        }
    }

    /** null 和空串视为同一个值，避免前端把没填的字段回传成空串时误判成修改 */
    private boolean isChanged(String existing, String updated)
    {
        return !StringUtils.trimToEmpty(existing).equals(StringUtils.trimToEmpty(updated));
    }

    /**
     * 单据引用到商品时打上「已使用」标记
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码集合
     * @return 本次新标记的商品数
     */
    @Override
    public int markGoodsUsed(String shopCode, Collection<String> goodsCodes)
    {
        if (StringUtils.isEmpty(shopCode) || goodsCodes == null || goodsCodes.isEmpty())
        {
            return 0;
        }
        Set<String> codes = new LinkedHashSet<String>();
        for (String code : goodsCodes)
        {
            if (StringUtils.isNotEmpty(code))
            {
                codes.add(code.trim());
            }
        }
        return codes.isEmpty() ? 0 : goodsMapper.markGoodsUsed(shopCode, codes);
    }

    private void validatePriceAndStockThresholds(InventoryGoods goods)
    {
        if (goods.getStandardPrice() != null && goods.getStandardPrice().compareTo(java.math.BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("商品参考价不能小于0");
        }
        if (goods.getMinStockQty() != null && goods.getMinStockQty().compareTo(java.math.BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("最低库存预警量不能小于0");
        }
        if (goods.getMaxStockQty() != null && goods.getMaxStockQty().compareTo(java.math.BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("最高库存预警量不能小于0");
        }
        if (goods.getMinStockQty() != null && goods.getMaxStockQty() != null
                && goods.getMaxStockQty().compareTo(java.math.BigDecimal.ZERO) > 0
                && goods.getMinStockQty().compareTo(goods.getMaxStockQty()) > 0)
        {
            throw new ServiceException("最低库存预警量不能大于最高库存预警量");
        }
    }

    /**
     * 校验商品分类。分类是独立的主数据（u_m_goodscategory），商品靠名称挂上去，
     * 所以这里只认分类表里已有的名称，避免前端绕过下拉传进来一个不存在的分类。
     *
     * <p>分类可以不填，表示未分类。停用的分类不能再挂新商品，但老商品本来就在停用分类下时，
     * 允许原样保存，否则改个参考价都要先把分类启用回来。</p>
     *
     * @param goods 待保存的商品
     * @param existingCategory 修改前的分类名，新增时传 null
     */
    private void validateCategory(InventoryGoods goods, String existingCategory)
    {
        String category = StringUtils.trimToEmpty(goods.getCategory());
        goods.setCategory(category);
        if (StringUtils.isEmpty(category))
        {
            return;
        }
        InventoryGoodsCategory matched = categoryMapper.selectCategoryByName(goods.getShopCode(), category);
        if (matched == null)
        {
            throw new ServiceException("商品分类'" + category + "'不存在，请先在商品分类中维护");
        }
        boolean categoryChanged = !category.equals(StringUtils.trimToEmpty(existingCategory));
        if (categoryChanged && "1".equals(matched.getStatus()))
        {
            throw new ServiceException("商品分类'" + category + "'已停用，不能选用");
        }
    }

    /**
     * 批量删除商品
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码数组
     * @return 结果
     */
    @Override
    public int deleteGoodsByCodes(String shopCode, String[] goodsCodes)
    {
        // 商品一旦被单据引用（含待审核、已驳回的单据）或产生过库存就不能删除，
        // 否则单据明细、流水和批次会指向一个查不到的商品。
        for (String goodsCode : goodsCodes)
        {
            InventoryGoods existing = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
            if (existing == null)
            {
                continue;
            }
            if (isGoodsUsed(existing))
            {
                throw new ServiceException("商品'" + goodsCode + "'已产生单据或库存记录，不能删除；"
                        + "如需停止使用请把状态改为「停用」");
            }
            assertNotUsedByProduction(shopCode, goodsCode);
        }
        return goodsMapper.deleteGoodsByCodes(shopCode, goodsCodes);
    }

    /**
     * 校验商品代码是否唯一
     *
     * @param shopCode 商家编码
     * @param goodsCode 商品代码
     * @return 结果
     */
    @Override
    public boolean checkGoodsCodeUnique(String shopCode, String goodsCode)
    {
        int count = goodsMapper.checkGoodsCodeUnique(shopCode, goodsCode);
        return count == 0;
    }
}

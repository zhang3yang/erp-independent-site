package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.InventoryGoodsCategory;
import com.ruoyi.system.mapper.InventoryGoodsCategoryMapper;
import com.ruoyi.system.service.IInventoryGoodsCategoryService;

/**
 * 商品分类Service业务层处理
 *
 * <p>规则与部门管理保持一致：上级停用不允许新增下级、启用下级会把上级一并启用、
 * 停用时下级必须先停用、有下级或有商品时不允许删除。</p>
 *
 * <p>与部门唯一不同的是名称唯一性：商品是靠分类名称挂到分类上的
 * （u_m_waregoods.category 存的就是名称），所以名称在同一商家下全局唯一，
 * 而不是像部门那样只在同一上级下唯一；改名时要把商品上的名称一起改掉。</p>
 */
@Service
public class InventoryGoodsCategoryServiceImpl implements IInventoryGoodsCategoryService
{
    /** 分类编码前缀，后面跟 4 位序号 */
    private static final String CODE_PREFIX = "GC";

    private static final int CODE_SEQ_LENGTH = 4;

    /** 正常状态 */
    private static final String STATUS_NORMAL = "0";

    /** 停用状态 */
    private static final String STATUS_DISABLE = "1";

    @Autowired
    private InventoryGoodsCategoryMapper categoryMapper;

    @Override
    public List<InventoryGoodsCategory> selectCategoryTree(String shopCode, String categoryName, String status)
    {
        return buildTree(selectCategoryList(shopCode, categoryName, status));
    }

    @Override
    public List<InventoryGoodsCategory> selectCategoryList(String shopCode, String categoryName, String status)
    {
        return categoryMapper.selectCategoryList(shopCode, categoryName, status);
    }

    @Override
    public List<InventoryGoodsCategory> selectCategoryListExcludeChild(String shopCode, String categoryCode)
    {
        List<InventoryGoodsCategory> all = categoryMapper.selectCategoryList(shopCode, null, null);
        Set<String> excluded = descendantsAndSelf(all, categoryCode);
        List<InventoryGoodsCategory> result = new ArrayList<InventoryGoodsCategory>();
        for (InventoryGoodsCategory item : all)
        {
            if (!excluded.contains(item.getCategoryCode()))
            {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public InventoryGoodsCategory selectCategoryByCode(String shopCode, String categoryCode)
    {
        return categoryMapper.selectCategoryByCode(shopCode, categoryCode);
    }

    @Override
    public int selectNormalChildrenCount(String shopCode, String categoryCode)
    {
        List<InventoryGoodsCategory> all = categoryMapper.selectCategoryList(shopCode, null, null);
        Set<String> descendants = descendantsAndSelf(all, categoryCode);
        int count = 0;
        for (InventoryGoodsCategory item : all)
        {
            if (!item.getCategoryCode().equals(categoryCode)
                    && descendants.contains(item.getCategoryCode())
                    && STATUS_NORMAL.equals(item.getStatus()))
            {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean hasChildByCategoryCode(String shopCode, String categoryCode)
    {
        return categoryMapper.countChildren(shopCode, categoryCode) > 0;
    }

    @Override
    public boolean checkCategoryExistGoods(String shopCode, String categoryCode)
    {
        InventoryGoodsCategory existing = categoryMapper.selectCategoryByCode(shopCode, categoryCode);
        if (existing == null)
        {
            return false;
        }
        return categoryMapper.countGoodsByCategoryName(shopCode,
                StringUtils.trimToEmpty(existing.getCategoryName())) > 0;
    }

    @Override
    public boolean checkCategoryNameUnique(InventoryGoodsCategory category)
    {
        String name = StringUtils.trimToEmpty(category.getCategoryName());
        return categoryMapper.checkCategoryNameUnique(category.getShopCode(), name,
                category.getCategoryCode()) == 0;
    }

    @Override
    public int insertCategory(InventoryGoodsCategory category)
    {
        normalize(category);
        InventoryGoodsCategory parent = assertParentExists(category.getShopCode(), category.getParentCode());
        // 与部门一致：上级停用了就不允许再往下挂
        if (parent != null && !STATUS_NORMAL.equals(parent.getStatus()))
        {
            throw new ServiceException("分类停用，不允许新增");
        }
        assertNameUnique(category.getShopCode(), category.getCategoryName(), null);
        category.setCategoryCode(nextCategoryCode(category.getShopCode()));
        return categoryMapper.insertCategory(category);
    }

    @Override
    @Transactional
    public int updateCategory(InventoryGoodsCategory category)
    {
        normalize(category);
        InventoryGoodsCategory existing = categoryMapper.selectCategoryByCode(
                category.getShopCode(), category.getCategoryCode());
        if (existing == null)
        {
            throw new ServiceException("商品分类不存在");
        }
        if (category.getCategoryCode().equals(category.getParentCode()))
        {
            throw new ServiceException("上级分类不能选择自己");
        }
        assertParentExists(category.getShopCode(), category.getParentCode());
        assertNotDescendant(category.getShopCode(), category.getCategoryCode(), category.getParentCode());
        assertNameUnique(category.getShopCode(), category.getCategoryName(), category.getCategoryCode());

        int rows = categoryMapper.updateCategory(category);
        // 商品记的是分类名称，改了名就要把商品上的名称同步过去，否则商品会掉出这个分类
        if (!category.getCategoryName().equals(StringUtils.trimToEmpty(existing.getCategoryName())))
        {
            categoryMapper.updateGoodsCategoryName(category.getShopCode(),
                    StringUtils.trimToEmpty(existing.getCategoryName()), category.getCategoryName());
        }
        // 与部门一致：启用了某个分类，就把它的所有上级分类一并启用，否则这个分类在树上是断的
        if (STATUS_NORMAL.equals(category.getStatus()))
        {
            updateParentCategoryStatusNormal(category.getShopCode(), category.getParentCode());
        }
        return rows;
    }

    @Override
    @Transactional
    public void updateCategorySort(String shopCode, String[] categoryCodes, String[] orderNums)
    {
        if (categoryCodes == null || orderNums == null || categoryCodes.length != orderNums.length)
        {
            throw new ServiceException("保存排序异常，请联系管理员");
        }
        try
        {
            for (int i = 0; i < categoryCodes.length; i++)
            {
                categoryMapper.updateCategorySort(shopCode, categoryCodes[i].trim(),
                        Integer.valueOf(orderNums[i].trim()));
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("保存排序异常，请联系管理员");
        }
    }

    @Override
    public int deleteCategoryByCode(String shopCode, String categoryCode)
    {
        return categoryMapper.deleteCategoryByCode(shopCode, categoryCode);
    }

    /** 名称去空格，上级、排序、状态给默认值 */
    private void normalize(InventoryGoodsCategory category)
    {
        if (category == null)
        {
            throw new ServiceException("商品分类信息不能为空");
        }
        String name = StringUtils.trimToEmpty(category.getCategoryName());
        if (StringUtils.isEmpty(name))
        {
            throw new ServiceException("分类名称不能为空");
        }
        category.setCategoryName(name);
        if (StringUtils.isEmpty(category.getParentCode()))
        {
            category.setParentCode(InventoryGoodsCategory.ROOT);
        }
        if (category.getOrderNum() == null || category.getOrderNum() < 0)
        {
            category.setOrderNum(0);
        }
        if (!STATUS_DISABLE.equals(category.getStatus()))
        {
            category.setStatus(STATUS_NORMAL);
        }
    }

    /** 上级分类必须存在；顶级分类返回 null */
    private InventoryGoodsCategory assertParentExists(String shopCode, String parentCode)
    {
        if (InventoryGoodsCategory.ROOT.equals(parentCode))
        {
            return null;
        }
        InventoryGoodsCategory parent = categoryMapper.selectCategoryByCode(shopCode, parentCode);
        if (parent == null)
        {
            throw new ServiceException("上级分类不存在");
        }
        return parent;
    }

    /** 把某个分类往上的所有上级分类置为正常 */
    private void updateParentCategoryStatusNormal(String shopCode, String parentCode)
    {
        if (InventoryGoodsCategory.ROOT.equals(parentCode))
        {
            return;
        }
        Map<String, String> parentOf = parentIndex(categoryMapper.selectCategoryList(shopCode, null, null));
        List<String> ancestors = new ArrayList<String>();
        String cursor = parentCode;
        // 层级有限，用已访问集合兜底，防止历史脏数据成环时死循环
        Set<String> visited = new LinkedHashSet<String>();
        while (StringUtils.isNotEmpty(cursor) && !InventoryGoodsCategory.ROOT.equals(cursor) && visited.add(cursor))
        {
            ancestors.add(cursor);
            cursor = parentOf.get(cursor);
        }
        if (!ancestors.isEmpty())
        {
            categoryMapper.updateCategoryStatusNormal(shopCode, ancestors);
        }
    }

    /** 不能把分类挂到自己的子孙下面，否则这棵子树会从树上掉下来 */
    private void assertNotDescendant(String shopCode, String categoryCode, String parentCode)
    {
        if (InventoryGoodsCategory.ROOT.equals(parentCode))
        {
            return;
        }
        Map<String, String> parentOf = parentIndex(categoryMapper.selectCategoryList(shopCode, null, null));
        String cursor = parentCode;
        Set<String> visited = new LinkedHashSet<String>();
        while (StringUtils.isNotEmpty(cursor) && !InventoryGoodsCategory.ROOT.equals(cursor) && visited.add(cursor))
        {
            if (categoryCode.equals(cursor))
            {
                throw new ServiceException("上级分类不能选择自己的下级分类");
            }
            cursor = parentOf.get(cursor);
        }
    }

    private void assertNameUnique(String shopCode, String categoryName, String excludeCode)
    {
        if (categoryMapper.checkCategoryNameUnique(shopCode, categoryName, excludeCode) > 0)
        {
            throw new ServiceException("分类名称'" + categoryName + "'已存在");
        }
    }

    /** 自己 + 自己的全部下级分类编码 */
    private Set<String> descendantsAndSelf(List<InventoryGoodsCategory> all, String categoryCode)
    {
        Map<String, List<InventoryGoodsCategory>> childrenOf =
                new LinkedHashMap<String, List<InventoryGoodsCategory>>();
        for (InventoryGoodsCategory item : all)
        {
            List<InventoryGoodsCategory> siblings = childrenOf.get(item.getParentCode());
            if (siblings == null)
            {
                siblings = new ArrayList<InventoryGoodsCategory>();
                childrenOf.put(item.getParentCode(), siblings);
            }
            siblings.add(item);
        }
        Set<String> result = new LinkedHashSet<String>();
        List<String> pending = new ArrayList<String>();
        pending.add(categoryCode);
        while (!pending.isEmpty())
        {
            String current = pending.remove(0);
            if (!result.add(current))
            {
                continue;
            }
            List<InventoryGoodsCategory> children = childrenOf.get(current);
            if (children != null)
            {
                for (InventoryGoodsCategory child : children)
                {
                    pending.add(child.getCategoryCode());
                }
            }
        }
        return result;
    }

    private Map<String, String> parentIndex(List<InventoryGoodsCategory> all)
    {
        Map<String, String> parentOf = new LinkedHashMap<String, String>();
        for (InventoryGoodsCategory item : all)
        {
            parentOf.put(item.getCategoryCode(), item.getParentCode());
        }
        return parentOf;
    }

    /** 生成 GC0001 这样的编码，取当前最大值加一 */
    private String nextCategoryCode(String shopCode)
    {
        String maxCode = categoryMapper.selectMaxCategoryCode(shopCode);
        int next = 1;
        if (StringUtils.isNotEmpty(maxCode) && maxCode.length() > CODE_PREFIX.length())
        {
            try
            {
                next = Integer.parseInt(maxCode.substring(CODE_PREFIX.length())) + 1;
            }
            catch (NumberFormatException e)
            {
                next = 1;
            }
        }
        return CODE_PREFIX + String.format("%0" + CODE_SEQ_LENGTH + "d", next);
    }

    /** 平铺列表拼成树；父节点被搜索条件过滤掉的节点当作顶级返回，避免整棵子树查不出来 */
    private List<InventoryGoodsCategory> buildTree(List<InventoryGoodsCategory> list)
    {
        Map<String, InventoryGoodsCategory> indexed = new LinkedHashMap<String, InventoryGoodsCategory>();
        for (InventoryGoodsCategory item : list)
        {
            item.setChildren(new ArrayList<InventoryGoodsCategory>());
            indexed.put(item.getCategoryCode(), item);
        }
        List<InventoryGoodsCategory> roots = new ArrayList<InventoryGoodsCategory>();
        for (InventoryGoodsCategory item : list)
        {
            InventoryGoodsCategory parent = indexed.get(item.getParentCode());
            if (parent == null)
            {
                roots.add(item);
            }
            else
            {
                parent.getChildren().add(item);
            }
        }
        return roots;
    }
}

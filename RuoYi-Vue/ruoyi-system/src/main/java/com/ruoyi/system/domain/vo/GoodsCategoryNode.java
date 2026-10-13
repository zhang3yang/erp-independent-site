package com.ruoyi.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类树节点。
 *
 * <p>两层结构：第一层是商品分类（category），第二层是该分类下的规格型号（spec）。
 * 点开分类可以看到该分类下的型号，选中型号后按「分类 + 型号」查出对应的商品。</p>
 *
 * <p>分类或型号为空的商品归到「未分类 / 无型号」节点。这类节点的 category / spec
 * 用 {@link #NONE} 占位而不是空串，因为前端 GET 参数序列化（tansParams）会丢掉空串，
 * 空串到了后端和「不传该条件」无法区分。</p>
 */
public class GoodsCategoryNode implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 空分类 / 空型号的占位值，前后端约定一致 */
    public static final String NONE = "__NONE__";

    /** 分类节点 */
    public static final String TYPE_CATEGORY = "category";

    /** 型号节点 */
    public static final String TYPE_SPEC = "spec";

    /** 节点唯一标识，供前端树 node-key 使用 */
    private String id;

    /** 节点显示名称 */
    private String label;

    /** 节点层级：category-分类，spec-型号 */
    private String nodeType;

    /** 所属分类，空分类为 {@link #NONE} */
    private String category;

    /** 规格型号，分类节点为 null，空型号为 {@link #NONE} */
    private String spec;

    /** 节点下的商品数量 */
    private Integer goodsCount;

    /** 子节点，型号节点为空 */
    private List<GoodsCategoryNode> children;

    /**
     * 构造分类节点
     *
     * @param category 分类，空分类传 {@link #NONE}
     * @return 分类节点
     */
    public static GoodsCategoryNode ofCategory(String category)
    {
        GoodsCategoryNode node = new GoodsCategoryNode();
        node.setId("category:" + category);
        node.setLabel(NONE.equals(category) ? "未分类" : category);
        node.setNodeType(TYPE_CATEGORY);
        node.setCategory(category);
        node.setGoodsCount(0);
        node.setChildren(new ArrayList<GoodsCategoryNode>());
        return node;
    }

    /**
     * 构造型号节点
     *
     * @param category 所属分类
     * @param spec 型号，空型号传 {@link #NONE}
     * @param goodsCount 该型号下的商品数量
     * @return 型号节点
     */
    public static GoodsCategoryNode ofSpec(String category, String spec, Integer goodsCount)
    {
        GoodsCategoryNode node = new GoodsCategoryNode();
        node.setId("spec:" + category + "/" + spec);
        node.setLabel(NONE.equals(spec) ? "无型号" : spec);
        node.setNodeType(TYPE_SPEC);
        node.setCategory(category);
        node.setSpec(spec);
        node.setGoodsCount(goodsCount);
        return node;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getNodeType()
    {
        return nodeType;
    }

    public void setNodeType(String nodeType)
    {
        this.nodeType = nodeType;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getSpec()
    {
        return spec;
    }

    public void setSpec(String spec)
    {
        this.spec = spec;
    }

    public Integer getGoodsCount()
    {
        return goodsCount;
    }

    public void setGoodsCount(Integer goodsCount)
    {
        this.goodsCount = goodsCount;
    }

    public List<GoodsCategoryNode> getChildren()
    {
        return children;
    }

    public void setChildren(List<GoodsCategoryNode> children)
    {
        this.children = children;
    }
}

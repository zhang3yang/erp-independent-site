package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 商品分类实体类
 * 对应表：sdsshop.u_m_goodscategory
 *
 * <p>分类是一棵树，商品通过分类名称（u_m_waregoods.category）挂到分类上，
 * 所以同一商家下分类名称必须唯一。</p>
 */
public class InventoryGoodsCategory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 顶级分类的上级编码 */
    public static final String ROOT = "0";

    /** 商家编码 */
    private String shopCode;

    /** 分类编码，系统生成 */
    private String categoryCode;

    /** 分类名称，同一商家下唯一 */
    private String categoryName;

    /** 上级分类编码，顶级为 0 */
    private String parentCode;

    /** 显示排序 */
    private Integer orderNum;

    /** 状态：0 正常 1 停用 */
    private String status;

    /** 备注 */
    private String remark;

    private String createUser;

    private Date createTime;

    private String updateUser;

    private Date updateTime;

    /** 该分类下的商品数，列表查询时统计 */
    private Integer goodsCount;

    /** 子分类，供前端树形表格使用 */
    private List<InventoryGoodsCategory> children = new ArrayList<InventoryGoodsCategory>();

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getCategoryCode()
    {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode)
    {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getParentCode()
    {
        return parentCode;
    }

    public void setParentCode(String parentCode)
    {
        this.parentCode = parentCode;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getCreateUser()
    {
        return createUser;
    }

    public void setCreateUser(String createUser)
    {
        this.createUser = createUser;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateUser()
    {
        return updateUser;
    }

    public void setUpdateUser(String updateUser)
    {
        this.updateUser = updateUser;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public Integer getGoodsCount()
    {
        return goodsCount;
    }

    public void setGoodsCount(Integer goodsCount)
    {
        this.goodsCount = goodsCount;
    }

    public List<InventoryGoodsCategory> getChildren()
    {
        return children;
    }

    public void setChildren(List<InventoryGoodsCategory> children)
    {
        this.children = children;
    }
}

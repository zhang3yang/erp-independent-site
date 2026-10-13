package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 销售套餐主数据。
 *
 * <p><b>套餐不是一种商品</b>，只是一组商品的组合标签：下单时点「选择套餐」，
 * 把套餐里的商品按各自数量一次性铺进订单明细，之后每一行仍是独立的普通商品行，
 * 单价可改、可删、可加。订单上<b>不记</b>它是从哪个套餐来的——预占、发货、
 * 退货全都按商品行走，多存一层来源只会让核销多一处要对齐的地方。</p>
 *
 * <p>停用（{@code isActive=0}）后不再出现在下单的选择列表里，但已经下过的订单不受影响，
 * 因为订单存的是商品行快照，不是对套餐的引用。</p>
 */
public class SalesPackage implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String packageCode;
    private String packageName;
    private Integer isActive;
    private String remark;
    private String createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private String updateUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 套餐包含的商品行 */
    private List<SalesPackageItem> items = new ArrayList<>();

    /** 列表页展示用，不落库：本套餐含多少种商品 */
    private Integer goodsKinds;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String packageCode) { this.packageCode = packageCode; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public List<SalesPackageItem> getItems() { return items; }
    public void setItems(List<SalesPackageItem> items) { this.items = items; }
    public Integer getGoodsKinds() { return goodsKinds; }
    public void setGoodsKinds(Integer goodsKinds) { this.goodsKinds = goodsKinds; }
}

package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 收支分类表（说明书 5.3.3 u_f_accountcategory）
 *
 * <p>说明书 5.2 把这张表标成「按月分表」，但它是配置字典：按月分表意味着每跨一个月
 * 分类就全空了，历史流水也查不到自己的分类名。实现按单表处理，见建表脚本的说明。</p>
 */
public class FinanceCategory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 收入 */
    public static final String TYPE_INCOME = "收入";

    /** 支出 */
    public static final String TYPE_EXPENSE = "支出";

    /** 业务类型「其他」的系统保留支出分类 */
    public static final String OTHER_EXPENSE_CODE = "QT_ZC";

    /** 业务类型「其他」的系统保留收入分类 */
    public static final String OTHER_INCOME_CODE = "QT_SR";

    /** 业务类型「其他」的支出分类名称 */
    public static final String OTHER_EXPENSE_NAME = "其他支出";

    /** 业务类型「其他」的收入分类名称 */
    public static final String OTHER_INCOME_NAME = "其他收入";

    /**
     * 业务类型「其他」在某个收支方向上唯一可用的分类代码。
     *
     * <p>其他这类非业务流水不分明细科目，一收一支各一条系统分类，
     * 具体用途写在流水的业务摘要或备注里。</p>
     */
    public static String otherCategoryCode(String type)
    {
        return TYPE_INCOME.equals(type) ? OTHER_INCOME_CODE : OTHER_EXPENSE_CODE;
    }

    /** 是不是业务类型「其他」的两条系统保留分类之一 */
    public static boolean isReservedOtherCode(String categoryCode)
    {
        return OTHER_EXPENSE_CODE.equals(categoryCode) || OTHER_INCOME_CODE.equals(categoryCode);
    }

    /** 系统保留分类的固定名称 */
    public static String reservedOtherName(String categoryCode)
    {
        return OTHER_INCOME_CODE.equals(categoryCode) ? OTHER_INCOME_NAME : OTHER_EXPENSE_NAME;
    }

    /** 系统保留分类的固定收支方向 */
    public static String reservedOtherType(String categoryCode)
    {
        return OTHER_INCOME_CODE.equals(categoryCode) ? TYPE_INCOME : TYPE_EXPENSE;
    }

    /** 主键ID */
    private Long id;

    /** 商家编码 */
    private String shopCode;

    /** 分类代码 */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 收入/支出 */
    private String type;

    /** 关联业务类型：采购/销售/其他（费用、工资仅保留历史值） */
    private String bizType;

    /** 是否启用：1-启用 0-停用 */
    private Integer isActive;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新人 */
    private String updateUser;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }

    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getCategoryCode() { return categoryCode; }

    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }

    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getBizType() { return bizType; }

    public void setBizType(String bizType) { this.bizType = bizType; }

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
}

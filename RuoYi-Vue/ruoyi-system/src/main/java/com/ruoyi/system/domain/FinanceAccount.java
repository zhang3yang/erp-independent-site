package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 账户信息表（说明书 5.3.1 u_f_account）
 *
 * <p>公司银行账户。{@link #currentBalance} 只由资金流水「完成」时驱动，
 * 账户维护接口不接收余额字段，否则账面余额会和流水对不上账。</p>
 */
public class FinanceAccount implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 商家编码 */
    private String shopCode;

    /** 账户代码 */
    private String accountCode;

    /** 账户简称 */
    private String accountName;

    /** 账户全称 */
    private String accountFullName;

    /** 银行账号 */
    private String accountNo;

    /** 开户行名称 */
    private String bankName;

    /** 当前余额，只由资金流水完成时驱动 */
    private BigDecimal currentBalance;

    /** ISO 4217 币种代码；一个账户只能对应一种币种 */
    private String currency;

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

    /** 创建人昵称，仅查询返回 */
    private String createUserName;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }

    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getAccountCode() { return accountCode; }

    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getAccountName() { return accountName; }

    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAccountFullName() { return accountFullName; }

    public void setAccountFullName(String accountFullName) { this.accountFullName = accountFullName; }

    public String getAccountNo() { return accountNo; }

    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public String getBankName() { return bankName; }

    public void setBankName(String bankName) { this.bankName = bankName; }

    public BigDecimal getCurrentBalance() { return currentBalance; }

    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    public String getCurrency() { return currency; }

    public void setCurrency(String currency) { this.currency = currency; }

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

    public String getCreateUserName() { return createUserName; }

    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }
}

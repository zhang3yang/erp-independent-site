package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;

/**
 * 站点客户档案。对应表 {@code u_s_buyer}。
 *
 * <h3>⚠ 和 ERP 的销售客户没有任何关系</h3>
 * <p>docs/独立站与ERP的边界.md §6c：买家属于站点。这个类里没有一个字段指向
 * {@code sdsshop.u_m_buyer}，也没有「转成 ERP 客户」那个动作——那个动作是一条互通，
 * §10.1 里已经删掉了。名字里都有个「buyer」，但那是两个库里两张互不相干的表。</p>
 *
 * <h3>档案是「长出来的」，不是「建出来的」</h3>
 * <p>没有「新建客户」这个入口。谁发了询盘、谁下了单，就按<b>邮箱</b>为他落一行；
 * 同一个邮箱再来，更新联系方式和计数。管理台里能改的只有 {@link #tag} 和 {@link #remark}。</p>
 *
 * <p>这和 {@code SiteUser}「谁从 ERP 跳进来就落一行」是同一个思路：
 * 没有「新建」按钮，就没有「建了一个对不上任何人的档案」这种状态。</p>
 *
 * <h3>为什么身份是邮箱</h3>
 * <p>唯一键是 {@code (shop_code, email)}。外贸场景里同一个人换公司、同一个公司来好几个人
 * 都很常见，邮箱是唯一稳定的标识；按人名去重会把两个同名的人合成一个，
 * 而那种错一旦发生，两个客户的往来记录就混在一起了，事后拆不开。</p>
 *
 * <h3>它同时是<b>前台买家登录</b>的账号</h3>
 * <p>{@link #passwordHash} 有值 = 这个人在前台注册过，能自己登录看订单；
 * 为 null = 只有档案没有账号（发过询盘但没注册），<b>这是这张表里的常态</b>。</p>
 *
 * <p>⚠ 别和 {@code SiteUser} 搞混，那是<b>管理台操作员</b>：</p>
 * <table border="1">
 *   <tr><th>类 / 表</th><th>谁</th><th>怎么进来</th><th>存密码吗</th></tr>
 *   <tr><td>{@code SiteUser} / {@code u_s_user}</td><td>管理台操作员（自己人）</td>
 *       <td>只能从 ERP 点「进入管理」免密进</td><td><b>不存</b></td></tr>
 *   <tr><td>{@code SiteBuyer} / {@code u_s_buyer}</td><td>前台买家（客户）</td>
 *       <td>前台自助注册 + 邮箱验证 + 密码登录</td><td>存</td></tr>
 * </table>
 *
 * <p>两拨人不共用一张表：{@code u_s_user.rolecode} 是管理台权限的来源，
 * 合在一起的话，某个查询漏一个 {@code where} 就是「买家拿到了管理台权限」。</p>
 */
public class SiteBuyer implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String siteCode;
    private String buyerCode;

    /** ⚠ 客户的身份就是它，见类注释 */
    private String email;

    /**
     * BCrypt 密文。
     *
     * <p><b>{@code @JsonIgnore} 不能去掉</b>：管理台的客户列表和详情接口会把这个对象
     * 直接序列化出去。BCrypt 密文不能直接拿来登录，但它是可以离线爆破的——
     * 买家用的多半是他到处都在用的那个密码，泄一个等于泄一串站。</p>
     *
     * <p>为 null = 有档案没账号，见类注释。<b>这是常态，不是数据缺失。</b></p>
     *
     * <p>SQL 那一层还有第一道：{@code SiteBuyerMapper.xml} 里给管理台用的列清单
     * 压根不含这一列，只有认证专用的那两条查询会取它。</p>
     */
    @JsonIgnore
    private String passwordHash;

    /**
     * 邮箱验证过没有。
     *
     * <p><b>没验证过不许登录。</b>注册时那个邮箱可能压根不是他的——不验证的话，
     * 任何人都能拿别人的邮箱注册，然后那个真正的邮箱主人就再也注册不了了
     * （唯一键撞上），而且他之前发的询盘会落进一个别人能登进去的账号里。</p>
     */
    private Boolean emailVerified;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date verifyTime;
    /** 正常/停用。停用后登不进来，但档案、询盘、订单都留着——运营还要看 */
    private String status;

    /** 联系人。跟着最近一次提交更新 */
    private String name;
    private String company;
    private String phone;
    /** 国家。只有下过单才有——询盘表单不收这一项 */
    private String country;

    private Integer inquiryCount;
    private Integer orderCount;

    /** 最后登录时间。⚠ 和 {@link #lastActive} 不是一回事：那一列是最近一次询盘或下单 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastLoginTime;
    /** 最后登录 IP。只用于排查，不参与任何判定——那个头是客户端可伪造的 */
    private String lastLoginIp;
    private Integer loginCount;
    /** 最近一次询盘或下单的时间。客户列表默认按它倒排 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastActive;

    /** 运营自己打的标签，如「重点」「已寄样」。一期就一个自由文本，不做标签表 */
    private String tag;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 显示名：有公司名就用公司名，否则用联系人，都没有就用邮箱 */
    public String displayName()
    {
        if (company != null && !company.isBlank()) return company;
        if (name != null && !name.isBlank()) return name;
        return email;
    }

    /** 在前台注册过账号没有。没有 = 只有档案（发过询盘但没注册），是常态 */
    @JsonIgnore
    public boolean isRegistered()
    {
        return passwordHash != null && !passwordHash.isBlank();
    }

    /** 邮箱验证过没有。null 当成 false——历史行没有这一列的值 */
    @JsonIgnore
    public boolean isVerified()
    {
        return Boolean.TRUE.equals(emailVerified);
    }

    /** 被停用了没有。停用后登不进来，但档案照样在管理台里看得到 */
    @JsonIgnore
    public boolean isDisabled()
    {
        return "停用".equals(status);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    /** ⚠ 只给认证代码用。序列化被 {@code @JsonIgnore} 挡住了，别绕过它 */
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public Date getVerifyTime() { return verifyTime; }
    public void setVerifyTime(Date verifyTime) { this.verifyTime = verifyTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(Date lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

    public Integer getLoginCount() { return loginCount; }
    public void setLoginCount(Integer loginCount) { this.loginCount = loginCount; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Integer getInquiryCount() { return inquiryCount; }
    public void setInquiryCount(Integer inquiryCount) { this.inquiryCount = inquiryCount; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public Date getLastActive() { return lastActive; }
    public void setLastActive(Date lastActive) { this.lastActive = lastActive; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}

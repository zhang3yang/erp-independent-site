package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Date;

/**
 * 站点客户询盘。对应表 {@code u_s_inquiry}。
 *
 * <p>客户在站上留下联系方式和需求，落进<b>本站的库</b>，由站点运营在管理台里跟进。</p>
 *
 * <p><b>「转成 ERP 客户」那个动作没有了</b>（docs/独立站与ERP的边界.md §3.2）：
 * 它往 ERP 的 {@code sdsshop.u_m_buyer} 里写数据，是一条互通。询盘从头到尾只在这台机器上。</p>
 */
public class SiteInquiry implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_NEW = "待处理";
    public static final String STATUS_CONTACTED = "已联系";
    /** 已成交。以前叫「已转客户」——那时候「转」的是 ERP 客户档案，现在没有那个动作了 */
    public static final String STATUS_CONVERTED = "已成交";
    public static final String STATUS_SPAM = "无效";

    private Long id;
    private String shopCode;
    private String siteCode;
    private String inquiryCode;
    private String name;
    private String email;
    private String company;
    private String phone;
    /** 意向商品编码，从详情页提交时带上 */
    private String goodsCode;
    /** 意向商品名称，存客户当时看到的那个标题 */
    private String productTitle;
    /**
     * 客户在详情页上选中的那个组合的编码。
     *
     * <p>⚠ 不是外键：商家随后可能把那条组合删了或停用了，历史询盘不受影响。</p>
     */
    private String comboCode;
    /**
     * 组合名称快照。
     *
     * <p><b>这一项由服务端按 comboCode 查库填写，不采信客户端传来的值。</b>
     * 名称是要发给销售看、要拿去开报价单的，让浏览器随便填等于让人往你的
     * 询盘台账里写字。{@code SiteOpenService.submitInquiry} 里会覆盖掉。</p>
     */
    private String comboName;
    /**
     * 提交时客户所在的语言（URL 前缀那个）。
     *
     * <p>建库时这一列的注释写的是「【RFQ 留位置】」，现在真的用上了：组合名有七份，
     * 快照要存的是**客户当时看到的那一份**，不然销售看到的名字和客户说的对不上。</p>
     */
    private String lang;
    private String message;
    private String pageUrl;
    private String clientIp;
    private String userAgent;
    private String status;
    /**
     * 【留位置】本站客户编码。
     *
     * <p>站点自己的客户管理一期不做（boundary §11），所以这一列恒为 null。
     * <b>它不再指向 ERP 的 u_m_buyer</b>——两边客户没有任何关系。</p>
     *
     * <p>留着不删：删列是不可逆的，而做站点客户管理时它正好是落 ID 的地方。</p>
     */
    private String buyerCode;
    private String handleUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date handleTime;
    private String remark;

    /**
     * 负责人（{@code u_s_user.username}）。
     *
     * <p>⚠ 和 {@link #handleUser} <b>不是一回事</b>：那个是「最后动过这条的人」，
     * 每次保存都会被改写；这个是「这条归谁」，由人指派、不会自己变。</p>
     *
     * <p>为什么要分开：一条谁都没碰过的询盘，{@code handleUser} 是空的。
     * 而「没人负责」和「没人动过」是两个不同的问题——前者是分派没做，
     * 后者是人手不够或者没人看见。合成一个字段就再也分不出来了。</p>
     */
    private String owner;

    /**
     * 下次跟进日期。
     *
     * <p>⚠ 只到天，不到时分：销售定的是「下周三再问一次」，精确到分钟是假精度，
     * 还会让列表里每一行都拖着一个 {@code 00:00:00}。</p>
     *
     * <p>跟进看板的「今天要跟」和「已逾期」都看它。为空<b>不代表不用跟</b>——
     * 恰恰相反，最危险的是「没人给它定过日期、也没人碰过」的那一批，
     * 看板里用「躺了几天」把它们捞出来。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date nextFollow;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 站点名称。
     *
     * <p>ERP 那版是 {@code left join u_s_site} 带出来的，用来在「整个商户的询盘」列表里
     * 区分是哪个站的。这里一台机器一个站，那个 join 没有了，也不需要——
     * 由 Service 从 {@code u_s_siteinfo} 填一次即可。</p>
     */
    private String siteName;

    /**
     * 站点标识，站点前端提交表单时一起带上来。
     *
     * <p>不落库，而且<b>不再被读</b>：以前 ERP 要靠它区分是哪个站来的，现在一个部署一个站，
     * 站点身份由后端自己知道。字段留着只为兼容还在发它的老前端，收到就丢。</p>
     */
    private String siteKey;

    /**
     * 蜜罐字段：页面上用 CSS 藏起来，真人看不见，爬虫会填。
     *
     * <p>填了就静默丢弃，比给外国客户上图形验证码友好得多。</p>
     *
     * <p>注解必须是 {@code WRITE_ONLY} 而不是 {@code @JsonIgnore}：后者连反序列化一起忽略，
     * 前端填了也读不到，蜜罐就成了摆设（这个坑踩过一次——机器人的询盘照样落库了）。
     * WRITE_ONLY 的意思是「能从请求体读进来，但不往响应里写出去」，正是蜜罐要的。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String website;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getInquiryCode() { return inquiryCode; }
    public void setInquiryCode(String inquiryCode) { this.inquiryCode = inquiryCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }

    public String getComboCode() { return comboCode; }
    public void setComboCode(String comboCode) { this.comboCode = comboCode; }

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getHandleUser() { return handleUser; }
    public void setHandleUser(String handleUser) { this.handleUser = handleUser; }

    public Date getHandleTime() { return handleTime; }
    public void setHandleTime(Date handleTime) { this.handleTime = handleTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public Date getNextFollow() { return nextFollow; }
    public void setNextFollow(Date nextFollow) { this.nextFollow = nextFollow; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getSiteKey() { return siteKey; }
    public void setSiteKey(String siteKey) { this.siteKey = siteKey; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}

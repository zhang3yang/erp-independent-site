package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 在线咨询的会话线程。对应表 {@code u_s_chat}，一个访客一行。
 *
 * <h3>匿名就能聊</h3>
 * <p>这个站的询盘和下单都不要求账号（{@code useBuyer.ts}：「挡一道登录会掉单，
 * 外贸站尤其如此」），聊天没有理由比它们更严。访客靠 {@code CHAT_VISITOR} 这个
 * HttpOnly Cookie 认线程，邮箱<b>选填</b>——填了才走
 * {@code SiteBuyerService.resolveByEmail} 挂到 {@code u_s_buyer} 上。</p>
 *
 * <p>代价写在这里免得将来被当成 bug：<b>没留邮箱的人清了浏览器数据就找不回对话</b>，
 * 运营也没法邮件回访。那是选「不挡登录」换来的，不是缺陷。</p>
 *
 * <h3>⚠ 未读数不在这个类里存成一个计数器</h3>
 * <p>存的是两个<b>已读游标</b>（{@link #consoleReadId} / {@link #buyerReadId}），
 * 未读数由 {@link #unread} 现算后填进来。理由和 {@code SiteBuyer} 的
 * {@code inquiryCount} 那条一样：累加的计数会漂，漏减一次就永远错下去。</p>
 *
 * <p>设计文档：{@code docs/设计稿/2026-09-04-在线咨询.md}</p>
 */
public class SiteChat implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shopCode;
    private String siteCode;
    private String chatCode;

    /**
     * 访客令牌。<b>这是匿名会话的唯一凭据，拿到就能读走整条对话。</b>
     *
     * <h3>⚠ 三道防线，这个 {@code @JsonIgnore} 是第二道</h3>
     * <ol>
     *   <li>管理台那份列清单（{@code SiteChatMapper.xml} 的 {@code columns}）里
     *       <b>没有它</b>，物理上取不出来；</li>
     *   <li>这个注解——万一哪天有人图省事用了 {@code authColumns}，它也序列化不出去；</li>
     *   <li>它只在 Cookie 里流动，<b>绝不进 URL</b>：query 参数会原样进 nginx 的
     *       access log，那份日志谁都能看。</li>
     * </ol>
     *
     * <p>照的是 {@code SiteBuyer.passwordHash} 那一套，不是新发明。</p>
     */
    @JsonIgnore
    private String visitorToken;

    /** 挂上的客户档案。留了邮箱或已登录才有，为 null 是常态 */
    private String buyerCode;
    /** 访客留的邮箱（选填）。留了才能邮件回访 */
    private String email;
    /** 访客填的称呼（选填） */
    private String name;
    /** 访客用哪种语言开的口。前台是 8 语言站，运营得知道用哪国话回 */
    private String lang;

    /** 他在哪个页面开的口。运营一眼看出他在看哪个商品 */
    private String pageUrl;
    private String clientIp;
    private String userAgent;

    /** 最后一条的摘要。会话列表直接显示它，不用 join 消息表 */
    private String lastMessage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastMessageTime;

    /** 运营读到的最大 message id。⚠ 只增不减，写的时候套 greatest */
    private Long consoleReadId;
    /** 买家读到的最大 message id。同上 */
    private Long buyerReadId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /**
     * 未读数。<b>不是库里的列</b>——现算出来填进这里的。
     *
     * <p>语义随取数的一方变：管理台的列表里它是「运营没读的买家消息数」，
     * 买家侧是「买家没读的运营消息数」。两边各自的 SQL 里写死了 sender 的方向。</p>
     */
    private Integer unread;

    /**
     * 列表上显示什么名字。库里没有这一列。
     *
     * <p>优先级：称呼 &gt; 邮箱 &gt; 会话编码。三样都可能为空——匿名访客一句话都没留时，
     * 运营至少要能看到一个 {@code LT0000000001} 把这一行和别的区分开，
     * 而不是一片空白。</p>
     */
    public String getDisplayName()
    {
        if (name != null && !name.isBlank()) return name;
        if (email != null && !email.isBlank()) return email;
        return chatCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getChatCode() { return chatCode; }
    public void setChatCode(String chatCode) { this.chatCode = chatCode; }

    public String getVisitorToken() { return visitorToken; }
    public void setVisitorToken(String visitorToken) { this.visitorToken = visitorToken; }

    public String getBuyerCode() { return buyerCode; }
    public void setBuyerCode(String buyerCode) { this.buyerCode = buyerCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public Date getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(Date lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public Long getConsoleReadId() { return consoleReadId; }
    public void setConsoleReadId(Long consoleReadId) { this.consoleReadId = consoleReadId; }

    public Long getBuyerReadId() { return buyerReadId; }
    public void setBuyerReadId(Long buyerReadId) { this.buyerReadId = buyerReadId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Integer getUnread() { return unread; }
    public void setUnread(Integer unread) { this.unread = unread; }
}

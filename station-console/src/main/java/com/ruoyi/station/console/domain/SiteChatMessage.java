package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

/**
 * 在线咨询里的一句话。对应表 {@code u_s_chatmessage}。
 *
 * <p>设计文档：{@code docs/设计稿/2026-09-04-在线咨询.md}</p>
 */
public class SiteChatMessage implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 谁发的。中文常量，和 {@code SiteOrder.STATUS_*} 同风格——
     * 它要直接显示在管理台的面板里，转换一道只是多一处会对不上的地方。
     */
    public static final String SENDER_BUYER = "买家";
    public static final String SENDER_CONSOLE = "运营";

    /**
     * ⚠ 自增主键<b>同时是已读游标的取值</b>（{@code SiteChat.consoleReadId} 比的就是它），
     * 增量拉取的 {@code sinceId} 比的也是它。所以它必须单调递增，
     * 不能改成 UUID、雪花之类「唯一但不保证有序」的东西。
     */
    private Long id;
    private String shopCode;
    private Long chatId;

    /** {@link #SENDER_BUYER} 或 {@link #SENDER_CONSOLE} */
    private String sender;
    /** 哪个操作员回的（{@code u_s_user.username}）。买家发的为 null */
    private String senderUser;

    /**
     * 纯文本。
     *
     * <p>⚠ 存<b>原文</b>，不做 HTML 过滤：过滤会把买家问的「{@code <M8 螺丝}」这类内容吃掉。
     * 安全性由渲染侧保证——前台和管理台都用 Vue 的 {@code {{ }}} 文本插值，
     * 两边都<b>不用 {@code v-html}</b>，所以这里的内容永远不会被当成 HTML 解释。</p>
     */
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getSenderUser() { return senderUser; }
    public void setSenderUser(String senderUser) { this.senderUser = senderUser; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}

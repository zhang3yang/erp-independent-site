package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一条跟进记录。对应表 {@code u_s_followup}。
 *
 * <h3>这个模块补的是什么</h3>
 * <p>{@code docs/独立站与ERP的边界.md} §11 里「营销管理」只有一个名字。落到这个站上，
 * 营销最值钱的一块不是优惠券也不是群发邮件——<b>是「询盘来了没人回」</b>。
 * 这是个 B2B 询盘站：前台不收款、价格 0 表示面议、成交靠人跟出来的报价单。
 * 漏掉一条询盘 = 漏掉一单，而漏掉是静悄悄的。</p>
 *
 * <h3>⚠ 为什么不复用 {@code u_s_inquiry.remark}</h3>
 * <p>那是<b>一个字段</b>，改一次盖一次。而跟进最需要的恰恰是历史——
 * 「上次他说预算不够」「三周后再问一次」，这些话的价值在于时间顺序，
 * 压成一段文本就没了。</p>
 *
 * <h3>⚠ 只记人写的动作</h3>
 * <p>不记系统事件（状态变更、邮件送达）。混在一起的话，真正的对话会被一堆自动流水
 * 淹掉，而这张表存在的意义就是让人一眼看完这条线索的来龙去脉。</p>
 */
public class SiteFollowup implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 目前唯一支持的跟进对象类型。
     *
     * <p>⚠ 表上留了 {@code targettype} 这一列是给订单跟进预备的，
     * 但<b>今天不要往里写别的值</b>——没有任何代码读得懂它们。</p>
     */
    public static final String TARGET_INQUIRY = "inquiry";

    /**
     * 跟进方式的取值。
     *
     * <p>⚠ 它<b>不驱动任何逻辑</b>，纯粹是给人看的分类（列表上一个小标签）。
     * 所以这里只做白名单校验、不做别的——将来加一种方式只用往这个列表里加一个词，
     * 不会牵动任何分支。</p>
     */
    public static final List<String> ACTIONS =
            List.of("记录", "邮件", "电话", "whatsapp", "会面");

    /** {@link #content} 的长度上限，和库里那一列一致 */
    public static final int CONTENT_MAX = 2000;

    private Long id;
    private String siteCode;
    private String targetType;

    /** 对象编码。{@code targetType=inquiry} 时是 {@code u_s_inquiry.inquirycode} */
    private String targetCode;

    private String action;

    /**
     * 跟进内容。
     *
     * <p>⚠ 纯文本。<b>前台永不显示它</b>（这是内部记录），所以不过 {@code RichTextCleaner}
     * 的富文本白名单——那一套是给「会被 v-html 渲染的东西」用的。
     * 管理台里用 {@code {{ }}} 渲染，Vue 自己转义。</p>
     *
     * <p>⚠ 如果哪天要把跟进记录发给买家看（比如「跟进摘要」邮件），
     * <b>那一刻必须回来加转义</b>——那时候它才第一次离开内部。</p>
     */
    private String content;

    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 跟进方式合法吗。⚠ 空值算合法，由服务层兜成默认的「记录」 */
    public static boolean validAction(String action)
    {
        return action == null || action.isBlank() || ACTIONS.contains(action);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String targetCode) { this.targetCode = targetCode; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}

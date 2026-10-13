package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 博客文章。对应表 {@code u_s_post}，文案在 {@code u_s_post_lang}。
 *
 * <h3>这个模块为什么值得做</h3>
 * <p>{@code docs/独立站与ERP的边界.md} §11 把「博客管理」列在「一期不做」里，
 * 理由是「需求现在只有名字」。这条现在不成立：这个站的 SEO 价值目前<b>全压在商品
 * 详情页上</b>（{@code sitemap.xml.ts} 的注释写着这一条），而商品页能覆盖的关键词
 * 就是商品名——覆盖不到「how to choose silicone tubing」这类真正带来询盘的长尾词。</p>
 *
 * <h3>⚠ 两个标识，别混用</h3>
 * <ul>
 *   <li>{@link #postCode} —— 内部编码（{@code CodeGenerator} 发号，{@code BLOG} 前缀）。
 *       语言表的外键、管理台的增删改用它。<b>永不出现在前台 URL 里。</b></li>
 *   <li>{@link #slug} —— 公开 URL 那一段：{@code /blog/how-to-choose-silicone-tubing}。</li>
 * </ul>
 *
 * <p>为什么不像商品那样直接用编码做 URL（{@code /products/G001}）：
 * <b>商品那样是可以的</b>，买家搜的是商品名而商品名在 {@code <title>} 里；
 * 博客不是——博客整个存在的理由就是自然搜索，而 URL 里的关键词是排名因素之一。
 * {@code /blog/BLOG0000000001} 等于把这个优势扔掉。</p>
 *
 * <h3>⚠⚠ 缺译文时的行为和商品<b>故意相反</b></h3>
 * <p>商品缺某语言文案会回退站点默认语言（宁可标题是英文，也不能让日语站少一个能
 * 下单的商品）。文章缺某语言标题则<b>那个语言版本直接不存在</b>：列表里没有、详情 404、
 * sitemap 不列、hreflang 也不列。</p>
 *
 * <p>一篇英文长文摆在日语站上对日本读者没有任何价值，而它会被 Google 判成
 * 「hreflang 说是日语、内容是英文」——比少一篇文章更伤。商品页的标题短、
 * 图和价格自己会说话；文章除了文字什么都没有。</p>
 */
public class SitePost implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 草稿。只在管理台可见 */
    public static final String STATUS_DRAFT = "草稿";
    /** 已发布。**唯一**对外可见的状态 */
    public static final String STATUS_PUBLISHED = "已发布";
    /** 已下架。曾经发布过、现在收回。前台等同于不存在 */
    public static final String STATUS_OFFLINE = "已下架";

    /**
     * slug 的合法形状：小写字母、数字、连字符，不以连字符开头或结尾。
     *
     * <p>⚠ 为什么卡这么死：它是 URL 的一部分。大写字母会让
     * {@code /blog/Foo} 和 {@code /blog/foo} 变成两个地址（Google 眼里是内容重复）；
     * 下划线在 URL 里 Google 不当分词符（连字符才是）；空格和中文要 percent-encode，
     * 出来的地址没法在邮件里粘贴。</p>
     *
     * <p>长度上限 160 —— 库里那一列是 {@code varchar(160)}。</p>
     */
    public static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    /** slug 的长度上限，和库里那一列一致 */
    public static final int SLUG_MAX = 160;

    private String siteCode;
    private String postCode;
    private String slug;
    private String category;
    private String coverImg;

    /** 署名。⚠ 留空则前台不显示作者行——不要兜底成公司名，那看起来像是没填 */
    private String author;

    private String publishStatus;

    /**
     * 发布时间。
     *
     * <p>⚠ 和 {@link #createTime} 是两件事，别用后者代替：一篇文章可能写了两周才发，
     * 按创建时间排序会让它排在这两周里发的所有文章之前。</p>
     *
     * <p>首次转「已发布」时由 {@code SitePostService} 自动填当前时间，之后可手工改
     * ——补录一篇旧文章时要能把日期写成当时的。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    /** 置顶用，越小越靠前。⚠ 排序是 sortNum 再 publishTime 倒序——全为 0 时就是纯时间序 */
    private Integer sortNum;

    private String remark;
    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ==================== 非表字段 ====================

    /**
     * 这篇文章有译文的语言。
     *
     * <p>只在<b>对外</b>的查询里填（{@code SitePostMapper} 的 {@code selectPublic*}），
     * 管理台列表不填。前台用它拼 hreflang：<b>只列真的有译文的语言</b>，
     * 否则等于向 Google 承诺一个 404。</p>
     */
    private List<String> langs = new ArrayList<>();

    /**
     * {@link #langs} 的原始形态：逗号分隔的语言代码串。
     *
     * <p>⚠ <b>只有 {@code selectSitemapRows} 会填它</b>——那一条用 {@code group_concat}
     * 一次把整个矩阵拿回来（一行一篇文章，带上它所有有译文的语言），
     * 而 MyBatis 没法把一个逗号串直接映射成 {@code List<String>}。</p>
     *
     * <p>由 {@code SitePostService} 调 {@link #splitLangCodes()} 拆进 {@link #langs}，
     * 之后就不该再读这个字段。留它是因为「在 SQL 里 group_concat」比
     * 「对每篇文章再查一次语言」少 N 次往返，而 sitemap 一次要拿几百篇。</p>
     */
    private String langCodes;

    /** 当前语言的标题。对外查询时从语言表 join 出来 */
    private String title;

    /** 当前语言的摘要 */
    private String summary;

    /** 当前语言的正文富文本。⚠ 只在详情接口里返回，列表接口不带（它可以很大） */
    private String content;

    private String keywords;

    /**
     * 把 slug 规整成合法形状。
     *
     * <p>转小写、非法字符换连字符、压掉连续的连字符、去掉首尾连字符、截到上限。
     * <b>不做音译</b>：中文标题生成出来会是一串连字符，那时候返回空串，
     * 由调用方要求人工填——猜一个不可读的 slug 比让人填一个更糟。</p>
     */
    public static String normalizeSlug(String raw)
    {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (s.length() > SLUG_MAX)
        {
            s = s.substring(0, SLUG_MAX).replaceAll("-+$", "");
        }
        return s;
    }

    /**
     * 把 {@link #langCodes} 拆进 {@link #langs}，并清掉前者。
     *
     * <p>拆完就清是有意的：两个字段表示同一件事时，「该读哪个」这个问题
     * 每个调用点都要想一遍，早晚有一处读了那个没更新的。清成 null 之后，
     * 谁误读它都会立刻拿到空，而不是拿到一个陈旧的串。</p>
     */
    public void splitLangCodes()
    {
        List<String> out = new ArrayList<>();
        if (langCodes != null)
        {
            for (String one : langCodes.split(","))
            {
                String t = one.trim();
                if (!t.isEmpty() && !out.contains(t)) out.add(t);
            }
        }
        this.langs = out;
        this.langCodes = null;
    }

    /** 对外可见吗。⚠ 只有「已发布」——和 {@code u_s_sitegoods} 的「已上架」同一个口径 */
    public boolean publicVisible()
    {
        return STATUS_PUBLISHED.equals(publishStatus);
    }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCoverImg() { return coverImg; }
    public void setCoverImg(String coverImg) { this.coverImg = coverImg; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }

    public Date getPublishTime() { return publishTime; }
    public void setPublishTime(Date publishTime) { this.publishTime = publishTime; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

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

    public List<String> getLangs() { return langs; }
    public void setLangs(List<String> langs) { this.langs = langs; }

    public String getLangCodes() { return langCodes; }
    public void setLangCodes(String langCodes) { this.langCodes = langCodes; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
}

package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SitePost;
import com.ruoyi.station.console.domain.SitePostLang;

/**
 * 博客文章。对应表 {@code u_s_post} 和 {@code u_s_post_lang}。
 *
 * <h3>对外和后台的语句刻意分开写</h3>
 * <p>和 {@link SiteGoodsMapper} 同一条规矩：对外那几条（{@code selectPublic*}）永远带
 * {@code publishstatus = '已发布'}，后台那几条（{@code selectAdmin*}）永远不带。
 * 混在一起用参数控制，早晚会有人传错值把草稿捅到公网上。</p>
 *
 * <h3>⚠⚠ 对外的查询用 inner join，和商品那边相反</h3>
 * <p>{@link SiteGoodsMapper} 的 {@code selectPublic*} 是 {@code left join} + 按
 * 「当前语言 → 默认语言」回退：商品必须能被买到，宁可标题是英文，
 * 也不能让日语站少一个可下单的商品。</p>
 *
 * <p>文章反过来：{@code inner join} + 要求 {@code title} 非空，
 * <b>某语言没有译文就等于那个语言版本不存在</b>。一篇英文长文摆在日语站上对读者
 * 没有价值，而它会被 Google 判成「hreflang 说是日语、内容是英文」——
 * 比少一篇文章更伤。</p>
 *
 * <p>⚠ 这条规则要在<b>三处</b>一起生效，缺一处就漏：这里的 {@code selectPublic*}、
 * sitemap（只列有译文的语言）、以及页面上的 hreflang（同上）。
 * 后两处靠 {@link #selectPublicLangs} 和 {@link #selectSitemapRows} 提供的语言集合。</p>
 *
 * <h3>方法签名里为什么没有 siteCode</h3>
 * <p>一个部署一个站，这个库里只有这一个站的文章，那一列不是筛选维度（boundary §5.3）。
 * 写入时它由 Service 从站点档案填。</p>
 */
public interface SitePostMapper
{
    // ==================== 对外（前台）：只返回已发布，且该语言有译文 ====================

    /**
     * 对外的文章列表。
     *
     * <p>⚠ <b>不返回正文</b>（{@code content} 可以很大，列表页也用不上它）。
     * 卡片上的正文用 {@code summary}。</p>
     *
     * @param lang     当前语言。⚠ <b>没有默认语言回退</b>，见类注释
     * @param keyword  关键字，匹配标题和摘要；为空不过滤
     * @param category 博客分类；为空不过滤
     */
    List<SitePost> selectPublicList(@Param("lang") String lang, @Param("keyword") String keyword,
            @Param("category") String category, @Param("offset") int offset, @Param("limit") int limit);

    /** 对外列表的总数，条件与 {@link #selectPublicList} 完全一致 */
    int countPublicList(@Param("lang") String lang, @Param("keyword") String keyword,
            @Param("category") String category);

    /**
     * 对外的文章详情，按 <b>slug</b> 查——那是公开 URL 上的那一段。
     *
     * <p>⚠ 不是按 {@code postcode} 查：编码不出现在前台任何地方。</p>
     *
     * <p>查不到有两种情况，对外<b>都是 404</b>，不区分：文章不存在，
     * 或者这个语言没有译文。区分了等于告诉外面「这篇文章存在但你看不到」，
     * 而那个信息对访客没有用。</p>
     */
    SitePost selectPublicOne(@Param("slug") String slug, @Param("lang") String lang);

    /**
     * 这篇文章<b>有译文的语言</b>。给 hreflang 用。
     *
     * <p>⚠ 只回 {@code title} 非空的那些语言。全列出来等于向 Google 承诺
     * 一批 404 地址——hreflang 里有一个坏链接，Google 会忽略<b>整组</b>声明
     * （见 {@code useSeo.ts} 里那三条规矩）。</p>
     *
     * <p>⚠ 这里<b>不按站点开的语言过滤</b>：库里可能留着站点已经关掉的语言的旧译文。
     * 过滤放在 Service 层（那里才知道站点当前开了哪些语言），
     * 免得这条语句要多一个参数、而 mapper 层并不需要知道这件事。</p>
     */
    List<String> selectPublicLangs(@Param("postCode") String postCode);

    /** 已发布文章用到的分类，前台列表页的筛选按钮由它去重生成 */
    List<String> selectPublicCategories();

    /**
     * 给 sitemap 用：<b>每一篇</b>已发布文章 + 它有译文的语言。
     *
     * <h3>⚠ 为什么不复用 {@link #selectPublicList}</h3>
     * <p>那一条是<b>按语言过滤</b>的（它服务的是「/ja/blog 这一页显示什么」）。
     * 用它生成 sitemap 会漏掉「只翻译了日语、没有默认语言版本」的文章——
     * 那种文章确实存在（先写日语版是正常的工作方式），而漏进 sitemap
     * 的表现是「文章在站上能打开、但 Google 一直发现不了」，
     * 恰好是最难注意到的一种。</p>
     *
     * <p>所以 sitemap 要的是整个矩阵：一行一篇文章，带上它所有有译文的语言。
     * 回的字段只有 {@code slug} / {@code publishTime} / {@code langs} 三样，
     * 标题和正文一个都不带。</p>
     *
     * @param limit 条数上限，来自 {@code u_s_seo.sitemapmax}
     */
    List<SitePost> selectSitemapRows(@Param("limit") int limit);

    // ==================== 后台：含草稿和已下架，绝不能被对外接口调用 ====================

    /**
     * 后台列表。文案取指定语言那一条给列表显示（取不到就是空标题，
     * 那正是管理台要让人看见的状态）。
     */
    List<SitePost> selectAdminList(@Param("lang") String lang, @Param("keyword") String keyword,
            @Param("publishStatus") String publishStatus, @Param("category") String category);

    /** 后台按内部编码查一条，不限状态 */
    SitePost selectAdminOne(@Param("postCode") String postCode);

    /**
     * slug 被别的文章占了吗。
     *
     * <p>{@code exceptPostCode} 传当前文章的编码（改的时候），它自己不算冲突。
     * 新建时传 null。</p>
     *
     * <p>⚠ 这个检查是为了给一句<b>说得清的错误</b>，不是为了保证唯一——
     * 唯一性由 {@code uk_post_slug} 那个唯一索引保证。并发下这里查过之后
     * 仍然可能撞上，那时候由索引抛出来，Service 兜成同一句话。</p>
     */
    int countBySlug(@Param("slug") String slug, @Param("exceptPostCode") String exceptPostCode);

    int insert(SitePost post);

    int update(SitePost post);

    /** 批量改状态，「发布 / 下架」按钮走它 */
    int updateStatus(@Param("postCodes") String[] postCodes,
            @Param("publishStatus") String publishStatus,
            @Param("publishTime") java.util.Date publishTime,
            @Param("updateUser") String updateUser);

    int deleteByCodes(@Param("postCodes") String[] postCodes);

    // ==================== 多语言文案 ====================

    /** 一篇文章的全部语言文案，编辑弹窗按语言分页签展示 */
    List<SitePostLang> selectLangs(@Param("postCode") String postCode);

    /** 存一种语言的文案。有则更新无则插入 */
    int upsertLang(SitePostLang lang);

    int deleteLangsByPosts(@Param("postCodes") String[] postCodes);
}

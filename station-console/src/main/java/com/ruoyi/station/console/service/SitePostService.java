package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SitePost;
import com.ruoyi.station.console.domain.SitePostLang;
import com.ruoyi.station.console.mapper.SitePostMapper;
import com.ruoyi.station.console.util.RichTextCleaner;

/**
 * 博客文章的后台读写。
 *
 * <p>对外那一侧（前台列表 / 详情 / sitemap）不走这里，走
 * {@link SiteOpenService}——和商品一样的分工：对外接口的取数集中在那一个类里，
 * 它的类注释上挂着「只读已发布、字段白名单、唯一的写是询盘」三条硬规矩。</p>
 *
 * <h3>状态机很小，但有一处不显然</h3>
 * <p>草稿 → 已发布 → 已下架 → 已发布，随便转。唯一有记忆的是发布时间：
 * <b>它只在第一次发布时填，之后不会被再次发布覆盖</b>。一篇下架后重新上线的文章，
 * 日期应该还是当初那个——外链和收录都指着那个日期，跳到今天等于告诉 Google
 * 「这是篇新文章」，而内容并没有变。</p>
 */
@Service
public class SitePostService
{
    private static final Logger log = LoggerFactory.getLogger(SitePostService.class);

    private static final Set<String> STATUSES = new LinkedHashSet<>(List.of(
            SitePost.STATUS_DRAFT, SitePost.STATUS_PUBLISHED, SitePost.STATUS_OFFLINE));

    /**
     * slug 里不许出现的整段词。
     *
     * <p>只挡一个：{@code blog}。因为前台的地址是 {@code /blog/<slug>}，
     * 一个叫 {@code blog} 的 slug 会生成 {@code /blog/blog}——不报错，
     * 只是看起来像配错了。别的路径段（{@code products} / {@code cart}）不用挡：
     * 它们在 {@code /blog/} 底下不会和任何路由冲突。</p>
     */
    private static final Set<String> SLUG_RESERVED = Set.of("blog");

    @Autowired
    private SitePostMapper postMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private CodeGenerator codeGenerator;

    // ==================== 查 ====================

    /**
     * 后台列表。
     *
     * <p>列表上显示的是<b>站点默认语言</b>那一条文案；某篇文章没填默认语言标题时
     * 标题是空的——那正是管理台该让人看见的状态，不要在这里兜底成 slug 或编码。</p>
     */
    public List<SitePost> adminList(String keyword, String publishStatus, String category)
    {
        SiteInfo site = siteInfoService.require();
        return postMapper.selectAdminList(site.defaultLang(), trimToNull(keyword),
                trimToNull(publishStatus), trimToNull(category));
    }

    /**
     * 后台详情：档案 + 全部语言文案 + 站点开着的语言列表。
     *
     * <p>{@code siteLangs} 是给编辑弹窗排语言页签用的。⚠ 它是<b>站点当前开的语言</b>，
     * 不是「库里有译文的语言」——后者可能包含站点已经关掉的语言的旧译文，
     * 那些不该再摆出来让人继续维护。</p>
     */
    public Map<String, Object> adminDetail(String postCode)
    {
        SiteInfo site = siteInfoService.require();
        String code = trimToNull(postCode);
        if (code == null) throw new ServiceException("文章编码不能为空");

        SitePost post = postMapper.selectAdminOne(code);
        if (post == null) throw new ServiceException("这篇文章不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("post", post);
        result.put("langs", postMapper.selectLangs(code));
        result.put("siteLangs", site.langList());
        return result;
    }

    // ==================== 增 ====================

    /**
     * 新建一篇文章。
     *
     * <p>只建骨架（编码 + slug + 状态草稿），文案由随后的保存写入——和商品那边
     * 「先建档再填文案」同一个流程。这样新建对话框只要两个必填项，
     * 而不是一上来就要求把八种语言都填好。</p>
     *
     * <p>⚠ 新建<b>一律是草稿</b>，不接受前端传状态：一篇还没有任何正文的文章
     * 不该有机会直接出现在公网上。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public String createPost(SitePost form, String operator)
    {
        SiteInfo site = siteInfoService.require();
        if (form == null) throw new ServiceException("参数不能为空");

        String slug = requireSlug(form.getSlug(), null);

        SitePost post = new SitePost();
        post.setSiteCode(site.getSiteCode());
        post.setPostCode(codeGenerator.nextPostCode());
        post.setSlug(slug);
        post.setCategory(trimToNull(form.getCategory()));
        post.setCoverImg(trimToNull(form.getCoverImg()));
        post.setAuthor(trimToNull(form.getAuthor()));
        post.setPublishStatus(SitePost.STATUS_DRAFT);
        post.setSortNum(form.getSortNum() == null ? 0 : form.getSortNum());
        post.setRemark(trimToNull(form.getRemark()));
        post.setCreateUser(operator);
        post.setUpdateUser(operator);

        insertOrExplain(post);
        log.info("新建博客文章 {}（slug={}，操作人 {}）", post.getPostCode(), slug, operator);
        return post.getPostCode();
    }

    // ==================== 改 ====================

    /**
     * 保存档案和各语言文案。
     *
     * <p>档案和文案在一个事务里存：只存住一半的话，站上会出现「有标题没封面」
     * 这种半截数据。</p>
     *
     * <p>⚠ 转「已发布」之前必须有<b>至少一种语言</b>的标题。不要求默认语言——
     * 先写日语版再补英文是正常的工作方式，而这个模块的规则本来就是
     * 「哪种语言有译文，哪种语言才有这篇文章」。一种都没有的话，
     * 发布出去就是一篇在每个语言下都不存在的文章，等于什么都没发生。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int savePost(SitePost form, List<Map<String, Object>> langs, String operator)
    {
        SiteInfo site = siteInfoService.require();
        if (form == null) throw new ServiceException("参数不能为空");

        String code = trimToNull(form.getPostCode());
        if (code == null) throw new ServiceException("文章编码不能为空");

        SitePost old = postMapper.selectAdminOne(code);
        if (old == null) throw new ServiceException("这篇文章不存在");

        String slug = requireSlug(form.getSlug(), code);

        String status = trimToNull(form.getPublishStatus());
        if (status == null) status = old.getPublishStatus();
        if (!STATUSES.contains(status))
        {
            throw new ServiceException("状态只能是：" + String.join("、", STATUSES));
        }

        // 站点开着的语言里，这次提交后**有标题**的那些
        Set<String> allowed = new LinkedHashSet<>(site.langList());
        Set<String> withTitle = new LinkedHashSet<>();
        for (Map<String, Object> item : langs == null ? new ArrayList<Map<String, Object>>() : langs)
        {
            String lang = trimToNull(str(item.get("lang")));
            if (lang != null && allowed.contains(lang) && trimToNull(str(item.get("title"))) != null)
            {
                withTitle.add(lang);
            }
        }
        if (SitePost.STATUS_PUBLISHED.equals(status) && withTitle.isEmpty())
        {
            throw new ServiceException("发布之前至少要填一种语言的标题。"
                    + "这个模块的规则是「哪种语言有标题，哪种语言才有这篇文章」——"
                    + "一种都没有的话，发布出去在每个语言下都是不存在的");
        }

        form.setPostCode(code);
        form.setSlug(slug);
        form.setPublishStatus(status);
        form.setCategory(trimToNull(form.getCategory()));
        form.setCoverImg(trimToNull(form.getCoverImg()));
        form.setAuthor(trimToNull(form.getAuthor()));
        form.setRemark(trimToNull(form.getRemark()));
        form.setUpdateUser(operator);

        /*
         * 发布时间。
         *
         * ⚠ 三种情况分清楚：
         *   · 前端明确传了值 → 用它（补录旧文章时要能把日期写成当时的）；
         *   · 首次转「已发布」而库里还没有 → 填当前时间；
         *   · 其余 → 传 null，由 mapper 的 coalesce 保持原值。
         *
         * 第三条是关键：一次「只改了标题」的保存不能把发布时间清掉，
         * 而那个症状是列表排序突然乱掉、sitemap 里那篇的 lastmod 没了。
         */
        if (form.getPublishTime() == null
                && SitePost.STATUS_PUBLISHED.equals(status)
                && old.getPublishTime() == null)
        {
            form.setPublishTime(new Date());
        }

        // ⚠ 改已发布文章的 slug = 换地址。挡不住（有时确实要改），但要留痕：
        //   老地址会 404，已经积累的排名和外链一起丢，而这件事在界面上看不出来。
        if (!slug.equals(old.getSlug()) && SitePost.STATUS_PUBLISHED.equals(old.getPublishStatus()))
        {
            log.warn("⚠ 已发布文章 {} 的 slug 从「{}」改成「{}」（操作人 {}）。"
                    + "老地址 /blog/{} 从此 404——已经积累的排名和外链会丢。"
                    + "这个站没有 301 重定向表（站长工具那一轮刻意没做），所以救不回来",
                    code, old.getSlug(), slug, operator, old.getSlug());
        }

        int rows = updateOrExplain(form);

        for (Map<String, Object> item : langs == null ? new ArrayList<Map<String, Object>>() : langs)
        {
            String lang = trimToNull(str(item.get("lang")));
            if (lang == null) continue;
            // 站点没开的语言不许存：存了也没人读得到，只会变成一堆没人维护的僵尸文案。
            // 同 SiteGoodsService.saveGoods 的处理。
            if (!allowed.contains(lang)) continue;

            SitePostLang text = new SitePostLang();
            text.setPostCode(code);
            text.setLang(lang);
            text.setTitle(trimToNull(str(item.get("title"))));
            text.setSummary(trimToNull(str(item.get("summary"))));
            /*
             * 正文会被前台当 HTML 渲染（blog/[slug].vue 里的 v-html），在这里过白名单。
             *
             * ⚠ 放服务层而不是靠 filter：这个方法是文章正文唯一的写入路径，
             *   而 Servlet 层的过滤器很容易漏配路径。前台是 Nuxt、v-html 渲染，
             *   指望它转义是不成立的。和 SiteGoodsService 里那段是同一条规矩，
             *   **不因为换了张表就放宽**。
             */
            text.setContent(RichTextCleaner.clean(trimToNull(str(item.get("content")))));
            text.setKeywords(trimToNull(str(item.get("keywords"))));
            postMapper.upsertLang(text);
        }

        return rows;
    }

    /**
     * 批量改状态。
     *
     * <p>⚠ 发布时间只在它原来是空的时候填（判断在 SQL 里，见 mapper）：
     * 下架后重新发布的文章，日期应该还是当初那个。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateStatus(String[] postCodes, String publishStatus, String operator)
    {
        if (postCodes == null || postCodes.length == 0)
        {
            throw new ServiceException("没有选中任何文章");
        }
        String status = trimToNull(publishStatus);
        if (status == null || !STATUSES.contains(status))
        {
            throw new ServiceException("状态只能是：" + String.join("、", STATUSES));
        }

        /*
         * ⚠ 批量发布**不检查「有没有标题」**，和 savePost 那里不一样。
         *
         *   不是漏了：这个入口是列表页上的批量按钮，一次可能勾了二十篇。
         *   为其中一篇没填标题就整批失败，会让人不知道是哪一篇；
         *   而逐篇检查再报「第 7、13 篇不行」也帮不上忙——他还得回去一篇篇找。
         *
         *   没标题的文章发布出去的后果是「在每个语言下都不存在」，
         *   也就是等于没发布，看得见（列表里状态变了、前台没有），改得回来。
         *   真正要挡住的是「有标题但内容是草稿」，而那个挡不住也不该挡。
         */
        Date now = SitePost.STATUS_PUBLISHED.equals(status) ? new Date() : null;
        int rows = postMapper.updateStatus(postCodes, status, now, operator);
        log.info("批量改文章状态：{} 篇 → {}（操作人 {}）", rows, status, operator);
        return rows;
    }

    // ==================== 删 ====================

    /**
     * 删文章。档案和文案一起删，在一个事务里。
     *
     * <p>⚠ 先删文案再删档案：反过来的话，档案删掉、文案删除失败时，
     * 语言表里会留下一批<b>没有主表的孤儿行</b>——而它们不会被任何查询发现
     * （所有读都从 {@code u_s_post} 起手 join），只会在库里一直占着，
     * 直到某天有人按 postcode 建了一篇新文章、然后看到一堆来历不明的旧译文。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int deletePosts(String[] postCodes, String operator)
    {
        if (postCodes == null || postCodes.length == 0)
        {
            throw new ServiceException("没有选中任何文章");
        }
        postMapper.deleteLangsByPosts(postCodes);
        int rows = postMapper.deleteByCodes(postCodes);
        log.info("删除博客文章 {} 篇（操作人 {}）", rows, operator);
        return rows;
    }

    // ==================== slug ====================

    /**
     * 校验并规整 slug。
     *
     * <p>前端会拿标题生成一个建议值，但<b>这里不替人生成</b>：
     * 一个空的或者非法的 slug 一律报错要求人填。</p>
     *
     * <p>⚠ 为什么不自动兜底（比如用 postcode 当 slug）：那样一篇文章会带着
     * {@code /blog/blog0000000001} 这种地址上线，而它看起来是「正常工作」的
     * ——没人会回来改。博客的 URL 是这个模块唯一的产出物之一，
     * 值得在这里挡一下。</p>
     */
    private String requireSlug(String raw, String exceptPostCode)
    {
        String input = trimToNull(raw);
        if (input == null)
        {
            throw new ServiceException("URL 短名（slug）不能为空。它是文章的公开地址那一段，"
                    + "如 /blog/how-to-choose-silicone-tubing 里的 how-to-choose-silicone-tubing");
        }

        String slug = SitePost.normalizeSlug(input);
        if (slug.isEmpty())
        {
            // 中文标题会走到这里：normalizeSlug 把非 ASCII 全换成连字符再去掉，剩下空串
            throw new ServiceException("URL 短名（slug）只能用小写英文字母、数字和连字符，"
                    + "请手工填一个。⚠ 中文标题生成不出来——这里刻意不做音译，"
                    + "猜出来的拼音串既不可读也不含关键词");
        }
        if (!SitePost.SLUG.matcher(slug).matches())
        {
            throw new ServiceException("URL 短名（slug）格式不对：只能是小写字母、数字和连字符，"
                    + "且不能以连字符开头或结尾");
        }
        if (SLUG_RESERVED.contains(slug))
        {
            throw new ServiceException("URL 短名（slug）不能叫「" + slug + "」——"
                    + "文章地址是 /blog/<短名>，那样会生成 /blog/blog");
        }
        if (postMapper.countBySlug(slug, exceptPostCode) > 0)
        {
            throw new ServiceException("URL 短名「" + slug + "」已经被另一篇文章用了。"
                    + "它是文章的公开地址，全站只能有一个");
        }
        return slug;
    }

    /**
     * 插入，并把唯一索引冲突翻成一句人话。
     *
     * <p>⚠ {@link #requireSlug} 已经查过一遍重了，但那是「查完再插」——
     * 两个并发请求可以都查过、都往下插。真正保证唯一的是
     * {@code uk_post_slug} 那个索引，而它抛出来的是一句带 SQL 的
     * {@code DuplicateKeyException}。兜成同一句话，免得界面上出现一段栈。</p>
     */
    private void insertOrExplain(SitePost post)
    {
        try
        {
            postMapper.insert(post);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("URL 短名「" + post.getSlug() + "」已经被占用了，请换一个");
        }
    }

    private int updateOrExplain(SitePost post)
    {
        try
        {
            return postMapper.update(post);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("URL 短名「" + post.getSlug() + "」已经被占用了，请换一个");
        }
    }

    // ==================== 小工具 ====================

    private static String trimToNull(String s)
    {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String str(Object o)
    {
        return o == null ? null : String.valueOf(o);
    }
}

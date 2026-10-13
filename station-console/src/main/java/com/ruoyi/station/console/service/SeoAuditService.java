package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.domain.SeoAuditRow;
import com.ruoyi.station.console.domain.SeoIssue;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteSeoMapper;

/**
 * SEO 体检。对现有数据做检查，<b>不建业务表、不落库</b>。
 *
 * <h3>它检查什么，为什么是这些</h3>
 * <p>这个站的 SEO 价值几乎全在商品详情页上（{@code sitemap.xml.ts} 的注释里写着这一条）。
 * 而详情页的 {@code <title>} 和 {@code <meta description>} 来自
 * {@code u_s_sitegoods_lang} 的 {@code title} / {@code subtitle}
 * （见 {@code products/[code].vue} 里那次 {@code useSiteSeo} 调用）。
 * 所以体检的重心就是「每个上架商品、每种开着的语言，这两个值填了没有、填得合不合规」。</p>
 *
 * <h3>⚠ 有一件事它检查不了，界面上必须说出来</h3>
 * <p><b>canonical 指向的域名对不对。</b>那个值来自 {@code NUXT_PUBLIC_SITE_URL}，
 * 是 station-site 那个 Node 进程的环境变量，这个后端读不到它。而按
 * {@code nuxt.config.ts} 里的注释，它设错时「只有 canonical 看得出来」
 * ——埋点会静默发到错的域上，页面完全正常。</p>
 *
 * <p>所以体检结果里带一条 {@code notChecked} 说明 + 一条 curl 命令让人自己验。
 * 假装检查过了比不检查更坏：那会让人以为这一项已经有人看着了。</p>
 *
 * <h3>⚠ 为什么不落库</h3>
 * <p>落库就会出现「已经改好了但体检表还在报警」。一份需要人手动刷新才准的检查清单，
 * 比没有清单更糟——因为它会被信。代价是每次打开扫一遍，两条查询，商品在几百量级。</p>
 */
@Service
public class SeoAuditService
{
    private static final Logger log = LoggerFactory.getLogger(SeoAuditService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 标题长度的建议区间。
     *
     * <p>60 是 Google 在桌面搜索结果里截断标题的大致宽度（它按像素算，不按字符，
     * 所以这是个经验值不是硬规则——这也是为什么超了只算「中」不算「高」）。</p>
     *
     * <p>下限 10：比这更短的标题基本不含关键词，等于放弃这个页面的搜索入口。</p>
     */
    private static final int TITLE_MIN = 10;
    private static final int TITLE_MAX = 60;

    /**
     * 描述长度的建议区间。
     *
     * <p>160 同样是截断宽度的经验值。下限 50：太短的描述 Google 往往干脆不用，
     * 改成自己从正文里截一段——那时候这个字段等于没填。</p>
     */
    private static final int DESC_MIN = 50;
    private static final int DESC_MAX = 160;

    @Autowired
    private SiteSeoMapper siteSeoMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    /**
     * 跑一次体检。
     *
     * <p>返回给管理台的整份结果：问题清单 + 汇总数字 + 那条「查不了什么」的说明。</p>
     */
    public Map<String, Object> audit()
    {
        SiteInfo site = siteInfoService.require();
        List<String> langs = site.langList();
        Set<String> openLangs = new HashSet<>(langs);

        List<SeoIssue> issues = new ArrayList<>();
        int skipped = 0;

        // ==================== 商品 ====================

        List<SeoAuditRow> rows;
        try
        {
            rows = siteSeoMapper.selectAuditRows();
        }
        catch (Exception e)
        {
            // 取不到数就说取不到，不要回一个空清单——空清单看起来和「一切正常」一样。
            log.error("SEO 体检取数失败：{}", e.getMessage(), e);
            throw new ServiceException("体检取数失败，请稍后再试");
        }

        /*
         * 先按商品把行归拢。
         *
         * ⚠ 一个商品可能有 0 条译文（mapper 用的是 left join，那时 lang 为 null）。
         *   那正是最严重的一种情况，所以这里必须允许「这个商品在 map 里、
         *   但它的语言集合是空的」，不能顺手把 null 行丢掉。
         */
        Map<String, String> goodsNames = new LinkedHashMap<>();
        Map<String, Boolean> goodsHasImage = new HashMap<>();
        Map<String, Map<String, SeoAuditRow>> byGoods = new LinkedHashMap<>();

        for (SeoAuditRow row : rows)
        {
            String code = row.getGoodsCode();
            if (code == null || code.isBlank())
            {
                // 理论上不可能（它在主键里），但真出现了就跳过并计数，
                // 不让一行坏数据把整张体检表变成一个 500
                skipped++;
                continue;
            }
            goodsNames.putIfAbsent(code, blankToCode(row.getGoodsName(), code));
            goodsHasImage.putIfAbsent(code, row.imagePresent());
            byGoods.computeIfAbsent(code, k -> new LinkedHashMap<>());
            if (row.getLang() != null && !row.getLang().isBlank())
            {
                byGoods.get(code).put(row.getLang(), row);
            }
        }

        // 查重用：同一种语言下，标题一模一样的商品
        Map<String, Map<String, List<String>>> titleIndex = new HashMap<>();

        for (Map.Entry<String, Map<String, SeoAuditRow>> entry : byGoods.entrySet())
        {
            String code = entry.getKey();
            String name = goodsNames.get(code);
            Map<String, SeoAuditRow> langRows = entry.getValue();

            for (String lang : langs)
            {
                SeoAuditRow row = langRows.get(lang);

                // ---- 标题 ----
                String title = row == null ? null : trim(row.getTitle());
                if (title == null)
                {
                    /*
                     * ⚠ 后果要写清楚，不能只说「标题缺失」。
                     *
                     * 前台在这种情况下会回退到站点默认语言的文案
                     * （SiteGoodsMapper 的 selectPublic* 都带 defaultLang 回退），
                     * 所以页面不会空——它会显示**另一种语言**的标题。
                     * 那比空白更糟：Google 看到的是一个 hreflang 说是日语、
                     * 而 <title> 是英文的页面。
                     */
                    issues.add(SeoIssue.goods(SeoIssue.LEVEL_HIGH, "goods.title.missing",
                            code, name, lang,
                            "没有 " + lang + " 标题。前台会回退显示站点默认语言的标题，"
                            + "于是这个页面的 hreflang 说是 " + lang + "、<title> 却是另一种语言"));
                }
                else
                {
                    int len = title.length();
                    if (len > TITLE_MAX)
                    {
                        issues.add(SeoIssue.goods(SeoIssue.LEVEL_MEDIUM, "goods.title.long",
                                code, name, lang,
                                "标题 " + len + " 字符，超过 " + TITLE_MAX
                                + "，在搜索结果里会被截断（Google 按像素算，这是经验值）"));
                    }
                    else if (len < TITLE_MIN)
                    {
                        issues.add(SeoIssue.goods(SeoIssue.LEVEL_MEDIUM, "goods.title.short",
                                code, name, lang,
                                "标题只有 " + len + " 字符，基本不含关键词"));
                    }
                    titleIndex.computeIfAbsent(lang, k -> new HashMap<>())
                            .computeIfAbsent(title, k -> new ArrayList<>())
                            .add(code);
                }

                // ---- 描述 ----
                //
                // ⚠ 口径要和前台一致：详情页的 meta description 首选 subtitle，
                //   subtitle 为空时从 description 富文本里剥标签截 160 字
                //   （见 products/[code].vue）。所以「有 description 但没 subtitle」
                //   不是「缺描述」，只是描述质量不可控——算「中」。
                String subtitle = row == null ? null : trim(row.getSubtitle());
                boolean hasDesc = row != null && row.descPresent();
                if (subtitle == null && !hasDesc)
                {
                    issues.add(SeoIssue.goods(SeoIssue.LEVEL_HIGH, "goods.desc.missing",
                            code, name, lang,
                            "没有 " + lang + " 的卖点，详情富文本也是空的。"
                            + "这个页面的 meta description 会是空的，"
                            + "搜索结果里的摘要由 Google 自己从页面上乱截"));
                }
                else if (subtitle == null)
                {
                    issues.add(SeoIssue.goods(SeoIssue.LEVEL_MEDIUM, "goods.desc.fromhtml",
                            code, name, lang,
                            "没填卖点，meta description 会从详情富文本里剥标签截前 "
                            + DESC_MAX + " 字——截出来的句子通常是断的"));
                }
                else
                {
                    int len = subtitle.length();
                    if (len > DESC_MAX)
                    {
                        issues.add(SeoIssue.goods(SeoIssue.LEVEL_MEDIUM, "goods.desc.long",
                                code, name, lang,
                                "卖点 " + len + " 字符，超过 " + DESC_MAX + "，摘要会被截断"));
                    }
                    else if (len < DESC_MIN)
                    {
                        issues.add(SeoIssue.goods(SeoIssue.LEVEL_MEDIUM, "goods.desc.short",
                                code, name, lang,
                                "卖点只有 " + len + " 字符，太短的描述 Google 往往不用、"
                                + "改成自己从正文里截一段"));
                    }
                }
            }

            // ---- 图 ----
            if (!Boolean.TRUE.equals(goodsHasImage.get(code)))
            {
                issues.add(SeoIssue.goods(SeoIssue.LEVEL_LOW, "goods.image.missing",
                        code, name, null,
                        "没有图片，og:image 会回落到站点 logo——分享到 WhatsApp / LinkedIn 时"
                        + "所有商品的卡片长得一样"));
            }

            // ---- 库里有译文、但站点没开这个语言 ----
            //
            // 不是错误，只是提醒：这些译文对外不可见，白填了。算「低」。
            for (String lang : langRows.keySet())
            {
                if (!openLangs.contains(lang))
                {
                    issues.add(SeoIssue.goods(SeoIssue.LEVEL_LOW, "goods.lang.notopen",
                            code, name, lang,
                            "填了 " + lang + " 文案，但站点语言里没开 " + lang + "，这份译文对外不可见"));
                }
            }
        }

        // ---- 标题重复 ----
        //
        // ⚠ 一组重复只报一条，不是每个商品报一条：报 N 条的话，一个「所有商品标题
        //   都叫 Product」的站会瞬间刷出几百行，把别的问题全挤出视野。
        for (Map.Entry<String, Map<String, List<String>>> byLang : titleIndex.entrySet())
        {
            for (Map.Entry<String, List<String>> group : byLang.getValue().entrySet())
            {
                List<String> codes = group.getValue();
                if (codes.size() < 2) continue;
                issues.add(SeoIssue.goods(SeoIssue.LEVEL_MEDIUM, "goods.title.duplicate",
                        codes.get(0), goodsNames.get(codes.get(0)), byLang.getKey(),
                        codes.size() + " 个商品的 " + byLang.getKey() + " 标题完全相同（"
                        + preview(group.getKey()) + "）：" + String.join("、", limit(codes, 8))
                        + "。它们会互相抢排名，Google 也可能判成内容重复"));
            }
        }

        // ==================== 首页装修文案 ====================

        Map<String, Object> content = parseContent(site);
        Object i18nRaw = content.get("i18n");
        Map<?, ?> i18n = i18nRaw instanceof Map ? (Map<?, ?>) i18nRaw : null;

        for (String lang : langs)
        {
            /*
             * ⚠ 这里**故意不走** SiteOpenService 那套「语言层盖共享层、缺了退回默认语言」
             *   的合并。合并之后每种语言都能取到一个值，于是「日语没配 seoTitle」
             *   这个问题会被回退机制掩盖掉——而那正是要查的东西。
             *   体检必须看原始的、分语言的那一份。
             */
            Object node = i18n == null ? null : i18n.get(lang);
            Map<?, ?> langNode = node instanceof Map ? (Map<?, ?>) node : null;

            String seoTitle = langNode == null ? null : trim(str(langNode.get("seoTitle")));
            String seoDesc = langNode == null ? null : trim(str(langNode.get("seoDescription")));

            if (langNode == null)
            {
                issues.add(SeoIssue.home(SeoIssue.LEVEL_HIGH, "home.lang.missing", lang,
                        "装修内容里没有 " + lang + " 这一层。首页整个退回默认语言的文案，"
                        + "而首页是权重最高的页面"));
                continue;
            }
            if (seoTitle == null)
            {
                issues.add(SeoIssue.home(SeoIssue.LEVEL_HIGH, "home.seotitle.missing", lang,
                        "没填 " + lang + " 的 SEO 标题，首页 <title> 会退成公司名"));
            }
            else if (seoTitle.length() > TITLE_MAX)
            {
                issues.add(SeoIssue.home(SeoIssue.LEVEL_MEDIUM, "home.seotitle.long", lang,
                        "SEO 标题 " + seoTitle.length() + " 字符，超过 " + TITLE_MAX + " 会被截断"));
            }
            if (seoDesc == null)
            {
                issues.add(SeoIssue.home(SeoIssue.LEVEL_HIGH, "home.seodesc.missing", lang,
                        "没填 " + lang + " 的 SEO 描述，首页的 meta description 会退成一句标语或空"));
            }
            else if (seoDesc.length() > DESC_MAX)
            {
                issues.add(SeoIssue.home(SeoIssue.LEVEL_MEDIUM, "home.seodesc.long", lang,
                        "SEO 描述 " + seoDesc.length() + " 字符，超过 " + DESC_MAX + " 会被截断"));
            }
        }

        // ==================== 站点级 ====================

        // 语言开了，但一个商品译文都没有 —— 整个语种是空壳，比不开更糟：
        // hreflang 向 Google 承诺了一个语言版本，点进去全是另一种语言。
        for (String lang : langs)
        {
            boolean any = byGoods.values().stream().anyMatch(m -> {
                SeoAuditRow r = m.get(lang);
                return r != null && trim(r.getTitle()) != null;
            });
            if (!any && !byGoods.isEmpty())
            {
                issues.add(SeoIssue.site(SeoIssue.LEVEL_HIGH, "site.lang.empty", lang,
                        "站点开了 " + lang + "，但没有任何一个上架商品填了 " + lang
                        + " 标题。hreflang 会向 Google 承诺一个 " + lang
                        + " 版本，点进去全是默认语言——这种情况下不如先把这个语言关掉"));
            }
        }

        if (byGoods.isEmpty())
        {
            issues.add(SeoIssue.site(SeoIssue.LEVEL_HIGH, "site.goods.empty", null,
                    "一个上架商品都没有。sitemap 里只有首页和列表页，"
                    + "而这个站的 SEO 价值几乎全在商品详情页上"));
        }

        // 站点档案里的 domain。⚠ 它**不是** SEO 字段（canonical 走前台的
        // NUXT_PUBLIC_SITE_URL），但买家验证邮件和找回密码的链接靠它，空着那两个
        // 接口直接抛 500 而前台看不出异常。既然已经在扫，就顺手报一句。
        if (trim(site.getDomain()) == null)
        {
            issues.add(SeoIssue.site(SeoIssue.LEVEL_MEDIUM, "site.domain.missing", null,
                    "站点设置里没填域名。它不影响 canonical（那个走前台的 NUXT_PUBLIC_SITE_URL），"
                    + "但买家验证邮件和找回密码的链接靠它，空着那两个功能会直接报错"));
        }

        // ==================== 汇总 ====================

        issues.sort(Comparator.comparingInt(SeoIssue::levelWeight)
                .thenComparing(i -> i.getRule() == null ? "" : i.getRule())
                .thenComparing(i -> i.getTargetCode() == null ? "" : i.getTargetCode()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodsCount", byGoods.size());
        result.put("langs", langs);
        result.put("issues", issues);
        result.put("high", issues.stream().filter(i -> SeoIssue.LEVEL_HIGH.equals(i.getLevel())).count());
        result.put("medium", issues.stream().filter(i -> SeoIssue.LEVEL_MEDIUM.equals(i.getLevel())).count());
        result.put("low", issues.stream().filter(i -> SeoIssue.LEVEL_LOW.equals(i.getLevel())).count());
        result.put("skipped", skipped);

        /*
         * ⚠ 这一条必须跟着结果一起发出去，界面上要显示。
         *
         * 一份体检报告最危险的地方不是漏报，是让人以为「没报就是没问题」。
         * canonical 的域名这个后端确实查不到（那是 Nuxt 进程的环境变量），
         * 所以要明说，并给出自己验的办法。
         */
        result.put("notChecked", List.of(Map.of(
                "item", "canonical / hreflang 指向的域名对不对",
                "why", "那个值来自 station-site 的 NUXT_PUBLIC_SITE_URL 环境变量，"
                        + "是前台那个 Node 进程的配置，这个后端读不到。"
                        + "设错时只有 canonical 看得出来——埋点会静默发到错的域上，页面完全正常",
                "how", "curl -s https://<主域>/ | grep canonical")));

        return result;
    }

    // ==================== 小工具 ====================

    private Map<String, Object> parseContent(SiteInfo site)
    {
        String text = trim(site.getContentJson());
        if (text == null) return new LinkedHashMap<>();
        try
        {
            Map<String, Object> map = JSON.readValue(text,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            return map == null ? new LinkedHashMap<>() : map;
        }
        catch (Exception e)
        {
            // 装修内容解析不了是另一个问题（装修页会先炸），这里按空处理并报一句就够
            log.warn("体检时装修内容解析失败，按空处理：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static String trim(String s)
    {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String str(Object o)
    {
        return o == null ? null : String.valueOf(o);
    }

    private static String blankToCode(String name, String code)
    {
        String t = trim(name);
        return t == null ? code : t;
    }

    /** 重复标题在提示里只露一小截，长标题会把表格撑坏 */
    private static String preview(String s)
    {
        if (s == null) return "";
        return s.length() <= 24 ? s : s.substring(0, 24) + "…";
    }

    private static List<String> limit(List<String> list, int max)
    {
        if (list.size() <= max) return list;
        List<String> out = new ArrayList<>(list.subList(0, max));
        out.add("等 " + list.size() + " 个");
        return out;
    }
}

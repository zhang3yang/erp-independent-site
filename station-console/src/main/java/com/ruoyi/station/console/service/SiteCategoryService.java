package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.station.console.domain.SiteCategoryLang;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteCategoryLangMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;

/**
 * 站点分类的多语言显示名。
 *
 * <h3>这个站没有分类表，本类也不是分类表</h3>
 * <p>分类的身份仍然是 {@code u_s_sitegoods.sitecategory} 那一列自由文本，它同时兼着
 * 筛选键（URL 的 {@code ?cat=}）、两级层次的载体（靠分隔符）、以及没有译名时的兜底文字。
 * 本类只接管「显示文字」，所以 <b>URL 永远是原串，切语言不断链</b>。</p>
 *
 * <h3>⚠ catkey 是「路径的一级」，而且按 / 归一</h3>
 * <p>{@code 加压疗法/腿部系统} 拆成 {@code 加压疗法} 和 {@code 加压疗法/腿部系统} 两个 key。
 * 逐级拆是为了让大组只翻一次（整串译的话，大组下挂 5 个子分类就要翻 5 遍，
 * 很可能翻出 5 种写法，侧栏的组标题就飘了）。</p>
 *
 * <p>拼 key 时<b>一律用半角 {@code /} 连接修剪过的段</b>，不保留原串里的分隔符和空格。
 * 于是商家写的 {@code 加压疗法 > 腿部系统} 和 {@code 加压疗法/腿部系统} 落到同一组 key 上，
 * 译名自动共用。前台 {@code app/lib/categories.ts} 用同一条规则拼 key，两边必须一致。</p>
 */
@Service
public class SiteCategoryService
{
    private static final Logger log = LoggerFactory.getLogger(SiteCategoryService.class);

    /**
     * 认得的分隔符，和前台 {@code categories.ts} 的 {@code SEP}、
     * 以及 {@code SiteGoodsMapper.xml} 里前缀子树匹配的那个 {@code in (...)} 必须是同一组。
     * 三处改一处就会出现「侧栏分两级、筛选却按整串匹配」这种对不上的情况。
     */
    private static final String SEPARATORS = "/／>＞";

    /** 拼 key 时用的规范分隔符。见类注释 */
    private static final String CANONICAL = "/";

    @Autowired
    private SiteCategoryLangMapper categoryLangMapper;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private SiteTranslateService siteTranslateService;

    // ==================== 路径拆分 ====================

    /**
     * 把一个分类串拆成它路径上的各级 key。
     *
     * <p>{@code 加压疗法 > 腿部系统} → {@code [加压疗法, 加压疗法/腿部系统]}</p>
     *
     * <p>空串、只有分隔符、拆完一段都不剩的，返回空列表——调用方据此跳过，
     * 不要往库里塞空 key。</p>
     */
    public List<String> catKeysOf(String siteCategory)
    {
        List<String> keys = new ArrayList<>();
        if (siteCategory == null)
        {
            return keys;
        }
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < siteCategory.length(); i++)
        {
            char c = siteCategory.charAt(i);
            if (SEPARATORS.indexOf(c) >= 0)
            {
                addSegment(segments, current);
            }
            else
            {
                current.append(c);
            }
        }
        addSegment(segments, current);

        StringBuilder path = new StringBuilder();
        for (String segment : segments)
        {
            if (path.length() > 0)
            {
                path.append(CANONICAL);
            }
            path.append(segment);
            keys.add(path.toString());
        }
        return keys;
    }

    private void addSegment(List<String> segments, StringBuilder current)
    {
        String segment = current.toString().trim();
        current.setLength(0);
        if (!segment.isEmpty())
        {
            segments.add(segment);
        }
    }

    /** key 的最后一段，也就是没有译名时前台显示的那个文字 */
    public String leafOf(String catKey)
    {
        if (catKey == null)
        {
            return "";
        }
        int at = catKey.lastIndexOf(CANONICAL);
        return at < 0 ? catKey : catKey.substring(at + 1);
    }

    /**
     * 全站已上架商品用到的全部 key，按层级顺序去重。
     *
     * <p>来源和前台侧栏是同一个集合（{@code select distinct sitecategory}），
     * 所以管理台那张表里出现的行，正好就是前台看得见的那些分类。</p>
     */
    public List<String> allCatKeys()
    {
        Set<String> keys = new LinkedHashSet<>();
        for (String category : goodsMapper.selectPublicCategories())
        {
            keys.addAll(catKeysOf(category));
        }
        List<String> sorted = new ArrayList<>(keys);
        // 按路径排序，父级自然排在子级前面（前缀短的在前），管理台可以直接按缩进渲染
        sorted.sort(String::compareTo);
        return sorted;
    }

    // ==================== 读 ====================

    /**
     * 某种语言的译名字典，给前台 {@code /openapi/site/info} 的 {@code categoryNames}。
     *
     * <p>查不到的 key 不出现在字典里，前台据此回退显示原文——<b>绝不会出现空标签</b>。
     * 传进来的是站点默认语言时直接返回空字典：默认语言本来就用原文。</p>
     */
    public Map<String, String> namesFor(String lang)
    {
        Map<String, String> names = new LinkedHashMap<>();
        SiteInfo site = siteInfoService.require();
        if (lang == null || lang.equals(site.defaultLang()))
        {
            return names;
        }
        for (SiteCategoryLang row : categoryLangMapper.selectByLang(lang))
        {
            names.put(row.getCatKey(), row.getName());
        }
        return names;
    }

    /**
     * 管理台「分类译名」那张表的数据：行是全站分类，列是各语言。
     *
     * <p>{@code keys} 按层级排好序，{@code langs} 是站点开启的语言（默认语言在第一个），
     * {@code names} 是 {@code catkey → lang → 译名} 的两层字典。空格子不出现在字典里。</p>
     */
    public Map<String, Object> adminTable()
    {
        SiteInfo site = siteInfoService.require();
        Map<String, Map<String, String>> names = new LinkedHashMap<>();
        for (SiteCategoryLang row : categoryLangMapper.selectAll())
        {
            if (row.getName() == null || row.getName().isEmpty())
            {
                continue;
            }
            names.computeIfAbsent(row.getCatKey(), k -> new LinkedHashMap<>())
                    .put(row.getLang(), row.getName());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keys", allCatKeys());
        result.put("langs", site.langList());
        result.put("defaultLang", site.defaultLang());
        result.put("names", names);
        return result;
    }

    // ==================== 写 ====================

    /**
     * 手工保存管理台改过的格子。
     *
     * <p>⚠ <b>只 upsert 提交上来的那几格</b>，没提交的一个字不动。译名是站点级共享的，
     * 按「提交的就是全部」去删会把别人正在用的译名删掉。</p>
     */
    public int saveManual(List<Map<String, Object>> rows, String operator)
    {
        SiteInfo site = siteInfoService.require();
        Set<String> allowed = new LinkedHashSet<>(site.langList());
        Set<String> knownKeys = new LinkedHashSet<>(allCatKeys());
        int saved = 0;
        for (Map<String, Object> row : rows == null ? new ArrayList<Map<String, Object>>() : rows)
        {
            String catKey = trimToNull(str(row.get("catKey")));
            String lang = trimToNull(str(row.get("lang")));
            if (catKey == null || lang == null)
            {
                continue;
            }
            requireKnownKey(knownKeys, catKey);
            // 站点没开的语言不写库；默认语言不写库（它本来就用原文，存一份只会和原文不同步）
            if (!allowed.contains(lang) || lang.equals(site.defaultLang()))
            {
                continue;
            }
            String name = trimToNull(str(row.get("name")));
            if (name != null && name.length() > 100)
            {
                throw new ServiceException("分类译名不能超过 100 个字：" + catKey + " / " + lang);
            }
            upsert(site, catKey, lang, name);
            saved++;
        }
        log.info("分类译名手工保存 {} 条，操作人 {}", saved, operator);
        return saved;
    }

    /**
     * 保存商品时顺手补齐这个商品分类的译名。
     *
     * <p><b>⚠ 绝不抛异常。</b>保存商品和翻分类名是两件事，不能让腾讯云抖一下
     * 就让商家存不了商品。翻译失败只记日志：前台先显示原文，下次保存、
     * 或者在管理台点一次「一键补齐」再补上。</p>
     */
    public void autoFillQuietly(String siteCategory)
    {
        try
        {
            fill(catKeysOf(siteCategory), new ArrayList<>());
        }
        catch (Exception e)
        {
            log.warn("分类译名自动补齐失败（不影响商品保存）：category={}, err={}",
                    siteCategory, e.getMessage());
        }
    }

    /**
     * 一键补齐：把全站分类缺的译名翻出来。管理台那个按钮调它。
     *
     * <p>和上面那个不同，<b>这个会把失败抛出去</b>——用户是主动点的按钮，
     * 失败了必须看得见，否则他会以为补齐了而实际没有。</p>
     */
    public Map<String, Object> fillAll()
    {
        List<String> failed = new ArrayList<>();
        int filled = fill(allCatKeys(), failed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filled", filled);
        result.put("failed", failed);
        return result;
    }

    /**
     * 把这批 key 缺的语言翻出来存上。返回新补了几格。
     *
     * <h3>已有的永远不覆盖</h3>
     * <p>人工改过的、之前翻过的都保住。所以重复点「一键补齐」是安全的，
     * 也不会白白消耗翻译额度——全都有译名时一次接口都不调。</p>
     *
     * <h3>⚠ 源语言按「有没有 CJK 字符」本地判定</h3>
     * <p>这个站的分类是中英混着的（{@code Silicone Parts} 是英文、{@code 加压疗法} 是中文）。
     * 一律按站点默认语言当源语言的话，{@code Silicone Parts} 会被当中文翻，结果不可控。</p>
     *
     * <p>没有用腾讯云的 {@code Source: "auto"}：{@link SiteTranslateService} 的语种白名单
     * 里没有它，而<b>批量</b>翻译接口收不收 {@code auto} 没有验证过——单条接口收不代表
     * 批量也收。不拿没验证过的 API 行为当前提。</p>
     *
     * <p>已知局限：分类名要是用第三种语言写的（商家直接写了日文分类名），会被判成默认语言
     * 而翻错。这种靠管理台那张表手工改——那也正是那个界面存在的理由。</p>
     */
    private int fill(List<String> catKeys, List<String> failedLangs)
    {
        if (catKeys == null || catKeys.isEmpty())
        {
            return 0;
        }
        SiteInfo site = siteInfoService.require();
        String defaultLang = site.defaultLang();

        // 已有什么：catkey → 已经有译名的语言
        Map<String, Set<String>> have = new LinkedHashMap<>();
        for (SiteCategoryLang row : categoryLangMapper.selectAll())
        {
            if (row.getName() != null && !row.getName().isEmpty())
            {
                have.computeIfAbsent(row.getCatKey(), k -> new LinkedHashSet<>()).add(row.getLang());
            }
        }

        int filled = 0;
        for (String target : site.langList())
        {
            if (target.equals(defaultLang))
            {
                continue;
            }
            // 这种语言还缺哪些 key。按源语言分组：中文分类和英文分类要分两批发
            Map<String, List<String>> bySource = new LinkedHashMap<>();
            for (String catKey : catKeys)
            {
                Set<String> done = have.get(catKey);
                if (done != null && done.contains(target))
                {
                    continue;
                }
                String leaf = leafOf(catKey);
                if (leaf.isEmpty())
                {
                    continue;
                }
                String source = sourceLangOf(leaf, defaultLang);
                // 源语言和目标语言相同 = 这一格本来就该显示原文，不用翻也不用存
                if (source.equals(target))
                {
                    continue;
                }
                bySource.computeIfAbsent(source, k -> new ArrayList<>()).add(catKey);
            }
            // 一种语言失败不拖垮其余语言：腾讯云不支持某个语种、或者临时抖一下，
            // 其他语言该补的照补，最后把失败的语种一起报给用户
            try
            {
                for (Map.Entry<String, List<String>> batch : bySource.entrySet())
                {
                    List<String> keys = batch.getValue();
                    List<String> texts = new ArrayList<>();
                    for (String catKey : keys)
                    {
                        texts.add(leafOf(catKey));
                    }
                    List<String> translated = siteTranslateService.translate(batch.getKey(), target, texts);
                    for (int i = 0; i < keys.size() && i < translated.size(); i++)
                    {
                        String name = trimToNull(translated.get(i));
                        if (name == null)
                        {
                            continue;
                        }
                        upsert(site, keys.get(i), target, name.length() > 100 ? name.substring(0, 100) : name);
                        filled++;
                    }
                }
            }
            catch (Exception e)
            {
                failedLangs.add(target);
                log.warn("分类译名补齐失败：lang={}, err={}", target, e.getMessage());
            }
        }
        log.info("分类译名补齐 {} 格，失败语种 {}", filled, failedLangs);
        return filled;
    }

    /**
     * 这段文字该按哪种语言去翻。见 {@link #fill} 的注释。
     *
     * <p>有 CJK 字符就用站点默认语言；默认语言本身不是 CJK 语种时退回 {@code zh}，
     * 否则会出现「拿 en 当源语言去翻中文」这种必然翻歪的组合。</p>
     */
    private String sourceLangOf(String text, String defaultLang)
    {
        if (!hasCjk(text))
        {
            return "en";
        }
        if ("zh".equals(defaultLang) || "zh-TW".equals(defaultLang)
                || "ja".equals(defaultLang) || "ko".equals(defaultLang))
        {
            return defaultLang;
        }
        return "zh";
    }

    private boolean hasCjk(String text)
    {
        for (int i = 0; i < text.length(); )
        {
            int cp = text.codePointAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL)
            {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * ⚠ 只认「某个在库分类按分隔符切出来的某一级前缀」。
     *
     * <p>不能写成朴素的字符串前缀判断：那样 {@code 加压疗} 这种切在字中间的值也会通过，
     * 往库里塞出一个永远不会被任何分类命中的孤儿行。</p>
     */
    private void requireKnownKey(Set<String> knownKeys, String catKey)
    {
        if (!knownKeys.contains(catKey))
        {
            throw new ServiceException("这个分类不存在：" + catKey);
        }
    }

    private void upsert(SiteInfo site, String catKey, String lang, String name)
    {
        SiteCategoryLang row = new SiteCategoryLang();
        row.setShopCode(site.getShopCode());
        row.setSiteCode(site.getSiteCode());
        row.setCatKey(catKey);
        row.setLang(lang);
        row.setName(name);
        categoryLangMapper.upsert(row);
    }

    private static String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

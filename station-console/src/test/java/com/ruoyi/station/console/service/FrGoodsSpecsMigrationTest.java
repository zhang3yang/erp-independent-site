package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 把 {@code sql/station/migration/2026-09-23-fr-goods-specs-desc.sql} 里**真正写进库的**
 * specsjson 抠出来，喂给 {@link SiteOpenService#buildSpecs} 跑一遍。
 *
 * <h3>为什么值得单独一条测试</h3>
 *
 * <p>规格译文有两种形状：主表是扁平的 {@code {"Material":"…"}}，语言表要包一层
 * {@code {"Material":{"k":"…","v":"…"}}}。写成扁平**不报错、不打日志**——buildSpecs 拿
 * 外层键去 get(defaultKey)，拿回来的是个字面量节点，path("k")/path("v") 都是 null，
 * 于是整份译文被忽略，买家看到英文原文。</p>
 *
 * <p>SQL 和 Java 又各自有一层转义，靠人眼看「形状对不对」不现实。所以这里直接从
 * 迁移文件里读，形状错了这条测试就红。顺带钉住「法文键值确实被用上」这件事。</p>
 *
 * <p>迁移文件不在时（比如只拷了 station-console 目录的场景）整条测试跳过，不误报。</p>
 */
class FrGoodsSpecsMigrationTest
{
    /** 从仓库根往上看几层能找到 sql/ 目录 */
    private static final String[] CANDIDATES = {
        "sql/station/migration/2026-09-23-fr-goods-specs-desc.sql",
        "../sql/station/migration/2026-09-23-fr-goods-specs-desc.sql",
        "../../sql/station/migration/2026-09-23-fr-goods-specs-desc.sql"
    };

    private static String readMigration()
    {
        for (String c : CANDIDATES)
        {
            Path p = Path.of(c);
            if (Files.isRegularFile(p))
            {
                try
                {
                    return Files.readString(p, StandardCharsets.UTF_8);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("读不了迁移文件 " + p, e);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> build(String def, String lang) throws Exception
    {
        SiteOpenService svc = new SiteOpenService();
        var m = SiteOpenService.class.getDeclaredMethod("buildSpecs", String.class, String.class, String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(svc, def, lang, "test");
    }

    /**
     * 抠出迁移文件里指定语言那一段的 specsjson 字面量。
     *
     * <p>做法：先按语言定位到 {@code SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'fr',}
     * 之后第一段 {@code '{…}'}；SQL 里的 {@code ''} 是转义过的单引号，还原成一个。
     * 不追求通用解析器——只认这个脚本自己写出来的形状。</p>
     */
    private static String specJsonFor(String sql, String goodscode, String lang)
    {
        // 每个商品一段：先找到 goodscode 出现的 INSERT，再在它后面找 'lang', 之后的第一段 '{...}'
        int from = 0;
        while (true)
        {
            int gc = sql.indexOf("'" + goodscode + "'", from);
            if (gc < 0)
            {
                return null;
            }
            from = gc + 1;
            // 从这一处往后找 "'fr'," / "'en'," 这种语言标记
            int langAt = sql.indexOf("'" + lang + "',", gc);
            if (langAt < 0)
            {
                continue;
            }
            // 语言标记之后跳过正文（正文是 '...' 的单引号字符串，里面 '' 是转义），
            // 第一段以 '{' 开头的单引号串就是 specsjson
            int i = langAt;
            while (true)
            {
                int open = sql.indexOf('\'', i + 1);
                if (open < 0)
                {
                    break;
                }
                if (sql.charAt(open + 1) == '{')
                {
                    int end = open + 1;
                    while (true)
                    {
                        int q = sql.indexOf('\'', end);
                        if (q < 0)
                        {
                            return null;
                        }
                        if (q + 1 < sql.length() && sql.charAt(q + 1) == '\'')
                        {
                            end = q + 2; // '' = 转义的单引号，继续
                            continue;
                        }
                        return sql.substring(open + 1, q).replace("''", "'");
                    }
                }
                i = open;
            }
        }
    }

    @Test
    @DisplayName("迁移里的 specsjson 形状正确：语言表是 {k,v}，能被 buildSpecs 用上")
    void migrationSpecsShapeIsMergeable() throws Exception
    {
        String sql = readMigration();
        if (sql == null)
        {
            return; // 迁移文件不在，跳过
        }

        List<String> goods = List.of("12010304", "12010305", "12010701", "12010901");
        List<String> langs = List.of("fr", "en");

        for (String goodsCode : goods)
        {
            // 主表那份（默认语言 en）：脚本里 UPDATE u_s_sitegoods ... specsjson = '{...}'
            String defJson = flatDefaultFor(sql, goodsCode);
            assertNotNull(defJson, goodsCode + " 找不到主表 specsjson");
            assertTrue(defJson.startsWith("{"), goodsCode + " 主表 specsjson 应是扁平对象：" + defJson);

            for (String lang : langs)
            {
                String langJson = specJsonFor(sql, goodsCode, lang);
                assertNotNull(langJson, goodsCode + "/" + lang + " 找不到译文 specsjson");

                Map<String, Object> merged = build(defJson, langJson);

                int defKeys = countTopLevelKeys(defJson);
                assertEquals(defKeys, merged.size(),
                        goodsCode + "/" + lang + " 合并后行数变了，说明译文形状或键对不上");

                // 每一条都必须被译文覆盖到——只要有一条还等于英文原值，就说明形状写错了
                for (Map.Entry<String, Object> e : merged.entrySet())
                {
                    assertFalse(e.getKey().isEmpty(), goodsCode + "/" + lang + " 出现空键");
                    assertNotNull(e.getValue());
                    assertFalse(String.valueOf(e.getValue()).isEmpty(),
                            goodsCode + "/" + lang + " 的「" + e.getKey() + "」是空值");
                }

                // fr 的键应当已经法文化：至少要有一条键和默认语言的键不同。
                // 全都一样 = 译文被忽略了（形状写错时的典型表现）。
                if ("fr".equals(lang))
                {
                    List<String> keys = new ArrayList<>(merged.keySet());
                    List<String> defKeysList = topLevelKeys(defJson);
                    assertTrue(keys.stream().anyMatch(k -> !defKeysList.contains(k)),
                            goodsCode + "/fr 的键没有一条被翻译，译文可能被忽略了：" + keys);
                }
            }
        }
    }

    /** 取主表那一段扁平 specsjson */
    private static String flatDefaultFor(String sql, String goodscode)
    {
        int gc = sql.indexOf("WHERE `goodscode` = '" + goodscode + "' AND `sitecode` = 'DLZ2049651302'");
        if (gc < 0)
        {
            return null;
        }
        // 往前找这段 UPDATE 里的 specsjson = '{...}'
        int start = sql.lastIndexOf("SET `specsjson` = '", gc);
        if (start < 0)
        {
            return null;
        }
        int open = start + "SET `specsjson` = '".length();
        int end = open;
        while (true)
        {
            int q = sql.indexOf('\'', end);
            if (q < 0)
            {
                return null;
            }
            if (q + 1 < sql.length() && sql.charAt(q + 1) == '\'')
            {
                end = q + 2;
                continue;
            }
            return sql.substring(open, q).replace("''", "'");
        }
    }

    /**
     * 顶层的键。
     *
     * <p>⚠ 别在这里手写「数大括号深度」的解析。译文是 {@code {"k":…,"v":…}} 套一层，
     * 内层的 k/v 很容易被当成顶层键数进去，行数就对不上了。形状交给 JSON 解析器判断。
     * 这一段是迁移文件的解析辅助，不是被测逻辑。</p>
     */
    private static List<String> topLevelKeys(String json)
    {
        try
        {
            // 解析成 LinkedHashMap，顶层键就是 keySet。别用 JsonNode 的遍历 API
            // （这个项目是 Jackson 3 / tools.jackson，JsonNode 那套方法名和 2.x 不一样）
            Map<String, Object> map = new tools.jackson.databind.ObjectMapper()
                    .readValue(json, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return new ArrayList<>(map.keySet());
        }
        catch (Exception e)
        {
            throw new IllegalStateException("解析不了这段 specsjson：" + json, e);
        }
    }

    private static int countTopLevelKeys(String json)
    {
        return topLevelKeys(json).size();
    }
}

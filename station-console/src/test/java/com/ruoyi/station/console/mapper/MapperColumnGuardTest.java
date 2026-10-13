package com.ruoyi.station.console.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 被合并冲掉过的映射列，守住它们还在。
 *
 * <p>这两处都是「编译通过、测试全绿、上线才发现」的丢法：MyBatis 映射文件少一列不会报错，
 * 只是那个字段永远是 null，保存时再把库里的值清成 null。</p>
 * <ul>
 *   <li>{@code u_s_siteinfo.theme}：3285ea01 用旧版文件覆盖掉，前台主题全退成黑蓝（2026-09-27 补回）</li>
 *   <li>{@code u_s_sitegoods.optionsjson}：合并 56d20cbb 冲掉，管理台读不回规格维度、前台两排选择退成平铺（2026-09-29 补回）</li>
 * </ul>
 */
class MapperColumnGuardTest
{
    private static String read(String file) throws Exception
    {
        try (InputStream in = MapperColumnGuardTest.class.getClassLoader().getResourceAsStream(file))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String text, String word)
    {
        return text.split(java.util.regex.Pattern.quote(word), -1).length - 1;
    }

    @Test
    @DisplayName("商品映射：optionsjson 在 resultMap / 查询列 / insert / update 四处都在")
    void goodsOptionsJson() throws Exception
    {
        String xml = read("mapper/SiteGoodsMapper.xml");
        assertTrue(xml.contains("column=\"optionsjson\""), "resultMap 少了 optionsjson");
        assertTrue(xml.contains("sg.optionsjson"), "ownColumns 查询列少了 sg.optionsjson");
        assertTrue(xml.contains("#{optionsJson}"), "insert 少了 #{optionsJson}");
        assertTrue(xml.contains("optionsjson = #{optionsJson}"), "update 少了 optionsjson = #{optionsJson}");
    }

    @Test
    @DisplayName("站点映射：theme 在 resultMap 和查询列里")
    void siteTheme() throws Exception
    {
        String xml = read("mapper/SiteInfoMapper.xml");
        assertTrue(xml.contains("column=\"theme\""), "resultMap 少了 theme");
        assertTrue(count(xml, "theme") >= 2, "selectSite 的查询列少了 theme");
    }
}

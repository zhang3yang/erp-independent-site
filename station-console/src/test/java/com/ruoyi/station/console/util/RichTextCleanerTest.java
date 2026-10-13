package com.ruoyi.station.console.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RichTextCleaner} 的行为契约。
 *
 * <h3>为什么专门盯属性引号</h3>
 *
 * <p>2026-09-23：商家在商品详情里插图，管理台里能插、能预览、保存不报错，前台
 * <b>永远不显示图</b>。根因是 {@link HTMLFilter#processTag} 把属性写成
 * {@code src=\"https://…\"}——反斜杠真的进了输出的 HTML，浏览器把 {@code src}
 * 解析成裸的 {@code \}（相对路径），于是每张图都 404。</p>
 *
 * <p>那行是从 ruoyi-common 抄来的，抄错了没人发现：{@code \"} 在 Java 字符串里是
 * 「反斜杠 + 引号」，而作者想写的是「一个引号」。<b>这个类是在前台 v-html 渲染之前
 * 唯一的一道处理</b>，所以它的输出必须是浏览器能直接吃的 HTML——这条测试就是钉住
 * 这一点，别再被抄回去。</p>
 */
public class RichTextCleanerTest
{
    private static final String IMG_URL = "https://file.example.com/image/2026/09/23/SIMG1.jpg";

    @Test
    @DisplayName("img 的 src 原样保留，不能被转义成 \\\" 或 &#92;")
    public void keepsImageSrcVerbatim()
    {
        String out = RichTextCleaner.clean("<img src=\"" + IMG_URL + "\" alt=\"\">");

        assertTrue(out.contains("src=\"" + IMG_URL + "\""), "src 必须是干净的属性值，实际：" + out);
        assertFalse(out.contains("\\\""), "属性引号前不能出现反斜杠，实际：" + out);
        assertFalse(out.contains("&#92;"), "反斜杠也不能被实体化成 &#92;，实际：" + out);
    }

    @Test
    @DisplayName("单引号 / 不带引号的属性统一规范化成双引号")
    public void normalizesAttributeQuoting()
    {
        assertTrue(RichTextCleaner.clean("<img src='" + IMG_URL + "' alt=''>")
                .contains("src=\"" + IMG_URL + "\""));
        assertTrue(RichTextCleaner.clean("<img src=" + IMG_URL + " alt=''>")
                .contains("src=\"" + IMG_URL + "\""));
    }

    @Test
    @DisplayName("a 的 href 同样不能被转义坏掉")
    public void keepsAnchorHrefVerbatim()
    {
        String out = RichTextCleaner.clean("<a href=\"https://example.com/p/1\" target=\"_blank\">l</a>");

        assertTrue(out.contains("href=\"https://example.com/p/1\""), "实际：" + out);
        assertFalse(out.contains("\\\""), "实际：" + out);
    }

    @Test
    @DisplayName("详情正文里的排版标签原样留着——商家保存完回来不能「全没了」")
    public void keepsLayoutTags()
    {
        String out = RichTextCleaner.clean(
                "<h3>Points forts</h3><p>texte</p><ul><li>un</li><li>deux</li></ul>"
                        + "<table><tr><td colspan=\"2\">x</td></tr></table>");

        assertTrue(out.contains("<h3>Points forts</h3>"));
        assertTrue(out.contains("<li>un</li>"));
        assertTrue(out.contains("colspan=\"2\""), "跨列要留，否则商家的规格表会散架：" + out);
    }

    @Test
    @DisplayName("script / iframe / 事件属性 / javascript: 一个都不能漏进前台")
    public void stripsScriptableContent()
    {
        String out = RichTextCleaner.clean(
                "<p onclick=\"steal()\">a</p>"
                        + "<script>alert(1)</script>"
                        + "<iframe src=\"https://evil.example\"></iframe>"
                        + "<a href=\"javascript:alert(1)\">x</a>"
                        + "<img src=\"https://ok.example/i.jpg\" onerror=\"alert(1)\">");

        assertFalse(out.toLowerCase().contains("<script"), "实际：" + out);
        assertFalse(out.toLowerCase().contains("<iframe"), "实际：" + out);
        assertFalse(out.toLowerCase().contains("onclick"), "实际：" + out);
        assertFalse(out.toLowerCase().contains("onerror"), "实际：" + out);
        assertFalse(out.toLowerCase().contains("javascript:"), "实际：" + out);
        // 顺带确认「挡脚本」没有顺手把正常的图也挡掉
        assertTrue(out.contains("https://ok.example/i.jpg"), "实际：" + out);
    }

    @Test
    @DisplayName("null / 空白原样返回，不把 null 变成空串")
    public void passesBlankThrough()
    {
        assertEquals(null, RichTextCleaner.clean(null));
        assertEquals("  ", RichTextCleaner.clean("  "));
    }
}

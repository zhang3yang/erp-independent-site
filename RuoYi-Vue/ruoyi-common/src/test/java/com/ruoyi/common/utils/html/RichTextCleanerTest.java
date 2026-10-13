package com.ruoyi.common.utils.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 商品详情富文本的白名单过滤。
 *
 * <p>这段内容会被站点前台<b>当 HTML 渲染</b>，而录入者是商家（不是内部人员），
 * 写入路径又都不在 XssFilter 的 urlPatterns 里。所以过滤必须在服务层做，
 * 而且必须有测试盯着——白名单这种东西，改漏一个标签不会报错，只会静悄悄地放行。</p>
 */
@DisplayName("富文本白名单过滤")
class RichTextCleanerTest
{
    private static String clean(String html)
    {
        String result = RichTextCleaner.clean(html);
        return result == null ? null : result.toLowerCase(Locale.ROOT);
    }

    @Nested
    @DisplayName("必须挡住的")
    class Blocked
    {
        @Test
        void script标签整个剥掉()
        {
            String out = clean("<p>正文</p><script>alert(1)</script>");
            assertFalse(out.contains("<script"), out);
            assertTrue(out.contains("正文"), "正文不能跟着一起没了：" + out);
        }

        @Test
        void iframe剥掉()
        {
            assertFalse(clean("<iframe src=\"https://evil.example.com\"></iframe>").contains("<iframe"));
        }

        @Test
        void style标签剥掉()
        {
            assertFalse(clean("<style>body{display:none}</style>").contains("<style"));
        }

        @Test
        @DisplayName("事件属性不在白名单里，连标签一起保留但属性必须没了")
        void 事件属性剥掉()
        {
            String out = clean("<img src=\"https://cdn/a.jpg\" onerror=\"alert(1)\">");
            assertFalse(out.contains("onerror"), out);
            // 图片本身是正常内容，不能连它一起删
            assertTrue(out.contains("https://cdn/a.jpg"), out);
        }

        @Test
        void onclick剥掉()
        {
            String out = clean("<p onclick=\"steal()\">点我</p>");
            assertFalse(out.contains("onclick"), out);
            assertTrue(out.contains("点我"), out);
        }

        @Test
        @DisplayName("javascript: 协议过不了协议白名单")
        void javascript协议剥掉()
        {
            String out = clean("<a href=\"javascript:alert(1)\">click</a>");
            assertFalse(out.contains("javascript:"), out);
        }

        @Test
        @DisplayName("style 属性故意不放行：它是常见载体，而详情是纯文本域录入的，用不上行内样式")
        void style属性剥掉()
        {
            String out = clean("<p style=\"background:url(javascript:alert(1))\">x</p>");
            assertFalse(out.contains("style"), out);
        }

        @Test
        void html注释剥掉()
        {
            assertFalse(clean("<p>a</p><!-- 藏点东西 -->").contains("藏点东西"));
        }
    }

    @Nested
    @DisplayName("必须留下的")
    class Kept
    {
        @Test
        @DisplayName("段落、换行、加粗这些排版标签要留——留不住的话商家会以为保存没生效")
        void 基本排版()
        {
            String out = clean("<p>第一段</p><p>第二段<br><strong>重点</strong></p>");
            assertTrue(out.contains("<p>"), out);
            assertTrue(out.contains("<strong>"), out);
            assertTrue(out.contains("第一段"), out);
        }

        @Test
        void 列表()
        {
            String out = clean("<ul><li>一</li><li>二</li></ul>");
            assertTrue(out.contains("<ul>"), out);
            assertTrue(out.contains("<li>"), out);
        }

        @Test
        @DisplayName("规格表是商品详情的主要形态，表格标签必须留全")
        void 表格()
        {
            String out = clean("<table><thead><tr><th>项</th></tr></thead>"
                    + "<tbody><tr><td>值</td></tr></tbody></table>");
            assertTrue(out.contains("<table>"), out);
            assertTrue(out.contains("<thead>"), out);
            assertTrue(out.contains("<th>"), out);
            assertTrue(out.contains("<td>"), out);
        }

        @Test
        @DisplayName("跨行跨列要留，不然商家画的规格表会散架")
        void 表格跨行跨列()
        {
            String out = clean("<table><tr><td colspan=\"2\" rowspan=\"3\">值</td></tr></table>");
            assertTrue(out.contains("colspan"), out);
            assertTrue(out.contains("rowspan"), out);
        }

        @Test
        void 标题()
        {
            assertTrue(clean("<h2>产品优势</h2>").contains("<h2>"));
        }

        @Test
        void https链接和图片()
        {
            String out = clean("<a href=\"https://kangchen.com\">home</a>"
                    + "<img src=\"https://cdn/a.jpg\" alt=\"a\" width=\"100\">");
            assertTrue(out.contains("https://kangchen.com"), out);
            assertTrue(out.contains("https://cdn/a.jpg"), out);
            assertTrue(out.contains("alt="), out);
            assertTrue(out.contains("width="), out);
        }

        @Test
        void mailto链接()
        {
            assertTrue(clean("<a href=\"mailto:sales@kangchen.com\">mail</a>").contains("mailto:"));
        }
    }

    @Nested
    @DisplayName("边界")
    class Edges
    {
        @Test
        @DisplayName("null 原样返回，不要把「没填」变成空串")
        void null原样()
        {
            assertNull(RichTextCleaner.clean(null));
        }

        @Test
        void 空白原样()
        {
            assertEquals("   ", RichTextCleaner.clean("   "));
        }

        @Test
        @DisplayName("纯文本不受影响")
        void 纯文本()
        {
            assertTrue(RichTextCleaner.clean("这是一段普通说明").contains("这是一段普通说明"));
        }

        @Test
        @DisplayName("过滤是幂等的：过一遍和过两遍结果一样，不然反复保存会越洗越少")
        void 幂等()
        {
            String once = RichTextCleaner.clean("<p>正文<script>x</script></p><ul><li>a</li></ul>");
            assertEquals(once, RichTextCleaner.clean(once));
        }

        @Test
        @DisplayName("HTMLFilter 有可变状态，实现里每次新建一个——并发下不能互相踩")
        void 并发下结果稳定() throws Exception
        {
            String html = "<p>正文</p><table><tr><td>值</td></tr></table><script>bad()</script>";
            String expected = RichTextCleaner.clean(html);

            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
            try
            {
                java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < 200; i++)
                {
                    futures.add(pool.submit(() -> RichTextCleaner.clean(html)));
                }
                for (java.util.concurrent.Future<String> future : futures)
                {
                    assertEquals(expected, future.get());
                }
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }
}

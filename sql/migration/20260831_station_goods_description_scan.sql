-- ============================================================================
-- 排查：过滤上线之前存进来的商品详情里有没有危险 HTML
--
-- 背景：RichTextCleaner 是在写入侧（StationSiteGoodsServiceImpl.saveGoods）加的，
--      只管住新写入。这个脚本用来看**存量**数据里有没有已经躺着的脚本。
--
-- 为什么只检测不清洗：SQL 里没法正确地解析和过滤 HTML，用 REPLACE 去删标签会留下半截
--      标签，反而可能拼出新的可执行片段。命中的行走人工：在管理台里把那条商品的详情
--      重新保存一次，就会经过 RichTextCleaner，是最省事也最可靠的洗法。
--
-- 前台还不存在的时候，这些内容没有被渲染过，所以现在跑是「上线前的例行检查」，不是事故响应。
--
-- 只读脚本，不改任何数据，可以随便跑。
--
-- ⚠ 这个文件放在 migration/ 里是个例外：它不是结构变更，是这次发布配套的一次性检查。
--   升级已有环境时跑不跑都行（跑了只会返回一个结果集），发布后跟着本目录一起清掉。
-- ============================================================================

-- ⚠ 表上的列名是 shop_code（带下划线），其余是 sitecode / goodscode（不带）。
--   这张表就是这么建的，别按其中一种去推另一种。
--
-- 检测用 LIKE 而不是 REGEXP：MySQL 8.0 把正则引擎换成了 ICU，5.7 用的是老引擎，
-- 两边对 POSIX 字符类的支持不完全一样。LIKE 在哪个版本上都是一个意思。
SELECT shop_code,
       sitecode,
       goodscode,
       lang,
       CASE
           WHEN LOWER(description) LIKE '%<script%'  THEN 'script 标签'
           WHEN LOWER(description) LIKE '%<iframe%'  THEN 'iframe 标签'
           WHEN LOWER(description) LIKE '%<object%'  THEN 'object 标签'
           WHEN LOWER(description) LIKE '%<embed%'   THEN 'embed 标签'
           WHEN LOWER(description) LIKE '%<style%'   THEN 'style 标签'
           WHEN LOWER(description) LIKE '%javascript:%' THEN 'javascript: 协议'
           WHEN LOWER(description) LIKE '%onerror=%' THEN '事件属性 onerror'
           WHEN LOWER(description) LIKE '%onclick=%' THEN '事件属性 onclick'
           WHEN LOWER(description) LIKE '%onload=%'  THEN '事件属性 onload'
           WHEN LOWER(description) LIKE '%onmouse%'  THEN '事件属性 onmouse*'
           ELSE '其他'
       END                      AS `命中`,
       CHAR_LENGTH(description) AS `长度`,
       LEFT(description, 200)   AS `前200字`
FROM sdsstation.u_s_sitegoods_lang
WHERE description IS NOT NULL
  AND (
        LOWER(description) LIKE '%<script%'
     OR LOWER(description) LIKE '%<iframe%'
     OR LOWER(description) LIKE '%<object%'
     OR LOWER(description) LIKE '%<embed%'
     OR LOWER(description) LIKE '%<style%'
     OR LOWER(description) LIKE '%javascript:%'
     -- 事件属性逐个列，不用正则。常见的就这几个，漏一两个不影响判断——
     -- 真出现可疑内容时是人去看那条记录，不是靠这个 CASE 定性
     OR LOWER(description) LIKE '%onerror=%'
     OR LOWER(description) LIKE '%onclick=%'
     OR LOWER(description) LIKE '%onload=%'
     OR LOWER(description) LIKE '%onmouse%'
     OR LOWER(description) LIKE '%onfocus=%'
     OR LOWER(description) LIKE '%onsubmit=%'
  )
ORDER BY sitecode, goodscode, lang;

-- 一行都没有 = 存量干净，不用做任何事。
--
-- 有命中的话，逐条处理：
--   1. 在管理台打开那个商品，看详情里那段是不是商家自己贴的正常内容（比如从别处复制来的
--      带 style 的表格）——是的话，重新保存一次就会被过滤成安全版本；
--   2. 确认是恶意注入的，直接清空这一条的 description 再让商家重填；
--   3. 处理完重新跑一遍这个脚本，应该返回空。

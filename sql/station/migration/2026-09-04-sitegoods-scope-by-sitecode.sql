-- =====================================================================
-- u_s_sitegoods：索引改成以 sitecode 领头
-- =====================================================================
--
-- 配套 SiteGoodsMapper.xml 的改动（给每条语句加 sitecode 作用域）。两者必须一起上：
-- 只改 mapper 不改索引，列表查询会退化成全表扫；只改索引不改 mapper，泄露还在。
--
-- =====================================================================
-- 为什么要改：一个正在发生的跨站泄露
-- =====================================================================
--
-- 现场实测（ERP 那台机器上还没拆的 sdsstation）：
--
--     sitecode        publishstatus   条数
--     DLZ2049651302   已上架            4     ← 另一个站
--     DLZ2049653802   已上架            1     ← 本站
--     DLZ2049653802   草稿              2     ← 本站
--
-- 而 /openapi/site/products 返回 5 条——其中 4 条是**另一个站的商品**。
-- 商品名、价格、起订量全都放出去了，接口 200、无任何报错。
--
-- 原因：mapper 里每条语句都不带站点作用域。它头部的注释是这么解释的：
--
--     shop_code / sitecode 不在任何 where 里。一个部署一个站，那两列不是筛选维度（§5.3）。
--
-- 这个前提只在**迁移完成之后**成立（boundary §10.4⑦：mysqldump 按站 restore 到各自
-- 服务器）。现在后端连的是还没拆的共享库，前提不成立，于是漏。
--
-- 而且 §5.3 原文只降级了 shop_code：
--     「**shop_code 降级为遗留列**……新代码不得读它、不得按它过滤」
--     「**sitecode 保留**：跳转令牌的 site 声明要和 u_s_siteinfo.sitecode 对齐」
-- 文档明确保留 sitecode 的意义。mapper 把两列并列处理，这一步是它自己加的。
--
-- 更严重的是**写操作**也没作用域：update / updateStatus / deleteByCodes /
-- deleteLangsByGoods 都是 `where goodscode = ?`。两个站共用一个商品编码时
-- （12010304 就是），在管理台编辑或删除本站的商品会**连带改掉/删掉邻站那一行**。
-- 那不是泄露，是跨站数据损坏。
--
-- =====================================================================
-- 为什么不靠「把库拆了就好了」
-- =====================================================================
--
--  · 这个错**静默**：接口 200、商品有名有价，只有人工比对清单才会发现
--  · 拆完库泄露会「消失」，但代码仍然是「只要库里有第二个站就漏」的状态——
--    隐患一直在，只是被环境藏住了
--  · restore 时 --where 写错、或者两个站临时共用一个库做测试，就又漏了
--
-- 作用域写在 SQL 里，环境怎么变都不漏。
--
-- =====================================================================
-- 为什么索引要跟着改
-- =====================================================================
--
-- 原索引 idx_sitegoods_list (shop_code, sitecode, publishstatus, sortnum)
-- 以 shop_code 领头。而 shop_code 是遗留列、值恒定，选择度为 0——
-- 它占着索引第一列，纯属占位。
--
-- 按文档「不得按 shop_code 过滤」，新语句只过滤 sitecode，那就跳过了索引第 1 列，
-- 前缀对不上。实测（EXPLAIN，7 行数据）：
--
--     只过滤 publishstatus（改之前）      type=index  filtered=14%  filesort
--     加 sitecode + 原索引                type=ALL    ← 更差，全表扫
--     加 sitecode + 本迁移的新索引        type=ref    filtered=100%  Using index
--
-- 新的 idx_sitegoods_list 把列表查询的 ORDER BY 整个覆盖进去了，filesort 也没了。
--
-- ⚠ 它和 selectPublicList 的 ORDER BY 是**绑死**的：
--       order by sg.featured desc, sg.sortnum asc, sg.createtime desc, sg.goodscode asc
--   改排序规则就要回来改这个索引，否则 filesort 会悄悄回来（只是变慢，不报错）。
--   少一个 tie-breaker 都不行——实测只到 sortnum 时 filesort 仍然存在。
--
-- idx_sitegoods_goods (shop_code, goodscode) 同理换成 (sitecode, goodscode)：
-- 详情页、编辑、删除都是「按 sitecode + goodscode 定位一行」的形状。
-- 原来那条的用途注释写的是「反查一个商品上了哪些站」——一站一库之后这个反查没有意义了。
--
-- =====================================================================

USE `sdsstation`;

DROP PROCEDURE IF EXISTS `__rescope_sitegoods_indexes`;

DELIMITER $$
CREATE PROCEDURE `__rescope_sitegoods_indexes`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- 旧的 list 索引：以 shop_code 领头，换掉
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=db
             AND table_name='u_s_sitegoods' AND index_name='idx_sitegoods_list'
             AND seq_in_index=1 AND column_name='shop_code') THEN
    ALTER TABLE `u_s_sitegoods` DROP INDEX `idx_sitegoods_list`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=db
                 AND table_name='u_s_sitegoods' AND index_name='idx_sitegoods_list') THEN
    -- 列顺序 = selectPublicList 的 where + order by，逐项对齐。
    -- 降序列需要 MySQL 8+（8 以下会被当成升序忽略，filesort 回来但结果仍然正确）
    ALTER TABLE `u_s_sitegoods`
      ADD INDEX `idx_sitegoods_list`
        (`sitecode`, `publishstatus`, `featured` DESC, `sortnum` ASC,
         `createtime` DESC, `goodscode` ASC);
  END IF;

  -- 旧的 goods 索引：同样以 shop_code 领头
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=db
             AND table_name='u_s_sitegoods' AND index_name='idx_sitegoods_goods') THEN
    ALTER TABLE `u_s_sitegoods` DROP INDEX `idx_sitegoods_goods`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=db
                 AND table_name='u_s_sitegoods' AND index_name='idx_sitegoods_code') THEN
    ALTER TABLE `u_s_sitegoods` ADD INDEX `idx_sitegoods_code` (`sitecode`, `goodscode`);
  END IF;
END$$
DELIMITER ;

CALL `__rescope_sitegoods_indexes`();
DROP PROCEDURE `__rescope_sitegoods_indexes`;


-- 多语言文案表同理：selectLangs / deleteLangsByGoods 也要按 sitecode 收口。
-- 它的主键是 (shop_code, sitecode, goodscode, lang)，同样以遗留列领头。
DROP PROCEDURE IF EXISTS `__rescope_lang_index`;

DELIMITER $$
CREATE PROCEDURE `__rescope_lang_index`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='u_s_sitegoods_lang' AND index_name='idx_goodslang_code') THEN
    ALTER TABLE `u_s_sitegoods_lang` ADD INDEX `idx_goodslang_code` (`sitecode`, `goodscode`);
  END IF;
END$$
DELIMITER ;

CALL `__rescope_lang_index`();
DROP PROCEDURE `__rescope_lang_index`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='u_s_sitegoods' AND index_name='idx_sitegoods_list' AND seq_in_index=1)
                                                          AS `list索引第1列_应为sitecode`,
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='u_s_sitegoods' AND index_name='idx_sitegoods_list')
                                                          AS `list索引列数_应为6`,
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='u_s_sitegoods' AND index_name IN ('idx_sitegoods_code','idx_sitegoods_goods'))
                                                          AS `code索引_应为1_旧的已删`,
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='u_s_sitegoods_lang' AND index_name='idx_goodslang_code')
                                                          AS `lang索引_应为1`;

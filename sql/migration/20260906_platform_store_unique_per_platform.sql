-- 店铺唯一性改为「按平台」（2026-09-06）。可重复执行，跑两遍结果一样。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- sdsorder.u_o_platform_store 的唯一约束
--
--   旧：UNIQUE (shop_code, store_code)                 —— 店铺代码在整个商家内唯一
--   新：UNIQUE (shop_code, platform_code, store_code)  —— 店铺代码只在平台内唯一
--       UNIQUE (shop_code, platform_code, store_name)  —— 店铺名称也只在平台内唯一
--
-- 也就是：不同平台下，店铺代码和店铺名称都可以重复；同一平台下两者都不能重复。
--
-- ============================================================================
-- 为什么
-- ============================================================================
--
-- 同一个商家在各平台开的店，代码和名称撞车是常态：「自营店」几乎每个平台都有一个，
-- 店铺代码又是各平台自己的编码规则，抖音和淘宝各给一串 ZYDTB 完全正常。
-- 旧口径把这类正常情况判成重复，逼着用户给每个平台的同名店手工编一个假代码。
--
-- 名称原先根本没查重（只有代码有唯一键），同一平台下可以建出两个「自营店」，
-- 订单上只存名称快照，事后分不清是哪家。这次一起补上。
--
-- ============================================================================
-- ⚠ 存量数据有冲突会报错退出
-- ============================================================================
--
-- 代码维度只会变松（旧约束是新约束的超集），不可能冲突。
-- 名称维度是新增约束：如果同一平台下已经有重名店铺，第 1 段会把重名的行列出来并报错
-- 退出——这时先在界面上把重名的店铺改名，再重跑本脚本。
--
-- 相关代码：OrderPlatformServiceImpl.requireStoreUnique（应用层同口径校验）、
-- OrderPlatformMapper.selectStoreByCode / countStoreOrders（查询都补上了平台代码）、
-- SalesOrderServiceImpl.applyPlatformStore（按「平台+店铺代码」定位店铺）。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1      -uroot -p --default-character-set=utf8mb4 < 20260906_platform_store_unique_per_platform.sql
--   mysql -h 82.156.158.174 -uroot -p --default-character-set=utf8mb4 < 20260906_platform_store_unique_per_platform.sql

SET NAMES utf8mb4;
USE `sdsorder`;

-- ============================================================
-- 1. 名称重复预检：有冲突就停在这里，不做任何改动
-- ============================================================

SET @dup_name = (
  SELECT COUNT(*) FROM (
    SELECT 1 FROM `u_o_platform_store`
     GROUP BY `shop_code`, `platform_code`, `store_name`
    HAVING COUNT(*) > 1
  ) t
);

-- 有冲突就先把冲突行打出来，方便直接照着改
SELECT `shop_code`, `platform_code`, `store_name`, COUNT(*) `重复条数`
  FROM `u_o_platform_store`
 GROUP BY `shop_code`, `platform_code`, `store_name`
HAVING COUNT(*) > 1;

-- 然后故意查一张不存在的表把脚本顶掉。SIGNAL 不能放进 PREPARE
-- （ERROR 1295: This command is not supported in the prepared statement protocol yet），
-- 而 mysql 客户端遇到错误就停，所以用表名当报错信息。
SET @stmt = IF(@dup_name > 0,
  'SELECT * FROM `上面列出的重名店铺请先改名_再重跑本脚本`',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ============================================================
-- 2. 先建新索引，再删旧索引
-- ============================================================
-- 顺序不能反：旧的 (shop_code, store_code) 是外键 fk_platform_store_platform
-- 之外唯一覆盖 shop_code 前缀的唯一键，先删可能让 InnoDB 挑不到可用索引。

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'sdsorder' AND TABLE_NAME = 'u_o_platform_store'
                  AND INDEX_NAME = 'uk_platform_store_shop_platform_code');
SET @stmt = IF(@exists = 0,
  'ALTER TABLE `u_o_platform_store` ADD UNIQUE KEY `uk_platform_store_shop_platform_code` (`shop_code`,`platform_code`,`store_code`)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'sdsorder' AND TABLE_NAME = 'u_o_platform_store'
                  AND INDEX_NAME = 'uk_platform_store_shop_platform_name');
SET @stmt = IF(@exists = 0,
  'ALTER TABLE `u_o_platform_store` ADD UNIQUE KEY `uk_platform_store_shop_platform_name` (`shop_code`,`platform_code`,`store_name`)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = 'sdsorder' AND TABLE_NAME = 'u_o_platform_store'
                  AND INDEX_NAME = 'uk_platform_store_shop_code');
SET @stmt = IF(@exists > 0,
  'ALTER TABLE `u_o_platform_store` DROP INDEX `uk_platform_store_shop_code`',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ============================================================
-- 3. 结果自查
-- ============================================================
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) cols, NON_UNIQUE
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = 'sdsorder' AND TABLE_NAME = 'u_o_platform_store'
 GROUP BY INDEX_NAME, NON_UNIQUE;

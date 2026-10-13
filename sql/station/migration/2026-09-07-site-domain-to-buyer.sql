-- =====================================================================
-- 把 u_s_siteinfo.domain 从开发期占位改成前台真实地址
-- =====================================================================
--
-- 库：sdsstation。可重复执行。
--
-- 为什么需要这么一个脚本：02_init_siteinfo.sql 用的是 INSERT IGNORE
-- （那是刻意的，见那个文件里的注释——避免重跑把 consolesecret 和装修内容
-- 冲回初始值）。所以改了那份初始脚本，**已经建好的库里那一行不会动**，
-- 得走这一条 UPDATE。
--
-- 症状：不跑这个的话，
--   · 买家注册的验证信、找回密码信里的链接是 https://localhost:5174/verify-email?...
--     ——海外买家点开是自己电脑的 5174 端口，永远打不开（BuyerAuthService#siteBase）
--   · 管理台顶栏那个「访问前台」也指到同一个地方
-- 两处都不会报错，信照发、链接照显示，只是点了没用。
--
-- ⚠ 存**主机名**：不带 https://、不带尾斜杠。
--   （BuyerAuthService#siteBase 和 console-ui 的 utils/siteUrl.js 都能兼容带协议头的
--    历史值，但新写的别再带，否则两套拼接规则里只要有一处没兼容就拼出 https://https//…）
--
-- ⚠ 存的是**前台那个 buyer. 子域**，不是主域。
--   主域 tongling.vclinker.com 没备案，nginx 上没有它的 server 块，
--   443 落到兜底块被 444 掉（deploy/nginx/tongling.conf）。
--
-- ⚠ 这一列和 ERP 那边 sdsshop.u_s_site.domain 是**两个语义**，不要照抄：
--     ERP 的     domain = 主域名，另外四个子域列（consoledomain / buyerdomain /
--                datadomain / filedomain）留空时由它派生 console.<主域名> 之类
--     本站的     domain = 前台地址本身
--   ERP 那份如果也还是 localhost:5174，要在 ERP 的站点登记页面单独改成
--   tongling.vclinker.com，否则「进入管理」拼出来的跳转地址是错的。
-- =====================================================================

USE `sdsstation`;

-- 只改还是占位值的那一行：已经填过真实域名的库重跑这个脚本不会被覆盖回去。
UPDATE `u_s_siteinfo`
   SET `domain` = 'buyer.tongling.vclinker.com'
 WHERE `id` = 1
   AND (`domain` IS NULL
        OR `domain` = ''
        OR `domain` LIKE 'localhost%'
        OR `domain` LIKE '127.0.0.1%');


-- ---------------------------------------------------------------------
-- 顺手把列注释也纠正掉（只改注释，不动数据）
--
-- 原来写的是「主域名，如 kangchen.com。前台拼绝对地址（canonical / sitemap）用」，
-- 两句都不对：存的是前台那个子域不是主域，而 canonical / sitemap 根本不读这一列
-- ——那是前台自己的 NUXT_PUBLIC_SITE_URL。
--
-- MODIFY COLUMN 要把列定义整段重写，漏掉的属性会被丢掉，
-- 所以这里和 01_station_schema.sql 里那一行保持逐字一致，只有 COMMENT 不同。
-- ---------------------------------------------------------------------
ALTER TABLE `u_s_siteinfo`
  MODIFY COLUMN `domain` varchar(200) DEFAULT NULL
  COMMENT '前台地址，存主机名（不带协议头），如 buyer.tongling.vclinker.com——⚠ 存的是前台那个子域，不是主域，主域上没有前台。两处在用：买家验证/找回密码信里的链接（BuyerAuthService#siteBase）、管理台顶栏的「访问前台」。canonical / sitemap 不读这一列，那是前台的 NUXT_PUBLIC_SITE_URL';


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  `domain` AS `前台地址`,
  IF(`domain` = 'buyer.tongling.vclinker.com',
     'OK',
     CONCAT('⚠ 不是预期值——要么这一行本来就填过别的真实域名（那没问题，确认一下），',
            '要么 WHERE 条件没命中，当前值是：', IFNULL(`domain`, 'NULL')))
    AS `结论`
  FROM `u_s_siteinfo` WHERE `id` = 1;

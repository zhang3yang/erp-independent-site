-- =====================================================================
-- u_s_siteinfo.domain：从 buyer. 子域改回主域
-- =====================================================================
--
-- ⛔ **现在还不要跑**（2026-09-08）。
--
--    它属于「前台改成两 host」那一整套里的第 5 步，而第 1 步还没完成：
--    tongling.vclinker.com **还没备案**，也没有证书，nginx 上没有它的 server 块。
--
--    现在跑的后果：买家注册信、找回密码信里的链接立刻变成
--    https://tongling.vclinker.com/verify-email?token=…，而那个域名连不上
--    ——**信照发、页面照显示、一个错都不报**，只是买家永远验不了邮箱。
--
--    完整的六步清单在 deploy/nginx/tongling.conf 文件头。等那份配置上线了再跑这个。
--
-- 库：sdsstation。可重复执行。
--
-- 这一条是 2026-09-07-site-domain-to-buyer.sql 的**后续**，不是回滚：那一条把
-- 开发期占位（localhost:5174）改成了当时前台真正的地址 buyer.tongling.vclinker.com。
-- 现在前台劈成了两个主机名，这一列要跟着改成主域。
--
-- ## 现在的域名分工（deploy/nginx/tongling.conf）
--
--   tongling.vclinker.com        公开页：首页、商品、购物车、我的询价
--   buyer.tongling.vclinker.com  账号页：登录、注册、找回密码、重置密码、邮箱验证、账户
--
-- 分界线是**要不要登录**。购物车和「我的询价」看着像买家自己的东西，但它们的数据在
-- localStorage 里、按源隔离，挪到 buyer. 上买家在主域加的货那边就是空的，所以留在主域。
--
-- ## 为什么这一列存**主域**
--
-- 两个消费方：
--   · 管理台顶栏那个「访问前台」——当然是主域，那是对外的地址；
--   · 买家验证信 / 找回密码信里的链接（BuyerAuthService#siteBase）——拼出来是
--     https://tongling.vclinker.com/verify-email?token=…，而那一页在 buyer. 上，
--     靠 nginx 那条 302 转过去、query 一起带走。
--
-- 也就是说**后端不需要知道 buyer. 这个域名的存在**，这一列不用记两个值。
-- 让后端也存一份 buyer. 地址的话，同一件事就在 nginx 和数据库里各存一份，
-- 分界线一挪就对不上。
--
-- ## 不跑这个脚本的症状
--
-- 都不报错，都是「点了没用」或者「地址不对但页面照开」：
--   · 验证信、找回密码信里的链接是 https://buyer.tongling.vclinker.com/verify-email?…
--     ——**还是能打开的**（那一页确实在 buyer. 上），所以这一条不是坏，只是让买家
--     看到并可能收藏一个不是对外地址的域名；
--   · 管理台顶栏「访问前台」点进去落在 buyer. 上，被 nginx 301 到主域
--     ——多一跳，而且运营会以为前台地址是 buyer. 那个。
--
-- ⚠ 存**主机名**：不带 https://、不带尾斜杠。
--   （BuyerAuthService#siteBase 和 console-ui 的 utils/siteUrl.js 都能兼容带协议头的
--    历史值，但新写的别再带，否则两套拼接规则里只要有一处没兼容就拼出 https://https//…）
--
-- ⚠ 这一列和 ERP 那边 sdsshop.u_s_site.domain 现在是**同一个语义**了（都是主域名）。
--   那两列曾经一个存主域、一个存前台地址，照抄会错——这个坑没了。
--   ERP 那份如果还是 localhost:5174 或者别的占位，要在 ERP 的站点登记页面单独改成
--   tongling.vclinker.com，否则「进入管理」拼出来的跳转地址是错的。
-- =====================================================================

USE `sdsstation`;

-- ---------------------------------------------------------------------
-- 只改「还是 buyer. 子域」或者「还是占位值」的那一行。
--
-- ⚠ WHERE 里把带协议头的历史写法也算进来（https://buyer.…），
--   因为这一列容忍那种值，库里可能真的存着。
--
-- 已经填过别的真实域名的库（比如以后每个客户带自己的品牌域名）重跑不会被覆盖。
-- ---------------------------------------------------------------------
UPDATE `u_s_siteinfo`
   SET `domain` = 'tongling.vclinker.com'
 WHERE `id` = 1
   AND (`domain` IS NULL
        OR `domain` = ''
        OR `domain` LIKE 'localhost%'
        OR `domain` LIKE '127.0.0.1%'
        OR `domain` = 'buyer.tongling.vclinker.com'
        OR `domain` LIKE 'http://buyer.tongling.vclinker.com%'
        OR `domain` LIKE 'https://buyer.tongling.vclinker.com%');


-- ---------------------------------------------------------------------
-- 列注释跟着纠正（只改注释，不动数据）
--
-- 上一版注释写的是「存的是前台那个子域，不是主域，主域上没有前台」——
-- 那句话现在整个反过来了。
--
-- MODIFY COLUMN 要把列定义整段重写，漏掉的属性会被丢掉，
-- 所以这里和 01_station_schema.sql 里那一行保持逐字一致，只有 COMMENT 不同。
-- ---------------------------------------------------------------------
ALTER TABLE `u_s_siteinfo`
  MODIFY COLUMN `domain` varchar(200) DEFAULT NULL
  COMMENT '主域名，存主机名（不带协议头），如 tongling.vclinker.com——⚠ 存主域不是 buyer. 子域。前台分两个 host：主域放公开页，buyer. 放登录/注册/找回密码/重置密码/邮箱验证/账户。验证信里的链接拼主域，靠 nginx 302 转到 buyer.（deploy/nginx/tongling.conf）。两处在用：买家验证/找回密码信里的链接（BuyerAuthService#siteBase）、管理台顶栏的「访问前台」。canonical / sitemap 不读这一列，那是前台的 NUXT_PUBLIC_SITE_URL';


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  `domain` AS `主域名`,
  IF(`domain` = 'tongling.vclinker.com',
     'OK',
     CONCAT('⚠ 不是预期值——要么这一行本来就填过别的真实域名（那没问题，确认一下），',
            '要么 WHERE 条件没命中，当前值是：', IFNULL(`domain`, 'NULL')))
    AS `结论`
  FROM `u_s_siteinfo` WHERE `id` = 1;

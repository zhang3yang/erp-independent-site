-- =====================================================================
-- 迁移体检：这个库还缺哪些 sql/migration/ 下的结构变更
-- =====================================================================
--
-- 起因：sdsstation 有台账表（u_s_schema_migration），ERP 这三个库
-- （sdssys / sdsshop / sdsorder）没有。部署前想知道远程跑到哪一步了，
-- 只能拿 information_schema 逐条比对。这个脚本就是把那份比对固化下来。
--
-- ⚠ 只读。全程只查 information_schema 和几张配置表，不改任何东西，
--   在生产库上跑是安全的。
--
-- ⚠ 它查的是「结构在不在」，不是「脚本跑没跑」。二者绝大多数时候等价，
--   但脚本里的数据回填部分（比如 goods_summary 的刷值）判不出来。
--   显示「已应用」只代表列/表/索引已经存在。
--
-- 用法：
--   mysql -h <主机> -uroot -p --default-character-set=utf8mb4 < check_migrations.sql
--
-- 输出里凡是带 ⚠ 的，就是要跑的脚本。都不带 ⚠ 就只需要部署代码。
--
-- 判不出来也没关系：sql/migration/ 下的脚本全部写了幂等（见该目录 README），
-- 拿不准就按文件名顺序全跑一遍，跑两遍结果一样。
-- =====================================================================

SET NAMES utf8mb4;

SELECT '20260828_receive_status_default' AS 脚本,
       IF((SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema='sdsorder' AND table_name REGEXP '^u_p_receive_[0-9]{6}$') = 0,
          '— 没有月表，无从判断',
          IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema='sdsorder' AND table_name REGEXP '^u_p_receive_[0-9]{6}$'
                 AND column_name='status' AND column_default IS NULL) = 0,
             '已应用', '⚠ 未应用')) AS 结果,
       '到货单月表 status 列的默认值不为 NULL' AS 判据

UNION ALL SELECT '20260828_shipbatch_status_flow',
       IF((SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema='sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$') = 0,
          '— 没有月表，无从判断',
          IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema='sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$'
                 AND column_name='status' AND (is_nullable='YES' OR column_default IS NULL)) = 0,
             '已应用', '⚠ 未应用')),
       '发货批次月表 status 列 NOT NULL 且有默认值'

UNION ALL SELECT '20260903_goods_summary',
       IF(EXISTS(SELECT 1 FROM information_schema.columns
                  WHERE table_schema='sdsshop' AND table_name='u_p_purchase' AND column_name='goodssummary')
          AND (SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$')
            = (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$'
                  AND column_name='goodssummary'),
          '已应用', '⚠ 未应用'),
       '采购主表 + 全部订单月表都有 goodssummary 列'

UNION ALL SELECT '20260903_station_erp_split',
       IF((SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$') = 0,
          '— 没有月表，无从判断',
          IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$'
                 AND column_name IN ('buyershop','buyeruser') AND is_nullable='YES') = 0,
             '已应用', '⚠ 未应用')),
       '订单月表 buyershop / buyeruser 收成 NOT NULL'

UNION ALL SELECT '20260903_u_s_site_to_sdsshop',
       IF(EXISTS(SELECT 1 FROM information_schema.tables
                  WHERE table_schema='sdsshop' AND table_name='u_s_site'),
          '已应用', '⚠ 未应用'),
       '站点档案表已搬到 sdsshop'

UNION ALL SELECT '20260904_goods_partner_recommend',
       IF(EXISTS(SELECT 1 FROM information_schema.tables
                  WHERE table_schema='sdsshop' AND table_name='u_m_goods_partner'),
          '已应用', '⚠ 未应用'),
       'sdsshop.u_m_goods_partner 表存在'

UNION ALL SELECT '20260906_platform_store_unique_per_platform',
       IF(EXISTS(SELECT 1 FROM information_schema.statistics
                  WHERE table_schema='sdsorder' AND table_name='u_o_platform_store'
                    AND index_name='uk_platform_store_shop_platform_code'),
          '已应用', '⚠ 未应用'),
       '店铺表上的「平台内唯一」索引'

UNION ALL SELECT '20260909_shipbatch_menu_rename',
       IF(EXISTS(SELECT 1 FROM sdssys.sys_menu
                  WHERE fun_code='fn5105' AND menu_name='仓库发货打单'),
          '已应用', '⚠ 未应用'),
       '菜单 fn5105 已改名为「仓库发货打单」'

-- ---- 20260910 拆成四个检查点：它一次改了列、字典和三处列宽 ----
UNION ALL SELECT '20260910_sales_order_export_fields ①外贸字段',
       IF((SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$') = 0,
          '— 没有月表，无从判断',
          IF((SELECT COUNT(*) FROM information_schema.tables
               WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$') * 7
            = (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$'
                  AND column_name IN ('buyercountry','buyeraddress','receivetype',
                                      'markneeded','markfile','descitems','orderfiles')),
             '已应用', '⚠ 未应用')),
       '全部订单月表都齐了那 7 个新列'

UNION ALL SELECT '20260910_sales_order_export_fields ②国家字典',
       IF((SELECT COUNT(*) FROM sdssys.sys_dict_data WHERE dict_type='order_country') >= 200,
          '已应用', '⚠ 未应用'),
       CONCAT('order_country 字典现有 ',
              (SELECT COUNT(*) FROM sdssys.sys_dict_data WHERE dict_type='order_country'),
              ' 行（应为 242）')

UNION ALL SELECT '20260910_sales_order_export_fields ④收件电话放宽',
       IF((SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema='sdsorder' AND column_name='receivetelephone'
              AND character_maximum_length < 100) = 0,
          '已应用', '⚠ 未应用'),
       '订单月表与补发表的 receivetelephone 都是 varchar(100)'

UNION ALL SELECT '20260911_shipbatch_reject',
       IF((SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema='sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$') = 0,
          '— 没有月表，无从判断',
          IF((SELECT COUNT(*) FROM information_schema.tables
               WHERE table_schema='sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$') * 3
            = (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema='sdsorder' AND table_name REGEXP '^u_f_shipbatch_[0-9]{6}$'
                  AND column_name IN ('rejectuser','rejecttime','rejectreason')),
             '已应用', '⚠ 未应用')),
       '全部发货批次月表都齐了驳回三列'

-- ---- 以下为 2026-09-11 补齐：原文件只覆盖 10 个脚本，漏了 11 个。
--      漏掉的里头 09-10/09-11 那四个全是往 sys_menu 插权限点，不改表结构，
--      翻表看不出来，恰恰是最容易在远程缺失的。 ----

UNION ALL SELECT '20260828_drop_builtin_station_console',
       IF((SELECT COUNT(*) FROM sdssys.sys_menu WHERE fun_code REGEXP '^fn90[123][0-9]$') = 0,
          '已应用', '⚠ 未应用'),
       '内置独立站管理台的菜单 fn9010~fn9039 已删除'

-- ⚠ 这一条判「不适用」，不是偷懒。2026-09-11 在远程实测时它报过一次假阳性：
--   远程 sdsshop.u_s_site 确实没有 notifyemail，但 ERP 根本不读这一列——
--   StationSiteMapper.xml 的 <sql id="columns"> 片段里没有它，询盘通知早已整个
--   归站点服务器的 u_s_siteinfo 管（boundary §7.2）。而且这脚本 ALTER 的是
--   sdsstation.u_s_site（旧库），跑了也落不到 sdsshop 上。
--   本地之所以有这一列，是因为本地先跑了 20260903_u_s_site_to_sdsshop（建的是胖版），
--   远程先跑了 20260903_station_erp_split（建的是瘦版），两个脚本都用 CREATE TABLE
--   IF NOT EXISTS，谁先跑谁说了算。瘦版才是和 mapper 对得上的那个。
UNION ALL SELECT '20260831_station_inquiry_notify',
       '— 不适用',
       'ERP 侧不读 notifyemail；本地多出的那一列是建表顺序造成的残留，不必补齐'

UNION ALL SELECT '20260831_station_content_i18n',
       IF(EXISTS(SELECT 1 FROM information_schema.tables
                  WHERE table_schema='sdsstation' AND table_name='u_s_site'),
          '— 不适用（旧库残留）', '— 不适用'),
       '改的是 sdsstation.u_s_site；ERP 代码早已改读 sdsshop.u_s_site，这库留着也不再被访问'

UNION ALL SELECT '20260831_station_goods_description_scan',
       '— 不适用',
       '纯检测脚本，只报告存量商品详情里的危险 HTML，不改任何数据'

UNION ALL SELECT '20260904_rename_station_menu',
       IF(EXISTS(SELECT 1 FROM sdssys.sys_menu WHERE fun_code='fn9001' AND menu_name='独立站管理'),
          '已应用', '⚠ 未应用'),
       '菜单 fn9001 已从「站点控制台」改名为「独立站管理」'

UNION ALL SELECT '20260907_site_buyerdomain_is_front',
       '— 被 20260908 取代',
       '同一列的注释前后改了两次，按文件名顺序跑即可，最终以 20260908 为准'

UNION ALL SELECT '20260908_site_buyerdomain_is_buyer_center',
       IF(EXISTS(SELECT 1 FROM information_schema.columns
                  WHERE table_schema='sdsshop' AND table_name='u_s_site'
                    AND column_name='buyerdomain' AND column_comment LIKE '%买家中心%'),
          '已应用', '⚠ 未应用'),
       'buyerdomain 列注释为「买家中心域名」。只改注释，不跑功能也正常'

UNION ALL SELECT '20260910_price_visibility',
       IF((SELECT COUNT(*) FROM sdssys.sys_menu WHERE fun_code IN ('fn9040','fn9041','fn9042')) = 3,
          '已应用', '⚠ 未应用'),
       '价格可见性菜单 fn9040 + fn9041(price:cost) + fn9042(price:sale)'

-- ⚠ 这一条原来会假阳性：它数的是「违反宽度要求的列有几个」，而列压根不存在时
--   这个计数同样是 0，于是在还没跑 ① 的库上显示「已应用」。2026-09-11 远程实测
--   就踩到了：①②④ 全红，③ 独自显示绿。加一个「列得先存在」的前置判断。
UNION ALL SELECT '20260910_sales_order_export_fields ③附件列放宽',
       IF(NOT EXISTS(SELECT 1 FROM information_schema.columns
                      WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$'
                        AND column_name='markfile'),
          '— 待①先建列',
          IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema='sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$'
                 AND ((column_name='markfile'   AND character_maximum_length < 1000)
                   OR (column_name='orderfiles' AND data_type <> 'text'))) = 0,
             '已应用', '⚠ 未应用')),
       'markfile 至少 varchar(1000)、orderfiles 是 text'

UNION ALL SELECT '20260911_sales_order_own_scope',
       IF(EXISTS(SELECT 1 FROM sdssys.sys_menu WHERE fun_code='fn8042' AND perms='order:sales:all'),
          '已应用', '⚠ 未应用'),
       '菜单 fn8042「查看全部订单」order:sales:all'

UNION ALL SELECT '20260911_order_own_scope_rest',
       IF((SELECT COUNT(*) FROM sdssys.sys_menu WHERE fun_code IN ('fn8043','fn8044','fn8045')) = 3,
          '已应用', '⚠ 未应用'),
       '发货批次 / 销售退货 / 销售补发 三个「查看全部」权限点'

UNION ALL SELECT '20260911_shipbatch_warehouse_permi',
       IF(EXISTS(SELECT 1 FROM sdssys.sys_menu WHERE perms='order:shipbatch:warehouse'),
          '已应用', '⚠ 未应用'),
       '仓库发货页的独立权限串 order:shipbatch:warehouse'

ORDER BY 脚本;

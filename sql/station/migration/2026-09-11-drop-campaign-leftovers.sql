-- =====================================================================
-- 清掉「邮件群发」撤销之后留在库里的东西
-- =====================================================================
--
-- 库：sdsstation。可重复执行。
--
-- =====================================================================
-- 背景
-- =====================================================================
--
-- 邮件群发功能在 `442f02cb`（@ 撤销邮件群发）里从代码侧整个删掉了：
-- SiteCampaign / SiteCampaignTarget / SiteCampaignMapper / CampaignSender /
-- SiteCampaignService 连同 mapper XML 和两个 Controller 里的接口，一共 -1552 行。
--
-- **但库里的东西没有跟着走**。2026-09-11 盘点本机 sdsstation 时发现三张表还在：
--
--   u_s_campaign          2 行
--   u_s_campaign_target   2 行
--   u_s_unsub             1 行（一条真实的退订记录，来自 CAMP0000000002）
--
-- 另外 u_s_role 里 operator 的 perms 上还挂着三个已经没有任何接口对应的权限：
-- site:campaign:list / :query / :edit。
--
-- 它们不影响运行（后端零引用，权限字符串多几个也不报错），属于该清的残渣。
-- 这和 [[stale-target-mapper-xml]] 记的那个坑是同一件事的两半：
-- **撤销一个功能时，代码、target/ 产物、数据库三处都要走一遍**，
-- 只删代码的话后两处会以「查起来完全摸不着头脑」的方式留在那儿。
--
-- =====================================================================
-- ⚠ 跑之前
-- =====================================================================
--
-- 这一条是**删数据**，不可逆。u_s_unsub 里那条退订记录会一起没。
-- 判断「能不能删」的依据是**代码侧零引用**，不是「表是空的」：
--
--     grep -rn "campaign\|Campaign\|u_s_unsub" station-console/src station-console-ui/src
--
-- 应当零结果。不是零结果说明有人在本脚本写成之后又把功能接回来了，
-- 那就别跑，回去看 git 历史。
--
-- 跑之前留一份（本机那次留在 scratchpad/drop-backup-20260911.sql）：
--
--     mysqldump -uroot -p --databases sdsstation \
--       --tables u_s_campaign u_s_campaign_target u_s_unsub > <某处>.sql
--
-- ⚠ Windows 上跑这个文件要带 --default-character-set=utf8mb4，
--   否则客户端按 latin1 读，带中文默认值/注释的语句会报 Invalid default value。
-- =====================================================================

USE `sdsstation`;

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 一、三张表
--
-- 没有外键，顺序其实无所谓，但还是先删子表，免得以后有人加了外键再跑这个脚本。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `u_s_campaign_target`;
DROP TABLE IF EXISTS `u_s_campaign`;
DROP TABLE IF EXISTS `u_s_unsub`;


-- ---------------------------------------------------------------------
-- 二、operator 身上那三个没人认的权限
--
-- 用 REPLACE 一个一个摘，不整列重写：这一列在不同站点上内容不一样
-- （装修、站长工具那几批各自往上追加过），整列写死会把别的站的配置抹平。
--
-- 带上前面那个逗号一起摘，否则会留下 `,,`。
-- admin 是 '*'，REPLACE 碰不到它。
-- ---------------------------------------------------------------------
UPDATE `u_s_role`
   SET `perms` = REPLACE(REPLACE(REPLACE(`perms`,
         ',site:campaign:list',  ''),
         ',site:campaign:query', ''),
         ',site:campaign:edit',  '')
 WHERE `perms` LIKE '%site:campaign:%';


-- ---------------------------------------------------------------------
-- 三、登记台账
-- ---------------------------------------------------------------------
SET @has_ledger := (
  SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_schema_migration'
);
SET @ledger_sql := IF(@has_ledger > 0,
  'INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
     VALUES (''2026-09-11-drop-campaign-leftovers.sql'', ''claude'', ''执行'',
             ''删 u_s_campaign/u_s_campaign_target/u_s_unsub，摘掉 operator 的 site:campaign:* 三个权限'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记'' AS msg');
PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 四、验一遍
-- ---------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM information_schema.`TABLES`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` IN ('u_s_campaign','u_s_campaign_target','u_s_unsub'))
                                                          AS `三张表还剩几张_应为0`,
  (SELECT COUNT(*) FROM `u_s_role` WHERE `perms` LIKE '%site:campaign:%')
                                                          AS `还挂着权限的角色_应为0`,
  (SELECT COUNT(*) FROM `u_s_role` WHERE `perms` LIKE '%,,%')
                                                          AS `权限串里有双逗号的_应为0`;

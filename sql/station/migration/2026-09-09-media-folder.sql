-- =====================================================================
-- 文件资源的文件夹：给图片和视频各一棵可以无限嵌套的目录树
-- =====================================================================
--
-- 管理台「文件资源」页左侧要一棵树，上传时能选传到哪个文件夹，
-- 商品/博客里挑图的 MediaPicker 也按文件夹浏览。
--
-- ⚠ 图片和视频是**两棵独立的树**，靠 mediatype 分开。
--   页面上本来就是「图片 / 视频」两个页签，共用一棵的话切到视频页签会看到
--   一堆只装着图片的空文件夹。
--
-- ⚠ 删文件夹时里面的文件**回落到根目录**（folderid 置 NULL），不级联删文件。
--   图片很可能正被商品页或博客引用着，跟着文件夹一起删掉，前台就是一片裂图，
--   而且不可恢复。这一条在 SiteFolderService.remove() 里实现，不靠外键。
--
-- 可重复执行：CREATE TABLE IF NOT EXISTS + information_schema 判列 + INSERT IGNORE。
-- =====================================================================

USE `sdsstation`;


-- =====================================================================
-- 一、目录树
-- =====================================================================
--
-- 自引用的邻接表（parentid 指向父行），不是 path 字符串或闭包表：
-- 一个站的文件夹撑死几十个，树整棵读出来在内存里拼，比维护物化路径省事得多，
-- 改名和移动也不用批量重写子孙行。

CREATE TABLE IF NOT EXISTS `u_s_sitefolder` (
  `folderid`   varchar(50)  NOT NULL                COMMENT '主键，CodeGenerator 发号，形如 SFD00000001',
  `shop_code`  varchar(50)  NOT NULL DEFAULT ''     COMMENT '【遗留列】恒为建站时那个商户码，新代码不得读它',
  `sitecode`   varchar(50)  NOT NULL                COMMENT '来自哪个站点，恒等于 u_s_siteinfo.sitecode',
  `mediatype`  varchar(10)  NOT NULL                COMMENT '这棵树装什么：image / video。⚠ 两棵树全靠它分开，每条查询都要带上',
  `parentid`   varchar(50)           DEFAULT NULL   COMMENT '父文件夹。NULL = 顶层。⚠ 不建外键：删父文件夹时子孙要「提升」而不是级联删，外键做不了这个',
  `foldername` varchar(100) NOT NULL                COMMENT '文件夹名。同一个父下不重名，由 Service 判，不靠唯一索引（软删/大小写规则以后可能变）',
  `sort`       int          NOT NULL DEFAULT 0      COMMENT '同级排序，小的在前。相同时按 foldername',
  `createtime` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`  varchar(64)           DEFAULT NULL   COMMENT '创建人',
  `update_by`  varchar(64)           DEFAULT NULL   COMMENT '最后修改人',
  PRIMARY KEY (`folderid`),
  KEY `idx_folder_tree` (`sitecode`, `mediatype`, `parentid`, `sort`) COMMENT '按树读整棵：一次查出某一类的全部文件夹，在内存里拼层级'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件资源的目录树。图片和视频各一棵，靠 mediatype 分';


-- =====================================================================
-- 二、发号器加一行
-- =====================================================================
-- ⚠ 少了这一行，建文件夹时 CodeGenerator.next() 会抛「发号器里没有 folder 这一行」。

INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES ('folder', 1);


-- =====================================================================
-- 三、图片和视频各加一列 folderid
-- =====================================================================
--
-- NULL = 没归类，显示在「全部 / 未分类」里。历史数据全是 NULL，这是安全的落点：
-- 不加这一列之前的图片一张都不会丢，只是待在根上等人整理。
--
-- MySQL 没有 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的扩展），用存储过程判一下。

DROP PROCEDURE IF EXISTS `__add_folderid_columns`;

DELIMITER $$
CREATE PROCEDURE `__add_folderid_columns`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_siteimage'
                   AND column_name = 'folderid') THEN
    ALTER TABLE `u_s_siteimage`
      ADD COLUMN `folderid` varchar(50) DEFAULT NULL
        COMMENT '所在文件夹，指向 u_s_sitefolder.folderid。NULL = 未分类（根）。⚠ 删文件夹时这一列被置回 NULL，文件本身不删'
        AFTER `remark`,
      ADD KEY `idx_image_folder` (`folderid`);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitevideo'
                   AND column_name = 'folderid') THEN
    ALTER TABLE `u_s_sitevideo`
      ADD COLUMN `folderid` varchar(50) DEFAULT NULL
        COMMENT '所在文件夹，指向 u_s_sitefolder.folderid。NULL = 未分类（根）'
        AFTER `remark`,
      ADD KEY `idx_video_folder` (`folderid`);
  END IF;
END$$
DELIMITER ;

CALL `__add_folderid_columns`();
DROP PROCEDURE IF EXISTS `__add_folderid_columns`;


-- =====================================================================
-- 四、权限
-- =====================================================================
-- 文件夹的增删改跟着文件资源那组权限走，不另开一套：能传图的人本来就能建文件夹。
-- 只给 operator 追加，admin 是 '*' 不用动。

UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`, ',site:file:folder')
 WHERE `rolecode` = 'operator'
   AND `perms` != '*'
   AND NOT FIND_IN_SET('site:file:folder', `perms`);


-- =====================================================================
-- 五、登记台账
-- =====================================================================

SET @has_ledger := (
  SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_schema_migration'
);
SET @ledger_sql := IF(@has_ledger > 0,
  'INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
     VALUES (''2026-09-09-media-folder.sql'', ''claude'', ''执行'',
             ''建 u_s_sitefolder，图片/视频各加 folderid 列，发号器加 folder'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记'' AS msg');
PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 六、自查
-- =====================================================================
--   SHOW CREATE TABLE u_s_sitefolder;
--   SHOW COLUMNS FROM u_s_siteimage LIKE 'folderid';
--   SELECT * FROM u_s_sequence WHERE name = 'folder';

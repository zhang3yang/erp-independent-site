-- =====================================================================
-- 商品 3D 模型：文件库第三种媒体类型 + 商品上一列模型地址
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
--
-- 前台的 3D 展示台（station-site，2026-09-04 提交 3b190ef8）早就上线了，但一直没有
-- 正规的数据来源——商家只能把模型地址塞进规格参数的 model3d 键。这份脚本补后端那三截：
--
--   ① u_s_sitemodel          文件库里的模型，和 u_s_sitevideo 同构，去掉宽高时长
--   ② u_s_sitegoods.model3d  商品挂的那一个模型的完整地址；空 = 没有模型
--   ③ u_s_sequence           SMDL 号段
--
-- 设计：docs/superpowers/specs/2026-09-20-product-3d-model-design.md
--
-- ---------------------------------------------------------------------
-- 两个取舍
-- ---------------------------------------------------------------------
--
-- 1. model3d 存 URL 不存 modelid。和 siteimgs / sitevideos 一致：前台只认 URL，
--    文件库删了记录商品也不跟着裂（文件本身留在桶里）。代价是换存储域名时这一列
--    要按 2026-09-06-file-url-rebase.sql 那套做字符串替换——已加进那份脚本的清单。
--
-- 2. 只收 .glb。白名单在 application.yml 的 station.upload.model-ext，不在库里。
--    .gltf 是多文件格式（外链 .bin 和贴图），单文件上传上去必裂，所以不放行。
--
-- 可重复执行：建表 IF NOT EXISTS，加列走 information_schema 判断，序列 INSERT IGNORE。


-- =====================================================================
-- ① 模型文件库
-- =====================================================================

CREATE TABLE IF NOT EXISTS `u_s_sitemodel` (
  `modelid`    varchar(50)  NOT NULL COMMENT '模型主键，SMDL 开头',
  `shop_code`  varchar(50)  NOT NULL DEFAULT '' COMMENT '【遗留列】同 u_s_sitevideo',
  `sitecode`   varchar(50)  DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `modelname`  varchar(200) NOT NULL COMMENT '显示名称，默认取原始文件名，可改',
  `modelurl`   varchar(500) NOT NULL COMMENT '完整访问地址，上传那一刻整串入库',
  `objectname` varchar(300) DEFAULT NULL COMMENT '桶内对象路径，model/YYYY/MM/DD/<modelid>.glb',
  `provider`   varchar(20)  DEFAULT NULL COMMENT '存到了哪个厂商，换厂商后老文件仍可追溯',
  `filesize`   bigint       DEFAULT NULL COMMENT '字节数。前台按它提示加载慢不慢',
  `mimetype`   varchar(100) DEFAULT NULL COMMENT '浏览器给的 Content-Type，.glb 常见 model/gltf-binary 或空',
  `remark`     varchar(500) DEFAULT NULL,
  `folderid`   varchar(50)  DEFAULT NULL COMMENT '所在文件夹，NULL = 未分类',
  `createtime` datetime     DEFAULT NULL,
  `updatetime` datetime     DEFAULT NULL,
  `create_by`  varchar(64)  DEFAULT NULL,
  `update_by`  varchar(64)  DEFAULT NULL,
  PRIMARY KEY (`modelid`),
  KEY `idx_sitemodel_shop` (`shop_code`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点 3D 模型资源（.glb）';


-- =====================================================================
-- ② 商品上的模型地址
-- =====================================================================

DROP PROCEDURE IF EXISTS `__add_goods_model3d`;
DELIMITER $$
CREATE PROCEDURE `__add_goods_model3d`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  -- 挨着 sitevideos 放：三列都是「来自文件资源库的站点媒体」
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_sitegoods'
                   AND column_name = 'model3d') THEN
    ALTER TABLE `u_s_sitegoods`
      ADD COLUMN `model3d` varchar(500) DEFAULT NULL
      COMMENT '3D 模型（.glb）完整地址，来自文件资源库；空 = 没有模型，详情页不渲染展示台'
      AFTER `sitevideos`;
  END IF;
END$$
DELIMITER ;

CALL `__add_goods_model3d`();
DROP PROCEDURE `__add_goods_model3d`;


-- =====================================================================
-- ③ 号段
-- =====================================================================

INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES ('model', 1);


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitemodel')        AS `模型表_应为1`,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_sitegoods'
      AND column_name = 'model3d')                                            AS `商品模型列_应为1`,
  (SELECT COUNT(*) FROM `u_s_sequence` WHERE `name` = 'model')                AS `号段_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_sitegoods` DROP COLUMN `model3d`;
-- DROP TABLE `u_s_sitemodel`;
-- DELETE FROM `u_s_sequence` WHERE `name` = 'model';
--
-- ⚠ 回滚前先确认 jar 已经换回旧版：新版 SiteGoodsMapper.xml 的列清单里有 model3d，
--    列没了它每次查商品都 500。

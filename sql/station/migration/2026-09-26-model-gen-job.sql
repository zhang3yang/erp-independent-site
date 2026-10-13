-- =====================================================================
-- 混元生 3D 任务表：管理台「用照片生成」3D 模型
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
--
-- 流程：管理台从文件库选商品照片 → 后端提交腾讯混元生 3D（SubmitHunyuanTo3DProJob）→
-- 定时轮询（QueryHunyuanTo3DProJob）→ 完成后下载 glb 转存到本站存储，入 u_s_sitemodel。
-- 这张表只记任务本身；生成出来的模型和手动上传的一样在 u_s_sitemodel 里。
--
-- 设计：docs/superpowers/specs/2026-09-26-hunyuan-3d-generate-design.md
--
-- 可重复执行：建表 IF NOT EXISTS，号段 INSERT IGNORE。本脚本不写迁移台账，跑完手工补一条。


CREATE TABLE IF NOT EXISTS `u_s_model_job` (
  `jobid`       varchar(50)   NOT NULL COMMENT '本站任务号，SMJB 开头',
  `sitecode`    varchar(50)   DEFAULT NULL COMMENT '来源追溯用，不参与过滤',
  `tcjobid`     varchar(100)  DEFAULT NULL COMMENT '混元返回的 JobId（24 小时有效）；提交失败时为空',
  `status`      varchar(20)   NOT NULL COMMENT 'SUBMITTING / WAIT / RUN / SAVING / DONE / FAIL',
  `jobname`     varchar(200)  DEFAULT NULL COMMENT '生成后模型的显示名',
  `imagesjson`  varchar(4000) DEFAULT NULL COMMENT '{"front":url,"left":url,"right":url,"back":url}，只存用到的',
  `facecount`   int           DEFAULT NULL,
  `enablepbr`   tinyint       NOT NULL DEFAULT '0',
  `folderid`    varchar(50)   DEFAULT NULL COMMENT '生成后放进哪个模型文件夹',
  `modelid`     varchar(50)   DEFAULT NULL COMMENT '成功后对应的 u_s_sitemodel.modelid',
  `previewurl`  varchar(1000) DEFAULT NULL COMMENT '混元给的预览图地址（临时，过期就不显示）',
  `errorcode`   varchar(100)  DEFAULT NULL,
  `errormsg`    varchar(1000) DEFAULT NULL COMMENT '已翻译成给商家看的话',
  `polls`       int           NOT NULL DEFAULT '0' COMMENT '已查询次数',
  `saveretries` int           NOT NULL DEFAULT '0' COMMENT '下载转存失败次数，满 5 次置 FAIL',
  `create_by`   varchar(64)   DEFAULT NULL,
  `createtime`  datetime      DEFAULT NULL,
  `updatetime`  datetime      DEFAULT NULL,
  `finishtime`  datetime      DEFAULT NULL,
  PRIMARY KEY (`jobid`),
  KEY `idx_modeljob_status` (`status`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='混元生 3D 任务（照片生成 .glb）';


-- 本站任务号 SMJB 的号段
INSERT IGNORE INTO `u_s_sequence` (`name`, `nextval`) VALUES ('modeljob', 1);


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'u_s_model_job')        AS `任务表_应为1`,
  (SELECT COUNT(*) FROM `u_s_sequence` WHERE `name` = 'modeljob')           AS `号段_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- DROP TABLE `u_s_model_job`;
-- DELETE FROM `u_s_sequence` WHERE `name` = 'modeljob';
--
-- ⚠ 回滚前先把 jar 换回旧版：新版每 15 秒轮询这张表，表没了日志会刷错误（不影响其他功能）。

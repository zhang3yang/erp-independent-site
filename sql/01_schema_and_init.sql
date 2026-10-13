-- ============================================================================
-- wczl-erp 数据库基线：表结构 + 出厂数据
--
-- 这一个文件就是全部。执行它，再执行可选的 02_demo_data.sql，就能启动项目。
--
--     mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < 01_schema_and_init.sql
--
-- ----------------------------------------------------------------------------
-- ⚠ 本脚本自带 CREATE DATABASE 和 USE，会无视你当前选的库
-- ----------------------------------------------------------------------------
-- 下面每一段前面都有 `USE sdssys` / `USE sdsshop` / `USE sdsorder`。
-- 也就是说：你在客户端里先建一个 sdssys_test 再执行本文件，**没有用**——脚本会跳回
-- 上面那三个库。想灌到别的库名下测试，必须先把文件里的库名替换掉：
--
--     # Git Bash
--     sed 's/`sdssys`/`t_sdssys`/g; s/`sdsshop`/`t_sdsshop`/g;
--          s/`sdsorder`/`t_sdsorder`/g' \\
--         01_schema_and_init.sql > /tmp/t01.sql
--
-- 换了库名之后应用是连不上的（库名写死在 mapper XML 里），那样只能验 SQL 本身。
--
-- ----------------------------------------------------------------------------
-- 跑在已有数据的库上：不会毁数据，但也修不好结构
-- ----------------------------------------------------------------------------
-- 全文只有 CREATE DATABASE IF NOT EXISTS / CREATE TABLE IF NOT EXISTS /
-- INSERT IGNORE，没有 DROP、没有 TRUNCATE、没有 DELETE、没有 ALTER。
-- 已存在的表会被整张跳过，已存在的行会被忽略。
--
-- 所以它是给「新拉取项目的人」用的出厂脚本，不是迁移工具。
-- 已经在跑的环境（包括远程库）请用 sql/migration/ 下的增量脚本。
--
-- ----------------------------------------------------------------------------
-- 建了三个库，库名不能改
-- ----------------------------------------------------------------------------
--     sdssys      系统库：菜单、字典、角色、账号、租户、定时任务
--     sdsshop     主数据库：商品、仓库、库存、批次、序列号、采购、生产
--     sdsorder    单据库：出入库单、流水、订单、发货批次、资金流水
--
-- ⚠ 以前还有第四个库（独立站：站点、上架商品、多语言文案、询盘）。
--   它整个搬到独立站服务器上去了，ERP 这边不再建它
--   （docs/独立站与ERP的边界.md §5.2）。站点服务器上那份建库脚本在
--   sql/station/01_station_schema.sql。
--
--   只有站点【登记表】u_s_site 留了下来，进了 sdsshop——见那一节。
--   这个脚本跑完之后，ERP 侧应当建不出那个库，
--   那是「拆干净了」最省事的一个判据（§10.1）。
--
-- 库名在 mapper XML 里是写死的（如 `from sdsshop.u_f_account`），
-- 改库名要连着几十个 XML 一起改，别改。
--
-- ----------------------------------------------------------------------------
-- 月表不在这里
-- ----------------------------------------------------------------------------
-- sdsorder 下 11 类按月分表（u_f_docmain_yyyyMM、u_o_order_yyyyMM ……）
-- 由 MonthlyTableInitializer 在后端启动时自动建当月和下月，本脚本一张都不建。
-- 所以：执行完 SQL 直接启动后端，月表会自己出来，不要手工建。
--
-- 反过来说，月表的列定义只存在于 Java 代码里
-- （ruoyi-system/.../runner/MonthlyTableInitializer.java 的 build*DDL 方法）。
-- 以后给月表加列，改的是那里 + sql/migration/ 下的存量月表脚本，不是本文件。
--
-- ----------------------------------------------------------------------------
-- 出厂账号
-- ----------------------------------------------------------------------------
--     wczladmin    平台超级管理员，能看到全部三个商家
--     us208XQ03    天津康万家 的商家管理员
--     us208XQ02    常德睿腾 / 天津康晨 的商家管理员
--
-- 密码统一是 admin123。这是公开的默认口令，部署到任何真实环境前先改掉。
--
-- ----------------------------------------------------------------------------
-- 本文件是生成的，不要手改
-- ----------------------------------------------------------------------------
-- 生成方式见 sql/README.md 和 sql/tools/rebuild_baseline.sh。
-- 手改会在下次重新生成时被覆盖；要改结构请走 sql/migration/。
-- ============================================================================

SET NAMES utf8mb4;

/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `sdssys` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `sdssys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `gen_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生成功能作者',
  `form_col_num` int DEFAULT '1' COMMENT '表单布局（单列 双列 三列）',
  `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `gen_table_column` (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_blob_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='Blob类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_calendars` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='日历信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_cron_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='Cron类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_fired_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='已触发的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_job_details` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='任务详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_locks` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='存储的悲观锁信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_paused_trigger_grps` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='暂停的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_scheduler_state` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='调度器状态表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_simple_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='简单触发器的信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_simprop_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='同步机制的行锁表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`) USING BTREE,
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='触发器详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_m_function` (
  `fun_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '功能代码',
  `fun_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '功能名称（英文标识，如 order_view）',
  `fun_text` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '功能显示名称（中文，如订单查询）',
  `fun_parent` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '功能父节点（构建菜单树）',
  `order_no` int DEFAULT '0' COMMENT '排序号（同级排序）',
  `fun_type` tinyint NOT NULL COMMENT '功能类型：1-模块 2-页面 3-按钮',
  `status` tinyint DEFAULT '1' COMMENT '启用状态：1-启用 0-禁用',
  `remark` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `max_sub` int DEFAULT '0' COMMENT '最大子编号（用于子功能编号生成）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`fun_code`) USING BTREE,
  UNIQUE KEY `uk_s_m_function_name` (`fun_name`) USING BTREE,
  KEY `idx_s_m_function_parent_order` (`fun_parent`,`order_no`) USING BTREE,
  KEY `idx_s_m_function_status_type` (`status`,`fun_type`) USING BTREE,
  CONSTRAINT `fk_s_m_function_parent` FOREIGN KEY (`fun_parent`) REFERENCES `s_m_function` (`fun_code`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='系统功能表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_m_shop` (
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺代码',
  `shop_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺名称',
  `province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属省',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属市',
  `district` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属区县',
  `address` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '详细地址',
  `telephone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '联系电话',
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '描述',
  `image_url` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图片网址（Logo）',
  `console_url` varchar(500) DEFAULT NULL COMMENT '这个商家专用的独立站管理台地址（https://...）。留空=用全局 ruoyi.station-console.base-url。站点档案上的管理台地址优先级更高。只允许 http/https',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-停用 -1-已删除',
  PRIMARY KEY (`shop_code`) USING BTREE,
  KEY `idx_s_m_shop_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_m_shop_role` (
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺代码',
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `remark` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `order_no` int DEFAULT '0' COMMENT '排序号（数字越小越靠前）',
  `system_flag` tinyint DEFAULT '0' COMMENT '是否系统内置：1-是 0-否',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用 0-停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`role_code`) USING BTREE,
  UNIQUE KEY `uk_s_m_shop_role_shop_role` (`shop_code`,`role_code`) USING BTREE,
  UNIQUE KEY `uk_s_m_shop_role_shop_name` (`shop_code`,`role_name`) USING BTREE,
  KEY `idx_s_m_shop_role_status_order` (`shop_code`,`status`,`order_no`) USING BTREE,
  CONSTRAINT `fk_s_m_shop_role_shop` FOREIGN KEY (`shop_code`) REFERENCES `s_m_shop` (`shop_code`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_m_shop_staff` (
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺代码',
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户代码',
  `job_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '工号',
  `job_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '岗位名称',
  `org_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '组织代码（部门编号）',
  `org_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '组织名称（冗余）',
  `org_path` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '组织路径（冗余）',
  `mobile` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '手机号',
  `user_image` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '头像网址',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_code`) USING BTREE,
  UNIQUE KEY `uk_s_m_shop_staff_shop_user` (`shop_code`,`user_code`) USING BTREE,
  KEY `idx_s_m_shop_staff_org` (`shop_code`,`org_code`) USING BTREE,
  KEY `idx_s_m_shop_staff_mobile` (`shop_code`,`mobile`) USING BTREE,
  CONSTRAINT `fk_s_m_shop_staff_org` FOREIGN KEY (`shop_code`, `org_code`) REFERENCES `s_u_organize` (`shop_code`, `org_code`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_s_m_shop_staff_shop` FOREIGN KEY (`shop_code`) REFERENCES `s_m_shop` (`shop_code`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家员工表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_m_shop_storage` (
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码，一个商家一套配置',
  `provider` varchar(20) NOT NULL COMMENT '厂商：aliyun / huawei / tencent',
  `endpoint` varchar(200) DEFAULT NULL COMMENT '访问域名。腾讯云留空，由 region 推导',
  `region` varchar(50) DEFAULT NULL COMMENT '地域代号，腾讯云必填，如 ap-beijing',
  `access_key_id` varchar(200) NOT NULL COMMENT 'AccessKeyId / SecretId，明文存',
  `access_key_secret` varchar(500) NOT NULL COMMENT 'AccessKeySecret / SecretKey，AES-GCM 密文',
  `bucket` varchar(100) NOT NULL COMMENT '桶名。腾讯云必须带 APPID，如 name-1300000000',
  `public_url` varchar(200) DEFAULT NULL COMMENT '自定义域名/CDN。留空用 https://<bucket>.<endpoint>/',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 1 停用',
  `active` char(1) NOT NULL DEFAULT '0' COMMENT '是否当前生效：1 是 0 否。同一商家只有一行为 1',
  `createtime` datetime DEFAULT NULL,
  `updatetime` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`shop_code`,`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家对象存储配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_m_staff_role` (
  `relation_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺代码',
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户代码',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色代码',
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '角色名称（冗余）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`relation_code`) USING BTREE,
  UNIQUE KEY `uk_s_m_staff_role_user_role` (`shop_code`,`user_code`,`role_code`) USING BTREE,
  KEY `idx_s_m_staff_role_role` (`shop_code`,`role_code`) USING BTREE,
  CONSTRAINT `fk_s_m_staff_role_role` FOREIGN KEY (`shop_code`, `role_code`) REFERENCES `s_m_shop_role` (`shop_code`, `role_code`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_s_m_staff_role_staff` FOREIGN KEY (`shop_code`, `user_code`) REFERENCES `s_m_shop_staff` (`shop_code`, `user_code`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='员工角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_shop_role_function` (
  `relation_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺代码',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色代码',
  `fun_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '功能代码',
  `fun_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '功能名称（冗余）',
  `fun_text` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '功能显示名称（冗余）',
  `fun_parent` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '功能父节点（冗余）',
  `order_no` int DEFAULT '0' COMMENT '排序号（冗余）',
  `fun_type` tinyint DEFAULT NULL COMMENT '功能类型（冗余）',
  `fun_status` tinyint DEFAULT '1' COMMENT '启用状态（冗余）',
  `fun_remark` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注（冗余）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`relation_code`) USING BTREE,
  UNIQUE KEY `uk_s_shop_role_function_role_fun` (`shop_code`,`role_code`,`fun_code`) USING BTREE,
  KEY `idx_s_shop_role_function_fun` (`fun_code`) USING BTREE,
  CONSTRAINT `fk_s_shop_role_function_fun` FOREIGN KEY (`fun_code`) REFERENCES `s_m_function` (`fun_code`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_s_shop_role_function_role` FOREIGN KEY (`shop_code`, `role_code`) REFERENCES `s_m_shop_role` (`shop_code`, `role_code`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家角色功能表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `s_u_organize` (
  `org_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组织代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '店铺代码',
  `org_parent` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '父节点代码（顶级统一使用NULL）',
  `org_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组织名称',
  `order_no` int DEFAULT '0' COMMENT '排序号（同级排序）',
  `max_sub` int DEFAULT '0' COMMENT '最大子编号（用于编号生成）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`org_code`) USING BTREE,
  UNIQUE KEY `uk_s_u_organize_shop_org` (`shop_code`,`org_code`) USING BTREE,
  KEY `idx_s_u_organize_parent_order` (`shop_code`,`org_parent`,`order_no`) USING BTREE,
  CONSTRAINT `fk_s_u_organize_parent` FOREIGN KEY (`shop_code`, `org_parent`) REFERENCES `s_u_organize` (`shop_code`, `org_code`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_s_u_organize_shop` FOREIGN KEY (`shop_code`) REFERENCES `s_m_shop` (`shop_code`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='组织机构表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家代码（空字符串表示平台数据）',
  `legacy_org_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '旧组织代码（迁移映射）',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`) USING BTREE,
  UNIQUE KEY `uk_sys_dept_shop_legacy_org` (`shop_code`,`legacy_org_code`) USING BTREE,
  KEY `idx_sys_dept_shop_parent_status` (`shop_code`,`parent_id`,`status`,`del_flag`) USING BTREE,
  KEY `idx_sys_dept_shop_name` (`shop_code`,`dept_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=231 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '字典类型',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE KEY `dict_type` (`dict_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务调度表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_job_log` (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '执行开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '执行结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_logininfor` (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '操作系统',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`) USING BTREE,
  KEY `idx_sys_logininfor_s` (`status`) USING BTREE,
  KEY `idx_sys_logininfor_lt` (`login_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1136 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜单名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '路由名称',
  `is_frame` int DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '备注',
  `fun_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `parent_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0',
  PRIMARY KEY (`fun_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_notice` (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_notice_read` (
  `read_id` bigint NOT NULL AUTO_INCREMENT COMMENT '已读主键',
  `notice_id` int NOT NULL COMMENT '公告id',
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `read_time` datetime NOT NULL COMMENT '阅读时间',
  PRIMARY KEY (`read_id`) USING BTREE,
  UNIQUE KEY `uk_user_notice` (`user_code`,`notice_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='公告已读记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '请求方式',
  `operator_type` int DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`) USING BTREE,
  KEY `idx_sys_oper_log_bt` (`business_type`) USING BTREE,
  KEY `idx_sys_oper_log_s` (`status`) USING BTREE,
  KEY `idx_sys_oper_log_ot` (`oper_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1831 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家代码（空字符串表示平台岗位）',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`) USING BTREE,
  KEY `idx_sys_post_shop_status` (`shop_code`,`status`) USING BTREE,
  KEY `idx_sys_post_shop_name` (`shop_code`,`post_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_role` (
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家代码（空字符串表示平台角色）',
  `role_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PLATFORM' COMMENT '角色类型（PLATFORM平台 TENANT商家）',
  `system_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否内置角色（1是 0否）',
  `legacy_role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '旧角色代码（迁移映射）',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_code`) USING BTREE,
  UNIQUE KEY `uk_sys_role_shop_legacy_role` (`shop_code`,`legacy_role_code`) USING BTREE,
  KEY `idx_sys_role_shop_type_status` (`shop_code`,`role_type`,`status`,`del_flag`) USING BTREE,
  KEY `idx_sys_role_shop_name` (`shop_code`,`role_name`) USING BTREE,
  KEY `idx_sys_role_shop_key` (`shop_code`,`role_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_code`,`dept_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='角色和部门关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `fun_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`role_code`,`fun_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_shop_menu` (
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家代码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `fun_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`shop_code`,`fun_code`) USING BTREE,
  KEY `idx_sys_shop_menu_fun` (`fun_code`,`status`) USING BTREE,
  KEY `idx_sys_shop_menu_query` (`shop_code`,`status`,`fun_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家可用菜单表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_shop_user` (
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家代码',
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `dept_id` bigint DEFAULT NULL COMMENT '商家部门ID（sys_dept.dept_id）',
  `job_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '店内工号',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '员工状态（0正常 1停用）',
  `is_owner` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否商家所有者（1是 0否）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`user_code`) USING BTREE,
  UNIQUE KEY `uk_sys_shop_user_job_no` (`shop_code`,`job_no`) USING BTREE,
  KEY `idx_sys_shop_user_dept` (`shop_code`,`dept_id`,`status`,`del_flag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家员工关系表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_shop_user_post` (
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家代码',
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `post_id` bigint NOT NULL COMMENT '商家岗位ID（sys_post.post_id）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`shop_code`,`user_code`,`post_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家员工岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_shop_user_role` (
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家代码',
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`shop_code`,`user_code`,`role_code`) USING BTREE,
  KEY `idx_sys_shop_user_role_role` (`shop_code`,`role_code`,`user_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商家员工角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_tenant_migration_issue` (
  `issue_id` bigint NOT NULL AUTO_INCREMENT COMMENT '问题ID',
  `issue_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题类型',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商家代码',
  `legacy_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '旧数据标识',
  `issue_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '未迁移原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`issue_id`) USING BTREE,
  UNIQUE KEY `uk_tenant_migration_issue` (`issue_type`,`shop_code`,`legacy_code`) USING BTREE,
  KEY `idx_tenant_migration_issue_shop` (`shop_code`,`issue_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='租户旧数据迁移问题清单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user` (
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '密码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_code`) USING BTREE,
  UNIQUE KEY `uk_sys_user_username` (`user_name`) USING BTREE,
  KEY `idx_sys_user_shop_username` (`user_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user_post` (
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_code`,`post_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`user_code`,`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `sdsshop` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `sdsshop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_f_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `accountcode` varchar(50) NOT NULL COMMENT '账户代码',
  `accountname` varchar(50) NOT NULL COMMENT '账户简称',
  `accountfullname` varchar(100) NOT NULL COMMENT '账户全称',
  `accountno` varchar(50) NOT NULL COMMENT '银行账号',
  `bankname` varchar(100) NOT NULL COMMENT '开户行名称',
  `currentbalance` decimal(18,2) DEFAULT '0.00' COMMENT '当前余额，只由资金流水完成时驱动',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT 'ISO 4217 币种代码；一个账户只能对应一种币种',
  `isactive` tinyint DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_shop_code` (`shop_code`,`accountcode`),
  KEY `idx_account_shop_active` (`shop_code`,`isactive`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_batchinfo` (
  `batchcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '批次代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `goodscode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品代码',
  `goodsname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '计量单位',
  `purchasecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '采购单号',
  `owner_ordercode` varchar(50) DEFAULT NULL COMMENT '专属销售订单号：定制件完工入库时回写，只作展示，判定一律用 owner_orderdetailcode',
  `owner_orderdetailcode` varchar(50) DEFAULT NULL COMMENT '专属销售订单明细代码：非空表示这批货是为该明细专门生产的，出库时只能发给它',
  `purchasedate` datetime NOT NULL COMMENT '采购日期',
  `purchaseprice` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '批次当前成本单价',
  `purchaseamount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '批次累计入库金额',
  `initialqty` decimal(18,4) NOT NULL COMMENT '本次到货数量',
  `remainingqty` decimal(18,4) DEFAULT '0.0000' COMMENT '剩余数量',
  `warecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所在仓库代码',
  `warename` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库名称（冗余）',
  `position` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '库位/货架位置',
  `expiredate` date DEFAULT NULL COMMENT '有效期',
  `productiondate` date DEFAULT NULL COMMENT '生产日期',
  `partnercode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '供应商代码',
  `partnername` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '供应商名称（冗余）',
  `qualityreport` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '质检报告路径',
  `closedflag` tinyint DEFAULT '0' COMMENT '是否关闭：0-开启，1-关闭',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `qualityflag` tinyint NOT NULL DEFAULT '1' COMMENT '良品状态：1-良品，0-不良品',
  `onhandflag` tinyint NOT NULL DEFAULT '1' COMMENT '在库状态：1-在库，0-不在库',
  PRIMARY KEY (`shop_code`,`batchcode`,`goodscode`,`warecode`,`qualityflag`,`onhandflag`) USING BTREE,
  KEY `idx_shop_batch_goods` (`shop_code`,`goodscode`) USING BTREE,
  KEY `idx_shop_batch_ware` (`shop_code`,`warecode`) USING BTREE,
  KEY `idx_shop_batch_purchase` (`shop_code`,`purchasecode`) USING BTREE,
  KEY `idx_shop_batch_state2` (`shop_code`,`qualityflag`,`onhandflag`) USING BTREE,
  KEY `idx_batch_owner_detail` (`shop_code`,`owner_orderdetailcode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='批次信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_buyer` (
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码（租户列）',
  `buyercode` varchar(50) NOT NULL COMMENT '客户编码，KH + Base10 流水，业务主键',
  `imageurl` varchar(500) DEFAULT NULL COMMENT '客户头像',
  `shopimage` varchar(500) DEFAULT NULL COMMENT '客户店铺图',
  `buyershop` varchar(50) DEFAULT NULL COMMENT '买家商家代码（二期商家客户用，一期恒 NULL）',
  `buyeruser` varchar(50) DEFAULT NULL COMMENT '买家用户代码，与 u_o_order_.buyeruser 同口径',
  `buyertype` char(1) NOT NULL DEFAULT '1' COMMENT '客户类型：1-个人 2-商家（一期只有 1）',
  `buyersimple` varchar(50) DEFAULT NULL COMMENT '客户简称，列表和订单快照优先取它',
  `buyerfull` varchar(200) DEFAULT NULL COMMENT '客户全称',
  `staffcode` varchar(50) DEFAULT NULL COMMENT '归属业务员代码',
  `staffname` varchar(50) DEFAULT NULL COMMENT '归属业务员名称（冗余）',
  `contactperson` varchar(50) DEFAULT NULL COMMENT '联系人',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号，商户内唯一；无值存 NULL，禁止空串',
  `telephone` varchar(50) DEFAULT NULL COMMENT '固定电话',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `starrating` tinyint NOT NULL DEFAULT '0' COMMENT '客户星级 0-5',
  `partnercode` varchar(50) DEFAULT NULL COMMENT '弱关联 sdsshop.u_m_partner.partnercode，转正式往来单位后回填，不建外键',
  `sysusercode` varchar(64) DEFAULT NULL COMMENT '二期客户账号用，绑定 sys_user.user_code；一期恒 NULL',
  `activeflag` tinyint NOT NULL DEFAULT '1' COMMENT '启用标记：1-启用 0-停用，与 u_m_partner.activeflag 对齐',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`buyercode`),
  UNIQUE KEY `uk_buyer_shop_mobile` (`shop_code`,`mobile`),
  KEY `idx_buyer_shop_staff` (`shop_code`,`staffcode`),
  KEY `idx_buyer_shop_simple` (`shop_code`,`buyersimple`),
  CONSTRAINT `ck_buyer_starrating` CHECK (((`starrating` >= 0) and (`starrating` <= 5)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售客户主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_buyer2` (
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码（租户列）',
  `buyer2code` varchar(50) NOT NULL COMMENT '二级客户编码，KHMX + Base10 流水，业务主键',
  `buyercode` varchar(50) NOT NULL COMMENT '所属主客户编码，关联 u_m_buyer.buyercode',
  `imageurl` varchar(500) DEFAULT NULL COMMENT '二级客户头像',
  `buyer2name` varchar(100) NOT NULL COMMENT '二级客户名称，订单快照取它',
  `buyer2user` varchar(50) DEFAULT NULL COMMENT '二级客户联系人',
  `mobile` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `activeflag` tinyint NOT NULL DEFAULT '1' COMMENT '启用标记：1-启用 0-停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`buyer2code`),
  KEY `idx_buyer2_shop_buyer` (`shop_code`,`buyercode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='二级客户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_doctypeconfig` (
  `doctype` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单据类型',
  `doctypename` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单据类型名称',
  `direction` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '方向：I-入库，O-出库，N-调拨',
  `needconfirmflag` tinyint DEFAULT '1' COMMENT '是否需要审核',
  `allownegativeflag` tinyint DEFAULT '0' COMMENT '是否允许负库存',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`doctype`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='单据类型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_goods_position` (
  `position_id` bigint NOT NULL AUTO_INCREMENT COMMENT '位置ID',
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码（租户隔离）',
  `goods_code` varchar(50) NOT NULL COMMENT '商品代码',
  `goods_name` varchar(100) DEFAULT NULL COMMENT '商品名称快照',
  `spec` varchar(100) DEFAULT NULL COMMENT '商品规格快照',
  `unit` varchar(20) DEFAULT NULL COMMENT '商品单位快照',
  `ware_code` varchar(50) NOT NULL COMMENT '仓库代码',
  `ware_name` varchar(100) DEFAULT NULL COMMENT '仓库名称快照',
  `position` text NOT NULL COMMENT '存放位置，库管员填写的大文本',
  `create_user` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间，列表按这个排',
  PRIMARY KEY (`position_id`),
  UNIQUE KEY `uk_goods_position_shop_goods` (`shop_code`,`goods_code`),
  KEY `idx_goods_position_shop_ware` (`shop_code`,`ware_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品位置信息';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_goodscategory` (
  `shop_code` varchar(50) NOT NULL DEFAULT '' COMMENT '商家编码',
  `categorycode` varchar(50) NOT NULL COMMENT '分类编码，系统生成 GCxxxx',
  `categoryname` varchar(100) NOT NULL COMMENT '分类名称，同商家下唯一',
  `parentcode` varchar(50) NOT NULL DEFAULT '0' COMMENT '上级分类编码，顶级为 0',
  `ordernum` int NOT NULL DEFAULT '0' COMMENT '显示排序',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态：0 正常 1 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(64) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(64) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`categorycode`) USING BTREE,
  UNIQUE KEY `uk_shop_category_name` (`shop_code`,`categoryname`) USING BTREE,
  KEY `idx_shop_parent` (`shop_code`,`parentcode`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='商品分类主数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_partner` (
  `partnercode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单位代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `partnername` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单位名称',
  `partnertype` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型：supplier/customer/department',
  `address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '地址',
  `contactperson` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '联系人',
  `mobile` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '手机号',
  `telephone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '电话',
  `activeflag` tinyint DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`partnercode`) USING BTREE,
  KEY `idx_shop_partner_name` (`shop_code`,`partnername`) USING BTREE,
  KEY `idx_shop_partner_type` (`shop_code`,`partnertype`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='往来单位表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_serial` (
  `serialcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '序列号',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `goodscode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品代码',
  `goodsname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称（冗余）',
  `spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规格型号（冗余）',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '计量单位（冗余）',
  `batchcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '批次代码',
  `costprice` decimal(18,6) DEFAULT NULL COMMENT '序列号个别计价成本',
  `warecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库代码',
  `warename` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库名称（冗余）',
  `activeflag` tinyint DEFAULT '1' COMMENT '是否在库：1-在库，0-已出库',
  `indoccode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入库单据代码',
  `indetailcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入库明细代码',
  `intime` datetime NOT NULL COMMENT '入库时间',
  `outdoccode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '出库单据代码',
  `outdetailcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '出库明细代码',
  `outtime` datetime DEFAULT NULL COMMENT '出库时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `qualityflag` tinyint NOT NULL DEFAULT '1' COMMENT '良品状态：1-良品，0-不良品',
  `onhandflag` tinyint NOT NULL DEFAULT '1' COMMENT '在库状态：1-在库，0-不在库',
  PRIMARY KEY (`shop_code`,`serialcode`) USING BTREE,
  KEY `idx_shop_serial_goods` (`shop_code`,`goodscode`) USING BTREE,
  KEY `idx_shop_serial_batch` (`shop_code`,`batchcode`) USING BTREE,
  KEY `idx_shop_serial_ware` (`shop_code`,`warecode`) USING BTREE,
  KEY `idx_shop_serial_indoc` (`shop_code`,`indoccode`) USING BTREE,
  KEY `idx_shop_serial_outdoc` (`shop_code`,`outdoccode`) USING BTREE,
  KEY `idx_shop_serial_state2` (`shop_code`,`qualityflag`,`onhandflag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='序列号明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_stock` (
  `goodscode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `goodsname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '计量单位',
  `warecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库代码',
  `warename` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库名称（冗余）',
  `physicalqty` decimal(18,4) DEFAULT '0.0000' COMMENT '物理库存数量',
  `lockedqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '锁定数量（待审核出库单占用数量）',
  `reservedqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '订单预占数量（明细见 u_o_reservation，不同于待审核出库单占用的 lockedqty）',
  `totalcostamount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '库存总成本金额',
  `avgcostprice` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '移动加权平均成本价',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `lastupdated` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `qualityflag` tinyint NOT NULL DEFAULT '1' COMMENT '良品状态：1-良品，0-不良品',
  `onhandflag` tinyint NOT NULL DEFAULT '1' COMMENT '在库状态：1-在库，0-不在库',
  PRIMARY KEY (`shop_code`,`goodscode`,`warecode`,`qualityflag`,`onhandflag`) USING BTREE,
  KEY `idx_shop_stock_goods` (`shop_code`,`goodscode`) USING BTREE,
  KEY `idx_shop_stock_ware` (`shop_code`,`warecode`) USING BTREE,
  KEY `idx_shop_stock_state2` (`shop_code`,`qualityflag`,`onhandflag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='实时库存汇总表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_waregoods` (
  `goodscode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `goodsname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商品分类',
  `spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '个' COMMENT '计量单位',
  `goodstype` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'raw' COMMENT '商品类型：raw-原材料，semifinished-半成品，finished-成品',
  `batchstrategy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'none' COMMENT '批次策略：none/batch/serial',
  `requireexpireflag` tinyint DEFAULT '0' COMMENT '是否必须记录有效期',
  `costcalculation` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'moving_avg' COMMENT '成本计算方法：moving_avg/fifo/individual',
  `standardprice` decimal(18,4) DEFAULT '0.0000' COMMENT '标准单价/参考价',
  `minstockqty` decimal(18,4) DEFAULT '0.0000' COMMENT '最低库存预警量',
  `maxstockqty` decimal(18,4) DEFAULT '0.0000' COMMENT '最高库存预警量',
  `imgurl` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商品主图URL',
  `thumburl` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '主图缩略图URL',
  `mainimgs` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '其他附加主图（逗号分隔）',
  `barcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '条形码',
  `confirmitems` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '入库检查事项',
  `activeflag` tinyint DEFAULT '1' COMMENT '是否启用',
  `usedflag` tinyint NOT NULL DEFAULT '0' COMMENT '是否已被单据使用：0-未使用 1-已建过单据或已有库存',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`goodscode`) USING BTREE,
  KEY `idx_shop_goods_name` (`shop_code`,`goodsname`) USING BTREE,
  KEY `idx_shop_category` (`shop_code`,`category`) USING BTREE,
  KEY `idx_shop_goods_type` (`shop_code`,`goodstype`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='仓储商品表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_warehouse` (
  `warecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库代码',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '商家编码',
  `warename` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库名称',
  `parentcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '上级仓库代码',
  `waretype` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '实体仓' COMMENT '仓库类型：实体仓/虚拟仓',
  `address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '仓库地址',
  `contactperson` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '联系人',
  `contactphone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '联系电话',
  `activeflag` tinyint DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`shop_code`,`warecode`) USING BTREE,
  KEY `idx_shop_ware_name` (`shop_code`,`warename`) USING BTREE,
  KEY `idx_shop_ware_type` (`shop_code`,`waretype`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='仓库主数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_warning_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '预警记录ID',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家编码',
  `goods_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编码',
  `goods_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称快照',
  `spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规格快照',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单位快照',
  `ware_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库编码',
  `ware_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库名称快照',
  `warning_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'LOW_STOCK' COMMENT '预警类型',
  `available_qty` decimal(18,4) NOT NULL COMMENT '可用库存',
  `threshold_qty` decimal(18,4) NOT NULL COMMENT '预警阈值',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、ACKNOWLEDGED、RECOVERED、CLOSED',
  `trigger_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次触发时间',
  `last_checked_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后检查时间',
  `acknowledged_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '确认人',
  `acknowledged_time` datetime DEFAULT NULL COMMENT '确认时间',
  `handle_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '处理备注',
  `recovered_time` datetime DEFAULT NULL COMMENT '恢复时间',
  `closed_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '关闭人',
  `closed_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `active_key` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '未结束预警唯一键',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`record_id`) USING BTREE,
  UNIQUE KEY `uk_warning_record_active_key` (`active_key`) USING BTREE,
  KEY `idx_warning_record_shop_status` (`shop_code`,`status`,`last_checked_time`) USING BTREE,
  KEY `idx_warning_record_shop_goods_ware` (`shop_code`,`goods_code`,`ware_code`) USING BTREE,
  KEY `idx_warning_record_shop_trigger` (`shop_code`,`trigger_time`) USING BTREE,
  CONSTRAINT `chk_warning_record_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ACKNOWLEDGED',_utf8mb4'RECOVERED',_utf8mb4'CLOSED')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='库存低库存预警记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_m_warning_rule` (
  `rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家编码',
  `goods_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编码',
  `ware_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '仓库编码',
  `threshold_qty` decimal(18,4) NOT NULL COMMENT '低库存阈值',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用，1启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE KEY `uk_warning_rule_shop_goods_ware` (`shop_code`,`goods_code`,`ware_code`) USING BTREE,
  KEY `idx_warning_rule_shop_enabled` (`shop_code`,`enabled`) USING BTREE,
  CONSTRAINT `chk_warning_rule_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `chk_warning_rule_threshold` CHECK ((`threshold_qty` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='库存低库存预警规则';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `reservecode` varchar(50) NOT NULL COMMENT '预占行代码（YZ + 小时级Base10编码）',
  `ordercode` varchar(50) NOT NULL COMMENT '订单代码',
  `orderdetailcode` varchar(50) NOT NULL COMMENT '订单明细代码',
  `goodscode` varchar(50) NOT NULL COMMENT '商品代码',
  `goodsname` varchar(100) NOT NULL COMMENT '商品名称（冗余）',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号（冗余）',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位（冗余）',
  `warecode` varchar(50) NOT NULL COMMENT '预占仓库代码',
  `warename` varchar(100) NOT NULL COMMENT '预占仓库名称（冗余）',
  `qualityflag` tinyint NOT NULL DEFAULT '1' COMMENT '良品状态：1-良品，0-不良品',
  `onhandflag` tinyint NOT NULL DEFAULT '1' COMMENT '在库状态：1-在库，0-不在库',
  `reservedqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '当前占用数量（计入 u_m_stock.reservedqty）',
  `releasedqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已核销数量（已转成出库单 lockedqty）',
  `status` varchar(20) NOT NULL DEFAULT '有效' COMMENT '有效/已核销/已取消',
  `shipcode` varchar(50) DEFAULT NULL COMMENT '核销去向：发货批次代码',
  `doccode` varchar(50) DEFAULT NULL COMMENT '核销去向：库存出库单代码',
  `createuser` varchar(50) NOT NULL COMMENT '创建人',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_shop_code` (`shop_code`,`reservecode`),
  KEY `idx_reservation_shop_order` (`shop_code`,`ordercode`),
  KEY `idx_reservation_shop_orderdetail` (`shop_code`,`orderdetailcode`),
  KEY `idx_reservation_shop_stockkey` (`shop_code`,`goodscode`,`warecode`,`qualityflag`,`onhandflag`,`status`),
  KEY `idx_reservation_shop_status` (`shop_code`,`status`),
  KEY `idx_reservation_shop_ship` (`shop_code`,`shipcode`),
  CONSTRAINT `ck_reservation_closed_zero` CHECK (((`status` = _utf8mb4'有效') or (`reservedqty` = 0))),
  CONSTRAINT `ck_reservation_releasedqty` CHECK ((`releasedqty` >= 0)),
  CONSTRAINT `ck_reservation_reservedqty` CHECK ((`reservedqty` >= 0)),
  CONSTRAINT `ck_reservation_status` CHECK ((`status` in (_utf8mb4'有效',_utf8mb4'已核销',_utf8mb4'已取消')))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单预占明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_bom` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `bomcode` varchar(50) NOT NULL COMMENT '配方代码（SCPF + 小时级Base10编码）',
  `productcode` varchar(50) NOT NULL COMMENT '成品商品代码',
  `productname` varchar(100) NOT NULL COMMENT '成品商品名称',
  `spec` varchar(100) DEFAULT NULL COMMENT '成品规格（取自商品档案，冗余展示）',
  `unit` varchar(20) DEFAULT NULL COMMENT '成品单位（取自商品档案，冗余展示）',
  `status` varchar(10) NOT NULL DEFAULT '启用' COMMENT '启用/停用；停用的配方不再自动带入工单',
  `createuser` varchar(50) NOT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_bom` (`shop_code`,`bomcode`),
  UNIQUE KEY `uk_shop_bom_product` (`shop_code`,`productcode`),
  KEY `idx_bom_shop_status` (`shop_code`,`status`),
  CONSTRAINT `ck_bom_status` CHECK ((`status` in (_utf8mb4'启用',_utf8mb4'停用')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品配方（BOM）主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_bomdetail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `bomdetailcode` varchar(50) NOT NULL COMMENT '配方明细代码（SCPFMX + 小时级Base10编码）',
  `bomcode` varchar(50) NOT NULL COMMENT '配方代码',
  `lineno` int NOT NULL DEFAULT '0' COMMENT '行号',
  `goodscode` varchar(50) NOT NULL COMMENT '物料商品代码（原材料或半成品）',
  `goodsname` varchar(100) NOT NULL COMMENT '物料名称',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `qtyperunit` decimal(18,4) NOT NULL COMMENT '单耗（每产出 1 个成品的用量）',
  `remark` varchar(500) DEFAULT NULL COMMENT '行备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_bomdetail` (`shop_code`,`bomdetailcode`),
  UNIQUE KEY `uk_shop_bom_goods` (`shop_code`,`bomcode`,`goodscode`),
  UNIQUE KEY `uk_shop_bom_line` (`shop_code`,`bomcode`,`lineno`),
  KEY `idx_bomdetail_bom` (`shop_code`,`bomcode`),
  CONSTRAINT `ck_bomdetail_qty_per_unit` CHECK ((`qtyperunit` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品配方（BOM）物料明细';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_production` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `productioncode` varchar(50) NOT NULL COMMENT '工单号，前缀 SC',
  `sourcetype` varchar(20) NOT NULL COMMENT '工单来源：order-销售订单 stock-备库',
  `sourceordercodes` varchar(500) DEFAULT NULL COMMENT '来源销售订单号（逗号分隔，由 orderallocation 去重推导，不接受前端传值）',
  `orderallocation` text COMMENT '产出分配明细（JSON数组，元素含产出行号/订单号/月表后缀/订单明细代码/数量）',
  `productcode` varchar(50) NOT NULL COMMENT '主产出商品编码',
  `productname` varchar(100) DEFAULT NULL COMMENT '主产出商品名称',
  `planyqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '计划产出数量，取产出明细合计',
  `actualyqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '实际产出数量',
  `plandate` date DEFAULT NULL COMMENT '计划生产日期，只到天',
  `actualdate` datetime DEFAULT NULL COMMENT '实际完工时间',
  `status` varchar(20) NOT NULL DEFAULT '待生产' COMMENT '待生产/生产中/已完工/已关闭',
  `materialcost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '物料成本，完工时按实际领料回填',
  `processingcost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '加工费',
  `freightcost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '运费',
  `othercost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '其他费用',
  `totalcost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '总成本',
  `unitcost` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '单位成本，与库存 costprice 同精度',
  `createuser` varchar(50) NOT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_shop_code` (`shop_code`,`productioncode`),
  KEY `idx_production_status` (`shop_code`,`status`),
  KEY `idx_production_product` (`shop_code`,`productcode`),
  KEY `idx_production_plandate` (`shop_code`,`plandate`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产工单主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_productiondetail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `detailcode` varchar(50) NOT NULL COMMENT '明细代码',
  `productioncode` varchar(50) NOT NULL COMMENT '工单号',
  `lineno` int NOT NULL COMMENT '行号',
  `productcode` varchar(50) NOT NULL COMMENT '产出商品编码',
  `productname` varchar(100) DEFAULT NULL COMMENT '产出商品名称',
  `planyqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '计划产出数量',
  `actualyqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '实际产出数量',
  `materialcost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '本行物料消耗成本（按本行配方实算）',
  `unitcost` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '单位成本',
  `totalcost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '该行总成本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_productiondetail_shop_code` (`shop_code`,`detailcode`),
  UNIQUE KEY `uk_productiondetail_line` (`shop_code`,`productioncode`,`lineno`),
  KEY `idx_productiondetail_order` (`shop_code`,`productioncode`),
  KEY `idx_productiondetail_product` (`shop_code`,`productcode`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产工单产出明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_productionmaterial` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `materialcode` varchar(50) NOT NULL COMMENT '物料行代码',
  `productioncode` varchar(50) NOT NULL COMMENT '工单号',
  `detailcode` varchar(50) NOT NULL COMMENT '所属产出明细代码（u_p_productiondetail.detailcode）',
  `lineno` int NOT NULL COMMENT '行号',
  `goodscode` varchar(50) NOT NULL COMMENT '物料商品编码',
  `goodsname` varchar(100) DEFAULT NULL COMMENT '物料商品名称',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `qtyperunit` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '单耗（该产出每1单位的用量）',
  `totalqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '计划总用量 = qtyperunit × 所属产出行的计划数量',
  `pickedqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已领数量',
  `actualqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '实际领用量，退料后回冲',
  `returnedqty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已退回数量',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_productionmaterial_shop_code` (`shop_code`,`materialcode`),
  UNIQUE KEY `uk_productionmaterial_line` (`shop_code`,`productioncode`,`lineno`),
  KEY `idx_productionmaterial_order` (`shop_code`,`productioncode`),
  KEY `idx_productionmaterial_goods` (`shop_code`,`goodscode`),
  KEY `idx_production_material_detail` (`shop_code`,`productioncode`,`detailcode`),
  CONSTRAINT `ck_production_material_consumed_qty` CHECK (((`returnedqty` >= 0) and ((`actualqty` + `returnedqty`) <= `pickedqty`))),
  CONSTRAINT `ck_production_material_picked_qty` CHECK (((`pickedqty` >= 0) and (`pickedqty` <= `totalqty`)))
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产工单物料清单表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_purchase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `purchasecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `partnercode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `partnername` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `purchasedate` date NOT NULL,
  `expectdate` date DEFAULT NULL,
  `totalamount` decimal(18,2) DEFAULT '0.00',
  `paidamount` decimal(18,2) DEFAULT '0.00',
  `paystatus` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `freighttype` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'prepaid',
  `estimatedfreight` decimal(18,2) DEFAULT '0.00',
  `actualfreight` decimal(18,2) DEFAULT '0.00',
  `freightpaid` decimal(18,2) DEFAULT '0.00',
  `freightpaystatus` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `logisticsno` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `carrier` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'draft',
  `rejectreason` varchar(500) DEFAULT NULL COMMENT '驳回原因',
  `createuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP,
  `updateuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `confirmuser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `confirmtime` datetime DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_shop_purchase` (`shop_code`,`purchasecode`) USING BTREE,
  KEY `idx_shop_status` (`shop_code`,`status`) USING BTREE,
  KEY `idx_shop_partner` (`shop_code`,`partnercode`) USING BTREE,
  KEY `idx_shop_date` (`shop_code`,`purchasedate`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_p_purchasedetail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `detailcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `purchasecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `lineno` int NOT NULL,
  `goodscode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `goodsname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `spec` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `qty` decimal(18,4) NOT NULL,
  `price` decimal(18,4) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `taxrate` decimal(8,4) DEFAULT NULL,
  `taxamount` decimal(18,2) DEFAULT '0.00',
  `warehousecode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `warehousename` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `receivedqty` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `remainingqty` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `batchcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `serialnumbers` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_shop_purchase_line` (`shop_code`,`purchasecode`,`lineno`) USING BTREE,
  UNIQUE KEY `uk_shop_purchase_detail` (`shop_code`,`detailcode`) USING BTREE,
  UNIQUE KEY `uk_shop_purchase_goods` (`shop_code`,`purchasecode`,`goodscode`) USING BTREE,
  KEY `idx_shop_goods` (`shop_code`,`goodscode`) USING BTREE,
  KEY `idx_purchase_code` (`shop_code`,`purchasecode`) USING BTREE,
  CONSTRAINT `ck_purchase_item_received_qty` CHECK ((`receivedqty` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
--
-- 独立站【登记表】。
--
-- ⚠ 这张表以前在独立站那个库里，现在在这里（sdsshop）。
--
-- 依据 docs/独立站与ERP的边界.md §5.2：那个库整个搬到独立站服务器上去了，
-- ERP 这边不再有那个库。u_s_site 留下来进 sdsshop——它是按 shop_code 分租户的
-- 商家资产，和 u_m_buyer / u_m_waregoods 是同一类东西。
--
-- 更实际的理由：ERP 侧要是留一个同名的空库，下一个人看到它还在，
-- 会以为独立站的数据仍然在这边。
--
-- 它现在只回答「我们有哪些站、在哪、怎么进」，不含站里的任何业务数据（§7）。
-- 站点服务器上那份建库脚本见 sql/station/01_station_schema.sql。
--
CREATE TABLE IF NOT EXISTS `u_s_site` (
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码（租户列）',
  `sitecode` varchar(50) NOT NULL COMMENT '站点编码，DLZ + Base10 流水，业务主键。同一个值也存在站点库的 u_s_siteinfo 里，跳转令牌靠它对齐',
  `sitename` varchar(100) NOT NULL COMMENT '站点名称，如「英文主站」',
  `domain` varchar(200) DEFAULT NULL COMMENT '主域名，如 example.com；商户内唯一，无值存 NULL 禁止空串。下面四个子域默认由它派生',
  `datadomain` varchar(200) DEFAULT NULL COMMENT '后端服务域名。留空则取 data.<主域名>',
  `filedomain` varchar(200) DEFAULT NULL COMMENT '文件域名。留空则取 file.<主域名>',
  `consoledomain` varchar(200) DEFAULT NULL COMMENT '管理后台域名。留空则取 console.<主域名>。⚠「进入管理」的跳转地址由它拼',
  `buyerdomain` varchar(200) DEFAULT NULL COMMENT '买家中心域名（登录后的我的订单、账号资料、地址簿）。留空则取 buyer.<主域名>。⚠ 这不是前台——前台（商城）在主域上，站点登记列表那个链接由主域名拼，见 StationSite#getFrontUrl',
  `serverip` varchar(100) DEFAULT NULL COMMENT '站点服务器公网 IP，逗号分隔。⚠ 运维找机器用，ERP 不会去连它',
  `consolesecret` varchar(500) DEFAULT NULL COMMENT '这个站的跳转令牌签名密钥（AES-GCM 密文，主密钥 OSS_CONFIG_SECRET）。一站一个，泄露只影响本站。和站点库 u_s_siteinfo.consolesecret 是同一个值',
  `ownername` varchar(50) DEFAULT NULL COMMENT '负责人',
  `expiredate` date DEFAULT NULL COMMENT '域名/服务器到期日，用于到期提醒；留空表示不提醒',
  `status` varchar(20) NOT NULL DEFAULT '启用' COMMENT '站点状态：启用/停用。⚠ 管的是「ERP 还发不发跳转令牌」；站点前台开不开是那边 u_s_siteinfo.status 的事，两个决定不是一个值的副本',
  `lastaccesstime` datetime DEFAULT NULL COMMENT '最后一次【点了进入管理】的时间。⚠ 不是「进成功了」——新协议下验签在站点侧、不回调 ERP，这边只知道有人点了按钮。别拿它当活跃度指标',
  `sortnum` int NOT NULL DEFAULT '0' COMMENT '列表排序，越小越靠前',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`shop_code`,`sitecode`),
  UNIQUE KEY `uk_site_shop_domain` (`shop_code`,`domain`),
  KEY `idx_site_shop_status` (`shop_code`,`status`),
  KEY `idx_site_shop_expire` (`shop_code`,`expiredate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='独立站登记表（只登记有哪些站、在哪、怎么进）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `sdsorder` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `sdsorder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_f_accountcategory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',
  `categorycode` varchar(30) NOT NULL COMMENT '分类代码',
  `categoryname` varchar(50) NOT NULL COMMENT '分类名称',
  `type` varchar(10) NOT NULL COMMENT '收入/支出',
  `biztype` varchar(30) DEFAULT NULL COMMENT '关联业务类型：采购/销售/费用/工资/其他',
  `isactive` tinyint DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `createuser` varchar(50) DEFAULT NULL COMMENT '创建人',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateuser` varchar(50) DEFAULT NULL COMMENT '更新人',
  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_shop_code` (`shop_code`,`categorycode`),
  KEY `idx_category_shop_type` (`shop_code`,`type`,`isactive`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收支分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码（租户隔离）',
  `package_code` varchar(50) NOT NULL COMMENT '套餐代码，商家内唯一，建后不可改',
  `package_name` varchar(100) NOT NULL COMMENT '套餐名称',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用；停用后下单选不到，已下订单不受影响',
  `remark` varchar(200) DEFAULT NULL,
  `create_user` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` varchar(50) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_shop_code` (`shop_code`,`package_code`),
  KEY `idx_package_shop_active` (`shop_code`,`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售套餐主数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_package_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码（租户隔离）',
  `package_code` varchar(50) NOT NULL COMMENT '所属套餐代码',
  `goods_code` varchar(50) NOT NULL COMMENT '商品编码',
  `goods_name` varchar(100) DEFAULT NULL COMMENT '商品名称快照（下单时按 goods_code 重新取当前档案）',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格快照',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位快照',
  `qty` decimal(18,4) NOT NULL COMMENT '本套餐里这个商品的数量',
  `remark` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_item_goods` (`shop_code`,`package_code`,`goods_code`),
  CONSTRAINT `fk_package_item_package` FOREIGN KEY (`shop_code`, `package_code`) REFERENCES `u_o_package` (`shop_code`, `package_code`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售套餐包含的商品';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_platform` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码（租户隔离）',
  `platform_code` varchar(30) NOT NULL COMMENT '销售平台代码',
  `platform_name` varchar(50) NOT NULL COMMENT '销售平台名称',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `remark` varchar(200) DEFAULT NULL,
  `create_user` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` varchar(50) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_shop_code` (`shop_code`,`platform_code`),
  KEY `idx_platform_shop_active` (`shop_code`,`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售平台主数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_platform_store` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码（租户隔离）',
  `platform_code` varchar(30) NOT NULL COMMENT '所属销售平台代码',
  `store_code` varchar(50) NOT NULL COMMENT '平台店铺代码',
  `store_name` varchar(100) NOT NULL COMMENT '平台店铺名称',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `remark` varchar(200) DEFAULT NULL,
  `create_user` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` varchar(50) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_store_shop_platform_code` (`shop_code`,`platform_code`,`store_code`),
  UNIQUE KEY `uk_platform_store_shop_platform_name` (`shop_code`,`platform_code`,`store_name`),
  KEY `idx_platform_store_platform` (`shop_code`,`platform_code`,`is_active`),
  CONSTRAINT `fk_platform_store_platform` FOREIGN KEY (`shop_code`, `platform_code`) REFERENCES `u_o_platform` (`shop_code`, `platform_code`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售平台下的店铺主数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_sales_reship` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL COMMENT 'ERP商家编码',
  `reshipcode` varchar(50) NOT NULL COMMENT '销售补发单号',
  `originalordercode` varchar(50) NOT NULL COMMENT '原销售订单号',
  `originalordersuffix` char(6) NOT NULL COMMENT '原订单月表后缀yyyyMM',
  `platorderid` varchar(100) DEFAULT NULL COMMENT '原平台订单号快照',
  `platcode` varchar(30) DEFAULT NULL,
  `platname` varchar(50) DEFAULT NULL,
  `shopstorecode` varchar(50) DEFAULT NULL,
  `shopstorename` varchar(100) DEFAULT NULL,
  `receiveperson` varchar(50) NOT NULL,
  `receiveaddress` varchar(200) NOT NULL,
  `receivetelephone` varchar(100) NOT NULL COMMENT '联系电话（可以是虚拟号、固话，或逗号/分号分隔的多个号码）',
  `receivezipcode` varchar(20) DEFAULT NULL,
  `warehousecode` varchar(50) NOT NULL,
  `warehousename` varchar(100) NOT NULL,
  `carrier` varchar(50) DEFAULT NULL,
  `logisticsno` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT '待发货' COMMENT '待发货/已发货',
  `outbounddoccode` varchar(50) DEFAULT NULL COMMENT '一次性发货生成的销售出库单号',
  `createuser` varchar(50) NOT NULL,
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `shipuser` varchar(50) DEFAULT NULL,
  `shiptime` datetime DEFAULT NULL,
  `updatetime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_reship_shop_code` (`shop_code`,`reshipcode`),
  UNIQUE KEY `uk_sales_reship_original_order` (`shop_code`,`originalordercode`),
  KEY `idx_sales_reship_status` (`shop_code`,`status`),
  KEY `idx_sales_reship_create_time` (`shop_code`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售补发单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `u_o_sales_reship_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_code` varchar(50) NOT NULL,
  `detailcode` varchar(50) NOT NULL,
  `reshipcode` varchar(50) NOT NULL,
  `originalorderdetailcode` varchar(50) NOT NULL COMMENT '原销售订单明细号',
  `lineno` int NOT NULL,
  `goodscode` varchar(50) NOT NULL,
  `goodsname` varchar(100) NOT NULL,
  `spec` varchar(100) DEFAULT NULL,
  `unit` varchar(20) DEFAULT NULL,
  `qty` decimal(18,4) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_reship_detail_code` (`shop_code`,`detailcode`),
  UNIQUE KEY `uk_sales_reship_detail_order_line` (`shop_code`,`reshipcode`,`originalorderdetailcode`),
  KEY `idx_sales_reship_detail_reship` (`shop_code`,`reshipcode`),
  CONSTRAINT `ck_sales_reship_detail_qty` CHECK ((`qty` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售补发明细';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;

/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
USE `sdssys`;
SET FOREIGN_KEY_CHECKS = 0;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('系统管理',1,'system',NULL,'','',1,0,'M','0','0','','system','admin','2026-07-21 11:18:21','',NULL,'系统管理目录','fn1','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户管理',1,'user','system/user/index','','',1,0,'C','0','0','system:user:list','user','admin','2026-07-21 11:18:21','',NULL,'用户管理菜单','fn100','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户查询',1,'','','','',1,0,'F','0','0','system:user:query','#','admin','2026-07-21 11:18:21','',NULL,'','fn1000','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户新增',2,'','','','',1,0,'F','0','0','system:user:add','#','admin','2026-07-21 11:18:21','',NULL,'','fn1001','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户修改',3,'','','','',1,0,'F','0','0','system:user:edit','#','admin','2026-07-21 11:18:21','',NULL,'','fn1002','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户删除',4,'','','','',1,0,'F','0','0','system:user:remove','#','admin','2026-07-21 11:18:21','',NULL,'','fn1003','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户导出',5,'','','','',1,0,'F','0','0','system:user:export','#','admin','2026-07-21 11:18:21','',NULL,'','fn1004','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('用户导入',6,'','','','',1,0,'F','0','0','system:user:import','#','admin','2026-07-21 11:18:21','',NULL,'','fn1005','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('重置密码',7,'','','','',1,0,'F','0','0','system:user:resetPwd','#','admin','2026-07-21 11:18:21','',NULL,'','fn1006','fn100');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色查询',1,'','','','',1,0,'F','0','0','system:role:query','#','admin','2026-07-21 11:18:21','',NULL,'','fn1007','fn101');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色新增',2,'','','','',1,0,'F','0','0','system:role:add','#','admin','2026-07-21 11:18:21','',NULL,'','fn1008','fn101');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色修改',3,'','','','',1,0,'F','0','0','system:role:edit','#','admin','2026-07-21 11:18:21','',NULL,'','fn1009','fn101');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色管理',2,'role','system/role/index','','',1,0,'C','0','0','system:role:list','peoples','admin','2026-07-21 11:18:21','',NULL,'角色管理菜单','fn101','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色删除',4,'','','','',1,0,'F','0','0','system:role:remove','#','admin','2026-07-21 11:18:21','',NULL,'','fn1010','fn101');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色导出',5,'','','','',1,0,'F','0','0','system:role:export','#','admin','2026-07-21 11:18:21','',NULL,'','fn1011','fn101');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('菜单查询',1,'','','','',1,0,'F','0','0','system:menu:query','#','admin','2026-07-21 11:18:21','',NULL,'','fn1012','fn102');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('菜单新增',2,'','','','',1,0,'F','0','0','system:menu:add','#','admin','2026-07-21 11:18:21','',NULL,'','fn1013','fn102');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('菜单修改',3,'','','','',1,0,'F','0','0','system:menu:edit','#','admin','2026-07-21 11:18:21','',NULL,'','fn1014','fn102');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('菜单删除',4,'','','','',1,0,'F','0','0','system:menu:remove','#','admin','2026-07-21 11:18:21','',NULL,'','fn1015','fn102');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门查询',1,'','','','',1,0,'F','0','0','system:dept:query','#','admin','2026-07-21 11:18:21','',NULL,'','fn1016','fn103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门新增',2,'','','','',1,0,'F','0','0','system:dept:add','#','admin','2026-07-21 11:18:21','',NULL,'','fn1017','fn103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门修改',3,'','','','',1,0,'F','0','0','system:dept:edit','#','admin','2026-07-21 11:18:21','',NULL,'','fn1018','fn103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门删除',4,'','','','',1,0,'F','0','0','system:dept:remove','#','admin','2026-07-21 11:18:21','',NULL,'','fn1019','fn103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('菜单管理',3,'menu','system/menu/index','','',1,0,'C','0','0','system:menu:list','tree-table','admin','2026-07-21 11:18:21','',NULL,'菜单管理菜单','fn102','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('岗位查询',1,'','','','',1,0,'F','0','0','system:post:query','#','admin','2026-07-21 11:18:21','',NULL,'','fn1020','fn104');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('岗位新增',2,'','','','',1,0,'F','0','0','system:post:add','#','admin','2026-07-21 11:18:21','',NULL,'','fn1021','fn104');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('岗位修改',3,'','','','',1,0,'F','0','0','system:post:edit','#','admin','2026-07-21 11:18:21','',NULL,'','fn1022','fn104');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('岗位删除',4,'','','','',1,0,'F','0','0','system:post:remove','#','admin','2026-07-21 11:18:21','',NULL,'','fn1023','fn104');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('岗位导出',5,'','','','',1,0,'F','0','0','system:post:export','#','admin','2026-07-21 11:18:21','',NULL,'','fn1024','fn104');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('字典查询',1,'#','','','',1,0,'F','0','0','system:dict:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1025','fn105');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('字典新增',2,'#','','','',1,0,'F','0','0','system:dict:add','#','admin','2026-07-21 11:18:22','',NULL,'','fn1026','fn105');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('字典修改',3,'#','','','',1,0,'F','0','0','system:dict:edit','#','admin','2026-07-21 11:18:22','',NULL,'','fn1027','fn105');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('字典删除',4,'#','','','',1,0,'F','0','0','system:dict:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1028','fn105');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('字典导出',5,'#','','','',1,0,'F','0','0','system:dict:export','#','admin','2026-07-21 11:18:22','',NULL,'','fn1029','fn105');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门管理',4,'dept','system/dept/index','','',1,0,'C','0','0','system:dept:list','tree','admin','2026-07-21 11:18:21','',NULL,'部门管理菜单','fn103','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('参数查询',1,'#','','','',1,0,'F','0','0','system:config:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1030','fn106');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('参数新增',2,'#','','','',1,0,'F','0','0','system:config:add','#','admin','2026-07-21 11:18:22','',NULL,'','fn1031','fn106');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('参数修改',3,'#','','','',1,0,'F','0','0','system:config:edit','#','admin','2026-07-21 11:18:22','',NULL,'','fn1032','fn106');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('参数删除',4,'#','','','',1,0,'F','0','0','system:config:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1033','fn106');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('参数导出',5,'#','','','',1,0,'F','0','0','system:config:export','#','admin','2026-07-21 11:18:22','',NULL,'','fn1034','fn106');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('公告查询',1,'#','','','',1,0,'F','0','0','system:notice:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1035','fn107');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('公告新增',2,'#','','','',1,0,'F','0','0','system:notice:add','#','admin','2026-07-21 11:18:22','',NULL,'','fn1036','fn107');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('公告修改',3,'#','','','',1,0,'F','0','0','system:notice:edit','#','admin','2026-07-21 11:18:22','',NULL,'','fn1037','fn107');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('公告删除',4,'#','','','',1,0,'F','0','0','system:notice:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1038','fn107');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('操作查询',1,'#','','','',1,0,'F','0','0','monitor:operlog:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1039','fn500');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('岗位管理',5,'post','system/post/index','','',1,0,'C','0','0','system:post:list','post','admin','2026-07-21 11:18:21','',NULL,'岗位管理菜单','fn104','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('操作删除',2,'#','','','',1,0,'F','0','0','monitor:operlog:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1040','fn500');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('日志导出',3,'#','','','',1,0,'F','0','0','monitor:operlog:export','#','admin','2026-07-21 11:18:22','',NULL,'','fn1041','fn500');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('登录查询',1,'#','','','',1,0,'F','0','0','monitor:logininfor:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1042','fn501');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('登录删除',2,'#','','','',1,0,'F','0','0','monitor:logininfor:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1043','fn501');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('日志导出',3,'#','','','',1,0,'F','0','0','monitor:logininfor:export','#','admin','2026-07-21 11:18:22','',NULL,'','fn1044','fn501');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('账户解锁',4,'#','','','',1,0,'F','0','0','monitor:logininfor:unlock','#','admin','2026-07-21 11:18:22','',NULL,'','fn1045','fn501');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('在线查询',1,'#','','','',1,0,'F','0','0','monitor:online:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1046','fn109');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('批量强退',2,'#','','','',1,0,'F','0','0','monitor:online:batchLogout','#','admin','2026-07-21 11:18:22','',NULL,'','fn1047','fn109');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('单条强退',3,'#','','','',1,0,'F','0','0','monitor:online:forceLogout','#','admin','2026-07-21 11:18:22','',NULL,'','fn1048','fn109');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('任务查询',1,'#','','','',1,0,'F','0','0','monitor:job:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1049','fn110');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('字典管理',6,'dict','system/dict/index','','',1,0,'C','0','0','system:dict:list','dict','admin','2026-07-21 11:18:21','',NULL,'字典管理菜单','fn105','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('任务新增',2,'#','','','',1,0,'F','0','0','monitor:job:add','#','admin','2026-07-21 11:18:22','',NULL,'','fn1050','fn110');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('任务修改',3,'#','','','',1,0,'F','0','0','monitor:job:edit','#','admin','2026-07-21 11:18:22','',NULL,'','fn1051','fn110');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('任务删除',4,'#','','','',1,0,'F','0','0','monitor:job:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1052','fn110');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('状态修改',5,'#','','','',1,0,'F','0','0','monitor:job:changeStatus','#','admin','2026-07-21 11:18:22','',NULL,'','fn1053','fn110');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('任务导出',6,'#','','','',1,0,'F','0','0','monitor:job:export','#','admin','2026-07-21 11:18:22','',NULL,'','fn1054','fn110');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生成查询',1,'#','','','',1,0,'F','0','0','tool:gen:query','#','admin','2026-07-21 11:18:22','',NULL,'','fn1055','fn116');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生成修改',2,'#','','','',1,0,'F','0','0','tool:gen:edit','#','admin','2026-07-21 11:18:22','',NULL,'','fn1056','fn116');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生成删除',3,'#','','','',1,0,'F','0','0','tool:gen:remove','#','admin','2026-07-21 11:18:22','',NULL,'','fn1057','fn116');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('导入代码',4,'#','','','',1,0,'F','0','0','tool:gen:import','#','admin','2026-07-21 11:18:22','',NULL,'','fn1058','fn116');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预览代码',5,'#','','','',1,0,'F','0','0','tool:gen:preview','#','admin','2026-07-21 11:18:22','',NULL,'','fn1059','fn116');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('参数设置',7,'config','system/config/index','','',1,0,'C','0','0','system:config:list','edit','admin','2026-07-21 11:18:21','',NULL,'参数设置菜单','fn106','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生成代码',6,'#','','','',1,0,'F','0','0','tool:gen:code','#','admin','2026-07-21 11:18:22','',NULL,'','fn1060','fn116');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('通知公告',8,'notice','system/notice/index','','',1,0,'C','0','0','system:notice:list','message','admin','2026-07-21 11:18:21','',NULL,'通知公告菜单','fn107','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('日志管理',9,'log','','','',1,0,'M','0','0','','log','admin','2026-07-21 11:18:21','',NULL,'日志管理菜单','fn108','fn1');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('在线用户',1,'online','monitor/online/index','','',1,0,'C','0','0','monitor:online:list','online','admin','2026-07-21 11:18:21','',NULL,'在线用户菜单','fn109','fn2');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('定时任务',2,'job','monitor/job/index','','',1,0,'C','0','0','monitor:job:list','job','admin','2026-07-21 11:18:21','',NULL,'定时任务菜单','fn110','fn2');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('数据监控',3,'druid','monitor/druid/index','','',1,0,'C','0','0','monitor:druid:list','druid','admin','2026-07-21 11:18:21','',NULL,'数据监控菜单','fn111','fn2');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('服务监控',4,'server','monitor/server/index','','',1,0,'C','0','0','monitor:server:list','server','admin','2026-07-21 11:18:21','',NULL,'服务监控菜单','fn112','fn2');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('缓存监控',5,'cache','monitor/cache/index','','',1,0,'C','0','0','monitor:cache:list','redis','admin','2026-07-21 11:18:21','',NULL,'缓存监控菜单','fn113','fn2');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('缓存列表',6,'cacheList','monitor/cache/list','','',1,0,'C','0','0','monitor:cache:list','redis-list','admin','2026-07-21 11:18:21','',NULL,'缓存列表菜单','fn114','fn2');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('表单构建',1,'build','tool/build/index','','',1,0,'C','0','0','tool:build:list','build','admin','2026-07-21 11:18:21','',NULL,'表单构建菜单','fn115','fn3');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('代码生成',2,'gen','tool/gen/index','','',1,0,'C','0','0','tool:gen:list','code','admin','2026-07-21 11:18:21','',NULL,'代码生成菜单','fn116','fn3');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('系统接口',3,'swagger','tool/swagger/index','','',1,0,'C','0','0','tool:swagger:list','swagger','admin','2026-07-21 11:18:21','',NULL,'系统接口菜单','fn117','fn3');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('系统监控',2,'monitor',NULL,'','',1,0,'M','0','0','','monitor','admin','2026-07-21 11:18:21','',NULL,'系统监控目录','fn2','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购管理',6,'purchase',NULL,'','',1,0,'M','0','0','','shopping','admin','2026-08-03 09:35:01','',NULL,'采购管理目录','fn2000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单',1,'order','purchase/order/index','','PurchaseOrder',1,0,'C','0','0','purchase:order:list','list','admin','2026-08-03 09:35:01','',NULL,'采购订单菜单','fn2001','fn2000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单查询',1,'','','','',1,0,'F','0','0','purchase:order:query','#','admin','2026-08-03 09:35:01','',NULL,'','fn2002','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单新增',2,'','','','',1,0,'F','0','0','purchase:order:add','#','admin','2026-08-03 09:35:01','',NULL,'','fn2003','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单修改',3,'','','','',1,0,'F','0','0','purchase:order:edit','#','admin','2026-08-03 09:35:01','',NULL,'','fn2004','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单删除',4,'','','','',1,0,'F','0','0','purchase:order:remove','#','admin','2026-08-03 09:35:01','',NULL,'','fn2005','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单提交',5,'','','','',1,0,'F','0','0','purchase:order:submit','#','admin','2026-08-03 09:35:01','',NULL,'','fn2006','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购订单审核',6,'','','','',1,0,'F','0','0','purchase:order:approve','#','admin','2026-08-03 09:35:01','',NULL,'','fn2007','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('录入物流信息',7,'','','','',1,0,'F','0','0','purchase:order:logistics','#','admin','2026-08-13 16:12:53','',NULL,'','fn2008','fn2001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购发货',2,'receive','purchase/receive/index','','PurchaseReceive',1,0,'C','0','0','purchase:receive:list','shopping','admin','2026-08-12 17:29:28','',NULL,'采购员登记供应商发货，入库由库存模块的采购入库办理','fn2010','fn2000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货查询',1,'','','','',1,0,'F','0','0','purchase:receive:query','#','admin','2026-08-12 17:29:28','',NULL,'','fn2011','fn2010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('登记发货',2,'','','','',1,0,'F','0','0','purchase:receive:add','#','admin','2026-08-12 17:29:28','',NULL,'','fn2012','fn2010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('修改发货',3,'','','','',1,0,'F','0','0','purchase:receive:edit','#','admin','2026-08-12 17:29:28','',NULL,'','fn2013','fn2010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('取消发货',8,'','','','',1,0,'F','0','0','purchase:receive:cancel','#','admin','2026-08-12 17:29:28','',NULL,'','fn2018','fn2010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('系统工具',3,'tool',NULL,'','',1,0,'M','0','0','','tool','admin','2026-07-21 11:18:21','',NULL,'系统工具目录','fn3','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('员工管理',4,'employee-management','Layout','','EmployeeManagement',1,0,'M','0','0','','peoples','admin','2026-07-23 11:33:43','admin','2026-08-03 09:35:02','商家员工管理目录','fn3000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门管理',1,'dept','tenant/employee/dept','','TenantEmployeeDept',1,0,'C','0','0','tenant:employee:dept:list','tree','admin','2026-07-23 11:33:43','admin','2026-08-03 09:35:02','商家部门管理菜单','fn3001','fn3000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('员工管理',2,'employee','tenant/employee/index','','TenantEmployeeUser',1,0,'C','0','0','tenant:employee:user:list','user','admin','2026-07-23 11:33:43','admin','2026-08-03 09:35:02','商家员工管理菜单','fn3002','fn3000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色权限',3,'permission','tenant/employee/permission','','TenantEmployeePermission',1,0,'C','0','0','tenant:employee:role:list','peoples','admin','2026-07-23 11:33:43','admin','2026-08-03 09:35:02','商家角色权限管理菜单','fn3003','fn3000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门查询',1,'','','','',1,0,'F','0','0','tenant:employee:dept:list','#','admin','2026-07-23 11:33:43','',NULL,'','fn3010','fn3001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门新增',2,'','','','',1,0,'F','0','0','tenant:employee:dept:add','#','admin','2026-07-23 11:33:43','',NULL,'','fn3011','fn3001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门修改',3,'','','','',1,0,'F','0','0','tenant:employee:dept:edit','#','admin','2026-07-23 11:33:43','',NULL,'','fn3012','fn3001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('部门删除',4,'','','','',1,0,'F','0','0','tenant:employee:dept:remove','#','admin','2026-07-23 11:33:43','',NULL,'','fn3013','fn3001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('员工查询',1,'','','','',1,0,'F','0','0','tenant:employee:user:query','#','admin','2026-07-23 11:33:43','',NULL,'','fn3020','fn3002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('员工新增',2,'','','','',1,0,'F','0','0','tenant:employee:user:add','#','admin','2026-07-23 11:33:43','',NULL,'','fn3021','fn3002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('员工修改',3,'','','','',1,0,'F','0','0','tenant:employee:user:edit','#','admin','2026-07-23 11:33:43','',NULL,'','fn3022','fn3002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('员工删除',4,'','','','',1,0,'F','0','0','tenant:employee:user:remove','#','admin','2026-07-23 11:33:43','',NULL,'','fn3023','fn3002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色查询',1,'','','','',1,0,'F','0','0','tenant:employee:role:query','#','admin','2026-07-23 11:33:43','',NULL,'','fn3030','fn3003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色新增',2,'','','','',1,0,'F','0','0','tenant:employee:role:add','#','admin','2026-07-23 11:33:43','',NULL,'','fn3031','fn3003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色修改',3,'','','','',1,0,'F','0','0','tenant:employee:role:edit','#','admin','2026-07-23 11:33:43','',NULL,'','fn3032','fn3003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('角色删除',4,'','','','',1,0,'F','0','0','tenant:employee:role:remove','#','admin','2026-07-23 11:33:43','',NULL,'','fn3033','fn3003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分配权限',5,'','','','',1,0,'F','0','0','tenant:employee:role:editPermission','#','admin','2026-07-23 11:33:43','',NULL,'','fn3034','fn3003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('操作日志',1,'operlog','monitor/operlog/index','','',1,0,'C','0','0','monitor:operlog:list','form','admin','2026-07-21 11:18:21','',NULL,'操作日志菜单','fn500','fn108');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存管理',5,'inventory',NULL,'','',1,0,'M','0','0','','component','admin','2026-08-03 09:35:01','',NULL,'库存管理目录','fn5000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品信息',2,'goods','inventory/goods/index','','',1,0,'C','0','0','inventory:goods:list','shopping','admin','2026-08-03 09:35:01','admin','2026-08-03 10:02:00','商品管理菜单','fn5001','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库信息',2,'warehouse','inventory/warehouse/index','','',1,0,'C','0','0','inventory:warehouse:list','server','admin','2026-08-03 09:35:01','admin','2026-08-03 10:02:00','仓库管理菜单','fn5002','fn5091');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存查询',4,'stock','inventory/stock/index','','',1,0,'C','0','0','','chart','admin','2026-08-03 09:35:01','',NULL,'库存查询菜单','fn5003','fn5093');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('供应商信息',3,'partner','inventory/partner/index','','',1,0,'C','0','0','inventory:partner:list','peoples','admin','2026-08-03 09:35:01','admin','2026-08-03 10:02:00','供应商管理菜单','fn5004','fn5091');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存新增',2,'','','','',1,0,'F','0','0','inventory:stock:add','#','admin','2026-08-03 09:35:01','',NULL,'','fn5006','fn5003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存修改',3,'','','','',1,0,'F','0','0','inventory:stock:edit','#','admin','2026-08-03 09:35:01','',NULL,'','fn5007','fn5003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存删除',4,'','','','',1,0,'F','0','0','inventory:stock:remove','#','admin','2026-08-03 09:35:01','',NULL,'','fn5008','fn5003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('登录日志',2,'logininfor','monitor/logininfor/index','','',1,0,'C','0','0','monitor:logininfor:list','logininfor','admin','2026-07-21 11:18:21','',NULL,'登录日志菜单','fn501','fn108');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存流水',3,'ledger','inventory/ledger/index',NULL,'',1,0,'C','0','0','inventory:ledger:list','log','admin','2026-08-03 10:00:31','admin',NULL,'库存流水查询','fn5010','fn5093');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('入库信息',1,'inbound','inventory/inbound/index',NULL,'',1,0,'C','0','0','inventory:inbound:list','download','admin','2026-08-03 10:00:31','admin',NULL,'入库信息管理','fn5011','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('出库信息',2,'outbound','inventory/outbound/index',NULL,'',1,0,'C','0','0','inventory:outbound:list','upload','admin','2026-08-03 10:00:31','admin',NULL,'出库管理','fn5012','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('调拨信息',3,'transfer','inventory/transfer/index',NULL,'',1,0,'C','0','0','inventory:transfer:list','switch','admin','2026-08-03 10:00:32','admin',NULL,'仓库调拨管理','fn5013','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('批次信息',1,'batch','inventory/batch/index',NULL,'',1,0,'C','0','0','inventory:batch:list','component','admin','2026-08-03 10:00:32','admin','2026-08-04 15:19:10','批次管理','fn5014','fn5093');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('序列号信息',2,'serial','inventory/serial/index',NULL,'',1,0,'C','0','0','inventory:serial:list','drag','admin','2026-08-03 10:00:32','admin','2026-08-04 15:19:25','序列号管理','fn5015','fn5093');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('入库信息新增',1,'#','',NULL,'',1,0,'F','0','0','inventory:inbound:add','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5016','fn5011');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('入库信息审核',2,'#','',NULL,'',1,0,'F','0','0','inventory:inbound:approve','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5017','fn5011');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('出库新增',1,'#','',NULL,'',1,0,'F','0','0','inventory:outbound:add','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5018','fn5012');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('出库审核',2,'#','',NULL,'',1,0,'F','0','0','inventory:outbound:approve','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5019','fn5012');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('调拨新增',1,'#','',NULL,'',1,0,'F','0','0','inventory:transfer:add','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5020','fn5013');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('调拨审核',2,'#','',NULL,'',1,0,'F','0','0','inventory:transfer:approve','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5021','fn5013');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('批次查询',1,'#','',NULL,'',1,0,'F','0','0','inventory:batch:query','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5023','fn5014');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('批次删除',2,'#','',NULL,'',1,0,'F','0','0','inventory:batch:remove','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5024','fn5014');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('序列号新增',1,'#','',NULL,'',1,0,'F','0','0','inventory:serial:add','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5025','fn5015');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('序列号查询',2,'#','',NULL,'',1,0,'F','0','0','inventory:serial:query','#','admin','2026-08-03 10:00:32','admin',NULL,'','fn5026','fn5015');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('出库修改',3,'#','','','',1,0,'F','0','0','inventory:outbound:edit','#','admin','2026-08-03 10:02:00','',NULL,'出库管理修改按钮','fn5027','fn5012');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('出库删除',4,'#','','','',1,0,'F','0','0','inventory:outbound:remove','#','admin','2026-08-03 10:02:00','',NULL,'出库管理删除按钮','fn5028','fn5012');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('调拨修改',3,'#','','','',1,0,'F','0','0','inventory:transfer:edit','#','admin','2026-08-03 10:02:00','',NULL,'调拨管理修改按钮','fn5029','fn5013');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('调拨删除',4,'#','','','',1,0,'F','0','0','inventory:transfer:remove','#','admin','2026-08-03 10:02:00','',NULL,'调拨管理删除按钮','fn5030','fn5013');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('借出信息',4,'loan','inventory/asset/index','','',1,0,'C','0','0','inventory:loan:list','upload','admin','2026-08-04 17:00:10','admin','2026-08-11 14:11:33','借出信息','fn5031','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('归还信息',5,'return','inventory/asset/index','','',1,0,'C','0','0','inventory:return:list','download','admin','2026-08-04 17:00:10','admin','2026-08-11 14:11:33','归还信息','fn5032','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('维修信息',7,'repair','inventory/asset/index','','',1,0,'C','0','0','inventory:repair:list','edit','admin','2026-08-04 17:00:10','admin','2026-08-11 14:11:33','维修信息','fn5033','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('损坏信息',6,'damage','inventory/asset/index','','',1,0,'C','0','0','inventory:damage:list','warning','admin','2026-08-04 17:00:10','admin','2026-08-11 14:11:33','损坏信息','fn5034','fn5092');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('借出新增',1,'#','','','',1,0,'F','0','0','inventory:loan:add','#','admin','2026-08-04 17:00:10','',NULL,'借出新增按钮','fn5035','fn5031');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('借出编辑',2,'#','','','',1,0,'F','0','0','inventory:loan:edit','#','admin','2026-08-04 17:00:10','',NULL,'借出编辑按钮','fn5036','fn5031');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('借出删除',3,'#','','','',1,0,'F','0','0','inventory:loan:remove','#','admin','2026-08-04 17:00:10','',NULL,'借出删除按钮','fn5037','fn5031');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('归还新增',1,'#','','','',1,0,'F','0','0','inventory:return:add','#','admin','2026-08-04 17:00:10','',NULL,'归还新增按钮','fn5038','fn5032');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('归还编辑',2,'#','','','',1,0,'F','0','0','inventory:return:edit','#','admin','2026-08-04 17:00:10','',NULL,'归还编辑按钮','fn5039','fn5032');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('归还删除',3,'#','','','',1,0,'F','0','0','inventory:return:remove','#','admin','2026-08-04 17:00:10','',NULL,'归还删除按钮','fn5040','fn5032');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('维修新增',1,'#','','','',1,0,'F','0','0','inventory:repair:add','#','admin','2026-08-04 17:00:10','',NULL,'维修新增按钮','fn5041','fn5033');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('维修编辑',2,'#','','','',1,0,'F','0','0','inventory:repair:edit','#','admin','2026-08-04 17:00:10','',NULL,'维修编辑按钮','fn5042','fn5033');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('维修删除',3,'#','','','',1,0,'F','0','0','inventory:repair:remove','#','admin','2026-08-04 17:00:10','',NULL,'维修删除按钮','fn5043','fn5033');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('损坏新增',1,'#','','','',1,0,'F','0','0','inventory:damage:add','#','admin','2026-08-04 17:00:10','',NULL,'损坏新增按钮','fn5044','fn5034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('损坏编辑',2,'#','','','',1,0,'F','0','0','inventory:damage:edit','#','admin','2026-08-04 17:00:10','',NULL,'损坏编辑按钮','fn5045','fn5034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('损坏删除',3,'#','','','',1,0,'F','0','0','inventory:damage:remove','#','admin','2026-08-04 17:00:10','',NULL,'损坏删除按钮','fn5046','fn5034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('借出审核',4,'#','','','',1,0,'F','0','0','inventory:loan:approve','#','admin','2026-08-11 14:09:36','',NULL,'借出审核按钮','fn5047','fn5031');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('归还审核',4,'#','','','',1,0,'F','0','0','inventory:return:approve','#','admin','2026-08-11 14:09:36','',NULL,'归还审核按钮','fn5048','fn5032');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('维修审核',4,'#','','','',1,0,'F','0','0','inventory:repair:approve','#','admin','2026-08-11 14:09:36','',NULL,'维修审核按钮','fn5049','fn5033');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('损坏审核',4,'#','','','',1,0,'F','0','0','inventory:damage:approve','#','admin','2026-08-11 14:09:36','',NULL,'损坏审核按钮','fn5050','fn5034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('入库信息编辑',3,'#','','','',1,0,'F','0','0','inventory:inbound:edit','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','入库信息编辑按钮','fn5051','fn5011');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('入库信息删除',4,'#','','','',1,0,'F','0','0','inventory:inbound:remove','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','入库信息删除按钮','fn5052','fn5011');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品查询',1,'#','','','',1,0,'F','0','0','inventory:goods:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','商品详情查询按钮','fn5053','fn5001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品新增',2,'#','','','',1,0,'F','0','0','inventory:goods:add','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','商品新增按钮','fn5054','fn5001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品修改',3,'#','','','',1,0,'F','0','0','inventory:goods:edit','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','商品修改按钮','fn5055','fn5001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品删除',4,'#','','','',1,0,'F','0','0','inventory:goods:remove','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','商品删除按钮','fn5056','fn5001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库查询',1,'#','','','',1,0,'F','0','0','inventory:warehouse:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','仓库详情查询按钮','fn5057','fn5002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库新增',2,'#','','','',1,0,'F','0','0','inventory:warehouse:add','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','仓库新增按钮','fn5058','fn5002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库修改',3,'#','','','',1,0,'F','0','0','inventory:warehouse:edit','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','仓库修改按钮','fn5059','fn5002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库删除',4,'#','','','',1,0,'F','0','0','inventory:warehouse:remove','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','仓库删除按钮','fn5060','fn5002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('往来单位查询',1,'#','','','',1,0,'F','0','0','inventory:partner:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','往来单位详情查询按钮','fn5061','fn5004');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('往来单位新增',2,'#','','','',1,0,'F','0','0','inventory:partner:add','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','往来单位新增按钮','fn5062','fn5004');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('往来单位修改',3,'#','','','',1,0,'F','0','0','inventory:partner:edit','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','往来单位修改按钮','fn5063','fn5004');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('往来单位删除',4,'#','','','',1,0,'F','0','0','inventory:partner:remove','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','往来单位删除按钮','fn5064','fn5004');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('单据类型查询',20,'#','','','',1,0,'F','0','0','inventory:doctype:list','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','单据类型列表查询','fn5069','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('单据类型详情',21,'#','','','',1,0,'F','0','0','inventory:doctype:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','单据类型详情查询','fn5070','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('单据类型新增',22,'#','','','',1,0,'F','0','0','inventory:doctype:add','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','单据类型新增','fn5071','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('单据类型修改',23,'#','','','',1,0,'F','0','0','inventory:doctype:edit','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','单据类型修改','fn5072','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('单据类型删除',24,'#','','','',1,0,'F','0','0','inventory:doctype:remove','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','单据类型删除','fn5073','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('批次详情查询',3,'#','','','',1,0,'F','0','0','inventory:batch:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','批次详情查询','fn5074','fn5014');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('序列号详情查询',3,'#','','','',1,0,'F','0','0','inventory:serial:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','序列号详情查询','fn5075','fn5015');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存查询',1,'#','','','',1,0,'F','0','0','inventory:stock:query','#','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','库存查询按钮','fn5076','fn5003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存预警',5,'warning',NULL,'','',1,0,'M','0','0','','warning','admin','2026-08-11 15:05:09','',NULL,'库存低库存预警','fn5080','fn5093');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警规则',1,'rule','inventory/warningRule/index','','InventoryWarningRule',1,0,'C','0','0','inventory:warning:rule:list','edit','admin','2026-08-11 15:05:09','',NULL,'库存低库存预警规则','fn5081','fn5080');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警记录',2,'record','inventory/warningRecord/index','','InventoryWarningRecord',1,0,'C','0','0','inventory:warning:record:list','list','admin','2026-08-11 15:05:09','',NULL,'库存低库存预警记录','fn5082','fn5080');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警规则查询',1,'#','','','',1,0,'F','0','0','inventory:warning:rule:query','#','admin','2026-08-11 15:05:09','',NULL,'预警规则查询','fn5083','fn5081');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警规则新增',2,'#','','','',1,0,'F','0','0','inventory:warning:rule:add','#','admin','2026-08-11 15:05:09','',NULL,'预警规则新增','fn5084','fn5081');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警规则修改',3,'#','','','',1,0,'F','0','0','inventory:warning:rule:edit','#','admin','2026-08-11 15:05:09','',NULL,'预警规则修改','fn5085','fn5081');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警规则删除',4,'#','','','',1,0,'F','0','0','inventory:warning:rule:remove','#','admin','2026-08-11 15:05:09','',NULL,'预警规则删除','fn5086','fn5081');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警记录查询',1,'#','','','',1,0,'F','0','0','inventory:warning:record:query','#','admin','2026-08-11 15:05:09','',NULL,'预警记录查询','fn5087','fn5082');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('预警记录处理',2,'#','','','',1,0,'F','0','0','inventory:warning:record:handle','#','admin','2026-08-11 15:05:09','',NULL,'预警记录处理','fn5088','fn5082');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('手动检查预警',3,'#','','','',1,0,'F','0','0','inventory:warning:check','#','admin','2026-08-11 15:05:09','',NULL,'手动检查预警','fn5089','fn5080');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品分类',1,'goodsCategory','inventory/goodsCategory/index','','',1,0,'C','0','0','inventory:goodscategory:list','tree','admin','2026-08-14 16:27:43','',NULL,'按分类与规格型号浏览商品','fn5090','fn5091');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('基础设置',1,'base',NULL,'','',1,0,'M','0','0','','system','admin','2026-08-18 09:16:21','',NULL,'库存管理-基础设置分组','fn5091','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存变动',3,'change',NULL,'','',1,0,'M','0','0','','guide','admin','2026-08-18 09:16:21','',NULL,'库存管理-库存变动分组','fn5092','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('库存统计',4,'stat',NULL,'','',1,0,'M','0','0','','chart','admin','2026-08-18 09:16:21','',NULL,'库存管理-库存统计分组','fn5093','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类查询',1,'#',NULL,'','',1,0,'F','0','0','inventory:goodscategory:query','#','admin','2026-08-18 14:26:53','',NULL,'商品分类查询','fn5094','fn5090');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类新增',2,'#',NULL,'','',1,0,'F','0','0','inventory:goodscategory:add','#','admin','2026-08-18 14:26:53','',NULL,'商品分类新增','fn5095','fn5090');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类修改',3,'#',NULL,'','',1,0,'F','0','0','inventory:goodscategory:edit','#','admin','2026-08-18 14:26:53','',NULL,'商品分类修改','fn5096','fn5090');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类删除',4,'#',NULL,'','',1,0,'F','0','0','inventory:goodscategory:remove','#','admin','2026-08-18 14:26:53','',NULL,'商品分类删除','fn5097','fn5090');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品位置',4,'goodsPosition','inventory/goodsPosition/index','','InventoryGoodsPosition',1,0,'C','0','0','inventory:goodsposition:list','guide','admin','2026-08-24 11:22:22','',NULL,'登记商品在仓库中的存放位置','fn5098','fn5091');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品位置查询',1,'#','','','',1,0,'F','0','0','inventory:goodsposition:query','#','admin','2026-08-24 11:22:22','',NULL,'商品位置查询','fn5099','fn5098');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品位置新增',2,'#','','','',1,0,'F','0','0','inventory:goodsposition:add','#','admin','2026-08-24 11:22:22','',NULL,'商品位置新增','fn5100','fn5098');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品位置修改',3,'#','','','',1,0,'F','0','0','inventory:goodsposition:edit','#','admin','2026-08-24 11:22:22','',NULL,'商品位置修改','fn5101','fn5098');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('商品位置删除',4,'#','','','',1,0,'F','0','0','inventory:goodsposition:remove','#','admin','2026-08-24 11:22:22','',NULL,'商品位置删除','fn5102','fn5098');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库发货管理',5,'shipbatch','inventory/shipbatch/index','','WarehouseShipBatch',1,0,'C','0','0','order:shipbatch:warehouse','guide','admin','2026-08-28 09:34:30','',NULL,'仓库按订单排好的批次办确认、备货、发货','fn5103','fn5000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库发货查询',1,'#','','','',1,0,'F','0','0','order:shipbatch:query','#','admin','2026-08-28 09:34:30','',NULL,'仓库发货查询','fn5104','fn5103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库打印备货单/快递单',2,'#','','','',1,0,'F','0','0','order:shipbatch:print','#','admin','2026-08-28 09:34:30','',NULL,'仓库侧两个打印动作共用：打印备货单（已提交仓库→已打印备货单）、打印快递单号（已录入快递单号→已打印快递单号）','fn5105','fn5103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库发货完成/驳回',3,'#','','','',1,0,'F','0','0','order:shipbatch:ship','#','admin','2026-08-28 09:34:30','',NULL,'点「完成」开销售出库单扣库存，以及把批次「驳回」退给业务员','fn5106','fn5103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('仓库备货完成',4,'#','','','',1,0,'F','0','0','order:shipbatch:picking','#','wczladmin','2026-09-20 00:00:00','',NULL,'上传备货照片并点备货完成（已打印备货单→备货完成）。照片至少一张','fn5107','fn5103');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生产管理',7,'production',NULL,'','',1,0,'M','0','0','','build','admin','2026-08-14 11:47:04','',NULL,'生产管理目录','fn6000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生产工单',1,'order','production/order/index','','ProductionOrder',1,0,'C','0','0','production:order:list','list','admin','2026-08-14 11:47:04','',NULL,'生产工单菜单','fn6001','fn6000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('工单新增修改',1,'','','','',1,0,'F','0','0','production:order:add','#','admin','2026-08-14 11:47:04','',NULL,'','fn6002','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('工单领料',2,'','','','',1,0,'F','0','0','production:order:pick','#','admin','2026-08-14 11:47:04','',NULL,'','fn6003','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('工单完工',3,'','','','',1,0,'F','0','0','production:order:complete','#','admin','2026-08-14 11:47:04','',NULL,'','fn6004','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('工单退料',4,'','','','',1,0,'F','0','0','production:order:return','#','admin','2026-08-14 11:47:04','',NULL,'','fn6005','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('工单关闭',5,'','','','',1,0,'F','0','0','production:order:close','#','admin','2026-08-14 11:47:04','',NULL,'','fn6006','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生产工单关闭',6,'#','','','',1,0,'F','0','0','production:order:close','#','admin','2026-08-19 17:11:09','',NULL,'生产工单关闭','fn6007','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生产工单删除',7,'#','','','',1,0,'F','0','0','production:order:remove','#','admin','2026-08-19 17:11:09','',NULL,'生产工单删除','fn6008','fn6001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('生产单据',2,'document','production/document/index','','ProductionDocument',1,0,'C','0','0','production:document:list','list','admin','2026-08-19 17:11:09','',NULL,'生产工单产生的库存单据（只读）','fn6009','fn6000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('产品配方',3,'bom','production/bom/index','','ProductionBom',1,0,'C','0','0','production:bom:list','edit','admin','2026-08-21 10:22:22','',NULL,'产品配方（BOM）主数据','fn6010','fn6000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('配方查询',1,'#','','','',1,0,'F','0','0','production:bom:list','#','admin','2026-08-21 10:22:22','',NULL,'配方查询','fn6011','fn6010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('配方新增',2,'#','','','',1,0,'F','0','0','production:bom:add','#','admin','2026-08-21 10:22:22','',NULL,'配方新增','fn6012','fn6010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('配方修改',3,'#','','','',1,0,'F','0','0','production:bom:edit','#','admin','2026-08-21 10:22:22','',NULL,'配方修改','fn6013','fn6010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('配方删除',4,'#','','','',1,0,'F','0','0','production:bom:remove','#','admin','2026-08-21 10:22:22','',NULL,'配方删除','fn6014','fn6010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('财务管理',8,'finance',NULL,'','',1,0,'M','0','0','','money','admin','2026-08-14 17:07:48','',NULL,'财务管理目录','fn7000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('资金流水',1,'transaction','finance/transaction/index','','FinanceTransaction',1,0,'C','0','0','finance:transaction:list','money','admin','2026-08-14 17:07:48','',NULL,'资金流水菜单','fn7001','fn7000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('财务账户',2,'account','finance/account/index','','FinanceAccount',1,0,'C','0','0','finance:account:list','clipboard','admin','2026-08-14 17:07:48','',NULL,'财务账户菜单','fn7002','fn7000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('收支分类',3,'category','finance/category/index','','FinanceCategory',1,0,'C','0','0','finance:category:list','dict','admin','2026-08-14 17:07:48','',NULL,'收支分类菜单','fn7003','fn7000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('流水详情',1,'','','','',1,0,'F','0','0','finance:transaction:query','#','admin','2026-08-14 17:07:48','',NULL,'','fn7010','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('流水新增',2,'','','','',1,0,'F','0','0','finance:transaction:add','#','admin','2026-08-14 17:07:48','',NULL,'','fn7011','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('流水修改',3,'','','','',1,0,'F','0','0','finance:transaction:edit','#','admin','2026-08-14 17:07:48','',NULL,'','fn7012','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('流水删除',4,'','','','',1,0,'F','0','0','finance:transaction:remove','#','admin','2026-08-14 17:07:48','',NULL,'','fn7013','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('流水提交',5,'','','','',1,0,'F','0','0','finance:transaction:submit','#','admin','2026-08-14 17:07:48','',NULL,'','fn7014','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('流水审核',6,'','','','',1,0,'F','0','0','finance:transaction:audit','#','admin','2026-08-14 17:07:48','',NULL,'','fn7015','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('执行打款',7,'','','','',1,0,'F','0','0','finance:transaction:execute','#','admin','2026-08-14 17:07:48','',NULL,'','fn7016','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('回款认领',8,'','','','',1,0,'F','0','0','finance:transaction:claim','#','admin','2026-08-14 17:07:48','',NULL,'','fn7017','fn7001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('账户详情',1,'','','','',1,0,'F','0','0','finance:account:query','#','admin','2026-08-14 17:07:48','',NULL,'','fn7020','fn7002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('账户新增',2,'','','','',1,0,'F','0','0','finance:account:add','#','admin','2026-08-14 17:07:48','',NULL,'','fn7021','fn7002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('账户修改',3,'','','','',1,0,'F','0','0','finance:account:edit','#','admin','2026-08-14 17:07:48','',NULL,'','fn7022','fn7002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('账户删除',4,'','','','',1,0,'F','0','0','finance:account:remove','#','admin','2026-08-14 17:07:48','',NULL,'','fn7023','fn7002');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类新增',1,'','','','',1,0,'F','0','0','finance:category:add','#','admin','2026-08-14 17:07:48','',NULL,'','fn7030','fn7003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类修改',2,'','','','',1,0,'F','0','0','finance:category:edit','#','admin','2026-08-14 17:07:48','',NULL,'','fn7031','fn7003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('分类删除',3,'','','','',1,0,'F','0','0','finance:category:remove','#','admin','2026-08-14 17:07:48','',NULL,'','fn7032','fn7003');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('订单管理',9,'order',NULL,'','Order',1,0,'M','0','0','','shopping','admin','2026-08-19 16:35:18','',NULL,'订单管理目录','fn8000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单',1,'sales','order/sales/index','','SalesOrder',1,0,'C','0','0','order:sales:list','form','admin','2026-08-19 16:35:18','',NULL,'销售订单（前端页面待建，暂隐藏）','fn8001','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单查询',1,'#','','','',1,0,'F','0','0','order:sales:query','#','admin','2026-08-19 16:35:18','',NULL,'销售订单查询','fn8002','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单新增',2,'#','','','',1,0,'F','0','0','order:sales:add','#','admin','2026-08-19 16:35:18','',NULL,'销售订单新增','fn8003','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单修改',3,'#','','','',1,0,'F','0','0','order:sales:edit','#','admin','2026-08-19 16:35:18','',NULL,'销售订单修改','fn8004','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单删除',4,'#','','','',1,0,'F','0','0','order:sales:remove','#','admin','2026-08-19 16:35:18','',NULL,'销售订单删除','fn8005','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单确认',5,'#','','','',1,0,'F','0','0','order:sales:confirm','#','admin','2026-08-19 16:35:18','',NULL,'销售订单确认','fn8006','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单取消',6,'#','','','',1,0,'F','0','0','order:sales:cancel','#','admin','2026-08-19 16:35:18','',NULL,'销售订单取消','fn8007','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次',2,'shipbatch','order/shipbatch/index','','ShipBatch',1,0,'C','0','0','order:shipbatch:list','guide','admin','2026-08-19 16:35:18','',NULL,'发货批次（前端页面待建，暂隐藏）','fn8010','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次查询',1,'#','','','',1,0,'F','0','0','order:shipbatch:query','#','admin','2026-08-19 16:35:18','',NULL,'发货批次查询','fn8011','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次新增',2,'#','','','',1,0,'F','0','0','order:shipbatch:add','#','admin','2026-08-19 16:35:18','',NULL,'发货批次新增','fn8012','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次修改',3,'#','','','',1,0,'F','0','0','order:shipbatch:edit','#','admin','2026-08-19 16:35:18','',NULL,'发货批次修改','fn8013','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次确认',4,'#','','','',1,0,'F','0','0','order:shipbatch:confirm','#','admin','2026-08-19 16:35:18','',NULL,'发货批次确认','fn8014','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次发货',5,'#','','','',1,0,'F','0','0','order:shipbatch:ship','#','admin','2026-08-19 16:35:18','',NULL,'发货批次发货','fn8015','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次取消',6,'#','','','',1,0,'F','0','0','order:shipbatch:cancel','#','admin','2026-08-19 16:35:18','',NULL,'发货批次取消','fn8016','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('发货批次录入快递单号',7,'#','','','',1,0,'F','0','0','order:shipbatch:logistics','#','wczladmin','2026-09-20 00:00:00','',NULL,'业务员填快递单号、承运商与本次实发数量（备货完成→已录入快递单号）。这一步卡客户付款额度','fn8017','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售退货',3,'salesreturn','order/salesreturn/index','','SalesReturn',1,0,'C','0','0','order:salesreturn:list','shopping-bag','admin','2026-08-21 10:24:17','',NULL,'销售退货','fn8020','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售退货查询',1,'#','','','',1,0,'F','0','0','order:salesreturn:query','#','admin','2026-08-21 10:24:17','',NULL,'销售退货查询','fn8021','fn8020');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售退货新增',2,'#','','','',1,0,'F','0','0','order:salesreturn:add','#','admin','2026-08-21 10:24:17','',NULL,'销售退货新增','fn8022','fn8020');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单提交',8,'#','','','',1,0,'F','0','0','order:sales:submit','#','admin','2026-08-21 10:24:04','',NULL,'销售订单提交','fn8023','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售订单关闭',9,'#','','','',1,0,'F','0','0','order:sales:close','#','admin','2026-08-21 10:24:04','',NULL,'销售订单关闭','fn8024','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('平台维护',5,'platform','order/platform/index','','OrderPlatform',1,0,'C','0','0','order:platform:list','shop','admin','2026-08-21 16:39:07','',NULL,'维护销售平台及平台下的店铺','fn8025','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('平台维护查询',1,'#','','','',1,0,'F','0','0','order:platform:query','#','admin','2026-08-21 16:39:07','',NULL,'平台维护查询','fn8026','fn8025');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('平台维护新增',2,'#','','','',1,0,'F','0','0','order:platform:add','#','admin','2026-08-21 16:39:07','',NULL,'平台维护新增','fn8027','fn8025');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('平台维护修改',3,'#','','','',1,0,'F','0','0','order:platform:edit','#','admin','2026-08-21 16:39:07','',NULL,'平台维护修改','fn8028','fn8025');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('平台维护删除',4,'#','','','',1,0,'F','0','0','order:platform:remove','#','admin','2026-08-21 16:39:07','',NULL,'平台维护删除','fn8029','fn8025');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售补发',4,'salesreship','order/salesreship/index','','SalesReship',1,0,'C','0','0','order:salesreship:list','refresh-left','admin','2026-08-21 16:39:26','',NULL,'独立销售补发单，一张原订单只能补发一次','fn8030','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售补发查询',1,'#','','','',1,0,'F','0','0','order:salesreship:query','#','admin','2026-08-21 16:39:26','',NULL,'销售补发查询','fn8031','fn8030');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售补发新增',2,'#','','','',1,0,'F','0','0','order:salesreship:add','#','admin','2026-08-21 16:39:26','',NULL,'销售补发新增','fn8032','fn8030');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售补发发货',3,'#','','','',1,0,'F','0','0','order:salesreship:ship','#','admin','2026-08-21 16:39:26','',NULL,'销售补发发货','fn8033','fn8030');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售套餐',7,'salespackage','order/salespackage/index','','SalesPackage',1,0,'C','0','0','order:package:list','component','admin','2026-08-24 11:27:28','',NULL,'维护由多个商品组合成的销售套餐','fn8034','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售套餐查询',1,'#','','','',1,0,'F','0','0','order:package:query','#','admin','2026-08-24 11:27:28','',NULL,'销售套餐查询','fn8035','fn8034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售套餐新增',2,'#','','','',1,0,'F','0','0','order:package:add','#','admin','2026-08-24 11:27:28','',NULL,'销售套餐新增','fn8036','fn8034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售套餐修改',3,'#','','','',1,0,'F','0','0','order:package:edit','#','admin','2026-08-24 11:27:28','',NULL,'销售套餐修改','fn8037','fn8034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售套餐删除',4,'#','','','',1,0,'F','0','0','order:package:remove','#','admin','2026-08-24 11:27:28','',NULL,'销售套餐删除','fn8038','fn8034');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('客户信息',3,'buyer','order/buyer/index','','Buyer',1,0,'C','0','0','order:buyer:list','peoples','admin','2026-08-24 10:18:23','',NULL,'客户信息','fn8050','fn8000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('客户查询',1,'#','','','',1,0,'F','0','0','order:buyer:query','#','admin','2026-08-24 10:18:23','',NULL,'客户查询','fn8051','fn8050');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('客户新增',2,'#','','','',1,0,'F','0','0','order:buyer:add','#','admin','2026-08-24 10:18:23','',NULL,'客户新增','fn8052','fn8050');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('客户修改',3,'#','','','',1,0,'F','0','0','order:buyer:edit','#','admin','2026-08-24 10:18:23','',NULL,'客户修改','fn8053','fn8050');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('客户删除',4,'#','','','',1,0,'F','0','0','order:buyer:remove','#','admin','2026-08-24 10:18:23','',NULL,'客户删除','fn8054','fn8050');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('客户导出',5,'#','','','',1,0,'F','0','0','order:buyer:export','#','admin','2026-08-24 10:18:23','',NULL,'客户导出','fn8055','fn8050');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('独立站',10,'site',NULL,'','Site',1,0,'M','0','0','','international','admin','2026-08-25 14:29:45','',NULL,'独立站目录','fn9000','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('独立站管理',1,'station','site/station/index','','StationSite',1,0,'C','0','0','site:station:list','guide','admin','2026-08-25 14:29:45','',NULL,'独立站登记表：有哪些站、在哪、怎么进。站里的内容在各站自己的管理台改','fn9001','fn9000');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('站点查询',1,'#','','','',1,0,'F','0','0','site:station:query','#','admin','2026-08-25 14:29:45','',NULL,'站点查询','fn9002','fn9001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('站点新增',2,'#','','','',1,0,'F','0','0','site:station:add','#','admin','2026-08-25 14:29:45','',NULL,'站点新增','fn9003','fn9001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('站点修改',3,'#','','','',1,0,'F','0','0','site:station:edit','#','admin','2026-08-25 14:29:45','',NULL,'站点修改','fn9004','fn9001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('站点删除',4,'#','','','',1,0,'F','0','0','site:station:remove','#','admin','2026-08-25 14:29:45','',NULL,'站点删除','fn9005','fn9001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('价格可见性',900,'pricevisible',NULL,'','',1,0,'M','1','0','','money','wczladmin','2026-09-10 00:00:00','',NULL,'金额按岗位可见的权限点容器，不出现在侧边栏','fn9040','0');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('采购与成本价',1,'','','','',1,0,'F','0','0','price:cost','#','wczladmin','2026-09-10 00:00:00','',NULL,'看得到采购价、库存成本、生产成本、运费','fn9041','fn9040');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('销售价',2,'','','','',1,0,'F','0','0','price:sale','#','wczladmin','2026-09-10 00:00:00','',NULL,'看得到销售单价、销售额、商品参考价','fn9042','fn9040');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('查看全部订单',10,'','','','',1,0,'F','0','0','order:sales:all','#','wczladmin','2026-09-11 00:00:00','',NULL,'不勾选＝只看得到归属于自己的销售订单；勾选＝看得到全店的。归属认建单时的登录账号','fn8042','fn8001');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('查看全部发货批次',10,'','','','',1,0,'F','0','0','order:shipbatch:all','#','wczladmin','2026-09-11 00:00:00','',NULL,'只管「订单管理 → 发货批次」这个页面；不勾＝只看得到自己建的批次。仓库侧不需要它','fn8043','fn8010');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('查看全部退货单',10,'','','','',1,0,'F','0','0','order:salesreturn:all','#','wczladmin','2026-09-11 00:00:00','',NULL,'不勾＝只看得到自己建的退货单。提交（审核即过账）不受它限制，入库员不勾也能审','fn8044','fn8020');
INSERT IGNORE INTO `sys_menu` (`menu_name`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `fun_code`, `parent_id`) VALUES ('查看全部补发单',10,'','','','',1,0,'F','0','0','order:salesreship:all','#','wczladmin','2026-09-11 00:00:00','',NULL,'不勾＝只看得到自己建的补发单。⚠ 仓库要在补发页点发货，仓库角色也得勾上','fn8045','fn8030');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'用户性别','sys_user_sex','0','admin','2026-07-21 11:18:23','',NULL,'用户性别列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2,'菜单状态','sys_show_hide','0','admin','2026-07-21 11:18:23','',NULL,'菜单状态列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3,'系统开关','sys_normal_disable','0','admin','2026-07-21 11:18:23','',NULL,'系统开关列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (4,'任务状态','sys_job_status','0','admin','2026-07-21 11:18:23','',NULL,'任务状态列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (5,'任务分组','sys_job_group','0','admin','2026-07-21 11:18:23','',NULL,'任务分组列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (6,'系统是否','sys_yes_no','0','admin','2026-07-21 11:18:23','',NULL,'系统是否列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (7,'通知类型','sys_notice_type','0','admin','2026-07-21 11:18:23','',NULL,'通知类型列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (8,'通知状态','sys_notice_status','0','admin','2026-07-21 11:18:23','',NULL,'通知状态列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (9,'操作类型','sys_oper_type','0','admin','2026-07-21 11:18:23','',NULL,'操作类型列表');
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (10,'系统状态','sys_common_status','0','admin','2026-07-21 11:18:23','',NULL,'登录状态列表');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,1,'男','0','sys_user_sex','','','Y','0','admin','2026-07-21 11:18:23','',NULL,'性别男');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2,2,'女','1','sys_user_sex','','','N','0','admin','2026-07-21 11:18:23','',NULL,'性别女');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3,3,'未知','2','sys_user_sex','','','N','0','admin','2026-07-21 11:18:23','',NULL,'性别未知');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (4,1,'显示','0','sys_show_hide','','primary','Y','0','admin','2026-07-21 11:18:23','',NULL,'显示菜单');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (5,2,'隐藏','1','sys_show_hide','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'隐藏菜单');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (6,1,'正常','0','sys_normal_disable','','primary','Y','0','admin','2026-07-21 11:18:23','',NULL,'正常状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (7,2,'停用','1','sys_normal_disable','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'停用状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (8,1,'正常','0','sys_job_status','','primary','Y','0','admin','2026-07-21 11:18:23','',NULL,'正常状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (9,2,'暂停','1','sys_job_status','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'停用状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (10,1,'默认','DEFAULT','sys_job_group','','','Y','0','admin','2026-07-21 11:18:23','',NULL,'默认分组');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (11,2,'系统','SYSTEM','sys_job_group','','','N','0','admin','2026-07-21 11:18:23','',NULL,'系统分组');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (12,1,'是','Y','sys_yes_no','','primary','Y','0','admin','2026-07-21 11:18:23','',NULL,'系统默认是');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (13,2,'否','N','sys_yes_no','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'系统默认否');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (14,1,'通知','1','sys_notice_type','','warning','Y','0','admin','2026-07-21 11:18:23','',NULL,'通知');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (15,2,'公告','2','sys_notice_type','','success','N','0','admin','2026-07-21 11:18:23','',NULL,'公告');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (16,1,'正常','0','sys_notice_status','','primary','Y','0','admin','2026-07-21 11:18:23','',NULL,'正常状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (17,2,'关闭','1','sys_notice_status','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'关闭状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (18,99,'其他','0','sys_oper_type','','info','N','0','admin','2026-07-21 11:18:23','',NULL,'其他操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (19,1,'新增','1','sys_oper_type','','info','N','0','admin','2026-07-21 11:18:23','',NULL,'新增操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (20,2,'修改','2','sys_oper_type','','info','N','0','admin','2026-07-21 11:18:23','',NULL,'修改操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (21,3,'删除','3','sys_oper_type','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'删除操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (22,4,'授权','4','sys_oper_type','','primary','N','0','admin','2026-07-21 11:18:23','',NULL,'授权操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (23,5,'导出','5','sys_oper_type','','warning','N','0','admin','2026-07-21 11:18:23','',NULL,'导出操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (24,6,'导入','6','sys_oper_type','','warning','N','0','admin','2026-07-21 11:18:23','',NULL,'导入操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (25,7,'强退','7','sys_oper_type','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'强退操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (26,8,'生成代码','8','sys_oper_type','','warning','N','0','admin','2026-07-21 11:18:23','',NULL,'生成操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (27,9,'清空数据','9','sys_oper_type','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'清空操作');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (28,1,'成功','0','sys_common_status','','primary','N','0','admin','2026-07-21 11:18:23','',NULL,'正常状态');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (29,2,'失败','1','sys_common_status','','danger','N','0','admin','2026-07-21 11:18:23','',NULL,'停用状态');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'主框架页-默认皮肤样式名称','sys.index.skinName','skin-blue','Y','admin','2026-07-21 11:18:23','',NULL,'蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2,'用户管理-账号初始密码','sys.user.initPassword','123456','Y','admin','2026-07-21 11:18:23','',NULL,'初始化密码 123456');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3,'主框架页-侧边栏主题','sys.index.sideTheme','theme-dark','Y','admin','2026-07-21 11:18:23','',NULL,'深色主题theme-dark，浅色主题theme-light');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (4,'账号自助-验证码开关','sys.account.captchaEnabled','true','Y','admin','2026-07-21 11:18:23','',NULL,'是否开启验证码功能（true开启，false关闭）');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (5,'账号自助-是否开启用户注册功能','sys.account.registerUser','false','Y','admin','2026-07-21 11:18:23','',NULL,'是否开启注册用户功能（true开启，false关闭）');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (6,'用户登录-黑名单列表','sys.login.blackIPList','','Y','admin','2026-07-21 11:18:23','',NULL,'设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (7,'用户管理-初始密码修改策略','sys.account.initPasswordModify','1','Y','admin','2026-07-21 11:18:23','',NULL,'0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (8,'用户管理-账号密码更新周期','sys.account.passwordValidateDays','0','Y','admin','2026-07-21 11:18:23','',NULL,'密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (9,'用户管理-密码字符范围','sys.account.chrtype','0','Y','admin','2026-07-21 11:18:23','',NULL,'默认任意字符范围，0任意（密码可以输入任意字符），1数字（密码只能为0-9数字），2英文字母（密码只能为a-z和A-Z字母），3字母和数字（密码必须包含字母，数字）,4字母数字和特殊字符（目前支持的特殊字符包括：~!@#$%^&*()-=_+）');
INSERT IGNORE INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (100,'采购订单直接入库开关','purchase.inbound.legacy.enabled','false','Y','admin','2026-08-13 13:57:11','',NULL,'true 允许采购订单直接生成入库单（旧通道）；false 强制走采购到货与质检。不影响手工直接入库。');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT IGNORE INTO `sys_post` (`post_id`, `shop_code`, `post_code`, `post_name`, `post_sort`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'','ceo','董事长',1,'0','admin','2026-07-21 11:18:21','',NULL,'');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT IGNORE INTO `sys_job` (`job_id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'系统默认（无参）','DEFAULT','ryTask.ryNoParams','0/10 * * * * ?','3','1','1','admin','2026-07-21 11:18:24','',NULL,'');
INSERT IGNORE INTO `sys_job` (`job_id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2,'系统默认（有参）','DEFAULT','ryTask.ryParams(\'ry\')','0/15 * * * * ?','3','1','1','admin','2026-07-21 11:18:24','',NULL,'');
INSERT IGNORE INTO `sys_job` (`job_id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3,'系统默认（多参）','DEFAULT','ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)','0/20 * * * * ?','3','1','1','admin','2026-07-21 11:18:24','',NULL,'');
INSERT IGNORE INTO `sys_job` (`job_id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (100,'库存低库存预警检查','SYSTEM','inventoryWarningTask.checkWarnings','0 0 2 * * ?','3','1','0','admin','2026-08-11 15:05:09','',NULL,'每天凌晨 2 点检查库存低库存预警');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;

/*!40000 ALTER TABLE `s_m_function` DISABLE KEYS */;
INSERT IGNORE INTO `s_m_function` (`fun_code`, `fun_name`, `fun_text`, `fun_parent`, `order_no`, `fun_type`, `status`, `remark`, `max_sub`, `create_time`, `update_time`) VALUES ('FUN_TENANT_HOME','tenant_home','商家工作台',NULL,1,2,1,'进入商家后的基础工作台权限',0,'2026-07-22 16:27:27','2026-07-22 16:27:27');
/*!40000 ALTER TABLE `s_m_function` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (100,'',NULL,0,'0','若依科技',0,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (101,'',NULL,100,'0,100','深圳总公司',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (102,'',NULL,100,'0,100','长沙分公司',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (103,'',NULL,101,'0,100,101','研发部门',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (104,'',NULL,101,'0,100,101','市场部门',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (105,'',NULL,101,'0,100,101','测试部门',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (106,'',NULL,101,'0,100,101','财务部门',4,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (107,'',NULL,101,'0,100,101','运维部门',5,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (108,'',NULL,102,'0,100,102','市场部门',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (109,'',NULL,102,'0,100,102','财务部门',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-07-21 11:18:21','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (218,'sp708xm02q','TJ_KWJ_ROOT',0,'0','天津康万家总部',0,NULL,NULL,NULL,'0','0','wczladmin','2026-08-07 10:13:07','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (219,'sp708xm02u','TJ_KC_ROOT',0,'0','天津康晨总部',0,NULL,NULL,NULL,'0','0','wczladmin','2026-08-07 10:13:07','',NULL);
INSERT IGNORE INTO `sys_dept` (`dept_id`, `shop_code`, `legacy_org_code`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (220,'sp708xm02r','CD_RT_ROOT',0,'0','常德睿腾总部',0,NULL,NULL,NULL,'0','0','wczladmin','2026-08-07 10:13:07','',NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT IGNORE INTO `sys_role` (`role_code`, `shop_code`, `role_type`, `system_flag`, `legacy_role_code`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('rl1','','PLATFORM',1,NULL,'超级管理员','admin',1,'1',1,1,'0','0','admin','2026-07-21 11:18:21','',NULL,'超级管理员');
INSERT IGNORE INTO `sys_role` (`role_code`, `shop_code`, `role_type`, `system_flag`, `legacy_role_code`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('rl108','sp708xm02q','TENANT',1,NULL,'商家管理员','shop_admin_tj_kwj',1,'1',1,1,'0','0','wczladmin','2026-08-07 10:13:07','',NULL,'平台创建的内置商家管理员角色');
INSERT IGNORE INTO `sys_role` (`role_code`, `shop_code`, `role_type`, `system_flag`, `legacy_role_code`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('rl109','sp708xm02u','TENANT',1,NULL,'商家管理员','shop_admin_tj_kc',1,'1',1,1,'0','0','wczladmin','2026-08-07 10:13:07','',NULL,'平台创建的内置商家管理员角色');
INSERT IGNORE INTO `sys_role` (`role_code`, `shop_code`, `role_type`, `system_flag`, `legacy_role_code`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('rl110','sp708xm02r','TENANT',1,NULL,'商家管理员','shop_admin_cd_rt',1,'1',1,1,'0','0','wczladmin','2026-08-07 10:13:07','',NULL,'平台创建的内置商家管理员角色');
INSERT IGNORE INTO `sys_role` (`role_code`, `shop_code`, `role_type`, `system_flag`, `legacy_role_code`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('rl2','','PLATFORM',0,NULL,'普通角色','common',2,'2',1,1,'0','0','admin','2026-07-21 11:18:21','',NULL,'普通角色');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5019');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5092');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn5093');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn2018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn3034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5019');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5039');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5046');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5047');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5048');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5049');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5056');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5057');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5058');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5059');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5060');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5061');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5062');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5063');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5064');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5069');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5070');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5071');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5072');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5073');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5074');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5075');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5076');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5080');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5081');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5082');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5083');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5084');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5085');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5086');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5087');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5088');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5089');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5090');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5091');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5092');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5093');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5094');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5095');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5096');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5097');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5098');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5099');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5100');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5101');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5102');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5103');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5104');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5105');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5106');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn5107');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6009');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn6014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn7032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn2018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn3034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5019');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5039');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5046');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5047');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5048');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5049');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5056');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5057');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5058');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5059');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5060');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5061');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5062');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5063');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5064');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5069');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5070');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5071');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5072');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5073');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5074');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5075');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5076');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5080');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5081');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5082');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5083');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5084');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5085');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5086');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5087');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5088');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5089');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5090');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5091');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5092');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5093');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5094');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5095');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5096');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5097');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5098');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5099');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5100');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5101');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5102');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5103');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5104');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5105');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5106');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn5107');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6009');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn6014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn7032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn2018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn3034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5019');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5039');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5046');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5047');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5048');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5049');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5056');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5057');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5058');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5059');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5060');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5061');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5062');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5063');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5064');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5069');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5070');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5071');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5072');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5073');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5074');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5075');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5076');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5080');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5081');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5082');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5083');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5084');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5085');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5086');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5087');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5088');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5089');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5090');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5091');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5092');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5093');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5094');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5095');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5096');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5097');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5098');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5099');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5100');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5101');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5102');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5103');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5104');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5105');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5106');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn5107');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6009');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn6014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn7032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn100');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1005');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1006');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1007');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1008');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1009');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn101');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1010');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1011');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1012');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1013');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1014');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1015');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1016');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1017');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1018');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1019');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn102');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1020');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1021');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1022');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1023');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1024');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1025');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1026');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1027');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1028');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1029');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn103');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1030');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1031');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1032');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1033');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1034');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1035');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1036');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1037');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1038');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1039');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn104');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1046');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1047');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1048');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1049');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn105');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1050');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1051');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1052');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1053');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1054');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1055');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1056');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1057');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1058');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1059');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn106');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn1060');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn107');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn108');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn109');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn110');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn111');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn112');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn113');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn114');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn115');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn116');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn117');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn2');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn2000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn2001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn3');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn500');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5000');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5001');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5002');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5003');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5004');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn501');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5080');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5081');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5082');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5083');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5084');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5085');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5086');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5087');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5088');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5089');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5090');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5091');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5093');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5094');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5095');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5096');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn5097');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn9040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn9041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn9042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn9040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn9041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn9042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn9042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn9042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9040');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9041');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn9042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn8042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn8042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8042');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn8043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn8044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl1','fn8045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn8043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn8044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl2','fn8045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl108','fn8045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl109','fn8045');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8043');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8044');
INSERT IGNORE INTO `sys_role_menu` (`role_code`, `fun_code`) VALUES ('rl110','fn8045');
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
INSERT IGNORE INTO `sys_role_dept` (`role_code`, `dept_id`) VALUES ('rl2',100);
INSERT IGNORE INTO `sys_role_dept` (`role_code`, `dept_id`) VALUES ('rl2',101);
INSERT IGNORE INTO `sys_role_dept` (`role_code`, `dept_id`) VALUES ('rl2',105);
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT IGNORE INTO `sys_user` (`user_code`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `pwd_update_date`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('us1',NULL,'wczladmin','超级管理员','00','','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','',NULL,NULL,'admin','2026-08-07 10:13:07','',NULL,NULL);
INSERT IGNORE INTO `sys_user` (`user_code`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `pwd_update_date`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('us208XQ02',NULL,'us208XQ02','商家管理员','00','','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','',NULL,NULL,'wczladmin','2026-08-12 08:28:21','',NULL,NULL);
INSERT IGNORE INTO `sys_user` (`user_code`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `pwd_update_date`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES ('us208XQ03',NULL,'us208XQ03','商家管理员','00','','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','',NULL,NULL,'wczladmin','2026-08-12 09:55:29','',NULL,NULL);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT IGNORE INTO `sys_user_role` (`user_code`, `role_code`) VALUES ('us1','rl1');
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT IGNORE INTO `sys_user_post` (`user_code`, `post_id`) VALUES ('us1',1);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;

/*!40000 ALTER TABLE `s_m_shop` DISABLE KEYS */;
INSERT IGNORE INTO `s_m_shop` (`shop_code`, `shop_name`, `province`, `city`, `district`, `address`, `telephone`, `description`, `image_url`, `console_url`, `createtime`, `updatetime`, `status`) VALUES ('sp708xm02q','天津康万家','天津市','','','','','天津康万家科技有限公司','',NULL,'2026-08-07 10:13:07','2026-08-08 19:27:20',1);
INSERT IGNORE INTO `s_m_shop` (`shop_code`, `shop_name`, `province`, `city`, `district`, `address`, `telephone`, `description`, `image_url`, `console_url`, `createtime`, `updatetime`, `status`) VALUES ('sp708xm02r','常德睿腾','湖南省','常德市','','','','常德睿腾众联科技有限公司','',NULL,'2026-08-07 10:13:07','2026-08-10 17:31:14',1);
INSERT IGNORE INTO `s_m_shop` (`shop_code`, `shop_name`, `province`, `city`, `district`, `address`, `telephone`, `description`, `image_url`, `console_url`, `createtime`, `updatetime`, `status`) VALUES ('sp708xm02u','天津康晨','天津市','','','','','天津康晨电子有限公司','',NULL,'2026-08-07 10:13:07','2026-08-08 19:31:04',1);
/*!40000 ALTER TABLE `s_m_shop` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_shop_menu` DISABLE KEYS */;
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn2007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-13 16:12:53','',NULL,'fn2008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-12 17:29:28','',NULL,'fn2010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-12 17:29:28','',NULL,'fn2011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-12 17:29:28','',NULL,'fn2012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-12 17:29:28','',NULL,'fn2013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-12 17:29:28','',NULL,'fn2018');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5018');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5019');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5024');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5025');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5026');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5027');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5028');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5029');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5035');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5036');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5037');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5038');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5039');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5040');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5041');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5043');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5044');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5045');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5046');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5047');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5048');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5049');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5050');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5051');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5052');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5053');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5054');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5055');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5056');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5057');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5058');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5059');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5060');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5061');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5062');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5063');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5064');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5069');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5070');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5071');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5072');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5073');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5074');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5075');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5076');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5080');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5081');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5082');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5083');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5084');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5085');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5086');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5087');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5088');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-08-12 11:14:17','',NULL,'fn5089');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 16:27:43','',NULL,'fn5090');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 09:16:21','',NULL,'fn5091');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 09:16:21','',NULL,'fn5092');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 09:16:21','',NULL,'fn5093');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 14:26:53','',NULL,'fn5094');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 14:26:53','',NULL,'fn5095');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 14:26:53','',NULL,'fn5096');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-18 14:26:53','',NULL,'fn5097');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:22:22','',NULL,'fn5098');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:22:22','',NULL,'fn5099');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:22:22','',NULL,'fn5100');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:22:22','',NULL,'fn5101');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:22:22','',NULL,'fn5102');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-28 09:34:30','',NULL,'fn5103');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-28 09:34:30','',NULL,'fn5104');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-28 09:34:30','',NULL,'fn5105');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-28 09:34:30','',NULL,'fn5106');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-28 09:34:30','',NULL,'fn5107');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 11:47:04','',NULL,'fn6006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 17:11:09','',NULL,'fn6007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 17:11:09','',NULL,'fn6008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 17:11:09','',NULL,'fn6009');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:22:22','',NULL,'fn6010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:22:22','',NULL,'fn6011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:22:22','',NULL,'fn6012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:22:22','',NULL,'fn6013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:22:22','',NULL,'fn6014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-14 17:07:48','',NULL,'fn7032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-19 16:35:18','',NULL,'fn8016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:24:17','',NULL,'fn8020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:24:17','',NULL,'fn8021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:24:17','',NULL,'fn8022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:24:04','',NULL,'fn8023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 10:24:04','',NULL,'fn8024');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:07','',NULL,'fn8025');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:07','',NULL,'fn8026');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:07','',NULL,'fn8027');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:07','',NULL,'fn8028');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:07','',NULL,'fn8029');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:26','',NULL,'fn8030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:26','',NULL,'fn8031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:26','',NULL,'fn8032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-21 16:39:26','',NULL,'fn8033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:27:28','',NULL,'fn8034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:27:28','',NULL,'fn8035');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:27:28','',NULL,'fn8036');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:27:28','',NULL,'fn8037');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 11:27:28','',NULL,'fn8038');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 10:18:23','',NULL,'fn8050');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 10:18:23','',NULL,'fn8051');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 10:18:23','',NULL,'fn8052');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 10:18:23','',NULL,'fn8053');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 10:18:23','',NULL,'fn8054');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-24 10:18:23','',NULL,'fn8055');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-25 14:29:45','',NULL,'fn9000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-25 14:29:45','',NULL,'fn9001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-25 14:29:45','',NULL,'fn9002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-25 14:29:45','',NULL,'fn9003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-25 14:29:45','',NULL,'fn9004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','admin','2026-08-25 14:29:45','',NULL,'fn9005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn2007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-13 16:12:53','',NULL,'fn2008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-12 17:29:28','',NULL,'fn2010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-12 17:29:28','',NULL,'fn2011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-12 17:29:28','',NULL,'fn2012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-12 17:29:28','',NULL,'fn2013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-12 17:29:28','',NULL,'fn2018');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5018');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5019');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5024');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5025');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5026');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5027');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5028');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5029');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5035');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5036');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5037');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5038');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5039');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5040');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5041');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5043');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5044');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5045');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5046');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5047');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5048');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5049');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5050');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5051');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5052');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5053');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5054');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5055');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5056');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5057');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5058');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5059');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5060');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5061');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5062');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5063');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5064');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5069');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5070');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5071');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5072');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5073');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5074');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5075');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-08-11 14:32:33','',NULL,'fn5076');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5080');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5081');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5082');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5083');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5084');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5085');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5086');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5087');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5088');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-11 15:05:09','',NULL,'fn5089');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 16:27:43','',NULL,'fn5090');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 09:16:21','',NULL,'fn5091');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 09:16:21','',NULL,'fn5092');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 09:16:21','',NULL,'fn5093');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 14:26:53','',NULL,'fn5094');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 14:26:53','',NULL,'fn5095');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 14:26:53','',NULL,'fn5096');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-18 14:26:53','',NULL,'fn5097');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:22:22','',NULL,'fn5098');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:22:22','',NULL,'fn5099');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:22:22','',NULL,'fn5100');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:22:22','',NULL,'fn5101');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:22:22','',NULL,'fn5102');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-28 09:34:30','',NULL,'fn5103');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-28 09:34:30','',NULL,'fn5104');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-28 09:34:30','',NULL,'fn5105');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-28 09:34:30','',NULL,'fn5106');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-28 09:34:30','',NULL,'fn5107');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 11:47:04','',NULL,'fn6006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 17:11:09','',NULL,'fn6007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 17:11:09','',NULL,'fn6008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 17:11:09','',NULL,'fn6009');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:22:22','',NULL,'fn6010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:22:22','',NULL,'fn6011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:22:22','',NULL,'fn6012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:22:22','',NULL,'fn6013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:22:22','',NULL,'fn6014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-14 17:07:48','',NULL,'fn7032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-19 16:35:18','',NULL,'fn8016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:24:17','',NULL,'fn8020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:24:17','',NULL,'fn8021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:24:17','',NULL,'fn8022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:24:04','',NULL,'fn8023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 10:24:04','',NULL,'fn8024');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:07','',NULL,'fn8025');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:07','',NULL,'fn8026');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:07','',NULL,'fn8027');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:07','',NULL,'fn8028');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:07','',NULL,'fn8029');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:26','',NULL,'fn8030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:26','',NULL,'fn8031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:26','',NULL,'fn8032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-21 16:39:26','',NULL,'fn8033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:27:28','',NULL,'fn8034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:27:28','',NULL,'fn8035');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:27:28','',NULL,'fn8036');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:27:28','',NULL,'fn8037');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 11:27:28','',NULL,'fn8038');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 10:18:23','',NULL,'fn8050');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 10:18:23','',NULL,'fn8051');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 10:18:23','',NULL,'fn8052');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 10:18:23','',NULL,'fn8053');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 10:18:23','',NULL,'fn8054');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-24 10:18:23','',NULL,'fn8055');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-25 14:29:45','',NULL,'fn9000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-25 14:29:45','',NULL,'fn9001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-25 14:29:45','',NULL,'fn9002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-25 14:29:45','',NULL,'fn9003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-25 14:29:45','',NULL,'fn9004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','admin','2026-08-25 14:29:45','',NULL,'fn9005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn2007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-13 16:12:53','',NULL,'fn2008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-12 17:29:28','',NULL,'fn2010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-12 17:29:28','',NULL,'fn2011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-12 17:29:28','',NULL,'fn2012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-12 17:29:28','',NULL,'fn2013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-12 17:29:28','',NULL,'fn2018');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-07 10:13:07','',NULL,'fn3034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5018');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5019');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5024');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5025');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5026');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5027');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5028');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5029');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','',NULL,'fn5030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5035');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5036');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5037');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5038');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5039');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5040');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5041');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5043');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5044');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5045');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-08-08 10:19:49','admin','2026-08-11 14:11:33','fn5046');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:36','admin','2026-08-11 14:11:33','fn5047');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:36','admin','2026-08-11 14:11:33','fn5048');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:36','admin','2026-08-11 14:11:33','fn5049');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:36','admin','2026-08-11 14:11:33','fn5050');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5051');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5052');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5053');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5054');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5055');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5056');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5057');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5058');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5059');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5060');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5061');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5062');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5063');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5064');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5069');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5070');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5071');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5072');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5073');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5074');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5075');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 14:09:56','admin','2026-08-11 14:11:33','fn5076');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5080');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5081');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5082');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5083');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5084');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5085');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5086');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5087');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5088');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-11 15:05:09','',NULL,'fn5089');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 16:27:43','',NULL,'fn5090');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 09:16:21','',NULL,'fn5091');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 09:16:21','',NULL,'fn5092');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 09:16:21','',NULL,'fn5093');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 14:26:53','',NULL,'fn5094');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 14:26:53','',NULL,'fn5095');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 14:26:53','',NULL,'fn5096');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-18 14:26:53','',NULL,'fn5097');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:22:22','',NULL,'fn5098');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:22:22','',NULL,'fn5099');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:22:22','',NULL,'fn5100');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:22:22','',NULL,'fn5101');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:22:22','',NULL,'fn5102');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-28 09:34:30','',NULL,'fn5103');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-28 09:34:30','',NULL,'fn5104');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-28 09:34:30','',NULL,'fn5105');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-28 09:34:30','',NULL,'fn5106');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-28 09:34:30','',NULL,'fn5107');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 11:47:04','',NULL,'fn6006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 17:11:09','',NULL,'fn6007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 17:11:09','',NULL,'fn6008');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 17:11:09','',NULL,'fn6009');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:22:22','',NULL,'fn6010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:22:22','',NULL,'fn6011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:22:22','',NULL,'fn6012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:22:22','',NULL,'fn6013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:22:22','',NULL,'fn6014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-14 17:07:48','',NULL,'fn7032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8006');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8007');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8010');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8011');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8012');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8013');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8014');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8017');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8015');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-19 16:35:18','',NULL,'fn8016');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:24:17','',NULL,'fn8020');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:24:17','',NULL,'fn8021');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:24:17','',NULL,'fn8022');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:24:04','',NULL,'fn8023');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 10:24:04','',NULL,'fn8024');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:07','',NULL,'fn8025');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:07','',NULL,'fn8026');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:07','',NULL,'fn8027');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:07','',NULL,'fn8028');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:07','',NULL,'fn8029');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:26','',NULL,'fn8030');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:26','',NULL,'fn8031');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:26','',NULL,'fn8032');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-21 16:39:26','',NULL,'fn8033');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:27:28','',NULL,'fn8034');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:27:28','',NULL,'fn8035');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:27:28','',NULL,'fn8036');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:27:28','',NULL,'fn8037');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 11:27:28','',NULL,'fn8038');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 10:18:23','',NULL,'fn8050');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 10:18:23','',NULL,'fn8051');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 10:18:23','',NULL,'fn8052');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 10:18:23','',NULL,'fn8053');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 10:18:23','',NULL,'fn8054');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-24 10:18:23','',NULL,'fn8055');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-25 14:29:45','',NULL,'fn9000');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-25 14:29:45','',NULL,'fn9001');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-25 14:29:45','',NULL,'fn9002');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-25 14:29:45','',NULL,'fn9003');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-25 14:29:45','',NULL,'fn9004');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','admin','2026-08-25 14:29:45','',NULL,'fn9005');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9040');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9041');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9040');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9041');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9040');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9041');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-10 00:00:00','',NULL,'fn9042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8042');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8043');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8044');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02q','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8045');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8043');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8044');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02r','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8045');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8043');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8044');
INSERT IGNORE INTO `sys_shop_menu` (`shop_code`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `fun_code`) VALUES ('sp708xm02u','0','wczladmin','2026-09-11 00:00:00','',NULL,'fn8045');
/*!40000 ALTER TABLE `sys_shop_menu` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_shop_user` DISABLE KEYS */;
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02q','us1',218,NULL,'0',0,'0','migration','2026-08-07 10:35:18','restore','2026-08-07 17:31:52');
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02q','us208XQ02',218,NULL,'1',0,'0','wczladmin','2026-08-12 08:28:22','wczladmin','2026-08-12 09:55:29');
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02q','us208XQ03',218,NULL,'0',1,'0','wczladmin','2026-08-12 09:55:29','wczladmin','2026-08-12 09:55:29');
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02r','us1',220,NULL,'0',0,'0','migration','2026-08-07 10:35:18','restore','2026-08-07 17:31:52');
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02r','us208XQ02',220,NULL,'0',1,'0','wczladmin','2026-08-12 08:29:27','wczladmin','2026-08-12 08:29:27');
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02u','us1',219,NULL,'0',0,'0','migration','2026-08-07 10:35:18','restore','2026-08-07 17:31:52');
INSERT IGNORE INTO `sys_shop_user` (`shop_code`, `user_code`, `dept_id`, `job_no`, `status`, `is_owner`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('sp708xm02u','us208XQ02',219,NULL,'0',1,'0','wczladmin','2026-08-12 08:30:27','wczladmin','2026-08-12 08:30:27');
/*!40000 ALTER TABLE `sys_shop_user` ENABLE KEYS */;

/*!40000 ALTER TABLE `sys_shop_user_role` DISABLE KEYS */;
INSERT IGNORE INTO `sys_shop_user_role` (`shop_code`, `user_code`, `role_code`, `create_by`, `create_time`) VALUES ('sp708xm02q','us1','rl108','migration','2026-08-07 10:35:18');
INSERT IGNORE INTO `sys_shop_user_role` (`shop_code`, `user_code`, `role_code`, `create_by`, `create_time`) VALUES ('sp708xm02q','us208XQ03','rl108','wczladmin','2026-08-12 09:55:29');
INSERT IGNORE INTO `sys_shop_user_role` (`shop_code`, `user_code`, `role_code`, `create_by`, `create_time`) VALUES ('sp708xm02r','us1','rl110','migration','2026-08-07 10:35:18');
INSERT IGNORE INTO `sys_shop_user_role` (`shop_code`, `user_code`, `role_code`, `create_by`, `create_time`) VALUES ('sp708xm02r','us208XQ02','rl110','wczladmin','2026-08-12 08:29:27');
INSERT IGNORE INTO `sys_shop_user_role` (`shop_code`, `user_code`, `role_code`, `create_by`, `create_time`) VALUES ('sp708xm02u','us1','rl109','migration','2026-08-07 10:35:18');
INSERT IGNORE INTO `sys_shop_user_role` (`shop_code`, `user_code`, `role_code`, `create_by`, `create_time`) VALUES ('sp708xm02u','us208XQ02','rl109','wczladmin','2026-08-12 08:30:27');
/*!40000 ALTER TABLE `sys_shop_user_role` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET FOREIGN_KEY_CHECKS = 1;
USE `sdsshop`;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40000 ALTER TABLE `u_m_doctypeconfig` DISABLE KEYS */;
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('CGTHCK','采购退货出库','O',1,0,'退回供应商');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('CK','销售出库','O',1,0,'销售出库；单据号前缀与类型统一为CK');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('DB','仓库调拨','N',1,0,'仓库调拨；单据号前缀与类型统一为DB');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('GH','归还','N',1,0,'GH + 小时级 Base10 数字编码；同仓库状态转换：借出中库存减少，正常库存增加（3 -> 1）');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('JC','借出','N',1,0,'JC + 小时级 Base10 数字编码；同仓库状态转换：正常库存减少，借出中库存增加（1 -> 3）');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('LLCK','领料出库','O',1,0,'生产工单领料，从物料仓库出库');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('OO','其他出库','O',1,0,'出库页已有该类型，补齐配置');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('RK','采购入库','I',1,0,'采购入库；单据号前缀与类型统一为RK');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SCCPRK','生产成品入库','I',1,0,'生产工单完工，成品入库');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SCLL','生产领料','N',1,0,'良品·在库 → 良品·不在库，料领到车间就算离库');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SCTL','生产退料','N',1,0,'良品·不在库 → 良品·在库，坏了的料另开损坏登记');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SCTLRK','生产退料入库','I',1,0,'生产工单退料，未耗用物料退回仓库');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SCXH','生产消耗','O',1,0,'从车间的在制品（良品·不在库）永久出库，工单物料成本来源');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SHCK','损坏出库','O',1,0,'损坏商品直接报废出库，来源为不良品·在库');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SHDJ','损坏','N',1,0,'SH + 小时级 Base10 数字编码；1/3/4 -> 2');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('SX','送修','N',1,0,'WX + 小时级 Base10 数字编码；损坏库存转为维修中（2 -> 4）');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('WXGH','维修归还','N',1,0,'WX + 小时级 Base10 数字编码；4 -> 1/2');
INSERT IGNORE INTO `u_m_doctypeconfig` (`doctype`, `doctypename`, `direction`, `needconfirmflag`, `allownegativeflag`, `remark`) VALUES ('XSTHRK','销售退货入库','I',1,0,'客户退回已发出的货，成本按原出库实际成本还回；由订单模块销售退货调用');
/*!40000 ALTER TABLE `u_m_doctypeconfig` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


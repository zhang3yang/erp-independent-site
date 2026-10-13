-- 销售订单外贸字段（2026-09-10）。可重复执行，跑两遍结果一样。
--
-- ============================================================================
-- 改了什么
-- ============================================================================
--
-- 销售订单原来只有一套国内收件信息，做出口的单子填不下去：客户在哪个国家、
-- 国外那段地址是什么、国内这段货是发货代还是发客户还是客户自己来提、要不要唛头，
-- 这些都没有地方放。加七列：
--
--   buyercountry  客户国家，取值是字典 order_country 的键（ISO 3166-1 alpha-2）
--   buyeraddress  客户地址。国外地址不结构化，整段自由文本存着就行
--   receivetype   国内收货方式：货代地址 / 客户地址 / 上门提货
--   markneeded    要不要唛头。只有走货代才谈得上，别的方式恒为 0
--   markfile      唛头文件，JSON 数组：name 是上传时的源文件名，url 是相对路径
--   descitems     商品描述清单，JSON 数组，销售自由填写的名称/数量/单价
--   orderfiles    订单资料，与客户来回确认的文档，格式同 markfile
--
-- 附件为什么连源文件名一起存：只存 url 的话，页面上只能显示服务端改写过的存储名
-- （原名后面缀了序列号，走 OSS 时更是整段换掉），而用户要看的是自己上传时的那个
-- 文件名。存储名怎么定跟显示无关，名字跟着一起存就行。
--
-- 另外建一个 order_country 字典，242 个国家/地区，中文名 + 两位代码。
--
-- ============================================================================
-- descitems 为什么是 JSON 列，不是一张明细月表
-- ============================================================================
--
-- 这张清单是销售写给客户和生产看的口径说明：一行只有名称、数量、单价，
-- 不挂商品编码、不占库存、不参与发货、也<b>不进货款合计</b>（货款仍旧只按
-- 货品明细算）。既然它不跟任何东西关联，开一张 u_o_orderdesc_YYYYMM 月表
-- 换来的只是多一套 DDL、mapper 和跨月清理逻辑。
--
-- 同库里已有先例：u_p_production.orderallocation 也是 JSON 文本。
-- 服务层写入前会整体解析校验一遍再序列化回去，脏数据进不来。
--
-- ============================================================================
-- ⚠ 历史月表必须靠这个脚本刷
-- ============================================================================
--
-- MonthlyTableInitializer 的 ADDED_COLUMNS 只补它启动时碰到的当月和下月，
-- 更早的月表它不会打开。而列表查询是把所有月表 UNION 起来的——少一张表少一列，
-- 整个列表页直接报 Unknown column。所以本脚本按 information_schema 遍历全部月表。
--
-- ============================================================================
-- 执行（本地和远程都要跑）
-- ============================================================================
--   mysql -h 127.0.0.1       -uroot -p --default-character-set=utf8mb4 < 20260910_sales_order_export_fields.sql
--   mysql -h 124.221.220.89  -uroot -p --default-character-set=utf8mb4 < 20260910_sales_order_export_fields.sql

SET NAMES utf8mb4;

-- ============================================================
-- 1. 订单主表月表加列
--    列定义与 MonthlyTableInitializer.buildOrderDDL / ADDED_COLUMNS 逐字对齐，
--    改这里就要改那边，否则下个月 1 号新建的表和这个月的对不上。
-- ============================================================

DROP PROCEDURE IF EXISTS sdsorder.add_order_export_columns;
DELIMITER $$
CREATE PROCEDURE sdsorder.add_order_export_columns()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);

  DECLARE cur_order CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur_order;
  ord_loop: LOOP
    FETCH cur_order INTO tname;
    IF done = 1 THEN LEAVE ord_loop; END IF;

    -- 缺哪列拼哪列，最后一条 ALTER 打包执行：月表行数不小，能一次改完就别改七次
    SET @cols = '';

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'buyercountry') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `buyercountry` varchar(10) DEFAULT NULL',
        ' COMMENT ''客户国家（ISO 3166-1 alpha-2，取值见字典 order_country）'' AFTER `buyer2image`');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'buyeraddress') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `buyeraddress` varchar(500) DEFAULT NULL',
        ' COMMENT ''客户地址（国外，不结构化，整段自由文本）'' AFTER `buyercountry`');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'receivetype') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `receivetype` varchar(20) NOT NULL DEFAULT ''货代地址''',
        ' COMMENT ''国内收货方式：货代地址/客户地址/上门提货'' AFTER `receivezipcode`');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'markneeded') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `markneeded` tinyint(1) NOT NULL DEFAULT 0',
        ' COMMENT ''是否需要唛头：1=需要。只有 receivetype=货代地址 时才有意义，其余方式恒为 0''',
        ' AFTER `receivetype`');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'markfile') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `markfile` varchar(1000) DEFAULT NULL',
        ' COMMENT ''唛头文件。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）；markneeded=1 时才有值'' AFTER `markneeded`');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'descitems') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `descitems` text',
        ' COMMENT ''商品描述清单（JSON 数组：名称/数量/单价），只作记录，不参与货款合计'' AFTER `remark`');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'orderfiles') = 0 THEN
      SET @cols = CONCAT(@cols, ', ADD COLUMN `orderfiles` text',
        ' COMMENT ''订单资料（与客户来回确认的文档）。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）'' AFTER `descitems`');
    END IF;

    IF @cols <> '' THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '` ', SUBSTRING(@cols, 3));
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur_order;
END$$
DELIMITER ;

CALL sdsorder.add_order_export_columns();
DROP PROCEDURE IF EXISTS sdsorder.add_order_export_columns;

-- ------------------------------------------------------------
-- 1.1 附件列和收件电话放宽
--
-- 本脚本的上一版把 markfile 建成 varchar(500)、orderfiles 建成 varchar(2000)，
-- 那时候只存 url。改成连源文件名一起存之后，一条记录长了一倍不止，十个附件的
-- 订单资料装不下。已经跑过上一版的库靠这一步补齐；没跑过的上面已经按新定义建好，
-- 这里查一下类型就跳过。
--
-- receivetelephone 一起放宽：它原来是 varchar(50)，那是照 11 位手机号给的宽度。
-- 现在收件电话不再限 11 位（平台下单给的是虚拟号，位数不固定；客户留的可能是带
-- 区号的固话；也常常一次留两三个号码用逗号分隔），50 位装不下。口径见 ContactPhoneUtils。
-- ------------------------------------------------------------

DROP PROCEDURE IF EXISTS sdsorder.widen_order_file_columns;
DELIMITER $$
CREATE PROCEDURE sdsorder.widen_order_file_columns()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);

  DECLARE cur_order CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur_order;
  ord_loop: LOOP
    FETCH cur_order INTO tname;
    IF done = 1 THEN LEAVE ord_loop; END IF;

    SET @cols = '';

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'markfile'
           AND (data_type <> 'varchar' OR character_maximum_length < 1000)) > 0 THEN
      SET @cols = CONCAT(@cols, ', MODIFY COLUMN `markfile` varchar(1000) DEFAULT NULL',
        ' COMMENT ''唛头文件。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）；markneeded=1 时才有值''');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'orderfiles' AND data_type <> 'text') > 0 THEN
      SET @cols = CONCAT(@cols, ', MODIFY COLUMN `orderfiles` text',
        ' COMMENT ''订单资料（与客户来回确认的文档）。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）''');
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'sdsorder' AND table_name = tname
           AND column_name = 'receivetelephone' AND character_maximum_length < 100) > 0 THEN
      SET @cols = CONCAT(@cols, ', MODIFY COLUMN `receivetelephone` varchar(100) NOT NULL',
        ' COMMENT ''收件电话（可以是虚拟号、固话，或逗号/分号分隔的多个号码）''');
    END IF;

    IF @cols <> '' THEN
      SET @s = CONCAT('ALTER TABLE `sdsorder`.`', tname, '` ', SUBSTRING(@cols, 3));
      PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur_order;
END$$
DELIMITER ;

CALL sdsorder.widen_order_file_columns();
DROP PROCEDURE IF EXISTS sdsorder.widen_order_file_columns;

-- 销售补发的联系电话是从原订单原样带过来的（SalesReshipServiceImpl.create），
-- 订单那边放宽了这边不放宽，补发单一保存就会被截断。它不是月表，单独改一次。
SET @ddl := IF(
  (SELECT character_maximum_length FROM information_schema.columns
    WHERE table_schema = 'sdsorder' AND table_name = 'u_o_sales_reship'
      AND column_name = 'receivetelephone') >= 100,
  'SELECT ''u_o_sales_reship.receivetelephone 已经够宽，跳过'' AS msg',
  'ALTER TABLE `sdsorder`.`u_o_sales_reship`
     MODIFY COLUMN `receivetelephone` varchar(100) NOT NULL
     COMMENT ''联系电话（可以是虚拟号、固话，或逗号/分号分隔的多个号码）''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 客户国家字典 order_country
--    先整型删干净再重灌：这是一份参照表，没有业务数据挂在 dict_code 上，
--    重灌比逐条 upsert 简单，也保证跑完之后就是脚本里这一份。
-- ============================================================

INSERT INTO `sdssys`.`sys_dict_type`
  (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '客户国家', 'order_country', '0', 'admin', now(),
       '销售订单的客户国家，ISO 3166-1 alpha-2 两位代码 + 中文名'
 WHERE NOT EXISTS (SELECT 1 FROM `sdssys`.`sys_dict_type` WHERE `dict_type` = 'order_country');

DELETE FROM `sdssys`.`sys_dict_data` WHERE `dict_type` = 'order_country';

USE `sdssys`;

INSERT INTO `sys_dict_data`
  (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`,
   `list_class`, `is_default`, `status`, `create_by`, `create_time`)
VALUES
  (1, '中国', 'CN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (2, '美国', 'US', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (3, '英国', 'GB', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (4, '德国', 'DE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (5, '法国', 'FR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (6, '意大利', 'IT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (7, '西班牙', 'ES', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (8, '荷兰', 'NL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (9, '加拿大', 'CA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (10, '澳大利亚', 'AU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (11, '日本', 'JP', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (12, '韩国', 'KR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (13, '俄罗斯', 'RU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (14, '墨西哥', 'MX', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (15, '巴西', 'BR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (16, '印度', 'IN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (17, '阿拉伯联合酋长国', 'AE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (18, '沙特阿拉伯', 'SA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (19, '新加坡', 'SG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (20, '马来西亚', 'MY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (100, '阿尔巴尼亚', 'AL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (101, '阿尔及利亚', 'DZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (102, '阿富汗', 'AF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (103, '阿根廷', 'AR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (104, '阿鲁巴', 'AW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (105, '阿曼', 'OM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (106, '阿塞拜疆', 'AZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (107, '埃及', 'EG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (108, '埃塞俄比亚', 'ET', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (109, '爱尔兰', 'IE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (110, '爱沙尼亚', 'EE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (111, '安道尔', 'AD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (112, '安哥拉', 'AO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (113, '安圭拉', 'AI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (114, '安提瓜和巴布达', 'AG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (115, '奥地利', 'AT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (116, '奥兰群岛', 'AX', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (117, '巴巴多斯', 'BB', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (118, '巴布亚新几内亚', 'PG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (119, '巴哈马', 'BS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (120, '巴基斯坦', 'PK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (121, '巴拉圭', 'PY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (122, '巴勒斯坦领土', 'PS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (123, '巴林', 'BH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (124, '巴拿马', 'PA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (125, '白俄罗斯', 'BY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (126, '百慕大', 'BM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (127, '保加利亚', 'BG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (128, '北马里亚纳群岛', 'MP', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (129, '北马其顿', 'MK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (130, '贝宁', 'BJ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (131, '比利时', 'BE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (132, '冰岛', 'IS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (133, '波多黎各', 'PR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (134, '波兰', 'PL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (135, '波斯尼亚和黑塞哥维那', 'BA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (136, '玻利维亚', 'BO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (137, '伯利兹', 'BZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (138, '博茨瓦纳', 'BW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (139, '不丹', 'BT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (140, '布基纳法索', 'BF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (141, '布隆迪', 'BI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (142, '朝鲜', 'KP', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (143, '赤道几内亚', 'GQ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (144, '丹麦', 'DK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (145, '东帝汶', 'TL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (146, '多哥', 'TG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (147, '多米尼加共和国', 'DO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (148, '多米尼克', 'DM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (149, '厄瓜多尔', 'EC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (150, '厄立特里亚', 'ER', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (151, '法罗群岛', 'FO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (152, '法属波利尼西亚', 'PF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (153, '法属圭亚那', 'GF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (154, '法属圣马丁', 'MF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (155, '梵蒂冈', 'VA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (156, '菲律宾', 'PH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (157, '斐济', 'FJ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (158, '芬兰', 'FI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (159, '佛得角', 'CV', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (160, '福克兰群岛', 'FK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (161, '冈比亚', 'GM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (162, '刚果（布）', 'CG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (163, '刚果（金）', 'CD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (164, '哥伦比亚', 'CO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (165, '哥斯达黎加', 'CR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (166, '格林纳达', 'GD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (167, '格陵兰', 'GL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (168, '格鲁吉亚', 'GE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (169, '根西岛', 'GG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (170, '古巴', 'CU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (171, '瓜德罗普', 'GP', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (172, '关岛', 'GU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (173, '圭亚那', 'GY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (174, '哈萨克斯坦', 'KZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (175, '海地', 'HT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (176, '荷属加勒比区', 'BQ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (177, '荷属圣马丁', 'SX', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (178, '黑山', 'ME', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (179, '洪都拉斯', 'HN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (180, '基里巴斯', 'KI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (181, '吉布提', 'DJ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (182, '吉尔吉斯斯坦', 'KG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (183, '几内亚', 'GN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (184, '几内亚比绍', 'GW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (185, '加纳', 'GH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (186, '加蓬', 'GA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (187, '柬埔寨', 'KH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (188, '捷克', 'CZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (189, '津巴布韦', 'ZW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (190, '喀麦隆', 'CM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (191, '卡塔尔', 'QA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (192, '开曼群岛', 'KY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (193, '科科斯（基林）群岛', 'CC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (194, '科摩罗', 'KM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (195, '科特迪瓦', 'CI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (196, '科威特', 'KW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (197, '克罗地亚', 'HR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (198, '肯尼亚', 'KE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (199, '库克群岛', 'CK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (200, '库拉索', 'CW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (201, '拉脱维亚', 'LV', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (202, '莱索托', 'LS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (203, '老挝', 'LA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (204, '黎巴嫩', 'LB', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (205, '立陶宛', 'LT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (206, '利比里亚', 'LR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (207, '利比亚', 'LY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (208, '列支敦士登', 'LI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (209, '留尼汪', 'RE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (210, '卢森堡', 'LU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (211, '卢旺达', 'RW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (212, '罗马尼亚', 'RO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (213, '马达加斯加', 'MG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (214, '马恩岛', 'IM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (215, '马尔代夫', 'MV', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (216, '马耳他', 'MT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (217, '马拉维', 'MW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (218, '马里', 'ML', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (219, '马绍尔群岛', 'MH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (220, '马提尼克', 'MQ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (221, '马约特', 'YT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (222, '毛里求斯', 'MU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (223, '毛里塔尼亚', 'MR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (224, '美属萨摩亚', 'AS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (225, '美属维尔京群岛', 'VI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (226, '蒙古', 'MN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (227, '蒙特塞拉特', 'MS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (228, '孟加拉国', 'BD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (229, '秘鲁', 'PE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (230, '密克罗尼西亚', 'FM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (231, '缅甸', 'MM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (232, '摩尔多瓦', 'MD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (233, '摩洛哥', 'MA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (234, '摩纳哥', 'MC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (235, '莫桑比克', 'MZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (236, '纳米比亚', 'NA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (237, '南非', 'ZA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (238, '南苏丹', 'SS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (239, '瑙鲁', 'NR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (240, '尼加拉瓜', 'NI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (241, '尼泊尔', 'NP', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (242, '尼日尔', 'NE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (243, '尼日利亚', 'NG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (244, '纽埃', 'NU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (245, '挪威', 'NO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (246, '诺福克岛', 'NF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (247, '帕劳', 'PW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (248, '皮特凯恩群岛', 'PN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (249, '葡萄牙', 'PT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (250, '瑞典', 'SE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (251, '瑞士', 'CH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (252, '萨尔瓦多', 'SV', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (253, '萨摩亚', 'WS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (254, '塞尔维亚', 'RS', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (255, '塞拉利昂', 'SL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (256, '塞内加尔', 'SN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (257, '塞浦路斯', 'CY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (258, '塞舌尔', 'SC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (259, '圣巴泰勒米', 'BL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (260, '圣诞岛', 'CX', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (261, '圣多美和普林西比', 'ST', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (262, '圣赫勒拿', 'SH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (263, '圣基茨和尼维斯', 'KN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (264, '圣卢西亚', 'LC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (265, '圣马力诺', 'SM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (266, '圣皮埃尔和密克隆群岛', 'PM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (267, '圣文森特和格林纳丁斯', 'VC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (268, '斯里兰卡', 'LK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (269, '斯洛伐克', 'SK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (270, '斯洛文尼亚', 'SI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (271, '斯威士兰', 'SZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (272, '苏丹', 'SD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (273, '苏里南', 'SR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (274, '所罗门群岛', 'SB', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (275, '索马里', 'SO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (276, '塔吉克斯坦', 'TJ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (277, '泰国', 'TH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (278, '坦桑尼亚', 'TZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (279, '汤加', 'TO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (280, '特克斯和凯科斯群岛', 'TC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (281, '特立尼达和多巴哥', 'TT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (282, '突尼斯', 'TN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (283, '图瓦卢', 'TV', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (284, '土耳其', 'TR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (285, '土库曼斯坦', 'TM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (286, '托克劳', 'TK', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (287, '瓦利斯和富图纳', 'WF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (288, '瓦努阿图', 'VU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (289, '危地马拉', 'GT', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (290, '委内瑞拉', 'VE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (291, '文莱', 'BN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (292, '乌干达', 'UG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (293, '乌克兰', 'UA', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (294, '乌拉圭', 'UY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (295, '乌兹别克斯坦', 'UZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (296, '西撒哈拉', 'EH', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (297, '希腊', 'GR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (298, '新喀里多尼亚', 'NC', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (299, '新西兰', 'NZ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (300, '匈牙利', 'HU', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (301, '叙利亚', 'SY', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (302, '牙买加', 'JM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (303, '亚美尼亚', 'AM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (304, '也门', 'YE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (305, '伊拉克', 'IQ', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (306, '伊朗', 'IR', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (307, '以色列', 'IL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (308, '印度尼西亚', 'ID', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (309, '英属维尔京群岛', 'VG', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (310, '英属印度洋领地', 'IO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (311, '约旦', 'JO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (312, '越南', 'VN', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (313, '赞比亚', 'ZM', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (314, '泽西岛', 'JE', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (315, '乍得', 'TD', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (316, '直布罗陀', 'GI', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (317, '智利', 'CL', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (318, '中非共和国', 'CF', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (319, '中国澳门', 'MO', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (320, '中国台湾', 'TW', 'order_country', '', 'default', 'N', '0', 'admin', now()),
  (321, '中国香港', 'HK', 'order_country', '', 'default', 'N', '0', 'admin', now());

-- ============================================================
-- 3. 核对
-- ============================================================
SELECT 'order_country' AS dict, COUNT(*) AS rows_total
  FROM `sdssys`.`sys_dict_data` WHERE `dict_type` = 'order_country';

SELECT table_name,
       SUM(column_name IN ('buyercountry','buyeraddress','receivetype',
                           'markneeded','markfile','descitems','orderfiles')) AS new_columns
  FROM information_schema.columns
 WHERE table_schema = 'sdsorder' AND table_name REGEXP '^u_o_order_[0-9]{6}$'
 GROUP BY table_name;

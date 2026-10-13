-- ============================================================================
-- 询盘通知：u_s_site 加一列通知邮箱
--
-- 背景：询盘是独立站唯一往回流的数据，也是这个模块存在的商业理由。在此之前，一条询盘进来
--      只做 inquiryMapper.insert，没有邮件、没有短信、没有站内信、没有 Webhook——商家不
--      主动打开管理台就发现不了。外贸询盘的回复时效直接决定成单率。
--
-- 一个站一个收件地址。多个收件人用逗号分隔（StationInquiryNotifier 会拆）。
-- 留空 = 这个站不发通知，不是错误状态：有的站可能就是不想收。
--
-- 可重复执行。
-- ============================================================================

SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'sdsstation' AND TABLE_NAME = 'u_s_site' AND COLUMN_NAME = 'notifyemail'
);
SET @ddl := IF(@col = 0,
    'ALTER TABLE sdsstation.u_s_site
       ADD COLUMN notifyemail varchar(500) NULL COMMENT ''询盘通知邮箱，多个用逗号分隔，留空则不发''
       AFTER ownername',
    'SELECT ''notifyemail 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 验证 ----
SELECT sitecode, sitename, ownername, notifyemail
FROM sdsstation.u_s_site
ORDER BY sitecode;

-- 建好之后记得去 ERP 的站点档案里给每个站填上收件邮箱，否则不会发。
-- 另外 SMTP 没配的话一样不会发（只在启动和首次触发时各记一条 WARN），
-- 配置项见 application.yml 里 spring.mail 那一段的注释。

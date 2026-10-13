-- =====================================================================
-- 站点订单记下买家下单时用的语言
-- =====================================================================
--
-- ⚠ 只跑在**独立站那台**（库 sdsstation）。ERP 那台不需要。
--
-- 这一列是「前台所有东西都跟着语言走」那一批改动里唯一要动库的地方。它被三处读：
--
--   ① 下单回执邮件（BuyerMailer）按它选语言——之前一律英文；
--   ② 买家中心 / 凭单号查单再按买家**当前**浏览的语言把商品名换成那种语言的
--      （SiteOrderService#localizeItems）。没传语言时退回它；
--   ③ 管理台订单详情里能看到买家是哪种语言进来的，回复时有个参考。
--
-- ⚠ u_s_orderitem 的快照**仍然按站点默认语言存**，一个字没改：
--    管理台只有中文界面，运营看到的商品名必须稳定；买家看到的名字由 ② 在读的时候换。
--
-- 可重复执行：加列走 information_schema 判断。

DROP PROCEDURE IF EXISTS `__add_order_lang`;
DELIMITER $$
CREATE PROCEDURE `__add_order_lang`()
BEGIN
  DECLARE db VARCHAR(64) DEFAULT DATABASE();

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = db AND table_name = 'u_s_order'
                   AND column_name = 'lang') THEN
    ALTER TABLE `u_s_order`
      ADD COLUMN `lang` varchar(10) DEFAULT NULL
      COMMENT '买家下单时浏览的语言（en/zh/ja…），回执邮件按它写。NULL = 老单，按站点默认语言处理'
      AFTER `source`;
  END IF;
END$$
DELIMITER ;

CALL `__add_order_lang`();
DROP PROCEDURE `__add_order_lang`;


-- =====================================================================
-- 验一遍
-- =====================================================================

SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'u_s_order'
      AND column_name = 'lang') AS `订单语言列_应为1`;


-- =====================================================================
-- 回滚（手工，按需）
-- =====================================================================
--
-- ALTER TABLE `u_s_order` DROP COLUMN `lang`;
--
-- ⚠ 回滚前先确认 jar 已经换回旧版：新版 SiteOrderMapper.xml 的列清单里有 lang，
--    列没了它每次查订单都 500。

-- =====================================================================
-- 首页装修：草稿 / 发布两态
-- 2026-09-13
-- =====================================================================
--
-- 在这之前，管理台的「保存装修」是**直接改 contentjson**，也就是「保存即上线」。
-- DecorateForm.vue 的保存条上原话是「保存后刷新站点立刻生效，没有『发布』这一步」。
--
-- 后果是商家没法在线上站上安心改：改到一半（Banner 换了图、文案还没填）就已经见客了。
-- 本次加一份草稿：
--
--   · 「保存草稿」写 draftjson，**contentjson 一个字不动**，前台照旧渲染已发布那份；
--   · 「发布」把 draftjson 整份拷进 contentjson，然后把 draftjson 清掉；
--   · 「放弃草稿」只清 draftjson。
--
-- draftjson 为 NULL = 没有未发布的改动（这也是所有老站点迁移后的初始状态，
-- 所以本脚本不需要回填：管理台载入时 draftjson 为空就退回 contentjson）。
--
-- ---------------------------------------------------------------------
-- 为什么不做成版本历史表
-- ---------------------------------------------------------------------
--
-- 只加一列就已经拿到了「改坏了能退回去」这个最要紧的性质：草稿在手时，
-- 上一个已发布版本完整地躺在 contentjson 里，点「放弃草稿」就回去了。
-- 真正的多版本历史（留最近 N 版、能挑一版回滚）是另一件事，要一张表加一套界面，
-- 等商家真提了再做——**别顺手在这里加一列 prevcontentjson**，
-- 那种「半个历史」既不够用又要一直维护。
--
-- ---------------------------------------------------------------------
-- 列类型跟 contentjson 走
-- ---------------------------------------------------------------------
--
-- contentjson 是 mediumtext（16MB），草稿是它的同构副本，类型必须一致——
-- 给 text（64KB）的话，平时都好好的，等哪个站装修内容一大就在「发布」那一步
-- 静默截断，而截断后的 JSON 解析不了，表现是首页整个空掉。
--
-- ⚠ 线上库 sdsstation，用 mysql 客户端跑的话记得 --default-character-set=utf8mb4，
--   见 memory/mysql-cli-charset-gbk。
-- =====================================================================

alter table `u_s_siteinfo`
  add column `draftjson` mediumtext null
    comment '装修草稿。结构和 contentjson 完全一样（v4），NULL=没有未发布的改动。管理台「保存草稿」写这一列，「发布」把它拷进 contentjson 再置 NULL。⚠ 前台/公开接口一律不读这一列，只有管理台载入和预览读它'
    after `contentjson`,
  add column `draftuser` varchar(50) null
    comment '草稿最后一次保存的人。和 updateuser 分开：那个是档案的更新人，这个只管草稿'
    after `draftjson`,
  add column `drafttime` datetime null
    comment '草稿最后一次保存的时间。管理台在保存条上显示「草稿保存于 …」'
    after `draftuser`;

-- 顺带把 contentjson 的注释从 v3 更新成 v4（结构在 2026-09-11 就改了，注释一直没跟）
alter table `u_s_siteinfo`
  modify column `contentjson` mediumtext
    comment '**已发布**的装修内容 JSON（草稿在 draftjson）。v4 分三层：i18n 之外的顶层键是共享层（所有语言通用的结构：order/off/blocks、logo、hero.slides、nav/highlights/capabilities 等列表的 id 与非文字字段、about.image、contact、reach）；i18n.<lang> 是语言层（文案：companyName/slogan/seo*/hero.title 等，以及按 id 存的文案袋子 navText/highlightText/capText/slideText/aboutStatText/reachStatText）。从下往上铺：共享层 → 站点默认语言层 → 当前语言层，缺译文按项退回默认语言；同名键两边都是对象时合一层，其余整个覆盖。最后由 SiteOpenService#compose 把条目和文案按 id 拼回前台认识的形状（nav:[{title,url}]）。v1/v2/v3 老数据不用迁移，照常显示。实现见 SiteOpenService#siteContent';

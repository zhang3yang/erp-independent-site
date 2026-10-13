-- =====================================================================
-- 文件访问地址换域名：把库里所有存着旧前缀的完整 URL 重写成新前缀
-- =====================================================================
--
-- 库：sdsstation。可重复执行（重跑时旧前缀已经匹配不到，是个空操作）。
--
-- ⚠ 这个脚本要人改两行再跑，不能直接执行：@old_prefix / @new_prefix 在第 1 节。
--
-- =====================================================================
-- 为什么会需要这么一个脚本
-- =====================================================================
--
-- 因为文件地址是**上传那一刻整串固化进库**的，不是渲染时拼的。
-- SiteFileService.publicUrl() 把「域名 + / + 对象键」拼好之后，存进
-- u_s_siteimage.imageurl 的就是 https://file.example.com/image/2026/09/06/xxx.png
-- 这样一个完整字符串。
--
-- 这个设计是故意的，理由在 publicUrl() 的注释里：存相对路径的话，前台在自己域名下
-- 看着正常，被别的站引用、或者哪天前台换了域名就全裂了。代价就是这个脚本——
-- 换域名要动历史数据。
--
-- 所以：**域名是一次性决策**。跑这个脚本是补救，不是常规运维动作。
--
-- =====================================================================
-- 爆炸半径：不只是文件库那两张表
-- =====================================================================
--
-- 地址被**复制**到了六个地方。只重写 u_s_siteimage 是这件事上最容易犯的错——
-- 文件页看着好了，商品详情页和首页装修还是裂的。
--
--   ① u_s_siteimage.imageurl         文件库，图片        ← 有 objectname，可精确重建
--   ② u_s_siteimage.thumburl         缩略图（当前恒为 NULL，接上图片处理后会有值）
--   ③ u_s_sitevideo.videourl         文件库，视频        ← 有 objectname，可精确重建
--      u_s_sitemodel.modelurl        文件库，3D 模型（2026-09-20 加）← 同上，有 objectname
--   ④ u_s_sitegoods.siteimgs         商品站点图，逗号分隔的完整 URL
--      u_s_sitegoods.sitevideos      商品站点视频，同上
--      u_s_sitegoods.model3d         商品 3D 模型地址（2026-09-20 加），单个 URL
--      u_s_sitegoods.mainvideo       商品主图视频（2026-09-22 加），单个 URL
--      u_s_sitegoods.model3d_marks   商品 3D 标注 JSON（2026-09-25 加），logo/屏幕图地址埋在里面
--      u_s_sitegoods.model3d_states  商品 3D 模型状态 JSON（2026-10-08 加），各状态的 glb 地址埋在里面
--      u_s_sitegoods.imgurl          商品主图
--      u_s_sitegoods_combo.model3d   规格自己的 3D 模型地址（2026-10-09 加），单个 URL
--      u_s_sitegoods_combo.model3d_marks 规格自己的 3D 标注 JSON（2026-10-09 加），图片地址埋在里面
--      u_s_sitegoods_combo.model3d_states 规格自己的 3D 模型状态 JSON（2026-10-13 加）
--      u_s_custom_plan.model3d       买家定制方案的模型地址快照（2026-09-29 加）
--      u_s_custom_plan.marks         买家定制方案标注 JSON，logo/屏幕图地址埋在里面
--      u_s_custom_plan.model3d_states 买家定制方案的模型状态快照（2026-10-08 加）
--   ⑤ u_s_sitegoods_lang.description 详情富文本，地址埋在 <img src> 里 ← 最容易漏
--   ⑥ u_s_siteinfo.contentjson       首页装修 JSON，logo / hero / 关于我们配图都在里面
--
-- ①③ 能用 objectname 精确重建；④⑤⑥ 只能做字符串替换。
--
-- ⚠ u_s_orderitem.imgurl **故意不动**。那是下单当时的商品快照，它的意义就是
--   「这一单当时长这样」。跟着改等于篡改历史凭据；老图片只要还在（见 §0 第 2 条），
--   快照照样打得开。
--
-- ⚠ u_s_sitegoods.mainimgs 也不动。那一列是从 ERP 商品档案带过来的，
--   地址指向 ERP 那边的存储（file.erp.vclinker.com 之类），和本站的文件库无关。
--   真要动它是另一件事，不要顺手塞进这个脚本。
--
-- =====================================================================
-- §0 跑之前必须先做的两件事
-- =====================================================================
--
--   1. **新地址要真的能取到文件。** 先把老文件按原相对路径搬到新位置
--      （scripts/station/upload-to-cos.ps1 干的就是这件事），确认
--      curl https://新域名/image/2026/09/xx/某个真实文件 返回 200，再跑这个脚本。
--      顺序反了的话，中间那段时间整站都是裂图。
--
--   2. **老域名不要撤。** 改完之后老地址仍然可能躺在别人的收藏夹、爬虫索引、
--      已经发出去的报价单里。它不花钱，留着。
--
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 改这两行，然后整个脚本从头跑到尾
-- ---------------------------------------------------------------------
--
-- ⚠ 两个前缀都**不要带结尾斜杠**，也不要带 image/ 或 video/ 那一层——
--   对象键本身就是 image/2026/09/06/xxx.png，前缀只到域名（或域名+回源路径）为止。
--
-- 康辰这次的实际值就是下面这两个。换别的站时照着改。

SET @old_prefix = 'http://127.0.0.1:8081/files';
SET @new_prefix = 'https://file.kangchen.vclinker.com';


-- ---------------------------------------------------------------------
-- 2. 空跑：先看清楚要动多少行
-- ---------------------------------------------------------------------
--
-- 八个数，一个都别跳过。和预期对不上就停下来查，别硬跑第 5 节。

SELECT 'u_s_siteimage.imageurl'          AS col, COUNT(*) AS rows_to_change
  FROM u_s_siteimage      WHERE imageurl    LIKE CONCAT(@old_prefix, '%')
UNION ALL
SELECT 'u_s_siteimage.thumburl',         COUNT(*)
  FROM u_s_siteimage      WHERE thumburl    LIKE CONCAT(@old_prefix, '%')
UNION ALL
SELECT 'u_s_sitevideo.videourl',         COUNT(*)
  FROM u_s_sitevideo      WHERE videourl    LIKE CONCAT(@old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods.siteimgs',         COUNT(*)
  FROM u_s_sitegoods      WHERE siteimgs    LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods.sitevideos',       COUNT(*)
  FROM u_s_sitegoods      WHERE sitevideos  LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods.imgurl',           COUNT(*)
  FROM u_s_sitegoods      WHERE imgurl      LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods_lang.description', COUNT(*)
  FROM u_s_sitegoods_lang WHERE description LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_siteinfo.contentjson',       COUNT(*)
  FROM u_s_siteinfo       WHERE contentjson LIKE CONCAT('%', @old_prefix, '%');


-- ---------------------------------------------------------------------
-- 3. 溢出预检：新前缀比旧的长时，那两个逗号分隔的列可能撑爆
-- ---------------------------------------------------------------------
--
-- siteimgs / sitevideos 是 varchar(2000)，里面可能塞了十几个完整 URL。
-- 换成更长的域名之后总长度会涨，撑爆的表现分两种，都很难受：
--   · 严格模式（MySQL 8 默认）→ UPDATE 直接报 Data too long，整条语句回滚，还算好的
--   · 非严格模式             → **静默截断**，最后一个 URL 被砍掉半截，页面上少一张图
--
-- 所以先算一遍。这个查询**有结果**就说明会溢出，先扩宽再跑第 5 节。

SELECT 'u_s_sitegoods.siteimgs' AS col, shop_code, sitecode, goodscode,
       CHAR_LENGTH(siteimgs) AS now_len,
       CHAR_LENGTH(REPLACE(siteimgs, @old_prefix, @new_prefix)) AS after_len
  FROM u_s_sitegoods
 WHERE siteimgs LIKE CONCAT('%', @old_prefix, '%')
   AND CHAR_LENGTH(REPLACE(siteimgs, @old_prefix, @new_prefix)) > 2000
UNION ALL
SELECT 'u_s_sitegoods.sitevideos', shop_code, sitecode, goodscode,
       CHAR_LENGTH(sitevideos),
       CHAR_LENGTH(REPLACE(sitevideos, @old_prefix, @new_prefix))
  FROM u_s_sitegoods
 WHERE sitevideos LIKE CONCAT('%', @old_prefix, '%')
   AND CHAR_LENGTH(REPLACE(sitevideos, @old_prefix, @new_prefix)) > 2000;

-- 上面有结果的话先跑这两句（没结果就跳过）：
--
--   ALTER TABLE u_s_sitegoods MODIFY `siteimgs`   varchar(4000) DEFAULT NULL
--     COMMENT '站点专用图片，逗号分隔的完整 URL，第一张为主图。留空则回退 imgurl / mainimgs';
--   ALTER TABLE u_s_sitegoods MODIFY `sitevideos` varchar(4000) DEFAULT NULL
--     COMMENT '站点商品视频，逗号分隔的完整 URL。来自文件资源库，无回退来源';
--
-- 改完记得同步回基线 01_station_schema.sql，否则新装的机器又是 2000。


-- ---------------------------------------------------------------------
-- 4. 备份：只备这次要动的五张表
-- ---------------------------------------------------------------------
--
-- ⚠ 表名带日期，且**故意不加 IF NOT EXISTS**。跑第二次时这里会因为表已存在而报错——
--   那正是想要的：它在提醒你「上一次的备份还在，先确认那一次的结果」。
--   真要重跑就手工换个后缀，别让脚本自己覆盖掉上一次的现场。
--
-- 这几张备份表占不了多少空间，**确认站点一切正常之前不要删**。

CREATE TABLE bak_20260906_siteimage      AS SELECT * FROM u_s_siteimage;
CREATE TABLE bak_20260906_sitevideo      AS SELECT * FROM u_s_sitevideo;
CREATE TABLE bak_20260906_sitegoods      AS SELECT * FROM u_s_sitegoods;
CREATE TABLE bak_20260906_sitegoods_lang AS SELECT * FROM u_s_sitegoods_lang;
CREATE TABLE bak_20260906_siteinfo       AS SELECT * FROM u_s_siteinfo;


-- ---------------------------------------------------------------------
-- 5. 重写
-- ---------------------------------------------------------------------

START TRANSACTION;

-- ①② 文件库图片：用 objectname 精确重建，不做字符串替换。
--
-- 为什么这两张表区别对待：objectname 是「这个文件在桶里 / 盘上的真实路径」，
-- 是唯一真相；imageurl 只是它的派生值。按真相重建出来的一定对，而字符串替换
-- 只能保证「旧前缀被换掉了」——万一某几行当初存的是第三种前缀（更早期某次
-- 配置错误留下的），替换会漏掉它们，重建不会。
--
-- objectname 为空的老行退回字符串替换：COS 支持是 2026-09-05 才加的，
-- 更早写进去的行这一列可能是 NULL。
UPDATE u_s_siteimage
   SET imageurl = CASE
         WHEN objectname IS NOT NULL AND objectname <> ''
           THEN CONCAT(@new_prefix, '/', objectname)
         ELSE REPLACE(imageurl, @old_prefix, @new_prefix)
       END
 WHERE imageurl LIKE CONCAT(@old_prefix, '%');

-- thumburl 没有对应的 objectname（缩略图是厂商现生成的，不是一个独立对象），
-- 只能替换。当前它恒为 NULL，这一句是为将来接上图片处理之后准备的。
UPDATE u_s_siteimage
   SET thumburl = REPLACE(thumburl, @old_prefix, @new_prefix)
 WHERE thumburl LIKE CONCAT(@old_prefix, '%');

-- ③ 文件库视频：同 ①
UPDATE u_s_sitevideo
   SET videourl = CASE
         WHEN objectname IS NOT NULL AND objectname <> ''
           THEN CONCAT(@new_prefix, '/', objectname)
         ELSE REPLACE(videourl, @old_prefix, @new_prefix)
       END
 WHERE videourl LIKE CONCAT(@old_prefix, '%');

-- ④ 商品上的图、视频、模型、3D 标注。这些列是从文件库**复制**过去的字符串，没有
--    objectname 可依，只能替换。siteimgs / sitevideos 是逗号分隔的一串、model3d_marks
--    是 JSON，REPLACE 会把每一处地址都换掉，这正是要的效果。
--    （model3d / mainvideo 当初只进了上面的清单注释、没进这条 UPDATE，2026-09-25 补上。
--     ⚠ model3d_marks 列要先跑 2026-09-25-goods-model3d-marks.sql 才有；
--       model3d_states 列要先跑 2026-10-08-goods-model3d-states.sql 才有。）
UPDATE u_s_sitegoods
   SET siteimgs      = REPLACE(siteimgs,      @old_prefix, @new_prefix),
       sitevideos    = REPLACE(sitevideos,    @old_prefix, @new_prefix),
       imgurl        = REPLACE(imgurl,        @old_prefix, @new_prefix),
       model3d       = REPLACE(model3d,       @old_prefix, @new_prefix),
       mainvideo     = REPLACE(mainvideo,     @old_prefix, @new_prefix),
       model3d_marks = REPLACE(model3d_marks, @old_prefix, @new_prefix),
       model3d_states = REPLACE(model3d_states, @old_prefix, @new_prefix)
 WHERE siteimgs      LIKE CONCAT('%', @old_prefix, '%')
    OR sitevideos    LIKE CONCAT('%', @old_prefix, '%')
    OR imgurl        LIKE CONCAT('%', @old_prefix, '%')
    OR model3d       LIKE CONCAT('%', @old_prefix, '%')
    OR mainvideo     LIKE CONCAT('%', @old_prefix, '%')
    OR model3d_marks LIKE CONCAT('%', @old_prefix, '%')
    OR model3d_states LIKE CONCAT('%', @old_prefix, '%');

-- ④a 规格（组合）各自的 3D 模型、标注、状态（2026-10-13 加）。
--    ⚠ 对应列要先跑 2026-10-09-combo-model3d.sql 和 2026-10-13-combo-model3d-states.sql。
UPDATE u_s_sitegoods_combo
   SET model3d       = REPLACE(model3d,       @old_prefix, @new_prefix),
       model3d_marks = REPLACE(model3d_marks, @old_prefix, @new_prefix),
       model3d_states = REPLACE(model3d_states, @old_prefix, @new_prefix)
 WHERE model3d       LIKE CONCAT('%', @old_prefix, '%')
    OR model3d_marks LIKE CONCAT('%', @old_prefix, '%')
    OR model3d_states LIKE CONCAT('%', @old_prefix, '%');

-- ④b 买家定制方案（2026-09-29 加）。存的是保存那一刻的快照，同样只能字符串替换。
--    ⚠ 这张表要先跑 2026-09-29-custom-plan.sql 才有；老库没这张表就把这一句注释掉。
UPDATE u_s_custom_plan
   SET model3d = REPLACE(model3d, @old_prefix, @new_prefix),
       marks   = REPLACE(marks,   @old_prefix, @new_prefix),
       model3d_states = REPLACE(model3d_states, @old_prefix, @new_prefix)
 WHERE model3d LIKE CONCAT('%', @old_prefix, '%')
    OR marks   LIKE CONCAT('%', @old_prefix, '%')
    OR model3d_states LIKE CONCAT('%', @old_prefix, '%');

-- ⑤ 详情富文本。地址埋在 <img src="..."> 里，REPLACE 照样能换——
--    它不解析 HTML，只认那一段前缀字符串，而 URL 前缀里没有任何 HTML 元字符。
UPDATE u_s_sitegoods_lang
   SET description = REPLACE(description, @old_prefix, @new_prefix)
 WHERE description LIKE CONCAT('%', @old_prefix, '%');

-- ⑥ 首页装修 JSON。同样是字符串替换，不做 JSON 解析：
--    contentjson 是 v2 分语言结构，共享层和各语言层里都可能有图片地址。
--    挨个 JSON_SET 要先知道所有键名，而那份键名列表会随着装修器改版失效。
--    整串替换不关心结构，改版了也不用跟。
--
--    ⚠ 前提是前缀里不含需要 JSON 转义的字符。URL 前缀不会有——它只有字母、数字、
--      `.`、`:`、`/`、`-`。真要换成带引号或反斜杠的前缀时这一句就不成立了。
UPDATE u_s_siteinfo
   SET contentjson = REPLACE(contentjson, @old_prefix, @new_prefix)
 WHERE contentjson LIKE CONCAT('%', @old_prefix, '%');

COMMIT;


-- ---------------------------------------------------------------------
-- 6. 验收：这个查询必须一行都查不出来
-- ---------------------------------------------------------------------
--
-- 查得出来说明有列漏改了。注意它只查这一个旧前缀——历史上换过不止一次域名的话，
-- 每个用过的旧前缀都要各查一遍。

SELECT 'u_s_siteimage.imageurl' AS col, imageurl AS still_old
  FROM u_s_siteimage      WHERE imageurl    LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_siteimage.thumburl',         thumburl
  FROM u_s_siteimage      WHERE thumburl    LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitevideo.videourl',         videourl
  FROM u_s_sitevideo      WHERE videourl    LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods.siteimgs',         siteimgs
  FROM u_s_sitegoods      WHERE siteimgs    LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods.sitevideos',       sitevideos
  FROM u_s_sitegoods      WHERE sitevideos  LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods.imgurl',           imgurl
  FROM u_s_sitegoods      WHERE imgurl      LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_sitegoods_lang.description', LEFT(description, 200)
  FROM u_s_sitegoods_lang WHERE description LIKE CONCAT('%', @old_prefix, '%')
UNION ALL
SELECT 'u_s_siteinfo.contentjson',       LEFT(contentjson, 200)
  FROM u_s_siteinfo       WHERE contentjson LIKE CONCAT('%', @old_prefix, '%');


-- 再抽查几条新地址，拿出去 curl 一下确认真的能打开：
SELECT imageid, imageurl FROM u_s_siteimage ORDER BY createtime DESC LIMIT 5;


-- ---------------------------------------------------------------------
-- 7. 出事了怎么退回去
-- ---------------------------------------------------------------------
--
-- 备份表还在，整表换回来即可（在业务低峰做，中间有几秒钟查不到数据）：
--
--   START TRANSACTION;
--   DELETE FROM u_s_siteimage;      INSERT INTO u_s_siteimage      SELECT * FROM bak_20260906_siteimage;
--   DELETE FROM u_s_sitevideo;      INSERT INTO u_s_sitevideo      SELECT * FROM bak_20260906_sitevideo;
--   DELETE FROM u_s_sitegoods;      INSERT INTO u_s_sitegoods      SELECT * FROM bak_20260906_sitegoods;
--   DELETE FROM u_s_sitegoods_lang; INSERT INTO u_s_sitegoods_lang SELECT * FROM bak_20260906_sitegoods_lang;
--   DELETE FROM u_s_siteinfo;       INSERT INTO u_s_siteinfo       SELECT * FROM bak_20260906_siteinfo;
--   COMMIT;
--
-- ⚠ 退回去只是把地址改回旧前缀，**旧地址那时候还得能取到文件**。
--   这就是 §0 第 2 条「老域名不要撤」的实际用处。

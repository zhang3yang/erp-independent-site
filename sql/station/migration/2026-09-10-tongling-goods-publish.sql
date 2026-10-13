-- =====================================================================
-- 统领站上架一批真实商品 + 站点分类（一次性数据脚本，只对统领站有效）
-- 2026-09-10
-- =====================================================================
--
-- 背景：统领站线上只有 1 个商品（G001 按摩仪），`sitecategory` 全空。后果有两处，
-- 都不是样式问题，改代码修不好：
--
--   · 商品列表页左侧那栏分类导航整块不渲染 —— `v-if="groups.length"`，
--     而 groups 来自 /openapi/site/info 的 categories，它是
--     `select distinct sitecategory`（SiteGoodsMapper.selectPublicCategories）。
--     一个商品都没填分类 → categories 是空数组 → 侧栏消失。
--   · 首页那屏商品网格（index.vue 的 instruments 区块）取 size=6，
--     只有 1 条就只有 1 张卡，两行网格塌成一行。
--
-- 康辰站（kangchen.vclinker.com）上架了 9 条，分类填的是
-- `Silicone Parts` / `Compression Therapy/Leg Systems` 这种英文两级串 —— 本脚本照它的做法。
--
-- ---------------------------------------------------------------------
-- 商品数据从哪来：**没有编造**
-- ---------------------------------------------------------------------
--
-- 全部取自 ERP 商品档案 `sdsshop.u_m_waregoods`（shop_code = sp708xm02q，
-- activeflag = 1），编码、中文品名、规格型号、计量单位都是库里的原值。
-- 站上的英文标题是对中文品名 + 规格的直译，副标题只复述品名/型号/计量单位，
-- **没有编任何认证、寿命、流量、公差之类的指标** —— 那些只有商家自己能提供。
--
-- 同样地：
--   · `price` 一律留 0。前台见 price<=0 会渲染成「Inquire」而不是 $0.00
--     （ProductCard.vue: `Number(item.price) > 0 ? money(item.price) : shop.inquire`），
--     所以留 0 是**正确**的表达，不是漏填。ERP 里 standardprice 也确实全是 0。
--   · `moq` 一律留 NULL。起订量编不得，编了买家会照着下单。
--
-- 挑选口径：只上买家会搜的成品与零部件（气囊套、按摩设备、硅胶管、泵阀、电池、
-- 适配器）。ERP 里另外那 60 多条是说明书、彩箱、EPE 内衬、以及「联想主机机箱」
-- 「可口可乐」这类明显的测试数据，不往对外店铺上放。
--
-- ---------------------------------------------------------------------
-- 分类用的两级约定
-- ---------------------------------------------------------------------
--
-- `sitecategory` 是**一列自由文本**，没有父子结构。前台 `app/lib/categories.ts`
-- 按第一个分隔符（`/`、`／`、`>`）拆成两级。所以这里写 `Compression Garments/Leg Sleeves`
-- 就能得到「Compression Garments」大组下挂「Leg Sleeves」子项，筛选参数仍是整串。
--
-- ⚠ ERP 那边的分类串是反斜杠加编号的（`\\1202半成品39气囊类`），**不能直接抄**：
--   反斜杠不是前台认的分隔符，抄过去侧栏会出现一条又长又乱的单级分类。
--
-- ---------------------------------------------------------------------
-- 语言
-- ---------------------------------------------------------------------
--
-- 站点开了 en/ru/es/zh，默认 en。取名走
-- `coalesce(l.title, d.title, sg.goodsname)`（SiteGoodsMapper.xml），
-- 所以只要写了 en（=默认语言）这一行，ru/es 也能回退到英文，不会出现空标题。
-- zh 那行照写中文原名，中文站访客看到的就是 ERP 里的品名。
--
-- 可重复执行：全部 INSERT ... ON DUPLICATE KEY UPDATE。
-- 回滚：见文件末尾。
-- =====================================================================

USE `sdsstation`;
SET NAMES utf8mb4;

SET @shop = 'sp708xm02q';
SET @site = 'DLZ2049684105';   -- ⚠ 统领站的 sitecode，换站必改（select sitecode from u_s_siteinfo）

-- ---------------------------------------------------------------------
-- 一、商品主表
-- ---------------------------------------------------------------------
--
-- sortnum 越小越靠前，首页那屏取前 6 条 —— 所以 10~60 这六条是**首页出现的那六个**，
-- 同时把 featured 置 1（角标「推荐」）。G001 保持 sortnum=0 排在最前：它是目前
-- 唯一有图的商品，让它占住首页第一格比排在后面强。
-- ---------------------------------------------------------------------

INSERT INTO `u_s_sitegoods`
  (`shop_code`, `sitecode`, `goodscode`, `goodsname`, `spec`, `unit`,
   `sitecategory`, `price`, `moq`, `featured`, `publishstatus`, `sortnum`, `createuser`)
VALUES
  -- ---- 首页那六格（sortnum 10~60，featured=1）----
  (@shop, @site, '12030101', '脉冲按摩腰带',        '8721-3R',                  'pcs',  'Therapy Devices',                    0, NULL, 1, '已上架',  10, 'sql-2026-09-10'),
  (@shop, @site, '12030105', '冷敷机',              '7961-C1',                  'unit', 'Therapy Devices',                    0, NULL, 1, '已上架',  20, 'sql-2026-09-10'),
  (@shop, @site, '12023907', '6腔腿套',             '3922-6M',                  'pair', 'Compression Garments/Leg Sleeves',   0, NULL, 1, '已上架',  30, 'sql-2026-09-10'),
  (@shop, @site, '12023906', '6腔腰带',             '3921-6A',                  'pcs',  'Compression Garments/Waist & Vest',  0, NULL, 1, '已上架',  40, 'sql-2026-09-10'),
  (@shop, @site, '12023914', '6腔背心（马甲）',      '3927-6A',                  'pcs',  'Compression Garments/Waist & Vest',  0, NULL, 0, '已上架',  50, 'sql-2026-09-10'),
  (@shop, @site, '12010901', '气泵',                'WP36-12',                  'pcs',  'Pumps & Valves',                     0, NULL, 1, '已上架',  60, 'sql-2026-09-10'),
  -- ---- 列表页其余 ----
  (@shop, @site, '12023916', '8腔腿套',             '3922-8L',                  'pair', 'Compression Garments/Leg Sleeves',   0, NULL, 0, '已上架',  70, 'sql-2026-09-10'),
  (@shop, @site, '12023902', '4腔腿套',             '3922-4M',                  'pair', 'Compression Garments/Leg Sleeves',   0, NULL, 0, '已上架',  80, 'sql-2026-09-10'),
  (@shop, @site, '12023911', '6腔手臂（上肢）',      '3923-6A',                  'pcs',  'Compression Garments/Arm Sleeves',   0, NULL, 0, '已上架',  90, 'sql-2026-09-10'),
  (@shop, @site, '12023913', '6腔长裤',             '3926-6A',                  'pcs',  'Compression Garments/Trousers',      0, NULL, 0, '已上架', 100, 'sql-2026-09-10'),
  (@shop, @site, '12010304', '4腔单排硅胶管',        'ST-G447',                  'm',    'Silicone Parts',                     0, NULL, 0, '已上架', 110, 'sql-2026-09-10'),
  (@shop, @site, '12010301', '6腔双排手感油硅胶管',  'DSTO-B647',                'pcs',  'Silicone Parts',                     0, NULL, 0, '已上架', 120, 'sql-2026-09-10'),
  (@shop, @site, '12010305', '8腔双排手感油硅胶管',  'DSTO-B847',                'pcs',  'Silicone Parts',                     0, NULL, 0, '已上架', 130, 'sql-2026-09-10'),
  (@shop, @site, '12010902', '气阀',                'WV33CL-12',                'set',  'Pumps & Valves',                     0, NULL, 0, '已上架', 140, 'sql-2026-09-10'),
  (@shop, @site, '12010701', '锂离子电池',          '26650-3S1P-11.1V5000mAh',  'pcs',  'Batteries',                          0, NULL, 0, '已上架', 150, 'sql-2026-09-10'),
  (@shop, @site, '12020101', '适配器主体',          'JXY50W',                   'pcs',  'Power Adapters',                     0, NULL, 0, '已上架', 160, 'sql-2026-09-10'),
  (@shop, @site, '12030501', '美规适配器',          'US',                       'pcs',  'Power Adapters',                     0, NULL, 0, '已上架', 170, 'sql-2026-09-10'),
  (@shop, @site, '12030502', '欧规适配器',          'EU',                       'pcs',  'Power Adapters',                     0, NULL, 0, '已上架', 180, 'sql-2026-09-10')
ON DUPLICATE KEY UPDATE
  `goodsname`     = VALUES(`goodsname`),
  `spec`          = VALUES(`spec`),
  `unit`          = VALUES(`unit`),
  `sitecategory`  = VALUES(`sitecategory`),
  `featured`      = VALUES(`featured`),
  `publishstatus` = VALUES(`publishstatus`),
  `sortnum`       = VALUES(`sortnum`),
  `updateuser`    = 'sql-2026-09-10';

-- 已有的那一个：补分类，并且**放到首页第一格**。价格、图片、combo 一个字不动。
--
-- 为什么要专门抬它：对外列表的排序是 `featured desc, sortnum asc`
-- （SiteGoodsMapper.xml），首页取前 6 条。G001 是目前**唯一有商品图**的一条
-- （5 张，在 u_s_siteimage 里），不置顶的话它排在 6 个 featured 后面，
-- 首页那一屏就是清一色的占位图卡片。
--
-- 抬它上来的同时把 12023914（6腔背心）的 featured 撤掉，保持首页正好 6 格。
UPDATE `u_s_sitegoods`
   SET `sitecategory` = 'Therapy Devices',
       `featured`     = 1,
       `sortnum`      = 5,
       `updateuser`   = 'sql-2026-09-10'
 WHERE `shop_code` = @shop AND `sitecode` = @site AND `goodscode` = 'G001';


-- ---------------------------------------------------------------------
-- 二、英文文案（en = 站点默认语言，ru/es 靠 coalesce 回退到它）
-- ---------------------------------------------------------------------
--
-- subtitle 只复述品名、型号、计量单位。想写「铂金硫化 / 符合 FDA / 噪音低于 45dB」
-- 这类卖点的话，请商家给准确参数再补 —— 别在这份脚本里编。
-- ---------------------------------------------------------------------

INSERT INTO `u_s_sitegoods_lang`
  (`shop_code`, `sitecode`, `goodscode`, `lang`, `title`, `subtitle`)
VALUES
  (@shop, @site, '12030101', 'en', 'Pulse Massage Belt 8721-3R',
   'Pulse massage belt, model 8721-3R. Also produced with English-language labelling (8721-3RE).'),
  (@shop, @site, '12030105', 'en', 'Cold Compression Therapy Unit 7961-C1',
   'Cold therapy unit, model 7961-C1. Supplied by the unit.'),
  (@shop, @site, '12023907', 'en', '6-Chamber Leg Sleeve, Size M',
   'Six-chamber compression leg sleeve, model 3922-6M. Sold per pair. Sizes L, XL and XXL are also produced.'),
  (@shop, @site, '12023906', 'en', '6-Chamber Waist Belt',
   'Six-chamber compression waist belt, model 3921-6A. Four- and eight-chamber versions are also produced.'),
  (@shop, @site, '12023914', 'en', '6-Chamber Compression Vest',
   'Six-chamber compression vest, model 3927-6A. An eight-chamber version (3927-8A) is also produced.'),
  (@shop, @site, '12010901', 'en', 'Air Pump WP36-12',
   '12 V miniature air pump, model WP36-12. Used with the compression garments on this catalogue.'),
  (@shop, @site, '12023916', 'en', '8-Chamber Leg Sleeve, Size L',
   'Eight-chamber compression leg sleeve, model 3922-8L. Sold per pair. Sizes M, XL and XXL are also produced.'),
  (@shop, @site, '12023902', 'en', '4-Chamber Leg Sleeve, Size M',
   'Four-chamber compression leg sleeve, model 3922-4M. Sold per pair. Size L is also produced.'),
  (@shop, @site, '12023911', 'en', '6-Chamber Arm Sleeve',
   'Six-chamber compression sleeve for the upper limb, model 3923-6A. A paired version (3923-6AD) is also produced.'),
  (@shop, @site, '12023913', 'en', '6-Chamber Full-Leg Trousers',
   'Six-chamber compression trousers, model 3926-6A. An eight-chamber version (3926-8A) is also produced.'),
  (@shop, @site, '12010304', 'en', '4-Channel Single-Row Silicone Tube ST-G447',
   'Single-row silicone tube with four channels, model ST-G447. Supplied by the metre.'),
  (@shop, @site, '12010301', 'en', '6-Channel Double-Row Silicone Tube DSTO-B647',
   'Double-row silicone tube with six channels, soft-touch surface finish. Model DSTO-B647.'),
  (@shop, @site, '12010305', 'en', '8-Channel Double-Row Silicone Tube DSTO-B847',
   'Double-row silicone tube with eight channels, soft-touch surface finish. Model DSTO-B847.'),
  (@shop, @site, '12010902', 'en', 'Air Valve WV33CL-12',
   '12 V air valve, model WV33CL-12. Supplied as a set. A mirrored version (WV33CR-12) is also produced.'),
  (@shop, @site, '12010701', 'en', 'Li-ion Battery Pack 11.1 V 5000 mAh',
   '26650 cells in a 3S1P pack, 11.1 V / 5000 mAh.'),
  (@shop, @site, '12020101', 'en', 'Power Adapter Body JXY50W',
   'Adapter body, model JXY50W. Mains leads for US, EU, UK and AU plugs are produced separately.'),
  (@shop, @site, '12030501', 'en', 'Power Adapter, US Plug', 'Finished power adapter with US plug.'),
  (@shop, @site, '12030502', 'en', 'Power Adapter, EU Plug', 'Finished power adapter with EU plug.')
ON DUPLICATE KEY UPDATE
  `title`    = VALUES(`title`),
  `subtitle` = VALUES(`subtitle`);

-- 中文文案：标题用 ERP 里的原品名 + 型号，副标题留空（中文卖点同样得商家给）
INSERT INTO `u_s_sitegoods_lang`
  (`shop_code`, `sitecode`, `goodscode`, `lang`, `title`)
VALUES
  (@shop, @site, '12030101', 'zh', '脉冲按摩腰带 8721-3R'),
  (@shop, @site, '12030105', 'zh', '冷敷机 7961-C1'),
  (@shop, @site, '12023907', 'zh', '6腔腿套 3922-6M'),
  (@shop, @site, '12023906', 'zh', '6腔腰带 3921-6A'),
  (@shop, @site, '12023914', 'zh', '6腔背心（马甲）3927-6A'),
  (@shop, @site, '12010901', 'zh', '气泵 WP36-12'),
  (@shop, @site, '12023916', 'zh', '8腔腿套 3922-8L'),
  (@shop, @site, '12023902', 'zh', '4腔腿套 3922-4M'),
  (@shop, @site, '12023911', 'zh', '6腔手臂（上肢）3923-6A'),
  (@shop, @site, '12023913', 'zh', '6腔长裤 3926-6A'),
  (@shop, @site, '12010304', 'zh', '4腔单排硅胶管 ST-G447'),
  (@shop, @site, '12010301', 'zh', '6腔双排手感油硅胶管 DSTO-B647'),
  (@shop, @site, '12010305', 'zh', '8腔双排手感油硅胶管 DSTO-B847'),
  (@shop, @site, '12010902', 'zh', '气阀 WV33CL-12'),
  (@shop, @site, '12010701', 'zh', '锂离子电池 26650-3S1P 11.1V 5000mAh'),
  (@shop, @site, '12020101', 'zh', '适配器主体 JXY50W'),
  (@shop, @site, '12030501', 'zh', '美规适配器'),
  (@shop, @site, '12030502', 'zh', '欧规适配器')
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`);


-- ---------------------------------------------------------------------
-- 三、验一眼
-- ---------------------------------------------------------------------
--   select publishstatus, count(*) from u_s_sitegoods group by publishstatus;
--   select distinct sitecategory from u_s_sitegoods where publishstatus='已上架' order by 1;
--   -- 上架数、分类清单；分类清单就是前台侧栏那棵树的来源
--
-- 接口侧（不用登录）：
--   curl -s 'https://tongling.vclinker.com/openapi/site/info'     | grep -o '"categories":\[[^]]*\]'
--   curl -s 'https://tongling.vclinker.com/openapi/site/products?page=1&size=20' | head -c 400
--
-- ⚠ 站点档案缓存在 station-console 进程里，但**商品和分类不在缓存内**
--   （SiteOpenService 每次查库），所以改完立刻生效，不用重启。


-- ---------------------------------------------------------------------
-- 四、回滚
-- ---------------------------------------------------------------------
-- 本脚本只新增这 18 个 goodscode，G001 只补了一列分类。撤掉：
--
--   delete from u_s_sitegoods_lang
--    where shop_code='sp708xm02q' and sitecode='DLZ2049684105' and goodscode<>'G001';
--   delete from u_s_sitegoods
--    where shop_code='sp708xm02q' and sitecode='DLZ2049684105' and goodscode<>'G001';
--   update u_s_sitegoods set sitecategory=null, featured=0, sortnum=0
--    where shop_code='sp708xm02q' and sitecode='DLZ2049684105' and goodscode='G001';
--
-- 或者恢复执行前的备份（部署时打的）：
--   /tmp/sitegoods-before-2026-09-10.sql
-- ---------------------------------------------------------------------

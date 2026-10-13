-- =====================================================================
-- Esva boots 商品目录（一次性数据脚本，只对 esvafit 站有效）
-- 2026-09-21
-- =====================================================================
--
-- 背景：这个站 `u_s_sitegoods` 里有 18 条商品，但 sitecode 全是 `DLZ2049684105`
-- （统领站），本站是 `DLZ2049739702` —— 对外接口按 sitecode 过滤，所以前台
-- **一条商品都不显示**：首页「主推商品」整块不渲染，商品列表页是空的，
-- 侧栏分类导航也不出现（categories 来自 select distinct sitecategory）。
--
-- 本脚本建 8 条 Esva boots 自己的商品，结构照 getjolt.fr：
-- 加压衣（靴 / 裤 / 臂套 / 腰带）+ 主机 + 配件，靴和裤按尺码分型号。
--
-- ---------------------------------------------------------------------
-- 三样**刻意留空**，不是漏填
-- ---------------------------------------------------------------------
--
--   · `price` 全 0。前台见 price<=0 渲染成「询价」而不是 £0.00
--     （ProductCard.vue），所以 0 是正确的表达。本站本来就不收款、一单一议
--     （购物车文案：No payment on this site），询价流程和它是一致的。
--     **要上真实价格**：币种是 GBP，改 `u_s_sitegoods.price`（和 combo 的 price）即可。
--     ⚠ 趁现在改。管理台明写「改结算币种不会换算已有商品的价格数字」，
--       录了价再改币种就是错账。
--   · `moq` 全 NULL。起订量编不得，编了买家会照着下单。
--   · `spec` / `description` / `specsjson` 全空。腔室数、压力范围、材质、认证
--     只有商家能提供，编一个就是替他做技术承诺。副标题只说「这是什么、怎么卖、
--     配什么用」，一个指标都没写。
--
-- 尺码同理：型号只叫 S/M、L、XL，**不写厘米范围**。getjolt.fr 那套
-- （≤79 / 80–94 / ≥95cm）是别家的规格。首页常见问题 q1 已经改成与此一致：
-- 页面上选码、量出来的数写留言、报价前确认。
--
-- 没有商品图：`u_s_siteimage` 里本站一张都没有，卡片会渲染成干净的占位块
-- （ProductCard 的 .pc-noimg），不是裂图。有实拍后在管理台「商品管理」里传。
--
-- ---------------------------------------------------------------------
-- 跑之前 / 跑之后
-- ---------------------------------------------------------------------
--
--   可重复执行：全部 INSERT ... ON DUPLICATE KEY UPDATE。
--   跑完**不用**重启 station-console —— 商品不像站点档案那样缓存在进程里。
--   回滚：见文件末尾。
-- =====================================================================

USE `sdsstation`;
SET NAMES utf8mb4;

SET @shop = 'sp708xm02q';
SET @site = 'DLZ2049739702';   -- ⚠ Esva boots 的 sitecode（select sitecode from u_s_siteinfo）
SET @who  = 'sql-2026-09-21';


-- ---------------------------------------------------------------------
-- 一、商品主表
-- ---------------------------------------------------------------------
--
-- 对外列表排序是 `featured desc, sortnum asc`（SiteGoodsMapper.xml），首页取前 6 条。
-- 所以 sortnum 10~60 那六条置 featured=1，正好填满首页那屏的两行三列；
-- 配件两条不置顶，只在列表页出现。
--
-- `goodsname` 是后台看的名字（不分语言），对外显示的是下面 `_lang` 表里的 title。
-- ---------------------------------------------------------------------

INSERT INTO `u_s_sitegoods`
  (`shop_code`, `sitecode`, `goodscode`, `goodsname`, `unit`,
   `sitecategory`, `price`, `moq`, `featured`, `publishstatus`, `sortnum`, `createuser`)
VALUES
  (@shop, @site, 'EB-BOOT-01', 'Esva Recovery Boots', 'pair', 'Compression Therapy/Boots', 0, NULL, 1, '已上架', 10, @who),
  (@shop, @site, 'EB-SET-01', 'Esva Recovery Boots — Complete Set', 'set', 'Compression Therapy/Boots', 0, NULL, 1, '已上架', 20, @who),
  (@shop, @site, 'EB-UNIT-01', 'Esva Control Unit', 'unit', 'Control Units', 0, NULL, 1, '已上架', 30, @who),
  (@shop, @site, 'EB-TROUS-01', 'Esva Recovery Trousers', 'pcs', 'Compression Therapy/Trousers', 0, NULL, 1, '已上架', 40, @who),
  (@shop, @site, 'EB-ARM-01', 'Esva Arm Sleeves', 'pair', 'Compression Therapy/Arm Sleeves', 0, NULL, 1, '已上架', 50, @who),
  (@shop, @site, 'EB-WAIST-01', 'Esva Waist Belt', 'pcs', 'Compression Therapy/Waist', 0, NULL, 1, '已上架', 60, @who),
  (@shop, @site, 'EB-BAG-01', 'Esva Carry Bag', 'pcs', 'Accessories', 0, NULL, 0, '已上架', 70, @who),
  (@shop, @site, 'EB-LINER-01', 'Hygienic Boot Liners', 'pack', 'Accessories', 0, NULL, 0, '已上架', 80, @who)
ON DUPLICATE KEY UPDATE
  `goodsname`     = VALUES(`goodsname`),
  `unit`          = VALUES(`unit`),
  `sitecategory`  = VALUES(`sitecategory`),
  `featured`      = VALUES(`featured`),
  `publishstatus` = VALUES(`publishstatus`),
  `sortnum`       = VALUES(`sortnum`),
  `updateuser`    = @who;


-- ---------------------------------------------------------------------
-- 二、商品文案（六种语言）
-- ---------------------------------------------------------------------
--
-- 取名走 `coalesce(l.title, d.title, sg.goodsname)`（SiteGoodsMapper.xml），
-- 所以就算某种语言漏了也不会出空标题。这里六种全写了，不依赖回退。
--
-- `description`（详情页正文）**刻意不写**：那一整段要讲腔室、压力、程序、材质，
-- 全是只有商家能给的东西。副标题已经把「是什么、怎么卖、配什么用」说清楚了。
-- ---------------------------------------------------------------------

INSERT INTO `u_s_sitegoods_lang`
  (`shop_code`, `sitecode`, `goodscode`, `lang`, `title`, `subtitle`)
VALUES
  (@shop, @site, 'EB-BOOT-01', 'en', 'Esva Recovery Boots',
   'Sequential compression boots for both legs, sold per pair. Pick a size below and send us your measurement with the request. Runs from the Esva control unit, sold separately or together as the complete set.'),
  (@shop, @site, 'EB-BOOT-01', 'fr', 'Bottes de récupération Esva',
   'Bottes à compression séquentielle pour les deux jambes, vendues par paire. Choisissez une taille ci-dessous et indiquez votre mesure dans la demande. Fonctionnent avec l’unité de commande Esva, vendue séparément ou en pack complet.'),
  (@shop, @site, 'EB-BOOT-01', 'ru', 'Сапоги для восстановления Esva',
   'Сапоги с последовательной компрессией для обеих ног, продаются парой. Выберите размер ниже и укажите свою мерку в запросе. Работают от блока управления Esva, который продаётся отдельно или в полном комплекте.'),
  (@shop, @site, 'EB-BOOT-01', 'es', 'Botas de recuperación Esva',
   'Botas de compresión secuencial para ambas piernas, se venden por par. Elija una talla abajo e indique su medida en la solicitud. Funcionan con la unidad de control Esva, que se vende por separado o en el pack completo.'),
  (@shop, @site, 'EB-BOOT-01', 'ja', 'Esva リカバリーブーツ',
   '両脚用の順次加圧ブーツ。1 組単位でのご提供です。下でサイズをお選びのうえ、ご依頼時に測った数値をお知らせください。Esva コントロールユニットで動作します（単体販売、またはコンプリートセット）。'),
  (@shop, @site, 'EB-BOOT-01', 'zh', 'Esva 恢复靴',
   '双腿用顺序加压靴，按副出售。在下面选码，并在询价时告诉我们量出来的数。需配 Esva 主机使用，主机可单买，也有含主机的整套。'),
  (@shop, @site, 'EB-SET-01', 'en', 'Esva Recovery Boots — Complete Set',
   'The control unit and a pair of boots, supplied together. The usual starting point if you do not already own a unit. Pick the boot size below.'),
  (@shop, @site, 'EB-SET-01', 'fr', 'Bottes de récupération Esva — Pack complet',
   'L’unité de commande et une paire de bottes, livrées ensemble. Le point de départ habituel si vous ne possédez pas encore d’unité. Choisissez la taille des bottes ci-dessous.'),
  (@shop, @site, 'EB-SET-01', 'ru', 'Сапоги для восстановления Esva — полный комплект',
   'Блок управления и пара сапог в одной поставке. Обычная отправная точка, если блока у вас ещё нет. Размер сапог выберите ниже.'),
  (@shop, @site, 'EB-SET-01', 'es', 'Botas de recuperación Esva — Pack completo',
   'La unidad de control y un par de botas, suministradas juntas. El punto de partida habitual si aún no tiene una unidad. Elija abajo la talla de las botas.'),
  (@shop, @site, 'EB-SET-01', 'ja', 'Esva リカバリーブーツ コンプリートセット',
   'コントロールユニットとブーツ 1 組をまとめてお届けします。ユニットをまだお持ちでない場合の標準的な出発点です。ブーツのサイズは下でお選びください。'),
  (@shop, @site, 'EB-SET-01', 'zh', 'Esva 恢复靴 整套',
   '主机 + 一副恢复靴，一起发货。还没有主机的话，通常从这一套开始。靴子的码在下面选。'),
  (@shop, @site, 'EB-UNIT-01', 'en', 'Esva Control Unit',
   'The pump and controller that drives every Esva garment. Sold on its own for anyone adding a second garment to a unit they already have, or replacing one.'),
  (@shop, @site, 'EB-UNIT-01', 'fr', 'Unité de commande Esva',
   'La pompe et le boîtier qui alimentent tous les manchons Esva. Vendue seule, pour ajouter un second manchon à une unité que vous possédez déjà ou pour la remplacer.'),
  (@shop, @site, 'EB-UNIT-01', 'ru', 'Блок управления Esva',
   'Насос и контроллер, от которых работают все манжеты Esva. Продаётся отдельно — чтобы добавить вторую манжету к уже имеющемуся блоку или заменить его.'),
  (@shop, @site, 'EB-UNIT-01', 'es', 'Unidad de control Esva',
   'La bomba y el controlador que mueven todas las prendas Esva. Se vende suelta, para añadir una segunda prenda a una unidad que ya tenga o para sustituirla.'),
  (@shop, @site, 'EB-UNIT-01', 'ja', 'Esva コントロールユニット',
   'Esva の各カフを動かすポンプとコントローラーです。すでにお持ちのユニットにカフを追加する場合や、買い替えの場合に単体でどうぞ。'),
  (@shop, @site, 'EB-UNIT-01', 'zh', 'Esva 主机',
   '驱动所有 Esva 加压衣的气泵和控制器。单独出售——已经有主机、想再加一件加压衣，或者要换一台，都买这个。'),
  (@shop, @site, 'EB-TROUS-01', 'en', 'Esva Recovery Trousers',
   'Covers the legs and hips in one garment. Pick a size below and send us your measurement with the request. Runs from the same control unit as the boots.'),
  (@shop, @site, 'EB-TROUS-01', 'fr', 'Pantalon de récupération Esva',
   'Couvre les jambes et les hanches en une seule pièce. Choisissez une taille ci-dessous et indiquez votre mesure dans la demande. Fonctionne avec la même unité de commande que les bottes.'),
  (@shop, @site, 'EB-TROUS-01', 'ru', 'Брюки для восстановления Esva',
   'Охватывают ноги и бёдра одной манжетой. Выберите размер ниже и укажите свою мерку в запросе. Работают от того же блока управления, что и сапоги.'),
  (@shop, @site, 'EB-TROUS-01', 'es', 'Pantalón de recuperación Esva',
   'Cubre piernas y caderas en una sola prenda. Elija una talla abajo e indique su medida en la solicitud. Funciona con la misma unidad de control que las botas.'),
  (@shop, @site, 'EB-TROUS-01', 'ja', 'Esva リカバリーパンツ',
   '脚と腰を 1 着でカバーします。下でサイズをお選びのうえ、ご依頼時に測った数値をお知らせください。ブーツと同じコントロールユニットで動作します。'),
  (@shop, @site, 'EB-TROUS-01', 'zh', 'Esva 加压裤',
   '一件覆盖腿部和臀部。在下面选码，并在询价时告诉我们量出来的数。和恢复靴共用同一台主机。'),
  (@shop, @site, 'EB-ARM-01', 'en', 'Esva Arm Sleeves',
   'Compression sleeves for both arms, sold per pair. Two lengths — pick one below. Runs from the same control unit as the boots.'),
  (@shop, @site, 'EB-ARM-01', 'fr', 'Manchons de bras Esva',
   'Manchons de compression pour les deux bras, vendus par paire. Deux longueurs, à choisir ci-dessous. Fonctionnent avec la même unité de commande que les bottes.'),
  (@shop, @site, 'EB-ARM-01', 'ru', 'Манжеты для рук Esva',
   'Компрессионные манжеты для обеих рук, продаются парой. Две длины — выберите ниже. Работают от того же блока управления, что и сапоги.'),
  (@shop, @site, 'EB-ARM-01', 'es', 'Manguitos de brazo Esva',
   'Manguitos de compresión para ambos brazos, se venden por par. Dos largos, a elegir abajo. Funcionan con la misma unidad de control que las botas.'),
  (@shop, @site, 'EB-ARM-01', 'ja', 'Esva アームカフ',
   '両腕用の加圧カフ。1 組単位でのご提供です。長さは 2 種類、下からお選びください。ブーツと同じコントロールユニットで動作します。'),
  (@shop, @site, 'EB-ARM-01', 'zh', 'Esva 臂套',
   '双臂用加压套，按副出售。两种长度，在下面选。和恢复靴共用同一台主机。'),
  (@shop, @site, 'EB-WAIST-01', 'en', 'Esva Waist Belt',
   'Compression belt for the waist. Runs from the same control unit as the boots.'),
  (@shop, @site, 'EB-WAIST-01', 'fr', 'Ceinture de compression Esva',
   'Ceinture de compression pour la taille. Fonctionne avec la même unité de commande que les bottes.'),
  (@shop, @site, 'EB-WAIST-01', 'ru', 'Пояс Esva',
   'Компрессионный пояс на талию. Работает от того же блока управления, что и сапоги.'),
  (@shop, @site, 'EB-WAIST-01', 'es', 'Cinturón de compresión Esva',
   'Cinturón de compresión para la cintura. Funciona con la misma unidad de control que las botas.'),
  (@shop, @site, 'EB-WAIST-01', 'ja', 'Esva ウエストベルト',
   '腰用の加圧ベルトです。ブーツと同じコントロールユニットで動作します。'),
  (@shop, @site, 'EB-WAIST-01', 'zh', 'Esva 加压腰带',
   '腰部用加压带。和恢复靴共用同一台主机。'),
  (@shop, @site, 'EB-BAG-01', 'en', 'Esva Carry Bag',
   'Carries the control unit and one set of garments. For anyone who takes the equipment to training rather than leaving it at home.'),
  (@shop, @site, 'EB-BAG-01', 'fr', 'Sac de transport Esva',
   'Contient l’unité de commande et un jeu de manchons. Pour celles et ceux qui emportent le matériel à l’entraînement plutôt que de le laisser chez eux.'),
  (@shop, @site, 'EB-BAG-01', 'ru', 'Сумка для переноски Esva',
   'Вмещает блок управления и один комплект манжет. Для тех, кто возит оборудование на тренировки, а не оставляет дома.'),
  (@shop, @site, 'EB-BAG-01', 'es', 'Bolsa de transporte Esva',
   'Lleva la unidad de control y un juego de prendas. Para quien se lleva el equipo a entrenar en vez de dejarlo en casa.'),
  (@shop, @site, 'EB-BAG-01', 'ja', 'Esva キャリーバッグ',
   'コントロールユニットとカフ 1 式が入ります。機材を自宅に置かず練習に持って行く方へ。'),
  (@shop, @site, 'EB-BAG-01', 'zh', 'Esva 收纳包',
   '装得下一台主机和一套加压衣。给那些把设备带去训练、而不是放在家里的人。'),
  (@shop, @site, 'EB-LINER-01', 'en', 'Hygienic Boot Liners',
   'Disposable liners worn inside the boots, sold by the pack. Intended for shared use — clinics, gyms and team rooms.'),
  (@shop, @site, 'EB-LINER-01', 'fr', 'Sous-chaussons hygiéniques',
   'Protections jetables portées à l’intérieur des bottes, vendues par lot. Prévues pour un usage partagé : cabinets, salles de sport, vestiaires d’équipe.'),
  (@shop, @site, 'EB-LINER-01', 'ru', 'Гигиенические вкладыши',
   'Одноразовые вкладыши внутрь сапог, продаются упаковкой. Предназначены для совместного использования: клиники, залы, командные комнаты.'),
  (@shop, @site, 'EB-LINER-01', 'es', 'Fundas higiénicas',
   'Fundas desechables que se llevan dentro de las botas, se venden por paquete. Pensadas para uso compartido: clínicas, gimnasios y vestuarios.'),
  (@shop, @site, 'EB-LINER-01', 'ja', '衛生用インナーライナー',
   'ブーツの内側に着ける使い捨てライナー。パック単位でのご提供です。クリニック・ジム・チームルームなど共用の場を想定しています。'),
  (@shop, @site, 'EB-LINER-01', 'zh', '卫生内衬',
   '穿在靴子里的一次性内衬，按包出售。给多人共用的场合——理疗室、健身房、队内更衣室。')
ON DUPLICATE KEY UPDATE
  `title`    = VALUES(`title`),
  `subtitle` = VALUES(`subtitle`);


-- ---------------------------------------------------------------------
-- 三、尺码型号（详情页那排型号选择器）
-- ---------------------------------------------------------------------
--
-- `status` 只有两个取值：'可选' / '停用'（SiteGoodsCombo.STATUS_ON / STATUS_OFF）。
-- 对外查询 `selectPublicByGoods` 只取 '可选' 的，排序 `sortnum, combocode`，
-- **排第一的是详情页默认选中的那个**。
--
-- `combocode` 是稳定标识：名称有六份、还会被改，而深链 ?combo=、单据快照、
-- 按型号做的统计全靠它。别用名字代替。
--
-- 价格同样是 0 = 面议。型号之间真有差价的话，在这里按型号填。
-- ---------------------------------------------------------------------

INSERT INTO `u_s_sitegoods_combo`
  (`shop_code`, `sitecode`, `goodscode`, `combocode`, `price`, `sortnum`, `status`, `createuser`)
VALUES
  (@shop, @site, 'EB-BOOT-01', 'SM', 0, 10, '可选', @who),
  (@shop, @site, 'EB-BOOT-01', 'L', 0, 20, '可选', @who),
  (@shop, @site, 'EB-BOOT-01', 'XL', 0, 30, '可选', @who),
  (@shop, @site, 'EB-SET-01', 'SM', 0, 10, '可选', @who),
  (@shop, @site, 'EB-SET-01', 'L', 0, 20, '可选', @who),
  (@shop, @site, 'EB-SET-01', 'XL', 0, 30, '可选', @who),
  (@shop, @site, 'EB-TROUS-01', 'SM', 0, 10, '可选', @who),
  (@shop, @site, 'EB-TROUS-01', 'L', 0, 20, '可选', @who),
  (@shop, @site, 'EB-TROUS-01', 'XL', 0, 30, '可选', @who),
  (@shop, @site, 'EB-ARM-01', 'STD', 0, 10, '可选', @who),
  (@shop, @site, 'EB-ARM-01', 'LONG', 0, 20, '可选', @who)
ON DUPLICATE KEY UPDATE
  `sortnum`    = VALUES(`sortnum`),
  `status`     = VALUES(`status`),
  `updateuser` = @who;

INSERT INTO `u_s_sitegoods_combo_lang`
  (`shop_code`, `sitecode`, `goodscode`, `combocode`, `lang`, `title`)
VALUES
  (@shop, @site, 'EB-BOOT-01', 'SM', 'en', 'Size S/M'),
  (@shop, @site, 'EB-BOOT-01', 'SM', 'fr', 'Taille S/M'),
  (@shop, @site, 'EB-BOOT-01', 'SM', 'ru', 'Размер S/M'),
  (@shop, @site, 'EB-BOOT-01', 'SM', 'es', 'Talla S/M'),
  (@shop, @site, 'EB-BOOT-01', 'SM', 'ja', 'サイズ S/M'),
  (@shop, @site, 'EB-BOOT-01', 'SM', 'zh', '尺码 S/M'),
  (@shop, @site, 'EB-BOOT-01', 'L', 'en', 'Size L'),
  (@shop, @site, 'EB-BOOT-01', 'L', 'fr', 'Taille L'),
  (@shop, @site, 'EB-BOOT-01', 'L', 'ru', 'Размер L'),
  (@shop, @site, 'EB-BOOT-01', 'L', 'es', 'Talla L'),
  (@shop, @site, 'EB-BOOT-01', 'L', 'ja', 'サイズ L'),
  (@shop, @site, 'EB-BOOT-01', 'L', 'zh', '尺码 L'),
  (@shop, @site, 'EB-BOOT-01', 'XL', 'en', 'Size XL'),
  (@shop, @site, 'EB-BOOT-01', 'XL', 'fr', 'Taille XL'),
  (@shop, @site, 'EB-BOOT-01', 'XL', 'ru', 'Размер XL'),
  (@shop, @site, 'EB-BOOT-01', 'XL', 'es', 'Talla XL'),
  (@shop, @site, 'EB-BOOT-01', 'XL', 'ja', 'サイズ XL'),
  (@shop, @site, 'EB-BOOT-01', 'XL', 'zh', '尺码 XL'),
  (@shop, @site, 'EB-SET-01', 'SM', 'en', 'Size S/M'),
  (@shop, @site, 'EB-SET-01', 'SM', 'fr', 'Taille S/M'),
  (@shop, @site, 'EB-SET-01', 'SM', 'ru', 'Размер S/M'),
  (@shop, @site, 'EB-SET-01', 'SM', 'es', 'Talla S/M'),
  (@shop, @site, 'EB-SET-01', 'SM', 'ja', 'サイズ S/M'),
  (@shop, @site, 'EB-SET-01', 'SM', 'zh', '尺码 S/M'),
  (@shop, @site, 'EB-SET-01', 'L', 'en', 'Size L'),
  (@shop, @site, 'EB-SET-01', 'L', 'fr', 'Taille L'),
  (@shop, @site, 'EB-SET-01', 'L', 'ru', 'Размер L'),
  (@shop, @site, 'EB-SET-01', 'L', 'es', 'Talla L'),
  (@shop, @site, 'EB-SET-01', 'L', 'ja', 'サイズ L'),
  (@shop, @site, 'EB-SET-01', 'L', 'zh', '尺码 L'),
  (@shop, @site, 'EB-SET-01', 'XL', 'en', 'Size XL'),
  (@shop, @site, 'EB-SET-01', 'XL', 'fr', 'Taille XL'),
  (@shop, @site, 'EB-SET-01', 'XL', 'ru', 'Размер XL'),
  (@shop, @site, 'EB-SET-01', 'XL', 'es', 'Talla XL'),
  (@shop, @site, 'EB-SET-01', 'XL', 'ja', 'サイズ XL'),
  (@shop, @site, 'EB-SET-01', 'XL', 'zh', '尺码 XL'),
  (@shop, @site, 'EB-TROUS-01', 'SM', 'en', 'Size S/M'),
  (@shop, @site, 'EB-TROUS-01', 'SM', 'fr', 'Taille S/M'),
  (@shop, @site, 'EB-TROUS-01', 'SM', 'ru', 'Размер S/M'),
  (@shop, @site, 'EB-TROUS-01', 'SM', 'es', 'Talla S/M'),
  (@shop, @site, 'EB-TROUS-01', 'SM', 'ja', 'サイズ S/M'),
  (@shop, @site, 'EB-TROUS-01', 'SM', 'zh', '尺码 S/M'),
  (@shop, @site, 'EB-TROUS-01', 'L', 'en', 'Size L'),
  (@shop, @site, 'EB-TROUS-01', 'L', 'fr', 'Taille L'),
  (@shop, @site, 'EB-TROUS-01', 'L', 'ru', 'Размер L'),
  (@shop, @site, 'EB-TROUS-01', 'L', 'es', 'Talla L'),
  (@shop, @site, 'EB-TROUS-01', 'L', 'ja', 'サイズ L'),
  (@shop, @site, 'EB-TROUS-01', 'L', 'zh', '尺码 L'),
  (@shop, @site, 'EB-TROUS-01', 'XL', 'en', 'Size XL'),
  (@shop, @site, 'EB-TROUS-01', 'XL', 'fr', 'Taille XL'),
  (@shop, @site, 'EB-TROUS-01', 'XL', 'ru', 'Размер XL'),
  (@shop, @site, 'EB-TROUS-01', 'XL', 'es', 'Talla XL'),
  (@shop, @site, 'EB-TROUS-01', 'XL', 'ja', 'サイズ XL'),
  (@shop, @site, 'EB-TROUS-01', 'XL', 'zh', '尺码 XL'),
  (@shop, @site, 'EB-ARM-01', 'STD', 'en', 'Standard'),
  (@shop, @site, 'EB-ARM-01', 'STD', 'fr', 'Standard'),
  (@shop, @site, 'EB-ARM-01', 'STD', 'ru', 'Стандартный'),
  (@shop, @site, 'EB-ARM-01', 'STD', 'es', 'Estándar'),
  (@shop, @site, 'EB-ARM-01', 'STD', 'ja', '標準'),
  (@shop, @site, 'EB-ARM-01', 'STD', 'zh', '标准'),
  (@shop, @site, 'EB-ARM-01', 'LONG', 'en', 'Long'),
  (@shop, @site, 'EB-ARM-01', 'LONG', 'fr', 'Long'),
  (@shop, @site, 'EB-ARM-01', 'LONG', 'ru', 'Удлинённый'),
  (@shop, @site, 'EB-ARM-01', 'LONG', 'es', 'Largo'),
  (@shop, @site, 'EB-ARM-01', 'LONG', 'ja', 'ロング'),
  (@shop, @site, 'EB-ARM-01', 'LONG', 'zh', '加长')
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`);


-- ---------------------------------------------------------------------
-- 四、分类译名
-- ---------------------------------------------------------------------
--
-- `sitecategory` 是一列自由文本，没有父子结构；前台 app/lib/categories.ts 按
-- 第一个分隔符（/、／、>）拆成两级，所以 'Compression Therapy/Boots' 会显示成
-- 「Compression Therapy」大组下挂「Boots」，而筛选参数仍是整串。
--
-- catkey = 拆开后用**半角 /** 逐级拼回去的前缀，规则要和后端
-- SiteCategoryService.catKeysOf 逐字一致；不一致的表现是「译名存进去了但前台查不到」
-- ——不报错，只是一直显示原文。
--
-- ⚠ **只写 en 以外的五种**。后端 namesFor 对默认语言直接返回空字典
-- （「默认语言本来就用原文」），写 en 进去是永远读不到的死数据。
-- ---------------------------------------------------------------------

INSERT INTO `u_s_sitecategory_lang`
  (`shop_code`, `sitecode`, `catkey`, `lang`, `name`)
VALUES
  (@shop, @site, 'Compression Therapy', 'fr', 'Pressothérapie'),
  (@shop, @site, 'Compression Therapy', 'ru', 'Прессотерапия'),
  (@shop, @site, 'Compression Therapy', 'es', 'Presoterapia'),
  (@shop, @site, 'Compression Therapy', 'ja', '加圧セラピー'),
  (@shop, @site, 'Compression Therapy', 'zh', '空气波加压'),
  (@shop, @site, 'Compression Therapy/Boots', 'fr', 'Bottes'),
  (@shop, @site, 'Compression Therapy/Boots', 'ru', 'Сапоги'),
  (@shop, @site, 'Compression Therapy/Boots', 'es', 'Botas'),
  (@shop, @site, 'Compression Therapy/Boots', 'ja', 'ブーツ'),
  (@shop, @site, 'Compression Therapy/Boots', 'zh', '恢复靴'),
  (@shop, @site, 'Compression Therapy/Trousers', 'fr', 'Pantalons'),
  (@shop, @site, 'Compression Therapy/Trousers', 'ru', 'Брюки'),
  (@shop, @site, 'Compression Therapy/Trousers', 'es', 'Pantalones'),
  (@shop, @site, 'Compression Therapy/Trousers', 'ja', 'パンツ'),
  (@shop, @site, 'Compression Therapy/Trousers', 'zh', '加压裤'),
  (@shop, @site, 'Compression Therapy/Arm Sleeves', 'fr', 'Manchons de bras'),
  (@shop, @site, 'Compression Therapy/Arm Sleeves', 'ru', 'Манжеты для рук'),
  (@shop, @site, 'Compression Therapy/Arm Sleeves', 'es', 'Manguitos de brazo'),
  (@shop, @site, 'Compression Therapy/Arm Sleeves', 'ja', 'アームカフ'),
  (@shop, @site, 'Compression Therapy/Arm Sleeves', 'zh', '臂套'),
  (@shop, @site, 'Compression Therapy/Waist', 'fr', 'Ceintures'),
  (@shop, @site, 'Compression Therapy/Waist', 'ru', 'Пояса'),
  (@shop, @site, 'Compression Therapy/Waist', 'es', 'Cinturones'),
  (@shop, @site, 'Compression Therapy/Waist', 'ja', 'ウエスト'),
  (@shop, @site, 'Compression Therapy/Waist', 'zh', '腰带'),
  (@shop, @site, 'Control Units', 'fr', 'Unités de commande'),
  (@shop, @site, 'Control Units', 'ru', 'Блоки управления'),
  (@shop, @site, 'Control Units', 'es', 'Unidades de control'),
  (@shop, @site, 'Control Units', 'ja', 'コントロールユニット'),
  (@shop, @site, 'Control Units', 'zh', '主机'),
  (@shop, @site, 'Accessories', 'fr', 'Accessoires'),
  (@shop, @site, 'Accessories', 'ru', 'Аксессуары'),
  (@shop, @site, 'Accessories', 'es', 'Accesorios'),
  (@shop, @site, 'Accessories', 'ja', 'アクセサリー'),
  (@shop, @site, 'Accessories', 'zh', '配件')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`);


-- ---------------------------------------------------------------------
-- 五、跑完对一下
-- ---------------------------------------------------------------------

SELECT '商品' AS 项, COUNT(*) AS 条数 FROM `u_s_sitegoods`
 WHERE `sitecode` = @site AND `publishstatus` = '已上架'
UNION ALL SELECT '其中主推', COUNT(*) FROM `u_s_sitegoods`
 WHERE `sitecode` = @site AND `featured` = 1
UNION ALL SELECT '商品文案（应为商品数×6）', COUNT(*) FROM `u_s_sitegoods_lang`
 WHERE `sitecode` = @site
UNION ALL SELECT '尺码型号', COUNT(*) FROM `u_s_sitegoods_combo`
 WHERE `sitecode` = @site AND `status` = '可选'
UNION ALL SELECT '型号名称（应为型号数×6）', COUNT(*) FROM `u_s_sitegoods_combo_lang`
 WHERE `sitecode` = @site
UNION ALL SELECT '分类译名（应为 7×5）', COUNT(*) FROM `u_s_sitecategory_lang`
 WHERE `sitecode` = @site;

-- 前台侧栏分类导航取的就是这一串
SELECT DISTINCT `sitecategory` FROM `u_s_sitegoods`
 WHERE `sitecode` = @site AND `publishstatus` = '已上架' ORDER BY 1;


-- ---------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------
--
-- 这批商品的编码都以 EB- 开头，删起来不会碰到别的数据：
--
--   DELETE FROM u_s_sitegoods_combo_lang WHERE sitecode = 'DLZ2049739702' AND goodscode LIKE 'EB-%';
--   DELETE FROM u_s_sitegoods_combo      WHERE sitecode = 'DLZ2049739702' AND goodscode LIKE 'EB-%';
--   DELETE FROM u_s_sitegoods_lang       WHERE sitecode = 'DLZ2049739702' AND goodscode LIKE 'EB-%';
--   DELETE FROM u_s_sitegoods            WHERE sitecode = 'DLZ2049739702' AND goodscode LIKE 'EB-%';
--   DELETE FROM u_s_sitecategory_lang    WHERE sitecode = 'DLZ2049739702';
--
-- 删光之后前台回到现在的样子：首页「主推商品」整块不渲染，列表页为空。

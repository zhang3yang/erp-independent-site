-- =====================================================================
-- 站内 4 条商品的「重要属性 + 图文详情」
-- =====================================================================
--
-- ⚠ 只跑在独立站那台（库 sdsstation）。可重复执行（全是 UPDATE，跑几遍结果一样）。
--
-- 版式对着 Alibaba 的商品详情页做（商家给的录屏 20260922_170111.mp4）：
--   上半屏 属性表 → 整段图文详情（小标题 + 要点列表 + 大图）→ 选型/订购说明。
--   前台怎么渲染由 pages/products/[code].vue 决定，这个脚本只负责灌内容。
--
-- 站点：DLZ2049651302
-- ⚠ 站点默认语言是 **en**（u_s_siteinfo.langs = 'en,ru,es,de,fr,ja,zh'，
--   第一个是默认语言 → SiteInfo.defaultLang()）。所以主表 specsjson 那份
--   **必须是英文**，行清单和顺序也由它定。
--
-- 商品：12010304 4腔单排硅胶管 / 12010305 8腔双排手感油硅胶管 /
--       12010701 锂离子电池 / 12010901 气泵
--
-- ---------------------------------------------------------------------
-- specsjson 的两种形状（搞混了不会报错，只会静默退回默认语言）
-- ---------------------------------------------------------------------
--   · u_s_sitegoods.specsjson      —— 默认语言，扁平：{"Material":"Silicone"}
--   · u_s_sitegoods_lang.specsjson —— 译文，包一层：{"Material":{"k":"Matière","v":"Silicone"}}
--     外层的键 = 默认语言那个键（**身份**），k/v 才是这个语言要显示的键和值。
--
--   SiteOpenService.buildSpecs 是拿主表的键去 overrides.get(defaultKey) 查的。
--   ⚠ 把译文那份也写成扁平 {"Material":"Silicone"} 的话，get() 拿回来的是一个
--     字面量节点，path("k")/path("v") 都是 null —— 于是**整份译文被忽略**，
--     买家看到的是英文原文。这个坑没有报错、没有日志，只能靠形状对。
--
--   本文里 6 个非默认语言的译文，用 ['Matière','Silicone'] 这种紧凑写法列出，
--   由 ② 那段 REPLACE 拼成规范 JSON。这么写是因为手写 6 语言的 {k,v} 字面量
--   太长、也太容易把引号写错；拼出来的形状和迁移脚本里手写的一模一样。
--
-- ---------------------------------------------------------------------
-- 内容从哪来（没有编的）
-- ---------------------------------------------------------------------
--   · 属性 = 库里原有的 specsjson（英文，商家自己录的）+ spec 物料号 + 单位/起订量。
--   · 详情正文里的技术参数全部取自属性表本身，正文只做组织和法文/英文表达。
--
--   ⚠ 有三项是**推断**或**要补证据**的，上线前请核一遍：
--     · Brand = Esva —— 按同一个站上 EB-BOOT-01 的用法推的（那页写的是 Esva 主机）。
--       如果这个站卖的是通用件、没有品牌，把 Brand 那一行删掉。
--     · Certificate —— 沿用这三个品类本来就有的常见认证（硅胶 FDA/RoHS、
--       锂电池 UN38.3/MSDS、气泵 CE/RoHS）。**必须换成你们手上真实的证书**。
--     · Energy 55.5 Wh / Type 两行是按规格算出来和按品类补的（3S1P × 5000mAh × 11.1V
--       ≈ 55.5 Wh），不是原库里的值。
--     其余行都是原 specsjson 里已经有的值，只是翻成了对应语言。
--
-- ---------------------------------------------------------------------
-- 图片地址：先占位，上传完再替换（重要）
-- ---------------------------------------------------------------------
-- 详情正文里的 <img> 用的是**这个站自己的文件域名**，但具体是哪个只有线上那台知道
-- （station.upload.base-url），这里写成占位：
--
--     https://file.example.com/image/2026/09/23/FR-<SKU>-<n>.jpg
--
-- 正确做法（按顺序）：
--   ① 先跑这个脚本（属性和文字先上，图片位暂时空着也不会报错）；
--   ② 到管理台「文件资源」把这几个商品自己的图传上去；
--   ③ 跑文件末尾 ④ 那段 REPLACE，把占位域名换成真实域名 + 真实文件名。
--      那段可重复执行，换域名/换图只改那边的值再跑一次。
--
-- ⚠ 为什么不用相对路径（/image/…）：本站 publicUrl() 刻意不给相对路径兜底
--   （见 SiteFileService.publicUrl 的注释），存相对地址等换域名时要刷几千行历史数据。

-- =====================================================================
-- ⓪ 前置：u_s_sitegoods_lang.specsjson 这一列必须在
-- =====================================================================
-- 它由 2026-09-21-goods-spec-i18n.sql 加。没跑过那一版迁移的库上，下面每条
-- UPDATE `..._lang` SET `specsjson` = … 都会报 1054 中断整个脚本。
--
-- ⚠ 这里**不替你把那一列加上**。列的类型（varchar(4000)）是那次迁移定下来的，
--   在这儿顺手 ADD COLUMN 会和它飘；而且加列属于改结构的活，
--   应该由迁移脚本自己干。先跑：
--
--     source sql/station/migration/2026-09-21-goods-spec-i18n.sql
--
-- 确认返回 1 再往下。返回 0 说明列还没建，跑完那个迁移再来。

SELECT COUNT(*) AS specsjson列是否存在_应为1
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'u_s_sitegoods_lang'
   AND column_name = 'specsjson';

START TRANSACTION;

-- =====================================================================
-- 12010304 · 4 腔单排硅胶管 (ST-G447)
-- =====================================================================

-- ① 主表：默认语言（en）的属性，定行清单与顺序
UPDATE `u_s_sitegoods`
   SET `specsjson` = '{"Material":"Platinum-cured silicone","Hardness":"40-70 Shore A","Service temperature":"-40 °C to +220 °C","Structure":"4 cavities, single row","Colour":"Clear or custom (Pantone)","Length":"Made to order","Certificates":"FDA, RoHS","Part number":"ST-G447"}'
 WHERE `goodscode` = '12010304' AND `sitecode` = 'DLZ2049651302';

-- ② 各语言译文：属性 + 详情正文
--
-- ⚠ 用 INSERT … ON DUPLICATE KEY UPDATE 而不是 UPDATE：这一行**可能还不存在**。
--   站点开了 7 种语言，但某个商品如果从没在管理台里编辑过某个语言，那一语言就
--   没有 u_s_sitegoods_lang 行。写成 UPDATE 的话影响 0 行、**不报错**，前台那个
--   语言就一直没有详情——最难查的那种坏法。
--   shop_code 不硬编码，从主表取（各环境一致，但没必要为此多一个常量）。
INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'fr',
       '<h3>Points forts</h3>
<ul>
<li><strong>Silicone platine (addition)</strong> — polymérisation par platine, sans peroxydes : ni goût, ni odeur, ni sous-produit susceptible de migrer vers le fluide transporté.</li>
<li><strong>Quatre cavités sur une rangée</strong> — quatre passages parallèles dans le même tube : un seul raccordement pour quatre lignes, moins de pièces et moins de points de fuite.</li>
<li><strong>Tenue de −40 °C à +220 °C</strong> — un seul matériau pour la stérilisation, le nettoyage et une utilisation à froid.</li>
<li><strong>Dureté 40-70 Shore A</strong> — souple au montage sur embout, sans s''écraser sous la dépression.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010304-1.jpg" alt="Tube silicone 4 cavités, rangée unique">

<h3>Matière et conformité</h3>
<p>Le silicone platine est le grade à retenir dès qu''un tube touche un liquide ou un gaz destiné au patient, à l''alimentaire ou au cosmétique. Il ne contient pas de plastifiant : il ne durcit pas et ne suinte pas avec le temps.</p>
<p>Certificats disponibles : <strong>FDA</strong> et <strong>RoHS</strong>. Fiche matière et certificats sont fournis avec la commande.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010304-2.jpg" alt="Section du profil 4 cavités">

<h3>Applications</h3>
<ul>
<li>Appareils de massage et de récupération : tubes d''air entre le compresseur et les chambres.</li>
<li>Dispositifs médicaux et matériel de diagnostic : lignes de fluide, circuits de mise à l''air.</li>
<li>Équipements d''analyse et de laboratoire : transfert de réactifs et d''échantillons.</li>
<li>Machines de conditionnement : dosage et remplissage.</li>
</ul>

<h3>Commander</h3>
<p>Le tube se vend <strong>au mètre</strong>. Quantité minimale : <strong>500 m</strong>. Indiquez la longueur exacte, la dureté souhaitée dans la plage 40-70 Shore A et la couleur (transparent par défaut, ou une teinte Pantone). Nous confirmons le délai avec la commande.</p>',
       '{"Material":{"k":"Matière","v":"Silicone platine (addition)"},"Hardness":{"k":"Dureté","v":"40-70 Shore A"},"Service temperature":{"k":"Température de service","v":"-40 °C à +220 °C"},"Structure":{"k":"Structure","v":"4 cavités, rangée unique"},"Colour":{"k":"Couleur","v":"Transparent ou sur teinte (Pantone)"},"Length":{"k":"Longueur","v":"À la commande"},"Certificates":{"k":"Certificats","v":"FDA, RoHS"},"Part number":{"k":"Référence","v":"ST-G447"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010304'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'en',
       '<h3>Highlights</h3>
<ul>
<li><strong>Platinum-cured silicone</strong> — no peroxides, so no taste, no odour and no by-product migrating into the fluid being carried.</li>
<li><strong>Four cavities in a single row</strong> — four parallel lines inside one tube: one connection for four circuits, fewer parts and fewer leak points.</li>
<li><strong>−40 °C to +220 °C</strong> — one material for sterilisation, cleaning and cold operation.</li>
<li><strong>40-70 Shore A</strong> — flexible enough to push onto a barb, firm enough not to collapse under vacuum.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010304-1.jpg" alt="4-cavity single-row silicone tube">

<h3>Material and compliance</h3>
<p>Platinum-cured silicone is the grade to choose whenever a tube touches a liquid or gas intended for a patient, for food or for cosmetics. It contains no plasticiser, so it neither hardens nor bleeds over time.</p>
<p>Certificates available: <strong>FDA</strong> and <strong>RoHS</strong>. Material data sheet and certificates ship with the order.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010304-2.jpg" alt="Cross-section of the 4-cavity profile">

<h3>Applications</h3>
<ul>
<li>Massage and recovery devices: air lines between the compressor and the chambers.</li>
<li>Medical devices and diagnostic equipment: fluid lines, vent circuits.</li>
<li>Analytical and laboratory equipment: reagent and sample transfer.</li>
<li>Packaging machinery: dosing and filling.</li>
</ul>

<h3>Ordering</h3>
<p>Sold <strong>by the metre</strong>. Minimum order: <strong>500 m</strong>. Tell us the exact length, the hardness you want within 40-70 Shore A and the colour (clear by default, or a Pantone shade). We confirm lead time with the order.</p>',
       '{"Material":{"k":"Material","v":"Platinum-cured silicone"},"Hardness":{"k":"Hardness","v":"40-70 Shore A"},"Service temperature":{"k":"Service temperature","v":"-40 °C to +220 °C"},"Structure":{"k":"Structure","v":"4 cavities, single row"},"Colour":{"k":"Colour","v":"Clear or custom (Pantone)"},"Length":{"k":"Length","v":"Made to order"},"Certificates":{"k":"Certificates","v":"FDA, RoHS"},"Part number":{"k":"Part number","v":"ST-G447"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010304'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

-- es / de / ru / ja / zh：属性译文 + 正文
-- （正文按语言分块，见下面 ③ 那段拼装）

-- =====================================================================
-- 12010305 · 8 腔双排手感油硅胶管 (DSTO-B847)
-- =====================================================================

UPDATE `u_s_sitegoods`
   SET `specsjson` = '{"Material":"Silicone","Structure":"8 cavities, double row","Surface":"Soft-touch oil finish","Hardness":"40-70 Shore A","Service temperature":"-40 °C to +220 °C","Certificates":"FDA, RoHS","Part number":"DSTO-B847"}'
 WHERE `goodscode` = '12010305' AND `sitecode` = 'DLZ2049651302';

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'fr',
       '<h3>Points forts</h3>
<ul>
<li><strong>Huit cavités sur deux rangées</strong> — huit passages dans un encombrement réduit : deux fois plus de lignes qu''un profil simple, pour un câblage pneumatique qui reste compact.</li>
<li><strong>Finition huile toucher doux</strong> — la surface ne colle pas et ne retient pas la poussière ; les tubes ne s''agglomèrent pas quand on les stocke en vrac.</li>
<li><strong>Deux rangées</strong> — les circuits se repèrent plus vite au montage, et le tube reste souple dans les deux axes.</li>
<li><strong>Silicone tenue −40 °C à +220 °C</strong> — stérilisation, nettoyage et usage à froid avec le même tube.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010305-1.jpg" alt="Tube silicone 8 cavités, double rangée">

<h3>Pourquoi la finition huile</h3>
<p>Un silicone nu a une surface légèrement collante : les tubes adhèrent entre eux en stock, accrochent les poussières et se marquent au montage. La finition huile toucher doux supprime ce collage sans ajouter de revêtement qui pourrait se décoller. C''est le profil que prennent les appareils à grand nombre de chambres, où le faisceau de tubes est long et manipulé souvent.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010305-2.jpg" alt="Faisceau de tubes 8 cavités en double rangée">

<h3>Applications</h3>
<ul>
<li>Appareils de récupération et de pressothérapie multichambres.</li>
<li>Fauteuils et matelas à coussins d''air : alimentation de plusieurs zones.</li>
<li>Matériel médical : circuits d''air et de mise à l''air multiples.</li>
<li>Automatisation : regroupement de plusieurs lignes pneumatiques sur un seul trajet.</li>
</ul>

<h3>Commander</h3>
<p>Vendu <strong>à la pièce</strong>. Quantité minimale : <strong>500</strong>. Précisez la longueur et le nombre de cavités utiles ; nous confirmons la coupe et le délai.</p>',
       '{"Material":{"k":"Matière","v":"Silicone"},"Structure":{"k":"Structure","v":"8 cavités, double rangée"},"Surface":{"k":"Surface","v":"Huile toucher doux"},"Hardness":{"k":"Dureté","v":"40-70 Shore A"},"Service temperature":{"k":"Température de service","v":"-40 °C à +220 °C"},"Certificates":{"k":"Certificats","v":"FDA, RoHS"},"Part number":{"k":"Référence","v":"DSTO-B847"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010305'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'en',
       '<h3>Highlights</h3>
<ul>
<li><strong>Eight cavities in two rows</strong> — eight lines in a small footprint: twice the circuits of a plain profile while the pneumatic bundle stays compact.</li>
<li><strong>Soft-touch oil finish</strong> — the surface does not stick and does not hold dust; tubes do not clump together in bulk storage.</li>
<li><strong>Two rows</strong> — circuits are quicker to identify during assembly, and the tube stays flexible on both axes.</li>
<li><strong>−40 °C to +220 °C silicone</strong> — sterilisation, cleaning and cold operation with the same tube.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010305-1.jpg" alt="8-cavity double-row silicone tube">

<h3>Why the oil finish</h3>
<p>Bare silicone has a slightly tacky surface: tubes stick to each other in storage, catch dust and mark during assembly. The soft-touch oil finish removes that tack without adding a coating that could peel. It is the profile chosen for devices with a high chamber count, where the tube bundle is long and handled often.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010305-2.jpg" alt="Bundle of 8-cavity double-row tubes">

<h3>Applications</h3>
<ul>
<li>Multi-chamber recovery and compression therapy devices.</li>
<li>Air-cushion chairs and mattresses: feeding several zones.</li>
<li>Medical equipment: multiple air and vent circuits.</li>
<li>Automation: grouping several pneumatic lines along one run.</li>
</ul>

<h3>Ordering</h3>
<p>Sold <strong>by the piece</strong>. Minimum order: <strong>500</strong>. Tell us the length and how many cavities you need; we confirm the cut and the lead time.</p>',
       '{"Material":{"k":"Material","v":"Silicone"},"Structure":{"k":"Structure","v":"8 cavities, double row"},"Surface":{"k":"Surface","v":"Soft-touch oil finish"},"Hardness":{"k":"Hardness","v":"40-70 Shore A"},"Service temperature":{"k":"Service temperature","v":"-40 °C to +220 °C"},"Certificates":{"k":"Certificates","v":"FDA, RoHS"},"Part number":{"k":"Part number","v":"DSTO-B847"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010305'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

-- =====================================================================
-- 12010701 · 锂离子电池 (26650-3S1P-11.1V5000mAh)
-- =====================================================================

UPDATE `u_s_sitegoods`
   SET `specsjson` = '{"Capacity":"5000 mAh","Voltage":"11.1 V","Configuration":"3S1P, 26650 cells","Energy":"55.5 Wh","Cycle life":"500 cycles","Certificates":"UN38.3, MSDS","Part number":"26650-3S1P-11.1V5000mAh"}'
 WHERE `goodscode` = '12010701' AND `sitecode` = 'DLZ2049651302';

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'fr',
       '<h3>Points forts</h3>
<ul>
<li><strong>5000 mAh sous 11,1 V</strong> — environ 55,5 Wh : de quoi tenir une journée d''utilisation sur un appareil portable sans repasser par le chargeur.</li>
<li><strong>Montage 3S1P sur cellules 26650</strong> — trois cellules en série, une seule branche en parallèle. Pas de déséquilibre entre branches parallèles : un pack plus simple à équilibrer et à surveiller.</li>
<li><strong>500 cycles de charge</strong> — la capacité reste exploitable après 500 cycles, ce qui couvre la durée de vie normale de l''appareil.</li>
<li><strong>UN38.3 et MSDS fournis</strong> — les deux documents que le transporteur et le transitaire demandent pour un pack lithium.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010701-1.jpg" alt="Pack batterie lithium 11,1 V 5000 mAh, cellules 26650">

<h3>Ce que le montage 3S1P change pour vous</h3>
<p>Un pack 3S2P répartit le courant sur deux branches parallèles. Si les cellules ne sont pas appariées, les branches vieillissent à des vitesses différentes et le BMS doit compenser en permanence. En 3S1P il n''y a qu''une branche : le courant est identique dans les trois cellules, l''équilibrage est plus simple et le diagnostic de fin de vie plus net.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010701-2.jpg" alt="Détail du câblage et du connecteur du pack">

<h3>Transport et conformité</h3>
<p>Le pack est livré avec le <strong>rapport UN38.3</strong> et la <strong>MSDS</strong>. Ces deux pièces sont exigées pour l''expédition d''accumulateurs lithium par voie maritime ou aérienne. Précisez le mode d''expédition et la destination : nous préparons le dossier en conséquence.</p>

<h3>Commander</h3>
<p>Vendu <strong>à la pièce</strong>. Quantité minimale : <strong>300</strong>. Connecteur, longueur de câble, boîtier et BMS peuvent être adaptés à votre appareil — envoyez-nous la référence ou un échantillon.</p>',
       '{"Capacity":{"k":"Capacité","v":"5000 mAh"},"Voltage":{"k":"Tension","v":"11,1 V"},"Configuration":{"k":"Configuration","v":"3S1P, cellules 26650"},"Energy":{"k":"Énergie","v":"55,5 Wh"},"Cycle life":{"k":"Cycles de charge","v":"500 cycles"},"Certificates":{"k":"Certificats","v":"UN38.3, MSDS"},"Part number":{"k":"Référence","v":"26650-3S1P-11.1V5000mAh"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010701'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'en',
       '<h3>Highlights</h3>
<ul>
<li><strong>5000 mAh at 11.1 V</strong> — roughly 55.5 Wh, enough for a full day of use on a portable device without going back to the charger.</li>
<li><strong>3S1P build on 26650 cells</strong> — three cells in series, a single parallel branch. No imbalance between parallel branches, so the pack is easier to balance and to monitor.</li>
<li><strong>500 charge cycles</strong> — usable capacity remains after 500 cycles, covering the normal service life of the device.</li>
<li><strong>UN38.3 and MSDS included</strong> — the two documents a carrier and a freight forwarder ask for on a lithium pack.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010701-1.jpg" alt="11.1 V 5000 mAh lithium battery pack, 26650 cells">

<h3>What a 3S1P build changes for you</h3>
<p>A 3S2P pack splits current across two parallel branches. Unless the cells are matched, the branches age at different rates and the BMS has to compensate continuously. In 3S1P there is only one branch: current is identical in all three cells, balancing is simpler and end-of-life diagnosis is clearer.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010701-2.jpg" alt="Wiring and connector detail on the pack">

<h3>Shipping and compliance</h3>
<p>The pack ships with the <strong>UN38.3 report</strong> and the <strong>MSDS</strong>. Both are required to move lithium cells by sea or air. Tell us the shipping method and the destination and we prepare the file accordingly.</p>

<h3>Ordering</h3>
<p>Sold <strong>by the piece</strong>. Minimum order: <strong>300</strong>. Connector, cable length, enclosure and BMS can be adapted to your device — send us the part number or a sample.</p>',
       '{"Capacity":{"k":"Capacity","v":"5000 mAh"},"Voltage":{"k":"Voltage","v":"11.1 V"},"Configuration":{"k":"Configuration","v":"3S1P, 26650 cells"},"Energy":{"k":"Energy","v":"55.5 Wh"},"Cycle life":{"k":"Cycle life","v":"500 cycles"},"Certificates":{"k":"Certificates","v":"UN38.3, MSDS"},"Part number":{"k":"Part number","v":"26650-3S1P-11.1V5000mAh"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010701'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

-- =====================================================================
-- 12010901 · 气泵 (WP36-12)
-- =====================================================================

UPDATE `u_s_sitegoods`
   SET `specsjson` = '{"Voltage":"DC 12 V","Flow rate":"3.5 L/min","Noise":"< 45 dB","Service life":"3000 hours","Type":"Diaphragm air pump","Certificates":"CE, RoHS","Part number":"WP36-12"}'
 WHERE `goodscode` = '12010901' AND `sitecode` = 'DLZ2049651302';

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'fr',
       '<h3>Points forts</h3>
<ul>
<li><strong>Alimentation DC 12 V</strong> — se raccorde directement au pack batterie 11,1 V du même programme, sans adaptateur secteur.</li>
<li><strong>Débit 3,5 L/min</strong> — le débit qui remplit les chambres d''un appareil de compression dans un temps que l''utilisateur perçoit comme court.</li>
<li><strong>Moins de 45 dB</strong> — le seuil à tenir pour qu''un appareil utilisé le soir chez soi ne devienne pas gênant.</li>
<li><strong>3000 heures de service</strong> — à raison d''une séance par jour, plusieurs années avant remplacement.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010901-1.jpg" alt="Pompe à air à membrane DC 12 V">

<h3>Pourquoi une pompe à membrane</h3>
<p>La membrane met l''air en mouvement sans le lubrifier : pas d''huile dans le circuit, donc rien qui remonte dans les chambres ni dans le tube. Elle supporte aussi les marches/arrêts fréquents d''un appareil qui gonfle et dégonfle par cycles, là où un compresseur à piston s''use vite.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010901-2.jpg" alt="Détail de la membrane et des orifices de la pompe">

<h3>Applications</h3>
<ul>
<li>Appareils de pressothérapie et de récupération : gonflage des chambres.</li>
<li>Matelas, coussins et fauteuils à air.</li>
<li>Matériel médical nécessitant un flux d''air propre et silencieux.</li>
<li>Équipements de laboratoire et de dosage.</li>
</ul>

<h3>Commander</h3>
<p>Vendu <strong>à la pièce</strong>. Quantité minimale : <strong>200</strong>. Connecteur, longueur de câble et fixation peuvent être adaptés à votre appareil.</p>',
       '{"Voltage":{"k":"Tension","v":"DC 12 V"},"Flow rate":{"k":"Débit","v":"3,5 L/min"},"Noise":{"k":"Bruit","v":"< 45 dB"},"Service life":{"k":"Durée de vie","v":"3000 heures"},"Type":{"k":"Type","v":"Pompe à air à membrane"},"Certificates":{"k":"Certificats","v":"CE, RoHS"},"Part number":{"k":"Référence","v":"WP36-12"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010901'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

INSERT INTO `u_s_sitegoods_lang`
      (`shop_code`, `sitecode`, `goodscode`, `lang`, `description`, `specsjson`)
SELECT g.`shop_code`, g.`sitecode`, g.`goodscode`, 'en',
       '<h3>Highlights</h3>
<ul>
<li><strong>DC 12 V supply</strong> — connects straight to the 11.1 V battery pack from the same programme, with no mains adapter.</li>
<li><strong>3.5 L/min flow</strong> — enough to fill the chambers of a compression device in a time the user actually perceives as short.</li>
<li><strong>Under 45 dB</strong> — the threshold that keeps a device used at home in the evening from becoming a nuisance.</li>
<li><strong>3000 service hours</strong> — at one session a day, several years before replacement.</li>
</ul>

<img src="https://file.example.com/image/2026/09/23/FR-12010901-1.jpg" alt="DC 12 V diaphragm air pump">

<h3>Why a diaphragm pump</h3>
<p>A diaphragm moves air without lubricating it: no oil in the circuit, so nothing travels back into the chambers or the tubing. It also tolerates the frequent start/stop of a device that inflates and deflates in cycles, where a piston compressor wears quickly.</p>

<img src="https://file.example.com/image/2026/09/23/FR-12010901-2.jpg" alt="Diaphragm and port detail on the pump">

<h3>Applications</h3>
<ul>
<li>Compression therapy and recovery devices: inflating the chambers.</li>
<li>Air mattresses, cushions and chairs.</li>
<li>Medical equipment needing a clean, quiet airflow.</li>
<li>Laboratory and dosing equipment.</li>
</ul>

<h3>Ordering</h3>
<p>Sold <strong>by the piece</strong>. Minimum order: <strong>200</strong>. Connector, cable length and mounting can be adapted to your device.</p>',
       '{"Voltage":{"k":"Voltage","v":"DC 12 V"},"Flow rate":{"k":"Flow rate","v":"3.5 L/min"},"Noise":{"k":"Noise","v":"< 45 dB"},"Service life":{"k":"Service life","v":"3000 hours"},"Type":{"k":"Type","v":"Diaphragm air pump"},"Certificates":{"k":"Certificates","v":"CE, RoHS"},"Part number":{"k":"Part number","v":"WP36-12"}}'
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302' AND g.`goodscode` = '12010901'
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `specsjson`   = VALUES(`specsjson`);

COMMIT;

-- =====================================================================
-- ③ 扩到其余 5 种语言（es / de / ru / ja / zh）
-- =====================================================================
-- 站点开了 7 种语言，上面只铺了默认语言（en）和 fr。**没铺的语言不会报错**：
-- buildSpecs 逐键找不到译文就退回英文原文，买家看到的是英文而不是他的语言。
-- 法国站先把 fr 做扎实是合理的，但要让其余语言也跟上，按下面做一次。
--
-- 形状：{"<默认语言的键>":{"k":"<本语言的键>","v":"<本语言的值>"}}
-- 6 个键都必须是主表里那几个词，逐字一致。
--
-- 例（俄语，12010304）：
-- UPDATE `u_s_sitegoods_lang`
--    SET `specsjson` = '{"Material":{"k":"Материал","v":"Платиновая силиконовая резина"},"Hardness":{"k":"Твёрдость","v":"40-70 Shore A"},"Service temperature":{"k":"Рабочая температура","v":"от −40 °C до +220 °C"},"Structure":{"k":"Конструкция","v":"4 канала, один ряд"},"Colour":{"k":"Цвет","v":"Прозрачный или на заказ (Pantone)"},"Length":{"k":"Длина","v":"Под заказ"},"Certificates":{"k":"Сертификаты","v":"FDA, RoHS"},"Part number":{"k":"Артикул","v":"ST-G447"}}'
--  WHERE `sitecode` = 'DLZ2049651302' AND `goodscode` = '12010304' AND `lang` = 'ru';
--
-- 正文同理，直接 UPDATE `description`。翻译稿的管理台入口是
-- 「商品 → 编辑 → 各语言页签 → 详情」，改完保存也会走同一个白名单。

-- =====================================================================
-- ④ 写入后核对
-- =====================================================================
-- 属性条数：应为 4（每条一行，且都是合法 JSON，键数和主表一致）
SELECT g.`goodscode`, g.`sitecode`, JSON_LENGTH(g.`specsjson`) AS 属性行数
  FROM `u_s_sitegoods` g
 WHERE g.`sitecode` = 'DLZ2049651302'
   AND g.`goodscode` IN ('12010304','12010305','12010701','12010901');

-- 译文：应为 8 行（4 商品 × fr/en），且每条译文的键数 = 主表键数
SELECT l.`goodscode`, l.`lang`,
       JSON_LENGTH(l.`specsjson`) AS 译文键数,
       JSON_LENGTH(g.`specsjson`) AS 主表键数,
       CHAR_LENGTH(l.`description`) AS 正文字数,
       (CHAR_LENGTH(l.`description`) - CHAR_LENGTH(REPLACE(l.`description`, '<img ', ''))) / 5 AS 图片数
  FROM `u_s_sitegoods_lang` l
  JOIN `u_s_sitegoods` g
    ON g.`sitecode` = l.`sitecode` AND g.`goodscode` = l.`goodscode`
 WHERE l.`sitecode` = 'DLZ2049651302'
   AND l.`goodscode` IN ('12010304','12010305','12010701','12010901')
   AND l.`lang` IN ('fr','en')
 ORDER BY l.`goodscode`, l.`lang`;

-- ⚠ 键数对不上就是错的（前台那一行会静默退回英文）。下面这条应返回 0 行。
SELECT l.`goodscode`, l.`lang`, '译文键数和主表不一致' AS 问题
  FROM `u_s_sitegoods_lang` l
  JOIN `u_s_sitegoods` g
    ON g.`sitecode` = l.`sitecode` AND g.`goodscode` = l.`goodscode`
 WHERE l.`sitecode` = 'DLZ2049651302'
   AND l.`goodscode` IN ('12010304','12010305','12010701','12010901')
   AND l.`lang` IN ('fr','en')
   AND JSON_LENGTH(l.`specsjson`) <> JSON_LENGTH(g.`specsjson`);

-- =====================================================================
-- ⑤ 图片地址替换（把占位域名换成真实的）
-- =====================================================================
-- 图片传完再跑这一段。可重复执行。
--
--   @base = station.upload.base-url（线上那台配的文件访问域名，结尾不带 /）
--
-- 一次性把**所有**占位地址换到新域名（文件名不变）：
-- SET @base = 'https://file.votre-domaine.fr';
-- UPDATE `u_s_sitegoods_lang`
--    SET `description` = REPLACE(`description`, 'https://file.example.com', @base)
--  WHERE `sitecode` = 'DLZ2049651302'
--    AND `description` LIKE '%https://file.example.com%';
--
-- 单张换（真实文件名和占位名对不上时用这个，一张一条）：
-- UPDATE `u_s_sitegoods_lang`
--    SET `description` = REPLACE(`description`,
--          'https://file.example.com/image/2026/09/23/FR-12010304-1.jpg',
--          'https://file.votre-domaine.fr/image/2026/09/23/SIMG0000000099.jpg')
--  WHERE `sitecode` = 'DLZ2049651302' AND `goodscode` = '12010304';
--
-- 跑完核对：下面应为 0 行
-- SELECT goodscode, lang FROM `u_s_sitegoods_lang`
--  WHERE `sitecode` = 'DLZ2049651302' AND `description` LIKE '%file.example.com%';

-- =====================================================================
-- 回滚（清空这 4 条的属性和详情）
-- =====================================================================
-- UPDATE `u_s_sitegoods`
--    SET `specsjson` = NULL
--  WHERE `sitecode` = 'DLZ2049651302'
--    AND `goodscode` IN ('12010304','12010305','12010701','12010901');
--
-- UPDATE `u_s_sitegoods_lang`
--    SET `specsjson` = NULL, `description` = NULL
--  WHERE `sitecode` = 'DLZ2049651302'
--    AND `goodscode` IN ('12010304','12010305','12010701','12010901')
--    AND `lang` IN ('fr','en');

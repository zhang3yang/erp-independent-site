-- =====================================================================
-- 法国站 6 条商品的「卖点型图文详情」—— 图插进正文，属性表仍留空
-- =====================================================================
--
-- ⚠ 只跑在独立站那台（库 sdsstation）。可重复执行（INSERT ... ON DUPLICATE KEY UPDATE）。
--
-- 站点：DLZ2049739702（esvafit.vclinker.com）
-- ⚠ sitecode 用 SET @site 从库里取，**不硬编码**。
--   2026-09-23 我犯过一次：从本地库推了个 sitecode，线上根本没有，
--   迁移跑下去影响 0 行还不报错。跑之前下面会打出生效站点，核对一眼。
--
-- 商品：EB-SET-01 / EB-UNIT-01 / EB-TROUS-01 / EB-ARM-01 / EB-WAIST-01 / EB-BAG-01
-- 语言：fr, ru, es, ja, zh, en（= 站点 langs）六种全写
--
-- ---------------------------------------------------------------------
-- 图从哪来、为什么是这几张
-- ---------------------------------------------------------------------
--   全部是本站文件库里**已有的**图，地址形如
--     https://file.esvafit.vclinker.com/image/2026/09/21/SIMG00000000NN.png
--
--   ⚠ 刻意避开带**英文标注**的方案图（如 "8 Massage Modes"、"Standard accessories"、
--     "Exhaust hole design"）。它们挂在法语/日语页上会把英文大字糊在买家脸上，
--     而这几张是同一张图、没法按语言换。受影响的只有正文配图，
--     顶部图库（siteimgs）不受影响，那里本来就是这些图。
--     要放它们的话，得商家自己做多语言版本再重传。
--
--   ⚠ 图里没有一句承诺、没有一个参数——都是商品实拍或模特使用照，
--     所以对 6 种语言是同一张，不涉及"翻译图片内容"。
--
-- ---------------------------------------------------------------------
-- 只写卖点，不写参数
-- ---------------------------------------------------------------------
--   u_s_sitegoods.specsjson 保持 NULL（详情页那张属性表不渲染）。
--   参数（腔室数/压力档/材质/认证）只有商家能给，编一个等于给买家一个索赔把手。
--   正文里出现的"六腔、八种模式、360°、拉链、防水、排气孔、彩屏、手机 App"
--   全部引自 EB-BOOT-01 的既有属性——那是商家自己的口径，且它们共用同一台主机。
--
-- 回滚：文件末尾
-- =====================================================================

USE `sdsstation`;
SET NAMES utf8mb4;

SET @site = (SELECT sitecode FROM u_s_siteinfo LIMIT 1);
SET @shop = (SELECT shop_code FROM u_s_siteinfo LIMIT 1);
SET @img  = 'https://file.esvafit.vclinker.com/image/2026/09/21/';

SELECT CONCAT('生效站点: ', @site, '   商家: ', @shop) AS `前置检查①——应为 esvafit 那个站`;
SELECT CONCAT('目标商品 ', COUNT(*), ' / 6') AS `前置检查②——应为 6`
  FROM `u_s_sitegoods`
 WHERE `sitecode` = @site
   AND `goodscode` IN ('EB-SET-01','EB-UNIT-01','EB-TROUS-01','EB-ARM-01','EB-WAIST-01','EB-BAG-01');
SELECT CONCAT('文件库里这些图在不在: ', COUNT(*), ' / 17') AS `前置检查③——应为 17`
  FROM `u_s_siteimage`
 WHERE `imageid` IN ('SIMG0000000012','SIMG0000000013','SIMG0000000015','SIMG0000000016',
                     'SIMG0000000018','SIMG0000000019','SIMG0000000020','SIMG0000000021',
                     'SIMG0000000022','SIMG0000000023','SIMG0000000025','SIMG0000000026',
                     'SIMG0000000029','SIMG0000000030','SIMG0000000031','SIMG0000000032',
                     'SIMG0000000033');

START TRANSACTION;

-- =====================================================================
-- ① EB-SET-01 · 整套（主机 + 一副靴子）
--    配图：0023 整套 / 0020 App 控制
-- =====================================================================
INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-SET-01', 'fr', CONCAT('<h3>Points forts</h3>
<ul>
<li><strong>Le point de départ complet</strong> — l’unité de commande et une paire de bottes dans la même commande : rien à compléter avant la première séance.</li>
<li><strong>Six chambres par jambe</strong> — de la cuisse au pied, gonflées tour à tour de l’extrémité vers le cœur.</li>
<li><strong>Huit modes de massage</strong> — chaque mode a son ordre et son rythme ; la récupération après l’entraînement et les jambes lourdes du soir ne se traitent pas pareil.</li>
<li><strong>Enveloppement à 360°</strong> — la jambière fait le tour complet de la jambe, sans zone laissée de côté, et se met seule grâce à la fermeture à glissière latérale.</li>
<li><strong>Réglages sur l’appareil ou sur le téléphone</strong> — durée, pression, mode et chambre se choisissent sur l’écran couleur de l’unité ; l’application Esva reprend les mêmes réglages à distance.</li>
</ul>

<img src="', @img, 'SIMG0000000023.png" alt="Bottes de récupération Esva et unité de commande">

<h3>Dans quels contextes</h3>
<p>Récupération des jambes après l’entraînement et entre deux épreuves ; jambes lourdes après de longues heures debout, assis ou en avion ; usage partagé en centre de rééducation, cabinet de kinésithérapie, salle de sport et club.</p>

<img src="', @img, 'SIMG0000000020.png" alt="Réglage d’une séance depuis l’application">

<h3>Avant de commander</h3>
<p>Choisissez la taille des bottes ci-dessous — quatre tailles : S/M, L, XL et XXL. Mesurez le tour au point le plus large du mollet et la longueur du talon au haut de la cuisse, puis indiquez ces deux chiffres dans votre demande : nous confirmons la taille. Si vous possédez déjà une unité de commande, regardez plutôt la page des bottes seules.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-SET-01', 'en', CONCAT('<h3>Highlights</h3>
<ul>
<li><strong>The complete starting point</strong> — control unit and a pair of boots in one order: nothing left to add before the first session.</li>
<li><strong>Six chambers per leg</strong> — thigh to foot, inflated in turn from the extremity towards the heart.</li>
<li><strong>Eight massage modes</strong> — each mode has its own order and rhythm; recovery after training and heavy legs in the evening are not the same job.</li>
<li><strong>360° wrap</strong> — the leg sleeve goes all the way round with no area left out, and the side zip means you can put it on by yourself.</li>
<li><strong>Set on the device or on your phone</strong> — time, pressure, mode and chamber are chosen on the unit’s colour screen; the Esva app carries the same settings from a distance.</li>
</ul>

<img src="', @img, 'SIMG0000000023.png" alt="Esva recovery boots and control unit">

<h3>Where it fits</h3>
<p>Leg recovery after training and between events; heavy legs after long hours standing, sitting or flying; shared equipment in rehabilitation centres, physiotherapy practices, gyms and clubs.</p>

<img src="', @img, 'SIMG0000000020.png" alt="Setting up a session from the app">

<h3>Before you order</h3>
<p>Pick the boot size below — four sizes: S/M, L, XL and XXL. Measure the circumference at the widest point of the calf and the length from heel to top of thigh, then send us both numbers with your request and we confirm the size. If you already own a control unit, see the boots-only page instead.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-SET-01', 'ru', CONCAT('<h3>Главное</h3>
<ul>
<li><strong>Готовый комплект</strong> — блок управления и пара сапог одним заказом: докупать перед первым сеансом нечего.</li>
<li><strong>Шесть камер на каждую ногу</strong> — от бедра до стопы, нагнетаются по очереди от периферии к сердцу.</li>
<li><strong>Восемь режимов массажа</strong> — у каждого свой порядок и ритм: восстановление после тренировки и тяжёлые ноги вечером — разные задачи.</li>
<li><strong>Охват 360°</strong> — манжета обхватывает ногу полностью, без пропущенных зон; боковая молния позволяет надеть её самостоятельно.</li>
<li><strong>Настройка на блоке или в телефоне</strong> — время, давление, режим и камера задаются на цветном экране блока; приложение Esva повторяет те же настройки дистанционно.</li>
</ul>

<img src="', @img, 'SIMG0000000023.png" alt="Сапоги для восстановления Esva и блок управления">

<h3>Где применять</h3>
<p>Восстановление ног после тренировок и между стартами; тяжесть в ногах после долгих часов стоя, сидя или в полёте; совместное использование в центрах реабилитации, кабинетах физиотерапии, фитнес-клубах и спортивных командах.</p>

<img src="', @img, 'SIMG0000000020.png" alt="Настройка сеанса через приложение">

<h3>Перед заказом</h3>
<p>Выберите размер сапог ниже — четыре размера: S/M, L, XL и XXL. Измерьте обхват в самой широкой части голени и длину от пятки до верха бедра, укажите оба числа в запросе — мы подтвердим размер. Если блок управления у вас уже есть, смотрите страницу сапог отдельно.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-SET-01', 'es', CONCAT('<h3>Puntos fuertes</h3>
<ul>
<li><strong>El punto de partida completo</strong> — la unidad de control y un par de botas en el mismo pedido: no queda nada por añadir antes de la primera sesión.</li>
<li><strong>Seis cámaras por pierna</strong> — del muslo al pie, infladas por turnos desde la extremidad hacia el corazón.</li>
<li><strong>Ocho modos de masaje</strong> — cada modo tiene su orden y su ritmo; la recuperación tras entrenar y las piernas cansadas de la noche no se tratan igual.</li>
<li><strong>Envoltura 360°</strong> — la bota rodea la pierna por completo, sin zonas sin cubrir, y se pone uno solo gracias a la cremallera lateral.</li>
<li><strong>Ajustes en el equipo o en el teléfono</strong> — tiempo, presión, modo y cámara se eligen en la pantalla a color de la unidad; la aplicación Esva repite los mismos ajustes a distancia.</li>
</ul>

<img src="', @img, 'SIMG0000000023.png" alt="Botas de recuperación Esva y unidad de control">

<h3>En qué contextos</h3>
<p>Recuperación de piernas después de entrenar y entre pruebas; piernas cansadas tras muchas horas de pie, sentado o en avión; uso compartido en centros de rehabilitación, consultas de fisioterapia, gimnasios y clubes.</p>

<img src="', @img, 'SIMG0000000020.png" alt="Ajuste de una sesión desde la aplicación">

<h3>Antes de pedir</h3>
<p>Elija la talla de las botas abajo — cuatro tallas: S/M, L, XL y XXL. Mida el contorno en el punto más ancho de la pantorrilla y la longitud del talón a la parte alta del muslo, e indíquenos ambas cifras en la solicitud: confirmamos la talla. Si ya tiene una unidad de control, vea la página de las botas sueltas.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-SET-01', 'ja', CONCAT('<h3>特長</h3>
<ul>
<li><strong>これ一套で始められます</strong> — コントロールユニットとブーツ 1 組を同じご注文で。初回の施術前に買い足すものはありません。</li>
<li><strong>片脚 6 気室</strong> — 太ももから足先まで、末梢から心臓に向けて順に加圧します。</li>
<li><strong>8 種類のマッサージモード</strong> — モードごとに加圧の順序とリズムが異なります。トレーニング後の回復と、夜の脚の重さは同じ対処ではありません。</li>
<li><strong>360° 全周ラップ</strong> — カフが脚を一周し、未装着部分ができません。サイドファスナーで一人でも装着できます。</li>
<li><strong>本体でもスマートフォンでも設定</strong> — 時間・圧力・モード・気室は本体のカラー画面で設定でき、Esva アプリからも同じ設定を遠隔で操作できます。</li>
</ul>

<img src="', @img, 'SIMG0000000023.png" alt="Esva リカバリーブーツとコントロールユニット">

<h3>どんな場面で</h3>
<p>トレーニング後や試合間の脚の回復。長時間の立ち仕事・座り仕事・フライト後の脚の重さ。リハビリ施設・整骨院・フィットネスクラブ・スポーツチームでの共用。</p>

<img src="', @img, 'SIMG0000000020.png" alt="アプリからのセッション設定">

<h3>ご注文前に</h3>
<p>下からブーツのサイズをお選びください — S/M・L・XL・XXL の 4 サイズです。ふくらはぎの最も太い部分の周径と、かかとから太もも上部までの長さを測り、ご依頼時にお知らせください。サイズを確認のうえご連絡します。すでにコントロールユニットをお持ちの場合は、ブーツ単体のページをご覧ください。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-SET-01', 'zh', CONCAT('<h3>产品亮点</h3>
<ul>
<li><strong>一步到位</strong> —— 主机和一副靴子一次下单，第一次用之前不需要再添东西。</li>
<li><strong>每条腿 6 个气室</strong> —— 从大腿到脚，由远心端向近心端依次充气。</li>
<li><strong>8 种按摩模式</strong> —— 每种模式的充气顺序和节奏都不同：训练后恢复和晚上的腿沉，处理方式本来就不一样。</li>
<li><strong>360° 全包裹</strong> —— 靴套绕腿一整圈，不留空档；侧边拉链，一个人也穿得上。</li>
<li><strong>主机上设置，也能用手机</strong> —— 时间、压力、模式、气室都在主机彩屏上调；Esva App 可以远程做同样的设置。</li>
</ul>

<img src="', @img, 'SIMG0000000023.png" alt="Esva 恢复靴与主机">

<h3>适用场景</h3>
<p>训练后和比赛之间的腿部恢复；久站、久坐、长途飞行后的腿沉；康复中心、理疗诊所、健身房和运动队的共用设备。</p>

<img src="', @img, 'SIMG0000000020.png" alt="用 App 设置一次护理">

<h3>下单前</h3>
<p>在下面选靴子的码 —— 共 4 个码：S/M、L、XL、XXL。量一下小腿最粗处的周长、以及从脚跟到大腿根的长度，连同询价一起告诉我们，我们会确认码数。如果你已经有主机，靴子可以单独配，那种情况请看靴子的单独页面。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- =====================================================================
-- ② EB-UNIT-01 · 主机
--    配图：0030 正面彩屏 / 0031 白天模式界面
-- =====================================================================
INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-UNIT-01', 'fr', CONCAT('<h3>Points forts</h3>
<ul>
<li><strong>Une unité pilote tous les manchons Esva</strong> — bottes, pantalon, manchons de bras et ceinture fonctionnent tous à partir de la même unité : le même boîtier, les mêmes réglages, le même écran.</li>
<li><strong>Un programme par manchon, l’un après l’autre</strong> — l’unité alimente un manchon à la fois. Une séance de jambes prend le temps d’une séance de jambes : décidez de votre ordre avant de commencer.</li>
<li><strong>Écran couleur, réglages sur l’appareil</strong> — durée, pression, mode et chambre se choisissent directement sur l’unité, sans téléphone.</li>
<li><strong>Application Esva</strong> — quand le téléphone est plus pratique, l’application reprend les mêmes réglages.</li>
<li><strong>Se vend seule, volontairement</strong> — parce que le besoin le plus courant n’est pas de remplacer une unité, c’est d’en avoir une seconde pour le second manchon.</li>
</ul>

<img src="', @img, 'SIMG0000000030.png" alt="Unité de commande Esva, écran couleur">

<h3>Dans quels contextes</h3>
<p>Seconde unité pour une deuxième zone — une séance de jambes et une séance de bras qui n’ont pas à attendre l’une après l’autre ; seconde unité pour un second utilisateur, sans partager les réglages ; remplacement d’une unité existante ; utilisation partagée en club, en cabinet ou en centre.</p>

<img src="', @img, 'SIMG0000000031.png" alt="Réglage du mode et de la pression sur l’unité">

<h3>Bien choisir</h3>
<p>Cette page vend <strong>l’unité seule</strong> : aucun manchon n’est compris. Si vous démarrez sans rien posséder, prenez plutôt le pack complet (unité + bottes). Et si vous ne savez pas quelle zone traiter en premier, commencez par les jambes : c’est là que le volume de tissu est le plus important.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-UNIT-01', 'en', CONCAT('<h3>Highlights</h3>
<ul>
<li><strong>One unit drives every Esva garment</strong> — boots, trousers, arm sleeves and waist belt all run from the same unit: same box, same settings, same screen.</li>
<li><strong>One garment at a time</strong> — the unit drives a single garment per session. A leg session takes as long as a leg session: settle the order before you start.</li>
<li><strong>Colour screen, controls on the device</strong> — time, pressure, mode and chamber are chosen on the unit itself, with no phone.</li>
<li><strong>Esva app</strong> — when the phone is handier, the app carries the same settings.</li>
<li><strong>Sold on its own, deliberately</strong> — the most common reason is not replacing a unit but owning a second one for the second garment.</li>
</ul>

<img src="', @img, 'SIMG0000000030.png" alt="Esva control unit with colour screen">

<h3>Where it fits</h3>
<p>A second unit for a second area — a leg session and an arm session that should not queue behind each other; a second unit for a second user so nobody shares settings; replacing an existing unit; shared use in a club, practice or centre.</p>

<img src="', @img, 'SIMG0000000031.png" alt="Setting mode and pressure on the unit">

<h3>Choosing well</h3>
<p>This page sells <strong>the unit alone</strong>: no garment is included. If you are starting from nothing, take the complete set instead (unit + boots). And if you are unsure which area to treat first, start with the legs — that is where the tissue volume is greatest.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-UNIT-01', 'ru', CONCAT('<h3>Главное</h3>
<ul>
<li><strong>Один блок работает со всеми манжетами Esva</strong> — сапоги, брюки, манжеты для рук и пояс работают от одного блока: тот же корпус, те же настройки, тот же экран.</li>
<li><strong>Одна манжета за раз</strong> — блок работает с одной манжетой за сеанс. Сеанс на ноги занимает столько, сколько занимает: определите порядок заранее.</li>
<li><strong>Цветной экран, управление на самом блоке</strong> — время, давление, режим и камера задаются на блоке, телефон не нужен.</li>
<li><strong>Приложение Esva</strong> — когда телефон удобнее, приложение повторяет те же настройки.</li>
<li><strong>Продаётся отдельно — и это осознанно</strong> — чаще всего блок нужен не взамен, а как второй, для второй манжеты.</li>
</ul>

<img src="', @img, 'SIMG0000000030.png" alt="Блок управления Esva с цветным экраном">

<h3>Где применять</h3>
<p>Второй блок для второй зоны — чтобы сеанс на ноги и сеанс на руки не ждали друг друга; второй блок для второго пользователя, чтобы не делить настройки; замена вышедшего из строя; совместное использование в клубе, кабинете или центре.</p>

<img src="', @img, 'SIMG0000000031.png" alt="Настройка режима и давления на блоке">

<h3>Как выбрать</h3>
<p>На этой странице продаётся <strong>только блок</strong>: манжеты в комплект не входят. Если вы начинаете с нуля, возьмите полный комплект (блок + сапоги). Если не знаете, с какой зоны начать, начинайте с ног: там объём тканей наибольший.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-UNIT-01', 'es', CONCAT('<h3>Puntos fuertes</h3>
<ul>
<li><strong>Una unidad mueve todas las prendas Esva</strong> — botas, pantalón, manguitos y cinturón funcionan con la misma unidad: la misma caja, los mismos ajustes, la misma pantalla.</li>
<li><strong>Una prenda cada vez</strong> — la unidad mueve una prenda por sesión. Una sesión de piernas dura lo que dura: decida el orden antes de empezar.</li>
<li><strong>Pantalla a color, mandos en el equipo</strong> — tiempo, presión, modo y cámara se eligen en la propia unidad, sin teléfono.</li>
<li><strong>Aplicación Esva</strong> — cuando el teléfono resulta más cómodo, la aplicación repite los mismos ajustes.</li>
<li><strong>Se vende sola, a propósito</strong> — el motivo más habitual no es sustituir una unidad, sino tener una segunda para la segunda prenda.</li>
</ul>

<img src="', @img, 'SIMG0000000030.png" alt="Unidad de control Esva con pantalla a color">

<h3>En qué contextos</h3>
<p>Una segunda unidad para una segunda zona — que una sesión de piernas y una de brazos no tengan que esperar una a la otra; una segunda unidad para un segundo usuario, sin compartir ajustes; sustituir una unidad existente; uso compartido en club, consulta o centro.</p>

<img src="', @img, 'SIMG0000000031.png" alt="Ajuste de modo y presión en la unidad">

<h3>Elegir bien</h3>
<p>Esta página vende <strong>solo la unidad</strong>: no incluye ninguna prenda. Si empieza de cero, coja el pack completo (unidad + botas). Y si no sabe qué zona tratar primero, empiece por las piernas: es donde el volumen de tejido es mayor.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-UNIT-01', 'ja', CONCAT('<h3>特長</h3>
<ul>
<li><strong>1 台ですべての Esva カフを動かせます</strong> — ブーツ・パンツ・アームカフ・ウエストベルトはすべて同じユニットで動きます。同じ本体、同じ設定、同じ画面です。</li>
<li><strong>同時に動くのは 1 枚</strong> — 1 回の施術で動かせるのは 1 枚です。脚の施術には脚の施術分の時間がかかります。順番は始める前に決めておいてください。</li>
<li><strong>カラー画面、本体で操作</strong> — 時間・圧力・モード・気室は本体で設定でき、スマートフォンは不要です。</li>
<li><strong>Esva アプリ</strong> — スマートフォンのほうが便利なときは、アプリから同じ設定ができます。</li>
<li><strong>あえて単体で販売しています</strong> — 多いのは買い替えではなく、2 枚目のカフのための 2 台目です。</li>
</ul>

<img src="', @img, 'SIMG0000000030.png" alt="Esva コントロールユニット（カラー画面）">

<h3>どんな場面で</h3>
<p>2 つ目の部位のための 2 台目 — 脚の施術と腕の施術を順番待ちにしないため。2 人目用の 2 台目 — 設定を共有しないため。既存ユニットの買い替え。クラブ・施術院・施設での共用。</p>

<img src="', @img, 'SIMG0000000031.png" alt="本体でのモードと圧力の設定">

<h3>選び方</h3>
<p>このページは<strong>ユニット本体のみ</strong>の販売です。カフは付属しません。ゼロから始める方は、セット（ユニット＋ブーツ）をお選びください。どの部位から始めるか迷ったら、脚から始めてください。組織量が最も多い部位です。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-UNIT-01', 'zh', CONCAT('<h3>产品亮点</h3>
<ul>
<li><strong>一台主机带动所有 Esva 加压衣</strong> —— 靴子、裤子、臂套、腰带都用同一台主机：同一个机身、同一套设置、同一块屏。</li>
<li><strong>一次只带一件</strong> —— 一次护理主机只驱动一件加压衣。腿部护理就是要花腿部护理的时间，顺序请提前想好。</li>
<li><strong>彩屏，在主机上操作</strong> —— 时间、压力、模式、气室都在主机上调，不用手机。</li>
<li><strong>Esva App</strong> —— 手机更方便时，App 能做同样的设置。</li>
<li><strong>特意做成单独出售</strong> —— 最常见的需求不是换主机，而是为第二件加压衣再配一台。</li>
</ul>

<img src="', @img, 'SIMG0000000030.png" alt="Esva 主机彩屏">

<h3>适用场景</h3>
<p>为第二个部位配第二台 —— 让腿部护理和手臂护理不用互相排队；为第二个人配第二台，设置不用共用；替换旧主机；俱乐部、诊所、机构的共用设备。</p>

<img src="', @img, 'SIMG0000000031.png" alt="在主机上设置模式和压力">

<h3>怎么选</h3>
<p>这一页卖的是<strong>主机单件</strong>，不含任何加压衣。如果是从零开始，请选整套（主机 + 靴子）。如果拿不准先做哪个部位，从腿开始 —— 那里的组织量最大。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- =====================================================================
-- ③ EB-TROUS-01 · 恢复裤
--    配图：0033 裤子平铺 / 0022 模特穿着
-- =====================================================================
INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-TROUS-01', 'fr', CONCAT('<h3>Points forts</h3>
<ul>
<li><strong>Jambes et hanches en une seule pièce</strong> — là où les bottes s’arrêtent au genou, le pantalon remonte jusqu’à la taille : cuisses et hanches sont traitées par la même séance.</li>
<li><strong>Six chambres par jambe</strong> — le même découpage que les bottes, du bas de la jambe vers le haut.</li>
<li><strong>Enveloppement à 360°</strong> — le tissu fait le tour complet, sans zone laissée de côté.</li>
<li><strong>Même unité que les bottes</strong> — aucune commande supplémentaire à apprendre : si vous avez déjà une unité Esva, le pantalon s’y branche.</li>
</ul>

<img src="', @img, 'SIMG0000000033.png" alt="Pantalon de récupération Esva">

<h3>Bottes ou pantalon ?</h3>
<p>Les bottes couvrent le bas de la jambe — du pied au genou. Le pantalon couvre le haut — cuisses et hanches. Ce ne sont pas deux façons de faire la même chose : ce sont <strong>deux moitiés</strong> de la jambe. Pour une récupération complète, les deux se complètent ; si vous ne devez en prendre qu’un, choisissez selon l’endroit qui vous gêne le plus.</p>

<img src="', @img, 'SIMG0000000022.png" alt="Pantalon de récupération porté">

<h3>Avant de commander</h3>
<p>Choisissez une taille ci-dessous et indiquez votre mesure (tour de taille et tour de cuisse) dans votre demande : nous confirmons la taille. Le pantalon fonctionne avec une unité de commande Esva, vendue séparément ou comprise dans le pack complet.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-TROUS-01', 'en', CONCAT('<h3>Highlights</h3>
<ul>
<li><strong>Legs and hips in one garment</strong> — where the boots stop at the knee, the trousers carry on up to the waist: thighs and hips are covered in the same session.</li>
<li><strong>Six chambers per leg</strong> — the same division as the boots, running from the lower leg upwards.</li>
<li><strong>360° wrap</strong> — the fabric goes all the way round with no area left out.</li>
<li><strong>Same unit as the boots</strong> — nothing new to learn: if you already own an Esva unit, the trousers plug into it.</li>
</ul>

<img src="', @img, 'SIMG0000000033.png" alt="Esva recovery trousers">

<h3>Boots or trousers?</h3>
<p>The boots cover the lower leg — foot to knee. The trousers cover the upper leg — thighs and hips. They are not two ways of doing the same thing: they are <strong>two halves</strong> of the leg. For full recovery they complement each other; if you are only buying one, choose by where it bothers you most.</p>

<img src="', @img, 'SIMG0000000022.png" alt="Recovery trousers being worn">

<h3>Before you order</h3>
<p>Pick a size below and send us your measurement (waist and thigh circumference) with your request and we confirm the size. The trousers run from an Esva control unit, sold separately or included in the complete set.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-TROUS-01', 'ru', CONCAT('<h3>Главное</h3>
<ul>
<li><strong>Ноги и бёдра одной манжетой</strong> — там, где сапоги заканчиваются на колене, брюки идут до пояса: бёдра обрабатываются в том же сеансе.</li>
<li><strong>Шесть камер на каждую ногу</strong> — то же деление, что у сапог, снизу вверх.</li>
<li><strong>Охват 360°</strong> — ткань обхватывает ногу полностью, без пропущенных зон.</li>
<li><strong>Тот же блок, что и у сапог</strong> — учиться ничему не нужно: если блок Esva уже есть, брюки подключаются к нему.</li>
</ul>

<img src="', @img, 'SIMG0000000033.png" alt="Брюки для восстановления Esva">

<h3>Сапоги или брюки?</h3>
<p>Сапоги закрывают низ ноги — от стопы до колена. Брюки закрывают верх — бёдра и таз. Это не два способа делать одно и то же, это <strong>две половины</strong> ноги. Для полного восстановления они дополняют друг друга; если берёте только одно, выбирайте по тому, где беспокоит сильнее.</p>

<img src="', @img, 'SIMG0000000022.png" alt="Брюки для восстановления на модели">

<h3>Перед заказом</h3>
<p>Выберите размер ниже и укажите свою мерку (обхват талии и обхват бедра) в запросе — мы подтвердим размер. Брюки работают от блока управления Esva: отдельно или в полном комплекте.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-TROUS-01', 'es', CONCAT('<h3>Puntos fuertes</h3>
<ul>
<li><strong>Piernas y caderas en una sola prenda</strong> — donde las botas terminan en la rodilla, el pantalón sigue hasta la cintura: los muslos y las caderas se tratan en la misma sesión.</li>
<li><strong>Seis cámaras por pierna</strong> — la misma división que las botas, de abajo hacia arriba.</li>
<li><strong>Envoltura 360°</strong> — el tejido rodea la pierna por completo, sin zonas sin cubrir.</li>
<li><strong>La misma unidad que las botas</strong> — nada nuevo que aprender: si ya tiene una unidad Esva, el pantalón se conecta a ella.</li>
</ul>

<img src="', @img, 'SIMG0000000033.png" alt="Pantalón de recuperación Esva">

<h3>¿Botas o pantalón?</h3>
<p>Las botas cubren la parte baja de la pierna — del pie a la rodilla. El pantalón cubre la parte alta — muslos y caderas. No son dos formas de hacer lo mismo: son <strong>dos mitades</strong> de la pierna. Para una recuperación completa se complementan; si solo va a comprar uno, elija según dónde le moleste más.</p>

<img src="', @img, 'SIMG0000000022.png" alt="Pantalón de recuperación puesto">

<h3>Antes de pedir</h3>
<p>Elija una talla abajo e indíquenos su medida (contorno de cintura y de muslo) en la solicitud: confirmamos la talla. El pantalón funciona con una unidad de control Esva, vendida aparte o incluida en el pack completo.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-TROUS-01', 'ja', CONCAT('<h3>特長</h3>
<ul>
<li><strong>脚と腰を 1 着で</strong> — ブーツが膝で終わるのに対し、パンツはウエストまで届きます。太ももと腰を同じ施術でカバーします。</li>
<li><strong>片脚 6 気室</strong> — ブーツと同じ分割で、下から上へ加圧します。</li>
<li><strong>360° 全周ラップ</strong> — 生地が脚を一周し、未装着部分ができません。</li>
<li><strong>ブーツと同じユニット</strong> — 新しく覚えることはありません。Esva ユニットをお持ちなら、そのまま接続できます。</li>
</ul>

<img src="', @img, 'SIMG0000000033.png" alt="Esva リカバリーパンツ">

<h3>ブーツかパンツか</h3>
<p>ブーツは脚の下部（足先から膝まで）を覆います。パンツは上部（太ももと腰）を覆います。同じことをする 2 つの方法ではなく、脚の<strong>上下 2 つの半分</strong>です。全身の回復には両方が補い合います。どちらか一方だけなら、気になる場所で選んでください。</p>

<img src="', @img, 'SIMG0000000022.png" alt="リカバリーパンツの着用">

<h3>ご注文前に</h3>
<p>下からサイズをお選びいただき、ご依頼時に採寸値（ウエスト周径・太もも周径）をお知らせください。サイズを確認のうえご連絡します。Esva コントロールユニットで動作します（単体またはフルセットに含まれます）。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-TROUS-01', 'zh', CONCAT('<h3>产品亮点</h3>
<ul>
<li><strong>腿和臀一件覆盖</strong> —— 靴子到膝盖为止，裤子一直到腰：大腿和臀部在同一次护理里做。</li>
<li><strong>每条腿 6 个气室</strong> —— 和靴子一样的分区，由下往上依次充气。</li>
<li><strong>360° 全包裹</strong> —— 面料绕腿一整圈，不留空档。</li>
<li><strong>和靴子共用同一台主机</strong> —— 不用学新东西：已经有 Esva 主机，裤子直接接上去。</li>
</ul>

<img src="', @img, 'SIMG0000000033.png" alt="Esva 加压裤">

<h3>买靴子还是买裤子？</h3>
<p>靴子管小腿 —— 从脚到膝盖。裤子管大腿和臀部。这不是两种做同一件事的方式，而是腿的<strong>上下两半</strong>。要做完整恢复，两者互补；如果只买一件，按你更不舒服的部位来选。</p>

<img src="', @img, 'SIMG0000000022.png" alt="模特穿着加压裤">

<h3>下单前</h3>
<p>在下面选码，并在询价时告诉我们量出来的数（腰围、大腿围），我们会确认码数。裤子由 Esva 主机驱动，主机单独出售，或者包含在整套里。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- =====================================================================
-- ④ EB-ARM-01 · 臂套
--    配图：0027 右臂实拍 / 0021 半身视角
-- =====================================================================
INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-ARM-01', 'fr', CONCAT('<h3>Points forts</h3>
<ul>
<li><strong>Six chambres le long du bras</strong> — le même découpage que les bottes, du poignet vers l’épaule.</li>
<li><strong>Deux longueurs</strong> — selon que vous voulez couvrir l’avant-bras seul ou tout le bras. La longueur se choisit ci-dessous.</li>
<li><strong>Vendu par paire</strong> — les deux bras, pas un seul.</li>
<li><strong>Enveloppement à 360°</strong> — le manchon fait le tour complet du bras, sans laisser de zone de côté.</li>
<li><strong>Même unité que les bottes</strong> — rien de nouveau à apprendre : si vous avez déjà une unité Esva, le manchon s’y branche.</li>
</ul>

<img src="', @img, 'SIMG0000000027.png" alt="Manchon de bras Esva">

<h3>Dans quels contextes</h3>
<p>Après une séance de musculation ou d’escalade ; avant-bras tendus par le travail répétitif ; bras lourds après une longue journée ; en complément d’une séance de jambes, avec une seconde unité pour ne pas faire la queue.</p>

<img src="', @img, 'SIMG0000000021.png" alt="Manchon de bras porté">

<h3>Bien choisir</h3>
<p>Prenez la longueur qui correspond à la zone qui vous gêne : l’avant-bras pour le travail répétitif et le poignet, le bras entier pour une séance complète. Le manchon fonctionne avec une unité de commande Esva, vendue séparément ou comprise dans le pack complet.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-ARM-01', 'en', CONCAT('<h3>Highlights</h3>
<ul>
<li><strong>Six chambers along the arm</strong> — the same division as the boots, from wrist towards shoulder.</li>
<li><strong>Two lengths</strong> — depending on whether you want to cover the forearm alone or the whole arm. Pick the length below.</li>
<li><strong>Sold per pair</strong> — both arms, not one.</li>
<li><strong>360° wrap</strong> — the sleeve goes all the way round with no area left out.</li>
<li><strong>Same unit as the boots</strong> — nothing new to learn: if you already own an Esva unit, the sleeve plugs into it.</li>
</ul>

<img src="', @img, 'SIMG0000000027.png" alt="Esva arm sleeve">

<h3>Where it fits</h3>
<p>After a weights or climbing session; forearms tight from repetitive work; heavy arms after a long day; alongside a leg session, with a second unit so the two do not queue.</p>

<img src="', @img, 'SIMG0000000021.png" alt="Arm sleeve being worn">

<h3>Choosing well</h3>
<p>Take the length that matches the area bothering you: the forearm for repetitive work and the wrist, the whole arm for a full session. The sleeve runs from an Esva control unit, sold separately or included in the complete set.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-ARM-01', 'ru', CONCAT('<h3>Главное</h3>
<ul>
<li><strong>Шесть камер вдоль руки</strong> — то же деление, что у сапог, от запястья к плечу.</li>
<li><strong>Две длины</strong> — смотря что нужно закрыть: только предплечье или всю руку. Длина выбирается ниже.</li>
<li><strong>Продаётся парой</strong> — обе руки, не одна.</li>
<li><strong>Охват 360°</strong> — манжета обхватывает руку полностью, без пропущенных зон.</li>
<li><strong>Тот же блок, что и у сапог</strong> — учиться ничему не нужно: если блок Esva уже есть, манжета подключается к нему.</li>
</ul>

<img src="', @img, 'SIMG0000000027.png" alt="Манжета для рук Esva">

<h3>Где применять</h3>
<p>После силовой тренировки или скалолазания; предплечья после монотонной работы; тяжёлые руки после долгого дня; вместе с сеансом на ноги — со вторым блоком, чтобы не ждать очереди.</p>

<img src="', @img, 'SIMG0000000021.png" alt="Манжета для рук на модели">

<h3>Как выбрать</h3>
<p>Берите ту длину, которая совпадает с беспокоящей зоной: предплечье — при монотонной работе и для запястья, вся рука — для полного сеанса. Манжета работает от блока управления Esva: отдельно или в полном комплекте.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-ARM-01', 'es', CONCAT('<h3>Puntos fuertes</h3>
<ul>
<li><strong>Seis cámaras a lo largo del brazo</strong> — la misma división que las botas, de la muñeca hacia el hombro.</li>
<li><strong>Dos largos</strong> — según quiera cubrir solo el antebrazo o todo el brazo. El largo se elige abajo.</li>
<li><strong>Se vende por par</strong> — los dos brazos, no uno.</li>
<li><strong>Envoltura 360°</strong> — el manguito rodea el brazo por completo, sin dejar zonas sin cubrir.</li>
<li><strong>La misma unidad que las botas</strong> — nada nuevo que aprender: si ya tiene una unidad Esva, el manguito se conecta a ella.</li>
</ul>

<img src="', @img, 'SIMG0000000027.png" alt="Manguito de brazo Esva">

<h3>En qué contextos</h3>
<p>Después de una sesión de pesas o de escalada; antebrazos cargados por trabajo repetitivo; brazos pesados tras un día largo; junto a una sesión de piernas, con una segunda unidad para no hacer cola.</p>

<img src="', @img, 'SIMG0000000021.png" alt="Manguito de brazo puesto">

<h3>Elegir bien</h3>
<p>Coja el largo que corresponda a la zona que le molesta: el antebrazo para el trabajo repetitivo y la muñeca, el brazo entero para una sesión completa. El manguito funciona con una unidad de control Esva, vendida aparte o incluida en el pack completo.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-ARM-01', 'ja', CONCAT('<h3>特長</h3>
<ul>
<li><strong>腕に沿って 6 気室</strong> — ブーツと同じ分割で、手首から肩に向けて加圧します。</li>
<li><strong>2 つの長さ</strong> — 前腕だけか、腕全体か。長さは下からお選びください。</li>
<li><strong>1 組単位でのご提供</strong> — 両腕分です。片腕ではありません。</li>
<li><strong>360° 全周ラップ</strong> — カフが腕を一周し、未装着部分ができません。</li>
<li><strong>ブーツと同じユニット</strong> — 新しく覚えることはありません。Esva ユニットをお持ちなら、そのまま接続できます。</li>
</ul>

<img src="', @img, 'SIMG0000000027.png" alt="Esva アームカフ">

<h3>どんな場面で</h3>
<p>ウエイトトレーニングやクライミングの後。繰り返し作業で張った前腕。長い一日の後の腕のだるさ。脚の施術と並行したいとき — 2 台目があれば順番待ちになりません。</p>

<img src="', @img, 'SIMG0000000021.png" alt="アームカフの着用">

<h3>選び方</h3>
<p>気になる部位に合う長さをお選びください。繰り返し作業や手首なら前腕、全身の施術なら腕全体です。Esva コントロールユニットで動作します（単体またはフルセットに含まれます）。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-ARM-01', 'zh', CONCAT('<h3>产品亮点</h3>
<ul>
<li><strong>沿手臂 6 个气室</strong> —— 和靴子一样的分区，从手腕往肩膀方向依次充气。</li>
<li><strong>两种长度</strong> —— 看你是想只做前臂，还是整条手臂。长度在下面选。</li>
<li><strong>按副出售</strong> —— 是两条手臂，不是一条。</li>
<li><strong>360° 全包裹</strong> —— 臂套绕手臂一整圈，不留空档。</li>
<li><strong>和靴子共用同一台主机</strong> —— 不用学新东西：已经有 Esva 主机，臂套直接接上去。</li>
</ul>

<img src="', @img, 'SIMG0000000027.png" alt="Esva 臂套">

<h3>适用场景</h3>
<p>力量训练或攀岩之后；重复性劳动导致前臂发紧；忙了一整天后手臂发沉；和腿部护理一起做 —— 配第二台主机就不用排队。</p>

<img src="', @img, 'SIMG0000000021.png" alt="模特佩戴臂套">

<h3>怎么选</h3>
<p>选和你难受的部位对应的长度：重复性劳动和手腕选前臂，做整套护理选整条手臂。臂套由 Esva 主机驱动，主机单独出售，或者包含在整套里。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- =====================================================================
-- ⑤ EB-WAIST-01 · 加压腰带
--    配图：0029 腰腹实拍 / 0028 另一角度
-- =====================================================================
INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-WAIST-01', 'fr', CONCAT('<h3>Points forts</h3>
<ul>
<li><strong>Concentré sur la taille et le bas du dos</strong> — une zone que ni les bottes ni le pantalon ne traitent vraiment.</li>
<li><strong>Chambres réparties sur la sangle</strong> — le gonflage travaille le tour de taille et les lombaires, pas seulement le devant.</li>
<li><strong>À porter sans rester allongé</strong> — la ceinture tient en place pendant que vous bougez, ce que les manchons de jambe ne permettent pas.</li>
<li><strong>Même unité que les bottes</strong> — si vous avez déjà une unité Esva, la ceinture s’y branche.</li>
</ul>

<img src="', @img, 'SIMG0000000029.png" alt="Ceinture de compression Esva">

<h3>Dans quels contextes</h3>
<p>Bas du dos et taille après l’entraînement ; longue station assise ou debout ; en complément d’un pantalon de récupération — la ceinture vise le tour de taille, le pantalon vise les cuisses et les hanches.</p>

<img src="', @img, 'SIMG0000000028.png" alt="Ceinture portée sur la taille">

<h3>Avant de commander</h3>
<p>Indiquez votre tour de taille dans votre demande : la ceinture se règle, mais nous préférons confirmer avec vous. Elle fonctionne avec une unité de commande Esva, vendue séparément ou comprise dans le pack complet.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-WAIST-01', 'en', CONCAT('<h3>Highlights</h3>
<ul>
<li><strong>Focused on the waist and lower back</strong> — an area neither the boots nor the trousers really treat.</li>
<li><strong>Chambers spread around the belt</strong> — inflation works the whole waist and the lumbar area, not just the front.</li>
<li><strong>Worn without lying down</strong> — the belt stays put while you move, which leg sleeves do not allow.</li>
<li><strong>Same unit as the boots</strong> — if you already own an Esva unit, the belt plugs into it.</li>
</ul>

<img src="', @img, 'SIMG0000000029.png" alt="Esva compression waist belt">

<h3>Where it fits</h3>
<p>Lower back and waist after training; long hours sitting or standing; alongside recovery trousers — the belt covers the waist, the trousers cover thighs and hips.</p>

<img src="', @img, 'SIMG0000000028.png" alt="Waist belt being worn">

<h3>Before you order</h3>
<p>Send us your waist measurement with your request: the belt adjusts, but we prefer to confirm with you. It runs from an Esva control unit, sold separately or included in the complete set.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-WAIST-01', 'ru', CONCAT('<h3>Главное</h3>
<ul>
<li><strong>Сосредоточен на талии и пояснице</strong> — зона, которую ни сапоги, ни брюки толком не обрабатывают.</li>
<li><strong>Камеры распределены по поясу</strong> — давление работает по всему обхвату талии и по пояснице, а не только спереди.</li>
<li><strong>Можно носить не лёжа</strong> — пояс держится на месте, пока вы двигаетесь; манжеты для ног этого не позволяют.</li>
<li><strong>Тот же блок, что и у сапог</strong> — если блок Esva уже есть, пояс подключается к нему.</li>
</ul>

<img src="', @img, 'SIMG0000000029.png" alt="Компрессионный пояс Esva">

<h3>Где применять</h3>
<p>Поясница и талия после тренировки; долгие часы сидя или стоя; вместе с брюками для восстановления — пояс работает по талии, брюки по бёдрам.</p>

<img src="', @img, 'SIMG0000000028.png" alt="Пояс на модели">

<h3>Перед заказом</h3>
<p>Укажите обхват талии в запросе: пояс регулируется, но мы предпочитаем подтвердить с вами. Работает от блока управления Esva: отдельно или в полном комплекте.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-WAIST-01', 'es', CONCAT('<h3>Puntos fuertes</h3>
<ul>
<li><strong>Centrado en la cintura y la zona lumbar</strong> — una zona que ni las botas ni el pantalón tratan de verdad.</li>
<li><strong>Cámaras repartidas por la faja</strong> — el inflado trabaja todo el contorno de la cintura y la lumbar, no solo la parte delantera.</li>
<li><strong>Se puede llevar sin estar tumbado</strong> — el cinturón se mantiene en su sitio mientras se mueve, algo que los manguitos de pierna no permiten.</li>
<li><strong>La misma unidad que las botas</strong> — si ya tiene una unidad Esva, el cinturón se conecta a ella.</li>
</ul>

<img src="', @img, 'SIMG0000000029.png" alt="Cinturón de compresión Esva">

<h3>En qué contextos</h3>
<p>Zona lumbar y cintura después de entrenar; muchas horas sentado o de pie; junto al pantalón de recuperación — el cinturón cubre la cintura, el pantalón los muslos y las caderas.</p>

<img src="', @img, 'SIMG0000000028.png" alt="Cinturón puesto en la cintura">

<h3>Antes de pedir</h3>
<p>Indíquenos su contorno de cintura en la solicitud: el cinturón se ajusta, pero preferimos confirmarlo con usted. Funciona con una unidad de control Esva, vendida aparte o incluida en el pack completo.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-WAIST-01', 'ja', CONCAT('<h3>特長</h3>
<ul>
<li><strong>腰と腰背部に特化</strong> — ブーツでもパンツでも十分にはケアできない部位です。</li>
<li><strong>ベルト全体に気室を配置</strong> — 加圧が腰回りと腰背部全体に働きます。前面だけではありません。</li>
<li><strong>横にならずに装着できます</strong> — 動いてもベルトは位置を保ちます。脚のカフではこうはいきません。</li>
<li><strong>ブーツと同じユニット</strong> — Esva ユニットをお持ちなら、そのまま接続できます。</li>
</ul>

<img src="', @img, 'SIMG0000000029.png" alt="Esva ウエストベルト">

<h3>どんな場面で</h3>
<p>トレーニング後の腰背部と腰回り。長時間の座り仕事・立ち仕事。リカバリーパンツとの併用 — ベルトは腰回り、パンツは太ももと腰を担当します。</p>

<img src="', @img, 'SIMG0000000028.png" alt="ウエストベルトの装着">

<h3>ご注文前に</h3>
<p>ご依頼時にウエスト周径をお知らせください。ベルトは調節できますが、確認のうえご案内します。Esva コントロールユニットで動作します（単体またはフルセットに含まれます）。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-WAIST-01', 'zh', CONCAT('<h3>产品亮点</h3>
<ul>
<li><strong>专做腰和腰背</strong> —— 靴子和裤子都照顾不到位的部位。</li>
<li><strong>气室分布在整条腰带上</strong> —— 加压作用在整圈腰围和腰背，不只是前面。</li>
<li><strong>不用躺着也能戴</strong> —— 腰带在你活动时不会移位，腿部套做不到这一点。</li>
<li><strong>和靴子共用同一台主机</strong> —— 已经有 Esva 主机，腰带直接接上去。</li>
</ul>

<img src="', @img, 'SIMG0000000029.png" alt="Esva 加压腰带">

<h3>适用场景</h3>
<p>训练后的腰背和腰围；长时间坐着或站着；和加压裤配合 —— 腰带管腰围，裤子管大腿和臀部。</p>

<img src="', @img, 'SIMG0000000028.png" alt="模特佩戴腰带">

<h3>下单前</h3>
<p>在询价时告诉我们你的腰围：腰带可以调，但我们更想跟你确认一下。腰带由 Esva 主机驱动，主机单独出售，或者包含在整套里。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- =====================================================================
-- ⑥ EB-BAG-01 · 收纳包
--    配图：0026 包的平铺图（025/024 是微信小图，最大仅 326px，放正文会糊，不用）
-- =====================================================================
INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-BAG-01', 'fr', CONCAT('<h3>Points forts</h3>
<ul>
<li><strong>Une unité et un jeu de manchons</strong> — de quoi emporter l’appareil et une tenue complète, sans sac supplémentaire.</li>
<li><strong>Format cabine</strong> — il se glisse dans un bagage à main : l’appareil reste avec vous au lieu de partir en soute.</li>
<li><strong>Ouverture large</strong> — on sort l’unité et les manchons sans tout vider.</li>
<li><strong>Toile souple, poignée et bandoulière</strong> — se porte à la main ou à l’épaule.</li>
</ul>

<img src="', @img, 'SIMG0000000026.png" alt="Sac de transport Esva">

<h3>Pour qui</h3>
<p>Pour celles et ceux qui emportent le matériel au club, au cabinet ou en déplacement plutôt que de le laisser à la maison. En usage partagé, il sert aussi à ranger l’appareil entre deux séances, à l’abri de la poussière.</p>

<h3>Avant de commander</h3>
<p>Le sac contient l’unité de commande et un jeu de manchons. Si vous ne possédez encore ni l’un ni l’autre, commencez par le pack complet — le sac est un accessoire, pas un point de départ.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-BAG-01', 'en', CONCAT('<h3>Highlights</h3>
<ul>
<li><strong>One unit and one set of garments</strong> — enough to carry the device and a full set, with no extra bag.</li>
<li><strong>Cabin-sized</strong> — it fits in hand luggage, so the equipment stays with you instead of going in the hold.</li>
<li><strong>Wide opening</strong> — get the unit and the garments out without emptying the whole bag.</li>
<li><strong>Soft fabric, handle and shoulder strap</strong> — carry it by hand or over the shoulder.</li>
</ul>

<img src="', @img, 'SIMG0000000026.png" alt="Esva carry bag">

<h3>Who it is for</h3>
<p>For anyone taking the equipment to the club, the practice or on a trip rather than leaving it at home. With shared equipment it also keeps the unit out of the dust between sessions.</p>

<h3>Before you order</h3>
<p>The bag holds the control unit and one set of garments. If you own neither yet, start with the complete set — the bag is an accessory, not a starting point.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-BAG-01', 'ru', CONCAT('<h3>Главное</h3>
<ul>
<li><strong>Блок и один комплект манжет</strong> — хватает, чтобы увезти прибор и полный комплект, без дополнительной сумки.</li>
<li><strong>Под ручную кладь</strong> — помещается в ручную кладь: прибор остаётся с вами, а не уезжает в багаж.</li>
<li><strong>Широкое открытие</strong> — блок и манжеты достаются, не вытряхивая всё содержимое.</li>
<li><strong>Мягкая ткань, ручка и плечевой ремень</strong> — носить в руке или на плече.</li>
</ul>

<img src="', @img, 'SIMG0000000026.png" alt="Сумка для переноски Esva">

<h3>Для кого</h3>
<p>Для тех, кто возит оборудование в клуб, в кабинет или в поездку, а не оставляет дома. При совместном использовании сумка ещё и держит прибор закрытым от пыли между сеансами.</p>

<h3>Перед заказом</h3>
<p>В сумке помещаются блок управления и один комплект манжет. Если у вас пока нет ни того, ни другого, начните с полного комплекта — сумка это аксессуар, а не отправная точка.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-BAG-01', 'es', CONCAT('<h3>Puntos fuertes</h3>
<ul>
<li><strong>Una unidad y un juego de prendas</strong> — suficiente para llevar el equipo y un juego completo, sin bolsa adicional.</li>
<li><strong>Tamaño de cabina</strong> — cabe en el equipaje de mano: el equipo se queda con usted en lugar de ir en la bodega.</li>
<li><strong>Apertura amplia</strong> — se sacan la unidad y las prendas sin vaciar toda la bolsa.</li>
<li><strong>Tejido suave, asa y correa</strong> — se lleva en la mano o al hombro.</li>
</ul>

<img src="', @img, 'SIMG0000000026.png" alt="Bolsa de transporte Esva">

<h3>Para quién</h3>
<p>Para quien se lleva el equipo al club, a la consulta o de viaje en vez de dejarlo en casa. En uso compartido también mantiene la unidad protegida del polvo entre sesiones.</p>

<h3>Antes de pedir</h3>
<p>La bolsa admite la unidad de control y un juego de prendas. Si aún no tiene ninguna de las dos cosas, empiece por el pack completo: la bolsa es un accesorio, no un punto de partida.</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-BAG-01', 'ja', CONCAT('<h3>特長</h3>
<ul>
<li><strong>ユニット 1 台とカフ 1 式</strong> — 本体と 1 式をまとめて持ち運べます。別のバッグは要りません。</li>
<li><strong>機内持ち込みサイズ</strong> — 手荷物に入ります。預け入れではなく、手元に置いたまま移動できます。</li>
<li><strong>大きく開く</strong> — 中身を全部出さなくても、ユニットとカフを取り出せます。</li>
<li><strong>柔らかい生地、ハンドルとショルダーストラップ</strong> — 手でも肩でも持てます。</li>
</ul>

<img src="', @img, 'SIMG0000000026.png" alt="Esva キャリーバッグ">

<h3>こんな方に</h3>
<p>自宅に置かず、クラブ・施術院・出張先へ機材を持って行く方に。共用の場合、施術と施術のあいだ本体をほこりから守る収納にもなります。</p>

<h3>ご注文前に</h3>
<p>バッグにはコントロールユニット 1 台とカフ 1 式が入ります。どちらもまだお持ちでない場合は、まずフルセットからお始めください。バッグはアクセサリーであり、出発点ではありません。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `u_s_sitegoods_lang` (`shop_code`,`sitecode`,`goodscode`,`lang`,`description`)
SELECT @shop, @site, 'EB-BAG-01', 'zh', CONCAT('<h3>产品亮点</h3>
<ul>
<li><strong>装一台主机 + 一套加压衣</strong> —— 设备和一整套都能带走，不用另外找袋子。</li>
<li><strong>登机箱尺寸</strong> —— 能塞进随身行李：设备跟着你走，而不是进托运。</li>
<li><strong>开口大</strong> —— 不用把东西全倒出来，就能拿到主机和加压衣。</li>
<li><strong>软面料，提手 + 肩带</strong> —— 手提或单肩都行。</li>
</ul>

<img src="', @img, 'SIMG0000000026.png" alt="Esva 收纳包">

<h3>给谁用</h3>
<p>给那些把设备带去俱乐部、诊所或出差、而不是放在家里的人。共用设备时，它也能让主机在两次使用之间不落灰。</p>

<h3>下单前</h3>
<p>包里装得下一台主机和一套加压衣。如果这两样你都还没有，先从整套开始 —— 包是配件，不是起点。</p>')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

COMMIT;

-- =====================================================================
-- 核对
-- =====================================================================
SELECT goodscode, lang, CHAR_LENGTH(description) AS 字数,
       (CHAR_LENGTH(description) - CHAR_LENGTH(REPLACE(description,'<img ','')))/5 AS 图数
  FROM `u_s_sitegoods_lang`
 WHERE `sitecode` = @site
   AND `goodscode` IN ('EB-SET-01','EB-UNIT-01','EB-TROUS-01','EB-ARM-01','EB-WAIST-01','EB-BAG-01')
 ORDER BY goodscode, lang;

-- =====================================================================
-- 回滚
-- =====================================================================
-- UPDATE `u_s_sitegoods_lang` SET `description` = NULL
--  WHERE `sitecode` = @site
--    AND `goodscode` IN ('EB-SET-01','EB-UNIT-01','EB-TROUS-01','EB-ARM-01','EB-WAIST-01','EB-BAG-01');

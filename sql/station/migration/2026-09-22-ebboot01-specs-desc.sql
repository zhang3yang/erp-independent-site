-- =====================================================================
-- EB-BOOT-01「Esva 恢复靴」的规格参数与商品详情
-- =====================================================================
--
-- ⚠ 只跑在独立站那台（库 sdsstation）。
--
-- 内容全部取自这个商品自己的 5 张商品图，没有编的成分：
--   · Chambres               ← 图5 6-Zone
--   · Modes de massage       ← 图4 8 Massage Modes
--   · Enveloppement          ← 图3 360 / Zipper
--   · Tissu                  ← 图3 Waterproof
--   · Dégonflage             ← 图3 Exhaust hole
--   · Commande               ← 图2 主机屏
--   · Tailles                ← 库里的 combos
--
-- ⚠ 刻意没写「标配清单」。第 2 张图的 Standard accessories 里列了主机，
--   而本商品的简介写明「需配 Esva 主机使用，主机可单买」——那张图多半属于
--   整套（EB-SET-01）。把主机写进本商品的标配是会被买家拿来说事的。
--
-- 规格的行清单由默认语言（fr）定，其余语言按默认键对照，
-- 和管理台「按语言编辑规格」存的是同一个结构。
--
-- 可重复执行：都是 UPDATE，跑几遍结果一样。

START TRANSACTION;

-- ---------------------------------------------------------------------
-- ① 主表：默认语言的规格（定行清单与顺序）
-- ---------------------------------------------------------------------
UPDATE `u_s_sitegoods`
   SET `specsjson` = '{"Chambres":"6 — cuisse, genou, mollet supérieur, mollet inférieur, cheville, pied","Modes de massage":"8 modes","Enveloppement":"360°, ouverture par fermeture à glissière latérale","Tissu":"Tissu déperlant, nettoyage par simple essuyage","Dégonflage":"Orifices d’évacuation d’air intégrés","Commande":"Par l’unité Esva : écran couleur, durée / pression / mode / chambre réglables sur l’appareil","Tailles":"S/M · L · XL · XXL"}'
 WHERE `goodscode` = 'EB-BOOT-01' AND `sitecode` = 'DLZ2049739702';

-- ---------------------------------------------------------------------
-- ② 各语言：规格译文 + 详情正文
-- ---------------------------------------------------------------------

-- fr
UPDATE `u_s_sitegoods_lang`
   SET `specsjson`   = '{"Chambres":{"k":"Chambres","v":"6 — cuisse, genou, mollet supérieur, mollet inférieur, cheville, pied"},"Modes de massage":{"k":"Modes de massage","v":"8 modes"},"Enveloppement":{"k":"Enveloppement","v":"360°, ouverture par fermeture à glissière latérale"},"Tissu":{"k":"Tissu","v":"Tissu déperlant, nettoyage par simple essuyage"},"Dégonflage":{"k":"Dégonflage","v":"Orifices d’évacuation d’air intégrés"},"Commande":{"k":"Commande","v":"Par l’unité Esva : écran couleur, durée / pression / mode / chambre réglables sur l’appareil"},"Tailles":{"k":"Tailles","v":"S/M · L · XL · XXL"}}',
       `description` = '<h3>Points forts</h3>
<ul>
<li><strong>Compression séquentielle à six chambres</strong> — cuisse, genou, mollet supérieur, mollet inférieur, cheville et pied se gonflent tour à tour, de l’extrémité vers le cœur.</li>
<li><strong>Huit modes de massage</strong> — chacun avec son ordre et son rythme de gonflage : la récupération après l’entraînement et les jambes lourdes en fin de journée ne se traitent pas de la même façon.</li>
<li><strong>Enveloppement à 360°</strong> — la jambière fait le tour complet de la jambe, sans zone découverte ; la fermeture à glissière latérale permet de l’enfiler seul.</li>
<li><strong>Tissu déperlant</strong> — la transpiration ou une éclaboussure s’essuie directement.</li>
<li><strong>Orifices d’évacuation</strong> — le dégonflage est rapide en fin de séance, sans attente.</li>
</ul>
<h3>Dans quels contextes</h3>
<p>Récupération des jambes après l’entraînement et entre les épreuves ; jambes lourdes après de longues heures debout, assis ou en avion ; équipement partagé en centre de rééducation, cabinet de kinésithérapie, salle de sport et club sportif.</p>
<h3>Choisir sa taille</h3>
<p>Quatre tailles : S/M, L, XL et XXL. Mesurez la circonférence au point le plus large du mollet ainsi que la longueur du talon au haut de la cuisse, puis transmettez-nous ces deux chiffres avec votre demande : nous confirmerons la taille.</p>
<h3>À propos de l’unité de commande</h3>
<p>Cette page vend les jambières, au prix de la paire. Elles fonctionnent avec une unité de commande Esva, disponible séparément ou dans un ensemble complet — précisez-nous ce dont vous avez besoin dans votre demande.</p>'
 WHERE `goodscode` = 'EB-BOOT-01' AND `lang` = 'fr';

-- ru
UPDATE `u_s_sitegoods_lang`
   SET `specsjson`   = '{"Chambres":{"k":"Количество камер","v":"6 — бедро, колено, верх голени, низ голени, лодыжка, стопа"},"Modes de massage":{"k":"Режимы массажа","v":"8 режимов"},"Enveloppement":{"k":"Охват","v":"360°, боковая молния"},"Tissu":{"k":"Ткань","v":"Водоотталкивающая, чистится протиранием"},"Dégonflage":{"k":"Сброс давления","v":"Встроенные выпускные отверстия"},"Commande":{"k":"Управление","v":"Блоком Esva: цветной экран, время / давление / режим / камера задаются на устройстве"},"Tailles":{"k":"Размеры","v":"S/M · L · XL · XXL"}}',
       `description` = '<h3>Ключевые особенности</h3>
<ul>
<li><strong>Последовательная компрессия в шести камерах</strong> — бедро, колено, верх и низ голени, лодыжка и стопа накачиваются поочерёдно, от дальнего конца к сердцу.</li>
<li><strong>Восемь режимов массажа</strong> — у каждого свой порядок и ритм накачивания: восстановление после тренировки и тяжесть в ногах после долгого дня на ногах настраиваются по-разному.</li>
<li><strong>Охват 360°</strong> — манжета полностью обхватывает ногу без зазоров, а боковая молния позволяет надеть её без посторонней помощи.</li>
<li><strong>Водоотталкивающая ткань</strong> — пот или пролитая вода просто вытираются.</li>
<li><strong>Выпускные отверстия</strong> — после сеанса воздух выходит быстро, ждать не приходится.</li>
</ul>
<h3>Где применяется</h3>
<p>Восстановление ног после тренировок и между стартами; тяжесть в ногах после долгих часов стоя, сидя или в перелёте; общее оборудование в реабилитационных центрах, физиотерапевтических кабинетах, спортзалах и командах.</p>
<h3>Как выбрать размер</h3>
<p>Четыре размера: S/M, L, XL и XXL. Измерьте обхват голени в самом широком месте и длину от пятки до верха бедра, затем пришлите нам обе цифры вместе с запросом — мы подтвердим размер.</p>
<h3>О блоке управления</h3>
<p>На этой странице продаются манжеты, цена указана за пару. Для работы нужен блок управления Esva: его можно купить отдельно или в составе полного комплекта — укажите в запросе, что именно вам нужно.</p>'
 WHERE `goodscode` = 'EB-BOOT-01' AND `lang` = 'ru';

-- es
UPDATE `u_s_sitegoods_lang`
   SET `specsjson`   = '{"Chambres":{"k":"Cámaras de aire","v":"6: muslo, rodilla, pantorrilla superior, pantorrilla inferior, tobillo y pie"},"Modes de massage":{"k":"Modos de masaje","v":"8 modos"},"Enveloppement":{"k":"Envoltura","v":"360° completa, cierre lateral de cremallera"},"Tissu":{"k":"Tejido","v":"Hidrófugo, se limpia con un paño"},"Dégonflage":{"k":"Desinflado","v":"Orificios de escape integrados"},"Commande":{"k":"Control","v":"Mediante la unidad Esva: pantalla a color; tiempo / presión / modo / cámara se ajustan en el equipo"},"Tailles":{"k":"Tallas","v":"S/M · L · XL · XXL"}}',
       `description` = '<h3>Puntos destacados</h3>
<ul>
<li><strong>Compresión secuencial de seis cámaras</strong>: muslo, rodilla, pantorrilla superior, pantorrilla inferior, tobillo y pie se inflan por turnos, desde el extremo hacia el corazón.</li>
<li><strong>Ocho modos de masaje</strong>: cada uno con su propio orden y ritmo de inflado, de modo que la recuperación tras el entrenamiento y las piernas cansadas al final del día se tratan de forma distinta.</li>
<li><strong>Envoltura de 360°</strong>: la pernera rodea la pierna por completo, sin huecos, y la cremallera lateral permite ponérsela sin ayuda.</li>
<li><strong>Tejido hidrófugo</strong>: el sudor o una salpicadura se limpian con un paño.</li>
<li><strong>Orificios de escape</strong>: el desinflado al terminar la sesión es rápido, sin esperas.</li>
</ul>
<h3>Dónde se utiliza</h3>
<p>Recuperación de piernas después del entrenamiento y entre pruebas; piernas cansadas tras muchas horas de pie, sentado o en avión; equipo compartido en centros de rehabilitación, salas de fisioterapia, gimnasios y equipos deportivos.</p>
<h3>Cómo elegir la talla</h3>
<p>Cuatro tallas: S/M, L, XL y XXL. Mida el contorno en la parte más ancha de la pantorrilla y la longitud desde el talón hasta la parte alta del muslo, y envíenos ambas cifras con su consulta: le confirmaremos la talla.</p>
<h3>Sobre la unidad de control</h3>
<p>En esta página se venden las perneras, con precio por par. Funcionan con una unidad de control Esva, que puede adquirirse por separado o dentro de un conjunto completo; indíquenos cuál necesita en su consulta.</p>'
 WHERE `goodscode` = 'EB-BOOT-01' AND `lang` = 'es';

-- ja
UPDATE `u_s_sitegoods_lang`
   SET `specsjson`   = '{"Chambres":{"k":"エアチャンバー数","v":"6（太もも・膝・ふくらはぎ上部・ふくらはぎ下部・足首・足）"},"Modes de massage":{"k":"マッサージモード","v":"8 種類"},"Enveloppement":{"k":"装着方式","v":"360° フルラップ、サイドファスナー開閉"},"Tissu":{"k":"生地","v":"防水生地、拭き取りで清掃可"},"Dégonflage":{"k":"排気","v":"本体に排気口を設計"},"Commande":{"k":"操作方法","v":"Esva 本体で操作：カラー液晶、時間 / 圧力 / モード / チャンバーを本体で設定"},"Tailles":{"k":"サイズ","v":"S/M・L・XL・XXL"}}',
       `description` = '<h3>製品の特長</h3>
<ul>
<li><strong>6 チャンバーの順次加圧</strong> — 太もも・膝・ふくらはぎ上部・ふくらはぎ下部・足首・足の順に加圧し、末端から心臓へ向かって送ります。</li>
<li><strong>8 種類のマッサージモード</strong> — 加圧の順序とリズムがそれぞれ異なり、トレーニング後のリカバリーと立ち仕事後の脚の重さを分けて使えます。</li>
<li><strong>360° フルラップ</strong> — 脚をぐるりと覆い、すき間がありません。サイドファスナーなので一人でも着脱できます。</li>
<li><strong>防水生地</strong> — 汗や水がかかっても拭き取るだけです。</li>
<li><strong>排気口の設計</strong> — 使用後の空気抜けが早く、待たされません。</li>
</ul>
<h3>使用シーン</h3>
<p>トレーニング後や競技の合間の脚のリカバリー、立ち仕事・座り仕事・長時間のフライト後の脚の重さ、リハビリ施設・整骨院・ジム・スポーツチームでの共用機器として。</p>
<h3>サイズの選び方</h3>
<p>S/M・L・XL・XXL の 4 サイズです。ふくらはぎの最も太い部分の周囲と、かかとから太もも付け根までの長さを測り、お問い合わせ時にその 2 つの数値をお知らせください。こちらでサイズを確認いたします。</p>
<h3>本体について</h3>
<p>このページで販売するのはレッグカバーで、1 組（2 本）単位です。使用には Esva 本体が必要です。本体は単体でも、本体付きのセットでもご用意があります。お問い合わせの際にご希望をお知らせください。</p>'
 WHERE `goodscode` = 'EB-BOOT-01' AND `lang` = 'ja';

-- zh
UPDATE `u_s_sitegoods_lang`
   SET `specsjson`   = '{"Chambres":{"k":"气室数量","v":"6 个——大腿、膝盖、小腿上段、小腿下段、脚踝、足部"},"Modes de massage":{"k":"按摩模式","v":"8 种"},"Enveloppement":{"k":"包裹方式","v":"360° 全包裹，侧边拉链开合"},"Tissu":{"k":"面料","v":"防水面料，擦拭即可清洁"},"Dégonflage":{"k":"排气","v":"机身排气孔设计"},"Commande":{"k":"控制方式","v":"由 Esva 主机控制：彩色显示屏，时间 / 压力 / 模式 / 气室都在机器上调"},"Tailles":{"k":"尺码","v":"S/M · L · XL · XXL"}}',
       `description` = '<h3>产品亮点</h3>
<ul>
<li><strong>6 气室顺序加压</strong>：大腿、膝盖、小腿上段、小腿下段、脚踝、足部逐段充放，按由远端向心脏的方向推送。</li>
<li><strong>8 种按摩模式</strong>：充气顺序和节奏各不相同，训练后放松和久站久坐后的腿部不适可以分开选。</li>
<li><strong>360° 全包裹</strong>：腿套绕整条腿一圈，不留空档；侧边拉链开合，一个人也能自己穿脱。</li>
<li><strong>防水面料</strong>：出汗或洒到水，擦干净就行。</li>
<li><strong>排气孔设计</strong>：一程结束后放气快，不用等。</li>
</ul>
<h3>适合什么场合</h3>
<p>训练后和比赛间歇的腿部放松；久站、久坐、长途飞行之后的腿部不适；康复中心、理疗室、健身房和运动队的共用设备。</p>
<h3>怎么选码</h3>
<p>共 S/M、L、XL、XXL 四个尺码。量一下小腿最粗处的周长，以及从脚跟到大腿根的长度，询价时把这两个数告诉我们，我们按数据帮你确认。</p>
<h3>关于主机</h3>
<p>本页出售的是腿套，按副计（两只）。使用时需要配 Esva 主机——主机可以单买，也有含主机的整套，询价时说明需要哪一种即可。</p>'
 WHERE `goodscode` = 'EB-BOOT-01' AND `lang` = 'zh';

-- en
UPDATE `u_s_sitegoods_lang`
   SET `specsjson`   = '{"Chambres":{"k":"Chambers","v":"6 — thigh, knee, upper calf, lower calf, ankle, foot"},"Modes de massage":{"k":"Massage modes","v":"8 modes"},"Enveloppement":{"k":"Wrap","v":"360° full wrap, side zip closure"},"Tissu":{"k":"Fabric","v":"Water-repellent, wipe clean"},"Dégonflage":{"k":"Deflation","v":"Built-in exhaust vents"},"Commande":{"k":"Control","v":"Via the Esva unit: colour screen, time / pressure / mode / chamber set on the device"},"Tailles":{"k":"Sizes","v":"S/M · L · XL · XXL"}}',
       `description` = '<h3>Highlights</h3>
<ul>
<li><strong>Six-chamber sequential compression</strong> — thigh, knee, upper calf, lower calf, ankle and foot inflate in turn, working from the far end toward the heart.</li>
<li><strong>Eight massage modes</strong> — each has its own inflation order and rhythm, so recovery after training and heavy legs after a long day on your feet can be treated differently.</li>
<li><strong>360° full wrap</strong> — the sleeve goes all the way around the leg with no gap, and the side zip means you can put it on unaided.</li>
<li><strong>Water-repellent fabric</strong> — sweat or a spill wipes straight off.</li>
<li><strong>Exhaust vents</strong> — the sleeves deflate quickly at the end of a session, with no waiting around.</li>
</ul>
<h3>Where it is used</h3>
<p>Leg recovery after training and between events; heavy legs after long hours standing, sitting or flying; shared equipment in rehabilitation centres, physiotherapy rooms, gyms and sports teams.</p>
<h3>Choosing a size</h3>
<p>Four sizes: S/M, L, XL and XXL. Measure the circumference at the widest part of the calf and the length from heel to upper thigh, then send us both figures with your enquiry and we will confirm the size for you.</p>
<h3>About the control unit</h3>
<p>This page sells the leg sleeves, priced per pair. They run from an Esva control unit, which can be bought on its own or as part of a complete set — tell us which you need when you enquire.</p>'
 WHERE `goodscode` = 'EB-BOOT-01' AND `lang` = 'en';

COMMIT;

-- ---------------------------------------------------------------------
-- 验一遍
-- ---------------------------------------------------------------------
SELECT
  (SELECT JSON_LENGTH(`specsjson`) FROM `u_s_sitegoods`
    WHERE `goodscode` = 'EB-BOOT-01' AND `sitecode` = 'DLZ2049739702')        AS `规格行数_应为7`,
  (SELECT COUNT(*) FROM `u_s_sitegoods_lang`
    WHERE `goodscode` = 'EB-BOOT-01' AND JSON_LENGTH(`specsjson`) = 7)  AS `规格齐的语言数_应为6`,
  (SELECT COUNT(*) FROM `u_s_sitegoods_lang`
    WHERE `goodscode` = 'EB-BOOT-01' AND CHAR_LENGTH(`description`) > 300) AS `详情齐的语言数_应为6`;

-- ---------------------------------------------------------------------
-- 回滚（清空这个商品的规格和详情，回到跑之前的样子）
-- ---------------------------------------------------------------------
--
-- UPDATE `u_s_sitegoods`      SET `specsjson` = NULL WHERE `goodscode` = 'EB-BOOT-01' AND `sitecode` = 'DLZ2049739702';
-- UPDATE `u_s_sitegoods_lang` SET `specsjson` = NULL, `description` = NULL WHERE `goodscode` = 'EB-BOOT-01';

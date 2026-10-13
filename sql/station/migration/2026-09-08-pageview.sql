-- =====================================================================
-- 前台浏览量：u_s_pageview（明细）+ u_s_pv_daily（日聚合）
-- =====================================================================
--
-- 起因：运营想知道「哪个商品被看得多」。在此之前这个库里**一条浏览记录都没有**，
-- 唯一的痕迹是 nginx 的 access log，而那份日志既不区分真人和爬虫、
-- 也没法和询盘/订单放在一句 SQL 里对照。
--
-- =====================================================================
-- 为什么是两张表
-- =====================================================================
--
--   · u_s_pageview   一次浏览一行。查细节用（这个访客还看了什么、从哪来的），
--                    **保留 90 天**，过期由 PageViewCleaner 删掉；
--   · u_s_pv_daily   按「天 + 页面类型 + 商品」汇总，一行一个格子。管理台读它。
--                    永不删除——它很小（一天几十行），而它是 90 天之后
--                    唯一还剩下的历史。
--
-- 只有明细表的话，管理台每次开概览都要扫全表；只有汇总表的话，
-- 「这个访客看了哪几个商品」这类问题永远答不了。两张都要。
--
-- ⚠ 汇总不是从明细跑批算出来的，是**每次上报时同步 upsert**（pv = pv + 1）。
--   跑批要处理「批没跑成」和「跑了两遍」，而 upsert 天然幂等于一次事件。
--   代价是两张表理论上会不一致（明细写成功、汇总写失败）——PageViewService
--   把两次写放在同一个事务里，所以实际只会一起成功或一起失败。
--
-- =====================================================================
-- ⚠ 这两张表都没有 shop_code
-- =====================================================================
--
-- 老表都带这一列。01_station_schema.sql 末尾那段写着它的约束：
-- 「新代码不得读它、不得按它过滤」。既然不读，新表就不建——
-- 建了只会让下一个人以为它有意义，然后在 WHERE 里带上它。
--
-- sitecode 留着：和 u_s_inquiry / u_s_chat 一致，将来多站数据合并时是唯一的区分依据。
--
-- =====================================================================
-- ⚠ clientip 存的是**截断过的** IP，和 u_s_chat.clientip 不一样
-- =====================================================================
--
-- 那两张表存完整 IP，是对的：询盘和聊天是「一个留了邮箱、主动联系我们的人」的
-- 业务记录，出了纠纷要查。
--
-- 这张表不是。它记的是**匿名浏览行为**，量大一百倍、保留 90 天，而访客在境外
-- （8 语言的出海站）。完整 IP 加一个长期 Cookie 在 GDPR 下就是个人数据，
-- 要征得同意才能存。截掉 IPv4 最后一段（203.0.113.47 → 203.0.113.0）之后，
-- 定位精度只到城市级，够用来看「哪个国家来的人多」，而不再指向某一个人。
--
-- 谁需要完整 IP？只有排查刷量。那种情况下限流日志里有完整 IP
-- （PageViewRateLimiter 打的 WARN），够定位了，不必为它把全表的精度都提上去。
--
-- =====================================================================
-- 可重复执行：只有 CREATE TABLE IF NOT EXISTS 和一条幂等的 UPDATE。
-- 和同目录其他脚本相互独立，谁先谁后都行。
-- 新建的库不用跑——01_station_schema.sql 里已经带上这两张表了。
-- =====================================================================

USE `sdsstation`;


-- ---------------------------------------------------------------------
-- 明细。一次浏览一行
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_pageview` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `sitecode`   varchar(50) NOT NULL COMMENT '站点编码，恒等于 u_s_siteinfo.sitecode',

  -- ⚠ 这一列是 UV 的**唯一**依据。它来自 SITE_VISITOR Cookie 里一个 16 字节随机串，
  --   不含任何个人信息、和买家账号没有关联。清了 Cookie 或换个浏览器就算一个新访客——
  --   这是这种统计方式的固有误差，不是 bug，别试图用 IP+UA 去「修正」它
  `visitorid`  varchar(64) NOT NULL COMMENT '访客标识，SITE_VISITOR Cookie 里的随机串。算 UV 靠它',
  `sessionid`  varchar(64) DEFAULT NULL COMMENT '会话标识，SITE_SESSION Cookie，30 分钟不活动就换一个。算「一次来访看了几页」用',

  `pagetype`   varchar(20) NOT NULL COMMENT '页面类型：home/list/detail/cart/checkout/account/other。⚠ 服务端按白名单校验，不认识的一律记 other',

  -- detail 之外的页面这一列是**空串不是 NULL**：它进了 u_s_pv_daily 的主键，
  -- 而 NULL 进不了主键。两张表用同一个约定，免得对不上
  `goodscode`  varchar(50) NOT NULL DEFAULT '' COMMENT '看的哪个商品，pagetype=detail 才有值，其余为空串。⚠ 服务端校验过它在 u_s_sitegoods 里真的存在，不是前端说什么就存什么',

  `pagepath`   varchar(300) NOT NULL COMMENT '页面路径，带语言前缀，如 /ja/products/G001。不存 query string',
  `lang`       varchar(10)  DEFAULT NULL COMMENT '访客在看哪个语种的页面',
  `referrer`   varchar(500) DEFAULT NULL COMMENT '来源页。⚠ 这一列比 pv 值钱：它区分「搜索进来的」和「直接输网址的」，是判断 SEO 有没有起作用的唯一依据',

  `clientip`   varchar(64)  DEFAULT NULL COMMENT '⚠ 已截断，不是完整 IP。IPv4 去掉最后一段、IPv6 只留前 48 位。理由见文件头',
  `country`    varchar(2)   DEFAULT NULL COMMENT '国家二字码。⚠ 现在恒为 NULL——没接 IP 归属库。留列不留空谈：将来接了直接回填，不用改表',
  `useragent`  varchar(500) DEFAULT NULL COMMENT '浏览器标识。留着是为了事后能重新判定爬虫（isbot 的规则会改，原始 UA 不会）',

  -- ⚠ 判定为爬虫的行**照样入库**，只是统计时排除。删掉的话，将来发现规则误杀了
  --   某个真实浏览器，那些数据就永远找不回来了
  `isbot`      tinyint      NOT NULL DEFAULT 0 COMMENT '1=判定为爬虫。不删，只在统计时排除',

  `createtime` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '浏览时间。⚠ 精度到毫秒：同一个人连点两下的间隔小于 1 秒，秒精度会把它们压成同一时刻',

  PRIMARY KEY (`id`),
  -- 按时间范围捞（今日 UV 现算走这条）
  KEY `idx_pv_time` (`sitecode`,`createtime`),
  -- 商品维度回溯：「这个商品最近 30 天被谁看过」
  KEY `idx_pv_goods` (`sitecode`,`goodscode`,`createtime`),
  -- 单个访客的足迹，以及 90 天清理的批量删除
  KEY `idx_pv_visitor` (`visitorid`,`createtime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前台浏览明细。保留 90 天，由 PageViewCleaner 清理';


-- ---------------------------------------------------------------------
-- 日聚合。管理台概览读的是这张
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `u_s_pv_daily` (
  `sitecode`  varchar(50) NOT NULL COMMENT '站点编码',

  -- ⚠ 「一天」按 **Asia/Shanghai** 切，不按 UTC。
  --   访客在境外，两种切法都有道理，选哪个都行——但必须**只选一个并且写下来**，
  --   否则 PageViewService 落库时用一种、管理台查询时用另一种，
  --   跨零点那几个小时的数字会对不上，而且极难发现。
  --   选东八区的理由：看这个数字的是国内的运营，他说「昨天」指的是他的昨天。
  `statdate`  date        NOT NULL COMMENT '统计日期，按 Asia/Shanghai 切天。见上面那段注释',

  -- ⚠⚠ 这一列有一个**特殊值 `_all`**，它不是一种页面类型，是「当天全站合计」。
  --
  --   每次上报会往这张表写**两行**：一行 ('_all','')，一行 (真实类型, 商品编码或空串)。
  --
  --   所以：
  --     · 查趋势、查「今天多少浏览」→ 只读 pagetype='_all' 那一行；
  --     · 查页面类型分布、商品排行 → 必须 pagetype <> '_all'，
  --       **不排除的话总数正好翻倍**，而翻倍这种错看起来只像「量比想象的大」。
  --
  --   为什么要存这么一行而不是把各类型加起来：因为 uv 加不起来（见下面 uv 那一列
  --   的注释）。pv 确实能加，但让 pv 和 uv 走两条不同的路子，迟早有人弄混。
  --
  --   下划线开头是刻意的：pagetype 的白名单里全是纯字母，撞不上。
  `pagetype`  varchar(20) NOT NULL COMMENT '页面类型，同 u_s_pageview.pagetype。⚠ 特殊值 _all = 当天全站合计，不是一种类型；查分布时必须排除它，否则总数翻倍',
  `goodscode` varchar(50) NOT NULL DEFAULT '' COMMENT '商品编码，非详情页和 _all 行为空串。⚠ 不能用 NULL——它在主键里',

  `pv`        int         NOT NULL DEFAULT 0 COMMENT '浏览次数，含爬虫。每次上报同步 +1',
  `pvhuman`   int         NOT NULL DEFAULT 0 COMMENT '排除爬虫之后的浏览次数。⚠ 看报表要看这一列，pv 那列只用来估爬虫占比',

  -- ⚠ **今天这一行的 uv 恒为 0**，直到次日 01:50 由 PageViewAggregator 回填。
  --   原因：UV 是 COUNT(DISTINCT visitorid)，没法像 pv 那样每来一次 +1
  --   （+1 之前得先知道这个访客今天来过没有，那就是一次查询）。
  --   所以管理台显示**今天**的 UV 时从 u_s_pageview 现算，显示历史某天时读这一列。
  --   两个数据源，但边界清楚：今天现算、往前读列。别把这两条路混起来用。
  --
  -- ⚠⚠ **uv 不能相加。任何方向都不行。**
  --
  --   它是 COUNT(DISTINCT visitorid)。同一个访客既看了首页又看了商品详情，
  --   在 home 那一行和 detail 那一行里各算一次——两行相加就把他算了两遍。
  --   跨天同理：一个访客周一来、周三又来，两天的 uv 各是 1，加起来不是 2 个人。
  --
  --   所以：全站某天的 uv 读 _all 那一行；**一段时间的 uv 只能从明细现算**，
  --   不存在从这张表推出来的办法；各维度那些 uv 只能单独看，
  --   比如「这个商品被 40 个人看了 200 次」。
  `uv`        int         NOT NULL DEFAULT 0 COMMENT '独立访客数，排除爬虫。⚠ 两条铁律：次日凌晨才回填（当天恒为 0，今天的由管理台现算）；且**不能相加**，跨行跨天都不行。见上面注释',

  `updatetime` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后累加时间',

  -- 主键就是四个维度：upsert 靠它命中，也就不需要额外的唯一索引
  PRIMARY KEY (`sitecode`,`statdate`,`pagetype`,`goodscode`),
  -- 趋势图（最近 N 天按天汇总）走这条
  KEY `idx_pvdaily_date` (`sitecode`,`statdate`),
  -- 商品排行（最近 30 天按商品汇总）走这条
  KEY `idx_pvdaily_goods` (`sitecode`,`goodscode`,`statdate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前台浏览日聚合。永不删除，是 90 天之后唯一剩下的历史';


-- ---------------------------------------------------------------------
-- 给运营角色加上看统计的权限
--
-- ⚠ admin 的 perms 是 `*`，不用管（ConsoleIdentity.hasPerm 里 `*` 短路通过）。
--   但 operator 是一串明确的权限串，不加这一条的话运营打开概览页
--   那个统计块就是 403——而页面上只会显示成「加载失败」，看不出是权限问题。
--
-- 幂等：FIND_IN_SET 判过，重复执行不会追加两次。
--
-- ⚠ 这条 UPDATE 的结果必须和 01_station_schema.sql 里那行出厂 perms **一致**。
--   老库靠这里追加、新库靠那一行，两条路要走到同一个终点。
--   （那个文件里的注释专门说了这件事：订单/客户那六个串就是因为只加在
--     migration 里、忘了同步基线，导致新装的机器上运营看不到订单菜单。）
-- ---------------------------------------------------------------------
UPDATE `u_s_role`
   SET `perms` = CONCAT(`perms`, ',site:stat:query')
 WHERE `rolecode` = 'operator'
   AND `perms` <> '*'
   AND NOT FIND_IN_SET('site:stat:query', `perms`);


-- ---------------------------------------------------------------------
-- 登记台账（见 2026-09-08-schema-migration-ledger.sql）
--
-- ⚠ 自己登记自己。那个台账脚本里写的是「跑完一个脚本，自己插一行」——靠人。
--   而它自己也说了「错的台账比没有台账更危险」。既然这一行可以由脚本自己写，
--   就不该留给人去记。
--
-- ⚠ 但**必须判一下台账表在不在**：那张表是 2026-09-08-schema-migration-ledger.sql
--   建的，而迁移脚本之间「谁先谁后都行」。台账还没建的库上直接 INSERT 会
--   ERROR 1146 把整个脚本中断在最后一步——前面的表其实都建好了，
--   而错误信息说的是一张跟这次改动毫无关系的表，看起来像脚本坏了。
--   （本机 2026-09-08 就是这个情况：台账脚本没在这里跑过。）
--
-- 幂等：INSERT IGNORE + 表存在判断，重复执行不报错也不重复插。
-- ---------------------------------------------------------------------
SET @has_ledger := (
  SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'u_s_schema_migration'
);

SET @ledger_sql := IF(@has_ledger > 0,
  'INSERT IGNORE INTO `u_s_schema_migration` (`filename`, `appliedby`, `source`, `note`)
     VALUES (''2026-09-08-pageview.sql'', ''claude'', ''执行'',
             ''建 u_s_pageview + u_s_pv_daily，给 operator 加 site:stat:query'')',
  'SELECT ''u_s_schema_migration 还不存在，跳过登记——这个库上跑一下台账脚本'' AS msg');

PREPARE stmt FROM @ledger_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 跑完之后
--
-- ⚠ 必须重启 station-console。两个原因：
--    · 上报接口、清理任务、聚合任务都是新代码，不重启就没有；
--    · 角色权限在会话里，运营得重新从 ERP 跳一次才拿到新权限（或等会话过期）。
--
-- 验证有没有真的在记：
--
--   -- 打开前台点几个商品，然后
--   SELECT pagetype, goodscode, pagepath, isbot, clientip, createtime
--     FROM u_s_pageview ORDER BY id DESC LIMIT 10;
--
--   -- clientip 应该是 x.x.x.0 这种形状。如果全都是 127.0.0.0，
--   -- 说明 nginx 或 Nuxt 那层没转发真实 IP，回去看 station.conf
--   -- （不影响计数，只是地域统计和限流会退化成全站一个额度）
--
--   SELECT * FROM u_s_pv_daily ORDER BY statdate DESC, pv DESC LIMIT 10;
--
-- ⚠ 如果 u_s_pageview 有数据而 u_s_pv_daily 是空的，那是 upsert 出了问题，
--   不是「还没到聚合时间」——汇总是同步写的，不等定时任务。
-- ---------------------------------------------------------------------

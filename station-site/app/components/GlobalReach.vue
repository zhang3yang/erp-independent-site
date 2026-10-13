<!--
  首页那一屏世界地图：以总部为中心，向各地代理商辐射的弧线。

  从 kangwanjia-website-EN 的 components/GlobalReach.vue 搬来，三处改了：

    1. **地点和数字变成 props**。上游把天津总部和 11 个城市写死在组件里。这个站是
       一套代码部署多个站，写死等于每个站都得改代码，所以改成传进来（不传就用
       上游那一套当默认值，直接放上去也能看）。
    2. **标题文案按站点语言取**。上游只有英文。
    3. **投影中心跟着总部走**，不再写死 117.2°E。

  ## 为什么敢在服务端算几何

  投影、经纬度转屏幕坐标、TopoJSON 转 GeoJSON 全是纯数学，没有 DOM，SSR 直接能跑，
  出来的 SVG 路径进 HTML——爬虫和关掉 JS 的浏览器都能看到这张图。

  ## 包体积

  `world-atlas/countries-110m.json` 是 108KB（gzip 后约 30KB），加上 d3-geo 摇树后
  的一小块，会进浏览器端的 chunk。这个量级可以接受，所以没有绕成服务端组件/接口。
  真要再省，换 `land-110m.json`（55KB，只有陆地轮廓、没有国界）。
-->
<template>
  <section class="section global">
    <div class="container">
      <div class="section-head">
        <h2>{{ heading.lead }}<span class="accent">{{ heading.accent }}</span></h2>
        <p>{{ lead || t.lead.replace('{city}', hqPlace.name) }}</p>
      </div>

      <div v-if="variant !== 'list'" class="map-wrap">
        <svg
          :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
          class="world-map"
          role="img"
          :aria-label="t.mapAlt.replace('{city}', hqPlace.name)"
        >
          <!-- 海面 -->
          <path :d="spherePath" class="sphere" />
          <!-- 经纬网 -->
          <path :d="graticulePath" class="graticule" />
          <!-- 国界（TopoJSON → GeoJSON） -->
          <path v-for="(d, i) in countryPaths" :key="i" :d="d" class="country" />

          <!-- 辐射弧线 -->
          <path
            v-for="(a, i) in arcs"
            :key="a.name"
            :d="a.d"
            class="arc"
            :style="{ animationDelay: `${(i % 6) * 0.2}s` }"
          />

          <!-- 目的地 -->
          <g v-for="d in destPoints" :key="d.name" class="marker">
            <circle :cx="d.xy[0]" :cy="d.xy[1]" r="3.4" class="dot" />
            <title>{{ d.name }}</title>
          </g>

          <!-- 总部 -->
          <g class="hq">
            <circle :cx="hqXY[0]" :cy="hqXY[1]" r="5.5" class="hq-pulse" />
            <circle :cx="hqXY[0]" :cy="hqXY[1]" r="5" class="hq-dot" />
            <text :x="hqXY[0] + 10" :y="hqXY[1] + 4" class="hq-label">
              {{ hqLabel }}
            </text>
            <title>{{ hqLabel }}</title>
          </g>
        </svg>
      </div>

      <!--
        目的地图例。地图上的点太小，手机上根本点不到，所以下面再列一遍。
        版式选「只列目的地」时地图整个不画，这一列就是这一块的主体，
        所以那时候它排得松一些（.legend--solo）。
      -->
      <ul v-if="destPoints.length" class="legend" :class="{ 'legend--solo': variant === 'list' }">
        <li v-for="d in destPoints" :key="d.name">
          <span class="legend-dot"></span>
          <span class="legend-name">{{ d.name }}</span>
        </li>
      </ul>

      <div v-if="statRows.length" class="stats">
        <div v-for="s in statRows" :key="s.label" class="stat">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { geoNaturalEarth1, geoPath, geoGraticule10 } from 'd3-geo'
import { feature } from 'topojson-client'
import worldData from 'world-atlas/countries-110m.json'

export interface ReachPlace {
  name: string
  /** [经度, 纬度] */
  coords: [number, number]
}

const props = withDefaults(
  defineProps<{
    /** 总部。地图会转到让它落在正中间 */
    hq?: ReachPlace | null
    destinations?: ReachPlace[] | null
    stats?: Array<{ value: string; label: string }> | null
    /** 站点语言，决定标题和图例的文案 */
    lang?: string
    /** 整句标题。传了就完全用它，不再拼「Global Reach, Local Impact」那种两段式 */
    title?: string
    lead?: string
    /**
     * 版式：`map` 画那张世界地图，`list` 只列目的地。
     *
     * ⚠ 选 `list` 也**省不掉**那 108KB 的国界数据：d3-geo 和 world-atlas 是这个
     *   组件的静态依赖，进的是同一个 chunk。真要省得把地图拆成一个异步组件。
     *   这里的取舍只是版面：地图在窄屏上本来就看不清几个点。
     */
    variant?: string
  }>(),
  { hq: null, destinations: null, stats: null, lang: 'en', title: '', lead: '', variant: 'map' }
)

/* ---------- 默认地点 ----------
   上游那一套（天津 + 11 个城市）。装修里没配 reach 的站直接用它，
   页面不至于空一块。 */
const DEFAULT_HQ: ReachPlace = { name: 'Tianjin', coords: [117.2, 39.13] }

const DEFAULT_DESTINATIONS: ReachPlace[] = [
  { name: 'Alberta', coords: [-113.49, 53.55] },
  { name: 'Texas', coords: [-97.74, 30.27] },
  { name: 'St. Petersburg', coords: [30.36, 59.93] },
  { name: 'Brussels', coords: [4.35, 50.85] },
  { name: 'Plovdiv', coords: [24.75, 42.14] },
  { name: 'Abidjan', coords: [-4.02, 5.36] },
  { name: 'Al Ain', coords: [55.76, 24.21] },
  { name: 'Bali', coords: [115.19, -8.41] },
  { name: 'Melbourne', coords: [144.96, -37.81] },
  { name: 'Alicante', coords: [-0.48, 38.35] },
  { name: 'Lyon', coords: [4.83, 45.76] }
]

/** 默认那 11 个地名按语言。装修里配了地名的站走后端拼好的那份（分语言），这里只管没配的 */
const DEFAULT_DEST_NAMES: Record<string, string[]> = {
  en: ['Alberta', 'Texas', 'St. Petersburg', 'Brussels', 'Plovdiv', 'Abidjan', 'Al Ain', 'Bali', 'Melbourne', 'Alicante', 'Lyon'],
  zh: ['阿尔伯塔', '得克萨斯', '圣彼得堡', '布鲁塞尔', '普罗夫迪夫', '阿比让', '艾因', '巴厘岛', '墨尔本', '阿利坎特', '里昂'],
  ja: ['アルバータ', 'テキサス', 'サンクトペテルブルク', 'ブリュッセル', 'プロヴディフ', 'アビジャン', 'アルアイン', 'バリ', 'メルボルン', 'アリカンテ', 'リヨン'],
  es: ['Alberta', 'Texas', 'San Petersburgo', 'Bruselas', 'Plovdiv', 'Abiyán', 'Al Ain', 'Bali', 'Melbourne', 'Alicante', 'Lyon'],
  pt: ['Alberta', 'Texas', 'São Petersburgo', 'Bruxelas', 'Plovdiv', 'Abidjan', 'Al Ain', 'Bali', 'Melbourne', 'Alicante', 'Lyon'],
  ru: ['Альберта', 'Техас', 'Санкт-Петербург', 'Брюссель', 'Пловдив', 'Абиджан', 'Эль-Айн', 'Бали', 'Мельбурн', 'Аликанте', 'Лион'],
  it: ['Alberta', 'Texas', 'San Pietroburgo', 'Bruxelles', 'Plovdiv', 'Abidjan', 'Al Ain', 'Bali', 'Melbourne', 'Alicante', 'Lione'],
  ko: ['앨버타', '텍사스', '상트페테르부르크', '브뤼셀', '플로브디프', '아비장', '알아인', '발리', '멜버른', '알리칸테', '리옹'],
  fr: ['Alberta', 'Texas', 'Saint-Pétersbourg', 'Bruxelles', 'Plovdiv', 'Abidjan', 'Al-Aïn', 'Bali', 'Melbourne', 'Alicante', 'Lyon']
}
/* ---------- 文案 ---------- */
const COPY: Record<string, Record<string, string>> = {
  en: { lead: 'From our headquarters in {city}, we ship to partners and buyers across six continents.',
        titleLead: 'Global Reach, ', titleAccent: 'Local Impact',
        hq: '{city} HQ', mapAlt: 'World map of our global network radiating from {city}',
        nodes: 'Global Nodes', partners: 'Distribution Partners', hqName: 'Tianjin' },
  zh: { lead: '从{city}总部出发，我们的产品发往六大洲的经销商和买家。',
        titleLead: '全球布局，', titleAccent: '本地服务',
        hq: '{city}总部', mapAlt: '以{city}为中心向全球辐射的网络地图',
        nodes: '全球节点', partners: '经销伙伴', hqName: '天津' },
  ja: { lead: '{city}の本社から、六大陸のパートナーとバイヤーへお届けしています。',
        titleLead: 'グローバルに、', titleAccent: 'ローカルに',
        hq: '{city}本社', mapAlt: '{city}を中心に世界へ広がるネットワークの地図',
        nodes: '拠点数', partners: '販売パートナー', hqName: '天津' },
  es: { lead: 'Desde nuestra sede en {city}, enviamos a socios y compradores de seis continentes.',
        titleLead: 'Alcance global, ', titleAccent: 'impacto local',
        hq: 'Sede en {city}', mapAlt: 'Mapa mundial de nuestra red desde {city}',
        nodes: 'Nodos globales', partners: 'Socios distribuidores', hqName: 'Tianjin' },
  pt: { lead: 'Da nossa sede em {city}, enviamos para parceiros e compradores em seis continentes.',
        titleLead: 'Alcance global, ', titleAccent: 'impacto local',
        hq: 'Sede em {city}', mapAlt: 'Mapa-mundi da nossa rede a partir de {city}',
        nodes: 'Nos globais', partners: 'Parceiros distribuidores', hqName: 'Tianjin' },
  ru: { lead: 'Из головного офиса в {city} мы поставляем партнёрам и покупателям на шести континентах.',
        titleLead: 'Глобальная сеть, ', titleAccent: 'местный сервис',
        hq: 'Офис {city}', mapAlt: 'Карта мира: наша сеть с центром в {city}',
        nodes: 'Точек присутствия', partners: 'Партнёров-дистрибьюторов', hqName: 'Тяньцзинь' },
  it: { lead: 'Dalla nostra sede di {city} spediamo a partner e acquirenti in sei continenti.',
        titleLead: 'Portata globale, ', titleAccent: 'presenza locale',
        hq: 'Sede di {city}', mapAlt: 'Mappa mondiale della nostra rete con centro a {city}',
        nodes: 'Nodi globali', partners: 'Partner distributori', hqName: 'Tianjin' },
  ko: { lead: '{city} 본사에서 6대륙의 파트너와 구매자에게 공급합니다.',
        titleLead: '글로벌 네트워크, ', titleAccent: '현지 대응',
        hq: '{city} 본사', mapAlt: '{city}를 중심으로 전 세계로 이어지는 네트워크 지도',
        nodes: '글로벌 거점', partners: '유통 파트너', hqName: '톈진' },
  fr: { lead: 'Depuis notre siège de {city}, nous livrons partenaires et acheteurs sur six continents.',
        titleLead: 'Une portée mondiale, ', titleAccent: 'un impact local',
        hq: 'Siège de {city}', mapAlt: 'Carte mondiale de notre réseau, rayonnant depuis {city}',
        nodes: 'Sites dans le monde', partners: 'Partenaires de distribution', hqName: 'Tianjin' }
}
const t = computed(() => COPY[props.lang] || COPY.en!)

/**
 * 总部。传了就用传的，没传用默认那一个——但**名字要跟着语言走**。
 *
 * 踩过一次：默认名写死成 'Tianjin'，中文站上就渲染出「从Tianjin总部出发」这种
 * 中英混排。目的地保持拉丁字母是可以的（地图上的地名惯例如此，而且都是专名），
 * 总部名出现在正文句子里，必须是本地语言。
 */
const hqPlace = computed<ReachPlace>(() =>
  props.hq || { ...DEFAULT_HQ, name: t.value.hqName || DEFAULT_HQ.name }
)
const destinations = computed(() => {
  if (props.destinations?.length) return props.destinations
  const names = DEFAULT_DEST_NAMES[props.lang] || DEFAULT_DEST_NAMES.en!
  return DEFAULT_DESTINATIONS.map((d, i) => ({ ...d, name: names[i] || d.name }))
})

const hqLabel = computed(() => t.value.hq!.replace('{city}', hqPlace.value.name))

/** 两段式标题：前半句常规色，后半句品牌色。传了 title 就整句用常规色。 */
const heading = computed(() =>
  props.title
    ? { lead: props.title, accent: '' }
    : { lead: t.value.titleLead, accent: t.value.titleAccent }
)

/**
 * 装修里没配数字就按地点数量算两个。
 * 刻意不编「1M+ 活跃用户」那种数——那是上游品牌站的营销文案，
 * 放到一个刚上线的外贸站上是假数据。
 */
const statRows = computed(() => {
  if (props.stats?.length) return props.stats
  return [
    { value: String(destinations.value.length + 1), label: t.value.nodes! },
    { value: `${destinations.value.length}+`, label: t.value.partners! }
  ]
})

/* ---------- 地图几何 ----------
   纯数学，SSR 安全。跟着 hq/destinations 变，所以是 computed 而不是模块级常量。 */
const WIDTH = 980
const HEIGHT = 520

const world = worldData as any
// TopoJSON → GeoJSON。这一步和地点无关，模块级只做一次
const countries = feature(world, world.objects.countries) as any

const projection = computed(() =>
  geoNaturalEarth1()
    // 转到让总部落在地图水平中心——弧线从中间往两边散开才好看，
    // 总部贴在边上的话有一半弧线要绕出画面
    .rotate([-hqPlace.value.coords[0], 0])
    .fitExtent([[8, 8], [WIDTH - 8, HEIGHT - 8]], { type: 'Sphere' } as any)
)

/**
 * ⚠ `digits(1)` 不是可有可无的调参。
 *
 * 177 个国家的轮廓最后是**内联在 HTML 里**的（SSR 出来的 SVG）。d3-geo 默认每个坐标
 * 输出 3 位小数，在这张 980px 宽的图上第 2、3 位小于十分之一像素，肉眼看不出，
 * 却让首页的 HTML 白白大出一截。降到 1 位之后首页从 255KB 降到 198KB
 * （gzip 87KB → 63KB），画面没有任何区别。
 *
 * 没有继续降到 0：那样再省 37KB，但整数坐标在图放大到 1100px 宽时海岸线会看出锯齿。
 *
 * 首页体积对这个站是直接的成本：它是 Google 抓得最勤的一页，也是海外买家进来的第一页。
 */
const pathGen = computed(() => geoPath(projection.value).digits(1))

const spherePath = computed(() => pathGen.value({ type: 'Sphere' } as any) || '')
const graticulePath = computed(() => pathGen.value(geoGraticule10()) || '')
const countryPaths = computed<string[]>(() =>
  countries.features.map((f: any) => pathGen.value(f) || '')
)

const hqXY = computed(() => projection.value(hqPlace.value.coords) as [number, number])

const destPoints = computed(() =>
  destinations.value
    .map(d => ({ ...d, xy: projection.value(d.coords) }))
    // 投影对超出范围的坐标会回 null。过滤掉，不然 SVG 里会出现 cx="NaN"
    .filter((d): d is ReachPlace & { xy: [number, number] } => Array.isArray(d.xy))
)

/** 屏幕坐标上的二次贝塞尔，微微拱起。不是大圆航线，只是好看。 */
function arcPath(a: [number, number], b: [number, number]): string {
  const [x1, y1] = a
  const [x2, y2] = b
  const dx = x2 - x1
  const dy = y2 - y1
  const dr = Math.sqrt(dx * dx + dy * dy) || 1
  const mx = (x1 + x2) / 2
  const my = (y1 + y2) / 2
  const lift = dr * 0.22
  // 中垂线方向偏移出控制点
  const cx = mx + (-dy / dr) * lift
  const cy = my + (dx / dr) * lift
  return `M${x1},${y1} Q${cx},${cy} ${x2},${y2}`
}

const arcs = computed(() =>
  destPoints.value.map(d => ({ name: d.name, d: arcPath(hqXY.value, d.xy) }))
)
</script>

<style scoped>
.map-wrap {
  background: radial-gradient(120% 120% at 50% 0%, #f4f8fb 0%, #ecf1f6 100%);
  border-radius: var(--radius-lg);
  padding: 18px;
  margin-bottom: 36px;
}

.world-map { width: 100%; height: auto; display: block; }

.sphere { fill: #e3edf3; stroke: none; }
.graticule { fill: none; stroke: rgba(11, 114, 171, 0.08); stroke-width: 0.5; }
.country {
  fill: #c9dce8;
  stroke: #f1f6fa;
  stroke-width: 0.5;
  transition: fill 0.2s ease;
}
.country:hover { fill: #b4cee0; }

/* 辐射弧线 */
.arc {
  fill: none;
  stroke: var(--primary);
  stroke-width: 1.1;
  stroke-opacity: 0.55;
  stroke-linecap: round;
  stroke-dasharray: 5 7;
  animation: arc-dash 1.5s linear infinite;
}
@keyframes arc-dash {
  to { stroke-dashoffset: -24; }
}

.dot { fill: var(--accent-teal); stroke: #fff; stroke-width: 1; }
.marker:hover .dot { fill: var(--primary); }

.hq-dot { fill: var(--primary); stroke: #fff; stroke-width: 1.5; }
.hq-pulse {
  fill: var(--primary);
  transform-box: fill-box;
  transform-origin: center;
  animation: hq-pulse 2.2s ease-out infinite;
}
@keyframes hq-pulse {
  0% { transform: scale(1); opacity: 0.5; }
  100% { transform: scale(3.6); opacity: 0; }
}
.hq-label {
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
  fill: var(--primary-dark);
}

/* 动画全是装饰性的，跟着系统的「减少动态效果」一起关掉 */
@media (prefers-reduced-motion: reduce) {
  .arc, .hq-pulse { animation: none; }
}

/* 图例 */
/* 只列目的地那个版式下，这一列就是这一块的主体：字大一点、排松一点，
   不然一屏里只剩一行小灰字 */
.legend--solo {
  margin-top: 6px;
  gap: 14px 30px;
}
.legend--solo .legend-name { font-size: 1rem; color: var(--text); }

.legend {
  list-style: none;
  padding: 0;
  margin: 0 auto 44px;
  max-width: 860px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px 18px;
}
.legend li {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.86rem;
  color: var(--text);
}
.legend-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent-teal); }
.legend-name { color: var(--text-muted); font-size: 0.8rem; }

/* 数字 */
.stats { display: flex; justify-content: center; gap: 90px; flex-wrap: wrap; }
.stat { text-align: center; }
.stat-value {
  font-family: var(--font-display);
  font-size: 2.4rem;
  font-weight: 700;
  color: var(--text);
}
.stat-label {
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-soft);
}

@media (max-width: 720px) {
  .stats { gap: 40px; }
  .hq-label { font-size: 16px; }
}
</style>

<!--
  商品列表。版式照 kangwanjia-website-EN 的 pages/product/index.vue：
  左边 220px 的两级分类侧栏，右边「当前分类标题 + 两列大卡片」，
  下面接一段「探索其他产品」。

  ## 分类是两级的，来自分隔符

  后端下发的 `categories` 是一列平铺的自由文本，两级结构由 `~/lib/categories`
  按分隔符拆出来（`加压疗法/腿部系统`）。为什么这么做、代价是什么，见那个文件的头注释。
  筛选参数仍然是完整的分类串，后端一个字没改。

  ## 筛选状态放 URL 而不是组件里

  分类、搜索词、页码全部走 query string，理由有三个：
    1. SSR 时服务端就能按条件取数——爬虫和用户拿到的是同一份已经筛好的 HTML；
    2. 用户能把「第 3 页的腿部系统」这个链接直接发给同事；
    3. 浏览器前进后退符合预期。

  所以侧栏那些子项是真的 <a href>（NuxtLink），不是 @click 改本地 state——
  参考站那边是纯前端 state（点了只换标题，产品是写死筛的），这里不能照抄：
  分类页是买家从谷歌落地的第二入口，每个分类得有自己的 URL 才收得进索引。

  ## 没选分类时默认第一个

  跟参考站一致：进 /products 默认选中第一个大组的第一个子项，不是「全部」。
  站点一个分类都没配时（categories 为空）退回显示全部商品，侧栏只剩标题。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <section class="products-page section">
      <div class="wrap products-layout">
        <!-- 侧栏 -->
        <aside class="sidebar">
          <h1 class="page-title">{{ t.products }}</h1>

          <!-- 参考站的搜索框在顶栏上，这个站的顶栏没有，所以放在侧栏标题下面。
               整站唯一能搜商品的入口，不能因为版式对齐就删掉 -->
          <form class="side-search" @submit.prevent="applySearch">
            <input v-model="kwInput" class="ss-input" :placeholder="t.searchHint"
                   :aria-label="t.search" maxlength="60">
            <button class="ss-btn" type="submit" :aria-label="t.search">
              <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <path d="M10.6 4.4a6.2 6.2 0 1 0 0 12.4 6.2 6.2 0 0 0 0-12.4Zm4.5 10.7 4.5 4.5"
                      fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              </svg>
            </button>
          </form>

          <nav v-if="groups.length" class="cat-nav">
            <div v-for="cat in groups" :key="cat.name" class="cat-group">
              <!--
                有子项的大组：组名**本身也能筛**（后端按前缀匹配整个子树，见
                SiteGoodsMapper.xml 的 publicWhere），所以标题是链接、箭头是开关，
                两件事分开两个可点区域。

                以前整行是一个 <button>，只能折叠。那时候「看完这个大组下面所有商品」
                这件事在站上是做不到的——只能一个子类一个子类点过去。

                ⚠ 标题必须是真的 <a>（NuxtLink 渲染成 a）：大组页是一个真实的落地页，
                  爬虫要能从侧栏走进去。做成 @click 跳转的话，站内层级对 Google 就断了。

                没子项的（商家只填了「配件」）走下面那个分支，整行就是一个链接。
              -->
              <div
                v-if="cat.items.length"
                class="cat-heading"
                :class="{ 'cat-heading--open': cat.name === openGroup, 'cat-heading--on': cat.name === currentCat }"
              >
                <!-- ⚠ 链接里必须是 cat.name（原串），显示的是 cat.label（当前语言）：
                     后端按原串筛，拿译名去筛一条都查不出来 -->
                <NuxtLink class="cat-heading-link" :to="linkTo({ cat: cat.name, page: undefined })">
                  {{ cat.label }}
                </NuxtLink>
                <button
                  type="button"
                  class="cat-toggle"
                  :aria-expanded="cat.name === openGroup"
                  :aria-label="cat.label"
                  @click="toggleGroup(cat.name)"
                >
                  <span class="cat-caret" aria-hidden="true">▾</span>
                </button>
              </div>
              <NuxtLink
                v-else
                class="cat-heading cat-heading--leaf"
                :class="{ 'cat-heading--open': cat.value === currentCat }"
                :to="linkTo({ cat: cat.value, page: undefined })"
              >
                <span>{{ cat.label }}</span>
              </NuxtLink>

              <div class="cat-items" :class="{ 'cat-items--open': cat.name === openGroup }">
                <NuxtLink
                  v-for="item in cat.items"
                  :key="item.value"
                  class="cat-item"
                  :class="{ 'cat-item--active': item.value === currentCat }"
                  :to="linkTo({ cat: item.value, page: undefined })"
                >
                  {{ item.label }}
                </NuxtLink>
              </div>
            </div>
          </nav>
        </aside>

        <!-- 主栏 -->
        <div class="main-col">
          <header class="col-head">
            <h2>{{ headTitle }}</h2>
            <p>{{ t.lead }}</p>
          </header>

          <!-- 只有下面没有「探索其他产品」那一段时才撑最小高度：商家刚上线只有三五个
               商品时，一行卡片下面直接就是页脚，整页看着像没加载完。有那一段的时候
               撑高只会在两个网格中间留出一大片空白 -->
          <div class="results" :class="{ 'results--fill': !others.length }">
            <div v-if="data.rows.length" class="card-grid">
              <ProductListCard v-for="item in data.rows" :key="item.code"
                               :item="item" :lang="site.lang" />
            </div>
            <p v-else class="empty muted">{{ t.empty }}</p>
          </div>

          <!-- 分页链接是真的 <a>：爬虫要靠它们把第 2、3 页也爬到。
               用按钮 + JS 的话，除了第一页之外的商品 Google 永远看不到 -->
          <nav v-if="totalPages > 1" class="pager">
            <NuxtLink v-if="currentPage > 1" :to="linkTo({ page: currentPage - 1 })" class="pg">
              ‹ {{ t.prev }}
            </NuxtLink>
            <span class="pg-now">{{ currentPage }} / {{ totalPages }}</span>
            <NuxtLink v-if="currentPage < totalPages" :to="linkTo({ page: currentPage + 1 })" class="pg">
              {{ t.next }} ›
            </NuxtLink>
          </nav>

          <template v-if="others.length">
            <header class="col-head second">
              <h2>{{ t.other }}</h2>
            </header>
            <div class="card-grid">
              <ProductListCard v-for="item in others" :key="item.code"
                               :item="item" :lang="site.lang" />
            </div>
          </template>
        </div>
      </div>
    </section>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { categoryLeaf, groupCategories, groupOf, isUnder } from '~/lib/categories'

const site = await useSiteInfo()
const route = useRoute()
const localePath = useLocalePath()

const PAGE_SIZE = 12

const groups = computed(() => groupCategories(site.categories, site.categoryNames))

/*
  默认分类：**子项最多的那个大组**（整组，不是它下面某一个子类）。

  以前这里取的是 `groups[0]`——也就是字典序第一个分类。那个顺序来自后端的
  `select distinct sitecategory ... order by 1`（SiteGoodsMapper.selectPublicCategories），
  和「哪个品类是主打」毫无关系。统领站上它落在 `Batteries`：整站 19 个商品，
  买家进 /products 先看到的是一块电池，主打的 `Compression Garments`（4 个子类、
  7 个商品）反而要自己去侧栏点。

  换成「子项最多的大组」而不是写死某个分类名：**这份代码是多站共用的**
  （康辰站、统领站跑的是同一套），写死站名就成了另一个站的 bug。
  商家愿意给某个品类分子类，本身就说明他在那儿的品最多、也最在意，
  拿它当落地分类比字典序第一个（多半是个只有一个商品的叶子分类）合理。

  落到**整组**而不是它的第一个子类：统领站上第一个子类是 `Arm Sleeves`，只有一个
  商品，那和落在 Batteries 没多大区别。组名能筛是后端那条前缀匹配给的
  （SiteGoodsMapper.xml 的 publicWhere），改这里之前它筛不出东西。

  ⚠ 比较用 `>` 不是 `>=`：并列时保留靠前的那个，也就是字典序在前的，
  这样默认分类不会因为两个组子项数相同就随查询顺序摆动。
*/
const firstCat = computed(() => {
  const list = groups.value
  if (!list.length) return ''
  let best = list[0]!
  for (const g of list) {
    if (g.items.length > best.items.length) best = g
  }
  // 没子项的组，name 和 value 是同一个串；有子项的组只有 name 能用
  return best.name || best.value || ''
})

const currentPage = computed(() => Math.max(1, Number(route.query.page) || 1))
const currentKw = computed(() => (route.query.kw as string) || '')

/*
  当前分类。URL 上没写的时候默认第一个分类（跟参考站一致），**但搜索时例外**：

  买家敲关键词是想在整个目录里找，不是在「我们替他选中的那个分类」里找。
  不例外的话 /products?kw=xxx 只搜第一个分类，搜不到的东西比搜到的多，
  而页面上完全看不出来是被圈住了。

  URL 上显式写了 cat 就照它办——那是买家自己点的分类，在里面搜是他要的。
*/
const currentCat = computed(() => {
  const picked = (route.query.cat as string) || ''
  if (picked) return picked
  return currentKw.value ? '' : firstCat.value
})

// 输入框是本地状态，回车才写进 URL——每敲一个字就请求一次服务端太浪费
const kwInput = ref(currentKw.value)
watch(currentKw, v => { kwInput.value = v })

/** 侧栏展开哪个大组。跟着当前分类走，也允许手动折叠（openGroup = ''） */
const openGroup = ref(groupOf(groups.value, currentCat.value) || groups.value[0]?.name || '')
watch(currentCat, cat => {
  const g = groupOf(groups.value, cat)
  if (g) openGroup.value = g
})

function toggleGroup(name: string) {
  openGroup.value = openGroup.value === name ? '' : name
}

const { data } = await useAsyncData(
  () => `products-${site.lang}-${currentPage.value}-${currentCat.value}-${currentKw.value}`,
  () => useProducts({
    lang: site.lang,
    page: currentPage.value,
    size: PAGE_SIZE,
    cat: currentCat.value || undefined,
    kw: currentKw.value || undefined
  }),
  {
    watch: [currentPage, currentCat, currentKw, () => site.lang],
    default: () => ({ rows: [] as any[], total: 0, page: 1, size: PAGE_SIZE, currency: '' })
  }
)

/*
  「探索其他产品」那一段。

  参考站那边是 `products.filter(p => p.category !== 'Leg Systems')`——本地数组，
  想怎么筛怎么筛。这里的商品在服务端，而 /products 接口没有「排除某个分类」的参数，
  所以取一页不带分类的商品回来，在前台把当前分类的剔掉，取前四个。

  多取几条（8 条）是因为剔完可能不够四个：一整页恰好都是当前分类的情况是有的。
  刻意不为这一段加接口参数：它是个「随便看看」的入口，不值得让后端多一个查询分支。

  没选分类时（站点没配分类）不显示这一段——上面那个网格已经是全部商品了。
*/
const { data: otherRows } = await useAsyncData(
  () => `products-other-${site.lang}-${currentCat.value}`,
  async () => {
    if (!currentCat.value) return [] as any[]
    const res = await useProducts({ lang: site.lang, page: 1, size: 8 })
    // 用 isUnder 不是 `!==`：选中的是大组时，它下面每个子类的串都和组名不相等，
    // 拿 `!==` 筛的话「探索其他产品」里全是刚在上面看过的同组商品
    return (res.rows || []).filter(r => !isUnder(r.category, currentCat.value)).slice(0, 4)
  },
  { watch: [currentCat, () => site.lang], default: () => [] as any[] }
)
const others = computed(() => otherRows.value || [])

const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total || 0) / PAGE_SIZE)))

/** 主栏大标题：当前分类的末级名。没分类时说「全部产品」 */
const headTitle = computed(() => categoryLeaf(currentCat.value, site.categoryNames) || t.value.allTitle!)

/** 在当前 query 上改几个键，其余保持——切分类不该把搜索词丢掉 */
function linkTo(patch: Record<string, string | number | undefined>) {
  const query: Record<string, string> = {}
  for (const [k, v] of Object.entries({ ...route.query, ...patch })) {
    if (v !== undefined && v !== null && v !== '') query[k] = String(v)
  }
  return { path: localePath('/products'), query }
}

function applySearch() {
  navigateTo(linkTo({ kw: kwInput.value.trim() || undefined, page: undefined }))
}

useSiteSeo(site, {
  path: '/products',
  title: categoryLeaf(currentCat.value, site.categoryNames) || undefined,
  description: site.seoDescription || site.slogan
})

const COPY: Record<string, Record<string, string>> = {
  en: { products: 'Products', search: 'Search', searchHint: 'Search products...',
        empty: 'No products match your filter.', prev: 'Prev', next: 'Next',
        allTitle: 'All Products', other: 'Explore Other Products',
        lead: "Browse what we make. Prices shown are indicative — send a request and we reply with a full quotation including freight." },
  zh: { products: '产品', search: '搜索', searchHint: '搜索产品……',
        empty: '没有符合条件的产品。', prev: '上一页', next: '下一页',
        allTitle: '全部产品', other: '探索其他产品',
        lead: "这里是我们在做的产品。页面上的价格仅供参考，发来询价后我们会给出含运费的完整报价。" },
  ja: { products: '製品', search: '検索', searchHint: '製品を検索……',
        empty: '条件に一致する製品がありません。', prev: '前へ', next: '次へ',
        allTitle: 'すべての製品', other: 'その他の製品を見る',
        lead: "当社の製品一覧です。表示価格は目安です。お問い合わせいただければ送料込みの正式なお見積りをお送りします。" },
  es: { products: 'Productos', search: 'Buscar', searchHint: 'Buscar productos...',
        empty: 'No hay productos que coincidan.', prev: 'Anterior', next: 'Siguiente',
        allTitle: 'Todos los productos', other: 'Explore otros productos',
        lead: "Explore lo que fabricamos. Los precios son indicativos: envíenos una consulta y le responderemos con una cotización completa con flete." },
  pt: { products: 'Produtos', search: 'Buscar', searchHint: 'Buscar produtos...',
        empty: 'Nenhum produto corresponde ao filtro.', prev: 'Anterior', next: 'Próximo',
        allTitle: 'Todos os produtos', other: 'Explore outros produtos',
        lead: "Veja o que fabricamos. Os preços são indicativos — envie um pedido e responderemos com um orçamento completo, com frete." },
  it: { products: 'Prodotti', search: 'Cerca', searchHint: 'Cerca prodotti...',
        empty: 'Nessun prodotto corrisponde al filtro.', prev: 'Precedente', next: 'Successivo',
        allTitle: 'Tutti i prodotti', other: 'Scopri altri prodotti',
        lead: "Scopra cosa produciamo. I prezzi sono indicativi: ci invii una richiesta e le risponderemo con un preventivo completo, trasporto incluso." },
  ko: { products: '제품', search: '검색', searchHint: '제품 검색...',
        empty: '조건에 맞는 제품이 없습니다.', prev: '이전', next: '다음',
        allTitle: '전체 제품', other: '다른 제품 보기',
        lead: "당사가 생산하는 제품입니다. 표시 가격은 참고용이며, 문의를 보내주시면 운임을 포함한 정식 견적을 보내드립니다." },
  ru: { products: 'Товары', search: 'Поиск', searchHint: 'Поиск товаров...',
        empty: 'Нет товаров по вашему запросу.', prev: 'Назад', next: 'Вперёд',
        allTitle: 'Все товары', other: 'Другие товары',
        lead: "Смотрите, что мы производим. Цены ориентировочные — отправьте запрос, и мы пришлём полное предложение с учётом доставки." },
  fr: { products: 'Produits', search: 'Rechercher', searchHint: 'Rechercher un produit…',
        empty: 'Aucun produit ne correspond à votre filtre.', prev: 'Précédent', next: 'Suivant',
        allTitle: 'Tous les produits', other: 'Découvrir d’autres produits',
        lead: 'Parcourez notre catalogue. Les prix affichés sont indicatifs — envoyez une demande et nous répondons avec un devis complet, transport inclus.' }
}
const t = computed(() => COPY[site.lang] || COPY.en!)
</script>

<style scoped>
.products-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 56px;
  align-items: start;
}

/* ---------------- 侧栏 ---------------- */

.page-title {
  font-size: 3rem;
  margin-bottom: 24px;
}

.side-search {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 28px;
}
.ss-input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: #fff;
  font: inherit;
  font-size: 0.84rem;
  color: var(--text);
}
.ss-input:focus {
  outline: 2px solid rgba(11, 114, 171, .28);
  border-color: var(--primary-light);
}
.ss-btn {
  flex: none;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: #fff;
  color: var(--primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.ss-btn:hover { background: var(--primary); border-color: var(--primary); color: #fff; }

.cat-nav {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.cat-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cat-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background: none;
  border: none;
  text-align: left;
  /* 竖向内边距挂在里面那个链接上，不挂在这一行：
     这一行现在是「链接 + 折叠按钮」两个可点区域的容器，padding 放这儿的话
     点在行内边距上既不进大组页也不折叠，成了一条死区 */
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-soft);
  transition: color 0.15s ease;
}

.cat-heading:hover { color: var(--primary); }
.cat-heading--open { color: var(--text); }
.cat-heading--leaf.cat-heading--open { color: var(--primary); }
/* 大组本身被选中（在看整个子树）。和 --open（只是展开了）是两回事，
   所以另起一个类：一个大组可以展开但没被选中，也可以被选中却折叠着 */
.cat-heading--on { color: var(--primary); }

/* 组名那半边：占满剩余宽度，点哪儿都能进大组页 */
.cat-heading-link {
  flex: 1;
  min-width: 0;
  padding: 6px 0;
  color: inherit;
}

/* 箭头那半边。命中区域刻意做得比字形大一圈——0.75rem 的「▾」本身只有十来像素，
   在触屏上点不中，而点歪了会跳进大组页，不是「没反应」而是「跳走了」 */
.cat-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin: -6px -6px -6px 4px;
  padding: 0;
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
}
.cat-toggle:hover { color: var(--primary); }

.cat-caret {
  font-size: 0.75rem;
  transition: transform 0.25s ease;
}

.cat-heading--open .cat-caret { transform: rotate(180deg); }

.cat-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 0;
  margin-top: 0;
  overflow: hidden;
  opacity: 0;
  transition: max-height 0.25s ease, opacity 0.2s ease, margin-top 0.25s ease;
}

/* 折叠靠 max-height 做动画，所以要给一个够大的上限。
   400px 约等于 12 个子项——一个大组下面挂到这个数已经该拆组了 */
.cat-items--open {
  max-height: 400px;
  margin-top: 8px;
  opacity: 1;
}

.cat-item {
  padding: 7px 0 7px 16px;
  border-left: 2px solid var(--border);
  font-size: 0.92rem;
  color: var(--text-muted);
  transition: color 0.15s ease, border-color 0.15s ease;
}

.cat-item:hover { color: var(--primary); }

.cat-item--active {
  color: var(--primary);
  font-weight: 600;
  border-left-color: var(--primary);
  position: relative;
}

.cat-item--active::before {
  content: '';
  position: absolute;
  left: -6px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}

/* ---------------- 主栏 ---------------- */

.col-head h2 {
  font-size: 2rem;
  margin-bottom: 10px;
}

.col-head p {
  color: var(--text-muted);
  max-width: 640px;
  margin: 0 0 30px;
}

.col-head.second {
  margin-top: 56px;
  padding-top: 40px;
  border-top: 1px solid var(--border);
}

.results--fill { min-height: 40vh; }

.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 26px;
}

.empty { padding: 60px 0; text-align: center; }

.pager { display: flex; align-items: center; justify-content: center; gap: 18px; margin: 34px 0 0; }
.pg { padding: 8px 16px; border: 1px solid var(--border); border-radius: var(--radius-sm); }
.pg:hover { border-color: var(--primary); color: var(--primary); }
.pg-now { font-size: 14px; color: var(--text-muted); }

@media (max-width: 900px) {
  .products-layout {
    grid-template-columns: 1fr;
    gap: 32px;
  }
  .page-title { font-size: 2.2rem; margin-bottom: 18px; }
  .card-grid { grid-template-columns: 1fr; }
}
</style>

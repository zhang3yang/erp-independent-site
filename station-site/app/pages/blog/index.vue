<!--
  博客列表。

  ## 这一页为什么存在

  这个站的 SEO 价值原来全压在商品详情页上（见 server/routes/sitemap.xml.ts 的注释），
  而商品页能覆盖的关键词就是商品名——覆盖不到「how to choose silicone tubing」
  这类真正带来询盘的长尾词。博客补的是这一块。

  ## 筛选状态放 URL，和商品列表一致

  分类、搜索词、页码全部走 query string。三个理由（同 products/index.vue）：
    1. SSR 时服务端就能按条件取数，爬虫和用户拿到同一份已经筛好的 HTML；
    2. 用户能把「第 2 页的 Manufacturing」这个链接直接发给同事；
    3. 浏览器前进后退符合预期。

  ## ⚠ 这一页的文章是按语言过滤的，没有默认语言回退

  某篇文章没有当前语言的译文，它在这一页就是不存在的——不是显示一个英文标题。
  和商品列表相反，理由见后端 SitePostMapper 的类注释。所以 /ja/blog 和 /blog
  上的文章篇数**可以不一样**，那是对的，不是 bug。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <section class="blog-page section">
      <div class="wrap">
        <header class="blog-head">
          <h1 class="blog-title">{{ t.blog }}</h1>
          <p class="blog-lead">{{ t.lead }}</p>
        </header>

        <!-- 搜索 + 分类。分类是真链接（NuxtLink），不是 @click 改本地 state：
             分类页是从谷歌落地的入口之一，每个分类得有自己的 URL 才收得进索引 -->
        <div class="blog-tools">
          <form class="bt-search" @submit.prevent="applySearch">
            <input v-model="kwInput" class="bt-input" :placeholder="t.searchHint"
                   :aria-label="t.search" maxlength="60">
            <button class="bt-btn" type="submit">{{ t.search }}</button>
          </form>

          <nav v-if="categories.length" class="bt-cats">
            <NuxtLink :to="linkTo({ cat: undefined, page: undefined })"
                      class="bt-cat" :class="{ on: !currentCat }">{{ t.all }}</NuxtLink>
            <NuxtLink v-for="c in categories" :key="c"
                      :to="linkTo({ cat: c, page: undefined })"
                      class="bt-cat" :class="{ on: c === currentCat }">{{ c }}</NuxtLink>
          </nav>
        </div>

        <p v-if="!rows.length" class="blog-empty">{{ t.empty }}</p>

        <div v-else class="blog-grid">
          <article v-for="post in rows" :key="post.slug" class="bc">
            <NuxtLink :to="localePath('/blog/' + post.slug)" class="bc-cover">
              <!-- 过一遍 isUsableImageUrl：填了相对路径时不渲染 img，
                   退到一个纯色占位块，而不是挂一个裂图标（同顶栏 logo 的处理） -->
              <img v-if="isUsableImageUrl(post.coverImg)" :src="post.coverImg"
                   :alt="post.title" loading="lazy">
              <span v-else class="bc-nocover" aria-hidden="true"></span>
            </NuxtLink>

            <div class="bc-body">
              <div class="bc-meta">
                <span v-if="post.category" class="bc-cat">{{ post.category }}</span>
                <!-- ⚠ 日期用 <time datetime>：那个属性是机器可读的格式，
                     而显示出来的是按访客语言本地化的。两者不能只有一个 -->
                <time v-if="post.publishTime" :datetime="isoDate(post.publishTime)"
                      class="bc-date">{{ humanDate(post.publishTime) }}</time>
              </div>

              <h2 class="bc-title">
                <NuxtLink :to="localePath('/blog/' + post.slug)">{{ post.title }}</NuxtLink>
              </h2>

              <p v-if="post.summary" class="bc-summary">{{ post.summary }}</p>

              <NuxtLink :to="localePath('/blog/' + post.slug)" class="bc-more">
                {{ t.readMore }} →
              </NuxtLink>
            </div>
          </article>
        </div>

        <nav v-if="totalPages > 1" class="blog-pager">
          <NuxtLink v-if="page > 1" :to="linkTo({ page: page - 1 })" class="bp-btn">
            {{ t.prev }}
          </NuxtLink>
          <span class="bp-at">{{ page }} / {{ totalPages }}</span>
          <NuxtLink v-if="page < totalPages" :to="linkTo({ page: page + 1 })" class="bp-btn">
            {{ t.next }}
          </NuxtLink>
        </nav>
      </div>
    </section>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { isUsableImageUrl } from '~/lib/shop'

const route = useRoute()
const site = await useSiteInfo()
const localePath = useLocalePath()

const PAGE_SIZE = 9

const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const currentCat = computed(() => (route.query.cat as string) || '')
const kw = computed(() => (route.query.kw as string) || '')

const kwInput = ref(kw.value)
// URL 变了（比如按了后退）要把输入框同步回去，否则框里留着上一次的词、
// 而列表已经是另一份结果了
watch(kw, v => { kwInput.value = v })

const { data } = await useAsyncData(
  () => `posts-${site.lang}-${currentCat.value}-${kw.value}-${page.value}`,
  () => usePosts({
    lang: site.lang,
    cat: currentCat.value || undefined,
    kw: kw.value || undefined,
    page: page.value,
    size: PAGE_SIZE
  }),
  { watch: [page, currentCat, kw, () => site.lang] }
)

const rows = computed(() => data.value?.rows || [])
// 分类跟着列表接口一起回来，不单独发请求
const categories = computed(() => data.value?.categories || [])
const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total || 0) / PAGE_SIZE)))

/** 在当前 query 上改几个键，其余保持——切分类不该把搜索词丢掉（同商品列表） */
function linkTo(patch: Record<string, string | number | undefined>) {
  const query: Record<string, string> = {}
  for (const [k, v] of Object.entries({ ...route.query, ...patch })) {
    if (v !== undefined && v !== null && v !== '') query[k] = String(v)
  }
  return { path: localePath('/blog'), query }
}

function applySearch() {
  navigateTo(linkTo({ kw: kwInput.value.trim() || undefined, page: undefined }))
}

/*
 * 日期。
 *
 * 后端给的是 `yyyy-MM-dd HH:mm:ss`（本地时间，无时区）。
 *
 * ⚠ 直接 new Date('2026-09-08 10:00:00') 在 Safari 上是 Invalid Date——
 *   那个格式不是 ISO 8601。必须把空格换成 T。这个坑只在 iPhone 上出现，
 *   而这个站的访客里 iPhone 占很大一块。
 */
function toDate(s: string) {
  return new Date(s.replace(' ', 'T'))
}

/** <time datetime> 用的机器可读格式，只要日期 */
function isoDate(s: string) {
  const d = toDate(s)
  return isNaN(d.getTime()) ? '' : d.toISOString().slice(0, 10)
}

/**
 * 显示给人看的日期，按访客语言本地化。
 *
 * ⚠ 用 site.lang 而不是浏览器语言：这一页的语言由 URL 决定（SEO 的前提是
 *   一个 URL 永远对应同一份内容），日期格式跟着页面语言走才一致。
 *   服务端和客户端都用同一个值，所以不会 hydration mismatch。
 */
function humanDate(s: string) {
  const d = toDate(s)
  if (isNaN(d.getTime())) return ''
  try {
    return d.toLocaleDateString(site.lang, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    // 语言代码不被 Intl 认识时（不该发生，白名单里都是标准代码）退到 ISO
    return d.toISOString().slice(0, 10)
  }
}

const t = computed(() => COPY[site.lang] || COPY.en!)

/*
 * ⚠ useSiteSeo 必须放在 COPY 声明**之后**，见这个文件末尾。
 *
 * `t` 是 computed，本身是惰性的——但这里 `t.value.blog` 在 setup 阶段就把它求值了，
 * 而 `const COPY` 声明在下面。`const` 有暂时性死区（TDZ），提前访问不是 undefined，
 * 是直接抛：
 *
 *     Cannot access 'COPY' before initialization
 *
 * 2026-09-09 这个页面就是这么在生产上 500 的。开发时不一定撞得到——热更新的
 * 模块求值顺序和产物不同，所以 `nuxt build` 过了不代表页面能开。
 */

/*
 * 界面文案。⚠ 这张表的语言必须和后端 SiteInfo.SUPPORTED_LANGS 对齐
 * （en/zh/ja/es/pt/ru/it/ko），少一种就是「切过去整页退回英文」。
 */
const COPY: Record<string, Record<string, string>> = {
  en: { blog: 'Blog', search: 'Search', searchHint: 'Search articles...', all: 'All',
        empty: 'No articles yet.', prev: 'Prev', next: 'Next', readMore: 'Read more',
        lead: 'Guides, material notes and manufacturing know-how from our engineering team.' },
  zh: { blog: '博客', search: '搜索', searchHint: '搜索文章……', all: '全部',
        empty: '还没有文章。', prev: '上一页', next: '下一页', readMore: '阅读全文',
        lead: '来自我们工程团队的选型指南、材料说明和生产工艺笔记。' },
  ja: { blog: 'ブログ', search: '検索', searchHint: '記事を検索……', all: 'すべて',
        empty: 'まだ記事がありません。', prev: '前へ', next: '次へ', readMore: '続きを読む',
        lead: '当社エンジニアリングチームによる選定ガイド、材料メモ、製造ノウハウ。' },
  es: { blog: 'Blog', search: 'Buscar', searchHint: 'Buscar artículos...', all: 'Todos',
        empty: 'Aún no hay artículos.', prev: 'Anterior', next: 'Siguiente', readMore: 'Leer más',
        lead: 'Guías, notas sobre materiales y conocimientos de fabricación de nuestro equipo de ingeniería.' },
  pt: { blog: 'Blog', search: 'Buscar', searchHint: 'Buscar artigos...', all: 'Todos',
        empty: 'Ainda não há artigos.', prev: 'Anterior', next: 'Próximo', readMore: 'Leia mais',
        lead: 'Guias, notas sobre materiais e conhecimento de fabricação da nossa equipe de engenharia.' },
  ru: { blog: 'Блог', search: 'Поиск', searchHint: 'Поиск статей...', all: 'Все',
        empty: 'Пока нет статей.', prev: 'Назад', next: 'Далее', readMore: 'Читать далее',
        lead: 'Руководства, заметки о материалах и производственный опыт нашей инженерной команды.' },
  it: { blog: 'Blog', search: 'Cerca', searchHint: 'Cerca articoli...', all: 'Tutti',
        empty: 'Nessun articolo disponibile.', prev: 'Precedente', next: 'Successivo', readMore: 'Leggi tutto',
        lead: 'Guide, note sui materiali e know-how produttivo dal nostro team di ingegneria.' },
  ko: { blog: '블로그', search: '검색', searchHint: '기사 검색...', all: '전체',
        empty: '아직 기사가 없습니다.', prev: '이전', next: '다음', readMore: '자세히 보기',
        lead: '엔지니어링 팀이 제공하는 선정 가이드, 소재 노트, 제조 노하우.' },
  fr: { blog: 'Blog', search: 'Rechercher', searchHint: 'Rechercher un article…', all: 'Tout',
        empty: 'Aucun article pour le moment.', prev: 'Précédent', next: 'Suivant', readMore: 'Lire la suite',
        lead: 'Guides, notes techniques et savoir-faire de notre équipe.' }
}

useSiteSeo(site, {
  path: '/blog',
  title: currentCat.value || t.value.blog,
  description: t.value.lead
})
</script>

<style scoped>
.blog-head {
  max-width: 720px;
  margin-bottom: 32px;
}
.blog-title {
  font-family: var(--font-display);
  font-size: clamp(28px, 4vw, 40px);
  margin: 0 0 12px;
}
.blog-lead {
  margin: 0;
  color: var(--muted, #5b6472);
  line-height: 1.7;
}

.blog-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}
.bt-search {
  display: flex;
  gap: 8px;
}
.bt-input {
  min-width: 220px;
  padding: 8px 12px;
  border: 1px solid var(--line, #dfe3e8);
  border-radius: 4px;
  font: inherit;
}
.bt-btn {
  padding: 8px 16px;
  border: 1px solid var(--line, #dfe3e8);
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font: inherit;
}
.bt-cats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.bt-cat {
  padding: 6px 12px;
  border-radius: 100px;
  border: 1px solid var(--line, #dfe3e8);
  font-size: 13px;
  text-decoration: none;
  color: inherit;
}
.bt-cat.on {
  background: var(--accent, #1f2d3d);
  border-color: var(--accent, #1f2d3d);
  color: #fff;
}

.blog-empty {
  padding: 48px 0;
  color: var(--muted, #5b6472);
}

/* auto-fill + minmax：一篇文章时不会被拉成整行宽，多篇时自动换行 */
.blog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 28px;
}
.bc {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line, #dfe3e8);
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}
.bc-cover {
  display: block;
  aspect-ratio: 16 / 9;
  background: var(--fill, #f2f4f7);
}
.bc-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.bc-nocover {
  display: block;
  width: 100%;
  height: 100%;
}
.bc-body {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}
.bc-meta {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 12px;
  color: var(--muted, #5b6472);
}
.bc-cat {
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.bc-title {
  font-family: var(--font-display);
  font-size: 19px;
  line-height: 1.4;
  margin: 0;
}
.bc-title a {
  color: inherit;
  text-decoration: none;
}
.bc-title a:hover {
  text-decoration: underline;
}
.bc-summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--muted, #5b6472);
}
.bc-more {
  margin-top: auto;
  font-size: 13px;
  text-decoration: none;
}

.blog-pager {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: center;
  margin-top: 40px;
}
.bp-btn {
  padding: 8px 16px;
  border: 1px solid var(--line, #dfe3e8);
  border-radius: 4px;
  text-decoration: none;
  color: inherit;
}
.bp-at {
  font-size: 13px;
  color: var(--muted, #5b6472);
}
</style>

<!--
  博客文章详情。

  ## 地址是 slug，不是编码

  `/blog/how-to-choose-silicone-tubing`，不是 `/blog/BLOG0000000001`。
  文章的内部编码根本不下发到前台。理由：博客整个存在的理由就是自然搜索，
  而 URL 里的关键词是排名因素之一。见后端 SitePost 的类注释。

  ## ⚠ 正文是后端过滤过的 HTML

  `content` 在**写入侧**过了 RichTextCleaner 的白名单（后端 SitePostService.savePost），
  script / iframe / style / 事件属性 / javascript: 协议全部剥掉了。
  所以这里用 v-html 渲染是安全的——**前提是那一层过滤还在**。
  和商品详情页同一条规矩（products/[code].vue 的文件头也写着这一条）。

  改后端那个过滤之前，先想起这一页。

  ## ⚠ hreflang 用文章自己的语言列表

  站点开了 8 种语言不等于这篇文章有 8 个版本——文章是一种语言一种语言攒出来的，
  没有译文的那个语言前缀下这篇文章是 404。所以 useSiteSeo 传的是 `post.langs`
  而不是 site.langs。列一个没有译文的语言等于向 Google 承诺一个 404，
  而 hreflang 里有一个坏链接，Google 会忽略**整组**声明。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <article class="post section">
      <div class="wrap post-wrap">
        <nav class="post-crumb">
          <NuxtLink :to="localePath('/blog')">{{ t.blog }}</NuxtLink>
          <span v-if="post.category"> / {{ post.category }}</span>
        </nav>

        <h1 class="post-title">{{ post.title }}</h1>

        <div class="post-meta">
          <!-- ⚠ <time datetime> 是机器可读的，显示出来的是本地化的。两者不能只留一个 -->
          <time v-if="post.publishTime" :datetime="isoDate(post.publishTime)">
            {{ humanDate(post.publishTime) }}
          </time>
          <!-- 作者留空就整行不显示。⚠ 不要兜底成公司名——那看起来像是没填 -->
          <span v-if="post.author" class="post-author">{{ post.author }}</span>
        </div>

        <img v-if="isUsableImageUrl(post.coverImg)" :src="post.coverImg" :alt="post.title"
             class="post-cover">

        <!-- eslint-disable-next-line vue/no-v-html -- 见文件头注释：写入侧已过白名单 -->
        <div class="post-body" v-html="post.content"></div>

        <footer class="post-foot">
          <NuxtLink :to="localePath('/blog')" class="post-back">← {{ t.backToBlog }}</NuxtLink>
        </footer>
      </div>

      <!--
        文章末尾接一个询价入口。

        博客的目的是把自然流量变成询盘——一篇文章读完没有下一步，那个流量就白来了。
        用现成的 InquiryForm，不新做一个：多一个提交入口就多一处限流和蜜罐要维护，
        而那两样都在这个组件里（见 InquiryForm.vue）。
      -->
      <div class="post-cta">
        <div class="wrap">
          <h2 class="cta-title">{{ t.ctaTitle }}</h2>
          <p class="cta-lead">{{ t.ctaLead }}</p>
          <InquiryForm :lang="site.lang" :fallback-email="site.contact?.email" />
        </div>
      </div>
    </article>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { isUsableImageUrl } from '~/lib/shop'

const route = useRoute()
const site = await useSiteInfo()
const localePath = useLocalePath()

const slug = computed(() => String(route.params.slug || ''))

const { data, error } = await useAsyncData(
  () => `post-${site.lang}-${slug.value}`,
  () => usePostDetail(slug.value, site.lang),
  { watch: [slug, () => site.lang] }
)

/*
 * 取不到 = 这个地址在这个语言下不存在。**404，不是 500。**
 *
 * ⚠ 三种情况都走这里，而且不区分（后端也回同一句话）：
 *   · slug 不存在；
 *   · 文章还是草稿 / 已下架；
 *   · 这个语言没有译文。
 *
 * 第三种是这个模块特有的，也是最容易被误当成 bug 的：/blog/xxx 打得开、
 * /ja/blog/xxx 404，这是**对的**——那篇文章的日语版还没翻。
 *
 * ⚠ 必须 fatal: true：非 fatal 的 createError 在 SSR 时只是把错误挂在页面上，
 *   HTTP 状态码仍然是 200，而 Google 会把它当成一个正常页面收录进去
 *   （软 404）。那比返回 404 糟得多。
 */
if (error.value || !data.value) {
  throw createError({
    statusCode: 404,
    statusMessage: 'Article not found',
    message: `这个语言下没有这篇文章：${slug.value}`,
    fatal: true
  })
}

const post = data.value

/*
 * 日期。后端给的是 `yyyy-MM-dd HH:mm:ss`（本地时间，无时区）。
 *
 * ⚠ 直接 new Date('2026-09-09 10:00:00') 在 Safari 上是 Invalid Date——
 *   那个格式不是 ISO 8601，必须把空格换成 T。这个坑只在 iPhone 上出现，
 *   而这个站的访客里 iPhone 占很大一块。同 blog/index.vue。
 */
function toDate(s: string) {
  return new Date(s.replace(' ', 'T'))
}

function isoDate(s: string) {
  const d = toDate(s)
  return isNaN(d.getTime()) ? '' : d.toISOString().slice(0, 10)
}

function humanDate(s: string) {
  const d = toDate(s)
  if (isNaN(d.getTime())) return ''
  try {
    return d.toLocaleDateString(site.lang, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    return d.toISOString().slice(0, 10)
  }
}

/** og:image 和 article:published_time 要的是 ISO 8601 带时区的形式 */
const publishedIso = computed(() => {
  if (!post.publishTime) return null
  const d = toDate(post.publishTime)
  return isNaN(d.getTime()) ? null : d.toISOString()
})

const t = computed(() => COPY[site.lang] || COPY.en!)

/*
 * ⚠ useSiteSeo 挪到 COPY 声明之后了，见这个文件末尾。
 *
 * 这一页的 useSiteSeo 本身不读 t，但和 index.vue 保持同一个次序，
 * 免得下次有人往里加一行 `title: t.value.xxx` 又踩同一个坑：
 * `const` 有暂时性死区，setup 阶段提前访问直接抛
 * `Cannot access 'COPY' before initialization`，而 `nuxt build` 是过的。
 */

/*
 * 界面文案。⚠ 语言必须和后端 SiteInfo.SUPPORTED_LANGS 对齐
 * （en/zh/ja/es/pt/ru/it/ko），少一种就是「切过去整页退回英文」。
 */
const COPY: Record<string, Record<string, string>> = {
  en: { blog: 'Blog', backToBlog: 'Back to all articles',
        ctaTitle: 'Need this for your project?',
        ctaLead: 'Tell us the spec and quantity — we reply with a full quotation including freight.' },
  zh: { blog: '博客', backToBlog: '返回文章列表',
        ctaTitle: '项目上需要这个？',
        ctaLead: '把规格和数量告诉我们，我们会回一份含运费的完整报价。' },
  ja: { blog: 'ブログ', backToBlog: '記事一覧に戻る',
        ctaTitle: 'プロジェクトでご検討中ですか？',
        ctaLead: '仕様と数量をお知らせください。送料込みの正式なお見積りをお送りします。' },
  es: { blog: 'Blog', backToBlog: 'Volver a todos los artículos',
        ctaTitle: '¿Lo necesita para su proyecto?',
        ctaLead: 'Indíquenos la especificación y la cantidad: le enviaremos una cotización completa con flete.' },
  pt: { blog: 'Blog', backToBlog: 'Voltar para todos os artigos',
        ctaTitle: 'Precisa disso para o seu projeto?',
        ctaLead: 'Informe a especificação e a quantidade — enviamos uma cotação completa com frete.' },
  ru: { blog: 'Блог', backToBlog: 'Ко всем статьям',
        ctaTitle: 'Нужно для вашего проекта?',
        ctaLead: 'Сообщите спецификацию и количество — вышлем полное предложение с доставкой.' },
  it: { blog: 'Blog', backToBlog: 'Torna a tutti gli articoli',
        ctaTitle: 'Ti serve per il tuo progetto?',
        ctaLead: 'Indicaci specifica e quantità: ti inviamo un preventivo completo con trasporto.' },
  ko: { blog: '블로그', backToBlog: '전체 기사로 돌아가기',
        ctaTitle: '프로젝트에 필요하신가요?',
        ctaLead: '규격과 수량을 알려주시면 운송비가 포함된 정식 견적을 보내드립니다.' },
  fr: { blog: 'Blog', backToBlog: 'Retour à tous les articles',
        ctaTitle: 'Besoin de cela pour votre projet ?',
        ctaLead: 'Indiquez-nous la spécification et la quantité — nous répondons avec un devis complet, transport inclus.' }
}

useSiteSeo(site, {
  path: `/blog/${slug.value}`,
  title: post.title,
  // 摘要没填时从正文剥标签截一段兜底。⚠ 和商品详情页同一个做法——
  // 截出来的句子通常是断的，所以 SEO 体检里「没填摘要」是一条「中」级问题
  description: post.summary
    || (post.content || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 160),
  image: post.coverImg || undefined,
  type: 'article',
  publishedTime: publishedIso.value,
  // ⚠⚠ 这一行是这一页 SEO 上最要紧的一处，见文件头注释
  langs: post.langs
})
</script>

<style scoped>
.post-wrap {
  max-width: 760px;
}
.post-crumb {
  font-size: 13px;
  color: var(--muted, #5b6472);
  margin-bottom: 16px;
}
.post-crumb a {
  color: inherit;
}
.post-title {
  font-family: var(--font-display);
  font-size: clamp(26px, 4.2vw, 40px);
  line-height: 1.25;
  margin: 0 0 14px;
}
.post-meta {
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 13px;
  color: var(--muted, #5b6472);
  margin-bottom: 28px;
}
.post-cover {
  width: 100%;
  border-radius: 6px;
  margin-bottom: 32px;
}

/*
  正文。

  ⚠ 这些选择器要盖到 v-html 出来的内容上，而 scoped 样式**不会**给 v-html 的子节点
    加 data-v 属性——所以必须用 :deep()。不用的话样式一条都不生效，
    而页面看起来只是「排版很丑」，很容易以为是没写样式。

  白名单允许的标签就那些（见后端 RichTextCleaner），这里逐个给上排版。
*/
.post-body {
  font-size: 16px;
  line-height: 1.8;
}
.post-body :deep(p) {
  margin: 0 0 1.2em;
}
.post-body :deep(h2),
.post-body :deep(h3),
.post-body :deep(h4) {
  font-family: var(--font-display);
  line-height: 1.35;
  margin: 1.8em 0 0.7em;
}
.post-body :deep(h2) { font-size: 24px; }
.post-body :deep(h3) { font-size: 20px; }
.post-body :deep(h4) { font-size: 17px; }
.post-body :deep(ul),
.post-body :deep(ol) {
  margin: 0 0 1.2em;
  padding-left: 1.6em;
}
.post-body :deep(li) {
  margin-bottom: 0.4em;
}
.post-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 1.2em 0;
}
.post-body :deep(a) {
  color: var(--accent, #1f2d3d);
}
.post-body :deep(blockquote) {
  margin: 1.4em 0;
  padding: 4px 0 4px 18px;
  border-left: 3px solid var(--line, #dfe3e8);
  color: var(--muted, #5b6472);
}
/* 表格可能比正文宽。包一层能横向滚的容器管不到 v-html 里面，
   所以直接让它自己能滚——不加的话整页会出现横向滚动条 */
.post-body :deep(table) {
  display: block;
  overflow-x: auto;
  max-width: 100%;
  border-collapse: collapse;
  margin: 1.4em 0;
}
.post-body :deep(th),
.post-body :deep(td) {
  border: 1px solid var(--line, #dfe3e8);
  padding: 8px 12px;
  text-align: left;
}

.post-foot {
  margin-top: 48px;
  padding-top: 24px;
  border-top: 1px solid var(--line, #dfe3e8);
}
.post-back {
  font-size: 14px;
  text-decoration: none;
}

.post-cta {
  margin-top: 64px;
  padding: 48px 0;
  background: var(--fill, #f2f4f7);
}
.cta-title {
  font-family: var(--font-display);
  font-size: 26px;
  margin: 0 0 10px;
}
.cta-lead {
  margin: 0 0 24px;
  color: var(--muted, #5b6472);
}
</style>

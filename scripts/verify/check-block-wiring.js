/*
 * 首页区块的「四层接线」自检：前台模板 / 前台 script / 管理台表单 / 后端拼接，
 * 四处说的是不是同一件事。
 *
 * 为什么值得单独有一项：这四处任意两处对不上，**构建都不会报错**，
 * 症状全是安静的——
 *
 *   · 模板里写了 `t.faqLead`，而某个语言的 COPY 表里没有这个键
 *       → 那个语言的页面上显示 undefined，别的语言一切正常；
 *   · BLOCK_META 的键序和 DEFAULT_ORDER 不一致
 *       → 管理台里排好的顺序和首页上看到的顺序不一样（老站点的 order 里
 *         没有新区块，两边各自「补在最后」，补的顺序却不同）；
 *   · 管理台加了一块、后端 compose 忘了拼
 *       → 条目在、文字不在，页面上一排空卡片，而管理台里字都填着。
 *
 * 2026-09-21 新增承诺横条 / 顾客评价 / 合作方 / 常见问题四块时写的，
 * 那一次要同时改的地方有九处，漏一处就是上面某一种。
 */
const fs = require('fs')
const src = fs.readFileSync('station-site/app/pages/index.vue', 'utf8')

const tplStart = src.indexOf('<template>')
const tplEnd = src.indexOf('</template>\n\n<script') >= 0
  ? src.indexOf('</template>\r\n\r\n<script')
  : src.lastIndexOf('</template>')
const scriptStart = src.indexOf('<script setup')
const tpl = src.slice(tplStart, scriptStart)
const script = src.slice(scriptStart)

let bad = 0
const fail = m => { console.log('  ✗ ' + m); bad++ }

/* ---- 1. t.xxx 每个键在 COPY 的九种语言里都要有 ---- */
const used = [...new Set([...tpl.matchAll(/\bt\.([A-Za-z0-9_]+)/g)].map(m => m[1]))].sort()
const LANGS = ['en', 'zh', 'ja', 'es', 'pt', 'it', 'ko', 'ru', 'fr']
for (const lang of LANGS) {
  const at = script.indexOf('\n  ' + lang + ': {')
  if (at < 0) { fail(`COPY 里没有 ${lang}`); continue }
  // 该语言块到下一个语言块（或对象结尾）之间
  let end = script.length
  for (const other of LANGS) {
    const p = script.indexOf('\n  ' + other + ': {', at + 1)
    if (p > at && p < end) end = p
  }
  const closing = script.indexOf('\n}', at)
  if (closing > at && closing < end) end = closing
  const block = script.slice(at, end)
  const missing = used.filter(k => !new RegExp('\\b' + k + ':').test(block))
  if (missing.length) fail(`COPY.${lang} 缺键: ${missing.join(', ')}`)
}
console.log(`  模板用到 ${used.length} 个文案键，九种语言逐个核对完毕`)

/* ---- 2. 模板里调用的函数都要在 script 里定义 ---- */
const called = [...new Set([...tpl.matchAll(/[\s"(:]([a-z][A-Za-z0-9_]*)\(/g)].map(m => m[1]))]
const BUILTIN = new Set(['String', 'Number', 'Boolean', 'Array', 'Object', 'JSON', 'Math', 'if', 'for'])
for (const fn of called) {
  if (BUILTIN.has(fn)) continue
  // import 进来的也算定义（如 categoryLeaf 来自 ~/lib/categories）
  const imported = new RegExp('import\\s*\\{[^}]*\\b' + fn + '\\b[^}]*\\}').test(script)
  const defined = imported || new RegExp('(function\\s+' + fn + '\\b|const\\s+' + fn + '\\s*=)').test(script)
  if (!defined) fail(`模板里调了 ${fn}()，script 里没定义`)
}
console.log(`  模板调用 ${called.length} 个函数，全部有定义`)

/* ---- 3. 新四块：区块键要同时出现在 DEFAULT_ORDER 和模板里 ---- */
const orderMatch = script.match(/const DEFAULT_ORDER = \[([\s\S]*?)\]/)
if (!orderMatch) fail('没找到 DEFAULT_ORDER')
const order = [...orderMatch[1].matchAll(/'([a-z]+)'/g)].map(m => m[1])
console.log('  DEFAULT_ORDER:', order.join(' → '))
for (const key of ['trust', 'reviews', 'partners', 'faq']) {
  if (!order.includes(key)) fail(`DEFAULT_ORDER 里没有 ${key}`)
  if (!tpl.includes(`key === '${key}'`)) fail(`模板里没有 ${key} 那一块`)
  if (!new RegExp(`${key}Variant`).test(script)) fail(`${key} 没有版式 computed`)
}

/* ---- 4. 管理台那三张表的键必须和前台对齐 ---- */
const deco = fs.readFileSync('station-shared/DecorateForm.vue', 'utf8')
// 管理台多语言之后 BLOCK_META 变成了 computed(() => ({ ... }))（文字要跟着界面语言切），两种写法都认
const metaMatch = deco.match(/const BLOCK_META = \{([\s\S]*?)\n\}/) ||
  deco.match(/const BLOCK_META = computed\(\(\) => \(\{([\s\S]*?)\n\}\)\)/)
const metaKeys = [...metaMatch[1].matchAll(/\n  ([a-z]+):/g)].map(m => m[1])
console.log('  BLOCK_META  :', metaKeys.join(' → '))
if (metaKeys.join(',') !== order.join(',')) {
  fail(`BLOCK_META 的键序和 DEFAULT_ORDER 不一致（管理台排的顺序会和首页对不上）`)
}
for (const key of ['trust', 'reviews', 'partners', 'faq']) {
  if (!new RegExp(`\\n  ${key}: \\[\\[`).test(deco)) fail(`BLOCK_VARIANTS 里没有 ${key}`)
  if (!deco.includes(`v-show="sel === '${key}'"`)) fail(`管理台没有 ${key} 的编辑面板`)
}

/* ---- 5. 后端 compose 要拼这四块 ---- */
const java = fs.readFileSync('station-console/src/main/java/com/ruoyi/station/console/service/SiteOpenService.java', 'utf8')
for (const [parent, bag] of [['trust', 'trustText'], ['reviews', 'reviewText'], ['partners', 'partnerText'], ['faq', 'faqText']]) {
  if (!java.includes(`joinNested(merged, "${parent}", "items", "${bag}");`)) {
    fail(`后端 compose 没拼 ${parent}（文案袋子 ${bag} 会原样漏给前台，而条目上没有文字）`)
  }
}
console.log('  后端 compose：四块的拼接都在')
/* ---- 6. Banner 背景视频：前台模板 / 前台 script / 管理台三处要一起在 ----

   和上面四块同一个毛病：三处少任意一处，构建都不报错，症状全是安静的——

     · 管理台有格子、前台模板没 <video>      → 商家填了视频，首页纹丝不动；
     · 前台有 <video>、heroSlides 不读 video → 同上，而且更难查（模板是对的）；
     · 两边都有、管理台保存时没写进 slides   → 填完一保存就没了。

   后端不用查：compose 的 joinRows 是整条拷贝，slides 上多一个键自然就过去了。 */
if (!/<video\s/.test(tpl)) fail('前台模板里没有 <video>（Banner 视频渲染不出来）')
if (!/sld\.video/.test(tpl)) fail('前台模板没读 sld.video')
if (!/video: isUsableImageUrl\(s\.video\)/.test(script)) fail('heroSlides 没把 video 读出来')
if (!/s\.image \|\| s\.video/.test(script)) fail('heroBgCount 没把视频算成「有背景」（只配视频会掉进 hero--plain）')
if (!deco.includes('<video-field v-model="shared.heroSlides[slideIndex].video"')) {
  fail('管理台 Banner 那块没有视频格子')
}
if (!deco.includes('video: s.video')) fail('管理台保存时没把 video 写进 slides')
console.log('  Banner 背景视频：前台模板 / 前台 script / 管理台三处都在')

console.log(bad ? `\n✗ ${bad} 项有问题` : '\n全部通过')
process.exit(bad ? 1 : 0)

/*
 * 首页装修表单的「载入 → 保存」往返校验。
 *
 * 这一页的分工是：**结构只存一份，文案按语言各存一份**。这个脚本验的就是那条线：
 * 老数据载进来会不会把结构捞到共享层、各语言的文案会不会按条对上、
 * 存出去那份 JSON 是不是新结构、以及切语言时结构会不会跟着变（不该变）。
 *
 *   node scripts/verify/decorate-roundtrip.mjs
 *
 * ## 为什么不开浏览器
 *
 * 要验的是 DecorateForm.vue 里那几段纯逻辑（fillShared / fillRows / textBag /
 * composePayload），它们不碰 DOM。所以这里把 <script setup> 抠出来、把 vue 之外的
 * 依赖换成空壳，在 Node 里直接跑——几秒钟，能进 CI，也不会因为装不上 Chrome 就跑不了。
 *
 * ⚠ **模板里的绑定这个脚本验不到。**「格子绑错字段」那一类（2026-09-11 撞到过两次）
 *   只能靠点开页面，或者靠对着 shared / emptyLangForm 数一遍模板里的 form.x / shared.x。
 *
 * ⚠ 它 import 的是**真的那个 .vue 文件**，不是拷贝。那一段逻辑改了这里就跟着改，
 *   不会像 MergeCheck 那样需要额外防漂移。
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { createRequire } from 'node:module'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')
// vue 装在管理台那个工程里（这个仓库根目录没有 node_modules）
const require = createRequire(resolve(root, 'station-console-ui/package.json'))
const vue = require('vue')

/* ---------------- 把 <script setup> 抠出来跑 ---------------- */

const sfc = readFileSync(resolve(root, 'station-shared/DecorateForm.vue'), 'utf8')
const body = sfc.slice(sfc.indexOf('>', sfc.indexOf('<script setup')) + 1, sfc.lastIndexOf('</script>'))

/**
 * 干掉 import，换成外面传进来的几个变量；defineProps 换成一个普通函数。
 * 其余一个字都不改——跑的就是真代码。
 */
const source = body
  .replace(/^import .*$/gm, '')
  .replace(/\bdefineProps\(/g, '__defineProps(')

const stub = () => {}
const ElMessage = Object.assign(msg => messages.push(String(msg?.message || msg)), {
  success: m => messages.push('success:' + m),
  warning: m => messages.push('warning:' + m),
  error: m => messages.push('error:' + m)
})
const messages = []

const saved = []
const site = vue.ref({})

const factory = new Function(
  'ref', 'reactive', 'computed', 'inject', 'watch', 'onMounted', 'onBeforeUnmount', 'h',
  'onBeforeRouteLeave', 'ElMessage', 'ElMessageBox', 'ItemRows', 'ImageField', 'VideoField',
  '__defineProps',
  `${source}\n;return { shared, byLang, lang, reload, save, composePayload, buildReach, passthrough, addRow, removeRow, blockBadge, isFallback, isDirty, markClean, linkError }`
)

/**
 * @param content  已发布那份（contentJson）
 * @param langs    站点开的语言，第一个是默认语言
 * @param draft    草稿（draftJson）。给了就该优先于 content 被载进表单
 */
function mount(content, langs = 'en,zh,ko', draft = null) {
  site.value = {
    siteCode: 'T1', siteName: '测试站', domain: 'x.test', langs,
    contentJson: content, draftJson: draft, hasDraft: !!draft
  }
  messages.length = 0
  saved.length = 0
  return factory(
    vue.ref, vue.reactive, vue.computed,
    (key, dflt) => (key === 'stationSite' ? site : key === 'stationCanEdit' ? vue.ref(true) : dflt),
    vue.watch, stub, stub, vue.h,
    // onBeforeRouteLeave：这个脚本不跑路由，注册一下就够了
    stub,
    ElMessage, { confirm: () => Promise.resolve() }, {}, {}, {},
    () => ({
      saver: json => { saved.push(json); return Promise.resolve() },
      previewer: null, publisher: null, discarder: null, translator: null
    })
  )
}

/* ---------------- 断言 ---------------- */

let failed = 0
function expect(name, actual, want) {
  const a = JSON.stringify(actual)
  const b = JSON.stringify(want)
  const ok = a === b
  if (!ok) failed++
  console.log((ok ? '  ok   ' : '  失败 ') + name + (ok ? '' : `\n         想要: ${b}\n         得到: ${a}`))
}

/* ---------------- 1. v2 老数据升上来 ---------------- */

const V2 = {
  v: 2,
  logo: '/logo.png',
  i18n: {
    en: {
      companyName: 'Acme', seoTitle: 'EN title', seoDescription: 'EN desc',
      nav: [{ title: 'Home', url: '/' }, { title: 'Products', url: '/products' }],
      highlights: [{ icon: '★', title: 'EN one', desc: 'EN one desc' },
                   { icon: '◆', title: 'EN two', desc: 'EN two desc' }],
      capabilities: [{ icon: '✦', title: 'EN cap', desc: 'EN cap desc' }],
      hero: { title: 'EN hero', subtitle: 'EN sub', image: '/hero-en.jpg' },
      about: { title: 'EN about', content: 'EN body', image: '/about-en.jpg',
               stats: [{ value: '15+', label: 'Years' }, { value: '30', label: 'Countries' }] },
      contact: { address: 'EN addr', phone: '+86 1', email: 'a@b.c' },
      bento: { title: 'EN bento', stat: { value: '60+', label: 'Countries served' } },
      reach: { title: 'EN reach', stats: [{ value: '9', label: 'Ports' }] }
    },
    zh: {
      companyName: '艾克米', seoTitle: '中文标题',
      // ⚠ 中文这份比英文多一条导航：条数该按最长的那种算，不然那一条会被丢掉
      nav: [{ title: '首页', url: '/' }, { title: '产品', url: '/products' },
            { title: '博客', url: '/blog' }],
      highlights: [{ icon: '★', title: '中文一', desc: '中文一说明' }],
      hero: { title: '中文大标题', image: '/hero-zh.jpg' },
      about: { title: '关于我们', content: '中文正文' },
      contact: { address: '中文地址', phone: '+86 1' }
    },
    ko: {}
  }
}

console.log('1. v2 老数据：结构捞到共享层、文案按条目对上')
const a = mount(JSON.stringify(V2))
expect('导航条数按最长的那种语言算', a.shared.nav.length, 3)
expect('导航地址进共享层', a.shared.nav.map(n => n.url), ['/', '/products', '/blog'])
expect('特色条数按最长的算', a.shared.highlights.length, 2)
expect('图标进共享层', a.shared.highlights.map(h => h.icon), ['★', '◆'])
expect('数字亮点的数值进共享层', a.shared.aboutStats.map(s => s.value), ['15+', '30'])
expect('粉色数字卡的数字进共享层', a.shared.bentoStatValue, '60+')
expect('Banner 背景图升成幻灯片', a.shared.heroSlides.length, 1)
expect('电话进共享层', a.shared.contact.phone, '+86 1')

const navIds = a.shared.nav.map(n => n.id)
expect('英文的导航名字按条目对上', navIds.map(id => a.byLang.en.navText[id]?.title),
  ['Home', 'Products', undefined])
expect('中文的导航名字按条目对上', navIds.map(id => a.byLang.zh.navText[id]?.title),
  ['首页', '产品', '博客'])
expect('中文只译了第一条特色', a.shared.highlights.map(h => a.byLang.zh.highlightText[h.id]?.title),
  ['中文一', undefined])
expect('韩语一条都没译', Object.keys(a.byLang.ko.navText).length, 0)
expect('数字亮点的说明分语言', a.shared.aboutStats.map(s => a.byLang.en.aboutStatText[s.id]?.label),
  ['Years', 'Countries'])
expect('粉卡说明分语言', a.byLang.en.bento.statLabel, 'Countries served')
expect('SEO 分语言', [a.byLang.en.seoTitle, a.byLang.zh.seoTitle], ['EN title', '中文标题'])

/* ---------------- 2. 存出去是不是新结构 ---------------- */

console.log('2. 存出去的 JSON：结构在共享层，文案在 i18n 里')
a.save()
expect('保存调了一次', saved.length, 1)
const v4Json = saved[0] || "{}"
const out = JSON.parse(v4Json)
expect('版本是 4', out.v, 4)
expect('导航在共享层，只有 id 和地址',
  out.nav.map(n => Object.keys(n).sort().join('+')), ['id+url', 'id+url', 'id+url'])
expect('语言层里没有 nav 这个数组', out.i18n.en.nav, undefined)
expect('英文导航名字在 navText 里',
  Object.values(out.i18n.en.navText).map(v => v.title), ['Home', 'Products'])
expect('中文导航名字在 navText 里',
  Object.values(out.i18n.zh.navText).map(v => v.title), ['首页', '产品', '博客'])
expect('特色的图标在共享层', out.highlights.map(h => h.icon), ['★', '◆'])
expect('特色的文字在语言层', Object.values(out.i18n.en.highlightText).map(v => v.title),
  ['EN one', 'EN two'])
expect('数字亮点：数值共享', out.about.stats.map(s => s.value), ['15+', '30'])
expect('数字亮点：说明分语言', Object.values(out.i18n.en.aboutStatText).map(v => v.label),
  ['Years', 'Countries'])
expect('粉卡数字共享', out.bento.statValue, '60+')
expect('粉卡说明分语言', out.i18n.en.bento.statLabel, 'Countries served')
expect('幻灯片文案袋子在语言层顶层', out.i18n.en.slideText !== undefined
  || a.shared.heroSlides.every(s => !a.byLang.en.slideText[s.id]?.title), true)
expect('韩语整份是空的（一个字都没填）', out.i18n.ko, undefined)
expect('共享层不含任何文案',
  ['companyName', 'seoTitle', 'navText', 'highlightText'].filter(k => k in out), [])

/* ---------------- 3. 再载一次：新结构进出一致 ---------------- */

console.log('3. 存完再载一次，进出一致（第二次保存不该再变形）')
const b = mount(v4Json)
expect('导航条数不变', b.shared.nav.length, 3)
expect('导航地址不变', b.shared.nav.map(n => n.url), ['/', '/products', '/blog'])
expect('英文名字还在', b.shared.nav.map(n => b.byLang.en.navText[n.id]?.title),
  ['Home', 'Products', undefined])
expect('中文名字还在', b.shared.nav.map(n => b.byLang.zh.navText[n.id]?.title),
  ['首页', '产品', '博客'])
b.save()
expect('第二次存出来和第一次一样', JSON.parse(saved[0]), out)

/* ---------------- 4. 切语言不动结构 ---------------- */

console.log('4. 切语言只换文字，结构一动不动')
const c = mount(v4Json)
const before = JSON.stringify(c.shared)
c.lang.value = 'zh'
expect('切到中文，共享层一个字节都没变', JSON.stringify(c.shared), before)
c.lang.value = 'ko'
expect('切到韩语，共享层一个字节都没变', JSON.stringify(c.shared), before)

/* ---------------- 5. 加一条 / 删一条：所有语言一起 ---------------- */

console.log('5. 加一条、删一条：所有语言一起变')
const d = mount(v4Json)
d.addRow(d.shared.nav, ['url'])
const added = d.shared.nav[d.shared.nav.length - 1]
added.url = '/contact'
d.byLang.en.navText[added.id] = { title: 'Contact' }
expect('新条目在共享层，所有语言都能看见它', d.shared.nav.length, 4)

const doomed = d.shared.nav[0]
d.removeRow(d.shared.nav, 'navText', 0)
expect('删掉之后共享层少一条', d.shared.nav.length, 3)
expect('英文里那条文案也没了', d.byLang.en.navText[doomed.id], undefined)
expect('中文里那条文案也没了', d.byLang.zh.navText[doomed.id], undefined)

d.save()
const out5 = JSON.parse(saved[0])
expect('存出去的导航是加完删完的那三条',
  out5.nav.map(n => n.url), ['/products', '/blog', '/contact'])
expect('新加那条的名字进了英文袋子',
  Object.values(out5.i18n.en.navText).map(v => v.title), ['Products', 'Contact'])

/* ---------------- 6. 一条都没配时不写空数组 ---------------- */

console.log('6. 一条都没配：整个键不写（写了空数组会把内置默认盖没）')
const e = mount('')
e.save()
const out6 = JSON.parse(saved[0])
expect('没有 nav 这个键', 'nav' in out6, false)
expect('没有 highlights 这个键', 'highlights' in out6, false)
expect('order 照写（默认顺序也是一种明确的选择）', Array.isArray(out6.order), true)

/* ---------------- 7. v3 数据：图片已在共享层、条目还在语言层 ---------------- */

console.log('7. v3 数据（图片已共享、条目还在语言层）也升得上来')
const V3 = {
  v: 3,
  logo: '/logo.png',
  hero: { slides: [{ id: 's1', image: '/a.jpg' }] },
  about: { image: '/about.jpg' },
  contact: { phone: '+86 1', email: 'a@b.c' },
  order: ['hero', 'bento', 'products', 'caps', 'about', 'reach', 'inquiry'],
  i18n: {
    en: { nav: [{ title: 'Home', url: '/' }],
          hero: { title: 'EN hero', slideText: { s1: { title: 'EN slide' } } },
          highlights: [{ icon: '★', title: 'EN one', desc: 'd' }] },
    zh: { nav: [{ title: '首页', url: '/' }], hero: { title: '中文' } }
  }
}
const f = mount(JSON.stringify(V3))
expect('幻灯片还是那一张，id 没变', f.shared.heroSlides.map(s => s.id), ['s1'])
expect('幻灯片文案从 hero.slideText 挪到顶层', f.byLang.en.slideText.s1?.title, 'EN slide')
expect('导航升上来了', f.shared.nav.map(n => n.url), ['/'])
expect('两种语言的名字都在', f.shared.nav.map(n =>
  [f.byLang.en.navText[n.id]?.title, f.byLang.zh.navText[n.id]?.title]), [['Home', '首页']])

/* ---------------- 8. 草稿优先于已发布那份（2026-09-13 草稿/发布） ---------------- */

console.log('8. 有草稿就载草稿，没有才载已发布那份')
const PUBLISHED = { v: 4, i18n: { en: { companyName: '已发布' } } }
const DRAFT = { v: 4, i18n: { en: { companyName: '草稿' } } }

const g = mount(JSON.stringify(PUBLISHED), 'en,zh', JSON.stringify(DRAFT))
expect('有草稿时载的是草稿', g.byLang.en.companyName, '草稿')

const g2 = mount(JSON.stringify(PUBLISHED), 'en,zh', null)
expect('没草稿时载的是已发布那份', g2.byLang.en.companyName, '已发布')

// 「清空了但还没发布」那一种：草稿是一个空对象，不是 null。
// 这时候**不能**退回已发布那份，否则商家看到的是他刚清掉的内容
const g3 = mount(JSON.stringify(PUBLISHED), 'en,zh', '{}')
expect('空草稿也是草稿，不退回已发布那份', g3.byLang.en.companyName, '')

/* ---------------- 9. Banner 按钮链接 + 图片说明（2026-09-13） ---------------- */

console.log('9. 按钮链接在共享层、图片说明分语言')
const h9 = mount(JSON.stringify({
  v: 4,
  hero: { slides: [{ id: 's1', image: '/a.jpg' }], btn1Url: '/products', btn2Url: '#inquiry' },
  about: { image: '/about.jpg' },
  i18n: {
    en: { hero: { title: 'EN' }, slideText: { s1: { alt: 'EN alt' } }, about: { imageAlt: 'EN about alt' } },
    zh: { hero: { title: '中文' }, slideText: { s1: { alt: '中文说明' } } }
  }
}), 'en,zh')
expect('按钮链接载到共享层', [h9.shared.heroBtn1Url, h9.shared.heroBtn2Url], ['/products', '#inquiry'])
expect('图片说明按语言各一份', [h9.byLang.en.slideText.s1?.alt, h9.byLang.zh.slideText.s1?.alt],
  ['EN alt', '中文说明'])
expect('关于我们的图片说明也分语言', h9.byLang.en.about.imageAlt, 'EN about alt')

const out9 = h9.composePayload(undefined)
expect('存出去：按钮链接在共享层的 hero 底下', [out9.hero.btn1Url, out9.hero.btn2Url],
  ['/products', '#inquiry'])
expect('存出去：共享层的 hero 里没有文案', 'title' in out9.hero, false)
expect('存出去：图片说明在语言层的袋子里', out9.i18n.en.slideText.s1.alt, 'EN alt')
// ⚠ 这一条是 v4 那条规矩的回归测试：袋子必须在语言层**顶层**，
//   塞进 hero 底下的话，翻了一张另外两张的字会被整个换掉
expect('存出去：袋子在语言层顶层，不在 hero 底下', 'slideText' in (out9.i18n.en.hero || {}), false)

// 没填链接时整个键不写，前台看不到这个键就用内置目的地
const out9b = mount(JSON.stringify({ v: 4, i18n: { en: { hero: { title: 'x' } } } }), 'en')
  .composePayload(undefined)
expect('没填链接时不写这两个键', [out9b.hero?.btn1Url, out9b.hero?.btn2Url], [undefined, undefined])

/* ---------------- 10. 链接校验：和前台 heroLink() 认的写法必须一致 ---------------- */

console.log('10. 链接校验')
const i10 = mount('', 'en')
expect('留空没问题', i10.linkError(''), '')
expect('站内路径没问题', i10.linkError('/products'), '')
expect('本页锚点没问题', i10.linkError('#inquiry'), '')
expect('站内路径带锚点没问题', i10.linkError('/#about'), '')
expect('外链没问题', i10.linkError('https://example.com'), '')
expect('javascript: 挡掉', i10.linkError('javascript:alert(1)') !== '', true)
expect('data: 挡掉', i10.linkError('data:text/html,<script>') !== '', true)
expect('没有斜杠的相对路径挡掉', i10.linkError('products') !== '', true)

/* ---------------- 11. 未保存改动（2026-09-13） ---------------- */

console.log('11. 未保存改动')
const j11 = mount(JSON.stringify({ v: 4, i18n: { en: { companyName: 'Acme' } } }), 'en')
j11.markClean()
expect('刚载入完不算脏', j11.isDirty.value, false)
j11.byLang.en.companyName = 'Acme 2'
expect('改一个字就算脏', j11.isDirty.value, true)
j11.byLang.en.companyName = 'Acme'
// 改回来就不算脏。这条是有意的：否则商家会被一个并不存在的改动拦住
expect('改回原样又不算脏', j11.isDirty.value, false)
j11.shared.heroBtn1Url = '/products'
expect('共享层的改动也算脏', j11.isDirty.value, true)
/* ---------------- 12. Banner 背景视频（2026-09-21） ---------------- */
//
// 视频和背景图是同一张幻灯片的两层：有视频就播视频，图退成它的封面。
// 这一节盯的是那条老规矩——**结构在共享层**：video 必须和 image 一样存在
// 共享层的 slides 里，不能跟着文案分语言存（一段产品视频不会因为访客
// 切到日语就换一段）。

console.log('12. Banner 背景视频')
const k12 = mount(JSON.stringify({
  v: 4,
  hero: {
    slides: [
      { id: 's1', image: '/cover.jpg', video: '/hero.mp4' },
      { id: 's2', image: '/b.jpg' }
    ]
  },
  i18n: { en: { hero: { title: 'EN' } } }
}), 'en,zh')
expect('视频载到共享层', k12.shared.heroSlides.map(s => s.video), ['/hero.mp4', ''])
expect('没配视频的那张读出来是空串，不是 undefined', typeof k12.shared.heroSlides[1].video, 'string')

const out12 = k12.composePayload(undefined)
expect('存出去：视频和图在同一条 slides 里', out12.hero.slides,
  [{ id: 's1', image: '/cover.jpg', video: '/hero.mp4' }, { id: 's2', image: '/b.jpg' }])
// prune 把空串剪掉，所以没配视频的那张根本不写这个键——
// 老站点存一次之后，库里那份 JSON 和改造之前一模一样
expect('存出去：没配视频的那张不写 video 键', 'video' in out12.hero.slides[1], false)

// 只有视频、没配封面的那张也要留得住：前台认 image 或 video 任意一个
const k12b = mount(JSON.stringify({
  v: 4, hero: { slides: [{ id: 's1', video: '/only.mp4' }] }, i18n: { en: {} }
}), 'en')
expect('只有视频没有封面的那张也在', k12b.shared.heroSlides[0].video, '/only.mp4')
expect('存出去还是只有视频那一个键', k12b.composePayload(undefined).hero.slides,
  [{ id: 's1', video: '/only.mp4' }])

// 老数据（2026-09-21 之前）一个 video 键都没有：升上来全是空串，存出去也不该多出这个键
const k12c = mount(JSON.stringify({
  v: 3, hero: { image: '/old.jpg' }, i18n: { en: { hero: { title: 'x' } } }
}), 'en')
expect('老数据升上来没有视频', k12c.shared.heroSlides.map(s => s.video), [''])
expect('老数据存出去也不写 video 键',
  'video' in k12c.composePayload(undefined).hero.slides[0], false)

console.log(failed === 0 ? '\n全部通过' : `\n有 ${failed} 条不通过`)
process.exit(failed === 0 ? 0 : 1)

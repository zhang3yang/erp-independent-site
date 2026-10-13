/*
 * 管理台后端的**假接口**，本机开发/验收装修页时用。⚠ 不是生产代码，不参与任何构建。
 *
 *     node scripts/dev/mock-console-api.js          # 有编辑权限
 *     MOCK_NO_EDIT=1 node scripts/dev/mock-console-api.js   # 试「只读」那一版
 *
 * 起在 8081——station-console-ui 的 vite 代理和 station-site 的 NUXT_API_BASE
 * 默认都指着这个端口，所以**不用改项目里任何文件**。
 *
 * 它同时顶两套接口：管理台的 `/api/**` 和前台的 `/openapi/site/**`。
 * 起真后端要 MySQL + Redis + 一个 ERP 免密跳转过来的会话，
 * 只想点一下界面的话这个省事得多。
 *
 * 种的是一份 **v2 老数据**（图片和电话都在语言层）外加 order/off/blocks 三个新键，
 * 正好能验两件事：载入时会不会把语言层那几项捞到共享层、
 * 保存时不认识的键有没有被原样带回去。
 *
 * ⚠ 里面的合并是照 SiteOpenService 抄的**测试替身**，不是那份逻辑的第二实现——
 *   线上那份有自己的真值表：scripts/verify/MergeCheck.java。
 *
 * 跑在 8081——station-console-ui 的 vite 代理本来就把 /api 转到这个端口，
 * 所以**不用改项目里的任何文件**。
 *
 * 种的是一份 v2 老数据（图片和电话都在语言层），外加两个「未来版本写的」顶层键
 * order / blocks —— 正好把这次改动的两处都盖住：
 *   · 载入时能不能把语言层里那几项捞到共享层（fillShared）
 *   · 保存时认不认识的键有没有被原样带回去（passthrough）
 */
const http = require('http')
const fs = require('fs')
const path = require('path')

// 收到的保存写到系统临时目录，不往仓库里拉屎。启动时会把完整路径打出来
const OUT = path.join(require('os').tmpdir(), 'station-mock-saved-content.json')

const SEED = {
  v: 2,
  logo: '/api/mockimg/logo.svg',
  // 未来版本才有编辑界面的三个键。表单不认识它们，但保存时必须原样写回去
  order: ['hero', 'bento', 'products', 'caps', 'about', 'reach', 'inquiry'],
  off: ['bento'],
  blocks: { caps: { accent: 1 }, products: { limit: 6 } },
  i18n: {
    en: {
      companyName: 'Tongling Shidai',
      slogan: 'Precision Airflow and Power Components',
      seoTitle: 'Tongling Shidai - Silicone Tube Supplier',
      seoDescription: 'Chinese manufacturer of silicone tubes and air pumps.',
      /*
       * ⚠ 默认用**老存法**（单张 image）：载入时要能被归一成数组，见 fillShared。
       *   MOCK_HERO_IMAGES=3 换成新存法（images 数组），用来验轮播。
       */
      hero: Object.assign(
        { title: 'Precision Airflow.', subtitle: 'Tested as one system.' },
        Number(process.env.MOCK_HERO_IMAGES) > 1
          ? { images: ['/api/mockimg/hero.svg', '/api/mockimg/line.svg', '/api/mockimg/pump.svg']
                .slice(0, Number(process.env.MOCK_HERO_IMAGES)) }
          : { image: '/api/mockimg/hero.svg' }),
      highlights: [{ icon: '〰', title: 'Tuned as One System', desc: 'Matched on the same bench.' }],
      capabilities: [{ icon: '◆', title: 'OEM / ODM', desc: 'Prototype in 7 to 15 days.' }],
      about: {
        title: 'About Us', content: 'One extrusion line became a plant.',
        image: '/api/mockimg/about.svg',
        stats: [{ value: '15+', label: 'Years' }, { value: '40+', label: 'Countries' }]
      },
      contact: {
        address: 'Jinghai Industrial Park, Tianjin',
        phone: '+86 22 5988 6600', email: 'sales@example.com', whatsapp: '+86 138 0013 8000'
      },
      nav: [{ title: 'Home', url: '/' }, { title: 'Products', url: '/products' }]
    },
    ja: {
      companyName: 'トンリン時代',
      // 日语故意只填一半：没有 slogan、没有 about、没有 nav
      hero: { title: '精密なエア。', subtitle: '一つのシステムとして。', image: '/api/mockimg/hero.svg' },
      contact: {
        address: '中国天津市静海工業園',
        phone: '+86 22 5988 6600', email: 'sales@example.com', whatsapp: '+86 138 0013 8000'
      }
    }
  }
}

let contentJson = JSON.stringify(SEED)

/*
 * 草稿。真后端里它是 u_s_siteinfo.draftjson 那一列：
 * 「保存草稿」写它、「发布」把它拷进 contentjson 再置 null、「放弃草稿」只置 null。
 *
 * ⚠ null 和 '{}' 是两回事：前者是「没有未发布的改动」，后者是「清空了但还没发布」。
 *   假后端也要分清楚，不然装修页那条「空草稿也是草稿」的行为在本地试不出来。
 */
let draftJson = null
let draftTime = null

/** 假的身份。改这里的 perms 就能试没有编辑权限的样子 */
const PERMS = process.env.MOCK_NO_EDIT
  ? ['site:station:list', 'site:file:upload']
  : ['site:station:list', 'site:station:edit', 'site:file:upload', 'site:goods:list', 'site:chat:reply']

const me = {
  siteCode: 'SDSSHOP', siteName: '统领时代（本机假数据）',
  langs: ['en', 'ja', 'zh'], currency: 'USD',
  userName: 'local', displayName: '本机测试', roleCode: 'admin',
  perms: PERMS, via: 'erp', erpHomeUrl: ''
}

const siteRow = () => ({
  siteCode: 'SDSSHOP', siteName: '统领时代（本机假数据）',
  // 指向本机跑着的那个前台，这样管理台点「预览」开出来的窗口是真页面
  domain: 'http://127.0.0.1:3000', status: '运行中',
  langs: 'en,ja,zh', currency: 'USD', currencies: 'USD,EUR',
  notifyEmail: 'sales@example.com',
  contentJson,
  // 草稿/发布两态（2026-09-13）。draftJson 为 null = 没有未发布的改动
  draftJson,
  draftTime: draftJson == null ? null : draftTime,
  hasDraft: draftJson != null
})

function svg(label, c1, c2) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="320">
    <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${c1}"/><stop offset="1" stop-color="${c2}"/></linearGradient></defs>
    <rect width="480" height="320" fill="url(#g)"/>
    <text x="240" y="170" font-family="sans-serif" font-size="34" fill="#fff"
      text-anchor="middle">${label}</text></svg>`
}
const IMAGES = {
  'logo.svg': svg('LOGO', '#6c4b9c', '#a98fce'),
  'hero.svg': svg('HERO', '#4e3670', '#9e68f8'),
  'about.svg': svg('FACTORY', '#2a7d6f', '#7fd1c1'),
  'line.svg': svg('LINE 2', '#b4690e', '#f0c274'),
  'pump.svg': svg('PUMP', '#c0392b', '#f0938a')
}

const imageRows = Object.keys(IMAGES).map((name, i) => ({
  imageId: i + 1,
  imageName: name.replace('.svg', ''),
  imageUrl: '/api/mockimg/' + name,
  thumbUrl: '/api/mockimg/' + name,
  folderId: i < 2 ? 'F1' : 'ROOT'
}))

const folders = [{ folderId: 'F1', folderName: '首页用图', fileCount: 2, children: [] }]

function send(res, body, type = 'application/json;charset=UTF-8') {
  res.writeHead(200, { 'Content-Type': type })
  res.end(typeof body === 'string' ? body : JSON.stringify(body))
}
const ok = (res, data) => send(res, { code: 200, msg: 'ok', data })

/* ==========================================================================
   前台那一套（/openapi/site/**）+ 装修预览的合并
   --------------------------------------------------------------------------
   这里的三层合并是照 SiteOpenService#siteContent 抄的（共享层 → 默认语言层 →
   当前语言层，同名键两边都是对象就合一层），后面那一步拼接照的是 #compose
   （共享的条目 + 语言层的文案，按 id 拼）。这是**测试替身**，不是线上代码；
   线上那份有自己的真值表在 MergeCheck.java 里。
   ========================================================================== */
function overlay(merged, layer) {
  if (!layer || typeof layer !== 'object') return
  for (const [k, v] of Object.entries(layer)) {
    const cur = merged[k]
    if (cur && typeof cur === 'object' && !Array.isArray(cur) &&
        v && typeof v === 'object' && !Array.isArray(v)) {
      merged[k] = { ...cur, ...v }
    } else {
      merged[k] = v
    }
  }
}

const BUILTIN = () => ({
  companyName: '统领时代（本机假数据）', slogan: '', logo: '',
  seoTitle: '统领时代（本机假数据）', seoDescription: '',
  currency: 'USD', currencies: ['USD'], rates: { USD: 1 }, rateTime: null,
  lang: 'en', defaultLang: 'en', langs: ['en', 'ja', 'zh'],
  nav: [{ title: 'Home', url: '/' }, { title: 'Products', url: '/products' },
        { title: 'About Us', url: '/#about' }, { title: 'Contact', url: '/#inquiry' }],
  hero: { title: '统领时代（本机假数据）', subtitle: '', image: '' },
  highlights: [], capabilities: [], about: {}, contact: {},
  seo: {}, categories: []
})

/**
 * 合完之后的拼接，照 SiteOpenService#compose 抄的。
 *
 * 每一种可重复的东西都是两半存的：**条目在共享层**（`nav: [{id,url}]`，
 * 有几条、指向哪、什么顺序，所有语言共用），**文案在语言层按 id 存**
 * （`navText: {n1:{title}}`）。这里按 id 把两边拼回前台认识的那种形状。
 *
 * 没有 id 的条目原样放行——那是 v3 及更早的数据（整条都在语言层）。
 */
function joinRows(merged, owner, listKey, textKey) {
  const text = merged[textKey]
  delete merged[textKey]
  const list = owner[listKey]
  if (!Array.isArray(list)) return
  owner[listKey] = list.map(row => {
    if (!row || typeof row !== 'object') return row
    const bag = text && typeof text === 'object' && row.id ? text[row.id] : null
    return bag && typeof bag === 'object' ? { ...row, ...bag } : { ...row }
  })
}

function compose(merged) {
  joinRows(merged, merged, 'nav', 'navText')
  joinRows(merged, merged, 'highlights', 'highlightText')
  joinRows(merged, merged, 'capabilities', 'capText')
  for (const [parent, list, bag] of [['hero', 'slides', 'slideText'],
                                     ['about', 'stats', 'aboutStatText'],
                                     ['reach', 'stats', 'reachStatText']]) {
    if (merged[parent] && typeof merged[parent] === 'object') {
      const copy = { ...merged[parent] }
      joinRows(merged, copy, list, bag)
      merged[parent] = copy
    } else {
      delete merged[bag]
    }
  }
  // 粉色数字卡：数字共享、说明分语言，拼成前台认识的 bento.stat
  const bento = merged.bento
  if (bento && typeof bento === 'object') {
    const copy = { ...bento }
    const value = copy.statValue
    const label = copy.statLabel
    delete copy.statValue
    delete copy.statLabel
    if (value !== undefined || label !== undefined) {
      copy.stat = { ...(copy.stat || {}) }
      if (value !== undefined) copy.stat.value = value
      if (label !== undefined) copy.stat.label = label
    }
    merged.bento = copy
  }
}

/** 把一份 contentJson 算成前台看到的那份数据 */
function resolveInfo(contentJson, lang) {
  const result = BUILTIN()
  const language = ['en', 'ja', 'zh'].includes(lang) ? lang : 'en'
  result.lang = language

  let root = {}
  try { root = contentJson ? JSON.parse(contentJson) : {} } catch { root = {} }

  const sharedLayer = { ...root }
  delete sharedLayer.i18n
  delete sharedLayer.v
  overlay(result, sharedLayer)

  const i18n = root.i18n
  if (i18n && typeof i18n === 'object') {
    if (language !== 'en') overlay(result, i18n.en)
    overlay(result, i18n[language])
  }
  compose(result)
  return result
}

const allProducts = Array.from({ length: 12 }, (_, i) => ({
  code: 'P' + (i + 1), title: 'Silicone Tube ' + (i + 1), summary: 'Medical grade.',
  category: 'Tubing', price: 1.2 + i, currency: 'USD', moq: 500, image: '', hot: i === 0
}))

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1:8081')

  // ---- 前台那一套 ----
  if (url.pathname.startsWith('/openapi/site')) {
    const sp = url.pathname.replace('/openapi/site', '')
    const json = body => {
      res.writeHead(200, { 'Content-Type': 'application/json;charset=UTF-8' })
      res.end(JSON.stringify({ code: 200, msg: 'ok', data: body }))
    }
    if (sp === '/info') return json(resolveInfo(contentJson, url.searchParams.get('lang')))
    if (sp === '/products') {
      const size = Number(url.searchParams.get('size') || 6)
      return json({ total: allProducts.length, page: 1, size, currency: 'USD',
        rows: allProducts.slice(0, size) })
    }
    return json({})
  }

  const p = url.pathname.replace(/^\/api/, '')

  // ---- 装修预览：拿草稿算一遍，不写库 ----
  if (p === '/site/content/preview' && req.method === 'POST') {
    let body = ''
    req.on('data', c => { body += c })
    req.on('end', () => {
      console.log('  → 预览请求，草稿 ' + body.length + ' 字节，lang=' + url.searchParams.get('lang'))
      res.writeHead(200, { 'Content-Type': 'application/json;charset=UTF-8' })
      res.end(JSON.stringify({ code: 200, msg: 'ok',
        data: resolveInfo(body, url.searchParams.get('lang')) }))
    })
    return
  }

  if (p.startsWith('/mockimg/')) {
    const name = p.slice('/mockimg/'.length)
    if (IMAGES[name]) return send(res, IMAGES[name], 'image/svg+xml')
    res.writeHead(404); return res.end()
  }

  if (p === '/me') return ok(res, me)
  if (p === '/site' && req.method === 'GET') return ok(res, siteRow())

  // ---- 保存草稿。⚠ 不动 contentJson，前台看到的还是已发布那份 ----
  if (p === '/site/content' && req.method === 'PUT') {
    let body = ''
    req.on('data', c => { body += c })
    req.on('end', () => {
      // 真后端把空串存成 '{}'（见 SiteInfoService#saveDraft）：null 这个值
      // 在 draftjson 上已经表示「没有草稿」了，两者必须分得开
      draftJson = body && body.trim() ? body.trim() : '{}'
      draftTime = new Date().toISOString().slice(0, 19).replace('T', ' ')
      fs.writeFileSync(OUT, body || '(空串 = 清空)', 'utf8')
      console.log('\n=== 收到保存草稿，' + body.length + ' 字节，已写入 saved-content.json ===')
      console.log('    （前台还是已发布那份，要点「发布到网站」才变）')
      ok(res, 1)
    })
    return
  }

  // ---- 发布：草稿拷进已发布那份，草稿清掉 ----
  if (p === '/site/content/publish' && req.method === 'POST') {
    if (draftJson == null) {
      res.writeHead(200, { 'Content-Type': 'application/json;charset=UTF-8' })
      return res.end(JSON.stringify({ code: 500, msg: '没有待发布的改动' }))
    }
    contentJson = draftJson
    draftJson = null
    draftTime = null
    console.log('\n=== 已发布，前台现在看到的是刚才那份草稿 ===')
    return ok(res, 1)
  }

  // ---- 放弃草稿 ----
  if (p === '/site/content/draft' && req.method === 'DELETE') {
    draftJson = null
    draftTime = null
    console.log('\n=== 已放弃草稿，回到已发布那份 ===')
    return ok(res, 1)
  }

  /*
   * ---- 机器翻译 ----
   * 不真调腾讯云（本机多半没配密钥，也不该为了试界面花翻译额度）。
   * 回的是「原文 + 语言标记」，这足以验管理台那一侧的全部逻辑：
   * 挑哪些格子、条数对不对得上、填回哪儿、以及「已填的不覆盖」。
   */
  if (p === '/site/translate' && req.method === 'POST') {
    let body = ''
    req.on('data', c => { body += c })
    req.on('end', () => {
      let payload = {}
      try { payload = JSON.parse(body || '{}') } catch { /* 下面按空处理 */ }
      const texts = Array.isArray(payload.texts) ? payload.texts : []
      console.log('  → 翻译请求 ' + payload.from + ' → ' + payload.to + '，' + texts.length + ' 段')
      ok(res, { texts: texts.map(t => `[${payload.to}] ${t}`) })
    })
    return
  }

  if (p === '/file/folder') return ok(res, folders)
  if (p === '/file/image') {
    const folderId = url.searchParams.get('folderId')
    const name = url.searchParams.get('imageName')
    let rows = imageRows
    if (folderId) rows = rows.filter(r => r.folderId === folderId)
    if (name) rows = rows.filter(r => r.imageName.includes(name))
    return send(res, { code: 200, rows, total: rows.length })
  }
  if (p === '/file/video') return send(res, { code: 200, rows: [], total: 0 })

  // 其余接口一律回空，别让别的页面报错干扰
  if (req.method === 'GET') return send(res, { code: 200, rows: [], total: 0, data: {} })
  return ok(res, 1)
})

server.listen(8081, '127.0.0.1', () => {
  console.log('假后端已启动 http://127.0.0.1:8081' + (process.env.MOCK_NO_EDIT ? '（无编辑权限模式）' : ''))
  console.log('保存的内容会写到 ' + OUT)
})

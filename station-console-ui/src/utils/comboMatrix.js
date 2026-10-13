/**
 * 商品组合的「规格矩阵」模式（尺码 × 款式）——编辑器用的数据模型和几个纯函数。
 *
 * 为什么有这个模式、库里怎么存，见 sql/station/migration/2026-09-25-goods-options.sql。
 * 一句话：维度和取值是 u_s_sitegoods.optionsjson，每个能卖的搭配仍是组合表的一行
 * （带 opt1/opt2）。组合名由后端按取值名拼，这边不用管。
 *
 * ## 界面上的模型
 *
 *   {
 *     dims:  [{ uid, code, names: {lang: ''}, values: [{ uid, code, names: {lang: ''}, image }] }],
 *     cells: { 'uid1|uid2': { comboCode, price, image, model3d, model3dMarks, enabled, existed } }
 *     （model3d / model3dMarks：规格自己的 3D 模型和标注，2026-10-09；标注空 = logo 跟随主模型）
 *   }
 *
 * ⚠ 格子用 uid 当键，**不用取值编码**。新加的取值在保存前还没有编码
 *   （编码在保存时才按名字生成，见 toPayload），而名字随时在改——拿会变的东西当键，
 *   改一个字这一行的价格就丢了。uid 只活在这一次编辑里，不入库。
 *
 * ⚠ 已经存在的组合，编码一律沿用（existed=true 的格子）。它是订单、询盘、
 *   购物车里记着的那个身份，从平铺切到矩阵也不换。
 */

let seq = 0
const uid = () => `u${++seq}`

/** 取值编码：只许字母数字 - _，最长 20（后端 GoodsOptions.CODE 同一条规则） */
const CODE_RE = /^[A-Za-z0-9_-]{1,20}$/

export const MAX_DIMS = 2

export function cellKey(uid1, uid2) {
  return `${uid1}|${uid2 || ''}`
}

function blankNames(langs) {
  const names = {}
  langs.forEach(lang => { names[lang] = '' })
  return names
}

function fillNames(src, langs) {
  const names = blankNames(langs)
  Object.entries(src || {}).forEach(([lang, name]) => {
    if (lang in names) names[lang] = name || ''
  })
  return names
}

export function newValue(langs) {
  return { uid: uid(), code: '', names: blankNames(langs), image: '' }
}

export function newDim(langs) {
  return { uid: uid(), code: '', names: blankNames(langs), values: [newValue(langs)] }
}

/**
 * 从后端数据建模型。库里没有维度（平铺模式），或者组合一条都没带 opt1，返回 null。
 *
 * @param {string|null} optionsJson u_s_sitegoods.optionsjson
 * @param {Array} combos 后台组合列表（带 opt1/opt2/price/image/status）
 */
export function fromServer(optionsJson, combos, langs) {
  let parsed = null
  try { parsed = optionsJson ? JSON.parse(optionsJson) : null } catch { parsed = null }
  const rawDims = Array.isArray(parsed?.dims) ? parsed.dims.slice(0, MAX_DIMS) : []
  if (!rawDims.length) return null
  if (!(combos || []).some(c => c.opt1)) return null

  const dims = rawDims.map(d => ({
    uid: uid(),
    code: d.code || '',
    names: fillNames(d.names, langs),
    values: (d.values || []).map(v => ({
      uid: uid(), code: v.code || '', names: fillNames(v.names, langs), image: v.image || ''
    }))
  }))
  const uidOf = (di, code) => dims[di]?.values.find(v => v.code === code)?.uid
  const cells = {}
  ;(combos || []).forEach(c => {
    const u1 = uidOf(0, c.opt1)
    const u2 = dims.length > 1 ? uidOf(1, c.opt2) : ''
    if (!u1 || (dims.length > 1 && !u2)) return
    cells[cellKey(u1, u2)] = {
      comboCode: c.comboCode,
      price: Number(c.price || 0),
      image: c.image || '',
      model3d: c.model3d || '',
      model3dMarks: c.model3dMarks || '',
      model3dStateRows: c.model3dStateRows || [],
      enabled: c.status !== '停用',
      existed: true
    }
  })
  return { dims, cells }
}

/**
 * 平铺 → 矩阵。现有的每一条组合变成第一维的一个取值（名称照搬），
 * 编码、价格、图、状态原样留在对应格子里。维度名空着，由商家填（「尺码」）。
 *
 * 取值编码先用组合编码（M / L / XL 这种本来就合规），不合规的留空，保存时再生成。
 */
export function fromFlat(rows, langs) {
  const dim = { uid: uid(), code: 'D1', names: blankNames(langs), values: [] }
  const cells = {}
  ;(rows || []).forEach(row => {
    const code = (row.comboCode || '').trim()
    const value = {
      uid: uid(),
      code: CODE_RE.test(code) ? code : '',
      names: fillNames(row.titles, langs),
      image: ''
    }
    dim.values.push(value)
    if (code) {
      cells[cellKey(value.uid, '')] = {
        comboCode: code,
        price: Number(row.price || 0),
        image: row.image || '',
        model3d: row.model3d || '',
        model3dMarks: row.model3dMarks || '',
        model3dStateRows: row.model3dStateRows || [],
        enabled: row.status !== '停用',
        existed: true
      }
    }
  })
  if (!dim.values.length) dim.values.push(newValue(langs))
  splitCommonPrefix(dim, langs)
  return { dims: [dim], cells }
}

/**
 * 平铺时代的组合名常常自带维度名：「尺码 S/M」「尺码 L」、「Taille S/M」「Taille L」。
 * 转成矩阵后维度名单独一格，取值名里再带一遍就成了「尺码：尺码 S/M」。
 *
 * 所以按语言找所有取值名共同的**第一个词**（空格前那一段），两个以上取值都以它开头
 * 就把它提出来当这种语言的维度名，取值名去掉它。找不到就原样不动。
 * 只认空格分隔的整词：「XL」「XXL」有公共前缀「XL」，但那不是维度名。
 *
 * 某种语言里有几个取值没填名字（esvafit 的 ru/es/ja 就缺 XXL）不影响判断：
 * 只看填了的那些，至少两个。空的照旧空着，前台回退默认语言。
 */
function splitCommonPrefix(dim, langs) {
  langs.forEach(lang => {
    const names = dim.values.map(v => (v.names[lang] || '').trim()).filter(Boolean)
    if (names.length < 2) return
    const first = names[0].split(/\s+/)[0]
    if (!first || first === names[0]) return
    if (!names.every(n => n.startsWith(first + ' ') && n.length > first.length + 1)) return
    if (!dim.names[lang]) dim.names[lang] = first
    dim.values.forEach(v => {
      const n = (v.names[lang] || '').trim()
      if (n) v.names[lang] = n.slice(first.length).trim()
    })
  })
}

/** 矩阵 → 平铺：每个格子变回一行组合，名称按「取值名 · 取值名」拼好（各语言） */
export function toFlat(model, langs, defaultLang) {
  return pairs(model).map(({ key, v1, v2 }) => {
    const cell = model.cells[key]
    if (!cell) return null
    const titles = {}
    langs.forEach(lang => { titles[lang] = pairTitle(v1, v2, lang, defaultLang) })
    return {
      comboCode: cell.comboCode || '',
      price: cell.price ?? 0,
      image: cell.image || '',
      model3d: cell.model3d || '',
      model3dMarks: cell.model3dMarks || '',
      model3dStateRows: cell.model3dStateRows || [],
      status: cell.enabled ? '可选' : '停用',
      titles
    }
  }).filter(Boolean)
}

/** 所有搭配，按第一维 × 第二维的顺序。只有一个维度时 v2 为 null */
export function pairs(model) {
  const [d1, d2] = model.dims
  if (!d1) return []
  const out = []
  d1.values.forEach(v1 => {
    if (d2 && d2.values.length) {
      d2.values.forEach(v2 => out.push({ key: cellKey(v1.uid, v2.uid), v1, v2 }))
    } else {
      out.push({ key: cellKey(v1.uid, ''), v1, v2: null })
    }
  })
  return out
}

function nameIn(value, lang, defaultLang) {
  return (value?.names?.[lang] || '').trim() || (value?.names?.[defaultLang] || '').trim()
}

export function pairTitle(v1, v2, lang, defaultLang) {
  const a = nameIn(v1, lang, defaultLang)
  if (!v2) return a
  const b = nameIn(v2, lang, defaultLang)
  return a && b ? `${a} · ${b}` : (a || b)
}

/**
 * 维度 / 取值变了之后，让每个搭配都有一个格子。
 *
 * - 新出现的搭配默认「提供」、价格取商品售价：加一个款式时，通常是每个尺码都做；
 *   不做的那几格由商家取消勾选。
 * - ⚠ 从一维变成二维（刚给第二个维度加了第一个取值）时，原来的 `uid1|` 格子
 *   **挪到** `uid1|第二维第一个取值` 上，而不是作废另建：那些格子带着现有组合的编码，
 *   作废的话已有的询盘、订单、购物车就全对不上了。反方向见 removeSecondDim。
 * - 已经没有对应搭配的格子留着不删：商家删了一个取值又加回来，不该丢价格。
 *   提交时只提交 pairs() 里的那些（toPayload），留着的不会被存。
 */
export function syncCells(model, basePrice) {
  const firstV2 = model.dims[1]?.values?.[0]?.uid || ''
  if (firstV2) {
    Object.keys(model.cells).forEach(key => {
      const [u1, u2] = key.split('|')
      if (u2) return
      const target = cellKey(u1, firstV2)
      if (!model.cells[target]) model.cells[target] = model.cells[key]
      delete model.cells[key]
    })
  }
  pairs(model).forEach(({ key }) => {
    if (!model.cells[key]) {
      model.cells[key] = { comboCode: '', price: Number(basePrice || 0), image: '', model3d: '', model3dMarks: '', model3dStateRows: [], enabled: true, existed: false }
    }
  })
}

/**
 * 删掉第二个维度，变回一维。每个第一维取值留下**一个**格子：优先留已经存在的、
 * 且正在提供的那条组合（它的编码在单据里），其次是已存在的，最后是第一个。
 * 其余的格子丢掉——保存后对应的组合会被删掉，这是删维度本来的意思。
 */
export function removeSecondDim(model) {
  if (model.dims.length < 2) return
  const d2 = model.dims[1]
  const score = cell => (cell?.existed ? 2 : 0) + (cell?.enabled ? 1 : 0)
  model.dims[0].values.forEach(v1 => {
    let best = null
    d2.values.forEach(v2 => {
      const cell = model.cells[cellKey(v1.uid, v2.uid)]
      if (cell && (!best || score(cell) > score(best))) best = cell
    })
    if (best && !model.cells[cellKey(v1.uid, '')]) model.cells[cellKey(v1.uid, '')] = best
  })
  Object.keys(model.cells).forEach(key => { if (key.split('|')[1]) delete model.cells[key] })
  model.dims.splice(1, 1)
}

/** 从默认语言名称里挑一个能当编码的（「XL」「S/M」→「SM」）；挑不出来返回空串 */
function codeFromName(name) {
  const ascii = String(name || '').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 20)
  return CODE_RE.test(ascii) ? ascii.toUpperCase() : ''
}

/**
 * 组保存用的数据：{ optionsJson, combos }；不合规返回 { error: { key, vars } }（文案键 + 参数）。
 *
 * 做三件事：
 *   1. 查默认语言名称：维度名、每个取值名都得有（后端也查，这里查能指出是哪一个）；
 *   2. 给还没有编码的取值生成编码：优先用默认语言名（XL、SM），不行就 V1、V2……
 *      同一维度里不重复；
 *   3. 每个格子出一条组合：已有的沿用编码；新的编码 =「取值编码-取值编码」。
 *      没勾「提供」的：已有的存成「停用」（保住编码，以后重新勾上还是它），新的直接不存。
 */
export function toPayload(model, langs, defaultLang) {
  if (!model.dims.length) return { error: { key: 'goods.matrix.errNoDim' } }
  for (const [di, dim] of model.dims.entries()) {
    if (!(dim.names[defaultLang] || '').trim()) {
      return { error: { key: 'goods.matrix.errDimName', vars: { n: di + 1, lang: defaultLang } } }
    }
    if (!dim.values.length) {
      return { error: { key: 'goods.matrix.errNoValue', vars: { dim: dim.names[defaultLang] } } }
    }
    for (const value of dim.values) {
      if (!(value.names[defaultLang] || '').trim()) {
        return { error: { key: 'goods.matrix.errValueName', vars: { dim: dim.names[defaultLang], lang: defaultLang } } }
      }
    }
  }

  // 编码
  model.dims.forEach((dim, di) => {
    if (!CODE_RE.test(dim.code || '')) dim.code = `D${di + 1}`
    const used = new Set(dim.values.map(v => v.code).filter(c => CODE_RE.test(c || '')))
    let n = 1
    dim.values.forEach(value => {
      if (CODE_RE.test(value.code || '')) return
      let code = codeFromName(value.names[defaultLang])
      while (!code || used.has(code)) code = `V${n++}`
      value.code = code
      used.add(code)
    })
  })

  const trimNames = names => {
    const out = {}
    Object.entries(names || {}).forEach(([lang, name]) => {
      const v = (name || '').trim()
      if (v) out[lang] = v
    })
    return out
  }
  const optionsJson = JSON.stringify({
    dims: model.dims.map(dim => ({
      code: dim.code,
      names: trimNames(dim.names),
      values: dim.values.map(v => ({
        code: v.code,
        names: trimNames(v.names),
        ...(v.image ? { image: v.image } : {})
      }))
    }))
  })

  const combos = []
  const all = pairs(model)
  // ⚠ 先把已有组合的编码全占上，再给新格子生成：否则新格子生成的「M-BLK」可能抢在
  //   一条本来就叫「M-BLK」的老组合前面，老组合反被挤成「M-BLK-2」——它在单据里的身份就丢了
  const codes = new Set(all.map(p => model.cells[p.key]).filter(c => c?.existed && c.comboCode).map(c => c.comboCode))
  all.forEach(({ key, v1, v2 }) => {
    const cell = model.cells[key]
    if (!cell) return
    if (!cell.enabled && !cell.existed) return
    let code = cell.existed && cell.comboCode ? cell.comboCode : ''
    if (!code) {
      // 和已有编码撞上：加后缀，不覆盖别人
      const base = v2 ? `${v1.code}-${v2.code}` : v1.code
      code = base
      let n = 2
      while (codes.has(code)) code = `${base.slice(0, 46)}-${n++}`
      codes.add(code)
    }
    cell.comboCode = code
    combos.push({
      comboCode: code,
      opt1: v1.code,
      opt2: v2 ? v2.code : null,
      price: cell.price ?? 0,
      image: (cell.image || '').trim() || null,
      model3d: (cell.model3d || '').trim() || null,
      model3dMarks: cell.model3d ? (cell.model3dMarks || null) : null,
      model3dStateRows: cell.model3dStateRows || [],
      status: cell.enabled ? '可选' : '停用',
      sortNum: combos.length,
      // 后端二维模式下按取值名重拼，不看这一份；带上只为老后端兼容
      titles: Object.fromEntries(langs.map(lang => [lang, pairTitle(v1, v2, lang, defaultLang)]))
    })
  })
  if (!combos.some(c => c.status === '可选')) return { error: { key: 'goods.matrix.errNothingOn' } }
  return { optionsJson, combos }
}

/* ==================== 名称的一键翻译（2026-09-25） ====================

   商家的习惯是**在中文里把维度填好**（尺码 / 款式 / 黑色……），然后一键铺到所有语言。
   所以这里的源语言不是站点默认语言，而是「他正在填的那种语言」（Goods.vue 的 comboLang），
   目标是其余**所有**语言——包括默认语言（esvafit 的默认是 fr，保存时 fr 名称必填，
   正好由这一步补上）。

   什么会被写：
     · 空着的格子；
     · 这次编辑里机器翻出来、之后没被手改过的格子——商家把「灰色」改成「深灰」再点一次，
       各语言跟着变，这才叫「同步」。
   什么不会被写：商家自己填过或手改过的译名。

   「机器翻的、没手改过」记在 machineText 里（WeakMap，键是 names 对象）：
   不进模型、不入库，toPayload 序列化时也看不见。代价是重新打开商品后这份记录没了，
   那时只补空格子——宁可少同步一次，也不覆盖掉商家可能手改过的字。 */

const machineText = new WeakMap()

function writable(names, lang) {
  const cur = (names[lang] || '').trim()
  if (!cur) return true
  return machineText.get(names)?.[lang] === names[lang]
}

/** 每一处名称（维度名 + 取值名）。源语言没填的那一处跳过 */
function allNames(model) {
  const out = []
  ;(model?.dims || []).forEach(dim => {
    out.push(dim.names)
    dim.values.forEach(v => out.push(v.names))
  })
  return out
}

/**
 * 以 sourceLang 为源，列出要翻的：[{ to, items: [{ text, names }] }]，按目标语言分组，
 * 一种目标语言一次请求。没有要翻的返回空数组。
 */
export function planNameTranslation(model, langs, sourceLang) {
  const groups = []
  langs.filter(lang => lang !== sourceLang).forEach(to => {
    const items = []
    allNames(model).forEach(names => {
      const text = (names[sourceLang] || '').trim()
      if (text && writable(names, to)) items.push({ text, names })
    })
    if (items.length) groups.push({ to, items })
  })
  return groups
}

/**
 * 执行上面那份计划。translate(from, to, texts) → Promise<string[]>（Goods.vue 递进来，
 * 包一层 api/console 的 translateTexts）。
 *
 * 同一个原因连着失败两次就停（账号没开通机器翻译这种，换哪种语言都一样），
 * 和 Goods.vue translateAllLanguages 同一条规矩。
 *
 * @returns {{ done: string[], failed: string[] }} 成功 / 失败的目标语言
 */
export async function runNameTranslation(model, langs, sourceLang, translate) {
  const groups = planNameTranslation(model, langs, sourceLang)
  const done = []
  const failed = []
  let lastError = ''
  for (const [index, group] of groups.entries()) {
    try {
      const out = await translate(sourceLang, group.to, group.items.map(i => i.text))
      group.items.forEach((item, i) => {
        const text = (out?.[i] || '').trim()
        // 翻译请求在飞的时候商家可能已经手填了这一格：落地前再判一次
        if (!text || !writable(item.names, group.to)) return
        item.names[group.to] = text
        const mark = machineText.get(item.names) || {}
        mark[group.to] = text
        machineText.set(item.names, mark)
      })
      done.push(group.to)
    } catch (error) {
      failed.push(group.to)
      const reason = error?.message || ''
      if (reason && reason === lastError) {
        failed.push(...groups.slice(index + 1).map(g => g.to))
        break
      }
      lastError = reason
    }
  }
  return { done, failed }
}

/**
 * 引导式下单（下单向导）的类型和纯逻辑。页面在 `app/pages/configure.vue`。
 *
 * 设计：docs/superpowers/specs/2026-09-28-order-wizard-design.md
 *
 * ## ⚠ 这里的规则和后端 `OrderWizardService.resolve` 是同一套
 *
 * 显隐（showIf）、选项可选（needs）、必填、起订量——前台算一遍是为了**体验**
 * （灰掉、提示、提交前拦下），后端再算一遍才是**把关**（公网上的 JS 谁都能改）。
 * 两边对不上的表现是「页面上能提交、后端退回来一句报错」，所以改一边就要改另一边。
 *
 * 两条共同的约定：
 *   - 条件只引用**排在前面**的字段（后端 validateSchema 强制），所以从上往下一遍就能求出全部显隐；
 *   - 看不见的字段，答案当作没有（提交前 `prune` 掉，后端也会丢）。
 */

export interface WizardCond {
  field: string
  in: string[]
}

export interface WizardOption {
  code: string
  title: string
  desc: string
  badge: string
  icon: string
  image: string
  color: string
  needs: WizardCond[]
  /** 选了它时 goods 类字段数量合计的下限；0 = 没有 */
  moq: number
  /** 挂的商品编码（cards 选项），goods 类字段按它出型号表 */
  goods: string | null
  /** 选中时顺带改别的字段：{ plug: 'EU', certs: ['CE'] } */
  sets: Record<string, string | string[]>
}

export type WizardFieldType = 'cards' | 'options' | 'swatch' | 'number' | 'text' | 'date' | 'goods'

export interface WizardField {
  code: string
  type: WizardFieldType
  multi: boolean
  required: boolean
  title: string
  help: string
  hint: string
  showIf: WizardCond[]
  options?: WizardOption[]
  min?: number
  max?: number
  /** number 字段：这个数就是该商品的下单数量 */
  goods?: string | null
  /** goods 字段：从哪个选项字段取「选了哪些商品」 */
  source?: string
}

export interface WizardGroup {
  code: string
  title: string
  sub: string
  showIf: WizardCond[]
  fields: WizardField[]
}

export interface WizardGoods {
  code: string
  title: string
  unit: string
  image: string
  /** 单价（站点本币）。0 / null = 面议 */
  price?: number | null
  combos: Array<{ code: string; title: string; image?: string | null; price?: number | null }>
}

export interface Wizard {
  title: string
  lead: string
  groups: WizardGroup[]
  goods: Record<string, WizardGoods>
  /** goods 里单价的币种（站点本币）。老后端没有这个字段 */
  currency?: string
}

/** goods 字段的值：{ 商品编码: { 型号编码（没有型号用 ''）: 数量 } } */
export type GoodsQty = Record<string, Record<string, number>>
export type Answer = string | string[] | number | GoodsQty | undefined
export type Answers = Record<string, Answer>

const OPTION_TYPES = new Set(['cards', 'options', 'swatch'])
export const isOptionField = (f: WizardField) => OPTION_TYPES.has(f.type)

/** 一个字段当前选中的选项编码（单选也包成数组） */
export function picked(v: Answer): string[] {
  if (typeof v === 'string') return v ? [v] : []
  if (Array.isArray(v)) return v
  return []
}

export function met(conds: WizardCond[] | undefined, answers: Answers): boolean {
  return (conds || []).every(c => picked(answers[c.field]).some(v => c.in.includes(v)))
}

/**
 * 从上往下求一遍：哪些分组、字段看得见。
 *
 * ⚠ 求值时用的是「已经确认看得见的字段」的答案——看不见的字段就算答案还留在 answers 里，
 *   也不能拿来点亮后面的条件。后端是同一个口径（accepted 只收看得见的）。
 */
export function visibility(w: Wizard, answers: Answers) {
  const visible = new Set<string>()
  const groups = new Set<string>()
  const live: Answers = {}
  for (const g of w.groups) {
    if (!met(g.showIf, live)) continue
    let any = false
    for (const f of g.fields) {
      if (!met(f.showIf, live)) continue
      visible.add(f.code)
      any = true
      if (answers[f.code] !== undefined) live[f.code] = answers[f.code]
    }
    if (any) groups.add(g.code)
  }
  return { fields: visible, groups, live }
}

/** 这个选项现在能不能选（needs 满足没有）。不能选的显示成灰的，不是藏起来 */
export function optionOpen(o: WizardOption, live: Answers) {
  return met(o.needs, live)
}

/** 某个 goods 字段现在该列出哪些商品：来源字段里选中的、挂了商品的那些 */
export function goodsRows(w: Wizard, f: WizardField, answers: Answers): WizardGoods[] {
  const source = allFields(w).find(x => x.code === f.source)
  if (!source?.options) return []
  const rows: WizardGoods[] = []
  for (const code of picked(answers[source.code])) {
    const opt = source.options.find(o => o.code === code)
    const g = opt?.goods ? w.goods[opt.goods] : undefined
    if (g) rows.push(g)
  }
  return rows
}

export function allFields(w: Wizard): WizardField[] {
  return w.groups.flatMap(g => g.fields)
}

/** 所有看得见的 goods 字段里填的数量合计（起订量按它比） */
export function goodsTotal(w: Wizard, answers: Answers, visible: Set<string>): number {
  let total = 0
  for (const f of allFields(w)) {
    if (f.type !== 'goods' || !visible.has(f.code)) continue
    const allowed = new Set(goodsRows(w, f, answers).map(g => g.code))
    const v = answers[f.code] as GoodsQty | undefined
    for (const [code, cells] of Object.entries(v || {})) {
      if (!allowed.has(code)) continue
      for (const n of Object.values(cells)) total += Number(n) > 0 ? Math.floor(Number(n)) : 0
    }
  }
  return total
}

/**
 * 提交前的答案：只留看得见的字段；选项里剔掉灰着的；goods 只留来源里还选着的商品、数量 > 0 的格子。
 * 后端会再按同一口径复核一遍，这里剔干净只是为了不让一个过期的答案把整张单打回来。
 */
export function prune(w: Wizard, answers: Answers): Answers {
  const { fields, live } = visibility(w, answers)
  const out: Answers = {}
  for (const f of allFields(w)) {
    if (!fields.has(f.code)) continue
    const v = answers[f.code]
    if (v === undefined || v === '') continue
    if (isOptionField(f)) {
      const ok = picked(v).filter(code => {
        const o = f.options?.find(x => x.code === code)
        return !!o && optionOpen(o, live)
      })
      if (ok.length) out[f.code] = f.multi ? ok : ok[0]
    } else if (f.type === 'goods') {
      const allowed = new Set(goodsRows(w, f, answers).map(g => g.code))
      const cleaned: GoodsQty = {}
      for (const [code, cells] of Object.entries((v as GoodsQty) || {})) {
        if (!allowed.has(code)) continue
        const keep: Record<string, number> = {}
        for (const [combo, n] of Object.entries(cells)) {
          const q = Math.floor(Number(n))
          if (q > 0) keep[combo] = q
        }
        if (Object.keys(keep).length) cleaned[code] = keep
      }
      if (Object.keys(cleaned).length) out[f.code] = cleaned
    } else if (f.type === 'number') {
      const n = Number(v)
      if (Number.isFinite(n)) out[f.code] = Math.floor(n)
    } else {
      out[f.code] = v
    }
  }
  return out
}

export type IssueKind = 'required' | 'range' | 'moq' | 'noLines'
export interface Issue {
  field: string
  kind: IssueKind
  /** 起订量那条要的：哪个选项、下限多少、现在多少 */
  option?: string
  min?: number
  max?: number
  now?: number
}

/** 提交前检查。和后端 resolve 的顺序、口径一致（见文件头） */
export function check(w: Wizard, answers: Answers): Issue[] {
  const clean = prune(w, answers)
  const { fields } = visibility(w, answers)
  const issues: Issue[] = []
  let lines = 0
  for (const f of allFields(w)) {
    if (!fields.has(f.code)) continue
    const v = clean[f.code]
    if (f.type === 'number' && v !== undefined) {
      const n = Number(v)
      if (n < (f.min ?? 0) || n > (f.max ?? 9999999)) {
        issues.push({ field: f.code, kind: 'range', min: f.min ?? 0, max: f.max ?? 9999999 })
        continue
      }
      if (n > 0 && f.goods) lines++
    }
    if (f.type === 'goods' && v) lines += Object.keys(v as GoodsQty).length
    if (f.required && (v === undefined || (Array.isArray(v) && !v.length))) {
      issues.push({ field: f.code, kind: 'required' })
    }
  }
  const total = goodsTotal(w, clean, fields)
  for (const f of allFields(w)) {
    if (!fields.has(f.code) || !isOptionField(f)) continue
    for (const code of picked(clean[f.code])) {
      const o = f.options?.find(x => x.code === code)
      if (o && o.moq > 0 && total < o.moq) issues.push({ field: f.code, kind: 'moq', option: o.title, min: o.moq, now: total })
    }
  }
  if (!lines) issues.push({ field: '', kind: 'noLines' })
  return issues
}

/** 汇总栏 / 本地回执用的「字段：值」，按当前语言的文字 */
export function summaryRows(w: Wizard, answers: Answers, sep = ', ') {
  const clean = prune(w, answers)
  const rows: Array<{ group: string; field: string; value: string }> = []
  for (const g of w.groups) {
    for (const f of g.fields) {
      const v = clean[f.code]
      if (v === undefined) continue
      let value = ''
      if (isOptionField(f)) {
        value = picked(v).map(c => f.options?.find(o => o.code === c)?.title || c).join(sep)
      } else if (f.type === 'goods') {
        value = Object.entries(v as GoodsQty).map(([code, cells]) => {
          const goods = w.goods[code]
          const parts = Object.entries(cells).map(([combo, n]) => {
            const name = combo ? (goods?.combos.find(c => c.code === combo)?.title || combo) + ' ' : ''
            return `${name}×${n}`
          })
          return `${goods?.title || code}: ${parts.join(sep)}`
        }).join('; ')
      } else {
        value = String(v)
      }
      rows.push({ group: g.title, field: f.title, value })
    }
  }
  return rows
}

/**
 * 订单行（本地回执 + 预估总价用）：goods 字段的每个 商品×型号，加上挂了商品的数字字段。
 * `price` 是这一行的单价：选了型号按型号价，否则按商品价——和后端 repriceLines 同一个口径。
 */
export function orderLines(w: Wizard, answers: Answers) {
  const clean = prune(w, answers)
  const lines: Array<{ goods: WizardGoods; combo?: { code: string; title: string }; qty: number; price: number }> = []
  for (const f of allFields(w)) {
    const v = clean[f.code]
    if (v === undefined) continue
    if (f.type === 'number' && f.goods && Number(v) > 0 && w.goods[f.goods]) {
      const goods = w.goods[f.goods]!
      lines.push({ goods, qty: Number(v), price: Number(goods.price) || 0 })
    }
    if (f.type === 'goods') {
      for (const [code, cells] of Object.entries(v as GoodsQty)) {
        const goods = w.goods[code]
        if (!goods) continue
        for (const [combo, n] of Object.entries(cells)) {
          const c = goods.combos.find(x => x.code === combo)
          lines.push({
            goods,
            combo: c ? { code: c.code, title: c.title } : undefined,
            qty: n,
            price: Number(c ? c.price : goods.price) || 0
          })
        }
      }
    }
  }
  return lines
}

/**
 * 预估总价：有标价的行 单价×数量 相加；单价为 0 的行记作面议、不计入（和结算页、后端 inquireOnly 同口径）。
 *
 * ⚠ 只算**商品**。选项上的加价（logo、包装……）是预留字段，现在不计价——页面上要写明
 *   「定制项和运费在书面报价里」，否则买家会把这个数当成最终价。
 */
export function estimate(w: Wizard, answers: Answers) {
  let subtotal = 0
  let onRequest = 0
  let qty = 0
  for (const l of orderLines(w, answers)) {
    qty += l.qty
    if (l.price > 0) subtotal += l.price * l.qty
    else onRequest++
  }
  return { subtotal: Math.round(subtotal * 100) / 100, onRequest, qty }
}

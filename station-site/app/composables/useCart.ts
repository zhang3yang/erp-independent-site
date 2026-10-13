/**
 * 询价清单（购物车）。存 localStorage，没有后端。
 *
 * 从 kangwanjia-website-EN 的 composables/useCart.ts 搬来，改了三处：
 *
 *   1. **不预置示例商品**。上游会往空购物车里塞两件商品，让首次访客看到一个「有东西」
 *      的页面。那是演示站的做法；真站上这么干等于替买家做了决定，他结账时才发现
 *      多了两件没点过的货。
 *   2. **价格 0 = 面议**，不参与金额合计。外贸站大量商品是价格面议，
 *      上游那种「$0.00」的显示会被买家理解成免费或者站没维护。
 *   3. **带上起订量和单位**。清单里要能提示「起订 500 只」，不然买家填个 1
 *      发过来，商家还得回一封信告诉他起订量。
 *
 * ## 为什么不做「账号里的购物车」
 *
 * 这个站没有买家账号（docs/独立站与ERP的边界.md §6c：买家中心一期不实现）。
 * 清单在浏览器里，换设备就没了——页面上明说了这一点，见「我的询价」那页。
 */

import { MAX_QTY, cartLineKey, cartTotals, type CartItem } from '~/lib/shop'
import type { ProductCard, ProductCombo, ProductDetail } from '~/composables/useSite'
import { useProductDetail } from '~/composables/useSite'

export type { CartItem }

/**
 * ⚠ 一个部署一个站，localStorage 又是按源隔离的，所以不用把 siteKey 拼进键名。
 *   本地开发时几个站轮流跑在 localhost:3000 上会共用这一份——那只影响开发机。
 */
const STORAGE_KEY = 'station-cart'

export function useCart() {
  // useState 而不是模块级 ref：服务端一次请求一份，不会把 A 访客的清单渲染给 B
  const items = useState<CartItem[]>('cart-items', () => [])
  const restored = useState<boolean>('cart-restored', () => false)

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items.value))
    } catch {
      /* 隐私模式 / storage 被禁：这一次会话内还在内存里，够用 */
    }
  }

  /**
   * 只能在挂载后调（客户端）。
   *
   * 服务端和客户端首帧都渲染空清单，localStorage 在这之后才读——
   * 否则两边渲染出来的 DOM 不一样，hydration 会报 mismatch。
   */
  function restore() {
    if (restored.value) return
    restored.value = true
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw)
      if (!Array.isArray(parsed)) return
      // 挑掉坏行。老版本写的、或者写一半断电的数据不能进 UI
      items.value = parsed.filter(
        (i: unknown): i is CartItem =>
          !!i &&
          typeof (i as CartItem).code === 'string' &&
          typeof (i as CartItem).title === 'string' &&
          Number.isFinite((i as CartItem).price) &&
          Number.isFinite((i as CartItem).qty)
      )
    } catch {
      /* 坏 JSON：当成空清单，不要抛——抛了整页就白屏 */
    }
  }

  /**
   * 加进清单。同一个商品**加同一个型号**才合并数量；不同型号是两行——它们价格不同。
   *
   * 数量默认取起订量而不是 1：买家从详情页点「加入清单」，
   * 他要的必然不少于起订量，先填成起订量能省掉一次来回。
   *
   * @param combo 详情页上选中的型号。选了型号时**价格和缩略图都以型号为准**——
   *              清单里显示的必须是他选的那一款，否则结账时服务端一按型号重新定价，
   *              金额就变了，「提交前后不一样」最伤信任。
   */
  function add(product: ProductCard | ProductDetail, qty?: number, combo?: ProductCombo | null, lang?: string) {
    const moq = Math.max(1, Number(product.moq) || 1)
    addLine(
      {
        code: product.code,
        lang: lang || undefined,
        comboCode: combo?.code,
        comboName: combo?.title,
        title: product.title,
        subtitle: product.category || '',
        price: Number(combo ? combo.price : product.price) || 0,
        currency: product.currency || '',
        image: combo?.image || product.images?.[0] || '',
        moq,
        unit: product.unit || ''
      },
      qty ?? moq
    )
  }

  /**
   * 直接加一行。给「再询一次」用——那边手上是一张历史单的明细快照，
   * 不是一个商品对象（商品可能已经下架了，重新取一次不一定拿得到）。
   */
  function addLine(line: Omit<CartItem, 'qty'>, qty: number) {
    const want = clamp(qty)
    const key = cartLineKey(line)
    const existing = items.value.find(i => cartLineKey(i) === key)
    if (existing) {
      existing.qty = clamp(existing.qty + want)
    } else {
      items.value.push({ ...line, qty: want })
    }
    persist()
  }

  /**
   * 改某一行的数量。
   *
   * ⚠ 参数是 {@link cartLineKey} 算出来的**行标识**，不是商品编码。传编码进来的话，
   *   同一个商品的两个型号会被当成同一行——改一个，另一个跟着变。
   */
  function setQty(key: string, qty: number) {
    const row = items.value.find(i => cartLineKey(i) === key)
    if (!row) return
    // 减到 0 就是删掉这一行，而不是留一条数量为 0 的记录
    if (qty < 1) return remove(key)
    row.qty = clamp(qty)
    persist()
  }

  /** 删一行。参数同 {@link setQty}，是行标识不是商品编码 */
  function remove(key: string) {
    items.value = items.value.filter(i => cartLineKey(i) !== key)
    persist()
  }

  function clear() {
    items.value = []
    persist()
  }

  /**
   * 把清单里的商品名、型号名换成 `lang` 那种语言的（2026-09-22 起）。
   *
   * 清单存在 localStorage 里，标题是加入时那种语言的快照；买家切了语言，购物车页
   * 不能还是上一种语言的名字。这里按 code 重新取一遍详情，只换名字和单位——
   * 价格、数量不动（价格以结账时服务端重算为准，见 SiteOrderService#repriceLines）。
   *
   * 取不到（下架了、网络不通）就保留原来的，不会出空行。只在浏览器里跑：
   * 服务端渲染时清单本来就是空的。
   */
  async function localize(lang: string) {
    if (import.meta.server || !lang) return
    const stale = items.value.filter(i => i.lang !== lang)
    if (!stale.length) return
    const codes = [...new Set(stale.map(i => i.code))]
    await Promise.all(codes.map(async code => {
      let detail: ProductDetail
      try {
        detail = await useProductDetail(code, lang)
      } catch {
        return
      }
      for (const line of items.value) {
        if (line.code !== code) continue
        line.title = detail.title || line.title
        line.subtitle = detail.category || line.subtitle
        line.unit = detail.unit || line.unit
        if (line.comboCode) {
          const combo = (detail.combos || []).find(c => c.code === line.comboCode)
          if (combo?.title) line.comboName = combo.title
        }
        line.lang = lang
      }
    }))
    persist()
  }

  const totals = computed(() => cartTotals(items.value))
  /** 顶栏那个角标上的数字。用行数而不是件数——外贸的件数动辄上千，角标放不下 */
  const count = computed(() => items.value.length)

  return { items, count, totals, restore, add, addLine, setQty, remove, clear, localize, MAX_QTY }
}

function clamp(n: number) {
  return Math.min(Math.max(Math.floor(n) || 1, 1), MAX_QTY)
}

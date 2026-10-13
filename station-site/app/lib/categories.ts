/**
 * 商品分类的两级分组。
 *
 * ## 为什么是在前台拆字符串
 *
 * 库里 `u_s_sitegoods.sitecategory` 是**一列自由文本**，没有父子结构；
 * `/openapi/site/info` 下发的 `categories` 也就是一串平铺的字符串
 * （SiteGoodsMapper.selectPublicCategories：distinct 一列，按字典序）。
 *
 * 参考站 kangwanjia-website-EN 的商品页侧栏是两级的：大组「加压疗法」下面挂
 * 「腿部系统 / 袖套 / 躯干包裹」。要在不动库表、不动管理台的前提下拿到这个层级，
 * 约定用分隔符表达——商家把分类填成 `加压疗法/腿部系统`，前台按分隔符拆开。
 *
 * 取舍：
 *   · 零迁移。商家改几个分类名，侧栏立刻变成两级；筛选参数仍然是那个**完整**的
 *     字符串（`加压疗法/腿部系统`），后端 `and sg.sitecategory = #{category}`
 *     一个字都不用改。
 *   · 代价是分类名里不能出现分隔符。外贸分类里「阀门/管件」这种并列写法是有的，
 *     所以哪天真上了分类表，这个模块要**整个删掉**，不要两套并存。
 *
 * 认三种分隔符：半角 `/`、全角 `／`、`>`（有人习惯写「加压疗法 > 腿部系统」）。
 * 只按第一个分隔符拆：侧栏就两级，三级分类的后两段并进子项名里显示，
 * 比硬塞出第三层好读。
 */

/**
 * 分类的各语言显示名，来自 `/openapi/site/info` 的 `categoryNames`。
 *
 * 键是 **catkey**：分类路径按分隔符拆开后、用半角 `/` 逐级拼回去的前缀。
 * `加压疗法 > 腿部系统` 对应两个 key：`加压疗法` 和 `加压疗法/腿部系统`。
 *
 * ⚠ 拼 key 的规则必须和后端 `SiteCategoryService.catKeysOf` **逐字一致**：
 * 一律用半角 `/` 连接修剪过的段，不保留原串里的分隔符和空格。两边不一致的表现是
 * 「译名存进去了但前台查不到」——不报错，只是一直显示原文。
 */
export type CategoryNames = Record<string, string>

/** 侧栏里的一个子项。value 是传给接口的完整分类串，label 是当前语言的显示名 */
export interface CategoryItem {
  label: string
  value: string
}

export interface CategoryGroup {
  /**
   * 大组的**原文**，就是分隔符前面那段。
   *
   * ⚠ 这是**身份**：分组、`groupOf` 的比较、筛选链接里的 `?cat=` 全用它。
   * 要显示给人看的是 {@link label}。两者混用的表现是「切到日文后侧栏点不动了」——
   * 因为链接里带的是日文名，而后端按原串筛。
   */
  name: string
  /** 大组的显示名：当前语言的译名，没有译名就等于 {@link name} */
  label: string
  items: CategoryItem[]
  /**
   * 没带分隔符的分类（比如商家只填了「配件」）。
   *
   * 这种分类没有子项，组名本身就是筛选条件——标题这时候是一个链接而不是折叠开关，
   * 点了直接筛。刻意不给它造一个和组名同名的子项：侧栏里出现
   * 「配件 ▾ / └ 配件」比没有层级更难读。
   */
  value: string
}

const SEP = /\s*[/／>＞]\s*/

/** 拆成修剪过的各段。`加压疗法 > 腿部系统` → `['加压疗法', '腿部系统']` */
function segmentsOf(cat?: string | null): string[] {
  return String(cat || '').split(SEP).map(s => s.trim()).filter(Boolean)
}

/**
 * 第 `i` 段对应的 catkey：前 `i+1` 段用半角 `/` 拼起来。
 * ⚠ 规则要和后端 `SiteCategoryService.catKeysOf` 逐字一致，见 {@link CategoryNames}。
 */
function catKeyAt(parts: string[], i: number): string {
  return parts.slice(0, i + 1).join('/')
}

/** 第 `i` 段该显示的文字：有译名用译名，没有就用原文 */
function nameAt(parts: string[], i: number, names?: CategoryNames | null): string {
  return names?.[catKeyAt(parts, i)] || parts[i]!
}

/**
 * '加压疗法/腿部系统' → '腿部系统'；没分隔符时原样返回。列表页大标题和面包屑用它。
 *
 * 给了 `names` 就返回最后一段的译名。
 */
export function categoryLeaf(cat?: string | null, names?: CategoryNames | null): string {
  const parts = segmentsOf(cat)
  return parts.length ? nameAt(parts, parts.length - 1, names) : ''
}

/**
 * 平铺分类串 → 两级分组，顺序保持后端给的字典序。
 *
 * `names` 只影响**显示**（`group.label` 和 `item.label`）；分组、`group.name`、
 * `item.value` 全都还是原串——它们是身份和筛选键，翻了就点不动了。
 */
export function groupCategories(cats?: string[] | null, names?: CategoryNames | null): CategoryGroup[] {
  const groups: CategoryGroup[] = []
  const index = new Map<string, CategoryGroup>()

  for (const raw of cats || []) {
    const cat = String(raw || '').trim()
    if (!cat) continue

    const parts = segmentsOf(cat)
    if (!parts.length) continue

    const name = parts[0]!
    let group = index.get(name)
    if (!group) {
      group = { name, label: nameAt(parts, 0, names), items: [], value: '' }
      index.set(name, group)
      groups.push(group)
    }

    if (parts.length > 1) {
      // 三级以上的分类照旧并进子项名里显示，但每一段各取各的译名
      const label = parts.slice(1).map((_, k) => nameAt(parts, k + 1, names)).join(' / ')
      group.items.push({ label, value: cat })
    }
    else group.value = cat
  }

  // 「配件」和「配件/清洁包」同时存在：这时组名既是一个能筛的分类、又是一个大组。
  // 标题只能是二者之一，选折叠开关（有子项的组必须能展开），
  // 那个光秃秃的父分类降级成子项，否则它就点不到了。
  for (const g of groups) {
    if (g.value && g.items.length) {
      g.items.unshift({ label: g.label, value: g.value })
      g.value = ''
    }
  }

  return groups
}

/** 当前选中的分类属于哪个大组。找不到返回空串（侧栏就谁都不展开） */
export function groupOf(groups: CategoryGroup[], cat?: string | null): string {
  const v = String(cat || '').trim()
  if (!v) return ''
  for (const g of groups) {
    // 选中的就是大组本身（组名现在也能筛，后端按前缀匹配整个子树）
    if (g.name === v) return g.name
    if (g.value === v) return g.name
    if (g.items.some(i => i.value === v)) return g.name
  }
  return ''
}

/**
 * `cat` 是不是落在 `parent` 这棵子树里（含 parent 自己）。
 *
 * 判据要和后端 SiteGoodsMapper.xml 的 publicWhere 一致：相等，或者
 * 「以 parent 开头、且紧接着的那个字符是分隔符」。
 *
 * ⚠ 不能写成 `cat.startsWith(parent)`：那样 `Pumps` 会把 `Pumps & Valves` 也算进去，
 *   「探索其他产品」那一段就会漏掉本该出现的商品——不报错，只是少几张卡。
 *
 * 分隔符前面允许有空格（商家爱写「加压疗法 / 腿部系统」），所以是拿剩下那一截去匹配，
 * 不是只看紧挨着的那一个字符。后端那条 SQL 也做了同样的 trim。
 */
const SEP_HEAD = /^\s*[/／>＞]/

export function isUnder(cat?: string | null, parent?: string | null): boolean {
  const c = String(cat || '').trim()
  const p = String(parent || '').trim()
  if (!c || !p) return false
  if (c === p) return true
  return c.startsWith(p) && SEP_HEAD.test(c.slice(p.length))
}

/**
 * 起订量单位按语言显示。
 *
 * 商家在商品上填的单位（`u_s_sitegoods.unit`）是一个短串——pcs、件、set——库里**不分语言**，
 * 一列存一份。之前它原样上屏，于是日语站上也显示「件」或「pcs」。
 *
 * 这里做的是：认得出的（下面 ALIASES 那几十种写法）归成一个键，再按站点语言给显示名；
 * 认不出的原样返回——`1000pcs/ctn` 这种自由文本原样总比错译好。
 *
 * ⚠ 后端 `BuyerMailCopy.unit`（回执邮件里那一份）是同一张表，键名要保持一致。
 *   新增一种单位两边都要加。
 *
 * 为什么不把单位做成分语言的列：商家为每种语言填一次「件」没有价值，而且填漏了整页就混着两种。
 * 单位是有限集合，前台一张表就够。
 */

const ALIASES: string[][] = [
  ['pcs', 'pcs', 'pc', 'piece', 'pieces', '件', '个', '只', '枚', '颗', '支'],
  ['set', 'set', 'sets', '套'],
  ['pair', 'pair', 'pairs', '双', '对'],
  ['unit', 'unit', 'units', '台'],
  ['box', 'box', 'boxes', '盒'],
  ['carton', 'carton', 'cartons', 'ctn', 'ctns', '箱'],
  ['roll', 'roll', 'rolls', '卷'],
  ['bag', 'bag', 'bags', '袋'],
  ['pack', 'pack', 'packs', '包'],
  ['bottle', 'bottle', 'bottles', '瓶'],
  ['sheet', 'sheet', 'sheets', '张'],
  ['pallet', 'pallet', 'pallets', '托', '托盘'],
  ['dozen', 'dozen', 'doz', '打'],
  ['kg', 'kg', 'kgs', '千克', '公斤'],
  ['g', 'g', 'gram', 'grams', '克'],
  ['ton', 'ton', 'tons', 't', '吨'],
  ['m', 'm', 'meter', 'meters', 'metre', 'metres', '米'],
  ['cm', 'cm', '厘米'],
  ['mm', 'mm', '毫米'],
  ['l', 'l', 'liter', 'liters', 'litre', 'litres', '升'],
  ['ml', 'ml', '毫升'],
  ['sqm', 'sqm', 'm2', 'm²', '平方米', '平米']
]

const KEY_OF: Record<string, string> = {}
for (const row of ALIASES) {
  for (let i = 1; i < row.length; i++) KEY_OF[row[i]!.toLowerCase()] = row[0]!
}

const ORDER = [
  'pcs', 'set', 'pair', 'unit', 'box', 'carton', 'roll', 'bag', 'pack', 'bottle',
  'sheet', 'pallet', 'dozen', 'kg', 'g', 'ton', 'm', 'cm', 'mm', 'l', 'ml', 'sqm'
] as const

/** 每种语言一行，顺序和 ORDER 一致 */
const LABELS: Record<string, string[]> = {
  en: ['pcs', 'set', 'pair', 'unit', 'box', 'carton', 'roll', 'bag', 'pack', 'bottle',
       'sheet', 'pallet', 'dozen', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²'],
  zh: ['件', '套', '双', '台', '盒', '箱', '卷', '袋', '包', '瓶',
       '张', '托', '打', '千克', '克', '吨', '米', '厘米', '毫米', '升', '毫升', '平方米'],
  ja: ['個', 'セット', '組', '台', '箱', 'カートン', 'ロール', '袋', 'パック', '本',
       '枚', 'パレット', 'ダース', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²'],
  es: ['uds.', 'juego', 'par', 'unidad', 'caja', 'cartón', 'rollo', 'bolsa', 'paquete', 'botella',
       'hoja', 'palé', 'docena', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²'],
  pt: ['un.', 'conjunto', 'par', 'unidade', 'caixa', 'caixa de papelão', 'rolo', 'saco', 'pacote', 'garrafa',
       'folha', 'palete', 'dúzia', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²'],
  ru: ['шт.', 'компл.', 'пара', 'ед.', 'коробка', 'короб', 'рулон', 'мешок', 'упак.', 'бутылка',
       'лист', 'паллета', 'дюжина', 'кг', 'г', 'т', 'м', 'см', 'мм', 'л', 'мл', 'м²'],
  it: ['pz.', 'set', 'paio', 'unità', 'scatola', 'cartone', 'rotolo', 'sacco', 'confezione', 'bottiglia',
       'foglio', 'pallet', 'dozzina', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²'],
  ko: ['개', '세트', '쌍', '대', '박스', '카톤', '롤', '봉지', '팩', '병',
       '장', '팔레트', '다스', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²'],
  fr: ['pcs', 'jeu', 'paire', 'unité', 'boîte', 'carton', 'rouleau', 'sac', 'paquet', 'bouteille',
       'feuille', 'palette', 'douzaine', 'kg', 'g', 't', 'm', 'cm', 'mm', 'L', 'ml', 'm²']
}

/**
 * `localizeUnit('件', 'ja')` → `'個'`；`localizeUnit('1000pcs/ctn', 'ja')` → 原样。
 * 空值返回空串，调用方照旧用 `v-if` 判有没有。
 */
export function localizeUnit(raw: string | null | undefined, lang?: string): string {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  const key = KEY_OF[text.toLowerCase()]
  if (!key) return text
  const idx = ORDER.indexOf(key as (typeof ORDER)[number])
  const row = LABELS[lang || 'en'] || LABELS.en!
  return row[idx] || LABELS.en![idx] || text
}

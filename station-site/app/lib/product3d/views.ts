import type { ViewerView } from './framing'

/**
 * 展示台下面那排参考视角。
 *
 * ## 和上游的区别：这里只有角度，没有文案
 *
 * kangwanjia-website-EN 的 views.ts 里每个视角都带一句英文说明（「只有 62mm 厚，
 * 能平放进收纳袋」这种）——那是给它那一款按摩仪写的。这个站的商品来自各站自己的
 * 商品库，什么品类都有，写死的说明必然是错的。
 *
 * 所以这里只留几何：角度、缩放、看向哪。文案由调用方按站点语言给（`buildViews`），
 * 一站多语的翻译在 app/composables/useShopCopy.ts 里。
 *
 * `azimuth` 0 是正对模型正面，`elevation` 是离地面的仰角（度）。
 * `zoom` 乘在自动取景距离上，小于 1 就是推近特写；`target` 是相机看向的高度，
 * 取模型总高的比例。
 */

export const VIEW_KEYS = ['front', 'threeQuarter', 'side', 'rear', 'detail', 'top'] as const

export type ViewKey = (typeof VIEW_KEYS)[number]

const ANGLES: Record<ViewKey, Omit<ViewerView, 'label'>> = {
  front: { azimuth: 0, elevation: 8 },
  threeQuarter: { azimuth: 38, elevation: 15 },
  side: { azimuth: 90, elevation: 5 },
  rear: { azimuth: 180, elevation: 10 },
  // 唯一一个特写：推到 0.42 倍距离、看向偏下的位置，刻意裁掉边缘
  detail: { azimuth: 10, elevation: 6, zoom: 0.42, target: 0.26 },
  top: { azimuth: 0, elevation: 74 }
}

/** 带文案的视角列表。`label` 用来显示，`note` 是下面那行说明。 */
export interface LabelledView extends ViewerView {
  key: ViewKey
  note: string
}

/** 把角度和站点语言的文案拼起来。`label` / `note` 由调用方从文案表里取。 */
export function buildViews(
  label: (key: ViewKey) => string,
  note: (key: ViewKey) => string
): LabelledView[] {
  return VIEW_KEYS.map(key => ({ key, ...ANGLES[key], label: label(key), note: note(key) }))
}

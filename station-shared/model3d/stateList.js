/*
  模型状态列表的解析（不引 three）。

  页面要在 3D 组件加载之前就决定出不出状态按钮，而 states.js 引了 three（最重的那个包，
  只该在展示台进视口后才下）——所以纯解析单独放这里，states.js 再转出去。
  设计：docs/superpowers/specs/2026-10-08-product-3d-states-design.md
*/

/**
 * 接口下发的状态列表 → 能用的那几个。宽松：坏项丢掉；第一个是主模型（不需要 model），
 * 其余必须有 https:// 或 / 开头的模型地址。不足 2 个返回 null（不出按钮）。
 */
export function parseStates(value) {
  if (!Array.isArray(value)) return null
  const out = []
  const seen = new Set()
  value.forEach((s, i) => {
    if (!s || typeof s.id !== 'string' || seen.has(s.id)) return
    const name = typeof s.name === 'string' && s.name.trim() ? s.name.trim() : s.id
    if (i === 0) {
      out.push({ id: s.id, name })
      seen.add(s.id)
      return
    }
    const model = typeof s.model === 'string' ? s.model.trim() : ''
    if (!(model.startsWith('https://') || model.startsWith('/'))) return
    out.push({ id: s.id, name, model })
    seen.add(s.id)
  })
  // 第一个坏了就没有「主状态」可言
  if (!out.length || value[0]?.id !== out[0].id) return null
  return out.length < 2 ? null : out
}

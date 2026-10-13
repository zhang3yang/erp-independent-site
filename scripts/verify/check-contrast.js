/*
 * 主题配色的对比度自检（WCAG 2.1）。
 *
 * **从 app/assets/site.css 里真读 token**，不是另抄一份颜色——抄一份的话，
 * 有人改了主题而这里没跟着改，检查就会一直「通过」，而页面上的字已经看不清了。
 *
 * 为什么值得单独有一项：颜色是这个项目里最容易被「顺手调一下」的东西，
 * 而调深调浅没有任何报错。2026-09-21 换 Esva boots 主题时就踩到过一次——
 * logo 的电光蓝 #0196FA 在白底上只有 3.11:1，直接拿来当正文色是不合规的，
 * 所以主题里才分成 --brand-blue（只用在大字/图标/深底/按钮底）和
 * --primary（压暗到 4.5:1，正文和链接用）两个。这个脚本守的就是那条线。
 *
 *   node scripts/verify/check-contrast.js
 */
const fs = require('fs')

const css = fs.readFileSync('station-site/app/assets/site.css', 'utf8')

/** 从 :root 里取一个 token 的值。取不到就报错——不能默默当成黑色算过去 */
function token(name) {
  const m = css.match(new RegExp('--' + name + ':\\s*(#[0-9a-fA-F]{3,8})\\s*;'))
  if (!m) throw new Error('site.css 里找不到 --' + name + '（是不是改名或删了？）')
  return m[1]
}

const hex = h => {
  h = h.replace('#', '')
  if (h.length === 3) h = h.split('').map(c => c + c).join('')
  return [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16))
}
const lum = c => {
  const [r, g, b] = hex(c).map(v => {
    v /= 255
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m)
  return (x + 0.05) / (y + 0.05)
}

const T = {
  brandBlue: token('brand-blue'),
  primary: token('primary'),
  primaryDark: token('primary-dark'),
  primaryLight: token('primary-light'),
  accentSoft: token('accent-soft'),
  bg: token('bg'),
  surface: token('surface'),
  inkDark: token('ink-dark'),
  ink800: token('ink-800'),
  text: token('text'),
  textMuted: token('text-muted'),
  textSoft: token('text-soft'),
  textOnDark: token('text-on-dark'),
  border: token('border'),
  white: '#ffffff'
}

// [说明, 前景, 背景, 需要的比值]
// 4.5 = 正常字号正文；3 = 大字（≥24px，或 ≥18.7px 粗体）和图形
const CASES = [
  ['正文 on 页面底', T.text, T.bg, 4.5],
  ['正文 on 卡片', T.text, T.surface, 4.5],
  ['次要文字 on 卡片', T.textMuted, T.surface, 4.5],
  ['次要文字 on 页面底', T.textMuted, T.bg, 4.5],
  ['弱化小字 on 卡片', T.textSoft, T.surface, 4.5],
  ['弱化小字 on 页面底', T.textSoft, T.bg, 4.5],
  ['主按钮：近黑字 on logo 蓝', T.inkDark, T.brandBlue, 4.5],
  ['主按钮悬停：白字 on 深蓝', T.white, T.primaryDark, 4.5],
  ['链接 / 小标题 on 白', T.primary, T.surface, 4.5],
  ['链接 on 页面底', T.primary, T.bg, 4.5],
  ['浅蓝底上的正文', T.text, T.accentSoft, 4.5],
  ['深色区：白字 on 墨黑', T.white, T.inkDark, 4.5],
  ['深色区：说明文字 on 墨黑', T.textOnDark, T.inkDark, 4.5],
  ['深色区：浅蓝 on 墨黑', T.primaryLight, T.inkDark, 4.5],
  ['深色区：白字 on 次级底', T.white, T.ink800, 4.5],
  ['大标题强调半句：logo 蓝 on 白（≥30px 粗）', T.brandBlue, T.surface, 3],
  ['图标描边：主色 on 卡片', T.primary, T.surface, 3],
  ['分割线 on 卡片（纯装饰）', T.border, T.surface, 1.2]
]

let bad = 0
console.log('主题：' + Object.entries(T).map(([k, v]) => k + '=' + v).join(' '))
console.log()
for (const [name, fg, bg, need] of CASES) {
  const r = ratio(fg, bg)
  const ok = r >= need
  if (!ok) bad++
  console.log(`  ${ok ? 'ok  ' : '✗   '} ${r.toFixed(2).padStart(5)} : 1  (需 ${need})  ${name}`)
}

/* logo 原色是刻意不达标的那一个，这里顺便断言它**没有**被误当成正文色 */
const vivid = ratio(T.brandBlue, T.surface)
console.log()
console.log(`  说明：--brand-blue 在白底上是 ${vivid.toFixed(2)}:1，` +
  (vivid < 4.5 ? '达不到正文要求，所以主题里另有 --primary。' : '现在也够写正文了。') +
  ' 这不是错误。')

console.log(bad ? `\n✗ ${bad} 项不达标` : '\n全部达标')
process.exit(bad ? 1 : 0)

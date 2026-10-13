// 校验 03_init_decorate.sql 里那段 contentjson 字面量：SQL 转义还原后是不是合法 JSON
const fs = require('fs')

const file = process.argv[2]
const text = fs.readFileSync(file, 'utf8')
// UPDATE 里 contentjson 后面还有别的列，所以终止符是「引号 + 逗号 + 换行」
const m = text.match(/SET `contentjson` = '([\s\S]*?)',\r?\n/)
if (!m) { console.log('没匹配到 UPDATE 语句'); process.exit(1) }

const raw = m[1]
const SQ = String.fromCharCode(39)
const BS = String.fromCharCode(92)

// 裸单引号 / 转义单引号分别有几个
const escapedSq = (raw.match(new RegExp(BS + BS + SQ, 'g')) || []).length
const allSq = (raw.match(new RegExp(SQ, 'g')) || []).length
console.log('单引号总数', allSq, '其中前面带反斜杠的', escapedSq)

// MySQL 会先吃一层反斜杠转义：文件里的 \\n 到库里是 \n，文件里的 \' 到库里是 '
let json = raw.split(BS + BS).join(BS)
json = json.split(BS + SQ).join(SQ)

try {
  const o = JSON.parse(json)
  console.log('JSON 合法    v =', o.v)
  console.log('顶层键      ', Object.keys(o).join(', '))
  console.log('语言        ', Object.keys(o.i18n).join(', '))
  /*
   * 地图那几项只有配了 reach 的那份内容才有（03_init_decorate.sql 是这么一份）。
   * 这个校验器 2026-09-21 起也用来验别的装修脚本，那些可能整个关掉了全球布局——
   * 没有 reach 不是错，所以这里跳过，不是报错。
   */
  if (o.reach && o.reach.hq) {
    console.log('总部        ', o.reach.hq.name, JSON.stringify(o.reach.hq.coords))
    const dests = Array.isArray(o.reach.destinations) ? o.reach.destinations : []
    console.log('目的地      ', dests.length, '个')
    const bad = dests.filter(d =>
      !d.name || !Array.isArray(d.coords) || d.coords.length !== 2 ||
      Math.abs(d.coords[0]) > 180 || Math.abs(d.coords[1]) > 90)
    console.log('坐标越界    ', bad.length, bad.map(d => d.name).join(','))
  } else {
    console.log('全球布局    ', '这份没配（reach 缺失或关掉了）')
  }
  console.log('正文换行还原', o.i18n.en.about.content.includes('\n') ? '是' : '否 ⚠')
  console.log('英文段落数  ', o.i18n.en.about.content.split(/\n\s*\n/).length)
} catch (e) {
  console.log('JSON 不合法:', e.message)
  const pos = Number((e.message.match(/position (\d+)/) || [])[1])
  if (!Number.isNaN(pos)) {
    console.log('出错处上下文：', JSON.stringify(json.slice(Math.max(0, pos - 120), pos + 120)))
  }
  process.exit(1)
}

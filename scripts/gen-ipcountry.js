/**
 * 把 DB-IP 的 country-lite CSV 转成可以直接 SOURCE 的 SQL。
 *
 * 为什么要自己转、不用 LOAD DATA：线上 MySQL 的 secure_file_priv=NULL
 * 且 local_infile=0，两条 LOAD DATA 的路都被禁掉了，而放开它们要改配置 + 重启数据库。
 *
 * 为什么转成十六进制字面量、不在 SQL 里调 INET6_ATON：71 万行就是 143 万次函数调用，
 * 而且 INET6_ATON 对畸形输入返回 NULL 而不报错——那样错误会变成一批悄悄写进去的
 * NULL 行，事后极难发现。在这里转则可以对每一行做严格校验，有问题当场停。
 *
 * CSV 格式（无表头）：起始IP,结束IP,国家二字码
 */

const fs = require('fs')

/** IPv4 点分十进制 → 4 字节十六进制。认不出返回 null，绝不猜 */
function v4(s) {
  const p = s.split('.')
  if (p.length !== 4) return null
  let out = ''
  for (const x of p) {
    if (!/^\d{1,3}$/.test(x)) return null
    const n = Number(x)
    if (n > 255) return null
    out += n.toString(16).padStart(2, '0')
  }
  return out
}

/**
 * IPv6 → 16 字节十六进制。
 *
 * ⚠ 要处理三件事，少一件就会算错：
 *   1. `::` 压缩（可能出现在开头、结尾或中间）；
 *   2. 内嵌 IPv4 的写法 `::ffff:1.2.3.4`；
 *   3. 每组要补齐到 4 位十六进制。
 */
function v6(s) {
  let str = s
  // 内嵌 IPv4：把最后那段点分十进制换成两组十六进制
  const m = str.match(/^(.*:)(\d{1,3}(?:\.\d{1,3}){3})$/)
  if (m) {
    const h = v4(m[2])
    if (!h) return null
    str = m[1] + h.slice(0, 4) + ':' + h.slice(4)
  }

  const dbl = str.indexOf('::')
  let head, tail
  if (dbl >= 0) {
    head = str.slice(0, dbl) ? str.slice(0, dbl).split(':') : []
    tail = str.slice(dbl + 2) ? str.slice(dbl + 2).split(':') : []
  } else {
    head = str.split(':')
    tail = []
  }
  const missing = 8 - head.length - tail.length
  if (missing < 0) return null
  if (dbl < 0 && missing !== 0) return null

  const groups = [...head, ...Array(missing).fill('0'), ...tail]
  let out = ''
  for (const g of groups) {
    if (!/^[0-9a-fA-F]{1,4}$/.test(g)) return null
    out += g.toLowerCase().padStart(4, '0')
  }
  return out
}

// ---- 自检。改这个文件之前先让它跑过 ----
const CASES = [
  ['0.0.0.0', 4, '00000000'],
  ['255.255.255.255', 4, 'ffffffff'],
  ['8.8.8.8', 4, '08080808'],
  ['60.27.27.37', 4, '3c1b1b25'],
  ['::', 6, '00000000000000000000000000000000'],
  ['::1', 6, '00000000000000000000000000000001'],
  ['2001:db8::1', 6, '20010db8000000000000000000000001'],
  ['2001:0db8:0000:0000:0000:0000:0000:0001', 6, '20010db8000000000000000000000001'],
  ['fe80::', 6, 'fe800000000000000000000000000000'],
  ['::ffff:1.2.3.4', 6, '00000000000000000000ffff01020304'],
  ['2c0f:fff0:ffff:ffff:ffff:ffff:ffff:ffff', 6, '2c0ffff0ffffffffffffffffffffffff']
]
let selfFail = 0
for (const [input, ver, want] of CASES) {
  const got = ver === 4 ? v4(input) : v6(input)
  if (got !== want) {
    console.error(`自检失败: ${input} -> ${got}, 期望 ${want}`)
    selfFail++
  }
}
if (selfFail) {
  console.error(`${selfFail} 个自检用例没过，不生成 SQL`)
  process.exit(1)
}
console.error(`自检通过（${CASES.length} 个用例）`)

// ---- 转换 ----
const [, , inFile, outFile] = process.argv
const lines = fs.readFileSync(inFile, 'utf8').split(/\r?\n/)
const out = fs.createWriteStream(outFile)

out.write('-- 由 scripts 生成，勿手工编辑。数据来自 DB-IP country-lite（CC-BY）\n')
out.write('USE `sdsstation`;\n')
out.write('SET autocommit = 0;\n')
out.write('TRUNCATE TABLE `u_s_ip_country`;\n')

const BATCH = 2000
let buf = []
let n4 = 0, n6 = 0, skipped = 0

function flush() {
  if (!buf.length) return
  out.write('INSERT INTO `u_s_ip_country` (`ipver`,`ipstart`,`ipend`,`country`) VALUES\n')
  out.write(buf.join(',\n'))
  out.write(';\n')
  buf = []
}

for (const line of lines) {
  if (!line) continue
  const parts = line.split(',')
  if (parts.length !== 3) { skipped++; continue }
  const [s, e, c] = parts
  if (!/^[A-Za-z]{2}$/.test(c)) { skipped++; continue }

  const isV6 = s.includes(':')
  const hs = isV6 ? v6(s) : v4(s)
  const he = isV6 ? v6(e) : v4(e)
  if (!hs || !he) { skipped++; continue }

  buf.push(`(${isV6 ? 6 : 4},0x${hs},0x${he},'${c.toUpperCase()}')`)
  if (isV6) n6++; else n4++
  if (buf.length >= BATCH) flush()
}
flush()
out.write('COMMIT;\n')
out.write('SET autocommit = 1;\n')
out.end()

console.error(`IPv4 ${n4} 行, IPv6 ${n6} 行, 跳过 ${skipped} 行`)
if (skipped > 100) {
  console.error('⚠ 跳过的行太多，数据源格式可能变了，先看一眼再导')
  process.exit(1)
}

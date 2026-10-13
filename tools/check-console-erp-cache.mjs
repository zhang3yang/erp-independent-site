#!/usr/bin/env node
/**
 * console.erp.vclinker.com 静态资源「压缩 + 缓存」只读体检。
 *
 * 为什么有这个脚本：这次优化（2026-09-23）的全部收益都取决于 nginx 有没有真的
 * 用上磁盘上那些 .gz 文件。这件事在浏览器里看不出来——页面能打开，只是慢。
 * 而"改了 nginx 忘了 reload""gzip_static 模块没装""缓存头被内层 location 覆盖掉"
 * 这几种失败都表现为"看起来正常"，所以必须有一个能一眼看出对错的检查。
 *
 * 用法（在任意一台能访问 https://console.erp.vclinker.com 的机器上跑）：
 *
 *   node tools/check-console-erp-cache.mjs
 *   node tools/check-console-erp-cache.mjs --local RuoYi-Vue3/dist   # 再验字节一致性
 *
 * --local 会给出一张更硬的结论：把服务器发回来的东西解压后与本地构建产物逐字节
 * 比对（sha256）。它顺带能发现 Windows 上 sendfile/gzip_static 可能出现的截断——
 * 那类问题只看 Content-Length 是看不出来的。
 *
 * 全部通过退出码 0，任何一项不通过退出码 1。脚本只发 GET，不改任何东西。
 */
import { request } from 'node:https'
import { createHash } from 'node:crypto'
import { gunzipSync } from 'node:zlib'
import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const BASE = process.env.CONSOLE_ERP_BASE || 'https://console.erp.vclinker.com'

const argv = process.argv.slice(2)
const localIdx = argv.indexOf('--local')
const LOCAL_DIR = localIdx >= 0 ? argv[localIdx + 1] : null

/** 发一个 GET，返回原始字节和响应头；不做任何自动解压。 */
function get(path, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = request(BASE + path, { method: 'GET', headers }, res => {
      const chunks = []
      res.on('data', c => chunks.push(c))
      res.on('end', () => resolve({
        status: res.statusCode,
        headers: res.headers,
        body: Buffer.concat(chunks)
      }))
    })
    req.setTimeout(120000, () => req.destroy(new Error('timeout')))
    req.on('error', reject)
    req.end()
  })
}

const sha256 = b => createHash('sha256').update(b).digest('hex')
const kb = n => (n / 1024).toFixed(1) + 'KB'

// 服务器现在会给 index.html 也发 .gz（这正是我们要的），所以凡是"要读内容"的地方
// 都得先看 content-encoding 再决定解不解压——gzip 一开，明文假设就全失效了。
function bodyText(res) {
  const enc = res.headers['content-encoding'] || ''
  if (enc.includes('gzip')) {
    try { return gunzipSync(res.body).toString('utf8') } catch { return '' }
  }
  return res.body.toString('utf8')
}

const results = []
function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`)
}

console.log(`体检目标：${BASE}\n`)

// ---------------------------------------------------------------- 首屏
const login = await get('/login', { 'accept-encoding': 'gzip' })
check('/login 返回 200', login.status === 200, `status=${login.status}`)
check(
  '/login 明确不缓存（index.html 里写的是带 hash 的资源名，缓存住会白屏）',
  /no-cache|no-store|max-age=0/.test(login.headers['cache-control'] || ''),
  `cache-control: ${login.headers['cache-control'] || '（缺失）'}`
)

const html = bodyText(login)
const refs = [...new Set([...html.matchAll(/(?:src|href)="(\/static\/[^"]+\.(?:js|css))"/g)].map(m => m[1]))]
if (!refs.length) {
  console.log('❌ /login 里没解析出任何 /static/ 资源（html 长度 ' + html.length + '），后面的检查没法做')
  process.exit(1)
}
console.log(`     /login 传输 ${kb(login.body.length)}，解压后 ${kb(html.length)}`)

// HTTP/2：单纯"能打开页面"看不出来有没有生效，必须问协议本身。
// node 的 http2 客户端要求 ALPN 协商出 h2，协商不到会直接报错。
try {
  const { connect } = await import('node:http2')
  const client = connect(BASE)
  const ok = await new Promise(resolve => {
    client.once('error', () => resolve(false))
    const r = client.request({ ':path': '/login', 'accept-encoding': 'identity' })
    r.on('response', h => { r.close(); resolve(h[':status'] === 200) })
    r.on('error', () => resolve(false))
    r.end()
  })
  check('HTTP/2 已启用（ALPN 协商出 h2）', ok && client.alpnProtocol === 'h2', `alpn=${client.alpnProtocol || '（无）'}`)
  client.close()
} catch (e) {
  check('HTTP/2 已启用（ALPN 协商出 h2）', false, String(e && e.message || e))
}

console.log(`\n首屏引用 ${refs.length} 个资源：`)

// ---------------------------------------------------------------- 逐个静态资源
let wireTotal = 0
let rawTotal = 0
for (const ref of refs) {
  const name = ref.split('/').pop()
  console.log(`\n${name}`)

  const gz = await get(ref, { 'accept-encoding': 'gzip' })
  const identity = await get(ref, { 'accept-encoding': 'identity' })
  if (gz.status !== 200) {
    check(`${name} 可取`, false, `status=${gz.status}`)
    continue
  }

  const enc = gz.headers['content-encoding'] || ''
  const wire = gz.body.length
  const rawDist = identity.body.length
  wireTotal += wire
  rawTotal += rawDist

  check(
    `${name} 走了 gzip`,
    enc.includes('gzip'),
    `content-encoding: ${enc || '（无，说明 gzip_static 没生效）'}`
  )
  check(
    `${name} 未压缩时不误标 gzip`,
    !(identity.headers['content-encoding'] || '').includes('gzip'),
    `identity 请求 content-encoding: ${identity.headers['content-encoding'] || '（无）'}`
  )
  check(
    `${name} 强缓存（文件名带 content hash，可以 immutable）`,
    /immutable/.test(gz.headers['cache-control'] || '') && /max-age=\d{6,}/.test(gz.headers['cache-control'] || ''),
    `cache-control: ${gz.headers['cache-control'] || '（缺失）'}`
  )
  console.log(`     线上字节：未压缩 ${kb(rawDist)} / gzip ${kb(wire)}`)

  if (enc.includes('gzip')) {
    let plain
    try {
      plain = gunzipSync(gz.body)
    } catch (e) {
      check(`${name} gzip 能解开`, false, String(e.message))
      continue
    }
    // 解压出来的东西必须和「要求不压缩时服务器发的原始文件」逐字节一致。
    // 这一条是专门查 Windows 上 sendfile + gzip_static 把响应发坏/截断的：
    // 那种故障下 Content-Length 是对的、gzip 也解得开，只有内容是错的。
    check(
      `${name} gzip 解压后与原始文件逐字节一致`,
      sha256(plain) === sha256(identity.body),
      `${sha256(plain).slice(0, 12)} vs ${sha256(identity.body).slice(0, 12)}（${plain.length} vs ${rawDist} 字节）`
    )
    if (LOCAL_DIR) {
      const f = join(LOCAL_DIR, ref.replace(/^\//, ''))
      if (!existsSync(f)) {
        console.log(`     ⚠ 本地没有 ${f}（线上是别的构建？跳过与本地产物的比对）`)
      } else {
        const local = readFileSync(f)
        check(
          `${name} 与本地构建产物逐字节一致`,
          sha256(plain) === sha256(local),
          sha256(plain).slice(0, 12) + ' vs ' + sha256(local).slice(0, 12)
        )
      }
    }
  }
}

// ---------------------------------------------------------------- 账
console.log('\n=== 首屏传输账 ===')
console.log(`  实际下载 ${kb(wireTotal)}（未压缩则是 ${kb(rawTotal)}）`)
console.log(`  按 125 KB/s 估算：${(wireTotal / 1024 / 125).toFixed(1)} 秒（未压缩约 ${(rawTotal / 1024 / 125).toFixed(1)} 秒）`)
console.log('  参考：2026-09-23 改之前是 2256KB / 约 18 秒（线上实测 19 秒）')

const failed = results.filter(r => !r.ok)
console.log(`\n${failed.length ? '❌' : '✅'} ${results.length - failed.length}/${results.length} 项通过`)
if (failed.length) {
  console.log('\n没过的项及对应的改法见 deploy/nginx/erp/nginx.console-erp.conf 里的注释。')
  process.exit(1)
}

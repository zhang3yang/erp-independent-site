/**
 * 给商品图拼一个「缩到够用就行」的地址。
 *
 * ## 为什么要有它
 *
 * 商家在管理台上传的是原图：2026-09-25 看 esvafit 的 EB-BOOT-01，详情里是
 * 1600～3000px 的正方形 PNG，单张最大 3.3 MB，一页八张。页面上它最多显示 750px 宽，
 * 买家却要把原图整张下下来——在移动网络上就是一直转圈。
 *
 * 图放在腾讯云 COS 上（file.<主域名> 是桶的自定义域名），COS 自带图片处理：
 * 地址后面拼 `?imageMogr2/thumbnail/1500x>/format/webp`，同一张 3.3 MB 的图回来是 35 KB。
 *
 * ## 什么地址才拼
 *
 * 只认两类主机：`file.` 开头（本站自己的文件域名）和 `*.myqcloud.com`（COS 默认域名）。
 * 商家从别处粘来的外链（1688 的 alicdn 之类）不碰——别家的服务器不认这个参数会怎样，
 * 我们说不准。
 *
 * `file.` 那台如果不是 COS、而是 provider=local 的本机 nginx，这串查询参数会被
 * 静态文件服务直接忽略，回来的还是原图——多拼了也无害，只是省不下流量。
 *
 * 另外三种情况原样返回：
 *   · 相对路径（本机开发）；
 *   · 地址里已经带了查询参数（可能是商家自己配的处理参数，别叠上去）；
 *   · gif / svg —— 动图转 webp 会丢帧，矢量图缩放没有意义。
 *
 * ⚠ COS 图片处理超出免费额度后按量计费。真要关掉，让这个函数直接 `return url` 即可，
 *   调用方都有「处理失败回退原图」的兜底，不依赖它一定生效。
 */
export function resizedImage(url: string | null | undefined, width: number): string {
  const raw = String(url || '').trim()
  if (!raw) return ''
  let u: URL
  try {
    u = new URL(raw)
  } catch {
    return raw
  }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return raw
  if (!/^file\./i.test(u.hostname) && !/\.myqcloud\.com$/i.test(u.hostname)) return raw
  if (u.search) return raw
  if (!/\.(png|jpe?g|webp|bmp)$/i.test(u.pathname)) return raw
  // `>`（编码成 %3E）= 只缩不放：原图本来就比 width 小的不会被拉大
  return `${raw}?imageMogr2/thumbnail/${width}x%3E/format/webp`
}

/**
 * `<img>` 的 error 兜底：缩略图地址挂了（图片处理没开、格式不支持……）就换回原图。
 * 原图地址放在 `data-orig` 上。换过一次还挂，就不再管，交给调用方自己的占位逻辑。
 *
 * @returns 这次是否换回了原图——true 时调用方别再把它当成「图坏了」
 */
export function fallbackToOriginal(el: HTMLImageElement | null | undefined): boolean {
  const orig = el?.dataset?.orig
  if (!el || !orig || el.dataset.fellBack) return false
  el.dataset.fellBack = '1'
  el.src = orig
  return true
}

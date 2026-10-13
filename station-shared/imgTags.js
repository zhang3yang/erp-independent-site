/**
 * 富文本里 `<img>` 标签的几个纯函数。RichTextField（插图时查重）和商品页
 * （按语言同步插图 / 删图）共用，所以放在这里而不是各写一份正则。
 *
 * 只处理 RichTextField 自己产出的那种形状 `<img src="…" alt="">`，以及商家手写的
 * 单双引号 src。不解析成 DOM：两个宿主都在浏览器里，但这几个函数要能在
 * computed / 单元测试里直接跑，纯字符串最省事。
 */

const IMG_RE = /<img\b[^>]*>/gi

/** 一个 `<img …>` 标签的 src，取不到返回空串 */
export function srcOf(tag) {
  const m = String(tag || '').match(/\bsrc\s*=\s*(["'])(.*?)\1/i)
  return m ? m[2].trim() : ''
}

/** 正文里所有图片的 src，按出现顺序，含重复 */
export function imgSrcs(html) {
  return (String(html || '').match(IMG_RE) || []).map(srcOf).filter(Boolean)
}

/** 前后各留一个换行：源码里块级内容各占一行，回头人肉看得懂（和 RichTextField 的 block 一致） */
export function imgBlock(urls) {
  const list = (urls || []).filter(Boolean)
  if (!list.length) return ''
  return `\n${list.map(url => `<img src="${url}" alt="">`).join('\n')}\n`
}

/**
 * 删掉正文里 src 等于 `src` 的**第一张**图。
 *
 * 图独占一行时连同那一行一起删，不留空行；和别的内容写在同一行时只删标签本身，
 * 不吃掉同一行的文字（和 RichTextField.removeImageAtCaret 同一条规矩）。
 *
 * @returns {{ html: string, removed: boolean }}
 */
export function removeImgBySrc(html, src) {
  const text = String(html || '')
  const want = String(src || '').trim()
  if (!want) return { html: text, removed: false }
  IMG_RE.lastIndex = 0
  let m
  while ((m = IMG_RE.exec(text))) {
    if (srcOf(m[0]) !== want) continue
    let from = m.index
    let to = from + m[0].length
    const lineStart = text.lastIndexOf('\n', from - 1) + 1
    const nl = text.indexOf('\n', to)
    const lineEnd = nl < 0 ? text.length : nl
    if (text.slice(lineStart, from).trim() === '' && text.slice(to, lineEnd).trim() === '') {
      from = lineStart
      to = nl < 0 ? text.length : nl + 1
    }
    IMG_RE.lastIndex = 0
    return { html: text.slice(0, from) + text.slice(to), removed: true }
  }
  return { html: text, removed: false }
}

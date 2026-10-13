/**
 * 买家头像：六个内置色块、首字母字块、以及「把选中的图裁成 256px」。
 *
 * 从 kangwanjia-website-EN 的 composables/useProfile.ts 里那一段搬来，改了一处：
 *
 * ## ⚠ 头像存**服务端**，不存 localStorage
 *
 * 上游是个纯前端 stub，头像存在 localStorage 里，页面上那句提示写着
 * 「nothing is uploaded anywhere」。照搬会和这个站的登录自相矛盾——
 * 买家账号的全部意义就是「换台电脑还在」，而 localStorage 里的头像换台电脑就没了。
 *
 * 所以走 `POST /openapi/site/account/avatar` 落到 `u_s_buyer.avatar`，
 * 前台那句提示也跟着改成了实话。裁剪和缩放仍然**在浏览器里做**（下面的
 * `readAvatarFile`），那一半上游是对的：一张 5MB 的原图传上去，服务端还得再压一次。
 *
 * ## 两种取值
 *
 *   `preset:<id>`        挑了色块，画一个渐变底 + 首字母
 *   `data:image/jpeg;…`  传了图，256px 的 JPEG
 *   `null`               什么都没设，画纯色底 + 首字母
 *
 * ⚠ 色块 id 的白名单**后端也有一份**（`SiteBuyerService.AVATAR_PRESETS`），
 *   两处要一起改。后端那份是真正的闸——这一列的值会被直接放进 `<img src>`，
 *   而且会显示在管理台的客户列表上。
 */

/** 六个内置色块。⚠ id 和后端 SiteBuyerService.AVATAR_PRESETS 一一对应 */
export const AVATAR_PRESETS = [
  { id: 'lilac', from: '#b79ce0', to: '#6c4b9c' },
  { id: 'rose', from: '#f0b6d2', to: '#c05c8e' },
  { id: 'teal', from: '#8fd4cd', to: '#2f7f77' },
  { id: 'amber', from: '#f6cf8e', to: '#c07f22' },
  { id: 'slate', from: '#a9b2c6', to: '#4a566e' },
  { id: 'moss', from: '#aed69a', to: '#4d7a3a' }
] as const

export function presetById(id: string) {
  return AVATAR_PRESETS.find(p => p.id === id) ?? null
}

/** 存下来的头像最长边，px。再大没有意义——最大的显示位置也只有 96px */
export const AVATAR_SIZE = 256

/**
 * 首字母字块的字。
 *
 * 「Alex Chen」→「AC」，「张竞扬」→「张竞」。
 *
 * ⚠ 用 `Array.from` 取字符不用 `slice`：`slice(0, 2)` 按 UTF-16 码元切，
 *   碰上 emoji 或者某些生僻字会把一个字符劈成两半，渲染出一个「�」。
 */
export function initialsOf(name?: string | null) {
  const trimmed = (name || '').trim()
  if (!trimmed) return '?'
  const parts = trimmed.split(/\s+/)
  const letters = parts.length > 1 && parts[0] && parts[parts.length - 1]
    ? (Array.from(parts[0]!)[0] || '') + (Array.from(parts[parts.length - 1]!)[0] || '')
    : Array.from(trimmed).slice(0, 2).join('')
  return letters.toUpperCase()
}

/**
 * 读一个图片文件，从中间裁成正方形、缩到 {@link AVATAR_SIZE}，返回 JPEG 的 data URL。
 *
 * 为什么要在浏览器里裁：手机拍的原图动辄 3～5MB，直接传上去服务端还得再压一次，
 * 而且中间那一程是白白占带宽的。裁完落地十几到几十 KB。
 *
 * ⚠ 只在客户端调（用到 `Image` 和 `canvas`）。
 */
export function readAvatarFile(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('image/')) {
      return reject(new Error('not-an-image'))
    }
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      const side = Math.min(img.naturalWidth, img.naturalHeight)
      const canvas = document.createElement('canvas')
      canvas.width = AVATAR_SIZE
      canvas.height = AVATAR_SIZE
      const ctx = canvas.getContext('2d')
      if (!ctx) return reject(new Error('no-canvas'))
      ctx.drawImage(
        img,
        (img.naturalWidth - side) / 2,
        (img.naturalHeight - side) / 2,
        side, side,
        0, 0, AVATAR_SIZE, AVATAR_SIZE
      )
      // 0.82 是体积和观感的平衡点，256px 下再高看不出差别，只是把 base64 撑大
      resolve(canvas.toDataURL('image/jpeg', 0.82))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('unreadable'))
    }
    img.src = url
  })
}

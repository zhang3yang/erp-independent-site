import { isExternal } from '@/utils/validate'

/**
 * 商品主图的原始地址：优先用缩略图，退回主图。
 *
 * 上传有两种模式（见 InventoryGoodsController.uploadImage）：开了 OSS 会同时生成
 * 主图和缩略图；没开 OSS 走本地上传，只有主图没有缩略图。所以两个字段都要兜。
 *
 * 返回的是后端存的原始值，没有拼接前缀。要交给 <image-preview> 的用这个，
 * 因为该组件内部会自己做前缀拼接，传已拼好的地址会被拼两次。
 */
export function goodsImageSource(goods) {
  return goods?.thumbUrl || goods?.imgUrl || ''
}

/**
 * 把原始地址转成可以直接塞进 <el-image> 的 URL。
 * OSS 返回的是完整外链，原样返回；本地上传返回的是相对路径，补上接口前缀。
 * 逗号分隔的多图只取第一张。
 */
export function resolveImageUrl(url) {
  if (!url) return ''
  const first = String(url).split(',')[0].trim()
  if (!first) return ''
  return isExternal(first) ? first : import.meta.env.VITE_APP_BASE_API + first
}

/** 商品主图可直接渲染的 URL，没有图时返回空串 */
export function goodsImageUrl(goods) {
  return resolveImageUrl(goodsImageSource(goods))
}

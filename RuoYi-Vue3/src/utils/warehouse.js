/**
 * 仓库下拉选项的统一显示文案。
 *
 * u_m_warehouse 允许同名仓库（例如两个都叫"天津仓库"，编码分别是 CK20GZELO902 和
 * CK20GZEQ5802），下拉只显示 wareName 时用户无法区分，选错仓库会在过账阶段才暴露
 * 成"库存数据已被其他人修改"这类难以定位的错误。因此选项一律带上编码。
 */
export function warehouseLabel(warehouse) {
  if (!warehouse) return ''
  const name = String(warehouse.wareName || '').trim()
  const code = String(warehouse.wareCode || '').trim()
  if (!code) return name
  if (!name) return code
  return `${name}（${code}）`
}
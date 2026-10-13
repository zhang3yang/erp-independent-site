import { computed } from 'vue'
import { checkPermi } from '@/utils/permission'
import useUserStore from '@/store/modules/user'

/**
 * 金额按岗位可见：页面上该不该渲染这一列。
 *
 * 见 docs/金额按岗位可见.md。两个权限点一一对应四种岗位：
 *
 *   销售 → 只有 price:sale     采购 → 只有 price:cost
 *   仓库 → 两个都没有           财务 → 两个都有
 *
 * ⚠ 这里只负责「别在页面上留一排空格子」，不承担任何安全职责。真正的拦截在服务端
 *   序列化出口（@PriceField），无权的人拿到的值本来就是 null——前端改不改都一样。
 *   所以这里用不着防绕过，也不要把它当成一道防线。
 *
 * ⚠ 藏单价就要连合计一起藏。一张单只有一行的时候，合计就是单价，
 *   只藏单价等于没藏（方案第 6.3 节）。汇总行和 el-descriptions 里的金额项
 *   同样要处理，不能只处理 el-table-column。
 */

/** 当前用户能不能看到采购价与库存/生产成本。 */
export const canSeeCost = computed(
  () => useUserStore().platformAdmin || checkPermi(['price:cost'])
)

/** 当前用户能不能看到销售价与商品参考价。 */
export const canSeeSale = computed(
  () => useUserStore().platformAdmin || checkPermi(['price:sale'])
)

/**
 * 两样都看得到才为真 —— 口径不明的金额按最严的走，与服务端 PriceScope.AUTO 判不出时一致。
 */
export const canSeeBothPrices = computed(() => canSeeCost.value && canSeeSale.value)

/**
 * 任一侧看得到就为真。
 *
 * 给「一张列表里混着两种口径」的列用——出库列表同时有销售出库（销售额）和领料、
 * 报废（成本额）。服务端已经<b>逐行</b>把无权的那些行写成 null 了，这里整列藏掉
 * 反而会把他有权看的那几行也一起藏了，所以只要有一侧权限就把列留着。
 */
export const canSeeEitherPrice = computed(() => canSeeCost.value || canSeeSale.value)

/**
 * 按单据类型判断主表总金额算哪一侧，与服务端 DocumentMain.resolvePriceScope 同口径。
 *
 * 详情页知道自己是哪种单，可以用它判得更准；列表页混着多种单据，用 canSeeEitherPrice。
 *
 * @param {string} docType 单据类型，销售出库是 'CK'
 */
export function canSeeDocTotalAmount(docType) {
  if (!docType) {
    return canSeeBothPrices.value
  }
  return docType === 'CK' ? canSeeSale.value : canSeeCost.value
}

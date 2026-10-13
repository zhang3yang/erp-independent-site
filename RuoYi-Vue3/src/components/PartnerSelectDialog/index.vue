<!--
  选择供应商对话框。

  替掉原来的下拉框：供应商多起来之后，下拉框里几十个名字长得差不多，很容易选错，
  而且列表是一次性拉回来的（原来还硬截断在 100 条），翻找和搜索都不好使。

  两个页签：
    推荐供应商 —— 本单已添加的商品，历史上从哪几家进过货。按「覆盖的商品数」
                 再按「最近一次入库日期」排，第一行就是最可能的那家。
                 数据来自已审核采购入库单累积的供货关系表，第一次入库的商品
                 查不到记录，这个页签就是空的，转去「全部供应商」自己选。
    全部供应商 —— 分页 + 一个输入框同时按编码和名称模糊搜，只列启用的。

  单选。停用的供应商只会出现在推荐页签里（历史事实不因停用而消失），标出来但不给选。

  用法：
    <partner-select-dialog ref="partnerSelectRef" :goods="items" :selected-code="form.partnerCode"
      @confirm="onPartnerSelected" />
    proxy.$refs['partnerSelectRef'].open()
-->
<template>
  <el-dialog title="选择供应商" v-model="visible" width="80%" top="6vh" append-to-body
    :close-on-click-modal="false" class="partner-select-dialog">
    <el-tabs v-model="activeTab">
      <el-tab-pane name="recommend">
        <template #label>
          <span>推荐供应商<el-badge v-if="recommendRows.length" :value="recommendRows.length" class="tab-badge" /></span>
        </template>

        <el-alert v-if="!goodsCodes.length" type="info" show-icon :closable="false"
          title="先在下面的明细里添加商品，这里才能按商品的进货历史推荐供应商" />
        <el-alert v-else-if="!recommendLoading && !recommendRows.length" type="info" show-icon :closable="false"
          title="本单商品都没有已审核的入库记录，没有可推荐的供应商"
          description="第一次进这批货就是这样，请到「全部供应商」页签里自己选。选完入库单审核通过后，下次进同样的货就会出现在这里。" />

        <el-table v-else v-loading="recommendLoading" :data="recommendRows" border stripe height="420"
          :row-class-name="recommendRowClass" @row-click="chooseRecommend">
          <el-table-column width="50" align="center">
            <template #default="scope">
              <el-radio :model-value="chosenCode" :value="scope.row.partnerCode"
                :disabled="scope.row.disabled" @click.stop="chooseRecommend(scope.row)">
                <span></span>
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="200" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.partnerName }}</span>
              <el-tag v-if="scope.row.disabled" type="info" size="small" effect="plain" class="row-tag">已停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="编码" prop="partnerCode" width="140" align="center" show-overflow-tooltip />
          <el-table-column label="供过本单哪些商品" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.goodsLabel }}</span>
              <el-tag v-if="scope.row.goodsCount === goodsCodes.length && goodsCodes.length > 1"
                type="success" size="small" effect="plain" class="row-tag">全覆盖</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近入库" width="120" align="center">
            <template #default="scope">{{ parseTime(scope.row.lastDocDate, '{y}-{m}-{d}') || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="canSeeCost" label="最近成本单价" width="130" align="right">
            <template #default="scope">
              <span v-if="scope.row.lastCostPrice === null">-</span>
              <span v-else>¥{{ formatDecimal(scope.row.lastCostPrice, 2, 4) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库次数" prop="inboundTimes" width="100" align="center" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="全部供应商" name="all">
        <div class="partner-select-toolbar">
          <el-input v-model="keyword" placeholder="输入供应商编码或名称模糊查询" clearable size="small"
            style="width: 280px" @keyup.enter="handleSearch" @clear="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" size="small" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button size="small" icon="Refresh" @click="handleReset">重置</el-button>
          <span class="toolbar-hint">只列启用的供应商，停用的请先到「库存管理 → 基础设置 → 往来单位」启用</span>
        </div>

        <el-table v-loading="loading" :data="partnerList" border stripe height="420"
          empty-text="没有符合条件的供应商" @row-click="chooseAll">
          <el-table-column width="50" align="center">
            <template #default="scope">
              <el-radio :model-value="chosenCode" :value="scope.row.partnerCode"
                @click.stop="chooseAll(scope.row)">
                <span></span>
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column label="编码" prop="partnerCode" width="140" align="center" show-overflow-tooltip />
          <el-table-column label="供应商名称" prop="partnerName" min-width="220" show-overflow-tooltip />
          <el-table-column label="联系人" prop="contactPerson" width="110" align="center">
            <template #default="scope">{{ scope.row.contactPerson || '-' }}</template>
          </el-table-column>
          <el-table-column label="手机号" prop="mobile" width="140" align="center">
            <template #default="scope">{{ scope.row.mobile || scope.row.telephone || '-' }}</template>
          </el-table-column>
          <el-table-column label="地址" prop="address" min-width="200" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.address || '-' }}</template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize" :page-sizes="[10, 20, 50]" :auto-scroll="false"
          @pagination="getList" />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="dialog-footer">
        <span class="footer-chosen">
          <template v-if="chosen">已选：<b>{{ chosen.partnerName }}</b>（{{ chosen.partnerCode }}）</template>
          <template v-else>请选择一家供应商</template>
        </span>
        <el-button type="primary" :disabled="!chosen" @click="handleConfirm">确 定</el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="PartnerSelectDialog">
import { computed, ref, watch } from 'vue'
import { parseTime } from '@/utils/ruoyi'
import { listPartner, recommendPartners } from '@/api/inventory/partner'
import { groupPartnerRecommend } from '@/utils/goodsPartner'
import { formatDecimal } from '@/utils/decimal'
import { canSeeCost } from '@/utils/price'


const props = defineProps({
  /**
   * 本单已添加的商品，用来算推荐。元素取 goodsCode 和 goodsName 两个字段，
   * 直接把明细行数组传进来即可。
   */
  goods: { type: Array, default: () => [] },
  /** 当前已选的供应商编码，打开弹窗时预选上 */
  selectedCode: { type: String, default: '' }
})

const emit = defineEmits(['confirm'])

const visible = ref(false)
const activeTab = ref('recommend')
const loading = ref(false)
const recommendLoading = ref(false)
const partnerList = ref([])
const total = ref(0)
const keyword = ref('')
const recommendRows = ref([])
const chosen = ref(null)

const queryParams = ref({
  pageNum: 1,
  pageSize: 20
})

const chosenCode = computed(() => chosen.value?.partnerCode || '')

const goodsCodes = computed(() => {
  const codes = new Set()
  ;(props.goods || []).forEach(item => {
    if (item?.goodsCode) codes.add(item.goodsCode)
  })
  return [...codes]
})

const goodsNameMap = computed(() => {
  const map = {}
  ;(props.goods || []).forEach(item => {
    if (item?.goodsCode) map[item.goodsCode] = item.goodsName || item.goodsCode
  })
  return map
})

/** 打开弹窗：每次都重新算推荐，并把当前已选的供应商预选上 */
function open() {
  visible.value = true
  chosen.value = null
  keyword.value = ''
  queryParams.value.pageNum = 1
  getList()
  loadRecommend()
}

/**
 * 按商品查历史供应商，归并成「这家供过本单哪几个商品」。
 * 归并和排序规则见 @/utils/goodsPartner 的 groupPartnerRecommend。
 */
function loadRecommend() {
  recommendRows.value = []
  if (!goodsCodes.value.length) {
    // 还没添加商品，推荐页签只有一句说明，别让人停在死胡同里
    activeTab.value = 'all'
    return
  }
  recommendLoading.value = true
  recommendPartners(goodsCodes.value).then(response => {
    recommendRows.value = groupPartnerRecommend(response.data, goodsNameMap.value)

    // 没有推荐就直接停在「全部供应商」，省一次点击
    if (!recommendRows.value.length) activeTab.value = 'all'
    else activeTab.value = 'recommend'

    // 预选当前已选的那家；不在推荐里就不预选，避免在「全部」页签里翻页去找
    const current = recommendRows.value.find(
      item => item.partnerCode === props.selectedCode && !item.disabled)
    if (current) chosen.value = { partnerCode: current.partnerCode, partnerName: current.partnerName }
  }).catch(() => {
    recommendRows.value = []
    activeTab.value = 'all'
  }).finally(() => {
    recommendLoading.value = false
  })
}

function getList() {
  loading.value = true
  listPartner({
    pageNum: queryParams.value.pageNum,
    pageSize: queryParams.value.pageSize,
    partnerType: 'supplier',
    // 停用的供应商不该被开进新单据
    activeFlag: 1,
    keyword: keyword.value || undefined
  }).then(response => {
    partnerList.value = response.rows || []
    total.value = response.total || 0
  }).catch(() => {
    partnerList.value = []
    total.value = 0
  }).finally(() => {
    loading.value = false
  })
}

function handleSearch() {
  queryParams.value.pageNum = 1
  getList()
}

function handleReset() {
  keyword.value = ''
  queryParams.value.pageNum = 1
  getList()
}

function recommendRowClass({ row }) {
  return row.disabled ? 'row-disabled' : ''
}

function chooseRecommend(row) {
  if (row.disabled) return
  chosen.value = { partnerCode: row.partnerCode, partnerName: row.partnerName }
}

function chooseAll(row) {
  chosen.value = { partnerCode: row.partnerCode, partnerName: row.partnerName }
}

function handleConfirm() {
  if (!chosen.value) return
  emit('confirm', { ...chosen.value })
  visible.value = false
}

// 明细里的商品在弹窗打开期间被改动（不太可能，但父组件是同一个数组）时重算推荐
watch(goodsCodes, () => {
  if (visible.value) loadRecommend()
})

defineExpose({ open })
</script>

<style lang="scss" scoped>
.partner-select-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .toolbar-hint {
    margin-left: auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.row-tag {
  margin-left: 6px;
}

.tab-badge {
  margin-left: 6px;
  vertical-align: middle;
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.footer-chosen {
  margin-right: auto;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

:deep(.el-table__row) {
  cursor: pointer;
}

/* 停用的供应商整行置灰且不可点。斑马纹加在单元格上，背景色也要写到单元格。 */
:deep(.el-table__row.row-disabled) {
  cursor: not-allowed;
}

:deep(.el-table__row.row-disabled td.el-table__cell) {
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
}

/* 单选列只要那个圆点，不要 el-radio 默认留给文字的右边距 */
:deep(.el-radio) {
  margin-right: 0;

  .el-radio__label {
    display: none;
  }
}
</style>

<style lang="scss">
/*
  append-to-body 渲染到 body 下，scoped 选择器匹配不到弹窗根元素，这段不能加 scoped。
  固定宽度让表格列一次排开、不出横向滚动条；窄屏收成视口宽度。
*/
.partner-select-dialog {
  max-width: min(1400px, calc(100vw - 32px));

  .el-dialog__body {
    padding: 8px 24px 16px;
  }
}
</style>

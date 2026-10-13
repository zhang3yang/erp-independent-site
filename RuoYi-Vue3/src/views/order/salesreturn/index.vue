<!--
  销售退货。客户把已发出的货退回来，走 XSTHRK 入库单还货、还成本。

  与销售补发同一套节奏：主页面是退货记录，「新增退货」建单，记录行上再点「提交退货」才入库。
  建单只把可退数量占住（可退量把待提交单算进已退），货要提交那一刻才回库存池。

  与采购退货对称：那边是已入库后退给供应商（出库），这边是已发货后客户退回来（入库）。
  退货不新建业务表，事实落在库存单据上，靠来源单号关联回订单。

  两件容易被问到的事，页面上直接写出来：
  - 入库成本按<b>原出库的实际成本</b>算，不是订单售价。用售价入库会把毛利算进库存。
  - 退货<b>不回冲</b>订单的已发数量，订单不会从「已完成」退回「发货中」。
-->
<template>
  <div class="app-container">
    <el-alert type="warning" show-icon :closable="false" class="mb8"
      title="退货分两步：先新增退货单，再在记录上点「提交退货」，提交那一刻货才入库"
      description="成本按当初出库的实际成本还回库存池，不按订单售价。未提交的退货单已占住可退数量，不退了请删除。退货不改变订单的已发数量与履约状态。" />

    <!-- 待提交的单占着可退数量，跨月之后按月份查是看不到的，所以单独提醒并给一键查看 -->
    <el-alert v-if="pendingRows.length" type="error" show-icon :closable="false" class="mb8"
      :title="`有 ${pendingRows.length} 张退货单还没提交，正占用着可退数量`">
      <template #default>
        <span class="tracking-hint">这些单分布在 {{ pendingMonths }}，按月份查看容易漏掉。</span>
        <el-button link type="primary" @click="togglePending">
          {{ pendingMode ? '返回按月份查看' : '查看这些单' }}
        </el-button>
      </template>
    </el-alert>

    <el-form v-show="!pendingMode" :model="query" :inline="true" class="mb8">
      <el-form-item label="退货单号">
        <el-input v-model="query.docCode" clearable placeholder="支持模糊查询" style="width: 180px" @keyup.enter="loadList" />
      </el-form-item>
      <el-form-item label="订单号">
        <el-input v-model="query.orderCode" clearable placeholder="不填查全部" style="width: 170px" @keyup.enter="loadList" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.confirmState" clearable placeholder="全部" style="width: 120px" @change="loadList">
          <el-option label="待提交" :value="PENDING_STATE" />
          <el-option label="已入库" :value="DONE_STATE" />
          <!-- 退货单也可能在库存单据页被驳回，驳回后这张单作废、不再占用可退数量 -->
          <el-option label="已驳回" :value="VOID_STATE" />
        </el-select>
      </el-form-item>
      <!-- 退货单落在库存月表上，换月份就是换一张物理表，所以月份是必选项 -->
      <el-form-item label="月份">
        <el-date-picker v-model="listMonth" type="month" value-format="YYYY-MM" :clearable="false"
          style="width: 140px" @change="loadList" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadList">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['order:salesreturn:add']" @click="openCreate">新增退货</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="tableRows" border
      :empty-text="pendingMode ? '没有待提交的退货单' : '该月份没有退货记录'">
      <el-table-column prop="docCode" label="退货单号" min-width="180" />
      <el-table-column prop="sourceDocCode" label="订单号" min-width="150" />
      <el-table-column prop="partnerName" label="客户/店铺" min-width="140" :show-overflow-tooltip="true" />
      <!-- 入库方向，仓库在 toWareName 上，不是 fromWareName -->
      <el-table-column prop="toWareName" label="退回仓库" min-width="130" />
      <el-table-column prop="totalQty" label="退货数量" width="100" align="right" />
      <el-table-column v-if="canSeeCost" label="退回成本" width="110" align="right">
        <template #default="scope">{{ formatCurrency(scope.row.totalAmount, BASE_CURRENCY) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag size="small" :type="stateType(scope.row.confirmState)">{{ stateLabel(scope.row.confirmState) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建人" width="100" align="center">
        <template #default="scope">{{ scope.row.createUserName || scope.row.createUser }}</template>
      </el-table-column>
      <!-- 跨月查看时同屏会出现不同月份的单，标出来免得看串 -->
      <el-table-column v-if="pendingMode" label="所属月份" width="100" align="center">
        <template #default="scope">{{ monthLabel(scope.row.tableSuffix) }}</template>
      </el-table-column>
      <el-table-column prop="docDate" label="退货时间" width="160" align="center" />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['order:salesreturn:query']" @click="openDetail(scope.row)">详情</el-button>
          <el-button v-if="scope.row.confirmState === PENDING_STATE" link type="success"
            v-hasPermi="['order:salesreturn:submit']" @click="submit(scope.row)">提交退货</el-button>
          <el-button v-if="scope.row.confirmState === PENDING_STATE" link type="danger"
            v-hasPermi="['order:salesreturn:remove']" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="!pendingMode && total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <el-dialog v-model="createOpen" title="新增退货" width="1150px" append-to-body :close-on-click-modal="false">
      <el-alert type="info" show-icon :closable="false" class="mb20"
        title="保存后只是建了一张待提交的退货单，货还没回库"
        description="保存即占住这些可退数量，回到列表点「提交退货」货才真正入库并写流水。" />

      <el-form :inline="true" class="mb8">
        <el-form-item label="订单月份">
          <el-date-picker v-model="orderMonth" type="month" value-format="YYYY-MM" :clearable="false"
            style="width: 140px" @change="loadOrders" />
        </el-form-item>
        <el-form-item label="销售订单">
          <el-select v-model="orderCode" filterable clearable placeholder="只列出发过货的订单"
            style="width: 380px" @change="loadReturnable">
            <el-option v-for="order in orders" :key="order.orderCode"
              :label="orderLabel(order)" :value="order.orderCode" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Refresh" :disabled="!orderCode" @click="loadReturnable">查询可退商品</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="returnableLoading" :data="items" border empty-text="选择销售订单后显示可退商品">
        <el-table-column prop="goodsCode" label="商品编码" width="130" />
        <el-table-column prop="goodsName" label="商品名称" min-width="150" />
        <el-table-column prop="spec" label="规格" width="110" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="warehouseName" label="发货仓库" width="130" :show-overflow-tooltip="true" />
        <el-table-column prop="shippedQty" label="已发" width="90" align="right" />
        <el-table-column prop="returnedQty" label="已退" width="90" align="right" />
        <el-table-column prop="returnableQty" label="可退数量" width="100" align="right" />
        <el-table-column v-if="canSeeCost" label="原出库成本" width="120" align="right">
          <template #default="scope">{{ formatCurrency(scope.row.costPrice, BASE_CURRENCY) }}</template>
        </el-table-column>
        <el-table-column label="本次退货" width="170">
          <template #default="scope">
            <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.returnQty" :min="0" :max="Number(scope.row.returnableQty)" style="width: 100%" @change="onQtyChange(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column :label="`退款参考（${currencyLabel(orderCurrency)}）`" width="150" align="right">
          <template #default="scope">
            <span :class="{ 'text-muted': !Number(scope.row.returnQty) }">{{ refund(scope.row) }}</span>
          </template>
        </el-table-column>
        <!-- FIFO 出库的批次是过账时才定的，用户不可能记得，所以直接把当初出库
             动用过的批次列出来选；allow-create 保留手填，应对流水缺失的老数据 -->
        <el-table-column label="退回批次" width="190">
          <template #default="scope">
            <el-select v-if="scope.row.batchStrategy === 'batch'" v-model="scope.row.batchCode"
              filterable allow-create default-first-option placeholder="选原出库批次" style="width: 100%">
              <el-option v-for="opt in scope.row.batchOptions || []" :key="opt.batchCode"
                :label="opt.batchCode + '（出库 ' + Number(opt.qty) + '）'" :value="opt.batchCode" />
            </el-select>
            <span v-else class="tracking-hint">{{ scope.row.batchStrategy === 'serial' ? '按序列号入库' : '无需填写' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="序列号" width="110" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.batchStrategy === 'serial'" link type="primary" @click="openSerial(scope.$index)">
              {{ serialCount(scope.row) ? serialCount(scope.row) + '个' : '必须输入' }}
            </el-button>
            <span v-else class="tracking-hint">无需填写</span>
          </template>
        </el-table-column>
      </el-table>

      <el-form :model="form" label-width="80px" class="mt20">
        <el-form-item label="退货说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500"
            placeholder="选填，例如客户签收后发现破损、尺码不合适" />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="tracking-hint mr8">一张退货单只能退回同一个仓库，跨仓请分开提交。</span>
        <el-button type="primary" :loading="saving" :disabled="!selectedCount" @click="save">
          保存退货单（{{ selectedCount }} 项，合计 {{ totalQty }}）
        </el-button>
        <el-button @click="createOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="销售退货单详情" width="900px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="退货单号">{{ detail.docCode }}</el-descriptions-item>
        <el-descriptions-item label="订单号">{{ detail.sourceDocCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ stateLabel(detail.confirmState) }}</el-descriptions-item>
        <el-descriptions-item label="客户/店铺">{{ detail.partnerName || '无' }}</el-descriptions-item>
        <el-descriptions-item label="退回仓库">{{ detail.toWareName }}</el-descriptions-item>
        <el-descriptions-item label="退货时间">{{ detail.docDate }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">{{ detail.totalQty }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="退回成本">{{ formatCurrency(detail.totalAmount, BASE_CURRENCY) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="退货说明" :span="3">{{ detail.remark || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.details || []" border class="mt20">
        <el-table-column prop="goodsCode" label="商品编码" width="130" />
        <el-table-column prop="goodsName" label="商品名称" min-width="150" />
        <el-table-column prop="spec" label="规格" width="110" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="wareName" label="仓库" width="130" />
        <el-table-column prop="batchCode" label="批次号" width="150" />
        <el-table-column prop="qty" label="退货数量" width="100" align="right" />
        <el-table-column v-if="canSeeCost" label="成本单价" width="120" align="right">
          <template #default="scope">{{ formatCurrency(scope.row.costPrice, BASE_CURRENCY) }}</template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="serialOpen" title="输入退回的序列号" width="500px" append-to-body :close-on-click-modal="false">
      <div class="serial-tip">本次退货 {{ serialExpect }}，需要 {{ serialExpect }} 个序列号，每行一个</div>
      <el-input v-model="serialText" type="textarea" :rows="8" placeholder="每行一个序列号" />
      <template #footer>
        <el-button type="primary" @click="confirmSerial">确定</el-button>
        <el-button @click="serialOpen = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SalesReturn">
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { listReturnable, addReturn, submitReturn, delReturn, listReturn, listPendingReturns, getReturn } from '@/api/order/salesreturn'
import { listOrder } from '@/api/order/sales'
// parseTime 只挂在 globalProperties 上，script 里必须显式引入。
// 取当月用它而不是 toISOString()：后者是 UTC，GMT+8 的月初凌晨会切到上个月
import { parseTime } from '@/utils/ruoyi'
import { DEFAULT_CURRENCY, currencyLabel, formatCurrency } from '@/utils/currency'
import DecimalInput from '@/components/DecimalInput'
import { canSeeCost } from '@/utils/price'


/**
 * 库存成本的记账币种。退货入库按<b>原出库的实际成本</b>计价，那笔成本是采购进来时
 * 按本位币记的，跟订单卖给客户用什么币种没关系，所以成本列不跟 orderCurrency 走。
 */
const BASE_CURRENCY = DEFAULT_CURRENCY

/**
 * 退货单状态就是库存单据的审核状态，页面上换成业务说法：
 * 待审核 = 建好还没提交，货没动；已审核 = 已提交入库。
 *
 * 已驳回是从库存单据页驳回这张单产生的——那之后单子作废，也不再占用可退数量，
 * 所以按作废看待：不给提交也不给删除，只留个记录。
 */
const PENDING_STATE = '待审核'
const DONE_STATE = '已审核'
const VOID_STATE = '已驳回'

const { proxy } = getCurrentInstance()

// ---------- 退货记录 ----------
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const listMonth = ref(parseTime(new Date(), '{y}-{m}'))
const query = reactive({ docCode: undefined, orderCode: undefined, confirmState: undefined, pageNum: 1, pageSize: 20 })
const detailOpen = ref(false)
const detail = ref({})
const suffix = () => listMonth.value.replace('-', '')

// ---------- 待提交提醒（跨月） ----------
const pendingRows = ref([])
const pendingMode = ref(false)
const tableRows = computed(() => (pendingMode.value ? pendingRows.value : rows.value))
const pendingMonths = computed(() => [...new Set(pendingRows.value.map(row => monthLabel(row.tableSuffix)))].join('、'))

// ---------- 新增退货 ----------
const createOpen = ref(false)
const saving = ref(false)
const returnableLoading = ref(false)
const orders = ref([])
const items = ref([])
const orderCode = ref(undefined)
const orderMonth = ref(parseTime(new Date(), '{y}-{m}'))
const form = reactive({ remark: '' })

const serialOpen = ref(false)
const serialText = ref('')
const serialIndex = ref(-1)
const serialExpect = computed(() => (serialIndex.value < 0 ? 0 : Number(items.value[serialIndex.value]?.returnQty || 0)))

/**
 * 本次退货所属订单的币种。后端把它挂在每条可退商品上（SalesReturnableItem.currency），
 * 拿第一行的即可——一张订单只有一种币种。还没查出可退商品时退回下拉里选中的订单。
 */
const orderCurrency = computed(() => items.value[0]?.currency
  || orders.value.find(order => order.orderCode === orderCode.value)?.currency)

const selected = computed(() => items.value.filter(row => Number(row.returnQty) > 0))
const selectedCount = computed(() => selected.value.length)
const totalQty = computed(() => selected.value.reduce((sum, row) => sum + Number(row.returnQty || 0), 0))

function stateLabel(state) {
  if (state === PENDING_STATE) return '待提交'
  if (state === DONE_STATE) return '已入库'
  return state || '-'
}

function stateType(state) {
  if (state === DONE_STATE) return 'success'
  if (state === VOID_STATE) return 'info'
  return 'warning'
}

function monthLabel(tableSuffix) {
  const value = String(tableSuffix || '')
  return value.length === 6 ? value.slice(0, 4) + '-' + value.slice(4) : '-'
}

/**
 * 这一行归哪个月表。跨月查看待提交单时同屏会混着多个月份的单，
 * 提交和删除必须打到各自所在的月表上，不能一律用月份选择器的值。
 */
function rowSuffix(row) {
  return row.tableSuffix || suffix()
}

function loadList() {
  loading.value = true
  listReturn({ ...query, tableSuffix: suffix() })
    .then(response => {
      rows.value = response.rows || []
      total.value = response.total || 0
    })
    .finally(() => { loading.value = false })
}

function loadPending() {
  listPendingReturns().then(response => {
    pendingRows.value = response.data || []
    // 单子都清完了就别停在跨月视图上，退回按月份查看
    if (!pendingRows.value.length) pendingMode.value = false
  })
}

function togglePending() {
  pendingMode.value = !pendingMode.value
}

function resetQuery() {
  query.docCode = undefined
  query.orderCode = undefined
  query.confirmState = undefined
  query.pageNum = 1
  listMonth.value = parseTime(new Date(), '{y}-{m}')
  loadList()
}

function openDetail(row) {
  getReturn(row.docCode, rowSuffix(row)).then(response => {
    detail.value = response.data || {}
    detailOpen.value = true
  })
}

function submit(row) {
  proxy.$modal.confirm(`确认提交退货单 ${row.docCode}？提交后货立即入库并写流水，不能撤销。`).then(() => {
    return submitReturn(row.docCode, rowSuffix(row))
  }).then(response => {
    proxy.$modal.msgSuccess(response.msg || '退货已提交入库')
    refresh()
  }).catch(() => {})
}

function remove(row) {
  proxy.$modal.confirm(`确认删除退货单 ${row.docCode}？删除后这些商品的可退数量会放回去。`).then(() => {
    return delReturn(row.docCode, rowSuffix(row))
  }).then(() => {
    proxy.$modal.msgSuccess('退货单已删除')
    refresh()
  }).catch(() => {})
}

/** 提交、删除、新建都会改变待提交集合，两份数据要一起刷 */
function refresh() {
  loadList()
  loadPending()
}

function orderLabel(order) {
  return order.orderCode + ' / ' + (order.shopStoreName || order.shopStoreCode) + ' / ' + order.receivePerson
    + ' / ' + currencyLabel(order.currency)
}

/**
 * 退款参考金额。这一列是「按订单售价该退客户多少钱」，所以用<b>订单币种</b>；
 * 同表的「原出库成本」「退回成本」是库存成本，记的是本位币，不跟订单币种走，别一起改。
 */
function refund(row) {
  return formatCurrency(Number(row.returnQty || 0) * Number(row.salePrice || 0), orderCurrency.value)
}

function openCreate() {
  orderMonth.value = parseTime(new Date(), '{y}-{m}')
  form.remark = ''
  loadOrders()
  createOpen.value = true
}

/** 只有发过货的订单才有东西可退，草稿和一件没发的订单不列出来 */
function loadOrders() {
  orderCode.value = undefined
  items.value = []
  listOrder({ tableSuffix: orderMonth.value.replace('-', '') }).then(response => {
    orders.value = (response.rows || []).filter(order => Number(order.sendPercent) > 0)
  })
}

function loadReturnable() {
  if (!orderCode.value) {
    items.value = []
    return
  }
  returnableLoading.value = true
  listReturnable(orderCode.value)
    .then(response => {
      items.value = (response.data || []).map(item => ({
        ...item,
        returnQty: 0,
        // 只有一个候选批次时直接选上，最常见的情况少点一下
        batchCode: (item.batchOptions || []).length === 1 ? item.batchOptions[0].batchCode : '',
        serialNumbers: []
      }))
      if (!items.value.length) proxy.$modal.msgWarning('该订单没有可退商品')
    })
    .finally(() => { returnableLoading.value = false })
}

function serialCount(row) {
  return (row.serialNumbers || []).length
}

/** 退货数量变了就清掉已录的序列号，免得个数与数量对不上 */
function onQtyChange(row) {
  if (row.batchStrategy === 'serial' && serialCount(row) !== Number(row.returnQty || 0)) {
    row.serialNumbers = []
  }
}

function openSerial(index) {
  if (Number(items.value[index]?.returnQty || 0) <= 0) {
    proxy.$modal.msgWarning('请先填写本次退货数量')
    return
  }
  serialIndex.value = index
  serialText.value = (items.value[index].serialNumbers || []).join('\n')
  serialOpen.value = true
}

function confirmSerial() {
  const values = [...new Set(String(serialText.value || '').split('\n').map(v => v.trim()).filter(Boolean))]
  if (serialIndex.value >= 0) items.value[serialIndex.value].serialNumbers = values
  serialOpen.value = false
}

function save() {
  const lines = selected.value
  const serialMissing = lines.find(
    row => row.batchStrategy === 'serial' && serialCount(row) !== Number(row.returnQty)
  )
  if (serialMissing) {
    proxy.$modal.msgWarning(`商品“${serialMissing.goodsName}”的序列号个数必须等于退货数量`)
    return
  }
  const batchMissing = lines.find(row => row.batchStrategy === 'batch' && !String(row.batchCode || '').trim())
  if (batchMissing) {
    proxy.$modal.msgWarning(`商品“${batchMissing.goodsName}”按批次管理，请填写退回批次号`)
    return
  }
  const warehouses = new Set(lines.map(row => row.warehouseCode))
  if (warehouses.size > 1) {
    proxy.$modal.msgWarning('一张退货单只能退回同一仓库，请分开提交')
    return
  }

  saving.value = true
  addReturn({
    orderCode: orderCode.value,
    remark: form.remark,
    items: lines.map(row => ({
      goodsCode: row.goodsCode,
      warehouseCode: row.warehouseCode,
      qty: row.returnQty,
      batchCode: row.batchCode || undefined,
      serialNumbers: row.serialNumbers || []
    }))
  }).then(response => {
    proxy.$modal.msgSuccess(response.msg || '退货单创建成功，请在列表中提交退货')
    createOpen.value = false
    // 新建的单落在当月表，把列表切回当月才看得到它
    pendingMode.value = false
    listMonth.value = parseTime(new Date(), '{y}-{m}')
    refresh()
  }).finally(() => { saving.value = false })
}

onMounted(refresh)
</script>

<style scoped>
.tracking-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.text-muted {
  color: var(--el-text-color-secondary);
}
.serial-tip {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.mr8 { margin-right: 8px; }
.mt20 { margin-top: 20px; }
</style>

<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="本页只登记发货，不做入库"
      description="采购员在这里登记供应商发了什么、发了多少（预计到货数量）。签收、实际到货数量、批次序列号由入库员在「库存管理 → 入库信息 → 采购入库」办理。" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <el-form-item label="月份"><el-date-picker v-model="month" type="month" value-format="YYYY-MM" @change="loadList" /></el-form-item>
      <!-- 发货单号不在本页展示，按它搜也无从下手，改为按采购单号找 -->
      <el-form-item label="采购单号"><el-input v-model="query.purchaseCode" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 140px">
          <el-option v-for="state in states" :key="state" :label="state" :value="state" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="loadList">搜索</el-button><el-button icon="Refresh" @click="reset">重置</el-button></el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="2"><el-button type="primary" plain icon="Plus" v-hasPermi="['purchase:receive:add']" @click="openCreate">登记发货</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadList" />
    </el-row>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="purchaseCode" label="采购单号" min-width="150" />
      <el-table-column prop="sendDate" label="发货日期" width="110">
        <template #default="scope">{{ parseTime(scope.row.sendDate, '{y}-{m}-{d}') || '未填写' }}</template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="到货仓库" min-width="120">
        <template #default="scope">
          <span v-if="scope.row.warehouseName">{{ scope.row.warehouseName }}</span>
          <span v-else class="hint">入库时确定</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalQty" label="本次预计到货" width="120" align="right" />
      <!-- 实际到货由入库员在采购入库时填，未入库前为 0 -->
      <el-table-column prop="totalActualQty" label="实际到货数量" width="120" align="right">
        <template #default="scope">
          <span v-if="Number(scope.row.totalActualQty) > 0">{{ scope.row.totalActualQty }}</span>
          <span v-else class="hint">待入库</span>
        </template>
      </el-table-column>
      <el-table-column prop="carrier" label="承运商" min-width="110" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.carrier || '—' }}</template>
      </el-table-column>
      <el-table-column prop="logisticsNo" label="物流单号" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.logisticsNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="receiveUserName" label="签收人" width="110">
        <template #default="scope">
          <span v-if="scope.row.receiveUserName || scope.row.receiveUser">{{ scope.row.receiveUserName || scope.row.receiveUser }}</span>
          <span v-else class="hint">待签收</span>
        </template>
      </el-table-column>
      <el-table-column prop="receiveTime" label="签收时间" width="160">
        <template #default="scope">{{ scope.row.receiveTime || '—' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
          <el-button link type="primary" v-if="editable(scope.row)" v-hasPermi="['purchase:receive:edit']" @click="openEdit(scope.row)">修改</el-button>
          <el-button link type="danger" v-if="editable(scope.row)" v-hasPermi="['purchase:receive:cancel']" @click="cancel(scope.row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 登记 / 修改发货 -->
    <el-dialog v-model="createOpen" :title="editing ? '修改采购发货' : '登记采购发货'" width="1000px" destroy-on-close :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <div class="section-title">来源与发货</div>
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="采购订单">
              <el-select v-model="form.purchaseCode" filterable :disabled="editing" placeholder="请选择已审核的采购订单" style="width:100%" @change="loadPurchaseItems">
                <el-option v-for="order in orders" :key="order.purchaseCode" :label="orderLabel(order)" :value="order.purchaseCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 发货日期决定本单落哪张月表，跨月请取消重建 -->
            <el-form-item label="发货日期">
              <el-date-picker v-model="form.sendDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="物流单号"><el-input v-model="form.logisticsNo" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="承运商"><el-input v-model="form.carrier" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="8">
            <!-- 一张发货单只入一个仓，所以仓库是单头字段，不放在明细行里。
                 留空也行，入库员在采购入库时选即可，最终以入库单为准。 -->
            <el-form-item label="建议入库仓库">
              <el-select v-model="form.warehouseCode" placeholder="可留空，入库时再定" clearable filterable style="width:100%">
                <el-option v-for="ware in warehouses" :key="ware.wareCode" :label="warehouseLabel(ware)" :value="ware.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">运费</div>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="运费类型">
              <el-select v-model="form.freightType" placeholder="请选择" style="width:100%" @change="onFreightTypeChange">
                <el-option label="包邮" value="free" />
                <el-option label="寄付" value="prepaid" />
                <el-option label="到付" value="collect" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 金额栏跟着运费类型走：包邮没有运费，整栏收起；寄付是供应商已经付掉的，
               填的就是实际金额；到付要等入库员签收才知道，这里只能填预估。 -->
          <el-col :span="8" v-if="form.freightType !== 'free'">
            <el-form-item v-if="canSeeCost" :label="freightAmountLabel">
              <el-input-number v-model="freightAmountModel" :min="0" :precision="2" :step="10"
                controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><div class="hint form-hint">{{ freightAmountHint }}</div></el-col>
        </el-row>

        <div class="section-title">发货明细</div>
        <el-table :data="form.items" border stripe>
          <el-table-column prop="goodsName" label="商品" min-width="180" />
          <el-table-column prop="spec" label="规格" width="110" />
          <el-table-column prop="qty" label="采购数量" width="100" align="right" />
          <!-- 「已处理」= 已到货 + 在途（待入库 + 入库中），后端 /purchase/order/items 现算。
               不能只看已到货：它要等入库单审核通过才增加，光看它会让刚登记完发货的货
               看起来还没动过，同一批货被重复登记发货。 -->
          <el-table-column prop="processedQty" label="已处理" width="90" align="right">
            <template #default="scope">{{ Number(scope.row.processedQty || 0) }}</template>
          </el-table-column>
          <el-table-column label="预计到货数量" width="190">
            <template #default="scope">
              <el-input-number v-model="scope.row.sendQty" :min="0" :max="remainingQty(scope.row)"
                controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
        </el-table>
        <div class="hint mt8">整单入同一个仓库（上方选择）。批次号与序列号在入库环节采集，本页不填。</div>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '保存发货单' }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情：全只读 -->
    <el-dialog v-model="detailOpen" title="采购发货单详情" width="1080px" :close-on-click-modal="false">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="发货单号">{{ detail.receiveCode }}</el-descriptions-item>
        <el-descriptions-item label="采购单号">{{ detail.purchaseCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="发货日期">{{ parseTime(detail.sendDate, '{y}-{m}-{d}') || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ detail.logisticsNo || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="承运商">{{ detail.carrier || '未填写' }}</el-descriptions-item>
        <!-- 入库前显示采购员建议的仓库，入库后被入库员实选的仓库覆盖 -->
        <el-descriptions-item label="入库仓库">{{ detail.warehouseName || '待入库时确定' }}</el-descriptions-item>
        <el-descriptions-item label="运费类型">{{ freightTypeLabel(detail.freightType) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="预估运费">{{ formatCurrency(detail.estFreightAmount) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="实付运费">{{ formatCurrency(detail.freightAmount) }}</el-descriptions-item>
        <el-descriptions-item label="发货登记人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="签收人">{{ detail.receiveUserName || detail.receiveUser || '待入库员签收' }}</el-descriptions-item>
        <el-descriptions-item label="签收时间">{{ detail.receiveTime || '待入库员签收' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '无' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" class="mt20">
        <el-table-column prop="goodsName" label="商品" min-width="150" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="qty" label="预计到货" width="100" align="right" />
        <el-table-column label="实际到货" width="100" align="right">
          <template #default="scope">
            <span v-if="scope.row.actualQty !== null && scope.row.actualQty !== undefined">{{ scope.row.actualQty }}</span>
            <span v-else class="hint">待入库</span>
          </template>
        </el-table-column>
        <el-table-column label="差异" width="110" align="right">
          <template #default="scope">
            <span v-if="scope.row.actualQty === null || scope.row.actualQty === undefined" class="hint">—</span>
            <el-tag v-else-if="diff(scope.row) === 0" type="success" size="small">齐套</el-tag>
            <el-tag v-else-if="diff(scope.row) > 0" type="warning" size="small">短装 {{ diff(scope.row) }}</el-tag>
            <el-tag v-else type="primary" size="small">超发 {{ -diff(scope.row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="入库仓库" width="120">
          <template #default="scope">{{ scope.row.warehouseName || '待入库时确定' }}</template>
        </el-table-column>
        <el-table-column prop="batchCode" label="批次号" width="140">
          <template #default="scope">{{ scope.row.batchCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="序列号" width="90" align="center">
          <template #default="scope">{{ serialCount(scope.row) ? serialCount(scope.row) + '个' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="inventoryDocCode" label="入库单号" min-width="150">
          <template #default="scope">{{ scope.row.inventoryDocCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="lineStatus" label="行状态" width="90">
          <template #default="scope"><el-tag size="small" :type="lineStatusType(scope.row.lineStatus)">{{ scope.row.lineStatus }}</el-tag></template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="PurchaseReceive">
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { formatCurrency } from '@/utils/currency'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listReceive, getReceive, addReceive, updateReceive, cancelReceive } from '@/api/purchase/receive'
import { listAvailableOrders, getOrderItems } from '@/api/purchase/order'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
// parseTime 只挂在 globalProperties 上，模板里能直接用，script 里必须显式引入。
// 这里用它取"今天"而不是 toISOString()：后者返回 UTC，GMT+8 的凌晨会取到前一天，
// 而发货日期决定本单落哪张月表，差一天就落错表了。
import { parseTime } from '@/utils/ruoyi'
import { canSeeCost } from '@/utils/price'

// 发货单整单入库，只有两个状态。旧状态（待质检/质检中/已质检/部分退货/部分入库）
// 随开发数据清空一并作废，这里不再兼容。
const states = ['待入库', '已入库']

const loading = ref(false), saving = ref(false), showSearch = ref(true)
const detailOpen = ref(false), createOpen = ref(false), editing = ref(false)
const rows = ref([]), detail = ref({ items: [] }), orders = ref([]), warehouses = ref([])
const month = ref(parseTime(new Date(), '{y}-{m}'))
const query = reactive({ purchaseCode: undefined, status: undefined })
const form = ref({ items: [] })

const suffix = () => month.value.replace('-', '')

function loadList() {
  loading.value = true
  listReceive({ ...query, tableSuffix: suffix() })
    .then(r => rows.value = r.rows || [])
    .finally(() => loading.value = false)
}
function reset() {
  query.purchaseCode = undefined; query.status = undefined
  loadList()
}

// 只要有一行被入库员碰过，整单就锁住。要改就取消重建。
// 入库单一建出来整单就只读了，但这期间单头仍是「待入库」（货没过账就还没进仓库），
// 所以光看状态不够，要再看有没有行被入库单占用。
function editable(row) { return row.status === '待入库' && !Number(row.processingLines || 0) }

function statusType(status) {
  return status === '已入库' ? 'success' : 'info'
}
function lineStatusType(status) {
  return status === '已入库' ? 'success' : status === '入库中' ? 'warning' : 'info'
}
function freightTypeLabel(freightType) {
  return { free: '包邮', prepaid: '寄付', collect: '到付' }[freightType] || '未填写'
}
/**
 * 本条采购明细还能发多少 = 采购数量 − 已处理。不单独占一列展示（表上两个数一减就是），
 * 只用来卡住预计到货数量的上限。
 * 编辑发货单时要把本单自己已占的量加回来，否则改单时上限会被自己挤没。
 */
function remainingQty(row) {
  const base = Number(row.qty || 0) - Number(row.processedQty || 0)
  return Math.max(0, base + Number(row.ownQty || 0))
}

/** 短装为正、超发为负，与后端 badqty = max(预计-实际,0) 的口径一致 */
function diff(row) { return Number(row.qty || 0) - Number(row.actualQty || 0) }
function serialCount(row) {
  return String(row?.serialNumbers || '').replace(/^@SN:/, '').split(/[,\r\n]+/).filter(v => v.trim()).length
}
// 下拉里带出采购员录入的物流单号，便于对上货
function orderLabel(order) {
  return order.purchaseCode + ' / ' + order.partnerName + (order.logisticsNo ? ' / 物流' + order.logisticsNo : '')
}
/** 包邮不产生运费，切过去时清零，避免残留金额被提交 */
function onFreightTypeChange(value) {
  if (value === 'free') { form.value.estFreightAmount = 0; form.value.freightAmount = 0 }
}

/** 寄付的运费供应商已经付掉了，填的是实际金额；到付要等签收才知道，只能填预估 */
const freightAmountLabel = computed(() => form.value.freightType === 'prepaid' ? '实际运费' : '预估运费')

/**
 * 同一个输入框写进不同的列：寄付写实付运费 freightAmount，到付写预估运费 estFreightAmount。
 * 后端 normalizeFreight 会按运费类型再归一一次，这里只负责把值放对地方。
 */
const freightAmountModel = computed({
  get: () => form.value.freightType === 'prepaid' ? form.value.freightAmount : form.value.estFreightAmount,
  set: (v) => {
    if (form.value.freightType === 'prepaid') form.value.freightAmount = v
    else form.value.estFreightAmount = v
  }
})
const freightAmountHint = computed(() => ({
  free: '包邮不产生运费',
  prepaid: '寄付由供应商承担，填供应商实际支付的金额'
}[form.value.freightType] || '实付运费由入库员在采购入库时填写'))

function openCreate() {
  editing.value = false
  form.value = {
    purchaseCode: undefined,
    sendDate: parseTime(new Date(), '{y}-{m}-{d}'),
    logisticsNo: '', carrier: '', freightType: '', estFreightAmount: 0, freightAmount: 0,
    warehouseCode: '', items: []
  }
  listAvailableOrders().then(r => orders.value = r.data || [])
  createOpen.value = true
}

// 物流单号、承运商、运费类型优先取采购订单上采购员已录入的值
function loadPurchaseItems(code) {
  const order = orders.value.find(o => o.purchaseCode === code)
  if (order) {
    if (!form.value.logisticsNo) form.value.logisticsNo = order.logisticsNo || ''
    if (!form.value.carrier) form.value.carrier = order.carrier || ''
    if (!form.value.freightType) form.value.freightType = order.freightType || ''
  }
  getOrderItems(code).then(r => {
    const items = r.data || []
    form.value.items = items.map(i => ({ ...i, sendQty: 0 }))
    // 采购订单明细上如果带了建议仓库，取第一个非空的预填到单头，采购员可以改
    if (!form.value.warehouseCode) {
      form.value.warehouseCode = items.find(i => i.warehouseCode)?.warehouseCode || ''
    }
  })
}

function openEdit(row) {
  editing.value = true
  getReceive(row.receiveCode, suffix()).then(r => {
    const data = r.data || {}
    listAvailableOrders().then(res => orders.value = res.data || [])
    getOrderItems(data.purchaseCode).then(res => {
      const stored = new Map((data.items || []).map(i => [i.purchaseDetailCode, i]))
      form.value = {
        ...data,
        sendDate: data.sendDate ? String(data.sendDate).slice(0, 10) : '',
        estFreightAmount: Number(data.estFreightAmount || 0),
        freightAmount: Number(data.freightAmount || 0),
        // 单头仓库由后端从明细行反推带出，直接用
        warehouseCode: data.warehouseCode || '',
        items: (res.data || []).map(i => {
          const hit = stored.get(i.detailCode)
          // ownQty：本单自己已经占掉的量。它也算在 processedQty 里，改单时要加回上限，
          // 否则一张已发 3 件的单子重新打开，可发数量会变成 0，改都改不了。
          return { ...i, sendQty: hit ? Number(hit.qty) : 0, ownQty: hit ? Number(hit.qty) : 0 }
        })
      }
      createOpen.value = true
    })
  })
}

function save() {
  const picked = form.value.items.filter(i => Number(i.sendQty) > 0)
  if (!form.value.purchaseCode || !picked.length) {
    ElMessage.warning('请选择采购订单并填写预计到货数量'); return
  }
  if (!form.value.sendDate) { ElMessage.warning('请填写发货日期'); return }
  // 仓库是单头字段，后端会铺到每一行；这里不再逐行传
  const items = picked.map(i => ({
    purchaseDetailCode: i.detailCode,
    qty: i.sendQty
  }))
  saving.value = true
  const action = editing.value
    ? updateReceive({ ...form.value, items }, suffix()).then(() => ElMessage.success('发货单已修改'))
    : addReceive({ ...form.value, items }).then(r => {
        const created = r.data
        month.value = created.tableSuffix.slice(0, 4) + '-' + created.tableSuffix.slice(4)
        ElMessage.success('发货单已登记，等待入库员办理入库')
      })
  action.then(() => { createOpen.value = false; loadList() }).finally(() => saving.value = false)
}

function openDetail(row) {
  getReceive(row.receiveCode, suffix()).then(r => { detail.value = r.data || { items: [] }; detailOpen.value = true })
}

function cancel(row) {
  ElMessageBox.confirm('取消后将删除该发货单，是否继续？', '提示', { closeOnClickModal: false })
    .then(() => cancelReceive(row.receiveCode, suffix()))
    .then(() => { ElMessage.success('发货单已取消'); loadList() })
    .catch(() => {})
}

onMounted(() => {
  loadList()
  listWarehouse({ pageSize: 100 }).then(r => warehouses.value = r.rows || [])
})

// 本页被 keep-alive 缓存，从别的标签页切回来时 onMounted 不会再触发，
// 列表会停在离开前的快照——入库员刚在库存模块入过库，这里还显示「待入库」。
// onActivated 在首次挂载后也会触发一次，用标志位跳过，避免重复请求。
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) {
    skipFirstActivate = false
    return
  }
  loadList()
})
</script>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.form-hint {
  line-height: 32px;
}
.mt8 {
  margin-top: 8px;
}
/* 分组小标题，把「来源与发货 / 运费 / 发货明细」三段隔开，避免一堆输入框糊成一片 */
.section-title {
  margin: 4px 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
/* 表单项底部间距收紧一点，三段分组后整体更紧凑 */
:deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>

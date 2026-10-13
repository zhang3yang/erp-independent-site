<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="单据代码" prop="docCode">
        <el-input v-model="queryParams.docCode" placeholder="请输入单据代码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="confirmState">
        <el-select v-model="queryParams.confirmState" placeholder="请选择状态" clearable style="width: 240px">
          <el-option label="待审核" value="待审核" />
          <el-option label="已审核" value="已审核" />
          <el-option label="已驳回" value="已驳回" />
        </el-select>
      </el-form-item>
      <el-form-item label="单据日期">
        <el-date-picker v-model="docDateRange" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['inventory:transfer:add']">新建调拨单</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDeleteBatch" v-hasPermi="['inventory:transfer:remove']">批量删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="transferList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="单据代码" align="center" prop="docCode" width="150" />
      <el-table-column label="单据日期" align="center" prop="docDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.docDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="来源仓库" align="center" prop="fromWareName" width="120" />
      <el-table-column label="目标仓库" align="center" prop="toWareName" width="120" />
      <el-table-column label="总数量" align="center" prop="totalQty" width="100" />
      <el-table-column v-if="canSeeCost" label="总金额" align="center" prop="totalAmount" width="120">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="confirmState" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.confirmState === '已审核' ? 'success' : scope.row.confirmState === '已驳回' ? 'danger' : 'warning'">
            {{ scope.row.confirmState }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核人" align="center" prop="confirmUserName" width="100" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.confirmUserName || scope.row.confirmUser || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['inventory:transfer:edit']">编辑</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="success" icon="Check" @click="handleConfirm(scope.row, true)" v-hasPermi="['inventory:transfer:approve']">审核</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="danger" icon="Close" @click="handleConfirm(scope.row, false)" v-hasPermi="['inventory:transfer:approve']">驳回</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['inventory:transfer:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 建单前先定良品状态：良品和不良品是两格独立库存，一张单只能搬其中一格 -->
    <el-dialog title="新建调拨单" v-model="qualityPickOpen" width="440px" append-to-body :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon class="mb8"
        title="一张调拨单只能调同一种良品状态的货"
        description="良品和不良品在库存里是分开记的。选定后本单的明细行只能添加该状态下有库存的商品；要调另一种，请另建一张单。" />
      <el-form label-width="90px">
        <el-form-item label="良品状态">
          <el-radio-group v-model="qualityPick">
            <el-radio v-for="item in QUALITY_FLAG_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="confirmQualityPick">下一步</el-button>
        <el-button @click="qualityPickOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑调拨单对话框 -->
    <el-dialog :title="dialogTitle" v-model="addOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-form :model="addForm" :rules="addRules" ref="addRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="来源仓库" prop="fromWareCode">
              <el-select v-model="addForm.fromWareCode" placeholder="请选择仓库" style="width: 100%" @change="handleFromWarehouseChange" :disabled="!!addForm.docCode">
                <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="目标仓库" prop="toWareCode">
              <el-select v-model="addForm.toWareCode" placeholder="请选择仓库" style="width: 100%" @change="handleToWarehouseChange" :disabled="!!addForm.docCode">
                <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 良品状态在建单前就选定了，整张单只搬这一格库存，这里只作展示 -->
            <el-form-item label="良品状态">
              <el-input :model-value="qualityLabel(addForm.qualityFlag)" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="addForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>

        <el-divider content-position="left">调拨明细</el-divider>

        <el-button type="primary" icon="Plus" size="small" @click="addItem" style="margin-bottom: 10px">添加商品</el-button>

        <el-table :data="addForm.items" border>
          <!-- 编码、名称、规格、单位取自商品档案，只能通过「添加商品」选出来，单据上不可改 -->
          <el-table-column label="商品编码" prop="goodsCode" width="150" />
          <el-table-column label="商品名称" prop="goodsName" :show-overflow-tooltip="true" />
          <el-table-column label="规格" prop="spec" width="100" />
          <el-table-column label="单位" prop="unit" width="60" />
          <el-table-column label="当前库存" prop="currentQty" width="100" />
          <el-table-column label="调拨数量" width="150">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qty" :min="0" :max="availableTransferQuantity(scope.row)" size="small" style="width: 100%" @change="handleQtyChange(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column label="批次" width="210">
            <template #default="scope">
              <span v-if="scope.row.batchStrategy === 'serial'">{{ scope.row.batchDisplay || scope.row.batchCode || '由序列号带出' }}</span>
              <el-select v-else-if="scope.row.batchStrategy === 'batch'" v-model="scope.row.batchCode" placeholder="请选择批次" size="small" style="width: 100%" @change="handleBatchChange(scope.row)">
                <el-option v-for="batch in scope.row.availableBatches" :key="batch.batchCode" :label="`${batch.batchCode}（剩余：${batch.remainQty}）`" :value="batch.batchCode" />
              </el-select>
              <span v-else>无需填写</span>
            </template>
          </el-table-column>
          <el-table-column label="序列" width="120" align="center">
            <template #default="scope">
              <el-button link type="primary" size="small" @click="openSerialDialog(scope.$index)" :disabled="scope.row.batchStrategy !== 'serial'">
                {{ scope.row.batchStrategy !== 'serial' ? '无需填写' : (scope.row.serialNumbers?.length ? scope.row.serialNumbers.length + '个' : '必须选择') }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="removeItem(scope.$index)" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitAdd">{{ addForm.docCode ? '保存修改' : '确认创建' }}</el-button>
          <el-button @click="addOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog title="调拨单详情" v-model="detailOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单据编号">{{ detail.docCode }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ detail.docTypeName }}</el-descriptions-item>
        <el-descriptions-item label="单据日期">{{ parseTime(detail.docDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="来源仓库">{{ detail.fromWareName }}</el-descriptions-item>
        <el-descriptions-item label="目标仓库">{{ detail.toWareName }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ detail.totalQty }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="总金额">{{ formatCurrency(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.confirmState === '已审核' ? 'success' : detail.confirmState === '已驳回' ? 'danger' : 'warning'">
            {{ detail.confirmState }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.confirmUserName || detail.confirmUser || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">调拨明细</el-divider>

      <el-table :data="detail.details" border>
        <el-table-column label="商品编码" prop="goodsCode" width="120" />
        <el-table-column label="商品名称" prop="goodsName" :show-overflow-tooltip="true" />
        <el-table-column label="规格" prop="spec" width="100" />
        <el-table-column label="单位" prop="unit" width="60" />
        <el-table-column label="数量" prop="qty" width="100" />
        <el-table-column v-if="canSeeCost" label="成本单价" prop="costPrice" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.costPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canSeeCost" label="成本金额" prop="costAmount" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.costAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批次" width="120"><template #default="scope">{{ scope.row.batchCode ? '已关联' : '-' }}</template></el-table-column>
        <el-table-column label="序列" width="150"><template #default="scope">{{ scope.row.serialNumbers?.length ? `已选${scope.row.serialNumbers.length}个` : '-' }}</template></el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 序列号选择对话框 -->
    <el-dialog title="选择可用序列号" v-model="serialDialogOpen" width="560px" append-to-body :close-on-click-modal="false">
      <p style="color: #909399; font-size: 12px; margin-bottom: 10px;">
        仅显示当前商品、来源仓库和库存状态下的在库序列号；已选 {{ serialDialogSelected.length }} 个，调拨数量为 {{ serialDialogMax }} 个
      </p>
      <el-select v-model="serialDialogSelected" multiple filterable clearable collapse-tags collapse-tags-tooltip
        :multiple-limit="serialDialogMax" :loading="serialDialogLoading" placeholder="请选择序列号" style="width: 100%">
        <el-option v-for="item in serialDialogOptions" :key="item.serialNo"
          :label="item.batchCode ? `${item.serialNo}（批次 ${item.batchCode}）` : item.serialNo" :value="item.serialNo" />
      </el-select>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmSerialDialog">确 定</el-button>
          <el-button @click="serialDialogOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="selectedGoodsCodes" @confirm="handleGoodsSelected" />
  </div>
</template>

<script setup name="TransferManagement">
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { formatCurrency } from '@/utils/currency'
import { listDocument, getDocument, updateDocument, delDocument, confirmDocument } from '@/api/inventory/document'
import { createTransfer } from '@/api/inventory/transfer'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { QUALITY_FLAG, ONHAND_FLAG, QUALITY_FLAG_OPTIONS, qualityLabel } from '@/utils/stockState'
import { getStock } from '@/api/inventory/stock'
import { getGoods } from '@/api/inventory/goods'
import { listAvailableBatch } from '@/api/inventory/batch'
import { listInStockSerial } from '@/api/inventory/serial'
import DecimalInput from '@/components/DecimalInput'

import {
  applySerialSelection,
  availableInventoryQuantity,
  clearInventoryTracking,
  createInventoryDocumentLine,
  serialQuantityMatches
} from '@/utils/inventoryDocument'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()

const transferList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const warehouseOptions = ref([])
const addOpen = ref(false)
const detailOpen = ref(false)
const detail = ref({})
const dialogTitle = ref('新建调拨单')
// 新建前先选良品状态的小弹框
const qualityPickOpen = ref(false)
const qualityPick = ref(QUALITY_FLAG.GOOD)
const goodsSelectRef = ref(null)
const serialDialogOpen = ref(false)
const serialDialogIndex = ref(-1)
const serialDialogSelected = ref([])
const serialDialogOptions = ref([])
const serialDialogLoading = ref(false)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    docCode: undefined,
    confirmState: undefined,
    docDateBegin: undefined,
    docDateEnd: undefined
  },
  addForm: {
    docCode: '',
    fromWareCode: '',
    fromWareName: '',
    toWareCode: '',
    toWareName: '',
    qualityFlag: QUALITY_FLAG.GOOD,
    remark: '',
    items: []
  },
  addRules: {
    fromWareCode: [{ required: true, message: '请选择来源仓库', trigger: 'change' }],
    toWareCode: [{ required: true, message: '请选择目标仓库', trigger: 'change' }],

  }
})

const { queryParams, addForm, addRules } = toRefs(data)

// 不填就是查全部月份。单据按 docdate 分月表，后端会挑出区间覆盖到的月表；
// 留空时扫全部现存月表——跨月之后查不到上个月的调拨单才是真问题。
const docDateRange = ref([])

/**
 * 调拨只搬仓库不改状态：来源和目标都是「在库·自有」，良品还是不良品由页面选。
 * 在库和占用两维不给选——要调拨的货必然在仓库里，且借出、维修、生产领用中的货
 * 得先走对应的归还单回到自有才能调，所以只可能是自有。
 */
function transferState() {
  return { qualityFlag: addForm.value.qualityFlag, onhandFlag: ONHAND_FLAG.IN }
}

/** 摊平成单据的 from / to 三维字段 */
function transferStateFields(prefix) {
  const state = transferState()
  return prefix === 'from'
    ? { fromQualityFlag: state.qualityFlag, fromOnhandFlag: state.onhandFlag }
    : { toQualityFlag: state.qualityFlag, toOnhandFlag: state.onhandFlag }
}

const serialDialogMax = computed(() => {
  const row = addForm.value.items[serialDialogIndex.value]
  const qty = Number(row?.qty)
  return Number.isInteger(qty) && qty > 0 ? qty : 0
})

/** 已在明细里的商品，选商品弹窗用它打「已添加」标记 */
const selectedGoodsCodes = computed(() => addForm.value.items.map(row => row.goodsCode).filter(Boolean))

/** 查询调拨单列表 */
async function getList() {
  loading.value = true
  const range = docDateRange.value
  queryParams.value.docDateBegin = range && range.length ? range[0] + ' 00:00:00' : undefined
  queryParams.value.docDateEnd = range && range.length ? range[1] + ' 23:59:59' : undefined
  try {
    const response = await listDocument({ ...queryParams.value, docType: 'DB' })
    transferList.value = response.rows
    total.value = response.total
  } finally {
    loading.value = false
  }
}

/** 加载仓库选项 */
async function loadWarehouseOptions() {
  const response = await listWarehouse({ pageSize: 100 })
  warehouseOptions.value = response.rows || []
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  // 日期区间没绑在 queryParams 上，resetForm 清不掉，要单独还原
  docDateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.docCode)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 来源仓库选择变化 */
function handleFromWarehouseChange(value) {
  const warehouse = warehouseOptions.value.find(item => item.wareCode === value)
  if (warehouse) {
    addForm.value.fromWareName = warehouse.wareName
  }
  handleStockContextChange()
}

function handleStockContextChange() {
  addForm.value.items.forEach(row => {
    clearTrackingSelection(row)
    refreshRowStock(row)
  })
}

/** 目标仓库选择变化 */
function handleToWarehouseChange(value) {
  const warehouse = warehouseOptions.value.find(item => item.wareCode === value)
  if (warehouse) {
    addForm.value.toWareName = warehouse.wareName
  }
}

/**
 * 新增按钮：先弹小窗选良品状态。
 *
 * 良品和不良品在库存表里是两格独立的库存，一张调拨单只能搬其中一格；
 * 进主弹框前先定死，明细行才不会出现「选了良品却添进不良品」的错配。
 */
function handleAdd() {
  qualityPick.value = QUALITY_FLAG.GOOD
  qualityPickOpen.value = true
}

/** 选完良品状态，带着它进主弹框；此后该维度只读 */
function confirmQualityPick() {
  addForm.value = {
    docCode: '',
    fromWareCode: '',
    fromWareName: '',
    toWareCode: '',
    toWareName: '',
    qualityFlag: qualityPick.value,
    remark: '',
    items: []
  }
  qualityPickOpen.value = false
  dialogTitle.value = `新建调拨单（${qualityLabel(qualityPick.value)}）`
  addOpen.value = true
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  try {
    const response = await getDocument(row.docCode, row.tableSuffix)
    const doc = response.data
    addForm.value = {
      docCode: doc.docCode,
      fromWareCode: doc.fromWareCode,
      fromWareName: doc.fromWareName,
      toWareCode: doc.toWareCode,
      toWareName: doc.toWareName,
      qualityFlag: doc.fromQualityFlag,
      remark: doc.remark || '',
      items: doc.details?.map(d => createInventoryDocumentLine({
        goodsCode: d.goodsCode,
        goodsName: d.goodsName,
        spec: d.spec,
        unit: d.unit,
        currentQty: 0,
        qty: d.qty,
        batchCode: d.batchCode || '',
        batchDisplay: d.batchCode || '',
        serialNumbers: d.serialNumbers || [],
        batchStrategy: 'none'
      })) || []
    }
    dialogTitle.value = '编辑调拨单'
    addOpen.value = true
    addForm.value.items.forEach(row => refreshRowStock(row, false))
  } catch (error) {
    proxy.$modal.msgError('获取单据详情失败')
  }
}

/** 添加商品：从分类树弹窗里挑，挑完一个商品生成一行并带出库存 */
function addItem() {
  goodsSelectRef.value?.open()
}

function handleGoodsSelected(goodsItems) {
  goodsItems.forEach(goods => {
    const row = createInventoryDocumentLine({
      goodsCode: goods.goodsCode,
      goodsName: goods.goodsName || '',
      spec: goods.spec || '',
      unit: goods.unit || '',
      batchStrategy: goods.batchStrategy || 'none',
      costCalculation: goods.costCalculation || 'moving_avg'
    })
    addForm.value.items.push(row)
    // 商品档案字段以弹窗带来的为准，这里只补实时库存；没库存时提示一句。
    // 必须从数组里取回响应式代理再改：直接改 push 进去的原始对象不会触发重渲染，
    // 表格会一直停在「当前库存 0」，调拨数量的 max 也跟着卡在 0。
    refreshRowStock(addForm.value.items[addForm.value.items.length - 1], true)
  })
}

/** 删除商品行 */
function removeItem(index) {
  addForm.value.items.splice(index, 1)
}

/**
 * 重新查询一行的实时库存与批次。商品档案字段由「添加商品」带出，这里一概不改。
 * warnEmpty 用于刚添加的行，见下面的提示。
 */
async function refreshRowStock(row, warnEmpty = false) {
  if (!row.goodsCode || !addForm.value.fromWareCode || addForm.value.qualityFlag == null) {
    row.currentQty = 0
    row.availableBatches = []
    return
  }
  const goodsCode = row.goodsCode
  const wareCode = addForm.value.fromWareCode
  const state = transferState()
  try {
    const [stockResponse, goodsResponse] = await Promise.all([
      getStock(goodsCode, wareCode, state),
      getGoods(goodsCode)
    ])
    if (row.goodsCode !== goodsCode || addForm.value.fromWareCode !== wareCode || addForm.value.qualityFlag !== state.qualityFlag) return
    row.currentQty = stockResponse.data ? Number(stockResponse.data.physicalQty) || 0 : 0
    // 建单时定死了良品状态，商品在这一格没库存就提示一句，
    // 否则用户只会看到「当前库存 0」，不知道是货没有还是状态选错了
    if (warnEmpty && row.currentQty <= 0) {
      proxy.$modal.msgWarning(
        `商品“${goodsCode}”在该仓库没有${qualityLabel(state.qualityFlag)}库存，本单只能添加${qualityLabel(state.qualityFlag)}`)
    }
    row.batchStrategy = goodsResponse.data?.batchStrategy || 'none'
    if (row.batchStrategy !== 'serial') row.serialNumbers = []
    if (row.batchStrategy === 'none') row.batchCode = ''
    if (row.batchStrategy === 'batch') {
      const batchResponse = await listAvailableBatch({
        goodsCode,
        wareCode,
        ...state,
        pageSize: 100
      })
      if (row.goodsCode !== goodsCode || addForm.value.fromWareCode !== wareCode || addForm.value.qualityFlag !== state.qualityFlag) return
      row.availableBatches = batchResponse.data || batchResponse.rows || []
    } else {
      row.availableBatches = []
    }
    handleQtyChange(row, false)
  } catch (error) {
    row.currentQty = 0
    row.batchStrategy = 'none'
    row.availableBatches = []
    console.error('获取库存信息失败', error)
  }
}

function clearTrackingSelection(row) {
  clearInventoryTracking(row)
  row.availableBatches = []
}

function handleBatchChange(row) {
  const batch = row.availableBatches?.find(item => item.batchCode === row.batchCode)
  if (batch && Number(row.qty) > Number(batch.remainQty)) {
    row.qty = Number(batch.remainQty)
  }
}

function availableTransferQuantity(row) {
  const stockQty = availableInventoryQuantity(row)
  if (row.batchStrategy !== 'batch' || !row.batchCode) return stockQty
  const batch = row.availableBatches?.find(item => item.batchCode === row.batchCode)
  if (!batch) return stockQty
  const batchQty = Number(batch.remainQty)
  return Number.isFinite(batchQty) && batchQty >= 0 ? Math.min(stockQty, batchQty) : stockQty
}

function handleQtyChange(row, showWarning = true) {
  const maxQty = availableTransferQuantity(row)
  const qty = Number(row.qty) || 0
  if (qty > maxQty) {
    row.qty = maxQty
    if (showWarning) proxy.$modal.msgWarning(`调拨数量不能大于当前库存 ${maxQty}`)
  }
  if (row.batchStrategy === 'serial') {
    if (!Number.isInteger(Number(row.qty)) && showWarning) proxy.$modal.msgWarning('序列号商品的调拨数量必须是整数')
    if ((row.serialNumbers?.length || 0) > 0 && row.serialNumbers.length !== Number(row.qty)) {
      clearTrackingSelection(row)
      if (showWarning) proxy.$modal.msgWarning('调拨数量已变化，请重新选择序列号')
    }
  }
}

/** 提交新增/编辑 */
function submitAdd() {
  proxy.$refs['addRef'].validate(valid => {
    if (valid) {
      if (addForm.value.fromWareCode === addForm.value.toWareCode) {
        proxy.$modal.msgWarning('来源仓库和目标仓库不能相同')
        return
      }

      const items = addForm.value.items.filter(item => item.qty > 0)
      if (items.length === 0) {
        proxy.$modal.msgWarning('请添加调拨商品并填写数量')
        return
      }

      const invalidItem = items.find(item => !item.goodsCode || !item.goodsName)
      if (invalidItem) {
        proxy.$modal.msgWarning('请填写有效的商品编码')
        return
      }
      const overStockItem = items.find(item => Number(item.qty) > availableTransferQuantity(item))
      if (overStockItem) {
        proxy.$modal.msgWarning(`商品“${overStockItem.goodsName || overStockItem.goodsCode}”的调拨数量不能大于当前库存`)
        return
      }
      const invalidSerialItem = items.find(item => item.batchStrategy === 'serial' && !serialQuantityMatches(item))
      if (invalidSerialItem) {
        proxy.$modal.msgWarning(`商品“${invalidSerialItem.goodsName || invalidSerialItem.goodsCode}”必须选择与调拨数量相同的序列号`)
        return
      }
      const invalidBatchItem = items.find(item => item.batchStrategy === 'batch' && !item.batchCode)
      if (invalidBatchItem) {
        proxy.$modal.msgWarning(`商品“${invalidBatchItem.goodsName || invalidBatchItem.goodsCode}”请选择调拨批次`)
        return
      }

      if (addForm.value.docCode) {
        // 编辑模式
        const data = {
          docCode: addForm.value.docCode,
          docType: 'DB',
          fromWareCode: addForm.value.fromWareCode,
          fromWareName: addForm.value.fromWareName,
          ...transferStateFields('from'),
          toWareCode: addForm.value.toWareCode,
          toWareName: addForm.value.toWareName,
          ...transferStateFields('to'),
          remark: addForm.value.remark || undefined,
          details: items.map(item => ({
            goodsCode: item.goodsCode,
            goodsName: item.goodsName,
            spec: item.spec,
            unit: item.unit,
            qty: item.qty,
            batchCode: item.batchCode || undefined,
            serialNumbers: item.serialNumbers && item.serialNumbers.length > 0 ? item.serialNumbers : undefined
          }))
        }
        updateDocument(data).then(() => {
          proxy.$modal.msgSuccess('修改成功')
          addOpen.value = false
          getList()
        }).catch(error => {
          proxy.$modal.msgError(error.message || '修改失败')
        })
      } else {
        // 新增模式
        const params = {
          fromWareCode: addForm.value.fromWareCode,
          ...transferStateFields('from'),
          toWareCode: addForm.value.toWareCode,
          ...transferStateFields('to'),
          remark: addForm.value.remark || undefined
        }
        const data = items.map(item => ({
          goodsCode: item.goodsCode,
          qty: item.qty,
          batchCode: item.batchCode || undefined,
          serialNumbers: item.serialNumbers && item.serialNumbers.length > 0 ? item.serialNumbers : undefined
        }))
        createTransfer(params, data).then(() => {
          proxy.$modal.msgSuccess('调拨单创建成功')
          addOpen.value = false
          getList()
        }).catch(error => {
          proxy.$modal.msgError(error.message || '创建失败')
        })
      }
    }
  })
}

/** 打开序列号选择对话框 */
async function openSerialDialog(index) {
  serialDialogIndex.value = index
  const item = addForm.value.items[index]
  if (item.batchStrategy !== 'serial') return
  if (!item.goodsCode || !addForm.value.fromWareCode || addForm.value.qualityFlag == null) {
    proxy.$modal.msgWarning('请先选择来源仓库、库存状态和商品')
    return
  }
  if (!serialDialogMax.value) {
    proxy.$modal.msgWarning('请先填写整数调拨数量')
    return
  }
  serialDialogSelected.value = [...(item.serialNumbers || [])]
  serialDialogOptions.value = []
  serialDialogOpen.value = true
  serialDialogLoading.value = true
  try {
    const response = await listInStockSerial(item.goodsCode, addForm.value.fromWareCode, transferState())
    serialDialogOptions.value = Array.isArray(response.data) ? response.data : (response.rows || [])
  } catch (error) {
    proxy.$modal.msgError('获取可用序列号失败')
  } finally {
    serialDialogLoading.value = false
  }
}

/** 确认序列号选择 */
function confirmSerialDialog() {
  const index = serialDialogIndex.value
  if (index >= 0 && index < addForm.value.items.length) {
    const selected = [...new Set(serialDialogSelected.value)]
    if (selected.length > serialDialogMax.value) {
      proxy.$modal.msgWarning(`最多选择 ${serialDialogMax.value} 个序列号`)
      return
    }
    applySerialSelection(addForm.value.items[index], selected, serialDialogOptions.value)
  }
  serialDialogOpen.value = false
}

/** 查看详情 */
async function handleView(row) {
  try {
    const response = await getDocument(row.docCode, row.tableSuffix)
    detail.value = response.data
    detailOpen.value = true
  } catch (error) {
    proxy.$modal.msgError('获取单据详情失败')
  }
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('确认要删除调拨单"' + row.docCode + '"吗？').then(function () {
    return delDocument(row.docCode, row.tableSuffix)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 批量删除按钮操作 */
function handleDeleteBatch() {
  const docCodes = ids.value
  proxy.$modal.confirm('确认要删除选中的' + docCodes.length + '个调拨单吗？').then(function () {
    const promises = docCodes.map(docCode => {
      const row = transferList.value.find(item => item.docCode === docCode)
      return delDocument(docCode, row?.tableSuffix)
    })
    return Promise.all(promises)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('批量删除成功')
  }).catch(() => {})
}

/** 审核操作 */
function handleConfirm(row, approved) {
  const action = approved ? '审核通过' : '驳回'
  proxy.$modal.confirm('确认' + action + '调拨单"' + row.docCode + '"？').then(function () {
    return confirmDocument(row.docCode, row.tableSuffix, approved)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(action + '成功')
  }).catch(() => {})
}

/**
 * 认首页看板带过来的 ?confirmState=待审核。调拨只有 DB 一种类型，不看 docType。
 *
 * @returns 是否真的改了筛选条件
 */
function applyRouteFilter() {
  const wanted = useRoute().query.confirmState
  if (!wanted || queryParams.value.confirmState === wanted) return false
  queryParams.value.confirmState = wanted
  queryParams.value.pageNum = 1
  return true
}

// keep-alive 缓存下第二次点进来只会触发 onActivated，理由同出库页
onActivated(() => {
  if (applyRouteFilter()) getList()
})

applyRouteFilter()
getList()
loadWarehouseOptions()
</script>

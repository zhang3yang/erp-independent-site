<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="单据代码" prop="docCode">
        <el-input v-model="queryParams.docCode" placeholder="请输入单据代码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="出库类型" prop="docType">
        <el-select v-model="queryParams.docType" placeholder="请选择类型" clearable style="width: 240px">
          <el-option v-for="item in OUTBOUND_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['inventory:outbound:add']">新建出库单</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDeleteBatch" v-hasPermi="['inventory:outbound:remove']">批量删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="outboundList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="单据代码" align="center" prop="docCode" width="150" />
      <el-table-column label="单据类型" align="center" prop="docTypeName" width="100" />
      <el-table-column label="单据日期" align="center" prop="docDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.docDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="来源仓库" align="center" prop="fromWareName" width="100" />
      <el-table-column label="往来单位" align="center" prop="partnerName" :show-overflow-tooltip="true" />
      <!-- 商品内容：后端按明细算好的一句「可乐等3种」，纯展示，不展开 -->
      <el-table-column label="商品内容" align="center" prop="goodsSummary" min-width="160" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.goodsSummary || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="总数量" align="center" prop="totalQty" width="100" />
      <el-table-column v-if="canSeeEitherPrice" label="总金额" align="center" prop="totalAmount" width="120">
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
      <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button v-if="scope.row.confirmState === '待审核' || scope.row.confirmState === '已驳回'" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['inventory:outbound:edit']">编辑</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="success" icon="Check" @click="handleConfirm(scope.row, true)" v-hasPermi="['inventory:outbound:approve']">审核</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="danger" icon="Close" @click="handleConfirm(scope.row, false)" v-hasPermi="['inventory:outbound:approve']">驳回</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['inventory:outbound:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新建/编辑出库单对话框 -->
    <el-dialog :title="dialogTitle" v-model="addOpen" width="92%" top="5vh" class="inventory-document-dialog" :close-on-click-modal="false" append-to-body>
      <el-form :model="addForm" :rules="addRules" ref="addRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <!-- 编辑已有单据时类型不给改：改了单号前缀、明细号段都对不上 -->
            <el-form-item label="出库类型" prop="docType">
              <el-select v-if="!addForm.docCode" v-model="addForm.docType" placeholder="请选择类型" style="width: 100%">
                <el-option v-for="item in CREATABLE_OUTBOUND_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input v-else :model-value="outboundTypeLabel(addForm.docType)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源仓库" prop="fromWareCode">
              <el-select v-model="addForm.fromWareCode" placeholder="请选择仓库" style="width: 100%" @change="handleWarehouseChange" :disabled="!!addForm.docCode">
                <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 这个入口只出良品；坏货要出库走「损坏管理 → 损坏出库」，扣的是另一格库存 -->
            <el-form-item label="良品状态">
              <el-input :model-value="qualityLabel(addForm.fromQualityFlag)" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="往来单位名称" prop="partnerName">
              <el-input v-model="addForm.partnerName" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="addForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>

        <div class="detail-section-header">
          <div>
            <div class="detail-section-title">出库明细</div>
            <div class="detail-section-tip">商品编码、名称、规格、单位由「添加商品」从商品档案带出，不可修改；可编辑数量、批次与序列号</div>
          </div>
          <div class="detail-section-actions">
            <el-tag type="info" effect="plain">共 {{ addForm.items.length }} 项</el-tag>
            <el-button type="primary" icon="Plus" size="small" @click="addItem">添加商品</el-button>
          </div>
        </div>

        <el-table :data="addForm.items" border stripe max-height="360" empty-text="点击右上方“添加商品”开始录入" class="document-detail-table">
          <!-- 编码、名称、规格、单位取自商品档案，只能通过「添加商品」选出来，单据上不可改 -->
          <el-table-column label="商品编码" prop="goodsCode" width="180" fixed="left" />
          <el-table-column label="商品名称" prop="goodsName" min-width="180" fixed="left" show-overflow-tooltip />
          <el-table-column label="规格" prop="spec" min-width="150" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="120" align="center" />
          <el-table-column label="当前库存" width="150" align="right">
            <template #default="scope">
              <span class="readonly-qty">{{ formatQty(scope.row.currentQty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="出库数量" width="170" class-name="editable-column">
            <template #default="scope">
              <!-- 不设 precision：默认按整数显示和步进，用户手输小数照样收下并原样提交 -->
              <el-input-number v-model="scope.row.qty" :min="0" :max="availableInventoryQuantity(scope.row)" :step="1" size="small" controls-position="right" style="width: 100%" @change="handleQtyChange(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale && isSalesOutbound" label="销售单价" width="180" class-name="editable-column">
            <template #default="scope">
              <!-- 默认补足两位（9 显示成 9.00），手输超过两位就按输的留。
                   上限 4 位：后端 saleprice 是 decimal(18,4)，与 inventorySalePriceScale 同一个口径 -->
              <decimal-input v-model="scope.row.salePrice" :max-decimals="4" size="small"
                style="width: 100%" placeholder="入库成本" @change="handleSalePriceChange(scope.row)" />
              <div v-if="canSeeCost && isSalePriceOverridden(scope.row)" class="price-hint">
                入库成本 {{ formatCurrency(inventorySalePriceScale(scope.row.costPrice)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale && isSalesOutbound" label="销售金额" width="130" align="right">
            <template #default="scope">
              <span class="readonly-qty">{{ formatCurrency(inventorySaleAmount(scope.row)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次" width="180" class-name="editable-column">
            <template #default="scope">
              <el-select v-if="isBatchSelectable(scope.row)" v-model="scope.row.batchCode" placeholder="请选择批次" size="small" style="width: 100%" @change="handleBatchChange(scope.row)">
                <el-option v-for="batch in scope.row.availableBatches" :key="batch.batchCode" :label="`${batch.batchCode}（余${batch.remainQty}）`" :value="batch.batchCode" />
              </el-select>
              <span v-else-if="scope.row.batchStrategy === 'serial'" class="readonly-tracking">{{ scope.row.batchDisplay || scope.row.batchCode || '由序列号带出' }}</span>
              <span v-else-if="scope.row.fifo" class="readonly-tracking">FIFO 自动分配</span>
              <span v-else-if="scope.row.batchStrategy === 'batch'" class="readonly-tracking">不按批次出库</span>
              <span v-else class="readonly-tracking">-</span>
            </template>
          </el-table-column>
          <el-table-column label="序列" width="130" align="center" class-name="editable-column">
            <template #default="scope">
              <el-button link type="primary" size="small" @click="openSerialDialog(scope.$index)" :disabled="scope.row.batchStrategy !== 'serial'">
                {{ scope.row.batchStrategy !== 'serial' ? '无需填写' : (scope.row.serialNumbers && scope.row.serialNumbers.length > 0 ? scope.row.serialNumbers.length + '个' : '必须输入') }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
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
    <el-dialog title="出库单详情" v-model="detailOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单据编号">{{ detail.docCode }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ detail.docTypeName }}</el-descriptions-item>
        <el-descriptions-item label="单据日期">{{ parseTime(detail.docDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="来源仓库">{{ detail.fromWareName }}</el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ detail.partnerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ detail.totalQty }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeDocTotalAmount(detail.docType)" label="总金额">{{ formatCurrency(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.confirmState === '已审核' ? 'success' : detail.confirmState === '已驳回' ? 'danger' : 'warning'">
            {{ detail.confirmState }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.confirmUserName || detail.confirmUser || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">出库明细</el-divider>

      <el-table :data="detail.details" border>
        <el-table-column label="商品编码" prop="goodsCode" width="120" />
        <el-table-column label="商品名称" prop="goodsName" :show-overflow-tooltip="true" />
        <el-table-column label="规格" prop="spec" width="100" />
        <el-table-column label="单位" prop="unit" width="60" />
        <el-table-column label="数量" prop="qty" width="100" />
        <el-table-column v-if="canSeeSale && detail.docType === SALES_OUTBOUND_TYPE" label="销售单价" prop="salePrice" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.salePrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canSeeSale && detail.docType === SALES_OUTBOUND_TYPE" label="销售金额" prop="saleAmount" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.saleAmount) }}</span>
          </template>
        </el-table-column>
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
        仅显示当前商品、仓库和库存状态下的在库序列号；已选 {{ serialDialogSelected.length }} 个，出库数量为 {{ serialDialogMax }} 个
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

<script setup name="OutboundManagement">
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import DecimalInput from '@/components/DecimalInput'
import { formatCurrency } from '@/utils/currency'
import { listDocument, getDocument, updateDocument, delDocument, confirmDocument } from '@/api/inventory/document'
import { createOutbound } from '@/api/inventory/outbound'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { QUALITY_FLAG, ONHAND_FLAG, qualityLabel } from '@/utils/stockState'
import { getStock } from '@/api/inventory/stock'
import { getGoods } from '@/api/inventory/goods'
import { listAvailableBatch } from '@/api/inventory/batch'
import { listInStockSerial } from '@/api/inventory/serial'
import {
  applySerialSelection,
  availableInventoryQuantity,
  createInventoryDocumentLine,
  inventorySaleAmount,
  inventorySalePriceOverridden,
  inventorySalePriceScale,
  serialQuantityMatches
} from '@/utils/inventoryDocument'
import { canSeeCost, canSeeSale, canSeeEitherPrice, canSeeDocTotalAmount } from '@/utils/price'

/** 销售出库单据类型，只有该类型按入库成本带出销售价。 */
const SALES_OUTBOUND_TYPE = 'CK'

const { proxy } = getCurrentInstance()

const outboundList = ref([])
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
const dialogTitle = ref('新建出库单')
// 新建前先选良品状态的小弹框

/**
 * 出库类型选项，查询条件和只读展示用这一份（含历史单据才有的类型）。
 * 采购退货出库由「采购退货」模块自己开单，损坏出库由「损坏管理 → 损坏出库」开，
 * 这里只负责把它们的名字显示出来。
 */
const OUTBOUND_TYPE_OPTIONS = [
  { value: 'CK', label: '销售出库' },
  { value: 'LLCK', label: '领料出库' },
  { value: 'CGTHCK', label: '采购退货出库' },
  { value: 'SHCK', label: '损坏出库' },
  { value: 'OO', label: '其他出库' }
]

/** 「新建出库单」能选的类型：退货、报废各有各的入口，不在这里建 */
const CREATABLE_OUTBOUND_TYPES = OUTBOUND_TYPE_OPTIONS.filter(
  item => item.value !== 'CGTHCK' && item.value !== 'SHCK'
)

function outboundTypeLabel(value) {
  return OUTBOUND_TYPE_OPTIONS.find(item => item.value === value)?.label || value || ''
}
const serialDialogOpen = ref(false)
const serialDialogIndex = ref(-1)
const serialDialogSelected = ref([])
const serialDialogOptions = ref([])
const serialDialogLoading = ref(false)
const goodsSelectRef = ref(null)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    docCode: undefined,
    docType: undefined,
    confirmState: undefined,
    // 采购退货出库(CGTHCK)、损坏出库(SHCK) 各有各的页面管理，不在这里列
    docTypes: 'CK,LLCK,OO',
    docDateBegin: undefined,
    docDateEnd: undefined
  },
  addForm: {
    docCode: '',
    docDate: '',
    docType: 'CK',
    fromWareCode: '',
    fromQualityFlag: QUALITY_FLAG.GOOD,
    // 出库的货必然在仓库里、也必然是自有的：借出、维修、生产领用中的货
    // 要先走对应的归还单回到自有才能出库，所以这两维不给选，固定死
    fromOnhandFlag: ONHAND_FLAG.IN,
    partnerCode: '',
    partnerName: '',
    remark: '',
    items: []
  },
  addRules: {
    docType: [{ required: true, message: '请选择出库类型', trigger: 'change' }],
    fromWareCode: [{ required: true, message: '请选择来源仓库', trigger: 'change' }],
    fromQualityFlag: [{ required: true, message: '请选择良品状态', trigger: 'change' }]
  }
})

const { queryParams, addForm, addRules } = toRefs(data)

// 不填就是查全部月份。单据按 docdate 分月表，后端会挑出区间覆盖到的月表；
// 留空时扫全部现存月表——跨月之后查不到上个月的出库单才是真问题。
const docDateRange = ref([])

/**
 * 出库只从仓库里拿得到的货扣：在库、自有，良品还是不良品由页面选。
 * 借出、维修、生产领用中的货要先走对应的归还单回到自有才能出库。
 */
function outboundState() {
  return {
    qualityFlag: addForm.value.fromQualityFlag,
    onhandFlag: addForm.value.fromOnhandFlag

  }
}

/** 三维状态是否都选好了；没选全就不去查库存 */
function hasStockContext() {
  const state = outboundState()
  return state.qualityFlag != null && state.onhandFlag != null
}

/** 摊平成单据的 from 三维字段 */
function outboundStateFields() {
  const state = outboundState()
  return { fromQualityFlag: state.qualityFlag, fromOnhandFlag: state.onhandFlag }
}

/** 只有销售出库按入库成本带出销售价，其余出库类型不展示销售价。 */
const isSalesOutbound = computed(() => addForm.value.docType === SALES_OUTBOUND_TYPE)

/** 已在明细里的商品，选商品弹窗用它打「已添加」标记 */
const selectedGoodsCodes = computed(() => addForm.value.items.map(row => row.goodsCode).filter(Boolean))

/**
 * 用库存的移动平均成本估算本行入库成本，与后端开单时的口径一致。
 * FIFO、个别计价的实际成本要到审核按批次、序列号结算时才确定。
 */
function estimateStockCostPrice(stock) {
  const physicalQty = Number(stock?.physicalQty)
  const totalCostAmount = Number(stock?.totalCostAmount)
  if (Number.isFinite(physicalQty) && physicalQty > 0
      && Number.isFinite(totalCostAmount) && totalCostAmount > 0) {
    return inventorySalePriceScale(totalCostAmount / physicalQty)
  }
  const avgCostPrice = Number(stock?.avgCostPrice)
  return Number.isFinite(avgCostPrice) && avgCostPrice > 0 ? inventorySalePriceScale(avgCostPrice) : null
}

/**
 * 只有个别计价商品需要业务员指定出库批次，它的成本就来自那个批次。
 * FIFO 由审核时自动分配，加权平均直接扣汇总库存、不消耗批次，都不用选。
 */
function isBatchSelectable(row) {
  return row.batchStrategy === 'batch' && row.costCalculation === 'individual'
}

/** 成本变化后同步未改价的行；改过价的行保留业务员填的值。 */
function syncSalePrice(row) {
  if (row.salePriceEdited) return
  row.salePrice = row.costPrice == null ? null : inventorySalePriceScale(row.costPrice)
}

/** 业务员手工改价后，该行不再跟随成本变化。 */
function handleSalePriceChange(row) {
  row.salePriceEdited = inventorySalePriceOverridden(row.salePrice, row.costPrice)
}

/** 售价与入库成本不一致时才提示成本，避免默认带出的行也显示一遍。 */
function isSalePriceOverridden(row) {
  return row.costPrice != null && inventorySalePriceOverridden(row.salePrice, row.costPrice)
}

const serialDialogMax = computed(() => {
  const index = serialDialogIndex.value
  if (index < 0 || index >= addForm.value.items.length) return 0
  const qty = Number(addForm.value.items[index].qty)
  return Number.isInteger(qty) && qty > 0 ? qty : 0
})

/** 查询出库单列表 */
async function getList() {
  loading.value = true
  const range = docDateRange.value
  queryParams.value.docDateBegin = range && range.length ? range[0] + ' 00:00:00' : undefined
  queryParams.value.docDateEnd = range && range.length ? range[1] + ' 23:59:59' : undefined
  try {
    const response = await listDocument(queryParams.value)
    outboundList.value = response.rows
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

/** 仓库选择变化 */
function handleWarehouseChange(value) {
  const warehouse = warehouseOptions.value.find(item => item.wareCode === value)
  if (warehouse) {
    addForm.value.fromWareName = warehouse.wareName
  }
  refreshAllRowStocks()
}

/**
 * 新增按钮：直接进主弹框，出库类型在表单里选。
 *
 * 这个入口只出良品：良品和不良品在库存表里是两格独立的库存，一张单只能扣其中一格，
 * 坏货报废走「损坏管理 → 损坏出库」，那边扣的是「不良品·在库」那一格。
 */
function handleAdd() {
  addForm.value = {
    docCode: '',
    docDate: '',
    docType: 'CK',
    fromWareCode: '',
    fromQualityFlag: QUALITY_FLAG.GOOD,
    // 出库的货必然在仓库里、也必然是自有的：借出、维修、生产领用中的货
    // 要先走对应的归还单回到自有才能出库，所以这两维不给选，固定死
    fromOnhandFlag: ONHAND_FLAG.IN,
    partnerCode: '',
    partnerName: '',
    remark: '',
    items: []
  }
  dialogTitle.value = '新建出库单'
  addOpen.value = true
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  try {
    const response = await getDocument(row.docCode, row.tableSuffix)
    const doc = response.data
    addForm.value = {
      docCode: doc.docCode,
      docDate: doc.docDate || '',
      docType: doc.docType,
      fromWareCode: doc.fromWareCode,
      fromQualityFlag: doc.fromQualityFlag,
      fromOnhandFlag: doc.fromOnhandFlag ?? ONHAND_FLAG.IN,
      partnerCode: doc.partnerCode || '',
      partnerName: doc.partnerName || '',
      remark: doc.remark || '',
      items: doc.details?.map(d => createInventoryDocumentLine({
        detailCode: d.detailCode,
        goodsCode: d.goodsCode,
        goodsName: d.goodsName,
        spec: d.spec,
        unit: d.unit,
        currentQty: 0,
        qty: d.qty,
        costPrice: d.costPrice,
        costAmount: d.costAmount,
        // 回填本单已确定的销售价快照。改过价的行标记出来，后面刷新库存成本时不覆盖它；
        // 未改价的行会跟着当前入库成本重新带出，与后端编辑单据的口径一致。
        salePrice: d.salePrice == null ? null : Number(d.salePrice),
        salePriceEdited: inventorySalePriceOverridden(d.salePrice, d.costPrice),
        batchCode: d.batchCode || '',
        batchDisplay: d.batchCode || '',
        serialNumbers: d.serialNumbers || [],
        batchStrategy: 'none',
        fifo: false,
        availableBatches: []
      })) || []
    }
    dialogTitle.value = '编辑出库单'
    addOpen.value = true
    refreshAllRowStocks()
    refreshAllTrackingData()
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
    // 表格会一直停在「当前库存 0」，出库数量的 max 也跟着卡在 0。
    refreshRowStock(addForm.value.items[addForm.value.items.length - 1], true)
  })
}

/** 删除商品行 */
function removeItem(index) {
  addForm.value.items.splice(index, 1)
}

/**
 * 重新查询一行的实时库存。商品档案字段由「添加商品」带出，这里一概不改。
 * warnEmpty 用于刚添加的行：建单时定死了良品状态，商品在这一格没库存就提示一句，
 * 否则用户只会看到「当前库存 0」，不知道是货没有还是状态选错了。
 */
async function refreshRowStock(row, warnEmpty = false) {
  if (!row.goodsCode || !addForm.value.fromWareCode || !hasStockContext()) {
    row.currentQty = 0
    row.qty = 0
    return
  }
  const goodsCode = row.goodsCode
  const wareCode = addForm.value.fromWareCode
  const state = outboundState()
  try {
    const response = await getStock(goodsCode, wareCode, state)
    if (!matchesStockContext(row, goodsCode, wareCode, state)) {
      return
    }
    row.currentQty = response.data ? Number(response.data.physicalQty) || 0 : 0
    row.costPrice = response.data ? estimateStockCostPrice(response.data) : null
    syncSalePrice(row)
    if (warnEmpty && row.currentQty <= 0) {
      proxy.$modal.msgWarning(
        `商品“${goodsCode}”在该仓库没有${qualityLabel(state.qualityFlag)}库存，本单只能添加${qualityLabel(state.qualityFlag)}`)
    }
  } catch (error) {
    if (matchesStockContext(row, goodsCode, wareCode, state)) {
      row.currentQty = 0
    }
    console.error('获取库存信息失败', error)
  }
  handleQtyChange(row, false)
  await loadTrackingData(row)
}

async function loadTrackingData(row) {
  if (!row.goodsCode) return
  try {
    const response = await getGoods(row.goodsCode)
    const goods = response.data
    row.batchStrategy = goods?.batchStrategy || 'none'
    row.costCalculation = goods?.costCalculation || 'moving_avg'
    row.fifo = row.batchStrategy === 'batch' && goods?.costCalculation === 'fifo'
    if (isBatchSelectable(row) && addForm.value.fromWareCode && addForm.value.fromQualityFlag != null) {
      const batchResponse = await listAvailableBatch({
        goodsCode: row.goodsCode,
        wareCode: addForm.value.fromWareCode,
        ...outboundState()
      })
      row.availableBatches = batchResponse.data || batchResponse.rows || []
    } else {
      row.availableBatches = []
      // 不按批次出库的商品不留批次残值，避免把换商品前选中的批次提交上去。
      // 序列号商品的批次由序列号带出，不在这里清。
      if (row.batchStrategy !== 'serial') {
        row.batchCode = ''
        row.batchDisplay = ''
      }
    }
  } catch (error) {
    row.batchStrategy = 'none'
    row.fifo = false
    row.availableBatches = []
  }
}

async function refreshAllTrackingData() {
  for (const row of addForm.value.items) await loadTrackingData(row)
}

function handleBatchChange(row) {
  const batch = row.availableBatches.find(item => item.batchCode === row.batchCode)
  if (batch && Number(row.qty) > Number(batch.remainQty)) row.qty = Number(batch.remainQty)
  // 个别计价按所选批次结算成本，选定批次后售价的基准也要换成该批次的成本。
  // 移动平均、FIFO 仍以库存平均成本估算，实际成本由审核时结算。
  if (row.costCalculation === 'individual') {
    const batchCostPrice = Number(batch?.costPrice)
    if (Number.isFinite(batchCostPrice) && batchCostPrice > 0) {
      row.costPrice = inventorySalePriceScale(batchCostPrice)
      syncSalePrice(row)
    }
  }
}

function matchesStockContext(row, goodsCode, wareCode, state) {
  const current = outboundState()
  return row.goodsCode === goodsCode
    && addForm.value.fromWareCode === wareCode
    && current.qualityFlag === state.qualityFlag
    && current.onhandFlag === state.onhandFlag
}

function refreshAllRowStocks() {
  addForm.value.items.forEach(row => refreshRowStock(row))
}

function formatQty(value) {
  return Number(availableInventoryQuantity({ currentQty: value }).toFixed(4))
}

function handleQtyChange(row, showWarning = true) {
  const maxQty = availableInventoryQuantity(row)
  const qty = Number(row.qty) || 0
  if (qty > maxQty) {
    row.qty = maxQty
    if (showWarning) {
      proxy.$modal.msgWarning(`出库数量不能大于当前库存（${formatQty(maxQty)}）`)
    }
  }
  if (row.batchStrategy === 'serial') {
    const normalizedQty = Number(row.qty) || 0
    if (!Number.isInteger(normalizedQty) && showWarning) {
      proxy.$modal.msgWarning('序列号商品的出库数量必须是整数')
    }
    if (row.serialNumbers?.length > Math.max(0, Math.floor(normalizedQty))) {
      row.serialNumbers = []
      row.batchCode = ''
      if (showWarning) proxy.$modal.msgWarning('出库数量已变化，请重新选择序列号')
    }
  }
}

/** 提交新增/编辑 */
function submitAdd() {
  proxy.$refs['addRef'].validate(valid => {
    if (valid) {
      const items = addForm.value.items.filter(item => item.qty > 0)
      if (items.length === 0) {
        proxy.$modal.msgWarning('请添加出库商品并填写数量')
        return
      }

      const invalidItem = items.find(item => !item.goodsCode || !item.goodsName)
      if (invalidItem) {
        proxy.$modal.msgWarning('请填写商品编码和商品名称')
        return
      }

      const overStockItem = items.find(item => Number(item.qty) > availableInventoryQuantity(item))
      if (overStockItem) {
        proxy.$modal.msgWarning(`商品“${overStockItem.goodsName || overStockItem.goodsCode}”的出库数量不能大于当前库存（${formatQty(overStockItem.currentQty)}）`)
        return
      }

      const serialItem = items.find(item => item.batchStrategy === 'serial' && !serialQuantityMatches(item))
      if (serialItem) {
        proxy.$modal.msgWarning(`商品“${serialItem.goodsName || serialItem.goodsCode}”必须填写与出库数量相同的序列号`)
        return
      }

      const batchItem = items.find(item => isBatchSelectable(item) && !item.batchCode)
      if (batchItem) {
        proxy.$modal.msgWarning(`商品“${batchItem.goodsName || batchItem.goodsCode}”请选择出库批次`)
        return
      }

      if (addForm.value.docCode) {
        // 编辑模式
        const data = {
          docCode: addForm.value.docCode,
          docDate: addForm.value.docDate || undefined,
          docType: addForm.value.docType,
          fromWareCode: addForm.value.fromWareCode,
          fromWareName: warehouseOptions.value.find(w => w.wareCode === addForm.value.fromWareCode)?.wareName || '',
          ...outboundStateFields(),
          partnerCode: addForm.value.partnerCode || undefined,
          partnerName: addForm.value.partnerName || undefined,
          remark: addForm.value.remark || undefined,
          details: items.map(item => ({
            detailCode: item.detailCode || undefined,
            goodsCode: item.goodsCode,
            goodsName: item.goodsName,
            spec: item.spec,
            unit: item.unit,
            qty: item.qty,
            costPrice: item.costPrice,
            costAmount: item.costAmount,
            salePrice: isSalesOutbound.value ? item.salePrice ?? undefined : undefined,
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
          docType: addForm.value.docType,
          fromWareCode: addForm.value.fromWareCode,
          ...outboundStateFields(),
          partnerCode: addForm.value.partnerCode || undefined,
          partnerName: addForm.value.partnerName || undefined,
          remark: addForm.value.remark || undefined
        }
        const data = items.map(item => ({
          goodsCode: item.goodsCode,
          goodsName: item.goodsName,
          spec: item.spec,
          unit: item.unit,
          qty: item.qty,
          salePrice: isSalesOutbound.value ? item.salePrice ?? undefined : undefined,
          batchCode: item.batchCode || undefined,
          serialNumbers: item.serialNumbers && item.serialNumbers.length > 0 ? item.serialNumbers : undefined
        }))
        createOutbound(params, data).then(() => {
          proxy.$modal.msgSuccess('出库单创建成功')
          addOpen.value = false
          getList()
        }).catch(error => {
          proxy.$modal.msgError(error.message || '创建失败')
        })
      }
    }
  })
}

/** 打开序列号输入对话框 */
async function openSerialDialog(index) {
  serialDialogIndex.value = index
  const item = addForm.value.items[index]
  if (item.batchStrategy !== 'serial') return
  if (!addForm.value.fromWareCode || !item.goodsCode) {
    proxy.$modal.msgWarning('请先选择仓库和商品')
    return
  }
  serialDialogSelected.value = [...(item.serialNumbers || [])]
  serialDialogOptions.value = []
  serialDialogOpen.value = true
  serialDialogLoading.value = true
  try {
    const response = await listInStockSerial(item.goodsCode, addForm.value.fromWareCode, outboundState())
    const rows = Array.isArray(response.data) ? response.data : (response.rows || [])
    const options = rows.slice()
    serialDialogSelected.value.forEach(serialNo => {
      if (!options.some(option => option.serialNo === serialNo)) options.push({ serialNo, batchCode: '' })
    })
    serialDialogOptions.value = options
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
    if (serialDialogSelected.value.length > serialDialogMax.value) {
      proxy.$modal.msgWarning(`最多选择 ${serialDialogMax.value} 个序列号`)
      return
    }
    applySerialSelection(addForm.value.items[index], serialDialogSelected.value, serialDialogOptions.value)
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
  proxy.$modal.confirm('确认要删除出库单"' + row.docCode + '"吗？').then(function () {
    return delDocument(row.docCode, row.tableSuffix)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 批量删除按钮操作 */
function handleDeleteBatch() {
  const docCodes = ids.value
  proxy.$modal.confirm('确认要删除选中的' + docCodes.length + '个出库单吗？').then(function () {
    const promises = docCodes.map(docCode => {
      const row = outboundList.value.find(item => item.docCode === docCode)
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
  proxy.$modal.confirm('确认' + action + '出库单"' + row.docCode + '"？').then(function () {
    return confirmDocument(row.docCode, row.tableSuffix, approved)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(action + '成功')
  }).catch(() => {})
}

/**
 * 认首页看板带过来的 ?confirmState=待审核&docType=CK。
 *
 * 状态和类型筛选界面上本来就有，这里只是把 URL 参数灌进去，让用户落地时
 * 就看见筛选条上写着「待审核」，而不是拿到一个不知道被谁筛过的列表。
 *
 * @returns 是否真的改了筛选条件——没改就不必额外发一次请求
 */
function applyRouteFilter() {
  const query = useRoute().query
  let changed = false
  if (query.confirmState && queryParams.value.confirmState !== query.confirmState) {
    queryParams.value.confirmState = query.confirmState
    changed = true
  }
  if (query.docType && queryParams.value.docType !== query.docType) {
    queryParams.value.docType = query.docType
    changed = true
  }
  if (changed) queryParams.value.pageNum = 1
  return changed
}

// 本页被 keep-alive 缓存：第二次从看板点进来时组件不重新初始化，只有 onActivated 会触发。
// 不带筛选参数的普通切换不会多发请求。
onActivated(() => {
  if (applyRouteFilter()) getList()
})

applyRouteFilter()
getList()
loadWarehouseOptions()
</script>

<style scoped>
:deep(.inventory-document-dialog) {
  max-width: 1440px;
}

:deep(.inventory-document-dialog .el-dialog__body) {
  padding-top: 12px;
}

.detail-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 20px 0 12px;
  padding-left: 12px;
  border-left: 4px solid var(--el-color-primary);
}

.detail-section-title {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.detail-section-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-section-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

:deep(.document-detail-table th.el-table__cell) {
  height: 46px;
  color: #344054;
  background: #f4f7fb !important;
  font-weight: 600;
}

:deep(.document-detail-table td.el-table__cell) {
  padding: 10px 0;
}

/*
  同 inbound：原本给 .editable-column 刷的 #f5f9ff 浅蓝底已删除，
  写死的色值在暗色主题下是一排白块。要恢复用 var(--el-fill-color-light)。
*/

:deep(.document-detail-table .el-input__wrapper),
:deep(.document-detail-table .el-select__wrapper) {
  min-height: 34px;
}

.readonly-tracking {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.readonly-qty {
  display: inline-block;
  min-width: 100%;
  padding: 8px 10px;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

/** 改价后提示商品主档的基准价，便于业务员核对折让幅度。 */
.price-hint {
  margin-top: 2px;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.2;
}
</style>

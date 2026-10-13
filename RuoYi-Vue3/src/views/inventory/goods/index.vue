<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品分类" prop="category">
        <el-tree-select
          v-model="queryParams.category"
          :data="categoryOptions"
          placeholder="请选择商品分类"
          check-strictly
          filterable
          clearable
          style="width: 240px"
        >
          <template #default="{ data }">{{ data.shortLabel }}</template>
        </el-tree-select>
      </el-form-item>
      <el-form-item label="商品类型" prop="goodsType">
        <el-select v-model="queryParams.goodsType" placeholder="请选择商品类型" clearable style="width: 240px">
          <el-option label="原材料" value="raw" />
          <el-option label="半成品" value="semifinished" />
          <el-option label="成品" value="finished" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="activeFlag">
        <el-select v-model="queryParams.activeFlag" placeholder="请选择状态" clearable style="width: 240px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="goodsList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="商品主图" align="center" width="90">
        <template #default="scope">
          <image-preview v-if="goodsImageSource(scope.row)" :src="goodsImageSource(scope.row)" :width="50" :height="50" />
          <span v-else class="no-image">未上传</span>
        </template>
      </el-table-column>
      <el-table-column label="商品编码" align="center" prop="goodsCode" width="100">
        <template #default="scope">
          <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.goodsCode }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="商品名称" align="center" prop="goodsName" min-width="160" :show-overflow-tooltip="true" />
      <!-- 商品表只存末级分类名，显示时按分类树还原成「父级 / 子级 / 孙级」全路径 -->
      <el-table-column label="商品分类" align="center" prop="category" min-width="200" :show-overflow-tooltip="true">
        <template #default="scope">{{ categoryPath(scope.row.category) }}</template>
      </el-table-column>
      <el-table-column label="规格型号" align="center" prop="spec" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="单位" align="center" prop="unit" width="60" />
      <el-table-column label="商品类型" align="center" prop="goodsType" width="100">
        <template #default="scope">
          <el-tag :type="getGoodsTypeTag(scope.row.goodsType)">{{ getGoodsTypeLabel(scope.row.goodsType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeSale" label="参考价" align="center" prop="standardPrice" width="100">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.standardPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="activeFlag" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.activeFlag === 1 ? 'success' : 'danger'">{{ scope.row.activeFlag === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width op-column">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-tooltip v-if="scope.row.usedFlag === 1" content="已产生单据或库存记录，不能删除；如需停止使用请改为停用" placement="top">
            <span><el-button link type="info" icon="Delete" disabled>删除</el-button></span>
          </el-tooltip>
          <el-button v-else link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改商品对话框 -->
    <el-dialog :title="title" v-model="open" width="1000px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="goodsLocked" type="warning" :closable="false" show-icon class="lock-tip"
        title="该商品已产生入库单或库存记录"
        description="商品编码、名称、规格型号、计量单位、商品类型、批次策略、成本计算方法已被单据和成本引用，不能再修改；分类、参考价、库存预警量、图片、备注、状态仍可修改。" />
      <el-form :model="form" :rules="rules" ref="goodsRef" label-width="100px">
        <!-- 上半区：商品被单据用过之后会锁定的字段，集中放在一起；下半区才是随时可改的 -->
        <el-divider v-if="goodsLocked" content-position="left">已锁定，不可修改</el-divider>
        <!-- 三列排布，弹框不至于太长 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="商品编码" prop="goodsCode">
              <el-input v-model="form.goodsCode" placeholder="请输入商品编码" :disabled="!!form.goodsCode && !!form.shopCode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="商品名称" prop="goodsName">
              <el-input v-model="form.goodsName" placeholder="请输入商品名称" :disabled="goodsLocked" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格型号" prop="spec">
              <el-input v-model="form.spec" placeholder="请输入规格型号" :disabled="goodsLocked" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计量单位" prop="unit">
              <el-input v-model="form.unit" placeholder="请输入计量单位" :disabled="goodsLocked" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成本计算" prop="costCalculation">
              <!-- 成本计算方法与批次策略一一对应，只跟着批次策略走，不给用户单独选 -->
              <el-select v-model="form.costCalculation" style="width: 100%" disabled>
                <el-option label="移动加权平均" value="moving_avg" />
                <el-option label="先进先出" value="fifo" />
                <el-option label="个别计价" value="individual" />
              </el-select>
              <div v-if="legacyCostCalculation" class="form-tip form-tip-warning">
                原成本计算方法为「{{ getCostCalculationLabel(legacyCostCalculation) }}」，与批次策略不匹配，保存后按上述口径统一
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批次策略" prop="batchStrategy">
              <el-select v-model="form.batchStrategy" placeholder="请选择批次策略" style="width: 100%" :disabled="goodsLocked" @change="handleBatchStrategyChange">
                <el-option label="无批次" value="none" />
                <el-option label="批次管理" value="batch" />
                <el-option label="序列号管理" value="serial" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="商品类型" prop="goodsType">
              <el-select v-model="form.goodsType" placeholder="请选择商品类型" style="width: 100%" :disabled="goodsLocked">
                <el-option label="原材料" value="raw" />
                <el-option label="半成品" value="semifinished" />
                <el-option label="成品" value="finished" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider v-if="goodsLocked" content-position="left">可修改</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="商品分类" prop="category">
              <!-- 分类在「库存管理 → 基础设置 → 商品分类」里维护，这里只能选，不能手输 -->
              <el-tree-select
                v-model="form.category"
                :data="formCategoryOptions"
                placeholder="请选择商品分类"
                check-strictly
                filterable
                clearable
                style="width: 100%"
              >
                <!-- 输入框里显示「父级/子级/孙级」全路径，树里仍只显示本级名称 -->
                <template #default="{ data }">{{ data.shortLabel }}</template>
              </el-tree-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="activeFlag">
              <el-radio-group v-model="form.activeFlag">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item v-if="canSeeSale" label="参考价" prop="standardPrice">
              <decimal-input :min-decimals="2" :max-decimals="4" v-model="form.standardPrice" :min="0" style="width: 100%" />
              <div class="form-tip">仅供开单时参照，不参与销售、成本等任何计算</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最低库存" prop="minStockQty">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="form.minStockQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最高库存" prop="maxStockQty">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="form.maxStockQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 图片统一在这里维护：可传多张，第一张就是列表上显示的主图，顺序可拖动调整 -->
        <el-form-item label="商品图片" prop="mainImgs">
          <image-upload v-model="form.mainImgs" action="/inventory/goods/images" :limit="9" :file-size="10" />
          <div class="form-tip">最多 9 张，第一张作为商品主图，可拖动图片调整顺序</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 商品详情对话框 -->
    <el-dialog title="商品详情" v-model="detailOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="商品编码">{{ detailForm.goodsCode }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ detailForm.goodsName }}</el-descriptions-item>
        <el-descriptions-item label="商品分类">{{ categoryPath(detailForm.category) }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ detailForm.spec }}</el-descriptions-item>
        <el-descriptions-item label="计量单位">{{ detailForm.unit }}</el-descriptions-item>
        <el-descriptions-item label="商品类型">{{ getGoodsTypeLabel(detailForm.goodsType) }}</el-descriptions-item>
        <el-descriptions-item label="批次策略">{{ getBatchStrategyLabel(detailForm.batchStrategy) }}</el-descriptions-item>
        <el-descriptions-item label="成本计算">{{ getCostCalculationLabel(detailForm.costCalculation) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeSale" label="参考价">{{ formatCurrency(detailForm.standardPrice) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailForm.activeFlag === 1 ? 'success' : 'danger'">{{ detailForm.activeFlag === 1 ? '启用' : '停用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最低库存">{{ formatQty(detailForm.minStockQty) }}</el-descriptions-item>
        <el-descriptions-item label="最高库存">{{ formatQty(detailForm.maxStockQty) }}</el-descriptions-item>
        <el-descriptions-item label="条形码">{{ detailForm.barcode || '无' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailForm.createUserName || detailForm.createUser }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark || '无' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="InventoryGoods">
import { goodsImageSource } from '@/utils/goodsImage'
import { formatCurrency } from '@/utils/currency'
import { listGoods, getGoods, addGoods, updateGoods, delGoods } from '@/api/inventory/goods'
import { listCategoryTree } from '@/api/inventory/goodsCategory'
import DecimalInput from '@/components/DecimalInput'
import { formatQty } from '@/utils/decimal'
import { canSeeSale } from '@/utils/price'


const { proxy } = getCurrentInstance()

const goodsList = ref([])
const open = ref(false)
const detailOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const selectedRows = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
/** 商品分类树选项，取自「库存管理 → 基础设置 → 商品分类」维护的分类主数据 */
const categoryOptions = ref([])
/** 商品表单用的分类选项，停用的分类置灰不能再选到商品上 */
const formCategoryOptions = ref([])
/**
 * 分类名称 → 完整路径。商品表 category 存的是末级分类名称，列表和详情要显示层级
 * 就得自己按分类树拼。分类名称在同一商家内唯一（后端 assertNameUnique 是全表校验，
 * 不分父级），所以用名称做键不会串到别的分支上。
 */
const categoryPathMap = ref({})

/**
 * 批次策略与成本计算方法一一对应，选定批次策略成本计算方法就定死了。
 * 后端 InventoryCostPolicy 用同一张对应表，保存时会再推导一次。
 */
const COST_CALCULATION_BY_STRATEGY = {
  none: 'moving_avg',
  batch: 'fifo',
  serial: 'individual'
}

/** 历史数据里与批次策略对不上的旧成本计算方法，只用于提示，保存时会被覆盖 */
const legacyCostCalculation = ref('')

const data = reactive({
  form: {},
  detailForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    goodsCode: undefined,
    goodsName: undefined,
    category: undefined,
    goodsType: undefined,
    activeFlag: undefined
  },
  rules: {
    goodsCode: [
      { required: true, message: '商品编码不能为空', trigger: 'blur' }
    ],
    goodsName: [
      { required: true, message: '商品名称不能为空', trigger: 'blur' }
    ],
    unit: [
      { required: true, message: '计量单位不能为空', trigger: 'blur' }
    ],
    goodsType: [
      { required: true, message: '请选择商品类型', trigger: 'change' }
    ]
  }
})

const { form, detailForm, queryParams, rules } = toRefs(data)

/**
 * 商品被单据用过（有入库记录，或建过引用它的单据，不论是否审核）后，
 * 编码、名称、规格型号、计量单位、商品类型、批次策略、成本计算方法就锁死不能改，
 * 因为这些字段已经被单据明细和库存成本引用。后端 updateGoods 也会再校验一次。
 */
const goodsLocked = computed(() => form.value.usedFlag === 1)

/** 查询商品列表 */
function getList() {
  loading.value = true
  listGoods(queryParams.value).then(response => {
    goodsList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  selectedRows.value = selection
  ids.value = selection.map(item => item.goodsCode)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '添加商品'
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const goodsCode = row.goodsCode || ids.value[0]
  getGoods(goodsCode).then(response => {
    form.value = response.data
    // 老数据主图和多图分开存，编辑时合成一个列表，主图排在最前面
    form.value.mainImgs = mergeGoodsImages(form.value)
    // 成本计算方法一律按批次策略推导；历史数据对不上的先记下来提示用户
    const derived = costCalculationFor(form.value.batchStrategy)
    legacyCostCalculation.value = form.value.costCalculation && form.value.costCalculation !== derived
      ? form.value.costCalculation : ''
    form.value.costCalculation = derived
    open.value = true
    title.value = '修改商品'
  })
}

/** 详情按钮操作 */
function handleDetail(row) {
  getGoods(row.goodsCode).then(response => {
    detailForm.value = response.data
    detailOpen.value = true
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  // 后端会拒绝整批，这里先挑出来提示，免得用户不知道是哪几个商品卡住了
  const locked = (row.goodsCode ? [row] : selectedRows.value).filter(item => item.usedFlag === 1)
  if (locked.length) {
    proxy.$modal.msgWarning('商品 ' + locked.map(item => item.goodsCode).join('、')
      + ' 已产生单据或库存记录，不能删除；如需停止使用请把状态改为「停用」')
    return
  }
  const goodsCodes = row.goodsCode || ids.value
  proxy.$modal.confirm('是否确认删除商品编号为"' + goodsCodes + '"的数据项？').then(function () {
    return delGoods(goodsCodes)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/**
 * 编辑时把 imgUrl（主图）和 mainImgs（多图）合成一个列表，主图放第一位。
 * 表单里只维护这一个列表，提交时再拆回两个字段。
 */
function mergeGoodsImages(goods) {
  const main = (goods.imgUrl || '').trim()
  const list = (goods.mainImgs || '').split(',').map(item => item.trim()).filter(Boolean)
  const merged = main ? [main, ...list.filter(item => item !== main)] : list
  return merged.join(',') || undefined
}

/**
 * 提交前把图片列表拆回后端的两个字段：第一张写 imgUrl 当主图，整个列表写 mainImgs。
 * 主图换了的话，旧缩略图就对不上了，清掉让列表退回用 imgUrl 显示。
 */
function applyGoodsImages(goods) {
  const list = (goods.mainImgs || '').split(',').map(item => item.trim()).filter(Boolean)
  const main = list[0]
  if (main !== goods.imgUrl) {
    goods.thumbUrl = undefined
  }
  goods.imgUrl = main || undefined
  goods.mainImgs = list.join(',') || undefined
}

/** 表单重置 */
function reset() {
  form.value = {
    goodsCode: undefined,
    goodsName: undefined,
    category: undefined,
    spec: undefined,
    unit: '个',
    goodsType: 'raw',
    batchStrategy: 'none',
    requireExpireFlag: 0,
    costCalculation: 'moving_avg',
    standardPrice: 0,
    minStockQty: 0,
    maxStockQty: 0,
    imgUrl: undefined,
    thumbUrl: undefined,
    mainImgs: undefined,
    barcode: undefined,
    confirmItems: undefined,
    activeFlag: 1,
    usedFlag: 0,
    remark: undefined
  }
  legacyCostCalculation.value = ''
  proxy.resetForm('goodsRef')
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 确定按钮 */
function submitForm() {
  proxy.$refs['goodsRef'].validate(valid => {
    if (valid) {
      applyGoodsImages(form.value)
      // 兜底：提交的成本计算方法始终等于批次策略对应的算法
      form.value.costCalculation = costCalculationFor(form.value.batchStrategy)
      if (form.value.shopCode) {
        updateGoods(form.value).then(response => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
          getCategoryOptions()
        })
      } else {
        addGoods(form.value).then(response => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
          getCategoryOptions()
        })
      }
    }
  })
}

/** 批次策略一变，成本计算方法就跟着换成对应的算法 */
function handleBatchStrategyChange(strategy) {
  form.value.costCalculation = costCalculationFor(strategy)
  legacyCostCalculation.value = ''
}

function costCalculationFor(strategy) {
  return COST_CALCULATION_BY_STRATEGY[strategy] || 'moving_avg'
}

/** 获取商品类型标签 */
function getGoodsTypeTag(type) {
  const typeMap = {
    'raw': 'primary',
    'semifinished': 'warning',
    'finished': 'success'
  }
  return typeMap[type] || 'info'
}

/** 获取商品类型标签 */
function getGoodsTypeLabel(type) {
  const typeMap = {
    'raw': '原材料',
    'semifinished': '半成品',
    'finished': '成品'
  }
  return typeMap[type] || '未知'
}

/** 获取批次策略标签 */
function getBatchStrategyLabel(strategy) {
  const strategyMap = {
    'none': '无批次',
    'batch': '批次管理',
    'serial': '序列号管理'
  }
  return strategyMap[strategy] || '未知'
}

/** 获取成本计算标签 */
function getCostCalculationLabel(calculation) {
  const calculationMap = {
    'moving_avg': '移动加权平均',
    'fifo': '先进先出',
    'individual': '个别计价'
  }
  return calculationMap[calculation] || '未知'
}

/**
 * 分类树转成 el-tree-select 要的结构。
 * 商品表存的是分类名称，所以节点的 value 用名称而不是编码。
 * label 用「父级/子级/孙级」全路径，选中后输入框才能看出层级；
 * 树节点里通过 default 插槽显示 shortLabel，只有本级名称。
 * disableStopped 为真时把停用的分类置灰（商品表单用），
 * 查询条件不置灰，否则翻不出挂在停用分类下的历史商品。
 */
function toCategoryOptions(list, disableStopped, parentPath) {
  return (list || []).map(item => {
    const stopped = String(item.status) === '1'
    const suffix = stopped ? '（停用）' : ''
    const path = joinCategoryPath(parentPath, item.categoryName)
    return {
      value: item.categoryName,
      label: path + suffix,
      shortLabel: item.categoryName + suffix,
      disabled: disableStopped && stopped,
      children: toCategoryOptions(item.children, disableStopped, path)
    }
  })
}

/** 分类路径的分隔符，下拉输入框和列表用同一套写法 */
const CATEGORY_PATH_SEPARATOR = ' / '

function joinCategoryPath(parentPath, name) {
  return parentPath ? parentPath + CATEGORY_PATH_SEPARATOR + name : name
}

/** 把分类树压成「名称 → 完整路径」的字典，供列表、详情还原层级 */
function collectCategoryPaths(list, parentPath, target) {
  (list || []).forEach(item => {
    const path = joinCategoryPath(parentPath, item.categoryName)
    target[item.categoryName] = path
    collectCategoryPaths(item.children, path, target)
  })
  return target
}

/**
 * 商品行上的分类显示成完整路径。
 * 分类树里查不到（分类被删了，或历史数据里手输的名字）就原样显示，不能因为对不上
 * 就把值吞掉——那样列表看着像没填分类。
 */
function categoryPath(category) {
  const name = String(category || '').trim()
  if (!name) return ''
  return categoryPathMap.value[name] || name
}

/** 加载商品分类选项，分类主数据在「库存管理 → 基础设置 → 商品分类」维护 */
function getCategoryOptions() {
  listCategoryTree().then(response => {
    const tree = Array.isArray(response.data) ? response.data : []
    categoryOptions.value = toCategoryOptions(tree, false)
    formCategoryOptions.value = toCategoryOptions(tree, true)
    categoryPathMap.value = collectCategoryPaths(tree, '', {})
  })
}

// 页面被 keep-alive 缓存，从「商品分类」维护完切回来时要重新取一次，否则选不到刚加的分类
onActivated(getCategoryOptions)

getList()
getCategoryOptions()
</script>

<style scoped>
.lock-tip {
  margin-bottom: 12px;
}
.no-image {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
/* 操作列：删除按钮在禁用时会被 el-tooltip 的 span 包一层，
   导致按钮间距与普通行不一致，这里统一用 flex 布局对齐 */
:deep(.op-column .cell) {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
:deep(.op-column .cell .el-button + .el-button) {
  margin-left: 0;
}
.form-tip {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.2;
}
.form-tip-warning {
  color: var(--el-color-warning);
}
</style>

<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="成品编码"><el-input v-model="queryParams.productCode" clearable @keyup.enter="getList" /></el-form-item>
      <el-form-item label="成品名称"><el-input v-model="queryParams.productName" clearable @keyup.enter="getList" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable style="width: 130px">
          <el-option label="启用" value="启用" /><el-option label="停用" value="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="配方是建工单时的模板，一个成品一套"
      description="建生产工单选完产出商品会自动带出这里的配方，带出后在工单上可以改、可以删——工单存的是建单那一刻的快照，所以事后改配方不会影响已经建好的工单。停用的配方不再自动带出。" />

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" v-hasPermi="['production:bom:add']" @click="openAdd">新建配方</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list">
      <el-table-column label="成品编码" prop="productCode" width="150" />
      <el-table-column label="成品名称" prop="productName" min-width="180" show-overflow-tooltip />
      <el-table-column label="规格" prop="spec" width="120" />
      <el-table-column label="单位" prop="unit" width="70" align="center" />
      <el-table-column label="物料项数" prop="itemCount" width="100" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="scope"><el-tag :type="scope.row.status === '启用' ? 'success' : 'info'">{{ scope.row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="165">
        <template #default="scope">{{ parseTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" v-hasPermi="['production:bom:edit']" @click="openEdit(scope.row)">修改</el-button>
          <el-button link :type="scope.row.status === '启用' ? 'warning' : 'success'"
            icon="Switch" v-hasPermi="['production:bom:edit']" @click="toggleStatus(scope.row)">
            {{ scope.row.status === '启用' ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" icon="Delete" v-hasPermi="['production:bom:remove']" @click="removeBom(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="formOpen" :title="form.bomCode ? '修改配方' : '新建配方'" width="900px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="90px" v-loading="formLoading">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="成品">
              <!-- 成品是配方的身份，建好之后不能改：换成品等于换一套配方 -->
              <el-input v-if="form.bomCode" :model-value="`${form.productCode} ${form.productName}`" disabled />
              <el-button v-else type="primary" plain icon="Search" @click="pickProduct">
                {{ form.productCode ? `${form.productCode} ${form.productName}` : '选择成品' }}
              </el-button>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width:100%">
                <el-option label="启用" value="启用" /><el-option label="停用" value="停用" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">物料清单</el-divider>
        <el-button type="primary" link icon="Plus" @click="pickMaterial">添加物料</el-button>
        <div class="bom-hint">单位用量 = 每产出 1 个成品要用多少，和工单上的口径一致，带入工单时不做换算</div>
        <el-table :data="form.items" border size="small" empty-text="还没有物料，点「添加物料」选">
          <el-table-column label="物料编码" prop="goodsCode" width="140" />
          <el-table-column label="物料名称" prop="goodsName" min-width="150" show-overflow-tooltip />
          <el-table-column label="规格" width="120"><template #default="scope"><span class="bom-readonly">{{ scope.row.spec || '-' }}</span></template></el-table-column>
          <el-table-column label="单位" width="70"><template #default="scope"><span class="bom-readonly">{{ scope.row.unit || '-' }}</span></template></el-table-column>
          <el-table-column label="单位用量" width="180">
            <template #default="scope"><decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qtyPerUnit" :min="0.0001" size="small" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="行备注"><template #default="scope"><el-input v-model="scope.row.remark" size="small" /></template></el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="scope"><el-button link type="danger" icon="Delete" @click="form.items.splice(scope.$index, 1)" /></template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button type="primary" :disabled="formLoading" @click="submitForm">保存</el-button>
        <el-button @click="formOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="excludeCodes" @confirm="handleGoodsSelected" />
  </div>
</template>

<script setup name="ProductionBom">
import { ref, reactive, computed, getCurrentInstance } from 'vue'
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { listProductionBom, listBomProductCodes, getProductionBom, addProductionBom, updateProductionBom, delProductionBom } from '@/api/production/bom'
import DecimalInput from '@/components/DecimalInput'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const showSearch = ref(true)
const queryParams = reactive({ pageNum: 1, pageSize: 20, productCode: undefined, productName: undefined, status: undefined })

const formOpen = ref(false)
const formLoading = ref(false)
const emptyForm = () => ({ bomCode: undefined, productCode: '', productName: '', spec: '', unit: '', status: '启用', remark: '', items: [] })
const form = reactive(emptyForm())

const goodsSelectRef = ref(null)
/** 弹窗这次是选成品还是选物料 */
const goodsSelectTarget = ref('product')
/**
 * 已经有配方的成品编码，全量。
 *
 * <p>不能用列表页的 list——那只是当前页，翻页之后已有配方的成品照样能选中，
 * 要等保存时后端才报「已经有配方了」。每次打开选成品弹窗重新拉一次，
 * 别人刚建的也能挡住；拉不到就退化成不排除，保存时后端仍会拒。</p>
 */
const bomProductCodes = ref([])
/** 选物料时排除已录入的，选成品时排除已有配方的成品 */
const excludeCodes = computed(() => goodsSelectTarget.value === 'material'
  ? form.items.map(row => row.goodsCode).filter(Boolean)
  : bomProductCodes.value)

function getList() {
  loading.value = true
  listProductionBom(queryParams)
    .then(r => { list.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => { loading.value = false })
}

function resetQuery() {
  queryParams.productCode = undefined
  queryParams.productName = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}

function openAdd() {
  Object.assign(form, emptyForm())
  formOpen.value = true
}

async function openEdit(row) {
  Object.assign(form, emptyForm())
  formOpen.value = true
  formLoading.value = true
  try {
    const bom = (await getProductionBom(row.bomCode)).data
    if (!bom) { proxy.$modal.msgError('配方不存在'); formOpen.value = false; return }
    Object.assign(form, {
      bomCode: bom.bomCode, productCode: bom.productCode, productName: bom.productName,
      spec: bom.spec || '', unit: bom.unit || '', status: bom.status, remark: bom.remark || '',
      items: (bom.items || []).map(item => ({
        goodsCode: item.goodsCode, goodsName: item.goodsName, spec: item.spec,
        unit: item.unit, qtyPerUnit: Number(item.qtyPerUnit), remark: item.remark || ''
      }))
    })
  } catch (error) {
    formOpen.value = false
  } finally {
    formLoading.value = false
  }
}

async function pickProduct() {
  goodsSelectTarget.value = 'product'
  try {
    bomProductCodes.value = (await listBomProductCodes()).data || []
  } catch (error) {
    // 拉不到就不排除，保存时后端还会拒；拦截器已经提示过失败原因
    bomProductCodes.value = []
  }
  goodsSelectRef.value?.open()
}
function pickMaterial() { goodsSelectTarget.value = 'material'; goodsSelectRef.value?.open() }

function handleGoodsSelected(selected) {
  if (goodsSelectTarget.value === 'product') {
    // 成品只要一个，选多了取第一个
    const goods = selected[0]
    if (!goods) return
    form.productCode = goods.goodsCode
    form.productName = goods.goodsName || ''
    form.spec = goods.spec || ''
    form.unit = goods.unit || ''
    return
  }
  selected.forEach(goods => {
    form.items.push({
      goodsCode: goods.goodsCode, goodsName: goods.goodsName || '',
      spec: goods.spec || '', unit: goods.unit || '', qtyPerUnit: 1, remark: ''
    })
  })
}

function submitForm() {
  if (!String(form.productCode || '').trim()) { proxy.$modal.msgWarning('请选择成品'); return }
  if (!form.items.length) { proxy.$modal.msgWarning('配方至少要有一行物料'); return }
  if (form.items.some(row => !(Number(row.qtyPerUnit) > 0))) { proxy.$modal.msgWarning('单位用量必须大于0'); return }
  if (form.items.some(row => row.goodsCode === form.productCode)) {
    proxy.$modal.msgWarning('成品不能出现在自己的配方里')
    return
  }
  formLoading.value = true
  const api = form.bomCode ? updateProductionBom : addProductionBom
  api(form)
    .then(() => { proxy.$modal.msgSuccess('保存成功'); formOpen.value = false; getList() })
    .finally(() => { formLoading.value = false })
}

/** 停用只是不再自动带入新工单，已建工单不受影响，所以不用确认弹窗 */
function toggleStatus(row) {
  formLoading.value = true
  getProductionBom(row.bomCode).then(response => {
    const bom = response.data
    if (!bom) { proxy.$modal.msgError('配方不存在'); return null }
    return updateProductionBom({ ...bom, status: bom.status === '启用' ? '停用' : '启用' })
      .then(() => { proxy.$modal.msgSuccess(bom.status === '启用' ? '已停用' : '已启用'); getList() })
  }).finally(() => { formLoading.value = false })
}

function removeBom(row) {
  proxy.$modal.confirm(`确认删除「${row.productName}」的配方吗？已建工单不受影响。`)
    .then(() => delProductionBom(row.bomCode))
    .then(() => { proxy.$modal.msgSuccess('删除成功'); getList() })
    .catch(() => {})
}

getList()
</script>

<style scoped>
.bom-hint {
  margin: 2px 0 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.bom-readonly {
  color: var(--el-text-color-secondary);
}
</style>

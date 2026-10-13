<!--
  销售套餐维护。

  套餐不是商品，只是一组商品的组合标签：下单时在「新建销售订单」里点「选择套餐」，
  把套餐里的商品按各自数量一次铺进商品明细，之后每一行都是独立的普通商品行。

  订单不记来自哪个套餐——预占、发货、退货全按商品行走。所以：
  - 改套餐、停用套餐、删套餐，都不影响已经下过的订单
  - 套餐里不设价格，单价按下单当时的商品标准价带出，需要谈价在订单上改
-->
<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="套餐是商品的组合标签，不是一种新商品"
      description="下单时点「选择套餐」即可把套餐内的商品一次性铺进明细，之后仍可改数量、改单价、删行。套餐不含价格，单价按下单当时的商品标准价带出。改套餐不影响已经下过的订单。" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <el-form-item label="套餐代码">
        <el-input v-model="query.packageCode" clearable style="width: 160px" @keyup.enter="loadList" />
      </el-form-item>
      <el-form-item label="套餐名称">
        <el-input v-model="query.packageName" clearable style="width: 180px" @keyup.enter="loadList" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.isActive" clearable placeholder="全部" style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadList">搜索</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['order:package:add']" @click="openCreate">新建套餐</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadList" />
    </el-row>

    <el-table v-loading="loading" :data="rows" @expand-change="loadItems">
      <!-- 展开才查明细：列表页只需要知道有几种商品 -->
      <el-table-column type="expand">
        <template #default="scope">
          <el-table :data="scope.row.items || []" size="small" border class="inner-table"
            v-loading="scope.row.itemsLoading" empty-text="这个套餐还没有商品">
            <el-table-column prop="goodsCode" label="商品编码" width="150" />
            <el-table-column prop="goodsName" label="商品名称" min-width="180" />
            <el-table-column prop="spec" label="规格" width="130" />
            <el-table-column prop="unit" label="单位" width="80" align="center" />
            <el-table-column prop="qty" label="数量" width="110" align="right">
              <template #default="line">{{ Number(line.row.qty) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="packageCode" label="套餐代码" min-width="150" />
      <el-table-column prop="packageName" label="套餐名称" min-width="180" :show-overflow-tooltip="true" />
      <el-table-column prop="goodsKinds" label="商品种类" width="100" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.isActive === 1 ? 'success' : 'info'">
            {{ scope.row.isActive === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column prop="updateTime" label="更新时间" width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['order:package:edit']" @click="openEdit(scope.row)">修改</el-button>
          <el-button link type="warning" v-hasPermi="['order:package:edit']" @click="toggleActive(scope.row)">
            {{ scope.row.isActive === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" v-hasPermi="['order:package:remove']" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 新建 / 修改套餐 ===================== -->
    <el-dialog v-model="editOpen" :title="editing ? '修改套餐' : '新建套餐'" width="960px"
      :close-on-click-modal="false" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="套餐代码" required>
              <!-- 代码是业务主键，建后不给改：改了等于换了一个套餐 -->
              <el-input v-model="form.packageCode" :disabled="editing" placeholder="如 TC-2026-A" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="套餐名称" required>
              <el-input v-model="form.packageName" placeholder="如 新春礼盒三件套" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态">
              <el-switch v-model="form.isActive" :active-value="1" :inactive-value="0"
                active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" maxlength="200" />
        </el-form-item>

        <div class="section-title">套餐商品</div>
        <div class="mb8">
          <el-button type="primary" icon="Plus" @click="openGoodsSelect">添加商品</el-button>
          <span class="hint ml8">同一商品只能一行，数量合并填写。</span>
        </div>
        <el-table :data="form.items" border stripe empty-text="点击上方“添加商品”开始录入">
          <el-table-column prop="goodsCode" label="商品编码" width="150" />
          <el-table-column prop="goodsName" label="商品" min-width="180" />
          <el-table-column prop="spec" label="规格" width="120" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column label="数量" width="160">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qty" :min="0.0001" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="选填" maxlength="200" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="scope">
              <el-button link type="danger" @click="form.items.splice(scope.$index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="pickedGoodsCodes" @confirm="handleGoodsSelected" />
  </div>
</template>

<script setup name="SalesPackage">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPackage, getPackage, addPackage, updatePackage, delPackage } from '@/api/order/salespackage'
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import DecimalInput from '@/components/DecimalInput'

const loading = ref(false), saving = ref(false), showSearch = ref(true)
const editOpen = ref(false), editing = ref(false)
const rows = ref([]), total = ref(0)
const goodsSelectRef = ref(null)
const query = reactive({ packageCode: undefined, packageName: undefined, isActive: undefined, pageNum: 1, pageSize: 20 })
const form = ref({ packageCode: '', packageName: '', isActive: 1, remark: '', items: [] })

const pickedGoodsCodes = computed(() => form.value.items.map(i => i.goodsCode))

function loadList() {
  loading.value = true
  listPackage(query)
    .then(r => { rows.value = (r.rows || []).map(row => ({ ...row, items: [], itemsLoading: false })); total.value = r.total || 0 })
    .finally(() => loading.value = false)
}
function reset() {
  query.packageCode = undefined; query.packageName = undefined; query.isActive = undefined
  query.pageNum = 1
  loadList()
}

/** 展开行时才拉明细，收起不重复请求 */
function loadItems(row, expanded) {
  const isOpen = Array.isArray(expanded) ? expanded.includes(row) : expanded
  if (!isOpen || (row.items && row.items.length)) return
  row.itemsLoading = true
  getPackage(row.packageCode)
    .then(r => row.items = r.data?.items || [])
    .finally(() => row.itemsLoading = false)
}

function openCreate() {
  editing.value = false
  form.value = { packageCode: '', packageName: '', isActive: 1, remark: '', items: [] }
  editOpen.value = true
}

function openEdit(row) {
  editing.value = true
  getPackage(row.packageCode).then(r => {
    const data = r.data || {}
    form.value = {
      packageCode: data.packageCode, packageName: data.packageName,
      isActive: data.isActive ?? 1, remark: data.remark || '',
      items: (data.items || []).map(i => ({ ...i, qty: Number(i.qty) })),
    }
    editOpen.value = true
  })
}

function openGoodsSelect() {
  goodsSelectRef.value?.open()
}
function handleGoodsSelected(goodsList) {
  let added = 0
  for (const goods of goodsList || []) {
    if (form.value.items.some(i => i.goodsCode === goods.goodsCode)) continue
    form.value.items.push({
      goodsCode: goods.goodsCode, goodsName: goods.goodsName,
      spec: goods.spec, unit: goods.unit, qty: 1, remark: '',
    })
    added++
  }
  if (added) ElMessage.success(`已添加 ${added} 个商品`)
}

function save() {
  if (!form.value.packageCode.trim()) { ElMessage.warning('请填写套餐代码'); return }
  if (!form.value.packageName.trim()) { ElMessage.warning('请填写套餐名称'); return }
  if (!form.value.items.length) { ElMessage.warning('套餐至少要包含一个商品'); return }
  if (form.value.items.some(i => !Number(i.qty))) { ElMessage.warning('商品数量必须大于 0'); return }

  saving.value = true
  const action = editing.value
    ? updatePackage(form.value).then(() => ElMessage.success('套餐已修改'))
    : addPackage(form.value).then(r => ElMessage.success(r.msg || '套餐已创建'))
  action.then(() => { editOpen.value = false; loadList() }).finally(() => saving.value = false)
}

/** 停用只是让它在下单时选不到，已下订单不受影响，所以不必二次确认 */
function toggleActive(row) {
  getPackage(row.packageCode).then(r => {
    const data = r.data || {}
    return updatePackage({ ...data, isActive: row.isActive === 1 ? 0 : 1 })
  }).then(() => {
    ElMessage.success(row.isActive === 1 ? '套餐已停用' : '套餐已启用')
    loadList()
  })
}

function remove(row) {
  ElMessageBox.confirm(
    `确认删除套餐「${row.packageName}」？已经下过的订单不受影响。若只是想让它下单时选不到，用「停用」更稳妥。`,
    '提示', { closeOnClickModal: false })
    .then(() => delPackage(row.packageCode))
    .then(() => { ElMessage.success('套餐已删除'); loadList() })
    .catch(() => {})
}

loadList()
</script>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ml8 { margin-left: 8px; }
.inner-table {
  margin: 8px 16px 12px 48px;
  width: auto;
}
.section-title {
  margin: 4px 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
</style>

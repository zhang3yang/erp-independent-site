<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="分类名称" prop="categoryName">
        <el-input v-model="queryParams.categoryName" placeholder="请输入分类名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="收支方向" prop="type">
        <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 120px">
          <el-option label="收入" value="收入" />
          <el-option label="支出" value="支出" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型" prop="bizType">
        <el-select v-model="queryParams.bizType" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="item in bizTypes" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['finance:category:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['finance:category:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="categoryList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" :selectable="isCategorySelectable" />
      <el-table-column label="分类代码" align="center" prop="categoryCode" min-width="120" />
      <el-table-column label="分类名称" align="center" prop="categoryName" min-width="140" />
      <el-table-column label="收支方向" align="center" prop="type" min-width="100">
        <template #default="scope">
          <el-tag :type="scope.row.type === '收入' ? 'success' : 'danger'">{{ scope.row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="业务类型" align="center" prop="bizType" min-width="100" />
      <el-table-column label="状态" align="center" prop="isActive" min-width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isActive === 1 ? 'success' : 'info'">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['finance:category:edit']">修改</el-button>
          <el-button v-if="!isReservedOtherCode(scope.row.categoryCode)" link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['finance:category:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="560px" append-to-body :close-on-click-modal="false">
      <el-form ref="categoryRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="分类代码" prop="categoryCode">
          <el-input v-model="form.categoryCode" placeholder="如 FY_CL" maxlength="30" :disabled="form.id != null" />
          <div class="form-tip" v-if="form.id != null">分类代码是流水的引用键，不能修改</div>
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="如 差旅费" maxlength="50" :disabled="isReservedOtherCategory" />
        </el-form-item>
        <el-form-item label="收支方向" prop="type">
          <el-radio-group v-model="form.type" :disabled="form.id != null">
            <el-radio value="收入">收入</el-radio>
            <el-radio value="支出">支出</el-radio>
          </el-radio-group>
          <div class="form-tip" v-if="form.id != null">已完成的流水按方向决定了余额加减，方向不能改</div>
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="form.bizType" placeholder="请选择" clearable style="width: 100%" :disabled="isReservedOtherCategory">
            <el-option v-for="item in bizTypes" :key="item" :label="item" :value="item" />
          </el-select>
          <div class="form-tip">业务类型和资金流水一致，只有采购、销售、其他；其他固定为 QT_ZC（其他支出）和 QT_SR（其他收入）两条系统分类，只能改备注。</div>
        </el-form-item>
        <el-form-item label="状态" prop="isActive">
          <el-radio-group v-model="form.isActive" :disabled="isReservedOtherCategory">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FinanceCategory">
import { listCategory, addCategory, updateCategory, delCategory } from '@/api/finance/category'

const { proxy } = getCurrentInstance()

/** 业务类型只有这三种，查询和新建用同一份：与资金流水、与后端 BIZ_TYPES 同一口径 */
const bizTypes = ['采购', '销售', '其他']

const categoryList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    categoryName: null,
    type: null,
    bizType: null
  },
  rules: {
    categoryCode: [{ required: true, message: '分类代码不能为空', trigger: 'blur' }],
    categoryName: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }],
    type: [{ required: true, message: '收支方向不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)
/** 业务类型「其他」的两条系统分类：QT_ZC 记支出、QT_SR 记收入，与后端 FinanceCategory 同一口径 */
const RESERVED_OTHER = { QT_ZC: { name: '其他支出', type: '支出' }, QT_SR: { name: '其他收入', type: '收入' } }
function isReservedOtherCode(categoryCode) {
  return Object.prototype.hasOwnProperty.call(RESERVED_OTHER, categoryCode)
}
const isReservedOtherCategory = computed(() => isReservedOtherCode(form.value.categoryCode))

function getList() {
  loading.value = true
  listCategory(queryParams.value).then(response => {
    categoryList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    id: null,
    categoryCode: null,
    categoryName: null,
    type: '支出',
    bizType: null,
    isActive: 1,
    remark: null
  }
  proxy.resetForm('categoryRef')
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  multiple.value = !selection.length
}

function isCategorySelectable(row) {
  return !isReservedOtherCode(row.categoryCode)
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增收支分类'
}

function handleUpdate(row) {
  reset()
  form.value = Object.assign({}, row)
  open.value = true
  title.value = '修改收支分类'
}

function submitForm() {
  proxy.$refs['categoryRef'].validate(valid => {
    if (!valid) return
    if (form.value.bizType === '其他' || isReservedOtherCode(form.value.categoryCode)) {
      const reserved = isReservedOtherCode(form.value.categoryCode) ? RESERVED_OTHER[form.value.categoryCode] : null
      if (!reserved) {
        proxy.$modal.msgError('业务类型「其他」只能使用系统分类 QT_ZC（其他支出）和 QT_SR（其他收入）')
        return
      }
      if (form.value.bizType !== '其他' || form.value.categoryName !== reserved.name
        || form.value.type !== reserved.type || form.value.isActive !== 1) {
        proxy.$modal.msgError(`系统分类 ${form.value.categoryCode} 必须保持为启用的「${reserved.name}」${reserved.type}分类，只能修改备注`)
        return
      }
    }
    const action = form.value.id != null ? updateCategory : addCategory
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.id != null ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  if (isReservedOtherCode(row.categoryCode)) {
    proxy.$modal.msgError(`系统分类「${row.categoryName}」不能删除`)
    return
  }
  const categoryIds = row.id || ids.value
  proxy.$modal.confirm('是否确认删除选中的收支分类？已被流水引用的分类不能删除。').then(function () {
    return delCategory(categoryIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
</style>

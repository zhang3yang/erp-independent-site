<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="账户简称" prop="accountName">
        <el-input v-model="queryParams.accountName" placeholder="请输入账户简称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="开户行" prop="bankName">
        <el-input v-model="queryParams.bankName" placeholder="请输入开户行" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="isActive">
        <el-select v-model="queryParams.isActive" placeholder="全部" clearable style="width: 120px">
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['finance:account:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['finance:account:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-alert type="info" :closable="false" show-icon class="mb8"
      title="账户余额由资金流水驱动，这里不提供手工修改；新开账户的期初余额请录一笔「其他收入」流水，保证余额的每一分钱都能在流水里找到出处。" />

    <el-table v-loading="loading" :data="accountList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="账户代码" align="center" prop="accountCode" min-width="120" />
      <el-table-column label="账户简称" align="center" prop="accountName" min-width="120" />
      <el-table-column label="账户全称" align="center" prop="accountFullName" min-width="180" show-overflow-tooltip />
      <el-table-column label="银行账号" align="center" prop="accountNo" min-width="180" />
      <el-table-column label="开户行" align="center" prop="bankName" min-width="180" show-overflow-tooltip />
      <el-table-column label="币种" align="center" prop="currency" width="100">
        <template #default="scope">{{ currencyLabel(scope.row.currency) }}</template>
      </el-table-column>
      <el-table-column label="当前余额" align="right" prop="currentBalance" min-width="130">
        <template #default="scope">
          <span :class="Number(scope.row.currentBalance) < 0 ? 'amount-negative' : 'amount-positive'">
            {{ formatCurrency(scope.row.currentBalance, scope.row.currency) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="isActive" min-width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isActive === 1 ? 'success' : 'info'">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['finance:account:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['finance:account:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="620px" append-to-body :close-on-click-modal="false">
      <el-form ref="accountRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账户代码" prop="accountCode">
          <el-input v-model="form.accountCode" placeholder="如 ZH001" maxlength="50" :disabled="form.id != null" />
          <div class="form-tip" v-if="form.id != null">账户代码是流水的引用键，不能修改</div>
        </el-form-item>
        <el-form-item label="账户简称" prop="accountName">
          <el-input v-model="form.accountName" placeholder="如 建行基本户" maxlength="50" />
        </el-form-item>
        <el-form-item label="账户全称" prop="accountFullName">
          <el-input v-model="form.accountFullName" placeholder="营业执照上的账户全称" maxlength="100" />
        </el-form-item>
        <el-form-item label="银行账号" prop="accountNo">
          <el-input v-model="form.accountNo" placeholder="请输入银行账号" maxlength="50" />
        </el-form-item>
        <el-form-item label="开户行" prop="bankName">
          <el-input v-model="form.bankName" placeholder="请输入开户行名称" maxlength="100" />
        </el-form-item>
        <!-- 一个账户只对应一种币种：余额是按币种累加的，改币种等于把两种钱加到一起。
             余额不为零就直接锁死；余额为零但有过流水的情况前端看不出来，由后端兜底拒绝。 -->
        <el-form-item label="币种" prop="currency">
          <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%"
            :disabled="currencyLocked">
            <el-option v-for="item in currencyOptions" :key="item.code"
              :label="currencyOptionLabel(item)" :value="item.code" />
          </el-select>
          <div class="form-tip" v-if="currencyLocked">账户已有余额，币种不能修改；如需换币种请停用后新建账户</div>
          <div class="form-tip" v-else-if="form.id != null">账户一旦产生流水，币种就不能再改了</div>
        </el-form-item>
        <el-form-item label="状态" prop="isActive">
          <el-radio-group v-model="form.isActive">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" maxlength="500" show-word-limit />
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

<script setup name="FinanceAccount">
import { listAccount, addAccount, updateAccount, delAccount } from '@/api/finance/account'
import { currencyOptions, currencyLabel, currencyOptionLabel, formatCurrency } from '@/utils/currency'

const { proxy } = getCurrentInstance()

const accountList = ref([])
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
    accountName: null,
    bankName: null,
    isActive: null
  },
  rules: {
    accountCode: [{ required: true, message: '账户代码不能为空', trigger: 'blur' }],
    accountName: [{ required: true, message: '账户简称不能为空', trigger: 'blur' }],
    accountFullName: [{ required: true, message: '账户全称不能为空', trigger: 'blur' }],
    accountNo: [{ required: true, message: '银行账号不能为空', trigger: 'blur' }],
    bankName: [{ required: true, message: '开户行不能为空', trigger: 'blur' }],
    currency: [{ required: true, message: '币种不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 余额不为零就锁死币种。余额为零但已经有过流水的情况这里看不出来，由后端 updateAccount 拒绝。 */
const currencyLocked = computed(() => form.value.id != null && Number(form.value.currentBalance) !== 0)

function getList() {
  loading.value = true
  listAccount(queryParams.value).then(response => {
    accountList.value = response.rows
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
    accountCode: null,
    accountName: null,
    accountFullName: null,
    accountNo: null,
    bankName: null,
    currency: 'CNY',
    isActive: 1,
    remark: null
  }
  proxy.resetForm('accountRef')
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

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增账户'
}

function handleUpdate(row) {
  reset()
  form.value = Object.assign({}, row)
  open.value = true
  title.value = '修改账户'
}

function submitForm() {
  proxy.$refs['accountRef'].validate(valid => {
    if (!valid) return
    const action = form.value.id != null ? updateAccount : addAccount
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.id != null ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const accountIds = row.id || ids.value
  proxy.$modal.confirm('是否确认删除选中的账户？余额不为 0 或已产生流水的账户不能删除。').then(function () {
    return delAccount(accountIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.amount-positive {
  color: #67c23a;
  font-weight: 600;
}
.amount-negative {
  color: #f56c6c;
  font-weight: 600;
}
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
</style>

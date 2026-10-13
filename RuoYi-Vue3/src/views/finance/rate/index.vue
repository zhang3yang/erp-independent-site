<!--
  汇率设置：1 单位外币 = 多少人民币，按生效日期记。

  汇率变了不要改旧的那条，而是新加一条更晚生效的：销售利润按每笔发货当天有效的汇率折算，
  旧的留着，之前发出去的单利润才不会跟着变。录错了可以直接改或删。
-->
<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb8"
      title="销售利润报表按每笔发货当天生效的汇率把外币收入折成人民币。汇率变了请「新增」一条更晚生效的，别改旧的。" />

    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="币种" prop="currency">
        <el-select v-model="queryParams.currency" placeholder="全部" clearable style="width: 160px" @change="handleQuery">
          <el-option v-for="c in foreignCurrencies" :key="c.code" :label="c.code + ' ' + c.name" :value="c.code" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['finance:rate:edit']" type="primary" plain icon="Plus" @click="handleAdd">新增汇率</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list">
      <el-table-column label="币种" align="center" width="140">
        <template #default="{ row }">{{ row.currency }} {{ nameOf(row.currency) }}</template>
      </el-table-column>
      <el-table-column label="汇率" align="right" width="200">
        <template #default="{ row }">1 {{ row.currency }} = <b>{{ Number(row.rate) }}</b> 元</template>
      </el-table-column>
      <el-table-column label="生效日期" align="center" width="130">
        <template #default="{ row }">
          {{ row.effectiveDate }}
          <el-tag v-if="currentIds.has(row.id)" type="success" size="small" style="margin-left: 4px">现行</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
      <el-table-column label="录入人" align="center" width="110">
        <template #default="{ row }">{{ row.createUserName || row.createUser }}</template>
      </el-table-column>
      <el-table-column label="修改时间" align="center" width="160">
        <template #default="{ row }">{{ parseTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="140">
        <template #default="{ row }">
          <el-button v-hasPermi="['finance:rate:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
          <el-button v-hasPermi="['finance:rate:edit']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="open" :title="form.id ? '修改汇率' : '新增汇率'" width="480px" append-to-body>
      <el-form ref="rateRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="币种" prop="currency">
          <el-select v-model="form.currency" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in foreignCurrencies" :key="c.code" :label="c.code + ' ' + c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="汇率" prop="rate">
          <el-input-number v-model="form.rate" :min="0" :precision="6" :controls="false" style="width: 200px" />
          <span class="hint">1 {{ form.currency || '外币' }} = ? 人民币</span>
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="200" placeholder="例如：中行 9 月 1 日结汇价" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FinanceRate">
import { listRate, addRate, updateRate, delRate } from '@/api/finance/rate'
import { currencyOptions } from '@/utils/currency'

const { proxy } = getCurrentInstance()

const foreignCurrencies = currencyOptions.filter(c => c.code !== 'CNY')
const list = ref([])
const loading = ref(true)
const total = ref(0)
const open = ref(false)
const form = ref({})
const queryParams = ref({ pageNum: 1, pageSize: 50, currency: undefined })
const rules = {
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
  rate: [{ required: true, message: '请填写汇率', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}

// 每个币种里生效日期不晚于今天的最后一条就是现行汇率（列表已按币种、日期倒序）
const currentIds = computed(() => {
  const today = proxy.parseTime(new Date(), '{y}-{m}-{d}')
  const seen = new Set()
  const ids = new Set()
  for (const row of list.value) {
    if (!seen.has(row.currency) && row.effectiveDate <= today) {
      seen.add(row.currency)
      ids.add(row.id)
    }
  }
  return ids
})

function nameOf(code) {
  return (currencyOptions.find(c => c.code === code) || {}).name || ''
}

function getList() {
  loading.value = true
  listRate(queryParams.value).then(res => {
    list.value = res.rows
    total.value = res.total
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function handleAdd() {
  form.value = { currency: queryParams.value.currency || 'USD', rate: undefined, effectiveDate: proxy.parseTime(new Date(), '{y}-{m}-{d}'), remark: '' }
  open.value = true
  nextTick(() => proxy.$refs.rateRef?.clearValidate())
}

function handleUpdate(row) {
  form.value = { ...row, rate: Number(row.rate) }
  open.value = true
}

function handleDelete(row) {
  proxy.$modal.confirm(`删除 ${row.effectiveDate} 起生效的 ${row.currency} 汇率？这段时间的外币单会改用更早的那条，没有更早的就算不出利润。`)
    .then(() => delRate(row.id))
    .then(() => { proxy.$modal.msgSuccess('已删除'); getList() })
    .catch(() => {})
}

function submitForm() {
  proxy.$refs.rateRef.validate(valid => {
    if (!valid) return
    const action = form.value.id ? updateRate : addRate
    action(form.value).then(() => {
      proxy.$modal.msgSuccess('已保存')
      open.value = false
      getList()
    })
  })
}

getList()
</script>

<style scoped>
.hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.mb8 {
  margin-bottom: 8px;
}
</style>

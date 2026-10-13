<template>
  <div class="app-container platform-employee-page">
    <el-alert
      title="当前为平台只读总览，员工数据来自各商家的真实成员关系。员工维护请进入对应商家后操作。"
      type="info"
      :closable="false"
      show-icon
      class="mb20"
    />

    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="所属商家" prop="shopCode">
        <el-select v-model="queryParams.shopCode" placeholder="全部商家" clearable filterable style="width: 220px">
          <el-option v-for="shop in shops" :key="shop.shopCode" :label="shop.shopName" :value="shop.shopCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="员工姓名" prop="nickName">
        <el-input v-model="queryParams.nickName" placeholder="请输入员工姓名" clearable style="width: 190px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="phonenumber">
        <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号" clearable style="width: 190px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="employeeList">
      <el-table-column label="所属商家" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <strong>{{ scope.row.shopName }}</strong>
        </template>
      </el-table-column>
      <el-table-column prop="nickName" label="员工姓名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
      <el-table-column prop="jobNo" label="工号" width="120" />
      <el-table-column prop="roleNames" label="商家角色" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.roleNames || '未分配' }}</template>
      </el-table-column>
      <el-table-column label="手机号" width="130">
        <template #default="scope">{{ maskPhoneNumber(scope.row.phonenumber) }}</template>
      </el-table-column>
      <el-table-column label="身份" align="center" width="100">
        <template #default="scope"><el-tag v-if="scope.row.administrator" type="warning">商家管理员</el-tag><span v-else>员工</span></template>
      </el-table-column>
      <el-table-column prop="joinTime" label="加入时间" align="center" width="180">
        <template #default="scope">{{ parseTime(scope.row.joinTime) }}</template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup name="PlatformEmployeeOverview">
import { listPlatformEmployees, listTenantAdmins } from '@/api/system/tenantAdmin'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const shops = ref([])
const employeeList = ref([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  shopCode: undefined,
  nickName: undefined,
  phonenumber: undefined
})

async function loadShops() {
  const response = await listTenantAdmins()
  shops.value = response.data || []
}

async function getList() {
  loading.value = true
  try {
    const response = await listPlatformEmployees(queryParams)
    employeeList.value = response.rows || []
    total.value = response.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.pageNum = 1
  getList()
}

function maskPhoneNumber(value) {
  const phone = String(value || '')
  if (!phone) return '-'
  if (/^1\d{10}$/.test(phone)) return `${phone.slice(0, 3)}****${phone.slice(-4)}`
  return '****'
}

Promise.all([loadShops(), getList()])
</script>

<style scoped>
.shop-code { margin-top: 4px; color: var(--el-text-color-secondary); font-family: Consolas, monospace; font-size: 12px; }
</style>

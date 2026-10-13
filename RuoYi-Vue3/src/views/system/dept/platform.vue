<template>
  <div class="app-container platform-dept-page">
    <el-alert
      title="当前为平台只读总览，部门数据来自各商家的真实组织架构。部门维护请进入对应商家后操作。"
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
      <el-form-item label="部门名称" prop="deptName">
        <el-input v-model="queryParams.deptName" placeholder="请输入部门名称" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="12" class="overview-row">
      <el-col :xs="24" :sm="8"><el-card shadow="never"><strong>{{ shops.length }}</strong><span>商家数量</span></el-card></el-col>
      <el-col :xs="24" :sm="8"><el-card shadow="never"><strong>{{ departmentCount }}</strong><span>部门数量</span></el-card></el-col>
      <el-col :xs="24" :sm="8"><el-card shadow="never"><strong>{{ employeeCount }}</strong><span>部门员工数</span></el-card></el-col>
    </el-row>

    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="departmentTree"
      row-key="rowKey"
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="displayName" label="商家 / 部门" min-width="300">
        <template #default="scope">
          <div class="name-cell">
            <el-tag v-if="scope.row.nodeType === 'shop'" size="small" effect="plain">商家</el-tag>
            <span>{{ scope.row.displayName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="employeeCount" label="直属员工" align="center" width="110">
        <template #default="scope">{{ scope.row.nodeType === 'dept' ? scope.row.employeeCount || 0 : scope.row.employeeCount }}</template>
      </el-table-column>
      <el-table-column prop="leader" label="负责人" width="130" show-overflow-tooltip />
      <el-table-column prop="phone" label="联系电话" width="140" />
      <el-table-column prop="status" label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.nodeType === 'shop'" :type="Number(scope.row.shopStatus) === 1 ? 'success' : 'info'">
            {{ Number(scope.row.shopStatus) === 1 ? '正常' : '停用' }}
          </el-tag>
          <dict-tag v-else :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" align="center" width="180">
        <template #default="scope"><span v-if="scope.row.createTime">{{ parseTime(scope.row.createTime) }}</span></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup name="PlatformDeptOverview">
import { listPlatformDepartments, listTenantAdmins } from '@/api/system/tenantAdmin'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')
const loading = ref(false)
const refreshTable = ref(true)
const shops = ref([])
const departments = ref([])
const queryParams = reactive({ shopCode: undefined, deptName: undefined, status: undefined })

const departmentCount = computed(() => departments.value.length)
const employeeCount = computed(() => departments.value.reduce((total, dept) => total + Number(dept.employeeCount || 0), 0))
const departmentTree = computed(() => buildShopTrees(departments.value))

async function loadShops() {
  const response = await listTenantAdmins()
  shops.value = response.data || []
}

async function getList() {
  loading.value = true
  try {
    const response = await listPlatformDepartments(queryParams)
    departments.value = response.data || []
  } finally {
    loading.value = false
  }
}

function buildShopTrees(rows) {
  const shopMap = new Map()
  rows.forEach(row => {
    if (!shopMap.has(row.shopCode)) {
      shopMap.set(row.shopCode, {
        rowKey: `shop:${row.shopCode}`,
        nodeType: 'shop',
        shopCode: row.shopCode,
        shopStatus: row.shopStatus,
        displayName: row.shopName,
        employeeCount: 0,
        children: []
      })
    }
    const shop = shopMap.get(row.shopCode)
    const dept = { ...row, rowKey: `dept:${row.shopCode}:${row.deptId}`, nodeType: 'dept', displayName: row.deptName, children: [] }
    shop.children.push(dept)
    shop.employeeCount += Number(row.employeeCount || 0)
  })
  shopMap.forEach(shop => {
    const nodes = new Map(shop.children.map(dept => [String(dept.deptId), dept]))
    const roots = []
    shop.children.forEach(dept => {
      const parent = nodes.get(String(dept.parentId))
      if (parent && parent !== dept) parent.children.push(dept)
      else roots.push(dept)
    })
    shop.children = roots
  })
  return [...shopMap.values()]
}

function handleQuery() {
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  getList()
}

Promise.all([loadShops(), getList()])
</script>

<style scoped lang="scss">
.overview-row { margin-bottom: 18px; }
.overview-row .el-card :deep(.el-card__body) { display: flex; align-items: baseline; gap: 10px; }
.overview-row strong { color: var(--el-color-primary); font-size: 26px; }
.overview-row span { color: var(--el-text-color-secondary); }
.name-cell { display: flex; align-items: center; gap: 8px; }
.name-cell small { color: var(--el-text-color-secondary); font-family: Consolas, monospace; }
</style>

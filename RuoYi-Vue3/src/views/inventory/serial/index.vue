<template>
  <div class="app-container">
    <el-alert title="序列号数据由业务单据自动维护，当前页面仅供查询。" type="info" :closable="false" class="mb8" />
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="序列号" prop="serialNo">
        <el-input v-model="queryParams.serialNo" placeholder="请输入序列号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 240px">
          <el-option label="在库" value="instock" />
          <el-option label="已出库" value="outstock" />
        </el-select>
      </el-form-item>
      <el-form-item label="入库单号" prop="inboundDoc">
        <el-input v-model="queryParams.inboundDoc" placeholder="请输入入库单号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="采购单号" prop="purchaseCode">
        <el-input v-model="queryParams.purchaseCode" placeholder="请输入采购单号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="serialList">
      <el-table-column label="序列号" align="center" prop="serialNo" width="180" />
      <el-table-column label="商品编码" align="center" prop="goodsCode" width="120" />
      <el-table-column label="商品名称" align="center" prop="goodsName" :show-overflow-tooltip="true" />
      <el-table-column label="仓库" align="center" prop="wareName" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'instock' ? 'success' : 'info'">
            {{ scope.row.status === 'instock' ? '在库' : scope.row.status === 'outstock' ? '已出库' : scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="入库单号" align="center" prop="inboundDoc" width="160" />
      <el-table-column label="出库单号" align="center" prop="outboundDoc" width="160" />
      <el-table-column label="采购单号" align="center" prop="purchaseCode" width="150" />
      <el-table-column label="入库时间" align="center" prop="inTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.inTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="出库时间" align="center" prop="outTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.outTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 序列号详情对话框 -->
    <el-dialog title="序列号详情" v-model="detailOpen" width="600px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="序列号">{{ detailForm.serialNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailForm.status === 'instock' ? 'success' : 'info'">
            {{ detailForm.status === 'instock' ? '在库' : '已出库' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品编码">{{ detailForm.goodsCode }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ detailForm.goodsName }}</el-descriptions-item>
        <el-descriptions-item label="仓库名称">{{ detailForm.wareName }}</el-descriptions-item>
        <el-descriptions-item label="入库单号">{{ detailForm.inboundDoc }}</el-descriptions-item>
        <el-descriptions-item label="出库单号">{{ detailForm.outboundDoc }}</el-descriptions-item>
        <el-descriptions-item label="采购单号">{{ detailForm.purchaseCode }}</el-descriptions-item>
        <el-descriptions-item label="入库时间">{{ parseTime(detailForm.inTime) }}</el-descriptions-item>
        <el-descriptions-item label="出库时间">{{ parseTime(detailForm.outTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailForm.createUserName || detailForm.createUser }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup name="SerialManagement">
import { listSerial, getSerial } from '@/api/inventory/serial'

const { proxy } = getCurrentInstance()

const serialList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detailForm = ref({})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    serialNo: undefined,
    goodsCode: undefined,
    wareCode: undefined,
    status: undefined,
    inboundDoc: undefined,
    purchaseCode: undefined
  }
})

const { queryParams } = toRefs(data)

/** 查询序列号列表 */
async function getList() {
  loading.value = true
  try {
    const response = await listSerial(queryParams.value)
    serialList.value = response.rows
    total.value = response.total
  } finally {
    loading.value = false
  }
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

/** 查看序列号详情 */
async function handleView(row) {
  const response = await getSerial(row.serialNo)
  detailForm.value = response.data || row
  detailOpen.value = true
}

getList()
</script>

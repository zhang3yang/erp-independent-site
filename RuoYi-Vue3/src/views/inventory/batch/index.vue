<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" class="mb8"
      title="批次数据由业务单据自动维护，数量、成本、状态都不能手工改。"
      description="只有「存放位置」可以改：它是纯管理信息，不参与任何数量或成本计算。新批次入库时默认取商品档案上登记的位置（库存管理 → 基础设置 → 商品位置），挪了货再按实际改这一条批次即可，不影响商品档案。" />
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="采购单号" prop="purchaseCode">
        <el-input v-model="queryParams.purchaseCode" placeholder="请输入采购单号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <!-- 货位是自由文本，只能按内容模糊查——「A库区」能把整个库区的批次捞出来 -->
      <el-form-item label="存放位置" prop="position">
        <el-input v-model="queryParams.position" placeholder="按位置内容模糊查询" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="batchList">
      <el-table-column label="商品编码" align="center" prop="goodsCode" width="120" />
      <el-table-column label="商品名称" align="center" prop="goodsName" :show-overflow-tooltip="true" />
      <el-table-column label="仓库" align="center" prop="wareName" width="100" />
      <!-- 这一批实际放在哪儿。与商品档案上的位置是两个粒度：那是「这个商品一般放哪儿」，
           这里是「这一批放在哪儿」——先进先出拣货要按批次找货 -->
      <el-table-column label="存放位置" align="center" prop="position" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          <span v-if="scope.row.position">{{ scope.row.position }}</span>
          <span v-else class="text-muted">未登记</span>
        </template>
      </el-table-column>
      <el-table-column label="初始数量" align="center" prop="initQty" width="100" />
      <el-table-column label="剩余数量" align="center" prop="remainQty" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.remainQty > 0 ? 'success' : 'info'">
            {{ scope.row.remainQty }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeCost" label="成本单价" align="center" prop="costPrice" width="100">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.costPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="采购单号" align="center" prop="purchaseCode" width="150" />
      <el-table-column label="供应商" align="center" prop="partnerName" :show-overflow-tooltip="true" />
      <el-table-column label="生产日期" align="center" prop="productDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.productDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="有效期" align="center" prop="expireDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'active' ? 'success' : 'danger'">
            {{ scope.row.status === 'active' ? '有效' : scope.row.status === 'closed' ? '已关闭' : scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Location" @click="handlePosition(scope.row)">改位置</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 批次详情对话框 -->
    <el-dialog title="批次详情" v-model="detailOpen" width="600px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="状态">
          <el-tag :type="detailForm.status === 'active' ? 'success' : 'danger'">
            {{ detailForm.status === 'active' ? '有效' : detailForm.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品编码">{{ detailForm.goodsCode }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ detailForm.goodsName }}</el-descriptions-item>
        <el-descriptions-item label="仓库名称">{{ detailForm.wareName }}</el-descriptions-item>
        <el-descriptions-item label="初始数量">{{ detailForm.initQty }}</el-descriptions-item>
        <el-descriptions-item label="剩余数量">
          <el-tag :type="detailForm.remainQty > 0 ? 'success' : 'info'">{{ detailForm.remainQty }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="成本单价">{{ formatCurrency(detailForm.costPrice) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="采购金额">{{ formatCurrency(detailForm.purchaseAmount) }}</el-descriptions-item>
        <el-descriptions-item label="采购单号">{{ detailForm.purchaseCode }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailForm.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ parseTime(detailForm.productDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ parseTime(detailForm.expireDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailForm.createUserName || detailForm.createUser }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="存放位置" :span="2">
          {{ detailForm.position || '未登记' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 改存放位置。批次上唯一允许手工改的字段，所以单开一个只有这一项的弹窗，
         而不是复用「批次详情」——那里字段多，容易连带改到过账算出来的数 -->
    <el-dialog title="修改存放位置" v-model="positionOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-form :model="positionForm" label-width="90px">
        <el-form-item label="批次号"><span>{{ positionForm.batchCode }}</span></el-form-item>
        <el-form-item label="商品">
          <span>{{ positionForm.goodsCode }} {{ positionForm.goodsName }}</span>
        </el-form-item>
        <el-form-item label="仓库"><span>{{ positionForm.wareName }}</span></el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="positionForm.position" type="textarea" :rows="2" maxlength="100" show-word-limit
            placeholder="例如 A库区 101货架 3层；留空表示清掉位置" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
        title="只改这一条批次的位置，不影响商品档案上登记的默认位置，也不影响数量和成本。" />
      <template #footer>
        <el-button type="primary" :loading="positionSaving" @click="submitPosition">确 定</el-button>
        <el-button @click="positionOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="BatchManagement">
import { listBatch, getBatch, updateBatchPosition } from '@/api/inventory/batch'
import { formatCurrency } from '@/utils/currency'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()

const batchList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detailForm = ref({})
const positionOpen = ref(false)
const positionSaving = ref(false)
const positionForm = ref({})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    batchCode: undefined,
    goodsCode: undefined,
    wareCode: undefined,
    purchaseCode: undefined,
    position: undefined
  }
})

const { queryParams } = toRefs(data)

/** 查询批次列表 */
async function getList() {
  loading.value = true
  try {
    const response = await listBatch(queryParams.value)
    batchList.value = response.rows
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

/** 查看批次详情 */
async function handleView(row) {
  const response = await getBatch(row.batchCode, row.goodsCode, row.wareCode,
    row.qualityFlag, row.onhandFlag)
  detailForm.value = response.data || row
  detailOpen.value = true
}

/** 打开改位置弹窗。批次的复合主键六个字段都要带上，缺一个后端就定位不到行 */
function handlePosition(row) {
  positionForm.value = {
    batchCode: row.batchCode,
    goodsCode: row.goodsCode,
    goodsName: row.goodsName,
    wareCode: row.wareCode,
    wareName: row.wareName,
    qualityFlag: row.qualityFlag,
    onhandFlag: row.onhandFlag,
    position: row.position || ''
  }
  positionOpen.value = true
}

function submitPosition() {
  const f = positionForm.value
  positionSaving.value = true
  updateBatchPosition(f.batchCode, f.goodsCode, f.wareCode, f.qualityFlag, f.onhandFlag, f.position || '')
    .then(() => {
      proxy.$modal.msgSuccess('存放位置已更新')
      positionOpen.value = false
      getList()
    })
    .finally(() => { positionSaving.value = false })
}

getList()
</script>

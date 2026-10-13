<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="先维护销售平台，再维护平台下的店铺"
      description="销售订单会从这里选择平台和店铺，并自动保存名称快照。已经用于历史订单的店铺请停用，不要删除。" />

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>平台列表</span>
              <el-button type="primary" plain icon="Plus" v-hasPermi="['order:platform:add']" @click="openPlatform()">新增平台</el-button>
            </div>
          </template>
          <el-form :inline="true" :model="platformQuery">
            <el-form-item label="平台名称"><el-input v-model="platformQuery.platformName" clearable @keyup.enter="loadPlatforms" /></el-form-item>
            <el-form-item><el-button type="primary" icon="Search" @click="loadPlatforms">搜索</el-button></el-form-item>
          </el-form>
          <el-table ref="platformTableRef" v-loading="platformLoading" :data="platforms" highlight-current-row
            @current-change="selectPlatform">
            <el-table-column prop="platformCode" label="平台代码" min-width="110" />
            <el-table-column prop="platformName" label="平台名称" min-width="120" />
            <el-table-column prop="storeCount" label="店铺数" width="75" align="center" />
            <el-table-column label="状态" width="70">
              <template #default="scope"><el-tag size="small" :type="scope.row.isActive === 1 ? 'success' : 'info'">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="scope">
                <el-button link type="primary" v-hasPermi="['order:platform:edit']" @click.stop="openPlatform(scope.row)">修改</el-button>
                <el-button link type="danger" v-hasPermi="['order:platform:remove']" @click.stop="removePlatform(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="platformTotal > 0" :total="platformTotal" v-model:page="platformQuery.pageNum"
            v-model:limit="platformQuery.pageSize" @pagination="loadPlatforms" />
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ selectedPlatform ? `“${selectedPlatform.platformName}”下的店铺` : '平台店铺' }}</span>
              <el-button type="primary" plain icon="Plus" :disabled="!selectedPlatform"
                v-hasPermi="['order:platform:add']" @click="openStore()">新增店铺</el-button>
            </div>
          </template>
          <el-empty v-if="!selectedPlatform" description="请先点击左侧平台查看店铺" />
          <template v-else>
            <el-form :inline="true" :model="storeQuery">
              <el-form-item label="店铺名称"><el-input v-model="storeQuery.storeName" clearable @keyup.enter="loadStores" /></el-form-item>
              <el-form-item><el-button type="primary" icon="Search" @click="loadStores">搜索</el-button></el-form-item>
            </el-form>
            <el-table v-loading="storeLoading" :data="stores">
              <el-table-column prop="storeCode" label="店铺代码" min-width="130" />
              <el-table-column prop="storeName" label="店铺名称" min-width="150" />
              <el-table-column label="状态" width="80">
                <template #default="scope"><el-tag size="small" :type="scope.row.isActive === 1 ? 'success' : 'info'">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" v-hasPermi="['order:platform:edit']" @click="openStore(scope.row)">修改</el-button>
                  <el-button link type="danger" v-hasPermi="['order:platform:remove']" @click="removeStore(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <pagination v-show="storeTotal > 0" :total="storeTotal" v-model:page="storeQuery.pageNum"
              v-model:limit="storeQuery.pageSize" @pagination="loadStores" />
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="platformOpen" :title="platformForm.id ? '修改平台' : '新增平台'" width="520px" append-to-body>
      <el-form ref="platformRef" :model="platformForm" :rules="platformRules" label-width="90px">
        <el-form-item label="平台代码" prop="platformCode"><el-input v-model="platformForm.platformCode" maxlength="30" :disabled="!!platformForm.id" placeholder="如 TAOBAO" /></el-form-item>
        <el-form-item label="平台名称" prop="platformName"><el-input v-model="platformForm.platformName" maxlength="50" placeholder="如 淘宝" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="platformForm.isActive"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="platformForm.remark" type="textarea" maxlength="200" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="platformOpen=false">取消</el-button><el-button type="primary" @click="savePlatform">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="storeOpen" :title="storeForm.id ? '修改店铺' : '新增店铺'" width="520px" append-to-body>
      <el-form ref="storeRef" :model="storeForm" :rules="storeRules" label-width="90px">
        <el-form-item label="所属平台"><el-input :model-value="selectedPlatform?.platformName" disabled /></el-form-item>
        <el-form-item label="店铺代码" prop="storeCode"><el-input v-model="storeForm.storeCode" maxlength="50" :disabled="!!storeForm.id" placeholder="平台内使用的店铺代码" /></el-form-item>
        <el-form-item label="店铺名称" prop="storeName"><el-input v-model="storeForm.storeName" maxlength="100" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="storeForm.isActive"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="storeForm.remark" type="textarea" maxlength="200" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="storeOpen=false">取消</el-button><el-button type="primary" @click="saveStore">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="OrderPlatform">
import { listPlatforms, addPlatform, updatePlatform, deletePlatform, listStores, addStore, updateStore, deleteStore } from '@/api/order/platform'

const { proxy } = getCurrentInstance()
const platformLoading = ref(false), storeLoading = ref(false)
const platforms = ref([]), stores = ref([]), selectedPlatform = ref(null)
const platformTableRef = ref()
const platformTotal = ref(0), storeTotal = ref(0)
const platformOpen = ref(false), storeOpen = ref(false)
const platformForm = ref({}), storeForm = ref({})
const platformQuery = reactive({ pageNum: 1, pageSize: 20, platformName: undefined })
const storeQuery = reactive({ pageNum: 1, pageSize: 20, platformCode: undefined, storeName: undefined })
const platformRules = { platformCode: [{ required: true, message: '请输入平台代码', trigger: 'blur' }], platformName: [{ required: true, message: '请输入平台名称', trigger: 'blur' }] }
const storeRules = { storeCode: [{ required: true, message: '请输入店铺代码', trigger: 'blur' }], storeName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }] }

function loadPlatforms() {
  platformLoading.value = true
  listPlatforms(platformQuery).then(r => {
    platforms.value = r.rows || []
    platformTotal.value = r.total || 0
    keepCurrentPlatform()
  }).finally(() => platformLoading.value = false)
}
// 列表刷新后行对象会被整体替换，el-table 的当前行高亮会丢失，这里按平台代码重新选中
function keepCurrentPlatform() {
  const code = selectedPlatform.value?.platformCode
  if (!code) return
  const row = platforms.value.find(p => p.platformCode === code)
  if (row) selectedPlatform.value = row
  nextTick(() => platformTableRef.value?.setCurrentRow(row))
}
function selectPlatform(row) {
  if (!row) return
  const changed = storeQuery.platformCode !== row.platformCode
  selectedPlatform.value = row
  if (!changed) return
  storeQuery.platformCode = row.platformCode
  storeQuery.pageNum = 1
  loadStores()
}
function loadStores() {
  if (!storeQuery.platformCode) return
  storeLoading.value = true
  listStores(storeQuery).then(r => { stores.value = r.rows || []; storeTotal.value = r.total || 0 }).finally(() => storeLoading.value = false)
}
function openPlatform(row) {
  platformForm.value = row ? { ...row } : { platformCode: '', platformName: '', isActive: 1, remark: '' }
  platformOpen.value = true
}
function savePlatform() {
  proxy.$refs.platformRef.validate(valid => {
    if (!valid) return
    const editing = !!platformForm.value.id
    ;(editing ? updatePlatform : addPlatform)(platformForm.value).then(() => {
      proxy.$modal.msgSuccess(editing ? '平台修改成功' : '平台新增成功')
      platformOpen.value = false; loadPlatforms()
    })
  })
}
function removePlatform(row) {
  proxy.$modal.confirm(`确认删除平台“${row.platformName}”吗？平台下有店铺时不能删除。`).then(() => deletePlatform(row.id)).then(() => {
    if (selectedPlatform.value?.id === row.id) { selectedPlatform.value = null; stores.value = []; storeQuery.platformCode = undefined }
    proxy.$modal.msgSuccess('删除成功'); loadPlatforms()
  }).catch(() => {})
}
function openStore(row) {
  if (!selectedPlatform.value) return
  storeForm.value = row ? { ...row } : { platformCode: selectedPlatform.value.platformCode, storeCode: '', storeName: '', isActive: 1, remark: '' }
  storeOpen.value = true
}
function saveStore() {
  proxy.$refs.storeRef.validate(valid => {
    if (!valid) return
    storeForm.value.platformCode = selectedPlatform.value.platformCode
    const editing = !!storeForm.value.id
    ;(editing ? updateStore : addStore)(storeForm.value).then(() => {
      proxy.$modal.msgSuccess(editing ? '店铺修改成功' : '店铺新增成功')
      storeOpen.value = false; loadStores(); loadPlatforms()
    })
  })
}
function removeStore(row) {
  proxy.$modal.confirm(`确认删除店铺“${row.storeName}”吗？被销售订单使用后不能删除。`).then(() => deleteStore(row.id)).then(() => {
    proxy.$modal.msgSuccess('删除成功'); loadStores(); loadPlatforms()
  }).catch(() => {})
}
loadPlatforms()
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }

/* 当前平台使用系统主题色高光；半透明叠在深色表格上，避免默认的白色选中行。 */
:deep(.el-table__body tr.current-row > td.el-table__cell) {
  background-color: var(--current-color-dark-bg, rgba(194, 107, 0, 0.2)) !important;
}
</style>

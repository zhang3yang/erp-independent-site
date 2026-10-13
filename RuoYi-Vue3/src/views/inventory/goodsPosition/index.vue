<!--
  商品位置：登记某个商品放在哪个仓库的什么地方。

  位置是一段库管员自己写的文本（几号库区、几排几架几层、临时堆放点……），
  系统不解析，只原样存和展示。

  一种商品整个商家只登记一条：同一个商品不能既在 A 仓又在 B 仓各记一条，
  换地方就改原来那条（仓库也能一起改）。重复登记会被后端拦下来。
-->
<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库" prop="wareCode">
        <el-select v-model="queryParams.wareCode" placeholder="请选择仓库" clearable filterable style="width: 240px">
          <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="存放位置" prop="position">
        <el-input v-model="queryParams.position" placeholder="按位置内容模糊查询" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['inventory:goodsposition:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['inventory:goodsposition:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['inventory:goodsposition:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="positionList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="商品代码" prop="goodsCode" align="center" width="140" show-overflow-tooltip />
      <el-table-column label="商品名称" prop="goodsName" align="center" min-width="160" show-overflow-tooltip />
      <el-table-column label="规格" prop="spec" align="center" width="120" show-overflow-tooltip />
      <el-table-column label="单位" prop="unit" align="center" width="80" />
      <el-table-column label="仓库代码" prop="wareCode" align="center" width="140" show-overflow-tooltip />
      <el-table-column label="仓库名称" prop="wareName" align="center" min-width="130" show-overflow-tooltip />
      <el-table-column label="存放位置" prop="position" align="left" min-width="220" show-overflow-tooltip />
      <el-table-column label="创建人" prop="createUserName" align="center" width="110" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.createUserName || scope.row.createUser }}</span>
        </template>
      </el-table-column>
      <el-table-column label="修改时间" align="center" width="160">
        <template #default="{ row }">{{ parseTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['inventory:goodsposition:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
          <el-button v-hasPermi="['inventory:goodsposition:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="640px" append-to-body :close-on-click-modal="false">
      <el-form ref="positionRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="仓库" prop="wareCode">
          <el-select v-model="form.wareCode" placeholder="请选择仓库" filterable style="width: 100%">
            <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品" prop="goodsCode">
          <div class="goods-picker">
            <el-input v-model="form.goodsCode" readonly placeholder="请选择商品" style="width: 100%">
              <template #append>
                <el-button icon="Search" @click="openGoodsSelect">选择商品</el-button>
              </template>
            </el-input>
            <div v-if="form.goodsName" class="goods-brief">
              {{ form.goodsName }}
              <span v-if="form.spec">／规格：{{ form.spec }}</span>
              <span v-if="form.unit">／单位：{{ form.unit }}</span>
            </div>
            <div class="goods-brief">一种商品只能登记一个位置；换地方请改原来那条记录，仓库可以一起改。</div>
          </div>
        </el-form-item>
        <el-form-item label="存放位置" prop="position">
          <el-input v-model="form.position" type="textarea" :rows="6" maxlength="2000" show-word-limit
            placeholder="请填写商品在仓库中的存放位置，例如：A区3排2架4层，靠近西侧通道" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :multiple="false" @confirm="handleGoodsSelected" />
  </div>
</template>

<script setup name="InventoryGoodsPosition">
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { listGoodsPosition, getGoodsPosition, addGoodsPosition, updateGoodsPosition, delGoodsPosition } from '@/api/inventory/goodsPosition'

const { proxy } = getCurrentInstance()

const positionList = ref([])
const warehouses = ref([])
const loading = ref(true)
const showSearch = ref(true)
const open = ref(false)
const title = ref('')
const total = ref(0)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const goodsSelectRef = ref(null)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    goodsCode: undefined,
    goodsName: undefined,
    wareCode: undefined,
    position: undefined
  },
  rules: {
    wareCode: [{ required: true, message: '请选择仓库', trigger: 'change' }],
    goodsCode: [{ required: true, message: '请选择商品', trigger: 'change' }],
    position: [{ required: true, message: '请填写存放位置', trigger: 'blur' }]
  }
})
const { form, queryParams, rules } = toRefs(data)

/** 查询商品位置列表 */
function getList() {
  loading.value = true
  listGoodsPosition(queryParams.value).then(res => {
    positionList.value = res.rows
    total.value = res.total
  }).finally(() => {
    loading.value = false
  })
}

function loadWarehouses() {
  listWarehouse({ pageSize: 1000, activeFlag: 1 }).then(res => {
    warehouses.value = res.rows || []
  })
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
  ids.value = selection.map(item => item.positionId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  title.value = '新增商品位置'
  open.value = true
}

function handleUpdate(row) {
  reset()
  const positionId = row?.positionId || ids.value[0]
  getGoodsPosition(positionId).then(res => {
    form.value = res.data
    title.value = '修改商品位置'
    open.value = true
  })
}

function handleDelete(row) {
  const positionIds = row?.positionId || ids.value
  const target = row?.goodsName ? '商品“' + row.goodsName + '”的存放位置' : '选中的' + ids.value.length + '条位置记录'
  proxy.$modal.confirm('是否确认删除' + target + '？').then(() => delGoodsPosition(positionIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function openGoodsSelect() {
  goodsSelectRef.value?.open()
}

/** 单选模式，只会回来一个商品 */
function handleGoodsSelected(goodsList) {
  const goods = (goodsList || [])[0]
  if (!goods) return
  form.value.goodsCode = goods.goodsCode
  form.value.goodsName = goods.goodsName
  form.value.spec = goods.spec
  form.value.unit = goods.unit
  proxy.$refs.positionRef?.validateField('goodsCode')
}

function reset() {
  form.value = {
    positionId: undefined,
    goodsCode: undefined,
    goodsName: undefined,
    spec: undefined,
    unit: undefined,
    wareCode: undefined,
    wareName: undefined,
    position: undefined
  }
  proxy.resetForm('positionRef')
}

function cancel() {
  open.value = false
  reset()
}

function submitForm() {
  proxy.$refs.positionRef.validate(valid => {
    if (!valid) return
    const action = form.value.positionId ? updateGoodsPosition : addGoodsPosition
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.positionId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

loadWarehouses()
getList()
</script>

<style lang="scss" scoped>
.goods-picker {
  width: 100%;
}

.goods-brief {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>

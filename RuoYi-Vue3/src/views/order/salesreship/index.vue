<template>
  <div class="app-container">
    <el-alert type="warning" show-icon :closable="false" class="mb8"
      title="补发是独立单据：每张补发单必须关联一张原销售订单"
      description="同一原订单只能补发一次；补发单创建后不能拆批，发货时会把单内全部商品一次性出库，不改变原订单的已发数量和履约状态。补发单不支持改单，建错了在列表里删掉重建即可，删除后原订单的补发名额会还回去。" />

    <el-form :model="query" ref="queryRef" :inline="true" class="mb8">
      <el-form-item label="补发单号"><el-input v-model="query.reshipCode" clearable placeholder="支持模糊查询" @keyup.enter="loadList" /></el-form-item>
      <el-form-item label="原订单号"><el-input v-model="query.originalOrderCode" clearable placeholder="支持模糊查询" @keyup.enter="loadList" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option label="待发货" value="待发货" /><el-option label="已发货" value="已发货" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadList">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['order:salesreship:add']" @click="openCreate">新建补发</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="rows" border empty-text="暂无销售补发单">
      <el-table-column prop="reshipCode" label="补发单号" min-width="180" />
      <el-table-column prop="originalOrderCode" label="原订单号" min-width="150" />
      <el-table-column label="平台/店铺" min-width="180" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.platName || scope.row.platCode || '-' }} / {{ scope.row.shopStoreName || scope.row.shopStoreCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="receivePerson" label="收件人" width="100" />
      <el-table-column prop="warehouseName" label="补发仓库" min-width="130" />
      <el-table-column prop="totalQty" label="补发数量" width="100" align="right" />
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="scope"><el-tag :type="scope.row.status === '已发货' ? 'success' : 'warning'">{{ scope.row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="logisticsNo" label="物流单号" min-width="150" />
      <el-table-column label="创建人" width="100" align="center"><template #default="scope">{{ scope.row.createUserName || scope.row.createUser }}</template></el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
      <el-table-column label="操作" width="190" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['order:salesreship:query']" @click="openDetail(scope.row)">详情</el-button>
          <el-button v-if="scope.row.status === '待发货'" link type="success"
            v-hasPermi="['order:salesreship:ship']" @click="openShip(scope.row)">整单发货</el-button>
          <!-- 补发单不能改，建错了只能删掉重建；不删干净原订单就再也补发不了 -->
          <el-button v-if="scope.row.status === '待发货'" link type="danger"
            v-hasPermi="['order:salesreship:remove']" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadList" />

    <el-dialog v-model="createOpen" title="新建销售补发" width="1050px" append-to-body :close-on-click-modal="false">
      <el-alert type="info" show-icon :closable="false" class="mb20"
        title="请选择这次需要补齐的全部商品和数量"
        description="保存后不能再为同一原订单创建第二张补发单，也不能把本单拆成多次发货。填错了不用担心：发货之前都可以在列表里删掉这张单重建。" />
      <el-form :model="form" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="订单月份" required>
            <el-date-picker v-model="orderMonth" type="month" value-format="YYYY-MM" :clearable="false" style="width:100%" @change="loadEligible" />
          </el-form-item></el-col>
          <el-col :span="16"><el-form-item label="原销售订单" required>
            <el-select v-model="form.originalOrderCode" filterable placeholder="只列已发过货且未补发的订单" style="width:100%" @change="onOrderChange">
              <el-option v-for="order in eligibleOrders" :key="order.orderCode" :label="orderLabel(order)" :value="order.orderCode" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="补发仓库" required>
            <el-select v-model="form.warehouseCode" filterable placeholder="请选择" style="width:100%">
              <el-option v-for="ware in warehouses" :key="ware.wareCode" :label="ware.wareName" :value="ware.wareCode" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物流承运商"><el-input v-model="form.carrier" placeholder="可发货时再填" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物流单号"><el-input v-model="form.logisticsNo" placeholder="可发货时再填" /></el-form-item></el-col>
        </el-row>

        <div class="section-title">收件信息（默认带出原订单，可按实际补发地址修改）</div>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="收件人" required><el-input v-model="form.receivePerson" /></el-form-item></el-col>
          <!-- 号码是从原订单原样带过来的，那边不限 11 位（虚拟号、固话、多号码），这边也不能限 -->
          <el-col :span="8"><el-form-item label="联系电话" required>
            <el-input v-model="form.receiveTelephone" :maxlength="CONTACT_PHONE_MAX_LENGTH"
              placeholder="虚拟号、固话都行，多个号码用逗号或分号分隔" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="邮编"><el-input v-model="form.receiveZipCode" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="收件地址" required><el-input v-model="form.receiveAddress" /></el-form-item>

        <div class="section-title">本次完整补发明细</div>
        <el-table :data="form.items" border empty-text="选择原订单后显示已发商品">
          <el-table-column prop="goodsCode" label="商品编码" width="130" />
          <el-table-column prop="goodsName" label="商品名称" min-width="160" />
          <el-table-column prop="spec" label="规格" width="120" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="shippedQty" label="原单已发" width="100" align="right" />
          <el-table-column label="本次补发数量" width="190">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.reshipQty" :min="0" :max="Number(scope.row.shippedQty || 0)" style="width:100%" />
            </template>
          </el-table-column>
        </el-table>
        <el-form-item label="补发说明" class="mt20"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="例如漏发、错发、运输破损" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="save">确认创建一次性补发单</el-button>
        <el-button @click="createOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipOpen" title="整单补发" width="520px" append-to-body :close-on-click-modal="false">
      <el-alert type="warning" show-icon :closable="false" class="mb20" title="本次操作会把补发单内全部商品一次性出库，完成后不能再次发货。" />
      <el-form :model="shipForm" label-width="90px">
        <el-form-item label="补发单号"><el-input v-model="shipForm.reshipCode" disabled /></el-form-item>
        <el-form-item label="物流承运商"><el-input v-model="shipForm.carrier" /></el-form-item>
        <el-form-item label="物流单号"><el-input v-model="shipForm.logisticsNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="success" :loading="saving" @click="doShip">确认整单发货</el-button>
        <el-button @click="shipOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="销售补发单详情" width="950px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="补发单号">{{ detail.reshipCode }}</el-descriptions-item>
        <el-descriptions-item label="原订单号">{{ detail.originalOrderCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="平台/店铺">{{ detail.platName || detail.platCode || '-' }} / {{ detail.shopStoreName || detail.shopStoreCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="补发仓库">{{ detail.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="出库单号">{{ detail.outboundDocCode || '未发货' }}</el-descriptions-item>
        <el-descriptions-item label="收件人">{{ detail.receivePerson }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.receiveTelephone }}</el-descriptions-item>
        <el-descriptions-item label="物流">{{ detail.carrier || '-' }} {{ detail.logisticsNo || '' }}</el-descriptions-item>
        <el-descriptions-item label="收件地址" :span="3">{{ detail.receiveAddress }}</el-descriptions-item>
        <el-descriptions-item label="补发说明" :span="3">{{ detail.remark || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.items || []" border class="mt20">
        <el-table-column prop="goodsCode" label="商品编码" width="130" />
        <el-table-column prop="goodsName" label="商品名称" min-width="170" />
        <el-table-column prop="spec" label="规格" width="130" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="qty" label="补发数量" width="110" align="right" />
      </el-table>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="SalesReship">
import { getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { addSalesReship, delSalesReship, getSalesReship, listEligibleOrders, listSalesReship, shipSalesReship } from '@/api/order/salesreship'
import { listWarehouse } from '@/api/inventory/warehouse'
import { parseTime } from '@/utils/ruoyi'
import { isContactPhone, CONTACT_PHONE_MAX_LENGTH } from '@/utils/validate'
import DecimalInput from '@/components/DecimalInput'

const { proxy } = getCurrentInstance()
const loading = ref(false), saving = ref(false), createOpen = ref(false), shipOpen = ref(false), detailOpen = ref(false)
const rows = ref([]), total = ref(0), warehouses = ref([]), eligibleOrders = ref([]), detail = ref({ items: [] })
const orderMonth = ref(parseTime(new Date(), '{y}-{m}'))
const query = reactive({ pageNum: 1, pageSize: 20, reshipCode: undefined, originalOrderCode: undefined, status: undefined })
const form = ref(emptyForm())
const shipForm = reactive({ reshipCode: '', carrier: '', logisticsNo: '' })

function emptyForm() {
  return { originalOrderCode: undefined, warehouseCode: '', receivePerson: '', receiveAddress: '', receiveTelephone: '', receiveZipCode: '', carrier: '', logisticsNo: '', remark: '', items: [] }
}

function loadList() {
  loading.value = true
  listSalesReship(query).then(r => { rows.value = r.rows || []; total.value = r.total || 0 }).finally(() => { loading.value = false })
}

function resetQuery() {
  query.reshipCode = undefined; query.originalOrderCode = undefined; query.status = undefined; query.pageNum = 1; loadList()
}

function orderLabel(order) {
  return `${order.orderCode} / ${order.shopStoreName || order.shopStoreCode || '-'} / ${order.receivePerson || '-'} / 已发${order.sendPercent || 0}%`
}

function loadEligible() {
  form.value.originalOrderCode = undefined
  form.value.items = []
  return listEligibleOrders(orderMonth.value.replace('-', '')).then(r => { eligibleOrders.value = r.data || [] })
}

function openCreate() {
  orderMonth.value = parseTime(new Date(), '{y}-{m}')
  form.value = emptyForm()
  if (warehouses.value.length === 1) form.value.warehouseCode = warehouses.value[0].wareCode
  loadEligible().then(() => { createOpen.value = true })
}

function onOrderChange(orderCode) {
  const order = eligibleOrders.value.find(item => item.orderCode === orderCode)
  form.value.receivePerson = order?.receivePerson || ''
  form.value.receiveAddress = order?.receiveAddress || ''
  form.value.receiveTelephone = order?.receiveTelephone || ''
  form.value.receiveZipCode = order?.receiveZipCode || ''
  // 只显示原单真正发出过的行；默认数量为 0，由业务员一次填齐本次需要补发的全部数量。
  form.value.items = (order?.items || []).filter(item => Number(item.shippedQty || 0) > 0).map(item => ({ ...item, reshipQty: 0 }))
}

function save() {
  if (!form.value.originalOrderCode) { proxy.$modal.msgWarning('请选择原销售订单'); return }
  if (!form.value.warehouseCode) { proxy.$modal.msgWarning('请选择补发仓库'); return }
  if (!form.value.receivePerson || !form.value.receiveTelephone || !form.value.receiveAddress) { proxy.$modal.msgWarning('收件人、联系电话和地址都要填写'); return }
  // 这张表没挂 el-form 的 rules，手工校验一下格式，口径与全项目一致
  if (!isContactPhone(form.value.receiveTelephone)) { proxy.$modal.msgWarning('联系电话只能填数字、加号、横杠、空格，多个号码用逗号或分号分隔'); return }
  const items = form.value.items.filter(item => Number(item.reshipQty || 0) > 0)
  if (!items.length) { proxy.$modal.msgWarning('请填写至少一项补发数量'); return }
  proxy.$modal.confirm('确认这些就是本次需要补齐的全部商品？创建后同一原订单不能再建第二张补发单。').then(() => {
    saving.value = true
    return addSalesReship({
      ...form.value,
      items: items.map(item => ({ originalOrderDetailCode: item.detailCode, qty: item.reshipQty }))
    })
  }).then(r => {
    proxy.$modal.msgSuccess(r.msg || '销售补发单创建成功')
    createOpen.value = false
    loadList()
  }).finally(() => { saving.value = false })
}

function openShip(row) {
  shipForm.reshipCode = row.reshipCode
  shipForm.carrier = row.carrier || ''
  shipForm.logisticsNo = row.logisticsNo || ''
  shipOpen.value = true
}

function doShip() {
  proxy.$modal.confirm('确认把该补发单内全部商品一次性发出？完成后不能再次补发。').then(() => {
    saving.value = true
    return shipSalesReship(shipForm.reshipCode, { carrier: shipForm.carrier, logisticsNo: shipForm.logisticsNo })
  }).then(r => {
    proxy.$modal.msgSuccess(r.msg || '补发完成')
    shipOpen.value = false
    loadList()
  }).finally(() => { saving.value = false })
}

function remove(row) {
  proxy.$modal.confirm(`确认删除补发单 ${row.reshipCode}？删除后原订单 ${row.originalOrderCode} 可以重新创建补发单。`).then(() => {
    saving.value = true
    return delSalesReship(row.reshipCode)
  }).then(r => {
    proxy.$modal.msgSuccess(r.msg || '补发单已删除')
    loadList()
  }).catch(() => {}).finally(() => { saving.value = false })
}

function openDetail(row) {
  getSalesReship(row.reshipCode).then(r => { detail.value = { ...(r.data || {}), items: r.data?.items || [] }; detailOpen.value = true })
}

onMounted(() => {
  loadList()
  listWarehouse({ pageSize: 100 }).then(r => { warehouses.value = r.rows || [] })
})
</script>

<style scoped>
.section-title { margin: 4px 0 14px; padding-left: 10px; border-left: 3px solid var(--el-color-primary); font-weight: 600; }
.mt20 { margin-top: 20px; }
</style>

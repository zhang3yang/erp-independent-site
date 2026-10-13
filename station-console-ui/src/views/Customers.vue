<!--
  客户管理。

  boundary §11 原来把这一格列在「留位置」，但 u_s_inquiry.buyercode 那一列建库时
  就留好了钩子，注释写着「本站客户编码……将来转的是这里」。这一页就是那个「这里」。

  ⚠ 和 ERP 的销售客户（sdsshop.u_m_buyer）**没有任何关系**（§6c）。
    两张表名字里都有 buyer，但那是两个库里两张互不相干的表。这一页上的任何操作
    都不会碰到 ERP。

  ⚠ 这一页**没有「新建客户」**。档案是询盘/下单时按邮箱长出来的：
    谁发了询盘、谁下了单，后端就为他落一行。管理台能改的只有标签和备注。

    没有「新建」按钮，就不会有一个对不上任何来往记录的空档案——
    和用户管理那边「谁从 ERP 跳进来就落一行」是同一个思路。

  ⚠ 联系方式也不给改：那是客户自己填的，改了就对不上他的来信。
    它跟着最近一次提交自动更新。
-->
<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      :title="$t('customers.lead.title')"
      :description="$t('customers.lead.desc')" />

    <el-form :inline="true" :model="query">
      <el-form-item :label="$t('common.search')">
        <el-input v-model="query.email" clearable :placeholder="$t('customers.search')"
          style="width: 250px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item :label="$t('customers.col.country')">
        <el-input v-model="query.country" clearable :placeholder="$t('customers.countryPlaceholder')" style="width: 130px"
          @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="reset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="buyerCode" :label="$t('customers.col.code')" width="140" />
      <el-table-column prop="company" :label="$t('inquiry.col.company')" min-width="170" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.company || '—' }}</template>
      </el-table-column>
      <el-table-column prop="name" :label="$t('orders.col.contact')" width="110" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.name || '—' }}</template>
      </el-table-column>
      <el-table-column prop="email" :label="$t('inquiry.col.email')" min-width="180" :show-overflow-tooltip="true" />
      <el-table-column prop="country" :label="$t('customers.col.country')" width="100">
        <template #default="scope">{{ scope.row.country || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('overview.pv.colInquiry')" width="70" align="center">
        <template #default="scope">{{ scope.row.inquiryCount || 0 }}</template>
      </el-table-column>
      <el-table-column :label="$t('overview.pv.colOrder')" width="70" align="center">
        <template #default="scope">{{ scope.row.orderCount || 0 }}</template>
      </el-table-column>
      <el-table-column prop="tag" :label="$t('customers.col.tag')" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.tag" size="small">{{ scope.row.tag }}</el-tag>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastActive" :label="$t('customers.col.lastActive')" width="160" />
      <el-table-column :label="$t('common.operate')" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['site:buyer:query']"
            @click="openDetail(scope.row.buyerCode)">{{ $t('common.detail') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 详情 ===================== -->
    <el-dialog v-model="detailOpen" :title="$t('customers.detail.title')" width="900px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="$t('customers.col.code')">{{ buyer.buyerCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('customers.detail.firstSeen')">{{ buyer.createTime }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.company')">{{ buyer.company || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('orders.col.contact')">{{ buyer.name || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.email')">
          <el-link type="primary" :href="`mailto:${buyer.email}`">{{ buyer.email }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.phone')">{{ buyer.phone || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('customers.col.country')">{{ buyer.country || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('customers.col.lastActive')">{{ buyer.lastActive || $t('common.none') }}</el-descriptions-item>
      </el-descriptions>

      <!-- ⚠ 联系方式在上面是只读的，能改的只有这两项。理由见文件头 -->
      <div class="section-title mt20">{{ $t('customers.note.section') }}</div>
      <el-form label-width="90px">
        <el-form-item :label="$t('customers.col.tag')">
          <el-input v-model="noteForm.tag" maxlength="50" style="width: 240px"
            :placeholder="$t('customers.tagPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('inquiry.followup.remark')">
          <el-input v-model="noteForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit
            :placeholder="$t('customers.remarkPlaceholder')" />
        </el-form-item>
      </el-form>

      <div class="section-title mt20">{{ $t('customers.orders.section', { n: orders.length }) }}</div>
      <el-table :data="orders" size="small" border :empty-text="$t('customers.orders.empty')">
        <el-table-column prop="createTime" :label="$t('orders.col.placedAt')" width="160" />
        <el-table-column prop="orderCode" :label="$t('orders.col.code')" width="140" />
        <el-table-column :label="$t('orders.col.amount')" width="150" align="right">
          <template #default="scope">
            {{ scope.row.currency }} {{ money(scope.row.subtotal) }}
            <span v-if="scope.row.inquireOnly > 0" class="text-muted small">
              {{ $t('customers.plusOnRequest', { n: scope.row.inquireOnly }) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('common.status')" width="100">
          <template #default="scope">
            <el-tag size="small">{{ orderStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="" min-width="80">
          <template #default="scope">
            <el-button link type="primary" @click="goOrder(scope.row.orderCode)">{{ $t('customers.view') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title mt20">{{ $t('customers.inquiries.section', { n: inquiries.length }) }}</div>
      <el-table :data="inquiries" size="small" border :empty-text="$t('customers.inquiries.empty')">
        <el-table-column prop="createTime" :label="$t('inquiry.col.receivedAt')" width="160" />
        <el-table-column prop="productTitle" :label="$t('inquiry.col.product')" min-width="170" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.productTitle || $t('common.none') }}</template>
        </el-table-column>
        <el-table-column prop="message" :label="$t('inquiry.col.message')" min-width="220" :show-overflow-tooltip="true" />
        <el-table-column prop="status" :label="$t('common.status')" width="100">
          <template #default="scope">
            <el-tag size="small">{{ inquiryStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="detailOpen = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" :loading="saving" v-hasPermi="['site:buyer:edit']"
          @click="saveNote">{{ $t('customers.note.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SiteCustomers">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listSiteBuyer, getSiteBuyer, saveSiteBuyerNote } from '@/api/console'
import { t } from '@/i18n'

const route = useRoute()
const router = useRouter()

const loading = ref(false), saving = ref(false)
const total = ref(0)
const rows = ref([])
/*
  订单和询盘状态的译名。⚠ 两张小表显示的是**别的模块的**状态值，
  所以直接借 orders.* / inquiry.* 那两组键，别在这里再写一份中文——
  写两份的结果必然是「客户页说已发货、订单页说已出运」。
*/
const ORDER_STATUS_KEY = {
  待确认: 'orders.status.pending',
  已确认: 'orders.status.confirmed',
  已发货: 'orders.status.shipped',
  已完成: 'orders.status.done',
  已取消: 'orders.status.cancelled'
}
const orderStatusLabel = s => (ORDER_STATUS_KEY[s] ? t(ORDER_STATUS_KEY[s]) : s)

const INQUIRY_STATUS_KEY = {
  待处理: 'inquiry.status.pending',
  已联系: 'inquiry.status.contacted',
  已成交: 'inquiry.status.won',
  无效: 'inquiry.status.invalid'
}
const inquiryStatusLabel = s => (INQUIRY_STATUS_KEY[s] ? t(INQUIRY_STATUS_KEY[s]) : s)

const detailOpen = ref(false)
const buyer = ref({})
const orders = ref([])
const inquiries = ref([])
const noteForm = reactive({ tag: '', remark: '' })
const query = reactive({
  email: undefined, country: undefined, pageNum: 1, pageSize: 10
})

function loadList() {
  loading.value = true
  listSiteBuyer(query)
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => (loading.value = false))
}

function search() {
  query.pageNum = 1
  loadList()
}

function reset() {
  query.email = undefined
  query.country = undefined
  search()
}

function openDetail(buyerCode) {
  getSiteBuyer(buyerCode).then(r => {
    const data = r.data || {}
    buyer.value = data.buyer || {}
    orders.value = data.orders || []
    inquiries.value = data.inquiries || []
    noteForm.tag = buyer.value.tag || ''
    noteForm.remark = buyer.value.remark || ''
    detailOpen.value = true
  })
}

function saveNote() {
  saving.value = true
  saveSiteBuyerNote(buyer.value.buyerCode, noteForm.tag, noteForm.remark)
    .then(() => { ElMessage.success(t('common.saved')); loadList() })
    .finally(() => (saving.value = false))
}

function goOrder(orderCode) {
  detailOpen.value = false
  // 订单页按单号搜索。搜索框那一格同时查单号/联系人/公司/邮箱，所以直接塞单号进去
  router.push({ path: '/orders', query: { contactName: orderCode } })
}

function money(value) {
  if (value === null || value === undefined || value === '') return '0'
  const n = Number(value)
  if (Number.isNaN(n)) return String(value)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

onMounted(() => {
  loadList()
  // 从订单详情里点「客户档案」跳过来时，直接把那个人的详情打开——
  // 否则人落在一个列表上还得自己找一遍
  if (route.query.buyerCode) {
    openDetail(String(route.query.buyerCode))
  }
})
</script>

<style scoped>
.mb8 { margin-bottom: 8px; }
.mt20 { margin-top: 20px; }
.small { font-size: 12px; }
.text-muted { color: var(--el-text-color-secondary); }
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}
</style>

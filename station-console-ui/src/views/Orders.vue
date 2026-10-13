<!--
  订单管理。

  boundary §11 原来把这一格列在「留位置」（菜单 + 路由 + 空页），理由是需求只有名字。
  现在它有需求了：前台的询价流程已经把模型定完了（station-site/app/lib/shop.ts），
  而那些单子在这之前只存在买家自己的浏览器里，商家只收到一封邮件。

  ⚠ 这一页**没有「新建订单」**。订单只有一个来源：买家在前台自己提交。
    管理台能做的是看和推进状态——这和询盘页一样，是「跟进」而不是「录入」。

  ⚠ 也没有「同步到 ERP」按钮。要点 4：站点订单和 ERP 订单没有隶属关系；
    §8：一期人工在 ERP 里录一张，不做也不预埋接口。加这个按钮之前先读那两节。
-->
<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      :title="$t('orders.lead.title')"
      :description="$t('orders.lead.desc')" />

    <el-form :inline="true" :model="query">
      <el-form-item :label="$t('common.status')">
        <!-- ⚠ value 是库里的中文串，和后端 SiteOrder 那五个常量一字不差，只翻 label -->
        <el-select v-model="query.status" clearable :placeholder="$t('common.all')" style="width: 130px" @change="search">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('common.search')">
        <el-input v-model="query.contactName" clearable :placeholder="$t('orders.search')"
          style="width: 230px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="reset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="createTime" :label="$t('orders.col.placedAt')" width="160" />
      <el-table-column prop="orderCode" :label="$t('orders.col.code')" width="140" />
      <el-table-column prop="contactName" :label="$t('orders.col.contact')" width="110" :show-overflow-tooltip="true" />
      <el-table-column prop="company" :label="$t('inquiry.col.company')" min-width="150" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.company || '—' }}</template>
      </el-table-column>
      <el-table-column prop="country" :label="$t('orders.col.country')" width="100" :show-overflow-tooltip="true" />
      <el-table-column :label="$t('orders.col.amount')" width="150" align="right">
        <template #default="scope">
          <span>{{ scope.row.currency }} {{ money(scope.row.subtotal) }}</span>
          <!-- 面议行数要显示出来：不显示的话运营会以为这个金额就是全部 -->
          <div v-if="scope.row.inquireOnly > 0" class="text-muted small">
            {{ $t('orders.moreOnRequest', { n: scope.row.inquireOnly }) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('common.status')" width="100">
        <template #default="scope">
          <el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operate')" width="190" fixed="right">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['site:order:query']"
            @click="openDetail(scope.row)">{{ $t('common.detail') }}</el-button>
          <!-- 下一步的按钮直接给出来，不用先点详情——运营最常做的就是「确认」和「发货」 -->
          <el-button v-if="nextStatus(scope.row.status)" link type="primary"
            v-hasPermi="['site:order:edit']"
            @click="advance(scope.row, nextStatus(scope.row.status))">
            {{ nextLabel(scope.row.status) }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 详情 ===================== -->
    <el-dialog v-model="detailOpen" :title="$t('orders.detail.title')" width="860px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="$t('orders.col.code')">{{ detail.orderCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('orders.col.placedAt')">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.status')">
          <el-tag size="small" :type="statusType(detail.status)">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('orders.detail.source')">
          {{ detail.source === 'console' ? $t('orders.source.console') : $t('orders.source.storefront') }}
        </el-descriptions-item>

        <el-descriptions-item :label="$t('orders.col.contact')">{{ detail.contactName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.company')">{{ detail.company || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.email')">
          <el-link type="primary" :href="`mailto:${detail.email}`">{{ detail.email }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.phone')">{{ detail.phone || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('orders.detail.buyer')">
          <el-link v-if="detail.buyerCode" type="primary" @click="goBuyer(detail.buyerCode)">
            {{ detail.buyerCode }}
          </el-link>
          <span v-else class="text-muted">{{ $t('orders.detail.noBuyer') }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.ip')">{{ detail.clientIp || $t('common.none') }}</el-descriptions-item>

        <el-descriptions-item :label="$t('orders.detail.destination')" :span="2">{{ address(detail) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('orders.detail.shipping')">{{ shipLabel(detail.shippingPref) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('orders.detail.payment')">{{ payLabel(detail.paymentPref) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('orders.detail.note')" :span="2">
          <div class="message-text">{{ detail.buyerNote || '—' }}</div>
        </el-descriptions-item>
      </el-descriptions>

      <!--
        引导式下单的选配（2026-09-28）。订单行只有商品和数量，logo、包装、认证、打样这些都在这里——
        不看这一块就不知道客户要的是什么。中文界面给中文那份，其它界面给买家下单时那种语言的
      -->
      <template v-if="wizardRows.length">
        <div class="section-title mt20">{{ $t('orders.wizard.section') }}</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="(r, i) in wizardRows" :key="i" :label="r.field" label-class-name="wz-label">
            <span class="text-muted wz-group">{{ r.group }}</span>{{ r.value }}
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <div class="section-title mt20">{{ $t('orders.items.section') }}</div>
      <el-table :data="detail.items || []" size="small" border>
        <el-table-column prop="goodsCode" :label="$t('goods.col.code')" width="120" />
        <el-table-column prop="goodsName" :label="$t('goods.col.product')" min-width="180" :show-overflow-tooltip="true">
          <template #default="scope">
            {{ scope.row.goodsName || $t('common.none') }}
            <!-- 型号快照。同一个商品的两个型号在这里是两行，不写明就分不清 -->
            <el-tag v-if="scope.row.comboName" size="small" type="warning" class="ml8">
              {{ scope.row.comboName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('orders.items.unitPrice')" width="120" align="right">
          <template #default="scope">
            <!-- ⚠ 单价 0 是「面议」，不是「免费」。显示成 0.00 会让运营以为报过价了 -->
            <span v-if="scope.row.inquireOnly" class="text-muted">{{ $t('overview.pv.priceOnRequest') }}</span>
            <span v-else>{{ scope.row.currency }} {{ money(scope.row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('orders.items.qty')" width="120" align="right">
          <template #default="scope">{{ money(scope.row.qty) }} {{ scope.row.unit || '' }}</template>
        </el-table-column>
        <el-table-column :label="$t('orders.items.lineTotal')" width="130" align="right">
          <template #default="scope">
            <span v-if="scope.row.inquireOnly" class="text-muted">{{ $t('common.none') }}</span>
            <span v-else>{{ scope.row.currency }} {{ money(scope.row.lineTotal) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="sum-line">
        {{ $t('orders.sum.label') }}<b>{{ detail.currency }} {{ money(detail.subtotal) }}</b>
        <span v-if="detail.inquireOnly > 0" class="text-muted ml8">
          {{ $t('orders.sum.excluded', { n: detail.inquireOnly }) }}
        </span>
        <div class="text-muted small">{{ $t('orders.sum.note') }}</div>
      </div>

      <div class="section-title mt20">{{ $t('orders.progress.section') }}</div>
      <el-steps :active="stepActive(detail.status)" align-center size="small"
        :status="detail.status === '已取消' ? 'error' : 'process'">
        <el-step :title="$t('orders.status.pending')" :description="detail.createTime || ''" />
        <el-step :title="$t('orders.status.confirmed')" :description="detail.confirmTime || ''" />
        <el-step :title="$t('orders.status.shipped')" :description="detail.shipTime || ''" />
        <el-step :title="$t('orders.status.done')" :description="detail.finishTime || ''" />
      </el-steps>
      <div v-if="detail.status === '已取消'" class="text-muted mt8">{{ $t('orders.cancelled') }}</div>

      <div class="section-title mt20">{{ $t('inquiry.followup.section') }}</div>
      <el-form label-width="90px">
        <el-form-item :label="$t('inquiry.followup.remark')">
          <el-input v-model="remark" type="textarea" :rows="3" maxlength="500" show-word-limit
            :placeholder="$t('orders.remarkPlaceholder')" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="detailOpen = false">{{ $t('common.close') }}</el-button>
        <el-button v-hasPermi="['site:order:edit']" :loading="saving" @click="saveRemark">{{ $t('orders.saveRemark') }}</el-button>
        <el-button v-if="detail.status !== '已取消'" type="danger" plain
          v-hasPermi="['site:order:edit']" @click="advance(detail, '已取消')">{{ $t('orders.cancel') }}</el-button>
        <el-button v-if="nextStatus(detail.status)" type="primary"
          v-hasPermi="['site:order:edit']" @click="advance(detail, nextStatus(detail.status))">
          {{ nextLabel(detail.status) }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SiteOrders">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSiteOrder, getSiteOrder, changeSiteOrderStatus, saveSiteOrderRemark } from '@/api/console'
import { t, locale } from '@/i18n'

const route = useRoute()
const router = useRouter()

/**
 * 状态词表。⚠ 必须和后端 SiteOrder 里那五个常量一字不差——
 * 库里存的是中文，这里传回去的也是中文，差一个字后端就报「未知的订单状态」。
 */
const statuses = ['待确认', '已确认', '已发货', '已完成', '已取消']

/** 正向流转顺序。「已取消」不在里面：它是任何一步都能走的旁路 */
const FLOW = ['待确认', '已确认', '已发货', '已完成']

/* 状态和按钮动词的译名。⚠ 只翻显示，值一律是上面那几个中文串 */
const STATUS_KEY = {
  待确认: 'orders.status.pending',
  已确认: 'orders.status.confirmed',
  已发货: 'orders.status.shipped',
  已完成: 'orders.status.done',
  已取消: 'orders.status.cancelled'
}
const statusLabel = s => (STATUS_KEY[s] ? t(STATUS_KEY[s]) : s)

const loading = ref(false), saving = ref(false)
const total = ref(0)
const rows = ref([])
const detailOpen = ref(false)
const detail = ref({})

/**
 * 引导式下单的选配快照（u_s_order.wizardjson）拆成行。普通购物车的单没有，返回空数组。
 * 快照里文字存了两份（后端 OrderWizardService.snapshot）：中文界面取 *Zh 那份，
 * 英文 / 法语界面取买家下单时那种语言的——法国站长看法语正合适。
 */
const wizardRows = computed(() => {
  const raw = detail.value?.wizardJson
  if (!raw) return []
  try {
    const zh = locale.value === 'zh'
    return (JSON.parse(raw) || []).map(r => ({
      group: zh ? r.groupZh : r.group,
      field: zh ? r.fieldZh : r.field,
      value: zh ? r.valueZh : r.value
    }))
  } catch {
    return []
  }
})
const remark = ref('')
const query = reactive({
  // 不带 siteCode：一个管理台只管一个站，站点由服务端会话限定
  status: undefined, contactName: undefined, pageNum: 1, pageSize: 10
})

function loadList() {
  loading.value = true
  listSiteOrder(query)
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => (loading.value = false))
}

function search() {
  query.pageNum = 1
  loadList()
}

function reset() {
  query.status = undefined
  query.contactName = undefined
  search()
}

function openDetail(row) {
  getSiteOrder(row.orderCode).then(r => {
    detail.value = r.data || {}
    remark.value = detail.value.remark || ''
    detailOpen.value = true
  })
}

/** 下一个状态。已完成和已取消没有下一步 */
function nextStatus(status) {
  const i = FLOW.indexOf(status)
  return i >= 0 && i < FLOW.length - 1 ? FLOW[i + 1] : null
}

/** 按钮上的动词，不是状态名——「确认」比「改成已确认」短，而且是运营真正在做的事 */
function nextLabel(status) {
  const key = { 待确认: 'orders.act.confirm', 已确认: 'orders.act.ship', 已发货: 'orders.act.finish' }[status]
  return key ? t(key) : ''
}

function advance(row, target) {
  if (!target) return
  const isCancel = target === '已取消'
  ElMessageBox.confirm(
    isCancel
      ? t('orders.msg.cancelConfirm', { code: row.orderCode })
      : t('orders.msg.advanceConfirm', { code: row.orderCode, status: statusLabel(target) }),
    t('common.confirmTitle'), { type: isCancel ? 'warning' : 'info' })
    .then(() => changeSiteOrderStatus(row.orderCode, target))
    .then(() => {
      ElMessage.success(t('orders.msg.updated'))
      detailOpen.value = false
      loadList()
    })
    .catch(() => {})
}

function saveRemark() {
  saving.value = true
  saveSiteOrderRemark(detail.value.orderCode, remark.value)
    .then(() => { ElMessage.success(t('orders.msg.remarkSaved')); loadList() })
    .finally(() => (saving.value = false))
}

function goBuyer(buyerCode) {
  detailOpen.value = false
  router.push({ path: '/customers', query: { buyerCode } })
}

/* ---------------- 显示用的小工具 ---------------- */

function statusType(status) {
  return { 待确认: 'warning', 已确认: 'primary', 已发货: 'primary', 已完成: 'success', 已取消: 'info' }[status] || ''
}

/** el-steps 的 active。已取消时停在它实际走到的那一步之后，靠 status="error" 表达 */
function stepActive(status) {
  if (status === '已取消') return FLOW.length
  const i = FLOW.indexOf(status)
  return i < 0 ? 0 : i + 1
}

/**
 * 金额和数量都用这个格。
 *
 * ⚠ 后端是 decimal(18,4)，JSON 里可能是 `1560.0000` 也可能是 `1560`。
 *   直接显示会出现「1560.0000 米」这种。这里去掉无意义的尾零，但保留真的有小数的那部分。
 */
function money(value) {
  if (value === null || value === undefined || value === '') return '0'
  const n = Number(value)
  if (Number.isNaN(n)) return String(value)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

function address(order) {
  if (!order) return '—'
  const parts = [order.addrLine1, order.addrLine2, order.city, order.region, order.postal, order.country]
  const text = parts.filter(Boolean).join(', ')
  return text || '—'
}

/**
 * 运输和付款倾向的中文名。
 *
 * ⚠ 这两组 id 的定义在前台 station-site/app/lib/shop.ts 的 SHIPPING_PREFS /
 *   PAYMENT_PREFS 里，那边是买家看到的多语文案，这里是运营看到的中文。
 *   加一种运输方式要改三处：那个常量、那边的文案表、这里。
 */
function shipLabel(pref) {
  const key = { sea: 'orders.ship.sea', air: 'orders.ship.air', express: 'orders.ship.express', agent: 'orders.ship.agent' }[pref]
  return key ? t(key) : t('orders.notChosen')
}

function payLabel(pref) {
  const key = { tt: 'orders.pay.tt', lc: 'orders.pay.lc', paypal: 'orders.pay.paypal', other: 'orders.pay.other' }[pref]
  return key ? t(key) : t('orders.notChosen')
}

onMounted(() => {
  // 从客户详情里点「查看」跳过来时带着单号。搜索框那一格同时查
  // 单号/联系人/公司/邮箱，所以直接把它填进去再查
  if (route.query.contactName) {
    query.contactName = String(route.query.contactName)
  }
  loadList()
})
</script>

<style scoped>
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
.mt20 { margin-top: 20px; }
.ml8 { margin-left: 8px; }
.small { font-size: 12px; }
.text-muted { color: var(--el-text-color-secondary); }
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}
/* 买家说明是多行文本，换行要留着 */
.message-text { white-space: pre-line; }
/* 引导下单那一块：分组名做成一个小前缀，字段名在左边那一栏 */
.wz-group { display: inline-block; min-width: 88px; margin-right: 10px; font-size: 12px; }
.sum-line {
  margin-top: 10px;
  text-align: right;
  font-size: 13px;
}
</style>

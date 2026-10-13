<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      :title="$t('inquiry.lead.title')"
      :description="$t('inquiry.lead.desc')" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <el-form-item :label="$t('common.status')">
        <!-- ⚠ value 是库里那几个中文串（u_s_inquiry.status），只翻 label -->
        <el-select v-model="query.status" clearable style="width: 120px" @change="search">
          <el-option v-for="s in statuses" :key="s" :label="statusLabel(s)" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('inquiry.col.customer')">
        <el-input v-model="query.name" clearable :placeholder="$t('inquiry.search.name')" style="width: 190px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="reset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="createTime" :label="$t('inquiry.col.receivedAt')" width="160" />
      <!-- 没有「站点」列：一个管理台只管一个站，每行都显示同一个名字纯属浪费宽度。
           ERP 内置管理台那版留着它，是因为从主菜单进去时能看到多个站的询盘 -->
      <el-table-column prop="name" :label="$t('inquiry.col.customer')" width="120" :show-overflow-tooltip="true" />
      <el-table-column prop="company" :label="$t('inquiry.col.company')" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.company || $t('common.none') }}</template>
      </el-table-column>
      <el-table-column prop="email" :label="$t('inquiry.col.email')" min-width="170" :show-overflow-tooltip="true" />
      <el-table-column prop="phone" :label="$t('inquiry.col.phone')" width="130">
        <template #default="scope">{{ scope.row.phone || $t('common.none') }}</template>
      </el-table-column>
      <el-table-column prop="productTitle" :label="$t('inquiry.col.product')" min-width="180" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ scope.row.productTitle || $t('common.none') }}
          <el-tag v-if="scope.row.comboName" size="small" type="warning" class="ml8">
            {{ scope.row.comboName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" :label="$t('inquiry.col.message')" min-width="200" :show-overflow-tooltip="true" />
      <el-table-column prop="status" :label="$t('common.status')" width="100">
        <template #default="scope">
          <el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operate')" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['site:inquiry:query']" @click="openDetail(scope.row)">{{ $t('common.detail') }}</el-button>
          <!--
            「转客户」回来了，但**转的东西变了**。

            这个按钮以前调 POST /api/inquiry/{id}/convert，往 ERP 的 sdsshop.u_m_buyer
            建一条客户档案——那是一条互通（boundary §3.2），整个删掉了，
            **那一版永远不会回来**。

            现在同一个路径挂到的是**本站自己的** u_s_buyer，也就是
            u_s_inquiry.buyercode 那一列注释一直写着的「将来转的是这里」。全程不出这台机器。

            已经转过的显示成客户编码，点它跳客户档案。可以重复点：
            后端只会更新联系方式，不会重复建档。
          -->
          <el-button v-if="!scope.row.buyerCode" link type="primary"
            v-hasPermi="['site:buyer:edit']" @click="convert(scope.row)">{{ $t('inquiry.convert') }}</el-button>
          <el-button v-else link type="success" @click="goBuyer(scope.row.buyerCode)">
            {{ scope.row.buyerCode }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 详情 ===================== -->
    <el-dialog v-model="detailOpen" :title="$t('inquiry.detail.title')" width="760px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="$t('inquiry.detail.code')">{{ detail.inquiryCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.receivedAt')">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.site')">{{ detail.siteName || detail.siteCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.status')">
          <el-tag size="small" :type="statusType(detail.status)">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.name')">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.company')">{{ detail.company || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.email')">
          <el-link type="primary" :href="`mailto:${detail.email}`">{{ detail.email }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.phone')">{{ detail.phone || $t('common.none') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.col.product')" :span="2">
          {{ detail.productTitle || '—' }}
          <span v-if="detail.goodsCode" class="text-muted ml8">（{{ detail.goodsCode }}）</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.message')" :span="2">
          <div class="message-text">{{ detail.message }}</div>
        </el-descriptions-item>
        <!-- 来源页面和 IP 主要用来判断垃圾询盘：同一个 IP 一天来十几条基本就是机器人 -->
        <el-descriptions-item :label="$t('inquiry.detail.page')" :span="2">
          <el-link v-if="detail.pageUrl" type="primary" :href="detail.pageUrl" target="_blank" rel="noopener">
            {{ detail.pageUrl }}
          </el-link>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('inquiry.detail.ip')">{{ detail.clientIp || $t('common.none') }}</el-descriptions-item>
      </el-descriptions>

      <div class="section-title mt20">{{ $t('inquiry.followup.section') }}</div>
      <el-form label-width="90px">
        <el-form-item :label="$t('inquiry.followup.status')">
          <el-select v-model="handleForm.status" style="width: 200px">
            <el-option v-for="s in statuses" :key="s" :label="statusLabel(s)" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inquiry.followup.owner')">
          <el-input v-model="handleForm.owner" style="width: 200px" maxlength="50"
            :placeholder="$t('inquiry.followup.ownerPlaceholder')" />
          <!--
            ⚠ 负责人和上面那个「处理状态」旁边显示的处理人不是一回事，
              这句话要写出来，否则运营会以为重复了。
          -->
          <span class="hint ml8">
            {{ $t('inquiry.followup.ownerHint', { who: detail.handleUser || $t('inquiry.followup.nobody') }) }}
          </span>
        </el-form-item>
        <el-form-item :label="$t('inquiry.followup.next')">
          <el-date-picker v-model="handleForm.nextFollow" type="date" value-format="YYYY-MM-DD"
            :placeholder="$t('inquiry.followup.nextPlaceholder')" style="width: 200px" />
          <span class="hint ml8">
            {{ $t('inquiry.followup.nextHint') }}
          </span>
        </el-form-item>
        <el-form-item :label="$t('inquiry.followup.remark')">
          <el-input v-model="handleForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit
            :placeholder="$t('inquiry.followup.remarkPlaceholder')" />
        </el-form-item>
      </el-form>

      <!--
        跟进记录。和上面那个「内部备注」是两件事：
        备注只有一条、会被覆盖；这里是**只增不改**的历史。
        跟进最需要的恰恰是时间顺序——「上次他说预算不够」「三周后再问一次」，
        压成一段文本就没了。
      -->
      <div class="section-title mt20">{{ $t('inquiry.log.section') }}</div>

      <div class="fu-add">
        <!-- ⚠ action 也是库里的中文值（u_s_followup.action），只翻 label -->
        <el-select v-model="fuForm.action" size="small" style="width: 110px">
          <el-option v-for="a in FU_ACTIONS" :key="a" :label="actionLabel(a)" :value="a" />
        </el-select>
        <el-input v-model="fuForm.content" type="textarea" :rows="2" maxlength="2000" show-word-limit
          class="fu-input" :placeholder="$t('inquiry.log.placeholder')" />
        <el-button type="primary" size="small" :loading="fuSaving"
          v-hasPermi="['site:inquiry:edit']" @click="addFu">{{ $t('inquiry.log.add') }}</el-button>
      </div>
      <p class="hint" v-html="$t('inquiry.log.hint')"></p>

      <el-timeline v-if="followups.length" class="fu-list">
        <el-timeline-item v-for="f in followups" :key="f.id" :timestamp="f.createTime" placement="top">
          <div class="fu-head">
            <el-tag size="small" effect="plain">{{ actionLabel(f.action) }}</el-tag>
            <span class="hint">{{ f.createUser || $t('common.none') }}</span>
          </div>
          <!-- 纯文本，{{ }} 渲染，Vue 自己转义。⚠ 这是内部记录，前台永不显示它 -->
          <div class="fu-content">{{ f.content }}</div>
        </el-timeline-item>
      </el-timeline>
      <p v-else-if="!fuLoading" class="hint fu-empty">{{ $t('inquiry.log.empty') }}</p>
      <template #footer>
        <el-button @click="detailOpen = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" :loading="saving" v-hasPermi="['site:inquiry:edit']" @click="saveHandle">{{ $t('inquiry.followup.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SiteInquiry">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listInquiry, getInquiry, updateInquiry, convertInquiryToBuyer,
  listFollowup, addFollowup
} from '@/api/console'
import { useRoute, useRouter } from 'vue-router'
import { t } from '@/i18n'

// ⚠「已转客户」改成「已成交」：以前「转」的是 ERP 的客户档案，现在没有那个动作了（§3.2）。
//   后端 SiteInquiry.STATUS_CONVERTED 的值同步改了，两边必须一致。
/* ⚠ 库里存的就是这四个中文串（u_s_inquiry.status），筛选和后端判断都用它，别改 */
const statuses = ['待处理', '已联系', '已成交', '无效']

const STATUS_KEY = {
  待处理: 'inquiry.status.pending',
  已联系: 'inquiry.status.contacted',
  已成交: 'inquiry.status.won',
  无效: 'inquiry.status.invalid'
}
const statusLabel = s => (STATUS_KEY[s] ? t(STATUS_KEY[s]) : s)

const loading = ref(false), saving = ref(false), showSearch = ref(true)
const total = ref(0)
const rows = ref([])
const detailOpen = ref(false)
const detail = ref({})
const query = reactive({
  // 不带 siteCode：只看本站的询盘。站点由服务端会话限定，前端传了也会被 ERP 侧覆盖
  status: undefined, name: undefined, pageNum: 1, pageSize: 10
})
const handleForm = reactive({ status: '', remark: '', owner: '', nextFollow: null })

/*
 * 跟进记录。⚠ 和 handleForm.remark 是两件事：
 *   remark    一条、会被覆盖  —— 「现在的情况是什么」
 *   followups 多条、只增不改  —— 「一路是怎么走过来的」
 */
/* 同上：这几个值会落库（u_s_followup.action） */
const FU_ACTIONS = ['记录', '邮件', '电话', 'whatsapp', '会面']

const ACTION_KEY = {
  记录: 'inquiry.action.note',
  邮件: 'inquiry.action.email',
  电话: 'inquiry.action.call',
  whatsapp: 'inquiry.action.whatsapp',
  会面: 'inquiry.action.meeting'
}
const actionLabel = a => (ACTION_KEY[a] ? t(ACTION_KEY[a]) : a)
const followups = ref([])
const fuLoading = ref(false)
const fuSaving = ref(false)
const fuForm = reactive({ action: '记录', content: '' })

function loadList() {
  loading.value = true
  listInquiry(query)
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => loading.value = false)
}
function search() {
  query.pageNum = 1
  loadList()
}
function reset() {
  query.status = undefined; query.name = undefined
  search()
}

function statusType(status) {
  return { 待处理: 'warning', 已联系: 'primary', 已成交: 'success', 无效: 'info' }[status] || 'info'
}

const router = useRouter()

/**
 * 转成本站客户档案。
 *
 * ⚠ 不是 ERP 那个「转客户」。见表格里那个按钮上方的注释。
 *
 * 没有「转过一次就不能再转」的限制：后端按邮箱找人，已经有档案的只更新联系方式。
 * 所以这里也不用先判 status——「无效」的询盘运营也可能想留个档案。
 */
function convert(row) {
  ElMessageBox.confirm(
    t('inquiry.msg.convertConfirm', { name: row.name, email: row.email }),
    t('inquiry.convert'), { type: 'info' })
    .then(() => convertInquiryToBuyer(row.id))
    .then(r => {
      const buyer = r.data || {}
      ElMessage.success(t('inquiry.msg.converted', { code: buyer.buyerCode }))
      loadList()
    })
    .catch(() => {})
}

function goBuyer(buyerCode) {
  router.push({ path: '/customers', query: { buyerCode } })
}

function openDetail(row) {
  getInquiry(row.id).then(r => {
    detail.value = r.data || {}
    handleForm.status = detail.value.status
    handleForm.remark = detail.value.remark || ''
    handleForm.owner = detail.value.owner || ''
    handleForm.nextFollow = detail.value.nextFollow || null
    fuForm.action = '记录'
    fuForm.content = ''
    detailOpen.value = true
    loadFollowups(detail.value.id)
  })
}

function loadFollowups(id) {
  if (!id) return
  fuLoading.value = true
  listFollowup(id)
    .then(r => { followups.value = r.data || [] })
    .catch(() => { followups.value = [] })
    .finally(() => { fuLoading.value = false })
}

function addFu() {
  if (!fuForm.content?.trim()) { ElMessage.warning(t('inquiry.msg.emptyContent')); return }
  fuSaving.value = true
  addFollowup(detail.value.id, { action: fuForm.action, content: fuForm.content.trim() })
    .then(() => {
      fuForm.content = ''
      loadFollowups(detail.value.id)
      /*
       * 第一条跟进会把「待处理」推成「已联系」——那是后端做的。
       * 这里把表单里的状态也跟上，否则接着点「保存跟进」会用表单里那个
       * 还是「待处理」的旧值把后端刚推好的状态又写回去。
       */
      if (handleForm.status === '待处理') handleForm.status = '已联系'
      loadList()
    })
    // ⚠ 空 catch：request.js 的拦截器已经弹过一次了
    .catch(() => {})
    .finally(() => { fuSaving.value = false })
}

function saveHandle() {
  saving.value = true
  // ⚠ 整份送：后端 updateHandle 里 remark / owner / nextfollow 都是直赋值
  //   （不是 coalesce），少送一个就等于把它清空。这是有意的——
  //   把负责人取消掉、把跟进日期删掉，都要能做到
  updateInquiry({
    id: detail.value.id,
    status: handleForm.status,
    remark: handleForm.remark,
    owner: handleForm.owner || null,
    nextFollow: handleForm.nextFollow || null
  })
    .then(() => { ElMessage.success(t('common.saved')); detailOpen.value = false; loadList() })
    .finally(() => saving.value = false)
}

/*
 * 从营销管理那一页点「去跟进」跳过来时，地址栏带着 ?id=123，直接打开那一条。
 *
 * ⚠ 打开后把参数擦掉（router.replace）：不擦的话关掉弹窗再刷新会又弹一次。
 *   同 Goods.vue 里 openFromQuery 的处理。
 */
const route = useRoute()
function openFromQuery() {
  const id = route.query.id
  if (!id) return
  openDetail({ id: Number(id) })
  router.replace({ path: '/inquiry' })
}

/**
 * 转成 ERP 客户。
 *
 * 外国电话不会写进客户的手机号字段——那个字段全项目按 11 位手机号校验，
 * 一个 +1 555 0100 直接过不去。电话和邮箱都写进客户备注里，邮箱另外落到「买家账号」。
 */

onMounted(() => {
  loadList()
  openFromQuery()
})
</script>

<style scoped>
.text-muted {
  color: var(--el-text-color-secondary);
}
/* 下拉选项里的次要信息：语言和站点编码，重名时靠它分辨 */
.opt-meta {
  float: right;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.message-text {
  white-space: pre-wrap;
  line-height: 1.7;
  max-height: 200px;
  overflow-y: auto;
}
.section-title {
  margin: 4px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
.mt20 { margin-top: 20px; }
.fu-add {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.fu-input { flex: 1; }
.fu-list { margin-top: 12px; padding-left: 2px; }
.fu-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
/* 跟进内容保留换行：销售常常分行写「他说……」「下一步……」 */
.fu-content {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 13px;
}
.fu-empty { padding: 12px 0; }
.ml8 { margin-left: 8px; }
</style>

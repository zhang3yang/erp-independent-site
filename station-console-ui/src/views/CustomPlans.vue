<!--
  定制方案（2026-09-29）：买家在前台 /customize 页上贴自己的 logo、换开机画面后保存的方案。
  设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md §8。

  ⚠ **只读**。方案是买家发来的原始凭据，这里不改、不删：
    买家拿着链接来问「就照这个做」时，商家看到的必须和他发的是同一份。

  ⚠ 3D 预览只挂在 destroy-on-close 的弹窗里（同 ModelPreview 的显存规矩），列表里不渲染模型。

  邮箱、备注只在这里显示——前台方案页链接谁拿到都能看，那边不下发这两项。
-->
<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      :title="$t('customPlan.lead.title')" :description="$t('customPlan.lead.desc')" />

    <el-form :inline="true" :model="query">
      <el-form-item :label="$t('customPlan.col.goods')">
        <el-input v-model="query.goodsCode" clearable :placeholder="$t('customPlan.goodsPlaceholder')"
          style="width: 180px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item :label="$t('customPlan.col.createTime')">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" unlink-panels
          :start-placeholder="$t('customPlan.from')" :end-placeholder="$t('customPlan.to')" style="width: 260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="reset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" :empty-text="$t('customPlan.empty')">
      <el-table-column prop="planId" :label="$t('customPlan.col.id')" width="170">
        <template #default="scope"><code>{{ scope.row.planId }}</code></template>
      </el-table-column>
      <el-table-column :label="$t('customPlan.col.goods')" min-width="200" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ scope.row.goodsTitle || '—' }} <span class="text-muted">({{ scope.row.goodsCode }})</span>
        </template>
      </el-table-column>
      <el-table-column prop="email" :label="$t('customPlan.col.email')" min-width="180" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.email || '—' }}</template>
      </el-table-column>
      <el-table-column prop="note" :label="$t('customPlan.col.note')" min-width="200" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.note || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createtime" :label="$t('customPlan.col.createTime')" width="160" />
      <el-table-column :label="$t('common.operate')" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.planId)">{{ $t('common.detail') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 详情 ===================== -->
    <el-dialog v-model="detailOpen" :title="$t('customPlan.detail.title', { id: plan.planId || '' })" width="960px"
      destroy-on-close :close-on-click-modal="false">
      <el-row :gutter="16">
        <el-col :span="15">
          <CustomPlanViewer v-if="detailOpen && plan.model3d" :src="plan.model3d" :marks="marks" />
        </el-col>
        <el-col :span="9">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item :label="$t('customPlan.col.goods')">
              {{ plan.goodsTitle || '—' }} ({{ plan.goodsCode }})
            </el-descriptions-item>
            <el-descriptions-item :label="$t('customPlan.col.createTime')">{{ plan.createtime }}</el-descriptions-item>
            <el-descriptions-item :label="$t('customPlan.col.email')">
              <el-link v-if="plan.email" type="primary" :href="`mailto:${plan.email}`">{{ plan.email }}</el-link>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('customPlan.col.note')">
              <span class="pre">{{ plan.note || '—' }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="plan.parentId" :label="$t('customPlan.parent')">
              <el-link type="primary" @click="openDetail(plan.parentId)"><code>{{ plan.parentId }}</code></el-link>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title mt16">{{ $t('customPlan.images') }}</div>
          <div v-if="images.length" class="imgs">
            <a v-for="img in images" :key="img.url" :href="img.url" target="_blank" rel="noopener" class="img" :title="img.label">
              <img :src="img.url" alt="">
              <span>{{ img.label }}</span>
            </a>
          </div>
          <p v-else class="text-muted small">—</p>
          <p class="text-muted small">{{ $t('customPlan.imagesHint') }}</p>
        </el-col>
      </el-row>

      <template #footer>
        <el-link v-if="frontLink" type="primary" :href="frontLink" target="_blank" rel="noopener" class="mr12">
          {{ $t('customPlan.openFront') }}
        </el-link>
        <el-button @click="detailOpen = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SiteCustomPlans">
import { ref, reactive, computed, inject, onMounted, defineAsyncComponent } from 'vue'
import { listCustomPlan, getCustomPlan } from '@/api/console'
import { toFrontUrl } from '@/utils/siteUrl'
import { t } from '@/i18n'

// three.js 那个包只在点开详情时才下
const CustomPlanViewer = defineAsyncComponent(() => import('@/components/CustomPlanViewer.vue'))

const site = inject('stationSite', ref({}))

const loading = ref(false)
const total = ref(0)
const rows = ref([])
const range = ref(null)
const query = reactive({ goodsCode: undefined, pageNum: 1, pageSize: 10 })

const detailOpen = ref(false)
const plan = ref({})
const marks = ref(null)

function loadList() {
  loading.value = true
  const params = {
    goodsCode: query.goodsCode || undefined,
    from: range.value?.[0] || undefined,
    to: range.value?.[1] || undefined,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }
  listCustomPlan(params)
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => (loading.value = false))
}

function search() {
  query.pageNum = 1
  loadList()
}

function reset() {
  query.goodsCode = undefined
  range.value = null
  search()
}

function openDetail(planId) {
  getCustomPlan(planId).then(r => {
    const data = r.data || {}
    // 先关再开：从「来源方案」链接点过去时，让预览组件重新挂一次、换成那一份
    detailOpen.value = false
    plan.value = data.plan || {}
    marks.value = data.marks || null
    setTimeout(() => { detailOpen.value = true })
  })
}

/** 方案里用到的图：买家的 logo + 换过的开机 logo。点开是原图，右键另存即可给工厂 */
const images = computed(() => {
  const m = marks.value || {}
  const out = (m.logos || []).map((l, i) => ({ url: l.src, label: t('customPlan.logoN', { n: i + 1 }) }))
  const boot = m.screen?.bootSrc
  // 只列买家传的（在 custom/ 目录下）；没换就是商家原来那张，不算方案内容
  if (boot && boot.includes('/custom/')) out.push({ url: boot, label: t('customPlan.bootLogo') })
  return out
})

const frontLink = computed(() => {
  const base = toFrontUrl(site.value?.domain)
  return base && plan.value.planId ? `${base}/customize/plan/${plan.value.planId}` : ''
})

onMounted(loadList)
</script>

<style scoped>
.mb8 { margin-bottom: 8px; }
.mt16 { margin-top: 16px; }
.mr12 { margin-right: 12px; }
.small { font-size: 12px; }
.pre { white-space: pre-wrap; word-break: break-word; }
.text-muted { color: var(--el-text-color-secondary); }
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}
.imgs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.img {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  text-decoration: none;
}
.img img {
  width: 100%;
  height: 64px;
  object-fit: contain;
  background: repeating-conic-gradient(#eef1f4 0% 25%, #fff 0% 50%) 50% / 10px 10px;
}
</style>

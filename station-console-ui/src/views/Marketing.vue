<template>
  <div class="mk-page">
    <el-result v-if="!canList" icon="warning" :title="$t('blog.noPermi')"
      :sub-title="$t('mk.noPermiDesc')" />

    <template v-else>
      <!--
        ⚠ 这一段说明这一页为什么长这样，不是废话。

        「营销管理」听上去应该是优惠券和群发邮件。这个站不是——它是 B2B 询盘站，
        前台不收款、价格 0 表示面议、成交靠人跟出来的报价单。优惠券对「面议」
        没有意义；群发邮件的买家基数还小、合规代价却不小（同意横幅还没做）。
        而漏掉一条询盘 = 漏掉一单，并且**漏掉是静悄悄的**。
      -->
      <el-alert type="info" :closable="false" class="mb12" :title="$t('mk.lead.title')">
        <template #default>
          <div class="tip-body">
            <span v-html="$t('mk.lead.body')"></span>
          </div>
        </template>
      </el-alert>

      <!-- 四个数字。点一下切清单 -->
      <div class="mk-stats">
        <div v-for="b in BUCKETS" :key="b.key" class="mk-stat"
          :class="{ on: bucket === b.key, danger: b.key === 'overdue' && stat.overdueCount > 0 }"
          @click="switchBucket(b.key)">
          <div class="ms-num">{{ statOf(b.key) }}</div>
          <div class="ms-label">{{ $t(b.label) }}</div>
          <div class="ms-hint">{{ $t(b.hint) }}</div>
        </div>
      </div>

      <el-card shadow="never" class="mt12">
        <template #header>
          <div class="card-head">
            <b>{{ $t(currentBucket.label) }}</b>
            <span class="head-right">
              <el-select v-model="owner" size="small" clearable :placeholder="$t('mk.allOwners')"
                class="q-owner" @change="load">
                <el-option v-for="u in owners" :key="u" :label="u" :value="u" />
              </el-select>
              <el-button size="small" :loading="loading" @click="load">{{ $t('common.refresh') }}</el-button>
            </span>
          </div>
        </template>

        <p class="bucket-why">{{ $t(currentBucket.why) }}</p>

        <el-table v-loading="loading" :data="rows" size="small" row-key="id">
          <el-table-column :label="$t('inquiry.col.customer')" min-width="180">
            <template #default="{ row }">
              <div>{{ row.name }}<span v-if="row.company" class="dim"> · {{ row.company }}</span></div>
              <div class="dim small">{{ row.email }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inquiry.col.product')" prop="productTitle" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.productTitle">{{ row.productTitle }}</span>
              <span v-else class="dim">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="88">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inquiry.followup.owner')" width="110">
            <template #default="{ row }">
              <span v-if="row.owner">{{ row.owner }}</span>
              <el-tag v-else size="small" type="danger" effect="plain">{{ $t('mk.bucket.unowned') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inquiry.followup.next')" width="110">
            <template #default="{ row }">
              <span v-if="row.nextFollow" :class="{ 'is-late': isPast(row.nextFollow) }">
                {{ fmtDate(row.nextFollow) }}
              </span>
              <span v-else class="dim">{{ $t('mk.noDate') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('mk.idle')" width="100" sortable
            :sort-method="(a, b) => (a.idleDays || 0) - (b.idleDays || 0)">
            <template #default="{ row }">
              <!--
                ⚠ 这一列是这一页的核心。「躺了多久」= 距离最后一次被碰过多少天，
                  而「最后被碰」是 最后一条跟进 → 处理时间 → 收到时间 三级回退。
                  没有第三级的话，一条刚进来、谁都没碰过的询盘会没有天数——
                  它会从「最该被看见的那条」变成排序垫底。
              -->
              <span :class="idleClass(row.idleDays)">{{ $t('mk.days', { n: row.idleDays }) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="" width="80" align="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goInquiry(row)">{{ $t('overview.stat.goFollow') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <p v-if="!loading && !rows.length" class="empty">
          {{ $t(currentBucket.empty) }}
        </p>

        <p v-if="rows.length >= limit" class="limit-note">
          {{ $t('mk.limitNote', { n: limit }) }}
        </p>
      </el-card>
    </template>
  </div>
</template>

<script setup name="Marketing">
/**
 * 营销管理 = 询盘跟进看板。
 *
 * <h3>为什么「营销管理」做成了这个</h3>
 * §11 里它只有一个名字。落到这个站上，营销最值钱的一块不是优惠券也不是群发邮件——
 * 是「询盘来了没人回」。理由写在 SiteFollowupService 的类注释里，
 * 页面顶部那条 el-alert 也对运营说了一遍。
 *
 * <h3>⚠ 这一页不重复造已经有的东西</h3>
 * 跟进状态（待处理/已联系/已成交/无效）、内部备注、客户标签，库里本来就有。
 * 这一轮加的只有三样：跟进记录（多条、只增不改）、下次跟进日期、负责人。
 *
 * <h3>⚠ 数字和清单必须同一套口径</h3>
 * 后端两条 SQL 共用一段 where 就是为了这个。出现「顶上说 3 条、点进去 5 条」时，
 * 别在前端补差——那是后端两处口径分叉了，去 SiteFollowupMapper.xml 看 boardBase。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getFollowupBoard } from '@/api/console'
import { identity, hasPermi } from '@/store/identity'
import { t } from '@/i18n'

const router = useRouter()

const canList = computed(() => identity.loaded && hasPermi(['site:inquiry:list']))

/*
  ⚠ label / hint / why / empty 都是**文案键**，不是文字：模板里统一 $t(...) 展开。
    这个数组是模块级常量，写死文字的话切语言时四张卡片不会跟着变。
    key 是发给后端的桶名，一个字都不能动。
*/
const BUCKETS = [
  {
    key: 'overdue', label: 'mk.bucket.overdue', hint: 'mk.bucket.overdueHint',
    why: 'mk.bucket.overdueWhy',
    empty: 'mk.bucket.overdueEmpty'
  },
  {
    key: 'today', label: 'mk.bucket.today', hint: 'mk.bucket.todayHint',
    why: 'mk.bucket.todayWhy',
    empty: 'mk.bucket.todayEmpty'
  },
  {
    key: 'unowned', label: 'mk.bucket.unowned', hint: 'mk.bucket.unownedHint',
    why: 'mk.bucket.unownedWhy',
    empty: 'mk.bucket.unownedEmpty'
  },
  {
    key: 'all', label: 'mk.bucket.all', hint: 'mk.bucket.allHint',
    why: 'mk.bucket.allWhy',
    empty: 'mk.bucket.allEmpty'
  }
]

const bucket = ref('overdue')
const owner = ref(undefined)
const loading = ref(false)
const rows = ref([])
const stat = reactive({ openCount: 0, overdueCount: 0, todayCount: 0, unownedCount: 0 })
const limit = ref(300)

const currentBucket = computed(() => BUCKETS.find(b => b.key === bucket.value) || BUCKETS[0])

/** 负责人下拉：从当前清单里现取，不单独发请求要用户列表 */
const owners = computed(() => {
  const set = new Set()
  rows.value.forEach(r => { if (r.owner) set.add(r.owner) })
  return [...set].sort()
})

function statOf(key) {
  return { overdue: stat.overdueCount, today: stat.todayCount,
           unowned: stat.unownedCount, all: stat.openCount }[key] ?? 0
}

function switchBucket(key) {
  if (bucket.value === key) return
  bucket.value = key
  load()
}

function load() {
  if (!canList.value) return
  loading.value = true
  getFollowupBoard({ bucket: bucket.value, owner: owner.value })
    .then(res => {
      const d = res.data || {}
      rows.value = d.rows || []
      Object.assign(stat, d.stat || {})
      limit.value = d.limit || 300
    })
    // ⚠ 空 catch：request.js 的拦截器已经对每种失败弹过一次了，这里再弹就是第二个框
    .catch(() => {})
    .finally(() => { loading.value = false })
}

/** 去跟进 = 打开询盘管理并定位到那一条。⚠ 依赖 Inquiry.vue 认地址栏的 id 参数 */
function goInquiry(row) {
  router.push({ path: '/inquiry', query: { id: row.id } })
}

const statusType = s => ({ 待处理: 'danger', 已联系: 'warning', 已成交: 'success', 无效: 'info' }[s] || 'info')

/** 躺得越久越红。3 天是后端 STALE_DAYS，7 天以上就是真的忘了 */
function idleClass(days) {
  const d = Number(days || 0)
  if (d >= 7) return 'idle-bad'
  if (d >= 3) return 'idle-warn'
  return ''
}

/*
 * 日期。后端给的是 `yyyy-MM-dd`（只到天，见 SiteInquiry.nextFollow 的注释）。
 *
 * ⚠ 比较用字符串，不 new Date()：`yyyy-MM-dd` 按字典序比就是按时间序比，
 *   而 new Date('2026-09-09') 会被当成 UTC 零点，在东八区拿回来是前一天——
 *   那会让「今天要跟」的那一条显示成已逾期。
 */
function todayStr() {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function isPast(s) {
  return typeof s === 'string' && s.slice(0, 10) < todayStr()
}
function fmtDate(s) {
  return typeof s === 'string' ? s.slice(0, 10) : s
}

onMounted(load)
</script>

<style scoped>
.mk-page { padding: 16px 20px 32px; }
.mb12 { margin-bottom: 12px; }
.mt12 { margin-top: 12px; }
.tip-body { font-size: 12px; line-height: 20px; }
.tip-body code,
.bucket-why code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.mk-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}
.mk-stat {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s;
}
.mk-stat:hover { border-color: var(--el-color-primary-light-5); }
.mk-stat.on {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}
/* 逾期不为 0 时红一下——这一栏为 0 才是正常状态 */
.mk-stat.danger .ms-num { color: var(--el-color-danger); }
.ms-num { font-size: 26px; font-weight: 600; line-height: 1.2; }
.ms-label { font-size: 13px; margin-top: 4px; }
.ms-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.head-right { display: inline-flex; align-items: center; gap: 8px; }
.q-owner { width: 150px; }

.bucket-why {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.dim { color: var(--el-text-color-secondary); }
.small { font-size: 12px; }
.is-late { color: var(--el-color-danger); }
.idle-warn { color: var(--el-color-warning); font-weight: 500; }
.idle-bad { color: var(--el-color-danger); font-weight: 600; }

.empty {
  padding: 28px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.limit-note {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>

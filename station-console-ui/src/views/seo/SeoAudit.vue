<template>
  <el-card shadow="never" class="mb16">
    <template #header>
      <div class="card-head">
        <b>{{ $t('seo.audit.title') }}</b>
        <span class="head-right">
          <span v-if="summary" class="summary">
            {{ $t('seo.audit.scanned', { n: summary.goodsCount, langs: (summary.langs || []).length }) }}
          </span>
          <el-button link type="primary" size="small" :loading="loading" @click="load">
            {{ $t('seo.audit.rerun') }}
          </el-button>
        </span>
      </div>
    </template>

    <div v-loading="loading">
      <!-- 计分板 -->
      <div v-if="summary" class="levels">
        <el-tag :type="summary.high ? 'danger' : 'success'" effect="dark" size="large">
          {{ $t('seo.audit.high') }} {{ summary.high }}
        </el-tag>
        <el-tag :type="summary.medium ? 'warning' : 'info'" size="large">{{ $t('seo.audit.medium') }} {{ summary.medium }}</el-tag>
        <el-tag type="info" size="large">{{ $t('seo.audit.low') }} {{ summary.low }}</el-tag>
        <!--
          跳过的坏数据要显示出来。正常恒为 0——不为 0 说明库里有商品编码是空的那种行，
          那是另一个问题，但不该让人从「体检没报什么」里猜出来。
        -->
        <el-tag v-if="summary.skipped" type="danger" size="large">
          {{ $t('seo.audit.skipped', { n: summary.skipped }) }}
        </el-tag>
      </div>

      <el-alert v-if="summary && !issues.length" type="success" :closable="false" class="mt12"
        :title="$t('seo.audit.clean')" :description="$t('seo.audit.cleanDesc')" />

      <el-table v-if="issues.length" :data="issues" size="small" class="mt12" row-key="rowKey">
        <el-table-column :label="$t('seo.audit.level')" width="70">
          <template #default="{ row }">
            <!--
              ⚠ row.level 是后端算出来的中文值（'高'/'中'/'低'），判色和判 effect
                仍然按原值，只把显示换成译名——两边都改的话英文界面下全是灰的。
            -->
            <el-tag :type="levelType(row.level)" size="small"
              :effect="row.level === '高' ? 'dark' : 'light'">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('seo.audit.target')" width="150">
          <template #default="{ row }">
            <div>{{ serverText(row.targetName) }}</div>
            <div v-if="row.targetCode" class="code">{{ row.targetCode }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('seo.audit.lang')" width="70">
          <template #default="{ row }">
            <span v-if="row.lang">{{ row.lang }}</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>
        <!--
          ⚠ message / targetName 是**后端生成的中文句子**（SeoAuditService），
            按句式在 i18n/server.js 里换成界面语言；后端新加了一种问题而表里还没有的话，显示中文原话。
        -->
        <el-table-column :label="$t('seo.audit.issue')" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">{{ serverText(row.message) }}</template>
        </el-table-column>
        <el-table-column label="" width="80" align="right">
          <template #default="{ row }">
            <!--
              「去修」要真的能修。三种对象分别去不同的地方：
                · 商品 → 商品管理，带上编码和语言，直接打开那个商品的编辑框
                · 首页 → 首页装修
                · 站点 → 站点概览（站点设置在那一页上）

              ⚠ 商品那条依赖 Goods.vue 认地址栏参数（?code=&lang=）。
                那是这一轮对现有文件的一处改动——不加的话点过去只是打开一个列表，
                体检就变成一张只会抱怨、给不出路径的清单。
            -->
            <el-button link type="primary" size="small" @click="goFix(row)">{{ $t('seo.audit.fix') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!--
        ⚠ 这一块必须显示，不是装饰。

        一份体检报告最危险的地方不是漏报，是让人以为「没报就是没问题」。
        canonical 指向的域名后端确实查不到（那是 station-site 那个 Node 进程的
        环境变量 NUXT_PUBLIC_SITE_URL），所以要明说，并给出自己验的命令。
      -->
      <div v-if="notChecked.length" class="notchecked">
        <div class="notchecked-head">{{ $t('seo.audit.notChecked') }}</div>
        <div v-for="(item, i) in notChecked" :key="i" class="notchecked-item">
          <div class="nc-item">{{ serverText(item.item) }}</div>
          <div class="nc-why">{{ serverText(item.why) }}</div>
          <div v-if="item.how" class="nc-how">
            <code>{{ serverText(item.how) }}</code>
            <el-button link type="primary" size="small" @click="copy(serverText(item.how))">{{ $t('common.copy') }}</el-button>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup name="SeoAudit">
/**
 * SEO 体检。
 *
 * 结果是**现算的**，后端不落库——落库就会出现「已经改好了但还在报警」，
 * 而一份需要人手动刷新才准的检查清单比没有清单更糟，因为它会被信。
 * 所以这一页有「重新体检」按钮，没有「上次体检时间」。
 *
 * 检查项和判定口径全在后端（SeoAuditService），这里只负责显示和「去修」的跳转。
 * ⚠ 别在这里加前端的判定规则：两处各判一份，早晚会给出互相矛盾的结论。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSeoAudit } from '@/api/console'
import { t } from '@/i18n'
import { translateServerText as serverText } from '@/i18n/server'

const router = useRouter()

const loading = ref(false)
const summary = ref(null)

const issues = computed(() => {
  const list = summary.value?.issues || []
  // row-key 给 el-table 用。后端没有给每条问题一个 id（它们是现算出来的，没有身份），
  // 所以在这里拼一个稳定的键，免得表格在重新体检后复用错行
  return list.map((it, i) => ({ ...it, rowKey: `${it.rule}-${it.targetCode || ''}-${it.lang || ''}-${i}` }))
})

const notChecked = computed(() => summary.value?.notChecked || [])

const levelType = level => ({ 高: 'danger', 中: 'warning', 低: 'info' }[level] || 'info')

/* 级别的译名。⚠ 值是后端给的中文，只翻显示 */
const LEVEL_KEY = { 高: 'seo.audit.high', 中: 'seo.audit.medium', 低: 'seo.audit.low' }
const levelLabel = level => (LEVEL_KEY[level] ? t(LEVEL_KEY[level]) : level)

function load() {
  loading.value = true
  getSeoAudit()
    .then(res => { summary.value = res.data || null })
    // ⚠ 空 catch，不在这里弹提示：request.js 的拦截器已经对每一种失败弹过一次了
    //   （业务码非 200、403、网络错误都弹），这里再弹就是第二个框。
    .catch(() => {})
    .finally(() => { loading.value = false })
}

function goFix(row) {
  if (row.target === '商品' && row.targetCode) {
    router.push({ path: '/goods', query: { code: row.targetCode, lang: row.lang || undefined } })
  } else if (row.target === '首页') {
    router.push({ path: '/decorate' })
  } else {
    router.push({ path: '/overview' })
  }
}

/**
 * 复制那条 curl 命令。
 *
 * ⚠ navigator.clipboard 只在 HTTPS（或 localhost）下有；管理台线上是 HTTPS，
 *   但本机用 http://127.0.0.1 以外的地址开发时就没有。所以要兜一下，
 *   兜不住时至少告诉人「自己选中复制」，而不是静默什么都不发生。
 */
function copy(text) {
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text)
      .then(() => ElMessage.success(t('common.copied')))
      .catch(() => ElMessage.warning(t('files.msg.copyFailed')))
  } else {
    ElMessage.warning(t('files.msg.copyUnsupported'))
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head-right {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.summary {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.levels {
  display: flex;
  gap: 8px;
  align-items: center;
}
.mb16 { margin-bottom: 16px; }
.mt12 { margin-top: 12px; }
.code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
.dim { color: var(--el-text-color-disabled); }

.notchecked {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-warning);
}
.notchecked-head {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.notchecked-item + .notchecked-item {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.nc-item { font-size: 13px; }
.nc-why {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.nc-how {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.nc-how code {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
</style>

<template>
  <el-card shadow="never" class="mb16">
    <template #header>
      <div class="card-head">
        <b>{{ $t('seo.idx.title') }}</b>
        <span>
          <el-tooltip v-if="!canEdit" placement="top"
            :content="$t('seo.idx.noEditPermi')">
            <el-button link type="primary" size="small" disabled>{{ $t('common.save') }}</el-button>
          </el-tooltip>
          <el-button v-else link type="primary" size="small" :loading="saving" @click="save">
            {{ $t('common.save') }}
          </el-button>
        </span>
      </div>
    </template>

    <el-form v-loading="loading" label-width="150px" class="seo-form">
      <!--
        ⚠ 整站禁收录放在最上面并且用 el-alert 圈出来，不是为了好看。

        这是个后果重、而且**没有任何前台症状**的开关：站照常显示、页面照常打开，
        只有几周之后自然流量掉了才发现。所以它要被看见，并且要有二次确认。
      -->
      <el-form-item :label="$t('seo.idx.noindexAll')">
        <el-switch v-model="noindexAll" :disabled="!canEdit" @change="onNoindexChange" />
        <div class="hint" v-html="$t('seo.idx.noindexHint')"></div>
        <el-alert v-if="noindexAll" type="warning" :closable="false" class="mt8"
          :title="$t('seo.idx.noindexOnTitle')"
          :description="$t('seo.idx.noindexOnDesc')" />
      </el-form-item>

      <el-divider />

      <el-form-item :label="$t('seo.idx.disallow')">
        <el-input v-model="form.disallowPaths" :disabled="!canEdit" type="textarea" :rows="2"
          placeholder="/promo-draft,/tmp" />
        <div class="hint" v-html="$t('seo.idx.disallowHint')"></div>
      </el-form-item>

      <el-form-item :label="$t('seo.idx.robotsExtra')">
        <el-input v-model="form.robotsExtra" :disabled="!canEdit" type="textarea" :rows="3"
          placeholder="Crawl-delay: 1" />
        <div class="hint" v-html="$t('seo.idx.robotsExtraHint')"></div>
      </el-form-item>

      <el-divider />

      <el-form-item :label="$t('seo.idx.sitemap')">
        <el-switch v-model="sitemapOn" :disabled="!canEdit" />
        <div v-if="noindexAll" class="hint warn">
          {{ $t('seo.idx.sitemapOffHint') }}
        </div>
      </el-form-item>

      <el-form-item :label="$t('seo.idx.sitemapMax')">
        <el-input-number v-model="form.sitemapMax" :disabled="!canEdit" :min="1" :max="6000"
          :step="500" controls-position="right" />
        <div class="hint" v-html="$t('seo.idx.sitemapMaxHint')"></div>
      </el-form-item>

      <el-form-item :label="$t('seo.idx.homeFreq')">
        <el-select v-model="form.homeFreq" :disabled="!canEdit" style="width: 160px">
          <el-option v-for="f in FREQS" :key="f" :label="f" :value="f" />
        </el-select>
      </el-form-item>

      <el-form-item :label="$t('seo.idx.goodsFreq')">
        <el-select v-model="form.goodsFreq" :disabled="!canEdit" style="width: 160px">
          <el-option v-for="f in FREQS" :key="f" :label="f" :value="f" />
        </el-select>
        <div class="hint" v-html="$t('seo.idx.goodsFreqHint')"></div>
      </el-form-item>

      <el-divider />

      <!--
        ⚠ 预览是两个链接，不是页面内嵌的内容。

        内嵌要么让后端去 GET 127.0.0.1:3000/robots.txt（那是从 nginx 后面看，
        和公网访客看到的可能不一样，还得给后端新加一个「前台地址」配置），
        要么浏览器直接取（跨域：管理台在 console. 子域，取不到主域）。

        两个链接看到的是**真的那一份**，一次点击，零行代码。
      -->
      <el-form-item :label="$t('seo.idx.preview')">
        <template v-if="frontUrl">
          <el-link type="primary" :href="frontUrl + '/robots.txt'" target="_blank"
            rel="noopener" :underline="false">robots.txt</el-link>
          <el-divider direction="vertical" />
          <el-link type="primary" :href="frontUrl + '/sitemap.xml'" target="_blank"
            rel="noopener" :underline="false">sitemap.xml</el-link>
          <div class="hint" v-html="$t('seo.idx.cacheHint')"></div>
        </template>
        <span v-else class="hint">
          {{ $t('seo.idx.noDomain') }}
        </span>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup name="SeoIndexing">
/**
 * 收录规则与 sitemap。改的是 u_s_seo 那一行的前半截。
 *
 * ⚠ 保存时送的是**整份配置**（含验证码和统计 ID 那半截），因为后端一个字段都不走
 *   coalesce（见 SiteSeoMapper.xml 里那段注释）。所以 config 是父组件持有、
 *   两张卡共用的同一个对象——这样就不存在「只送半份」的写法。
 */
import { ref, computed, watch, inject } from 'vue'
import { ElMessageBox } from 'element-plus'
import { saveSeo } from '@/api/console'
import { toFrontUrl } from '@/utils/siteUrl'
import { t } from '@/i18n'

const props = defineProps({
  config: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  canEdit: { type: Boolean, default: false }
})
const emit = defineEmits(['update:config', 'saved'])

const FREQS = ['always', 'hourly', 'daily', 'weekly', 'monthly', 'yearly', 'never']

const site = inject('stationSite', ref({}))
const frontUrl = computed(() => toFrontUrl(site.value?.domain))

const saving = ref(false)

/*
 * 本地表单。
 *
 * ⚠ 不直接改 props.config：那是父组件的对象，两张卡共用。直接改会让
 *   「另一张卡还没保存的编辑」被这张卡的保存一起送出去——用户没按那张卡的保存，
 *   值却生效了。所以各自持一份副本，保存时才合并。
 */
const form = ref({})

watch(() => props.config, cfg => {
  form.value = { ...(cfg || {}) }
}, { immediate: true, deep: false })

// tinyint 0/1 ↔ 布尔。后端收的是 0/1（那一列是 tinyint），el-switch 要布尔
const noindexAll = computed({
  get: () => form.value.noindexAll === 1,
  set: v => { form.value.noindexAll = v ? 1 : 0 }
})
const sitemapOn = computed({
  get: () => form.value.sitemapOn === 1,
  set: v => { form.value.sitemapOn = v ? 1 : 0 }
})

/**
 * 打开「整站禁止收录」时二次确认。
 *
 * ⚠ 按破坏性操作对待。它的后果重（已收录的页面会掉出搜索结果），
 *   而且**没有任何前台症状**——页面照常显示，只有几周后流量掉了才发现。
 *   一个没有反馈的开关必须在按下之前把后果说清楚。
 *
 *   关掉不确认：那是往安全的一边走。
 */
function onNoindexChange(value) {
  if (!value) return
  ElMessageBox.confirm(
    t('seo.idx.confirmBody'),
    t('seo.idx.confirmTitle'),
    { type: 'warning', confirmButtonText: t('seo.idx.confirmOk'), cancelButtonText: t('common.cancel') }
  ).catch(() => {
    // 取消就拨回来。⚠ el-switch 已经视觉上切过去了，不拨回去的话
    //   界面显示「开」、而用户明确点了取消
    noindexAll.value = false
  })
}

function save() {
  saving.value = true
  // ⚠ 以父组件那份为底、盖上本卡的编辑：这样另一张卡里**已保存过**的值不会被回退，
  //   而本卡的编辑会生效。整份送是后端要求的（没有 coalesce）。
  const payload = { ...(props.config || {}), ...form.value }
  saveSeo(payload)
    .then(res => {
      emit('update:config', res.data || payload)
      emit('saved', res.data)
    })
    // ⚠ 空 catch，不在这里弹提示。后端的校验消息（ServiceException 里那几句
    //   写给人看的话）已经由 request.js 的拦截器弹过了——它对业务码非 200 的响应
    //   一律 ElMessage.error(data.msg)。这里再弹一次就是两个一样的框。
    .catch(() => {})
    .finally(() => { saving.value = false })
}
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mb16 { margin-bottom: 16px; }
.mt8 { margin-top: 8px; }
.seo-form {
  max-width: 780px;
}
.hint {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.hint.warn { color: var(--el-color-warning); }
.hint code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
</style>

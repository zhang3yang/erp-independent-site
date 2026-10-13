<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <b>{{ $t('seo.trk.title') }}</b>
        <span>
          <el-tooltip v-if="!canEdit" placement="top"
            :content="$t('seo.trk.noEditPermi')">
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
        ⚠ 这条提示放在最前面，而且不可关闭。

        「只填 ID、不粘代码」是这一整块的安全前提，不是填写习惯。
        看不到这句话的人第一反应一定是把 Google 给的那段 <script> 整段粘进来，
        然后得到一个「格式不对」的报错却不知道为什么。
      -->
      <el-alert type="info" :closable="false" class="mb16"
        :title="$t('seo.trk.idOnly')">
        <template #default>
          <div class="alert-body" v-html="$t('seo.trk.idOnlyBody')"></div>
        </template>
      </el-alert>

      <el-form-item :label="$t('seo.trk.google')">
        <el-input v-model="form.googleVerify" :disabled="!canEdit"
          :placeholder="$t('seo.trk.googlePlaceholder')" />
        <div class="hint" v-html="$t('seo.trk.googleHint')"></div>
      </el-form-item>

      <el-form-item :label="$t('seo.trk.bing')">
        <el-input v-model="form.bingVerify" :disabled="!canEdit" :placeholder="$t('seo.trk.bingPlaceholder')" />
      </el-form-item>

      <el-form-item :label="$t('seo.trk.baidu')">
        <el-input v-model="form.baiduVerify" :disabled="!canEdit"
          :placeholder="$t('seo.trk.baiduPlaceholder')" />
      </el-form-item>

      <el-divider />

      <el-form-item :label="$t('seo.trk.ga4')">
        <el-input v-model="form.ga4Id" :disabled="!canEdit" placeholder="G-XXXXXXXXXX" />
        <div class="hint" v-html="$t('seo.trk.ga4Hint')"></div>
      </el-form-item>

      <el-form-item :label="$t('seo.trk.gtm')">
        <el-input v-model="form.gtmId" :disabled="!canEdit" placeholder="GTM-XXXXXXX" />
        <!--
          ⚠ 两个都填时当场提示，不要等保存后才说，也不要静默丢掉一个。
            静默丢掉会让人对着一个「配了但数字不对」的界面查很久。
        -->
        <div v-if="bothTags" class="hint warn" v-html="$t('seo.trk.bothTags')"></div>
      </el-form-item>

      <el-form-item :label="$t('seo.trk.baiduHm')">
        <el-input v-model="form.baiduHmId" :disabled="!canEdit"
          :placeholder="$t('seo.trk.baiduHmPlaceholder')" />
        <div class="hint" v-html="$t('seo.trk.baiduHmHint')"></div>
      </el-form-item>

      <!--
        ⚠⚠ 这一条必须在界面上，不能只写在设计文档和代码注释里。

        决定「要不要开 GA」的是坐在这个页面前面的人，合规风险要在他按下开关的地方
        告诉他。写在 spec 里等于没写——他不会去看 spec。
      -->
      <el-alert v-if="anyAnalytics" type="warning" :closable="false" class="mt8"
        :title="$t('seo.trk.gdprTitle')">
        <template #default>
          <div class="alert-body" v-html="$t('seo.trk.gdprBody')"></div>
        </template>
      </el-alert>
    </el-form>
  </el-card>
</template>

<script setup name="SeoTracking">
/**
 * 站点验证码与统计代码。改的是 u_s_seo 那一行的后半截。
 *
 * <h3>⚠ 只存 ID</h3>
 * 库里存的是 G-XXXXXXXXXX / GTM-XXXXXXX / 32 位十六进制这样的**标识**，
 * <script> 标签由 station-site 的 server/plugins/seo-head.ts 自己拼。
 *
 * 格式校验在三处：这里（提示）、后端保存时（拒绝）、前台输出时（不注入）。
 * ⚠ **真正的防线是第三处**——库里的值可能是别人用 SQL 直接改的，
 *   而输出端正是把它拼进 <script> 的地方。前两处只是让人早点知道填错了。
 *
 * 所以这个组件里**不做**正则校验：做了只会和后端那份产生分歧，而它挡不住任何东西。
 * 填错了后端会回一句写给人看的消息，原样弹出来。
 */
import { ref, computed, watch } from 'vue'
import { saveSeo } from '@/api/console'
import { t } from '@/i18n'

const props = defineProps({
  config: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  canEdit: { type: Boolean, default: false }
})
const emit = defineEmits(['update:config', 'saved'])

const saving = ref(false)
const form = ref({})

// 见 SeoIndexing.vue 里同一段注释：各自持一份副本，免得一张卡的保存
// 把另一张卡里还没提交的编辑一起送出去
watch(() => props.config, cfg => {
  form.value = { ...(cfg || {}) }
}, { immediate: true, deep: false })

const bothTags = computed(() => !!(trim(form.value.ga4Id) && trim(form.value.gtmId)))

const anyAnalytics = computed(() =>
  !!(trim(form.value.ga4Id) || trim(form.value.gtmId) || trim(form.value.baiduHmId)))

function trim(v) {
  return typeof v === 'string' ? v.trim() : (v || '')
}

function save() {
  saving.value = true
  const payload = { ...(props.config || {}), ...form.value }
  saveSeo(payload)
    .then(res => {
      emit('update:config', res.data || payload)
      emit('saved', res.data)
    })
    // ⚠ 空 catch：后端那几句校验消息已经由 request.js 的拦截器弹过了，见 SeoIndexing.vue
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
.alert-body {
  font-size: 12px;
  line-height: 20px;
}
.hint {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.hint.warn { color: var(--el-color-warning); }
.hint code,
.alert-body code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
</style>

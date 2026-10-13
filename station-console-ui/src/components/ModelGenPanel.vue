<!--
  「文件 → 模型」里的「用照片生成」：提交弹窗 + 生成任务列表。腾讯混元生 3D（2026-09-26）。
  设计：docs/superpowers/specs/2026-09-26-hunyuan-3d-generate-design.md §7

  生成本身在后端：提交后后端每 15 秒去查进度，完成后自动把 glb 转存进模型库。
  这里只做两件事——收集照片提交、把任务表显示出来（有没跑完的就每 10 秒刷新一次）。
  关掉页面不影响生成。

  宿主（Files.vue）用 ref 调 open() 打开弹窗；任务完成时发 generated，宿主刷新模型列表。
-->
<template>
  <div v-if="jobs.length" class="gen-panel">
    <div class="gen-head">
      <span class="gen-title">{{ $t('files.gen.tasks') }}</span>
      <span class="gen-quota">{{ $t('files.gen.quota', { used: usedToday, limit: dailyLimit }) }}</span>
    </div>
    <div v-for="job in jobs" :key="job.jobId" class="gen-row">
      <img v-if="job.previewUrl && !brokenPreview[job.jobId]" :src="job.previewUrl" class="gen-thumb" alt=""
        @error="brokenPreview[job.jobId] = true" />
      <div v-else class="gen-thumb gen-thumb--empty"><el-icon><Box /></el-icon></div>
      <div class="gen-info">
        <div class="gen-name" :title="job.jobName">{{ job.jobName }}</div>
        <div class="gen-meta">
          <el-tag size="small" :type="statusType(job.status)">{{ $t('files.gen.status.' + job.status) }}</el-tag>
          <span>{{ job.createtime }}</span>
          <span v-if="job.status === 'DONE' && job.finishtime">· {{ elapsed(job) }}</span>
        </div>
        <div v-if="job.status === 'FAIL'" class="gen-error">{{ job.errorMsg }}</div>
        <div v-else-if="job.status === 'DONE'" class="gen-hint">{{ $t('files.gen.doneHint') }}</div>
        <div v-else class="gen-hint">{{ $t('files.gen.runningHint') }}</div>
      </div>
      <el-button v-hasPermi="['site:file:remove']" link type="danger" size="small"
        @click="remove(job)">{{ $t('files.gen.removeTask') }}</el-button>
    </div>
  </div>

  <el-dialog v-model="dialogOpen" :title="$t('files.gen.title')" width="640px" append-to-body
    :close-on-click-modal="false">
    <el-alert type="info" :closable="false" show-icon class="gen-tip" :title="$t('files.gen.tip')" />

    <div class="gen-views">
      <div v-for="v in VIEWS" :key="v" class="gen-view">
        <div class="gen-view-label">
          {{ $t('files.gen.view.' + v) }}<span v-if="v === 'front'" class="gen-required">*</span>
        </div>
        <div class="gen-view-box" @click="pick(v)">
          <img v-if="form[v]" :src="form[v]" alt="" />
          <div v-else class="gen-view-empty"><el-icon><Plus /></el-icon></div>
        </div>
        <el-button v-if="form[v]" link type="danger" size="small" @click="form[v] = ''">{{ $t('files.gen.clear') }}</el-button>
      </div>
    </div>

    <el-form label-width="96px" class="gen-form">
      <el-form-item :label="$t('files.gen.name')">
        <el-input v-model="form.name" maxlength="100" :placeholder="$t('files.gen.namePlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('files.gen.quality')">
        <el-radio-group v-model="form.quality">
          <el-radio value="standard">{{ $t('files.gen.qualityStandard') }}</el-radio>
          <el-radio value="fine">{{ $t('files.gen.qualityFine') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="$t('files.gen.pbr')">
        <el-switch v-model="form.pbr" />
        <span class="gen-pbr-hint">{{ $t('files.gen.pbrHint') }}</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <span class="gen-quota">{{ $t('files.gen.quota', { used: usedToday, limit: dailyLimit }) }}</span>
      <el-button @click="dialogOpen = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!form.front" @click="submit">
        {{ $t('files.gen.submit') }}
      </el-button>
    </template>
  </el-dialog>

  <media-picker v-model="pickerOpen" type="image" :limit="1"
    :selected="pickingView && form[pickingView] ? [form[pickingView]] : []" @confirm="onPicked" />
</template>

<script setup name="ModelGenPanel">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box, Plus } from '@element-plus/icons-vue'
import MediaPicker from '@station-shared/MediaPicker.vue'
import { submitModelGen, listModelGen, delModelGen } from '@/api/console'
import { t } from '@/i18n'

const props = defineProps({
  /** 生成的模型放进哪个文件夹。'' 和 'ROOT' 都是「不指定」 */
  folderId: { type: String, default: '' }
})
const emit = defineEmits(['generated'])

const VIEWS = ['front', 'left', 'right', 'back']
const REFRESH_MS = 10_000

const jobs = ref([])
const enabled = ref(true)
const usedToday = ref(0)
const dailyLimit = ref(0)
const brokenPreview = reactive({})

const dialogOpen = ref(false)
const submitting = ref(false)
const form = reactive({ front: '', left: '', right: '', back: '', name: '', quality: 'standard', pbr: false })

const pickerOpen = ref(false)
const pickingView = ref('')

let timer = 0
/** 上一次看到的状态，用来发现「刚刚完成」的任务 */
const lastStatus = new Map()

async function load() {
  let data
  try {
    const response = await listModelGen({ pageNum: 1, pageSize: 10 })
    data = response.data || {}
  } catch {
    // 列表拉不到不打扰人：下次刷新再说
    return
  }
  const rows = data.rows || []
  let justDone = false
  for (const job of rows) {
    const before = lastStatus.get(job.jobId)
    if (before && before !== 'DONE' && job.status === 'DONE') justDone = true
    lastStatus.set(job.jobId, job.status)
  }
  jobs.value = rows
  enabled.value = data.enabled !== false
  usedToday.value = data.usedToday || 0
  dailyLimit.value = data.dailyLimit || 0
  if (justDone) {
    ElMessage.success(t('files.gen.msgDone'))
    emit('generated')
  }
  schedule(!!data.active)
}

function schedule(active) {
  clearTimeout(timer)
  timer = 0
  if (active) timer = setTimeout(load, REFRESH_MS)
}

function open() {
  if (!enabled.value) {
    ElMessage.warning(t('files.gen.disabled'))
    return
  }
  Object.assign(form, { front: '', left: '', right: '', back: '', name: '', quality: 'standard', pbr: false })
  dialogOpen.value = true
}

function pick(view) {
  pickingView.value = view
  pickerOpen.value = true
}

function onPicked(urls) {
  if (pickingView.value) form[pickingView.value] = urls?.[0] || ''
}

async function submit() {
  if (!form.front) return
  try {
    await ElMessageBox.confirm(t('files.gen.confirm'), t('files.gen.title'), { type: 'warning' })
  } catch {
    return
  }
  submitting.value = true
  try {
    const folder = props.folderId && props.folderId !== 'ROOT' ? props.folderId : null
    const response = await submitModelGen({
      front: form.front,
      left: form.left || null,
      right: form.right || null,
      back: form.back || null,
      name: form.name.trim() || null,
      quality: form.quality,
      pbr: form.pbr,
      folderId: folder
    })
    const job = response.data || {}
    if (job.status === 'FAIL') {
      // 提交阶段就失败（密钥 / 权限 / 积分）：任务照样进列表，原因写在那一行
      ElMessage.error(job.errorMsg || t('files.gen.msgFailed'))
    } else {
      ElMessage.success(t('files.gen.msgSubmitted'))
      dialogOpen.value = false
    }
    lastStatus.set(job.jobId, job.status)
    await load()
  } finally {
    submitting.value = false
  }
}

async function remove(job) {
  try {
    await ElMessageBox.confirm(t('files.gen.removeConfirm'), t('files.gen.removeTask'), { type: 'warning' })
  } catch {
    return
  }
  await delModelGen(job.jobId)
  lastStatus.delete(job.jobId)
  await load()
}

function statusType(status) {
  if (status === 'DONE') return 'success'
  if (status === 'FAIL') return 'danger'
  return 'warning'
}

function elapsed(job) {
  const start = Date.parse(String(job.createtime).replace(' ', 'T'))
  const end = Date.parse(String(job.finishtime).replace(' ', 'T'))
  if (!start || !end || end < start) return ''
  const s = Math.round((end - start) / 1000)
  return s < 60 ? t('files.gen.seconds', { n: s }) : t('files.gen.minutes', { n: Math.round(s / 60) })
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(timer))

defineExpose({ open, load })
</script>

<style scoped>
.gen-panel { margin-bottom: 14px; padding: 10px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.gen-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.gen-title { font-weight: 600; font-size: 14px; }
.gen-quota { font-size: 12px; color: var(--el-text-color-secondary); margin-right: 12px; }
.gen-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-top: 1px solid var(--el-border-color-extra-light); }
.gen-row:first-of-type { border-top: 0; }
.gen-thumb { width: 48px; height: 48px; object-fit: cover; border-radius: 4px; flex-shrink: 0; background: var(--el-fill-color-light); }
.gen-thumb--empty { display: flex; align-items: center; justify-content: center; color: var(--el-text-color-placeholder); font-size: 20px; }
.gen-info { flex: 1; min-width: 0; }
.gen-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gen-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.gen-error { font-size: 12px; color: var(--el-color-danger); margin-top: 2px; line-height: 1.5; }
.gen-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.gen-tip { margin-bottom: 14px; }
.gen-views { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.gen-view { text-align: center; }
.gen-view-label { font-size: 12px; color: var(--el-text-color-regular); margin-bottom: 6px; }
.gen-required { color: var(--el-color-danger); margin-left: 2px; }
.gen-view-box { aspect-ratio: 1; border: 1px dashed var(--el-border-color); border-radius: 6px; cursor: pointer; overflow: hidden; display: flex; align-items: center; justify-content: center; background: var(--el-fill-color-lighter); }
.gen-view-box:hover { border-color: var(--el-color-primary); }
.gen-view-box img { width: 100%; height: 100%; object-fit: contain; }
.gen-view-empty { font-size: 22px; color: var(--el-text-color-placeholder); }
.gen-form { margin-top: 4px; }
.gen-pbr-hint { margin-left: 10px; font-size: 12px; color: var(--el-text-color-secondary); }
</style>

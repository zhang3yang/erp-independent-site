<!--
  装修里的「一段视频」这一格：地址输入框 + 从文件库挑 + 首帧缩略图。
  和同目录的 ImageField 是一对，布局、留框的理由、缩略图常驻的理由都一样，
  见那个文件的头部注释。这里只记两处**不一样**的地方：

  · 缩略图是 <video preload="metadata" muted>，靠首帧当封面，不额外生成封面文件。
    不给 controls——这一格是「填哪一段视频」，不是播放器，放上播放条会让人
    在装修页里点着玩，而真正的播放效果要去右边的预览里看；
  · 多一行「时长/体积」的提醒。Banner 视频是**首屏**就要下载的东西，
    商家手上的素材常常是几十兆的原片，传上去首页会卡到打不开。
    这句话写在这里而不是只写在 Banner 那一块，是因为以后别处用到这一格时同样成立。
-->
<template>
  <div class="vidf">
    <el-input v-model="url" :placeholder="placeholder || st('field.videoUrlPh', '视频完整地址，或点右边从文件库选')" :disabled="disabled">
      <template #append>
        <el-button :disabled="disabled" @click="pickerOpen = true">{{ st('field.pickVideo', '选视频') }}</el-button>
      </template>
    </el-input>

    <!-- 和 ImageField 一样常驻：位置固定，一排字段扫下来才看得出哪几格配了东西 -->
    <div class="vidf-thumb" :class="{ bad: broken }">
      <video v-if="url && !broken" :src="url" preload="metadata" muted playsinline
        @error="broken = true"></video>
      <span v-else>{{ url ? st('field.broken', '打不开') : st('field.noVideo', '无视频') }}</span>
    </div>

    <media-picker v-model="pickerOpen" type="video" :limit="1"
      :selected="url ? [url] : []" @confirm="onPicked" />
  </div>
</template>

<script setup name="StationVideoField">
import { ref, computed, watch } from 'vue'
import MediaPicker from './MediaPicker.vue'
import { useStationT } from './useStationT'

/* 同 ImageField：宿主没注入翻译器时原样显示中文 */
const st = useStationT()

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 不传就用内置那句（跟着界面语言走），所以默认值留空 */
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const url = computed({
  get: () => props.modelValue || '',
  set: v => emit('update:modelValue', v)
})

const pickerOpen = ref(false)

/** 地址换了就重新给它一次机会：上一段打不开不代表这一段也打不开 */
const broken = ref(false)
watch(url, () => { broken.value = false })

function onPicked(list) {
  // limit=1，但确定时一段都没选也是可能的（把原来那段点掉了）——那就是「清空」
  emit('update:modelValue', list[0] || '')
}
</script>

<style scoped>
.vidf {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.vidf :deep(.el-input) { flex: 1; min-width: 0; }

.vidf-thumb {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 84px;
  height: 56px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.vidf-thumb video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.vidf-thumb.bad {
  border-color: var(--el-color-danger-light-5);
  color: var(--el-color-danger);
}
</style>

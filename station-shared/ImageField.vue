<!--
  装修里的「一张图」这一格：地址输入框 + 从文件库挑 + 缩略图。

  ## 为什么还留着手填地址那个框

  改版 demo 主张「不让手填 URL」，理由是商家得先去文件页复制地址再回来粘，
  粘错了要到前台才看得出来。前半句对，但整条不能照抄：

    · 新站初始化脚本（sql/station/03_init_decorate.sql）里的 Banner 图和关于我们配图
      指向 /files/deco/ 下的两张图，那是按部署 README 直接放到服务器磁盘上的，
      **不在文件资源库里**——去掉输入框，这两张图就没法填也没法改；
    · 商品图用的对象存储地址也是整串 URL，商家手上可能已经有了。

  真正要解决的是「粘错了看不出来」。所以留框，但是：
    · 「选图片」按钮就在框右边，正常路径一次都不用去文件页；
    · 右边永远有一张缩略图，地址错了当场就是一个「图裂了」，不用等到前台。
-->
<template>
  <div class="imgf">
    <el-input v-model="url" :placeholder="placeholder || st('field.imageUrlPh', '图片完整地址，或点右边从文件库选')" :disabled="disabled">
      <template #append>
        <el-button :disabled="disabled" @click="pickerOpen = true">{{ st('field.pickImage', '选图片') }}</el-button>
      </template>
    </el-input>

    <!-- 缩略图这一格是**常驻**的，没填时显示「无图」而不是整块消失：
         位置固定，一排字段扫下来才看得出哪几张配了图 -->
    <div class="imgf-thumb" :class="{ bad: broken }">
      <img v-if="url && !broken" :src="url" alt="" @error="broken = true">
      <span v-else>{{ url ? st('field.broken', '打不开') : st('field.noImage', '无图') }}</span>
    </div>

    <media-picker v-model="pickerOpen" type="image" :limit="1"
      :selected="url ? [url] : []" @confirm="onPicked" />
  </div>
</template>

<script setup name="StationImageField">
import { ref, computed, watch } from 'vue'
import MediaPicker from './MediaPicker.vue'
import { useStationT } from './useStationT'

/* 宿主没注入翻译器时，st() 原样返回第二个参数那句中文 */
const st = useStationT()

const props = defineProps({
  modelValue: { type: String, default: '' },
  /* ⚠ 默认值在 props 里，取不到宿主的翻译器时就是这句中文 */
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

/** 地址换了就重新给它一次机会：上一张打不开不代表这一张也打不开 */
const broken = ref(false)
watch(url, () => { broken.value = false })

function onPicked(list) {
  // limit=1，但确定时一张都没选也是可能的（把原来那张点掉了）——那就是「清空」
  emit('update:modelValue', list[0] || '')
}
</script>

<style scoped>
.imgf {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.imgf :deep(.el-input) { flex: 1; min-width: 0; }

.imgf-thumb {
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
.imgf-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.imgf-thumb.bad {
  border-color: var(--el-color-danger-light-5);
  color: var(--el-color-danger);
}
</style>

<!--
  买家头像。三种形态：传的图 / 内置色块 / 纯色底 + 首字母。

  用在顶栏（32px）、账户页左栏（52px）、Profile 面板（92px）。
  取值的含义见 app/composables/useAvatar.ts。
-->
<template>
  <span class="ua" :style="style">
    <img v-if="isImage" :src="src!" alt="" width="100" height="100">
    <template v-else>{{ initials }}</template>
  </span>
</template>

<script setup lang="ts">
import { presetById } from '~/composables/useAvatar'

const props = withDefaults(defineProps<{
  /** `data:` 开头的图、`preset:<id>`，或 null */
  src?: string | null
  initials?: string
  /** 直径，px */
  size?: number
}>(), { src: null, initials: '?', size: 40 })

const isImage = computed(() => !!props.src && props.src.startsWith('data:'))

const preset = computed(() =>
  props.src?.startsWith('preset:') ? presetById(props.src.slice(7)) : null)

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  // 字号跟着直径走，不然同一个字块在 32px 的顶栏和 92px 的面板上比例会差很多
  fontSize: `${Math.max(11, Math.round(props.size * 0.34))}px`,
  background: preset.value
    ? `linear-gradient(140deg, ${preset.value.from}, ${preset.value.to})`
    : undefined
}))
</script>

<style scoped>
.ua {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 50%;
  overflow: hidden;
  background: var(--primary, #0b72ab);
  color: #fff;
  font-weight: 700;
  letter-spacing: .03em;
  line-height: 1;
  user-select: none;
}
.ua img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>

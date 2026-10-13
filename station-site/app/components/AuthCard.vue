<!--
  买家账号那几页共用的壳：居中一张卡、标题、一条消息位、内容插槽。

  为什么单独抽一个组件：登录/注册/找回/重置/验证五页的骨架逐字节相同，
  只有标题和表单不同。抄五份的结果是改一次样式要改五处，而其中一处会被漏掉。

  ⚠ 这几页一律 noindex（调用方自己在 useHead 里写）：它们是每个访客各自的东西，
    进 Google 索引没有意义，还会分掉商品页的权重。
-->
<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1>{{ title }}</h1>
      <p v-if="lead" class="muted auth-lead">{{ lead }}</p>

      <!--
        消息位。成功和失败用同一个位置、不同颜色——
        分成两块的话，页面会在两种状态之间跳高度。
        ⚠ 文本插值，不用 v-html：这里显示的是后端返回的 msg，
          而后端那些 msg 里可能带用户填进去的内容。
      -->
      <p v-if="error" class="auth-msg is-error" role="alert">{{ error }}</p>
      <p v-else-if="notice" class="auth-msg is-ok" role="status">{{ notice }}</p>

      <slot />

      <div v-if="$slots.foot" class="auth-foot">
        <slot name="foot" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  lead?: string
  /** 出错了显示这句（红） */
  error?: string
  /** 成功/提示显示这句（绿）。error 优先 */
  notice?: string
}>()
</script>

<style scoped>
.auth-wrap {
  display: flex;
  justify-content: center;
  padding: 56px 20px 80px;
}
.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 32px;
  background: var(--surface, #fff);
  border: 1px solid var(--border, #e6e6ef);
  border-radius: var(--radius, 10px);
}
.auth-card h1 {
  margin: 0 0 8px;
  font-size: 1.6rem;
}
.auth-lead {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 22px;
}
.auth-msg {
  margin: 0 0 18px;
  padding: 10px 12px;
  border-radius: var(--radius, 10px);
  font-size: 13px;
  line-height: 20px;
}
.is-error {
  background: #fdecec;
  color: #a12020;
}
.is-ok {
  background: #eaf7ee;
  color: #1c6b34;
}
.auth-foot {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border, #e6e6ef);
  font-size: 13px;
  line-height: 22px;
  text-align: center;
  color: var(--text-muted, #6b6b7b);
}
.auth-foot :deep(a) {
  color: var(--primary, #0b72ab);
}
</style>

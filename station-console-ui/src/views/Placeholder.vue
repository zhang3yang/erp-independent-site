<template>
  <div class="placeholder">
    <el-result icon="info" :title="title" :sub-title="$t('placeholder.sub')">
      <template #extra>
        <div class="note">{{ note }}</div>
        <!--
          ⚠ 这不是「敬请期待」的客套话，是一条设计决定，值得摆在页面上。

          docs/独立站与ERP的边界.md §11 定义了「留位置」的确切含义：
          菜单项 + 路由 + 一个说明用途的空页面，**不建表、不写 model、不留 TODO 接口**。

          为什么不顺手建表：表结构定下来就会有人往上写代码，而这几个模块的需求
          现在只有名字。空表比没表更容易引出错的实现。
        -->
        <div class="why">{{ $t('placeholder.body') }}</div>
      </template>
    </el-result>
  </div>
</template>

<script setup name="Placeholder">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { t } from '@/i18n'

const route = useRoute()
/* meta.title 是文案键（见 router/index.js），这里翻出来当标题 */
const title = computed(() => (route.meta?.title ? t(route.meta.title) : t('placeholder.thisModule')))
const note = computed(() => route.meta?.note || '')
</script>

<style scoped>
.placeholder {
  padding: 40px 20px;
}
.note {
  max-width: 620px;
  margin: 0 auto 16px;
  font-size: 14px;
  line-height: 24px;
  color: var(--el-text-color-regular);
  text-align: left;
}
.why {
  max-width: 620px;
  margin: 0 auto;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  text-align: left;
}
</style>

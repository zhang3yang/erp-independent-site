<template>
  <!--
    整个应用包在 config-provider 里，只为一件事：Element Plus 自带的那些字
    （日期选择器的月份、分页的「条/页」、确认框的按钮）跟着界面语言一起变。

    ⚠ 不做这一步的后果不是「有几个字没翻」，而是界面是法语、日历是中文——
      比整屏中文更像坏了。
  -->
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup name="App">
// 壳子在 layout/AppShell.vue 里，这里只放一个出口。
// 之所以还留这一层：将来如果要加全局的 loading 或错误边界，有个地方放。
import { ElConfigProvider } from 'element-plus'
import { elLocale } from '@/i18n'
</script>

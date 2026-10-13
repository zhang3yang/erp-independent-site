<!--
  首页装修（独立管理台宿主）。

  表单本体在 station-shared/DecorateForm.vue，和 ERP 内嵌的那套共用一份实现——
  两边曾经各有一份 315 行的拷贝，改一处漏一处，盘点文档 §二.3 记过一次真实漂移。
  这里只剩宿主之间的差异：保存接口怎么调、以及这个人有没有编辑权限。

  独立管理台的 saveSiteContent 不带 siteCode——商家的会话里已经绑定了站点，
  siteCode 由后端从会话取，不接受前端传（传了就等于让商家自己指定要改哪个站）。

  ## 为什么权限要由宿主注入

  表单里那个保存按钮原来直接写 `v-hasPermi="['site:station:edit']"`，而那个指令的做法是
  **把元素从 DOM 里删掉**。对这一页来说结果很糟：整页输入框照样能填、能改，
  只有底下的保存按钮不见了，人填完一整屏才发现没有地方能存。

  改成宿主注入一个 `stationCanEdit`：没权限时整个表单置灰、保存条上写明原因。
  共享组件因此也不再依赖宿主注册过 `v-hasPermi` 这个指令，更符合它「宿主中立」的定位。
-->
<template>
  <decorate-form :saver="save" :previewer="preview"
    :publisher="publish" :discarder="discard" :translator="translate" />
</template>

<script setup name="StationConsoleDecorate">
import { computed, provide } from 'vue'
import DecorateForm from '@station-shared/DecorateForm.vue'
import {
  saveSiteContent, previewSiteContent, publishSiteContent, discardSiteDraft, translateTexts
} from '@/api/console'
import { identity, hasPermi } from '@/store/identity'

const save = contentJson => saveSiteContent(contentJson)

/*
 * 发布 / 放弃草稿。两个都不带站点参数，理由和 save 一样：一个部署一个站，
 * 站点由后端从会话取。
 *
 * ⚠ publish 不把 JSON 传回去——要发布的就是刚才存下去的那一份。传一遍的话就有了
 *   「发布的和刚保存的不是同一份」的可能（两个标签页各开一个装修页面）。
 */
const publish = () => publishSiteContent()
const discard = () => discardSiteDraft()

/** 机器翻译。密钥复用「文件存储」里那对腾讯云的，后端取，前端碰不到 */
const translate = (from, to, texts) => translateTexts(from, to, texts)

/**
 * 装修预览。表单把草稿算好推给另开的那个前台窗口，算这一步在后端。
 *
 * 和 save 一样不带 siteCode——一个部署一个站，站点由后端从会话取。
 */
const preview = (contentJson, lang) => previewSiteContent(contentJson, lang)

/**
 * 能不能改装修。
 *
 * ⚠ 用 computed 而不是在 setup 里算一个布尔值：`identity` 是会话拉回来才填的，
 *   而这一页有可能在那之前就挂载（刷新页面时路由守卫会 await，但登录后跳转过来那一次
 *   是同一个应用实例内的导航）。算成静态值的话，会出现「权限够却整页灰着」，
 *   而且刷新一下就好了——那种时好时坏的问题最难查。
 *
 * ⚠ 依赖 identity.perms 而不是 identity.roleCode：角色码将来会变多，
 *   而权限点是稳定的那一层。后端 AuthInterceptor 判的也是同一个权限点。
 */
const canEdit = computed(() => {
  // 显式读一下 perms，让这个 computed 真的依赖它——
  // hasPermi 内部读的是同一个 reactive 对象，但把依赖写在这里更难被以后的人改坏
  void identity.perms
  return hasPermi(['site:station:edit'])
})

provide('stationCanEdit', canEdit)
</script>

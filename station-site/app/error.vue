<!--
  错误页。三种情况会走到这里：
    404  URL 里的语言这个站没开（useSiteInfo 里主动抛的），或者路径不存在
    503  站点不存在 / 已停用 / 接口被关（后端对这三种一律回同一句话）
    502  后端连不上或返回了非 200 的信封

  故意不显示技术细节：这个页面是给海外买家看的，堆栈和状态码对他没用，
  而且会暴露后端结构。

  ## 文案按语言（2026-09-22 起）

  这一页拿不到站点信息（503 就是「取不到站点信息」本身），所以语言只能从 URL 第一段猜：
  是前台有文案表的那几种就用它，否则英文。404 的情况里 URL 上的语言可能正是那个
  「这个站没开的语言」——但只要文案表里有，用它显示 404 也比英文合适：
  访客是按那个语言来的。「返回首页」的链接同理带上前缀；带错了也只是再 404 一次。
-->
<template>
  <div class="err">
    <h1>{{ error?.statusCode === 404 ? '404' : 'Oops' }}</h1>
    <p>{{ message }}</p>
    <a :href="home" class="btn">{{ t.errHome }}</a>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ error: { statusCode: number; statusMessage?: string; message?: string } }>()

/** 前台有文案表的语言。和 useShopCopy 的 COPY 键一致；这里不 import 那份表，免得错误页也把它整个拖进来 */
const KNOWN = ['en', 'zh', 'ja', 'es', 'pt', 'ru', 'it', 'ko', 'fr']
const route = useRoute()
const urlLang = computed(() => {
  const first = String(route.path || '').split('/')[1] || ''
  return KNOWN.includes(first) ? first : ''
})
/**
 * 没有前缀 = 站点默认语言，那得问站点信息才知道是哪种（这个站是中文，别的站可能是英文）。
 * 站点信息取不到（503 那种情况正是「取不到」本身）就退回英文——这时候本来也没别的可依据。
 * 拿到了也不覆盖 URL 上的语言：/ja/xxx 的 404 就该是日文的。
 */
let siteDefault = ''
try {
  const site = await useSiteInfo()
  siteDefault = KNOWN.includes(site.defaultLang) ? site.defaultLang : ''
} catch {
  siteDefault = ''
}
const lang = computed(() => urlLang.value || siteDefault)
const t = useShopCopy(() => lang.value || 'en')
// 默认语言不带前缀，所以只看 URL 上那一段，不看 siteDefault
const home = computed(() => (urlLang.value ? `/${urlLang.value}` : '/'))

const message = computed(() => {
  if (props.error?.statusCode === 404) return t.value.errNotFound
  // 500 = 配置问题（比如没配 siteKey），这时候把原话显示出来，因为看到它的是开发/运维，
  // 不是买家。503 才是「后端取不到数」，那种情况面向买家，只说一句笼统的。
  //
  // 读 message 不读 statusMessage：长文案放在 message 里（statusMessage 对应 HTTP
  // 状态行，h3 以后会清洗它）
  if (props.error?.statusCode === 500 && props.error?.message) {
    return props.error.message
  }
  return t.value.errUnavailable
})
</script>

<style scoped>
.err {
  min-height: 70vh;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; text-align: center; padding: 40px 20px;
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
}
.err h1 { font-size: 52px; margin: 0; letter-spacing: -.02em; }
.err p { color: #5b6472; margin: 0 0 12px; }
.btn {
  display: inline-block; padding: 11px 22px; border-radius: 8px;
  background: #1f5fbf; color: #fff; text-decoration: none;
}
</style>

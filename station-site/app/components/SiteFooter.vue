<!--
  页脚：联系方式 + 公司名/标语。

  联系方式对外贸站不是装饰——很多买家不填表单，直接抄邮箱或加 WhatsApp。
  所以邮箱和 WhatsApp 都做成可点的链接，手机上点一下就能发起会话。

  ## 底栏那条「查询我的询价单」

  游客下单不要求登录，回执只存在他当时那台浏览器里。确认信里有一条直达链接，
  但信被删了、进了垃圾箱、或者他换了台电脑只记得单号时，**站上原来没有任何入口**。
  这条链接就是那个入口，指向 `/find-order`（单号 + 下单邮箱两格）。

  ⚠ 放在底栏而不是顶栏：它是少数人偶尔用一次的东西，占顶栏的位置不值当，
    而底栏是买家找「联系方式 / 条款」这类东西时本来就会看的地方。
  ⚠ 用 `localeHref` 不是 `localePath`：页脚在 `buyer.` 子域的页面上也会渲染，
    而 `/find-order` 归主域，相对路径会把它渲染在买家域下（客户端路由不经过
    `domain-split` 中间件，那条 301 永远不触发）。
-->
<template>
  <footer class="ft">
    <div class="wrap ft-in">
      <div class="ft-brand">
        <div class="ft-name">{{ site.companyName }}</div>
        <p v-if="site.slogan" class="ft-slogan">{{ site.slogan }}</p>
      </div>

      <div v-if="hasContact" class="ft-contact">
        <div v-if="site.contact.address" class="ft-row">
          <span class="ft-label">{{ shopCopy.ftAddress }}</span>{{ site.contact.address }}
        </div>
        <div v-if="site.contact.phone" class="ft-row">
          <span class="ft-label">{{ shopCopy.ftTel }}</span>
          <a :href="`tel:${site.contact.phone}`">{{ site.contact.phone }}</a>
        </div>
        <div v-if="site.contact.email" class="ft-row">
          <span class="ft-label">{{ shopCopy.ftEmail }}</span>
          <a :href="`mailto:${site.contact.email}`">{{ site.contact.email }}</a>
        </div>
        <div v-if="site.contact.whatsapp" class="ft-row">
          <span class="ft-label">{{ shopCopy.ftWhatsapp }}</span>
          <!-- wa.me 只认纯数字，把空格、加号、横杠都去掉 -->
          <a :href="`https://wa.me/${site.contact.whatsapp.replace(/[^0-9]/g, '')}`"
             target="_blank" rel="noopener">{{ site.contact.whatsapp }}</a>
        </div>
      </div>
    </div>

    <div class="wrap ft-bottom">
      <span>© {{ year }} {{ site.companyName }}</span>
      <NuxtLink :to="localeHref('/find-order')" class="ft-track">
        {{ shopCopy.orcTrackLink }}
      </NuxtLink>
    </div>
  </footer>
</template>

<script setup lang="ts">
import type { SiteInfo } from '~/composables/useSite'

const props = defineProps<{ site: SiteInfo }>()

/* 跨 host 时要绝对地址，理由见文件头 */
const localeHref = useLocaleHref()
const shopCopy = useShopCopy(() => props.site.lang)

const hasContact = computed(() => {
  const c = props.site.contact || {}
  return Boolean(c.address || c.phone || c.email || c.whatsapp)
})

// 年份在服务端算好，避免 hydration 时客户端算出不同的值（跨年那一刻会 mismatch）
const year = new Date().getFullYear()
</script>

<style scoped>
.ft {
  margin-top: 40px;
  background: var(--ink-dark);
  color: #c8cfd8;
  font-size: 14px;
}
.ft-in {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
  padding-top: 40px;
  padding-bottom: 28px;
}
.ft-brand { flex: 1 1 260px; }
.ft-name { color: #fff; font-size: 18px; font-weight: 650; }
.ft-slogan { margin: 6px 0 0; color: #93a0ae; }
.ft-contact { flex: 1 1 320px; display: grid; gap: 6px; }
.ft-row { display: flex; gap: 10px; }
.ft-label {
  flex: 0 0 82px;
  color: #7d8996;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: .04em;
  padding-top: 2px;
}
.ft-contact a:hover { color: var(--primary-light); text-decoration: underline; }
.ft-bottom {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  justify-content: space-between;
  border-top: 1px solid #232a32;
  padding-top: 16px;
  padding-bottom: 22px;
  color: #6f7b88;
  font-size: 13px;
}
.ft-track { color: #93a0ae; }
.ft-track:hover { color: var(--primary-light); text-decoration: underline; }
</style>

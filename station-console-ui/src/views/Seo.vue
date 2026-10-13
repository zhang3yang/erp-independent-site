<template>
  <div class="seo-page">
    <!--
      没有 site:seo:query 就整页不发请求。
      理由和「文件存储」那张卡一样：没有 query 权限，GET 会直接吃 403，
      被 request.js 弹成一句看不懂的报错——不如明说。
    -->
    <el-result v-if="!canQuery" icon="warning" :title="$t('blog.noPermi')"
      :sub-title="$t('seo.noPermiDesc')" />

    <template v-else>
      <SeoAudit />
      <SeoIndexing v-model:config="config" :loading="loading" :can-edit="canEdit" @saved="onSaved" />
      <SeoTracking v-model:config="config" :loading="loading" :can-edit="canEdit" @saved="onSaved" />
    </template>
  </div>
</template>

<script setup name="Seo">
/**
 * 站长工具。
 *
 * <h3>为什么拆成四个文件</h3>
 * 这一页有三块互不相干的内容（体检、收录规则、验证与统计）。全塞进一个 .vue 的话，
 * 它会长成第二个 Overview.vue——那个文件 1717 行，站点档案、存储配置、汇率、
 * 浏览量统计全在里面，现在改任何一块都要先在里面找半天。
 *
 * 不去重构 Overview.vue（那是另一件事，和这一轮无关），但不在它旁边再长一个。
 *
 * <h3>配置只在这一层取一次</h3>
 * SeoIndexing 和 SeoTracking 改的是**同一行**配置（u_s_seo 只有一行），所以
 * 配置对象放在这里、用 v-model 往下传，两张卡里的任何一次保存都送整份。
 *
 * ⚠ 这是必须的，不是图省事：后端一个字段都不走 coalesce（见 SiteSeoMapper.xml），
 *   只送半份会把另外半份清空。让两张卡共用一个对象，就没有「只送半份」这种写法了。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { hasPermi, identity } from '@/store/identity'
import { getSeo } from '@/api/console'
import SeoAudit from '@/views/seo/SeoAudit.vue'
import SeoIndexing from '@/views/seo/SeoIndexing.vue'
import SeoTracking from '@/views/seo/SeoTracking.vue'
import { t } from '@/i18n'

/*
 * ⚠ 用 computed 而不是静态布尔值：identity 是会话拉回来的，
 *   组件初始化时可能还没到。写成 const canQuery = hasPermi([...]) 的话，
 *   刷新页面偶尔会看到「没有权限」——而且只是偶尔，最难查的那种。
 *   （Overview.vue 里的 canEdit / canQueryStorage 也是这么写的，同一个理由。）
 */
const canQuery = computed(() => {
  if (!identity.loaded) return false
  return hasPermi(['site:seo:query'])
})

const canEdit = computed(() => {
  if (!identity.loaded) return false
  return hasPermi(['site:seo:edit'])
})

const loading = ref(false)
const config = ref({})

function load() {
  if (!canQuery.value) return
  loading.value = true
  getSeo()
    .then(res => { config.value = res.data || {} })
    // ⚠ 空 catch，不在这里弹提示：request.js 的拦截器已经对每一种失败弹过一次了
    //   （业务码非 200、403、网络错误都弹），这里再弹就是第二个框。
    //
    // ⚠ 也**不清空 config**：清了的话界面上所有开关会跳到「关」的样子，
    //   看起来像「配置真的是关的」。取数失败就让它保持上一次的样子。
    .catch(() => {})
    .finally(() => { loading.value = false })
}

/**
 * 保存成功后，用后端回的那一份回填。
 *
 * ⚠ 必须回填，不能就地留着用户填的值：后端会把越界的值夹回去
 *   （比如 sitemapMax 超过 6000），界面上要显示**真正生效的值**，
 *   否则下次打开会发现和刚才填的不一样，而中间没有任何提示。
 */
function onSaved(saved) {
  if (saved) config.value = saved
  ElMessage.success(t('common.saved'))
}

onMounted(load)
</script>

<style scoped>
.seo-page {
  padding: 16px 20px 32px;
}
</style>

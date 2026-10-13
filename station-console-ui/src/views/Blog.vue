<template>
  <div class="blog-page">
    <el-result v-if="!canList" icon="warning" :title="$t('blog.noPermi')"
      :sub-title="$t('blog.noPermiDesc')" />

    <template v-else>
      <!--
        ⚠ 这条提示不是客套话，是这个模块唯一一个「做完了但看不见」的坑。

        前台的导航来自首页装修的 nav，不是代码写死的。所以文章发布了、
        /blog 能打开、sitemap 里也有，但**顶栏上没有入口**——除了 Google，
        没人找得到。这句话放在发布按钮旁边的页面顶部，而不是藏在文档里。
      -->
      <el-alert type="info" :closable="false" class="mb12" :title="$t('blog.navTip.title')">
        <template #default>
          <div class="tip-body">
            <span v-html="$t('blog.navTip.body', { url: frontUrl || $t('blog.frontDomain') })"></span>
            <el-link v-if="canDecorate" type="primary" :underline="false"
              @click="$router.push('/decorate')">{{ $t('blog.navTip.go') }}</el-link>
          </div>
        </template>
      </el-alert>

      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <b>{{ $t('blog.title') }}</b>
            <span class="head-right">
              <el-input v-model="query.keyword" size="small" clearable :placeholder="$t('blog.searchPlaceholder')"
                class="q-kw" @keyup.enter="search" @clear="search" />
              <!-- ⚠ value 是库里的中文串（u_s_post.publishstatus），只翻 label -->
              <el-select v-model="query.publishStatus" size="small" clearable :placeholder="$t('common.status')"
                class="q-st" @change="search">
                <el-option v-for="s in STATUSES" :key="s" :label="statusLabel(s)" :value="s" />
              </el-select>
              <el-button size="small" @click="search">{{ $t('common.search') }}</el-button>
              <el-button v-if="canAdd" size="small" type="primary" @click="openCreate">{{ $t('blog.write') }}</el-button>
            </span>
          </div>
        </template>

        <el-table v-loading="loading" :data="rows" size="small" row-key="postCode"
          @selection-change="onSelect">
          <el-table-column type="selection" width="42" />
          <el-table-column :label="$t('blog.col.title')" min-width="240">
            <template #default="{ row }">
              <!--
                标题取的是**站点默认语言**那一条。为空说明默认语言的译文还没填——
                ⚠ 这里刻意不兜底成 slug 或编码：那会让「还没填标题」看起来像填过了。
              -->
              <div v-if="row.title" class="t-title">{{ row.title }}</div>
              <div v-else class="t-empty">{{ $t('blog.noTitle', { lang: defaultLang }) }}</div>
              <div class="t-slug">/blog/{{ row.slug }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('blog.col.category')" prop="category" width="120" show-overflow-tooltip />
          <el-table-column :label="$t('common.status')" width="90">
            <template #default="{ row }">
              <el-tag :type="statusType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('blog.col.publishTime')" width="150">
            <template #default="{ row }">
              <span v-if="row.publishTime">{{ row.publishTime }}</span>
              <span v-else class="t-empty">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('goods.col.sort')" prop="sortNum" width="70" />
          <el-table-column :label="$t('common.operate')" width="170" align="right">
            <template #default="{ row }">
              <el-button v-if="canEdit" link type="primary" size="small"
                @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
              <el-link v-if="frontUrl && row.publishStatus === '已发布'" type="primary"
                :href="`${frontUrl}/blog/${row.slug}`" target="_blank" rel="noopener"
                :underline="false" class="ml8" style="font-size:12px">{{ $t('blog.viewFront') }}</el-link>
              <el-button v-if="canRemove" link type="danger" size="small" class="ml8"
                @click="doDelete([row.postCode])">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="canEdit && selected.length" class="batch">
          {{ $t('blog.selected', { n: selected.length }) }}
          <el-button size="small" type="success" @click="doStatus('已发布')">{{ $t('blog.act.publish') }}</el-button>
          <el-button size="small" @click="doStatus('已下架')">{{ $t('blog.act.unpublish') }}</el-button>
          <el-button size="small" @click="doStatus('草稿')">{{ $t('blog.act.toDraft') }}</el-button>
        </div>
      </el-card>
    </template>

    <!-- ==================== 新建 ==================== -->
    <el-dialog v-model="createOpen" :title="$t('blog.write')" width="520px">
      <el-form label-width="110px">
        <el-form-item :label="$t('blog.slug')" required>
          <el-input v-model="createForm.slug" placeholder="how-to-choose-silicone-tubing" />
          <!--
            ⚠ 这段提示值得占三行：slug 是这个模块唯一一个「改了会有真实代价」的字段。
          -->
          <div class="hint" v-html="$t('blog.slugHint')"></div>
        </el-form-item>
        <el-form-item :label="$t('blog.col.category')">
          <el-input v-model="createForm.category" placeholder="Manufacturing" />
          <div class="hint">{{ $t('blog.categoryHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('blog.author')">
          <el-input v-model="createForm.author" :placeholder="$t('blog.authorPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">{{ $t('blog.createDraft') }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 编辑 ==================== -->
    <el-dialog v-model="editOpen" :title="$t('blog.editTitle')" width="900px" top="4vh">
      <el-form label-width="110px">
        <el-form-item :label="$t('blog.slug')" required>
          <el-input v-model="form.slug" />
          <div v-if="form.publishStatus === '已发布'" class="hint warn"
            v-html="$t('blog.slugPublishedWarn', { slug: originalSlug })"></div>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('blog.col.category')">
              <el-input v-model="form.category" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('blog.author')">
              <el-input v-model="form.author" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('common.status')">
              <el-select v-model="form.publishStatus" style="width:100%">
                <el-option v-for="s in STATUSES" :key="s" :label="statusLabel(s)" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('goods.col.sort')">
              <el-input-number v-model="form.sortNum" :min="0" controls-position="right"
                style="width:100%" />
              <div class="hint">{{ $t('blog.sortHint') }}</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="$t('blog.col.publishTime')">
          <el-input v-model="form.publishTime" :placeholder="$t('blog.publishTimePlaceholder')" />
          <div class="hint">{{ $t('blog.publishTimeHint') }}</div>
        </el-form-item>

        <el-form-item :label="$t('blog.cover')">
          <div class="cover">
            <el-image v-if="form.coverImg" :src="form.coverImg" fit="cover" class="cover-img" />
            <div class="cover-ops">
              <el-button size="small" @click="coverPickerOpen = true">{{ $t('blog.pickCover') }}</el-button>
              <el-button v-if="form.coverImg" size="small" link type="danger"
                @click="form.coverImg = ''">{{ $t('goods.media.remove') }}</el-button>
            </div>
          </div>
          <div class="hint">{{ $t('blog.coverHint') }}</div>
        </el-form-item>

        <el-divider />

        <!--
          ⚠ 语言页签只列**站点当前开的语言**（后端 siteLangs），不是库里有译文的语言。
            库里可能留着站点已经关掉的语言的旧译文，那些不该再摆出来让人继续维护。
        -->
        <el-tabs v-model="activeLang" class="lang-tabs">
          <el-tab-pane v-for="lang in siteLangs" :key="lang" :name="lang">
            <template #label>
              <span>{{ lang }}</span>
              <!-- 有标题的语言打个点：一眼看出哪几种语言真的有这篇文章 -->
              <span v-if="langForm[lang] && langForm[lang].title" class="lang-dot">·</span>
            </template>

            <div v-if="langForm[lang]" class="lang-body">
              <el-alert type="warning" :closable="false" class="mb12"
                :title="$t('blog.langWarn.title')">
                <template #default>
                  <div class="tip-body" v-html="$t('blog.langWarn.body')"></div>
                </template>
              </el-alert>

              <el-form-item :label="$t('blog.col.title')">
                <el-input v-model="langForm[lang].title" maxlength="300" show-word-limit />
                <div class="hint" v-html="$t('blog.titleHint')"></div>
              </el-form-item>

              <el-form-item :label="$t('blog.summary')">
                <el-input v-model="langForm[lang].summary" type="textarea" :rows="3"
                  maxlength="500" show-word-limit />
                <div class="hint" v-html="$t('blog.summaryHint')"></div>
              </el-form-item>

              <el-form-item :label="$t('blog.content')">
                <rich-text-field v-model="langForm[lang].content" :rows="14" />
                <!--
                  ⚠⚠ 这段提示必须在，它解释了一个会被当成 bug 的行为。
                -->
                <div class="hint" v-html="$t('blog.contentHint')"></div>
              </el-form-item>

              <el-form-item :label="$t('goods.copy.keywords')">
                <el-input v-model="langForm[lang].keywords" maxlength="500" />
                <div class="hint" v-html="$t('blog.keywordsHint')"></div>
              </el-form-item>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="editOpen = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <MediaPicker v-model="coverPickerOpen" type="image" :limit="1"
      :selected="form.coverImg ? [form.coverImg] : []" @confirm="onCoverPicked" />
  </div>
</template>

<script setup name="Blog">
/**
 * 博客管理。
 *
 * <h3>两个标识，别混用</h3>
 * `postCode` 是内部编码（BLOG 前缀），这一页所有接口用的都是它；
 * `slug` 是公开地址那一段（/blog/how-to-choose-silicone-tubing）。
 * 编码不出现在前台任何地方——博客的意义就是自然搜索，而 URL 里的关键词是排名因素。
 *
 * <h3>⚠ 这一页最容易被误解的两件事，都写在界面上了</h3>
 * 1. **前台导航要自己加**：文章发布后 /blog 能开、sitemap 里也有，但顶栏没有入口
 *    （导航来自首页装修的 nav）。页面顶部那条 el-alert 就是为它存在的。
 * 2. **没填标题的语言前台就没有这篇文章**（和商品相反）。语言页签里那条 el-alert。
 *
 * <h3>正文是纯文本域，不是所见即所得</h3>
 * 和商品详情同一个做法：运营手写 HTML，保存时后端过 RichTextCleaner 白名单。
 * ⚠ 前端**不做**任何过滤或校验——做了只会和后端那份产生分歧，而它挡不住任何东西
 * （谁都能直接发请求）。真正的防线是服务层那一道。
 */
import { ref, reactive, computed, inject, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSitePosts, getSitePost, addSitePost, saveSitePost,
  updateSitePostStatus, delSitePosts
} from '@/api/console'
import MediaPicker from '@station-shared/MediaPicker.vue'
import RichTextField from '@station-shared/RichTextField.vue'
import { identity, hasPermi } from '@/store/identity'
import { toFrontUrl } from '@/utils/siteUrl'
import { t } from '@/i18n'

/* ⚠ 库里存的就是这三个中文串（u_s_post.publishstatus），筛选和后端都按它判 */
const STATUSES = ['草稿', '已发布', '已下架']

const STATUS_KEY = {
  草稿: 'blog.status.draft',
  已发布: 'blog.status.published',
  已下架: 'blog.status.withdrawn'
}
const statusLabel = s => (STATUS_KEY[s] ? t(STATUS_KEY[s]) : s)

const site = inject('stationSite', ref({}))
const frontUrl = computed(() => toFrontUrl(site.value?.domain))

/*
 * ⚠ 权限一律用 computed，不用静态布尔值：identity 是会话拉回来的，
 *   组件初始化时可能还没到。写成 const canX = hasPermi([...]) 的话，
 *   刷新页面偶尔会看到「没有权限」——而且只是偶尔，最难查的那种。
 *   （Overview.vue / Seo.vue 里也是这么写的，同一个理由。）
 */
const canList = computed(() => identity.loaded && hasPermi(['site:post:list']))
const canAdd = computed(() => identity.loaded && hasPermi(['site:post:add']))
const canEdit = computed(() => identity.loaded && hasPermi(['site:post:edit']))
const canRemove = computed(() => identity.loaded && hasPermi(['site:post:remove']))
/** 那条「去首页装修」的链接：没有装修权限就不给链接，免得点过去只能看不能改 */
const canDecorate = computed(() => identity.loaded && hasPermi(['site:station:edit']))

const defaultLang = computed(() => (identity.langs && identity.langs[0]) || 'en')

const loading = ref(false)
const rows = ref([])
const selected = ref([])
const query = reactive({ keyword: undefined, publishStatus: undefined, category: undefined })

const statusType = s => ({ 草稿: 'info', 已发布: 'success', 已下架: 'warning' }[s] || 'info')

function load() {
  if (!canList.value) return
  loading.value = true
  listSitePosts(query)
    .then(r => { rows.value = r.data || [] })
    // ⚠ 空 catch：request.js 的拦截器已经对每种失败弹过一次了，这里再弹就是第二个框
    .catch(() => {})
    .finally(() => { loading.value = false })
}
function search() { load() }
function onSelect(sel) { selected.value = sel.map(r => r.postCode) }

// ---------- 新建 ----------
const createOpen = ref(false)
const creating = ref(false)
const createForm = ref({ slug: '', category: '', author: '' })

function openCreate() {
  createForm.value = { slug: '', category: '', author: '' }
  createOpen.value = true
}
function doCreate() {
  if (!createForm.value.slug?.trim()) { ElMessage.warning(t('blog.msg.needSlug')); return }
  creating.value = true
  addSitePost(createForm.value)
    .then(r => {
      ElMessage.success(r.msg || t('goods.msg.created'))
      createOpen.value = false
      load()
      // 建完直接打开编辑：新建对话框只收了骨架，文案还得填，
      // 让人再去列表里找一遍那一行是多余的一步
      if (r.data) openEdit({ postCode: r.data })
    })
    .catch(() => {})
    .finally(() => { creating.value = false })
}

// ---------- 编辑 ----------
const editOpen = ref(false)
const saving = ref(false)
const form = ref({})
const originalSlug = ref('')
const siteLangs = ref(['en'])
const activeLang = ref('en')
const langForm = reactive({})
const coverPickerOpen = ref(false)

function openEdit(row) {
  getSitePost(row.postCode).then(r => {
    const data = r.data || {}
    form.value = { ...(data.post || {}) }
    form.value.sortNum = Number(form.value.sortNum || 0)
    originalSlug.value = form.value.slug || ''

    siteLangs.value = data.siteLangs?.length ? data.siteLangs : ['en']
    activeLang.value = siteLangs.value[0]

    // 每种语言都建一份空表单，切页签时不会因为没数据而报错（同 Goods.vue）
    Object.keys(langForm).forEach(k => delete langForm[k])
    siteLangs.value.forEach(lang => {
      const exist = (data.langs || []).find(l => l.lang === lang) || {}
      langForm[lang] = {
        title: exist.title || '',
        summary: exist.summary || '',
        content: exist.content || '',
        keywords: exist.keywords || ''
      }
    })
    editOpen.value = true
  }).catch(() => {})
}

function onCoverPicked(urls) {
  form.value.coverImg = (urls || [])[0] || ''
}

function doSave() {
  saving.value = true
  const langs = siteLangs.value.map(lang => ({ lang, ...langForm[lang] }))
  // ⚠ 整份送（post + 全部语言）：后端 update 里只有 publishTime 走 coalesce，
  //   其余都是直赋值。送半份会把没送的那半清空
  saveSitePost({ post: form.value, langs })
    .then(() => {
      ElMessage.success(t('common.saved'))
      editOpen.value = false
      load()
    })
    .catch(() => {})
    .finally(() => { saving.value = false })
}

// ---------- 批量 / 删除 ----------
function doStatus(status) {
  updateSitePostStatus(selected.value, status)
    .then(r => { ElMessage.success(r.msg || t('orders.msg.updated')); load() })
    .catch(() => {})
}

function doDelete(codes) {
  ElMessageBox.confirm(
    t('blog.msg.delConfirm', { n: codes.length }),
    t('blog.msg.delTitle'), { type: 'warning' }
  ).then(() => delSitePosts(codes.join(',')))
    .then(r => { ElMessage.success(r?.msg || t('common.deleted')); load() })
    // ⚠ catch 同时兜住「点了取消」和「请求失败」。取消不该弹任何东西
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.blog-page {
  padding: 16px 20px 32px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.head-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.q-kw { width: 190px; }
.q-st { width: 110px; }
.mb12 { margin-bottom: 12px; }
.ml8 { margin-left: 8px; }

.t-title { font-weight: 500; }
.t-empty { color: var(--el-text-color-disabled); }
.t-slug {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.batch {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
}

.tip-body {
  font-size: 12px;
  line-height: 20px;
}
.hint {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.hint.warn { color: var(--el-color-warning); }
.hint code,
.tip-body code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.cover {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.cover-img {
  width: 160px;
  height: 90px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
}
.cover-ops {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lang-tabs {
  margin-top: 4px;
}
.lang-dot {
  color: var(--el-color-success);
  font-weight: 700;
  margin-left: 2px;
}
.lang-body {
  padding-top: 4px;
}
</style>

<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="独立站管理：登记有哪些站、在哪、怎么进。站里的内容在各站自己的管理台里改"
      description="每个独立站是一台独立的服务器，有自己的库、自己的用户体系，能在 ERP 停机时照常运行。这张表只登记站点本身：域名、服务器 IP、负责人、到期日、跳转密钥。商品、询盘、首页装修都在那台机器的管理台（console.主域名）上做，不在这里。" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <el-form-item label="站点名称"><el-input v-model="query.siteName" clearable style="width: 160px" @keyup.enter="search" /></el-form-item>
      <el-form-item label="域名"><el-input v-model="query.domain" clearable style="width: 180px" @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 120px">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人"><el-input v-model="query.ownerName" clearable style="width: 130px" @keyup.enter="search" /></el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">搜索</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['site:station:add']" @click="openCreate">登记站点</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="search" />
    </el-row>

    <!-- 用卡片而不是表格：一屏就几个站，卡片能把「到期没」「谁负责」这些一眼摆出来 -->
    <div v-loading="loading">
      <el-empty v-if="!rows.length" description="还没有登记站点，点右上角「登记站点」开始" />
      <el-row v-else :gutter="16">
        <el-col v-for="site in rows" :key="site.siteCode" :xs="24" :sm="12" :md="8" :lg="8" :xl="6">
          <el-card class="site-card" shadow="hover" :class="{ 'site-card-off': isOff(site) }">
            <div class="site-head">
              <span class="site-name" :title="site.siteName">{{ site.siteName }}</span>
            </div>

            <!--
              ⚠ 这里显示和链接的都是**前台地址**（buyer 子域），不是主域名。
              原来拼的是 `https://${site.domain}`，点开是打不开的：前台不在主域上
              （主域还没备案、也没有自己的证书，nginx 里根本没有它的 server 块）。

              ⚠ 别改回在这边拼。地址由后端的 StationSite#getFrontUrl 给：那边有一套
              去协议头、剥 user:pass@ 凭据的清洗规则（这一列容忍脏值），在 JS 里
              重写一遍迟早对不上，而对不上的后果是卡片上出现一个看着像自家域名的
              钓鱼地址。

              🔧 两 host 布局上线后前台地址会变成主域，那时候改的是后端那个方法，
                 这里一个字都不用动——见 StationSite#getFrontUrl 的注释。
            -->
            <div class="site-domain">
              <el-link v-if="site.frontUrl" type="primary" :href="site.frontUrl" target="_blank" rel="noopener">
                {{ frontHost(site) }}
              </el-link>
              <span v-else class="text-muted">未填域名</span>
            </div>

            <div class="site-meta">
              <div :class="expireClass(site)"><span class="meta-label">到期</span>{{ expireText(site) }}</div>
              <div><span class="meta-label">负责人</span>{{ site.ownerName || '—' }}</div>
              <div><span class="meta-label">服务器</span>{{ site.serverIp || '未登记' }}</div>
              <!--
                语义是「点了进入管理」，不是「进成功了」——新协议下验签在站点侧、不回调 ERP，
                这边只知道有人点了按钮。别拿它当活跃度指标。
              -->
              <div><span class="meta-label">上次点入</span>{{ site.lastAccessTime || '未点过' }}</div>
              <div>
                <span class="meta-label">跳转密钥</span>
                <el-tag v-if="site.secretConfigured" size="small" type="success" effect="plain">已配置</el-tag>
                <el-tag v-else size="small" type="danger" effect="plain">未配置</el-tag>
                <el-button v-if="canEdit" link type="primary" size="small"
                  :loading="rotating === site.siteCode" @click="rotate(site)">重置</el-button>
              </div>
            </div>

            <div class="site-actions">
              <!--
                没登记域名 = 跳转地址拼不出来，按钮置灰。
                §4.5：没登记域名的站，「进入管理」按钮就该是灰的，
                而不是跳到一个 ERP 自己实现的替身——那个内置替身已经删了。
              -->
              <el-button type="primary" size="small" icon="Setting"
                :loading="entering === site.siteCode"
                :disabled="!!entering || !canJump(site)"
                :title="canJump(site) ? '' : '先登记主域名或管理后台域名'"
                @click="enterConsole(site)">进入管理</el-button>
              <!--
                站点用户：站点管理台的「用户管理」菜单拿掉了（全是 ERP 登录名，不给海外客户看），
                唯一入口在这儿。在新标签页打开一个只有用户列表的页面。
              -->
              <el-button size="small" icon="User" v-hasPermi="['site:station:edit']"
                :loading="entering === 'users:' + site.siteCode"
                :disabled="!!entering || !canJump(site)"
                :title="canJump(site) ? '管理这个站点的操作用户（角色、停用、删除）' : '先登记主域名或管理后台域名'"
                @click="enterConsole(site, 'users')">站点用户</el-button>
              <el-button size="small" icon="CopyDocument" :disabled="!site.frontUrl"
                @click="copyText(site.frontUrl, '前台地址')">复制地址</el-button>
              <el-button size="small" v-hasPermi="['site:station:edit']" @click="openEdit(site)">修改</el-button>
              <el-button size="small" type="danger" plain v-hasPermi="['site:station:remove']" @click="remove(site)">删除</el-button>
              <!--
                状态就摆在删除右边，改它不用进修改弹窗：停用是个常用动作（域名到期、不再合作），
                为它开一次弹窗、改一个下拉、再点保存太重了。
              -->
              <el-switch class="site-status" :model-value="!isOff(site)"
                inline-prompt active-text="启用" inactive-text="停用" :width="56"
                :loading="switching === site.siteCode"
                :disabled="!canEdit || (!!switching && switching !== site.siteCode)"
                @change="value => switchStatus(site, value)" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 后端 startPage 默认每页 10 条，没有这个组件就只看得到第一页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 登记 / 修改 ===================== -->
    <el-dialog v-model="formOpen" :title="editing ? '修改站点登记' : '登记站点'" width="760px" :close-on-click-modal="false">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="站点名称" required>
            <el-input v-model="form.siteName" maxlength="100" placeholder="如：康辰英文站" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="主域名">
            <el-input v-model="form.domain" maxlength="200" placeholder="example.com" />
            <div class="hint">同一个域名只能建一条，协议头和末尾斜杠会自动去掉。</div>
          </el-form-item></el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="服务器 IP">
            <el-input v-model="form.serverIp" maxlength="100" placeholder="多个用逗号分隔" />
            <div class="hint">
              运维找机器用的登记信息。<b>ERP 不会去连它</b>——两边后端没有任何互通。
            </div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="负责人">
            <el-input v-model="form.ownerName" maxlength="50" placeholder="选填" />
          </el-form-item></el-col>
        </el-row>

        <!--
          四个子域按约定从主域名派生，留空即可。留出覆盖位是因为「以后每个客户带自己的
          品牌域名」的场景里，客户不一定照我们的约定命名。
        -->
        <el-divider content-position="left">
          <span class="divider-text">子域名（留空则按 前缀.主域名 自动派生）</span>
        </el-divider>
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="后端服务域名">
            <el-input v-model="form.dataDomain" maxlength="200" :placeholder="derived('data')" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="文件域名">
            <el-input v-model="form.fileDomain" maxlength="200" :placeholder="derived('file')" />
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="管理后台域名">
            <el-input v-model="form.consoleDomain" maxlength="200" :placeholder="derived('console')" />
            <div class="hint"><b>「进入管理」跳的就是它。</b>它和主域名都空着，那个按钮是灰的。</div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="前台域名">
            <el-input v-model="form.buyerDomain" maxlength="200" :placeholder="derived('buyer')" />
            <div class="hint">
              <b>卡片上那个链接和「复制地址」用的就是它。</b>
              访客看到的站在这里，不在主域上——主域还没备案，服务器上也没给它配 server 块。
            </div>
          </el-form-item></el-col>
        </el-row>

        <el-divider content-position="left"><span class="divider-text">管理</span></el-divider>
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="服务到期日">
            <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD"
              placeholder="域名/服务器到期日" style="width:100%" />
            <div class="hint">填了才有到期提醒，剩 30 天内标黄、过期标红。</div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="排序">
            <el-input-number v-model="form.sortNum" :min="0" :precision="0" controls-position="right" style="width:100%" />
            <div class="hint">越小越靠前。相同排序按到期日近的排前面。</div>
          </el-form-item></el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="选填，例如：机房、续费方式、服务商联系人" />
        </el-form-item>

        <!--
          ⚠ 这里没有语言、币种、询盘通知邮箱、首页装修。
          它们是站里的业务数据，搬到那台站点服务器的 u_s_siteinfo 去了（§7.2）。
          两边都留一份的话，就是两份会各自被改的配置，而且没有任何机制能发现它们不一致了。
        -->
        <el-alert v-if="!editing" type="warning" :closable="false" show-icon
          title="保存后会生成一份跳转密钥，只显示这一次"
          description="那串密钥要填进这台站点服务器的 u_s_siteinfo.consolesecret，两边必须一致，否则「进入管理」进不去。" />
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 明文密钥，只显示这一次 ===================== -->
    <el-dialog v-model="secretOpen" title="跳转密钥（只显示这一次）" width="620px"
      :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert type="error" :closable="false" show-icon class="mb8"
        title="关掉这个窗口之后就再也看不到了"
        description="库里存的是密文。丢了只能重置一份新的，而重置会让那个站的「进入管理」立刻失效，直到你把新密钥同步过去。" />
      <div class="secret-box">{{ secretValue }}</div>
      <div class="hint">
        把它填进 <b>{{ secretSiteName }}</b> 那台服务器的
        <code>sdsstation.u_s_siteinfo.consolesecret</code>，然后重启站点后端。
      </div>
      <template #footer>
        <el-button type="primary" icon="CopyDocument" @click="copyText(secretValue, '密钥')">复制</el-button>
        <el-button @click="secretOpen = false">我已保存好</el-button>
      </template>
    </el-dialog>

    <!--
      跳转令牌的提交表单不在模板里：由 utils/consoleJump.js 动态建、提交完拆掉。
      它必须是 POST、必须是真的 <form>——理由见那个文件顶部的注释（边界 §4.2）。
    -->
  </div>
</template>

<script setup name="StationSite">
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSites, getSite, addSite, updateSite, deleteSite, rotateSecret, createConsoleJump, createUsersJump } from '@/api/site/station'
import { checkPermi } from '@/utils/permission'
import { submitConsoleJump } from '@/utils/consoleJump'

/**
 * 站点只有启用和停用两态。
 *
 * 升级前是「建设中 / 运行中 / 已停用」三态：建设中和运行中在系统里没有任何行为差别，
 * 只是给人看的两个词。历史数据由 sql/station_site_status_20260826.sql 折成两态。
 *
 * ⚠ 这个状态管的是「这个站还在不在服务、ERP 还发不发跳转令牌」。
 *   站点那边还有一个自己的 status，管「前台开不开」——
 *   两个决定，不是一个值的副本，所以不需要同步（§7.2）。
 */
const statuses = ['启用', '停用']
/** 停用的站：ERP 不给它发跳转令牌 */
const STATUS_OFF = '停用'

const loading = ref(false), saving = ref(false), showSearch = ref(true)
/** 正在改状态的站点编码。改状态是一次网络往返，开关期间要转圈，别让人连点 */
const switching = ref('')
/** 正在签令牌的站点编码 */
const entering = ref('')
/** 正在重置密钥的站点编码 */
const rotating = ref('')
/** 没有改站点权限的人也看得见状态开关和重置按钮，只是点不动 */
const canEdit = computed(() => checkPermi(['site:station:edit']))
const total = ref(0)
const rows = ref([])
const formOpen = ref(false), editing = ref(false)
const secretOpen = ref(false), secretValue = ref(''), secretSiteName = ref('')
const query = reactive({
  siteName: undefined, domain: undefined, status: undefined, ownerName: undefined,
  pageNum: 1, pageSize: 20
})
const form = ref({})

function loadList() {
  loading.value = true
  listSites(query)
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => loading.value = false)
}
/** 换条件要回到第一页，否则会停在新结果集里根本不存在的页码上 */
function search() {
  query.pageNum = 1
  loadList()
}
function reset() {
  query.siteName = undefined; query.domain = undefined
  query.status = undefined; query.ownerName = undefined
  search()
}

/**
 * 这个站是不是停用的。
 *
 * 迁移脚本还没跑的库里停用存的是「已停用」，两个值都得认——否则那些站在界面上
 * 会显示成启用，而后端照样把人挡在门外。
 */
const isOff = site => site.status === STATUS_OFF || site.status === '已停用'

/** 跳得动吗：管理后台域名和主域名至少要有一个，否则地址拼不出来 */
const canJump = site => !!(site.consoleDomain || site.domain)

/**
 * 卡片上显示的前台主机名：把后端给的 frontUrl 去掉协议头，纯粹为了好看。
 *
 * ⚠ 只用来显示。href 和「复制地址」一律用 site.frontUrl 原值——
 * 那是后端 StationSite#getFrontUrl 清洗过的，别在这边二次加工再拼回去。
 */
const frontHost = site => (site.frontUrl || '').replace(/^https?:\/\//i, '')

/** 子域输入框的占位提示：按当前填的主域名现拼一个给人看 */
function derived(prefix) {
  const root = (form.value.domain || '').trim()
  return root ? `${prefix}.${root}` : `留空则用 ${prefix}.主域名`
}

/**
 * 到期文案。expireDays 是后端按当天现算的：负数已过期，null 是没填到期日。
 * 不在前端拿本地时间算——客户端时区和时钟都不可靠。
 */
function expireText(site) {
  if (!site.expireDate) return '未设置'
  const days = Number(site.expireDays)
  if (!Number.isFinite(days)) return site.expireDate
  if (days < 0) return `${site.expireDate}（已过期 ${-days} 天）`
  if (days === 0) return `${site.expireDate}（今天到期）`
  return `${site.expireDate}（剩 ${days} 天）`
}

/** 快到期标黄、过期标红。判据用后端算好的 expireDays，别在前端拿本地时钟算 */
function expireClass(site) {
  const days = Number(site.expireDays)
  if (!site.expireDate || !Number.isFinite(days)) return ''
  if (days < 0) return 'expire-danger'
  return days <= 30 ? 'expire-warn' : ''
}

function openCreate() {
  editing.value = false
  // 状态不在表单上，新登记的站一律启用，之后在卡片的开关上改
  form.value = {
    siteName: '', domain: '', serverIp: '', ownerName: '',
    dataDomain: '', fileDomain: '', consoleDomain: '', buyerDomain: '',
    expireDate: '', status: '启用', sortNum: 0, remark: ''
  }
  formOpen.value = true
}

function openEdit(row) {
  editing.value = true
  getSite(row.siteCode).then(r => {
    form.value = { ...(r.data || {}) }
    formOpen.value = true
  })
}

function save() {
  if (!form.value.siteName?.trim()) { ElMessage.warning('请填写站点名称'); return }
  saving.value = true
  const action = editing.value ? updateSite(form.value) : addSite(form.value)
  action
    .then(res => {
      formOpen.value = false
      loadList()
      if (editing.value) {
        ElMessage.success('登记已修改')
        return
      }
      ElMessage.success('站点已登记')
      // 新建时后端会返回一份明文密钥，弹出来让人抄走——它只出现这一次
      const created = res.data || {}
      if (created.consoleSecret) {
        showSecret(created.consoleSecret, created.siteName)
      }
    })
    .finally(() => saving.value = false)
}

/**
 * 重置跳转密钥。
 *
 * 这是个有后果的动作，所以要确认：换完之后那个站的「进入管理」立刻失效，
 * 直到有人把新密钥填进那台服务器。前台和站点本地登录都不受影响——
 * 所以这个故障看起来会像「只有跳转坏了」，很容易查错方向。
 */
function rotate(site) {
  if (rotating.value) return
  ElMessageBox.confirm(
    `确认为「${site.siteName}」重置跳转密钥？<br><br>` +
    `重置之后，<b>在你把新密钥填进那台服务器之前，这个站的「进入管理」都进不去</b>。` +
    `站点前台和它自己的登录页不受影响。`,
    '提示', { type: 'warning', dangerouslyUseHTMLString: true, closeOnClickModal: false })
    .then(() => {
      rotating.value = site.siteCode
      return rotateSecret(site.siteCode)
        .then(res => {
          showSecret(res.data, site.siteName)
          loadList()
        })
        .finally(() => { rotating.value = '' })
    })
    .catch(() => {})
}

function showSecret(secret, siteName) {
  secretValue.value = secret || ''
  secretSiteName.value = siteName || ''
  secretOpen.value = true
}

/**
 * 进站点管理台。
 *
 * ⚠ 令牌走 POST 表单，不走 URL。表单由 utils/consoleJump.js 动态建、提交完拆掉，
 *   理由见那个文件顶部的注释，以及 api/site/station.js 里 createConsoleJump 的说明。
 *   登录直达（views/tenant/select.vue）用的是同一个函数，只是在当前窗口走。
 *
 * @param purpose 'users' = 「站点用户」按钮：令牌带 scope=users，站点那边直接打开用户列表
 */
async function enterConsole(site, purpose) {
  if (entering.value) return
  entering.value = purpose === 'users' ? 'users:' + site.siteCode : site.siteCode
  try {
    const res = await (purpose === 'users' ? createUsersJump(site.siteCode) : createConsoleJump(site.siteCode))
    const data = res.data || {}
    if (!data.action || !data.token) {
      ElMessage.error('没拿到跳转令牌，请重试')
      return
    }
    submitConsoleJump({ action: data.action, token: data.token, target: '_blank' })
  } catch (e) {
    // 站点被停用/删除、没登记域名、没配密钥等等，request.js 已经弹过提示了
  } finally {
    entering.value = ''
  }
}

/** clipboard API 在非 https 页面上不可用（内网 ERP 常见），退回老办法 */
function copyText(text, label) {
  if (!text) return
  const fallback = () => {
    const input = document.createElement('textarea')
    input.value = text
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
  }
  const done = () => ElMessage.success(`已复制${label || ''}`)
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text).then(done).catch(() => { fallback(); done() })
  } else {
    fallback()
    done()
  }
}

/**
 * 卡片上的启用/停用开关。
 *
 * 走的是普通的改站点接口，所以要把整条档案原样带上——后端的 updateSite 会走一遍
 * 完整校验（站点名称非空等），只传 { siteCode, status } 会被「站点名称不能为空」挡下来。
 *
 * 开关是受控的：视图跟着 site.status 走，不跟着开关自己的内部状态走。请求失败时什么都不改，
 * 开关自己会弹回原位——不会出现「界面上是停用、库里还是启用」这种对不上的中间态。
 */
function switchStatus(site, value) {
  if (switching.value) return
  const target = value ? '启用' : STATUS_OFF
  const ask = value
    ? Promise.resolve()
    : ElMessageBox.confirm(
        `确认停用站点「${site.siteName}」？停用后 ERP 不再给它发跳转令牌，「进入管理」进不去。` +
        `⚠ 站点前台不受影响——那是那台服务器上另一个开关。`,
        '提示', { type: 'warning', closeOnClickModal: false })

  ask.then(() => {
    switching.value = site.siteCode
    return updateSite({ ...site, status: target })
      .then(() => {
        site.status = target
        ElMessage.success(`站点已${target}`)
      })
      .finally(() => { switching.value = '' })
  }).catch(() => {})
}

function remove(site) {
  ElMessageBox.confirm(
    `确认删除站点「${site.siteName}」的登记？<br><br>` +
    `⚠ 删的只是 ERP 这条记录，<b>那台服务器上的库、商品、询盘都还在</b>——` +
    `要真正下线一个站，是去关那台机器。不再合作的站建议改成「停用」留档。`,
    '提示', { type: 'warning', dangerouslyUseHTMLString: true, closeOnClickModal: false })
    .then(() => deleteSite(site.siteCode))
    .then(() => { ElMessage.success('登记已删除'); loadList() })
    .catch(() => {})
}

onMounted(loadList)

// 本页被 keep-alive 缓存，从别的页切回来时 onMounted 不会再触发。
// onActivated 首次挂载后也会触发一次，用标志位跳过，避免重复请求。
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) { skipFirstActivate = false; return }
  loadList()
})
</script>

<style scoped>
.site-card {
  margin-bottom: 16px;
}
/* 停用的站压暗，一眼看得出它不该再被点进去 */
.site-card-off {
  opacity: 0.65;
}
.site-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.site-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.site-domain {
  margin-top: 6px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.site-meta {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-regular);
}
.expire-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}
.expire-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}
.meta-label {
  display: inline-block;
  width: 68px;
  color: var(--el-text-color-secondary);
}
.site-actions {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
/*
  开关排在「删除」右边，和按钮一行，文字（启用/停用）写在开关里面——所以要给足宽度，
  默认的 40px 装不下两个汉字。
  关掉时的底色也要自己指定：默认是很浅的边框灰，而开关里的字是白的，白底灰上根本看不清。
*/
.site-status {
  --el-switch-off-color: var(--el-color-info);
}
/* 密钥是一串 43 字符的 base64url，等宽字体 + 可选中，方便手工核对 */
.secret-box {
  margin: 12px 0;
  padding: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 14px;
  word-break: break-all;
  user-select: all;
}
.divider-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}
.text-muted {
  color: var(--el-text-color-secondary);
}
</style>

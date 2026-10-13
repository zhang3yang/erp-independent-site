<template>
  <div class="app-container home">
    <!-- 欢迎区域 -->
    <el-row :gutter="24" class="welcome-section">
      <el-col :span="24">
        <div class="welcome-card">
          <div class="welcome-text">
            <!-- ⚠ 这里不写「众联ERP系统」「企业资源计划」这类字眼：首页也会被客户看到，
                 写上等于自报「这是个 ERP」。理由同 .env.production 里 VITE_APP_TITLE 那段。 -->
            <h1>欢迎使用管理后台</h1>
            <p class="subtitle">内部管理平台</p>
            <!--
              看板不出来时这一屏就只剩这张卡了，所以这里要把原因说清楚：
              留一句「请选择下方业务模块」而下方空无一物，比什么都不说更让人发懵。
            -->
            <p class="desc" v-if="dashboardVisible">
              下面这排数字是今天该处理的事，点一下就能落到对应的页面。
            </p>
            <p class="desc" v-else>
              经营看板需要「查看看板」权限，管理员还要先选定商家。
              日常业务请从左侧菜单进入。
            </p>
            <!-- 看板里已经有「未处理预警」这张卡，同屏显示两遍同一个数没有意义。
                 但看板可能因为没权限而整块不渲染，那时这个角标是唯一的入口，所以只是让位、不是删掉 -->
            <el-badge v-if="warningCount > 0 && warningPath && !dashboardVisible" :value="warningCount" class="warning-badge">
              <el-button type="danger" plain @click="goTarget(warningPath)">库存低库存预警</el-button>
            </el-badge>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 经营看板。没权限/没选商家时整块不渲染，见 loadDashboard 的注释 -->
    <template v-if="dashboardVisible">
      <el-row :gutter="16" class="stat-section">
        <el-col :xs="12" :sm="12" :md="8" :lg="6" v-for="stat in stats" :key="stat.key">
          <!--
            整排卡片都套一层 popover，只有两张真正启用（stat.popover 给出浮层的种类）：
            「待我处理」拆成各个审批队列，「本月销售额」拆成各个币种。
            这么写是为了不把卡片内容抄三遍，也不用把这两张卡挪出 v-for——挪出去它们就
            不在原来的位置上了，而这排卡片的顺序是有讲究的（先待办、后状态、再本月流水）。
          -->
          <el-popover :disabled="!stat.popover" placement="bottom-start" :width="stat.popover === 'sales' ? 280 : 248"
            trigger="click" popper-class="pending-popover">
            <template #reference>
              <div class="stat-card" :class="{ clickable: !!stat.path || !!stat.popover }"
                @click="stat.path && goTarget(stat.path)">
                <div class="stat-label">{{ stat.label }}</div>
                <div class="stat-value" :style="{ color: stat.color }" :title="stat.title">
                  {{ stat.value }}<span class="stat-unit">{{ stat.unit }}</span>
                </div>
                <!-- 副行：逾期/超期的张数，或客单价。没有就整行不占位 -->
                <div v-if="stat.sub" class="stat-sub" :class="{ danger: stat.subDanger }">{{ stat.sub }}</div>
              </div>
            </template>

            <!--
              v-if 不能省：popover 的默认插槽是<b>提前渲染</b>的，不加的话这排十来张卡片
              各渲染一份列表，DOM 里躺着一堆隐藏的废内容。
              :disabled 只管不弹出，不管不渲染。
            -->
            <div v-if="stat.popover === 'pending'" class="pending-list">
              <div v-for="row in pendingDocs" :key="row.docType" class="pending-row"
                :class="{ linkable: !!pendingRoute(row) }" @click="goPending(row)">
                <span class="pending-name">{{ row.docTypeName || row.docType }}</span>
                <span class="pending-count">{{ row.docCount }} 单</span>
              </div>
              <!--
                灰色有两种来由：一是生产四类、借出归还这些没有独立审核页面的单据，
                二是这个人没被授权那张菜单。措辞要同时对得上，别只说前一种。
              -->
              <div v-if="hasUnroutablePending" class="pending-hint">
                灰色的这几行跳不过去，要到对应业务模块里处理
              </div>
            </div>

            <!--
              有外币时才弹这一份：逐个币种列原币金额、今天的汇率和折合人民币。
              没录汇率的那行标红，它没进卡片上的合计。
            -->
            <div v-else-if="stat.popover === 'sales'" class="pending-list">
              <div v-for="row in salesRows" :key="row.currency" class="pending-row sales-row">
                <div class="sales-row-top">
                  <span class="pending-name">{{ currencyLabel(row.currency) }}</span>
                  <span class="pending-count">{{ formatCurrency(row.amount, row.currency) }}</span>
                </div>
                <div class="sales-row-sub">
                  {{ row.orderCount }} 单
                  <template v-if="normalizeCurrency(row.currency) !== 'CNY'">
                    <span v-if="row.rate != null"> · 汇率 {{ Number(row.rate) }} · 折合 {{ formatCurrency(row.amountBase, 'CNY') }}</span>
                    <span v-else class="rate-missing"> · 未录汇率，没算进合计</span>
                  </template>
                  <span v-else> · 客单价 {{ formatCurrency(avgTicket(row), row.currency) }}</span>
                </div>
              </div>
              <div class="pending-hint">
                按「财务管理 → 汇率设置」里今天生效的汇率折算
              </div>
            </div>
          </el-popover>
        </el-col>
      </el-row>

      <!-- 两张图各自跟着自己那份权限走；一张都画不了时整行不渲染，不留一条空白 -->
      <el-row :gutter="16" class="chart-section" v-if="costVisible || financeVisible">
        <el-col :xs="24" :lg="costVisible && financeVisible ? 12 : 24" v-if="costVisible">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">近 {{ TREND_MONTHS }} 个月出入库金额</span></template>
            <div ref="stockChartRef" class="chart-body"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="costVisible && financeVisible ? 12 : 24" v-if="financeVisible">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">近 {{ TREND_MONTHS }} 个月收支</span></template>
            <div ref="financeChartRef" class="chart-body"></div>
          </el-card>
        </el-col>
      </el-row>
    </template>

  </div>
</template>

<script setup name="Index">
import { useRouter } from 'vue-router'
import { countWarnings } from '@/api/inventory/warning'
import * as echarts from 'echarts'
import { getDashboardSummary, getDashboardTrend } from '@/api/dashboard'
import usePermissionStore from '@/store/modules/permission'
import { currencyLabel, formatCurrency, normalizeCurrency } from '@/utils/currency'

const router = useRouter()
const permissionStore = usePermissionStore()
const warningCount = ref(0)

/*
 * 这一屏里凡是要跳转的地方，认的都是<b>组件路径</b>而不是路由地址。
 *
 * 菜单树一改（比如库存模块加了基础设置/库存变动/库存统计三层分组），路由地址就从
 * /inventory/warehouse 变成 /inventory/base/warehouse，写死地址的入口会全部 404。
 * 组件路径是 sys_menu.component 那一列，分组怎么调都不变，所以按它去实际路由表里
 * 反查地址；反查不到说明这个用户没被授权那张菜单，那就只显示数字、不给跳。
 */
const WARNING_RECORD = 'inventory/warningRecord/index'
const SALES_ORDER_PAGE = 'order/sales/index'
const PURCHASE_ORDER_PAGE = 'purchase/order/index'
const PRODUCTION_ORDER_PAGE = 'production/order/index'

/** 拼父子路由地址，口径与 router/installRoutes.js 的 resolveRoutePath 一致 */
function joinPath(parentPath, routePath) {
  if (!routePath) return parentPath || '/'
  if (/^https?:\/\//i.test(routePath)) return routePath
  if (routePath.startsWith('/')) return routePath.replace(/\/+/g, '/')
  return `${parentPath}/${routePath}`.replace(/\/+/g, '/')
}

/**
 * 把当前用户的路由树压成「组件路径 → 完整地址」。
 * __componentKey 是 permission store 在把组件名换成组件对象之前留下的原始值。
 */
const routePathByComponent = computed(() => {
  const map = {}
  const walk = (routes, parentPath) => {
    (routes || []).forEach(route => {
      const path = joinPath(parentPath, route.path)
      // 同一个组件被多个菜单复用时（如 inventory/asset/index）只认第一个，
      // 首页用到的这几个组件都是一对一的，不受影响
      if (route.__componentKey && !map[route.__componentKey]) map[route.__componentKey] = path
      walk(route.children, path)
    })
  }
  walk(permissionStore.sidebarRouters, '')
  return map
})

/**
 * 按组件路径反查一个可以直接丢给 router.push 的目标；没授权那张菜单时返回 null。
 *
 * @param query 带过去的筛选条件，落地页要认这些参数并把筛选条显示出来
 */
function routeTo(component, query) {
  const path = routePathByComponent.value[component]
  if (!path) return null
  return query ? { path, query } : path
}

const warningPath = computed(() => routePathByComponent.value[WARNING_RECORD])

/** path 可以是地址串，也可以是 { path, query }——router.push 两种都收 */
function goTarget(path) {
  router.push(path)
}

countWarnings().then(res => { warningCount.value = Number(res.data || 0) }).catch(() => {})

/* ==========================================================================
 * 经营看板
 * ========================================================================== */

const TREND_MONTHS = 6

const dashboardVisible = ref(false)
const summary = ref(null)
const stockChartRef = ref(null)
const financeChartRef = ref(null)

let stockChart = null
let financeChart = null

/** 金额压成「万/亿」，卡片位置就那么宽，整数位一多就撑破了 */
function compactAmount(value) {
  const n = Number(value || 0)
  if (!isFinite(n)) return '0.00'
  const abs = Math.abs(n)
  if (abs >= 1e8) return (n / 1e8).toFixed(2)
  if (abs >= 1e4) return (n / 1e4).toFixed(2)
  return n.toFixed(2)
}

/**
 * 单位跟着币种走：人民币照旧「万元/亿元」，其他币种用 ISO 代码（万 USD）。
 *
 * 不给非人民币也写「元」：那会让一张 $12,345 的卡片读成一万两千多块钱。
 */
function compactUnit(value, currency) {
  const abs = Math.abs(Number(value || 0))
  const scale = abs >= 1e8 ? '亿' : (abs >= 1e4 ? '万' : '')
  const code = normalizeCurrency(currency)
  if (code === 'CNY') return ' ' + scale + '元'
  return scale ? ` ${scale} ${code}` : ` ${code}`
}

/** 精确值，挂在 title 上给鼠标悬停看——压缩过的数字不该是唯一的呈现 */
function exactAmount(value, currency) {
  return formatCurrency(value, currency)
}

function amountStat(key, label, value, color, currency) {
  return {
    key, label, color,
    value: compactAmount(value),
    unit: compactUnit(value, currency),
    title: exactAmount(value, currency)
  }
}

/** 没有数的那几张卡用常规文字色，免得一排 0 都在喊「注意我」 */
const MUTED = 'var(--el-text-color-regular)'

/**
 * 「待我处理」里各个队列点进去落到哪个页面。
 *
 * 键是队列的 docType（库存单据用自己的类型码，另两条是后端合成的
 * SALES_ORDER / PURCHASE_ORDER），值里的 component 是<b>组件路径</b>，理由见文件上方。
 *
 * 没列在这里的库存类型（生产四类、销售退货入库、借出归还、送修等）都由各自的业务模块
 * 建单并即时审核，没有独立的审核页面，所以在浮层里只报数、不给跳。
 */
const PENDING_ROUTES = {
  RK: { component: 'inventory/inbound/index' },
  CK: { component: 'inventory/outbound/index', docType: 'CK' },
  LLCK: { component: 'inventory/outbound/index', docType: 'LLCK' },
  OO: { component: 'inventory/outbound/index', docType: 'OO' },
  DB: { component: 'inventory/transfer/index' },
  // 销售订单和采购订单不是库存单据，落地页的筛选参数也各是各的一套
  SALES_ORDER: { component: SALES_ORDER_PAGE, salesPending: true },
  PURCHASE_ORDER: { component: PURCHASE_ORDER_PAGE, purchaseStatus: 'submitted' }
}

const pendingDocs = computed(() => (summary.value && summary.value.pendingDocs) || [])

/**
 * 这一行能不能跳，能跳的话带什么筛选条件过去。
 *
 * 三条队列的落地页参数互不相同：库存单据用 confirmState + docType，采购订单用它自己的
 * 英文 status，销售订单除了状态还得带上月份——那个列表是按月查的，不带月份会落在当月的
 * 空列表上，而待审核的那张单可能是上个月建的（docMonth 就是后端为此给的）。
 */
function pendingRoute(row) {
  const target = PENDING_ROUTES[row.docType]
  if (!target) return null
  const path = routePathByComponent.value[target.component]
  if (!path) return null
  if (target.salesPending) {
    const query = { orderState: '待审核' }
    // 后端给的是 202609 这种，页面上的月份选择器要 2026-09
    if (row.docMonth) query.month = row.docMonth.slice(0, 4) + '-' + row.docMonth.slice(4)
    return { path, query }
  }
  if (target.purchaseStatus) return { path, query: { status: target.purchaseStatus } }
  const query = { confirmState: '待审核' }
  if (target.docType) query.docType = target.docType
  return { path, query }
}

const hasUnroutablePending = computed(() => pendingDocs.value.some(row => !pendingRoute(row)))

function goPending(row) {
  const target = pendingRoute(row)
  if (target) router.push(target)
}

/*
 * 下面这几个可见性都是后端说了算：无权的那块它压根没查，前端也就不该画。
 * 画一张写着 0 的卡片，等于告诉用户「这个月一单都没有」，那是一件假事。
 */
const financeVisible = computed(() => !!(summary.value && summary.value.financeVisible))
const costVisible = computed(() => !!(summary.value && summary.value.costVisible))

/** 本月销售额，一个币种一行，金额大的在前（SQL 已排过，这里只是不依赖它） */
const salesRows = computed(() => {
  const rows = (summary.value && summary.value.salesAmounts) || []
  return [...rows].sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0))
})

/** 客单价。一单都没有时不做除法，返回 0 */
function avgTicket(row) {
  const count = Number(row.orderCount || 0)
  return count > 0 ? Number(row.amount || 0) / count : 0
}

/** 本月有订单、但「汇率设置」里还没录汇率的币种 */
const salesRateMissing = computed(() => (summary.value && summary.value.salesRateMissing) || [])

/**
 * 本月销售额那张卡。
 *
 * 只有人民币时照旧：卡上是金额，副行是客单价。
 * 有外币时卡上放「折合人民币合计」（后端按「汇率设置」里今天生效的汇率折算），
 * 点开浮层看各币种原币金额和汇率。没录汇率的币种不进合计，副行标红提醒——
 * 否则用户会把偏小的合计当成全部。
 * 外币全都没录汇率时合计是 0，这时退回老样子，只显示金额最大的那个币种。
 */
function salesAmountCard() {
  const rows = salesRows.value
  if (!rows.length) return null
  const top = rows[0]
  const missing = salesRateMissing.value
  if (rows.length === 1 && normalizeCurrency(top.currency) === 'CNY') {
    const card = amountStat('salesAmount', '本月销售额', top.amount, '#409EFF', top.currency)
    card.sub = `客单价 ${formatCurrency(avgTicket(top), top.currency)}`
    return card
  }
  if (missing.length === rows.length) {
    const card = amountStat('salesAmount', '本月销售额', top.amount, '#409EFF', top.currency)
    card.sub = '外币未录汇率，没法合计，点开看'
    card.subDanger = true
    card.popover = 'sales'
    return card
  }
  const card = amountStat('salesAmount', '本月销售额（折合人民币）', summary.value.salesAmountBase, '#409EFF', 'CNY')
  card.sub = missing.length
    ? `${missing.join('、')} 未录汇率，没算进来`
    : `${rows.length} 个币种，按今日汇率折算`
  card.subDanger = missing.length > 0
  card.popover = 'sales'
  return card
}

const stats = computed(() => {
  const d = summary.value
  if (!d) return []
  const cards = []

  // 一、今天该动手的
  const pending = Number(d.pendingDocCount || 0)
  cards.push({
    key: 'pending', label: '待我处理', value: pending, unit: ' 单',
    color: pending > 0 ? '#E6A23C' : MUTED,
    title: '库存单据、销售订单、采购订单三处待审核的合计，近 12 个月内',
    // 一张待审核都没有时不给浮层，免得点开是个空盒子
    popover: pendingDocs.value.length > 0 ? 'pending' : null
  })
  const warning = Number(d.warningCount || 0)
  cards.push({
    key: 'warning', label: '未处理预警', value: warning, unit: ' 条',
    color: warning > 0 ? '#F56C6C' : MUTED, path: warningPath.value
  })
  if (d.purchaseVisible) {
    const receiving = Number(d.purchaseReceivingCount || 0)
    const overdue = Number(d.purchaseOverdueCount || 0)
    cards.push({
      key: 'purchaseReceiving', label: '待收货采购', value: receiving, unit: ' 单',
      title: '已审核和部分到货的采购单，货还没收齐',
      color: receiving > 0 ? '#409EFF' : MUTED,
      // 逾期数是待收货的子集，措辞上写明「其中」，别让人加成两笔
      sub: overdue > 0 ? `其中 ${overdue} 单已过预计到货日` : '',
      subDanger: true,
      path: routeTo(PURCHASE_ORDER_PAGE, { status: 'pending_receive' })
    })
  }
  if (d.productionVisible) {
    const active = Number(d.productionActiveCount || 0)
    const overdue = Number(d.productionOverdueCount || 0)
    cards.push({
      key: 'production', label: '在产工单', value: active, unit: ' 单',
      title: '待生产、待领料、生产中三种状态的工单',
      color: active > 0 ? '#409EFF' : MUTED,
      sub: overdue > 0 ? `其中 ${overdue} 单已过计划生产日` : '',
      subDanger: true,
      path: routeTo(PRODUCTION_ORDER_PAGE, { status: '在产' })
    })
  }

  // 二、家底
  if (d.costVisible) cards.push(amountStat('stock', '在库货值', d.stockAmount, '#409EFF'))
  cards.push({ key: 'sku', label: '在库商品', value: Number(d.skuCount || 0), unit: ' 种', color: '#b37feb' })

  // 三、本月流水
  if (d.saleAmountVisible) {
    const card = salesAmountCard()
    if (card) cards.push(card)
  }
  if (d.salesVisible) {
    cards.push({
      key: 'salesCount', label: '本月订单', value: Number(d.salesOrderCount || 0), unit: ' 单',
      title: '本月审核通过且未作废的订单张数，各币种合计',
      color: '#b37feb'
    })
  }
  if (d.costVisible) {
    cards.push(amountStat('inbound', '本月入库', d.inboundAmount, '#67C23A'))
    cards.push(amountStat('outbound', '本月出库', d.outboundAmount, '#409EFF'))
  }
  if (d.financeVisible) {
    cards.push(amountStat('income', '本月收入', d.incomeAmount, '#67C23A'))
    cards.push(amountStat('expense', '本月支出', d.expenseAmount, '#F56C6C'))
  }
  return cards
})

/** 图表颜色跟着 Element 的主题变量走，换肤后不会剩下一堆写死的灰 */
function chartTheme() {
  const style = getComputedStyle(document.documentElement)
  return {
    label: style.getPropertyValue('--el-text-color-secondary').trim() || '#909399',
    line: style.getPropertyValue('--el-border-color-lighter').trim() || '#EBEEF5'
  }
}

function buildOption(months, series) {
  const theme = chartTheme()
  return {
    tooltip: { trigger: 'axis', valueFormatter: value => exactAmount(value) },
    legend: { top: 0, textStyle: { color: theme.label } },
    grid: { top: 36, left: 8, right: 16, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, data: months,
      // 首尾两个标签贴着栅格边对齐，不居中压在刻度上。
      // 默认居中时「2026-09」有一半落在栅格外面，被容器裁成「2026-0」——
      // containLabel 只管纵向撑开 x 轴标签的高度，管不了最右那个的横向溢出。
      // hideOverlap：窄屏时宁可少画几个标签，也不要糊成一团。
      // 6 个「2026-09」在半宽卡片里刚好挨着，再窄一点就叠了。
      axisLabel: { color: theme.label, alignMinLabel: 'left', alignMaxLabel: 'right', hideOverlap: true },
      axisLine: { lineStyle: { color: theme.line } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: theme.label, formatter: value => compactAmount(value) + compactUnit(value) },
      splitLine: { lineStyle: { color: theme.line } }
    },
    series
  }
}

function renderCharts(points) {
  const months = points.map(item => item.month.slice(0, 4) + '-' + item.month.slice(4))
  const line = (name, key, color) => ({
    name, type: 'line', smooth: true, showSymbol: points.length <= 12,
    itemStyle: { color }, areaStyle: { opacity: 0.08, color },
    data: points.map(item => Number(item[key] || 0))
  })
  // 两张图各自可能因为缺权限而没渲染容器，谁都不能假定对方存在
  if (stockChart) {
    stockChart.setOption(buildOption(months, [
      line('入库', 'inboundAmount', '#67C23A'),
      line('出库', 'outboundAmount', '#409EFF')
    ]))
  }
  if (financeChart) {
    financeChart.setOption(buildOption(months, [
      line('收入', 'incomeAmount', '#67C23A'),
      line('支出', 'expenseAmount', '#F56C6C')
    ]))
  }
}

function handleResize() {
  stockChart && stockChart.resize()
  financeChart && financeChart.resize()
}

/**
 * 拉看板数据。
 *
 * 拿不到就整块不渲染，而不是显示一排 0：
 *   - 403（没有 dashboard:overview:query 权限）——和预警角标一样静默吞掉；
 *   - month 为空（管理员还没选商家）——后端此时返回的是空对象，不是这个商家真的没数据。
 * 这两种情况下画一排「在库货值 0.00」，等于在告诉用户一件假事。
 */
async function loadDashboard() {
  try {
    const res = await getDashboardSummary()
    if (!res.data || !res.data.month) return
    summary.value = res.data
    dashboardVisible.value = true
  } catch (e) {
    // 没有权限，保持不渲染
  }
}

onMounted(async () => {
  await loadDashboard()
  if (!dashboardVisible.value) return
  // 等 v-if 那块真的挂上去，否则 echarts 拿到的是 0 宽容器
  await nextTick()
  // 出入库图缺 price:cost、收支图缺财务权限时，对应的容器根本没渲染。
  // 两张图互相独立，不能因为第一张不在就整段 return——只有仓库岗位的人会两张都没有。
  if (stockChartRef.value) stockChart = echarts.init(stockChartRef.value)
  if (financeChartRef.value) financeChart = echarts.init(financeChartRef.value)
  if (!stockChart && !financeChart) return
  window.addEventListener('resize', handleResize)
  try {
    const res = await getDashboardTrend(TREND_MONTHS)
    renderCharts(res.data || [])
  } catch (e) {
    // 卡片已经出来了，趋势拿不到就留一张空图，不影响上面那一排
  }
})

// echarts 实例内部持有 canvas 和 DOM，和 window 上的监听一样必须手动释放
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  stockChart && stockChart.dispose()
  financeChart && financeChart.dispose()
  stockChart = null
  financeChart = null
})
</script>

<style scoped lang="scss">
.home {
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", Arial, sans-serif;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
  min-height: calc(100vh - 84px);
    padding: 24px;

  // 欢迎区域
  .welcome-section {
    margin-bottom: 20px;
  }

  .welcome-card {
    background: linear-gradient(135deg, #409EFF 0%, #337ecc 100%);
    border-radius: 12px;
    padding: 32px 40px;
    color: #fff;
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      right: -40px;
      top: -40px;
      width: 200px;
      height: 200px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 50%;
    }

    &::after {
      content: '';
      position: absolute;
      right: 60px;
      bottom: -60px;
      width: 160px;
      height: 160px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 50%;
    }

    .welcome-text {
      position: relative;
      z-index: 1;
    }

    h1 {
      font-size: 26px;
      font-weight: 600;
      margin: 0 0 8px;
    }

    .subtitle {
      font-size: 16px;
      opacity: 0.9;
      margin: 0 0 16px;
      font-weight: 300;
    }

    .desc {
      font-size: 14px;
      opacity: 0.8;
      margin: 0;
      max-width: 600px;
      line-height: 1.8;
    }

    .warning-badge {
      margin-top: 16px;
    }
  }

  // 指标卡片
  .stat-section {
    margin-bottom: 20px;

    .el-col {
      margin-bottom: 16px;
    }
  }

  .stat-card {
    background: var(--el-bg-color-overlay);
    border-radius: 10px;
    padding: 18px 20px;
    height: 100%;
    transition: all 0.3s ease;

    &.clickable {
      cursor: pointer;

      &:hover {
        transform: translateY(-2px);
        box-shadow: var(--el-box-shadow-light);
      }
    }

    .stat-label {
      font-size: 13px;
      color: var(--el-text-color-secondary);
      margin-bottom: 10px;
    }

    .stat-value {
      font-size: 24px;
      font-weight: 600;
      line-height: 1.2;
      // 等宽字形，一排卡片的小数点才对得齐
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .stat-unit {
      font-size: 13px;
      font-weight: 400;
      color: var(--el-text-color-secondary);
    }

    // 副行：逾期张数或客单价。只有部分卡片有，没有的不占位——
    // 同一行卡片靠 el-row 的 flex 拉平高度，缺这一行不会长短不齐
    .stat-sub {
      margin-top: 8px;
      font-size: 12px;
      line-height: 1.4;
      color: var(--el-text-color-secondary);

      &.danger {
        color: var(--el-color-danger);
      }
    }
  }

  // 趋势图
  .chart-section {
    margin-bottom: 20px;

    .el-col {
      margin-bottom: 16px;
    }
  }

  .chart-card {
    border-radius: 10px;
    border: none;

    .chart-title {
      font-size: 15px;
      font-weight: 600;
    }

    // 高度写死：echarts 要一个确定高度的容器，父级用百分比它会算出 0
    .chart-body {
      height: 260px;
    }
  }

}
</style>

<style>
/*
  popover 的内容是 teleport 到 body 上的，scoped 样式够不着，只能写全局。
  类名统一带 pending- 前缀并限定在 .pending-popover 里，不会影响别处。
*/
.pending-popover .pending-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  /* 默认是不可跳转的样式，能跳的在下面覆盖 */
  color: var(--el-text-color-placeholder);
}

.pending-popover .pending-row.linkable {
  cursor: pointer;
  color: var(--el-text-color-primary);
}

/*
  币种那份浮层每行两层：上层币种 + 金额，下层单数和客单价。
  外层的 flex 是给「左名称右数字」那种一行式用的，这里要改成纵向堆叠。
*/
.pending-popover .pending-row.sales-row {
  display: block;
  color: var(--el-text-color-primary);
}

.pending-popover .sales-row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.pending-popover .sales-row-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pending-popover .sales-row-sub .rate-missing {
  color: var(--el-color-danger);
}

.pending-popover .pending-row.linkable:hover {
  background: var(--el-fill-color-light);
}

.pending-popover .pending-count {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.pending-popover .pending-hint {
  margin-top: 6px;
  padding: 6px 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
}
</style>

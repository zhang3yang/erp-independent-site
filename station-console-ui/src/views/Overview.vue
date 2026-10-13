<template>
  <div class="page">
    <el-alert type="info" show-icon :closable="false" class="mb16"
      :title="$t('overview.lead.title')"
      :description="$t('overview.lead.desc')" />

    <el-row :gutter="16" class="mb16">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <div class="stat-num">{{ stat.published }}</div>
          <div class="stat-label">{{ $t('overview.stat.published') }}</div>
          <el-button link type="primary" size="small" @click="go('goods')">{{ $t('overview.stat.goManage') }}</el-button>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <div class="stat-num">{{ stat.draft }}</div>
          <div class="stat-label">{{ $t('overview.stat.draft') }}</div>
          <span class="stat-hint">{{ $t('overview.stat.draftHint') }}</span>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <div class="stat-num" :class="{ 'stat-warn': stat.pending > 0 }">{{ stat.pending }}</div>
          <div class="stat-label">{{ $t('overview.stat.pending') }}</div>
          <el-button link type="primary" size="small" @click="go('inquiry')">{{ $t('overview.stat.goFollow') }}</el-button>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <div class="stat-num" :class="expireClass">{{ expireNum }}</div>
          <div class="stat-label">{{ expireLabel }}</div>
          <span class="stat-hint">{{ site.expireDate || $t('overview.stat.noExpire') }}</span>
        </el-card>
      </el-col>
    </el-row>

    <!--
      ================================================================
      前台浏览量
      ================================================================

      ⚠ 整块用 v-if="canQueryStat" 包住。没有 site:stat:query 就不发那个请求——
        发了会吃一个 403，request 拦截器把它弹成「当前操作没有权限」，
        一句运营既看不懂也不需要处理的报错。这一条在 Files.vue 和存储卡上都踩过。

      老库要跑 sql/station/migration/2026-09-08-pageview.sql 才会给 operator
      加上这个权限。不跑的话运营这块是空的（不是报错）。
    -->
    <el-card v-if="canQueryStat" shadow="never" class="mb16">
      <template #header>
        <div class="card-head">
          <b>{{ $t('overview.pv.title') }}</b>
          <span class="pv-head-hint">
            <template v-if="pv.enabled">
              {{ $t('overview.pv.range', { top: pv.topDays, retain: pv.retainDays }) }}
            </template>
            <template v-else>{{ $t('overview.pv.off') }}</template>
          </span>
        </div>
      </template>

      <!--
        ⚠ 「没启用」和「加载失败」是两种状态，必须分开显示。
          后端在 enabled=false 时照样回 200，就是为了让这里能分辨——
          混成一句「加载失败」的话，有人会去查一个根本没坏的东西。
      -->
      <el-alert v-if="!pv.enabled" type="info" show-icon :closable="false"
        :title="$t('overview.pv.offTitle')"
        :description="$t('overview.pv.offDesc')" />

      <template v-else>
        <el-row :gutter="16" class="mb16">
          <el-col :xs="12" :sm="6">
            <div class="pv-num">{{ pv.today.pvHuman }}</div>
            <div class="pv-label">{{ $t('overview.pv.todayPv') }}</div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="pv-num">{{ pv.today.uv }}</div>
            <div class="pv-label">{{ $t('overview.pv.todayUv') }}</div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="pv-num pv-num-quiet">{{ pv.yesterday.pvHuman }}</div>
            <div class="pv-label">{{ $t('overview.pv.yesterdayPv') }}</div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="pv-num pv-num-quiet">{{ pv.yesterday.uv }}</div>
            <div class="pv-label">{{ $t('overview.pv.yesterdayUv') }}</div>
          </el-col>
        </el-row>

        <!--
          ⚠ 上面那四个数字是**排除爬虫之后**的（pvHuman，不是 pv）。
            爬虫占比单独说一句：新站的原始流量里爬虫常常占九成，
            不说清楚的话运营会觉得「怎么才这么点」。
        -->
        <div v-if="botRatio !== null" class="pv-bot-note">
          {{ $t('overview.pv.botNote', { pv: pv.today.pv, ratio: botRatio }) }}
          <span class="pv-bot-hint">{{ $t('overview.pv.botHint') }}</span>
        </div>

        <!--
          14 天趋势。没有图表库，用 CSS 柱子——为一个 14 根柱子的图引一个
          几百 KB 的 echarts 不值得。

          ⚠ 后端已经把没数据的日子补成 0 了。补零这件事必须在有数据的一侧做：
            只画库里有的那几天，折线会把「周末没人来」画成一条斜着连过去的线，
            看起来像「量在稳步上升」。
        -->
        <div class="pv-trend">
          <div v-for="d in pv.trend" :key="d.date" class="pv-bar-col"
            :title="$t('overview.pv.barTip', { date: d.date, pv: d.pvHuman, uv: d.uv })">
            <div class="pv-bar" :style="{ height: barHeight(d.pvHuman) }"></div>
            <div class="pv-bar-day">{{ d.date.slice(5) }}</div>
          </div>
        </div>

        <!--
          来访质量。三个数：来了几次、平均看几页、看一眼就走的比例。

          ⚠ 口径写在后端 SitePageViewMapper.selectEngagement 的注释里，
            这里只说界面上要让运营知道的那一条：**只算已经结束的来访**。
            不这么算的话，一个刚落地十秒、正在看第一页的人会被算成「跳出」，
            跳出率虚高——而那个数字看起来只像「页面有问题」。
        -->
        <div class="pv-section-head">
          <b>{{ $t('overview.pv.quality') }}</b>
          <span class="pv-head-hint">
            {{ $t('overview.pv.qualityHint', { minutes: pv.engagement.sessionMinutes }) }}
          </span>
        </div>
        <el-row :gutter="16" class="mb16">
          <el-col :xs="8" :sm="8">
            <div class="pv-num pv-num-sm">{{ pv.engagement.sessions }}</div>
            <div class="pv-label">{{ $t('overview.pv.sessions') }}</div>
          </el-col>
          <el-col :xs="8" :sm="8">
            <!-- 没数据时显示「—」而不是 0：后端在分母为 0 时回的是 null -->
            <div class="pv-num pv-num-sm">{{ pv.engagement.pagesPerSession ?? '—' }}</div>
            <div class="pv-label">{{ $t('overview.pv.pagesPerSession') }}</div>
          </el-col>
          <el-col :xs="8" :sm="8">
            <div class="pv-num pv-num-sm" :class="{ 'stat-warn': bounceHigh }">
              {{ pv.engagement.bounceRate === null || pv.engagement.bounceRate === undefined
                  ? '—' : pv.engagement.bounceRate + '%' }}
            </div>
            <div class="pv-label">{{ $t('overview.pv.bounce') }}</div>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="mb16">
          <el-col :xs="24" :sm="12">
            <div class="pv-section-head">
              <b>{{ $t('overview.pv.device') }}</b>
              <span class="pv-head-hint">{{ $t('overview.pv.deviceHint') }}</span>
            </div>
            <el-table :data="pv.devices" size="small" :empty-text="$t('common.noData')">
              <el-table-column :label="$t('overview.pv.device')" min-width="120">
                <template #default="{ row }">{{ deviceLabel(row.device) }}</template>
              </el-table-column>
              <el-table-column prop="pv" :label="$t('overview.pv.colPv')" width="80" align="right" />
              <el-table-column prop="uv" :label="$t('overview.pv.colUv')" width="80" align="right" />
            </el-table>
          </el-col>
          <el-col :xs="24" :sm="12">
            <div class="pv-section-head"><b>{{ $t('overview.pv.browser') }}</b></div>
            <el-table :data="pv.browsers" size="small" :empty-text="$t('common.noData')">
              <el-table-column prop="browser" :label="$t('overview.pv.browser')" min-width="120" />
              <el-table-column prop="pv" :label="$t('overview.pv.colPv')" width="80" align="right" />
              <el-table-column prop="uv" :label="$t('overview.pv.colUv')" width="80" align="right" />
            </el-table>
          </el-col>
        </el-row>

        <!--
          国家分布。数据来自服务器上的离线 IP 对照表，不调任何外部服务。

          ⚠ 空表的含义有两种，都不是「没人来」：
            · u_s_ip_country 还没导数据（建表和导数据是两步）；
            · 或者这段时间的访客 IP 都查不到归属。
          所以空态文案要说清这件事，不能只写「没有数据」。
        -->
        <div class="pv-section-head">
          <b>{{ $t('overview.pv.country') }}</b>
          <span class="pv-head-hint">{{ $t('overview.pv.countryHint') }}</span>
        </div>
        <el-table :data="pv.countries" size="small" class="mb16"
          :empty-text="$t('overview.pv.countryEmpty')">
          <el-table-column :label="$t('overview.pv.country')" min-width="160">
            <template #default="{ row }">
              {{ countryLabel(row.country) }}
              <span class="pv-head-hint">{{ row.country }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="pv" :label="$t('overview.pv.colPv')" width="90" align="right" />
          <el-table-column prop="uv" :label="$t('overview.pv.colUv')" width="90" align="right" />
        </el-table>

        <!--
          ================================================================
          商品排行。这一张表是整个功能存在的理由
          ================================================================

          它把浏览量和询盘数、成单数、标价放在同一行——接谷歌统计做不到这件事：
          那边的浏览数据在别人的服务器上，没法和这个库里的询盘表、订单表放一起查。

          怎么读：
            · 浏览高、询盘 0  → 价格 / 起订量 / 详情页有问题，人看完就走；
            · 浏览低、转化好  → 这个商品是好的，只是没人找得到它，该加曝光。
        -->
        <div class="pv-section-head">
          <b>{{ $t('overview.pv.topGoods') }}</b>
          <span class="pv-head-hint">
            {{ $t('overview.pv.topGoodsHint') }}
          </span>
        </div>
        <el-table :data="pv.topGoods" size="small" class="mb16" :empty-text="$t('overview.pv.topGoodsEmpty')">
          <el-table-column prop="goodscode" :label="$t('overview.pv.goodsCode')" width="130" />
          <el-table-column prop="goodsname" :label="$t('overview.pv.goodsName')" min-width="160" show-overflow-tooltip />
          <el-table-column prop="pv" :label="$t('overview.pv.colPv')" width="80" align="right" />
          <el-table-column prop="uv" :label="$t('overview.pv.colUv')" width="80" align="right" />
          <el-table-column :label="$t('overview.pv.colInquiry')" width="80" align="right">
            <template #default="{ row }">
              <!-- 有浏览没询盘：标出来。这正是这张表要让人看见的那一格 -->
              <span :class="{ 'pv-zero': row.pv >= 20 && row.inquirycount === 0 }">
                {{ row.inquirycount }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="ordercount" :label="$t('overview.pv.colOrder')" width="80" align="right" />
          <el-table-column :label="$t('overview.pv.colPrice')" width="120" align="right">
            <template #default="{ row }">
              <!-- 0 表示不标价、只显示 Inquiry（u_s_sitegoods.price 那一列的约定） -->
              <span v-if="Number(row.price) > 0">{{ row.price }}</span>
              <span v-else class="pv-head-hint">{{ $t('overview.pv.priceOnRequest') }}</span>
            </template>
          </el-table-column>
          <!--
            ⚠ 这一列的值是库里的中文串（'已上架'/'草稿'），这里只翻列头不翻值：
              它是一个数据值不是界面词，而商品页的筛选下拉送的也是它。
          -->
          <el-table-column prop="publishstatus" :label="$t('common.status')" width="90" />
        </el-table>

        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <div class="pv-section-head"><b>{{ $t('overview.pv.referrer') }}</b></div>
            <!--
              ⚠ 自己站的域名会出现在这里（站内跳转）。SQL 那边刻意不过滤：
                过滤要写死域名，而这个站有四个子域、还随环境变。在这里标一下就行。
            -->
            <el-table :data="pv.referrers" size="small" :empty-text="$t('overview.pv.referrerEmpty')">
              <el-table-column prop="host" :label="$t('overview.pv.referrer')" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.host }}
                  <el-tag v-if="isSelfHost(row.host)" size="small" type="info">{{ $t('overview.pv.self') }}</el-tag>
                  <el-tag v-else-if="row.host === '(direct)'" size="small" type="info">{{ $t('overview.pv.direct') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="pv" :label="$t('overview.pv.colPv')" width="80" align="right" />
              <el-table-column prop="uv" :label="$t('overview.pv.colUv')" width="80" align="right" />
            </el-table>
          </el-col>
          <el-col :xs="24" :sm="12">
            <div class="pv-section-head"><b>{{ $t('overview.pv.pageType') }}</b></div>
            <el-table :data="pv.typeBreakdown" size="small" :empty-text="$t('common.noData')">
              <el-table-column :label="$t('overview.pv.page')" min-width="140">
                <template #default="{ row }">{{ typeLabel(row.pagetype) }}</template>
              </el-table-column>
              <el-table-column prop="pvhuman" :label="$t('overview.pv.colPv')" width="80" align="right" />
            </el-table>
          </el-col>
        </el-row>
      </template>
    </el-card>

    <el-row :gutter="16">
      <!-- 右边那一列撤了（见本行下方那段注释），这一列吃满整行 -->
      <el-col :span="24">
        <el-card shadow="never">
          <!--
            ⚠ 这一块以前叫「接口对接」，摆着一个只读的 siteKey 和「对外接口」开关。

            两样都没了（docs/独立站与ERP的边界.md §7.2）：
              · sitekey —— 站点前端拿它调 ERP 的开放接口限定数据范围用。现在前台取的是
                同一台机器上的后端，那个后端只连本站的库，没有范围要限定；
              · apienabled —— 同上，开放接口不再由 ERP 提供。

            换成了「站点设置」：语言、币种、前台开关、询盘通知邮箱——它们从 ERP 的
            站点档案上搬过来了（§7.2），既然搬来了就该能在这儿改。
          -->
          <template #header>
            <div class="card-head">
              <b>{{ $t('overview.set.title') }}</b>
              <!--
                ⚠ 这里原来是 `v-hasPermi="['site:station:edit']"`，而那个指令会把元素
                  **从 DOM 里删掉**。后果比装修页更绕：进编辑态的唯一入口就是这个「修改」，
                  它一消失，「保存」和「取消」（v-else 分支）也永远出不来——
                  页面上一个按钮都没有，也没有任何说明，看起来像功能没做。

                  出厂的 operator 角色刻意不含这个权限（见建库脚本里角色表那条备注），
                  而从 ERP 免密进来的人默认就是 operator，所以这是常态、不是边角情况。
                  改成置灰 + 一句 tooltip 说清为什么。
              -->
              <el-tooltip v-if="!editing && !canEdit" placement="top"
                :content="$t('overview.set.noEditPermi')">
                <el-button link type="primary" size="small" disabled>{{ $t('common.edit') }}</el-button>
              </el-tooltip>
              <el-button v-else-if="!editing" link type="primary" size="small"
                @click="startEdit">{{ $t('common.edit') }}</el-button>
              <span v-else>
                <el-button link size="small" @click="editing = false">{{ $t('common.cancel') }}</el-button>
                <el-button link type="primary" size="small" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
              </span>
            </div>
          </template>
          <el-form label-width="110px" class="info-form">
            <!--
              域名是从右边那张「登记信息」卡搬过来的。原来它在那儿、标着「在 ERP 里改」，
              而那句话拆分之后就不成立了：ERP 的域名存在它自己库的 u_s_site，和本站的
              u_s_siteinfo 之间没有任何同步通道（ERP 侧全文只有注释提到这张表，没有一处写它）。
              在 ERP 填多少次这边也不会变，而页面还指着那条走不通的路。
              这个值现在只有本站一份，就该在本站改。
            -->
            <el-form-item :label="$t('overview.set.domain')">
              <el-input v-if="editing" v-model="form.domain" maxlength="200" style="width:100%"
                :placeholder="$t('overview.set.domainPlaceholder')" />
              <template v-else>
                <el-link v-if="frontUrl" type="primary" :href="frontUrl" target="_blank" rel="noopener">
                  {{ site.domain || frontUrl }}
                </el-link>
                <span v-else class="text-muted">{{ $t('overview.set.domainEmpty') }}</span>
              </template>
              <!--
                这一栏空着不是「少填了个展示字段」，是真的有东西坏掉，而且坏得没有症状：
                买家点注册，前台看起来一切正常，只是那封信永远不到。所以要在这儿说。
              -->
              <div v-if="!site.domain" class="hint hint-warn" v-html="$t('overview.set.domainBroken')"></div>
              <div class="hint" v-html="$t('overview.set.domainHint')"></div>
            </el-form-item>
            <el-form-item :label="$t('overview.set.status')">
              <!--
                ⚠ value 一律是库里那个中文串，只有 label 跟着界面语言走：
                  u_s_siteinfo.status 存的就是这三个中文字符串，ERP、SQL 脚本、
                  后端 isPublicOpen() 读的都是它。把 value 也翻了，保存下去就是一个
                  谁都不认识的状态——前台会当成「不是运行中」直接关站。
              -->
              <el-select v-if="editing" v-model="form.status" style="width:160px">
                <el-option :label="$t('site.status.building')" value="建设中" />
                <el-option :label="$t('site.status.running')" value="运行中" />
                <el-option :label="$t('site.status.disabled')" value="已停用" />
              </el-select>
              <el-tag v-else :type="site.status === '运行中' ? 'success' : 'info'" size="small">{{ statusText(site.status) }}</el-tag>
              <div class="hint" v-html="$t('overview.set.statusHint')"></div>
            </el-form-item>
            <el-form-item :label="$t('overview.set.langs')">
              <el-select v-if="editing" v-model="form.langList" multiple filterable allow-create
                default-first-option :placeholder="$t('overview.set.langsPlaceholder')" style="width:100%">
                <el-option v-for="item in langOptions" :key="item" :label="item" :value="item" />
              </el-select>
              <template v-else>
                <el-tag v-for="lang in langList" :key="lang" size="small" effect="plain" class="mr4">{{ lang }}</el-tag>
                <span v-if="!langList.length" class="text-muted">{{ $t('overview.set.langsEmpty') }}</span>
              </template>
              <!--
                越界语言必须当场说，不能等保存后靠一条 toast 补救：那时人已经离开这个字段了。
                措辞说的是「前台没有文案表」而不是「不合法」——运营要知道的是缺什么、能不能补。
              -->
              <div v-if="editing && unsupportedLangs.length" class="hint hint-warn">
                {{ $t('overview.set.langsUnsupported', { langs: unsupportedLangs.join('、') }) }}
              </div>
              <!-- 文案按语言分开存：站点语言是 ja 而文案填在 en 下面，站上就会回退显示商品名称 -->
              <div class="hint">{{ $t('overview.set.langsHint') }}</div>
            </el-form-item>

            <!--
              ================================================================
              管理台语言。2026-09-22 加的
              ================================================================

              ⚠ 它**紧挨着上面那个「站点语言」是有意的，也正因为如此必须把区别说死**：
                两栏都叫「语言」，一个管前台对买家开放哪几种，一个管这一屏自己画成什么样。
                看混的表现是「我把站点语言设成了法语，后台怎么还是中文」——
                所以两条 hint 第一句都在说「这个不是那个」。

              为什么会有这一栏：法国站（esvafit）的站长看不懂中文，而我们的业务员要看中文，
              两个人用的是同一个管理台。所以这里选的是**默认值**，个人的选择在右上角，
              存在各自的浏览器里。两层的分工见 src/i18n/index.js。
            -->
            <el-form-item :label="$t('overview.set.consoleLang')">
              <el-select v-if="editing" v-model="form.consoleLang" style="width:220px">
                <!-- label 是各语言自己的写法（中文 / English / Français），理由同顶栏那个下拉 -->
                <el-option v-for="item in consoleLangOptions" :key="item.code"
                  :label="item.label" :value="item.code" />
              </el-select>
              <template v-else>
                <el-tag size="small" effect="plain">{{ consoleLangLabel }}</el-tag>
                <!--
                  ⚠ 这个「站点默认」的缀词不是装饰：不写的话，一个标着「管理台语言」、
                    值是 Français 的字段摆在一屏中文界面上，看着就是坏的。
                -->
                <span class="hint ml8">{{ $t('overview.set.consoleLangTag') }}</span>
              </template>
              <div class="hint" v-html="$t('overview.set.consoleLangHint')"></div>
              <div class="hint" v-html="$t('overview.set.consoleLangHint2')"></div>
              <!--
                最后一行回答的是看这一栏的人心里那个问题：「我改了它，我自己这屏会变吗」。
                两种答案是相反的，所以必须按人分开说——说错一次，他就会以为设置没生效。
              -->
              <div v-if="localeChosen" class="hint hint-warn">
                {{ $t('overview.set.consoleLangMine', { lang: myLangLabel, site: consoleLangLabel }) }}
              </div>
              <div v-else class="hint">
                {{ $t('overview.set.consoleLangCurrent', { lang: consoleLangLabel }) }}
              </div>
            </el-form-item>
            <el-form-item :label="$t('overview.set.currency')">
              <el-select v-if="editing" v-model="form.currency" filterable style="width:220px" placeholder="USD">
                <el-option v-for="item in currencyOptions" :key="item.code"
                  :label="currencyOptionLabel(item)" :value="item.code" />
                <!--
                  存量站的 currency 可能是这六个之外的码。不塞这一项的话，打开编辑框
                  el-select 匹配不到选项、显示成空，随手一保存就把它换掉了——
                  又是一次没人察觉的静默变更。
                -->
                <el-option v-if="form.currency && !isKnownCurrency(form.currency)"
                  :label="$t('overview.set.currencyUnknown', { code: form.currency })" :value="form.currency" />
              </el-select>
              <!--
                只读态不能走 currencyLabel：它内部的 normalizeCurrency 把不认识的码
                一律当 CNY，于是一个设成 SGD 的站在这里显示「CNY（¥）」——
                页面在说一件假话。认识才带符号，不认识就把原码摆出来。
              -->
              <template v-else>
                <span v-if="currencyKnown">{{ currencyLabel(site.currency) }}</span>
                <span v-else>{{ site.currency || '—' }}</span>
              </template>
              <div v-if="site.currency && !currencyKnown" class="hint hint-warn">
                {{ $t('overview.set.currencyNoSymbol', { code: site.currency }) }}
              </div>
              <div class="hint" v-html="$t('overview.set.currencyHint')"></div>
              <div class="hint hint-warn" v-html="$t('overview.set.currencyWarn')"></div>
              <div class="hint" v-html="$t('overview.set.currencyWarn2')"></div>
            </el-form-item>

            <!--
              买家可选币种。和上面那个只差一个字母，所以标签和提示都要把区别说死：
              上面是「价格用什么存」，这里是「买家能切到哪些」。
            -->
            <el-form-item :label="$t('overview.set.buyerCurrency')">
              <el-select v-if="editing" v-model="form.currencyList" multiple filterable
                :placeholder="$t('overview.set.buyerCurrencyPlaceholder')" style="width:100%">
                <el-option v-for="item in currencyOptions" :key="item.code"
                  :label="currencyOptionLabel(item)" :value="item.code" />
              </el-select>
              <template v-else>
                <el-tag v-for="c in buyerCurrencyList" :key="c" size="small" effect="plain" class="mr4">{{ c }}</el-tag>
                <span v-if="!buyerCurrencyList.length" class="text-muted">{{ $t('overview.set.buyerCurrencyEmpty') }}</span>
              </template>
              <div class="hint" v-html="$t('overview.set.buyerCurrencyHint', { code: site.currency || 'USD' })"></div>
              <div class="hint" v-html="$t('overview.set.buyerCurrencyWarn', { code: site.currency || 'USD' })"></div>
            </el-form-item>
            <!--
              服务到期。页首第四个统计块就是看这个的——在此之前它引用的
              site.expireDate 后端从来没下发过（u_s_siteinfo 没有这一列），
              从拆分上线起一直显示「未设置」，而那看着只像「这个站没填」。
              2026-09-07 把列和接口补上了，这里给个入口。
            -->
            <el-form-item :label="$t('overview.set.expire')">
              <el-date-picker v-if="editing" v-model="form.expireDate" type="date"
                value-format="YYYY-MM-DD" :placeholder="$t('overview.set.expirePlaceholder')" style="width:220px" />
              <template v-else>
                <span v-if="site.expireDate">
                  {{ site.expireDate }}
                  <!-- hasExpire 已经保证 expireLabel 不是那个占位的「服务到期」了 -->
                  <el-tag v-if="hasExpire" size="small" class="ml8" effect="plain"
                    :type="expireDays < 0 ? 'danger' : (expireDays <= 30 ? 'warning' : 'info')">
                    {{ expireNum }} {{ expireLabel }}
                  </el-tag>
                </span>
                <span v-else class="text-muted">{{ $t('overview.set.expireEmpty') }}</span>
              </template>
              <div class="hint" v-html="$t('overview.set.expireHint')"></div>
              <!--
                两份到期日的关系必须写在页面上。运营在 ERP 里续了费、以为这边会跟着变，
                然后这边到期标红——他会以为是显示 bug，而不是「有两个地方要填」。
              -->
              <div class="hint" v-html="$t('overview.set.expireWarn')"></div>
            </el-form-item>
            <el-form-item :label="$t('overview.set.notifyEmail')">
              <el-input v-if="editing" v-model="form.notifyEmail" maxlength="500"
                :placeholder="$t('overview.set.notifyEmailPlaceholder')" />
              <span v-else>{{ site.notifyEmail || $t('common.none') }}</span>
              <div class="hint" v-html="$t('overview.set.notifyEmailHint')"></div>
            </el-form-item>
          </el-form>
        </el-card>

        <!--
          文件存储。2026-09-07 从「文件资源」页右上角那个「对象存储设置」弹窗搬过来的。

          搬的理由：这一份和上面的站点设置是**同一个粒度**——u_s_storage 一站一份，
          没有商家维度（那是 ERP s_m_shop_storage 的形状，照抄结构时留下的错觉）。
          既然是一次性的站点配置，就不该挂在文件列表页顶上：改一次站要跑两个页面，
          而「文件资源」那一页真正的内容是文件本身。

          ⚠ 但它**不能和上面那张卡合成一个表单、一个保存按钮**，两个原因：

            · 保存路径不同，而且这一条会失败。POST /file/storage 落库之前，后端会
              **真的往桶里放一个探针对象、再按访客的方式取回来**
              （ConsoleApiController#storageSave → SiteFileService#verifyStorage），
              不通就抛异常、这行配置根本不写库。合成一次提交的话，密钥填错会连带让
              域名、币种、询盘邮箱也存不进去——那几样和存储毫无关系，而人只看到一句
              「保存失败」，根本不会想到是被存储那一段拖下水的；

            · 权限不同。上面看 site:station:edit，这里看 site:storage:edit。出厂那两个
              角色恰好是「两个都有」（站长）或「两个都没有」（运营），但自定义角色不是，
              合成一个按钮就得取两者交集——那等于悄悄把两边的权限都收紧了。

          所以是同一页上的两张卡，各自「修改／保存」——和下面那张「汇率」卡一个套路。

          整张卡按 site:storage:query 显隐（原来那个按钮也是这么挂的 v-hasPermi）。
          这里用 v-if 而不是上面那种「置灰 + tooltip」是有区别的：上面灰掉的是**改**的
          权限、值本身还看得见；这里没有 query 权限连值都不该看见（密钥所在的一张卡），
          而且 GET 会直接吃 403、被拦截器弹成一句看不懂的报错。
        -->
        <el-card v-if="canQueryStorage" shadow="never" class="mt16">
          <template #header>
            <div class="card-head">
              <b>{{ $t('overview.storage.title') }}</b>
              <el-tooltip v-if="!storageEditing && !canEditStorage" placement="top"
                :content="$t('overview.storage.noEditPermi')">
                <el-button link type="primary" size="small" disabled>{{ $t('common.edit') }}</el-button>
              </el-tooltip>
              <el-button v-else-if="!storageEditing" link type="primary" size="small"
                @click="startStorageEdit">{{ $t('common.edit') }}</el-button>
              <span v-else>
                <el-button link size="small" @click="storageEditing = false">{{ $t('common.cancel') }}</el-button>
                <!--
                  local 时禁用保存：provider=local 这一行是后端在 u_s_storage 一行都没有时
                  造出来的兜底（SiteStorageService#requireActive），不是一份可提交的配置。
                  真让它提交下去，后端会把 local 当成一家厂商 upsert 进表、再
                  deactivateOthers——原来配好的腾讯云会被悄悄停用。
                -->
                <el-tooltip v-if="storageIsLocal" placement="top"
                  :content="$t('overview.storage.localNoSave')">
                  <el-button link type="primary" size="small" disabled>{{ $t('common.save') }}</el-button>
                </el-tooltip>
                <el-button v-else link type="primary" size="small"
                  :loading="storageSaving" @click="saveStorage">{{ $t('common.save') }}</el-button>
              </span>
            </div>
          </template>

          <!--
            本机磁盘模式的提示，只读态和编辑态都要出。

            没有它的时候这张卡会撒谎：后端在没配过任何厂商时回的是一行 provider=local
            的兜底配置，publicUrl 那一列填的是服务端的 station.upload.base-url。照单
            铺进「自定义域名」那一栏的话，框里赫然是一个真实地址——看起来像是配好了的、
            改一改还能改动。实际上 SiteFileService.publicUrl() 只在 provider=tencent
            时才读这一列，local 走的是环境变量，这个框改了存不下、存下了也不生效。
          -->
          <el-alert v-if="storageIsLocal" type="warning" :closable="false" show-icon class="mb8"
            :title="$t('overview.storage.localTitle')">
            <p v-html="$t('overview.storage.localBody')"></p>
            <p v-if="storageBaseUrl" style="margin-top:6px">
              {{ $t('overview.storage.localBase') }}<code>{{ storageBaseUrl }}</code>
            </p>
            <p v-else class="hint-warn" style="margin-top:6px" v-html="$t('overview.storage.localNoBase')"></p>
            <p style="margin-top:6px">{{ $t('overview.storage.localHow') }}</p>
          </el-alert>

          <el-alert v-if="storageEditing && !storageIsLocal" type="info" :closable="false"
            show-icon class="mb8">
            {{ $t('overview.storage.ownNote') }}
          </el-alert>

          <!--
            ⚠ rules 只在编辑态挂上。只读态挂着的代价是「厂商」那一行的标签上多一个红
              星号——provider 那条是 { required: true }，而 Element 一看见 required
              就画星号，不管这一栏此刻是输入框还是一段文字。只读态一个必填标记什么也
              不指示，只会让人以为这一屏有东西没填。
          -->
          <el-form ref="storageFormRef" v-loading="storageLoading" :model="storageForm"
            :rules="storageEditing ? storageRules : {}" label-width="120px" class="info-form">
            <el-form-item :label="$t('overview.storage.provider')" prop="provider">
              <el-radio-group v-if="storageEditing" v-model="storageForm.provider"
                @change="handleProviderChange">
                <el-radio-button value="aliyun">{{ $t('overview.storage.provider.aliyun') }}<span v-if="isConfigured('aliyun')" class="dot" /></el-radio-button>
                <el-radio-button value="huawei">{{ $t('overview.storage.provider.huawei') }}<span v-if="isConfigured('huawei')" class="dot" /></el-radio-button>
                <el-radio-button value="tencent">{{ $t('overview.storage.provider.tencent') }}<span v-if="isConfigured('tencent')" class="dot" /></el-radio-button>
              </el-radio-group>
              <template v-else>
                {{ providerLabel(storage.provider) }}
                <!-- status 是「这套配置启用没有」，和 active「当前用哪套」是两列 -->
                <el-tag v-if="storage.provider && storage.status === '1'" size="small"
                  type="info" effect="plain" class="ml8">{{ $t('overview.storage.off') }}</el-tag>
              </template>
              <div v-if="storageEditing" class="hint">
                {{ $t('overview.storage.providerHint') }}
              </div>
            </el-form-item>

            <!--
              local 时这几栏没有意义：没有远端、没有桶、没有密钥。

              前面那个 storageProvider 判空不是多余的：只读态第一帧 storage 还是 {}，
              storageIsLocal 因此是 false，这一段会先铺出一屏「Endpoint —／桶 —」再
              跳成本机磁盘的那条 warning。空着等一下比闪一屏假值好。
            -->
            <template v-if="storageProvider && !storageIsLocal">
              <!-- 腾讯云用 region 推导访问域名，另外两家要自己填 endpoint -->
              <el-form-item v-if="storageProvider === 'tencent'" :label="$t('overview.storage.region')" prop="region">
                <el-input v-if="storageEditing" v-model="storageForm.region"
                  :placeholder="$t('overview.storage.regionPlaceholder')" />
                <span v-else>{{ storage.region || $t('common.none') }}</span>
                <div v-if="storageEditing" class="hint">
                  {{ $t('overview.storage.regionHint') }}
                </div>
              </el-form-item>
              <el-form-item v-else label="Endpoint" prop="endpoint">
                <el-input v-if="storageEditing" v-model="storageForm.endpoint"
                  :placeholder="endpointPlaceholder" />
                <span v-else>{{ storage.endpoint || $t('common.none') }}</span>
              </el-form-item>

              <el-form-item :label="akLabel" prop="accessKeyId">
                <el-input v-if="storageEditing" v-model="storageForm.accessKeyId" />
                <span v-else>{{ storage.accessKeyId || $t('common.none') }}</span>
              </el-form-item>

              <el-form-item :label="skLabel" prop="accessKeySecret">
                <el-input v-if="storageEditing" v-model="storageForm.accessKeySecret" type="password"
                  show-password :placeholder="secretPlaceholder" />
                <!--
                  只读态只说「配过没有」，不摆一串星号。

                  密钥永远不下发（SiteStorage.accessKeySecret 是 WRITE_ONLY），星号会让人
                  以为那是回显的原值——以为把星号删掉再保存就能清空密钥，而实际上留空的
                  语义恰恰是「不改密钥」，什么都不会发生。
                -->
                <template v-else>
                  <el-tag v-if="storage.secretConfigured" size="small" type="success" effect="plain">
                    {{ $t('overview.storage.secretConfigured') }}
                  </el-tag>
                  <span v-else class="text-muted">{{ $t('overview.storage.secretMissing') }}</span>
                </template>
              </el-form-item>

              <el-form-item :label="$t('overview.storage.bucket')" prop="bucket">
                <el-input v-if="storageEditing" v-model="storageForm.bucket" :placeholder="bucketPlaceholder" />
                <span v-else>{{ storage.bucket || $t('common.none') }}</span>
                <div v-if="storageEditing && storageForm.provider === 'tencent'" class="hint">
                  {{ $t('overview.storage.bucketTencentHint') }}
                </div>
              </el-form-item>

              <el-form-item :label="$t('overview.storage.publicUrl')" prop="publicUrl">
                <el-input v-if="storageEditing" v-model="storageForm.publicUrl"
                  :placeholder="$t('overview.storage.publicUrlPlaceholder')" />
                <template v-else>
                  <span v-if="storage.publicUrl">{{ storage.publicUrl }}</span>
                  <span v-else class="text-muted">{{ $t('overview.storage.publicUrlEmpty') }}</span>
                </template>
                <div class="hint" v-html="$t('overview.storage.publicUrlHint')"></div>
                <!--
                  不做成校验规则而是一句提示：带路径的回源在 CDN 上是合法配法，拦死了那种
                  站点就永远保存不了。真伪由保存时的连通性自检判——它是真的去 GET 一次，
                  比在浏览器里猜准。
                -->
                <div v-if="storageEditing && publicUrlWarning" class="hint hint-warn">
                  {{ publicUrlWarning }}
                </div>
              </el-form-item>

              <el-form-item :label="$t('overview.storage.enable')">
                <el-switch v-if="storageEditing" v-model="storageEnabled" />
                <el-tag v-else :type="storage.status === '1' ? 'info' : 'success'" size="small">
                  {{ storage.status === '1' ? $t('overview.storage.off') : $t('overview.storage.on') }}
                </el-tag>
              </el-form-item>
            </template>
          </el-form>

          <div class="hint">
            {{ $t('overview.storage.foot') }}
            <el-link type="primary" :underline="false" @click="go('files')">{{ $t('overview.storage.footLink') }}</el-link>
            <span v-html="$t('overview.storage.foot2')"></span>
          </div>
        </el-card>

        <!--
          汇率。单独一张卡，只在开了买家可选币种时出现——没开这个功能的站，
          这一屏是纯噪音。

          ⚠ 这张卡存在的唯一理由是「免费 API 静悄悄挂掉」：它挂了之后前台没有任何
            症状，价格照常显示，只是停在几周前的汇率上。所以「多久没更新」必须显眼。
        -->
        <el-card v-if="buyerCurrencyList.length" shadow="never" class="mt16">
          <template #header>
            <div class="card-head">
              <b>{{ $t('overview.rate.title') }}</b>
              <span>
                <el-button link size="small" :loading="rateLoading" @click="loadRate">{{ $t('overview.rate.reload') }}</el-button>
                <el-tooltip v-if="!canEdit" placement="top"
                  :content="$t('overview.rate.noEditPermi')">
                  <el-button link type="primary" size="small" disabled>{{ $t('overview.rate.refreshNow') }}</el-button>
                </el-tooltip>
                <el-button v-else link type="primary" size="small"
                  :loading="rateRefreshing" @click="doRefreshRate">{{ $t('overview.rate.refreshNow') }}</el-button>
              </span>
            </div>
          </template>

          <div v-if="rate.fetchTime" :class="['rate-stat', { stale: rateStale }]">
            <span class="rate-time">{{ rate.fetchTime }}</span>
            <span class="rate-age">
              {{ rate.staleDays > 0 ? $t('overview.rate.daysAgo', { n: rate.staleDays }) : $t('overview.rate.today') }}{{ $t('overview.rate.updatedSuffix') }}
              <template v-if="rateStale">{{ $t('overview.rate.stale', { n: rate.staleLimit }) }}</template>
            </span>
          </div>
          <div v-else class="rate-stat stale">
            <span class="rate-time">{{ $t('overview.rate.never') }}</span>
            <span class="rate-age">{{ $t('overview.rate.neverHint', { code: rate.baseCurrency || site.currency }) }}</span>
          </div>

          <!-- ⚠ fetchStatus 是库里存的中文值（'失败'），不是码，比较时别翻它 -->
          <el-alert v-if="rate.fetchStatus === '失败'" type="warning" :closable="false" class="mt8"
            :title="$t('overview.rate.failTitle')">
            <div class="rate-err">{{ translateServerText(rate.fetchError) }}</div>
          </el-alert>

          <el-table v-if="rateRows.length" :data="rateRows" size="small" class="mt8">
            <el-table-column prop="code" :label="$t('overview.rate.colCode')" width="90" />
            <el-table-column :label="$t('overview.rate.colRate')">
              <template #default="{ row }">
                <span class="rate-num">1 {{ rate.baseCurrency }} = {{ row.rate }} {{ row.code }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint">{{ $t('overview.rate.foot') }}</div>

          <el-divider />

          <div class="rate-ov">
            <b>{{ $t('overview.rate.override') }}</b>
            <el-input v-if="canEdit" v-model="rateOverride" type="textarea" :rows="2"
              :placeholder="$t('overview.rate.overridePlaceholder')" class="mt8" />
            <span v-else class="text-muted">{{ rate.overrideJson || $t('common.unset') }}</span>
            <div class="hint hint-warn" v-html="$t('overview.rate.overrideWarn', { base: rate.baseCode || 'USD' })"></div>
            <div class="hint">{{ $t('overview.rate.overrideHint') }}</div>
            <el-button v-if="canEdit" type="primary" size="small" class="mt8"
              :loading="rateSaving" @click="doSaveRate">{{ $t('overview.rate.saveOverride') }}</el-button>
          </div>
        </el-card>

        <!--
          币种换算记录。

          ⚠ 刻意不并进上面那张「汇率」卡片：那张卡挂着 v-if="buyerCurrencyList.length"，
            一个没开买家币种切换的站根本看不到它——而恰恰是这种站改起结算币种来
            最没有顾虑（「反正没人切币种」）。把不可逆操作的唯一还原依据
            藏在一张可能不渲染的卡片里，等于没记。

          整张卡在没换算过的站上不出现：一个从来没改过币种的站，这里空着只是噪音。
        -->
        <el-card v-if="convertLogs.length" shadow="never" class="mt16">
          <template #header>
            <div class="card-head">
              <b>{{ $t('overview.convert.title') }}</b>
              <span class="rate-age">{{ $t('overview.convert.subtitle') }}</span>
            </div>
          </template>

          <el-table :data="convertLogs" size="small">
            <el-table-column prop="createTime" :label="$t('overview.convert.colTime')" width="160" />
            <el-table-column :label="$t('overview.convert.colConvert')" width="120">
              <template #default="{ row }">{{ row.fromCode }} → {{ row.toCode }}</template>
            </el-table-column>
            <el-table-column :label="$t('overview.convert.colRate')" width="150">
              <template #default="{ row }">
                <span class="rate-num">1 {{ row.fromCode }} = {{ row.rate }} {{ row.toCode }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('overview.convert.colAffected')">
              <template #default="{ row }">
                <span class="rate-num">
                  {{ $t('overview.convert.affected', {
                    goods: row.goodsRows, combo: row.comboRows,
                    order: row.orderRows, items: row.itemRows }) }}
                </span>
                <span v-if="row.mismatch > 0" class="text-muted">
                  {{ $t('overview.convert.mismatch', { n: row.mismatch }) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="operator" :label="$t('overview.convert.colOperator')" width="110" />
          </el-table>

          <div class="hint hint-warn" v-html="$t('overview.convert.warn')"></div>
          <div class="hint" v-html="$t('overview.convert.foot')"></div>
        </el-card>
      </el-col>

      <!--
        ⚠ 右边原来还有一张卡，叫「登记信息（在 ERP 里改）」，摆着域名、站点编码、
          服务到期、备注四项只读值。2026-09-07 整张撤掉：

            · 域名搬进左边「站点设置」了——它不在 ERP 那边改。ERP 的域名存在它自己库的
              u_s_site，和本站的 u_s_siteinfo 之间没有同步通道，那句「在 ERP 里改」
              会让人一遍遍去改一个不起作用的地方；
            · 服务到期当时是空的：u_s_siteinfo 没有这一列，/site 接口也不下发这个字段，
              这一栏从上线起就永远显示「未设置」。列已于 2026-09-07 补上，
              入口也搬进了「站点设置」，所以更没必要在这儿再摆一份只读的；
            · 剩下站点编码和备注两项只读值，撑不起一整张卡。

          **撤掉的后果，要恢复的话从这里看起**：站点编码现在管理台里哪儿都看不到了
          （顶栏显示的是 siteName，不是它），备注同理。两者都还在 /site 的返回体里，
          要用随时能取。站点编码本来也不该给编辑入口——它要和 ERP 发的跳转令牌里的
          site 声明对上（boundary §4.3），单改一边「进入管理」就全被拒，
          而表现只是「点了没反应」。

          页首那个「服务到期」统计块是另一处，还留着，现在有数据了。
      -->
    </el-row>
  </div>
</template>

<script setup name="StationConsoleOverview">
import { ref, computed, inject, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSiteGoods, listInquiry, saveSite, previewCurrency, getRate, saveRate, refreshRate,
  getShopStorage, saveShopStorage, getPageViewStat
} from '@/api/console'
import { currencyLabel, currencyOptions, currencyOptionLabel } from '@/utils/currency'
import { toFrontUrl } from '@/utils/siteUrl'
import { translateServerText } from '@/i18n/server'
import { identity, hasPermi, loadIdentity } from '@/store/identity'
import { t, locale, localeChosen, resetLocale, LOCALES } from '@/i18n'

const router = useRouter()

/**
 * 能不能改站点设置。
 *
 * 用 computed 而不是静态布尔值：`identity` 是会话拉回来才填的，算成静态值会出现
 * 「权限够却灰着、刷新一下就好了」那种时好时坏的问题。
 */
const canEdit = computed(() => {
  void identity.perms
  return hasPermi(['site:station:edit'])
})

/*
  站点状态的译文。⚠ 库里存的就是中文串（'运行中'），不是枚举码——
  只翻显示，value 一个字不动。和 layout/AppShell.vue 那一份是同一张表，
  两处都要用所以没法只留一份（除非单开一个模块，为三个词不值当）。
*/
const STATUS_KEY = {
  建设中: 'site.status.building',
  运行中: 'site.status.running',
  已停用: 'site.status.disabled'
}
const statusText = status => (STATUS_KEY[status] ? t(STATUS_KEY[status]) : (status || ''))

/** 站点档案由管理台外壳进入时取好，这里直接用，不再请求一次 */
const site = inject('stationSite', ref({}))
/** 改完站点设置要让外壳重新取一次档案，顶栏那个状态标签才会跟着变 */
const reloadSite = inject('reloadStationSite', null)

const stat = ref({ published: 0, draft: 0, pending: 0 })

/*
  站点设置的就地编辑。

  ⚠ 这些字段（语言、币种、前台开关、询盘通知邮箱）以前在 ERP 的站点档案上，管理台只读。
  它们搬过来了（docs/独立站与ERP的边界.md §7.2）——搬过来的理由是：

      站点前台现在向自己的后端取数，ERP 留一份就是两份会各自被改的配置，
      而且没有任何机制能发现它们不一致了。
*/
const editing = ref(false), saving = ref(false)
const form = ref({})
/*
  前台真正有文案表的语言，必须和后端 SiteInfo.SUPPORTED_LANGS 一字不差。

  ⚠ 这里原来是 13 个「常见的出海语言」（多出 de/fr/ar/vi/th），而后端只认 8 个。
    多出来那几个的表现最坑：页面能选、提示「已保存」、langs 列里也真写进去了，
    但 SiteInfo.langList() 返回时被白名单剔掉——前台、装修页、商品文案的语言页签
    全都没有它。运营看到的就是「我明明选了法语，怎么还是只有原来那几种」，
    而唯一的痕迹是 station-console 启动日志里一条 WARN，没人会去翻。

    所以候选收敛到 8 个。allow-create 保留（存量站的 langs 列里可能已经有越界值，
    不保留的话打开编辑框就等于把它们悄悄删掉，又是一次静默变更），但改成
    当场标红说明 + 保存时明确剔除并告知，不再让人以为选上了。

    加语言要动 5 个地方，起点是 station-site/app/composables/useShopCopy.ts 的 COPY 表。
*/
const langOptions = ['en', 'zh', 'ja', 'es', 'pt', 'ru', 'it', 'ko', 'fr']

/*
  ---------------- 管理台语言 ----------------

  ⚠ 和上面那个 langOptions 是两份，而且**不该合并**：那一份是前台有文案表的语言
  （九种，真相在 station-site 的 useShopCopy.ts），这一份是**管理台**有文案表的语言
  （三种，真相在 src/i18n/index.js 的 LOCALES）。两个界面各自翻到哪儿了是两件事，
  合成一份的结果必然是其中一边在撒谎——要么管理台里选得上一个进去还是中文的语言，
  要么前台少放出几种本来能用的。
*/
const consoleLangOptions = LOCALES

/** 站点默认语言的显示名（中文 / English / Français）。库里空着时按 zh */
const consoleLangLabel = computed(() =>
  (LOCALES.find(item => item.code === (site.value.consoleLang || 'zh')) || LOCALES[0]).label)

/** 我自己现在看的是哪种语言。和上面那个可能不同，最后那行提示就是为了说清这件事 */
const myLangLabel = computed(() =>
  (LOCALES.find(item => item.code === locale.value) || LOCALES[0]).label)

/** 当前选中里前台不支持的那些。非空时表单上标红，保存时剔除 */
const unsupportedLangs = computed(() =>
  (form.value.langList || []).filter(lang => !langOptions.includes(String(lang).trim().toLowerCase()))
)

/*
  币种和语言不一样，不做剔除：前台 formatMoney 走 Intl.NumberFormat，
  任何合法 ISO 4217 码都渲染得出来，站点后端也没有白名单。
  这六个是**管理台自己**画得出符号的范围（utils/currency.js），超出只影响管理台的显示，
  不影响前台，所以越界值留着、只提示，不像语言那样丢掉。
*/
function isKnownCurrency(code) {
  return currencyOptions.some(item => item.code === String(code || '').trim().toUpperCase())
}
const currencyKnown = computed(() => isKnownCurrency(site.value.currency))

/** 买家可选币种（只读展示用）。库里存的是逗号分隔串 */
const buyerCurrencyList = computed(() =>
  (site.value.currencies || '').split(',').map(s => s.trim()).filter(Boolean))

/* ---------------- 文件存储 ---------------- */

/*
  这一整段从 views/Files.vue 搬过来（那一页右上角的「存储设置」按钮和「对象存储设置」
  弹窗一起删了）。为什么落在这一页、为什么**不**和上面那张卡合成一个保存按钮，
  见模板里那张卡上方的注释。
*/

/** 能不能看存储配置。密钥所在的一张卡，没这个权限整张卡不出现 */
const canQueryStorage = computed(() => {
  void identity.perms
  return hasPermi(['site:storage:query'])
})
/** 能不能改。只灰掉「修改」按钮，值照样看得见——和上面那张卡一个处置 */
const canEditStorage = computed(() => {
  void identity.perms
  return hasPermi(['site:storage:edit'])
})

const storageFormRef = ref(null)
/** 当前生效的那一套，只读态显示它 */
const storage = ref({})
/*
  编辑态的表单。和 storage 分开存不是重复：切一次厂商会把整个表单换成那一家的值
  （handleProviderChange 去查回来），而只读态显示的必须还是「当前生效的那套」——
  共用一份的话，点开编辑、切到没配过的华为云、再取消，只读态就变成一片空白了。
*/
const storageForm = ref({ provider: 'aliyun' })
const storageEditing = ref(false)
const storageLoading = ref(false)
const storageSaving = ref(false)
const storageEnabled = ref(true)
/*
  进编辑态时的厂商。换厂商之后原来那把密钥就作废了——它是上一家的凭据，
  「留空 = 沿用原来那份」的语义在这时候不成立。
*/
const originalProvider = ref('')
/** 已经配过哪几家。厂商按钮上打个「已配置」的小点 */
const configuredProviders = ref([])
const isConfigured = code => configuredProviders.value.includes(code)

const PROVIDER_KEYS = {
  aliyun: 'overview.storage.provider.aliyun',
  huawei: 'overview.storage.provider.huawei',
  tencent: 'overview.storage.provider.tencent',
  /*
    local 不是「一家厂商」，是「还没接对象存储」。这一条在 Files.vue 那份表里没有——
    那边只在切换厂商的提示里用得到标签，local 永远不出现。搬到这儿之后只读态要把
    provider 打在页面上，缺这一条的话页面上会出现一个孤零零的 "local"。
  */
  local: 'overview.storage.provider.local'
}
const providerLabel = code => (PROVIDER_KEYS[code] ? t(PROVIDER_KEYS[code]) : (code || '—'))

/**
 * 当前在说哪一家：编辑态看表单，只读态看库里生效的那套。
 *
 * 两态共用同一份 form-item（label 叫 SecretId 还是 AccessKeyId、region 和 endpoint
 * 哪一栏出现，都按它走），所以要有一个统一的口径。直接读 storageForm 的话，
 * 只读态会拿编辑态残留的表单值去决定显示哪一栏。
 */
const storageProvider = computed(() =>
  storageEditing.value ? storageForm.value.provider : storage.value.provider)

/*
  「还没选厂商」的状态。后端在 u_s_storage 一行都没有时会造一个 provider=local 的
  兜底配置回来（SiteStorageService#requireActive），它不对应三个单选按钮里的任何一个——
  三个都不选中就是这个状态。它不是一份能提交的配置，见模板里那条 warning。
*/
const storageIsLocal = computed(() => storageProvider.value === 'local')

/*
  本机磁盘那个地址前缀（后端把 station.upload.base-url 放在 publicUrl 里回显）。

  ⚠ 两态分别取。只读一个的话，点「修改」之后那句「当前生效的地址前缀」会突然消失，
    看着像是这个值被改掉了——而它其实是个环境变量，管理台碰不到。
*/
const storageBaseUrl = computed(() =>
  storageEditing.value ? storageForm.value.publicUrl : storage.value.publicUrl)

const akLabel = computed(() => storageProvider.value === 'tencent' ? 'SecretId' : 'AccessKeyId')
const skLabel = computed(() => storageProvider.value === 'tencent' ? 'SecretKey' : 'AccessKeySecret')

const providerChanged = computed(() =>
  !!originalProvider.value && storageForm.value.provider !== originalProvider.value)

const secretPlaceholder = computed(() => {
  if (providerChanged.value) return t('overview.storage.secretChanged')
  return storageForm.value.secretConfigured
    ? t('overview.storage.secretKeep') : t('overview.storage.secretInput')
})

const endpointPlaceholder = computed(() => storageForm.value.provider === 'huawei'
  ? t('overview.storage.endpointHintHuawei')
  : t('overview.storage.endpointHintAliyun'))

const bucketPlaceholder = computed(() => storageForm.value.provider === 'tencent'
  ? t('overview.storage.bucketHintTencent')
  : t('overview.storage.bucketHint'))

/**
 * 「满足条件时必填」。
 *
 * ⚠ 别退回 `{ required: true }`。region 和 endpoint 是二选一的（腾讯云显示 region，
 * 另外两家显示 endpoint），两条规则都写死 required 之后能正常提交，靠的是
 * 「v-if 把 form-item 卸载掉了，卸载掉的项不参与校验」这个副作用——
 * 不是这份规则表表达出来的意图。哪天有人把那两个 v-if 改成 v-show（比如为了切换时
 * 不闪一下），两条规则会同时生效，而两个输入框永远只显示一个，表单就再也提交不了了，
 * 且页面上看不出是被哪条规则拦的。把条件写进规则里，改成 v-show 也不会坏。
 *
 * ⚠ 这份规则表只在编辑态挂到 el-form 上（模板里 `storageEditing ? storageRules : {}`），
 *   所以 predicate 里的 storageIsLocal / storageProvider 求值时一定在编辑态，
 *   跟的是 storageForm。别把那个三元去掉——只读态挂上去会多画一个红星号。
 */
const requiredWhen = (predicate, message) => ({
  validator: (rule, value, callback) => {
    if (!predicate()) { callback(); return }
    if (String(value ?? '').trim()) { callback(); return }
    callback(new Error(message))
  },
  trigger: 'blur'
})

/*
  ⚠ 是 computed 不是普通对象：校验提示里的那几句话要跟着界面语言变。
    写成常量的话，切成英文之后表单报错仍然是中文——而报错恰恰是最需要看懂的时刻。
    模板里 :rules 绑的是它，Vue 会自动解包，用法不变。
*/
const storageRules = computed(() => ({
  provider: [{ required: true, message: t('overview.storage.rule.provider'), trigger: 'change' }],
  accessKeyId: [requiredWhen(() => !storageIsLocal.value, t('overview.storage.rule.required'))],
  // 新建时必填；换厂商后也必填——原来那把是上一家的凭据，沿用它连不上新厂商。
  // 后端 SiteStorageService.save 也拦一次，这里只是让人当场看到。
  accessKeySecret: [{
    validator: (rule, value, callback) => {
      if (storageIsLocal.value) { callback(); return }
      if ((value || '').trim()) { callback(); return }
      if (providerChanged.value) {
        callback(new Error(t('overview.storage.rule.secretNew')))
        return
      }
      if (!storageForm.value.secretConfigured) {
        callback(new Error(t('overview.storage.rule.required')))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
  bucket: [requiredWhen(() => !storageIsLocal.value, t('overview.storage.rule.bucket'))],
  region: [requiredWhen(() => storageProvider.value === 'tencent', t('overview.storage.rule.region'))],
  endpoint: [requiredWhen(
    () => !storageIsLocal.value && storageProvider.value !== 'tencent', t('overview.storage.rule.endpoint'))],
  publicUrl: [{ validator: validatePublicUrl, trigger: 'blur' }]
}))

/**
 * 自定义域名的格式校验。
 *
 * 这一栏填错的代价比别的栏高一个量级：它会被**整串拼进 imageurl 存库**，
 * 而且是上传那一刻就固化下来的。别的栏填错顶多是上传失败当场报错，这一栏填错是
 * 「上传成功了、库里存下了、每张图都是死链」，要等到有人打开页面才发现，
 * 发现之后要改的是历史数据。所以能在前端拦的先拦掉。
 *
 * 只拦「一定是错的」那两种。带路径的形态交给保存时的连通性自检去判——
 * 见 publicUrlWarning。
 */
function validatePublicUrl(rule, value, callback) {
  const url = String(value ?? '').trim()
  // 留空是合法的，表示用厂商默认域名
  if (!url || storageIsLocal.value) { callback(); return }
  if (!/^https?:\/\//i.test(url)) {
    callback(new Error(t('overview.storage.rule.urlScheme')))
    return
  }
  try {
    // eslint-disable-next-line no-new
    new URL(url)
  } catch {
    callback(new Error(t('overview.storage.rule.urlBad')))
    return
  }
  callback()
}

/**
 * 带路径的自定义域名 —— 提示，不拦。
 *
 * 用户最容易犯的错就是把本机磁盘模式那个带 /files 的地址抄过来
 * （`https://data.xxx.com/files`）。对象存储这边地址是 `域名 + / + 对象键`，
 * 对象键本身就是 `image/2026/09/06/xxx.png`，多出来的那一层会拼成桶里不存在的键。
 *
 * 但「域名后面带一层路径」在 CDN 上是合法配法（回源路径），拦死了那种站点就永远
 * 存不了配置。所以这里只说话，真伪由后端那次真实 GET 决定。
 */
const publicUrlWarning = computed(() => {
  const url = String(storageForm.value.publicUrl ?? '').trim()
  if (!url || storageIsLocal.value) return ''
  let parsed
  try {
    parsed = new URL(url)
  } catch {
    return ''
  }
  const path = parsed.pathname.replace(/\/+$/, '')
  if (!path) return ''
  return t('overview.storage.publicUrlPath', { path })
})

/** 只读态显示的那套。不带 provider 参数 = 「当前用的是哪套」 */
function loadStorage() {
  storageLoading.value = true
  getShopStorage().then(response => {
    storage.value = response.data || {}
    configuredProviders.value = response.configured || []
  }).finally(() => {
    storageLoading.value = false
  })
}

/*
  进编辑态要重新查一次，不能直接拿只读态那份 storage 铺表单。

  理由是 secretConfigured：它是后端按库里有没有密文算出来的，而上一次查询可能
  已经过了很久（这一页不刷新就一直挂着）。中间要是有人在别处改过存储，
  这里拿旧值当「已配置，留空表示不修改」，保存下去就是拿一份过期的判断在做决定。
*/
function startStorageEdit() {
  getShopStorage().then(response => {
    applyStorage(response, response.data ? response.data.provider : 'aliyun')
    storageEditing.value = true
  })
}

/**
 * 切换厂商单选框时，把那一家已经存过的信息查回来填上。
 *
 * 一个站可以同时存着阿里云、华为云、腾讯云三套配置，切换只是换看哪一套——
 * 不去查的话，切过去看到的还是上一家的 endpoint 和桶名，改一改保存就串了。
 * 这家没配过就清空表单，让人从头填。
 */
function handleProviderChange(provider) {
  getShopStorage(provider).then(response => applyStorage(response, provider))
}

/** 把接口回来的一套配置铺进表单。data 为 null 表示这家没配过，除厂商外全部清空 */
function applyStorage(response, provider) {
  const data = response.data
  configuredProviders.value = response.configured || []
  storageForm.value = data
    ? { ...data, accessKeySecret: '' }
    : { provider, accessKeySecret: '', endpoint: '', region: '', accessKeyId: '', bucket: '', publicUrl: '' }
  storageEnabled.value = !data || data.status !== '1'
  // 回显之后表单里就是这家自己的信息了，「换了厂商要重填密钥」的提示不再适用
  originalProvider.value = data ? data.provider : ''
  storageFormRef.value?.clearValidate()
}

function saveStorage() {
  storageFormRef.value.validate(valid => {
    if (!valid) return
    storageSaving.value = true
    const payload = { ...storageForm.value, status: storageEnabled.value ? '0' : '1' }
    saveShopStorage(payload).then(response => {
      storageEditing.value = false
      // 只读态那份要跟着变。这一步之后 secretConfigured 才会变成 true
      loadStorage()
      if (response.providerChanged) {
        notifyProviderChanged(response)
        return
      }
      ElMessage.success(t('common.saved'))
    }).finally(() => {
      storageSaving.value = false
    })
  })
}

/**
 * 换厂商 = 换了一个文件库。原厂商的文件不再出现在文件资源列表里，但没有被删。
 *
 * 用 alert 而不是一闪而过的 toast：商家会发现「我的图怎么没了」，
 * 这句话必须让他看见并点掉，否则就会来问是不是数据丢了。
 */
function notifyProviderChanged(result) {
  const from = providerLabel(result.previousProvider)
  const to = providerLabel(storageForm.value.provider)
  const images = result.hiddenImages || 0
  const videos = result.hiddenVideos || 0
  const models = result.hiddenModels || 0
  const parts = []
  if (images) parts.push(t('overview.storage.nImages', { n: images }))
  if (videos) parts.push(t('overview.storage.nVideos', { n: videos }))
  if (models) parts.push(t('overview.storage.nModels', { n: models }))

  let html = t('overview.storage.switchedBody', { from, to })
  if (parts.length) {
    html += t('overview.storage.switchedLeft', { from, parts: parts.join('、') })
  }
  ElMessageBox.alert(html, t('overview.storage.switched'), {
    dangerouslyUseHTMLString: true,
    confirmButtonText: t('common.gotIt'),
    type: 'success'
  })
}

/* ---------------- 汇率 ---------------- */

const rate = ref({})
const rateOverride = ref('')
const rateLoading = ref(false)
const rateRefreshing = ref(false)
const rateSaving = ref(false)

/** 超过后端给的天数没成功更新过，或者一次都没成功过 */
const rateStale = computed(() =>
  rate.value.fetchTime ? rate.value.staleDays > rate.value.staleLimit : true)

/** 生效汇率表。基准自己那一行不显示——「1 USD = 1 USD」没有信息量 */
const rateRows = computed(() =>
  Object.entries(rate.value.rates || {})
    .filter(([code]) => code !== rate.value.baseCurrency)
    .map(([code, value]) => ({ code, rate: Number(value).toFixed(6) })))

/*
  最近几次币种换算，和汇率一起由 /rate 下发。

  ⚠ 它渲染在一张独立的卡片里，不在「汇率」那张卡里——那张卡挂着
    v-if="buyerCurrencyList.length"，没开买家币种切换的站看不到它。
    换算不可逆，这是唯一的还原依据，不能藏在一张可能不渲染的卡片里。
*/
const convertLogs = computed(() => rate.value.convertLogs || [])

function loadRate() {
  rateLoading.value = true
  getRate()
    .then(res => {
      rate.value = res.data || {}
      rateOverride.value = rate.value.overrideJson || ''
    })
    .finally(() => { rateLoading.value = false })
}

function doRefreshRate() {
  rateRefreshing.value = true
  refreshRate()
    .then(res => {
      // 后端把成功与否放在 data 里，msg 里是给人看的原因。失败不抛错——
      // 「拉取失败」是一个正常的业务结果（网络不通），不是接口出错
      if (res.data) ElMessage.success(res.msg || t('overview.rate.updated'))
      else ElMessage.warning(res.msg || t('overview.rate.updateFailed'))
      loadRate()
    })
    .finally(() => { rateRefreshing.value = false })
}

function doSaveRate() {
  rateSaving.value = true
  saveRate(rateOverride.value.trim())
    .then(() => {
      ElMessage.success(t('common.saved'))
      loadRate()
    })
    .finally(() => { rateSaving.value = false })
}

function startEdit() {
  form.value = {
    domain: site.value.domain || '',
    expireDate: site.value.expireDate || null,
    status: site.value.status || '运行中',
    /*
      ⚠ 空值要兜成 'zh'，不能直接铺 site.consoleLang。

      老库里这一列是 NULL（这一栏是 2026-09-22 才加的），铺进去 el-select 显示空白，
      随手一保存送的就是 null——而 SQL 那边它走 coalesce，null 等于「不改」，
      于是这一栏永远存不下去，表现是「选了保存，再打开还是空的」。
      后端读的时候 null 本来就按 zh 算，所以在这儿写实它没有任何语义变化。
    */
    consoleLang: site.value.consoleLang || 'zh',
    currency: site.value.currency || 'USD',
    notifyEmail: site.value.notifyEmail || '',
    langList: (site.value.langs || '').split(',').filter(Boolean),
    currencyList: buyerCurrencyList.value.slice()
  }
  editing.value = true
}

async function save() {
  // 越界语言在这里就剔掉，而不是交给后端静默丢弃：库里存了也不会生效，
  // 留在 langs 列里只会让下次打开编辑框的人再被骗一次。剔了要说，不能默默改。
  const dropped = unsupportedLangs.value
  const kept = (form.value.langList || [])
    .map(lang => String(lang).trim().toLowerCase())
    .filter(lang => lang && langOptions.includes(lang))
  const payload = {
    ...form.value,
    // 统一存主机名。库里那批历史值和种子数据带着协议头，两种写法混着存的代价是
    // 每个出口都得各留一段兼容分支——toFrontUrl 一份、BuyerAuthService#siteBase 一份，
    // 将来第三个消费方大概率会漏掉。在入口这一处剪掉最省事。
    // 顺带去掉尾斜杠：拼出来的链接会变成 https://host//verify-email。
    domain: String(form.value.domain || '').trim()
      .replace(/^https?:\/\//i, '').replace(/\/+$/, ''),
    // 清空到期日要送 null，不能送空串：后端那个字段是 java.util.Date 带
    // @JsonFormat("yyyy-MM-dd")，空串靠 Jackson 的宽容才变成 null，
    // 别把「能清空」这件事押在那上面
    expireDate: form.value.expireDate || null,
    langs: [...new Set(kept)].join(','),
    // 三字母码统一大写：库里是 varchar，'usd' 和 'USD' 会变成两个值，
    // 而前台 formatMoney 和管理台 currencyOptions 都只按大写匹配
    currency: String(form.value.currency || 'USD').trim().toUpperCase(),
    // 同样大写去重。基准币种不在这里剔除——后端 SiteInfo.currencyList() 会把它并进去，
    // 前端也剔一遍只会让「我明明选了却没保存上」变成一个新的困惑
    currencies: [...new Set((form.value.currencyList || [])
      .map(c => String(c).trim().toUpperCase())
      .filter(Boolean))].join(',')
  }
  delete payload.langList
  delete payload.currencyList

  /*
    币种变了就先问清楚。

    ⚠ 这一步不能省成一个 confirm('确定吗')：后端保存这一下会按汇率改掉全站商品价
    和**已经下的订单**的金额，而且不可逆。运营在这个框里要看到的是三件具体的事——
    用哪个汇率、会动什么、改不回去，而不是一句泛泛的「确定」。

    askCurrencyConvert 返回三种结果：
      true  → 按汇率换算
      false → 只改币种代码，数字一个不动（当初填错了的那种情况）
      null  → 放弃，整个保存都不做
  */
  const fromCode = String(site.value.currency || 'USD').trim().toUpperCase()
  if (fromCode !== payload.currency) {
    const choice = await askCurrencyConvert(fromCode, payload.currency)
    if (choice === null) return
    payload.convertAmounts = choice
  }

  saving.value = true
  saveSite(payload)
    .then(res => {
      // data 是这次换算的流水，币种没变或选了「只改代码」时为 null
      const converted = res && res.data
      if (converted) {
        reportConverted(converted)
      } else if (dropped.length) {
        ElMessage.warning(t('overview.msg.savedDropped', { langs: dropped.join('、') }))
      } else {
        ElMessage.success(t('common.saved'))
      }
      /*
        自己这一屏跟着新的站点默认走——**但只在这个人没自己选过语言的时候**。

        ⚠ 两种人的正确行为是相反的，这个判断就是全部的区别：
          · 没自己选过的（跟着站点默认走）：改完立刻看到效果，否则他会以为没保存上，
            再点一次、再点一次；
          · 自己选过的（比如业务员把自己切成了中文）：一个字都不该动。把他的选择
            冲掉的话，他只是顺手帮站长改了个默认值，自己的界面却莫名其妙变成了法语。

        resetLocale 而不是 setLocale：后者会把这次当成「他自己的选择」记进 localStorage，
        那样他就再也跟不上站点默认了。
      */
      if (!localeChosen.value) resetLocale(payload.consoleLang)
      editing.value = false
      // 外壳持有的那份档案也要刷新，否则顶栏的状态标签还是旧的
      reloadSite?.()
      /*
        身份里那份**站点快照**也要重拉，这一行不能省。

        币种和语言在这一页改，读它们的却是别的页：商品管理的价格符号走
        `identity.currency`（Goods.vue 的 siteCurrency），商品的语言横幅和博客的
        默认语言走 `identity.langs`。

        后端保存那一下已经把**会话里**那份刷新了（ConsoleApiController#refreshIdentityFromSite），
        但浏览器里这份是 main.js 启动时拉的一次，而路由守卫开头就是
        `if (identity.loaded) return true`——切页面不会再拉第二次。

        ⚠ 少了这一行的现象最坑人：改完币种，商品列表的价格数字**已经换算过了**、
        符号却还是旧的（2026-09-22 实测：库里已是 USD，页面画着 €458.84），
        要按 F5 整页刷新才对得上。数字变了符号没变，看着像「只换了一半」，
        很容易被当成换算本身出了错去查后端——而后端是对的。

        catch 吞掉：身份重拉失败不该把一次已经成功的保存报成失败。真拉不到的话
        下次刷新页面会补上，401 那一支有 request.js 统一接管。
      */
      loadIdentity(true).catch(() => {})
      /*
        ⚠ 上面那一行现在还多管一件事：identity.consoleLang（管理台默认语言）。
          它和币种、语言是同一份站点快照，main.js 启动时拉一次就不再动——
          不重拉的话，一个没自己选过语言的人**下一次开新标签页**看到的还是旧默认。
          本次会话内的立即生效由上面那句 resetLocale 负责，两件事不能互相替代。
      */
      // 换算之后汇率卡片底下的「换算记录」要多一行，顺手刷一次
      if (converted) loadRate()
    })
    .finally(() => { saving.value = false })
}

/**
 * 改币种前的确认框。返回 true=换算 / false=只改代码 / null=放弃。
 *
 * ⚠ 拿不到汇率时**不弹红框把流程掐断**：那条路仍然有解——「只改代码」压根不需要
 *   汇率。掐断的话，一个建站时币种填错的站永远改不回来了。
 */
async function askCurrencyConvert(fromCode, toCode) {
  let preview = null
  try {
    preview = (await previewCurrency(toCode)).data || {}
  } catch (e) {
    // 接口本身挂了（不是「汇率取不到」，那种情况后端回的是 200 + error 字段）。
    // 这时候连「会发生什么」都说不出来，不能让人在信息缺失的情况下按确定
    return null
  }

  const rate = preview.rate
  const canConvert = rate !== null && rate !== undefined

  let html = t('overview.cc.lead', { from: fromCode, to: toCode })
  if (canConvert) {
    html += t('overview.cc.rate', { from: fromCode, to: toCode, rate })
      + t('overview.cc.mail')
      + t('overview.cc.irreversible')
  } else {
    html += t('overview.cc.noRate', { err: translateServerText(preview.error || '') })
  }
  html += t('overview.cc.typo', { from: fromCode, to: toCode })

  if (!canConvert) {
    return ElMessageBox.confirm(html, t('overview.cc.title'), {
      dangerouslyUseHTMLString: true,
      confirmButtonText: t('overview.cc.onlyCode'),
      cancelButtonText: t('overview.cc.giveUp'),
      type: 'error'
    }).then(() => false).catch(() => null)
  }

  /*
    三个出口挤在一个框里，靠 distinguishCancelAndClose 区分：
      确定按钮 → resolve            → 换算
      取消按钮 → reject('cancel')   → 只改代码
      右上角 X / ESC → reject('close') → 放弃

    不拆成两个框串着弹：第二个框弹出来的时候，第一个框里那段「不可逆」已经没了，
    而那正是要人记住的那一句。
  */
  return ElMessageBox.confirm(html, t('overview.cc.title'), {
    dangerouslyUseHTMLString: true,
    distinguishCancelAndClose: true,
    confirmButtonText: t('overview.cc.doConvert', { rate }),
    cancelButtonText: t('overview.cc.onlyCode'),
    type: 'warning'
  }).then(() => true).catch(action => (action === 'cancel' ? false : null))
}

/**
 * 换算完了报一份账。
 *
 * ⚠ 用 alert 不用 ElMessage：这几个数字（尤其是汇率）是事后唯一能核对的东西，
 *   一个 3 秒就飘走的 toast 承载不了它。库里那张 u_s_currency_convert_log 是
 *   长期依据，但运营此刻要的是「刚才到底动了多少」。
 */
function reportConverted(c) {
  let html = t('overview.cc.doneLead', {
    from: c.fromCode, to: c.toCode, rate: c.rate,
    goods: c.goodsRows, combo: c.comboRows, order: c.orderRows, items: c.itemRows
  })
  if (c.mismatch > 0) {
    html += t('overview.cc.doneMismatch', { n: c.mismatch })
  }
  html += t('overview.cc.doneFoot')
  ElMessageBox.alert(html, t('overview.cc.doneTitle'), {
    dangerouslyUseHTMLString: true,
    confirmButtonText: t('common.gotIt'),
    type: 'success'
  })
}

const langList = computed(() => (site.value.langs || '').split(',').filter(Boolean))

// siteurl 那一列删了（§7.2）——它和 domain 是一回事，留两个只会各自被改。
const frontUrl = computed(() => toFrontUrl(site.value.domain))

/*
  到期天数用后端算好的 expireDays，不在前端拿本地时钟算——客户端时区和时钟都不可靠。
  负数是已过期，null 是没填到期日。

  ⚠ 这几个 computed 从拆分上线起一直空转：site.expireDays / site.expireDate 后端
    根本不下发（u_s_siteinfo 当时没有 expiredate 这一列），统计块永远显示
    「—／服务到期／未设置到期日」，看着像「这个站没填」而不像「功能没接上」。
    2026-09-07 补了列和接口，代码一行没动就活了——它本来就是对的，缺的是数据。

  ⚠ 后端的 expireDays 是每次调 getExpireDays() 现算的，不是查询时算完落在对象上的。
    站点档案在后端进程里有缓存，算完存下来的话天数会冻住，隔天不会变。
*/
const expireDays = computed(() => Number(site.value.expireDays))
const hasExpire = computed(() => !!site.value.expireDate && Number.isFinite(expireDays.value))
const expireNum = computed(() => hasExpire.value ? Math.abs(expireDays.value) : '—')
const expireLabel = computed(() => {
  if (!hasExpire.value) return t('overview.stat.expire')
  return expireDays.value < 0 ? t('overview.stat.expired') : t('overview.stat.expireIn')
})
const expireClass = computed(() => {
  if (!hasExpire.value) return ''
  if (expireDays.value < 0) return 'stat-danger'
  return expireDays.value <= 30 ? 'stat-warn' : ''
})

/*
  统计只借现成的列表接口拿 total，pageSize 给 1——要的是数字，不是那一页数据。
  为四个数字单开一个统计接口不值得，这里的量级也撑得住。
*/
function loadStat() {
  // 不带 siteCode：站点由服务端会话决定
  const base = { pageNum: 1, pageSize: 1 }
  listSiteGoods({ ...base, publishStatus: '已上架' }).then(r => stat.value.published = r.total || 0)
  listSiteGoods({ ...base, publishStatus: '草稿' }).then(r => stat.value.draft = r.total || 0)
  listInquiry({ ...base, status: '待处理' }).then(r => stat.value.pending = r.total || 0)
}

/*
  ================================================================
  前台浏览量
  ================================================================

  ⚠ 初始值给的是一个**结构完整**的空对象，不是 {} 或 null。
    模板里直接读 pv.today.pvHuman、v-for pv.trend——给 {} 的话首屏渲染
    就是「读 undefined 的属性」。给 enabled: true 是为了让首屏不闪一下
    「未启用」那条提示（真的没启用时请求回来会把它改成 false）。
*/
const pv = ref({
  enabled: true,
  today: { pv: 0, pvHuman: 0, uv: 0 },
  yesterday: { pv: 0, pvHuman: 0, uv: 0 },
  trend: [],
  topGoods: [],
  typeBreakdown: [],
  referrers: [],
  devices: [],
  browsers: [],
  countries: [],
  // ⚠ 三个计数给 0、两个比率给 null。比率的 null 是**有意义的值**：
  //   后端在「一次来访都没有」时回 null，界面显示「—」。
  //   给 0 的话报表上会出现「跳出率 0%」——那是个极不寻常的好数字，
  //   会让人以为统计坏了
  engagement: {
    sessions: 0, views: 0, bounces: 0,
    pagesPerSession: null, bounceRate: null, sessionMinutes: 30
  },
  topDays: 30,
  retainDays: 90
})

/**
 * 能不能看统计。
 *
 * 用 computed 而不是静态布尔值，理由和上面 canEdit 一样：`identity` 是会话拉回来
 * 才填的，算成静态值会出现「权限够却看不到、刷新一下就好了」那种时好时坏的问题。
 */
const canQueryStat = computed(() => {
  void identity.perms
  return hasPermi(['site:stat:query'])
})

function loadPageViewStat() {
  getPageViewStat().then(r => {
    if (r.data) pv.value = r.data
  }).catch(() => {
    /*
      ⚠ 静默失败，不弹提示。

      这一块是概览页上的**附属信息**：它没加载出来不影响运营做任何事，
      而弹一个「加载失败」会让人以为整个管理台出了问题。
      真出错了 request 拦截器已经在控制台里留了痕迹。

      表格会显示成 empty-text，那就是这里的失败表现——够了。
    */
  })
}

/** 爬虫占今天原始请求的百分比。今天一条都没有时返回 null（不显示那句话） */
const botRatio = computed(() => {
  const total = Number(pv.value.today?.pv || 0)
  if (!total) return null
  const human = Number(pv.value.today?.pvHuman || 0)
  return Math.round(((total - human) / total) * 100)
})

/**
 * 趋势柱子的高度。
 *
 * ⚠ 按这 14 天里的**最大值**归一化，不按一个固定上限。固定上限的问题是：
 *   量小的站柱子全趴在底部看不出变化，量大了又全部顶格。
 *
 * ⚠ 非零的值至少给 2px。归一化之后 1 次浏览在一个 500 次的峰值旁边算出来是 0px，
 *   柱子直接消失——而「那天有 1 个人来」和「那天没人来」在这张图上必须能分开。
 */
function barHeight(value) {
  const list = pv.value.trend || []
  const max = list.reduce((m, d) => Math.max(m, Number(d.pvHuman || 0)), 0)
  const v = Number(value || 0)
  if (!max || !v) return '0px'
  return Math.max(2, Math.round((v / max) * 56)) + 'px'
}

/**
 * 这个来源是不是本站自己（站内跳转）。
 *
 * ⚠ 拿当前管理台的域名去比是不对的——管理台在 console.xxx，前台在 buyer.xxx。
 *   所以比的是**主域**：去掉第一段子域之后相同就算自己。
 *   不精确，但这一格只是给个提示，不参与任何计算。
 */
function isSelfHost(host) {
  if (!host || host === '(direct)') return false
  const root = h => h.split('.').slice(-3).join('.')
  return root(host) === root(window.location.hostname)
}

/**
 * 跳出率要不要标黄。
 *
 * ⚠ 阈值 70% 是个粗线，别当成「低于它就健康」。外贸站的首页跳出率天然就高
 *   （很多人搜进来看一眼不是目标产品就走），真正值得看的是**商品详情页**的跳出率，
 *   而那个要按页面类型拆开——现在还没做。这一格只是给个粗略提醒。
 *
 * ⚠ 来访次数太少时不标：三次来访里两次跳出就是 67%，那个百分比没有意义。
 */
const bounceHigh = computed(() => {
  const r = pv.value.engagement?.bounceRate
  return r !== null && r !== undefined && r > 70 && (pv.value.engagement?.sessions || 0) >= 20
})

/**
 * 国家二字码 → 中文名。
 *
 * ⚠ 只列了出海生意常见的那些，**不求全**（ISO 3166 有 249 个）。
 *   查不到就原样显示二字码——那比显示「未知」有用得多，
 *   运营至少还能自己查一下 MX 是哪儿。
 *
 * ⚠ ZZ 不是国家：那是 DB-IP 给保留段/未分配段的标记。
 */
const COUNTRY_LABELS = {
  US: '美国', CN: '中国', DE: '德国', GB: '英国', FR: '法国', IT: '意大利',
  ES: '西班牙', NL: '荷兰', BE: '比利时', PL: '波兰', SE: '瑞典', NO: '挪威',
  DK: '丹麦', FI: '芬兰', CH: '瑞士', AT: '奥地利', PT: '葡萄牙', IE: '爱尔兰',
  RU: '俄罗斯', UA: '乌克兰', TR: '土耳其', GR: '希腊', CZ: '捷克', RO: '罗马尼亚',
  CA: '加拿大', MX: '墨西哥', BR: '巴西', AR: '阿根廷', CL: '智利', CO: '哥伦比亚',
  JP: '日本', KR: '韩国', IN: '印度', ID: '印尼', TH: '泰国', VN: '越南',
  MY: '马来西亚', SG: '新加坡', PH: '菲律宾', PK: '巴基斯坦', BD: '孟加拉国',
  AU: '澳大利亚', NZ: '新西兰', ZA: '南非', EG: '埃及', NG: '尼日利亚',
  KE: '肯尼亚', MA: '摩洛哥', SA: '沙特', AE: '阿联酋', IL: '以色列',
  QA: '卡塔尔', KW: '科威特', IQ: '伊拉克', IR: '伊朗',
  HK: '中国香港', TW: '中国台湾', MO: '中国澳门',
  ZZ: '未知 / 保留段'
}
/*
  国家名的其余语言**不手写**，走浏览器自带的 Intl.DisplayNames。

  ⚠ 上面那张中文表留着不动：它里面有几个 Intl 给不出的说法（'中国香港'、
    'ZZ = 未知 / 保留段'），而且中文界面是日常在用的那一面，不该跟着这次改动变样。
    其余语言照着二字码现取，249 个国家一个都不用抄，也不会漏。

  取不到（老浏览器、或者根本不是国家码）就原样显示二字码——那比显示「未知」有用，
  看的人至少还能自己查一下 MX 是哪儿。
*/
const regionNames = new Map()
function regionName(code) {
  const lang = locale.value
  if (!regionNames.has(lang)) {
    try {
      regionNames.set(lang, new Intl.DisplayNames([lang], { type: 'region' }))
    } catch {
      regionNames.set(lang, null)
    }
  }
  const names = regionNames.get(lang)
  if (!names) return ''
  try {
    return names.of(code) || ''
  } catch {
    // 不是合法的地区码，Intl 会抛
    return ''
  }
}
const countryLabel = c => {
  if (!c) return c
  // ZZ 不是国家，Intl 也不认，两种语言都得自己说
  if (c === 'ZZ') return t('overview.pv.country.unknown')
  if (locale.value === 'zh') return COUNTRY_LABELS[c] || c
  return regionName(c) || COUNTRY_LABELS[c] || c
}

/** 设备。⚠ 要和后端 UserAgentParser 里那三个常量对得上 */
const DEVICE_KEYS = {
  mobile: 'overview.pv.dev.mobile',
  tablet: 'overview.pv.dev.tablet',
  desktop: 'overview.pv.dev.desktop'
}
const deviceLabel = d => (DEVICE_KEYS[d] ? t(DEVICE_KEYS[d]) : d)

/** pagetype。⚠ 要和后端 PageViewService.PAGE_TYPES 那份白名单对得上 */
const TYPE_KEYS = {
  home: 'overview.pv.type.home',
  list: 'overview.pv.type.list',
  detail: 'overview.pv.type.detail',
  cart: 'overview.pv.type.cart',
  checkout: 'overview.pv.type.checkout',
  account: 'overview.pv.type.account',
  other: 'overview.pv.type.other'
}
/* ⚠ 参数别再叫 t：那会盖住上面 import 进来的翻译函数 t */
const typeLabel = type => (TYPE_KEYS[type] ? t(TYPE_KEYS[type]) : type)

const go = path => router.push('/' + path)

// ⚠ 这里原来有一个 copy()，给「复制接口标识」那个按钮用的。
//   siteKey 那一列删了（§7.2），按钮和这个函数一起没了。

onMounted(() => {
  loadStat()
  // 同上那条规矩：没权限就别发，否则吃一个 403 弹成「当前操作没有权限」
  if (canQueryStat.value) loadPageViewStat()
  /*
    没有 site:storage:query 就别发这个请求。那张卡已经被 v-if 隐掉了，
    而这一发会吃一个 403，request 拦截器把它弹成「当前操作没有权限」——
    一句他既看不懂、也不需要处理的报错。这一条在 Files.vue 里就踩过。
  */
  if (canQueryStorage.value) loadStorage()
  // 只在开了买家可选币种时才拉汇率：没开这个功能的站，这一次请求纯属浪费
  if (buyerCurrencyList.value.length) loadRate()
})
</script>

<style scoped>
/* 卡片头：标题在左，「修改 / 保存」在右 */
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page {
  padding: 20px;
}
.mb16 { margin-bottom: 16px; }
.ml8 { margin-left: 8px; }
.mr4 { margin-right: 4px; }

.stat {
  text-align: center;
}
.stat-num {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.stat-hint {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-warn { color: var(--el-color-warning); }
.stat-danger { color: var(--el-color-danger); }

/*
  卡片吃满整行了（右边那一列撤掉之后，留个 10/24 的空槽看起来像有块东西没加载出来），
  但表单内容不能跟着拉满：宽屏上一个域名输入框拉到 1800px 很怪，
  底下那几行 hint 也会长到一行看不完。所以卡片满宽、内容限宽。
*/
.info-form {
  max-width: 760px;
}
.info-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.copy-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}
/* 越界语言的提示。用警告色而不是危险色：这不是填错了，是前台还没补文案 */
.hint-warn {
  color: var(--el-color-warning);
}

/* ---- 汇率卡 ---- */
.mt8 { margin-top: 8px; }
.mt16 { margin-top: 16px; }

.rate-stat {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.rate-time { font-size: 15px; font-weight: 600; }
.rate-age { font-size: 12px; color: var(--el-text-color-secondary); }
/*
  过期标红。这是整张卡存在的理由——汇率停更在前台没有任何症状，
  价格照常显示，只是停在几周前的数字上。不显眼就等于没做。
*/
.rate-stat.stale .rate-time,
.rate-stat.stale .rate-age { color: var(--el-color-danger); }

.rate-err {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  word-break: break-all;
}
.rate-num { font-variant-numeric: tabular-nums; }
.rate-ov code {
  padding: 1px 4px;
  background: var(--el-fill-color-light);
  border-radius: 3px;
  font-family: Consolas, Monaco, monospace;
}
.hint code {
  padding: 1px 4px;
  background: var(--el-fill-color-light);
  border-radius: 3px;
  font-family: Consolas, Monaco, monospace;
}
.text-muted {
  color: var(--el-text-color-secondary);
}

/* ---- 文件存储卡 ---- */
.mb8 { margin-bottom: 8px; }

/* 厂商按钮上的「已配置」小点 */
.dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-left: 5px;
  border-radius: 50%;
  background: var(--el-color-success);
  vertical-align: middle;
}

/*
  存储卡那两条 el-alert 里的 <code>：环境变量名和域名，要能一眼和正文分开。
  这些 <code> 是写在本组件模板里的，带着 scoped 的属性选择器，所以裸 code 选得中——
  不需要 :deep（那是给 Element 自己渲染出来的 DOM 用的）。
*/
.el-alert code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: Consolas, Monaco, monospace;
}

/* ==================== 前台浏览量 ==================== */

.pv-num {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

/* 来访质量那三个数比顶上四个小一档：它们是辅助指标，不是主角 */
.pv-num-sm {
  font-size: 20px;
}

/* 昨天那两个数字压一档：同一行里今天是主角 */
.pv-num-quiet {
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.pv-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pv-head-hint {
  font-size: 12px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

.pv-bot-note {
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pv-bot-hint {
  display: block;
  margin-top: 2px;
  color: var(--el-text-color-placeholder);
}

.pv-section-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

/*
  14 天趋势的柱状图。没有图表库，纯 CSS。

  ⚠ align-items: flex-end 是关键：柱子从底部往上长。
    不写的话高度不同的柱子会顶部对齐，图完全反过来。
*/
.pv-trend {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 76px;
  margin-bottom: 16px;
  padding-bottom: 2px;
}

.pv-bar-col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}

.pv-bar {
  width: 100%;
  max-width: 28px;
  border-radius: 2px 2px 0 0;
  background: var(--el-color-primary-light-3);
}

.pv-bar-day {
  margin-top: 4px;
  font-size: 10px;
  white-space: nowrap;
  color: var(--el-text-color-placeholder);
}

/*
  「有人看、但一个询盘都没有」那一格。

  ⚠ 这是整张表唯一标红的地方，是有意的：标红的东西一多就没人看了。
    这一格标出来的正是这个功能想让人发现的那件事。
*/
.pv-zero {
  font-weight: 600;
  color: var(--el-color-danger);
}

</style>

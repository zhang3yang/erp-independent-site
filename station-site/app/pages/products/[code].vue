<!--
  商品详情。整个站 SEO 价值最高的页面——买家在 Google 上搜的是具体产品，
  落地的就是这一页，然后在这一页上发询盘。所以这里做了三件首页不做的事：
    1. Product 结构化数据（JSON-LD），让 Google 展示价格和图片；
    2. 询盘表单预填商品编码和名称，商家一眼知道问的是哪个货；
    3. 商品名进 <title>，描述进 meta description。

  ## 版式照 kangwanjia-website-EN 的 pages/product/detail.vue

  上半屏是它那套：左右各半，左边 1:1 主图轮播（左右箭头 + 5 秒自动播 + 缩略图条），
  右边「标签 / 大标题 / 价格 / 一段简介 / 整宽按钮 / 一行说明」。
  规格参数也换成了它那种两列对照表。

  **只搬版式，没搬它的交易语义**：那边是零售站（加入购物车、免运费、30 天试用），
  这边是外贸询价站——按钮仍然是「加入询价清单」，数量默认填起订量，底下那行说明
  写的是「一个工作日内给含运费的报价」而不是「免运费」。站上不收款、运费按目的港算，
  写一句做不到的承诺比版式不一致糟得多（同一条理由见 lib/shop.ts 的文件头）。

  ## 首屏以下照 1688 国际站排（2026-09-25）

  吸顶分区导航（带询价按钮）→ 规格参数 → 3D → 商品详情（富文本 + 详情长图）→ 视频 → 询盘。
  原来是「详情 → 3D → 参数」：参数表压在十几屏图后面，一滚过首屏整页没有询价入口，
  切片长图还被一张张拆成带间距的圆角卡片。三处的理由各自写在下面对应的地方。

  没搬的还有「组合」选择器：库里没有变体/组合这个概念（u_s_sitegoods 一行就是一个货），
  硬造一个选了没用的选择器等于骗买家。等哪天有了变体数据再补这一块。

  ## description 是后端过滤过的 HTML

  商家在管理台里录的富文本，写入时已经过 RichTextCleaner 的白名单
  （script/iframe/事件属性/javascript: 全剥掉，排版和表格保留）。
  所以这里用 v-html 渲染是安全的——**前提是那一层过滤还在**。
  如果哪天有人把服务层的过滤去掉，这里就是一个现成的存储型 XSS 出口。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <!-- --hd-h 挂在整页容器上而不是导航条上：各分块的 scroll-margin-top 也要读它 -->
    <div class="wrap detail" :style="{ '--hd-h': headerH + 'px' }">
      <!-- 参考站没有面包屑。这里留着：它是从详情页回到分类页的唯一入口，
           也是给爬虫的层级信号，删掉是功能上的倒退 -->
      <nav class="crumb muted">
        <NuxtLink :to="localePath('/')">{{ t.home }}</NuxtLink>
        <span>/</span>
        <NuxtLink :to="localePath('/products')">{{ t.products }}</NuxtLink>
        <template v-if="product.category">
          <span>/</span>
          <!-- ⚠ query 里是原串（筛选键），显示的是当前语言的译名 -->
          <NuxtLink :to="{ path: localePath('/products'), query: { cat: product.category } }">
            {{ categoryLeaf(product.category, site.categoryNames) }}
          </NuxtLink>
        </template>
        <span>/</span>
        <span>{{ product.title }}</span>
      </nav>

      <!-- ============ 上半屏 ============ -->
      <div class="overview">
        <!-- 图库轮播 -->
        <div class="gallery">
          <div class="main-image" @mouseenter="pause" @mouseleave="resume">
            <Transition name="slide-fade" mode="out-in">
              <!--
                选了带图的规格（比如某个款式）时，主图先换成那张——买家点「灰色」
                是想看灰色长什么样。点缩略图 / 箭头就回到图库（见 goTo）。
              -->
              <img v-if="variantImage" :key="'variant:' + variantImage"
                   v-bind="imgAttrs(variantImage, 1200)" :alt="product.title" @error="onVariantError">
              <!--
                主图视频。封面用跟在它后面的第一张图（slide.poster），所以没播之前
                这一格看着和普通主图一样，只是多个播放键——不会在首屏留一块黑。
                controls 而不是 autoplay：外贸站访客很多在移动网络上，替他做主的代价是流量。
              -->
              <video
                v-else-if="slide?.kind === 'video'"
                :key="slide.src"
                class="main-video"
                :src="slide.src"
                :poster="slide.poster ? resizedImage(slide.poster, 1200) : undefined"
                controls
                playsinline
                preload="metadata"
                @play="onVideoPlay"
                @pause="onVideoStop"
                @ended="onVideoStop"
              ></video>
              <img v-else-if="slide && !brokenMain" :key="slide.src"
                   v-bind="imgAttrs(slide.src, 1200)" :alt="product.title" @error="onMainError">
              <div v-else class="ga-noimg"></div>
            </Transition>

            <template v-if="slides.length > 1">
              <button class="car-arrow car-arrow--prev" type="button"
                      :aria-label="t.imgPrev" @click="prev">‹</button>
              <button class="car-arrow car-arrow--next" type="button"
                      :aria-label="t.imgNext" @click="next">›</button>
            </template>

            <!--
              「查看 3D」：只在商品真的配了模型时出现，点了滚到下面的展示台。
              展示台放在参数表之下，首屏看不见——没有这个入口，买家不往下翻根本不知道有 3D。
              点击同时把 stageSeen 置 true：让 three.js 那个 chunk 立刻开始下，
              不用等滚动到位才触发 IntersectionObserver，飞过去时模型多半已经在加载了。
            -->
            <button v-if="modelUrl" class="btn-3d" type="button"
                    :class="{ 'btn-3d--raised': slide?.kind === 'video' }" @click="jumpTo3d">
              <svg class="btn-3d-cube" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2.5 3.5 7v10L12 21.5l8.5-4.5V7L12 2.5Zm0 2.3 6 3.2-6 3.2-6-3.2 6-3.2ZM5.5 9.3l5.5 2.9v6.6l-5.5-2.9V9.3Zm7.5 9.5v-6.6l5.5-2.9v6.6L13 18.8Z"
                      fill="currentColor"/>
              </svg>
              {{ shop.d3JumpLabel }}
            </button>
          </div>

          <!-- 缩略图既是导航也是索引 -->
          <div v-if="slides.length > 1" class="thumbs">
            <button
              v-for="(s, i) in slides"
              :key="s.kind + s.src"
              class="thumb"
              :class="{ 'thumb--active': i === current }"
              type="button"
              :aria-label="s.kind === 'video' ? t.mainVideo : fill(t.imgGoto, { n: s.no })"
              @click="goTo(i)"
            >
              <!-- 视频格没有自己的缩略图，借后面第一张图当封面；没有图时留空底色 -->
              <img v-if="s.kind === 'image' || s.poster" v-bind="imgAttrs(s.kind === 'video' ? s.poster : s.src, 160)"
                   alt="" loading="lazy" @error="onThumbError">
              <span v-if="s.kind === 'video'" class="thumb-play" aria-hidden="true">
                <svg viewBox="0 0 24 24"><path d="M8 5.5v13l11-6.5-11-6.5Z" fill="currentColor"/></svg>
              </span>
            </button>
          </div>
        </div>

        <!-- 询价盒 -->
        <div class="buy">
          <span v-if="product.featured" class="pill flag">{{ t.flagship }}</span>
          <h1>{{ product.title }}</h1>

          <!--
            ⚠ 这里原来是 `${product.currency} ${product.price}` 直接拼串，
              没走 formatMoney——显示成「USD 100」而不是「$100.00」，日元还会带上
              它本来就不该有的小数位。全站别处都走 formatMoney，只有这一页是拼的。
              接币种换算时一起修掉了：详情页是买家停留最久、最需要看到本地币种的一页。
          -->
          <!-- 选了型号就显示型号价：客户看到的必须是他选的那一款的价格，
               否则加进清单、结账时服务端一按型号重新定价，金额就变了。
               换算照旧走 money()，型号价和商品价是同一个币种（站点本币） -->
          <div class="price-row">
            <span class="price">
              {{ shownPrice > 0 ? money(shownPrice) : t.inquirePrice }}
            </span>
          </div>

          <p v-if="product.summary" class="lead">{{ product.summary }}</p>

          <!-- ============ 规格（二维：尺码 × 款式） ============
                商品配了规格维度时，组合按维度拆成一排一排来选（1688 那样），
                两排选中的搭配就是一条组合（SKU）。选中项的名字跟在维度名后面，
                不用低头去找哪个按钮是亮的。
                当前搭配下没有的取值画成虚线：**仍然能点**，点了会把另一排换到
                有货的那一个——比「灰着点不动」少一个死胡同。 -->
          <div v-if="isMatrix" class="opt-block">
            <div v-for="(dim, di) in options" :key="dim.code" class="opt-dim">
              <span class="field-label">
                {{ dim.name }}<span v-if="pickedName(di)" class="opt-picked">{{ pickedSep }}{{ pickedName(di) }}</span>
              </span>
              <div class="combo-options" role="radiogroup" :aria-label="dim.name">
                <button
                  v-for="v in dim.values"
                  :key="v.code"
                  type="button"
                  role="radio"
                  class="combo-opt"
                  :class="{
                    'combo-opt--active': picks[di] === v.code,
                    'combo-opt--na': picks[di] !== v.code && !reachable(di, v.code)
                  }"
                  :aria-checked="picks[di] === v.code"
                  @click="pickValue(di, v.code)"
                >
                  <img v-if="v.image" v-bind="imgAttrs(v.image, 100)" :alt="v.name" class="combo-thumb"
                       loading="lazy" @error="onThumbError">
                  <span class="combo-title">{{ v.name }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- ============ 组合（平铺一排） ============
               后端没配就是空数组，整块不渲染。参考站那份是写死的常量，这里是真数据：
               选中的会跟着「加入询价清单」和询盘一起提交给商家。 -->
          <div v-else-if="combos.length" class="combo-block">
            <span class="field-label">{{ t.combo }}</span>

            <!-- 折叠壳。高度由 maxHeight 绑定驱动，所以展开/收起是有过渡的；
                 DOM 一直是全的，不 v-if 掉多余的按钮——那样键盘用户展开时会丢焦点 -->
            <div
              class="combo-wrap"
              :class="{ 'combo-wrap--faded': comboOverflow && !comboOpen }"
              :style="{ maxHeight: comboMaxHeight }"
            >
              <div ref="comboListEl" class="combo-options">
                <button
                  v-for="c in combos"
                  :key="c.code"
                  type="button"
                  class="combo-opt"
                  :class="{ 'combo-opt--active': c.code === activeCombo }"
                  :aria-pressed="c.code === activeCombo"
                  @click="activeCombo = c.code"
                >
                  <img v-if="c.image" v-bind="imgAttrs(c.image, 100)" :alt="c.title" class="combo-thumb"
                       loading="lazy" @error="onThumbError">
                  <span class="combo-title">{{ c.title }}</span>
                </button>
              </div>
            </div>

            <button v-if="comboOverflow" type="button" class="combo-toggle"
                    :aria-expanded="comboOpen" @click="comboOpen = !comboOpen">
              {{ comboOpen ? t.comboLess : t.comboAll.replace('{n}', String(combos.length)) }}
              <span class="combo-caret" :class="{ 'combo-caret--up': comboOpen }"
                    aria-hidden="true">▾</span>
            </button>
          </div>

          <!-- 数量默认填起订量——买家要的必然不少于起订量，先填上能省掉一次来回 -->
          <!--
            ⚠ 数量 + 加入清单 + 「查看清单」**对游客也显示**。2026-09-10 当天这三样
              连同顶栏购物车一起按 buyer 藏过（「匿名只能询盘、下单要先登录」），
              同日撤掉——理由见 SiteHeader.vue 购物车那一段。
              要再藏回去的话三处一起藏：这里、SiteHeader.vue、ProductCard.vue。
          -->
          <div class="qty-block">
            <span class="field-label">{{ t.qtyLabel }}</span>
            <div class="qty" role="group" :aria-label="fill(shop.cartQtyFor, { name: product.title })">
              <button type="button" class="qty-step" :aria-label="shop.cartDecrease"
                      @click="qty = Math.max(1, qty - 1)">−</button>
              <input v-model.number="qty" class="qty-input" type="text" inputmode="numeric"
                     :aria-label="fill(shop.cartQtyFor, { name: product.title })">
              <button type="button" class="qty-step" :aria-label="shop.cartIncrease"
                      @click="qty = qty + 1">+</button>
              <span v-if="product.unit" class="qty-unit muted">{{ unitLabel }}</span>
            </div>
            <p v-if="product.moq" class="moq-note">
              {{ shop.moq }} {{ product.moq }}{{ unitLabel ? ' ' + unitLabel : '' }}
            </p>
          </div>

          <button type="button" class="btn btn-primary btn-block add-cart"
                  :class="{ 'add-cart--done': added }" @click="addToList">
            {{ added ? shop.cartAdded : shop.cartAdd }}
          </button>

          <NuxtLink v-if="added" :to="localePath('/cart')" class="view-cart">
            {{ shop.cartTitle }} →
          </NuxtLink>

          <!--
            询盘入口对所有人都在，而且对所有人都是一行小链接：主按钮的位子归
            「加入询价清单」。

            ⚠ 2026-09-10 那道登录门在时，游客态这里是主按钮样式——因为加购整块
              不渲染，右栏只剩标题价格加一大片空白，询盘不顶上去整页像没做完。
              加购块回来之后那个理由没了，两种人合成一行，不要再拆成两支。
          -->
          <a href="#inquiry" class="view-cart">{{ t.inquire }} →</a>

          <!-- 引导式下单（2026-09-28）：这个商品挂在向导里时才出现，logo / 包装 / 尺码搭配走那边 -->
          <NuxtLink v-if="site.wizard?.enabled && site.wizard.goods?.includes(product.code)"
                    :to="localePath('/configure')" class="view-cart">{{ shop.wzEntry }} →</NuxtLink>

          <!-- 参考站这里写的是「免运费 + 30 天试用」。站上不收款、运费按目的港算，
               那句话这个站兑现不了，所以换成兑现得了的那句 -->
          <p class="ship-note">⛟ {{ t.shipNote }}</p>
        </div>
      </div>

      <!-- ============ 分区导航（吸顶） ============
           1688 国际站那条：往下翻长长的详情图时，顶上一直有「参数 / 详情 / …」和询价按钮。
           没有它的话，一滚过首屏，直到页底表单之前整页都没有一个能发询盘的地方——
           买家看完图想问价，得自己往下找。
           ⚠ top 吃 --hd-h（顶栏实际高度，onMounted 里量）：顶栏在窄屏会换行变高，
             写死 62px 的话导航会钻到顶栏底下去 -->
      <nav class="sec-nav" :aria-label="t.sections">
        <div class="sec-tabs">
          <a v-for="tab in secTabs" :key="tab.id" :href="'#' + tab.id" class="sec-tab"
             :class="{ 'sec-tab--on': tab.id === activeSec }"
             :aria-current="tab.id === activeSec ? 'true' : undefined"
             @click.prevent="jumpToSection(tab.id)">{{ tab.label }}</a>
        </div>
        <a href="#inquiry" class="btn btn-primary sec-cta" @click.prevent="jumpToSection('inquiry')">
          {{ t.inquire }}
        </a>
      </nav>

      <!-- ============ 规格参数 ============
           放在详情图**前面**，和 1688 国际站一样：Key attributes 在上，商家的详情长图在下。
           B2B 买家先要确认尺码、材质、起订量这些「对不对得上」，再看图；
           原来参数表压在一长串图后面，要翻十几屏才看得到。

           参考站那种两列对照表。左右两列是**同一张表按行数对半分**的，
           不是「性能」「结构」那种分类——分类信息库里没有，硬给两列起名字
           就得靠猜哪个参数算性能，猜错了比不分类更误导 -->
      <section v-if="specRows.length" id="specs" class="specs">
        <div class="section-head">
          <h2>{{ t.specsTitle }}<span class="accent">{{ t.specsTitleAccent }}</span></h2>
          <p>{{ t.specsLead }}</p>
        </div>

        <div class="specs-grid">
          <div v-for="(col, ci) in specCols" :key="ci" class="spec-col">
            <div v-for="row in col" :key="row[0]" class="spec-row">
              <span class="spec-key">{{ row[0] }}</span>
              <span class="spec-val">{{ row[1] }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 3D 展示台 ============
           只有商品真的配了模型才出现。没配就不渲染，也就不会去下 three.js
           那个包——见下面 modelUrl 和 stageSeen 的注释 -->
      <section v-if="modelUrl" id="model-3d" ref="stageEl" class="showcase">
        <div class="section-head showcase-head">
          <h2>{{ shop.d3Title }}<span class="accent">{{ shop.d3TitleAccent }}</span></h2>
          <p>{{ shop.d3Lead }}</p>
        </div>

        <div class="showcase-viewport">
          <span class="showcase-floor" aria-hidden="true"></span>

          <!-- Lazy + v-if：进视口之前连组件的 chunk 都不请求 -->
          <LazyProduct3DViewer
            v-if="stageSeen"
            ref="viewer"
            v-model:active-view="viewIndex"
            v-model:auto-rotate="autoSpin"
            :views="views"
            :src="modelUrl"
            :poster="product.images?.[0] || ''"
            :loading-text="shop.d3Loading"
            :unavailable-text="shop.d3Unavailable"
            :marks="product.model3dMarks"
            :states="modelStates"
            :variants="variantModels"
            :variant-code="activeCombo || null"
            v-model:power-on="powerOn"
            v-model:state-id="stateId"
            @power-available="v => (hasPower = v)"
            @state-status="onStateStatus"
          />
        </div>

        <!-- 模型状态（2026-10-08，如腿套没气/半充/充满）：接口给了 2 个以上带模型的状态才出现 -->
        <div v-if="modelStates" class="showcase-states" role="group">
          <button
            v-for="s in modelStates"
            :key="s.id"
            type="button"
            class="showcase-btn"
            :class="{ 'is-on': s.id === stateId }"
            :aria-pressed="s.id === stateId"
            :disabled="!stageSeen"
            @click="pickState(s.id)"
          >
            <span v-if="isStateLoading(s.id)" class="showcase-btn-spin" aria-hidden="true"></span>{{ s.name }}
          </button>
          <p v-if="stateFailed" class="showcase-state-error" role="alert">{{ shop.d3StateFailed }}</p>
        </div>
        <p v-if="variantFailed" class="showcase-state-error" role="alert">{{ shop.d3ComboFailed }}</p>

        <div class="showcase-caption">
          <div :key="currentView.label">
            <h3>{{ currentView.label }}</h3>
            <p>{{ currentView.note }}</p>
          </div>
        </div>

        <div class="showcase-controls">
          <button
            type="button"
            class="showcase-btn"
            :class="{ 'is-on': autoSpin }"
            :aria-pressed="autoSpin"
            @click="autoSpin = !autoSpin"
          >{{ autoSpin ? shop.d3AutoOn : shop.d3AutoOff }}</button>
          <button type="button" class="showcase-btn" @click="resetView">{{ shop.d3Reset }}</button>
          <!-- 标注里有屏幕和开机键才出现。和画布上点开机键是同一个开关；手机上点不准那个小光圈时用它。
               屏幕只在主状态（第一个）上，切到别的状态时不出现 -->
          <button
            v-if="hasPower && onMainState"
            type="button"
            class="showcase-btn"
            :class="{ 'is-on': powerOn }"
            :aria-pressed="powerOn"
            @click="powerOn = !powerOn"
          >{{ powerOn ? shop.d3PowerOff : shop.d3PowerOn }}</button>
          <!-- 买家 3D 定制（2026-09-29）：新标签页打开干净的单独页面。只认接口的 model3d 字段——
               后端定制接口也只认它，规格键里填的老模型地址进不去定制页 -->
          <NuxtLink
            v-if="canCustomize"
            :to="localePath(`/customize/${encodeURIComponent(product.code)}`) + (onVariantModel ? `?combo=${encodeURIComponent(activeCombo)}` : '')"
            target="_blank"
            class="showcase-btn showcase-btn-cta"
          >{{ shop.czEntry }}</NuxtLink>
          <div class="showcase-dots">
            <button
              v-for="(view, i) in views"
              :key="view.key"
              type="button"
              class="showcase-dot"
              :class="{ active: i === viewIndex }"
              :aria-label="view.label"
              @click="viewIndex = i"
            ></button>
          </div>
        </div>
      </section>

      <!-- ============ 商品详情（富文本 + 详情图） ============
           渲染的是 descHtml 不是 product.description 原串：图片要补懒加载、换缩略地址
           （理由见脚本里 descHtml 的注释） -->
      <section v-if="product.description" id="details" class="desc">
        <div class="section-head"><h2>{{ t.details }}</h2></div>
        <!-- eslint-disable-next-line vue/no-v-html -- 见文件头注释：写入侧已过白名单 -->
        <div ref="descEl" class="desc-body" v-html="descHtml"></div>
      </section>

      <!-- 视频。没挑过就是空数组，整块不渲染 -->
      <section v-if="product.videos?.length" id="videos" class="videos">
        <h2>{{ t.video }}</h2>
        <video v-for="(v, i) in product.videos" :key="i" :src="v" controls preload="none"></video>
      </section>

      <section id="inquiry" class="inq">
        <h2>{{ fill(t.inquireAbout, { name: product.title }) }}</h2>
        <InquiryForm
          :lang="site.lang"
          :goods-code="product.code"
          :product-title="product.title"
          :combo-code="activeCombo"
          :fallback-email="site.contact?.email"
        />
      </section>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { buildViews, type ViewKey } from '~/lib/product3d/views'
import { categoryLeaf } from '~/lib/categories'
import { fill } from '~/composables/useShopCopy'
import { isUsableImageUrl } from '~/lib/shop'
import { localizeUnit } from '~/lib/units'
import { resizedImage, fallbackToOriginal } from '~/lib/imageUrl'
// 只引不带 three 的解析：three 要等展示台进视口才下（见 Product3DViewer 头注释）
import { parseStates } from '@station-shared/model3d/stateList.js'
import type { StateStatus } from '@station-shared/model3d/states.js'

const site = await useSiteInfo()
const route = useRoute()
const localePath = useLocalePath()
const shopCopy = useShopCopy(() => site.lang)
const shop = computed(() => shopCopy.value)

/* 价格按买家在顶栏选的币种换算显示 */
const currency = useDisplayCurrency(() => site)
const money = (n: number) => currency.displayMoney(n, site.lang)
/** 起订量单位按语言显示（商家填的是 pcs / 件 这种，库里不分语言） */
const unitLabel = computed(() => localizeUnit(product.value?.unit, site.lang))

const code = computed(() => String(route.params.code || ''))

const { data: product } = await useAsyncData(
  () => `product-${site.lang}-${code.value}`,
  () => useProductDetail(code.value, site.lang),
  { watch: [code, () => site.lang] }
)

// 后端对「没上架」和「不存在」一律返回 null，不区分——省得被人拿来探测有哪些商品
if (!product.value) {
  throw createError({ statusCode: 404, statusMessage: 'Product not found', fatal: true })
}

/* ---------------- 图库轮播 ----------------

   先过 isUsableImageUrl 再渲染：商家在管理台填的可能是相对路径，那种地址
   在服务端渲染出来就是个裂图，而 @error 抓不到水合之前发生的加载失败
   （原因见 lib/shop.ts 里那个函数的注释）。

   刻意**不**像参考站那样 slice(0, 6) 截断：那边是写死的六张图，这边是商家自己上的，
   截掉第七张等于把他上传的图藏起来。缩略图条会换行，多几张只是多一行。 */

const images = computed(() => (product.value?.images || []).filter(isUsableImageUrl))

/**
 * 主图视频：图库的第一格。
 *
 * ⚠ 和详情底部那一排 `product.videos` 是两个位置，互不相干——这里只认接口的
 * `mainVideo`，商家在管理台是分开挑的。
 *
 * 判定和 modelUrl 一样只放行相对路径和 https：http 视频在 https 页面上会被浏览器
 * 当混合内容拦掉，与其在首屏留一个永远转圈的播放器，不如当成没配。
 */
const mainVideo = computed(() => {
  const url = String(product.value?.mainVideo || '').trim()
  return /^(\/|https:\/\/)/.test(url) ? url : ''
})

type Slide =
  | { kind: 'video'; src: string; poster: string; no: 0 }
  | { kind: 'image'; src: string; poster: ''; no: number }

/**
 * 轮播的格子。有主图视频时它排在最前面，后面照旧是图片。
 *
 * 视频的封面**不是**单独配的，就是紧跟其后的第一张图——商家换主图，封面自动跟着换，
 * 不会出现「封面还是上一版的图」。没有图时 poster 为空串，那一格就是播放器的默认黑底。
 *
 * `no` 是这张图在**图片里**的序号（从 1 起），给缩略图的无障碍文案用：
 * 视频占了第 0 格，不能直接拿数组下标当「第几张图」。
 */
const slides = computed<Slide[]>(() => {
  const list: Slide[] = images.value.map((src, i) => ({ kind: 'image', src, poster: '', no: i + 1 }))
  if (mainVideo.value) {
    list.unshift({ kind: 'video', src: mainVideo.value, poster: images.value[0] || '', no: 0 })
  }
  return list
})

const current = ref(0)
const brokenMain = ref(false)
/** 鼠标停在主图上。停着的时候不自动翻页——买家正在看这一张 */
const hovering = ref(false)
/** 主图视频正在播。播着的时候更不能翻页：翻走等于把播放器拆了 */
const videoPlaying = ref(false)

/** 当前这一格。取不到（一张图都没有）时是 undefined，模板退占位块 */
const slide = computed<Slide | undefined>(() => slides.value[current.value])

// 换商品（同一个页面组件切 code）时回到第一格
watch(product, () => {
  current.value = 0
  brokenMain.value = false
  videoPlaying.value = false
})
// 换格时重置：上一张挂了不代表下一张也挂；离开视频格时播放器已经被 v-if 拆掉，
// 它的 pause 事件不一定还能到得了这里，所以在这里把标志收干净
watch(current, () => {
  brokenMain.value = false
  videoPlaying.value = false
})

/**
 * 主图加载失败 → 退占位块。
 *
 * 多一道 isConnected：主图套在 `<Transition :key="current">` 里，切图时元素会被
 * 换掉，在飞的请求被取消**也会触发 error**。那种 error 来自一个已经不在文档里的
 * 元素，跟图片本身好不好没关系，认下来会把好图判成坏图。
 */
function onMainError(e: Event) {
  const el = e.target as HTMLImageElement | null
  if (el && !el.isConnected) return
  // 缩略地址挂了先换回原图，原图也挂了才算坏图
  if (fallbackToOriginal(el)) return
  brokenMain.value = true
}

/**
 * 图片的 src + data-orig。换成了缩略地址时才带 data-orig，供 error 时换回原图；
 * 没换（外链、相对路径）就不带——否则「换回原图」等于原地重试一次同一个坏地址。
 */
function imgAttrs(src: string, width: number) {
  const small = resizedImage(src, width)
  return small !== src ? { src: small, 'data-orig': src } : { src }
}

function onThumbError(e: Event) {
  fallbackToOriginal(e.target as HTMLImageElement | null)
}

let galleryTimer: ReturnType<typeof setInterval> | null = null

function stopTimer() {
  if (galleryTimer) {
    clearInterval(galleryTimer)
    galleryTimer = null
  }
}

/**
 * 起自动播。三个「别翻页」的理由在这里一起判，调用方不用各自记得判：
 * 只有一格、鼠标停在图上、视频正在播。
 */
function startTimer() {
  stopTimer()
  if (slides.value.length < 2) return
  if (hovering.value || videoPlaying.value) return
  // 正在看刚选的规格图：五秒后被轮播翻走，等于没给他看
  if (variantImage.value) return
  galleryTimer = setInterval(() => goTo(current.value + 1, false), 5000)
}

/** reset=true 时重排倒计时——手动点过之后不该马上被自动播接走 */
function goTo(i: number, reset = true) {
  const n = slides.value.length
  if (!n) return
  current.value = ((i % n) + n) % n
  if (reset) {
    // 手动翻图库 = 不看规格图了
    variantImage.value = ''
    startTimer()
  }
}

function next() { goTo(current.value + 1) }
function prev() { goTo(current.value - 1) }

function pause() {
  hovering.value = true
  stopTimer()
}

function resume() {
  hovering.value = false
  startTimer()
}

function onVideoPlay() {
  videoPlaying.value = true
  stopTimer()
}

/** 暂停或播完：自动播可以接着走了（鼠标还停在上面的话 startTimer 自己会拦下来） */
function onVideoStop() {
  videoPlaying.value = false
  startTimer()
}

// setInterval 是客户端的事，onMounted 在 SSR 时不会跑
onMounted(startTimer)
onBeforeUnmount(stopTimer)
watch(slides, startTimer)

/* ---------------- 组合 ---------------- */

/** 后端已经把没名字的、停用的都滤掉了，这里拿到的都是能显示的 */
const combos = computed(() => product.value?.combos || [])

/** 选中的型号编码。没有型号时是空串——那时下面所有型号相关的逻辑都退化成「没选」 */
const activeCombo = ref(combos.value[0]?.code || '')
// 换商品时要重选：上一个商品的型号编码在新商品上不存在，留着会让价格算错
watch(product, () => { activeCombo.value = combos.value[0]?.code || '' })

const activeComboItem = computed(() =>
  combos.value.find(c => c.code === activeCombo.value) || null)

/* ---------------- 规格（二维：尺码 × 款式） ----------------

   后端给 options（维度 + 取值）和带 opt1/opt2 的组合。这里只做一件事：
   把「每一排选中了哪个取值」翻译成「选中了哪一条组合」，写回 activeCombo。
   **价格、加入清单、询盘全都照旧读 activeCombo**——它们不知道也不需要知道有两排。 */

const options = computed(() => (product.value?.options || []).filter(d => d.values?.length))
/** 有维度、组合也确实带着取值编码，才按两排画；否则退回平铺一排 */
const isMatrix = computed(() => options.value.length > 0 && combos.value.some(c => c.opt1))

/** 每一排选中的取值编码。下标 0 = 第一维，1 = 第二维 */
const picks = ref<string[]>([])

function syncPicksFromCombo() {
  const c = activeComboItem.value
  picks.value = c ? [c.opt1 || '', c.opt2 || ''] : []
}
syncPicksFromCombo()
watch(product, syncPicksFromCombo)

/** 两排取值 → 那一条组合。只有一个维度时第二个不看 */
function comboFor(p1?: string, p2?: string) {
  const two = options.value.length > 1
  return combos.value.find(c => c.opt1 === p1 && (!two || c.opt2 === p2)) || null
}

/** 在另一排保持不动的前提下，这个取值有没有货。只有一个维度时就是「有没有这条组合」 */
function reachable(di: number, code: string) {
  if (options.value.length < 2) return !!comboFor(code)
  return di === 0 ? !!comboFor(code, picks.value[1]) : !!comboFor(picks.value[0], code)
}

/**
 * 点了某一排的某个取值。
 *
 * 当前搭配有货就直接选中；没有（比如 XXL 只做黑色、此刻选的是灰色），
 * 就把另一排换成这个取值下排在最前面的那一个——买家点的是「XXL」，
 * 他要的是 XXL，而不是一个点了没反应的按钮。
 */
function pickValue(di: number, code: string) {
  const next = [...picks.value]
  next[di] = code
  let c = comboFor(next[0], next[1])
  if (!c) c = combos.value.find(x => (di === 0 ? x.opt1 : x.opt2) === code) || null
  if (!c) return
  picks.value = [c.opt1 || '', c.opt2 || '']
  activeCombo.value = c.code
  // 主图换成这个选择对应的图：组合自己的图优先，其次是刚点的那个取值的图（款式图）
  const valueImage = options.value[di]?.values.find(v => v.code === code)?.image
  const img = [c.image, valueImage].find(u => isUsableImageUrl(u))
  if (img) showVariant(img)
}

/** 维度名和选中项之间的冒号：中日文用全角，其余语言用「: 」 */
const pickedSep = computed(() => (['zh', 'ja'].includes(site.lang) ? '：' : ': '))

/** 维度名后面跟的「：黑色」 */
function pickedName(di: number) {
  const code = picks.value[di]
  return options.value[di]?.values.find(v => v.code === code)?.name || ''
}

/* 主图的「规格图」。有值时主图区显示它而不是图库当前格，自动轮播停下；
   手动点缩略图 / 箭头就清掉，回到图库（goTo 里）。 */
const variantImage = ref('')

function showVariant(src: string) {
  variantImage.value = src
  stopTimer()
}

function onVariantError(e: Event) {
  if (fallbackToOriginal(e.target as HTMLImageElement | null)) return
  // 规格图坏了就别挡着图库
  variantImage.value = ''
  startTimer()
}

watch(product, () => { variantImage.value = '' })

/**
 * 页面上显示的价格（换算前的站点本币金额，交给 money() 去换算和格式化）。
 * 选了型号就是型号价。
 *
 * ⚠ 这只是显示。真正算钱的是服务端（SiteOrderService.repriceLines 按 comboCode 现查），
 * 这里改成什么都不影响成交价——但两边必须一致，否则买家提交前后看到的金额不一样。
 */
const shownPrice = computed(() =>
  Number(activeComboItem.value ? activeComboItem.value.price : product.value?.price) || 0)

/*
  折叠到两行，跟参考站一致。

  ## 为什么要量，不数个数

  按「超过 N 个就折」是错的：型号名长短差得很远（「机器」和「机器、腰带和靴子」），
  四个长名字就已经占三行，四个短的还在一行里。所以量的是**列表实际多高**，
  超过两行才折——折叠壳被裁短，量的是里面那个列表的 scrollHeight。

  ## 142px 是怎么来的

  两行卡片 + 一个间距：卡高 66px（44px 缩略图 + 上下各 10px 内边距 + 1px 描边）×2 + 10px。
  改卡的内边距或缩略图尺寸时要跟着改这个数——不然会裁在第二行中间。
  底部那 48px 的渐变本来就压着第二行的下半截，那是「下面还有」的信号，不是裁坏了。
*/
const COMBO_COLLAPSED_H = 142

const comboListEl = ref<HTMLElement | null>(null)
const comboOpen = ref(false)
/** 0 = 还没量过。SSR 时按折叠高度渲染，型号少的时候本来就装得下，等于没折 */
const comboFullH = ref(0)

const comboOverflow = computed(() => comboFullH.value > COMBO_COLLAPSED_H + 4)
const comboMaxHeight = computed(() =>
  comboOpen.value ? `${comboFullH.value}px` : `${COMBO_COLLAPSED_H}px`)

// 换商品时收起来：上一个商品展开过，不代表这一个也该展开
watch(product, () => { comboOpen.value = false })

onMounted(() => {
  const el = comboListEl.value
  if (!el) return
  const measure = () => { comboFullH.value = el.scrollHeight }
  measure()
  // 字体晚到、窗口变窄都会让换行变多，量一次不够
  if (typeof ResizeObserver === 'undefined') return
  const ro = new ResizeObserver(measure)
  ro.observe(el)
  onBeforeUnmount(() => ro.disconnect())
})

/* ---------------- 加入询价清单 ---------------- */

const { add } = useCart()

const qty = ref(Math.max(1, Number(product.value.moq) || 1))
watch(product, p => { qty.value = Math.max(1, Number(p?.moq) || 1) })

const added = ref(false)
let addedTimer: ReturnType<typeof setTimeout> | null = null

function addToList() {
  if (!product.value) return
  // 手输可能是空串或者负数，加之前先夹一下
  const n = Math.max(1, Math.floor(Number(qty.value) || 1))
  qty.value = n
  // 带上选中的型号：清单里按「商品 + 型号」分行，价格也跟着型号走
  add(product.value, n, activeComboItem.value, site.lang)
  added.value = true
  if (addedTimer) clearTimeout(addedTimer)
  addedTimer = setTimeout(() => { added.value = false }, 2400)
}

onBeforeUnmount(() => { if (addedTimer) clearTimeout(addedTimer) })

/* ---------------- 规格参数表 ----------------

   商家能填的规格有两处：`spec` 一个自由文本字段，和 `specs` 一张键值表。
   起订量、单位、分类这些本来在概要栏那个 dl 里，现在概要栏按参考站的样子只留
   标签/标题/价格/简介，所以它们并进这张表——数据一条都没少，只是挪了位置。 */

const specRows = computed<Array<[string, string]>>(() => {
  const p = product.value
  if (!p) return []
  const rows: Array<[string, string]> = []
  if (p.spec) rows.push([t.value.spec!, String(p.spec)])
  if (p.moq) rows.push([shop.value.moq!, `${p.moq}${unitLabel.value ? ' ' + unitLabel.value : ''}`])
  else if (p.unit) rows.push([t.value.unit!, unitLabel.value])
  if (p.category) rows.push([t.value.category!, String(p.category).replace(/\s*[/／>＞]\s*/g, ' / ')])
  for (const [k, v] of Object.entries(p.specs || {})) {
    // model3d / 3d 是模型文件地址，不是给买家看的参数（见下面 modelUrl）
    if (k === 'model3d' || k === '3d') continue
    if (v === null || v === undefined || String(v).trim() === '') continue
    rows.push([k, String(v)])
  }
  return rows
})

/** 按行数对半分成两列，左列多的那一行 */
const specCols = computed(() => {
  const rows = specRows.value
  const half = Math.ceil(rows.length / 2)
  return [rows.slice(0, half), rows.slice(half)].filter(c => c.length)
})

/* ---------------- 3D 展示台 ----------------

   ## 什么时候显示

   商品**真的有模型**才显示。地址来自详情接口的 `model3d`（2026-09-20 起后端下发，
   管理台「商品」页从文件库挑）。后端有这个字段之前商家把地址填在规格参数的
   `model3d` / `3d` 键里，那批老商品仍然认——接口字段优先，规格键兜底。

   刻意不做「没有模型就拿替身模型顶上」：那个替身长得像一台气压按摩仪
   （见 lib/product3d/placeholderModel.ts），配在一个阀门商品下面比不显示 3D 糟得多。
   想看替身长什么样，去 /3d-demo。

   ## 为什么等进视口才渲染

   three.js 那一坨大约 160KB（gzip）。商品页上绝大多数买家只看图片和参数，
   一进页面就把它下下来是白花的流量——外贸站的访客网络条件差异很大。
   `LazyProduct3DViewer` + `v-if="stageSeen"` 让这个 chunk 只在这一屏被滚到时才请求。 */

const modelUrl = computed(() => {
  const p = product.value
  if (!p) return ''
  const fromSpecs = p.specs?.model3d ?? p.specs?.['3d']
  // 接口字段优先（null/undefined 都当没配），规格键兜底
  const url = String(p.model3d || fromSpecs || '').trim()
  // 只认相对路径和 https。http 的模型在 https 页面上会被浏览器直接拦掉（混合内容），
  // 与其让它静默失败，不如当成没配
  return /^(\/|https:\/\/)/.test(url) ? url : ''
})

const stageEl = ref<HTMLElement | null>(null)
const stageSeen = ref(false)
const viewIndex = ref(0)
const autoSpin = ref(true)
/** 3D 标注的屏幕开关；hasPower 由展示台加载完标注后告诉我们 */
const powerOn = ref(false)
const hasPower = ref(false)
/** 能不能进定制页：商品真的挂了 glb（接口字段，不认规格键兜底），和后端 CustomPlanService 同一个判定 */
/** 模型状态：parseStates 过滤后的列表（不足 2 个为 null，不出按钮）；stateId 和展示台双向绑定 */
const modelStates = computed(() => {
  const combo = combos.value.find(c => c.code === activeCombo.value)
  return combo?.model3d
    ? parseStates(combo.model3dStates)
    : parseStates(product.value?.model3dStates)
})
const stateId = ref<string | null>(null)
const stateLoading = ref<string | null>(null)
const stateFailed = ref(false)
const onMainState = computed(() => !modelStates.value || !stateId.value || stateId.value === modelStates.value[0]?.id)
/* 规格各自的模型（2026-10-09）：配了 model3d 的规格交给展示台，选中它时展示台切过去 */
const variantModels = computed(() => combos.value
  .filter(c => /^(\/|https:\/\/)/.test(String(c.model3d || '').trim()))
  .map(c => ({ code: c.code, name: c.title, model: String(c.model3d).trim(), marks: c.model3dMarks ?? null, states: c.model3dStates ?? null })))
/** 当前规格有独立模型：状态按钮切换到这条规格自己的状态，定制链接带上规格 */
const onVariantModel = computed(() => variantModels.value.some(v => v.code === activeCombo.value))
const variantFailed = ref(false)
watch(activeCombo, () => {
  variantFailed.value = false
  stateFailed.value = false
  stateId.value = modelStates.value?.[0]?.id ?? null
})
function onStateStatus(id: string, status: StateStatus) {
  if (id.startsWith('c:')) {
    variantFailed.value = status === 'failed' && id.startsWith('c:' + activeCombo.value)
    return
  }
  stateLoading.value = status === 'loading' ? id : stateLoading.value === id ? null : stateLoading.value
  stateFailed.value = status === 'failed'
}
/** 点状态按钮：先清掉上一次的失败提示（失败时展示台会把 stateId 改回去，不能拿 stateId 变化来清） */
function pickState(id: string) {
  stateFailed.value = false
  stateId.value = id
}
function isStateLoading(id: string) {
  return stateLoading.value === id || stateLoading.value === `c:${activeCombo.value}:${id}`
}
const canCustomize = computed(() => /^(\/|https:\/\/)/.test(String(product.value?.model3d || '').trim()))
const viewer = ref<{ reset: () => void } | null>(null)

const views = computed(() =>
  buildViews(
    (key: ViewKey) => shop.value[`d3View${cap(key)}`] || key,
    (key: ViewKey) => shop.value[`d3View${cap(key)}Note`] || ''
  )
)
const currentView = computed(() => views.value[viewIndex.value] || views.value[0]!)

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

function resetView() {
  // 拖动过之后索引可能还是 0，赋同一个值不会触发 watcher，
  // 所以这种情况直接让 viewer 自己飞回去
  if (viewIndex.value === 0) viewer.value?.reset()
  else viewIndex.value = 0
}

/** 顶部「查看 3D」按钮：先让 chunk 开始下，再平滑滚到展示台 */
function jumpTo3d() {
  stageSeen.value = true
  stageEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  if (!modelUrl.value) return
  const host = stageEl.value
  if (!host) return
  // 不支持 IntersectionObserver 的老浏览器：直接渲染，功能优先
  if (typeof IntersectionObserver === 'undefined') {
    stageSeen.value = true
    return
  }
  const io = new IntersectionObserver(entries => {
    if (entries.some(e => e.isIntersecting)) {
      stageSeen.value = true
      io.disconnect()
    }
  }, { rootMargin: '200px' })
  io.observe(host)
  onBeforeUnmount(() => io.disconnect())
})

/* ---------------- 详情富文本 ----------------

   商家的 HTML 原样进来，这里只动 <img> 一种标签，补两件事：

   1. loading="lazy" + decoding="async"。v-html 进来的图不会自己懒加载，
      原来一进页面就把详情里所有图一起下——EB-BOOT-01 一页八张、单张最大 3.3 MB，
      买家还停在首屏，几十 MB 已经在路上了。商家自己写了 loading 的不覆盖。
   2. src 换成缩略地址（lib/imageUrl.ts），原图放 data-orig，挂了由下面的监听换回。
      1500 = 版面上最宽 750px × 2 倍屏。

   只做字符串替换，不解析成 DOM：这段在服务端也要跑（SSR 出来的 HTML 就得是懒加载的），
   服务端没有 DOM。标签结构是后端白名单洗过的，正则够用。 */

const descHtml = computed(() => {
  const html = String(product.value?.description || '')
  return html.replace(/<img\b([^>]*)>/gi, (_tag, attrs: string) => {
    let a = attrs
    const m = a.match(/\bsrc\s*=\s*(["'])(.*?)\1/i)
    if (m && !m[2]!.includes('"')) {
      const orig = m[2]!
      const small = resizedImage(orig, 1500)
      if (small !== orig) a = a.replace(m[0], `src="${small}" data-orig="${orig}"`)
    }
    if (!/\bloading\s*=/i.test(a)) a = ' loading="lazy"' + a
    if (!/\bdecoding\s*=/i.test(a)) a = ' decoding="async"' + a
    return `<img${a}>`
  })
})

const descEl = ref<HTMLElement | null>(null)

/*
  详情图的「缩略地址挂了换原图」。v-html 里的元素挂不上 @error，所以在容器上
  捕获阶段监听（error 不冒泡，只能捕获）。
  ⚠ 水合之前就已经失败的图，监听挂上时事件早过去了——挂完补扫一遍
    「已经加载完却没有宽度」的那些。
*/
onMounted(() => {
  const host = descEl.value
  if (!host) return
  const onErr = (e: Event) => {
    if (e.target instanceof HTMLImageElement) fallbackToOriginal(e.target)
  }
  host.addEventListener('error', onErr, true)
  host.querySelectorAll<HTMLImageElement>('img[data-orig]').forEach(img => {
    if (img.complete && img.naturalWidth === 0) fallbackToOriginal(img)
  })
  onBeforeUnmount(() => host.removeEventListener('error', onErr, true))
})

/* ---------------- 分区导航 ----------------

   页上真有哪几块，导航就列哪几块——没配 3D 的商品不该有一个点了没反应的「3D」。
   询价不在这一排里，它是右边那个按钮。 */

const secTabs = computed(() => {
  const tabs: Array<{ id: string; label: string }> = []
  if (specRows.value.length) tabs.push({ id: 'specs', label: t.value.specsTab! })
  if (modelUrl.value) tabs.push({ id: 'model-3d', label: '3D' })
  if (product.value?.description) tabs.push({ id: 'details', label: t.value.details! })
  if (product.value?.videos?.length) tabs.push({ id: 'videos', label: t.value.video! })
  return tabs
})

/** 顶栏实际高度。SSR 时给宽屏的 62px；窄屏顶栏换行会变高，挂载后按实量的改 */
const headerH = ref(62)
/** 当前滚到了哪一块（导航上高亮它）。还在首屏时是空串，一个都不亮 */
const activeSec = ref('')

function jumpToSection(id: string) {
  // 3D 那块等进视口才下 three.js；点导航直接飞过去时先让它开始下
  if (id === 'model-3d') stageSeen.value = true
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  const measure = () => {
    const hd = document.querySelector<HTMLElement>('.hd')
    if (hd) headerH.value = hd.offsetHeight
  }
  /*
    滚动定位：最后一个「顶边已经越过导航条下沿」的分块就是当前块。
    用 scroll + rAF 而不是 IntersectionObserver：几块高矮差得很远（参数表半屏、
    详情十几屏），IO 的阈值怎么设都有一头判不准。
  */
  let raf = 0
  const spy = () => {
    raf = 0
    const line = headerH.value + 72
    let cur = ''
    for (const tab of secTabs.value) {
      const el = document.getElementById(tab.id)
      if (el && el.getBoundingClientRect().top <= line) cur = tab.id
    }
    const inq = document.getElementById('inquiry')
    if (inq && inq.getBoundingClientRect().top <= line) cur = ''
    activeSec.value = cur
  }
  const onScroll = () => { if (!raf) raf = requestAnimationFrame(spy) }
  measure()
  spy()
  window.addEventListener('scroll', onScroll, { passive: true })
  /*
    顶栏高度要一直盯着，不能只在挂载时量一次：窄屏上 logo、字体晚到之后顶栏会再换一次行
    （实测 390px 宽从 119 长到 130），量早了导航条就有一截钻到顶栏底下。
  */
  const hd = document.querySelector<HTMLElement>('.hd')
  const ro = hd && typeof ResizeObserver !== 'undefined'
    ? new ResizeObserver(() => { measure(); onScroll() })
    : null
  if (hd && ro) ro.observe(hd)
  onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    ro?.disconnect()
    if (raf) cancelAnimationFrame(raf)
  })
})

useSiteSeo(site, {
  path: `/products/${code.value}`,
  title: product.value.title,
  // 详情是 HTML，做摘要前先把标签剥掉，否则 meta description 里会出现 <p>
  description: product.value.summary
    || (product.value.description || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 160),
  image: product.value.images?.[0]
})

/**
 * Product 结构化数据。让 Google 在搜索结果里直接展示图片、价格、供应商，
 * 点击率比纯文字结果高得多——这是商品页相对首页额外值得做的一件事。
 */
useHead({
  script: [{
    type: 'application/ld+json',
    innerHTML: JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: product.value.title,
      description: product.value.summary || '',
      image: product.value.images || [],
      sku: product.value.code,
      category: product.value.category || undefined,
      brand: { '@type': 'Brand', name: site.companyName },
      ...(Number(product.value.price) > 0
        ? {
            offers: {
              '@type': 'Offer',
              price: String(product.value.price),
              priceCurrency: product.value.currency,
              availability: 'https://schema.org/InStock'
            }
          }
        : {})
    })
  }]
})

const COPY: Record<string, Record<string, string>> = {
  en: { home: 'Home', products: 'Products', spec: 'Specification', category: 'Category', unit: 'Unit',
        details: 'Product Details', video: 'Video', inquire: 'Send Inquiry',
        inquireAbout: 'Inquire about {name}', inquirePrice: 'Inquire for price',
        flagship: 'Flagship Product', qtyLabel: 'Quantity', specsTab: 'Specifications', sections: 'Page sections',
        specsTitle: 'Comprehensive ', specsTitleAccent: 'Specifications',
        specsLead: 'Everything we can put in writing before the quotation. Ask us for anything that is missing.',
        shipNote: 'Full quotation including freight, usually within one working day.',
        imgPrev: 'Previous image', imgNext: 'Next image', imgGoto: 'Go to image {n}',
        mainVideo: 'Product video',
        combo: 'Combo', comboAll: 'Show all {n} combos', comboLess: 'Show less' },
  zh: { home: '首页', products: '产品', spec: '规格', category: '分类', unit: '单位',
        details: '产品详情', video: '视频', inquire: '发送询盘',
        inquireAbout: '咨询「{name}」', inquirePrice: '价格面议',
        flagship: '旗舰产品', qtyLabel: '数量', specsTab: '规格参数', sections: '页面分区',
        specsTitle: '完整', specsTitleAccent: '规格参数',
        specsLead: '报价之前能写清楚的都在这里。还想知道的参数，问我们就好。',
        shipNote: '含运费的完整报价，通常一个工作日内回复。',
        imgPrev: '上一张', imgNext: '下一张', imgGoto: '看第 {n} 张',
        mainVideo: '商品视频',
        combo: '组合', comboAll: '展开全部 {n} 种', comboLess: '收起' },
  ja: { home: 'ホーム', products: '製品', spec: '仕様', category: 'カテゴリ', unit: '単位',
        details: '製品詳細', video: '動画', inquire: 'お問い合わせ',
        inquireAbout: '「{name}」について問い合わせる', inquirePrice: '価格はお問い合わせ',
        flagship: '主力製品', qtyLabel: '数量', specsTab: '仕様', sections: 'ページ内の項目',
        specsTitle: '詳細', specsTitleAccent: '仕様',
        specsLead: 'お見積り前にお伝えできる内容です。記載のない項目はお問い合わせください。',
        shipNote: '送料込みの正式なお見積りを、通常1営業日以内にお送りします。',
        imgPrev: '前の画像', imgNext: '次の画像', imgGoto: '{n} 枚目を表示',
        mainVideo: '商品動画',
        combo: '構成', comboAll: '{n} 種類すべて表示', comboLess: '閉じる' },
  es: { home: 'Inicio', products: 'Productos', spec: 'Especificación', category: 'Categoría', unit: 'Unidad',
        details: 'Detalles del producto', video: 'Vídeo', inquire: 'Enviar consulta',
        inquireAbout: 'Consultar sobre {name}', inquirePrice: 'Consultar precio',
        flagship: 'Producto estrella', qtyLabel: 'Cantidad', specsTab: 'Especificaciones', sections: 'Secciones de la página',
        specsTitle: 'Especificaciones ', specsTitleAccent: 'completas',
        specsLead: 'Todo lo que podemos indicar antes de la cotización. Pregúntenos por lo que falte.',
        shipNote: 'Cotización completa con flete, normalmente en un día laborable.',
        imgPrev: 'Imagen anterior', imgNext: 'Imagen siguiente', imgGoto: 'Ir a la imagen {n}',
        mainVideo: 'Vídeo del producto',
        combo: 'Configuración', comboAll: 'Ver las {n} configuraciones', comboLess: 'Ver menos' },
  pt: { home: 'Início', products: 'Produtos', spec: 'Especificação', category: 'Categoria', unit: 'Unidade',
        details: 'Detalhes do produto', video: 'Vídeo', inquire: 'Enviar consulta',
        inquireAbout: 'Consultar sobre {name}', inquirePrice: 'Consultar preço',
        flagship: 'Produto principal', qtyLabel: 'Quantidade', specsTab: 'Especificações', sections: 'Seções da página',
        specsTitle: 'Especificações ', specsTitleAccent: 'completas',
        specsLead: 'Tudo o que podemos informar antes do orçamento. Pergunte-nos o que faltar.',
        shipNote: 'Orçamento completo com frete, normalmente em um dia útil.',
        imgPrev: 'Imagem anterior', imgNext: 'Próxima imagem', imgGoto: 'Ir para a imagem {n}',
        mainVideo: 'Vídeo do produto',
        combo: 'Configuração', comboAll: 'Ver as {n} configurações', comboLess: 'Ver menos' },
  ru: { home: 'Главная', products: 'Товары', spec: 'Характеристики', category: 'Категория', unit: 'Единица',
        details: 'Описание товара', video: 'Видео', inquire: 'Отправить запрос',
        inquireAbout: 'Запрос по «{name}»', inquirePrice: 'Цена по запросу',
        flagship: 'Флагманский товар', qtyLabel: 'Количество', specsTab: 'Характеристики', sections: 'Разделы страницы',
        specsTitle: 'Полные ', specsTitleAccent: 'характеристики',
        specsLead: 'Всё, что можем указать до подготовки предложения. Об остальном спросите нас.',
        shipNote: 'Полное предложение с доставкой — обычно в течение одного рабочего дня.',
        imgPrev: 'Предыдущее фото', imgNext: 'Следующее фото', imgGoto: 'Показать фото {n}',
        mainVideo: 'Видео товара',
        combo: 'Комплектация', comboAll: 'Показать все {n}', comboLess: 'Свернуть' },
  it: { home: 'Home', products: 'Prodotti', spec: 'Specifiche', category: 'Categoria', unit: 'Unità',
        details: 'Dettagli prodotto', video: 'Video', inquire: 'Invia richiesta',
        inquireAbout: 'Richiesta per {name}', inquirePrice: 'Prezzo su richiesta',
        flagship: 'Prodotto di punta', qtyLabel: 'Quantità', specsTab: 'Specifiche', sections: 'Sezioni della pagina',
        specsTitle: 'Specifiche ', specsTitleAccent: 'complete',
        specsLead: 'Tutto ciò che possiamo indicare prima del preventivo. Per il resto, ci scriva.',
        shipNote: 'Preventivo completo con trasporto, di norma entro un giorno lavorativo.',
        imgPrev: 'Immagine precedente', imgNext: 'Immagine successiva', imgGoto: 'Vai all\'immagine {n}',
        mainVideo: 'Video del prodotto',
        combo: 'Configurazione', comboAll: 'Mostra tutte le {n}', comboLess: 'Mostra meno' },
  ko: { home: '홈', products: '제품', spec: '규격', category: '분류', unit: '단위',
        details: '제품 상세', video: '동영상', inquire: '문의 보내기',
        inquireAbout: '「{name}」 문의하기', inquirePrice: '가격 문의',
        flagship: '주력 제품', qtyLabel: '수량', specsTab: '규격', sections: '페이지 구성',
        specsTitle: '전체 ', specsTitleAccent: '규격',
        specsLead: '견적 전에 알려드릴 수 있는 내용입니다. 없는 항목은 문의해 주세요.',
        shipNote: '운임을 포함한 정식 견적을 보통 1영업일 안에 보내드립니다.',
        imgPrev: '이전 이미지', imgNext: '다음 이미지', imgGoto: '{n}번째 이미지 보기',
        mainVideo: '제품 동영상',
        combo: '구성', comboAll: '{n}가지 모두 보기', comboLess: '접기' },
  fr: { home: 'Accueil', products: 'Produits', spec: 'Spécification', category: 'Catégorie', unit: 'Unité',
        details: 'Détails du produit', video: 'Vidéo', inquire: 'Envoyer une demande',
        inquireAbout: 'Demander des informations sur {name}', inquirePrice: 'Prix sur demande',
        flagship: 'Produit phare', qtyLabel: 'Quantité', specsTab: 'Caractéristiques', sections: 'Sections de la page',
        specsTitle: 'Caractéristiques ', specsTitleAccent: 'complètes',
        specsLead: 'Tout ce que nous pouvons mettre par écrit avant le devis. Demandez-nous ce qui manque.',
        shipNote: 'Devis complet transport inclus, généralement sous un jour ouvré.',
        imgPrev: 'Image précédente', imgNext: 'Image suivante', imgGoto: 'Aller à l’image {n}',
        mainVideo: 'Vidéo du produit',
        combo: 'Variante', comboAll: 'Afficher les {n} variantes', comboLess: 'Afficher moins' }
}
const t = computed(() => COPY[site.lang] || COPY.en!)
</script>

<style scoped>
/* 底部 20px 太紧：询盘表单的提交按钮下面紧接着就是深色页脚。
   72px 让最后一屏有个收尾 */
.detail { padding: 22px 20px 72px; }
.crumb { display: flex; gap: 8px; font-size: 13.5px; margin-bottom: 24px; flex-wrap: wrap; }
.crumb a:hover { color: var(--brand); }

/* ---------------- 上半屏 ---------------- */

/* align-items:start：两栏各自按内容高度收，不被拉成一样高。
   参考站这一屏就是 1fr 1fr + start，右栏也不粘住——组合选择器那一块被删掉之后
   右栏比左栏矮，但粘住会让它一直压在下面的富文本详情上，比留白糟 */
.overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 56px;
  align-items: start;
}

.main-image {
  position: relative;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--surface-muted);
  aspect-ratio: 1 / 1;
}

.main-image img { width: 100%; height: 100%; object-fit: cover; }
/*
  视频用 contain，不像图片那样 cover：商品视频多半是 16:9，塞进 1:1 的框里 cover
  会把上下各裁掉一截——裁掉的常常正好是产品本身。留黑边比裁掉画面好。
  poster 走同一套 object-fit，方图封面正好铺满，播起来才出现黑边。
*/
.main-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}
.ga-noimg { width: 100%; height: 100%; background: linear-gradient(135deg, #ecf3f8, #dae7f0); }

/* ----- 轮播箭头，浮在主图上 ----- */
.car-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.85);
  color: var(--primary);
  font-size: 1.8rem;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
  z-index: 3;
  transition: background 0.15s ease;
}
.car-arrow:hover { background: #fff; }
.car-arrow--prev { left: 14px; }
.car-arrow--next { right: 14px; }

/* 缩略图条。space-between 把定宽的缩略图铺满主图的宽度，间距自己长 */
.btn-3d {
  position: absolute;
  left: 14px;
  bottom: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  backdrop-filter: blur(4px);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.btn-3d:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  transform: translateY(-1px);
}
/*
  视频那一格要把按钮挪到左上：播放器的控制条就在底部通栏，按钮压在左下正好盖住播放键。
  既配了 3D 模型又配了主图视频的商品不多，但撞上了就是「点不到播放」，没有中间状态。
*/
.btn-3d--raised { bottom: auto; top: 14px; }
.btn-3d-cube { width: 18px; height: 18px; color: var(--accent); }

.thumbs {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
}

.thumb {
  position: relative;
  width: 74px;
  height: 74px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--surface-muted);
  padding: 0;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.thumb--active {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(11, 114, 171, 0.25);
}

/* 视频那一格的播放角标。压一层半透明黑，否则白底封面上的白三角看不见 */
.thumb-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.3);
}
.thumb-play svg {
  width: 24px;
  height: 24px;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.45));
}

/* 主图切换的滑入淡出，被 .main-image 的 overflow 裁住 */
.slide-fade-enter-active,
.slide-fade-leave-active { transition: opacity 0.4s ease, transform 0.4s ease; }
.slide-fade-enter-from { opacity: 0; transform: translateX(28px); }
.slide-fade-leave-to { opacity: 0; transform: translateX(-28px); }
@media (prefers-reduced-motion: reduce) {
  .slide-fade-enter-active,
  .slide-fade-leave-active { transition: none; }
}

/* ----- 询价盒 ----- */
.flag { background: var(--surface-muted); box-shadow: none; }

.buy h1 {
  font-size: clamp(2rem, 3.4vw, 2.7rem);
  margin: 16px 0 14px;
  line-height: 1.15;
}

.price-row { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.price-row .price { font-size: 1.5rem; font-weight: 700; color: var(--primary); }

.lead { color: var(--text-muted); margin: 0 0 28px; }

/* ---------- 规格（二维） ----------
   选项按钮和平铺那一排共用 .combo-opt，只多一个「当前搭配下没货」的样子 */
.opt-block { margin-bottom: 24px; display: grid; gap: 18px; }
.opt-picked { color: var(--text); font-weight: 600; }
/* 虚线 + 压暗：看得出「现在这么搭没有」，但不是禁用——点了会换到有货的搭配 */
.combo-opt--na {
  border-style: dashed;
  color: var(--text-soft);
  background: var(--surface-muted);
}
.combo-opt--na .combo-thumb { opacity: 0.55; }

/* ---------- 组合 ---------- */
.combo-block { margin-bottom: 24px; }

/* 折叠壳。高度吃模板上的 max-height 绑定，所以收放是有过渡的，不是硬切 */
.combo-wrap {
  position: relative;
  overflow: hidden;
  transition: max-height 0.32s ease;
}

/* 盖在被裁掉那一行上的渐变——只在「折叠着且真的有超出」时才出现。
   「下面还有」这件事要让人看得出来，裁得齐反而像没有了 */
.combo-wrap::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 48px;
  background: linear-gradient(to bottom, rgba(244, 246, 249, 0), var(--bg) 88%);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.24s ease;
}
.combo-wrap--faded::after { opacity: 1; }

.combo-options { display: flex; flex-wrap: wrap; gap: 10px; }

.combo-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font: inherit;
  color: var(--text);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, color 0.15s ease;
}
.combo-opt:hover { border-color: var(--primary); }
.combo-opt--active {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(11, 114, 171, 0.18);
}
.combo-opt--active .combo-title { color: var(--primary); font-weight: 600; }

.combo-thumb {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--surface-muted);
}

/* 名称不换行，卡片按内容宽度收——换行的卡片会让一排卡高矮不齐 */
.combo-title {
  flex: none;
  text-align: left;
  font-size: 0.86rem;
  font-weight: 500;
  line-height: 1.3;
  white-space: nowrap;
}

.combo-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--primary);
}
.combo-toggle:hover { text-decoration: underline; }
.combo-caret { font-size: 0.7rem; transition: transform 0.24s ease; }
.combo-caret--up { transform: rotate(180deg); }

.qty-block { margin-bottom: 24px; }
.field-label {
  display: block;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.qty {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: #fff;
  overflow: hidden;
}
.qty-step {
  width: 38px;
  height: 42px;
  border: none;
  background: none;
  font-size: 1.05rem;
  color: var(--text-muted);
}
.qty-step:hover { background: var(--surface-muted); color: var(--primary); }
.qty-input {
  width: 66px;
  border: none;
  outline: none;
  background: transparent;
  text-align: center;
  font: inherit;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}
.qty-unit { padding-right: 14px; font-size: 0.8rem; }
.moq-note { margin: 10px 0 0; font-size: 0.8rem; color: var(--text-soft); }

.add-cart { padding: 16px; font-size: 1rem; margin-bottom: 12px; justify-content: center; }
.add-cart--done { background: #1f7a45; }
.add-cart--done:hover { background: #1a6a3b; }

.view-cart {
  display: block;
  text-align: center;
  margin: -2px 0 10px;
  font-size: 0.86rem;
  font-weight: 600;
  color: var(--primary);
}
.view-cart:hover { color: var(--primary-dark); }

.ship-note {
  text-align: center;
  color: var(--text-soft);
  font-size: 0.82rem;
  margin: 12px 0 0;
}

/* ---------------- 3D 展示台 ---------------- */
.showcase { margin-top: 56px; }
.showcase-head { margin-bottom: 30px; }

.showcase-viewport {
  position: relative;
  height: 480px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 50% 0%, rgba(175, 211, 235, 0.42) 0%, rgba(91, 99, 105, 0) 62%),
    linear-gradient(160deg, #575f65 0%, #6893b0 100%);
  /* canvas 要吃拖动和捏合，页面不能跟着滚 */
  touch-action: none;
}
.showcase-floor {
  position: absolute;
  left: 50%;
  bottom: 56px;
  transform: translateX(-50%);
  width: 420px;
  height: 58px;
  border-radius: 50%;
  background: radial-gradient(50% 50% at 50% 50%, rgba(186, 218, 240, 0.5) 0%, rgba(186, 218, 240, 0) 70%);
  filter: blur(6px);
  pointer-events: none;
}

/* 固定高度：换视角时说明文字长短不一，不定高的话下面那排控件会上下跳 */
.showcase-caption { margin-top: 24px; text-align: center; min-height: 74px; }
.showcase-caption h3 { font-size: 1.1rem; margin: 0 0 6px; }
.showcase-caption p { margin: 0 auto; max-width: 560px; font-size: 0.88rem; color: var(--text-muted); }

/* 模型状态按钮（没气/半充/充满）：展示台正下方一排，比下面的操作按钮更显眼 */
.showcase-states {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}
.showcase-states .showcase-btn { display: inline-flex; align-items: center; gap: 8px; }
.showcase-states .showcase-btn:disabled { opacity: 0.5; cursor: default; }
.showcase-btn-spin {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: showcase-spin 0.8s linear infinite;
}
@keyframes showcase-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .showcase-btn-spin { animation-duration: 2.4s; } }
.showcase-state-error { flex-basis: 100%; margin: 4px 0 0; text-align: center; font-size: 0.82rem; color: var(--danger, #c0392b); }
.showcase-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
}
.showcase-btn {
  padding: 9px 18px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.82rem;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
}
.showcase-btn:hover { color: var(--primary); border-color: var(--primary-light); }
.showcase-btn-cta { color: var(--ink-dark); background: var(--brand-blue); border-color: var(--brand-blue); text-decoration: none; }
.showcase-btn-cta:hover { color: #fff; background: var(--primary-dark); border-color: var(--primary-dark); }
.showcase-btn.is-on { color: var(--primary); border-color: var(--primary-light); background: var(--surface-muted); }

.showcase-dots { display: flex; gap: 8px; }
.showcase-dot {
  width: 9px;
  height: 9px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--border);
  transition: background 0.2s, transform 0.2s;
}
.showcase-dot.active { background: var(--primary); transform: scale(1.45); }

/* ---------------- 分区导航 ----------------
   贴在顶栏下沿。背景要不透明：下面滚过去的是详情图，半透明会透出一片花。 */
.sec-nav {
  position: sticky;
  top: var(--hd-h, 62px);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 48px;
  padding: 8px 0;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
}
.sec-tabs {
  display: flex;
  gap: 4px;
  flex: 1;
  min-width: 0;
  /* 窄屏装不下就横着滑，不换行——换成两行导航条会吃掉半屏 */
  overflow-x: auto;
  scrollbar-width: none;
}
.sec-tabs::-webkit-scrollbar { display: none; }
.sec-tab {
  flex: none;
  padding: 10px 14px;
  border-bottom: 2px solid transparent;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-muted);
  white-space: nowrap;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.sec-tab:hover { color: var(--primary); }
.sec-tab--on { color: var(--primary); border-bottom-color: var(--primary); }
.sec-cta { flex: none; padding: 10px 20px; font-size: 0.9rem; white-space: nowrap; }

/* 点导航 / 「发送询盘」跳过来时，分块顶边要落在顶栏 + 导航条下面，不能被它俩盖住 */
.specs, .showcase, .desc, .videos, .inq {
  scroll-margin-top: calc(var(--hd-h, 62px) + 72px);
}

/* ---------------- 规格参数 ---------------- */
.specs { margin-top: 48px; }
.specs .section-head { margin-bottom: 40px; }

.specs-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 56px;
}

.spec-row {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 16px;
  padding: 14px 0;
  border-top: 1px solid var(--border);
  font-size: 0.85rem;
}
.spec-key { color: var(--text); font-weight: 500; }
.spec-val { color: var(--text-muted); overflow-wrap: anywhere; }

/* ---------------- 其余分块 ---------------- */
.desc, .videos, .inq { margin-top: 56px; }
.videos h2, .inq h2 { font-size: 21px; margin: 0 0 14px; }

/* ---------------- 商品详情 ----------------

   这一块的内容是**商家自己写的 HTML**，分几段、每段多长、有没有图都不可控，
   所以样式得按「最坏情况也不难看」来定，不能假设它长成某个样子。

   两条宽度不是一个值：
     · 外层 750px —— 图片按它铺。1688 / 阿里国际站的详情图标准宽度就是 750，
       商家搬过来的长图不会被压小，也不会被放大发虚。
       （原来是 860，但图又被另一条规则压成 72% ≈ 620px，比 750 的原图还窄，
       两条规则互相打架。现在容器就是图的宽度，图片规则只管「别超出」。）
     · 文字 70ch —— 一行看多少字由字号决定，跟着缩放走。
   两者**都左对齐**：容器和图一样宽，图片和文字自然共一条左边线。 */

.desc .section-head { margin-bottom: 36px; }

.desc-body {
  max-width: 750px;
  margin: 0 auto;
  line-height: 1.9;
  color: var(--text);
}

.desc-body :deep(p),
.desc-body :deep(ul),
.desc-body :deep(ol),
.desc-body :deep(h2),
.desc-body :deep(h3),
.desc-body :deep(h4) { max-width: 70ch; }

/* 小标题带一道竖线。光靠字号拉层次，在「商家只写了三个字的小标题」和
   「小标题本身就是一句话」之间总有一头不好看，一道色条两头都稳 */
.desc-body :deep(h2),
.desc-body :deep(h3),
.desc-body :deep(h4) {
  font-weight: 700;
  line-height: 1.5;
  margin: 40px 0 16px;
  padding-left: 14px;
  border-left: 3px solid var(--primary);
}
.desc-body :deep(h2) { font-size: 1.28rem; }
.desc-body :deep(h3) { font-size: 1.12rem; }
.desc-body :deep(h4) { font-size: 1rem; }
.desc-body :deep(> :first-child) { margin-top: 0; }

.desc-body :deep(p) { margin: 0 0 18px; }
.desc-body :deep(blockquote) {
  margin: 20px 0;
  padding: 14px 18px;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
}

/* 圆点自己画：默认的 list-style 在 1.9 的行高下会偏上一截，而且列表项
   一换行，第二行会顶到符号正下方去——详情里的条目基本都不止一行 */
.desc-body :deep(ul),
.desc-body :deep(ol) { margin: 0 0 22px; padding: 0; list-style: none; }
.desc-body :deep(li) { position: relative; padding-left: 24px; margin-bottom: 12px; }
.desc-body :deep(ul) > li::before {
  content: '';
  position: absolute;
  left: 6px;
  top: .82em;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
}
.desc-body :deep(ol) { counter-reset: descitem; }
.desc-body :deep(ol) > li { counter-increment: descitem; }
.desc-body :deep(ol) > li::before {
  content: counter(descitem) '.';
  position: absolute;
  left: 0;
  top: 0;
  color: var(--primary);
  font-weight: 700;
}

/* 详情图。

   ## 宽度：原图多宽显示多宽，最多铺满 750 的容器

   width:auto 而不是 100%：一张 300px 的小图标不该被拉成 750 发虚。
   2026-09-23 那版是 72% + 居中，用来对付「3000px 方图铺满一屏」——容器收到 750 之后
   方图一张约 750 高，和 1688 国际站上一样；而 72% 那条会把 750 的切片长图压成 540，
   上面的小字直接看不清，代价更大。

   ## 连着的几张图：无缝拼接

   1688 的详情图是**一张长图切成几段**上传的，靠上下无缝拼在一起才完整。
   原来每张图上下各留 24px、还带圆角，一张长图被切成一块块卡片，接缝处的字和图案都断开。
   所以：相邻的图之间间距为 0、不要圆角；只有一串图的头尾和文字之间留白。
   单独夹在文字中间的一张图照旧有间距和圆角。

   「相邻」靠 `img + img` 和 `:has(+ img)` 判断：中间只隔空白文本时算相邻，
   隔了一段文字就不算——正好是「连着贴的一串」的意思。

   height:auto 是必须的——商家贴的 <img> 常带死的 width/height 属性
   （白名单放行了这两个），不覆盖就会被拉变形 */
.desc-body :deep(img) {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  border-radius: var(--radius);
  /* 左右 auto：比容器窄的小图居中 */
  margin: 24px auto;
}
.desc-body :deep(img:has(+ img)) {
  margin-bottom: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}
.desc-body :deep(img + img) {
  margin-top: 0;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}
/* 图包在 <p> 里（商家手写的 <p><img></p>）时，别让段落的 70ch 把图也限窄了 */
.desc-body :deep(p:has(> img)) { max-width: none; }
/* 管理台里显式写过宽度的图：尊重它，只兜住"别被拉变形、别溢出" */
.desc-body :deep(img[width]) {
  width: auto;
  height: auto;
  max-width: 100%;
}
.desc-body :deep(table) { border-collapse: collapse; width: 100%; max-width: 70ch; margin: 20px 0; }
.desc-body :deep(th), .desc-body :deep(td) {
  border: 1px solid var(--line); padding: 8px 12px; text-align: left;
}
.desc-body :deep(th) { background: var(--bg-soft); }
.videos video { width: 100%; max-width: 720px; border-radius: var(--radius); margin-bottom: 14px; }

@media (max-width: 900px) {
  .overview { grid-template-columns: 1fr; gap: 32px; }
  .specs-grid { grid-template-columns: 1fr; gap: 0 32px; }
  .showcase-viewport { height: 340px; }
  .showcase-floor { width: 260px; bottom: 40px; }
}

@media (max-width: 640px) {
  .buy h1 { font-size: 1.7rem; }
  .thumbs { gap: 10px; }
  .thumb { width: 62px; height: 62px; }
  /* 长参数值挤在 150px + 剩下那点宽度里读不了，键值上下排 */
  .spec-row { grid-template-columns: 1fr; gap: 4px; }
}
</style>

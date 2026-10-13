<!--
  首页装修表单。两个管理台宿主共用，见同目录 README。

  ## 一条主线：页面搭一次，之后只翻译文字

  站点走「一站多语」：一个站挂多种语言，访客切语言时**整站内容**都要跟着变。
  但「变」的只该是**文字**：

  - **页面长什么样只存一份**——有哪几块、什么顺序、什么版式、几条导航指向哪、
    几张幻灯片配哪张图、图标、数值、电话、地图点位。所有语言共用这一份。
  - **页面上写着什么每种语言各存一份**——标题、正文、按钮字、SEO 标题和描述。

  所以商家的流程是：搭一次页面，之后切到别的语言只把字填一遍，版式一动不动。
  同时每种语言又都有自己的 H1 和 meta，hreflang 才成立，也不会被 Google 判成
  重复内容。反过来（整块内容都按语言存）的话，七种语言能排出七种不同的导航，
  而同一个网址商家得填七遍、改七遍。

  结构是 contentJson v4：

      {
        "v": 4,
        "logo": "https://…",                        ← i18n 之外 = 共享层
        "hero":    { "slides": [{"id":"s1","image":"…","video":"…"}] },
        "about":   { "image": "…", "stats": [{"id":"t1","value":"15+"}] },
        "contact": { "phone": "…", "email": "…", "whatsapp": "…" },
        "reach":   { "hq": {...}, "destinations": [...], "stats": [...] },
        "nav":          [{"id":"n1","url":"/products"}],
        "highlights":   [{"id":"h1","icon":"★"}],
        "capabilities": [{"id":"c1","icon":"◆"}],
        "bento":   { "statValue": "60+" },
        "order":   [...],  "off": [...],  "blocks": {...},
        "i18n": {
          "en": { "companyName":"…", "seoTitle":"…",
                  "navText":       {"n1": {"title":"Products"}},
                  "highlightText": {"h1": {"title":"…","desc":"…"}},
                  "slideText":     {"s1": {"title":"…"}},
                  "aboutStatText": {"t1": {"label":"Years"}},
                  "hero": {"title":"…"}, "bento": {"statLabel":"Countries"} },
          "ja": { … 同样那几个 id，另一套文字 … }
        }
      }

  后端读取见 SiteOpenService#siteContent + #compose：共享层先铺、默认语言层再铺、
  当前语言层最后盖，**最后把条目和文案按 id 拼回前台早就认识的那种形状**
  （`nav:[{title,url}]`）。所以前台一个字都不用改。

  ### 那几个 `xxxText` 袋子为什么在语言层的顶层

  后端合并**只合一层**。放在顶层的袋子能和默认语言那份合一次，于是「这一条没译」
  可以按条退回默认语言；塞进 `hero` 底下的话，整个袋子会被当前语言那份整个换掉——
  三张幻灯片译了一张，另外两张的字就没了。同理，共享层和语言层在同一个父键底下
  **必须各用各的键名**（`bento.statValue` / `bento.statLabel`）。

  ### 首页区块的顺序和开关

  `order`（从上到下的顺序）、`off`（关掉了哪几块）、`blocks`（每块的小设置，
  比如版式、能力卡强调第几张、首页取几个商品）都在共享层，不分语言。
  左边那一列就是它们的编辑界面。**取舍规则要和前台
  `station-site/app/pages/index.vue` 的 `blockOrder` 一致**（见 `normalizeOrder`）。

  不认识的顶层键仍然会原样带回去，见 `passthrough`——那是给以后新增的键留的兜底。

  ### 缺译文怎么办

  某个语言只填了一半时，**缺的那几项按项退回默认语言**（2026-09-10 定的，
  和商品多语言一致）。所以下面每个「分语言」的提示都是真的。

  ⚠ 结构不参与回退，因为它根本不分语言：日语没译的站点，导航条数、版式、图片
  和英语站一模一样，只是那几个名字显示的是英文。

  ## 版式：三列工作台

  左边选改哪一块、中间填、**右边同时看着站点跟着变**。整页占满管理台的内容区，
  自己不滚，三列各自滚——右边那一列是实时预览，跟着页面滚走就没有意义了。

  右边装的是**真前台本身**（一个 iframe），不是照着真站在这里抄的一份版式。
  ⚠ 它要 nginx 放行一次才能用（主域默认不许 `console.` 子域嵌它），
  没放行时这一列会自己发现并让人改用「新窗口」。细节见 script 里「预览」那一段。

  ## 只读怎么处理

  没有编辑权限时：**整页置灰、最上面写明原因**，语言页签和内容页签仍然能点。

  这里原来写的是 `v-hasPermi="['site:station:edit']"`，而那个指令的做法是
  **把元素从 DOM 里删掉**。对这一页来说结果很糟：整页输入框照样能填能改，
  只有底下的保存按钮不见了，人填完一整屏才发现没有地方能存。

  权限由宿主注入（`stationCanEdit`），组件因此也不依赖宿主注册过 `v-hasPermi`——
  这个目录里的东西不该对宿主提要求。宿主那边的理由见
  `station-console-ui/src/views/Decorate.vue`。

  ⚠ 只灰掉输入，**不灰掉语言切换**：没权限的人也需要能翻看各语言配了什么。

  ### 老数据：v1 / v2 / v3 都不用跑迁移

  没有 `i18n` 键的是 v1（整份只有一种语言），这里按站点默认语言载入。
  批量迁移脚本（v1 → v2）：sql/migration/20260831_station_content_i18n.sql

  v2 → v3 → v4 **都没有迁移脚本，也不需要**，两边各做了一半：

  - **后端**：v2/v3 的条目整条都在语言层、没有 id，`compose` 认出这种条目就原样
    放行，页面显示和以前一模一样；
  - **这里**：载入时把语言层里那些该共享的东西捞上来（图片、电话、导航地址、
    图标、数值……见 `fillShared` 和 `fillRows`），文案按下标放进新袋子
    （见 `textBag`）。**商家下次一保存就自动归位。**

  ⚠ 所以发版顺序是**后端先上**：管理台先上的话，它存出来的 v4 数据会被老后端
  当成「有 id 没文字」的条目，导航名字全空。
-->
<template>
  <!--
    整页是一个**占满内容区的三列工作台**，不是一张会往下滚的长表单：
    左边选改哪一块、中间填、右边同时看着站点跟着变。
    页面自己不滚，三列各自滚——右边那一列是实时预览，它跟着页面滚走就没有意义了。
  -->
  <div class="deco" :class="{ locked: !canEdit }" v-loading="loading">

    <!-- ===================== 顶上一条：编辑语言 ===================== -->
    <!--
      语言对**三列同时生效**（左边的状态标签、中间的输入框、右边的预览都跟着它），
      所以它不能塞进任何一列里，只能横在最上面。
    -->
    <div class="deco-top" v-if="langs.length">
      <span class="top-lbl">{{ st('decorate.lang.edit', '编辑语言') }}</span>
      <div class="langbar">
        <button v-for="code in langs" :key="code" type="button"
          class="tabbtn" :class="{ on: lang === code }" @click="lang = code">
          {{ langLabel(code) }}
          <span v-if="code === defaultLang" class="dflt">{{ st('decorate.lang.default', '默认') }}</span>
          <!--
            这个小点是「这种语言一个字都没填」。标出来不是催人填满：
            没填的项会退回默认语言，站点不会空——但商家得知道现在看的是哪种情况。
          -->
          <span v-else-if="langEmpty(code)" class="miss"
            :title="st('decorate.lang.emptyHint', '这种语言一个字都没填。访客切到它时，整页显示的是默认语言那一份')"></span>
        </button>
      </div>
      <!--
        机器翻译。只在「不是默认语言」时出现——对着默认语言自己翻自己没有意义，
        摆一个点了会报错的按钮不如不摆。
      -->
      <el-button v-if="translator && canEdit && lang && lang !== defaultLang"
        class="top-tr" size="small" :loading="translating" @click="autoTranslate">
        {{ st('decorate.action.autofillFrom', '用{v}翻译填充', { v: langLabel(defaultLang) }) }}
      </el-button>
      <span class="top-sp"></span>
      <span class="top-hint">
        {{ st('decorate.intro.storagePerLanguage', '首页上除了商品之外的内容都在这里配。内容按语言各存一份，某种语言') }}<b>{{ st('decorate.intro.fallbackPerItem', '没填的项按项退回默认语言') }}</b>{{ st('decorate.intro.blankDefaults', '（{v}）； 留空的项用系统内置的默认值，站点不会因为没配而空白。', { v: langLabel(defaultLang) }) }}
      </span>
    </div>
    <el-alert v-else type="warning" show-icon :closable="false" class="deco-nolang"
      :title="st('decorate.lang.notConfigured', '这个站点还没设置语言')"
      :description="st('decorate.lang.notConfiguredDesc', '去 ERP 的站点档案里把「站点语言」填上，再回来配装修内容。')" />

    <!--
      没有编辑权限时：在最上面说明原因 + 左中两列整体置灰（见 .locked），
      而不是把保存按钮藏起来。藏按钮的后果见文件头「只读怎么处理」那一段。
      ⚠ 右边那一列不灰：看站点长什么样，只读的人也该能看。
    -->
    <div class="deco-nop" v-if="!canEdit">
      {{ st('decorate.permission.noEditBefore', '你的角色没有装修编辑权限（') }}<code>site:station:edit</code>{{ st('decorate.permission.noEditAfter', '），下面的内容只能看不能改。 需要开权限的话找站长。切语言、翻区块照旧。') }}
    </div>

    <div class="deco-cols">

      <!--
        ===================== 第一列：改哪一块 =====================

        分成两组，不是一排平铺的页签。理由：顶栏、页脚、SEO 是**全站**的东西
        （每一页都在用），而 Banner、关于我们是**首页的一块**——
        把它们并排摆在同一排页签里，商家找「改页脚的电话」时无从下手。

        下面那组还兼作首页的「目录」：从上到下就是页面上从上到下，
        能拖着换顺序、能用眼睛图标整块关掉。
      -->
      <aside class="col-list">
        <div class="grp">
          <div class="grp-t">
            {{ st('decorate.group.siteSettings', '全站设置') }}
            <el-tooltip placement="right"
              :content="st('decorate.group.sharedIntro', '这些不是首页的一块，而是每个页面都在用的东西。放在这里，下面那组才只剩首页的事。')">
              <span class="q">?</span>
            </el-tooltip>
          </div>
          <button v-for="item in GLOBAL_ITEMS" :key="item.key" type="button"
            class="item" :class="{ on: sel === item.key }" @click="sel = item.key">
            <span class="hnd hide">⠿</span>
            <span class="ic">{{ item.icon }}</span>
            <span class="nm"><b>{{ item.name }}</b></span>
            <span v-if="globalBadge(item.key)" class="tag">{{ globalBadge(item.key) }}</span>
          </button>
        </div>

        <div class="grp">
          <div class="grp-t">
            {{ st('decorate.group.homeBlocks', '首页区块 · 可拖拽排序') }}
            <el-tooltip placement="right"
              :content="st('decorate.group.homeIntro', '从上到下就是首页上从上到下。拖动或用箭头换顺序，眼睛可以整块关掉（内容还留着）。')">
              <span class="q">?</span>
            </el-tooltip>
          </div>
          <!--
            原生拖放，不引第三方库：station-console-ui 没有 vuedraggable，
            而这个目录里的组件不该逼宿主装依赖（README「宿主要提供的东西」那张表）。
            七个固定条目的排序，原生够用了。

            ⚠ 同时留了上下箭头。拖放在触摸屏上基本不可用，而且拖错一格没法撤销；
              箭头一次挪一格，看得见、点得准。
          -->
          <div v-for="(key, i) in usedBlocks" :key="key"
            class="item" :class="{ on: sel === key, dragging: dragKey === key }"
            :draggable="canEdit" @dragstart="onDragStart(key)" @dragend="dragKey = ''"
            @dragover.prevent @drop="onDrop(key)" @click="sel = key">
            <span class="hnd" :class="{ hide: !canEdit }" :title="st('decorate.action.dragToSort', '拖动排序')">⠿</span>
            <span class="ic">{{ BLOCK_META[key].icon }}</span>
            <span class="nm">
              <b>{{ BLOCK_META[key].name }}</b>
              <span>{{ blockSub(key) }}</span>
            </span>
            <span v-if="blockBadge(key)" class="tag" :class="{ 'tag-warn': isFallback(key) }">
              {{ blockBadge(key) }}
            </span>
            <!-- 三个小按钮平时藏着，鼠标移上去或选中时才出现——七行挂二十一个按钮，
                 不藏的话这一列会糊成一片 -->
            <span class="acts" @click.stop>
              <button type="button" class="mv" :disabled="!canEdit || i === 0"
                :title="st('decorate.action.moveUp', '上移一格')" @click="moveBlock(key, -1)">↑</button>
              <button type="button" class="mv" :disabled="!canEdit || i === usedBlocks.length - 1"
                :title="st('decorate.action.moveDown', '下移一格')" @click="moveBlock(key, 1)">↓</button>
              <!-- 「移除」不是「删除」：内容留着，从「添加区块」里能原样加回来。
                   title 里把这件事说清楚，不然没人敢点 -->
              <button type="button" class="rm" :disabled="!canEdit"
                :title="st('decorate.action.removeFromHome', '从首页上移除（内容留着，随时能加回来）')"
                @click="removeBlock(key)">✕</button>
            </span>
          </div>

          <div class="addrow">
            <button type="button" class="addbtn" :disabled="!canEdit || !removedBlocks.length"
              :title="removedBlocks.length ? '' : st('decorate.picker.allOnHome', '七块都在首页上了')"
              @click="pickerOpen = true">
              {{ st('decorate.picker.addBlock', '＋ 添加区块') }}<template v-if="removedBlocks.length">{{ st('decorate.picker.addBlockCount', '（{n}）', { n: removedBlocks.length }) }}</template>
            </button>
          </div>
        </div>
      </aside>

      <!-- ===================== 第二列：选中那一块的表单 ===================== -->
      <div class="col-form">
        <div class="fhead">
          <h2><span class="fhead-ic">{{ currentMeta.icon }}</span>{{ currentMeta.name }}</h2>
          <p>{{ currentMeta.desc }}</p>
        </div>

        <div class="fbody">

      <!-- ===================== 品牌与 Logo ===================== -->
      <section v-show="sel === 'brand'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.logoImage', 'Logo 图') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <image-field v-model="shared.logo" :disabled="!canEdit"
              :placeholder="st('decorate.field.logoImagePlaceholder', '图片完整地址，留空则顶栏只显示公司名')" />
            <div class="f-hint">
              {{ st('decorate.hint.imageSourcePick', '点「选图片」从文件资源库里挑，也可以直接粘一个完整地址（比如商品图用的那个对象存储）。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.companyName', '公司名') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.companyName" maxlength="100" :placeholder="phCompanyName" />
            <div class="f-hint">{{ st('decorate.field.companyNameHint', '对外用的名字，往往和 ERP 里的站点名称不是一回事。显示在顶栏和页脚。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.slogan', '一句话标语') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.slogan" maxlength="200" :placeholder="st('decorate.field.sloganPlaceholder', '如：Your Reliable Supplier Since 2008')" />
            <div class="f-hint">{{ st('decorate.field.sloganHint', '显示在页脚，Banner 副标题没填时也用它兜底。') }}</div>
          </el-form-item>
        </el-form>
      </section>

      <!-- ===================== SEO 标题与描述 ===================== -->
      <section v-show="sel === 'seo'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.seoTitle', 'SEO 标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.seoTitle" maxlength="200" :placeholder="phSeoTitle" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.seoDescription', 'SEO 描述') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.seoDescription" type="textarea" :rows="3" maxlength="500" show-word-limit
              :placeholder="phFromSlogan" />
            <div class="f-hint">{{ st('decorate.field.seoHint', '这两项一定要按语言分别填——搜索引擎是按语言分别收录的。') }}</div>
          </el-form-item>
        </el-form>
        <!--
          ⚠ 这里只有标题和描述。sitemap、robots、统计代码那些在另一个菜单
          「站长工具」里（u_s_seo 那张表），不在装修内容里——两处都叫 SEO，
          说清楚省得来回找。
        -->
        <div class="tip">
          <b>{{ st('decorate.field.seoElsewhereHint', 'sitemap、robots、统计代码不在这里。') }}</b>
          {{ st('decorate.hint.seoTitleDescOnly', '那些在左边菜单的「站长工具」里。这一页只管搜索结果上显示的标题和描述这两行字。') }}
        </div>
      </section>

      <!-- ===================== Banner ===================== -->
      <section v-show="sel === 'hero'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <div class="two-up">
              <el-select v-model="shared.variants.hero">
                <el-option v-for="v in HERO_ALIGNS" :key="v[0]" :value="v[0]" :label="v[1]" />
              </el-select>
              <el-select v-model="shared.variants.heroHeight">
                <el-option v-for="v in HERO_HEIGHTS" :key="v[0]" :value="v[0]" :label="v[1]" />
              </el-select>
            </div>
            <div class="f-hint">
              {{ st('decorate.hint.halfScreen', '「半屏」适合下面内容多的站：首屏不占满一整屏，访客一眼就能看到第二块。') }}
            </div>
          </el-form-item>
        </el-form>

        <!--
          幻灯片。⚠ 张数和顺序在共享层（shared.heroSlides），每张的文案在语言层
          （form.slideText[id]）——一张厂房图不会因为语言不同就换一张，
          但图上那句话当然要分语言。两边靠 id 对上，不是靠下标。
        -->
        <div class="sec">
          {{ st('decorate.slide.listTitle', '幻灯片 · {n} 张', { n: shared.heroSlides.length }) }}
          <em class="sh">{{ st('decorate.field.slideOrderShared', '张数和顺序所有语言通用') }}</em>
        </div>
        <div class="strip">
          <button v-for="(sld, i) in shared.heroSlides" :key="sld.id" type="button"
            class="sl" :class="{ on: i === slideIndex }"
            :style="sld.image ? { backgroundImage: 'url(' + sld.image + ')' } : null"
            :title="st('decorate.slide.nth', '第 {v} 张', { v: i + 1 }) + (sld.video ? st('decorate.slide.videoTag', '（视频）') : '')" @click="slideIndex = i">
            <span class="n">{{ i + 1 }}</span>
            <!-- 配了视频的那张标一个 ▶。缩略条铺的是封面图，不标的话它和一张静态图
                 长得一模一样，而两者能不能自动播、耗多少流量差得远 -->
            <span v-if="sld.video" class="vd">▶</span>
            <span v-else-if="!sld.image" class="no">{{ st('decorate.slide.noImage', '无图') }}</span>
          </button>
          <button v-if="shared.heroSlides.length < HERO_IMAGE_MAX" type="button" class="add"
            :disabled="!canEdit" :title="st('decorate.action.addSlide', '加一张')" @click="addSlide()">＋</button>
        </div>
        <div class="f-hint mb12">
          <template v-if="shared.heroSlides.length > 1">
            {{ st('decorate.slide.countOnHome', '{n} 张，首页', { n: shared.heroSlides.length }) }} <b>{{ st('decorate.slide.rotationHint', '5 秒切一张') }}</b>{{ st('decorate.slide.dotsSuffix', '，底下带小圆点。') }}
            <b>{{ st('decorate.slide.videoCutHint', '配了视频的那张也是 5 秒就切走') }}</b>{{ st('decorate.slide.videoCutAfter', '，不会等它播完—— 想让人看完整段，就别再加别的幻灯片。') }}
          </template>
          <template v-else-if="shared.heroSlides.length === 1">
            {{ st('decorate.slide.singleStatic', '只有一张就是一张静态大图，没有轮播、没有圆点。') }}
          </template>
          <template v-else>
            {{ st('decorate.slide.noneBackground', '一张都没有时首页显示纯色背景（一层制图纸网格加柔光，不会开天窗）。') }}
          </template>
          {{ st('decorate.slide.maxHint', '最多 {HERO_IMAGE_MAX} 张——每一张都要在首屏下载完，再多就是拿加载速度换一个没人看完的轮播。', { HERO_IMAGE_MAX }) }}
          <br>{{ st('decorate.slide.firstAsSocialPreview', '第一张还会当作分享到社交媒体时的预览图（og:image）。') }}
        </div>

        <div v-if="shared.heroSlides[slideIndex]" class="slide">
          <div class="slide-h">
            {{ st('decorate.slide.nth', '第 {v} 张', { v: slideIndex + 1 }) }}
            <span class="sb-sp"></span>
            <button type="button" class="mv" :disabled="!canEdit || slideIndex === 0"
              :title="st('decorate.action.moveSlideUp', '往前挪一张')" @click="moveSlide(slideIndex, -1)">↑</button>
            <button type="button" class="mv"
              :disabled="!canEdit || slideIndex === shared.heroSlides.length - 1"
              :title="st('decorate.action.moveSlideDown', '往后挪一张')" @click="moveSlide(slideIndex, 1)">↓</button>
            <button type="button" class="del" :disabled="!canEdit"
              :title="st('decorate.action.deleteSlide', '删掉这一张（连同各语言里它的文案）')" @click="removeSlide(slideIndex)">✕</button>
          </div>
          <el-form class="deco-form" label-position="top" :disabled="!canEdit">
            <el-form-item>
              <template #label><span class="f-lbl">{{ st('decorate.field.backgroundImage', '背景图') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
              <image-field v-model="shared.heroSlides[slideIndex].image" :disabled="!canEdit"
                :placeholder="st('decorate.field.backgroundImagePlaceholder', '图片完整地址，留空则这一屏用纯色底纹')" />
              <div class="f-hint">
                {{ st('decorate.note.imageSizeHint', '建议 1920×700 以上的横图。') }}
                <template v-if="shared.heroSlides[slideIndex].video">
                  <br><b>{{ st('decorate.field.videoPosterHint', '这一张配了视频，所以这张图现在是它的封面') }}</b>{{ st('decorate.note.videoPosterDetail', '：视频下载出来之前、 访客勾了「减少动态效果」时、以及视频放不出来时，显示的都是它。 最好就用视频的第一帧——那样开始播的那一刻画面不会跳。') }}
                </template>
              </div>
            </el-form-item>
            <!--
              背景视频。2026-09-21 加的，和背景图是「同一张幻灯片的两层」：
              有视频就播视频、上面那张图退成封面；没有视频就还是一张静态大图。
              做成两个字段而不是一个「图/视频」二选一，是因为封面图**必须**能单独配：
              首屏视频下载完之前那几秒、以及减少动态效果的访客，看到的全是它。
            -->
            <el-form-item>
              <template #label><span class="f-lbl">{{ st('decorate.field.backgroundVideo', '背景视频') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
              <video-field v-model="shared.heroSlides[slideIndex].video" :disabled="!canEdit"
                :placeholder="st('decorate.field.backgroundVideoPlaceholder', '视频完整地址，留空则这一屏就用上面那张图')" />
              <div class="f-hint">
                {{ st('decorate.note.backgroundVideoLead', '填了就用视频当这一屏的背景：') }}<b>{{ st('decorate.field.backgroundVideoHint', '自动播放、循环、没有声音、也没有播放条') }}</b>{{ st('decorate.note.backgroundVideoMuted', '。 （带声音不是「没做」——首屏自动播放的视频只要有声音，浏览器就直接不给播。）') }}
                <br>{{ st('decorate.field.videoFileSizeLead', '素材先自己压到') }} <b>{{ st('decorate.field.videoFileSizeSpec', '10MB 以内、10～15 秒') }}</b> {{ st('decorate.note.videoFileSizeTrail', '再传。它是首屏就要下载的东西， 几十兆的原片会让首页在手机网络下久到访客直接退出。') }}
                <br>{{ st('decorate.note.reducedMotionReason', '访客系统里勾了「减少动态效果」时不播，显示上面那张封面图——这是有意的， 整屏画面自己动会让晕动症的人难受。') }}
              </div>
            </el-form-item>
            <el-form-item v-if="shared.heroSlides[slideIndex].image || shared.heroSlides[slideIndex].video">
              <template #label>
                <span class="f-lbl">
                  {{ shared.heroSlides[slideIndex].video ? st('decorate.field.videoAlt', '画面说明') : st('decorate.field.imageAlt', '图片说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em>
                </span>
              </template>
              <el-input v-model="slideText.alt" maxlength="120"
                :placeholder="st('decorate.field.imageAltPlaceholder', '这张图里是什么，如「十万级无尘车间的硅胶注塑产线」')" />
              <div class="f-hint">
                <b>{{ st('decorate.field.imageAltHint', 'Google 图片搜索只认这一句') }}</b>{{ st('decorate.note.imageAltDetail', '，图片本身它看不懂；图挂了显示它，读屏软件也念它。 留空不是错——纯背景纹理那种本来就该留空，读屏会跳过去。 但如果这张图是厂房、产线、主打产品，那就值得填。') }}
                <b>{{ st('decorate.field.videoAltHint', '配了视频时这一句同样生效') }}</b>{{ st('decorate.note.videoAltDetail', '：读屏软件念的就是它（视频没有 alt， 前台把它当成这一屏的无障碍名称）。') }}
              </div>
            </el-form-item>
            <el-form-item>
              <template #label><span class="f-lbl">{{ st('decorate.field.slideTitle', '这一张的标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
              <el-input v-model="slideText.title" maxlength="200" :placeholder="st('decorate.field.slideTitlePlaceholder', '留空则用下面那句整组共用的大标题')" />
            </el-form-item>
            <el-form-item>
              <template #label><span class="f-lbl">{{ st('decorate.field.slideSubtitle', '这一张的副标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
              <el-input v-model="slideText.subtitle" maxlength="300" :placeholder="st('decorate.field.slideSubtitlePlaceholder', '留空则用下面整组共用的副标题')" />
            </el-form-item>
          </el-form>
        </div>

        <div class="sec">{{ st('decorate.section.sharedCopy', '整组共用的文案') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></div>
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.eyebrow', '眉标') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.hero.eyebrow" maxlength="60" :placeholder="st('decorate.field.eyebrowPlaceholder', '如 ISO 9001:2015；留空则用一句话标语兜底')" />
            <div class="f-hint">{{ st('decorate.field.eyebrowHint', '大标题上面那个小圆标。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.heroHeading', '大标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.hero.title" maxlength="200" :placeholder="phHeroTitle" />
            <div class="f-hint">
              {{ st('decorate.note.slideTitleFallback', '哪一张幻灯片没填自己的标题，就显示这一句。 按句读断行，最后一段上强调色——填「A. B.」这种两句式效果最好。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.heroSubheading', '副标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.hero.subtitle" maxlength="300" :placeholder="phFromSlogan" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.heroPrimaryButton', '主按钮') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.hero.btn1" maxlength="30" :placeholder="st('decorate.ph.heroPrimaryButtonBuiltin', '留空则用内置文案（按访客语言自动切换）')" />
            <div class="f-hint">{{ st('decorate.hint.heroPrimaryButton', '左边那个实心按钮。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.heroPrimaryButtonLink', '主按钮链接') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.heroBtn1Url" maxlength="300"
              :placeholder="st('decorate.ph.heroPrimaryButtonLink', '留空则去商品列表')" :class="{ 'is-bad': !!linkError(shared.heroBtn1Url) }" />
            <div class="f-hint" :class="{ bad: !!linkError(shared.heroBtn1Url) }">
              <template v-if="linkError(shared.heroBtn1Url)">{{ linkError(shared.heroBtn1Url) }}</template>
              <template v-else>{{ LINK_HINT }}</template>
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.heroSecondaryButton', '次按钮') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.hero.btn2" maxlength="30" :placeholder="st('decorate.ph.heroPrimaryButtonBuiltin', '留空则用内置文案（按访客语言自动切换）')" />
            <div class="f-hint">{{ st('decorate.hint.heroSecondaryButton', '右边那个描边按钮。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.heroSecondaryButtonLink', '次按钮链接') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.heroBtn2Url" maxlength="300"
              :placeholder="st('decorate.ph.heroSecondaryButtonLink', '留空则跳到本页的询盘表单')" :class="{ 'is-bad': !!linkError(shared.heroBtn2Url) }" />
            <div class="f-hint" :class="{ bad: !!linkError(shared.heroBtn2Url) }">
              <template v-if="linkError(shared.heroBtn2Url)">{{ linkError(shared.heroBtn2Url) }}</template>
              <template v-else>{{ LINK_HINT }}</template>
            </div>
          </el-form-item>
        </el-form>
      </section>

      <!-- ===================== 三栏特色 ===================== -->
      <!-- 原来这块挤在 Banner 页签的下半截。它在首页上是**独立的一屏**，
           能单独关掉、能换位置，所以拆出来自己一块 -->
      <section v-show="sel === 'bento'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.bento" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.bento" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.note.bentoThirdsIntro', '「三等分」是三张一样大的卡，不借数字亮点那一条，也就不会出现同一个数字一屏两次。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.bento.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.bento.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
          <!--
            粉色数字卡。这一格是**它自己的**，不是从「关于我们」的数字亮点里借的。
            借的做法会让同一个数字在一屏里出现两次（首页上这两块挨着）。
            ⚠ 留空时仍然照旧去借第一条——老站点都是那么配的，改成「留空就不显示」
              等于让所有人的首页悄悄少一张卡。
          -->
          <el-form-item v-if="shared.variants.bento !== 'grid3'">
            <template #label>
              <span class="f-lbl">{{ st('decorate.field.bentoStatCard', '数字卡') }}<em class="sh">{{ st('decorate.tag.bentoStatShared', '数字通用') }}</em><em>{{ st('decorate.tag.bentoStatLabelPerLanguage', '说明分语言') }}</em></span>
            </template>
            <div class="two-up">
              <el-input v-model="shared.bentoStatValue" maxlength="40" placeholder="15+" />
              <el-input v-model="form.bento.statLabel" maxlength="80" :placeholder="st('decorate.ph.bentoStatLabel', '年出口经验')" />
            </div>
            <div class="f-hint">
              {{ st('decorate.note.bentoStatPinkNote', '三张卡里那张粉色的。') }}<b>{{ st('decorate.hint.bentoStatFilledBold', '填了就用填的') }}</b>{{ st('decorate.note.bentoStatFilledTrail', '，「关于我们」下面那排数字也就一条都不少；') }}
              <b>{{ st('decorate.hint.bentoStatEmptyBold', '留空') }}</b>{{ st('decorate.note.bentoStatEmptyTrail', '则借「关于我们」的第一条来填这张卡（那一条就不在下面重复出现了）。 两格都空着、「关于我们」也没数字时，这张卡不出现，主卡自动不跨行。') }}
            </div>
          </el-form-item>
        </el-form>
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.featureItems', '特色条目') }}<em class="sh">{{ st('decorate.tag.featureCountIconsShared', '条数和图标通用') }}</em><em>{{ st('decorate.tag.featureTextPerLanguage', '文字分语言') }}</em></span>
          <!-- 站点模板只渲染前 3 条，多填的存得下但显示不出来 -->
          <item-rows :rows="shared.highlights" :text="form.highlightText"
            :cols="BENTO_COLS" :max="3" :disabled="!canEdit"
            :empty-text="st('decorate.empty.featureItems', '还没有条目 —— 这一块暂时不会出现在首页')"
            @add="addRow(shared.highlights, ['icon'])"
            @remove="removeRow(shared.highlights, 'highlightText', $event)" />
          <div class="f-hint">
            {{ st('decorate.note.featureSlotOrder', '第 1 条进左边那张大卡，第 2 条进小卡，第 3 条进底下那条深色横幅。只显示前 3 条。') }}
            <b>{{ st('decorate.hint.featureCountIconsSharedBold', '有几条、每条什么图标是所有语言共用的') }}</b>{{ st('decorate.note.featureCountSharedTrail', '，切到别的语言只有标题和说明要重填。') }}
          </div>
        </div>
      </section>

      <!-- ===================== 主推商品 ===================== -->
      <!-- 内容不在装修里填（来自商品库），所以这一块只有一个设置 -->
      <section v-show="sel === 'products'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.products" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.products" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">{{ st('decorate.hint.twoColumnGallery', '「两列大图」图更大，适合商品少、图好看的站。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.products.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.products.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.galleryVisibleCount', '显示几个') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.productLimit" style="width: 220px">
              <el-option :value="3" :label="st('decorate.option.galleryCount3', '3 个（一行）')" />
              <el-option :value="6" :label="st('decorate.option.galleryCount6', '6 个（两行满）')" />
              <el-option :value="9" :label="st('decorate.option.galleryCount9', '9 个（三行满）')" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.note.gallerySelectionRule', '优先取标了「主推」的商品，不够就拿最新上架的补齐。 三列布局下 3 / 6 / 9 都正好排满整行，不会留半行空位。') }}
            </div>
          </el-form-item>
        </el-form>
        <div class="tip">
          <b>{{ st('decorate.hint.galleryProductsBold', '商品内容在「商品管理」里改。') }}</b>
          {{ st('decorate.note.galleryProductsNote', '这一块的标题、卖点、图片都来自商品库，不在装修里填。一个商品都没上架时，首页上这一块整块不出现。') }}
        </div>
      </section>

      <!-- ===================== 能力 ===================== -->
      <section v-show="sel === 'caps'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.caps" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.caps" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">{{ st('decorate.hint.galleryColumnLayout', '自适应列数下 3 / 4 / 5 条都不会留空缺；固定三列时第 4 条会自己换行。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.caps.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.caps.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
        </el-form>
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.capabilityItems', '能力条目') }}<em class="sh">{{ st('decorate.tag.featureCountIconsShared', '条数和图标通用') }}</em><em>{{ st('decorate.tag.featureTextPerLanguage', '文字分语言') }}</em></span>
          <item-rows :rows="shared.capabilities" :text="form.capText"
            :cols="CAPS_COLS" :disabled="!canEdit"
            :empty-text="st('decorate.empty.featureItems', '还没有条目 —— 这一块暂时不会出现在首页')"
            @add="addRow(shared.capabilities, ['icon'])"
            @remove="removeRow(shared.capabilities, 'capText', $event)" />
          <div class="f-hint">{{ st('decorate.hint.capabilityItemsSection', '首页「What We Offer」那一块，几条都行，按填的顺序排。') }}</div>
        </div>
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.capabilityHighlightedCard', '强调哪一张') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.capsAccent" style="width: 220px">
              <el-option :value="-1" :label="st('decorate.option.capabilityHighlightNone', '都不强调')" />
              <el-option :value="0" :label="st('decorate.option.capabilityHighlight1', '第 1 张')" />
              <el-option :value="1" :label="st('decorate.option.capabilityHighlight2', '第 2 张（推荐）')" />
              <el-option :value="2" :label="st('decorate.option.capabilityHighlight3', '第 3 张')" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.note.highlightedCardPurple', '被强调的那张填成实心紫。只能挑一张——整排都填就没有重点了。') }}
            </div>
          </el-form-item>
        </el-form>
      </section>

      <!-- ===================== 关于我们 ===================== -->
      <section v-show="sel === 'about'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.about" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.about" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.note.variantLayoutNeedsImage', '没配图时三种看起来都一样（都是一张整宽文字卡）——错位和左右分栏本来就是靠那张图撑出来的。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.aboutTitle', '标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <!-- ⚠ 这一格的默认值**不在后端**，是前台按访客语言选的一句内置文案
                 （英文是 About Us，日语是「会社概要」）。所以这里只能说清楚有这回事，
                 说不出具体是哪几个字——写死一个「About Us」摆在日语站上就是错的 -->
            <el-input v-model="form.about.title" maxlength="100"
              :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.aboutBody', '正文') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.about.content" type="textarea" :rows="6" maxlength="2000" show-word-limit
              :placeholder="st('decorate.ph.aboutBody', '工厂规模、成立年份、认证、主要出口市场……')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.aboutImage', '配图') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <!-- ⚠ 同背景图：绑 shared.aboutImage，配图 v3 起归共享层 -->
            <image-field v-model="shared.aboutImage" :disabled="!canEdit"
              :placeholder="st('decorate.ph.aboutImageUrl', '图片完整地址，留空则退成一张整宽文字卡')" />
            <div class="f-hint">
              {{ st('decorate.note.offsetCardNeedsImage', '留空时那张错位叠卡会退成一张整宽文字卡——错位效果本来就是靠这张图撑出来的。') }}
            </div>
          </el-form-item>
          <el-form-item v-if="shared.aboutImage">
            <template #label><span class="f-lbl">{{ st('decorate.field.imageAlt', '图片说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.about.imageAlt" maxlength="120"
              :placeholder="st('decorate.ph.aboutImageAlt', '这张图里是什么；留空则用上面那个标题')" />
            <div class="f-hint">
              {{ st('decorate.note.imageDescriptionHint', '给搜索引擎和读屏软件看的一句话。留空时沿用「标题」那一格， 和 2026-09-13 之前的行为一样——所以这一格是**补充**，不填也不会变差。') }}
            </div>
          </el-form-item>
        </el-form>

        <div class="sec">{{ st('decorate.section.statHighlights', '数字亮点') }}<em class="sh">{{ st('decorate.tag.bentoStatShared', '数字通用') }}</em><em>{{ st('decorate.tag.bentoStatLabelPerLanguage', '说明分语言') }}</em></div>
        <div class="f">
          <item-rows :rows="shared.aboutStats" :text="form.aboutStatText"
            :cols="STAT_COLS" :disabled="!canEdit" :empty-text="st('decorate.empty.statHighlights', '不填就不显示这一排')"
            @add="addRow(shared.aboutStats, ['value'])"
            @remove="removeRow(shared.aboutStats, 'aboutStatText', $event)" />
          <div class="f-hint">
            {{ st('decorate.note.statNumberTranslation', '「关于我们」下面那排数字，如 15 Years / 60+ Countries。 数字本身不分语言（15 就是 15），要翻译的是后面那个词。') }}
          </div>
        </div>
      </section>

      <!-- ===================== 联系方式 ===================== -->
      <section v-show="sel === 'contact'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.contactAddress', '地址') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.contact.address" maxlength="300" />
            <div class="f-hint">{{ st('decorate.hint.contactAddressPerLanguage', '按语言分开填——地址的写法各语言不一样。') }}</div>
          </el-form-item>
          <!--
            号码类的三项是**所有语言共用**的：一个电话号码不会因为访客切了语言就变。
            2026-09-10 之前它们在语言层，商家得把同一串号码填七遍，改一次也要改七遍。
            老数据里语言层那份会在载入时被捞上来（见 fillShared），保存后自动归位。
          -->
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.contactPhone', '电话') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.contact.phone" maxlength="100" />
            <div class="f-hint">{{ st('decorate.hint.contactSharedThreeFields', '以下三项只填一次，所有语言共用。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.contactEmail', '邮箱') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.contact.email" maxlength="200" placeholder="sales@example.com" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">WhatsApp<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.contact.whatsapp" maxlength="100" />
          </el-form-item>
        </el-form>
        <div class="tip">{{ st('decorate.tip.contactEmailFooter', '显示在页脚。询盘发送失败时，页面也会提示客户直接发这个邮箱。') }}</div>
      </section>

      <!-- ===================== 全球布局（世界地图） ===================== -->
      <!--
        首页最下面那张世界地图。2026-09-10 补的——在这之前管理台里配不了，
        组件一直在用自带的天津 + 11 个城市顶着（见 station-site 的 GlobalReach.vue）。

        字段形状是站点那边早就定好等着的，不能改：coords 是 [经度, 纬度]。
        整块不填也不会空一屏，还是那套自带地点。
      -->
      <section v-show="sel === 'reach'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.reach" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.reach" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.hint.destinationsListOnlyNoMap', '「只列目的地」不画那张世界地图，只留一排城市名和下面那几个数字。 地图在窄屏上本来就只有几个看不清的点，站点做给手机看的话这一种更实在。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.reach.title" maxlength="100"
              :placeholder="st('decorate.field.reachTitlePlaceholder', '留空用「Global Reach, Local Impact」那套内置标题')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.reachSubtitle', '区块副标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.reach.lead" maxlength="300" :placeholder="st('decorate.field.reachLeadPlaceholder', '留空用内置的那句（会自动带上总部城市名）')" />
            <div class="f-hint">
              {{ st('decorate.msg.reachLeadIfFilledPrefix', '⚠ 填了就') }}<b>{{ st('decorate.field.wholeSentence', '整句') }}</b>{{ st('decorate.msg.reachLeadWholeSentence', '用你填的这句，里面写') }} <code>{city}</code> {{ st('decorate.msg.reachLeadNoCitySubstitution', '不会被替换成城市名—— 自动带城市名的只有留空时那句内置文案。') }}
            </div>
          </el-form-item>
        </el-form>

        <div class="sec">{{ st('decorate.section.headquarters', '总部（地图以它为中心）') }}</div>
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.cityName', '城市名') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.reach.hqName" maxlength="60" :placeholder="shared.reach.hqName || 'Tongling'" />
            <div class="f-hint">{{ st('decorate.hint.cityName', '会显示在地图上那个点旁边。按语言分开填；这一种语言留空就退回默认语言的名字。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.longitude', '经度') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.reach.hqLon" placeholder="117.81" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.latitude', '纬度') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.reach.hqLat" placeholder="30.94" />
            <div class="f-hint">
              {{ st('decorate.hint.coordinatesFromMapRightClick', '经纬度可以在地图网站上右键查到，') }}<b>{{ st('decorate.field.longitudeBeforeLatitude', '经度在前、纬度在后') }}</b>{{ st('decorate.hint.coordinatesSharedAcrossLanguages', '（如上海 121.47 / 31.23）。 城市点在地图上的位置不分语言，所以这几项所有语言通用。') }}
            </div>
          </el-form-item>
        </el-form>

        <div class="sec">{{ st('decorate.section.destinations', '目的地') }}<em class="sh">{{ st('decorate.tag.coordinatesShared', '坐标通用') }}</em><em>{{ st('decorate.tag.cityNamePerLanguage', '城市名分语言') }}</em></div>
        <div class="f">
          <item-rows :rows="shared.reach.destinations" :text="form.reachDestText" :cols="DEST_COLS" :disabled="!canEdit"
            :empty-text="st('decorate.msg.noDestinations', '没有目的地 —— 地图上只有总部一个点')"
            @add="addRow(shared.reach.destinations, ['lon', 'lat'])"
            @remove="removeRow(shared.reach.destinations, 'reachDestText', $event)" />
          <div class="f-hint">
            {{ st('decorate.hint.shippingCityNamesPerLanguage', '发货去的城市，每条一个点，地图上从总部拉一条弧线过去。 城市名按语言分开填（2026-09-22 起）：日语站上显示「リヨン」而不是 Lyon。 哪种语言没填就退回默认语言那个名字。') }}
          </div>
        </div>

        <div class="sec">{{ st('decorate.section.stats', '数字') }}<em class="sh">{{ st('decorate.tag.bentoStatShared', '数字通用') }}</em><em>{{ st('decorate.tag.bentoStatLabelPerLanguage', '说明分语言') }}</em></div>
        <div class="f">
          <item-rows :rows="shared.reachStats" :text="form.reachStatText"
            :cols="STAT_COLS" :disabled="!canEdit"
            :empty-text="st('decorate.msg.statsAutoCalculated', '不填时组件按地图上的地点数自己算两个，不会空着')"
            @add="addRow(shared.reachStats, ['value'])"
            @remove="removeRow(shared.reachStats, 'reachStatText', $event)" />
          <div class="f-hint">{{ st('decorate.hint.stats', '地图下面那一排数字。数字通用，后面那个词按语言分开填。') }}</div>
        </div>

        <div class="tip">
          <b>{{ st('decorate.msg.statsFallback', '整块留空也不会空着') }}</b>{{ st('decorate.msg.statsFallbackThirdPartyCities', '——站点会用一套自带的地点，但那是别人的城市，配上自己的更好。') }}
        </div>
      </section>

      <!-- ===================== 承诺横条 ===================== -->
      <section v-show="sel === 'trust'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.trust" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.trust" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.hint.layoutBarVersusCardGrid', '深色细横条紧贴 Banner，像一条分隔带，几乎不占高度；四张浅色卡更显眼，但会多占大半屏。') }}
            </div>
          </el-form-item>
        </el-form>
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.commitmentItems', '承诺条目') }}<em class="sh">{{ st('decorate.tag.featureCountIconsShared', '条数和图标通用') }}</em><em>{{ st('decorate.tag.featureTextPerLanguage', '文字分语言') }}</em></span>
          <item-rows :rows="shared.trustItems" :text="form.trustText"
            :cols="TRUST_COLS" :disabled="!canEdit" :max="6"
            :empty-text="st('decorate.empty.featureItems', '还没有条目 —— 这一块暂时不会出现在首页')"
            @add="addRow(shared.trustItems, ['icon'])"
            @remove="removeRow(shared.trustItems, 'trustText', $event)" />
          <div class="f-hint">{{ st('decorate.hint.commitmentItems', '说明那一格可以空着，那一条就只显示一行粗体字。四条是最常见的排法。') }}</div>
        </div>
        <div class="tip">
          <b>{{ st('decorate.msg.commitmentWarning', '这里写的是对外承诺，买家会拿它来主张权利。') }}</b>
          {{ st('decorate.msg.commitmentMustBeOperationallyTrue', '「两年保修」「30 天退换」要有对应的售后流程，「48 小时发货」要仓库真的做得到。 做不到的条目写上去，比不写糟得多——它会变成客服每天都要解释一遍的东西。') }}
        </div>
      </section>

      <!-- ===================== 顾客评价 ===================== -->
      <section v-show="sel === 'reviews'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.reviews" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.reviews" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">{{ st('decorate.hint.reviewsHorizontalScroll', '条数多（六条以上）时用横向滚动，不然评价会占掉两三整屏。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.averageRating', '平均分') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.reviewScore" maxlength="4" style="width: 220px" placeholder="4.8" />
            <div class="f-hint">
              {{ st('decorate.hint.ratingStarsRoundedDown', '留空就不显示那个大分数，只显示下面的评价卡。星星按这个分数向下取整画 （4.8 画 4 颗实星 + 1 颗空星，不画半颗）。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.reviewCount', '评价条数') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-input v-model="shared.reviewCount" maxlength="12" style="width: 220px" placeholder="9363" />
            <div class="f-hint">{{ st('decorate.hint.reviewCount', '显示成「9363 条顾客评价」。这是总数，不是下面录了几条。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.reviews.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.reviews.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
        </el-form>
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.reviews', '评价') }}<em class="sh">{{ st('decorate.tag.countAndStarsShared', '条数和星级通用') }}</em><em>{{ st('decorate.tag.featureTextPerLanguage', '文字分语言') }}</em></span>
          <item-rows :rows="shared.reviewItems" :text="form.reviewText"
            :cols="REVIEW_COLS" :disabled="!canEdit" :max="12"
            :empty-text="st('decorate.msg.noReviews', '还没有评价 —— 只填了分数的话，首页上只显示那个评分头')"
            @add="addRow(shared.reviewItems, ['stars'])"
            @remove="removeRow(shared.reviewItems, 'reviewText', $event)" />
          <div class="f-hint">{{ st('decorate.hint.reviewStars', '星级填 0–5 的整数，留空按 5 星算。正文空着的那一条存的时候会被丢掉。') }}</div>
        </div>
        <div class="tip">
          <b>{{ st('decorate.msg.ratingNotRichSnippet', '⚠ 这个分数不会变成搜索结果里的金色星星，这是有意的。') }}</b>
          {{ st('decorate.msg.ratingNotDeclaredToSearchEngines', '那种富摘要要求评分来自站点上真实、可核查的评价，而这里的数字是手填的。 Google 对「自报评分」的处置是整站结构化数据降权，严重的直接人工处罚—— 拿整站信誉换一行星星不划算。页面上照常显示给访客看，只是不向搜索引擎声明。 下面「常见问题」那一块相反，问答是本站自己的陈述，所以它是会声明的。') }}
        </div>
      </section>

      <!-- ===================== 合作方与代言人 ===================== -->
      <section v-show="sel === 'partners'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.partners" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.partners" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">{{ st('decorate.hint.logoOnlyNeedsLogo', '「只排 logo」那一档不显示名字，所以每条都得有 logo，否则那一格是个首字母圆圈。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.partners.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.partners.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
        </el-form>
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.partners', '合作方') }}<em class="sh">{{ st('decorate.tag.countAndLogoShared', '条数和 Logo 通用') }}</em><em>{{ st('decorate.tag.featureTextPerLanguage', '文字分语言') }}</em></span>
          <item-rows :rows="shared.partnerItems" :text="form.partnerText"
            :cols="PARTNER_COLS" :disabled="!canEdit" :max="12"
            :empty-text="st('decorate.empty.featureItems', '还没有条目 —— 这一块暂时不会出现在首页')"
            @add="addRow(shared.partnerItems, ['logo'])"
            @remove="removeRow(shared.partnerItems, 'partnerText', $event)" />
          <div class="f-hint">
            {{ st('decorate.hint.logoFullUrlAndInitialFallback', 'Logo 要的是完整地址。没有现成地址的话，在「Banner」或「关于我们」那边用选图上传一张， 地址会出现在那个输入框里，复制过来再把那边改回去。 Logo 留空或者加载不出来时，那一格显示名称的首字母——不会出现裂图。') }}
          </div>
        </div>
        <!--
          合作方下面那排竖屏短视频（2026-09-30，照 getjolt.fr）。视频和封面所有语言共用，说明分语言。
          不用 item-rows：那个组件只有文字输入框，视频和封面要走文件库挑（video-field / image-field）。
        -->
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.partnerVideos', '合作方视频') }}<em class="sh">{{ st('decorate.tag.videoShared', '视频和封面通用') }}</em><em>{{ st('decorate.tag.captionPerLanguage', '说明分语言') }}</em></span>
          <div v-if="!shared.partnerVideos.length" class="f-hint">{{ st('decorate.empty.partnerVideos', '还没有视频 —— 合作方下面那一排暂时不会出现') }}</div>
          <div v-for="(row, i) in shared.partnerVideos" :key="row.id" class="pv-row">
            <span class="pv-no">{{ i + 1 }}</span>
            <div class="pv-body">
              <video-field v-model="row.video" :disabled="!canEdit"
                :placeholder="st('decorate.ph.partnerVideo', '竖屏短视频的完整地址')" />
              <image-field v-model="row.poster" :disabled="!canEdit"
                :placeholder="st('decorate.ph.partnerVideoPoster', '封面图（可选，视频下载出来之前显示它）')" />
              <el-input :model-value="form.partnerVideoText?.[row.id]?.caption || ''" maxlength="80" :disabled="!canEdit"
                :placeholder="st('decorate.ph.partnerVideoCaption', '说明（可选），如「Équipe de France · récupération」')"
                @update:model-value="v => setPartnerVideoCaption(row.id, v)" />
            </div>
            <div class="pv-ops">
              <el-button link :disabled="!canEdit || i === 0" @click="moveRow(shared.partnerVideos, i, -1)">↑</el-button>
              <el-button link :disabled="!canEdit || i === shared.partnerVideos.length - 1" @click="moveRow(shared.partnerVideos, i, 1)">↓</el-button>
              <el-button link type="danger" :disabled="!canEdit" @click="removeRow(shared.partnerVideos, 'partnerVideoText', i)">×</el-button>
            </div>
          </div>
          <el-button size="small" :disabled="!canEdit || shared.partnerVideos.length >= 12"
            @click="addRow(shared.partnerVideos, ['video', 'poster'])">+ {{ st('decorate.action.addVideo', '加一条视频') }}</el-button>
          <div class="f-hint">
            {{ st('decorate.hint.partnerVideos', '竖屏 9:16，每条 10～20 秒、压到 10MB 以内再传。前台静音循环播放，滚到才开始播、划走就停；访客点一下才出声音。最多 12 条。') }}
          </div>
        </div>
        <div class="tip">
          <b>{{ st('decorate.msg.partnersRealOnly', '只放真的有合作关系的。') }}</b>{{ st('decorate.msg.partnersTrademarkRisk', '把没授权的球队徽标摆上首页是商标侵权， 而这类页面恰恰是对方法务最容易搜到的。') }}
        </div>
      </section>

      <!-- ===================== 常见问题 ===================== -->
      <section v-show="sel === 'faq'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.faq" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.faq" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">
              {{ st('decorate.hint.faqAccordionAnswersIndexed', '手风琴默认展开第一条，点标题换一条。两种版式下答案都在网页源码里， 收录不受影响（折叠只是视觉上的）。') }}
            </div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.faq.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.faq.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
        </el-form>
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.faq', '问答') }}<em class="sh">{{ st('decorate.tag.countAndOrderShared', '条数和顺序通用') }}</em><em>{{ st('decorate.tag.questionsAnswersPerLanguage', '问和答分语言') }}</em></span>
          <item-rows :rows="shared.faqItems" :text="form.faqText"
            :cols="FAQ_COLS" :disabled="!canEdit" :max="20"
            :empty-text="st('decorate.msg.noFaq', '还没有问答 —— 这一块暂时不会出现在首页')"
            @add="addRow(shared.faqItems, [])"
            @remove="removeRow(shared.faqItems, 'faqText', $event)" />
          <div class="f-hint">
            {{ st('decorate.hint.faqBothFieldsRequired', '问和答都填了才算一条，只填一半的存的时候会被丢掉。答案里空一行就是分段。') }}
          </div>
        </div>
        <div class="tip">
          <b>{{ st('decorate.msg.faqSentToSearchEngines', '这一块是会发给搜索引擎的') }}</b>{{ st('decorate.msg.faqAnswerCarriesLongTailKeywords', '（FAQPage 结构化数据），所以答案值得写长一点： 「配送要多久」「怎么选尺码」这类长尾搜索词全在答案里，而不在问题里。') }}
          <br>
          {{ st('decorate.msg.faqSameTextAsPageNoSeparateVersion', '⚠ 发给搜索引擎的和页面上显示的是**同一份文字**，Google 要求两者一字不差。 所以别在别处另写一套「给搜索引擎看的」版本——改就在这里改。') }}
        </div>
      </section>

      <!-- ===================== 询盘表单 ===================== -->
      <!-- 表单字段是写死的（姓名 / 邮箱 / 需求），装修里没有可配的东西。
           留这一块是因为它在首页的**顺序**和**开关**要能改——那正是左边那一列的事 -->
      <section v-show="sel === 'inquiry'">
        <el-form class="deco-form" label-position="top" :disabled="!canEdit">
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.layout', '版式') }}<em class="sh">{{ st('decorate.badge.allLanguages', '所有语言通用') }}</em></span></template>
            <el-select v-model="shared.variants.inquiry" style="width: 100%">
              <el-option v-for="v in BLOCK_VARIANTS.inquiry" :key="v[0]" :value="v[0]" :label="v[1]" />
            </el-select>
            <div class="f-hint">{{ st('decorate.hint.whiteBackgroundAfterLight', '上一块也是浅底的时候，这里换成白底，两屏之间才有个交界。') }}</div>
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoTitle', '区块标题') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.inquiry.title" maxlength="100" :placeholder="st('decorate.ph.bentoTitleBuiltin', '留空则用内置标题（按访客语言自动切换）')" />
          </el-form-item>
          <el-form-item>
            <template #label><span class="f-lbl">{{ st('decorate.field.bentoLead', '一句话说明') }}<em>{{ st('decorate.badge.perLanguage', '分语言') }}</em></span></template>
            <el-input v-model="form.inquiry.lead" maxlength="200" :placeholder="st('decorate.ph.bentoLeadBuiltin', '留空则用内置的那一句')" />
          </el-form-item>
        </el-form>
        <div class="tip">
          <b>{{ st('decorate.msg.formFieldsFixed', '表单字段是固定的') }}</b>{{ st('decorate.msg.formFieldsFixedInquiryManagement', '（姓名、邮箱、需求），不在装修里配。收到的询盘在左边菜单「询盘管理」里看。') }}
        </div>
        <div class="f-hint mt12">
          {{ st('decorate.hint.inquiryBlockDefaultNavTarget', '⚠ 把这一块移除之前想清楚：首页默认导航里的「Contact」指向的就是它（') }}<code>/#inquiry</code>{{ st('decorate.hint.inquiryBlockNavMustBeUpdated', '）。 移除之后那条导航会点了没反应，得同时去「导航菜单」里改掉或删掉它。') }}
        </div>
      </section>

      <!-- ===================== 导航 ===================== -->
      <section v-show="sel === 'nav'">
        <!--
          一条都不填时后端返回内置的默认导航（见 SiteOpenService.defaultNav）。
          所以这里不预填默认值：预填了就等于把那份默认复制到前端来，
          将来后端改了默认，两边就对不上。

          下面那句提示里的默认导航是**问后端要来的**（defaultNav，见 script 里
          「留空 = 用默认」那一段），不是抄在这儿的——同一个理由。
        -->
        <div class="f">
          <span class="f-lbl">{{ st('decorate.field.menuItems', '菜单项') }}<em class="sh">{{ st('decorate.tag.countAndUrlsShared', '条数和地址通用') }}</em><em>{{ st('decorate.tag.namesPerLanguage', '名称分语言') }}</em></span>
          <item-rows :rows="shared.nav" :text="form.navText" :cols="NAV_COLS"
            :disabled="!canEdit" :empty-text="st('decorate.msg.noMenuItems', '一条都没填 —— 前台用内置的那几条导航')"
            @add="addRow(shared.nav, ['url'])"
            @remove="removeRow(shared.nav, 'navText', $event)" />
          <div class="f-hint">
            <template v-if="defaultNav.length">
              {{ st('decorate.hint.menuBuiltInListIntro', '一条都不填的话，前台显示这几条：') }}<b>{{ defaultNav.map(n => n.title).join(' / ') }}</b>。
            </template>
            <template v-else>{{ st('decorate.hint.menuBuiltInFallback', '留空则使用系统内置的那几条导航。') }}</template>
            {{ st('decorate.hint.menuCustomReplacesDefault', '填了就整份替换掉默认的。') }}
            <b>{{ st('decorate.msg.menuItemsShared', '有几条、每条指向哪是所有语言共用的') }}</b>{{ st('decorate.msg.menuItemsSharedRootRelativePaths', '——换语言只是换那几个名字， 不该出现「日语站少一个入口」这种事。 地址填站点内的根相对路径，如') }} <code>/products</code>、<code>/#about</code>。
            <b>{{ st('decorate.msg.addBlogMenuItem', '站点写了文章的话，博客入口要自己在这加一条') }} <code>/blog</code></b>{{ st('decorate.msg.blogEntryNotInDefaultNav', '—— 默认导航里没有它（新站还没有文章，加进去是个死链）。') }}
            <b>{{ st('decorate.msg.noLanguagePrefix', '语言前缀不要写在这里') }}</b>{{ st('decorate.msg.languagePrefixSuffix', '——前台会按当前语言自己拼（比如') }} <code>/ja/products</code>）。
          </div>
        </div>
        <div class="tip">
          {{ st('decorate.hint.topBarForVisitorsOnly', '买家登录后顶栏会把导航整条收起来，换成购物车和头像。所以这一排主要是给访客和搜索引擎看的。') }}
        </div>
      </section>

        </div><!-- /.fbody -->

        <!-- 保存条吸在这一列底下。装修内容长，翻到下面还能存 -->
        <div class="savebar">
          <div class="savebar-btns">
            <el-button type="primary" :loading="saving" :disabled="!canEdit" @click="save">{{ st('decorate.action.saveDraft', '保存草稿') }}</el-button>
            <!--
              ⚠ 「发布」在有草稿**或者**当前有没保存的改动时才能点。
                 只看 hasDraft 的话，商家填完直接点发布会被告知「没有待发布的改动」——
                 他明明刚填了一屏。所以这一路走「先存再发」，见 publish()。
            -->
            <el-button type="success" :loading="publishing"
              :disabled="!canEdit || (!hasDraft && !isDirty)" @click="publish">{{ st('decorate.action.publish', '发布到网站') }}</el-button>
            <el-button :disabled="!canEdit" @click="reload">{{ st('decorate.action.discardChanges', '放弃修改') }}</el-button>
            <span class="sb-sp"></span>
            <el-button v-if="hasDraft" link :disabled="!canEdit" @click="discard">{{ st('decorate.btn.discardDraft', '放弃草稿') }}</el-button>
            <el-button link type="danger" :disabled="!canEdit" @click="clearAll">{{ st('decorate.btn.clearAll', '全部清空') }}</el-button>
          </div>
          <div class="savebar-note">
            <template v-if="!canEdit">{{ st('decorate.msg.readOnlyHint', '只读模式：可以切语言、翻区块看内容，改不了也存不了。') }}</template>
            <template v-else>
              <b>{{ st('decorate.draftNotice.savingChangesNothing', '保存草稿不会改变网站') }}</b>{{ st('decorate.draftNotice.publishToGoLive', '，点「发布到网站」才见客。存的是') }}<b>{{ st('decorate.draftNotice.allLanguages', '全部语言') }}</b>{{ st('decorate.draftNotice.notOnlyThisOne', '，不只当前这一种。') }}
              <template v-if="hasDraft">
                <br><span class="sb-draft">{{ st('decorate.status.unpublishedChanges', '● 有未发布的改动') }}</span>
                <template v-if="site.draftTime">{{ st('decorate.draftNotice.savedAt', '（草稿保存于 {draftTime}）', { draftTime: site.draftTime }) }}</template>
                {{ st('decorate.draftNotice.siteStillShowsLastPublish', '——网站上现在显示的还是上一次发布的内容。') }}
              </template>
            </template>
          </div>
        </div>
      </div><!-- /.col-form -->

      <!--
        ===================== 第三列：实时预览 =====================

        里面装的是**真前台**（一个 iframe），不是照着真站抄的一份假版式——
        抄一份的话真站改一次样式这边就旧一次，而且旧了没人会发现。
        推过去的也是后端算好的数据，不是草稿 JSON，理由见 script 里那一段。
      -->
      <div class="col-prev">
        <div class="pvbar">
          <span class="pvbar-t">{{ st('decorate.preview.title', '实时预览') }}</span>
          <button type="button" class="pvbtn" :class="{ on: pvMode === 'desktop' }"
            @click="pvMode = 'desktop'">{{ st('decorate.preview.modeDesktop', '桌面') }}</button>
          <button type="button" class="pvbtn" :class="{ on: pvMode === 'mobile' }"
            @click="pvMode = 'mobile'">{{ st('decorate.preview.modeMobile', '手机') }}</button>
          <span class="pvzoom">{{ Math.round(pvZoom * 100) }}%</span>
          <span class="pvbar-sp"></span>
          <span class="pvstate" :class="pvState">{{ PV_STATE_TEXT[pvState] }}</span>
          <button type="button" class="pvbtn" :title="st('decorate.preview.reloadTitle', '重新载入预览')" :disabled="!previewUrl"
            @click="reloadPreview">{{ st('decorate.btn.reload', '刷新') }}</button>
          <button type="button" class="pvbtn" :disabled="!previewUrl"
            :title="st('decorate.preview.openWindowTitle', '在一个独立窗口里打开同一份预览（可以拖到另一块屏幕上）')"
            @click="openPreviewWindow">{{ st('decorate.btn.newWindow', '新窗口') }}</button>
        </div>

        <div class="pvstage" ref="stageEl">
          <!-- 开不了预览的三种原因，各说清楚是哪一种，别只画一块空白 -->
          <div v-if="!previewer" class="pvmsg">{{ st('decorate.preview.noPreviewService', '这个管理台没有接预览接口，所以这一列是空的。') }}</div>
          <div v-else-if="!canEdit" class="pvmsg">
            {{ st('decorate.preview.endpointNeedsPermission', '预览接口要') }} <code>site:station:edit</code> {{ st('decorate.preview.readOnlyCannotOpen', '权限，只读的人开不了。 想看站点现在长什么样，用顶栏那个「访问站点」。') }}
          </div>
          <div v-else-if="!frontUrl" class="pvmsg">
            {{ st('decorate.preview.domainMissingForStorefront', '站点档案里还没填域名，没法打开前台。去「站点概览」补上再回来。') }}
          </div>
          <template v-else>
            <div class="pvframe" :style="frameStyle">
              <iframe ref="frameEl" :src="previewUrl" :title="st('decorate.preview.frameTitle', '装修预览')"></iframe>
            </div>
            <!--
              浏览器拒绝内嵌时盖上来的那一层。判据是「一直没收到那边的 ready」——
              被 X-Frame-Options 拦掉的 iframe 从外面读不出任何状态，
              「它没跟我说话」是唯一能拿到的信号。
            -->
            <div v-if="pvState === 'blocked'" class="pvblock">
              <b>{{ st('decorate.preview.embedBlockedTitle', '浏览器不让把站点嵌进这一列') }}</b>
              <p>
                {{ st('decorate.preview.frameOptionsFromMainDomain', '主域发的') }} <code>X-Frame-Options: SAMEORIGIN</code> {{ st('decorate.preview.embedBlockedCsp', '不许') }} <code>console.</code>
                {{ st('decorate.preview.embedBlockedForSubdomains', '子域嵌它。要用这一列，nginx 上给预览那条地址放行一次 （') }}<code>deploy/nginx</code> {{ st('decorate.preview.cspNginxIn', '里') }} <code>$station_frame_csp</code> {{ st('decorate.preview.nginxRuleBeforeAllowing', '那两行）； 放行之前先用「新窗口」。') }}
              </p>
              <el-button size="small" type="primary" @click="openPreviewWindow">{{ st('decorate.preview.openInNewWindow', '用新窗口预览') }}</el-button>
            </div>
          </template>
        </div>
      </div><!-- /.col-prev -->

    </div><!-- /.deco-cols -->

    <!--
      「添加区块」。列的是**被移除的那几块**，不是一个组件库——
      首页就这七块，每块一份内容，加不出第二个 Banner 来。
      （demo 里那个能同一种放好几次，那要换一套存法，见评审文档 §4.A。）
    -->
    <el-dialog v-model="pickerOpen" :title="st('decorate.block.addTitle', '添加区块')" width="520px" align-center>
      <p class="dlg-lead">
        {{ st('decorate.lead.hiddenBlocksRestore', '下面这几块现在不在首页上。内容都还在，加回来还是原来那份，位置也还是原来那个位置。') }}
      </p>
      <div class="blocklist">
        <button v-for="key in removedBlocks" :key="key" type="button"
          class="blockcard" :disabled="!canEdit" @click="addBlock(key)">
          <span class="ic">{{ BLOCK_META[key].icon }}</span>
          <b>{{ BLOCK_META[key].name }}</b>
          <small>{{ BLOCK_META[key].desc }}</small>
        </button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="StationConsoleDecorateForm">
import { ref, reactive, computed, inject, watch, onMounted, onBeforeUnmount, h } from 'vue'
/*
 * ⚠ 这是这个「宿主中立」目录里唯一一个框架级的 import。vue-router 是两个宿主
 *   都装了的包（不是某个宿主的 `@/`），所以不违反 README 那条约束；
 *   用它是因为「站内跳走」只有路由守卫拦得住，beforeunload 对它一无所知。
 */
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ItemRows from './ItemRows.vue'
import ImageField from './ImageField.vue'
import VideoField from './VideoField.vue'
import { useStationT } from './useStationT'

const props = defineProps({
  /**
   * 保存函数，由宿主注入：(contentJsonString) => Promise
   *
   * 两个宿主的接口签名不一样——ERP 那边要带 siteCode，独立管理台从会话里取——
   * 这是它们之间唯一的实质差异，所以只把这一处交出去，组件内部不关心是谁在存。
   */
  saver: { type: Function, required: true },
  /**
   * 装修预览：`(contentJsonString, lang) => Promise<{ data }>`。
   *
   * 和 saver 一样由宿主注入——它调的是宿主自己的接口。
   * 不传就没有「预览」按钮，表单其余部分照常能用。
   */
  previewer: { type: Function, default: null },
  /**
   * 发布：`() => Promise`。把库里那份草稿推成网站上看到的内容。
   *
   * ⚠ 不传参数是有意的——要发布的就是刚才存下去的那一份。让前端把 JSON 再传一遍，
   *   就有了「发布的和刚才保存的不是同一份」的可能（两个标签页各开一个装修页）。
   */
  publisher: { type: Function, default: null },
  /** 放弃草稿：`() => Promise`。回到上一次发布的那份 */
  discarder: { type: Function, default: null },
  /**
   * 机器翻译：`(from, to, texts) => Promise<{ data: { texts } }>`。
   *
   * 不传就不显示「翻译」按钮，其余部分照常用——宿主没接这个接口时
   * 界面上不该出现一个点了报错的按钮。
   */
  translator: { type: Function, default: null }
})

/**
 * 文案出口。共享组件不能 import 宿主的 i18n（见 useStationT.js），
 * 所以拿宿主 provide 进来的翻译器；宿主没提供、或表里缺键时落回调用处写的中文原文。
 */
const st = useStationT()

const site = inject('stationSite', ref({}))
const reloadSite = inject('reloadStationSite', () => {})

/**
 * 这个人能不能改装修。由宿主注入，见文件头「只读怎么处理」。
 *
 * ⚠ 默认 true，不是 false。宿主忘了注入的表现应该是「和以前一样能用」，
 *   而不是「整页灰着谁也存不了」——真正的闸门在后端（AuthInterceptor 判
 *   site:station:edit），前端这一层只是别让人白填一屏。
 */
const canEdit = inject('stationCanEdit', ref(true))

/* ==========================================================================
   左边那一列：全站设置 + 首页区块
   ========================================================================== */

/** 全站的东西。不是首页的一块，所以不参与排序，也没有开关 */
const GLOBAL_ITEMS = computed(() => [
  { key: 'brand', icon: '✳', name: st('decorate.block.brand.name', '品牌与 Logo'), desc: st('decorate.block.brand.desc', '顶栏和页脚上的名字、标语、Logo 图。') },
  { key: 'nav', icon: '☰', name: st('decorate.block.nav.name', '导航菜单'), desc: st('decorate.block.nav.desc', '顶栏那一排链接。买家登录后顶栏会把它整条收起来，所以这排主要是给访客和搜索引擎看的。') },
  { key: 'contact', icon: '✆', name: st('decorate.block.contact.name', '联系方式'), desc: st('decorate.block.contact.desc', '显示在页脚；询盘发送失败时也提示客户直接发这个邮箱。') },
  { key: 'seo', icon: '⌕', name: st('decorate.block.seo.name', 'SEO 标题与描述'), desc: st('decorate.block.seo.desc', '搜索结果里显示的那两行字。') }
])

/**
 * 首页的十一块。
 *
 * ⚠ 这里的键必须和 station-site/app/pages/index.vue 里的 DEFAULT_ORDER 一模一样。
 *   对不上的键前台会直接丢掉——表现是「在管理台里排了序，首页纹丝不动」。
 */
const BLOCK_META = computed(() => ({
  hero: { icon: '▤', name: 'Banner', desc: st('decorate.block.hero.desc', '首屏那一整屏：背景图 + 大标题 + 两个按钮。') },
  trust: { icon: '✓', name: st('decorate.block.trust.name', '承诺横条'), desc: st('decorate.block.trust.desc', 'Banner 正下方那一条细横幅：保修、发货、退货、支持。') },
  bento: { icon: '▦', name: st('decorate.block.bento.name', '三栏特色'), desc: st('decorate.block.bento.desc', 'Banner 下面那一组卡：一张大卡 + 一张小卡 + 一条深色横幅。') },
  products: { icon: '▣', name: st('decorate.block.products.name', '主推商品'), desc: st('decorate.block.products.desc', '从商品库里取，不在这里填内容。') },
  caps: { icon: '◆', name: st('decorate.block.caps.name', '能力介绍'), desc: st('decorate.block.caps.desc', '「我们能做什么」那一组卡，几条都行。') },
  reviews: { icon: '★', name: st('decorate.block.reviews.name', '顾客评价'), desc: st('decorate.block.reviews.desc', '评分 + 评价卡。⚠ 分数是手填的，所以刻意不做成搜索结果里的星星。') },
  partners: { icon: '⛨', name: st('decorate.block.partners.name', '合作方与代言人'), desc: st('decorate.block.partners.desc', '球队、俱乐部、运动员的 logo 和名字。') },
  about: { icon: '▧', name: st('decorate.block.about.name', '关于我们'), desc: st('decorate.block.about.desc', '配图 + 正文 + 一排数字。首页 #about 这个锚点指向它。') },
  faq: { icon: '?', name: st('decorate.block.faq.name', '常见问题'), desc: st('decorate.block.faq.desc', '问答手风琴。同一批问答会以 FAQPage 结构化数据发给搜索引擎。') },
  reach: { icon: '◍', name: st('decorate.block.reach.name', '全球布局'), desc: st('decorate.block.reach.desc', '世界地图，从总部向各目的地辐射弧线。') },
  inquiry: { icon: '✉', name: st('decorate.block.inquiry.name', '询盘表单'), desc: st('decorate.block.inquiry.desc', '首页 #inquiry 这个锚点指向它。表单字段固定，不用配。') }
}))
const BLOCK_KEYS = Object.keys(BLOCK_META.value)

/**
 * 每块可选的版式。
 *
 * ⚠ 这张表是**管理台这一侧**的清单，真正把它画出来的是
 *   station-site/app/pages/index.vue（和 GlobalReach.vue）。加一种版式要两边一起加，
 *   只加这边的表现是「选了没反应」。
 *
 * Banner 是两个维度（对齐 + 高度），所以它不在这张表里，单独两个下拉。
 */
const BLOCK_VARIANTS = computed(() => ({
  bento: [['bento', st('decorate.style.bento.optionStaggered', '错落 bento（大卡 + 数字卡 + 深色横条）')], ['grid3', st('decorate.style.bento.optionGrid3', '三等分卡片')]],
  products: [['grid', st('decorate.style.products.optionGrid', '三列卡片')], ['wide', st('decorate.style.products.optionWide', '两列大图')],
    ['carousel', st('decorate.style.products.optionCarousel', '横向轮播（左右翻页 + 底部「查看全部」按钮）')]],
  caps: [['auto', st('decorate.caps.auto', '自适应列数（推荐）')], ['g3', st('decorate.caps.g3', '固定三列')]],
  about: [['stagger', st('decorate.about.stagger', '错位叠卡（图在上、文字卡压上来）')], ['side', st('decorate.about.side', '左图右文')], ['solo', st('decorate.about.solo', '只有文字卡')]],
  reach: [['map', st('decorate.reach.map', '地图 + 图例')], ['list', st('decorate.reach.list', '只列目的地，不画地图')]],
  inquiry: [['soft', st('decorate.inquiry.soft', '浅底')], ['plain', st('decorate.inquiry.plain', '白底')]],
  trust: [['bar', st('decorate.trust.bar', '深色细横条')], ['cards', st('decorate.trust.cards', '四张浅色卡')]],
  reviews: [['cards', st('decorate.reviews.cards', '卡片（自适应列数）')], ['strip', st('decorate.reviews.strip', '一行横向滚动')]],
  partners: [['named', st('decorate.partners.named', 'logo + 名字和项目')], ['logos', st('decorate.partners.logos', '只排 logo')]],
  faq: [['accordion', st('decorate.faq.accordion', '手风琴（同时只展开一条）')], ['list', st('decorate.faq.list', '全部展开')]]
}))
const HERO_ALIGNS = computed(() => [['left', st('decorate.hero.alignLeft', '左对齐')], ['center', st('decorate.hero.alignCenter', '居中')]])
const HERO_HEIGHTS = computed(() => [['tall', st('decorate.hero.heightTall', '整屏')], ['short', st('decorate.hero.heightShort', '半屏')]])

const LINK_HINT = computed(() => st('decorate.hint.link', '填 /products 这种站内路径（会自动加上语言前缀）、#inquiry 这种本页锚点，或者 https:// 开头的外链。'))

/**
 * 校验一个链接。返回一句话说明哪儿不对，没问题返回空串。
 *
 * ⚠ 这**不是安全边界**，真正挡住 `javascript:` 的是前台 index.vue 的 `heroLink()`——
 *   库里可能有老数据，也可能有人绕开这个界面直接调接口，所以最后一道必须在渲染那一侧。
 *   这里只是让商家当场看见自己填错了，而不是发布之后点一下才发现没反应。
 *
 * 认的四种写法和前台 `heroLink()` **必须一致**。那边加一种，这里也要加一种，
 * 否则表现是「管理台说这么填不行，但其实是可以的」，或者反过来（更糟）。
 */
function linkError(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return ''
  if (value.startsWith('#')) return ''
  if (value.startsWith('/')) return ''
  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) {
    // javascript: data: vbscript: 这一类。说清楚为什么不让填，不然会被当成界面的毛病
    return st('decorate.hint.linkProtocol', '只支持 http/https 外链。其它协议（javascript:、data: 等）出于安全不允许，点了会在访客浏览器里执行代码。')
  }
  return st('decorate.hint.linkFormat', '站内地址要以 / 开头（如 /products），本页锚点以 # 开头（如 #inquiry），外链要带 https://。')
}

/** 当前在改哪一块。值要么是 GLOBAL_ITEMS 的 key，要么是区块的 key */
const sel = ref('brand')

const currentMeta = computed(() =>
  GLOBAL_ITEMS.value.find(i => i.key === sel.value) || BLOCK_META.value[sel.value] || { icon: '', name: '', desc: '' })
const loading = ref(false)
const saving = ref(false)

/** 站点开的语言，第一个是默认语言 */
const langs = computed(() =>
  String(site.value.langs || '').split(',').map(s => s.trim().toLowerCase()).filter(Boolean))
const defaultLang = computed(() => langs.value[0] || 'en')

/** 当前正在编辑哪种语言 */
const lang = ref('')

/** 共享层：所有语言通用的字段。哪些能放进来见文件头「哪些东西只存一份」 */
const shared = reactive({
  logo: '',
  /**
   * Banner 幻灯片：`[{ id, image }]`，顺序就是轮播顺序。
   *
   * ⚠ 有几张、什么顺序是**所有语言通用**的，每张的文案分语言（存在语言层的
   *   `slideText[id]` 里，见 emptyLangForm）。id 就是为了把两边对上——
   *   用下标的话，往中间插一张，后面每一张的文案就都错位了。
   */
  heroSlides: [],
  /**
   * Banner 两个按钮点了去哪儿。留空 = 前台内置的那两个目的地
   * （主按钮去商品列表、次按钮跳本页询盘表单）。
   *
   * ⚠ 在共享层：一个网址不会因为语言不同就换一个。按钮上的**字**分语言
   *   （form.hero.btn1/btn2），两边在同一个 `hero` 父键底下，所以键名必须岔开——
   *   这正是文件头「各用各的键名」那条规矩。
   */
  heroBtn1Url: '',
  heroBtn2Url: '',
  /*
   * 下面这五张列表和幻灯片是同一个套路：**有几条、什么顺序、以及条目上那些
   * 不是文字的东西（网址、图标、数值）在这儿，只存一份；每条的文字在语言层
   * 按 id 存**（navText / highlightText / capText / aboutStatText / reachStatText）。
   *
   * 这么拆是因为商家的实际流程是「页面搭一次，之后只翻译文字」：加一条导航、
   * 换一个图标、调一下顺序，都该是所有语言一起变的。整条都放在语言层的老做法下，
   * 七种语言能排出七种不同的导航，而同一个网址商家得填七遍、改七遍。
   */
  /** 导航：`[{ id, url }]`。名字分语言 */
  nav: [],
  /** 三栏特色：`[{ id, icon }]`。标题和说明分语言 */
  highlights: [],
  /** 能力条目：`[{ id, icon }]`。标题和说明分语言 */
  capabilities: [],
  /** 关于我们下面那排数字：`[{ id, value }]`。数字不分语言，说明分语言 */
  aboutStats: [],
  /** 地图下面那排数字：`[{ id, value }]`。同上 */
  reachStats: [],
  /** 承诺横条：`[{ id, icon }]`。标题和说明分语言 */
  trustItems: [],
  /**
   * 顾客评价：`[{ id, stars }]`。署名、身份、正文分语言。
   *
   * ⚠ `stars` 在表单里是**字符串**（输入框里敲的），存之前转成数字，见 composePayload。
   *   不转的话库里会躺着 `"5"`，而前台按数字画星——一个字符串在那儿也能工作，
   *   但下一个人读到这份 JSON 会以为星级本来就是文本。
   */
  reviewItems: [],
  /** 合作方：`[{ id, logo }]`。名称和项目分语言（球队名各语言确实不同） */
  partnerItems: [],
  /** 合作方下面那排竖屏短视频（2026-09-30）：[{id, video, poster}]，说明在语言层 partnerVideoText */
  partnerVideos: [],
  /** 常见问题：`[{ id }]`。问和答全在语言层——这一块没有任何不分语言的东西 */
  faqItems: [],
  /** 评分和评价条数。数字不分语言，说明文字在语言层的 reviews.title/lead */
  reviewScore: '',
  reviewCount: '',
  /** 三栏特色里那张粉色数字卡的数字。说明在语言层的 bento.statLabel */
  bentoStatValue: '',
  aboutImage: '',
  contact: { phone: '', email: '', whatsapp: '' },
  // 经纬度在表单里是字符串（输入框），存之前转成数字。见 buildReach
  reach: { hqName: '', hqLon: '', hqLat: '', destinations: [] },
  /** 首页区块从上到下的顺序。存进 contentJson 的 order */
  order: [],
  /** 关掉了哪几块。存进 contentJson 的 off */
  off: [],
  /** blocks.products.limit —— 首页显示几个商品 */
  productLimit: 6,
  /** blocks.caps.accent —— 能力卡强调第几张，-1 = 都不强调 */
  capsAccent: 1,
  /**
   * 每块的版式。存进 `blocks.<区块>` 里，不分语言——
   * 换一种摆法不该让七种语言各填一遍。
   *
   * ⚠ 键名和取值必须和 station-site/app/pages/index.vue 里认的一致，
   *   对不上的表现是「在管理台里换了版式，首页纹丝不动」。
   */
  variants: {
    hero: 'left', heroHeight: 'tall', bento: 'bento', products: 'grid', caps: 'auto',
    about: 'stagger', reach: 'map', inquiry: 'soft',
    trust: 'bar', reviews: 'cards', partners: 'named', faq: 'accordion'
  }
})

/**
 * 这份 JSON 里我们不认识的顶层键，原样带回去。
 *
 * 现在**一个都没有**——order / off / blocks 都进了 KNOWN_ROOT_KEYS，各自有编辑界面了。
 * 这个兜底留着不删，因为它防的是一类持续存在的事故：
 *
 *   将来后端或者别的地方往这份 JSON 里加了一个新顶层键，而这个表单还不认识它。
 *   没有这一层的话，商家在这一页点一次保存，那个键就没了，**而且没有任何提示**——
 *   order/off/blocks 在第三步之前正是这个处境。
 */
const passthrough = reactive({})

/** 我们自己管的顶层键，其余的进 passthrough */
const KNOWN_ROOT_KEYS = ['v', 'i18n', 'logo', 'hero', 'about', 'contact', 'reach',
  'bento', 'products', 'caps', 'inquiry',
  'nav', 'highlights', 'capabilities',
  'trust', 'reviews', 'partners', 'faq',
  'order', 'off', 'blocks']

/* ==========================================================================
   区块顺序与开关
   -------------------------------------------------------------------------
   ⚠ 这一段的取舍要和 station-site/app/pages/index.vue 的 blockOrder 保持一致：
     不认识的键丢掉、重复的去掉、**漏掉的补在最后**。
     两边一致，商家在这儿看到的顺序才等于首页上的顺序。
   ========================================================================== */

/** 规整一份顺序：只留认识的、去重、缺的补在最后 */
function normalizeOrder(list) {
  const seen = new Set()
  const out = []
  for (const key of Array.isArray(list) ? list : []) {
    if (BLOCK_KEYS.includes(key) && !seen.has(key)) { seen.add(key); out.push(key) }
  }
  for (const key of BLOCK_KEYS) if (!seen.has(key)) out.push(key)
  return out
}

const isOff = key => shared.off.includes(key)

/**
 * 首页上现在有哪几块（左边那一列列的就是它）。
 *
 * ⚠ `shared.order` 里**七块一直都在**，移除只是往 `off` 里加一个键——
 *   所以「移除」不会丢内容，加回来还是原来那份，位置也还在原处。
 *   界面上说的是「移除 / 添加」，存的是同一个 off，两件事没有第二套状态。
 */
const usedBlocks = computed(() => shared.order.filter(key => !isOff(key)))

/** 不在首页上的那几块。「添加区块」那个弹层列的就是它 */
const removedBlocks = computed(() => shared.order.filter(key => isOff(key)))

/** 「添加区块」弹层开着没有 */
const pickerOpen = ref(false)

/**
 * 从首页上移除一块。内容留着，随时能加回来。
 *
 * 移掉的正好是当前正在编辑的那一块时，跳到还留在页面上的第一块——
 * 不跳的话中间那一列会停在一个「已经不在页面上」的表单上，
 * 填进去的东西看不见效果。
 */
function removeBlock(key) {
  if (!canEdit.value || isOff(key)) return
  shared.off.push(key)
  if (sel.value === key) sel.value = usedBlocks.value[0] || 'brand'
}

/** 加回首页。位置还是它原来在 order 里的位置，不是追加到最后 */
function addBlock(key) {
  if (!canEdit.value) return
  const i = shared.off.indexOf(key)
  if (i >= 0) shared.off.splice(i, 1)
  sel.value = key
  pickerOpen.value = false
}

/**
 * 上移 / 下移一格。
 *
 * ⚠ 按**看得见的那几块**算一格，不是按 order 的下标：中间隔着一块被移除的，
 *   按下标挪会出现「点一下箭头，列表纹丝不动」——它和那块看不见的换了位置。
 */
function moveBlock(key, delta) {
  if (!canEdit.value) return
  const list = usedBlocks.value
  const from = list.indexOf(key)
  const to = from + delta
  if (from < 0 || to < 0 || to >= list.length) return
  const a = shared.order.indexOf(key)
  const b = shared.order.indexOf(list[to])
  ;[shared.order[a], shared.order[b]] = [shared.order[b], shared.order[a]]
}

/**
 * Banner 最多几张。
 *
 * 5 张不是随手定的：每一张都是整屏大图，全都要在首屏下载
 * （轮播的图留在 DOM 里，切过去才下载的话第一次切换会白一下）。
 * 再多就是拿首屏加载速度换一个没人看完的轮播。
 */
const HERO_IMAGE_MAX = 5

/** 当前在编辑第几张幻灯片。只是界面状态，不进 contentJson */
const slideIndex = ref(0)

/**
 * 新条目的 id。够短能看，够随机不会撞——它只要在这一份 JSON 里唯一。
 *
 * id 是「结构共享一份、文案分语言」这套存法的关键：条目在共享层、文案在语言层，
 * 靠它把两边对上。**不能用下标代替**——往中间插一条，后面每一条的文案就全错位了。
 */
function newRowId() {
  return 'r' + Math.random().toString(36).slice(2, 7)
}

/** 幻灯片的 id 带个 s，在库里那份 JSON 里一眼能认出来是哪一种条目 */
function newSlideId() {
  return 's' + Math.random().toString(36).slice(2, 7)
}

function addSlide(image = '') {
  if (!canEdit.value || shared.heroSlides.length >= HERO_IMAGE_MAX) return
  shared.heroSlides.push({ id: newSlideId(), image, video: '' })
  slideIndex.value = shared.heroSlides.length - 1
  ensureSlideText()
}

/**
 * 删一张。
 *
 * ⚠ 各语言里那张的文案一起删掉：留着的话，商家看不见它、改不了它，
 *   而它会一直躺在 contentJson 里，下次加一张正好撞上同一个 id 时又冒出来。
 */
function removeSlide(index) {
  if (!canEdit.value) return
  const [gone] = shared.heroSlides.splice(index, 1)
  if (gone) Object.values(byLang).forEach(item => { delete item.slideText[gone.id] })
  if (slideIndex.value >= shared.heroSlides.length) slideIndex.value = Math.max(0, shared.heroSlides.length - 1)
}

function moveSlide(index, delta) {
  const target = index + delta
  if (!canEdit.value || target < 0 || target >= shared.heroSlides.length) return
  const list = shared.heroSlides
  ;[list[index], list[target]] = [list[target], list[index]]
  slideIndex.value = target
}

/**
 * 当前这张幻灯片在当前语言下的那一格文案。
 *
 * ⚠ 补空格子这件事放在 watch 里，不放在下面那个 computed 里：
 *   computed 里写它自己读的那个响应式对象，是一条能跑但迟早咬人的路
 *   （求值有副作用、依赖自己）。watch 在渲染前跑，效果一样而且说得清。
 */
function ensureSlideText() {
  const entry = shared.heroSlides[slideIndex.value]
  if (!entry) return
  for (const item of Object.values(byLang)) {
    if (!item.slideText[entry.id]) item.slideText[entry.id] = { title: '', subtitle: '', alt: '' }
  }
}
watch([slideIndex, lang, () => shared.heroSlides.length], ensureSlideText, { immediate: true })

const slideText = computed(() => {
  const entry = shared.heroSlides[slideIndex.value]
  return (entry && form.value.slideText[entry.id]) || { title: '', subtitle: '', alt: '' }
})

/* ---- 原生拖放。dragKey 是正在拖的那一条，只用来把它画淡一点 ---- */
const dragKey = ref('')
function onDragStart(key) {
  if (!canEdit.value) return
  dragKey.value = key
}
function onDrop(overKey) {
  const from = shared.order.indexOf(dragKey.value)
  const to = shared.order.indexOf(overKey)
  dragKey.value = ''
  if (!canEdit.value || from < 0 || to < 0 || from === to) return
  // 先摘出来再插进去，不是两两交换——交换会让「拖过好几格」变成跳格
  const [moved] = shared.order.splice(from, 1)
  shared.order.splice(to, 0, moved)
}

/**
 * 某一块在某个语言下算不算「有内容」。
 *
 * 判据要和 station-site/app/pages/index.vue 里那三个 v-if 一字对应：
 * 只有这三块是「没内容就整块不渲染」的，其余四块空着也会出现
 * （Banner 有内置默认、商品来自商品库、地图组件自带地点、询盘表单字段是写死的）。
 */
function blockHasContent(key) {
  // 条目现在在共享层：有几条特色是所有语言共用的，和当前在编哪种语言无关
  if (key === 'bento') return shared.highlights.length > 0
  if (key === 'caps') return shared.capabilities.length > 0
  if (key === 'trust') return shared.trustItems.length > 0
  if (key === 'partners') return shared.partnerItems.length > 0 || shared.partnerVideos.length > 0
  if (key === 'faq') return shared.faqItems.length > 0
  // 评价只填了分数、一条评价都没录时，首页上仍然有那个评分头
  if (key === 'reviews') return shared.reviewItems.length > 0 || !!shared.reviewScore.trim()
  /*
   * 「关于我们」是唯一一块「有没有」还取决于文案的：它没有共享的条目，
   * 空标题空正文就整块不渲染。
   *
   * ⚠ 判据要带上默认语言：后端按项退回默认语言，所以「日语没填」不等于
   *   「日语页面上没有」——英文填了的话那一块照样出现，显示的是英文。
   *   只看当前语言的话这里会标成「空 · 不显示」，而商家会照着这句话
   *   去排查一个不存在的问题。
   */
  if (key === 'about') {
    const filled = item => !!(item?.about?.title?.trim() || item?.about?.content?.trim())
    return filled(byLang[lang.value]) || filled(byLang[defaultLang.value])
  }
  return true
}

/**
 * 这一块在这个语言下**有没有自己的文字**（没有就是「还没译」）。
 *
 * 和 blockHasContent 是两件事：那个问的是「首页上出不出现这一块」（多半由共享层
 * 决定），这个问的是「切到这个语言，这一块的字是不是它自己的」。
 * 拆开之后左边那一列才说得准——2026-09-11 之前条目还在语言层，两件事是一件事。
 */
function blockTranslated(key, item) {
  if (!item) return false
  const bag = name => Object.keys(item[name] || {}).length > 0
  const some = (...values) => values.some(v => String(v || '').trim())
  if (key === 'hero') return bag('slideText') || some(item.hero.title, item.hero.subtitle, item.hero.eyebrow, item.hero.btn1, item.hero.btn2)
  if (key === 'bento') return bag('highlightText') || some(item.bento.title, item.bento.lead, item.bento.statLabel)
  if (key === 'caps') return bag('capText') || some(item.caps.title, item.caps.lead)
  if (key === 'about') return bag('aboutStatText') || some(item.about.title, item.about.content)
  if (key === 'reach') return bag('reachStatText') || bag('reachDestText') || some(item.reach.title, item.reach.lead, item.reach.hqName)
  if (key === 'products') return some(item.products.title, item.products.lead)
  if (key === 'inquiry') return some(item.inquiry.title, item.inquiry.lead)
  if (key === 'trust') return bag('trustText')
  if (key === 'reviews') return bag('reviewText') || some(item.reviews?.title, item.reviews?.lead)
  if (key === 'partners') return bag('partnerText') || bag('partnerVideoText') || some(item.partners?.title, item.partners?.lead)
  if (key === 'faq') return bag('faqText') || some(item.faq?.title, item.faq?.lead)
  return false
}

/**
 * 左边那一列每条后面那句小字。
 *
 * 告诉商家这一块**在首页上现在是什么状态**：关掉了、退回默认语言了、
 * 还是真的没内容所以不会出现。「填了没效果」是这一页最容易发生的困惑，
 * 能在列表上直接看见就不用去猜。
 *
 * ⚠ 不能只看当前语言。后端是**按项退回默认语言**的（2026-09-10 起，
 *   见 SiteOpenService#siteContent），所以「日语没填」不等于「日语页面上没有」——
 *   默认语言填了的话，那一块照样会出现，只是显示的是默认语言的文案。
 *   这里标成「空 · 不显示」就是骗人，而商家会照着这句话去排查一个不存在的问题。
 */
function blockBadge(key) {
  if (isOff(key)) return st('decorate.status.hidden', '已隐藏')
  if (!blockHasContent(key)) return st('decorate.status.empty', '空 · 不显示')
  /*
   * 这一块在首页上有，但当前语言一个字都没填 —— 页面上显示的是默认语言那套字。
   * ⚠ 版式、图片、条数不算「填了」：那些是共享的，所有语言本来就一样，
   *   拿它们来判断的话，日语一个字没译也会显示成「已填」。
   */
  if (lang.value !== defaultLang.value
      && !blockTranslated(key, byLang[lang.value])
      && blockTranslated(key, byLang[defaultLang.value])) {
    return st('decorate.summary.usingDefaultLang', '用 {defaultLang} 的文案', { defaultLang: defaultLang.value })
  }
  return ''
}


/**
 * 左边那一条的第二行小字：这一块**现在装着什么**。
 *
 * 写的是当前状态（几条、几张图），不是版式名——版式在这一版里每块只有一种，
 * 摆一个选不了的「错落 bento」只会让人去找那个并不存在的开关。
 */
function blockSub(key) {
  const item = byLang[lang.value] || emptyLangForm()
  if (key === 'hero') {
    if (shared.heroSlides.length > 1) return st('decorate.summary.slideCount', '{n} 张轮播', { n: shared.heroSlides.length })
    const first = shared.heroSlides[0]
    if (first?.video) return st('decorate.field.backgroundVideo', '背景视频')
    return first?.image ? st('decorate.summary.bgImage', '单张背景图') : st('decorate.summary.bgSolid', '纯色背景')
  }
  if (key === 'bento') return shared.highlights.length ? st('decorate.summary.highlightCount', '{n} 条特色', { n: shared.highlights.length }) : st('decorate.summary.noItems', '还没有条目')
  if (key === 'products') return st('decorate.summary.productLimit', '显示 {productLimit} 个', { productLimit: shared.productLimit })
  if (key === 'caps') return shared.capabilities.length ? st('decorate.summary.capabilityCount', '{n} 条能力', { n: shared.capabilities.length }) : st('decorate.summary.noItems', '还没有条目')
  if (key === 'about') return shared.aboutImage ? st('decorate.summary.aboutWithImage', '配图 + 正文') : st('decorate.about.solo', '只有文字卡')
  if (key === 'reach') {
    const n = shared.reach.destinations.length
    return n ? st('decorate.summary.hqPlusDestinations', '总部 + {n} 个目的地', { n }) : st('decorate.summary.reachDefault', '用组件自带的地点')
  }
  if (key === 'inquiry') return st('decorate.summary.inquiryFixed', '字段固定，不用配')
  if (key === 'trust') return shared.trustItems.length ? st('decorate.summary.trustCount', '{n} 条承诺', { n: shared.trustItems.length }) : st('decorate.summary.noItems', '还没有条目')
  if (key === 'reviews') {
    const parts = []
    if (shared.reviewScore.trim()) parts.push(st('decorate.summary.reviewScore', '{reviewScore} 分', { reviewScore: shared.reviewScore }))
    if (shared.reviewItems.length) parts.push(st('decorate.summary.reviewCount', '{n} 条评价', { n: shared.reviewItems.length }))
    return parts.join(' · ') || st('decorate.summary.noContent', '还没有内容')
  }
  if (key === 'partners') return (shared.partnerItems.length || shared.partnerVideos.length)
    ? st('decorate.summary.partnerCount', '{n} 个合作方', { n: shared.partnerItems.length }) + (shared.partnerVideos.length ? ' · ' + st('decorate.summary.videoCount', '{n} 条视频', { n: shared.partnerVideos.length }) : '')
    : st('decorate.summary.noItems', '还没有条目')
  if (key === 'faq') return shared.faqItems.length ? st('decorate.summary.faqCount', '{n} 条问答', { n: shared.faqItems.length }) : st('decorate.summary.noItems', '还没有条目')
  return ''
}

/** 这一块的标签是不是「这个语言没填、页面上是别的语言的字」那一种（黄的那种） */
function isFallback(key) {
  return !isOff(key) && lang.value !== defaultLang.value
    && blockHasContent(key)
    && !blockTranslated(key, byLang[lang.value])
    && blockTranslated(key, byLang[defaultLang.value])
}

/** 任意一种语言里填了这一项没有。全站设置的「用默认」要按所有语言算，不是只看当前这种 */
function anyLangHas(pick) {
  return Object.values(byLang).some(item => {
    const value = pick(item)
    return typeof value === 'string' ? !!value.trim() : !!value
  })
}

/**
 * 全站设置那四条后面的「用默认」。
 *
 * 含义是**一个字都没填，前台用的是系统内置的那份**。所以判据要跨语言 +
 * 带上共享层：电话只填在共享层、公司名只填了英语，都算填了。
 */
const GLOBAL_FILLED = {
  brand: () => !!String(shared.logo || '').trim() || anyLangHas(i => i.companyName || i.slogan),
  // 导航条目在共享层，和语言无关
  nav: () => shared.nav.length > 0,
  contact: () => !!String(shared.contact.phone || shared.contact.email || shared.contact.whatsapp || '').trim()
    || anyLangHas(i => i.contact.address),
  seo: () => anyLangHas(i => i.seoTitle || i.seoDescription)
}
function globalBadge(key) {
  return GLOBAL_FILLED[key] && !GLOBAL_FILLED[key]() ? st('decorate.status.usingDefault', '用默认') : ''
}

/** 这种语言一个字都没填（共享层不算——那本来就是所有语言共用的） */
function langEmpty(code) {
  return !prune(byLang[code] || {})
}

/* ==========================================================================
   那几张「一行一条」的小表
   --------------------------------------------------------------------------
   列定义交给 ItemRows 渲染，五处共用一个组件：特色、能力、数字亮点、
   地图数字、目的地、导航。中间这一列只有 392px 宽，el-table 在这个宽度下
   光是内边距就吃掉一半，所以用的是紧凑的格子版（见 ItemRows.vue）。
   ========================================================================== */

/*
 * 列定义。`lang: true` 的那几格分语言（写进语言层的文案袋子），其余的所有语言共用。
 * ⚠ 这就是这一版的分界线：图标、网址、数值是「页面长什么样」，只存一份；
 *   标题、说明、名称是「页面上写着什么」，每种语言各存一份。
 */

/** 「图标 + 标题 + 说明」那种三列表。两处在用，只有图标的占位符不同 */
const itemCols = iconPlaceholder => [
  { k: 'icon', label: st('decorate.field.icon', '图标'), width: 'w-ic', ph: iconPlaceholder, max: 4 },
  { k: 'title', label: st('decorate.field.aboutTitle', '标题'), ph: 'Fast Delivery', max: 100, lang: true },
  { k: 'desc', label: st('decorate.field.desc', '说明'), ph: 'Ready stock, ships within 7 days', max: 300, lang: true }
]
const BENTO_COLS = computed(() => itemCols('★'))
const CAPS_COLS = computed(() => itemCols('◆'))
const STAT_COLS = computed(() => [
  { k: 'value', label: st('decorate.field.value', '数值'), width: 'w-num', ph: '15+', max: 40 },
  { k: 'label', label: st('decorate.field.desc', '说明'), ph: 'Years of Export', max: 80, lang: true }
])
const NAV_COLS = computed(() => [
  { k: 'title', label: st('decorate.field.title', '名称'), ph: 'Products', max: 100, lang: true },
  { k: 'url', label: st('decorate.field.contactAddress', '地址'), ph: '/products', max: 200 }
])
/* 承诺横条和能力是同一种形状（图标 + 标题 + 说明），只有图标的占位符不同 */
const TRUST_COLS = computed(() => itemCols('✓'))
/*
 * 顾客评价。星级在共享层——同一条评价在七种语言下不会是不同的星数；
 * 名字也分语言，因为「张伟 / Wei Zhang」在中英文站上确实写法不同。
 */
const REVIEW_COLS = computed(() => [
  { k: 'stars', label: st('decorate.field.stars', '星'), width: 'w-ic', ph: '5', max: 1 },
  { k: 'name', label: st('decorate.field.author', '署名'), ph: 'Marie B.', max: 60, lang: true },
  { k: 'role', label: st('decorate.field.role', '身份'), ph: 'Marathon runner', max: 80, lang: true },
  { k: 'body', label: st('decorate.field.reviewBody', '评价正文'), ph: st('decorate.ph.reviewBody', '把这条评价原话填进来'), max: 400, lang: true, multiline: true }
])
const PARTNER_COLS = computed(() => [
  { k: 'logo', label: st('decorate.field.logoUrl', 'Logo 地址'), ph: '/files/deco/team.png', max: 300 },
  { k: 'name', label: st('decorate.field.title', '名称'), ph: 'Stade Français', max: 80, lang: true },
  { k: 'role', label: st('decorate.field.projectRelation', '项目 / 关系'), ph: st('decorate.ph.projectRelation', 'Rugby · 官方合作'), max: 80, lang: true }
])
/* 常见问题整条都是文字，共享层只剩一个 id（有几条、什么顺序） */
const FAQ_COLS = computed(() => [
  { k: 'q', label: st('decorate.field.question', '问题'), ph: st('decorate.ph.question', '多久能发货？'), max: 200, lang: true },
  { k: 'a', label: st('decorate.field.answer', '答案'), ph: st('decorate.ph.answer', '写完整一点——这一段就是搜索引擎收录的内容。空一行分段。'), max: 1200, lang: true, multiline: true }
])
const DEST_COLS = computed(() => [
  { k: 'name', label: st('decorate.field.cityName', '城市名'), ph: 'Lyon', max: 60, lang: true },
  { k: 'lon', label: st('decorate.field.longitude', '经度'), width: 'w-num', ph: '4.83', max: 20 },
  { k: 'lat', label: st('decorate.field.latitude', '纬度'), width: 'w-num', ph: '45.76', max: 20 }
])

/**
 * 加一行 / 删一行。
 *
 * ⚠ 这两件事必须在这儿做，不能留在 ItemRows 里：加一行要发一个 id（分语言的文案
 *   靠它对上），删一行要把**所有语言**里那一条的文案一起删掉。那个组件只看得见
 *   当前这一种语言。
 *
 * ⚠ 删的时候连袋子一起删，是因为留着的话商家看不见它、改不了它，而它会一直躺在
 *   contentJson 里，下次新加一条正好撞上同一个 id 时又冒出来（幻灯片那边同理，
 *   见 removeSlide）。
 */
function addRow(list, fields) {
  if (!canEdit.value) return
  const row = { id: newRowId() }
  fields.forEach(f => { row[f] = '' })
  list.push(row)
}

/** @param bagKey 这张表的文案袋子。整张表都不分语言（目的地）时传 null */
function removeRow(list, bagKey, index) {
  if (!canEdit.value) return
  const [gone] = list.splice(index, 1)
  if (gone && bagKey) Object.values(byLang).forEach(item => { delete item[bagKey]?.[gone.id] })
}

/** 上移 / 下移一行。文案按 id 存，顺序只在共享层，所以挪共享层就够了 */
function moveRow(list, index, dir) {
  if (!canEdit.value) return
  const to = index + dir
  if (to < 0 || to >= list.length) return
  const [row] = list.splice(index, 1)
  list.splice(to, 0, row)
}

/** 合作方视频的说明：写进当前语言的 partnerVideoText[id].caption（它不走 item-rows，自己写） */
function setPartnerVideoCaption(id, value) {
  const bag = form.value.partnerVideoText || (form.value.partnerVideoText = {})
  const row = bag[id] || (bag[id] = {})
  row.caption = value
}

/** 各语言的编辑态：{ en: {...}, ja: {...} }。一次建齐，模板里就不用到处判空 */
const byLang = reactive({})

/** 当前语言那一份。模板里所有 form.xxx 都指向它 */
const form = computed(() => byLang[lang.value] || emptyLangForm())

/* ==========================================================================
   「留空 = 用默认」——那个默认到底是什么字
   --------------------------------------------------------------------------
   以前每个格子的提示都是「留空用站点名称」「留空则使用默认导航：Home / Products /
   About Us / Contact」这种。前者商家看不到到底是哪几个字，后者更糟——
   **那四个词是抄在前端的**，后端哪天改了默认导航，这里还写着老的。
   导航那一格的注释本来就写着「不预填默认值，预填了将来两边就对不上」，
   而提示文案自己违反了这条。

   现在改成**问后端要**：拿一份空的装修内容去算一遍，算出来的就是「什么都不填时
   前台显示什么」。同一个预览接口，同一段合并代码，不可能和线上不一致。
   ========================================================================== */

/** 按语言缓存：{ en: {...前台会显示的那份...} } */
const defaultsByLang = reactive({})
const defaults = computed(() => defaultsByLang[lang.value] || null)

function loadDefaults(code) {
  if (!props.previewer || !code || defaultsByLang[code]) return
  /*
   * 传 '{}' 而不是空串：空串在 axios 那儿可能被当成「没有 body」，
   * 连 Content-Type 都不发，而后端那个接口是 consumes=text/plain，会回 415。
   * '{}' 一样表示「什么都没配」（后端解析出一个空对象，全走内置默认）。
   */
  props.previewer('{}', code)
    .then(res => { defaultsByLang[code] = res.data || {} })
    .catch(err => console.error('[装修] 取内置默认失败，提示里就不显示具体默认值了：', err))
}
watch(lang, loadDefaults, { immediate: true })

/** 公司名没填时前台显示什么（后端说了算，一般是站点档案里的站点名称） */
const defaultCompanyName = computed(() => defaults.value?.companyName || '')
/** 一条导航都不配时前台那几条 */
const defaultNav = computed(() => (Array.isArray(defaults.value?.nav) ? defaults.value.nav : []))

/** 顶栏和页脚上实际会显示的公司名：填了用填的，没填用默认 */
const effectiveCompanyName = computed(() =>
  form.value.companyName?.trim() || defaultCompanyName.value)

const phCompanyName = computed(() =>
  defaultCompanyName.value ? st('decorate.ph.companyNameDefault', '留空则显示「{defaultCompanyName}」', { defaultCompanyName: defaultCompanyName.value }) : st('decorate.hint.blankUsesSiteName', '留空用站点名称'))
const phHeroTitle = computed(() =>
  effectiveCompanyName.value ? st('decorate.ph.heroTitleDefault', '留空则显示公司名「{effectiveCompanyName}」', { effectiveCompanyName: effectiveCompanyName.value }) : st('decorate.hint.blankUsesCompanyName', '留空用公司名'))
const phSeoTitle = computed(() =>
  effectiveCompanyName.value ? st('decorate.ph.seoTitleDefault', '留空则用公司名「{effectiveCompanyName}」', { effectiveCompanyName: effectiveCompanyName.value }) : st('decorate.hint.blankUsesCompanyName', '留空用公司名'))
/** 副标题和 SEO 描述都退回标语，而标语是**当前这份草稿里**的，不用问后端 */
const phFromSlogan = computed(() => {
  const slogan = form.value.slogan?.trim()
  return slogan ? st('decorate.ph.sloganDefault', '留空则用标语「{slogan}」', { slogan }) : st('decorate.hint.blankUsesSlogan', '留空则用标语（标语现在也是空的，那这一行就不显示）')
})

/**
 * 一种语言的表单。**这里只有文字**——图片、网址、图标、数值、顺序都在 shared 里。
 *
 * ⚠ 那几个 `xxxText` 是「按条目 id 存的文案袋子」，而且一律放在**顶层**，
 *   哪怕它配的那个列表挂在别的键底下（幻灯片的袋子叫 `slideText`，不叫
 *   `hero.slideText`）。原因在后端：合并只合一层，顶层的袋子能和默认语言那份
 *   合一次，所以「这一条没译」能按条退回默认语言；塞进 `hero` 底下的话，
 *   整个袋子会被当前语言那份整个换掉——译了三张里的一张，另外两张的字就没了。
 *   见 SiteOpenService#compose 的第 2 条。
 */
function emptyLangForm() {
  return {
    companyName: '', slogan: '', seoTitle: '', seoDescription: '',
    /*
     * 背景图不在这里了，挪到 shared.heroSlides —— 一张厂房图不会因为语言不同就换一张。
     * 留在这里的是文案：眉标、整组共用的大标题/副标题、两个按钮上的字。
     */
    hero: { eyebrow: '', title: '', subtitle: '', btn1: '', btn2: '' },
    /**
     * 每张幻灯片自己的标题/副标题/图片说明，按幻灯片 id 存。
     *
     * `alt` 是图片说明（HTML 的 alt）：读屏念它，图挂了显示它，
     * 而且**图片搜索只认它**。分语言是必须的——一句英文说明摆在日语站上没有意义。
     */
    slideText: {},
    /** 每条导航在这个语言下叫什么，按导航 id 存：`{ n1: { title } }` */
    navText: {},
    /** 三栏特色每条的标题和说明，按条目 id 存：`{ h1: { title, desc } }` */
    highlightText: {},
    /** 能力每条的标题和说明，按条目 id 存 */
    capText: {},
    /** 关于我们那排数字每条的说明，按条目 id 存：`{ t1: { label } }` */
    aboutStatText: {},
    /** 地图那排数字每条的说明，按条目 id 存 */
    reachStatText: {},
    /** 地图上每个目的地的城市名，按条目 id 存（2026-09-22 起分语言）：`{ r1: { name } }` */
    reachDestText: {},
    /** 承诺横条每条的标题和说明，按条目 id 存 */
    trustText: {},
    /** 每条评价的署名、身份、正文，按条目 id 存 */
    reviewText: {},
    /** 每个合作方的名称和项目，按条目 id 存 */
    partnerText: {},
    /** 合作方视频每条的说明，按条目 id 存：`{ v1: { caption } }` */
    partnerVideoText: {},
    /** 每条问答的问和答，按条目 id 存 */
    faqText: {},
    about: { title: '', content: '', imageAlt: '' },
    // 电话/邮箱/WhatsApp 也挪走了：号码不分语言。地址留下，各语言写法确实不同
    contact: { address: '' },
    // 地图的地点在 shared.reach（点位不分语言），这里只有区块标题和副标题
    reach: { title: '', lead: '', hqName: '' },
    /*
     * 四块的「区块标题 / 一句话说明」。留空就用前台内置的那套
     * （英文 What We Offer、中文「我们能做什么」……），所以这里不预填默认值——
     * 预填就等于把前台那份文案抄一份到管理台来，前台改了这边还是老的。
     *
     * bento.statLabel 是粉色数字卡下面那行字，数字本身在 shared.bentoStatValue。
     * ⚠ 键名不能也叫 stat：共享层和语言层在同一个父键底下重名的话，
     *   后端合并时语言层那个会把共享层整个换掉，数字就没了。
     */
    bento: { title: '', lead: '', statLabel: '' },
    products: { title: '', lead: '' },
    caps: { title: '', lead: '' },
    inquiry: { title: '', lead: '' },
    // 承诺横条没有区块标题：它是贴着 Banner 的一条细横幅，顶一个标题上去反而把它变成一屏
    reviews: { title: '', lead: '' },
    partners: { title: '', lead: '' },
    faq: { title: '', lead: '' }
  }
}

const LANG_NAMES = computed(() => ({
  en: st('decorate.lang.en', '英语'), zh: st('decorate.lang.zh', '中文'), 'zh-cn': st('decorate.lang.zhCn', '简体中文'), 'zh-tw': st('decorate.lang.zhTw', '繁体中文'), ja: st('decorate.lang.ja', '日语'), ko: st('decorate.lang.ko', '韩语'),
  ru: st('decorate.lang.ru', '俄语'), es: st('decorate.lang.es', '西班牙语'), fr: st('decorate.lang.fr', '法语'), de: st('decorate.lang.de', '德语'), pt: st('decorate.lang.pt', '葡萄牙语'), it: st('decorate.lang.it', '意大利语'),
  ar: st('decorate.lang.ar', '阿拉伯语'), vi: st('decorate.lang.vi', '越南语'), th: st('decorate.lang.th', '泰语'), id: st('decorate.lang.id', '印尼语'), tr: st('decorate.lang.tr', '土耳其语'), nl: st('decorate.lang.nl', '荷兰语')
}))
function langLabel(code) {
  if (!code) return ''
  return LANG_NAMES.value[code] ? st('decorate.lang.labelFormat', '{name}（{code}）', { name: LANG_NAMES.value[code], code }) : code
}

/* ==========================================================================
   未保存的改动
   -------------------------------------------------------------------------
   这一页能填的东西太多了（七个区块 × 七种语言），而在 2026-09-13 之前，
   填了一屏然后点一下左边的菜单，填的东西**一声不响地全没了**。

   判据是「现在的表单」和「上一次载入/保存时的表单」逐字节比。不用「有没有敲过键盘」
   那种脏标记：那种做法下，改一个字再改回来也算脏，商家会被一个并不存在的改动拦住，
   几次之后就学会了无脑点「不保存」——那比没有这道提示更糟。

   ⚠ 比的是 shared / byLang / passthrough 三样，不是 composePayload() 的结果。
     composePayload 会剪空值、会跑 buildReach（地点填一半时它会弹提示），
     拿它来做「有没有改动」的判断，等于每敲一个字就跑一遍校验。
   ========================================================================== */

/** 上一次「干净」时的样子。空串表示还没载入完，那时一律不算脏 */
const cleanSnapshot = ref('')

function stateSnapshot() {
  return JSON.stringify({ shared, byLang, passthrough })
}

/** 载入完、保存完、发布完之后都要调一次 */
function markClean() {
  cleanSnapshot.value = stateSnapshot()
}

const isDirty = computed(() =>
  !!cleanSnapshot.value && stateSnapshot() !== cleanSnapshot.value)

/**
 * 关标签页/刷新时的拦截。
 *
 * ⚠ 浏览器不让自定义这句话，写什么都显示它自己那句「您所做的更改可能未保存」。
 *   所以这里不写文案，只表态「要拦」。Chrome 认 returnValue，别的认 preventDefault，
 *   两个都给。
 */
function onBeforeUnload(event) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

/**
 * 站内跳走（点左边菜单去别的页面）时的拦截。
 *
 * <p>和上面那个是两件事：beforeunload 只管「离开这个网页」，管理台内部换页面
 * 根本不触发它——而后者恰恰是更常发生的那一种。</p>
 */
onBeforeRouteLeave(() => {
  if (!isDirty.value) return true
  return ElMessageBox.confirm(
    st('decorate.leave.unsavedConfirm', '装修还有没保存的改动，离开这一页就没了。确定要离开吗？'),
    st('decorate.common.tip', '提示'), { type: 'warning', confirmButtonText: st('decorate.leave.leaveWithoutSaving', '离开，不保存'), cancelButtonText: st('decorate.leave.stayAndEdit', '留下继续改') })
    .then(() => true)
    .catch(() => false)
})

/**
 * 装修内容跟着站点档案一起进来，站点换了或重新读了就重铺一次表单。
 *
 * ⚠ 这个 watch 是 `immediate` 的，也就是**在 setup 里当场跑一次**。所以它用到的
 *   markClean / cleanSnapshot 必须写在它<b>前面</b>——写在后面的话是
 *   「Cannot access 'cleanSnapshot' before initialization」，
 *   而且整页白屏。2026-09-13 犯过一次，被 scripts/verify/decorate-roundtrip.mjs 挡下来了。
 */
watch(() => site.value.siteCode, () => { reload(); markClean() }, { immediate: true })

/* 站点语言被改过之后（比如平台方在 ERP 里加了一种语言），补出新语言的空表单 */
watch(langs, list => {
  list.forEach(code => { if (!byLang[code]) byLang[code] = emptyLangForm() })
  if (!list.includes(lang.value)) lang.value = list[0] || ''
})

function reload() {
  Object.keys(byLang).forEach(k => delete byLang[k])
  Object.keys(passthrough).forEach(k => delete passthrough[k])
  Object.assign(shared, {
    logo: '', heroSlides: [], heroBtn1Url: '', heroBtn2Url: '', aboutImage: '',
    nav: [], highlights: [], capabilities: [], aboutStats: [], reachStats: [], bentoStatValue: '',
    trustItems: [], reviewItems: [], partnerItems: [], partnerVideos: [], faqItems: [], reviewScore: '', reviewCount: '',
    contact: { phone: '', email: '', whatsapp: '' },
    reach: { hqName: '', hqLon: '', hqLat: '', destinations: [] },
    order: [...BLOCK_KEYS], off: [], productLimit: 6, capsAccent: 1,
    variants: {
      hero: 'left', heroHeight: 'tall', bento: 'bento', products: 'grid', caps: 'auto',
      about: 'stagger', reach: 'map', inquiry: 'soft',
      trust: 'bar', reviews: 'cards', partners: 'named', faq: 'accordion'
    }
  })
  slideIndex.value = 0
  langs.value.forEach(code => { byLang[code] = emptyLangForm() })
  lang.value = defaultLang.value

  /*
   * ⚠ 有草稿就载草稿，没有才载已发布那份。
   *
   * 顺序反过来（或者只认 contentJson）的表现最难查：商家存了草稿、关掉页面、
   * 第二天回来看到的是上一次发布的内容，会以为草稿丢了，于是重填一遍再点保存——
   * 那一下正好把真正的草稿盖掉。
   *
   * 空串也算草稿（「清空了但还没发布」那一种），所以判的是 `== null` 而不是真假值。
   */
  const draft = site.value.draftJson
  const raw = draft == null || draft === '' ? site.value.contentJson : draft
  if (!raw) return
  let data
  try {
    data = JSON.parse(raw)
  } catch {
    // 库里那段 JSON 坏了。清空表单让人重填，比把界面卡住强——反正保存时会整份覆盖
    ElMessage.warning(st('decorate.msg.storedContentInvalidJson', '这个站点已存的装修内容不是合法 JSON，已按空白载入，保存后会覆盖掉它'))
    return
  }
  if (!data || typeof data !== 'object') return

  // v1 没有 i18n 键，整份就是一种语言的内容
  const isV1 = !(data.i18n && typeof data.i18n === 'object')
  const byLangRaw = isV1 ? { [defaultLang.value]: data } : data.i18n

  /*
   * 不认识的顶层键先收好，保存时原样写回去。见 passthrough 的注释。
   *
   * ⚠ v1 不能收：那份数据的顶层就是内容本身（companyName、nav、highlights…），
   *   全都「不认识」。收了的话，这些文案会同时出现在共享层和语言层里，
   *   而且从此赖在共享层不走——语言层能盖住它，页面上看不出来，
   *   但库里那份 JSON 会带着一份永远改不掉的旧文案。
   */
  if (!isV1) {
    Object.entries(data).forEach(([key, value]) => {
      if (!KNOWN_ROOT_KEYS.includes(key)) passthrough[key] = value
    })
  }

  /*
   * ⚠ 先铺共享层，再铺各语言。
   *
   * 顺序不能反：老数据里导航、特色这些整条都在语言层，fillShared 要把它们捞上来
   * 并且**当场发 id**，而 fillLangForm 正是靠这些 id 把老数组里的文字按下标
   * 放进新袋子里的。反过来的话，那一步拿不到 id，七种语言的文案会全部丢掉。
   */
  fillShared(data, byLangRaw)

  Object.entries(byLangRaw).forEach(([code, value]) => {
    const key = String(code).toLowerCase()
    // 站点已经不开这种语言了，但内容还在库里：也载进来，免得保存时被静默丢掉
    if (!byLang[key]) byLang[key] = emptyLangForm()
    fillLangForm(byLang[key], value)
  })

  seedReachNames()
  ensureSlideText()
}

/**
 * 铺共享层。
 *
 * 麻烦的是**老数据里这些字段还在语言层**（v2 只有 logo 能共享，别的都在各语言下）。
 * 所以每一项都是「共享层有就用共享层的，没有就从语言层捞一个上来」。
 *
 * 捞的顺序是默认语言优先、其次任意一个填了的：这些东西本来就不分语言，
 * 几种语言填得不一样的话，默认语言那份最可能是对的。
 *
 * 捞上来之后语言层那份就不再写回去了（save 只写共享层），所以**商家下次一保存，
 * 老数据就自动变成新结构**——不需要为这件事跑 SQL 迁移。
 * 在他保存之前，后端的逐键合并让老数据照常显示，见 SiteOpenService#siteContent。
 */
function fillShared(root, byLangRaw) {
  const fromLangs = pick => {
    const codes = [defaultLang.value, ...Object.keys(byLangRaw)]
    for (const code of codes) {
      const value = pick(byLangRaw[String(code).toLowerCase()] || byLangRaw[code] || {})
      if (typeof value === 'string' && value.trim()) return value
    }
    return ''
  }

  shared.logo = root.logo || ''
  /*
   * Banner 幻灯片。四种来源，从新到旧：
   *   1. `hero.slides` —— 现在的存法，`[{id, image}]`，每张能有自己的文案；
   *   2. `hero.images` —— 2026-09-10 那版，只有一串地址；
   *   3. `hero.image`  —— v3 早期的单图；
   *   4. 语言层里的 `hero.image` —— v2 老数据，每种语言各存了一张。
   * 都归一成幻灯片数组，保存时只写这一种。
   *
   * ⚠ 从 2、3、4 升上来的那几张**当场生成 id**。生成之后语言层里当然没有
   *   对应的文案，于是它们都退回整组共用的那句大标题——和升级之前看到的一模一样。
   */
  const slides = Array.isArray(root.hero?.slides) ? root.hero.slides : []
  shared.heroSlides = slides
    .filter(s => s && typeof s === 'object')
    .map(s => ({
      id: String(s.id || newSlideId()),
      image: typeof s.image === 'string' ? s.image : '',
      // 2026-09-21 起一张幻灯片可以配视频。老数据里没有这个键，读出来是空串 =
      // 还是一张静态图，行为一个字不变
      video: typeof s.video === 'string' ? s.video : ''
    }))
  if (!shared.heroSlides.length) {
    const heroList = Array.isArray(root.hero?.images) ? root.hero.images : []
    const usable = heroList.filter(u => typeof u === 'string' && u.trim())
    const single = root.hero?.image || fromLangs(d => d?.hero?.image)
    const images = usable.length ? usable : (single ? [single] : [])
    shared.heroSlides = images.map(image => ({ id: newSlideId(), image, video: '' }))
  }
  slideIndex.value = 0
  // 两个按钮的地址。老数据里没有这两个键，读出来是空串 = 用内置目的地，行为不变
  shared.heroBtn1Url = typeof root.hero?.btn1Url === 'string' ? root.hero.btn1Url : ''
  shared.heroBtn2Url = typeof root.hero?.btn2Url === 'string' ? root.hero.btn2Url : ''
  shared.aboutImage = root.about?.image || fromLangs(d => d?.about?.image)
  shared.contact = {
    phone: root.contact?.phone || fromLangs(d => d?.contact?.phone),
    email: root.contact?.email || fromLangs(d => d?.contact?.email),
    whatsapp: root.contact?.whatsapp || fromLangs(d => d?.contact?.whatsapp)
  }

  /*
   * 那五张列表。共享层有就用共享层的，没有就按下标从各语言层捞上来（见 fillRows）。
   * 捞上来之后语言层那几个数组不会再写回去，商家下次一保存就自动变成新结构。
   */
  const langs = langLayers(byLangRaw)
  shared.nav = fillRows(root.nav, langs, d => d?.nav, ['url'])
  shared.highlights = fillRows(root.highlights, langs, d => d?.highlights, ['icon'])
  shared.capabilities = fillRows(root.capabilities, langs, d => d?.capabilities, ['icon'])
  shared.aboutStats = fillRows(root.about?.stats, langs, d => d?.about?.stats, ['value'])
  shared.reachStats = fillRows(root.reach?.stats, langs, d => d?.reach?.stats, ['value'])
  const noLegacy = () => null
  shared.trustItems = fillRows(root.trust?.items, langs, noLegacy, ['icon'])
  shared.partnerItems = fillRows(root.partners?.items, langs, noLegacy, ['logo'])
  shared.partnerVideos = fillRows(root.partners?.videos, langs, noLegacy, ['video', 'poster'])
  shared.faqItems = fillRows(root.faq?.items, langs, noLegacy, [])
  /*
   * 评价的星级不能走 fillRows：那个帮手只认字符串字段（`typeof === 'string'`），
   * 而星级在 JSON 里是数字，会被它读成空串——表现是「存了 5 星，重新打开变空」。
   */
  shared.reviewItems = (Array.isArray(root.reviews?.items) ? root.reviews.items : [])
    .filter(item => item && typeof item === 'object')
    .map(item => ({
      id: String(item.id || newRowId()),
      stars: item.stars === undefined || item.stars === null ? '' : String(item.stars)
    }))
  shared.reviewScore = String(root.reviews?.score ?? '')
  shared.reviewCount = String(root.reviews?.count ?? '')
  // 粉色数字卡的数字。老数据里整格都在语言层（bento.stat.value）
  shared.bentoStatValue = String(root.bento?.statValue || fromLangs(d => d?.bento?.stat?.value) || '')

  /*
   * 顺序、开关、每块的小设置。
   *
   * 这三样从来只在共享层，没有老数据要捞——它们是 v3 才有的。
   * ⚠ order 一定要过 normalizeOrder：库里那份可能是老的（少一个区块）
   *   或者被人手改坏了，直接用会让左边那一列少几条，而少掉的那几块
   *   保存时就真的没了。
   */
  shared.order = normalizeOrder(root.order)
  shared.off = (Array.isArray(root.off) ? root.off : []).filter(k => BLOCK_KEYS.includes(k))
  const limit = Number(root.blocks?.products?.limit)
  shared.productLimit = [3, 6, 9].includes(limit) ? limit : 6
  const accent = Number(root.blocks?.caps?.accent)
  shared.capsAccent = [-1, 0, 1, 2].includes(accent) ? accent : 1

  /*
   * 版式。库里那个值可能是老的、或者被人手改坏了，认不出来就退回第一种——
   * 第一种就是「和以前一样」，所以坏数据的后果是页面照旧，不是版式乱掉。
   */
  const pickVariant = (key, value) => {
    const list = BLOCK_VARIANTS.value[key] || []
    return list.some(v => v[0] === value) ? value : list[0][0]
  }
  const has = (list, value) => list.some(v => v[0] === value)
  shared.variants = {
    hero: has(HERO_ALIGNS.value, root.blocks?.hero?.align) ? root.blocks.hero.align : 'left',
    heroHeight: has(HERO_HEIGHTS.value, root.blocks?.hero?.height) ? root.blocks.hero.height : 'tall',
    bento: pickVariant('bento', root.blocks?.bento?.variant),
    products: pickVariant('products', root.blocks?.products?.variant),
    caps: pickVariant('caps', root.blocks?.caps?.variant),
    about: pickVariant('about', root.blocks?.about?.variant),
    reach: pickVariant('reach', root.blocks?.reach?.variant),
    inquiry: pickVariant('inquiry', root.blocks?.inquiry?.variant),
    trust: pickVariant('trust', root.blocks?.trust?.variant),
    reviews: pickVariant('reviews', root.blocks?.reviews?.variant),
    partners: pickVariant('partners', root.blocks?.partners?.variant),
    faq: pickVariant('faq', root.blocks?.faq?.variant)
  }

  // 地图：共享层独有，没有老数据要捞（这一块以前在管理台上根本配不了）
  const hq = root.reach?.hq
  shared.reach = {
    hqName: hq?.name || '',
    hqLon: numText(hq?.coords?.[0]),
    hqLat: numText(hq?.coords?.[1]),
    destinations: (Array.isArray(root.reach?.destinations) ? root.reach.destinations : [])
      // id 是 2026-09-22 起才有的（城市名分语言要靠它对上袋子）；老数据没有就发一个
      .map(d => ({ id: d?.id || newRowId(), name: d?.name || '', lon: numText(d?.coords?.[0]), lat: numText(d?.coords?.[1]) }))
  }
}

/** 经纬度进输入框：0 是合法值（本初子午线、赤道），不能用 `|| ''` 判 */
function numText(value) {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : ''
}

/** 各语言那几份原始数据，**默认语言排在最前**。老数据里哪种语言最可能是对的就是它 */
function langLayers(byLangRaw) {
  const seen = new Set()
  const out = []
  for (const code of [defaultLang.value, ...Object.keys(byLangRaw)]) {
    const key = String(code).toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    const data = byLangRaw[key] || byLangRaw[code]
    if (data && typeof data === 'object') out.push(data)
  }
  return out
}

/**
 * 铺一张「条目在共享层、文案在语言层」的列表。
 *
 * @param rootList 共享层里那份（新结构）。有就直接用它
 * @param langs    各语言那几份原始数据，默认语言在最前（langLayers）
 * @param legacyOf 从某种语言里取出老数组（那时候整条都在语言层）
 * @param fields   条目上**不分语言**的那几个字段：导航是 ['url']，特色是 ['icon']
 *
 * ## 老数据怎么升上来
 *
 * 每种语言各有一份完整数组。条数按**最长的那种**算——短的那种只是没译完，
 * 不该让它决定这个站有几条导航；不分语言的那几个字段按「默认语言优先、
 * 其次任意一种填了的」取。文案不在这里处理，见 fillLangForm 的 textBag：
 * 它按下标把老数组里的文字放进新袋子，靠的就是这里发的 id。
 *
 * ⚠ 这一段的几个帮手**必须写成函数声明，不能写成 const 箭头函数**：
 * 上面那个 watch 带 immediate，setup 阶段就会同步跑一次 reload()，而 reload 用到它们。
 * 写成 const 的话它们还在暂时性死区里，直接抛 ReferenceError——错误发生在 setup 期间，
 * Vue 会把整棵子树连同外层的管理台外壳一起卸载掉，表现为「点导航整个页面没了」。
 */
function fillRows(rootList, langs, legacyOf, fields) {
  const rows = []
  if (Array.isArray(rootList)) {
    for (const item of rootList) {
      if (!item || typeof item !== 'object') continue
      const row = { id: String(item.id || newRowId()) }
      fields.forEach(f => { row[f] = typeof item[f] === 'string' ? item[f] : '' })
      rows.push(row)
    }
  }
  if (rows.length) return rows

  const legacy = langs.map(legacyOf).filter(Array.isArray)
  const count = legacy.reduce((n, list) => Math.max(n, list.length), 0)
  for (let i = 0; i < count; i++) {
    const row = { id: newRowId() }
    fields.forEach(f => {
      const hit = legacy
        .map(list => list[i])
        .find(item => item && typeof item[f] === 'string' && item[f].trim())
      row[f] = hit ? hit[f] : ''
    })
    rows.push(row)
  }
  return rows
}

/**
 * 把一份装修对象铺进某种语言的表单。
 *
 * ⚠ 逐字段取，不能再 Object.assign 整个对象：老数据的 `hero` 里带着 image、
 * `contact` 里带着 phone/email/whatsapp，而那几项现在归共享层管（见 fillShared）。
 * 整个 assign 会把它们又塞回语言表单里，保存时就会两边各写一份，
 * 而语言层那份优先级更高——商家改共享层那格会「改了没反应」。
 */
function fillLangForm(target, data) {
  if (!data || typeof data !== 'object') return
  Object.assign(target, {
    companyName: data.companyName || '', slogan: data.slogan || '',
    seoTitle: data.seoTitle || '', seoDescription: data.seoDescription || ''
  })
  target.hero = {
    eyebrow: data.hero?.eyebrow || '',
    title: data.hero?.title || '',
    subtitle: data.hero?.subtitle || '',
    btn1: data.hero?.btn1 || '',
    btn2: data.hero?.btn2 || ''
  }
  for (const key of ['bento', 'products', 'caps', 'inquiry', 'reviews', 'partners', 'faq']) {
    target[key] = { title: data[key]?.title || '', lead: data[key]?.lead || '' }
  }
  // 粉色数字卡下面那行字。数字在 shared.bentoStatValue；老数据里两格都在 stat 里
  target.bento.statLabel = data.bento?.statLabel || data.bento?.stat?.label || ''
  target.about = { title: data.about?.title || '', content: data.about?.content || '', imageAlt: data.about?.imageAlt || '' }
  target.contact = { address: data.contact?.address || '' }
  target.reach = { title: data.reach?.title || '', lead: data.reach?.lead || '', hqName: data.reach?.hqName || '' }

  /*
   * 那几个文案袋子。⚠ 幻灯片的袋子在**顶层**（slideText），不在 hero 底下——
   * 理由见 emptyLangForm 上面那段。老数据里它是 hero.slideText，两处都认。
   */
  target.slideText = textBag(data.slideText || data.hero?.slideText, shared.heroSlides, null, ['title', 'subtitle', 'alt'])
  target.navText = textBag(data.navText, shared.nav, data.nav, ['title'])
  target.highlightText = textBag(data.highlightText, shared.highlights, data.highlights, ['title', 'desc'])
  target.capText = textBag(data.capText, shared.capabilities, data.capabilities, ['title', 'desc'])
  target.aboutStatText = textBag(data.aboutStatText, shared.aboutStats, data.about?.stats, ['label'])
  target.reachStatText = textBag(data.reachStatText, shared.reachStats, data.reach?.stats, ['label'])
  target.reachDestText = textBag(data.reachDestText, shared.reach.destinations, null, ['name'])
  // 2026-09-21 那四块从一开始就是新结构，没有「整条在语言层」的老数据要按下标补
  target.trustText = textBag(data.trustText, shared.trustItems, null, ['title', 'desc'])
  target.reviewText = textBag(data.reviewText, shared.reviewItems, null, ['name', 'role', 'body'])
  target.partnerText = textBag(data.partnerText, shared.partnerItems, null, ['name', 'role'])
  target.partnerVideoText = textBag(data.partnerVideoText, shared.partnerVideos, null, ['caption'])
  target.faqText = textBag(data.faqText, shared.faqItems, null, ['q', 'a'])
}

/**
 * 铺一个文案袋子：`{ 条目id: { 字段: 文字 } }`。
 *
 * 新结构（袋子）优先，**老结构按下标补**——老数据里整条都在语言层，
 * 那时候没有 id，只能靠「第几条」和共享层那份对上（共享层那份正是刚才
 * 从这些老数组升上来的，见 fillRows，所以下标一定对得上）。
 *
 * ⚠ 空的那几条不进袋子：袋子里有没有东西，就是「这个语言译了没有」的判据。
 * ⚠ 袋子里有、但共享层里已经没有的 id（商家删了那一条）在这里被丢掉，
 *   不然它会一直躺在库里，下次正好撞上同一个 id 时又冒出来。
 */
function textBag(bag, rows, legacy, fields) {
  const out = {}
  const source = bag && typeof bag === 'object' ? bag : {}
  rows.forEach((row, i) => {
    const own = source[row.id]
    const old = Array.isArray(legacy) ? legacy[i] : null
    const entry = {}
    let filled = false
    for (const field of fields) {
      const value = own?.[field] ?? old?.[field]
      entry[field] = typeof value === 'string' ? value : ''
      if (entry[field].trim()) filled = true
    }
    if (filled) out[row.id] = entry
  })
  return out
}


/**
 * 存之前把空值全部剪掉。
 *
 * 这不是洁癖，是必须的：开放接口那边是先铺一份内置默认、再让这段 JSON 覆盖
 * （见 StationOpenServiceImpl.siteInfo）。存进一个 nav: []，站点导航就整条没了；
 * 存进 slogan: ''，页脚标语被一个空串盖掉。剪干净了，「没填」才真的等于「用默认」。
 */
function prune(value) {
  if (Array.isArray(value)) {
    const list = value.map(prune).filter(v => v !== undefined)
    return list.length ? list : undefined
  }
  if (value && typeof value === 'object') {
    const out = {}
    Object.entries(value).forEach(([k, v]) => {
      const pruned = prune(v)
      if (pruned !== undefined) out[k] = pruned
    })
    return Object.keys(out).length ? out : undefined
  }
  const text = typeof value === 'string' ? value.trim() : value
  return text === '' || text === null || text === undefined ? undefined : text
}

/**
 * 把地图那一块拼成前台要的形状：`{ hq: {name, coords:[经度, 纬度]}, destinations: [...] }`。
 *
 * 形状是站点那边**早就定好等着的**（station-site/app/composables/useSite.ts 的 reach），
 * 字段名和顺序都不能改：coords 是 [经度, 纬度]，不是 [纬度, 经度]。
 *
 * @param quiet 只算不提示。预览要用它——商家正打着经纬度，
 *              每敲一个字弹一次「填了一半」是灾难。这种时候整块当没配处理，
 *              预览里显示组件自带的默认地点，等他填完自然就对了。
 * @return 拼好的对象；整块都没填返回 undefined；填了一半返回 false
 */
function buildReach(quiet) {
  const rows = [
    { label: st('decorate.reach.hq', '总部'), name: hqNameForSave(), lon: shared.reach.hqLon, lat: shared.reach.hqLat },
    ...shared.reach.destinations.map((d, i) => ({ label: st('decorate.reach.destinationNth', '第 {v} 个目的地', { v: i + 1 }), ...d, name: destNameForSave(d) }))
  ]

  for (const row of rows) {
    const filled = [row.name, row.lon, row.lat].filter(v => String(v ?? '').trim())
    if (!filled.length) continue          // 整行空着 = 没填这一条，跳过
    /*
     * 三样缺一不可。少了经纬度就画不出点，少了名字图例上是一个空标签——
     * 两种都是「地图上少一个地方」，而这件事在管理台里看不出来。
     */
    if (filled.length < 3) {
      if (!quiet) warnReach(st('decorate.msg.destinationIncomplete', '{label}：名称、经度、纬度要么都填，要么都留空', { label: row.label }))
      return false
    }
    const lon = Number(row.lon)
    const lat = Number(row.lat)
    if (!Number.isFinite(lon) || !Number.isFinite(lat)) {
      if (!quiet) warnReach(st('decorate.msg.destinationNotNumeric', '{label}：经纬度得是数字', { label: row.label }))
      return false
    }
    // 范围反了（经纬度写颠倒是最常见的一种）不会报错，只会把点画到海里，所以这里挡住
    if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
      if (!quiet) warnReach(st('decorate.msg.destinationOutOfRange', '{label}：经度要在 -180~180、纬度要在 -90~90 之间。是不是把两个填反了？', { label: row.label }))
      return false
    }
    row.coords = [lon, lat]
  }

  // 共享层存的 name 是默认语言那份（见 destNameForSave）：老前端、以及没译的语言都靠它
  const place = row => ({ ...(row.id ? { id: row.id } : {}), name: String(row.name).trim(), coords: row.coords })
  const hqRow = rows[0]
  const destRows = rows.slice(1).filter(r => r.coords)

  if (!hqRow.coords && !destRows.length) return undefined
  const out = {}
  if (hqRow.coords) out.hq = place(hqRow)
  if (destRows.length) out.destinations = destRows.map(place)
  return out
}

function warnReach(message) {
  ElMessage.warning(message)
  sel.value = 'reach'
}

/** 这一条在**任意一种语言**下填了字没有。条目是共享的，所以判据也得跨语言 */
function anyLangText(bagKey, id) {
  return Object.values(byLang).some(item =>
    Object.values(item[bagKey]?.[id] || {}).some(v => String(v || '').trim()))
}

/** 这一条在任意一种语言下的那个字段，用来在提示里指认是哪一条 */
/**
 * 地图地名存进共享层的那一份：默认语言那格优先，没有就沿用共享层原来的（老数据只有那一份）。
 * 共享层这一份是所有没译语言的兜底，也是老版前台唯一读的那一份。
 */
function destNameForSave(row) {
  return String(byLang[defaultLang.value]?.reachDestText?.[row.id]?.name || row.name || '').trim()
}
function hqNameForSave() {
  return String(byLang[defaultLang.value]?.reach?.hqName || shared.reach.hqName || '').trim()
}
/**
 * 老数据的地名只在共享层有一份：载入时把它预填进默认语言那一页，
 * 否则商家打开会看到地图上明明有名字、表单里的格子却是空的。
 */
function seedReachNames() {
  const item = byLang[defaultLang.value]
  if (!item) return
  if (!String(item.reach?.hqName || '').trim() && shared.reach.hqName) item.reach.hqName = shared.reach.hqName
  for (const row of shared.reach.destinations) {
    if (!row.id || !row.name) continue
    const bag = item.reachDestText[row.id] || (item.reachDestText[row.id] = {})
    if (!String(bag.name || '').trim()) bag.name = row.name
  }
}

function someLangText(bagKey, id, field) {
  for (const code of [lang.value, defaultLang.value, ...Object.keys(byLang)]) {
    const value = byLang[code]?.[bagKey]?.[id]?.[field]
    if (String(value || '').trim()) return value
  }
  return ''
}

/**
 * 存之前清一张列表：一行在**所有语言下都没有文字**、共享那几格也不算数的，丢掉。
 * 各语言袋子里那些已经没有对应条目的孤儿一起丢——留着的话它会一直躺在库里，
 * 商家看不见也改不了，下次新加一条正好撞上同一个 id 时又冒出来。
 *
 * ⚠ 判据跨语言：某一行只填了日语，英文站上也要留着（它在共享层，本来就是所有
 *   语言共用的一条）。按单种语言清的话，在日语页签上点一次保存会把只填了
 *   英文的那几条全删掉。
 */
function tidyRows(listKey, bagKey, keepIfShared) {
  const kept = shared[listKey].filter(row =>
    (keepIfShared ? keepIfShared(row) : false) || anyLangText(bagKey, row.id))
  shared[listKey] = kept
  const ids = new Set(kept.map(row => row.id))
  for (const item of Object.values(byLang)) {
    const bag = item[bagKey]
    if (!bag) continue
    Object.keys(bag).forEach(id => { if (!ids.has(id)) delete bag[id] })
  }
}

function save() {
  /*
   * 导航的地址是共享的（所有语言指向同一个页面），名字分语言。
   * 名字填了、地址空着的话站上就是一条点了没反应的链接，挡在这里。
   */
  const badNav = shared.nav.find(row => !row.url?.trim() && anyLangText('navText', row.id))
  if (badNav) {
    ElMessage.warning(st('decorate.msg.navMissingUrl', '导航「{v}」还没填地址', { v: someLangText('navText', badNav.id, 'title') }))
    sel.value = 'nav'
    return
  }

  /*
   * 空行站上会渲染出一块空白，存之前先丢掉。条目在共享层，所以只清一遍。
   *
   * ⚠ 导航只要填了地址就留着，哪怕一种语言的名字都还没填——新的填法是
   *   「地址填一次，名字各语言各填一遍」，先填地址再去挨个语言填名字是常事，
   *   在这中间点一次保存就把那几条删掉的话，人会以为是自己填错了。
   *   留着的后果只是站上多一条空白链接，下面那句提示会说清楚。
   */
  tidyRows('nav', 'navText', row => !!row.url?.trim())
  const nameless = shared.nav.filter(row => !someLangText('navText', row.id, 'title')).length
  if (nameless) {
    ElMessage.warning(st('decorate.msg.navMissingName', '有 {nameless} 条导航还没填名字，站点上那几条会是看不见的空链接', { nameless }))
  }
  tidyRows('highlights', 'highlightText')
  tidyRows('capabilities', 'capText')
  tidyRows('aboutStats', 'aboutStatText', row => !!row.value?.trim())
  tidyRows('reachStats', 'reachStatText', row => !!row.value?.trim())
  tidyRows('trustItems', 'trustText')
  tidyRows('reviewItems', 'reviewText')
  // 只有 logo、名字一个语言都还没填的合作方留着：「只排 logo」那个版式下它是完整的一条
  tidyRows('partnerItems', 'partnerText', row => !!row.logo?.trim())
  // 视频没填地址的那条前台不会显示，只填了说明也一样——留着它只会让人以为配好了
  tidyRows('partnerVideos', 'partnerVideoText', row => !!row.video?.trim())
  tidyRows('faqItems', 'faqText')
  // 幻灯片不清：一张图都没配、一句话也没写的那一屏是「纯色底纹 + 什么都不写」，
  // 是一种合法的配法（见 composePayload 里那段注释）

  const reach = buildReach(false)
  if (reach === false) return   // 地点填了一半，buildReach 已经提示过并跳过去了

  const payload = composePayload(reach)

  saving.value = true
  return props.saver(JSON.stringify(payload))
    .then(() => {
      ElMessage.success(st('decorate.msg.draftSaved', '草稿已保存。点「发布到网站」才会改变访客看到的内容'))
      // ⚠ 先 markClean 再 reloadSite：后者是异步的，等它回来再标，
      //   中间这一段时间里页面还认为自己是脏的，商家点左边菜单会被拦一下
      markClean()
      reloadSite()
      return true
    })
    .finally(() => saving.value = false)
}

/* ==========================================================================
   草稿 / 发布
   -------------------------------------------------------------------------
   2026-09-13 之前这一页是「保存即上线」：商家在**线上站**上改到一半，
   访客就已经看见半成品了。现在分两步，保存只写草稿，发布才见客。

   已发布那份一直完整地躺在库里，所以「放弃草稿」顺带就是这一版的回滚。
   ========================================================================== */

const publishing = ref(false)

/** 库里有没有未发布的草稿。由后端下发（SiteInfo#isHasDraft） */
const hasDraft = computed(() => !!site.value.hasDraft)

/**
 * 发布。
 *
 * <p>当前有没保存的改动时**先存再发**——商家的心理模型是「我填完了，发出去」，
 * 让他先点一次保存再点一次发布是多余的一步，而漏点那一步的后果是
 * 「发布了，但发的是上一版」，他要等到看网站时才发现。</p>
 */
function publish() {
  if (!canEdit.value || !props.publisher) return
  ElMessageBox.confirm(
    isDirty.value
      ? st('decorate.publish.confirmSaveAndPublish', '会先保存当前改动，然后发布到网站。访客刷新后立刻看到新内容。')
      : st('decorate.publish.confirmPublishDraft', '把草稿发布到网站？访客刷新后立刻看到新内容。'),
    st('decorate.publish.action', '发布'), { type: 'info', confirmButtonText: st('decorate.publish.action', '发布'), cancelButtonText: st('decorate.publish.thinkAgain', '再想想') })
    .then(() => {
      publishing.value = true
      // save() 里有校验（地点填一半会中断并提示），它中断时返回 undefined，
      // 这里就不该再往下发布——发出去的会是库里那份旧草稿
      const ready = isDirty.value ? save() : Promise.resolve(true)
      return Promise.resolve(ready).then(ok => {
        if (!ok) return null
        return props.publisher()
      })
    })
    .then(result => {
      if (result === null || result === undefined) return
      ElMessage.success(st('decorate.msg.published', '已发布，刷新站点即可看到'))
      markClean()
      reloadSite()
    })
    .catch(() => {})
    .finally(() => publishing.value = false)
}

/** 放弃草稿，回到上一次发布的那份 */
function discard() {
  if (!canEdit.value || !props.discarder) return
  ElMessageBox.confirm(
    st('decorate.discard.confirmRevert', '放弃草稿，回到网站上现在显示的那一版？草稿里改的东西会全部丢掉，找不回来。'),
    st('decorate.common.tip', '提示'), { type: 'warning', confirmButtonText: st('decorate.btn.discardDraft', '放弃草稿'), cancelButtonText: st('decorate.common.cancel', '取消') })
    .then(() => props.discarder())
    .then(() => {
      ElMessage.success(st('decorate.msg.draftDiscarded', '已放弃草稿'))
      /*
       * ⚠ 这里必须等 reloadSite() 拉回新档案才能重填表单：reload() 读的是
       *   site.value.draftJson，而那一列刚被后端清空，本地这份还是旧的。
       *   不等的话表单会把刚放弃的草稿又载一遍，看起来像「放弃没生效」。
       */
      return Promise.resolve(reloadSite())
    })
    .then(() => { reload(); markClean() })
    .catch(() => {})
}

/* ==========================================================================
   机器翻译：拿默认语言那份，填当前语言的空格子
   -------------------------------------------------------------------------
   七种语言意味着每个文案格子要填七遍。v4 已经把「结构」从这件事里摘出去了
   （导航条数、图片、版式只填一次），剩下的纯文字仍然是七遍。这里把第一遍自动填上。

   ⚠ 只填**空**格子，不覆盖已经填了的。商家自己斟酌过的那一句，
     不能被一次误点的机翻冲掉——那种损失他多半要过很久才发现。
   ========================================================================== */

const translating = ref(false)

/**
 * 把一份语言表单里的文字全都摘出来，记住每一条在哪儿。
 *
 * 走的是「递归到字符串为止」，不是列一张字段清单：清单会漏
 * ——以后往 emptyLangForm 里加一个格子，忘了同步清单的表现是
 * 「别的都翻了，就这一格永远空着」，而且没人会想到来这里找原因。
 */
function collectTexts(node, path = [], out = []) {
  for (const [key, value] of Object.entries(node || {})) {
    if (typeof value === 'string') out.push({ path: [...path, key], value })
    else if (value && typeof value === 'object') collectTexts(value, [...path, key], out)
  }
  return out
}

function getByPath(root, path) {
  return path.reduce((node, key) => (node == null ? undefined : node[key]), root)
}

/** 沿途缺的对象补出来：目标语言里可能还没有这条幻灯片/导航的文案袋子 */
function setByPath(root, path, value) {
  let node = root
  for (let i = 0; i < path.length - 1; i++) {
    if (!node[path[i]] || typeof node[path[i]] !== 'object') node[path[i]] = {}
    node = node[path[i]]
  }
  node[path[path.length - 1]] = value
}

function autoTranslate() {
  if (!props.translator || !canEdit.value || lang.value === defaultLang.value) return
  const source = byLang[defaultLang.value]
  const target = form.value
  if (!source || !target) return

  /*
   * 要翻的：默认语言填了、当前语言空着的那些格子。
   *
   * ⚠ 按**路径**对，不是按下标对。文案袋子的键是条目 id，两种语言的袋子里
   *   键的顺序可能不一样（一种语言先填了第 3 条），按下标对会把译文放错条目。
   */
  const todo = collectTexts(source)
    .filter(item => item.value.trim())
    .filter(item => !String(getByPath(target, item.path) || '').trim())

  if (!todo.length) {
    ElMessage.info(st('decorate.msg.nothingToTranslate', '{v}的格子都填过了，没有需要翻译的内容', { v: langLabel(lang.value) }))
    return
  }

  ElMessageBox.confirm(
    h('div', null, [
      h('p', null, st('decorate.msg.translateConfirm', '把{v}的 {n} 段文字翻成{v2}，填进空着的格子。', { v: langLabel(defaultLang.value), n: todo.length, v2: langLabel(lang.value) })),
      h('p', { style: 'color:var(--el-text-color-secondary);font-size:13px' },
        st('decorate.translate.filledFieldsUntouched', '已经填了字的格子不动。翻译只填进表单，要点「保存草稿」才存得下来。')),
      h('p', { style: 'color:var(--el-color-warning);font-size:13px' },
        st('decorate.translate.machineWarning', '⚠ 机器翻译是初稿，发布前请自己过一遍——品牌名、型号、行业术语是机翻最容易弄错的地方。'))
    ]),
    st('decorate.translate.title', '机器翻译'), { type: 'info', confirmButtonText: st('decorate.translate.action', '翻译'), cancelButtonText: st('decorate.common.cancel', '取消') })
    .then(() => {
      translating.value = true
      return props.translator(defaultLang.value, lang.value, todo.map(item => item.value))
    })
    .then(response => {
      const texts = response?.data?.texts
      if (!Array.isArray(texts) || texts.length !== todo.length) {
        // 条数对不上就整批不填。按短的那个填，会把译文错位放进别的格子
        ElMessage.error(st('decorate.translate.countMismatch', '翻译返回的条数和请求不一致，这一次没有填入任何内容'))
        return
      }
      let filled = 0
      texts.forEach((text, i) => {
        if (typeof text === 'string' && text.trim()) {
          setByPath(target, todo[i].path, text)
          filled++
        }
      })
      ElMessage.success(st('decorate.msg.translationFilled', '已填入 {filled} 段译文，记得核对后点「保存草稿」', { filled }))
    })
    .catch(() => {})
    .finally(() => translating.value = false)
}

/**
 * 把当前表单拼成一份完整的 contentJson 对象。
 *
 * ⚠ 保存和预览**都走这一个函数**。分成两份写的话，以后加一个字段只改了保存那边，
 * 预览里就永远看不到它——而那种不一致的表现是「预览是对的、发出去少一块」，
 * 或者反过来，两种都难查。
 *
 * @param reach buildReach 的结果。传 undefined 表示这块不写
 */
function composePayload(reach) {
  const i18n = {}
  for (const [code, item] of Object.entries(byLang)) {
    const pruned = prune(item)
    if (pruned) i18n[code] = pruned
  }

  /*
   * v3。先摊开 passthrough，再写我们自己管的键——
   * 顺序反过来的话，不认识的键会盖掉刚算好的值。
   */
  const payload = { v: 4, ...passthrough }
  const logo = shared.logo?.trim()
  if (logo) payload.logo = logo

  /*
   * 那几张列表的**条目**。文案不在这儿，在上面的 i18n 里（按 id 存）。
   *
   * ⚠ 一条都没有时**整个键不写**，不能写成 `nav: []`：接口那边是先铺一份内置默认
   *   再让这份 JSON 覆盖，写一个空数组等于把内置导航整条盖没了。prune 正好干这件事
   *   （空数组返回 undefined），所以这里全都过一遍 prune。
   *
   * ⚠ 条目上那几个字段可能是空的（图标没填），但 id 不是空的，所以 prune 剪不掉整条——
   *   这正是要的：有几条是共享层说了算，文字填没填是另一回事。
   */
  const rows = (list, fields) => prune(list.map(row => {
    const out = { id: row.id }
    fields.forEach(f => { out[f] = row[f] })
    return out
  }))
  const navRows = rows(shared.nav, ['url'])
  if (navRows) payload.nav = navRows
  const highlightRows = rows(shared.highlights, ['icon'])
  if (highlightRows) payload.highlights = highlightRows
  const capRows = rows(shared.capabilities, ['icon'])
  if (capRows) payload.capabilities = capRows

  /*
   * ⚠ 只写 `slides`，不再写 `images` / `image`。
   *
   * 几个键指着同一件事的话，改了一个忘了另一个就是「首页背景图改不掉」。
   * 前台三种都认（`index.vue` 的 heroSlides），老数据下次一保存就自动归位。
   * og:image 用的是第一张，见前台那边。
   *
   * ⚠ 一张图都没填的幻灯片也要留着：它是「只有文字、背景用纯色底纹」那一种，
   *   所以这里不能用 prune 把 `{id}` 剪掉——prune 剪的是空字符串，
   *   而 id 不是空的，正好留得住。
   */
  const heroShared = prune({
    slides: shared.heroSlides.map(s => ({ id: s.id, image: s.image, video: s.video })),
    // 留空就整个键不写（prune 剪掉空串），前台那边看不到这个键就用内置目的地
    btn1Url: shared.heroBtn1Url,
    btn2Url: shared.heroBtn2Url
  })
  if (heroShared) payload.hero = heroShared
  const aboutShared = prune({
    image: shared.aboutImage,
    stats: rows(shared.aboutStats, ['value'])
  })
  if (aboutShared) payload.about = aboutShared
  const bentoShared = prune({ statValue: shared.bentoStatValue })
  if (bentoShared) payload.bento = bentoShared
  const trustShared = prune({ items: rows(shared.trustItems, ['icon']) })
  if (trustShared) payload.trust = trustShared
  /*
   * 星级转成数字，转不出来的整格不写（前台的 starRow 对「没填」按满分处理）。
   * ⚠ 不能把输入框里的字原样存：库里躺着 `"5"` 的话前台照样能画出来，
   *   但下一个读这份 JSON 的人会以为星级本来就是文本，照着写一个 `"五星"` 进去。
   */
  const reviewRows = prune(shared.reviewItems.map(row => {
    const n = Number(row.stars)
    const ok = String(row.stars ?? '').trim() !== '' && Number.isInteger(n) && n >= 0 && n <= 5
    return ok ? { id: row.id, stars: n } : { id: row.id }
  }))
  const reviewsShared = prune({
    score: shared.reviewScore, count: shared.reviewCount, items: reviewRows
  })
  if (reviewsShared) payload.reviews = reviewsShared
  const partnersShared = prune({
    items: rows(shared.partnerItems, ['logo']),
    videos: rows(shared.partnerVideos, ['video', 'poster'])
  })
  if (partnersShared) payload.partners = partnersShared
  // 问答在共享层只有 id。rows 的 fields 传空数组，出来就是 `[{id}]`
  const faqShared = prune({ items: rows(shared.faqItems, []) })
  if (faqShared) payload.faq = faqShared
  const contactShared = prune(shared.contact)
  if (contactShared) payload.contact = contactShared
  /*
   * 地图。点位（总部和目的地）是 buildReach 拼好的，这里再把那排数字的**数值**
   * 放进去——说明分语言，在 i18n 里（reachStatText）。
   */
  const reachShared = prune({ ...(reach || {}), stats: rows(shared.reachStats, ['value']) })
  if (reachShared) payload.reach = reachShared

  /*
   * 顺序、开关、每块的小设置。
   *
   * ⚠ order **总是写**，哪怕和默认顺序一模一样。
   *   这三样不能走 prune：prune 会把空数组剪掉，而 `off: []` 的意思是
   *   「一块都没关」，被剪掉之后和「没配过」长得一样——本来也确实等价，
   *   但顺序不行：默认顺序也是一种明确的选择，商家拖回原样之后
   *   下次打开必须还是原样。
   */
  payload.order = [...shared.order]
  if (shared.off.length) payload.off = [...shared.off]
  /*
   * 每块的小设置。只写**和默认不一样**的那些：全写一遍的话，以后改默认值
   * 就改不动了——每个站的 JSON 里都躺着一份当年的默认值。
   */
  const blocks = {}
  const put = (key, field, value, dflt) => {
    if (value === dflt) return
    blocks[key] = blocks[key] || {}
    blocks[key][field] = value
  }
  put('products', 'limit', shared.productLimit, 6)
  put('caps', 'accent', shared.capsAccent, 1)
  put('hero', 'align', shared.variants.hero, 'left')
  put('hero', 'height', shared.variants.heroHeight, 'tall')
  for (const key of Object.keys(BLOCK_VARIANTS.value)) {
    put(key, 'variant', shared.variants[key], BLOCK_VARIANTS.value[key][0][0])
  }
  if (Object.keys(blocks).length) payload.blocks = blocks

  if (Object.keys(i18n).length) payload.i18n = i18n
  return payload
}

/* ==========================================================================
   预览：右边那一列里装的就是真前台
   --------------------------------------------------------------------------
   里面是一个 iframe，装的是**站点自己**（`<主域>/?__preview=1`），不是照着真站
   在管理台里抄的一份版式。抄一份的话，真站改一次样式这边就旧一次，
   而且旧了没人会发现——station-shared/README.md 记过同一类事故。

   推过去的是**后端算好的数据**，不是草稿 JSON 本身：合并规则（共享层 →
   默认语言层 → 当前语言层）只有后端那一份，前端重算一遍迟早和线上不一致，
   而症状是「预览里对、发出去不对」，最难查。

   ## ⚠ 这一列要 nginx 放行一次才能用

   主域的 nginx 发 `X-Frame-Options: SAMEORIGIN`，而管理台在 `console.` 子域上，
   跨源内嵌会被浏览器直接拒绝。所以 `deploy/nginx/*` 里给**带 `?__preview=1` 的
   那一次请求**换成 `Content-Security-Policy: frame-ancestors`（见那两个文件里的
   `$station_frame_csp`）。没放行的部署上，这一列会自己发现嵌不进来，
   盖一层说明并让人改用「新窗口」——不会只剩一块空白。

   判据是「一直没收到那边的 ready」：被 X-Frame-Options 拦掉的 iframe
   从外面读不出任何状态，`onload` 照样会触发，contentWindow 也碰不得。
   ========================================================================== */

const PREVIEW_MESSAGE_TYPE = 'station-decorate-preview'

/** 前台首页地址。站点档案里的 domain 可能带协议头，也可能不带 */
const frontUrl = computed(() => {
  const value = String(site.value.domain || '').trim().replace(/\/+$/, '')
  if (!value) return ''
  return /^https?:\/\//i.test(value) ? value : `https://${value}`
})

/**
 * 预览地址。
 *
 * ⚠ 带上语言前缀（`/ja?__preview=1`）。以前另开窗口那版一直开默认语言的首页，
 *   推过去的内容是日语的、页面里的链接却指向不带前缀的地址——
 *   看内容没问题，一点链接就跳回默认语言。前台的语言前缀规则见
 *   station-site/nuxt.config.ts 的 `pages:extend`。
 */
const previewUrl = computed(() => {
  if (!props.previewer || !canEdit.value || !frontUrl.value || !lang.value) return ''
  const prefix = lang.value === defaultLang.value ? '' : `/${lang.value}`
  return prefix ? `${frontUrl.value}${prefix}?__preview=1` : `${frontUrl.value}/?__preview=1`
})

const stageEl = ref(null)
const frameEl = ref(null)
/** 预览里按哪个宽度排版。桌面 1280 / 手机 390，和真站的断点对得上 */
const pvMode = ref('desktop')
const pvZoom = ref(1)
const stageHeight = ref(600)

/**
 * 预览这会儿是什么状态。
 *
 * 'off' 没有预览（没接口 / 没权限 / 没域名，各自有各自的说明）；
 * 'loading' 载入中，还没收到那边的 ready；
 * 'live' 连上了，改一个字它就跟着变；
 * 'blocked' 等了几秒还没动静 —— 多半是被 X-Frame-Options 拦了。
 */
const pvState = ref('off')
const PV_STATE_TEXT = computed(() => ({ off: '', loading: st('decorate.preview.stateLoading', '正在载入…'), live: st('decorate.preview.stateLive', '● 跟着你改'), blocked: st('decorate.preview.stateBlocked', '嵌不进来') }))

/** 等那边喊 ready 的宽限时间。慢一点的机器上首屏 SSR + hydrate 要两三秒 */
const READY_TIMEOUT = 6000
let readyTimer = null
let pushTimer = null

function waitForReady() {
  clearTimeout(readyTimer)
  pvState.value = 'loading'
  readyTimer = setTimeout(() => {
    if (pvState.value !== 'live') pvState.value = 'blocked'
  }, READY_TIMEOUT)
}

/* 地址一变（换语言、站点档案重新载入）就是一次新的载入，重新等 ready */
watch(previewUrl, url => {
  if (!url) { clearTimeout(readyTimer); pvState.value = 'off'; return }
  waitForReady()
}, { immediate: true })

/** 手动重载。加一个用不上的参数，免得地址没变浏览器不重新请求 */
function reloadPreview() {
  if (!frameEl.value || !previewUrl.value) return
  waitForReady()
  frameEl.value.src = `${previewUrl.value}&__r=${Date.now()}`
}

/* ---- 缩放：真站按 1280 排版，这一列放不下就整体缩小 ----
   不是把 iframe 做窄：做窄的话看到的是手机版，而商家想看的是桌面版长什么样 */
function fitPreview() {
  const stage = stageEl.value
  if (!stage) return
  const width = pvMode.value === 'desktop' ? 1280 : 390
  pvZoom.value = Math.min(1, Math.max(0.2, (stage.clientWidth - 36) / width))
  stageHeight.value = Math.max(240, stage.clientHeight - 36)
}
watch(pvMode, fitPreview)

/*
 * iframe 自己要按 1280 那么宽、按「缩放后正好填满这一列」那么高，
 * 然后整体 scale 下去。高度不这么算的话，缩小之后底下会空一大片。
 */
const frameStyle = computed(() => {
  const width = pvMode.value === 'desktop' ? 1280 : 390
  const k = pvZoom.value || 1
  return {
    width: `${width}px`,
    height: `${Math.round(stageHeight.value / k)}px`,
    transform: `scale(${k})`
  }
})

/* ---- 另开一个窗口看：nginx 还没放行时的退路，也是想拖到第二块屏幕上时的办法 ---- */
let previewWin = null

function openPreviewWindow() {
  if (!previewUrl.value) return
  // 已经开着就把它叫到前面来，不要每点一次开一个新窗口
  if (previewWin && !previewWin.closed) {
    previewWin.focus()
    pushPreview()
    return
  }
  /*
   * 窗口名字固定，这样反复点只会复用同一个窗口。
   * ⚠ 必须在点击这个事件里同步调用 window.open——放进 then 里会被拦截器当成弹窗拦掉。
   */
  previewWin = window.open(previewUrl.value, 'station-decorate-preview')
  if (!previewWin) ElMessage.warning(st('decorate.preview.popupBlocked', '浏览器把预览窗口拦住了，允许一下弹出窗口再试'))
  // 不在这里推：那边还在加载，监听器都没装上。等它自己喊 ready（见 onPreviewMessage）
}

/**
 * 那边装好了会喊一声，收到才推得进去。
 *
 * ⚠ 比 event.source，不比 event.origin：认的是「我自己嵌的那个 iframe」和
 *   「我开出来的那个窗口」，别的标签页、别的站点上的页面都冒充不了。
 *   比 origin 反而更松——同一个域下随便哪个页面都能冒充。
 */
function onPreviewMessage(event) {
  if (event.data?.type !== PREVIEW_MESSAGE_TYPE || !event.data.ready) return
  if (frameEl.value && event.source === frameEl.value.contentWindow) {
    clearTimeout(readyTimer)
    pvState.value = 'live'
    pushPreview()
    return
  }
  if (previewWin && event.source === previewWin) pushPreview()
}

/** 把当前草稿算一遍推过去（算的是后端），内嵌的那个和另开的那个都推 */
function pushPreview() {
  if (previewWin && previewWin.closed) previewWin = null
  const targets = []
  if (pvState.value === 'live' && frameEl.value?.contentWindow) targets.push(frameEl.value.contentWindow)
  if (previewWin) targets.push(previewWin)
  if (!targets.length || !props.previewer || !frontUrl.value) return

  const payload = composePayload(buildReach(true) || undefined)
  props.previewer(JSON.stringify(payload), lang.value)
    .then(res => {
      /*
       * targetOrigin 写前台的确切地址，不用 '*'。
       * 这份数据是商家还没发布的内容，不该发给「碰巧在那个窗口里的任何页面」——
       * 比如预览窗口被导航到了别处。
       */
      const origin = new URL(frontUrl.value).origin
      targets.forEach(win => {
        try {
          win.postMessage({ type: PREVIEW_MESSAGE_TYPE, site: res.data }, origin)
        } catch (err) {
          console.error('[装修预览] 推不进去：', err)
        }
      })
    })
    /*
     * 预览失败不弹提示：商家正在填表，一个红条蹦出来只会碍事，而预览本来就是可选的。
     *
     * ⚠ 但**一定要往控制台写一行**。这里原来是空的 `catch(() => {})`，
     *   结果是「预览开着、就是不跟着变」这种问题**一点线索都没有**——
     *   连推送有没有发出去都看不到。空的 catch 比没有 catch 更难查。
     */
    .catch(err => console.error('[装修预览] 推送失败：', err))
}

/*
 * 边填边推。
 *
 * 600ms 防抖：打字时每个键都算一次的话，后端和网络都白忙，而人眼也跟不上。
 * 停手半秒再推，观感上就是「跟着变」。
 */
watch([() => byLang, () => shared, lang], () => {
  clearTimeout(pushTimer)
  pushTimer = setTimeout(pushPreview, 600)
}, { deep: true })

let stageObserver = null

onMounted(() => {
  window.addEventListener('message', onPreviewMessage)
  window.addEventListener('beforeunload', onBeforeUnload)
  fitPreview()
  // 拖窗口、折叠侧边栏都会改这一列的宽度，缩放得跟着重算
  if (window.ResizeObserver && stageEl.value) {
    stageObserver = new ResizeObserver(fitPreview)
    stageObserver.observe(stageEl.value)
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('message', onPreviewMessage)
  window.removeEventListener('beforeunload', onBeforeUnload)
  clearTimeout(pushTimer)
  clearTimeout(readyTimer)
  stageObserver?.disconnect()
  /*
   * ⚠ 离开这一页时**不关**另开的那个预览窗口：那是一个独立的浏览器窗口，
   *   用户可能正把它拖到另一块屏幕上看。替他关掉是越权。
   *   窗口自己会一直显示最后推过去的那一版，直到他关掉或者刷新。
   */
})

function clearAll() {
  ElMessageBox.confirm(
    h('div', null, [
      h('p', null, st('decorate.clear.confirmTitle', '确认清空这个站点的全部装修内容？')),
      h('p', { style: 'color:var(--el-text-color-secondary);font-size:13px' },
        st('decorate.clear.warningAllLanguages', '所有语言的内容都会被清掉，不只是当前这一种。清空之后站点会回到系统内置的默认内容（Banner 用站点名、导航是 Home/Products/About Us/Contact），页面不会空白，但你填过的文案都会没有。')),
      // ⚠ 这句是 2026-09-13 加的，别删：清空现在只落到草稿上，
      //   而人点「全部清空」时心里想的往往是「网站上那份也没了」
      h('p', { style: 'color:var(--el-text-color-secondary);font-size:13px' },
        st('decorate.clear.draftOnlyNotice', '清空的是草稿。网站上现在显示的内容不变，要等你点「发布到网站」才真的清掉。'))
    ]),
    st('decorate.common.tip', '提示'), { type: 'warning', closeOnClickModal: false })
    .then(() => props.saver(''))
    .then(() => {
      ElMessage.success(st('decorate.msg.draftCleared', '草稿已清空。点「发布到网站」后站点才恢复默认内容'))
      /*
       * 整份清掉，包括共享层和 order/off/blocks——「全部清空」就得是全部，
       * 留半份下来会让下一次保存把它们又写回去。
       *
       * ⚠ 这里必须一项不落地列全，不能指望下面那个 reloadSite()：它重新拉的是
       *   站点档案，而 reload() 挂在 `site.siteCode` 上——站点没换，那个 watch
       *   不会触发。2026-09-11 之前这里漏了 order / off / 两个 blocks 设置和
       *   六张列表，清空之后它们还在表单里，下一次保存就原样写回库里。
       */
      Object.keys(byLang).forEach(k => delete byLang[k])
      Object.keys(passthrough).forEach(k => delete passthrough[k])
      langs.value.forEach(code => { byLang[code] = emptyLangForm() })
      Object.assign(shared, {
        logo: '', heroSlides: [], heroBtn1Url: '', heroBtn2Url: '', aboutImage: '',
        nav: [], highlights: [], capabilities: [], aboutStats: [], reachStats: [], bentoStatValue: '',
        trustItems: [], reviewItems: [], partnerItems: [], partnerVideos: [], faqItems: [], reviewScore: '', reviewCount: '',
        contact: { phone: '', email: '', whatsapp: '' },
        reach: { hqName: '', hqLon: '', hqLat: '', destinations: [] },
        order: [...BLOCK_KEYS], off: [], productLimit: 6, capsAccent: 1,
        variants: {
          hero: 'left', heroHeight: 'tall', bento: 'bento', products: 'grid', caps: 'auto',
          about: 'stagger', reach: 'map', inquiry: 'soft',
          trust: 'bar', reviews: 'cards', partners: 'named', faq: 'accordion'
        }
      })
      slideIndex.value = 0
      markClean()
      reloadSite()
    })
    .catch(() => {})
}
</script>

<style scoped>
/*
  ==========================================================================
  版式：占满内容区的三列工作台
  --------------------------------------------------------------------------
  ⚠ 这一页和管理台里别的页不一样：**它自己不滚**。

  外壳的 .console-main 是一个高度确定的 flex 项（AppShell.vue），所以这里
  height:100% 就正好铺满内容区；三列各自 overflow:auto。
  右边那一列是实时预览，要是它跟着整页滚走，一边填一边看就不成立了。
  ========================================================================== */
.deco {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--el-bg-color-page);
}

/* ---------------- 顶上一条：编辑语言 ---------------- */
.deco-top {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.top-lbl {
  flex: none;
  font-size: 13px;
  font-weight: 600;
}
.langbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
/* 翻译按钮。flex: none 不能省——顶上这一条是 flex 容器，
   不写的话它会被语言页签挤扁，按钮上的字折成两行 */
.top-tr { flex: none; margin-left: 8px; }
.top-sp { flex: 1; }
.top-hint {
  /* 窄一点，省下的宽度给语言页签——八种语言的中文名加起来很长，
     提示挤走一个页签的话，那个页签会掉到第二行 */
  flex: 0 1 auto;
  max-width: 330px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.tabbtn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 11px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.tabbtn:hover { border-color: var(--el-color-primary-light-5); }
.tabbtn.on {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
.tabbtn .dflt { font-size: 10px; opacity: .7; }
/* 「这种语言一个字都没填」。不是错误，所以是一个点不是一个感叹号 */
.tabbtn .miss {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-warning);
}
.tabbtn.on .miss { background: #ffd79a; }
.deco-nolang { margin: 12px; width: auto; }

.deco-nop {
  flex: none;
  padding: 9px 14px;
  background: var(--el-color-warning-light-9);
  border-bottom: 1px solid var(--el-color-warning-light-7);
  font-size: 12.5px;
  color: var(--el-color-warning-dark-2);
}

/* ---------------- 三列 ---------------- */
.deco-cols {
  flex: 1;
  display: flex;
  min-height: 0;
}
.col-list {
  flex: none;
  width: 268px;
  overflow: auto;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
}
.col-form {
  flex: none;
  width: 392px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
}
.col-prev {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color);
}

/*
  没有编辑权限时把左中两列整体压一层灰，右边那一列不压——
  看站点长什么样是只读的人也该有的。上面那条黄条说的是为什么。
*/
.locked .col-list,
.locked .col-form { position: relative; }
.locked .col-list::after,
.locked .col-form::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 6;
  background: var(--el-overlay-color-lighter);
  opacity: .35;
  cursor: not-allowed;
}

/* ---------------- 第一列：区块清单 ---------------- */
.grp { padding: 12px 10px 4px; }
.grp-t {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 6px 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
  color: var(--el-text-color-placeholder);
}
.q {
  display: inline-grid;
  place-items: center;
  width: 14px;
  height: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  font-size: 9px;
  letter-spacing: 0;
  cursor: help;
}
.item {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  margin-bottom: 2px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.item:hover { background: var(--el-fill-color-light); }
.item.on {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}
.item.dragging { opacity: .35; }
/* 关掉的那几块画成灰的加删除线：一眼看出首页上现在没有它 */
.item.off .nm b,
.item.off .ic { color: var(--el-text-color-placeholder); text-decoration: line-through; }
.item .hnd {
  flex: none;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  line-height: 1;
  cursor: grab;
}
.item .hnd.hide { visibility: hidden; }
.item .ic {
  flex: none;
  width: 18px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.item .nm {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item .nm b { font-weight: 600; }
/* 第二行是这一块**现在装着什么**（几条、几张图），不是版式名——
   版式在这一版里还只有一种，写一个假的选择只会骗人 */
.item .nm span {
  display: block;
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
}
.tag {
  flex: none;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 10px;
}
/* 「用 xx 的内容」是要提醒的那一种：这个语言没填，页面上是别的语言的字 */
.tag-warn {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
}
/* 两个箭头平时藏着，鼠标移上去或选中时才出现 */
.acts {
  flex: none;
  display: flex;
  gap: 1px;
  opacity: 0;
}
.item:hover .acts,
.item.on .acts { opacity: 1; }
.mv, .del, .eye {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.mv:hover:not(:disabled),
.eye:hover:not(:disabled) { background: var(--el-fill-color); color: var(--el-color-primary); }
.del { color: var(--el-text-color-placeholder); }
.del:hover:not(:disabled) { color: var(--el-color-danger); }
.mv:disabled, .del:disabled, .eye:disabled { opacity: .3; cursor: not-allowed; }
.item .eye { flex: none; color: var(--el-text-color-placeholder); }

/* ---------------- 第二列：表单 ---------------- */
.fhead {
  flex: none;
  padding: 14px 16px 11px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.fhead h2 {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-size: 15px;
}
.fhead-ic { color: var(--el-text-color-secondary); }
.fhead p {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}
.fbody {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px 20px;
}
/* 这一列平时只有 392px，封顶只在窄屏（预览列让位、这一列变宽）时起作用：
   一行文案拉到一千像素宽就不好读了 */
.deco-form, .f, .tip { max-width: 720px; }

/*
  一格的版式是**标签在上、输入框在下**：这一列只有 392px 宽，
  标签摆左边的话输入框只剩两百来像素，而这一页填的多半是整句文案。
  标签后面那个小牌子说的是「这一格是分语言的还是所有语言共用的」——
  v3 之后这件事逐格不同，不标出来就只能靠猜。
*/
.deco-form :deep(.el-form-item) { margin-bottom: 15px; }
.deco-form :deep(.el-form-item__label) {
  height: auto;
  padding: 0 0 5px;
  line-height: 1.4;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.deco-form :deep(.el-form-item__content) { line-height: 1.6; }
.f { display: block; margin-bottom: 15px; }
.f-lbl {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 5px;
  font-size: 12.5px;
  font-weight: 600;
}
.f-lbl em,
.sec em {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
}
/* 共用的那种用绿色：它的含义是「填一次，七种语言都有了」，是个好消息 */
.f-lbl em.sh,
.sec em.sh {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.f-hint {
  flex: 0 0 100%;
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}
/* 填错了的链接：提示语变红，输入框描红边。⚠ 不禁用保存——
   填一半就去点别的格子是常事，拦住保存等于逼人一次填完 */
.f-hint.bad { color: var(--el-color-danger); }
:deep(.is-bad .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}
.f-hint code, .deco-nop code, .pvblock code {
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: Consolas, Monaco, monospace;
}
.mt12 { margin-top: 12px; }
.sec {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 20px 0 12px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}
/* 合作方视频的一条：序号 | 视频 + 封面 + 说明 | 上移下移删除 */
.pv-row {
  display: flex; gap: 8px; align-items: flex-start;
  padding: 10px 0; border-bottom: 1px dashed var(--el-border-color-lighter);
}
.pv-no { flex: none; width: 18px; padding-top: 6px; font-size: 12px; color: var(--el-text-color-secondary); }
.pv-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.pv-ops { flex: none; display: flex; flex-direction: column; }
.pv-ops .el-button + .el-button { margin-left: 0; }
/* 说明块。el-alert 在 392px 宽里太占地方，而这些话每一句都要读 */
.tip {
  margin-top: 14px;
  padding: 9px 11px;
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 7px;
  background: var(--el-color-primary-light-9);
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

/* ---- 两个下拉并排（Banner 的对齐 + 高度） ---- */
.two-up {
  display: flex;
  gap: 8px;
  width: 100%;
}
.two-up :deep(.el-select) { flex: 1; min-width: 0; }

/* ---- 幻灯片缩略图条 ----
   轮播的主体是「一组图」，所以它得在这一块最上面、永远在视野里。
   点缩略图切换在改哪一张，点 ＋ 直接多一张 */
.strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.strip .sl,
.strip .add {
  position: relative;
  flex: none;
  width: 88px;
  height: 58px;
  padding: 0;
  border: 2px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-light) center/cover no-repeat;
  cursor: pointer;
}
.strip .sl:hover { border-color: var(--el-color-primary-light-5); }
.strip .sl.on {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}
/* 序号常驻：轮播的顺序就是这个数字的顺序，不标出来只能靠数 */
.strip .n {
  position: absolute;
  left: 0;
  top: 0;
  min-width: 17px;
  padding: 0 3px;
  border-bottom-right-radius: 5px;
  background: rgba(31, 36, 48, .72);
  color: #fff;
  font-size: 10px;
  line-height: 15px;
}
.strip .no {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}
/* 视频角标。放右下角，避开左上角那个序号；描一层深底，
   免得封面图正好是浅色时这个三角看不见 */
.strip .vd {
  position: absolute;
  right: 3px;
  bottom: 2px;
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(31, 36, 48, .72);
  color: #fff;
  font-size: 9px;
  line-height: 14px;
}
.strip .add {
  border-style: dashed;
  background: transparent;
  color: var(--el-text-color-placeholder);
  font-size: 20px;
  line-height: 1;
}
.strip .add:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.strip .add:disabled { opacity: .5; cursor: not-allowed; }

/* ---- 当前那一张的表单 ---- */
.slide {
  margin-bottom: 10px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 9px;
  background: var(--el-fill-color-blank);
}
.slide-h {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}
.slide :deep(.el-form-item:last-child) { margin-bottom: 0; }

/* ---- 添加区块 ---- */
.addrow { padding: 4px 4px 14px; }
.addbtn {
  width: 100%;
  padding: 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.addbtn:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.addbtn:disabled { opacity: .55; cursor: not-allowed; }
.dlg-lead {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.blocklist {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.blockcard {
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 9px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.blockcard:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.blockcard:disabled { opacity: .5; cursor: not-allowed; }
.blockcard .ic { color: var(--el-text-color-secondary); margin-right: 6px; }
.blockcard b { font-size: 13px; }
.blockcard small {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 11.5px;
  line-height: 1.5;
}

/* ---- 移除按钮：和上下箭头一起藏在 .acts 里 ---- */
.rm {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-placeholder);
  font: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.rm:hover:not(:disabled) { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.rm:disabled { opacity: .3; cursor: not-allowed; }


/* ---- 保存条：吸在这一列底下 ---- */
.savebar {
  flex: none;
  padding: 10px 16px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}
.savebar-btns {
  display: flex;
  align-items: center;
  gap: 10px;
  /* 窄屏下这一列会变宽，不封顶的话「全部清空」会飞到最右边，
     正好落在右下角那个在线咨询球下面 */
  max-width: 620px;
}
.sb-sp { flex: 1; }
.savebar-note {
  margin-top: 7px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
/* 「有未发布的改动」。用警告色不用危险色：这是一个状态，不是一个错误 */
.sb-draft {
  color: var(--el-color-warning);
  font-weight: 600;
}

/* ---------------- 第三列：实时预览 ---------------- */
.pvbar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pvbar-t { font-weight: 600; color: var(--el-text-color-primary); }
.pvbar-sp { flex: 1; }
.pvzoom { color: var(--el-text-color-placeholder); }
.pvbtn {
  padding: 3px 9px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.pvbtn:hover:not(:disabled) { border-color: var(--el-color-primary-light-5); }
.pvbtn.on {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
.pvbtn:disabled { opacity: .45; cursor: not-allowed; }
/* 预览现在是什么状态。三个字就够，但不能没有——
   「改了没跟着变」的时候，人第一件想知道的就是它还连着没有 */
.pvstate { font-size: 11.5px; }
.pvstate.live { color: var(--el-color-success); }
.pvstate.loading { color: var(--el-text-color-placeholder); }
.pvstate.blocked { color: var(--el-color-warning); }

.pvstage {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 18px;
}
.pvframe {
  flex: none;
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 30px rgba(20, 20, 40, .13);
  /* 缩放的是整个前台页面：真站按 1280 排版，这一列放不下就整体缩小，
     而不是让页面按 600px 去排——那样看到的是手机版，不是桌面版 */
  transform-origin: top center;
}
.pvframe iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}
.pvmsg {
  max-width: 380px;
  margin-top: 40px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.pvblock {
  position: absolute;
  inset: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  border-radius: 10px;
  background: var(--el-bg-color);
  text-align: center;
}
.pvblock p {
  max-width: 420px;
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

/*
  窄屏（把浏览器拖窄、或者在平板上）：预览那一列先让位。
  268 + 392 已经占掉 660，再挤下去预览缩到看不清，还把表单挤没了。
  ⚠ 是整列收起来，不是缩小：留一条 200px 宽的预览比没有更糟——
    看不清，还占着填表的地方。
*/
@media (max-width: 1180px) {
  .col-prev { display: none; }
  .col-form { flex: 1; width: auto; }
}
@media (max-width: 820px) {
  .col-list { width: 210px; }
  .top-hint { display: none; }
}
</style>

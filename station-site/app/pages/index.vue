<!--
  首页。除了「主推商品」那一块，其余全部来自装修内容——
  商家在管理台改完，刷新这里立刻生效，没有发布步骤（这也是选 SSR 不选 SSG 的原因）。

  每一块都做了「没配就不渲染」：商家不可能一次把所有字段填满，
  空着的区块直接不出现，比显示一个空框好。

  ## 版式照 kangwanjia-website-EN

  站点主题（2026-09-21 起是 Esva boots 的黑 + 电光蓝，见 app/assets/site.css 文件头）
  这个文件搬的是**首页的版式语汇**：

    · 全屏 hero，背景图上压一层**浅色**蒙版，文字是深色的
      —— 源站是这么做的，比「深色蒙版 + 白字」更亮，也更禁得住商家传一张亮图；
    · bento 网格，三张卡三种处理（白色主卡 / 粉色数字卡 / 深色横条）；
    · 商品区用源站 instruments 那段渐变底；
    · 关于我们用源站 about 页的错位叠卡（图 70% 靠左，文字卡 60% 靠右压上去）。

  ⚠ 和源站的根本差别：那边每一块的文案都是写死在模板里的常量，这边**全部来自接口**。
  所以凡是源站靠「多写一段 HTML」实现的效果（比如 hero 三张轮播图、bento 主卡里那张
  配图），这里都不能照抄——装修表单里没有对应的字段，硬做出来只能填假数据。
  少做的那几处在下面各自的注释里写了原因。
-->
<template>
  <div>
    <!-- 预览角标。**必须有**：这个页面的地址就是首页地址加一个参数，
         不标一下的话，看到的人分不清眼前是草稿还是已经发出去的样子 -->
    <div v-if="isPreview" class="preview-flag">{{ t.previewFlag }}</div>

    <SiteHeader :site="site" />

    <!--
      ===================== 七个区块，顺序由装修决定 =====================

      2026-09-10 起首页区块能在管理台里排序和关闭（blockOrder 见 script）。
      每一块的写法一个字没动，只是外面套了一层循环、条件里多一个 `key === '…'`。

      ⚠ 这里是七个各自独立的 v-if，**不是 v-if / v-else-if 链**。
        链要求相邻，而每块上面那段说明为什么这么写的注释就夹在中间；
        谁哪天顺手删掉一条注释、或者在两块之间插一段，链就会以一种
        「某一块无声消失」的方式坏掉，而且构建不报错。
        七个独立判断多算六次字符串比较，换的是「怎么改都不会错」。

      ⚠ 每块原来的显示条件都还在，和 key 判断 and 在一起：
        排序管的是**顺序**，「没配就不渲染」管的是**有没有内容**，两件事。
    -->
    <template v-for="key in blockOrder" :key="key">

    <!-- ===================== Banner ===================== -->
    <!--
      背景图轮播（源站就是三张图 5 秒一切 + 底部小圆点）。
      2026-09-10 管理台把 Banner 图做成了一个列表，这里才做得起来——
      在那之前装修里只有一个 `hero.image` 字段，做成轮播只能自己编另外两张图。

      **2026-09-11 起一张图可以配一句自己的话。** 存的时候是两半：幻灯片本身
      （`hero.slides`，`[{id, image}]`）在共享层、所有语言共用，每张的文案
      （语言层顶层的 `slideText[id]`）分语言，两边靠 id 对上。
      ⚠ **拼起来是后端做的**（SiteOpenService#compose），这里拿到的每一张
      已经带着当前语言那句话了。哪一张没填自己的标题，就退回整组共用的
      `hero.title`——所以老站点（一句话配三张图）行为一个字没变。

      ⚠ 只有一张（或者一张都没有）时，轮播那套东西全部不生效：
        没有定时器、没有圆点。
    -->
    <section v-if="key === 'hero'" class="hero"
             :class="[{ 'hero--plain': !heroBgCount }, `hero--${heroAlign}`, `hero--${heroHeight}`]">
      <!-- 每张一层，靠 opacity 交叉淡入。都留在 DOM 里而不是 v-if 切换：
           切过去才开始下载的话，第一次切换会先白一下。
           没配图的那一张不铺层，露出底下那层纯色/底纹 -->
      <!--
        ⚠ 这里是真的 <img>，不是 CSS 背景图（2026-09-13 改的）。三个理由：

          · **图片搜索索引不到 CSS 背景**。首屏那张往往是厂房、产线、主打产品，
            正是最值得被搜到的一张，而 background-image 在搜索引擎眼里根本不存在；
          · alt 有了落脚处。装修里新增的「图片说明」填的就是它；
          · LCP：首屏第一张给 fetchpriority="high"，浏览器会在解析到它的那一刻
            就开始下载，而 CSS 背景要等样式算完才开始。

        排版靠 object-fit: cover 顶替原来的 background-size: cover，
        视觉上和改之前一样。外面那层 div 保留，淡入淡出仍然作用在它身上。

        ⚠ 没填说明的那些 alt="" —— 这是**正确**的无障碍表达（纯装饰图，
          读屏应当跳过），不是「忘了填」。千万别改成 alt="banner" 之类的占位词，
          那会让读屏用户每翻一屏都听见一句没有信息的话。
      -->
      <div
        v-for="(sld, i) in heroSlides"
        v-show="sld.image || sld.video"
        :key="sld.id"
        class="hero-bg"
        :class="{ 'is-on': i === heroSlide }"
        :aria-hidden="sld.alt ? undefined : 'true'"
      >
        <!--
          配了视频的那一张放视频，背景图退成它的封面（2026-09-21）。

          ⚠ muted / playsinline 不是可有可无的两个词：
            · 带声音的自动播放，浏览器一律拦下来不给播；
            · 不写 playsinline，iOS 会把它顶成一个全屏播放器盖住整个首页。
            两种情况下访客看到的都不是商家配的那个样子。

          ⚠ **故意没有 autoplay 属性**，开播在 syncHeroVideos() 里做。
            写上 autoplay 的话，服务端渲染出来的那一份一到浏览器就开始播，
            「勾了减少动态效果就不播」和「只播当前这一张」这两件事都来不及拦。
            JS 没跑起来时它停在 poster 上，而 poster 正是这一张的背景图——
            降级之后就是改造之前那张静态大图，看不出区别。

          ⚠ 视频没有 alt。图片说明（slideText[id].alt）在这里当无障碍名称用，
            没填时外面那层 div 已经是 aria-hidden，读屏会整块跳过去。
        -->
        <video
          v-if="sld.video"
          :ref="el => setHeroVideo(i, el)"
          :src="sld.video"
          :poster="sld.image || undefined"
          :aria-label="sld.alt || undefined"
          class="hero-bg-img"
          muted
          loop
          playsinline
          :preload="i === 0 ? 'auto' : 'metadata'"
        ></video>
        <img
          v-else-if="sld.image"
          :src="sld.image"
          :alt="sld.alt || ''"
          class="hero-bg-img"
          :loading="i === 0 ? 'eager' : 'lazy'"
          :fetchpriority="i === 0 ? 'high' : 'auto'"
        >
      </div>
      <div class="hero-overlay" aria-hidden="true"></div>

      <div class="hero-in">
        <!-- 眉标。商家填了就用他填的；没填时拿标语顶上，但**只在 hero.subtitle
             也配了的时候**——否则标语已经当副标题用了，同一句话不该出现两次 -->
        <span v-if="eyebrow" class="pill hero-pill">✦ {{ eyebrow }}</span>
        <!-- 源站的大标题是「一行普通 + 一行强调色」。这里的标题是商家填的一整句，
             所以按句读切开，最后一段上强调色——填「A. B.」或者「甲，乙。」都能出效果，
             填一整句不带标点的就只是一行普通标题，不会出错。

             注释放在 h1 外面：放里面的话 dev 构建会把它当成 h1 的子节点渲染出来
             （生产构建会剥掉），查 DOM 时标题里夹一段中文注释很碍眼 -->
        <h1>
          <template v-for="(line, i) in heroLines" :key="i">
            <span :class="{ accent: heroLines.length > 1 && i === heroLines.length - 1 }">{{ line }}</span>
            <br v-if="i < heroLines.length - 1">
          </template>
        </h1>
        <p v-if="heroSub">{{ heroSub }}</p>
        <!-- 按钮上的字可以自己填（「Request a Quote」这类），留空用内置文案。
             去哪儿也可以自己填了（2026-09-13），留空还是原来那两个目的地：
             主按钮去商品列表、次按钮跳本页的询盘表单。见 heroBtn1 / heroBtn2 -->
        <!--
          ⚠ 外链用 <a>、站内用 <NuxtLink>，两个分支写开而不是用 <component :is>：
            NuxtLink 拿站外地址会当成路由去跳，表现是点一下整页白掉。
        -->
        <div class="hero-actions">
          <a v-if="heroBtn1.external" :href="heroBtn1.href" class="btn btn-primary"
             target="_blank" rel="noopener noreferrer">
            {{ site.hero?.btn1 || t.viewProducts }}
          </a>
          <NuxtLink v-else :to="heroBtn1.href" class="btn btn-primary">
            {{ site.hero?.btn1 || t.viewProducts }}
          </NuxtLink>

          <a v-if="heroBtn2.external" :href="heroBtn2.href" class="btn btn-ghost"
             target="_blank" rel="noopener noreferrer">
            {{ site.hero?.btn2 || t.contactUs }}
          </a>
          <NuxtLink v-else :to="heroBtn2.href" class="btn btn-ghost">
            {{ site.hero?.btn2 || t.contactUs }}
          </NuxtLink>
        </div>
      </div>

      <!-- 圆点。**是按钮不是装饰**：能点、能用键盘走到、读屏能念出「第几张」。
           只有一张图时整条不出现 -->
      <!-- 标签只用数字（「2 / 3」）。这一版没有「幻灯片」这个词的译文，
           而现编一个英文词摆在日语站上比数字更糟——数字不用翻译 -->
      <div v-if="heroSlides.length > 1" class="hero-dots" role="group">
        <button
          v-for="(sld, i) in heroSlides"
          :key="sld.id"
          type="button"
          class="hero-dot"
          :class="{ 'is-on': i === heroSlide }"
          :aria-label="`${i + 1} / ${heroSlides.length}`"
          :aria-current="i === heroSlide ? 'true' : undefined"
          @click="showHeroSlide(i)"
        ></button>
      </div>
    </section>

    <!-- ===================== 承诺横条 ===================== -->
    <!--
      Banner 正下方那一条「保修 / 发货 / 退货 / 支持」。

      ⚠ 它和下面 bento 那三栏不是一回事，别因为长得都像「三四个小格子」就合并：
        bento 讲的是「我们凭什么做得好」，是要读的；这一条讲的是「买了之后你有什么
        保障」，是扫一眼就过的。消费品站把它贴着 Banner 放，因为访客的第一个疑问
        是「出了问题怎么办」，而不是「你厂多大」。

      没有 desc 的条目只显示一行粗体字——四条里有两条一句话说得清、两条要补一句，
      是常态，不该逼商家把每条都填满。
    -->
    <section v-if="key === 'trust' && trustItems.length" class="trust"
             :class="`trust--${trustVariant}`">
      <div class="wrap">
        <ul class="trust-row">
          <li v-for="(item, i) in trustItems" :key="item.id || i" class="trust-item">
            <!-- 图标是纯装饰：它右边那行字已经把同样的意思说清楚了，
                 读屏软件再念一遍「箭头符号」只是噪音 -->
            <span v-if="item.icon" class="trust-icon" aria-hidden="true">{{ item.icon }}</span>
            <div class="trust-text">
              <strong>{{ item.title }}</strong>
              <span v-if="item.desc">{{ item.desc }}</span>
            </div>
          </li>
        </ul>
      </div>
    </section>

    <!-- ===================== 三栏特色 → bento ===================== -->
    <!--
      源站的 bento 是三张卡：左边一张带配图的主卡竖跨两行，右上粉色数字卡，右下小卡。
      这里的映射：

        主卡（竖跨两行）  ← 特色 1     粉色数字卡 ← 数字亮点的第一条
        小卡              ← 特色 2     深色横条   ← 特色 3

      粉色那张要的是一个「大数字」，而特色条目只有图标 + 标题 + 描述，
      没有数字可放；数字亮点那一排正好有，借第一条过来。数字亮点为空时这张卡不出现，
      主卡也就不跨行了（is-tall 那个 class），网格不会留一个洞。
    -->
    <section v-if="key === 'bento' && highlights.length" class="section strengths">
      <div class="wrap">
        <!-- 区块标题：商家填了就整句用他填的（不拆两段、不上强调色——
             强调色扣在哪半句只有原文案的作者知道），没填用内置那套两段式 -->
        <div class="section-head">
          <h2 v-if="site.bento?.title">{{ site.bento.title }}</h2>
          <h2 v-else>{{ t.strengthsA }}<span class="accent">{{ t.strengthsB }}</span></h2>
          <p>{{ site.bento?.lead || t.strengthsLead }}</p>
        </div>

        <div class="bento" :class="{ 'bento--grid3': bentoVariant === 'grid3' }">
          <article v-if="highlights[0]" class="bento-card bento-feature" :class="{ 'is-tall': featureIsTall }">
            <span v-if="highlights[0].icon" class="bento-icon">{{ highlights[0].icon }}</span>
            <h3>{{ highlights[0].title }}</h3>
            <p v-if="highlights[0].desc">{{ highlights[0].desc }}</p>
          </article>

          <article v-if="bentoStat" class="bento-card bento-pink">
            <div class="stat-mark">◷</div>
            <div class="big-stat">{{ bentoStat.value }}</div>
            <span class="stat-caption">{{ bentoStat.label }}</span>
          </article>

          <article v-if="highlights[1]" class="bento-card bento-small">
            <span v-if="highlights[1].icon" class="bento-icon">{{ highlights[1].icon }}</span>
            <h3>{{ highlights[1].title }}</h3>
            <p v-if="highlights[1].desc">{{ highlights[1].desc }}</p>
          </article>

          <article v-if="highlights[2]" class="bento-card"
                   :class="bentoVariant === 'grid3' ? 'bento-plain' : 'bento-dark'">
            <span v-if="highlights[2].icon" class="bento-icon"
                  :class="{ 'bento-icon--light': bentoVariant !== 'grid3' }">{{ highlights[2].icon }}</span>
            <div>
              <h3>{{ highlights[2].title }}</h3>
              <p v-if="highlights[2].desc">{{ highlights[2].desc }}</p>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ===================== 主推商品 ===================== -->
    <!-- 没有主推就退而取最新的一批，不然首页会缺一大块 -->
    <section v-if="key === 'products' && products.length" class="instruments">
      <div class="wrap">
        <div class="instruments-head" :class="{ 'instruments-head--center': productsVariant === 'carousel' }">
          <div>
            <h2>{{ site.products?.title || t.products }}</h2>
            <p>{{ site.products?.lead || t.productsLead }}</p>
          </div>
          <!-- 轮播版式的「查看全部」在下面居中那颗按钮上，这里不再重复一个 -->
          <NuxtLink v-if="productsVariant !== 'carousel'" :to="localePath('/products')" class="link-more">{{ t.viewAll }} →</NuxtLink>
        </div>
        <!--
          「横向轮播」（2026-09-30，照 getjolt.fr 首页那排「恢复产品」）：
          白底大图卡片横着排、两侧箭头翻页，卡片上只有 品类 / 商品名 / 查看 三样，
          下面居中一个「查看全部」的描边按钮。价格和加购留给详情页——这一排的任务是把人引进去。

          ⚠ 参考站卡片上还有星级和评论数，这里**刻意不做**：本站没有真实评价数据，
            编一个 4.9 分 / 2957 条是对消费者的虚假陈述（也会被谷歌判成自报评分降权），
            见首页「顾客评价」那一块的注释。以后有了真实评价系统再接。

          滑动用原生 scroll-snap：手机上直接手指拖，桌面用箭头；没有 JS 也能横着滚。
        -->
        <div v-if="productsVariant === 'carousel'" class="pcar">
          <button v-if="carouselOverflow" type="button" class="pcar-nav pcar-prev" :aria-label="t.prev" @click="scrollCarousel(-1)">‹</button>
          <ul ref="carouselTrack" class="pcar-track">
            <li v-for="item in products" :key="item.code" class="pcar-card">
              <NuxtLink :to="localePath(`/products/${item.code}`)" class="pcar-link">
                <span class="pcar-img">
                  <img v-if="item.images?.length" v-bind="imgAttrs(item.images[0], 480)" :alt="item.title" loading="lazy">
                </span>
                <span v-if="categoryLeaf(item.category, site.categoryNames)" class="pcar-cat">
                  {{ categoryLeaf(item.category, site.categoryNames) }}
                </span>
                <strong class="pcar-title">{{ item.title }}</strong>
                <span class="pcar-cta">{{ t.discover }}</span>
              </NuxtLink>
            </li>
          </ul>
          <button v-if="carouselOverflow" type="button" class="pcar-nav pcar-next" :aria-label="t.next" @click="scrollCarousel(1)">›</button>
        </div>
        <div v-if="productsVariant === 'carousel'" class="pcar-more">
          <NuxtLink :to="localePath('/products')" class="btn btn-ghost">{{ t.viewCollection }}</NuxtLink>
        </div>

        <!-- 「两列大图」只是把列数从 3 改成 2：卡片本身是 ProductCard，
             它自己按容器宽度排版，图会跟着变大 -->
        <div v-else class="grid" :class="productsVariant === 'wide' ? 'grid-2' : 'grid-3'">
          <ProductCard v-for="item in products" :key="item.code" :item="item" :lang="site.lang" :site="site" />
        </div>
      </div>
    </section>

    <!-- ===================== 能力介绍 ===================== -->
    <!-- 卡片形状照源站 about 页的 value-card。默认第二张填成实心紫，
         源站也只填一张——整排都填就没有重点了。
         哪一张由装修里的 blocks.caps.accent 决定，填 -1 就一张都不强调 -->
    <section v-if="key === 'caps' && site.capabilities?.length" class="section caps">
      <div class="wrap">
        <div class="section-head">
          <h2 v-if="site.caps?.title">{{ site.caps.title }}</h2>
          <h2 v-else>{{ t.offerA }}<span class="accent">{{ t.offerB }}</span></h2>
          <p>{{ site.caps?.lead || t.offerLead }}</p>
        </div>
        <div class="cap-grid" :class="{ 'cap-grid--g3': capsVariant === 'g3' }">
          <article v-for="(item, i) in site.capabilities" :key="i"
                   class="cap-card" :class="{ 'cap-card--filled': i === capsAccent }">
            <span v-if="item.icon" class="cap-icon" :class="{ 'cap-icon--light': i === capsAccent }">{{ item.icon }}</span>
            <h3>{{ item.title }}</h3>
            <p v-if="item.desc">{{ item.desc }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- ===================== 顾客评价 ===================== -->
    <!--
      ⚠ 这一块**刻意不输出 schema.org 的 AggregateRating**。

      那个富摘要（搜索结果里的金色星星）要求评分来自站点上真实、可核查的评价，
      而这里的分数和条数是商家在装修表单里手填的数字。Google 对「自报评分」
      的处置是整站结构化数据降权，严重的直接人工处罚——拿整站信誉换一行星星，
      不划算。页面上照常显示给访客看，只是不向搜索引擎声明。
      下面 FAQ 那块相反，问答是商家自己的陈述，声明它是正当的。

      评分头（大分数 + 星 + 条数）和评价卡是两件事：只填了分数、一条评价都没录，
      仍然渲染评分头；反过来只有评价卡也成立。
    -->
    <section v-if="key === 'reviews' && (reviewItems.length || site.reviews?.score)"
             class="section reviews">
      <div class="wrap">
        <div class="section-head">
          <h2>{{ site.reviews?.title || t.reviews }}</h2>
          <p>{{ site.reviews?.lead || t.reviewsLead }}</p>
        </div>

        <div v-if="site.reviews?.score" class="score">
          <div class="score-value">{{ site.reviews.score }}</div>
          <div class="score-meta">
            <!-- 星星本身是图形，读屏软件念「黑星黑星黑星白星」没有意义，
                 所以整行标成装饰，真正的信息由 aria-label 那句话承担 -->
            <div class="score-stars" role="img"
                 :aria-label="t.starsLabel.replace('{n}', String(site.reviews.score))">
              <span aria-hidden="true">{{ starRow(site.reviews.score) }}</span>
            </div>
            <p v-if="site.reviews.count">
              {{ t.reviewCount.replace('{n}', String(site.reviews.count)) }}
            </p>
          </div>
        </div>

        <div v-if="reviewItems.length" class="review-grid"
             :class="{ 'review-grid--strip': reviewsVariant === 'strip' }">
          <figure v-for="(item, i) in reviewItems" :key="item.id || i" class="review-card">
            <div class="review-stars" role="img"
                 :aria-label="t.starsLabel.replace('{n}', String(item.stars ?? 5))">
              <span aria-hidden="true">{{ starRow(item.stars) }}</span>
            </div>
            <blockquote>{{ item.body }}</blockquote>
            <figcaption>
              <strong>{{ item.name }}</strong>
              <span v-if="item.role">{{ item.role }}</span>
            </figcaption>
          </figure>
        </div>
      </div>
    </section>

    <!-- ===================== 合作方与代言人 ===================== -->
    <!--
      logo 拿不到就退成名字的首字母方块，不留裂图——这一排里出现一个破图标，
      比那一格干脆显示文字要显眼得多，而且会让旁边真的 logo 也显得不可信。
    -->
    <section v-if="key === 'partners' && (partnerItems.length || partnerVideos.length)" class="section section-soft partners">
      <div class="wrap">
        <div class="section-head">
          <h2>{{ site.partners?.title || t.partners }}</h2>
          <p>{{ site.partners?.lead || t.partnersLead }}</p>
        </div>
        <ul v-if="partnerItems.length" class="partner-grid" :class="{ 'partner-grid--logos': partnersVariant === 'logos' }">
          <li v-for="(item, i) in partnerItems" :key="item.id || i" class="partner-card">
            <img v-if="partnerLogo(item, i)" :src="partnerLogo(item, i)" :alt="item.name || ''"
                 loading="lazy" @error="markPartnerBroken(item.id || i)">
            <span v-else class="partner-mono" aria-hidden="true">{{ initials(item.name) }}</span>
            <template v-if="partnersVariant !== 'logos'">
              <strong>{{ item.name }}</strong>
              <span v-if="item.role">{{ item.role }}</span>
            </template>
          </li>
        </ul>

        <!--
          合作方的短视频（2026-09-30，照 getjolt.fr 合作方下面那排竖屏短视频）。
          装修里 partners.videos：[{id, video, poster}]，说明文字分语言（partnerVideoText → caption）。

          · 竖屏 9:16 横着排，原生 scroll-snap：手机手指拖，桌面两侧箭头；
          · 进入视口才开始播（IntersectionObserver），**静音 + 循环 + 行内**——
            带声音的自动播放浏览器一律拦，不写 playsinline 的话 iOS 会弹成全屏；
          · 点一下开声音（同时把别的几条静音），再点一下关；
          · 勾了「减少动态效果」的访客不自动播，停在封面上，点了才播。
          preload="none"：一排十几条视频在首页上不能一进来就全下，滚到了才拉。
        -->
        <div v-if="partnerVideos.length" class="pvid">
          <button v-if="videoOverflow" type="button" class="pcar-nav pcar-prev" :aria-label="t.prev"
                  @click="scrollVideos(-1)">‹</button>
          <ul ref="videoTrack" class="pvid-track">
            <li v-for="(v, i) in partnerVideos" :key="v.id || i" class="pvid-card">
              <video :ref="el => setPartnerVideo(i, el)" :src="v.video" :poster="v.poster || undefined"
                     muted loop playsinline preload="none"
                     :aria-label="v.caption || undefined"
                     @click="toggleSound(i)"></video>
              <button type="button" class="pvid-sound" :aria-label="t.videoSound"
                      :aria-pressed="soundOn === i" @click="toggleSound(i)">
                {{ soundOn === i ? '🔊' : '🔇' }}
              </button>
              <span v-if="v.caption" class="pvid-cap">{{ v.caption }}</span>
            </li>
          </ul>
          <button v-if="videoOverflow" type="button" class="pcar-nav pcar-next" :aria-label="t.next"
                  @click="scrollVideos(1)">›</button>
        </div>
      </div>
    </section>

    <!-- ===================== 关于我们 ===================== -->
    <!--
      源站 about 页的错位叠卡：图片 70% 宽靠左，文字卡 60% 宽靠右、往上压 15%。
      两块都留在正常流里（不用绝对定位），所以外层高度会跟着长，卡片挂在图下面
      也不会压到下一屏——源站那段注释就是这个意思。

      商家没传配图时退成一张普通的整宽卡（is-solo）：错位效果本来就是靠图撑出来的。
    -->
    <section v-if="key === 'about' && (site.about?.content || site.about?.title)"
             id="about" class="section about">
      <div class="wrap">
        <div class="about-stagger" :class="`about-stagger--${aboutVariant}`">
          <!-- 图挂了整块不显示，卡片会自动退成整宽——比留一个裂图标好。
               版式选「只有文字卡」时也不渲染它 -->
          <figure v-if="aboutImage && aboutVariant !== 'solo'" class="about-media">
            <!-- 图片说明优先用商家在装修里填的那句（分语言）；留空退回标题，
                 和 2026-09-13 之前的行为一致 -->
            <img :src="aboutImage" :alt="site.about.imageAlt || site.about.title || t.aboutUs"
                 loading="lazy" @error="aboutImgBroken = true">
          </figure>

          <div class="about-card" :class="{ 'is-solo': !aboutImage || aboutVariant === 'solo' }">
            <span class="pill">✦ {{ t.aboutPill }}</span>
            <h2>{{ site.about.title || t.aboutUs }}</h2>
            <!-- 正文按空行切段。装修表单里那个 textarea 存的是带 \n 的纯文本，
                 整段丢进一个 <p> 里靠 white-space:pre-line 也能换行，但段间距是 0，
                 长文案会糊成一坨 -->
            <p v-for="(para, i) in aboutParas" :key="i" :class="{ 'about-lead': i === 0 }">{{ para }}</p>

            <div v-if="aboutStats.length" class="stats">
              <div v-for="(s, i) in aboutStats" :key="i" class="stat">
                <strong>{{ s.value }}</strong>
                <span>{{ s.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== 常见问题 ===================== -->
    <!--
      手风琴，但答案用 `v-show` 而不是 `v-if`——折叠起来的答案仍然在 HTML 里。
      这一块存在的主要理由就是自然搜索：`v-if` 会让爬虫只看到一排问题、一个答案，
      而「配送要多久」这类长尾词全在答案里。

      ⚠ 同一段问答还会以 FAQPage 结构化数据再出一遍（见 script 里那段 useHead）。
        两处必须是同一份文字：Google 明确要求结构化数据里的问答和页面上可见的
        一字不差，对不上是人工处罚的理由。所以两边都从 `faqItems` 来，
        别在任何一边「顺手润色一下」。

      按钮套在 h3 里，是 WAI-ARIA 手风琴的标准写法：h3 让读屏用户能按标题跳读，
      button 让整行可点、可用键盘操作。二者缺一都会让这一块变得难用。
    -->
    <section v-if="key === 'faq' && faqItems.length" id="faq" class="section faq">
      <div class="wrap">
        <div class="section-head">
          <h2>{{ site.faq?.title || t.faq }}</h2>
          <p>{{ site.faq?.lead || t.faqLead }}</p>
        </div>
        <ul class="faq-list">
          <li v-for="(item, i) in faqItems" :key="item.id || i" class="faq-item"
              :class="{ 'is-open': faqOpen(i) }">
            <h3 class="faq-q">
              <button type="button" :aria-expanded="faqOpen(i) ? 'true' : 'false'"
                      :aria-controls="`faq-a-${i}`" @click="toggleFaq(i)">
                <span>{{ item.q }}</span>
                <span class="faq-mark" aria-hidden="true">{{ faqOpen(i) ? '−' : '+' }}</span>
              </button>
            </h3>
            <div v-show="faqOpen(i)" :id="`faq-a-${i}`" class="faq-a">
              <p v-for="(para, j) in splitParas(item.a)" :key="j">{{ para }}</p>
            </div>
          </li>
        </ul>
      </div>
    </section>

    <!-- ===================== 全球布局 ===================== -->
    <!-- 地图数据装修里还没有，组件自带一套默认地点，
         所以这一屏不会因为没配就空着，见 components/GlobalReach.vue

         ⚠ stats **刻意不回退** site.about.stats。回退的后果是同一组
         「15+ / 30+ / 8000m² / 120+」在首页出现三次：bento 粉卡借第一条、
         关于我们卡列全部、这里再列一遍。数字重复三次不显得业绩多，
         只显得没内容。没配 reach.stats 时组件自己按地图上的地点数算两个
         （见 GlobalReach 的 statRows），那两个数和地图是同一件事，不打架。 -->
    <GlobalReach
      v-if="key === 'reach'"
      :lang="site.lang"
      :hq="site.reach?.hq"
      :destinations="site.reach?.destinations"
      :stats="site.reach?.stats"
      :title="site.reach?.title"
      :lead="site.reach?.lead"
      :variant="reachVariant"
    />

    <!-- ===================== 询盘 ===================== -->
    <section v-if="key === 'inquiry'" id="inquiry" class="section"
             :class="{ 'section-soft': inquiryVariant === 'soft' }">
      <div class="wrap">
        <div class="section-head">
          <h2>{{ site.inquiry?.title || t.contactUs }}</h2>
          <p>{{ site.inquiry?.lead || t.contactLead }}</p>
        </div>
        <div class="inquiry-box">
          <InquiryForm :lang="site.lang" :fallback-email="site.contact?.email" />
        </div>
      </div>
    </section>

    </template>
    <!-- ===================== 区块循环到此结束 ===================== -->

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { isUsableImageUrl } from '~/lib/shop'
import { categoryLeaf } from '~/lib/categories'
import { resizedImage, fallbackToOriginal } from '~/lib/imageUrl'

const site = await useSiteInfo()
const localePath = useLocalePath()

/*
 * 装修预览（`?__preview=1`，管理台点「预览」开出来的那个窗口）。
 *
 * 收到管理台推来的草稿数据后，**就地改** site 这个对象，不是换一个新的：
 * 上面那行 `await useSiteInfo()` 已经把对象交给模板了，换引用模板看不见。
 *
 * 先删掉草稿里没有的键再覆盖——不删的话，商家把「关于我们」清空之后，
 * 预览里那一块还在（旧值没人去掉），而线上是没有的。
 */
const isPreview = useIsPreview()

usePreviewReceiver(payload => {
  for (const key of Object.keys(site)) {
    if (!(key in payload)) delete (site as Record<string, unknown>)[key]
  }
  Object.assign(site, payload)
})

/*
 * 区块顺序。
 *
 * 这份数组既是**默认顺序**，也是「认识哪些区块」的白名单。
 * 商家在管理台里排的顺序存在 site.order，关掉的存在 site.off。
 */
const DEFAULT_ORDER = ['hero', 'trust', 'bento', 'products', 'caps',
  'reviews', 'partners', 'about', 'faq', 'reach', 'inquiry']

const blockOrder = computed(() => {
  const wanted = Array.isArray(site.order) && site.order.length ? site.order : DEFAULT_ORDER
  const seen = new Set<string>()
  const out: string[] = []
  for (const key of wanted) {
    // 不认识的键直接丢掉：装修 JSON 是用户可写的，别让它往首页里塞东西
    if (DEFAULT_ORDER.includes(key) && !seen.has(key)) { seen.add(key); out.push(key) }
  }
  /*
   * ⚠ 漏掉的补在最后，不能不补。
   *
   * 以后新增一种区块时，所有老站点存的 order 里都没有它——不补的话它在那些站上
   * 永远不出现，而且没有任何报错，只是首页少一块。补在最后至少能被看见，
   * 商家再自己拖到想要的位置。
   */
  for (const key of DEFAULT_ORDER) if (!seen.has(key)) out.push(key)

  const off = new Set(Array.isArray(site.off) ? site.off : [])
  return out.filter(key => !off.has(key))
})

/** 能力卡里哪一张填成实心紫。整排都填就没有重点了，所以只能挑一张 */
const capsAccent = computed(() => {
  const value = site.blocks?.caps?.accent
  return typeof value === 'number' ? value : 1
})

/*
 * 首页取几个商品。默认 6：三列布局下正好两行满，不会出现半行空位。
 *
 * ⚠ 夹在 1~24 之间。这个数来自装修 JSON（用户可写），直接透给接口的话，
 *   一个 size=99999 就是一次把整库商品拉进首页。
 */
const productLimit = computed(() => {
  const value = Number(site.blocks?.products?.limit)
  return Number.isFinite(value) && value > 0 ? Math.min(Math.round(value), 24) : 6
})

const { data: productData } = await useAsyncData(
  () => `home-products-${site.lang}-${productLimit.value}`,
  () => useProducts({ lang: site.lang, size: productLimit.value }),
  {
    watch: [() => site.lang, productLimit],
    default: () => ({ rows: [] as any[], total: 0, page: 1, size: 6, currency: '' })
  }
)
const products = computed(() => productData.value?.rows || [])

/*
 * 关于我们那张配图。两道判断缺一不可：
 *
 *   isUsableImageUrl  —— 服务端就能下结论的那类问题（相对路径、空串）。
 *                        它让 SSR 直接渲染出正确版式，没有抖动。见 lib/shop.ts 的注释。
 *   aboutImgBroken    —— 地址形状没问题但文件不在了（管理台把图删了、桶换了）。
 *                        这类只有真去下一次才知道，所以还得留着 @error，
 *                        外加下面 onMounted 里那一次补检。
 */
const aboutImgBroken = ref(false)
const aboutImage = computed(() =>
  isUsableImageUrl(site.about?.image) && !aboutImgBroken.value ? site.about!.image! : '')

/*
 * 补检水合之前就已经失败的图。
 *
 * @error 抓不到那一类（见 lib/shop.ts）。挂载后再问一次浏览器：这张图「加载完了」
 * 但「固有宽度是 0」——那就是加载失败了，complete 为 true 只说明它不再等了。
 * 这一步只在客户端跑，SSR 阶段 onMounted 不执行。
 */
onMounted(() => {
  const img = document.querySelector<HTMLImageElement>('.about-media img')
  if (img && img.complete && img.naturalWidth === 0) aboutImgBroken.value = true
})
/**
 * 按空行切段。装修表单里那个多行文本框存的是带 \n 的纯文本，整段丢进一个 <p> 里
 * 靠 white-space:pre-line 也能换行，但段间距是 0，长文案会糊成一坨。
 *
 * 「关于我们」正文和常见问题的答案都走这里——两处是同一种东西（多行文本框里的纯文本），
 * 各写一份必然会在某一次改动里走样。
 */
function splitParas(text?: string) {
  return String(text || '').split(/\n\s*\n/).map(s => s.trim()).filter(Boolean)
}
const aboutParas = computed(() => splitParas(site.about?.content))

/*
 * Banner 的那一组幻灯片。
 *
 * hero 图走 CSS background-image，填了一个加载不出来的地址不会裂图，
 * 但会让 hero--plain 那套替代视觉不生效——整屏变成纯色。所以每张都过一遍判断。
 *
 * ⚠ 三种存法都要认，从新到旧：
 *   · `hero.slides` —— 2026-09-11 起管理台存这个，`[{id, image}]` 存在共享层，
 *     每张还能有自己的一句话（文案分语言存，**后端拼好了才送到这儿**，
 *     所以这里读到的是 `{id, image, title, subtitle}`）；
 *   · `hero.images` —— 2026-09-10 那版，只有一串地址；
 *   · `hero.image`  —— 再之前的单张。
 *   认漏了的表现是「首页 Banner 背景突然没了」，而库里那张图明明还在。
 *
 * ⚠ 没有图的幻灯片也留着：那是「只有文字、背景用纯色底纹」的一屏，
 *   它有自己的一句话，不能因为没配图就把整屏吞掉。
 */
type HeroSlide = { id: string; image: string; video: string; alt?: string; title?: string; subtitle?: string }

const heroSlides = computed<HeroSlide[]>(() => {
  const slides = Array.isArray(site.hero?.slides) ? site.hero!.slides! : []
  if (slides.length) {
    return slides
      .filter(s => s && typeof s === 'object')
      .map((s, i) => ({
        id: String(s.id || `s${i}`),
        image: isUsableImageUrl(s.image) ? String(s.image) : '',
        // 2026-09-21 起这一张可以是视频，背景图退成它的封面。
        // 判地址形状用的还是 isUsableImageUrl——它管的是「这串东西能不能当地址用」
        // （http(s):// 或 / 开头），和是图是视频无关，不值得再写一个一模一样的
        video: isUsableImageUrl(s.video) ? String(s.video) : '',
        // alt 和 title/subtitle 一样是文案，分语言存在 slideText[id] 里，
        // 由后端 compose 拼进条目。没填就是空串 = 纯装饰图，见模板里那段
        alt: typeof s.alt === 'string' ? s.alt : '',
        title: s.title,
        subtitle: s.subtitle
      }))
  }
  const list = Array.isArray(site.hero?.images) ? site.hero!.images! : []
  const usable = list.filter(url => isUsableImageUrl(url))
  const legacy = usable.length
    ? usable
    : (isUsableImageUrl(site.hero?.image) ? [site.hero!.image!] : [])
  return legacy.map((image, i) => ({ id: `legacy-${i}`, image, video: '' }))
})

/**
 * 真正有背景的有几张（图或者视频）。一张都没有时整屏走 hero--plain 那套替代视觉。
 *
 * ⚠ 视频必须算进来。只数图的话，「只配了视频、没配封面」的那种会被判成「没有背景」，
 *   于是整屏铺上 hero--plain 的制图纸底纹、蒙版还被关掉——视频照样在底下播着，
 *   白字压在一张亮画面上。
 */
const heroBgCount = computed(() => heroSlides.value.filter(s => s.image || s.video).length)

/** 当前显示第几张。只有一张时这套东西全部不生效 */
const heroSlide = ref(0)

/*
 * 自动切换。
 *
 * ⚠ 只在客户端起定时器（onMounted 里），服务端渲染的永远是第一张——
 *   SSR 和首帧必须一致，否则 hydration 对不上。
 *
 * ⚠ 跟随系统的「减少动态效果」设置：晕动症患者受不了整屏图片自己动。
 *   勾了那个设置就不自动切，圆点还在，想看第二张自己点。
 */
let heroTimer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  if (heroSlides.value.length < 2) return
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  heroTimer = setInterval(() => {
    heroSlide.value = (heroSlide.value + 1) % heroSlides.value.length
  }, 5000)
})
onBeforeUnmount(() => { if (heroTimer) clearInterval(heroTimer) })

/*
 * 图片组变了（装修预览里删掉一张、或者切了语言）就把下标收回来。
 * 不收的话下标可能指到数组外面，那一屏会变成纯色——预览里看着就像「图丢了」。
 */
watch(heroSlides, list => {
  if (heroSlide.value >= list.length) heroSlide.value = 0
})

/*
 * 背景视频的播放控制（2026-09-21）。
 *
 * 三件事，都只在浏览器里发生：
 *
 *   · **只播当前那一张**。多张幻灯片各自铺一层、全都留在 DOM 里（见模板那段注释），
 *     不管的话几段视频会同时解码，手机上的表现是整页发烫、滚动掉帧；
 *   · **跟随系统的「减少动态效果」**。勾了就一帧都不播，停在封面图上——
 *     和上面自动切换那件事同一个理由，整屏画面自己动会让晕动症的人难受；
 *   · **切走的那张倒回开头**，下次切回来从头播，而不是从中间接着放。
 *
 * ⚠ el.muted 在这里再赋一次值，虽然模板上已经写了 muted。属性和 DOM 属性是两回事，
 *   而 play() 在「浏览器认为它没静音」时会被直接拒绝——首屏黑一块，还不报错。
 */
const heroVideos = new Map<number, HTMLVideoElement>()

function setHeroVideo(index: number, el: unknown) {
  if (el instanceof HTMLVideoElement) heroVideos.set(index, el)
  else heroVideos.delete(index)
}

/** 勾了「减少动态效果」就一直是 false。服务端没有这个概念，所以初值随便，反正不在服务端跑 */
let heroMotionOk = true

function syncHeroVideos() {
  heroVideos.forEach((el, i) => {
    el.muted = true
    if (i === heroSlide.value && heroMotionOk) {
      // 被浏览器拦下来是正常结果之一（省流量模式、低电量模式），
      // 那时候停在封面图上就好，不该往控制台里扔一个没人能处理的报错
      void el.play()?.catch(() => {})
      return
    }
    el.pause()
    if (i !== heroSlide.value) el.currentTime = 0
  })
}

onMounted(() => {
  heroMotionOk = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  syncHeroVideos()
})

// 切到哪一张、以及装修预览里改了幻灯片，都要重新对一遍谁该播。
// 预览那条要等 DOM 更新完：新那一张的 <video> 这会儿还没挂上来
watch(heroSlide, syncHeroVideos)
watch(heroSlides, () => nextTick(syncHeroVideos))

function showHeroSlide(index: number) {
  heroSlide.value = index
  // 人点了圆点就别再自动跑了：他正在挑一张看，图自己换走很烦
  if (heroTimer) { clearInterval(heroTimer); heroTimer = null }
}
/*
 * 当前这一张的文案。
 *
 * ⚠ 这里**不用自己按语言去拼**：每张的文案分语言存（语言层的 `slideText[id]`），
 *   但拼接是后端做的（SiteOpenService#compose），送到这儿的每一张已经带着
 *   当前语言那句话了。前台自己再拼一遍的话，「缺译文退回默认语言」那套规则
 *   就有了第二份实现，而两份迟早会不一致——症状是管理台预览里对、发出去不对。
 *
 * 这一张没有自己的标题就退回整组共用的 `hero.title` / `hero.subtitle`，
 * 所以「三张图共用一句话」那种配法行为不变。
 */
const slideText = computed(() => heroSlides.value[heroSlide.value] || {})
const heroSub = computed(() => slideText.value.subtitle || site.hero?.subtitle || site.slogan || '')

/*
 * 眉标。商家自己填了就用他填的。
 *
 * 没填时拿标语顶上，但**标语已经当副标题在用的时候不顶**——
 * 同一句话在一屏里出现两次比少一个小标签难看得多。
 */
const eyebrow = computed(() => {
  const own = String(site.hero?.eyebrow || '').trim()
  if (own) return own
  const slogan = site.slogan || ''
  return slogan && heroSub.value !== slogan ? slogan : ''
})

/**
 * 把大标题按句读切成几行，最后一行会被上强调色。
 *
 * ⚠ 断句符里**故意没有 ASCII 逗号**。英文标题里的逗号是句内停顿
 * （"Silicone Tubing, Air Pumps and Packs"），按它断开会把强调色扣在半句话上；
 * 而中日文的「，、」在标题里基本就是分句用的（「流路精准，动力安静。」），
 * 不认它们的话这两种语言永远只有一行、永远没有强调色。
 *
 * 一段都切不出来（没有任何标点）时返回整句，模板那边 length===1 就不上色。
 */
function splitHeadline(text: string): string[] {
  const parts = String(text || '').match(/[^.。！!？?，、；;]+[.。！!？?，、；;]?/g) || []
  const lines = parts.map(s => s.trim()).filter(Boolean)
  return lines.length ? lines : [String(text || '')]
}
const heroLines = computed(() =>
  splitHeadline(slideText.value.title || site.hero?.title || site.companyName))

/* ==========================================================================
   版式
   --------------------------------------------------------------------------
   商家在管理台「首页装修」里选，存在 `blocks.<区块>.variant`（不分语言）。

   ⚠ 取值认不出来就退回第一种，而第一种就是「和以前一样」——
     所以一份坏数据的后果是页面照旧，不是版式乱掉。
   ⚠ 这里认的键和取值必须和 station-shared/DecorateForm.vue 的 BLOCK_VARIANTS
     一模一样。对不上的表现是「在管理台里换了版式，首页纹丝不动」。
   ========================================================================== */
function variantOf(key: string, allowed: string[]): string {
  const value = (site.blocks as Record<string, any> | undefined)?.[key]?.variant
  return allowed.includes(value) ? value : allowed[0]!
}
const heroAlign = computed(() => (site.blocks?.hero?.align === 'center' ? 'center' : 'left'))
const heroHeight = computed(() => (site.blocks?.hero?.height === 'short' ? 'short' : 'tall'))

/**
 * Banner 两个按钮点了去哪儿。2026-09-13 之前这两个目的地是写死的
 * （商品列表 / 本页询盘表单），现在商家可以在装修里填。
 *
 * 认四种写法，其余一律退回默认目的地：
 *
 *   `/products`      站内路径 —— 加当前语言前缀
 *   `/#about`        站内路径 + 锚点 —— 前缀加在 # 前面（和顶栏 navTo 一个规则）
 *   `#inquiry`       本页锚点 —— 原样，不加前缀
 *   `https://…`      外链 —— 新窗口打开
 *
 * ⚠ 最后那句「其余一律退回默认」是**安全边界**，不是容错：这个值是商家在管理台里
 *   填的，会原样进 href。不挡的话 `javascript:alert(1)` 这种写法点一下就在访客的
 *   浏览器里执行了——装修内容对所有访客可见，等于一个存储型 XSS。
 *   管理台那边也校验一遍，但**这里是真正起作用的那一道**：库里可能已经躺着
 *   老数据，也可能有人绕开界面直接调接口。
 */
type HeroBtn = { href: string; external: boolean }

function heroLink(raw: unknown, fallback: string): HeroBtn {
  const url = typeof raw === 'string' ? raw.trim() : ''
  if (!url) return { href: fallback, external: false }
  if (/^https?:\/\//i.test(url)) return { href: url, external: true }
  // 本页锚点：不加语言前缀，加了就变成「跳到另一个页面的锚点」
  if (url.startsWith('#')) return { href: url, external: false }
  if (url.startsWith('/')) {
    const [path, hash] = url.split('#')
    const prefixed = localePath(path || '/')
    return { href: hash ? `${prefixed}#${hash}` : prefixed, external: false }
  }
  // 认不出来的（javascript:、data:、mailto:、没有斜杠的相对路径…）一律退回默认。
  // 退回而不是隐藏按钮：Banner 上少一个按钮商家很难发现，而链接跳错了他一点就知道
  return { href: fallback, external: false }
}

const heroBtn1 = computed(() => heroLink(site.hero?.btn1Url, localePath('/products')))
const heroBtn2 = computed(() => heroLink(site.hero?.btn2Url, '#inquiry'))
const bentoVariant = computed(() => variantOf('bento', ['bento', 'grid3']))
const productsVariant = computed(() => variantOf('products', ['grid', 'wide', 'carousel']))
const capsVariant = computed(() => variantOf('caps', ['auto', 'g3']))
const aboutVariant = computed(() => variantOf('about', ['stagger', 'side', 'solo']))
const reachVariant = computed(() => variantOf('reach', ['map', 'list']))
const inquiryVariant = computed(() => variantOf('inquiry', ['soft', 'plain']))

/* ==================== 2026-09-21 新增的四块 ==================== */

const trustVariant = computed(() => variantOf('trust', ['bar', 'cards']))
const reviewsVariant = computed(() => variantOf('reviews', ['cards', 'strip']))
const partnersVariant = computed(() => variantOf('partners', ['named', 'logos']))
const faqVariant = computed(() => variantOf('faq', ['accordion', 'list']))

/*
 * 四块各自的条目。
 *
 * 都要过一遍「这一条填了没有」：管理台里加一条是加在所有语言上的（条目在共享层），
 * 但字是一种语言一种语言填的——某个语言还没译到这一条，它在这里就是一条只有 id
 * 的空条目。不滤掉的话，那个语言的页面上会出现一个空卡片/空问句，
 * 而商家在自己的默认语言下看一切正常，这种问题能挂很久没人发现。
 *
 * 每一块「算不算填了」的标准不一样：承诺看标题，评价看正文（没正文的星星毫无意义），
 * 合作方名字和 logo 有一个就行，问答必须两样都有（只有问题没有答案比不显示更糟）。
 */
const trustItems = computed(() => (site.trust?.items || []).filter(x => x && x.title))
const reviewItems = computed(() => (site.reviews?.items || []).filter(x => x && x.body))
const partnerItems = computed(() => (site.partners?.items || []).filter(x => x && (x.name || x.logo)))
/** 合作方视频：没有视频地址的那条（只填了说明）不出现 */
const partnerVideos = computed(() => (site.partners?.videos || []).filter(x => x && x.video))

/* ---------------- 主推商品轮播 / 合作方视频那排的左右翻页 ---------------- */
const carouselTrack = ref<HTMLElement | null>(null)
const videoTrack = ref<HTMLElement | null>(null)

/** 翻一屏：按可见宽度的 85% 滚，留一点上一屏的边，让人知道是连着的 */
function scrollTrack(el: HTMLElement | null, dir: number) {
  if (!el) return
  el.scrollBy({ left: dir * el.clientWidth * 0.85, behavior: 'smooth' })
}
const scrollCarousel = (dir: number) => scrollTrack(carouselTrack.value, dir)
const scrollVideos = (dir: number) => scrollTrack(videoTrack.value, dir)

/*
  箭头只在「真的放不下」时出现：桌面一屏四张，商品只有四个时两侧的箭头点了也不动，
  摆在那儿像坏了。放不放得下取决于屏宽，所以挂载和窗口变化时各量一次。
  服务端渲染时是 false（不出箭头），挂载后量出来再补——多一次重绘，但不会出一对假按钮。
*/
const carouselOverflow = ref(false)
const videoOverflow = ref(false)
function measureTracks() {
  const over = (el: HTMLElement | null) => !!el && el.scrollWidth > el.clientWidth + 4
  carouselOverflow.value = over(carouselTrack.value)
  videoOverflow.value = over(videoTrack.value)
}
onMounted(() => {
  measureTracks()
  window.addEventListener('resize', measureTracks, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('resize', measureTracks))

/** 轮播卡片的图：走缩略图，缩略图不在就退回原图（同商品详情页 imgAttrs） */
function imgAttrs(src: string, width: number) {
  const small = resizedImage(src, width)
  return small !== src ? { src: small, 'data-orig': src, onError: (e: Event) => fallbackToOriginal(e.target as HTMLImageElement | null) } : { src }
}

/*
  合作方视频：进入视口才播、离开就停，同时最多一条有声音。
  勾了「减少动态效果」的访客不自动播（停在封面上），点了才播。
*/
const partnerVideoEls: Array<HTMLVideoElement | null> = []
const soundOn = ref(-1)
function setPartnerVideo(i: number, el: unknown) {
  partnerVideoEls[i] = el instanceof HTMLVideoElement ? el : null
}
function toggleSound(i: number) {
  const target = partnerVideoEls[i]
  if (!target) return
  const turnOn = soundOn.value !== i
  partnerVideoEls.forEach(v => { if (v) v.muted = true })
  target.muted = !turnOn
  soundOn.value = turnOn ? i : -1
  if (target.paused) void target.play().catch(() => {})
}
let videoObserver: IntersectionObserver | null = null
onMounted(() => {
  if (!partnerVideos.value.length || typeof IntersectionObserver === 'undefined') return
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reduce) return
  videoObserver = new IntersectionObserver(entries => {
    for (const e of entries) {
      const v = e.target as HTMLVideoElement
      if (e.isIntersecting) void v.play().catch(() => {})
      else v.pause()
    }
  }, { threshold: 0.5 })
  partnerVideoEls.forEach(v => v && videoObserver!.observe(v))
})
onBeforeUnmount(() => videoObserver?.disconnect())
const faqItems = computed(() => (site.faq?.items || []).filter(x => x && x.q && x.a))

/**
 * 一行星星。
 *
 * ⚠ 向下取整不四舍五入：4.8 分画 4 颗实星 + 1 颗空星。画 5 颗实星等于把
 *   「4.8」显示成满分，而旁边就写着 4.8，自相矛盾；半颗星要另做图形，
 *   在一个商家手填分数的块里不值得。
 *   留空（没填过星级）当满分处理——商家录一条自己精选的好评不会想填 0 星。
 */
function starRow(value: unknown) {
  const raw = value === undefined || value === null || value === '' ? 5 : Number(value)
  const n = Math.max(0, Math.min(5, Math.floor(Number.isFinite(raw) ? raw : 5)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}

/**
 * 合作方 logo 挂了就不显示它，退成首字母方块（见模板）。
 *
 * 和「关于我们」那张配图同一个道理，但这里是一排图，所以得按条记——
 * 一个 broken 标记会把整排都清掉。键优先用条目自己的 id，没有 id 才用下标。
 */
const partnerBroken = ref(new Set<string>())
function markPartnerBroken(key: string | number) {
  // 换一个新的 Set，不是往原来那个里加：Set 的 add 改不动引用，模板不会重算
  partnerBroken.value = new Set(partnerBroken.value).add(String(key))
}
function partnerLogo(item: { id?: string; logo?: string }, i: number) {
  const key = String(item.id || i)
  return isUsableImageUrl(item.logo) && !partnerBroken.value.has(key) ? item.logo! : ''
}

/** logo 缺位时那个方块里的字。取名字的前两个字符——中日韩两个汉字就够认 */
function initials(name?: string) {
  const text = String(name || '').trim()
  if (!text) return '·'
  // 拉丁文名字取每个词的首字母（最多两个），其余语言直接取前两个字符
  const words = text.split(/\s+/).filter(Boolean)
  if (words.length > 1 && /^[\x20-\x7e]+$/.test(text)) {
    return (words[0]![0]! + words[1]![0]!).toUpperCase()
  }
  return text.slice(0, 2).toUpperCase()
}

/*
 * 手风琴：同时只开一条，默认开第一条。
 *
 * 「全部展开」那个版式下这套不生效——faqOpen 恒为 true，点标题也不收起来。
 * 商家选了全展开还能被点收起，是两套交互打架。
 */
const faqOpenIndex = ref(0)
function faqOpen(i: number) {
  return faqVariant.value === 'list' || faqOpenIndex.value === i
}
function toggleFaq(i: number) {
  if (faqVariant.value === 'list') return
  faqOpenIndex.value = faqOpenIndex.value === i ? -1 : i
}

/**
 * FAQPage 结构化数据。
 *
 * ⚠ 和页面上可见的那一份**必须逐字一致**，所以两边都从 `faqItems` 来。
 *   Google 对「结构化数据里有、页面上没有」的处置是人工处罚，
 *   而这种不一致最常见的来源就是有人在其中一边顺手改了个措辞。
 *
 * ⚠ 上面 reviews 那块**刻意没有**对应的 AggregateRating，理由见那一段的注释。
 *   这两块摆在一起看容易让人以为是漏了，所以在这里也写一句。
 *
 * 用 computed 包起来是为了装修预览：管理台推一份新草稿过来，头里这段也要跟着换。
 */
useHead(computed(() => ({
  script: faqItems.value.length
    ? [{
        type: 'application/ld+json',
        innerHTML: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'FAQPage',
          mainEntity: faqItems.value.map(item => ({
            '@type': 'Question',
            name: item.q,
            acceptedAnswer: { '@type': 'Answer', text: item.a }
          }))
        })
      }]
    : []
})))

/* ---------- bento ---------- */
const highlights = computed(() => (site.highlights || []).slice(0, 3))
/*
 * 粉色数字卡。
 *
 * 装修里**有它自己的一格**（`bento.stat`）：填了就用填的。没填才去借
 * 「关于我们」数字亮点的第一条——2026-09-11 之前只有借这一条路，
 * 结果同一个数字在挨着的两屏里出现两次。
 *
 * ⚠ 三等分版式下整张卡不出现，所以也不借：借了的话那条数字既不显示在这儿，
 *   又被下面「关于我们」那排排除掉——一条数字凭空消失。
 */
const bentoOwnStat = computed(() => {
  const own = site.bento?.stat
  const value = String(own?.value || '').trim()
  const label = String(own?.label || '').trim()
  return value || label ? { value, label } : null
})
const bentoStat = computed(() => {
  if (bentoVariant.value === 'grid3') return null
  return bentoOwnStat.value || site.about?.stats?.[0] || null
})
/*
 * 关于我们卡里列的数字：去掉被上面粉卡借走的那一条。
 *
 * 粉卡要的是「一个大数字」，而它只能从这一组里借（特色条目没有数字可放）。
 * 借完不排除的话，同一个「15+ 年」会在同一屏里出现两次，
 * 隔着两个区块——看不出是刻意呼应，只看得出是重复。
 *
 * 只借了才排除：数字亮点为空时粉卡不出现（bentoStat 为 null），
 * 那时这里要把四条全列出来，一条都不能少。
 */
const aboutStats = computed(() => {
  const all = site.about?.stats || []
  // 只有**借走了**的时候才排除第一条。数字卡自己填了的话，这排一条都不少
  return bentoStat.value && !bentoOwnStat.value ? all.slice(1) : all
})
// 右列有两张卡时主卡才竖跨两行；只有一张的话跨行会在右下留一个洞
const featureIsTall = computed(() => !!(bentoStat.value && highlights.value[1]))

/* og:image 用轮播的**第一张**。heroImages 已经把老的单图那种存法也算进去了，
   所以这里不用再判一次 hero.image。

   ⚠ 找的是第一张**有图**的，不是第一张。配了视频的那张，这里取到的是它的封面图——
     分享卡片里放不了视频，而封面正是这段视频最像样的一帧。整屏只有视频、
     一张封面都没配的话就退到 logo，和从前一样。 */
useSiteSeo(site, { path: '/', image: heroSlides.value.find(s => s.image)?.image || site.logo })

/*
 * 界面文案。这张表的语言必须和后端 SiteInfo.SUPPORTED_LANGS 对齐
 * （en/zh/ja/es/pt/ru/it/ko），少一种就是「切过去整页退回英文」。
 *
 * 大标题拆成 A/B 两半是为了给后半段上强调色（源站每个区块标题都是这个样式）。
 * 不写成「取最后一个词」那种自动切法：中日韩没有词间空格，切出来是错的。
 */
/*
 * 区块的内置文案。**只在商家没填那一块的标题时用**——填了就整句用他填的。
 *
 * ⚠ 这是站点代码，三个站共用（康辰 / 统领 / Esva boots），所以这里的话要**保持通用**，
 *   别写成某一个行业的口径。具体到某个站的说法走装修内容（contentjson），不走这里。
 *
 * ⚠ 加一种语言要同时改的清单里**也包括这张表**。2026-09-21 补法语时就是漏了这里
 *   （以及 products/index、products/[code]、InquiryForm、GlobalReach、blog 两页），
 *   结果是法语站开出来了、区块标题却全是英文——`COPY[site.lang] || COPY.en` 不会报错，
 *   只是悄悄退回英文，所以从日志和构建里都看不出来。
 */
const COPY: Record<string, Record<string, string>> = {
  en: { viewProducts: 'View Products', products: 'Products',
        productsLead: 'Selected items from our catalog.', viewAll: 'View complete catalog',
        strengthsA: 'What Sets Us ', strengthsB: 'Apart',
        strengthsLead: 'The details that decide whether a part works in your product.',
        offerA: 'What We ', offerB: 'Offer',
        offerLead: 'The capabilities behind every order we take.',
        aboutUs: 'About Us', aboutPill: 'Who We Are', contactUs: 'Send an Inquiry',
        contactLead: 'Tell us what you need and we will reply with a quotation.',
        reviews: 'What Our Customers Say',
        reviewsLead: 'Reviews from people who use our equipment.',
        reviewCount: '{n} customer reviews', starsLabel: 'Rated {n} out of 5',
        partners: 'Teams and Athletes',
        partnersLead: 'Clubs, national squads and professionals who work with us.',
        faq: 'Frequently Asked Questions',
        faqLead: 'The questions we are asked most.',
        previewFlag: 'Design preview · unsaved draft',
        prev: 'Previous',
        next: 'Next',
        discover: 'Discover',
        viewCollection: 'View the whole collection',
        videoSound: 'Sound on / off' },
  zh: { viewProducts: '查看产品', products: '产品',
        productsLead: '目录中的精选产品。', viewAll: '查看完整目录',
        strengthsA: '我们的', strengthsB: '不同',
        strengthsLead: '决定一个零件能不能用在你产品里的，是这些细节。',
        offerA: '我们能做', offerB: '什么',
        offerLead: '每一单背后，我们自己能做的那些事。',
        aboutUs: '关于我们', aboutPill: '我们是谁', contactUs: '发送询盘',
        contactLead: '告诉我们您的需求，我们会尽快回复报价。',
        reviews: '顾客怎么说',
        reviewsLead: '来自实际使用者的评价。',
        reviewCount: '{n} 条顾客评价', starsLabel: '5 分制 {n} 分',
        partners: '合作球队与运动员',
        partnersLead: '正在与我们合作的俱乐部、国家队和职业选手。',
        faq: '常见问题',
        faqLead: '被问得最多的那几个问题。',
        previewFlag: '装修预览 · 这是还没保存的草稿',
        prev: '上一组',
        next: '下一组',
        discover: '查看',
        viewCollection: '查看全部商品',
        videoSound: '开 / 关声音' },
  ja: { viewProducts: '製品を見る', products: '製品',
        productsLead: 'カタログからの厳選製品。', viewAll: 'すべての製品を見る',
        strengthsA: '選ばれる', strengthsB: '理由',
        strengthsLead: 'その部品が製品で使えるかどうかを決めるのは、こうした細部です。',
        offerA: '提供できる', offerB: 'こと',
        offerLead: 'すべての受注を支える自社の能力。',
        aboutUs: '会社概要', aboutPill: '私たちについて', contactUs: 'お問い合わせ',
        contactLead: 'ご要望をお知らせください。お見積りをお送りします。',
        reviews: 'お客様の声',
        reviewsLead: '実際にご利用いただいた方からの評価です。',
        reviewCount: 'お客様レビュー {n} 件', starsLabel: '5 段階評価で {n}',
        partners: 'チームとアスリート',
        partnersLead: '当社と取り組んでいるクラブ・代表チーム・プロ選手。',
        faq: 'よくあるご質問',
        faqLead: 'よくいただくご質問をまとめました。',
        previewFlag: 'デザインのプレビュー · 未保存の下書き',
        prev: '前へ',
        next: '次へ',
        discover: '詳しく見る',
        viewCollection: 'すべての製品を見る',
        videoSound: '音声のオン / オフ' },
  es: { viewProducts: 'Ver productos', products: 'Productos',
        productsLead: 'Productos seleccionados de nuestro catálogo.', viewAll: 'Ver el catálogo completo',
        strengthsA: 'Lo que nos ', strengthsB: 'distingue',
        strengthsLead: 'Los detalles que deciden si una pieza funciona en su producto.',
        offerA: 'Qué ', offerB: 'ofrecemos',
        offerLead: 'Las capacidades detrás de cada pedido.',
        aboutUs: 'Sobre nosotros', aboutPill: 'Quiénes somos', contactUs: 'Enviar consulta',
        contactLead: 'Cuéntenos qué necesita y le enviaremos una cotización.',
        reviews: 'Lo que dicen nuestros clientes',
        reviewsLead: 'Opiniones de quienes usan nuestros equipos.',
        reviewCount: '{n} opiniones de clientes', starsLabel: 'Valorado con {n} sobre 5',
        partners: 'Equipos y deportistas',
        partnersLead: 'Clubes, selecciones y profesionales que trabajan con nosotros.',
        faq: 'Preguntas frecuentes',
        faqLead: 'Lo que más nos preguntan.',
        previewFlag: 'Vista previa del diseño · borrador sin guardar',
        prev: 'Anterior',
        next: 'Siguiente',
        discover: 'Descubrir',
        viewCollection: 'Ver toda la colección',
        videoSound: 'Activar / desactivar sonido' },
  pt: { viewProducts: 'Ver produtos', products: 'Produtos',
        productsLead: 'Produtos selecionados do nosso catálogo.', viewAll: 'Ver o catálogo completo',
        strengthsA: 'O que nos ', strengthsB: 'distingue',
        strengthsLead: 'Os detalhes que decidem se uma peça funciona no seu produto.',
        offerA: 'O que ', offerB: 'oferecemos',
        offerLead: 'As capacidades por trás de cada pedido.',
        aboutUs: 'Sobre nós', aboutPill: 'Quem somos', contactUs: 'Enviar consulta',
        contactLead: 'Diga-nos o que precisa e enviaremos um orçamento.',
        reviews: 'O que dizem os nossos clientes',
        reviewsLead: 'Avaliações de quem usa os nossos equipamentos.',
        reviewCount: '{n} avaliações de clientes', starsLabel: 'Avaliado com {n} em 5',
        partners: 'Equipas e atletas',
        partnersLead: 'Clubes, seleções e profissionais que trabalham connosco.',
        faq: 'Perguntas frequentes',
        faqLead: 'O que mais nos perguntam.',
        previewFlag: 'Pré-visualização do design · rascunho não salvo',
        prev: 'Anterior',
        next: 'Seguinte',
        discover: 'Descobrir',
        viewCollection: 'Ver toda a coleção',
        videoSound: 'Ligar / desligar som' },
  it: { viewProducts: 'Vedi i prodotti', products: 'Prodotti',
        productsLead: 'Una selezione dal nostro catalogo.', viewAll: 'Vedi il catalogo completo',
        strengthsA: 'Ciò che ci ', strengthsB: 'distingue',
        strengthsLead: 'I dettagli che decidono se un componente funziona nel suo prodotto.',
        offerA: 'Cosa ', offerB: 'offriamo',
        offerLead: 'Le capacità dietro ogni ordine.',
        aboutUs: 'Chi siamo', aboutPill: 'Chi siamo', contactUs: 'Invia una richiesta',
        contactLead: 'Ci dica cosa le serve e le invieremo un preventivo.',
        reviews: 'Cosa dicono i nostri clienti',
        reviewsLead: 'Recensioni di chi usa le nostre attrezzature.',
        reviewCount: '{n} recensioni dei clienti', starsLabel: 'Valutato {n} su 5',
        partners: 'Squadre e atleti',
        partnersLead: 'Club, nazionali e professionisti che lavorano con noi.',
        faq: 'Domande frequenti',
        faqLead: 'Le domande che ci vengono poste più spesso.',
        previewFlag: 'Anteprima del layout · bozza non salvata',
        prev: 'Precedente',
        next: 'Successivo',
        discover: 'Scopri',
        viewCollection: 'Vedi tutta la collezione',
        videoSound: 'Attiva / disattiva audio' },
  ko: { viewProducts: '제품 보기', products: '제품',
        productsLead: '카탈로그에서 선별한 제품입니다.', viewAll: '전체 카탈로그 보기',
        strengthsA: '우리의 ', strengthsB: '차별점',
        strengthsLead: '부품이 제품에서 제대로 작동할지를 가르는 것은 이런 세부입니다.',
        offerA: '제공 가능한 ', offerB: '것',
        offerLead: '모든 주문을 뒷받침하는 역량.',
        aboutUs: '회사 소개', aboutPill: '회사 소개', contactUs: '문의 보내기',
        contactLead: '필요한 사항을 알려주시면 견적을 보내드립니다.',
        reviews: '고객 후기',
        reviewsLead: '실제로 사용해 본 분들이 남긴 평가입니다.',
        reviewCount: '고객 후기 {n}건', starsLabel: '5점 만점에 {n}점',
        partners: '팀과 선수',
        partnersLead: '저희와 함께하는 구단, 국가대표팀, 프로 선수들입니다.',
        faq: '자주 묻는 질문',
        faqLead: '가장 많이 받는 질문입니다.',
        previewFlag: '디자인 미리보기 · 저장되지 않은 초안',
        prev: '이전',
        next: '다음',
        discover: '자세히 보기',
        viewCollection: '전체 제품 보기',
        videoSound: '소리 켜기 / 끄기' },
  ru: { viewProducts: 'Посмотреть товары', products: 'Товары',
        productsLead: 'Избранные позиции из каталога.', viewAll: 'Весь каталог',
        strengthsA: 'Наши ', strengthsB: 'отличия',
        strengthsLead: 'Детали, от которых зависит, подойдёт ли деталь вашему изделию.',
        offerA: 'Что мы ', offerB: 'предлагаем',
        offerLead: 'Возможности, стоящие за каждым заказом.',
        aboutUs: 'О компании', aboutPill: 'О нас', contactUs: 'Отправить запрос',
        contactLead: 'Расскажите, что вам нужно, и мы пришлём предложение.',
        reviews: 'Отзывы покупателей',
        reviewsLead: 'Отзывы тех, кто пользуется нашим оборудованием.',
        reviewCount: 'Отзывов покупателей: {n}', starsLabel: 'Оценка {n} из 5',
        partners: 'Команды и спортсмены',
        partnersLead: 'Клубы, сборные и профессионалы, которые работают с нами.',
        faq: 'Частые вопросы',
        faqLead: 'То, о чём спрашивают чаще всего.',
        previewFlag: 'Предпросмотр оформления · несохранённый черновик',
        prev: 'Назад',
        next: 'Вперёд',
        discover: 'Подробнее',
        viewCollection: 'Смотреть всю коллекцию',
        videoSound: 'Звук вкл. / выкл.' },
  /* ⚠ 法语的撇号一律用排印撇号 ’（U+2019）。用直角撇号会截断这一行的单引号字符串，
     而且 TypeScript 报的错在几十行之外，很难找回这里 */
  fr: { viewProducts: 'Voir les produits', products: 'Produits',
        productsLead: 'Une sélection de notre catalogue.', viewAll: 'Voir tout le catalogue',
        strengthsA: 'Ce qui nous ', strengthsB: 'distingue',
        strengthsLead: 'Les détails qui décident si une pièce fonctionne dans votre produit.',
        offerA: 'Ce que nous ', offerB: 'proposons',
        offerLead: 'Les moyens derrière chaque commande.',
        aboutUs: 'À propos', aboutPill: 'Qui nous sommes', contactUs: 'Nous contacter',
        contactLead: 'Dites-nous ce dont vous avez besoin, nous vous répondons avec un devis.',
        reviews: 'Ce que disent nos clients',
        reviewsLead: 'Les avis de celles et ceux qui utilisent nos équipements.',
        reviewCount: '{n} avis clients', starsLabel: 'Noté {n} sur 5',
        partners: 'Équipes et athlètes',
        partnersLead: 'Clubs, équipes nationales et professionnels qui travaillent avec nous.',
        faq: 'Questions fréquentes',
        faqLead: 'Les questions qui reviennent le plus souvent.',
        previewFlag: 'Aperçu de la mise en page · brouillon non enregistré',
        prev: 'Précédent',
        next: 'Suivant',
        discover: 'Découvrir',
        viewCollection: 'Voir toute la collection',
        videoSound: 'Activer / couper le son' }
}
const t = computed(() => COPY[site.lang] || COPY.en!)
</script>

<style scoped>
/* ---------------- 预览角标 ---------------- */
/* 固定在右下角而不是顶上：顶栏是 sticky 的，压在它上面会挡住导航，
   而导航正是商家要预览的东西之一 */
.preview-flag {
  position: fixed;
  right: 14px;
  bottom: 14px;
  z-index: 999;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(10, 13, 17, .86);
  color: #fff;
  font-size: 12.5px;
  letter-spacing: .01em;
  box-shadow: 0 6px 20px rgba(10, 13, 17, .28);
  pointer-events: none;
}

/* ==================== Banner ====================
   源站的做法：整屏高、背景图上压一层**浅色**渐变蒙版、文字用深色，
   文字块靠左对齐到版心的左边缘而不是居中。 */
.hero {
  position: relative;
  overflow: hidden;
  /* 减掉 SiteHeader 的 62px（它是 sticky 的），这样首屏正好一整屏，
     往下滚才露出后面的内容 */
  height: calc(100vh - 62px);
  min-height: 520px;
  display: flex;
  align-items: center;
}
.hero-bg {
  position: absolute;
  inset: 0;
  /*
   * 多张时靠透明度交叉淡入。只有一张时它也是 is-on，opacity 1，
   * 和改造之前一模一样——单图站点看不出这里动过。
   */
  opacity: 0;
  transition: opacity .9s ease;
}
.hero-bg.is-on { opacity: 1; }
/*
 * 2026-09-13 起背景是真 <img>（理由见模板里那段）。这三行顶替原来的
 * background-position/size/repeat，排版结果一致。
 * 2026-09-21 起这一格也可能是 <video>，同一个类名——object-fit 对两者的效果一样，
 * 图换成视频不会改变构图。
 *
 * ⚠ display:block 不能省：img 默认是 inline，行盒底下会多出几像素的基线间隙，
 *   表现是 Banner 底部露出一条和页面底色不一样的细缝。
 */
.hero-bg-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
}
/* 跟随系统「减少动态效果」：不淡入，直接换。定时器那边也一并停了 */
@media (prefers-reduced-motion: reduce) {
  .hero-bg { transition: none; }
}

/* ---------------- 轮播圆点 ---------------- */
/* 位置照源站：贴着 Banner 底边居中。z-index 要压过 hero-overlay */
.hero-dots {
  position: absolute;
  left: 50%;
  bottom: 18px;
  z-index: 3;
  display: flex;
  gap: 8px;
  transform: translateX(-50%);
}
.hero-dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  /* 深底上要用白的。原来是深蓝半透明，翻成深色 Banner 之后等于隐身 */
  background: rgba(255, 255, 255, .34);
  cursor: pointer;
  transition: width .25s ease, background .25s ease;
}
.hero-dot:hover { background: rgba(255, 255, 255, .6); }
/* 当前那颗拉成小长条，不只是变深——只靠深浅的话，
   浅色背景图上根本看不出哪颗是亮的 */
.hero-dot.is-on {
  width: 22px;
  border-radius: 999px;
  background: var(--brand-blue);
}
.hero-dot:focus-visible {
  outline: 2px solid var(--brand-blue);
  outline-offset: 3px;
}
/* 恒定的深色渐变：左边压到 .82，右边完全透出图。
   商家传什么亮度的图，左侧的白字都还看得清（白字压在 .82 的墨黑上是 17.8:1）。

   ⚠ 2026-09-21 从浅色蒙版翻成深色。原来那版照的是 kangwanjia 的做法
     （浅蒙版 + 深色字），注释里写着「比深色蒙版更亮，也更禁得住亮图」——
     那个判断对上一版的紫色 spa 主题成立，对 Esva boots 不成立：
     这个品牌的 logo 就是黑底电光蓝，Banner 是浅的反而会把 logo 孤立出来。 */
.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg,
    rgba(10, 13, 17, .82) 0%,
    rgba(10, 13, 17, .78) 30%,
    rgba(10, 13, 17, .58) 48%,
    rgba(10, 13, 17, .12) 72%);
}
/* 没传 Banner 图时不留一片死白：用主题色系的柔和渐变兜底，
   蒙版也就不需要了（下面把它关掉） */
.hero--plain {
  background: radial-gradient(120% 120% at 18% 0%, #161b22 0%, #0a0d11 58%, #05070a 100%);
}
.hero--plain .hero-overlay { display: none; }

/* ---- 没有 Banner 图时的替代视觉 ----
   为什么要专门做：这一屏是 calc(100vh - 62px)，整整一屏。文案块只占左边
   640px，右边四成是空的——有图的时候那里是图，没图就是一片纯色。
   商家上线初期普遍没传 Banner（现在线上就没传），也就是说这个「降级版式」
   其实是大多数站的**常态**，不是偶发情况，值得当一个正式版式做。

   两层都是纯 CSS，不需要任何素材，商家永远不传图也成立：
     ::before  制图纸网格，向右下淡出——给空白处一点密度，不抢文字；
     ::after   两团柔光，落在右侧文案块管不到的地方，把重心拉回画面中间。

   放 .hero--plain 上而不是 .hero 上：有图时这两层会和图打架。 */
.hero--plain::before {
  content: '';
  position: absolute;
  inset: 0;
  /* 深底上要把网格调亮才看得见，.07 的深蓝在墨黑上等于没有 */
  background-image:
    linear-gradient(to right, rgba(1, 150, 250, .16) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(1, 150, 250, .16) 1px, transparent 1px);
  background-size: 46px 46px;
  /* 网格铺满整屏会像后台表格。往右下淡出，只在文字周围留一点纹理。
     两个 mask 相乘（intersect）得到「右侧淡出 + 底部淡出」 */
  -webkit-mask-image:
    linear-gradient(100deg, #000 0%, #000 34%, transparent 76%),
    linear-gradient(to bottom, #000 0%, #000 58%, transparent 100%);
  mask-image:
    linear-gradient(100deg, #000 0%, #000 34%, transparent 76%),
    linear-gradient(to bottom, #000 0%, #000 58%, transparent 100%);
  -webkit-mask-composite: source-in;
  mask-composite: intersect;
}
.hero--plain::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(34% 42% at 82% 22%, rgba(1, 150, 250, .22) 0%, rgba(1, 150, 250, 0) 100%),
    radial-gradient(30% 38% at 68% 88%, rgba(77, 193, 255, .14) 0%, rgba(77, 193, 255, 0) 100%);
}

.hero-in {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 640px;
  /* 对齐到版心左边缘，而不是居中——源站的文案块就是贴左的 */
  margin-left: max(24px, calc((100% - var(--container)) / 2));
  margin-right: auto;
  padding: 60px 24px 60px 0;
}
/* 深底上的角标：全局 .pill 是深底蓝字，压在深色 Banner 上会糊成一块。
   这里换成透明底 + 蓝边 + 蓝字，像 logo 那行标语两侧的细线 */
.hero-pill {
  margin-bottom: 22px;
  background: rgba(1, 150, 250, .12);
  border: 1px solid rgba(1, 150, 250, .45);
  color: var(--primary-light);
  box-shadow: none;
}
.hero h1 {
  color: #fff;
  font-size: clamp(2.4rem, 5vw, 4rem);
  letter-spacing: -.02em;
  margin: 0 0 20px;
}
/* 标题里被强调的半句。全局 .accent 是 --primary（为白底选的，深底上只有 3.98:1），
   深底上换成 --primary-light —— 9.63:1，而且正是 logo 那种亮蓝 */
.hero .accent { color: var(--primary-light); }
.hero p {
  color: var(--text-on-dark);
  font-size: 1.02rem;
  max-width: 480px;
  margin: 0 0 32px;
}
.hero-actions { display: flex; gap: 16px; flex-wrap: wrap; }

/* ---------------- Banner 的两个版式维度 ----------------
   对齐（左 / 居中）和高度（整屏 / 半屏）是两件独立的事，所以是两个 class，
   不是四选一：以后加一种高度不用把对齐那两种再抄一遍 */
.hero--short {
  height: 62vh;
  min-height: 420px;
}
.hero--center .hero-in {
  max-width: 760px;
  margin-left: auto;
  margin-right: auto;
  padding-left: 24px;
  text-align: center;
}
.hero--center .hero-actions { justify-content: center; }
/* ⚠ 蒙版也要跟着换。默认那层是从左往右淡出的（配合贴左的文案块），
   文案居中时它正好压在渐变透明的那一半上——浅色背景图上就看不清字了 */
.hero--center .hero-overlay {
  background: radial-gradient(120% 90% at 50% 50%,
    rgba(10, 13, 17, .88) 0%,
    rgba(10, 13, 17, .70) 46%,
    rgba(10, 13, 17, .10) 80%);
}

/* ==================== bento ==================== */
.bento {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 20px;
}
.bento-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 30px;
  box-shadow: var(--shadow-sm);
}
.bento-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--surface-muted);
  color: var(--primary);
  font-size: 1.1rem;
  margin-bottom: 16px;
}
.bento-card h3 { font-size: 1.25rem; margin: 0 0 10px; }
.bento-card p { color: var(--text-muted); font-size: .9rem; margin: 0; }

/* 主卡。竖跨两行时内容垂直居中，不然文字会顶在卡片上沿 */
.bento-feature.is-tall {
  grid-row: 1 / 3;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.bento-feature h3 { font-size: 1.5rem; }
.bento-feature p { font-size: .95rem; }

/* 数字卡。2026-09-21 从粉底换成 logo 蓝实底 + 近黑字 ——
   和主按钮同一个配色关系（6.25:1），在一排白卡里像个记分牌，
   也是整屏里唯一一处用 logo 原色铺满的地方 */
.bento-pink {
  background: var(--brand-blue);
  border-color: var(--brand-blue);
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.stat-mark { font-size: 1.3rem; color: rgba(10, 13, 17, .55); margin-bottom: 10px; }
.big-stat {
  font-family: var(--font-display);
  font-size: 3.4rem;
  font-weight: 800;
  letter-spacing: -.03em;
  color: var(--ink-dark);
  line-height: 1;
}
.stat-caption {
  text-transform: uppercase;
  letter-spacing: .12em;
  font-size: .72rem;
  font-weight: 700;
  color: rgba(10, 13, 17, .78);
  margin-top: 10px;
}

/* 深色横条，压在最后一行、横跨两列 */
.bento-dark {
  grid-column: 1 / -1;
  background: var(--ink-dark);
  border-color: var(--ink-dark);
  display: flex;
  align-items: center;
  gap: 22px;
}
.bento-dark h3 { color: #fff; }
.bento-dark p { color: rgba(255, 255, 255, .72); max-width: 720px; }
.bento-icon--light { background: rgba(255, 255, 255, .12); color: var(--primary-light); margin-bottom: 0; flex: none; }

/* ---- 三等分：三张一样大的卡 ----
   粉色数字卡在这个版式下不出现（见 script 里的 bentoStat），
   主卡也就不跨行了，所以这里只要改列数 */
.bento--grid3 { grid-template-columns: repeat(3, 1fr); }
/* 三张一样大就得连字号也一样：主卡默认比另外两张大一号，
   在错落版式里那是重点，在三等分里只显得没对齐 */
.bento--grid3 .bento-feature h3 { font-size: 1.25rem; }
.bento--grid3 .bento-feature p { font-size: .9rem; }
@media (max-width: 900px) {
  .bento--grid3 { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .bento--grid3 { grid-template-columns: 1fr; }
}

/* ==================== 商品 ==================== */
/* 两列大图。site.css 里只有 3 列和 4 列，两列只有首页这一处在用 */
.grid-2 { grid-template-columns: repeat(2, 1fr); }
@media (max-width: 700px) {
  .grid-2 { grid-template-columns: 1fr; }
}
.instruments {
  background: linear-gradient(180deg, #ecf1f6 0%, #eef4f8 100%);
  padding: 84px 0;
}
.instruments-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 40px;
}
.instruments-head h2 { font-size: clamp(1.7rem, 2.6vw, 2.3rem); margin: 0 0 10px; }
.instruments-head p { color: var(--text-muted); max-width: 520px; margin: 0; font-size: .92rem; }
.instruments-head--center { justify-content: center; text-align: center; }
.instruments-head--center p { margin: 0 auto; }

/* ---------- 横向轮播（照 getjolt.fr 那排「恢复产品」） ---------- */
/*
 * 桌面一屏四张，平板两张半，手机一张半——露出下一张的边，人才知道能往右划。
 * 原生 scroll-snap，不引轮播库：没有 JS 也能横着滚，手机上就是手指拖。
 */
.pcar { position: relative; }
.pcar-track {
  list-style: none; margin: 0; padding: 6px 4px 18px;
  display: grid; grid-auto-flow: column;
  grid-auto-columns: calc((100% - 3 * 24px) / 4);
  gap: 24px;
  overflow-x: auto; scroll-snap-type: x mandatory; scroll-behavior: smooth;
  scrollbar-width: none;
}
.pcar-track::-webkit-scrollbar { display: none; }
.pcar-card { scroll-snap-align: start; }
.pcar-link {
  display: flex; flex-direction: column; height: 100%;
  background: var(--surface); border-radius: var(--radius);
  box-shadow: 0 6px 18px rgba(10, 13, 17, .08);
  padding: 16px 16px 18px; color: var(--text); text-decoration: none;
  transition: transform .2s, box-shadow .2s;
}
.pcar-link:hover { transform: translateY(-3px); box-shadow: 0 12px 28px rgba(10, 13, 17, .14); }
.pcar-img { aspect-ratio: 1 / 1; display: grid; place-items: center; margin-bottom: 14px; }
.pcar-img img { width: 100%; height: 100%; object-fit: contain; }
/* 品类那行小字：参考站是加粗斜体，读起来像标签 */
.pcar-cat { font-weight: 800; font-style: italic; font-size: .92rem; letter-spacing: .01em; }
.pcar-title { font-weight: 500; font-size: .95rem; margin-top: 4px; line-height: 1.35; }
.pcar-cta { margin-top: auto; padding-top: 10px; font-weight: 800; color: var(--primary); }
.pcar-link:hover .pcar-cta { text-decoration: underline; }
.pcar-nav {
  position: absolute; top: 42%; z-index: 2;
  width: 42px; height: 42px; border-radius: 50%; border: 0;
  background: rgba(10, 13, 17, .55); color: #fff; font-size: 1.6rem; line-height: 1;
  display: grid; place-items: center; cursor: pointer; transition: background .2s;
}
.pcar-nav:hover { background: rgba(10, 13, 17, .8); }
.pcar-prev { left: -14px; }
.pcar-next { right: -14px; }
.pcar-more { display: flex; justify-content: center; margin-top: 30px; }
.pcar-more .btn { border-width: 2px; padding: 14px 28px; font-weight: 800; }
@media (max-width: 900px) {
  .pcar-track { grid-auto-columns: calc((100% - 24px) / 2.4); }
}
@media (max-width: 560px) {
  .pcar-track { grid-auto-columns: 72%; gap: 16px; }
  .pcar-nav { display: none; }
}

/* ==================== 能力 ==================== */
/* 固定三列。默认那套是 auto-fit：条目 4 条时会排成 4 列，
   商家要「一行就三张、第四张换行」时选这个 */
.cap-grid--g3 { grid-template-columns: repeat(3, 1fr); }
@media (max-width: 900px) {
  .cap-grid--g3 { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .cap-grid--g3 { grid-template-columns: 1fr; }
}
.caps { background: var(--bg-alt); }
/* auto-fit 而不是写死 3 列：能力条目是 3 条还是 4 条由商家在装修里定，
   写死 3 列时填 4 条就变成「一行 3 张 + 末行孤零零 1 张」，那个空缺很显眼。
   238px 是这批卡装得下标题加两行描述的最小宽度——版心 1180 下 4 条正好排一行，
   3 条铺满 3 列，5 条退成 3+2，都不留空缺。 */
.cap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(238px, 1fr));
  gap: 20px;
}
.cap-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 26px;
  box-shadow: var(--shadow-sm);
}
.cap-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--surface-muted);
  color: var(--primary);
  font-size: 1.1rem;
  margin-bottom: 14px;
}
.cap-card h3 { font-size: 1.15rem; color: var(--primary); margin: 0 0 8px; }
.cap-card p { color: var(--text-muted); font-size: .86rem; margin: 0; }
.cap-card--filled { background: var(--primary); border-color: var(--primary); }
.cap-card--filled h3 { color: #fff; }
.cap-card--filled p { color: rgba(255, 255, 255, .85); }
.cap-icon--light { background: rgba(255, 255, 255, .2); color: #fff; }

/* ==================== 关于我们 ====================
   错位叠卡。两块都留在正常流里，外层高度会跟着长，
   卡片挂在图下面也不会压到下一屏（绝对定位就会）。 */
.about-stagger { display: flex; flex-direction: column; }
.about-media {
  width: 70%;
  align-self: flex-start;
  margin: 0;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.about-media img { width: 100%; height: auto; }
.about-card {
  position: relative;
  z-index: 2;
  width: 60%;
  align-self: flex-end;
  /* 百分比外边距是按容器**宽度**算的，所以 -15% 会跟着图一起缩放：
     图占 70% 宽时高度约是容器宽的 56%，卡片正好压过它下面四分之一多一点 */
  margin-top: -15%;
  background: rgba(255, 255, 255, .92);
  backdrop-filter: blur(6px);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 44px 46px;
  box-shadow: var(--shadow);
}
/* 没配图，错位就没有意义了，退成一张整宽卡 */
.about-card.is-solo { width: 100%; align-self: stretch; margin-top: 0; }

/* ---- 左图右文 ----
   ⚠ 三条都要写：默认那套是「图 70% 靠左 + 卡片 60% 靠右往上压 15%」，
   只改 flex-direction 的话卡片还是 60% 宽、还往上压，两块会叠在一起 */
.about-stagger--side {
  flex-direction: row;
  align-items: center;
  gap: 40px;
}
.about-stagger--side .about-media { width: 46%; align-self: center; }
.about-stagger--side .about-card {
  width: 54%;
  align-self: center;
  margin-top: 0;
  padding: 36px 38px;
}
@media (max-width: 900px) {
  .about-stagger--side { flex-direction: column; gap: 0; }
  .about-stagger--side .about-media,
  .about-stagger--side .about-card { width: 100%; }
}
.about-card .pill { background: var(--surface-muted); box-shadow: none; margin-bottom: 22px; }
.about-card h2 { font-size: clamp(1.9rem, 3.4vw, 2.6rem); color: var(--primary); margin: 0 0 18px; }
.about-card p { color: var(--text-muted); margin: 0 0 16px; }
.about-card p:last-of-type { margin-bottom: 0; }
/* 第一段承接标题的分量，后面几段回到正常阅读字号 */
.about-lead { font-size: 1.06rem; color: var(--text); }

.stats { display: flex; flex-wrap: wrap; gap: 30px; margin-top: 26px; }
.stat { display: flex; flex-direction: column; }
.stat strong { font-family: var(--font-display); font-size: 1.9rem; color: var(--primary); line-height: 1.15; }
.stat span { font-size: .8rem; color: var(--text-muted); }

/* 询盘表单不铺满整个版心：一行几百像素宽的输入框看着像后台，不像联系表单 */
.inquiry-box { max-width: 720px; margin: 0 auto; }

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .hero { height: auto; min-height: 78vh; }
  .bento { grid-template-columns: 1fr; }
  .bento-feature.is-tall { grid-row: auto; }
  .bento-dark { flex-direction: column; align-items: flex-start; gap: 14px; }
  .bento-icon--light { margin-bottom: 0; }
  .instruments { padding: 60px 0; }
  .instruments-head { flex-direction: column; align-items: flex-start; }
  .cap-grid { grid-template-columns: repeat(2, 1fr); }
  /* 窄屏放不下错位：图和卡各占整宽，卡片只留一点点压叠 */
  .about-media { width: 100%; }
  .about-card { width: 100%; margin-top: -28px; padding: 32px 26px; }
}

@media (max-width: 640px) {
  .hero { min-height: 74vh; }
  /* 手机上 46px 的格子相对屏宽太粗，而且文案是整宽的，
     网格只留顶部一点点，别顶到字底下 */
  .hero--plain::before { background-size: 32px 32px; }
  .hero-in {
    margin-left: 0;
    max-width: 100%;
    padding: 40px 20px 72px;
  }
  /* 整宽文字块下改成竖向渐变：上面压得住字，下面把图透出来 */
  .hero-overlay {
    background: linear-gradient(180deg,
      rgba(10, 13, 17, .88) 0%,
      rgba(10, 13, 17, .74) 44%,
      rgba(10, 13, 17, .46) 74%,
      rgba(10, 13, 17, .18) 100%);
  }
  .hero p { font-size: .96rem; }
  .hero-actions .btn { flex: 1 1 100%; justify-content: center; }
  .cap-grid { grid-template-columns: 1fr; }
  .stats { gap: 20px 26px; }
}

/* ==================== 承诺横条 ==================== */
/* 整条贴着 Banner，所以自己不留上下外边距，padding 由两个版式各自给 */
.trust { padding: 0; }
.trust--bar { background: var(--ink-dark); color: #fff; }
.trust--bar .trust-row { padding: 24px 0; }
.trust--cards { background: var(--bg); padding: 34px 0; }
/* auto-fit 而不是写死 4 列：条目是 3 条还是 4 条由商家定，
   写死 4 列时填 3 条右边会空一格，填 5 条末行孤零零一个 */
.trust-row {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(212px, 1fr));
  gap: 18px 30px;
}
.trust-item { display: flex; align-items: flex-start; gap: 12px; }
.trust-icon { flex: none; font-size: 1.3rem; line-height: 1.35; }
.trust--bar .trust-icon { color: var(--primary-light); }
.trust--cards .trust-icon { color: var(--primary); }
/* min-width:0 —— 不给的话 grid 子项的最小宽度是内容宽度，
   一条长描述会把整行撑出版心，在窄屏上表现为横向滚动条 */
.trust-text { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.trust-text strong { font-size: .95rem; font-weight: 600; }
.trust-text span { font-size: .8rem; line-height: 1.5; }
.trust--bar .trust-text span { color: rgba(255, 255, 255, .72); }
.trust--cards .trust-text span { color: var(--text-muted); }
.trust--cards .trust-item {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px 20px;
  box-shadow: var(--shadow-sm);
}

/* ==================== 顾客评价 ==================== */
.reviews { background: var(--bg-alt); }
.score {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin: -18px 0 38px;
}
.score-value { font-family: var(--font-display); font-size: 3.2rem; line-height: 1; color: var(--primary); }
/* 星星用琥珀色而不是主题紫：五角星在所有购物站上都是这个颜色，
   换成紫色会让人多看一眼才认出这是评分 */
.score-stars, .review-stars { color: #e0a32b; letter-spacing: 2px; }
.score-stars { font-size: 1.15rem; }
.score-meta p { margin: 5px 0 0; color: var(--text-muted); font-size: .85rem; }
.review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(268px, 1fr));
  gap: 20px;
}
/* 「一行横向滚动」：条目多的时候不想让评价占掉三整行 */
.review-grid--strip {
  grid-template-columns: none;
  grid-auto-flow: column;
  grid-auto-columns: minmax(290px, 1fr);
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  padding-bottom: 10px;
}
.review-grid--strip .review-card { scroll-snap-align: start; }
.review-card {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 13px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}
.review-stars { font-size: .95rem; }
/* flex:1 让署名行贴着卡片底边：几条评价长短不一时，署名不对齐很扎眼 */
.review-card blockquote { margin: 0; flex: 1; font-size: .92rem; line-height: 1.68; color: var(--text); }
.review-card figcaption {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-top: 1px solid var(--border);
  padding-top: 13px;
}
.review-card figcaption strong { font-size: .88rem; color: var(--primary); }
.review-card figcaption span { font-size: .78rem; color: var(--text-soft); }

/* ==================== 合作方与代言人 ==================== */
.partner-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(164px, 1fr));
  gap: 18px;
}
.partner-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  text-align: center;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 22px 16px;
}
/* 队徽灰度 + 半透明：一排五颜六色的徽标会把页面本身的配色盖掉。
   鼠标移上去恢复原色，等于告诉访客这一格是可以细看的 */
.partner-card img {
  max-width: 100%;
  height: 54px;
  object-fit: contain;
  filter: grayscale(1);
  opacity: .78;
  transition: filter .2s ease, opacity .2s ease;
}
.partner-card:hover img { filter: none; opacity: 1; }
.partner-mono {
  display: inline-flex;
  width: 54px;
  height: 54px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-muted);
  color: var(--primary);
  font-family: var(--font-display);
  font-size: 1.05rem;
}
.partner-card strong { font-size: .88rem; }
.partner-card > span:not(.partner-mono) { font-size: .76rem; color: var(--text-soft); }
.partner-grid--logos .partner-card { padding: 18px 14px; }

/* ---------- 合作方视频（照 getjolt.fr 合作方下面那排竖屏短视频） ---------- */
.pvid { position: relative; margin-top: 40px; }
.pvid-track {
  list-style: none; margin: 0; padding: 4px;
  display: grid; grid-auto-flow: column; grid-auto-columns: 230px; gap: 18px;
  overflow-x: auto; scroll-snap-type: x mandatory; scrollbar-width: none;
}
.pvid-track::-webkit-scrollbar { display: none; }
/* 只有两三条时居中，不要都挤在左边留一大块空 */
.pvid-track:has(> li:nth-child(-n+3):last-child) { justify-content: center; }
.pvid-card {
  position: relative; scroll-snap-align: start;
  aspect-ratio: 9 / 16; border-radius: 14px; overflow: hidden;
  background: var(--ink-dark, #0a0d11);
  box-shadow: 0 8px 22px rgba(10, 13, 17, .18);
}
.pvid-card video { width: 100%; height: 100%; object-fit: cover; display: block; cursor: pointer; }
.pvid-sound {
  position: absolute; right: 10px; top: 10px;
  width: 34px; height: 34px; border-radius: 50%; border: 0;
  background: rgba(0, 0, 0, .45); color: #fff; font-size: .95rem; cursor: pointer;
}
.pvid-cap {
  position: absolute; left: 0; right: 0; bottom: 0;
  padding: 36px 12px 12px; color: #fff; font-weight: 700; font-size: .85rem; line-height: 1.35;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, .7));
  pointer-events: none;
}
@media (max-width: 560px) {
  .pvid-track { grid-auto-columns: 62%; }
}

/* ==================== 常见问题 ==================== */
/* 820 是一行放得下约 85 个字符的宽度。问答是要逐行读的长文，
   铺满 1180 的版心时眼睛找不到下一行的行首 */
.faq-list {
  list-style: none;
  margin: 0 auto;
  padding: 0;
  max-width: 820px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.faq-item {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}
.faq-item.is-open { border-color: var(--primary-light); box-shadow: var(--shadow-sm); }
.faq-q { margin: 0; font-size: 1rem; font-weight: 600; }
.faq-q button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  background: none;
  border: 0;
  font: inherit;
  color: var(--text);
  text-align: left;
  cursor: pointer;
}
.faq-q button:hover { color: var(--primary); }
/* 键盘焦点环。这一块是整个首页唯一要用键盘操作的东西，
   没有可见焦点的话，用 Tab 的人完全不知道自己停在哪一条上 */
.faq-q button:focus-visible { outline: 2px solid var(--primary); outline-offset: -4px; }
.faq-mark {
  display: inline-flex;
  flex: none;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-muted);
  color: var(--primary);
  font-size: 1rem;
  line-height: 1;
}
.faq-a { padding: 0 22px 20px; }
.faq-a p { margin: 0 0 10px; color: var(--text-muted); font-size: .9rem; line-height: 1.7; }
.faq-a p:last-child { margin-bottom: 0; }

@media (max-width: 640px) {
  /* 手机上一行一条：两条并排时每条只剩半屏宽，描述会折成四五行 */
  .trust-row { grid-template-columns: 1fr; }
  .score { flex-direction: column; gap: 8px; text-align: center; }
  .partner-grid { grid-template-columns: repeat(auto-fit, minmax(132px, 1fr)); }
  .faq-q button { padding: 16px 18px; }
  .faq-a { padding: 0 18px 18px; }
}
</style>

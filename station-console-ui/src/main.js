import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from '@/App.vue'
import router from '@/router'
import i18n, { initLocale, tryT } from '@/i18n'
import hasPermi from '@/directive/hasPermi'
import { identity, loadIdentity } from '@/store/identity'
import Pagination from '@/components/Pagination.vue'
import RightToolbar from '@/components/RightToolbar.vue'
import { listSiteImage, listSiteVideo, listSiteModel, listSiteFolder, uploadSiteImage } from '@/api/console'
import '@/styles/index.css'

const app = createApp(App)

/*
 * ⚠ 这里给的 zhCn 只是**兜底**，真正生效的是 App.vue 里那个 <el-config-provider>：
 *   Element Plus 自己的那些字（日期选择器的月份、分页的「条/页」、确认框的按钮）
 *   要跟着界面语言一起变，而这一行是在挂载前执行一次的，变不了。
 *   留着它是因为 ElMessage / ElMessageBox 这类**脱离组件树**调用的东西，
 *   在 config-provider 还没渲染出来之前（比如启动阶段 401 弹的那个框）读的是它。
 */
app.use(ElementPlus, { locale: zhCn })
// 模板里直接写 $t('nav.goods')，脚本里 import { t } from '@/i18n'
app.use(i18n)
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

// 这两个在 ERP 前端里是全局注册的，页面模板里直接写 <pagination> / <right-toolbar>。
// 搬过来的页面不改模板，所以这里也全局注册。
app.component('Pagination', Pagination)
app.component('RightToolbar', RightToolbar)
app.directive('hasPermi', hasPermi)

/*
 * 文件资源库的三个列表接口，注入给 station-shared/MediaPicker.vue。
 *
 * 那个组件在共用目录里，不能 import 本项目的 `@/api/console`（共用目录不许依赖某个宿主，
 * 见 station-shared/README.md）。所以由宿主在这里递进去——和 DecorateForm 的 saver
 * 是同一个路子：组件不关心接口长什么样，只关心能拿到列表。
 *
 * ⚠ 在 app 上 provide 而不是在某个页面里：用挑图的有商品、博客、首页装修三处，
 *   将来还会更多，每处各 provide 一遍迟早漏一个，而漏了的表现是弹窗空白。
 */
/*
 * 翻译器，注入给 station-shared 下那几个共用组件（MediaPicker、DecorateForm……）。
 *
 * ⚠ 递的是 tryT 不是 t：查不到键要回 undefined，让组件用它自带的中文原文兜底。
 *   理由写在 station-shared/useStationT.js 上。
 */
app.provide('stationT', tryT)

app.provide('stationMediaApi', {
  listImage: listSiteImage,
  listVideo: listSiteVideo,
  listModel: listSiteModel,
  listFolder: listSiteFolder,
  // 2026-09-22 加，给 RichTextField 的「上传图片」用。只有图片一种——
  // 详情正文里放不了视频和模型，递进去也没人调
  uploadImage: uploadSiteImage
})

app.use(router)

/**
 * 先把身份拉下来再挂载。
 *
 * 顺序不能反：顶栏要显示站点名，商品页要按站点开的语言铺文案页签——这些都来自身份。
 * 先挂载再异步补的话，第一帧会是一个「站点名空着、语言页签只有一个」的界面，
 * 然后突然跳变。多等一个往返换一个稳定的首屏，值得。
 *
 * <h3>⚠ 拉不到的时候去哪儿，变了</h3>
 * <p>这里以前是：</p>
 * <pre>
 *   .catch(() => { window.location.href = '/entry-required.html?reason=no-ticket' })
 * </pre>
 * <p>那个静态页上写着「请从 ERP 进入」——因为那时候管理台没有登录页，
 * 那一页是唯一的出口。</p>
 *
 * <p>那一页删了，取而代之的是 {@code /login} 上一个**空页面**（Entry.vue）——
 * 管理台仍然只能从 ERP 免密进来，但直接打开它的人看到的是一个什么都没有的页面，
 * 不是一句「请从众联 ERP 进入」（那是白送的情报）。</p>
 *
 * <p>所以拉不到身份不是错误：<b>照常挂载应用</b>，让路由守卫把人送到 /login。
 * 不整页跳转是有意的——整页跳会丢掉 vue-router 里那个 redirect 参数，
 * 而从 ERP 跳进来的人本来是要回到他原来那一页的。</p>
 */
loadIdentity(true)
  .catch(() => {
    // 拉不到身份不是错误，是「还没登录」。吞掉它，让下面照常挂载
  })
  .finally(() => {
    /*
      定语言，然后才挂载。

      顺序和上面那段是同一个道理，而且更不能反：法国站的站长如果先看到一帧中文
      再跳成法语，那一下足够让他以为自己点错了地方。

      identity.consoleLang 是**站点默认**（站点设置里选的）。个人在顶栏切过的话，
      initLocale 里那份 localStorage 优先——两层的分工见 i18n/index.js。
      身份没拉到时它是空的，落回中文，那是 /login 空页面的场景。
    */
    initLocale(identity.consoleLang)
    app.mount('#app')
  })

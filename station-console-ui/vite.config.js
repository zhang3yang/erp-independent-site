import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import setupExtend from 'unplugin-vue-setup-extend-plus/vite'
import { gzipSync } from 'node:zlib'
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { extname, join } from 'node:path'

/**
 * 给产物造 `.gz` 兄弟文件，供 nginx 的 `gzip_static` 直接发。
 *
 * <h3>为什么需要它</h3>
 *
 * 2026-09-23 法国客户报「从 ERP 登录进来是一片空白、转不出来」。查下来不是会话问题
 * （会话 Cookie 好好的），是**首屏体积**：管理台入口 JS 1.43 MB + CSS 357 KB，
 * 而这台服务器的 nginx 当时没压 JS/CSS（`gzip_types` 那一行是注释掉的），
 * 于是浏览器要下 **1.79 MB 未压缩**；出口又小，冷缓存十几秒，而这份 index.html
 * 连个加载动画都没有——那十几秒里是**纯白**，看着就是坏了。
 *
 * 压完约 460 KB：冷加载 18 秒 → 5 秒左右（实测对比见部署手册附录 F）。
 *
 * <h3>⚠ 两件事必须同时成立</h3>
 *
 * 1. 这里有 `.gz` 文件（本插件）；
 * 2. nginx 那边 `gzip_static on;`（服务器配置，见 deploy/nginx/station/README.md）。
 *
 * 缺任何一条，浏览器拿到的都还是原始的 1.79 MB，而且**看不出任何错**。
 *
 * <h3>为什么不引 vite-plugin-compression</h3>
 *
 * ERP 前端（RuoYi-Vue3）用的是它，这里没有照抄：这台机器**在服务器上构建**
 * （`/root/rebuild.sh`），多一个依赖就要在 2G 内存的机器上多跑一次 `npm install`。
 * 只需要 gzip 一种、只需要几个后缀，node 自带的 zlib 完全够用，零依赖。
 */
function gzipStatic(options = {}) {
  const exts = options.exts || ['.js', '.css', '.html', '.svg']
  const threshold = options.threshold || 1024
  let outDir = 'dist'
  return {
    name: 'station-gzip-static',
    apply: 'build',
    configResolved(config) {
      outDir = config.build.outDir
    },
    closeBundle() {
      let count = 0
      let saved = 0
      const walk = dir => {
        for (const name of readdirSync(dir)) {
          const file = join(dir, name)
          if (statSync(file).isDirectory()) {
            walk(file)
            continue
          }
          if (!exts.includes(extname(file))) continue
          const raw = readFileSync(file)
          if (raw.length < threshold) continue
          const gz = gzipSync(raw, { level: 9 })
          writeFileSync(file + '.gz', gz)
          count++
          saved += raw.length - gz.length
        }
      }
      walk(outDir)
      console.log(`[gzip-static] ${count} 个文件生成了 .gz，省下 ${(saved / 1024 / 1024).toFixed(2)} MB 传输`)
    }
  }
}

/**
 * 独立站管理台前端。
 *
 * 它和 ERP 前端（RuoYi-Vue3）没有任何代码依赖——需要的那几个工具和组件是复制过来的，
 * 不是引用过去的。两边可以各自演进，代价是有几百行重复代码，这个代价是值得付的：
 * 让一个跑在公网的外部系统去 import ERP 前端的东西，等于把两个部署绑死。
 *
 * ⚠ 2026-08-31 起有一个例外：@station-shared 指向仓库根目录的 station-shared/。
 *   上面那条约束仍然成立——**这里依赖的是一个中立目录，不是 RuoYi-Vue3**，
 *   两个前端谁都不 import 谁，构建和部署照旧各自独立。
 *
 *   开这个口子是因为「首页装修」这类页面两边逐字节相同，而一站多语要给它加语言 tab，
 *   不抽出来就得写两遍、而且已经漂移过一次（盘点文档 §二.3）。
 *   代价是改 station-shared 下的文件要同时验两个构建。见 station-shared/README.md。
 */
export default defineConfig({
  plugins: [
    vue(),
    // 让 <script setup name="Xxx"> 里的 name 生效，和 ERP 前端一致，
    // 复制过来的页面不用改这一行
    setupExtend({}),
    // 产物旁边生成 .gz（理由见文件顶部 gzipStatic 的注释）
    gzipStatic()
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@station-shared': fileURLToPath(new URL('../station-shared', import.meta.url)),
        // ⚠ station-shared 在两个项目根目录之外，Node 的模块解析会从它自己的位置往上找
        // node_modules——仓库根目录没有，于是 vue / element-plus 这类**裸导入**解析不到，
        // 构建直接失败。所以把这两个精确重定向到本项目的依赖上。
        //
        // 用 ^…$ 精确匹配而不是前缀匹配：只改写裸导入，vue/dist/… 之类的子路径不受影响。
        // 指向的就是本项目原本在用的那一份，不会多出第二个 Vue 实例。
      vue: fileURLToPath(new URL('./node_modules/vue', import.meta.url)),
      // 2026-09-13 加：DecorateForm 用 onBeforeRouteLeave 拦「有未保存改动就跳走」，
      // 那是 vue-router 的东西。和上面两个同一个理由、同一个办法
      'vue-router': fileURLToPath(new URL('./node_modules/vue-router', import.meta.url)),
      'element-plus': fileURLToPath(new URL('./node_modules/element-plus', import.meta.url)),
      // 2026-09-20 加：station-shared/ModelPreview.vue 用 three 画 .glb 预览。同一个理由、同一个办法。
      // 字符串别名对 'three' 和 'three/…' 子路径都生效（rollup alias 的匹配规则是
      // 全等或「前缀 + /」），所以 three/examples/jsm/… 那些也一并指到本项目的那一份
      three: fileURLToPath(new URL('./node_modules/three', import.meta.url))
    },
    dedupe: ['vue', 'vue-router', 'element-plus', 'three']
  },
  server: {
    port: 5174,
    // station-shared 在项目根目录之外，dev server 默认不许读。生产构建不受这个限制，
    // 但不放开的话 npm run dev 会 403。
    fs: { allow: ['..'] },
    proxy: {
      // 本地开发时，这两个路径转给站点后端。
      //
      // 生产环境**不用**这个代理：Nginx 把前端静态文件和后端放在同一个域名下，
      // 前端调 /api 就是同源请求。同源是这套设计的一部分——跨域的话
      // SameSite=Lax 的会话 Cookie 带不过去，还得额外处理 CORS。
      //
      // ⚠ /auth 这一条也删了：那一组（/auth/state、/auth/login、/auth/password）
      //   是 §6a 那版登录页用的，整组撤掉了。退出在 /api/auth/logout 下，
      //   走上面那条 /api。
      //
      //   本地怎么进管理台：ERP 也起在本机时，把那个站登记的「管理后台域名」填成
      //   http://127.0.0.1:5174，然后在 ERP 里点「进入管理」——表单 POST 到
      //   /sso，由下面这条转给 8081。手敲 http://127.0.0.1:5174/login 是进不来的，
      //   那一页是空的。
      '/api': { target: 'http://127.0.0.1:8081', changeOrigin: false },
      '/sso': { target: 'http://127.0.0.1:8081', changeOrigin: false }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})

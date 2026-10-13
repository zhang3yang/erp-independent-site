import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'

// 本地后端接口。业务请求不走这里——.env.development 里 VITE_APP_BASE_API 是绝对地址，
// axios 直连后端（和生产一致，都靠后端 CorsFilter 放行跨域）。这个 baseUrl 只给下面的
// springdoc 代理用。
const baseUrl = 'http://localhost:8080'

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const { VITE_APP_ENV } = env
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: VITE_APP_ENV === 'production' ? '/' : '/',
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src'),
        // 两个管理台宿主共用的页面组件，在仓库根目录的 station-shared/。
        // 它是一个中立目录，不属于任何一边——独立管理台也指向它，但两个前端谁都不 import 谁，
        // 构建和部署仍然各自独立。理由见 station-shared/README.md。
        '@station-shared': path.resolve(__dirname, '../station-shared')
      },
        // ⚠ station-shared 在两个项目根目录之外，Node 的模块解析会从它自己的位置往上找
        // node_modules——仓库根目录没有，于是 vue / element-plus 这类**裸导入**解析不到，
        // 构建直接失败。所以把这两个精确重定向到本项目的依赖上。
        //
        // 用 ^…$ 精确匹配而不是前缀匹配：只改写裸导入，vue/dist/… 之类的子路径不受影响。
        // 指向的就是本项目原本在用的那一份，不会多出第二个 Vue 实例。
      // ⚠ vue-router 是 2026-09-13 加进这张表的：DecorateForm 用了
      //   onBeforeRouteLeave。ERP 现在并不 import station-shared 的任何组件
      //   （见那个目录的 README），加在这里是为了将来真要再嵌一个页面时，
      //   不用重新踩一遍「裸导入解析不到」那个坑。
      dedupe: ['vue', 'vue-router', 'element-plus'],
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]',
          // ⚠ 这里**不要**加 manualChunks 拆包。2026-09-23 试过一版，把它撤了：
          //
          //   manualChunks(id) {
          //     … vue 全家桶 → 'vendor-vue'，element-plus → 'vendor-element-plus'
          //   }
          //
          //   后果是把 vue 那一坨拆进 vendor-vue 之后，chunk 之间排出环，模块初始化
          //   顺序错乱，浏览器一进页面就抛：
          //
          //     Uncaught ReferenceError: Cannot access 'bm' before initialization
          //       source: /static/js/vendor-vue-*.js
          //
          //   表现是**整个 ERP 前端都起不来**（不只是某一个页面）：脚本一进来就抛，
          //   app.mount('#app') 永远执行不到，屏幕上只剩 index.html 里那个「正在加载
          //   系统资源」的转圈。login、store-login 都一样。当天已经上线过一次，靠
          //   C:\build\web-dist-bak-<时间戳> 回退目录救回来的。
          //
          //   拆包换来的只是「发版后二次访问少下一坨 vendor」，首次访问一点不变快
          //   ——首屏真正变小靠的是把 quill 移出入口（见 src/main.js 里 Editor 那段
          //   异步注册），那部分保留着。
          //
          //   要再把拆包捡起来，先准备一个能**本地**验证「应用挂得上」的门禁：
          //   起静态服务跑 dist，用无头浏览器打开 /login，看 #app 有没有被挂载、
          //   控制台有没有 ReferenceError，验过再上线。
          //
          //   ⚠ 也不要加「其它 node_modules 全丢进一个 vendor」的兜底分支：入口 chunk
          //     会因此依赖那个 vendor，于是 echarts / vuedraggable 这些「只有懒加载
          //     页面才用」的库会被拽回首屏，反而变慢。没点名的一律不拆。
        }
      }
    },
    // vite 相关配置
    server: {
      port: 80,
      host: true,
      open: true,
      proxy: {
        // https://cn.vitejs.dev/config/#server-proxy
        // springdoc proxy
        '^/v3/api-docs/(.*)': {
          target: baseUrl,
          changeOrigin: true,
        }
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    }
  }
})

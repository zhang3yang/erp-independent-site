import { ref } from 'vue'

/**
 * 路由切换是否还挂着（分包在下载、守卫在等）。router 写，AppShell 读来显示进度条。
 *
 * 单独放一个文件而不是从 router 里 export：router 静态 import 了 AppShell，
 * AppShell 再反过来 import router 就成了循环依赖。
 */
export const navPending = ref(false)

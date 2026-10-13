import { inject } from 'vue'

/**
 * 共用组件的翻译出口。
 *
 * <h3>为什么不能直接 import 宿主的 i18n</h3>
 *
 * station-shared 下的东西**谁都不 import**（README 里那条）：它在两个项目根目录之外，
 * 写 `import { t } from '@/i18n'` 的话，`@` 在每个宿主里指向不同的 src，
 * 没有 i18n 的那个宿主当场构建失败。所以走 provide/inject——和 MediaPicker 的
 * `stationMediaApi`、DecorateForm 的 saver 是同一个路子：组件声明它需要什么，
 * 宿主递进来。
 *
 * <h3>宿主没提供时会怎样</h3>
 *
 * 显示**中文原文**，和加这套东西之前一模一样。调用处长这样：
 *
 *     const st = useStationT()
 *     st('picker.confirm', '确定')
 *
 * 第二个参数是中文原文，就写在调用的地方——这不是啰嗦：
 * 它让「没有翻译器的宿主」和「翻译表里漏了这个键」两种情况都落回一句人话，
 * 而不是落到 `picker.confirm` 这种键名上。共用组件不知道自己被谁用着，
 * 兜底必须自带。
 *
 * 宿主怎么提供（station-console-ui/src/main.js）：
 *
 *     app.provide('stationT', tryT)   // tryT：查不到键时返回 undefined
 */
export function useStationT() {
  const host = inject('stationT', null)

  return function st(key, zh, vars) {
    let text
    if (typeof host === 'function') {
      text = host(key, vars)
    }
    if (text === undefined || text === null || text === '') {
      text = zh
    }
    if (!vars || typeof text !== 'string') return text
    return Object.entries(vars).reduce(
      (out, [name, value]) => out.split(`{${name}}`).join(String(value)),
      text
    )
  }
}

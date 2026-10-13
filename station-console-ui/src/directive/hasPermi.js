import { hasPermi } from '@/store/identity'

/**
 * v-hasPermi 指令。从 ERP 前端搬过来的同名指令，行为保持一致，
 * 这样复制过来的页面模板不用改一个字。
 *
 * 区别只在权限从哪来：ERP 那份读 Pinia 的 user store，这份读会话带回来的 perms。
 *
 * ⚠ 藏起来的按钮不等于关上的门。真正的授权在 ERP 侧，那边每个接口都会现查权限。
 *    这个指令的作用是「别让人点了才知道不行」，不是安全边界。
 */
export default {
  mounted(el, binding) {
    if (!hasPermi(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

<!--
  装修里那种「一行一条」的小表：特色、能力、数字亮点、地图数字、目的地、导航
  六处共用这一个。列是外面传进来的（`cols`），组件只管格子和空态。

  ## 一行里有两种格子

  装修内容的分工是「**结构只存一份，文案按语言各存一份**」，而这几张表正好
  横跨两边：一条导航有几条、指向哪（共享），加上它在这个语言下叫什么（分语言）。

  所以列定义里带 `lang: true` 的那几格写进 `text[行id][列键]`，其余写进行对象本身：

      cols:  [{ k: 'url', label: '地址' }, { k: 'title', label: '名称', lang: true }]
      行:    { id: 'n1', url: '/products' }        ← 共享层，所有语言共用这一份
      text:  { n1: { title: 'Products' } }          ← 语言层，每种语言各一份

  表头上分语言的那几格带一个小标记，商家一眼能看出「切了语言只有这一格会变」。

  ## 增删交给外面

  加一行要生成 id，删一行要把**所有语言**里那一条的文案一起删掉——
  这个组件只看得见当前语言那一份，做不了第二件事，所以它只 emit `add` / `remove`。

  ⚠ 因此条目那个 prop 叫 `rows` 而不是 `modelValue`（原来是后者，组件自己 emit
  `update:modelValue` 增删，那时候还没有分语言这回事）。改名是故意的：留着
  `modelValue` 的话，外面写一个 `v-model` 会**半好用**——格子里改的字能存下
  （行对象是就地改的），加一条删一条却没反应，而这种半好用最难查。

  ## 为什么不是 el-table

  中间那一列只有 392px 宽。el-table 在这个宽度下光是单元格内边距和边框就吃掉
  一半，三列的表会横向溢出，而且它的表头自带层级——吸底的保存条被表头压住、
  「按钮看得见点不着」那个问题就是它带来的（2026-09-10 在「全球布局」那页撞到）。

  这里是一张 flex 做的格子：输入框自己没有边框，边框是格子的，
  所以一行三格看上去就是一行三格，没有嵌套控件的那层厚度。
-->
<template>
  <div>
    <div class="rows">
      <div class="row rh">
        <div v-for="c in cols" :key="c.k" class="cell" :class="c.width">
          {{ c.label }}
          <em v-if="c.lang" class="lg" :title="st('rows.langCellTip', '这一格分语言填：切到别的语言，只有这几格会变')">{{ st('rows.langCell', '文') }}</em>
        </div>
        <span class="del-sp"></span>
      </div>

      <!-- 空态写一句「所以现在会怎样」，不是一句「暂无数据」 -->
      <div v-if="!rows.length" class="row">
        <div class="cell empty">{{ emptyText || st('rows.empty', '还没有条目') }}</div>
      </div>

      <!--
        ⚠ 行对象直接改（v-model 到 row 上），只有增删才 emit：
        逐格 emit 会让每敲一个字就重建一次数组，光标会跳。
        分语言那几格同理，写的是 text 里那个对象，不整份换掉它。
      -->
      <div v-for="(row, i) in rows" :key="row.id || i" class="row">
        <div v-for="c in cols" :key="c.k" class="cell" :class="[c.width, { lang: c.lang }]">
          <!--
            多行那一档（`multiline: true`）。给常见问题的答案、顾客评价的正文用——
            那些是几句话到一整段，塞进单行输入框的话商家只能看见其中十来个字，
            而且换段（空行）在里面根本敲不出来。

            ⚠ 行高跟着内容长——用固定 rows 的话，一条长答案和一条短答案的行
              高度一样，长的那条要在一个小框里滚。
          -->
          <textarea v-if="c.lang && c.multiline" :value="textOf(row)[c.k] || ''"
            :placeholder="c.ph" :maxlength="c.max" :disabled="disabled" rows="2"
            @input="writeText(row, c.k, $event.target.value)"></textarea>
          <textarea v-else-if="c.multiline" v-model="row[c.k]" :placeholder="c.ph"
            :maxlength="c.max" :disabled="disabled" rows="2"></textarea>
          <input v-else-if="c.lang" :value="textOf(row)[c.k] || ''" type="text"
            :placeholder="c.ph" :maxlength="c.max" :disabled="disabled"
            @input="writeText(row, c.k, $event.target.value)">
          <input v-else v-model="row[c.k]" type="text" :placeholder="c.ph" :maxlength="c.max"
            :disabled="disabled">
        </div>
        <button type="button" class="del" :disabled="disabled" :title="st('rows.del', '删除这一条')"
          @click="$emit('remove', i)">✕</button>
      </div>
    </div>

    <el-button size="small" icon="Plus" :disabled="disabled || reachedMax"
      @click="$emit('add')">{{ st('rows.add', '添加一条') }}</el-button>
    <span v-if="reachedMax" class="hint">{{ st('rows.max', `最多 ${max} 条，站点上也只显示这么多`, { n: max }) }}</span>
  </div>
</template>

<script setup name="StationItemRows">
import { computed } from 'vue'
import { useStationT } from './useStationT'

const st = useStationT()

const props = defineProps({
  /** 共享层那份条目。每条都有 id（分语言的文案靠它对上），其余字段见 cols */
  rows: { type: Array, default: () => [] },
  /**
   * 列定义：`[{ k, label, width, ph, max, lang, multiline }]`。
   * `width` 传 CSS 类名（'w-ic' / 'w-num'），不传就是等分那一档；
   * `lang: true` 表示这一格分语言，写进 `text` 而不是行对象；
   * `multiline: true` 换成文本框（常见问题的答案、顾客评价的正文那种整段文字）。
   */
  cols: { type: Array, required: true },
  /**
   * 当前语言的文案袋子：`{ 行id: { 列键: 值 } }`。整张表都不分语言时不用传。
   *
   * ⚠ 这是父组件那份响应式对象，这里**就地写**（只改里面，不换整份），
   *   所以切语言只要换传进来的这一份，格子里的字就跟着变。
   */
  text: { type: Object, default: null },
  /** 站点上最多显示几条，0 表示不限。传了就在这里挡住，别让人填了却看不见 */
  max: { type: Number, default: 0 },
  /** 一条都没有时那一行灰字。说清楚「所以首页上现在是什么样」 */
  /* ⚠ 默认值是中文：没有宿主翻译器时就用它，调用方一般会自己传一句更具体的 */
  /** 不传就用内置那句（跟着界面语言走），所以默认值留空 */
  emptyText: { type: String, default: '' },
  /**
   * 只读（没有编辑权限时）。
   *
   * 得一格一格地传：这些是原生 input，不在任何 el-form 内，
   * 拿不到 el-form 那个会自动往下传的 disabled。
   */
  disabled: { type: Boolean, default: false }
})
defineEmits(['add', 'remove'])

const reachedMax = computed(() => props.max > 0 && props.rows.length >= props.max)

/** 空对象共用一份且冻起来：渲染时读到它的话，写进去的东西谁都看不见，不如直接不让写 */
const EMPTY = Object.freeze({})

const textOf = row => (props.text && row.id && props.text[row.id]) || EMPTY

/**
 * 写一格分语言的文案。
 *
 * ⚠ 袋子里那一条是**敲第一个字的时候才建**的，不是一开始就给每行每语言铺一个空对象：
 *   铺了的话，一个开了七种语言的站点里，商家只填了英文，另外六种语言的袋子里
 *   也各躺着一堆空壳——而「这个语言译了没有」正是靠袋子里有没有东西判断的
 *   （左边那一列的语言标签、以及存之前的 prune 都看这个）。
 */
function writeText(row, key, value) {
  if (!props.text || !row.id) return
  const bag = props.text[row.id] || (props.text[row.id] = {})
  bag[key] = value
}
</script>

<style scoped>
.rows {
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}
.row {
  display: flex;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.row:last-child { border-bottom: 0; }
.row.rh {
  background: var(--el-fill-color-lighter);
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-placeholder);
}
.row.rh .cell { padding: 4px 8px; }
/* 分语言那几格的表头标记。只有一个字，挤得下 */
.lg {
  display: inline-block;
  margin-left: 3px;
  padding: 0 3px;
  border-radius: 3px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  cursor: help;
}
.cell {
  flex: 1;
  min-width: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}
.cell:last-of-type { border-right: 0; }
/* 分语言那几格垫一层极淡的底：一眼看得出这一行里哪几格会跟着语言变 */
.cell.lang { background: var(--el-color-primary-light-9); }
/* 图标一格只填一个字符，数值一格是「15+」那种，都不需要等分那么宽 */
.cell.w-ic { flex: none; width: 50px; }
.cell.w-num { flex: none; width: 86px; }
.cell.empty {
  padding: 9px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.cell input, .cell textarea {
  width: 100%;
  padding: 7px 8px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
}
/* 多行那一档。行高跟着内容长（field-sizing 支持的浏览器上），
   不支持的退回两行 + 可手动拖高——两种情况下都不会把长文锁在一个小框里 */
.cell textarea {
  display: block;
  min-height: 34px;
  resize: vertical;
  field-sizing: content;
  line-height: 1.5;
}
.cell input:focus, .cell textarea:focus {
  outline: 2px solid var(--el-color-primary-light-7);
  outline-offset: -2px;
  border-radius: 4px;
}
.cell input:disabled, .cell textarea:disabled { color: var(--el-text-color-placeholder); cursor: not-allowed; }
.del, .del-sp {
  flex: none;
  width: 30px;
}
.del {
  border: 0;
  background: transparent;
  color: var(--el-text-color-placeholder);
  font: inherit;
  cursor: pointer;
}
.del:hover:not(:disabled) { color: var(--el-color-danger); }
.del:disabled { opacity: .4; cursor: not-allowed; }
.hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

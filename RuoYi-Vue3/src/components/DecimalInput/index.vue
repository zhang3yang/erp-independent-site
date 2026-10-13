<template>
  <el-input
    v-model="text"
    :placeholder="placeholder"
    :disabled="disabled"
    inputmode="decimal"
    @input="handleInput"
    @blur="normalize"
    @keyup.enter="normalize"
  />
</template>

<script setup name="DecimalInput">
import { ref, watch } from 'vue'

/**
 * 数量 / 单价输入框。
 *
 * <p>规则是「默认几位就几位，输多了按输的来」。数量传 minDecimals=0：填 1 显示 1，
 * 填 1.5 显示 1.5；单价传 minDecimals=2：填 4 显示 4.00，填 4.12345 原样留着。
 * 价格读起来要像钱，而数量写成 1.0000 只是噪音——两种都不能替人截掉多出来的位数。</p>
 *
 * <p>为什么不用 el-input-number：它没有 formatter，显示位数只认 precision 这一个死数——
 * 设成 2 就把用户多输的位数抹掉，设成 4 就把 1 显示成 1.0000。把 precision 改成跟着值走
 * 也不行：输入过程中 setCurrentValue 拿的是<b>改之前</b>那个 precision，
 * 输 4.12345 会在第一时间被四舍五入成 4.12，之后再怎么算都回不来。</p>
 *
 * <p>代价是没有上下箭头。单价和数量都是直接敲的，点箭头一格一格挪本来就没人用。</p>
 *
 * <p>v-model 收发的是数字（空值是 null），跟 el-input-number 一样，
 * 调用方不用管这里的字符串。</p>
 */
const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  /**
   * 最多几位小数，超出的位数在录入时就截掉。
   * 别等到落库时被 MySQL 截断——那样界面上显示的和账上记的对不上。
   * 按各自落库的列给：库存成本 decimal(18,6)，采购单价与数量 decimal(18,4)。
   */
  maxDecimals: { type: Number, default: 6 },
  /** 至少显示几位小数，不足补零。数量给 0，金额给 2 */
  minDecimals: { type: Number, default: 2 },
  /** 下限，失焦时把小于它的值抬上来。与 el-input-number 的 min 同义 */
  min: { type: Number, default: 0 },
  /** 上限，失焦时把超出的值压回来。不限就不传 */
  max: { type: Number, default: null },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

const text = ref('')
/** 正在输入时不要拿格式化后的值去覆盖输入框，否则打到一半光标会跳 */
const typing = ref(false)

/** 这个数写出来有几位小数。用字符串数，Number 的科学计数法在这里数不准 */
function decimalsOf(value) {
  const dot = String(value).indexOf('.')
  return dot < 0 ? 0 : String(value).length - dot - 1
}

/** 不足 minDecimals 补零，超过的按值本身来，最多 maxDecimals 位 */
function format(value) {
  if (value === null || value === undefined || value === '') return ''
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return ''
  const digits = Math.min(Math.max(props.minDecimals, decimalsOf(parsed)), props.maxDecimals)
  return parsed.toFixed(digits)
}

watch(() => props.modelValue, value => {
  if (!typing.value) text.value = format(value)
}, { immediate: true })

/**
 * 边输边收。
 *
 * <p>只留数字和一个小数点：数量和单价都不会是负数，也不该出现字母。
 * 小数位当场截到 maxDecimals，不然人一路敲下去，到保存那一刻才被后端截，
 * 屏幕上看到的和存进去的就不是一回事了。</p>
 *
 * <p>上下限不在这里卡：正在敲 <code>1</code> 准备敲 <code>12</code> 的时候，
 * 把它当场抬到下限会让人根本输不进去。留到失焦时再夹。</p>
 */
function handleInput(raw) {
  typing.value = true

  let clean = String(raw ?? '').replace(/[^\d.]/g, '')
  // 多打出来的小数点直接丢掉，只认第一个
  const dot = clean.indexOf('.')
  if (dot > -1) {
    clean = clean.slice(0, dot + 1) + clean.slice(dot + 1).replace(/\./g, '')
    clean = clean.slice(0, dot + 1 + props.maxDecimals)
  }
  // 「.5」补成「0.5」，不然 Number('.5') 虽然认，但输入框里看着像少了点什么
  if (clean.startsWith('.')) clean = '0' + clean

  text.value = clean
  // 清空才报 null；「4.」这种敲了一半的状态照 4 算，输入框里那个点留着让人接着敲
  emit('update:modelValue', clean === '' ? null : Number(clean))
  emit('change')
}

/** 离开输入框时夹到上下限之间，再补齐小数位，让这一列对得整整齐齐 */
function normalize() {
  typing.value = false

  const parsed = Number(props.modelValue)
  if (props.modelValue !== null && props.modelValue !== '' && Number.isFinite(parsed)) {
    let clamped = parsed
    if (props.min !== null && clamped < props.min) clamped = props.min
    if (props.max !== null && clamped > props.max) clamped = props.max
    if (clamped !== parsed) {
      emit('update:modelValue', clamped)
      emit('change')
      text.value = format(clamped)
      return
    }
  }
  text.value = format(props.modelValue)
}
</script>

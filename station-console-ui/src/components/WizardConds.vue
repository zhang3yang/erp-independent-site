<!--
  引导下单的条件编辑（views/Wizard.vue 用）：[{ field, in: [...] }]，多条之间是「且」。

  ⚠ 候选字段只给**排在前面的选项类字段**（由父组件算好传进来）。后端 validateSchema
    也是这条规矩——条件引用后面的字段会被拒，这里不给选就不会出现那种保存失败。
-->
<template>
  <div class="wc">
    <div v-for="(c, i) in modelValue" :key="i" class="wc-row">
      <el-select :model-value="c.field" size="small" class="wc-field" :placeholder="$t('wizard.condField')"
        :disabled="disabled" @update:model-value="v => setField(i, v)">
        <el-option v-for="f in fields" :key="f.code" :value="f.code" :label="`${f.title || f.code} (${f.code})`" />
      </el-select>
      <span class="wc-in">{{ $t('wizard.condIn') }}</span>
      <el-select :model-value="c.in" size="small" multiple collapse-tags collapse-tags-tooltip class="wc-values"
        :disabled="disabled || !c.field" @update:model-value="v => setValues(i, v)">
        <el-option v-for="o in optionsOf(c.field)" :key="o.code" :value="o.code" :label="o.title || o.code" />
      </el-select>
      <el-button v-if="!disabled" link type="danger" size="small" @click="remove(i)">×</el-button>
    </div>
    <div class="wc-foot">
      <span v-if="!modelValue.length" class="text-muted">{{ $t('wizard.condNone') }}</span>
      <el-button v-if="!disabled" link type="primary" size="small" :disabled="!fields.length" @click="add">
        + {{ $t('wizard.condAdd') }}
      </el-button>
    </div>
  </div>
</template>

<script setup name="WizardConds">
const props = defineProps({
  /** [{ field, in: [] }] */
  modelValue: { type: Array, default: () => [] },
  /** 可引用的字段：[{ code, title, options: [{ code, title }] }] */
  fields: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

function optionsOf(code) {
  return props.fields.find(f => f.code === code)?.options || []
}
function emitList(list) {
  emit('update:modelValue', list)
}
function setField(i, field) {
  const list = props.modelValue.map((c, j) => (j === i ? { field, in: [] } : c))
  emitList(list)
}
function setValues(i, values) {
  emitList(props.modelValue.map((c, j) => (j === i ? { ...c, in: values } : c)))
}
function add() {
  emitList([...props.modelValue, { field: '', in: [] }])
}
function remove(i) {
  emitList(props.modelValue.filter((_, j) => j !== i))
}
</script>

<style scoped>
.wc { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.wc-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.wc-field { width: 200px; }
.wc-values { flex: 1; min-width: 180px; }
.wc-in { color: var(--el-text-color-secondary); font-size: 12px; }
.wc-foot { display: flex; align-items: center; gap: 10px; font-size: 12px; }
</style>

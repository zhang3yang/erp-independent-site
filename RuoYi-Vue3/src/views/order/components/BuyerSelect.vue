<template>
  <div class="buyer-select">
    <template v-if="modelValue">
      <el-image v-if="picked && picked.imageUrl" :src="resolveImage(picked.imageUrl)" fit="cover" class="avatar" />
      <el-avatar v-else :size="28">{{ (displayName || '客')[0] }}</el-avatar>
      <span class="name">{{ displayName }}</span>
      <span class="code">{{ modelValue }}</span>
      <el-button link type="primary" :disabled="disabled" @click="openDialog">更换</el-button>
      <el-button link type="danger" :disabled="disabled" @click="clearPick">清除</el-button>
    </template>
    <el-button v-else type="primary" plain icon="User" :disabled="disabled" @click="openDialog">选择客户</el-button>

    <el-dialog v-model="open" title="选择客户" width="720px" append-to-body>
      <el-input v-model="keyword" placeholder="按客户名称、联系人、手机号或编码搜索" clearable class="mb8"
        @keyup.enter="load" @clear="load">
        <template #append><el-button icon="Search" @click="load">搜索</el-button></template>
      </el-input>
      <el-table v-loading="loading" :data="rows" height="360" highlight-current-row @row-dblclick="confirmRow">
        <el-table-column label="头像" width="66" align="center">
          <template #default="scope">
            <el-image v-if="scope.row.imageUrl" :src="resolveImage(scope.row.imageUrl)" fit="cover" class="avatar" />
            <el-avatar v-else :size="28">{{ (scope.row.buyerSimple || scope.row.buyerFull || '客')[0] }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="buyerCode" label="客户编码" min-width="110" />
        <el-table-column prop="buyerSimple" label="简称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="buyerFull" label="全称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" min-width="90" />
        <el-table-column prop="mobile" label="手机号" min-width="115" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="confirmRow(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 后端固定返回前 50 条：客户多了靠搜索定位，不做分页，弹窗里翻页比直接搜更慢 -->
      <div class="tip">只显示启用中的客户，最多 50 条；客户多时请用上方搜索。双击一行即可选中。</div>
      <template #footer><el-button @click="open = false">取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { buyerSelector } from '@/api/order/buyer'

const props = defineProps({
  /** 客户编码。父组件只需要维护这一个值，名称头像都由后端建单时按编码查表填。 */
  modelValue: { type: String, default: '' },
  /** 已选客户的显示名，父组件从订单快照里带进来，避免改单时打开页面显示成一串编码 */
  name: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])

const baseApi = import.meta.env.VITE_APP_BASE_API
const open = ref(false)
const loading = ref(false)
const keyword = ref('')
const rows = ref([])
const picked = ref(null)

const displayName = computed(() => {
  if (picked.value) return picked.value.buyerSimple || picked.value.buyerFull || picked.value.buyerCode
  return props.name || props.modelValue
})

// 父组件把编码清空时（比如切换到另一张单），本地缓存的客户对象也要跟着丢，
// 否则会显示上一张单的客户名。
watch(() => props.modelValue, val => {
  if (!val) picked.value = null
})

function resolveImage(url) {
  if (!url) return ''
  return /^https?:\/\//.test(url) ? url : baseApi + url
}

function openDialog() {
  open.value = true
  load()
}

function load() {
  loading.value = true
  buyerSelector(keyword.value).then(res => {
    rows.value = res.data || []
  }).finally(() => loading.value = false)
}

function confirmRow(row) {
  if (!row) return
  picked.value = row
  emit('update:modelValue', row.buyerCode)
  emit('change', row)
  open.value = false
}

function clearPick() {
  picked.value = null
  emit('update:modelValue', '')
  emit('change', null)
}
</script>

<style scoped>
.buyer-select {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
}
.name {
  font-weight: 500;
}
.code {
  color: #909399;
  font-size: 12px;
}
.tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
</style>

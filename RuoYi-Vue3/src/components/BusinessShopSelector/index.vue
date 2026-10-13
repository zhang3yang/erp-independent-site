<template>
  <div v-if="visible" class="business-shop-context">
    <div class="context-label">
      <el-icon><Shop /></el-icon>
      <span>当前管理商家</span>
    </div>
    <el-select v-model="selectedCode" filterable placeholder="请选择要管理的商家"
      :loading="loading" style="width: 320px" @change="handleChange">
      <el-option v-for="shop in shops" :key="shop.shopCode"
        :label="shop.shopName" :value="shop.shopCode" />
    </el-select>
    <el-tag v-if="businessShopStore.shopCode" type="success" effect="plain">
      平台管理员正在管理：{{ businessShopStore.shopName }}
    </el-tag>
    <span v-else class="context-tip">选择后才能查询和操作采购、库存、生产数据</span>
  </div>
</template>

<script setup>
import { listTenantAdmins } from '@/api/system/tenantAdmin'
import useBusinessShopStore from '@/store/modules/businessShop'
import useUserStore from '@/store/modules/user'

const emit = defineEmits(['change'])
const userStore = useUserStore()
const businessShopStore = useBusinessShopStore()
const loading = ref(false)
const shops = ref([])
const selectedCode = ref('')
const visible = computed(() => userStore.platformAdmin && !userStore.shopCode)

async function loadShops() {
  if (!visible.value) return
  loading.value = true
  try {
    const response = await listTenantAdmins()
    shops.value = (response.data || []).filter(shop => Number(shop.shopStatus) === 1)
    const selected = shops.value.find(shop => shop.shopCode === businessShopStore.shopCode)
    if (!selected && businessShopStore.shopCode) {
      businessShopStore.clear()
    }
    selectedCode.value = selected?.shopCode || ''
  } finally {
    loading.value = false
  }
}

function handleChange(shopCode) {
  const shop = shops.value.find(item => item.shopCode === shopCode)
  businessShopStore.selectShop(shop)
  emit('change', shop)
}

onMounted(loadShops)
</script>

<style scoped lang="scss">
.business-shop-context {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  margin: 16px 20px 0;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
}
.context-label { display: flex; align-items: center; gap: 6px; color: var(--el-color-primary); font-weight: 600; white-space: nowrap; }
.context-tip { color: var(--el-text-color-secondary); font-size: 13px; }
@media (max-width: 768px) { .business-shop-context { align-items: flex-start; flex-direction: column; } }
</style>

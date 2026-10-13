<template>
  <main class="tenant-page">
    <div class="tenant-orb tenant-orb-one"></div>
    <div class="tenant-orb tenant-orb-two"></div>

    <header class="tenant-header">
      <div>
        <p class="tenant-kicker">MULTI-TENANT WORKSPACE</p>
        <h1>选择你要进入的商家</h1>
        <p class="tenant-subtitle">
          你好，{{ userStore.nickName || userStore.name }}。当前账号可在 {{ shops.length }} 个商家中工作。
        </p>
      </div>
      <div class="header-actions">
        <el-button
          v-if="isPlatformAdmin"
          class="platform-button"
          type="primary"
          round
          :loading="openingPlatform"
          @click="openPlatformManagement"
        >
          <el-icon><OfficeBuilding /></el-icon>
          平台商家管理
        </el-button>
        <el-button class="logout-button" text @click="logout">
          <el-icon><SwitchButton /></el-icon>
          重新登录
        </el-button>
      </div>
    </header>

    <section v-loading="loading" class="tenant-content">
      <div v-if="shops.length" class="shop-grid">
        <article
          v-for="(shop, index) in shops"
          :key="shop.shopCode"
          class="shop-card"
          :class="{ 'is-entering': enteringCode === shop.shopCode }"
          :style="{ '--delay': `${index * 80}ms` }"
          tabindex="0"
          @click="enterShop(shop)"
          @keyup.enter="enterShop(shop)"
        >
          <div class="shop-card-top">
            <div class="shop-logo">
              <img v-if="shop.imageUrl" :src="resolveImage(shop.imageUrl)" :alt="shop.shopName" />
              <span v-else>{{ shop.shopName.slice(0, 1) }}</span>
            </div>
          </div>

          <div class="shop-card-body">
            <h2>{{ shop.shopName }}</h2>
            <p>{{ shop.description || '欢迎进入商家工作空间' }}</p>
            <div class="shop-meta">
              <span><el-icon><Location /></el-icon>{{ locationText(shop) }}</span>
              <span><el-icon><User /></el-icon>{{ shop.jobName || '员工' }}</span>
            </div>
          </div>

          <div class="shop-card-footer">
            <span>{{ shop.roleNames || '暂未分配商家角色' }}</span>
            <el-button type="primary" round :loading="enteringCode === shop.shopCode">
              进入商家
              <el-icon class="el-icon--right"><Right /></el-icon>
            </el-button>
          </div>
        </article>
      </div>

      <el-empty v-else-if="!loading" description="当前账号还没有加入任何正常商家">
        <el-button type="primary" @click="loadShops">重新加载</el-button>
      </el-empty>
    </section>
  </main>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, OfficeBuilding, Right, SwitchButton, User } from '@element-plus/icons-vue'
import { getTenantShops, selectPlatform, selectTenantShop } from '@/api/login'
import { isHttp } from '@/utils/validate'
import useUserStore from '@/store/modules/user'
import usePermissionStore, { installRoutes } from '@/store/modules/permission'

const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const shops = ref([])
const loading = ref(false)
const enteringCode = ref('')
const openingPlatform = ref(false)
const isPlatformAdmin = computed(() => userStore.platformAdmin)

function resolveImage(url) {
  return isHttp(url) || url.startsWith('data:') ? url : import.meta.env.VITE_APP_BASE_API + url
}

function locationText(shop) {
  return [shop.province, shop.city, shop.district].filter(Boolean).join(' / ') || '未设置地区'
}

async function loadShops() {
  loading.value = true
  try {
    const res = await getTenantShops()
    shops.value = res.shops || []
  } finally {
    loading.value = false
  }
  // 只有一个商家就没得选，替他点了。平台管理员除外：他还有「平台商家管理」这条路要挑。
  if (!isPlatformAdmin.value && shops.value.length === 1) {
    enterShop(shops.value[0])
  }
}

/**
 * 进商家。
 *
 * 这里不做「登录直达站点管理台」：ERP 登录页进来的就进 ERP（用户 2026-09-22 定）。
 * 外部客户直达站点后台走专用登录页 /store-login（views/storeLogin.vue）。
 */
async function enterShop(shop) {
  if (enteringCode.value) return
  enteringCode.value = shop.shopCode
  try {
    const res = await selectTenantShop(shop.shopCode)
    userStore.shopCode = shop.shopCode
    userStore.shopName = shop.shopName
    userStore.memberUserCode = res.shop?.memberUserCode ?? shop.memberUserCode
    userStore.tenantSelected = true
    userStore.tenantRoles = res.tenantRoles || []
    userStore.tenantPermissions = res.tenantPermissions || []
    ElMessage.success(`已进入${shop.shopName}`)
    window.location.replace(`${window.location.origin}${import.meta.env.BASE_URL}`)
  } finally {
    enteringCode.value = ''
  }
}

async function openPlatformManagement() {
  if (openingPlatform.value) return
  openingPlatform.value = true
  try {
    await selectPlatform()
    await userStore.getInfo()
    // This page bypasses the first-load guard, so install the platform routes here.
    const accessRoutes = await permissionStore.generateRoutes()
    installRoutes(accessRoutes)
    await router.push('/system/tenant-admin')
  } finally {
    openingPlatform.value = false
  }
}

function logout() {
  ElMessageBox.confirm('确定退出当前账号并重新登录吗？', '重新登录', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    closeOnClickModal: false
  }).then(async () => {
    await userStore.logOut()
    await router.replace('/login')
  }).catch(() => {})
}

loadShops()
</script>

<style lang="scss" scoped>
.tenant-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 58px clamp(24px, 6vw, 96px) 72px;
  color: #eef6ff;
  background:
    radial-gradient(circle at 76% 18%, rgba(81, 166, 255, 0.22), transparent 30%),
    radial-gradient(circle at 16% 86%, rgba(27, 207, 187, 0.16), transparent 32%),
    linear-gradient(135deg, #071b33 0%, #103b68 52%, #0b284b 100%);
}

.tenant-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(to bottom, black, transparent 86%);
}

.tenant-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  pointer-events: none;
}

.tenant-orb-one {
  top: -110px;
  right: -80px;
  width: 390px;
  height: 390px;
  background: rgba(61, 138, 255, 0.15);
  border: 1px solid rgba(142, 197, 255, 0.2);
}

.tenant-orb-two {
  bottom: -160px;
  left: 16%;
  width: 340px;
  height: 340px;
  background: rgba(36, 210, 190, 0.1);
}

.tenant-header,
.tenant-content {
  position: relative;
  z-index: 1;
  max-width: 1440px;
  margin: 0 auto;
}

.tenant-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 52px;
}

.tenant-kicker {
  margin: 0 0 14px;
  color: #63d7cb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.24em;
}

h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 58px);
  font-weight: 650;
  letter-spacing: -0.04em;
}

.tenant-subtitle {
  margin: 18px 0 0;
  color: rgba(225, 239, 255, 0.7);
  font-size: 16px;
}

.logout-button {
  color: rgba(238, 246, 255, 0.78);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.platform-button {
  border-color: rgba(99, 215, 203, 0.7);
  background: linear-gradient(135deg, #21b8a8, #2b7fd0);
  box-shadow: 0 10px 28px rgba(20, 164, 177, 0.26);
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(330px, 1fr));
  gap: 24px;
}

.shop-card {
  display: flex;
  min-height: 350px;
  flex-direction: column;
  padding: 28px;
  cursor: pointer;
  border: 1px solid rgba(185, 220, 255, 0.2);
  border-radius: 24px;
  outline: none;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.055));
  box-shadow: 0 24px 70px rgba(0, 12, 30, 0.28);
  backdrop-filter: blur(18px);
  transform: translateY(14px);
  opacity: 0;
  animation: card-in 0.6s ease forwards;
  animation-delay: var(--delay);
  transition: transform 0.25s ease, border-color 0.25s ease, background 0.25s ease;
}

.shop-card:hover,
.shop-card:focus-visible {
  transform: translateY(-6px);
  border-color: rgba(99, 215, 203, 0.62);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.18), rgba(68, 156, 230, 0.09));
}

.shop-card.is-entering {
  border-color: #63d7cb;
}

.shop-card-top,
.shop-card-footer,
.shop-meta span {
  display: flex;
  align-items: center;
}

.shop-card-top,
.shop-card-footer {
  justify-content: space-between;
  gap: 20px;
}

.shop-logo {
  display: grid;
  width: 66px;
  height: 66px;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 18px;
  background: linear-gradient(135deg, #24bfae, #2772c9);
  color: white;
  font-size: 28px;
  font-weight: 700;
}

.shop-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-card-body {
  flex: 1;
  padding: 30px 0 26px;
}

.shop-card h2 {
  margin: 0 0 12px;
  font-size: 27px;
  font-weight: 600;
}

.shop-card-body > p {
  min-height: 46px;
  margin: 0;
  color: rgba(224, 238, 252, 0.68);
  line-height: 1.7;
}

.shop-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 24px;
  color: rgba(232, 244, 255, 0.8);
  font-size: 13px;
}

.shop-meta .el-icon {
  margin-right: 6px;
  color: #63d7cb;
}

.shop-card-footer {
  padding-top: 20px;
  color: rgba(224, 238, 252, 0.64);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 13px;
}

@keyframes card-in {
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@media (max-width: 680px) {
  .tenant-page {
    padding: 32px 18px 48px;
  }

  .tenant-header {
    flex-direction: column;
    margin-bottom: 32px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .shop-grid {
    grid-template-columns: 1fr;
  }

  .shop-card {
    min-height: 330px;
    padding: 22px;
  }
}
</style>

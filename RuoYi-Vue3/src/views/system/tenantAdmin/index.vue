<template>
  <div class="app-container tenant-admin-page">
    <el-row :gutter="20">
      <el-col :xs="24" :md="9" :lg="7">
        <el-card shadow="never" class="shop-card">
          <template #header>
            <div class="card-header">
              <div>
                <strong>商家管理员</strong>
                <p>由平台超级管理员统一分配权限</p>
              </div>
              <div class="shop-header-actions">
                <el-button type="primary" icon="Plus" circle @click="openCreateShop" />
                <el-button icon="Refresh" circle @click="loadShops()" />
              </div>
            </div>
          </template>

          <el-input v-model="keyword" placeholder="搜索商家" clearable class="shop-search" />
          <div v-loading="loading" class="shop-list">
            <el-empty v-if="!filteredShops.length && !loading" description="暂无商家管理员角色" />
            <button
              v-for="shop in filteredShops"
              :key="shop.roleCode"
              type="button"
              class="shop-item"
              :class="{ active: String(selectedRoleCode) === String(shop.roleCode) }"
              @click="selectShop(shop)"
            >
              <span class="shop-avatar">{{ shop.shopName?.slice(0, 1) || '店' }}</span>
              <span class="shop-content">
                <strong>{{ shop.shopName }}</strong>
                <span>{{ shop.roleName }}</span>
              </span>
              <el-tag size="small" :type="Number(shop.shopStatus) === 1 ? 'success' : 'info'">
                {{ Number(shop.shopStatus) === 1 ? '正常' : '停用' }}
              </el-tag>
            </button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="15" :lg="17">
        <el-card v-if="selectedShop" shadow="never" class="shop-info-card">
          <div class="shop-info-row">
            <div>
              <strong>{{ selectedShop.shopName }}</strong>
              <p>{{ Number(selectedShop.shopStatus) === 1 ? '正常营业' : '已停用' }}</p>
            </div>
            <div>
              <el-button icon="Edit" @click="openEditShop">编辑商家</el-button>
              <el-button type="danger" plain icon="Delete" @click="removeShop">删除商家</el-button>
            </div>
          </div>
        </el-card>
        <el-card v-if="selectedShop" shadow="never" class="account-card">
          <template #header>
            <div class="permission-header">
              <div>
                <strong>管理员登录账号</strong>
                <p>同一登录账号可以关联多个商家；当前权限仅在所选商家内生效。</p>
              </div>
              <el-tag :type="selectedShop.adminUserCode ? 'success' : 'warning'">
                {{ selectedShop.adminUserCode ? '已创建' : '未创建' }}
              </el-tag>
            </div>
          </template>
          <div v-if="selectedShop.adminUserCode && !accountEditing" class="account-view">
            <div class="account-view-fields">
              <div><span>姓名</span><strong>{{ selectedShop.adminNickName || '-' }}</strong></div>
              <div><span>手机号</span><strong>{{ selectedShop.adminPhonenumber || '-' }}</strong></div>
            </div>
            <el-button icon="Refresh" @click="startAccountEdit">更换</el-button>
          </div>
          <el-form v-else ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="92px">
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="管理员姓名" prop="nickName">
                  <el-input v-model="accountForm.nickName" placeholder="请输入管理员姓名" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="手机号" prop="phonenumber">
                  <el-input v-model="accountForm.phonenumber" maxlength="11" placeholder="请输入管理员手机号" @input="handleAccountPhoneChange">
                    <template #append>
                      <el-button :disabled="smsCountdown > 0 || smsSending" :loading="smsSending" @click="sendAccountCode">
                        {{ smsCountdown > 0 ? `${smsCountdown}s后重试` : '获取验证码' }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="短信验证码" prop="smsCode">
                  <el-input v-model="accountForm.smsCode" maxlength="6" placeholder="请输入6位短信验证码" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" class="account-actions">
                <el-button v-if="accountEditing" :disabled="accountSaving" @click="cancelAccountEdit">取消</el-button>
                <el-button type="primary" :disabled="!/^\d{6}$/.test(accountForm.smsCode || '')" :loading="accountSaving" @click="saveAccount">
                  {{ selectedShop.adminUserCode ? '保存账号' : '创建账号' }}
                </el-button>
              </el-col>
            </el-row>
          </el-form>
        </el-card>
        <el-card shadow="never" class="permission-card">
          <template #header>
            <div class="permission-header">
              <div>
                <strong>{{ selectedShop?.shopName || '请选择商家' }}</strong>
                <p v-if="selectedShop">分配后商家管理员只能查看，不能修改自己的权限。</p>
              </div>
              <div>
                <el-button :disabled="!selectedShop || editing || saving" @click="startEdit">编辑</el-button>
                <el-button type="primary" :disabled="!editing" :loading="saving" @click="save">保存</el-button>
                <el-button :disabled="!editing || saving" @click="cancel">取消</el-button>
              </div>
            </div>
          </template>

          <el-alert
            title="这里展示 tenant:* 商家功能、purchase:* 采购功能、inventory:* 库存功能和 production:* 生产功能；平台 system:*、monitor:* 等权限不会进入商家管理员。"
            type="info"
            :closable="false"
            show-icon
            class="permission-alert"
          />
          <el-tree
            v-if="selectedShop"
            ref="permissionTreeRef"
            v-loading="treeLoading"
            :data="menus"
            :props="treeProps"
            node-key="id"
            show-checkbox
            default-expand-all
            check-strictly
          />
          <el-empty v-else description="请从左侧选择一个商家" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="shopDialogOpen" :title="shopDialogTitle" width="680px" append-to-body @closed="resetShopForm" :close-on-click-modal="false">
      <el-form ref="shopFormRef" :model="shopForm" :rules="shopRules" label-width="92px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="商家名称" prop="shopName">
              <el-input v-model="shopForm.shopName" placeholder="请输入商家名称" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="省"><el-input v-model="shopForm.province" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="市"><el-input v-model="shopForm.city" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="区县"><el-input v-model="shopForm.district" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="详细地址"><el-input v-model="shopForm.address" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="联系电话" prop="telephone"><el-input v-model="shopForm.telephone" maxlength="11" placeholder="11 位手机号" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="商家状态">
              <el-radio-group v-model="shopForm.status">
                <el-radio :value="1">正常</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="商家 Logo">
              <image-upload v-model="shopForm.imageUrl" action="/system/tenant-admin/shop/image" :limit="1" :file-size="10" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="商家描述"><el-input v-model="shopForm.description" type="textarea" :rows="3" /></el-form-item></el-col>
          <!--
            ⚠ 这里原来有一个「管理台地址」输入框（shopForm.consoleUrl），删掉了。

            它是「三级管理台地址」里的商家那一级：站点档案 → 商家档案 → 全局配置。
            docs/独立站与ERP的边界.md §4.5 把这条链整个删了：

                地址现在一站一份，从站点档案的域名派生（§7），
                全局兜底反而会让 A 站的人跳到 B 站。

            s_m_shop.console_url 那一列还在（删列不可逆），但**没有任何读取方**了。
            留着一个写进去没人读的输入框，比没有更糟——它上面那句
            「留空则用平台默认地址」现在是假话。
          -->
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="shopDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="shopSaving" @click="submitShop">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TenantAdminPermission">
import {
  createTenantShop,
  deleteTenantShop,
  getTenantShop,
  getTenantAdminPermissionTree,
  listTenantAdmins,
  sendTenantAdminAccountSmsCode,
  saveTenantAdminAccount,
  updateTenantShop,
  updateTenantAdminPermissions
} from '@/api/system/tenantAdmin'
import { mobileRule } from '@/utils/validate'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const treeLoading = ref(false)
const saving = ref(false)
const accountSaving = ref(false)
const smsSending = ref(false)
const smsCountdown = ref(0)
let smsTimer
const shopSaving = ref(false)
const shopDialogOpen = ref(false)
const shopEditing = ref(false)
const shopForm = ref({})
const editing = ref(false)
const keyword = ref('')
const shops = ref([])
const selectedRoleCode = ref()
const menus = ref([])
const checkedKeys = ref([])
const accountEditing = ref(false)
const accountForm = ref({ userName: '', nickName: '', phonenumber: '', smsCode: '' })
const accountRules = {
  userName: [{ min: 2, max: 20, message: '登录账号长度必须介于 2 和 20 之间', trigger: 'blur' }],
  nickName: [{ required: true, message: '请输入管理员姓名', trigger: 'blur' }],
  phonenumber: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    mobileRule()
  ],
  smsCode: [
    { required: true, message: '短信验证码不能为空', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入6位短信验证码', trigger: 'blur' }
  ]
}
const shopDialogTitle = computed(() => shopEditing.value ? '编辑商家' : '新增商家')
const shopRules = {
  shopCode: [],
  shopName: [{ required: true, message: '请输入商家名称', trigger: 'blur' }],
  // 联系电话按 11 位手机号校验，口径统一在 utils/validate.js 的 MOBILE_PATTERN
  telephone: [mobileRule('联系电话必须是 11 位手机号')],
}

const selectedShop = computed(() => shops.value.find(shop => String(shop.roleCode) === String(selectedRoleCode.value)))
const filteredShops = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return shops.value
  return shops.value.filter(shop => [shop.shopName, shop.shopCode, shop.roleName]
    .filter(Boolean).some(text => String(text).toLowerCase().includes(value)))
})
const treeProps = computed(() => ({
  children: 'children',
  label: 'label',
  disabled: () => !editing.value
}))

async function loadShops(preferredShopCode) {
  loading.value = true
  try {
    const response = await listTenantAdmins()
    shops.value = response.data || []
    const shop = shops.value.find(item => item.shopCode === preferredShopCode)
      || shops.value.find(item => String(item.roleCode) === String(selectedRoleCode.value)) || shops.value[0]
    if (shop) await selectShop(shop)
    else {
      selectedRoleCode.value = undefined
      menus.value = []
    }
  } finally {
    loading.value = false
  }
}

async function selectShop(shop) {
  if (editing.value) {
    proxy.$modal.msgWarning('请先保存或取消当前权限修改')
    return
  }
  clearSmsTimer()
  selectedRoleCode.value = shop.roleCode
  accountEditing.value = false
  resetAccountForm(shop)
  await loadPermissionTree()
}

function resetAccountForm(shop) {
  accountForm.value = {
    userName: shop.adminUserName || '',
    nickName: shop.adminNickName || `${shop.shopName}管理员`,
    phonenumber: shop.adminPhonenumber || '',
    smsCode: ''
  }
}

function startAccountEdit() {
  resetAccountForm(selectedShop.value)
  clearSmsTimer()
  accountEditing.value = true
}

function cancelAccountEdit() {
  resetAccountForm(selectedShop.value)
  clearSmsTimer()
  accountEditing.value = false
}

function resetShopForm() {
  shopForm.value = {
    shopCode: '', shopName: '', province: '', city: '', district: '', address: '',
    // consoleUrl 不再出现在表单里（那一级兜底删了，见上面的注释），
    // 但字段留着一个空串带上去：后端 normalizeShop 会 trimToNull 掉它，
    // 而 mapper 的 update 是直接赋值——不带这个键的话，改一次商家资料就会把
    // 库里的历史值清成 null。历史值没人读，但也没必要顺手抹掉。
    telephone: '', description: '', imageUrl: '', consoleUrl: '', status: 1
  }
  proxy.resetForm('shopFormRef')
}

function openCreateShop() {
  resetShopForm()
  shopEditing.value = false
  shopDialogOpen.value = true
}

async function openEditShop() {
  const response = await getTenantShop(selectedShop.value.shopCode)
  shopForm.value = { ...response.data, status: Number(response.data.status) }
  shopEditing.value = true
  shopDialogOpen.value = true
}

async function submitShop() {
  await proxy.$refs.shopFormRef.validate()
  shopSaving.value = true
  try {
    const response = shopEditing.value
      ? await updateTenantShop(shopForm.value.shopCode, shopForm.value)
      : await createTenantShop(shopForm.value)
    proxy.$modal.msgSuccess(shopEditing.value ? '商家信息已更新' : '商家创建成功，已初始化管理员角色')
    const targetCode = shopEditing.value ? shopForm.value.shopCode : response.shopCode
    shopDialogOpen.value = false
    await loadShops(targetCode)
  } finally {
    shopSaving.value = false
  }
}

async function removeShop() {
  const shop = selectedShop.value
  await proxy.$modal.confirm(`确定删除商家“${shop.shopName}”吗？商家部门、员工关系和角色权限将一并删除。`)
  await deleteTenantShop(shop.shopCode)
  proxy.$modal.msgSuccess('商家已删除')
  selectedRoleCode.value = undefined
  await loadShops()
}

async function saveAccount() {
  await proxy.$refs.accountFormRef.validate()
  accountSaving.value = true
  try {
    await saveTenantAdminAccount(selectedRoleCode.value, accountForm.value)
    clearSmsTimer()
    accountForm.value.smsCode = ''
    proxy.$modal.msgSuccess(selectedShop.value.adminUserCode ? '管理员账号已更新' : '管理员账号已创建')
    accountEditing.value = false
    await loadShops()
  } finally {
    accountSaving.value = false
  }
}

function clearSmsTimer() {
  if (smsTimer) {
    clearInterval(smsTimer)
    smsTimer = undefined
  }
  smsCountdown.value = 0
}

function handleAccountPhoneChange() {
  accountForm.value.smsCode = ''
  clearSmsTimer()
}

async function sendAccountCode() {
  const phone = accountForm.value.phonenumber
  if (!/^1[3-9]\d{9}$/.test(phone || '')) {
    proxy.$modal.msgWarning('请先输入正确的11位手机号')
    return
  }
  smsSending.value = true
  try {
    await sendTenantAdminAccountSmsCode(selectedRoleCode.value, { phonenumber: phone })
    accountForm.value.smsCode = ''
    smsCountdown.value = 60
    smsTimer = setInterval(() => {
      smsCountdown.value -= 1
      if (smsCountdown.value <= 0) clearSmsTimer()
    }, 1000)
    proxy.$modal.msgSuccess('验证码已发送')
  } finally {
    smsSending.value = false
  }
}

async function loadPermissionTree() {
  if (!selectedRoleCode.value) return
  treeLoading.value = true
  try {
    const response = await getTenantAdminPermissionTree(selectedRoleCode.value)
    menus.value = response.menus || []
    checkedKeys.value = response.checkedKeys || []
    await nextTick()
    restoreCheckedKeys(checkedKeys.value)
  } finally {
    treeLoading.value = false
  }
}

function startEdit() {
  editing.value = true
}

function cancel() {
  editing.value = false
  nextTick(() => restoreCheckedKeys(checkedKeys.value))
}

function restoreCheckedKeys(keys) {
  const tree = proxy.$refs.permissionTreeRef
  if (!tree) return
  tree.setCheckedKeys([])
  keys.forEach(key => tree.setChecked(key, true, false))
}

function getAllCheckedKeys() {
  const tree = proxy.$refs.permissionTreeRef
  if (!tree) return []
  return [...new Set([...tree.getCheckedKeys(), ...tree.getHalfCheckedKeys()])]
}

async function save() {
  saving.value = true
  try {
    const funCodes = getAllCheckedKeys()
    await updateTenantAdminPermissions(selectedRoleCode.value, funCodes)
    checkedKeys.value = funCodes
    editing.value = false
    proxy.$modal.msgSuccess('商家管理员权限已更新')
    await loadShops()
  } finally {
    saving.value = false
  }
}

loadShops()
onBeforeUnmount(clearSmsTimer)
</script>

<style scoped lang="scss">
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
.tenant-admin-page .el-card { border-radius: 12px; }
.card-header,
.permission-header,
.shop-item { display: flex; align-items: center; }
.shop-header-actions,
.shop-info-row { display: flex; align-items: center; gap: 10px; }
.shop-info-row { justify-content: space-between; }
.shop-info-row p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.card-header,
.permission-header { justify-content: space-between; gap: 16px; }
.card-header p,
.permission-header p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.shop-search { margin-bottom: 14px; }
.shop-list { min-height: 420px; }
.shop-item {
  width: 100%; margin-bottom: 10px; padding: 14px; cursor: pointer; text-align: left;
  border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: transparent;
}
.shop-item.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.shop-avatar {
  display: grid; width: 42px; height: 42px; flex: 0 0 42px; place-items: center;
  margin-right: 12px; border-radius: 10px; color: white; background: var(--el-color-primary); font-weight: 700;
}
.shop-content { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }
.shop-content small,
.shop-content span { color: var(--el-text-color-secondary); }
.permission-card { min-height: 570px; }
.account-card { margin-bottom: 20px; }
.shop-info-card { margin-bottom: 20px; }
.account-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; padding-bottom: 18px; }
.account-view { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.account-view-fields { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; gap: 12px 48px; }
.account-view-fields > div { display: flex; align-items: baseline; gap: 10px; }
.account-view-fields span { color: var(--el-text-color-secondary); font-size: 13px; }
.account-view-fields strong { font-size: 15px; }
.permission-alert { margin-bottom: 18px; }
@media (max-width: 992px) { .shop-card { margin-bottom: 20px; } }
</style>

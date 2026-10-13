<template>
  <div class="page">
    <!--
      ⚠ 「新建用户」那个按钮删了，所以这条提示要把「人是怎么来的」说清楚——
        不说的话，站长会在这一页找一个不存在的新建按钮。
    -->
    <el-alert type="info" show-icon :closable="false" class="mb12"
      :title="$t('users.lead.title')"
      :description="$t('users.lead.desc')" />

    <el-form :inline="true" :model="query" class="mb8">
      <el-form-item :label="$t('users.keyword')">
        <el-input v-model="query.keyword" clearable :placeholder="$t('users.keywordPlaceholder')" style="width: 180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <!-- ⚠ value 是库里的中文串（u_s_user.status），只翻 label -->
        <el-select v-model="query.status" clearable style="width: 120px">
          <el-option :label="$t('users.status.active')" value="正常" />
          <el-option :label="$t('users.status.disabled')" value="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="load">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="reset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="username" :label="$t('users.col.username')" min-width="140" />
      <el-table-column prop="nickname" :label="$t('users.col.nickname')" min-width="120" />
      <el-table-column :label="$t('users.col.role')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.roleCode === 'admin' ? 'danger' : 'info'" effect="plain">
            {{ roleName(row.roleCode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('users.col.source')" width="150">
        <template #default="{ row }">
          <!--
            正常情况下这一列全是「ERP 免密」。
            「本站账号」是**历史遗留**：本站有过一版账号密码登录，站长能手工建账号。
            那一版撤掉了，那些行留在库里但已经进不来——摆出来是为了让站长看得见
            「这个人为什么一直没登录记录」。
          -->
          <el-tooltip v-if="row.source === 'erpsso'" placement="top"
            :content="$t('users.source.erpTip')">
            <el-tag size="small" type="warning" effect="plain">{{ $t('users.source.erp') }}</el-tag>
          </el-tooltip>
          <el-tooltip v-else placement="top"
            :content="$t('users.source.legacyTip')">
            <el-tag size="small" type="info" effect="plain">{{ $t('users.source.legacy') }}</el-tag>
          </el-tooltip>
          <span v-if="row.erpUser" class="erp-user" :title="row.erpUser">{{ row.erpUser }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === '停用' ? 'info' : 'success'">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" :label="$t('users.col.lastLogin')" width="170" />
      <el-table-column prop="loginCount" :label="$t('users.col.loginCount')" width="90" align="right" />
      <!-- ⚠ 「重置密码」删了：本站不存密码。见 SessionController 那段注释 -->
      <el-table-column :label="$t('common.operate')" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!--
      ===================== 修改 =====================
      只有「修改」，没有「新建」：账号是从 ERP 进来时自动落的（见页顶那条提示）。
    -->
    <el-dialog v-model="formOpen" :title="$t('users.edit.title')" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item :label="$t('users.col.username')">
          <!--
            登录名不允许改：它是这个人的身份，而历史操作记录（createuser / handleuser /
            updateuser 那些列）里存的都是旧名字，改完对不上。
          -->
          <el-input v-model="form.username" maxlength="64" disabled />
          <div class="hint">{{ $t('users.edit.usernameHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('users.col.nickname')">
          <el-input v-model="form.nickname" maxlength="100" :placeholder="$t('users.edit.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('users.col.role')" required>
          <el-select v-model="form.roleCode" style="width:100%">
            <el-option v-for="r in roles" :key="r.roleCode" :label="roleName(r.roleCode)" :value="r.roleCode">
              <span>{{ roleName(r.roleCode) }}</span>
              <span class="role-remark">{{ r.remark }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="正常">{{ $t('users.status.active') }}</el-radio>
            <el-radio value="停用">{{ $t('users.status.disabled') }}</el-radio>
          </el-radio-group>
          <div class="hint">
            <span v-html="$t('users.edit.disabledHint')"></span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Users">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSiteUser, listSiteRole, updateSiteUser, delSiteUser } from '@/api/console'
import { t } from '@/i18n'

const loading = ref(false), saving = ref(false)
const rows = ref([])
const roles = ref([])
const query = reactive({ keyword: '', status: '' })
const formOpen = ref(false)
const form = ref({})

function load() {
  loading.value = true
  listSiteUser(query)
    .then(r => { rows.value = r.rows || [] })
    .finally(() => { loading.value = false })
}

function reset() {
  query.keyword = ''
  query.status = ''
  load()
}

/*
  角色名。出厂那两个角色（admin / operator）在这里翻，其余自定义角色显示库里那个名字。

  ⚠ 不能一律显示 roleName：那一列是后端 u_s_role 里的中文（「站长」「运营」），
    英文界面上会突兀地冒出两个中文标签——而这两个恰恰是绝大多数站唯一用到的角色。
*/
const BUILTIN_ROLE_KEY = { admin: 'role.admin', operator: 'role.operator' }
const roleName = code => (BUILTIN_ROLE_KEY[code]
  ? t(BUILTIN_ROLE_KEY[code])
  : (roles.value.find(r => r.roleCode === code)?.roleName || code))

/* 账号状态。⚠ 库里存的是「正常」「停用」两个中文串，只翻显示 */
const statusLabel = s => (s === '停用' ? t('users.status.disabled') : (s === '正常' ? t('users.status.active') : s))

// ⚠ 没有 openCreate：本站建不出账号，人是从 ERP 跳进来时自动落的。
// 也没有 resetPassword：本站不存密码。两个都随那一版登录页一起删了。

function openEdit(row) {
  form.value = { ...row }
  formOpen.value = true
}

function save() {
  saving.value = true
  updateSiteUser(form.value)
    .then(() => {
      ElMessage.success(t('users.msg.saved'))
      formOpen.value = false
      load()
    })
    .finally(() => { saving.value = false })
}

function remove(row) {
  ElMessageBox.confirm(
    t('users.msg.removeConfirm', { name: row.username }),
    t('common.confirmTitle'), { type: 'warning', dangerouslyUseHTMLString: true, closeOnClickModal: false })
    .then(() => delSiteUser(row.id))
    .then(() => { ElMessage.success(t('common.deleted')); load() })
    .catch(() => {})
}

onMounted(() => {
  listSiteRole().then(r => { roles.value = r.data || [] })
  load()
})
</script>

<style scoped>
.page {
  padding: 16px;
}
.mb8 { margin-bottom: 8px; }
.mb12 { margin-bottom: 12px; }
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}
/* ERP 账号名跟在来源标签后面，一串英文，窄屏上截断 */
.erp-user {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: Consolas, Monaco, monospace;
}
.role-remark {
  float: right;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

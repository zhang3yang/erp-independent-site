<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="客户档案供销售订单选择使用"
      description="订单会保存下单当时的客户名称和图片快照，之后在这里改名换头像不会影响历史订单。已经有成交记录的客户请停用，不要删除。" />

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="客户名称" prop="buyerSimple">
        <el-input v-model="queryParams.buyerSimple" placeholder="简称或全称" clearable style="width: 160px"
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="queryParams.mobile" placeholder="请输入手机号" clearable style="width: 150px"
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="联系人" prop="contactPerson">
        <el-input v-model="queryParams.contactPerson" placeholder="请输入联系人" clearable style="width: 130px"
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户编码" prop="buyerCode">
        <el-input v-model="queryParams.buyerCode" placeholder="请输入客户编码" clearable style="width: 150px"
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="星级" prop="starRating">
        <el-select v-model="queryParams.starRating" placeholder="全部" clearable style="width: 100px">
          <el-option v-for="n in [0, 1, 2, 3, 4, 5]" :key="n" :label="`${n} 星`" :value="n" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="activeFlag">
        <el-select v-model="queryParams.activeFlag" placeholder="全部" clearable style="width: 100px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['order:buyer:add']" @click="openForm()">新增客户</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="selected.length !== 1"
          v-hasPermi="['order:buyer:edit']" @click="openForm(selected[0])">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!selected.length"
          v-hasPermi="['order:buyer:remove']" @click="handleDelete()">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" v-hasPermi="['order:buyer:export']"
          @click="handleExport">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>客户列表</span><span class="sub">点击一行查看它的二级客户</span></div>
          </template>
          <el-table ref="buyerTableRef" v-loading="loading" :data="buyerList" highlight-current-row
            @current-change="selectBuyer" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" align="center" />
            <el-table-column label="头像" width="62" align="center">
              <template #default="scope">
                <el-image v-if="scope.row.imageUrl" :src="resolveImage(scope.row.imageUrl)" fit="cover" class="avatar"
                  :preview-src-list="[resolveImage(scope.row.imageUrl)]" preview-teleported />
                <el-avatar v-else :size="32">{{ (scope.row.buyerSimple || scope.row.buyerFull || '客')[0] }}</el-avatar>
              </template>
            </el-table-column>
            <el-table-column prop="buyerCode" label="客户编码" min-width="110" />
            <el-table-column prop="buyerSimple" label="简称" min-width="110" show-overflow-tooltip />
            <el-table-column prop="contactPerson" label="联系人" min-width="85" />
            <el-table-column prop="mobile" label="手机号" min-width="115" />
            <el-table-column prop="buyer2Count" label="二级客户" width="85" align="center">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.buyer2Count > 0 ? 'primary' : 'info'">
                  {{ scope.row.buyer2Count || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="星级" width="120">
              <template #default="scope"><el-rate :model-value="scope.row.starRating || 0" disabled size="small" /></template>
            </el-table-column>
            <el-table-column label="状态" width="72" align="center">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.activeFlag === 1 ? 'success' : 'info'">
                  {{ scope.row.activeFlag === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="scope">
                <el-button link type="primary" v-hasPermi="['order:buyer:query']" @click.stop="openDetail(scope.row)">详情</el-button>
                <el-button link type="primary" v-hasPermi="['order:buyer:edit']" @click.stop="openForm(scope.row)">修改</el-button>
                <el-button link type="danger" v-hasPermi="['order:buyer:remove']" @click.stop="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize" @pagination="getList" />
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ current ? `“${currentName}”的二级客户` : '二级客户' }}</span>
              <el-button type="primary" plain icon="Plus" :disabled="!current"
                v-hasPermi="['order:buyer:add']" @click="openSubForm()">新增</el-button>
            </div>
          </template>
          <el-empty v-if="!current" description="请先点击左侧客户" :image-size="80" />
          <template v-else>
            <el-form :inline="true" :model="subQuery" class="mb8">
              <el-form-item label="名称">
                <el-input v-model="subQuery.buyer2Name" clearable style="width: 140px" @keyup.enter="loadSubs" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Search" @click="loadSubs">搜索</el-button>
              </el-form-item>
            </el-form>
            <el-table v-loading="subLoading" :data="subList">
              <el-table-column label="头像" width="62" align="center">
                <template #default="scope">
                  <el-image v-if="scope.row.imageUrl" :src="resolveImage(scope.row.imageUrl)" fit="cover" class="avatar" />
                  <el-avatar v-else :size="32">{{ (scope.row.buyer2Name || '店')[0] }}</el-avatar>
                </template>
              </el-table-column>
              <el-table-column prop="buyer2Name" label="名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="buyer2User" label="联系人" min-width="90" />
              <el-table-column prop="mobile" label="电话" min-width="110" />
              <el-table-column label="状态" width="72" align="center">
                <template #default="scope">
                  <el-tag size="small" :type="scope.row.activeFlag === 1 ? 'success' : 'info'">
                    {{ scope.row.activeFlag === 1 ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="115" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" v-hasPermi="['order:buyer:edit']" @click="openSubForm(scope.row)">修改</el-button>
                  <el-button link type="danger" v-hasPermi="['order:buyer:remove']" @click="handleSubDelete(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <pagination v-show="subTotal > 0" :total="subTotal" v-model:page="subQuery.pageNum"
              v-model:limit="subQuery.pageSize" @pagination="loadSubs" />
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="open" :title="form.buyerCode ? '修改客户' : '新增客户'" width="760px" append-to-body>
      <el-form ref="buyerRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户简称" prop="buyerSimple">
              <el-input v-model="form.buyerSimple" maxlength="50" placeholder="订单上显示的名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户全称" prop="buyerFull">
              <el-input v-model="form.buyerFull" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contactPerson" maxlength="50" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="form.mobile" maxlength="11" placeholder="11 位手机号，商户内唯一，可不填" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="固定电话" prop="telephone"><el-input v-model="form.telephone" maxlength="11" placeholder="11 位手机号，可不填" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.activeFlag">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地址"><el-input v-model="form.address" maxlength="200" /></el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <!-- 业务员固定是当前登录人，不给改。新增时后端按登录人写死，
                 修改时后端保留原值，这里只做展示，传什么都不算数。 -->
            <el-form-item label="业务员">
              <el-input :model-value="form.buyerCode ? (form.staffName || '—') : currentStaffName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="星级">
              <el-rate v-model="form.starRating" :max="5" show-score score-template="{value} 星" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="客户头像"><image-upload v-model="form.imageUrl" :limit="1" :file-size="10" /></el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="subOpen" :title="subForm.buyer2Code ? '修改二级客户' : '新增二级客户'" width="600px" append-to-body>
      <el-form ref="subRef" :model="subForm" :rules="subRules" label-width="90px">
        <el-form-item label="所属客户">
          <el-input :model-value="currentName" disabled />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="名称" prop="buyer2Name">
              <el-input v-model="subForm.buyer2Name" maxlength="100" placeholder="如：XX 路门店" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="subForm.buyer2User" maxlength="50" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电话" prop="mobile"><el-input v-model="subForm.mobile" maxlength="11" placeholder="11 位手机号，可不填" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="subForm.activeFlag">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地址"><el-input v-model="subForm.address" maxlength="200" /></el-form-item>
        <el-form-item label="头像"><image-upload v-model="subForm.imageUrl" :limit="1" :file-size="10" /></el-form-item>
        <el-form-item label="备注">
          <el-input v-model="subForm.remark" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subOpen = false">取消</el-button>
        <el-button type="primary" :loading="subSaving" @click="submitSubForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="客户详情" width="680px" append-to-body>
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="客户编码">{{ detail.buyerCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="detail.activeFlag === 1 ? 'success' : 'info'">
            {{ detail.activeFlag === 1 ? '启用' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户简称">{{ detail.buyerSimple || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户全称">{{ detail.buyerFull || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.mobile || '-' }}</el-descriptions-item>
        <!-- 买家账号一期没有录入入口（表单上去掉了），这里也就不列，免得永远显示一个横杠 -->
        <el-descriptions-item label="固定电话">{{ detail.telephone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务员">{{ detail.staffName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="星级">
          <el-rate :model-value="detail.starRating || 0" disabled size="small" />
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="二级客户">{{ detail.buyer2Count || 0 }} 个</el-descriptions-item>
        <el-descriptions-item label="历史订单">
          <el-tag :type="detail.orderCount > 0 ? 'warning' : 'info'" size="small">
            {{ detail.orderCount || 0 }} 笔
          </el-tag>
          <span v-if="detail.orderCount > 0" class="hint">有成交记录，请用停用代替删除</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="Buyer">
import {
  listBuyers, getBuyer, addBuyer, updateBuyer, deleteBuyer,
  listBuyer2, addBuyer2, updateBuyer2, deleteBuyer2
} from '@/api/order/buyer'
import useUserStore from '@/store/modules/user'
import { mobileRule } from '@/utils/validate'

const { proxy } = getCurrentInstance()
const baseApi = import.meta.env.VITE_APP_BASE_API

// 业务员就是当前登录人，只用于新增表单上的展示；真正落库的值由后端取登录态填
const userStore = useUserStore()
const currentStaffName = computed(() => userStore.nickName || userStore.name)

const loading = ref(false)
const saving = ref(false)
const showSearch = ref(true)
const open = ref(false)
const detailOpen = ref(false)
const buyerList = ref([])
const selected = ref([])
const detail = ref(null)
const total = ref(0)
const form = ref({})
const buyerTableRef = ref()

// 当前选中的主客户，右侧二级客户列表跟着它走
const current = ref(null)
const subList = ref([])
const subLoading = ref(false)
const subTotal = ref(0)
const subOpen = ref(false)
const subSaving = ref(false)
const subForm = ref({})

const currentName = computed(() =>
  current.value ? (current.value.buyerSimple || current.value.buyerFull || current.value.buyerCode) : '')

const queryParams = reactive({
  pageNum: 1, pageSize: 20,
  buyerCode: undefined, buyerSimple: undefined, mobile: undefined,
  contactPerson: undefined, starRating: undefined, activeFlag: undefined
})

const subQuery = reactive({ pageNum: 1, pageSize: 20, buyerCode: undefined, buyer2Name: undefined })

// 简称和全称在后端是「至少填一个」，两个输入框共用一个校验器
const rules = {
  buyerSimple: [{ validator: validateName, trigger: 'blur' }],
  buyerFull: [{ validator: validateName, trigger: 'blur' }],
  // 手机号和固定电话都按 11 位手机号校验，口径统一在 utils/validate.js 的 MOBILE_PATTERN
  mobile: [mobileRule('手机号必须是 11 位手机号')],
  telephone: [mobileRule('固定电话必须是 11 位手机号')]
}
const subRules = {
  buyer2Name: [{ required: true, message: '请输入二级客户名称', trigger: 'blur' }],
  mobile: [mobileRule('电话必须是 11 位手机号')]
}

function validateName(rule, value, callback) {
  if (!form.value.buyerSimple && !form.value.buyerFull) {
    callback(new Error('客户简称和全称至少填一个'))
    return
  }
  callback()
}

// 后端存的是 /profile/upload/... 这样的相对路径，要拼上网关前缀才能显示
function resolveImage(url) {
  if (!url) return ''
  return /^https?:\/\//.test(url) ? url : baseApi + url
}

function getList() {
  loading.value = true
  listBuyers(queryParams).then(res => {
    buyerList.value = res.rows || []
    total.value = res.total || 0
    keepCurrent()
  }).finally(() => loading.value = false)
}

// 列表刷新后行对象被整体替换，el-table 的当前行高亮会丢，按编码重新选中
function keepCurrent() {
  const code = current.value?.buyerCode
  if (!code) return
  const row = buyerList.value.find(b => b.buyerCode === code)
  if (!row) {
    // 当前客户被筛掉或删掉了，右侧一并清空，免得停在一个已经不存在的客户上
    current.value = null
    subList.value = []
    subQuery.buyerCode = undefined
    return
  }
  current.value = row
  nextTick(() => buyerTableRef.value?.setCurrentRow(row))
}

function selectBuyer(row) {
  if (!row) return
  const changed = subQuery.buyerCode !== row.buyerCode
  current.value = row
  if (!changed) return
  subQuery.buyerCode = row.buyerCode
  subQuery.pageNum = 1
  subQuery.buyer2Name = undefined
  loadSubs()
}

function loadSubs() {
  if (!subQuery.buyerCode) return
  subLoading.value = true
  listBuyer2(subQuery).then(res => {
    subList.value = res.rows || []
    subTotal.value = res.total || 0
  }).finally(() => subLoading.value = false)
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.starRating = undefined
  queryParams.activeFlag = undefined
  handleQuery()
}

function handleSelectionChange(rows) {
  selected.value = rows
}

function openForm(row) {
  // 客户编码、买家账号、店铺图暂不在表单上露出：编码由后端生成，
  // 另两个一期用不到，字段留在库里，等要用时把表单项加回来即可。
  // 业务员不在这里给值——新增由后端取登录人，修改由后端保留原值。
  form.value = row
    ? { ...row }
    : {
        buyerCode: '', buyerSimple: '', buyerFull: '', contactPerson: '', mobile: '',
        telephone: '', address: '', starRating: 0,
        imageUrl: '', remark: '', activeFlag: 1
      }
  open.value = true
  nextTick(() => proxy.$refs.buyerRef?.clearValidate())
}

function submitForm() {
  proxy.$refs.buyerRef.validate(valid => {
    if (!valid) return
    const editing = !!form.value.buyerCode
    saving.value = true
    ;(editing ? updateBuyer : addBuyer)(form.value).then(res => {
      // 新增走的是 success(msg)，后端把生成的客户编码放在 msg 里
      proxy.$modal.msgSuccess(editing ? '客户修改成功' : (res.msg || '客户创建成功'))
      open.value = false
      getList()
    }).finally(() => saving.value = false)
  })
}

function openSubForm(row) {
  if (!current.value) return
  subForm.value = row
    ? { ...row }
    : {
        buyer2Code: '', buyerCode: current.value.buyerCode, buyer2Name: '', buyer2User: '',
        mobile: '', address: '', imageUrl: '', remark: '', activeFlag: 1
      }
  subOpen.value = true
  nextTick(() => proxy.$refs.subRef?.clearValidate())
}

function submitSubForm() {
  proxy.$refs.subRef.validate(valid => {
    if (!valid) return
    // 归属永远取当前选中的主客户；后端修改时也不认这个字段，以库里的为准
    subForm.value.buyerCode = current.value.buyerCode
    const editing = !!subForm.value.buyer2Code
    subSaving.value = true
    ;(editing ? updateBuyer2 : addBuyer2)(subForm.value).then(res => {
      proxy.$modal.msgSuccess(editing ? '二级客户修改成功' : (res.msg || '二级客户创建成功'))
      subOpen.value = false
      loadSubs()
      // 左侧那一列「二级客户数」要跟着变
      getList()
    }).finally(() => subSaving.value = false)
  })
}

function openDetail(row) {
  getBuyer(row.buyerCode).then(res => {
    detail.value = res.data
    detailOpen.value = true
  })
}

function handleDelete(row) {
  const rows = row ? [row] : selected.value
  const codes = rows.map(r => r.buyerCode)
  const subCount = rows.reduce((sum, r) => sum + (r.buyer2Count || 0), 0)
  const label = rows.length === 1
    ? `客户「${rows[0].buyerSimple || rows[0].buyerFull || codes[0]}」`
    : `选中的 ${codes.length} 个客户`
  const subTip = subCount > 0 ? `名下 ${subCount} 个二级客户会一并删除。` : ''
  proxy.$modal.confirm(`确认删除${label}吗？${subTip}已有历史订单的客户不能删除，请改为停用。`)
    .then(() => deleteBuyer(codes.join(',')))
    .then(() => { proxy.$modal.msgSuccess('删除成功'); getList() })
    .catch(() => {})
}

function handleSubDelete(row) {
  proxy.$modal.confirm(`确认删除二级客户「${row.buyer2Name}」吗？历史订单上的快照不受影响。`)
    .then(() => deleteBuyer2(row.buyer2Code))
    .then(() => { proxy.$modal.msgSuccess('删除成功'); loadSubs(); getList() })
    .catch(() => {})
}

function handleExport() {
  proxy.download('order/buyer/export', { ...queryParams }, `buyer_${new Date().getTime()}.xlsx`)
}

getList()
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-header .sub {
  color: #909399;
  font-size: 12px;
  font-weight: normal;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}
.hint {
  margin-left: 8px;
  color: #e6a23c;
  font-size: 12px;
}
</style>

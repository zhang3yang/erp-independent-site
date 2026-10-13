<!--
  选择商品对话框。

  左侧是商品分类树，结构就是「库存管理 → 基础设置 → 商品分类」维护的那棵树，
  右侧是该分类（含其下级分类）下的商品列表，勾选后确定，一次可以选多个。
  搜索框按商品编码或名称模糊匹配，搜索范围是当前选中的树节点，
  默认停在「全部商品」上，所以不动树直接搜就是全库搜。

  勾选状态自己维护（不用 el-table 的 type="selection"），翻页、切分类、改搜索
  都不会丢，底部能看到已选清单并逐个移除。

  用法：
    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="codes" @confirm="onGoodsSelected" />
    proxy.$refs['goodsSelectRef'].open()
-->
<template>
  <el-dialog title="选择商品" v-model="visible" width="90%" top="6vh" append-to-body
    :close-on-click-modal="false" class="goods-select-dialog">
    <div class="goods-select-body">
      <div class="goods-select-tree">
        <div class="panel-title">商品分类</div>
        <div class="tree-scroll" v-loading="treeLoading">
          <el-tree ref="treeRef" :data="treeData" node-key="id" highlight-current
            :expand-on-click-node="false"
            :current-node-key="currentKey" @node-click="handleNodeClick">
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon class="node-icon">
                  <Menu v-if="data.nodeType === 'all'" />
                  <Folder v-else />
                </el-icon>
                <span class="node-label" :title="data.path || data.label">{{ data.label }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>

      <div class="goods-select-main">
        <div class="goods-select-toolbar">
          <el-input v-model="keyword" placeholder="输入商品名称或编码模糊查询" clearable size="small"
            style="width: 280px" @keyup.enter="handleSearch" @clear="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" size="small" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button size="small" icon="Refresh" @click="handleReset">重置</el-button>
          <span class="toolbar-scope">当前范围：{{ scopeLabel }}</span>
        </div>

        <el-table ref="tableRef" v-loading="loading" :data="goodsList" border stripe height="480"
          empty-text="没有符合条件的商品" :row-class-name="rowClassName" @row-click="toggleRow">
          <el-table-column width="50" align="center">
            <template #header>
              <el-checkbox :model-value="pageAllChecked" :indeterminate="pageIndeterminate"
                :disabled="!selectableRows.length || !multiple" @change="togglePage" />
            </template>
            <template #default="scope">
              <el-checkbox :model-value="isChecked(scope.row.goodsCode)"
                :disabled="isExcluded(scope.row.goodsCode)" @change="toggleRow(scope.row)"
                @click.stop />
            </template>
          </el-table-column>
          <el-table-column label="商品编码" prop="goodsCode" width="150" align="center" show-overflow-tooltip />
          <!-- 这里整行点击即勾选，所以图片只作辨认用，不挂点击放大，免得和选择抢点击 -->
          <el-table-column label="主图" width="80" align="center">
            <template #default="scope">
              <el-image v-if="goodsImageUrl(scope.row)" :src="goodsImageUrl(scope.row)"
                fit="cover" class="goods-thumb">
                <template #error>
                  <div class="thumb-slot"><el-icon><PictureFilled /></el-icon></div>
                </template>
              </el-image>
              <span v-else class="no-image">未上传</span>
            </template>
          </el-table-column>
          <el-table-column label="商品名称" prop="goodsName" min-width="280" align="center" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.goodsName }}</span>
              <el-tag v-if="isExcluded(scope.row.goodsCode)" type="info" size="small"
                effect="plain" class="added-tag">已添加</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分类" prop="category" min-width="200" align="center" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.category || '未分类' }}</template>
          </el-table-column>
          <el-table-column label="规格型号" prop="spec" min-width="150" align="center" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.spec || '无型号' }}</template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="70" align="center" />
          <el-table-column label="类型" prop="goodsType" width="90" align="center">
            <template #default="scope">
              <el-tag :type="goodsTypeTag(scope.row.goodsType)" size="small">{{ goodsTypeLabel(scope.row.goodsType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale" label="参考价" prop="standardPrice" width="130" align="center">
            <template #default="scope">{{ formatCurrency(scope.row.standardPrice) }}</template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize" :page-sizes="[10, 20, 50]" :auto-scroll="false"
          @pagination="getList" />
      </div>
    </div>

    <div class="goods-select-selected">
      <div class="selected-head">
        <span>已选 {{ selected.length }} 项</span>
        <el-button v-if="selected.length" link type="primary" @click="clearSelected">清空</el-button>
      </div>
      <div class="selected-tags">
        <el-tag v-for="item in selected" :key="item.goodsCode" closable size="small" disable-transitions
          @close="removeSelected(item.goodsCode)">
          {{ item.goodsName }}（{{ item.goodsCode }}）
        </el-tag>
        <span v-if="!selected.length" class="selected-empty">在上方勾选商品</span>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :disabled="!selected.length" @click="handleConfirm">确 定</el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="GoodsSelectDialog">
import { computed, nextTick, ref } from 'vue'
import { formatCurrency } from '@/utils/currency'
import { canSeeSale } from '@/utils/price'
import { goodsImageUrl } from '@/utils/goodsImage'
import { listGoodsByCategory } from '@/api/inventory/goods'
import { listCategoryTree } from '@/api/inventory/goodsCategory'

const props = defineProps({
  /** 是否允许多选，单选时选中一个会替换掉之前的 */
  multiple: { type: Boolean, default: true },
  /** 只显示启用的商品；停用商品不该被开进新单据 */
  activeOnly: { type: Boolean, default: true },
  /** 明细里已有的商品编码，打上「已添加」并锁住，不允许再选一次 */
  excludeCodes: { type: Array, default: () => [] }
})

const emit = defineEmits(['confirm'])

const visible = ref(false)
const loading = ref(false)
const treeLoading = ref(false)
const treeRef = ref(null)
const treeData = ref([])
const currentKey = ref('all')
const currentNode = ref(null)
const goodsList = ref([])
const selected = ref([])
const keyword = ref('')
const total = ref(0)

const queryParams = ref({
  pageNum: 1,
  pageSize: 20
})

const activeFlag = computed(() => (props.activeOnly ? 1 : undefined))

const scopeLabel = computed(() => {
  const node = currentNode.value
  if (!node || node.nodeType === 'all') return '全部商品'
  return node.path || node.label
})

/** 明细里已经有的商品，本次不能再选 */
const excludedSet = computed(() => new Set(props.excludeCodes || []))

/** 当前页里还能选的行，整页全选/取消只作用在这些行上 */
const selectableRows = computed(() => goodsList.value.filter(row => !isExcluded(row.goodsCode)))

/* 表头状态跟着行上的勾走：已添加的行显示成勾选，所以也算进来，
   否则整页都是已添加时，行都勾着、表头却是空的 */
const pageAllChecked = computed(() =>
  goodsList.value.length > 0 && goodsList.value.every(row => isChecked(row.goodsCode)))

const pageIndeterminate = computed(() =>
  goodsList.value.some(row => isChecked(row.goodsCode)) && !pageAllChecked.value)

/** 打开弹窗，每次都从「全部商品」重新开始，不残留上一次的勾选 */
function open() {
  visible.value = true
  selected.value = []
  keyword.value = ''
  currentKey.value = 'all'
  currentNode.value = null
  queryParams.value.pageNum = 1
  getTree()
  getList()
}

/** 分类主数据在「库存管理 → 基础设置 → 商品分类」维护，这里原样取那棵树 */
function getTree() {
  treeLoading.value = true
  listCategoryTree().then(response => {
    treeData.value = [{ id: 'all', label: '全部分类', nodeType: 'all' }, ...toTreeNodes(response.data, '')]
    treeLoading.value = false
    nextTick(() => treeRef.value?.setCurrentKey(currentKey.value))
  }).catch(() => {
    treeLoading.value = false
  })
}

/**
 * 分类树转成 el-tree 要的结构。
 * 商品表存的是分类名称，所以查询用 category（名称）而不是编码；
 * path 是从根拼下来的全路径，只用来显示当前范围和悬浮提示。
 */
function toTreeNodes(list, parentPath) {
  return (list || []).map(item => {
    const path = parentPath ? `${parentPath} / ${item.categoryName}` : item.categoryName
    return {
      id: item.categoryCode,
      label: item.categoryName,
      category: item.categoryName,
      nodeType: 'category',
      path,
      children: toTreeNodes(item.children, path)
    }
  })
}

function getList() {
  loading.value = true
  const node = currentNode.value
  listGoodsByCategory({
    pageNum: queryParams.value.pageNum,
    pageSize: queryParams.value.pageSize,
    // 选中父分类时后端会把下级分类一并展开，所以点哪一级都能看到该级以下的全部商品
    category: node && node.nodeType === 'category' ? node.category : undefined,
    keyword: keyword.value || undefined,
    activeFlag: activeFlag.value
  }).then(response => {
    goodsList.value = response.rows || []
    total.value = response.total || 0
    loading.value = false
  }).catch(() => {
    goodsList.value = []
    total.value = 0
    loading.value = false
  })
}

function handleNodeClick(data) {
  currentNode.value = data
  currentKey.value = data.id
  queryParams.value.pageNum = 1
  getList()
}

function handleSearch() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置：清掉搜索词并回到「全部商品」，已勾选的商品保留 */
function handleReset() {
  keyword.value = ''
  currentKey.value = 'all'
  currentNode.value = null
  treeRef.value?.setCurrentKey('all')
  queryParams.value.pageNum = 1
  getList()
}

function isSelected(goodsCode) {
  return selected.value.some(item => item.goodsCode === goodsCode)
}

function isExcluded(goodsCode) {
  return excludedSet.value.has(goodsCode)
}

/** 复选框的显示状态：明细里已有的商品也勾上（并置灰），一眼就能看出这行已经选过了 */
function isChecked(goodsCode) {
  return isSelected(goodsCode) || isExcluded(goodsCode)
}

/** 已添加的行整行置灰，并去掉「可点击」的手型，免得看着像能选 */
function rowClassName({ row }) {
  return isExcluded(row.goodsCode) ? 'row-excluded' : ''
}

function toggleRow(row) {
  // 明细里已经有这个商品了，整行锁住：点行、点复选框都不响应
  if (isExcluded(row.goodsCode)) {
    return
  }
  const index = selected.value.findIndex(item => item.goodsCode === row.goodsCode)
  if (index > -1) {
    selected.value.splice(index, 1)
    return
  }
  if (!props.multiple) {
    selected.value = [row]
    return
  }
  selected.value.push(row)
}

/** 表头复选框：整页全选 / 整页取消，已添加的行跳过 */
function togglePage(checked) {
  if (checked) {
    selectableRows.value.forEach(row => {
      if (!isSelected(row.goodsCode)) selected.value.push(row)
    })
    return
  }
  const pageCodes = new Set(selectableRows.value.map(row => row.goodsCode))
  selected.value = selected.value.filter(item => !pageCodes.has(item.goodsCode))
}

function removeSelected(goodsCode) {
  selected.value = selected.value.filter(item => item.goodsCode !== goodsCode)
}

function clearSelected() {
  selected.value = []
}

function handleConfirm() {
  emit('confirm', selected.value.map(item => ({ ...item })))
  visible.value = false
}

function goodsTypeTag(type) {
  const typeMap = { 'raw': 'primary', 'semifinished': 'warning', 'finished': 'success' }
  return typeMap[type] || 'info'
}

function goodsTypeLabel(type) {
  const typeMap = { 'raw': '原材料', 'semifinished': '半成品', 'finished': '成品' }
  return typeMap[type] || '未知'
}

defineExpose({ open })
</script>

<style lang="scss" scoped>
.goods-select-body {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.goods-select-tree {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 220px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;

  .panel-title {
    padding: 6px 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-light);
    font-size: 13px;
    font-weight: 600;
  }

  .tree-scroll {
    flex: 1;
    height: 340px;
    overflow-y: auto;
    padding: 4px 6px 8px;
  }
}

.goods-select-main {
  flex: 1;
  min-width: 0;
}

.goods-select-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .toolbar-scope {
    margin-left: auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.goods-select-selected {
  margin-top: 12px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-blank);

  .selected-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 600;
  }

  .selected-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    max-height: 76px;
    overflow-y: auto;
  }

  .selected-empty {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.added-tag {
  margin-left: 6px;
}

.goods-thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  vertical-align: middle;
  background: var(--el-fill-color-light);
}

.thumb-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--el-text-color-placeholder);
  font-size: 18px;
}

.no-image {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  overflow: hidden;
  font-size: 13px;

  .node-icon {
    flex-shrink: 0;
    font-size: 14px;
    color: #f5a623;
  }

  .node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

:deep(.el-table__row) {
  cursor: pointer;
}

/* 已添加的商品：整行置灰且不可点，与可选行区分开。
   斑马纹是加在单元格上的，所以背景色也要写到单元格，否则会被盖掉。 */
:deep(.el-table__row.row-excluded) {
  cursor: not-allowed;
}

:deep(.el-table__row.row-excluded td.el-table__cell) {
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
}
</style>

<style lang="scss">
/*
  弹窗用 append-to-body 渲染到 body 下，scoped 选择器匹配不到它的根元素，所以这段不能加 scoped。
  固定宽度是为了让表格列一次排开、不出横向滚动条；窄屏放不下时收成视口宽度，
  避免整个页面被撑出横向滚动。
*/
.goods-select-dialog {
  /* 90% 在超宽屏上会大得离谱，1800px 封顶；窄屏则收成视口宽度。 */
  max-width: min(1800px, calc(100vw - 32px));

  /* 默认内边距下表格几乎贴着弹窗边框，两侧各留出一截。 */
  .el-dialog__body {
    padding: 16px 24px;
  }
}
</style>

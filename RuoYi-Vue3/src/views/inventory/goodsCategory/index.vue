<!--
  商品分类维护，页面与功能对齐「系统管理 → 部门管理」：
  搜索、新增、修改、删除、保存排序、展开/折叠。

  与部门唯一不同的是名称唯一性：商品是按分类名称挂到分类上的（商品表存的就是名称），
  所以名称在同一商家下全局唯一；改名后端会级联更新商品，分类下还有商品或下级分类时不允许删除。
-->
<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="分类名称" prop="categoryName">
            <el-input
               v-model="queryParams.categoryName"
               placeholder="请输入分类名称"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="分类状态" clearable style="width: 200px">
               <el-option
                  v-for="dict in sys_normal_disable"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button
               type="primary"
               plain
               icon="Plus"
               @click="handleAdd"
               v-hasPermi="['inventory:goodscategory:add']"
            >新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="warning"
               plain
               icon="Check"
               @click="handleSaveSort"
               v-hasPermi="['inventory:goodscategory:edit']"
            >保存排序</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="info"
               plain
               icon="Sort"
               @click="toggleExpandAll"
            >展开/折叠</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table
         v-if="refreshTable"
         v-loading="loading"
         :data="categoryList"
         row-key="categoryCode"
         :default-expand-all="isExpandAll"
         :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
         <el-table-column prop="categoryName" label="分类名称" width="260"></el-table-column>
         <el-table-column prop="orderNum" label="排序" width="200">
            <template #default="scope">
               <el-input-number v-model="scope.row.orderNum" controls-position="right" :min="0" style="width: 88px" />
            </template>
         </el-table-column>
         <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
               <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="创建时间" align="center" prop="createTime" width="200">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['inventory:goodscategory:edit']">修改</el-button>
               <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['inventory:goodscategory:add']">新增</el-button>
               <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['inventory:goodscategory:remove']">删除</el-button>
            </template>
         </el-table-column>
      </el-table>

      <!-- 添加或修改商品分类对话框 -->
      <el-dialog :title="title" v-model="open" width="600px" append-to-body :close-on-click-modal="false">
         <el-form ref="categoryRef" :model="form" :rules="rules" label-width="80px">
            <el-row>
               <el-col :span="24">
                  <el-form-item label="上级分类" prop="parentCode">
                     <el-tree-select
                        v-model="form.parentCode"
                        :data="categoryOptions"
                        :props="{ value: 'categoryCode', label: 'categoryName', children: 'children' }"
                        value-key="categoryCode"
                        placeholder="选择上级分类"
                        check-strictly
                     />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="分类名称" prop="categoryName">
                     <el-input v-model="form.categoryName" placeholder="请输入分类名称" maxlength="50" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="显示排序" prop="orderNum">
                     <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="备注" prop="remark">
                     <el-input v-model="form.remark" placeholder="请输入备注" maxlength="200" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="分类状态">
                     <el-radio-group v-model="form.status">
                        <el-radio
                           v-for="dict in sys_normal_disable"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="InventoryGoodsCategory">
import { listCategory, listCategoryExcludeChild, getCategory, delCategory, addCategory, updateCategory, updateCategorySort } from "@/api/inventory/goodsCategory"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict("sys_normal_disable")

/** 顶级分类在库里是 parentcode = '0'，表里没有这一行，
 *  这里补一个虚拟根节点，让「上级分类」和部门管理一样必选 */
const ROOT = { categoryCode: "0", categoryName: "顶级分类" }

const categoryList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const title = ref("")
const categoryOptions = ref([])
const isExpandAll = ref(true)
const refreshTable = ref(true)
const originalOrders = ref({})

const data = reactive({
  form: {},
  queryParams: {
    categoryName: undefined,
    status: undefined
  },
  rules: {
    parentCode: [{ required: true, message: "上级分类不能为空", trigger: "blur" }],
    categoryName: [{ required: true, message: "分类名称不能为空", trigger: "blur" }],
    orderNum: [{ required: true, message: "显示排序不能为空", trigger: "blur" }]
  },
})

const { queryParams, form, rules } = toRefs(data)

/** 查询商品分类列表 */
function getList() {
  loading.value = true
  listCategory(queryParams.value).then(response => {
    categoryList.value = proxy.handleTree(response.data, "categoryCode", "parentCode")
    recordOriginalOrders(categoryList.value)
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    categoryCode: undefined,
    parentCode: undefined,
    categoryName: undefined,
    orderNum: 0,
    remark: undefined,
    status: "0"
  }
  proxy.resetForm("categoryRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 新增按钮操作 */
function handleAdd(row) {
  reset()
  listCategory().then(response => {
    categoryOptions.value = buildOptions(response.data)
  })
  if (row != undefined) {
    form.value.parentCode = row.categoryCode
  }
  open.value = true
  title.value = "添加商品分类"
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  refreshTable.value = false
  isExpandAll.value = !isExpandAll.value
  nextTick(() => {
    refreshTable.value = true
  })
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  listCategoryExcludeChild(row.categoryCode).then(response => {
    categoryOptions.value = buildOptions(response.data)
  })
  getCategory(row.categoryCode).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改商品分类"
  })
}

/** 上级分类下拉：平铺列表拼成树，再挂到虚拟的「顶级分类」下 */
function buildOptions(list) {
  const root = { ...ROOT, children: proxy.handleTree(list || [], "categoryCode", "parentCode") }
  return [root]
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["categoryRef"].validate(valid => {
    if (valid) {
      if (form.value.categoryCode != undefined) {
        updateCategory(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addCategory(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 递归记录原始排序 */
function recordOriginalOrders(list) {
  list.forEach(item => {
    originalOrders.value[item.categoryCode] = item.orderNum
    if (item.children && item.children.length) {
      recordOriginalOrders(item.children)
    }
  })
}

/** 保存排序 */
function handleSaveSort() {
  const changedCategoryCodes = []
  const changedOrderNums = []
  const collectChanged = (list) => {
    list.forEach(item => {
      if (String(originalOrders.value[item.categoryCode]) !== String(item.orderNum)) {
        changedCategoryCodes.push(item.categoryCode)
        changedOrderNums.push(item.orderNum)
      }
      if (item.children && item.children.length) {
        collectChanged(item.children)
      }
    })
  }
  collectChanged(categoryList.value)
  if (changedCategoryCodes.length === 0) {
   proxy.$modal.msgWarning("未检测到排序修改")
    return
  }
  updateCategorySort({ categoryCodes: changedCategoryCodes.join(","), orderNums: changedOrderNums.join(",") }).then(() => {
   proxy.$modal.msgSuccess("排序保存成功")
    recordOriginalOrders(categoryList.value)
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除名称为"' + row.categoryName + '"的数据项?').then(function() {
    return delCategory(row.categoryCode)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>

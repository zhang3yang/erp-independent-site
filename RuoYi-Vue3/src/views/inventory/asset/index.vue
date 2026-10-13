<template>
  <div class="app-container">
    <el-form :inline="true" :model="queryParams" v-show="showSearch">
      <el-form-item label="单据号"><el-input v-model="queryParams.docCode" clearable @keyup.enter="loadList" /></el-form-item>
      <el-form-item label="审核状态"><el-select v-model="queryParams.confirmState" clearable><el-option label="待审核" value="待审核" /><el-option label="已审核" value="已审核" /><el-option label="已驳回" value="已驳回" /></el-select></el-form-item>
      <!-- 不填就是全部月份：单据按业务日期分月表，留空时后端扫全部现存月表 -->
      <el-form-item label="业务日期"><el-date-picker v-model="docDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:260px" /></el-form-item>
      <el-button type="primary" icon="Search" @click="loadList">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button>
    </el-form>
    <!-- 归还、送修没有「新建」：这两种单必须针对已经借出/已经损坏的货，
         入口统一走「归还已借出」「损坏去送修」，从真实库存里挑，挑不出来的东西就不该建单 -->
    <el-row :gutter="10" class="mb8"><el-col v-if="!pickOnly" :span="1.5"><el-button type="primary" plain icon="Plus" @click="openAdd" v-hasPermi="[`${permissionPrefix}:add`]">新建{{ title }}</el-button></el-col><el-col v-if="isReturn" :span="2"><el-button type="primary" plain icon="Plus" @click="openBorrowedReturn" v-hasPermi="['inventory:return:add']">归还已借出</el-button></el-col><el-col v-if="isRepair" :span="2"><el-button type="primary" plain icon="Plus" @click="openDamagedPick" v-hasPermi="['inventory:repair:add']">损坏去送修</el-button></el-col><el-col v-if="isDamage" :span="2"><el-button type="danger" plain icon="Delete" @click="openDamagedPick" v-hasPermi="['inventory:damage:add']">损坏出库</el-button></el-col><right-toolbar v-model:showSearch="showSearch" @queryTable="loadList" /></el-row>
    <el-table v-loading="loading" :data="rows"><el-table-column label="单据号" prop="docCode" width="170" /><el-table-column label="业务日期" prop="docDate" width="120"><template #default="s">{{ parseTime(s.row.docDate, '{y}-{m}-{d}') }}</template></el-table-column><el-table-column label="仓库" prop="fromWareName" /><el-table-column label="对象/维修单位" prop="partnerName" /><el-table-column label="总数量" prop="totalQty" width="100" /><el-table-column label="状态" prop="confirmState" width="100"><template #default="s"><el-tag :type="s.row.confirmState === '已审核' ? 'success' : s.row.confirmState === '已驳回' ? 'danger' : 'warning'">{{ s.row.confirmState }}</el-tag></template></el-table-column><el-table-column v-if="isDamage" label="单据类型" width="110"><template #default="s"><el-tag :type="s.row.docType === 'SHCK' ? 'danger' : 'info'">{{ s.row.docType === 'SHCK' ? '损坏出库' : '损坏登记' }}</el-tag></template></el-table-column><el-table-column v-if="isRepair" label="维修状态" width="120"><template #default="s"><el-tag :type="repairStatus(s.row).type">{{ repairStatus(s.row).label }}</el-tag></template></el-table-column><el-table-column label="操作" :width="isRepair ? 320 : 250" fixed="right"><template #default="s"><el-button link type="primary" icon="View" @click="viewRow(s.row)">查看</el-button><el-button v-if="canFinishRepair(s.row)" link type="warning" icon="Select" @click="openRepairFinish(s.row)">维修完成</el-button><el-button v-if="s.row.confirmState === '待审核' || s.row.confirmState === '已驳回'" link type="primary" icon="Edit" @click="editRow(s.row)">编辑</el-button><el-button v-if="s.row.confirmState === '待审核'" link type="success" icon="Check" @click="approve(s.row,true)">审核</el-button><el-button v-if="s.row.confirmState === '待审核'" link type="danger" icon="Close" @click="approve(s.row,false)">驳回</el-button><el-button v-if="s.row.confirmState === '待审核' || s.row.confirmState === '已驳回'" link type="danger" icon="Delete" @click="removeRow(s.row)">删除</el-button></template></el-table-column></el-table>
    <pagination v-show="total" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="loadList" />
    <!-- 建单前先定良品状态：良品和不良品是两格独立库存，一张单只能动其中一格 -->
    <el-dialog v-model="qualityPickOpen" :title="`新建${title}单`" width="440px" append-to-body :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon class="mb10"
        :title="`一张${title}单只能处理同一种良品状态的货`"
        description="良品和不良品在库存里是分开记的。选定后本单的明细行只能添加该状态下有库存的商品；要处理另一种，请另建一张单。" />
      <el-form label-width="90px">
        <el-form-item label="良品状态">
          <el-radio-group v-model="qualityPick">
            <el-radio v-for="item in QUALITY_FLAG_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="confirmQualityPick">下一步</el-button>
        <el-button @click="qualityPickOpen=false">取消</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="formOpen" :title="formTitle" width="1100px" append-to-body :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px"><el-row :gutter="16"><el-col :span="8"><el-form-item label="仓库" prop="wareCode"><el-select v-model="form.wareCode" style="width:100%" :disabled="!!form.docCode || form.repairAction === 'in'" @change="onContextChange"><el-option v-for="w in warehouses" :key="w.wareCode" :label="warehouseLabel(w)" :value="w.wareCode" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item v-if="route.path.endsWith('/repair')" label="维修操作"><el-input :model-value="form.repairAction === 'in' ? '维修归还' : '送修'" disabled /></el-form-item><el-form-item v-else label="当前状态"><el-input :model-value="getStateName(form.fromState)" disabled /></el-form-item></el-col><el-col :span="8"><el-form-item label="对象/单位"><el-input v-model="form.partnerName" placeholder="员工、部门、客户或维修单位" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="8"><el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item></el-col><el-col :span="8"><el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item></el-col><el-col :span="8"><el-form-item v-if="route.path.endsWith('/loan')" label="预计归还日期"><el-date-picker v-model="form.expectedReturnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item v-else-if="route.path.endsWith('/repair') && form.repairAction === 'out'" label="预计维修归还"><el-date-picker v-model="form.expectedRepairDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row><el-row v-if="isRepair && form.repairAction === 'in'" :gutter="16"><el-col :span="10"><el-form-item label="维修结果"><el-radio-group v-model="form.repairResult"><el-radio value="good">已修好（回良品）</el-radio><el-radio value="defective">没修好（回不良品）</el-radio></el-radio-group></el-form-item></el-col><el-col :span="8"><el-form-item label="关联送修单"><el-input :model-value="form.sourceDocCode" disabled /></el-form-item></el-col></el-row><el-row v-if="isRepair && form.repairAction === 'out'" :gutter="16"><el-col :span="8"><el-form-item label="送修人"><el-input v-model="form.handler" /></el-form-item></el-col><el-col :span="8"><el-form-item label="送修时间"><el-date-picker v-model="form.handlerTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="维修方式"><el-select v-model="form.repairMode" style="width:100%"><el-option label="送外维修（不在库）" value="outsourced" /><el-option label="厂内维修（仍在库）" value="in_house" /></el-select></el-form-item></el-col></el-row><el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="2" placeholder="维修说明、损坏原因等" /></el-form-item><el-divider content-position="left">{{ damageOutMode ? '出库' : title }}明细</el-divider><!-- 归还、送修的明细只能从库存弹窗里挑，所以不给手工加行、也不给手输编码 --><el-button v-if="form.repairAction !== 'in' && !pickOnly" type="primary" size="small" icon="Plus" @click="addItem">添加商品</el-button><span v-else-if="pickOnly && form.repairAction !== 'in'" class="repair-tip">{{ pickTip }}</span>
    <el-table :data="form.items" border class="mt10"><!-- 编码、名称是商品档案上的固定信息，只能通过「添加商品」选出来，单据上不可改 --><el-table-column label="商品编码" prop="goodsCode" width="170" /><el-table-column label="商品名称" prop="goodsName" min-width="160" /><el-table-column label="数量" width="130"><template #default="s"><decimal-input :min-decimals="0" :max-decimals="4" v-model="s.row.qty" :min="0" :max="s.row.currentQty" size="small" style="width:100%" @change="qtyChanged(s.row)" /></template></el-table-column><el-table-column label="批次" width="210"><template #default="s"><el-select v-if="s.row.batchStrategy === 'batch'" v-model="s.row.batchCode" size="small" style="width:100%" placeholder="请选择批次"><el-option v-for="b in s.row.batches" :key="b.batchCode" :label="`${b.batchCode}（余${b.remainQty}）`" :value="b.batchCode" /></el-select><span v-else>无需填写</span></template></el-table-column><el-table-column label="序列" width="150"><template #default="s"><el-button v-if="s.row.batchStrategy === 'serial'" link type="primary" size="small" @click="openSerial(s.$index)">{{ s.row.serialNumbers.length ? `已选${s.row.serialNumbers.length}个` : '选择序列' }}</el-button><span v-else>无需填写</span></template></el-table-column><el-table-column label="操作" width="70"><template #default="s"><el-button v-if="form.repairAction !== 'in'" link type="danger" icon="Delete" @click="form.items.splice(s.$index,1)" /><span v-else>-</span></template></el-table-column></el-table>
      </el-form>
      <template #footer><el-button type="primary" @click="submit">保存</el-button><el-button @click="formOpen=false">取消</el-button></template>
    </el-dialog>
    <el-dialog v-model="serialOpen" title="选择序列" width="560px" append-to-body :close-on-click-modal="false"><el-select v-model="serialSelected" multiple filterable collapse-tags :multiple-limit="serialLimit" style="width:100%" :loading="serialLoading" placeholder="请选择序列号"><el-option v-for="s in serialOptions" :key="s.serialNo" :label="s.batchCode ? `${s.serialNo}（批次 ${s.batchCode}）` : s.serialNo" :value="s.serialNo" /></el-select><template #footer><el-button type="primary" @click="confirmSerial">确定</el-button></template></el-dialog>
    <el-dialog v-model="borrowedReturnOpen" title="归还已借出商品" width="1000px" append-to-body :close-on-click-modal="false">
      <el-alert title="请选择借出中商品并填写本次归还数量；同一张归还单只能选择同一仓库、同一良品状态的商品。" type="info" :closable="false" show-icon class="mb10" />
      <el-table v-loading="borrowedReturnLoading" :data="borrowedRows" border @selection-change="onBorrowedSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column label="商品编码" prop="goodsCode" width="120" />
        <el-table-column label="商品名称" prop="goodsName" min-width="150" />
        <el-table-column label="仓库" prop="wareName" width="120" />
        <el-table-column label="良品状态" prop="qualityName" width="90" />
        <el-table-column label="借出中数量" prop="physicalQty" width="120" />
        <el-table-column label="本次归还数量" width="150"><template #default="scope"><decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.returnQty" :min="0" :max="Number(scope.row.physicalQty)" size="small" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="borrowedReturnOpen=false">取消</el-button><el-button type="primary" :loading="borrowedReturnSubmitting" @click="createBorrowedReturn">生成归还单</el-button></template>
    </el-dialog>
    <el-dialog v-model="damagedPickOpen" :title="isDamage ? '损坏出库' : '损坏去送修'" width="1000px" append-to-body :close-on-click-modal="false">
      <el-alert :title="isDamage
        ? '下面是登记为损坏、目前还在库里的商品。勾选要报废出库的商品并填写数量，审核后库存直接扣减，不再走维修。'
        : '下面是登记为损坏、目前还在库里的商品。勾选要送修的商品并填写本次送修数量，同一张送修单只能选择同一仓库的商品。'" type="info" :closable="false" show-icon class="mb10" />
      <el-alert v-if="!isDamage" title="厂内维修期间货没有离库，仍然记在这一格里，所以这里的数量可能包含已经在修的货；具体在修多少请看下方送修单列表。" type="warning" :closable="false" show-icon class="mb10" />
      <el-table v-loading="damagedLoading" :data="damagedRows" border @selection-change="onDamagedSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column label="商品编码" prop="goodsCode" width="120" />
        <el-table-column label="商品名称" prop="goodsName" min-width="150" />
        <el-table-column label="仓库" prop="wareName" width="120" />
        <el-table-column label="损坏在库数量" prop="physicalQty" width="130" />
        <el-table-column :label="isDamage ? '本次出库数量' : '本次送修数量'" width="150"><template #default="scope"><decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.repairQty" :min="0" :max="Number(scope.row.physicalQty)" size="small" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="damagedPickOpen=false">取消</el-button><el-button type="primary" :loading="damagedSubmitting" @click="createRepairFromDamaged">{{ isDamage ? '下一步：填写出库信息' : '下一步：填写送修信息' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailOpen" :title="`${title}详情`" width="900px" append-to-body :close-on-click-modal="false"><el-descriptions :column="2" border><el-descriptions-item label="单据号">{{ detail.docCode }}</el-descriptions-item><el-descriptions-item label="对象/单位">{{ detail.partnerName || '-' }}</el-descriptions-item><el-descriptions-item label="仓库">{{ detail.fromWareName }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.confirmState }}</el-descriptions-item><el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item></el-descriptions><el-table :data="detail.details" border class="mt10"><el-table-column label="商品编码" prop="goodsCode" /><el-table-column label="商品名称" prop="goodsName" /><el-table-column label="数量" prop="qty" /></el-table></el-dialog>
    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="selectedGoodsCodes" @confirm="handleGoodsSelected" />
  </div>
</template>

<script setup name="InventoryAssetManagement">
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { listDocument, getDocument, addDocument, updateDocument, delDocument, confirmDocument } from '@/api/inventory/document'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { AVAILABLE_STATE, QUALITY_FLAG, ONHAND_FLAG, QUALITY_FLAG_OPTIONS, stockStateLabel } from '@/utils/stockState'
import { getStock, listBorrowedStock, listDamagedStock } from '@/api/inventory/stock'
import { getGoods } from '@/api/inventory/goods'
import { listAvailableBatch } from '@/api/inventory/batch'
import { listInStockSerial } from '@/api/inventory/serial'
import DecimalInput from '@/components/DecimalInput'

const route = useRoute(); const { proxy } = getCurrentInstance()
// 三维状态下每种单据只改自己那一维，其余维度原样带过去。
// 送修的目标在库状态由「维修方式」决定（厂内还在库，送外就离库了），见 repairToState()。
const DEFECTIVE_STATE = { qualityFlag: QUALITY_FLAG.DEFECTIVE, onhandFlag: ONHAND_FLAG.IN }
/** 在库自有的某个良品状态，借出的来源、归还的目标都是这一格 */
function ownState(quality) {
  return { qualityFlag: quality, onhandFlag: ONHAND_FLAG.IN }
}
/** 借出中的某个良品状态；借出不改变货的好坏，所以借出去什么样，还回来还是什么样 */
function loanedState(quality) {
  return { qualityFlag: quality, onhandFlag: ONHAND_FLAG.OUT }
}
// 三维状态下每种单据只改自己那一维，其余维度原样带过去。
// 借出和归还的良品状态由建单前的选择决定，所以 config 里用函数按良品状态推导。
const config = computed(() => { const p = route.path.split('/').filter(Boolean).pop(); return ({
  loan: { type:'JC', title:'借出', byQuality: q => ({ from: ownState(q), to: loanedState(q) }) },
  return: { type:'GH', title:'归还', byQuality: q => ({ from: loanedState(q), to: ownState(q) }) },
  // 只有不良品能送修，所以来源是「不良品·在库·自有」，良品要先做损坏登记
  repair: { type:'SX', title:'维修', from:DEFECTIVE_STATE, to:null },
  // 损坏登记只翻良品状态，在库状态和占用类型保持不变
  damage: { type:'SHDJ', title:'损坏', from:AVAILABLE_STATE, to:DEFECTIVE_STATE }
})[p] || { type:'JC', title:'借出', byQuality: q => ({ from: ownState(q), to: loanedState(q) }) } })
/** 借出、归还要在建单前选良品状态；送修、损坏的来源状态是固定的 */
const needQualityPick = computed(() => !!config.value.byQuality)
const title = computed(() => config.value.title); const permissionPrefix = computed(() => `inventory:${route.path.split('/').filter(Boolean).pop()}`)
const isRepair = computed(() => route.path.endsWith('/repair'))
const isReturn = computed(() => route.path.endsWith('/return'))
const isDamage = computed(() => route.path.endsWith('/damage'))
/** 损坏页的「损坏出库」模式：从不良品·在库直接报废出库，与「新建损坏」登记区分开 */
const damageOutMode = computed(() => isDamage.value && form.damageAction === 'out')
/** 明细只能从库存弹窗里挑：归还挑「借出中」，送修和损坏出库挑「损坏在库」 */
const pickOnly = computed(() => isRepair.value || isReturn.value || damageOutMode.value)
const pickTip = computed(() => {
  if (damageOutMode.value) return '明细来自「损坏出库」的勾选，如需增减请取消后重新挑选'
  return isRepair.value
    ? '明细来自「损坏去送修」的勾选，如需增减请取消后重新挑选'
    : '明细来自「归还已借出」的勾选，如需增减请取消后重新挑选'
})
const qualityPickOpen=ref(false), qualityPick=ref(QUALITY_FLAG.GOOD)
const loading=ref(false), showSearch=ref(true), rows=ref([]), total=ref(0), warehouses=ref([]), formOpen=ref(false), detailOpen=ref(false), detail=ref({}), serialOpen=ref(false), serialLoading=ref(false), serialOptions=ref([]), serialSelected=ref([]), serialIndex=ref(-1), borrowedReturnOpen=ref(false), borrowedReturnLoading=ref(false), borrowedReturnSubmitting=ref(false), borrowedRows=ref([]), borrowedSelected=ref([]), damagedPickOpen=ref(false), damagedLoading=ref(false), damagedSubmitting=ref(false), damagedRows=ref([]), damagedSelected=ref([])
/** 送修单编码 -> 已开出的维修归还量，用来推「维修中 / 部分完成 / 维修完成」 */
const repairReturnIndex=ref({})
const queryParams=reactive({ pageNum:1,pageSize:20,docCode:'',confirmState:'',docTypes:'' }); const docDateRange=ref([]); const form=reactive({ docCode:'',wareCode:'',wareName:'',fromState:null,toState:null,repairMode:'outsourced',repairAction:'out',repairResult:'good',damageAction:'register',sourceDocCode:'',partnerName:'',contactName:'',contactPhone:'',expectedReturnDate:'',expectedRepairDate:'',handler:'',handlerTime:'',note:'',items:[] }); const formRef=ref(); const rules={ wareCode:[{required:true,message:'请选择仓库',trigger:'change'}] }
/** 送修目标状态：不良品 + 维修占用，在库与否看是厂内修还是送外修 */
function repairToState(mode){return { qualityFlag:QUALITY_FLAG.DEFECTIVE, onhandFlag: mode==='in_house'?ONHAND_FLAG.IN:ONHAND_FLAG.OUT }}
/** 把单据上的三维状态字段摊平进 payload */
function stateFields(prefix,state){if(!state)return {};return prefix==='from'
  ? { fromQualityFlag:state.qualityFlag, fromOnhandFlag:state.onhandFlag }
  : { toQualityFlag:state.qualityFlag, toOnhandFlag:state.onhandFlag }}
const goodsSelectRef=ref(null)
/** 已在明细里的商品，选商品弹窗用它打「已添加」标记 */
const selectedGoodsCodes=computed(()=>form.items.map(i=>i.goodsCode).filter(Boolean))
const serialLimit=computed(()=>{const n=Number(form.items[serialIndex.value]?.qty);return Number.isInteger(n)&&n>0?n:0})
const formTitle=computed(()=>{if(damageOutMode.value)return `${form.docCode?'编辑':'新建'}损坏出库单`;if(!isRepair.value)return `${form.docCode?'编辑':'新建'}${title.value}`;if(form.repairAction==='in')return form.docCode?'编辑维修完成单':`维修完成${form.sourceDocCode?`（${form.sourceDocCode}）`:''}`;return `${form.docCode?'编辑':'新建'}送修单`})
function getStateName(state){return stockStateLabel(state) || '未知状态'}
/**
 * 送修页列送修单和维修归还单：归还单是送修单的「维修完成」动作，通过 sourceDocCode 挂回去，
 * 送修单那一行据此显示维修进度；归还单本身仍要单独一行，否则没地方审核它。
 */
async function loadList(){loading.value=true;try{const types=isRepair.value?'SX,WXGH':(isDamage.value?'SHDJ,SHCK':config.value.type);const r=await listDocument({...queryParams,...docDateFilter(),docTypes:types});rows.value=r.rows||[];total.value=r.total||0;if(isRepair.value)await loadRepairReturnIndex()}finally{loading.value=false}}
/** 日期区间转成后端的起止参数；没选就不传，后端按全部月份查 */
function docDateFilter(){const r=docDateRange.value;return r&&r.length?{docDateBegin:r[0]+' 00:00:00',docDateEnd:r[1]+' 23:59:59'}:{}}

/**
 * 把维修归还单按来源送修单汇总：已审核的算已完成，待审核的算在途，都要占掉剩余量。
 *
 * 不限月份——送修和归还常常隔月（7 月送出去 8 月修好），只查当月的话上个月送出去的
 * 那批会一直显示成「维修中」。
 */
async function loadRepairReturnIndex(){
  const index={}
  try{
    const r=await listDocument({pageNum:1,pageSize:500,docTypes:'WXGH'})
    ;(r.rows||[]).forEach(doc=>{
      const source=doc.sourceDocCode
      if(!source||doc.confirmState==='已驳回')return
      const item=index[source]||(index[source]={done:0,pending:0})
      const qty=Number(doc.totalQty)||0
      if(doc.confirmState==='已审核')item.done+=qty;else item.pending+=qty
    })
  }catch(e){/* 拿不到就退化成「维修中」，不挡主流程 */}
  repairReturnIndex.value=index
}

/** 维修状态：送修单看它开出了多少归还量；归还单本身就是一次「维修完成」，看自己的审核状态 */
function repairStatus(row){
  if(row.docType==='WXGH'){
    if(row.confirmState==='已审核')return { label:'已完成', type:'success' }
    if(row.confirmState==='已驳回')return { label:'完成被驳回', type:'danger' }
    return { label:'完成待审核', type:'primary' }
  }
  if(row.confirmState!=='已审核')return { label:'未开始', type:'info' }
  const item=repairReturnIndex.value[row.docCode]||{done:0,pending:0}
  const total=Number(row.totalQty)||0
  const handled=item.done+item.pending
  if(handled<=0)return { label:'维修中', type:'warning' }
  if(handled<total)return { label:'部分完成', type:'primary' }
  return item.pending>0?{ label:'完成待审核', type:'primary' }:{ label:'维修完成', type:'success' }
}

/** 还有没归还完的量才给「维修完成」；待审核的归还量也要扣掉，免得重复开单 */
function canFinishRepair(row){
  if(!isRepair.value||row.docType!=='SX'||row.confirmState!=='已审核')return false
  const item=repairReturnIndex.value[row.docCode]||{done:0,pending:0}
  return (Number(row.totalQty)||0)-(item.done+item.pending)>0
}
function resetQuery(){queryParams.pageNum=1;queryParams.docCode='';queryParams.confirmState='';docDateRange.value=[];loadList()}
async function loadOptions(){const w=await listWarehouse({pageSize:100});warehouses.value=w.rows||[]}
/**
 * 新建：借出和归还要先弹小窗选良品状态——良品和不良品在库存里是两格独立的库存，
 * 一张单只能动其中一格；送修、损坏的来源状态是固定的，直接进表单。
 */
function openAdd(){
  if(needQualityPick.value){qualityPick.value=QUALITY_FLAG.GOOD;qualityPickOpen.value=true;return}
  openForm(config.value.from,config.value.to)
}

/** 选完良品状态，带着推导出的来源/目标状态进主表单 */
function confirmQualityPick(){
  const states=config.value.byQuality(qualityPick.value)
  qualityPickOpen.value=false
  openForm(states.from,states.to)
}

function openForm(fromState,toState){Object.assign(form,{docCode:'',wareCode:'',wareName:'',fromState,toState,repairMode:'outsourced',repairAction:'out',repairResult:'good',damageAction:'register',sourceDocCode:'',partnerName:'',contactName:'',contactPhone:'',expectedReturnDate:'',expectedRepairDate:'',handler:'',handlerTime:'',note:'',items:[]});formOpen.value=true}
async function openBorrowedReturn(){borrowedReturnOpen.value=true;borrowedReturnLoading.value=true;borrowedSelected.value=[];try{const r=await listBorrowedStock();const data=r.data||r.rows||[];borrowedRows.value=data.filter(x=>Number(x.physicalQty)>0).map(x=>({...x,returnQty:Number(x.physicalQty)}))}finally{borrowedReturnLoading.value=false}}
function onBorrowedSelectionChange(selection){borrowedSelected.value=selection}
async function createBorrowedReturn(){const selected=borrowedSelected.value.filter(x=>Number(x.returnQty)>0);if(!selected.length){proxy.$modal.msgWarning('请选择商品并填写归还数量');return}const wareCodes=[...new Set(selected.map(x=>x.wareCode))];if(wareCodes.length!==1){proxy.$modal.msgWarning('一张归还单只能选择同一仓库的商品');return}
  // 良品和不良品是两格独立库存，一张归还单只能动其中一格，所以必须先收敛到唯一的良品状态
  const qualities=[...new Set(selected.map(x=>Number(x.qualityFlag)))];if(qualities.length!==1){proxy.$modal.msgWarning('一张归还单只能选择同一良品状态的商品');return}
  if(selected.some(x=>Number(x.returnQty)>Number(x.physicalQty))){proxy.$modal.msgWarning('归还数量不能大于借出中数量');return}borrowedReturnSubmitting.value=true;try{Object.assign(form,{docCode:'',wareCode:selected[0].wareCode,wareName:selected[0].wareName,fromState:loanedState(qualities[0]),toState:ownState(qualities[0]),repairAction:'out',repairResult:'good',damageAction:'register',sourceDocCode:'',partnerName:'',contactName:'',contactPhone:'',expectedReturnDate:'',expectedRepairDate:'',handler:'',handlerTime:'',note:'',items:selected.map(x=>({goodsCode:x.goodsCode,goodsName:x.goodsName,spec:x.spec,unit:x.unit,qty:Number(x.returnQty),currentQty:Number(x.physicalQty),batchCode:'',batchStrategy:'none',batches:[],serialNumbers:[]}))});borrowedReturnOpen.value=false;formOpen.value=true;for(const item of form.items)await goodsChanged(item,false)}catch(e){proxy.$modal.msgError(e.message||'加载归还单失败')}finally{borrowedReturnSubmitting.value=false}}
/** 挑损坏的货：送修页用来开送修单，损坏页用来开损坏出库单，来源都是「不良品·在库」 */
async function openDamagedPick(){damagedPickOpen.value=true;damagedLoading.value=true;damagedSelected.value=[];try{const r=await listDamagedStock();const data=r.data||r.rows||[];damagedRows.value=data.filter(x=>Number(x.physicalQty)>0).map(x=>({...x,repairQty:Number(x.physicalQty)}))}finally{damagedLoading.value=false}}
function onDamagedSelectionChange(selection){damagedSelected.value=selection}
async function createRepairFromDamaged(){
  const forOutbound=isDamage.value
  const what=forOutbound?'出库':'送修'
  const selected=damagedSelected.value.filter(x=>Number(x.repairQty)>0)
  if(!selected.length){proxy.$modal.msgWarning(`请选择损坏商品并填写${what}数量`);return}
  const wareCodes=[...new Set(selected.map(x=>x.wareCode))]
  if(wareCodes.length!==1){proxy.$modal.msgWarning(`一张${what}单只能选择同一仓库的商品`);return}
  if(selected.some(x=>Number(x.repairQty)>Number(x.physicalQty))){proxy.$modal.msgWarning(`${what}数量不能大于损坏在库数量`);return}
  damagedSubmitting.value=true
  try{
    // 两者来源都是「不良品·在库」：送修的目标状态由「维修方式」在保存时推导，
    // 损坏出库是方向 O 的出库单，压根没有目标状态
    Object.assign(form,{docCode:'',wareCode:selected[0].wareCode,wareName:selected[0].wareName,fromState:DEFECTIVE_STATE,toState:null,repairMode:'outsourced',repairAction:'out',repairResult:'good',damageAction:forOutbound?'out':'register',sourceDocCode:'',partnerName:'',contactName:'',contactPhone:'',expectedReturnDate:'',expectedRepairDate:'',handler:'',handlerTime:'',note:'',items:selected.map(x=>({goodsCode:x.goodsCode,goodsName:x.goodsName,spec:x.spec,unit:x.unit,qty:Number(x.repairQty),currentQty:Number(x.physicalQty),batchCode:'',batchStrategy:'none',batches:[],serialNumbers:[]}))})
    damagedPickOpen.value=false
    formOpen.value=true
    for(const item of form.items)await goodsChanged(item,false)
  }catch(e){proxy.$modal.msgError(e.message||`加载${what}单失败`)}finally{damagedSubmitting.value=false}
}

/**
 * 维修完成：按送修单开一张维修归还单，来源状态就是这批货被送修时去到的那一格
 * （送外维修在「不良品·不在库」，厂内维修还在「不良品·在库」），目标状态看修没修好。
 * 数量默认带送修单的原数量，goodsChanged 会按实际还剩多少压回去，所以分批完成也能用。
 */
async function openRepairFinish(row){
  try{
    const d=(await getDocument(row.docCode,row.tableSuffix)).data||{}
    Object.assign(form,{docCode:'',wareCode:d.toWareCode||d.fromWareCode,wareName:d.toWareName||d.fromWareName,
      fromState:{qualityFlag:d.toQualityFlag,onhandFlag:d.toOnhandFlag},toState:AVAILABLE_STATE,
      repairMode:'outsourced',repairAction:'in',repairResult:'good',damageAction:'register',sourceDocCode:row.docCode,
      partnerName:d.partnerName||'',contactName:'',contactPhone:'',expectedReturnDate:'',expectedRepairDate:'',handler:'',handlerTime:'',note:'',
      items:(d.details||[]).map(x=>({goodsCode:x.goodsCode,goodsName:x.goodsName,spec:x.spec,unit:x.unit,qty:Number(x.qty),currentQty:Number(x.qty),batchCode:x.batchCode||'',batchStrategy:'none',batches:[],serialNumbers:x.serialNumbers||[]}))})
    formOpen.value=true
    for(const item of form.items)await goodsChanged(item,false)
    form.items.forEach(item=>qtyChanged(item))
  }catch(e){proxy.$modal.msgError(e.message||'加载送修单失败')}
}
async function editRow(row){const r=await getDocument(row.docCode,row.tableSuffix);const d=r.data;let meta={};try{meta=JSON.parse(d.remark||'{}')}catch(e){meta={note:d.remark||''}}Object.assign(form,{docCode:d.docCode,wareCode:d.fromWareCode,wareName:d.fromWareName,fromState:{qualityFlag:d.fromQualityFlag,onhandFlag:d.fromOnhandFlag},toState:{qualityFlag:d.toQualityFlag,onhandFlag:d.toOnhandFlag},partnerName:d.partnerName||'',contactName:meta.contactName||'',contactPhone:meta.contactPhone||'',expectedReturnDate:meta.expectedReturnDate||'',expectedRepairDate:meta.expectedRepairDate||'',note:meta.note||'',repairAction:d.docType==='WXGH'?'in':'out',repairResult:d.toQualityFlag===QUALITY_FLAG.DEFECTIVE?'defective':'good',damageAction:d.docType==='SHCK'?'out':'register',sourceDocCode:d.sourceDocCode||'',items:(d.details||[]).map(x=>({...x,currentQty:Number(x.qty),serialNumbers:x.serialNumbers||[],batches:[],batchStrategy:'none'}))});formOpen.value=true;for(const i of form.items) await goodsChanged(i,false)}
/** 「添加商品」从分类树弹窗里挑；goodsChanged 传 clear=false，保留弹窗带来的名称规格 */
function addItem(){goodsSelectRef.value?.open()}
function handleGoodsSelected(list){list.forEach(g=>{const row={goodsCode:g.goodsCode,goodsName:g.goodsName||'',spec:g.spec||'',unit:g.unit||'',qty:0,currentQty:0,batchCode:'',batchStrategy:g.batchStrategy||'none',batches:[],serialNumbers:[]};form.items.push(row);goodsChanged(form.items[form.items.length-1],false)})}
function onContextChange(){form.items.forEach(i=>{i.currentQty=0;i.serialNumbers=[];i.batchCode='';if(i.goodsCode)goodsChanged(i)})}
async function goodsChanged(row,clear=true){if(clear){row.goodsName='';row.qty=0;row.batchCode='';row.serialNumbers=[];row.batches=[]}if(!row.goodsCode||!form.wareCode||!form.fromState)return;try{const [g,s,b]=await Promise.all([getGoods(row.goodsCode),getStock(row.goodsCode,form.wareCode,form.fromState),listAvailableBatch({goodsCode:row.goodsCode,wareCode:form.wareCode,...form.fromState,pageSize:100})]);row.goodsName=g.data?.goodsName||'';row.spec=g.data?.spec||'';row.unit=g.data?.unit||'';row.currentQty=Number(s.data?.physicalQty)||0;row.batchStrategy=g.data?.batchStrategy||'none';row.batches=b.data||b.rows||[]}catch(e){row.currentQty=0;row.batches=[]}}
function qtyChanged(row){if(row.qty>row.currentQty)row.qty=row.currentQty;if(row.batchStrategy==='serial'&&row.serialNumbers.length!==Number(row.qty))row.serialNumbers=[]}
async function openSerial(index){
  serialIndex.value=index
  const row=form.items[index]
  if(!row||row.batchStrategy!=='serial')return
  if(!row.goodsCode||!form.wareCode||!form.fromState){proxy.$modal.msgWarning('请先选择仓库并填写商品编码');return}
  if(!serialLimit.value){proxy.$modal.msgWarning('请先填写大于 0 的整数数量');return}
  serialSelected.value=[...row.serialNumbers]
  serialOptions.value=[]
  serialOpen.value=true
  serialLoading.value=true
  try{
    const r=await listInStockSerial(row.goodsCode,form.wareCode,form.fromState)
    serialOptions.value=r.data||r.rows||[]
    if(!serialOptions.value.length)proxy.$modal.msgWarning('当前商品没有可用序列号')
  }catch(e){
    proxy.$modal.msgError(e.message||'获取可用序列号失败')
  }finally{serialLoading.value=false}
}
function confirmSerial(){const row=form.items[serialIndex.value];row.serialNumbers=[...new Set(serialSelected.value)];const bs=[...new Set(serialOptions.value.filter(x=>row.serialNumbers.includes(x.serialNo)).map(x=>x.batchCode).filter(Boolean))];row.batchCode=bs.length===1?bs[0]:'';serialOpen.value=false}
async function submit(){await formRef.value.validate();const items=form.items.filter(x=>Number(x.qty)>0);if(!items.length){proxy.$modal.msgWarning('请至少添加一条商品明细');return}if(items.some(x=>x.batchStrategy==='serial'&&x.serialNumbers.length!==Number(x.qty))){proxy.$modal.msgWarning('序列号数量必须与商品数量一致');return}const wh=warehouses.value.find(x=>x.wareCode===form.wareCode);const repairIn=route.path.endsWith('/repair')&&form.repairAction==='in';
  // 损坏出库是一张出库单（方向 O）：只有来源状态「不良品·在库」，没有目标状态
  const damageOut=damageOutMode.value;
  const docType=damageOut?'SHCK':(repairIn?'WXGH':config.value.type);const repairOut=route.path.endsWith('/repair')&&!repairIn;const fromState=damageOut?DEFECTIVE_STATE:(repairIn?{qualityFlag:QUALITY_FLAG.DEFECTIVE,onhandFlag:form.fromState?.onhandFlag??ONHAND_FLAG.OUT}:(form.fromState||config.value.from));
  // 维修完成：修好了回良品在库，没修好仍是不良品在库（可以再送修一次）
  const repairedState=form.repairResult==='defective'?DEFECTIVE_STATE:AVAILABLE_STATE;
  const toState=damageOut?null:(repairIn?repairedState:(repairOut?repairToState(form.repairMode):(form.toState||config.value.to)));
  if(repairIn){
    // 厂内维修的货本来就在库，没修好就是原地不动，开出来是一张什么都不改的空单
    if(fromState.qualityFlag===toState.qualityFlag&&fromState.onhandFlag===toState.onhandFlag){proxy.$modal.msgWarning('厂内维修且没修好时，货的状态没有变化，无需开维修完成单');return}
    if(form.sourceDocCode){
      const source=rows.value.find(x=>x.docCode===form.sourceDocCode)
      const handled=repairReturnIndex.value[form.sourceDocCode]||{done:0,pending:0}
      const remain=(Number(source?.totalQty)||0)-(handled.done+handled.pending)
      const current=items.reduce((n,x)=>n+Number(x.qty),0)
      if(source&&current>remain+1e-6){proxy.$modal.msgWarning(`本次完成数量不能超过送修单剩余的 ${remain}`);return}
    }
  }const remark=JSON.stringify({contactName:form.contactName,contactPhone:form.contactPhone,expectedReturnDate:form.expectedReturnDate,expectedRepairDate:form.expectedRepairDate,note:form.note});const payload={docCode:form.docCode||undefined,docType,docTypeName:damageOut?'损坏出库':(repairIn?'维修归还':title.value),
    // 维修归还挂回它的送修单，列表据此推导维修状态
    sourceDocCode:form.sourceDocCode||undefined,sourceDocType:form.sourceDocCode?'SX':undefined,sourceDocTypeName:form.sourceDocCode?'维修':undefined,
    fromWareCode:form.wareCode,fromWareName:wh?.wareName||form.wareName,toWareCode:form.wareCode,toWareName:wh?.wareName||form.wareName,...stateFields('from',fromState),...stateFields('to',toState),partnerName:form.partnerName,handler:form.handler,handlerTime:form.handlerTime,remark,totalQty:items.reduce((n,x)=>n+Number(x.qty),0),details:items.map(x=>({goodsCode:x.goodsCode,goodsName:x.goodsName,spec:x.spec,unit:x.unit,wareCode:form.wareCode,qualityFlag:fromState.qualityFlag,onhandFlag:fromState.onhandFlag,qty:x.qty,batchCode:x.batchCode||undefined,serialNumbers:x.serialNumbers.length?x.serialNumbers:undefined}))};try{if(form.docCode)await updateDocument(payload);else await addDocument(payload);proxy.$modal.msgSuccess('保存成功');formOpen.value=false;loadList()}catch(e){proxy.$modal.msgError(e.message||'保存失败')}}
function getDisplayRemark(value){if(!value)return '';try{const meta=JSON.parse(value);if(meta&&typeof meta==='object')return meta.note||''}catch(e){}return value}
async function viewRow(row){const d=(await getDocument(row.docCode,row.tableSuffix)).data||{};d.remark=getDisplayRemark(d.remark);detail.value=d;detailOpen.value=true}
async function approve(row,ok){await proxy.$modal.confirm(`确认${ok?'审核通过':'驳回'}${row.docCode}？`);await confirmDocument(row.docCode,row.tableSuffix,ok);proxy.$modal.msgSuccess('操作成功');loadList()}
async function removeRow(row){await proxy.$modal.confirm(`确认删除${row.docCode}？`);await delDocument(row.docCode,row.tableSuffix);proxy.$modal.msgSuccess('删除成功');loadList()}
loadOptions();loadList()
</script>

<style scoped>
.repair-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

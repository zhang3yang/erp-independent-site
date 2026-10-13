<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="采购单号" prop="purchaseCode">
        <el-input v-model="queryParams.purchaseCode" placeholder="请输入采购单号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商" prop="partnerName">
        <el-input v-model="queryParams.partnerName" placeholder="请输入供应商名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 240px">
          <!--
            「待收货」库里不存在这个状态，是已审核 + 部分到货两种的合称，由后端的
            selectPurchaseOrderList 翻成 in 条件。首页看板那张「待收货采购」卡片点进来
            就带这个值，两边口径必须是同一个。
          -->
          <el-option label="待收货（已审核+部分到货）" value="pending_receive" />
          <el-option label="草稿" value="draft" />
          <el-option label="待审核" value="submitted" />
          <el-option label="已审核" value="approved" />
          <el-option label="部分到货" value="partial_received" />
          <el-option label="已完成" value="completed" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
      </el-form-item>
      <el-form-item label="采购日期" style="width: 308px">
        <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期"></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['purchase:order:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['purchase:order:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['purchase:order:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="orderList" @selection-change="handleSelectionChange">
      <!--
        列宽一律用 min-width 而不是 width：宽屏上多出来的空间会按比例摊给每一列，
        而不是全部涌进唯一没写宽度的「供应商」列，把单号和日期挤到两头。
        窄屏时各列退到 min-width 并出现横向滚动，不会互相挤压。
      -->
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="采购单号" align="center" prop="purchaseCode" min-width="180">
        <template #default="scope">
          <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.purchaseCode }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="供应商" align="center" prop="partnerName" min-width="200" :show-overflow-tooltip="true" />
      <!-- 商品内容：后端按明细算好的一句「可乐等3种」，纯展示，不展开 -->
      <el-table-column label="商品内容" align="center" prop="goodsSummary" min-width="160" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.goodsSummary || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="采购日期" align="center" prop="purchaseDate" min-width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.purchaseDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <!-- 超期未到货的订单标红并提示，避免采购员要逐单点开详情才看得到预计到货日期 -->
      <el-table-column label="预计到货" align="center" prop="expectDate" min-width="130">
        <template #default="scope">
          <span v-if="!scope.row.expectDate" class="expect-none">未填写</span>
          <el-tooltip v-else-if="overdueDays(scope.row) > 0" :content="'已超期 ' + overdueDays(scope.row) + ' 天未到货'" placement="top">
            <span class="expect-overdue">{{ parseTime(scope.row.expectDate, '{y}-{m}-{d}') }}</span>
          </el-tooltip>
          <span v-else>{{ parseTime(scope.row.expectDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeCost" label="总金额" align="center" prop="totalAmount" min-width="120">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <!-- 支付进度由财务模块的付款流水完成时回写，采购侧只读 -->
      <el-table-column label="支付进度" align="center" prop="payStatus" min-width="150">
        <template #default="scope">
          <el-tag :type="payStatusType(scope.row.payStatus)">{{ scope.row.payStatus || '未支付' }}</el-tag>
          <div v-if="canSeeCost" class="pay-progress">已付 {{ formatCurrency(scope.row.paidAmount) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" min-width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <!-- 人员一律显示昵称，后端连不出昵称时（如账号已删除）回退显示账号 -->
      <el-table-column label="创建人" align="center" prop="createUserName" min-width="100">
        <template #default="scope">{{ scope.row.createUserName || scope.row.createUser }}</template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" min-width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-if="scope.row.status === 'draft'" v-hasPermi="['purchase:order:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-if="scope.row.status === 'draft' || scope.row.status === 'rejected'" v-hasPermi="['purchase:order:remove']">删除</el-button>
          <el-button link type="success" icon="Check" @click="handleSubmit(scope.row)" v-if="scope.row.status === 'draft'" v-hasPermi="['purchase:order:submit']">提交</el-button>
          <el-button link type="danger" icon="CircleClose" @click="handleCancel(scope.row)" v-if="scope.row.status === 'draft' || scope.row.status === 'submitted'" v-hasPermi="['purchase:order:edit']">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改采购订单对话框 -->
    <el-dialog :title="title" v-model="open" width="92%" top="5vh" append-to-body :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="orderRef" label-width="100px">
        <el-row :gutter="20">
          <el-col v-if="form.id" :span="12">
            <el-form-item label="采购单号" prop="purchaseCode">
              <el-input v-model="form.purchaseCode" disabled />
            </el-form-item>
          </el-col>
          <!-- 供应商改成弹窗选：供应商多起来之后下拉框里的名字长得都差不多，容易选错。
               先录明细，再点这里，弹窗会按商品的进货历史把最可能的那家排在最前面。 -->
          <el-col :span="form.id ? 12 : 24">
            <el-form-item label="供应商" prop="partnerCode">
              <el-input :model-value="partnerLabel" readonly
                :placeholder="form.items.length ? '点右侧「选择」，按商品历史推荐' : '点右侧「选择」，或先录明细再选'">
                <template #append>
                  <el-button icon="Search" @click="openPartnerSelect">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="采购日期" prop="purchaseDate">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择采购日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预计到货日期" prop="expectDate">
              <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择预计到货日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <!-- 运费类型暂不在本页采集，也不再必填：一张采购订单可以分多次发货，
               每次发货的运费类型未必相同，改由采购员在「采购发货」登记时逐单选择。
               这里留空即可，后端不校验。 -->
        </el-row>
        <!--
          预估运费只在「到付」下可填：包邮不产生运费，寄付由供应商垫付、金额要等财务付款才知道，
          只有到付需要采购员下单时预估。后端对非到付会强制归零。
          新建单进不来（运费类型已不采集），改存量的到付订单时还会出现，所以保留。
        -->
        <el-row v-if="form.freightType === 'collect'" :gutter="20">
          <el-col :span="8">
            <el-form-item v-if="canSeeCost" label="预估运费">
              <el-input-number v-model="form.estimatedFreight" :min="0" :precision="2" :step="10"
                controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <div class="freight-hint">到付运费由库管员签收时支付，实际金额在到货单上登记并自动汇总到本单。</div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 采购明细。仓库、批次号、序列号不属于采购订单，在到货与质检环节采集。 -->
        <goods-line-table v-model="form.items" mode="purchase" title="采购明细"
          tip="录入商品、数量、采购单价和税率。入库仓库在创建到货单时选择，批次号与序列号在到货质检环节采集。"
          show-partner-hint :partner-hints="partnerHints"
          :current-partner-code="form.partnerCode"
          @goods-added="handleGoodsAdded" />
        <el-alert v-if="mismatchNames.length" type="warning" show-icon :closable="false" class="mt10"
          :title="`有 ${mismatchNames.length} 个商品的历史供应商不是「${form.partnerName}」`"
          :description="`${mismatchNames.join('、')}。历史不一致不影响下单——换供应商、或者这批货确实换了家进，都正常，确认一下别选错就行。`" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>

      <partner-select-dialog ref="partnerSelectRef" :goods="form.items"
        :selected-code="form.partnerCode || ''" @confirm="handlePartnerSelected" />
    </el-dialog>

    <!-- 采购订单详情对话框 -->
    <el-dialog title="采购订单详情" v-model="detailOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="采购单号">{{ detailForm.purchaseCode }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailForm.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="采购日期">{{ parseTime(detailForm.purchaseDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="预计到货日期">{{ detailForm.expectDate ? parseTime(detailForm.expectDate, '{y}-{m}-{d}') : '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="运费类型">{{ freightTypeLabel(detailForm.freightType) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="价税合计">{{ formatCurrency(detailForm.totalAmount) }}</el-descriptions-item>
        <!-- 预估运费只对到付有意义；实际运费由各次到货累加，包邮单两者都是 0 -->
        <el-descriptions-item v-if="canSeeCost" label="预估运费">
          <span v-if="detailForm.freightType === 'collect'">{{ formatCurrency(detailForm.estimatedFreight) }}</span>
          <span v-else class="expect-none">不适用</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="实际运费">{{ formatCurrency(detailForm.actualFreight) }}</el-descriptions-item>
        <!-- 已付货款与付款状态由财务模块的付款流水完成时回写，采购侧只读 -->
        <el-descriptions-item v-if="canSeeCost" label="已付货款">
          {{ formatCurrency(detailForm.paidAmount) }}
          <span v-if="detailForm.totalAmount" class="pay-progress">（{{ payProgressText(detailForm) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="付款状态">
          <el-tag :type="payStatusType(detailForm.payStatus)">{{ detailForm.payStatus || '未支付' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detailForm.status)">{{ getStatusLabel(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailForm.createUserName || detailForm.createUser }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detailForm.confirmUserName || detailForm.confirmUser || '未审核' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detailForm.confirmTime ? parseTime(detailForm.confirmTime) : '未审核' }}</el-descriptions-item>
        <el-descriptions-item v-if="detailForm.status === 'rejected'" label="驳回原因" :span="2">
          <span class="reject-reason">{{ detailForm.rejectReason || '未填写' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark || '无' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">采购明细</el-divider>
      <el-table :data="detailForm.items" border style="width: 100%">
        <el-table-column label="行号" prop="lineNo" width="60" align="center" />
        <el-table-column label="商品编码" prop="goodsCode" width="120" />
        <el-table-column label="商品名称" prop="goodsName" width="150" />
        <el-table-column label="规格" prop="spec" width="100" />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="数量" prop="qty" width="100" align="right" />
        <el-table-column v-if="canSeeCost" label="单价" prop="unitPrice" width="100" align="right">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.unitPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="税率(%)" prop="taxRate" width="90" align="right" />
        <el-table-column v-if="canSeeCost" label="税额" prop="taxAmount" width="100" align="right">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.taxAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canSeeCost" label="不含税金额" prop="amount" width="110" align="right">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已到货数量" prop="receivedQty" width="100" align="right" />
        <el-table-column label="行备注" prop="remark" min-width="120" :show-overflow-tooltip="true" />
      </el-table>

      <!-- 短装汇总。供应商说发了多少（预计到货）与入库员点到多少（实际到货）有差额时列出来，
           采购员据此追货。差额本身已经留在采购剩余可发量里，不需要额外操作。 -->
      <div v-if="shortageItems.length" class="shortage-block">
        <div class="shortage-title">短装汇总</div>
        <div class="shortage-tip">以下商品实际到货少于供应商声称的发货量，差额仍算在采购剩余可发数量里，可以再开发货单追货。</div>
        <el-table :data="shortageItems" border size="small">
          <el-table-column label="发货单号" prop="receiveCode" min-width="160" />
          <el-table-column label="发货日期" prop="sendDate" width="110" />
          <el-table-column label="商品名称" prop="goodsName" min-width="140" />
          <el-table-column label="规格" prop="spec" width="100" />
          <el-table-column label="单位" prop="unit" width="70" align="center" />
          <el-table-column label="预计到货" prop="expectQty" width="100" align="right" />
          <el-table-column label="实际到货" prop="actualQty" width="100" align="right" />
          <el-table-column label="短装" width="100" align="right">
            <template #default="scope"><el-tag type="warning" size="small">{{ scope.row.shortageQty }}</el-tag></template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 付款流水。已付货款只是个合计数，采购员真正要看的是「钱走到哪一步了」：
           压在审核队列里和财务已经打款出去，是两回事。 -->
      <el-divider content-position="left">付款流水</el-divider>
      <el-table :data="payTransactions" v-loading="payTransLoading" border size="small" style="width: 100%">
        <el-table-column label="流水号" prop="transactionCode" min-width="150" />
        <el-table-column label="交易时间" width="160">
          <template #default="scope">{{ scope.row.tradeTime ? parseTime(scope.row.tradeTime) : '未打款' }}</template>
        </el-table-column>
        <el-table-column label="付款账户" min-width="120">
          <template #default="scope">{{ scope.row.accountName || '未选账户' }}</template>
        </el-table-column>
        <el-table-column v-if="canSeeCost" label="金额" width="110" align="right">
          <template #default="scope">
            <span class="amount-out">{{ formatCurrency(Math.abs(Number(scope.row.amount || 0))) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="流水状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="transStatusType(scope.row.status)">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="经办" width="100">
          <template #default="scope">{{ scope.row.completeUserName || scope.row.createUserName || scope.row.createUser }}</template>
        </el-table-column>
        <el-table-column label="业务摘要" prop="bizSummary" min-width="180" :show-overflow-tooltip="true" />
        <template #empty><span class="pay-empty">还没有对应的付款流水，付款请去财务管理 → 资金流水发起</span></template>
      </el-table>
      <div v-if="canSeeCost" class="pay-flow-tip">
        已付货款 {{ formatCurrency(detailForm.paidAmount) }} = 上表中<b>已完成</b>的流水合计
        （{{ formatCurrency(completedPayment) }}）。「待审核 / 已审核」的流水表示款还没打出去，不计入已付货款。
      </div>

      <!-- 存量订单可能带有历史的建议仓库、批次号和序列号。新订单不再采集这些字段，
           入库仓库与批次序列号都在采购入库环节采集。 -->
      <el-collapse v-if="hasLegacyTracking" class="mt10">
        <el-collapse-item title="历史追踪信息（旧版数据，仅供参考）" name="legacy">
          <el-table :data="detailForm.items" border size="small">
            <el-table-column label="商品名称" prop="goodsName" min-width="150" />
            <el-table-column label="建议入库仓库" prop="warehouseName" width="140" />
            <el-table-column label="批次号" prop="batchCode" width="150" />
            <el-table-column label="序列号" width="110">
              <template #default="scope">{{ scope.row.serialNumbers?.length ? scope.row.serialNumbers.length + '个' : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>

      <template #footer>
        <div class="dialog-footer">
          <!-- 审核入口只在详情里出现：审核前要先看清明细和金额，列表上直接点审核容易误批 -->
          <el-button v-if="detailForm.status === 'submitted'" type="warning" icon="Stamp"
            v-hasPermi="['purchase:order:approve']" @click="handleApprove(detailForm)">审 核</el-button>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 审核对话框 -->
    <el-dialog title="采购订单审核" v-model="approveOpen" width="500px" append-to-body :close-on-click-modal="false">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="采购单号">
          <el-input v-model="approveForm.purchaseCode" disabled />
        </el-form-item>
        <el-form-item label="审核意见">
          <el-radio-group v-model="approveForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 驳回必须写原因：采购员要据此修改，事后追溯也要有依据。后端同样会校验。 -->
        <el-form-item v-if="approveForm.approved === false" label="驳回原因" required>
          <el-input v-model="approveForm.rejectReason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="请说明驳回原因，例如单价高于合同价、供应商选择有误" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitApprove">确 定</el-button>
          <el-button @click="approveOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PurchaseOrder">
import { listOrder, getOrder, addOrder, updateOrder, delOrder, submitOrder, approveOrder, cancelOrder } from '@/api/purchase/order'
import { formatCurrency } from '@/utils/currency'
import { listShortage } from '@/api/purchase/receive'
import { recommendPartners } from '@/api/inventory/partner'
import { buildPartnerHints, pickRecommendedPartner } from '@/utils/goodsPartner'
// 采购单只读地展示财务那边的付款流水；已付货款的写入口全在财务模块
import { listTransactionByBiz } from '@/api/finance/transaction'
import GoodsLineTable from '@/components/GoodsLineTable/index.vue'
import PartnerSelectDialog from '@/components/PartnerSelectDialog/index.vue'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()
const route = useRoute()

const orderList = ref([])
const open = ref(false)
const detailOpen = ref(false)
/** 详情页的短装汇总，来自采购发货单的实际到货与预计到货差额 */
const shortageItems = ref([])
/** 详情页的付款流水，来自财务模块，含还没打款的在途流水 */
const payTransactions = ref([])
const payTransLoading = ref(false)
const approveOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const dateRange = ref([])

const data = reactive({
  form: {},
  detailForm: {},
  approveForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    purchaseCode: undefined,
    partnerName: undefined,
    status: undefined
  },
  rules: {
    partnerCode: [
      { required: true, message: '请选择供应商', trigger: 'change' }
    ],
    purchaseDate: [
      { required: true, message: '请选择采购日期', trigger: 'change' }
    ]
  }
})

const { form, detailForm, approveForm, queryParams, rules } = toRefs(data)

const partnerSelectRef = ref(null)

/** 明细里每个商品的历史供应商：{ [goodsCode]: { partnerCode, partnerName } } */
const partnerHints = ref({})

/** 供应商输入框里显示的文字，编码跟在后面便于核对 */
const partnerLabel = computed(() => (form.value.partnerCode
  ? `${form.value.partnerName}（${form.value.partnerCode}）`
  : ''))

/** 明细里历史供应商和表头所选不一致的商品名称，用来提示别选错 */
const mismatchNames = computed(() => {
  if (!form.value.partnerCode) return []
  return (form.value.items || [])
    .filter(item => item.goodsCode
      && partnerHints.value[item.goodsCode]
      && partnerHints.value[item.goodsCode].partnerCode !== form.value.partnerCode)
    .map(item => `${item.goodsName}（历史：${partnerHints.value[item.goodsCode].partnerName}）`)
})

function openPartnerSelect() {
  partnerSelectRef.value?.open()
}

function handlePartnerSelected(partner) {
  form.value.partnerCode = partner.partnerCode
  form.value.partnerName = partner.partnerName
  // 供应商是必填项，选完手动触发一次校验把红字消掉
  proxy.$refs['orderRef']?.validateField('partnerCode')
}

/**
 * 拉一次明细里各商品的历史供应商。
 *
 * 一次带上全部商品编码，不按行发请求。拿回来逐行显示「历史供应商」用于比对。
 * 推荐依据是已审核入库单的进货实绩，从没进过货的商品查不到记录，属正常情况。
 *
 * @param {boolean} autoFill 供应商还空着时是否自动填入首选那家。
 *   已经填了的绝不覆盖 —— 人选的一定比推荐的准，推荐只负责省掉第一次点击。
 */
function refreshPartnerHints(autoFill) {
  const codes = (form.value.items || []).map(item => item.goodsCode).filter(Boolean)
  if (!codes.length) {
    partnerHints.value = {}
    return
  }
  recommendPartners(codes).then(response => {
    partnerHints.value = buildPartnerHints(response.data)
    if (!autoFill || form.value.partnerCode) return
    const picked = pickRecommendedPartner(response.data)
    if (!picked) return
    form.value.partnerCode = picked.partnerCode
    form.value.partnerName = picked.partnerName
    proxy.$refs['orderRef']?.validateField('partnerCode')
    proxy.$modal.msgSuccess(`已按进货历史填入供应商「${picked.partnerName}」，不对请点「选择」改`)
  }).catch(() => {
    // 推荐失败不该挡住下单，清掉提示列自己选就行
    partnerHints.value = {}
  })
}

function handleGoodsAdded() {
  refreshPartnerHints(true)
}

/** 查询采购订单列表 */
async function getList() {
  loading.value = true
  try {
    const response = await listOrder(proxy.addDateRange(queryParams.value, dateRange.value))
    orderList.value = response.rows
    total.value = response.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  // 明细留空，由「添加商品」从分类树里挑。预置空行在弹窗改成选商品之后已无意义：
  // 那行没有商品编码，删也删不掉，还得先手工清掉才能正常录入。
  reset()
  open.value = true
  title.value = '添加采购订单'
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  reset()
  const id = row.id || ids.value[0]
  const response = await getOrder(id)
  const order = response.data
  order.items = order.items || []
  order.estimatedFreight = Number(order.estimatedFreight || 0)
  form.value = order
  open.value = true
  title.value = '修改采购订单'
  // 改单也要能看到每行的历史供应商，但不自动改已经选好的那家
  refreshPartnerHints(false)
}

/** 详情按钮操作 */
function handleDetail(row) {
  const id = row.id
  shortageItems.value = []
  payTransactions.value = []
  getOrder(id).then(response => {
    detailForm.value = response.data
    detailOpen.value = true
    // 短装汇总跨月表统计，查不到就当没有，不阻断详情展示
    listShortage(response.data.purchaseCode)
      .then(res => shortageItems.value = res.data || [])
      .catch(() => shortageItems.value = [])
    loadPayTransactions(response.data.purchaseCode)
  })
}

/** 该采购单的付款流水。查不到就当没有，不阻断详情展示 */
function loadPayTransactions(purchaseCode) {
  payTransLoading.value = true
  listTransactionByBiz('采购', purchaseCode)
    .then(res => payTransactions.value = res.data || [])
    .catch(() => payTransactions.value = [])
    .finally(() => payTransLoading.value = false)
}

/** 已完成的付款合计。对得上已付货款，对不上说明还有流水没打款 */
const completedPayment = computed(() => payTransactions.value
  .filter(t => t.status === '已完成')
  .reduce((sum, t) => sum + Math.abs(Number(t.amount || 0)), 0))

/** 资金流水状态。与财务模块同一套取值，颜色也保持一致 */
function transStatusType(status) {
  return { 草稿: 'info', 待审核: 'warning', 已审核: 'primary', 已完成: 'success', 已驳回: 'danger' }[status] || 'info'
}

/** 提交审核按钮操作 */
function handleSubmit(row) {
  proxy.$modal.confirm('确认提交采购订单"' + row.purchaseCode + '"进行审核？').then(function () {
    return submitOrder(row.purchaseCode)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('提交成功')
  }).catch(() => {})
}

/** 取消按钮操作 */
function handleCancel(row) {
  proxy.$modal.confirm('确认取消采购订单"' + row.purchaseCode + '"？').then(function () {
    return cancelOrder(row.purchaseCode)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('取消成功')
  }).catch(() => {})
}

/** 审核按钮操作。入口在详情对话框右下角，row 传的是 detailForm */
function handleApprove(row) {
  approveForm.value = {
    purchaseCode: row.purchaseCode,
    approved: true,
    rejectReason: ''
  }
  approveOpen.value = true
}

/** 提交审核 */
function submitApprove() {
  const { purchaseCode, approved, rejectReason } = approveForm.value
  if (approved === false && !(rejectReason || '').trim()) {
    proxy.$modal.msgWarning('驳回时必须填写驳回原因')
    return
  }
  approveOrder(purchaseCode, approved, (rejectReason || '').trim()).then(response => {
    proxy.$modal.msgSuccess(approved ? '审核通过' : '已驳回')
    approveOpen.value = false
    // 详情里的状态已经过期，一并关掉，避免看到审核前的旧状态
    detailOpen.value = false
    getList()
  })
}

/**
 * 预计到货超期天数。只有还没收齐的订单才算超期——
 * 已完成、已取消、已驳回的单子早就不等货了，标红只会造成噪音。
 */
function overdueDays(row) {
  if (!row.expectDate) return 0
  if (!['approved', 'partial_received'].includes(row.status)) return 0
  const expect = new Date(row.expectDate)
  if (Number.isNaN(expect.getTime())) return 0
  const today = new Date()
  // 按自然日比较，忽略时分秒，避免当天被算成超期
  const diff = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    - new Date(expect.getFullYear(), expect.getMonth(), expect.getDate())
  return Math.max(0, Math.floor(diff / 86400000))
}

/** 删除按钮操作 */
function handleDelete(row) {
  const orderIds = row.id || ids.value
  proxy.$modal.confirm('是否确认删除采购订单编号为"' + orderIds + '"的数据项？').then(function () {
    return delOrder(orderIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}


// 订单级的物流单号与承运商已下线：一张采购订单可以分多次发货，每次发货有各自的
// 物流单号，挂在订单上只能记住最后一次。物流信息改在「采购发货」单上逐次录入
// （RuoYi-Vue3/src/views/purchase/receive/index.vue）。后端接口与权限均予保留。

/** 运费类型展示。说明书定义为 包邮/寄付/到付。 */
function freightTypeLabel(freightType) {
  return { free: '包邮', prepaid: '寄付', collect: '到付' }[freightType] || '未填写'
}

/** 付款状态由后端按已付货款与订单金额算出，取值见说明书 3.3.1 */
function payStatusType(payStatus) {
  return { '未支付': 'info', '部分支付': 'warning', '已支付': 'success', '超额支付': 'danger' }[payStatus] || 'info'
}

/** 详情页的付款比例。订单金额为 0 时不显示比例，避免出现除零得到的 Infinity */
function payProgressText(order) {
  const total = Number(order.totalAmount || 0)
  if (!total) return '订单金额为 0'
  const paid = Number(order.paidAmount || 0)
  return `占订单金额 ${(paid / total * 100).toFixed(1)}%`
}

/** 存量订单是否带有旧版的仓库、批次或序列号数据 */
const hasLegacyTracking = computed(() => (detailForm.value.items || []).some(
  item => item.warehouseCode || item.batchCode || item.serialNumbers?.length
))

/** 获取状态类型 */
function getStatusType(status) {
  const statusMap = {
    'draft': 'info',
    'submitted': 'warning',
    'approved': 'success',
    'partial_received': 'warning',
    'completed': 'success',
    'rejected': 'danger',
    'cancelled': 'info'
  }
  return statusMap[status] || 'info'
}

/** 获取状态标签 */
function getStatusLabel(status) {
  const statusMap = {
    'draft': '草稿',
    'submitted': '待审核',
    'approved': '已审核',
    'partial_received': '部分到货',
    'completed': '已完成',
    'rejected': '已驳回',
    'cancelled': '已取消'
  }
  return statusMap[status] || '未知'
}

/** 表单重置 */
function reset() {
  form.value = {
    id: undefined,
    purchaseCode: undefined,
    partnerCode: undefined,
    partnerName: undefined,
    purchaseDate: undefined,
    expectDate: undefined,
    // 不预设运费类型：默认成包邮会让发货登记时也带出包邮，把运费栏直接藏掉
    freightType: undefined,
    estimatedFreight: 0,
    remark: undefined,
    items: []
  }
  partnerHints.value = {}
  proxy.resetForm('orderRef')
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 确定按钮 */
function submitForm() {
  proxy.$refs['orderRef'].validate(valid => {
    if (valid) {
      if (form.value.items.length === 0) {
        proxy.$modal.msgError('请添加采购明细')
        return
      }
      const invalidBatchItem = form.value.items.find(item => item.batchStrategy !== 'batch' && (item.batchCode || '').trim())
      if (invalidBatchItem) {
        proxy.$modal.msgError(`商品“${invalidBatchItem.goodsName || invalidBatchItem.goodsCode}”无需填写批次号`)
        return
      }
      const invalidSerialItem = form.value.items.find(item => item.batchStrategy !== 'serial' && item.serialNumbers?.length)
      if (invalidSerialItem) {
        proxy.$modal.msgError(`商品“${invalidSerialItem.goodsName || invalidSerialItem.goodsCode}”无需填写序列号`)
        return
      }
      // 计算总金额
      form.value.totalAmount = form.value.items.reduce((sum, item) => sum + (item.amount || 0), 0)

      if (form.value.id) {
        updateOrder(form.value).then(response => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addOrder(form.value).then(response => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

// 从财务资金流水的业务单号点过来时，直接用单号过滤，省掉手工复制粘贴那一步
if (route.query.purchaseCode) {
  queryParams.value.purchaseCode = route.query.purchaseCode
}

/**
 * 认首页看板带过来的 ?status=submitted（待审核）或 ?status=pending_receive（待收货）。
 *
 * 状态筛选界面上本来就有，这里只是把 URL 参数灌进去，让用户落地时就看见筛选条上
 * 写着状态，而不是拿到一个不知道被谁筛过的列表。
 *
 * @returns 是否真的改了筛选条件——没改就不必额外发一次请求
 */
function applyRouteFilter() {
  const status = route.query.status
  if (!status || queryParams.value.status === status) {
    return false
  }
  queryParams.value.status = status
  queryParams.value.pageNum = 1
  return true
}

applyRouteFilter()
getList()

// 本页被 keep-alive 缓存，切走再切回来不会重新执行 getList()。
// 采购入库审核后订单状态会变（部分到货 / 已完成）、已到货数量也会变，
// 不刷新的话这里一直显示离开前的旧状态。
// onActivated 首次挂载后也会触发一次，用标志位跳过，避免重复请求。
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) {
    skipFirstActivate = false
    return
  }
  // 第二次从看板点进来时组件不重新初始化，只有这里会触发，筛选条件得在这儿再灌一次
  applyRouteFilter()
  getList()
})

if (route.query.action === 'add') {
  nextTick(() => handleAdd())
}
</script>

<style scoped>
.shortage-block {
  margin-top: 16px;
  padding-left: 12px;
  border-left: 4px solid var(--el-color-warning);
}
.shortage-title {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}
.shortage-tip {
  margin: 4px 0 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.expect-none {
  color: var(--el-text-color-secondary);
}
.expect-overdue {
  color: var(--el-color-danger);
  font-weight: 600;
  cursor: default;
}
.reject-reason {
  color: var(--el-color-danger);
}
.pay-progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pay-flow-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pay-empty {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.amount-out {
  color: var(--el-color-danger);
  font-weight: 600;
}
.freight-hint {
  line-height: 32px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

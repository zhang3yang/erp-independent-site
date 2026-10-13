<template>
  <div class="app-container">
    <el-alert v-if="!legacyInboundEnabled" type="info" show-icon :closable="false" class="mb8"
      title="有采购订单的入库请点「采购入库」，选择采购员登记的发货单"
      description="采购员在「采购管理 → 采购发货」登记预计到货数量；入库员在这里填实际到货数量、签收人、签收时间、批次序列号。「新增入库单」仅用于无采购订单的手工入库（应急入库、期初建账）。" />

    <el-form v-if="legacyInboundEnabled" :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="采购单号" prop="purchaseCode">
        <el-input v-model="queryParams.purchaseCode" placeholder="请输入采购单号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商" prop="partnerName">
        <el-input v-model="queryParams.partnerName" placeholder="请输入供应商名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 240px">
          <el-option label="待审核" value="approved" />
          <el-option label="已完成" value="completed" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" icon="Van" @click="handleShipmentAdd" v-hasPermi="['inventory:inbound:add']">采购入库</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleDirectAdd" v-hasPermi="['inventory:inbound:add']">新增入库单</el-button>
      </el-col>
      <right-toolbar v-if="legacyInboundEnabled" v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-if="legacyInboundEnabled" v-loading="loading" :data="purchaseList">
      <el-table-column label="采购单号" align="center" prop="purchaseCode" width="150" />
      <el-table-column label="供应商" align="center" prop="partnerName" :show-overflow-tooltip="true" />
      <el-table-column label="采购日期" align="center" prop="purchaseDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.purchaseDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeCost" label="总金额" align="center" prop="totalAmount" width="120">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'completed' ? 'success' : 'primary'">
            {{ scope.row.status === 'approved' ? '待审核' : scope.row.status === 'completed' ? '已完成' : scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Plus" @click="handleCreateInbound(scope.row)" v-hasPermi="['inventory:inbound:add']">生成入库单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-if="legacyInboundEnabled" v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-divider content-position="left">采购入库单审核</el-divider>

    <!--
      这张表原来一个筛选都没有，只能按日期倒序翻页。于是待审核的单子只要不是最近几天的，
      就会被后面开的单据挤到第二、三页，页面上等于看不见——2026-08 那 12 张挂了一个月
      没人审就是这么来的。状态筛选后端早就支持（DocumentMainMapper 的 confirmState），
      这里只是把它接出来。
    -->
    <el-form :inline="true" class="document-filter">
      <el-form-item label="状态">
        <el-select v-model="documentQuery.confirmState" placeholder="全部状态" clearable
          style="width: 160px" @change="handleDocumentQuery">
          <el-option label="待审核" value="待审核" />
          <el-option label="已审核" value="已审核" />
          <el-option label="已驳回" value="已驳回" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table v-loading="documentLoading" :data="inboundDocumentList">
      <el-table-column label="入库单号" align="center" prop="docCode" width="180" />
      <el-table-column label="采购单号" align="center" prop="sourceDocCode" width="160">
        <template #default="scope">
          <span v-if="isManualInbound(scope.row.sourceDocType, scope.row.sourceDocCode)" class="manual-inbound">手动入库</span>
          <span v-else>{{ scope.row.sourceDocCode || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="供应商" align="center" prop="partnerName" :show-overflow-tooltip="true" />
      <!-- 商品内容：后端按明细算好的一句「可乐等3种」，纯展示，不展开 -->
      <el-table-column label="商品内容" align="center" prop="goodsSummary" min-width="160" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.goodsSummary || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="入库日期" align="center" prop="docDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.docDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="总数量" align="center" prop="totalQty" width="110" />
      <el-table-column v-if="canSeeCost" label="总金额" align="center" prop="totalAmount" width="120">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="confirmState" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.confirmState === '已审核' ? 'success' : scope.row.confirmState === '已驳回' ? 'danger' : 'warning'">
            {{ scope.row.confirmState }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleViewInbound(scope.row)">查看</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="success" icon="Check" @click="handleConfirmInbound(scope.row, true)" v-hasPermi="['inventory:inbound:approve']">审核</el-button>
          <el-button v-if="scope.row.confirmState === '待审核'" link type="danger" icon="Close" @click="handleConfirmInbound(scope.row, false)" v-hasPermi="['inventory:inbound:approve']">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="documentTotal > 0" :total="documentTotal" v-model:page="documentQuery.pageNum" v-model:limit="documentQuery.pageSize" @pagination="getInboundDocumentList" />

    <!-- 采购入库：由采购员登记的发货单入库 -->
    <el-dialog title="采购入库" v-model="shipmentOpen" width="92%" top="5vh" class="inventory-document-dialog" :close-on-click-modal="false" append-to-body>
      <el-form :model="shipmentForm" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="采购发货单" required>
              <el-select v-model="shipmentForm.receiveCode" filterable placeholder="请选择待入库的发货单" style="width: 100%" @change="handleShipmentChange">
                <el-option v-for="item in shipmentOptions" :key="item.receiveCode" :label="shipmentLabel(item)" :value="item.receiveCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="采购单号"><el-input :value="shipmentForm.purchaseCode" disabled /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="物流单号"><el-input :value="shipmentForm.logisticsNo" disabled /></el-form-item></el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="签收人" required>
              <el-input v-model="shipmentForm.receiveUser" placeholder="默认当前登录人，可改" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="签收时间" required>
              <el-date-picker v-model="shipmentForm.receiveTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="入库仓库" required>
              <el-select v-model="shipmentForm.toWareCode" placeholder="请选择仓库" style="width: 100%">
                <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 只有到付才由入库员出钱。包邮没有运费；寄付的运费供应商发货时已付清，
             金额由采购员填在发货单上，入库员再录一次就成了同一笔钱记两遍。 -->
        <el-row :gutter="20">
          <el-col :span="6" v-if="canSeeCost && shipmentForm.freightType === 'collect'">
            <el-form-item label="本次实付运费">
              <el-input-number v-model="shipmentForm.freightAmount" :min="0" :precision="2" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="shipmentForm.freightType === 'collect' ? 18 : 24">
            <div class="shipment-hint">
              <template v-if="shipmentForm.freightType === 'collect'">
                <span>预估运费 {{ formatCurrency(shipmentForm.estFreightAmount) }}</span>
                <span v-if="Number(shipmentForm.paidFreight || 0) > 0" class="shipment-warn">
                　本发货单已累计实付运费 {{ formatCurrency(shipmentForm.paidFreight) }}，如无新增支出请保持 0
                </span>
              </template>
              <span v-else-if="shipmentForm.freightType">
                {{ freightTypeLabel(shipmentForm.freightType) }}，运费不由入库员承担{{ Number(shipmentForm.paidFreight || 0) > 0 ? '，采购员已记实付运费 ' + formatCurrency(shipmentForm.paidFreight) : '' }}
              </span>
            </div>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="shipmentForm.remark" type="textarea" /></el-form-item>

        <div class="detail-section-header">
          <div>
            <div class="detail-section-title">入库明细</div>
            <div class="detail-section-tip">整单一次入完：每一行都要填实际到货数量，以点到的实物为准，可增可减。整行一件都没到就填 0，按短装记账，差额会回到采购订单的剩余可发量。</div>
          </div>
          <el-tag type="info" effect="plain">待入库 {{ shipmentForm.items.length }} 项</el-tag>
        </div>

        <el-table :data="shipmentForm.items" border stripe max-height="360" empty-text="该发货单没有待入库的明细" class="document-detail-table">
          <el-table-column label="商品名称" prop="goodsName" min-width="160" show-overflow-tooltip />
          <el-table-column label="规格" prop="spec" min-width="110" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="70" align="center" />
          <el-table-column label="预计到货" prop="qty" width="100" align="right" />
          <el-table-column label="实际到货" width="170" class-name="editable-column">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.actualQty" :min="0" size="small" style="width: 100%"
                @change="handleActualQtyChange(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column label="差异" width="120" align="center">
            <template #default="scope">
              <el-tag v-if="shipmentDiff(scope.row) === 0" type="success" size="small">齐套</el-tag>
              <el-tag v-else-if="Number(scope.row.actualQty || 0) === 0" type="danger" size="small">未到货</el-tag>
              <el-tag v-else-if="shipmentDiff(scope.row) > 0" type="warning" size="small">短装 {{ shipmentDiff(scope.row) }}</el-tag>
              <el-tag v-else type="primary" size="small">超发 {{ -shipmentDiff(scope.row) }}</el-tag>
            </template>
          </el-table-column>
          <!-- 批次号在审核时才生成，这张表只用于新建，没有值可显示，改为显示商品的批次策略 -->
          <el-table-column label="批次策略" width="160">
            <template #default="scope">
              <span class="readonly-tracking">{{ batchStrategyLabel(scope.row.batchStrategy) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="序列号" width="130" align="center" fixed="right" class-name="editable-column">
            <template #default="scope">
              <el-button v-if="scope.row.batchStrategy === 'serial'" link type="primary" size="small"
                :disabled="Number(scope.row.actualQty || 0) === 0" @click="openShipmentSerial(scope.$index)">
                {{ scope.row.serialNumbers && scope.row.serialNumbers.length > 0 ? scope.row.serialNumbers.length + '个' : '必须输入' }}
              </el-button>
              <span v-else class="readonly-tracking">无需填写</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="shipmentSaving" @click="submitShipmentInbound">确认创建</el-button>
          <el-button @click="shipmentOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 序列号输入（采购入库弹窗专用，按实际到货数量校验） -->
    <el-dialog title="输入序列号" v-model="shipmentSerialOpen" width="500px" append-to-body :close-on-click-modal="false">
      <p class="serial-tip">实际到货 {{ shipmentSerialExpect }}，需要 {{ shipmentSerialExpect }} 个序列号，每行一个</p>
      <el-input v-model="shipmentSerialText" type="textarea" :rows="8" placeholder="每行一个序列号" />
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmShipmentSerial">确 定</el-button>
          <el-button @click="shipmentSerialOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="新增采购入库单" v-model="directOpen" width="92%" top="5vh" class="inventory-document-dialog" :close-on-click-modal="false" append-to-body>
      <el-form :model="directForm" ref="directRef" label-width="100px">
        <!-- 采购单号一律由后端自动生成（DIRECT-入库单号），建单时不给填，查看单据时才显示 -->
        <el-row :gutter="20">
          <!-- 供应商改成弹窗选：供应商多起来之后下拉框里的名字长得都差不多，容易选错。
               先添加商品，再点这里，弹窗会按商品的进货历史把最可能的那家排在最前面。 -->
          <el-col :span="8">
            <el-form-item label="供应商" required>
              <el-input :model-value="directPartnerLabel" readonly
                :placeholder="directForm.items.length ? '点右侧「选择」，按商品历史推荐' : '点右侧「选择」，或先添加商品再选'">
                <template #append>
                  <el-button icon="Search" @click="openDirectPartnerSelect">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="入库日期"><el-date-picker v-model="directForm.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="入库仓库" required><el-select v-model="directForm.wareCode" placeholder="请选择仓库" style="width: 100%"><el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" /></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="directForm.remark" type="textarea" /></el-form-item>
        <goods-line-table v-model="directForm.items" mode="inbound" title="入库明细"
          tip="录入商品、数量和成本单价，保存后生成待审核入库单。批次号由系统在审核时生成。"
          show-partner-hint :partner-hints="directPartnerHints"
          :current-partner-code="directForm.partnerCode"
          @goods-added="handleDirectGoodsAdded" />
        <el-alert v-if="directMismatchNames.length" type="warning" show-icon :closable="false" class="mt10"
          :title="`有 ${directMismatchNames.length} 个商品的历史供应商不是「${directForm.partnerName}」`"
          :description="`${directMismatchNames.join('、')}。历史不一致不影响建单——换供应商、或者这批货确实换了家进，都正常，确认一下别选错就行。`" />
      </el-form>
      <template #footer><el-button type="primary" @click="submitDirectInbound">确认创建</el-button><el-button @click="directOpen = false">取消</el-button></template>

      <partner-select-dialog ref="directPartnerSelectRef" :goods="directForm.items"
        :selected-code="directForm.partnerCode" @confirm="handleDirectPartnerSelected" />
    </el-dialog>

    <!-- 生成入库单对话框 -->
    <el-dialog :title="inboundDialogTitle" v-model="inboundOpen" width="92%" top="5vh" class="inventory-document-dialog" :close-on-click-modal="false" append-to-body>
      <el-form :model="inboundForm" ref="inboundRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="采购单号">
              <el-input :value="inboundPurchaseCodeLabel" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input :value="inboundForm.partnerName" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="入库仓库">
              <el-select v-model="inboundForm.defaultWareCode" placeholder="请选择仓库" style="width: 100%" @change="applyDefaultWarehouse" :disabled="inboundReadonly">
                <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="inboundForm.remark" type="textarea" placeholder="请输入备注" :disabled="inboundReadonly" />
        </el-form-item>

        <div class="detail-section-header">
          <div>
            <div class="detail-section-title">入库明细</div>
            <div class="detail-section-tip">{{ inboundReadonly ? '当前为只读查看，表格可左右滚动查看更多字段' : '蓝色区域为可编辑内容，表格可左右滚动查看更多字段' }}</div>
          </div>
          <el-tag type="info" effect="plain">共 {{ inboundForm.items.length }} 项</el-tag>
        </div>

        <el-table :data="inboundForm.items" border stripe max-height="360" empty-text="暂无入库明细" class="document-detail-table">
          <!-- 明细来自采购订单，商品档案信息一律只读 -->
          <el-table-column label="商品编码" prop="goodsCode" width="150" />
          <el-table-column label="商品名称" prop="goodsName" min-width="160" fixed="left" show-overflow-tooltip />
          <el-table-column label="规格" prop="spec" min-width="120" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="80" align="center" />
          <el-table-column label="采购数量" prop="qty" width="110" align="right" />
          <el-table-column label="已到货" prop="receivedQty" width="100" align="right" />
          <el-table-column label="未到货" width="110" align="right">
            <template #default="scope">
              <span>{{ formatQty(scope.row.qty - (scope.row.receivedQty || 0)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="本次入库" width="170" class-name="editable-column">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.inboundQty" :min="0" :max="scope.row.qty - (scope.row.receivedQty || 0)" size="small" style="width: 100%" :disabled="inboundReadonly" />
            </template>
          </el-table-column>
          <el-table-column label="入库仓库" width="180" class-name="editable-column">
            <template #default="scope">
              <el-select v-model="scope.row.wareCode" placeholder="请选择仓库" size="small" style="width: 100%" :disabled="inboundReadonly">
                <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </template>
          </el-table-column>
          <!-- 与新增入库单的明细表统一：这里只说明商品按什么方式追踪，
               批次号由系统在审核时生成，要查具体批次号看库存台账或批次管理 -->
          <el-table-column label="批次策略" width="170">
            <template #default="scope">
              <span class="readonly-tracking">{{ batchStrategyLabel(scope.row.batchStrategy) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="序列号" width="130" align="center" fixed="right" class-name="editable-column">
            <template #default="scope">
              <el-button v-if="scope.row.batchStrategy === 'serial'" link type="primary" size="small" @click="openSerialDialog(scope.$index)">
                {{ scope.row.serialNumbers && scope.row.serialNumbers.length > 0 ? scope.row.serialNumbers.length + '个' : '必须输入' }}
              </el-button>
              <span v-else class="readonly-tracking">无需填写</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="!inboundReadonly" type="primary" @click="submitInbound">{{ inboundForm.docCode ? '保存修改' : '确认生成' }}</el-button>
          <el-button @click="inboundOpen = false">{{ inboundReadonly ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 序列号输入对话框 -->
    <el-dialog title="输入序列号" v-model="serialDialogOpen" width="500px" append-to-body :close-on-click-modal="false">
      <p style="color: #909399; font-size: 12px; margin-bottom: 10px;">每行一个序列号，用于序列号追踪管理（可选）</p>
      <el-input v-model="serialDialogText" type="textarea" :rows="8" :disabled="serialDialogReadonly" placeholder="请输入序列号，每行一个&#10;例如：&#10;SN20260001&#10;SN20260002&#10;SN20260003" />
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="!serialDialogReadonly" type="primary" @click="confirmSerialDialog">确 定</el-button>
          <el-button @click="serialDialogOpen = false">{{ serialDialogReadonly ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PurchaseInbound">
import { listPurchaseOrder } from '@/api/purchase/order'
import { formatCurrency } from '@/utils/currency'
import { getPurchaseOrderItems } from '@/api/purchase/order'
import { createInbound, confirmInbound, getInbound, updateInbound, addDirectInbound, createInboundFromShipment } from '@/api/inventory/purchaseInbound'
import { listPendingShipments, getReceive } from '@/api/purchase/receive'
import useUserStore from '@/store/modules/user'
// parseTime 只挂在 globalProperties 上，模板里能直接用，script 里必须显式引入
import { parseTime } from '@/utils/ruoyi'
import { listDocument } from '@/api/inventory/document'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { recommendPartners } from '@/api/inventory/partner'
import { buildPartnerHints, pickRecommendedPartner } from '@/utils/goodsPartner'
import { getGoods } from '@/api/inventory/goods'
import { getConfigKey } from '@/api/system/config'
import GoodsLineTable from '@/components/GoodsLineTable/index.vue'
import PartnerSelectDialog from '@/components/PartnerSelectDialog/index.vue'
import DecimalInput from '@/components/DecimalInput'
import { formatQty } from '@/utils/decimal'

import {
  createInventoryDocumentLine,
  formatSerialNumbers,
  parseSerialNumbers
} from '@/utils/inventoryDocument'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()

/**
 * 采购订单直接入库（旧通道）开关，取自 sys_config 的 purchase.inbound.legacy.enabled。
 * 关闭时隐藏本页的采购订单区块，入库统一走「采购管理 -> 采购到货与质检」。
 * 手工直接入库不受该开关影响。
 */
const legacyInboundEnabled = ref(false)

const purchaseList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const warehouseOptions = ref([])
const inboundOpen = ref(false)
const inboundDialogTitle = ref('生成采购入库单')
const inboundReadonly = ref(false)
const documentLoading = ref(false)
const inboundDocumentList = ref([])
const documentTotal = ref(0)
// 入库单列表自己的分页参数，和上面采购视图的 queryParams 分开，互不影响
const documentQuery = reactive({ pageNum: 1, pageSize: 20, confirmState: undefined })
const directOpen = ref(false)
const directPartnerSelectRef = ref(null)
/** 手工入库明细里每个商品的历史供应商：{ [goodsCode]: { partnerCode, partnerName } } */
const directPartnerHints = ref({})
const serialDialogOpen = ref(false)
const serialDialogText = ref('')
const serialDialogIndex = ref(-1)

// 采购入库（由采购发货单入库）
const shipmentOpen = ref(false)
const shipmentSaving = ref(false)
const shipmentOptions = ref([])
const shipmentSerialOpen = ref(false)
const shipmentSerialText = ref('')
const shipmentSerialIndex = ref(-1)
const userStore = useUserStore()

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    purchaseCode: undefined,
    partnerName: undefined,
    status: 'approved',
    docTypes: 'RK'
  },
  inboundForm: {
    docCode: '',
    tableSuffix: '',
    purchaseCode: '',
    sourceDocType: '',
    partnerName: '',
    partnerCode: '',
    defaultWareCode: '',
    remark: '',
    items: []
  },
  directForm: {
    purchaseCode: '', partnerCode: '', partnerName: '', purchaseDate: '', wareCode: '', remark: '', items: []
  },
  shipmentForm: {
    receiveCode: '', tableSuffix: '', purchaseCode: '', logisticsNo: '',
    receiveUser: '', receiveTime: '', toWareCode: '',
    freightAmount: 0, estFreightAmount: 0, paidFreight: 0, remark: '', items: []
  }
})

// 采购入库一律入到「良品·在库·自有」，不良品要先入库再登记损坏，所以页面不再选库存状态。
const { queryParams, inboundForm, directForm, shipmentForm } = toRefs(data)
const serialDialogReadonly = computed(() => inboundReadonly.value)
// 采购入库的序列号按实际到货数量校验，不是按预计
const shipmentSerialExpect = computed(() => shipmentSerialIndex.value < 0
  ? 0 : Number(shipmentForm.value.items[shipmentSerialIndex.value]?.actualQty || 0))

/** 读取旧通道开关。读取失败按停用处理，避免误开放绕过质检的入口。 */
async function loadLegacyInboundSwitch() {
  try {
    const response = await getConfigKey('purchase.inbound.legacy.enabled')
    legacyInboundEnabled.value = String(response.msg).toLowerCase() === 'true'
  } catch (error) {
    legacyInboundEnabled.value = false
  }
}

/** 查询采购订单列表 */
async function getList() {
  if (!legacyInboundEnabled.value) {
    purchaseList.value = []
    total.value = 0
    loading.value = false
    return
  }
  loading.value = true
  try {
    const response = await listPurchaseOrder(queryParams.value)
    purchaseList.value = response.rows
    total.value = response.total
  } finally {
    loading.value = false
  }
}

/** 查询采购入库单，供审核和结果核对 */
async function getInboundDocumentList() {
  documentLoading.value = true
  try {
    const response = await listDocument({
      pageNum: documentQuery.pageNum,
      pageSize: documentQuery.pageSize,
      docType: 'RK',
      confirmState: documentQuery.confirmState || undefined
    })
    inboundDocumentList.value = response.rows || []
    documentTotal.value = response.total || 0
  } finally {
    documentLoading.value = false
  }
}

/** 加载仓库选项 */
async function loadWarehouseOptions() {
  const response = await listWarehouse({ pageSize: 100 })
  warehouseOptions.value = response.rows || []
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

/* ---------------- 采购入库：由采购员登记的发货单入库 ---------------- */

/** 库存状态默认「正常」（决议 B10-1）。字典里没有就退回第一项。 */
function shipmentLabel(item) {
  const date = item.sendDate ? parseTime(item.sendDate, '{y}-{m}-{d}') : '未填发货日期'
  // 下拉里只会出现整单未被占用的待入库单，不用再标注状态
  return `${item.receiveCode} / ${item.purchaseCode} / ${date}` +
    (item.logisticsNo ? ` / 物流${item.logisticsNo}` : '')
}

/** 短装为正、超发为负，与后端 badqty = max(预计-实际,0) 的口径一致 */
function shipmentDiff(row) { return Number(row.qty || 0) - Number(row.actualQty || 0) }

/** 运费类型展示。说明书定义为 包邮/寄付/到付。 */
function freightTypeLabel(freightType) {
  return { free: '包邮', prepaid: '寄付', collect: '到付' }[freightType] || '未填写'
}

async function handleShipmentAdd() {
  try {
    const response = await listPendingShipments()
    shipmentOptions.value = response.data || []
    if (!shipmentOptions.value.length) {
      proxy.$modal.msgWarning('当前没有待入库的采购发货单，请先让采购员在「采购管理 → 采购发货」登记')
      return
    }
    shipmentForm.value = {
      receiveCode: '', tableSuffix: '', purchaseCode: '', logisticsNo: '',
      // 签收人默认当前登录人，允许改成实际收货的人（决议 B8-1）
      receiveUser: userStore.nickName || userStore.name || '',
      // 用 parseTime 取本地时间。toISOString() 是 UTC，GMT+8 下会早 8 小时，
      // 凌晨签收时会算出比发货日期还早的时间，被后端的「签收时间不能早于发货日期」挡下来。
      receiveTime: parseTime(new Date(), '{y}-{m}-{d} {h}:{i}:{s}'),
      toWareCode: '',
      freightType: '', freightAmount: 0, estFreightAmount: 0, paidFreight: 0, remark: '', items: []
    }
    shipmentOpen.value = true
  } catch (error) {
    proxy.$modal.msgError('获取待入库发货单失败')
  }
}

async function handleShipmentChange(receiveCode) {
  const picked = shipmentOptions.value.find(item => item.receiveCode === receiveCode)
  if (!picked) return
  try {
    const response = await getReceive(receiveCode, picked.tableSuffix)
    const receive = response.data || {}
    shipmentForm.value.tableSuffix = picked.tableSuffix
    shipmentForm.value.purchaseCode = receive.purchaseCode || ''
    shipmentForm.value.logisticsNo = receive.logisticsNo || ''
    shipmentForm.value.freightType = receive.freightType || ''
    shipmentForm.value.estFreightAmount = Number(receive.estFreightAmount || 0)
    // 非到付的运费不归入库员，先清零，避免切换发货单时残留上一单的金额被提交
    if (receive.freightType !== 'collect') shipmentForm.value.freightAmount = 0
    // 驳回后重建时，这里会显示上一次已经付掉的运费，避免重复录入
    shipmentForm.value.paidFreight = Number(receive.freightAmount || 0)
    // 只列尚未入库的行；已入库或被其它入库单占用的行不能再选
    shipmentForm.value.items = (receive.items || [])
      .filter(item => item.lineStatus === '未处理')
      .map(item => ({
        receiveDetailCode: item.detailCode,
        goodsCode: item.goodsCode,
        goodsName: item.goodsName,
        spec: item.spec,
        unit: item.unit,
        qty: Number(item.qty || 0),
        actualQty: Number(item.qty || 0),
        warehouseCode: item.warehouseCode || '',
        batchStrategy: 'none',
        batchCode: '',
        serialNumbers: []
      }))
    if (!shipmentForm.value.items.length) {
      proxy.$modal.msgWarning('该发货单没有待入库的明细')
    }
    // 建议入库仓库取发货单上第一行填的，入库员可以改（决议 B5-1）
    const suggested = (receive.items || []).find(item => item.warehouseCode)
    if (suggested && !shipmentForm.value.toWareCode) shipmentForm.value.toWareCode = suggested.warehouseCode
    await enrichInboundTrackingStrategies(shipmentForm.value.items)
  } catch (error) {
    proxy.$modal.msgError('获取发货单明细失败')
  }
}

/** 实际到货数量变了就清掉已录序列号，避免个数与数量对不上 */
function handleActualQtyChange(row) {
  if (row.batchStrategy === 'serial' && (row.serialNumbers || []).length !== Number(row.actualQty || 0)) {
    row.serialNumbers = []
  }
}

function openShipmentSerial(index) {
  shipmentSerialIndex.value = index
  shipmentSerialText.value = formatSerialNumbers(shipmentForm.value.items[index].serialNumbers)
  shipmentSerialOpen.value = true
}

function confirmShipmentSerial() {
  const index = shipmentSerialIndex.value
  if (index >= 0 && index < shipmentForm.value.items.length) {
    shipmentForm.value.items[index].serialNumbers = parseSerialNumbers(shipmentSerialText.value)
  }
  shipmentSerialOpen.value = false
}

function submitShipmentInbound() {
  const form = shipmentForm.value
  // 整单入库：全部明细一次提交，填 0 的行由后端按全额短装关闭
  const lines = form.items
  const arrived = lines.filter(item => Number(item.actualQty) > 0)
  if (!form.receiveCode) { proxy.$modal.msgWarning('请选择采购发货单'); return }
  if (!lines.length) { proxy.$modal.msgWarning('该发货单没有待入库的明细'); return }
  if (!arrived.length) {
    proxy.$modal.msgWarning('整单一件都没到货，请让采购员取消这张发货单，不要建入库单')
    return
  }
  if (!form.receiveUser) { proxy.$modal.msgWarning('请填写签收人'); return }
  if (!form.receiveTime) { proxy.$modal.msgWarning('请填写签收时间'); return }
  if (!form.toWareCode) { proxy.$modal.msgWarning('请选择入库仓库'); return }

  const missingSerial = arrived.find(item =>
    item.batchStrategy === 'serial' && (item.serialNumbers || []).length !== Number(item.actualQty))
  if (missingSerial) {
    proxy.$modal.msgWarning(`商品“${missingSerial.goodsName}”需要填写 ${missingSerial.actualQty} 个序列号`)
    return
  }

  // 实际到货数量不设上限（决议 A1b），超发不拦，只做提示
  const overs = lines.filter(item => shipmentDiff(item) < 0)
  const missing = lines.filter(item => Number(item.actualQty || 0) === 0)
  const notes = []
  if (overs.length) notes.push(`${overs.length} 个商品实际到货多于预计，将按实收数量入库`)
  if (missing.length) notes.push(`${missing.length} 个商品一件都没到，将按短装关闭，差额回到采购剩余可发量`)
  const confirmText = notes.length
    ? notes.join('；') + '，是否继续？'
    : '确认按实际到货数量创建采购入库单？创建后需审核才会进库存。'

  proxy.$modal.confirm(confirmText).then(() => {
    shipmentSaving.value = true
    return createInboundFromShipment({
      receiveCode: form.receiveCode,
      tableSuffix: form.tableSuffix,
      receiveUser: form.receiveUser,
      receiveTime: form.receiveTime,
      toWareCode: form.toWareCode,
      freightAmount: form.freightAmount || 0,
      remark: form.remark || undefined,
      items: lines.map(item => ({
        receiveDetailCode: item.receiveDetailCode,
        actualQty: item.actualQty,
        batchCode: item.batchCode || undefined,
        serialNumbers: item.serialNumbers && item.serialNumbers.length ? item.serialNumbers : undefined
      }))
    })
  }).then(() => {
    proxy.$modal.msgSuccess('采购入库单已创建，待审核')
    shipmentOpen.value = false
    getInboundDocumentList()
  }).catch(error => {
    if (error) proxy.$modal.msgError(error.message || '创建失败')
  }).finally(() => { shipmentSaving.value = false })
}

/* ---------------- 手工直接入库 ---------------- */

function handleDirectAdd() {
  // 明细留空，由「添加商品」从分类树里挑；预置空行在弹窗改成选商品之后已无意义。
  // 入库日期会作为 docDate 决定本单落哪张库存月表，不能用 toISOString()（UTC 会早 8 小时）
  directForm.value = { partnerCode: '', partnerName: '', purchaseDate: parseTime(new Date(), '{y}-{m}-{d}'), wareCode: '', remark: '', items: [] }
  directPartnerHints.value = {}
  directOpen.value = true
}

/** 供应商输入框里显示的文字，编码跟在后面便于核对 */
const directPartnerLabel = computed(() => (directForm.value.partnerCode
  ? `${directForm.value.partnerName}（${directForm.value.partnerCode}）`
  : ''))

/** 明细里历史供应商和表头所选不一致的商品名称，用来提示别选错 */
const directMismatchNames = computed(() => {
  if (!directForm.value.partnerCode) return []
  return directForm.value.items
    .filter(item => item.goodsCode
      && directPartnerHints.value[item.goodsCode]
      && directPartnerHints.value[item.goodsCode].partnerCode !== directForm.value.partnerCode)
    .map(item => `${item.goodsName}（历史：${directPartnerHints.value[item.goodsCode].partnerName}）`)
})

function openDirectPartnerSelect() {
  directPartnerSelectRef.value?.open()
}

function handleDirectPartnerSelected(partner) {
  directForm.value.partnerCode = partner.partnerCode
  directForm.value.partnerName = partner.partnerName
}

/**
 * 添加完商品后拉一次历史供应商。
 *
 * 一次带上明细里的全部商品编码，不按行发请求。拿回来做两件事：
 * 逐行显示「历史供应商」用于比对，以及在供应商还空着时自动填一次。
 *
 * 已经手填过的供应商绝不覆盖 —— 人选的一定比推荐的准，推荐只负责省掉第一次点击。
 */
function handleDirectGoodsAdded() {
  const codes = directForm.value.items.map(item => item.goodsCode).filter(Boolean)
  if (!codes.length) {
    directPartnerHints.value = {}
    return
  }
  recommendPartners(codes).then(response => {
    directPartnerHints.value = buildPartnerHints(response.data)
    if (directForm.value.partnerCode) return
    const picked = pickRecommendedPartner(response.data)
    if (!picked) return
    directForm.value.partnerCode = picked.partnerCode
    directForm.value.partnerName = picked.partnerName
    proxy.$modal.msgSuccess(`已按进货历史填入供应商「${picked.partnerName}」，不对请点「选择」改`)
  }).catch(() => {
    // 推荐失败不该挡住建单，清掉提示列自己选就行
    directPartnerHints.value = {}
  })
}
function submitDirectInbound() {
  const validItems = directForm.value.items.filter(item => item.goodsCode && Number(item.qty) > 0 && Number(item.unitPrice) >= 0)
  if (!directForm.value.partnerCode || !directForm.value.wareCode || !validItems.length) { proxy.$modal.msgWarning('请完善供应商、仓库和商品明细'); return }
  const details = validItems.map(item => ({ goodsCode: item.goodsCode, goodsName: item.goodsName, spec: item.spec, unit: item.unit, qty: item.qty, costPrice: item.unitPrice, costAmount: Number(item.amount || 0), wareCode: directForm.value.wareCode, batchCode: item.batchCode || undefined, serialNumbers: item.serialNumbers?.length ? item.serialNumbers : undefined }))
  // 不传 sourceDocCode，让后端统一生成 DIRECT-入库单号
  addDirectInbound({ ...directForm.value, docDate: directForm.value.purchaseDate, toWareCode: directForm.value.wareCode, details }).then(() => { proxy.$modal.msgSuccess('入库单创建成功，待审核'); directOpen.value = false; getInboundDocumentList() }).catch(error => proxy.$modal.msgError(error.message || '创建失败'))
}

function applyDefaultWarehouse(wareCode) {
  inboundForm.value.items.forEach(item => {
    item.wareCode = wareCode
  })
}

/** 生成入库单 */
async function handleCreateInbound(row = {}) {
  const purchaseCode = row.purchaseCode
  if (!purchaseCode) {
    proxy.$modal.msgWarning('请选择要生成入库单的采购订单')
    return
  }

  try {
    inboundReadonly.value = false
    // 获取采购订单详情
    const orderResponse = await getPurchaseOrderItems(purchaseCode)
    const orderItems = orderResponse.data || []

    inboundForm.value = {
      docCode: '',
      tableSuffix: '',
      purchaseCode,
      partnerName: row.partnerName,
      partnerCode: row.partnerCode || '',
      defaultWareCode: '',
        remark: '',
      items: orderItems.map(item => createInventoryDocumentLine({
        goodsCode: item.goodsCode,
        goodsName: item.goodsName,
        spec: item.spec,
        unit: item.unit,
        qty: item.qty,
        receivedQty: item.receivedQty || 0,
        wareCode: item.warehouseCode || '',
        batchCode: item.batchCode || '',
        inboundQty: item.qty - (item.receivedQty || 0),
        serialNumbers: item.serialNumbers || [],
        batchStrategy: 'none'
      }))
    }

    await enrichInboundTrackingStrategies(inboundForm.value.items)
    inboundDialogTitle.value = '生成采购入库单'
    inboundOpen.value = true
  } catch (error) {
    proxy.$modal.msgError('获取采购订单详情失败')
  }
}

/** 提交生成入库单 */
function submitInbound() {
  const items = inboundForm.value.items.filter(item => item.inboundQty > 0)
  if (items.length === 0) {
    proxy.$modal.msgWarning('请填写入库数量')
    return
  }

  const missingWarehouse = items.find(item => !item.wareCode)
  if (missingWarehouse) {
    proxy.$modal.msgWarning(`请为商品“${missingWarehouse.goodsName || missingWarehouse.goodsCode}”选择入库仓库`)
    return
  }

  const data = items.map(item => ({
    detailCode: item.detailCode || undefined,
    goodsCode: item.goodsCode,
    goodsName: item.goodsName,
    spec: item.spec,
    unit: item.unit,
    qty: item.inboundQty,
    wareCode: item.wareCode,
    batchCode: item.batchCode || undefined,
    serialNumbers: item.serialNumbers && item.serialNumbers.length > 0 ? item.serialNumbers : undefined
  }))

  const request = inboundForm.value.docCode
    ? updateInbound({
      docCode: inboundForm.value.docCode,
      tableSuffix: inboundForm.value.tableSuffix,
      docType: 'RK',
      docTypeName: '采购入库',
      sourceDocCode: inboundForm.value.purchaseCode,
      sourceDocType: 'CG',
      partnerCode: inboundForm.value.partnerCode || undefined,
      partnerName: inboundForm.value.partnerName || undefined,
      toWareCode: inboundForm.value.defaultWareCode || undefined,
      remark: inboundForm.value.remark || undefined,
      details: data
    })
    : createInbound(inboundForm.value.purchaseCode, data)
  request.then(response => {
    proxy.$modal.msgSuccess('入库单创建成功')
    inboundOpen.value = false
    getList()
    getInboundDocumentList()
  }).catch(error => {
    proxy.$modal.msgError(error.message || '创建失败')
  })
}

async function handleViewInbound(row) {
  try {
    const response = await getInbound(row.docCode, row.tableSuffix)
    const doc = response.data
    inboundForm.value = {
      docCode: doc.docCode,
      tableSuffix: doc.tableSuffix || row.tableSuffix || '',
      purchaseCode: doc.sourceDocCode,
      sourceDocType: doc.sourceDocType || '',
      partnerName: doc.partnerName || '',
      partnerCode: doc.partnerCode || '',
      defaultWareCode: doc.toWareCode || '',
      remark: doc.remark || '',
      items: (doc.details || []).map(item => createInventoryDocumentLine({
        detailCode: item.detailCode,
        goodsCode: item.goodsCode,
        goodsName: item.goodsName,
        spec: item.spec,
        unit: item.unit,
        qty: item.qty,
        receivedQty: 0,
        inboundQty: item.qty,
        costPrice: item.costPrice,
        costAmount: item.costAmount,
        wareCode: item.wareCode || doc.toWareCode || '',
        batchCode: item.batchCode || '',
        serialNumbers: item.serialNumbers || [],
        batchStrategy: 'none'
      }))
    }
    await enrichInboundTrackingStrategies(inboundForm.value.items)
    inboundReadonly.value = true
    inboundDialogTitle.value = '查看采购入库单'
    inboundOpen.value = true
  } catch (error) {
    proxy.$modal.msgError('获取入库单详情失败')
  }
}

/**
 * 手工直接入库没有真实采购单号，后端统一写的是 DIRECT-入库单号（见
 * PurchaseInboundServiceImpl#createDirectInbound）。这个号只用于追溯，
 * 页面上不往外露，一律显示成「手动入库」。
 */
function isManualInbound(sourceDocType, sourceDocCode) {
  return String(sourceDocType || '').toUpperCase() === 'DIRECT'
    || String(sourceDocCode || '').startsWith('DIRECT-')
}

/** 查看入库单弹窗里的采购单号文案；提交时仍然用原始的 inboundForm.purchaseCode */
const inboundPurchaseCodeLabel = computed(() => {
  const { purchaseCode, sourceDocType } = inboundForm.value
  return isManualInbound(sourceDocType, purchaseCode) ? '手动入库' : (purchaseCode || '-')
})

const BATCH_STRATEGY_LABELS = {
  none: '无批次',
  batch: '批次管理',
  serial: '序列号管理'
}

/** 批次策略文案，与商品档案里的口径一致 */
function batchStrategyLabel(strategy) {
  return BATCH_STRATEGY_LABELS[String(strategy || 'none').trim().toLowerCase()] || '无批次'
}

/** 根据商品主数据加载批次/序列号策略，避免页面把两种追踪方式同时展示。 */
async function enrichInboundTrackingStrategies(items) {
  await Promise.all((items || []).map(async item => {
    if (!item.goodsCode) return
    try {
      const response = await getGoods(item.goodsCode)
      const goods = response.data
      item.batchStrategy = goods?.batchStrategy || 'none'
      if (item.batchStrategy !== 'serial') item.serialNumbers = []
      if (item.batchStrategy !== 'batch') item.batchCode = ''
    } catch (error) {
      item.batchStrategy = 'none'
      item.serialNumbers = []
      item.batchCode = ''
    }
  }))
}

/**
 * 打开序列号输入对话框。
 * 手工直接入库的序列号已由 GoodsLineTable 内部处理，这里只服务于
 * 「采购订单生成入库单」对话框。
 */
function openSerialDialog(index) {
  serialDialogIndex.value = index
  const item = inboundForm.value.items[index]
  serialDialogText.value = formatSerialNumbers(item.serialNumbers)
  serialDialogOpen.value = true
}

/** 确认序列号输入 */
function confirmSerialDialog() {
  const index = serialDialogIndex.value
  if (index >= 0 && index < inboundForm.value.items.length) {
    inboundForm.value.items[index].serialNumbers = parseSerialNumbers(serialDialogText.value)
  }
  serialDialogOpen.value = false
}

/** 审核或驳回采购入库单 */
function handleConfirmInbound(row, approved) {
  const action = approved ? '审核通过' : '驳回'
  proxy.$modal.confirm(`确认${action}入库单“${row.docCode}”？`).then(function () {
    return confirmInbound(row.docCode, row.tableSuffix, approved)
  }).then(() => {
    proxy.$modal.msgSuccess(action + '成功')
    getList()
    getInboundDocumentList()
  }).catch(() => {})
}

/** 状态筛选变了就回到第一页再查，否则会停在新结果里并不存在的页码上 */
function handleDocumentQuery() {
  documentQuery.pageNum = 1
  getInboundDocumentList()
}

/**
 * 认首页看板带过来的 ?confirmState=待审核。
 *
 * @returns 是否真的改了筛选条件——没改就不必额外发一次请求
 */
function applyRouteFilter() {
  const wanted = useRoute().query.confirmState
  if (!wanted || documentQuery.confirmState === wanted) return false
  documentQuery.confirmState = wanted
  documentQuery.pageNum = 1
  return true
}

loadLegacyInboundSwitch().then(getList)
applyRouteFilter()
getInboundDocumentList()
loadWarehouseOptions()

// 本页被 keep-alive 缓存，切走再切回来不会重新执行上面的初始化，
// 入库单列表会停在离开前的快照（比如别处刚审核过，这里还显示待审核）。
// onActivated 首次挂载后也会触发一次，用标志位跳过，避免重复请求。
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) {
    skipFirstActivate = false
    return
  }
  applyRouteFilter()
  getInboundDocumentList()
  if (legacyInboundEnabled.value) getList()
})
</script>

<style scoped>
/* 紧挨着分隔线，别再撑出一整行的上边距 */
.document-filter {
  margin-bottom: 4px;
}

.document-filter :deep(.el-form-item) {
  margin-bottom: 8px;
}

:deep(.inventory-document-dialog) {
  max-width: 1440px;
}

:deep(.inventory-document-dialog .el-dialog__body) {
  padding-top: 12px;
}

.detail-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 12px;
  padding-left: 12px;
  border-left: 4px solid var(--el-color-primary);
}

.detail-section-title {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.detail-section-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:deep(.document-detail-table th.el-table__cell) {
  height: 46px;
  color: #344054;
  background: #f4f7fb !important;
  font-weight: 600;
}

:deep(.document-detail-table td.el-table__cell) {
  padding: 10px 0;
}

/*
  这里原本给可填写的列（.editable-column）刷一层 #f5f9ff 浅蓝底，提示「这列能填」，已删除：
  色值写死只对浅色主题成立，暗色主题下变成一排白块；又因为表格开了 stripe，
  偶数行被 Element Plus 优先级更高的斑马纹规则盖住，只有奇数行白，看着像渲染坏了。
  要恢复这个提示别再写死色值，用 background: var(--el-fill-color-light)。
*/
:deep(.document-detail-table .el-input__wrapper),
:deep(.document-detail-table .el-select__wrapper) {
  min-height: 34px;
}

.shipment-hint {
  line-height: 32px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.shipment-warn {
  color: var(--el-color-warning);
}

.manual-inbound {
  color: var(--el-text-color-secondary);
}

.serial-tip {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

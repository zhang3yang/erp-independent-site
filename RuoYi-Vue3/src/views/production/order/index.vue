<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="工单号"><el-input v-model="queryParams.productionCode" clearable @keyup.enter="getList" /></el-form-item>
      <el-form-item label="产出商品"><el-input v-model="queryParams.productCode" clearable @keyup.enter="getList" /></el-form-item>
      <!--
        「在产」库里不存在这个状态，是待生产 + 待领料 + 生产中三种的合称，由后端的
        selectProductionOrderList 翻成 in 条件。首页看板那张「在产工单」卡片点进来就带
        这个值，两边口径必须是同一个。它不进 statuses：那个数组是真实状态的清单。
      -->
      <el-form-item label="状态"><el-select v-model="queryParams.status" clearable style="width: 150px"><el-option label="在产（未完工）" value="在产" /><el-option v-for="item in statuses" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="getList">查询</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" v-hasPermi="['production:order:add']" @click="openAdd">新建工单</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="工单号" prop="productionCode" width="170"><template #default="scope"><el-link type="primary" @click="openDetail(scope.row)">{{ scope.row.productionCode }}</el-link></template></el-table-column>
      <el-table-column label="产出商品" prop="productName" min-width="160" />
      <!-- 订单生产的工单把来源订单号带出来：不点进详情也能看出这批货是给谁做的 -->
      <el-table-column label="来源" min-width="150">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.sourceType === 'order' ? 'warning' : 'info'">
            {{ scope.row.sourceType === 'order' ? '订单生产' : '备货生产' }}
          </el-tag>
          <span v-if="scope.row.sourceOrderCodes" class="production-hint">{{ scope.row.sourceOrderCodes }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划数量" prop="planYQty" width="110" />
      <el-table-column label="实际数量" prop="actualYQty" width="110" />
      <el-table-column v-if="canSeeCost" label="单位成本" prop="unitCost" width="110" />
      <el-table-column label="状态" prop="status" width="110"><template #default="scope"><el-tag :type="tagType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column>
      <el-table-column label="计划日期" prop="planDate" width="150"><template #default="scope">{{ parseTime(scope.row.planDate, '{y}-{m}-{d}') }}</template></el-table-column>
      <el-table-column label="操作" width="380" fixed="right"><template #default="scope">
        <el-button link type="primary" icon="View" @click="openDetail(scope.row)">详情</el-button>
        <!-- 只有待生产工单能改能删：领过料就已经动过库存、写过生产日志，改删都会让单据追溯断链 -->
        <el-button v-if="scope.row.status === '待生产'" link type="primary" icon="Edit" v-hasPermi="['production:order:add']" @click="openEdit(scope.row)">修改</el-button>
        <!-- 支持分批领料：领了一部分停在「待领料」，还能接着领，领足才转「生产中」 -->
        <el-button v-if="scope.row.status === '待生产' || scope.row.status === '待领料'" link type="success" icon="Box" v-hasPermi="['production:order:pick']" @click="openExecute(scope.row, 'pick')">领料</el-button>
        <el-button v-if="scope.row.status === '生产中'" link type="warning" icon="Finished" v-hasPermi="['production:order:complete']" @click="openExecute(scope.row, 'complete')">完工</el-button>
        <!-- 领过料就有在制品可退；已完工工单也可能还有没退的剩余料，与后端放行的状态保持一致 -->
        <el-button v-if="['待领料', '生产中', '已完工'].includes(scope.row.status)" link type="info" icon="Refresh" v-hasPermi="['production:order:return']" @click="openExecute(scope.row, 'return')">退料</el-button>
        <!-- 发票晚到是常态：已完工未关闭的工单还能补录费用，关闭即成本定案 -->
        <el-button v-if="scope.row.status === '已完工'" link type="warning" icon="Money" v-hasPermi="['production:order:complete']" @click="openCostAdjust(scope.row)">调成本</el-button>
        <!--
          关闭是工单唯一的结案动作：已完工是正常收尾，没完工是「这批不做了」的终止。
          终止要先把领出去的料退干净（后端校验），并说明原因。已关闭的不再显示。
        -->
        <el-button v-if="scope.row.status !== '已关闭'" link type="danger" icon="CircleClose"
          v-hasPermi="['production:order:close']" @click="closeOrder(scope.row)">
          {{ scope.row.status === '已完工' ? '关闭' : '终止' }}
        </el-button>
        <el-button v-if="scope.row.status === '待生产'" link type="danger" icon="Delete" v-hasPermi="['production:order:remove']" @click="removeOrder(scope.row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="formOpen" :title="form.id ? '修改生产工单' : '新建生产工单'" width="900px" append-to-body :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <!-- 工单产出商品（主表 productcode/productname）不再单独录入，保存时由产出明细第一行自动带出 -->
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="来源" prop="sourceType"><el-select v-model="form.sourceType" style="width:100%" @change="handleSourceTypeChange"><el-option label="订单生产" value="order" /><el-option label="备货生产" value="stock" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="计划产出数量"><el-input :model-value="detailPlanTotal.toFixed(4)" disabled style="width:100%" /><div class="production-hint">自动等于各产出计划数量之和</div></el-form-item></el-col><el-col :span="8"><el-form-item label="计划日期" prop="planDate"><el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="16"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col></el-row>
        <!--
          产出明细 + 各自的物料清单。配方按产出商品分开：一个产出一张物料表，
          计划用量 = 单位用量 × 本产出的计划数量，不再按整单产出合计算。
          编码、名称、规格、单位都是商品档案上的固定信息，只能从选商品弹窗挑，工单上不可改。
        -->
        <el-divider content-position="left">产出明细</el-divider>
        <!--
          两种来源各只有一个入口：备货生产从商品档案挑，订单生产从销售订单明细挑。
          订单工单不给「添加产出」，是因为那样加出来的行不挂任何订单，跟工单来源自相矛盾；
          想多做一些进库存，把订单带出来那行的计划数量调大即可。
        -->
        <el-button v-if="form.sourceType !== 'order'" type="primary" link icon="Plus" @click="addDetailLine">添加产出</el-button>
        <el-button v-else type="warning" link icon="Tickets" @click="openCandidatePicker">选销售订单</el-button>
        <div class="production-hint">每个产出商品各有各的配方，物料录在对应的产出下面</div>
        <el-alert v-if="form.sourceType === 'order'" type="info" show-icon :closable="false" class="production-alert"
          title="订单生产必须挂上销售订单"
          description="点「选销售订单」挑要做的订单明细，按商品自动并成产出行。选单表里列的是所有已过审、还没发完的明细，不分备货来源。订单不占用库存，「库存里有没有现货」不影响能不能排产。标「专门生产」的明细，完工入库时成品批次会绑给它，别的订单发不走；普通件进通用库存，挂订单只是为了能从订单追溯到工单。一条订单明细同时只能挂一张工单；把计划数量调大就能比分配量多做，多出来的部分算备货。" />
        <div v-if="!form.details.length" class="production-empty">
          还没有产出商品，点「{{ form.sourceType === 'order' ? '选销售订单' : '添加产出' }}」选
        </div>
        <div v-for="(detailRow, detailIndex) in form.details" :key="detailRow.rowKey" class="production-output">
          <div class="production-output-head">
            <span class="production-output-name">{{ detailRow.productCode }} {{ detailRow.productName }}</span>
            <span class="production-output-qty">
              计划数量
              <!-- 计划产出至少 1 个：0.0001 个成品不是业务，只是 spinner 一路点到底的产物 -->
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="detailRow.planYQty" :min="1" size="small" style="width:150px" />
            </span>
            <el-button link type="danger" icon="Delete" @click="form.details.splice(detailIndex, 1)">删除产出</el-button>
          </div>
          <div class="production-output-body">
            <!--
              这一行产出是为哪些订单明细做的。分配量之和不能超过本行计划产出，
              单行也不能超过该订单明细的未发量（订单量 − 已发量）——两条后端都会再算一遍，
              前端只是先给个提示。标「专门生产」的分配，完工入库时成品批次会绑给那条订单明细。
            -->
            <template v-if="detailRow.allocations && detailRow.allocations.length">
              <div class="production-output-bar">
                <span>来源订单</span>
                <span :class="allocationExceeded(detailRow) ? 'production-warn' : 'production-hint'">
                  已分配 {{ formatQty(allocationTotal(detailRow)) }} / 计划 {{ num(detailRow.planYQty).toFixed(4) }}
                  <template v-if="allocationExceeded(detailRow)">　分配量超过计划产出，请调大计划或调小分配</template>
                </span>
              </div>
              <el-table :data="detailRow.allocations" border size="small" :row-class-name="candidateRowClass">
                <!-- 标记跟着分配行走：带进来之后还看得出哪几条是不缺货、专门为定制件做的 -->
                <el-table-column label="销售订单" min-width="240">
                  <template #default="scope">
                    {{ scope.row.orderCode }}
                    <el-tag v-if="isSpecialCandidate(scope.row)" size="small" type="warning" effect="plain" class="ml8">专门生产</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="月份" prop="tableSuffix" width="80" align="center" />
                <el-table-column label="订单未发" width="100" align="right">
                  <template #default="scope"><span class="production-readonly">{{ num(scope.row.pendingQty).toFixed(4) }}</span></template>
                </el-table-column>
                <el-table-column label="本工单生产" width="170">
                  <template #default="scope">
                    <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qty" :min="0.0001" :max="num(scope.row.pendingQty)" size="small" style="width:100%" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="70">
                  <template #default="scope"><el-button link type="danger" icon="Delete" @click="detailRow.allocations.splice(scope.$index, 1)" /></template>
                </el-table-column>
              </el-table>
            </template>
            <div class="production-output-bar">
              <span>物料清单</span>
              <el-button type="primary" link icon="Plus" @click="addMaterialLine(detailIndex)">添加物料</el-button>
            </div>
            <el-table :data="detailRow.materials" border size="small" empty-text="这个产出还没有物料">
              <el-table-column label="物料编码" prop="goodsCode" width="130" />
              <el-table-column label="物料名称" prop="goodsName" min-width="140" show-overflow-tooltip />
              <el-table-column label="规格" width="110"><template #default="scope"><span class="production-readonly">{{ scope.row.spec || '-' }}</span></template></el-table-column>
              <el-table-column label="单位" width="70"><template #default="scope"><span class="production-readonly">{{ scope.row.unit || '-' }}</span></template></el-table-column>
              <el-table-column label="单位用量" width="165">
                <template #default="scope"><decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qtyPerUnit" :min="0.0001" size="small" style="width:100%" /></template>
              </el-table-column>
              <el-table-column label="计划用量" width="110" align="right">
                <template #default="scope"><span class="production-readonly">{{ materialPlanQty(detailRow, scope.row) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="scope"><el-button link type="danger" icon="Delete" @click="detailRow.materials.splice(scope.$index, 1)" /></template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">保存</el-button><el-button @click="formOpen = false">取消</el-button></template>
    </el-dialog>

    <!--
      从销售订单明细里选要做的活。列的是「还没发完」的行——订单不占用库存，
      「库存里有没有现货」不影响能不能为这条明细排产。
      月份留空查全部月表：上月下的单这月才排产是常态，只给当月会让计划员找不到活。
    -->
    <el-dialog v-model="candidateOpen" title="选销售订单明细" width="1080px" append-to-body :close-on-click-modal="false">
      <el-form :inline="true" class="mb8">
        <el-form-item label="订单月份">
          <el-date-picker v-model="candidateMonth" type="month" value-format="YYYYMM" placeholder="全部月份" clearable style="width:150px" @change="handleCandidateMonthChange" />
        </el-form-item>
        <el-form-item label="商品">
          <el-input v-model="candidateKeyword" placeholder="按编码或名称过滤" clearable style="width:200px" />
        </el-form-item>
        <!-- 选项来自已取回的行，不另发请求：一张订单常有好几条明细，按单看才知道这单要做哪些成品 -->
        <el-form-item label="销售订单">
          <el-select v-model="candidateOrderCode" filterable clearable placeholder="全部订单" style="width:230px">
            <el-option v-for="item in candidateOrderOptions" :key="item.orderCode"
              :label="`${item.orderCode}（${item.count} 条）`" :value="item.orderCode" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button icon="Refresh" @click="loadCandidates">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" show-icon :closable="false" class="mb8"
        title="列的是已过审、还没发完的订单明细"
        description="一件没发的和发了一部分没发完的都在这里，挑哪些做由你定。排产上限是「订单未发」（订单量 − 已发量）；订单不占用库存，所以「库存里有没有现货」不影响能不能排产。草稿和待审核还没过审，替它们排产等于绕过审核；已完成、已取消、已关闭没有再生产的意义——这些都不在列表里。" />
      <el-alert type="warning" show-icon :closable="false" class="mb8"
        title="标黄的行是「专门生产」，做出来的货只归这张订单"
        description="标记来自新建销售订单时勾的「专门生产」，这里只读。这些行完工入库时，成品批次会绑到对应的订单明细上，别的订单发货挑不到它，直到本单发完或订单被取消/关闭。标「常规」的行做出来的是通用库存，谁先发货谁拿到——普通标准件走备货生产还是订单生产都行，选订单生产只是为了能从订单追溯到工单。" />
      <el-table ref="candidateTableRef" :data="filteredCandidates" v-loading="candidateLoading" border height="420"
        row-key="detailCode" :row-class-name="candidateRowClass"
        @selection-change="rows => candidateSelection = rows"
        empty-text="没有还没发完的订单明细">
        <el-table-column type="selection" width="46" reserve-selection />
        <el-table-column label="销售订单" prop="orderCode" min-width="170" />
        <el-table-column label="月份" prop="tableSuffix" width="80" align="center" />
        <el-table-column label="状态" prop="orderState" width="90">
          <template #default="scope"><el-tag size="small" type="warning">{{ scope.row.orderState }}</el-tag></template>
        </el-table-column>
        <el-table-column label="商品" min-width="180" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.goodsCode }} {{ scope.row.goodsName }}</template>
        </el-table-column>
        <el-table-column label="订单量" prop="qty" width="90" align="right" />
        <el-table-column label="已发" prop="shippedQty" width="80" align="right" />
        <!-- 排产上限就是它。订单不再预占库存，「缺口」那一列随之取消了 -->
        <el-table-column label="订单未发" width="95" align="right">
          <template #default="scope"><strong class="production-warn">{{ scope.row.pendingQty }}</strong></template>
        </el-table-column>
        <!--
          定制标记来自销售订单明细（下单时人工勾的），这里只读，决定的是做出来的货归谁：
          定制件完工入库时批次绑到这条订单明细上，别的订单出库挑不到；
          普通件进通用库存，谁先发货谁拿到。
        -->
        <el-table-column label="生产性质" width="105" align="center">
          <template #default="scope">
            <el-tag v-if="isSpecialCandidate(scope.row)" size="small" type="warning" effect="plain">专门生产</el-tag>
            <span v-else class="production-readonly">常规</span>
          </template>
        </el-table-column>
        <!--
          已挂工单的明细仍然可选：那张工单可能已经关闭，关闭后这一行本就该让新工单接手。
          前端看不出对方状态，所以只提示不禁选，真占着的会被后端拦下并说清是哪张工单。
        -->
        <!--
          「当前来源」列去掉了：备货来源订单页已经不展示也不再能登记，这里显示它没有意义。
          留下的是「已挂哪张工单」——一条订单明细同时只能挂一张在产工单，后端会拒
          （ProductionOrderServiceImpl 的占用检查），不先摆出来计划员会挑完了才被拒。
        -->
        <el-table-column label="已挂工单" min-width="150">
          <template #default="scope">
            <!-- 不用 production-warn：它带 margin-left，那是给「跟在标签后面」准备的，独占一格会歪 -->
            <span v-if="scope.row.sourceCode" class="candidate-held">{{ scope.row.sourceCode }}</span>
            <span v-else class="production-hint">—</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="production-hint">
          已选 {{ candidateSelection.length }} 条，合计 {{ formatQty(candidateSelectedQty) }}
          <!-- 带上专门生产的条数：整表标黄的行未必都在当前筛选下看得见，勾完总数摆在这里更难漏 -->
          <span v-if="candidateSelectedSpecial" class="candidate-custom">
            　其中 {{ candidateSelectedSpecial }} 条是专门生产，做出来的货只归对应订单
          </span>
        </span>
        <el-button type="primary" :disabled="!candidateSelection.length" @click="confirmCandidates">带入产出明细</el-button>
        <el-button @click="candidateOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 三段都带物料明细表格，弹窗要留够宽度，完工还多一张产出表 -->
    <el-dialog v-model="executeOpen" :title="executeTitle" :width="executeType === 'complete' ? '1000px' : '860px'" append-to-body :close-on-click-modal="false">
      <el-form :model="executeForm" label-width="120px" v-loading="executeLoading">
        <!-- 三段都要指定物料所在仓库：领料从这个仓库出，消耗从这个仓库的在制品出，退料退回这个仓库 -->
        <el-form-item :label="materialWareLabel">
          <el-select v-model="executeForm.materialWareCode" filterable style="width:100%" @change="handleExecuteWarehouseChange">
            <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
          </el-select>
          <div v-if="executeType === 'complete'" class="production-hint">消耗的是车间在制品，选领料时的那个仓库</div>
        </el-form-item>
        <el-form-item label="成品入库仓库" v-if="executeType === 'complete'">
          <el-select v-model="executeForm.productWareCode" filterable style="width:100%">
            <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
          </el-select>
        </el-form-item>

        <template v-if="executeType === 'complete'">
          <el-divider content-position="left">实际产出</el-divider>
          <!--
            订单生产的工单，完工入库后自动认领来源订单：定制件的成品批次绑给对应的订单明细，
            备齐的订单从「备货中」回到「已确认」，可以去建发货批次了。
            少做的时候按分配顺序先到先得。
          -->
          <el-alert v-if="hasOrderClaim" type="success" show-icon :closable="false" class="production-alert"
            title="完工后会自动认领来源订单"
            description="标「专门生产」的分配，成品批次会绑到对应的销售订单明细上，别的订单发货挑不到；普通件进通用库存。定制件备齐后，订单会从「备货中」回到「已确认」。实际产出少于分配量时按下表顺序先到先得，排在后面的订单继续等。" />
          <el-table :data="executeForm.outputs" border size="small">
            <el-table-column label="商品编码" prop="productCode" width="140" />
            <el-table-column label="商品名称" prop="productName" min-width="150" />
            <el-table-column label="计划数量" prop="planYQty" width="110" />
            <el-table-column label="实际产出" width="170">
              <template #default="scope">
                <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.actualYQty" :min="0" style="width:100%"
                  @change="handleOutputQtyChange(scope.row)" />
              </template>
            </el-table-column>
            <!--
              成品的批次 / 序列号：批次管理的成品留空由系统生成批次号，序列号管理的必须逐个录。
              不带这两样，入库后库存里就只有数量没有批次，FIFO 结算和按序列号发货都走不通。
            -->
            <el-table-column label="批次" width="180">
              <template #default="scope">
                <el-input v-if="outputTracked(scope.row)" v-model="scope.row.batchCode" size="small"
                  placeholder="留空自动生成" maxlength="50" />
                <span v-else class="production-readonly">{{ scope.row.batchStrategy ? '无需填写' : '读取中…' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="序列号" width="120" align="center">
              <template #default="scope">
                <el-button v-if="scope.row.batchStrategy === 'serial'" link type="primary" size="small"
                  :disabled="num(scope.row.actualYQty) <= 0" @click="openOutputSerial(scope.$index)">
                  {{ (scope.row.serialNumbers || []).length ? `${scope.row.serialNumbers.length} 个` : '必须输入' }}
                </el-button>
                <span v-else class="production-readonly">无需填写</span>
              </template>
            </el-table-column>
            <!-- 填多少能认领多少，一眼看出谁会被短给 -->
            <el-table-column v-if="hasOrderClaim" label="认领来源订单" min-width="240">
              <template #default="scope">
                <div v-for="(claim, index) in claimPreview(scope.row)" :key="index"
                  :class="claim.got > 0 ? 'production-hint' : 'production-warn'">
                  {{ claim.orderCode }} 需 {{ formatQty(claim.want) }}，可认领 {{ claim.got.toFixed(4) }}
                </div>
                <span v-if="!(scope.row.allocations || []).length" class="production-readonly">不挂订单，进库存</span>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!--
          三段共用一张物料表：列和上限随环节变，展开行放批次/序列号明细。
          批次商品的数量由展开行里按批次填的量合计出来，主行只读——库存模块要按批次
          定位成本，一次动几个批次就得拆几行报给后端。
        -->
        <el-divider content-position="left">{{ materialSectionTitle }}</el-divider>
        <el-table ref="materialTableRef" :data="executeForm.materials" border size="small" empty-text="该工单没有物料" row-key="materialCode">
          <!--
            展开列必须钉在左边：领料模式下这张表的列宽合计约 1058px，弹窗只有 860px，
            一定会横向滚动。不钉住的话用户拖到右边看「追踪」列的提示时，48px 的展开箭头
            正好滚出视野，看着像这一行没有箭头。
          -->
          <el-table-column type="expand" fixed="left">
            <template #default="scope">
              <div class="production-tracking">
                <template v-if="needsBatch(scope.row)">
                  <div class="production-hint">按批次填写本次数量，合计自动写回上面的「{{ stageQtyLabel }}」</div>
                  <el-table :data="scope.row.availableBatches" border size="small"
                            :empty-text="executeForm.materialWareCode ? '该状态下没有可用批次' : `请先在上方选择${materialWareLabel}`">
                    <el-table-column label="批次" prop="batchCode" min-width="180" />
                    <el-table-column label="可用数量" prop="remainQty" width="120" align="right" />
                    <el-table-column v-if="canSeeCost" label="成本单价" width="130" align="right">
                      <template #default="batch">{{ batch.row.costPrice == null ? '-' : Number(batch.row.costPrice).toFixed(6) }}</template>
                    </el-table-column>
                    <el-table-column :label="stageQtyLabel" width="170">
                      <template #default="batch">
                        <decimal-input :min-decimals="0" :max-decimals="4" v-model="batch.row.qty" :min="0" :max="num(batch.row.remainQty)" size="small" style="width:100%" @change="syncTrackedQty(scope.row)" />
                      </template>
                    </el-table-column>
                  </el-table>
                </template>
                <template v-else-if="needsSerial(scope.row)">
                  <div class="production-hint">选中几个序列号就是几个数量</div>
                  <!-- 序列号按仓库查，仓库没选时列表必然是空的，空提示要说清是哪一种空 -->
                  <el-select v-model="scope.row.serialNumbers" multiple filterable collapse-tags collapse-tags-tooltip
                             :no-data-text="executeForm.materialWareCode ? '该仓库的这一格库存里没有登记序列号' : `请先在上方选择${materialWareLabel}`"
                             placeholder="选择序列号" style="width:100%" @change="syncTrackedQty(scope.row)">
                    <el-option v-for="item in scope.row.serialOptions" :key="item.serialNo"
                               :label="item.batchCode ? `${item.serialNo}（${item.batchCode}）` : item.serialNo" :value="item.serialNo" />
                  </el-select>
                </template>
                <span v-else class="production-readonly">
                  {{ scope.row.batchStrategy === 'batch' && executeType === 'complete' && scope.row.costCalculation === 'fifo'
                     ? '按 FIFO 自动结算批次，无需选择' : '该物料不做批次追踪，直接填数量即可' }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="物料编码" prop="goodsCode" width="130" />
          <el-table-column label="物料名称" prop="goodsName" min-width="140" show-overflow-tooltip />
          <!-- 同一个物料给两个产出各用一份时是两行，这一列区分它们 -->
          <el-table-column label="所属产出" prop="outputName" min-width="130" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="60" align="center" />
          <el-table-column label="计划用量" prop="totalQty" width="95" align="right" />
          <el-table-column label="已领" prop="pickedQty" width="85" align="right" />
          <el-table-column v-if="executeType !== 'pick'" label="已耗" prop="actualQty" width="85" align="right" />
          <el-table-column v-if="executeType !== 'pick'" label="已退" prop="returnedQty" width="85" align="right" />
          <el-table-column :label="stageLimitLabel" width="95" align="right">
            <template #default="scope"><span class="production-readonly">{{ stageLimit(scope.row) }}</span></template>
          </el-table-column>
          <el-table-column :label="stageQtyLabel" width="165">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-if="!isTracked(scope.row)" v-model="scope.row.qty" :min="0" :max="stageLimit(scope.row)" size="small" style="width:100%" />
              <span v-else class="production-tracked-qty">{{ formatQty(scope.row.qty) }}</span>
            </template>
          </el-table-column>
          <!-- 标签本身可点：提示写着「展开选序列号」，用户的下一个动作就是点它，
               不该逼他把表格拖回最左边去找箭头 -->
          <el-table-column label="追踪" width="130">
            <template #default="scope">
              <el-tag v-if="isTracked(scope.row)" size="small" :type="trackingTagType(scope.row)"
                      class="production-tracking-tag" @click="toggleMaterialExpand(scope.row)">
                {{ trackingTagText(scope.row) }}
              </el-tag>
              <span v-else class="production-readonly">-</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="production-hint">{{ stageHint }}</div>

        <template v-if="executeType === 'complete'">
          <el-divider content-position="left">费用</el-divider>
          <el-row :gutter="16">
            <el-col :span="8"><el-form-item label="加工费" label-width="80px"><el-input-number v-model="executeForm.processingCost" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
            <el-col v-if="canSeeCost" :span="8"><el-form-item label="运费" label-width="80px"><el-input-number v-model="executeForm.freightCost" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="其他费用" label-width="80px"><el-input-number v-model="executeForm.otherCost" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          </el-row>
          <div class="production-hint">
            完工会先开一张「生产消耗」出库单把实际消耗的物料永久出库，物料成本 = 这张出库单按实际批次结算出的成本，未消耗的部分留在车间，走退料退回库存。
            总成本 = 物料成本 + 加工费 + 运费 + 其他费用（实际产出合计 {{ totalOutputQty.toFixed(4) }}）。
            每个产出先认领自己配方消耗掉的物料成本，三项费用再按各产出的物料成本占比分摊，所以各产出的单位成本不一样。
          </div>
        </template>

        <el-form-item label="备注"><el-input v-model="executeForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <!-- 提交中禁用并转圈：完工会开出消耗单和成品入库单，点两下就是两套单据 -->
      <template #footer><el-button type="primary" :loading="executeSubmitting" :disabled="executeLoading || executeSubmitting" @click="submitExecute">确认执行</el-button><el-button @click="executeOpen = false">取消</el-button></template>
    </el-dialog>

    <!-- 成品序列号录入：每行一个，个数必须等于这一行的实际产出 -->
    <el-dialog v-model="outputSerialOpen" title="输入成品序列号" width="500px" append-to-body :close-on-click-modal="false">
      <div class="production-hint">实际产出 {{ outputSerialExpect }}，需要 {{ outputSerialExpect }} 个不重复的序列号，每行一个</div>
      <el-input v-model="outputSerialText" type="textarea" :rows="8" placeholder="每行一个序列号" />
      <template #footer>
        <el-button type="primary" @click="confirmOutputSerial">确定</el-button>
        <el-button @click="outputSerialOpen = false">取消</el-button>
      </template>
    </el-dialog>
    <!-- 完工后补录费用：只重算工单成本，不动已入库成品的库存成本 -->
    <el-dialog v-model="costOpen" title="调整工单成本" width="620px" append-to-body :close-on-click-modal="false">
      <el-form :model="costForm" label-width="110px" v-loading="costLoading">
        <el-form-item label="工单号">
          <span class="production-readonly">{{ costForm.productionCode }}</span>
        </el-form-item>
        <el-form-item v-if="canSeeCost" label="物料成本">
          <span>{{ formatAmount(costForm.materialCost) }}</span>
          <div class="production-hint">由生产消耗单据结算，事后不可改</div>
        </el-form-item>
        <el-form-item label="实际产出">
          <span>{{ formatQty(costForm.actualYQty) }}</span>
        </el-form-item>
        <el-form-item v-if="canSeeCost" label="加工费">
          <el-input-number v-model="costForm.processingCost" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="canSeeCost" label="运费">
          <el-input-number v-model="costForm.freightCost" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="canSeeCost" label="其他费用">
          <el-input-number v-model="costForm.otherCost" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-divider content-position="left">重算结果</el-divider>
        <el-form-item v-if="canSeeCost" label="总成本">
          <span>{{ formatAmount(costForm.totalCostBefore) }} → <strong>{{ formatAmount(newTotalCost) }}</strong></span>
        </el-form-item>
        <el-form-item v-if="canSeeCost" label="单位成本">
          <span>{{ num(costForm.unitCostBefore).toFixed(6) }} → <strong>{{ newUnitCost.toFixed(6) }}</strong></span>
        </el-form-item>
        <el-alert type="warning" show-icon :closable="false"
          title="库存成本不会同步"
          description="成品已按调整前的单位成本入库，这次只改工单和产出明细的成本。差额会记进生产日志（操作类型「费用调整」），需要时可据此在财务侧处理。" />
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="costSubmitting" :disabled="costLoading || costSubmitting || !costChanged" @click="submitCostAdjust">确认调整</el-button>
        <el-button @click="costOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="生产工单详情" width="1000px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="工单号">{{ detail.productionCode }}</el-descriptions-item>
        <el-descriptions-item label="产出商品">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ detail.planYQty }}</el-descriptions-item>
        <el-descriptions-item label="实际数量">{{ detail.actualYQty }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="物料成本">{{ detail.materialCost }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="加工费">{{ detail.processingCost }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="运费">{{ detail.freightCost }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="其他费用">{{ detail.otherCost }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="总成本">{{ detail.totalCost }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="单位成本">{{ detail.unitCost }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.sourceType === 'order' ? '订单生产' : '备货生产' }}</el-descriptions-item>
        <el-descriptions-item label="来源订单" :span="2">{{ detail.sourceOrderCodes || '—' }}</el-descriptions-item>
      </el-descriptions>
      <!-- 产出和它的配方放一起看：单位用量、计划用量都是相对这一行产出的 -->
      <el-divider content-position="left">产出与配方</el-divider>
      <div v-for="output in (detail.details || [])" :key="output.detailCode" class="production-output">
        <div class="production-output-head">
          <span class="production-output-name">{{ output.productCode }} {{ output.productName }}</span>
          <span class="production-readonly">计划 {{ output.planYQty }}　实际 {{ output.actualYQty }}</span>
          <span class="production-readonly">物料成本 {{ formatAmount(output.materialCost) }}　单位成本 {{ formatAmount(output.unitCost) }}　小计 {{ formatAmount(output.totalCost) }}</span>
        </div>
        <div class="production-output-body">
          <template v-if="(output.allocations || []).length">
            <div class="production-output-bar"><span>来源订单</span></div>
            <el-table :data="output.allocations" border size="small">
              <el-table-column label="销售订单" prop="orderCode" min-width="170" />
              <el-table-column label="月份" prop="tableSuffix" width="80" align="center" />
              <el-table-column label="订单明细" prop="detailCode" min-width="160" show-overflow-tooltip />
              <el-table-column label="商品" min-width="150" show-overflow-tooltip>
                <template #default="scope">{{ scope.row.goodsCode }} {{ scope.row.goodsName }}</template>
              </el-table-column>
              <el-table-column label="本工单生产" prop="qty" width="110" align="right" />
            </el-table>
          </template>
          <div class="production-output-bar"><span>物料清单</span></div>
          <el-table :data="output.materials || []" border size="small" empty-text="这个产出没有物料">
            <el-table-column label="编码" prop="goodsCode" width="130" />
            <el-table-column label="名称" prop="goodsName" min-width="140" show-overflow-tooltip />
            <el-table-column label="单位用量" prop="qtyPerUnit" align="right" />
            <el-table-column label="计划用量" prop="totalQty" align="right" />
            <el-table-column label="已领" prop="pickedQty" align="right" />
            <el-table-column label="已耗" prop="actualQty" align="right" />
            <el-table-column label="已退" prop="returnedQty" align="right" />
            <el-table-column label="可退" align="right">
              <template #default="scope">{{ returnableQty(scope.row) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <el-divider content-position="left">操作日志</el-divider>
      <el-table v-loading="logsLoading" :data="productionLogs" border empty-text="暂无操作日志">
        <el-table-column label="操作" prop="operation" width="90">
          <template #default="scope"><el-tag :type="operationTagType(scope.row.operation)">{{ operationName(scope.row.operation) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="关联单据" prop="docCode" min-width="160" />
        <el-table-column label="商品编码" prop="goodsCode" min-width="130" />
        <el-table-column label="数量" prop="qty" width="100" />
        <!--
          费用调整行记的是成本变化的差额（可能为负），不是本次操作金额，带上正负号区分。
          领料、退料和订单认领本来就不产生成本，显示「—」比一个 0.00 更清楚。
        -->
        <el-table-column v-if="canSeeCost" label="成本金额" prop="costAmount" width="110">
          <template #default="scope">
            <span v-if="scope.row.operation === '费用调整'">
              {{ num(scope.row.costAmount) > 0 ? '+' : '' }}{{ formatAmount(scope.row.costAmount) }}
            </span>
            <span v-else-if="noCostOperation(scope.row.operation)" class="production-readonly">—</span>
            <span v-else>{{ scope.row.costAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" prop="operationTime" width="165" />
        <el-table-column label="备注" prop="remark" min-width="130" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="selectedGoodsCodes" @confirm="handleGoodsSelected" />
  </div>
</template>

<script setup name="ProductionOrder">
import { ref, reactive, computed, getCurrentInstance, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { formatAmount } from '@/utils/currency'
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { listProductionOrder, getProductionOrder, addProductionOrder, updateProductionOrder, delProductionOrder, pickProductionOrder, completeProductionOrder, returnProductionMaterial, adjustProductionCost, closeProductionOrder, listProductionLogs } from '@/api/production/order'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { getGoods } from '@/api/inventory/goods'
import { getBomByProduct } from '@/api/production/bom'
import { listProductionCandidates } from '@/api/order/sales'
import { listAvailableBatch } from '@/api/inventory/batch'
import { listInStockSerial } from '@/api/inventory/serial'
import { QUALITY_FLAG, ONHAND_FLAG } from '@/utils/stockState'
import { parseSerialNumbers, formatSerialNumbers } from '@/utils/inventoryDocument'
// parseTime 只注册在 app.config.globalProperties 上，模板能用、script setup 里不能，必须显式导入。
import { parseTime } from '@/utils/ruoyi'
import DecimalInput from '@/components/DecimalInput'
import { formatQty } from '@/utils/decimal'
import { canSeeCost } from '@/utils/price'


const { proxy } = getCurrentInstance()
const loading = ref(false); const list = ref([]); const total = ref(0); const showSearch = ref(true)
// 「待领料」是分批领料的中间态：领了一部分、还没领足
const statuses = ['待生产', '待领料', '生产中', '已完工', '已关闭']; const warehouses = ref([])
const formOpen = ref(false); const detailOpen = ref(false); const executeOpen = ref(false); const executeType = ref('pick'); const detail = ref({})
const logsLoading = ref(false); const productionLogs = ref([])
const queryParams = reactive({ pageNum: 1, pageSize: 20, productionCode: undefined, productCode: undefined, status: undefined })
const emptyForm = () => ({ id: undefined, sourceType: 'stock', productCode: '', productName: '', planYQty: 0, planDate: parseTime(new Date(), '{y}-{m}-{d}'), remark: '', details: [] })
/** 计划产出的下界。做 0.0001 个成品不是业务，只是 spinner 一路点到底的产物 */
const MIN_PLAN_QTY = 1
// 产出行的渲染 key：同一个商品可以录两行（不同批计划），productCode 不唯一，用自增号
let detailRowSeq = 0
// allocations = 这行产出分给了哪些销售订单明细，备货生产恒为空
function nextDetailRow(productCode, productName, planYQty, materials, allocations) {
  return { rowKey: ++detailRowSeq, productCode, productName, planYQty, materials, allocations: allocations || [] }
}
const form = reactive(emptyForm())
const executeLoading = ref(false)
/** 提交中：完工一次会开出消耗单和成品入库单，双击就是两套单据，后端也会拒第二次 */
const executeSubmitting = ref(false)
// 成品序列号录入：每行一个，个数必须等于该行实际产出
const outputSerialOpen = ref(false)
const outputSerialIndex = ref(-1)
const outputSerialText = ref('')
const executeForm = reactive({
  productionCode: '', materialWareCode: '', productWareCode: '', remark: '',
  outputs: [], materials: [], processingCost: 0, freightCost: 0, otherCost: 0
})
const rules = { sourceType: [{ required: true, message: '请选择来源', trigger: 'change' }] }
// 销售订单明细选单：月份留空查全部月表，关键字只在已取回的行里过滤，不再打后端
const candidateOpen = ref(false)
const candidateLoading = ref(false)
const candidates = ref([])
const candidateSelection = ref([])
const candidateMonth = ref('')
const candidateKeyword = ref('')
const candidateOrderCode = ref('')
const candidateTableRef = ref(null)
// 完工后调整费用：物料成本只读（由消耗单结算），三项费用可改，总成本/单位成本前端先算给用户看，后端会再算一遍
const costOpen = ref(false)
const costLoading = ref(false)
const costSubmitting = ref(false)
const costForm = reactive({
  productionCode: '', materialCost: 0, actualYQty: 0,
  processingCost: 0, freightCost: 0, otherCost: 0, totalCostBefore: 0, unitCostBefore: 0
})
const newTotalCost = computed(() => num(costForm.materialCost) + num(costForm.processingCost)
  + num(costForm.freightCost) + num(costForm.otherCost))
const newUnitCost = computed(() => {
  const qty = num(costForm.actualYQty)
  return qty > 0 ? newTotalCost.value / qty : 0
})
/** 三项费用一个没变时后端会拒绝，按钮先禁掉 */
const costChanged = computed(() => Math.abs(newTotalCost.value - num(costForm.totalCostBefore)) >= 0.005)
/** 计划产出数量以产出明细为唯一数据源，头部只读展示合计，物料计划用量 = 单位用量 × 本合计。 */
const detailPlanTotal = computed(() => form.details.reduce((sum, row) => {
  const value = Number(row?.planYQty)
  return sum + (Number.isFinite(value) ? value : 0)
}, 0))
const executeTitle = computed(() => executeType.value === 'pick' ? '生产领料' : executeType.value === 'complete' ? '生产完工' : '生产退料')
/** 三段用的都是物料所在仓库，只是叫法不同 */
const materialWareLabel = computed(() => executeType.value === 'pick' ? '领料仓库'
  : executeType.value === 'complete' ? '物料消耗仓库' : '退料仓库')
const materialSectionTitle = computed(() => executeType.value === 'pick' ? '本次领料'
  : executeType.value === 'complete' ? '实际物料消耗' : '本次退料')
/** 本次数量列的表头，也是展开行里按批次填数量那一列的表头 */
const stageQtyLabel = computed(() => executeType.value === 'pick' ? '本次领料'
  : executeType.value === 'complete' ? '实际消耗' : '本次退回')
/** 本次数量的上限列 */
const stageLimitLabel = computed(() => executeType.value === 'pick' ? '未领'
  : executeType.value === 'complete' ? '可消耗' : '可退')
const stageHint = computed(() => {
  if (executeType.value === 'pick') {
    return '领料把物料从「良品·在库」转到「良品·不在库」（车间在制品），不产生成本，成本在完工消耗时结算。'
      + '可以分批领：没领足停在「待领料」，全部领足才转「生产中」。'
  }
  if (executeType.value === 'complete') {
    return '消耗扣的是车间在制品（良品·不在库），可消耗量 = 已领 − 已退。'
  }
  return '可退数量 = 已领 − 已耗 − 已退。退料只是把物料从「良品·不在库」转回「良品·在库」，不产生成本；'
    + '退回来的料如果坏了，请另开损坏登记。'
})

/** 本环节动的是哪一格库存：领料从在库出，消耗和退料动的都是车间在制品 */
const stageState = () => executeType.value === 'pick'
  ? { qualityFlag: QUALITY_FLAG.GOOD, onhandFlag: ONHAND_FLAG.IN }
  : { qualityFlag: QUALITY_FLAG.GOOD, onhandFlag: ONHAND_FLAG.OUT }

/**
 * 是否必须指定批次。口径跟着库存模块走（InventoryCostResolver）：
 * 领料和退料是同仓库内的状态流转，移动平均按库存均价搬成本、不碰批次，
 * FIFO 和个别计价必须点名搬哪一批；消耗是出库，FIFO 由库存模块自动按批次结算，
 * 只有个别计价要手工指定。
 */
function needsBatch(row) {
  if (row.batchStrategy !== 'batch') return false
  if (row.costCalculation === 'moving_avg') return false
  if (executeType.value === 'complete') return row.costCalculation === 'individual'
  return true
}

function needsSerial(row) { return row.batchStrategy === 'serial' }

const isTracked = row => needsBatch(row) || needsSerial(row)

const materialTableRef = ref(null)

/** 点「追踪」列的标签就展开/收起这一行，等价于点第一列的箭头 */
function toggleMaterialExpand(row) {
  materialTableRef.value?.toggleRowExpansion(row)
}

/**
 * 「追踪」列的标签文案。
 *
 * <p>仓库没选时先催选仓库：批次和序列号都要按仓库现查（见 loadMaterialTracking），
 * 这时候展开也只有一个空列表，提示「展开选序列号」等于让人白点一次。</p>
 */
function trackingTagText(row) {
  if (!executeForm.materialWareCode) return `请先选${materialWareLabel.value}`
  if (needsBatch(row)) return num(row.qty) > 0 ? '已选批次' : '展开选批次'
  return row.serialNumbers.length ? `${row.serialNumbers.length} 个序列号` : '展开选序列号'
}

function trackingTagType(row) {
  if (!executeForm.materialWareCode) return 'info'
  if (needsBatch(row)) return num(row.qty) > 0 ? 'success' : 'warning'
  return row.serialNumbers.length ? 'success' : 'warning'
}

/** 本次数量的上限，随环节变 */
function stageLimit(row) {
  if (executeType.value === 'pick') return unpickedQty(row)
  if (executeType.value === 'complete') return consumableQty(row)
  return returnableQty(row)
}

/** 批次或序列号选完后，把合计写回主行的本次数量 */
function syncTrackedQty(row) {
  if (needsBatch(row)) {
    row.qty = Number(row.availableBatches.reduce((sum, batch) => sum + num(batch.qty), 0).toFixed(4))
  } else if (needsSerial(row)) {
    row.qty = row.serialNumbers.length
  }
}

/**
 * 取商品档案，一次弹窗内按编码缓存。
 *
 * <p>同一个物料给两个产出各用一份时是两行，成品和物料也可能重名，不缓存就要重复请求。
 * 缓存里存的是 Promise，并发调用只会打一次接口；每次打开弹窗清空，档案改了能拿到新的。
 * 拉不到返回 null，由调用方按「不追踪」兜底——后端会用档案再判一次。</p>
 */
const goodsCache = new Map()
function fetchGoods(goodsCode) {
  if (!goodsCache.has(goodsCode)) {
    goodsCache.set(goodsCode, getGoods(goodsCode).then(response => response.data).catch(() => null))
  }
  return goodsCache.get(goodsCode)
}

/**
 * 拉物料的追踪方式和可选批次/序列号。
 *
 * <p>注意传进来的 row 必须是 executeForm.materials 里取出的响应式代理，
 * 直接改 map() 出来的原始对象不会触发重渲染。</p>
 */
async function loadMaterialTracking(row) {
  const goods = await fetchGoods(row.goodsCode)
  row.batchStrategy = goods?.batchStrategy || 'none'
  row.costCalculation = goods?.costCalculation || 'moving_avg'
  if (!executeForm.materialWareCode) return
  const state = stageState()
  try {
    if (needsBatch(row)) {
      const response = await listAvailableBatch({ goodsCode: row.goodsCode, wareCode: executeForm.materialWareCode, ...state })
      row.availableBatches = (response.data || response.rows || []).map(batch => ({ ...batch, qty: 0 }))
    } else if (needsSerial(row)) {
      const response = await listInStockSerial(row.goodsCode, executeForm.materialWareCode, state)
      row.serialOptions = response.data || response.rows || []
    }
  } catch (error) {
    row.availableBatches = []
    row.serialOptions = []
  }
  // 追踪型物料的数量由选批次/序列号决定，默认清零，避免带出一个选不出来的数
  if (isTracked(row)) {
    row.qty = 0
    row.serialNumbers = []
  }
}

/**
 * 拉成品的追踪方式，决定完工时要不要填批次号 / 序列号。
 *
 * <p>拉不到就按不追踪处理，后端会用商品档案再判一次；真填错了会被拦下并说清原因。</p>
 */
async function loadOutputTracking(row) {
  row.batchStrategy = (await fetchGoods(row.productCode))?.batchStrategy || 'none'
}

/** 仓库换了，可选批次和序列号都得重拉。并行发：物料多的时候串行要等好几秒 */
async function reloadTracking() {
  await Promise.all(executeForm.materials.map(row => loadMaterialTracking(row)))
}

const goodsSelectRef = ref(null)
/**
 * 弹窗这次是给谁选的：'detail' 是加产出商品，数字是加物料时的产出行下标。
 * 物料按产出分开，所以必须记住是哪一个产出的配方在录。
 */
const goodsSelectTarget = ref('detail')
/**
 * 已录入的商品，选商品弹窗用它打「已添加」标记并锁住。
 *
 * <p>选物料时除了同一个产出下已录的，还要锁住这个产出商品自己——自己做自己算不出成本，
 * 完工时同一张单既消耗又入库同一个商品，后端也会拒。</p>
 */
const selectedGoodsCodes = computed(() => {
  if (goodsSelectTarget.value === 'detail') return form.details.map(row => row.productCode).filter(Boolean)
  const detailRow = form.details[goodsSelectTarget.value]
  if (!detailRow) return []
  return [detailRow.productCode, ...detailRow.materials.map(row => row.goodsCode)].filter(Boolean)
})
/** 某行物料的计划用量 = 单位用量 × 所属产出的计划数量 */
function materialPlanQty(detailRow, materialRow) {
  return formatQty(num(materialRow.qtyPerUnit) * num(detailRow.planYQty))
}

/**
 * 成品要不要落批次号。
 *
 * <p>批次管理和序列号管理的成品都要有批次（序列号挂在批次下），留空由后端生成；
 * 不做追踪的成品填了后端会拒。口径与采购入库一致。</p>
 */
function outputTracked(row) {
  return row.batchStrategy === 'batch' || row.batchStrategy === 'serial'
}

/** 序列号个数要等于实际产出，改了数量就把已录的清掉，免得个数对不上 */
function handleOutputQtyChange(row) {
  if (row.batchStrategy === 'serial' && (row.serialNumbers || []).length !== num(row.actualYQty)) {
    row.serialNumbers = []
  }
}

const outputSerialExpect = computed(() => {
  const row = executeForm.outputs[outputSerialIndex.value]
  return row ? num(row.actualYQty) : 0
})

function openOutputSerial(index) {
  outputSerialIndex.value = index
  outputSerialText.value = formatSerialNumbers(executeForm.outputs[index].serialNumbers)
  outputSerialOpen.value = true
}

function confirmOutputSerial() {
  const row = executeForm.outputs[outputSerialIndex.value]
  if (row) row.serialNumbers = parseSerialNumbers(outputSerialText.value)
  outputSerialOpen.value = false
}

/** 这张工单挂着订单吗——决定完工弹窗要不要展示「认领来源订单」那一列 */
const hasOrderClaim = computed(() => executeForm.outputs.some(row => (row.allocations || []).length))

/**
 * 按当前填的实际产出，预演每条分配能认领到多少。
 *
 * <p>和后端 fulfillSourceOrders 同一套算法：按分配顺序先到先得，做多少发多少。
 * 这里只是让用户在点确认之前就看到谁会被短给，真正的认领量还要受订单当下的未发量限制，
 * 以后端为准。</p>
 */
function claimPreview(outputRow) {
  let remaining = num(outputRow.actualYQty)
  return (outputRow.allocations || []).map(allocation => {
    const want = num(allocation.qty)
    const got = Math.min(want, Math.max(remaining, 0))
    remaining -= got
    return { orderCode: allocation.orderCode, want, got }
  })
}

/** 这行产出分给订单的总量 */
function allocationTotal(detailRow) {
  return (detailRow.allocations || []).reduce((sum, row) => sum + num(row.qty), 0)
}
/** 分出去的比计划做的还多，后端会拒；前端先标红，省一次往返 */
function allocationExceeded(detailRow) {
  return allocationTotal(detailRow) - num(detailRow.planYQty) > 1e-9
}

/**
 * 订单号下拉的选项：已取回行里出现过的订单，按后端给的顺序（下单时间倒序）去重。
 *
 * <p>带上条数是因为一张订单常有好几条明细，看得出这单选下去会带进来几个产出。</p>
 */
const candidateOrderOptions = computed(() => {
  const map = new Map()
  candidates.value.forEach(row => {
    const code = row.orderCode
    if (!code) return
    const hit = map.get(code)
    if (hit) hit.count++
    else map.set(code, { orderCode: code, count: 1 })
  })
  return Array.from(map.values())
})

/** 关键字和订单号都在已取回的行里过滤：一次拉全、本地筛，选单时改条件不用等接口 */
const filteredCandidates = computed(() => {
  const keyword = candidateKeyword.value.trim().toLowerCase()
  const orderCode = candidateOrderCode.value
  return candidates.value.filter(row => {
    if (orderCode && row.orderCode !== orderCode) return false
    if (!keyword) return true
    return String(row.goodsCode || '').toLowerCase().includes(keyword)
      || String(row.goodsName || '').toLowerCase().includes(keyword)
  })
})
const candidateSelectedQty = computed(() => candidateSelection.value.reduce((sum, row) => sum + num(row.pendingQty), 0))

/**
 * 「专门生产」看的是订单明细上的定制标记，决定做出来的货归谁。
 *
 * <p>定制件完工入库时，成品批次会绑到对应的订单明细上，别的订单发货挑不到——客户要的
 * 规格库存里的通用货顶不上，被别人发走就得重做一遍。普通件做出来是通用库存，
 * 谁先发货谁拿到。这个判断从数量上看不出来，由下单的人在新建销售订单时勾，这里只读。</p>
 */
function isSpecialCandidate(row) {
  return row?.customMade === true || row?.customMade === 1
}
function candidateRowClass({ row }) {
  return isSpecialCandidate(row) ? 'candidate-special' : ''
}
const candidateSelectedSpecial = computed(() => candidateSelection.value.filter(isSpecialCandidate).length)

function openCandidatePicker() {
  candidateOpen.value = true
  candidateSelection.value = []
  candidateOrderCode.value = ''
  candidateTableRef.value?.clearSelection()
  loadCandidates()
}

/** 换月份要清掉订单筛选：选中的那张单未必在新月份里，留着会筛出一张空表 */
function handleCandidateMonthChange() {
  candidateOrderCode.value = ''
  loadCandidates()
}

function loadCandidates() {
  candidateLoading.value = true
  // 月份留空 → 后端查全部月表
  listProductionCandidates({ tableSuffix: candidateMonth.value || undefined }).then(response => {
    candidates.value = response.data || []
  }).finally(() => { candidateLoading.value = false })
}

/**
 * 把选中的订单明细并进产出明细。
 *
 * <p>按商品归并：同一个商品被三张订单要，做成一个产出行、挂三条分配，而不是三行产出——
 * 三行产出意味着三套配方、三次领料，车间实际就做一批。计划产出默认等于分配量之和，
 * 想多做备货再手工调大。</p>
 *
 * <p>已经在表单里的同商品产出行直接追加分配（同一条订单明细不重复挂），
 * 新出现的商品才建行，建完自动带配方。</p>
 */
function confirmCandidates() {
  const claimed = new Set()
  form.details.forEach(row => (row.allocations || []).forEach(item => claimed.add(item.detailCode)))
  const created = []
  let appended = 0
  let skipped = 0
  candidateSelection.value.forEach(row => {
    if (claimed.has(row.detailCode)) { skipped++; return }
    claimed.add(row.detailCode)
    const allocation = {
      orderCode: row.orderCode,
      tableSuffix: row.tableSuffix,
      detailCode: row.detailCode,
      goodsCode: row.goodsCode,
      goodsName: row.goodsName,
      // 定制标记只留档，决定完工时要不要把批次绑给这条明细；上限和默认分配量都用未发量
      customMade: isSpecialCandidate(row),
      pendingQty: num(row.pendingQty),
      qty: num(row.pendingQty)
    }
    const target = form.details.find(item => item.productCode === row.goodsCode)
    if (target) {
      target.allocations.push(allocation)
      // 计划产出至少要够分配的量，但不动手工调大过的计划——多做的那部分是备货，不该被抹掉。
      // 下界还要吃住 MIN_PLAN_QTY：未发量不足 1 个时也得按 1 个排产
      target.planYQty = Math.max(num(target.planYQty), allocationTotal(target), MIN_PLAN_QTY)
      appended++
    } else {
      form.details.push(nextDetailRow(row.goodsCode, row.goodsName || '',
        Math.max(allocation.qty, MIN_PLAN_QTY), [], [allocation]))
      // 同 handleGoodsSelected：交给 fillBom 的必须是 push 之后的响应式代理
      created.push(form.details[form.details.length - 1])
    }
  })
  candidateOpen.value = false
  if (skipped) proxy.$modal.msgWarning(`有 ${skipped} 条订单明细已经在本工单里，跳过`)
  if (created.length) fillBom(created)
  else if (appended) proxy.$modal.msgSuccess(`已并入 ${appended} 条订单分配`)
}

/** 改回备货生产就把分配清掉：备货工单带着订单分配，后端会直接拒 */
function handleSourceTypeChange(value) {
  if (value !== 'order') form.details.forEach(row => { row.allocations = [] })
}
function getList() { loading.value = true; listProductionOrder(queryParams).then(r => { list.value = r.rows || []; total.value = r.total || 0 }).finally(() => { loading.value = false }) }
function resetQuery() { queryParams.productionCode = undefined; queryParams.productCode = undefined; queryParams.status = undefined; queryParams.pageNum = 1; getList() }
// 产出明细留空，由「添加产出」从分类树里挑；不能在这里预置一行，
// 因为 addDetailLine 现在是打开选商品弹窗，会在表单还没显示时就弹出来。
function openAdd() { Object.assign(form, emptyForm()); formOpen.value = true }

/** 修改工单：拉详情回填表单，走 updateProductionOrder。后端只放行待生产工单。 */
async function openEdit(row) {
  try {
    const response = await getProductionOrder(row.productionCode)
    const order = response.data
    if (!order) { proxy.$modal.msgError('生产工单不存在'); return }
    Object.assign(form, emptyForm(), {
      id: order.id,
      sourceType: order.sourceType || 'stock',
      // productCode/productName 不回填：保存时按产出明细第一行重新带出
      planYQty: num(order.planYQty),
      planDate: order.planDate ? parseTime(order.planDate, '{y}-{m}-{d}') : parseTime(new Date(), '{y}-{m}-{d}'),
      remark: order.remark || '',
      // 物料跟着自己的产出行回填；单位用量是录入项，计划用量由后端按「单位用量 × 本产出计划数」重算
      details: (order.details || []).map(item => nextDetailRow(
        item.productCode, item.productName, num(item.planYQty),
        (item.materials || []).map(material => ({
          goodsCode: material.goodsCode, goodsName: material.goodsName, spec: material.spec,
          unit: material.unit, qtyPerUnit: num(material.qtyPerUnit)
        })),
        // 订单分配原样回填。未发量和定制标记都是建单当时的快照，只用来把输入框的上限
        // 和标记画出来；后端保存时会按当下的订单明细重新校验，不信这两个数。
        // pendingQty 兜底成 qty：老工单的分配 JSON 里没有这个字段，缺了会让上限变成 0，
        // 表现为改单时数量只能减不能加。
        (item.allocations || []).map(allocation => ({
          orderCode: allocation.orderCode, tableSuffix: allocation.tableSuffix,
          detailCode: allocation.detailCode, goodsCode: allocation.goodsCode,
          goodsName: allocation.goodsName, customMade: allocation.customMade === true,
          pendingQty: num(allocation.pendingQty) || num(allocation.qty),
          qty: num(allocation.qty)
        }))
      ))
    })
    formOpen.value = true
  } catch (error) {
    proxy.$modal.msgError('获取工单详情失败')
  }
}

/** 删除工单，只对待生产工单开放 */
function removeOrder(row) {
  proxy.$modal.confirm(`确认删除生产工单「${row.productionCode}」吗？`).then(() => {
    return delProductionOrder(row.productionCode)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}
/**
 * 产出明细和各产出的物料清单共用同一个选商品弹窗，goodsSelectTarget 记住这次是给谁选的。
 */
function addDetailLine() { goodsSelectTarget.value = 'detail'; goodsSelectRef.value?.open() }
function openDetail(row) {
  detailOpen.value = true
  detail.value = {}
  productionLogs.value = []
  logsLoading.value = true
  // 日志不传月份后端会跨月表查全，工单跨月时前面几段才不会漏；条数少，后端也不分页
  Promise.all([
    getProductionOrder(row.productionCode),
    listProductionLogs(row.productionCode)
  ]).then(([orderResponse, logResponse]) => {
    detail.value = orderResponse.data || {}
    productionLogs.value = logResponse.rows || []
  }).finally(() => { logsLoading.value = false })
}
/**
 * 主表的产出商品编码/名称是冗余字段（列表页「产出商品」列和按商品筛选读它，后端也校验非空），
 * 界面上不再单独录入，统一取产出明细第一行；多行产出时名称补「等N项」，并按列宽 varchar(100) 截断。
 */
function headerProduct() {
  const first = form.details[0] || {}
  const code = String(first.productCode || '').trim()
  const name = String(first.productName || '').trim() || code
  const suffix = form.details.length > 1 ? ` 等${form.details.length}项` : ''
  return { productCode: code, productName: name.slice(0, 100 - suffix.length) + suffix }
}

function submitForm() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    if (!form.details.length) { proxy.$modal.msgWarning('请至少添加一行产出明细'); return }
    if (form.details.some(row => !String(row.productCode || '').trim())) { proxy.$modal.msgWarning('产出明细的商品编码不能为空'); return }
    // 改单回填的老数据可能带着 1 以下的计划量，输入框的 min 拦不住，提交前再挡一次
    if (form.details.some(row => num(row.planYQty) < MIN_PLAN_QTY)) { proxy.$modal.msgWarning(`每个产出的计划数量不能少于 ${MIN_PLAN_QTY}`); return }
    if (detailPlanTotal.value <= 0) { proxy.$modal.msgWarning('产出明细的计划数量合计必须大于0'); return }
    // 整单一行物料都没有多半是漏录了；某个产出单独没物料是允许的（比如伴生的副产品）
    if (!form.details.some(row => row.materials.length)) { proxy.$modal.msgWarning('请为产出商品录入物料清单'); return }
    const badMaterial = form.details.flatMap(row => row.materials)
      .find(row => !String(row.goodsCode || '').trim() || num(row.qtyPerUnit) <= 0)
    if (badMaterial) { proxy.$modal.msgWarning('物料的编码不能为空、单位用量必须大于0'); return }
    // 配方带出来的老数据里可能混着成品自己，选商品弹窗锁不住这种，提交前再挡一次
    const selfUsed = form.details.find(row => row.materials.some(item => item.goodsCode === row.productCode))
    if (selfUsed) {
      proxy.$modal.msgWarning(`产出「${selfUsed.productName}」的物料里有它自己，请把这一行删掉`); return
    }
    // 订单生产必须挂订单，否则完工入库后没人知道这批货该发给谁——后端也会拒
    if (form.sourceType === 'order' && !form.details.some(row => (row.allocations || []).length)) {
      proxy.$modal.msgWarning('订单生产的工单请先选销售订单，不挂订单请把来源改成备货生产'); return
    }
    const overAllocated = form.details.find(row => allocationExceeded(row))
    if (overAllocated) {
      proxy.$modal.msgWarning(`产出「${overAllocated.productName}」分给订单的总量超过计划产出，请调大计划或调小分配`); return
    }
    if (form.details.flatMap(row => row.allocations || []).some(row => num(row.qty) <= 0)) {
      proxy.$modal.msgWarning('订单分配数量必须大于0，不做的行请删掉'); return
    }
    // 头部计划数量是冗余字段，提交前用产出明细合计覆盖，后端也会再算一次。
    form.planYQty = detailPlanTotal.value
    Object.assign(form, headerProduct())
    // rowKey 只是前端渲染用的行号，不发给后端
    const payload = {
      ...form,
      details: form.details.map(({ rowKey, ...row }) => row)
    }
    const api = form.id ? updateProductionOrder : addProductionOrder
    api(payload).then(() => { proxy.$modal.msgSuccess('保存成功'); formOpen.value = false; getList() })
  })
}
function num(value) { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : 0 }
/**
 * 三段式下的数量口径：totalQty 是计划总用量，pickedQty 才是实际已领量。
 * 能消耗、能退的都只有已经领到车间的那部分，跟计划量无关。
 */
/** 未领 = 计划用量 - 已领，本次领料量的上限 */
function unpickedQty(row) { return Number((num(row.totalQty) - num(row.pickedQty)).toFixed(4)) }
/** 可消耗量 = 已领 - 已退，实际消耗不能超过它 */
function consumableQty(row) { return Number((num(row.pickedQty) - num(row.returnedQty)).toFixed(4)) }
/** 剩余可退 = 已领 - 已耗 - 已退 */
function returnableQty(row) { return Number((num(row.pickedQty) - num(row.actualQty) - num(row.returnedQty)).toFixed(4)) }
const totalOutputQty = computed(() => executeForm.outputs.reduce((sum, row) => sum + num(row.actualYQty), 0))

function openExecute(row, type) {
  executeType.value = type
  // 每次打开重新拉档案：上一次打开之后商品的批次策略可能改过
  goodsCache.clear()
  Object.assign(executeForm, {
    materialWareCode: '', productWareCode: '', remark: '',
    productionCode: row.productionCode, outputs: [], materials: [],
    processingCost: 0, freightCost: 0, otherCost: 0
  })
  executeOpen.value = true
  // 三段都要按行看数量：领料看未领量，完工要录实际产出和实际消耗，退料要展示可退数量。
  executeLoading.value = true
  getProductionOrder(row.productionCode).then(async response => {
    const detail = response.data || {}
    // 实际产出默认按计划数量带出；本次数量默认按本环节上限带足，追踪型物料在
    // loadMaterialTracking 里会清零，改由选批次/序列号决定。
    executeForm.outputs = (detail.details || []).map(item => ({
      ...item, actualYQty: num(item.planYQty),
      // 成品的追踪方式在下面按商品档案回填，回填前先当作不追踪
      batchStrategy: '', batchCode: '', serialNumbers: []
    }))
    // 配方按产出分开，同一个物料可能给两个产出各领一次，行上要标出是哪个产出的料
    const outputNames = new Map((detail.details || []).map(item => [item.detailCode, item.productName]))
    executeForm.materials = (detail.materials || []).map(item => ({
      ...item, outputName: outputNames.get(item.detailCode) || '-',
      batchStrategy: 'none', costCalculation: 'moving_avg',
      availableBatches: [], serialOptions: [], serialNumbers: [], qty: 0
    }))
    // 必须遍历响应式数组取回代理，不能改上面 map() 出来的原始对象——否则表格不重渲染。
    // 每行要打 1~2 个接口，串行发的话十几项物料就要等好几秒，一起发出去等齐
    executeForm.materials.forEach(material => { material.qty = stageLimit(material) })
    await Promise.all([
      ...executeForm.materials.map(material => loadMaterialTracking(material)),
      ...(type === 'complete' ? executeForm.outputs.map(output => loadOutputTracking(output)) : [])
    ])
  }).finally(() => { executeLoading.value = false })
}

/** 仓库是批次和序列号的查询条件，换了要重拉 */
function handleExecuteWarehouseChange() {
  executeLoading.value = true
  reloadTracking().finally(() => { executeLoading.value = false })
}

/**
 * 把一行物料摊成报给后端的请求行。
 *
 * 批次商品按批次拆成多行，序列号商品带上选中的序列号，其余就是一行数量。
 * 数量为 0 时也要发一行占位——后端对「没提到的物料」会按上限兜底全领/全耗/全退，
 * 发个 0 才能表达「这个物料本次不动」。
 */
function materialLines(row) {
  const field = executeType.value === 'pick' ? 'pickedQty'
    : executeType.value === 'complete' ? 'actualQty' : 'returnedQty'
  const zero = [{ materialCode: row.materialCode, [field]: 0 }]
  if (needsBatch(row)) {
    const lines = row.availableBatches
      .filter(batch => num(batch.qty) > 0)
      .map(batch => ({ materialCode: row.materialCode, [field]: num(batch.qty), batchCode: batch.batchCode }))
    return lines.length ? lines : zero
  }
  if (needsSerial(row)) {
    return row.serialNumbers.length
      ? [{ materialCode: row.materialCode, [field]: row.serialNumbers.length, serialNumbers: [...row.serialNumbers] }]
      : zero
  }
  return [{ materialCode: row.materialCode, [field]: num(row.qty) }]
}

function submitExecute() {
  const code = executeForm.productionCode
  // 三段都要指定物料仓库：领料从这里出，消耗从这里的在制品出，退料退回这里
  if (!executeForm.materialWareCode) { proxy.$modal.msgWarning(`请选择${materialWareLabel.value}`); return }
  const over = executeForm.materials.find(row => num(row.qty) > stageLimit(row))
  if (over) { proxy.$modal.msgWarning(`物料“${over.goodsName}”的${stageQtyLabel.value}超过${stageLimitLabel.value}数量`); return }
  const totalQty = executeForm.materials.reduce((sum, row) => sum + num(row.qty), 0)
  if (executeType.value !== 'complete' && totalQty <= 0) {
    proxy.$modal.msgWarning(`${stageQtyLabel.value}合计必须大于0`)
    return
  }
  if (executeType.value === 'complete') {
    if (!executeForm.productWareCode) { proxy.$modal.msgWarning('请选择成品入库仓库'); return }
    if (totalOutputQty.value <= 0) { proxy.$modal.msgWarning('实际产出数量合计必须大于0'); return }
    // 序列号成品必须逐个录号，个数等于实际产出——后端也会拒，这里先说清是哪一行
    const badSerial = executeForm.outputs.find(row => row.batchStrategy === 'serial'
      && num(row.actualYQty) > 0 && (row.serialNumbers || []).length !== num(row.actualYQty))
    if (badSerial) {
      proxy.$modal.msgWarning(`成品「${badSerial.productName}」实际产出 ${num(badSerial.actualYQty)}，请录入同样数量的序列号`)
      return
    }
  }
  const payload = {
    materialWareCode: executeForm.materialWareCode,
    productWareCode: executeForm.productWareCode,
    remark: executeForm.remark,
    materials: executeForm.materials.flatMap(materialLines)
  }
  if (executeType.value === 'complete') {
    // 批次号留空由后端生成；不追踪的成品一律不带这两个字段，带了后端会拒
    payload.outputs = executeForm.outputs.map(row => ({
      detailCode: row.detailCode,
      actualYQty: row.actualYQty,
      batchCode: outputTracked(row) ? (row.batchCode || '').trim() : undefined,
      serialNumbers: row.batchStrategy === 'serial' ? row.serialNumbers : undefined
    }))
    payload.processingCost = executeForm.processingCost
    payload.freightCost = executeForm.freightCost
    payload.otherCost = executeForm.otherCost
  }
  const api = executeType.value === 'pick' ? pickProductionOrder
    : executeType.value === 'complete' ? completeProductionOrder : returnProductionMaterial
  executeSubmitting.value = true
  api(code, payload).then(() => { proxy.$modal.msgSuccess('执行成功'); executeOpen.value = false; getList() })
    .finally(() => { executeSubmitting.value = false })
}
/** 打开调成本弹窗，费用带出工单上的当前值 */
async function openCostAdjust(row) {
  costOpen.value = true
  costLoading.value = true
  Object.assign(costForm, {
    productionCode: row.productionCode, materialCost: 0, actualYQty: 0,
    processingCost: 0, freightCost: 0, otherCost: 0, totalCostBefore: 0, unitCostBefore: 0
  })
  try {
    const order = (await getProductionOrder(row.productionCode)).data || {}
    Object.assign(costForm, {
      materialCost: num(order.materialCost), actualYQty: num(order.actualYQty),
      processingCost: num(order.processingCost), freightCost: num(order.freightCost),
      otherCost: num(order.otherCost),
      totalCostBefore: num(order.totalCost), unitCostBefore: num(order.unitCost)
    })
  } finally {
    costLoading.value = false
  }
}

function submitCostAdjust() {
  costSubmitting.value = true
  adjustProductionCost(costForm.productionCode, {
    processingCost: costForm.processingCost,
    freightCost: costForm.freightCost,
    otherCost: costForm.otherCost
  }).then(() => {
    proxy.$modal.msgSuccess('成本已调整')
    costOpen.value = false
    getList()
  }).finally(() => { costSubmitting.value = false })
}

/**
 * 关闭 = 工单结案，此后不再产出任何东西。
 *
 * <p>已完工是正常收尾，成本就此定案；没完工是「这批不做了」的终止，要填原因，
 * 原因会记进生产日志。两种都要求料已经退干净——关闭之后没有任何入口能把料取回来，
 * 后端会拦着并列出还差多少。来源订单明细一并松开，可由新工单接手。</p>
 */
function closeOrder(row) {
  if (row.status === '已完工') {
    const tip = row.sourceType === 'order'
      ? `确认关闭工单 ${row.productionCode}？关闭后不能再调整费用；来源订单明细会被松开，可由新工单接手。`
      : `确认关闭工单 ${row.productionCode}？关闭后不能再调整费用。`
    proxy.$modal.confirm(tip)
      .then(() => closeProductionOrder(row.productionCode))
      .then(() => { proxy.$modal.msgSuccess('已关闭'); getList() })
      .catch(() => {})
    return
  }
  proxy.$prompt(
    `工单 ${row.productionCode} 当前是「${row.status}」，终止后不能再领料、完工，也不能恢复。`
    + '领出去的料要先退干净，来源订单明细会被松开。',
    '终止生产工单',
    { inputPlaceholder: '请说明这批为什么不做了', inputPattern: /\S/, inputErrorMessage: '终止原因不能为空' }
  ).then(({ value }) => closeProductionOrder(row.productionCode, value.trim()))
    .then(() => { proxy.$modal.msgSuccess('已终止'); getList() })
    .catch(() => {})
}
/** 加物料要说明加到哪个产出的配方里，下标就是产出行在 form.details 里的位置 */
function addMaterialLine(detailIndex) { goodsSelectTarget.value = detailIndex; goodsSelectRef.value?.open() }

function handleGoodsSelected(list) {
  const detailRow = goodsSelectTarget.value === 'detail' ? null : form.details[goodsSelectTarget.value]
  const added = []
  list.forEach(goods => {
    if (goodsSelectTarget.value === 'detail') {
      form.details.push(nextDetailRow(goods.goodsCode, goods.goodsName || '', MIN_PLAN_QTY, []))
      // 必须把刚 push 进去的那个「响应式代理」取回来交给 fillBom，不能留着 push 前的原始对象：
      // form 是 reactive 的，写原始对象虽然改到了同一份数据，却绕过了代理的 set 陷阱，
      // 不触发重渲染——表现就是配方明明带出来了，物料清单看着还是空的，
      // 直到点一下计划数量之类的东西逼出一次重渲染，物料才突然冒出来
      added.push(form.details[form.details.length - 1])
    } else if (detailRow) {
      detailRow.materials.push({
        goodsCode: goods.goodsCode,
        goodsName: goods.goodsName || '',
        spec: goods.spec || '',
        unit: goods.unit || '',
        qtyPerUnit: 1
      })
    }
  })
  if (added.length) fillBom(added)
}

/**
 * 新加的产出行按商品档案上的配方（BOM 主数据）自动带出物料。
 *
 * <p>带出来的是配方在这一刻的快照，行可以改单耗、可以删、可以再加——工单存的是自己那一份，
 * 事后改配方不会回头影响这张工单。没有配方或配方已停用的商品就留空，手工录照样能建。</p>
 */
async function fillBom(rows) {
  const filled = []
  const missing = []
  for (const row of rows) {
    try {
      const bom = (await getBomByProduct(row.productCode)).data
      // 没配方或配方已停用：说一声。不说的话用户看到的就是一张空物料表，
      // 分不清是「这个成品没建过配方」还是「带出失败了」
      if (!bom || !(bom.items || []).length) { missing.push(row.productName); continue }
      row.materials = bom.items.map(item => ({
        goodsCode: item.goodsCode,
        goodsName: item.goodsName || '',
        spec: item.spec || '',
        unit: item.unit || '',
        qtyPerUnit: num(item.qtyPerUnit)
      }))
      filled.push(`${row.productName}（${row.materials.length} 项）`)
    } catch (error) {
      // 配方带不出来不影响建单，手工录物料即可，拦截器已经提示过失败原因
    }
  }
  if (filled.length) proxy.$modal.msgSuccess(`已按配方带出物料：${filled.join('、')}`)
  if (missing.length) {
    proxy.$modal.msgWarning(`${missing.join('、')} 还没有启用中的配方，请点「添加物料」手工录，`
      + `或先去「生产管理 → 产品配方」建一套`)
  }
}
function tagType(status) {
  if (status === '已完工') return 'success'
  if (status === '生产中') return 'primary'
  if (status === '待领料') return 'warning'
  return 'info'
}
/** 生产日志的 operation 由后端直接写中文：领料 / 消耗 / 入库 / 退料 / 费用调整 / 订单认领（老数据里叫「订单预占」）*/
function operationName(operation) { return operation }
/**
 * 这几步不产生成本：领料和退料是「良品·在库 ↔ 良品·不在库」的状态流转，
 * 订单认领只是把已入库的货记到订单名下。成本只在消耗（物料成本）和入库（成品成本）时发生。
 */
function noCostOperation(operation) { return ["领料", "退料", "订单认领", "订单预占"].includes(operation) }
function operationTagType(operation) {
  if (operation === '领料') return 'success'
  if (operation === '消耗') return 'warning'
  if (operation === '入库') return 'primary'
  if (operation === '退料') return 'info'
  if (operation === '费用调整') return 'danger'
  if (operation === "订单认领" || operation === "订单预占") return "success"
  return ''
}
/**
 * 认首页看板带过来的 ?status=在产。
 *
 * 状态筛选界面上本来就有，这里只是把 URL 参数灌进去，让用户落地时就看见筛选条上
 * 写着「在产」，而不是拿到一个不知道被谁筛过的列表。
 *
 * @returns 是否真的改了筛选条件——没改就不必额外发一次请求
 */
function applyRouteFilter() {
  const status = useRoute().query.status
  if (!status || queryParams.status === status) {
    return false
  }
  queryParams.status = status
  queryParams.pageNum = 1
  return true
}

// 本页被 keep-alive 缓存：第二次从看板点进来时组件不重新初始化，只有 onActivated 会触发。
// 不带筛选参数的普通切换不会多发请求。
onActivated(() => {
  if (applyRouteFilter()) getList()
})

applyRouteFilter()
listWarehouse({ pageNum: 1, pageSize: 500 }).then(r => { warehouses.value = r.rows || [] }); getList()
</script>

<style scoped>
.production-readonly {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.production-hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
/* 追踪标签点一下就展开这一行，给个手型让人看出来它可点 */
.production-tracking-tag {
  cursor: pointer;
}
/* 展开行里的批次/序列号明细，缩进一点与主表拉开层次 */
.production-tracking {
  padding: 8px 12px 8px 48px;
}
/* 一个产出商品一块：上面是产出本身，下面缩进的是它的配方 */
.production-output {
  margin-top: 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}
.production-output-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 12px;
  background-color: var(--el-fill-color-light);
}
.production-output-name {
  flex: 1;
  font-weight: 600;
}
.production-output-qty {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}
.production-output-body {
  padding: 8px 12px 12px 24px;
}
.production-output-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  color: var(--el-text-color-secondary);
}
.production-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
/* 追踪型物料的数量是批次合计出来的，只读展示 */
.production-tracked-qty {
  font-variant-numeric: tabular-nums;
}
/*
  选单表里「专门生产」的行：底色标出来，一眼能跟做通用库存的行分开。
  背景写在 td 上而不是 tr 上——el-table 的斑马纹和 hover 都作用在 td，写在 tr 上会被盖掉。
*/
:deep(.candidate-special > td.el-table__cell) {
  background-color: var(--el-color-warning-light-9);
}
.candidate-custom {
  font-size: 12px;
  color: var(--el-color-warning);
}
/* 已挂在别的在产工单上——挑了会被后端拒，用警示色让人先看见 */
.candidate-held {
  font-size: 12px;
  color: var(--el-color-warning);
}
/* 未发量、超分配这类要人看一眼的数 */
.production-warn {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-color-warning);
}
.production-alert {
  margin: 6px 0;
}
.ml8 {
  margin-left: 8px;
}
.mb8 {
  margin-bottom: 8px;
}
</style>

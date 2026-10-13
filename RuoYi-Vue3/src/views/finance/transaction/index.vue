<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="流水号" prop="transactionCode">
        <el-input v-model="queryParams.transactionCode" placeholder="请输入流水号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="收支方向" prop="transType">
        <el-select v-model="queryParams.transType" placeholder="全部" clearable style="width: 110px">
          <el-option label="收入" value="收入" />
          <el-option label="支出" value="支出" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型" prop="bizType">
        <el-select v-model="queryParams.bizType" placeholder="全部" clearable style="width: 110px">
          <el-option v-for="item in bizTypes" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="item in statusOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="账户" prop="accountCode">
        <el-select v-model="queryParams.accountCode" placeholder="全部" clearable style="width: 160px">
          <el-option v-for="item in accounts" :key="item.accountCode" :label="item.accountName" :value="item.accountCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务单号" prop="bizCode">
        <el-input v-model="queryParams.bizCode" placeholder="采购单号/订单号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="对方名称" prop="partyName">
        <el-input v-model="queryParams.partyName" placeholder="供应商/客户" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="交易时间">
        <el-date-picker v-model="tradeTimeRange" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['finance:transaction:add']">新建流水</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="transactionList">
      <el-table-column label="流水号" align="center" prop="transactionCode" min-width="150" />
      <el-table-column label="交易时间" align="center" prop="tradeTime" min-width="160">
        <template #default="scope">
          <span v-if="scope.row.tradeTime">{{ scope.row.tradeTime }}</span>
          <span v-else class="text-muted">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="收支" align="center" prop="transType" min-width="80">
        <template #default="scope">
          <el-tag :type="scope.row.transType === '收入' ? 'success' : 'danger'">{{ scope.row.transType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分类" align="center" prop="categoryName" min-width="110" />
      <el-table-column label="金额" align="right" prop="amount" min-width="120">
        <template #default="scope">
          <span :class="Number(scope.row.amount) < 0 ? 'amount-negative' : 'amount-positive'">
            {{ formatCurrency(scope.row.amount, scope.row.currency) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="对方名称" align="center" prop="partyName" min-width="140" show-overflow-tooltip />
      <el-table-column label="业务摘要" align="center" prop="bizSummary" min-width="200" show-overflow-tooltip />
      <!-- 采购/销售的单号点得进去，直接跳到那张单。财务对不上账时第一件事就是翻原单，
           手工复制单号再切模块搜索是最费时间的一步。 -->
      <el-table-column label="业务单号" align="center" prop="bizCode" min-width="140">
        <template #default="scope">
          <el-link v-if="scope.row.bizCode && bizLinkable(scope.row)" type="primary"
            @click="openBizDocument(scope.row)">{{ scope.row.bizCode }}</el-link>
          <span v-else-if="scope.row.bizCode">{{ scope.row.bizCode }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="账户" align="center" prop="accountName" min-width="120">
        <template #default="scope">
          <span v-if="scope.row.accountName">{{ scope.row.accountName }}</span>
          <span v-else class="text-muted">待选择</span>
        </template>
      </el-table-column>
      <el-table-column label="完成后余额" align="right" prop="balance" min-width="130">
        <template #default="scope">
          <span v-if="scope.row.balance !== null && scope.row.balance !== undefined">{{ formatCurrency(scope.row.balance, scope.row.currency) }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <!-- 客户回款的「待审核」其实是在等业务员认领，标成「待认领」让财务一眼看出还没人认 -->
      <el-table-column label="状态" align="center" prop="status" min-width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ awaitingClaim(scope.row) ? '待认领' : scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建人" align="center" prop="createUserName" min-width="100">
        <template #default="scope">{{ scope.row.createUserName || scope.row.createUser }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="300" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button v-if="editable(scope.row)" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['finance:transaction:edit']">修改</el-button>
          <el-button v-if="editable(scope.row)" link type="success" icon="Check" @click="handleSubmit(scope.row)" v-hasPermi="['finance:transaction:submit']">提交</el-button>
          <el-button v-if="canAudit(scope.row)" link type="warning" icon="Stamp" @click="handleAudit(scope.row)" v-hasPermi="['finance:transaction:audit']">审核</el-button>
          <!-- 这里没有「认领」：认领在销售订单详情的「收款流水」页签里做，业务员站在自己的单上挑打款。
               财务建单时知道是哪张单的，选了订单号提交即绑定，也不需要认领。 -->
          <el-button v-if="canComplete(scope.row)" link type="success" icon="CircleCheck" @click="handleComplete(scope.row)" v-hasPermi="['finance:transaction:audit']">完成</el-button>
          <el-button v-if="canExecute(scope.row)" link type="success" icon="Money" @click="handleExecute(scope.row)" v-hasPermi="['finance:transaction:execute']">执行打款</el-button>
          <el-button v-if="editable(scope.row)" link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['finance:transaction:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新建 / 修改 -->
    <el-dialog :title="title" v-model="open" width="760px" append-to-body :close-on-click-modal="false">
      <el-form ref="transactionRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="业务类型" prop="bizType">
              <el-select v-model="form.bizType" placeholder="请选择" style="width: 100%" :disabled="form.transactionCode != null" @change="handleBizTypeChange">
                <el-option v-for="item in bizTypes" :key="item" :label="item" :value="item" />
              </el-select>
              <div class="form-tip">{{ bizTypeTip }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收支方向" prop="transType">
              <!-- 三种业务类型都放开两个方向：采购/销售的反方向是退款（供应商退我方 / 我方退客户），
                   走的流程和回写的正负号都不一样，后端按方向分派；其他业务两个方向各对应一条系统分类 -->
              <el-radio-group v-model="form.transType" @change="handleTransTypeChange">
                <el-radio value="收入">收入</el-radio>
                <el-radio value="支出">支出</el-radio>
              </el-radio-group>
              <div v-if="refundTip" class="form-tip refund-tip">{{ refundTip }}</div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 付款和退款列的是相反的两批单：付款只列还没付满的，退款只列已经付过钱的 -->
        <el-form-item v-if="form.bizType === '采购'" label="采购单号" prop="bizCode">
          <el-select v-model="form.bizCode" :placeholder="isRefund ? '请选择要退款的采购单' : '请选择采购单'"
            filterable :loading="purchaseLoading" style="width: 100%" @change="handlePurchaseChange">
            <el-option v-for="item in purchaseOrders" :key="item.purchaseCode"
              :label="purchaseOptionLabel(item)" :value="item.purchaseCode" />
            <template #empty>
              <div class="select-empty">
                <p v-if="isRefund">没有可退款的采购单。</p>
                <p v-else>没有还要付款的采购单。</p>
                <p v-if="isRefund">只列<b>已经付过钱</b>的采购单（已付货款 &gt; 0），一分没付过的没什么可退。</p>
                <p v-if="isRefund">而且要在<b>退款时效</b>内：最后一笔付款完成后 3 天，过期的单会自动从这里消失。</p>
                <p v-else>只列<b>还没付满</b>的采购单，以下三类不会出现：已付货款 ≥ 订单总额（含超额支付）、草稿、已取消。</p>
              </div>
            </template>
          </el-select>
          <div class="form-tip">
            <template v-if="isRefund">
              只列还在<b>退款时效</b>内的采购单——时效是最后一笔付款完成后 3 天，过期的单自动消失。
              已付货款就是这张单最多能退多少。供应商与业务摘要由系统按采购单自动带出，
              完成后冲减该单的已付货款。
            </template>
            <template v-else>
              只列还没付满的单，括号外是还差多少没付。付满了这张单的货款就结清了，不会再出现在这里。
              供应商与业务摘要由系统按采购单自动带出，完成打款后回写该单的已付货款。
            </template>
          </div>
        </el-form-item>

        <!-- 订单号和采购单号一样从下拉里选。后端 applyBusinessBinding 对填了单号的销售流水
             是要校验存在性并按订单重算业务摘要的，让人手打单号只会打出一个「销售订单不存在」。
             回款和退款选的是相反的两批单：回款选没收满的，退款选收过钱的。 -->
        <el-form-item v-if="form.bizType === '销售'" label="订单号" prop="bizCode" :required="isRefund">
          <el-select v-model="form.bizCode"
            :placeholder="isRefund ? '退款必须选订单，退的是哪张单' : '知道是哪张单就选；不知道留空，由业务员在订单里认领'"
            filterable :clearable="!isRefund"
            :loading="receivableLoading" style="width: 100%" @change="handleSalesOrderChange">
            <el-option v-for="o in receivableOrders" :key="o.orderCode" :label="salesOptionLabel(o)" :value="o.orderCode" />
            <template #empty>
              <div class="select-empty">
                <template v-if="isRefund">
                  <p>没有可退款的订单。</p>
                  <p>只列<b>已经收过钱</b>的订单（已收金额 &gt; 0），一分没收过的没什么可退。</p>
                  <p>而且要在<b>退款时效</b>内：最后一笔回款完成后 3 天，过期的单会自动从这里消失。</p>
                </template>
                <template v-else>
                  <p>没有还能收款的订单。</p>
                  <p>只列<b>未收满</b>的订单，以下三类不会出现：已收金额 ≥ 订单总额（含超额收款）、草稿、已取消。</p>
                  <p>这里留空也能存，提交后由业务员在销售订单详情里认领。</p>
                </template>
              </div>
            </template>
          </el-select>
          <div class="form-tip">
            <template v-if="isRefund">
              只列已经收过钱、且还在<b>退款时效</b>内的订单（含已取消的），跨全部月份。
              时效是最后一笔回款完成后 3 天，过期的单自动消失。括号里是已收金额与剩余时效。
            </template>
            <template v-else>
              只列还没收满的订单，跨全部月份。<b>选了订单号，提交即算已认领</b>，只等财务终审，通过后回写该单的已收金额；
              <b>留空</b>则提交后等业务员在<b>销售订单详情 → 收款流水</b>里认领到自己的单上。
              一笔打款对应多张订单的，请按订单<b>拆成多笔流水</b>录入——一笔流水只能绑一张单。
            </template>
          </div>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="收支分类" prop="categoryCode">
              <el-select v-model="form.categoryCode" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in filteredCategories" :key="item.categoryCode" :label="item.categoryName" :value="item.categoryCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" controls-position="right" style="width: 100%" />
              <div class="form-tip">填正数即可，支出入库时自动转为负数</div>
            </el-form-item>
            <el-alert v-if="amountWarning" type="error" :closable="false" show-icon :title="amountWarning" class="mt8 mb8" />
          </el-col>
        </el-row>


        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="对方名称" prop="partyName">
              <el-input v-model="form.partyName" :disabled="form.bizType === '采购'" placeholder="供应商 / 客户 / 收款方" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账户" prop="accountCode">
              <el-select v-model="form.accountCode" placeholder="请选择账户" clearable style="width: 100%">
                <el-option v-for="item in formAccounts" :key="item.accountCode"
                  :label="accountLabel(item)" :value="item.accountCode" />
                <template #empty>
                  <div class="select-empty">
                    <p v-if="requiredCurrency">没有 {{ currencyLabel(requiredCurrency) }} 的启用账户。</p>
                    <p v-else>没有可用的账户。</p>
                    <p v-if="form.bizType === '采购'">采购的收付款只能走{{ currencyLabel(BASE_CURRENCY) }}账户。请先到<b>财务管理 → 财务账户</b>建一个{{ currencyLabel(BASE_CURRENCY) }}账户。</p>
                    <p v-else-if="selectedOrder">回款只能收进与订单同币种的账户，本系统不做汇率换算。请先到<b>财务管理 → 财务账户</b>建一个同币种账户。</p>
                  </div>
                </template>
              </el-select>
              <!-- 币种不给单独选：采购一律本位币，绑了订单就是订单的币种，其余跟账户走，后端按同一口径重算 -->
              <div class="form-tip" v-if="formCurrency">
                本笔币种 <b>{{ currencyLabel(formCurrency) }}</b><template v-if="form.bizType === '采购'">，采购只走本位币，下拉里已滤掉外币账户</template><template v-else-if="selectedOrder">，跟随订单，只能选同币种账户</template><template v-else>，跟随所选账户</template>
              </div>
              <div class="form-tip" v-if="form.bizType === '采购'">采购付款的账户由财务在执行打款时选择，这里可以不填</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="业务摘要" prop="bizSummary">
          <!-- 绑了单据的流水，摘要由后端按单据重算，这里禁掉避免用户白填一遍 -->
          <el-input v-model="form.bizSummary" :disabled="summaryLocked" placeholder="请填写业务摘要" maxlength="200" show-word-limit />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="交易时间" prop="tradeTime">
              <el-date-picker v-model="form.tradeTime" type="datetime" placeholder="资金实际到账/付出时间"
                value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 对方信息（文本 + 图片）挨在一起，凭证附件跟在后面：
             前两个说的是「钱从谁那来/到谁那去」，附件说的是「这笔钱的凭据」，是两件事 -->
        <el-form-item label="对方文本" prop="partyText">
          <el-input v-model="form.partyText" placeholder="对方账户等文本信息" maxlength="500" />
        </el-form-item>
        <el-form-item label="对方图片" prop="partyImage">
          <image-upload v-model="form.partyImage" :limit="2" />
        </el-form-item>
        <el-form-item label="凭证附件" prop="attachment">
          <image-upload v-model="form.attachment" :limit="3" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 审核 -->
    <el-dialog title="审核资金流水" v-model="auditOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="auditRef" :model="auditForm" label-width="100px">
        <el-form-item label="流水号">
          <span>{{ auditForm.transactionCode }}</span>
        </el-form-item>
        <el-form-item label="金额">
          <span :class="Number(auditForm.amount) < 0 ? 'amount-negative' : 'amount-positive'">{{ formatCurrency(auditForm.amount, auditForm.currency) }}</span>
        </el-form-item>
        <el-form-item label="业务摘要">
          <span>{{ auditForm.bizSummary }}</span>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.approved === false" label="驳回原因" required>
          <el-input v-model="auditForm.rejectReason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="说明驳回原因，经办人据此修改后重新提交" />
        </el-form-item>
        <el-alert v-if="auditForm.approved && auditForm.bizType === '销售'" type="warning" :closable="false" show-icon
          title="通过后流水立即完成入账，账户余额会随之变动。" />
        <el-alert v-else-if="auditForm.approved" type="info" :closable="false" show-icon
          title="通过后进入「已审核」，还需要财务执行打款才会入账。" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitAudit">确 定</el-button>
          <el-button @click="auditOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 执行打款 -->
    <el-dialog title="执行打款" v-model="executeOpen" width="560px" append-to-body :close-on-click-modal="false">
      <el-form ref="executeRef" :model="executeForm" :rules="executeRules" label-width="100px">
        <el-form-item label="流水号">
          <span>{{ executeForm.transactionCode }}</span>
        </el-form-item>
        <el-form-item label="付款金额">
          <span class="amount-negative">{{ formatCurrency(executeForm.amount, executeCurrency) }}</span>
        </el-form-item>
        <el-form-item label="付款账户" prop="accountCode">
          <el-select v-model="executeForm.accountCode" placeholder="请选择付款账户" style="width: 100%">
            <el-option v-for="item in executeAccounts" :key="item.accountCode"
              :label="accountLabel(item)" :value="item.accountCode" />
            <template #empty>
              <div class="select-empty">
                <p v-if="executeForm.currency">没有 {{ currencyLabel(executeForm.currency) }} 的启用账户。</p>
                <p v-else>没有可用的账户。</p>
                <p v-if="executeForm.bizType === '采购'">采购付款只能从{{ currencyLabel(BASE_CURRENCY) }}账户付出。请先到<b>财务管理 → 财务账户</b>建一个{{ currencyLabel(BASE_CURRENCY) }}账户。</p>
                <p v-else-if="executeForm.currency">退款只能从与订单同币种的账户付出，本系统不做汇率换算。</p>
              </div>
            </template>
          </el-select>
          <!-- 采购付款一律本位币；销售退款已绑订单，只能同币种；其余跟所选账户走 -->
          <div class="form-tip" v-if="executeForm.bizType === '采购'">只列 {{ currencyLabel(BASE_CURRENCY) }} 账户，采购只走本位币</div>
          <div class="form-tip" v-else-if="executeForm.currency">只列 {{ currencyLabel(executeForm.currency) }} 账户，与订单币种一致</div>
          <div class="form-tip" v-else>付款币种跟随所选账户</div>
        </el-form-item>
        <el-form-item label="交易时间" prop="tradeTime">
          <el-date-picker v-model="executeForm.tradeTime" type="datetime" placeholder="实际打款时间"
            value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="回单凭证">
          <image-upload v-model="executeForm.attachment" :limit="3" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon
          title="点击完成后账户余额立即扣减，并回写采购单的已付货款与付款状态。" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitExecute">确认已打款</el-button>
          <el-button @click="executeOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog title="资金流水详情" v-model="detailOpen" width="820px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="流水号">{{ detail.transactionCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="收支方向">{{ detail.transType }}</el-descriptions-item>
        <el-descriptions-item label="金额">
          <span :class="Number(detail.amount) < 0 ? 'amount-negative' : 'amount-positive'">{{ formatCurrency(detail.amount, detail.currency) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ detail.bizType }}</el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ detail.bizCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="收支分类">{{ detail.categoryName }}</el-descriptions-item>
        <el-descriptions-item label="对方名称">{{ detail.partyName }}</el-descriptions-item>
        <el-descriptions-item label="账户">{{ detail.accountName || '待选择' }}</el-descriptions-item>
        <el-descriptions-item label="完成后余额">
          {{ detail.balance !== null && detail.balance !== undefined ? formatCurrency(detail.balance, detail.currency) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="交易时间">{{ detail.tradeTime || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="审核人">
          {{ detail.auditUserName || detail.auditUser || '—' }}
          <span v-if="detail.auditTime" class="text-muted">（{{ detail.auditTime }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="完成人">
          {{ detail.completeUserName || detail.completeUser || '—' }}
          <span v-if="detail.completeTime" class="text-muted">（{{ detail.completeTime }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="对方文本">{{ detail.partyText || '—' }}</el-descriptions-item>
        <el-descriptions-item label="业务摘要" :span="2">{{ detail.bizSummary }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.status === '已驳回'" label="驳回原因" :span="2">
          <span class="reject-reason">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        <!-- 顺序与新建弹窗一致：对方图片在前、凭证附件在后 -->
        <el-descriptions-item label="对方图片" :span="2">
          <image-preview v-if="detail.partyImage" :src="detail.partyImage" :width="80" :height="80" />
          <span v-else class="text-muted">未上传</span>
        </el-descriptions-item>
        <el-descriptions-item label="凭证附件" :span="2">
          <image-preview v-if="detail.attachment" :src="detail.attachment" :width="80" :height="80" />
          <span v-else class="text-muted">未上传</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FinanceTransaction">
import {
  listTransaction, getTransaction, addTransaction, updateTransaction, submitTransaction,
  auditTransaction, executeTransaction, completeTransaction, delTransaction
} from '@/api/finance/transaction'
import { accountOptions } from '@/api/finance/account'
import { categoryOptions } from '@/api/finance/category'
import { listPayableOrders, listPurchaseRefundableOrders } from '@/api/purchase/order'
import { listReceivableOrders, listRefundableOrders } from '@/api/order/sales'
import { DEFAULT_CURRENCY, formatCurrency, currencyLabel, normalizeCurrency } from '@/utils/currency'

/**
 * 采购单没有币种字段：采购侧的金额一律按本位币显示，收付款也只能走本位币账户。
 * 后端 FinanceTransactionServiceImpl.requirePurchaseInBaseCurrency 是同一条规则。
 */
const BASE_CURRENCY = DEFAULT_CURRENCY

const { proxy } = getCurrentInstance()
const router = useRouter()

/** 业务类型只有这三种，查询和新建用同一份：与后端 FinanceTransactionServiceImpl.BIZ_TYPES 同一口径 */
const bizTypes = ['采购', '销售', '其他']
/** 业务类型「其他」的两条系统分类：QT_ZC 记支出、QT_SR 记收入，与后端 FinanceCategory 同一口径 */
const OTHER_CATEGORY_CODES = ['QT_ZC', 'QT_SR']
const statusOptions = ['草稿', '待审核', '已审核', '已完成', '已驳回']

const transactionList = ref([])
const accounts = ref([])
const categories = ref([])
const purchaseOrders = ref([])
const purchaseLoading = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const auditOpen = ref(false)
const executeOpen = ref(false)
const receivableOrders = ref([])
const receivableLoading = ref(false)
const detailOpen = ref(false)
const detail = ref({})
const tradeTimeRange = ref([])

const data = reactive({
  form: {},
  auditForm: {},
  executeForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    transactionCode: null,
    transType: null,
    bizType: null,
    bizCode: null,
    status: null,
    accountCode: null,
    partyName: null,
    tradeTimeBegin: null,
    tradeTimeEnd: null
  },
  rules: {
    bizType: [{ required: true, message: '业务类型不能为空', trigger: 'change' }],
    transType: [{ required: true, message: '收支方向不能为空', trigger: 'change' }],
    categoryCode: [{ required: true, message: '收支分类不能为空', trigger: 'change' }],
    amount: [{ required: true, message: '金额不能为空', trigger: 'blur' }],
    partyName: [{ required: true, message: '对方名称不能为空', trigger: 'blur' }],
    bizSummary: [{ required: true, message: '业务摘要不能为空', trigger: 'blur' }]
  },
  executeRules: {
    accountCode: [{ required: true, message: '请选择付款账户', trigger: 'change' }],
    tradeTime: [{ required: true, message: '请填写交易时间', trigger: 'change' }]
  }
})

const { queryParams, form, rules, auditForm, executeForm, executeRules } = toRefs(data)

/**
 * 分类下拉先按收支方向过滤——后端会校验分类的方向必须与流水一致，方向不符的选了也存不进去。
 *
 * <p>采购和销售还要再按业务类型收窄：这两类各有一收一支两条对口分类
 * （采购货款/采购退款、销售回款/销售退款），把「其他」也列出来只会让人选错。
 * 收窄后为空则退回按方向过滤——老库还没跑退款分类的迁移脚本时不至于选不到东西。</p>
 */
const filteredCategories = computed(() => {
  const byType = categories.value.filter(item => !form.value.transType || item.type === form.value.transType)
  if (form.value.bizType === '其他') {
    // 其他业务不分明细科目：一收一支各一条系统分类，方向定了分类就只剩一条
    return byType.filter(item => item.bizType === '其他' && OTHER_CATEGORY_CODES.includes(item.categoryCode))
  }
  if (form.value.bizType !== '采购' && form.value.bizType !== '销售') return byType
  const byBiz = byType.filter(item => item.bizType === form.value.bizType)
  return byBiz
})

/**
 * 是不是退款：钱的方向与该业务类型的常态相反。
 * 采购常态是付钱出去，收进来就是供应商退款；销售常态是收钱进来，付出去就是退给客户。
 * 判断口径与后端 isRefund 一致，改一边要改两边。
 */
const isRefund = computed(() => {
  if (form.value.bizType === '采购') return form.value.transType === '收入'
  if (form.value.bizType === '销售') return form.value.transType === '支出'
  return false
})

/**
 * 币种规则（与后端 CurrencySupport / FinanceTransactionServiceImpl 同一口径，改一边要改两边）：
 *
 * - 采购的收付款一律走本位币账户：采购单没有币种列，业务上也只做人民币，
 *   外币账户直接从下拉里滤掉（后端 requirePurchaseInBaseCurrency 是同一条规则）；
 * - 绑了销售订单的流水，币种 = 订单币种，账户只能选同币种的；
 * - 其余流水，币种 = 所选账户的币种，用户不能单独指定。
 *
 * 前端过滤只是别让人选错，真正拦住的是后端——直接调接口照样绕得过这里。
 */
const selectedOrder = computed(() => receivableOrders.value.find(item => item.orderCode === form.value.bizCode))
/** 本笔流水被业务限定死的币种；返回 undefined 表示不限定，跟所选账户走 */
const requiredCurrency = computed(() => {
  if (form.value.bizType === '采购') return BASE_CURRENCY
  return selectedOrder.value?.currency
})
const formCurrency = computed(() => requiredCurrency.value
  || accounts.value.find(item => item.accountCode === form.value.accountCode)?.currency)
const formAccounts = computed(() => filterByCurrency(requiredCurrency.value))
/**
 * 执行打款时的账户候选。executeForm.currency 在销售退款（跟订单）和采购付款（本位币）
 * 两种情况下都有值，只有「其他」类支出才留空表示不过滤。
 */
const executeAccounts = computed(() => filterByCurrency(executeForm.value.currency))

/** 按币种筛启用账户；currency 为空表示不筛 */
function filterByCurrency(currency) {
  if (!currency) return accounts.value
  return accounts.value.filter(item => normalizeCurrency(item.currency) === normalizeCurrency(currency))
}
/** 付款金额按最终会入账的币种显示：选了账户就跟账户，没选就用订单币种 */
const executeCurrency = computed(() =>
  accounts.value.find(item => item.accountCode === executeForm.value.accountCode)?.currency
  || executeForm.value.currency)

/** 采购金额校验提示：付款不能超过待付，退款不能超过已付。实时显示，不等到提交才报错。 */

/** 金额校验提示：采购付款不能超过待付，采购退款不能超过已付；销售回款不能超过待收，销售退款不能超过已收。实时显示。 */
const amountWarning = computed(() => {
  if (!form.value.bizCode || !form.value.amount) return ''
  const amount = Math.abs(Number(form.value.amount))

  if (form.value.bizType === '采购') {
    const order = purchaseOrders.value.find(item => item.purchaseCode === form.value.bizCode)
    if (!order) return ''
    if (isRefund.value) {
      const paid = Number(order.paidAmount || 0)
      if (paid <= 0) return '采购单「' + order.purchaseCode + '」没有已付货款，没有可退的钱'
      if (amount > paid) return '本笔退款 ' + amount.toFixed(2) + ' 超过采购单「' + order.purchaseCode + '」的已付货款 ' + paid.toFixed(2) + '，不允许超额退款'
    } else {
      const total = Number(order.totalAmount || 0)
      if (total <= 0) return ''
      const remaining = total - Number(order.paidAmount || 0)
      if (amount > remaining) return '本笔付款 ' + amount.toFixed(2) + ' 超过采购单「' + order.purchaseCode + '」的待付 ' + remaining.toFixed(2) + '，不允许超额付款'
    }
  }

  if (form.value.bizType === '销售') {
    const order = receivableOrders.value.find(item => item.orderCode === form.value.bizCode)
    if (!order) return ''
    if (isRefund.value) {
      // 销售退款：不能超过已收金额
      const paid = Number(order.paidAmount || 0)
      if (paid <= 0) return '订单「' + order.orderCode + '」没有已收金额，没有可退的钱'
      if (amount > paid) return '本笔退款 ' + amount.toFixed(2) + ' 超过订单「' + order.orderCode + '」的已收金额 ' + paid.toFixed(2) + '，不允许超额退款'
    } else {
      // 销售回款：不能超过待收金额
      const total = Number(order.totalAmount || 0)
      if (total <= 0) return ''
      const remaining = total - Number(order.paidAmount || 0)
      if (amount > remaining) return '本笔回款 ' + amount.toFixed(2) + ' 超过订单「' + order.orderCode + '」的待收 ' + remaining.toFixed(2) + '，不允许超额回款'
    }
  }

  return ''
})

/**
/**
 * 付款关心还差多少没付，退款关心已付了多少（能退的上限）。
 *
 * 采购单本期没有币种字段，金额一律按本位币显示；采购付款的实际币种要等
 * 「执行打款」选定账户才定下来，那一步的弹窗按账户币种显示。
 */
function purchaseOptionLabel(item) {
  if (isRefund.value) {
    return `${item.purchaseCode}　${item.partnerName}　已付 ${formatCurrency(item.paidAmount, BASE_CURRENCY)}`
      + refundWindowText(item.refundDeadline)
  }
  const total = Number(item.totalAmount || 0)
  const paid = Number(item.paidAmount || 0)
  return `${item.purchaseCode}　${item.partnerName}　待付 ${formatCurrency(total - paid, BASE_CURRENCY)}` +
    `（总额 ${formatCurrency(total, BASE_CURRENCY)}` +
    (paid > 0 ? `　已付 ${formatCurrency(paid, BASE_CURRENCY)}` : '') + '）'
}

const refundTip = computed(() => {
  if (!isRefund.value) return ''
  return form.value.bizType === '采购'
    ? '这是一笔采购退款：供应商把钱退回来，完成后会冲减该采购单的已付货款'
    : '这是一笔销售退款：钱退给客户，完成后会冲减该订单的已收金额'
})

const bizTypeTip = computed(() => {
  if (form.value.bizType === '采购') {
    return isRefund.value
      ? '采购退款：提交 → 审核通过即完成入账，冲减采购单已付货款'
      : '流程1：提交 → 审核 → 财务执行打款，完成后回写采购单'
  }
  if (form.value.bizType === '销售') {
    return isRefund.value
      ? '销售退款：提交 → 审核 → 财务执行打款，完成后冲减订单已收金额（建单时必须选订单）'
      : '流程2：提交 → 业务员在销售订单详情里认领到自己的单（建单时选了订单号的，提交即绑定）→ 审核人终审完成'
  }
  return form.value.transType === '收入'
    ? '流程3：提交 → 审核人确认完成，直接入账（非业务收入，如利息、赔款、退税）'
    : '流程3：提交 → 审核人确认完成，直接入账'
})

function accountLabel(item) {
  return `${item.accountName}（${currencyLabel(item.currency)}，余额 ${formatCurrency(item.currentBalance, item.currency)}）`
}

function statusTagType(status) {
  return { '草稿': 'info', '待审核': 'warning', '已审核': 'primary', '已完成': 'success', '已驳回': 'danger' }[status] || 'info'
}

/** 草稿和已驳回可以改、可以提交 */
function editable(row) {
  return row.status === '草稿' || row.status === '已驳回'
}

/** 采购付款在待审核时审核；销售回款在认领之后（已审核）终审 */
/** 某一行是不是退款：方向与该业务类型的常态相反。判断口径与后端 isRefund 一致 */
function rowIsRefund(row) {
  if (row.bizType === '采购') return row.transType === '收入'
  if (row.bizType === '销售') return row.transType === '支出'
  return false
}

function canAudit(row) {
  // 客户回款要先认领，所以审核发生在已审核（= 已认领）态；其余业务流水都在待审核态审
  if (row.bizType === '销售' && !rowIsRefund(row)) return row.status === '已审核'
  if (row.bizType === '采购' || row.bizType === '销售') return row.status === '待审核'
  return false
}

/** 还在等业务员认领的客户回款：状态列把它标成「待认领」。退款在建单时就绑好订单，没有这一步 */
function awaitingClaim(row) {
  return row.bizType === '销售' && !rowIsRefund(row) && row.status === '待审核'
}

/** 非业务流水由审核人一步完成 */
function canComplete(row) {
  return row.status === '待审核' && row.bizType !== '采购' && row.bizType !== '销售'
}

function canExecute(row) {
  return row.status === '已审核' && row.transType === '支出'
}

function getList() {
  loading.value = true
  queryParams.value.tradeTimeBegin = tradeTimeRange.value && tradeTimeRange.value.length
    ? tradeTimeRange.value[0] + ' 00:00:00' : null
  queryParams.value.tradeTimeEnd = tradeTimeRange.value && tradeTimeRange.value.length
    ? tradeTimeRange.value[1] + ' 23:59:59' : null
  listTransaction(queryParams.value).then(response => {
    transactionList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function loadOptions() {
  accountOptions().then(response => { accounts.value = response.data || [] })
  categoryOptions().then(response => { categories.value = response.data || [] })
  // 采购单不在这里拉：付款和退款要的是相反的两批单，得等知道方向了才知道拉哪一批。
  // 见 loadPurchaseOrderOptions。
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  tradeTimeRange.value = []
  handleQuery()
}

function reset() {
  form.value = {
    transactionCode: null,
    tableSuffix: null,
    bizType: '其他',
    transType: '支出',
    bizCode: null,
    categoryCode: null,
    amount: null,
    partyName: null,
    accountCode: null,
    bizSummary: null,
    tradeTime: null,
    partyText: null,
    partyImage: null,
    attachment: null,
    remark: null,
    // 超额付款要人明确勾一下才放行，每次开弹窗都从「不允许」重新开始
  }
  proxy.resetForm('transactionRef')
}

function cancel() {
  open.value = false
  reset()
}

/**
 * 换业务类型时把方向拨回该类型的常态：采购是付款、销售是回款、其他是支出。
 * 方向本身仍可以再改（采购/销售的反方向是退款，其他的反方向是其他收入）。
 */
function handleBizTypeChange(value) {
  if (value === '采购') form.value.transType = '支出'
  if (value === '销售') form.value.transType = '收入'
  if (value === '其他') form.value.transType = '支出'
  form.value.categoryCode = null
  autoSelectOtherCategory()
  form.value.bizCode = null
  // 换到采购之后账户候选只剩本位币，之前选的外币账户已经不在下拉里了，
  // 留着的话界面显示成空、提交时才被后端拒，不如当场清掉
  const picked = accounts.value.find(item => item.accountCode === form.value.accountCode)
  if (picked && requiredCurrency.value
      && normalizeCurrency(picked.currency) !== normalizeCurrency(requiredCurrency.value)) {
    form.value.accountCode = null
  }
  // 两份单据列表都按需拉：多数流水既不是回款也不是采购付款，没必要一进页面就去扫。
  // 销售那份每次都重新拉，是因为它和认领弹窗共用，那边可能按月份筛过
  if (value === '销售') loadSalesOrderOptions()
  else if (value === '采购') loadPurchaseOrderOptions()
}

/** 绑了单据的流水摘要由后端按单据重算，前端别让人白填一遍 */
const summaryLocked = computed(() =>
  form.value.bizType === '采购' || (form.value.bizType === '销售' && !!form.value.bizCode))

/**
 * 选中订单后把对方名称和摘要先带出来。服务端还会按订单重算一遍，
 * 这里只是让财务在保存前就看到摘要长什么样。
 */
function handleSalesOrderChange(orderCode) {
  const order = receivableOrders.value.find(item => item.orderCode === orderCode)
  if (!order) return
  if (form.value.accountCode) {
    const account = accounts.value.find(item => item.accountCode === form.value.accountCode)
    if (account && account.currency !== order.currency) form.value.accountCode = null
  }
  // 对方名称是银行回单上的付款方，财务已经填了就别覆盖——付款方和收件人常常不是同一个人
  if (!form.value.partyName) form.value.partyName = order.receivePerson
  const customer = form.value.partyName || order.receivePerson
  const total = Number(order.totalAmount || 0)
  const paid = Number(order.paidAmount || 0)
  form.value.bizSummary = `订单 ${order.orderCode} ${isRefund.value ? '退款' : '回款'}` +
    (customer ? `（客户：${customer}）` : '') +
    `，订单金额 ${formatCurrency(total, order.currency)}` + (paid > 0 ? `，已收 ${formatCurrency(paid, order.currency)}` : '')
}

function handleTransTypeChange() {
  form.value.categoryCode = null
  autoSelectOtherCategory()
  // 正常方向和退款方向选的是不同的候选集，方向一变原来选的单多半已经不在新列表里了
  if (form.value.bizType === '销售') {
    form.value.bizCode = null
    loadSalesOrderOptions()
  }
  else if (form.value.bizType === '采购') {
    form.value.bizCode = null
    loadPurchaseOrderOptions()
  }
}

/**
 * 建单弹窗里的采购单下拉：付款拉还没付满的，退款拉付过钱的。
 *
 * <p>筛选在服务端做（/purchase/order/payable）。原先是拉 /list 的第一页 200 条再前端过滤，
 * 采购单超过 200 张的商家从第 201 张起压根进不了下拉，而且没有任何提示。</p>
 *
 * <p>includeCode 传的是当前已经绑上的单号：改一笔已存的草稿时，它绑的那张单可能在
 * 草稿存下之后才被付满，按候选条件已经不在列表里了；不带上它，编辑框一打开单号就空了。</p>
 */
function loadPurchaseOrderOptions(includeCode) {
  const fetcher = isRefund.value ? listPurchaseRefundableOrders : listPayableOrders
  purchaseOrders.value = []
  purchaseLoading.value = true
  return fetcher(includeCode || undefined)
    .then(r => purchaseOrders.value = r.data || [])
    .finally(() => purchaseLoading.value = false)
}

/** 建单弹窗里的销售订单下拉：回款拉未收满的，退款拉收过钱的 */
function loadSalesOrderOptions() {
  const fetcher = isRefund.value ? listRefundableOrders : listReceivableOrders
  receivableOrders.value = []
  receivableLoading.value = true
  return fetcher(undefined)
    .then(r => receivableOrders.value = r.data || [])
    .finally(() => receivableLoading.value = false)
}

/** 下拉文案：回款关心还差多少没收，退款关心已经收了多少（能退的上限） */
function salesOptionLabel(order) {
  if (!isRefund.value) return receivableLabel(order)
  const suffix = String(order.tableSuffix || '')
  const month = suffix.length === 6 ? `${suffix.slice(0, 4)}-${suffix.slice(4)} ` : ''
  const cancelled = order.orderState === '已取消' ? '［已取消］' : ''
  return `${month}${order.orderCode} / ${order.receivePerson || ''} ${cancelled}` +
    `（已收 ${formatCurrency(order.paidAmount, order.currency)}${refundWindowText(order.refundDeadline)}）`
}

/**
 * 退款时效的剩余时间。后端只返回还在窗口内的单据，所以这里不用判断"是否过期"，
 * 只把还剩多久说清楚——快到期的单要显眼，免得业务员拖到过期才发现退不了。
 */
function refundWindowText(deadline) {
  if (!deadline) return ''
  const left = new Date(String(deadline).replace(/-/g, '/')) - Date.now()
  if (left <= 0) return '　⚠ 已过期'
  const hours = Math.floor(left / 3600000)
  return hours < 24 ? `　⚠ 退款时效仅剩 ${hours} 小时` : `　退款时效剩 ${Math.floor(hours / 24)} 天`
}

function handlePurchaseChange(purchaseCode) {
  // 换了一张单，之前那张单上的超额确认就不作数了，得重新看重新勾
  const order = purchaseOrders.value.find(item => item.purchaseCode === purchaseCode)
  if (!order) return
  // 供应商与摘要服务端还会按采购单重算一遍，这里只是让用户提前看到
  form.value.partyName = order.partnerName
  form.value.bizSummary = isRefund.value
    ? `采购单 ${order.purchaseCode} 退款（供应商：${order.partnerName}）`
    : `支付采购单 ${order.purchaseCode} 货款（供应商：${order.partnerName}）`
}

function handleAdd() {
  reset()
  autoSelectOtherCategory()
  open.value = true
  title.value = '新建资金流水'
}

/**
 * 其他业务的分类由收支方向唯一确定（支出 QT_ZC、收入 QT_SR），直接替用户选上，
 * 省得在只有一条的下拉里再点一次。分类还没加载完就先留空，用户手选。
 */
function autoSelectOtherCategory() {
  if (form.value.bizType !== '其他') return
  const candidates = filteredCategories.value
  form.value.categoryCode = candidates.length === 1 ? candidates[0].categoryCode : null
}

function handleUpdate(row) {
  reset()
  getTransaction(row.transactionCode, row.tableSuffix).then(response => {
    const item = response.data
    form.value = Object.assign({}, item, { amount: Math.abs(Number(item.amount)) })
    // 改的是绑了单据的草稿时，对应的下拉得有数据，否则已绑的单号在框里只是一个光秃秃的字符串。
    // 采购要把已绑的单号当 includeCode 传回去：它可能在草稿存下之后才被付满，
    // 那样就不在「还没付满」的候选里了
    if (item.bizType === '销售') loadSalesOrderOptions()
    else if (item.bizType === '采购') loadPurchaseOrderOptions(item.bizCode)
    open.value = true
    title.value = '修改资金流水'
  })
}

function submitForm() {
  proxy.$refs['transactionRef'].validate(valid => {
    if (!valid) return
    const action = form.value.transactionCode ? updateTransaction : addTransaction
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.transactionCode ? '修改成功' : '新建成功')
      open.value = false
      getList()
      loadOptions()
    })
  })
}

function handleSubmit(row) {
  // 客户回款提交后去哪一步，取决于建单时有没有选订单号：选了直接算已认领等终审，没选等业务员在订单里认领
  const receipt = row.bizType === '销售' && !rowIsRefund(row)
  const done = !receipt ? '提交成功'
    : row.bizCode ? '已提交并绑定订单，等财务终审'
      : '已提交，等业务员在销售订单详情里认领'
  proxy.$modal.confirm(`确认提交流水「${row.transactionCode}」？`).then(function () {
    return submitTransaction(row.transactionCode, row.tableSuffix)
  }).then(() => {
    proxy.$modal.msgSuccess(done)
    getList()
  }).catch(() => {})
}

function handleAudit(row) {
  auditForm.value = {
    transactionCode: row.transactionCode,
    tableSuffix: row.tableSuffix,
    amount: row.amount,
    currency: row.currency,
    bizType: row.bizType,
    bizSummary: row.bizSummary,
    approved: true,
    rejectReason: null
  }
  auditOpen.value = true
}

function submitAudit() {
  if (auditForm.value.approved === false && !auditForm.value.rejectReason) {
    proxy.$modal.msgError('驳回必须填写原因')
    return
  }
  auditTransaction(auditForm.value.transactionCode, {
    tableSuffix: auditForm.value.tableSuffix,
    approved: auditForm.value.approved,
    rejectReason: auditForm.value.rejectReason
  }).then(() => {
    proxy.$modal.msgSuccess(auditForm.value.approved ? '审核通过' : '已驳回')
    auditOpen.value = false
    getList()
    loadOptions()
  })
}

function handleExecute(row) {
  executeForm.value = {
    transactionCode: row.transactionCode,
    tableSuffix: row.tableSuffix,
    amount: row.amount,
    // 弹窗里要按业务类型给不同的币种说明，光有 currency 分不出「采购锁本位币」和「退款跟订单」
    bizType: row.bizType,
    // 销售退款建单时就绑死了订单，币种跟订单走，只能从同币种账户付出。
    // 采购付款建单时没选账户，但采购一律走本位币，所以这里直接锁 CNY——
    // 不能照搬 row.currency：那是建单时按当时账户（可能没选）落下的值，不作数。
    // 其余情况留空表示不过滤，币种由这一步选的账户决定。
    currency: row.bizType === '销售' ? row.currency
      : row.bizType === '采购' ? BASE_CURRENCY : undefined,
    accountCode: row.accountCode,
    tradeTime: row.tradeTime,
    attachment: row.attachment
  }
  executeOpen.value = true
}

function submitExecute() {
  proxy.$refs['executeRef'].validate(valid => {
    if (!valid) return
    executeTransaction(executeForm.value.transactionCode, {
      tableSuffix: executeForm.value.tableSuffix,
      accountCode: executeForm.value.accountCode,
      tradeTime: executeForm.value.tradeTime,
      attachment: executeForm.value.attachment
    }).then(() => {
      proxy.$modal.msgSuccess('打款已完成')
      executeOpen.value = false
      getList()
      loadOptions()
    })
  })
}

/** 只有采购单和销售订单在系统里有对应的单据页，其他流水的单号是自由填的 */
function bizLinkable(row) {
  return row.bizType === '采购' || row.bizType === '销售'
}

/**
 * 跳到业务单据页。跳不过去多半是没有那个模块的菜单权限，
 * 直接说清楚，别让人对着一个没反应的链接点。
 */
function openBizDocument(row) {
  const target = row.bizType === '采购'
    ? { path: '/purchase/order', query: { purchaseCode: row.bizCode } }
    : { path: '/order/sales', query: { orderCode: row.bizCode } }
  router.push(target).catch(() => {
    proxy.$modal.msgError(`打不开 ${row.bizCode}，你可能没有${row.bizType}模块的访问权限`)
  })
}

/**
 * 下拉文案带上金额与已收，业务员一眼能对上银行回单的数。
 * 全部月份时会混着多个月的单，所以把订单月份也标出来。
 */
function receivableLabel(order) {
  const total = Number(order.totalAmount || 0)
  const paid = Number(order.paidAmount || 0)
  const suffix = String(order.tableSuffix || '')
  const month = suffix.length === 6 ? `${suffix.slice(0, 4)}-${suffix.slice(4)} ` : ''
  return `${month}${order.orderCode} / ${order.receivePerson || ''} / 待收 ${formatCurrency(total - paid, order.currency)}` +
    `（应收 ${formatCurrency(total, order.currency)}` + (paid > 0 ? ` / 已收 ${formatCurrency(paid, order.currency)}` : '') + '）'
}

// 这里没有认领弹窗：认领搬到了销售订单详情的「收款流水」页签，业务员站在自己的单上挑打款，
// 认不到同事的单上；认错了也在那里撤回。见 docs/superpowers/specs/2026-09-20-order-side-receipt-claim-design.md

function handleComplete(row) {
  proxy.$modal.confirm(`确认完成流水「${row.transactionCode}」？完成后账户余额立即变动。`).then(function () {
    return completeTransaction(row.transactionCode, row.tableSuffix)
  }).then(() => {
    proxy.$modal.msgSuccess('流水已完成')
    getList()
    loadOptions()
  }).catch(() => {})
}

function handleDetail(row) {
  getTransaction(row.transactionCode, row.tableSuffix).then(response => {
    detail.value = response.data
    detailOpen.value = true
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`是否确认删除流水「${row.transactionCode}」？`).then(function () {
    return delTransaction(row.transactionCode, row.tableSuffix)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

getList()
loadOptions()

// 本页被 keep-alive 缓存，去「财务账号」「收支分类」维护完切回来不会重新执行上面的初始化，
// 刚新增的账户在「账户」「付款账户」下拉里就选不到。onActivated 首次挂载也会触发一次，用标志位跳过。
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) {
    skipFirstActivate = false
    return
  }
  loadOptions()
})
</script>

<style scoped>
.amount-positive {
  color: #67c23a;
  font-weight: 600;
}
.amount-negative {
  color: #f56c6c;
  font-weight: 600;
}
.text-muted {
  color: #909399;
}
.refund-tip {
  color: var(--el-color-warning);
}
.select-empty {
  padding: 10px 20px;
  color: #909399;
  font-size: 12px;
  line-height: 1.7;
  text-align: left;
}
.select-empty p {
  margin: 0;
}
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
.reject-reason {
  color: #f56c6c;
}
</style>

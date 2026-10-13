<template>
  <div class="app-container">
    <el-alert v-if="warehouseMode" type="warning" show-icon :closable="false" class="mb8"
      title="点「完成」＝承运商把货取走，在那之前都能驳回"
      description="仓库这边干四件事：①「打印备货单」拿着纸去拣货，纸没出来可以在那一行点「补打」；②货备好装箱后点「上传」，传备货照片并点「备货完成」——照片至少一张，业务员就靠它确认货对不对；③等业务员录完快递单号，点「打印快递单号」把面单打出来贴箱子上；④承运商真把货拉走了才点「完成」，那一步开销售出库单、真正扣减库存并回写订单已发量。中间「备货完成 → 已录入快递单号」那一格球在业务员脚下，仓库看得到进度但没有按钮。「完成」之前发现装错货、贴错单，点「驳回」把批次退回「待确认」，填清原因交还业务员重排，不用先把错的货扣掉再走退货。批次要新建、改数量或作废，得回「订单管理 → 发货批次」——那是订单的排产计划，仓库不动。" />
    <el-alert v-else type="info" show-icon :closable="false" class="mb8"
      title="确认之后交给仓库，等仓库备完货再回来录快递单号"
      description="新建的批次是「待确认」，这时还能改、能取消。点「确认」后状态变成「已提交仓库」，仓库发货管理才看得到它，订单这边就改不了数量了。接着仓库打印备货单、拣货、传备货照片并点「备货完成」——批次一到「备货完成」，这里就多出「录入快递单号」按钮，那是业务员的活：看着备货照片填快递单号、承运商，并按实际备好的货敲定本次发货数量（只能往下调）。这一步会卡客户的付款额度：整单累计的发货货值不能超过客户已付金额，超了发不出去。录完之后仓库打面单、点「完成」，库存在那一刻才扣。到「备货完成」为止这里都可以作废整张批次；已经录了快递单号的，请让仓库先点「驳回」退回待确认。被驳回的批次会带着驳回原因回到这个列表，改完重新确认即可。" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <!-- 批次按「计划发货日期」落月表，不是订单的创建月份 -->
      <el-form-item label="发货月份">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" @change="loadList" />
      </el-form-item>
      <el-form-item label="批次号"><el-input v-model="query.shipCode" clearable style="width: 170px" @keyup.enter="loadList" /></el-form-item>
      <el-form-item label="订单号"><el-input v-model="query.orderCode" clearable style="width: 170px" @keyup.enter="loadList" /></el-form-item>
      <el-form-item label="状态">
        <!-- 170 而不是 140：最长的选项「已打印快递单号」七个字，140 会把尾字压在下拉箭头底下 -->
        <el-select v-model="query.status" clearable style="width: 170px">
          <el-option v-for="s in states" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="发货仓库">
        <el-select v-model="query.warehouseCode" clearable filterable style="width: 200px">
          <el-option v-for="w in warehouses" :key="w.wareCode" :label="warehouseLabel(w)" :value="w.wareCode" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadList">搜索</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5" v-if="!warehouseMode"><el-button type="primary" plain icon="Plus" v-hasPermi="['order:shipbatch:add']" @click="openCreate">新建批次</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadList" />
    </el-row>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="shipCode" label="批次号" min-width="165" />
      <el-table-column prop="orderCode" label="订单号" min-width="130" />
      <!--
        状态紧跟订单号，<b>不放在最右边</b>。两个原因：

        一是这张表上最常被扫的就是状态列——「哪几张该我动」全看它，摆在第三列一眼扫得到；
        二是它<b>不能</b>挨着「操作」列。操作列是 fixed="right"，一旦表格总宽超出容器，
        它会悬浮在滚动层之上，把紧邻的那一列盖掉一截——状态原先就在那个位置，
        新状态名长到七个字（已打印快递单号）之后被盖得只剩半截。

        宽度按最长的一种算：七字状态标签（约 100px）+「曾驳回」标签（约 57px）
        + 单元格左右内边距 24px ≈ 181px，取 190 留一点余量，两个标签能并排不换行。
      -->
      <el-table-column prop="status" label="状态" min-width="190">
        <template #default="scope">
          <el-tag size="small" :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag>
          <!--
            被驳回退回来的批次和业务员自己新建的批次状态一模一样，都是「待确认」。
            列表上不标一下，人打开订单侧只会看见一张平平无奇的草稿，不知道它是被退回来的，
            更不知道为什么退。原因挂 tooltip 上，不占列宽。
          -->
          <el-tooltip v-if="wasRejected(scope.row)" placement="top" :content="rejectHint(scope.row)">
            <el-tag size="small" type="danger" effect="plain" class="ml5">被驳回</el-tag>
          </el-tooltip>
          <!--
            已经重新提交上来的：对仓库来说这是「上次我退回去的那张又回来了」，
            标一下才会想起来去核对业务员到底改没改。驳回三列不随重走流程清空，所以标得出来。
            订单侧同样受用——自己重新提交过的单子，一眼能看出历史。
          -->
          <el-tooltip v-else-if="scope.row.rejectTime" placement="top" :content="rejectHint(scope.row)">
            <el-tag size="small" type="info" effect="plain" class="ml5">曾驳回</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="shipDate" label="计划发货" width="110">
        <template #default="scope">{{ parseTime(scope.row.shipDate, '{y}-{m}-{d}') }}</template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="发货仓库" min-width="110" :show-overflow-tooltip="true" />
      <el-table-column prop="totalQty" label="数量" width="90" align="right" />
      <el-table-column prop="carrier" label="承运商" min-width="110">
        <template #default="scope">{{ scope.row.carrier || '—' }}</template>
      </el-table-column>
      <el-table-column prop="logisticsNo" label="快递单号" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.logisticsNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="actualShipTime" label="实际发货" width="160">
        <template #default="scope">{{ scope.row.actualShipTime || '—' }}</template>
      </el-table-column>
      <!--
        宽度按按钮最多/最长的那一行算：仓库侧「详情 打印快递单号 驳回」十个字、
        「详情 上传 补打 驳回」四个按钮，两者都在 200px 出头；订单侧最长是
        「详情 录入快递单号 取消」。原先给到 320/250 是照着「宁可宽一点」拍的，
        而每多给一点这里，表格就多一点横向溢出，固定列就会去盖左边那一列。
      -->
      <el-table-column label="操作" :width="warehouseMode ? 250 : 220" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
          <!--
            订单侧：改数量只到「待确认」为止——批次一提交，仓库可能已经在按单拣货了，
            这时候悄悄改数量拣出来的货就对不上。真要调，到「录入快递单号」那一步按实际
            备好的货往下调，那时候货已经在箱子里了，调的是事实而不是计划。

            取消口子留到「备货完成」：客户临时取消订单是常有的事，而订单要关闭/取消/撤审，
            SalesOrderServiceImpl.assertNoPendingBatches 要求先把未完成的批次清掉。
            只留在「待确认」的话，「批次已提交、仓库还没发货、客户要取消」就成了死局——
            批次取消不掉，订单也关不掉。

            「已录入快递单号」往后不给取消，但不是因为撤不回来——那时候货其实还在仓库里。
            是因为单子上带的是业务员自己填的面单信息、仓库正按它打单贴箱，该由仓库先点
            「驳回」把它退回来并写清原因，订单侧一键作废的话仓库只会看见行凭空消失。
            退回「待确认」之后照样能取消。
            「已完成」才是真撤不回来的：货被承运商拉走、库存也扣了，只能走退货流程。
            后端 cancelBatch 也是这么挡的。
          -->
          <el-button link type="primary" v-if="!warehouseMode && isPending(scope.row)" v-hasPermi="['order:shipbatch:edit']" @click="openEdit(scope.row)">修改</el-button>
          <el-button link type="success" v-if="!warehouseMode && isPending(scope.row)" v-hasPermi="['order:shipbatch:confirm']" @click="confirm(scope.row)">确认</el-button>
          <!--
            录入快递单号：业务员在这条链上的第二个动作，只在「备货完成」这一格出现。
            仓库传完备货照片、球交到业务员脚下，这里才亮起来。
          -->
          <el-button link type="warning" v-if="!warehouseMode && scope.row.status === '备货完成'" v-hasPermi="['order:shipbatch:logistics']" @click="openLogistics(scope.row)">录入快递单号</el-button>
          <el-button link type="danger" v-if="!warehouseMode && canCancel(scope.row)" v-hasPermi="['order:shipbatch:cancel']" @click="cancel(scope.row)">取消</el-button>
          <!--
            仓库侧按 打印备货单 → 备货完成 →（等业务员录单）→ 打印快递单号 → 完成 往下推。
            两个打印动作后面都留一个「补打」：浏览器的打印窗口点了取消我们这边收不到
            （onafterprint 打印和取消都会触发，分不出来），状态照样已经推过去了；
            没有补打入口，那张批次就再也打不出纸了。补打只出纸，不改状态。
          -->
          <el-button link type="warning" v-if="warehouseMode && scope.row.status === '已提交仓库'" v-hasPermi="['order:shipbatch:print']" @click="openPrint(scope.row)">打印备货单</el-button>
          <el-button link type="success" v-if="warehouseMode && scope.row.status === '已打印备货单'" v-hasPermi="['order:shipbatch:picking']" @click="openUpload(scope.row)">上传</el-button>
          <el-button link type="info" v-if="warehouseMode && scope.row.status === '已打印备货单'" v-hasPermi="['order:shipbatch:print']" @click="openPrint(scope.row)">补打</el-button>
          <!-- 备货完成之后球在业务员那儿，仓库这一格只剩「补传照片」：漏拍一张不至于
               让整张批次被驳回重走一遍 -->
          <el-button link type="info" v-if="warehouseMode && scope.row.status === '备货完成'" v-hasPermi="['order:shipbatch:picking']" @click="openUpload(scope.row)">补传照片</el-button>
          <el-button link type="warning" v-if="warehouseMode && scope.row.status === '已录入快递单号'" v-hasPermi="['order:shipbatch:print']" @click="openLabel(scope.row)">打印快递单号</el-button>
          <el-button link type="danger" v-if="warehouseMode && scope.row.status === '已打印快递单号'" v-hasPermi="['order:shipbatch:ship']" @click="openComplete(scope.row)">完成</el-button>
          <el-button link type="info" v-if="warehouseMode && scope.row.status === '已打印快递单号'" v-hasPermi="['order:shipbatch:print']" @click="openLabel(scope.row)">补打</el-button>
          <!--
            驳回：仓库的后悔药，货还没被承运商取走的五格都给。装箱时才发现装错货、
            面单贴错箱，这些都发生在「已打印快递单号」之后、「完成」之前——那时货还在
            仓库里，拆开重装就是了，不该被迫先点完成把错的货扣掉再走一遍退货。
            驳回后批次退回「待确认」，从这张列表上消失，回到订单侧等业务员处理。
          -->
          <el-button link type="danger" v-if="warehouseMode && canReject(scope.row)" v-hasPermi="['order:shipbatch:ship']" @click="openReject(scope.row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ===================== 新建 / 修改批次 ===================== -->
    <el-dialog v-model="createOpen" :title="editing ? '修改发货批次' : '新建发货批次'" width="1000px" destroy-on-close :close-on-click-modal="false">
      <!--
        被仓库驳回退回来的批次，打开修改弹窗时先把原因摆在最上面。
        这是业务员改这张单唯一的依据，埋在详情弹窗里没人会专门去点。
      -->
      <el-alert v-if="editing && wasRejected(form)" type="error" show-icon :closable="false" class="mb8"
        title="这张批次被仓库驳回了"
        :description="rejectHint(form) + '。改完数量/仓库/计划日期后重新点「确认」提交仓库；这单不发了就直接取消。'" />
      <el-form :model="form" label-width="110px">
        <div class="section-title">来源订单</div>
        <el-row :gutter="20">
          <el-col :span="8" v-if="!editing">
            <!-- 订单按创建月份分表，跨全部月份扫代价太高，所以这里也给月份选择器 -->
            <el-form-item label="订单月份">
              <el-date-picker v-model="orderMonth" type="month" value-format="YYYY-MM" :clearable="false"
                style="width:100%" @change="loadShippableOrders" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="销售订单" required>
              <el-select v-model="form.orderCode" filterable :disabled="editing" placeholder="选择可发货的订单"
                style="width:100%" @change="onOrderChange">
                <el-option v-for="o in shippableOrders" :key="o.orderCode" :label="orderLabel(o)" :value="o.orderCode" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <!-- 计划发货日期决定本批次落哪张月表，跨月请取消重建 -->
            <el-form-item label="计划发货日期" required>
              <el-date-picker v-model="form.shipDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发货仓库" required>
              <el-select v-model="form.warehouseCode" filterable placeholder="从哪个仓发" style="width:100%">
                <el-option v-for="w in warehouses" :key="w.wareCode" :label="warehouseLabel(w)" :value="w.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="canSeeCost" :span="8">
            <el-form-item label="运费">
              <el-input-number v-model="form.freightAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="承运商"><el-input v-model="form.carrier" placeholder="选填，「录入快递单号」那一步必须填上（上门提货除外）" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="快递单号"><el-input v-model="form.logisticsNo" placeholder="选填，业务员在「录入快递单号」那一步必须填上（上门提货除外）" /></el-form-item></el-col>
          <el-col :span="8"><div class="hint form-hint">
            本批次实付的运费，方向与订单上向客户收的运费相反。付给承运商的钱按本位币
            {{ currencyLabel(BASE_CURRENCY) }} 记，不跟订单币种走。
          </div></el-col>
        </el-row>

        <div class="section-title">发货明细</div>
        <el-table :data="form.items" border stripe>
          <el-table-column prop="goodsName" label="商品" min-width="180" />
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="qty" label="订单数量" width="100" align="right" />
          <el-table-column prop="shippedQty" label="已发" width="90" align="right" />
          <!-- 可发量 = 订单数量 - 已发 - 其他未发货批次已排的量，由后端 /shippable 现算。
               只看已发量会让同一批货被排进两张批次。 -->
          <el-table-column label="可发数量" width="100" align="right">
            <template #default="scope">{{ Number(scope.row.remainingQty || 0) }}</template>
          </el-table-column>
          <el-table-column label="本批次发货" width="190">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.shipQty" :min="0" :max="maxShip(scope.row)" style="width:100%" />
            </template>
          </el-table-column>
        </el-table>
        <div class="hint mt8">填 0 的行不会进入批次。批次只是排产计划，建单不动库存预占。</div>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '保存批次' }}</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 打印备货单 ===================== -->
    <!--
      单据用原生 table 画，不用 el-table：这块 DOM 要整段 innerHTML 搬进打印用的 iframe，
      组件渲染出来的结构带一堆 scoped 属性和外部样式，搬过去只剩没样式的裸表格。
    -->
    <el-dialog v-model="printOpen" :title="reprint ? '补打备货单' : '打印备货单'" width="800px" :close-on-click-modal="false">
      <el-alert v-if="reprint" type="info" :closable="false" class="mb8"
        title="补打不改状态"
        description="这张批次已经是「已打印备货单」，补打只是再出一次纸——上一次在浏览器打印窗口点了取消、单子卡纸或者弄丢了，都从这里重新打。" />
      <el-alert v-else type="info" :closable="false" class="mb8"
        title="打完备货单这张批次就归你了"
        description="点「打印并标记」后弹出浏览器打印窗口，同时批次状态变成「已打印备货单」，操作按钮换成「上传」。这一步不动库存。真没打出来也不要紧，那一行上会留一个「补打」入口。" />
      <div class="ship-sheet" ref="printSheet">
        <h2>销售备货单</h2>
        <table class="sheet-meta">
          <tr>
            <td class="label">批次号</td><td>{{ printForm.shipCode }}</td>
            <td class="label">订单号</td><td>{{ printForm.orderCode }}</td>
          </tr>
          <tr>
            <td class="label">发货仓库</td><td>{{ printForm.warehouseName }}</td>
            <td class="label">计划发货</td><td>{{ parseTime(printForm.shipDate, '{y}-{m}-{d}') }}</td>
          </tr>
          <tr>
            <td class="label">承运商</td><td>{{ printForm.carrier || '—' }}</td>
            <td class="label">快递单号</td><td>{{ printForm.logisticsNo || '—' }}</td>
          </tr>
        </table>
        <table class="sheet-goods">
          <thead>
            <tr><th>#</th><th>商品</th><th>规格</th><th>单位</th><th class="num">数量</th><th>订单明细</th></tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in printForm.items" :key="item.detailCode">
              <td>{{ index + 1 }}</td>
              <td>{{ item.goodsName }}</td>
              <td>{{ item.spec || '—' }}</td>
              <td>{{ item.unit || '—' }}</td>
              <td class="num">{{ Number(item.qty) }}</td>
              <td>{{ item.orderDetailCode }}</td>
            </tr>
          </tbody>
        </table>
        <div class="sheet-sign">
          <span>拣货人：____________</span>
          <span>复核人：____________</span>
          <span>打印时间：{{ printedAt }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="printOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doPrint">{{ reprint ? '补打' : '打印并标记' }}</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 上传备货照片 / 备货完成 ===================== -->
    <el-dialog v-model="uploadOpen" :title="photoOnly ? '补传备货照片' : '上传备货照片'" width="720px" destroy-on-close :close-on-click-modal="false">
      <el-alert v-if="photoOnly" type="info" :closable="false" class="mb8"
        title="补传不改状态"
        description="这张批次已经是「备货完成」，这里只是把漏拍的照片补上、或者换掉拍糊的那张。业务员看到的就是这组照片，所以至少要留一张。等他录完快递单号，照片就锁住不能再改了。" />
      <el-alert v-else type="info" :closable="false" class="mb8"
        title="点「备货完成」之后，这张批次就交回给业务员了"
        description="照片至少传一张——备货照片、产品照片都行，不分类别，业务员在录快递单号之前只能靠它确认货备成什么样。传完点「备货完成」，批次推到「备货完成」，这里的按钮就只剩补传照片了，剩下的等业务员录完快递单号再回来打面单。这一步不动库存。" />
      <el-form label-width="110px">
        <el-form-item label="批次号">{{ uploadForm.shipCode }}</el-form-item>
        <el-form-item label="订单号">{{ uploadForm.orderCode }}</el-form-item>
        <el-form-item label="发货仓库">{{ uploadForm.warehouseName }}</el-form-item>
        <el-form-item label="备货照片" required>
          <!--
            备货照片和产品照片业务上是同一件事——「货备成什么样」，所以一个上传框收完，
            后端也只有 pickphotos 一列。硬拆两栏只会逼库管员纠结某张该传进哪一栏。
          -->
          <image-upload v-model="uploadForm.photos" :limit="9" :file-size="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doUpload">{{ photoOnly ? '保存照片' : '备货完成' }}</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 录入快递单号 ===================== -->
    <el-dialog v-model="logisticsOpen" title="录入快递单号" width="860px" destroy-on-close :close-on-click-modal="false">
      <el-alert type="info" :closable="false" class="mb8"
        title="这一步不扣库存，货还在仓库里"
        description="填好快递单号和承运商，并按仓库实际备好的货把每行的本次发货数量敲定下来（只能往下调，填 0 表示这一行本次不发）。提交后批次推到「已录入快递单号」，仓库那边会多出「打印快递单号」按钮。库存要等仓库点「完成」（＝承运商把货拉走）才扣。" />

      <el-form label-width="110px">
        <el-form-item label="批次号">{{ logisticsForm.shipCode }}</el-form-item>
        <el-form-item label="订单号">{{ logisticsForm.orderCode }}</el-form-item>
        <!--
          两项默认必填。这一步的语义就是「这批货交给谁、单号多少」，那时候一定知道；
          留空过去了，货出门之后查不到是谁拉走的，客户催货只能靠人回忆。
          建批次时填过的会由 openLogistics 原样带出来，不用重打。

          「上门提货」的订单是唯一的例外，见 logisticsNeeded。
        -->
        <el-form-item v-if="!logisticsNeeded" label="收货方式">
          <el-tag size="small" type="warning">{{ logisticsForm.receiveType }}</el-tag>
          <span class="hint ml5">客户自己来仓库拉，没有承运商和面单，下面两项可以不填</span>
        </el-form-item>
        <el-form-item label="快递单号" :required="logisticsNeeded">
          <el-input v-model="logisticsForm.logisticsNo" :placeholder="logisticsNeeded ? '必填，箱子上贴的那个单号' : '选填，上门提货一般没有单号'" />
        </el-form-item>
        <el-form-item label="承运商" :required="logisticsNeeded">
          <el-input v-model="logisticsForm.carrier" :placeholder="logisticsNeeded ? '必填，货交给谁拉走' : '选填，上门提货一般没有承运商'" />
        </el-form-item>
      </el-form>

      <!--
        仓库刚传的备货照片摆在数量上面：业务员定「这次到底发多少」的依据就是它，
        埋在详情弹窗里没人会专门去点。
      -->
      <template v-if="logisticsForm.pickPhotos">
        <div class="section-title">仓库传的备货照片</div>
        <image-upload v-model="logisticsForm.pickPhotos" disabled :limit="9" :is-show-tip="false" />
      </template>

      <div class="section-title">本次发货数量</div>
      <el-table :data="logisticsForm.items" v-loading="logisticsLoading" border size="small" max-height="260">
        <el-table-column prop="goodsName" label="商品" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="plannedQty" label="批次计划" width="90" align="right" />
        <el-table-column label="本次发货" width="170">
          <!-- 上限就是计划量：往上加等于绕过建批次时那道可发量校验，而这时候货已经按
               计划量备好了，多出来的那部分根本没有货。后端 resolveShipQty 同样这么挡 -->
          <template #default="scope">
            <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.shipQty"
              :min="0" :max="Number(scope.row.plannedQty || 0)" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column v-if="canSeeSale" label="单价" width="110" align="right">
          <template #default="scope">{{ formatCurrency(scope.row.price, logisticsForm.currency) }}</template>
        </el-table-column>
        <el-table-column v-if="canSeeSale" label="小计" width="120" align="right">
          <template #default="scope">{{ formatCurrency(lineAmount(scope.row), logisticsForm.currency) }}</template>
        </el-table-column>
      </el-table>

      <!--
        付款额度。后端提交时会用同一套口径再算一次并拦下来，这里提前把账摊开，
        免得业务员靠「提交 → 报错 → 改数量」试出来能发多少。
        金额按岗位可见：没有 price:sale 的人拿到的是 null，整块不显示（canSeeSale）。
      -->
      <div v-if="canSeeSale" class="allowance mt8">
        <div class="allowance-row">
          <span>订单总价</span><b>{{ formatCurrency(logisticsForm.totalAmount, logisticsForm.currency) }}</b>
        </div>
        <div class="allowance-row">
          <span>客户已付</span><b>{{ formatCurrency(logisticsForm.paidAmount, logisticsForm.currency) }}</b>
        </div>
        <div class="allowance-row">
          <span>已发 / 在途占用</span><b>{{ formatCurrency(logisticsForm.committedAmount, logisticsForm.currency) }}</b>
        </div>
        <div class="allowance-row">
          <span>本批次还能发</span><b>{{ formatCurrency(logisticsForm.allowanceAmount, logisticsForm.currency) }}</b>
        </div>
        <div class="allowance-row total" :class="overAllowance ? 'bad' : 'ok'">
          <span>本次货值合计</span>
          <b>{{ formatCurrency(totalShipAmount, logisticsForm.currency) }}
            <template v-if="overAllowance">（超出 {{ formatCurrency(totalShipAmount - Number(logisticsForm.allowanceAmount || 0), logisticsForm.currency) }}）</template>
          </b>
        </div>
      </div>
      <div v-if="canSeeSale && overAllowance" class="hint mt8 over-hint">
        发货货值超过客户已付金额，提交会被拒。请调减数量，或等财务收到款再发。
      </div>

      <template #footer>
        <el-button @click="logisticsOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doLogistics">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 打印快递单号 ===================== -->
    <!-- 同样用原生 table，理由见上面的备货单 -->
    <el-dialog v-model="labelOpen" :title="relabel ? '补打快递单号' : '打印快递单号'" width="720px" :close-on-click-modal="false">
      <el-alert v-if="relabel" type="info" :closable="false" class="mb8"
        title="补打不改状态"
        description="这张批次已经是「已打印快递单号」，补打只是再出一次纸——面单撕坏了、贴错箱要重贴，都从这里重新打。" />
      <el-alert v-else type="info" :closable="false" class="mb8"
        title="打完面单就等承运商上门"
        description="点「打印并标记」后弹出浏览器打印窗口，同时批次状态变成「已打印快递单号」，操作按钮换成「完成」。这一步<b>仍然不动库存</b>，货还在仓库里。等承运商真把货拉走了再点「完成」。" />
      <div class="ship-sheet" ref="labelSheet">
        <h2>快递面单信息</h2>
        <table class="sheet-meta">
          <tr>
            <td class="label">快递单号</td><td class="big">{{ labelForm.logisticsNo || '—' }}</td>
          </tr>
          <tr>
            <td class="label">承运商</td><td>{{ labelForm.carrier || '—' }}</td>
          </tr>
          <tr>
            <td class="label">批次号</td><td>{{ labelForm.shipCode }}</td>
          </tr>
          <tr>
            <td class="label">订单号</td><td>{{ labelForm.orderCode }}</td>
          </tr>
          <tr>
            <td class="label">发货仓库</td><td>{{ labelForm.warehouseName }}</td>
          </tr>
        </table>
        <table class="sheet-goods">
          <thead>
            <tr><th>#</th><th>商品</th><th>规格</th><th>单位</th><th class="num">本次发货</th></tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in labelForm.items" :key="item.detailCode">
              <td>{{ index + 1 }}</td>
              <td>{{ item.goodsName }}</td>
              <td>{{ item.spec || '—' }}</td>
              <td>{{ item.unit || '—' }}</td>
              <td class="num">{{ Number(item.qty) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="sheet-sign">
          <span>装箱人：____________</span>
          <span>承运商签收：____________</span>
          <span>打印时间：{{ labelPrintedAt }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="labelOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doLabel">{{ relabel ? '补打' : '打印并标记' }}</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 驳回 ===================== -->
    <el-dialog v-model="rejectOpen" title="驳回发货批次" width="600px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" class="mb8"
        title="批次会退回「待确认」，从这个列表上消失"
        description="驳回不动库存，也不动订单——到这一步为止这条链一次都没碰过库存。批次整个退回业务员手里，在「订单管理 → 发货批次」可以改数量、改仓库、改计划日期，也可以直接作废，改完重新确认再从打印备货单走一遍。确认人、备货人、录单人、发货人和实际发货时间会一并清空，<b>备货照片也会清掉</b>——货要重拣，那组照片拍的正是要被推翻的那一次备货，留着只会误导人。承运商和快递单号保留，面单还能用就不用重录。" />
      <el-form label-width="110px">
        <el-form-item label="批次号">{{ rejectForm.shipCode }}</el-form-item>
        <el-form-item label="订单号">{{ rejectForm.orderCode }}</el-form-item>
        <el-form-item label="当前状态"><el-tag size="small" :type="statusType(rejectForm.status)">{{ rejectForm.status }}</el-tag></el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="必填，写清楚为什么退回去——业务员拿到的就是这句话，比如「3 号箱装成了同规格的另一款，需要改数量重排」" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectOpen = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 完成 ===================== -->
    <el-dialog v-model="completeOpen" title="完成发货" width="720px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" class="mb8"
        title="确认承运商已经把货取走了吗？这一步会真正扣减库存"
        description="开销售出库单 → 同事务过账 → 回写订单已发量，三步一起完成。这是整条链上唯一不可逆的一格：完成之后这张批次就结了，驳回也用不了，要退只能走销售退货流程。货还在仓库里就别点，发现装错了请点「驳回」。" />
      <el-form label-width="110px">
        <el-form-item label="批次号">{{ completeForm.shipCode }}</el-form-item>
        <el-form-item label="订单号">{{ completeForm.orderCode }}</el-form-item>
        <el-form-item label="发货仓库">{{ completeForm.warehouseName }}</el-form-item>
        <el-form-item label="快递单号">{{ completeForm.logisticsNo || '未填写' }}</el-form-item>
      </el-form>

      <!--
        序列号 / 出库批次到真正出库这一刻才定得下来，所以录在这里而不是发货那一步：
        出库单是在「完成」时才开的，缺了它过不了账。不需要跟踪的商品一行都不会出现。
      -->
      <template v-if="trackingItems.length">
        <div class="section-title">发哪几件</div>
        <el-alert type="info" :closable="false" class="mb8"
          title="这些商品要指定发出去的是哪几件"
          description="序列号管理的商品必须选够与发货数量相同的个数；个别计价的批次管理商品要指定出库批次。可选项来自本批次发货仓库的「良品·在库」，选不到说明货还没入库或已经被别的单据出掉了。" />
        <el-table :data="trackingItems" v-loading="trackingLoading" border size="small" max-height="260">
          <el-table-column prop="goodsName" label="商品" min-width="150" :show-overflow-tooltip="true" />
          <el-table-column prop="qty" label="发货数量" width="90" align="right" />
          <el-table-column label="序列号 / 出库批次" min-width="300">
            <template #default="scope">
              <el-select v-if="scope.row.batchStrategy === 'serial'" v-model="scope.row.serialNumbers"
                multiple filterable clearable collapse-tags collapse-tags-tooltip
                :multiple-limit="serialCount(scope.row)" placeholder="选择序列号" style="width:100%">
                <el-option v-for="item in scope.row.serialOptions" :key="item.serialNo"
                  :label="item.batchCode ? `${item.serialNo}（批次 ${item.batchCode}）` : item.serialNo"
                  :value="item.serialNo" />
              </el-select>
              <el-select v-else v-model="scope.row.batchCode" filterable clearable
                placeholder="选择出库批次" style="width:100%">
                <el-option v-for="item in scope.row.batchOptions" :key="item.batchCode"
                  :label="`${item.batchCode}（余 ${item.remainQty}）`" :value="item.batchCode" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="已选" width="80" align="center">
            <template #default="scope">
              <span :class="trackingReady(scope.row) ? 'tracking-ok' : 'tracking-miss'">
                {{ scope.row.batchStrategy === 'serial'
                  ? `${(scope.row.serialNumbers || []).length}/${serialCount(scope.row)}`
                  : (scope.row.batchCode ? '已选' : '未选') }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="completeOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doComplete">确认完成并扣库存</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 详情 ===================== -->
    <el-dialog v-model="detailOpen" title="发货批次详情" width="1000px" :close-on-click-modal="false">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="批次号">{{ detail.shipCode }}</el-descriptions-item>
        <el-descriptions-item label="订单号">{{ detail.orderCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="计划发货日期">{{ parseTime(detail.shipDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="实际发货时间">{{ detail.actualShipTime || '未发货' }}</el-descriptions-item>
        <el-descriptions-item label="发货仓库">{{ detail.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="承运商">{{ detail.carrier || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="快递单号">{{ detail.logisticsNo || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="订单币种">{{ currencyLabel(detail.orderCurrency) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="本批次运费">{{ formatCurrency(detail.freightAmount, BASE_CURRENCY) }}</el-descriptions-item>
        <el-descriptions-item label="运费支付状态">{{ detail.freightPayStatus || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <!-- confirmuser/confirmtime 记的是「谁把批次提交给仓库的」，列名沿用旧的确认人 -->
        <el-descriptions-item label="提交仓库人">{{ detail.confirmUser || '未提交' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.confirmTime || '—' }}</el-descriptions-item>
        <!-- 这条链上每换一次责任人就留一组经手人，出了岔子不用靠人回忆是谁干的。
             两个打印动作没有单独的经手人列，那两笔在 sys_oper_log 里 -->
        <el-descriptions-item label="备货完成人">{{ detail.pickUser || '未备货' }}</el-descriptions-item>
        <el-descriptions-item label="备货完成时间">{{ detail.pickTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="录快递单号人">{{ detail.logisticsUser || '未录入' }}</el-descriptions-item>
        <el-descriptions-item label="录入时间">{{ detail.logisticsTime || '—' }}</el-descriptions-item>
        <!-- shipuser 记的是「谁点的完成」，那一刻货才真的离开仓库 -->
        <el-descriptions-item label="发货人">{{ detail.shipUser || '未发货' }}</el-descriptions-item>
        <!--
          驳回三列只在被驳回过的批次上显示。批次重走完流程后不清空，所以一张「已完成」的
          单子上也可能挂着驳回记录——那是历史，标题里写明「最近一次」免得读成当前状态。
        -->
        <template v-if="detail.rejectTime || detail.rejectReason">
          <el-descriptions-item label="最近驳回人">{{ detail.rejectUser || '—' }}</el-descriptions-item>
          <el-descriptions-item label="驳回时间">{{ detail.rejectTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="当前是否待处理">{{ wasRejected(detail) ? '是，等业务员改完重新确认' : '否，已重新走过流程' }}</el-descriptions-item>
          <el-descriptions-item label="驳回原因" :span="3">{{ detail.rejectReason || '—' }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '无' }}</el-descriptions-item>
      </el-descriptions>

      <!--
        备货照片。两边都看得到：仓库要核对自己传的是哪一组，业务员录单前要看货对不对，
        客户来问「我的货备好没」时这也是唯一能拿出来的东西。
        用 disabled 的 ImageUpload 而不是 ImagePreview：后者只渲染第一张，
        前者按逗号拆开把每张都摆出来，点开还能翻。
      -->
      <template v-if="detail.pickPhotos">
        <div class="section-title mt20">备货照片</div>
        <image-upload v-model="detail.pickPhotos" disabled :limit="9" :is-show-tip="false" />
      </template>

      <el-table :data="detail.items" class="mt20">
        <el-table-column prop="goodsName" label="商品" min-width="170" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="qty" label="本批次发货" width="120" align="right" />
        <!-- 这里是「实发来源」，订单明细上的同名字段是「备货来源」，两者可能不同：
             订单原本打算走采购补货，发货时现货够了，实发来源就是现货 -->
        <el-table-column prop="stockSource" label="实发来源" width="100">
          <template #default="scope"><el-tag size="small" :type="sourceType(scope.row.stockSource)">{{ scope.row.stockSource }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="sourceCode" label="来源单号" min-width="150">
          <template #default="scope">{{ scope.row.sourceCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="orderDetailCode" label="订单明细" min-width="160" />
      </el-table>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="ShipBatch">
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listBatch, getBatch, addBatch, updateBatch, confirmBatch,
  printBatch, completePicking, savePickPhotos, getLogisticsInfo, enterLogistics, printLabel,
  rejectBatch, completeBatch, cancelBatch
} from '@/api/order/shipbatch'
import { listShippableOrders } from '@/api/order/sales'
import { listWarehouse } from '@/api/inventory/warehouse'
// 序列号与批次的可选项都来自库存模块，这两个接口只要求有商家上下文，不额外要库存权限
import { listInStockSerial } from '@/api/inventory/serial'
import { listAvailableBatch } from '@/api/inventory/batch'
import { warehouseLabel } from '@/utils/warehouse'
// parseTime 只挂在 globalProperties 上，script 里必须显式引入。
// 取「今天」用它而不是 toISOString()：后者返回 UTC，GMT+8 的凌晨会取到前一天，
// 而计划发货日期决定本批次落哪张月表，差一天就落错表了。
import { parseTime } from '@/utils/ruoyi'
import { DEFAULT_CURRENCY, currencyLabel, formatCurrency } from '@/utils/currency'
import DecimalInput from '@/components/DecimalInput'
import { canSeeCost, canSeeSale } from '@/utils/price'

/*
  「库存管理 → 仓库发货管理」复用本页面，传 warehouse-mode 切成仓库视角。

  两边的分工按状态切开，球来回传四次：
    订单侧  待确认                                  → 修改 / 确认（＝提交给仓库）
            备货完成                                → 录入快递单号（填单号+敲定实发数量）
            待确认 / 已提交仓库 / 已打印备货单 / 备货完成 → 取消（作废整张批次）
    仓库侧  已提交仓库                              → 打印备货单
            已打印备货单                            → 上传（传备货照片 + 备货完成）/ 补打
            备货完成                                → 补传照片（球在业务员那儿，只剩这个）
            已录入快递单号                          → 打印快递单号
            已打印快递单号                          → 完成（开出库单、扣库存、回写已发量）/ 补打
            中间五格                                → 驳回（整个退回「待确认」，交还业务员）
  批次一提交，订单侧就改不了数量；没提交，仓库侧连行都看不到（列表带 warehouseView 过滤）。

  时间轴上只有一个硬分界：点「完成」＝承运商把货取走。在那之前货一直在仓库里，
  「已打印快递单号」也只是贴了面单在等人上门，所以中间五格全都能驳回。订单侧的取消停在
  「备货完成」不往后延，是因为录完单那一格该由仓库先驳回说明白为什么——责任方不同，
  见后端 cancelBatch 的注释。

  按钮该不该显示还叠了一层 v-hasPermi，而且这次权限串是按岗位拆开的：
    业务员  confirm（提交仓库）+ logistics（录入快递单号）+ add/edit/cancel
    仓库    print（两个打印）  + picking（备货完成）      + ship（完成 / 驳回）
  拆之前仓库的「打单」沿用的是业务员「提交仓库」那一串，后端分不出谁该点哪个按钮；
  现在这条链来回交接四次，再共用就说不清责任了。驳回沿用 ship：能点完成把货放出门的人，
  就该能把批次拉回来，不为它单开权限串。
*/
const props = defineProps({
  warehouseMode: { type: Boolean, default: false }
})

/**
 * 运费的记账币种。本批次运费是<b>付给承运商</b>的钱，与订单卖给客户用什么币种无关，
 * 所以一律按本位币显示，不跟 detail.orderCurrency 走。带出订单币种只是让操作人
 * 知道这张批次服务的是哪种币的订单。
 */
const BASE_CURRENCY = DEFAULT_CURRENCY

const ALL_STATES = ['待确认', '已提交仓库', '已打印备货单', '备货完成',
  '已录入快递单号', '已打印快递单号', '已完成', '已取消']
// 仓库看不到「待确认」和「已取消」，筛选项也别给——选了永远是空列表
const states = computed(() => props.warehouseMode ? ALL_STATES.slice(1, 7) : ALL_STATES)

const loading = ref(false), saving = ref(false), showSearch = ref(true)
const createOpen = ref(false), detailOpen = ref(false), editing = ref(false)
const printOpen = ref(false), rejectOpen = ref(false), completeOpen = ref(false)
const uploadOpen = ref(false), logisticsOpen = ref(false), labelOpen = ref(false)
const rows = ref([]), detail = ref({ items: [] }), warehouses = ref([]), shippableOrders = ref([])
const month = ref(parseTime(new Date(), '{y}-{m}'))
const orderMonth = ref(parseTime(new Date(), '{y}-{m}'))
const query = reactive({ shipCode: undefined, orderCode: undefined, status: undefined, warehouseCode: undefined })
const form = ref({ items: [], freightAmount: 0 })
const printSheet = ref(null), printedAt = ref('')
const printForm = reactive({ shipCode: '', orderCode: '', warehouseName: '', tableSuffix: '', shipDate: '', carrier: '', logisticsNo: '', status: '', items: [] })
/** 这次是补打（批次已经在「已打印备货单」上），只出纸不改状态 */
const reprint = computed(() => printForm.status === '已打印备货单')

// ---------- 上传备货照片 / 备货完成 ----------
const uploadForm = reactive({ shipCode: '', orderCode: '', warehouseName: '', tableSuffix: '', status: '', photos: '' })
/** 这次只是补传照片（批次已经在「备货完成」上），不改状态 */
const photoOnly = computed(() => uploadForm.status === '备货完成')

// ---------- 录入快递单号 ----------
const logisticsLoading = ref(false)
const logisticsForm = reactive({
  shipCode: '', orderCode: '', tableSuffix: '', receiveType: '', currency: '',
  carrier: '', logisticsNo: '', pickPhotos: '',
  totalAmount: 0, paidAmount: 0, committedAmount: 0, allowanceAmount: 0, items: []
})
/**
 * 这张批次要不要求填承运商和快递单号。
 *
 * 「上门提货」的订单是客户自己开车来仓库拉，既没有承运商也没有面单，硬要求只会逼人往框里
 * 敲「无」——假数据比空值更糟，填过之后就分不清「本来就没有」和「忘了填」。其余两种收货
 * 方式（货代地址／客户地址）都得有。判据与后端 enterLogistics 一致，两边改要一起改。
 *
 * 收货方式拿不到时（接口查失败）落进「要填」这一支：拿不准的时候按要填算。
 */
const PICKUP_RECEIVE_TYPE = '上门提货'
const logisticsNeeded = computed(() => logisticsForm.receiveType !== PICKUP_RECEIVE_TYPE)
const lineAmount = row => Number(row.shipQty || 0) * Number(row.price || 0)
const totalShipAmount = computed(() => logisticsForm.items.reduce((sum, row) => sum + lineAmount(row), 0))
/**
 * 本次货值有没有超过额度。
 *
 * 没有 price:sale 权限的人拿到的 allowanceAmount 是 null（后端按岗位抹掉了），
 * 那样恒为 false——整块额度提示本来就不给他看，提交时后端照样会拦。
 */
const overAllowance = computed(() =>
  canSeeSale.value && logisticsForm.allowanceAmount !== null && logisticsForm.allowanceAmount !== undefined
  && totalShipAmount.value > Number(logisticsForm.allowanceAmount) + 1e-9)

// ---------- 打印快递单号 ----------
const labelSheet = ref(null), labelPrintedAt = ref('')
const labelForm = reactive({ shipCode: '', orderCode: '', warehouseName: '', tableSuffix: '', carrier: '', logisticsNo: '', status: '', items: [] })
/** 这次是补打面单（批次已经在「已打印快递单号」上），只出纸不改状态 */
const relabel = computed(() => labelForm.status === '已打印快递单号')

const rejectForm = reactive({ shipCode: '', orderCode: '', tableSuffix: '', status: '', reason: '' })
/** 货还没被承运商取走的五格都能驳回，与后端 REJECTABLE_STATES 一致 */
const REJECTABLE_STATES = ['已提交仓库', '已打印备货单', '备货完成', '已录入快递单号', '已打印快递单号']
const canReject = row => REJECTABLE_STATES.includes(row.status)
/**
 * 这张批次是不是「被驳回后还没重新提交」。
 *
 * 驳回三列在批次重走流程后不清空（留作痕迹），所以光看 rejectTime 有值会把早就重新
 * 发完的单子也标成被驳回。加一个「当前还停在待确认」的条件才是当下的状态。
 */
const wasRejected = row => !!row && !!row.rejectTime && row.status === '待确认'
const rejectHint = row => `${row.rejectUser || '仓库'} 于 ${row.rejectTime} 驳回：${row.rejectReason || '未填写原因'}`
const completeForm = reactive({ shipCode: '', orderCode: '', warehouseName: '', tableSuffix: '', logisticsNo: '' })
/*
  本批次里需要在完成时指定「发哪几件」的行。

  销售出库单过账时，序列号管理的商品必须逐个给序列号，个别计价的批次管理商品必须给出库批次
  （OutboundServiceImpl.validateTrackingData）。FIFO 和加权平均由出库单自己按入库时间挑，
  不用人管，所以这两类不进这张表。
*/
const trackingItems = ref([]), trackingLoading = ref(false)

/** 这一行出库时要不要人来指定发哪几件 */
const needsTracking = item => item.batchStrategy === 'serial'
  || (item.batchStrategy === 'batch' && item.costCalculation === 'individual')
/** 序列号商品要选的个数＝发货数量，序列号只能整件发 */
const serialCount = row => Math.floor(Number(row.qty || 0))
const trackingReady = row => row.batchStrategy === 'serial'
  ? (row.serialNumbers || []).length === serialCount(row)
  : !!row.batchCode

const suffix = () => month.value.replace('-', '')

function loadList() {
  loading.value = true
  // warehouseView 由后端加 status in (...) 过滤，不在前端筛：分页是后端做的，
  // 前端过滤会让每页少几行，页码和总数全对不上
  listBatch({ ...query, tableSuffix: suffix(), warehouseView: props.warehouseMode || undefined })
    .then(r => rows.value = r.rows || [])
    .finally(() => loading.value = false)
}
function reset() {
  query.shipCode = undefined; query.orderCode = undefined
  query.status = undefined; query.warehouseCode = undefined
  loadList()
}

const isPending = row => row.status === '待确认'
/**
 * 还能不能作废。「已完成」的货已经被承运商取走、库存也扣了，撤不回来，只能走退货流程
 * ——后端 cancelBatch 同样这么挡。
 *
 * 「已提交仓库 / 已打印备货单 / 备货完成」也放行是必需的：订单要关闭、取消或撤审，
 * 后端要求先把未完成的批次清掉（assertNoPendingBatches），只留「待确认」会让
 * 「批次已提交、仓库还没发货、客户临时要取消」变成批次和订单互相卡死的死局。
 *
 * 「已录入快递单号」往后不在里面，不是因为撤不回来（那时候货还在仓库里），而是那两格
 * 该由仓库先点「驳回」——单子上带的是业务员自己填的面单信息、仓库正按它打单贴箱，
 * 订单侧一键作废的话仓库只会看见行凭空消失，也没人写下为什么。
 * 驳回退回「待确认」之后这里照样能取消，死局并没有回来。
 */
const canCancel = row => ['待确认', '已提交仓库', '已打印备货单', '备货完成'].includes(row.status)

/*
  状态配色按「球在谁脚下」分：
    info    没人在动（待确认 / 已完成前的终态另算）
    primary 球在仓库脚下，等仓库操作
    warning 球在业务员脚下，等业务员操作
    success 终态，走完了
*/
function statusType(status) {
  return {
    待确认: 'info', 已提交仓库: 'primary', 已打印备货单: 'primary',
    备货完成: 'warning', 已录入快递单号: 'primary', 已打印快递单号: 'primary',
    已完成: 'success', 已取消: 'danger'
  }[status] || 'info'
}
function sourceType(source) {
  return { 现货: 'success', 采购: 'warning', 生产: 'primary' }[source] || 'info'
}
function orderLabel(order) {
  return order.orderCode + ' / ' + (order.shopStoreName || order.shopStoreCode) + ' / ' + order.receivePerson
    + ' / ' + currencyLabel(order.currency)
}
/**
 * 本行最多能发多少。改单时要把本批次自己已排的量加回来，
 * 否则一张已排 3 件的批次重新打开，可发数量会变成 0，改都改不了。
 */
function maxShip(row) {
  return Number(row.remainingQty || 0) + Number(row.ownQty || 0)
}

// ---------- 建单 / 改单 ----------
/** 月表后缀 202608 → 日期选择器要的 2026-08；拿不到就返回空，由调用方兜底 */
function monthOf(tableSuffix) {
  const value = String(tableSuffix || '')
  return value.length === 6 ? value.slice(0, 4) + '-' + value.slice(4) : ''
}

function loadShippableOrders() {
  return listShippableOrders(orderMonth.value.replace('-', ''))
    .then(r => shippableOrders.value = r.data || [])
}

function openCreate() {
  editing.value = false
  orderMonth.value = parseTime(new Date(), '{y}-{m}')
  form.value = {
    orderCode: undefined,
    shipDate: parseTime(new Date(), '{y}-{m}-{d}'),
    warehouseCode: warehouses.value.length === 1 ? warehouses.value[0].wareCode : '',
    carrier: '', logisticsNo: '', freightAmount: 0, remark: '', items: []
  }
  loadShippableOrders().then(() => createOpen.value = true)
}

function onOrderChange(orderCode) {
  const order = shippableOrders.value.find(o => o.orderCode === orderCode)
  // /shippable 已经把每条明细的 remainingQty 现算好带出来了，不用再请求一次。
  // 行来自订单明细，detailCode 就是订单明细代码——显式落到 orderDetailCode，与 openEdit 两支对齐
  form.value.items = (order?.items || []).map(i => ({ ...i, orderDetailCode: i.detailCode, shipQty: 0 }))
}

function openEdit(row) {
  editing.value = true
  getBatch(row.shipCode, row.tableSuffix || suffix()).then(r => {
    const data = r.data || {}
    // 改单要拿到订单的可发量，按<b>订单所在月份</b>把可发货订单捞出来。
    // 不能用当前月，也不能用 data.tableSuffix——后者是批次按计划发货日期落的月，
    // 月底下单、次月发货时和订单的月份不是同一个，查错月份会让每行可发量变成 0，
    // 界面上表现为数量只能减不能加。orderTableSuffix 由后端读详情时从订单带出来。
    orderMonth.value = monthOf(data.orderTableSuffix) || parseTime(new Date(), '{y}-{m}')
    return loadShippableOrders().then(() => {
      const order = shippableOrders.value.find(o => o.orderCode === data.orderCode)
      const stored = new Map((data.items || []).map(i => [i.orderDetailCode, i]))
      form.value = {
        ...data,
        shipDate: data.shipDate ? String(data.shipDate).slice(0, 10) : '',
        freightAmount: Number(data.freightAmount || 0),
        // 这一支的行来自<b>订单明细</b>，它的 detailCode 就是订单明细代码。
        // 显式落到 orderDetailCode 上，save() 只认这个字段——下面那一支的行来自批次明细，
        // 那边的 detailCode 是批次明细自己的代码（XSFHMX…），两支混用一个字段名必然取错。
        items: (order?.items || []).map(i => {
          const hit = stored.get(i.detailCode)
          // ownQty：本批次自己已排的量，也算在别人的占用里，改单时要加回上限
          const own = hit ? Number(hit.qty) : 0
          return { ...i, orderDetailCode: i.detailCode, shipQty: own, ownQty: own }
        })
      }
      // 订单剩余量已经全部排进批次（或已发完），不在可发货列表里时，至少把已存的行显示出来，
      // 别让页面空着。可发量给 0，只能往下调——这是对的，本来就没有多余的量可加。
      if (!order) {
        form.value.items = (data.items || []).map(i => ({
          ...i, goodsName: i.goodsName, shipQty: Number(i.qty), ownQty: Number(i.qty), remainingQty: 0
        }))
      }
      createOpen.value = true
    })
  })
}

function save() {
  if (!form.value.orderCode) { ElMessage.warning('请选择销售订单'); return }
  if (!form.value.shipDate) { ElMessage.warning('请填写计划发货日期'); return }
  if (!form.value.warehouseCode) { ElMessage.warning('请选择发货仓库'); return }
  const picked = form.value.items.filter(i => Number(i.shipQty) > 0)
  if (!picked.length) { ElMessage.warning('请至少填写一行发货数量'); return }

  // 只认 orderDetailCode。原先写的是 `i.detailCode || i.orderDetailCode`：
  // 建单和「订单还在可发货列表里」的改单，行来自订单明细，detailCode 正好是订单明细代码，所以没露馅；
  // 而订单剩余量已被批次排满时走的是 openEdit 的兜底分支，行来自批次明细，
  // 它的 detailCode 是批次明细自己的代码，|| 短路后就把 XSFHMX… 当订单明细发上去，
  // 后端报「订单明细'XSFHMX…'不存在」。两支现在都保证带着 orderDetailCode。
  const items = picked.map(i => ({ orderDetailCode: i.orderDetailCode, qty: i.shipQty }))
  saving.value = true
  const action = editing.value
    ? updateBatch({ ...form.value, items }, form.value.tableSuffix || suffix()).then(() => ElMessage.success('批次已修改'))
    : addBatch({ ...form.value, items }).then(r => {
        // 批次按计划发货日期落表，跳到对应月份才看得到刚建的单
        month.value = String(form.value.shipDate).slice(0, 7)
        ElMessage.success(r.msg || '批次已创建')
      })
  action.then(() => { createOpen.value = false; loadList() }).finally(() => saving.value = false)
}

// ---------- 状态流转 ----------
function confirm(row) {
  ElMessageBox.confirm('确认后这张批次提交给仓库，订单这边就不能再改数量了——真要调，等仓库备完货，在「录入快递单号」那一步按实际备好的货往下调。仓库备好货之前仍然可以作废整张批次。是否继续？', '提示', { closeOnClickModal: false })
    .then(() => confirmBatch(row.shipCode, row.tableSuffix || suffix()))
    .then(() => { ElMessage.success('批次已提交仓库'); loadList() })
    .catch(() => {})
}

// ---------- 打印备货单 ----------
function openPrint(row) {
  getBatch(row.shipCode, row.tableSuffix || suffix()).then(r => {
    const data = r.data || {}
    Object.assign(printForm, {
      shipCode: data.shipCode || row.shipCode,
      orderCode: data.orderCode || row.orderCode,
      warehouseName: data.warehouseName || row.warehouseName,
      tableSuffix: row.tableSuffix || suffix(),
      shipDate: data.shipDate || row.shipDate,
      carrier: data.carrier || '',
      logisticsNo: data.logisticsNo || '',
      status: data.status || row.status,
      items: data.items || []
    })
    printedAt.value = parseTime(new Date(), '{y}-{m}-{d} {h}:{i}:{s}')
    printOpen.value = true
  })
}

/**
 * 单据打印用的样式。要跟着 innerHTML 一起进 iframe，所以只能写成字符串常量。
 * 备货单和快递面单共用这一份——两张纸的结构一样（抬头表 + 商品表 + 签字栏）。
 */
const PRINT_CSS = `
  body { font-family: "Microsoft YaHei", sans-serif; color: #000; margin: 24px; }
  h2 { text-align: center; margin: 0 0 16px; font-size: 20px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 14px; }
  td, th { border: 1px solid #333; padding: 6px 8px; font-size: 13px; }
  .label { width: 90px; background: #f2f2f2; }
  .num { text-align: right; }
  /* 快递单号印大一点：仓库贴箱、承运商核对都靠扫这一行 */
  .big { font-size: 22px; font-weight: 700; letter-spacing: 1px; }
  .sheet-sign { display: flex; justify-content: space-between; font-size: 13px; margin-top: 24px; }
`

/**
 * 把单据搬进隐藏 iframe 打印。
 *
 * 不用 window.open：弹窗会被浏览器拦（这里已经隔了一次异步请求，不再算「用户手势触发」）；
 * 也不用 @media print 改整页——那样得把页面其余部分全部隐藏，弹窗遮罩、固定表头都要单独处理。
 *
 * @param title 打印任务的名字，多数浏览器拿它当默认文件名。备货单传批次号，
 *              面单传快递单号——打成 PDF 存档时一眼能认出是哪张。
 */
function renderToPrinter(sheet, title) {
  const frame = document.createElement('iframe')
  frame.setAttribute('style', 'position:fixed;right:0;bottom:0;width:0;height:0;border:0')
  document.body.appendChild(frame)
  const doc = frame.contentWindow.document
  doc.open()
  doc.write('<!DOCTYPE html><html><head><meta charset="utf-8"><title>'
    + (title || '') + '</title><style>' + PRINT_CSS + '</style></head><body>'
    + sheet.innerHTML + '</body></html>')
  doc.close()
  frame.contentWindow.focus()
  frame.contentWindow.print()
  // print() 在部分浏览器里是异步的，立刻移除 iframe 会让打印内容变空白
  setTimeout(() => document.body.removeChild(frame), 1000)
}

/**
 * 补打只出纸；首次打印先改状态再出纸——接口失败就不该弹打印窗口，否则单子打出来了
 * 状态还停在「已提交仓库」，仓库拿着单子在系统里找不到对应的「上传」按钮。
 *
 * <b>反过来那一半救不了：</b>浏览器打印窗口点「取消」我们这边收不到（onafterprint
 * 打印和取消都会触发，分不出来），状态照样已经推到了「已打印备货单」。所以那一行上
 * 留了「补打」入口，纸没出来就再打一次，不必也不能把状态退回去。
 */
function doPrint() {
  const sheet = printSheet.value
  if (!sheet) return
  if (reprint.value) {
    renderToPrinter(sheet, printForm.shipCode)
    printOpen.value = false
    return
  }
  saving.value = true
  printBatch(printForm.shipCode, printForm.tableSuffix)
    .then(() => {
      renderToPrinter(sheet, printForm.shipCode)
      ElMessage.success('已标记为已打印备货单，没打出来可以点那一行的「补打」')
      printOpen.value = false
      loadList()
    })
    .finally(() => saving.value = false)
}

// ---------- 上传备货照片 / 备货完成 ----------
/**
 * 打开上传弹窗。要回查一次详情拿已传的照片：
 * 「已打印备货单」上可能上次传了一半没点完成，「备货完成」上则是回来补传的，
 * 两种情况都得把已有的照片摆出来，否则 ImageUpload 是空的，一保存就把原来的冲掉了。
 */
function openUpload(row) {
  uploadForm.shipCode = row.shipCode
  uploadForm.orderCode = row.orderCode
  uploadForm.warehouseName = row.warehouseName
  uploadForm.tableSuffix = row.tableSuffix || suffix()
  uploadForm.status = row.status
  uploadForm.photos = ''
  uploadOpen.value = true
  getBatch(row.shipCode, uploadForm.tableSuffix)
    .then(r => uploadForm.photos = (r.data || {}).pickPhotos || '')
    .catch(() => uploadForm.photos = '')
}

function doUpload() {
  // 后端也拦，但那时弹窗已经关不掉又弹个红条，不如在这里说清楚
  if (!uploadForm.photos) { ElMessage.warning('请至少上传一张备货照片'); return }
  saving.value = true
  const body = { photos: uploadForm.photos, tableSuffix: uploadForm.tableSuffix }
  const action = photoOnly.value
    ? savePickPhotos(uploadForm.shipCode, body).then(() => ElMessage.success('照片已保存'))
    : completePicking(uploadForm.shipCode, body)
        .then(() => ElMessage.success('已备货完成，等业务员录快递单号'))
  action.then(() => { uploadOpen.value = false; loadList() }).finally(() => saving.value = false)
}

// ---------- 录入快递单号 ----------
/**
 * 打开录单弹窗。走专用接口 /logisticsInfo 而不是批次详情：那里要现算「这张订单已经占掉
 * 多少发货额度」，得把发货批次的月表枚举一遍，比普通详情贵，所以只在开这个弹窗时付这笔钱。
 *
 * 备货照片一并带出来摆在数量上面——业务员定「这次到底发多少」的依据就是它。
 */
function openLogistics(row) {
  logisticsForm.shipCode = row.shipCode
  logisticsForm.orderCode = row.orderCode
  logisticsForm.tableSuffix = row.tableSuffix || suffix()
  logisticsForm.carrier = row.carrier || ''
  logisticsForm.logisticsNo = row.logisticsNo || ''
  logisticsForm.receiveType = ''
  logisticsForm.currency = ''
  logisticsForm.pickPhotos = ''
  logisticsForm.items = []
  logisticsOpen.value = true
  logisticsLoading.value = true

  // 照片在批次详情里，额度和单价在 /logisticsInfo 里，两边都要，并行取
  Promise.all([
    getLogisticsInfo(row.shipCode, logisticsForm.tableSuffix),
    getBatch(row.shipCode, logisticsForm.tableSuffix).catch(() => ({ data: {} }))
  ]).then(([info, batch]) => {
    const data = info.data || {}
    logisticsForm.receiveType = data.receiveType || ''
    logisticsForm.currency = data.currency || ''
    logisticsForm.totalAmount = data.totalAmount
    logisticsForm.paidAmount = data.paidAmount
    logisticsForm.committedAmount = data.committedAmount
    logisticsForm.allowanceAmount = data.allowanceAmount
    // 默认按批次原本排的计划量发，业务员只在仓库少备了货时才往下调
    logisticsForm.items = (data.items || []).map(i => ({ ...i, shipQty: Number(i.plannedQty || 0) }))
    logisticsForm.pickPhotos = (batch.data || {}).pickPhotos || ''
  }).finally(() => logisticsLoading.value = false)
}

function doLogistics() {
  // 后端也拦，但那时弹窗已经关不掉又弹个红条，不如在这里说清是哪一项
  if (logisticsNeeded.value) {
    if (!logisticsForm.logisticsNo.trim()) { ElMessage.warning('请填写快递单号'); return }
    if (!logisticsForm.carrier.trim()) { ElMessage.warning('请填写承运商'); return }
  }
  if (!logisticsForm.items.some(i => Number(i.shipQty) > 0)) {
    ElMessage.warning('每一行都填了 0，这张批次等于什么都不发。不发了请直接取消')
    return
  }
  // 额度只在看得见金额的岗位上提前拦；看不见的人由后端拦，报错里会把数字说清楚
  if (overAllowance.value) {
    ElMessage.warning('本次货值超过客户已付金额，请调减数量后再提交')
    return
  }
  const qtys = {}
  for (const item of logisticsForm.items) qtys[item.detailCode] = Number(item.shipQty || 0)

  saving.value = true
  // 上门提货且没填时传 undefined 而不是空串：后端把空白当「没带」，会保留批次上的原值
  enterLogistics(logisticsForm.shipCode, {
    logisticsNo: logisticsForm.logisticsNo.trim() || undefined,
    carrier: logisticsForm.carrier.trim() || undefined,
    qtys,
    tableSuffix: logisticsForm.tableSuffix
  }).then(() => {
    ElMessage.success('已录入快递单号，等仓库打面单并发货')
    logisticsOpen.value = false
    loadList()
  }).finally(() => saving.value = false)
}

// ---------- 打印快递单号 ----------
/**
 * 打开面单弹窗。要回查详情拿商品行——列表行上只有合计数量，面单上要逐行列出来，
 * 承运商核对箱数时按它数。
 */
function openLabel(row) {
  getBatch(row.shipCode, row.tableSuffix || suffix()).then(r => {
    const data = r.data || {}
    Object.assign(labelForm, {
      shipCode: data.shipCode || row.shipCode,
      orderCode: data.orderCode || row.orderCode,
      warehouseName: data.warehouseName || row.warehouseName,
      tableSuffix: row.tableSuffix || suffix(),
      carrier: data.carrier || '',
      logisticsNo: data.logisticsNo || '',
      status: data.status || row.status,
      items: data.items || []
    })
    labelPrintedAt.value = parseTime(new Date(), '{y}-{m}-{d} {h}:{i}:{s}')
    labelOpen.value = true
  })
}

/** 与 doPrint 同一个路子：补打只出纸，首打先改状态再出纸。理由见 doPrint 的注释 */
function doLabel() {
  const sheet = labelSheet.value
  if (!sheet) return
  if (relabel.value) {
    renderToPrinter(sheet, labelForm.logisticsNo || labelForm.shipCode)
    labelOpen.value = false
    return
  }
  saving.value = true
  printLabel(labelForm.shipCode, labelForm.tableSuffix)
    .then(() => {
      renderToPrinter(sheet, labelForm.logisticsNo || labelForm.shipCode)
      ElMessage.success('已标记为已打印快递单号，承运商取走货后点「完成」结单扣库存')
      labelOpen.value = false
      loadList()
    })
    .finally(() => saving.value = false)
}

// ---------- 驳回 ----------
function openReject(row) {
  rejectForm.shipCode = row.shipCode
  rejectForm.orderCode = row.orderCode
  rejectForm.tableSuffix = row.tableSuffix || suffix()
  rejectForm.status = row.status
  // 每次打开都清空：原因是给业务员看的，留着上一张单的话术只会误导人
  rejectForm.reason = ''
  rejectOpen.value = true
}

function doReject() {
  // 后端也拦（reason 必填），但那时弹窗已经关不掉又报个红条，不如在这里说清楚
  if (!rejectForm.reason.trim()) { ElMessage.warning('请填写驳回原因'); return }
  saving.value = true
  rejectBatch(rejectForm.shipCode, {
    reason: rejectForm.reason.trim(),
    tableSuffix: rejectForm.tableSuffix
  }).then(r => {
    ElMessage.success(r.msg || '已驳回')
    rejectOpen.value = false
    // 退成「待确认」后这张批次不在仓库视图的状态白名单里，刷新后本行会消失，这是对的
    loadList()
  }).finally(() => saving.value = false)
}

// ---------- 完成 ----------
function openComplete(row) {
  completeForm.shipCode = row.shipCode
  completeForm.orderCode = row.orderCode
  completeForm.warehouseName = row.warehouseName
  completeForm.tableSuffix = row.tableSuffix || suffix()
  completeForm.logisticsNo = row.logisticsNo || ''
  completeOpen.value = true
  loadTracking(row)
}

/**
 * 把这张批次里要人指定序列号 / 出库批次的行捞出来，并按发货仓库拉可选项。
 *
 * 明细上的 batchStrategy / costCalculation 是后端 join 商品档案带出来的，批次表不存。
 * 拉不到可选项不拦着弹窗：下拉是空的比弹窗打不开好说话，真发不出去出库单会说清原因。
 */
function loadTracking(row) {
  trackingItems.value = []
  trackingLoading.value = true
  getBatch(row.shipCode, row.tableSuffix || suffix())
    .then(r => {
      const data = r.data || {}
      const wareCode = data.warehouseCode || row.warehouseCode
      trackingItems.value = (data.items || []).filter(needsTracking).map(item => ({
        ...item, serialNumbers: [], serialOptions: [], batchCode: '', batchOptions: []
      }))
      return Promise.all(trackingItems.value.map(item => loadTrackingOptions(item, wareCode)))
    })
    .catch(() => trackingItems.value = [])
    .finally(() => trackingLoading.value = false)
}

/** 单行的可选项：序列号取在库序列号，批次取可用批次，都限定在发货仓的「良品·在库」 */
function loadTrackingOptions(item, wareCode) {
  if (!wareCode) return Promise.resolve()
  if (item.batchStrategy === 'serial') {
    // 不传状态，接口默认就是「良品·在库」，与销售出库单要动的那批库存一致
    return listInStockSerial(item.goodsCode, wareCode)
      .then(r => item.serialOptions = r.data || r.rows || [])
      .catch(() => item.serialOptions = [])
  }
  return listAvailableBatch({ goodsCode: item.goodsCode, wareCode, qualityFlag: 1, onhandFlag: 1 })
    .then(r => item.batchOptions = r.data || r.rows || [])
    .catch(() => item.batchOptions = [])
}

function doComplete() {
  // 先在前端拦一道。出库单也会拦，但那时报的是出库单的话术，操作人对不上是哪一行
  for (const item of trackingItems.value) {
    if (item.batchStrategy === 'serial') {
      const need = serialCount(item)
      if (need !== Number(item.qty)) {
        ElMessage.warning(`商品「${item.goodsName}」按序列号管理，发货数量必须是整数`)
        return
      }
      const picked = (item.serialNumbers || []).length
      if (picked !== need) {
        ElMessage.warning(`商品「${item.goodsName}」要选 ${need} 个序列号，现在选了 ${picked} 个`)
        return
      }
    } else if (!item.batchCode) {
      ElMessage.warning(`商品「${item.goodsName}」是个别计价，必须指定出库批次`)
      return
    }
  }
  const serials = {}, batchCodes = {}
  for (const item of trackingItems.value) {
    if (item.batchStrategy === 'serial') serials[item.detailCode] = [...item.serialNumbers]
    else batchCodes[item.detailCode] = item.batchCode
  }

  saving.value = true
  completeBatch(completeForm.shipCode, {
    tableSuffix: completeForm.tableSuffix,
    serials: Object.keys(serials).length ? serials : undefined,
    batchCodes: Object.keys(batchCodes).length ? batchCodes : undefined
  }).then(r => { ElMessage.success(r.msg || '已完成'); completeOpen.value = false; loadList() })
    .finally(() => saving.value = false)
}

function cancel(row) {
  // 已经交给仓库的批次，那边可能备货单都打了、货正在装箱，作废之前先提醒一句去打个招呼。
  // 被驳回退回来的不用提醒——仓库自己推回来的，它知道
  const warn = isPending(row)
    ? (wasRejected(row) ? '这张批次是仓库驳回退回来的。' : '')
    : (row.status === '备货完成'
        ? '仓库已经把这批货备好并拍了照，作废之前务必先跟仓库打个招呼，让他们把货归位。'
        : '这张批次已经交给仓库，请先跟仓库确认货还没装箱。')
  ElMessageBox.confirm(warn + '取消后该批次作废，订单上这部分量会放回去，可以重新排批次。是否继续？',
    '提示', { closeOnClickModal: false })
    .then(() => cancelBatch(row.shipCode, row.tableSuffix || suffix()))
    .then(() => { ElMessage.success('批次已取消'); loadList() })
    .catch(() => {})
}

function openDetail(row) {
  getBatch(row.shipCode, row.tableSuffix || suffix())
    // items 兜成数组：el-table 的 :data 收到 null 会在渲染时抛
    .then(r => { detail.value = { ...(r.data || {}), items: r.data?.items || [] }; detailOpen.value = true })
}

onMounted(() => {
  loadList()
  listWarehouse({ pageSize: 100 }).then(r => warehouses.value = r.rows || [])
})

// keep-alive 缓存：从订单页切回来时 onMounted 不再触发，列表会停在旧快照
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) { skipFirstActivate = false; return }
  loadList()
})
</script>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.form-hint { line-height: 32px; }
.mt8 { margin-top: 8px; }

/* 录单弹窗的付款额度小账单。靠右摆一列，让「已付 / 已占 / 还能发 / 本次」四个数对齐 */
.allowance {
  margin-left: auto;
  width: 340px;
  font-size: 13px;
}
.allowance-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  color: var(--el-text-color-regular);
}
.allowance-row.total {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color);
  font-size: 14px;
}
.allowance-row.total.ok b { color: var(--el-color-success); }
.allowance-row.total.bad b { color: var(--el-color-danger); }
.over-hint {
  color: var(--el-color-danger);
  text-align: right;
}
.tracking-ok {
  color: var(--el-color-success);
}
.tracking-miss {
  color: var(--el-color-danger);
  font-weight: 600;
}
.section-title {
  margin: 4px 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
:deep(.el-form-item) {
  margin-bottom: 16px;
}

/* 打印单据预览。这块 DOM 会被整段搬进打印用的 iframe，那边另有一份等价样式
   （PRINT_CSS）——scoped 样式带的 data-v 属性搬过去就失效了，两边要一起改。 */
.ship-sheet {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  padding: 16px;
}
.ship-sheet h2 {
  text-align: center;
  margin: 0 0 16px;
  font-size: 18px;
}
.ship-sheet table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12px;
}
.ship-sheet td,
.ship-sheet th {
  border: 1px solid var(--el-border-color);
  padding: 6px 8px;
  font-size: 13px;
}
.ship-sheet .label {
  width: 90px;
  background: var(--el-fill-color-light);
}
.ship-sheet .num { text-align: right; }
.sheet-sign {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-top: 20px;
  color: var(--el-text-color-regular);
}
</style>

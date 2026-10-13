<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="草稿 → 提交 → 审核通过 → 发货在「订单管理 → 发货批次」办理"
      description="整条链一件货都不占，库存到发货那一刻才由出库单锁定扣减。「提交」和「审核」都在「详情」弹窗底部：这两步一个把单推给审核人、一个放行到仓库，都该先把单子摊开看一遍再点，所以建单弹窗里没有「保存并提交」，列表上也没有这两颗。其余动作在列表右侧的操作列上。待审核的单，提交人自己可以「撤回」。审核通过时不挑仓、不占货：货看着凑齐就落「已备货」，还差就落「备货中」——这只是提示，不影响建发货批次，也照样能撤审、关闭、取消。一件未发的用「取消」，发过货又不再发的用「关闭」。真正会挡住终止的是已经派出去的活儿：有在途发货批次或在制工单时，得先去那边处理掉。" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <el-form-item label="月份">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" @change="search" />
      </el-form-item>
      <el-form-item label="订单号"><el-input v-model="query.orderCode" clearable style="width: 170px" @keyup.enter="search" /></el-form-item>
      <el-form-item label="履约状态">
        <el-select v-model="query.orderState" clearable style="width: 130px">
          <el-option v-for="s in orderStates" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="支付状态">
        <el-select v-model="query.payStatus" clearable style="width: 130px">
          <el-option v-for="s in payStates" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <!--
        两个入口，跟这张表其余条件同一套口径：代码精确、名称模糊。
        账号走 staffcode 精确匹配；名称是后端拿姓名去 sys_user 反查出一批账号再筛，
        因为姓名并不落在订单表上。两个都填就是「且」，一般只用一个。
      -->
      <el-form-item v-if="canSeeAllOrders" label="业务员账号"><el-input v-model="query.staffCode" clearable style="width: 120px" @keyup.enter="search" /></el-form-item>
      <el-form-item v-if="canSeeAllOrders" label="业务员名称"><el-input v-model="query.staffName" clearable style="width: 120px" @keyup.enter="search" /></el-form-item>
      <el-form-item label="收件人"><el-input v-model="query.receivePerson" clearable style="width: 130px" @keyup.enter="search" /></el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">搜索</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" v-hasPermi="['order:sales:add']" @click="openCreate">新建订单</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="search" />
    </el-row>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="orderCode" label="订单号" min-width="160" />
      <el-table-column prop="shopStoreName" label="店铺" min-width="120" :show-overflow-tooltip="true" />
      <!--
        业务员 = 建单那一刻的登录账号（staffcode），不是客户档案上那个「归属业务员」。
        显示的姓名由后端现查 sys_user.nick_name 填进 staffName，账号查不到就退回显示账号本身。
      -->
      <el-table-column prop="staffName" label="业务员" width="100" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.staffName || '-' }}</template>
      </el-table-column>
      <!-- 显示的是下单当时的客户快照，不是客户档案的当前值：客户改了名，历史单照旧 -->
      <el-table-column prop="buyerName" label="客户" min-width="120" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.buyerName || '-' }}</template>
      </el-table-column>
      <!-- 商品内容：后端按明细算好的一句「可乐等3种」，纯展示，不展开 -->
      <el-table-column prop="goodsSummary" label="商品内容" min-width="160" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.goodsSummary || '-' }}</template>
      </el-table-column>
      <el-table-column prop="receivePerson" label="收件人" width="100" />
      <el-table-column v-if="canSeeSale" label="订单金额" width="110" align="right">
        <template #default="scope">{{ formatCurrency(scope.row.totalAmount, scope.row.currency) }}</template>
      </el-table-column>
      <!-- 已收金额与支付/回款状态由财务模块的回款流水终审通过时回写，订单侧不能手工改，
           与采购单的已付货款同一口径。明细和「认领打款」都在详情的「收款流水」页签。 -->
      <el-table-column v-if="canSeeSale" label="已收金额" width="110" align="right">
        <template #header>
          已收金额
          <el-tooltip content="由财务模块的回款流水终审通过后回写，订单侧不能手工改" placement="top">
            <el-icon class="head-tip"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <template #default="scope">
          <span :class="{ 'text-muted': !Number(scope.row.paidAmount) }">{{ formatCurrency(scope.row.paidAmount, scope.row.currency) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="payStatus" label="支付" width="95">
        <template #default="scope"><el-tag size="small" :type="payType(scope.row.payStatus)">{{ scope.row.payStatus }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="orderState" label="履约" width="95">
        <template #default="scope"><el-tag size="small" :type="stateType(scope.row.orderState)">{{ scope.row.orderState }}</el-tag></template>
      </el-table-column>
      <!-- 设计文档里发货进度就是画成进度条的，照做 -->
      <el-table-column label="发货进度" width="150">
        <template #default="scope">
          <el-progress :percentage="percent(scope.row.sendPercent)" :stroke-width="12"
            :status="percent(scope.row.sendPercent) >= 100 ? 'success' : undefined" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <!--
        按钮按状态出现：编辑期（草稿/已驳回）→ 审核期（待审核）→ 执行期（已备货起）。

        <b>除「提交」和「审核」外，动作都在这一列</b>。原先撤审 / 关闭 / 取消同时出现在列表和
        详情底部，而「已备货」又偏偏只在详情里给——同一件事三个状态三种找法，谁都记不住，
        所以收到了这里。

        <b>「提交」和「审核」反过来，只在详情弹窗底部，这一列里没有</b>：这两步是推进流程的
        两道关口（推给审核人、放行到仓库），都该看完整张单再点。摆在列表上一行扫过去就点得到，
        等于鼓励不看就推。要点就先「详情」把单子摊开——建单弹窗里也因此没有「保存并提交」。
        判据仍然是各个 canXxx，它们表达「这个状态在业务上允不允许做这件事」，后端按同一套口径挡。
      -->
      <el-table-column label="操作" width="390" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
          <el-button link type="primary" v-if="canEdit(scope.row)" v-hasPermi="['order:sales:edit']" @click="openEdit(scope.row)">修改</el-button>
          <el-button link type="info" v-if="isPending(scope.row)" v-hasPermi="['order:sales:submit']" @click="withdraw(scope.row)">撤回</el-button>
          <el-button link type="warning" v-if="canUnconfirm(scope.row)" v-hasPermi="['order:sales:confirm']" @click="unconfirm(scope.row)">撤审</el-button>
          <el-button link type="danger" v-if="canClose(scope.row)" v-hasPermi="['order:sales:close']" @click="openClose(scope.row)">关闭</el-button>
          <el-button link type="danger" v-if="canCancel(scope.row)" v-hasPermi="['order:sales:cancel']" @click="cancel(scope.row)">取消</el-button>
          <el-button link type="danger" v-if="canDelete(scope.row)" v-hasPermi="['order:sales:remove']" @click="remove(scope.row)">删除</el-button>
          <!--
            复制建单：把这张单的内容抄进「新建销售订单」弹窗，单号由后端另发。
            它和上面那些按钮不是一类——不改原单的任何状态，只读一次详情，
            所以不挂任何 canXxx，什么状态的单都抄得动（取消掉的单常常正是要再来一单的那张）。
            权限跟着建单走：抄出来的是新单，能建单的人才给按。
          -->
          <el-button link type="primary" v-hasPermi="['order:sales:add']" @click="openCopy(scope.row)">复制</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 后端 startPage 默认每页 10 条，没有这个组件就只看得到第一页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 新建 / 修改订单 ===================== -->
    <el-dialog v-model="createOpen" :title="editing ? '修改销售订单' : '新建销售订单'" width="1080px" destroy-on-close :close-on-click-modal="false">
      <!--
        复制来源。抄完之后表单里的一切都与原单无关了，这里只说清两件事：
        内容是从哪张单抄的（好核对），以及新单是独立的一张（原单不受影响）。
      -->
      <el-alert v-if="copyingFrom" type="info" show-icon :closable="false" class="mb8"
        :title="`正在复制订单 ${copyingFrom} 的内容`"
        description="保存后是一张全新的草稿订单，单号由系统另发。原单的订单号与平台订单号、履约与支付状态、已收金额、发货进度，以及挂在旧明细上的工单和批次都不会带过来（平台订单号需要按新单重填，平台留言照抄了）；原单不会有任何变化。保存前请再核一遍内容。" />
      <el-form :model="form" label-width="100px">
        <div class="section-title">平台信息</div>
        <el-row :gutter="20">
          <el-col :span="6"><el-form-item label="平台代码" required>
            <el-select v-model="form.platCode" filterable placeholder="请选择平台" style="width:100%" @change="handlePlatformChange">
              <el-option v-for="item in platformList" :key="item.platformCode"
                :label="`${item.platformName}（${item.platformCode}）`" :value="item.platformCode" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="6"><el-form-item label="平台名称"><el-input v-model="form.platName" disabled placeholder="选择平台后自动带出" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="平台订单号"><el-input v-model="form.platOrderId" placeholder="选填" /></el-form-item></el-col>
          <!--
            平台留言是买家写的要求，常常好几句话，单行输入框里只能左右横移着看，改成两行文本域。
            500 是库里这一列的长度（varchar(500)），maxlength 卡在这里，别等保存时才被数据库顶回来。

            「展开」不是单纯把行数加大：这一格只有弹窗的四分之一宽，500 字要排二十多行才装得下，
            光加行数会得到一个又高又窄的柱子。所以展开时连格子一起放成整行（span 6 → 24），
            文本框 2 行 → 10 行。整行宽度一行约 60 多个字，10 行够装下满格的 500 字，
            再大就是空地方了。展开状态在每次打开弹窗时复位，免得下次建单顶着一个大框。

            同排另外三格不会被拽歪：el-row 不设 align 时是 flex 默认的 stretch，四格会跟最高的一格
            齐平撑高，但每格里的标签和控件仍旧贴在自己那格的顶部，只是下面多留一点空白。
          -->
          <el-col :span="platLeaveExpanded ? 24 : 6">
            <el-form-item label="平台留言">
              <el-input v-model="form.platOrderLeave" type="textarea" :rows="platLeaveExpanded ? 10 : 2"
                maxlength="500" show-word-limit placeholder="选填" />
              <div class="field-expand">
                <el-button link type="primary" :icon="platLeaveExpanded ? 'ArrowUp' : 'ArrowDown'"
                  @click="platLeaveExpanded = !platLeaveExpanded">{{ platLeaveExpanded ? '收起' : '展开' }}</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">店铺信息</div>
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="店铺代码" required>
            <el-select v-model="form.shopStoreCode" filterable placeholder="请先选择平台" style="width:100%" :disabled="!form.platCode" @change="handleStoreChange">
              <el-option v-for="item in storeList" :key="item.storeCode"
                :label="`${item.storeName}（${item.storeCode}）`" :value="item.storeCode" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="店铺名称"><el-input v-model="form.shopStoreName" disabled placeholder="选择店铺后自动带出" /></el-form-item></el-col>
        </el-row>

        <div class="section-title">买家信息</div>
        <el-row :gutter="20">
          <!-- 只把客户编码交给后端，名称和图片由后端按编码查 u_m_buyer 现填。
               前端传的快照值后端一概丢弃，否则订单上的客户名可以被改成任意值。 -->
          <el-col :span="12"><el-form-item label="客户">
            <buyer-select v-model="form.buyerCode" :name="form.buyerName" @change="handleBuyerChange" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="二级客户">
            <!-- 依赖主客户，没选客户时禁用。换客户会清空这里，见 handleBuyerChange -->
            <el-select v-model="form.buyer2Code" filterable clearable style="width:100%"
              :disabled="!form.buyerCode"
              :placeholder="form.buyerCode ? (buyer2List.length ? '选填，如具体门店' : '该客户暂无二级客户') : '请先选择客户'">
              <el-option v-for="item in buyer2List" :key="item.buyer2Code"
                :label="item.buyer2User ? `${item.buyer2Name}（${item.buyer2User}）` : item.buyer2Name"
                :value="item.buyer2Code" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="买家账号"><el-input v-model="form.buyerUser" placeholder="散客可直接手填，选填" /></el-form-item></el-col>
          <!-- 国家取字典 order_country（242 个国家/地区）。下拉带 filterable，
               输中文名或两位代码都筛得到，不用在两百多项里翻 -->
          <el-col :span="8"><el-form-item label="客户国家">
            <el-select v-model="form.buyerCountry" filterable clearable placeholder="输中文名或代码筛选" style="width:100%">
              <el-option v-for="item in order_country" :key="item.value"
                :label="`${item.label}（${item.value}）`" :value="item.value" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <!--
          国外地址不结构化，整段收着就行：各国的行政区划层级对不上，硬拆成省市区只会拆错。
          这一段是货最终要到客户手里的地方；下面「国内收货信息」是国内这一程先发到哪，
          两者不是一回事，别拿一个去填另一个。
        -->
        <el-form-item label="客户地址">
          <el-input v-model="form.buyerAddress" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="客户在国外的收货地址，整段填写即可，选填" />
        </el-form-item>

        <div class="section-title">国内收货信息</div>
        <el-form-item label="收货方式" required>
          <el-radio-group v-model="form.receiveType" @change="handleReceiveTypeChange">
            <el-radio v-for="type in receiveTypes" :key="type" :value="type">{{ type }}</el-radio>
          </el-radio-group>
          <div class="hint form-hint">{{ receiveTypeHint }}</div>
        </el-form-item>
        <!-- 上门提货货不出仓、不发快递、不打面单，收件人/电话/地址都没有着落处，三样都不强求；
             后端 validateHeader 同口径。电话填了的话照样按 11 位手机号收。 -->
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item label="收件人" :required="!isPickup">
            <el-input v-model="form.receivePerson" :placeholder="isPickup ? '上门提货可以不填' : ''" />
          </el-form-item></el-col>
          <!-- 不限 11 位：平台下单给的是虚拟号，位数不固定；客户留的可能是带区号的固话；
               也常常一次留好几个号码。口径见 utils/validate.js 的 isContactPhone -->
          <el-col :span="8"><el-form-item label="收件电话" :required="!isPickup">
            <el-input v-model="form.receiveTelephone" :maxlength="CONTACT_PHONE_MAX_LENGTH"
              :placeholder="isPickup ? '上门提货可以不填' : '虚拟号、固话都行，多个号码用逗号或分号分隔'" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="邮编"><el-input v-model="form.receiveZipCode" placeholder="选填" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="收件地址" :required="!isPickup">
          <el-input v-model="form.receiveAddress" :placeholder="isPickup ? '上门提货可以不填' : ''" />
        </el-form-item>
        <!-- 唛头是货代认箱用的，只有走货代才问；换成别的收货方式时下面 handleReceiveTypeChange 会清掉 -->
        <template v-if="form.receiveType === RECEIVE_TYPE_FORWARDER">
          <el-form-item label="唛头">
            <el-radio-group v-model="form.markNeeded">
              <el-radio :value="false">不需要</el-radio>
              <el-radio :value="true">需要</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.markNeeded" label="唛头文件">
            <file-upload v-model="form.markFile" preserve-name thumbnail :limit="1" :file-size="20"
              :file-type="['pdf', 'doc', 'docx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg', 'zip', 'rar']" />
          </el-form-item>
        </template>

        <div class="section-title">订单明细</div>
        <!-- 币种一旦提交或收过钱就锁死：已收金额按原币种记账，改币种等于把两种钱混进同一个字段；
             财务那边也是按币种筛可回款订单的。后端 SalesOrderServiceImpl.updateDraft 同口径再卡一道。 -->
        <el-form-item label="订单币种" required>
          <el-select v-model="form.currency" style="width: 260px" :disabled="currencyLocked">
            <el-option v-for="item in currencyOptions" :key="item.code"
              :label="currencyOptionLabel(item)" :value="item.code" />
          </el-select>
          <div class="hint form-hint" v-if="currencyLocked">
            {{ Number(form.paidAmount) ? '订单已有收款记录' : '订单已提交审核' }}，币种不能再改；如需换币种请另开新单。
          </div>
          <div class="hint form-hint" v-else>明细单价、运费、订单总额都按此币种记账，本系统不做汇率换算。</div>
        </el-form-item>
        <!--
          商品描述清单。销售按客户的口径写：客户谈的是「定制礼盒 A 款 1000 个」，
          内部备货对应的可能是好几个 SKU。这张表只有名称、数量、单价，不挂商品编码、
          不占库存、不参与发货，也不进货款合计——货款按下面的货品明细算。
        -->
        <div class="sub-title">商品描述</div>
        <div class="mb8">
          <el-button type="primary" icon="Plus" @click="addDescItem">添加商品描述</el-button>
        </div>
        <el-table :data="form.descItems" border stripe
          empty-text="选填。写给客户和生产看的口径说明，不写也能下单">
          <el-table-column label="名称 / 描述" min-width="240">
            <template #default="scope">
              <el-input v-model="scope.row.name" maxlength="200" placeholder="按客户的说法写，例如：定制礼盒 A 款" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qty" :min="0" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale" label="销售单价" width="160">
            <template #default="scope">
              <decimal-input :min-decimals="2" :max-decimals="4" v-model="scope.row.price" :min="0" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale" label="金额" width="120" align="right">
            <template #default="scope">{{ formatCurrency(lineAmount(scope.row), form.currency) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="scope"><el-button link type="danger" @click="form.descItems.splice(scope.$index, 1)">移除</el-button></template>
          </el-table-column>
        </el-table>
        <div v-if="canSeeSale" class="hint mt8">
          这张清单<b>不进货款合计</b>，货款按下面的货品明细算；名称留空的行保存时会被丢掉。
          <template v-if="form.descItems.length">
            本清单合计 <b>{{ formatCurrency(descAmount, form.currency) }}</b>，
            下面货品明细 <b>{{ formatCurrency(goodsAmount, form.currency) }}</b>。
            <!-- 两边不等不是错，定制单常常谈总价、内部拆成几个 SKU，所以只提示不拦 -->
            <span v-if="descAmount !== goodsAmount" class="shortage">两边对不上，报给客户前先核一遍。</span>
          </template>
        </div>

        <div class="sub-title">货品明细</div>
        <div class="mb8">
          <el-button type="primary" icon="Plus" @click="openGoodsSelect">添加货品</el-button>
          <!-- 套餐只是一组货品的组合标签，选完铺进来的行跟手工添加的没有区别 -->
          <el-button icon="Files" @click="openPackageSelect">选择套餐</el-button>
          <!--
            整单开关。定制单往往每一行都是定制的，一行行拨太啰嗦；混合单拨完再单独改那几行。
            这个标记只能人来定：从数量上看不出「库存里占上的货顶不顶得上用」。
          -->
          <el-button :disabled="!form.items.length" :type="allCustomMade ? 'warning' : ''"
            :icon="allCustomMade ? 'Close' : 'Tools'" @click="toggleAllCustomMade">
            {{ allCustomMade ? '整单取消专门生产' : '整单标为专门生产' }}
          </el-button>
        </div>
        <el-table :data="form.items" border stripe empty-text="点击上方“添加货品”开始录入">
          <el-table-column prop="goodsName" label="货品" min-width="180" />
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <!--
            专门生产 = 库存里的通用货顶不上，必须为这一行单独做一批。勾上之后：
            这一行做出来的成品入库时会绑给本条明细，别的订单发货挑不到（u_m_batchinfo.owner_orderdetailcode）；
            订单确认时它也不看通用库存，专属批次没备齐就落「备货中」；
            后端会把这一行的备货来源一并置成「生产」（订单页不再展示这个字段），
            生产模块的排产选单只列来源是生产的明细，所以勾了才排得上产。
            不勾的行走通用库存，谁先发货谁拿到。只有下单的人知道该勾哪个，所以在这里录。
          -->
          <el-table-column label="专门生产" width="100" align="center">
            <template #default="scope">
              <el-switch v-model="scope.row.customMade" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template #default="scope">
              <decimal-input :min-decimals="0" :max-decimals="4" v-model="scope.row.qty" :min="0.0001" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale" label="销售单价" width="160">
            <template #default="scope">
              <decimal-input :min-decimals="2" :max-decimals="4" v-model="scope.row.price" :min="0" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeSale" label="金额" width="120" align="right">
            <template #default="scope">{{ formatCurrency(lineAmount(scope.row), form.currency) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="scope"><el-button link type="danger" @click="form.items.splice(scope.$index, 1)">移除</el-button></template>
          </el-table-column>
        </el-table>
        <!-- 同一货品重复开行会让发货回写失去唯一定位，后端会拒，这里提前拦 -->
        <div class="hint mt8">同一货品只能开一行，数量合并填写。</div>
        <div class="hint mt8">
          <b>专门生产</b>：客户要的规格库存里的通用货顶不上，必须为这一行单独排一批产。
          勾上之后，生产那边的排产选单才挑得到这一行；为它做出来的成品会绑定给这条明细，
          别的订单发货拿不走。不勾的行走通用库存，谁先发货谁拿到。
          这个判断只有下单的人做得了，订单确认后要改得先撤审。
        </div>

        <div class="section-title">金额</div>
        <el-row :gutter="20">
          <el-col v-if="canSeeSale" :span="8"><el-form-item label="运费"><el-input-number v-model="form.freightAmount" :min="0" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="16">
            <div v-if="canSeeSale" class="amount-summary">
              货款 <b>{{ formatCurrency(goodsAmount, form.currency) }}</b>
              ＋ 运费 <b>{{ formatCurrency(form.freightAmount, form.currency) }}</b>
              ＝ 订单总额 <b class="total">{{ formatCurrency(goodsAmount + Number(form.freightAmount || 0), form.currency) }}</b>
            </div>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>

        <div class="section-title">订单资料</div>
        <el-form-item label="附件">
          <file-upload v-model="form.orderFiles" preserve-name thumbnail :limit="10" :file-size="20"
            :file-type="['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'png', 'jpg', 'jpeg', 'zip', 'rar']" />
          <div class="hint">与客户来回确认的合同、图纸、确认函这些，留在单上给生产做参考。选填，最多 10 个。</div>
        </el-form-item>
      </el-form>
      <!--
        两个出口：「保存为草稿」只落库；「提交审核」一步落待审核，中间不经过草稿。
        提交那颗做主按钮：大多数单填完就是要交审的，先存草稿再进详情点提交是多走一步。
        两条路都由后端在同一个事务里做完，不存在「先存后提」那种两步半成功的状态——
        提交审核失败就什么都没落库，连草稿也不会留下。
        改单时对应「保存修改」和「保存并提交」，已驳回的单改完直接重提。
        详情弹窗底部那颗「提交审核」照旧，给「先存着，回头再看一遍」的人用。
      -->
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button :loading="saving" @click="save()">{{ editing ? '保存修改' : '保存为草稿' }}</el-button>
        <el-button type="primary" :loading="saving" v-hasPermi="['order:sales:submit']" @click="save(true)">
          {{ editing ? '保存并提交' : '提交审核' }}</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 强制关闭 ===================== -->
    <el-dialog v-model="closeOpen" title="关闭订单" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" class="mb8"
        title="只终止没发的部分，已发的货不受影响"
        description="关闭后订单不再能建发货批次。已经发出去的货要退，请走「销售退货」。关闭后不能撤销。" />
      <el-form label-width="90px">
        <el-form-item label="订单号">{{ reasonForm.orderCode }}</el-form-item>
        <el-form-item label="发货进度">{{ reasonForm.sendPercent }}%</el-form-item>
        <el-form-item label="关闭原因" required>
          <el-input v-model="reasonForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="必填，例如：客户弃单，剩余部分不再发货" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeOpen = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doClose">确认关闭</el-button>
      </template>
    </el-dialog>

    <!-- 这里没有回款登记弹窗：已收金额只由财务模块的回款流水终审通过时回写，订单侧不能手填。
         业务员在订单里能做的只有「认领」——把财务已经录好的一笔打款认到本单，见详情的「收款流水」页签。 -->

    <!-- ===================== 详情 ===================== -->
    <el-dialog v-model="detailOpen" title="销售订单详情" width="1120px" :close-on-click-modal="false">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="订单号">{{ detail.orderCode }}</el-descriptions-item>
        <el-descriptions-item label="履约状态"><el-tag :type="stateType(detail.orderState)">{{ detail.orderState }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="发货进度">{{ percent(detail.sendPercent) }}%</el-descriptions-item>
        <el-descriptions-item label="店铺">{{ detail.shopStoreName || detail.shopStoreCode }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ detail.platName || detail.platCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="平台订单号">{{ detail.platOrderId || '—' }}</el-descriptions-item>
        <!--
          买家在平台上提的要求（发哪家快递、要不要发票）就写在这儿，往往一次好几句话，
          所以整行铺开，不挤在三分之一格里把字压成一条。空着说「无」，与下面的「备注」同口径——
          留一片空白会让人以为是数据没取回来，而这个字段本来就是选填的。
        -->
        <el-descriptions-item label="平台留言" :span="3">{{ detail.platOrderLeave || '无' }}</el-descriptions-item>
        <!-- 姓名后面挂上账号：同名员工不少，出了问题要按账号找人 -->
        <el-descriptions-item label="业务员">
          {{ detail.staffName || '—' }}<span v-if="detail.staffCode" class="hint">（{{ detail.staffCode }}）</span>
        </el-descriptions-item>
        <!-- 客户信息全部取订单上的快照，不回查客户档案：这里显示的就是下单当时的样子 -->
        <el-descriptions-item label="客户">{{ detail.buyerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="二级客户">
          {{ detail.buyer2Name ? (detail.buyer2User ? `${detail.buyer2Name}（${detail.buyer2User}）` : detail.buyer2Name) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="买家账号">{{ detail.buyerUser || '—' }}</el-descriptions-item>
        <el-descriptions-item label="客户国家">{{ countryLabel(detail.buyerCountry) }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ currencyLabel(detail.currency) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeSale" label="货款">{{ formatCurrency(detail.goodsAmount, detail.currency) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeSale" label="运费">{{ formatCurrency(detail.freightAmount, detail.currency) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeSale" label="订单总额">{{ formatCurrency(detail.totalAmount, detail.currency) }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeSale" label="已收金额">{{ formatCurrency(detail.paidAmount, detail.currency) }}</el-descriptions-item>
        <el-descriptions-item label="支付状态"><el-tag size="small" :type="payType(detail.payStatus)">{{ detail.payStatus }}</el-tag></el-descriptions-item>
        <!-- 回款状态与支付状态同源，一个是订单视角一个是财务视角，两边都要有是因为
             订单设计文档要 paystatus，V2.0 §5.4 流程2 要求财务回写 receivestatus -->
        <el-descriptions-item label="回款状态">{{ detail.receiveStatus || '—' }}</el-descriptions-item>
        <!-- 客户地址是货最终到客户手里的地方，下面的收件信息是国内这一程发到哪，两段各管一头 -->
        <el-descriptions-item label="客户地址" :span="3">{{ detail.buyerAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="国内收货方式">{{ detail.receiveType || '—' }}</el-descriptions-item>
        <!-- 上门提货这三样都可以留空，空的时候说清楚为什么空，别显示成一片空白 -->
        <el-descriptions-item label="收件人">{{ receiveText(detail.receivePerson) }}</el-descriptions-item>
        <el-descriptions-item label="收件电话">{{ receiveText(detail.receiveTelephone) }}</el-descriptions-item>
        <el-descriptions-item label="收件地址" :span="2">{{ receiveText(detail.receiveAddress) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <!-- 唛头只有走货代才有，别的收货方式后端会把这两列清掉，这里也就不占位置 -->
        <el-descriptions-item v-if="detail.receiveType === RECEIVE_TYPE_FORWARDER" label="唛头" :span="3">
          <template v-if="detail.markNeeded">
            需要
            <file-upload v-if="detail.markFile" v-model="detail.markFile" preserve-name thumbnail disabled class="detail-files" />
            <span v-else class="shortage ml8">还没上传唛头文件</span>
          </template>
          <span v-else class="text-muted">不需要</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="订单资料" :span="3">
          <file-upload v-if="detail.orderFiles" v-model="detail.orderFiles" preserve-name thumbnail disabled class="detail-files" />
          <span v-else class="text-muted">无</span>
        </el-descriptions-item>
        <!-- 驳回原因不因重新提交而清空，留着正好对照这轮改了什么 -->
        <el-descriptions-item v-if="detail.rejectReason" label="驳回原因" :span="3">
          <span class="reason-text">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="3">
          <span class="reason-text">{{ detail.closeReason }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="detailTab" class="mt20">
        <el-tab-pane label="货品明细" name="items">
          <el-table :data="detail.items">
            <el-table-column prop="lineNo" label="行" width="55" />
            <el-table-column prop="goodsName" label="货品" min-width="160" />
            <el-table-column prop="spec" label="规格" width="100" />
            <el-table-column prop="qty" label="订单数量" width="100" align="right" />
            <el-table-column prop="shippedQty" label="已发" width="90" align="right" />
            <!-- 未发量 = 数量 - 已发，后端现算不落库 -->
            <el-table-column label="未发" width="90" align="right">
              <template #default="scope">
                <span v-if="Number(scope.row.pendingQty) > 0" class="shortage">{{ scope.row.pendingQty }}</span>
                <span v-else class="text-muted">0</span>
              </template>
            </el-table-column>
            <el-table-column label="定制" width="70" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.customMade" size="small" type="warning">定制</el-tag>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <!--
              「备货来源」列和「登记来源」按钮去掉了：这一行的货从哪来是备货侧的事，
              销售订单只关心卖了什么、发了多少。要不要专门做由建单时的「定制」决定，
              那一列还在；采购、生产各自的进度去对应模块看。
            -->
            <el-table-column v-if="canSeeSale" label="单价" width="110" align="right">
              <template #default="scope">{{ formatCurrency(scope.row.price, detail.currency) }}</template>
            </el-table-column>
            <el-table-column v-if="canSeeSale" label="金额" width="110" align="right">
              <template #default="scope">{{ formatCurrency(scope.row.amount, detail.currency) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!--
          商品描述清单。写给客户和生产看的口径说明，不进货款合计，也不参与发货，
          所以不放角标——它不会「又多出来一条」，只随改单变。没写过就整个页签不出现。
        -->
        <el-tab-pane v-if="(detail.descItems || []).length" label="商品描述" name="descItems">
          <el-table :data="detail.descItems">
            <el-table-column prop="name" label="名称 / 描述" min-width="240" :show-overflow-tooltip="true" />
            <el-table-column prop="qty" label="数量" width="120" align="right" />
            <el-table-column v-if="canSeeSale" label="单价" width="130" align="right">
              <template #default="scope">{{ formatCurrency(scope.row.price, detail.currency) }}</template>
            </el-table-column>
            <el-table-column v-if="canSeeSale" label="金额" width="130" align="right">
              <template #default="scope">{{ formatCurrency(scope.row.amount, detail.currency) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="canSeeSale" class="hint mt8">
            本清单合计 <b>{{ formatCurrency(detailDescAmount, detail.currency) }}</b>，
            <b>不计入货款</b>——订单的货款 <b>{{ formatCurrency(detail.goodsAmount, detail.currency) }}</b>
            只按货品明细算。这张表是销售按客户口径写的说明，给客户和生产看。
          </div>
        </el-tab-pane>

        <el-tab-pane name="batches">
          <!--
            页签上单独标一个「驳回」：右边那个 tab-count 角标只在条数比上次看到的多时才亮，
            而驳回不改变批次条数（退回「待确认」而已），光靠它这件事永远不会被提示到。
            标在页签上，订单详情一打开就能看见，不用先点进这张 tab。
          -->
          <template #label>发货批次<span v-if="rejectedBatches.length" class="tab-flag">驳回</span><span v-if="showCount('batches', batches)" class="tab-count">{{ batches.length }}</span></template>
          <!--
            被仓库驳回的批次要在这张 tab 上自己喊出来。业务员日常是在销售订单里跟单的，
            驳回却发生在仓库那边，不主动提示就只能靠人定期去「发货批次」页翻——
            而被驳回的批次退回「待确认」后，状态和自己新建的草稿一模一样，翻也翻不出来。
          -->
          <el-alert v-if="rejectedBatches.length" type="error" show-icon :closable="false" class="mb8"
            :title="`有 ${rejectedBatches.length} 张批次被仓库驳回，还没重新提交`"
            description="货没发出去，仓库把批次退回来了。到「订单管理 → 发货批次」改数量／仓库／计划日期后重新确认，或者直接作废重排。下表「驳回原因」列是仓库填的说明。" />
          <el-table :data="batches">
            <el-table-column prop="shipCode" label="批次号" min-width="160" />
            <el-table-column prop="shipDate" label="计划发货" width="110">
              <template #default="scope">{{ parseTime(scope.row.shipDate, '{y}-{m}-{d}') }}</template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="发货仓库" min-width="120" />
            <el-table-column prop="totalQty" label="数量" width="90" align="right" />
            <el-table-column prop="logisticsNo" label="快递单号" min-width="140">
              <template #default="scope">{{ scope.row.logisticsNo || '—' }}</template>
            </el-table-column>
            <!-- 190 的算法同 views/order/shipbatch/index.vue 的状态列：
                 七字状态标签 + 「曾驳回」标签 + 单元格内边距，两个标签要能并排不换行 -->
            <el-table-column prop="status" label="状态" min-width="190">
              <template #default="scope">
                <el-tag size="small" :type="batchType(scope.row.status)">{{ scope.row.status }}</el-tag>
                <!--
                  驳回三列在批次重走流程后不清空（留作痕迹），所以「有 rejectTime」≠「现在被驳回着」。
                  停在待确认的才是待处理，标红；已经重新走下去的标灰，是历史不是待办。
                -->
                <el-tag v-if="batchRejected(scope.row)" size="small" type="danger" effect="plain" class="ml5">待处理</el-tag>
                <el-tag v-else-if="scope.row.rejectTime" size="small" type="info" effect="plain" class="ml5">曾驳回</el-tag>
              </template>
            </el-table-column>
            <!--
              原因给一整列而不是只挂 tooltip：业务员来这张 tab 就是为了搞清楚「为什么退回来」，
              要悬停才看得到等于没写。整行用 tooltip 兜住超长文本。
            -->
            <el-table-column prop="rejectReason" label="驳回原因" min-width="220" :show-overflow-tooltip="true">
              <template #default="scope">
                <span v-if="scope.row.rejectTime" :class="{ 'reject-reason': batchRejected(scope.row) }">
                  {{ scope.row.rejectReason || '未填写' }}
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="rejectUser" label="驳回人/时间" width="170">
              <template #default="scope">
                <template v-if="scope.row.rejectTime">
                  {{ scope.row.rejectUser || '仓库' }}<br />
                  <span class="hint">{{ scope.row.rejectTime }}</span>
                </template>
                <template v-else>—</template>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint mt8">批次按计划发货日期分月表，这里已跨月汇总。发货操作在「发货批次」页办理。</div>
        </el-tab-pane>

        <!--
          这张订单被哪些工单做过。按工单的产出分配反查，不看订单明细上的 sourcecode——
          那个字段是单值的，一条明细换过工单只留最后一张，关闭工单时还会被清空，
          拿它追溯会把历史工单整段丢掉。所以这里连已关闭、已完工的都列得出来。
        -->
        <el-tab-pane name="productions">
          <template #label>生产工单<span v-if="showCount('productions', productions)" class="tab-count">{{ productions.length }}</span></template>
          <el-table :data="productions" v-loading="productionLoading" empty-text="这张订单没有关联的生产工单">
            <el-table-column prop="productionCode" label="工单号" min-width="160" />
            <el-table-column label="产出商品" min-width="180" :show-overflow-tooltip="true">
              <template #default="scope">{{ scope.row.productCode }} {{ scope.row.productName }}</template>
            </el-table-column>
            <el-table-column prop="planYQty" label="计划产出" width="100" align="right" />
            <el-table-column prop="actualYQty" label="实际产出" width="100" align="right" />
            <!-- 一张工单可能同时服务多张订单，只把分给本单的那部分摘出来 -->
            <el-table-column label="为本单做" width="100" align="right">
              <template #default="scope">{{ allocatedToThisOrder(scope.row) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="scope"><el-tag size="small" :type="productionType(scope.row.status)">{{ scope.row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="计划日期" width="110">
              <template #default="scope">{{ parseTime(scope.row.planDate, '{y}-{m}-{d}') || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
          </el-table>
          <div class="hint mt8">
            按工单建单时登记的产出分配反查，已关闭和已完工的工单也在其中。
            工单的领料、完工、关闭在<b>生产管理 → 生产工单</b>页办理。
          </div>
        </el-tab-pane>

        <el-tab-pane name="transactions">
          <template #label>收款流水<span v-if="showCount('transactions', transactions)" class="tab-count">{{ transactions.length }}</span></template>
          <!-- 认领在这里做、不在财务的资金流水页：业务员站在自己的订单里挑打款，只看得到自己的单，
               认不到同事的单上。草稿和已取消的订单不能收款，按钮直接不出现，与后端 requireSalesOrder 同一口径。 -->
          <div class="mb8">
            <el-button type="primary" plain icon="Link" v-if="claimable"
              v-hasPermi="['finance:transaction:claim']" @click="openClaim">认领打款</el-button>
            <span class="hint" style="margin-left: 8px">客户直接打款、财务不知道是哪张单的，从这里认到本单</span>
          </div>
          <el-table :data="transactions" v-loading="transLoading">
            <el-table-column prop="transactionCode" label="流水号" min-width="150" />
            <el-table-column label="交易时间" width="160">
              <template #default="scope">{{ parseTime(scope.row.tradeTime || scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="accountName" label="收款账户" min-width="120">
              <template #default="scope">{{ scope.row.accountName || '—' }}</template>
            </el-table-column>
            <el-table-column prop="partyName" label="对方名称" min-width="120" />
            <el-table-column v-if="canSeeSale" label="金额" width="110" align="right">
              <template #default="scope"><span class="amount-in">{{ formatCurrency(scope.row.amount, scope.row.currency || detail.currency) }}</span></template>
            </el-table-column>
            <el-table-column prop="status" label="流水状态" width="100">
              <template #default="scope"><el-tag size="small" :type="transType(scope.row.status)">{{ scope.row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="认领人" width="100">
              <template #default="scope">{{ scope.row.completeUserName || scope.row.completeUser || '—' }}</template>
            </el-table-column>
            <el-table-column prop="bizSummary" label="业务摘要" min-width="200" :show-overflow-tooltip="true" />
            <!-- 撤回只给「已认领、还没终审」的行，且只有认领人本人或财务审核人能撤，与后端 unclaimTransaction 同一口径 -->
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button v-if="canUnclaim(scope.row)" link type="warning" icon="RefreshLeft"
                  v-hasPermi="['finance:transaction:claim']" @click="unclaimReceipt(scope.row)">撤回认领</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="canSeeSale" class="hint mt8">
            已收金额 <b>{{ formatCurrency(detail.paidAmount, detail.currency) }}</b> = 上表中<b>已完成</b>的流水合计
            （<b>{{ formatCurrency(completedReceipt, detail.currency) }}</b>）。
            还在「待审核 / 已审核」的流水表示钱已经报上来但财务还没终审，终审通过才会计入已收金额。
            认错了单，在那一行「撤回认领」；补录流水、改金额请去<b>财务管理 → 资金流水</b>。
          </div>
        </el-tab-pane>
      </el-tabs>
      <!--
        「提交」和「审核」<b>只有这里一个入口</b>，列表的操作列和建单弹窗里都没有：
        这两步都该看完整张单再点，而该看的内容就在上面。
        两颗按钮永不同时出现——canEdit 是草稿/已驳回，isPending 是待审核，互斥。
        提交完就地刷新，这颗按钮会自己换成「审核通过」。
        其余动作（撤回 / 撤审 / 关闭 / 取消）反过来只在列表的操作列上——那些不需要先摊开单子，
        两处各摆一份只会让出现条件各写一套、日子久了对不上。
      -->
      <template #footer>
        <el-button @click="detailOpen = false">关闭</el-button>
        <el-button type="primary" v-if="canEdit(detail)" :loading="saving"
          v-hasPermi="['order:sales:submit']" @click="submit(detail)">提交审核</el-button>
        <el-button type="success" v-if="isPending(detail)" :loading="saving"
          v-hasPermi="['order:sales:confirm']" @click="confirmOrderRow(detail)">审核通过</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 认领打款 ===================== -->
    <!--
      只列「客户回款、待审核、与本单同币种」的流水，后端 /finance/transaction/unclaimed 是同一条规则。
      不做汇率换算，所以美元订单看不到人民币打款。每行一颗「认领到本单」：一笔打款一行，
      一笔打款对应多张订单的，财务录入时就该按订单拆成多笔，这里不拆。
      财务建单时预填过订单号、又卡在待审核的老数据也会列出来，「财务预填单号」那列给业务员做参考。
    -->
    <el-dialog v-model="claimOpen" title="认领打款到本单" width="1040px" append-to-body :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon class="mb8">
        <template #title>
          订单 <b>{{ detail.orderCode }}</b>（{{ currencyLabel(detail.currency) }}）
          <template v-if="canSeeSale">
            ：应收 <b>{{ formatCurrency(detail.totalAmount, detail.currency) }}</b>，已收 <b>{{ formatCurrency(detail.paidAmount, detail.currency) }}</b>，
            待收 <b>{{ formatCurrency(receivableRemaining, detail.currency) }}</b>
          </template>
          。下面只列财务已录入提交、还没人认领的 {{ currencyLabel(detail.currency) }} 打款；认领后要财务终审通过才计入已收金额。
        </template>
      </el-alert>
      <el-table :data="unclaimed" v-loading="unclaimedLoading">
        <el-table-column prop="transactionCode" label="流水号" min-width="150" />
        <el-table-column label="交易时间" width="160">
          <template #default="scope">{{ parseTime(scope.row.tradeTime || scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="收款账户" min-width="120">
          <template #default="scope">{{ scope.row.accountName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="partyName" label="打款人" min-width="120" />
        <el-table-column label="对方文本" min-width="140" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.partyText || '—' }}</template>
        </el-table-column>
        <el-table-column label="凭证" width="70" align="center">
          <template #default="scope">
            <image-preview v-if="scope.row.partyImage" :src="scope.row.partyImage" :width="40" :height="40" />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canSeeSale" label="金额" width="110" align="right">
          <template #default="scope"><span class="amount-in">{{ formatCurrency(scope.row.amount, scope.row.currency || detail.currency) }}</span></template>
        </el-table-column>
        <el-table-column label="财务预填单号" min-width="150">
          <template #default="scope">{{ scope.row.bizCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="录入人" width="90">
          <template #default="scope">{{ scope.row.createUserName || scope.row.createUser || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button link type="primary" icon="Link" :loading="claiming === scope.row.transactionCode"
              @click="claimReceipt(scope.row)">认领到本单</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="hint" style="padding: 12px 0">
            <p>没有待认领的 {{ currencyLabel(detail.currency) }} 打款。</p>
            <p>财务在「资金流水」录入客户回款并提交后才会出现在这里；打进其他币种账户的钱不会列出，本系统不做汇率换算。</p>
          </div>
        </template>
      </el-table>
      <template #footer>
        <el-button @click="claimOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 明细里已有的商品传给弹窗，弹窗会标成「已添加」并锁住，避免开出重复行 -->
    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="pickedGoodsCodes" @confirm="handleGoodsSelected" />

    <!-- ===================== 选择套餐 ===================== -->
    <el-dialog v-model="packageOpen" title="选择套餐" width="880px" append-to-body :close-on-click-modal="false">
      <el-alert type="info" :closable="false" class="mb8"
        title="套餐里的货品按各自数量铺进货品明细，套餐名另外记成一条商品描述"
        description="货品明细里已有的货品不会开成第二行，数量在原行上累加；铺进来之后每一行都是普通货品行，可以改数量、改单价、删行，单价按货品的标准价带出。同时商品描述里会多一行套餐名（数量 1，单价按套餐内标准价合计带出）——客户谈的是「一套」，货品明细摊开的是几个 SKU，留下这一行报价时才对得上话。同一个套餐选两次都不开第二行，数量累加。" />
      <el-input v-model="packageKeyword" placeholder="按套餐代码或名称筛选" clearable class="mb8" style="width: 280px" />
      <el-table v-loading="packageLoading" :data="filteredPackages" height="380"
        empty-text="没有可用的套餐（需要在「订单管理 → 销售套餐」里维护并启用）">
        <el-table-column type="expand">
          <template #default="scope">
            <el-table :data="scope.row.items || []" size="small" border class="inner-table">
              <el-table-column prop="goodsCode" label="商品编码" width="150" />
              <el-table-column prop="goodsName" label="商品名称" min-width="180" />
              <el-table-column prop="spec" label="规格" width="120" />
              <el-table-column prop="unit" label="单位" width="70" align="center" />
              <el-table-column label="数量" width="100" align="right">
                <template #default="line">{{ Number(line.row.qty) }}</template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="packageCode" label="套餐代码" min-width="150" />
        <el-table-column prop="packageName" label="套餐名称" min-width="200" :show-overflow-tooltip="true" />
        <el-table-column label="商品种类" width="100" align="center">
          <template #default="scope">{{ (scope.row.items || []).length }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="applyPackage(scope.row)">加入明细</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="packageOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="SalesOrder">
import { ref, reactive, computed, watch, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listOrder, getOrder, addOrder, updateOrder, confirmOrder, cancelOrder, delOrder,
  addAndSubmitOrder, updateAndSubmitOrder,
  listOrderProductions,
  submitOrder, withdrawOrder, unconfirmOrder, closeOrder
} from '@/api/order/sales'
import { checkPermi } from '@/utils/permission'
import useUserStore from '@/store/modules/user'
import { listBatchesByOrder } from '@/api/order/shipbatch'
import { listActivePackages } from '@/api/order/salespackage'
// 订单展示财务那边的回款流水，并让业务员把待认领的打款认到本单（或撤回）。
// 已收金额本身只在财务终审通过时回写，订单侧没有直接写它的口子
import { listTransactionByBiz, listUnclaimedReceipts, claimTransaction, unclaimTransaction } from '@/api/finance/transaction'
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import BuyerSelect from '@/views/order/components/BuyerSelect.vue'
import { buyer2Options } from '@/api/order/buyer'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import cache from '@/plugins/cache'
// parseTime 只挂在 globalProperties 上，模板里能直接用，script 里必须显式引入
import { parseTime } from '@/utils/ruoyi'
import { platformOptions, storeOptions } from '@/api/order/platform'
import { currencyOptions, currencyLabel, currencyOptionLabel, formatCurrency } from '@/utils/currency'
import { isContactPhone, CONTACT_PHONE_MAX_LENGTH } from '@/utils/validate'
import DecimalInput from '@/components/DecimalInput'
import { formatQty } from '@/utils/decimal'
import { canSeeSale } from '@/utils/price'


const route = useRoute()

const orderStates = ['草稿', '待审核', '已驳回', '已备货', '备货中', '发货中', '已完成', '已取消', '已关闭']
const payStates = ['未支付', '部分支付', '已支付', '超额支付']

// 客户国家。242 个国家/地区，用 RuoYi 字典而不是前端写死的表：
// 译名和增删由运营在「系统管理 → 字典管理」里维护，改一次全系统生效。
// 出厂数据见 sql/migration/20260910_sales_order_export_fields.sql。
const { order_country } = useDict('order_country')

/**
 * 国内收货方式。取值必须与后端 SalesOrder.RECEIVE_TYPE_* 一致，后端会拒认不出的值。
 *
 * 这一段说的是货在国内先发到哪，与买家信息里的「客户地址」（货最终到客户手里的地方）
 * 不是一回事。
 */
const RECEIVE_TYPE_FORWARDER = '货代地址'
const RECEIVE_TYPE_PICKUP = '上门提货'
const receiveTypes = [RECEIVE_TYPE_FORWARDER, '客户地址', RECEIVE_TYPE_PICKUP]
const RECEIVE_TYPE_HINTS = {
  [RECEIVE_TYPE_FORWARDER]: '货交给货代，由货代出口。只有这一种要问唛头——货代靠唛头认箱。',
  客户地址: '发到客户自己在国内的地址。',
  [RECEIVE_TYPE_PICKUP]: '客户自己来仓库拉，货不出仓，收件人、电话、地址都可以不填。'
}

const loading = ref(false), saving = ref(false), showSearch = ref(true)
const total = ref(0)
const createOpen = ref(false), detailOpen = ref(false)
const editing = ref(false)
// 复制建单的来源单号，空串=不是复制来的。只用来在弹窗顶上提示一句，不参与保存
const copyingFrom = ref('')
// 平台留言文本框是不是展开着。纯界面状态，不参与保存；每次打开弹窗复位
const platLeaveExpanded = ref(false)
const closeOpen = ref(false)
const rows = ref([]), detail = ref({ items: [] }), batches = ref([])
const transactions = ref([]), transLoading = ref(false)
// 认领打款弹窗：待认领流水列表，以及正在认领的那一笔（让那一行的按钮转圈、防止连点）
const claimOpen = ref(false), unclaimed = ref([]), unclaimedLoading = ref(false), claiming = ref('')
const productions = ref([]), productionLoading = ref(false)
const warehouses = ref([]), detailTab = ref('items')

/*
  详情页签上的条数角标，作用是「这里有东西，你还没看过」。

  已读状态按订单记在浏览器本地：翻过的页签不再提醒，关掉弹窗、刷新页面、第二天
  再进来都不提醒；等这个页签又多出新记录（又发了一批、又收了一笔），条数超过上次
  看到的数，角标重新变红。

  判据是条数，不是逐条比对——同一个页签删一条又加一条、总数没变，这里认不出来。
  角标只是提示，认不出来的代价是少提醒一次，不值得为它存一份明细快照。

  角标是自己写的 span，没用 el-badge——el-badge 内部把数字包在
  <Transition persisted> + v-show 里，而页签标签是由 TabNav 反复调用 label
  插槽重建的（切页签时 el-tab-pane 的 onBeforeUpdate 会主动 scheduleRender），
  重建时 v-show 留下的 display:none 会粘在复用的 <sup> 上，导致角标该显示时也不显示。
*/
const READ_STORE_KEY = 'sales-order-detail-read'
// 只留最近这么多张订单的已读记录，免得 localStorage 一直长
const READ_STORE_LIMIT = 200
const BADGE_TABS = ['batches', 'productions', 'transactions']

/** 当前详情这张订单上次看到的条数：{ 页签名: 条数 } */
const readCounts = ref({})
const detailOrderCode = ref('')

function tabList(tabName) {
  return { batches, productions, transactions }[tabName]?.value || []
}

/** 存储坏了（手改过、隐私模式、写满了）就当没有已读记录，不能连累详情打不开 */
function loadReadStore() {
  try {
    return cache.local.getJSON(READ_STORE_KEY) || {}
  } catch {
    return {}
  }
}

/** 条数比上次看到的多，才亮角标 */
function showCount(tabName, list) {
  return list.length > (readCounts.value[tabName] || 0)
}

function markTabRead(tabName) {
  if (!BADGE_TABS.includes(tabName) || !detailOrderCode.value) return
  const count = tabList(tabName).length
  if (readCounts.value[tabName] === count) return
  readCounts.value = { ...readCounts.value, [tabName]: count }

  const store = loadReadStore()
  store[detailOrderCode.value] = { t: Date.now(), tabs: { ...readCounts.value } }
  const codes = Object.keys(store)
  if (codes.length > READ_STORE_LIMIT) {
    codes.sort((a, b) => (store[b].t || 0) - (store[a].t || 0))
      .slice(READ_STORE_LIMIT)
      .forEach(code => delete store[code])
  }
  try {
    cache.local.setJSON(READ_STORE_KEY, store)
  } catch {
    // 写不进去就只在本次弹窗里生效，不弹错
  }
}

/*
  切走的那一刻才算「看过」，人还停在这个页签上时角标留着，免得标签在眼前跳一下。

  只认弹窗开着时的切换：openDetail 里把页签拨回「商品明细」也会触发这个 watch，
  那次带上来的 previous 是上一张订单停留的页签，不该算进这一张的已读。
*/
watch(detailTab, (current, previous) => {
  if (previous && detailOpen.value) markTabRead(previous)
})

// 停在某个页签上直接关弹窗，也算看过了——不然下次进来还在提醒同一批数据
watch(detailOpen, open => {
  if (!open) markTabRead(detailTab.value)
})
const platformList = ref([]), storeList = ref([])
/** 当前所选主客户名下启用中的二级客户，换客户时清空重拉 */
const buyer2List = ref([])
const goodsSelectRef = ref(null)
// 选择套餐
const packageOpen = ref(false), packageLoading = ref(false)
const packages = ref([]), packageKeyword = ref('')
const filteredPackages = computed(() => {
  const kw = packageKeyword.value.trim().toLowerCase()
  if (!kw) return packages.value
  return packages.value.filter(p =>
    String(p.packageCode || '').toLowerCase().includes(kw)
    || String(p.packageName || '').toLowerCase().includes(kw))
})
const month = ref(parseTime(new Date(), '{y}-{m}'))

/**
 * 横向权限：看不看得到全店的订单。不勾这个权限的人（业务员）只看得到归属于自己的单。
 *
 * 真正的过滤在服务端（SalesOrderServiceImpl 按 staffcode 筛，详情和改单也各自校验归属），
 * 这里只负责把「业务员账号 / 业务员名称」两个搜索框收起来 —— 只能看自己的单时，
 * 这两个框填什么都只会搜出空，留着纯属误导。
 */
const canSeeAllOrders = computed(
  () => useUserStore().platformAdmin || checkPermi(['order:sales:all'])
)

const query = reactive({ orderCode: undefined, orderState: undefined, payStatus: undefined, staffCode: undefined, staffName: undefined, receivePerson: undefined, pageNum: 1, pageSize: 20 })
const form = ref({ items: [], freightAmount: 0 })
// 关闭用的表单：单号 + 一段必填原因
const reasonForm = reactive({ orderCode: '', tableSuffix: '', reason: '', sendPercent: 0 })

const suffix = () => month.value.replace('-', '')
const percent = v => Math.min(100, Math.round(Number(v || 0)))
const lineAmount = row => Number(row.qty || 0) * Number(row.price || 0)
const goodsAmount = computed(() => form.value.items.reduce((sum, i) => sum + lineAmount(i), 0))
/** 商品描述清单的合计。只给人对照用，不进货款，也不进订单总额 */
const descAmount = computed(() => (form.value.descItems || []).reduce((sum, i) => sum + lineAmount(i), 0))
const receiveTypeHint = computed(() => RECEIVE_TYPE_HINTS[form.value.receiveType] || '')
/**
 * 上门提货：货不出仓、不发快递、不打面单，收件人、电话、地址三样都没有着落处，
 * 一概不强求。另外两种要把货发出去，一样都不能少。与后端 validateHeader 同口径。
 */
const isPickup = computed(() => form.value.receiveType === RECEIVE_TYPE_PICKUP)

/**
 * 换收货方式。不走货代就把唛头连同已传的文件一起清掉——
 * 留着会出现「上门提货却要唛头」这种谁也解释不了的单。后端 applyExportFields 同样会清一遍。
 */
function handleReceiveTypeChange(type) {
  if (type !== RECEIVE_TYPE_FORWARDER) {
    form.value.markNeeded = false
    form.value.markFile = ''
  }
}

function addDescItem() {
  form.value.descItems.push({ name: '', qty: 1, price: 0 })
}
const pickedGoodsCodes = computed(() => form.value.items.map(i => i.goodsCode))
/** 整单是不是都标了专门生产，决定按钮显示「标为」还是「取消」 */
const allCustomMade = computed(() =>
  form.value.items.length > 0 && form.value.items.every(i => i.customMade === true))
/** 整单一键拨：全标上，已经全标了就全取消 */
function toggleAllCustomMade() {
  const next = !allCustomMade.value
  form.value.items.forEach(i => i.customMade = next)
}

function loadList() {
  loading.value = true
  listOrder({ ...query, tableSuffix: suffix() })
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => loading.value = false)
}
/** 换条件、换月份都要回到第一页，否则会停在一个新结果集里根本不存在的页码上 */
function search() {
  query.pageNum = 1
  loadList()
}
function reset() {
  query.orderCode = undefined; query.orderState = undefined
  query.payStatus = undefined; query.staffCode = undefined
  query.staffName = undefined; query.receivePerson = undefined
  search()
}

/**
 * 可删除的状态。与 canEdit 同一个集合：草稿和已驳回都还没过审、没发过货，
 * 删掉不牵连任何下游。已驳回原先删不掉，只能改完重提或者走取消——取消会留下一张
 * 「已取消」的废单占着列表，而这张单本来就没成立过，没有留痕的价值。
 */
const canDelete = row => ['草稿', '已驳回'].includes(row.orderState)
const isPending = row => row.orderState === '待审核'
/** 编辑期：草稿与已驳回都能改、能提交，两者都不占库存 */
const canEdit = row => ['草稿', '已驳回'].includes(row.orderState)
/**
 * 终止动作按状态二选一，每个状态只留一个，与后端 closeOrder / cancelOrder 的白名单同一口径：
 *
 *   草稿、已驳回 → 删除        待审核 → 撤回
 *   已备货、备货中 → 取消      发货中 → 关闭
 *
 * 分界是「有没有发过货」：一件没发就整单取消；发过的既成事实收不回来，关闭只终止剩余部分。
 * 备货中照给取消和撤审：整条链一件货都不占，「货还没凑齐」只是审核那一刻的快照，
 * 不构成终止订单的门槛。真正拦得住的是已经派出去的活儿：后端按在途批次和在制工单挡。
 */
/** 撤审只在没发过货时给：发过货的用「关闭」终止，后端也会再挡一次 */
const canUnconfirm = row => ['已备货', '备货中'].includes(row.orderState) && !Number(row.sendPercent)
const canClose = row => row.orderState === '发货中'
const canCancel = row => ['已备货', '备货中'].includes(row.orderState)

/**
 * 详情里的客户国家：中文名（代码），如「美国（US）」。
 *
 * 字典里查不到的（字典被删过、库里是手改进去的老值）原样把代码显示出来，
 * 不要显示成空——显示成空会让人以为这张单没填过国家。
 */
function countryLabel(code) {
  if (!code) return '—'
  const hit = (order_country.value || []).find(item => item.value === code)
  return hit ? `${hit.label}（${hit.value}）` : code
}

/**
 * 详情里的收件信息。上门提货时收件人/电话/地址都可以留空，
 * 空着就说明为什么空——直接显示成一片空白，看的人只会以为是数据丢了。
 */
function receiveText(value) {
  if (value) return value
  return detail.value.receiveType === RECEIVE_TYPE_PICKUP ? '上门提货，未填' : '—'
}

/** 详情里商品描述清单的合计。金额是后端按数量×单价算好落库的，这里只求和 */
const detailDescAmount = computed(() => (detail.value.descItems || [])
  .reduce((sum, row) => sum + Number(row.amount || 0), 0))

/** 已完成的回款合计。对得上订单的已收金额，对不上就说明有流水没走完终审 */
const completedReceipt = computed(() => transactions.value
  .filter(t => t.status === '已完成')
  .reduce((sum, t) => sum + Math.abs(Number(t.amount || 0)), 0))

function stateType(state) {
  return {
    草稿: 'info', 待审核: 'warning', 已驳回: 'danger',
    已备货: 'primary', 备货中: 'warning', 发货中: 'warning',
    已完成: 'success', 已取消: 'danger', 已关闭: 'info'
  }[state] || 'info'
}
function payType(status) {
  return { 未支付: 'info', 部分支付: 'warning', 已支付: 'success', 超额支付: 'danger' }[status] || 'info'
}
/** 配色口径与 views/order/shipbatch/index.vue 的 statusType 一致，两边改要一起改 */
function batchType(status) {
  return {
    待确认: 'info', 已提交仓库: 'primary', 已打印备货单: 'primary',
    备货完成: 'warning', 已录入快递单号: 'primary', 已打印快递单号: 'primary',
    已完成: 'success', 已取消: 'danger'
  }[status] || 'info'
}
/**
 * 这张批次是不是「被仓库驳回后还没重新提交」。
 *
 * rejectTime 有值只说明历史上被驳回过——驳回三列在批次重走流程后不清空，留作痕迹。
 * 加上「还停在待确认」才是当下要业务员处理的那一张。判据与
 * views/order/shipbatch/index.vue 的 wasRejected 一致，两边改要一起改。
 */
const batchRejected = row => !!row && !!row.rejectTime && row.status === '待确认'
/** 当前待处理的驳回批次，用来决定 tab 顶上那条红色提示要不要出来 */
const rejectedBatches = computed(() => batches.value.filter(batchRejected))
/** 资金流水状态。与财务模块同一套取值，颜色也保持一致 */
function transType(status) {
  return { 草稿: 'info', 待审核: 'warning', 已审核: 'primary', 已完成: 'success', 已驳回: 'danger' }[status] || 'info'
}

// ---------- 建单 / 改单 ----------
function openGoodsSelect() {
  goodsSelectRef.value?.open()
}

/** 商品档案 → 明细行。同一商品开两行会让发货回写失去唯一定位，后端也会拒，所以已存在就跳过 */
function pushItem(goods) {
  if (form.value.items.some(i => i.goodsCode === goods.goodsCode)) return false
  form.value.items.push({
    goodsCode: goods.goodsCode, goodsName: goods.goodsName,
    spec: goods.spec, unit: goods.unit,
    // 新行默认不是定制品：多数订单发通用库存，默认标成定制会让每张单都得排产
    qty: 1, price: Number(goods.standardPrice || 0), customMade: false
  })
  return true
}

function handleGoodsSelected(goodsList) {
  const added = (goodsList || []).filter(goods => pushItem(goods)).length
  if (added) ElMessage.success(`已添加 ${added} 个商品`)
}

// ---------- 选择套餐 ----------
function openPackageSelect() {
  packageOpen.value = true
  // 每次打开都重拉：套餐是别人也在维护的主数据，缓存住会让新建的套餐选不到
  packageLoading.value = true
  packageKeyword.value = ''
  listActivePackages()
    .then(r => packages.value = r.data || [])
    .finally(() => packageLoading.value = false)
}

/**
 * 把套餐里的商品铺进明细。
 *
 * <p>已经在明细里的商品<b>累加数量</b>而不是跳过：套餐的意思是「这些货各要这么多」，
 * 已经手工加过一件再选套餐，期望是变成一件加套餐的量，而不是套餐那份被默默丢掉。
 * 单价只在新开行时按标准价带出，已有行不动——那可能是谈好的价。</p>
 */
function applyPackage(pkg) {
  let created = 0
  let merged = 0
  for (const line of pkg.items || []) {
    const qty = Number(line.qty || 0)
    if (qty <= 0) continue
    const exist = form.value.items.find(i => i.goodsCode === line.goodsCode)
    if (exist) {
      exist.qty = Number(exist.qty || 0) + qty
      merged++
    } else {
      form.value.items.push({
        goodsCode: line.goodsCode, goodsName: line.goodsName,
        spec: line.spec, unit: line.unit,
        qty, price: Number(line.standardPrice || 0), customMade: false
      })
      created++
    }
  }
  if (!created && !merged) {
    ElMessage.warning('这个套餐没有可用的货品行')
    return
  }
  pushPackageDesc(pkg)
  ElMessage.success(`套餐「${pkg.packageName}」已加入：货品明细新增 ${created} 行`
    + (merged ? `，${merged} 行数量已累加` : '') + '，商品描述加了一行套餐名')
  packageOpen.value = false
}

/**
 * 把套餐名加进商品描述清单。
 *
 * <p>客户谈的是「XX 套餐一套」，货品明细里摊开的却是好几个 SKU——两边对不上话。
 * 铺套餐时顺手把套餐名记成一条描述，报给客户的那份就有据可依。</p>
 *
 * <p>单价按套餐内各行的标准价合计带出来，跟货品明细那边同一个口径，
 * 两边合计对得上；谈好的价不一样就直接在描述行上改，这张表本来就是自由填的。
 * 同一个套餐选两次不开第二行，累加数量——与货品明细的处理一致。</p>
 */
function pushPackageDesc(pkg) {
  const name = String(pkg.packageName || pkg.packageCode || '').trim()
  if (!name) return
  const price = (pkg.items || []).reduce(
    (sum, line) => sum + Number(line.qty || 0) * Number(line.standardPrice || 0), 0)
  const exist = form.value.descItems.find(row => String(row.name || '').trim() === name)
  if (exist) {
    exist.qty = Number(exist.qty || 0) + 1
    return
  }
  form.value.descItems.push({ name, qty: 1, price })
}

/**
 * 币种是否已锁死。可改的窗口只有「还没提交过、也没收过钱」的草稿。
 * 已驳回的订单也算提交过——退回来改的是内容不是币种。与后端 updateDraft 同口径。
 */
const currencyLocked = computed(() => editing.value
  && (Number(form.value.paidAmount) > 0 || form.value.orderState !== '草稿'))

/**
 * 建单表单的空白底。
 *
 * <p>每个字段都写出默认值，不让任何一栏落在 undefined 上：el-select / el-switch 收到
 * undefined 会变成非受控，切换单子时留着上一张的内容。新建、复制两条路都以它打底，
 * 复制那条再用原单的字段覆盖过去，于是「哪些字段会被抄」在代码里一眼能数出来。</p>
 */
function emptyForm() {
  return {
    shopStoreCode: '', shopStoreName: '', buyerUser: '',
    buyerCode: '', buyerName: '', buyer2Code: '',
    platCode: '', platName: '', platOrderId: '', platOrderLeave: '',
    buyerCountry: '', buyerAddress: '',
    receivePerson: '', receiveTelephone: '', receiveAddress: '', receiveZipCode: '',
    receiveType: RECEIVE_TYPE_FORWARDER, markNeeded: false, markFile: '',
    freightAmount: 0, currency: 'CNY', remark: '', orderFiles: '',
    items: [], descItems: []
  }
}

function openCreate() {
  editing.value = false
  copyingFrom.value = ''
  platLeaveExpanded.value = false
  storeList.value = []
  buyer2List.value = []
  form.value = emptyForm()
  createOpen.value = true
}

/**
 * 复制建单：把一张已有订单的内容抄进「新建销售订单」弹窗。
 *
 * <p>复制不是「克隆单据」，而是「用旧单的内容新建一张」——保存走的是原来的
 * {@code addOrder}，所以单号由后端另发、落当月表、状态是草稿、业务员是当前登录账号，
 * 原单一个字段都不动。这也是「不影响旧的订单」的兜底：这里只发一次 getOrder 读接口。</p>
 *
 * <p>带过来的：平台/店铺、客户与二级客户、买家账号、客户国家与地址、国内收货信息
 * （收货方式、收件人、电话、地址、邮编、唛头）、商品描述、货品明细（含单价与专门生产标记）、
 * 运费与币种、备注、订单附件、平台留言。</p>
 *
 * <p>不带过来的分三类：
 * 一是<b>属于原来那一单的身份与状态</b>（订单号、月份表后缀、履约/支付/回款状态、已收金额、
 * 发货进度、业务员），新单由后端重新发号、重新按当月落表、重新汇总金额；
 * 二是<b>平台订单号</b>：它是「平台上那一单」的标识，新单在平台上对应的是另一张单，
 * 抄过来就会出现「内部两张单指向同一个平台订单」，日后按平台单号对账、查重复下单都会被这张
 * 假重复带偏。由业务员按真实单号填，留空也不影响建单（这一栏本来就是选填）。
 * 注意平台<b>留言</b>不在此列，它是买家提的要求，照抄——两者一个字之差，含义正相反；
 * 三是<b>挂在旧明细上的下游引用</b>（明细代码、已发数量、备货来源），带上就会让新单的明细
 * 指到旧单的工单和批次上去。金额（货款/总额）也一律不带，保存时后端按明细重算。</p>
 */
function openCopy(row) {
  editing.value = false
  copyingFrom.value = row.orderCode
  platLeaveExpanded.value = false
  storeList.value = []
  buyer2List.value = []
  getOrder(row.orderCode, row.tableSuffix || suffix()).then(r => {
    const data = r.data || {}
    form.value = {
      ...emptyForm(),
      platCode: data.platCode || '', platName: data.platName || '',
      // 平台订单号故意留空：它是「平台上那一单」的标识，抄过来就成了两张内部单指着同一个
      // 平台订单，日后按平台单号对账、查重复下单都会被这张假重复带偏。选填，留空照样能存。
      // 平台留言照抄：它是买家提的要求（发哪家快递、要不要发票），再来一单正是要接着照办的东西。
      platOrderLeave: data.platOrderLeave || '',
      shopStoreCode: data.shopStoreCode || '', shopStoreName: data.shopStoreName || '',
      buyerCode: data.buyerCode || '', buyerName: data.buyerName || '',
      buyer2Code: data.buyer2Code || '', buyerUser: data.buyerUser || '',
      buyerCountry: data.buyerCountry || '', buyerAddress: data.buyerAddress || '',
      receiveType: data.receiveType || RECEIVE_TYPE_FORWARDER,
      receivePerson: data.receivePerson || '', receiveTelephone: data.receiveTelephone || '',
      receiveAddress: data.receiveAddress || '', receiveZipCode: data.receiveZipCode || '',
      // tinyint 回来可能是 0/1，单选框和开关按 true/false 绑，不兜就会两个都选不中
      markNeeded: data.markNeeded === true || data.markNeeded === 1,
      markFile: data.markFile || '',
      freightAmount: Number(data.freightAmount || 0),
      currency: data.currency || 'CNY',
      remark: data.remark || '',
      orderFiles: data.orderFiles || '',
      descItems: (data.descItems || []).map(d => ({
        name: d.name, qty: Number(d.qty || 0), price: Number(d.price || 0)
      })),
      // 只挑建单要的那几项。展开整个 data.items 会把 detailCode、shippedQty、sourceCode
      // 一起带进请求体，而这些是旧单明细的身份和下游引用
      items: (data.items || []).map(i => ({
        goodsCode: i.goodsCode, goodsName: i.goodsName,
        spec: i.spec, unit: i.unit,
        qty: Number(i.qty || 0), price: Number(i.price || 0),
        customMade: i.customMade === true || i.customMade === 1
      }))
    }
    // 二级客户下拉得按抄来的主客户重新拉，否则那一栏显示成一串编码
    loadBuyer2(data.buyerCode)
    preparePlatformStore(data.platCode, data.shopStoreCode, data.platName)
      .finally(() => { createOpen.value = true })
  })
}

/**
 * 选中/清除客户。
 *
 * 只在收货信息还空着的时候用客户档案去填，不覆盖业务员已经敲进去的内容——
 * 同一个客户这单发给别的收件人是常事，覆盖等于把人家改好的地址冲掉。
 * buyerName 只用于本地回显，提交时后端不认它。
 *
 * 换客户必须清掉已选的二级客户，否则会出现「A 客户的订单挂着 B 客户的门店」。
 * 后端 applyBuyerSnapshot 还会再卡一道归属校验，两边都不能少。
 */
function handleBuyerChange(buyer) {
  form.value.buyer2Code = ''
  buyer2List.value = []
  if (!buyer) {
    form.value.buyerName = ''
    return
  }
  form.value.buyerName = buyer.buyerSimple || buyer.buyerFull || buyer.buyerCode
  if (!form.value.buyerUser && buyer.buyerUser) form.value.buyerUser = buyer.buyerUser
  if (!form.value.receivePerson && buyer.contactPerson) form.value.receivePerson = buyer.contactPerson
  if (!form.value.receiveTelephone && buyer.mobile) form.value.receiveTelephone = buyer.mobile
  loadBuyer2(buyer.buyerCode)
}

/** 拉某个主客户名下启用中的二级客户，供上面的下拉用 */
function loadBuyer2(buyerCode) {
  if (!buyerCode) {
    buyer2List.value = []
    return Promise.resolve()
  }
  return buyer2Options(buyerCode).then(r => { buyer2List.value = r.data || [] })
}

function loadPlatforms() {
  return platformOptions().then(r => { platformList.value = r.data || [] })
}

function loadStores(platformCode, selectedStoreCode) {
  storeList.value = []
  if (!platformCode) return Promise.resolve()
  return storeOptions(platformCode).then(r => {
    storeList.value = r.data || []
    if (selectedStoreCode && !storeList.value.some(item => item.storeCode === selectedStoreCode)) {
      storeList.value.push({ storeCode: selectedStoreCode, storeName: form.value.shopStoreName || selectedStoreCode, isActive: 0 })
    }
  })
}

function handlePlatformChange(platformCode) {
  const platform = platformList.value.find(item => item.platformCode === platformCode)
  form.value.platName = platform?.platformName || ''
  form.value.shopStoreCode = ''
  form.value.shopStoreName = ''
  loadStores(platformCode)
}

function handleStoreChange(storeCode) {
  const store = storeList.value.find(item => item.storeCode === storeCode)
  form.value.shopStoreName = store?.storeName || ''
}

/**
 * 建单弹窗里平台、店铺两个下拉的预加载。
 *
 * <p>单子上记的是建单当时的代码快照，平台或店铺后来被停用了，下拉里就找不到它——
 * 直接把历史值补成一个选项，让表单显示得出来，而不是留一片看得见代码、选不中任何一项的空白。
 * 补进去的标 isActive: 0，停用的语义没被抹掉：保存时后端照旧拦，界面上也还是停用色。</p>
 *
 * <p>改单和复制建单走同一条路。调用前 form 必须已经填好：loadStores 兜底造选项时
 * 要读 form.value.shopStoreName。</p>
 */
function preparePlatformStore(platCode, storeCode, platName) {
  const known = platformList.value.some(item => item.platformCode === platCode)
  return (known ? Promise.resolve() : loadPlatforms()).then(() => {
    if (platCode && !platformList.value.some(item => item.platformCode === platCode)) {
      platformList.value.push({ platformCode: platCode, platformName: platName || platCode, isActive: 0 })
    }
    return loadStores(platCode, storeCode)
  })
}

function openEdit(row) {
  editing.value = true
  copyingFrom.value = ''
  platLeaveExpanded.value = false
  getOrder(row.orderCode, row.tableSuffix || suffix()).then(r => {
    const data = r.data || {}
    form.value = {
      ...data, freightAmount: Number(data.freightAmount || 0),
      // customMade 兜成布尔：custom_made 是 tinyint，回来可能是 0/1，
      // el-switch 收到数字 0 会当成真值一直显示成开
      items: (data.items || []).map(i => ({ ...i, customMade: i.customMade === true || i.customMade === 1 })),
      // markneeded 同样是 tinyint，单选框按 true/false 绑，0/1 会两个都选不中
      receiveType: data.receiveType || RECEIVE_TYPE_FORWARDER,
      markNeeded: data.markNeeded === true || data.markNeeded === 1,
      markFile: data.markFile || '',
      orderFiles: data.orderFiles || '',
      // 后端存的是 JSON 文本，接口里已经还原成数组；老单没有这一列，兜成空数组
      descItems: (data.descItems || []).map(d => ({
        name: d.name, qty: Number(d.qty || 0), price: Number(d.price || 0)
      }))
    }
    // 改单时要把这个客户的二级客户先拉回来，否则下拉是空的，
    // 已选的 buyer2Code 显示成一串编码，业务员一保存就以为自己没选过
    loadBuyer2(data.buyerCode)
    preparePlatformStore(data.platCode, data.shopStoreCode, data.platName)
      .finally(() => { createOpen.value = true })
  })
}

/**
 * 保存。<b>只落库，不提交</b>——提交在「详情」弹窗底部，见 {@link submit}。
 *
 * <p>原先这里有个「保存并提交」，是「先存后提」两个接口，第二步失败就停在草稿、
 * 只弹一句话。提交挪走之后这种两步半成功的状态也一并没了：这里成了就是成了。</p>
 */
/**
 * 保存。submit 为 true 走「提交审核 / 保存并提交」：后端同一个事务里落库并置待审核，
 * 失败什么都不留；为 false 只落草稿（改单时只改内容）。
 */
function save(submit = false) {
  if (!form.value.platCode) { ElMessage.warning('请选择平台'); return }
  if (!form.value.shopStoreCode) { ElMessage.warning('请选择店铺'); return }
  // 上门提货三样都不强求；另外两种要把货发出去，一样都不能少
  if (!isPickup.value) {
    if (!form.value.receivePerson || !form.value.receiveTelephone) {
      ElMessage.warning('收件人和收件电话都要填'); return
    }
    if (!form.value.receiveAddress) {
      ElMessage.warning('收件地址要填；客户自己来提货的话，把收货方式改成「上门提货」'); return
    }
  }
  // 这张表没挂 el-form 的 rules，手工校验一下格式，口径与后端 ContactPhoneUtils 一致。
  // 上门提货可以不填电话，但填了就得像一串电话
  if (form.value.receiveTelephone && !isContactPhone(form.value.receiveTelephone)) {
    ElMessage.warning('收件电话只能填数字、加号、横杠、空格，多个号码用逗号或分号分隔'); return
  }
  if (form.value.receiveType === RECEIVE_TYPE_FORWARDER && form.value.markNeeded && !form.value.markFile) {
    ElMessage.warning('唛头选了「需要」，请上传唛头文件'); return
  }
  // 名称是描述行唯一的内容，后端会把没名称的行整行丢掉。与其静悄悄地丢，不如在这里说清楚
  if (form.value.descItems.some(d => !String(d.name || '').trim())) {
    ElMessage.warning('商品描述里有没填名称的行，请补上名称或删掉该行'); return
  }
  if (!form.value.items.length) { ElMessage.warning('请至少添加一条货品明细'); return }
  if (form.value.items.some(i => !Number(i.qty))) { ElMessage.warning('货品明细数量必须大于 0'); return }

  saving.value = true
  // 改单的单已经有自己的月份，原样带回去；缺了才退回列表上正在看的那个月。
  // 新建不用管月份：落哪张月表由后端按建单时间定
  const month = form.value.tableSuffix || suffix()
  const action = editing.value
    ? (submit
        ? updateAndSubmitOrder(form.value, month).then(() => '订单已修改并提交审核')
        : updateOrder(form.value, month).then(() => '订单已修改'))
    : (submit ? addAndSubmitOrder(form.value) : addOrder(form.value))
        .then(r => r.msg || (submit ? '订单已创建并提交审核' : '订单已创建'))

  action
    .then(msg => { ElMessage.success(msg); createOpen.value = false; loadList() })
    .finally(() => saving.value = false)
}

// ---------- 审核通过 ----------
/**
 * 「审核」。唯一入口是详情弹窗底部那颗，列表的操作列上没有。
 *
 * <p>确认框留着：这一步过后仓库就能建发货批次，而「撤审」只在一件都没发时还给得起
 * （见 canUnconfirm），发过货就撤不回来了。它只把订单号念一遍，不妨碍看内容。</p>
 *
 * @param row 详情里那张订单，带 orderCode 与 tableSuffix
 */
function confirmOrderRow(row) {
  ElMessageBox.confirm(
    `审核通过订单 ${row.orderCode}？通过后仓库就能建发货批次了。`, '提示',
    { closeOnClickModal: false })
    .then(() => doConfirm(row))
    .catch(() => {})
}

function doConfirm(row) {
  saving.value = true
  confirmOrder(row.orderCode, row.tableSuffix || suffix())
    .then(r => {
      // 落已备货还是备货中由后端判定，直接用它返回的状态，别在前端重算
      const order = r.data || {}
      if (order.orderState === '备货中') {
        // 只是告知，不是拦截：备货中照样能建发货批次、也照样能撤审/关闭/取消
        ElMessage.warning('订单已审核通过，转「备货中」：还有明细的货没凑齐，不影响后续发货')
      } else {
        ElMessage.success('订单已审核通过')
      }
      loadList()
      // 弹窗不关——刚审完常常还要接着翻明细和批次。但顶上那个履约状态得跟着变，
      // 否则单子已经审过了，摊开的这一份还写着「待审核」，底下那颗按钮也还在
      reloadDetail()
    })
    .finally(() => saving.value = false)
}

/**
 * 重新拉一遍当前详情的订单主体。
 *
 * <p>只拉订单本身，不重拉发货批次 / 工单 / 资金流水：审核只动履约状态，那三份都没变，
 * 跟着重拉是白跑三个接口。也不碰 detailTab，别把人正看着的页签跳回去。</p>
 *
 * <p>tableSuffix 取详情里那一份：后端 selectOrderDetail 和 findOrderAcrossMonths 都会回填它，
 * 所以即便是跨月找到的单，这里也拿得到准确月份，不必再跨月找一遍。</p>
 */
function reloadDetail() {
  getOrder(detail.value.orderCode, detail.value.tableSuffix || undefined).then(r => {
    if (!r.data) return
    detail.value = { ...r.data, items: r.data.items || [], descItems: r.data.descItems || [] }
  })
}

// ---------- 详情 ----------
function openDetail(row) {
  // 列表行都带着 tableSuffix；没带的（比如从财务模块跳过来的）留空，让后端跨月找
  const tableSuffix = row.tableSuffix || undefined
  // 取这张订单上次看到的条数，比它多才提醒
  detailOrderCode.value = row.orderCode
  readCounts.value = loadReadStore()[row.orderCode]?.tabs || {}
  detailTab.value = 'items'
  getOrder(row.orderCode, tableSuffix).then(r => {
    // items 兜成数组：el-table 的 :data 收到 null 会在渲染时抛，
    // 而接口异常返回 data=null 时页面已经打开了，看到的是白屏而不是报错
    detail.value = { ...(r.data || {}), items: r.data?.items || [], descItems: r.data?.descItems || [] }
    detailOpen.value = true
  })
  // 这个和下面的工单、流水一样：查不到就当没有，不阻断详情展示，也别留下未处理的 rejection
  listBatchesByOrder(row.orderCode)
    .then(r => batches.value = r.data || [])
    .catch(() => batches.value = [])
  loadProductions(row.orderCode)
  loadTransactions(row.orderCode)
}

/** 该订单关联的生产工单。查不到就当没有，不阻断详情展示 */
function loadProductions(orderCode) {
  productions.value = []
  productionLoading.value = true
  listOrderProductions(orderCode)
    .then(r => productions.value = r.data || [])
    .finally(() => productionLoading.value = false)
}

/**
 * 这张工单里分给<b>当前这张订单</b>的数量。
 *
 * <p>一张工单可以同时服务多张订单，orderAllocation 里是全部分配；只累加订单号对得上的。
 * 后端存的是 JSON 文本，解析失败就显示「—」，不要让详情页因为一条脏数据整个崩掉。</p>
 */
function allocatedToThisOrder(row) {
  try {
    const list = JSON.parse(row.orderAllocation || '[]')
    const total = list
      .filter(item => item.orderCode === detail.value.orderCode)
      .reduce((sum, item) => sum + Number(item.qty || 0), 0)
    return total ? formatQty(total) : '—'
  } catch {
    return '—'
  }
}

function productionType(status) {
  return { 待生产: 'info', 待领料: 'warning', 生产中: 'warning', 已完工: 'success', 已关闭: 'danger' }[status] || 'info'
}

/** 该订单的回款流水。查不到就当没有，不阻断详情展示 */
function loadTransactions(orderCode) {
  transactions.value = []
  transLoading.value = true
  listTransactionByBiz('销售', orderCode)
    .then(r => transactions.value = r.data || [])
    .catch(() => transactions.value = [])
    .finally(() => transLoading.value = false)
}

// ---------- 认领打款 / 撤回认领 ----------
/** 订单还能不能收款：草稿还没成立、已取消的作废。与后端 requireSalesOrder 同一口径 */
const claimable = computed(() => !!detail.value.orderCode
  && detail.value.orderState !== '草稿' && detail.value.orderState !== '已取消')
/** 待收 = 应收 − 已收，与后端 requireWithinReceivable 同口径 */
const receivableRemaining = computed(() =>
  Number(detail.value.totalAmount || 0) - Number(detail.value.paidAmount || 0))

function openClaim() {
  claimOpen.value = true
  unclaimed.value = []
  unclaimedLoading.value = true
  listUnclaimedReceipts(detail.value.currency)
    .then(r => unclaimed.value = r.data || [])
    .finally(() => unclaimedLoading.value = false)
}

/**
 * 把一笔打款认到本单。超过待收先让人确认，确认了带 allowOverpay 放行——
 * 真正的拦截在后端 requireWithinReceivable，这里只是提前把话说清楚，两边口径要一致。
 * 认领后流水进「已审核」，还要财务终审到「已完成」才会回写已收金额，所以这里只刷新流水页签。
 */
function claimReceipt(row) {
  const amount = Math.abs(Number(row.amount || 0))
  const hint = amount > receivableRemaining.value
    ? `本笔打款 ${formatCurrency(amount, row.currency || detail.value.currency)} 超过本单待收 `
      + `${formatCurrency(receivableRemaining.value, detail.value.currency)}。\n\n`
      + '超额认领终审后订单会变成「超额收款」，多出来的钱就没法再挂到别的单上了。'
      + '如果这笔钱其实是几张单合并打的，请让财务按订单拆成多笔流水再认。确定要放行吗？'
    : ''
  const confirmed = hint
    ? ElMessageBox.confirm(hint, '超过待收', { type: 'warning' }).then(() => true)
    : Promise.resolve(false)
  confirmed
    .then(allowOverpay => {
      claiming.value = row.transactionCode
      return claimTransaction(row.transactionCode, {
        tableSuffix: row.tableSuffix, bizCode: detail.value.orderCode, allowOverpay
      })
    })
    .then(() => {
      ElMessage.success('认领成功，财务终审通过后才会计入已收金额')
      unclaimed.value = unclaimed.value.filter(t => t.transactionCode !== row.transactionCode)
      loadTransactions(detail.value.orderCode)
    })
    // 在超额确认框上点取消会走到这里，不当成错误
    .catch(() => {})
    .finally(() => claiming.value = '')
}

/** 撤回只给「已认领、还没终审」的行，且只有认领人本人或财务审核人能撤。与后端 unclaimTransaction 同一口径 */
function canUnclaim(row) {
  if (row.status !== '已审核') return false
  return row.completeUser === useUserStore().name || checkPermi(['finance:transaction:audit'])
}

function unclaimReceipt(row) {
  ElMessageBox.confirm(`撤回对流水「${row.transactionCode}」的认领？撤回后这笔钱退回待认领，订单号清空。`,
    '撤回认领', { type: 'warning' })
    .then(() => unclaimTransaction(row.transactionCode, row.tableSuffix))
    .then(() => {
      ElMessage.success('已撤回认领')
      loadTransactions(detail.value.orderCode)
    })
    .catch(() => {})
}

// ---------- 提交 / 撤回 / 撤审 / 关闭 ----------
/**
 * 「提交审核」。唯一入口是详情弹窗底部那颗，列表和建单弹窗里都没有。
 *
 * <p>不拦二次确认：提交可逆——待审核还能「撤回」退回草稿，也没占过库存，
 * 再弹一个框纯属碍事。真正要拦的是不看就提交，那靠「必须先摊开详情」来拦。</p>
 */
function submit(row) {
  saving.value = true
  submitOrder(row.orderCode, row.tableSuffix || suffix())
    .then(() => {
      ElMessage.success('已提交，等待审核')
      loadList()
      // 同 doConfirm：弹窗不关，但履约状态得跟着变——底下这颗按钮要从「提交审核」
      // 换成「审核通过」，不刷新的话点完还杵在那儿，会让人以为没提交上
      reloadDetail()
    })
    .finally(() => saving.value = false)
}

// 撤回不确认：待审核没占过库存，退回草稿没有任何副作用，弹窗只会碍事
function withdraw(row) {
  withdrawOrder(row.orderCode, row.tableSuffix || suffix())
    .then(() => { ElMessage.success('已撤回，订单退回草稿'); loadList() })
}

function unconfirm(row) {
  ElMessageBox.confirm(
    '撤审会把订单退回草稿，是否继续？', '提示', { closeOnClickModal: false })
    .then(() => unconfirmOrder(row.orderCode, row.tableSuffix || suffix()))
    .then(() => { ElMessage.success('已撤审，订单退回草稿'); loadList() })
    .catch(() => {})
}

function openClose(row) {
  reasonForm.orderCode = row.orderCode
  reasonForm.tableSuffix = row.tableSuffix || suffix()
  reasonForm.reason = ''
  reasonForm.sendPercent = percent(row.sendPercent)
  closeOpen.value = true
}
function doClose() {
  if (!reasonForm.reason.trim()) { ElMessage.warning('请填写关闭原因'); return }
  saving.value = true
  closeOrder(reasonForm.orderCode, reasonForm.reason.trim(), reasonForm.tableSuffix)
    .then(() => {
      ElMessage.success('订单已关闭')
      closeOpen.value = false
      loadList()
    })
    .finally(() => saving.value = false)
}

// ---------- 取消 / 删除 / 巡检 ----------
function cancel(row) {
  ElMessageBox.confirm('取消订单，是否继续？', '提示', { closeOnClickModal: false })
    .then(() => cancelOrder(row.orderCode, row.tableSuffix || suffix()))
    .then(() => { ElMessage.success('订单已取消'); loadList() })
    .catch(() => {})
}
function remove(row) {
  ElMessageBox.confirm('确认删除该订单？没过审的单删掉后不留记录。', '提示', { closeOnClickModal: false })
    .then(() => delOrder(row.orderCode, row.tableSuffix || suffix()))
    .then(() => { ElMessage.success('订单已删除'); loadList() })
    .catch(() => {})
}
onMounted(() => {
  applyRouteFilter()
  loadList()
  loadPlatforms()
  listWarehouse({ pageSize: 100 }).then(r => warehouses.value = r.rows || [])
  openFromRoute()
})

/**
 * 认首页看板带过来的 ?orderState=待审核&month=2026-09。
 *
 * 状态和月份界面上本来就有，这里只是把 URL 参数灌进去，让用户落地时就看见筛选条上
 * 写着「待审核 / 2026-09」，而不是拿到一个不知道被谁筛过的列表。
 *
 * 月份必须跟着一起带：这张列表是按月表查的，看板数出来的待审核单可能是上个月建的，
 * 只灌状态不灌月份，用户会落在当月的空列表上——数字说有 3 张，一张都不显示。
 * 格式不对的月份直接忽略：suffix() 会把它原样拼进表名，后端拿到非法后缀会抛。
 *
 * @returns 是否真的改了筛选条件——没改就不必额外发一次请求
 */
function applyRouteFilter() {
  let changed = false
  const routeState = route.query.orderState
  if (routeState && query.orderState !== routeState) {
    query.orderState = routeState
    changed = true
  }
  const routeMonth = route.query.month
  if (routeMonth && /^\d{4}-\d{2}$/.test(routeMonth) && month.value !== routeMonth) {
    month.value = routeMonth
    changed = true
  }
  if (changed) query.pageNum = 1
  return changed
}

/**
 * 从财务资金流水的业务单号点过来时，直接把那张订单的详情打开。
 *
 * <p>不走列表过滤：列表是按月表查的，而流水与订单常常不在同一个月，
 * 按当月筛只会得到一个空列表。详情接口不带月份就会跨月找。</p>
 */
function openFromRoute() {
  const orderCode = route.query.orderCode
  if (!orderCode) return
  query.orderCode = orderCode
  openDetail({ orderCode })
}

// 本页被 keep-alive 缓存，从发货批次页切回来时 onMounted 不会再触发，
// 列表会停在离开前的快照——刚发完货，这里还显示旧的发货进度。
// onActivated 首次挂载后也会触发一次，用标志位跳过，避免重复请求。
let skipFirstActivate = true
onActivated(() => {
  if (skipFirstActivate) { skipFirstActivate = false; return }
  // 第二次从看板点进来时组件不重新初始化，只有这里会触发，筛选条件得在这儿再灌一次
  applyRouteFilter()
  loadList()
})
</script>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
/* 还没处理的驳回原因用主题的危险色标出来，已经重走过流程的那些保持正文色——
   同一列里两种含义，不靠颜色区分就得靠人逐行去对状态 */
.reject-reason {
  color: var(--el-color-danger);
}
.text-muted {
  color: var(--el-text-color-secondary);
}
/* 表头文字后面跟的问号图标，跟着文字走 */
.head-tip {
  margin-left: 2px;
  color: var(--el-text-color-secondary);
  vertical-align: middle;
  cursor: help;
}
/*
  平台留言的「展开 / 收起」。

  el-form-item__content 是个 flex-wrap 容器，文本框自己就是 width:100%，
  所以这条只能靠 flex-basis:100% 独占一行、再靠 justify-content 靠右——
  光给它 text-align 是不行的，它是 flex item 不是行内内容。
*/
.field-expand {
  flex: 1 0 100%;
  display: flex;
  justify-content: flex-end;
}
.inner-table {
  margin: 8px 16px 12px 48px;
  width: auto;
}
.shortage {
  color: var(--el-color-danger);
  font-weight: 600;
}
.reason-text {
  color: var(--el-color-danger);
  white-space: pre-wrap;
}
.amount-in {
  color: var(--el-color-success);
  font-weight: 600;
}
.mt8 { margin-top: 8px; }
.ml8 { margin-left: 8px; }

/*
  页签上的条数角标。写成 :deep() 是因为标签内容最终由 el-tabs 内部的 TabNav
  渲染，普通的 scoped 选择器选不到它。
*/
:deep(.el-tabs__item) .tab-count {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 12px;
  font-weight: normal;
  color: #fff;
  background-color: var(--el-color-danger);
  border-radius: 9px;
  vertical-align: middle;
}
/*
  「有批次被驳回」的页签标记。和上面的条数角标并排，所以做成描边而不是实心——
  两个都填成危险色的实心块会分不清哪个是条数、哪个是状态。
*/
:deep(.el-tabs__item) .tab-flag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  height: 18px;
  line-height: 16px;
  font-size: 12px;
  font-weight: normal;
  color: var(--el-color-danger);
  border: 1px solid var(--el-color-danger);
  border-radius: 9px;
  vertical-align: middle;
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
/*
  详情里只读的附件列表。FileUpload 在 disabled 下只渲染文件行，
  但它自带的上下留白是按「上传控件」排的，塞进 el-descriptions 的格子里太空。
*/
.detail-files :deep(.upload-file-list),
.detail-files :deep(.upload-file-cards) {
  margin: 0;
}
/* 大节（section-title）下面的小标题，比如「订单明细」里的商品描述和货品明细两块 */
.sub-title {
  margin: 16px 0 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}
.amount-summary {
  line-height: 32px;
  color: var(--el-text-color-regular);
}
.amount-summary .total {
  color: var(--el-color-primary);
  font-size: 16px;
}
:deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>

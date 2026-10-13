<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      :title="$t('goods.lead.title')"
      :description="$t('goods.lead.desc')" />

    <el-form :inline="true" :model="query" v-show="showSearch">
      <el-form-item :label="$t('goods.col.product')">
        <el-input v-model="query.keyword" clearable :placeholder="$t('goods.search.keyword')" style="width: 190px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <!--
          ⚠ value 是库里那三个中文串，只翻 label：publishStatus 存的就是
            '草稿'/'已上架'/'已下架'，前台和后端都按这个值判。
        -->
        <el-select v-model="query.publishStatus" clearable style="width: 120px" @change="search">
          <el-option v-for="s in statuses" :key="s" :label="statusLabel(s)" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('goods.col.category')">
        <el-input v-model="query.category" clearable style="width: 140px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="reset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <!--
      站点语言不是装饰字段：文案按语言分开存，接口也按站点语言取。
      站点语言是 de、文案填在 en 下面，站上显示的就会是 ERP 中文品名。
    -->
    <el-alert type="warning" :closable="false" class="mb8" show-icon
      :title="$t('goods.langBar.title', { langs: identity.langs.join('、') })"
      :description="$t('goods.langBar.desc')" />

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus"
          v-hasPermi="['site:goods:add']" @click="openCreate">{{ $t('goods.new') }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Top" :disabled="!selection.length"
          v-hasPermi="['site:goods:edit']" @click="batchStatus('已上架')">{{ $t('goods.publish') }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Bottom" :disabled="!selection.length"
          v-hasPermi="['site:goods:edit']" @click="batchStatus('已下架')">{{ $t('goods.unpublish') }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!selection.length"
          v-hasPermi="['site:goods:remove']" @click="removeSelected">{{ $t('common.delete') }}</el-button>
      </el-col>
      <!--
        分类译名放在这里、而不是放进商品编辑弹窗里：分类是**全站共享**的，
        同一个分类挂在十个商品上、译名只有一份。放进单个商品的弹窗会让人以为
        「我只改了这个商品」，实际上十个都变了。一张全站的表既符合它真实的作用域，
        也能一眼看出哪个翻歪了。
      -->
      <el-col :span="1.5">
        <el-button plain icon="Notebook"
          v-hasPermi="['site:goods:edit']" @click="openCatLang">{{ $t('goods.catLang') }}</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="search" />
    </el-row>

    <el-table v-loading="loading" :data="rows" @selection-change="rows => selection = rows">
      <el-table-column type="selection" width="46" />
      <el-table-column :label="$t('goods.col.image')" width="70">
        <template #default="scope">
          <el-image v-if="rowThumb(scope.row)" :src="rowThumb(scope.row)" fit="cover" class="thumb"
            :preview-src-list="[rowThumb(scope.row)]" preview-teleported />
          <span v-else class="no-img">{{ $t('goods.noImage') }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="goodsCode" :label="$t('goods.col.code')" width="120" />
      <el-table-column prop="goodsName" :label="$t('goods.col.name')" min-width="150" :show-overflow-tooltip="true" />
      <!-- 站点标题取站点默认语言那一条。空着说明还没填，上架会被后端挡下来 -->
      <el-table-column :label="$t('goods.col.title')" min-width="220" :show-overflow-tooltip="true">
        <template #default="scope">
          <span v-if="scope.row.title">{{ scope.row.title }}</span>
          <span v-else class="warn-text">{{ $t('goods.noTitle') }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="siteCategory" :label="$t('goods.col.category')" width="120">
        <template #default="scope">{{ scope.row.siteCategory || $t('common.none') }}</template>
      </el-table-column>
      <el-table-column :label="$t('goods.col.price')" width="100" align="right">
        <template #default="scope">{{ formatCurrency(scope.row.price, siteCurrency) }}</template>
      </el-table-column>
      <el-table-column :label="$t('goods.col.moq')" width="100" align="right">
        <template #default="scope">{{ scope.row.moq ? Number(scope.row.moq) + ' ' + (scope.row.unit || '') : $t('common.none') }}</template>
      </el-table-column>
      <el-table-column :label="$t('goods.col.featured')" width="70" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.featured" size="small" type="warning">{{ $t('goods.featuredTag') }}</el-tag>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="publishStatus" :label="$t('common.status')" width="90">
        <template #default="scope">
          <el-tag size="small" :type="statusType(scope.row.publishStatus)">{{ statusLabel(scope.row.publishStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortNum" :label="$t('goods.col.sort')" width="70" align="center" />
      <el-table-column :label="$t('common.operate')" width="200" fixed="right">
        <template #default="scope">
          <el-button link type="primary" v-hasPermi="['site:goods:edit']" @click="openEdit(scope.row)">{{ $t('common.edit') }}</el-button>
          <el-button link type="success" v-if="scope.row.publishStatus !== '已上架'"
            v-hasPermi="['site:goods:edit']" @click="batchStatus('已上架', scope.row)">{{ $t('goods.publish') }}</el-button>
          <el-button link type="warning" v-else
            v-hasPermi="['site:goods:edit']" @click="batchStatus('已下架', scope.row)">{{ $t('goods.unpublish') }}</el-button>
          <el-button link type="danger" v-hasPermi="['site:goods:remove']" @click="removeSelected(scope.row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
      v-model:limit="query.pageSize" @pagination="loadList" />

    <!-- ===================== 新建商品 ===================== -->
    <!--
      ⚠ 这里以前是 <goods-select-dialog>：一个从 ERP 商品档案里挑商品的选择器。

      整个删了（docs/独立站与ERP的边界.md §5.4）。取商品名和图片那条
      `join sdsshop.u_m_waregoods` 是一条跨库互通，库搬到这台机器之后连语法上都不成立，
      所以商品从「挑」变成「录」——这个表单就是那个改动在界面上的样子。
    -->
    <el-dialog v-model="createOpen" :title="$t('goods.new')" width="560px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="90px">
        <el-form-item :label="$t('goods.col.code')" required>
            <el-input v-model="createForm.goodsCode" maxlength="50" :placeholder="$t('goods.create.codePlaceholder')" />
          <div class="hint" v-html="$t('goods.create.codeHint')"></div>
        </el-form-item>
        <el-form-item :label="$t('goods.col.name')" required>
          <el-input v-model="createForm.goodsName" maxlength="200" :placeholder="$t('goods.create.namePlaceholder')" />
          <div class="hint">{{ $t('goods.create.nameHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('goods.field.spec')">
          <el-input v-model="createForm.spec" maxlength="200" :placeholder="$t('common.optional')" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item :label="$t('goods.col.price')">
            <el-input-number v-model="createForm.price" :min="0" :precision="4" controls-position="right" style="width:100%" />
            <div class="hint">{{ siteCurrency }}{{ $t('goods.create.priceHint') }}</div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('goods.field.unit')">
            <el-input v-model="createForm.unit" maxlength="20" :placeholder="$t('goods.field.unitPlaceholder')" />
          </el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('goods.field.siteCategory')">
          <el-input v-model="createForm.siteCategory" maxlength="100" :placeholder="$t('goods.field.siteCategoryPlaceholder')" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
          :title="$t('goods.create.draftTitle')"
          :description="$t('goods.create.draftDesc')" />
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">{{ $t('goods.create.submit') }}</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 编辑 ===================== -->
    <el-dialog v-model="editOpen" :title="$t('goods.edit.title')" width="980px" :close-on-click-modal="false" destroy-on-close>
      <el-descriptions :column="3" border size="small" class="mb8">
        <el-descriptions-item :label="$t('goods.col.code')">{{ form.goodsCode }}</el-descriptions-item>
      </el-descriptions>

      <!--
        ⚠ 名称和规格现在是**可改的**。

        它们以前是 ERP 商品档案上的字段，join 出来只读展示；现在是本表的列（§5.4），
        由站点自己维护。所以从 el-descriptions 里挪出来，变成下面表单里的输入框。
      -->

      <el-form :model="form" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item :label="$t('goods.col.name')">
            <el-input v-model="form.goodsName" maxlength="200" />
            <div class="hint">{{ $t('goods.edit.nameHint') }}</div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('goods.field.spec')">
            <el-input v-model="form.spec" maxlength="200" />
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item :label="$t('goods.field.siteCategory')">
            <el-input v-model="form.siteCategory" maxlength="100" :placeholder="$t('goods.field.siteCategoryPlaceholder')" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('goods.col.price')">
            <el-input-number v-model="form.price" :min="0" :precision="4" controls-position="right" style="width:100%" />
            <div class="hint">{{ siteCurrency }}{{ $t('goods.edit.priceHint') }}</div>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('goods.col.moq')">
            <el-input-number v-model="form.moq" :min="0" :precision="2" controls-position="right" style="width:100%" />
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8"><el-form-item :label="$t('goods.field.unit')">
            <el-input v-model="form.unit" maxlength="20" :placeholder="$t('goods.field.unitPlaceholder')" />
            <div class="hint">{{ $t('goods.edit.unitHint') }}</div>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('goods.col.sort')">
            <el-input-number v-model="form.sortNum" :min="0" :precision="0" controls-position="right" style="width:100%" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('common.status')">
            <el-select v-model="form.publishStatus" style="width:100%">
              <el-option v-for="s in statuses" :key="s" :label="statusLabel(s)" :value="s" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('goods.field.featured')">
          <el-switch v-model="form.featured" :active-value="1" :inactive-value="0" />
          <span class="hint ml8">{{ $t('goods.field.featuredHint') }}</span>
        </el-form-item>

        <!-- 主图视频排在图片之前：它在前台就是轮播的第一格，编辑顺序跟展示顺序对上 -->
        <div class="section-title">{{ $t('goods.media.mainVideo') }}</div>
        <div class="hint mb8" v-html="$t('goods.media.mainVideoHint')"></div>
        <div class="site-imgs">
          <div v-if="siteMainVideo" class="site-img site-video">
            <!-- poster 跟前台一样取第一张图：这里能直接看到买家会看到的封面 -->
            <video class="site-img-inner" preload="metadata" controls
              :src="siteMainVideo" :poster="siteImgList[0] || undefined" />
            <span class="site-img-main site-img-main--top">{{ $t('goods.media.mainVideoBadge') }}</span>
            <el-button class="site-img-del" link type="danger" icon="Close"
              @click="siteMainVideo = ''" />
          </div>
          <el-button v-else class="site-img-add" @click="mainVideoPickerOpen = true">
            <div><el-icon><Plus /></el-icon><div class="hint">{{ $t('goods.media.pickMainVideo') }}</div></div>
          </el-button>
        </div>

        <div class="section-title mt20">{{ $t('goods.media.images') }}</div>
        <div class="hint mb8" v-html="$t('goods.media.imagesHint')"></div>
        <div class="site-imgs">
          <div v-for="(url, index) in siteImgList" :key="url" class="site-img">
            <el-image :src="url" fit="contain" class="site-img-inner"
              :preview-src-list="siteImgList" :initial-index="index" preview-teleported />
            <span v-if="index === 0" class="site-img-main">{{ $t('goods.media.mainImage') }}</span>
            <el-button class="site-img-del" link type="danger" icon="Close"
              @click="siteImgList.splice(index, 1)" />
          </div>
          <el-button class="site-img-add" @click="pickerOpen = true">
            <div><el-icon><Plus /></el-icon><div class="hint">{{ $t('goods.media.pickImage') }}</div></div>
          </el-button>
        </div>


        <div class="section-title mt20">{{ $t('goods.media.videos') }}</div>
        <div class="hint mb8">{{ $t('goods.media.videosHint') }}</div>
        <div class="site-imgs">
          <div v-for="(url, index) in siteVideoList" :key="url" class="site-img site-video">
            <!-- 列表里的视频给 controls，这里是已选中的内容，点开看看是应该的 -->
            <video class="site-img-inner" preload="metadata" controls :src="url" />
            <el-button class="site-img-del" link type="danger" icon="Close"
              @click="siteVideoList.splice(index, 1)" />
          </div>
          <el-button class="site-img-add" @click="videoPickerOpen = true">
            <div><el-icon><Plus /></el-icon><div class="hint">{{ $t('goods.media.pickVideo') }}</div></div>
          </el-button>
        </div>

        <div class="section-title mt20">{{ $t('goods.media.model') }}</div>
        <div class="hint mb8">{{ $t('goods.media.modelHint') }}</div>
        <div v-if="siteModel" class="site-model">
          <el-icon class="site-model-icon"><Box /></el-icon>
          <span class="site-model-name" :title="siteModel">{{ modelFileName(siteModel) }}</span>
          <el-button link type="primary" size="small" @click="modelPreviewOpen = true">{{ $t('common.preview') }}</el-button>
          <el-button link type="primary" size="small" @click="marksOpen = true">{{ $t('goods.media.marks') }}</el-button>
          <el-tag v-if="siteMarks" size="small" type="success">{{ $t('goods.media.marksSet') }}</el-tag>
          <el-button link type="primary" size="small" @click="modelPickerOpen = true">{{ $t('goods.media.replace') }}</el-button>
          <el-button link type="danger" size="small" @click="siteModel = ''">{{ $t('goods.media.remove') }}</el-button>
        </div>
        <el-button v-else class="site-img-add" @click="modelPickerOpen = true">
          <div><el-icon><Plus /></el-icon><div class="hint">{{ $t('goods.media.pickModel') }}</div></div>
        </el-button>

        <!-- 模型状态（2026-10-08）：多个 .glb 切换，如腿套没气/半充/充满 -->
        <div class="section-title mt20">{{ $t('goods.states.title') }}</div>
        <div class="hint mb8">{{ $t('goods.states.lead') }}</div>
        <model-states-field :rows="stateRows" :langs="siteLangs" :default-lang="defaultLang"
          :main-model="siteModel" :marks="siteMarks" />

        <div class="section-title mt20">{{ $t('goods.spec.title') }}</div>
        <div class="hint mb8" v-html="$t('goods.spec.hint', { lang: defaultLang })"></div>
        <div class="combo-langbar" v-if="siteLangs.length > 1">
          <span class="hint">{{ $t('goods.spec.editing') }}</span>
          <el-radio-group v-model="specLang" size="small">
            <el-radio-button v-for="lang in siteLangs" :key="lang" :value="lang">
              {{ lang }}<span v-if="lang === defaultLang" class="lang-flag">{{ $t('goods.defaultLangFlag') }}</span>
            </el-radio-button>
          </el-radio-group>
          <span class="hint">{{ $t('goods.spec.rowsHint') }}</span>
        </div>
        <div v-for="(row, index) in specRows" :key="index" class="spec-row">
          <template v-if="specLang === defaultLang">
            <el-input v-model="row.key" placeholder="Material" style="width: 220px" maxlength="50" />
            <el-input v-model="row.value" placeholder="Platinum cured silicone" style="flex:1" maxlength="200" />
          </template>
          <template v-else>
            <el-input v-model="row.tr[specLang].k" :placeholder="row.key || $t('goods.spec.keyPlaceholder')" style="width: 220px" maxlength="50" />
            <el-input v-model="row.tr[specLang].v" :placeholder="row.value || $t('goods.spec.valuePlaceholder')" style="flex:1" maxlength="200" />
          </template>
          <el-button v-if="specLang === defaultLang" link type="danger" @click="specRows.splice(index, 1)">{{ $t('goods.media.remove') }}</el-button>
        </div>
        <el-button v-if="specLang === defaultLang" link type="primary" icon="Plus" @click="addSpec">{{ $t('goods.spec.addRow') }}</el-button>

        <!--
          组合 = 详情页上那排带缩略图的选项卡（参考站叫 Combo）。

          和上面「规格参数」不是一回事，名字像但用途相反：
            · 规格参数：一张只读的表格，说明这个商品是什么（材质、尺寸）
            · 组合：客户要**选**一个，选中的会跟着询盘一起提交给销售

          一条都不填 = 这个商品没有组合可选，详情页不渲染这一块。
        -->
        <div class="section-title mt20">{{ $t('goods.combo.title') }}</div>
        <div class="hint mb8" v-html="$t('goods.combo.hint')"></div>
        <!--
          名称分语言，价格/图片/状态不分——后者改一次七种语言一起变，
          做成分语言的话就会出现「六种语言 199、一种语言 299」这种没人发现的错。
          所以这里只有「名称」那一列跟着下面这个语言开关走。
        -->
        <div class="combo-langbar" v-if="siteLangs.length > 1">
          <span class="hint">{{ $t('goods.combo.nameLang') }}</span>
          <el-radio-group v-model="comboLang" size="small">
            <el-radio-button v-for="lang in siteLangs" :key="lang" :value="lang">
              {{ lang }}<span v-if="lang === defaultLang" class="lang-flag">{{ $t('goods.defaultLangFlag') }}</span>
            </el-radio-button>
          </el-radio-group>
          <span class="hint">{{ $t('goods.combo.sharedHint') }}</span>
        </div>
        <!--
          两种录法（2026-09-25）：
            · 平铺：一行一个组合，前台一排（老的那种，维度只有一个、不需要名字时够用）；
            · 规格矩阵：先定义「尺码」「款式」这样的维度，再在自动展开的表里逐格定价，
              前台是两排选择器（1688 那样）。
          切换时现有组合的编码、价格、图都带过去（编码是单据里的身份，不能换）。
        -->
        <div class="combo-mode mb8">
          <el-radio-group :model-value="comboMode" size="small" @update:model-value="switchComboMode">
            <el-radio-button value="flat">{{ $t('goods.matrix.modeFlat') }}</el-radio-button>
            <el-radio-button value="matrix">{{ $t('goods.matrix.modeMatrix') }}</el-radio-button>
          </el-radio-group>
          <span class="hint">{{ $t(comboMode === 'matrix' ? 'goods.matrix.modeMatrixHint' : 'goods.matrix.modeFlatHint') }}</span>
        </div>
        <combo-matrix v-if="comboMode === 'matrix' && matrixModel" :model="matrixModel" :langs="siteLangs"
          :default-lang="defaultLang" :edit-lang="comboLang" :base-price="Number(form.price || 0)"
          :translating="translating" :main-model="siteModel" :main-marks="siteMarks" @translate="translateMatrixNames" />
        <template v-else>
        <el-table :data="comboRows" size="small" class="mb8" v-if="comboRows.length">
          <el-table-column :label="$t('goods.combo.code')" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.comboCode" placeholder="CB01" maxlength="50" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('goods.combo.name', { lang: comboLang })">
            <template #default="scope">
              <el-input v-model="scope.row.titles[comboLang]" placeholder="machine & boots" maxlength="200" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('goods.col.price')" width="160">
            <template #default="scope">
              <el-input-number v-model="scope.row.price" :min="0" :precision="4"
                controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('goods.combo.thumb')" width="120" align="center">
            <template #default="scope">
              <el-image v-if="scope.row.image" :src="scope.row.image" fit="contain" class="combo-thumb"
                @click="openComboPicker(scope.$index)" />
              <el-button v-else link type="primary" @click="openComboPicker(scope.$index)">{{ $t('goods.combo.pick') }}</el-button>
              <el-button v-if="scope.row.image" link type="danger" @click="scope.row.image = ''">{{ $t('goods.combo.clear') }}</el-button>
            </template>
          </el-table-column>
          <!-- 规格自己的 3D 模型（2026-10-09）：空 = 用主模型 -->
          <el-table-column :label="$t('goods.comboModel.col')" min-width="200">
            <template #default="scope">
              <combo-model-cell :target="scope.row" :main-model="siteModel" :main-marks="siteMarks"
                :langs="siteLangs" :default-lang="defaultLang"
                :label="scope.row.titles?.[defaultLang] || scope.row.comboCode" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="110">
            <template #default="scope">
              <!-- ⚠ value 是库里的中文串，同商品状态那一处，只翻 label -->
              <el-select v-model="scope.row.status" style="width:100%">
                <el-option :label="$t('goods.combo.on')" value="可选" />
                <el-option :label="$t('goods.combo.off')" value="停用" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column width="70" align="center">
            <template #default="scope">
              <el-button link type="danger" @click="comboRows.splice(scope.$index, 1)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button link type="primary" icon="Plus" @click="addCombo">{{ $t('goods.combo.add') }}</el-button>
        <div class="hint mt8" v-if="comboRows.length" v-html="$t('goods.combo.codeWarn')"></div>
        </template>

        <div class="section-title mt20">{{ $t('goods.copy.title') }}</div>
        <div class="lang-actions mb8">
          <span class="hint">{{ $t('goods.copy.translateHint', { lang: defaultLang }) }}</span>
          <el-button size="small" type="primary" plain icon="MagicStick"
            :loading="translating" :disabled="!canTranslateCurrent"
            @click="translateCurrentLanguage">{{ $t('goods.copy.translateCurrent') }}</el-button>
          <el-button size="small" type="success" plain icon="MagicStick"
            :loading="translating" :disabled="siteLangs.length < 2"
            @click="translateAllLanguages">{{ $t('goods.copy.translateAll') }}</el-button>
        </div>
        <el-tabs v-model="activeLang">
          <el-tab-pane v-for="lang in siteLangs" :key="lang" :name="lang">
            <template #label>
              {{ lang }}<span v-if="lang === defaultLang" class="lang-flag">{{ $t('goods.defaultLangFlag') }}</span>
            </template>
            <el-form-item :label="$t('goods.copy.titleField')">
              <el-input v-model="langForm[lang].title" maxlength="300" show-word-limit
                :placeholder="$t('goods.copy.titlePlaceholder')" />
              <div class="hint" v-if="lang === defaultLang">{{ $t('goods.copy.titleRequired') }}</div>
            </el-form-item>
            <el-form-item :label="$t('goods.copy.subtitle')">
              <el-input v-model="langForm[lang].subtitle" maxlength="500" show-word-limit
                :placeholder="$t('goods.copy.subtitlePlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('goods.copy.description')">
              <!-- 详情是 HTML，前台 v-html 渲染。带工具条和插图的编辑器见
                   station-shared/RichTextField.vue（不是所见即所得，原因写在那个文件头）

                   @insert：在**默认语言**里插图时，把同一张图同步到其它语言。
                   详情按语言各存一份，不同步的话商家得在 6 个页签里各插一遍——
                   2026-09-23 就是这么漏的（只有 fr 有图，en/zh/ru/es/ja 全空）。
                   非默认语言里插图**不反向同步**，否则会把图推到别的语言去。

                   @remove：默认语言里删图，其它语言里的同一张也一起删。
                   2026-09-25 之前只同步插、不同步删，删了重插一次，别的语言就多一份。 -->
              <rich-text-field v-model="langForm[lang].description" :rows="12"
                :placeholder="$t('goods.copy.descriptionPlaceholder')"
                @insert="payload => onDescImageInserted(lang, payload)"
                @remove="payload => onDescImageRemoved(lang, payload)" />
            </el-form-item>
            <el-form-item :label="$t('goods.copy.keywords')">
              <el-input v-model="langForm[lang].keywords" maxlength="500"
                :placeholder="$t('goods.copy.keywordsPlaceholder')" />
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 选择器：读的都是「文件资源」库，也就是商家自己对象存储里的文件 -->
    <media-picker v-model="pickerOpen" type="image" :selected="siteImgList" :limit="9"
      @confirm="urls => (siteImgList = urls)" />
    <media-picker v-model="videoPickerOpen" type="video" :selected="siteVideoList" :limit="3"
      @confirm="urls => (siteVideoList = urls)" />
    <!-- 主图视频只有一个，limit=1；和上面那个选择器读的是同一个视频库 -->
    <media-picker v-model="mainVideoPickerOpen" type="video" :selected="siteMainVideo ? [siteMainVideo] : []" :limit="1"
      @confirm="urls => (siteMainVideo = urls[0] || '')" />
    <media-picker v-model="modelPickerOpen" type="model" :selected="siteModel ? [siteModel] : []" :limit="1"
      @confirm="urls => (siteModel = urls[0] || '')" />
    <!-- 预览弹窗：three.js 那个包只在点「预览」时才下（ModelPreview 是异步组件） -->
    <el-dialog v-model="modelPreviewOpen" :title="$t('goods.media.modelPreview')" width="720px" append-to-body destroy-on-close>
      <model-preview v-if="modelPreviewOpen && siteModel" :src="siteModel" :height="420" />
    </el-dialog>
    <!-- 3D 标注：同样只在弹窗里挂、关了就销毁（WebGL 上下文），编辑器也是异步组件 -->
    <el-dialog v-model="marksOpen" :title="$t('goods.media.marksTitle')" width="90vw" top="4vh" append-to-body destroy-on-close
      :close-on-click-modal="false">
      <model-marks-editor v-if="marksOpen && siteModel" :src="siteModel" :marks="siteMarks"
        @confirm="v => { siteMarks = v || ''; marksOpen = false }" @cancel="marksOpen = false" />
    </el-dialog>
    <!-- 组合缩略图：一次只给一行挑一张，所以 limit=1，选完把当前行的下标清掉 -->
    <media-picker v-model="comboPickerOpen" type="image" :limit="1"
      :selected="comboPickerIndex >= 0 && comboRows[comboPickerIndex]?.image
        ? [comboRows[comboPickerIndex].image] : []"
      @confirm="onComboImagePicked" />

    <!--
      分类译名。行是全站分类按路径逐级拆出来的每一级，列是站点开启的语言。

      ⚠ 这里改的是**全站共享**的数据：同一个分类挂在十个商品上，译名只有一份，
        改一格十个商品的前台都跟着变。所以它不在商品编辑弹窗里，见工具栏那条注释。

      ⚠ 默认语言那一列是只读的原文：分类的身份就是这串原文，它同时还是前台的筛选键
        （?cat=），存一份「默认语言的译名」进去只会和原文不同步。
    -->
    <el-dialog v-model="catLangOpen" :title="$t('goods.catLang')" width="900px" top="6vh">
      <el-alert type="info" show-icon :closable="false" class="mb8"
        :title="$t('goods.cat.warnTitle')"
        :description="$t('goods.cat.warnDesc')" />
      <div class="mb8">
        <el-button type="primary" plain icon="MagicStick"
          :loading="catFilling" @click="fillCatLangs">{{ $t('goods.cat.fill') }}</el-button>
        <span class="hint ml8">{{ $t('goods.cat.fillHint', { lang: catDefaultLang }) }}</span>
      </div>
      <el-table :data="catRows" v-loading="catLoading" size="small" border max-height="52vh">
        <el-table-column :label="$t('goods.cat.source')" min-width="200">
          <template #default="scope">
            <span :style="{ paddingLeft: scope.row.depth * 18 + 'px' }">
              <span v-if="scope.row.depth" class="hint">└ </span>{{ scope.row.leaf }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-for="lang in catTargetLangs" :key="lang" :label="lang" min-width="150">
          <template #default="scope">
            <el-input v-model="catNames[scope.row.key][lang]" maxlength="100"
              :placeholder="scope.row.leaf" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="catLangOpen = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" :loading="catSaving" @click="saveCatLangs">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SiteGoods">
import { ref, reactive, computed, onMounted, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSiteGoods, getSiteGoods, addSiteGoods, updateSiteGoods,
  changeSiteGoodsStatus, deleteSiteGoods, translateTexts,
  listCategoryLangs, saveCategoryLangs, translateCategoryLangs
} from '@/api/console'
import MediaPicker from '@station-shared/MediaPicker.vue'
import RichTextField from '@station-shared/RichTextField.vue'
import { imgSrcs, imgBlock, removeImgBySrc } from '@station-shared/imgTags.js'
import ComboMatrix from '@/components/ComboMatrix.vue'
import ModelStatesField from '@/components/ModelStatesField.vue'
import ComboModelCell from '@/components/ComboModelCell.vue'
import { fromServer as matrixFromServer, fromFlat as matrixFromFlat, toFlat as matrixToFlat,
  toPayload as matrixPayload, planNameTranslation, runNameTranslation } from '@/utils/comboMatrix'
// 异步：three.js 那个包（约 160KB gzip）只在点「预览」时才下，不进商品页的主 chunk
const ModelPreview = defineAsyncComponent(() => import('@station-shared/ModelPreview.vue'))
const ModelMarksEditor = defineAsyncComponent(() => import('@station-shared/ModelMarksEditor.vue'))
import { formatCurrency } from '@/utils/currency'
import { identity } from '@/store/identity'
import { t } from '@/i18n'

const route = useRoute()
const router = useRouter()

/**
 * 这个页面不知道自己在管理哪个站，也不需要知道。
 *
 * 一个部署一个站：前端不传 siteCode，后端从本库读。ERP 内置管理台那版这里有个
 * 站点下拉（独立菜单进来时要选站），搬出来之后那个下拉没有了——
 * 一个管理台就是一个站，想换站就该换一台机器。
 */
/*
  ⚠ 这三个值是**库里存的中文串**（u_s_sitegoods.publishstatus），不是枚举码：
    筛选、批量上下架、后端校验、前台可见性判断读的都是它。所以这个数组一个字不能改，
    界面上显示的译名走下面的 statusLabel。
*/
const statuses = ['草稿', '已上架', '已下架']

const STATUS_KEY = {
  草稿: 'goods.status.draft',
  已上架: 'goods.status.online',
  已下架: 'goods.status.offline'
}
const statusLabel = s => (STATUS_KEY[s] ? t(STATUS_KEY[s]) : s)

const loading = ref(false), saving = ref(false), showSearch = ref(true)
const total = ref(0)
const rows = ref([]), selection = ref([])
const createOpen = ref(false), creating = ref(false)
const createForm = ref({})
const editOpen = ref(false)
const translating = ref(false)
const query = reactive({
  keyword: undefined, publishStatus: undefined, category: undefined,
  pageNum: 1, pageSize: 10
})
const form = ref({})
/** 规格用键值对行编辑，保存时再拼成 JSON——让人直接写 JSON 太容易写坏 */
const specRows = ref([])
// 站点专用图片和视频。表单里用数组好增删排序，提交前才拼回后端要的逗号分隔串
const siteImgList = ref([])
const pickerOpen = ref(false)
const siteVideoList = ref([])
const videoPickerOpen = ref(false)
// 主图视频只有一个，和 3D 模型一样存 URL 字符串；空串 = 没挂，前台第一格还是图
const siteMainVideo = ref("")
const mainVideoPickerOpen = ref(false)
// 3D 模型只有一个，存 URL 字符串；空串 = 没挂
const siteModel = ref('')
const modelPickerOpen = ref(false)
const modelPreviewOpen = ref(false)
// 3D 标注（logo / 屏幕 / 开机键），JSON 字符串；空串 = 没标。换模型、移除模型都不清——
// 编辑器打开时会按里面记的模型地址提示「模型已更换」
const siteMarks = ref('')
const marksOpen = ref(false)
// 3D 模型状态：[{ id, name: { 语言: 名字 }, model }]，第一行是主模型；空数组 = 没有状态
const stateRows = ref([])

/** 库里的 JSON → 编辑行。每种站点语言都建一格（同 buildComboRows 的理由：v-model 写不存在的键追踪不到） */
function buildStateRows(json) {
  let raw = null
  try { raw = json ? JSON.parse(json) : null } catch { raw = null }
  const list = Array.isArray(raw?.states) ? raw.states : []
  return list.map(s => ({
    id: s.id,
    name: Object.fromEntries(siteLangs.value.map(l => [l, s.name?.[l] || ''])),
    model: s.model || ''
  }))
}

/** 编辑行 → 提交的 JSON；没有行就是 null。空名字不带，后端校验默认语言必填 */
function statesPayload(rows = stateRows.value) {
  if (!rows.length) return null
  return JSON.stringify({
    v: 1,
    states: rows.map((r, i) => {
      const name = {}
      Object.entries(r.name).forEach(([l, v]) => { if (hasText(v)) name[l] = v.trim() })
      return i === 0 || !r.model ? { id: r.id, name } : { id: r.id, name, model: r.model }
    })
  })
}
/** 卡片上显示文件名就够了，整串 COS 地址塞进表单没人看得出是哪个模型 */
const modelFileName = url => {
  try { return decodeURIComponent(String(url).split('?')[0].split('/').pop() || url) } catch { return url }
}

/**
 * 列表缩略图。站点专用图的第一张优先，没挑过才回退档案主图（imgUrl）。
 * 优先级和后端 SiteOpenService.images() 保持一致——
 * 两边不一致的话，管理台看到的和站上显示的就不是同一张图。
 */
function rowThumb(row) {
  const site = (row.siteImgs || '').split(',').map(s => s.trim()).filter(Boolean)
  return site.length ? site[0] : row.imgUrl
}
const siteLangs = ref(['en'])
const activeLang = ref('en')
const langForm = reactive({})

const defaultLang = computed(() => siteLangs.value[0] || 'en')
const canTranslateCurrent = computed(() =>
  siteLangs.value.length > 1 && activeLang.value !== defaultLang.value)

/**
 * 在**默认语言**的详情里插图之后，把同一张图同步到其它语言。
 *
 * <p>为什么需要：详情是按语言各存一份（`u_s_sitegoods_lang.description`），
 * 插图只写当前那一份。商家在默认语言插了图、切到别的语言一看没有，很容易以为
 * 插失败了——2026-09-23 就出过这个：`fr` 有 3 张图，`en/zh/ru/es/ja` 全是空的，
 * 而前台那 5 个语言的商品页上一张图都没有。</p>
 *
 * <p>⚠ 不能拿默认语言那个字符偏移去切译文。各语言句子长短不同，同一个偏移在译文里
 * 可能落在词中间，会把一句话切开。所以：</p>
 * <ul>
 *   <li>目标语言**还没有正文** → 按原偏移插入（内容为空，偏移一定对得上）；</li>
 *   <li>**已经有译文** → 追加到末尾。位置不完美，但图不会丢、译文不会断。</li>
 * </ul>
 *
 * <p>只在默认语言里插才同步。在译文里插图不反向同步——那会把一张图推到
 * 别的语言去，越推越乱。</p>
 *
 * <p>⚠ 目标语言里**已经有**的那几张不再追加（2026-09-25）。原来是整段 html 无条件接到末尾，
 * 商家在默认语言删了图再插、或者某个语言本来就单独插过，那边就多出一份——
 * EB-BOOT-01 的法语详情就是这样变成「33,30 + 33,30,23 + 33,30,23」的。</p>
 */
function onDescImageInserted(lang, { html, at, urls }) {
  if (lang !== defaultLang.value) return
  const others = siteLangs.value.filter(l => l !== defaultLang.value)
  if (!others.length) return
  // 老版本的 RichTextField 不带 urls，从 html 里取
  const inserted = urls && urls.length ? urls : imgSrcs(html)

  const synced = []
  others.forEach(other => {
    const target = langForm[other]
    if (!target) return
    const cur = target.description || ''
    const present = new Set(imgSrcs(cur))
    const missing = inserted.filter(url => !present.has(url))
    if (!missing.length) return
    const piece = imgBlock(missing)
    const at2 = Math.min(at, cur.length)
    target.description = cur.trim() ? cur + piece : cur.slice(0, at2) + piece + cur.slice(at2)
    synced.push(other)
  })
  if (synced.length) {
    ElMessage.info(t('goods.msg.imageSynced', { n: synced.length, langs: synced.join(' / ') }))
  }
}

/**
 * 默认语言里删了一张图 → 其它语言里同一张（按 src 认）也删一张。
 *
 * <p>和插图同步对称：插是默认语言推给其它语言的，删也得跟着推，
 * 否则「删掉重插」一次，其它语言就平白多一份。</p>
 *
 * <p>⚠ 每种语言只删**第一张**同 src 的图，不是全删：那边要是本来就有重复，
 * 那是存量问题，一次删图不该悄悄把它们全抹掉——商家会以为自己只删了一张。</p>
 *
 * <p>译文里删图同样不反向同步，理由和插图那边一样。</p>
 */
function onDescImageRemoved(lang, { src }) {
  if (lang !== defaultLang.value || !src) return
  const removedFrom = []
  siteLangs.value.filter(l => l !== defaultLang.value).forEach(other => {
    const target = langForm[other]
    if (!target) return
    const { html, removed } = removeImgBySrc(target.description || '', src)
    if (!removed) return
    target.description = html
    removedFrom.push(other)
  })
  if (removedFrom.length) {
    ElMessage.info(t('goods.msg.imageRemovedSynced', { n: removedFrom.length, langs: removedFrom.join(' / ') }))
  }
}

/* ---------- 分类译名 ----------
   全站共享的一张表：行是分类路径的每一级，列是语言。和商品无关，所以不在编辑弹窗里。

   ⚠ 分类没有自己的表。身份就是 u_s_sitegoods.sitecategory 那一列原文，它同时是前台的
     筛选键（?cat=）和两级层次的载体。这里只管「各语言显示成什么字」，原文一个字不动。 */
const catLangOpen = ref(false)
const catLoading = ref(false), catFilling = ref(false), catSaving = ref(false)
/** 后端给的 catkey 列表，已按路径排好序（父级在子级前） */
const catKeys = ref([])
const catAllLangs = ref([])
const catDefaultLang = ref('')
/** catkey → lang → 译名。所有格子都预先建好，省得模板里判空 */
const catNames = ref({})
/** 打开时的快照。保存只发改动过的格子，避免把别人刚填的覆盖掉 */
let catNamesOrig = {}

/** 除默认语言外的语言＝需要填译名的那几列。默认语言那一列是只读原文 */
const catTargetLangs = computed(() => catAllLangs.value.filter(l => l !== catDefaultLang.value))

/** catkey 按 `/` 的个数决定缩进；显示的是最后一段 */
const catRows = computed(() => catKeys.value.map(key => {
  const parts = key.split('/')
  return { key, depth: parts.length - 1, leaf: parts[parts.length - 1] }
}))

function openCatLang() {
  catLangOpen.value = true
  loadCatLangs()
}

async function loadCatLangs() {
  catLoading.value = true
  try {
    const { data } = await listCategoryLangs()
    catKeys.value = data?.keys || []
    catAllLangs.value = data?.langs || []
    catDefaultLang.value = data?.defaultLang || ''
    const names = {}
    for (const key of catKeys.value) {
      names[key] = {}
      for (const lang of catAllLangs.value) {
        names[key][lang] = data?.names?.[key]?.[lang] || ''
      }
    }
    catNames.value = names
    catNamesOrig = JSON.parse(JSON.stringify(names))
  } finally {
    catLoading.value = false
  }
}

async function fillCatLangs() {
  catFilling.value = true
  try {
    const { data } = await translateCategoryLangs()
    const failed = data?.failed || []
    if (failed.length) {
      ElMessage.warning(t('goods.msg.catFilledPartly', { n: data?.filled || 0, failed: failed.join('、') }))
    } else if (!data?.filled) {
      ElMessage.info(t('goods.msg.catAllFilled'))
    } else {
      ElMessage.success(t('goods.msg.catFilled', { n: data.filled }))
    }
    // 后端已经写库了，重新拉一遍才能看到；顺带把快照也刷新，避免又当成本地改动发回去
    await loadCatLangs()
  } finally {
    catFilling.value = false
  }
}

async function saveCatLangs() {
  // 只发改过的格子。全量发的话，另一个人在这期间填的东西会被我这份旧数据盖掉
  const rows = []
  for (const key of catKeys.value) {
    for (const lang of catTargetLangs.value) {
      const now = (catNames.value[key]?.[lang] || '').trim()
      const was = (catNamesOrig[key]?.[lang] || '').trim()
      if (now !== was) rows.push({ catKey: key, lang, name: now })
    }
  }
  if (!rows.length) {
    ElMessage.info(t('goods.msg.noChange'))
    return
  }
  catSaving.value = true
  try {
    await saveCategoryLangs(rows)
    ElMessage.success(t('goods.msg.savedRows', { n: rows.length }))
    await loadCatLangs()
  } finally {
    catSaving.value = false
  }
}

/* ---------- 组合 ---------- */
/** [{ comboCode, price, image, status, titles: { en: '', ja: '' } }] */
const comboRows = ref([])
/** 上面那张表「名称」那一列正在填哪种语言。价格/图片/状态不跟着它变 */
const comboLang = ref('en')
/**
 * 组合的录法：'flat' 平铺一排 / 'matrix' 规格矩阵（尺码 × 款式）。
 * matrix 时组合数据在 matrixModel 里（形状见 utils/comboMatrix.js），comboRows 清空不用——
 * 两份同时活着的话，一键翻译、保存都得先判断以哪份为准，迟早用错。
 */
const comboMode = ref('flat')
const matrixModel = ref(null)

function switchComboMode(mode) {
  if (mode === comboMode.value) return
  if (mode === 'matrix') {
    matrixModel.value = matrixFromFlat(comboRows.value, siteLangs.value)
    comboRows.value = []
    ElMessage.info(t('goods.matrix.switchedToMatrix'))
  } else {
    comboRows.value = matrixToFlat(matrixModel.value, siteLangs.value, defaultLang.value)
    matrixModel.value = null
  }
  comboMode.value = mode
}
const specLang = ref('en')

/** 规格行：key/value 是默认语言；tr[lang] = {k,v} 是各语言译文。每行都给所有语言建好格子，省得模板判空 */
function makeSpecRow(key = '', value = '') {
  const tr = {}
  siteLangs.value.forEach(lang => { tr[lang] = { k: '', v: '' } })
  return { key, value, tr }
}
function addSpec() { specRows.value.push(makeSpecRow()) }
/** 某语言的规格译文，按默认键当身份：{默认键:{k,v}}。只含填了内容的行；全空返回 null */
function buildLangSpecs(lang) {
  const out = {}
  specRows.value.forEach(row => {
    const dk = (row.key || '').trim()
    if (!dk) return
    const t = row.tr?.[lang] || {}
    const k = (t.k || '').trim()
    const v = (t.v || '').trim()
    if (k || v) out[dk] = { k, v }
  })
  return Object.keys(out).length ? JSON.stringify(out) : null
}
const comboPickerOpen = ref(false)
/** 正在给哪一行挑图。-1 = 没有。挑完要清回 -1，否则下次打开选择器会带上上一行的选中态 */
const comboPickerIndex = ref(-1)

function addCombo() {
  // 每种语言都建一格空的，切语言时不会因为没这个键而报错
  const titles = {}
  siteLangs.value.forEach(lang => { titles[lang] = '' })
  comboRows.value.push({ comboCode: '', price: 0, image: '', model3d: '', model3dMarks: '', model3dStateRows: [], status: '可选', titles })
}

function openComboPicker(index) {
  comboPickerIndex.value = index
  comboPickerOpen.value = true
}

function onComboImagePicked(urls) {
  const row = comboRows.value[comboPickerIndex.value]
  if (row) row.image = (urls || [])[0] || ''
  comboPickerIndex.value = -1
}
/** 币种来自会话里的站点档案，不再从站点下拉的当前项上取 */
const siteCurrency = computed(() => identity.currency || 'USD')

function loadList() {
  loading.value = true
  listSiteGoods(query)
    .then(r => { rows.value = r.rows || []; total.value = r.total || 0 })
    .finally(() => loading.value = false)
}
function search() {
  query.pageNum = 1
  loadList()
}
function reset() {
  query.keyword = undefined; query.publishStatus = undefined; query.category = undefined
  search()
}

function statusType(status) {
  return { 草稿: 'info', 已上架: 'success', 已下架: 'warning' }[status] || 'info'
}

// ---------- 新建商品 ----------
//
// ⚠ 以前这里是「打开 ERP 商品选择器 → 拿回一串编码 → 提交」。
//   现在是「填一张表 → 提交一个完整的商品」（§5.4）。
function openCreate() {
  createForm.value = { goodsCode: '', goodsName: '', spec: '', price: 0, unit: '', siteCategory: '' }
  createOpen.value = true
}
function doCreate() {
  const g = createForm.value
  if (!g.goodsCode?.trim()) { ElMessage.warning(t('goods.msg.needCode')); return }
  if (!g.goodsName?.trim()) { ElMessage.warning(t('goods.msg.needName')); return }
  creating.value = true
  addSiteGoods(g)
    .then(r => {
      ElMessage.success(r.msg || t('goods.msg.created'))
      createOpen.value = false
      loadList()
    })
    .finally(() => { creating.value = false })
}

// ---------- 编辑 ----------
/**
 * 打开编辑弹窗。
 *
 * @param row        至少要有 goodsCode
 * @param preferLang 打开后直接切到哪个语言页签。给「站长工具 → SEO 体检 → 去修」用：
 *                   体检报的是「某个商品的某种语言缺标题」，落地时就该停在那一格上，
 *                   而不是让人自己再点一次语言页签。传空或传一个站点没开的语言时，
 *                   照旧停在第一种语言上。
 */
function openEdit(row, preferLang) {
  getSiteGoods(row.goodsCode).then(r => {
    const data = r.data || {}
    form.value = { ...(data.goods || {}) }
    // 数字字段后端是 BigDecimal，转成 Number 才能给 el-input-number
    form.value.price = Number(form.value.price || 0)
    form.value.moq = form.value.moq == null ? undefined : Number(form.value.moq)

    siteLangs.value = (data.siteLangs || ['en'])
    // preferLang 只在它真的是这个站开着的语言时才用——体检链接上的语言参数是
    // 地址栏来的，塞一个站点没开的语言进去会让页签切到一个不存在的 tab（整块空白）
    activeLang.value = preferLang && siteLangs.value.includes(preferLang)
      ? preferLang
      : siteLangs.value[0]
    // 每种语言都建一份空表单，切页签时不会因为没数据而报错
    Object.keys(langForm).forEach(k => delete langForm[k])
    siteLangs.value.forEach(lang => {
      const exist = (data.langs || []).find(l => l.lang === lang) || {}
      langForm[lang] = {
        title: exist.title || '', subtitle: exist.subtitle || '',
        description: exist.description || '', keywords: exist.keywords || ''
      }
    })
    comboLang.value = siteLangs.value[0]
    // 配过规格维度、组合也带着取值的 → 矩阵模式；否则照旧平铺
    matrixModel.value = matrixFromServer(form.value.optionsJson, data.combos, siteLangs.value)
    comboMode.value = matrixModel.value ? 'matrix' : 'flat'
    comboRows.value = matrixModel.value ? [] : buildComboRows(data.combos, data.comboLangs)
    specLang.value = siteLangs.value[0]
    specRows.value = parseSpecs(form.value.specsJson)
    // 把各语言的规格译文（按默认键当身份）填进每行的 tr
    ;(data.langs || []).forEach(l => {
      if (!l.lang || l.lang === defaultLang.value || !l.specsJson) return
      let obj = {}
      try { obj = JSON.parse(l.specsJson) } catch { obj = {} }
      specRows.value.forEach(row => {
        const hit = obj[row.key]
        if (hit && row.tr[l.lang]) {
          row.tr[l.lang].k = hit.k || ''
          row.tr[l.lang].v = hit.v || ''
        }
      })
    })
    siteImgList.value = (form.value.siteImgs || "").split(",").map(s => s.trim()).filter(Boolean)
    siteVideoList.value = (form.value.siteVideos || "").split(",").map(s => s.trim()).filter(Boolean)
    siteModel.value = (form.value.model3d || "").trim()
    siteMarks.value = (form.value.model3dMarks || "").trim()
    stateRows.value = buildStateRows(form.value.model3dStates)
    siteMainVideo.value = (form.value.mainVideo || "").trim()
    editOpen.value = true
  })
}

/**
 * 后端把组合分两份给：主表一份（价格/图/状态/排序）、名称按 combocode + lang 一份。
 * 界面上要的是「一行一个组合，名称跟着语言开关切」，所以在这里合成一份。
 *
 * ⚠ 站点开的每种语言都建一格，哪怕库里没有那一行。不建的话，切到那种语言时
 * `row.titles[comboLang]` 是 undefined，v-model 写进去 Vue 追踪不到——
 * 表现是「输入框能打字，切走再切回来内容没了」。
 */
function buildComboRows(combos, comboLangs) {
  return (combos || []).map(c => {
    const titles = {}
    siteLangs.value.forEach(lang => {
      const hit = (comboLangs || []).find(l => l.comboCode === c.comboCode && l.lang === lang)
      titles[lang] = hit?.title || ''
    })
    return {
      comboCode: c.comboCode,
      price: Number(c.price || 0),
      image: c.image || '',
      model3d: c.model3d || '',
      model3dMarks: c.model3dMarks || '',
      model3dStateRows: buildStateRows(c.model3dStates),
      status: c.status || '可选',
      titles
    }
  })
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== ''
}

function translationSource(targetLang) {
  const source = langForm[defaultLang.value] || {}
  const target = langForm[targetLang] || {}
  const fields = ['title', 'subtitle', 'description', 'keywords']
  const texts = fields.map(field => hasText(target[field]) ? '' : (source[field] || ''))
  const comboIndexes = []
  comboRows.value.forEach((row, index) => {
    if (!hasText(row.titles?.[targetLang])) {
      comboIndexes.push(index)
      texts.push(row.titles?.[defaultLang.value] || '')
    }
  })
  // 规格：每行推「默认键 + 默认值」两条；键值都翻。只补目标语言还空着的行。
  const specIndexes = []
  specRows.value.forEach((row, index) => {
    const t = row.tr?.[targetLang] || {}
    if (!hasText(t.k) || !hasText(t.v)) {
      specIndexes.push(index)
      texts.push(row.key || '')
      texts.push(row.value || '')
    }
  })
  // 模型状态名：只补目标语言还空着的
  const stateIndexes = []
  stateRows.value.forEach((row, index) => {
    if (!hasText(row.name?.[targetLang]) && hasText(row.name?.[defaultLang.value])) {
      stateIndexes.push(index)
      texts.push(row.name[defaultLang.value])
    }
  })
  // 规格矩阵的维度名、取值名不在这里：它们的源语言不是默认语言，走 translateMatrixNames
  return { fields, texts, comboIndexes, specIndexes, stateIndexes }
}

function hasTranslatableText(targetLang) {
  if (!targetLang || targetLang === defaultLang.value) return false
  const { texts } = translationSource(targetLang)
  return texts.some(text => hasText(text))
}

function applyTranslation(targetLang, texts, meta) {
  const target = langForm[targetLang]
  if (!target) return
  meta.fields.forEach((field, index) => {
    if (!hasText(target[field]) && hasText(texts[index])) target[field] = texts[index]
  })
  meta.comboIndexes.forEach((rowIndex, index) => {
    const row = comboRows.value[rowIndex]
    const translated = texts[meta.fields.length + index]
    if (row && !hasText(row.titles?.[targetLang]) && hasText(translated)) {
      row.titles[targetLang] = translated
    }
  })
  // 规格译文接在 fields + combos 之后，每行占两条（键、值）
  const specBase = meta.fields.length + meta.comboIndexes.length
  ;(meta.specIndexes || []).forEach((rowIndex, i) => {
    const row = specRows.value[rowIndex]
    if (!row) return
    if (!row.tr[targetLang]) row.tr[targetLang] = { k: '', v: '' }
    const tk = texts[specBase + i * 2]
    const tv = texts[specBase + i * 2 + 1]
    if (!hasText(row.tr[targetLang].k) && hasText(tk)) row.tr[targetLang].k = tk
    if (!hasText(row.tr[targetLang].v) && hasText(tv)) row.tr[targetLang].v = tv
  })
  // 模型状态名接在规格之后，每行一条
  const stateBase = specBase + (meta.specIndexes || []).length * 2
  ;(meta.stateIndexes || []).forEach((rowIndex, i) => {
    const row = stateRows.value[rowIndex]
    const translated = texts[stateBase + i]
    if (row && !hasText(row.name[targetLang]) && hasText(translated)) row.name[targetLang] = translated.trim().slice(0, 30)
  })
}

async function translateLanguage(targetLang) {
  if (!hasTranslatableText(targetLang)) return false
  const meta = translationSource(targetLang)
  const response = await translateTexts(defaultLang.value, targetLang, meta.texts)
  const translated = response.data?.texts || []
  applyTranslation(targetLang, translated, meta)
  return true
}

async function translateCurrentLanguage() {
  if (!canTranslateCurrent.value) {
    ElMessage.warning(t('goods.msg.pickOtherLang', { lang: defaultLang.value }))
    return
  }
  if (!hasTranslatableText(activeLang.value)) {
    ElMessage.info(t('goods.msg.nothingToTranslate'))
    return
  }
  translating.value = true
  try {
    await translateLanguage(activeLang.value)
    ElMessage.success(t('goods.msg.translatedCurrent'))
  } finally {
    translating.value = false
  }
}

/* ---------- 规格矩阵名称的一键翻译 ----------
   源语言 = 组合那一栏正在填的语言（comboLang）：商家在中文里填好维度，就以中文为准铺到
   其余所有语言，包括默认语言。那一栏一个名字都没填时退回默认语言。
   规则（只补空的 + 同步本次机器翻的）见 utils/comboMatrix.js 的 runNameTranslation。 */

function matrixSourceLang() {
  const dims = matrixModel.value?.dims || []
  // 这种语言填了几处名称
  const filled = lang => dims.reduce((n, d) => n + ((d.names[lang] || '').trim() ? 1 : 0)
    + d.values.filter(v => (v.names[lang] || '').trim()).length, 0)
  if (filled(comboLang.value)) return comboLang.value
  if (filled(defaultLang.value)) return defaultLang.value
  // 两边都空：用填得最多的那种语言（商家在中文里填完、开关却停在法语上）
  return siteLangs.value.reduce((best, lang) => (filled(lang) > filled(best) ? lang : best), defaultLang.value)
}

const translateVia = (from, to, texts) =>
  translateTexts(from, to, texts).then(r => r.data?.texts || [])

async function translateMatrixNames() {
  if (comboMode.value !== 'matrix' || !matrixModel.value) return
  const from = matrixSourceLang()
  if (!planNameTranslation(matrixModel.value, siteLangs.value, from).length) {
    ElMessage.info(t('goods.matrix.nothingToTranslate', { lang: from }))
    return
  }
  translating.value = true
  try {
    const { done, failed } = await runNameTranslation(matrixModel.value, siteLangs.value, from, translateVia)
    if (failed.length) {
      ElMessage.warning(t('goods.msg.translatedPartly', { n: done.length, failed: failed.join('、') }))
    } else {
      ElMessage.success(t('goods.matrix.translated', { lang: from, n: done.length }))
    }
  } finally {
    translating.value = false
  }
}

async function translateAllLanguages() {
  const targets = siteLangs.value.filter(lang => lang !== defaultLang.value && hasTranslatableText(lang))
  // 规格矩阵的名称也算在「全部语言翻译」里：商家说的「一键翻译」就是这一个按钮
  const matrixFrom = comboMode.value === 'matrix' && matrixModel.value ? matrixSourceLang() : ''
  const matrixPending = matrixFrom
    ? planNameTranslation(matrixModel.value, siteLangs.value, matrixFrom).length > 0
    : false
  if (!targets.length && !matrixPending) {
    ElMessage.info(t('goods.msg.nothingToTranslateAll'))
    return
  }
  translating.value = true
  const failed = []
  let translatedCount = 0
  let aborted = false
  try {
    // 串行请求，避免一次点击触发多个翻译请求导致腾讯云限流；单个语言失败不影响其他语言。
    /*
      ⚠ 同一个原因连着失败两次就停，剩下的语言不再试（2026-09-25）。
        账号没开通机器翻译、密钥不对、额度用完——这些是账号级的问题，换哪种语言都一样失败，
        原来会一种语言弹一个红框，五种语言叠五个一模一样的。
        不在第一次失败就停：「这两种语言之间不能直译」是语种对级的，别的语言可能照样能翻。
    */
    let lastError = ''
    for (const [index, lang] of targets.entries()) {
      try {
        if (await translateLanguage(lang)) translatedCount++
      } catch (error) {
        failed.push(lang)
        const reason = error?.message || ''
        if (reason && reason === lastError) {
          failed.push(...targets.slice(index + 1))
          aborted = true
          break
        }
        lastError = reason
      }
    }
    // 文案那边已经因为账号级问题停下了，名称这边必然同样失败，不再多弹一轮
    if (matrixPending && !aborted) {
      const names = await runNameTranslation(matrixModel.value, siteLangs.value, matrixFrom, translateVia)
      names.failed.forEach(lang => { if (!failed.includes(lang)) failed.push(lang) })
      names.done.forEach(lang => { if (!targets.includes(lang)) translatedCount++ })
    }
    if (failed.length) {
      ElMessage.warning(t('goods.msg.translatedPartly', { n: translatedCount, failed: failed.join('、') }))
    } else {
      ElMessage.success(t('goods.msg.translatedAll', { n: translatedCount }))
    }
  } finally {
    translating.value = false
  }
}

/** 库里存的是 JSON，界面上按行编辑。解析不了就当空的，不要把弹窗卡住 */
function parseSpecs(json) {
  if (!json) return []
  try {
    return Object.entries(JSON.parse(json)).map(([key, value]) => makeSpecRow(key, String(value ?? '')))
  } catch {
    return []
  }
}

function save() {
  const title = (langForm[defaultLang.value]?.title || '').trim()
  if (form.value.publishStatus === '已上架' && !title) {
    ElMessage.warning(t('goods.msg.needTitleFirst', { lang: defaultLang.value }))
    activeLang.value = defaultLang.value
    return
  }
  // 键为空的行直接丢掉，别存出一个 {"":"xxx"} 这种没法在页面上显示的规格
  const specs = {}
  specRows.value.forEach(row => {
    const key = (row.key || '').trim()
    if (key) specs[key] = (row.value || '').trim()
  })

  /*
    组合。编码是这一条的身份，空的行直接丢掉（「添加一个组合」出来还没填就点保存，
    不该为它把整次保存打回去）；重复的必须拦下来——后端也拦，但在这里拦能指出是哪一行。
  */
  let combos = []
  let optionsJson = null
  if (comboMode.value === 'matrix' && matrixModel.value) {
    // 矩阵模式：维度 + 每格一条组合，由 utils/comboMatrix 组好（编码生成、默认语言名称检查都在里面）
    const built = matrixPayload(matrixModel.value, siteLangs.value, defaultLang.value)
    if (built.error) {
      // ⚠ 不把语言开关切到默认语言：商家多半是在中文里填的，切走之后
      //   「一键翻译」就以空着的那种语言为源，点了什么都不翻
      ElMessage.warning(t(built.error.key, built.error.vars))
      return
    }
    combos = built.combos.map(c => ({
      ...c,
      model3dStates: statesPayload(c.model3dStateRows)
    }))
    optionsJson = built.optionsJson
  }
  const seen = new Set()
  for (const row of comboMode.value === 'matrix' ? [] : comboRows.value) {
    const code = (row.comboCode || '').trim()
    if (!code) continue
    if (seen.has(code)) { ElMessage.warning(t('goods.msg.comboCodeDup', { code })); return }
    seen.add(code)
    const titles = {}
    Object.entries(row.titles || {}).forEach(([lang, value]) => { titles[lang] = (value || '').trim() })
    combos.push({
      comboCode: code,
      price: row.price ?? 0,
      image: (row.image || '').trim() || null,
      model3d: (row.model3d || '').trim() || null,
      model3dMarks: row.model3d ? (row.model3dMarks || null) : null,
      model3dStates: row.model3d ? statesPayload(row.model3dStateRows) : null,
      status: row.status || '可选',
      // 排序不在界面上出现：表格从上到下就是显示顺序，让人再手填一个数字是多余的
      sortNum: combos.length,
      titles
    })
  }
  // 填了组合却没给默认语言起名 = 详情页上那一格是空的（后端会把没名字的组合丢掉）
  const nameless = combos.find(c => !c.titles[defaultLang.value])
  if (nameless) {
    ElMessage.warning(t('goods.msg.comboNoName', { code: nameless.comboCode, lang: defaultLang.value }))
    comboLang.value = defaultLang.value
    return
  }

  // 每个使用独立 GLB 的规格都必须配置完整三态。
  for (const combo of combos) {
    if (!combo.model3d) continue
    const rows = combo.model3dStateRows || []
    const complete = rows.length === 3
      && !!rows[0]?.name?.[defaultLang.value]?.trim()
      && rows.slice(1).every(r => !!r.model && !!r.name?.[defaultLang.value]?.trim())
    if (!complete) {
      ElMessage.warning(`${combo.titles?.[defaultLang.value] || combo.comboCode}：请完整配置没气、半气、满气三个模型状态`)
      return
    }
  }

  const payload = {
    goods: {
      goodsCode: form.value.goodsCode,
      // ⚠ goodsName / spec / imgUrl / mainImgs 必须一起提交。
      //   SiteGoodsMapper 的 update 是 `goodsname = #{goodsName}` 无条件赋值，
      //   不带这几项的话每存一次商品，名称、规格和档案图就被清成 NULL——
      //   界面上明明有这两个输入框（§5.4 之后它们变成可改的了），只是提交时漏了。
      goodsName: form.value.goodsName,
      spec: form.value.spec,
      imgUrl: form.value.imgUrl,
      mainImgs: form.value.mainImgs,
      siteCategory: form.value.siteCategory,
      price: form.value.price,
      moq: form.value.moq,
      unit: form.value.unit,
      featured: form.value.featured ? 1 : 0,
      publishStatus: form.value.publishStatus,
      sortNum: form.value.sortNum,
      specsJson: Object.keys(specs).length ? JSON.stringify(specs) : null,
      // ⚠ 平铺模式必须显式传 null：后端 update 是无条件赋值，传 null 就是「不要维度了」，
      //   从矩阵切回平铺时靠它把库里的维度清掉
      optionsJson,
      siteImgs: siteImgList.value.length ? siteImgList.value.join(",") : null,
      siteVideos: siteVideoList.value.length ? siteVideoList.value.join(",") : null,
      model3d: siteModel.value || null,
      model3dMarks: siteMarks.value || null,
      model3dStates: statesPayload(),
      mainVideo: siteMainVideo.value || null,
      remark: form.value.remark
    },
    langs: siteLangs.value.map(lang => ({ lang, ...langForm[lang], specsJson: buildLangSpecs(lang) })),
    // ⚠ 这个键必须一直带着，哪怕是空数组。后端把「没有 combos 键」当成
    //   「这次不动组合」，把「空数组」当成「全删」——两者不能混
    combos
  }
  saving.value = true
  updateSiteGoods(payload)
    .then(() => { ElMessage.success(t('common.saved')); editOpen.value = false; loadList() })
    .finally(() => saving.value = false)
}

// ---------- 上下架 / 删除 ----------
function batchStatus(status, row) {
  const codes = row ? [row.goodsCode] : selection.value.map(r => r.goodsCode)
  if (!codes.length) { ElMessage.warning(t('goods.msg.pickFirst')); return }
  changeSiteGoodsStatus(codes, status)
    .then(() => {
      ElMessage.success(t(status === '已上架' ? 'goods.msg.published' : 'goods.msg.unpublished', { n: codes.length }))
      loadList()
    })
}

function removeSelected(row) {
  const codes = row?.goodsCode ? [row.goodsCode] : selection.value.map(r => r.goodsCode)
  if (!codes.length) { ElMessage.warning(t('goods.msg.pickFirst')); return }
  ElMessageBox.confirm(
    t('goods.msg.removeConfirm', { n: codes.length }),
    t('common.confirmTitle'), { type: 'warning', closeOnClickModal: false })
    .then(() => deleteSiteGoods(codes))
    .then(() => { ElMessage.success(t('goods.msg.removed')); loadList() })
    .catch(() => {})
}

/**
 * 从地址栏参数直接打开某个商品的编辑框。
 *
 * `?code=G001&lang=ja` —— 「站长工具 → SEO 体检 → 去修」就是这么跳过来的。
 * 体检报的是「哪个商品的哪种语言缺什么」，所以那个链接必须能落到那一格上，
 * 否则体检就是一张只会抱怨、给不出路径的清单。
 *
 * ⚠ 打开之后把参数从地址栏擦掉（router.replace，不是 push）。两个理由：
 *   · 不擦的话，关掉弹窗再刷新页面，它会又弹一次；
 *   · 这个地址会被收进浏览器历史，而「带着某个商品编码的商品管理页」
 *     不是一个值得留在历史里的位置。
 *
 * ⚠ 商品不存在时 getSiteGoods 会走 request.js 的统一报错，这里不额外处理——
 *   体检数据是刚刚才算出来的，商品在这几秒内被删掉是极边缘的情况，
 *   为它加一条专门的提示不值得。
 */
function openFromQuery() {
  const code = route.query.code
  if (!code) return
  const lang = typeof route.query.lang === 'string' ? route.query.lang : undefined
  openEdit({ goodsCode: String(code) }, lang)
  router.replace({ path: '/goods' })
}

onMounted(() => {
  loadList()
  openFromQuery()
})
</script>

<style scoped>
/* 下拉选项里的次要信息：语言和站点编码，重名时靠它分辨 */
.opt-meta {
  float: right;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
/* 站点专用图：缩略图 + 悬停出现的删除按钮 */
.site-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 4px;
}
.site-img {
  position: relative;
  width: 92px;
  height: 92px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
.site-img-inner { width: 100%; height: 100%; }
.site-img-main {
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 0 5px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}
.site-img-del {
  position: absolute;
  top: 0;
  right: 0;
  padding: 2px;
  opacity: 0;
  transition: opacity .2s;
}
.site-img:hover .site-img-del { opacity: 1; }
/* 视频卡片按 16:9 加宽，正方形里放宽屏视频会剩两条黑边 */
/* 视频卡上的角标要挪到左上：默认的 .site-img-main 在左下，正好压住播放控制条 */
.site-img-main--top { bottom: auto; top: 0; }
.site-video { width: 164px; }
.site-video .site-img-inner { object-fit: contain; background: #000; }
.site-model {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  max-width: 100%;
}
.site-model-icon { font-size: 20px; color: var(--el-color-primary); }
.site-model-name {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.site-img-add {
  width: 92px;
  height: 92px;
  border-style: dashed;
}
.thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
}
.no-img, .text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.warn-text {
  color: var(--el-color-warning);
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}
.spec-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}
.lang-flag {
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 3px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 11px;
}
.section-title {
  margin: 4px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
.mt20 { margin-top: 20px; }
.mt8 { margin-top: 8px; }
.ml8 { margin-left: 8px; }

/* 组合 */
/* 平铺 / 规格矩阵 切换 */
.combo-mode {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
}
.combo-langbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-bottom: 8px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
.combo-thumb {
  width: 40px;
  height: 40px;
  vertical-align: middle;
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
}
</style>

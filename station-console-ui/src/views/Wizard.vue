<!--
  引导下单（2026-09-28）：前台 /configure 那一页的参数在这里配。
  设计：docs/superpowers/specs/2026-09-28-order-wizard-design.md

  左边是树：页面 → 分组 → 字段；右边编辑选中的那一个。字段的选项在字段编辑里用表格改，
  不进树——一个字段动辄七八个选项，全挂树上左边就看不清结构了。

  ## 文字按语言分开编
  顶上「编辑语言」切的是**正在编辑哪种语言的文字**，不是管理台界面语言。
  空着的那种语言，前台按 英文 → 默认语言 显示（后端 OrderWizardService.pick），
  所以输入框的占位符里摆的就是那段回退文字：看得见「不填的话访客会看到什么」。

  ## 保存即生效
  不做草稿两态（设计文档「存储」一节）。格式由后端 validateSchema 把关，
  报错会说清是哪个分组 / 字段 / 选项；这里只在保存前把没填完的条件行剔掉。
-->
<template>
  <div class="page wz">
    <el-alert type="info" show-icon :closable="false" class="mb12"
      :title="$t('wizard.lead.title')" :description="$t('wizard.lead.desc')" />

    <div class="wz-bar">
      <el-switch v-model="enabled" :disabled="!canEdit" :active-text="$t('wizard.enabled')" />
      <el-divider direction="vertical" />
      <span class="text-muted">{{ $t('wizard.editLang') }}</span>
      <el-radio-group v-model="editLang" size="small">
        <el-radio-button v-for="l in langs" :key="l" :value="l">{{ l }}</el-radio-button>
      </el-radio-group>
      <span class="wz-sp"></span>
      <el-tag v-if="dirty" type="warning" effect="plain">{{ $t('wizard.unsaved') }}</el-tag>
      <el-link v-if="frontUrl" :href="frontUrl" target="_blank" rel="noopener" type="primary">{{ $t('wizard.preview') }} ↗</el-link>
      <!--
        载入预置模板：按站点挑（后端 OrderWizardService.PRESETS 按站点编码对应）。
        有本站那份 → 分裂按钮，主按钮直接载入它，箭头里能换别的；
        一份都对不上（比如康辰）→ 只给下拉，不替人挑别家的。
      -->
      <el-dropdown v-if="sitePreset" split-button :disabled="!canEdit" @click="loadTemplate(sitePreset.code)"
        @command="loadTemplate">
        {{ $t('wizard.loadTemplate') }}
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="p in presets" :key="p.code" :command="p.code">
              {{ $t('wizard.preset.' + p.code) }}
              <el-tag v-if="p.forThisSite" size="small" type="success" class="ml8">{{ $t('wizard.preset.thisSite') }}</el-tag>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown v-else :disabled="!canEdit || !presets.length" @command="loadTemplate">
        <el-button :disabled="!canEdit || !presets.length">{{ $t('wizard.loadTemplate') }} ▾</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>{{ $t('wizard.preset.pick') }}</el-dropdown-item>
            <el-dropdown-item v-for="p in presets" :key="p.code" :command="p.code" divided>
              {{ $t('wizard.preset.' + p.code) }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button @click="openJson">{{ $t('wizard.json') }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canEdit" @click="save">{{ $t('wizard.save') }}</el-button>
    </div>
    <p class="text-muted small wz-note">{{ $t('wizard.fallbackHint') }}</p>

    <el-empty v-if="!schema" :description="$t('wizard.empty')" />

    <div v-else class="wz-body">
      <!-- ---------------- 左：树 ---------------- -->
      <aside class="wz-tree">
        <div :class="['wz-node', 'root', { on: sel.kind === 'root' }]" @click="select('root')">
          {{ txt(schema, 'title') || $t('wizard.pageTitle') }}
        </div>
        <template v-for="(g, gi) in schema.groups" :key="gi">
          <div :class="['wz-node', 'group', { on: sel.kind === 'group' && sel.gi === gi }]" @click="select('group', gi)">
            <span class="wz-node-name">{{ txt(g, 'title') || g.code }}</span>
            <span class="wz-node-ops" @click.stop>
              <el-button link size="small" :disabled="!canEdit || gi === 0" @click="move(schema.groups, gi, -1)">↑</el-button>
              <el-button link size="small" :disabled="!canEdit || gi === schema.groups.length - 1" @click="move(schema.groups, gi, 1)">↓</el-button>
            </span>
          </div>
          <div v-for="(f, fi) in g.fields" :key="`${gi}-${fi}`"
            :class="['wz-node', 'field', { on: sel.kind === 'field' && sel.gi === gi && sel.fi === fi }]"
            @click="select('field', gi, fi)">
            <span class="wz-node-name">{{ txt(f, 'title') || f.code }}</span>
            <el-tag size="small" effect="plain" class="wz-type">{{ $t('wizard.type.' + f.type) }}</el-tag>
            <el-tag v-if="hiddenCount(f)" size="small" type="info">{{ $t('wizard.hiddenCount', { n: hiddenCount(f) }) }}</el-tag>
          </div>
          <div v-if="canEdit" class="wz-add" @click="addField(gi)">+ {{ $t('wizard.addField') }}</div>
        </template>
        <el-button v-if="canEdit" class="wz-add-group" size="small" @click="addGroup">+ {{ $t('wizard.addGroup') }}</el-button>
      </aside>

      <!-- ---------------- 右：编辑 ---------------- -->
      <section class="wz-edit">
        <!-- 页面 -->
        <el-form v-if="sel.kind === 'root'" label-width="120px" :disabled="!canEdit">
          <el-form-item :label="$t('wizard.pageTitle')">
            <el-input :model-value="txt(schema, 'title')" :placeholder="fallback(schema, 'title')"
              @update:model-value="v => setTxt(schema, 'title', v)" />
          </el-form-item>
          <el-form-item :label="$t('wizard.pageLead')">
            <el-input type="textarea" :rows="3" :model-value="txt(schema, 'lead')" :placeholder="fallback(schema, 'lead')"
              @update:model-value="v => setTxt(schema, 'lead', v)" />
          </el-form-item>
        </el-form>

        <!-- 分组 -->
        <el-form v-else-if="sel.kind === 'group' && group" label-width="120px" :disabled="!canEdit">
          <el-form-item :label="$t('wizard.code')">
            <el-input v-model="group.code" maxlength="40" />
            <div class="text-muted small">{{ $t('wizard.codeTip') }}</div>
          </el-form-item>
          <el-form-item :label="$t('wizard.title')">
            <el-input :model-value="txt(group, 'title')" :placeholder="fallback(group, 'title')"
              @update:model-value="v => setTxt(group, 'title', v)" />
          </el-form-item>
          <el-form-item :label="$t('wizard.sub')">
            <el-input :model-value="txt(group, 'sub')" :placeholder="fallback(group, 'sub')"
              @update:model-value="v => setTxt(group, 'sub', v)" />
          </el-form-item>
          <el-form-item :label="$t('wizard.showIf')">
            <wizard-conds v-model="group.showIf" :fields="condFields(sel.gi, 0)" :disabled="!canEdit" />
            <div class="text-muted small">{{ $t('wizard.condTip') }}</div>
          </el-form-item>
          <el-form-item>
            <el-button type="danger" plain :disabled="schema.groups.length <= 1" @click="removeGroup(sel.gi)">{{ $t('wizard.remove') }}</el-button>
          </el-form-item>
        </el-form>

        <!-- 字段 -->
        <el-form v-else-if="sel.kind === 'field' && field" label-width="120px" :disabled="!canEdit">
          <div class="wz-row2">
            <el-form-item :label="$t('wizard.code')">
              <el-input v-model="field.code" maxlength="40" />
            </el-form-item>
            <el-form-item :label="$t('wizard.type')">
              <el-select v-model="field.type" @change="onTypeChange">
                <el-option v-for="ty in TYPES" :key="ty" :value="ty" :label="$t('wizard.type.' + ty)" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item>
            <el-checkbox v-model="field.required">{{ $t('wizard.required') }}</el-checkbox>
            <el-checkbox v-if="isOptions(field)" v-model="field.multi">{{ $t('wizard.multi') }}</el-checkbox>
          </el-form-item>
          <el-form-item :label="$t('wizard.title')">
            <el-input :model-value="txt(field, 'title')" :placeholder="fallback(field, 'title')"
              @update:model-value="v => setTxt(field, 'title', v)" />
          </el-form-item>
          <el-form-item :label="$t('wizard.help')">
            <el-input type="textarea" :rows="2" :model-value="txt(field, 'help')" :placeholder="fallback(field, 'help')"
              @update:model-value="v => setTxt(field, 'help', v)" />
          </el-form-item>
          <el-form-item :label="$t('wizard.hint')">
            <el-input :model-value="txt(field, 'hint')" :placeholder="fallback(field, 'hint')"
              @update:model-value="v => setTxt(field, 'hint', v)" />
          </el-form-item>
          <el-form-item :label="$t('wizard.showIf')">
            <wizard-conds v-model="field.showIf" :fields="condFields(sel.gi, sel.fi)" :disabled="!canEdit" />
            <div class="text-muted small">{{ $t('wizard.condTip') }}</div>
          </el-form-item>

          <template v-if="field.type === 'number'">
            <div class="wz-row2">
              <el-form-item :label="$t('wizard.min')"><el-input-number v-model="field.min" :min="0" /></el-form-item>
              <el-form-item :label="$t('wizard.max')"><el-input-number v-model="field.max" :min="0" :max="9999999" /></el-form-item>
            </div>
            <el-form-item :label="$t('wizard.numberGoods')">
              <goods-select v-model="field.goods" />
            </el-form-item>
          </template>

          <el-form-item v-if="field.type === 'goods'" :label="$t('wizard.source')">
            <el-select v-model="field.source">
              <el-option v-for="f in condFields(sel.gi, sel.fi)" :key="f.code" :value="f.code" :label="`${f.title || f.code} (${f.code})`" />
            </el-select>
            <div class="text-muted small">{{ $t('wizard.sourceTip') }}</div>
          </el-form-item>

          <!-- 选项表 -->
          <template v-if="isOptions(field)">
            <div class="section-title">{{ $t('wizard.options') }}</div>
            <el-table :data="field.options" size="small" border row-key="code">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <el-form label-width="120px" class="wz-opt-more" :disabled="!canEdit">
                    <el-form-item v-if="field.type === 'cards'" :label="$t('wizard.opt.desc')">
                      <el-input :model-value="txt(row, 'desc')" :placeholder="fallback(row, 'desc')"
                        @update:model-value="v => setTxt(row, 'desc', v)" />
                    </el-form-item>
                    <el-form-item :label="$t('wizard.opt.badge')">
                      <el-input :model-value="txt(row, 'badge')" :placeholder="fallback(row, 'badge')"
                        @update:model-value="v => setTxt(row, 'badge', v)" />
                    </el-form-item>
                    <el-form-item v-if="field.type === 'cards'" :label="$t('wizard.opt.image')">
                      <el-input v-model="row.image" placeholder="https://…" />
                    </el-form-item>
                    <el-form-item :label="$t('wizard.needs')">
                      <wizard-conds v-model="row.needs" :fields="condFields(sel.gi, sel.fi)" :disabled="!canEdit" />
                      <div class="text-muted small">{{ $t('wizard.needsTip') }}</div>
                    </el-form-item>
                    <el-form-item :label="$t('wizard.opt.moq')">
                      <el-input-number v-model="row.moq" :min="0" />
                      <div class="text-muted small">{{ $t('wizard.opt.moqTip') }}</div>
                    </el-form-item>
                    <el-form-item :label="$t('wizard.opt.price')">
                      <el-input-number v-model="row.price" :min="0" :precision="2" />
                    </el-form-item>
                    <el-form-item :label="$t('wizard.opt.sets')">
                      <el-input :model-value="setsText(row)" placeholder='{"plug":"EU"}'
                        @update:model-value="v => setSets(row, v)" />
                      <div class="text-muted small">{{ $t('wizard.opt.setsTip') }}</div>
                    </el-form-item>
                  </el-form>
                </template>
              </el-table-column>
              <el-table-column :label="$t('wizard.code')" width="120">
                <template #default="{ row }"><el-input v-model="row.code" size="small" :disabled="!canEdit" /></template>
              </el-table-column>
              <el-table-column :label="$t('wizard.title')" min-width="160">
                <template #default="{ row }">
                  <el-input size="small" :disabled="!canEdit" :model-value="txt(row, 'title')" :placeholder="fallback(row, 'title')"
                    @update:model-value="v => setTxt(row, 'title', v)" />
                </template>
              </el-table-column>
              <el-table-column v-if="field.type === 'cards'" :label="$t('wizard.opt.icon')" width="70">
                <template #default="{ row }"><el-input v-model="row.icon" size="small" maxlength="8" :disabled="!canEdit" /></template>
              </el-table-column>
              <el-table-column v-if="field.type === 'swatch'" :label="$t('wizard.opt.color')" width="150">
                <template #default="{ row }">
                  <div class="wz-color">
                    <i :style="{ background: row.color || 'transparent' }"></i>
                    <el-input v-model="row.color" size="small" :disabled="!canEdit" />
                  </div>
                </template>
              </el-table-column>
              <el-table-column v-if="field.type === 'cards'" :label="$t('wizard.opt.goods')" width="180">
                <template #default="{ row }"><goods-select v-model="row.goods" size="small" /></template>
              </el-table-column>
              <el-table-column :label="$t('wizard.opt.hidden')" width="96" align="center">
                <template #header>
                  <el-tooltip :content="$t('wizard.opt.hiddenTip')"><span>{{ $t('wizard.opt.hidden') }}</span></el-tooltip>
                </template>
                <template #default="{ row }"><el-switch v-model="row.hidden" size="small" :disabled="!canEdit" /></template>
              </el-table-column>
              <el-table-column width="112" align="center">
                <template #default="{ $index }">
                  <el-button link size="small" :disabled="!canEdit || $index === 0" @click="move(field.options, $index, -1)">↑</el-button>
                  <el-button link size="small" :disabled="!canEdit || $index === field.options.length - 1" @click="move(field.options, $index, 1)">↓</el-button>
                  <el-button link size="small" type="danger" :disabled="!canEdit || field.options.length <= 1" @click="field.options.splice($index, 1)">×</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="canEdit" size="small" class="mt8" @click="addOption">+ {{ $t('wizard.addOption') }}</el-button>
          </template>

          <el-form-item class="mt20">
            <el-button type="danger" plain :disabled="group.fields.length <= 1" @click="removeField(sel.gi, sel.fi)">{{ $t('wizard.remove') }}</el-button>
          </el-form-item>
        </el-form>

        <el-empty v-else :description="$t('wizard.selectNode')" />
      </section>
    </div>

    <!-- JSON 导入 / 导出 -->
    <el-dialog v-model="jsonOpen" :title="$t('wizard.json')" width="760px">
      <p class="text-muted small">{{ $t('wizard.jsonTip') }}</p>
      <el-input v-model="jsonText" type="textarea" :rows="22" class="wz-json" spellcheck="false" />
      <template #footer>
        <el-button @click="jsonOpen = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" :disabled="!canEdit" @click="applyJson">{{ $t('wizard.jsonApply') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="StationConsoleWizard">
import { computed, defineComponent, h, inject, onMounted, reactive, ref, resolveComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WizardConds from '@/components/WizardConds.vue'
import { getWizard, saveWizard, getWizardTemplate, getWizardTemplates, listSiteGoods } from '@/api/console'
import { identity, hasPermi } from '@/store/identity'
import { toFrontUrl } from '@/utils/siteUrl'
import { t } from '@/i18n'

const TYPES = ['cards', 'options', 'swatch', 'number', 'text', 'date', 'goods']
const OPTION_TYPES = ['cards', 'options', 'swatch']
const isOptions = f => OPTION_TYPES.includes(f?.type)

const canEdit = computed(() => {
  void identity.perms
  return hasPermi(['site:station:edit'])
})

const site = inject('stationSite', ref({}))
const frontUrl = computed(() => {
  const base = toFrontUrl(site.value?.domain)
  return base ? base + '/configure' : ''
})

/* ---------------- 数据 ---------------- */
const enabled = ref(false)
const schema = ref(null)
const original = ref('')
const saving = ref(false)
const langs = computed(() => (identity.langs && identity.langs.length ? identity.langs : ['en']))
const defaultLang = computed(() => langs.value[0])
// 默认编辑中文：管理台主要是我们自己的业务员在用；站点没开中文就编默认语言
const editLang = ref('')

const snapshot = () => JSON.stringify({ e: enabled.value, s: schema.value })
const dirty = computed(() => !!original.value && snapshot() !== original.value)

async function load() {
  const res = await getWizard()
  const data = res.data || {}
  enabled.value = !!data.enabled
  schema.value = data.schemaJson ? normalize(JSON.parse(data.schemaJson)) : null
  original.value = snapshot()
  if (!editLang.value) editLang.value = langs.value.includes('zh') ? 'zh' : defaultLang.value
}

/** 补齐编辑器要用的空位（数组、对象），省得模板里到处判空 */
function normalize(s) {
  s.groups = Array.isArray(s.groups) ? s.groups : []
  for (const g of s.groups) {
    g.showIf = Array.isArray(g.showIf) ? g.showIf : []
    g.fields = Array.isArray(g.fields) ? g.fields : []
    for (const f of g.fields) {
      f.showIf = Array.isArray(f.showIf) ? f.showIf : []
      if (isOptions(f)) {
        f.options = Array.isArray(f.options) ? f.options : []
        for (const o of f.options) o.needs = Array.isArray(o.needs) ? o.needs : []
      }
    }
  }
  return s
}

/** 保存前去掉编辑器补的空位、没填完的条件，和值为空的可选键——后端存的是紧凑的那份 */
function clean(s) {
  const copy = JSON.parse(JSON.stringify(s))
  const conds = list => (list || []).filter(c => c.field && c.in && c.in.length)
  const strip = obj => {
    for (const k of Object.keys(obj)) {
      const v = obj[k]
      if (v === '' || v === null || v === undefined || v === false || (Array.isArray(v) && !v.length)) delete obj[k]
      else if (v && typeof v === 'object' && !Array.isArray(v) && !Object.keys(v).length) delete obj[k]
    }
  }
  for (const g of copy.groups) {
    g.showIf = conds(g.showIf)
    for (const f of g.fields) {
      f.showIf = conds(f.showIf)
      if (!isOptions(f)) delete f.options
      for (const o of f.options || []) {
        o.needs = conds(o.needs)
        if (!o.moq) delete o.moq
        if (!o.price) delete o.price
        strip(o)
      }
      strip(f)
    }
    strip(g)
  }
  return copy
}

/* ---------------- 多语言文字 ---------------- */
function txt(obj, key) {
  return obj?.[key]?.[editLang.value] || ''
}
function setTxt(obj, key, value) {
  if (!obj[key] || typeof obj[key] !== 'object') obj[key] = {}
  if (value) obj[key][editLang.value] = value
  else delete obj[key][editLang.value]
}
/** 这种语言空着时访客会看到什么：英文 → 默认语言 → 随便一种（和后端 pick 同一个顺序） */
function fallback(obj, key) {
  const m = obj?.[key] || {}
  return m.en || m[defaultLang.value] || Object.values(m).find(Boolean) || ''
}

/* ---------------- 树与选择 ---------------- */
const sel = reactive({ kind: 'root', gi: 0, fi: 0 })
const group = computed(() => schema.value?.groups[sel.gi])
const field = computed(() => group.value?.fields[sel.fi])
function select(kind, gi = 0, fi = 0) {
  sel.kind = kind
  sel.gi = gi
  sel.fi = fi
}
function move(list, i, d) {
  const j = i + d
  if (j < 0 || j >= list.length) return
  const [x] = list.splice(i, 1)
  list.splice(j, 0, x)
  if (list === schema.value.groups && sel.kind === 'group' && sel.gi === i) sel.gi = j
}
function hiddenCount(f) {
  return (f.options || []).filter(o => o.hidden).length
}

/** 条件能引用的字段：排在 (gi, fi) 前面的选项类字段（后端 validateSchema 同一条规矩） */
function condFields(gi, fi) {
  const out = []
  schema.value.groups.forEach((g, i) => {
    g.fields.forEach((f, j) => {
      if ((i < gi || (i === gi && j < fi)) && isOptions(f)) {
        out.push({
          code: f.code,
          title: txt(f, 'title') || fallback(f, 'title'),
          options: (f.options || []).map(o => ({ code: o.code, title: txt(o, 'title') || fallback(o, 'title') }))
        })
      }
    })
  })
  return out
}

function uniqueCode(prefix, taken) {
  let n = 1
  while (taken.has(prefix + n)) n++
  return prefix + n
}
const allFieldCodes = () => new Set(schema.value.groups.flatMap(g => g.fields.map(f => f.code)))
const newField = code => ({ code, type: 'options', title: { [editLang.value]: code }, showIf: [], options: [{ code: 'a', title: { [editLang.value]: 'A' }, needs: [] }] })

function addGroup() {
  const code = uniqueCode('group', new Set(schema.value.groups.map(g => g.code)))
  schema.value.groups.push({ code, title: { [editLang.value]: code }, showIf: [], fields: [newField(uniqueCode('field', allFieldCodes()))] })
  select('group', schema.value.groups.length - 1)
}
function addField(gi) {
  schema.value.groups[gi].fields.push(newField(uniqueCode('field', allFieldCodes())))
  select('field', gi, schema.value.groups[gi].fields.length - 1)
}
function addOption() {
  const taken = new Set(field.value.options.map(o => o.code))
  const code = uniqueCode('opt', taken)
  field.value.options.push({ code, title: { [editLang.value]: code }, needs: [] })
}
async function removeGroup(gi) {
  await ElMessageBox.confirm(t('wizard.removeConfirm', { name: txt(schema.value.groups[gi], 'title') || schema.value.groups[gi].code }), { type: 'warning' })
  schema.value.groups.splice(gi, 1)
  select('root')
}
async function removeField(gi, fi) {
  const f = schema.value.groups[gi].fields[fi]
  await ElMessageBox.confirm(t('wizard.removeConfirm', { name: txt(f, 'title') || f.code }), { type: 'warning' })
  schema.value.groups[gi].fields.splice(fi, 1)
  select('group', gi)
}
function onTypeChange() {
  const f = field.value
  if (isOptions(f) && !Array.isArray(f.options)) f.options = [{ code: 'a', title: { [editLang.value]: 'A' }, needs: [] }]
  if (f.type !== 'goods') delete f.source
  if (f.type !== 'number') { delete f.min; delete f.max; delete f.goods }
}

/* ---------------- 选项的联动（sets） ---------------- */
const setsDraft = reactive({})
function setsText(row) {
  if (setsDraft[row.code] !== undefined) return setsDraft[row.code]
  return row.sets && Object.keys(row.sets).length ? JSON.stringify(row.sets) : ''
}
function setSets(row, text) {
  setsDraft[row.code] = text
  if (!text.trim()) { delete row.sets; return }
  try {
    const v = JSON.parse(text)
    if (v && typeof v === 'object' && !Array.isArray(v)) row.sets = v
  } catch { /* 没写完：等写完再生效，保存前 validateSets 会拦 */ }
}
function validateSets() {
  for (const [code, text] of Object.entries(setsDraft)) {
    if (!text.trim()) continue
    try {
      const v = JSON.parse(text)
      if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error()
    } catch {
      ElMessage.error(`${code}: ${t('wizard.opt.setsBad')}`)
      return false
    }
  }
  return true
}

/* ---------------- 商品下拉 ---------------- */
const goodsOptions = ref([])
const GoodsSelect = defineComponent({
  name: 'GoodsSelect',
  props: { modelValue: { type: String, default: '' }, size: { type: String, default: 'default' } },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const ElSelect = resolveComponent('el-select')
    const ElOption = resolveComponent('el-option')
    return () => h(ElSelect, {
      modelValue: props.modelValue || '',
      size: props.size,
      clearable: true,
      filterable: true,
      disabled: !canEdit.value,
      placeholder: t('wizard.noGoods'),
      style: 'width: 100%',
      'onUpdate:modelValue': v => emit('update:modelValue', v || undefined)
    }, () => goodsOptions.value.map(g => h(ElOption, { key: g.code, value: g.code, label: `${g.code} ${g.name}` })))
  }
})

async function loadGoods() {
  try {
    const res = await listSiteGoods({})
    goodsOptions.value = (res.rows || []).map(g => ({ code: g.goodsCode, name: g.title || g.goodsName || '' }))
  } catch { goodsOptions.value = [] }
}

/* ---------------- 模板 / JSON / 保存 ---------------- */
/** 预置模板清单：[{ code, forThisSite }]，本站那份排第一 */
const presets = ref([])
const sitePreset = computed(() => presets.value.find(p => p.forThisSite) || null)
async function loadPresets() {
  try {
    const res = await getWizardTemplates()
    presets.value = res.data || []
  } catch { presets.value = [] }
}

async function loadTemplate(code) {
  const name = t('wizard.preset.' + code)
  if (schema.value) await ElMessageBox.confirm(t('wizard.loadTemplateConfirm', { name }), { type: 'warning' })
  const res = await getWizardTemplate(code)
  schema.value = normalize(JSON.parse(res.data))
  select('root')
}

const jsonOpen = ref(false)
const jsonText = ref('')
function openJson() {
  jsonText.value = schema.value ? JSON.stringify(clean(schema.value), null, 2) : ''
  jsonOpen.value = true
}
function applyJson() {
  try {
    const parsed = JSON.parse(jsonText.value)
    schema.value = normalize(parsed)
    select('root')
    jsonOpen.value = false
  } catch {
    ElMessage.error(t('wizard.jsonBad'))
  }
}

async function save() {
  if (!validateSets()) return
  saving.value = true
  try {
    const json = schema.value ? JSON.stringify(clean(schema.value)) : ''
    const res = await saveWizard(enabled.value, json)
    const data = res.data || {}
    enabled.value = !!data.enabled
    schema.value = data.schemaJson ? normalize(JSON.parse(data.schemaJson)) : null
    for (const k of Object.keys(setsDraft)) delete setsDraft[k]
    original.value = snapshot()
    ElMessage.success(t('wizard.saved'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadGoods()
  loadPresets()
})
</script>

<style scoped>
.wz-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
.wz-sp { flex: 1; }
.wz-note { margin: 0 0 12px; }
.wz-body { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; align-items: start; }
.wz-tree {
  position: sticky; top: 12px; max-height: calc(100vh - 120px); overflow: auto;
  background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: 6px; padding: 8px;
}
.wz-node {
  display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 13px;
}
.wz-node:hover { background: var(--el-fill-color-light); }
.wz-node.on { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.wz-node.root { font-weight: 700; }
.wz-node.group { font-weight: 600; margin-top: 6px; }
.wz-node.field { padding-left: 22px; }
.wz-node-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wz-node-ops { display: inline-flex; }
.wz-type { flex: none; }
.wz-add { padding: 3px 8px 3px 22px; font-size: 12px; color: var(--el-color-primary); cursor: pointer; }
.wz-add-group { margin-top: 10px; width: 100%; }
.wz-edit {
  background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: 6px; padding: 16px 18px;
}
.wz-row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; }
.wz-opt-more { padding: 8px 12px 0; }
.wz-color { display: flex; align-items: center; gap: 6px; }
.wz-color i { flex: none; width: 16px; height: 16px; border-radius: 50%; border: 1px solid var(--el-border-color); }
.wz-json :deep(textarea) { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.section-title { font-weight: 600; margin: 8px 0 8px; }
.small { font-size: 12px; }
.mt8 { margin-top: 8px; }
.mt20 { margin-top: 20px; }
</style>

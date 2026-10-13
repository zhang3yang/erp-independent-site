# 独立站商品规格（组合）各自的 3D 模型 —— 设计

日期：2026-10-09
范围：`station-console`、`station-console-ui` + `station-shared`、`station-site`、独立站库 `sdsstation`。**ERP 不动。**
前置：3D 模型（`2026-09-20-product-3d-model-design.md`）、3D 标注（`2026-09-25-product-3d-marks-design.md`）、
买家定制（`2026-09-29-buyer-3d-customize-design.md`）、模型状态（`2026-10-08-product-3d-states-design.md`）。

## 1. 背景与目标

现在一个商品只挂一个 3D 模型。商家要求**不同规格显示不同模型**，而且两种情况都有：

- 外形相近（同款 S/M/L、黑/灰）——希望 logo 只标一次；
- 外形完全不同（同一商品下「腿套 / 手臂套 / 腰套」）——logo 要各自标。

已确认（用户 2026-10-09 定）：

- **每个规格可以配自己的 `.glb`**；没配的规格用商品主模型（= 现在的行为）。
- **规格模型的 logo 由商家选**：默认「跟随主模型」（主模型的 logo 自动投影过去，同模型状态那套）；
  也可以「单独标注」（在这个规格的模型上用现有标注编辑器标 logo / 屏幕 / 开机键）。
- **模型状态（没气/半充/充满）只属于主模型**：切到有独立模型的规格时状态按钮隐藏。
- **定制页跟着规格走**：从详情页进入带上当前规格；定制页能切规格；方案记下规格编码。

## 2. 不做什么

- 不做规格模型自己的「模型状态」。
- 不做按「维度取值」挂模型（矩阵模式下按格子即每个组合挂；同款不同尺码要同一个模型就每格选同一个文件）。
- 买家定制时 logo 不跨规格投影：买家贴在哪个模型上就存在哪个模型上。
- 跟随主模型的规格不投影屏幕 / 开机键（同模型状态的规则），只投 logo。
- 价格、购物车、询盘、订单不动（规格编码本来就在链路里）。

## 3. 数据

### 3.1 迁移 `sql/station/migration/2026-10-09-combo-model3d.sql`

只跑 `sdsstation`，可重复执行；基线 `01_station_schema.sql` 同步：

```sql
-- u_s_sitegoods_combo（挨着 image）
`model3d`       varchar(500) DEFAULT NULL COMMENT '这个规格自己的 3D 模型 .glb 地址；空 = 用商品主模型',
`model3d_marks` text         DEFAULT NULL COMMENT '这个规格自己的 3D 标注；空 = logo 跟随主模型（前台自动投影）',
-- u_s_custom_plan（挨着 goodscode）
`combocode`     varchar(50)  DEFAULT NULL COMMENT '定制的是哪个规格；空 = 主模型'
```

⚠ 迁移必须先于 jar（`SiteGoodsComboMapper.xml`、`CustomPlanMapper.xml` 列清单会带新列）。
⚠ 换域名脚本 `2026-09-06-file-url-rebase.sql` 加 `u_s_sitegoods_combo.model3d`、`model3d_marks`。

### 3.2 规则

- `model3d` 同主模型：`SiteGoodsService.normalizeMediaUrl`（`https://` 或 `/` 开头）且 `.glb` 结尾（忽略查询串），否则拒绝并说清是哪个规格。
- `model3d_marks` 走 `Model3dMarks.normalize`（严格，拒绝不合规）；**没有 `model3d` 时强制清空**（标注离开模型没意义）。
- 「跟随主模型」= `model3d_marks` 为空。管理台「恢复跟随主模型」就是清空它。

## 4. 后端

### 4.1 保存（`SiteGoodsService.saveCombos`）

平铺和矩阵两种模式的组合提交里都多两个字段 `model3d`、`model3dMarks`，按 3.2 校验后存。

### 4.2 详情接口（`SiteOpenService.comboCards`）

配了模型的组合卡片多两个字段：`model3d`（地址）、`model3dMarks`（对象，`Model3dMarks.toObject`；跟随主模型时为 `null`）。
没配模型的组合不带这两个字段。列表卡片不受影响。

### 4.3 定制页（`CustomPlanService`）

- **编辑视图** `editorView(goodsCode, lang)` 多给 `combos`：
  `[{ code, title, image, model3d, screen, power, own }]`，只列**配了模型**的规格（停用的不列）；
  `own` = 是否单独标注；`screen`/`power` 只在单独标注时从规格自己的标注里取，跟随主模型时为 `null`。
  主模型那份仍在原来的 `model3d`/`screen`/`power`/`states` 字段里。
- **保存** 请求体多一个可选 `combo`（规格编码）：
  - 为空 → 照旧用主模型；
  - 不为空 → 必须是这个商品下**可选且配了模型**的组合，否则 `custom.badData`；
    模型地址、屏幕、开机键全部从这个组合取（前端提交的不收，同现在「只从商家那份来」）；
    跟随主模型的规格没有屏幕 → 带了开机画面就 `custom.badData`（同现在没标屏幕的规则）。
  - `plan.combocode` = 规格编码，`plan.model3d` = 规格模型；规格方案**不存模型状态快照**（状态只属于主模型）。
- **方案公开视图** 多给 `comboCode`、`comboTitle`（按当前语言；组合已删/停用时只给编码，不报错）。

## 5. 共享模块 `station-shared/model3d/states.js` 扩展

规格模型在前台按「主模型的一种特殊状态」处理，复用加载、缓存、对齐、淡入淡出：

- 状态项可以带 `marks`（规格自己的标注）：带了就**直接用它**画（含屏幕/开机键），不投影；没带就照旧投影主模型的 logo。
- 切换器加 `currentMarks()`：返回当前显示的那个模型的标注句柄（主模型时返回 `null`，由宿主用自己的那份），
  宿主据此做开机、点开机键、正对屏幕。
- 状态项的 `id` 由宿主约定：模型状态用 `s1…`，规格用 `c:<组合编码>`，两类不会撞。

## 6. 前台

### 6.1 详情页展示台

- `Product3DViewer` 新 prop `variants`（配了模型的规格 `[{ code, name, model, marks }]`，一次全给，便于缓存和预加载）+ `variantCode`（当前选中的规格编码）。
- `variantCode` 在 `variants` 里 → 切换器 `show('c:' + code)`（首次加载、对齐、淡入淡出，视角不动）；否则回到当前模型状态或主模型。
- 规格模型显示时：模型状态按钮隐藏；开机按钮看当前模型的标注里有没有屏幕（`currentMarks()`）。
- 规格模型加载失败：停在当前模型，展示台下方提示「该规格的 3D 模型加载失败」；规格本身照常选中（价格、询盘不受影响）。
- 已加载的规格模型缓存；首屏后空闲预加载**当前规格**以外的规格模型最多 3 个（避免一个商品十几个规格全下）。

### 6.2 定制页 `customize/[code].vue`

- 详情页「定制我的 logo」链接带上当前规格：`?combo=<编码>`（当前规格没有独立模型时不带）。
- `editor.combos` 非空时，画布上方出一排规格切换：「主模型名（商品名）」+ 各规格名。
  - 切规格 = 换画布模型（画布按 `src` 重建）。已贴的 logo 会清空——**先确认**（坐标属于原模型）；没贴过就直接切。
  - 当前是单独标注且有屏幕的规格 → 开机画面区照常；否则不出开机画面区。
  - 只有主模型显示模型状态按钮。
- 保存时带 `combo`。`?from=<方案号>` 进来时规格取方案里的 `comboCode`（规格已不可用就退回主模型并提示）。

### 6.3 方案页

显示「规格：xxx」；画布用方案快照里的模型；「在此基础上修改」带 `?combo=` 和 `?from=`。

### 6.4 文案

「该规格的 3D 模型加载失败」「切换规格会清空已贴的 logo，确定吗？」「规格：{name}」进 `useShopCopy.ts`（9 语）。

## 7. 管理台（`station-console-ui`）

平铺组合行、矩阵格子（`ComboMatrix.vue`）各加一栏「3D 模型」：

- 选模型（复用 `MediaPicker` 模型类型）/ 清除；
- 标签：「跟随主模型」或「已单独标注」；
- 「单独标注」：用这个规格的模型打开 `ModelMarksEditor`（弹窗，规则同主模型）；
- 「恢复跟随主模型」：清空这个规格的标注（二次确认）；
- 「预览」：`ModelPreview` 弹窗，跟随主模型时传主模型 + 主模型 logo 看投影效果（复用「预览全部状态」那套）。

提交时组合数据里带 `model3d`、`model3dMarks`（平铺 `Goods.vue` 组 combos、矩阵 `utils/comboMatrix.js` 的 `toPayload`）。
i18n 三份（zh/en/fr）。

## 8. 错误处理

| 情况 | 表现 |
|---|---|
| 规格模型 404 / CORS / 坏文件 | 详情页停在当前模型并提示；定制页画布进「加载失败」兜底，可切回别的规格 |
| 定制保存时规格已停用 / 没模型 / 不属于这个商品 | `custom.badData`（同现有伪造数据的处理） |
| 规格单独标注的 JSON 坏了（手改库） | `toObject` 返回 null → 前台当「跟随主模型」 |
| 方案里的规格后来被删 | 方案页照常显示（用快照模型），规格名只显示编码；「在此基础上修改」退回主模型并提示 |

## 9. 测试与验收

- 后端单测：组合 `model3d` 地址/扩展名校验、没模型时标注被清空、标注严格校验；详情只给配了模型的组合带字段；
  定制编辑视图的 `combos`（own / screen 取法）；保存带 `combo` 时取规格模型、伪造/停用/无模型规格被拒、
  跟随主模型的规格带开机画面被拒；方案视图的规格名。
- `states.js`：状态项自带 `marks` 时不投影、`currentMarks()` 返回对的句柄（Node 几何测试补用例）。
- 前台 e2e（本机，无头 Edge）：测试商品两个规格（一个跟随主模型、一个单独标注带屏幕）——
  详情页切规格换模型、状态按钮隐藏/出现、开机按钮随标注出现；定制页 `?combo=` 进入、切规格清空确认、保存后方案页显示规格。
- 管理台：本机构建通过（管理台要 SSO 登录，浏览器实点留给上线后人工看）。

## 10. 发版

迁移 → 后端 jar → 管理台 → 前台，顺序不能反。只发康辰（106.54），基线 `c0e27409`，照 `deploy/scripts/kangchen-release-2026-10-09.sh` 的做法出一份新脚本。

## 11. 影响面

- SQL：`migration/2026-10-09-combo-model3d.sql`（新）、`01_station_schema.sql`、`migration/2026-09-06-file-url-rebase.sql`
- `station-console`：`SiteGoodsCombo`、`SiteGoodsComboMapper.xml`、`SiteGoodsService`、`SiteOpenService`、
  `CustomPlan`、`CustomPlanMapper.xml`、`CustomPlanService`、测试
- `station-shared`：`model3d/states.js`（+ `.d.ts`）、`ModelPreview.vue`（如需）
- `station-console-ui`：`views/Goods.vue`、`components/ComboMatrix.vue`、`utils/comboMatrix.js`、i18n 三份
- `station-site`：`components/Product3DViewer.client.vue`、`components/CustomizeCanvas.client.vue`、
  `pages/products/[code].vue`、`pages/customize/[code].vue`、`pages/customize/plan/[id].vue`、
  `lib/customize.ts`、`composables/useShopCopy.ts`、`composables/useSite.ts`（类型）

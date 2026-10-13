# 独立站商品 3D 模型状态（多模型切换，首个用例：腿套充气三态）—— 设计

日期：2026-10-08（同日修订：logo 自动投影到各状态、定制页/方案页也显示状态）
范围：`station-console`、`station-console-ui` + `station-shared`、`station-site`、独立站库 `sdsstation`。**ERP 不动。**
前置：商品 3D 模型链路（`2026-09-20-product-3d-model-design.md`）、3D 标注（`2026-09-25-product-3d-marks-design.md`）、
买家 3D 定制方案（`2026-09-29-buyer-3d-customize-design.md`）。

## 1. 背景与目标

康辰站的空气压力腿套（模型 `SMDL0000000006.glb`，混元生 3D 生成）要展示**充气过程**：
没气 → 充一半 → 充满。混元生成的模型是一整块固定形状的网格，没有形变动画、没有独立零件，
模型自己「不会充气」。

做法（用户 2026-10-08 定，方案 A）：**每个状态一个模型**，运营照三种状态各拍照、各生成一个 glb，
在管理台挂到商品的「模型状态」上；前台展示台下方出一排状态按钮，点了淡入淡出切换模型。

已确认：

- **通用「模型状态」**，不写死充气：商家自己加状态、起名字（多语言），以后「折叠/展开」「关盖/开盖」照用。
- **标注只存一份（主模型坐标），logo 自动投影到其它状态显示**。商家、买家都只在主模型上贴；
  切到别的状态时前台现场把 logo 投到新模型表面。屏幕/开机键只在主模型上。
- **详情页、定制页、方案页都显示状态按钮**；定制页里贴/拖 logo 只能在主状态做。
- 现在只有一个模型，**允许先建状态、模型以后补**；没模型的状态前台不出现。

⚠ 投影射线必须**双面**打：鼓起来的状态比主模型大，logo 原位置在新网格内部，往外打碰到的是背面（实现时 Node 几何测试抓到）。

⚠ 投影的前提是各状态模型**朝向一致**——运营拍三种状态的照片要用同一个角度。

## 2. 不做什么

- 不做程序化「吹胀」形变（方案 B），不做 glb 内置动画/morph 播放。
- 不做每状态单独标注、不做每状态的手动位置/旋转校正（先靠自动对齐 + 投影；朝向不一致再议「旋转校正」）。
- 屏幕、开机键不投影，只在主状态显示。
- 商品列表卡片不出现状态。
- 状态之间不做过渡形变动画，只做淡入淡出。
- 最多 5 个状态。

## 3. 数据

### 3.1 新列

迁移 `sql/station/migration/2026-10-08-goods-model3d-states.sql`（只跑 `sdsstation`，可重复执行），
基线 `sql/station/01_station_schema.sql` 同步，两列：

```sql
-- u_s_sitegoods
`model3d_states` text DEFAULT NULL COMMENT '3D 模型状态 JSON（第一个=主模型 model3d，其余各一个 glb）；空 = 没有状态'
-- u_s_custom_plan
`model3d_states` text DEFAULT NULL COMMENT '保存方案时商品模型状态的快照（只含有模型的状态，名字全语言）；空 = 没有状态'
```

⚠ 迁移必须先于 jar（两个 mapper 的列清单会带上新列，反了查商品/存方案直接 500）。
⚠ 换域名脚本 `2026-09-06-file-url-rebase.sql` 的清单注释和 UPDATE 都要加这两列——里面埋着模型地址。

### 3.2 商品上的 JSON（版本 1）

```json
{"v":1,"states":[
  {"id":"s1","name":{"zh":"没气","en":"Deflated"}},
  {"id":"s2","name":{"zh":"半充","en":"Half"},"model":"https://file.kangchen.vclinker.com/model/…/b.glb"},
  {"id":"s3","name":{"zh":"充满","en":"Full"}}
]}
```

- `states[0]` **永远是主模型**：不带 `model`（带了也丢弃），模型就是商品的 `model3d`。标注坐标属于它。
- 其余状态 `model` 可缺省 = 「先建着，以后补」。
- `id`：`s` + 数字，同一商品内唯一，管理台生成；前台用它当按钮 key 和模型缓存 key。
- `name`：`{语言: 名字}`，只收**站点开着的**语言（`site.langList()`），别的语言静默丢掉——站点关掉一种语言后
  老数据照样能保存；每个名字 1~30 字，默认语言那个必填（没有就拒）。

### 3.3 后端校验（新类 `Model3dStates`，照 `Model3dMarks` 的写法）

`normalize(String json)`：严格，不合规抛给商家看的中文 `ServiceException`，说清是第几个状态哪一项；
合规则**白名单重组**（只留认识的字段）后返回 JSON 字符串；空/没有状态返回 `null`。

- 状态 1~5 个；`id` 格式与唯一性；`name` 语言白名单、长度、默认语言必填。
- `model`：同 `SiteGoodsService.normalizeModelUrl` 一字不差（只认 `https://`、`/` 开头），且以 `.glb` 结尾（忽略查询串）。
- 商品没有主模型（`model3d` 空）却带了状态 → 存为 `null`（不报错，状态依附主模型）。

另给一个宽松的 `toPublic(String json, String lang, String defaultLang)`：解析不了返回 `null`；
只留 `states[0]` + 有 `model` 的状态，名字换成当前语言（缺译回退默认语言），**少于 2 个返回 `null`**。

`SiteGoodsService` 里和 `Model3dMarks.normalize` 同两处调用（新增、修改）；字段随 `SiteGoods` 请求体绑定，控制器不用改。

### 3.4 下发

形状统一为（前台三处共用一个类型）：

```json
[{"id":"s1","name":"没气"},{"id":"s2","name":"半充","model":"https://…/b.glb"}]
```

| 接口 | 字段 | 来源 |
|---|---|---|
| 商品详情（`SiteOpenService`，`model3d`/`model3dMarks` 旁） | `model3dStates` | 商品当前 `toPublic` |
| 定制页数据（`CustomPlanService` 编辑视图，`screen`/`power` 旁） | `states` | 商品当前 `toPublic` |
| 方案公开视图 | `states` | **方案快照**按当前语言换名 |

列表卡片 `toCard` 不带。老前台不认识这些字段，忽略即可（非破坏性）。

### 3.5 方案快照

买家保存方案时，`CustomPlanService.save` 把商品当时的状态存进 `u_s_custom_plan.model3d_states`：
只留有模型的状态、**名字保留全语言**（方案页按看的人的语言换名）。快照规则同 `marks`：之后商家换模型、删状态，
已有方案不变。前端提交的数据里**不收**状态（同屏幕/开机键「只从商家那份来」）。

## 4. 共享模块 `station-shared/model3d/states.js`（新）

三个宿主（详情页展示台、定制页画布、管理台预览）共用，**唯一**实现「对齐 + 投影 + 切换」；
`marks.js` 不动，状态模型上的 logo 照样用 `createMarks` 画。

### 4.1 对齐 `alignTo(mainRoot, stateRoot)`

算两者包围盒，给 state 的**外层包**设缩放（最长边与主模型相等）和平移（中心对齐）；**不动 `gltf.scene` 本身**（同标注规则）。

### 4.2 投影 `projectLogos(logos, mainScene, stateScene)`

对每个 logo（主模型 `gltf.scene` 局部坐标 `p`、`n`、`size`）：

1. `p`、`n` 从主场景局部 → 世界 → 状态场景局部（`n` 用法线矩阵）；
2. 从该点沿 `+n`、`-n` 各发一条射线打状态模型网格，最远距离 = 状态包围盒最长边的 15%，取最近命中；
3. 命中点为新 `p`，命中面法线（转到局部、翻到与 `n` 同侧）为新 `n`；
4. `size` 按两边世界缩放比换算（视觉大小不变），`rot`、`opacity`、`src`、`id` 原样；
5. 两个方向都没命中 → 这个 logo **在该状态隐藏**（不悬空）。

结果按状态 `id` 缓存；logo 变了（定制页拖动/新增）只重算该状态当前显示的那份。

### 4.3 切换控制 `createStateSwitcher(host, opts)`

管加载（`GLTFLoader.loadAsync(corsUrl(model))`）、缓存（按 `id`）、对齐、投影、淡入淡出、dispose；
「最后一次点击为准」也在这里。宿主只给：场景、主模型根、当前标注、要不要画屏幕/开机键、加载状态回调。

## 5. 管理台（`station-console-ui`）

### 5.1 入口

「商品管理 → 编辑商品」3D 模型那一栏下方，新增「模型状态」区；商品没挂主模型时整区禁用并提示「先选主模型」。

### 5.2 编辑区

- 第一行固定「主模型」：只能改名字，不能删、不能挪。
- 「+ 添加状态」（满 5 个禁用）：名字 + 模型（复用 `MediaPicker` 选/传 glb）。
- 每行可删除、上移/下移（第一行除外）。
- 名字按站点语言分别填，语言切换方式照商品文案；默认语言那格必填。
- 没模型的行显示灰字「未上传模型，前台不显示」。
- 「全部语言翻译」顺带补状态名（`translationSource` 加一组），**只补空不覆盖**。
- 「预览全部状态」按钮：弹窗（**只在弹窗里**，规则见 3D 模型设计）里显示主模型 + 状态按钮 + **商家 logo 投影后的效果**，
  上传完一眼看出准不准。`ModelPreview.vue` 加可选 `states` / `marks` 两个 prop，接 `states.js`。

保存时整段 JSON 随商品一起提交（字段 `model3dStates`），后端校验失败的中文提示原样弹出。

## 6. 前台（`station-site`）

### 6.1 详情页展示台 `Product3DViewer.client.vue`

新 prop `states`；有值时展示台下方出一排按钮，样式同定制页 `cz-chip`，默认选中第一个。

切换：目标状态没加载过 → 加载（按钮转圈）→ 对齐 → 投影商家 logo → **淡入淡出约 300ms**
（`prefers-reduced-motion` 时直接切）；**相机和控制器不动**，便于对比外形。
首屏渲染完后 `requestIdleCallback` 预加载其余状态。

离开主状态时先 `setPower(false, {instant:true})`，屏幕/开机键隐藏；logo 换成投影那份。回到主状态全部还原。

### 6.2 定制页 `customize/[code].vue` + `CustomizeCanvas.client.vue`

- 画布下方同样一排状态按钮（接口 `states`）。买家的 logo（含未保存的本地图）实时投影到当前状态。
- **只在主状态能放置、拖动、删除 logo**；在其它状态时这些操作置灰，旁边提示「请切回『{主状态名}』调整 logo」。
- 「模拟开机」按钮只在主状态可用。
- 保存方案的请求体不变（状态由后端从商品取快照）。

### 6.3 方案页 `customize/plan/[id].vue`

用方案快照里的 `states` 出按钮，logo 投影显示，只读。

### 6.4 文案

「该状态加载失败」「请切回『{name}』调整 logo」进 `useShopCopy.ts`（COPY 与 CUSTOMIZE 字典，9 语）。
按钮文字是商家起的名字（接口已按语言换好）。

## 7. 错误处理

| 情况 | 表现 |
|---|---|
| 某状态 glb 加载失败（404/CORS/坏文件） | 停在当前状态，按钮下方提示「该状态加载失败」，其它状态照常可点；控制台记一条 |
| 加载中又点了别的状态 | 以最后一次点击为准，先前的加载完只进缓存不切换 |
| 某 logo 投影没命中 | 该状态下隐藏这个 logo，其它 logo 照常 |
| 状态 JSON 坏了（手工改库） | `toPublic` 解析不了返回 `null`，前台不出按钮，主模型照常 |
| 主模型被换掉 | 状态保留；标注规则不变（标注本就绑主模型）；投影按新主模型重算 |
| 不支持 WebGL | 走现有兜底，不出按钮 |

## 8. 测试与验收

- 后端单测 `Model3dStatesTest`：数量上限、id 格式/重复、名字语言白名单/长度/默认语言必填、模型地址规则、
  `states[0]` 的 model 被丢弃、没主模型存 null、坏 JSON；`toPublic` 只留有模型的、按语言换名与回退、少于 2 个返回 null。
- `CustomPlanServiceTest` 补：保存时快照状态（全语言名字）、前端塞的 states 被忽略、方案视图按语言换名。
- `states.js`：本机测试页（同 `marks.js` 那次的无头 Edge 做法）——两个不同大小/位置的模型对齐、
  logo 投到放大版模型表面、射线没命中时隐藏。
- 前台 e2e：本机把现有模型复制三份（再造一份放大 1.2 倍的 glb 模拟「充满」）当三个状态，无头 Edge 截图：
  详情页切换/对齐/logo 投影/失败提示；定制页在非主状态时贴 logo 置灰；方案页切换。
- 管理台：本机实点添加/排序/删除/翻译/保存/预览全部状态，校验失败提示。
- 两个前端构建都要过（展示台、画布都引 `@station-shared`）。

## 9. 发版

迁移 SQL → 后端 jar → 管理台 → 前台，顺序不能反。康辰在新机 106.54（Linux + systemd），走 Linux 发版流程。
法国站不发（还没有 3D 标注那一列，另议）。

## 10. 影响面清单

- SQL：`migration/2026-10-08-goods-model3d-states.sql`（新）、`01_station_schema.sql`、`migration/2026-09-06-file-url-rebase.sql`
- `station-console`：`SiteGoods`、`SiteGoodsMapper.xml`（列清单/resultMap/insert/update）、`Model3dStates`（新）、
  `SiteGoodsService`、`SiteOpenService`、`CustomPlan`、`CustomPlanMapper.xml`、`CustomPlanService`、测试
- `station-shared`：`model3d/states.js`（新）、`ModelPreview.vue`
- `station-console-ui`：`views/Goods.vue`（模型状态区、翻译源、预览）、i18n 三份
- `station-site`：`components/Product3DViewer.client.vue`、`components/CustomizeCanvas.client.vue`、
  `pages/products/[code].vue`、`pages/customize/[code].vue`、`pages/customize/plan/[id].vue`、
  `composables/useShopCopy.ts`、`composables/useSite.ts`（类型）

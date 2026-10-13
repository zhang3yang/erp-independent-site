# 独立站商品 3D 标注（logo 贴花 + 开机屏幕）—— 设计

日期：2026-09-25
范围：`station-console`、`station-console-ui` + `station-shared`、`station-site`、独立站库 `sdsstation`。**ERP 不动。**
前置：商品 3D 模型链路（`2026-09-20-product-3d-model-design.md`，2026-09-21 已上线）。

## 1. 背景与目标

商品可以挂一个 `.glb` 模型，前台展示台能转着看。商家想再进一步：

1. 在模型上**自由贴 logo**（任意图片、任意位置、可以多个）；
2. 对带屏幕的设备（首个用例：esvafit 站的空气压力治疗仪主机，`SIMG0000000030.png`），
   买家能**点开机键 → 屏幕亮起 → 先显示 logo → 再进工作界面**，再点关机。

关键约束：模型来自 AI 图生 3D（腾讯混元 3D 网站生成后手动上传），是**一整块网格 + 一张烘焙贴图**，
屏幕、按键都不是独立零件；商家运营**不会用建模软件**。所以「屏幕在哪、开机键在哪、logo 贴哪」
必须在**管理台里用鼠标在模型上点出来**，存成商品上的标注数据，前台加载模型后现场叠加。

已确认（用户 2026-09-25 定）：

- 在管理台里点选标注，不走 Blender 分件、不把标注烧进 glb（方案 A）。
- logo 由商家每个商品自由上传、自由摆放，可多个。
- 屏幕开机流程：**先 logo 再进工作界面**；工作界面是一张静态图（不做会动的数字）。

## 2. 不做什么

- 不做图生 3D、不接混元生 3D API（后续单独立项；API 已确认存在：`SubmitHunyuanTo3DJob`，支持 `MultiViewImages`）。
- 不改 glb 文件本身，不做服务端模型解析。
- 工作界面不做动画（数字跳动、气囊逐格点亮）。
- 一个商品一块屏幕、一个开机键；logo 最多 10 个。
- 标注不分语言（logo、界面图对所有语言通用）。
- 不给前台加 Draco / Meshopt 解码器（另议）。

## 3. 数据

### 3.1 新列

迁移 `sql/station/migration/2026-09-25-goods-model3d-marks.sql`（只跑 `sdsstation`，可重复执行），
基线 `sql/station/01_station_schema.sql` 同步：

```sql
ALTER TABLE u_s_sitegoods ADD COLUMN model3d_marks TEXT DEFAULT NULL
  COMMENT '3D 标注 JSON（logo 贴花/屏幕/开机键），空 = 没有标注' AFTER model3d;
```

（迁移脚本用 information_schema 判断列是否已存在，保证可重复执行，照 `2026-09-20-goods-model3d.sql` 的写法。）

`2026-09-06-file-url-rebase.sql` 的爆炸半径清单注释里补这一列：JSON 里含图片 URL，
按字符串 `REPLACE` 同样适用。

### 3.2 JSON 结构（版本 1）

```json
{
  "v": 1,
  "model": "https://file.../model/2026/09/25/SMDL0000000003.glb",
  "logos": [
    { "id": "a1", "src": "https://file.../image/...png",
      "p": [0.01, 0.12, 0.034], "n": [0, 0, 1],
      "size": 0.03, "rot": 0, "opacity": 1 }
  ],
  "screen": {
    "p": [0, 0.1, 0.035], "n": [0, 0, 1], "w": 0.045, "h": 0.06, "rot": 0,
    "bootSrc": "https://file.../image/...png", "bootBg": "#000000", "bootMs": 1500,
    "runSrc": "https://file.../image/...png"
  },
  "power": { "p": [0, 0.02, 0.035], "n": [0, 0, 1], "r": 0.006 }
}
```

- **坐标系 = glb 场景自身的坐标**（`gltf.scene` 的局部空间），不是取景后的世界坐标。
  管理台预览和前台展示台的 `frameModel` 挪法不同（一个包围盒中心归零，一个只挪 xz 并落地），
  标注挂在 `gltf.scene` 下面就跟着模型走，两边一致。
- `p` 点、`n` 表面法线（单位向量）；`size` / `w` / `h` / `r` 用模型单位；`rot` 为绕法线旋转的角度（度）。
- logo 的高度按图片原始宽高比从 `size`（宽）推出，不入库。
- `model` 记标注时的模型地址。与商品当前 `model3d` 不一致 → 管理台提示「模型已更换，请检查标注位置」；
  前台不管，照坐标贴。
- `screen`、`power`、`logos` 都可缺省。`power` 在没有 `screen` 时前台忽略。

### 3.3 后端校验（`SiteGoodsService`）

新增 `static String normalizeMarks(String json)`，`createGoods` / `saveGoods` 在 `normalizeMediaUrl` 旁调用：

- 空白 → NULL；整串 > 20 KB → 拒绝。
- 解析失败、`v` ≠ 1 → 拒绝（与规格 JSON 同理：不静默存坏数据）。
- 所有数字必须有限，坐标绝对值 ≤ 10000；`size`/`w`/`h`/`r` > 0；`rot` ∈ [-180, 180]；
  `opacity` ∈ [0.1, 1]；`bootMs` ∈ [0, 10000]；`bootBg` 匹配 `#RRGGBB`；`n` 非零向量。
- logos ≤ 10 个。
- 所有图片地址（`src` / `bootSrc` / `runSrc` / `model`）只认 `https://` 和 `/` 开头、≤ 500 字符
  （复用 `normalizeMediaUrl` 的判定）；不合规 → 拒绝。
- 未知字段丢弃；按白名单重新序列化后入库（防止任意内容借道下发到前台）。

拒绝走现有「保存失败 + 提示」通道，文案进 i18n。编辑器产出的数据不会触发拒绝，只防篡改和 bug。

### 3.4 下发

`SiteGoods` 加 `model3dMarks`；`SiteGoodsMapper.xml` 的 resultMap、`columns`、insert、update 四处加列。
`SiteOpenService.goodsDetail` 下发 `model3dMarks`（**解析后的对象**，不是字符串）；`toCard` 不带。

## 4. 共享渲染模块 `station-shared/model3d/marks.js`

纯 JS + three，无 Vue。管理台编辑器和前台展示台**用同一份**，保证「管理台看到的就是前台的样子」。

```js
export function createMarks(root, marks, { renderer, onChange }) → {
  group,              // 已挂到 root（= gltf.scene）下
  setPower(on, { instant }),
  isOn(),
  hitPower(raycaster),// 射线是否打中开机键光圈
  tick(dtMs),         // 光圈呼吸、屏幕渐变
  dispose()
}
export function parseMarks(value)   // 字符串或对象 → 合法 v1 对象或 null（前台兜底用，和后端同一套范围）
```

- **logo**：从 `p + n·ε` 沿 `-n` 打一条射线找到命中的网格，用 `DecalGeometry`（`three/examples/jsm/geometries/DecalGeometry.js`）
  投到该网格上；材质 `MeshStandardMaterial`（`map` = logo，`transparent`，`depthWrite: false`，`polygonOffset`），受光照。
  射线打空 → 退化为贴在 `p` 处、朝 `n` 的平面。
- **屏幕**：`PlaneGeometry(w, h)`，放在 `p + n·ε`，朝 `n`，绕法线转 `rot`。
  关机：`MeshStandardMaterial` 近黑、低粗糙度（黑玻璃）。
  开机：`MeshBasicMaterial`（`toneMapped: false`，不受光照 = 发光）。
  开机贴图 = canvas 合成（`bootBg` 铺底 + logo 按 contain 居中占 70%）；工作界面贴图 = `runSrc` 按 cover 裁切。
  开机时序：黑 → 0.4 s 渐亮到开机图 → 停 `bootMs` → 0.4 s 交叉渐变到工作界面。关机：0.3 s 渐黑。
  `prefers-reduced-motion` 或 `instant` → 无渐变直接切。
- **开机键**：`p + n·ε` 处一个朝 `n` 的圆环（`RingGeometry`，半径 `r`），`depthTest: true`，
  所以转到背面会被机身挡住；关机时呼吸闪烁，开机时常亮品牌色。
- 贴图用 `TextureLoader` + `crossOrigin = 'anonymous'`；单张加载失败只跳过该项，`console.warn`，不抛。
- `dispose()` 释放全部几何、材质、贴图。

**依赖解析**：模块裸导入 `three`。`station-console-ui` 的 vite 别名已把共享目录里的 `three` 指到宿主
`node_modules`；`station-site` 的 `nuxt.config.ts` 新增同样的别名（`@station-shared` 与 `three`）和
`vite.server.fs.allow`。必须保证全站只有一个 three 实例（否则 `instanceof` 判定失效）。
这是前台**第一次**引用共享目录——只是构建期引用，部署产物互不依赖；`station-shared/README.md` 更新
「宿主」表和「改这里要验两个构建」。前台是 TS，给模块配一个 `marks.d.ts`。

## 5. 管理台（`station-console-ui` + `station-shared`）

### 5.1 入口

`Goods.vue` 的「3D 模型」区在「预览」旁加「标注」按钮（选了模型才出现）。表单装载 `form.model3dMarks`
→ `siteMarks`；提交带 `model3dMarks: siteMarks || null`（JSON 字符串）。移除模型时标注保留，不清。

### 5.2 编辑器 `station-shared/ModelMarksEditor.vue`

挂在 `el-dialog`（宽 90vw，`destroy-on-close`，与 `ModelPreview` 同一条显存规矩）。异步组件引入。

- 左：画布（复用 `ModelPreview` 的场景搭法：RoomEnvironment、OrbitControls，**编辑时不自转**）。
- 右：面板。
  - **logo 列表**：「添加 logo」→ `MediaPicker type="image" :limit="1"` 选图 → 进入放置模式：
    鼠标下实时显示半透明贴花预览，单击落点（射线命中点 + 面法线，换算到 `gltf.scene` 局部空间）。
    每条：缩略图、大小/旋转/透明度滑块、「重新放置」、「删除」。满 10 个按钮置灰。
  - **屏幕**：「设置屏幕」→ 单击落中心；宽、高、旋转滑块；开机 logo 图、背景色（`el-color-picker`，无透明度）、
    logo 停留秒数（0–10，步长 0.1）、工作界面图。编辑期间屏幕显示工作界面图以便对齐边框。「删除」。
  - **开机键**：「设置开机键」→ 单击落点；光圈大小滑块；「删除」。
- 单击与拖动区分：pointerdown→pointerup 位移 < 5 px 才算点选，否则是转视角。
- 底部：「模拟开机」（走一遍 `setPower(true)` 全流程，再点关机）、「取消」、「确定」。
  确定时写回 `siteMarks`，并把 `model` 设为当前模型地址。
- 顶部提示：`marks.model` 与当前模型不一致时显示「模型已更换，请检查标注位置」。
- 滑块范围按模型包围球半径 R 换算：logo 0.02R–1R，屏幕宽高 0.02R–1.5R，光圈 0.005R–0.2R。
- 文案全部走 i18n（`station-console-ui/src/i18n/locales/{zh,en,fr}.js`，键 `goods.marks.*`；
  共享组件内用 `useStationT`）。

## 6. 前台（`station-site`）

- `useSite.ts` 商品类型加 `model3dMarks?: unknown`。
- `pages/products/[code].vue` 把 `p.model3dMarks` 作为 prop `marks` 传给 `Product3DViewer`。
- `Product3DViewer.client.vue`：
  - `loadModel` 之后 `parseMarks(props.marks)`，非空则 `createMarks(gltf.scene, …)`；`tick` 里调 `marks.tick`。
    **只对真实 glb 生效**，占位模型不挂标注。
  - 画布 pointer 事件做点选（< 5 px 位移），射线命中开机键 → 切换。
  - 有 `screen` 且有 `power` 时，展示台按钮行多一个「开机 / 关机」按钮（与现有「自动旋转」「重置」同排同款）。
  - 开机时：`autoRotate = false`，复用现有 tween，把相机飞到**正对屏幕**（方向 = 屏幕法线转世界坐标，
    距离 = 当前视角 0 的拟合距离，目标 = 屏幕中心）。关机不动相机。
  - `onBeforeUnmount` 调 `marks.dispose()`。
- 文案：`useShopCopy.ts` 九种语言各加 `d3PowerOn`、`d3PowerOff`（**不留空串**）。

## 7. 错误处理

| 情况 | 表现 |
|---|---|
| logo / 屏幕图加载失败（含 CORS） | 跳过该项，其余照常；控制台 warn |
| 标注 JSON 坏或版本不认识 | `parseMarks` 返回 null，当作没标注 |
| 模型换了，坐标对不上 | 前台照贴；管理台提示检查 |
| logo 射线打空（点在边缘） | 退化成平面贴片 |
| 模型本身加载失败 | 与现在一致：海报 + 「暂不可用」 |
| 不支持 WebGL | 与现在一致 |
| 后端校验不过 | 保存失败，提示原因 |

**CORS 前置检查**：贴图走 `TextureLoader`（fetch/img crossOrigin），与 glb 一样受 CORS 管。
康辰桶已配。**esvafit 的桶上线前必须验**：
`curl -D- -H "Origin: https://<esvafit 前台域>" <图片URL>` 要回 `Access-Control-Allow-Origin`，
管理台域同样验一遍；没有就先在桶上加规则（同 `station-3d-model` 记忆里的配法）。

## 8. 测试与验收

后端单测（`station-console/src/test/.../service/`，纯 Mockito）`SiteGoodsServiceMarksTest`：
合法 JSON 原样（规范化后）保留；空白 → NULL；> 20 KB、坏 JSON、`v`≠1、11 个 logo、http 地址、
NaN/超范围数字、非法颜色 → 拒绝；未知字段被丢弃。
`SiteOpenServiceModelTest` 补：详情下发 `model3dMarks` 为对象，`toCard` 不含。

前端：`station-console-ui`、`station-site` 各 `npm run build` 通过；
`marks.js` 的 `parseMarks` 用 node 跑一组断言（与后端同一组用例）。

手工验收（本机起 console + site；模型用用户提供的空气泵混元 glb）：

1. 编辑器贴 2 个 logo、调大小旋转、重新放置、删除；标屏幕、标开机键；模拟开机走完全流程。
2. 保存、重开，标注都在；换一个模型，出现「模型已更换」提示。
3. 前台：logo 随模型转；光圈转到背面被挡住；点光圈与点「开机」按钮都能开机，
   相机飞到正对屏幕，logo 停 1.5 s 后切工作界面；再点关机。
4. 窄屏（固定宽 iframe，见 `station-site-local-preview` 记忆）按钮可点。
5. 老商品（无标注）与现在完全一致；无 3D 模型的商品不受影响。
6. 线上前：esvafit 桶 CORS 验过。

发版顺序：迁移 SQL → console jar → console-ui 静态包 → site（同 3D 模型那次，SQL 与 jar 不能反）。
流程按 `station-deploy-procedure`；基线见 `station-theme-per-site`（康辰三模块 = `03ed9613` 整棵）。

## 9. 影响面清单

- SQL：`migration/2026-09-25-goods-model3d-marks.sql`（新）、`01_station_schema.sql`、`2026-09-06-file-url-rebase.sql`（注释）
- station-console：`SiteGoods`、`SiteGoodsMapper.xml`、`SiteGoodsService`（`normalizeMarks`）、`SiteOpenService`、单测
- station-shared：`model3d/marks.js` + `marks.d.ts`（新）、`ModelMarksEditor.vue`（新）、`README.md`
- station-console-ui：`views/Goods.vue`、`i18n/locales/{zh,en,fr}.js`
- station-site：`nuxt.config.ts`（别名 + fs.allow）、`components/Product3DViewer.client.vue`、
  `pages/products/[code].vue`、`composables/useSite.ts`、`composables/useShopCopy.ts`

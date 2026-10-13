# 买家 3D 定制方案（自己贴 logo、换开机画面）—— 设计

日期：2026-09-29
范围：`station-console`、`station-console-ui`、`station-site`，复用 `station-shared/model3d/marks.js`；独立站库 `sdsstation`。**ERP 不动。**
前置：商品 3D 模型（`2026-09-20-product-3d-model-design.md`）、商品 3D 标注（`2026-09-25-product-3d-marks-design.md`），都已上线康辰。

## 1. 背景与目标

商家已经能在管理台给商品模型标 logo、屏幕、开机键（存在 `u_s_sitegoods.model3d_marks`，一个商品一份，所有人看到同一份）。
现在想让**买家自己**在模型上贴自己的 logo、换开机画面，做成一份「定制方案」，得到一个链接，商家在后台能看到。

已确认（用户 2026-09-29 定）：

- 页面放在前台 `station-site` 里，是一张**干净的单独页面**（不带前台导航/页脚），不另起站点、不用新域名。
- 买家**只能动 logo 和开机画面**；屏幕、开机键的位置由商家标好，买家这边锁死。
- **游客能用**，不要求登录。
- 提交后**只保存成一个方案链接**，不接询盘/订单。
- 入口：商品详情页 3D 展示台加「定制我的 logo」按钮；同一地址也可以由业务员直接发给客户。
- 管理台加只读的「定制方案」列表。
- 方案**保存后不可修改**，每次保存生成新链接；旧方案可以「在此基础上修改」。

### 为什么不做成独立站点

新域名要单独配 COS 桶 CORS（2026-09-21、09-26 都踩过，curl 验不出）、要登记域名才能认出是哪个站、
买家会话 Cookie 是 host-only 的（故意，见 `station-cross-host-session`）得再做一次交接、九种语言/同意横幅/证书/发版脚本全部重来。
放在前台里一样可以是一张「看起来独立」的页面，这些问题一个都没有。

## 2. 不做什么

- 不接询盘、订单；不给买家或商家发邮件。
- 买家不能动屏幕、开机键的位置，不能换工作界面图，不能改开机 logo 停留时长。
- 不做真实尺寸换算（模型单位是任意的，编辑器里的大小只是示意）。
- 方案不能删除、不做过期清理（后续按需要加）。
- 不收 SVG、webp。
- 只发康辰；法国站 esvafit 连商品标注都还没发。
- 不改已上线的管理台标注编辑器 `ModelMarksEditor.vue`。

## 3. 实现方案

选定：**前台新页面 + 保存时一次性上传**。

- 买家选 logo 后只在浏览器本地（blob URL）预览，**不上传**；点「保存方案」时标注 JSON 和图片在一个 multipart 请求里提交，
  后端验图、存 COS、写方案、返回方案号。
- 公网上**没有可以单独上传文件的接口**；上传和建方案是同一次限流；不会产生「传了图没保存」的孤儿文件。

放弃的方案：

- 先传图后保存：等于开一个匿名文件上传口，要单独防刷，还要清孤儿文件。
- 把管理台编辑器抽成共用：动已上线的编辑器，风险落在不相关的功能上；放置逻辑本身不多，
  `marks.js` 已导出 `hitToLocal`、`orientation`、`localRadius`、`corsUrl`、`parseMarks`、`createMarks`，买家页直接用。

## 4. 数据

### 4.1 新表 `u_s_custom_plan`

迁移 `sql/station/migration/2026-09-29-custom-plan.sql`（只跑 `sdsstation`，可重复执行），基线 `sql/station/01_station_schema.sql` 同步。

| 列 | 类型 | 说明 |
|---|---|---|
| `planid` | `varchar(16)` PK | 16 位 base62 随机串（`SecureRandom`），**不走发号器**，防挨个猜 |
| `shopcode` / `sitecode` | `varchar` | 同其他 `u_s_*` 表 |
| `goodscode` | `varchar` | 商品编码，普通索引（`sitecode, goodscode`） |
| `model3d` | `varchar(500)` | 保存时商品模型地址的快照 |
| `marks` | `text` | **完整的 v1 标注 JSON 快照**（见 4.2） |
| `email` | `varchar(200)` NULL | 选填 |
| `note` | `varchar(500)` NULL | 选填 |
| `parentid` | `varchar(16)` NULL | 从哪个方案「在此基础上修改」来的 |
| `iphash` | `char(64)` | 提交 IP 的 SHA-256（加站点 salt），不存明文 |
| `createtime` | `datetime` | 索引（`sitecode, createtime`），列表按它倒序 |

迁移同时给 `operator` 角色加权限 `site:customplan:list`（照 `2026-09-04-chat.sql` 的 `CONCAT … NOT LIKE` 写法），末尾附验证查询。
`2026-09-06-file-url-rebase.sql` 的清单补上 `u_s_custom_plan.model3d`、`u_s_custom_plan.marks`（JSON 里含图片 URL，按字符串 REPLACE）。

### 4.2 方案里的标注 = 合并后的快照

`marks` 沿用商品标注的 v1 结构（`2026-09-25-product-3d-marks-design.md` §3.2），来源拆成两半：

| 字段 | 来自 |
|---|---|
| `logos[]`（图、`p`、`n`、`size`、`rot`、`opacity`） | 买家 |
| `screen.bootSrc`、`screen.bootBg` | 买家（不传则用商家的） |
| `screen.p/n/w/h/rot`、`screen.runSrc`、`screen.bootMs` | **商品当前标注**，客户端传什么都不认 |
| `power` | **商品当前标注** |
| `model` | 商品当前 `model3d` |

存的是快照：之后商家挪了屏幕、换了模型，已有方案都不变。前台方案页和管理台展示方案都**只读这一份**，不回读商品表。

商品没有 `screen` 时，方案里也没有，买家页不出现「开机画面」那一块。`power` 可有可无（2026-10-08 放宽）：没标开机键时买家靠「模拟开机」按钮看效果，方案里也就没有 `power`。

### 4.3 图片存放

- 对象路径 `custom/yyyy/MM/dd/{planId}-{n}.{png|jpg}`，走当前启用的存储（`SiteStorageService.requireActive()`），
  复用 `SiteFileService` 里的 `store` 逻辑（抽一个包内可见的方法，不经 `MultipartFile` 的另开一个重载）。
- **不写进 `u_s_siteimage`**，不出现在商家素材库。
- 同一个桶、同一个 `file.` 域名，康辰桶的 CORS 已覆盖主域和 console.，不用加规则。

## 5. 接口

都挂在 `OpenApiController` 同一个前缀 `/openapi/site/` 下，匿名可调，站点照现有方式按域名识别。

### 5.1 `GET /openapi/site/customize/{goodsCode}`

返回定制页需要的：`goodsCode`、商品名（当前语言）、主图、`model3d`、商家标注里的 `screen`、`power`（解析后的对象）。
商品不存在、不属于本站、已下架或没有模型 → 404。

### 5.2 `POST /openapi/site/customize/plan`（multipart）

字段：`data`（JSON 串）+ `files`（0–6 个文件）。`data`：

```json
{
  "goodsCode": "1201302",
  "from": "Ab3dEf...",          // 选填，parentid
  "logos": [ { "id": "a1", "src": "upload:0", "p": [..], "n": [..], "size": 0.03, "rot": 0, "opacity": 1 } ],
  "boot": { "src": "upload:1", "bg": "#000000" },   // 选填
  "email": "", "note": "",
  "agree": true,
  "website": ""                  // 蜜罐
}
```

校验顺序（每一步不过都直接返回，**校验全部通过之前不写 COS**）：

1. 蜜罐 `website` 有值 → 静默返回成功（和询盘、下单同一套），不存任何东西。
2. 限流 `CustomPlanRateLimiter`（见 §6）。
3. `agree` 必须为 true。
4. 商品存在、属于本站、有模型。
5. 文件 ≤ 6 个；每个 ≤ 2MB；**文件头魔数**是 PNG（`89 50 4E 47`）或 JPEG（`FF D8 FF`），扩展名不作数；
   `ImageIO.read` 能解开，宽高都 ≤ 4096。
6. 图片引用：`logos[].src`、`boot.src` 只能是 `upload:N`，N 必须在本次文件范围内；**每个上传的文件都必须被引用**（多传一个就拒绝）。
   任何 `http(s)://`、`/` 开头的地址一律拒绝——买家方案里不可能出现外链。
7. logo ≤ 5 个；`boot.bg` 匹配 `#RRGGBB`；`email` 为空或格式合法（≤ 200）；`note` ≤ 500 字，去掉控制字符；`from` 为空或 16 位 base62。
8. 按 §4.2 拼出完整标注（`upload:N` 先换成占位 URL），整段过 `Model3dMarks.normalize`——范围、数值、白名单重组都复用那一套。
9. 生成 `planId`，按顺序存 COS，把占位 URL 换成真实地址，**再过一遍** `normalize`，在事务里插入 `u_s_custom_plan`。
   插库失败 → 本次已上传的对象**删不掉**（`CosClient` 故意没有删除能力），把对象地址记进 ERROR 日志、返回 500，由运维和其他孤儿对象一起清。
10. 返回 `{ "planId": "..." }`。

错误走现有 `ApiExceptionHandler`：参数/文件不合规 400（带原因），限流 429。

### 5.3 `GET /openapi/site/customize/plan/{planId}`

- `planId` 不是 16 位 base62 → 直接 404，不查库。只查本站。
- 返回：`planId`、`goodsCode`、商品名（当前语言）、`model3d`、`marks`（对象）、`createTime`、`parentId`。
- **不返回 `email`、`note`**：链接谁拿到都能看，这两项只给商家。

## 6. 限流 `CustomPlanRateLimiter`

Redis 键前缀 `station:customplan:rate`，固定窗口，写法照 `InquiryRateLimiter`：

- 每 IP 每小时 10 次、每天 30 次；
- **每站点每天 500 次**封顶（控制存储费用）。

**⚠ Redis 挂了要拒绝**（catch 里返回 false），和 `InquiryRateLimiter`「拿不到计数就放行」相反，和 `OrderLookupRateLimiter` 一样。
理由：这是唯一一个能让匿名请求往 COS 写文件的口子，放行等于不限量写。代价是 Redis 故障期间存不了方案，
页面提示「稍后再试」，编辑内容还在页面上不丢。以后有人「顺手对齐成和询盘一样」要拦下来——类注释里写明。

## 7. 前台（`station-site`）

### 7.1 入口

`Product3DViewer.client.vue` 按钮行加「定制我的 logo」（只在加载的是真实 glb 时出现），`target="_blank"` 打开
`/customize/{code}`（用 `localePath`，同在主域）。

### 7.2 定制页 `app/pages/customize/[code].vue`

- **干净的单独页面**：不用 `SiteHeader`/页脚；顶部一窄条：站点 logo（回首页）+ 语言切换 +「返回商品」。
- `noindex`；**不写进 robots.txt 的 Disallow**（同 `/find-order` 的理由：详情页链着它，Disallow 了爬虫反而读不到 noindex）。
- 布局：宽屏左画布右面板；窄屏画布在上、面板在下（可收起）。
- 画布：场景搭法照 `Product3DViewer`（RoomEnvironment、OrbitControls，编辑时不自转）；标注用 `createMarks`，
  `showPowerRing: false`（前台不画光圈，用户 2026-09-26 定），点击区保留、点开机键能开关机。
  坐标规则照旧：存 `gltf.scene` 局部坐标，取景只挪外层，绝不挪 `gltf.scene`。
- 画法组件新建 `app/components/CustomizeCanvas.client.vue`（画布 + 放置模式 + 点选），
  面板和页面逻辑都在页面里（方案页只读，用不上编辑状态，没必要抽 composable）；接口、本机记录、验图放在 `app/lib/customize.ts`。

**面板**

1. **Logo**：「添加 logo」→ `<input type="file" accept="image/png,image/jpeg">` → 前端先拦类型与 2MB → blob 预览 →
   放置模式：指针下实时半透明贴花，点一下落定（射线命中点 + 面法线，经 `hitToLocal` 换成局部坐标）。
   每条：缩略图、大小/旋转/透明度滑块、「重新放置」「删除」。满 5 个置灰。滑块范围照管理台（按包围球半径 R：0.02R–1R）。
2. **开机画面**（商品标了 screen 就出现，开机键不是必需，2026-10-08 放宽）：开机 logo 图、底色（原生 `<input type="color">`）、「模拟开机」
   （黑屏 → 买家 logo → 商家工作界面）。
3. **保存**：邮箱（选填，注「只用于商家联系你」）、备注（选填，≤ 500）、必勾「我有权使用上传的 logo」、蜜罐隐藏字段、「保存方案」。

**交互细节**

- pointerdown→pointerup 位移 < 5px 才算点选，否则是转视角；触屏单指转、双指缩放（OrbitControls 自带）。
- 屏幕、开机键不可选中、不可拖动。
- 有未保存改动时 `beforeunload` 提示。
- 保存成功：留在本页，弹框显示方案链接 +「复制」「打开」；方案号记入 localStorage（键 `station-custom-plans`，最多 10 条，
  读写都包 try/catch），页面顶部可展开「这台设备上的方案」。
- `?from={planId}`：先读方案，把 logos、boot 带进编辑器（图片地址是线上 URL，保存时前端要把它们重新取回为 Blob 一并上传，
  保证「所有图片都是本次上传」这条规则不破例；取回用 `fetch(corsUrl(url))`，避开 COS 不回 `Vary: Origin` 的缓存坑）。方案的 `model` ≠ 商品当前 `model3d` 时顶部提示「商品模型已更新，logo 位置可能需要重新调整」。

### 7.3 方案查看页 `app/pages/customize/plan/[id].vue`

- 只读：模型 + 方案标注，可转、可「模拟开机」；显示方案号、商品名、创建日期。
- 按钮：「复制链接」「在此基础上修改」（→ `/customize/{goodsCode}?from={id}`）。
- `noindex`，同样不进 Disallow。
- 加载的是方案快照里的 `model3d`，不是商品当前模型。

### 7.4 文案

`useShopCopy.ts` 九种语言新增 `cz*` 一组键（入口按钮、面板标题、滑块、提示、错误、弹框），**不留空串**。

## 8. 管理台（`station-console` + `station-console-ui`）

- 接口：`GET /console/customplan`（筛选 `goodsCode`、日期区间，分页）、`GET /console/customplan/{id}`，`@RequirePerm("site:customplan:list")`。
  这两处返回 `email`、`note`。
- 页面「定制方案」，菜单挂在「商品」附近：列表列 方案号、商品、邮箱、备注摘要、提交时间。
- 点一行 → `el-dialog`（`destroy-on-close`，同 `ModelPreview` 的显存规矩）：模型 + `createMarks` 展示方案、「模拟开机」、
  右侧列 logo 原图（逐张下载）、「在前台打开」链接。
- **只读**，不删、不改。
- 文案进 `station-console-ui/src/i18n/locales/{zh,en,fr}.js`，键 `customPlan.*`。

## 9. 错误处理

| 情况 | 表现 |
|---|---|
| 图片类型/大小不对 | 前端先拦；后端 400 带原因，页面显示 |
| 限流 / Redis 挂 | 429「稍后再试」，编辑内容保留 |
| 商品没模型、下架、不存在 | 定制页「这个商品暂不支持定制」+ 返回链接 |
| 方案 id 格式不对 / 不存在 | 方案页「找不到这个方案」 |
| logo / 贴图加载失败（含 CORS） | 跳过该项（`marks.js` 现有行为） |
| 不支持 WebGL / 模型加载失败 | 同展示台的兜底文案 |
| 存 COS 后插库失败 | 对象地址记 ERROR 日志（删不掉），返回 500 |

## 10. 测试与验收

后端单测（纯 Mockito，`station-console/src/test/.../service/`）：

- `CustomPlanServiceTest`：合法提交入库、标注正确合并；多传 / 少传 / 越界 `upload:N`；外链地址；
  改扩展名的 SVG、GIF；超 2MB、超 4096；超 5 个 logo；客户端塞的 screen 坐标 / runSrc / power 被商家值覆盖；
  邮箱格式；`from` 格式；蜜罐不落库不写 COS；插库失败时删对象；读方案不带 email/note；非法 id 不查库。
- `CustomPlanRateLimiterTest`：三层计数；**Redis 异常时拒绝**。

前台 / 管理台：

- 两个前端 build 过，three 各只一份（`marks.js` 共享规则）。
- 本机起前后端（按 `erp-e2e-start-backend`），无头 Edge 跑：上传 → 放置 → 模拟开机 → 保存 → 打开方案链接 → 在此基础上修改 → 再保存；
  窄屏用固定宽 iframe 截图。
- 管理台列表、弹窗各截一张。
- 上线后用真浏览器（CDP）截线上定制页，验贴图没被 COS 缓存串用拦掉（`corsUrl`）。

## 11. 实现时补的几条（2026-09-30）

- **画布组件必须用 boot() + watch(canvasEl)**：整页加载时 Nuxt 先渲染 .client 组件的占位、再在同一实例上换真模板，`onMounted` 时 canvas 还不存在。只在 onMounted 里初始化会永远停在「加载中」（本机 e2e 踩到，同 Product3DViewer 的写法）。
- **编辑/查看时屏幕常亮哪一张**：有开机图（买家换的或商家原有的）亮开机图，没有就亮工作界面——只配了工作界面的商品不能是黑屏。
- `marks.js` 的 `corsUrl()` 对 `blob:` / `data:` 原样返回：blob 地址拼了查询串就取不到，本地预览的 logo 会显示不出来。
- 生产 nginx 主域 `location /openapi/site/` 要 `client_max_body_size 16m`（一次最多 6×2MB），否则保存回 413；前台对 413 单独提示「图片太大」。
- 商品页入口只认接口的 `model3d` 字段（不认规格键兜底），和后端判定一致。

## 12. 发版

照 `station-deploy-procedure`，顺序不能反：**迁移 → 后端 jar → 前台 → 管理台**（mapper 引用新表，jar 先上会 500）。
只发康辰；基线以 `station-theme-per-site` 记的康辰当前树为准，发前拉线上树对账。

# 独立站商品 3D 模型 —— 设计

日期：2026-09-20
范围：独立站三个模块（`station-console`、`station-console-ui` + `station-shared`、`station-site`）与独立站库 `sdsstation`。**ERP（RuoYi）不动。**

## 1. 背景与目标

前台商品页的「3D 展示台」已经在线上（2026-09-04 提交 `3b190ef8`，`station-site/app/components/Product3DViewer.client.vue`，three.js，支持 .glb/.gltf、自动旋转、多视角切换、进视口才加载 chunk）。但整条链只做了最后一环：

- `u_s_sitegoods` 没有模型字段，站点接口不下发；
- 管理台「文件」页只有图片/视频两个标签，上传白名单不认 .glb；
- 「商品」编辑表单没有模型选择器；
- 商家现在只能把模型地址塞进「规格参数」的 `model3d` / `3d` 键临时用。

目标：把「上传模型 → 挂到商品 → 前台显示」这条链补完整，让商家在管理台上像传视频一样传模型、像选视频一样挂到商品。

已确认的前提（用户 2026-09-20 定）：

1. 模型文件由商家自备（设计师/供应商给 .glb），**不做**从照片生成 3D。
2. 前台沿用现有展示台，不重做顶部相册。
3. 后台按「第三种媒体类型」存，与图片、视频同构。

## 2. 不做什么

- 不做 AI 建模、不接第三方生成服务。
- 不收 `.gltf`：它是多文件格式（外链 `.bin` 和贴图），单文件上传必裂。只收 `.glb`。
- 不做 .glb 的服务端解析、压缩、缩略图生成。
- 一个商品只挂一个模型。
- 不改 ERP 的商品档案；ERP 与独立站不互通（boundary 要点 1）。
- 不动 `/3d-demo` 页。

## 3. 数据层

一份迁移脚本 `sql/station/migration/2026-09-20-goods-model3d.sql`，**只跑在独立站那台**（库 `sdsstation`）。基线 `sql/station/01_station_schema.sql` 同步加上同样的 DDL 和序列种子行，保持「基线 = 线上」。

### 3.1 新表 `u_s_sitemodel`

照 `u_s_sitevideo` 抄，去掉对模型无意义的 `width` / `height` / `duration`：

| 列 | 类型 | 说明 |
|---|---|---|
| `modelid` | varchar(50) PK | 主键，前缀 `SMDL`，走 `u_s_sequence` |
| `shop_code` | varchar(50) NOT NULL DEFAULT '' | 遗留列，与其他媒体表一致 |
| `sitecode` | varchar(50) | 来源追溯用，不参与过滤 |
| `modelname` | varchar(200) NOT NULL | 显示名，默认原始文件名，可改 |
| `modelurl` | varchar(500) NOT NULL | 完整访问地址（上传那一刻整串入库） |
| `objectname` | varchar(300) | 桶内对象路径 |
| `provider` | varchar(20) | 当初存到哪家，换厂商后靠它追溯 |
| `filesize` | bigint | 字节数 |
| `mimetype` | varchar(100) | 浏览器给的 Content-Type，.glb 通常是 `model/gltf-binary` 或空 |
| `remark` | varchar(500) | |
| `folderid` | varchar(50) | 文件夹，NULL = 未分类 |
| `createtime` / `updatetime` / `create_by` / `update_by` | 同视频表 | |

索引：`KEY idx_sitemodel_shop (shop_code, createtime)`，与视频表一致。

### 3.2 `u_s_sitegoods` 加列

`model3d varchar(500) DEFAULT NULL COMMENT '3D 模型（.glb）完整地址，来自文件资源库；空 = 没有模型'`。

放在 `sitevideos` 之后。存 URL 而不是 `modelid`，与 `siteimgs` / `sitevideos` 的做法一致：前台只认 URL，且换存储时迁移脚本按字符串替换处理（这一列要加进 `2026-09-06-file-url-rebase.sql` 的「爆炸半径」清单，本次在脚本注释里补一句）。

### 3.3 序列

`INSERT IGNORE INTO u_s_sequence (name, nextval) VALUES ('model', 1);`

### 3.4 文件夹

`u_s_sitefolder.mediatype` 是自由字符串，多认一个值 `model`，表结构不动。

## 4. 管理台后端（`station-console`）

全部照视频那一套镜像，命名把 Video 换成 Model。

### 4.1 新增

- `domain/SiteModel.java`：字段对应 3.1。
- `mapper/SiteModelMapper.java` + `resources/mapper/SiteModelMapper.xml`：`selectList` / `countList` / `countByProvider` / `selectByCode` / `insertModel` / `updateModel` / `deleteByCode` / `clearFolder` / `updateFolder`，`listWhere` 里的 provider 过滤含 `or (provider is null and #{provider}='local')` 那一支，理由见 `SiteImageMapper.xml` 顶部。
- `CodeGenerator`：`SEQ_MODEL = "model"`、`PREFIX_MODEL = "SMDL"`、`nextModelId()`。
- `StationProperties.Upload`：`modelExt`，默认 `glb`，配置键 `station.upload.model-ext`；`application.yml` 里同步加 `model-ext: glb` 并注释「只收 glb，不收 gltf 的原因」。

### 4.2 `SiteFileService`

- 常量 `MODEL_FOLDER = "model"`，对象路径 `model/YYYY/MM/DD/<modelid>.glb`。
- `selectModelList` / `countModelsOf` / `uploadModel` / `renameModel` / `removeModel`，与视频版逐一对应。`uploadModel` 不收宽高时长。
- 存储切换弹框的隐藏计数：`ConsoleApiController` 保存存储配置那个端点里，在 `storageService.save()` **之前**多数一个 `hiddenModels = fileService.countModelsOf(previous)`，与 `hiddenImages` / `hiddenVideos` 并列返回；`Files.vue` 的弹框文案加上这一项。
- 单文件大小：沿用 `spring.servlet.multipart.max-file-size: 200MB`，**不另加硬限制**。界面提示「建议 10MB 以内」。理由：硬限太紧会挡住真实的工业模型，太松等于没有；前台加载慢是商家能当场看到的后果，交给人判断。

### 4.3 `SiteFolderService`

image / video 二选一的几处（`clearFolder` 分支、`updateFolder` 分支、`normalizeType`）改成三选一；`normalizeType` 认不出来的仍落图片。`SiteFolderMapper.xml` 里 `<when test="mediaType == 'video'">` 那处计数查询加 `model` 分支。

### 4.4 `ConsoleApiController`

```
GET    /api/file/model            site:file:list    列表（query: modelName, folderId, pageNum, pageSize）
POST   /api/file/model            site:file:upload  上传（multipart: file, folderId）
PUT    /api/file/model            site:file:edit    改名/备注（body: modelId, modelName, remark）
DELETE /api/file/model/{modelId}  site:file:remove  删除
```

权限复用 `site:file:*`，不新增权限码。

### 4.5 商品链路

- `SiteGoods` 加 `model3d`（getter/setter），`SiteGoodsMapper.xml` 的 resultMap、`columns`、insert、update 四处加上。
- `SiteGoodsService` 的 `createGoods` 和 `saveGoods` 里，在现有那串 `trimToNull(...)` 清洗旁边加 `model3d`：先 trim，再只认以 `https://` 或 `/` 开头的值，其余置 NULL（与前台 `modelUrl` 的判定一致，避免 http 混合内容）。抽成一个私有方法 `normalizeModelUrl`，两处共用。
- `SiteOpenService.goodsDetail` 加 `result.put("model3d", item.getModel3d())`。**只在详情下发**，`toCard` 不带：列表卡片用不到，也别让每张卡片多一个字段。

## 5. 管理台前端（`station-console-ui` + `station-shared`）

### 5.1 `station-shared/ModelPreview.vue`（新）

- props：`src`（必填）、`height`（默认 320）。
- 用 `three` 的 `GLTFLoader` + `OrbitControls` + `RoomEnvironment`，取景逻辑从 `station-site/app/lib/product3d/framing.ts` 的 `orbitFor` 抄一份最小版（包围盒居中、按尺寸拉相机距离）。**不 import 站点那份**：两个工程互不依赖。
- 加载失败显示一行「模型加载失败」。
- 组件内自己 `dispose` 场景、几何、材质、renderer，`onBeforeUnmount` 释放。
- 用 `defineAsyncComponent` 在宿主页按需引入，three.js 不进管理台主 chunk。
- `station-console-ui/package.json` 加 `three` 依赖（版本与 `station-site` 对齐，`^0.185.1`）。

### 5.2 「文件」页 `Files.vue`

- 单选按钮组加第三项「模型」（value `model`）。
- `isImage` 二元判定改为 `activeType` 三态；`keyOf` / `nameOf` / `urlOf` / `acceptTypes`（`.glb`）/ 列表查询字段 / 上传函数 / 改名请求体 / 删除函数逐一加 `model` 分支。
- 上传前不 `measure()`：模型没有宽高时长可量。
- 模型卡片：媒体区显示一个立方体图标（Element Plus `Box` 图标）+ 「预览」按钮，点击弹出 `el-dialog` 内嵌 `ModelPreview`。卡片 meta 只显示大小。
- 拖拽归类、批量移动、文件夹树按 `activeType` 传 `model`，逻辑不变。

### 5.3 `MediaPicker.vue`

- `type` 多认 `model`；`isImage` 改成 `mediaType` 三态；`listSiteFolder` / 列表接口 / `urlOf` / `nameOf` / `typeLabel`（「模型」）分支。
- 模型卡片同 5.2（图标 + 名称 + 「预览」）。

### 5.4 「商品」页 `Goods.vue`

- 视频区下方加「3D 模型」区：没选时一个「选择模型」按钮；选中后显示文件名（从 URL 取最后一段）、「预览」、「移除」。
- `<media-picker type="model" :limit="1">`，confirm 回来取第一条。
- 表单装载：`form.model3d` → `siteModel`；提交：`model3d: siteModel || null`。

### 5.5 `api/console.js`

`listSiteModel` / `uploadSiteModel` / `updateSiteModel` / `delSiteModel`，路径 `/file/model`。

## 6. 前台（`station-site`）

- `useSite.ts` 的商品类型 `model3d?: string` 已声明，注释改成「后端已下发」。
- `pages/products/[code].vue` 的 `modelUrl`：优先 `p.model3d`，其次规格里的 `model3d` / `3d`（**保留兼容**，已经这样配的商品不受影响），仍只认 `/` 和 `https://` 开头。文件头那段「后端还没有 model3d 字段」的注释改掉。
- 顶部图片区加一个「查看 3D」按钮：只在 `modelUrl` 非空时出现，点击 `stageEl.scrollIntoView({ behavior: 'smooth' })`；同时把 `stageSeen` 置 true，让 chunk 立即开始下，不用等滚到位。文案放在 `composables/useShopCopy.ts` 现有的 `d3*` 那组键旁边，键名 `d3JumpLabel`，每种站点语言各给一句（该文件里日语/韩语 `d3Title` 是空串是故意的，别照着把新键也留空）。
- 展示台组件 `Product3DViewer.client.vue` 与 `lib/product3d/*` 不动。

## 7. 错误处理

| 场景 | 行为 |
|---|---|
| 上传非 .glb | 后端 `requireExtension` 拒绝，提示与图片视频同款；前端 accept 只列 `.glb` 避免白选一场 |
| 上传的 .glb 本身坏了 | 后端不解析；管理台「预览」显示加载失败，商家当场能看到 |
| 存储 provider 切换 | 列表按当前生效 provider 过滤，与图片视频一致；隐藏数计入弹框 |
| 商品挂着的模型在文件库被删 | **不**反查商品清引用，与图片视频现状一致；前台展示台显示海报图 + 「暂不可用」 |
| 商品 `model3d` 是 http 地址 | 保存时置 NULL；前台也不认 |
| 浏览器不支持 WebGL | 展示台已有兜底，显示海报图 |

## 8. 测试与验收

后端单测。⚠ `station-console` **目前没有 `src/test` 目录**，pom 里已有 `spring-boot-starter-test`。本次新建 `station-console/src/test/java/com/ruoyi/station/console/service/`，放三个纯 Mockito 单测（不起 Spring 上下文，mapper 和存储全 mock）：

- `SiteFileServiceModelTest`：`.glb` 通过、`.gltf` / `.obj` 被拒、对象路径以 `model/` 开头、`provider` 取自当前存储。
- `SiteOpenServiceModelTest`：`goodsDetail` 下发 `model3d`；`toCard` 不含。
- `SiteGoodsServiceModelTest`：`model3d` 为 http 地址时落 NULL，`https://` 和 `/` 开头的原样保留。

前端：`station-console-ui` 与 `station-site` 各 `npm run build` 通过。

手工验收（本机起 console + site，见 `erp-e2e-start-backend` 那条记忆里的起法）：

1. 「文件」页切「模型」标签，传一个 .glb，卡片出现，预览能转。
2. 传一个 .gltf，被拒。
3. 「商品」页选中该模型保存，重开表单还在。
4. 前台该商品详情：顶部出现「查看 3D」，点击滚到展示台，模型能转；接口响应里有 `model3d`。
5. 一个只在规格里填了 `model3d` 的老商品，前台照常显示。
6. 「存储设置」切 provider 的弹框里出现模型隐藏数。

发版顺序（四步不能反）：迁移 SQL → console jar → console-ui 静态包 → site。前两步顺序反了会让 jar 起来查不到列；后两步反了只是按钮暂时没数据，无害。流程按 `station-deploy-procedure` 那条记忆。

## 9. 影响面清单

- SQL：`sql/station/migration/2026-09-20-goods-model3d.sql`（新）、`sql/station/01_station_schema.sql`、`sql/station/migration/2026-09-06-file-url-rebase.sql`（注释补一列）
- station-console：`SiteModel`、`SiteModelMapper` + XML、`src/test` 三个单测（新）；`SiteFileService`、`SiteFolderService`、`SiteFolderMapper.xml`、`CodeGenerator`、`StationProperties`、`application.yml`、`ConsoleApiController`（四个端点 + 隐藏计数）、`SiteGoods`、`SiteGoodsMapper.xml`、`SiteGoodsService`、`SiteOpenService`
- station-shared：`ModelPreview.vue`（新）、`MediaPicker.vue`
- station-console-ui：`package.json`、`api/console.js`、`views/Files.vue`、`views/Goods.vue`
- station-site：`composables/useSite.ts`、`pages/products/[code].vue`、多语文案表

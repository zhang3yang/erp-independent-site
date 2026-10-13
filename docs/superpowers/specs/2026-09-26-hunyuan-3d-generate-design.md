# 独立站管理台接入腾讯混元生 3D（照片 → .glb 模型）—— 设计

日期：2026-09-26
范围：`station-console`、`station-console-ui`、独立站库 `sdsstation`。**前台 `station-site` 不动，ERP 不动。**
目标站点：康辰站（用户 2026-09-26 定：只管康辰，法国站先不管）。
前置：商品 3D 模型链路（2026-09-21 上线）、3D 标注（2026-09-26 上线）。

## 1. 背景与目标

现在商品的 `.glb` 模型要商家自己去别处做（混元 3D 网站、外包、我手写的照片挤出程序），
再到「文件 → 模型」上传。运营不会建模，来回倒腾文件也容易出错。

目标：**在管理台里从文件库选几张商品照片，点「生成」，几分钟后模型自动进模型库**，
之后挂商品、做标注和现在完全一样。

已确认（用户 2026-09-26）：

- 用腾讯云「混元生 3D」API，与康辰站 COS 是**同一个腾讯云账号**。

## 2. 接口事实（2026-09-26 查官方文档）

| 项 | 值 |
|---|---|
| 域名 / 服务 / 版本 | `ai3d.tencentcloudapi.com` / `ai3d` / `2025-05-13` |
| 地域 | `ap-guangzhou`（配置项可改；首次接入用 API Explorer 验一次） |
| 提交 | `SubmitHunyuanTo3DProJob` → 返回 `JobId`（24 小时有效） |
| 查询 | `QueryHunyuanTo3DProJob(JobId)` → `Status`：`WAIT` / `RUN` / `DONE` / `FAIL`；`DONE` 时 `ResultFile3Ds[]`（`Type` / `Url` / `PreviewImageUrl`），`FAIL` 时 `ErrorCode` / `ErrorMessage` |
| 图片输入 | `ImageUrl`（≤8MB，jpg/png/jpeg/webp，边长 128–5000px）+ 可选 `MultiViewImages`（左/右/背等视角） |
| 面数 | `FaceCount` 3,000–1,500,000，默认 500,000 |
| 其他 | `EnablePBR`（PBR 材质，默认 false）、`Model`（3.0 / 3.1）、默认输出 obj/glb |
| 并发 | 同时 3 个任务，多的排队 |
| 签名 | TC3-HMAC-SHA256，与现有 `TmtClient` 同一套 |
| 权限 | 子账号要挂 `QcloudAI3DFullAccess` |
| 计费 | 按积分；开通后可领免费积分；**用完不会自动转按量**，调用直接报计费错误 |

来源：腾讯云文档 1804/123447（提交专业版）、1804/120757（快速入门）、1804/123461（计费）、
国际站 1284/75541（查询接口字段）。

## 3. 设计决策（带推荐，写代码前请确认）

| # | 决策 | 推荐 | 理由 |
|---|---|---|---|
| D1 | 用哪把密钥 | **复用「文件存储」里的腾讯云 SecretId/SecretKey**（和机器翻译 `SiteTranslateService` 一样的取法） | 同一账号，零配置；管理台已有密钥加密存储和「密钥读不出来」的提示。代价：那把密钥若是子账号，要给它加挂 `QcloudAI3DFullAccess`；报 `AuthFailure.UnauthorizedOperation` 时提示里写明这一步 |
| D2 | 默认面数 | **15 万**（界面给「标准 15 万 / 精细 40 万」两档） | 默认 50 万的 glb 常见二三十 MB，前台展示台要下完才显示；15 万对商品展示足够 |
| D3 | PBR 材质 | **默认关**，界面可勾 | 积分消耗大概率更高（以控制台为准），且我们的标注贴花不依赖它 |
| D4 | 模型版本 | 默认 `3.0`，配置项 `station.ai3d.model` 可改 | 文档默认值 |
| D5 | 进度怎么拿 | **后端定时轮询**（每 15 秒），前端只读任务表 | 关掉浏览器也会完成；不依赖某个人一直开着页面 |
| D6 | 结果存哪 | **下载 glb 转存到本站 COS**，入 `u_s_sitemodel`，和手动上传的模型完全一样 | 混元给的下载地址是临时的；转存后前台、CORS、换域名脚本都不用为它特殊处理 |
| D7 | 防误点烧积分 | 每站**每天上限 20 次**（`station.ai3d.daily-limit`），提交前弹确认「会消耗混元积分」 | 按钮就在文件库里，谁都能点；上限可调 |

## 4. 不做什么

- 不做文生 3D（只从照片生成）。
- 不做智能拓扑、贴图重生成、格式转换等其他混元接口。
- 不在前台（买家侧）暴露任何生成能力。
- 不做生成后自动挂商品、自动标注——生成完进模型库，挂商品和标注仍是人来做。
- 不接极速版（`SubmitHunyuanTo3DRapidJob`）；专业版效果更好，速度差异对「后台生成」不敏感。

## 5. 数据

迁移 `sql/station/migration/2026-09-26-model-gen-job.sql`（只跑 `sdsstation`，可重复执行），基线同步。

### 5.1 新表 `u_s_model_job`

| 列 | 类型 | 说明 |
|---|---|---|
| `jobid` | varchar(50) PK | 本站任务号，前缀 `SMJB`，走 `u_s_sequence`（新行 `modeljob`） |
| `sitecode` | varchar(50) | 追溯用 |
| `tcjobid` | varchar(100) | 混元返回的 JobId；提交失败时为空 |
| `status` | varchar(20) NOT NULL | `SUBMITTING` / `WAIT` / `RUN` / `SAVING` / `DONE` / `FAIL` |
| `jobname` | varchar(200) | 生成后模型的显示名（默认取正面图文件名） |
| `imagesjson` | varchar(4000) | `{"front":url,"left":url,"right":url,"back":url}`，只存用到的 |
| `facecount` | int | |
| `enablepbr` | tinyint | |
| `folderid` | varchar(50) | 生成后放进哪个模型文件夹 |
| `modelid` | varchar(50) | 成功后对应的 `u_s_sitemodel.modelid` |
| `previewurl` | varchar(1000) | 混元给的预览图地址（临时，只在任务列表里显示，过期就不显示） |
| `errorcode` / `errormsg` | varchar(100) / varchar(1000) | 失败原因（已翻译成给商家看的话 + 原始错误码） |
| `polls` | int NOT NULL DEFAULT 0 | 已查询次数 |
| `saveretries` | int NOT NULL DEFAULT 0 | 下载转存失败次数，满 5 次置 FAIL（重试时重新查一次拿新地址，所以不存结果地址） |
| `create_by` / `createtime` / `updatetime` / `finishtime` | | |

索引：`KEY idx_modeljob_status (status, createtime)`。

`INSERT IGNORE INTO u_s_sequence (name, nextval) VALUES ('modeljob', 1);`

⚠ 批量造数据要顶号：见记忆「发号器顶号」。

## 6. 后端（`station-console`）

### 6.1 腾讯云 API 客户端抽一层

`util/TmtClient.java` 的签名逻辑把 host / service / version 写死了。抽出
`util/TencentApiClient.call(host, service, version, action, region, payload, secretId, secretKey)`，
`TmtClient.call(...)` 改成调它（行为逐字节不变，`TmtClient` 现有调用方不动）。
新增 `util/Ai3dClient`：`submit(payload)`、`query(jobId)` 两个方法，只做「拼请求 → 调用 → 解析 Response / Error」。

### 6.2 `ModelGenService`

- `submit(ModelGenRequest req, operator)`：
  1. 校验：正面图必填；所有图片地址必须是**本站文件库里的图片**（`u_s_siteimage.imageurl` 里查得到）
     且 `https://` 开头——混元要能从公网拉到，也防止把任意外部地址塞进来；
     面数只接受两档；当天已提交数 < `daily-limit`。
  2. 取密钥（同 `SiteTranslateService.credentials()`）；取不到 → 「先去站点概览配好腾讯云文件存储」。
  3. 先落一行 `SUBMITTING`（拿到本站 jobid），再调 `SubmitHunyuanTo3DProJob`：
     `ImageUrl=front`、`MultiViewImages=[{ViewType:left/right/back, ViewImageUrl}]`、`FaceCount`、`EnablePBR`、`Model`。
  4. 成功 → `WAIT` + `tcjobid`；失败 → `FAIL` + 翻译后的错误。
- `poll()`：`@Scheduled(fixedDelay = 15s, initialDelay = 30s)`。
  ⚠ 依赖 `@EnableScheduling` 仍在（`PageViewMaintenance` 类注释里那条）。
  1. 取 `WAIT`/`RUN` 的任务（最多 10 条）逐个查询；`polls+1`。
  2. `DONE` → 置 `SAVING` → 在 `ResultFile3Ds` 里找 `Type` 为 GLB 的那个（大小写不敏感；
     没有 GLB → `FAIL`「混元没返回 glb」）→ 下载（超时 120s、上限 100MB）→
     校验前 4 字节是 `glTF` → 走 `SiteFileService` 新增的 `saveGeneratedModel(bytes, name, folderId, operator)`
     （复用 `store` / `publicUrl` / `modelMapper.insertModel`，和 `uploadModel` 共用同一段落库代码）→ `DONE` + `modelid`。
  3. `FAIL` → 记 `ErrorCode` / `ErrorMessage`。
  4. 超过 2 小时仍未完成 → `FAIL`「生成超时」（JobId 24 小时有效，2 小时足够宽）。
  5. 单个任务出异常只影响它自己（try/catch 包每一条），`SAVING` 状态卡住的任务下次轮询重试下载。
- `list(pageNum, pageSize)`、`remove(jobid)`（只删任务记录，不删已生成的模型）。

### 6.3 错误码翻译（给商家看的话）

| 腾讯云错误 | 提示 |
|---|---|
| `AuthFailure.*`（签名/密钥） | 腾讯云密钥不对，去「站点概览 → 文件存储」重新填一次 |
| `AuthFailure.UnauthorizedOperation` | 这把腾讯云密钥没有混元生 3D 权限：在访问管理里给它挂 `QcloudAI3DFullAccess` |
| `FailedOperation.*Billing*` / `ResourceInsufficient.*` / 欠费类 | 混元积分不足或没开按量付费：去腾讯云「混元生 3D」控制台充值积分或开启按量付费 |
| `ResourceUnavailable.NotExist` / 未开通 | 还没开通混元生 3D：去腾讯云控制台开通 |
| 图片类 `InvalidParameterValue.*` | 图片不符合要求（≤8MB、边长 128–5000 像素、jpg/png/webp） |
| 其他 | 生成失败：{原始 ErrorMessage}（{ErrorCode}） |

具体错误码字符串以首次接入时实际返回为准，单测里用样例固定。

### 6.4 接口（`ConsoleApiController`）

```
POST   /api/file/model/gen          site:file:upload  提交（body: front, left?, right?, back?, name?, faceCount, pbr, folderId?）
GET    /api/file/model/gen          site:file:list    任务列表（分页）
DELETE /api/file/model/gen/{jobId}  site:file:remove  删任务记录
```

权限复用 `site:file:*`，不新增权限码。

### 6.5 配置（`application.yml`，`ModelGenService` 里用 `@Value` 读，照 `SiteTranslateService` 读 `station.tmt.region` 的写法）

```yaml
station:
  ai3d:
    enabled: true          # 关掉则接口回「未启用」、不轮询
    region: ap-guangzhou
    model: "3.0"
    daily-limit: 20
```

## 7. 管理台前端（`station-console-ui`）

「文件」页切到「模型」标签时：

- 工具栏多一个 **「用照片生成」** 按钮 → 弹窗：
  - 正面图（必选）、左 / 右 / 背面图（可选）：各一个 `MediaPicker type="image" :limit="1"`，选完显示缩略图；
  - 模型名称（默认正面图文件名）、精细度（标准 / 精细）、PBR 材质开关、放入文件夹；
  - 提示文字：「照片要白底或干净背景、主体完整；多给几个角度会更准。每次生成消耗腾讯混元积分」；
  - 提交前二次确认。
- 模型列表上方一块 **「生成任务」** 面板（有任务时才显示）：每行 名称 / 状态（排队中 · 生成中 · 保存中 · 完成 · 失败）/
  已用时 / 失败原因 / 预览图（有就显示）/ 删除；完成的行给「预览」「去挂商品」提示。
  有进行中的任务时每 10 秒刷新一次，全部结束停止刷新；刷新时同时刷新模型列表。
- 文案进 `i18n/locales/{zh,en,fr}.js`，键 `files.gen.*`。
- `api/console.js`：`submitModelGen` / `listModelGen` / `delModelGen`。

## 8. 错误处理

| 情况 | 表现 |
|---|---|
| 没配腾讯云文件存储 / 密钥解不出来 | 提交即报，提示去站点概览 |
| 密钥无混元权限 / 没开通 / 积分不足 | 提交即报（Submit 返回错误），任务 `FAIL`，原因按 6.3 翻译 |
| 图片不是本站文件库的 / 不是 https | 400，提示从文件库选 |
| 达到每日上限 | 400，「今天已经生成 N 次，明天再试或调高上限」 |
| 混元侧失败 | 任务 `FAIL`，显示原因 |
| 下载 / 转存失败 | 保持 `SAVING`，下次轮询重试；连续 5 次失败 → `FAIL` |
| 服务重启 | 任务状态都在库里，重启后继续轮询 |
| 生成的 glb 很大 | 照常入库（上限 100MB）；模型卡片上本来就显示大小。**没做**「超过 15MB 标黄」——默认 15 万面已经压住了体积，真遇到再加 |
| 提交那一刻服务重启（停在 SUBMITTING、没有混元任务号） | 轮询发现超过 5 分钟还没任务号 → FAIL「提交过程中服务重启了」 |

## 9. 测试与验收

后端单测（纯 Mockito，不起 Spring）：

- `TencentApiClientTest`：Authorization 的格式与凭证范围、日期按 UTC、对每个输入都敏感、action 大小写不影响签名。
  （腾讯云文档里的签名样例密钥是打码的，没法逐字对拍；真签名对不对靠联调那一次请求验。）
- `ModelGenServiceTest`：非本站 / http 图片被拒、每日上限、未启用、没配密钥、请求体（单图 / 多视图字段名）、
  挑 glb（Type 大小写、按路径兜底、忽略 http）、glb 魔数、错误码翻译表、超时判定、卡死的 SUBMITTING、响应外壳。

前端：`station-console-ui` `npm run build` 通过。

联调（需要真实积分，**用户在康辰站管理台操作**，我看日志和库）：

1. 开通 + 领免费积分 + （若是子账号）挂 `QcloudAI3DFullAccess`。
2. 用 12010304 的空气泵正面图提交一次「标准」→ 任务从排队到完成 → 模型库出现新模型 → 预览能转。
3. 把新模型挂到商品、做标注、前台能看。
4. 故意用一张非本站图片地址调接口 → 400。

发版顺序：**迁移 SQL → 后端 → 管理台**（前台不发）。
⚠ 本地 HEAD 里有同事未上线的「商品规格维度」（`75040245`，需要它自己的迁移），发版照 3D 标注那次的做法：
以线上基线拼树，只叠本功能的文件，并用 md5 对账证明只差这些文件。

## 10. 影响面

- SQL：`migration/2026-09-26-model-gen-job.sql`（新）、`01_station_schema.sql`
- station-console：`util/TencentApiClient`（新）、`util/Ai3dClient`（新）、`util/TmtClient`（改道）、
  `domain/SiteModelJob`、`mapper/SiteModelJobMapper` + XML（新）、`service/ModelGenService`（新）、
  `SiteFileService`（抽 `saveGeneratedModel`）、`SiteImageMapper`（`countByUrl`）、`CodeGenerator`（`SMJB`）、`application.yml`（`ai3d` 段）、
  `ConsoleApiController`（三个端点）、单测三个
- station-console-ui：`components/ModelGenPanel.vue`（新，弹窗 + 任务列表，不往 1000 行的 Files.vue 里塞）、`views/Files.vue`（按钮 + 挂面板）、`api/console.js`、`i18n/locales/{zh,en,fr}.js`

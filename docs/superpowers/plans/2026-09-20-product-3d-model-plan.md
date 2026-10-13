# 独立站商品 3D 模型 —— 实施计划

设计：`docs/superpowers/specs/2026-09-20-product-3d-model-design.md`。
每一步做完都有可验证的落点；按顺序做，前一步不通不进下一步。

## 一、数据层

1. 写 `sql/station/migration/2026-09-20-goods-model3d.sql`：建 `u_s_sitemodel`、`u_s_sitegoods` 加 `model3d`、`u_s_sequence` 插 `model`。附验收查询与回滚。
2. `sql/station/01_station_schema.sql` 同步：表 DDL 放在 `u_s_sitevideo` 之后，`model3d` 列放在 `sitevideos` 之后，序列种子加 `('model', 1)`。
3. `sql/station/migration/2026-09-06-file-url-rebase.sql` 注释里的「爆炸半径」清单补 `u_s_sitemodel.modelurl` 和 `u_s_sitegoods.model3d`。
4. 本机开发库跑迁移脚本（记忆里说本机库落后于线上，先确认 `u_s_sitevideo` 存在）。

验证：`show create table u_s_sitemodel`；`select * from u_s_sequence where name='model'`。

## 二、后端（station-console）

5. `StationProperties.Upload` 加 `modelExt`（默认 `glb`）+ `modelExtList()`；`application.yml` 加 `model-ext: glb` 和注释。
6. `CodeGenerator` 加 `SEQ_MODEL` / `PREFIX_MODEL = "SMDL"` / `nextModelId()`。
7. `domain/SiteModel.java`、`mapper/SiteModelMapper.java`、`resources/mapper/SiteModelMapper.xml`（照视频镜像，去掉宽高时长）。
8. `SiteFileService`：`MODEL_FOLDER`、`selectModelList` / `countModelsOf` / `uploadModel` / `renameModel` / `removeModel`。
9. `SiteFolderService` 三处二选一改三选一（常量 `TYPE_MODEL`、`clearFolder`、`moveFiles`、`normalizeType`）；`SiteFolderMapper.xml` 的 `countFiles` 加 `model` 分支。
10. `ConsoleApiController`：`/file/model` 四个端点；`storageSave` 加 `hiddenModels`。
11. `SiteGoods` 加 `model3d`；`SiteGoodsMapper.xml` 四处；`SiteGoodsService` 加 `normalizeModelUrl` 并在 `createGoods` / `saveGoods` 里调；`SiteOpenService.goodsDetail` 下发 `model3d`。
12. 新建 `src/test/java/.../service/` 三个 Mockito 单测。

验证：`mvn -o -q -pl station-console test`（或在 station-console 目录 `mvn -o test`）通过；本机起 jar，用 curl 传一个 .glb 到 `/api/file/model` 返回 200，传 .gltf 返回错误。

## 三、管理台前端

13. `station-console-ui/package.json` 加 `three`，`npm install`。
14. `station-shared/ModelPreview.vue`（新）。
15. `station-console-ui/src/api/console.js` 加四个模型接口。
16. `Files.vue`：第三个标签、三态判定、模型卡片 + 预览弹窗、上传不量尺寸、存储切换弹框加模型数。
17. `MediaPicker.vue`：`type="model"` 分支 + 模型卡片。
18. `Goods.vue`：「3D 模型」区 + 装载/提交。

验证：`npm run build` 通过；本机起 console-ui 指向本机后端，走一遍传模型→挂商品。

## 四、前台（station-site）

19. `useShopCopy.ts` 加 `d3JumpLabel` 各语言文案。
20. `pages/products/[code].vue`：`modelUrl` 优先 `p.model3d`；顶部「查看 3D」按钮 + 滚动 + 提前置 `stageSeen`；改文件头注释。
21. `useSite.ts` 的 `model3d` 注释改为「后端已下发」。

验证：`npm run build` 通过；本机起 site 指向本机后端，商品页出现按钮、展示台能转；一个只在规格里填 `model3d` 的商品仍显示。

## 五、收尾

22. 提交（分两笔：后端 + SQL 一笔，前端两个工程一笔）。
23. 发版按 `station-deploy-procedure`：迁移 SQL → console jar → console-ui → site。

# station-shared —— 两个管理台宿主共用的页面组件

这个目录是独立站管理台的共享组件目录。当前只有一个实际宿主：

| 宿主 | 给谁用 | 能改什么 |
| --- | --- | --- |
| 独立管理台（`station-console-ui/`） | 商家 | 装修、商品、询盘、文件；管不了合同类字段 |
| 前台（`station-site/`，2026-09-25 起） | 买家 | **只引 `model3d/marks.js`**（商品 3D 标注的画法），别的组件都不引 |

⚠ 前台引 `model3d/marks.js` 是为了让「管理台编辑器里摆的位置 = 前台显示的位置」，
两边各抄一份迟早漂移。所以**改 `model3d/` 下的文件要验两个构建**（`station-console-ui` 和
`station-site` 各 `npm run build`），别名配在 `station-site/nuxt.config.ts`。
这个目录里的 `.vue` 组件前台仍然不用。

**要合的是页面代码，不是入口。** 这个目录放独立站管理台的共享页面组件。

## ⚠ 现状（2026-09-10 复核）：ERP 内嵌宿主已经移除

ERP 那侧**已经不再内嵌本目录的任何组件**——`RuoYi-Vue3/src` 里搜不到
`@station-shared`，`views/site/console/` 目录也没了。装修、商品、询盘、文件
全部搬到站点自己那台机器的管理台上做（`views/site/station/index.vue` 文件头写了这件事），
ERP 只留下站点档案那张表（域名、语言、到期日、跳转密钥）。

所以修改这里的文件目前只需要验证 `station-console-ui` 一个构建。
ERP 侧的历史别名和说明暂时保留，不能据此判断 ERP 仍然承载独立站管理页面。

要动这个目录之前先确认一下现状还是不是这样（搜一次 `@station-shared` 就知道），
别为一个已经不存在的约束缩手缩脚。

## 为什么放在一个中立目录，而不是让一边 import 另一边

`station-console-ui/vite.config.js` 里写着一条明确的决定：

> 它和 ERP 前端（RuoYi-Vue3）没有任何代码依赖……让一个跑在公网的外部系统去 import
> ERP 前端的东西，等于把两个部署绑死。

这条约束仍然成立：`station-console-ui` 依赖的是这个中立目录，
**不是 RuoYi-Vue3**，独立站管理台与 ERP 前端各自构建、各自部署。

如果 ERP 将来重新接入，这个目录才会重新成为两边的公共依赖；届时改文件要同时验两个构建。
当前它保证「首页装修」这类页面只有一份实现——加一个语言 tab 不用写两遍，
也不会再出现盘点文档 §二.3 记的那种漂移（同一段商家指引，两边文案不一致）。

## 怎么用

`station-console-ui/vite.config.js` 通过 `@station-shared` 别名指向这里。

```js
import DecorateForm from '@station-shared/DecorateForm.vue'
```

管理台宿主通过 `saver` 等依赖注入点提供保存和文件接口，组件内部不关心后端路径。

### 宿主要提供的东西

这个目录里的组件不 import 任何宿主的 `@/`，需要什么由宿主递进来：

| 名字 | 谁在用 | 宿主在哪儿给 |
| --- | --- | --- |
| `saver` prop | DecorateForm | 页面上直接传 |
| `provide('stationSite')` / `provide('reloadStationSite')` | DecorateForm | 管理台外壳 |
| `provide('stationCanEdit')` | DecorateForm | `views/Decorate.vue`。没给就默认「能改」——真闸门在后端 |
| `provide('stationMediaApi')` | MediaPicker | `main.js`，三个文件列表接口 |
| 全局组件 `<pagination>` | MediaPicker | `main.js`（两个宿主都注册了） |

## 现在有什么

| 文件 | 说明 |
| --- | --- |
| `DecorateForm.vue` | 首页装修表单。分语言编辑，见组件头部注释 |
| `ItemRows.vue` | 「图标 + 标题 + 说明」三列可增删列表，装修里的特色/能力两块在用 |
| `MediaPicker.vue` | 文件资源库选择器。商品、博客封面、装修的图片格子都用它 |
| `ImageField.vue` | 装修里「一张图」那一格：地址框 + 选图按钮 + 缩略图。内部套 MediaPicker |
| `VideoField.vue` | 同上，换成「一段视频」。Banner 每张幻灯片可以配一段（2026-09-21） |

`MediaPicker.vue` 是 2026-09-10 从 `station-console-ui/src/components/` 搬过来的
（原先这里写着「还没抽，等改到那两个页面时再说」）。搬的理由是装修的图片格子要用它，
而共用目录里的组件不能反过来 import 宿主的 `@/components/`。搬的时候只改了一处：
三个列表接口从 `import … from '@/api/console'` 换成 `inject('stationMediaApi')`。
宿主的 Goods.vue / Blog.vue 跟着把 import 路径改成 `@station-shared/MediaPicker.vue`。

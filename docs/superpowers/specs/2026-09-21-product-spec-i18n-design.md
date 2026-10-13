# 商品规格参数多语言 —— 设计

日期：2026-09-21
范围：独立站 `station-console`(后端)、`station-console-ui`(管理台)、独立站库 `sdsstation`。
**前台商城 `station-site` 不改**(它按 `Object.entries(product.specs)` 渲染,后端按语言返回译好的键值对即可)。

## 背景与目标

规格参数(`u_s_sitegoods.specsjson`,如 `{"Material":"Platinum cured silicone","Structure":"4 cavities, single row"}`)
现在是**单语言**:只存在主表一块 JSON,多语言表 `u_s_sitegoods_lang` 里没有规格,详情接口对所有语言吐同一块。
于是葡语页也显示英文原样。

目标:规格按当前语言显示,**键和值都本地化**(用户 2026-09-21 定),机器翻译出初稿人工核对,
某格没译回退默认语言。

## 关键决策(已确认)

- **键值都翻**:切语言时左列参数名(Material→Estrutura)和右列值都本地化。
- **对齐用「默认语言的键」当身份**:和「分类译名」的 catkey 同一思路。改了默认键名,那一行的译文重置(可接受)。
- 有些值不该翻(FDA/RoHS/Shore A/数字单位):机翻只出初稿,商家人工留原文。

## 数据模型

复用 `u_s_sitegoods_lang`,加一列 `specsjson`,存这个商品在这个语言下的规格译文,
**按默认语言的键当 key**,值是 `{k: 译键, v: 译值}`:

```
-- 葡语行 u_s_sitegoods_lang(lang='pt').specsjson:
{"Material":{"k":"Material","v":"silicone curado com platina"},
 "Structure":{"k":"Estrutura","v":"4 cavidades, fila única"}}
```

- 主表 `u_s_sitegoods.specsjson` 仍是默认语言那份,是「有几行、什么顺序」的**源头**。
- 每行独立回退:某行在某语言没译 → 那一行显示默认语言的键值,其余行照常显示译文。
  (整块回退做不到「新加的行在已翻语言里显示」,逐键回退可以,所以用逐键。)

列类型 `varchar(4000)`:主表 specsjson 是 varchar(2000),译文带 `{k,v}` 包装约 2.5 倍,放宽到 4000。

## 后端(station-console)

**存**
- `SiteGoodsLang` 加 `specsJson` 字段;`LangResult` resultMap、`selectLangs`、`upsertLang` 三处加 `specsjson`。
- `SiteGoodsService.saveGoods` 的语言循环里 `text.setSpecsJson(...)`,存前做 JSON 合法性校验
  (复用 `normalizeJson` 的思路:parse 不过就拒)。
- 提交体:管理台 `langs[i]` 多带一个 `specsJson`(见下),后端原样落库。

**取(按语言下发)**
- `SiteGoods` 加**瞬态**字段 `langSpecsJson`;`GoodsResult` 加 `<result property="langSpecsJson" column="langspecsjson"/>`;
  `selectPublicOne` 多选 `l.specsjson langspecsjson`(请求语言那一份;默认语言那份仍是主表 `sg.specsjson`)。
- `SiteOpenService.productDetail` 把 `result.put("specs", parseObject(...))` 换成
  `buildSpecs(item.getSpecsJson(), item.getLangSpecsJson())`:以默认 specsjson 为行清单(保序 LinkedHashMap),
  逐行取 langSpecsJson 里同 key 的 `k`/`v`,非空则用译文、空则回退默认键/值,输出普通 `{键:值}`。

## 管理台(station-console-ui / Goods.vue)

- `specRows` 每行扩成 `{ key, value, tr: { <lang>: {k, v} } }`(key/value 是默认语言)。
- 规格表上方加**语言切换**(照 `comboLang` 那套 `specLang` 单选):
  - 选默认语言 → 编辑 `row.key` / `row.value`(定义有哪些行)。
  - 选其它语言 → 编辑 `row.tr[lang].k` / `row.tr[lang].v`,占位符提示默认键/值,留空则回退。
- **加载**:默认 specsjson → rows;再把 `data.langs[*].specsjson`(按默认 key)填进 `row.tr`。
- **保存**:默认 `specsJson = {row.key: row.value}`(不变);每个语言 `specsJson = {row.key: {k,v}}`
  (只含有内容的行),并进 `langs[lang].specsJson`。
- **翻译接进现有按钮**:`translationSource` 增加 `specIndexes`(每行推 [默认键,默认值] 两条),
  `applyTranslation` 按 `fields+combos` 之后的偏移回填 `row.tr[lang].k/.v`,只补空、不覆盖手改——
  和文案、组合完全一致。「全部语言翻译」一次把文案+组合+规格都补上。

## 前台(station-site)

不改。`[code].vue` 已按 `Object.entries(product.specs)` 渲染,后端返回译好的对象即生效。

## 错误处理 / 边界

- 某语言某行没译 → 逐键回退默认,不出空格。
- 译文 JSON 非法 → 保存时拒(和默认 specsjson 一致)。
- 两行译成同一个键 → 对象里后者覆盖前者(和默认 specsjson 本身同风险,罕见,商家自负)。
- 站点没开的语言不存(沿用 saveGoods 现有 `allowed` 过滤)。

## 测试

- 后端单测:`buildSpecs` 的逐键回退(有译/缺译/整块缺失/键改名)、下发对象保序。
- 手工:管理台某商品规格切语言填/翻译/保存 → `/openapi/site/products/{code}?lang=pt` 的 `specs` 是译文、
  `?lang=<未译>` 回退默认;前台产品页切语言看规格表。

## 发版

迁移(加列)→ 后端 jar → 管理台 dist。**前台不发**。按 [[station-deploy-procedure]]。
影响面:`u_s_sitegoods_lang`(+1 列)、`SiteGoodsLang`/`SiteGoods`/`SiteGoodsMapper(.xml)`/
`SiteGoodsService`/`SiteOpenService`、`Goods.vue`。

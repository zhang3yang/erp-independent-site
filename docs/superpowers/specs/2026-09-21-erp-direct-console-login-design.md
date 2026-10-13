# ERP 登录直达独立站管理台 —— 设计

> **2026-09-22 已被第二版取代**：用户看了效果后要求 ERP 登录页（带验证码）进来的就进 ERP，
> 不再自动跳站点后台；直达改走专用登录页 `/store-login`，见
> `2026-09-22-store-login-page-design.md`。本文的「直达判定」三条件仍然有效（那个接口复用它），
> 前端自动跳转和 `/tenant/select` 里的 `directConsoleSiteCode` 已删。

日期：2026-09-21
范围：ERP 后端 `ruoyi-admin` / `ruoyi-system`、ERP 前端 `RuoYi-Vue3`。
**独立站三个模块不改**：入口协议仍是 `docs/独立站与ERP的边界.md` §4.2 的签名令牌 + POST 表单。
**不加表、不加列。**

## 背景与目标

场景：一个外部客户（首例是法国商家，站点 `esvafit.vclinker.com`）在 ERP 里只有一件事可做——
点「独立站管理」里的「进入管理」。现在他登录后要经过「选商家」→ 工作台 → 独立站管理 → 进入管理
四步，而且全程是中文界面。

目标：这种账号登录 ERP 之后，**不看到选商家页和工作台**，直接落到站点管理台首页。
其他账号（业务员、管理员）的路径不变。

站点管理台**没有登录页**（边界文档 §6a′），账号密码只存在 ERP 一侧，所以 ERP 登录页这一步躲不掉；
本设计消掉的是登录之后的三步。

## 直达判定（核心规则）

在 `POST /tenant/select` 成功之后判，**三个条件同时成立**才直达：

1. 不是平台超级管理员。
2. 这个人在该商家下的租户权限**非空、包含 `site:station:list`、且每一条都以 `site:station:` 开头**。
   意思是：他在 ERP 里除了「独立站管理」没有别的菜单。业务员再怎么只管一个站也带订单、库存权限，不会命中。
3. 该商家名下**恰好一个**可跳转的站：`status` 非停用、算得出管理台地址（有主域名或管理台域名）、
   跳转密钥已配置。零个或多个都不直达。

不给账号加任何「直达」标记：判据全部来自已有的权限配置。以后再来一个同类客户，照样配权限即可，
不需要再改代码或改数据。

**为什么不放宽第 2 条**（比如只要一商家一站就直达）：那会把只管一个站的自家业务员也强推出去，
而他们进 ERP 是要看订单的。

**为什么不在 `u_s_site` 上加「一商家一站」唯一约束**：主键 `(shop_code, sitecode)` 本来就允许多站，
需求只是「恰好一个才直达」，运行时判定就够，不该为一个入口场景改表结构。

## 后端

### `IStationSiteService.resolveDirectConsoleSite(shopCode, tenantPermissions, platformAdmin)`

返回 `StationSite`（可直达的那一个）或 `null`。实现放 `StationSiteServiceImpl`：
先按权限条件短路，再 `siteMapper.selectList(query)` 拿该商家全部站点，
用 `StationSite.isDisabled(status)`、`consoleBaseUrl() != null`、`secretConfigured` 过滤，
剩下恰好一条才返回。不加新 SQL：`selectList` 已经带 `secretconfigured` 布尔列，密文不进内存。

### `TenantShopController.select`

在现有响应上多放两个字段：

```
directConsoleSiteCode: "DLZ000001" | null
directConsoleSiteName: "Esva boots"  | null
```

只是告诉前端「可以直达、直达哪个站」。**令牌不在这里发**——签发仍走原来的
`POST /site/station/{siteCode}/console-jump`（有 `@ss.hasBusinessShop()` + `site:station:list` 守着、
有操作日志、有 `lastaccesstime`）。分成两步是为了不复制一份签发逻辑，也不绕过那条审计日志。

## 前端

### 新工具 `src/utils/consoleJump.js`

`submitConsoleJump({ action, token, target })`：动态建一个 `<form method="post">`，塞 `token` 隐藏域，
挂到 body、`submit()`、随即移除。令牌**只出现在请求体**，不进 URL、不长驻 DOM（边界 §4.2 / §4.4）。
`views/site/station/index.vue` 的「进入管理」改用它，删掉模板里那个隐藏 `<form>` 和 `nextTick` 那段。
两处行为差异只有 `target`：列表页开新窗口（`_blank`），登录直达在当前窗口（`_self`）。

### `views/tenant/select.vue`

- `loadShops()` 之后：**非平台管理员且只有一个商家** → 自动 `enterShop(那一个)`，不再等他点。
  平台管理员仍看列表（他还有「平台商家管理」入口要选）。
- `enterShop()`：`selectTenantShop` 返回了 `directConsoleSiteCode` → 调 `createConsoleJump(code)` →
  `submitConsoleJump({ target: '_self' })`，流程到此结束（页面被 POST 带走）。
  没有 → 和现在一样 `window.location.replace(BASE_URL)` 进工作台。
- **失败回落**：签发令牌失败（站被停用、密钥没配、网络错）→ 提示「站点跳转失败，先进入工作台」，
  然后照常进工作台。工作台里仍有「独立站管理」菜单可手动进，不会卡成死胡同。

### 不改的地方

- `login.vue` 仍 `router.replace('/tenant/select')`，`permission.js` 守卫不动。
- 直达之后 ERP 的登录态还在浏览器里。他若再打开 ERP 地址，守卫判 `tenantSelected` 为真，
  落到只有一个菜单的工作台——这是正常状态，不需要再拦。

## 数据与配置（不改代码，但缺一项就进不去）

1. 平台层：超级管理员在「商家管理」建法国商家，只把「独立站管理」菜单分给它。
2. 商家层：建一个角色，只勾「独立站管理 → 查看」（`site:station:list`）；不勾新增/修改/删除/重置密钥。
   把客户账号挂到这个角色。
3. ERP「独立站管理」登记 esvafit：`shop_code` = 法国商家，主域名 `esvafit.vclinker.com`，
   管理台域名留空（派生 `console.esvafit.vclinker.com`）。点「重置跳转密钥」拿明文。
4. esvafit 服务器：`u_s_siteinfo.sitecode` 与 ERP 一致、`consolesecret` 写明文、重启 `station-console`。
   ⚠ 探到该机 `/sso` 302 到 `http://`，需补 `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`（同 kangchen 坑二）。
5. 站点侧首次进入默认 `operator` 角色；要给他装修/存储/用户管理权限，由站点 admin 在「用户管理」里提。

## 已知短板（本设计不解决）

ERP 登录页和 `station-console-ui` 都没有多语言。法国客户登录那一下可以忍，管理台是天天用的——
管理台英文化应作为配套项另立设计。

## 验证

- 后端：`mvn -o -pl ruoyi-system,ruoyi-admin compile` 通过。
- 前端：`vite build` 通过。
- 本机联调：ERP + station-console 都在开发机（见记忆 `station-sso-setup`「本地开发怎么配对」）。
  用一个只有 `site:station:list` 的商家账号登录 → 应直接落到 `localhost:5174`（管理台）；
  用超级管理员登录 → 仍看到选商家页；用带订单权限的账号登录 → 仍进工作台。
  把站点停用再登 → 提示回落、进工作台。

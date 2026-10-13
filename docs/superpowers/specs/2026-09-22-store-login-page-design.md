# 独立站专用登录页 —— 设计

日期：2026-09-22
范围：ERP 后端 `ruoyi-framework` / `ruoyi-admin`、ERP 前端 `RuoYi-Vue3`。站点三模块**不改代码**，只改一行配置。
接着 `2026-09-21-erp-direct-console-login-design.md`：那一版是「登进 ERP 再被送走」，这一版是「一开始就不进 ERP」。

## 背景与目标

用户 2026-09-22 看了效果后的反馈：法国客户登录时看到的仍是「众联 ERP 系统」标题、中文界面、验证码和
那张背景图。要的是：**一个只有账号、密码、登录按钮的独立页面，不带 ERP 的品牌和验证码，点登录直接进站点后台，
全程不进 ERP 的任何页面。**

账号密码仍然只存在 ERP（站点后台没有登录页，边界 §6a′ 撤掉的，不能再长回来）。
变的是入口页和后面的流程，不是身份源。

## 流程

```
浏览器打开 https://console.erp.vclinker.com/store-login
   │ 输账号密码，POST /station/login {username, password}
   ▼
ERP 后端（一个接口做完，不发 ERP token）：
   ① 校验账号密码（沿用 SysLoginService，跳过验证码）
   ② 这个人不是平台管理员、只属于一个商家
   ③ 租户权限只有 site:station:*、且含 list（同上一版直达判定）
   ④ 商家名下恰好一个可跳转的站
   ⑤ 签跳转令牌（原来的 issueJump，带 lastaccesstime 和日志）
   → { action, token, siteName }
   │
   ▼
前端把 token POST 表单到 action（站点 /sso），页面变成站点后台
```

②③④ 任何一条不满足，统一回一句英文「This is not a store administrator account」，不区分是哪条——
这是个免验证码的公开接口，说得越细越方便撞。

**浏览器里不留 ERP 会话**：接口不返回 ERP token，这个人从头到尾没有 ERP 登录态，
不会再出现「再打开 ERP 落到工作台」。

## 后端

### `SysLoginService.authenticate(username, password)`

从 `login()` 里把「前置校验 + AuthenticationManager 认证 + 记登录日志」拆出来，返回 `LoginUser`，
不发 token、不校验证码。`login()` 改成 `validateCaptcha` → `authenticate` → `createToken`，行为不变。
账号锁定（密码错 N 次）在 `UserDetailsServiceImpl` / `SysPasswordService` 里，走这条路照样生效。

### `StationLoginController`（ruoyi-admin，`POST /station/login`）

- `@Anonymous`（`PermitAllUrlProperties` 扫描它进白名单）；
- `@RateLimiter(time = 60, count = 10, limitType = IP)`：没有验证码，这道锁必须有；
- 认证失败一律 `Incorrect username or password.`，不透露账号存不存在；
- 判定不通过一律 `This is not a store administrator account.`；
- 签令牌失败（密钥解不开之类）`Store console is unavailable. Please contact the administrator.`；
- 复用 `IStationSiteService.resolveDirectConsoleSite`，判定规则不复制第二份。

接口的提示全是英文，因为它只给外部客户用；ERP 其他接口的中文提示不动。

## 前端

### `views/storeLogin.vue`，常量路由 `/store-login`，加进守卫白名单

- 只有：站点标题「Store Admin」、账号、密码、登录按钮、EN / FR 切换。没有验证码、没有「众联」、没有背景图。
- **不走 `utils/request.js`**：它会把中文错误弹出来、401 时弹「登录状态已过期」。这一页直接用 axios，
  自己渲染错误：后端给的英文（纯 ASCII）原样显示，其他情况（限流的中文提示、网络错）显示通用英文。
- 成功后 `submitConsoleJump({ target: '_self' })`，复用上一版抽出来的表单 POST 工具。
- 有 ERP 登录态的人打开这一页不影响（白名单直接放行）。

### 上一版的「登录后自动直达」**拿掉**（用户 2026-09-22 定）

从 ERP 登录页（带验证码的 `/login`）登进来的就进 ERP，不再自动跳站点后台；直达只走 `/store-login`。
删掉的是 `tenant/select.vue` 里签令牌那段和 `/tenant/select` 响应里的 `directConsoleSiteCode`；
「只有一个商家就自动进入」保留（和站点无关，是选商家页自己的便利）。
`resolveDirectConsoleSite` 留着，`/station/login` 用它。

## 站点侧（配置，不改代码）

esvafit 那台 `/etc/station/console.env` 的 `ERP_HOME_URL` 改成 `https://console.erp.vclinker.com/store-login`，
重启 `station-console`。这是管理台顶栏「回 ERP」那个链接，改完就是「回登录页」。

退出后的 `/login?reason=logged-out` 空页面**不动**：它故意不放链接（Entry.vue 注释里的理由），
客户退出后重新打开收藏的登录页即可。

## 不做的事

- 不给站点后台做本地账号密码（边界 §6a′）。
- 不做「按站点显示品牌 / logo」：登录前不知道是哪个站，页面保持中性。
- 不做独立域名（`login.esvafit...`）：nginx 一条反代就能加，等有第二个客户再说。

## 验证

- `mvn -o -pl ruoyi-framework,ruoyi-system,ruoyi-admin -am compile`、`vite build` 通过。
- 线上：法国账号在 `/store-login` 登录 → 直接到站点后台；wczladmin 在 `/store-login` 登录 → 英文拒绝；
  错密码 → 英文拒绝；连错 11 次 → 限流提示；`/login` 老入口不受影响。

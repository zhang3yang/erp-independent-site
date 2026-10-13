# station-console-ui — 独立站管理台前端

四个页面：站点概览、首页装修、商品上架、询盘管理。

从 ERP 前端（`RuoYi-Vue3`）的 `views/site/console/`、`views/site/goods/`、`views/site/inquiry/`
搬过来的。**是复制，不是引用**——两边可以各自演进，代价是有几百行重复代码（`Pagination`、
`RightToolbar`、`GoodsSelectDialog`、`currency.js` 这些），这个代价值得付：让一个跑在公网的
外部系统去 import ERP 前端的东西，等于把两个部署绑死。

---

## 跑起来

```bash
npm install
npm run dev      # http://localhost:5174，/api 等路径代理到 8081 的管理台后端
npm run build    # 产物在 dist/
```

开发时需要管理台后端（8081）和 Redis 跑着。**进不去是正常的**——直接打开
`http://localhost:5174` 会被送到「请从 ERP 进入」那一页，因为没有会话。
要真正进去，得从 ERP 点「进入管理」，或者手工走一遍 `/sso?t=票`。

---

## 从 ERP 那版改了什么

| | ERP 内置管理台 | 这里 |
| --- | --- | --- |
| 身份 | Pinia 里的 token，`Authorization` 头 | HttpOnly 会话 Cookie，JS 读不到 |
| 接口地址 | `/site/**`、`/inventory/**` | `/api/**`（同源，由本站 station-console 直接处理） |
| 站点 | URL 上的 `:siteCode` | **不存在**，由服务端会话决定 |
| 掉线 | 跳 ERP 登录页 | 跳入口页（没有登录页可跳） |
| 站点下拉 | 商品页/询盘页有 | 删了——一个管理台就是一个站 |
| 商品档案参考价 | 有 | 删了，后端白名单里就没这个字段 |

### 地址里没有 siteCode，这是有意的

ERP 那版是 `/station-console/:siteCode/goods`。搬过来时这一段去掉了：站点从会话来，地址栏上
根本不该出现它——出现了就会有人去改它，然后我们就得在每一层都防一遍。

现在前端从头到尾拿不到、也不需要一个可以被改的站点编码。

### `v-hasPermi` 只管显示，不管授权

`src/directive/hasPermi.js` 读的是会话带回来的 `perms`，那是**进门那一刻**抄下来的。
权限被收回之后它是旧的——按钮可能还在，但点下去是 403。

这是对的：让 ERP 做唯一的权威（那边每个接口都拿 userCode 现查一遍当下的权限），
管理台不维护第二份会过期的真相。藏起来的按钮不等于关上的门。

---

## 目录

```
src/
  api/console.js        全部 14 个接口，一个方法都不接受 siteCode
  store/identity.js     当前身份（站点、语言、币种、perms），只读，启动时拉一次
  utils/request.js      axios 实例：不带 token，401 跳入口页
  directive/hasPermi.js 只管按钮显示
  layout/AppShell.vue   顶栏 + 侧导航 + 退出
  views/                四个页面
  components/           从 ERP 前端复制过来的公共组件
```

`main.js` 里是**先拉身份、再挂载应用**。顺序不能反：顶栏要显示站点名，商品页要按站点开的语言
铺文案页签。先挂载再异步补的话，第一帧是个空壳然后突然跳变。

---

## 部署

`npm run build`，把 `dist/` 交给 Nginx，**和管理台后端放在同一个域名下**。

配置见 [`../deploy/nginx/station.windows.conf.template`](../deploy/nginx/station.windows.conf.template)
和 Linux 部署手册 [`../docs/独立站部署手册（Linux）.md`](../docs/独立站部署手册（Linux）.md)。

同源不是省事，是设计的一部分：跨域的话 `SameSite=Lax` 的会话 Cookie 带不过去，
还得额外处理 CORS。

@echo off
rem ============================================================
rem  独立站前台（SSR）—— 服务器启动脚本（Windows）
rem
rem  放在 C:\wczlerp\station-site\ 下，和 .output 同目录：
rem
rem    C:\wczlerp\station-site\
rem    ├── .output\            ← npm run build 的产物（整个目录传过来）
rem    ├── start-site.bat      ← 本文件
rem    └── install-service.ps1
rem
rem  ⚠ 直接双击运行只适合临时验证——关掉远程桌面进程就没了。
rem    生产上用同目录的 install-service.ps1 注册成 Windows 服务
rem    （那个脚本 -Target console 也能把管理台后端一起注册了）。
rem
rem  ⚠ 产物是 .output 不是 dist。SSR 的产物里带一个 Node 服务器，
rem    不是一堆静态文件，nginx 是反代给它而不是直接发文件。
rem ============================================================

rem 需要 Node 18+。先确认 node -v
set "NODE_BIN=node"

rem ---- 这里原来有一项 NUXT_PUBLIC_SITE_KEY，删了 ----
rem 那是「一个 ERP 服务 N 个站」时代的东西。现在一个部署一个站，前台打的是同一台
rem 机器上的站点后端，那个后端只连本站的库，物理上就取不到别的站的数据
rem （docs/独立站与ERP的边界.md §7.2）。站点身份由后端自己知道，前台一个字都不用配。

rem ---- 服务端取数：内网直连本机的站点后端 ----
rem SSR 时是这个 Node 进程自己去取数，走 127.0.0.1 少一次公网往返。
rem
rem ⚠ 8081 不是 8080。8080 是 ERP，而前台**不该认识 ERP**——要点 1：两边后端没有
rem   任何互通。那些 /openapi/site/** 接口在 ERP 那边已经删了，填过去只会得到一片 404。
set "NUXT_API_BASE=http://127.0.0.1:8081"

rem ---- 浏览器取数：留空 = 相对路径，由 nginx 转发 ----
rem 生产上就该留空：nginx 里有一条 /openapi/site/ 转 8081，
rem 这样浏览器发的是同源请求，不用 CORS 也少一次预检。
set "NUXT_PUBLIC_API_BASE="

rem ---- 站点对外地址：拼 canonical / hreflang / sitemap 的绝对 URL 要用 ----
rem 漏了它会从请求头推，反代场景下容易推出 http://，
rem 那样 Google 会以为你在往 http 导流。生产上必须填。
rem
rem 它必须是 nginx 上真的有 server 块的那个主机名。
rem
rem ⚠ 这台机器（康辰）是**单 host 布局**：前台整站在 buyer. 子域上，主域名在
rem   nginx 里没有 server 块。域名分工见
rem   deploy/nginx/station.windows.conf.template 文件头。
rem
rem ⚠ 统领站是**两 host 布局**（deploy/nginx/tongling.conf）：主域放公开页，
rem   buyer. 只放登录/注册/找回密码/重置密码/邮箱验证/账户。那种布局下
rem   SITE_URL 填主域、再加一个 NUXT_PUBLIC_BUYER_URL，两个变量都要填。
set "NUXT_PUBLIC_SITE_URL=https://buyer.kangchen.vclinker.com"

rem ---- 账号页那个 host。留空 = 不分 host，站内链接一律相对路径 ----
rem 单 host 布局（本机）留空；两 host 布局填 https://buyer.<主域名>。
set "NUXT_PUBLIC_BUYER_URL="

rem ---- 监听端口。⚠ 绝不放行到公网，安全组只开 80/443 ----
set "PORT=3000"
set "HOST=127.0.0.1"

cd /d "%~dp0"
%NODE_BIN% .output\server\index.mjs

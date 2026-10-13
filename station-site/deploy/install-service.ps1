<#
============================================================================
 把独立站前台注册成 Windows 服务（用 nssm）

 解决的问题：直接双击 start-site.bat 起的进程挂在那个登录会话上，**关掉远程桌面
 它就没了**，服务器重启后也不会自己起来。对一个对外的网站来说这不能接受。

 station-console（管理台后端 8081）有一模一样的问题——它现在也是双击 bat 起的，
 是文档 §15 里记的已知缺陷。这个脚本用 -Target console 也能把它一起注册了。

 ---------------------------------------------------------------------------
 为什么是 nssm 不是 Docker

 这台服务器是 Windows，上面跑的是 nginx + Java，没有容器运行时。为了一个 Node 进程
 装 Docker Desktop 是把运维复杂度整个抬一级——要管镜像、要管数据卷、Windows 上还要
 决定用 Hyper-V 还是 WSL2 后端。nssm 是单个 exe，做的正好是「把一个命令行进程包成
 Windows 服务」这一件事，和现有环境是一路的。

 以后真要上容器，那是整套（ERP + 管理台 + 前台）一起迁的事，不该从前台开始。
 ---------------------------------------------------------------------------

 用法（管理员 PowerShell）：

     .\install-service.ps1 -Target site
     .\install-service.ps1 -Target console
     .\install-service.ps1 -Target site -Uninstall

 前提：nssm 在 PATH 里，或用 -NssmPath 指定。
       没有的话去 https://nssm.cc/download 下载，解压把 win64\nssm.exe
       丢进 C:\Windows\System32 即可。
============================================================================
#>

param(
    [ValidateSet('site', 'console')]
    [string]$Target = 'site',

    [string]$NssmPath = 'nssm',

    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'

# ---- 必须是管理员：注册服务要写 SCM ----
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)
if (-not $isAdmin) {
    throw "要用管理员身份运行 PowerShell。右键 PowerShell → 以管理员身份运行。"
}

# ---- 找 nssm ----
$nssm = (Get-Command $NssmPath -ErrorAction SilentlyContinue)
if (-not $nssm) {
    throw @"
找不到 nssm。两种办法：
  1. 去 https://nssm.cc/download 下载，把 win64\nssm.exe 放进 C:\Windows\System32
  2. 已经有了但不在 PATH 里，用 -NssmPath "C:\tools\nssm.exe" 指定
"@
}
$nssmExe = $nssm.Source

# ---- 两个目标的差异全在这张表里 ----
$conf = switch ($Target) {
    'site' {
        @{
            Name        = 'wczl-station-site'
            Display     = '众联 独立站前台 (Nuxt SSR)'
            Description = '独立站前台，SSR，监听 127.0.0.1:3000，由 nginx 反代。数据来自 ERP 的 /openapi/site/**'
            Dir         = 'C:\wczlerp\station-site'
            Exe         = (Get-Command node -ErrorAction SilentlyContinue).Source
            Args        = '.output\server\index.mjs'
            ExeHint     = 'Node 18+。没装的话去 https://nodejs.org 下 LTS 版'
            # ⚠ 这些值要和 start-site.bat 里保持一致。改一处漏一处的话，
            #   手工起和服务起的行为会不一样，这种不一致最难查
            Env         = @(
                # ⚠ 8081 不是 8080。8080 是 ERP，而前台**不该认识 ERP**
                #   （docs/独立站与ERP的边界.md 要点 1）。8081 是同一台机器上的站点后端。
                #
                #   NUXT_PUBLIC_SITE_KEY 那一项删了：一个部署一个站，
                #   站点后端只连本站的库，没有范围要限定（§7.2）。
                'NUXT_API_BASE=http://127.0.0.1:8081'
                # 这个值拼的是 canonical / hreflang / sitemap 里的绝对 URL，
                # 所以它必须是**nginx 上真的有 server 块**的那个主机名，
                # 否则 Google 收录到的每一条都指向一个连不上的地址。
                #
                # ⚠ 这台机器（康辰）用的是**单 host 布局**：前台整站在 buyer. 子域上，
                #   主域名在 nginx 里没有 server 块
                #   （deploy/nginx/station.windows.conf.template §2）。
                #   所以这里填的是 buyer.。
                #
                # ⚠ 统领站是**两 host 布局**（deploy/nginx/tongling.conf）：
                #     主域   放公开页（首页、商品、购物车、我的询价）
                #     buyer. 只放登录 / 注册 / 找回密码 / 重置密码 / 邮箱验证 / 账户
                #   那种布局下这两个变量要一起填，而且 SITE_URL 是**主域**：
                #     NUXT_PUBLIC_SITE_URL=https://tongling.vclinker.com
                #     NUXT_PUBLIC_BUYER_URL=https://buyer.tongling.vclinker.com
                #   康辰哪天照那样改，要连着 nginx（主域的 server 块 + 证书）、
                #   station-console 的 STATION_COOKIE_DOMAIN、以及库里
                #   u_s_siteinfo.domain 一起改，单改这里只会让 canonical 指向死地址。
                'NUXT_PUBLIC_SITE_URL=https://buyer.kangchen.vclinker.com'
                # 账号页那个 host。留空 = 不分 host，站内链接一律相对路径。
                # 单 host 布局下就该留空，见上面。
                'NUXT_PUBLIC_BUYER_URL='
                'PORT=3000'
                'HOST=127.0.0.1'
            )
        }
    }
    'console' {
        @{
            Name        = 'wczl-station-console'
            Display     = '众联 独立站管理台后端'
            Description = '独立站后端，监听 8081，由 nginx 反代。前台开放接口 + 管理台接口 + 本站用户体系，自带 MySQL'
            Dir         = 'C:\wczlerp\station-console'
            Exe         = (Get-Command java -ErrorAction SilentlyContinue).Source
            Args        = '-Xms256m -Xmx512m -jar station-console-1.0.0.jar'
            ExeHint     = 'Java 17+（Spring Boot 4）。装了但不在 PATH 的话，把 Exe 改成绝对路径'
            # ⚠ STATION_CONSOLE_SECRET 和 ERP_BASE_URL 都删了。
            #
            #   前者是票据兑换的凭据、后者是回调 ERP 的地址——那条回调整个没有了
            #   （docs/独立站与ERP的边界.md §4.1：那第③步就是一条后端互通，
            #   而且是最硬的一条）。跳转密钥现在存在库里（u_s_siteinfo.consolesecret），
            #   不走环境变量：它一站一份，属于站点档案的一部分。
            #
            #   ERP_HOME_URL 留着，但它现在**只是顶栏上一个显示用的链接**，
            #   不是一个会被后端调用的地址（§13 第 5 条的反向）。
            Env         = @(
                # ---- 数据库。⚠ 只连本机——两边的库在网络上互不可达（§5.1、§13 第 6 条）----
                'DB_HOST=127.0.0.1'
                'DB_USER=station'
                'DB_PASSWORD=PUT_THE_DB_PASSWORD_HERE'

                # ---- Redis。会话（CONSOLE_SESSION）、jti 一次性、各处限流都在里面 ----
                # 不配就用默认 127.0.0.1:6379。⚠ 它挂了管理台会话会全掉，不是可选组件
                'REDIS_HOST=127.0.0.1'

                # ---- 反代头。⚠ 这一条漏了，SSO 能验签成功但登不进去 ----
                #
                # 后端在 nginx 后面看到的 scheme 是 http，/sso 验签通过后会 302 到
                # http:// 而不是 https://，Secure 的会话 Cookie 在那一跳上不会发送，
                # 表现是「从 ERP 点进来，转了一圈还是空白页」，日志里却是成功的。
                #
                # 顺带它也让 getRemoteAddr() 返回真实客户端 IP——询盘按 IP 限流
                # （station.inquiry.*）要靠这个才算得准，否则所有访客都是 127.0.0.1，
                # 每 IP 限流退化成全站限流。
                'SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK'

                # ---- 文件存储 ----
                # 康辰使用腾讯云 COS：file.kangchen.vclinker.com 是 COS 自定义域名，
                # 不是本机 station-upload 目录的 Nginx 入口。上传模式和公开 URL
                # 由 sdsstation.u_s_storage 的 provider/public_url 决定。
                # STATION_FILE_BASE_URL 只给 local 模式使用，当前必须留空；这样即使
                # COS 配置误丢，也会拒绝上传而不是生成指向 COS 的假本地文件地址。
                'STATION_UPLOAD_DIR=C:\wczlerp\station-upload'
                'STATION_FILE_BASE_URL='

                # ---- 对外地址 ----
                # 管理台自己的地址，询盘通知邮件里那个「去处理」链接用它拼。
                # ⚠ 只能从这里给。jar 同目录的 application-mail.yml 里**不要**再写一份
                #   console.home-url——两个来源哪个赢取决于 config.import 的优先级，
                #   赌错的表现是邮件链接指向 localhost:5174
                'CONSOLE_HOME_URL=https://console.kangchen.vclinker.com'
                # 顶栏上一个显示用的链接，后端不会去调它（§13 第 5 条的反向）
                'ERP_HOME_URL=https://console.erp.vclinker.com'
                'CONSOLE_COOKIE_SECURE=true'
                'STATION_BUYER_COOKIE_SECURE=true'

                # ---- 对象存储主密钥 ----
                # u_s_storage 里的 COS SecretKey 是用它加密存的（AesUtils）。
                # 换了这个值，已存的密钥全部解不开——要在管理台重填一遍。
                # 线上放在 C:\wczlerp\.config-secret，注册服务时读出来填这里
                'STATION_CONFIG_SECRET=PUT_THE_CONFIG_SECRET_HERE'

                # ---- 权限开关。⚠ 默认值是 true，也就是「所有权限点全放行」----
                # 站点只有一两个操作员时这样最省事，但一旦要区分角色，
                # 必须显式设成 false，否则 @RequirePerm 形同虚设
                'CONSOLE_GRANT_ALL_PERMS=true'

                # ---- 这里**没有**的两项，是故意没有的 ----
                #
                # STATION_JUMP_SECRET：跳转密钥的正位是库里的
                #   u_s_siteinfo.consolesecret（明文，43 字符，由 ERP 生成、只显示一次）。
                #   环境变量只是 JumpTokenVerifier.resolveSecret() 的兜底，走它会打 WARN。
                #   一站一份的东西属于站点档案，不属于服务配置。
                #
                # SMTP 四项（MAIL_HOST / MAIL_PORT / MAIL_USER / STATION_NOTIFY_FROM）：
                #   走 jar 同目录的 application-mail.yml，因为里面有 SMTP 密码，
                #   而 nssm 的环境变量在注册表里是明文、任何管理员都能看。
                #   模板见 station-console/application-mail.yml.example。
                #
                # STATION_CONSOLE_SECRET / ERP_BASE_URL：这两项**整个不存在了**。
                #   前者是票据兑换的凭据、后者是回调 ERP 的地址——那条回调没有了
                #   （§4.1：那第③步就是一条后端互通，而且是最硬的一条）。
                #   仓库里那份写着它们的 start-console.bat 已经删了。
            )
        }
    }
}

# ---- 卸载 ----
if ($Uninstall) {
    & $nssmExe stop $conf.Name confirm 2>$null | Out-Null
    & $nssmExe remove $conf.Name confirm
    Write-Host "已移除服务 $($conf.Name)"
    return
}

if (-not $conf.Exe) {
    throw "找不到可执行程序。$($conf.ExeHint)"
}
if (-not (Test-Path $conf.Dir)) {
    throw "目录不存在：$($conf.Dir)。先把产物传上去再注册服务。"
}

# ============================================================================
#  部署前检查（前台专有）
#
#  配置的生效顺序是：服务的环境变量 > nuxt build 时烘进产物的值。
#  **没有 .env 这一环**——Nitro 的生产服务器不读 .env，那是 nuxt dev / nuxt build
#  才认的东西。下面两条检查针对的就是这个顺序里的两个坑。
# ============================================================================
if ($Target -eq 'site') {

    # 产物本身在不在。上面只查了目录存在，目录建好但产物没传上来是常见情况
    $outputEntry = Join-Path $conf.Dir '.output\server\index.mjs'
    if (-not (Test-Path $outputEntry)) {
        throw @"
找不到构建产物：$outputEntry
SSR 的产物是 .output 整个目录（不是 dist），要连目录一起传上来。
本地构建：npm run build
"@
    }

    # ---- 坑① 生产目录里的 .env 是个纯陷阱 ----
    # 它不会生效（Nitro 生产服务器不读），但看起来像配置文件。运维改了它、
    # 重启服务、发现没变化，然后开始查后端——这半天是白花的。
    $strayEnv = Join-Path $conf.Dir '.env'
    if (Test-Path $strayEnv) {
        Write-Warning @"
生产目录里有 .env：$strayEnv
它不生效——Nitro 的生产服务器只认真正的环境变量。改它不会有任何效果。
真正生效的地方：$nssmExe edit $($conf.Name) → Environment 标签页
建议删掉，免得以后有人改了它以为改了配置。
"@
    }

    # ---- 坑② 产物里烘着一个构建时的 siteKey ----
    # runtimeConfig.public 的值在 nuxt build 时会被写进产物，当作运行时的默认值。
    # 构建机上只要有个 .env（或 shell 里设过 NUXT_PUBLIC_SITE_KEY），那个值就跟着
    # 产物走了。运行时没有同名环境变量时用的就是它。
    #
    # 后果特别难查：服务里忘了填 key，站点**不会**报「没配置 siteKey」——那个检查
    # 只在 key 为空时触发——而是拿着另一个站的 key 去取数，报 503「站点不可用」，
    # 日志里看着像后端挂了或者站点被停用了。
    $nitroChunk = Join-Path $conf.Dir '.output\server\chunks\_\nitro.mjs'
    $bakedKey = ''
    if (Test-Path $nitroChunk) {
        $m = [regex]::Match((Get-Content $nitroChunk -Raw), 'sk_[0-9a-f]{8,}')
        if ($m.Success) { $bakedKey = $m.Value }
    }
    $envKey = ($conf.Env | Where-Object { $_ -like 'NUXT_PUBLIC_SITE_KEY=*' }) -replace '^NUXT_PUBLIC_SITE_KEY=', ''

    if ($envKey -eq 'PUT_THE_SITE_KEY_HERE') {
        if ($bakedKey -ne '') {
            Write-Warning @"
本脚本里的 NUXT_PUBLIC_SITE_KEY 还是占位符，而产物里烘着一个构建时的 key：
  $bakedKey
不去填服务环境变量的话，站点会拿这个 key 取数。如果它不是本站的 key，
页面报的是 503「站点暂时不可用」，而不是「没配置 siteKey」——看着像后端坏了。
注册完务必去填：$nssmExe edit $($conf.Name) → Environment
"@
        }
    }
    elseif (($bakedKey -ne '') -and ($bakedKey -ne $envKey)) {
        # 环境变量会盖掉烘进去的值，所以这不是错误。只是把两个值都摆出来，
        # 免得以后有人在产物里搜到那个旧 key 时对不上号
        Write-Host "提示：产物里烘的是 $bakedKey，服务环境变量会用 $envKey 覆盖它（这是对的）。"
    }
}

# ---- 已存在就先移除，让这个脚本可以重复执行 ----
$exists = Get-Service -Name $conf.Name -ErrorAction SilentlyContinue
if ($exists) {
    Write-Host "服务 $($conf.Name) 已存在，先移除再重建……"
    & $nssmExe stop $conf.Name confirm 2>$null | Out-Null
    & $nssmExe remove $conf.Name confirm | Out-Null
    Start-Sleep -Seconds 2
}

Write-Host "注册服务 $($conf.Name) …"
& $nssmExe install $conf.Name $conf.Exe $conf.Args
& $nssmExe set $conf.Name AppDirectory  $conf.Dir
& $nssmExe set $conf.Name DisplayName   $conf.Display
& $nssmExe set $conf.Name Description   $conf.Description
& $nssmExe set $conf.Name Start         SERVICE_AUTO_START

# 环境变量。nssm 要求用 \n 连成一个串
& $nssmExe set $conf.Name AppEnvironmentExtra ($conf.Env -join "`n")

# ---- 日志。不设的话进程的输出直接丢掉，出事没有任何线索 ----
$logDir = Join-Path $conf.Dir 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
& $nssmExe set $conf.Name AppStdout (Join-Path $logDir 'stdout.log')
& $nssmExe set $conf.Name AppStderr (Join-Path $logDir 'stderr.log')
# 按大小轮转，否则跑几个月就是一个几 GB 的文件
& $nssmExe set $conf.Name AppRotateFiles 1
& $nssmExe set $conf.Name AppRotateOnline 1
& $nssmExe set $conf.Name AppRotateBytes 10485760   # 10MB

# ---- 崩了自动拉起 ----
& $nssmExe set $conf.Name AppExit Default Restart
# 起来就崩的话别疯狂重启刷爆日志，退避到 30 秒
& $nssmExe set $conf.Name AppRestartDelay 30000
# 停服务时先好好停，5 秒不退再杀
& $nssmExe set $conf.Name AppStopMethodSkip 0
& $nssmExe set $conf.Name AppStopMethodConsole 5000

Write-Host ""
Write-Host "注册完成。接下来："
Write-Host "  1. ⚠ 把环境变量里的占位符换成真值："
Write-Host "     $nssmExe edit $($conf.Name)    → Environment 标签页"
if ($Target -eq 'site') {
    Write-Host "     NUXT_API_BASE 要指向本机的站点后端（8081），不是 ERP 的 8080"
} else {
    Write-Host "     DB_PASSWORD 必须填对；康辰还要在管理台把 u_s_storage 配成腾讯云 COS"
    Write-Host "     ⚠ 还要在库里补一行 u_s_siteinfo（站点编码/名称/跳转密钥），"
    Write-Host "       其中 consolesecret 必须和 ERP「独立站管理」里那一份一模一样，否则跳转进不来"
}
Write-Host "  2. 启动：  Start-Service $($conf.Name)"
Write-Host "  3. 看状态：Get-Service $($conf.Name)"
Write-Host "  4. 看日志：Get-Content $logDir\stdout.log -Tail 50 -Wait"
Write-Host ""
Write-Host "验证（前台）：curl.exe -s -o NUL -w `"%{http_code}`" http://127.0.0.1:3000/"

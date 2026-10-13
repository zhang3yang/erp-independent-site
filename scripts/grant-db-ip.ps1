# =====================================================================
# 开发机换公网 IP 后，重新打通到生产库 49.234.194.194:3306 的直连
# =====================================================================
#
#   powershell -ExecutionPolicy Bypass -File scripts\grant-db-ip.ps1
#
# 自动探测本机公网 IP；想指定就 -Ip 1.2.3.4，想先看会做什么就加 -DryRun。
# 可重复跑：同一个 IP 跑第二次不会出错，也不会改坏什么。
#
# =====================================================================
# 为什么换个 IP 要动两个地方
# =====================================================================
#
# 直连要穿过两道门，**两道都认来源 IP**，缺一个都连不上：
#
#   1. 腾讯云安全组的 3306 放行规则  —— 只能在控制台点，本脚本管不了
#   2. MySQL 库里 station@<你的IP> 这个账号 + 对应的 GRANT —— 本脚本干这个
#
# 服务器自己的 Windows 防火墙三个配置文件全是关的，所以第 1 道就是唯一的
# 网络层防护。也因此 root 保持 localhost-only，外网只走 station 这个弱权限账号。
#
# 历史上换过三次 IP，其中 2026-09-08 和 2026-09-10 两次都是只改了安全组、
# 忘了补授权，浪费了两轮排查。这个脚本就是为了让这两步再也分不开。
#
# =====================================================================
# 卡在哪一层，不用装客户端也能判断
# =====================================================================
#
# 读 3306 的第一个包就够了（脚本里的 Test-Mysql3306 就是干这个）：
#
#   连不上 TCP                    -> 安全组没放行，去控制台
#   回 Host ... is not allowed    -> 安全组通了，缺库里的账号
#   回 8.0.28 版本串              -> 两道门都通了
#
# 还有一个伪装成成功的坑：**只 CREATE USER 不 GRANT**。那样能登进去、
# CURRENT_USER() 也正确，但 SHOW DATABASES 只剩 information_schema。
# 所以脚本最后一定会回读 SHOW GRANTS 确认，不只看「命令没报错」。
#
# =====================================================================
# 密码
# =====================================================================
#
# 仓库里不存明文库密码。脚本按这个顺序取：
#
#   root    : 环境变量 WCZL_DB_ROOT_PW  ，没有就当场提示输入
#   station : 环境变量 WCZL_STATION_PW  ，没有就当场提示输入
#
# 想省事就在自己的 PowerShell 配置里设这两个环境变量，
# 别写进仓库、别写进 .ps1。
#
# 另外要求：本机到服务器的 ssh 免密已配好（ed25519 公钥在服务器的
# administrators_authorized_keys 里）。脚本用 BatchMode，不会弹密码框，
# 免密不通会直接报「远端命令失败」。
#
# 这份文件必须存成 UTF-8 with BOM（头三字节 EF BB BF）。
# Windows PowerShell 5.1 读没 BOM 的中文 .ps1 会按 GBK 解码，
# 注释被拆开后吞掉换行、括号配对全乱，报一串 Unexpected token 且行号指向空行。
#
# =====================================================================

[CmdletBinding()]
param(
    [string]$Ip,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$Server    = '49.234.194.194'
$SshUser   = 'Administrator'
$MysqlExe  = 'C:\mysql\bin\mysql.exe'
$DbUser    = 'station'
$Databases = @('sdsstation')     # 要连 ERP 库就往这里加 'sdssys'

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok  ($msg) { Write-Host "    $msg" -ForegroundColor Green }
function Write-Bad ($msg) { Write-Host "    $msg" -ForegroundColor Red }

# ---------------------------------------------------------------------
# 本机公网 IP。强制走 IPv4 —— 这台机器有 IPv6，探测服务默认可能回 v6 地址，
# 而 MySQL 账号和安全组规则都是按 v4 配的，拿到 v6 会白忙一场
# ---------------------------------------------------------------------
function Get-PublicIp {
    foreach ($u in @('https://ifconfig.me', 'https://ipinfo.io/ip', 'https://api.ipify.org')) {
        try {
            $v = (curl.exe -4 -s --max-time 10 $u)
            if ($v -match '^\s*(\d{1,3}(\.\d{1,3}){3})\s*$') { return $Matches[1] }
        } catch { }
    }
    throw '探测不到本机公网 IPv4，用 -Ip 手工指定'
}

# ---------------------------------------------------------------------
# 读 3306 握手包：Unreachable / NotAllowed / Ok
# ---------------------------------------------------------------------
function Test-Mysql3306 {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($Server, 3306)
        if (-not $task.Wait(8000)) { return 'Unreachable' }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $buf = New-Object byte[] 512
        $n   = $stream.Read($buf, 0, 512)
        $txt = [Text.Encoding]::ASCII.GetString($buf, 0, $n)
        if ($txt -match 'is not allowed to connect') { return 'NotAllowed' }
        return 'Ok'
    } catch {
        return 'Unreachable'
    } finally {
        $client.Close()
    }
}

# ---------------------------------------------------------------------
# 在服务器上跑一段 PowerShell。
# 用 -EncodedCommand 是因为 本地shell -> ssh -> 远端PowerShell -> mysql -e "..."
# 四层嵌套引号，手写转义几乎必错（SQL 里全是单引号）。base64 之后一个引号都不剩
# ---------------------------------------------------------------------
function Invoke-RemotePs([string]$PsCommand) {
    $b64 = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($PsCommand))
    $out = ssh -o BatchMode=yes -o ConnectTimeout=15 "$SshUser@$Server" "powershell -NoProfile -EncodedCommand $b64"
    if ($LASTEXITCODE -ne 0) {
        throw "远端命令失败（退出码 $LASTEXITCODE）。上面的 stderr 是原因；免密登录不通也会走到这里。"
    }
    return $out
}

function Invoke-RemoteSql([string]$Sql, [string]$RootPw) {
    $sqlB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Sql))
    $pwB64  = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($RootPw))
    $remote = @"
`$sql = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$sqlB64'))
`$pw  = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$pwB64'))
& '$MysqlExe' '-uroot' "-p`$pw" '-N' '-B' '-e' `$sql
exit `$LASTEXITCODE
"@
    return Invoke-RemotePs $remote
}

function Read-Secret([string]$EnvName, [string]$Prompt) {
    $v = [Environment]::GetEnvironmentVariable($EnvName)
    if ($v) { return $v }
    $sec  = Read-Host -Prompt "$Prompt（也可预先设环境变量 $EnvName）" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try     { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# =====================================================================

if (-not $Ip) {
    Write-Step '探测本机公网 IP'
    $Ip = Get-PublicIp
}
if ($Ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw "不是合法的 IPv4：$Ip" }
Write-Ok "当前 IP = $Ip"

Write-Step "探测 ${Server}:3306（第一道门：安全组）"
$state = Test-Mysql3306
switch ($state) {
    'Unreachable' {
        Write-Bad 'TCP 连不上 —— 卡在安全组这一层，脚本到此为止。'
        Write-Host ''
        Write-Host '  去腾讯云控制台 -> 轻量/CVM -> 防火墙(安全组) -> 找到 3306 那条规则，' -ForegroundColor Yellow
        Write-Host "  把来源改成 $Ip/32（不要改成 0.0.0.0/0，root 是弱口令）。" -ForegroundColor Yellow
        Write-Host '  改完再跑一次这个脚本，它会接着做第二步授权。' -ForegroundColor Yellow
        exit 1
    }
    'NotAllowed' { Write-Ok '安全组已放行；MySQL 还不认这个来源 IP（预期内，下一步就是补它）' }
    'Ok'         { Write-Ok '安全组已放行，且 MySQL 已认这个 IP（可能上次跑过了，继续核对授权）' }
}

$rootPw = Read-Secret 'WCZL_DB_ROOT_PW' '服务器 MySQL root 密码'

Write-Step "查现有的 $DbUser@... 账号"
$existing = (Invoke-RemoteSql "SELECT host FROM mysql.user WHERE user='$DbUser';" $rootPw) |
            ForEach-Object { $_.Trim() } | Where-Object { $_ }
if ($existing) { Write-Ok ('已有：' + ($existing -join ', ')) } else { Write-Ok '一个都没有' }

# localhost 那个是服务器上应用自己用的，动不得；其余非当前 IP 的都是过期的旧 IP
$stale = $existing | Where-Object { $_ -ne 'localhost' -and $_ -ne $Ip }

$stationPw = Read-Secret 'WCZL_STATION_PW' 'station 账号密码（要和 Navicat 里填的一致）'

$stmts = @()
foreach ($h in $stale) { $stmts += "DROP USER IF EXISTS '$DbUser'@'$h';" }
$stmts += "DROP USER IF EXISTS '$DbUser'@'$Ip';"      # 先删再建，免得旧密码残留
$stmts += "CREATE USER '$DbUser'@'$Ip' IDENTIFIED BY '$stationPw';"
foreach ($db in $Databases) { $stmts += "GRANT ALL ON $db.* TO '$DbUser'@'$Ip';" }
$stmts += 'FLUSH PRIVILEGES;'

Write-Step '将要执行'
$stmts | ForEach-Object { Write-Host ('    ' + ($_ -replace "IDENTIFIED BY '.*'", "IDENTIFIED BY '***'")) }

if ($DryRun) { Write-Host ''; Write-Ok '-DryRun，没有真的执行'; exit 0 }

Write-Step '执行'
Invoke-RemoteSql ($stmts -join ' ') $rootPw | Out-Null
Write-Ok '已执行（DDL 成功时 mysql 不输出任何东西，那条 password on the command line 警告是正常的）'

# ---------------------------------------------------------------------
# 回读验证。「命令没报错」不等于能用 —— CREATE 成功但 GRANT 漏掉时，
# 前面每一步都是安静的，只有这里能看出来
# ---------------------------------------------------------------------
Write-Step '验证'

$state = Test-Mysql3306
if ($state -eq 'Ok') { Write-Ok '握手包回版本串 -> host 白名单通了' }
else { Write-Bad "握手包仍然是 $state —— 不该发生，把上面的输出发出来看看"; exit 1 }

$grants = Invoke-RemoteSql "SHOW GRANTS FOR '$DbUser'@'$Ip';" $rootPw
$grants | ForEach-Object { Write-Host "    $_" }

$missing = $Databases | Where-Object { $db = $_; -not ($grants -match ('`' + $db + '`')) }
if ($missing) {
    Write-Bad ('这些库没授上：' + ($missing -join ', ') + ' —— 连上去会只看到 information_schema')
    exit 1
}
Write-Ok '授权齐了'

Write-Host ''
Write-Host "  主机  ${Server}:3306"
Write-Host "  用户  $DbUser"
Write-Host ('  库    ' + ($Databases -join ', '))
Write-Host '  Navicat 的 SSH 标签页不要勾（这是直连，不是隧道）' -ForegroundColor Yellow

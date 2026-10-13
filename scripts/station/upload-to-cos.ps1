<#
.SYNOPSIS
    把本机磁盘上的历史文件按原相对路径搬进腾讯云 COS。

.DESCRIPTION
    独立站从「本机磁盘」切到「腾讯云 COS」之后，新传的文件会直接进桶，
    但切换之前传的还躺在服务器的上传目录里。这个脚本把它们搬过去。

    对象键 = 文件相对上传目录的路径，反斜杠换成正斜杠。也就是说
        C:\wczlerp\station-upload\image\2026\09\04\SIMG0000000005.png
    会被传成
        image/2026/09/04/SIMG0000000005.png
    这和 SiteFileService.store() 拼出来的键完全一致——必须一致，
    因为紧接着要跑的那个 SQL 是用 objectname 重建地址的。

    ⚠ 顺序：先跑这个脚本，确认文件能取到，再跑
      sql/station/migration/2026-09-06-file-url-rebase.sql。
      反过来的话中间那段时间整站都是裂图。

.PARAMETER Root
    上传目录。服务器上是 C:\wczlerp\station-upload。

.PARAMETER Bucket
    完整桶名，带 APPID，形如 kangchen-1479674498。

.PARAMETER Region
    地域英文代号，如 ap-shanghai。

.PARAMETER SecretId
    腾讯云 SecretId。

.PARAMETER SecretKey
    腾讯云 SecretKey。不传的话读环境变量 COS_SECRET_KEY。
    ⚠ 直接写在命令行上会进 PowerShell 历史（C:\Users\<你>\AppData\Roaming\
      Microsoft\Windows\PowerShell\PSReadline\ConsoleHost_history.txt）。
      推荐先 $env:COS_SECRET_KEY = '...' 再跑，跑完 Remove-Item Env:\COS_SECRET_KEY。

.PARAMETER PublicBase
    对外访问前缀，如 https://file.kangchen.vclinker.com（不带结尾斜杠）。
    用来跳过「已经在桶里」的文件，也用来在跑完之后抽查。

.PARAMETER Internal
    走腾讯云内网上传。**在那台 CVM 上跑就该带上它**：
    机器公网只有 3 Mbps，几个视频就能传一下午；内网不限速也不计流量费。
    ⚠ 只在 CVM 和桶同地域时有效，跨地域会连不上（表现是超时）。

.PARAMETER DryRun
    只列出打算传什么，不真传。第一次跑建议先带上它看一眼。

.EXAMPLE
    $env:COS_SECRET_KEY = '...'
    .\upload-to-cos.ps1 -Root C:\wczlerp\station-upload -Bucket kangchen-1479674498 `
        -Region ap-shanghai -SecretId AKID... -PublicBase https://file.kangchen.vclinker.com `
        -Internal -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $Root,
    [Parameter(Mandatory = $true)] [string] $Bucket,
    [Parameter(Mandatory = $true)] [string] $Region,
    [Parameter(Mandatory = $true)] [string] $SecretId,
    [string] $SecretKey = $env:COS_SECRET_KEY,
    [Parameter(Mandatory = $true)] [string] $PublicBase,
    [switch] $Internal,
    [switch] $DryRun
)

#
# ⚠ 这个文件必须存成 **UTF-8 with BOM**。
#
# Windows PowerShell 5.1（服务器上就是它，PowerShell 7 才改了默认值）在读 .ps1 时，
# 没有 BOM 就按系统 ANSI 代码页解码——中文机器上是 GBK。里面的中文注释被按 GBK
# 拆开之后，某些字节对会把紧跟其后的换行一起吞掉，于是括号配对全乱，
# 报出来的是一串 "Unexpected token '}'"，行号还指向空行。
#
# 那个错误极难自查：文件本身没问题，用 VS Code 打开、用 git diff 看都正常，
# 只有在那台机器上跑才炸。改完这个文件记得确认 BOM 还在（前三字节 EF BB BF）。
#

$ErrorActionPreference = 'Stop'

# PS 5.1 的 Invoke-WebRequest 每传一个块就刷一次进度条，传大文件时这件事本身
# 比传输还慢（实测能差一个数量级）。关掉。
$ProgressPreference = 'SilentlyContinue'

# Server 2016 上 .NET 默认还在用 TLS 1.0，而 COS 只收 1.2 以上。
# 不设这一行的表现是「基础连接已关闭: 发送时发生意外错误」——一个完全看不出
# 是握手失败的报错。
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

if (-not $SecretKey) {
    throw 'SecretKey 没给。用 -SecretKey，或者先设 $env:COS_SECRET_KEY'
}
if (-not (Test-Path -LiteralPath $Root)) {
    throw "上传目录不存在：$Root"
}
$PublicBase = $PublicBase.TrimEnd('/')

$cosHost = if ($Internal) {
    "$Bucket.cos-internal.$Region.myqcloud.com"
} else {
    "$Bucket.cos.$Region.myqcloud.com"
}

# ---------------------------------------------------------------------
# 签名。和 station-console 的 CosClient.authorization() 一字不差地对应——
# 两边算出来的必须是同一个串，改了一边记得改另一边。
# ---------------------------------------------------------------------

function Get-HmacSha1Hex([string] $Data, [string] $Key) {
    $mac = New-Object System.Security.Cryptography.HMACSHA1
    try {
        $mac.Key = [Text.Encoding]::UTF8.GetBytes($Key)
        ($mac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Data)) |
            ForEach-Object { $_.ToString('x2') }) -join ''
    } finally { $mac.Dispose() }
}

function Get-Sha1Hex([string] $Data) {
    $sha = [Security.Cryptography.SHA1]::Create()
    try {
        ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Data)) |
            ForEach-Object { $_.ToString('x2') }) -join ''
    } finally { $sha.Dispose() }
}

# RFC3986。.NET 4.5+ 的 EscapeDataString 就是这个语义（保留 A-Za-z0-9-._~，其余全转义），
# 和 Java 那边 URLEncoder + 三次 replace 的结果一致。
function ConvertTo-Rfc3986([string] $Value) {
    [Uri]::EscapeDataString($Value)
}

# 对象键按 / 分段各自编码。整串编码会把分隔用的 / 也转成 %2F，
# 那样传上去会得到一个名字里带 %2F 的对象，而不是一层层的目录
function ConvertTo-EncodedPath([string] $Key) {
    (($Key -split '/', -1) | ForEach-Object { ConvertTo-Rfc3986 $_ }) -join '/'
}

function New-CosAuthorization([string] $Method, [string] $Path) {
    $now = [int][double]::Parse((Get-Date -UFormat %s))
    $keyTime = "$($now - 60);$($now + 600)"
    $signKey = Get-HmacSha1Hex -Data $keyTime -Key $SecretKey

    $headerString = 'host=' + (ConvertTo-Rfc3986 $cosHost)
    # ⚠ 查询串留空，但那两个换行必须在。少一个换行整个签名就不对，
    #   而 COS 只会回一个 SignatureDoesNotMatch，看不出少的是什么
    $httpString = "$Method`n$Path`n`n$headerString`n"
    $stringToSign = "sha1`n$keyTime`n$(Get-Sha1Hex $httpString)`n"
    $signature = Get-HmacSha1Hex -Data $stringToSign -Key $signKey

    "q-sign-algorithm=sha1&q-ak=$SecretId&q-sign-time=$keyTime&q-key-time=$keyTime" +
        "&q-header-list=host&q-url-param-list=&q-signature=$signature"
}

# ---------------------------------------------------------------------

$MIME = @{
    '.jpg' = 'image/jpeg'; '.jpeg' = 'image/jpeg'; '.png' = 'image/png'
    '.gif' = 'image/gif';  '.webp' = 'image/webp'; '.bmp' = 'image/bmp'
    '.mp4' = 'video/mp4';  '.webm' = 'video/webm'; '.mov' = 'video/quicktime'
    '.m4v' = 'video/x-m4v'
}

function Get-ContentType([string] $Extension) {
    $key = $Extension.ToLowerInvariant()
    if ($MIME.ContainsKey($key)) { $MIME[$key] } else { 'application/octet-stream' }
}

# 已经在桶里就跳过。用**匿名** GET 打自定义域名，不签名——
# 这一步顺带证明了「传上去的东西访客真能取到」，比签名 HEAD 有用。
function Test-AlreadyUploaded([string] $Key) {
    try {
        $response = Invoke-WebRequest -Uri "$PublicBase/$(ConvertTo-EncodedPath $Key)" `
            -Method Head -UseBasicParsing -TimeoutSec 15
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
    } catch {
        return $false
    }
}

$rootFull = (Resolve-Path -LiteralPath $Root).Path.TrimEnd('\')
$files = Get-ChildItem -LiteralPath $rootFull -File -Recurse

Write-Host "上传目录：$rootFull"
Write-Host "目标桶　：$cosHost$(if ($Internal) { '（内网）' } else { '（公网）' })"
Write-Host "公开前缀：$PublicBase"
Write-Host "文件总数：$($files.Count)"
Write-Host ''

$uploaded = 0; $skipped = 0; $failed = 0
$failures = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
    $key = $file.FullName.Substring($rootFull.Length + 1).Replace('\', '/')

    if ($DryRun) {
        Write-Host "[dry-run] $key  ($([math]::Round($file.Length / 1KB, 1)) KB)"
        $uploaded++
        continue
    }

    if (Test-AlreadyUploaded $key) {
        Write-Host "[跳过　] $key"
        $skipped++
        continue
    }

    $path = '/' + (ConvertTo-EncodedPath $key)
    try {
        Invoke-WebRequest -Uri "https://$cosHost$path" -Method Put `
            -InFile $file.FullName `
            -ContentType (Get-ContentType $file.Extension) `
            -Headers @{ Authorization = (New-CosAuthorization 'put' $path) } `
            -UseBasicParsing -TimeoutSec 600 | Out-Null
        Write-Host "[已传　] $key"
        $uploaded++
    } catch {
        # COS 的错误体在响应里，异常消息只有一个 HTTP 状态码——把 body 掏出来，
        # 否则 SignatureDoesNotMatch / NoSuchBucket / AccessDenied 三种完全不同的
        # 处置在日志里长得一模一样
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try {
                $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $detail = $reader.ReadToEnd()
                $reader.Close()
            } catch { }
        }
        Write-Warning "[失败　] $key -> $detail"
        $failures.Add($key)
        $failed++
    }
}

Write-Host ''
Write-Host "完成：已传 $uploaded / 跳过 $skipped / 失败 $failed"

if ($failed -gt 0) {
    Write-Host ''
    Write-Warning '有文件没传上去。这个脚本可以直接重跑——传成功的会在「跳过」那一支被略过。'
    $failures | ForEach-Object { Write-Host "  $_" }
    # ⚠ 非零退出码：别让调用方以为搬完了就去跑那个 SQL。
    #   漏掉的文件在 SQL 跑完之后就是死链
    exit 1
}

if (-not $DryRun) {
    Write-Host ''
    Write-Host '下一步：抽查几个地址真的能打开，然后再跑'
    Write-Host '  sql/station/migration/2026-09-06-file-url-rebase.sql'
    $sample = $files | Select-Object -First 3
    foreach ($file in $sample) {
        $key = $file.FullName.Substring($rootFull.Length + 1).Replace('\', '/')
        Write-Host "  curl -I $PublicBase/$key"
    }
}

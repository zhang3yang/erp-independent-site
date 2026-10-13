# Step 7: give the ERP frontend vhost (console.erp.vclinker.com) gzip + gzip_static,
# long-lived cache headers for /static/, a revalidating index.html, and http2.
#
# Pure ASCII on purpose: this file gets pasted into an RDP console and/or copied into an
# RDP session, and Windows PowerShell 5.1 decodes non-BOM files as GBK.
#
# Why a script and not "edit these four places by hand":
#   * the live nginx.conf is the only copy,
#   * console.erp appears TWICE (a port-80 redirect block and the port-443 TLS block) and
#     data.erp has an identical `listen 443 ssl;` line, so "the first match" is wrong and
#     "the 443 one" is not obvious by eye,
#   * a mis-edited production nginx.conf takes the ERP down.
# So: anchor on unique strings, refuse to write anything if an anchor is missing, print
# every region it touched, then run nginx -t and restore the backup automatically if the
# test fails. Re-running it is a no-op (it detects its own work).
#
# Usage (on the server, in an ELEVATED PowerShell):
#   powershell -ExecutionPolicy Bypass -File C:\build\step7-erp-nginx-gzip.ps1
#
# This script never reloads nginx. `nginx -t` is the gate; reload is a human decision:
#   C:\nginx\nginx.exe -s reload
param(
    [string]$ConfigPath = 'C:\nginx\conf\nginx.conf',
    [string]$NginxExe   = 'C:\nginx\nginx.exe'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $ConfigPath)) { throw "missing config: $ConfigPath" }

# ---------------------------------------------------------------- backup
$stamp  = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = "$ConfigPath.bak-$stamp"
Copy-Item $ConfigPath $backup
Write-Host "backup: $backup"

# ---------------------------------------------------------------- read (encoding safe)
# The live file has non-ASCII comments. Read the bytes, pick the encoding, and write the
# content back with the SAME encoding and the SAME BOM presence, so everything except the
# lines this script touches survives byte for byte.
$bytes = [IO.File]::ReadAllBytes($ConfigPath)
$hadBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
try   { $enc = New-Object Text.UTF8Encoding($false, $true); $null = $enc.GetString($bytes) }
catch { $enc = [Text.Encoding]::GetEncoding(936) }
$text = $enc.GetString($bytes)
if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }
$nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
$lines = New-Object System.Collections.Generic.List[string]
foreach ($l in ($text -split "`r`n|`n")) { $lines.Add($l) }
Write-Host ("config: {0} lines, encoding {1}{2}, eol {3}" -f $lines.Count, $enc.WebName, $(if ($hadBom) { ' +BOM' } else { '' }), $(if ($nl -eq "`r`n") { 'CRLF' } else { 'LF' }))

function Find-Line([string]$pattern) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match $pattern) { return $i } }
    return -1
}
function Write-Back() {
    # Always write BOM-less bytes; put the original BOM back explicitly when it was there.
    $body = [string]::Join($nl, $lines)
    if ($hadBom) { $body = [string][char]0xFEFF + $body }
    [IO.File]::WriteAllText($ConfigPath, $body, (New-Object Text.UTF8Encoding($false)))
}

# Locate the TLS (443) block of console.erp. console.erp shows up twice in the file (port
# 80 redirect + port 443), and data.erp's 443 block looks the same from the listen line's
# point of view. So: walk every `server_name console.erp...` and keep the one whose
# preceding lines contain `listen 443 ssl` (the listen line sits directly above it).
$srvIdx = -1
$lisIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch '^\s*server_name\s+console\.erp\.vclinker\.com\s*;') { continue }
    for ($j = $i; $j -ge [Math]::Max(0, $i - 6); $j--) {
        if ($lines[$j] -match '^\s*listen\s+443\s+ssl(\s+http2)?\s*;') { $srvIdx = $i; $lisIdx = $j; break }
    }
    if ($srvIdx -ge 0) { break }
}

# ---------------------------------------------------------------- anchors (fail before writing)
$iGzip = Find-Line '^\s*#?\s*gzip\s+on\s*;'
$iRoot = Find-Line '^\s*root\s+C:/wczlerp/web/dist\s*;'

$missing = @()
if ($iGzip  -lt 0) { $missing += '"#gzip on;" in http{}' }
if ($srvIdx -lt 0) { $missing += 'the console.erp :443 server block' }
if ($iRoot  -lt 0) { $missing += '"root C:/wczlerp/web/dist;"' }
if ($missing.Count -gt 0) {
    throw ("ANCHOR NOT FOUND: " + ($missing -join ' | ') + " -- nothing was changed. Send this line back, do not hand-edit.")
}
Write-Host ("located: gzip line {0}, console.erp :443 server_name line {1}, listen line {2}" -f ($iGzip + 1), ($srvIdx + 1), ($lisIdx + 1))

# ---------------------------------------------------------------- 1) gzip block in http{}
if ((Find-Line '^\s*gzip_static\s+on\s*;') -ge 0) {
    Write-Host '1) gzip block already present - skip'
} else {
    $block = @(
        '    gzip              on;',
        '    gzip_static       on;',
        '    gzip_vary         on;',
        '    gzip_comp_level   6;',
        '    gzip_min_length   1k;',
        '    gzip_proxied      any;',
        '    gzip_types        text/plain text/css text/xml text/javascript',
        '                      application/javascript application/json application/xml image/svg+xml;'
    )
    $lines.RemoveAt($iGzip)
    $lines.InsertRange($iGzip, [string[]]$block)
    Write-Back
    Write-Host '1) gzip + gzip_static inserted into http{}'
}

# ---------------------------------------------------------------- 2) http2 on the console.erp 443 listener
# Re-locate: step 1 shifted every line below it.
$srvIdx = -1; $lisIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch '^\s*server_name\s+console\.erp\.vclinker\.com\s*;') { continue }
    for ($j = $i; $j -ge [Math]::Max(0, $i - 6); $j--) {
        if ($lines[$j] -match '^\s*listen\s+443\s+ssl(\s+http2)?\s*;') { $srvIdx = $i; $lisIdx = $j; break }
    }
    if ($srvIdx -ge 0) { break }
}
if ($srvIdx -lt 0) { throw 'CANNOT LOCATE the console.erp :443 block after step 1 -- nothing more changed. Send this line back.' }

if ($lines[$lisIdx] -match 'http2') {
    Write-Host '2) http2 already present - skip'
} else {
    $lines[$lisIdx] = ($lines[$lisIdx] -replace '^(\s*listen\s+443\s+ssl)\s*;', '$1 http2;')
    Write-Back
    Write-Host ('2) http2 added -> ' + $lines[$lisIdx].Trim())
}

# ---------------------------------------------------------------- 3) cache headers
if ((Find-Line '^\s*location\s+/static/\s*\{') -ge 0) {
    Write-Host '3) cache locations already present - skip'
} else {
    $iRoot  = Find-Line '^\s*root\s+C:/wczlerp/web/dist\s*;'
    $iIndex = -1
    for ($i = $iRoot; $i -le [Math]::Min($lines.Count - 1, $iRoot + 4); $i++) {
        if ($lines[$i] -match '^\s*index\s+index\.html\s*;') { $iIndex = $i; break }
    }
    if ($iIndex -lt 0) { throw 'CANNOT LOCATE "index index.html;" under the console.erp root -- nothing more changed.' }
    $loc = @(
        '',
        '        # index.html must be revalidated: it names the hashed assets, and a cached',
        '        # copy after a deploy points at chunks that no longer exist.',
        '        location = /index.html {',
        '            add_header Cache-Control "no-cache";',
        '        }',
        '',
        '        # Build output: the filename contains a content hash, so it can be cached hard.',
        '        location /static/ {',
        '            expires 1y;',
        '            add_header Cache-Control "public, immutable";',
        '            access_log off;',
        '        }',
        '',
        '        location = /favicon.ico {',
        '            expires 30d;',
        '            access_log off;',
        '        }'
    )
    $lines.InsertRange($iIndex + 1, [string[]]$loc)
    Write-Back
    Write-Host '3) cache locations inserted'
}

# ---------------------------------------------------------------- show what changed
Write-Host ''
Write-Host '=== gzip directives now in the config ==='
foreach ($l in $lines) { if ($l -match '^\s*gzip') { Write-Host ('  ' + $l) } }
Write-Host ''
Write-Host '=== console.erp :443 server block ==='
$srvIdx = -1; $lisIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch '^\s*server_name\s+console\.erp\.vclinker\.com\s*;') { continue }
    for ($j = $i; $j -ge [Math]::Max(0, $i - 6); $j--) {
        if ($lines[$j] -match '^\s*listen\s+443\s+ssl(\s+http2)?\s*;') { $srvIdx = $i; $lisIdx = $j; break }
    }
    if ($srvIdx -ge 0) { break }
}
for ($i = [Math]::Max(0, $lisIdx - 2); $i -le [Math]::Min($lines.Count - 1, $srvIdx + 34); $i++) { Write-Host ('  ' + $lines[$i]) }

# ---------------------------------------------------------------- gate: nginx -t
Write-Host ''
if (-not (Test-Path $NginxExe)) {
    Write-Host "nginx.exe not found at $NginxExe - skipped -t (dry run?)"
    return
}

# This Windows nginx build resolves its prefix from the CURRENT DIRECTORY. Run it from
# anywhere else and it looks for <cwd>/conf/nginx.conf and <cwd>/logs, which fails with:
#   CreateFile() "C:\Users\Administrator/conf/nginx.conf" failed (3: ...)
# so every invocation - test and reload alike - has to happen with C:\nginx as the cwd.
$nginxDir = Split-Path -Parent $NginxExe
function Invoke-NginxTest {
    Push-Location $nginxDir
    try {
        $ErrorActionPreference = 'Continue'
        $o = (& $NginxExe -t 2>&1 | Out-String)
        return @{ out = $o; code = $LASTEXITCODE }
    } finally { Pop-Location }
}

$r = Invoke-NginxTest
Write-Host $r.out.Trim()

# The one failure worth self-healing: this nginx build has no gzip_static module.
# Drop just that line and test again, instead of leaving a broken config on disk.
if ($r.code -ne 0 -and $r.out -match 'unknown directive\s+"?gzip_static') {
    Write-Host 'gzip_static module missing in this build - removing that one line and retrying'
    $i = Find-Line '^\s*gzip_static\s+on\s*;'
    $lines.RemoveAt($i)
    $lines.Insert($i, '    # gzip_static on;   # this nginx build has no ngx_http_gzip_static_module')
    Write-Back
    $r = Invoke-NginxTest
    Write-Host $r.out.Trim()
}

if ($r.code -ne 0) {
    Write-Host ''
    Write-Host "RESULT: NGINX -t FAILED - restoring $backup"
    Copy-Item $backup $ConfigPath -Force
    (Invoke-NginxTest).out.Trim() | Write-Host
    throw 'config restored to the backup; the file on disk is unchanged. Send the error above back.'
}

Write-Host ''
Write-Host 'RESULT: CONFIG OK'
Write-Host 'Now reload (note the Set-Location: nginx resolves conf/ and logs/ from the cwd):'
Write-Host '  Set-Location C:\nginx'
Write-Host '  .\nginx.exe -s reload'

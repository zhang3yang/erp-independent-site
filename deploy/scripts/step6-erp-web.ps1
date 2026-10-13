# Step 6: swap the ERP frontend (console.erp.vclinker.com) static files with a freshly uploaded build.
# ASCII only (Windows PowerShell 5.1 on this box decodes non-BOM files as GBK).
#
# The zip carries the CONTENTS of RuoYi-Vue3/dist at its top level, because that is how
# the live directory is laid out:
#   C:\wczlerp\web\dist\{index.html, favicon.ico, static\}
#
# Why move instead of overwrite: vite chunk names are content-hashed, so an overwrite
# leaves every past build's assets lying around forever, and you can never tell which
# files are live. Moving the old tree aside also makes the rollback a single Move-Item.
#
# Usage (on the server, after uploading the zip to C:\build\web-dist.zip):
#   powershell -ExecutionPolicy Bypass -File C:\build\step6-erp-web.ps1
$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$live  = 'C:\wczlerp\web\dist'
$zip   = 'C:\build\web-dist.zip'
$host_ = 'console.erp.vclinker.com'

if (-not (Test-Path $zip)) { throw "missing $zip" }
Write-Host "zip: $((Get-Item $zip).Length) bytes"

if (Test-Path $live) {
  Write-Host "== move the old one aside (never overwrite: chunk names are fingerprinted) =="
  Move-Item $live "C:\build\web-dist-bak-$stamp"
}
New-Item -ItemType Directory -Path $live | Out-Null

Write-Host "== expand =="
Expand-Archive -Path $zip -DestinationPath $live -Force
if (-not (Test-Path (Join-Path $live 'index.html'))) { throw 'expanded tree has no index.html' }
$files = (Get-ChildItem $live -Recurse -File).Count
Write-Host "new files: $files"

# A half-expanded tree looks fine on disk (index.html is there) but the browser gets
# 404s for the entry chunk. Read index.html, and require every /static/ asset it
# references to exist on disk.
Write-Host "== cross-check index.html against the tree =="
$html = Get-Content (Join-Path $live 'index.html') -Raw
$refs = [regex]::Matches($html, '(?:src|href)="(/static/[^"]+\.(?:js|css))"') |
        ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
if ($refs.Count -eq 0) { throw 'index.html references no /static/ assets - wrong build?' }
foreach ($r in $refs) {
  $p = Join-Path $live ($r.TrimStart('/') -replace '/', '\')
  if (-not (Test-Path $p)) { throw "index.html references a missing file: $r" }
  $gz = $p + '.gz'
  Write-Host ("  {0,-45} {1,10} bytes  {2}" -f $r, (Get-Item $p).Length, $(if (Test-Path $gz) { '(+ .gz ' + (Get-Item $gz).Length + ')' } else { '(no .gz)' }))
}

# gzip_static serves the .gz files that sit next to the originals. A build made without
# VITE_BUILD_COMPRESS=gzip has none, and the site still works but is 3x slower over the
# 1 Mbps uplink. Warn loudly rather than fail: gzip on still compresses on the fly.
$gzCount = (Get-ChildItem $live -Recurse -File -Filter *.gz).Count
Write-Host "pre-compressed .gz files in the tree: $gzCount"
if ($gzCount -eq 0) { Write-Host 'WARNING: no .gz files at all - this build was not compressed (check VITE_BUILD_COMPRESS=gzip)' }

# nginx serves these straight off disk - no service to restart. Probe through nginx
# with the right Host header so a bad root path shows up here, not in someone's browser.
$ErrorActionPreference = 'Continue'
$code = 0
try {
  $r = Invoke-WebRequest 'http://127.0.0.1/' -UseBasicParsing -TimeoutSec 10 -Headers @{ Host = $host_ }
  $code = [int]$r.StatusCode
} catch {
  if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
}
Write-Host "probe console.erp via nginx -> HTTP $code"
Write-Host "backup kept at C:\build\web-dist-bak-$stamp"
if ($code -ne 200) { Write-Host 'RESULT: ERP WEB PROBE NOT 200 - check nginx root path'; exit 1 }
Write-Host 'RESULT: ERP WEB OK'
Write-Host ''
Write-Host 'Next: from the dev machine run  node tools/check-console-erp-cache.mjs --local RuoYi-Vue3/dist'
Write-Host 'That is the only check that proves gzip + cache headers are actually being served.'

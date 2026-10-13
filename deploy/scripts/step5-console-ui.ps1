# Step 5: swap the station-console-ui static files with a freshly uploaded build.
# ASCII only (Windows PowerShell 5.1 on this box decodes non-BOM files as GBK).
#
# The zip carries the CONTENTS of dist (assets\ + index.html) at its top level,
# because that is how the live directory is laid out:
#   C:\wczlerp\station-console-ui\{assets, index.html}
#
# Why move instead of overwrite: vite chunk names are content-hashed, so an
# overwrite leaves every past build's assets lying around forever, and you can
# never tell which files are live.
$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$live  = 'C:\wczlerp\station-console-ui'
$zip   = 'C:\build\console-ui-dist.zip'

if (-not (Test-Path $zip)) { throw "missing $zip" }
Write-Host "zip: $((Get-Item $zip).Length) bytes"

if (Test-Path $live) {
  Write-Host "== move the old one aside (never overwrite: chunk names are fingerprinted) =="
  Move-Item $live "C:\build\console-ui-bak-$stamp"
}
New-Item -ItemType Directory -Path $live | Out-Null

Write-Host "== expand =="
Expand-Archive -Path $zip -DestinationPath $live -Force
if (-not (Test-Path (Join-Path $live 'index.html'))) { throw 'expanded tree has no index.html' }
$files = (Get-ChildItem $live -Recurse -File).Count
Write-Host "new files: $files"

# nginx serves these straight off disk - no service to restart. Probe through
# nginx anyway so a bad path shows up here rather than in someone's browser.
$ErrorActionPreference = 'Continue'
$code = 0
try {
  $r = Invoke-WebRequest 'http://127.0.0.1/' -UseBasicParsing -TimeoutSec 5 -Headers @{ Host = 'console.kangchen.vclinker.com' }
  $code = [int]$r.StatusCode
} catch {
  if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
}
Write-Host "probe console via nginx -> HTTP $code"
Write-Host "backup kept at C:\build\console-ui-bak-$stamp"
if ($code -ne 200) { Write-Host 'RESULT: CONSOLE PROBE NOT 200 - check nginx'; exit 1 }
Write-Host 'RESULT: CONSOLE UI OK'

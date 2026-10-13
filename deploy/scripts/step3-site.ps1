# Step 3: swap the station-site .output with the freshly uploaded build.
# ASCII only (Windows PowerShell 5.1 on this box decodes non-BOM files as GBK).
$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$root  = 'C:\wczlerp\station-site'
$live  = "$root\.output"
$zip   = 'C:\build\site-output.zip'

if (-not (Test-Path $zip)) { throw "missing $zip" }
Write-Host "zip: $((Get-Item $zip).Length) bytes"

Write-Host "== stop wczl-station-site =="
Stop-Service 'wczl-station-site' -Force
for ($i=0; $i -lt 60; $i++) {
  Start-Sleep -Milliseconds 500
  if ((Get-Service 'wczl-station-site').Status -eq 'Stopped') { break }
}
if ((Get-Service 'wczl-station-site').Status -ne 'Stopped') { throw 'service did not stop' }
Start-Sleep -Seconds 2

Write-Host "== move the old .output aside (never overwrite: chunk names are fingerprinted) =="
Move-Item $live "C:\build\site-output-bak-$stamp"

Write-Host "== expand the new one =="
# The zip carries a top-level ".output" folder, so expand into the site root.
Expand-Archive -Path $zip -DestinationPath $root -Force
if (-not (Test-Path "$live\server\index.mjs")) { throw 'expanded tree has no server/index.mjs' }
$files = (Get-ChildItem $live -Recurse -File).Count
Write-Host "new .output files: $files"

Write-Host "== start =="
Start-Service 'wczl-station-site'
for ($i=0; $i -lt 60; $i++) {
  Start-Sleep -Milliseconds 500
  if ((Get-Service 'wczl-station-site').Status -eq 'Running') { break }
}

$ErrorActionPreference = 'Continue'
$code = 0
for ($i=0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 1
  try { $r = Invoke-WebRequest 'http://127.0.0.1:3000/' -UseBasicParsing -TimeoutSec 5; $code=[int]$r.StatusCode }
  catch { if ($_.Exception.Response) { $code=[int]$_.Exception.Response.StatusCode } else { $code=0 } }
  if ($code -ne 0) { break }
}
Write-Host "probe 127.0.0.1:3000 -> HTTP $code"
if ($code -ne 200) { Write-Host 'RESULT: SITE NOT SERVING'; exit 1 }
Write-Host "backup kept at C:\build\site-output-bak-$stamp"
Write-Host 'RESULT: SITE OK'


$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Move-Item 'C:\build\station-console' "C:\build\station-console-srcbak-$stamp"
Expand-Archive -Path 'C:\build\console-src.zip' -DestinationPath 'C:\build' -Force
Write-Host "src files: $((Get-ChildItem 'C:\build\station-console\src' -Recurse -File).Count)"

$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\jdk17'
Set-Location 'C:\build\station-console'
& C:\maven\bin\mvn.cmd -B -o clean package -DskipTests | Select-String -Pattern 'BUILD SUCCESS|BUILD FAILURE|Compiling' | ForEach-Object { Write-Host $_.Line }
if ($LASTEXITCODE -ne 0) { Write-Host "RESULT: BUILD FAILED"; exit 1 }

# Start the freshly built jar on a spare port FIRST. Packaging succeeding does
# not mean the app starts: mapper XML is parsed at runtime, so a broken comment
# passes the build and then takes the service down. That is exactly what
# happened at 17:35 today.
Write-Host "=== smoke: start the new jar on 8099 before touching the live one ==="
$log = 'C:\build\smoke.log'
if (Test-Path $log) { Remove-Item $log -Force }
$p = Start-Process -FilePath 'C:\jdk17\bin\java.exe' `
  -ArgumentList '-Xms128m','-Xmx320m','-Dserver.port=8099','-jar','C:\build\station-console\target\station-console-1.0.0.jar' `
  -RedirectStandardOutput $log -RedirectStandardError 'C:\build\smoke.err.log' -PassThru -WindowStyle Hidden
$started = $false
for ($i = 0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 1
  if (Test-Path $log) {
    $t = Get-Content $log -Raw
    if ($t -match 'Started StationConsoleApplication') { $started = $true; break }
    if ($t -match 'Application run failed') { break }
  }
  if ($p.HasExited) { break }
}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
Start-Sleep -Seconds 2
if (-not $started) {
  Write-Host "RESULT: SMOKE FAILED - the new jar does not start. Live jar untouched."
  Get-Content $log -Tail 12 | ForEach-Object { Write-Host "  $_" }
  exit 1
}
Write-Host "smoke OK: new jar starts"

$ErrorActionPreference = 'Stop'
$live = 'C:\wczlerp\station-console\station-console-1.0.0.jar'
Copy-Item $live "$live.bak-$stamp"
Stop-Service 'wczl-station-console' -Force
for ($i=0; $i -lt 60; $i++) { Start-Sleep -Milliseconds 500; if ((Get-Service 'wczl-station-console').Status -eq 'Stopped') { break } }
Start-Sleep -Seconds 2
Copy-Item 'C:\build\station-console\target\station-console-1.0.0.jar' $live -Force
Write-Host "new jar in place: $((Get-Item $live).Length) bytes"
Start-Service 'wczl-station-console'
for ($i=0; $i -lt 60; $i++) { Start-Sleep -Milliseconds 500; if ((Get-Service 'wczl-station-console').Status -eq 'Running') { break } }
$ErrorActionPreference = 'Continue'
$code = 0
for ($i=0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 1
  try { $r = Invoke-WebRequest 'http://127.0.0.1:8081/api/site' -UseBasicParsing -TimeoutSec 5; $code=[int]$r.StatusCode }
  catch { if ($_.Exception.Response) { $code=[int]$_.Exception.Response.StatusCode } else { $code=0 } }
  if ($code -ne 0) { break }
}
Write-Host "probe /api/site -> HTTP $code (401 = up)"
if ($code -eq 0) { Write-Host "RESULT: NOT ANSWERING"; exit 1 }
Write-Host "RESULT: BACKEND OK"


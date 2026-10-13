# ERP release 2026-09-29: stocktake, cost adjust, sales profit, exchange rates, dashboard sales in CNY.
#
# ASCII only on purpose: Windows PowerShell 5.1 on the server decodes non-BOM files as GBK.
#
# Copy the whole release folder to the server (e.g. C:\build\erp-20260929\) and run:
#   powershell -ExecutionPolicy Bypass -File C:\build\erp-20260929\deploy-erp.ps1
#
# What it does, in order. Every step checks itself and stops on the first problem:
#   1. verify the package files against SHA256SUMS.txt
#   2. read-only check that the EARLIER migrations are already on this database
#   3. mysqldump sdssys + sdsshop + sdsorder to the backup folder
#   4. run the three new migrations (all re-runnable) and check what they created
#   5. start the NEW jar on a spare port first; only if it boots and answers,
#      stop the live one, swap the jar, start it again. If the live one does not
#      come back, the old jar is put back and started automatically.
#   6. swap the frontend (old tree is moved aside, never overwritten)
#
# Switches: -SkipDb -SkipBackend -SkipWeb to redo only part of it.
param(
  [string]$Dir = $PSScriptRoot,
  [string]$MysqlExe = '',
  [string]$DbUser = 'root',
  [string]$DbPassword = '',
  [string]$DbHost = '127.0.0.1',
  [int]$DbPort = 3306,
  [string]$BackupDir = 'C:\build\backup',
  [string]$LiveJar = '',
  [int]$LivePort = 0,
  [int]$SmokePort = 8099,
  [string]$WebRoot = 'C:\wczlerp\web\dist',
  [string]$WebHost = 'console.erp.vclinker.com',
  [string]$WebProbeUrl = 'http://127.0.0.1/',
  [switch]$SkipDb,
  [switch]$SkipBackend,
  [switch]$SkipWeb
)
$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = Join-Path $Dir "deploy-$stamp.log"
Start-Transcript -Path $log | Out-Null

function Step($t) { Write-Host ''; Write-Host "==== $t ====" -ForegroundColor Cyan }
function Ok($t) { Write-Host "  OK  $t" -ForegroundColor Green }
function Fail($t) { Write-Host "  FAIL  $t" -ForegroundColor Red; Stop-Transcript | Out-Null; throw $t }

function Wait-Http($url, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    try {
      $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
      if ($r.StatusCode -eq 200) { return $true }
    } catch { }
    Start-Sleep -Seconds 1
  }
  return $false
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$newJar = Join-Path $Dir 'ruoyi-admin.jar'
$webZip = Join-Path $Dir 'web-dist.zip'
$migrations = @('20260925_stocktake.sql', '20260928_cost_adjust.sql', '20260929_sales_profit.sql')

# ---------------------------------------------------------------- 1
Step '1. verify package files'
$sums = Join-Path $Dir 'SHA256SUMS.txt'
if (-not (Test-Path $sums)) { Fail "missing $sums" }
foreach ($line in Get-Content $sums) {
  if ($line.Trim() -eq '') { continue }
  $parts = $line -split '\s+', 2
  $file = Join-Path $Dir ($parts[1].Trim().TrimStart('*'))
  if (-not (Test-Path $file)) { Fail "missing file $file" }
  $actual = (Get-FileHash -Algorithm SHA256 $file).Hash.ToLower()
  if ($actual -ne $parts[0].ToLower()) { Fail "checksum mismatch: $file (copy it again)" }
  Ok (Split-Path $file -Leaf)
}

# ---------------------------------------------------------------- DB tools
if (-not $SkipDb) {
  if ($MysqlExe -eq '') {
    $found = Get-ChildItem 'C:\Program Files\MySQL' -Recurse -Filter mysql.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $MysqlExe = $found.FullName }
  }
  if ($MysqlExe -eq '' -or -not (Test-Path $MysqlExe)) { Fail 'mysql.exe not found, pass -MysqlExe' }
  $MysqlDump = Join-Path (Split-Path $MysqlExe) 'mysqldump.exe'
  if (-not (Test-Path $MysqlDump)) { Fail "mysqldump.exe not found next to $MysqlExe" }
  Ok "mysql: $MysqlExe"
  if ($DbPassword -eq '') {
    $sec = Read-Host "MySQL password for $DbUser@$DbHost" -AsSecureString
    $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
  }
  $env:MYSQL_PWD = $DbPassword

  function Sql($query) {
    $out = & $MysqlExe "-u$DbUser" "-h$DbHost" "-P$DbPort" -N -B --default-character-set=utf8mb4 -e $query 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "mysql error: $out" }
    return ($out | Out-String).Trim()
  }
  function SqlFile($path) {
    $p = Start-Process -FilePath $MysqlExe -ArgumentList "-u$DbUser", "-h$DbHost", "-P$DbPort", '--default-character-set=utf8mb4' `
      -RedirectStandardInput $path -RedirectStandardOutput "$path.out" -RedirectStandardError "$path.err" -NoNewWindow -Wait -PassThru
    $err = (Get-Content "$path.err" -Raw -ErrorAction SilentlyContinue)
    if ($err) { $err = ($err -split "`n" | Where-Object { $_ -notmatch 'Using a password' }) -join "`n" }
    if ($p.ExitCode -ne 0 -or ($err -and $err.Trim() -ne '')) { Fail "migration failed: $path`n$err" }
  }

  # -------------------------------------------------------------- 2
  Step '2. earlier migrations already on this database? (read only)'
  $cur = Get-Date -Format 'yyyyMM'
  $checks = [ordered]@{
    '20260910_price_visibility (menu fn9041)'          = "select count(*) from sdssys.sys_menu where fun_code='fn9041'"
    '20260911_dashboard_menu (menu fn9051)'            = "select count(*) from sdssys.sys_menu where fun_code='fn9051'"
    '20260911_shipbatch_warehouse_permi (fn5103 perms)' = "select count(*) from sdssys.sys_menu where fun_code='fn5103' and perms='order:shipbatch:warehouse'"
    '20260911_shipbatch_reject (column rejectreason)'  = "select count(*) from information_schema.columns where table_schema='sdsorder' and table_name='u_f_shipbatch_$cur' and column_name='rejectreason'"
    '20260920_shipbatch_picking_flow (menu fn5107)'    = "select count(*) from sdssys.sys_menu where fun_code='fn5107'"
  }
  $missing = @()
  foreach ($k in $checks.Keys) {
    $n = Sql $checks[$k]
    if ([int]$n -gt 0) { Ok $k } else { Write-Host "  MISSING  $k" -ForegroundColor Yellow; $missing += $k }
  }
  if ($missing.Count -gt 0) {
    Fail ("these earlier migrations are not on this database yet, run them first (sql\migration in the repo): " + ($missing -join '; '))
  }

  # -------------------------------------------------------------- 3
  Step '3. backup sdssys + sdsshop + sdsorder'
  $dump = Join-Path $BackupDir "erp-before-20260929-$stamp.sql"
  $p = Start-Process -FilePath $MysqlDump -ArgumentList "-u$DbUser", "-h$DbHost", "-P$DbPort", '--default-character-set=utf8mb4',
    '--single-transaction', '--routines', '--triggers', '--databases', 'sdssys', 'sdsshop', 'sdsorder' `
    -RedirectStandardOutput $dump -RedirectStandardError "$dump.err" -NoNewWindow -Wait -PassThru
  if ($p.ExitCode -ne 0) { Fail ("mysqldump failed: " + (Get-Content "$dump.err" -Raw)) }
  $tail = Get-Content $dump -Tail 1
  if ($tail -notmatch 'Dump completed') { Fail "backup looks truncated: $dump" }
  Ok ("backup {0:N1} MB -> {1}" -f ((Get-Item $dump).Length / 1MB), $dump)

  # -------------------------------------------------------------- 4
  Step '4. run the three new migrations'
  foreach ($m in $migrations) {
    SqlFile (Join-Path $Dir "sql\$m")
    Ok $m
  }
  $after = [ordered]@{
    'menus fn5108 fn5112 fn7004 fn8060' = "select count(*) from sdssys.sys_menu where fun_code in ('fn5108','fn5112','fn7004','fn8060')"
    'doc types PYRK PKCK CBTZ'          = "select count(*) from sdsshop.u_m_doctypeconfig where doctype in ('PYRK','PKCK','CBTZ')"
    'tables stocktake + exchange rate'  = "select count(*) from information_schema.tables where table_schema='sdsshop' and table_name in ('u_m_stocktake','u_m_stocktake_item','u_m_exchange_rate')"
  }
  $want = @{ 'menus fn5108 fn5112 fn7004 fn8060' = 4; 'doc types PYRK PKCK CBTZ' = 3; 'tables stocktake + exchange rate' = 3 }
  foreach ($k in $after.Keys) {
    $n = [int](Sql $after[$k])
    if ($n -eq $want[$k]) { Ok "$k = $n" } else { Fail "$k = $n, expected $($want[$k])" }
  }
}

# ---------------------------------------------------------------- 5
if (-not $SkipBackend) {
  Step '5. backend'
  $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match '-jar\s+"?[^"]*ruoyi-admin[^"\s]*\.jar' }
  if ($LivePort -gt 0) { $procs = $procs | Where-Object { $_.CommandLine -match "server\.port=$LivePort\b" -or ($LivePort -eq 8080 -and $_.CommandLine -notmatch 'server\.port=') } }
  $procs = @($procs)
  if ($procs.Count -ne 1) { Fail "expected exactly one running ruoyi-admin jar, found $($procs.Count); pass -LivePort to pick one" }
  $live = $procs[0]
  $cmd = $live.CommandLine
  if ($LiveJar -eq '') {
    $m = [regex]::Match($cmd, '-jar\s+"?([^"]*?ruoyi-admin[^"\s]*\.jar)')
    $LiveJar = $m.Groups[1].Value
  }
  if (-not (Test-Path $LiveJar)) { Fail "live jar not found: $LiveJar" }
  $jarDir = Split-Path $LiveJar
  $javaExe = $live.ExecutablePath
  if ($LivePort -eq 0) {
    $pm = [regex]::Match($cmd, 'server\.port=(\d+)')
    $LivePort = $(if ($pm.Success) { [int]$pm.Groups[1].Value } else { 8080 })
  }
  # everything after the executable, whether it was written as "C:\...\java.exe", C:\...\java.exe or just java
  if ($cmd.StartsWith('"')) { $argLine = $cmd.Substring($cmd.IndexOf('"', 1) + 1).Trim() }
  else { $argLine = $cmd.Substring($cmd.IndexOf(' ') + 1).Trim() }
  $svc = Get-CimInstance Win32_Service | Where-Object { $_.ProcessId -eq $live.ProcessId -or $_.ProcessId -eq $live.ParentProcessId } | Select-Object -First 1
  Ok "live pid $($live.ProcessId), port $LivePort, jar $LiveJar"
  Ok $(if ($svc) { "runs as Windows service: $($svc.Name)" } else { 'not a service: will be restarted with the same command line' })
  Write-Host "  command: $cmd"

  Write-Host '  -- smoke: boot the NEW jar on the spare port first, same working folder as the live one --'
  $smokeLog = Join-Path $BackupDir "smoke-$stamp.log"
  $smoke = Start-Process -FilePath $javaExe -ArgumentList '-Xmx512m', '-jar', "`"$newJar`"", "--server.port=$SmokePort" `
    -WorkingDirectory $jarDir -RedirectStandardOutput $smokeLog -RedirectStandardError "$smokeLog.err" -WindowStyle Hidden -PassThru
  $smokeOk = Wait-Http "http://127.0.0.1:$SmokePort/captchaImage" 150
  Stop-Process -Id $smoke.Id -Force -ErrorAction SilentlyContinue
  if (-not $smokeOk) { Fail "new jar did not answer on port $SmokePort; live one untouched. See $smokeLog" }
  Ok 'new jar boots and answers'

  $jarBackup = Join-Path $BackupDir "ruoyi-admin-before-20260929-$stamp.jar"
  Copy-Item $LiveJar $jarBackup
  Ok "old jar saved: $jarBackup"

  function Stop-Live {
    if ($svc) { Stop-Service $svc.Name -Force } else { Stop-Process -Id $live.ProcessId -Force }
    for ($i = 0; $i -lt 60; $i++) {
      if (-not (Get-Process -Id $live.ProcessId -ErrorAction SilentlyContinue)) { break }
      Start-Sleep -Milliseconds 500
    }
    Start-Sleep -Seconds 2
  }
  function Start-Live {
    if ($svc) { Start-Service $svc.Name }
    else {
      $outLog = Join-Path $jarDir "ruoyi-admin-$stamp.out.log"
      Start-Process -FilePath $javaExe -ArgumentList $argLine -WorkingDirectory $jarDir `
        -RedirectStandardOutput $outLog -RedirectStandardError "$outLog.err" -WindowStyle Hidden | Out-Null
    }
  }

  Stop-Live
  Copy-Item $newJar $LiveJar -Force
  Ok "new jar in place ($((Get-Item $LiveJar).Length) bytes)"
  Start-Live
  if (Wait-Http "http://127.0.0.1:$LivePort/captchaImage" 180) {
    Ok "live backend is back on port $LivePort with the new jar"
  } else {
    Write-Host '  new jar did not come back - ROLLING BACK to the old jar' -ForegroundColor Red
    $again = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'ruoyi-admin' -and $_.CommandLine -notmatch "server\.port=$SmokePort" }
    foreach ($x in $again) { Stop-Process -Id $x.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 3
    Copy-Item $jarBackup $LiveJar -Force
    Start-Live
    if (Wait-Http "http://127.0.0.1:$LivePort/captchaImage" 180) { Fail 'rolled back: OLD jar is running again. Nothing else changed except the database migrations (harmless for the old jar).' }
    Fail "rollback did not come up either - start it by hand: $cmd"
  }
}

# ---------------------------------------------------------------- 6
if (-not $SkipWeb) {
  Step '6. frontend'
  $webBackup = Join-Path $BackupDir "web-dist-before-20260929-$stamp"
  if (Test-Path $WebRoot) { Move-Item $WebRoot $webBackup; Ok "old frontend moved to $webBackup" }
  New-Item -ItemType Directory -Path $WebRoot | Out-Null
  Expand-Archive -Path $webZip -DestinationPath $WebRoot -Force
  $index = Join-Path $WebRoot 'index.html'
  if (-not (Test-Path $index)) { Fail 'expanded tree has no index.html' }
  if (-not (Test-Path "$index.gz")) { Fail 'index.html.gz missing - nginx gzip_static would keep serving the old page' }
  $html = Get-Content $index -Raw
  $refs = [regex]::Matches($html, '(?:src|href)="(/static/[^"]+\.(?:js|css))"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
  foreach ($r in $refs) {
    $p = Join-Path $WebRoot ($r.TrimStart('/') -replace '/', '\')
    if (-not (Test-Path $p)) { Fail "index.html references a missing file: $r" }
  }
  Ok ("frontend in place: {0} files, {1} .gz" -f (Get-ChildItem $WebRoot -Recurse -File).Count, (Get-ChildItem $WebRoot -Recurse -File -Filter *.gz).Count)
  try {
    $r = Invoke-WebRequest -Uri $WebProbeUrl -Headers @{ Host = $WebHost; 'Accept-Encoding' = 'gzip' } -UseBasicParsing -TimeoutSec 10 -MaximumRedirection 0 -ErrorAction Stop
    Ok "nginx answers $($r.StatusCode) for $WebHost"
  } catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -in 301, 302, 308) { Ok "nginx answers $code (redirect) for $WebHost" } else { Write-Host "  WARN  nginx probe: $($_.Exception.Message) - open the site in a browser to check" -ForegroundColor Yellow }
  }
}

Step 'DONE'
Write-Host '  Next: open the ERP in a browser (Ctrl+F5), log in, check menus:'
Write-Host '    Inventory > Stock changes > Stocktake / Cost adjust ; Finance > Exchange rates ; Orders > Sales profit'
Write-Host '  Then enter exchange rates for USD/EUR/... in Finance > Exchange rates.'
Write-Host "  Rollback: jar  -> copy $BackupDir\ruoyi-admin-before-20260929-$stamp.jar over the live jar and restart"
Write-Host "            web  -> move $BackupDir\web-dist-before-20260929-$stamp back to $WebRoot"
Write-Host "            db   -> not needed (new tables/menus only); full dump in $BackupDir"
Write-Host "  Log: $log"
Stop-Transcript | Out-Null

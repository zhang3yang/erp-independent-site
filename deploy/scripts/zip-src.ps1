# Zip the CURRENTLY DEPLOYED backend source (pom.xml + src) so it can be pulled down
# and used as the base for a "live + only my change" package.
# Pure ASCII on purpose: scp'd and run over ssh.
$ErrorActionPreference = 'Stop'
$root = 'C:\build\station-console'
$out  = 'C:\build\live-console-src.zip'
if (-not (Test-Path $root)) { Write-Output 'RESULT: MISSING_ROOT'; exit 1 }
if (Test-Path $out) { Remove-Item $out -Force }

# target\ must not go in - it is build output and would bloat the transfer
$items = @()
$items += (Join-Path $root 'pom.xml')
$items += (Join-Path $root 'src')
Compress-Archive -Path $items -DestinationPath $out -CompressionLevel Optimal

$size = (Get-Item $out).Length
Write-Output ('RESULT: OK size=' + $size)

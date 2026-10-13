# Hash every source file under C:\build\station-console (pom.xml + src),
# normalising CRLF -> LF first so the result can be compared with a Git Bash md5sum.
# Pure ASCII on purpose: this file is scp'd and run over ssh.
$ErrorActionPreference = 'Stop'
$root = 'C:\build\station-console'
if (-not (Test-Path $root)) { Write-Output 'MISSING_ROOT'; exit 1 }

$files = @()
$pom = Join-Path $root 'pom.xml'
if (Test-Path $pom) { $files += Get-Item $pom }
$srcDir = Join-Path $root 'src'
if (Test-Path $srcDir) { $files += Get-ChildItem -Path $srcDir -Recurse -File }

$md5 = [System.Security.Cryptography.MD5]::Create()
foreach ($f in $files) {
  $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
  # drop 0x0D so CRLF and LF hash the same
  $clean = New-Object System.Collections.Generic.List[byte]
  foreach ($b in $bytes) { if ($b -ne 13) { $clean.Add($b) } }
  $hash = [BitConverter]::ToString($md5.ComputeHash($clean.ToArray())).Replace('-','').ToLower()
  $rel = $f.FullName.Substring($root.Length + 1).Replace('\','/')
  Write-Output ($hash + '  ' + $rel)
}

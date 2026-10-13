# 独立站服务器部署手册（一站一机）

> 2026-09-05 首次实施于 `49.234.194.194`（康辰站）。这一份是**照着敲**的操作手册，
> 每一步都说清了在哪台机器上做、敲什么、怎么算成功、不对的话先看哪里。
>
> **下次部署另一个站，只需要换掉 [附录 C](#附录-c本次部署的实际值) 里那张表的值**，
> 正文里所有 `<主域名>`、`<站点编码>` 之类的占位符都从那张表来。
>
> 为什么这么设计（边界、协议、取舍理由）不在这里，在
> [独立站与ERP的边界.md](./独立站与ERP的边界.md)。那一份是"为什么"，这一份是"怎么做"。

| | |
| --- | --- |
| 适用 | 一台全新的 Windows Server，部署 `station-console` + `station-console-ui` + `station-site` |
| 前提 | ERP 已在别处运行；本机不安装 ERP |
| 耗时 | 首次约 3~4 小时（含踩坑），熟练后约 1.5 小时 |
| 中断服务 | 全程不影响 ERP |

---

## 目录

- [0. 开始之前](#0-开始之前)
- [1. 打通 SSH](#1-打通-ssh)
- [2. 基础组件：VC++ / MySQL / Redis / Nginx](#2-基础组件vc--mysql--redis--nginx)
- [3. 运行时：JDK / Node / Maven / nssm](#3-运行时jdk--node--maven--nssm)
- [4. 建库与迁数据](#4-建库与迁数据)
- [5. 构建与部署三个模块](#5-构建与部署三个模块)
- [6. nginx 与 SSL 证书](#6-nginx-与-ssl-证书)
- [7. 打通「从 ERP 进入管理」](#7-打通从-erp-进入管理)
- [8. 文件存储](#8-文件存储)
- [9. 验收清单](#9-验收清单)
- [10. 排查表](#10-排查表)
- [附录 A：网络限制与可用下载源](#附录-a网络限制与可用下载源)
- [附录 B：常用运维命令](#附录-b常用运维命令)
- [附录 C：本次部署的实际值](#附录-c本次部署的实际值)
- [附录 D：改早了会出事的三个地方](#附录-d改早了会出事的三个地方)
- [附录 E：ERP 前端（console.erp）首屏加载优化怎么落地](#附录-eerp-前端consoleerp首屏加载优化怎么落地)

---

## 0. 开始之前

### 0.1 先凑齐这些

| 东西 | 说明 |
| --- | --- |
| 服务器 | Windows Server，**最低 4 GB 内存**。4 GB 能跑起来，但前端构建必须在别处做（§5.2） |
| 云控制台 | 改安全组、签证书要用 |
| 五个域名 | 主域名 + `data.` + `console.` + `file.` + `buyer.`（用途见 §6.1） |
| SSL 证书 | 每个对外域名一张，单域名 DV 即可 |
| ERP 侧权限 | 能进「独立站管理」页面，能重置跳转密钥 |
| 对象存储（可选） | 用 COS 存图片的话，要 SecretId / SecretKey / 桶名 / 地域 |

### 0.2 端口与安全组

安全组**只开三个**：

```
22    SSH（部署用；也可以只用 RDP，但那样全程手工，很痛苦）
80    HTTP（只用于跳转到 HTTPS）
443   HTTPS
```

**这几个绝不对公网开放**：

```
8081  station-console      绑 127.0.0.1
3000  station-site (SSR)   绑 127.0.0.1
6379  Redis                绑 127.0.0.1
3306  MySQL                默认绑 127.0.0.1
```

> `3306` 如果你确实要远程连库管理，**优先走 SSH 隧道**，不要开安全组：
> ```bash
> ssh -L 3306:127.0.0.1:3306 Administrator@<服务器IP>
> ```
> 本地连 `127.0.0.1:3306` 即可。开公网 3306 + 弱口令，几小时内必被扫到。

### 0.3 一个贯穿全程的坑：PowerShell 脚本编码

**所有带中文的 `.ps1` 必须存成 UTF-8 with BOM。**

PowerShell 5.1 读取 `.ps1` 默认按系统 ANSI（中文环境是 GBK）解码。UTF-8 无 BOM 的文件里，
中文注释会被解成乱码，**并且乱码字符会破坏语法**，报一堆莫名其妙的「字符串缺少终止符」。

在本机转换：

```powershell
$p = 'xxx.ps1'
$c = [IO.File]::ReadAllText($p, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText($p, $c, (New-Object Text.UTF8Encoding $true))   # $true = 带 BOM
```

**另一个相关的坑**：不要用 `bash -c "ssh host 'powershell -Command ...'"` 这种嵌套方式传复杂脚本，
`$_`、`''`、`\"` 会被 bash 和 PowerShell 各吃一层，SQL 里的引号尤其容易丢。
**一律写成 `.ps1` 文件 → `scp` 上去 → `ssh` 执行**。

---

## 1. 打通 SSH

### 1.1 云控制台放行 22

安全组入站规则加：`TCP:22`、来源 `0.0.0.0/0`。顺手把 `80`、`443` 也加了。

验证（在你自己机器上）：

```bash
# 连得上但没服务 = "拒绝"；安全组没开 = "超时"。两者症状不同，能区分问题在哪一层
nc -vz <服务器IP> 22
```

### 1.2 装 OpenSSH Server

RDP 登录服务器，**以管理员身份**打开 PowerShell。

> ### ⚠ Windows Server 2016 装不了 Windows Capability
>
> ```powershell
> Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
> ```
> 这条命令在 **Server 2016 上不会报错，但也什么都不做**——OpenSSH 作为可选功能是
> Windows Server 2019 / Windows 10 1809 才引入的。判断方法：
>
> ```powershell
> Get-WindowsCapability -Online -Name OpenSSH*
> ```
> 输出里 `Name` 那一列是**空的** → 这个系统根本没有这个功能条目，别再试了，走下面的离线安装。
>
> Server 2019+ 可以直接用上面那条命令，装完跳到 §1.3。

**离线安装**（Server 2016 必走，其他版本也可用）：

```powershell
# GitHub 从国内服务器通常不通，先在自己机器上下好：
#   https://github.com/PowerShell/Win32-OpenSSH/releases/download/v9.5.0.0p1-Beta/OpenSSH-Win64.zip
# 然后通过 RDP 驱动器重定向传上去（见下方提示）

Expand-Archive -Path 'C:\OpenSSH-Win64.zip' -DestinationPath 'C:\Program Files' -Force
powershell -ExecutionPolicy Bypass -File 'C:\Program Files\OpenSSH-Win64\install-sshd.ps1'
Start-Service sshd
Set-Service -Name sshd -StartupType Automatic
Get-Service sshd     # 必须是 Running
```

> **传文件到服务器：用 RDP 驱动器重定向，别用剪贴板。**
>
> 剪贴板复制文件时好时坏（实测失败）。可靠做法：断开 RDP → `mstsc` → 「显示选项」→
> 「本地资源」→「详细信息」→ 勾选「驱动器」→ 重新连接。登录后本机磁盘在 `\\tsclient\c`：
>
> ```powershell
> Copy-Item '\\tsclient\c\Users\<你>\Downloads\OpenSSH-Win64.zip' -Destination 'C:\' -Force
> ```
>
> ⚠ `\\tsclient` **只在 RDP 会话内有效**，SSH 会话里访问不到。

### 1.3 防火墙、默认 shell、免密

```powershell
if (-not (Get-NetFirewallRule -Name sshd -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name sshd -DisplayName 'OpenSSH Server (sshd)' -Enabled True `
        -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null
}

# 默认 shell 换成 PowerShell，否则 SSH 进来是 cmd，脚本没法跑
New-ItemProperty -Path 'HKLM:\SOFTWARE\OpenSSH' -Name DefaultShell `
    -Value 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -PropertyType String -Force
```

免密登录（把部署机的公钥装上）：

```powershell
$key = '<粘贴部署机的 ~/.ssh/id_ed25519.pub 内容>'
$f = 'C:\ProgramData\ssh\administrators_authorized_keys'
New-Item -ItemType Directory -Force -Path 'C:\ProgramData\ssh' | Out-Null

# ⚠ 必须无 BOM。PS 5.1 的 -Encoding utf8 会写 BOM，OpenSSH 读到 BOM 会
#   静默忽略整个文件，表现是"密钥不生效、仍然要密码"，非常难查
[IO.File]::WriteAllText($f, $key + "`n", (New-Object Text.UTF8Encoding $false))

# 权限必须收紧，否则 OpenSSH 拒绝使用这个文件（同样是静默的）
icacls $f /inheritance:r /grant 'Administrators:F' /grant 'SYSTEM:F'
Restart-Service sshd
```

验证（部署机上）：

```bash
ssh Administrator@<服务器IP> '$PSVersionTable.PSVersion.ToString()'
```

**从这一步之后，所有操作都在部署机上通过 SSH 完成，不用再开 RDP。**

---

## 2. 基础组件：VC++ / MySQL / Redis / Nginx

### 2.1 Visual C++ 运行库（MySQL 的前置条件）

> ### ⚠ 不装这个，MySQL 初始化会失败，且错误信息毫无提示
>
> 症状：`mysqld --initialize-insecure` 退出码 **-1073741515**（`0xC0000135`，
> `STATUS_DLL_NOT_FOUND`），控制台一个字都不输出。
>
> 原因：Server 2016 镜像自带 `VCRUNTIME140.dll` 和 `MSVCP140.dll`，
> 但**缺 `VCRUNTIME140_1.dll`**，而 MySQL 8.0 的 `mysqld.exe` 依赖它。

```powershell
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$exe = 'C:\setup\vc_redist.x64.exe'
New-Item -ItemType Directory -Force -Path 'C:\setup' | Out-Null
(New-Object Net.WebClient).DownloadFile('https://aka.ms/vs/17/release/vc_redist.x64.exe', $exe)
Start-Process -FilePath $exe -ArgumentList '/install','/quiet','/norestart' -Wait
# 退出码 0 / 1638（已有更新版）/ 3010（需重启但 DLL 已就位）都算成功

# 验证三个 DLL 都在
foreach ($d in 'VCRUNTIME140.dll','VCRUNTIME140_1.dll','MSVCP140.dll') {
    "{0}: {1}" -f $d, (Test-Path "C:\Windows\System32\$d")
}
```

### 2.2 MySQL 8.0

> ### ⚠ 版本选择：跳过 8.0.29
>
> MySQL **8.0.29 有 instant DDL 导致数据损坏的缺陷**（Bug #107854），官方建议跳过。
> 本手册用 **8.0.28**。华为镜像上最高只到 8.0.29，所以 8.0.28 是可选范围内的最优。

下载解压（华为镜像实测 36 MB/s）：

```powershell
$url = 'https://mirrors.huaweicloud.com/mysql/Downloads/MySQL-8.0/mysql-8.0.28-winx64.zip'
(New-Object Net.WebClient).DownloadFile($url, 'C:\setup\mysql.zip')
Expand-Archive 'C:\setup\mysql.zip' -DestinationPath 'C:\setup\x_mysql' -Force
Move-Item 'C:\setup\x_mysql\mysql-8.0.28-winx64' 'C:\mysql'
```

> **解压后 `Move-Item` 可能报「访问被拒绝」** —— 杀软还占着刚解压出来的文件句柄。
> 重试 2~3 次（间隔 3 秒）通常就好，仍不行就改用 `Copy-Item -Recurse`。

写 `C:\mysql\my.ini`（**按 4 GB 内存调过参**）：

```ini
[mysqld]
basedir=C:/mysql
datadir=C:/mysql/data
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
default-storage-engine=INNODB
default_authentication_plugin=mysql_native_password
max_connections=200
max_allowed_packet=64M
default-time-zone='+08:00'
sql_mode=STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION
innodb_buffer_pool_size=512M
innodb_log_file_size=128M
performance_schema=OFF
log-error=C:/mysql/data/mysql-error.log
slow_query_log=1
slow_query_log_file=C:/mysql/data/mysql-slow.log
long_query_time=2

[client]
port=3306
default-character-set=utf8mb4
```

> 用 `[IO.File]::WriteAllText($path, $ini, (New-Object Text.UTF8Encoding $false))` 写，
> 不要用 `Set-Content -Encoding utf8`（会带 BOM）。
>
> `performance_schema=OFF` 是为 4 GB 内存省下约 200 MB。机器内存宽裕就别关，
> 它是排查性能问题的主要手段。

初始化并注册服务：

```powershell
& 'C:\mysql\bin\mysqld.exe' --defaults-file=C:\mysql\my.ini --initialize-insecure --console
& 'C:\mysql\bin\mysqld.exe' --install MySQL8 --defaults-file=C:\mysql\my.ini
Set-Service -Name MySQL8 -StartupType Automatic
Start-Service MySQL8

# root 初始无密码，立刻设上
& 'C:\mysql\bin\mysql.exe' -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '<root密码>'; FLUSH PRIVILEGES;"
```

### 2.3 Redis

> ### ⚠ Windows 上没有官方 Redis，能用的只有一个
>
> 实测（2026-09）：
>
> | 来源 | 版本 | 结果 |
> | --- | --- | --- |
> | GitHub tporadowski 5.0.14 | 5.0.14 | **下不到**，国内服务器连不上 GitHub，所有加速代理也已失效 |
> | chocolatey `redis` | 6.2.x ~ 8.x | **不能用**：MSYS2 兼容层构建，依赖 5 个 `msys-*.dll`，不支持 `--service-install`，实测直接启动就退出码 1 |
> | nuget `redis.windows.redist.x64` | 8.2.1 | 同上，也是 MSYS2 构建 |
> | **nuget `Redis-64` 3.0.503** | 3.0.503 | ✅ **唯一的原生 Win32 移植版**，支持注册 Windows 服务 |
>
> 所以用 3.0.503（MSOpenTech，2016 年）。对 RuoYi 的用途（缓存 + token + 会话 + jti）
> 功能完全够；**但要注意**：Spring Boot 3/4 的 Lettuce 6 默认发 `HELLO` 协商 RESP3，
> Redis 3.0 不支持这个命令，Lettuce 会降级到 RESP2。理论上没问题，
> 但后端首次启动时要**盯一眼日志有没有 Redis 相关报错**。

```powershell
$pkg = 'C:\setup\redis.nupkg.zip'
(New-Object Net.WebClient).DownloadFile('https://www.nuget.org/api/v2/package/Redis-64/3.0.503', $pkg)
Expand-Archive $pkg -DestinationPath 'C:\setup\x_redis' -Force
New-Item -ItemType Directory -Force -Path 'C:\redis' | Out-Null
Copy-Item 'C:\setup\x_redis\tools\*' 'C:\redis' -Recurse -Force
```

写 `C:\redis\redis.windows-service.conf`：

```
bind 127.0.0.1
port 6379
timeout 0
databases 16
save 900 1
save 300 10
save 60 10000
dbfilename dump.rdb
dir C:/redis/
appendonly no
maxmemory 256mb
maxmemory-policy allkeys-lru
logfile C:/redis/redis.log
loglevel notice
```

> ⚠ **不要写 `maxheap`**。那是 MSOpenTech 某些版本的参数，3.0.503 不认，
> 会导致 `--service-install` 直接失败并报 `unknown conf file parameter`。

```powershell
& 'C:\redis\redis-server.exe' --service-install 'C:\redis\redis.windows-service.conf' --service-name Redis
Set-Service -Name Redis -StartupType Automatic
Start-Service Redis
& 'C:\redis\redis-cli.exe' ping        # 必须是 PONG
```

### 2.4 Nginx

```powershell
(New-Object Net.WebClient).DownloadFile('https://nginx.org/download/nginx-1.26.2.zip', 'C:\setup\nginx.zip')
Expand-Archive 'C:\setup\nginx.zip' -DestinationPath 'C:\setup\x_nginx' -Force
Move-Item 'C:\setup\x_nginx\nginx-1.26.2' 'C:\nginx'
New-Item -ItemType Directory -Force -Path 'C:\nginx\conf\conf.d', 'C:\nginx\cert' | Out-Null
```

> `nginx.org` 能下但很慢（实测 2 MB 用了 113 秒），有耐心即可。

**nginx 在 Windows 上不是服务**，用计划任务实现开机自启：

```powershell
$action    = New-ScheduledTaskAction -Execute 'C:\nginx\nginx.exe' -WorkingDirectory 'C:\nginx'
$trigger   = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName 'nginx-autostart' -Action $action -Trigger $trigger `
    -Principal $principal -Settings $settings

Start-Process -FilePath 'C:\nginx\nginx.exe' -WorkingDirectory 'C:\nginx'
```

> **`Get-ScheduledTask` 在 Server 2016 上可能查不到自己刚建的任务**（返回空，
> 但再次 `Register-ScheduledTask` 会报「已存在」）。这是 cmdlet 的怪癖，任务本身是好的。
> 用 `schtasks /query /tn nginx-autostart /v /fo LIST` 核实，
> 看到 `Status: Ready` 和 `Schedule Type: At system start up` 就对了。

---

## 3. 运行时：JDK / Node / Maven / nssm

版本依据：`RuoYi-Vue/pom.xml` 的 `<java.version>17</java.version>`；
`station-site` 用 Nuxt 4（要求 Node ≥ 22.12），`station-console-ui` 用 Vite 6。

### 3.1 JDK 17

> ### ⚠ Adoptium(Temurin) 的国内镜像全部拒绝下载
>
> 实测清华 / 北外 / 南大 / 中科大：**能列目录，但文件 GET 一律 403**。
> 改用**微软的 OpenJDK 构建**（同一个 17.0.20.1 上游，CDN 在国内可达，实测 178 MB / 15 秒）。
> 备选是 `corretto.aws`（Amazon Corretto），也通。

```powershell
(New-Object Net.WebClient).DownloadFile('https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip', 'C:\setup\jdk.zip')
Expand-Archive 'C:\setup\jdk.zip' -DestinationPath 'C:\setup\x_jdk' -Force
Move-Item (Get-ChildItem 'C:\setup\x_jdk' -Directory)[0].FullName 'C:\jdk17'

[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\jdk17', 'Machine')
```

### 3.2 Node.js 22

```powershell
$v = '22.23.2'
(New-Object Net.WebClient).DownloadFile("https://mirrors.huaweicloud.com/nodejs/v$v/node-v$v-win-x64.zip", 'C:\setup\node.zip')
Expand-Archive 'C:\setup\node.zip' -DestinationPath 'C:\setup\x_node' -Force
Move-Item "C:\setup\x_node\node-v$v-win-x64" 'C:\nodejs'

# npmjs.org 从国内服务器不通，必须换源
& 'C:\nodejs\npm.cmd' config set registry https://registry.npmmirror.com --location=global
```

### 3.3 Maven 3.9

项目**没有 Maven Wrapper**（没有 `mvnw`），所以服务器上必须装 Maven 才能构建后端。

```powershell
$v = '3.9.16'
(New-Object Net.WebClient).DownloadFile("https://mirrors.huaweicloud.com/apache/maven/maven-3/$v/binaries/apache-maven-$v-bin.zip", 'C:\setup\maven.zip')
Expand-Archive 'C:\setup\maven.zip' -DestinationPath 'C:\setup\x_maven' -Force
Move-Item "C:\setup\x_maven\apache-maven-$v" 'C:\maven'
[Environment]::SetEnvironmentVariable('MAVEN_HOME', 'C:\maven', 'Machine')
```

`C:\maven\conf\settings.xml`（把 central 镜像到阿里云，实测 60ms vs 直连 832ms）：

```xml
<settings>
  <localRepository>C:/maven-repo</localRepository>
  <mirrors>
    <mirror>
      <id>aliyun-central</id>
      <url>https://maven.aliyun.com/repository/central</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

> `mirrorOf` 只写 `central`，不要写 `*`。写 `*` 会把 pom 里自己声明的仓库
> （spring milestone 之类）也强行导到阿里云，那些包在那边可能没有。

### 3.4 nssm（把 jar 和 node 进程做成服务）

> ### ⚠ nssm.cc 从国内服务器不通，从 chocolatey 取
>
> 而且 chocolatey 的包里是**嵌套 zip**：`nupkg` 解开后 `tools/` 下还有一个
> `nssm-2.24-xxx.zip`，要再解一层才能拿到 `win64/nssm.exe`。

```powershell
(New-Object Net.WebClient).DownloadFile('https://community.chocolatey.org/api/v2/package/NSSM', 'C:\setup\nssm.zip')
Expand-Archive 'C:\setup\nssm.zip' -DestinationPath 'C:\setup\x_nssm' -Force
$inner = Get-ChildItem 'C:\setup\x_nssm' -Recurse -Filter 'nssm*.zip' | Select-Object -First 1
Expand-Archive $inner.FullName -DestinationPath 'C:\setup\x_nssm2' -Force
$exe = Get-ChildItem 'C:\setup\x_nssm2' -Recurse -Filter 'nssm.exe' |
       Where-Object { $_.FullName -match 'win64' } | Select-Object -First 1
Copy-Item $exe.FullName 'C:\Windows\System32\nssm.exe' -Force
nssm version
```

### 3.5 统一加 PATH

```powershell
# 改之前先备份，PATH 改错很难恢复
$cur = [Environment]::GetEnvironmentVariable('Path','Machine')
[IO.File]::WriteAllText("C:\setup\path-backup-$(Get-Date -f yyyyMMdd-HHmmss).txt", $cur)

foreach ($d in 'C:\jdk17\bin','C:\nodejs','C:\maven\bin') {
    if (($cur -split ';') -notcontains $d) { $cur = $cur.TrimEnd(';') + ';' + $d }
}
[Environment]::SetEnvironmentVariable('Path', $cur, 'Machine')
```

**机器级环境变量只对新会话生效。** 断开 SSH 重连再验证：

```bash
ssh Administrator@<服务器IP> 'node -v; (java -version 2>&1)[0]; (mvn -v)[0]'
```

---

## 4. 建库与迁数据

### 4.1 建库和专用账号

```powershell
$mysql = 'C:\mysql\bin\mysql.exe'
$env:MYSQL_PWD = '<root密码>'      # 用环境变量传密码，见下方提示

& $mysql -h 127.0.0.1 -u root -e @"
CREATE DATABASE IF NOT EXISTS sdsstation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'station'@'localhost'  IDENTIFIED BY '<station密码>';
CREATE USER IF NOT EXISTS 'station'@'127.0.0.1'  IDENTIFIED BY '<station密码>';
GRANT ALL PRIVILEGES ON sdsstation.* TO 'station'@'localhost';
GRANT ALL PRIVILEGES ON sdsstation.* TO 'station'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
```

> **`localhost` 和 `127.0.0.1` 在 MySQL 里是两个不同的 host**，应用走 TCP 连
> `127.0.0.1`，两个都建才不会踩 access denied。
>
> **用 `$env:MYSQL_PWD` 传密码，不要用 `-p<密码>`。** 命令行带密码时 mysql 会往
> stderr 写一条警告，而 PS 5.1 会把 native 命令的 stderr 包装成 `NativeCommandError`——
> 在 `$ErrorActionPreference='Stop'` 下整个脚本会中断在一条**警告**上。

验证权限边界（这个账号不该看见别的库）：

```powershell
& $mysql -h 127.0.0.1 -u station -e 'SHOW DATABASES;'
# 期望只有 information_schema 和 sdsstation
```

### 4.2 建表

> ### ⚠ 基线曾经落后于 migration，现在补上了——但核对这一步别省
>
> 这里原来写着「不要只跑 `01_station_schema.sql`」，理由是 2026-09-05 实测按它
> 建出来的库比开发库少 `u_s_inquiry` 的 13 个列（`inquirytype` / `inquiryref` /
> `currency` / `lang` / `ship*` 等）和整张 `u_s_inquiry_item` 表。
>
> **2026-09-07 复核，那两条都不成立了：**
>
> - 那 13 个列基线**已经补上**（注释里标着「RFQ 留位置」，全部可空、恒为 NULL）。
> - `u_s_inquiry_item` **已经删了，线上没有这张表是对的**。它由
>   `migration/2026-09-04-inquiry-structured-rfq.sql` 建出来，但那套设计（结账页走
>   询盘接口、把明细结构化）**代码一处没接**，被订单接口 `POST /openapi/site/order`
>   → `u_s_order` + `u_s_orderitem` 取代，零引用的空表已由
>   `migration/2026-09-06-drop-inquiry-item-and-orphans.sql` 删除。
>   **不要因为看到这段历史就把它建回去**，同一件事留两套表会出现「这单在询盘里
>   还是在订单里」的问题。
>
> 表清单已与线上逐张一致（基线 15 张 / 线上 15 张，2026-09-07 实测）。
> 但基线会不会**再次**落后于 `migration/`，只有下面那步 diff 说得准——照旧要做。

**两个做法，二选一：**

**A. 从一个结构正确的库整库迁移**（推荐，同时完成建表和迁数据）

```bash
# 在有正确结构的机器上（比如开发机）
mysqldump -u root -p --databases sdsstation --add-drop-database \
  --default-character-set=utf8mb4 --single-transaction > sdsstation_full.sql
scp sdsstation_full.sql Administrator@<服务器IP>:C:/station-sql/
```

```powershell
# 服务器上
& $mysql -h 127.0.0.1 -u root --default-character-set=utf8mb4 -e "source C:/station-sql/sdsstation_full.sql"
# DROP DATABASE 不会清掉授权，但重新 GRANT 一次成本极低，省得纠结
```

**B. 全新空库**：先跑 `01_station_schema.sql`，再按文件名顺序跑 `migration/` 下全部脚本。

> **文件名顺序必须守住**，全部跑完的结果才是对的：`2026-09-04-inquiry-structured-rfq.sql`
> 会建出 `u_s_inquiry_item`，紧接着 `2026-09-06-drop-inquiry-item-and-orphans.sql`
> 又把它删掉——中间那张表一闪而过是正常的，别以为跑错了停在中途。
> 跳着挑脚本跑就会留下这张多余的表。

**无论走哪条，都要核对结构**（比读脚本可靠）：

```sql
-- 两边都跑，然后 diff 结果
SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='sdsstation' ORDER BY 1;
```

### 4.3 初始化本站档案

`u_s_siteinfo` **必须有且只有一行**，没有的话整站起不来
（`SiteInfoService.require()` 抛异常 → 前台全 500、管理台进不去）。

```sql
USE sdsstation;
INSERT IGNORE INTO u_s_siteinfo
  (id, sitecode, sitename, shop_code, domain, langs, currency, status,
   contentjson, notifyemail, consolesecret, remark)
VALUES
  (1,
   '<站点编码>',            -- ⚠ 必须和 ERP 登记表 sdsshop.u_s_site.sitecode 完全一致
   '<站点名称>',
   '<商户码>',
   'https://<前台域名>',
   'en,zh',                 -- 第一项是默认语言
   'USD',
   '运行中',
   NULL, NULL,
   NULL,                    -- 跳转密钥留 NULL，§7 再填。留空 = SSO 一律拒绝（fail-closed）
   '部署于 <日期>');
```

> **`sitecode` 是整个部署里最容易出错的一格。** 它必须和 ERP 登记表一致，
> 而且站点库里所有业务表的 `sitecode` 列也要跟着一致，否则前台查不到数据。
> 从别的库迁数据过来时尤其要核对（迁移过来的是源库的 sitecode）：
>
> ```sql
> -- 找出所有带 sitecode 的表，逐个看分布
> SELECT TABLE_NAME FROM information_schema.COLUMNS
> WHERE TABLE_SCHEMA='sdsstation' AND COLUMN_NAME='sitecode';
> -- 需要统一时（先备份！）
> UPDATE <表> SET sitecode='<新编码>' WHERE sitecode='<旧编码>';
> ```

---

## 5. 构建与部署三个模块

### 5.1 目录约定

```
C:\wczlerp\
  station-console\        后端 jar
  station-console-ui\     管理台静态文件（dist 的内容）
  station-site\           前台 SSR 产物（里面是 .output\）
  station-upload\         本地文件上传目录
  logs\                   服务日志
  .config-secret          存储密钥的加密主密钥（§8）
```

### 5.2 前端在本机构建，后端在服务器构建

> ### ⚠ 4 GB 内存跑不动 Nuxt 构建
>
> `nuxt build` 通常要吃 2~4 GB，而服务器扣掉 MySQL + Redis + nginx 后只剩约 2 GB，
> 会撞 `JavaScript heap out of memory`。**前端一律在本机构建，只把产物传上去。**
>
> 后端相反，**在服务器构建**：那里的 JDK 版本和运行时完全一致。开发机常装着更高版本的
> JDK（如 21），虽然 `maven.compiler.release=17` 能生成正确字节码，但 Spring Boot 4
> 加一堆插件在高版本 JDK 下容易有意外。

**本机构建前端**：

```bash
cd station-console-ui && npm run build          # 产物 dist/，baseURL 硬编码 /api，无需环境变量
cd ../station-site && NUXT_PUBLIC_SITE_URL=https://<前台域名> npm run build   # 产物 .output/
```

打包上传（用 zip，目录文件太多逐个 scp 很慢）：

```powershell
Compress-Archive -Path 'station-console-ui\dist\*' -DestinationPath 'console-ui-dist.zip' -Force
Compress-Archive -Path 'station-site\.output\*'    -DestinationPath 'site-output.zip' -Force
```

```bash
scp console-ui-dist.zip site-output.zip Administrator@<服务器IP>:C:/build/
```

**服务器构建后端**：

```bash
# 只传源码和 pom，别传 target
scp station-console-src.zip Administrator@<服务器IP>:C:/build/
ssh Administrator@<服务器IP> 'cd C:\build\station-console; mvn -B clean package -DskipTests'
```

首次构建要下依赖，约 2.5 分钟。产物是 `target\station-console-1.0.0.jar`。

### 5.3 产物就位

```powershell
Copy-Item 'C:\build\station-console\target\station-console-1.0.0.jar' 'C:\wczlerp\station-console\' -Force

Expand-Archive 'C:\build\console-ui-dist.zip' -DestinationPath 'C:\wczlerp\station-console-ui' -Force

# ⚠ 前台产物必须落成 C:\wczlerp\station-site\.output\server\index.mjs
New-Item -ItemType Directory -Force -Path 'C:\wczlerp\station-site\.output' | Out-Null
Expand-Archive 'C:\build\site-output.zip' -DestinationPath 'C:\wczlerp\station-site\.output' -Force
Test-Path 'C:\wczlerp\station-site\.output\server\index.mjs'    # 必须 True
```

### 5.4 注册成服务

**后端 `wczl-station-console`（8081）**：

```powershell
nssm install wczl-station-console 'C:\jdk17\bin\java.exe'
nssm set wczl-station-console AppDirectory 'C:\wczlerp\station-console'
nssm set wczl-station-console AppParameters '-Xms256m -Xmx512m -jar station-console-1.0.0.jar'
nssm set wczl-station-console Start SERVICE_AUTO_START
nssm set wczl-station-console AppStdout 'C:\wczlerp\logs\station-console.out.log'
nssm set wczl-station-console AppStderr 'C:\wczlerp\logs\station-console.err.log'
nssm set wczl-station-console AppRotateFiles 1
nssm set wczl-station-console AppRotateBytes 10485760
```

环境变量（`nssm set <服务> AppEnvironmentExtra`，多行用 `\r\n` 连接）：

```
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=station
DB_PASSWORD=<station密码>
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DATABASE=0
STATION_UPLOAD_DIR=C:\wczlerp\station-upload
# COS 模式留空；只有 local 模式才填能访问本机上传目录的 /files 地址
STATION_FILE_BASE_URL=
CONSOLE_HOME_URL=https://<管理台域名>
CONSOLE_COOKIE_SECURE=true
STATION_BUYER_COOKIE_SECURE=true
SERVER_ADDRESS=127.0.0.1
SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK
STATION_CONFIG_SECRET=<高熵随机串，见 §8>
```

> **`SERVER_ADDRESS=127.0.0.1`** —— 不设的话 Spring Boot 监听 `::`（所有地址），
> 8081 就暴露在公网了（虽然安全组挡着，但这是纵深防御该做的）。
>
> **`SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`** —— 这一条**必须有**，
> 原因见 §7.4，漏了会导致 SSO 跳转绕一跳明文 HTTP，而且询盘的按 IP 限流会失效。
>
> **`STATION_FILE_BASE_URL`** 只在文件落本机磁盘时用。康辰按腾讯云 COS 部署时必须留空，
> 公开地址填在 `u_s_storage.public_url`，即 `https://file.kangchen.vclinker.com`。
> 只有 local 模式才填写能访问本机 `station-upload` 目录的 `/files/` 地址。

**前台 `wczl-station-site`（3000）**：

```powershell
nssm install wczl-station-site 'C:\nodejs\node.exe'
nssm set wczl-station-site AppDirectory 'C:\wczlerp\station-site'
nssm set wczl-station-site AppParameters '.output\server\index.mjs'
nssm set wczl-station-site Start SERVICE_AUTO_START
nssm set wczl-station-site AppStdout 'C:\wczlerp\logs\station-site.out.log'
nssm set wczl-station-site AppStderr 'C:\wczlerp\logs\station-site.err.log'
```

环境变量：

```
NUXT_API_BASE=http://127.0.0.1:8081
NUXT_PUBLIC_SITE_URL=https://<前台域名>
PORT=3000
HOST=127.0.0.1
```

> **`NUXT_API_BASE` 是 8081 不是 8080。** 8080 是 ERP，前台不该认识它。
>
> **`HOST=127.0.0.1`** 让 SSR 只监听回环，公网只能经 nginx 进来。
>
> ⚠ 生产目录里如果有 `.env` 文件，**它不生效**——Nitro 的生产服务器只认真正的环境变量。
> 留着会让人改了它以为改了配置，建议删掉。

启动并自检：

```powershell
Start-Service wczl-station-console; Start-Sleep 25
Start-Service wczl-station-site;    Start-Sleep 8

Invoke-WebRequest 'http://127.0.0.1:8081/openapi/site/info' -UseBasicParsing | Select-Object StatusCode
Invoke-WebRequest 'http://127.0.0.1:3000/' -UseBasicParsing | Select-Object StatusCode
```

---

## 6. nginx 与 SSL 证书

### 6.1 五个域名各自干什么

| 域名 | 服务 | nginx 做什么 |
| --- | --- | --- |
| `<主域名>` | station-site (Nuxt SSR) 的**公开页** | 反代 `127.0.0.1:3000`；`/openapi/site/` 转 `:8081`（同源，免 CORS）；账号页那几条路径 302 到 `buyer.` |
| `buyer.<主域名>` | station-site 的**账号页** | 同上反代同一个 Node 进程，但只服务登录/注册/找回密码/重置密码/邮箱验证/账户，其余 301 回主域 |
| `console.<主域名>` | station-console-ui | 静态目录；`/api/` 和 `= /sso` 转 `:8081` |
| `data.<主域名>` | station-console 对外 API | 只开 `/openapi/site/` 和 `/files/`，其余 404 |
| `file.<主域名>` | 文件 | 用对象存储则不在本机配；用本机磁盘则 root 指向 `station-upload` |

> ⛔ **2026-09-08：两个站现在都还是单 host**（前台整站在 `buyer.` 上）。统领站的主域
> 还没备案、也没有证书，切换清单在 `deploy/nginx/tongling.conf` 文件头；
> 康辰那台的迁移步骤在 `deploy/nginx/station.windows.conf.template` 文件头。
> 下面这张表写的是目标分工。
>
> **前台是两个 host，一个 Node 进程。** 分的是 host 不是应用，分界线是要不要登录。
> 购物车和「我的询价」留在主域——它们的数据在 `localStorage` 里、按源隔离，
> 挪到 `buyer.` 上买家在主域加的货那边就是空的（而且页面不报错）。
> 完整配置见 `deploy/nginx/tongling.conf`，两个 host 各要一张证书。
>
> ⚠ `buyer.` 这一行原来写的是「买家后台，一期只登记，可不配」——**现在必须配**，
> 而且它一度是前台的**唯一** host（那时候主域没证书）。哪个站是哪种布局看附录 C。

> **前台的取数走自己域名下的 `/openapi/site/`，不要让浏览器直接打 `data.` 子域。**
> 同源省一次跨域预检，也不用维护 CORS 白名单（`station.cors.allowed-origins` 保持留空）。

### 6.2 证书放置约定

```
C:\nginx\cert\<域名>\<域名>.pem      ← 腾讯云下载包里的 xxx_bundle.pem（或 _bundle.crt）
C:\nginx\cert\<域名>\<域名>.key
```

> **一定要用 `_bundle` 那一份**（含完整证书链）。用不带 bundle 的单证书，
> 桌面浏览器正常但部分 Android 和老客户端会报证书链不完整——这种问题很难被发现。

装之前先校验（本机跑，openssl）：

```bash
crt=<域名>_bundle.pem ; key=<域名>.key
openssl x509 -in $crt -noout -subject -enddate -ext subjectAltName
openssl x509 -in $crt -noout -checkend 604800          # 7 天内会不会过期
# 私钥和证书是否配套（两个 md5 必须相同）
openssl x509 -in $crt -noout -modulus | openssl md5
openssl rsa  -in $key -noout -modulus | openssl md5
grep -c 'BEGIN CERTIFICATE' $crt                        # 应该 >1，说明带链
```

### 6.3 nginx.conf 必须加的一行

> ### ⚠ `server_names_hash_bucket_size` 默认 32，装不下长域名
>
> `console.kangchen.vclinker.com` 有 28 字符，四个域名一起会让 nginx 直接拒绝启动：
> ```
> nginx: [emerg] could not build server_names_hash, you should increase server_names_hash_bucket_size: 32
> ```
> 这个指令**只能写在 `http {}` 里**，放 `server` 块或 conf.d 的片段里都无效。

在 `C:\nginx\conf\nginx.conf` 的 `http {}` 内、第一个 `server {` 之前插入：

```nginx
    server_names_hash_bucket_size 64;
    include conf.d/*.conf;
```

### 6.4 站点配置

完整配置见仓库 `deploy/nginx/`，要点：

```nginx
# 1) http 全部跳 https
server {
    listen 80;
    server_name <前台域名> console.<主域名> data.<主域名>;
    return 301 https://$host$request_uri;
}

# 2) 前台：SSR 反代 + 同源接口
server {
    listen 443 ssl;
    server_name <前台域名>;
    ssl_certificate     C:/nginx/cert/<前台域名>/<前台域名>.pem;
    ssl_certificate_key C:/nginx/cert/<前台域名>/<前台域名>.key;
    ssl_session_cache   shared:SSL:1m;
    ssl_protocols       TLSv1 TLSv1.1 TLSv1.2;
    client_max_body_size 20M;

    location /openapi/site/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# 3) 管理台：静态 + /api + /sso
server {
    listen 443 ssl;
    server_name console.<主域名>;
    # ssl_* 同上
    client_max_body_size 220M;        # 后端 multipart 上限是 220MB，这里要放得开
    root  C:/wczlerp/station-console-ui;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_read_timeout 300s;      # 上传大文件要久一点
        proxy_request_buffering off;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    # ⚠ 必须精确匹配 = /sso，SsoController 上没有子路径
    location = /sso {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /files/ { alias C:/wczlerp/station-upload/; add_header X-Content-Type-Options nosniff always; }
    location /      { try_files $uri $uri/ /index.html; }
    location = /index.html { add_header Cache-Control "no-cache, no-store, must-revalidate"; }
}

# 4) 后端 API：只开 openapi 和 files
server {
    listen 443 ssl;
    server_name data.<主域名>;
    # ssl_* 同上
    location /openapi/site/ { proxy_pass http://127.0.0.1:8081; /* 同样的四个 proxy_set_header */ }
    location /files/ { alias C:/wczlerp/station-upload/; add_header X-Content-Type-Options nosniff always; }
    location / { return 404; }
}
```

> **`X-Forwarded-For` 必须转发。** 询盘接口按 IP 限流（每分钟 5 条 / 每天 50 条），
> 漏了这个头所有访客在后端看来都是 `127.0.0.1`，每 IP 限流会退化成**全站限流**——
> 第 6 个客户就提交不了询盘，而后端日志上看不出任何异常。
>
> **`data.` 子域上不要开放 `/api/` 和 `/sso`。** 它们靠同源 Cookie 认身份，
> 多开一个源只会多一条要防的路径。

应用：

```powershell
Copy-Item station.conf 'C:\nginx\conf\conf.d\' -Force
cd C:\nginx; .\nginx.exe -t          # 必须 successful，失败时旧配置继续生效、服务不中断
.\nginx.exe -s reload
```

### 6.5 验收

```bash
# 证书链（TLS 0 = 验证通过）
curl -s -o /dev/null -w "HTTP %{http_code} TLS %{ssl_verify_result}\n" https://<前台域名>/
# http 跳转
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" http://<前台域名>/
# 各域名分流
curl -s -o /dev/null -w "%{http_code}\n" https://console.<主域名>/api/site     # 期望 401
curl -s -o /dev/null -w "%{http_code}\n" https://data.<主域名>/                # 期望 404
```

---

## 7. 打通「从 ERP 进入管理」

### 7.1 先理解：管理台没有登录页

`station-console-ui` **没有登录页**，这是设计不是缺失。`SessionController` 只剩一个
`POST /api/auth/logout`，`/login` 是个空页面。唯一入口是从 ERP 点「进入管理」。

**所以：部署完之后 `console.` 域名能打开、`/api/**` 返回 401，就是正确状态，不要当故障排查。**

`u_s_user` 故意是空表，第一个管理员由 `SiteUserService.resolveFromErp()` 在首次跳转时自动落。

### 7.2 协议：自包含签名令牌（不回调 ERP）

> ⚠ **旧协议是「票据 + 回调 ERP 兑换」**（原 `docs/station-console-sso.md`，2026-09-06 已删除），
> 别照 git 历史里那一份配。现在是 [boundary §4.2](./独立站与ERP的边界.md) 的新协议：
> ERP 用站点的跳转密钥签一个 HMAC-SHA256 令牌（60 秒有效），
> 通过**隐藏表单 POST** 提交到站点的 `/sso`，站点**本地验签**，全程不回调 ERP。

### 7.3 三处必须一致

| | ERP 侧 `sdsshop.u_s_site` | 站点侧 `sdsstation.u_s_siteinfo` |
| --- | --- | --- |
| 站点编码 | `sitecode` | `sitecode` —— 不一致直接拒 |
| 跳转密钥 | `consolesecret`（**AES-GCM 密文**，96 字符） | `consolesecret`（**明文**，43 字符） |
| 管理台地址 | `consoledomain` —— 跳转地址由它拼 | — |

> ### ⚠ 密钥两边存法不对称，这是最容易搞错的一格
>
> ERP 签名前用 `OSS_CONFIG_SECRET` 把库里的密文解开再签；站点验签**直接用库里的值**。
> 所以：
>
> - ✅ 正确：把跳转密钥的**明文**填进 `u_s_siteinfo.consolesecret`
> - ❌ 错误：从 ERP 库 `SELECT consolesecret` 粘过来 —— 那是 96 字符密文，
>   粘过去**不会报任何错**，只是每次签名不匹配，日志里写着"签名不对"，看着像代码 bug
>
> **密钥只能由 ERP 生成**：ERP 前端「独立站管理」→ 选中站点 →「重置跳转密钥」，
> 明文**只显示这一次**，拿到后立刻填进站点库。

ERP 侧登记项还要补全（`domain` / `serverip` / `datadomain` / `filedomain` / `buyerdomain`）。

站点侧写入密钥后**必须重启后端**（站点档案在启动时读进内存）：

```powershell
Restart-Service wczl-station-console
```

### 7.4 `SERVER_FORWARD_HEADERS_STRATEGY` 必须配

> 症状：`/sso` 验签成功后 302 到 **`http://`** 而不是 `https://`。
>
> 原因：nginx 用 `proxy_pass http://127.0.0.1:8081` 转发，后端看到的 scheme 就是 http，
> 拼重定向绝对地址时照着它拼。nginx 已经带了 `X-Forwarded-Proto`，
> 但 Spring 默认忽略这组头（防止未经反代的部署被伪造头欺骗）。
>
> 后果：跳转绕一跳明文 HTTP，而 `Secure` Cookie 在那一跳上不会发送。
>
> 顺带：开启后 `getRemoteAddr()` 才返回真实客户端 IP，询盘按 IP 限流才算得准。

### 7.5 不用 ERP 登录态就能验证

用密钥自签一个令牌打过去，成功路径和拒绝路径一起测：

```bash
SECRET='<跳转密钥明文>'
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
NOW=$(date +%s)
JSON=$(printf '{"ver":1,"iss":"erp","site":"<站点编码>","user":"probe","name":"Probe","iat":%d,"exp":%d,"jti":"j-%s"}' \
       "$NOW" "$((NOW+60))" "$RANDOM$RANDOM")
P=$(printf '%s' "$JSON" | b64url)
S=$(printf '%s' "$P" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)

curl -s -o /dev/null -D - -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=$P.$S" \
  "https://console.<主域名>/sso" | grep -iE "^(HTTP/|location:|set-cookie:)"
```

**期望**：

```
HTTP/1.1 302
Set-Cookie: CONSOLE_SESSION=...; Path=/; Secure; HttpOnly; SameSite=Lax
Location: https://console.<主域名>/          ← 必须是 https
```

同一张令牌**再打一次必须被拒**（`jti` 一次性）。另外错密钥、错 sitecode、过期令牌
也都应被拒，且**四种失败的提示文字完全相同**（不泄露是哪一步失败）。

验证完记得删掉测试落下的用户：

```sql
DELETE FROM u_s_user WHERE username = 'probe';
```

---

## 8. 文件存储

两种模式，由 `u_s_storage` 表里当前生效那一行的 `provider` 决定。

### 8.1 模式一：本机磁盘（默认，零配置）

`u_s_storage` 空表时自动走这条。文件写 `STATION_UPLOAD_DIR`，
地址用 `STATION_FILE_BASE_URL` 拼。**确保那个地址真的能取到本机文件**：

```bash
# 放一个探针再从公网取
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" https://<后端域名>/files/image/_probe/probe.png
```

### 8.2 模式二：腾讯云 COS

**代码支持情况**：只实现了 `tencent` 和 `local`。`aliyun` / `huawei` 在表结构和前端下拉里
存在但**没有实现**，选了会被明确拒绝（刻意不静默降级到本机磁盘，那样是死链）。

**先配加密主密钥**（存储密钥要用它加密入库）：

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)
[IO.File]::WriteAllText('C:\wczlerp\.config-secret', $secret, (New-Object Text.UTF8Encoding $false))
icacls 'C:\wczlerp\.config-secret' /inheritance:r /grant 'Administrators:F' /grant 'SYSTEM:F'
# 然后把它作为 STATION_CONFIG_SECRET 加进服务环境变量并重启
```

> ⚠ **主密钥一旦有存储密钥入库就不能再换。** 换了以后已存的密钥全部解不开，
> 表现是上传时报"解密失败"。妥善备份 `.config-secret`。

**在管理台配置**（不用写 SQL，密钥不必经过部署方）：
管理台 →「站点概览」页 →「文件存储」卡 → 右上角「修改」：

> ⚠ 2026-09-07 之前这一屏在「文件」页右上角那个「存储设置」弹窗里。
> 弹窗和按钮都删了，配置项原样搬进了站点概览——跟站点设置、汇率
> 摆在同一页，一个站的一次性配置在一屏里改完。接口（`/api/file/storage`）一字未动。

| 字段 | 填什么 |
| --- | --- |
| 厂商 | 腾讯云 |
| 地域 Region | 桶的英文代号，如 `ap-shanghai`（控制台"所属地域"括号里那个） |
| SecretId / SecretKey | 建议用**子账号**密钥，只授这一个桶的读写 |
| 存储桶名称 | **完整桶名**，形如 `name-1300000000`（APPID 已在后半截，不用再拼） |
| 自定义域名 | `https://file.<主域名>` —— ⚠ **只填到域名为止** |

> ### ⚠ 「自定义域名」这一栏的两个坑
>
> **一、只填到域名为止。** 地址是 `这一栏 + / + 对象键` 拼出来的，而对象键本身就是
> `image/2026/09/06/xxx.png`。填成 `https://file.x.com/files` 会拼出
> `https://file.x.com/files/image/…`，桶里没有这个键，每张图都是 404。
> 那个 `/files` 是**本机磁盘模式**下 `data.` 子域的挂载路径，别抄过来。
>
> **二、它只对 `provider=tencent` 生效。** 本机磁盘模式下地址来自环境变量
> `STATION_FILE_BASE_URL`，这一栏填了不会生效——管理台现在会把它置灰并说明原因，
> 但知道这件事仍然有用：**改本机磁盘模式的地址要改环境变量并重启服务，不是改这个弹窗**。

> ### 上传走内网加速（可选，强烈建议）
>
> 链路是「浏览器 → 这台服务器 → COS」，同一份数据在服务器网卡上进出各一次，
> 而这台机器公网只有 3 Mbps——一个 200 MB 的视频光是第二段就要十几分钟。
> CVM 和桶都在上海，把 `endpoint` 列填成内网域名即可，不限速也不计流量费：
>
> ```sql
> UPDATE u_s_storage SET endpoint = 'cos-internal.ap-shanghai.myqcloud.com' WHERE provider = 'tencent';
> ```
>
> （管理台在腾讯云那一档不显示 Endpoint 输入框，所以这一项只能走 SQL。
> 跨地域时内网域名连不上，表现是上传超时——那时候把这一列清空就退回公网。）
>
> ⚠ 用了内网 endpoint 就**务必把「自定义域名」也填上**。两者都不填的话，
> 对外地址会回落到 COS 默认域名，而默认域名是从 endpoint 推的——推出来是内网域名，
> 于是每张图的地址都只有腾讯云内网能解析，访客一张都打不开。
> 代码里已经做了保护（`SiteFileService.publicCosHost` 会把 `cos-internal.` 换回 `cos.`），
> 但别指望它：填一个自有域名是本来就该做的事。

**桶权限必须允许公开读**。验证域名绑定是否正确：

```bash
curl -s https://file.<主域名>/__not_exist__.jpg | head -c 200
```
- 返回 `NoSuchKey` + 404 → 域名绑定正确、桶可公开读 ✅
- 返回 `AccessDenied` → 桶是私有的，或域名没绑对

> ### 保存时会自动做一次连通性自检
>
> 点「保存」之后后端会真的传一个探针对象上去、再<b>按访客的方式匿名取回来</b>
> （`SiteFileService.verifyStorage`）。不通就**拒绝保存**，配置根本不会落库。
> 所以保存这个动作会慢两三秒，这是有意的：
>
> - PUT 失败 → SecretId/Key、桶名、地域有问题，错误提示里会直接说是哪一项
> - GET 失败 → 自定义域名没绑对、证书不对，或者**桶不是公开读**
> - GET 回 200 但内容对不上 → 这个域名指向了别的服务，不是这个桶
>
> 自检拦下的每一种情况，放过去的后果都是同一个：上传成功、库里存下完整地址、
> **每张图都是死链**，而且要等到有人打开页面才发现。
>
> 探针对象留在桶里 `__probe/` 下（一次保存一个几十字节的文件），
> 和孤儿对象一起定期清即可。

### 8.3 从本机磁盘切到 COS：搬历史文件 + 重写历史地址

⚠ **切换之前上传的文件不会自己搬家。** 地址是上传那一刻整串固化进库的，
切到 COS 只影响之后传的；老记录仍然指着老地址。顺序不能乱：

```
① 冻结上传（通知使用方，或临时收走 site:file:upload 权限）
② 在管理台按 §8.2 填好 COS 配置并保存（自检通过 = 新地址确实能取到东西）
③ 把老文件按原相对路径搬进桶：
     scripts\station\upload-to-cos.ps1
④ 抽查：curl -I https://file.<主域名>/image/2026/09/04/<某个真实文件>  → 期望 200
⑤ 重写历史地址：
     sql/station/migration/2026-09-06-file-url-rebase.sql
     （改开头那两行前缀，然后从头跑到尾——它自带空跑、溢出预检、备份和验收查询）
⑥ 解冻，打开前台逐页看一遍
```

第 ③ 步必须在 ⑤ 之前：先有对象再改地址，中间任何时刻页面都不会裂。

**搬文件**（在服务器上跑，带 `-Internal` 走内网——这台机器公网只有 3 Mbps）：

```powershell
$env:COS_SECRET_KEY = '<SecretKey>'
C:\wczlerp\scripts\upload-to-cos.ps1 -Root C:\wczlerp\station-upload `
    -Bucket kangchen-1479674498 -Region ap-shanghai -SecretId <SecretId> `
    -PublicBase https://file.kangchen.vclinker.com -Internal -DryRun
# 看一眼要传什么，没问题就去掉 -DryRun 再跑一次
Remove-Item Env:\COS_SECRET_KEY
```

脚本可以重复跑：已经在桶里的会被跳过（它用匿名 GET 判断，顺带证明了访客真能取到）。
有文件失败时退出码非 0，**别在有失败的情况下去跑第 ⑤ 步**——漏掉的那些跑完就是死链。

> ⚠ `upload-to-cos.ps1` 存的是 **UTF-8 with BOM**。Windows PowerShell 5.1 读没有 BOM 的
> .ps1 会按系统 ANSI 代码页解码，中文注释被 GBK 拆开之后会吞掉换行，
> 报一串行号指向空行的 `Unexpected token '}'`。改这个文件时确认 BOM 还在。

**重写地址**：地址被复制到了六处，不只是文件库那两张表——商品的 `siteimgs` /
`sitevideos` / `imgurl`、详情富文本 `description`、首页装修 `contentjson` 里都有。
那个 SQL 脚本里逐个列了，照着跑即可。`u_s_orderitem.imgurl` 是下单快照，**故意不动**。

> ### 实现上的三个要点（改代码时别丢）
>
> 1. 上传显式设 `CannedAccessControlList.PublicRead`。不设的话对象继承桶权限，
>    私有桶会导致"上传成功、前台 403"，而这个失败要等到有人看页面才发现。
> 2. `ObjectMetadata.setContentLength` 必须设，否则 SDK 为了算长度把整个流读进内存
>    （视频上限 200 MB）。
> 3. `COSClient` 用完必须 `shutdown()`，否则泄漏连接池线程。
>
> ### 两个已经踩过的坑（在 `SiteStorage.java` 的注释里也记着）
>
> - **`accessKeySecret` 上不能用 `@JsonIgnore`**。字段上的 `@JsonIgnore` 会把
>   序列化和反序列化**一起**屏蔽，于是前端填的 SecretKey 在 `@RequestBody` 那步就被丢掉，
>   `save()` 拿到 null 当成"本次不改密钥"——表现是"保存成功了但密钥一直没存进去"，
>   页面上"还没有配置对象存储"的提示怎么保存都消不掉。要用
>   `@JsonProperty(access = Access.WRITE_ONLY)`：写得进来，读不出去。
> - **不要给 `active` 加 `isActive()` 便利方法**。它和 `getActive()` 在 JavaBeans 眼里是
>   同一属性的两个 getter，返回类型一个 String 一个 boolean，MyBatis 的 `Reflector`
>   直接抛 `Illegal overloaded getter method with ambiguous type`，
>   于是 `upsert` 一取值就炸——**整张表一行都写不进去**，而表现只是保存时一句
>   "系统异常，请稍后重试"，堆栈里完全看不出和这个方法有关。

---

## 9. 验收清单

逐项打勾，全绿才算部署完成。

**服务与自启**

```powershell
Get-Service MySQL8, Redis, wczl-station-console, wczl-station-site |
    Format-Table Name, Status, StartType -AutoSize      # 全部 Running + Automatic
schtasks /query /tn nginx-autostart /v /fo LIST | findstr /C:"Status" /C:"Schedule Type"
```

**监听地址**（对外只能有 80/443）

```powershell
Get-NetTCPConnection -State Listen |
    Where-Object { $_.LocalPort -in 80,443,3000,3306,6379,8081 } |
    Select-Object LocalAddress, LocalPort | Sort-Object LocalPort
```

期望：

```
0.0.0.0    80        nginx
0.0.0.0    443       nginx
127.0.0.1  3000      station-site
127.0.0.1  3306      MySQL
127.0.0.1  6379      Redis
127.0.0.1  8081      station-console
```

**公网可达性**（在部署机上跑，不是服务器上）

```bash
for p in 8081 3000 3306 6379; do
  timeout 6 bash -c "cat < /dev/null > /dev/tcp/<服务器IP>/$p" 2>/dev/null \
    && echo "$p 开放 ← 有问题" || echo "$p 不可达 ← 正确"
done
```

**HTTPS 与分流**：见 §6.5

**SSO**：见 §7.5（含四条拒绝路径）

**⚠ 整机重启验证**：所有自启配置都确认过之后，**找个没人用的时候真的重启一次**。
"配置正确"和"实际能起来"是两回事，`schtasks` 的 `Last Run Time` 显示 `1999/11/30`
就说明那个任务从未真正触发过。

---

## 10. 排查表

| 症状 | 先看这里 |
| --- | --- |
| `mysqld --initialize` 退出码 -1073741515，无输出 | 缺 `VCRUNTIME140_1.dll`，装 VC++ 运行库（§2.1） |
| `redis-server --service-install` 报 `unknown conf file parameter` | conf 里有 3.0.503 不认的参数（如 `maxheap`），删掉 |
| `nginx: [emerg] could not build server_names_hash` | 加 `server_names_hash_bucket_size 64`（§6.3） |
| SSH 密钥不生效、仍然要密码 | `administrators_authorized_keys` 带了 BOM，或权限没收紧（§1.3） |
| PowerShell 脚本报一堆"字符串缺少终止符" | `.ps1` 是 UTF-8 无 BOM，中文注释被 GBK 解坏（§0.3） |
| 脚本中断在一条 mysql **警告**上 | 命令行传密码触发 `NativeCommandError`，改用 `$env:MYSQL_PWD`（§4.1） |
| 前台整站 500 / 管理台进不去 | `u_s_siteinfo` 没有那一行（§4.3） |
| 前台能开但没有数据 | 业务表的 `sitecode` 和 `u_s_siteinfo.sitecode` 对不上（§4.3） |
| 「进入管理」提示令牌无效 | 三处不一致之一：sitecode / 密钥（明文 vs 密文）/ 改完没重启（§7.3） |
| SSO 成功但跳到 `http://` | 缺 `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`（§7.4） |
| 管理台 `/api/**` 返回 401 | **这是正确状态**，管理台没有登录页（§7.1） |
| 第 6 个客户提交不了询盘 | nginx 没转发 `X-Forwarded-For`，按 IP 限流退化成全站限流（§6.4） |
| 存储设置"保存成功但密钥没存进去" | `accessKeySecret` 上误用了 `@JsonIgnore`（§8.2） |
| 存储设置保存时"系统异常" | `SiteStorage` 上有 `isActive()` 与 `getActive()` 冲突（§8.2） |
| 图片上传成功但前台 403 | 桶是私有的，且对象没设 `PublicRead`（§8.2） |
| 图片地址打不开（404/域名不对） | `STATION_FILE_BASE_URL` 或 `public_url` 指向了取不到文件的地方（§8.1） |
| 前端构建 `JavaScript heap out of memory` | 别在 4 GB 服务器上构建，改本机构建（§5.2） |

---

## 附录 A：网络限制与可用下载源

在国内云服务器上实测（2026-09-05）。**下载任何东西前先确认源可达，别默认用官方地址。**

**不通**

| 源 | 表现 |
| --- | --- |
| `github.com` | 超时。Release 文件、SDK、工具全部拿不到 |
| GitHub 加速代理 | ghfast.top / gh-proxy.com / ghproxy.net / moeyy / kkgithub 等**全部失效**（返回错误页或域名解析失败） |
| `nssm.cc` | 超时 |
| Adoptium 国内镜像 | 清华 / 北外 / 南大 / 中科大：能列目录，**文件 GET 一律 403** |
| `registry.npmjs.org` | 不通 |
| `mirrors.tencentyun.com` | 没有 MySQL 仓库 |

**可用**

| 用途 | 地址 | 实测 |
| --- | --- | --- |
| MySQL | `mirrors.huaweicloud.com/mysql/Downloads/MySQL-8.0/` | 36 MB/s |
| Node.js | `mirrors.huaweicloud.com/nodejs/` | 快 |
| Maven | `mirrors.huaweicloud.com/apache/maven/maven-3/` | 9 MB / 0.5s |
| JDK | `aka.ms/download-jdk/...`（微软 OpenJDK）或 `corretto.aws` | 178 MB / 15s |
| VC++ 运行库 | `aka.ms/vs/17/release/vc_redist.x64.exe` | 24 MB / 3s |
| Nginx | `nginx.org/download/` | 通但慢（2 MB / 113s） |
| Redis / nssm | `nuget.org` 和 `community.chocolatey.org` 的 API | 可用 |
| npm 包 | `registry.npmmirror.com` | 可用 |
| Maven 依赖 | `maven.aliyun.com/repository/central` | 60ms（直连 central 是 832ms） |

> **需要 GitHub 上的文件时**：在本机（能挂代理）下载，再通过 RDP 驱动器重定向
> 或 `scp` 传上去。不要在服务器上和 GitHub 死磕。

---

## 附录 B：常用运维命令

```powershell
# --- 服务 ---
Restart-Service wczl-station-console
Restart-Service wczl-station-site
Get-Service MySQL8, Redis, wczl-station-console, wczl-station-site

# --- 改服务的环境变量 ---
nssm edit wczl-station-console                    # 图形界面，Environment 标签页
nssm get  wczl-station-console AppEnvironmentExtra
# ⚠ nssm set ... AppEnvironmentExtra 是**整体覆写**，不是追加。
#   要改一项也得把整份列表重新给一遍，否则其他项会丢

# --- nginx ---
cd C:\nginx; .\nginx.exe -t          # 改完必先测，失败时旧配置继续生效
cd C:\nginx; .\nginx.exe -s reload
cd C:\nginx; .\nginx.exe -s stop

# --- 日志 ---
Get-Content C:\wczlerp\logs\station-console.out.log -Tail 50 -Wait
Get-Content C:\wczlerp\logs\station-site.out.log    -Tail 50

# --- 数据库 ---
$env:MYSQL_PWD='<密码>'; & C:\mysql\bin\mysql.exe -h 127.0.0.1 -u station -D sdsstation
& C:\mysql\bin\mysqldump.exe -h 127.0.0.1 -u root --databases sdsstation `
  --default-character-set=utf8mb4 --single-transaction | Out-File C:\backup\sdsstation.sql -Encoding utf8

# --- Redis ---
C:\redis\redis-cli.exe ping
C:\redis\redis-cli.exe info memory
```

**发新版本**（改了代码之后）：

```bash
# 后端
scp station-console-src.zip Administrator@<IP>:C:/build/
ssh Administrator@<IP> 'cd C:\build\station-console; mvn -B clean package -DskipTests'
ssh Administrator@<IP> 'Stop-Service wczl-station-console; Copy-Item C:\build\station-console\target\station-console-1.0.0.jar C:\wczlerp\station-console\ -Force; Start-Service wczl-station-console'

# 前端（本机构建后）
scp console-ui-dist.zip Administrator@<IP>:C:/build/
ssh Administrator@<IP> 'Expand-Archive C:\build\console-ui-dist.zip -DestinationPath C:\wczlerp\station-console-ui -Force'
```

> 换 jar 前先备份旧的：`Copy-Item <jar> <jar>.bak-$(Get-Date -f yyyyMMdd-HHmmss)`。

---

## 附录 C：本次部署的实际值

**换站部署时，把这张表整个替换掉。**

### 康辰站 · 2026-09-05

| 项 | 值 |
| --- | --- |
| 服务器 | `49.234.194.194`，Windows Server 2016 Datacenter，4 GB / 2 vCPU |
| 主域名 | `kangchen.vclinker.com` |
| 前台 | `buyer.kangchen.vclinker.com` ← **康辰是单 host 布局**：前台整站挂在 buyer 子域，主域在 nginx 上没有 server 块（也没有证书，腾讯云免费 DV 严格单域名）。统领站已经改成两 host（主域公开页 + `buyer.` 账号页，见 `deploy/nginx/tongling.conf`）；康辰要迁的话四件事一起改，见 `deploy/nginx/station.windows.conf.template` 文件头 |
| 管理台 | `console.kangchen.vclinker.com` |
| 后端 API | `data.kangchen.vclinker.com` |
| 文件 | `file.kangchen.vclinker.com` → CNAME 到腾讯云 COS，**不经本机 nginx** |
| 站点编码 | `DLZ2049683802` |
| 商户码 | `sp708xm02q` |
| 库 / 账号 | `sdsstation` / `station` |
| 证书 | TrustAsia DV 单域名，**2026-12-04 到期**（90 天） |
| COS 桶 | `kangchen-1479674498`，地域 `ap-shanghai`，公有读私有写 |

**组件版本**

| 组件 | 版本 | 位置 |
| --- | --- | --- |
| MySQL | 8.0.28 | `C:\mysql` |
| Redis | 3.0.503 | `C:\redis` |
| Nginx | 1.26.2 | `C:\nginx` |
| JDK | Microsoft OpenJDK 17.0.20.1 | `C:\jdk17` |
| Node.js | 22.23.2 / npm 10.9.8 | `C:\nodejs` |
| Maven | 3.9.16 | `C:\maven` |
| nssm | 2.24-101 | `C:\Windows\System32` |

**内存占用**（4 GB 机器，稳态约 2.6 GB）

```
java (station-console)  ~320 MB
mysqld                  ~210 MB
node (station-site)      ~68 MB
redis-server             ~28 MB
nginx (2 进程)           ~18 MB
```

### 本站遗留问题

- [ ] **COS 尚未配置** —— `u_s_storage` 是空表，当前走本机磁盘。代码已支持，
      按 §8.2 在管理台填一次即可。⚠ 桶那侧 2026-09-06 实测**已经就绪**：
      `file.kangchen.vclinker.com` CNAME 到 `kangchen-1479674498.cos.ap-shanghai.myqcloud.com`，
      取一个不存在的对象回 `NoSuchKey` 404（不是 `AccessDenied`）——
      域名绑定、证书、公有读三项都对，差的只是填一次密钥
- [ ] **切 COS 之后要跑 §8.3** —— 已经传过的文件不会自己搬家，
      老记录里存的还是 `http://127.0.0.1:8081/files/...`（回环地址，出了那台机器打不开）。
      搬文件用 `scripts/station/upload-to-cos.ps1`，改地址用
      `sql/station/migration/2026-09-06-file-url-rebase.sql`
- [ ] **未配 SMTP** —— 询盘只入库不发信；买家注册和找回密码会直接报错。
      配置方式是往 jar 同目录放一个 `application-mail.yml`
- [ ] **整机重启未验证** —— 各项自启配置已逐项确认，但服务器从未重启过
- [ ] **`u_s_siteinfo` 的语言是 `zh,pt,it,ko`、站名"中文主站"** —— 对面向海外买家的站，
      默认语言 `zh` 大概不合适，等管理台能进去后改
- [ ] **迁移带来的杂质** —— `u_s_sitegoods` 里有 2 条 `sitecode=DLZ2049651303` 的记录，
      属于另一个站，按 sitecode 过滤不会显示，留着无害

---

## 附录 D：改早了会出事的三个地方

> 2026-09-06 从 `kangchen-domain-plan.md` §14/§15 搬过来的（那一份已删除，它的落地步骤
> 已被本手册取代，但下面这几条**和部署顺序有关，还成立**）。原文写的是阿里云 OSS +
> `kangchen.com` 三域名，这里按现在的实际情况（腾讯云 COS + `*.vclinker.com`）改过。

### D.1 站点的文件域名是**新增**一套存储，不是把 ERP 那套换掉

两套并存，各管各的：

| 谁的文件 | 存哪 | 配在哪 |
| --- | --- | --- |
| ERP 自己的（商品主数据、ERP 里传的附件） | `file.erp.vclinker.com`（阿里云 OSS） | ERP 全局 `ruoyi.oss.aliyun.public-url`，**不动** |
| 独立站的（以及以后每个客户的） | `file.<站点域名>`（本次是腾讯云 COS） | 该站自己的存储配置里那一行 `publicUrl`（见 [§8.2](#82-模式二腾讯云-cos)） |

> ### ⚠ `file.erp.vclinker.com` 的 OSS 绑定永远不要撤
>
> 撤了 **ERP 的历史图片全部 404**。那些是 ERP 自己的文件，本来就该留在那个域名下，
> 不需要跟着独立站迁移。`RuoYi-Vue/.../application.yml` 里 `public-url` 那一行同理，
> 本次和以后都保持 `https://file.erp.vclinker.com`。

早期方案把这件事理解成「把 `file.erp` 换成站点的域名」，于是有了「改 `public-url` 只影响
新文件、库里老记录要全库替换」这一整套麻烦。**按并存来做，那个问题不存在。**

**绑定域名的顺序**（顺序反了就是新传的图全 404）：

1. 在对象存储控制台给这个站用的桶绑定自定义域名 `file.<站点域名>`
2. DNS 加 **CNAME**，值是对象存储给的那个地址（**不是**服务器 IP）
3. 证书传到对象存储控制台（自定义域名侧托管，本机 nginx 不经手）
4. 浏览器直接开一张图的地址，确认取得到
5. 上面四步都过了，**才**去把该站存储配置里的 `publicUrl` 填成 `https://file.<站点域名>`

几条附带的：

- 桶必须**公共读**，私有桶拼出来的地址前台打不开（[§8.2](#82-模式二腾讯云-cos)）
- `file.` 是站点根域的子域，备案跟着根域一起下来；桶开在境内节点同样要求备案
- 桶如果开在**客户自己的云账号**下，绑定、证书、续期都是他们那边做，
  你这边只拿到一个 `publicUrl` 往存储配置里填
- **`STATION_CONFIG_SECRET` 必须在第一次配置存储之前设好。** 换了它，所有已存的
  存储密钥都解不开，得重填一次

### D.2 单域名证书不覆盖 `www`

腾讯云免费 DV 是**严格单域名**，不附送 `www`。`<主域名>` 那张证书只覆盖它自己。

**在解决之前不要加 `www` 的 A 记录** —— 解析通了证书没有，用户撞的是证书错误页。
三选一：再申一张 `www` 的证书、干脆不解析 `www`、或者用 win-acme 一张多 SAN 全包。

### D.3 独立站 CORS 与同源访问

独立站后端是 `station-console`，不是 ERP。生产环境推荐让前台、管理台和后端通过本站
Nginx 同源访问，因此默认不需要 CORS。只有前台确实部署在不同根域时，才在
`station.cors.allowed-origins` 中写入明确的前台来源；不要填 `*`。

按路径看，独立站后端的边界是：

| 路径 | 允许的来源 | 为什么 |
| --- | --- | --- |
| `/openapi/site/**` | 默认不跨域；需要跨域时只允许明确配置的站点来源 | 独立站前台访问本站 `station-console` 的公开接口；一个部署一个站，不再使用 URL 上的 `siteKey` |
| `/api/**` | 不开放跨域 | 独立站管理台使用本站会话 Cookie，前台静态文件和后端应保持同源 |

> 生产推荐让前台、管理台和后端通过 nginx 同源访问。公开接口不依赖管理台会话，
> 真正的防护是只返回已上架内容、询盘/查单限流和服务端校验，而不是依赖 CORS 隐藏接口。

⚠ 管理台和前台都走本站域名转发到 8081，从来不跨域打 ERP。

### D.4 ERP 那台机器上遗留的 nginx 缺陷

不是独立站引入的，但在同一套域名体系下，建议一并处理。ERP 侧的 nginx 配置快照在
[`deploy/nginx/erp/nginx.production.conf`](../deploy/nginx/erp/nginx.production.conf)，
改线上前先比对，改完同步回来。

| 位置 | 问题 | 建议 |
| --- | --- | --- |
| **ERP 线上 `nginx.conf`** | **没有 `default_server`（catch-all）** | 80/443 的第一个 server 是 `data.erp.vclinker.com`，它就成了默认 server：**任何 Host 头对不上的请求——包括直接用 IP 访问、扫描器的随机 Host——都被转进 `http://localhost:8080/`**，等于 ERP 后端对任意 Host 开放。加一个 `return 444` 的 catch-all，443 上用 `ssl_reject_handshake on`（nginx 1.19.4+，先 `nginx -V` 看版本）。片段在 `nginx.production.conf` 末尾 |
| ERP 各 server | `ssl_protocols TLSv1 TLSv1.1 TLSv1.2` | TLS 1.0/1.1 现代浏览器已拒绝，改成 `TLSv1.2 TLSv1.3` |
| ERP 各 server | `listen 443 ssl` 未启用 http2 | 加 `http2` |
| ERP 各 server | `ssl_ciphers` / `ssl_session_*` / `ssl_protocols` 四份完全重复 | 提到 `http {}` 级别，各 server 只留 `ssl_certificate` 两行。不然改的时候会漏掉某一个 server，出现版本漂移 |

---

## 附录 E：ERP 前端（console.erp）首屏加载优化怎么落地

> 背景、实测数据、字节账、以及"为什么拆包只省 0.3 秒还值得做"都在
> [`设计稿/2026-09-23-ERP首屏加载优化.md`](./设计稿/2026-09-23-ERP首屏加载优化.md)。
> 这里只讲怎么在 82.156.158.174 上把它做出来。
>
> 一句话：**nginx 加两行（`gzip` + `gzip_static`），首屏传输 2,204 KB → 604 KB，
> 冷加载约 19 秒 → 5 秒**；前端拆包是第二次发版才需要做的事。

### E.1 nginx（先做，零代码改动，收益的绝大部分）

> **首选：跑脚本，别手工改。** 配置里 `console.erp` 出现**两次**（80 重定向 + 443），
> `data.erp` 还有一个一模一样的 `listen 443 ssl;`，手工改很容易动错 vhost。
> `deploy/scripts/step7-erp-nginx-gzip.ps1` 把下面 ①②③ 步一次做完，而且
> **找不到锚点就一个字都不写、`nginx -t` 不过就自动还原备份**：
>
> ```powershell
> # 从开发机拷进 RDP 会话（仓库在 D 盘，mstsc 的「本地资源→驱动器」要把 D 盘勾上）
> Copy-Item '\\tsclient\d\wczl-erp\deploy\scripts\step7-erp-nginx-gzip.ps1' C:\build\ -Force
> powershell -ExecutionPolicy Bypass -File C:\build\step7-erp-nginx-gzip.ps1
> ```
>
> 跑到 `RESULT: CONFIG OK` 才算成，然后做 ③ 的 reload。下面的手工步骤留作参考，
> 以及脚本报 `ANCHOR NOT FOUND`（线上文件和预期不一致）时的对照。

> ⚠ **这台机器上所有 nginx 命令都要先 `cd C:\nginx`**（§8.2 就是这么写的）。
> 这个 Windows 版 nginx 按**当前目录**找 `conf/nginx.conf` 和 `logs/`，在别处执行
> `C:\nginx\nginx.exe -t` 会去找 `C:\Users\Administrator/conf/nginx.conf` 并报
> `[emerg] CreateFile() ... failed (3: ...)`。**那不是配置写错了，是目录不对。**
> 2026-09-23 第一次落地时在这里卡了一轮；`-s reload`（要读 `logs/nginx.pid`）
> 和 `-V` 一并照做，省得记哪些命令可以不加。

**① 先确认这个 nginx 带 `gzip_static` 模块**（官方 Windows 构建带，但别的构建不一定）：

```powershell
cd C:\nginx; .\nginx.exe -V 2>&1 | Select-String gzip_static
```

- 有输出 → 按下面的完整版本粘。
- 没输出 → `nginx -t` 会直接报 `unknown directive "gzip_static"`，
  **把 `nginx.console-erp.conf` 里 `gzip_static` 那一行删掉**再粘，
  只靠 `gzip on` 现场压（效果差一些，但不会起不来）。

**② 备份 + 改配置。** 要粘的内容在
[`deploy/nginx/erp/nginx.console-erp.conf`](../deploy/nginx/erp/nginx.console-erp.conf)，
分两段：第 1 段进 `http {}`（放 `sendfile` 那几行旁边），第 2 段替换掉原来
`console.erp.vclinker.com` 的两个 `server`。

```powershell
Copy-Item C:\nginx\conf\nginx.conf "C:\nginx\conf\nginx.conf.bak-$(Get-Date -Format yyyyMMdd-HHmmss)"
notepad C:\nginx\conf\nginx.conf
```

> ⚠ 这文件里有 `stream{}` 和 `http{}` 两个顶层块。加网站的 `server` 要进 `http{}`，
> 粘进 `stream{}` nginx 起不来。
>
> ⚠ `location = /index.html` 那条 `no-cache` **不能省**：`/static/` 一旦
> `immutable` 缓存 1 年，index.html 又被缓存住，发版后用户会拿旧入口去要已经被
> 换掉的 chunk，直接白屏。两条是成对的。

**③ 检查通过再 reload**（`-t` 不过千万别 reload）：

```powershell
cd C:\nginx; .\nginx.exe -t          # 必须 test is successful
.\nginx.exe -s reload
```

**④ 验。** 回到开发机（本仓库）跑只读体检：

```powershell
node tools\check-console-erp-cache.mjs
```

预期 **11 项全过**：`/login` 带 `no-cache` 且也走了 gzip；HTTP/2 协商出 `h2`；
两个静态资源带 `content-encoding: gzip` + `cache-control: ... immutable`；
**gzip 解压后与原始文件 sha256 一致**（这条查的是 Windows 上 sendfile + gzip_static
会不会把文件发坏，只看 `Content-Length` 查不出来）；首屏传输约 633 KB。

2026-09-23 实测：改之前 11 项过 3 项、首屏 2,204 KB / 约 17.6 秒；
改完 11/11、首屏 **633.5 KB / 约 5.1 秒**。

> 浏览器验证时要**开 DevTools 勾 Disable cache** 再看一次 Network，
> 否则看到的是本地缓存里的旧响应（尤其 `immutable` 生效之后）。

### E.2 前端 dist（第二次发版：这次带上拆包）

只有在 nginx 那步验过之后再做，两件事分开做才好回退。

```powershell
# 1) 构建（在开发机，不要在服务器上——那台 4 GB 内存不够）
cd D:\wczl-erp\RuoYi-Vue3
npm run build:prod

# 2) 打包。⚠ 必须含 .gz 文件（gzip_static 就靠它们），Compress-Archive 默认全收
if (Test-Path C:\build\web-dist.zip) { Remove-Item C:\build\web-dist.zip -Force }
Compress-Archive -Path dist\* -DestinationPath C:\build\web-dist.zip -CompressionLevel Optimal

# 3) 传上去
scp C:\build\web-dist.zip Administrator@82.156.158.174:C:/build/
scp D:\wczl-erp\deploy\scripts\step6-erp-web.ps1 Administrator@82.156.158.174:C:/build/

# 4) 在服务器上换（移开旧目录 → 解压 → 核对 index.html 引用 → 探活）
ssh Administrator@82.156.158.174 "powershell -ExecutionPolicy Bypass -File C:\build\step6-erp-web.ps1"
```

> ⚠ 换的过程中有几秒 `C:\wczlerp\web\dist` 是空的，正在用 ERP 的人会刷出 404。
> 挑没人的时候做。旧目录会留在 `C:\build\web-dist-bak-<时间戳>`，回退就是移回来。

```powershell
# 5) 验（带上本地 dist，脚本会逐个文件比对 sha256）
cd D:\wczl-erp
node tools\check-console-erp-cache.mjs --local RuoYi-Vue3\dist
```

### E.3 顺手把快照同步回仓库

线上这份 `nginx.conf` 改完之后，`deploy/nginx/erp/nginx.production.conf` 就过期了
（那份是"线上长什么样"的快照，用来在下次改动前比对的）。

⚠ **这台机器上没有 sshd**（22 端口从来没开过，腾讯云账号也不在这边），所以**别用 scp**。
在服务器的远程桌面里把文件转成 base64 打出来，粘到开发机解码即可——base64 是纯 ASCII，
不吃剪贴板的编码、也不会被 CRLF 改写：

```powershell
# 服务器上：
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\nginx\conf\nginx.conf'))
```

```powershell
# 开发机上（把上一段输出粘进 $b64）：
$b64 = '<粘贴>'
[IO.File]::WriteAllBytes('D:\wczl-erp\deploy\nginx\erp\nginx.production.conf',
    [Convert]::FromBase64String($b64))
```

然后**手工做三件事**：把开头「快照时间」改成当天；把「现状」里
console.erp 那条划掉；把这次已经落地的「已知问题 ⑤」标注成已修；
并把快照里现在**多余的**整段注释（线上文件里并没有那些中文注释）删掉，
免得下次又拿一份"文档版"当"线上版"比对。

### E.4 回退

```powershell
# 配置（⚠ 先 cd，理由见 E.1 开头）
Copy-Item C:\nginx\conf\nginx.conf.bak-<时间戳> C:\nginx\conf\nginx.conf -Force
cd C:\nginx; .\nginx.exe -t; .\nginx.exe -s reload

# dist
Move-Item C:\wczlerp\web\dist C:\build\web-dist-failed-<时间戳>
Move-Item C:\build\web-dist-bak-<时间戳> C:\wczlerp\web\dist
```

---

## 相关文档

| 文档 | 讲什么 |
| --- | --- |
| [独立站与ERP的边界.md](./独立站与ERP的边界.md) | **为什么这么分**：七条改动要点、数据边界、跳转协议的设计推理。**边界的唯一权威** |
| [独立站产品化设想（未落地）.md](./独立站产品化设想（未落地）.md) | **改成什么样、为什么**：系统形态、数据模型、里程碑；§八bis 是「为什么一站一服务器、放哪、多少钱」，§九 是 N 台机器的运维代价 |
| [独立站部署手册（Linux）.md](./独立站部署手册（Linux）.md) | 同一套东西装在 Linux / Ubuntu 上（systemd + `/etc/nginx/conf.d`） |
| [station-site/README.md](../station-site/README.md) | 前台为什么是 SSR、产物为什么是 `.output` |

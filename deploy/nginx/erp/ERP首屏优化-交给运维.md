# 交给运维：ERP 前端首屏从约 18 秒降到约 5 秒（改 nginx，5 分钟）

> 这份文件是**可以直接转发给管服务器的人**的说明。不需要腾讯云账号，不需要 SSH，
> 全程在服务器的远程桌面上完成，一条脚本 + 一条 reload 命令。

## 一、问题是什么（一句话）

`console.erp.vclinker.com` 冷缓存打开登录页要等约 18–20 秒。**不是接口慢**，是 nginx
把 1.79 MB 的入口 JS 原样不压缩发出去，而这台机器的出口带宽只有约 1 Mbps（实测
120–130 KB/s），光这一个文件就要 15 秒。

而压缩包**早就打好了**，就躺在原文件旁边没人用：

| 磁盘上的文件 | 大小 |
| --- | --- |
| `C:\wczlerp\web\dist\static\js\index-*.js` | 1,788,693 字节 |
| 同目录 `index-*.js.gz` | **583,170 字节** ← nginx 一直没用它 |
| `static\css\index-*.css.gz` | **65,500 字节** |

缺的只是 nginx 里两行开关：`gzip_static on;`（用它旁边的 .gz）和 `gzip on;`（兜底）。

## 二、要做什么（两件事）

1. **跑一个脚本**（它会：备份 `nginx.conf` → 在 `http{}` 里加 gzip/gzip_static →
   给 console.erp 那个 vhost 打开 http2、加 `location /static/` 一年缓存、
   `location = /index.html` 不缓存 → 打印改动 → 跑 `nginx -t` → **不通过就自动还原**）。
2. **`nginx -t` 通过后 reload**（脚本不会自己 reload，这一步留给人）。

## 三、具体命令（在服务器的远程桌面里，管理员 PowerShell）

脚本文件：`step7-erp-nginx-gzip.ps1`（纯 ASCII，可直接粘贴内容，或用 RDP 驱动器重定向拷进去）

```powershell
# ① 跑脚本（它自己会备份、自己验、失败自己还原，不会把坏的配置留在磁盘上）
powershell -ExecutionPolicy Bypass -File C:\build\step7-erp-nginx-gzip.ps1

# ② 上一步最后必须打印 RESULT: CONFIG OK，然后才 reload
#    ⚠ 必须先切目录：这个 Windows 版 nginx 是**按当前目录**找 conf/ 和 logs/ 的，
#      在别的目录下执行会报 "CreateFile() C:\Users\...\conf\nginx.conf failed"
Set-Location C:\nginx
.\nginx.exe -s reload
```

**如果脚本报 `ANCHOR NOT FOUND: ...`**：说明线上 `nginx.conf` 和预期不一致，
**它一个字都没改**，把那一行原样发回来即可（不要手改）。

## 四、验收（改完必须能同时看到这三条）

```powershell
# 1) 入口 JS 应该带 gzip，且传输量约 583 KB（不是 1.79 MB）
curl.exe -sI -H "Accept-Encoding: gzip" https://console.erp.vclinker.com/static/js/index-*.js

# 2) 静态资源应该有一年强缓存
#    期望看到: cache-control: public, max-age=31536000, immutable
#    （响应里会有两行 Cache-Control，一行来自 expires、一行来自 add_header，浏览器会合并，正常）

# 3) /login 必须**没有**长缓存（期望 cache-control: no-cache）
curl.exe -sI https://console.erp.vclinker.com/login
```

⚠ 第 3 条和第 2 条是**成对**的：`/static/` 缓存一年，而 `index.html` 里写的是带 hash 的
资源名，它一旦被缓存住，下次发版用户会拿着旧入口去要已经被换掉的 chunk，页面直接白屏。

浏览器里看的话：DevTools → Network → 勾 **Disable cache**，再刷新，
`index-*.js` 的 Size 那一列应该从 1.7 MB 变成约 583 KB。

## 五、回退（30 秒）

```powershell
Get-ChildItem C:\nginx\conf\nginx.conf.bak-* | Sort-Object Name -Descending | Select-Object -First 3
# 取最近那个时间戳：
Copy-Item C:\nginx\conf\nginx.conf.bak-<时间戳> C:\nginx\conf\nginx.conf -Force
Set-Location C:\nginx          # ⚠ 同上：必须切到 nginx 目录
.\nginx.exe -t
.\nginx.exe -s reload
```

## 六、为什么不让手工改

`nginx.conf` 里有**两个一模一样的 `listen 443 ssl;`**（`data.erp` 和 `console.erp`），
手工改很容易动到 API 那个 vhost。脚本按唯一锚点定位，只碰 `console.erp`，
并且找不到锚点就一个字都不写。

## 七、改完之后还能更快吗（可选，不急）

| 事项 | 收益 | 代价 |
| --- | --- | --- |
| 出口带宽 1 Mbps → 5 Mbps（腾讯云控制台改配置，或把 dist 放 COS + CDN） | 5 秒 → 约 1 秒 | 要花钱；**这是真正的天花板** |
| 前端 element-plus 改按需引入 | 首屏 604 KB → 约 330 KB（4.8 秒 → 2.6 秒） | 需要重新发版前端 + 逐页回归测试，属另一次改动 |

完整的技术记录（实测数据、字节账、验证脚本）在仓库
`docs/设计稿/2026-09-23-ERP首屏加载优化.md`，
可粘贴的完整 nginx 配置在同目录 `deploy/nginx/erp/nginx.console-erp.conf`。

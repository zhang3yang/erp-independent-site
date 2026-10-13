#!/usr/bin/env bash
#
# 只重建 station-site（前台）。由 deploy/scripts/esvafit-theme-2026-09-21.sh 传上去执行。
#
# 为什么单独一个脚本、不用 /root/rebuild.sh：那一份连 station-console（Maven）和
# station-console-ui（Vite）一起重建，而改主题只动了前台。在 2G 的机器上少跑两个
# 构建就是少两次 OOM 的机会，时间也从五六分钟压到一两分钟。
#
# ⚠ 2G 内存：Nuxt 构建吃 ~1.4G，必须先把两个服务停掉腾出来。
#   trap 挂在 EXIT 上，构建失败也会把它们拉回来——否则一次失败的部署会让站点一直是关的。

exec > /root/rebuild-site.log 2>&1
set -x

rm -f /root/rebuild-site.done /root/rebuild-site.fail

trap 'systemctl start station-console station-site 2>/dev/null' EXIT

systemctl stop station-site station-console
sleep 2
free -h

cd /root/build/station-site || { echo FAIL-cd > /root/rebuild-site.fail; exit 1; }
NODE_OPTIONS="--max-old-space-size=1400" npm run build || { echo FAIL-build > /root/rebuild-site.fail; exit 1; }

rm -rf /opt/station/site/.output
cp -r /root/build/station-site/.output /opt/station/site/.output
chown -R station:station /opt/station/site

systemctl start station-console station-site

# ⚠ 等「接口真的应答了」，不是等 systemctl 说 active。
#   单元是 Type=simple，进程一起来 systemd 就报 active，而 Spring Boot 还要六秒
#   才开始收请求；2026-09-21 上一轮就是在这个空窗里验收，商品列表页返回 503，
#   数据其实全对，白查了一轮。
for i in $(seq 1 40); do
  curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break
  sleep 2
done
for i in $(seq 1 30); do
  curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break
  sleep 2
done

systemctl is-active station-console station-site > /root/rebuild-site.status
echo DONE > /root/rebuild-site.done

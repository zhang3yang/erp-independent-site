#!/usr/bin/env bash
#
# 独立站发版（2026-09-30）：首页主推商品轮播 + 合作方视频。换后端 jar、管理台 dist、前台 .output，没有迁移。
# 法国机、统领机布局一样（/opt/station/{console,console-ui,site}），两台都用这一份。以 root 执行，
# 同目录放 station-console-1.0.0.jar、console-ui-dist.tgz、site-output.tgz。
#
# 新功能默认不生效：主推商品默认仍是三列，合作方视频没配就不出现——要在管理台「首页装修」里选 / 填。
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/station-release-$TS"
mkdir -p "$BK"

echo "== 1. 备份到 $BK"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
tar -C /opt/station/console-ui -czf "$BK/console-ui-dist.tgz" dist
tar -C /opt/station/site -czf "$BK/site-output.tgz" .output

echo "== 2. 换包"
trap 'systemctl start station-console station-site 2>/dev/null || true' EXIT
systemctl stop station-site station-console
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
rm -rf /opt/station/console-ui/dist && tar -C /opt/station/console-ui -xzf "$HERE/console-ui-dist.tgz"
rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf "$HERE/site-output.tgz"
chown -R station:station /opt/station/console-ui/dist /opt/station/site
systemctl start station-console station-site

echo "== 3. 验收"
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break; sleep 2; done
systemctl is-active station-console station-site
for p in / /products /configure /checkout; do
  echo "$p $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$p")"
done
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
echo "DONE 备份在 $BK"
# 回滚：停服务 → $BK 里三样放回原处 → 起服务

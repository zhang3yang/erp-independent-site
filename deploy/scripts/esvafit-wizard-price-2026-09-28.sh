#!/usr/bin/env bash
#
# 法国站：引导式下单右栏改成显示预估总价（2026-09-28 第二次发版）。
# 只换后端 jar 和前台 .output，没有迁移。在法国机上以 root 执行，同目录放
# station-console-1.0.0.jar 和 site-output.tgz。
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/esvafit-wizard-price-$TS"
mkdir -p "$BK"

echo "== 1. 备份到 $BK"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
tar -C /opt/station/site -czf "$BK/site-output.tgz" .output

echo "== 2. 换包"
trap 'systemctl start station-console station-site 2>/dev/null || true' EXIT
systemctl stop station-site station-console
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf "$HERE/site-output.tgz"
chown -R station:station /opt/station/site
systemctl start station-console station-site

echo "== 3. 验收"
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break; sleep 2; done
systemctl is-active station-console station-site
echo "currency: $(curl -s 'http://127.0.0.1:8081/openapi/site/wizard?lang=fr' | grep -o '"currency":"[A-Z]*"' | tail -1)"
echo "boot price: $(curl -s 'http://127.0.0.1:8081/openapi/site/wizard?lang=fr' | grep -o '"EB-BOOT-01":{[^{]*' | grep -o '"price":[0-9.]*')"
for p in / /configure /en/configure /products/EB-BOOT-01 /checkout; do
  echo "$p $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$p")"
done
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
echo "DONE 备份在 $BK"
# 回滚：停服务 → cp $BK/station-console-1.0.0.jar 回去、$BK/site-output.tgz 解回 /opt/station/site → 起服务

#!/usr/bin/env bash
#
# 只换后端 jar 和管理台 dist（2026-09-28：「载入预置模板」按站点挑模板）。没有迁移、不动前台。
# 法国机、统领机布局一样（/opt/station/{console,console-ui}），两台都用这一份。以 root 执行，
# 同目录放 station-console-1.0.0.jar 和 console-ui-dist.tgz。
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/console-ui-$TS"
mkdir -p "$BK"

echo "== 1. 备份到 $BK"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
tar -C /opt/station/console-ui -czf "$BK/console-ui-dist.tgz" dist

echo "== 2. 换包"
trap 'systemctl start station-console 2>/dev/null || true' EXIT
systemctl stop station-console
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
rm -rf /opt/station/console-ui/dist && tar -C /opt/station/console-ui -xzf "$HERE/console-ui-dist.tgz"
chown -R station:station /opt/station/console-ui/dist
systemctl start station-console

echo "== 3. 验收"
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
systemctl is-active station-console station-site
echo "wizard: $(curl -s 'http://127.0.0.1:8081/openapi/site/info' | grep -o '"wizard":{"enabled":[a-z]*')"
echo "/configure $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/configure)"
echo "未登录调模板接口（应 401）: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/api/wizard/templates)"
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
echo "DONE 备份在 $BK"
# 回滚：停 station-console → cp $BK/station-console-1.0.0.jar 回去、$BK/console-ui-dist.tgz 解回 /opt/station/console-ui → 起服务

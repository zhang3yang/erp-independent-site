#!/usr/bin/env bash
#
# 康辰独立站发版（2026-10-09，新机 106.54.235.248，Ubuntu + systemd）
#
# 内容（HEAD = 2c886880，康辰线上原来是 8ea79d8e）：
#   - 商品 3D 模型状态：多个 glb 切换（腿套没气/半充/充满），logo 自动投影到各状态
#     设计 docs/superpowers/specs/2026-10-08-product-3d-states-design.md
#   - 买家 3D 定制：开机画面只要求商家标了屏幕，开机键可有可无（380ef38c）
#   - 同事的首页主推商品「横向轮播」+ 合作方竖屏视频（b8650d6c，默认不生效，要在首页装修里选）
#
# 有迁移：2026-10-08-goods-model3d-states.sql（u_s_sitegoods、u_s_custom_plan 各加一列 model3d_states）。
# ⚠ 迁移必须先于新 jar——本脚本先停服务、备份库、跑迁移，再换包。
#
# 用法：同目录放 station-console-1.0.0.jar、console-ui-dist.tgz、site-output.tgz、
#       2026-10-08-goods-model3d-states.sql，然后 `sudo -i` 后执行：bash /tmp/kc-release/kangchen-release-2026-10-09.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/kc-release-$TS"
DB=sdsstation
mkdir -p "$BK"

for f in station-console-1.0.0.jar console-ui-dist.tgz site-output.tgz 2026-10-08-goods-model3d-states.sql; do
  [ -f "$HERE/$f" ] || { echo "缺文件：$HERE/$f"; exit 1; }
done

# 管理台静态文件：这台机是 dist 内容直接放在 /opt/station/console-ui（nginx root 指它），
# 法国机是 /opt/station/console-ui/dist。按 index.html 在哪判断，别猜。
if [ -f /opt/station/console-ui/dist/index.html ]; then UI=/opt/station/console-ui/dist
elif [ -f /opt/station/console-ui/index.html ]; then UI=/opt/station/console-ui
else echo "找不到线上管理台 index.html，停"; exit 1; fi
echo "管理台目录：$UI"

echo "== 1. 备份到 $BK"
mysqldump --default-character-set=utf8mb4 --single-transaction --routines "$DB" | gzip > "$BK/$DB.sql.gz"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
tar -C "$UI" -czf "$BK/console-ui.tgz" .
tar -C /opt/station/site -czf "$BK/site-output.tgz" .output
ls -la "$BK"

echo "== 2. 停服务、跑迁移"
trap 'systemctl start station-console station-site 2>/dev/null || true' EXIT
systemctl stop station-site station-console
mysql --default-character-set=utf8mb4 "$DB" < "$HERE/2026-10-08-goods-model3d-states.sql"
mysql -N "$DB" -e "select count(*) from information_schema.columns where table_schema='$DB' and column_name='model3d_states' and table_name in ('u_s_sitegoods','u_s_custom_plan')" \
  | grep -qx 2 || { echo "迁移后列数不是 2，停（服务会被 trap 用旧包拉起来）"; exit 1; }
echo "迁移 OK：两列都在"

echo "== 3. 换包"
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
find "$UI" -mindepth 1 -delete
tar -C "$UI" -xzf "$HERE/console-ui-dist.tgz"
rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf "$HERE/site-output.tgz"
chown -R station:station "$UI" /opt/station/site
systemctl start station-console station-site

echo "== 4. 验收"
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break; sleep 2; done
systemctl is-active station-console station-site
for p in / /products; do echo "前台 $p $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$p")"; done
CODE=$(curl -s "http://127.0.0.1:8081/openapi/site/products?page=1&size=1" | grep -o '"code":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
if [ -n "$CODE" ]; then
  curl -s "http://127.0.0.1:8081/openapi/site/products/$CODE" | grep -q '"model3dStates"' \
    && echo "详情接口带 model3dStates：OK（$CODE）" || echo "⚠ 详情接口没有 model3dStates（$CODE）"
fi
ls "$UI"/assets/ | grep -q '^Goods-9-7BftuR\.js$' && echo "管理台包：OK" || echo "⚠ 管理台包名对不上（应有 Goods-9-7BftuR.js）"
ls /opt/station/site/.output/public/_nuxt/ | grep -q '^DZlfad41\.js$' && echo "前台 3D 包：OK" || echo "⚠ 前台 3D 包名对不上（应有 DZlfad41.js）"
echo "最近 3 分钟后端错误（空 = 没有）："
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
trap - EXIT
echo "DONE 备份在 $BK"
#
# 回滚：
#   systemctl stop station-site station-console
#   install -o station -g station -m 644 $BK/station-console-1.0.0.jar /opt/station/console/
#   find $UI -mindepth 1 -delete && tar -C $UI -xzf $BK/console-ui.tgz
#   rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf $BK/site-output.tgz
#   chown -R station:station $UI /opt/station/site && systemctl start station-console station-site
#   新加的两列留着无害（旧 jar 不读）；真要删见迁移文件末尾的回滚语句。

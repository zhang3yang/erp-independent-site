#!/usr/bin/env bash
#
# 法国站（esvafit，162.62.132.149）上线引导式下单，同时追平到仓库当前版本（2026-09-28）。
#
# 在法国机上以 root 执行，同目录下要有：
#   station-console-1.0.0.jar   console-ui-dist.tgz   site-output.tgz
#   migration/*.sql             01_station_schema.sql（只用来比对表结构）
#   wizard-seed.sql             预置向导（按摩仪模板）写进 u_s_orderwizard，已有就不动
#
# 这一版顺带上线的（用户 2026-09-28 确认「全部上线，都开着」）：
#   · 引导式下单（本次）            · 前台主题按站点选（本站 theme 留空 = 黑蓝，不变）
#   · 同事的商品 3D 标注（44c3e98c） · 同事的管理台「用照片生成 3D」（591867d7，开着，每站每天 20 次）
#
# 出错即停；停在换包之前的话线上什么都没变，停在之后按文末「回滚」做。
# ⚠ 2G 内存：不在这台机上构建，三个包都是本地构建好传上来的。

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/esvafit-wizard-$TS"
mkdir -p "$BK"
SQL=(mysql --default-character-set=utf8mb4)

# 台账最后一条是 09-22 的 order-lang。之后的通用迁移全部幂等（加列走 information_schema 判断）。
MIGRATIONS=(
  2026-09-23-fix-richtext-escaped-quotes.sql
  2026-09-24-site-theme.sql
  2026-09-25-goods-model3d-marks.sql
  2026-09-25-goods-options.sql
  2026-09-26-model-gen-job.sql
  2026-09-28-order-wizard.sql
)

echo "== 1. 备份到 $BK"
mysqldump --default-character-set=utf8mb4 --single-transaction --routines sdsstation | gzip > "$BK/sdsstation.sql.gz"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
tar -C /opt/station/console-ui -czf "$BK/console-ui-dist.tgz" dist
tar -C /opt/station/site -czf "$BK/site-output.tgz" .output
ls -la "$BK"

echo "== 2. 迁移"
for f in "${MIGRATIONS[@]}"; do
  echo "-- $f"
  "${SQL[@]}" sdsstation < "$HERE/migration/$f"
  "${SQL[@]}" sdsstation -e "insert ignore into u_s_schema_migration (filename, appliedat, appliedby, source, note)
    values ('$f', now(), 'claude', '执行', '2026-09-28 法国站上线引导式下单，deploy/scripts/esvafit-wizard-2026-09-28.sh')"
done

echo "== 3. 表结构和仓库建表脚本逐列比对"
"${SQL[@]}" -e "drop database if exists __schemacheck; create database __schemacheck default charset utf8mb4"
sed -e 's/`sdsstation`/`__schemacheck`/g' "$HERE/01_station_schema.sql" | "${SQL[@]}" __schemacheck
DIFF="$("${SQL[@]}" -N -e "
  select concat('仓库有、本站缺: ', a.table_name, '.', a.column_name)
    from information_schema.columns a
   where a.table_schema='__schemacheck'
     and not exists (select 1 from information_schema.columns b
                      where b.table_schema='sdsstation' and b.table_name=a.table_name and b.column_name=a.column_name)")"
"${SQL[@]}" -e "drop database __schemacheck"
if [ -n "$DIFF" ]; then echo "$DIFF"; echo "表结构对不上，停在换包之前（线上没变）"; exit 1; fi
echo "表结构一致"

echo "== 4. 预置向导（已有就不动）"
"${SQL[@]}" sdsstation < "$HERE/wizard-seed.sql"
"${SQL[@]}" sdsstation -e "select sitecode, enabled, length(schemajson) len, updateuser, updatetime from u_s_orderwizard; select sitecode, theme from u_s_siteinfo"

echo "== 5. 换包"
trap 'systemctl start station-console station-site 2>/dev/null || true' EXIT
systemctl stop station-site station-console
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
mv /opt/station/console-ui/dist "/opt/station/console-ui/dist.old.$TS"
tar -C /opt/station/console-ui -xzf "$HERE/console-ui-dist.tgz"
rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf "$HERE/site-output.tgz"
chown -R station:station /opt/station/console-ui/dist /opt/station/site
systemctl start station-console station-site

echo "== 6. 验收"
# 等接口真的应答，不是等 systemctl 说 active（Type=simple，Spring Boot 还要几秒才收请求）
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break; sleep 2; done
systemctl is-active station-console station-site
echo "wizard: $(curl -s 'http://127.0.0.1:8081/openapi/site/info?lang=fr' | grep -o '"wizard":{[^}]*}' || echo 缺)"
for p in / /configure /en/configure /zh/configure /products /cart /checkout; do
  echo "$p $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$p")"
done
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
echo "DONE 备份在 $BK"

# 回滚：
#   systemctl stop station-site station-console
#   cp $BK/station-console-1.0.0.jar /opt/station/console/
#   rm -rf /opt/station/console-ui/dist && mv /opt/station/console-ui/dist.old.<TS> /opt/station/console-ui/dist
#   rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf $BK/site-output.tgz
#   systemctl start station-console station-site
#   迁移只加列加表，旧 jar 不读新列，库可以不回滚；真要回滚：gunzip < $BK/sdsstation.sql.gz | mysql sdsstation

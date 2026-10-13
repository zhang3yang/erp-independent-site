#!/usr/bin/env bash
#
# 统领站（124.221.220.89）开启引导式下单，同时追平到仓库当前版本（2026-09-28）。
#
# 在统领机上以 root 执行，同目录下要有：
#   station-console-1.0.0.jar   console-ui-dist.tgz   site-output.tgz
#   migration/*.sql             01_station_schema.sql（只用来比对表结构）
#   wizard-seed.sql             统领的向导（零部件 + 套件，order-wizard/tongling-template.json），已有就不动
#
# 相对 09-27 那版（1af86422）顺带上线的：同事的「用照片生成 3D」（591867d7，开着，和法国站一样）、
# 引导式下单的预估总价（统领商品目前全是 0 价，右栏会显示「询价」）。
# 出错即停；停在换包之前的话线上什么都没变，停在之后按文末「回滚」做。

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/tongling-wizard-$TS"
mkdir -p "$BK"

set -a; . /etc/station/console.env; set +a
export MYSQL_PWD="$DB_PASSWORD"
MYSQL=(mysql --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
# 建临时库比对要 root（socket 登录）；上面 export 的是 station 的密码，带着它 root 会被拒
ROOTSQL=(env -u MYSQL_PWD mysql --default-character-set=utf8mb4)

MIGRATIONS=(
  2026-09-26-model-gen-job.sql
  2026-09-28-order-wizard.sql
)

echo "== 1. 备份到 $BK"
mysqldump --default-character-set=utf8mb4 --single-transaction --routines --no-tablespaces \
  -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" sdsstation | gzip > "$BK/sdsstation.sql.gz"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
tar -C /opt/station/console-ui -czf "$BK/console-ui-dist.tgz" dist
tar -C /opt/station/site -czf "$BK/site-output.tgz" .output
ls -la "$BK"

echo "== 2. 迁移"
for f in "${MIGRATIONS[@]}"; do
  echo "-- $f"
  "${MYSQL[@]}" sdsstation < "$HERE/migration/$f"
  "${MYSQL[@]}" sdsstation -e "insert ignore into u_s_schema_migration (filename, appliedat, appliedby, source, note)
    values ('$f', now(), 'claude', '执行', '2026-09-28 统领开启引导式下单，deploy/scripts/tongling-wizard-2026-09-28.sh')"
done

echo "== 3. 表结构和仓库建表脚本逐列比对"
"${ROOTSQL[@]}" -e "drop database if exists __schemacheck; create database __schemacheck default charset utf8mb4"
sed -e 's/`sdsstation`/`__schemacheck`/g' "$HERE/01_station_schema.sql" | "${ROOTSQL[@]}" __schemacheck
DIFF="$("${ROOTSQL[@]}" -N -e "
  select concat('仓库有、统领缺: ', a.table_name, '.', a.column_name)
    from information_schema.columns a
   where a.table_schema='__schemacheck'
     and not exists (select 1 from information_schema.columns b
                      where b.table_schema='sdsstation' and b.table_name=a.table_name and b.column_name=a.column_name)")"
"${ROOTSQL[@]}" -e "drop database __schemacheck"
if [ -n "$DIFF" ]; then echo "$DIFF"; echo "表结构对不上，停在换包之前（线上没变）"; exit 1; fi
echo "表结构一致"

echo "== 4. 写入统领的向导（已有就不动）"
"${MYSQL[@]}" sdsstation < "$HERE/wizard-seed.sql"
"${MYSQL[@]}" sdsstation -e "select sitecode, enabled, length(schemajson) len, updateuser, updatetime from u_s_orderwizard"

echo "== 5. 换包"
trap 'systemctl start station-console station-site 2>/dev/null || true' EXIT
systemctl stop station-site station-console
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
rm -rf /opt/station/console-ui/dist && tar -C /opt/station/console-ui -xzf "$HERE/console-ui-dist.tgz"
rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf "$HERE/site-output.tgz"
chown -R station:station /opt/station/console-ui /opt/station/site
systemctl start station-console station-site

echo "== 6. 验收"
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break; sleep 2; done
systemctl is-active station-console station-site
echo "wizard: $(curl -s 'http://127.0.0.1:8081/openapi/site/info?lang=en' | grep -o '"wizard":{"enabled":[a-z]*,"title":"[^"]*"' || echo 缺)"
echo "theme: $(curl -s 'http://127.0.0.1:8081/openapi/site/info' | grep -o '"theme":"[a-z]*"')"
for p in / /configure /zh/configure /ru/configure /es/configure /products /checkout; do
  echo "$p $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$p")"
done
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
echo "DONE 备份在 $BK"

# 回滚：
#   systemctl stop station-site station-console
#   cp $BK/station-console-1.0.0.jar /opt/station/console/
#   rm -rf /opt/station/console-ui/dist && tar -C /opt/station/console-ui -xzf $BK/console-ui-dist.tgz
#   rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf $BK/site-output.tgz
#   systemctl start station-console station-site
#   迁移只加表加列，旧 jar 不读，库可以不回滚；要关掉向导只需 update u_s_orderwizard set enabled=0

#!/usr/bin/env bash
#
# 统领站（124.221.220.89）对齐法国站：前台、管理台、后端换成当前仓库这一版（2026-09-27）。
#
# 在统领机上以 root 执行（sudo bash deploy.sh），同目录下要有：
#   station-console-1.0.0.jar   本地 mvn package 出来的后端
#   console-ui-dist.tgz         station-console-ui/dist
#   site-output.tgz             station-site/.output
#   migration/*.sql             下面 MIGRATIONS 里列的那些
#   01_station_schema.sql       核对表结构用，不往 sdsstation 里跑
#
# 统领和法国站的差别只留三处，都不在代码里：
#   · 主题：2026-09-24-site-theme.sql 把非法国站设成 classic（统领原来的紫色）
#   · 用户管理留在菜单里：console.env 加 CONSOLE_USERS_MENU=true
#   · 内容：统领自己的装修和商品一行不动。法国站的内容脚本（esvafit-* / ebboot01 /
#     fr-goods-specs-desc）不跑——那是 Esva boots 的文案和商品，不是代码
#
# 出错即停；停在换包之前的话线上什么都没变，停在之后按文末「回滚」做。

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BK="/root/backup/tongling-sync-$TS"
mkdir -p "$BK"

set -a; . /etc/station/console.env; set +a
export MYSQL_PWD="$DB_PASSWORD"
MYSQL=(mysql --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")

# 09-11 之后统领一个结构迁移都没跑过（台账最后一条是 09-11 的 followup）。
# 顺序按日期：site-theme 的 AFTER `consolelang` 依赖 console-lang 先加列。
MIGRATIONS=(
  2026-09-11-drop-campaign-leftovers.sql
  2026-09-13-decorate-draft.sql
  2026-09-20-goods-model3d.sql
  2026-09-20-sitecategory-lang.sql
  2026-09-21-currency-convert-log.sql
  2026-09-21-goods-spec-i18n.sql
  2026-09-22-console-lang.sql
  2026-09-22-goods-main-video.sql
  2026-09-22-order-lang.sql
  2026-09-23-fix-richtext-escaped-quotes.sql
  2026-09-24-site-theme.sql
  2026-09-25-goods-model3d-marks.sql
  2026-09-25-goods-options.sql
)

echo "== 1. 备份到 $BK"
mysqldump --default-character-set=utf8mb4 --single-transaction --routines --no-tablespaces \
  -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" sdsstation | gzip > "$BK/sdsstation.sql.gz"
cp -a /opt/station/console/station-console-1.0.0.jar "$BK/"
cp -a /etc/station/console.env "$BK/console.env"
tar -C /opt/station/console-ui -czf "$BK/console-ui-dist.tgz" dist
tar -C /opt/station/site -czf "$BK/site-output.tgz" .output
ls -la "$BK"

echo "== 2. 迁移"
has_col() {
  "${MYSQL[@]}" -N sdsstation -e "select count(*) from information_schema.columns
    where table_schema='sdsstation' and table_name='$1' and column_name='$2'"
}
for f in "${MIGRATIONS[@]}"; do
  # decorate-draft 是裸 ALTER，不能重跑；其余都自带幂等判断
  if [ "$f" = 2026-09-13-decorate-draft.sql ] && [ "$(has_col u_s_siteinfo draftjson)" = 1 ]; then
    echo "-- $f：draftjson 已在，跳过"
  else
    echo "-- $f"
    "${MYSQL[@]}" sdsstation < "$HERE/migration/$f"
  fi
  "${MYSQL[@]}" sdsstation -e "insert ignore into u_s_schema_migration
    (filename, appliedat, appliedby, source, note)
    values ('$f', now(), 'claude', '执行', '2026-09-27 统领对齐法国站，deploy/scripts/tongling-sync-2026-09-27.sh')"
done

echo "== 3. 表结构和仓库建表脚本逐列比对"
# 在临时库里跑一遍 01_station_schema.sql，和 sdsstation 比列名。
# 比出差异就停：说明还有迁移没覆盖到的改动，换了 jar 会在某个查询上 500。
# ⚠ 用本机 root（socket 登录）：station 账号只有 sdsstation 的权限，建不了临时库
#   上面 export 的 MYSQL_PWD 是 station 的密码，带着它 root 会被拒，所以这里去掉
ROOTSQL=(env -u MYSQL_PWD mysql --default-character-set=utf8mb4)
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
"${MYSQL[@]}" sdsstation -e "select sitecode, sitename, theme, consolelang from u_s_siteinfo"

echo "== 4. 用户管理留在菜单里"
if grep -q '^CONSOLE_USERS_MENU=' /etc/station/console.env; then
  sed -i 's/^CONSOLE_USERS_MENU=.*/CONSOLE_USERS_MENU=true/' /etc/station/console.env
else
  echo 'CONSOLE_USERS_MENU=true' >> /etc/station/console.env
fi
grep '^CONSOLE_USERS_MENU=' /etc/station/console.env

echo "== 5. 换包"
trap 'systemctl start station-console station-site 2>/dev/null || true' EXIT
systemctl stop station-site station-console
install -o station -g station -m 644 "$HERE/station-console-1.0.0.jar" /opt/station/console/station-console-1.0.0.jar
rm -rf /opt/station/console-ui/dist && tar -C /opt/station/console-ui -xzf "$HERE/console-ui-dist.tgz"
rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf "$HERE/site-output.tgz"
chown -R station:station /opt/station/console-ui /opt/station/site
systemctl start station-console station-site

echo "== 6. 验收"
# 等接口真的应答，不是等 systemctl 说 active（Type=simple，Spring Boot 还要几秒才收请求）
for i in $(seq 1 60); do curl -sf -m 3 -o /dev/null http://127.0.0.1:8081/openapi/site/info && break; sleep 2; done
for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null http://127.0.0.1:3000/ && break; sleep 2; done
systemctl is-active station-console station-site
echo "theme: $(curl -s http://127.0.0.1:8081/openapi/site/info | grep -o '"theme":[^,]*' || echo 缺)"
echo "首页: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/)  $(curl -s http://127.0.0.1:3000/ | grep -o 'data-theme="[a-z]*"' | head -1 || true)"
echo "商品页: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/products)"
echo "管理台: $(curl -s -o /dev/null -w '%{http_code}' https://console.tongling.vclinker.com/)"
journalctl -u station-console --since "-3 min" --no-pager | grep -E " ERROR |Exception" | head -5 || true
echo "DONE 备份在 $BK"

# 回滚：
#   systemctl stop station-site station-console
#   cp $BK/station-console-1.0.0.jar /opt/station/console/ && cp $BK/console.env /etc/station/console.env
#   rm -rf /opt/station/console-ui/dist && tar -C /opt/station/console-ui -xzf $BK/console-ui-dist.tgz
#   rm -rf /opt/station/site/.output && tar -C /opt/station/site -xzf $BK/site-output.tgz
#   systemctl start station-console station-site
#   迁移只加列加表，旧 jar 不读新列，库可以不回滚；真要回滚：gunzip < $BK/sdsstation.sql.gz | mysql sdsstation

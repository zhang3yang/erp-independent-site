#!/usr/bin/env bash
# =====================================================================
# 每月刷新 u_s_ip_country —— IP 段到国家的离线对照表
# 数据源 DB-IP country-lite（CC-BY），每月出新版
# =====================================================================
#
# 用在管理台首页 Overview 的「国家 / 地区」访客分布，见
# PageViewService#lookupCountry → SitePageViewMapper.selectCountryByIp。
#
# ⚠ 核心设计：**先导影子表，验过了再原子换名**。
#   直接 TRUNCATE + LOAD 的话，下载残缺或 MySQL 中途出错就会留下一张空表，
#   而这个功能的失败是静默的（lookupCountry 吞异常返回 null），
#   等有人发现「国家分布怎么空了」可能是几周以后。
#   RENAME TABLE 是原子的，中间没有查不到数据的窗口。
#
# 手工跑：sudo bash refresh-ip-country.sh
# 自动跑：station-ip-country.timer，每月 1 号
# =====================================================================
set -euo pipefail

DB=sdsstation
TBL=u_s_ip_country
MYSQLDIR=/var/lib/mysql-files        # secure_file_priv，LOAD DATA INFILE 只认这里
WORK=/tmp/dbip-refresh
MIN_ROWS=500000                      # 低于这个数视为下载残缺。2026-09 版是 717170 行

SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
log() { echo "[$(date -Is)] $*"; }
cleanup() { rm -rf "$WORK"; $SUDO rm -f "$MYSQLDIR/dbip-refresh.csv"; }
trap cleanup EXIT

rm -rf "$WORK"; mkdir -p "$WORK"; cd "$WORK"

# ---------------------------------------------------------------------
# 下载。当月那版月初未必已发布，往前退最多 3 个月
# ---------------------------------------------------------------------
VER=""
for i in 0 1 2; do
  M=$(date -d "-$i month" +%Y-%m)
  log "尝试 $M 版"
  if curl -fsL --max-time 600 -o dbip.csv.gz "https://download.db-ip.com/free/dbip-country-lite-$M.csv.gz"; then
    # ⚠ 必须验 gzip：db-ip 对不存在的月份也可能回 200 加一个 HTML 错误页，
    #   那东西 gunzip 会失败，但 curl -f 拦不住
    if gzip -t dbip.csv.gz 2>/dev/null; then VER=$M; break; fi
    log "  下到的不是有效 gzip，跳过"
  fi
done
[ -n "$VER" ] || { log "✗ 三个月都没下到，放弃。现有数据保持不动"; exit 1; }

gunzip -f dbip.csv.gz
ROWS=$(wc -l < dbip.csv)
log "$VER 版，$ROWS 行"
[ "$ROWS" -ge "$MIN_ROWS" ] || { log "✗ 行数 $ROWS < $MIN_ROWS，疑似残缺，放弃"; exit 1; }

$SUDO mv dbip.csv "$MYSQLDIR/dbip-refresh.csv"
$SUDO chown mysql:mysql "$MYSQLDIR/dbip-refresh.csv"

# ---------------------------------------------------------------------
# 导影子表
# ---------------------------------------------------------------------
$SUDO mysql "$DB" <<SQL
DROP TABLE IF EXISTS ${TBL}_new;
CREATE TABLE ${TBL}_new LIKE ${TBL};
LOAD DATA INFILE '$MYSQLDIR/dbip-refresh.csv'
INTO TABLE ${TBL}_new
FIELDS TERMINATED BY ',' LINES TERMINATED BY '\n'
(@s, @e, @c)
SET ipver   = IF(LOCATE(':', @s) > 0, 6, 4),
    ipstart = INET6_ATON(@s),
    ipend   = INET6_ATON(@e),
    country = @c;
SQL

NEW=$($SUDO mysql -N "$DB" -e "select count(*) from ${TBL}_new;")
log "影子表 $NEW 行"
if [ "$NEW" -lt "$MIN_ROWS" ]; then
  log "✗ 影子表行数不足，丢弃。现有数据保持不动"
  $SUDO mysql "$DB" -e "DROP TABLE IF EXISTS ${TBL}_new;"
  exit 1
fi

# ---------------------------------------------------------------------
# 抽样自检。行数够不代表内容对——字段错位一样能导进去
# ---------------------------------------------------------------------
BAD=0
check() {
  local ip=$1 want=$2 ver=$3
  local got
  got=$($SUDO mysql -N "$DB" -e "select country from ${TBL}_new where ipver=$ver and ipstart<=inet6_aton('$ip') and ipend>=inet6_aton('$ip') order by ipstart desc limit 1;")
  if [ "$got" = "$want" ]; then log "  自检 $ip → $got ✓"
  else log "  ✗ 自检 $ip 期望 $want，得到 ${got:-空}"; BAD=1; fi
}
check 8.8.8.8     US 4
check 1.0.0.0     AU 4
check 2001:200::  JP 6
if [ "$BAD" -ne 0 ]; then
  log "✗ 自检未通过，丢弃影子表。现有数据保持不动"
  $SUDO mysql "$DB" -e "DROP TABLE IF EXISTS ${TBL}_new;"
  exit 1
fi

# ---------------------------------------------------------------------
# 原子换名。RENAME TABLE 一条语句里做完，没有查不到数据的窗口
# ---------------------------------------------------------------------
$SUDO mysql "$DB" -e "
  DROP TABLE IF EXISTS ${TBL}_old;
  RENAME TABLE ${TBL} TO ${TBL}_old, ${TBL}_new TO ${TBL};
  DROP TABLE ${TBL}_old;"

log "✓ 已切换到 $VER 版，$NEW 行"

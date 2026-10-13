#!/usr/bin/env bash
#
# 从一个「已经是最新状态」的开发库重新生成 sql/01_schema_and_init.sql 和 sql/02_demo_data.sql。
#
# 用法：
#     MYSQL_PWD=xxx sql/tools/rebuild_baseline.sh [host] [user]
#
# 什么时候跑：把 sql/migration/ 下的增量脚本合并回基线的时候。
# 流程是：先把 migration 脚本在本地库跑完 → 跑本脚本 → 清空 migration 目录 → 提交。
#
# 结构直接从真库导（--no-data），这样外键、索引、AUTO_INCREMENT 定义一个不漏；
# 出厂数据先在 bl_* 临时库里按名单过滤和脱敏，再单独导出来接在后面。
# 不整库 dump 开发库，是因为要剔掉月表、日志表、真实业务数据和账号口令，
# 这些用 mysqldump 的参数做不到。
#
set -euo pipefail

HOST="${1:-127.0.0.1}"
USER="${2:-root}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MY="mysql -h $HOST -u $USER --default-character-set=utf8mb4"
DUMP="mysqldump -h $HOST -u $USER --default-character-set=utf8mb4 --no-tablespaces --single-transaction --skip-add-drop-table --skip-add-locks --skip-comments --skip-set-charset --complete-insert --skip-extended-insert"

SRC=(sdssys sdsshop sdsorder sdsstation)

# 保留的三个商家。改这里等于改出厂数据的范围。
SHOPS="'sp708xm02q','sp708xm02r','sp708xm02u'"
# 平台超管 + 三个商家各自的管理员（q 归 us208XQ03，r 和 u 都归 us208XQ02）
USERS="'us1','us208XQ02','us208XQ03'"
# 平台超管、平台普通角色、三个商家的内置「商家管理员」
ROLES="'rl1','rl2','rl108','rl109','rl110'"
# 若依自带演示部门 100~109 + 三个商家总部
DEPTS="100,101,102,103,104,105,106,107,108,109,218,219,220"

# 所有账号的密码统一重置成 admin123 的 bcrypt。基线是给新人拉下来跑的，
# 不能带开发库里的真实口令 hash。
PWD_HASH='$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2'

# 出厂数据涉及的表，按导出顺序排。放在这里是为了让「基线里到底有哪些数据」
# 是一份能读的清单，而不是散在 INSERT 语句里。
SYS_TABLES="sys_menu sys_dict_type sys_dict_data sys_config sys_post sys_job s_m_function \
sys_dept sys_role sys_role_menu sys_role_dept sys_user sys_user_role sys_user_post \
s_m_shop sys_shop_menu sys_shop_user sys_shop_user_role"

echo ">>> 1/5 重建临时库 bl_sdssys / bl_sdsshop"
$MY -e "DROP DATABASE IF EXISTS bl_sdssys;  CREATE DATABASE bl_sdssys  DEFAULT CHARACTER SET utf8mb4;"
$MY -e "DROP DATABASE IF EXISTS bl_sdsshop; CREATE DATABASE bl_sdsshop DEFAULT CHARACTER SET utf8mb4;"
for t in $SYS_TABLES; do
  $MY -e "CREATE TABLE bl_sdssys.\`$t\` LIKE sdssys.\`$t\`;"
done
$MY -e "CREATE TABLE bl_sdsshop.u_m_doctypeconfig LIKE sdsshop.u_m_doctypeconfig;"

echo ">>> 2/5 按名单灌入出厂数据"
$MY <<SQL
-- ---- 菜单 / 字典 / 参数 / 定时任务：全量，这是系统能跑起来的最低要求 ----
INSERT INTO bl_sdssys.sys_menu      SELECT * FROM sdssys.sys_menu;
INSERT INTO bl_sdssys.sys_dict_type SELECT * FROM sdssys.sys_dict_type;
INSERT INTO bl_sdssys.sys_dict_data SELECT * FROM sdssys.sys_dict_data;
INSERT INTO bl_sdssys.sys_config    SELECT * FROM sdssys.sys_config;
INSERT INTO bl_sdssys.sys_post      SELECT * FROM sdssys.sys_post;
INSERT INTO bl_sdssys.sys_job       SELECT * FROM sdssys.sys_job;
INSERT INTO bl_sdssys.s_m_function  SELECT * FROM sdssys.s_m_function;

-- ---- 组织 / 角色 / 账号：按名单过滤，顺带把 del_flag=2 的软删行清掉 ----
INSERT INTO bl_sdssys.sys_dept      SELECT * FROM sdssys.sys_dept  WHERE dept_id IN ($DEPTS) AND del_flag='0';
INSERT INTO bl_sdssys.sys_role      SELECT * FROM sdssys.sys_role  WHERE role_code IN ($ROLES) AND del_flag='0';
INSERT INTO bl_sdssys.sys_role_menu SELECT * FROM sdssys.sys_role_menu WHERE role_code IN ($ROLES);
INSERT INTO bl_sdssys.sys_role_dept SELECT * FROM sdssys.sys_role_dept WHERE role_code IN ($ROLES);
INSERT INTO bl_sdssys.sys_user      SELECT * FROM sdssys.sys_user  WHERE user_code IN ($USERS) AND del_flag='0';
INSERT INTO bl_sdssys.sys_user_role SELECT * FROM sdssys.sys_user_role WHERE user_code IN ($USERS) AND role_code IN ($ROLES);
INSERT INTO bl_sdssys.sys_user_post SELECT * FROM sdssys.sys_user_post WHERE user_code IN ($USERS);

-- ---- 租户：三个商家 ----
INSERT INTO bl_sdssys.s_m_shop           SELECT * FROM sdssys.s_m_shop      WHERE shop_code IN ($SHOPS);
INSERT INTO bl_sdssys.sys_shop_menu      SELECT * FROM sdssys.sys_shop_menu WHERE shop_code IN ($SHOPS);
INSERT INTO bl_sdssys.sys_shop_user      SELECT * FROM sdssys.sys_shop_user
       WHERE shop_code IN ($SHOPS) AND user_code IN ($USERS) AND del_flag='0';
INSERT INTO bl_sdssys.sys_shop_user_role SELECT * FROM sdssys.sys_shop_user_role
       WHERE shop_code IN ($SHOPS) AND user_code IN ($USERS) AND role_code IN ($ROLES);

USE bl_sdssys;  -- 多表 DELETE 的删除列表不认库名前缀，必须先选库

-- ---- 清掉孤儿菜单 ----
-- 开发库里攒下了一批 parent 已经不存在的菜单，典型是二维库存改造删掉「库存状态」
-- 菜单 fn5005 之后留下的 4 条按钮权限（fn5065~fn5068）。它们在界面上看不见，
-- 但会跟着角色授权一起被导出来，是纯粹的噪音。基线是重新出厂，不带这些。
--
-- 同一条 DELETE 写五遍是在处理多级孤儿：删掉一层，它的子菜单就变成新的孤儿。
-- 菜单树只有三层，五遍绰绰有余；不用存储过程是因为 CREATE PROCEDURE 的函数体里
-- 有分号，mysql 客户端会在没有 DELIMITER 的情况下把它拆断。
-- 子查询套一层派生表，是因为 MySQL 不允许 DELETE 的子查询直接读被删的那张表。
DELETE m FROM sys_menu m WHERE m.parent_id <> '0'
  AND NOT EXISTS (SELECT 1 FROM (SELECT fun_code FROM sys_menu) x WHERE x.fun_code = m.parent_id);
DELETE m FROM sys_menu m WHERE m.parent_id <> '0'
  AND NOT EXISTS (SELECT 1 FROM (SELECT fun_code FROM sys_menu) x WHERE x.fun_code = m.parent_id);
DELETE m FROM sys_menu m WHERE m.parent_id <> '0'
  AND NOT EXISTS (SELECT 1 FROM (SELECT fun_code FROM sys_menu) x WHERE x.fun_code = m.parent_id);
DELETE m FROM sys_menu m WHERE m.parent_id <> '0'
  AND NOT EXISTS (SELECT 1 FROM (SELECT fun_code FROM sys_menu) x WHERE x.fun_code = m.parent_id);
DELETE m FROM sys_menu m WHERE m.parent_id <> '0'
  AND NOT EXISTS (SELECT 1 FROM (SELECT fun_code FROM sys_menu) x WHERE x.fun_code = m.parent_id);
DELETE rm FROM sys_role_menu rm
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.fun_code = rm.fun_code);
DELETE sm FROM sys_shop_menu sm
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.fun_code = sm.fun_code);

-- ---- 单据类型：系统级配置，不是业务数据，必须随基线走 ----
INSERT INTO bl_sdsshop.u_m_doctypeconfig SELECT * FROM sdsshop.u_m_doctypeconfig;

-- ---- 脱敏 ----
UPDATE bl_sdssys.sys_user SET
    password        = '$PWD_HASH',
    phonenumber     = '',
    email           = '',
    avatar          = '',
    login_ip        = '',
    login_date      = NULL,
    pwd_update_date = NULL,
    update_by       = '',
    update_time     = NULL,
    remark          = NULL;
UPDATE bl_sdssys.sys_user SET nick_name = '商家管理员' WHERE user_code <> 'us1';
SQL
$MY -N -B -e "SELECT CONCAT('    ', table_name, ' : ', table_rows) FROM information_schema.tables
              WHERE table_schema='bl_sdssys' ORDER BY table_name;" | sed 's/^/  /'

echo ">>> 3/5 导出表结构（跳过 _yyyyMM 月表，它们由 MonthlyTableInitializer 启动时自动建）"
STRUCT="$(mktemp)"
for db in "${SRC[@]}"; do
  ignore=""
  # ⚠ tr -d '\r' 不能去掉：Windows 下 mysql 客户端每行结尾带 CR，直接拼进
  #   --ignore-table=sdsorder.u_f_ledger_202608\r 是匹配不上任何表的，mysqldump 也不报错，
  #   结果就是月表悄悄进了基线。这个坑踩过一次，两边都对不出来（对比用的表名同样带 CR）。
  for t in $($MY -N -B -e "SELECT table_name FROM information_schema.tables
                            WHERE table_schema='$db' AND table_name REGEXP '_20[0-9]{4}\$';" | tr -d '\r'); do
    ignore="$ignore --ignore-table=$db.$t"
  done
  $DUMP --no-data --databases "$db" $ignore >> "$STRUCT"
done
# 空库上跑得通、脏库上不会毁数据：只加不删，重复执行跳过已存在的表。
sed -i 's/^CREATE TABLE `/CREATE TABLE IF NOT EXISTS `/' "$STRUCT"

echo ">>> 4/5 导出出厂数据"
DATA="$(mktemp)"
{
  echo "USE \`sdssys\`;"
  echo "SET FOREIGN_KEY_CHECKS = 0;"
  $DUMP --no-create-info bl_sdssys $SYS_TABLES | sed 's/^INSERT INTO /INSERT IGNORE INTO /'
  echo "SET FOREIGN_KEY_CHECKS = 1;"
  echo "USE \`sdsshop\`;"
  $DUMP --no-create-info bl_sdsshop u_m_doctypeconfig | sed 's/^INSERT INTO /INSERT IGNORE INTO /'
} >> "$DATA"

echo ">>> 5/5 拼装"
cat "$ROOT/tools/header_01.txt" "$STRUCT" "$DATA" > "$ROOT/01_schema_and_init.sql"
{
  cat "$ROOT/tools/header_02.txt"
  echo "USE \`sdsshop\`;"
  $DUMP --no-create-info sdsshop u_m_goodscategory u_m_waregoods u_m_warehouse u_m_partner \
    | sed 's/^INSERT INTO /INSERT IGNORE INTO /'
} > "$ROOT/02_demo_data.sql"
rm -f "$STRUCT" "$DATA"

# ---- 自检：这两条断言各自对应一次真实事故，别删 ----
fail=0
if grep -qniE '^\s*(drop|truncate|delete)\b' "$ROOT/01_schema_and_init.sql" "$ROOT/02_demo_data.sql"; then
  echo "!! 输出里有 DROP / TRUNCATE / DELETE。mysqldump 默认会加 DROP TABLE，"
  echo "   靠 --skip-add-drop-table 关掉。这个参数掉过一次，代价是一个开发库。"
  fail=1
fi
if grep -qE '^CREATE TABLE IF NOT EXISTS `[a-z_]+_20[0-9]{4}`' "$ROOT/01_schema_and_init.sql"; then
  echo "!! 月表混进基线了。月表归 MonthlyTableInitializer 启动时建，基线一张都不该有。"
  echo "   查 --ignore-table 那段的 tr -d '\\r' 还在不在。"
  fail=1
fi
[ $fail = 1 ] && { echo "生成失败，文件已写出但不可用。"; exit 1; }
echo "自检通过：无破坏性语句、无月表。"

echo
wc -l "$ROOT/01_schema_and_init.sql" "$ROOT/02_demo_data.sql"
echo "临时库 bl_sdssys / bl_sdsshop 保留着，用来和新库对拍；确认无误后可自行 DROP。"

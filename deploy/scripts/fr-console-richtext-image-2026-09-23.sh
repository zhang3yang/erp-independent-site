#!/usr/bin/env bash
#
# 上线：修好商品详情「插图」+ 灌 4 条商品的属性和图文详情。在仓库根目录跑：
#
#     bash deploy/scripts/fr-console-richtext-image-2026-09-23.sh
#
# 目标机：法国机 162.62.132.149（root 免密），Linux + systemd。
#
# 这一轮动**两样东西**：一个可执行产物（station-console 的 jar）和两份 SQL。
#
#   1. station-console-1.0.0.jar
#      修 HTMLFilter 把属性写成 src=\"https://…\" 的 bug。不换这个 jar，
#      迁移把历史数据修好了也没用——商家一插新图，存进去还是坏的。
#   2. 2026-09-23-fix-richtext-escaped-quotes.sql
#      把库里已经被写坏的 \" 捞回来（图片、链接都受影响）。
#   3. 2026-09-23-fr-goods-specs-desc.sql
#      4 条商品的属性 + fr/en 图文详情。
#
# ⚠ 顺序：上传 → 备份 → 迁移① → 迁移② → 换 jar + 重启 → 验收。
#   迁移放重启前是因为站点档案有**进程内缓存**（SiteInfoService）；jar 不换重启也没意义。
#
# ⚠ 图片地址本轮**不替换**（还是 https://file.example.com/… 占位）。
#   真实文件名要等商家把图传进文件库才知道，替换语句在那个 SQL 的 §⑤。
#
# ⚠ jar 的落地路径**不写死**，第一次跑时从线上现找（见步骤 1）。
#   写死一个猜的路径，cp 成功、服务起不来，是最难查的那种坏法。

set -euo pipefail

cd "$(dirname "$0")/../.."

FR=root@162.62.132.149
SSH="ssh -o BatchMode=yes -o ConnectTimeout=15 $FR"
SCP="scp -q -o BatchMode=yes"
STAMP=$(date +%Y%m%d-%H%M%S)

JAR=station-console/target/station-console-1.0.0.jar
FIX=sql/station/migration/2026-09-23-fix-richtext-escaped-quotes.sql
DATA=sql/station/migration/2026-09-23-fr-goods-specs-desc.sql

echo "== 0. 本地自检 =="
for f in "$JAR" "$FIX" "$DATA"; do
  [ -f "$f" ] || { echo "  ✗ 缺文件：$f"; exit 1; }
done
# 产物里必须是修过的（属性模板是 =" 而不是 =\"）。
# 做法：把那个 class 解出来，转成十六进制流，数 3d5c22（=" 带反斜杠）出现几次——必须是 0。
# ⚠ 用 `jar`（Java 自带）而不是 `unzip`：跑 Spring Boot 的机器必然有 jar，
#   不必然有 unzip。od 也是 coreutils，一定有。
_tmp=$(mktemp -d)
( cd "$_tmp" && jar xf "$OLDPWD/$JAR" BOOT-INF/classes/com/ruoyi/station/console/util/HTMLFilter.class )
nbad=$(od -An -tx1 -v "$_tmp/BOOT-INF/classes/com/ruoyi/station/console/util/HTMLFilter.class" \
       | tr -d ' \n' | grep -o '3d5c22' | wc -l | tr -d ' ')
rm -rf "$_tmp"
if [ "$nbad" != "0" ]; then
  echo "  ✗ 产物里的 HTMLFilter 还是旧写法（命中 $nbad 次 3d5c22），先重新 mvn package"; exit 1
fi
printf '  ok  jar=%s (%s)  属性模板检查通过\n' "$JAR" "$(du -h "$JAR" | cut -f1)"

echo "== 1. 连通性与现状 =="
$SSH 'systemctl is-active station-console station-site nginx | paste -sd" " | sed "s/^/  服务: /"'

# 线上 jar 在哪：从 systemd 单元里读，读不到再全局找一次
REMOTE_JAR=$($SSH 'systemctl cat station-console 2>/dev/null | grep -o "[^ ]*station-console[^ ]*\.jar" | head -1')
if [ -z "$REMOTE_JAR" ]; then
  REMOTE_JAR=$($SSH 'find /opt /root -maxdepth 4 -name "station-console-*.jar" 2>/dev/null | head -1')
fi
[ -n "$REMOTE_JAR" ] || { echo "  ✗ 线上找不到 station-console 的 jar，先人工确认部署路径"; exit 1; }
echo "  线上 jar: $REMOTE_JAR"
$SSH "ls -l '$REMOTE_JAR' | sed 's/^/    /'"

# 库结构必须齐（缺列说明那几版迁移没跑，先别继续）
$SSH 'P=$(cat /etc/station/.dbpw) && mysql -ustation -p"$P" -sN sdsstation 2>/dev/null -e "
  select concat(
    \"  specsjson(lang)=\", (select count(*) from information_schema.columns
       where table_schema=database() and table_name=\"u_s_sitegoods_lang\" and column_name=\"specsjson\"),
    \"  mainvideo=\",      (select count(*) from information_schema.columns
       where table_schema=database() and table_name=\"u_s_sitegoods\" and column_name=\"mainvideo\"),
    \"  consolelang=\",    (select count(*) from information_schema.columns
       where table_schema=database() and table_name=\"u_s_siteinfo\" and column_name=\"consolelang\"))
"'

# 顺手看一眼现在有多少坏数据（迁移①修的就是它）
$SSH 'P=$(cat /etc/station/.dbpw) && mysql -ustation -p"$P" -sN sdsstation 2>/dev/null -e "
  select concat(\"  当前详情里带 \\\\\" 的行数: \", count(*))
    from u_s_sitegoods_lang
   where description like concat(\"%\", char(92), \"\\\"\", \"%\")"'

echo "== 2. 上传 =="
# jar 可能已经先传好了（/root/stage-console.jar，体积大，重复传没必要）。
# 显式用一个环境变量控制：STAGE_JAR=1 表示复用。
if [ "${STAGE_JAR:-0}" = "1" ]; then
  $SSH "test -f /root/stage-console.jar" || { echo "  ✗ 指定复用但 /root/stage-console.jar 不在"; exit 1; }
  $SSH "cp -p /root/stage-console.jar /root/station-console-1.0.0.jar.$STAMP"
  echo "   复用已上传的 /root/stage-console.jar"
else
  $SCP "$JAR" "$FR:/root/station-console-1.0.0.jar.$STAMP"
  echo "   已上传 $JAR"
fi
$SCP "$FIX"  "$FR:/root/fix-richtext-$STAMP.sql"
$SCP "$DATA" "$FR:/root/fr-goods-specs-desc-$STAMP.sql"
echo "   ok"

echo "== 3. 备份（回滚就靠它） =="
$SSH "mkdir -p /root/backup && \
  cp -p '$REMOTE_JAR' /root/backup/station-console-1.0.0.jar.$STAMP && \
  echo \"  旧 jar \$(du -h /root/backup/station-console-1.0.0.jar.$STAMP | cut -f1)\" && \
  P=\$(cat /etc/station/.dbpw) && \
  mysqldump -ustation -p\"\$P\" --no-create-info --skip-add-locks sdsstation \
    u_s_sitegoods u_s_sitegoods_lang 2>/dev/null > /root/backup/fr-goods-\$STAMP.sql && \
  echo \"  数据 \$(wc -c < /root/backup/fr-goods-\$STAMP.sql) 字节\""

echo "== 4. 迁移①：修坏掉的历史数据 =="
$SSH "P=\$(cat /etc/station/.dbpw) && mysql -ustation -p\"\$P\" sdsstation < /root/fix-richtext-$STAMP.sql"

echo "== 5. 迁移②：4 条商品的属性 + 图文详情 =="
$SSH "P=\$(cat /etc/station/.dbpw) && mysql -ustation -p\"\$P\" sdsstation < /root/fr-goods-specs-desc-$STAMP.sql"

echo "== 6. 换 jar + 重启 =="
# 用 cp -p 保留原文件的属主/权限：新上传的那份 owner 是 root，
# 直接覆盖会让服务以 root 身份跑（systemd 单元里配的是 User=station）
$SSH "cp -p /root/station-console-1.0.0.jar.$STAMP '$REMOTE_JAR' && \
      systemctl restart station-console"
# ⚠ 不能只等 systemctl is-active：单元是 Type=simple，进程一起来就报 active，
#   而 Spring Boot 还要六秒才收请求。判据用「接口真的应答了」。
echo -n "   等后端起来"
$SSH 'for i in $(seq 1 40); do
        if curl -sf -m 3 -o /dev/null "http://127.0.0.1:8081/openapi/site/info"; then echo " ok"; exit 0; fi
        printf .
        sleep 2
      done
      echo " ✗ 40 次都没应答"; exit 1'
$SSH 'systemctl is-active station-console station-site nginx | paste -sd" " | sed "s/^/  服务: /"'

echo "== 7. 验收：把接口返回的 description 原样打出来 =="
# ⚠ 不用 python3 解 JSON：服务器上不一定有，而且为了一行判断多一个依赖不值。
#   直接用字符串判断——「有没有 <img src=\"」和「有没有带反斜杠的 src」两件事
#   用 grep -o 数就行，判据本身不依赖 JSON 结构。
$SSH 'for c in 12010304 12010901; do
        echo "  --- $c ---"
        body=$(curl -s -m 20 "http://127.0.0.1:8081/openapi/site/products/$c?lang=fr")
        good=$(printf "%s" "$body" | grep -o "<img src=\"" | wc -l | tr -d " ")
        bad=$(printf "%s"  "$body" | grep -o "src=\\\\\"" | wc -l | tr -d " ")
        printf "    <img src=\"  出现 %s 次\n" "$good"
        printf "    src=\\\" (坏) 出现 %s 次   %s\n" "$bad" "$([ "$bad" = "0" ] && echo OK || echo ！！！还在写坏)"
        printf "%s" "$body" | grep -o "<img src=\\\"[^\\\"]*\\\"" | head -2 | sed "s/^/      /"
      done'
echo
echo "   判据：good>=1（有图）且 bad==0（没有反斜杠）。"

echo
echo "完成。回滚："
echo "  jar  ssh $FR \"cp -p /root/backup/station-console-1.0.0.jar.$STAMP '$REMOTE_JAR' && systemctl restart station-console\""
echo "  数据 ssh $FR \"mysql ... < /root/backup/fr-goods-$STAMP.sql\""

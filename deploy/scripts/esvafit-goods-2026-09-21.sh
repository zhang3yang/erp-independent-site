#!/usr/bin/env bash
#
# Esva boots 商品目录上架 + 一处文案更正。在仓库根目录跑：
#
#     bash deploy/scripts/esvafit-goods-2026-09-21.sh
#
# 这一轮**只动数据，不动代码**，所以不需要重建三个模块——比上一轮快得多。
#
#   1. sql/station/migration/2026-09-21-esvafit-goods.sql
#      8 条商品（6 条主推）+ 48 行六语文案 + 11 个尺码型号 + 66 行型号名 + 35 行分类译名。
#   2. sql/station/migration/2026-09-21-esvafit-decorate.sql
#      装修内容整份重写一遍（幂等）。这一轮里唯一的变化是常见问题 q1：
#      原话承诺「每个商品页会列出每个码覆盖的范围」，而我们没有 Esva 的尺码表，
#      不能编，也不能抄 getjolt.fr 的。改成只承诺做得到的。
#
# ⚠ 顺序不能反：先建商品，再改装修。反过来的话，q1 新答案里说的「页面上能选码」
#   会有一小段时间是假的（商品还没建，商品页根本不存在）。
#
# ⚠ 跑完必须重启 station-console —— 站点档案缓存在进程里，不重启的话库改了、
#   首页那段常见问题不变。商品不受这个影响（不缓存），但一次重启两件事都到位。

set -euo pipefail

FR=root@162.62.132.149
SSH="ssh -o BatchMode=yes -o ConnectTimeout=15 $FR"
STAMP=$(date +%Y%m%d-%H%M%S)

GOODS=sql/station/migration/2026-09-21-esvafit-goods.sql
DECO=sql/station/migration/2026-09-21-esvafit-decorate.sql

echo "== 0. 本地自检 =="
node scripts/verify/check-init-json.js "$DECO" >/dev/null || { echo "装修脚本里的 JSON 坏了"; exit 1; }
for f in "$GOODS" "$DECO"; do [ -f "$f" ] || { echo "缺文件：$f"; exit 1; }; done
echo "   ok"

echo "== 1. 上传 =="
scp -q -o BatchMode=yes "$GOODS" "$FR:/root/esvafit-goods-$STAMP.sql"
scp -q -o BatchMode=yes "$DECO"  "$FR:/root/esvafit-decorate-$STAMP.sql"

echo "== 2. 备份（回滚就靠它） =="
# 装修内容整份存一份；商品这边本站现在一条 EB- 都没有，但仍然导一份，
# 重复执行这个脚本时那一份就是上一次的状态。
$SSH "mkdir -p /root/backup && P=\$(cat /etc/station/.dbpw) && \
  mysql -ustation -p\"\$P\" -sN --raw sdsstation \
    -e 'select contentjson from u_s_siteinfo where sitecode=\"DLZ2049739702\"' \
    2>/dev/null > /root/backup/contentjson-$STAMP.json && \
  echo \"  装修 \$(wc -c < /root/backup/contentjson-$STAMP.json) 字节\" && \
  mysqldump -ustation -p\"\$P\" --no-create-info --skip-add-locks sdsstation \
    u_s_sitegoods u_s_sitegoods_lang u_s_sitegoods_combo u_s_sitegoods_combo_lang u_s_sitecategory_lang \
    --where=\"sitecode='DLZ2049739702'\" 2>/dev/null > /root/backup/goods-$STAMP.sql && \
  echo \"  商品 \$(wc -c < /root/backup/goods-$STAMP.sql) 字节\""

echo "== 3. 建商品 =="
$SSH "P=\$(cat /etc/station/.dbpw) && mysql -ustation -p\"\$P\" sdsstation < /root/esvafit-goods-$STAMP.sql"

echo "== 4. 更正装修文案 =="
$SSH "P=\$(cat /etc/station/.dbpw) && mysql -ustation -p\"\$P\" sdsstation < /root/esvafit-decorate-$STAMP.sql"

echo "== 5. 重启 station-console（档案缓存在进程里） =="
$SSH 'systemctl restart station-console'
# ⚠ 不能只等 `systemctl is-active`。单元是 Type=simple，进程一起来 systemd 就报
#   active，而 Spring Boot 还要六秒才开始收请求。2026-09-21 就是在这个空窗里
#   跑了下面的 curl，商品列表页返回 503——数据全对，纯属误报。
#   判据换成「接口真的应答了」。
echo -n "   等后端起来"
$SSH 'for i in $(seq 1 40); do
        if curl -sf -m 3 -o /dev/null "http://127.0.0.1:8081/openapi/site/info"; then echo " ok"; exit 0; fi
        printf .
        sleep 2
      done
      echo " ✗ 40 次都没应答"; exit 1'
$SSH 'systemctl is-active station-console station-site nginx | paste -sd" "'

echo "== 6. 验一遍 =="
for path in /products /fr/products /zh/products; do
  printf '   %-40s %s\n' "esvafit.vclinker.com$path" \
    "$(curl -s -o /dev/null -w '%{http_code}' -m 25 "https://esvafit.vclinker.com$path")"
done
printf '   %-40s %s\n' 'esvafit.vclinker.com/products/EB-BOOT-01' \
  "$(curl -s -o /dev/null -w '%{http_code}' -m 25 'https://esvafit.vclinker.com/products/EB-BOOT-01')"

echo
echo "完成。回滚："
echo "  商品：ssh $FR \"mysql ... < /root/backup/goods-$STAMP.sql\"，或按 goods.sql 末尾那几条 DELETE 删干净"
echo "  装修：把 /root/backup/contentjson-$STAMP.json 写回 u_s_siteinfo.contentjson，再重启 station-console"

#!/usr/bin/env bash
#
# Esva boots 首页改版：发代码 + 换装修内容 + 重建 + 重启。
# 目标机：法国机 162.62.132.149（root 免密）。在仓库根目录跑：
#
#     bash deploy/scripts/esvafit-2026-09-21.sh
#
# 这一版改了什么：
#   · 首页新增四块（承诺横条 / 顾客评价 / 合作方 / 常见问题），端到端
#     ——前台 index.vue、后端 SiteOpenService#compose、管理台 DecorateForm.vue；
#   · 七个页面的界面文案表补上法语（2026-09-21 法语上线时漏的，法语站的区块标题
#     一直在显示英文）；
#   · 整份装修内容换成 Esva boots 自己的六语文案（原来是统领时代的硅胶管/气泵）。
#
# ⚠ 2G 内存：Nuxt 构建前必须先停 station-console 和 station-site（它们占 ~440M），
#   否则构建会被 OOM 杀掉。远端那个 rebuild 脚本里挂了 trap，失败也会把服务拉回来。
#
# ⚠ 启动远端脚本要**单独一条 ssh**：`ssh 机器 'a && b && x &'` 里的 `&` 作用在
#   整条 && 链上，ssh 一退就把它带走，脚本根本没跑起来。

set -euo pipefail

FR=root@162.62.132.149
SSH="ssh -o BatchMode=yes -o ConnectTimeout=15 $FR"
STAMP=$(date +%Y%m%d-%H%M%S)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# 这一版改动到的源文件。远端 /root/build 下不是 git 仓库，所以逐个送。
FILES=(
  station-shared/DecorateForm.vue
  station-shared/ItemRows.vue
  station-site/app/components/GlobalReach.vue
  station-site/app/components/InquiryForm.vue
  station-site/app/composables/useSite.ts
  "station-site/app/pages/blog/[slug].vue"
  station-site/app/pages/blog/index.vue
  station-site/app/pages/index.vue
  "station-site/app/pages/products/[code].vue"
  station-site/app/pages/products/index.vue
  station-console/src/main/java/com/ruoyi/station/console/service/SiteOpenService.java
)
SQL=sql/station/migration/2026-09-21-esvafit-decorate.sql

echo "== 0. 本地自检 =="
bash scripts/verify/run-all.sh >/dev/null || { echo "校验没过，别发"; exit 1; }
node scripts/verify/check-init-json.js "$SQL" >/dev/null || { echo "迁移脚本里的 JSON 坏了"; exit 1; }
echo "   ok"

echo "== 1. 打包（顺手把 CRLF 转成 LF） =="
mkdir -p "$TMP/stage"
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "缺文件：$f"; exit 1; }
  mkdir -p "$TMP/stage/$(dirname "$f")"
  tr -d '\r' < "$f" > "$TMP/stage/$f"
done
( cd "$TMP/stage" && tar czf ../sync.tgz . )
echo "   ${#FILES[@]} 个文件"

echo "== 2. 上传 =="
scp -q -o BatchMode=yes "$TMP/sync.tgz" "$FR:/root/sync-$STAMP.tgz"
scp -q -o BatchMode=yes "$SQL" "$FR:/root/esvafit-decorate-$STAMP.sql"

echo "== 3. 备份现在线上的装修内容（回滚就靠它） =="
$SSH "mkdir -p /root/backup && P=\$(cat /etc/station/.dbpw) && \
  mysql -ustation -p\"\$P\" -sN --raw sdsstation \
    -e 'select contentjson from u_s_siteinfo where sitecode=\"DLZ2049739702\"' \
    2>/dev/null > /root/backup/contentjson-$STAMP.json && wc -c /root/backup/contentjson-$STAMP.json"

echo "== 4. 解包源码 =="
$SSH "cd /root/build && tar xzf /root/sync-$STAMP.tgz && echo 解包完成"

echo "== 5. 换装修内容 =="
$SSH "P=\$(cat /etc/station/.dbpw) && mysql -ustation -p\"\$P\" sdsstation < /root/esvafit-decorate-$STAMP.sql"

echo "== 6. 重建三个模块（后台跑，2G 机器上大概 3~6 分钟） =="
# 远端那份 rebuild.sh 是 2026-09-21 加法语时写的，逻辑照旧（停服务 → 构建 → 部署 → 起服务）。
# 它里面那条改 langs 的 UPDATE 是幂等的，重复跑没事。
$SSH 'rm -f /root/rebuild.done /root/rebuild.fail'
$SSH 'setsid nohup bash /root/rebuild.sh >/dev/null 2>&1 </dev/null & disown' || true

echo "   等构建结束……"
for i in $(seq 1 60); do
  if $SSH 'test -f /root/rebuild.done' 2>/dev/null; then echo "   构建完成"; break; fi
  if $SSH 'test -f /root/rebuild.fail' 2>/dev/null; then
    echo "   ✗ 构建失败，日志尾部："
    $SSH 'tail -40 /root/rebuild.log'
    exit 1
  fi
  sleep 15
done

echo "== 7. 验一遍 =="
$SSH 'systemctl is-active station-console station-site nginx | paste -sd" "'
for host in esvafit.vclinker.com console.esvafit.vclinker.com; do
  printf '   %-34s %s\n' "$host" "$(curl -s -o /dev/null -w '%{http_code}' -m 20 "https://$host/")"
done
for path in / /fr /zh /ja /ru /es; do
  printf '   %-34s %s\n' "esvafit.vclinker.com$path" \
    "$(curl -s -o /dev/null -w '%{http_code}' -m 20 "https://esvafit.vclinker.com$path")"
done

echo
echo "完成。回滚：把 /root/backup/contentjson-$STAMP.json 写回 u_s_siteinfo.contentjson，"
echo "          再 systemctl restart station-console。"

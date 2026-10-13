#!/usr/bin/env bash
#
# Esva boots 视觉改版：把前台从紫色 spa 调子换成 logo 的黑 + 电光蓝。在仓库根目录跑：
#
#     bash deploy/scripts/esvafit-theme-2026-09-21.sh
#
# 改了什么：
#   · 调色板整套换掉（从 logo 图上取样：79% 近黑、蓝在 #0196FA 一带）；
#   · 标题字体 Fraunces 衬线 → Archivo 重体无衬线；
#   · Banner 从「浅蒙版 + 深色字」翻成「深蒙版 + 白字」；
#   · 顶栏从白翻成深色 —— logo 自带黑底，压在白顶栏上像贴纸，这是最扎眼的一处；
#   · 三栏特色里那张粉色数字卡 → logo 蓝实底 + 近黑字；
#   · 按钮从「紫底白字胶囊」→「logo 蓝底近黑字、圆角收紧」。
#
# **只动前台，不动后端和管理台**，所以只重建 station-site（见 remote-rebuild-site.sh）。
# 装修内容和商品数据一个字没改，不用跑 SQL。

set -euo pipefail

FR=root@162.62.132.149
SSH="ssh -o BatchMode=yes -o ConnectTimeout=15 $FR"
STAMP=$(date +%Y%m%d-%H%M%S)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "== 0. 本地自检 =="
bash scripts/verify/run-all.sh >/dev/null || { echo "校验没过，别发"; exit 1; }
echo "   ok（含配色对比度那一项）"

echo "== 1. 打包（整个 app 目录 + nuxt.config，顺手 CRLF → LF） =="
# 这轮改动散在 26 个文件里（换色是全局的），与其逐个列不如整份同步 ——
# 整份同步还有个好处：机器上的源码和本地**逐字节一致**，不会留下上一轮的残余。
mkdir -p "$TMP/stage/station-site"
while IFS= read -r f; do
  mkdir -p "$TMP/stage/$(dirname "$f")"
  tr -d '\r' < "$f" > "$TMP/stage/$f"
done < <(find station-site/app -type f \( -name '*.vue' -o -name '*.ts' -o -name '*.css' \))
tr -d '\r' < station-site/nuxt.config.ts > "$TMP/stage/station-site/nuxt.config.ts"
( cd "$TMP/stage" && tar czf ../site.tgz . )
echo "   $(find "$TMP/stage" -type f | wc -l) 个文件"

echo "== 2. 上传 =="
scp -q -o BatchMode=yes "$TMP/site.tgz" "$FR:/root/site-$STAMP.tgz"
scp -q -o BatchMode=yes deploy/scripts/remote-rebuild-site.sh "$FR:/root/rebuild-site.sh"

echo "== 3. 备份现在跑着的那份构建产物 =="
# 视觉改版不动数据，所以要能回滚的只有 .output 这一份。
$SSH "rm -rf /root/backup/site-output-prev && mkdir -p /root/backup && \
  cp -r /opt/station/site/.output /root/backup/site-output-$STAMP && \
  du -sh /root/backup/site-output-$STAMP"

echo "== 4. 解包源码 =="
$SSH "cd /root/build && tar xzf /root/site-$STAMP.tgz && chmod +x /root/rebuild-site.sh && echo 解包完成"

echo "== 5. 重建前台（2G 机器上 1~2 分钟；期间站点会短暂中断） =="
$SSH 'rm -f /root/rebuild-site.done /root/rebuild-site.fail'
# ⚠ 启动要**单独一条 ssh**：`ssh 机器 'a && b && x &'` 里的 & 作用在整条 && 链上，
#   ssh 一退就把它带走，脚本根本没跑起来。
$SSH 'setsid nohup bash /root/rebuild-site.sh >/dev/null 2>&1 </dev/null & disown' || true

echo -n "   等构建结束"
for i in $(seq 1 40); do
  if $SSH 'test -f /root/rebuild-site.done' 2>/dev/null; then echo " 完成"; break; fi
  if $SSH 'test -f /root/rebuild-site.fail' 2>/dev/null; then
    echo " ✗ 失败，日志尾部："
    $SSH 'tail -40 /root/rebuild-site.log'
    exit 1
  fi
  printf .
  sleep 10
done

echo "== 6. 验一遍 =="
$SSH 'systemctl is-active station-console station-site nginx | paste -sd" "'
for path in / /fr /zh /products /products/EB-BOOT-01; do
  printf '   %-42s %s\n' "esvafit.vclinker.com$path" \
    "$(curl -s -o /dev/null -w '%{http_code}' -m 25 "https://esvafit.vclinker.com$path")"
done

# 新主题真的上去了没有：直接在首页 HTML 里找新旧两套的特征值
echo "   —— 主题特征 ——"
HTML=$(curl -s -m 25 https://esvafit.vclinker.com/)
printf '   %-42s %s\n' '旧紫 #6c4b9c 残留' "$(printf '%s' "$HTML" | grep -c '6c4b9c' || true)"
printf '   %-42s %s\n' '旧衬线 Fraunces 残留' "$(printf '%s' "$HTML" | grep -c 'Fraunces' || true)"
printf '   %-42s %s\n' '新字体 Archivo' "$(printf '%s' "$HTML" | grep -c 'Archivo' || true)"
printf '   %-42s %s\n' 'logo 蓝 #0196fa' "$(printf '%s' "$HTML" | grep -oic '0196fa' | wc -l || true)"

echo
echo "完成。回滚（不用重建，直接换回上一份构建产物）："
echo "  ssh $FR 'systemctl stop station-site && rm -rf /opt/station/site/.output &&"
echo "           cp -r /root/backup/site-output-$STAMP /opt/station/site/.output &&"
echo "           chown -R station:station /opt/station/site && systemctl start station-site'"

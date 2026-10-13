#!/usr/bin/env bash
# =====================================================================
# 清掉「200 张背包占位图」这批测试数据
# =====================================================================
#
# 那批东西的两个标记，删除全靠它们，别改：
#   · 库里 imageid 以 SIMG9 开头
#   · 磁盘上在 /data/station/upload/test-backpack/
#
# ⚠ 真实图片的 imageid 是发号器出的 SIMG000000000x，永远不会撞上 SIMG9 开头
#   （要撞得先发到 90 亿号）。所以这个前缀删起来是安全的。
#
# 用法：sudo bash cleanup-test-backpack.sh
# =====================================================================
set -euo pipefail

SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
DIR=/data/station/upload/test-backpack

echo "== 删除前"
$SUDO mysql -N sdsstation -e 'select concat("  图片总数 ", count(*)) from u_s_siteimage;'
$SUDO mysql -N sdsstation -e 'select concat("  其中测试数据 ", count(*)) from u_s_siteimage where imageid like "SIMG9%";'

# ⚠ 先解除文件夹归属再删行：这批图可能已经被拖进某个文件夹了，
#   直接删行没问题，但先清一下能让树上的计数立刻回正
$SUDO mysql sdsstation -e 'update u_s_siteimage set folderid = null where imageid like "SIMG9%";'
$SUDO mysql sdsstation -e 'delete from u_s_siteimage where imageid like "SIMG9%";'
$SUDO rm -rf "$DIR"

echo "== 删除后"
$SUDO mysql -N sdsstation -e 'select concat("  图片总数 ", count(*)) from u_s_siteimage;'
[ -d "$DIR" ] && echo "  ⚠ 目录还在：$DIR" || echo "  磁盘目录已删"
echo "✓ 清理完成。真实图片一张没动。"

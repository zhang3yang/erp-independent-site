#!/usr/bin/env bash
# 目标机预检：只读，不改任何东西。SSH 一通先跑这个，看清底子再恢复。
echo "===== 系统 ====="
. /etc/os-release; echo "$PRETTY_NAME  $(uname -srm)"
echo "内存: $(free -h | awk '/Mem:/{print $2" 总 / "$7" 可用"}')"
echo "磁盘: $(df -h / | awk 'NR==2{print $2" 总 / "$4" 可用"}')"

echo; echo "===== 已装的运行时 ====="
for c in java node npm nginx mysql redis-server docker; do
  printf '%-14s' "$c"
  command -v $c >/dev/null && ($c --version 2>&1 || $c -v 2>&1) | head -1 || echo "未安装"
done

echo; echo "===== 端口占用（关键：80/443/3000/8081/3306/6379）====="
sudo ss -lntp | awk 'NR==1 || /:80 |:443 |:3000 |:8081 |:3306 |:6379 /'

echo; echo "===== Docker 容器（镜像自带 Docker，可能占端口）====="
sudo docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || echo "docker 不可用"

echo; echo "===== MySQL ====="
sudo mysql -N -e 'select version();' 2>&1 | head -2
echo "-- 现有库:"; sudo mysql -N -e 'show databases;' 2>&1 | grep -vE '^(information_schema|performance_schema|sys|mysql)$'
echo "-- 现有账号:"; sudo mysql -N -e 'select concat(user,"@",host) from mysql.user;' 2>&1
echo "-- ⚠ sdsstation 若已存在，恢复脚本会拒绝覆盖"

echo; echo "===== 会冲突的既有内容 ====="
for p in /opt/station /data/station /etc/station /etc/nginx/cert /etc/nginx/conf.d/tongling.conf; do
  [ -e "$p" ] && echo "已存在: $p" || echo "干净:   $p"
done
id station 2>/dev/null && echo "⚠ station 用户已存在" || echo "干净:   station 用户不存在"
echo "uid 999 被占: $(getent passwd 999 || echo 否)"
echo "gid 988 被占: $(getent group 988 || echo 否)"

echo; echo "===== 出网（装包要用）====="
for u in mirrors.tencentyun.com archive.ubuntu.com; do
  timeout 6 bash -c "echo > /dev/tcp/$u/80" 2>/dev/null && echo "$u 通" || echo "$u 不通"
done

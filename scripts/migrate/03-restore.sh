#!/usr/bin/env bash
# 目标机恢复：解包 → 建号建库 → 导数据 → 铺文件 → 起服务
# 前置：02-install-deps.sh 跑过，/tmp/station-migrate.tar.gz 已传到本机
# 幂等性：库已存在会中止（不覆盖数据）；文件会覆盖，旧的自动备份成 .bak-时间戳
set -euo pipefail

PKG=${1:-/tmp/station-migrate.tar.gz}
SRC=/tmp/station-migrate
TS=$(date +%Y%m%d-%H%M%S)
[ -f "$PKG" ] || { echo "找不到迁移包 $PKG"; exit 1; }

echo "===== 0. 解包"
rm -rf "$SRC"; tar -C /tmp -xzf "$PKG"
cat "$SRC/meta/source-env.txt"

echo; echo "===== 1. station 用户"
getent group station >/dev/null || sudo groupadd --system station
id station &>/dev/null || sudo useradd --system --gid station --no-create-home --shell /usr/sbin/nologin station
id station

echo; echo "===== 2. 建库与账号"
if sudo mysql -N -e 'show databases;' | grep -qx sdsstation; then
  echo "⛔ sdsstation 已存在。先确认能否覆盖，要覆盖就手动 drop 后重跑。"; exit 1
fi
# 密码从随包带过来的 env 里取，全程不落屏、不进 shell history
DBPASS=$(sudo grep -m1 '^DB_PASSWORD=' "$SRC/etc/station/console.env" | cut -d= -f2-)
[ -n "$DBPASS" ] || { echo "⛔ console.env 里没读到 DB_PASSWORD"; exit 1; }
sudo mysql <<SQL
CREATE DATABASE sdsstation CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS 'station'@'127.0.0.1' IDENTIFIED BY '$DBPASS';
GRANT ALL PRIVILEGES ON sdsstation.* TO 'station'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
unset DBPASS
echo "建库完成"

echo; echo "===== 3. 导入数据"
sudo mysql --default-character-set=utf8mb4 sdsstation < "$SRC/db/sdsstation.sql"
echo "导入表数: $(sudo mysql -N -e 'select count(*) from information_schema.tables where table_schema="sdsstation"')"

echo; echo "===== 4. 应用产物"
bak() { [ -e "$1" ] && sudo mv "$1" "$1.bak-$TS" && echo "  旧的备份为 $1.bak-$TS"; return 0; }
sudo mkdir -p /opt/station/{console,site,console-ui} /data/station
bak /opt/station/console/station-console-1.0.0.jar
bak /opt/station/site/.output
bak /opt/station/console-ui/dist
sudo cp    "$SRC"/app/console/station-console-1.0.0.jar /opt/station/console/
sudo cp -a "$SRC"/app/site/.output                      /opt/station/site/
sudo cp -a "$SRC"/app/console-ui/dist                   /opt/station/console-ui/
bak /data/station/upload
sudo cp -a "$SRC"/data/upload                           /data/station/
sudo chown -R station:station /opt/station /data/station

echo; echo "===== 5. 配置 / 证书 / systemd"
sudo mkdir -p /etc/station
sudo cp -a "$SRC"/etc/station/console.env "$SRC"/etc/station/site.env /etc/station/
sudo chown root:station /etc/station/*.env && sudo chmod 640 /etc/station/*.env
sudo cp -a "$SRC"/etc/nginx/cert            /etc/nginx/
# 打包时 chown 过一次，这里改回源机的 root:root
sudo chown -R root:root /etc/nginx/cert && sudo chmod 755 /etc/nginx/cert
sudo find /etc/nginx/cert -name privkey.pem   -exec chmod 600 {} ;
sudo find /etc/nginx/cert -name fullchain.pem -exec chmod 644 {} ;
sudo cp -a "$SRC"/etc/nginx/station-proxy.conf /etc/nginx/
sudo cp -a "$SRC"/etc/nginx/conf.d/tongling.conf /etc/nginx/conf.d/
sudo cp -a "$SRC"/etc/systemd/station-*.service /etc/systemd/system/
sudo systemctl daemon-reload

echo; echo "===== 6. 起服务"
# ⚠ apt 装的 nginx 自带 sites-enabled/default，它也声明 `listen 80 default_server`，
#   和 tongling.conf 的 444 兜底段撞车，nginx -t 会报 "duplicate default server"。
#   源机上这个文件早删了，新装机器又带回来 —— 必删。
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
sudo systemctl enable --now station-console station-site
sleep 8

echo; echo "===== 7. 自检"
sudo systemctl --no-pager --lines=0 status station-console station-site nginx redis-server mysql | grep -E 'station-|nginx|redis|mysql|Active:'
echo "-- 端口"; sudo ss -lntp | grep -E ':8081|:3000|:80 |:443 ' || echo "⚠ 端口没起来"
echo "-- 后端"; curl -s -o /dev/null -w "  8081 → %{http_code}\n" --max-time 10 http://127.0.0.1:8081/openapi/site/ || true
echo "-- 前台"; curl -s -o /dev/null -w "  3000 → %{http_code}\n" --max-time 15 http://127.0.0.1:3000/ || true
echo
echo "都是 200/404 就算通了（404 也说明进程活着）。失败看日志："
echo "  sudo journalctl -u station-console -n 60 --no-pager"
echo "  sudo journalctl -u station-site -n 60 --no-pager"

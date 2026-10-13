#!/usr/bin/env bash
# 在源机 129.211.4.32 上执行：把独立站的全部运行态打成一个包
# 产物 /tmp/station-migrate.tar.gz —— 含密码和私钥，只在两台服务器之间搬，别外发
set -euo pipefail

STAGE=/tmp/station-migrate
rm -rf "$STAGE" /tmp/station-migrate.tar.gz
mkdir -p "$STAGE"/{app,data,etc/station,etc/nginx/conf.d,etc/systemd,db,meta}

echo "== 1/5 应用产物（跳过 .bak）"
mkdir -p "$STAGE/app/console" "$STAGE/app/site" "$STAGE/app/console-ui"
cp /opt/station/console/station-console-1.0.0.jar "$STAGE/app/console/"
cp -a /opt/station/site/.output            "$STAGE/app/site/"
cp -a /opt/station/console-ui/dist         "$STAGE/app/console-ui/"

echo "== 2/5 上传文件"
sudo cp -a /data/station/upload "$STAGE/data/"

echo "== 3/5 配置 / 证书 / systemd"
sudo cp -a /etc/station/console.env /etc/station/site.env  "$STAGE/etc/station/"
sudo cp -a /etc/nginx/conf.d/tongling.conf                 "$STAGE/etc/nginx/conf.d/"
sudo cp -a /etc/nginx/station-proxy.conf                   "$STAGE/etc/nginx/"
sudo cp -a /etc/nginx/cert                                 "$STAGE/etc/nginx/"
sudo cp -a /etc/systemd/system/station-console.service \
           /etc/systemd/system/station-site.service        "$STAGE/etc/systemd/"

echo "== 4/5 数据库 sdsstation"
sudo mysqldump --single-transaction --routines --triggers --events \
     --default-character-set=utf8mb4 sdsstation > "$STAGE/db/sdsstation.sql"

echo "== 5/5 环境快照"
{
  echo "打包时间: $(date -Is)"
  echo "源机: $(hostname) $(uname -srm)"
  echo "系统: $(. /etc/os-release; echo "$PRETTY_NAME")"
  echo "java: $(java -version 2>&1 | head -1)"
  echo "node: $(node -v)"
  echo "nginx: $(nginx -v 2>&1)"
  echo "mysql: $(mysql --version)"
  echo "redis: $(redis-server -v | cut -d' ' -f1-3)"
  echo "station uid/gid: $(id station)"
  echo "库表数: $(sudo mysql -N -e 'select count(*) from information_schema.tables where table_schema="sdsstation"')"
} > "$STAGE/meta/source-env.txt"
cat "$STAGE/meta/source-env.txt"

# ⚠ 为了让当前用户能打 tar 才 chown。代价是 cp -a 到目标机后，证书属主会变成 ubuntu，
#   而源机上是 root:root。功能不受影响（600 权限 + nginx master 以 root 读），
#   但 03 恢复完要把 /etc/nginx/cert 的属主改回 root:root。
sudo chown -R "$USER:$USER" "$STAGE"
tar -C /tmp -czf /tmp/station-migrate.tar.gz station-migrate
rm -rf "$STAGE"
echo
echo "完成: $(ls -lh /tmp/station-migrate.tar.gz | awk '{print $5, $9}')"
echo "sha256: $(sha256sum /tmp/station-migrate.tar.gz | cut -c1-16)…"

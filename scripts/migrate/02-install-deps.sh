#!/usr/bin/env bash
# 目标机装依赖：MySQL / JDK17 / Node22 / Nginx / Redis
# 幂等：装过的跳过，绝不碰已有实例的数据。
set -euo pipefail
NODE_VER=v22.23.2   # 与源机一致

need() { command -v "$1" >/dev/null; }

echo "== apt 更新"
sudo apt-get update -qq

echo "== MySQL 8.0"
# ⚠ 目标机是 Docker 镜像，未必自带 MySQL
if need mysql && sudo systemctl is-active --quiet mysql; then
  echo "  已有并在运行，跳过"
else
  sudo apt-get install -y -qq mysql-server
  sudo systemctl enable --now mysql
fi
# apt 装出来的 root 走 auth_socket，`sudo mysql` 免密直进 —— 03 脚本依赖这一点
sudo mysql -N -e 'select version();'

echo "== JDK 17"
if need java && java -version 2>&1 | grep -q '"17\.'; then echo "  已有，跳过"
else sudo apt-get install -y -qq openjdk-17-jre-headless; fi
java -version 2>&1 | head -1

echo "== Nginx"
need nginx && echo "  已有，跳过" || sudo apt-get install -y -qq nginx
sudo systemctl enable --now nginx
nginx -v 2>&1

echo "== Redis"
need redis-server && echo "  已有，跳过" || sudo apt-get install -y -qq redis-server
sudo systemctl enable --now redis-server
redis-server -v | cut -d' ' -f1-3

echo "== Node $NODE_VER"
# systemd unit 里写死的是 /usr/bin/node，所以必须软链过去
if need node && [ "$(node -v)" = "$NODE_VER" ]; then
  echo "  已有 $NODE_VER，跳过"
else
  TARBALL=node-$NODE_VER-linux-x64.tar.xz
  cd /tmp
  # 腾讯云内网镜像优先，失败回落官方
  curl -fsSL -o "$TARBALL" "https://mirrors.tencentyun.com/nodejs-release/$NODE_VER/$TARBALL" \
    || curl -fsSL -o "$TARBALL" "https://mirrors.cloud.tencent.com/nodejs-release/$NODE_VER/$TARBALL" \
    || curl -fsSL -o "$TARBALL" "https://nodejs.org/dist/$NODE_VER/$TARBALL"
  sudo rm -rf /usr/local/node
  sudo mkdir -p /usr/local/node
  sudo tar -xJf "$TARBALL" -C /usr/local/node --strip-components=1
  sudo ln -sf /usr/local/node/bin/node /usr/bin/node
  sudo ln -sf /usr/local/node/bin/npm  /usr/bin/npm
  rm -f "$TARBALL"
fi
node -v; ls -l /usr/bin/node

echo; echo "== 全部就绪"

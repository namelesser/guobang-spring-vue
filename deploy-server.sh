#!/usr/bin/env bash
set -euo pipefail

server_root=/opt/guobang
server_host=root@117.72.179.214
release_id="$(date +%Y%m%d%H%M%S)"
release_dir="$server_root/releases/$release_id"
java_image=guobang-java:17-jre-jammy-amd64
nginx_image=guobang-nginx:alpine-amd64

test -f backend/target/transport-1.0.0.jar
test -d frontend/dist

if ! ssh "$server_host" "docker image inspect '$java_image' '$nginx_image' >/dev/null 2>&1"; then
  docker pull --platform linux/amd64 eclipse-temurin:17-jre-jammy
  docker tag eclipse-temurin:17-jre-jammy "$java_image"
  docker pull --platform linux/amd64 nginx:alpine
  docker tag nginx:alpine "$nginx_image"
  docker save "$java_image" "$nginx_image" | ssh "$server_host" docker load >/dev/null
fi

ssh "$server_host" "mkdir -p '$release_dir/backend/target' '$release_dir/frontend/dist' '$release_dir/deploy/server'"
rsync -az backend/target/transport-1.0.0.jar "$server_host:$release_dir/backend/target/"
rsync -az --delete frontend/dist/ "$server_host:$release_dir/frontend/dist/"
rsync -az deploy/server/ "$server_host:$release_dir/deploy/server/"

ssh "$server_host" "
  ln -sfn '$release_dir' '$server_root/current.next'
  mv -Tf '$server_root/current.next' '$server_root/current'
  docker rm -f guobang-frontend guobang-backend 2>/dev/null || true
  docker run -d --name guobang-backend --network host --restart unless-stopped \\
    --env-file '$server_root/shared/.env' \\
    -e SERVER_PORT=8000 -e PG_HOST=100.64.0.3 -e PG_PORT=5433 \\
    -e PG_DATABASE=transport -e PG_USER=transport -e FLYWAY_ENABLED=true \\
    -v '$server_root/current/backend/target/transport-1.0.0.jar:/app/app.jar:ro' \\
    '$java_image' java -jar /app/app.jar
  docker run -d --name guobang-frontend --restart unless-stopped \\
    --add-host host.docker.internal:host-gateway -p 9527:80 \\
    -v '$server_root/current/frontend/dist:/usr/share/nginx/html:ro' \\
    -v '$server_root/current/deploy/server/nginx.conf:/etc/nginx/conf.d/default.conf:ro' \\
    '$nginx_image'
"

echo "Published $release_id to http://117.72.179.214:9527"

#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/logs"
PID_DIR="$ROOT_DIR/.pids"

BACKEND_PORT="${BACKEND_PORT:-${SERVER_PORT:-8000}}"
FRONTEND_PORT="${FRONTEND_PORT:-9527}"
FRONTEND_HOST="${FRONTEND_HOST:-0.0.0.0}"
FRONTEND_BUILD_SCRIPT="${FRONTEND_BUILD_SCRIPT:-build:test}"
STARTUP_TIMEOUT_SEC="${STARTUP_TIMEOUT_SEC:-90}"
SKIP_BUILD="${SKIP_BUILD:-0}"
SKIP_TESTS="${SKIP_TESTS:-1}"

DEFAULT_AUTH_SECRET="guobang-secret-key-2024"
DEFAULT_AUTH_PASSWORD="552300"
DEFAULT_PG_PASSWORD="552300"
JAVA_NON_PROXY_HOSTS='localhost|*.localhost|127.*|10.*|172.*|192.168.*|100.64.*'

log() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<EOF
Usage:
  ./start-all.sh             Build backend/frontend, kill occupied ports, start both services
  ./start-all.sh restart     Same as default
  ./start-all.sh stop        Kill services listening on backend/frontend ports
  ./start-all.sh --help      Show this help

Environment overrides:
  BACKEND_PORT=8000          Backend port, also exported as SERVER_PORT
  FRONTEND_PORT=9527         Frontend Vite dev server port
  FRONTEND_HOST=0.0.0.0      Frontend bind host
  FRONTEND_BUILD_SCRIPT=build:test
  SKIP_BUILD=1               Skip frontend/backend build before restart
  SKIP_TESTS=0               Run backend tests during Maven package
  TRANSPORT_AUTH_SECRET=...  Backend auth secret, defaults to local dev value
  TRANSPORT_AUTH_PASSWORD=... Backend login password, defaults to local dev value
  PG_PASSWORD=...            PostgreSQL password, defaults to local dev value

Logs:
  $LOG_DIR/backend.log
  $LOG_DIR/frontend.log
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

kill_port() {
  local port="$1"
  local label="$2"
  local pids

  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    log "$label port $port is free"
    return
  fi

  log "Stopping $label process(es) on port $port: ${pids//$'\n'/ }"
  kill $pids 2>/dev/null || true
  sleep 2

  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    log "Force stopping $label process(es) on port $port: ${pids//$'\n'/ }"
    kill -9 $pids 2>/dev/null || true
  fi
}

stop_services() {
  require_cmd lsof
  kill_port "$FRONTEND_PORT" "frontend"
  kill_port "$BACKEND_PORT" "backend"
  rm -f "$PID_DIR/frontend.pid" "$PID_DIR/backend.pid"
}

ensure_frontend_deps() {
  require_cmd pnpm

  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    log "frontend/node_modules not found; running pnpm install"
    (cd "$FRONTEND_DIR" && pnpm install)
  fi
}

build_frontend() {
  ensure_frontend_deps
  log "Building frontend with: pnpm $FRONTEND_BUILD_SCRIPT"
  (cd "$FRONTEND_DIR" && pnpm "$FRONTEND_BUILD_SCRIPT")
}

build_backend() {
  [[ -x "$BACKEND_DIR/mvnw" ]] || die "Backend Maven wrapper not found or not executable: $BACKEND_DIR/mvnw"

  if [[ "$SKIP_TESTS" == "0" || "$SKIP_TESTS" == "false" ]]; then
    log "Building backend with tests: ./mvnw package"
    (cd "$BACKEND_DIR" && ./mvnw package)
  else
    log "Building backend without running tests: ./mvnw -DskipTests package"
    (cd "$BACKEND_DIR" && ./mvnw -DskipTests package)
  fi
}

find_backend_jar() {
  find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print | sort | head -n 1
}

ensure_backend_jar() {
  local jar
  jar="$(find_backend_jar)"
  [[ -n "$jar" ]] || die "Backend jar not found under $BACKEND_DIR/target; run with SKIP_BUILD=0 first"
}

wait_for_port() {
  local label="$1"
  local port="$2"
  local pid_file="$3"
  local log_file="$4"
  local pid

  pid="$(cat "$pid_file")"
  for _ in $(seq 1 "$STARTUP_TIMEOUT_SEC"); do
    if ! kill -0 "$pid" 2>/dev/null; then
      printf '\n%s exited before port %s opened. Last log lines:\n' "$label" "$port" >&2
      tail -n 80 "$log_file" >&2 || true
      exit 1
    fi

    if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      log "$label is listening on port $port"
      return
    fi

    sleep 1
  done

  printf '\nTimed out waiting for %s on port %s. Last log lines:\n' "$label" "$port" >&2
  tail -n 80 "$log_file" >&2 || true
  exit 1
}

start_backend() {
  require_cmd java

  local jar
  jar="$(find_backend_jar)"
  [[ -n "$jar" ]] || die "Backend jar not found under $BACKEND_DIR/target; run with SKIP_BUILD=0 first"

  log "Starting backend from $(basename "$jar")"
  (
    cd "$BACKEND_DIR"
    nohup env \
      SERVER_PORT="$BACKEND_PORT" \
      BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:$BACKEND_PORT}" \
      TRANSPORT_AUTH_SECRET="${TRANSPORT_AUTH_SECRET:-$DEFAULT_AUTH_SECRET}" \
      TRANSPORT_AUTH_PASSWORD="${TRANSPORT_AUTH_PASSWORD:-$DEFAULT_AUTH_PASSWORD}" \
      PG_PASSWORD="${PG_PASSWORD:-$DEFAULT_PG_PASSWORD}" \
      java \
        "-Dhttp.nonProxyHosts=$JAVA_NON_PROXY_HOSTS" \
        "-DsocksNonProxyHosts=$JAVA_NON_PROXY_HOSTS" \
        -jar "$jar" >"$LOG_DIR/backend.log" 2>&1 &
    echo $! >"$PID_DIR/backend.pid"
  )
}

start_frontend() {
  require_cmd pnpm

  log "Starting frontend dev server"
  (
    cd "$FRONTEND_DIR"
    nohup pnpm dev -- --host "$FRONTEND_HOST" --port "$FRONTEND_PORT" >"$LOG_DIR/frontend.log" 2>&1 &
    echo $! >"$PID_DIR/frontend.pid"
  )
}

main() {
  local command="${1:-restart}"

  case "$command" in
    restart|start)
      ;;
    stop)
      stop_services
      log "Stopped services on ports $BACKEND_PORT and $FRONTEND_PORT"
      return
      ;;
    -h|--help|help)
      usage
      return
      ;;
    *)
      usage
      die "Unknown command: $command"
      ;;
  esac

  require_cmd lsof
  mkdir -p "$LOG_DIR" "$PID_DIR"

  if [[ "$SKIP_BUILD" == "1" || "$SKIP_BUILD" == "true" ]]; then
    log "Skipping builds because SKIP_BUILD=$SKIP_BUILD"
    ensure_backend_jar
  else
    build_frontend
    build_backend
  fi

  stop_services
  start_backend
  start_frontend

  wait_for_port "backend" "$BACKEND_PORT" "$PID_DIR/backend.pid" "$LOG_DIR/backend.log"
  wait_for_port "frontend" "$FRONTEND_PORT" "$PID_DIR/frontend.pid" "$LOG_DIR/frontend.log"

  log "Ready"
  printf 'Backend:  http://localhost:%s\n' "$BACKEND_PORT"
  printf 'Frontend: http://localhost:%s\n' "$FRONTEND_PORT"
  printf 'Logs:     %s/backend.log, %s/frontend.log\n' "$LOG_DIR" "$LOG_DIR"
}

main "$@"

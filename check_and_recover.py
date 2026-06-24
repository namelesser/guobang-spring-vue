#!/usr/bin/env python3
"""
过磅项目本地服务健康检查 + 自动恢复脚本 (Spring Boot 3 + Vue 3)

检查:
  1. PostgreSQL (port 5432) — pg_isready
  2. 后端 Spring Boot (port 8000) — /actuator/health
  3. 前端 Vite dev server (port 9527) — HTTP 200

自动恢复:
  - PG 挂了 → brew services restart postgresql@17
  - 后端挂了 → 从 creds.sh 提取密码，启动 mvn spring-boot:run
  - 前端挂了 → pnpm dev

输出: 无输出=健康, 有输出=告警/恢复信息 (给 cron 推微信用)
"""

import subprocess
import socket
import time
import sys
import os
import re

PROJECT_ROOT = os.path.expanduser("~/.hermes/scripts/guobang-spring-vue")
BACKEND_PORT = 8000
FRONTEND_PORT = 9527
PG_PORT = 5432
MAX_RECOVERY_WAIT = 20  # seconds
CREDS_SCRIPT = os.path.expanduser("~/.hermes/scripts/creds/creds.sh")


def port_open(port: int) -> bool:
    """检查端口是否在监听"""
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=2):
            return True
    except (ConnectionRefusedError, OSError):
        return False


def http_ok(url: str, timeout: int = 5) -> bool:
    """HTTP GET 检查是否返回 2xx"""
    try:
        import urllib.request
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return 200 <= resp.status < 300
    except Exception:
        return False


def run_cmd(cmd: str, cwd: str = None, background: bool = False) -> tuple:
    """运行 shell 命令"""
    try:
        if background:
            subprocess.Popen(
                cmd, shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                cwd=cwd or PROJECT_ROOT, start_new_session=True
            )
            return 0, "started"
        result = subprocess.run(
            cmd, shell=True, capture_output=True, text=True,
            timeout=30, cwd=cwd or PROJECT_ROOT
        )
        return result.returncode, result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        return -1, "timeout"
    except Exception as e:
        return -1, str(e)


def get_credential(name: str):
    """从 creds.sh 获取凭证值"""
    code, out = run_cmd(f"bash {CREDS_SCRIPT} get {name}")
    if code != 0:
        return None
    # creds.sh 输出格式: key=value 或直接值
    for line in out.strip().splitlines():
        line = line.strip()
        if "=" in line:
            return line.split("=", 1)[1].strip()
        if line and not line.startswith("#"):
            return line
    return None


def check_postgres() -> bool:
    """检查 PostgreSQL 是否可用"""
    return port_open(PG_PORT)


def recover_postgres() -> bool:
    """尝试恢复 PostgreSQL"""
    pid_file = "/opt/homebrew/var/postgresql@17/postmaster.pid"
    if os.path.exists(pid_file):
        try:
            os.remove(pid_file)
        except OSError:
            pass
    run_cmd("brew services restart postgresql@17")
    time.sleep(3)
    return check_postgres()


def check_backend() -> bool:
    """检查 Spring Boot 后端"""
    if not port_open(BACKEND_PORT):
        return False
    return http_ok(f"http://127.0.0.1:{BACKEND_PORT}/actuator/health")


def recover_backend() -> bool:
    """重启 Spring Boot 后端"""
    # 杀掉残留进程
    run_cmd(f"lsof -tiTCP:{BACKEND_PORT} -sTCP:LISTEN | xargs kill -9 2>/dev/null")
    time.sleep(1)

    pg_pw = get_credential("jd-transport-postgres")
    auth_pw = get_credential("guobang-transport-auth")
    auth_secret = get_credential("guobang-transport-auth-secret")
    if not pg_pw:
        return False

    env_vars = (
        f"TRANSPORT_AUTH_PASSWORD='{auth_pw}' "
        f"TRANSPORT_AUTH_SECRET='{auth_secret}' "
        f"PG_PASSWORD='{pg_pw}' "
        f"PG_HOST=127.0.0.1 "
        f"PG_PORT=5432 "
        f"PG_DATABASE=transport "
        f"PG_USER=transport"
    )
    backend_dir = os.path.join(PROJECT_ROOT, "backend")
    cmd = f"cd {backend_dir} && {env_vars} nohup mvn spring-boot:run > /tmp/guobang-backend.log 2>&1 &"
    run_cmd(cmd, background=True)

    for _ in range(MAX_RECOVERY_WAIT):
        time.sleep(1)
        if check_backend():
            return True
    return False


def check_frontend() -> bool:
    """检查 Vite 前端"""
    return port_open(FRONTEND_PORT)


def recover_frontend() -> bool:
    """重启 Vite 前端"""
    run_cmd(f"lsof -tiTCP:{FRONTEND_PORT} -sTCP:LISTEN | xargs kill -9 2>/dev/null")
    time.sleep(1)

    frontend_dir = os.path.join(PROJECT_ROOT, "frontend")
    cmd = f"cd {frontend_dir} && nohup pnpm dev > /tmp/guobang-frontend.log 2>&1 &"
    run_cmd(cmd, background=True)

    for _ in range(MAX_RECOVERY_WAIT):
        time.sleep(1)
        if check_frontend():
            return True
    return False


def main():
    messages = []

    # 1. PostgreSQL
    if not check_postgres():
        messages.append("⚠️ PostgreSQL 不可用，尝试恢复...")
        if recover_postgres():
            messages.append("✅ PostgreSQL 已恢复")
        else:
            messages.append("❌ PostgreSQL 恢复失败！")
            print("\n".join(messages))
            return

    # 2. Backend
    if not check_backend():
        messages.append("⚠️ 后端(Spring Boot:8000)不可用，尝试恢复...")
        if recover_backend():
            messages.append("✅ 后端已恢复 (port 8000)")
        else:
            messages.append("❌ 后端恢复失败！检查 /tmp/guobang-backend.log")

    # 3. Frontend
    if not check_frontend():
        messages.append("⚠️ 前端(Vite:9527)不可用，尝试恢复...")
        if recover_frontend():
            messages.append("✅ 前端已恢复 (port 9527)")
        else:
            messages.append("❌ 前端恢复失败！检查 /tmp/guobang-frontend.log")

    if messages:
        print("\n".join(messages))
    # 无输出 = 一切正常


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
过磅项目本地服务健康检查 + 自动恢复脚本 (Spring Boot 3 + Vue 3)

检查:
  1. PostgreSQL (100.64.0.3:5433) — TCP 连接
  2. 后端 Spring Boot (port 8000) — /actuator/health
  3. 前端 Vite dev server (port 9527) — HTTP 200

自动恢复:
  - PG 挂了 → 告警（由服务器端恢复）
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
import shlex
from datetime import datetime

PROJECT_ROOT = os.path.expanduser("~/.hermes/scripts/guobang-spring-vue")
BACKEND_PORT = 8000
FRONTEND_PORT = 9527
PG_HOST = "100.64.0.3"
PG_PORT = 5433
MAX_RECOVERY_WAIT = 20  # seconds
CREDS_SCRIPT = os.path.expanduser("~/.hermes/scripts/creds/creds.sh")


def format_cron_message(title: str, body: str) -> str:
    ts = datetime.now().strftime("%m-%d %H:%M")
    return f"🔔 {title}｜{ts}\n\n{body.strip()}\n\n— 自动推送"


def port_open(port: int, host: str = "127.0.0.1") -> bool:
    """检查端口是否在监听"""
    try:
        with socket.create_connection((host, port), timeout=2):
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
    return port_open(PG_PORT, PG_HOST)


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
    env = {
        "TRANSPORT_AUTH_PASSWORD": auth_pw,
        "TRANSPORT_AUTH_SECRET": auth_secret,
        "PG_PASSWORD": pg_pw,
        "PG_HOST": PG_HOST,
        "PG_PORT": str(PG_PORT),
        "PG_DATABASE": "transport",
        "PG_USER": "transport",
    }
    env_vars = " ".join(f"{k}={shlex.quote(v)}" for k, v in env.items() if v)
    if env_vars:
        env_vars += " "
    cmd = (
        f"cd {PROJECT_ROOT} && "
        f"JAVA_HOME=/opt/homebrew/opt/openjdk@17 "
        f"PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH "
        f"{env_vars} SKIP_BUILD=1 ./start-all.sh restart > /tmp/guobang-backend.log 2>&1 &"
    )
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
        messages.append(f"❌ 服务器 PostgreSQL ({PG_HOST}:{PG_PORT}) 不可用")
        print(format_cron_message("过磅服务监控", "\n".join(messages)))
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
        print(format_cron_message("过磅服务监控", "\n".join(messages)))
    # 无输出 = 一切正常


if __name__ == "__main__":
    main()

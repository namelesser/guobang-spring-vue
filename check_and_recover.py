#!/usr/bin/env python3
"""京东云过磅服务健康检查与自动恢复；无输出表示健康。"""

import subprocess
import urllib.request
from datetime import datetime

SERVER = "root@117.72.179.214"
PUBLIC_URL = "https://transport.liumangtu.site/"
CONTAINERS = ("guobang-backend", "guobang-frontend")


def message(body: str) -> str:
    ts = datetime.now().strftime("%m-%d %H:%M")
    return f"🔔 过磅服务监控｜{ts}\n\n{body}\n\n— 自动推送"


def run(*args: str, timeout: int = 20) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def ssh(command: str) -> subprocess.CompletedProcess:
    return run("ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8", SERVER, command)


def public_ok() -> bool:
    try:
        with urllib.request.urlopen(PUBLIC_URL, timeout=8) as response:
            return 200 <= response.status < 400
    except Exception:
        return False


def container_ok(name: str) -> bool:
    result = ssh(f"test \"$(docker inspect -f '{{{{.State.Running}}}}' {name} 2>/dev/null)\" = true")
    return result.returncode == 0


def restart(name: str) -> bool:
    result = ssh(f"docker restart {name} >/dev/null && sleep 3 && test \"$(docker inspect -f '{{{{.State.Running}}}}' {name})\" = true")
    return result.returncode == 0


def main() -> None:
    probe = ssh("true")
    if probe.returncode != 0:
        print(message(f"❌ 京东云 SSH 不可用\n{probe.stderr.strip() or '连接失败'}"))
        return

    notes = []
    for container in CONTAINERS:
        if not container_ok(container):
            notes.append(f"⚠️ {container} 不可用，尝试重启...")
            notes.append(f"{'✅ 已恢复' if restart(container) else '❌ 恢复失败'}：{container}")

    if not public_ok():
        notes.append(f"❌ 站点不可用：{PUBLIC_URL}")

    if notes:
        print(message("\n".join(notes)))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""京东云过磅服务健康检查与自动恢复；无输出表示健康。"""

from typing import Optional
import subprocess
import time
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


def ssh(command: str, attempts: int = 3) -> subprocess.CompletedProcess:
    if attempts < 1:
        raise ValueError("attempts 必须大于零")
    for attempt in range(attempts):
        try:
            result = run("ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8", SERVER, command)
        except subprocess.TimeoutExpired as exc:
            stderr = exc.stderr or ""
            if isinstance(stderr, bytes):
                stderr = stderr.decode(errors="replace")
            result = subprocess.CompletedProcess(
                [], 255, "", f"SSH 超时（{exc.timeout}s）；{stderr}")
        if result.returncode != 255 or attempt == attempts - 1:
            return result
        time.sleep(2 * (attempt + 1))
    raise AssertionError("连接重试次数无效")


def public_ok() -> bool:
    try:
        with urllib.request.urlopen(PUBLIC_URL, timeout=8) as response:
            return 200 <= response.status < 400
    except Exception:
        return False


def container_ok(name: str, notes: Optional[list] = None) -> Optional[bool]:
    """仅成功的 inspect 返回 false 才表示停止；其他失败均为未知。"""
    result = ssh(f"docker inspect -f '{{{{.State.Running}}}}' {name}")
    state = ({"true": True, "false": False}.get(result.stdout.strip())
             if result.returncode == 0 else None)
    if state is None and notes is not None:
        detail = " ".join(result.stderr.split())[:300] or "未返回有效容器状态"
        notes.append(f"⚠️ {name} 探测失败：exitcode={result.returncode}；{detail}")
    return state


def restart(name: str, notes: Optional[list] = None) -> bool:
    # 写操作不能因连接丢失而重复执行；成功后单独读回验证。
    result = ssh(f"docker restart {name} >/dev/null && sleep 3", attempts=1)
    if result.returncode != 0:
        if notes is not None:
            detail = " ".join(result.stderr.split())[:300] or "重启结果未确认"
            notes.append(f"⚠️ {name} 重启失败或结果未知：exitcode={result.returncode}；{detail}")
        return False
    return container_ok(name, notes) is True


def main() -> None:
    notes = []
    for container in CONTAINERS:
        state = container_ok(container, notes)
        if state is None:
            notes.append(f"⚠️ {container} 状态未知（SSH/inspect 探测失败），未重启")
        elif state is False:
            notes.append(f"⚠️ {container} 已停止，尝试重启...")
            notes.append(f"{'✅ 容器已恢复运行' if restart(container, notes) else '❌ 恢复失败或未确认'}：{container}")

    if not public_ok():
        notes.append(f"❌ 站点不可用：{PUBLIC_URL}")

    if notes:
        print(message("\n".join(notes)))


if __name__ == "__main__":
    main()

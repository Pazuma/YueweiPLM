#!/usr/bin/env python3
"""Lightweight, stateful infrastructure checks for production hosts."""

import json
import os
import subprocess
import tempfile
from dataclasses import dataclass
from typing import Any, Dict, List, Optional


REMINDER_SECONDS = 21600


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


def severity_for_percent(
    value: float, warning_threshold: float, critical_threshold: float
) -> str:
    if value >= critical_threshold:
        return "CRITICAL"
    if value >= warning_threshold:
        return "WARNING"
    return "OK"


def restart_delta_status(delta: int, maximum_ok: int = 3) -> str:
    return "CRITICAL" if delta > maximum_ok else "OK"


def cpu_percent(previous: Any, current: Any) -> Optional[float]:
    previous_total, previous_idle = previous
    current_total, current_idle = current
    total_delta = current_total - previous_total
    idle_delta = current_idle - previous_idle
    if total_delta <= 0 or idle_delta < 0:
        return None
    value = 100.0 * (1.0 - (idle_delta / total_delta))
    return round(max(0.0, min(100.0, value)), 2)


def mount_status(
    expected_uuid: str,
    actual_uuid: str,
    fstype: str,
    is_mount: bool,
    expected_fstype: str = "ext4",
) -> str:
    if not is_mount:
        return "CRITICAL"
    if actual_uuid != expected_uuid or fstype != expected_fstype:
        return "CRITICAL"
    return "OK"


def container_status(state: str, health: Optional[str]) -> str:
    if state != "running":
        return "CRITICAL"
    if health == "unhealthy":
        return "CRITICAL"
    if health == "starting":
        return "WARNING"
    return "OK"


def parse_docker_inspect(raw: str) -> Dict[str, Dict[str, Any]]:
    try:
        parsed = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        return {}
    if not isinstance(parsed, list):
        return {}
    result: Dict[str, Dict[str, Any]] = {}
    for item in parsed:
        if not isinstance(item, dict):
            continue
        name = str(item.get("Name", "")).lstrip("/")
        if name:
            result[name] = item
    return result


def run_command(args: List[str], timeout: int = 5) -> CommandResult:
    try:
        completed = subprocess.run(
            args,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        return CommandResult(completed.returncode, completed.stdout, completed.stderr)
    except subprocess.TimeoutExpired:
        return CommandResult(124, "", "command timed out")
    except OSError as error:
        return CommandResult(127, "", str(error))


def transition_actions(
    previous_status: Optional[str],
    current_status: str,
    last_sent_at: float,
    now: float,
    reminder_seconds: int = REMINDER_SECONDS,
) -> List[str]:
    if previous_status is None:
        return [] if current_status == "OK" else ["ALERT"]
    if current_status == "OK":
        return [] if previous_status == "OK" else ["RECOVERY"]
    if previous_status != current_status:
        return ["ALERT"]
    if now - last_sent_at >= reminder_seconds:
        return ["REMINDER"]
    return []


def load_state(path: str) -> Dict[str, Any]:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return {}


def save_state(path: str, state: Dict[str, Any]) -> None:
    directory = os.path.dirname(path)
    os.makedirs(directory, mode=0o700, exist_ok=True)
    descriptor, temporary_path = tempfile.mkstemp(prefix=".state-", dir=directory)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(state, handle, ensure_ascii=False, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(temporary_path, 0o600)
        os.replace(temporary_path, path)
    finally:
        if os.path.exists(temporary_path):
            os.unlink(temporary_path)

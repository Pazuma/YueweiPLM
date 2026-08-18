#!/usr/bin/env python3
"""Lightweight, stateful infrastructure checks for production hosts."""

import json
import os
import tempfile
from typing import Any, Dict, List, Optional


REMINDER_SECONDS = 21600


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

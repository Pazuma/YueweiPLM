#!/usr/bin/env python3
"""Lightweight, stateful infrastructure checks for production hosts."""

import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional


REMINDER_SECONDS = 21600


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


def redact_text(value: str, extra_secrets: Optional[List[str]] = None) -> str:
    redacted = str(value)
    for secret in extra_secrets or []:
        if secret:
            redacted = redacted.replace(secret, "[REDACTED]")
    redacted = re.sub(
        r"(?i)([?&](?:access_token|token|appsecret|secret|password)=)[^&\s]+",
        r"\1[REDACTED]",
        redacted,
    )
    redacted = re.sub(
        r"(?i)((?:access_token|token|appsecret|secret|password)\s*[:=]\s*)[^\s]+",
        r"\1[REDACTED]",
        redacted,
    )
    return redacted


class DingTalkNotifier:
    def __init__(
        self, robot_code: str, app_key: str, app_secret: str, user_id: str
    ) -> None:
        values = (robot_code, app_key, app_secret, user_id)
        if any(not value for value in values):
            raise ValueError("DingTalk notifier configuration is incomplete")
        self.robot_code = robot_code
        self.app_key = app_key
        self.app_secret = app_secret
        self.user_id = user_id

    @classmethod
    def from_environment(cls) -> "DingTalkNotifier":
        return cls(
            os.environ.get("DINGTALK_ROBOT_CODE", ""),
            os.environ.get("DINGTALK_ROBOT_APPKEY", ""),
            os.environ.get("DINGTALK_ROBOT_APPSECRET", ""),
            os.environ.get("DINGTALK_ALERT_USER_ID", ""),
        )

    @staticmethod
    def _read_json(request: urllib.request.Request) -> Dict[str, Any]:
        with urllib.request.urlopen(request, timeout=5) as response:
            raw = response.read()
        parsed = json.loads(raw.decode("utf-8"))
        if not isinstance(parsed, dict):
            raise ValueError("DingTalk returned a non-object response")
        return parsed

    def _access_token(self) -> str:
        query = urllib.parse.urlencode(
            {"appkey": self.app_key, "appsecret": self.app_secret}
        )
        request = urllib.request.Request(
            "https://oapi.dingtalk.com/gettoken?" + query,
            headers={"Accept": "application/json"},
        )
        result = self._read_json(request)
        token = result.get("access_token")
        if result.get("errcode") not in (0, None) or not token:
            raise RuntimeError("DingTalk token request was rejected")
        return str(token)

    def send(self, title: str, text: str) -> bool:
        secrets = [self.app_secret, self.user_id]
        try:
            token = self._access_token()
            secrets.append(token)
            payload = {
                "robotCode": self.robot_code,
                "userIds": [self.user_id],
                "msgKey": "sampleMarkdown",
                "msgParam": json.dumps(
                    {"title": title, "text": text}, ensure_ascii=False
                ),
            }
            request = urllib.request.Request(
                "https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend",
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "x-acs-dingtalk-access-token": token,
                },
                method="POST",
            )
            result = self._read_json(request)
            invalid = result.get("invalidStaffIdList") or []
            limited = result.get("flowControlledStaffIdList") or []
            if invalid or limited:
                raise RuntimeError("DingTalk rejected or rate-limited the recipient")
            return True
        except Exception as error:  # Notification failure must not abort checks.
            print(
                "DingTalk delivery failed: " + redact_text(str(error), secrets),
                file=sys.stderr,
            )
            return False


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

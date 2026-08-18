#!/usr/bin/env python3
"""Lightweight, stateful infrastructure checks for production hosts."""

import argparse
import json
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple


REMINDER_SECONDS = 21600


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


@dataclass(frozen=True)
class CheckResult:
    key: str
    status: str
    summary: str


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


def severity_with_recovery(
    value: float,
    previous_status: Optional[str],
    warning_threshold: float,
    critical_threshold: float,
    recovery_threshold: float,
) -> str:
    current = severity_for_percent(value, warning_threshold, critical_threshold)
    if (
        previous_status in ("WARNING", "CRITICAL")
        and current == "OK"
        and value >= recovery_threshold
    ):
        return str(previous_status)
    return current


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


def read_proc_stat(path: str = "/proc/stat") -> Tuple[int, int]:
    with open(path, "r", encoding="ascii") as handle:
        fields = handle.readline().split()
    if not fields or fields[0] != "cpu" or len(fields) < 5:
        raise ValueError("invalid /proc/stat CPU line")
    counters = [int(value) for value in fields[1:]]
    total = sum(counters)
    idle = counters[3] + (counters[4] if len(counters) > 4 else 0)
    return total, idle


def filesystem_percent(path: str) -> Tuple[float, float]:
    stats = os.statvfs(path)
    block_total = stats.f_blocks
    block_available = stats.f_bavail
    inode_total = stats.f_files
    inode_available = stats.f_favail
    block_used = 0.0 if block_total == 0 else 100.0 * (1 - block_available / block_total)
    inode_used = 0.0 if inode_total == 0 else 100.0 * (1 - inode_available / inode_total)
    return round(block_used, 2), round(inode_used, 2)


def inspect_mount(path: str) -> Tuple[bool, str, str]:
    result = run_command(
        ["findmnt", "-n", "-o", "TARGET,UUID,FSTYPE", "--target", path]
    )
    if result.returncode != 0 or not result.stdout.strip():
        return False, "", ""
    fields = result.stdout.strip().split()
    if len(fields) < 3:
        return False, "", ""
    target, uuid, fstype = fields[0], fields[1], fields[2]
    return target == path, uuid, fstype


def http_status(url: str, expected_status: int, body_contains: str = "") -> bool:
    request = urllib.request.Request(
        url, headers={"User-Agent": "local-infra-alert/1.0"}
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            status = int(response.getcode())
            body = response.read(65536).decode("utf-8", errors="replace")
        return status == expected_status and (not body_contains or body_contains in body)
    except (urllib.error.URLError, TimeoutError, ValueError, OSError):
        return False


def container_ip(item: Dict[str, Any]) -> str:
    networks = item.get("NetworkSettings", {}).get("Networks", {})
    if not isinstance(networks, dict):
        return ""
    for network in networks.values():
        if isinstance(network, dict) and network.get("IPAddress"):
            return str(network["IPAddress"])
    return ""


def process_results(
    results: List[CheckResult],
    state: Dict[str, Any],
    notifier: Any,
    role: str,
    now: float,
) -> None:
    checks = state.setdefault("checks", {})
    for result in results:
        previous = checks.get(result.key)
        previous_status = previous.get("status") if isinstance(previous, dict) else None
        last_sent = float(previous.get("last_sent", 0)) if isinstance(previous, dict) else 0
        actions = transition_actions(previous_status, result.status, last_sent, now)
        delivered = True
        for action in actions:
            if action == "RECOVERY":
                title = "[恢复] {} 服务器告警已恢复".format(role)
            elif action == "REMINDER":
                title = "[持续告警] {} 服务器告警".format(role)
            else:
                title = "[告警] {} 服务器异常".format(role)
            text = "### {}\n- 主机：{}\n- 检查项：{}\n- 状态：{}\n- 详情：{}\n- 时间：{}".format(
                title,
                socket.gethostname(),
                result.key,
                result.status,
                result.summary,
                time.strftime("%Y-%m-%d %H:%M:%S %Z", time.localtime(now)),
            )
            delivered = notifier.send(title, text) and delivered
        if actions and not delivered:
            continue
        checks[result.key] = {
            "status": result.status,
            "summary": result.summary,
            "last_sent": now if actions else last_sent,
            "updated_at": now,
        }


def _previous_check_status(state: Dict[str, Any], key: str) -> Optional[str]:
    record = state.get("checks", {}).get(key, {})
    return record.get("status") if isinstance(record, dict) else None


def collect_results(config: Dict[str, Any], state: Dict[str, Any]) -> List[CheckResult]:
    results: List[CheckResult] = []
    metrics = state.setdefault("metrics", {})
    thresholds = config.get("thresholds", {})

    current_cpu = read_proc_stat()
    previous_cpu = metrics.get("cpu_counters")
    if isinstance(previous_cpu, list) and len(previous_cpu) == 2:
        value = cpu_percent(tuple(previous_cpu), current_cpu)
        if value is not None:
            previous_status = _previous_check_status(state, "cpu.average")
            status = severity_with_recovery(
                value,
                previous_status,
                float(thresholds.get("cpu_warning", 80)),
                float(thresholds.get("cpu_critical", 80)),
                float(thresholds.get("cpu_recovery", 70)),
            )
            results.append(CheckResult("cpu.average", status, "5分钟平均 {:.2f}%".format(value)))
    metrics["cpu_counters"] = list(current_cpu)

    for path, label in (("/", "root"), ("/data", "data")):
        try:
            capacity, inode = filesystem_percent(path)
            for metric, value in (("capacity", capacity), ("inode", inode)):
                key = "disk.{}.{}".format(label, metric)
                status = severity_with_recovery(
                    value,
                    _previous_check_status(state, key),
                    float(thresholds.get("disk_warning", 75)),
                    float(thresholds.get("disk_critical", 85)),
                    float(thresholds.get("disk_recovery", 70)),
                )
                results.append(CheckResult(key, status, "{} 使用率 {:.2f}%".format(path, value)))
        except OSError:
            results.append(CheckResult("disk.{}.access".format(label), "CRITICAL", "路径不可访问"))

    mount_config = config["data_mount"]
    is_mount, actual_uuid, fstype = inspect_mount("/data")
    status = mount_status(
        str(mount_config["uuid"]),
        actual_uuid,
        fstype,
        is_mount,
        str(mount_config.get("fstype", "ext4")),
    )
    summary = "挂载、UUID 和文件系统符合基线" if status == "OK" else "挂载、UUID 或文件系统不符合基线"
    results.append(CheckResult("mount.data", status, summary))

    docker_info = run_command(["docker", "info", "--format", "{{.ServerVersion}}"])
    if docker_info.returncode != 0:
        results.append(CheckResult("docker.daemon", "CRITICAL", "Docker daemon 不可用"))
        return results
    results.append(CheckResult("docker.daemon", "OK", "Docker daemon 可用"))

    names = list(dict.fromkeys(config.get("critical_containers", [])))
    inspect_result = run_command(["docker", "inspect"] + names)
    inspected = parse_docker_inspect(inspect_result.stdout) if inspect_result.returncode == 0 else {}
    previous_restarts = metrics.get("restart_counts", {})
    current_restarts: Dict[str, int] = {}
    for name in names:
        item = inspected.get(name)
        if not item:
            results.append(CheckResult("container.{}".format(name), "CRITICAL", "核心容器缺失或无法检查"))
            continue
        container_state = item.get("State", {})
        state_name = str(container_state.get("Status", "unknown"))
        health_data = container_state.get("Health")
        health = str(health_data.get("Status")) if isinstance(health_data, dict) else None
        results.append(
            CheckResult(
                "container.{}".format(name),
                container_status(state_name, health),
                "状态 {}{}".format(state_name, " / " + health if health else ""),
            )
        )
        restart_count = int(item.get("RestartCount", 0))
        current_restarts[name] = restart_count
        previous_count = int(previous_restarts.get(name, restart_count))
        delta = max(0, restart_count - previous_count)
        results.append(
            CheckResult(
                "restart.{}".format(name),
                restart_delta_status(delta, int(thresholds.get("restart_max", 3))),
                "最近窗口重启 {} 次".format(delta),
            )
        )
    metrics["restart_counts"] = current_restarts

    for check in config.get("service_checks", []):
        key = "service." + str(check["key"])
        check_type = check["type"]
        healthy = False
        if check_type == "http":
            healthy = http_status(
                str(check["url"]),
                int(check.get("status", 200)),
                str(check.get("body_contains", "")),
            )
        elif check_type == "docker_http":
            item = inspected.get(str(check["container"]))
            ip_address = container_ip(item) if item else ""
            if ip_address:
                url = "http://{}:{}{}".format(
                    ip_address, int(check["port"]), str(check.get("path", "/"))
                )
                healthy = http_status(
                    url,
                    int(check.get("status", 200)),
                    str(check.get("body_contains", "")),
                )
        elif check_type == "command":
            command = [str(value) for value in check["command"]]
            healthy = run_command(command, int(check.get("timeout", 5))).returncode == 0
        elif check_type == "container_health":
            item = inspected.get(str(check["container"]))
            container_state = item.get("State", {}) if item else {}
            health_data = container_state.get("Health")
            healthy = (
                container_state.get("Status") == "running"
                and isinstance(health_data, dict)
                and health_data.get("Status") == "healthy"
            )
        results.append(
            CheckResult(
                key,
                "OK" if healthy else "CRITICAL",
                "服务可用" if healthy else "服务健康检查失败",
            )
        )
    return results


def load_config(path: str) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as handle:
        config = json.load(handle)
    required = ("role", "data_mount", "critical_containers", "service_checks")
    if not isinstance(config, dict) or any(key not in config for key in required):
        raise ValueError("monitor configuration is incomplete")
    return config


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--state-dir", default="/var/lib/local-infra-alert")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--send-test", action="store_true")
    args = parser.parse_args(argv)

    config = load_config(args.config)
    if args.send_test:
        notifier = DingTalkNotifier.from_environment()
        title = "[测试] {} 服务器告警已启用".format(config["role"])
        text = "### {}\n- 主机：{}\n- 周期：每 5 分钟\n- 接收方式：机器人单聊".format(
            title, socket.gethostname()
        )
        return 0 if notifier.send(title, text) else 1

    state_path = os.path.join(args.state_dir, "state.json")
    state = load_state(state_path)
    results = collect_results(config, state)
    for result in results:
        print("{}={} {}".format(result.key, result.status, result.summary))
    if args.dry_run:
        return 0 if all(result.status == "OK" for result in results) else 2

    notifier = DingTalkNotifier.from_environment()
    process_results(results, state, notifier, str(config["role"]), time.time())
    save_state(state_path, state)
    return 0


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


if __name__ == "__main__":
    raise SystemExit(main())

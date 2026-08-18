import io
import json
from pathlib import Path
import unittest
from unittest import mock

from ops.monitoring.local_infra_alert import (
    CheckResult,
    DingTalkNotifier,
    container_status,
    cpu_percent,
    mount_status,
    parse_docker_inspect,
    process_results,
    redact_text,
    restart_delta_status,
    run_command,
    severity_for_percent,
    severity_with_recovery,
    transition_actions,
)


class StateMachineTests(unittest.TestCase):
    def test_percent_thresholds(self):
        self.assertEqual(severity_for_percent(74.9, 75, 85), "OK")
        self.assertEqual(severity_for_percent(75, 75, 85), "WARNING")
        self.assertEqual(severity_for_percent(85, 75, 85), "CRITICAL")

    def test_state_transitions_and_reminders(self):
        self.assertEqual(transition_actions(None, "OK", 0, 0), [])
        self.assertEqual(
            transition_actions("CRITICAL", "OK", 100, 200), ["RECOVERY"]
        )
        self.assertEqual(
            transition_actions("WARNING", "WARNING", 0, 21601), ["REMINDER"]
        )

    def test_restart_window_threshold(self):
        self.assertEqual(restart_delta_status(3), "OK")
        self.assertEqual(restart_delta_status(4), "CRITICAL")


class ProbeTests(unittest.TestCase):
    def test_cpu_percent_uses_counter_delta(self):
        self.assertEqual(cpu_percent((100, 40), (200, 60)), 80.0)
        self.assertIsNone(cpu_percent((100, 40), (100, 40)))

    def test_mount_baseline(self):
        self.assertEqual(mount_status("abc", "abc", "ext4", True), "OK")
        self.assertEqual(mount_status("abc", "def", "ext4", True), "CRITICAL")
        self.assertEqual(mount_status("abc", "abc", "xfs", True), "CRITICAL")
        self.assertEqual(mount_status("abc", "abc", "ext4", False), "CRITICAL")

    def test_container_health(self):
        self.assertEqual(container_status("running", "healthy"), "OK")
        self.assertEqual(container_status("running", None), "OK")
        self.assertEqual(container_status("running", "starting"), "WARNING")
        self.assertEqual(container_status("restarting", None), "CRITICAL")
        self.assertEqual(container_status("running", "unhealthy"), "CRITICAL")

    def test_malformed_docker_json_is_safe_failure(self):
        self.assertEqual(parse_docker_inspect("not-json"), {})

    @mock.patch("ops.monitoring.local_infra_alert.subprocess.run")
    def test_command_timeout_returns_bounded_failure(self, run):
        run.side_effect = __import__("subprocess").TimeoutExpired(["docker"], 5)
        result = run_command(["docker", "info"], timeout=5)
        self.assertEqual(result.returncode, 124)
        self.assertEqual(result.stdout, "")
        self.assertIn("timed out", result.stderr)


class DingTalkNotifierTests(unittest.TestCase):
    @staticmethod
    def response(payload):
        response = mock.MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps(payload).encode()
        return response

    @mock.patch("ops.monitoring.local_infra_alert.urllib.request.urlopen")
    def test_sends_one_direct_markdown_message(self, urlopen):
        urlopen.side_effect = [
            self.response({"errcode": 0, "access_token": "token-value"}),
            self.response({"processQueryKey": "query-key"}),
        ]
        notifier = DingTalkNotifier("robot-code", "app-key", "app-secret", "user-123")

        result = notifier.send("测试标题", "测试正文")

        self.assertTrue(result)
        token_request = urlopen.call_args_list[0].args[0]
        self.assertIn("gettoken", token_request.full_url)
        send_request = urlopen.call_args_list[1].args[0]
        payload = json.loads(send_request.data.decode())
        self.assertEqual(payload["robotCode"], "robot-code")
        self.assertEqual(payload["userIds"], ["user-123"])
        self.assertEqual(payload["msgKey"], "sampleMarkdown")
        self.assertEqual(
            json.loads(payload["msgParam"]),
            {"title": "测试标题", "text": "测试正文"},
        )
        self.assertNotIn("conversationId", payload)

    def test_missing_credentials_are_rejected(self):
        with self.assertRaises(ValueError):
            DingTalkNotifier("", "app-key", "app-secret", "user-123")

    def test_redacts_tokens_secrets_passwords_and_recipient(self):
        raw = (
            "https://example.test/send?access_token=topsecret "
            "password=hunter2 appsecret=secret-value user-123"
        )
        redacted = redact_text(raw, ["user-123"])
        for secret in ("topsecret", "hunter2", "secret-value", "user-123"):
            self.assertNotIn(secret, redacted)
        self.assertIn("[REDACTED]", redacted)

    @mock.patch("ops.monitoring.local_infra_alert.urllib.request.urlopen")
    def test_delivery_failure_is_retryable_and_redacted(self, urlopen):
        urlopen.side_effect = RuntimeError(
            "access_token=topsecret appsecret=secret-value user-123"
        )
        notifier = DingTalkNotifier("robot-code", "app-key", "secret-value", "user-123")
        error_stream = io.StringIO()

        with mock.patch("sys.stderr", error_stream):
            result = notifier.send("测试", "正文")

        self.assertFalse(result)
        output = error_stream.getvalue()
        self.assertNotIn("topsecret", output)
        self.assertNotIn("secret-value", output)
        self.assertNotIn("user-123", output)


class ResultProcessingTests(unittest.TestCase):
    class FakeNotifier:
        def __init__(self, success=True):
            self.success = success
            self.messages = []

        def send(self, title, text):
            self.messages.append((title, text))
            return self.success

    def test_hysteresis_holds_alert_until_recovery_threshold(self):
        self.assertEqual(severity_with_recovery(79, "CRITICAL", 80, 80, 70), "CRITICAL")
        self.assertEqual(severity_with_recovery(69, "CRITICAL", 80, 80, 70), "OK")

    def test_alert_is_deduplicated_and_recovery_is_sent(self):
        notifier = self.FakeNotifier()
        state = {"checks": {}}
        failing = [CheckResult("disk.root", "CRITICAL", "root 90%")]

        process_results(failing, state, notifier, "app", 100)
        process_results(failing, state, notifier, "app", 200)
        process_results(
            [CheckResult("disk.root", "OK", "root 50%")],
            state,
            notifier,
            "app",
            300,
        )

        self.assertEqual(len(notifier.messages), 2)
        self.assertIn("告警", notifier.messages[0][0])
        self.assertIn("恢复", notifier.messages[1][0])

    def test_failed_delivery_remains_pending(self):
        notifier = self.FakeNotifier(success=False)
        state = {"checks": {}}
        failing = [CheckResult("docker", "CRITICAL", "Docker unavailable")]

        process_results(failing, state, notifier, "db", 100)
        process_results(failing, state, notifier, "db", 200)

        self.assertEqual(len(notifier.messages), 2)
        self.assertNotIn("docker", state["checks"])


class ProductionConfigTests(unittest.TestCase):
    root = Path(__file__).resolve().parents[1]

    def test_host_configs_are_complete(self):
        for filename, role in (
            ("app-server.json", "application-server"),
            ("db-server.json", "database-server"),
        ):
            config = json.loads((self.root / filename).read_text())
            self.assertEqual(config["role"], role)
            self.assertEqual(config["interval_seconds"], 300)
            self.assertEqual(config["data_mount"]["fstype"], "ext4")
            self.assertGreater(len(config["data_mount"]["uuid"]), 10)
            self.assertTrue(config["critical_containers"])
            self.assertTrue(config["service_checks"])

    def test_systemd_units_bound_resources_and_use_five_minutes(self):
        service = (self.root / "local-infra-alert.service").read_text()
        timer = (self.root / "local-infra-alert.timer").read_text()
        for directive in (
            "Type=oneshot",
            "NoNewPrivileges=true",
            "PrivateTmp=true",
            "MemoryMax=128M",
            "CPUQuota=10%",
            "TasksMax=32",
            "IOSchedulingClass=idle",
            "TimeoutStartSec=120",
            "ReadWritePaths=/var/lib/local-infra-alert",
        ):
            self.assertIn(directive, service)
        self.assertIn("OnCalendar=*-*-* *:00/5:00", timer)
        self.assertIn("Persistent=true", timer)

    def test_script_entrypoint_runs_after_all_function_definitions(self):
        source = (self.root / "local_infra_alert.py").read_text()
        self.assertGreater(
            source.rfind('if __name__ == "__main__":'),
            source.rfind("def save_state("),
        )


if __name__ == "__main__":
    unittest.main()

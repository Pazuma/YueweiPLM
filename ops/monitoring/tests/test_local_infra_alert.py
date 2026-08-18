import unittest
from unittest import mock

from ops.monitoring.local_infra_alert import (
    container_status,
    cpu_percent,
    mount_status,
    parse_docker_inspect,
    restart_delta_status,
    run_command,
    severity_for_percent,
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


if __name__ == "__main__":
    unittest.main()

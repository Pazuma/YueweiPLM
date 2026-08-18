import unittest

from ops.monitoring.local_infra_alert import (
    restart_delta_status,
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


if __name__ == "__main__":
    unittest.main()

# Two-Server DingTalk Alerting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy five-minute, stateful infrastructure checks on both production servers and send deduplicated alert/recovery messages directly to the unique DingTalk user “施鸣坤”.

**Architecture:** A dependency-free Python monitor runs as a hardened systemd oneshot service on each host. It samples local CPU/disk/mount/Docker/service state, persists only non-secret state under `/var/lib/local-infra-alert`, and sends state-transition notifications through the existing DingTalk enterprise robot. A systemd timer triggers every five minutes without restarting business services.

**Tech Stack:** Python 3 standard library, Docker CLI, systemd service/timer, DingTalk enterprise robot OTo API, `unittest`.

---

## File map

- Create `ops/monitoring/local_infra_alert.py`: state machine, probes, DingTalk client, CLI and test injection.
- Create `ops/monitoring/tests/test_local_infra_alert.py`: unit tests for thresholds, restart windows, deduplication, recovery, redaction and API payload.
- Create `ops/monitoring/local-infra-alert.service`: hardened systemd oneshot unit.
- Create `ops/monitoring/local-infra-alert.timer`: five-minute timer.
- Create `ops/monitoring/app-server.json`: application-server roles, mount baseline, containers and HTTP checks.
- Create `ops/monitoring/db-server.json`: database-server roles, mount baseline, containers and readiness checks.
- Create `ops/monitoring/README.md`: installation, verification, logs and rollback.

### Task 1: Stateful alert engine

**Files:**
- Create: `ops/monitoring/tests/test_local_infra_alert.py`
- Create: `ops/monitoring/local_infra_alert.py`

- [ ] **Step 1: Write failing state-machine tests**

Add tests that import `severity_for_percent`, `transition_actions`, and `restart_delta_status` and assert:

```python
assert severity_for_percent(74.9, 75, 85) == "OK"
assert severity_for_percent(75, 75, 85) == "WARNING"
assert severity_for_percent(85, 75, 85) == "CRITICAL"
assert transition_actions(None, "OK", 0, 0) == []
assert transition_actions("CRITICAL", "OK", 100, 200) == ["RECOVERY"]
assert transition_actions("WARNING", "WARNING", 0, 21601) == ["REMINDER"]
assert restart_delta_status(3) == "OK"
assert restart_delta_status(4) == "CRITICAL"
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: FAIL because `ops.monitoring.local_infra_alert` does not exist.

- [ ] **Step 3: Implement the minimal state engine**

Implement the tested functions, JSON state load/save using atomic `os.replace`, state-transition notification decisions, six-hour reminders, and recovery hysteresis. Store alert state by stable check key and never store environment variables or credentials.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: all state-machine tests PASS.

- [ ] **Step 5: Commit**

```bash
git add ops/monitoring/local_infra_alert.py ops/monitoring/tests/test_local_infra_alert.py
git commit -m "feat: add stateful infrastructure alert engine"
```

### Task 2: Host and Docker probes

**Files:**
- Modify: `ops/monitoring/local_infra_alert.py`
- Modify: `ops/monitoring/tests/test_local_infra_alert.py`

- [ ] **Step 1: Write failing probe tests**

Use temporary files and injected command results to verify:

```python
assert cpu_percent((100, 40), (200, 60)) == 80.0
assert mount_status(expected_uuid="abc", actual_uuid="abc", fstype="ext4", is_mount=True) == "OK"
assert mount_status(expected_uuid="abc", actual_uuid="def", fstype="ext4", is_mount=True) == "CRITICAL"
assert container_status("running", "healthy") == "OK"
assert container_status("restarting", None) == "CRITICAL"
```

Also test command timeouts, malformed Docker JSON and safe failure when Docker is unavailable.

- [ ] **Step 2: Run tests and confirm RED**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: FAIL on missing probe functions.

- [ ] **Step 3: Implement probes**

Implement `/proc/stat` counter deltas for the exact interval between timer runs, `statvfs` capacity/inode checks, `findmnt` UUID/fstype verification, Docker state/RestartCount inspection, bounded HTTP checks, and container-exec readiness commands from JSON configuration. Every subprocess must have a timeout no longer than five seconds.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: all probe tests PASS.

- [ ] **Step 5: Commit**

```bash
git add ops/monitoring/local_infra_alert.py ops/monitoring/tests/test_local_infra_alert.py
git commit -m "feat: add host container and service probes"
```

### Task 3: DingTalk direct-message notifier

**Files:**
- Modify: `ops/monitoring/local_infra_alert.py`
- Modify: `ops/monitoring/tests/test_local_infra_alert.py`

- [ ] **Step 1: Write failing notifier tests**

Mock only `urllib.request.urlopen` and verify that the notifier:

- obtains an access token without logging credentials;
- posts `robotCode`, exactly one configured `userId`, `msgKey=sampleMarkdown`, and JSON-encoded `msgParam`;
- rejects missing credentials;
- redacts URL query strings, tokens, secrets, passwords and full recipient IDs from exceptions and journal messages;
- returns a retryable failure without interrupting check execution.

- [ ] **Step 2: Run tests and confirm RED**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: FAIL on missing `DingTalkNotifier`.

- [ ] **Step 3: Implement notifier and CLI test mode**

Read credentials only from process environment. Call the existing DingTalk token endpoint and `/v1.0/robot/oToMessages/batchSend`; treat non-2xx responses, invalid recipient lists and rate-limited recipient lists as failures. Add `--send-test`, `--dry-run`, `--config`, and `--state-dir` CLI options.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: all notifier tests PASS and captured output contains none of the injected fake secrets.

- [ ] **Step 5: Commit**

```bash
git add ops/monitoring/local_infra_alert.py ops/monitoring/tests/test_local_infra_alert.py
git commit -m "feat: send deduplicated DingTalk alerts"
```

### Task 4: Host configuration and systemd units

**Files:**
- Create: `ops/monitoring/app-server.json`
- Create: `ops/monitoring/db-server.json`
- Create: `ops/monitoring/local-infra-alert.service`
- Create: `ops/monitoring/local-infra-alert.timer`
- Create: `ops/monitoring/README.md`
- Modify: `ops/monitoring/tests/test_local_infra_alert.py`

- [ ] **Step 1: Write failing configuration tests**

Validate both JSON files and assert the timer contains `OnCalendar=*-*-* *:00/5:00`, `Persistent=true`, and the service contains `Type=oneshot`, `NoNewPrivileges=true`, `PrivateTmp=true`, `MemoryMax=128M`, `CPUQuota=10%`, `TasksMax=32`, `IOSchedulingClass=idle`, `TimeoutStartSec=120`, and `ReadWritePaths=/var/lib/local-infra-alert`.

- [ ] **Step 2: Run tests and confirm RED**

Run: `python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v`

Expected: FAIL because configuration and unit files do not exist.

- [ ] **Step 3: Add production configurations and units**

Record the observed ext4 UUID for each `/data` mount, explicit critical-container lists, PLM/FIFO/RAG checks, PostgreSQL/MySQL readiness commands, thresholds 75/85, CPU threshold 80 with recovery 70, restart delta greater than three, and reminder interval 21600 seconds. Configure `RandomizedDelaySec=20`, `AccuracySec=30s`, `MemoryMax=128M`, `CPUQuota=10%`, `TasksMax=32`, `IOSchedulingClass=idle`, and `TimeoutStartSec=120` so checks remain bounded and do not overlap.

- [ ] **Step 4: Run local and systemd validation**

Run:

```bash
python3 -m unittest ops.monitoring.tests.test_local_infra_alert -v
systemd-analyze verify ops/monitoring/local-infra-alert.service ops/monitoring/local-infra-alert.timer
```

Expected: tests PASS; unit verification reports no errors attributable to these units.

- [ ] **Step 5: Commit**

```bash
git add ops/monitoring
git commit -m "ops: configure five-minute production alerts"
```

### Task 5: Deploy and verify application server

**Files on application server:**
- Create: `/usr/local/lib/local-infra-alert/local_infra_alert.py`
- Create: `/etc/local-infra-alert/config.json`
- Create: `/etc/local-infra-alert/dingtalk.env`
- Create: `/etc/systemd/system/local-infra-alert.service`
- Create: `/etc/systemd/system/local-infra-alert.timer`

- [ ] **Step 1: Capture baseline evidence**

Record Docker restart counts, unhealthy/restarting containers, current mount UUIDs, systemd timer list and SHA256 of deployed source under `/root/local-infra-alert-evidence-<timestamp>`.

- [ ] **Step 2: Install files without enabling the timer**

Use `install` with root ownership. Extract only the existing robot app key, app secret and robot code into the 0600 environment file without printing values. Insert the unique recipient ID obtained by the validated read-only query.

- [ ] **Step 3: Run dry-run and direct-message test**

Run the monitor with `--dry-run`, then `--send-test`. Expected: all healthy checks are OK; DingTalk API returns success for one user and no group conversation ID is used.

- [ ] **Step 4: Enable the timer**

Run: `systemctl enable --now local-infra-alert.timer`

Expected: timer is active and next trigger is within five minutes.

### Task 6: Deploy and verify database server

**Files on database server:** same paths as Task 5.

- [ ] **Step 1: Capture baseline evidence**

Record Docker restart counts, unhealthy/restarting containers, mount UUIDs and PostgreSQL/MySQL/RAG health under a timestamped root-only evidence directory.

- [ ] **Step 2: Transfer root-only notification configuration**

Copy only the four required DingTalk values from the application server configuration to the database server over SSH/SCP, keep mode 0600, and verify fingerprints rather than printing values.

- [ ] **Step 3: Install, dry-run and enable**

Install the script/config/units, run `--dry-run`, then enable the timer. Do not send a second test notification unless the first recipient test failed.

- [ ] **Step 4: Verify database checks**

Expected: PostgreSQL readiness, MySQL container health, RAG backend health, Docker, `/data`, disk and inode checks are OK.

### Task 7: End-to-end verification and rollback record

- [ ] **Step 1: Wait for one real timer cycle**

After at least five minutes, verify both services have a fresh successful invocation and no overlapping process.

- [ ] **Step 2: Verify business stability**

Compare all captured RestartCount values with the baseline; check PLM, FIFO, RAG, PostgreSQL and MySQL health. Expected: no restart increase and no unhealthy/restarting containers.

- [ ] **Step 3: Verify secret hygiene**

Search the new journal entries and evidence directory for known credential prefixes and environment variable values using hashes/length checks without printing the secrets. Expected: zero matches.

- [ ] **Step 4: Record rollback**

Document that rollback is:

```bash
systemctl disable --now local-infra-alert.timer
rm /etc/systemd/system/local-infra-alert.{service,timer}
systemctl daemon-reload
```

The script, configuration and state may be retained for evidence; deleting them is optional and does not affect business services.

- [ ] **Step 5: Commit final verification notes**

```bash
git add ops/monitoring/README.md
git commit -m "docs: record alert deployment verification"
```

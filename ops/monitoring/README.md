# Local infrastructure alerts

This monitor runs once every five minutes, exits after each check, and sends state-transition messages directly to the configured DingTalk user. It never restarts services.

## Installed paths

- Script: `/usr/local/lib/local-infra-alert/local_infra_alert.py`
- Host configuration: `/etc/local-infra-alert/config.json`
- Root-only DingTalk environment: `/etc/local-infra-alert/dingtalk.env`
- State: `/var/lib/local-infra-alert/state.json`
- Units: `/etc/systemd/system/local-infra-alert.service` and `.timer`

The environment file must be owned by root with mode `0600`; its values must never be printed or committed.

## Verification

```bash
systemctl status local-infra-alert.timer --no-pager
systemctl list-timers local-infra-alert.timer --all
systemctl show local-infra-alert.service -p MemoryMax -p CPUQuotaPerSecUSec -p TasksMax
journalctl -u local-infra-alert.service --since '-15 minutes' --no-pager
```

Dry-run checks without sending or modifying state:

```bash
set -a
. /etc/local-infra-alert/dingtalk.env
set +a
/usr/bin/python3 /usr/local/lib/local-infra-alert/local_infra_alert.py \
  --config /etc/local-infra-alert/config.json \
  --state-dir /var/lib/local-infra-alert \
  --dry-run
```

## Rollback

```bash
systemctl disable --now local-infra-alert.timer
rm /etc/systemd/system/local-infra-alert.service
rm /etc/systemd/system/local-infra-alert.timer
systemctl daemon-reload
```

Removing the script, configuration and state is optional. Disabling the timer has no effect on business containers.

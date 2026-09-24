# dustui_go_ov11_automation_activity_v1

DustUI OV11 automation and Activity ownership overlay.

This non-gate overlay keeps Android buildability intentionally suspended and makes UI, WorkManager, Tasker, notifications, and recovery observe the same durable Go operation. Accepted, queued, and running are not terminal success; notification actions are nonce/expiry/single-use/replay guarded; cancellation is an operation transition; process death requires reconcile.

Evidence commands:

```bash
python3 scripts/validate_ov11_contract.py --audit-loop
cd go && go test ./...
```

Apply command:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov11_automation_activity_v1.zip --no-validate --commit "DustUI OV11 automation Activity ownership"
```

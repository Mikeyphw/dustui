# dustui_go_ov06_privileged_runtime_protocol_v1

OV06 implements the privileged Go runtime/root boundary under the Android-suspended roadmap.

It adds strict privileged admission, allowlisted operations/fields/scopes/capabilities, bounded queue/worker/frame contracts, destructive-class single-flight, durable status/reconcile records, and no-replay-after-root-restart semantics.

Android buildability remains intentionally suspended until OV13. Production destructive cleanup remains blocked until OV08.

Evidence commands:

```bash
python3 scripts/validate_ov06_contract.py --audit-loop
cd go && go test ./...
```

Apply command:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov06_privileged_runtime_protocol_v1.zip --no-validate --commit "DustUI OV06 privileged runtime protocol"
```

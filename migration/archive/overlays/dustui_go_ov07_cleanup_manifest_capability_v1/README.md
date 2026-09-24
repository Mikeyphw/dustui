# dustui_go_ov07_cleanup_manifest_capability_v1

DustUI OV07 non-gate overlay.

Adds the Go cleanup review authorization layer: actionable path evidence, immutable cleanup manifests, exact descendant binding, manifest hash/revision, root/source context binding, and durable single-use capabilities. Android buildability remains intentionally suspended until OV13. Production mutation authority remains blocked until OV08.

Evidence commands:

```bash
python3 scripts/validate_ov07_contract.py --audit-loop
cd go && go test ./...
```

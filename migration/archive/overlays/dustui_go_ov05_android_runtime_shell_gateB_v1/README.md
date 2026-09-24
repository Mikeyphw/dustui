# DustUI OV05 — Android Go runtime integration and shell foundation (Gate B)

This overlay implements Gate B under the Android-suspended roadmap. It promotes Go SQLite to the migration scan/read authority, models the Android-owned ARM64 Go runtime package/session contract, and adds the reconnect shell route scaffold. Android buildability remains intentionally suspended until OV13.

Evidence:

```bash
python3 scripts/validate_ov05_contract.py --audit-loop
cd go && go test ./...
```

Devtool target-local jobs/workflows are intentionally not added; the installed schema does not support them for this Android target.

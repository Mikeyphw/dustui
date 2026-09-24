# OV04 query/search/history contract

OV04 provides bounded Go read APIs before UI reconnect:

- deterministic page tokens bound to scan generation;
- explicit stale-generation errors;
- total/partial/unknown count kinds;
- bounded response size with `MaxPageLimit`;
- context cancellation;
- search and current-folder filtering as different operations;
- summaries using explicit size semantics from the scan;
- composition buckets suitable for treemap views;
- growth/history series with semantic compatibility checks;
- package-linked path lookup;
- activity page preparation.

The contract is intentionally independent of Android buildability. The hard evidence for OV04 is:

```bash
python3 scripts/validate_ov04_contract.py --audit-loop
cd go && go test ./...
```

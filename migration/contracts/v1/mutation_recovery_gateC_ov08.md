# DustUI OV08 mutation/recovery Gate C contract

OV08 makes Go the only destructive authority. Production deletion is no longer a
legacy Android/root-service concern and no runtime fallback is allowed. Android
buildability remains intentionally suspended until OV13.

Required destructive FSM states: reviewed, claimed, admitted, executing,
verifying, complete, failed, interrupted, recovery_required, and cleanup_debt.

Safety invariants:

- capability is single-use and must be claimed before admission;
- execution is exact-manifest only;
- pre-mutation identity revalidation is mandatory;
- mount-boundary enforcement is mandatory;
- protected descendant checks fail closed;
- retained-copy constraints fail closed;
- no unreviewed recursive expansion is allowed;
- post-mutation verification is mandatory;
- stale-after-cleanup is emitted after mutation;
- lost transport, interrupted deletion, process death, and ambiguous outcomes require status/reconcile;
- blind replay after restart is forbidden;
- global destructive execution is single-flight;
- Activity is the canonical cleanup run/recovery view.

Gate C local evidence is `python3 scripts/validate_ov08_contract.py --audit-loop`
and `go test ./...`. Canonical Devtool EXO evidence remains a gate dependency
when available, but unsupported target-local jobs/workflows must not be invented.

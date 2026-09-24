# DustUI OV08 audit loop

Gate C was audited against the Android-suspended roadmap and the OV07 capability
base. The overlay switches cleanup from non-mutating evidence/capability to
production Go mutation authority.

Coverage checked:

- Go is the only destructive authority;
- single-use capability claim occurs before mutation;
- pre-mutation identity checks reject path replacement;
- mount-boundary checks reject crossing;
- protected descendant and retained-copy failures fail closed;
- exact reviewed paths are executed without unreviewed expansion;
- post-mutation verification creates cleanup_debt on mismatch;
- interruption and lost response require status/reconcile;
- global destructive single-flight is enforced;
- legacy destructive executor is unreachable;
- Android buildability remains intentionally suspended.

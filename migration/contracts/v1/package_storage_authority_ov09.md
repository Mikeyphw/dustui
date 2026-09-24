# OV09 package/storage evidence authority

OV09 replaces package/path regex guessing with user-aware Android package evidence normalized by Go. The Android collector is thin and platform-only; Go owns snapshot normalization, evidence hashing, package/user keys, completeness states, UI state mapping, residue eligibility and action-time re-evaluation.

Required evidence covers package/user inventory, installed versus archived state, system app state, enabled state, source and split directories, CE and DE data paths, external/adoptable/secondary storage where observable, code/data/cache/native storage, and collection completeness or degradation reasons.

Truth rules:

- Partial package evidence is never rendered as zero.
- `supported`, `partial`, `unavailable`, and `blocked` are explicit states.
- Residue and cleanup eligibility must consume package/user evidence.
- Path regex alone is never authoritative.
- Package-sensitive deletion requires action-time re-evaluation against the same package key, path, and evidence hash.
- System, enabled, installed, blocked, unavailable, or partial evidence blocks deletion until better evidence or recheck resolves it.

Android buildability remains intentionally suspended until OV13.

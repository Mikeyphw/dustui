# Android Phase 24: Cleaning Intelligence Final Gate

Phase 24 seals DustUI's Android cleaning roadmap after the App Storage Inspector. It adds no new deletion shortcut. Instead, it makes the cleaning-specific qualification evidence a required release artifact.

## Required qualification areas

The gate blocks release unless all of these pass:

- full Android unit suite with no failures;
- Android lint with zero warnings and zero errors;
- exported Room schema and explicit non-destructive migration contract;
- protected-path, scan-root, outside-root, symlink, mount-boundary, and fingerprint regressions;
- hard-link-safe duplicate accounting;
- conservative multi-user residue ownership;
- reviewed imports and bounded cleanup-rule matching;
- historical scan isolation;
- deletion-report redaction and authoritative path verification;
- interrupted hashing and interrupted deletion recovery;
- compact, large-font, TalkBack, switch, and chart/list accessibility contracts;
- a 10,000-file rule and growth workload within the qualification budget;
- actionable current-file surfaces;
- zero active quarantine runtime references.

## Evidence

`scripts/cleaning-intelligence-gate.sh` writes:

- `android/build/cleaning-intelligence/qualification.json`
- `android/build/cleaning-intelligence/QUALIFICATION.md`

The JSON evidence is copied into the release-candidate bundle and is required by `scripts/final-rc-gate.sh`. Missing or failed evidence produces `DO_NOT_SHIP`.

## Release integration

- `scripts/release-gate.sh` runs the full build, unit tests, lint, cleaning audit, and RC packaging.
- `scripts/package-rc.sh` records `cleaningIntelligenceGatePassed` and packages the evidence.
- `scripts/final-rc-gate.sh` refuses shipment without a passing cleaning qualification.
- `FinalRcReadiness` also treats a missing or failed cleaning report as a blocker.

## Safety invariants

- No automatic deletion.
- No quarantine runtime behavior.
- Every destructive action remains bound to the active scan, reviewed paths, and fingerprints.
- Unknown verification never counts as confirmed deletion.
- Historical paths remain informational.
- Active application state remains protected.

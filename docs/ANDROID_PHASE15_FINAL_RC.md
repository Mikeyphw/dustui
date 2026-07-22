# DustUI Android Phase 15: Final RC Hardening

Phase 15 turns the release process into a repeatable evidence pipeline.

## Goals

- Keep the app code stable after the UI/UX polish and release-packaging phases.
- Add a deterministic final RC decision model.
- Require changelog, checklist, known-issues review, RC bundle checks, and ship-status evidence.
- Avoid executable-bit assumptions in scripts shipped through overlay ZIPs.

## Final RC gate

Run from the Android project directory:

```bash
bash scripts/final-rc-gate.sh
```

The script validates required docs, shell syntax, release-candidate manifest/checksums, known-issue blockers, install-smoke evidence, and device-certification evidence. It writes:

```text
android/build/final-rc/dustui-<version>/SHIP_STATUS.md
android/build/final-rc/dustui-<version>/final-rc-manifest.json
```

## Decisions

- `SHIP`: release gate passed, signed release exists, install smoke evidence exists, device certification archive exists, and no blocker issues are listed.
- `SHIP_WITH_WARNINGS`: no blockers, but some evidence is intentionally incomplete or unsigned local RC artifacts are present.
- `DO_NOT_SHIP`: tests, lint, release packaging, checklist, changelog, known-issues review, or known blocker issues failed.

## Non-goals

- No new user-facing feature surface.
- No hidden automatic uploads.
- No signing-key generation or distribution upload.

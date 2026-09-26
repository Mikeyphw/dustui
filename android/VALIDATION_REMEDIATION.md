# Android validation remediation — successor authority

> AUD-FIX successor-aware. Gate D is qualified. AUD-FIX-16 physically retires predecessor Room/NDJSON/app-local release authority; Gate E is next.

Use Devtool rather than direct Gradle/ADB helper scripts:

```bash
cd "$HOME/Code/dustui"
./devtoolw aud-fix-16
```

The AUD-FIX-16 workflow runs the read-only retirement audit twice, verifies the checked-in retirement evidence, runs the Go successor workflow, then Android debug unit, lint, assemble, and canonical qualification-state checks. It intentionally does **not** rerun or reseal Gate D.

Historical Room migration, device-certification, and release-helper instructions are retained only under `migration/archive/` for provenance.

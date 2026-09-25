# DustUI AUD-FIX-14 overlay archive — live rebase v2

This non-gate overlay is rebased onto the authoritative 2026-09-25 11:16 post-AUD-FIX-13 live tree. It recovers exact historical Gate A/B/C identity where possible, declares the complete Gate-D topology, preserves failed R1 truth, repairs the known Gradle 9.7.1 source-set configuration blocker, and preserves live `.gitignore` state. Gate D remains open. Apply with `--no-validate`, then run `./devtoolw aud-fix-14`.

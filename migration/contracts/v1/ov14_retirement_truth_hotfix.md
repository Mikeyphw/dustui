# OV14 Retirement Truth Hotfix

Purpose: prevent a declaration-only OV14/OV15 seal from outrunning production
source. The hotfix does not perform the Android→Go cutover. It changes the gate
so the repository cannot claim that cutover until source evidence proves it.

The non-sealing audit is `./devtoolw cutover-audit`. Gate D and the normal
`validate` workflow now start with `retirement_gate`, which fails while the
source-level blocker set is non-empty.

This is a non-gate corrective overlay and is intended to be applied with
`--no-validate`; its own static/audit checks are still provided for direct use.

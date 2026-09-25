# dustui_aud_fix_05_06_diagnostics_authority_gate_r1_hotfix_v3

Replacement AUD-FIX-05/AUD-FIX-06 Gate R1 overlay, rebased onto the fresh 2026-09-25 phone snapshot.

- Retains the AUD-FIX-05 durable Go diagnostics/problem/support authority cutover.
- Retains the AUD-FIX-06 Authority Convergence Gate R1 Devtool graph.
- Preserves the live retirement of `BoundedScanBrowseContractTest`.
- Repairs stale generated Go qualification state detected by `refresh_qualification_state.py --check`.
- Repairs the AUD-FIX-04 compatibility seam exposed by failed Gate R1 run `5e91a83aacb748bf929857d2f65b94d2`: a structurally valid legacy `kind=analysis` + `duplicate-analysis:<scan>:<run>` admission is canonicalized to typed `duplicate-analysis`, while generic analyses remain non-cancellable.
- Migrates persisted legacy duplicate-analysis admissions and their action gates on SQLite open without changing durable operation IDs.
- Canonical gate workflow remains `dustui_android#authority_gate_r1` / `validate`.
- Required validation remains enabled with rollback on failure. Gate D remains OPEN.

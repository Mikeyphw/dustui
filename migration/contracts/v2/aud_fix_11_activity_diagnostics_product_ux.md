# AUD-FIX-11 — Activity and Diagnostics product UX convergence

Activity is the canonical product surface for durable Go operation truth. List/run/evidence views distinguish active, completed, failed, interrupted, cancelling, and recovery-required states; cancel/retry/reconcile actions are rendered only from Go-owned gates. Full cleanup verification belongs to the owning Activity run/evidence correlation and is not a separate top-level product.

A global active-operation surface may expose the current durable run outside Activity, including Overview, but tapping it must resolve to the exact semantic `activity/run/{operationId}` identity.

System → Diagnostics is backed by the durable Go ProblemGraph and leads with root causes, evidence freshness/health, recovery state, user-facing guidance/remediation, and safe support export. Structured events, probes, protocol/runtime details, and the debug overlay are Advanced-only details. Diagnostics remediation and Activity evidence share the same durable operation identity and are navigable in both directions.

AUD-FIX-11 is non-gate: apply with `--no-validate`, then run `dustui_android#aud_fix_11` / `./devtoolw aud-fix-11`. Gate D remains open.

# AUD-FIX-09 — bound async presentation state and typed Go action gates

Every asynchronous content response is bound to one identity tuple: `routeKey + routeGeneration + entityKind + entityId + requestId + optional fingerprint`. Android admits a response only if the complete tuple still belongs to the active semantic route and to the latest request for that purpose. Route changes invalidate outstanding work rather than allowing a late response to populate a newer screen.

All production content routes now pass through the same presentation protocol: **Loading**, **Empty** (typed reason + recovery), **Blocked** (typed reason + remediation), **Error** (problem reference + retry/support), or **Content** (freshness + action gates). `Blocked` is intentionally distinct from `Empty`. The previous Files/Clean/scan-required one-off pre-scan screens are retired.

Risky and evidence-dependent action policy is owned by Go. `ActionGate` contains `allowed`, stable `reasonCode`, a user-facing `message`, and typed `remediationActions`. Android renders that decision and no longer recreates scan freshness/capability policy for scan cancellation, cleanup review/execution, or durable Activity cancel/retry/reconcile.

The adversarial suite covers stale directory queries, package switches, cleanup manifests, Activity selection, diagnostics evidence generations, request IDs, and fingerprints. AUD-FIX-09 is not a full validation gate: apply with `--no-validate`, then run `dustui_android#aud_fix_09` / `./devtoolw aud-fix-09`.

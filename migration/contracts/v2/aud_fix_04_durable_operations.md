# AUD-FIX-04 — Durable operation coordinator, Activity truth, and cancellation capability

## Authority

The durable operation coordinator is Go + SQLite. Android UI, foreground service,
WorkManager, Tasker and notifications are adapters/observers; none may invent a
terminal result from launch/admission state.

Protocol: `dustui.automation.activity.v2`.

## Lifecycle

`accepted -> running -> {success|failure|cancelled|interrupted|recovery_required}`

Cancellation is an explicit `cancelling` transition before `cancelled` when a
live scan is being stopped. Terminal truth is sticky. `accepted`, `running` and
`cancelling` are never success.

Every operation has a stable operation id derived from request identity, kind,
generation and evidence fingerprint — never from the adapter source. Multiple
adapters observing the same request therefore converge on one durable id.

## Activity truth

`activity_runs` is the common durable projection for scan/automation operations
and cleanup history. It carries active/history/recovery, terminal truth,
progress/result/verification, Go-owned action gates, evidence correlation and
source adapters. The coordinator accepts `analysis` as a first-class operation kind. The current
long-running exact-duplicate verification workflow is admitted as a durable analysis
operation, reports bucket progress into Activity, uses the durable operation id for
root execution/cancellation, and records terminal success/failure/cancellation.
Cleanup history is projected from the existing cleanup authority without inventing
unsafe generic cleanup actions.

AUD-FIX-04 exposes Activity list/detail and action adapters to Android UI state.
AUD-FIX-11 owns the final Activity route hierarchy and rendering.

## Adapter rules

- WorkManager waits for durable terminal/recovery truth. Launch is not success.
- Tasker uses `goAsync`/`PendingResult` only for a bounded admission/status exchange.
  A scan action returns the durable operation id plus explicit non-terminal state;
  `status` observes the same operation later. Tasker never holds a broadcast for
  the lifetime of a scan and never maps admission to success.
- Foreground service validates any supplied operation id is bound to the same
  scan request before using it.
- UI consumes Activity list/detail by operation id and invokes only Go-gated
  cancel/retry/reconcile operations. Scan cancel executes the foreground-service
  path; duplicate-analysis cancel executes the bound root verification path.
  Unsupported operation kinds remain explicitly blocked rather than pretending an
  action exists.

## Notification cancellation

A notification cancel PendingIntent carries a Go-issued capability with:

- cryptographic nonce;
- expiry;
- operation id/request binding;
- action binding;
- evidence/generation-derived operation binding;
- single-use claim semantics;
- fail-closed replay/binding-mismatch burn.

The capability claim only authorizes requesting the Go cancellation transition;
it does not itself assert cancellation succeeded.

## Recovery

Operation state survives SQLite close/reopen. Reconcile compares scan operations
with durable scan terminal truth after reconnect/process death. Active analysis
operations are also reconciled on application restart; because no blind analysis
replay is permitted, lost in-process execution becomes `recovery_required`.
Definitive terminal evidence wins; otherwise Go moves the run to
`recovery_required`. Blind replay is forbidden.

## Gate truth

This overlay does not qualify Gate D. It is a `--no-validate` remediation
step. Full authority convergence is validated at AUD-FIX-06.

# DustUI OV11 automation and Activity contract

OV11 makes automation and Activity ownership a Go authority. UI, WorkManager, Tasker, notification actions, and recovery are thin adapters that request or observe the same durable operation.

Required semantics:

- accepted != success;
- queued != success;
- running != success;
- terminal truth is read from the durable Go operation only;
- notification actions carry nonce, expiry, single-use, operation binding, and replay rejection;
- cancellation is an operation transition, not a side-channel;
- process death does not orphan a job and requires status/reconcile rather than blind replay;
- Activity exposes active/history filtering, run details, progress, result, verification, recovery-required, retry/cancel/reconcile gates, and operation/evidence correlation.

Automation scope remains non-destructive for this campaign: scan, status, cancel, and safe inspection only. Unattended cleanup/deletion automation is out of scope until a separate security and UX contract exists.

Android buildability remains intentionally suspended until OV13.

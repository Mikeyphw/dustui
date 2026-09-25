# AUD-FIX-05 — Go diagnostics/support authority

Canonical diagnostics, evidence health, root-cause/problem graph, retention and support-bundle selection/sealing are Go-owned. Android is a thin observation collector/presentation/packaging adapter. Diagnostic values are redacted before persistence; events/sessions/support bundles are bounded and expire deterministically. Support export is a durable `support-export` operation and never includes file contents or automatic upload.

Validation authority: Devtool target-local jobs/workflow `dustui_android#aud_fix_05` plus `dustui_go#aud_fix_05`; project scripts are leaf assertions only. Gate D remains open.

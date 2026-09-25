# AUD-FIX-10 — Scan, Overview, and Apps UX truth convergence

Scan launch is a mandatory four-stage protocol: **Choose scope → Preflight → Review → explicit Start**. Presets, custom roots, targeted rescans, and module scans may prepare a request, but no production UI path may call `startScan` before the reviewed request has passed preflight and the Go `scan.start` action gate.

Overview is useful before and after a scan. With scan evidence it renders scan identity, generation, integrity/freshness, active durable operation state, recent Activity, warnings/problems, and explicit **logical bytes** versus **allocated bytes** from Go authority. Evidence review routes to Diagnostics rather than back into Scan.

Apps distinguishes filesystem bytes measured by the scan from Android package/storage evidence completeness. Package evidence is explicitly `SUPPORTED`, `PARTIAL`, `UNAVAILABLE`, or `BLOCKED`; partial/unavailable platform evidence is unknown and must never be rendered as a numeric zero.

Go owns the typed remediation reasons for root unavailability, preflight failure, wrong scope, degraded integrity, stale/no-scan evidence, and unsupported evidence. AUD-FIX-10 is a non-gate stage: apply with `--no-validate`, then run `dustui_android#aud_fix_10` / `./devtoolw aud-fix-10`.

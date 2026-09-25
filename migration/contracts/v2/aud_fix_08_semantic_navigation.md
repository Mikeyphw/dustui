# AUD-FIX-08 — semantic route and deep-link cutover

DustUI now owns one stable semantic route identity across Android and Go. The contract contains 27 routes under six domains: Overview, Files, Clean, Apps, Activity, and System. Compact and wide layouts may change navigation chrome only; they may not substitute route meaning.

Entity routes carry durable identity in the route itself: file path, cleanup candidate/manifest, package name, or operation ID. Missing and stale entities are surfaced as typed route problems rather than silently falling back to an unrelated screen or relying on a transient sheet as the only identity.

Android accepts `dustui://` ACTION_VIEW links, persists route/entity/generation with saveable state, and restores those values across recreation. Go parses the same nested route family and preserves encoded path entities. The old 16-destination `DustDestination` / layout-remap shell is retired from production source.

System is decomposed into runtime, modules, automation, diagnostics, certification, and settings. Activity owns durable run and evidence identities. Scan remains a global workflow action, not a peer destination.

AUD-FIX-08 is a non-gate stage. Gate D remains open. Apply with `--no-validate`, then run `dustui_android#aud_fix_08` (or `./devtoolw aud-fix-08`) for the narrow contract, Go route tests, and Android unit compilation/tests.

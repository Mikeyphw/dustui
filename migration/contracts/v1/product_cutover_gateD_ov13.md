# OV13 product cutover / Gate D contract

OV13 is the Android reconnect gate. Android buildability is no longer suspended: compile, unit test, lint, package, install/smoke, route/instrumentation/accessibility evidence, and Go-runtime/APK binding return to the hard gate set.

The product default after Gate D is Go authority everywhere:

- Go SQLite/runtime is the source of truth for scans, query/search/history, cleanup, package/storage evidence, automation/activity, diagnostics/evidence, privacy retention, and support bundles.
- Go CLI and Go TUI are product surfaces backed by the same packages, not second implementations.
- The old Python/Textual host surface is a frozen parity oracle only; any retained `dust-tui` alias must dispatch to the Go implementation after Gate D.
- Android is a semantic shell over Go runtime contracts; no supported domain action may use Kotlin-local truth.
- The six semantic routes are Overview, Files, Clean, Apps, Activity, and System. The route count is not the product requirement; semantic task coverage and stable route identity are.
- Phone/tablet/rotation may change chrome/density but must not change route identity.

Gate D must not be closed by local Go tests alone. The validator checks local contracts; Devtool Android validation and device/instrumentation evidence are still required on the target machine.

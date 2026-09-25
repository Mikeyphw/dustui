# AUD-FIX-07 — Go CLI/TUI parity and production host cutover

The supported/default DustUI **host** authority is now Go. `dustui-go`, `dust-tui`, and `dust-space` converge on `go/internal/host`; the interactive TUI delegates data-bearing actions to that same host executor rather than owning a second domain implementation.

The Go host exposes persisted scan/query/search, durable activity, cleanup review/execution, diagnostics, support export, readiness/schema/product inspection, and an interactive TUI. CLI responses are JSON by default. Cleanup remains bound to the existing Go review/execution safety contracts.

The predecessor Python/Textual implementation is retained only at `migration/oracle/python_textual/` as a frozen non-production, non-default, non-packaged parity oracle. Root install and development launch paths do not import or package it. Physical oracle deletion remains an AUD-FIX-16 responsibility after Gate D evidence.

Host-default truth and Android-default truth are intentionally separate. Go CLI/TUI are production-default host surfaces at AUD-FIX-07; the Android surface remains `qualification.GateDQualified`. Canonical qualification must therefore continue to report Gate D open until the required evidence registry passes.

Validation authority is the Devtool-native `dustui_android#aud_fix_07` federation workflow plus `dustui_go#aud_fix_07`. AUD-FIX-07 is a non-gate overlay and is applied with `--no-validate`; the narrow workflow/checkers do not substitute for later full Gate-D qualification.

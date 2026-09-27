# DustUI

DustUI is a Go-authoritative storage analysis and cleanup engine with two supported host surfaces—machine-readable CLI and interactive terminal UI—and an Android client that consumes the same Go domain/runtime contracts.

As of **AUD-FIX-17**, Go is the supported/default host and persistence authority, Gate D remains a preserved qualified ancestor, AUD-FIX-16 physical retirement is complete, and Gate E is qualified by executed canonical release/device/adversarial evidence. Historical predecessor material is retained only under `migration/archive/` and is non-authoritative.

## Supported host commands

Build/install all compatibility commands:

```bash
./scripts/install.sh
```

This installs `dustui-go`, `dust-tui`, and `dust-space` into `${DUSTUI_INSTALL_DIR:-$HOME/.local/bin}`. Both historical aliases are thin Go-host wrappers; they no longer load Python/Textual.

Run the interactive Go TUI:

```bash
dust-tui /path/to/scan
# or
dustui-go tui --root /path/to/scan
```

Run directly from the source tree:

```bash
./scripts/run-dev.sh
```

With no command, `dustui-go` launches the Go TUI.

## Documented scan/query workflow

The CLI emits JSON by default. Persist a scan into the Go-owned SQLite authority, then query or search that same durable scan:

```bash
DB="$HOME/.local/state/dustui/dustui.db"

dustui-go scan --db "$DB" /path/to/scan
dustui-go query --db "$DB" --query cache --limit 100
dustui-go search --db "$DB" --query '*.log' --limit 100
```

Useful bounded scan controls include `--max-entries`, `--max-depth`, `--files-only`, and `--dirs-only`. Query/search page limits are bounded by the Go state authority rather than by a second host-side model.

## Activity, cleanup, and diagnostics

```bash
dustui-go activity --db "$DB" --limit 100
dustui-go diagnostics --db "$DB" --limit 100
dustui-go support-export --db "$DB" --privacy minimal

dustui-go cleanup-review --input review-request.json
dustui-go cleanup-execute --input sealed-execution-request.json
```

Cleanup review/execution dispatch directly through the Go privileged cleanup contracts. Physical deletion safety and Android capability admission remain enforced by those contracts; AUD-FIX-07 does not bypass the later destructive-safety gate.

Inspect current machine-readable contracts:

```bash
dustui-go inspect readiness
dustui-go inspect schema
dustui-go inspect product
dustui-go inspect cli
dustui-go inspect tui
```

## Go TUI

The TUI owns navigation/presentation only. Data-bearing commands are forwarded to the same Go host executor used by the CLI, preventing a second domain implementation from forming in the terminal UI.

Inside the TUI:

```text
Routes: overview files clean apps activity system
Host actions: scan query search activity cleanup-review cleanup-execute diagnostics support-export inspect
```

Use `route <name>` to switch semantic route, `run <host-command ...>` for explicit host dispatch, and `quit` to exit.

## Product identity and qualification

Canonical qualification lives in:

- `migration/qualification/v1/current_state.json`
- `migration/qualification/v1/gate_d_evidence.json`
- generated Go truth: `go/internal/qualification/state_generated.go`

AUD-FIX-07 deliberately separates two facts:

1. Go CLI/TUI are reachable and are the production/default **host** authority.
2. Android product-default reconnect remains `qualification.GateDQualified` and therefore is **not** declared qualified until the full Gate-D evidence set passes.

The retired Python/Textual source is archived only for historical provenance and is not a live parity, production, or qualification surface.

## Validation

AUD-FIX-07 is a non-gate overlay and must be applied with `--no-validate`. Its narrow Devtool-native workflow is `aud_fix_07`; full validation remains available separately and once again includes the Android Gradle contract, unit, lint, and assemble jobs.

Static contract check:

```bash
python3 scripts/validate_audfix07_contract.py --audit-loop
python3 scripts/refresh_qualification_state.py --check
```

Go-focused checks:

```bash
cd go
go test ./internal/host ./internal/product ./internal/readiness -run AUD_FIX07 -count=1
go test ./internal/tui -run AUD_FIX07 -count=1
```

## Android

The Android app remains under `android/` and continues to use the Go process/SQLite authority introduced by the earlier remediation stages. Android UI route/deep-link convergence is intentionally **not** folded into AUD-FIX-07; it is the next roadmap boundary (AUD-FIX-08). Gate-D qualification remains bound to the AUD-FIX-15 evidence tree; post-retirement source changes do not reseal or rewrite that milestone.

## Migration oracle

`migration/archive/oracles/aud_fix_16_python_textual/` preserves the predecessor Textual source as historical, non-authoritative provenance only. The live migration oracle was removed by AUD-FIX-16.

## License

MIT. See `LICENSE`.

### Semantic navigation (AUD-FIX-08)
Android and Go now share 27 stable semantic routes across Overview, Files, Clean, Apps, Activity, and System. `dustui://` deep links, saved route/entity restoration, and typed missing/stale identities use those same route keys. Compact and wide layouts change chrome only; they no longer substitute destinations.

### Bound async presentation and action gates (AUD-FIX-09)
Every content request is now tied to semantic route + generation + entity + request ID + optional query/draft fingerprint. Late work cannot populate a newer route or entity. Android renders one shared Loading / Empty / Blocked / Error / Content protocol and consumes structured Go action gates (`allowed`, `reasonCode`, `message`, `remediationActions`) for risky/evidence-dependent actions instead of rebuilding those decisions from local booleans.

### Scan, Overview, and Apps truth (AUD-FIX-10)
AUD-FIX-10 removes direct scan-start shortcuts: every scan request now passes through scope selection, preflight, review, and an explicit Start action. Overview separates logical bytes from allocated filesystem bytes and surfaces scan identity/integrity plus durable operation/activity state. Apps keeps measured scan bytes distinct from typed package-evidence completeness (`SUPPORTED`, `PARTIAL`, `UNAVAILABLE`, `BLOCKED`), so missing evidence is never presented as zero. Gate D is qualified under AUD-FIX-15 and preserved as ancestor evidence; Gate E is qualified under AUD-FIX-17.



### Activity and Diagnostics convergence (AUD-FIX-11)
Activity now renders durable Go operation truth for active/history/recovery states and owns cleanup verification as correlated evidence. System → Diagnostics is backed by the Go ProblemGraph and leads with root causes, evidence health, remediation, and support export; raw events/probes stay under Advanced diagnostics. Both surfaces navigate by the same durable operation ID. Gate D is qualified under AUD-FIX-15 and preserved as ancestor evidence; Gate E is qualified under AUD-FIX-17.

### Adaptive tablet and route continuity (AUD-FIX-12)
AUD-FIX-12 derives compact/medium/expanded layout from the active semantic route without changing route identity. Files, Clean, Apps, Activity, and System gain route-driven supporting/master panes on larger windows; compact and expanded layouts share the same deep links, entity IDs, generations, action gates, and durable Go authorities. Accessibility traversal is explicit and charts/treemap use exact shared textual equivalents. Gate D is qualified under AUD-FIX-15 and preserved as ancestor evidence; Gate E is qualified under AUD-FIX-17.

## AUD-FIX-13 acceptance authority

Current pre-Gate-D acceptance metadata is generated at `migration/acceptance/v1/current_state.json`. It inventories live/static validators, maps archived source-token guards to exact successor evidence, and maps the complete UI/UX requirement/task/rule matrix. `candidate.acceptance_converged=true` means the candidate acceptance system is internally coherent; it **does not** qualify Gate D. Gate-D qualification remains exclusively evidence-derived from `migration/qualification/v1/current_state.json`. App-local adb/APK/release helpers are quarantined compatibility artifacts and are not acceptance or Gate-D orchestration authority.

### AUD-FIX-14 Gate-D preflight

AUD-FIX-14 adds exact-tree Gate A/B/C history discovery and the complete Gate-D Devtool topology. It does **not** close Gate D. Historical proof is accepted only from the original Git tree replayed through its canonical Devtool workflow; missing history is reported as `UNRECOVERABLE_HISTORICAL_EVIDENCE`. The latest AUD-FIX-05/06 R1 validation remains failed even though its code was later force-applied.

## AUD-FIX-16 physical retirement

AUD-FIX-16 removes predecessor production authority after the qualified Gate-D milestone. Android now streams root-worker scan events directly over a Binder pipe; Go-owned SQLite remains the persistence authority. Room/KSP integration, the migration-only Room importer, persisted NDJSON file tailing, generic Android-local release/device helper scripts, and Gate-D-superseded source-token tests are absent from live source sets.

Canonical post-retirement evidence is `migration/qualification/v1/ov14_retirement.json`. It records the new source projection while preserving the Gate-D ancestor hash and explicitly states `gate_d_resealed=false`. Run `./devtoolw aud-fix-16` for the targeted successor test/lint/build sweep.

## AUD-FIX-17 Gate E final qualification

Gate E is qualified only from the canonical signed Devtool release plus the full AUD-FIX-17 regression/adversarial graph. `migration/qualification/v1/gate_e_evidence.json` and `gate_e_bundle.json` are derived qualification outputs bound to the qualified source projection, release report/APK, Go runtime, signing identity, device evidence, and final convergence audit.

The temporary `gate_e_fast` / `aud-fix-17-fast` resume path is retired after qualification. Gate-E v2 treats `source_projection_sha256` as qualification identity and Git commit/tree IDs as provenance. Use `./devtoolw gate-e-plan` after a commit to inspect reuse. If the qualified source projection is unchanged, qualification remains valid without rerunning build/device work. If only qualification-policy inputs changed while all expensive/environment-bearing node fingerprints are identical, `./devtoolw gate-e-reconcile` reruns the cheap policy leaves and recomposes the seal. Material execution-input changes remain fail-closed and require canonical execution for the affected qualification path.

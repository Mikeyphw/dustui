# DustUI

DustUI is a Go-authoritative storage analysis and cleanup engine with two supported host surfaces—machine-readable CLI and interactive terminal UI—and an Android client that consumes the same Go domain/runtime contracts.

As of **AUD-FIX-07**, the supported/default host authority is Go. The former Python/Textual host is retained only as a frozen, non-production migration oracle under `migration/oracle/python_textual/` until its post-Gate-D physical-retirement stage. Android reconnect remains independently governed by canonical **Gate D**, which is still open.

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

The Python/Textual oracle is observed separately and is not a production/default surface.

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

The Android app remains under `android/` and continues to use the Go process/SQLite authority introduced by the earlier remediation stages. Android UI route/deep-link convergence is intentionally **not** folded into AUD-FIX-07; it is the next roadmap boundary (AUD-FIX-08). Until Gate D is fully evidenced, repository metadata must continue to report Android as unqualified rather than inferring qualification from source presence.

## Migration oracle

`migration/oracle/python_textual/` preserves the predecessor Textual implementation for behavior comparison only. It is non-default, non-packaged, excluded from root install/development launch paths, and retained until AUD-FIX-16 performs the original post-Gate-D physical retirement.

## License

MIT. See `LICENSE`.

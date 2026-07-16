# dust-tui

A modern Textual interface for [dust](https://github.com/bootandy/dust), the fast and intuitive disk-usage analyzer.

## Highlights

- Runs `dust -j` in a non-blocking Textual worker.
- Falls back to a built-in filesystem scanner when `dust` is unavailable.
- Sortable results with size, share, type, modification time, and full path.
- Live filtering, top-usage chart, scan summary, and drill-down navigation.
- Configurable depth, result limit, file/directory mode, minimum size, hidden files, exclusions, apparent size, and filesystem boundary.
- GUI settings for the supported `dust` flags, including symlink, filter, file-list, time, output, thread, and display options.
- Safe “move to trash” flow with confirmation and protected-root checks.
- Open, copy, JSON export, and CSV export actions.
- Responsive compact layout for smaller terminals and Termux.
- Persistent settings under the platform configuration directory.

## Install

Python 3.10 or newer is required.

```bash
cargo install du-dust
python3 -m pip install .
dust-tui
```

For an isolated install:

```bash
pipx install .
dust-tui ~/Downloads
```

The `dust` binary is recommended but not mandatory. In **Automatic** mode, the app prefers `dust` and falls back to its native Python scanner.

### Ubuntu / Debian

```bash
sudo apt install python3-pip pipx cargo
cargo install du-dust
pipx install .
```

### Termux

```bash
pkg install python rust
cargo install du-dust
python -m pip install .
dust-tui /sdcard
```

Storage access in Termux may require:

```bash
termux-setup-storage
```

## Usage

```text
dust-tui [PATH] [--no-scan]
```

Keyboard shortcuts:

| Key | Action |
|---|---|
| `r` | Scan or rescan |
| `x` | Cancel scan |
| `b` | Browse directories |
| `/` | Focus result filter |
| `Enter` | Drill into selected directory |
| `Backspace` | Scan parent directory |
| `i` | Inspect selected item |
| `o` | Open selected path |
| `c` | Copy selected path |
| `t` | Move selected item to trash |
| `e` | Export JSON and CSV |
| `s` | Open scan settings |
| `?` | Show help |
| `q` | Quit |

Click a column heading to sort it. Select a row to update the details panel.

## Backend behavior

The preferred backend invokes dust with JSON, full-path, and no-progress output so the TUI can parse results reliably. The exact command is shown in the status line after a successful scan. The parser accepts nested and flat JSON shapes to tolerate dust output evolution.

The settings dialog exposes the `dust` flags that can be passed while keeping JSON mode enabled. Flags such as help/version are intentionally not settings because they would exit instead of scanning.

The native fallback:

- avoids following symbolic links by default;
- deduplicates hard-linked files by device/inode;
- supports apparent or allocated size;
- can remain on the root filesystem;
- records permission errors as non-fatal warnings.

The configured maximum depth is also the traversal limit for the native backend. Use **Unlimited** when complete recursive totals are required.

## Development

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -e '.[dev]'
pytest
ruff check .
```

Run directly from the source tree:

```bash
./scripts/run-dev.sh --no-scan
```

UI, UX, typography/topography, compact terminal, and Termux display changes must follow [docs/UI_UX_CONTRACT.md](docs/UI_UX_CONTRACT.md).

## Safety

The app never permanently deletes files. The Trash action uses the operating system trash facility through `send2trash`. It blocks the scan root, the user home directory, filesystem roots, and paths outside the active scan root.

## License

MIT. `dust` is a separate Apache-2.0-licensed project and is not bundled.

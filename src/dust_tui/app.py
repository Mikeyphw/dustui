from __future__ import annotations

import asyncio
import csv
import json
import shlex
import threading
from contextlib import suppress
from datetime import datetime
from pathlib import Path
from typing import Any

from rich.markup import escape
from rich.text import Text
from send2trash import send2trash
from textual import on
from textual.app import App, ComposeResult
from textual.containers import Horizontal, Vertical
from textual.css.query import NoMatches
from textual.events import Resize
from textual.widgets import Button, DataTable, Footer, Header, Input, Label, Static
from textual.worker import Worker, WorkerState

from .backends import ScanCancelled, scan
from .config import AppConfig, load_config, save_config
from .models import DiskEntry, ScanOptions, ScanResult
from .screens import ConfirmTrashScreen, DirectoryPicker, EntryDetailsScreen, HelpScreen, SettingsScreen
from .utils import (
    copy_to_clipboard,
    format_size,
    format_timestamp,
    make_usage_bar,
    open_path,
    parse_size,
    safe_resolve,
    user_data_dir,
)


class DustTuiApp(App[None]):
    TITLE = "dust space analyzer"
    SUB_TITLE = "Find what is consuming your storage"
    CSS_PATH = "styles.tcss"
    ENABLE_COMMAND_PALETTE = True

    BINDINGS = [
        ("q", "quit", "Quit"),
        ("r", "start_scan", "Scan"),
        ("x", "cancel_scan", "Cancel"),
        ("b", "browse", "Browse"),
        ("s", "settings", "Settings"),
        ("slash", "focus_search", "Search"),
        ("enter", "drill_down", "Drill down"),
        ("backspace", "scan_parent", "Parent"),
        ("i", "inspect_selected", "Inspect"),
        ("o", "open_selected", "Open"),
        ("c", "copy_selected", "Copy"),
        ("t", "trash_selected", "Trash"),
        ("e", "export", "Export"),
        ("question_mark", "help", "Help"),
    ]

    def __init__(self, initial_path: Path | None = None, auto_scan: bool = True) -> None:
        super().__init__()
        self.config = load_config()
        if initial_path is not None:
            self.config.last_path = str(initial_path)
        self.auto_scan = auto_scan
        self.result: ScanResult | None = None
        self.visible_entries: list[DiskEntry] = []
        self.row_entries: dict[str, DiskEntry] = {}
        self.selected_entry: DiskEntry | None = None
        self.cancel_event = threading.Event()
        self.scan_worker: Worker[Any] | None = None
        self.dust_process: asyncio.subprocess.Process | None = None
        self.sort_key = "size"
        self.sort_reverse = True

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        with Vertical(id="app-shell"):
            with Horizontal(id="scan-toolbar"):
                yield Input(value=self.config.last_path, placeholder="Directory to scan", id="path-input")
                yield Button("Browse", id="browse-button")
                yield Button("Scan", id="scan-button", variant="primary")
                yield Button("Cancel", id="cancel-button", variant="warning", disabled=True)
                yield Button("Settings", id="settings-button")
            with Horizontal(id="summary-row"):
                yield Static("[dim]Total[/dim]\n[b]—[/b]", id="summary-total", classes="summary-card")
                yield Static("[dim]Results[/dim]\n[b]—[/b]", id="summary-results", classes="summary-card")
                yield Static("[dim]Largest[/dim]\n[b]—[/b]", id="summary-largest", classes="summary-card")
                yield Static("[dim]Backend[/dim]\n[b]—[/b]", id="summary-backend", classes="summary-card")
            yield Static("Choose a directory and press Scan.", id="usage-chart")
            with Horizontal(id="content-row"):
                with Vertical(id="results-pane"):
                    with Horizontal(id="filter-row"):
                        yield Input(placeholder="Filter current results…", id="filter-input")
                        yield Button("Details", id="details-button", disabled=True)
                        yield Button("Export", id="export-button")
                    yield DataTable(id="results-table", cursor_type="row", zebra_stripes=True)
                with Vertical(id="details-pane"):
                    yield Label("Selection", id="details-title")
                    yield Static("Select a result to inspect it.", id="details-body")
                    with Horizontal(id="details-actions"):
                        yield Button("Drill down", id="drill-button", disabled=True)
                        yield Button("Open", id="open-button", disabled=True)
                        yield Button("Copy", id="copy-button", disabled=True)
                        yield Button("Trash", id="trash-button", variant="error", disabled=True)
            yield Static("Ready", id="status-line")
        yield Footer()

    def on_mount(self) -> None:
        table = self.query_one("#results-table", DataTable)
        table.add_column("Size", key="size", width=12)
        table.add_column("Share", key="share", width=22)
        table.add_column("Kind", key="kind", width=10)
        table.add_column("Modified", key="modified", width=17)
        table.add_column("Path", key="path")
        if self.auto_scan:
            self.call_after_refresh(self.action_start_scan)
        self._apply_responsive_layout(self.size.width, self.size.height)

    def on_resize(self, event: Resize) -> None:
        self._apply_responsive_layout(event.size.width, event.size.height)

    def _apply_responsive_layout(self, width: int, height: int) -> None:
        details = self.query_one("#details-pane", Vertical)
        chart = self.query_one("#usage-chart", Static)
        details.display = width >= 95
        chart.display = height >= 34

    @on(Button.Pressed)
    def on_button_pressed(self, event: Button.Pressed) -> None:
        actions = {
            "browse-button": self.action_browse,
            "scan-button": self.action_start_scan,
            "cancel-button": self.action_cancel_scan,
            "settings-button": self.action_settings,
            "export-button": self.action_export,
            "details-button": self.action_inspect_selected,
            "drill-button": self.action_drill_down,
            "open-button": self.action_open_selected,
            "copy-button": self.action_copy_selected,
            "trash-button": self.action_trash_selected,
        }
        action = actions.get(event.button.id or "")
        if action:
            action()

    @on(Input.Submitted, "#path-input")
    def on_path_submitted(self) -> None:
        self.action_start_scan()

    @on(Input.Changed, "#filter-input")
    def on_filter_changed(self, event: Input.Changed) -> None:
        self._populate_table(event.value)

    @on(DataTable.RowHighlighted, "#results-table")
    def on_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        entry = self.row_entries.get(str(event.row_key.value))
        self._select_entry(entry)

    @on(DataTable.RowSelected, "#results-table")
    def on_row_selected(self, event: DataTable.RowSelected) -> None:
        entry = self.row_entries.get(str(event.row_key.value))
        self._select_entry(entry)
        if entry and entry.is_dir:
            self._scan_path(entry.path)

    @on(DataTable.HeaderSelected, "#results-table")
    def on_header_selected(self, event: DataTable.HeaderSelected) -> None:
        key = str(event.column_key.value)
        if key == self.sort_key:
            self.sort_reverse = not self.sort_reverse
        else:
            self.sort_key = key
            self.sort_reverse = key in {"size", "share", "modified"}
        self._populate_table(self.query_one("#filter-input", Input).value)

    def action_start_scan(self) -> None:
        value = self.query_one("#path-input", Input).value.strip()
        self._scan_path(Path(value).expanduser())

    def _scan_path(self, path: Path) -> None:
        path = safe_resolve(path)
        if not path.is_dir():
            self.notify(f"Not a directory: {path}", severity="error")
            return
        try:
            min_size = parse_size(self.config.min_size)
            dust_threads = self._optional_positive_int(self.config.dust_threads, "dust threads")
            dust_terminal_width = self._optional_positive_int(self.config.dust_terminal_width, "terminal width")
            dust_stack_size = self._optional_positive_int(self.config.dust_stack_size, "stack size")
        except ValueError as error:
            self.notify(str(error), severity="error")
            return
        self.config.last_path = str(path)
        save_config(self.config)
        self.query_one("#path-input", Input).value = str(path)
        options = ScanOptions(
            root=path,
            backend=self.config.backend,  # type: ignore[arg-type]
            max_depth=self.config.max_depth,
            max_entries=self.config.max_entries,
            mode=self.config.mode,  # type: ignore[arg-type]
            min_size=min_size,
            include_hidden=self.config.include_hidden,
            apparent_size=self.config.apparent_size,
            same_filesystem=self.config.same_filesystem,
            follow_symlinks=self.config.follow_symlinks,
            excludes=tuple(part.strip() for part in self.config.excludes.split(",") if part.strip()),
            collapse=tuple(part.strip() for part in self.config.collapse.split(",") if part.strip()),
            dust_config_file=self.config.dust_config_file.strip(),
            dust_threads=dust_threads,
            dust_reverse=self.config.dust_reverse,
            dust_no_colors=self.config.dust_no_colors,
            dust_force_colors=self.config.dust_force_colors,
            dust_no_percent_bars=self.config.dust_no_percent_bars,
            dust_bars_on_right=self.config.dust_bars_on_right,
            dust_screen_reader=self.config.dust_screen_reader,
            dust_skip_total=self.config.dust_skip_total,
            dust_filecount=self.config.dust_filecount,
            dust_file_types=self.config.dust_file_types.strip(),
            dust_terminal_width=dust_terminal_width,
            dust_print_errors=self.config.dust_print_errors,
            dust_output_format=self.config.dust_output_format,  # type: ignore[arg-type]
            dust_stack_size=dust_stack_size,
            dust_mtime=self.config.dust_mtime.strip(),
            dust_atime=self.config.dust_atime.strip(),
            dust_ctime=self.config.dust_ctime.strip(),
            dust_filter=self.config.dust_filter.strip(),
            dust_invert_filter=self.config.dust_invert_filter.strip(),
            dust_ignore_file=self.config.dust_ignore_file.strip(),
            dust_files_from=self.config.dust_files_from.strip(),
            dust_files0_from=self.config.dust_files0_from.strip(),
        )
        self.cancel_event = threading.Event()
        self.scan_worker = self.run_worker(self._run_scan(options), group="scan", exclusive=True, name=f"scan:{path}")

    @staticmethod
    def _optional_positive_int(value: str, label: str) -> int | None:
        value = value.strip()
        if not value:
            return None
        number = int(value)
        if number <= 0:
            raise ValueError(f"{label} must be greater than zero")
        return number

    async def _run_scan(self, options: ScanOptions) -> None:
        self._set_scanning(True)
        self.result = None
        self._set_status(f"Scanning {options.root}…")
        try:
            result = await scan(
                options,
                self.cancel_event,
                progress=self._native_progress,
                process_hook=self._set_dust_process,
            )
        except ScanCancelled:
            self._set_status("Scan cancelled")
            return
        except asyncio.CancelledError:
            self._set_status("Scan cancelled")
            return
        except Exception as error:
            self._set_status("Scan failed")
            self.notify(str(error), title="Scan failed", severity="error", timeout=10)
            return
        finally:
            self._set_scanning(False)
        self.result = result
        self._populate_table("")
        self._update_summary(result)
        warning = f" · {len(result.warnings)} warning(s)" if result.warnings else ""
        command = f" · {shlex.join(result.command)}" if result.command else ""
        self._set_status(
            f"Scanned {result.scanned_items:,} items in {result.duration:.2f}s using {result.backend}{warning}{command}"
        )
        if result.warnings:
            self.notify(result.warnings[0], title="Scan warning", severity="warning", timeout=8)

    def _set_dust_process(self, process: asyncio.subprocess.Process | None) -> None:
        self.dust_process = process

    def _native_progress(self, scanned: int, bytes_seen: int) -> None:
        with suppress(RuntimeError):
            self.call_from_thread(self._set_status, f"Scanning… {scanned:,} items · {format_size(bytes_seen)} observed")

    def _set_scanning(self, scanning: bool) -> None:
        self.query_one("#scan-button", Button).disabled = scanning
        self.query_one("#cancel-button", Button).disabled = not scanning
        self.query_one("#path-input", Input).disabled = scanning
        self.query_one("#status-line", Static).set_class(scanning, "scanning")

    def action_cancel_scan(self) -> None:
        self.cancel_event.set()
        if self.dust_process and self.dust_process.returncode is None:
            self.dust_process.terminate()
        if self.scan_worker:
            self.scan_worker.cancel()
        self._set_status("Cancelling scan…")

    def action_browse(self) -> None:
        current = Path(self.query_one("#path-input", Input).value).expanduser()
        if not current.is_dir():
            current = Path.home()
        self.push_screen(DirectoryPicker(current), self._directory_picked)

    def _directory_picked(self, path: Path | None) -> None:
        if path is not None:
            self.query_one("#path-input", Input).value = str(path)
            self._scan_path(path)

    def action_settings(self) -> None:
        self.push_screen(SettingsScreen(self.config), self._settings_saved)

    def _settings_saved(self, config: AppConfig | None) -> None:
        if config is None:
            return
        config.last_path = self.query_one("#path-input", Input).value
        try:
            parse_size(config.min_size)
            self._optional_positive_int(config.dust_threads, "dust threads")
            self._optional_positive_int(config.dust_terminal_width, "terminal width")
            self._optional_positive_int(config.dust_stack_size, "stack size")
        except ValueError as error:
            self.notify(str(error), severity="error")
            return
        self.config = config
        save_config(config)
        self.notify("Settings saved")

    def action_focus_search(self) -> None:
        self.query_one("#filter-input", Input).focus()

    def action_drill_down(self) -> None:
        if self.selected_entry and self.selected_entry.is_dir:
            self._scan_path(self.selected_entry.path)

    def action_scan_parent(self) -> None:
        current = safe_resolve(Path(self.query_one("#path-input", Input).value))
        if current.parent != current:
            self._scan_path(current.parent)

    def action_open_selected(self) -> None:
        if not self.selected_entry:
            return
        if not open_path(self.selected_entry.path):
            self.notify("No supported file opener was found", severity="error")

    def action_copy_selected(self) -> None:
        if not self.selected_entry:
            return
        if copy_to_clipboard(str(self.selected_entry.path)):
            self.notify("Path copied")
        else:
            self.notify("No supported clipboard command was found", severity="warning")

    def action_inspect_selected(self) -> None:
        if not self.selected_entry:
            self.notify("Select a result to inspect", severity="warning")
            return
        self.push_screen(EntryDetailsScreen(self.selected_entry.name, self._entry_details_markup(self.selected_entry)))

    def action_trash_selected(self) -> None:
        entry = self.selected_entry
        if entry is None or self.result is None:
            return
        path = safe_resolve(entry.path)
        root = safe_resolve(self.result.root)
        dangerous = {safe_resolve(Path(path.anchor)), safe_resolve(Path.home()), root}
        if path in dangerous or not path.is_relative_to(root):
            self.notify("Refusing to trash a protected or out-of-scope path", severity="error")
            return
        self.push_screen(
            ConfirmTrashScreen(path, format_size(entry.size)),
            lambda confirmed: self._trash_confirmed(path, confirmed),
        )

    def _trash_confirmed(self, path: Path, confirmed: bool) -> None:
        if not confirmed:
            return
        try:
            send2trash(str(path))
        except Exception as error:
            self.notify(str(error), title="Could not move item to trash", severity="error")
            return
        self.notify(f"Moved {path.name} to trash")
        if self.result:
            self.result.entries = [entry for entry in self.result.entries if entry.path != path]
            self._populate_table(self.query_one("#filter-input", Input).value)
        self._select_entry(None)

    def action_export(self) -> None:
        if self.result is None:
            self.notify("Run a scan before exporting", severity="warning")
            return
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        directory = user_data_dir() / "exports"
        directory.mkdir(parents=True, exist_ok=True)
        json_path = directory / f"dust-scan-{stamp}.json"
        csv_path = directory / f"dust-scan-{stamp}.csv"
        payload = {
            "root": str(self.result.root),
            "backend": self.result.backend,
            "total_size": self.result.total_size,
            "duration_seconds": self.result.duration,
            "entries": [
                {
                    "path": str(entry.path),
                    "size": entry.size,
                    "is_directory": entry.is_dir,
                    "depth": entry.depth,
                    "modified": entry.modified,
                }
                for entry in self.result.entries
            ],
        }
        json_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        with csv_path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(("size_bytes", "share_percent", "kind", "modified", "path"))
            for entry in self.result.entries:
                share = entry.size / self.result.total_size * 100 if self.result.total_size else 0
                writer.writerow((entry.size, f"{share:.4f}", entry.kind, entry.modified or "", entry.path))
        copy_to_clipboard(str(json_path))
        self.notify(f"Exported JSON and CSV to {directory}", timeout=8)

    def action_help(self) -> None:
        self.push_screen(HelpScreen())

    def _populate_table(self, query: str) -> None:
        table = self.query_one("#results-table", DataTable)
        table.clear()
        self.row_entries.clear()
        self._select_entry(None)
        if self.result is None:
            return
        words = [word.casefold() for word in query.split() if word]
        entries = [entry for entry in self.result.entries if all(word in str(entry.path).casefold() for word in words)]
        entries.sort(key=self._sort_value, reverse=self.sort_reverse)
        self.visible_entries = entries
        total = self.result.total_size
        for index, entry in enumerate(entries):
            ratio = entry.size / total if total else 0
            share = Text(f"{make_usage_bar(ratio, ascii_only=self.config.ascii_bars)} {ratio * 100:5.1f}%")
            row_key = str(index)
            table.add_row(
                Text(format_size(entry.size), justify="right"),
                share,
                entry.kind,
                format_timestamp(entry.modified),
                str(entry.path),
                key=row_key,
            )
            self.row_entries[row_key] = entry
        self.query_one("#summary-results", Static).update(f"[dim]Results[/dim]\n[b]{len(entries):,}[/b]")
        self._update_chart(entries)

    def _sort_value(self, entry: DiskEntry) -> Any:
        values = {
            "size": entry.size,
            "share": entry.size,
            "kind": entry.kind.casefold(),
            "modified": entry.modified or 0,
            "path": str(entry.path).casefold(),
        }
        return values.get(self.sort_key, entry.size)

    def _update_summary(self, result: ScanResult) -> None:
        largest = max(result.entries, key=lambda item: item.size, default=None)
        self.query_one("#summary-total", Static).update(f"[dim]Total[/dim]\n[b]{format_size(result.total_size)}[/b]")
        self.query_one("#summary-results", Static).update(f"[dim]Results[/dim]\n[b]{len(result.entries):,}[/b]")
        largest_text = format_size(largest.size) if largest else "—"
        self.query_one("#summary-largest", Static).update(f"[dim]Largest[/dim]\n[b]{largest_text}[/b]")
        self.query_one("#summary-backend", Static).update(f"[dim]Backend[/dim]\n[b]{escape(result.backend)}[/b]")

    def _update_chart(self, entries: list[DiskEntry]) -> None:
        if self.result is None or not entries:
            self.query_one("#usage-chart", Static).update("No matching results.")
            return
        total = self.result.total_size
        lines = []
        for entry in entries[:8]:
            ratio = entry.size / total if total else 0
            name = entry.name
            if len(name) > 28:
                name = name[:25] + "…"
            bar = make_usage_bar(ratio, 24, ascii_only=self.config.ascii_bars)
            size = format_size(entry.size)
            lines.append(f"[b]{bar}[/b] {ratio * 100:5.1f}%  {escape(name)}  [dim]{size}[/dim]")
        self.query_one("#usage-chart", Static).update("\n".join(lines))

    def _select_entry(self, entry: DiskEntry | None) -> None:
        self.selected_entry = entry
        enabled = entry is not None
        for selector in ("#open-button", "#copy-button", "#trash-button", "#details-button"):
            with suppress(NoMatches):
                self.query_one(selector, Button).disabled = not enabled
        with suppress(NoMatches):
            self.query_one("#drill-button", Button).disabled = not (entry and entry.is_dir)
        try:
            details_title = self.query_one("#details-title", Label)
            details_body = self.query_one("#details-body", Static)
        except NoMatches:
            return
        if entry is None:
            details_title.update("Selection")
            details_body.update("Select a result to inspect it.")
            return
        details_title.update(entry.name)
        details_body.update(self._entry_details_markup(entry))

    def _entry_details_markup(self, entry: DiskEntry) -> str:
        ratio = entry.size / self.result.total_size if self.result and self.result.total_size else 0
        return (
            f"[dim]Path[/dim]\n{escape(str(entry.path))}\n\n"
            f"[dim]Size[/dim]\n[b]{format_size(entry.size)}[/b]\n\n"
            f"[dim]Share of scan[/dim]\n{ratio * 100:.2f}%\n\n"
            f"[dim]Type[/dim]\n{entry.kind}\n\n"
            f"[dim]Modified[/dim]\n{format_timestamp(entry.modified)}\n\n"
            f"[dim]Depth[/dim]\n{entry.depth}"
        )

    def _set_status(self, message: str) -> None:
        self.query_one("#status-line", Static).update(escape(message))

    def on_worker_state_changed(self, event: Worker.StateChanged) -> None:
        if event.worker is self.scan_worker and event.state in {WorkerState.CANCELLED, WorkerState.ERROR}:
            self._set_scanning(False)

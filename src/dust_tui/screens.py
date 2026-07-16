from __future__ import annotations

from pathlib import Path

from textual.app import ComposeResult
from textual.containers import Horizontal, Vertical
from textual.screen import ModalScreen
from textual.widgets import Button, Checkbox, DirectoryTree, Input, Label, Select, Static

from .config import AppConfig


class DirectoryPicker(ModalScreen[Path | None]):
    BINDINGS = [("escape", "cancel", "Cancel")]

    def __init__(self, initial: Path) -> None:
        super().__init__()
        self.initial = initial
        self.selected = initial

    def compose(self) -> ComposeResult:
        with Vertical(id="dialog", classes="wide-dialog"):
            yield Label("Choose scan directory", classes="dialog-title")
            yield Input(value=str(self.initial), id="picker-path")
            yield DirectoryTree(self.initial, id="directory-tree")
            with Horizontal(classes="dialog-actions"):
                yield Button("Cancel", id="picker-cancel")
                yield Button("Use directory", id="picker-select", variant="primary")

    def on_directory_tree_directory_selected(self, event: DirectoryTree.DirectorySelected) -> None:
        self.selected = Path(event.path)
        self.query_one("#picker-path", Input).value = str(self.selected)

    def on_input_submitted(self, event: Input.Submitted) -> None:
        self._finish(event.value)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "picker-cancel":
            self.dismiss(None)
        elif event.button.id == "picker-select":
            self._finish(self.query_one("#picker-path", Input).value)

    def _finish(self, value: str) -> None:
        path = Path(value).expanduser()
        if path.is_dir():
            self.dismiss(path)
        else:
            self.notify("That path is not a readable directory", severity="error")

    def action_cancel(self) -> None:
        self.dismiss(None)


class SettingsScreen(ModalScreen[AppConfig | None]):
    BINDINGS = [("escape", "cancel", "Cancel")]

    def __init__(self, config: AppConfig) -> None:
        super().__init__()
        self.config = config

    def compose(self) -> ComposeResult:
        depth_value = "all" if self.config.max_depth is None else str(self.config.max_depth)
        output_formats = {"", "si", "b", "k", "m", "g", "t", "kb", "mb", "gb", "tb"}
        output_format = self.config.dust_output_format if self.config.dust_output_format in output_formats else ""
        with Vertical(id="dialog", classes="wide-dialog settings-dialog"):
            yield Label("Scan settings", classes="dialog-title")
            yield Label("Backend")
            yield Select(
                [("Automatic (prefer dust)", "auto"), ("dust", "dust"), ("Native Python", "native")],
                value=self.config.backend,
                allow_blank=False,
                id="settings-backend",
            )
            yield Label("Maximum depth")
            yield Select(
                [("1", "1"), ("2", "2"), ("3", "3"), ("5", "5"), ("8", "8"), ("Unlimited", "all")],
                value=depth_value if depth_value in {"1", "2", "3", "5", "8", "all"} else "5",
                allow_blank=False,
                id="settings-depth",
            )
            yield Label("Maximum displayed entries")
            yield Select(
                [("100", 100), ("250", 250), ("500", 500), ("1,000", 1000), ("2,000", 2000)],
                value=self.config.max_entries if self.config.max_entries in {100, 250, 500, 1000, 2000} else 500,
                allow_blank=False,
                id="settings-entries",
            )
            yield Label("Entry mode")
            yield Select(
                [("Files and directories", "all"), ("Directories only", "directories"), ("Files only", "files")],
                value=self.config.mode,
                allow_blank=False,
                id="settings-mode",
            )
            yield Label("Minimum size (examples: 500MB, 2GiB)")
            yield Input(value=self.config.min_size, id="settings-min-size")
            yield Label("Exclude names or globs, comma-separated")
            yield Input(value=self.config.excludes, id="settings-excludes")
            yield Label("Collapse directories, comma-separated")
            yield Input(value=self.config.collapse, id="settings-collapse")
            yield Checkbox("Include hidden entries", value=self.config.include_hidden, id="settings-hidden")
            yield Checkbox("Use apparent size", value=self.config.apparent_size, id="settings-apparent")
            yield Checkbox("Stay on the same filesystem", value=self.config.same_filesystem, id="settings-filesystem")
            yield Checkbox("Use ASCII usage bars", value=self.config.ascii_bars, id="settings-ascii-bars")
            yield Checkbox(
                "Dereference symbolic links",
                value=self.config.follow_symlinks,
                id="settings-follow-symlinks",
            )
            yield Label("dust config file (--config)")
            yield Input(value=self.config.dust_config_file, id="settings-dust-config")
            yield Label("dust worker threads (-T)")
            yield Input(value=self.config.dust_threads, id="settings-dust-threads")
            yield Label("dust output format (-o)")
            yield Select(
                [
                    ("Default", ""),
                    ("SI", "si"),
                    ("Bytes", "b"),
                    ("KiB", "k"),
                    ("MiB", "m"),
                    ("GiB", "g"),
                    ("TiB", "t"),
                    ("kB", "kb"),
                    ("MB", "mb"),
                    ("GB", "gb"),
                    ("TB", "tb"),
                ],
                value=output_format,
                allow_blank=False,
                id="settings-dust-output-format",
            )
            yield Label("Minimum terminal width (-w)")
            yield Input(value=self.config.dust_terminal_width, id="settings-dust-terminal-width")
            yield Label("Stack size bytes (-S)")
            yield Input(value=self.config.dust_stack_size, id="settings-dust-stack-size")
            yield Label("File types (-t)")
            yield Input(value=self.config.dust_file_types, id="settings-dust-file-types")
            yield Label("Include regex (-e)")
            yield Input(value=self.config.dust_filter, id="settings-dust-filter")
            yield Label("Exclude regex (-v)")
            yield Input(value=self.config.dust_invert_filter, id="settings-dust-invert-filter")
            yield Label("Ignore regex file (-I)")
            yield Input(value=self.config.dust_ignore_file, id="settings-dust-ignore-file")
            yield Label("Modified time filter (-M)")
            yield Input(value=self.config.dust_mtime, id="settings-dust-mtime")
            yield Label("Access time filter (-A)")
            yield Input(value=self.config.dust_atime, id="settings-dust-atime")
            yield Label("Change time filter (-y)")
            yield Input(value=self.config.dust_ctime, id="settings-dust-ctime")
            yield Label("Read paths from file (--files-from)")
            yield Input(value=self.config.dust_files_from, id="settings-dust-files-from")
            yield Label("Read NUL paths from file (--files0-from)")
            yield Input(value=self.config.dust_files0_from, id="settings-dust-files0-from")
            yield Checkbox("Reverse output tree", value=self.config.dust_reverse, id="settings-dust-reverse")
            yield Checkbox("Disable colors", value=self.config.dust_no_colors, id="settings-dust-no-colors")
            yield Checkbox("Force colors", value=self.config.dust_force_colors, id="settings-dust-force-colors")
            yield Checkbox("Disable percent bars", value=self.config.dust_no_percent_bars, id="settings-dust-no-bars")
            yield Checkbox("Put bars on the right", value=self.config.dust_bars_on_right, id="settings-dust-bars-right")
            yield Checkbox("Screen reader mode", value=self.config.dust_screen_reader, id="settings-dust-screen-reader")
            yield Checkbox("Skip total row", value=self.config.dust_skip_total, id="settings-dust-skip-total")
            yield Checkbox(
                "Use file count as directory size",
                value=self.config.dust_filecount,
                id="settings-dust-filecount",
            )
            yield Checkbox("Print path errors", value=self.config.dust_print_errors, id="settings-dust-print-errors")
            with Horizontal(classes="dialog-actions"):
                yield Button("Cancel", id="settings-cancel")
                yield Button("Save", id="settings-save", variant="primary")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "settings-cancel":
            self.dismiss(None)
            return
        if event.button.id != "settings-save":
            return
        depth = self.query_one("#settings-depth", Select).value
        config = AppConfig(
            backend=str(self.query_one("#settings-backend", Select).value),
            max_depth=None if depth == "all" else int(str(depth)),
            max_entries=int(self.query_one("#settings-entries", Select).value),
            mode=str(self.query_one("#settings-mode", Select).value),
            min_size=self.query_one("#settings-min-size", Input).value.strip() or "0",
            include_hidden=self.query_one("#settings-hidden", Checkbox).value,
            apparent_size=self.query_one("#settings-apparent", Checkbox).value,
            same_filesystem=self.query_one("#settings-filesystem", Checkbox).value,
            ascii_bars=self.query_one("#settings-ascii-bars", Checkbox).value,
            follow_symlinks=self.query_one("#settings-follow-symlinks", Checkbox).value,
            excludes=self.query_one("#settings-excludes", Input).value,
            collapse=self.query_one("#settings-collapse", Input).value,
            dust_config_file=self.query_one("#settings-dust-config", Input).value.strip(),
            dust_threads=self.query_one("#settings-dust-threads", Input).value.strip(),
            dust_reverse=self.query_one("#settings-dust-reverse", Checkbox).value,
            dust_no_colors=self.query_one("#settings-dust-no-colors", Checkbox).value,
            dust_force_colors=self.query_one("#settings-dust-force-colors", Checkbox).value,
            dust_no_percent_bars=self.query_one("#settings-dust-no-bars", Checkbox).value,
            dust_bars_on_right=self.query_one("#settings-dust-bars-right", Checkbox).value,
            dust_screen_reader=self.query_one("#settings-dust-screen-reader", Checkbox).value,
            dust_skip_total=self.query_one("#settings-dust-skip-total", Checkbox).value,
            dust_filecount=self.query_one("#settings-dust-filecount", Checkbox).value,
            dust_file_types=self.query_one("#settings-dust-file-types", Input).value.strip(),
            dust_terminal_width=self.query_one("#settings-dust-terminal-width", Input).value.strip(),
            dust_print_errors=self.query_one("#settings-dust-print-errors", Checkbox).value,
            dust_output_format=str(self.query_one("#settings-dust-output-format", Select).value),
            dust_stack_size=self.query_one("#settings-dust-stack-size", Input).value.strip(),
            dust_mtime=self.query_one("#settings-dust-mtime", Input).value.strip(),
            dust_atime=self.query_one("#settings-dust-atime", Input).value.strip(),
            dust_ctime=self.query_one("#settings-dust-ctime", Input).value.strip(),
            dust_filter=self.query_one("#settings-dust-filter", Input).value.strip(),
            dust_invert_filter=self.query_one("#settings-dust-invert-filter", Input).value.strip(),
            dust_ignore_file=self.query_one("#settings-dust-ignore-file", Input).value.strip(),
            dust_files_from=self.query_one("#settings-dust-files-from", Input).value.strip(),
            dust_files0_from=self.query_one("#settings-dust-files0-from", Input).value.strip(),
            last_path=self.config.last_path,
        )
        self.dismiss(config)

    def action_cancel(self) -> None:
        self.dismiss(None)


class ConfirmTrashScreen(ModalScreen[bool]):
    BINDINGS = [("escape", "cancel", "Cancel")]

    def __init__(self, path: Path, display_size: str) -> None:
        super().__init__()
        self.path = path
        self.display_size = display_size

    def compose(self) -> ComposeResult:
        with Vertical(id="dialog", classes="confirm-dialog"):
            yield Label("Move to trash?", classes="dialog-title")
            yield Static(
                f"[b]{self.path.name or self.path}[/b]\n\n{self.path}\n\nApproximate size: {self.display_size}\n\n"
                "The item will be sent to the operating system trash, not permanently deleted."
            )
            with Horizontal(classes="dialog-actions"):
                yield Button("Cancel", id="trash-cancel")
                yield Button("Move to trash", id="trash-confirm", variant="error")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        self.dismiss(event.button.id == "trash-confirm")

    def action_cancel(self) -> None:
        self.dismiss(False)


class EntryDetailsScreen(ModalScreen[None]):
    BINDINGS = [("escape", "close", "Close"), ("i", "close", "Close")]

    def __init__(self, title: str, body: str) -> None:
        super().__init__()
        self.title_text = title
        self.body = body

    def compose(self) -> ComposeResult:
        with Vertical(id="dialog", classes="wide-dialog details-dialog"):
            yield Label(self.title_text, classes="dialog-title")
            yield Static(self.body, id="details-dialog-body")
            with Horizontal(classes="dialog-actions"):
                yield Button("Close", id="details-close", variant="primary")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        self.dismiss(None)

    def action_close(self) -> None:
        self.dismiss(None)


class HelpScreen(ModalScreen[None]):
    BINDINGS = [("escape", "close", "Close"), ("question_mark", "close", "Close")]

    def compose(self) -> ComposeResult:
        with Vertical(id="dialog", classes="wide-dialog help-dialog"):
            yield Label("Keyboard shortcuts", classes="dialog-title")
            yield Static(
                "[b]r[/b] Scan/rescan    [b]x[/b] Cancel scan    [b]b[/b] Browse\n"
                "[b]enter[/b] Drill into selected directory    [b]backspace[/b] Scan parent\n"
                "[b]i[/b] Inspect selected item    [b]o[/b] Open selected path    [b]c[/b] Copy path\n"
                "[b]t[/b] Move to trash\n"
                "[b]e[/b] Export JSON + CSV    [b]s[/b] Settings    [b]/[/b] Search\n"
                "[b]?[/b] Help    [b]q[/b] Quit\n\n"
                "Select a column heading to sort. The search field filters the current results without rescanning."
            )
            with Horizontal(classes="dialog-actions"):
                yield Button("Close", id="help-close", variant="primary")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        self.dismiss(None)

    def action_close(self) -> None:
        self.dismiss(None)

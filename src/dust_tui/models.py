from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

BackendName = Literal["auto", "dust", "native"]
EntryMode = Literal["all", "directories", "files"]
DustOutputFormat = Literal["", "si", "b", "k", "m", "g", "t", "kb", "mb", "gb", "tb"]


@dataclass(slots=True)
class ScanOptions:
    root: Path
    backend: BackendName = "auto"
    max_depth: int | None = 5
    max_entries: int = 500
    mode: EntryMode = "all"
    min_size: int = 0
    include_hidden: bool = False
    apparent_size: bool = False
    same_filesystem: bool = False
    follow_symlinks: bool = False
    excludes: tuple[str, ...] = ()
    collapse: tuple[str, ...] = ()
    dust_config_file: str = ""
    dust_threads: int | None = None
    dust_reverse: bool = False
    dust_no_colors: bool = False
    dust_force_colors: bool = False
    dust_no_percent_bars: bool = False
    dust_bars_on_right: bool = False
    dust_screen_reader: bool = False
    dust_skip_total: bool = False
    dust_filecount: bool = False
    dust_file_types: str = ""
    dust_terminal_width: int | None = None
    dust_print_errors: bool = False
    dust_output_format: DustOutputFormat = ""
    dust_stack_size: int | None = None
    dust_mtime: str = ""
    dust_atime: str = ""
    dust_ctime: str = ""
    dust_filter: str = ""
    dust_invert_filter: str = ""
    dust_ignore_file: str = ""
    dust_files_from: str = ""
    dust_files0_from: str = ""


@dataclass(slots=True)
class DiskEntry:
    path: Path
    size: int
    is_dir: bool
    depth: int = 0
    modified: float | None = None
    file_count: int | None = None

    @property
    def name(self) -> str:
        return self.path.name or str(self.path)

    @property
    def kind(self) -> str:
        return "Directory" if self.is_dir else "File"


@dataclass(slots=True)
class ScanResult:
    root: Path
    entries: list[DiskEntry]
    total_size: int
    backend: str
    duration: float
    scanned_items: int = 0
    command: tuple[str, ...] = ()
    warnings: list[str] = field(default_factory=list)
    raw_json: object | None = None

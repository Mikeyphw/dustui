from __future__ import annotations

import json
import os
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


@dataclass(slots=True)
class AppConfig:
    backend: str = "auto"
    max_depth: int | None = 5
    max_entries: int = 500
    mode: str = "all"
    min_size: str = "0"
    include_hidden: bool = False
    apparent_size: bool = False
    same_filesystem: bool = False
    follow_symlinks: bool = False
    ascii_bars: bool = False
    excludes: str = ".git,node_modules,.cache"
    collapse: str = ""
    dust_config_file: str = ""
    dust_threads: str = ""
    dust_reverse: bool = False
    dust_no_colors: bool = False
    dust_force_colors: bool = False
    dust_no_percent_bars: bool = False
    dust_bars_on_right: bool = False
    dust_screen_reader: bool = False
    dust_skip_total: bool = False
    dust_filecount: bool = False
    dust_file_types: str = ""
    dust_terminal_width: str = ""
    dust_print_errors: bool = False
    dust_output_format: str = ""
    dust_stack_size: str = ""
    dust_mtime: str = ""
    dust_atime: str = ""
    dust_ctime: str = ""
    dust_filter: str = ""
    dust_invert_filter: str = ""
    dust_ignore_file: str = ""
    dust_files_from: str = ""
    dust_files0_from: str = ""
    last_path: str = str(Path.home())


def config_dir() -> Path:
    if sys.platform == "win32":
        root = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
    elif sys.platform == "darwin":
        root = Path.home() / "Library" / "Application Support"
    else:
        root = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return root / "dust-tui"


def load_config() -> AppConfig:
    path = config_dir() / "config.json"
    try:
        payload: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return AppConfig()
    allowed = AppConfig.__dataclass_fields__.keys()
    return AppConfig(**{key: value for key, value in payload.items() if key in allowed})


def save_config(config: AppConfig) -> None:
    directory = config_dir()
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / "config.json"
    temporary = target.with_suffix(".tmp")
    temporary.write_text(json.dumps(asdict(config), indent=2), encoding="utf-8")
    temporary.replace(target)

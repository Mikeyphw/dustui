from __future__ import annotations

import json
import math
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

_SIZE_RE = re.compile(r"^\s*(\d+(?:\.\d+)?)\s*([kmgtpe]?i?b?)?\s*$", re.IGNORECASE)
_ANSI_RE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")


def format_size(value: int) -> str:
    value = max(0, int(value))
    if value < 1000:
        return f"{value} B"
    units = ("KB", "MB", "GB", "TB", "PB", "EB")
    size = float(value)
    for unit in units:
        size /= 1000.0
        if size < 1000 or unit == units[-1]:
            precision = 0 if size >= 100 else 1 if size >= 10 else 2
            return f"{size:.{precision}f} {unit}"
    return f"{value} B"


def parse_size(value: str | int | float | None) -> int:
    if value is None or value == "":
        return 0
    if isinstance(value, (int, float)):
        return max(0, int(value))
    match = _SIZE_RE.match(value)
    if not match:
        raise ValueError(f"Invalid size: {value!r}")
    number = float(match.group(1))
    suffix = (match.group(2) or "b").lower()
    if suffix in {"", "b"}:
        factor = 1
    else:
        binary = "i" in suffix
        letter = suffix[0]
        exponent = "kmgtpe".index(letter) + 1
        factor = (1024 if binary else 1000) ** exponent
    return max(0, int(number * factor))


def format_timestamp(value: float | None) -> str:
    if value is None:
        return "—"
    try:
        return datetime.fromtimestamp(value).strftime("%Y-%m-%d %H:%M")
    except (OSError, OverflowError, ValueError):
        return "—"


def strip_ansi(value: str) -> str:
    return _ANSI_RE.sub("", value)


def load_json_documents(value: str) -> object:
    """Parse one JSON value or a stream of adjacent JSON values."""
    text = strip_ansi(value).strip()
    if not text:
        raise ValueError("dust produced no JSON output")
    first_object = min((index for index in (text.find("{"), text.find("[")) if index >= 0), default=0)
    text = text[first_object:]
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        decoder = json.JSONDecoder()
        values: list[Any] = []
        offset = 0
        while offset < len(text):
            while offset < len(text) and text[offset].isspace():
                offset += 1
            if offset >= len(text):
                break
            parsed, offset = decoder.raw_decode(text, offset)
            values.append(parsed)
        if not values:
            raise
        return values[0] if len(values) == 1 else values


def make_usage_bar(ratio: float, width: int = 14, ascii_only: bool = False) -> str:
    ratio = min(1.0, max(0.0, ratio))
    if ascii_only:
        filled = int(math.floor(ratio * width))
        return ("#" * filled).ljust(width, ".")
    filled = int(math.floor(ratio * width))
    partial = ratio * width - filled
    blocks = "▏▎▍▌▋▊▉"
    bar = "█" * filled
    if filled < width and partial > 0:
        bar += blocks[min(len(blocks) - 1, int(partial * len(blocks)))]
    return bar.ljust(width, "·")


def safe_resolve(path: Path) -> Path:
    try:
        return path.expanduser().resolve(strict=False)
    except OSError:
        return path.expanduser().absolute()


def copy_to_clipboard(text: str) -> bool:
    candidates: list[tuple[list[str], bytes]] = []
    if shutil.which("termux-clipboard-set"):
        candidates.append((["termux-clipboard-set"], text.encode()))
    if shutil.which("wl-copy"):
        candidates.append((["wl-copy"], text.encode()))
    if shutil.which("xclip"):
        candidates.append((["xclip", "-selection", "clipboard"], text.encode()))
    if shutil.which("xsel"):
        candidates.append((["xsel", "--clipboard", "--input"], text.encode()))
    if shutil.which("pbcopy"):
        candidates.append((["pbcopy"], text.encode()))
    if shutil.which("clip.exe"):
        candidates.append((["clip.exe"], text.encode("utf-16le")))
    for command, data in candidates:
        try:
            subprocess.run(command, input=data, check=True, timeout=5)
            return True
        except (OSError, subprocess.SubprocessError):
            continue
    return False


def open_path(path: Path) -> bool:
    resolved = str(path)
    try:
        if hasattr(os, "startfile"):
            os.startfile(resolved)  # type: ignore[attr-defined]
            return True
        for command in ("termux-open", "xdg-open", "open"):
            if shutil.which(command):
                subprocess.Popen(
                    [command, resolved],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    start_new_session=True,
                )
                return True
    except OSError:
        return False
    return False


def user_data_dir() -> Path:
    if sys.platform == "win32":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    elif sys.platform == "darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    return base / "dust-tui"

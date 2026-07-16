from __future__ import annotations

import asyncio
import fnmatch
import json
import os
import shutil
import stat
import threading
import time
from collections.abc import Callable, Iterable
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

from .models import DiskEntry, ScanOptions, ScanResult
from .utils import load_json_documents, safe_resolve

ProgressCallback = Callable[[int, int], None]
_CHILD_KEYS = ("children", "entries", "nodes", "items", "subdirectories", "files")
_PATH_KEYS = ("path", "name", "file", "directory", "location")
_SIZE_KEYS = ("size", "bytes", "size_bytes", "apparent_size", "usage")


class ScanCancelled(RuntimeError):
    pass


class DustUnavailable(RuntimeError):
    pass


def find_dust() -> str | None:
    override = os.environ.get("DUST_TUI_DUST_BINARY")
    if override:
        return override if Path(override).is_file() else shutil.which(override)
    return shutil.which("dust")


def build_dust_command(binary: str, options: ScanOptions) -> list[str]:
    command = [binary]
    if options.dust_config_file:
        command += ["--config", options.dust_config_file]
    command += ["-j", "-p", "-P", "-n", str(options.max_entries)]
    if options.dust_threads is not None:
        command += ["-T", str(options.dust_threads)]
    if options.max_depth is not None:
        command += ["-d", str(options.max_depth)]
    if options.follow_symlinks:
        command.append("-L")
    if options.mode == "directories":
        command.append("-D")
    elif options.mode == "files":
        command.append("-F")
    if options.apparent_size:
        command.append("-s")
    if options.same_filesystem:
        command.append("-x")
    if not options.include_hidden:
        command.append("-i")
    if options.dust_reverse:
        command.append("-r")
    if options.dust_no_colors:
        command.append("-c")
    if options.dust_force_colors:
        command.append("-C")
    if options.dust_no_percent_bars:
        command.append("-b")
    if options.dust_bars_on_right:
        command.append("-B")
    if options.dust_screen_reader:
        command.append("-R")
    if options.dust_skip_total:
        command.append("--skip-total")
    if options.dust_filecount:
        command.append("-f")
    if options.min_size:
        command += ["-z", str(options.min_size)]
    if options.dust_file_types:
        command += ["-t", options.dust_file_types]
    if options.dust_terminal_width is not None:
        command += ["-w", str(options.dust_terminal_width)]
    if options.dust_print_errors:
        command.append("--print-errors")
    if options.dust_output_format:
        command += ["-o", options.dust_output_format]
    if options.dust_stack_size is not None:
        command += ["-S", str(options.dust_stack_size)]
    if options.dust_mtime:
        command += ["-M", options.dust_mtime]
    if options.dust_atime:
        command += ["-A", options.dust_atime]
    if options.dust_ctime:
        command += ["-y", options.dust_ctime]
    if options.dust_filter:
        command += ["-e", options.dust_filter]
    if options.dust_invert_filter:
        command += ["-v", options.dust_invert_filter]
    if options.dust_ignore_file:
        command += ["-I", options.dust_ignore_file]
    if options.dust_files_from:
        command += ["--files-from", options.dust_files_from]
    if options.dust_files0_from:
        command += ["--files0-from", options.dust_files0_from]
    for pattern in options.excludes:
        command += ["-X", pattern]
    for name in options.collapse:
        command.append(f"--collapse={name}")
    command.append(str(options.root))
    return command


async def scan_with_dust(
    options: ScanOptions,
    cancel_event: threading.Event,
    process_hook: Callable[[asyncio.subprocess.Process | None], None] | None = None,
) -> ScanResult:
    binary = find_dust()
    if binary is None:
        raise DustUnavailable("The dust executable was not found in PATH")
    command = build_dust_command(binary, options)
    started = time.monotonic()
    process = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    if process_hook:
        process_hook(process)
    try:
        stdout, stderr = await process.communicate()
    except asyncio.CancelledError:
        cancel_event.set()
        if process.returncode is None:
            process.terminate()
            try:
                await asyncio.wait_for(process.wait(), timeout=2)
            except asyncio.TimeoutError:
                process.kill()
        raise
    finally:
        if process_hook:
            process_hook(None)
    if cancel_event.is_set():
        raise ScanCancelled("Scan cancelled")
    stderr_text = stderr.decode(errors="replace").strip()
    if process.returncode != 0:
        raise RuntimeError(stderr_text or f"dust exited with status {process.returncode}")
    raw = load_json_documents(stdout.decode(errors="replace"))
    entries, total = parse_dust_json(raw, options.root)
    entries = _filter_and_limit(entries, options)
    warnings = [line for line in stderr_text.splitlines() if line.strip()]
    return ScanResult(
        root=options.root,
        entries=entries,
        total_size=total,
        backend="dust",
        duration=time.monotonic() - started,
        scanned_items=len(entries),
        command=tuple(command),
        warnings=warnings,
        raw_json=raw,
    )


def parse_dust_json(raw: object, root: Path) -> tuple[list[DiskEntry], int]:
    """Parse dust JSON defensively across known and future compatible schemas."""
    root = safe_resolve(root)
    entries: list[DiskEntry] = []
    roots: list[DiskEntry] = []
    seen: set[tuple[str, int]] = set()

    def walk(node: Any, parent: Path | None = None, depth: int = 0) -> None:
        if isinstance(node, list):
            for child in node:
                walk(child, parent, depth)
            return
        if not isinstance(node, dict):
            return

        path_value = next((node.get(key) for key in _PATH_KEYS if node.get(key) is not None), None)
        size_value = next((node.get(key) for key in _SIZE_KEYS if node.get(key) is not None), 0)
        children: list[Any] = []
        for key in _CHILD_KEYS:
            value = node.get(key)
            if isinstance(value, list):
                children.extend(value)
            elif isinstance(value, dict):
                children.append(value)

        current_parent = parent
        if path_value is not None:
            raw_path = Path(str(path_value))
            if raw_path.is_absolute():
                path = safe_resolve(raw_path)
            elif parent is not None:
                path = safe_resolve(parent / raw_path)
            else:
                path = safe_resolve(root / raw_path) if str(raw_path) not in {".", root.name} else root
            size = _coerce_size(size_value)
            is_dir_value = node.get("is_dir", node.get("directory"))
            kind_value = str(node.get("type", node.get("kind", ""))).lower()
            is_dir = bool(children) or is_dir_value is True or kind_value in {"dir", "directory", "folder"}
            try:
                metadata = path.stat()
            except OSError:
                modified = None
            else:
                modified = metadata.st_mtime
                if not children and not is_dir_value and not kind_value:
                    is_dir = stat.S_ISDIR(metadata.st_mode)
            entry = DiskEntry(path=path, size=size, is_dir=is_dir, depth=depth, modified=modified)
            marker = (str(path), size)
            if marker not in seen:
                seen.add(marker)
                entries.append(entry)
                if parent is None:
                    roots.append(entry)
            current_parent = path
        for child in children:
            walk(child, current_parent, depth + 1)

        # Some schemas wrap actual records under arbitrary keys.
        if path_value is None and not children:
            for value in node.values():
                if isinstance(value, (dict, list)):
                    walk(value, parent, depth)

    walk(raw)
    total = max((entry.size for entry in roots), default=max((entry.size for entry in entries), default=0))
    non_roots = [entry for entry in entries if safe_resolve(entry.path) != root]
    return non_roots, total


def _coerce_size(value: Any) -> int:
    if isinstance(value, bool):
        return 0
    if isinstance(value, (int, float)):
        return max(0, int(value))
    if isinstance(value, dict):
        for key in ("bytes", "size", "value"):
            if key in value:
                return _coerce_size(value[key])
    if isinstance(value, str):
        try:
            return max(0, int(float(value)))
        except ValueError:
            return 0
    return 0


def _matches_exclude(path: Path, root: Path, patterns: Iterable[str]) -> bool:
    try:
        relative = path.relative_to(root)
    except ValueError:
        relative = path
    text = str(relative)
    return any(fnmatch.fnmatch(path.name, pattern) or fnmatch.fnmatch(text, pattern) for pattern in patterns)


def _filter_and_limit(entries: list[DiskEntry], options: ScanOptions) -> list[DiskEntry]:
    filtered: list[DiskEntry] = []
    for entry in entries:
        if entry.size < options.min_size:
            continue
        if options.mode == "directories" and not entry.is_dir:
            continue
        if options.mode == "files" and entry.is_dir:
            continue
        if _matches_exclude(entry.path, options.root, options.excludes):
            continue
        filtered.append(entry)
    filtered.sort(key=lambda item: (-item.size, str(item.path).casefold()))
    return filtered[: options.max_entries]


def scan_native(
    options: ScanOptions,
    cancel_event: threading.Event,
    progress: ProgressCallback | None = None,
) -> ScanResult:
    started = time.monotonic()
    root = safe_resolve(options.root)
    try:
        root_stat = root.stat()
    except OSError as error:
        raise RuntimeError(f"Cannot read {root}: {error}") from error
    root_device = root_stat.st_dev
    warnings: list[str] = []
    scanned_items = 0
    scanned_bytes = 0
    seen_files: set[tuple[int, int]] = set()
    sizes: dict[Path, int] = {}
    mtimes: dict[Path, float] = {}
    is_dirs: dict[Path, bool] = {}
    depths: dict[Path, int] = {root: 0}
    stack: list[tuple[Path, bool]] = [(root, False)]

    while stack:
        if cancel_event.is_set():
            raise ScanCancelled("Scan cancelled")
        path, expanded = stack.pop()
        depth = depths[path]
        if expanded:
            total = sizes.get(path, 0)
            try:
                with os.scandir(path) as iterator:
                    for child in iterator:
                        child_path = Path(child.path)
                        total += sizes.get(child_path, 0)
            except OSError:
                pass
            sizes[path] = total
            continue

        try:
            metadata = path.stat(follow_symlinks=options.follow_symlinks)
        except OSError as error:
            warnings.append(f"{path}: {error.strerror or error}")
            sizes[path] = 0
            continue
        if options.same_filesystem and metadata.st_dev != root_device:
            sizes[path] = 0
            continue
        directory = stat.S_ISDIR(metadata.st_mode)
        is_dirs[path] = directory
        mtimes[path] = metadata.st_mtime
        scanned_items += 1

        if not directory:
            inode = (metadata.st_dev, metadata.st_ino)
            if inode in seen_files:
                size = 0
            else:
                seen_files.add(inode)
                size = metadata.st_size if options.apparent_size else getattr(metadata, "st_blocks", 0) * 512
                if size == 0 and metadata.st_size:
                    size = metadata.st_size
            sizes[path] = max(0, size)
            scanned_bytes += sizes[path]
        else:
            sizes[path] = 0
            stack.append((path, True))
            if options.max_depth is None or depth < options.max_depth:
                try:
                    with os.scandir(path) as iterator:
                        children = list(iterator)
                except OSError as error:
                    warnings.append(f"{path}: {error.strerror or error}")
                    children = []
                for child in reversed(children):
                    child_path = Path(child.path)
                    if not options.include_hidden and child.name.startswith("."):
                        continue
                    if _matches_exclude(child_path, root, options.excludes):
                        continue
                    if child.is_symlink() and not options.follow_symlinks:
                        try:
                            link_stat = child.stat(follow_symlinks=False)
                        except OSError:
                            continue
                        is_dirs[child_path] = False
                        mtimes[child_path] = link_stat.st_mtime
                        sizes[child_path] = link_stat.st_size
                        depths[child_path] = depth + 1
                        continue
                    depths[child_path] = depth + 1
                    stack.append((child_path, False))

        if progress and scanned_items % 250 == 0:
            progress(scanned_items, scanned_bytes)

    total_size = sizes.get(root, 0)
    entries = [
        DiskEntry(
            path=path,
            size=size,
            is_dir=is_dirs.get(path, False),
            depth=depths.get(path, 0),
            modified=mtimes.get(path),
        )
        for path, size in sizes.items()
        if path != root
    ]
    entries = _filter_and_limit(entries, options)
    if progress:
        progress(scanned_items, total_size)
    return ScanResult(
        root=root,
        entries=entries,
        total_size=total_size,
        backend="native",
        duration=time.monotonic() - started,
        scanned_items=scanned_items,
        warnings=warnings[:100],
    )


async def scan(
    options: ScanOptions,
    cancel_event: threading.Event,
    progress: ProgressCallback | None = None,
    process_hook: Callable[[asyncio.subprocess.Process | None], None] | None = None,
) -> ScanResult:
    if options.backend in {"auto", "dust"}:
        try:
            return await scan_with_dust(options, cancel_event, process_hook)
        except DustUnavailable:
            if options.backend == "dust":
                raise
        except (ValueError, json.JSONDecodeError) as error:
            if options.backend == "dust":
                raise RuntimeError(f"Could not parse dust JSON output: {error}") from error
    loop = asyncio.get_running_loop()
    with ThreadPoolExecutor(max_workers=1, thread_name_prefix="dust-tui-native") as executor:
        return await loop.run_in_executor(executor, scan_native, options, cancel_event, progress)

from pathlib import Path
from threading import Event

from dust_tui.backends import build_dust_command, parse_dust_json, scan_native
from dust_tui.models import ScanOptions


def test_parse_nested_dust_schema(tmp_path: Path) -> None:
    root = tmp_path.resolve()
    child = root / "large.bin"
    child.write_bytes(b"x" * 10)
    raw = {
        "name": str(root),
        "size": 10,
        "children": [{"name": str(child), "size": 10}],
    }
    entries, total = parse_dust_json(raw, root)
    assert total == 10
    assert len(entries) == 1
    assert entries[0].path == child
    assert entries[0].size == 10


def test_parse_flat_dust_schema(tmp_path: Path) -> None:
    root = tmp_path.resolve()
    raw = [
        {"path": str(root), "bytes": 30, "type": "directory"},
        {"path": str(root / "a"), "bytes": 20, "type": "file"},
    ]
    entries, total = parse_dust_json(raw, root)
    assert total == 30
    assert entries[0].size == 20


def test_build_dust_command() -> None:
    options = ScanOptions(
        root=Path("/tmp"),
        max_depth=3,
        max_entries=250,
        mode="files",
        min_size=1000,
        same_filesystem=True,
        excludes=(".git",),
    )
    command = build_dust_command("dust", options)
    assert command[:4] == ["dust", "-j", "-p", "-P"]
    assert "-F" in command
    assert "-x" in command
    assert command[-1] == "/tmp"


def test_build_dust_command_with_all_supported_flags() -> None:
    options = ScanOptions(
        root=Path("/tmp"),
        max_depth=None,
        max_entries=100,
        include_hidden=True,
        follow_symlinks=True,
        apparent_size=True,
        same_filesystem=True,
        excludes=("target",),
        collapse=("vendor",),
        dust_config_file="/tmp/dust.toml",
        dust_threads=4,
        dust_reverse=True,
        dust_no_colors=True,
        dust_force_colors=True,
        dust_no_percent_bars=True,
        dust_bars_on_right=True,
        dust_screen_reader=True,
        dust_skip_total=True,
        dust_filecount=True,
        dust_file_types="rs,py",
        dust_terminal_width=120,
        dust_print_errors=True,
        dust_output_format="gb",
        dust_stack_size=1048576,
        dust_mtime="+7",
        dust_atime="-3",
        dust_ctime="1",
        dust_filter="src",
        dust_invert_filter="\\.git",
        dust_ignore_file="/tmp/ignore.txt",
        dust_files_from="/tmp/files.txt",
        dust_files0_from="/tmp/files0.txt",
    )
    command = build_dust_command("dust", options)
    assert command[:3] == ["dust", "--config", "/tmp/dust.toml"]
    for flag in (
        "-j",
        "-p",
        "-P",
        "-L",
        "-s",
        "-x",
        "-r",
        "-c",
        "-C",
        "-b",
        "-B",
        "-R",
        "--skip-total",
        "-f",
        "--print-errors",
    ):
        assert flag in command
    assert "-i" not in command
    assert command[command.index("-T") + 1] == "4"
    assert command[command.index("-t") + 1] == "rs,py"
    assert command[command.index("-w") + 1] == "120"
    assert command[command.index("-o") + 1] == "gb"
    assert command[command.index("-S") + 1] == "1048576"
    assert command[command.index("-M") + 1] == "+7"
    assert command[command.index("-A") + 1] == "-3"
    assert command[command.index("-y") + 1] == "1"
    assert command[command.index("-e") + 1] == "src"
    assert command[command.index("-v") + 1] == "\\.git"
    assert command[command.index("-I") + 1] == "/tmp/ignore.txt"
    assert command[command.index("--files-from") + 1] == "/tmp/files.txt"
    assert command[command.index("--files0-from") + 1] == "/tmp/files0.txt"
    assert "--collapse=vendor" in command
    assert command[-1] == "/tmp"


def test_native_scan(tmp_path: Path) -> None:
    (tmp_path / "one.bin").write_bytes(b"x" * 512)
    folder = tmp_path / "folder"
    folder.mkdir()
    (folder / "two.bin").write_bytes(b"x" * 1024)
    result = scan_native(
        ScanOptions(root=tmp_path, backend="native", max_depth=4, apparent_size=True),
        Event(),
    )
    assert result.backend == "native"
    assert result.total_size >= 1536
    assert any(entry.path.name == "two.bin" for entry in result.entries)

from __future__ import annotations

import argparse
from pathlib import Path

from . import __version__
from .app import DustTuiApp


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Textual interface for the dust disk-space analyzer")
    parser.add_argument("path", nargs="?", type=Path, help="directory to scan")
    parser.add_argument("--no-scan", action="store_true", help="open without starting an initial scan")
    parser.add_argument("--version", action="version", version=f"dust-tui {__version__}")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    app = DustTuiApp(initial_path=args.path, auto_scan=not args.no_scan)
    app.run()


if __name__ == "__main__":
    main()

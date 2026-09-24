#!/usr/bin/env python3
from __future__ import annotations

import py_compile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = [
    ROOT / "scripts/validate_ov14_contract.py",
    ROOT / "scripts/validate_ov13_cleaning_performance_termux_hotfix.py",
]
failures: list[str] = []
for path in checks:
    if not path.is_file():
        failures.append(f"missing {path.relative_to(ROOT)}")
        continue
    try:
        py_compile.compile(str(path), doraise=True)
    except py_compile.PyCompileError as exc:
        failures.append(f"syntax error in {path.relative_to(ROOT)}: {exc.msg}")

ov14 = (ROOT / "scripts/validate_ov14_contract.py").read_text(encoding="utf-8")
perf = (ROOT / "scripts/validate_ov13_cleaning_performance_termux_hotfix.py").read_text(encoding="utf-8")
if 'wrapper.get("schema") == 1' not in ov14:
    failures.append("OV14 validator no longer checks wrapper schema acceptance")
if 'System.getenv("HOME")' not in perf or 'System.getenv("PREFIX")' not in perf:
    failures.append("performance validator no longer checks Termux env markers")
if 'unsupported Devtool jobs schema must not be added' in perf:
    failures.append("stale unsupported-jobs rejection still present")

if failures:
    for failure in failures:
        print(f"OV14 validator syntax hotfix failure: {failure}")
    raise SystemExit(1)

print("OV14 validator syntax hotfix PASS")

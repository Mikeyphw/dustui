#!/usr/bin/env python3
"""Validate AUD-FIX-07 Go host cutover without orchestrating the Devtool DAG."""
from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    p = ROOT / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def host_reachability(failures: list[str]) -> None:
    cfg = tomllib.loads(read(".devtool.toml"))
    require("project" not in cfg and "python" not in cfg, "root Python project/default declaration remains", failures)
    require(not (ROOT / "pyproject.toml").exists(), "root pyproject.toml still exposes Python/Textual production packaging", failures)
    require(not any(p.is_file() for p in (ROOT / "src/dust_tui").glob("**/*")), "root src/dust_tui remains production reachable", failures)
    state = json.loads(read("migration/qualification/v1/current_state.json"))
    retired = state.get("ov14", {}).get("status") == "physically-retired"
    live_oracle = ROOT / "migration/oracle/python_textual"
    archived_oracle = ROOT / "migration/archive/oracles/aud_fix_16_python_textual"
    if retired:
        require(not live_oracle.exists(), "live Python/Textual oracle remains after AUD-FIX-16", failures)
        require((archived_oracle / "src/dust_tui").is_dir(), "retired Python/Textual archive missing", failures)
        require("non-authoritative" in read("migration/archive/oracles/aud_fix_16_python_textual/AUD_FIX_16_ARCHIVE.md").lower(), "retired oracle archive lacks non-authority marker", failures)
    else:
        require((live_oracle / "pyproject.toml").is_file() and (live_oracle / "src/dust_tui").is_dir(), "isolated Python/Textual parity oracle missing before retirement", failures)

    host = read("go/internal/host/host.go")
    for command in ["scan", "query", "search", "activity", "cleanup-review", "cleanup-execute", "diagnostics", "support-export", "inspect", "tui"]:
        require(f'"{command}"' in host, f"Go host command missing: {command}", failures)
    for rel in ["go/cmd/dust-tui/main.go", "go/cmd/dust-space/main.go"]:
        require("host.RunAlias" in read(rel), f"compatibility alias is not Go-backed: {rel}", failures)
    require("host.Run" in read("go/cmd/dustui-go/main.go"), "dustui-go does not dispatch production host commands", failures)
    tui = read("go/internal/tui/tui.go")
    require("func Run(" in tui and "Executor" in tui and "ForwardedCount" in tui, "Go TUI remains Render/Smoke-only scaffolding", failures)
    require("python3 -m dust_tui" not in read("scripts/run-dev.sh"), "development launcher still selects Python/Textual", failures)
    require("pip install" not in read("scripts/install.sh"), "root installer still packages Python/Textual host", failures)
    require("go build" in read("scripts/install.sh"), "root installer does not build Go host commands", failures)

    android = cfg.get("targets", {}).get("dustui_android", {})
    go = cfg.get("targets", {}).get("dustui_go", {})
    require("aud_fix_07" in android.get("workflows", {}), "Android federation workflow aud_fix_07 missing", failures)
    require("aud_fix_07" in go.get("workflows", {}), "Go workflow aud_fix_07 missing", failures)
    for job in ["audfix07_host_reachability", "audfix07_product_identity"]:
        require(job in android.get("jobs", {}), f"AUD-FIX-07 leaf job missing: {job}", failures)
    for job in ["audfix07_cli_contract", "audfix07_tui_contract", "audfix07_build"]:
        require(job in go.get("jobs", {}), f"AUD-FIX-07 Go job missing: {job}", failures)

def product_identity(failures: list[str]) -> None:
    cfg = tomllib.loads(read(".devtool.toml"))
    gm = cfg.get("targets", {}).get("dustui_go", {}).get("metadata", {})
    require(gm.get("target_production_authority") is True, "Go target is not declared production host authority", failures)
    require(gm.get("target_product_default") is True and gm.get("target_go_cli_default") is True and gm.get("target_go_tui_default") is True, "Go host default declarations missing", failures)
    require(gm.get("host_authority_default") is True, "host_authority_default must be true", failures)
    require(gm.get("legacy_python_textual_production") is False, "legacy Python/Textual production declaration must be false", failures)

    state = json.loads(read("migration/qualification/v1/current_state.json"))
    gate = state.get("gate_d", {})
    obs = state.get("observed_repository", {})
    require(gate.get("status") in {"open", "qualified"}, "canonical Gate D status invalid", failures)
    require(obs.get("legacy_python_textual_present") is False, "canonical observation still sees Python/Textual production host", failures)
    if state.get("ov14", {}).get("status") == "physically-retired":
        require(obs.get("python_textual_oracle_present") is False, "canonical observation still sees live parity oracle after retirement", failures)
    require("migration/oracle/python_textual" not in read(".devtool.toml"), "Devtool still declares the retired live oracle path", failures)

    generated = read("go/internal/qualification/state_generated.go")
    require("const LegacyPythonTextualPresent = false" in generated, "generated production-host observation is stale", failures)
    product = read("go/internal/product/product.go")
    readiness = read("go/internal/readiness/readiness.go")
    require("HostAuthorityDefault:          true" in product, "product contract does not declare reachable Go host default", failures)
    require("LegacyPythonTextualProduction: false" in product, "product contract still exposes Python/Textual production", failures)
    require("ProductDefault: qualification.GateDQualified" in product, "Android surface must remain Gate-D-controlled", failures)
    require("ProductionEngine:        true" in readiness and "GoCLIDefault:            true" in readiness and "GoTUIDefault:            true" in readiness, "readiness does not reflect Go host cutover", failures)

def validate(check: str) -> list[str]:
    failures: list[str] = []
    if check in ("all", "host-reachability"):
        host_reachability(failures)
    if check in ("all", "product-identity"):
        product_identity(failures)
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", choices=["all", "host-reachability", "product-identity"], default="all")
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(args.check)
    if args.audit_loop and not failures:
        second = validate(args.check)
        if second:
            failures.extend([f"second-pass:{x}" for x in second])
    if failures:
        for item in failures:
            print(f"AUD-FIX-07 contract failure: {item}", file=sys.stderr)
        return 1
    print(f"DustUI AUD-FIX-07 contract PASS ({args.check}; canonical lifecycle preserved)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

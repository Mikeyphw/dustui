#!/usr/bin/env python3
"""Successor-aware OV14 milestone truth validator; static/evidence-only."""
from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load(rel: str) -> dict:
    path = ROOT / rel
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def validate() -> list[str]:
    failures: list[str] = []

    def req(ok: bool, msg: str) -> None:
        if not ok:
            failures.append(msg)

    state = load("migration/qualification/v1/current_state.json")
    acceptance = load("migration/acceptance/v1/current_state.json")
    ledger = load("migration/ledger/ov14_legacy_retirement_status.json")
    retirement = load("migration/qualification/v1/ov14_retirement.json")
    cfg = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    flows = cfg["targets"]["dustui_android"].get("workflows", {})

    gate_d = state.get("gate_d", {})
    ov14 = state.get("ov14", {})
    gate_e = state.get("gate_e", {})
    gate_status = gate_d.get("status")
    req(gate_status in {"open", "qualified"}, "canonical Gate-D status is invalid")
    req(ledger.get("qualification_state_source") == "migration/qualification/v1/current_state.json", "OV14 ledger not bound to canonical qualification state")
    req("gate_d_truth" not in ledger and "ov15_status" not in ledger, "OV14 ledger independently declares gate truth")
    req(acceptance.get("candidate", {}).get("acceptance_converged") is True, "successor acceptance must remain converged")
    req("job:retirement_gate" not in json.dumps(flows.get("gate_d")), "Gate D must not depend on post-Gate-D physical retirement")

    if gate_status == "open":
        req(ledger.get("status") == "blocked_on_gate_d", "OV14 must remain blocked while Gate D is open")
    elif gate_status == "qualified":
        physically_retired = ov14.get("status") == "physically-retired"
        if physically_retired:
            req(ledger.get("status") == "retirement_conditions_observed", "OV14 ledger must observe completed retirement conditions")
            req(not ledger.get("blockers"), "OV14 retirement ledger still has blockers")
            req(retirement.get("status") == "pass" and retirement.get("gate_d_resealed") is False, "AUD-FIX-16 retirement evidence missing or reseals Gate D")
            req(retirement.get("gate_d_ancestor_source_sha256") == gate_d.get("passing_source_tree_sha256"), "OV14 retirement evidence drifted from the qualified Gate-D ancestor")
            req(gate_e.get("status") == "eligible-for-qualification", "Gate E must be eligible after physical retirement")
            req("aud_fix_16" in flows, "AUD-FIX-16 successor workflow missing")
        else:
            req(ledger.get("status") == "ready_for_retirement_work", "qualified Gate D must expose OV14 as ready for retirement work")

    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-loop", action="store_true")
    args = ap.parse_args()
    passes = 2 if args.audit_loop else 1
    for run in range(1, passes + 1):
        failures = validate()
        print(f"OV14 successor contract pass {run}: failures={len(failures)}")
        for item in failures:
            print("OV14 contract failure: " + item, file=sys.stderr)
        if failures:
            return 1
    print("OV14 successor-aware milestone truth PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

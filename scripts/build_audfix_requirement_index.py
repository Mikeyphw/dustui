#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "migration/requirements/v2/source"
OUT = ROOT / "migration/requirements/v2/requirements.json"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def extract_h2_block(text: str, heading_pattern: str) -> tuple[str, str]:
    pattern = re.compile(rf"(?m)^##\s+({heading_pattern})\s*$")
    m = pattern.search(text)
    if not m:
        raise ValueError(f"missing heading pattern: {heading_pattern}")
    start = m.start()
    next_h2 = re.search(r"(?m)^##\s+", text[m.end():])
    end = m.end() + next_h2.start() if next_h2 else len(text)
    block = text[start:end].strip()
    return m.group(1).strip(), block


def extract_heading_block(text: str, heading: str) -> str:
    # Supports level-2/3 exact heading text and stops at same-or-higher level.
    lines = text.splitlines()
    for i, line in enumerate(lines):
        m = re.match(r"^(#{2,3})\s+(.+?)\s*$", line)
        if not m or m.group(2) != heading:
            continue
        level = len(m.group(1))
        out = [line]
        for line2 in lines[i + 1:]:
            m2 = re.match(r"^(#{2,3})\s+(.+?)\s*$", line2)
            if m2 and len(m2.group(1)) <= level:
                break
            out.append(line2)
        return "\n".join(out).strip()
    raise ValueError(f"missing heading: {heading}")


def evidence_kind_for_gap(gap_id: str, area: str) -> list[str]:
    area_l = area.lower()
    kinds = ["source-reachability", "behavioral-test"]
    if "gate" in area_l or "evidence" in area_l or gap_id in {"AUD-001", "AUD-021", "AUD-022", "AUD-023"}:
        kinds.append("machine-readable-evidence")
    if "ui" in area_l or gap_id.startswith("UIX-"):
        kinds.append("ui-contract")
    if "devtool" in area_l or "release" in area_l:
        kinds.append("devtool-execution")
    return sorted(set(kinds))


def main() -> int:
    gaps_doc = json.loads((SRC / "FULL_GAP_LEDGER_READ_ONLY.json").read_text())
    audfix = json.loads((SRC / "AUD_FIX_REMEDIATION_ROADMAP.json").read_text())
    original_text = (SRC / "ORIGINAL_ROADMAP_RECHECKED_EXO.md").read_text()
    amendment_text = (SRC / "ANDROID_SUSPENSION_AMENDMENT.md").read_text()
    uiux_text = (SRC / "AUTHORITATIVE_GO_UIUX_RECHECKED.md").read_text()

    coverage = audfix["gap_coverage"]
    reqs: list[dict] = []

    for gap in gaps_doc["gaps"]:
        gid = gap["id"]
        owners = coverage.get(gid, [])
        reqs.append({
            "id": f"GAP:{gid}",
            "class": "audit-gap",
            "title": gap.get("area", gid),
            "authority": "converged-read-only-audit",
            "owner": owners,
            "supersession": "not-superseded",
            "evidence_kind": evidence_kind_for_gap(gid, gap.get("area", "")),
            "closure_criteria": gap.get("future_target", "Close the audited root cause with behavioral/evidence proof."),
            "finding": gap.get("finding", ""),
            "roadmap_reference": gap.get("roadmap", ""),
            "fresh_status": gap.get("fresh_status"),
            "wip_status": gap.get("wip_status"),
        })

    for n in range(1, 16):
        prefix = f"OV{n:02d} — .*"
        title, block = extract_h2_block(original_text, prefix)
        reqs.append({
            "id": f"ORIG:OV{n:02d}",
            "class": "original-roadmap-overlay",
            "title": title,
            "authority": "original-roadmap-rechecked-exo",
            "owner": [f"ORIG-OV{n:02d}"],
            "supersession": (
                "android-buildability-suspended-only; semantic/safety/evidence promises remain" if 3 <= n <= 12
                else "not-superseded"
            ),
            "evidence_kind": ["behavioral-test", "source-reachability", "devtool-execution"],
            "closure_criteria": "All Required implementation/Acceptance/Gate/Validation clauses in the embedded source block are satisfied, except only the explicit amendment overrides.",
            "source_block": block,
        })

    amendment_heading = "2.3a Android buildability suspension policy"
    reqs.append({
        "id": "AMEND:ANDROID-SUSPENSION-OV03-OV12",
        "class": "roadmap-amendment",
        "title": amendment_heading,
        "authority": "android-suspension-amendment",
        "owner": ["AUD-FIX-01", "AUD-FIX-14", "AUD-FIX-15"],
        "supersession": "overrides only Android buildability/Gradle validation expectations for OV03-OV12; ends at OV13",
        "evidence_kind": ["machine-readable-evidence", "devtool-execution"],
        "closure_criteria": "No audit may count Android build failure alone as an OV03-OV12 gap; OV13/Gate D and later Android evidence remains mandatory; Gate A/B/C canonical EXO evidence remains required.",
        "source_block": extract_heading_block(amendment_text, amendment_heading),
    })

    ui_sections = {
        "UIUX:5.1": ("5.1 Borrow from BKX", ["AUD-FIX-08", "AUD-FIX-09"]),
        "UIUX:5.2": ("5.2 Preserve DustUI identity", ["AUD-FIX-10", "AUD-FIX-12"]),
        "UIUX:6.1": ("6.1 Recommended product areas", ["AUD-FIX-08", "AUD-FIX-10", "AUD-FIX-11"]),
        "UIUX:6.2": ("6.2 Scan is a workflow, not a navigation destination", ["AUD-FIX-10"]),
        "UIUX:6.3": ("6.3 Historical destinations absorbed by the new hierarchy", ["AUD-FIX-08", "AUD-FIX-13", "AUD-FIX-16"]),
        "UIUX:7.1": ("7.1 Compact phones", ["AUD-FIX-12"]),
        "UIUX:7.2": ("7.2 Large phones / tablets", ["AUD-FIX-12"]),
        "UIUX:7.3": ("7.3 Landscape / large tablet", ["AUD-FIX-12"]),
        "UIUX:10.1": ("10.1 Shared primitives", ["AUD-FIX-09", "AUD-FIX-12"]),
        "UIUX:10.2": ("10.2 State color semantics", ["AUD-FIX-09", "AUD-FIX-12"]),
        "UIUX:10.3": ("10.3 Information density", ["AUD-FIX-12"]),
        "UIUX:11.1": ("11.1 First launch / no trusted scan", ["AUD-FIX-10"]),
        "UIUX:11.2": ("11.2 Scan flow", ["AUD-FIX-10"]),
        "UIUX:11.3": ("11.3 Files flow", ["AUD-FIX-08", "AUD-FIX-09"]),
        "UIUX:11.4": ("11.4 Cleanup flow", ["AUD-FIX-09", "AUD-FIX-10"]),
        "UIUX:11.5": ("11.5 Duplicates", ["AUD-FIX-02", "AUD-FIX-10"]),
        "UIUX:11.6": ("11.6 App residue", ["AUD-FIX-03", "AUD-FIX-10"]),
        "UIUX:11.7": ("11.7 App storage", ["AUD-FIX-03", "AUD-FIX-10"]),
        "UIUX:11.8": ("11.8 Activity", ["AUD-FIX-04", "AUD-FIX-11"]),
        "UIUX:11.9": ("11.9 System / Diagnostics", ["AUD-FIX-05", "AUD-FIX-11"]),
        "UIUX:14.1": ("14.1 Structurally absorbed", ["AUD-FIX-13", "AUD-FIX-16"]),
        "UIUX:14.2": ("14.2 Still mandatory in the new UI", ["AUD-FIX-08", "AUD-FIX-09", "AUD-FIX-10", "AUD-FIX-11", "AUD-FIX-12"]),
        "UIUX:GO16": ("GO16 — Android semantic/UI cutover", ["AUD-FIX-08", "AUD-FIX-09", "AUD-FIX-10", "AUD-FIX-11", "AUD-FIX-12", "AUD-FIX-15"]),
        "UIUX:GO17": ("GO17 — Retirement purge", ["AUD-FIX-16"]),
        "UIUX:GO18": ("GO18 — Adversarial qualification", ["AUD-FIX-17"]),
        "UIUX:GO19": ("GO19 — Final migration + UX release seal", ["AUD-FIX-17"]),
    }
    for rid, (heading, owners) in ui_sections.items():
        reqs.append({
            "id": rid,
            "class": "uiux-authority",
            "title": heading,
            "authority": "authoritative-go-uiux-rechecked",
            "owner": owners,
            "supersession": "not-superseded",
            "evidence_kind": ["ui-contract", "behavioral-test", "accessibility", "device-evidence"],
            "closure_criteria": "Every normative requirement in the embedded source block is implemented and proven by behavioral/device evidence; source-token absence alone is insufficient.",
            "source_block": extract_heading_block(uiux_text, heading),
        })

    index = {
        "schema": "dustui.requirement-index.v2",
        "campaign": "AUD-FIX",
        "generated_by": "scripts/build_audfix_requirement_index.py",
        "authority_order": [
            "AUTHORITATIVE_GO_UIUX_RECHECKED.md",
            "ANDROID_SUSPENSION_AMENDMENT.md where it explicitly overrides the original roadmap",
            "ORIGINAL_ROADMAP_RECHECKED_EXO.md for promises not superseded",
            "FULL_GAP_LEDGER_READ_ONLY.json",
            "AUD_FIX_REMEDIATION_ROADMAP.json for remediation ownership/sequencing",
        ],
        "source_sha256": {p.name: sha(p) for p in sorted(SRC.iterdir()) if p.is_file() and p.name != "SHA256SUMS"},
        "requirements": reqs,
        "counts": {
            "total": len(reqs),
            "gap_families": sum(r["class"] == "audit-gap" for r in reqs),
            "original_overlays": sum(r["class"] == "original-roadmap-overlay" for r in reqs),
            "amendments": sum(r["class"] == "roadmap-amendment" for r in reqs),
            "uiux_sections": sum(r["class"] == "uiux-authority" for r in reqs),
        },
    }
    if index["counts"]["gap_families"] != 33:
        raise SystemExit(f"expected 33 gaps, got {index['counts']['gap_families']}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(index, indent=2, sort_keys=True) + "\n")
    print(f"wrote {OUT.relative_to(ROOT)} ({len(reqs)} requirements; 33 gap families)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

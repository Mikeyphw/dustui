from __future__ import annotations

import json
import sys
import tomllib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "scripts"))

from audfix01_core import REQUIRED_GATE_E_NODES
from audfix16_retirement import included
from audfix17_identity import (
    POLICY_REVALIDATION_NODES,
    REUSABLE_EXECUTION_NODES,
    node_fingerprint,
)


class GateEReuseV2(unittest.TestCase):
    def test_node_partition_covers_gate_e(self):
        self.assertFalse(set(REUSABLE_EXECUTION_NODES) & set(POLICY_REVALIDATION_NODES))
        self.assertEqual(
            set(REQUIRED_GATE_E_NODES),
            set(REUSABLE_EXECUTION_NODES) | set(POLICY_REVALIDATION_NODES),
        )

    def test_derived_gate_e_evidence_is_outside_source_projection(self):
        self.assertFalse(included("migration/qualification/v1/gate_e_evidence.json"))
        self.assertFalse(included("migration/qualification/v1/gate_e_bundle.json"))
        self.assertFalse(included("migration/qualification/v1/current_state.json"))

    def test_reusable_fingerprint_is_deterministic(self):
        first = node_fingerprint("HEAD", "canonical_devtool_release")
        second = node_fingerprint("HEAD", "canonical_devtool_release")
        self.assertEqual(first["fingerprint_sha256"], second["fingerprint_sha256"])
        self.assertEqual(64, len(first["fingerprint_sha256"]))
        self.assertGreater(first["tracked_file_count"], 0)

    def test_devtool_exposes_plan_and_reconcile(self):
        cfg = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
        android = cfg["targets"]["dustui_android"]
        wrapper = cfg["wrapper"]["commands"]
        self.assertIn("audfix17_reuse_plan", android["jobs"])
        self.assertIn("audfix17_reconcile", android["jobs"])
        self.assertIn("gate_e_reuse_plan", android["workflows"])
        self.assertIn("gate_e_reconcile", android["workflows"])
        self.assertEqual("gate_e_reuse_plan", wrapper["gate-e-plan"]["workflow"])
        self.assertEqual("gate_e_reconcile", wrapper["gate-e-reconcile"]["workflow"])

    def test_topology_declares_projection_identity(self):
        topology = json.loads((ROOT / "migration/qualification/v1/gate_e_topology.json").read_text())
        self.assertEqual("dustui.gate-e.topology.v2", topology["schema"])
        policy = topology["reuse_policy"]
        self.assertEqual("source_projection_sha256", policy["qualification_identity"])
        self.assertEqual("provenance-not-validity", policy["git_identity_role"])
        self.assertEqual("Devtool", policy["generic_execution_cache_owner"])

    def test_finalizer_no_longer_requires_matching_git_head(self):
        body = (ROOT / "scripts/audfix17_gate_e.py").read_text(encoding="utf-8")
        self.assertIn("dustui.gate-e.evidence-registry.v2", body)
        self.assertIn("provenance-not-validity", body)
        self.assertNotIn("evidence is bound to a different Git HEAD", body)

    def test_successor_reconcile_clears_stale_derived_evidence_before_policy(self):
        body = (ROOT / "scripts/audfix17_reuse.py").read_text(encoding="utf-8")
        clear_at = body.index("clear_stale_live_derived_evidence()", body.index("def reconcile"))
        refresh_at = body.index("refresh_retirement()", body.index("def reconcile"))
        self.assertLess(clear_at, refresh_at)
        self.assertIn("for path in (EVIDENCE, BUNDLE)", body)


if __name__ == "__main__":
    unittest.main()

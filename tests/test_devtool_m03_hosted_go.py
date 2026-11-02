"""M03 source-only native Go DAG tests, with a mocked Devtool compiler (no Gradle)."""
from __future__ import annotations
import copy
from pathlib import Path
import sys
import tomllib
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import dustui_m03_hosted as gate
from devtool_actions_contract import check_document, ContractError


def fake_graph():
    jobs = []
    for i, name in enumerate(gate.JOB_ORDER, 1):
        jobs.append({"id": f"node-{i:02d}", "job": name, "provider": "go",
                     "profile_id": gate.PROFILES, "go_command": gate.COMMANDS[name],
                     "outputs": [], "go_manifest": "go/go.mod", "race": name == "race",
                     "go_env": {"GOOS":"linux","GOARCH":"amd64","CGO_ENABLED":"1"} if name == "race" else {},
                     "needs": ["node-01", "node-02", "node-03"] if name == "build" else []})
    return {"target": gate.TARGET, "workflow": gate.WORKFLOW,
            "runtime_sha": gate.DEVTOOL_SHA, "jobs": jobs, "graph_sha256": "a" * 64}


class M03Contract(unittest.TestCase):
    def test_expected_go_jobs(self):
        config = tomllib.loads((ROOT / ".devtool.toml").read_text())
        self.assertEqual(check_document(config, ROOT)["stage"], "M03")

    def test_expected_native_graph(self):
        self.assertEqual(gate.assert_graph(fake_graph()),
                         {name: f"node-{i:02d}" for i, name in enumerate(gate.JOB_ORDER, 1)})

    def test_refuses_wrong_native_node_or_provider(self):
        graph = fake_graph()
        graph["jobs"][2]["provider"] = "command"
        with self.assertRaises(gate.HostedGoError):
            gate.assert_graph(graph)
        graph = fake_graph()
        graph["jobs"][3]["go_command"] = ["sh", "-lc", "go build ./..."]
        with self.assertRaises(gate.HostedGoError):
            gate.assert_graph(graph)

    def test_refuses_dropping_race_predecessor(self):
        graph = fake_graph()
        graph["jobs"][3]["needs"].remove("node-03")
        with self.assertRaises(gate.HostedGoError):
            gate.assert_graph(graph)

    def test_refuses_wrong_race_environment(self):
        graph = fake_graph()
        graph["jobs"][2]["go_env"]["CGO_ENABLED"] = "0"
        with self.assertRaises(gate.HostedGoError):
            gate.assert_graph(graph)

    def test_runner_refuses_off_host_without_invoking_devtool(self):
        with patch.dict("os.environ", {"GITHUB_ACTIONS":"", "GITHUB_SHA":""}):
            self.assertEqual(gate.main(["run", "--node", "test", "--root", str(ROOT),
                                        "--inputs", "/tmp/m03-none", "--outputs", "/tmp/m03-none"]), 2)

    def test_refuses_unsafe_hosted_command(self):
        config = tomllib.loads((ROOT / ".devtool.toml").read_text())
        mutant = copy.deepcopy(config)
        mutant["targets"]["dustui_hosted_go"]["jobs"]["race"]["command"] = ["go", "-C", "go", "test", "./..."]
        with self.assertRaises(ContractError):
            check_document(mutant, ROOT)

    def test_workflow_no_direct_go_or_gradle(self):
        wf = (ROOT / ".github/workflows/devtool-dustui.yml").read_text()
        for name in gate.JOB_ORDER:
            self.assertIn(f"--node {name}", wf)
            self.assertIn(f"  {name}:\n", wf)
        self.assertNotIn("./gradlew", wf)
        self.assertIn('RUNNER_ARCH', (ROOT / "scripts/dustui_m03_hosted.py").read_text())


if __name__ == "__main__":
    unittest.main()

# dustui_go_ov12_diagnostics_evidence_privacy_v1

DustUI OV12 implements Go-owned diagnostics/evidence health, DustProblemGraph, privacy retention, and sealed support bundle contracts under the Android-suspended roadmap.

Android buildability remains intentionally suspended until OV13. The hard local evidence is:

```bash
python3 scripts/validate_ov12_contract.py --audit-loop
cd go && go test ./...
```

No unsupported Devtool target-local jobs/workflows schema is introduced.

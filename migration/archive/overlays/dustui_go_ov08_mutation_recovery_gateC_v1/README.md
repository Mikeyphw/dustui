# dustui_go_ov08_mutation_recovery_gateC_v1

OV08 makes Go the production destructive mutation authority for DustUI while Android buildability remains intentionally suspended until OV13.

Apply:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov08_mutation_recovery_gateC_v1.zip --no-validate --commit "DustUI OV08 mutation recovery Gate C"
```

Evidence:

```bash
python3 scripts/validate_ov08_contract.py --audit-loop
cd go && go test ./...
```

No unsupported Devtool target-local jobs/workflows schema is added.

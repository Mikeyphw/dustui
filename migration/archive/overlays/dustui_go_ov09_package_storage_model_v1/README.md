# dustui_go_ov09_package_storage_model_v1

DustUI OV09 implements package/storage evidence authority under the Android-suspended Go-first roadmap.

Android remains intentionally non-authoritative for build/test/lint until OV13. The thin Android collector contract supplies platform-only facts; Go normalizes, hashes, versions, and decides package-sensitive residue eligibility with action-time re-evaluation.

Hard evidence commands:

```bash
python3 scripts/validate_ov09_contract.py --audit-loop
cd go && go test ./...
```

Apply:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov09_package_storage_model_v1.zip --no-validate --commit "DustUI OV09 package storage model"
```

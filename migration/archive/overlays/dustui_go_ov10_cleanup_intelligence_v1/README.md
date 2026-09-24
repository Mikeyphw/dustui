# dustui_go_ov10_cleanup_intelligence_v1

DustUI OV10 cleanup intelligence overlay.

This non-gate overlay keeps Android buildability intentionally suspended and moves duplicate grouping, residue classification, recommendations, and rules into Go as proof-bearing cleanup intelligence.

Evidence commands:

```bash
python3 scripts/validate_ov10_contract.py --audit-loop
cd go && go test ./...
```

Apply command:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov10_cleanup_intelligence_v1.zip --no-validate --commit "DustUI OV10 cleanup intelligence"
```

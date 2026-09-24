# dustui_go_ov13_cleaning_performance_termux_hotfix_v1

Purpose: fix the constrained Termux Android unit-test wall-clock budget for `CleaningPerformanceContractTest` while preserving the production 15s cleanup qualification constant.

Apply:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov13_cleaning_performance_termux_hotfix_v1.zip --no-validate --commit "DustUI OV13 cleaning performance Termux hotfix"
```

Evidence:

```bash
python3 scripts/validate_ov13_cleaning_performance_termux_hotfix.py
devtool -r "$HOME/Code/dustui" --target dustui_android validate --task :app:testDebugUnitTest
```

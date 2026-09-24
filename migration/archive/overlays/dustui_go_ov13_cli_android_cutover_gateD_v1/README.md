# dustui_go_ov13_cli_android_cutover_gateD_v1

OV13 Gate D reconnect overlay. Android buildability is mandatory again; Go CLI/TUI and Android semantic shell become product-default surfaces backed by Go runtime/state authority.

Evidence commands:

```bash
python3 scripts/validate_ov13_contract.py --audit-loop
cd go && go test ./...
devtool -r "$HOME/Code/dustui" --target dustui_android validate --task verifyDustMigrationContract --task :app:compileDebugSources --task :app:testDebugUnitTest --task :app:lintDebug --task :app:assembleDebug
```

# dustui_go_ov02_go_state_protocol_kernel_v1

DustUI OV02 overlay metadata.

Apply with `--no-validate` and commit:

```text
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay /sdcard/Download/dustui_go_ov02_go_state_protocol_kernel_v1.zip --no-validate --commit "DustUI OV02 Go state protocol kernel"
```

Then run the OV02 non-gate fast evidence commands:

```text
python3 scripts/validate_ov02_contract.py --audit-loop
cd go && go test ./...
cd ..
devtool -r "$HOME/Code/dustui" --target dustui_android test
```

This is not a milestone gate. It establishes the Go state/protocol/SQLite authority model without Android production cutover, scanner replacement, or destructive execution.

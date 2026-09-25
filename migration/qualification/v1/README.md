# DustUI qualification state v1

AUD-FIX successor-aware canonical truth.

`gate_d_evidence.json` is the executed-evidence registry. `current_state.json` and `go/internal/qualification/state_generated.go` are derived by `scripts/refresh_qualification_state.py` and must never be edited as independent gate declarations.

Gate D is computed **only** from required executed evidence and binding coherence. Source-token absence, a retirement ledger, or a product metadata declaration cannot close Gate D. OV14 physical retirement is a separate post-Gate-D state. Gate E remains blocked until both Gate D and OV14 retirement are complete.

# DustUI qualification state v1

AUD-FIX successor-aware canonical truth.

`current_state.json` and the generated Go qualification projections are derived state and must never be edited as independent gate declarations.

Gate D is computed only from required executed evidence and binding coherence in `gate_d_evidence.json` / `gate_d_bundle.json`. It remains immutable qualified ancestor evidence after AUD-FIX-16; source-token absence, retirement ledgers, or product metadata cannot reseal it.

AUD-FIX-16 records the post-Gate-D physical-retirement source projection in `ov14_retirement.json`. Gate E is qualified only when that projection is cross-bound to passing canonical signed release/device/adversarial evidence in `gate_e_evidence.json` and `gate_e_bundle.json`, with final migration/promise and counterexample convergence closed.

Gate E is qualified for the recorded source projection. The temporary Gate-E fast-resume path is not part of the qualified successor authority. Any later source-projection change creates a new candidate: refresh the post-retirement projection as required, commit the intended source tree, run `./devtoolw gate-e-release`, make no source changes, then run `./devtoolw aud-fix-17`.

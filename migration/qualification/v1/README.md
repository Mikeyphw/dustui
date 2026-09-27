# DustUI qualification state v1

AUD-FIX successor-aware canonical truth.

`current_state.json` and the generated Go qualification projections are derived state and must never be edited as independent gate declarations.

Gate D is computed only from required executed evidence and binding coherence in `gate_d_evidence.json` / `gate_d_bundle.json`. It remains immutable qualified ancestor evidence after AUD-FIX-16; source-token absence, retirement ledgers, or product metadata cannot reseal it.

AUD-FIX-16 records the post-Gate-D physical-retirement source projection in `ov14_retirement.json`. Gate E is qualified only when that projection is cross-bound to passing canonical signed release/device/adversarial evidence in `gate_e_evidence.json` and `gate_e_bundle.json`, with final migration/promise and counterexample convergence closed.

Gate E v2 is qualified by `source_projection_sha256`; Git commit/tree IDs are provenance rather than validity. The temporary Gate-E fast-resume path is not part of the qualified successor authority. After a commit, run `./devtoolw gate-e-plan`: an unchanged source projection requires no execution rerun, while `./devtoolw gate-e-reconcile` may compose prior expensive/environment evidence across a changed projection only when each curated node input fingerprint is identical and all policy/qualification nodes rerun successfully. Any changed expensive/environment fingerprint remains fail-closed and requires canonical execution for that material change.

# AUD-FIX-13 pre-Gate-D acceptance convergence contract

Status: implemented; candidate acceptance converged; Gate D remains open.

1. `migration/acceptance/v1/current_state.json` is the sole current pre-Gate-D candidate-acceptance state. Candidate convergence is never equivalent to Gate-D qualification.
2. Every live Android source-reading test is explicitly inventoried. A source-token guard whose behavioral successor already exists is removed from the live test source set, archived as non-authoritative history, and mapped to exact successor evidence.
3. Historical Python validators that require archived predecessor tokens are also removed from live validation and preserved beneath `migration/archive/validators/aud_fix_13_pre_gate_d/`.
4. Live successor validators must consume semantic/evidence contracts and cannot require retired destination names, Debug Workbench product identity, launch-success automation semantics, or other predecessor implementation tokens.
5. The UI/UX acceptance matrix covers every canonical UI/UX requirement, all 14 authoritative user-task outcomes, and visual/product rules VR-01 through VR-10.
6. Android and Go Devtool metadata consume canonical qualification and acceptance state. Go product-default metadata is host CLI/TUI scoped until Gate D qualifies Android reconnect.
7. The `aud_fix_13` workflow owns four read-only acceptance leaves: acceptance inventory, successor validator contract, evidence-derived metadata truth, and pre-Gate-D conformance; Go product metadata is validated through the federated Go target.
8. Gate-D/current acceptance orchestration must not depend on quarantined app-local ADB/APK/release helpers. Their physical retirement remains AUD-FIX-16 work.
9. Gate D must remain `open` / `qualified=false`; AUD-FIX-13 may not manufacture historical Gate A/B/C evidence or complete the Gate-D topology assigned to AUD-FIX-14.
10. Counterexample fixtures must prove detection of unmapped source guards, missing successors, false qualification metadata, quarantined helper authority, incomplete UI/UX coverage, predecessor docs, inaccessible visual data, unsafe destructive actions, and collapsed UI states.

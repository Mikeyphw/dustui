# AUD-FIX-06 — Authority Convergence Gate R1

This is the first full Devtool-native AUD-FIX gate. The authoritative workflow is `dustui_android#authority_gate_r1`, also installed as the target `validate` workflow. It composes `dustui_go#authority_gate_r1`, AUD-FIX-01..05 leaf assertions, migration Gradle contract, Android debug unit tests, lint and assemble, then verifies canonical qualification truth.

The overlay manifest requests normal Devtool validation and intentionally contains **no `validation.tasks`**. EXO run/node evidence is the gate evidence. R1 does not close Gate D.

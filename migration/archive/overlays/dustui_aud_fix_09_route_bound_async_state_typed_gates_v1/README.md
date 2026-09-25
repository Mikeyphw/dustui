# dustui_aud_fix_09_route_bound_async_state_typed_gates_v1

AUD-FIX-09 route-bound async protocol, shared presentation state, and typed Go action-gate cutover.

- Binds every async content response to route key/generation, entity kind/id, request ID, and optional fingerprint.
- Replaces MainViewModel AtomicLong request heuristics with one semantic request registry.
- Makes production routes consume Loading / Empty / Blocked / Error / Content state semantics.
- Moves risky/evidence-dependent policy for cleanup, scan cancellation, export, certification, duplicate analysis, and Activity actions into structured Go gates.
- Adds adversarial stale-response tests and successor-proofs the OV13 route validator for the AUD-FIX-08 nested route tree.
- Gate D remains OPEN. This is a non-gate overlay: apply with `--no-validate`, then run `./devtoolw aud-fix-09`.

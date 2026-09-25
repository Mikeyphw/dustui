# AUD-FIX-14 — historical evidence recovery and Gate-D topology

AUD-FIX-14 is a **preflight/topology** stage. It cannot close Gate D.

## Historical Gate A/B/C rule

Gate A/B/C evidence is valid only when the original Git commit/tree can be identified from immutable fingerprints and the canonical Devtool/EXO workflow is replayed on that exact tree. Current-tree runs must never be relabelled as historical proof. Gate B additionally requires ARM64 device/runtime evidence.

If exact history is absent or ambiguous, the discovery report records `UNRECOVERABLE_HISTORICAL_EVIDENCE` (or the more specific blocking status). That remains a qualification blocker until exact proof is recovered or a separately approved roadmap amendment changes the requirement.

## Gate-D topology

`migration/qualification/v1/gate_d_topology.json` is the topology authority. It binds historical replay plus current-tree requirement conformance; Go test/build/schema/product/CLI/TUI; Android Debug and Release unit/lint/assemble; migration validators; install/launch, route/deep-link/recreation/rotation and accessibility device proof; rooted ARM64 runtime; runtime/APK and Devtool artifact identities; durable operation truth; diagnostics/support sealing; and production reachability.

All non-historical Gate-D nodes must bind to one clean source tree. Historical nodes bind independently to their exact original tree and replay tree. Physical removal of the Python oracle, Room/NDJSON remnants and quarantined helper scripts is AUD-FIX-16 work; Gate D proves they are not production-reachable/default.

## R1 truth

The latest validated AUD-FIX-05/06 Authority Gate R1 attempt failed at `verifyDustMigrationContract` and rolled back. A later force-apply landed the code without validation; it does not turn R1 into a passed gate. AUD-FIX-14 repairs the known Gradle 9.7.1 source-set configuration blocker but does not retroactively change the R1 result.

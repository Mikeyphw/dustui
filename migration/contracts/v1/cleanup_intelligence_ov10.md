# OV10 cleanup intelligence authority

Cleanup intelligence is owned by Go in OV10. Duplicates, residue, recommendations, and rules must produce proof-bearing outputs before any review or capability flow can consume them.

Required invariants:

- duplicate groups use logical-size semantics plus content/identity verification;
- canonical keep selection must retain at least one verified copy;
- duplicate deletion candidates emit `ActionablePathEvidence`;
- residue requires package/user evidence and action-time re-evaluation;
- ambiguity, special-path context, exclusions, and protection state remain visible;
- recommendation and rule outputs preserve provenance;
- imports and regex/pattern execution are bounded;
- plan-level conflict detection runs before authorization;
- protected-descendant and retained-copy constraints are validated before capability creation;
- No specialized result can degrade to a bare path and retain a verified/safe label.

Android buildability remains intentionally suspended through OV12. Android Clean routes are reconnect scaffolding only and must not derive business-policy safety labels locally.

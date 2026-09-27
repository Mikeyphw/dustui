# DustUI known issues — post Gate E

## Release-blocking issues

None are recorded in the qualified Gate-E evidence for the current qualified source projection. A later source-projection change is a new release candidate and is not covered by the previous Gate-E seal.

## Environment / evidence notes

- Device/runtime evidence is environment-specific and must remain bound to the qualifying Devtool run.
- Rooted ARM64 and connected-device instrumentation are required by the final qualification topology.
- Multi-user/work-profile evidence is capability-conditional: when the qualification device cannot provide it, the absence must be explicit and non-fabricated rather than silently reported as executed coverage.
- Gate-D evidence is immutable ancestor evidence; post-Gate-D work must not reseal or rewrite it.

## Review rule

Any newly discovered `blocker`, `p0`, or `p1` issue reopens release readiness for the changed/current candidate and must be resolved before a fresh canonical `./devtoolw gate-e-release` + `./devtoolw aud-fix-17` qualification can be treated as passing.

Historical issue/roadmap notes and retired release scripts are provenance only under `migration/archive/`; they are not current acceptance or release authority.

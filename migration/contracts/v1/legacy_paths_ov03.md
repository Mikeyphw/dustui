# OV03 legacy path reachability

## Still reachable

- Android app source, Room entities/DAO, Kotlin scan repository, root service client, native scanner source, and UI screens remain in the tree.
- These paths are reference/import/reconnect scaffolding only while Android buildability is suspended.
- Room remains a one-way import source from the OV02 contract.

## Newly quarantined by OV03

- Legacy scan results cannot drive migration gate truth.
- Legacy scan IDs cannot determine history comparability.
- Legacy cleanup eligibility cannot bypass Go `ScanAuthority`.
- Legacy Android build/test/lint failure is not a gate failure until OV13 when it matches the suspension contract.

## Not yet removed

- Native scanner source is not deleted in OV03 because OV03 still needs legacy-oracle comparison references.
- Kotlin/Compose source is not rewritten in OV03; reconnect starts at OV13.

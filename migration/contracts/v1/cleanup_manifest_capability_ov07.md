# OV07 cleanup manifest and capability contract

OV07 makes cleanup review proof-bearing before OV08 mutation cutover. Actionable evidence records path, scan/generation, source surface/kind, expected hash/identity, retained-copy constraints, package/user/bucket, protected state, duplicate/rule/recommendation IDs, and deletion eligibility.

The immutable manifest binds exact descendants, path role/protection, mount boundary, identity evidence, duplicate retained-copy constraints, manifest hash/revision, source context hash, and root identity. Adding descendants after review or changing identity evidence invalidates the manifest hash.

Capabilities are durable and one-use. They bind capabilityId, operationId, scanId, scanGeneration, scanContextHash, manifestHash, manifestRevision, rootIdentity, reviewedAt, expiresAt, and claimState. Replay and expired capabilities fail. Lost response after claim requires status/reconcile.

OV07 permits dry-run/preflight only. Production mutation authority remains blocked until OV08.

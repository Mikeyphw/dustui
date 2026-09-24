# OV03 source/token retirement update

OV03 replaces scan-truth source-token checks with behavior-level Go tests for:

- scan identity and options fingerprint;
- logical and allocated size semantics;
- sparse file, hard-link, and symlink policies;
- truncation/incomplete source authority revocation;
- stale-after-cleanup authority revocation;
- history compatibility by semantic dimensions.

Legacy source/token checks around Android build scripts and cleanup execution remain temporary until their replacement overlays. No destructive source-token guard is retired in OV03 because mutation authority is not implemented until OV08.

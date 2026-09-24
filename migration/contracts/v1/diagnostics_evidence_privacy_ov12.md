# OV12 diagnostics, evidence, privacy, and support bundle contract

OV12 replaces Debug as the product truth surface with System → Diagnostics.
Android buildability remains intentionally suspended until OV13.

Required evidence health states are exactly: missing, stale, current, degraded.
A terminal operation state such as COMPLETED is never sufficient to mark the app
healthy. Derived health must invalidate when any authoritative evidence generation
advances, becomes stale, becomes missing, or becomes degraded.

DustProblemGraph requirements:

- stable problem IDs;
- root-cause suppression of dependent symptoms;
- guided remediation;
- remediation risk class;
- significant-problem records separate from raw diagnostic events.

Persistent diagnostics requirements:

- session, operation, and parent IDs;
- area, severity, action, and result fields;
- redaction before persistence;
- bounded values;
- bounded sessions and files;
- no automatic upload.

Privacy-retention matrix requirements cover raw paths, hashes, package names,
root/device metadata, retention, pruning coupling, export redaction, and
backup/device-transfer eligibility.

Support bundles must be sealed and reproducible with a manifest, checksums,
selected evidence generations, explicit privacy mode, and no file-content leakage
by default.

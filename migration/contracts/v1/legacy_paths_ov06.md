# OV06 legacy reachability

Legacy Android root service and cleanup paths may remain physically present while Android buildability is suspended, but they are not the target architecture.

OV06 does not add a new shell console or widen the legacy root surface. The new Go privileged boundary exists as the future root authority and as the validator for OV08 destructive cutover.

Reachable legacy cleanup must remain constrained until OV08. Any new privileged command added outside the Go allowlist is a migration violation.

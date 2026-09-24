# OV09 legacy path reachability

Reachable but non-authoritative:

- old Android package/path helper models;
- historical app residue screens;
- regex-derived package guesses;
- Room-backed app-storage hints imported from older releases.

Unreachable as authority:

- regex-only residue cleanup;
- package-sensitive deletion without Go package/user evidence;
- partial package evidence represented as zero bytes;
- package state captured at review time but not rechecked at action time.

Android buildability remains intentionally suspended until OV13.

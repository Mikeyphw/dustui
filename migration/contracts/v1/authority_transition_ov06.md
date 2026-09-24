# OV06 authority transition

OV06 advances DustUI from Go read/runtime authority to a Go privileged admission boundary.

Authority after OV06:

- scan/read/query/history: Go SQLite authority;
- normal Android runtime/session protocol: Go runtime contract from OV05;
- privileged admission/reconcile: Go root boundary contract from OV06;
- production destructive mutation: still legacy-constrained until OV08;
- Android buildability: intentionally suspended until OV13.

No Room/C++/Kotlin path becomes a new privileged authority in OV06.

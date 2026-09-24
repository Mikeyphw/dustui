# OV05 authority transition — Go read authority and Android runtime contract

OV05 is Gate B under the Android-suspended roadmap.

Authority changes:

- Go SQLite is the authoritative migration source for new scan sessions, scan entries, and scan history.
- Room v3 scan/session/entry data is retained/import-only; no new scan authority may be written by Room after OV05.
- The legacy C++ scanner and Kotlin domain scan writers are no longer migration scan authorities.
- Android production buildability remains intentionally-suspended until OV13; Android code added here is reconnect scaffold only.
- Destructive cleanup remains legacy-constrained until OV08 and is not widened by this overlay.

Gate B proves the read-authority/runtime/shell boundary is coherent enough for the Android shell to reconnect later without reintroducing Room/C++/Kotlin scan authority.

# DustUI OV08 legacy path status

The legacy destructive executor is unreachable. There is no dual-authority mode
and no fallback from Go mutation to Android/Kotlin/root-service deletion.

Allowed legacy state:

- source files may remain on disk for rollback archaeology until OV14;
- shell routes may display Go mutation status when later reconnected;
- read-only imported Room/quarantine history remains retained.

Forbidden legacy state:

- direct deletion without immutable manifest;
- cleanup from bare paths;
- recursive expansion at execution time;
- overwrite fallback for cleanup plan files;
- replay after transport loss or root runtime restart.

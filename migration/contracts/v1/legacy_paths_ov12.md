# OV12 legacy path reachability

Legacy Debug screens, raw log dumps, and `COMPLETED == healthy` heuristics are
not allowed to be authoritative.

Reachable legacy code may remain only as reference or Android reconnect scaffold
while buildability is intentionally suspended. It must not claim health from
terminal operation state alone, must not export raw paths/package names/root
metadata without redaction, and must not upload support data automatically.

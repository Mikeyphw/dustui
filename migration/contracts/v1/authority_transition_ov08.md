# DustUI OV08 authority transition

Go is the only destructive authority after OV08. The Go mutation engine owns the
review -> capability -> privileged admission -> execution -> verification ->
reconcile lifecycle.

Room, Kotlin repositories, RootServiceClient, and DustRootService are no longer
authoritative destructive execution surfaces. They may remain as suspended shell
or archaeology until later purge overlays, but they must not be used as runtime
fallbacks.

Android buildability remains intentionally suspended through OV12 and becomes
mandatory again at OV13.

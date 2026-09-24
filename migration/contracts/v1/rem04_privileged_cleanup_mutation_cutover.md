# REM-04 privileged cleanup/mutation cutover

Production root scanning and destructive mutation are Go-owned. Android RootService is limited to Binder/platform adapters, package visibility, process lifetime, and fixed root-worker invocation. Cleanup review emits an immutable `dustui.cleanup.capability.v1` manifest/capability; execution consumes it through `dustui.cleanup.mutation.v1`, revalidates identities and exact descendants, refuses mount crossings/unreviewed descendants, performs fd-relative mutation, verifies absence, and journals intermediate truth for reconciliation after cancellation or transport/runtime loss.

The legacy JNI bridge, C++ scanner, CMake wiring, and direct destructive fallback are absent from production source.

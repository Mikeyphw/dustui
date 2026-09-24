# OV05 Android Go runtime packaging contract

Production Android runtime ABI is `arm64-v8a`.

The app-owned Go runtime is modeled as `libdustui_go.so` under Android `nativeLibraryDir`. The Kotlin scaffold resolves the fixed executable path from `nativeLibraryDir`, while the Go runtime contract validates the package layout.

`x86_64` is test-only unless a matching Go runtime is packaged in a dedicated test-only variant. The production `abiFilters` list must not advertise x86_64 while only the ARM64 Go runtime contract is valid.

Session requirements:

- fixed executable path;
- readiness handshake;
- bounded request/response frames;
- request IDs;
- monotonic sequence numbers;
- generation binding;
- process death detection;
- reconnect/reconcile instead of replay after ambiguous death.

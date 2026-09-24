# OV05 source/token retirement update

OV05 begins replacement of UI/navigation and Android runtime source-token guards with behavioral contracts:

- ABI production contract is checked by Go runtime package-layout tests and the Android build-file contract.
- Route identity/restoration/deep-link/stale-response invariants are checked by Go shell contract tests.
- Room new scan-write guards are replaced by `state.OV05AuthorityMatrix` behavioral tests.

Legacy Android/UI source-token tests are not mass-retired yet because Android reconnect is deferred to OV13.

# OV13 hotfix: OV01 migration contract repo-root resolution

Gate D restored Android unit tests as mandatory evidence. The legacy OV01 unit
contract test runs under Gradle with `user.dir` set below the repository root on
some Android/Termux executions. A fixed parent path such as `File("..")` can
therefore resolve to `$REPO/android` instead of `$REPO` and produce false
FileNotFound failures like `android/android/app/...`.

This hotfix changes only the test fixture root resolver. It walks upward from
`user.dir` until it finds the DustUI repository markers:

- `.devtool.toml`
- `android/app/src/main`
- `migration/ledger/findings.json`
- `go/internal/readiness/readiness.go`

Production Android code, Go runtime code, scanner authority, cleanup authority,
and Gate D product semantics are unchanged.

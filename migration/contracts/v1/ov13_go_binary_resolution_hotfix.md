# DustUI OV13 Go binary resolution hotfix

OV13 Gate D runs `verifyDustMigrationContract` from Gradle. On Termux, a
Gradle daemon can run with a reduced or stale environment and fail to resolve
`go` even when Go is installed for the interactive shell.

The OV13 validator must not depend on Gradle daemon `PATH` alone. It resolves
Go in this order:

1. `DUSTUI_GO` environment override.
2. `shutil.which("go")` from the current environment.
3. Termux Go at `/data/data/com.termux/files/usr/bin/go`.
4. Common Linux Go paths.

This hotfix does not relax Gate D. It only makes the Go/product contract checks
reachable inside Gradle/Devtool validation. Memory guard interruptions remain a
resource condition, not a contract failure.

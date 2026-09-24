# OV13 OV01 contract repo-root hotfix audit

Observed gate blocker: `:app:testDebugUnitTest` failed only in
`Ov01MigrationContractTest` because the test resolved repository-relative files
from `$REPO/android` or `$REPO/android/app` instead of `$REPO`.

Hotfix scope:

- Replace fixed parent-path root selection with marker-based upward discovery.
- Keep all original OV01 assertions intact.
- Do not weaken Gate D or skip Android unit tests.
- Do not change production runtime behavior.

Expected evidence after applying:

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android validate --task :app:testDebugUnitTest
```

Then rerun the full Gate D validation command.

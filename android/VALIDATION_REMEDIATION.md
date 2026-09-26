# Android validation remediation — final successor authority

> Gate D is qualified and preserved. AUD-FIX-16 physical retirement is complete. AUD-FIX-17 / Gate E is the final qualification boundary.

Do not call raw Gradle/ADB or restore app-local device/release helper scripts. The final Android campaign is owned by Devtool and uses two source-bound runs:

```bash
cd "$HOME/Code/dustui"
export ANDROID_SERIAL=localhost:5555
./devtoolw gate-e-release
./devtoolw aud-fix-17
```

The first command creates canonical signed release evidence through Devtool's Android release orchestrator. The second imports/cross-binds that report and runs the Gate-E adversarial, Debug/Release, connected-device, rooted ARM64, migration/promise, and final seal graph.

Gate-E connected instrumentation intentionally requires `dustui.requireMultiUser=true`; a secondary Android user/work profile is a qualification prerequisite rather than an optional skip. Missing signing credentials, root, ARM64, device instrumentation, or multi-user state leaves Gate E open/deferred.

Historical Room migration, device-certification, and release-helper instructions live only under `migration/archive/` for provenance. Gate-D evidence is immutable ancestor evidence and is never regenerated for the final tree.

# Android validation remediation — qualified successor authority

> Gate D is qualified and preserved, AUD-FIX-16 physical retirement is complete, and Gate E is qualified. The commands below are the canonical requalification path for any later source candidate.

Do not call raw permanent Gradle/ADB fallbacks or restore app-local device/release helper scripts. Android release qualification is owned by Devtool and uses two source-bound runs:

```bash
cd "$HOME/Code/dustui"
export ANDROID_SERIAL=localhost:5555
./devtoolw gate-e-release
# Keep the source/configuration tree unchanged after this succeeds.
./devtoolw aud-fix-17
```

The first command creates canonical signed release evidence through Devtool's Android release orchestrator. The second imports/cross-binds that report and runs the Gate-E adversarial, Debug/Release, connected-device, rooted ARM64, migration/promise, convergence, final-seal, and post-seal graph.

Rooted ARM64 and device instrumentation remain required. Multi-user/work-profile coverage is conditional on actual device capability, as recorded by `migration/qualification/v1/gate_e_topology.json`; unavailable capability must be explicit and cannot be replaced with fabricated execution evidence. Missing signing credentials, required root/ARM64 support, required device instrumentation, or other mandatory evidence keeps a new candidate unqualified.

Historical Room migration, device-certification, and app-local release-helper instructions live only under `migration/archive/` for provenance. Gate-D evidence is immutable ancestor evidence and is never regenerated for a later tree. The temporary Gate-E fast-resume workflow is retired after qualification; requalification uses the canonical workflow above.

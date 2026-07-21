# Android Phase 9: Device certification and release candidate

Phase 9 adds a truthful, repeatable certification surface for real rooted devices.

The in-app report checks the privileged service UID and backend, scanner version, SELinux state, lock state, notification readiness, writable app storage, working free space, and battery restrictions. Failures block release readiness; notification and battery restrictions remain explicit warnings.

`android/scripts/device-certify.sh` collects ADB evidence for each tested backend. Run it after installing and exercising the debug build on Magisk, KernelSU, KernelSU Next, and APatch devices where available. The script records package state, services, notifications, jobs, launch behavior, properties, and focused logs.

This phase does not pretend that host-side unit tests certify root behavior. A release candidate is certified only after the generated device evidence and quarantine/restore smoke results are reviewed.

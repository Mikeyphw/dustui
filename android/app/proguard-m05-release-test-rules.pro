# M05 release-instrumentation ONLY; not included in ordinary release builds.
# The test runner's classloader may resolve AndroidX tracing from the tested app.
# R8 must not strip/rename the required class from the release candidate APK.
-keep class androidx.tracing.Trace { *; }
-keep class androidx.tracing.Trace$* { *; }

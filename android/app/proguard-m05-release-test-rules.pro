# M05 release-instrumentation ONLY; not included in ordinary release builds.
# The test runner's classloader may resolve AndroidX tracing from the tested app.
# R8 must not strip/rename the required class from the release candidate APK.
-keep class androidx.tracing.Trace { *; }
-keep class androidx.tracing.Trace$* { *; }

# AndroidX Test's Kotlin LazyKt/Intrinsics/Function0 are resolved by the
# runner during onCreate() before running any test. The release app and
# instrumentation are separate R8 inputs, so do not let release R8 shrink
# any standard-library class relied on reflectively/cross-APK by the runner.
# This file is ONLY attached when DUSTUI_M05_INSTRUMENTATION_BUILD=true.
-keep class kotlin.** { *; }

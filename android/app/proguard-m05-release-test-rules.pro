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

# M05 signed APK pair: androidTest is compiled against main classes, then R8
# optimizes the release app independently. The test APK calls these main-app
# classes across the APK boundary, including Kotlin Companion/INSTANCE fields,
# default-argument constructors and Kotlin object singleton methods.
# Retain names and *all* members of the small families used by four approved
# tests; do not disable application R8 or affect ordinary release builds.
# Gradle includes this file ONLY for DUSTUI_M05_INSTRUMENTATION_BUILD=true.
-keep class com.mikeyphw.dustui.go.GoRuntime* { *; }
-keep class com.mikeyphw.dustui.go.GoChildProcessPolicy { *; }
-keep class com.mikeyphw.dustui.ui.DustRoute* { *; }
-keep class com.mikeyphw.dustui.ui.DustSemantic* { *; }
-keep class com.mikeyphw.dustui.ui.DustAdaptive* { *; }

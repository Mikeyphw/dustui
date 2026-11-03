# R8: Error Prone metadata references a JDK compiler-only enum.
# It is not an Android runtime dependency.
-dontwarn javax.lang.model.element.Modifier

# AndroidJUnitRunner resolves this class before running any test methods.
# The app-side M05-only keep is in proguard-m05-release-test-rules.pro.
-keep class androidx.tracing.Trace { *; }
-keep class androidx.tracing.Trace$* { *; }

# The instrumented test APK is separately minified. Prevent the runner's
# early-init Kotlin runtime and AndroidX test components from disappearing
# merely because the release app and test APK are optimized separately.
# These rules apply only to AndroidTest, never ordinary application release.
-keep class kotlin.** { *; }
-keep class androidx.test.** { *; }

# The test APK undergoes a separate R8 pass. Preserve references to the
# M05 cross-APK app ABI alongside the release-app rules (above), so both
# passes agree on the test-accessed names and Kotlin member signatures.
-keep class com.mikeyphw.dustui.go.GoRuntime* { *; }
-keep class com.mikeyphw.dustui.go.GoChildProcessPolicy { *; }
-keep class com.mikeyphw.dustui.ui.DustRoute* { *; }
-keep class com.mikeyphw.dustui.ui.DustSemantic* { *; }
-keep class com.mikeyphw.dustui.ui.DustAdaptive* { *; }

# M05 AndroidTest and release APK are minified independently. Tests call the
# org.json.JSONObject APIs exported by the signed tested application.
# Keep the class names and all members (including optString(String)) stable.
# App-side rules are included only when DUSTUI_M05_INSTRUMENTATION_BUILD=true;
# these AndroidTest rules do not affect normal release builds.
-keep class org.json.** { *; }

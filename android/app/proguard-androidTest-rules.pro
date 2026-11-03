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

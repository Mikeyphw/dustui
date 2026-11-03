# R8: Error Prone metadata references a JDK compiler-only enum.
# It is not an Android runtime dependency.
-dontwarn javax.lang.model.element.Modifier

# AndroidJUnitRunner resolves this class before running any test methods.
# The app-side M05-only keep is in proguard-m05-release-test-rules.pro.
-keep class androidx.tracing.Trace { *; }
-keep class androidx.tracing.Trace$* { *; }

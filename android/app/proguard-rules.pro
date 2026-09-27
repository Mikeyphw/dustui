# Binder/platform adapter entry points used by the privileged service.
-keep class com.mikeyphw.dustui.root.IDustRootService$Stub { *; }
-keep class com.mikeyphw.dustui.root.** { *; }
-keep class com.mikeyphw.dustui.tasker.DustTaskerReceiver { *; }

# DustUI: WorkManager / Room reflective constructors under R8 full mode
#
# WorkManager/Room reflectively instantiate these classes by their no-arg
# constructors. R8 full mode can otherwise remove the constructor because
# there is no direct bytecode call site.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}

# WorkManager can also instantiate its built-in InputMerger reflectively.
# Keep only the required no-arg constructor instead of the whole WorkManager
# package.
-keep class androidx.work.OverwritingInputMerger {
    <init>();
}

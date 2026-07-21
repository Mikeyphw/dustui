-keep class com.mikeyphw.dustui.root.DustRootService { *; }
-keep class com.mikeyphw.dustui.root.IDustRootService$Stub { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# RootService/AIDL entry points are reached through Binder and JNI.
-keep class com.mikeyphw.dustui.root.** { *; }
-keep class com.mikeyphw.dustui.tasker.DustTaskerReceiver { *; }
-keepclasseswithmembernames class * { native <methods>; }

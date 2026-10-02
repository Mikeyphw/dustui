package com.mikeyphw.dustui.go

import java.io.File

/**
 * App-private launch policy for packaged Go child processes.
 *
 * Production Go children must not inherit shell/instrumentation environment or
 * working-directory state from the Android process. Apart from leaking host
 * assumptions into the storage engine, inherited /data/local/tests paths cause
 * SELinux shell_test_data_file search denials. Go 1.25+ also probes Linux
 * cgroups while deriving its default GOMAXPROCS; Android app sandboxes cannot
 * search the cgroup mount on all devices, so we pin GOMAXPROCS from Android's
 * own available-processor view before exec.
 */
internal object GoChildProcessPolicy {
    internal const val ANDROID_CHILD_MARKER = "DUSTUI_ANDROID_CHILD"

    fun apply(
        builder: ProcessBuilder,
        workingDirectory: File,
        homeDirectory: File,
        temporaryDirectory: File,
    ): ProcessBuilder {
        require(workingDirectory.isDirectory || workingDirectory.mkdirs()) { "go_child_working_directory_missing" }
        require(homeDirectory.isDirectory || homeDirectory.mkdirs()) { "go_child_home_directory_missing" }
        require(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs()) { "go_child_temp_directory_missing" }

        builder.directory(workingDirectory)
        val maxProcs = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        builder.environment().apply {
            // Do not inherit TEST_TMPDIR, LD_*, shell paths, instrumentation
            // variables, or arbitrary host settings into the packaged child.
            clear()
            this["PWD"] = workingDirectory.absolutePath
            this["HOME"] = homeDirectory.absolutePath
            this["TMPDIR"] = temporaryDirectory.absolutePath
            this["TMP"] = temporaryDirectory.absolutePath
            this["TEMP"] = temporaryDirectory.absolutePath
            this["PATH"] = "/system/bin:/system/xbin"
            this["ANDROID_ROOT"] = "/system"
            this["ANDROID_DATA"] = "/data"
            this["GOMAXPROCS"] = maxProcs.toString()
            this[ANDROID_CHILD_MARKER] = "1"
        }
        return builder
    }
}

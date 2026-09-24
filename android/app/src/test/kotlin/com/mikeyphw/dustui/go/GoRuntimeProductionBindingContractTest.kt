package com.mikeyphw.dustui.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoRuntimeProductionBindingContractTest {
    @Test fun fixedRuntimePathIsAppOwnedNativeLibrary() {
        assertEquals("/app/lib/libdustui_go.so", GoRuntimePathContract.executablePath("/app/lib"))
    }

    @Test fun productionBindingAndPackagingRemainSourceProven() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "go.mod").isFile && File(it, "app/build.gradle.kts").isFile }
        val bridge = File(root, "app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt").readText()
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(bridge.contains("ProcessBuilder(executable.absolutePath, \"serve\")"))
        assertTrue(bridge.contains("nativeLibraryDir"))
        assertTrue(gradle.contains("libdustui_go.so"))
        assertTrue(gradle.contains("GOOS\", \"android"))
        assertTrue(gradle.contains("buildGoAndroidRuntime"))
    }
}

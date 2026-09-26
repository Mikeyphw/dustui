package com.mikeyphw.dustui.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ValidationRoadmapContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" })) { it.parentFile }
        .first { File(it, "app/src/main").exists() }

    @Test
    fun remainingRoadmapValidationScriptsAreWired() {
        listOf(
            "scripts/validate-remediation-full.sh",
            "scripts/validate-room-migrations.sh",
            "scripts/run-rooted-destructive-safety-tests.sh",
            "scripts/run-multi-user-residue-validation.sh",
            "scripts/run-device-scale-performance.sh",
        ).forEach { relative ->
            val text = root.resolve(relative).readText()
            assertTrue("$relative should execute through Gradle instrumentation", text.contains("connectedDebugAndroidTest") || relative.endsWith("validate-remediation-full.sh"))
            assertTrue("$relative should fail fast", text.contains("set -euo pipefail"))
        }
    }

    @Test
    fun rootedAndScaleInstrumentationSuitesExist() {
        listOf(
            "app/src/androidTest/kotlin/com/mikeyphw/dustui/validation/DustDatabaseMigrationInstrumentedTest.kt" to listOf("MigrationTestHelper", "MIGRATION_1_2", "MIGRATION_2_3"),
            "app/src/androidTest/kotlin/com/mikeyphw/dustui/validation/RootedDestructiveSafetyInstrumentedTest.kt" to listOf("Go manifest/capability", "mount --bind", "fdRelativeDeletionBlocksTopLevelIdentityReplacement"),
            "app/src/androidTest/kotlin/com/mikeyphw/dustui/validation/MultiUserResidueValidationInstrumentedTest.kt" to listOf("pm list users", "InstalledPackageInventory", "dustui.requireMultiUser"),
            "app/src/androidTest/kotlin/com/mikeyphw/dustui/validation/DeviceScalePerformanceInstrumentedTest.kt" to listOf("dustui.entries", "100_000", "RootServiceClient"),
        ).forEach { (relative, markers) ->
            val text = root.resolve(relative).readText()
            markers.forEach { marker -> assertTrue("$relative missing $marker", text.contains(marker)) }
        }
    }

    @Test
    fun buildFileContainsInstrumentationValidationDependencies() {
        val build = root.resolve("app/build.gradle.kts").readText()
        assertTrue(build.contains("androidTestImplementation(libs.androidx.room.testing)"))
        assertTrue(build.contains("androidTestImplementation(libs.androidx.test.ext.junit)"))
        assertTrue(build.contains("androidTestImplementation(libs.androidx.test.runner)"))
        assertTrue(build.contains("getByName(\"androidTest\").assets.srcDir"))
    }
}

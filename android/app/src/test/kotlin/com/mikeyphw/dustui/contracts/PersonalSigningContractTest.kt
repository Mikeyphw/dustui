package com.mikeyphw.dustui.contracts

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalSigningContractTest {
    private fun repoRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "android/app/build.gradle.kts").isFile && File(it, ".devtool.toml").isFile }

    @Test
    fun `release personal signing remains external and fails closed early`() {
        val root = repoRoot()
        val build = File(root, "android/app/build.gradle.kts").readText()
        val setup = File(root, "android/tools/setup-personal-signing.sh").readText()
        val fingerprint = File(root, "android/tools/print-personal-signing-fingerprint.sh").readText()
        val ignore = File(root, ".gitignore").readText()

        assertTrue(build.contains(".config/devtool/dustui/android-signing.properties"))
        assertTrue(build.contains("DUSTUI_ANDROID_SIGNING_PROPERTIES"))
        assertTrue(build.contains("create(\"dustuiPersonal\")"))
        assertTrue(build.contains("signingConfig = signingConfigs.getByName(\"dustuiPersonal\")"))
        assertTrue(build.contains("verifyDustuiPersonalSigningConfigured"))
        assertTrue(build.contains("preReleaseBuild"))
        assertTrue(setup.contains("-storepass:env DUSTUI_SIGNING_SETUP_PASSWORD"))
        assertTrue(fingerprint.contains("-storepass:env DUSTUI_SIGNING_FINGERPRINT_PASSWORD"))
        assertTrue(ignore.contains("*.p12"))
        assertTrue(ignore.contains("**/android-signing.properties"))
    }
}

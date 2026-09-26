plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

val verifyStableToolchainBaseline = tasks.register("verifyStableToolchainBaseline") {
    group = "verification"
    description = "Verifies DustUI's normalized stable Android toolchain baseline."

    doLast {
        val expectedGradle = "9.7.1"
        val expectedWrapperSha256 = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
        val expectedJava = 21
        val expectedAgp = "9.3.1"
        val expectedKotlin = "2.4.10"

        check(gradle.gradleVersion == expectedGradle) {
            "DustUI requires Gradle $expectedGradle; found ${gradle.gradleVersion}."
        }
        check(JavaVersion.current().majorVersion.toInt() == expectedJava) {
            "DustUI requires Java $expectedJava to run Gradle; found ${JavaVersion.current()}."
        }

        val wrapper = file("gradle/wrapper/gradle-wrapper.properties").readText()
        check(wrapper.contains("gradle-$expectedGradle-bin.zip")) {
            "Gradle wrapper must pin Gradle $expectedGradle."
        }
        check(wrapper.contains("distributionSha256Sum=$expectedWrapperSha256")) {
            "Gradle wrapper checksum does not match the normalized Gradle $expectedGradle distribution."
        }

        val catalog = file("gradle/libs.versions.toml").readText()
        fun requireVersion(name: String, expected: String) {
            val pattern = Regex("(?m)^${Regex.escape(name)}\\s*=\\s*\"([^\"]+)\"$")
            val actual = pattern.find(catalog)?.groupValues?.get(1)
            check(actual == expected) {
                "Expected $name=$expected in libs.versions.toml; found ${actual ?: "missing"}."
            }
        }
        requireVersion("agp", expectedAgp)
        requireVersion("kotlin", expectedKotlin)

        val prerelease = Regex("(?i)(?:alpha|beta|rc|preview|milestone|snapshot|eap)")
        val versionLines = catalog.lineSequence()
            .dropWhile { it.trim() != "[versions]" }
            .drop(1)
            .takeWhile { !it.trim().startsWith("[") }
            .filter { it.contains('=') }
            .toList()
        check(versionLines.none { prerelease.containsMatchIn(it.substringAfter('=').trim().trim('\"')) }) {
            "Prerelease dependency/plugin versions are forbidden by the DustUI stable baseline."
        }

        val appBuild = file("app/build.gradle.kts").readText()
        check(appBuild.contains("compileSdk = 36")) { "DustUI compileSdk must remain 36." }
        check(appBuild.contains("targetSdk = 36")) { "DustUI targetSdk must remain 36." }
        check(appBuild.contains("buildToolsVersion = \"36.0.0\"")) { "DustUI Build Tools must be pinned to 36.0.0." }
        check(appBuild.contains("JavaVersion.VERSION_21")) { "DustUI Java source/target must remain 21." }
        check(appBuild.contains("jvmToolchain(21)")) { "DustUI Kotlin JVM toolchain must remain 21." }
    }
}


val verifyDustMigrationContract = tasks.register("verifyDustMigrationContract", Exec::class) {
    group = "verification"
    description = "Verifies DustUI OV13 Gate D product reconnect contract."
    workingDir = rootDir.parentFile
    commandLine("python3", "scripts/validate_ov13_contract.py")
}

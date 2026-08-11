package com.mikeyphw.dustui.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.dustui.data.AppResidueAnalyzer
import com.mikeyphw.dustui.data.InstalledPackageInventory
import com.mikeyphw.dustui.data.ScanEntryEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Multi-user/work-profile residue validation.
 *
 * The first test is a deterministic engine contract: a package installed for
 * any Android user must prevent residue classification for that package. The
 * second test enumerates real users/packages on a rooted validation device so
 * the release gate proves DustUI is not relying on current-user-only state.
 */
@RunWith(AndroidJUnit4::class)
class MultiUserResidueValidationInstrumentedTest {
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun residueAnalyzerTreatsAnyUserInstallAsAuthoritative() {
        val packageName = "com.example.crossuser"
        val entries = listOf(
            ScanEntryEntity(
                scanId = "scan",
                path = "/data/user/0/$packageName",
                parentPath = "/data/user/0",
                name = packageName,
                sizeBytes = 1024,
                allocatedBytes = 1024,
                isDirectory = true,
                depth = 3,
                modifiedAt = 1,
                fileCount = 1,
            ),
        )
        val inventory = InstalledPackageInventory(
            packagesByUser = mapOf(10 to setOf(packageName)),
            complete = true,
            message = null,
        )

        val analysis = AppResidueAnalyzer.analyze(
            scanId = "scan",
            entries = entries,
            inventory = inventory,
            protectedPaths = emptySet(),
            scanGeneration = 7,
        )

        assertTrue("installed package in another user/profile must suppress residue group", analysis.groups.isEmpty())
        assertTrue("analysis must preserve all checked user IDs", 10 in analysis.checkedUsers)
    }

    @Test
    fun rootedDeviceEnumeratesMultipleUsersBeforeResidueValidation() {
        val requireMultiUser = args.getString("dustui.requireMultiUser") == "true"
        assumeTrue("Root is required to enumerate all package users", hasRoot())
        val users = listUsers()
        if (requireMultiUser) {
            assertTrue("Validation device must include a secondary user or work profile", users.size >= 2)
        } else {
            assumeTrue("No secondary user/work profile configured; set -e dustui.requireMultiUser true to require this", users.size >= 2)
        }

        val packagesByUser = users.associateWith { userId -> listPackages(userId) }
        assertTrue("root package inventory must include every listed user", packagesByUser.keys.containsAll(users))
        assertFalse("at least one user must report installed packages", packagesByUser.values.flatten().isEmpty())

        val packageName = args.getString("dustui.packageUnderTest")
            ?: packagesByUser.values.firstOrNull { it.isNotEmpty() }?.firstOrNull()
            ?: return
        val syntheticResidue = ScanEntryEntity(
            scanId = "scan",
            path = "/data/user/0/$packageName",
            parentPath = "/data/user/0",
            name = packageName,
            sizeBytes = 1,
            allocatedBytes = 1,
            isDirectory = true,
            depth = 3,
            modifiedAt = 1,
            fileCount = 1,
        )
        val analysis = AppResidueAnalyzer.analyze(
            scanId = "scan",
            entries = listOf(syntheticResidue),
            inventory = InstalledPackageInventory(packagesByUser, complete = true),
            protectedPaths = emptySet(),
            scanGeneration = 1,
        )
        assertTrue("package installed in any user/profile must not be marked residue: $packageName", analysis.groups.isEmpty())
    }

    private fun listUsers(): List<Int> {
        val output = su("pm list users")
        return Regex("UserInfo\\{(\\d+):").findAll(output).map { it.groupValues[1].toInt() }.distinct().sorted().toList()
    }

    private fun listPackages(userId: Int): Set<String> {
        val output = su("cmd package list packages --user $userId")
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringBefore('=') }
            .filter { it.isNotBlank() }
            .toSortedSet()
    }

    private fun hasRoot(): Boolean = runCatching { su("id -u").trim() == "0" }.getOrDefault(false)

    private fun su(command: String): String {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        check(code == 0) { "su command failed ($code): $command\n$output" }
        return output
    }
}

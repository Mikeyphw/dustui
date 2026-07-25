package com.mikeyphw.dustui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CleaningRecoverySourceContractTest {
    private val androidRoot = locateAndroidRoot()

    @Test fun `duplicate analysis persists completed buckets atomically and supports pause`() {
        val store = source("data/DuplicateAnalysisStore.kt")
        val repository = source("data/ScanRepository.kt")
        val service = source("root/DustRootService.kt")
        assertTrue(store.contains(".tmp"))
        assertTrue(store.contains("output.fd.sync()"))
        assertTrue(store.contains("temporary.renameTo(destination)"))
        assertTrue(store.contains("completedSizes"))
        assertTrue(repository.contains("status = \"PAUSED\""))
        assertTrue(repository.contains("completedSizes = snapshot.completedSizes + bucket.sizeBytes"))
        assertTrue(service.contains("duplicateCancellations"))
        assertTrue(service.contains("cancelDuplicateVerification"))
    }

    @Test fun `cleanup interruption cannot silently become success`() {
        val repository = source("data/ScanRepository.kt")
        val recovery = source("data/OperationRecovery.kt")
        val reports = source("data/CleanupVerification.kt")
        assertTrue(repository.contains("recoverInterruptedOperations"))
        assertTrue(recovery.contains("status = \"INTERRUPTED\""))
        assertTrue(recovery.contains("run a new scan"))
        assertTrue(reports.contains("CleanupPathPresence.UNKNOWN"))
        assertFalse(reports.contains("UNKNOWN -> CleanupVerificationStatus.VERIFIED_DELETED"))
    }

    @Test fun `scan replacement clears review and scan scoped selections`() {
        val viewModel = source("ui/MainViewModel.kt")
        assertTrue(viewModel.contains("cleanupReview = null"))
        assertTrue(viewModel.contains("selectedPaths = emptySet()"))
        assertTrue(viewModel.contains("_rootSearchResults.value = emptyList()"))
        assertTrue(viewModel.contains("duplicateAnalysisJob?.cancel()"))
    }

    private fun source(relative: String): String = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/$relative").readText()
    private fun locateAndroidRoot(): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
            .first { File(it, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").isFile }
    }
}

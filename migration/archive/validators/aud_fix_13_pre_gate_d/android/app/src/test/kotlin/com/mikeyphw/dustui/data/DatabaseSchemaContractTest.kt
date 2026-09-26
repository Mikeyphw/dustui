package com.mikeyphw.dustui.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseSchemaContractTest {
    private val androidRoot = locateAndroidRoot()

    @Test fun `current room schema is exported and matches database version`() {
        val databaseSource = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/DustDatabase.kt").readText()
        val version = Regex("version\\s*=\\s*(\\d+)").find(databaseSource)?.groupValues?.get(1)?.toInt()
        assertTrue("Database version should advance beyond the legacy cleanup schema", requireNotNull(version) >= 3)
        val schemaFile = File(androidRoot, "app/schemas/com.mikeyphw.dustui.data.DustDatabase/$version.json")
        assertTrue(schemaFile.isFile)
        val schema = JSONObject(schemaFile.readText())
        assertEquals(version, schema.getJSONObject("database").getInt("version"))
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        val tables = buildSet { for (index in 0 until entities.length()) add(entities.getJSONObject(index).getString("tableName")) }
        assertTrue(tables.containsAll(setOf("scan_sessions", "scan_entries", "cleanup_operations", "cleanup_plan_items", "quarantine_entries")))
        val cleanupPlanItems = sequence { for (index in 0 until entities.length()) yield(entities.getJSONObject(index)) }
            .first { it.getString("tableName") == "cleanup_plan_items" }
        val planFields = cleanupPlanItems.getJSONArray("fields")
        val fieldNames = buildSet { for (index in 0 until planFields.length()) add(planFields.getJSONObject(index).getString("fieldPath")) }
        assertTrue(fieldNames.contains("expectedMountId"))
    }

    @Test fun `version one migration is explicit and never destructive`() {
        val container = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/AppContainer.kt").readText()
        assertFalse(container.contains("Room.databaseBuilder"))
        val importer = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/LegacyRoomV3Importer.kt").readText()
        assertTrue(importer.contains("SQLiteDatabase.OPEN_READONLY"))
        assertTrue(importer.contains("room-v3-imported.marker"))
        assertTrue(container.contains("Migration(1, 2)"))
        assertTrue(container.contains("Migration(2, 3)"))
        assertTrue(container.contains("index_cleanup_operations_scanId"))
        assertTrue(container.contains("cleanup_plan_items"))
        assertTrue(container.contains("expectedMountId"))
        assertTrue(container.contains("CREATE TABLE IF NOT EXISTS `cleanup_operations`"))
        assertTrue(container.contains("CREATE TABLE IF NOT EXISTS `quarantine_entries`"))
        assertFalse(container.contains("fallbackToDestructiveMigration"))
        assertTrue(container.contains("INSERT INTO `scan_entries_new`"))
        assertTrue(container.contains("COMPLETE_WITH_WARNINGS"))
        assertTrue(container.contains("INTERRUPTED"))
    }

    @Test fun `legacy quarantine table is compatibility only`() {
        val cleanupModels = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/CleanupModels.kt").readText()
        val repository = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        assertTrue(cleanupModels.contains("Retained only so existing version-2 databases remain readable"))
        assertFalse(repository.contains("insertQuarantine"))
        assertFalse(repository.contains("fun restoreCleanup("))
    }
    @Test fun `cleanup execution does not reset an existing operation to reviewed`() {
        val dao = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanDao.kt").readText()
        val repository = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        assertTrue(dao.contains("@Insert(onConflict = OnConflictStrategy.IGNORE)"))
        assertTrue(dao.contains("insertCleanupOperationIfAbsent"))
        assertTrue(repository.contains("val insertedOperation = domainStore.insertCleanupOperationIfAbsent"))
        assertTrue(repository.contains("if (insertedOperation != -1L)"))
        assertFalse(
            "Execution must not unconditionally upsert REVIEWED before claim; that can resurrect completed operations",
            repository.contains("domainStore.upsertCleanupOperation(\n            CleanupOperationEntity(\n                id = review.operationId,\n                scanId = review.scanId,\n                rootPath = review.rootPath,\n                status = CleanupOperationStatus.REVIEWED.name"),
        )
    }

    @Test fun `roadmap source contracts are wired into the implementation`() {
        val viewModel = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt").readText()
        val repository = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        val service = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt").readText()
        val worker = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/scan/ScheduledScanWorker.kt").readText()
        val root = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt").readText()
        val client = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt").readText()
        assertTrue(viewModel.contains("review.matches(session.id, session.rootPath, session.generation)"))
        assertTrue(viewModel.contains("selected path(s) no longer belong to the active scan"))
        assertTrue(repository.contains("Cleanup review expired; create a new plan"))
        assertTrue(repository.contains("interruptActiveScanSessions"))
        assertTrue(service.contains("stopSelfResult(startId)"))
        assertTrue(service.contains("onTimeout"))
        assertTrue(worker.contains("setForeground(createForegroundInfo(request))"))
        assertTrue(root.contains("GoRootWorker.reviewCleanup"))
        assertTrue(root.contains("GoRootWorker.executeCleanup"))
        assertTrue(root.contains("claimCleanupExecutionPlan"))
        assertTrue(root.contains("cleanupJournalFile"))
        assertTrue(client.contains("writeCleanupExecutionPlan(review)"))
        assertTrue(client.contains("CLEANUP_PLAN_DIRECTORY"))
        assertTrue(client.contains(".put(\"operationId\", review.operationId)"))
        assertFalse(client.contains("callMutex"))
        assertFalse(client.contains("it.executeCleanup(review.toExecutionJson())"))
    }

    @Test fun `cleanup root execution is Go owned cancellable and reconciliable`() {
        val aidl = File(androidRoot, "app/src/main/aidl/com/mikeyphw/dustui/root/IDustRootService.aidl").readText()
        val root = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt").readText()
        val client = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt").readText()
        val models = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/CleanupModels.kt").readText()
        val worker = File(androidRoot.parentFile, "go/internal/rootworker/cleanup.go").readText()
        assertTrue(aidl.contains("String cancelCleanup(String operationId);"))
        assertTrue(aidl.contains("String reconcileCleanup(String operationId);"))
        assertTrue(root.contains("GoRootWorker.executeCleanup"))
        assertTrue(root.contains("cleanupProcesses"))
        assertTrue(root.contains("claimCleanupExecutionPlan"))
        assertTrue(worker.contains("mount_boundary_changed"))
        assertTrue(worker.contains("unreviewed_descendant"))
        assertTrue(worker.contains("syscall.Openat"))
        assertTrue(worker.contains("SYS_UNLINKAT"))
        assertTrue(client.contains("service().cancelCleanup(operationId)"))
        assertTrue(client.contains("root.reconcileCleanup(operationId)"))
        assertTrue(models.contains("goManifestJson"))
        assertTrue(models.contains("dustui.cleanup.mutation.v1"))
    }

    @Test fun `ui async requests are semantic route and request owned`() {
        val viewModel = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt").readText()
        val registry = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/shell/DustRouteRequestRegistry.kt").readText()
        assertFalse(viewModel.contains("AtomicLong"))
        assertTrue(viewModel.contains("issueRouteRequest"))
        assertTrue(viewModel.contains("acceptsRouteResponse"))
        assertTrue(viewModel.contains("refreshRoutePresentation"))
        assertTrue(registry.contains("binding.routeKey == current.route.routeKey"))
        assertTrue(registry.contains("binding.routeGeneration == current.generation"))
        assertTrue(registry.contains("binding.entityId == current.entityId"))
        assertTrue(registry.contains("latest.accepts(binding)"))
    }


    @Test fun `analysis roadmap avoids repeated full scan reloads in refresh path`() {
        val repository = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        val viewModel = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt").readText()
        assertTrue(repository.contains("entriesForAnalysis(scanId: String)"))
        assertTrue(viewModel.contains("val analysisEntries = repository.entriesForAnalysis(scanId)"))
        assertTrue(viewModel.contains("repository.appSummaries(scanId, analysisEntries)"))
        assertTrue(viewModel.contains("repository.advancedAnalysis(scanId, analysisEntries)"))
        assertTrue(viewModel.contains("repository.cleanupRecommendations(scanId, residueAnalysis, analysisEntries)"))
    }

    @Test fun `go deletion uses fd relative openat unlinkat contract`() {
        val worker = File(androidRoot.parentFile, "go/internal/rootworker/cleanup.go").readText()
        val root = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt").readText()
        val python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" = File(androidRoot, "app/build.gradle.kts").readText()
        assertTrue(worker.contains("syscall.Openat"))
        assertTrue(worker.contains("O_NOFOLLOW"))
        assertTrue(worker.contains("syscall.Unlinkat"))
        assertTrue(worker.contains("SYS_UNLINKAT"))
        assertTrue(worker.contains("unreviewed_descendant"))
        assertTrue(root.contains("GoRootWorker.executeCleanup"))
        assertFalse(root.contains("NativeDustScanner"))
        assertFalse(gradle.contains("externalNativeBuild"))
    }

    @Test fun `duplicate verification has per operation cancellation identity`() {
        val root = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt").readText()
        val client = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt").readText()
        assertTrue(client.contains("activeDuplicateOperations"))
        assertTrue(client.contains("UUID.randomUUID().toString()"))
        assertTrue(client.contains(".put(\"operationId\", operationId)"))
        assertTrue(client.contains("service().cancelDuplicateVerification(operationId)"))
        assertTrue(root.contains("request.optString(\"operationId\").ifBlank { scanId }"))
        assertTrue(root.contains("duplicateCancellations.putIfAbsent(operationId, cancellation)"))
        assertTrue(root.contains("duplicateCancellations.remove(operationId, cancellation)"))
    }

    @Test fun `go deletion cancellation is operation owned and unknown outcome aware`() {
        val worker = File(androidRoot.parentFile, "go/internal/rootworker/cleanup.go").readText()
        val cleanup = File(androidRoot.parentFile, "go/internal/cleanup/cleanup.go").readText()
        val root = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt").readText()
        val viewModel = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt").readText()
        assertTrue(cleanup.contains("context_cancelled_during_mutation"))
        assertTrue(cleanup.contains("MutationInterrupted"))
        assertTrue(worker.contains("reconcileRequired"))
        assertTrue(root.contains("process.destroy()"))
        assertTrue(root.contains("cleanupJournalFile"))
        assertTrue(viewModel.contains("result.status == "UNKNOWN_OUTCOME""))
    }

    @Test fun `broader ui async surfaces are request owned`() {
        val viewModel = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt").readText()
        assertTrue(viewModel.contains("rootRefreshRequestId"))
        assertTrue(viewModel.contains("scanPreflightRequestId"))
        assertTrue(viewModel.contains("largeFileRequestId"))
        assertTrue(viewModel.contains("residueRequestId"))
        assertTrue(viewModel.contains("recommendationRequestId"))
        assertTrue(viewModel.contains("cleanupRuleRequestId"))
        assertTrue(viewModel.contains("cleanupRuleDraftRequestId"))
        assertTrue(viewModel.contains("storageGrowthRequestId"))
        assertTrue(viewModel.contains("cleanupVerificationRequestId"))
        assertTrue(viewModel.contains("certificationRequestId"))
        assertTrue(viewModel.contains("invalidateScanScopedRequests()"))
    }

    @Test fun `go scanner output is buffered instead of flushed per entry`() {
        val scanner = File(androidRoot.parentFile, "go/internal/rootworker/scan.go").readText()
        assertTrue(scanner.contains("bufio.NewWriterSize"))
        assertTrue(scanner.contains("progressInterval"))
        assertTrue(scanner.contains("if flush"))
        assertFalse(scanner.contains("Flush()") && scanner.contains("type\":\"entry") && scanner.contains("true"))
    }

    private fun locateAndroidRoot(): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
            .first { File(it, "app/schemas/com.mikeyphw.dustui.data.DustDatabase/3.json").isFile }
    }
}

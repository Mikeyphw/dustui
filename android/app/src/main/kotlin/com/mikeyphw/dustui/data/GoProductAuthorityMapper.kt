package com.mikeyphw.dustui.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire-only projection from Go-owned product truth into Android presentation models.
 * No classification, confidence, recommendation, cleanup, or growth policy belongs here.
 */
internal object GoProductAuthorityMapper {
    fun overviewStorage(o: JSONObject): OverviewStorageSemantics = OverviewStorageSemantics(
        scanId = o.getString("scanId"),
        generation = o.optLong("generation", 1L),
        rootPath = o.optString("rootPath"),
        integrityStatus = o.optString("integrityStatus", "UNKNOWN"),
        logicalBytes = o.optLong("logicalBytes"),
        allocatedBytes = o.optLong("allocatedBytes"),
        fileCount = o.optLong("fileCount"),
    )

    fun snapshotDelta(o: JSONObject): SnapshotDelta = SnapshotDelta(
        currentScanId = o.getString("currentScanId"),
        previousScanId = o.nullableString("previousScanId"),
        totalDeltaBytes = o.optLong("totalDeltaBytes"),
        addedBytes = o.optLong("addedBytes"),
        removedBytes = o.optLong("removedBytes"),
        changedEntries = o.optInt("changedEntries"),
    )

    fun appSummaries(a: JSONArray): List<AppStorageSummary> = a.objects().map { o ->
        AppStorageSummary(
            packageName = o.getString("packageName"), totalBytes = o.optLong("totalBytes"),
            privateBytes = o.optLong("privateBytes"), cacheBytes = o.optLong("cacheBytes"),
            externalBytes = o.optLong("externalBytes"), entryCount = o.optInt("entryCount"),
            orphaned = o.optBoolean("orphaned"),
        )
    }

    fun residue(o: JSONObject): AppResidueAnalysis = AppResidueAnalysis(
        scanId = o.getString("scanId"),
        inventoryComplete = o.optBoolean("inventoryComplete"),
        checkedUsers = o.optJSONArray("checkedUsers").ints().toSet(),
        groups = o.optJSONArray("groups").objects().map(::residueGroup),
        protectedLocationCount = o.optInt("protectedLocationCount"),
        generatedAt = o.optLong("generatedAt"),
        message = o.nullableString("message"),
        scanGeneration = o.optLong("scanGeneration", 1L),
    )

    private fun residueGroup(o: JSONObject): AppResidueGroup = AppResidueGroup(
        packageName = o.getString("packageName"), appLabel = o.nullableString("appLabel"),
        confidence = enumValue(o.optString("confidence"), AppResidueConfidence.UNCERTAIN),
        locations = o.optJSONArray("locations").objects().map(::residueLocation),
        relatedInstalledPackages = o.optJSONArray("relatedInstalledPackages").strings().toSet(),
    )

    private fun residueLocation(o: JSONObject): AppResidueLocation = AppResidueLocation(
        path = o.getString("path"), sizeBytes = o.optLong("sizeBytes"), modifiedAt = o.optLong("modifiedAt"),
        fileCount = o.optLong("fileCount"), userId = o.nullableInt("userId"),
        kind = enumValue(o.optString("kind"), AppResidueLocationKind.SHARED_PACKAGE_FOLDER),
        confidence = enumValue(o.optString("confidence"), AppResidueConfidence.UNCERTAIN),
        evidence = o.optJSONArray("evidence").objects().map { e -> AppResidueEvidence(e.optString("code"), e.optString("label"), e.optString("detail")) },
        userProtected = o.optBoolean("userProtected"),
    )

    fun advanced(o: JSONObject): AdvancedStorageReport = AdvancedStorageReport(
        duplicateCandidates = o.optJSONArray("duplicateCandidates").objects().map { x ->
            DuplicateCandidateGroup(x.optLong("sizeBytes"), x.optJSONArray("paths").strings(), x.optLong("estimatedSavingsBytes"))
        },
        largeOldFiles = o.optJSONArray("largeOldFiles").objects().map { x -> LargeOldFile(x.getString("path"), x.optLong("sizeBytes"), x.optLong("modifiedAt"), x.optLong("ageDays")) },
        moduleFootprints = o.optJSONArray("moduleFootprints").objects().map { x -> ModuleFootprint(x.getString("moduleId"), x.optLong("totalBytes"), x.optInt("entryCount")) },
        typeBuckets = o.optJSONArray("typeBuckets").objects().map(::storageBucket),
        ageBuckets = o.optJSONArray("ageBuckets").objects().map(::storageBucket),
        mountTopology = o.optJSONArray("mountTopology").objects().map { x -> MountTopology(x.optString("source"), x.optString("target"), x.optString("filesystem"), x.optBoolean("bind"), x.optBoolean("overlay"), x.optBoolean("readOnly")) },
        orphanedPackages = appSummaries(o.optJSONArray("orphanedPackages")),
    )

    private fun storageBucket(o: JSONObject) = StorageBucket(o.optString("label"), o.optLong("bytes"), o.optInt("count"))

    fun packageProfiles(a: JSONArray): List<PackageStorageProfile> = a.objects().map { o ->
        PackageStorageProfile(
            packageName = o.getString("packageName"), appLabel = o.nullableString("appLabel"), installed = o.optBoolean("installed"),
            totalBytes = o.optLong("totalBytes"), privateBytes = o.optLong("privateBytes"), cacheBytes = o.optLong("cacheBytes"),
            codeBytes = o.optLong("codeBytes"), databaseBytes = o.optLong("databaseBytes"), preferencesBytes = o.optLong("preferencesBytes"),
            externalBytes = o.optLong("externalBytes"), obbBytes = o.optLong("obbBytes"), mediaBytes = o.optLong("mediaBytes"), logBytes = o.optLong("logBytes"),
            duplicateCandidateBytes = o.optLong("duplicateCandidateBytes"), largestPaths = o.optJSONArray("largestPaths").objects().map(::pathSummary),
        )
    }

    fun appStorage(o: JSONObject): AppStorageInspectorAnalysis = AppStorageInspectorAnalysis(
        scanId = o.getString("scanId"), previousScanId = o.nullableString("previousScanId"),
        packages = o.optJSONArray("packages").objects().map(::appStoragePackage), generatedAt = o.optLong("generatedAt"),
    )

    private fun appStoragePackage(o: JSONObject): AppStoragePackageReport = AppStoragePackageReport(
        packageName = o.getString("packageName"), appLabel = o.optString("appLabel", o.getString("packageName")), installed = o.optBoolean("installed"),
        systemApp = o.optBoolean("systemApp"), evidenceState = enumValue(o.optString("evidenceState"), AppStorageEvidenceState.UNAVAILABLE),
        evidenceReasons = o.optJSONArray("evidenceReasons").strings(), versionName = o.nullableString("versionName"), versionCode = o.nullableLong("versionCode"),
        totalBytes = o.optLong("totalBytes"), recentGrowthBytes = o.optLong("recentGrowthBytes"),
        buckets = o.optJSONArray("buckets").objects().map(::appStorageBucket), duplicatePaths = o.optJSONArray("duplicatePaths").objects().map(::appStoragePath),
        residuePaths = o.optJSONArray("residuePaths").objects().map(::appStoragePath), recommendationsIgnored = o.optBoolean("recommendationsIgnored"),
    )

    private fun appStorageBucket(o: JSONObject): AppStorageBucket = AppStorageBucket(
        kind = enumValue(o.optString("kind"), AppStorageBucketKind.OTHER), bytes = o.optLong("bytes"), fileCount = o.optInt("fileCount"),
        paths = o.optJSONArray("paths").objects().map(::appStoragePath),
    )

    private fun appStoragePath(o: JSONObject): AppStoragePathItem = AppStoragePathItem(
        path = o.getString("path"), name = o.optString("name"), sizeBytes = o.optLong("sizeBytes"), modifiedAt = o.optLong("modifiedAt"),
        bucket = enumValue(o.optString("bucket"), AppStorageBucketKind.OTHER), protected = o.optBoolean("protected"),
        deletionEligible = o.optBoolean("deletionEligible"), deletionReason = o.optString("deletionReason"), expectedSha256 = o.nullableString("expectedSha256"),
    )

    fun recommendations(o: JSONObject): CleanupRecommendationAnalysis = CleanupRecommendationAnalysis(
        scanId = o.getString("scanId"), items = o.optJSONArray("items").objects().map(::recommendation),
        generatedAt = o.optLong("generatedAt"), scanGeneration = o.optLong("scanGeneration", 1L),
    )

    private fun recommendation(o: JSONObject): CleanupRecommendation = CleanupRecommendation(
        id = o.getString("id"), kind = enumValue(o.optString("kind"), CleanupRecommendationKind.LARGE_OLD_FILE), title = o.optString("title"),
        summary = o.optString("summary"), paths = o.optJSONArray("paths").strings(), reclaimableBytes = o.optLong("reclaimableBytes"),
        confidence = enumValue(o.optString("confidence"), CleanupRecommendationConfidence.LOW), risk = enumValue(o.optString("risk"), CleanupRecommendationRisk.HIGH),
        score = o.optInt("score"), ageDays = o.nullableLong("ageDays"), evidence = o.optJSONArray("evidence").objects().map { e -> CleanupRecommendationEvidence(e.optString("code"), e.optString("label"), e.optString("detail")) },
        expectedSha256ByPath = o.optJSONObject("expectedSha256ByPath").stringMap(),
    )

    fun growth(o: JSONObject): StorageGrowthAnalysis? {
        val current = o.optJSONObject("currentSession")?.let(::scanSession) ?: return null
        val previous = o.optJSONObject("previousSession")?.let(::scanSession) ?: return null
        return StorageGrowthAnalysis(
            currentSession = current,
            previousSession = previous,
            items = o.optJSONArray("items").objects().map(::growthItem),
            timeline = o.optJSONArray("timeline").objects().map(::timelinePoint),
            insights = o.optJSONArray("insights").objects().map(::growthInsight),
        )
    }

    private fun growthItem(o: JSONObject): StorageGrowthItem = StorageGrowthItem(
        path = o.getString("path"), name = o.optString("name"), kind = enumValue(o.optString("kind"), StorageGrowthKind.GROWN),
        previousBytes = o.optLong("previousBytes"), currentBytes = o.optLong("currentBytes"), deltaBytes = o.optLong("deltaBytes"),
        previousEntry = o.optJSONObject("previousEntry")?.let(::scanEntry), currentEntry = o.optJSONObject("currentEntry")?.let(::scanEntry),
    )

    private fun timelinePoint(o: JSONObject): StorageGrowthTimelinePoint = StorageGrowthTimelinePoint(
        sessionId = o.optString("sessionId"), completedAt = o.optLong("completedAt"), totalBytes = o.optLong("totalBytes"),
        deltaFromPreviousBytes = o.nullableLong("deltaFromPreviousBytes"),
    )

    private fun growthInsight(o: JSONObject): StorageGrowthInsight = StorageGrowthInsight(
        id = o.optString("id"), kind = enumValue(o.optString("kind"), StorageGrowthInsightKind.NEW_LARGE_FILE),
        title = o.optString("title"), detail = o.optString("detail"), deltaBytes = o.optLong("deltaBytes"), currentPaths = o.optJSONArray("currentPaths").strings(),
    )

    private fun scanSession(o: JSONObject): ScanSessionEntity = ScanSessionEntity(
        id = o.getString("id"), presetId = o.nullableString("presetId"), rootPath = o.getString("rootPath"), optionsJson = o.optString("optionsJson", "{}"),
        status = o.optString("status"), backend = o.optString("backend"), startedAt = o.optLong("startedAt"), completedAt = o.nullableLong("completedAt"),
        scannedItems = o.optLong("scannedItems"), scannedBytes = o.optLong("scannedBytes"), totalBytes = o.optLong("totalBytes"), warningCount = o.optLong("warningCount"),
        currentPath = o.optString("currentPath"), errorMessage = o.nullableString("errorMessage"), generation = o.optLong("generation", 1L),
        observedEntryCount = o.optLong("observedEntryCount"), persistedEntryCount = o.optLong("persistedEntryCount"), malformedEventCount = o.optLong("malformedEventCount"),
        truncated = o.optBoolean("truncated"), integrityStatus = o.optString("integrityStatus", ScanIntegrityStatus.UNKNOWN.name),
    )

    private fun scanEntry(o: JSONObject): ScanEntryEntity = ScanEntryEntity(
        scanId = o.getString("scanId"), path = o.getString("path"), parentPath = o.optString("parentPath"), name = o.optString("name"),
        sizeBytes = o.optLong("sizeBytes"), allocatedBytes = o.optLong("allocatedBytes"), isDirectory = o.optBoolean("isDirectory"), depth = o.optInt("depth"),
        modifiedAt = o.optLong("modifiedAt"), fileCount = o.optLong("fileCount"),
    )

    fun verificationReport(o: JSONObject): CleanupVerificationReport = CleanupVerificationReport(
        schemaVersion = o.optInt("schemaVersion", CleanupVerificationReport.SCHEMA_VERSION),
        operationId = o.getString("operationId"), scanId = o.getString("scanId"), rootPath = o.getString("rootPath"),
        startedAt = o.optLong("startedAt"), completedAt = o.optLong("completedAt"), plannedBytes = o.optLong("plannedBytes"),
        attemptedBytes = o.optLong("attemptedBytes"), reportedDeletedBytes = o.optLong("reportedDeletedBytes"), confirmedDeletedBytes = o.optLong("confirmedDeletedBytes"),
        partialBytes = o.optLong("partialBytes"), failedBytes = o.optLong("failedBytes"), freeBytesBefore = o.nullableLong("freeBytesBefore"), freeBytesAfter = o.nullableLong("freeBytesAfter"),
        items = o.optJSONArray("items").objects().map(::verificationItem), appVersion = o.optString("appVersion"),
        environment = o.optJSONObject("environment")?.let(::environment) ?: CleanupEnvironmentContext(), verificationAttempts = o.optInt("verificationAttempts", 1),
        lastVerifiedAt = o.optLong("lastVerifiedAt", o.optLong("completedAt")),
    )

    private fun verificationItem(o: JSONObject): CleanupVerifiedItem = CleanupVerifiedItem(
        path = o.getString("path"), plannedBytes = o.optLong("plannedBytes"), reportedDeletedBytes = o.optLong("reportedDeletedBytes"),
        status = enumValue(o.optString("status"), CleanupVerificationStatus.FAILED), pathPresence = enumValue(o.optString("pathPresence"), CleanupPathPresence.UNKNOWN),
        message = o.nullableString("message"), fingerprint = o.optJSONObject("fingerprint")?.let(::fingerprint),
    )

    private fun fingerprint(o: JSONObject): CleanupPathFingerprint = CleanupPathFingerprint(
        device = o.optLong("device"), inode = o.optLong("inode"), mode = o.optInt("mode"), statSizeBytes = o.optLong("statSizeBytes"), modifiedAt = o.optLong("modifiedAt"),
        treeDigest = o.optString("treeDigest"), entryCount = o.optLong("entryCount"), contentSha256 = o.nullableString("contentSha256"), mountId = o.nullableString("mountId"),
    )

    private fun environment(o: JSONObject): CleanupEnvironmentContext = CleanupEnvironmentContext(
        manufacturer = o.optString("manufacturer", "unknown"), model = o.optString("model", "unknown"), sdkInt = o.optInt("sdkInt"),
        filesystem = o.nullableString("filesystem"), mountPoint = o.nullableString("mountPoint"), mountSource = o.nullableString("mountSource"), readOnly = o.nullableBoolean("readOnly"),
    )

    fun rulePreview(o: JSONObject): CleanupRulePreview = CleanupRulePreview(
        scanId = o.getString("scanId"), generatedAt = o.optLong("generatedAt"),
        matches = o.optJSONArray("matches").objects().map { m ->
            CleanupRuleMatch(
                ruleId = m.optString("ruleId"), ruleName = m.optString("ruleName"),
                entries = m.optJSONArray("entries").objects().map(::scanEntry), exactBytes = m.optLong("exactBytes"),
                blocked = m.optJSONArray("blocked").objects().map { b -> CleanupBlockedItem(b.optString("path"), b.optString("reason")) },
            )
        },
        scanGeneration = o.optLong("scanGeneration", 1L),
    )

    fun modules(o: JSONObject): RootModuleReport = RootModuleReport(
        modules = o.optJSONArray("modules").objects().map(::moduleProfile),
        overlayConflicts = o.optJSONArray("overlayConflicts").objects().map { c -> ModuleOverlayConflict(c.getString("targetPath"), c.optJSONArray("moduleIds").strings(), c.optJSONArray("sourcePaths").objects().map(::pathSummary)) },
        diagnostic = o.optJSONObject("diagnostic")?.let(::moduleDiagnostic) ?: ModuleAnalysisDiagnostic(ModuleDiagnosticStatus.SCAN_FAILED, "Go module evidence unavailable", null),
    )

    private fun moduleProfile(o: JSONObject): RootModuleProfile = RootModuleProfile(
        moduleId = o.getString("moduleId"), totalBytes = o.optLong("totalBytes"), entryCount = o.optInt("entryCount"), hasModuleProp = o.optBoolean("hasModuleProp"),
        hasServiceScript = o.optBoolean("hasServiceScript"), hasPostFsDataScript = o.optBoolean("hasPostFsDataScript"), hasWebUi = o.optBoolean("hasWebUi"), disabled = o.optBoolean("disabled"),
        updatePending = o.optBoolean("updatePending"), overlayTargets = o.optJSONArray("overlayTargets").strings(), largestPaths = o.optJSONArray("largestPaths").objects().map(::pathSummary),
    )

    private fun moduleDiagnostic(o: JSONObject) = ModuleAnalysisDiagnostic(
        status = enumValue(o.optString("status"), ModuleDiagnosticStatus.SCAN_FAILED), message = o.optString("message"), currentScanRoot = o.nullableString("currentScanRoot"),
        expectedRoot = o.optString("expectedRoot", "/data/adb/modules"), scanIncludesModules = o.optBoolean("scanIncludesModules"),
        modulePathAccessible = o.nullableBoolean("modulePathAccessible"), rootCode = o.nullableString("rootCode"),
    )

    private fun pathSummary(o: JSONObject) = PackagePathSummary(o.getString("path"), o.optLong("sizeBytes"), o.optString("category"))

    private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T = enumValues<T>().firstOrNull { it.name.equals(raw, true) } ?: fallback
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }
    private fun JSONObject?.stringMap(): Map<String, String> = if (this == null) emptyMap() else keys().asSequence().associateWith { optString(it) }
    private fun JSONObject.nullableString(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
    private fun JSONObject.nullableLong(k: String): Long? = if (!has(k) || isNull(k)) null else optLong(k)
    private fun JSONObject.nullableInt(k: String): Int? = if (!has(k) || isNull(k)) null else optInt(k)
    private fun JSONObject.nullableBoolean(k: String): Boolean? = if (!has(k) || isNull(k)) null else optBoolean(k)
}

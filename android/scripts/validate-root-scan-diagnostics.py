#!/usr/bin/env python3
"""Static contract validation for DustUI root-scan diagnostics."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


class ValidationError(RuntimeError):
    pass


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise ValidationError(f"{label}: missing required marker: {needle!r}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise ValidationError(f"{label}: forbidden marker remains: {needle!r}")


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise ValidationError(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def resolve_roots(candidate: Path) -> tuple[Path, Path]:
    candidate = candidate.resolve()
    if (candidate / "android/app/src/main").is_dir() and (candidate / "android/settings.gradle.kts").is_file():
        return candidate, candidate / "android"
    if (candidate / "app/src/main").is_dir() and (candidate / "settings.gradle.kts").is_file():
        repository_root = candidate.parent if (candidate.parent / "docs").is_dir() else candidate
        return repository_root, candidate
    raise ValidationError(f"Unable to locate DustUI repository and Android root under {candidate}")


def validate(repository_root: Path, root: Path) -> None:
    aidl = read(root, "app/src/main/aidl/com/mikeyphw/dustui/root/IDustRootService.aidl")
    go_scanner = read(repository_root, "go/internal/rootworker/scan.go")
    service = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
    client = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt")
    diagnostics = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanDiagnostics.kt")
    repository = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
    module = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/data/ModuleAnalyzer.kt")
    parser = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanEventParser.kt")
    foreground = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt")
    viewmodel = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
    ui = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    app_storage_ui = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/ui/AppStorageInspectorScreen.kt")
    cleaning_ui = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/ui/CleaningIntelligenceScreens.kt")
    tasker = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/tasker/DustTaskerReceiver.kt")
    app_container = read(root, "app/src/main/kotlin/com/mikeyphw/dustui/AppContainer.kt")
    source_contract = read(root, "app/src/test/kotlin/com/mikeyphw/dustui/data/RootDiagnosticsSourceContractTest.kt")
    recovery_contract = read(root, "app/src/test/kotlin/com/mikeyphw/dustui/data/CleaningRecoverySourceContractTest.kt")
    database_contract = read(root, "app/src/test/kotlin/com/mikeyphw/dustui/data/DatabaseSchemaContractTest.kt")
    model_test = read(root, "app/src/test/kotlin/com/mikeyphw/dustui/scan/ScanDiagnosticsTest.kt")
    parser_test = read(root, "app/src/test/kotlin/com/mikeyphw/dustui/scan/ScanEventParserTest.kt")
    phase_doc = read(repository_root, "docs/ANDROID_ROOT_SCAN_DIAGNOSTICS.md")

    require(aidl, "String preflightScan(String requestJson)", "AIDL")

    for marker in (
        "Process.myUid() == 0",
        'File("/proc/self/attr/current")',
        'Os.readlink("/proc/self/ns/mnt")',
        'errnoCode("ROOT_OPEN"',
        "path.toFile().canonicalPath",
        '"ROOT_SYMLINK"',
        '"ROOT_NOT_DIRECTORY"',
        '"ROOT_OPEN_FAILED"',
    ):
        require(service, marker, "Root service")
    reject(service, '.put("ok", true)\n                .put("uid"', "Root service readiness")
    reject(service, "Os.realpath", "Android Os API compatibility")
    reject(service, "OsConstants.O_DIRECTORY", "Android Os API compatibility")
    reject(service, "OsConstants.O_CLOEXEC", "minSdk 26 Android Os API compatibility")
    require(service, 'preflightFailure("INVALID_REQUEST", "", error.message ?: "Invalid scan request").toString()', "Preflight binder return type")

    for marker in (
        '"backend": "dustui-go-root-v1"',
        '"code": "ROOT_OPEN_FAILED"',
        '"code": "ROOT_LSTAT_FAILED"',
        '"code": "ROOT_SYMLINK"',
        '"code": "ROOT_NOT_DIRECTORY"',
        's.warning("DIRECTORY_OPEN_FAILED"',
        "emittedFiles",
        "emittedDirectories",
        "depthLimitedDirectories",
        "skippedFilesystemEntries",
        "rootOpened",
        "s.fatal",
        "bufio.NewWriterSize",
    ):
        require(go_scanner, marker, "Go root scanner")

    for marker in (
        "data class RootServiceInfo(",
        "val uid: Int",
        "val selinuxContext: String?",
        "val mountNamespace: String?",
        "data class ScanRootPreflight(",
        "val errno: Int?",
        'path = json.optString("path").ifBlank { json.optString("requestedPath") }',
    ):
        require(client, marker, "Root client")

    for marker in (
        "data class ScanDiagnostics(",
        "val rootUid: Int = -1",
        "val preflightOk: Boolean = false",
        "val emittedFiles: Long = 0",
        "val emittedDirectories: Long = 0",
        "val truncated: Boolean = false",
        "private const val MAX_ISSUES = 64",
        'File(context.filesDir, "scan-diagnostics")',
        "output.fd.sync()",
    ):
        require(diagnostics, marker, "Diagnostics persistence")

    for marker in (
        "ScanDiagnosticStore(appContext)",
        "ScanRepository(appContext, goDomainStore, rootServiceClient, scanDiagnosticStore)",
    ):
        require(app_container, marker, "Application container")

    for marker in (
        "rootClient.serviceInfo()",
        "fun scanDiagnostics(scanId: String)",
        'val moduleRoot = "/data/adb/modules"',
        "ModuleDiagnosticStatus.WRONG_SCAN_ROOT",
        "ModuleDiagnosticStatus.ROOT_ACCESS_FAILED",
        "ModuleDiagnosticStatus.SCAN_FAILED",
        "ModuleDiagnosticStatus.INCOMPLETE_RESULTS",
        "ModuleDiagnosticStatus.NO_MODULES_FOUND",
        "import kotlinx.coroutines.Dispatchers",
        "import androidx.core.net.toUri",
        'Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())',
        'Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$packageName".toUri())',
    ):
        require(repository, marker, "Repository diagnostics")
    reject(repository, 'Uri.parse("package:$packageName")', "AndroidX Uri KTX compatibility")

    for marker in (
        "import androidx.core.os.bundleOf",
        'bundleOf(RESULT_STATUS to "accepted", RESULT_SCAN_ID to request.scanId, RESULT_PATH to request.rootPath)',
        'bundleOf(RESULT_STATUS to "cancel_requested", RESULT_SCAN_ID to it)',
    ):
        require(tasker, marker, "Tasker Bundle KTX compatibility")
    reject(tasker, "Bundle().apply", "Tasker Bundle KTX compatibility")

    kotlin_tree = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (root / "app/src/main").rglob("*.kt")
        if path.is_file()
    )
    for forbidden, label in (
        ("Uri.parse(", "whole-tree Uri KTX compatibility"),
        (".edit().", "whole-tree SharedPreferences KTX compatibility"),
        ("Bundle().apply", "whole-tree Bundle KTX compatibility"),
        ("ContentValues().apply", "whole-tree ContentValues KTX compatibility"),
        ("OsConstants.O_CLOEXEC", "whole-tree minSdk compatibility"),
        ("import androidx.compose.foundation.layout.weight", "whole-tree Compose compatibility"),
    ):
        reject(kotlin_tree, forbidden, label)

    for marker in (
        "enum class ModuleDiagnosticStatus",
        "data class ModuleAnalysisDiagnostic",
        "val diagnostic: ModuleAnalysisDiagnostic",
    ):
        require(module, marker, "Module diagnostics model")

    for marker in (
        "data class Warning(",
        "val code: String",
        "data class Complete(",
        "val emittedFiles: Long",
        "val rootOpened: Boolean",
        "data class Error(",
    ):
        require(parser, marker, "Scan event parser")

    for marker in (
        "ROOT_SERVICE_NOT_PRIVILEGED",
        "ZERO_FILES_EMITTED",
        "MALFORMED_SCANNER_EVENT",
        "MISSING_TERMINAL_EVENT",
        "RESULT_LIMIT_REACHED",
        "DEPTH_LIMIT_REACHED",
        "FILESYSTEM_BOUNDARIES_SKIPPED",
        "diagnosticStore.save(diagnostics)",
        "preflightScan(request)",
        "diagnosticMessage(",
    ):
        require(foreground, marker, "Foreground scan diagnostics")

    for marker in (
        "val rootServiceInfo: StateFlow",
        "val scanDiagnostics: StateFlow",
        "val pathPreflight: StateFlow",
        "fun diagnoseScanPath(path: String)",
        "fun startModuleScan()",
        'rootPath = "/data/adb/modules"',
    ):
        require(viewmodel, marker, "ViewModel diagnostics")

    for marker in (
        'Text("Check access")',
        '"Failure diagnostic"',
        '"Exact scanner and root-service diagnostics retained for this scan."',
        'Scan /data/adb/modules',
        'ModuleDiagnosticStatus.WRONG_SCAN_ROOT',
        'ModuleDiagnosticStatus.ROOT_ACCESS_FAILED',
        'ModuleDiagnosticStatus.SCAN_FAILED',
        'ModuleDiagnosticStatus.INCOMPLETE_RESULTS',
    ):
        require(ui, marker, "Diagnostics UI")

    reject(app_storage_ui, "import androidx.compose.foundation.layout.weight", "App storage Compose compatibility")
    reject(cleaning_ui, "import androidx.compose.foundation.layout.weight", "Cleaning workflows Compose compatibility")
    require(cleaning_ui, "val growthFilter by vm.storageGrowthFilter.collectAsStateWithLifecycle()", "Cleaning workflows lifecycle collection")
    reject(cleaning_ui, "vm.storageGrowthFilter.value", "Compose StateFlow access")

    for marker in (
        "preflights identity path and selinux context",
        "go root scanner treats root open failure as fatal and reports result limits",
        "scan and module screens expose actionable diagnostics",
        "diagnostics integration uses available coroutine and compose APIs",
        'import androidx.core.net.toUri',
        'assertFalse(repository.contains("Uri.parse(',
        'assertTrue(tasker.contains("import androidx.core.os.bundleOf"))',
        'assertFalse(tasker.contains("Bundle().apply"))',
        'File(sourceRoot(), "app/$relative")',
    ):
        require(source_contract, marker, "Source contract tests")

    for marker in (
        '_cleanupReview.value = null',
        '_selectedPaths.value = emptySet()',
        '_rootSearchResults.value = emptyList()',
        'duplicateAnalysisJob?.cancel()',
    ):
        require(recovery_contract, marker, "Cleaning recovery source contract")
    reject(recovery_contract, 'contains("cleanupReview = null")', "Cleaning recovery source contract")
    reject(recovery_contract, 'contains("selectedPaths = emptySet()")', "Cleaning recovery source contract")

    require(database_contract, 'contains("fun restoreCleanup(")', "Database compatibility contract")
    reject(database_contract, 'contains("restoreCleanup")', "Database compatibility contract")
    require(model_test, "diagnostics preserve root identity counters and exact issue context", "Diagnostics model tests")
    require(parser_test, '"ROOT_OPEN_FAILED"', "Parser tests")
    require(parser_test, '"errno":13', "Parser errno test")

    for marker in (
        "UID 0",
        "SELinux",
        "Scan-root preflight",
        "zero-file",
        "/data/adb/modules",
        "truncation",
    ):
        require(phase_doc, marker, "Phase documentation")

    print("Root scan diagnostics contract passed.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    try:
        repository_root, android_root = resolve_roots(Path(args.root))
        validate(repository_root, android_root)
    except ValidationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

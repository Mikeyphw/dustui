package platformcontract

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func repoRoot(t *testing.T) string {
	t.Helper()
	root, err := filepath.Abs(filepath.Join("..", "..", ".."))
	if err != nil {
		t.Fatal(err)
	}
	return root
}

func readRepoFile(t *testing.T, path string) string {
	t.Helper()
	b, err := os.ReadFile(filepath.Join(repoRoot(t), path))
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(b)
}

func requireContains(t *testing.T, source, needle, label string) {
	t.Helper()
	if !strings.Contains(source, needle) {
		t.Fatalf("%s missing %q", label, needle)
	}
}

func TestSQLiteAuthorityConnectionPolicyIsIntentional(t *testing.T) {
	store := readRepoFile(t, "go/internal/state/sqlite_store.go")
	androidRuntime := readRepoFile(t, "android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt")
	server := readRepoFile(t, "go/internal/runtimeio/server.go")

	requireContains(t, store, "const sqliteAuthorityMaxOpenConnections = 1", "SQLite authority")
	requireContains(t, store, "db.SetMaxOpenConns(sqliteAuthorityMaxOpenConnections)", "SQLite authority")
	requireContains(t, store, "db.SetMaxIdleConns(sqliteAuthorityMaxOpenConnections)", "SQLite authority")
	requireContains(t, store, "Keep one SQLite connection as a correctness invariant", "SQLite authority rationale")

	// Both ends are single-flight already, so widening the database pool would
	// not add request throughput; it would only make nested DB re-entry bugs less visible.
	requireContains(t, androidRuntime, "fun request(request: GoRuntimeRequest): GoRuntimeResponse = lock.withLock", "Android runtime serialization")
	requireContains(t, server, "payload, err := dispatch(ctx, frame)", "Go runtime synchronous dispatch")
	if strings.Contains(server, "go dispatch(ctx, frame)") {
		t.Fatal("Go runtime dispatch became concurrent without revisiting SQLite authority policy")
	}
}

func TestPackagedGoChildrenUseSanitizedAppPrivateLaunchPolicy(t *testing.T) {
	policy := readRepoFile(t, "android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoChildProcessPolicy.kt")
	runtime := readRepoFile(t, "android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt")
	rootWorker := readRepoFile(t, "android/app/src/main/kotlin/com/mikeyphw/dustui/root/GoRootWorker.kt")
	main := readRepoFile(t, "go/cmd/dustui-go/main.go")
	launch := readRepoFile(t, "go/internal/launchcontract/contract.go")
	instrumented := readRepoFile(t, "android/app/src/androidTest/kotlin/com/mikeyphw/dustui/go/GoChildLaunchIsolationInstrumentedTest.kt")

	requireContains(t, policy, "builder.directory(workingDirectory)", "Go child launch policy")
	requireContains(t, policy, "clear()", "Go child inherited-environment sanitization")
	for _, variable := range []string{"PWD", "HOME", "TMPDIR", "TMP", "TEMP", "PATH", "ANDROID_ROOT", "ANDROID_DATA", "GOMAXPROCS", "DUSTUI_ANDROID_CHILD"} {
		requireContains(t, policy, "\""+variable+"\"", "Go child environment")
	}
	requireContains(t, policy, "Runtime.getRuntime().availableProcessors().coerceAtLeast(1)", "Android-owned GOMAXPROCS policy")
	requireContains(t, runtime, "GoChildProcessPolicy.apply(", "long-lived Go runtime launcher")
	requireContains(t, runtime, "workingDirectory = stateDir", "long-lived Go runtime launcher")
	requireContains(t, rootWorker, "GoChildProcessPolicy.apply(", "Go root-worker launcher")
	requireContains(t, rootWorker, "go-root-worker", "Go root-worker private working directory")

	requireContains(t, main, "launchcontract.ValidateAndroidChild()", "Go production launch guard")
	requireContains(t, main, "case \"launch-contract\"", "Go launch diagnostics command")
	requireContains(t, launch, "UnexpectedEnvironment", "Go launch diagnostics")
	requireContains(t, launch, "/data/local/tests", "shell-test contamination guard")
	requireContains(t, launch, "/data/nativetest", "native-test contamination guard")

	// Device evidence is owned by an instrumentation test that launches the
	// actual packaged Go executable under an intentionally contaminated parent
	// ProcessBuilder and then starts the real long-lived serve path.
	for _, token := range []string{
		"packagedGoChildStartsWithSanitizedPrivateLaunchContract",
		"TEST_TMPDIR",
		"LD_LIBRARY_PATH",
		"launch-contract",
		"GoRuntimeProcess.start(context)",
		"shellTestPathPresent",
		"unexpectedEnvironment",
	} {
		requireContains(t, instrumented, token, "Android packaged-Go launch regression")
	}
}

func TestProductionSourcesDoNotDirectlyProbeShellTestOrCgroupPaths(t *testing.T) {
	root := repoRoot(t)
	productionRoots := []string{
		"go/cmd",
		"go/internal",
		"android/app/src/main/kotlin",
	}
	forbidden := []string{"/data/local/tests", "/data/nativetest", "/sys/fs/cgroup"}
	allowedFiles := map[string]bool{
		filepath.Clean("go/internal/launchcontract/contract.go"):                                     true,
		filepath.Clean("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoChildProcessPolicy.kt"): true,
	}
	for _, relRoot := range productionRoots {
		err := filepath.WalkDir(filepath.Join(root, relRoot), func(path string, entry os.DirEntry, err error) error {
			if err != nil {
				return err
			}
			if entry.IsDir() || strings.HasSuffix(path, "_test.go") || (!strings.HasSuffix(path, ".go") && !strings.HasSuffix(path, ".kt")) {
				return nil
			}
			rel, err := filepath.Rel(root, path)
			if err != nil {
				return err
			}
			if allowedFiles[filepath.Clean(rel)] {
				return nil
			}
			source, err := os.ReadFile(path)
			if err != nil {
				return err
			}
			for _, needle := range forbidden {
				if strings.Contains(string(source), needle) {
					t.Errorf("production source %s directly references forbidden launch-probe path %q", rel, needle)
				}
			}
			return nil
		})
		if err != nil {
			t.Fatal(err)
		}
	}
}

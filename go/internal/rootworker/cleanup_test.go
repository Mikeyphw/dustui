package rootworker

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func reviewFor(t *testing.T, root string, item cleanupPlanItem) map[string]any {
	t.Helper()
	input := cleanupPlanInput{ID: "op-1", OperationID: "op-1", ScanID: "scan-1", ScanGeneration: 3, RootPath: root, Items: []cleanupPlanItem{item}}
	var in bytes.Buffer
	if err := json.NewEncoder(&in).Encode(input); err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	if err := ReviewCleanup(&in, &out); err != nil {
		t.Fatal(err)
	}
	var review map[string]any
	if err := json.Unmarshal(out.Bytes(), &review); err != nil {
		t.Fatalf("decode review: %v\n%s", err, out.String())
	}
	return review
}

func executeReview(t *testing.T, root string, review map[string]any) map[string]any {
	t.Helper()
	input := map[string]any{
		"operationId": "op-1", "scanId": "scan-1", "scanGeneration": 3, "rootPath": root,
		"accepted": review["accepted"], "goManifest": review["goManifest"], "goCapability": review["goCapability"],
		"mutationProtocol": review["mutationProtocol"], "journalPath": filepath.Join(root, ".dustui-op-1-journal.json"),
	}
	var in bytes.Buffer
	if err := json.NewEncoder(&in).Encode(input); err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	if err := ExecuteCleanup(context.Background(), &in, &out); err != nil {
		t.Fatal(err)
	}
	var result map[string]any
	if err := json.Unmarshal(out.Bytes(), &result); err != nil {
		t.Fatalf("decode result: %v\n%s", err, out.String())
	}
	return result
}

func TestReviewAndExecuteDeletesExactReviewedFile(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "target.bin")
	if err := os.WriteFile(target, []byte("dustui"), 0600); err != nil {
		t.Fatal(err)
	}
	info, _ := os.Stat(target)
	review := reviewFor(t, root, cleanupPlanItem{Path: target, SizeBytes: info.Size(), ModifiedAt: info.ModTime().Unix()})
	if review["ok"] != true {
		t.Fatalf("review failed: %+v", review)
	}
	result := executeReview(t, root, review)
	if result["status"] != "COMPLETED" {
		t.Fatalf("execution = %+v", result)
	}
	if _, err := os.Lstat(target); !os.IsNotExist(err) {
		t.Fatalf("target still exists: %v", err)
	}
}

func TestReplacementAfterReviewFailsClosed(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "target.bin")
	if err := os.WriteFile(target, []byte("old"), 0600); err != nil {
		t.Fatal(err)
	}
	info, _ := os.Stat(target)
	review := reviewFor(t, root, cleanupPlanItem{Path: target, SizeBytes: info.Size(), ModifiedAt: info.ModTime().Unix()})
	if err := os.Remove(target); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(target, []byte("new"), 0600); err != nil {
		t.Fatal(err)
	}
	result := executeReview(t, root, review)
	if result["ok"] == true {
		t.Fatalf("replacement was deleted: %+v", result)
	}
	if data, err := os.ReadFile(target); err != nil || string(data) != "new" {
		t.Fatalf("replacement did not survive: %q %v", data, err)
	}
}

func TestAddedDescendantAfterReviewIsNeverDeleted(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "tree")
	if err := os.Mkdir(target, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(target, "reviewed"), []byte("a"), 0600); err != nil {
		t.Fatal(err)
	}
	info, _ := os.Stat(target)
	review := reviewFor(t, root, cleanupPlanItem{Path: target, Directory: true, ModifiedAt: info.ModTime().Unix()})
	newPath := filepath.Join(target, "added-after-review")
	if err := os.WriteFile(newPath, []byte("safe"), 0600); err != nil {
		t.Fatal(err)
	}
	result := executeReview(t, root, review)
	if result["ok"] == true {
		t.Fatalf("added descendant must fail review binding: %+v", result)
	}
	if data, err := os.ReadFile(newPath); err != nil || string(data) != "safe" {
		t.Fatalf("unreviewed descendant was touched: %q %v", data, err)
	}
}

func TestSelectedSymlinkIsRejected(t *testing.T) {
	root := t.TempDir()
	real := filepath.Join(root, "real")
	link := filepath.Join(root, "link")
	if err := os.WriteFile(real, []byte("safe"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("real", link); err != nil {
		t.Fatal(err)
	}
	review := reviewFor(t, root, cleanupPlanItem{Path: link})
	if review["ok"] == true {
		t.Fatalf("symlink review should fail: %+v", review)
	}
	if _, err := os.Lstat(link); err != nil {
		t.Fatalf("symlink was touched: %v", err)
	}
}

func TestExecutionReplayIsDurablyDenied(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "target.bin")
	if err := os.WriteFile(target, []byte("dustui"), 0600); err != nil {
		t.Fatal(err)
	}
	info, _ := os.Stat(target)
	review := reviewFor(t, root, cleanupPlanItem{Path: target, SizeBytes: info.Size(), ModifiedAt: info.ModTime().Unix()})
	first := executeReview(t, root, review)
	if first["status"] != "COMPLETED" {
		t.Fatalf("first execution = %+v", first)
	}
	second := executeReview(t, root, review)
	if second["ok"] == true || second["reconcileRequired"] != true {
		t.Fatalf("replay must require reconciliation: %+v", second)
	}
}

func TestRetainedCopyIsRevalidatedAtExecution(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "delete.bin")
	retained := filepath.Join(root, "keep.bin")
	payload := []byte("same-copy")
	if err := os.WriteFile(target, payload, 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(retained, payload, 0600); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(payload)
	info, _ := os.Stat(target)
	review := reviewFor(t, root, cleanupPlanItem{Path: target, SizeBytes: info.Size(), ModifiedAt: info.ModTime().Unix(), RetainedPath: retained, RetainedSHA256: hex.EncodeToString(sum[:])})
	if review["ok"] != true {
		t.Fatalf("review failed: %+v", review)
	}
	if err := os.WriteFile(retained, []byte("changed"), 0600); err != nil {
		t.Fatal(err)
	}
	result := executeReview(t, root, review)
	if result["ok"] == true {
		t.Fatalf("changed retained copy must fail closed: %+v", result)
	}
	if _, err := os.Stat(target); err != nil {
		t.Fatalf("target should survive failed retained-copy revalidation: %v", err)
	}
}

func TestExpiredCapabilityFailsBeforeMutation(t *testing.T) {
	root := t.TempDir()
	target := filepath.Join(root, "target.bin")
	if err := os.WriteFile(target, []byte("dustui"), 0600); err != nil {
		t.Fatal(err)
	}
	info, _ := os.Stat(target)
	review := reviewFor(t, root, cleanupPlanItem{Path: target, SizeBytes: info.Size(), ModifiedAt: info.ModTime().Unix()})
	capability := review["goCapability"].(map[string]any)
	capability["expiresAtUnixMs"] = float64(1)
	result := executeReview(t, root, review)
	if result["ok"] == true {
		t.Fatalf("expired capability must fail: %+v", result)
	}
	if _, err := os.Stat(target); err != nil {
		t.Fatalf("expired capability touched target: %v", err)
	}
}

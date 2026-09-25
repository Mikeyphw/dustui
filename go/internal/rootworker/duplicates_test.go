package rootworker

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func verifyDuplicateJSON(t *testing.T, ctx context.Context, req map[string]any) map[string]any {
	t.Helper()
	raw, err := json.Marshal(req)
	if err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	if err := VerifyDuplicates(ctx, bytes.NewReader(raw), &out); err != nil {
		t.Fatal(err)
	}
	var decoded map[string]any
	if err := json.Unmarshal(out.Bytes(), &decoded); err != nil {
		t.Fatalf("decode output: %v: %s", err, out.String())
	}
	return decoded
}

func TestVerifyDuplicatesFindsPhysicalCopiesAndSafeSavings(t *testing.T) {
	root := t.TempDir()
	content := bytes.Repeat([]byte("dustui-aud-fix-02"), 4096)
	a := filepath.Join(root, "a.bin")
	b := filepath.Join(root, "b.bin")
	if err := os.WriteFile(a, content, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(b, content, 0o600); err != nil {
		t.Fatal(err)
	}

	result := verifyDuplicateJSON(t, context.Background(), map[string]any{
		"operationId": "op-1", "scanId": "scan-1", "scanRoot": root,
		"sizeBytes": len(content), "partialBytes": 4096, "paths": []string{a, b},
	})
	if result["ok"] != true {
		t.Fatalf("expected ok result: %#v", result)
	}
	groups, ok := result["groups"].([]any)
	if !ok || len(groups) != 1 {
		t.Fatalf("expected one group: %#v", result["groups"])
	}
	group := groups[0].(map[string]any)
	if int(group["physicalCopyCount"].(float64)) != 2 {
		t.Fatalf("physical copies: %#v", group)
	}
	if got := int64(group["safeReclaimableBytes"].(float64)); got != int64(len(content)) {
		t.Fatalf("safe reclaimable=%d want=%d", got, len(content))
	}
}

func TestVerifyDuplicatesDoesNotCountHardlinksAsTwoPhysicalCopies(t *testing.T) {
	root := t.TempDir()
	content := bytes.Repeat([]byte("hardlink"), 8192)
	a := filepath.Join(root, "a.bin")
	b := filepath.Join(root, "b.bin")
	if err := os.WriteFile(a, content, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Link(a, b); err != nil {
		t.Skipf("hard links unavailable: %v", err)
	}

	result := verifyDuplicateJSON(t, context.Background(), map[string]any{
		"operationId": "op-hardlink", "scanId": "scan-1", "scanRoot": root,
		"sizeBytes": len(content), "paths": []string{a, b},
	})
	groups := result["groups"].([]any)
	if len(groups) != 0 {
		t.Fatalf("hard links must not become a reclaimable duplicate group: %#v", groups)
	}
}

func TestVerifyDuplicatesHonorsCancellation(t *testing.T) {
	root := t.TempDir()
	content := bytes.Repeat([]byte("cancel"), 2048)
	a := filepath.Join(root, "a.bin")
	b := filepath.Join(root, "b.bin")
	if err := os.WriteFile(a, content, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(b, content, 0o600); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	result := verifyDuplicateJSON(t, ctx, map[string]any{
		"operationId": "op-cancel", "scanId": "scan-1", "scanRoot": root,
		"sizeBytes": len(content), "paths": []string{a, b},
	})
	if result["cancelled"] != true || result["ok"] != false {
		t.Fatalf("expected typed cancellation: %#v", result)
	}
}

func TestVerifyDuplicatesRejectsUnboundedPathSet(t *testing.T) {
	paths := make([]string, maxDuplicatePaths+1)
	for i := range paths {
		paths[i] = "/tmp/ignored"
	}
	result := verifyDuplicateJSON(t, context.Background(), map[string]any{
		"operationId": "op-bound", "scanId": "scan-1", "scanRoot": "/tmp",
		"sizeBytes": 1, "paths": paths,
	})
	if result["ok"] != false {
		t.Fatalf("expected bounded request rejection: %#v", result)
	}
}

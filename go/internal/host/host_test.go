package host

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func runForTest(t *testing.T, args ...string) map[string]any {
	t.Helper()
	var out, errOut bytes.Buffer
	code := Run(context.Background(), args, Streams{In: strings.NewReader(""), Out: &out, Err: &errOut})
	if code != 0 {
		t.Fatalf("Run(%v)=%d stderr=%s", args, code, errOut.String())
	}
	var payload map[string]any
	if err := json.Unmarshal(out.Bytes(), &payload); err != nil {
		t.Fatalf("Run(%v) did not emit JSON: %v\n%s", args, err, out.String())
	}
	return payload
}

func TestProductInspectionReportsCurrentCapabilitiesOnly(t *testing.T) {
	payload := runForTest(t, "inspect", "product")
	if got, _ := payload["hostAuthorityDefault"].(bool); !got {
		t.Fatalf("Go host is not the product default: %#v", payload)
	}
	if payload["protocol"] != "dustui.product.v2" || payload["sourceOfTruth"] != "go-sqlite + go runtime protocols" {
		t.Fatalf("unexpected current authority contract: %#v", payload)
	}
	if _, ok := payload["gateDQualified"]; ok {
		t.Fatalf("historical qualification exposed: %#v", payload)
	}
	if _, ok := payload["phase"]; ok {
		t.Fatalf("historical migration phase exposed: %#v", payload)
	}
}

func TestScanQuerySearchShareSQLiteAuthority(t *testing.T) {
	root := t.TempDir()
	if err := os.WriteFile(filepath.Join(root, "needle.txt"), []byte("dustui"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(root, "nested"), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "nested", "other.bin"), []byte("123456"), 0o600); err != nil {
		t.Fatal(err)
	}
	db := filepath.Join(t.TempDir(), "dustui.db")
	scanPayload := runForTest(t, "scan", "--db", db, root)
	if persisted, _ := scanPayload["persisted"].(bool); !persisted {
		t.Fatalf("scan did not persist: %#v", scanPayload)
	}
	query := runForTest(t, "query", "--db", db, "--query", "needle", "--limit", "20")
	if ok, _ := query["ok"].(bool); !ok {
		t.Fatalf("query failed: %#v", query)
	}
	search := runForTest(t, "search", "--db", db, "--query", "needle", "--limit", "20")
	if ok, _ := search["ok"].(bool); !ok {
		t.Fatalf("search failed: %#v", search)
	}
	encoded, _ := json.Marshal(search)
	if !bytes.Contains(encoded, []byte("needle.txt")) {
		t.Fatalf("search missing persisted entry: %s", encoded)
	}
}

func TestCompatibilityAliasesConvergeOnGoTUI(t *testing.T) {
	for _, alias := range []string{"dust-tui", "dust-space"} {
		var out, errOut bytes.Buffer
		code := RunAlias(context.Background(), alias, []string{"."}, Streams{In: strings.NewReader("quit\n"), Out: &out, Err: &errOut})
		if code != 0 {
			t.Fatalf("%s alias failed: %s", alias, errOut.String())
		}
		if !strings.Contains(out.String(), "DustUI Go TUI") {
			t.Fatalf("%s not routed to Go TUI: %s", alias, out.String())
		}
	}
}

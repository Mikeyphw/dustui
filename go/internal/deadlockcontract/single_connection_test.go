package deadlockcontract

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func readRepoFile(t *testing.T, parts ...string) string {
	t.Helper()
	path := filepath.Join(append([]string{".."}, parts...)...)
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(b)
}

func functionBody(t *testing.T, source, signature string) string {
	t.Helper()
	start := strings.Index(source, signature)
	if start < 0 {
		t.Fatalf("missing %s", signature)
	}
	rest := source[start+len(signature):]
	end := strings.Index(rest, "\nfunc ")
	if end < 0 {
		return rest
	}
	return rest[:end]
}

func TestDuplicateCandidateQueryReleasesSingleConnectionBeforePathLookup(t *testing.T) {
	source := readRepoFile(t, "state", "product_views_bounded.go")
	body := functionBody(t, source, "func (s *SQLiteStore) boundedDuplicateCandidates")

	firstQuery := strings.Index(body, "rows, err := s.db.QueryContext")
	closeRows := strings.Index(body, "if err := rows.Close(); err != nil")
	pathQuery := strings.Index(body, "pathRows, err := s.db.QueryContext")
	if firstQuery < 0 || closeRows < 0 || pathQuery < 0 {
		t.Fatalf("bounded duplicate query must retain explicit bucket-query, rows-close, and path-query phases")
	}
	if !(firstQuery < closeRows && closeRows < pathQuery) {
		t.Fatalf("path lookup must happen only after bucket Rows releases the single SQLite connection")
	}
	if strings.Contains(body[firstQuery:closeRows], "pathRows, err := s.db.QueryContext") {
		t.Fatalf("single-connection query re-entry reintroduced before bucket Rows close")
	}
}

func TestSQLiteAuthorityRemainsIntentionallySingleConnection(t *testing.T) {
	source := readRepoFile(t, "state", "sqlite_store.go")
	if !strings.Contains(source, "db.SetMaxOpenConns(1)") {
		t.Fatalf("contract assumes the production SQLite authority remains single-connection")
	}
}

package state

import (
	"fmt"
	"strings"
)

const SchemaVersion = 1

var RequiredTables = []string{
	"scan_sessions",
	"scan_entries",
	"scan_contexts",
	"operations",
	"operation_events",
	"cleanup_manifests",
	"cleanup_capabilities",
	"cleanup_results",
	"package_inventory_snapshots",
	"analysis_results",
	"evidence_generations",
	"problems",
	"run_history",
}

var SQLitePragmas = []string{
	"PRAGMA foreign_keys = ON",
	"PRAGMA journal_mode = WAL",
	"PRAGMA synchronous = NORMAL",
}

var QueryIndexes = []string{
	"CREATE INDEX IF NOT EXISTS idx_scan_entries_parent_page ON scan_entries(scan_id, parent_path, name, path);",
	"CREATE INDEX IF NOT EXISTS idx_scan_entries_search_name ON scan_entries(scan_id, name, path);",
	"CREATE INDEX IF NOT EXISTS idx_scan_entries_logical_size ON scan_entries(scan_id, logical_size_bytes DESC, path);",
	"CREATE INDEX IF NOT EXISTS idx_scan_entries_allocated_size ON scan_entries(scan_id, allocated_size_bytes DESC, path);",
	"CREATE INDEX IF NOT EXISTS idx_scan_entries_modified ON scan_entries(scan_id, modified_at_ms DESC, path);",
	"CREATE INDEX IF NOT EXISTS idx_scan_sessions_history ON scan_sessions(canonical_root, completed_at_ms, generation);",
}

type Table struct {
	Name string
	SQL  string
}

var Tables = []Table{
	{"schema_metadata", `CREATE TABLE IF NOT EXISTS schema_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at_ms INTEGER NOT NULL);`},
	{"scan_sessions", `CREATE TABLE IF NOT EXISTS scan_sessions (id TEXT PRIMARY KEY, room_scan_id TEXT UNIQUE, generation INTEGER NOT NULL, root_path TEXT NOT NULL, options_fingerprint TEXT NOT NULL, backend TEXT NOT NULL, status TEXT NOT NULL, integrity_status TEXT NOT NULL, started_at_ms INTEGER NOT NULL, completed_at_ms INTEGER, observed_entry_count INTEGER NOT NULL DEFAULT 0, persisted_entry_count INTEGER NOT NULL DEFAULT 0, malformed_event_count INTEGER NOT NULL DEFAULT 0, truncated INTEGER NOT NULL DEFAULT 0, expected_generation INTEGER NOT NULL);`},
	{"scan_entries", `CREATE TABLE IF NOT EXISTS scan_entries (scan_id TEXT NOT NULL, path TEXT NOT NULL, parent_path TEXT NOT NULL, name TEXT NOT NULL, logical_size_bytes INTEGER NOT NULL, allocated_size_bytes INTEGER NOT NULL, is_directory INTEGER NOT NULL, depth INTEGER NOT NULL, modified_at_ms INTEGER NOT NULL, file_count INTEGER NOT NULL DEFAULT 0, source_room_path TEXT, PRIMARY KEY(scan_id, path), FOREIGN KEY(scan_id) REFERENCES scan_sessions(id) ON DELETE CASCADE);`},
	{"scan_contexts", `CREATE TABLE IF NOT EXISTS scan_contexts (scan_id TEXT PRIMARY KEY, canonical_root TEXT NOT NULL, generation INTEGER NOT NULL, backend_identity TEXT NOT NULL, hidden_policy TEXT NOT NULL, exclusion_policy TEXT NOT NULL, filesystem_boundary_policy TEXT NOT NULL, size_semantics TEXT NOT NULL, FOREIGN KEY(scan_id) REFERENCES scan_sessions(id) ON DELETE CASCADE);`},
	{"operations", `CREATE TABLE IF NOT EXISTS operations (id TEXT PRIMARY KEY, request_id TEXT NOT NULL UNIQUE, request_fingerprint TEXT NOT NULL, generation INTEGER NOT NULL, state TEXT NOT NULL, last_sequence INTEGER NOT NULL, terminal_sequence INTEGER, recovery_required INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL);`},
	{"operation_events", `CREATE TABLE IF NOT EXISTS operation_events (operation_id TEXT NOT NULL, sequence INTEGER NOT NULL, generation INTEGER NOT NULL, state TEXT NOT NULL, reason TEXT, message TEXT, created_at_ms INTEGER NOT NULL, PRIMARY KEY(operation_id, sequence), FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"cleanup_manifests", `CREATE TABLE IF NOT EXISTS cleanup_manifests (id TEXT PRIMARY KEY, operation_id TEXT NOT NULL, scan_id TEXT NOT NULL, scan_generation INTEGER NOT NULL, manifest_hash TEXT NOT NULL, immutable_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"cleanup_capabilities", `CREATE TABLE IF NOT EXISTS cleanup_capabilities (id TEXT PRIMARY KEY, manifest_id TEXT NOT NULL, operation_id TEXT NOT NULL, capability_hash TEXT NOT NULL, consumed_at_ms INTEGER, expires_at_ms INTEGER NOT NULL, FOREIGN KEY(manifest_id) REFERENCES cleanup_manifests(id) ON DELETE CASCADE);`},
	{"cleanup_results", `CREATE TABLE IF NOT EXISTS cleanup_results (operation_id TEXT PRIMARY KEY, status TEXT NOT NULL, reclaimed_bytes INTEGER NOT NULL DEFAULT 0, certainty TEXT NOT NULL, retained_legacy_quarantine_json TEXT, completed_at_ms INTEGER, FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"package_inventory_snapshots", `CREATE TABLE IF NOT EXISTS package_inventory_snapshots (id TEXT PRIMARY KEY, generation INTEGER NOT NULL, user_handle TEXT NOT NULL, source TEXT NOT NULL, snapshot_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL);`},
	{"analysis_results", `CREATE TABLE IF NOT EXISTS analysis_results (id TEXT PRIMARY KEY, scan_id TEXT NOT NULL, kind TEXT NOT NULL, generation INTEGER NOT NULL, result_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, FOREIGN KEY(scan_id) REFERENCES scan_sessions(id) ON DELETE CASCADE);`},
	{"evidence_generations", `CREATE TABLE IF NOT EXISTS evidence_generations (id TEXT PRIMARY KEY, subject TEXT NOT NULL, generation INTEGER NOT NULL, evidence_hash TEXT NOT NULL, created_at_ms INTEGER NOT NULL);`},
	{"problems", `CREATE TABLE IF NOT EXISTS problems (id TEXT PRIMARY KEY, severity TEXT NOT NULL, reason TEXT NOT NULL, subject TEXT NOT NULL, message TEXT NOT NULL, recovery_required INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL);`},
	{"run_history", `CREATE TABLE IF NOT EXISTS run_history (id TEXT PRIMARY KEY, kind TEXT NOT NULL, status TEXT NOT NULL, started_at_ms INTEGER NOT NULL, completed_at_ms INTEGER, summary_json TEXT NOT NULL);`},
}

func CreateStatements() []string {
	statements := append([]string{}, SQLitePragmas...)
	for _, table := range Tables {
		statements = append(statements, table.SQL)
	}
	statements = append(statements, QueryIndexes...)
	statements = append(statements, fmt.Sprintf("INSERT OR REPLACE INTO schema_metadata(key, value, updated_at_ms) VALUES('schema_version', '%d', strftime('%%s','now') * 1000);", SchemaVersion))
	return statements
}

func ValidateSchemaContract() error {
	joined := strings.Join(CreateStatements(), "\n")
	for _, table := range RequiredTables {
		if !strings.Contains(joined, "CREATE TABLE IF NOT EXISTS "+table) {
			return fmt.Errorf("required table %s missing", table)
		}
	}
	for _, required := range []string{"journal_mode = WAL", "expected_generation", "request_fingerprint", "operation_events", "retained_legacy_quarantine_json", "idx_scan_entries_parent_page", "idx_scan_entries_logical_size", "idx_scan_sessions_history"} {
		if !strings.Contains(joined, required) {
			return fmt.Errorf("schema contract missing %s", required)
		}
	}
	return nil
}

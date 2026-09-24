package state

import (
	"fmt"
	"strings"
)

const SchemaVersion = 6

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
	"runtime_sessions",
	"shell_routes",
	"root_runtime_identities",
	"privileged_admissions",
	"privileged_events",
	"cleanup_actionable_evidence",
	"cleanup_manifest_entries",
	"cleanup_capability_claims",
	"cleanup_mutation_journal",
	"cleanup_verification_results",
	"cleanup_recovery_status",
	"android_package_evidence_snapshots",
	"android_package_records",
	"android_storage_evidence",
	"package_residue_candidates",
	"package_action_rechecks",
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
	{"cleanup_manifests", `CREATE TABLE IF NOT EXISTS cleanup_manifests (id TEXT PRIMARY KEY, operation_id TEXT NOT NULL, scan_id TEXT NOT NULL, scan_generation INTEGER NOT NULL, scan_context_hash TEXT NOT NULL, manifest_hash TEXT NOT NULL, manifest_revision INTEGER NOT NULL, source_context_hash TEXT NOT NULL, root_identity TEXT NOT NULL, immutable_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"cleanup_capabilities", `CREATE TABLE IF NOT EXISTS cleanup_capabilities (id TEXT PRIMARY KEY, manifest_id TEXT NOT NULL, operation_id TEXT NOT NULL, scan_id TEXT NOT NULL, scan_generation INTEGER NOT NULL, scan_context_hash TEXT NOT NULL, manifest_hash TEXT NOT NULL, manifest_revision INTEGER NOT NULL, root_identity TEXT NOT NULL, capability_hash TEXT NOT NULL, claim_state TEXT NOT NULL, reviewed_at_ms INTEGER NOT NULL, claimed_at_ms INTEGER, expires_at_ms INTEGER NOT NULL, FOREIGN KEY(manifest_id) REFERENCES cleanup_manifests(id) ON DELETE CASCADE);`},
	{"cleanup_results", `CREATE TABLE IF NOT EXISTS cleanup_results (operation_id TEXT PRIMARY KEY, status TEXT NOT NULL, reclaimed_bytes INTEGER NOT NULL DEFAULT 0, certainty TEXT NOT NULL, retained_legacy_quarantine_json TEXT, completed_at_ms INTEGER, FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"package_inventory_snapshots", `CREATE TABLE IF NOT EXISTS package_inventory_snapshots (id TEXT PRIMARY KEY, generation INTEGER NOT NULL, user_handle TEXT NOT NULL, source TEXT NOT NULL, snapshot_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL);`},
	{"analysis_results", `CREATE TABLE IF NOT EXISTS analysis_results (id TEXT PRIMARY KEY, scan_id TEXT NOT NULL, kind TEXT NOT NULL, generation INTEGER NOT NULL, result_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, FOREIGN KEY(scan_id) REFERENCES scan_sessions(id) ON DELETE CASCADE);`},
	{"evidence_generations", `CREATE TABLE IF NOT EXISTS evidence_generations (id TEXT PRIMARY KEY, subject TEXT NOT NULL, generation INTEGER NOT NULL, evidence_hash TEXT NOT NULL, created_at_ms INTEGER NOT NULL);`},
	{"problems", `CREATE TABLE IF NOT EXISTS problems (id TEXT PRIMARY KEY, severity TEXT NOT NULL, reason TEXT NOT NULL, subject TEXT NOT NULL, message TEXT NOT NULL, recovery_required INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL);`},
	{"run_history", `CREATE TABLE IF NOT EXISTS run_history (id TEXT PRIMARY KEY, kind TEXT NOT NULL, status TEXT NOT NULL, started_at_ms INTEGER NOT NULL, completed_at_ms INTEGER, summary_json TEXT NOT NULL);`},
	{"runtime_sessions", `CREATE TABLE IF NOT EXISTS runtime_sessions (id TEXT PRIMARY KEY, abi TEXT NOT NULL, executable_path TEXT NOT NULL, readiness_protocol TEXT NOT NULL, process_state TEXT NOT NULL, last_sequence INTEGER NOT NULL DEFAULT 0, started_at_ms INTEGER NOT NULL, last_ready_at_ms INTEGER, died_at_ms INTEGER);`},
	{"shell_routes", `CREATE TABLE IF NOT EXISTS shell_routes (id TEXT PRIMARY KEY, route_id TEXT NOT NULL, entity_id TEXT, generation INTEGER NOT NULL, state TEXT NOT NULL, restored_at_ms INTEGER NOT NULL);`},
	{"root_runtime_identities", `CREATE TABLE IF NOT EXISTS root_runtime_identities (id TEXT PRIMARY KEY, root_namespace TEXT NOT NULL, runtime_identity TEXT NOT NULL, boot_id TEXT NOT NULL, process_id TEXT NOT NULL, started_at_ms INTEGER NOT NULL, scope_json TEXT NOT NULL);`},
	{"privileged_admissions", `CREATE TABLE IF NOT EXISTS privileged_admissions (id TEXT PRIMARY KEY, request_id TEXT NOT NULL UNIQUE, operation TEXT NOT NULL, operation_class TEXT NOT NULL, capability_class TEXT NOT NULL, target_scope TEXT NOT NULL, request_hash TEXT NOT NULL, transport_epoch INTEGER NOT NULL, boot_id TEXT NOT NULL, process_id TEXT NOT NULL, status TEXT NOT NULL, reason TEXT, no_replay_token TEXT NOT NULL, reconcile_required INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL);`},
	{"privileged_events", `CREATE TABLE IF NOT EXISTS privileged_events (admission_id TEXT NOT NULL, sequence INTEGER NOT NULL, event_kind TEXT NOT NULL, reason TEXT, event_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, PRIMARY KEY(admission_id, sequence), FOREIGN KEY(admission_id) REFERENCES privileged_admissions(id) ON DELETE CASCADE);`},
	{"cleanup_actionable_evidence", `CREATE TABLE IF NOT EXISTS cleanup_actionable_evidence (id TEXT PRIMARY KEY, path TEXT NOT NULL, scan_id TEXT NOT NULL, scan_generation INTEGER NOT NULL, source_surface TEXT NOT NULL, source_kind TEXT NOT NULL, expected_hash TEXT, expected_identity TEXT NOT NULL, retained_copy_constraint TEXT, package_name TEXT, user_handle TEXT, bucket TEXT, protected_state TEXT NOT NULL, duplicate_group_id TEXT, rule_id TEXT, recommendation_id TEXT, deletion_eligible INTEGER NOT NULL, evidence_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL);`},
	{"cleanup_manifest_entries", `CREATE TABLE IF NOT EXISTS cleanup_manifest_entries (manifest_id TEXT NOT NULL, path TEXT NOT NULL, exact_descendants_json TEXT NOT NULL, path_role TEXT NOT NULL, protection TEXT NOT NULL, mount_boundary TEXT NOT NULL, identity_evidence TEXT NOT NULL, retained_copy_satisfied INTEGER NOT NULL, warnings_json TEXT NOT NULL, PRIMARY KEY(manifest_id, path), FOREIGN KEY(manifest_id) REFERENCES cleanup_manifests(id) ON DELETE CASCADE);`},
	{"cleanup_capability_claims", `CREATE TABLE IF NOT EXISTS cleanup_capability_claims (capability_id TEXT PRIMARY KEY, operation_id TEXT NOT NULL, manifest_hash TEXT NOT NULL, claim_state TEXT NOT NULL, claimed_at_ms INTEGER, expires_at_ms INTEGER NOT NULL, reconcile_required INTEGER NOT NULL DEFAULT 1, status_lookup_key TEXT NOT NULL, FOREIGN KEY(capability_id) REFERENCES cleanup_capabilities(id) ON DELETE CASCADE);`},
	{"cleanup_mutation_journal", `CREATE TABLE IF NOT EXISTS cleanup_mutation_journal (operation_id TEXT NOT NULL, sequence INTEGER NOT NULL, state TEXT NOT NULL, reason TEXT, path TEXT, mutation_protocol TEXT NOT NULL, created_at_ms INTEGER NOT NULL, PRIMARY KEY(operation_id, sequence), FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"cleanup_verification_results", `CREATE TABLE IF NOT EXISTS cleanup_verification_results (operation_id TEXT NOT NULL, path TEXT NOT NULL, expected_identity TEXT NOT NULL, verified_absent INTEGER NOT NULL, reclaimed_bytes INTEGER NOT NULL DEFAULT 0, stale_after_cleanup INTEGER NOT NULL DEFAULT 1, certainty TEXT NOT NULL, created_at_ms INTEGER NOT NULL, PRIMARY KEY(operation_id, path), FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"cleanup_recovery_status", `CREATE TABLE IF NOT EXISTS cleanup_recovery_status (operation_id TEXT PRIMARY KEY, reconcile_required INTEGER NOT NULL, recovery_state TEXT NOT NULL, no_replay INTEGER NOT NULL DEFAULT 1, legacy_destructive_executor TEXT NOT NULL, last_checked_at_ms INTEGER NOT NULL, FOREIGN KEY(operation_id) REFERENCES operations(id) ON DELETE CASCADE);`},
	{"android_package_evidence_snapshots", `CREATE TABLE IF NOT EXISTS android_package_evidence_snapshots (id TEXT PRIMARY KEY, generation INTEGER NOT NULL, collector TEXT NOT NULL, completeness TEXT NOT NULL, degradation_reasons_json TEXT NOT NULL, evidence_hash TEXT NOT NULL UNIQUE, created_at_ms INTEGER NOT NULL);`},
	{"android_package_records", `CREATE TABLE IF NOT EXISTS android_package_records (snapshot_id TEXT NOT NULL, package_name TEXT NOT NULL, user_id INTEGER NOT NULL, installed INTEGER NOT NULL, archived INTEGER NOT NULL, system_app INTEGER NOT NULL, enabled_state TEXT NOT NULL, completeness TEXT NOT NULL, ui_state TEXT NOT NULL, degradation_reasons_json TEXT NOT NULL, paths_json TEXT NOT NULL, code_bytes_known INTEGER NOT NULL, code_bytes INTEGER, data_bytes_known INTEGER NOT NULL, data_bytes INTEGER, cache_bytes_known INTEGER NOT NULL, cache_bytes INTEGER, native_bytes_known INTEGER NOT NULL, native_bytes INTEGER, PRIMARY KEY(snapshot_id, package_name, user_id), FOREIGN KEY(snapshot_id) REFERENCES android_package_evidence_snapshots(id) ON DELETE CASCADE);`},
	{"android_storage_evidence", `CREATE TABLE IF NOT EXISTS android_storage_evidence (snapshot_id TEXT NOT NULL, package_name TEXT NOT NULL, user_id INTEGER NOT NULL, storage_kind TEXT NOT NULL, path TEXT NOT NULL, completeness TEXT NOT NULL, source_api TEXT NOT NULL, blocked_reason TEXT, PRIMARY KEY(snapshot_id, package_name, user_id, storage_kind, path), FOREIGN KEY(snapshot_id, package_name, user_id) REFERENCES android_package_records(snapshot_id, package_name, user_id) ON DELETE CASCADE);`},
	{"package_residue_candidates", `CREATE TABLE IF NOT EXISTS package_residue_candidates (id TEXT PRIMARY KEY, snapshot_evidence_hash TEXT NOT NULL, package_name TEXT NOT NULL, user_id INTEGER NOT NULL, package_key TEXT NOT NULL, path TEXT NOT NULL, eligibility TEXT NOT NULL, collection_completeness TEXT NOT NULL, blocks_deletion INTEGER NOT NULL, requires_recheck INTEGER NOT NULL, derived_from_regex INTEGER NOT NULL DEFAULT 0, recheck_reasons_json TEXT NOT NULL);`},
	{"package_action_rechecks", `CREATE TABLE IF NOT EXISTS package_action_rechecks (candidate_id TEXT PRIMARY KEY, snapshot_evidence_hash TEXT NOT NULL, package_key TEXT NOT NULL, path TEXT NOT NULL, checked_at_ms INTEGER NOT NULL, result TEXT NOT NULL, mismatch_reason TEXT, FOREIGN KEY(candidate_id) REFERENCES package_residue_candidates(id) ON DELETE CASCADE);`},
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
	for _, required := range []string{"journal_mode = WAL", "expected_generation", "request_fingerprint", "operation_events", "retained_legacy_quarantine_json", "idx_scan_entries_parent_page", "idx_scan_entries_logical_size", "idx_scan_sessions_history", "runtime_sessions", "shell_routes", "root_runtime_identities", "privileged_admissions", "privileged_events", "no_replay_token", "reconcile_required", "cleanup_actionable_evidence", "cleanup_manifest_entries", "cleanup_capability_claims", "manifest_revision", "scan_context_hash", "root_identity", "claim_state", "exact_descendants_json", "cleanup_mutation_journal", "cleanup_verification_results", "cleanup_recovery_status", "mutation_protocol", "verified_absent", "stale_after_cleanup", "legacy_destructive_executor", "no_replay", "android_package_evidence_snapshots", "android_package_records", "android_storage_evidence", "package_residue_candidates", "package_action_rechecks", "evidence_hash", "package_key", "collection_completeness", "requires_recheck", "derived_from_regex"} {
		if !strings.Contains(joined, required) {
			return fmt.Errorf("schema contract missing %s", required)
		}
	}
	return nil
}

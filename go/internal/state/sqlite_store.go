package state

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

const sqliteAuthorityMaxOpenConnections = 1

// SQLiteStore is the durable Go-owned persistence authority used by the
// production Android child process.  Android may transport requests, but it
// does not own this database's domain state.
type SQLiteStore struct {
	db   *sql.DB
	path string
}

type PersistedScanSession struct {
	ID                 string
	Generation         int64
	RootPath           string
	OptionsFingerprint string
	Backend            string
	Status             string
	IntegrityStatus    string
	StartedAtMS        int64
	CompletedAtMS      *int64
	ExpectedGeneration int64
}

type PersistedScanEntry struct {
	ScanID             string
	Path               string
	ParentPath         string
	Name               string
	LogicalSizeBytes   int64
	AllocatedSizeBytes int64
	IsDirectory        bool
	Depth              int
	ModifiedAtMS       int64
	FileCount          int64
}

type CleanupJournalRecord struct {
	OperationID      string
	Sequence         int64
	State            string
	Reason           string
	Path             string
	MutationProtocol string
	CreatedAtMS      int64
}

func OpenSQLiteStore(path string) (*SQLiteStore, error) {
	path = strings.TrimSpace(path)
	if path == "" {
		return nil, errors.New("sqlite_path_required")
	}
	if strings.ContainsRune(path, '\x00') {
		return nil, errors.New("sqlite_path_invalid")
	}
	if dir := filepath.Dir(path); dir != "." {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return nil, fmt.Errorf("create sqlite parent: %w", err)
		}
	}

	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	// The Android runtime protocol is intentionally single-flight: Android serializes
	// requests on GoRuntimeProcess and ServeFrames dispatches one frame at a time.
	// Keep one SQLite connection as a correctness invariant rather than using extra
	// connections to hide accidental query re-entry while Rows/Tx still owns the
	// authority connection. It also keeps connection-local PRAGMAs deterministic.
	db.SetMaxOpenConns(sqliteAuthorityMaxOpenConnections)
	db.SetMaxIdleConns(sqliteAuthorityMaxOpenConnections)
	store := &SQLiteStore{db: db, path: path}
	if err := store.initialize(context.Background()); err != nil {
		db.Close()
		return nil, err
	}
	return store, nil
}

func (s *SQLiteStore) initialize(ctx context.Context) error {
	for _, pragma := range SQLitePragmas {
		if _, err := s.db.ExecContext(ctx, pragma); err != nil {
			return fmt.Errorf("sqlite pragma %q: %w", pragma, err)
		}
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin schema migration: %w", err)
	}
	defer tx.Rollback()
	for _, table := range Tables {
		if _, err := tx.ExecContext(ctx, table.SQL); err != nil {
			return fmt.Errorf("create table %s: %w", table.Name, err)
		}
	}
	for _, migration := range []struct {
		column string
		ddl    string
	}{
		{"preset_id", "ALTER TABLE scan_sessions ADD COLUMN preset_id TEXT"},
		{"options_json", "ALTER TABLE scan_sessions ADD COLUMN options_json TEXT NOT NULL DEFAULT '{}'"},
		{"scanned_items", "ALTER TABLE scan_sessions ADD COLUMN scanned_items INTEGER NOT NULL DEFAULT 0"},
		{"scanned_bytes", "ALTER TABLE scan_sessions ADD COLUMN scanned_bytes INTEGER NOT NULL DEFAULT 0"},
		{"total_bytes", "ALTER TABLE scan_sessions ADD COLUMN total_bytes INTEGER NOT NULL DEFAULT 0"},
		{"warning_count", "ALTER TABLE scan_sessions ADD COLUMN warning_count INTEGER NOT NULL DEFAULT 0"},
		{"current_path", "ALTER TABLE scan_sessions ADD COLUMN current_path TEXT NOT NULL DEFAULT ''"},
		{"error_message", "ALTER TABLE scan_sessions ADD COLUMN error_message TEXT"},
	} {
		present, err := sqliteColumnExists(ctx, tx, "scan_sessions", migration.column)
		if err != nil {
			return err
		}
		if !present {
			if _, err := tx.ExecContext(ctx, migration.ddl); err != nil {
				return fmt.Errorf("migrate scan_sessions.%s: %w", migration.column, err)
			}
		}
	}
	if err := migrateLegacyDuplicateAnalysisAdmissions(ctx, tx); err != nil {
		return err
	}
	present, err := sqliteColumnExists(ctx, tx, "android_package_evidence_snapshots", "scan_id")
	if err != nil {
		return err
	}
	if !present {
		if _, err := tx.ExecContext(ctx, "ALTER TABLE android_package_evidence_snapshots ADD COLUMN scan_id TEXT NOT NULL DEFAULT ''"); err != nil {
			return fmt.Errorf("migrate android_package_evidence_snapshots.scan_id: %w", err)
		}
	}
	for _, stmt := range QueryIndexes {
		if _, err := tx.ExecContext(ctx, stmt); err != nil {
			return fmt.Errorf("create index: %w", err)
		}
	}
	if _, err := tx.ExecContext(ctx,
		"INSERT OR REPLACE INTO schema_metadata(key, value, updated_at_ms) VALUES('schema_version', ?, ?)",
		fmt.Sprint(SchemaVersion), time.Now().UnixMilli(),
	); err != nil {
		return fmt.Errorf("persist schema version: %w", err)
	}
	if _, err := tx.ExecContext(ctx, fmt.Sprintf("PRAGMA user_version = %d", SchemaVersion)); err != nil {
		return fmt.Errorf("set user_version: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit schema migration: %w", err)
	}
	return nil
}

func migrateLegacyDuplicateAnalysisAdmissions(ctx context.Context, tx *sql.Tx) error {
	rows, err := tx.QueryContext(ctx, `SELECT a.operation_id,a.request_id,o.state,o.recovery_required FROM automation_admissions a JOIN operations o ON o.id=a.operation_id WHERE a.automation_kind='analysis' AND a.request_id LIKE 'duplicate-analysis:%'`)
	if err != nil {
		return fmt.Errorf("inspect legacy duplicate-analysis admissions: %w", err)
	}
	type legacyAdmission struct {
		operationID string
		requestID   string
		state       string
		recovery    bool
	}
	var admissions []legacyAdmission
	for rows.Next() {
		var item legacyAdmission
		var recovery int
		if err := rows.Scan(&item.operationID, &item.requestID, &item.state, &recovery); err != nil {
			rows.Close()
			return fmt.Errorf("read legacy duplicate-analysis admission: %w", err)
		}
		item.recovery = recovery != 0
		if legacyDuplicateAnalysisRequest(item.requestID) {
			admissions = append(admissions, item)
		}
	}
	if err := rows.Close(); err != nil {
		return fmt.Errorf("close legacy duplicate-analysis admissions: %w", err)
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("iterate legacy duplicate-analysis admissions: %w", err)
	}
	for _, item := range admissions {
		if _, err := tx.ExecContext(ctx, `UPDATE automation_admissions SET automation_kind='duplicate-analysis' WHERE operation_id=?`, item.operationID); err != nil {
			return fmt.Errorf("migrate duplicate-analysis admission %s: %w", item.operationID, err)
		}
		retry, cancel, reconcile := automationGates("duplicate-analysis", item.state, item.recovery)
		if _, err := tx.ExecContext(ctx, `UPDATE activity_runs SET retry_gate=?,cancel_gate=?,reconcile_gate=? WHERE operation_id=?`, retry, cancel, reconcile, item.operationID); err != nil {
			return fmt.Errorf("migrate duplicate-analysis gates %s: %w", item.operationID, err)
		}
	}
	return nil
}

func sqliteColumnExists(ctx context.Context, tx *sql.Tx, table, column string) (bool, error) {
	rows, err := tx.QueryContext(ctx, "PRAGMA table_info("+table+")")
	if err != nil {
		return false, fmt.Errorf("inspect %s columns: %w", table, err)
	}
	defer rows.Close()
	for rows.Next() {
		var cid int
		var name, columnType string
		var notNull, primaryKey int
		var defaultValue any
		if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &primaryKey); err != nil {
			return false, err
		}
		if name == column {
			return true, nil
		}
	}
	return false, rows.Err()
}

func (s *SQLiteStore) Close() error {
	if s == nil || s.db == nil {
		return nil
	}
	return s.db.Close()
}

func (s *SQLiteStore) Path() string { return s.path }

func (s *SQLiteStore) SchemaVersion(ctx context.Context) (int, error) {
	var version int
	if err := s.db.QueryRowContext(ctx, "PRAGMA user_version").Scan(&version); err != nil {
		return 0, err
	}
	return version, nil
}

// ReplaceScanGeneration atomically replaces one scan generation.  A failed
// entry write rolls the whole generation back, so callers never observe a
// partially-persisted authoritative scan.
func (s *SQLiteStore) ReplaceScanGeneration(ctx context.Context, session PersistedScanSession, entries []PersistedScanEntry) error {
	if session.ID == "" || session.Generation <= 0 || session.RootPath == "" {
		return errors.New("scan_session_identity_required")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if _, err := tx.ExecContext(ctx, "DELETE FROM scan_sessions WHERE id = ?", session.ID); err != nil {
		return err
	}
	var completed any
	if session.CompletedAtMS != nil {
		completed = *session.CompletedAtMS
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO scan_sessions(
			id, generation, root_path, options_fingerprint, backend, status,
			integrity_status, started_at_ms, completed_at_ms, observed_entry_count,
			persisted_entry_count, malformed_event_count, truncated, expected_generation
		) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
		session.ID, session.Generation, session.RootPath, session.OptionsFingerprint,
		session.Backend, session.Status, session.IntegrityStatus, session.StartedAtMS,
		completed, len(entries), len(entries), 0, 0, session.ExpectedGeneration,
	); err != nil {
		return fmt.Errorf("insert scan session: %w", err)
	}
	for _, entry := range entries {
		if entry.ScanID == "" {
			entry.ScanID = session.ID
		}
		if entry.ScanID != session.ID || entry.Path == "" {
			return errors.New("scan_entry_identity_required")
		}
		isDir := 0
		if entry.IsDirectory {
			isDir = 1
		}
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO scan_entries(
				scan_id, path, parent_path, name, logical_size_bytes,
				allocated_size_bytes, is_directory, depth, modified_at_ms, file_count
			) VALUES(?,?,?,?,?,?,?,?,?,?)`,
			entry.ScanID, entry.Path, entry.ParentPath, entry.Name,
			entry.LogicalSizeBytes, entry.AllocatedSizeBytes, isDir, entry.Depth,
			entry.ModifiedAtMS, entry.FileCount,
		); err != nil {
			return fmt.Errorf("insert scan entry %s: %w", entry.Path, err)
		}
	}
	return tx.Commit()
}

func (s *SQLiteStore) LoadScanGeneration(ctx context.Context, id string) (PersistedScanSession, []PersistedScanEntry, error) {
	var session PersistedScanSession
	var completed sql.NullInt64
	err := s.db.QueryRowContext(ctx, `
		SELECT id, generation, root_path, options_fingerprint, backend, status,
		       integrity_status, started_at_ms, completed_at_ms, expected_generation
		  FROM scan_sessions WHERE id = ?`, id,
	).Scan(
		&session.ID, &session.Generation, &session.RootPath, &session.OptionsFingerprint,
		&session.Backend, &session.Status, &session.IntegrityStatus, &session.StartedAtMS,
		&completed, &session.ExpectedGeneration,
	)
	if err != nil {
		return PersistedScanSession{}, nil, err
	}
	if completed.Valid {
		v := completed.Int64
		session.CompletedAtMS = &v
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT scan_id, path, parent_path, name, logical_size_bytes,
		       allocated_size_bytes, is_directory, depth, modified_at_ms, file_count
		  FROM scan_entries WHERE scan_id = ? ORDER BY path`, id)
	if err != nil {
		return PersistedScanSession{}, nil, err
	}
	defer rows.Close()

	var entries []PersistedScanEntry
	for rows.Next() {
		var e PersistedScanEntry
		var isDir int
		if err := rows.Scan(
			&e.ScanID, &e.Path, &e.ParentPath, &e.Name, &e.LogicalSizeBytes,
			&e.AllocatedSizeBytes, &isDir, &e.Depth, &e.ModifiedAtMS, &e.FileCount,
		); err != nil {
			return PersistedScanSession{}, nil, err
		}
		e.IsDirectory = isDir != 0
		entries = append(entries, e)
	}
	return session, entries, rows.Err()
}

// RecordCleanupMutation persists the durable cleanup operation and its mutation
// journal entry in one transaction.
func (s *SQLiteStore) RecordCleanupMutation(ctx context.Context, record CleanupJournalRecord, generation int64) error {
	if record.OperationID == "" || record.Sequence <= 0 || generation <= 0 || record.MutationProtocol == "" {
		return errors.New("cleanup_journal_identity_required")
	}
	if record.CreatedAtMS == 0 {
		record.CreatedAtMS = time.Now().UnixMilli()
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	requestID := "journal:" + record.OperationID
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO operations(
			id, request_id, request_fingerprint, generation, state,
			last_sequence, recovery_required, created_at_ms, updated_at_ms
		) VALUES(?,?,?,?,?,?,?,?,?)
		ON CONFLICT(id) DO UPDATE SET
			state=excluded.state,
			last_sequence=MAX(operations.last_sequence, excluded.last_sequence),
			updated_at_ms=excluded.updated_at_ms`,
		record.OperationID, requestID, requestID, generation, record.State,
		record.Sequence, 0, record.CreatedAtMS, record.CreatedAtMS,
	); err != nil {
		return fmt.Errorf("persist operation: %w", err)
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO cleanup_mutation_journal(
			operation_id, sequence, state, reason, path, mutation_protocol, created_at_ms
		) VALUES(?,?,?,?,?,?,?)`,
		record.OperationID, record.Sequence, record.State, record.Reason, record.Path,
		record.MutationProtocol, record.CreatedAtMS,
	); err != nil {
		return fmt.Errorf("persist cleanup journal: %w", err)
	}
	return tx.Commit()
}

func (s *SQLiteStore) CleanupJournal(ctx context.Context, operationID string) ([]CleanupJournalRecord, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT operation_id, sequence, state, COALESCE(reason,''), COALESCE(path,''),
		       mutation_protocol, created_at_ms
		  FROM cleanup_mutation_journal
		 WHERE operation_id = ?
		 ORDER BY sequence`, operationID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []CleanupJournalRecord
	for rows.Next() {
		var r CleanupJournalRecord
		if err := rows.Scan(&r.OperationID, &r.Sequence, &r.State, &r.Reason, &r.Path, &r.MutationProtocol, &r.CreatedAtMS); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

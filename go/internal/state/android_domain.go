package state

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strconv"
	"strings"
)

// AndroidScanSession is the wire/storage model used by the Android shell after
// REM-03. JSON field names intentionally mirror the legacy Kotlin entity so the
// cutover can preserve the app-facing model while moving persistence authority.
type AndroidScanSession struct {
	ID                  string  `json:"id"`
	PresetID            *string `json:"presetId,omitempty"`
	RootPath            string  `json:"rootPath"`
	OptionsJSON         string  `json:"optionsJson"`
	Status              string  `json:"status"`
	Backend             string  `json:"backend"`
	StartedAt           int64   `json:"startedAt"`
	CompletedAt         *int64  `json:"completedAt,omitempty"`
	ScannedItems        int64   `json:"scannedItems"`
	ScannedBytes        int64   `json:"scannedBytes"`
	TotalBytes          int64   `json:"totalBytes"`
	WarningCount        int64   `json:"warningCount"`
	CurrentPath         string  `json:"currentPath"`
	ErrorMessage        *string `json:"errorMessage,omitempty"`
	Generation          int64   `json:"generation"`
	ObservedEntryCount  int64   `json:"observedEntryCount"`
	PersistedEntryCount int64   `json:"persistedEntryCount"`
	MalformedEventCount int64   `json:"malformedEventCount"`
	Truncated           bool    `json:"truncated"`
	IntegrityStatus     string  `json:"integrityStatus"`
}

type AndroidScanEntry struct {
	ScanID         string `json:"scanId"`
	Path           string `json:"path"`
	ParentPath     string `json:"parentPath"`
	Name           string `json:"name"`
	SizeBytes      int64  `json:"sizeBytes"`
	AllocatedBytes int64  `json:"allocatedBytes"`
	IsDirectory    bool   `json:"isDirectory"`
	Depth          int    `json:"depth"`
	ModifiedAt     int64  `json:"modifiedAt"`
	FileCount      int64  `json:"fileCount"`
}

type AndroidCleanupOperation struct {
	ID               string  `json:"id"`
	ScanID           string  `json:"scanId"`
	RootPath         string  `json:"rootPath"`
	Status           string  `json:"status"`
	SelectedCount    int     `json:"selectedCount"`
	PlannedBytes     int64   `json:"plannedBytes"`
	DeletedBytes     int64   `json:"deletedBytes"`
	CreatedAt        int64   `json:"createdAt"`
	CompletedAt      *int64  `json:"completedAt,omitempty"`
	Message          *string `json:"message,omitempty"`
	ReviewID         string  `json:"reviewId"`
	ScanGeneration   int64   `json:"scanGeneration"`
	PlanHash         string  `json:"planHash"`
	ExpiresAt        int64   `json:"expiresAt"`
	OutcomeCertainty string  `json:"outcomeCertainty"`
}

type AndroidCleanupPlanItem struct {
	OperationID        string  `json:"operationId"`
	Path               string  `json:"path"`
	Role               string  `json:"role"`
	ItemType           string  `json:"itemType"`
	PlannedBytes       int64   `json:"plannedBytes"`
	ExpectedDevice     *int64  `json:"expectedDevice,omitempty"`
	ExpectedMountID    *string `json:"expectedMountId,omitempty"`
	ExpectedInode      *int64  `json:"expectedInode,omitempty"`
	ExpectedMode       *int    `json:"expectedMode,omitempty"`
	ExpectedStatSize   *int64  `json:"expectedStatSizeBytes,omitempty"`
	ExpectedModifiedAt *int64  `json:"expectedModifiedAt,omitempty"`
	ExpectedTreeDigest *string `json:"expectedTreeDigest,omitempty"`
	ExpectedEntryCount *int64  `json:"expectedEntryCount,omitempty"`
	ExpectedSHA256     *string `json:"expectedSha256,omitempty"`
	RetainedPath       *string `json:"retainedPath,omitempty"`
	RetainedSHA256     *string `json:"retainedSha256,omitempty"`
	PackageName        *string `json:"packageName,omitempty"`
	CreatedAt          int64   `json:"createdAt"`
}

func (s *SQLiteStore) UpsertAndroidScanSession(ctx context.Context, v AndroidScanSession) error {
	if v.ID == "" || v.RootPath == "" || v.Generation <= 0 {
		return errors.New("android_scan_session_identity_required")
	}
	truncated := 0
	if v.Truncated {
		truncated = 1
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO scan_sessions(
			id, room_scan_id, generation, root_path, preset_id, options_json,
			options_fingerprint, backend, status, integrity_status, started_at_ms,
			completed_at_ms, scanned_items, scanned_bytes, total_bytes, warning_count,
			current_path, error_message, observed_entry_count, persisted_entry_count,
			malformed_event_count, truncated, expected_generation
		) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
		ON CONFLICT(id) DO UPDATE SET
			generation=excluded.generation, root_path=excluded.root_path,
			preset_id=excluded.preset_id, options_json=excluded.options_json,
			options_fingerprint=excluded.options_fingerprint, backend=excluded.backend,
			status=excluded.status, integrity_status=excluded.integrity_status,
			started_at_ms=excluded.started_at_ms, completed_at_ms=excluded.completed_at_ms,
			scanned_items=excluded.scanned_items, scanned_bytes=excluded.scanned_bytes,
			total_bytes=excluded.total_bytes, warning_count=excluded.warning_count,
			current_path=excluded.current_path, error_message=excluded.error_message,
			observed_entry_count=excluded.observed_entry_count,
			persisted_entry_count=excluded.persisted_entry_count,
			malformed_event_count=excluded.malformed_event_count,
			truncated=excluded.truncated, expected_generation=excluded.expected_generation`,
		v.ID, nil, v.Generation, v.RootPath, v.PresetID, v.OptionsJSON,
		v.OptionsJSON, v.Backend, v.Status, v.IntegrityStatus, v.StartedAt,
		v.CompletedAt, v.ScannedItems, v.ScannedBytes, v.TotalBytes, v.WarningCount,
		v.CurrentPath, v.ErrorMessage, v.ObservedEntryCount, v.PersistedEntryCount,
		v.MalformedEventCount, truncated, v.Generation,
	)
	return err
}

func (s *SQLiteStore) DeleteAndroidScanEntries(ctx context.Context, scanID string) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM scan_entries WHERE scan_id = ?", scanID)
	return err
}

func (s *SQLiteStore) UpsertAndroidScanEntries(ctx context.Context, entries []AndroidScanEntry) error {
	if len(entries) == 0 {
		return nil
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	stmt, err := tx.PrepareContext(ctx, `
		INSERT INTO scan_entries(scan_id,path,parent_path,name,logical_size_bytes,allocated_size_bytes,is_directory,depth,modified_at_ms,file_count)
		VALUES(?,?,?,?,?,?,?,?,?,?)
		ON CONFLICT(scan_id,path) DO UPDATE SET
			parent_path=excluded.parent_path,name=excluded.name,logical_size_bytes=excluded.logical_size_bytes,
			allocated_size_bytes=excluded.allocated_size_bytes,is_directory=excluded.is_directory,
			depth=excluded.depth,modified_at_ms=excluded.modified_at_ms,file_count=excluded.file_count`)
	if err != nil {
		return err
	}
	defer stmt.Close()
	for _, e := range entries {
		if e.ScanID == "" || e.Path == "" {
			return errors.New("android_scan_entry_identity_required")
		}
		isDir := 0
		if e.IsDirectory {
			isDir = 1
		}
		if _, err := stmt.ExecContext(ctx, e.ScanID, e.Path, e.ParentPath, e.Name, e.SizeBytes, e.AllocatedBytes, isDir, e.Depth, e.ModifiedAt, e.FileCount); err != nil {
			return err
		}
	}
	return tx.Commit()
}

const androidScanSessionColumns = `id,preset_id,root_path,options_json,status,backend,started_at_ms,completed_at_ms,scanned_items,scanned_bytes,total_bytes,warning_count,current_path,error_message,generation,observed_entry_count,persisted_entry_count,malformed_event_count,truncated,integrity_status`

func scanAndroidSession(scanner interface{ Scan(...any) error }) (AndroidScanSession, error) {
	var v AndroidScanSession
	var preset, errorMessage sql.NullString
	var completed sql.NullInt64
	var truncated int
	if err := scanner.Scan(&v.ID, &preset, &v.RootPath, &v.OptionsJSON, &v.Status, &v.Backend, &v.StartedAt, &completed, &v.ScannedItems, &v.ScannedBytes, &v.TotalBytes, &v.WarningCount, &v.CurrentPath, &errorMessage, &v.Generation, &v.ObservedEntryCount, &v.PersistedEntryCount, &v.MalformedEventCount, &truncated, &v.IntegrityStatus); err != nil {
		return AndroidScanSession{}, err
	}
	if preset.Valid {
		v.PresetID = &preset.String
	}
	if errorMessage.Valid {
		v.ErrorMessage = &errorMessage.String
	}
	if completed.Valid {
		v.CompletedAt = &completed.Int64
	}
	v.Truncated = truncated != 0
	return v, nil
}

func (s *SQLiteStore) AndroidScanSessionByID(ctx context.Context, id string) (*AndroidScanSession, error) {
	row := s.db.QueryRowContext(ctx, "SELECT "+androidScanSessionColumns+" FROM scan_sessions WHERE id=? LIMIT 1", id)
	v, err := scanAndroidSession(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &v, err
}

func (s *SQLiteStore) LatestAndroidScanSession(ctx context.Context) (*AndroidScanSession, error) {
	row := s.db.QueryRowContext(ctx, "SELECT "+androidScanSessionColumns+" FROM scan_sessions ORDER BY started_at_ms DESC LIMIT 1")
	v, err := scanAndroidSession(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &v, err
}

func (s *SQLiteStore) CompletedAndroidScanSessions(ctx context.Context, limit int) ([]AndroidScanSession, error) {
	if limit <= 0 {
		return []AndroidScanSession{}, nil
	}
	rows, err := s.db.QueryContext(ctx, "SELECT "+androidScanSessionColumns+" FROM scan_sessions WHERE status='COMPLETED' ORDER BY completed_at_ms DESC LIMIT ?", limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]AndroidScanSession, 0, limit)
	for rows.Next() {
		v, err := scanAndroidSession(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) AndroidScanEntriesPage(ctx context.Context, scanID string, offset, limit int) ([]AndroidScanEntry, error) {
	if offset < 0 {
		offset = 0
	}
	if limit <= 0 || limit > 1000 {
		limit = 512
	}
	rows, err := s.db.QueryContext(ctx, `SELECT scan_id,path,parent_path,name,logical_size_bytes,allocated_size_bytes,is_directory,depth,modified_at_ms,file_count FROM scan_entries WHERE scan_id=? ORDER BY logical_size_bytes DESC,path COLLATE NOCASE LIMIT ? OFFSET ?`, scanID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]AndroidScanEntry, 0, limit)
	for rows.Next() {
		var v AndroidScanEntry
		var isDir int
		if err := rows.Scan(&v.ScanID, &v.Path, &v.ParentPath, &v.Name, &v.SizeBytes, &v.AllocatedBytes, &isDir, &v.Depth, &v.ModifiedAt, &v.FileCount); err != nil {
			return nil, err
		}
		v.IsDirectory = isDir != 0
		out = append(out, v)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) PruneCompletedAndroidScans(ctx context.Context, keep int) error {
	if keep < 1 {
		keep = 1
	}
	_, err := s.db.ExecContext(ctx, `DELETE FROM scan_sessions WHERE id IN (SELECT id FROM scan_sessions WHERE status='COMPLETED' ORDER BY completed_at_ms DESC LIMIT -1 OFFSET ?)`, keep)
	return err
}

func (s *SQLiteStore) InterruptActiveAndroidScans(ctx context.Context, completedAt int64) (int64, error) {
	result, err := s.db.ExecContext(ctx, `UPDATE scan_sessions SET status='INTERRUPTED',completed_at_ms=?,integrity_status='INTERRUPTED',error_message='Scan was interrupted before reaching a terminal event' WHERE status IN ('STARTING','RUNNING','CANCELLING')`, completedAt)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

func (s *SQLiteStore) UpsertAndroidCleanupOperation(ctx context.Context, v AndroidCleanupOperation) error {
	if v.ID == "" || v.ScanID == "" {
		return errors.New("android_cleanup_operation_identity_required")
	}
	_, err := s.db.ExecContext(ctx, `INSERT INTO android_cleanup_operations(id,scan_id,root_path,status,selected_count,planned_bytes,deleted_bytes,created_at_ms,completed_at_ms,message,review_id,scan_generation,plan_hash,expires_at_ms,outcome_certainty) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET scan_id=excluded.scan_id,root_path=excluded.root_path,status=excluded.status,selected_count=excluded.selected_count,planned_bytes=excluded.planned_bytes,deleted_bytes=excluded.deleted_bytes,created_at_ms=excluded.created_at_ms,completed_at_ms=excluded.completed_at_ms,message=excluded.message,review_id=excluded.review_id,scan_generation=excluded.scan_generation,plan_hash=excluded.plan_hash,expires_at_ms=excluded.expires_at_ms,outcome_certainty=excluded.outcome_certainty`, v.ID, v.ScanID, v.RootPath, v.Status, v.SelectedCount, v.PlannedBytes, v.DeletedBytes, v.CreatedAt, v.CompletedAt, v.Message, v.ReviewID, v.ScanGeneration, v.PlanHash, v.ExpiresAt, v.OutcomeCertainty)
	return err
}

func (s *SQLiteStore) InsertAndroidCleanupOperationIfAbsent(ctx context.Context, v AndroidCleanupOperation) (bool, error) {
	result, err := s.db.ExecContext(ctx, `INSERT OR IGNORE INTO android_cleanup_operations(id,scan_id,root_path,status,selected_count,planned_bytes,deleted_bytes,created_at_ms,completed_at_ms,message,review_id,scan_generation,plan_hash,expires_at_ms,outcome_certainty) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`, v.ID, v.ScanID, v.RootPath, v.Status, v.SelectedCount, v.PlannedBytes, v.DeletedBytes, v.CreatedAt, v.CompletedAt, v.Message, v.ReviewID, v.ScanGeneration, v.PlanHash, v.ExpiresAt, v.OutcomeCertainty)
	if err != nil {
		return false, err
	}
	n, err := result.RowsAffected()
	return n == 1, err
}

func (s *SQLiteStore) ReplaceAndroidCleanupPlan(ctx context.Context, operationID string, items []AndroidCleanupPlanItem) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, "DELETE FROM android_cleanup_plan_items WHERE operation_id=?", operationID); err != nil {
		return err
	}
	for _, v := range items {
		if v.OperationID != operationID || v.Path == "" {
			return errors.New("android_cleanup_plan_identity_required")
		}
		_, err := tx.ExecContext(ctx, `INSERT INTO android_cleanup_plan_items(operation_id,path,role,item_type,planned_bytes,expected_device,expected_mount_id,expected_inode,expected_mode,expected_stat_size_bytes,expected_modified_at_ms,expected_tree_digest,expected_entry_count,expected_sha256,retained_path,retained_sha256,package_name,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`, v.OperationID, v.Path, v.Role, v.ItemType, v.PlannedBytes, v.ExpectedDevice, v.ExpectedMountID, v.ExpectedInode, v.ExpectedMode, v.ExpectedStatSize, v.ExpectedModifiedAt, v.ExpectedTreeDigest, v.ExpectedEntryCount, v.ExpectedSHA256, v.RetainedPath, v.RetainedSHA256, v.PackageName, v.CreatedAt)
		if err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *SQLiteStore) ClaimAndroidCleanupOperation(ctx context.Context, id string) (int64, error) {
	result, err := s.db.ExecContext(ctx, `UPDATE android_cleanup_operations SET status='EXECUTING',outcome_certainty='CERTAIN' WHERE id=? AND status='REVIEWED'`, id)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

func scanAndroidCleanupOperation(scanner interface{ Scan(...any) error }) (AndroidCleanupOperation, error) {
	var v AndroidCleanupOperation
	var completed sql.NullInt64
	var message sql.NullString
	if err := scanner.Scan(&v.ID, &v.ScanID, &v.RootPath, &v.Status, &v.SelectedCount, &v.PlannedBytes, &v.DeletedBytes, &v.CreatedAt, &completed, &message, &v.ReviewID, &v.ScanGeneration, &v.PlanHash, &v.ExpiresAt, &v.OutcomeCertainty); err != nil {
		return v, err
	}
	if completed.Valid {
		v.CompletedAt = &completed.Int64
	}
	if message.Valid {
		v.Message = &message.String
	}
	return v, nil
}

const androidCleanupColumns = `id,scan_id,root_path,status,selected_count,planned_bytes,deleted_bytes,created_at_ms,completed_at_ms,message,review_id,scan_generation,plan_hash,expires_at_ms,outcome_certainty`

func (s *SQLiteStore) AndroidCleanupOperations(ctx context.Context, limit int, status string) ([]AndroidCleanupOperation, error) {
	if limit <= 0 {
		limit = 50
	}
	query := "SELECT " + androidCleanupColumns + " FROM android_cleanup_operations"
	args := []any{}
	if status != "" {
		query += " WHERE status=?"
		args = append(args, status)
	}
	query += " ORDER BY created_at_ms DESC LIMIT ?"
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]AndroidCleanupOperation, 0)
	for rows.Next() {
		v, err := scanAndroidCleanupOperation(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) VerifyAndroidDomainSchema(ctx context.Context) error {
	for _, table := range []string{"scan_sessions", "scan_entries", "android_cleanup_operations", "android_cleanup_plan_items"} {
		var name string
		if err := s.db.QueryRowContext(ctx, "SELECT name FROM sqlite_master WHERE type='table' AND name=?", table).Scan(&name); err != nil {
			return fmt.Errorf("android domain table %s: %w", table, err)
		}
	}
	return nil
}

type AndroidDuplicateSizeBucket struct {
	SizeBytes      int64 `json:"sizeBytes"`
	CandidateCount int   `json:"candidateCount"`
}

type AndroidSearchRequest struct {
	Query       string  `json:"query"`
	EntryType   string  `json:"type"`
	MinBytes    int64   `json:"minBytes"`
	MaxBytes    *int64  `json:"maxBytes,omitempty"`
	Category    *string `json:"category,omitempty"`
	PackageName *string `json:"packageName,omitempty"`
	Extension   *string `json:"extension,omitempty"`
	Limit       int     `json:"limit"`
}

type AndroidSearchResult struct {
	Path        string  `json:"path"`
	ParentPath  string  `json:"parentPath"`
	Name        string  `json:"name"`
	SizeBytes   int64   `json:"sizeBytes"`
	ModifiedAt  int64   `json:"modifiedAt"`
	IsDirectory bool    `json:"isDirectory"`
	Category    string  `json:"category"`
	PackageName *string `json:"packageName,omitempty"`
}

const androidScanEntryColumns = `scan_id,path,parent_path,name,logical_size_bytes,allocated_size_bytes,is_directory,depth,modified_at_ms,file_count`

func scanAndroidEntry(scanner interface{ Scan(...any) error }) (AndroidScanEntry, error) {
	var v AndroidScanEntry
	var isDir int
	if err := scanner.Scan(&v.ScanID, &v.Path, &v.ParentPath, &v.Name, &v.SizeBytes, &v.AllocatedBytes, &isDir, &v.Depth, &v.ModifiedAt, &v.FileCount); err != nil {
		return AndroidScanEntry{}, err
	}
	v.IsDirectory = isDir != 0
	return v, nil
}

func (s *SQLiteStore) AndroidScanEntryByPath(ctx context.Context, scanID, path string) (*AndroidScanEntry, error) {
	row := s.db.QueryRowContext(ctx, "SELECT "+androidScanEntryColumns+" FROM scan_entries WHERE scan_id=? AND path=? LIMIT 1", scanID, path)
	v, err := scanAndroidEntry(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &v, err
}

func (s *SQLiteStore) AndroidScanEntriesByPaths(ctx context.Context, scanID string, paths []string) ([]AndroidScanEntry, error) {
	if len(paths) == 0 {
		return []AndroidScanEntry{}, nil
	}
	if len(paths) > 500 {
		return nil, errors.New("android_entries_by_paths_limit_exceeded")
	}
	args := make([]any, 0, len(paths)+1)
	args = append(args, scanID)
	marks := make([]string, 0, len(paths))
	for _, path := range paths {
		marks = append(marks, "?")
		args = append(args, path)
	}
	query := "SELECT " + androidScanEntryColumns + " FROM scan_entries WHERE scan_id=? AND path IN (" + strings.Join(marks, ",") + ") ORDER BY logical_size_bytes DESC,path COLLATE NOCASE"
	return s.androidScanEntriesQuery(ctx, query, args...)
}

func (s *SQLiteStore) AndroidFilesAtLeast(ctx context.Context, scanID string, minBytes int64) ([]AndroidScanEntry, error) {
	return s.androidScanEntriesQuery(ctx, "SELECT "+androidScanEntryColumns+" FROM scan_entries WHERE scan_id=? AND is_directory=0 AND logical_size_bytes>=? ORDER BY logical_size_bytes DESC,path COLLATE NOCASE", scanID, minBytes)
}

func (s *SQLiteStore) AndroidFilesByExtensions(ctx context.Context, scanID string, extensions []string) ([]AndroidScanEntry, error) {
	if len(extensions) == 0 {
		return []AndroidScanEntry{}, nil
	}
	if len(extensions) > 32 {
		return nil, errors.New("android_extension_filter_limit_exceeded")
	}
	args := []any{scanID}
	clauses := make([]string, 0, len(extensions))
	for _, ext := range extensions {
		ext = strings.TrimPrefix(strings.ToLower(strings.TrimSpace(ext)), ".")
		if ext == "" {
			continue
		}
		clauses = append(clauses, "LOWER(name) LIKE ?")
		args = append(args, "%."+ext)
	}
	if len(clauses) == 0 {
		return []AndroidScanEntry{}, nil
	}
	query := "SELECT " + androidScanEntryColumns + " FROM scan_entries WHERE scan_id=? AND is_directory=0 AND (" + strings.Join(clauses, " OR ") + ") ORDER BY logical_size_bytes DESC,path COLLATE NOCASE"
	return s.androidScanEntriesQuery(ctx, query, args...)
}

func (s *SQLiteStore) AndroidDuplicateSizeBuckets(ctx context.Context, scanID string, minBytes int64) ([]AndroidDuplicateSizeBucket, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT logical_size_bytes,COUNT(*) FROM scan_entries WHERE scan_id=? AND is_directory=0 AND logical_size_bytes>=? GROUP BY logical_size_bytes HAVING COUNT(*)>1 ORDER BY (logical_size_bytes*(COUNT(*)-1)) DESC,logical_size_bytes DESC`, scanID, minBytes)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []AndroidDuplicateSizeBucket{}
	for rows.Next() {
		var v AndroidDuplicateSizeBucket
		if err := rows.Scan(&v.SizeBytes, &v.CandidateCount); err != nil {
			return nil, err
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) AndroidDuplicateFilesBySize(ctx context.Context, scanID string, sizeBytes int64, limit int) ([]AndroidScanEntry, error) {
	if limit <= 0 {
		return []AndroidScanEntry{}, nil
	}
	if limit > 5000 {
		limit = 5000
	}
	return s.androidScanEntriesQuery(ctx, "SELECT "+androidScanEntryColumns+" FROM scan_entries WHERE scan_id=? AND is_directory=0 AND logical_size_bytes=? ORDER BY path COLLATE NOCASE LIMIT ?", scanID, sizeBytes, limit)
}

func (s *SQLiteStore) androidScanEntriesQuery(ctx context.Context, query string, args ...any) ([]AndroidScanEntry, error) {
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []AndroidScanEntry{}
	for rows.Next() {
		v, err := scanAndroidEntry(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

type normalizedAndroidSearch struct {
	term        string
	entryType   string
	minBytes    int64
	maxBytes    *int64
	category    string
	packageName string
	extension   string
	limit       int
}

func normalizeAndroidSearch(req AndroidSearchRequest) normalizedAndroidSearch {
	out := normalizedAndroidSearch{
		entryType: strings.ToUpper(strings.TrimSpace(req.EntryType)),
		minBytes:  req.MinBytes,
		maxBytes:  req.MaxBytes,
		limit:     req.Limit,
	}
	if out.entryType == "" {
		out.entryType = "ANY"
	}
	if out.limit < 1 {
		out.limit = 1
	}
	if out.limit > 1000 {
		out.limit = 1000
	}
	if req.Category != nil {
		out.category = strings.ToUpper(strings.TrimSpace(*req.Category))
	}
	if req.PackageName != nil {
		out.packageName = strings.TrimSpace(*req.PackageName)
	}
	if req.Extension != nil {
		out.extension = strings.TrimPrefix(strings.ToLower(strings.TrimSpace(*req.Extension)), ".")
	}
	terms := []string{}
	for _, token := range strings.Fields(req.Query) {
		lower := strings.ToLower(token)
		switch {
		case strings.HasPrefix(lower, "*.") && len(token) > 2:
			out.extension = strings.TrimPrefix(strings.ToLower(token[2:]), ".")
		case strings.HasPrefix(lower, "ext:"):
			out.extension = strings.TrimPrefix(strings.ToLower(strings.TrimSpace(token[len("ext:"):])), ".")
		case strings.HasPrefix(lower, "package:"):
			value := strings.TrimSpace(token[len("package:"):])
			if value != "" {
				out.packageName = value
			}
		case strings.HasPrefix(token, ">"):
			if value, ok := parseAndroidSearchSize(token[1:]); ok && value > out.minBytes {
				out.minBytes = value
			}
		default:
			terms = append(terms, token)
		}
	}
	out.term = strings.ToLower(strings.Join(terms, " "))
	return out
}

func parseAndroidSearchSize(raw string) (int64, bool) {
	raw = strings.ToLower(strings.TrimSpace(raw))
	multiplier := int64(1)
	for _, suffix := range []struct {
		text string
		mul  int64
	}{{"tb", 1 << 40}, {"t", 1 << 40}, {"gb", 1 << 30}, {"g", 1 << 30}, {"mb", 1 << 20}, {"m", 1 << 20}, {"kb", 1 << 10}, {"k", 1 << 10}, {"b", 1}} {
		if strings.HasSuffix(raw, suffix.text) {
			multiplier = suffix.mul
			raw = strings.TrimSuffix(raw, suffix.text)
			break
		}
	}
	value, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || value < 0 || (multiplier > 1 && value > (1<<63-1)/multiplier) {
		return 0, false
	}
	return value * multiplier, true
}

func (s *SQLiteStore) SearchAndroidScanEntries(ctx context.Context, scanID string, request AndroidSearchRequest) ([]AndroidSearchResult, error) {
	filter := normalizeAndroidSearch(request)
	query := "SELECT " + androidScanEntryColumns + " FROM scan_entries WHERE scan_id=?"
	args := []any{scanID}
	switch filter.entryType {
	case "FILES":
		query += " AND is_directory=0"
	case "DIRECTORIES":
		query += " AND is_directory=1"
	}
	if filter.minBytes > 0 {
		query += " AND logical_size_bytes>=?"
		args = append(args, filter.minBytes)
	}
	if filter.maxBytes != nil {
		query += " AND logical_size_bytes<=?"
		args = append(args, *filter.maxBytes)
	}
	query += " ORDER BY logical_size_bytes DESC,path COLLATE NOCASE"
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]AndroidSearchResult, 0, filter.limit)
	for rows.Next() {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		entry, err := scanAndroidEntry(rows)
		if err != nil {
			return nil, err
		}
		lowerPath := strings.ToLower(entry.Path)
		lowerName := strings.ToLower(entry.Name)
		if filter.term != "" && !strings.Contains(lowerPath, filter.term) && !strings.Contains(lowerName, filter.term) {
			continue
		}
		if filter.extension != "" && (entry.IsDirectory || !strings.HasSuffix(lowerName, "."+filter.extension)) {
			continue
		}
		category := androidClassifyPath(entry.Path)
		if filter.category != "" && category != filter.category {
			continue
		}
		packageName := androidPackageFromPath(entry.Path)
		if filter.packageName != "" && (packageName == nil || *packageName != filter.packageName) {
			continue
		}
		out = append(out, AndroidSearchResult{
			Path: entry.Path, ParentPath: entry.ParentPath, Name: entry.Name, SizeBytes: entry.SizeBytes,
			ModifiedAt: entry.ModifiedAt, IsDirectory: entry.IsDirectory, Category: category, PackageName: packageName,
		})
		if len(out) >= filter.limit {
			break
		}
	}
	return out, rows.Err()
}

func androidPackageFromPath(path string) *string {
	parts := strings.Split(strings.Trim(path, "/"), "/")
	var candidate string
	switch {
	case len(parts) >= 4 && parts[0] == "data" && (parts[1] == "user" || parts[1] == "user_de"):
		candidate = parts[3]
	case len(parts) >= 3 && parts[0] == "data" && parts[1] == "data":
		candidate = parts[2]
	case len(parts) >= 6 && parts[0] == "storage" && parts[1] == "emulated" && parts[3] == "Android" && (parts[4] == "data" || parts[4] == "media" || parts[4] == "obb"):
		candidate = parts[5]
	case len(parts) >= 3 && parts[0] == "data" && parts[1] == "app":
		index := 2
		if strings.HasPrefix(parts[index], "~~") && len(parts) >= 4 {
			index++
		}
		candidate = strings.SplitN(parts[index], "-", 2)[0]
	}
	if candidate == "" {
		return nil
	}
	value := candidate
	return &value
}

func androidClassifyPath(path string) string {
	lower := strings.ToLower(path)
	switch {
	case strings.HasPrefix(lower, "/data/adb/modules/"):
		return "ROOT_MODULE"
	case strings.Contains(lower, "/cache/") || strings.HasSuffix(lower, "/cache"):
		return "CACHE"
	case strings.Contains(lower, "/code_cache/") || strings.HasSuffix(lower, "/code_cache"):
		return "CODE_CACHE"
	case strings.Contains(lower, "/databases/") || strings.HasSuffix(lower, ".db") || strings.HasSuffix(lower, ".sqlite"):
		return "DATABASE"
	case strings.Contains(lower, "/shared_prefs/") || (strings.HasSuffix(lower, ".xml") && strings.Contains(lower, "/data/")):
		return "PREFERENCES"
	case strings.HasSuffix(lower, ".apk") || strings.HasPrefix(lower, "/data/app/"):
		return "APK"
	case strings.Contains(lower, "/android/obb/"):
		return "OBB"
	case strings.HasPrefix(lower, "/storage/emulated/") && (strings.Contains(lower, "/dcim/") || strings.Contains(lower, "/pictures/") || strings.Contains(lower, "/movies/") || strings.Contains(lower, "/music/")):
		return "MEDIA"
	case strings.Contains(lower, "/download/") || strings.HasSuffix(lower, "/download"):
		return "DOWNLOAD"
	case strings.HasSuffix(lower, ".log") || strings.HasPrefix(lower, "/data/tombstones") || strings.HasPrefix(lower, "/data/anr"):
		return "LOG"
	case strings.HasSuffix(lower, ".tmp") || strings.HasSuffix(lower, ".temp"):
		return "TEMPORARY"
	case androidPackageFromPath(path) != nil:
		return "APP_DATA"
	case strings.HasPrefix(lower, "/system") || strings.HasPrefix(lower, "/vendor") || strings.HasPrefix(lower, "/product"):
		return "SYSTEM"
	default:
		return "OTHER"
	}
}

package state

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
)

// AndroidBrowseRequest is the bounded persisted-scan query contract used by the
// Android shell. Full-scan features must page through this API rather than
// materializing scan_entries in Kotlin or Go memory.
type AndroidBrowseRequest struct {
	ParentPath     *string  `json:"parentPath,omitempty"`
	Query          string   `json:"query"`
	EntryType      string   `json:"type"`
	MinBytes       int64    `json:"minBytes"`
	MaxBytes       *int64   `json:"maxBytes,omitempty"`
	Category       *string  `json:"category,omitempty"`
	FileCategories []string `json:"fileCategories,omitempty"`
	PackageName    *string  `json:"packageName,omitempty"`
	Extensions     []string `json:"extensions,omitempty"`
	ModifiedBefore *int64   `json:"modifiedBefore,omitempty"`
	Sort           string   `json:"sort"`
	Descending     bool     `json:"descending"`
	Offset         int      `json:"offset"`
	Limit          int      `json:"limit"`
}

type AndroidBrowseItem struct {
	AndroidScanEntry
	Category     string  `json:"category"`
	FileCategory string  `json:"fileCategory"`
	PackageName  *string `json:"packageName,omitempty"`
}

type AndroidBrowsePage struct {
	ScanID     string              `json:"scanId"`
	Generation int64               `json:"generation"`
	Offset     int                 `json:"offset"`
	Limit      int                 `json:"limit"`
	TotalCount int64               `json:"totalCount"`
	TotalBytes int64               `json:"totalBytes"`
	HasMore    bool                `json:"hasMore"`
	Items      []AndroidBrowseItem `json:"items"`
}

func normalizedBrowseLimit(limit int) int {
	if limit < 1 {
		return 100
	}
	if limit > 500 {
		return 500
	}
	return limit
}

func androidCategorySQL() string {
	// Keep this aligned with androidClassifyPath. SQL classification makes
	// category paging/counting bounded and authoritative without a Go-side
	// whole-scan filtering pass.
	return `CASE
      WHEN lower(path) LIKE '/data/adb/modules/%' THEN 'ROOT_MODULE'
      WHEN lower(path) LIKE '%/cache/%' OR lower(path) LIKE '%/cache' THEN 'CACHE'
      WHEN lower(path) LIKE '%/code_cache/%' OR lower(path) LIKE '%/code_cache' THEN 'CODE_CACHE'
      WHEN lower(path) LIKE '%/databases/%' OR lower(path) LIKE '%.db' OR lower(path) LIKE '%.sqlite' THEN 'DATABASE'
      WHEN lower(path) LIKE '%/shared_prefs/%' OR (lower(path) LIKE '%.xml' AND lower(path) LIKE '%/data/%') THEN 'PREFERENCES'
      WHEN lower(path) LIKE '%.apk' OR lower(path) LIKE '/data/app/%' THEN 'APK'
      WHEN lower(path) LIKE '%/android/obb/%' THEN 'OBB'
      WHEN lower(path) LIKE '/storage/emulated/%' AND (lower(path) LIKE '%/dcim/%' OR lower(path) LIKE '%/pictures/%' OR lower(path) LIKE '%/movies/%' OR lower(path) LIKE '%/music/%') THEN 'MEDIA'
      WHEN lower(path) LIKE '%/download/%' OR lower(path) LIKE '%/download' THEN 'DOWNLOAD'
      WHEN lower(path) LIKE '%.log' OR lower(path) LIKE '/data/tombstones%' OR lower(path) LIKE '/data/anr%' THEN 'LOG'
      WHEN lower(path) LIKE '%.tmp' OR lower(path) LIKE '%.temp' THEN 'TEMPORARY'
      WHEN lower(path) LIKE '/data/data/%' OR lower(path) LIKE '/data/user/%' OR lower(path) LIKE '/data/user_de/%' OR lower(path) LIKE '/storage/emulated/%/android/data/%' OR lower(path) LIKE '/storage/emulated/%/android/media/%' OR lower(path) LIKE '/storage/emulated/%/android/obb/%' THEN 'APP_DATA'
      WHEN lower(path) LIKE '/system%' OR lower(path) LIKE '/vendor%' OR lower(path) LIKE '/product%' THEN 'SYSTEM'
      ELSE 'OTHER'
    END`
}

func androidFileCategorySQL() string {
	return `CASE
      WHEN lower(name) LIKE '%.apk' OR lower(name) LIKE '%.apex' THEN 'Android packages'
      WHEN lower(name) LIKE '%.jpg' OR lower(name) LIKE '%.jpeg' OR lower(name) LIKE '%.png' OR lower(name) LIKE '%.gif' OR lower(name) LIKE '%.webp' OR lower(name) LIKE '%.heic' THEN 'Images'
      WHEN lower(name) LIKE '%.mp4' OR lower(name) LIKE '%.mkv' OR lower(name) LIKE '%.webm' OR lower(name) LIKE '%.avi' OR lower(name) LIKE '%.mov' THEN 'Video'
      WHEN lower(name) LIKE '%.mp3' OR lower(name) LIKE '%.m4a' OR lower(name) LIKE '%.ogg' OR lower(name) LIKE '%.flac' OR lower(name) LIKE '%.wav' THEN 'Audio'
      WHEN lower(name) LIKE '%.zip' OR lower(name) LIKE '%.7z' OR lower(name) LIKE '%.rar' OR lower(name) LIKE '%.tar' OR lower(name) LIKE '%.gz' OR lower(name) LIKE '%.zst' THEN 'Archives'
      WHEN lower(name) LIKE '%.db' OR lower(name) LIKE '%.sqlite' OR lower(name) LIKE '%.sqlite3' THEN 'Databases'
      WHEN lower(name) LIKE '%.log' OR lower(name) LIKE '%.txt' THEN 'Logs and text'
      WHEN lower(name) LIKE '%.dex' OR lower(name) LIKE '%.odex' OR lower(name) LIKE '%.vdex' OR lower(name) LIKE '%.so' THEN 'Runtime and native code'
      ELSE 'Other'
    END`
}

func androidFileCategory(name string) string {
	lower := strings.ToLower(name)
	dot := strings.LastIndexByte(lower, '.')
	ext := ""
	if dot >= 0 && dot+1 < len(lower) {
		ext = lower[dot+1:]
	}
	switch ext {
	case "apk", "apex":
		return "Android packages"
	case "jpg", "jpeg", "png", "gif", "webp", "heic":
		return "Images"
	case "mp4", "mkv", "webm", "avi", "mov":
		return "Video"
	case "mp3", "m4a", "ogg", "flac", "wav":
		return "Audio"
	case "zip", "7z", "rar", "tar", "gz", "zst":
		return "Archives"
	case "db", "sqlite", "sqlite3":
		return "Databases"
	case "log", "txt":
		return "Logs and text"
	case "dex", "odex", "vdex", "so":
		return "Runtime and native code"
	default:
		return "Other"
	}
}

func appendAndroidPackagePredicate(where *strings.Builder, args *[]any, packageName string) {
	p := strings.TrimSpace(packageName)
	if p == "" {
		return
	}
	// Match only the known Android package ownership roots. This is a query
	// filter, not cleanup eligibility; destructive residue authority still
	// requires package evidence/recheck.
	where.WriteString(` AND (
      path LIKE ? OR path LIKE ? OR path LIKE ? OR path LIKE ? OR path LIKE ? OR path LIKE ? OR path LIKE ?
    )`)
	*args = append(*args,
		"/data/data/"+p+"/%",
		"/data/user/%/"+p+"/%",
		"/data/user_de/%/"+p+"/%",
		"/storage/emulated/%/Android/data/"+p+"/%",
		"/storage/emulated/%/Android/media/"+p+"/%",
		"/storage/emulated/%/Android/obb/"+p+"/%",
		"/data/app/%"+p+"%",
	)
}

func (s *SQLiteStore) AndroidBrowsePage(ctx context.Context, scanID string, generation int64, req AndroidBrowseRequest) (AndroidBrowsePage, error) {
	if scanID == "" || generation <= 0 {
		return AndroidBrowsePage{}, errors.New("android_browse_identity_required")
	}
	session, err := s.AndroidScanSessionByID(ctx, scanID)
	if err != nil {
		return AndroidBrowsePage{}, err
	}
	if session == nil {
		return AndroidBrowsePage{}, sql.ErrNoRows
	}
	if session.Generation != generation {
		return AndroidBrowsePage{}, fmt.Errorf("stale_generation: expected=%d actual=%d", generation, session.Generation)
	}

	limit := normalizedBrowseLimit(req.Limit)
	offset := req.Offset
	if offset < 0 {
		offset = 0
	}

	var where strings.Builder
	where.WriteString("scan_id=?")
	args := []any{scanID}
	if req.ParentPath != nil {
		where.WriteString(" AND parent_path=?")
		args = append(args, *req.ParentPath)
	}
	switch strings.ToUpper(strings.TrimSpace(req.EntryType)) {
	case "FILES":
		where.WriteString(" AND is_directory=0")
	case "DIRECTORIES":
		where.WriteString(" AND is_directory=1")
	}
	if req.MinBytes > 0 {
		where.WriteString(" AND logical_size_bytes>=?")
		args = append(args, req.MinBytes)
	}
	if req.MaxBytes != nil {
		where.WriteString(" AND logical_size_bytes<=?")
		args = append(args, *req.MaxBytes)
	}
	if req.ModifiedBefore != nil {
		where.WriteString(" AND modified_at_ms>0 AND modified_at_ms<=?")
		args = append(args, *req.ModifiedBefore)
	}
	term := strings.TrimSpace(req.Query)
	if term != "" {
		like := "%" + strings.ToLower(term) + "%"
		where.WriteString(" AND (lower(name) LIKE ? OR lower(path) LIKE ?)")
		args = append(args, like, like)
	}
	if req.Category != nil && strings.TrimSpace(*req.Category) != "" {
		where.WriteString(" AND (")
		where.WriteString(androidCategorySQL())
		where.WriteString(")=?")
		args = append(args, strings.ToUpper(strings.TrimSpace(*req.Category)))
	}
	cleanedCategories := make([]string, 0, len(req.FileCategories))
	for _, category := range req.FileCategories {
		if value := strings.TrimSpace(category); value != "" {
			cleanedCategories = append(cleanedCategories, value)
		}
	}
	if len(cleanedCategories) > 16 {
		return AndroidBrowsePage{}, errors.New("android_browse_file_category_limit_exceeded")
	}
	if len(cleanedCategories) > 0 {
		where.WriteString(" AND (")
		where.WriteString(androidFileCategorySQL())
		where.WriteString(") IN (")
		for i, category := range cleanedCategories {
			if i > 0 {
				where.WriteString(",")
			}
			where.WriteString("?")
			args = append(args, category)
		}
		where.WriteString(")")
	}
	appendAndroidPackagePredicate(&where, &args, derefString(req.PackageName))

	cleanedExt := make([]string, 0, len(req.Extensions))
	for _, ext := range req.Extensions {
		ext = strings.TrimPrefix(strings.ToLower(strings.TrimSpace(ext)), ".")
		if ext != "" {
			cleanedExt = append(cleanedExt, ext)
		}
	}
	if len(cleanedExt) > 32 {
		return AndroidBrowsePage{}, errors.New("android_browse_extension_limit_exceeded")
	}
	if len(cleanedExt) > 0 {
		where.WriteString(" AND (")
		for i, ext := range cleanedExt {
			if i > 0 {
				where.WriteString(" OR ")
			}
			where.WriteString("lower(name) LIKE ?")
			args = append(args, "%."+ext)
		}
		where.WriteString(")")
	}

	countSQL := "SELECT COUNT(*),COALESCE(SUM(logical_size_bytes),0) FROM scan_entries WHERE " + where.String()
	var totalCount, totalBytes int64
	if err := s.db.QueryRowContext(ctx, countSQL, args...).Scan(&totalCount, &totalBytes); err != nil {
		return AndroidBrowsePage{}, err
	}

	orderColumn := "logical_size_bytes"
	switch strings.ToUpper(strings.TrimSpace(req.Sort)) {
	case "NAME":
		orderColumn = "name COLLATE NOCASE"
	case "MODIFIED", "OLDEST", "NEWEST":
		orderColumn = "modified_at_ms"
	case "PATH":
		orderColumn = "path COLLATE NOCASE"
	}
	direction := "DESC"
	if !req.Descending {
		direction = "ASC"
	}
	if strings.EqualFold(req.Sort, "OLDEST") {
		direction = "ASC"
	}
	if strings.EqualFold(req.Sort, "NEWEST") {
		direction = "DESC"
	}

	query := "SELECT " + androidScanEntryColumns + " FROM scan_entries WHERE " + where.String() + " ORDER BY " + orderColumn + " " + direction + ",path COLLATE NOCASE LIMIT ? OFFSET ?"
	pageArgs := append(append([]any{}, args...), limit, offset)
	rows, err := s.db.QueryContext(ctx, query, pageArgs...)
	if err != nil {
		return AndroidBrowsePage{}, err
	}
	defer rows.Close()
	items := make([]AndroidBrowseItem, 0, limit)
	for rows.Next() {
		entry, err := scanAndroidEntry(rows)
		if err != nil {
			return AndroidBrowsePage{}, err
		}
		items = append(items, AndroidBrowseItem{AndroidScanEntry: entry, Category: androidClassifyPath(entry.Path), FileCategory: androidFileCategory(entry.Name), PackageName: androidPackageFromPath(entry.Path)})
	}
	if err := rows.Err(); err != nil {
		return AndroidBrowsePage{}, err
	}
	return AndroidBrowsePage{
		ScanID: scanID, Generation: generation, Offset: offset, Limit: limit,
		TotalCount: totalCount, TotalBytes: totalBytes,
		HasMore: int64(offset+len(items)) < totalCount,
		Items:   items,
	}, nil
}

func derefString(v *string) string {
	if v == nil {
		return ""
	}
	return *v
}

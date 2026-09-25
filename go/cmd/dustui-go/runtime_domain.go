package main

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/mikeyphw/dustui/go/internal/state"
)

type scanIDPayload struct {
	ScanID string `json:"scanId"`
}
type sessionUpsertPayload struct {
	Session state.AndroidScanSession `json:"session"`
}
type entriesUpsertPayload struct {
	Entries []state.AndroidScanEntry `json:"entries"`
}
type completedPayload struct {
	Limit int `json:"limit"`
}
type entriesPagePayload struct {
	ScanID string `json:"scanId"`
	Offset int    `json:"offset"`
	Limit  int    `json:"limit"`
}
type browsePagePayload struct {
	ScanID     string                     `json:"scanId"`
	Generation int64                      `json:"generation"`
	Request    state.AndroidBrowseRequest `json:"request"`
}

type entryPathPayload struct {
	ScanID string `json:"scanId"`
	Path   string `json:"path"`
}
type entriesByPathsPayload struct {
	ScanID string   `json:"scanId"`
	Paths  []string `json:"paths"`
}
type filesAtLeastPayload struct {
	ScanID   string `json:"scanId"`
	MinBytes int64  `json:"minBytes"`
}
type filesExtensionsPayload struct {
	ScanID     string   `json:"scanId"`
	Extensions []string `json:"extensions"`
}
type duplicateFilesPayload struct {
	ScanID    string `json:"scanId"`
	SizeBytes int64  `json:"sizeBytes"`
	Limit     int    `json:"limit"`
}
type searchPayload struct {
	ScanID  string                     `json:"scanId"`
	Request state.AndroidSearchRequest `json:"request"`
}
type prunePayload struct {
	Keep int `json:"keep"`
}
type interruptPayload struct {
	CompletedAt int64 `json:"completedAt"`
}
type cleanupUpsertPayload struct {
	Operation state.AndroidCleanupOperation `json:"operation"`
}
type cleanupPlanPayload struct {
	OperationID string                         `json:"operationId"`
	Items       []state.AndroidCleanupPlanItem `json:"items"`
}
type operationIDPayload struct {
	OperationID string `json:"operationId"`
}
type cleanupListPayload struct {
	Limit  int    `json:"limit"`
	Status string `json:"status"`
}
type productInsightsPayload struct {
	ScanID  string                       `json:"scanId"`
	Options state.ProductInsightsOptions `json:"options"`
}
type rulePreviewPayload struct {
	ScanID          string                   `json:"scanId"`
	Rules           []state.CleanupRuleInput `json:"rules"`
	IncludeDisabled bool                     `json:"includeDisabled"`
}

func decodePayload[T any](raw json.RawMessage) (T, error) {
	var v T
	if len(raw) == 0 {
		raw = []byte("{}")
	}
	if err := json.Unmarshal(raw, &v); err != nil {
		return v, fmt.Errorf("decode domain payload: %w", err)
	}
	return v, nil
}

func domainPayload(ctx context.Context, store *state.SQLiteStore, frame runtimeFrame) (any, error) {
	switch frame.Type {
	case "domain.schema.verify":
		if err := store.VerifyAndroidDomainSchema(ctx); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "schemaVersion": state.SchemaVersion}, nil
	case "domain.scan.session.upsert":
		p, err := decodePayload[sessionUpsertPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		if err := store.UpsertAndroidScanSession(ctx, p.Session); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true}, nil
	case "domain.scan.entries.delete":
		p, err := decodePayload[scanIDPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		if err := store.DeleteAndroidScanEntries(ctx, p.ScanID); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true}, nil
	case "domain.scan.entries.upsert":
		p, err := decodePayload[entriesUpsertPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		if err := store.UpsertAndroidScanEntries(ctx, p.Entries); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "count": len(p.Entries)}, nil
	case "domain.scan.latest":
		v, err := store.LatestAndroidScanSession(ctx)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "session": v}, nil
	case "domain.scan.session.get":
		p, err := decodePayload[scanIDPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidScanSessionByID(ctx, p.ScanID)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "session": v}, nil
	case "domain.scan.sessions.completed":
		p, err := decodePayload[completedPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.CompletedAndroidScanSessions(ctx, p.Limit)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "sessions": v}, nil
	case "domain.scan.entries.page":
		p, err := decodePayload[entriesPagePayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidScanEntriesPage(ctx, p.ScanID, p.Offset, p.Limit)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "entries": v}, nil

	case "domain.scan.browse.page":
		p, err := decodePayload[browsePagePayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidBrowsePage(ctx, p.ScanID, p.Generation, p.Request)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "page": v}, nil

	case "domain.scan.entry.get":
		p, err := decodePayload[entryPathPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidScanEntryByPath(ctx, p.ScanID, p.Path)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "entry": v}, nil
	case "domain.scan.entries.by-paths":
		p, err := decodePayload[entriesByPathsPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidScanEntriesByPaths(ctx, p.ScanID, p.Paths)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "entries": v}, nil
	case "domain.scan.files.at-least":
		p, err := decodePayload[filesAtLeastPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidFilesAtLeast(ctx, p.ScanID, p.MinBytes)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "entries": v}, nil
	case "domain.scan.files.extensions":
		p, err := decodePayload[filesExtensionsPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidFilesByExtensions(ctx, p.ScanID, p.Extensions)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "entries": v}, nil
	case "domain.scan.duplicate.buckets":
		p, err := decodePayload[filesAtLeastPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidDuplicateSizeBuckets(ctx, p.ScanID, p.MinBytes)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "buckets": v}, nil
	case "domain.scan.duplicate.files":
		p, err := decodePayload[duplicateFilesPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidDuplicateFilesBySize(ctx, p.ScanID, p.SizeBytes, p.Limit)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "entries": v}, nil
	case "domain.scan.search":
		p, err := decodePayload[searchPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		session, err := store.AndroidScanSessionByID(ctx, p.ScanID)
		if err != nil {
			return nil, err
		}
		if session == nil {
			return map[string]any{"ok": true, "page": state.AndroidSearchPage{ScanID: p.ScanID, Limit: p.Request.Limit}}, nil
		}
		v, err := store.SearchAndroidScanEntriesPage(ctx, p.ScanID, session.Generation, p.Request)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "page": v}, nil
	case "domain.scan.prune":
		p, err := decodePayload[prunePayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		if err := store.PruneCompletedAndroidScans(ctx, p.Keep); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true}, nil
	case "domain.scan.interrupt-active":
		p, err := decodePayload[interruptPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		count, err := store.InterruptActiveAndroidScans(ctx, p.CompletedAt)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "count": count}, nil
	case "domain.cleanup.operation.upsert":
		p, err := decodePayload[cleanupUpsertPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		if err := store.UpsertAndroidCleanupOperation(ctx, p.Operation); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true}, nil
	case "domain.cleanup.operation.insert-if-absent":
		p, err := decodePayload[cleanupUpsertPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		inserted, err := store.InsertAndroidCleanupOperationIfAbsent(ctx, p.Operation)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "inserted": inserted}, nil
	case "domain.cleanup.plan.replace":
		p, err := decodePayload[cleanupPlanPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		if err := store.ReplaceAndroidCleanupPlan(ctx, p.OperationID, p.Items); err != nil {
			return nil, err
		}
		return map[string]any{"ok": true}, nil
	case "domain.cleanup.operation.claim":
		p, err := decodePayload[operationIDPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		count, err := store.ClaimAndroidCleanupOperation(ctx, p.OperationID)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "count": count}, nil
	case "domain.product.rules.preview":
		p, err := decodePayload[rulePreviewPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		out, err := store.CleanupRulePreview(ctx, p.ScanID, p.Rules, p.IncludeDisabled)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "preview": out}, nil
	case "domain.product.insights":
		p, err := decodePayload[productInsightsPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.ProductInsights(ctx, p.ScanID, p.Options)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "insights": v}, nil
	case "domain.cleanup.operations.list":
		p, err := decodePayload[cleanupListPayload](frame.Payload)
		if err != nil {
			return nil, err
		}
		v, err := store.AndroidCleanupOperations(ctx, p.Limit, p.Status)
		if err != nil {
			return nil, err
		}
		return map[string]any{"ok": true, "operations": v}, nil
	default:
		return nil, fmt.Errorf("unsupported domain request %q", frame.Type)
	}
}

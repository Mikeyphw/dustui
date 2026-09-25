package state

import "strings"

type VerificationFingerprint struct {
	Device        *int64  `json:"device,omitempty"`
	MountID       *int64  `json:"mountId,omitempty"`
	Inode         *int64  `json:"inode,omitempty"`
	Mode          *int64  `json:"mode,omitempty"`
	StatSizeBytes *int64  `json:"statSizeBytes,omitempty"`
	ModifiedAt    *int64  `json:"modifiedAt,omitempty"`
	TreeDigest    *string `json:"treeDigest,omitempty"`
	EntryCount    *int64  `json:"entryCount,omitempty"`
	ContentSHA256 *string `json:"contentSha256,omitempty"`
}
type VerificationApproved struct {
	Path         string                  `json:"path"`
	PlannedBytes int64                   `json:"plannedBytes"`
	Fingerprint  VerificationFingerprint `json:"fingerprint"`
}
type VerificationBlocked struct {
	Path   string `json:"path"`
	Reason string `json:"reason"`
}
type VerificationReview struct {
	OperationID string                 `json:"operationId"`
	ScanID      string                 `json:"scanId"`
	RootPath    string                 `json:"rootPath"`
	Accepted    []VerificationApproved `json:"accepted"`
	Blocked     []VerificationBlocked  `json:"blocked"`
}
type VerificationResultItem struct {
	Path         string  `json:"path"`
	PlannedBytes int64   `json:"plannedBytes"`
	DeletedBytes int64   `json:"deletedBytes"`
	Status       string  `json:"status"`
	Message      *string `json:"message,omitempty"`
}
type VerificationResult struct {
	Items []VerificationResultItem `json:"items"`
}
type VerificationObservation struct {
	Presence string  `json:"presence"`
	Message  *string `json:"message,omitempty"`
}
type VerificationEnvironment struct {
	Manufacturer string  `json:"manufacturer"`
	Model        string  `json:"model"`
	SDKInt       int     `json:"sdkInt"`
	Filesystem   *string `json:"filesystem,omitempty"`
	MountPoint   *string `json:"mountPoint,omitempty"`
	MountSource  *string `json:"mountSource,omitempty"`
	ReadOnly     *bool   `json:"readOnly,omitempty"`
}
type VerificationBuildInput struct {
	Schema          string                             `json:"schema"`
	Domain          string                             `json:"domain"`
	ScanID          string                             `json:"scanId"`
	Review          VerificationReview                 `json:"review"`
	Result          VerificationResult                 `json:"result"`
	StartedAt       int64                              `json:"startedAt"`
	CompletedAt     int64                              `json:"completedAt"`
	Observations    map[string]VerificationObservation `json:"observations"`
	FreeBytesBefore *int64                             `json:"freeBytesBefore"`
	FreeBytesAfter  *int64                             `json:"freeBytesAfter"`
	AppVersion      string                             `json:"appVersion"`
	Environment     VerificationEnvironment            `json:"environment"`
}
type VerificationItem struct {
	Path                 string                   `json:"path"`
	PlannedBytes         int64                    `json:"plannedBytes"`
	ReportedDeletedBytes int64                    `json:"reportedDeletedBytes"`
	Status               string                   `json:"status"`
	PathPresence         string                   `json:"pathPresence"`
	Message              *string                  `json:"message,omitempty"`
	Fingerprint          *VerificationFingerprint `json:"fingerprint,omitempty"`
}
type VerificationReport struct {
	SchemaVersion         int                     `json:"schemaVersion"`
	OperationID           string                  `json:"operationId"`
	ScanID                string                  `json:"scanId"`
	RootPath              string                  `json:"rootPath"`
	StartedAt             int64                   `json:"startedAt"`
	CompletedAt           int64                   `json:"completedAt"`
	PlannedBytes          int64                   `json:"plannedBytes"`
	AttemptedBytes        int64                   `json:"attemptedBytes"`
	ReportedDeletedBytes  int64                   `json:"reportedDeletedBytes"`
	ConfirmedDeletedBytes int64                   `json:"confirmedDeletedBytes"`
	PartialBytes          int64                   `json:"partialBytes"`
	FailedBytes           int64                   `json:"failedBytes"`
	FreeBytesBefore       *int64                  `json:"freeBytesBefore"`
	FreeBytesAfter        *int64                  `json:"freeBytesAfter"`
	Items                 []VerificationItem      `json:"items"`
	AppVersion            string                  `json:"appVersion"`
	Environment           VerificationEnvironment `json:"environment"`
	VerificationAttempts  int                     `json:"verificationAttempts"`
	LastVerifiedAt        int64                   `json:"lastVerifiedAt"`
}
type VerificationReverifyInput struct {
	Schema         string                             `json:"schema"`
	Domain         string                             `json:"domain"`
	ScanID         string                             `json:"scanId"`
	Original       VerificationReport                 `json:"original"`
	Observations   map[string]VerificationObservation `json:"observations"`
	VerifiedAt     int64                              `json:"verifiedAt"`
	FreeBytesAfter *int64                             `json:"freeBytesAfter"`
	Environment    VerificationEnvironment            `json:"environment"`
}

func BuildVerification(in VerificationBuildInput) VerificationReport {
	byPath := map[string]VerificationResultItem{}
	for _, x := range in.Result.Items {
		byPath[x.Path] = x
	}
	items := []VerificationItem{}
	for _, a := range in.Review.Accepted {
		actual, found := byPath[a.Path]
		obs, ok := in.Observations[a.Path]
		if !ok {
			obs = VerificationObservation{Presence: "UNKNOWN", Message: strPtr("No post-deletion path observation was recorded")}
		}
		status := "FAILED"
		deleted := int64(0)
		var msg *string
		if found {
			deleted = actual.DeletedBytes
			msg = joinVerificationMessages(actual.Message, obs.Message)
		}
		switch {
		case !found:
			status = "FAILED"
		case actual.Status == "CHANGED" || (actual.Message != nil && strings.Contains(strings.ToLower(*actual.Message), "changed")):
			status = "CHANGED_SINCE_REVIEW"
		case actual.Status == "BLOCKED":
			status = "BLOCKED"
		case obs.Presence == "UNKNOWN":
			status = "FAILED"
		case obs.Presence == "MISSING" && actual.Status == "DELETED" && actual.DeletedBytes >= a.PlannedBytes:
			status = "VERIFIED_DELETED"
		case obs.Presence == "MISSING" && actual.DeletedBytes > 0:
			status = "PARTIAL"
		case obs.Presence == "PRESENT" && actual.DeletedBytes > 0:
			status = "PARTIAL"
		default:
			status = "FAILED"
		}
		fp := a.Fingerprint
		items = append(items, VerificationItem{Path: a.Path, PlannedBytes: a.PlannedBytes, ReportedDeletedBytes: deleted, Status: status, PathPresence: obs.Presence, Message: msg, Fingerprint: &fp})
	}
	for _, b := range in.Review.Blocked {
		m := redactVerification(b.Reason)
		items = append(items, VerificationItem{Path: b.Path, Status: "BLOCKED", PathPresence: "UNKNOWN", Message: &m})
	}
	return summarizeVerification(in.Review.OperationID, in.Review.ScanID, in.Review.RootPath, in.StartedAt, in.CompletedAt, in.FreeBytesBefore, in.FreeBytesAfter, items, in.AppVersion, in.Environment, 1, in.CompletedAt)
}
func ReverifyVerification(in VerificationReverifyInput) VerificationReport {
	items := append([]VerificationItem{}, in.Original.Items...)
	for i, item := range items {
		if item.Status == "BLOCKED" || item.Status == "CHANGED_SINCE_REVIEW" {
			continue
		}
		obs, ok := in.Observations[item.Path]
		if !ok {
			obs = VerificationObservation{Presence: "UNKNOWN", Message: strPtr("No retry observation was recorded")}
		}
		status := "FAILED"
		switch {
		case obs.Presence == "UNKNOWN":
			status = "FAILED"
		case obs.Presence == "MISSING" && item.ReportedDeletedBytes >= item.PlannedBytes:
			status = "VERIFIED_DELETED"
		case obs.Presence == "MISSING" && item.ReportedDeletedBytes > 0:
			status = "PARTIAL"
		case obs.Presence == "PRESENT" && item.ReportedDeletedBytes > 0:
			status = "PARTIAL"
		}
		item.Status = status
		item.PathPresence = obs.Presence
		item.Message = joinVerificationMessages(item.Message, obs.Message)
		items[i] = item
	}
	return summarizeVerification(in.Original.OperationID, in.Original.ScanID, in.Original.RootPath, in.Original.StartedAt, in.Original.CompletedAt, in.Original.FreeBytesBefore, in.FreeBytesAfter, items, in.Original.AppVersion, in.Environment, in.Original.VerificationAttempts+1, in.VerifiedAt)
}
func summarizeVerification(op, scan, root string, started, completed int64, before, after *int64, items []VerificationItem, app string, env VerificationEnvironment, attempts int, last int64) VerificationReport {
	r := VerificationReport{SchemaVersion: 2, OperationID: op, ScanID: scan, RootPath: root, StartedAt: started, CompletedAt: completed, FreeBytesBefore: before, FreeBytesAfter: after, Items: items, AppVersion: app, Environment: env, VerificationAttempts: attempts, LastVerifiedAt: last}
	for _, x := range items {
		r.PlannedBytes += x.PlannedBytes
		r.ReportedDeletedBytes += x.ReportedDeletedBytes
		if x.Status != "BLOCKED" {
			r.AttemptedBytes += x.PlannedBytes
		}
		switch x.Status {
		case "VERIFIED_DELETED":
			r.ConfirmedDeletedBytes += x.PlannedBytes
		case "PARTIAL":
			if x.ReportedDeletedBytes < x.PlannedBytes {
				r.PartialBytes += x.ReportedDeletedBytes
			} else {
				r.PartialBytes += x.PlannedBytes
			}
		case "FAILED", "CHANGED_SINCE_REVIEW":
			r.FailedBytes += x.PlannedBytes
		}
	}
	return r
}
func joinVerificationMessages(a, b *string) *string {
	parts := []string{}
	for _, p := range []*string{a, b} {
		if p != nil && strings.TrimSpace(*p) != "" {
			v := redactVerification(*p)
			found := false
			for _, x := range parts {
				if x == v {
					found = true
				}
			}
			if !found {
				parts = append(parts, v)
			}
		}
	}
	if len(parts) == 0 {
		return nil
	}
	v := strings.Join(parts, " · ")
	return &v
}
func redactVerification(v string) string {
	lower := strings.ToLower(v)
	for _, k := range []string{"authorization", "cookie", "token", "session", "password", "secret"} {
		if i := strings.Index(lower, k); i >= 0 {
			if j := strings.IndexAny(v[i:], "=: "); j >= 0 {
				prefix := v[:i+j+1]
				return prefix + "[redacted]"
			}
		}
	}
	return v
}
func strPtr(v string) *string { return &v }

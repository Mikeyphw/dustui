package authority

import (
	"errors"
	"fmt"
	"strings"
)

const (
	RequestSchema  = "dustui.domain.authority.request.v1"
	ResponseSchema = "dustui.domain.authority.response.v1"

	EvidenceSupported   = "supported"
	EvidencePartial     = "partial"
	EvidenceUnavailable = "unavailable"
	EvidenceBlocked     = "blocked"

	FreshnessFresh   = "fresh"
	FreshnessStale   = "stale"
	FreshnessUnknown = "unknown"
)

var ErrInvalidRequest = errors.New("domain_authority_request_invalid")

type Request struct {
	Schema     string `json:"schema"`
	Domain     string `json:"domain"`
	ScanID     string `json:"scanId"`
	Generation int64  `json:"generation"`
}

type Meta struct {
	Schema             string   `json:"schema"`
	Domain             string   `json:"domain"`
	ScanID             string   `json:"scanId"`
	Generation         int64    `json:"generation"`
	Freshness          string   `json:"freshness"`
	EvidenceState      string   `json:"evidenceState"`
	DegradationReasons []string `json:"degradationReasons"`
	Bounded            bool     `json:"bounded"`
	Durable            bool     `json:"durable"`
	Source             string   `json:"source"`
}

func ValidateRequest(r Request, expectedDomain string) error {
	if r.Schema != RequestSchema {
		return fmt.Errorf("%w:schema", ErrInvalidRequest)
	}
	if strings.TrimSpace(r.Domain) == "" || (expectedDomain != "" && r.Domain != expectedDomain) {
		return fmt.Errorf("%w:domain", ErrInvalidRequest)
	}
	if strings.TrimSpace(r.ScanID) == "" || r.Generation <= 0 {
		return fmt.Errorf("%w:scan_generation", ErrInvalidRequest)
	}
	return nil
}

func NewMeta(domain, scanID string, generation int64, freshness, evidence string, reasons []string) Meta {
	if freshness == "" {
		freshness = FreshnessUnknown
	}
	if evidence == "" {
		evidence = EvidenceUnavailable
	}
	if reasons == nil {
		reasons = []string{}
	}
	return Meta{Schema: ResponseSchema, Domain: domain, ScanID: scanID, Generation: generation, Freshness: freshness, EvidenceState: evidence, DegradationReasons: reasons, Bounded: true, Durable: true, Source: "go-sqlite"}
}

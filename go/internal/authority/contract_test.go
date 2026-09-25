package authority

import (
	"encoding/json"
	"os"
	"testing"
)

func TestAuthorityRequestRejectsOldOrUnboundRequests(t *testing.T) {
	good := Request{Schema: RequestSchema, Domain: "package-storage", ScanID: "scan-1", Generation: 7}
	if err := ValidateRequest(good, "package-storage"); err != nil {
		t.Fatal(err)
	}
	cases := []Request{
		{Schema: "v0", Domain: "package-storage", ScanID: "scan-1", Generation: 7},
		{Schema: RequestSchema, Domain: "residue", ScanID: "scan-1", Generation: 7},
		{Schema: RequestSchema, Domain: "package-storage", ScanID: "", Generation: 7},
		{Schema: RequestSchema, Domain: "package-storage", ScanID: "scan-1", Generation: 0},
	}
	for i, tc := range cases {
		if ValidateRequest(tc, "package-storage") == nil {
			t.Fatalf("case %d accepted", i)
		}
	}
}

func TestAuthorityMetaAlwaysCarriesTruthFields(t *testing.T) {
	m := NewMeta("growth", "scan-2", 9, FreshnessFresh, EvidencePartial, []string{"package_inventory_partial"})
	if m.Schema != ResponseSchema || !m.Bounded || !m.Durable || m.Source != "go-sqlite" {
		t.Fatalf("bad meta: %#v", m)
	}
	if m.EvidenceState != EvidencePartial || m.Generation != 9 || len(m.DegradationReasons) != 1 {
		t.Fatalf("bad evidence meta: %#v", m)
	}
}

func TestAuthorityGoldenFixtureIsSharedAndVersioned(t *testing.T) {
	raw, err := os.ReadFile("../../../migration/contracts/v2/fixtures/domain_authority_golden_v1.json")
	if err != nil {
		t.Fatal(err)
	}
	var fixture struct {
		Request                Request  `json:"request"`
		ResponseAuthority      Meta     `json:"responseAuthority"`
		RejectedRequestSchemas []string `json:"rejectedRequestSchemas"`
	}
	if err := json.Unmarshal(raw, &fixture); err != nil {
		t.Fatal(err)
	}
	if err := ValidateRequest(fixture.Request, "package-storage"); err != nil {
		t.Fatal(err)
	}
	if fixture.ResponseAuthority.Schema != ResponseSchema || fixture.ResponseAuthority.EvidenceState != EvidencePartial || !fixture.ResponseAuthority.Bounded || !fixture.ResponseAuthority.Durable {
		t.Fatalf("bad golden response: %#v", fixture.ResponseAuthority)
	}
	for _, schema := range fixture.RejectedRequestSchemas {
		bad := fixture.Request
		bad.Schema = schema
		if ValidateRequest(bad, "package-storage") == nil {
			t.Fatalf("accepted old schema %q", schema)
		}
	}
}

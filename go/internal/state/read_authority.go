package state

import "fmt"

type AuthoritySource string

const (
	SourceGoSQLite  AuthoritySource = "go_sqlite"
	SourceRoomV3    AuthoritySource = "room_v3"
	SourceLegacyCpp AuthoritySource = "legacy_cpp"
	SourceKotlin    AuthoritySource = "kotlin_domain"
)

type Domain string

const (
	DomainScanSession Domain = "scan_session"
	DomainScanEntry   Domain = "scan_entry"
	DomainHistory     Domain = "history"
	DomainCleanup     Domain = "cleanup"
)

type AuthorityDecision struct {
	Domain       Domain          `json:"domain"`
	Source       AuthoritySource `json:"source"`
	MayRead      bool            `json:"mayRead"`
	MayWriteNew  bool            `json:"mayWriteNew"`
	ImportOnly   bool            `json:"importOnly"`
	Reason       string          `json:"reason"`
	MigrationCut string          `json:"migrationCut"`
}

func OV05AuthorityMatrix() []AuthorityDecision {
	return []AuthorityDecision{
		{Domain: DomainScanSession, Source: SourceGoSQLite, MayRead: true, MayWriteNew: true, Reason: "Go SQLite owns new scan sessions after OV05", MigrationCut: "OV05"},
		{Domain: DomainScanEntry, Source: SourceGoSQLite, MayRead: true, MayWriteNew: true, Reason: "Go SQLite owns new scan entries after OV05", MigrationCut: "OV05"},
		{Domain: DomainHistory, Source: SourceGoSQLite, MayRead: true, MayWriteNew: true, Reason: "Go SQLite owns migration scan history after OV05", MigrationCut: "OV05"},
		{Domain: DomainScanSession, Source: SourceRoomV3, MayRead: true, MayWriteNew: false, ImportOnly: true, Reason: "Room scan sessions are retained/import-only", MigrationCut: "OV05"},
		{Domain: DomainScanEntry, Source: SourceRoomV3, MayRead: true, MayWriteNew: false, ImportOnly: true, Reason: "Room scan entries are retained/import-only", MigrationCut: "OV05"},
		{Domain: DomainCleanup, Source: SourceRoomV3, MayRead: true, MayWriteNew: false, ImportOnly: true, Reason: "legacy quarantine cleanup records are retained/read-only until cleanup authority moves in OV08", MigrationCut: "OV05"},
		{Domain: DomainScanSession, Source: SourceLegacyCpp, MayRead: false, MayWriteNew: false, Reason: "C++ scanner is not a migration scan authority after OV05", MigrationCut: "OV05"},
		{Domain: DomainScanEntry, Source: SourceKotlin, MayRead: false, MayWriteNew: false, Reason: "Kotlin domain cannot write new scan state after OV05", MigrationCut: "OV05"},
	}
}

func CanWriteNewScan(source AuthoritySource) bool {
	return source == SourceGoSQLite
}

func ValidateOV05AuthorityMatrix() error {
	seenGoSession := false
	seenRoomImportOnly := false
	for _, d := range OV05AuthorityMatrix() {
		if d.Domain == DomainScanSession && d.Source == SourceGoSQLite && d.MayWriteNew {
			seenGoSession = true
		}
		if d.Source == SourceRoomV3 {
			if d.MayWriteNew || !d.ImportOnly {
				return fmt.Errorf("Room source is not import-only: %+v", d)
			}
			seenRoomImportOnly = true
		}
		if (d.Source == SourceLegacyCpp || d.Source == SourceKotlin) && d.MayWriteNew {
			return fmt.Errorf("legacy source may still write: %+v", d)
		}
	}
	if !seenGoSession || !seenRoomImportOnly {
		return fmt.Errorf("authority matrix missing Go writer or Room import-only rule")
	}
	return nil
}

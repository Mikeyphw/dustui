package automation

import "testing"

func TestAUD_FIX04ContractPointsAtSingleDurableSQLiteAuthority(t *testing.T) {
	contract := Contract()
	if contract.Protocol != "dustui.automation.activity.v2" || contract.Phase != "AUD-FIX-04" {
		t.Fatalf("unexpected successor contract: %+v", contract)
	}
	if !contract.GoAuthority || contract.Persistence != "go-owned-sqlite" {
		t.Fatalf("automation contract must identify Go-owned SQLite authority: %+v", contract)
	}
	if contract.AcceptedIsSuccess || contract.QueuedIsSuccess || contract.RunningIsSuccess {
		t.Fatalf("nonterminal admission/runtime states must never be success: %+v", contract)
	}
	contains := func(values []string, want string) bool {
		for _, value := range values {
			if value == want {
				return true
			}
		}
		return false
	}
	for _, want := range []string{"scan", "analysis", "status", "inspect"} {
		if !contains(contract.OperationKinds, want) {
			t.Fatalf("missing operation kind %s", want)
		}
	}
	for _, want := range []string{"nonce", "expiry", "single_use", "operation_binding", "replay_rejection"} {
		if !contains(contract.NotificationGuards, want) {
			t.Fatalf("missing notification guard %s", want)
		}
	}
	if !contains(contract.SafetyRules, "single_durable_sqlite_authority") {
		t.Fatal("contract must reject a second in-memory automation authority")
	}
}

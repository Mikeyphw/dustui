package launchcontract

import (
	"os"
	"testing"
)

func TestCurrentDetectsAndroidLaunchContamination(t *testing.T) {
	t.Setenv(AndroidChildMarker, "1")
	t.Setenv("GOMAXPROCS", "2")
	t.Setenv("TEST_TMPDIR", "/data/local/tests")
	report := Current()
	if !report.ShellTestPathPresent {
		t.Fatal("shell test path contamination was not detected")
	}
	if len(report.UnexpectedEnvironment) == 0 {
		t.Fatal("unexpected inherited environment was not detected")
	}
}

func TestValidateHostLaunchIsNonIntrusive(t *testing.T) {
	old, had := os.LookupEnv(AndroidChildMarker)
	_ = os.Unsetenv(AndroidChildMarker)
	t.Cleanup(func() {
		if had {
			_ = os.Setenv(AndroidChildMarker, old)
		} else {
			_ = os.Unsetenv(AndroidChildMarker)
		}
	})
	if err := ValidateAndroidChild(); err != nil {
		t.Fatalf("host launch should not be constrained: %v", err)
	}
}

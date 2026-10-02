package launchcontract

import (
	"fmt"
	"os"
	"runtime/debug"
	"sort"
	"strings"
)

const AndroidChildMarker = "DUSTUI_ANDROID_CHILD"

var allowedAndroidEnvironment = map[string]struct{}{
	"PWD":              {},
	"HOME":             {},
	"TMPDIR":           {},
	"TMP":              {},
	"TEMP":             {},
	"PATH":             {},
	"ANDROID_ROOT":     {},
	"ANDROID_DATA":     {},
	"GOMAXPROCS":       {},
	AndroidChildMarker: {},
}

type Report struct {
	WorkingDirectory      string   `json:"workingDirectory"`
	HomeDirectory         string   `json:"homeDirectory"`
	TemporaryDirectory    string   `json:"temporaryDirectory"`
	GoMaxProcs            string   `json:"goMaxProcs"`
	GoVersion             string   `json:"goVersion"`
	AndroidChild          bool     `json:"androidChild"`
	ShellTestPathPresent  bool     `json:"shellTestPathPresent"`
	UnexpectedEnvironment []string `json:"unexpectedEnvironment"`
}

func Current() Report {
	cwd, _ := os.Getwd()
	report := Report{
		WorkingDirectory:   cwd,
		HomeDirectory:      os.Getenv("HOME"),
		TemporaryDirectory: os.Getenv("TMPDIR"),
		GoMaxProcs:         os.Getenv("GOMAXPROCS"),
		AndroidChild:       os.Getenv(AndroidChildMarker) == "1",
	}
	if info, ok := debug.ReadBuildInfo(); ok {
		report.GoVersion = info.GoVersion
	}
	for _, item := range os.Environ() {
		key, value, _ := strings.Cut(item, "=")
		if containsShellTestPath(value) {
			report.ShellTestPathPresent = true
		}
		if report.AndroidChild {
			if _, ok := allowedAndroidEnvironment[key]; !ok {
				report.UnexpectedEnvironment = append(report.UnexpectedEnvironment, key)
			}
		}
	}
	sort.Strings(report.UnexpectedEnvironment)
	return report
}

func ValidateAndroidChild() error {
	if os.Getenv(AndroidChildMarker) != "1" {
		return nil
	}
	report := Current()
	if strings.TrimSpace(report.GoMaxProcs) == "" {
		return fmt.Errorf("android child GOMAXPROCS is not pinned")
	}
	if containsShellTestPath(report.WorkingDirectory) || containsShellTestPath(report.HomeDirectory) || containsShellTestPath(report.TemporaryDirectory) || report.ShellTestPathPresent {
		return fmt.Errorf("android child inherited shell/native-test path")
	}
	if len(report.UnexpectedEnvironment) != 0 {
		return fmt.Errorf("android child inherited unexpected environment keys: %s", strings.Join(report.UnexpectedEnvironment, ","))
	}
	return nil
}

func containsShellTestPath(value string) bool {
	return strings.Contains(value, "/data/local/tests") || strings.Contains(value, "/data/nativetest")
}

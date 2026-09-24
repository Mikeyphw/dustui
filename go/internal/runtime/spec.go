package runtimecontract

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/readiness"
)

const (
	ProductionABI       = "arm64-v8a"
	TestOnlyABI         = "x86_64"
	RuntimeLibraryName  = "libdustui_go.so"
	RuntimeDisplayName  = "dustui-go"
	MaxFrameBytes       = 1 << 20
	ReadinessTimeoutMs  = 3000
	SessionProtocolName = "dustui.android.runtime.session.v1"
)

type ABIStatus string

const (
	ABIProduction ABIStatus = "production"
	ABITestOnly   ABIStatus = "test_only"
	ABIForbidden  ABIStatus = "forbidden_without_matching_runtime"
)

type Artifact struct {
	ABI         string    `json:"abi"`
	Status      ABIStatus `json:"status"`
	LibraryName string    `json:"libraryName"`
	Present     bool      `json:"present"`
}

type Spec struct {
	Protocol             string     `json:"protocol"`
	ProductionABI        string     `json:"productionAbi"`
	TestOnlyABI          string     `json:"testOnlyAbi"`
	RuntimeLibraryName   string     `json:"runtimeLibraryName"`
	NativeLibraryDirName string     `json:"nativeLibraryDirName"`
	MaxFrameBytes        int        `json:"maxFrameBytes"`
	ReadinessTimeoutMs   int        `json:"readinessTimeoutMs"`
	RequiresFixedPath    bool       `json:"requiresFixedPath"`
	RequiresReadiness    bool       `json:"requiresReadiness"`
	RequiresReconnect    bool       `json:"requiresReconnect"`
	RequiresSequence     bool       `json:"requiresSequence"`
	RequiresRequestID    bool       `json:"requiresRequestId"`
	Artifacts            []Artifact `json:"artifacts"`
	AndroidBuildability  string     `json:"androidBuildability"`
	AndroidReconnectGate string     `json:"androidReconnectGate"`
	ScanReadAuthority    string     `json:"scanReadAuthority"`
	CleanupAuthority     string     `json:"cleanupAuthority"`
	PrivilegedBoundary   string     `json:"privilegedBoundary"`
}

func CurrentSpec() Spec {
	return Spec{
		Protocol:             SessionProtocolName,
		ProductionABI:        ProductionABI,
		TestOnlyABI:          TestOnlyABI,
		RuntimeLibraryName:   RuntimeLibraryName,
		NativeLibraryDirName: "nativeLibraryDir",
		MaxFrameBytes:        MaxFrameBytes,
		ReadinessTimeoutMs:   ReadinessTimeoutMs,
		RequiresFixedPath:    true,
		RequiresReadiness:    true,
		RequiresReconnect:    true,
		RequiresSequence:     true,
		RequiresRequestID:    true,
		Artifacts: []Artifact{
			{ABI: ProductionABI, Status: ABIProduction, LibraryName: RuntimeLibraryName, Present: true},
			{ABI: TestOnlyABI, Status: ABITestOnly, LibraryName: RuntimeLibraryName, Present: false},
		},
		AndroidBuildability:  "intentionally-suspended",
		AndroidReconnectGate: "OV13",
		ScanReadAuthority:    "go-sqlite",
		CleanupAuthority:     "go-evidence-capability-non-mutating-until-OV08",
		PrivilegedBoundary:   "go-root-admission-boundary-ov06",
	}
}

func FixedExecutablePath(nativeLibraryDir string) (string, error) {
	nativeLibraryDir = strings.TrimSpace(nativeLibraryDir)
	if nativeLibraryDir == "" {
		return "", errors.New("native_library_dir_required")
	}
	if strings.Contains(nativeLibraryDir, "\x00") || strings.Contains(nativeLibraryDir, "\n") {
		return "", errors.New("native_library_dir_invalid")
	}
	return filepath.Join(nativeLibraryDir, RuntimeLibraryName), nil
}

type PackageLayout struct {
	Artifacts []Artifact `json:"artifacts"`
}

func ValidatePackageLayout(layout PackageLayout) error {
	production := false
	for _, artifact := range layout.Artifacts {
		if artifact.ABI == "" || artifact.LibraryName == "" {
			return errors.New("artifact_identity_required")
		}
		if artifact.ABI == ProductionABI {
			if artifact.Status != ABIProduction || artifact.LibraryName != RuntimeLibraryName || !artifact.Present {
				return fmt.Errorf("arm64 production runtime invalid: %+v", artifact)
			}
			production = true
			continue
		}
		if artifact.ABI == TestOnlyABI {
			if artifact.Status != ABITestOnly {
				return fmt.Errorf("x86_64 may only be test-only while production Android is suspended: %+v", artifact)
			}
			continue
		}
		if artifact.Status != ABIForbidden {
			return fmt.Errorf("unsupported production ABI %s would advertise no matching Go runtime", artifact.ABI)
		}
	}
	if !production {
		return errors.New("arm64_production_runtime_missing")
	}
	return nil
}

type Process interface {
	Start(context.Context, string) error
	Readiness(context.Context) (readiness.Report, error)
	Send(context.Context, protocol.Envelope) (protocol.Envelope, error)
	Alive() bool
	Kill() error
}

type SessionState string

const (
	SessionStopped  SessionState = "stopped"
	SessionStarting SessionState = "starting"
	SessionReady    SessionState = "ready"
	SessionDead     SessionState = "dead"
)

type Manager struct {
	NativeLibraryDir string
	Proc             Process
	MaxPayloadBytes  int

	executablePath string
	state          SessionState
	sequence       int64
	lastReady      readiness.Report
	startedAt      time.Time
}

func NewManager(nativeLibraryDir string, proc Process) Manager {
	return Manager{NativeLibraryDir: nativeLibraryDir, Proc: proc, MaxPayloadBytes: MaxFrameBytes, state: SessionStopped}
}

func (m *Manager) Start(ctx context.Context) error {
	if m.Proc == nil {
		return errors.New("runtime_process_required")
	}
	path, err := FixedExecutablePath(m.NativeLibraryDir)
	if err != nil {
		return err
	}
	m.state = SessionStarting
	if err := m.Proc.Start(ctx, path); err != nil {
		m.state = SessionDead
		return err
	}
	ready, err := m.Proc.Readiness(ctx)
	if err != nil {
		m.state = SessionDead
		return err
	}
	if ready.Protocol == "" || ready.RequiredTables == nil {
		m.state = SessionDead
		return errors.New("readiness_handshake_incomplete")
	}
	m.executablePath = path
	m.lastReady = ready
	m.startedAt = time.Now()
	m.sequence = 0
	m.state = SessionReady
	return nil
}

func (m *Manager) State() SessionState {
	if m.state == SessionReady && (m.Proc == nil || !m.Proc.Alive()) {
		return SessionDead
	}
	return m.state
}

func (m *Manager) ExecutablePath() string          { return m.executablePath }
func (m *Manager) LastReadiness() readiness.Report { return m.lastReady }

func (m *Manager) Send(ctx context.Context, kind protocol.EnvelopeType, requestID string, generation int64, payload any) (protocol.Envelope, error) {
	if m.State() != SessionReady {
		return protocol.Envelope{}, errors.New("runtime_not_ready")
	}
	encoded, err := json.Marshal(payload)
	if err != nil {
		return protocol.Envelope{}, err
	}
	max := m.MaxPayloadBytes
	if max <= 0 {
		max = MaxFrameBytes
	}
	if len(encoded) > max {
		return protocol.Envelope{}, errors.New("payload_exceeds_bounded_stream")
	}
	env, err := protocol.NewEnvelope(kind, requestID, generation, payload)
	if err != nil {
		return protocol.Envelope{}, err
	}
	m.sequence++
	env.Sequence = m.sequence
	resp, err := m.Proc.Send(ctx, env)
	if err != nil {
		if !m.Proc.Alive() {
			m.state = SessionDead
		}
		return protocol.Envelope{}, err
	}
	if resp.RequestID != env.RequestID || resp.Sequence != env.Sequence || resp.Generation != env.Generation {
		return protocol.Envelope{}, errors.New("stale_or_mismatched_runtime_response")
	}
	return resp, resp.Validate()
}

func (m *Manager) Reconnect(ctx context.Context) error {
	if m.Proc == nil || !m.Proc.Alive() {
		m.state = SessionDead
		return errors.New("runtime_dead_requires_reconcile")
	}
	ready, err := m.Proc.Readiness(ctx)
	if err != nil {
		m.state = SessionDead
		return err
	}
	m.lastReady = ready
	m.state = SessionReady
	return nil
}

func (m *Manager) ReconcileAfterDeath() string {
	if m.State() == SessionDead {
		return "reconcile_required_before_replay"
	}
	return "no_reconcile_needed"
}

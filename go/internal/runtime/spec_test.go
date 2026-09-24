package runtimecontract

import (
	"context"
	"errors"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/readiness"
)

type fakeProcess struct {
	alive bool
	path  string
	stale bool
}

func (p *fakeProcess) Start(_ context.Context, path string) error {
	p.alive = true
	p.path = path
	return nil
}
func (p *fakeProcess) Readiness(context.Context) (readiness.Report, error) {
	if !p.alive {
		return readiness.Report{}, errors.New("dead")
	}
	return readiness.Current(), nil
}
func (p *fakeProcess) Send(_ context.Context, env protocol.Envelope) (protocol.Envelope, error) {
	if !p.alive {
		return protocol.Envelope{}, errors.New("dead")
	}
	if p.stale {
		env.Sequence++
		return env, nil
	}
	return env, nil
}
func (p *fakeProcess) Alive() bool { return p.alive }
func (p *fakeProcess) Kill() error { p.alive = false; return nil }

func TestPackageLayoutRequiresArm64RuntimeAndForbidsAdvertisedMissingABI(t *testing.T) {
	if err := ValidatePackageLayout(PackageLayout{Artifacts: CurrentSpec().Artifacts}); err != nil {
		t.Fatalf("valid package layout rejected: %v", err)
	}
	if err := ValidatePackageLayout(PackageLayout{Artifacts: []Artifact{{ABI: "x86_64", Status: ABIProduction, LibraryName: RuntimeLibraryName, Present: true}}}); err == nil {
		t.Fatalf("x86_64 production ABI must not be accepted without production Go runtime contract")
	}
	if err := ValidatePackageLayout(PackageLayout{Artifacts: []Artifact{{ABI: "armeabi-v7a", Status: ABIProduction, LibraryName: RuntimeLibraryName, Present: true}}}); err == nil {
		t.Fatalf("unsupported production ABI must be rejected")
	}
}

func TestSessionHandshakeSequenceReconnectAndDeath(t *testing.T) {
	proc := &fakeProcess{}
	manager := NewManager("/data/app/com.mikeyphw.dustui/lib/arm64", proc)
	if err := manager.Start(context.Background()); err != nil {
		t.Fatalf("start: %v", err)
	}
	if manager.State() != SessionReady {
		t.Fatalf("state = %s", manager.State())
	}
	if manager.ExecutablePath() != "/data/app/com.mikeyphw.dustui/lib/arm64/libdustui_go.so" {
		t.Fatalf("unexpected executable path: %s", manager.ExecutablePath())
	}
	resp, err := manager.Send(context.Background(), protocol.EnvelopeQueryPage, "req-1", 11, map[string]string{"query": "files"})
	if err != nil {
		t.Fatalf("send: %v", err)
	}
	if resp.Sequence != 1 || resp.RequestID != "req-1" || resp.Generation != 11 {
		t.Fatalf("bad response identity: %+v", resp)
	}
	if err := manager.Reconnect(context.Background()); err != nil {
		t.Fatalf("reconnect: %v", err)
	}
	_ = proc.Kill()
	if manager.State() != SessionDead {
		t.Fatalf("death not observed")
	}
	if got := manager.ReconcileAfterDeath(); got != "reconcile_required_before_replay" {
		t.Fatalf("unexpected reconcile status %s", got)
	}
}

func TestBoundedPayloadAndStaleResponseRejection(t *testing.T) {
	proc := &fakeProcess{}
	manager := NewManager("/native", proc)
	manager.MaxPayloadBytes = 8
	if err := manager.Start(context.Background()); err != nil {
		t.Fatalf("start: %v", err)
	}
	if _, err := manager.Send(context.Background(), protocol.EnvelopeQueryPage, "too-big", 1, map[string]string{"payload": "long"}); err == nil {
		t.Fatalf("oversized payload must be rejected")
	}
	manager.MaxPayloadBytes = MaxFrameBytes
	proc.stale = true
	if _, err := manager.Send(context.Background(), protocol.EnvelopeQueryPage, "stale", 1, map[string]string{"ok": "yes"}); err == nil {
		t.Fatalf("stale/mismatched runtime response must be rejected")
	}
}

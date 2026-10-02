package runtimeio

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"
)

const testProtocol = "dustui.android.runtime.session.v1"

func requestLine(t *testing.T, kind, requestID string, sequence int64, payload any) string {
	t.Helper()
	encodedPayload, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("payload: %v", err)
	}
	frame := Frame{
		Protocol:   testProtocol,
		Type:       kind,
		RequestID:  requestID,
		Sequence:   sequence,
		Generation: 1,
		Payload:    encodedPayload,
	}
	encoded, err := json.Marshal(frame)
	if err != nil {
		t.Fatalf("frame: %v", err)
	}
	return string(encoded) + "\n"
}

func decodeReplies(t *testing.T, output string) []Frame {
	t.Helper()
	lines := strings.Split(strings.TrimSpace(output), "\n")
	out := make([]Frame, 0, len(lines))
	for _, line := range lines {
		if strings.TrimSpace(line) == "" {
			continue
		}
		var frame Frame
		if err := json.Unmarshal([]byte(line), &frame); err != nil {
			t.Fatalf("decode reply %q: %v", line, err)
		}
		out = append(out, frame)
	}
	return out
}

func payloadError(t *testing.T, frame Frame) string {
	t.Helper()
	var payload map[string]any
	if err := json.Unmarshal(frame.Payload, &payload); err != nil {
		t.Fatalf("decode payload: %v", err)
	}
	value, _ := payload["error"].(string)
	return value
}

func TestRuntimeServeMalformedFrameDoesNotKillSession(t *testing.T) {
	input := "{not-json}\n" + requestLine(t, "domain.scan.latest", "req-2", 2, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	calls := 0
	err := ServeFrames(context.Background(), strings.NewReader(input), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 1024}, func(_ context.Context, frame Frame) (any, error) {
		calls++
		return map[string]any{"ok": true, "type": frame.Type}, nil
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	if calls != 1 {
		t.Fatalf("dispatch calls = %d, want 1", calls)
	}
	replies := decodeReplies(t, output.String())
	if len(replies) != 2 {
		t.Fatalf("replies = %d, want 2", len(replies))
	}
	if got := payloadError(t, replies[0]); got != "malformed_runtime_frame" {
		t.Fatalf("first error = %q", got)
	}
	if replies[1].RequestID != "req-2" {
		t.Fatalf("second request did not survive malformed predecessor: %+v", replies[1])
	}
	if !strings.Contains(stderr.String(), "malformed_runtime_frame") {
		t.Fatalf("missing malformed frame diagnostic: %s", stderr.String())
	}
}

func TestRuntimeServeHandlerPanicIsRequestScoped(t *testing.T) {
	input := requestLine(t, "panic", "req-1", 1, map[string]any{}) + requestLine(t, "healthy", "req-2", 2, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	err := ServeFrames(context.Background(), strings.NewReader(input), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 4096}, func(_ context.Context, frame Frame) (any, error) {
		if frame.Type == "panic" {
			panic("simulated handler panic")
		}
		return map[string]any{"ok": true}, nil
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	replies := decodeReplies(t, output.String())
	if len(replies) != 2 {
		t.Fatalf("replies = %d, want 2", len(replies))
	}
	if got := payloadError(t, replies[0]); got != "runtime_internal_error" {
		t.Fatalf("panic error = %q", got)
	}
	if replies[1].RequestID != "req-2" {
		t.Fatalf("runtime did not continue after panic: %+v", replies[1])
	}
	if !strings.Contains(stderr.String(), "runtime_handler_panic") || !strings.Contains(stderr.String(), "simulated handler panic") {
		t.Fatalf("panic evidence missing: %s", stderr.String())
	}
}

func TestRuntimeServePanicDiagnosticsAreBoundedAndCorrelated(t *testing.T) {
	input := requestLine(t, "panic", "req-correlated", 17, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	err := ServeFrames(context.Background(), strings.NewReader(input), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 4096}, func(_ context.Context, frame Frame) (any, error) {
		panic(strings.Repeat("p", 20_000))
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	line := strings.TrimSpace(stderr.String())
	if len(line) > 8_192 {
		t.Fatalf("diagnostic line is not bounded: %d bytes", len(line))
	}
	for _, want := range []string{`"code":"runtime_handler_panic"`, `"requestId":"req-correlated"`, `"requestType":"panic"`, `"sequence":17`} {
		if !strings.Contains(line, want) {
			t.Fatalf("diagnostic missing correlation %s: %s", want, line)
		}
	}
}

func TestRuntimeServeDomainErrorAndOversizedResponseAreNonFatal(t *testing.T) {
	input := requestLine(t, "domain-error", "req-1", 1, map[string]any{}) +
		requestLine(t, "large", "req-2", 2, map[string]any{}) +
		requestLine(t, "healthy", "req-3", 3, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	err := ServeFrames(context.Background(), strings.NewReader(input), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 1024}, func(_ context.Context, frame Frame) (any, error) {
		switch frame.Type {
		case "domain-error":
			return nil, errors.New("expected_domain_error")
		case "large":
			return map[string]any{"ok": true, "blob": strings.Repeat("x", 4096)}, nil
		default:
			return map[string]any{"ok": true}, nil
		}
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	replies := decodeReplies(t, output.String())
	if len(replies) != 3 {
		t.Fatalf("replies = %d, want 3", len(replies))
	}
	if got := payloadError(t, replies[0]); got != "expected_domain_error" {
		t.Fatalf("domain error = %q", got)
	}
	if got := payloadError(t, replies[1]); got != "response_exceeds_bounded_stream" {
		t.Fatalf("oversized response error = %q", got)
	}
	if replies[2].RequestID != "req-3" {
		t.Fatalf("runtime did not continue after oversized response: %+v", replies[2])
	}
}

func TestRuntimeServeOversizedRequestIsDrainedAndNextRequestRuns(t *testing.T) {
	oversized := strings.Repeat("x", 1500) + "\n"
	input := oversized + requestLine(t, "healthy", "req-2", 2, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	calls := 0
	err := ServeFrames(context.Background(), strings.NewReader(input), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 1024}, func(_ context.Context, frame Frame) (any, error) {
		calls++
		return map[string]any{"ok": true}, nil
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	if calls != 1 {
		t.Fatalf("dispatch calls = %d, want 1", calls)
	}
	replies := decodeReplies(t, output.String())
	if len(replies) != 2 {
		t.Fatalf("replies = %d, want 2", len(replies))
	}
	if got := payloadError(t, replies[0]); got != "request_exceeds_bounded_stream" {
		t.Fatalf("oversized request error = %q", got)
	}
	if replies[1].RequestID != "req-2" {
		t.Fatalf("next request lost after oversized frame: %+v", replies[1])
	}
}

type panicMarshaler struct{}

func (panicMarshaler) MarshalJSON() ([]byte, error) {
	panic("simulated response marshal panic")
}

func TestRuntimeServeResponseEncodePanicFallsBackAndContinues(t *testing.T) {
	input := requestLine(t, "encode-panic", "req-1", 1, map[string]any{}) + requestLine(t, "healthy", "req-2", 2, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	err := ServeFrames(context.Background(), strings.NewReader(input), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 4096}, func(_ context.Context, frame Frame) (any, error) {
		if frame.Type == "encode-panic" {
			return map[string]any{"ok": true, "value": panicMarshaler{}}, nil
		}
		return map[string]any{"ok": true}, nil
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	replies := decodeReplies(t, output.String())
	if len(replies) != 2 {
		t.Fatalf("replies = %d, want 2", len(replies))
	}
	if got := payloadError(t, replies[0]); got != "runtime_response_encode_failed" {
		t.Fatalf("encode panic error = %q", got)
	}
	if replies[1].RequestID != "req-2" {
		t.Fatalf("runtime did not continue after response encode panic: %+v", replies[1])
	}
	if !strings.Contains(stderr.String(), "runtime_response_encode_panic") {
		t.Fatalf("encode panic evidence missing: %s", stderr.String())
	}
}

func TestRuntimeServeProtocolAndIdentityFailuresStayRequestScoped(t *testing.T) {
	badProtocol := requestLine(t, "bad-protocol", "req-1", 1, map[string]any{})
	badProtocol = strings.Replace(badProtocol, testProtocol, "wrong.protocol", 1)
	missingIdentity := `{"protocol":"` + testProtocol + `","type":"missing-id","requestId":"","sequence":2,"generation":1,"payload":{}}` + "\n"
	good := requestLine(t, "healthy", "req-3", 3, map[string]any{})
	var output bytes.Buffer
	var stderr bytes.Buffer
	err := ServeFrames(context.Background(), strings.NewReader(badProtocol+missingIdentity+good), &output, &stderr, Config{Protocol: testProtocol, MaxFrameBytes: 2048}, func(_ context.Context, frame Frame) (any, error) {
		return map[string]any{"ok": true}, nil
	})
	if err != nil {
		t.Fatalf("serve: %v", err)
	}
	replies := decodeReplies(t, output.String())
	if len(replies) != 3 {
		t.Fatalf("replies = %d, want 3", len(replies))
	}
	if got := payloadError(t, replies[0]); got != "unsupported_session_protocol" {
		t.Fatalf("protocol error = %q", got)
	}
	if got := payloadError(t, replies[1]); got != "frame_identity_required" {
		t.Fatalf("identity error = %q", got)
	}
	if replies[2].RequestID != "req-3" {
		t.Fatalf("runtime did not survive protocol failures: %+v", replies[2])
	}
}

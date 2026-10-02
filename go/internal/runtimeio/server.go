package runtimeio

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"runtime/debug"
	"strings"
)

const (
	defaultReaderBuffer = 64 * 1024
	syntheticRequestID  = "runtime-protocol-error"
	syntheticType       = "runtime.error"
	maxDiagnosticDetail = 1024
	maxDiagnosticStack  = 4096
)

var errFrameTooLarge = errors.New("runtime_frame_exceeds_bounded_stream")

// Config defines the bounded newline-delimited JSON protocol used by the
// Android-owned long-lived Go child process.
type Config struct {
	Protocol      string
	MaxFrameBytes int
}

// Frame is the transport envelope exchanged with the Android process bridge.
type Frame struct {
	Protocol   string          `json:"protocol"`
	Type       string          `json:"type"`
	RequestID  string          `json:"requestId"`
	Sequence   int64           `json:"sequence"`
	Generation int64           `json:"generation"`
	Payload    json.RawMessage `json:"payload"`
}

// Dispatch handles a validated request. Request-level errors are returned to
// the caller as structured payload failures and do not terminate the runtime.
type Dispatch func(context.Context, Frame) (any, error)

type reply struct {
	frame   Frame
	payload any
}

// ServeFrames owns the resilient request loop after the readiness handshake.
// Only transport closure/read/write failures are terminal. Malformed requests,
// handler panics, handler errors, and oversized responses are isolated to the
// affected request so they cannot collapse the long-lived runtime into EOF.
func ServeFrames(
	ctx context.Context,
	in io.Reader,
	out io.Writer,
	errOut io.Writer,
	config Config,
	dispatch Dispatch,
) error {
	if strings.TrimSpace(config.Protocol) == "" {
		return errors.New("runtime_protocol_required")
	}
	if config.MaxFrameBytes <= 0 {
		return errors.New("runtime_max_frame_bytes_required")
	}
	if dispatch == nil {
		return errors.New("runtime_dispatch_required")
	}
	if errOut == nil {
		errOut = io.Discard
	}

	reader := bufio.NewReaderSize(in, defaultReaderBuffer)
	writer := bufio.NewWriterSize(out, defaultReaderBuffer)
	for {
		line, err := readBoundedLine(reader, config.MaxFrameBytes)
		switch {
		case errors.Is(err, io.EOF):
			return nil
		case errors.Is(err, errFrameTooLarge):
			logFault(errOut, "request_exceeds_bounded_stream", Frame{}, "request frame exceeded configured maximum", "")
			if writeErr := writeReply(writer, config, errOut, errorReply(config, Frame{}, "request_exceeds_bounded_stream")); writeErr != nil {
				return writeErr
			}
			continue
		case err != nil:
			return fmt.Errorf("runtime transport read: %w", err)
		}

		if strings.TrimSpace(line) == "" {
			continue
		}
		response := handleLine(ctx, config, errOut, line, dispatch)
		if err := writeReply(writer, config, errOut, response); err != nil {
			return err
		}
	}
}

func handleLine(ctx context.Context, config Config, errOut io.Writer, line string, dispatch Dispatch) (result reply) {
	frame := normalizeIdentity(config, Frame{})
	result = errorReply(config, frame, "runtime_internal_error")
	defer func() {
		if recovered := recover(); recovered != nil {
			logFault(
				errOut,
				"runtime_handler_panic",
				frame,
				fmt.Sprintf("panic=%v", recovered),
				string(debug.Stack()),
			)
			result = errorReply(config, frame, "runtime_internal_error")
		}
	}()

	var decoded Frame
	if err := json.Unmarshal([]byte(line), &decoded); err != nil {
		logFault(errOut, "malformed_runtime_frame", frame, err.Error(), "")
		return errorReply(config, frame, "malformed_runtime_frame")
	}
	frame = decoded
	if frame.Protocol != config.Protocol {
		logFault(errOut, "unsupported_session_protocol", frame, frame.Protocol, "")
		return errorReply(config, frame, "unsupported_session_protocol")
	}
	if frame.RequestID == "" || frame.Type == "" || frame.Sequence <= 0 || frame.Generation <= 0 {
		logFault(errOut, "frame_identity_required", frame, "missing or invalid request identity", "")
		return errorReply(config, frame, "frame_identity_required")
	}

	payload, err := dispatch(ctx, frame)
	if err != nil {
		payload = map[string]any{"ok": false, "error": err.Error()}
	}
	return reply{frame: normalizeIdentity(config, frame), payload: payload}
}

func errorReply(config Config, frame Frame, code string) reply {
	return reply{
		frame: normalizeIdentity(config, frame),
		payload: map[string]any{
			"ok":    false,
			"error": code,
		},
	}
}

func normalizeIdentity(config Config, frame Frame) Frame {
	frame.Protocol = config.Protocol
	if strings.TrimSpace(frame.Type) == "" {
		frame.Type = syntheticType
	}
	if strings.TrimSpace(frame.RequestID) == "" {
		frame.RequestID = syntheticRequestID
	}
	if frame.Sequence <= 0 {
		frame.Sequence = 1
	}
	if frame.Generation <= 0 {
		frame.Generation = 1
	}
	return frame
}

func writeReply(writer *bufio.Writer, config Config, errOut io.Writer, response reply) (err error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			logFault(errOut, "runtime_response_encode_panic", response.frame, fmt.Sprintf("panic=%v", recovered), string(debug.Stack()))
			fallback := errorReply(config, response.frame, "runtime_response_encode_failed")
			frame := normalizeIdentity(config, fallback.frame)
			frame.Payload, _ = json.Marshal(fallback.payload)
			encoded, encodeErr := json.Marshal(frame)
			if encodeErr != nil {
				err = fmt.Errorf("runtime fallback response encode: %w", encodeErr)
				return
			}
			if _, writeErr := writer.Write(encoded); writeErr != nil {
				err = fmt.Errorf("runtime fallback transport write: %w", writeErr)
				return
			}
			if writeErr := writer.WriteByte('\n'); writeErr != nil {
				err = fmt.Errorf("runtime fallback transport newline: %w", writeErr)
				return
			}
			if writeErr := writer.Flush(); writeErr != nil {
				err = fmt.Errorf("runtime fallback transport flush: %w", writeErr)
				return
			}
			err = nil
		}
	}()

	frame := normalizeIdentity(config, response.frame)
	payload, marshalErr := json.Marshal(response.payload)
	if marshalErr != nil {
		logFault(errOut, "runtime_response_encode_failed", frame, marshalErr.Error(), "")
		payload, _ = json.Marshal(map[string]any{"ok": false, "error": "runtime_response_encode_failed"})
	}
	frame.Payload = payload

	encoded, marshalErr := json.Marshal(frame)
	if marshalErr != nil {
		return fmt.Errorf("runtime response frame encode: %w", marshalErr)
	}
	if len(encoded) > config.MaxFrameBytes {
		logFault(errOut, "response_exceeds_bounded_stream", frame, fmt.Sprintf("responseBytes=%d maxFrameBytes=%d", len(encoded), config.MaxFrameBytes), "")
		frame.Payload, _ = json.Marshal(map[string]any{
			"ok":            false,
			"error":         "response_exceeds_bounded_stream",
			"responseBytes": len(encoded),
			"maxFrameBytes": config.MaxFrameBytes,
		})
		encoded, marshalErr = json.Marshal(frame)
		if marshalErr != nil {
			return fmt.Errorf("runtime bounded response encode: %w", marshalErr)
		}
	}
	if _, err := writer.Write(encoded); err != nil {
		return fmt.Errorf("runtime transport write: %w", err)
	}
	if err := writer.WriteByte('\n'); err != nil {
		return fmt.Errorf("runtime transport write newline: %w", err)
	}
	if err := writer.Flush(); err != nil {
		return fmt.Errorf("runtime transport flush: %w", err)
	}
	return nil
}

func readBoundedLine(reader *bufio.Reader, maxBytes int) (string, error) {
	var line []byte
	for {
		fragment, err := reader.ReadSlice('\n')
		if len(line)+len(fragment) > maxBytes+1 {
			if errors.Is(err, bufio.ErrBufferFull) {
				drainLine(reader)
			}
			return "", errFrameTooLarge
		}
		line = append(line, fragment...)
		switch {
		case err == nil:
			value := strings.TrimSuffix(string(line), "\n")
			value = strings.TrimSuffix(value, "\r")
			if len([]byte(value)) > maxBytes {
				return "", errFrameTooLarge
			}
			return value, nil
		case errors.Is(err, bufio.ErrBufferFull):
			continue
		case errors.Is(err, io.EOF):
			if len(line) == 0 {
				return "", io.EOF
			}
			if len(line) > maxBytes {
				return "", errFrameTooLarge
			}
			return string(line), nil
		default:
			return "", err
		}
	}
}

func drainLine(reader *bufio.Reader) {
	for {
		_, err := reader.ReadSlice('\n')
		if !errors.Is(err, bufio.ErrBufferFull) {
			return
		}
	}
}

func logFault(errOut io.Writer, code string, frame Frame, detail string, stack string) {
	values := map[string]any{
		"component":   "runtime_serve",
		"code":        code,
		"requestType": frame.Type,
		"requestId":   frame.RequestID,
		"sequence":    frame.Sequence,
		"generation":  frame.Generation,
	}
	if strings.TrimSpace(detail) != "" {
		values["detail"] = tailBounded(detail, maxDiagnosticDetail)
	}
	if strings.TrimSpace(stack) != "" {
		values["stack"] = tailBounded(stack, maxDiagnosticStack)
	}
	encoded, err := json.Marshal(values)
	if err != nil {
		fmt.Fprintf(errOut, "runtime_serve code=%s detail=%s\n", code, detail)
		return
	}
	_, _ = fmt.Fprintln(errOut, string(encoded))
}

func tailBounded(value string, maxChars int) string {
	if maxChars <= 0 || len(value) <= maxChars {
		return value
	}
	return value[len(value)-maxChars:]
}

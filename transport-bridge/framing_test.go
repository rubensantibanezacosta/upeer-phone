package transportbridge

import (
	"bytes"
	"encoding/binary"
	"errors"
	"io"
	"testing"
)

type fragmentedReader struct {
	data []byte
	size int
}

func (reader *fragmentedReader) Read(target []byte) (int, error) {
	if len(reader.data) == 0 {
		return 0, io.EOF
	}
	limit := reader.size
	if limit > len(reader.data) {
		limit = len(reader.data)
	}
	if limit > len(target) {
		limit = len(target)
	}
	copy(target, reader.data[:limit])
	reader.data = reader.data[limit:]
	return limit, nil
}

type shortWriter struct {
	buffer bytes.Buffer
	size   int
}

func (writer *shortWriter) Write(payload []byte) (int, error) {
	limit := writer.size
	if limit > len(payload) {
		limit = len(payload)
	}
	return writer.buffer.Write(payload[:limit])
}

func TestFramingRoundTripAcrossFragments(t *testing.T) {
	payload := []byte(`{"type":"PING","value":"á"}`)
	var encoded bytes.Buffer
	if err := writeFrame(&encoded, payload); err != nil {
		t.Fatalf("write frame: %v", err)
	}
	decoded, err := readFrame(&fragmentedReader{data: encoded.Bytes(), size: 1})
	if err != nil {
		t.Fatalf("read fragmented frame: %v", err)
	}
	if !bytes.Equal(decoded, payload) {
		t.Fatalf("decoded frame = %q, want %q", decoded, payload)
	}
}

func TestFramingReadsCoalescedFrames(t *testing.T) {
	first := []byte(`{"type":"PING"}`)
	second := []byte(`{"type":"PONG"}`)
	var encoded bytes.Buffer
	if err := writeFrame(&encoded, first); err != nil {
		t.Fatal(err)
	}
	if err := writeFrame(&encoded, second); err != nil {
		t.Fatal(err)
	}
	reader := bytes.NewReader(encoded.Bytes())
	decodedFirst, err := readFrame(reader)
	if err != nil {
		t.Fatalf("read first frame: %v", err)
	}
	decodedSecond, err := readFrame(reader)
	if err != nil {
		t.Fatalf("read second frame: %v", err)
	}
	if !bytes.Equal(decodedFirst, first) || !bytes.Equal(decodedSecond, second) {
		t.Fatalf("coalesced frames = %q, %q", decodedFirst, decodedSecond)
	}
}

func TestFramingRejectsInvalidLengths(t *testing.T) {
	for _, length := range []uint32{0, MaxFrameBytes + 1} {
		header := make([]byte, 4)
		binary.BigEndian.PutUint32(header, length)
		if _, err := readFrame(bytes.NewReader(header)); err == nil {
			t.Fatalf("readFrame accepted length %d", length)
		}
	}
	if err := writeFrame(io.Discard, nil); !errors.Is(err, ErrEmptyFrame) {
		t.Fatalf("writeFrame empty error = %v", err)
	}
	if err := writeFrame(io.Discard, make([]byte, MaxFrameBytes+1)); !errors.Is(err, ErrFrameTooLarge) {
		t.Fatalf("writeFrame oversized error = %v", err)
	}
}

func TestFramingHandlesShortWritesAndTruncatedPayload(t *testing.T) {
	writer := &shortWriter{size: 2}
	if err := writeFrame(writer, []byte("payload")); err != nil {
		t.Fatalf("write frame through short writer: %v", err)
	}
	if _, err := readFrame(bytes.NewReader(writer.buffer.Bytes())); err != nil {
		t.Fatalf("read short-written frame: %v", err)
	}
	truncated := []byte{0, 0, 0, 5, 'a'}
	if _, err := readFrame(bytes.NewReader(truncated)); err == nil {
		t.Fatal("readFrame accepted truncated payload")
	}
}

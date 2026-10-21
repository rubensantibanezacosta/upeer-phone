package transportbridge

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"time"
)

const MaxFrameBytes = 10 * 1024 * 1024

var (
	ErrEmptyFrame    = errors.New("frame payload is empty")
	ErrFrameTooLarge = errors.New("frame exceeds maximum size")
)

func readFrame(reader io.Reader) ([]byte, error) {
	header := make([]byte, 4)
	read, err := io.ReadFull(reader, header)
	if err != nil {
		if read == 0 && errors.Is(err, io.EOF) {
			return nil, io.EOF
		}
		return nil, fmt.Errorf("read frame header: %w", err)
	}
	length := binary.BigEndian.Uint32(header)
	if length == 0 {
		return nil, ErrEmptyFrame
	}
	if length > MaxFrameBytes {
		return nil, fmt.Errorf("%w: %d", ErrFrameTooLarge, length)
	}
	payload := make([]byte, int(length))
	if _, err := io.ReadFull(reader, payload); err != nil {
		return nil, fmt.Errorf("read frame payload: %w", err)
	}
	return payload, nil
}

func writeFrame(writer io.Writer, payload []byte) error {
	if len(payload) == 0 {
		return ErrEmptyFrame
	}
	if len(payload) > MaxFrameBytes {
		return fmt.Errorf("%w: %d", ErrFrameTooLarge, len(payload))
	}
	header := make([]byte, 4)
	binary.BigEndian.PutUint32(header, uint32(len(payload)))
	if err := writeAll(writer, header); err != nil {
		return fmt.Errorf("write frame header: %w", err)
	}
	if err := writeAll(writer, payload); err != nil {
		return fmt.Errorf("write frame payload: %w", err)
	}
	return nil
}

func writeAll(writer io.Writer, payload []byte) error {
	for len(payload) > 0 {
		written, err := writer.Write(payload)
		if written > 0 {
			payload = payload[written:]
		}
		if err != nil {
			return err
		}
		if written == 0 {
			return io.ErrShortWrite
		}
	}
	return nil
}

func (b *Bridge) ReadFrame(connectionID int32, timeoutMillis int) ([]byte, error) {
	if timeoutMillis < 0 || timeoutMillis > 300_000 {
		return nil, errors.New("frame read timeout must be between 0 and 300000 milliseconds")
	}
	conn, err := b.connection(connectionID)
	if err != nil {
		return nil, err
	}
	deadline := time.Time{}
	if timeoutMillis > 0 {
		deadline = time.Now().Add(time.Duration(timeoutMillis) * time.Millisecond)
	}
	if err := conn.SetReadDeadline(deadline); err != nil {
		return nil, fmt.Errorf("set Yggdrasil frame read deadline: %w", err)
	}
	return readFrame(conn)
}

func (b *Bridge) WriteFrame(connectionID int32, payload []byte) error {
	conn, err := b.connection(connectionID)
	if err != nil {
		return err
	}
	if err := conn.SetWriteDeadline(time.Now().Add(30 * time.Second)); err != nil {
		return fmt.Errorf("set Yggdrasil frame write deadline: %w", err)
	}
	if err := writeFrame(conn, payload); err != nil {
		return fmt.Errorf("write Yggdrasil frame: %w", err)
	}
	return nil
}

package transportbridge

import (
	"crypto/rand"
	"errors"
	"fmt"
	"net"
	"sync"
	"time"
)

const listenerQueueSize = 32

type listenerState struct {
	listener  net.Listener
	accepted  chan net.Conn
	done      chan struct{}
	closeOnce sync.Once
	port      int
}

func (b *Bridge) ListenTCP(port int) (int32, error) {
	if port < 1 || port > 65535 {
		return 0, errors.New("listen port must be between 1 and 65535")
	}
	b.mutex.Lock()
	if !b.running || b.stack == nil || b.node == nil {
		b.mutex.Unlock()
		return 0, errors.New("Yggdrasil bridge is not started")
	}
	stack := b.stack
	address := b.node.Address()
	b.mutex.Unlock()
	listener, err := stack.ListenTCP(&net.TCPAddr{IP: address, Port: port})
	if err != nil {
		return 0, fmt.Errorf("listen on Yggdrasil TCP port %d: %w", port, err)
	}
	state := &listenerState{
		listener: listener,
		accepted: make(chan net.Conn, listenerQueueSize),
		done:     make(chan struct{}),
		port:     port,
	}
	b.mutex.Lock()
	if !b.running || b.stack != stack {
		b.mutex.Unlock()
		closeListenerState(state)
		return 0, errors.New("Yggdrasil bridge stopped while opening listener")
	}
	listenerID, err := nextListenerID(b.listeners)
	if err != nil {
		b.mutex.Unlock()
		closeListenerState(state)
		return 0, err
	}
	b.listeners[listenerID] = state
	b.mutex.Unlock()
	go b.acceptLoop(state)
	return listenerID, nil
}

func (b *Bridge) AcceptTCP(listenerID int32, timeoutMillis int) (int32, error) {
	if timeoutMillis < 0 || timeoutMillis > 300_000 {
		return 0, errors.New("accept timeout must be between 0 and 300000 milliseconds")
	}
	state, err := b.listener(listenerID)
	if err != nil {
		return 0, err
	}
	var timeout <-chan time.Time
	if timeoutMillis > 0 {
		timer := time.NewTimer(time.Duration(timeoutMillis) * time.Millisecond)
		defer timer.Stop()
		timeout = timer.C
	}
	select {
	case connection := <-state.accepted:
		return b.storeConnection(connection)
	case <-timeout:
		return 0, nil
	case <-state.done:
		return 0, errors.New("Yggdrasil TCP listener is closed")
	}
}

func (b *Bridge) CloseListener(listenerID int32) error {
	b.mutex.Lock()
	state, ok := b.listeners[listenerID]
	if ok {
		delete(b.listeners, listenerID)
	}
	b.mutex.Unlock()
	if !ok {
		return fmt.Errorf("unknown Yggdrasil listener ID %d", listenerID)
	}
	return closeListenerState(state)
}

func (b *Bridge) listener(listenerID int32) (*listenerState, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return nil, errors.New("Yggdrasil bridge is not started")
	}
	state, ok := b.listeners[listenerID]
	if !ok {
		return nil, fmt.Errorf("unknown Yggdrasil listener ID %d", listenerID)
	}
	return state, nil
}

func (b *Bridge) acceptLoop(state *listenerState) {
	for {
		connection, err := state.listener.Accept()
		if err != nil {
			closeListenerState(state)
			return
		}
		select {
		case state.accepted <- connection:
		case <-state.done:
			connection.Close()
			return
		}
	}
}

func closeListenerState(state *listenerState) error {
	var closeErr error
	state.closeOnce.Do(func() {
		close(state.done)
		closeErr = state.listener.Close()
		for {
			select {
			case connection := <-state.accepted:
				connection.Close()
			default:
				return
			}
		}
	})
	return closeErr
}

func nextListenerID(listeners map[int32]*listenerState) (int32, error) {
	var randomID [4]byte
	if _, err := rand.Read(randomID[:]); err != nil {
		return 0, fmt.Errorf("generate Yggdrasil listener ID: %w", err)
	}
	listenerID := int32(randomID[0])<<24 | int32(randomID[1])<<16 | int32(randomID[2])<<8 | int32(randomID[3])
	for listenerID == 0 || listeners[listenerID] != nil {
		listenerID++
	}
	return listenerID, nil
}

func (b *Bridge) ValidateControlFrame(payload []byte) (string, error) {
	return validateControlFrameFromPayload(payload)
}

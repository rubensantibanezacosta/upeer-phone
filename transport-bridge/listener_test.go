package transportbridge

import "testing"

func TestListenTCPLifecycle(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatalf("start bridge for listener test: %v", err)
	}
	defer bridge.Stop()
	if _, err := bridge.ListenTCP(0); err == nil {
		t.Fatal("ListenTCP accepted port 0")
	}
	listenerID, err := bridge.ListenTCP(50005)
	if err != nil {
		t.Fatalf("listen on TCP 50005: %v", err)
	}
	connectionID, err := bridge.AcceptTCP(listenerID, 50)
	if err != nil {
		t.Fatalf("accept with timeout: %v", err)
	}
	if connectionID != 0 {
		t.Fatalf("accept timeout connection ID = %d", connectionID)
	}
	if err := bridge.CloseListener(listenerID); err != nil {
		t.Fatalf("close listener: %v", err)
	}
	if _, err := bridge.AcceptTCP(listenerID, 0); err == nil {
		t.Fatal("AcceptTCP accepted a closed listener")
	}
}

func TestListenTCPRoundTripsFramedPayload(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatalf("start bridge for loopback test: %v", err)
	}
	defer bridge.Stop()
	listenerID, err := bridge.ListenTCP(50005)
	if err != nil {
		t.Fatalf("listen on TCP 50005: %v", err)
	}
	address, err := bridge.Address()
	if err != nil {
		t.Fatal(err)
	}
	dialID, err := bridge.DialTCP(address, 50005)
	if err != nil {
		t.Fatalf("dial loopback listener: %v", err)
	}
	defer bridge.CloseConnection(dialID)
	acceptID, err := bridge.AcceptTCP(listenerID, 1000)
	if err != nil {
		t.Fatalf("accept loopback connection: %v", err)
	}
	defer bridge.CloseConnection(acceptID)
	payload := []byte(`{"type":"PING","value":"loopback"}`)
	if err := bridge.WriteFrame(dialID, payload); err != nil {
		t.Fatalf("write framed payload: %v", err)
	}
	received, err := bridge.ReadFrame(acceptID, 1000)
	if err != nil {
		t.Fatalf("read framed payload: %v", err)
	}
	if string(received) != string(payload) {
		t.Fatalf("received frame = %q, want %q", received, payload)
	}
}

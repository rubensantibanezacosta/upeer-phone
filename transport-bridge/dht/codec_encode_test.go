package dht

import (
	"strings"
	"testing"
)

func TestEncodeKademliaMessagesRoundTrips(t *testing.T) {
	messages := []any{
		&DhtPing{QueryID: "q1", NodeID: strings.Repeat("aa", 20)},
		&DhtPong{QueryID: "q2", NodeID: strings.Repeat("bb", 20)},
		&DhtFindNode{QueryID: "q3", TargetID: strings.Repeat("cc", 20)},
		&DhtFoundNodes{QueryID: "q4", Nodes: []DhtNode{{
			UpeerID: strings.Repeat("dd", 16), Address: "200::1",
			PublicKey: strings.Repeat("ee", 32), NodeID: strings.Repeat("ff", 20),
		}}},
		&DhtFindValue{QueryID: "q5", Key: strings.Repeat("aa", 20)},
		&DhtFoundValue{QueryID: "q6", Key: strings.Repeat("aa", 20), Value: "value", Publisher: strings.Repeat("dd", 16), Timestamp: timestampPointer(123)},
		&DhtStore{QueryID: "q7", Key: strings.Repeat("aa", 20), Value: "value", Publisher: strings.Repeat("dd", 16), Timestamp: timestampPointer(123)},
		&DhtStoreAck{QueryID: "q8", Key: strings.Repeat("aa", 20)},
	}
	for _, message := range messages {
		packet, err := EncodeMessage(message)
		if err != nil {
			t.Fatal(err)
		}
		encoded, err := EncodeMessageJSON(message)
		if err != nil {
			t.Fatal(err)
		}
		decoded, err := DecodePacketJSON(encoded)
		if err != nil {
			t.Fatal(err)
		}
		if decoded.Type != packet["type"] {
			t.Fatalf("encoded %v decoded as %s", packet["type"], decoded.Type)
		}
	}
}

func TestEncodeFoundNodesOmitsMissingFieldsAndRejectsOversizeValues(t *testing.T) {
	packet, err := EncodeMessage(&DhtFoundNodes{Nodes: []DhtNode{{
		UpeerID: strings.Repeat("aa", 16), Address: "200::1",
	}}})
	if err != nil {
		t.Fatal(err)
	}
	if _, exists := packet["queryId"]; exists {
		t.Fatal("empty query identifier must be omitted")
	}
	if packet["type"] != "DHT_FOUND_NODES" {
		t.Fatalf("unexpected packet type %v", packet["type"])
	}
	_, err = EncodeMessage(&DhtStore{
		Key: strings.Repeat("aa", 20), Value: strings.Repeat("v", MaxStoredValueBytes+1),
	})
	if err == nil {
		t.Fatal("expected oversized value to be rejected")
	}
}

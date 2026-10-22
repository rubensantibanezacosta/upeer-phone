package dht

import (
	"encoding/json"
	"os"
	"strings"
	"testing"
)

type codecFixture struct {
	Identity struct {
		PublicKey string `json:"publicKey"`
		UpeerID   string `json:"upeerId"`
		Address   string `json:"address"`
	} `json:"identity"`
	SignedPackets []struct {
		Name   string         `json:"name"`
		Packet map[string]any `json:"packet"`
	} `json:"signedPackets"`
}

func loadCodecFixture(t *testing.T) codecFixture {
	t.Helper()
	file, err := os.Open("../testdata/dht_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	decoder := json.NewDecoder(file)
	decoder.UseNumber()
	var fixture codecFixture
	if err := decoder.Decode(&fixture); err != nil {
		t.Fatal(err)
	}
	return fixture
}

func TestDecodeDesktopPacketVectors(t *testing.T) {
	fixture := loadCodecFixture(t)
	if len(fixture.SignedPackets) < 12 {
		t.Fatalf("expected 12 signed packets, got %d", len(fixture.SignedPackets))
	}
	for _, vector := range fixture.SignedPackets {
		decoded, err := DecodePacketObject(vector.Packet)
		if err != nil {
			t.Fatalf("%s: %v", vector.Name, err)
		}
		if decoded.Type != vector.Name {
			t.Fatalf("%s decoded as %s", vector.Name, decoded.Type)
		}
	}
}

func TestDecodeRejectsInvalidCorpus(t *testing.T) {
	fixture := loadCodecFixture(t)
	validPacket := fixture.SignedPackets[0].Packet
	validJSON := marshalPacket(t, validPacket)

	expectCodecError(t, "empty packet", func() error {
		_, err := DecodePacketJSON("")
		return err
	})
	expectCodecError(t, "packet too large", func() error {
		_, err := DecodePacketJSON(strings.Repeat("a", MaxPacketJSONBytes+1))
		return err
	})
	expectCodecError(t, "truncated JSON", func() error {
		_, err := DecodePacketJSON(validJSON[:len(validJSON)-1])
		return err
	})
	expectCodecError(t, "invalid JSON", func() error {
		_, err := DecodePacketJSON(`{"type":}`)
		return err
	})
	expectCodecError(t, "unsupported packet type", func() error {
		_, err := DecodePacketJSON(`{"type":"DHT_UNKNOWN"}`)
		return err
	})
	expectCodecError(t, "missing packet type", func() error {
		_, err := DecodePacketJSON(`{"queryId":"q"}`)
		return err
	})

	extra := copyPacket(validPacket)
	extra["injected"] = true
	expectCodecError(t, "unexpected fields: injected", func() error {
		_, err := DecodePacketObject(extra)
		return err
	})

	nested := `{"type":"DHT_STORE","key":"` + strings.Repeat("a", 40) + `","value":` +
		strings.Repeat("[", MaxPacketJSONDepth+1) + "1" + strings.Repeat("]", MaxPacketJSONDepth+1) + "}"
	expectCodecError(t, "JSON is too deeply nested", func() error {
		_, err := DecodePacketJSON(nested)
		return err
	})
}

func TestDecodeEnforcesDhtLimits(t *testing.T) {
	expectCodecError(t, "invalid queryId", func() error {
		_, err := DecodePacketObject(map[string]any{
			"type": "DHT_PING", "queryId": strings.Repeat("q", MaxQueryIDLength+1),
		})
		return err
	})
	expectCodecError(t, "invalid key", func() error {
		_, err := DecodePacketObject(map[string]any{"type": "DHT_FIND_VALUE", "key": "short"})
		return err
	})
	expectCodecError(t, "invalid targetId", func() error {
		_, err := DecodePacketObject(map[string]any{"type": "DHT_QUERY", "targetId": "short"})
		return err
	})
	expectCodecError(t, "too many nodes", func() error {
		nodes := make([]any, MaxResponseNodes+1)
		for index := range nodes {
			nodes[index] = map[string]any{"upeerId": strings.Repeat("a", 64), "address": "200::1"}
		}
		_, err := DecodePacketObject(map[string]any{"type": "DHT_FOUND_NODES", "nodes": nodes})
		return err
	})
	expectCodecError(t, "too many peers", func() error {
		peers := make([]any, MaxExchangePeers+1)
		for index := range peers {
			peers[index] = map[string]any{
				"upeerId": strings.Repeat("a", 64), "publicKey": strings.Repeat("b", 64),
			}
		}
		_, err := DecodePacketObject(map[string]any{"type": "DHT_EXCHANGE", "peers": peers})
		return err
	})
	expectCodecError(t, "value too large", func() error {
		_, err := DecodePacketObject(map[string]any{
			"type": "DHT_STORE", "key": strings.Repeat("a", 40),
			"value": strings.Repeat("v", MaxStoredValueBytes+1),
		})
		return err
	})
	expectCodecError(t, "invalid TTL", func() error {
		_, err := DecodePacketObject(map[string]any{
			"type": "DHT_STORE", "key": strings.Repeat("a", 40), "value": "ok", "ttl": int64(MaxStoreTTLSeconds + 1),
		})
		return err
	})
	expectCodecError(t, "invalid locationBlock.signature", func() error {
		_, err := DecodePacketObject(map[string]any{
			"type": "DHT_UPDATE",
			"locationBlock": map[string]any{
				"address": "200::1", "dhtSeq": 1, "signature": strings.Repeat("a", 127),
			},
		})
		return err
	})
}

func marshalPacket(t *testing.T, packet map[string]any) string {
	t.Helper()
	encoded, err := json.Marshal(packet)
	if err != nil {
		t.Fatal(err)
	}
	return string(encoded)
}

func copyPacket(packet map[string]any) map[string]any {
	copy := make(map[string]any, len(packet))
	for key, value := range packet {
		copy[key] = value
	}
	return copy
}

func expectCodecError(t *testing.T, expected string, block func() error) {
	t.Helper()
	err := block()
	if err == nil || !strings.Contains(err.Error(), expected) {
		t.Fatalf("expected error containing %q, got %v", expected, err)
	}
}

package dht

import (
	"encoding/json"
	"strings"
	"testing"
)

func FuzzDecodePacketJSON(f *testing.F) {
	fixture := loadCodecFixture(f)
	for _, vector := range fixture.SignedPackets {
		encoded, err := json.Marshal(vector.Packet)
		if err != nil {
			f.Fatalf("%s: %v", vector.Name, err)
		}
		f.Add(string(encoded))
	}
	validPacket := fixture.SignedPackets[0].Packet
	validJSON, err := json.Marshal(validPacket)
	if err != nil {
		f.Fatalf("marshal valid packet: %v", err)
	}
	extra := copyPacket(validPacket)
	extra["injected"] = true
	extraJSON, err := json.Marshal(extra)
	if err != nil {
		f.Fatalf("marshal packet with unexpected field: %v", err)
	}
	f.Add("")
	f.Add("{}")
	f.Add(`{"type":"DHT_UNKNOWN"}`)
	f.Add(`{"type":"DHT_STORE","key":"` + strings.Repeat("a", 40) + `","value":` +
		strings.Repeat("[", MaxPacketJSONDepth+1) + "1" + strings.Repeat("]", MaxPacketJSONDepth+1) + `}`)
	f.Add(string(validJSON)[:len(validJSON)-1])
	f.Add(string(extraJSON))
	f.Add(strings.Repeat("a", MaxPacketJSONBytes+1))

	f.Fuzz(func(t *testing.T, packetJSON string) {
		decoded, err := DecodePacketJSON(packetJSON)
		if err != nil {
			return
		}
		if len(packetJSON) > MaxPacketJSONBytes {
			t.Fatalf("accepted packet of %d bytes over the %d byte cap", len(packetJSON), MaxPacketJSONBytes)
		}
		if decoded.Type == "" {
			t.Fatal("decoded packet without type")
		}
		reencoded, err := EncodeMessageJSON(decoded.Message)
		if err != nil {
			t.Fatalf("%s re-encode: %v", decoded.Type, err)
		}
		if len(reencoded) > MaxPacketJSONBytes {
			t.Fatalf("%s re-encoded to %d bytes over the cap", decoded.Type, len(reencoded))
		}
		again, err := DecodePacketJSON(reencoded)
		if err != nil {
			t.Fatalf("%s re-encoded packet no longer decodes: %v", decoded.Type, err)
		}
		if again.Type != decoded.Type {
			t.Fatalf("%s re-encoded as %s", decoded.Type, again.Type)
		}
		if again.QueryID != decoded.QueryID {
			t.Fatalf("%s changed its queryId on re-encode: %q vs %q", decoded.Type, decoded.QueryID, again.QueryID)
		}
	})
}

func FuzzVerifySignedPacket(f *testing.F) {
	fixture := loadCodecFixture(f)
	publicKey := fixture.Identity.PublicKey
	f.Add(`{"payload":"","signature":"","senderYggAddress":""}`)
	f.Add(`not json`)
	f.Add(`{"payload":{},"signature":"00","senderYggAddress":"200::1"}`)

	f.Fuzz(func(t *testing.T, packetJSON string) {
		first, err := VerifySignedPacket(packetJSON, publicKey)
		if err != nil {
			return
		}
		second, err := VerifySignedPacket(packetJSON, publicKey)
		if err != nil {
			t.Fatalf("verification of an accepted packet became unstable: %v", err)
		}
		if first != second {
			t.Fatalf("verification returned different payloads for the same input")
		}
	})
}

func FuzzDecodeLocationBlock(f *testing.F) {
	f.Add(`{}`)
	f.Add(`{"addresses":[]}`)
	f.Add(`null`)

	f.Fuzz(func(t *testing.T, blockJSON string) {
		if _, err := DecodeLocationBlock(blockJSON); err != nil {
			return
		}
		if _, err := DecodeLocationBlock(blockJSON); err != nil {
			t.Fatalf("decoding of an accepted block became unstable: %v", err)
		}
	})
}

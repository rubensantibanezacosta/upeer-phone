package transportbridge

import (
	"encoding/json"
	"strings"
	"testing"
)

const handshakeRequestVector = `{"type":"HANDSHAKE_REQ","publicKey":"79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664","ephemeralPublicKey":"1111111111111111111111111111111111111111111111111111111111111111","signedPreKey":{"spkId":1,"spkPub":"2222222222222222222222222222222222222222222222222222222222222222","spkSig":"33333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333"},"alias":"Nexo <uPeer> á","addresses":["200:1234::1"],"isInternalSync":false,"senderUpeerId":"4d4dbe917544b07922348a66b9c4b5a5a5f34a9ffb319915c39409c7d0ff230a","senderYggAddress":"200:1234::1","signature":"211216f171370e196652c65652fa269c390981169eab89a2d5402a47a30c83fd0345e500334430d3fb0522ccbd7647ce4c69b3aeb703ffdf994653bdc10fc301"}`

const handshakeRequestCanonical = `{"addresses":["200:1234::1"],"alias":"Nexo <uPeer> á","ephemeralPublicKey":"1111111111111111111111111111111111111111111111111111111111111111","isInternalSync":false,"publicKey":"79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664","senderUpeerId":"4d4dbe917544b07922348a66b9c4b5a5a5f34a9ffb319915c39409c7d0ff230a","senderYggAddress":"200:1234::1","signedPreKey":{"spkId":1,"spkPub":"2222222222222222222222222222222222222222222222222222222222222222","spkSig":"33333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333"},"type":"HANDSHAKE_REQ"}`

const handshakeAcceptVector = `{"type":"HANDSHAKE_ACCEPT","publicKey":"79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664","ephemeralPublicKey":"4444444444444444444444444444444444444444444444444444444444444444","signedPreKey":{"spkId":2,"spkPub":"5555555555555555555555555555555555555555555555555555555555555555","spkSig":"66666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666"},"alias":"Aceptado <uPeer>","addresses":["200:1234::1"],"isInternalSync":false,"senderUpeerId":"4d4dbe917544b07922348a66b9c4b5a5a5f34a9ffb319915c39409c7d0ff230a","senderYggAddress":"200:1234::1","signature":"8da47f2392d066abf53b7f73d7e8deabadf713694c152d00f19e7cdd4d4f488e0a89b93fb6428993c31950658ab34b04cf36ee11c8b47764fa9c960efc11f104"}`

func TestCanonicalStringifyMatchesDesktopVector(t *testing.T) {
	packet, err := decodeControlPacket([]byte(handshakeRequestVector))
	if err != nil {
		t.Fatal(err)
	}
	delete(packet, "signature")
	canonical, err := canonicalStringify(packet)
	if err != nil {
		t.Fatal(err)
	}
	if canonical != handshakeRequestCanonical {
		t.Fatalf("canonical packet differs from Desktop vector:\n got: %s\nwant: %s", canonical, handshakeRequestCanonical)
	}
}

func TestValidateControlFrameAcceptsDesktopHandshakeRequest(t *testing.T) {
	metadataJSON, err := validateControlFrameFromPayload([]byte(handshakeRequestVector))
	if err != nil {
		t.Fatalf("validate Desktop handshake request: %v", err)
	}
	var metadata controlMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != handshakeRequest {
		t.Fatalf("type = %q", metadata.Type)
	}
	if metadata.SenderUpeerID != "4d4dbe917544b07922348a66b9c4b5a5a5f34a9ffb319915c39409c7d0ff230a" {
		t.Fatalf("senderUpeerId = %q", metadata.SenderUpeerID)
	}
	if metadata.SenderYggAddress != "200:1234::1" {
		t.Fatalf("senderYggAddress = %q", metadata.SenderYggAddress)
	}
}

func TestValidateControlFrameAcceptsDesktopHandshakeAccept(t *testing.T) {
	metadataJSON, err := validateControlFrameFromPayload([]byte(handshakeAcceptVector))
	if err != nil {
		t.Fatalf("validate Desktop handshake accept: %v", err)
	}
	var metadata controlMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != handshakeAccept {
		t.Fatalf("type = %q", metadata.Type)
	}
}

func TestValidateControlFrameRejectsTamperingAndUnsupportedTypes(t *testing.T) {
	tampered := strings.Replace(handshakeRequestVector, `"alias":"Nexo <uPeer> á"`, `"alias":"Nexo <uPeer> b"`, 1)
	if _, err := validateControlFrameFromPayload([]byte(tampered)); err == nil {
		t.Fatal("tampered handshake request was accepted")
	}
	unsupported := strings.Replace(handshakeRequestVector, `"type":"HANDSHAKE_REQ"`, `"type":"PING"`, 1)
	if _, err := validateControlFrameFromPayload([]byte(unsupported)); err == nil {
		t.Fatal("unsupported control packet was accepted")
	}
	if _, err := validateControlFrameFromPayload([]byte(`{"type":"HANDSHAKE_REQ"}`)); err == nil {
		t.Fatal("incomplete control packet was accepted")
	}
}

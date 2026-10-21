package transportbridge

import (
	"bytes"
	"crypto/ed25519"
	"encoding/json"
	"testing"
)

func TestHandshakeAcceptIsSignedAndValidates(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	spkJSON, err := identity.CreateSignedPreKey(12345)
	if err != nil {
		t.Fatal(err)
	}
	var spk signedPreKeyState
	if err := json.Unmarshal([]byte(spkJSON), &spk); err != nil {
		t.Fatal(err)
	}
	if spk.SPKID != 12345 || !validHex(spk.SPKPub, 32) || !validHex(spk.SPKSk, 32) {
		t.Fatalf("signed pre-key state = %#v", spk)
	}
	if !ed25519.Verify(identity.publicKey, mustHex(t, spk.SPKPub), mustHex(t, spk.SPKSig)) {
		t.Fatal("signed pre-key signature did not verify")
	}
	packet, err := identity.BuildHandshakeAccept("200::1", spkJSON, "Móvil")
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains([]byte(packet), []byte("spkSk")) {
		t.Fatal("handshake accept leaked the signed pre-key secret")
	}
	metadataJSON, err := validateControlFrameFromPayload([]byte(packet))
	if err != nil {
		t.Fatal(err)
	}
	var metadata controlMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != handshakeAccept {
		t.Fatalf("metadata type = %s", metadata.Type)
	}
	if metadata.SenderUpeerID != identity.UpeerID() || metadata.SenderYggAddress != "200::1" {
		t.Fatalf("metadata = %#v", metadata)
	}
	var parsed map[string]any
	if err := json.Unmarshal([]byte(packet), &parsed); err != nil {
		t.Fatal(err)
	}
	if parsed["alias"] != "Móvil" || parsed["isInternalSync"] != false {
		t.Fatalf("packet fields = %#v", parsed)
	}
}

func TestBuildAckIsSignedAndValidates(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	ack, err := identity.BuildAck("200::1", "message-1", "delivered")
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateSignedPacket([]byte(ack), identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	var metadata signedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "ACK" || metadata.ID != "message-1" {
		t.Fatalf("metadata = %#v", metadata)
	}
}

func TestHandshakeAcceptRejectsInvalidState(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := identity.BuildHandshakeAccept("200::1", `{"spkPub":"00","spkSig":"00","spkId":1}`, ""); err == nil {
		t.Fatal("invalid signed pre-key state was accepted")
	}
	if _, err := identity.BuildHandshakeAccept("not-an-address", `{}`, ""); err == nil {
		t.Fatal("invalid Yggdrasil address was accepted")
	}
}

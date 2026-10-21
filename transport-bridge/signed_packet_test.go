package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"testing"
)

func TestValidateSignedPacket(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	packet := map[string]any{
		"type":             "CHAT",
		"id":               "message-1",
		"timestamp":        json.Number("1792596172000"),
		"content":          "0123456789abcdef0123456789abcdef",
		"nonce":            "0123456789abcdef0123456789abcdef0123456789abcdef",
		"isInternalSync":   false,
		"senderUpeerId":    identity.UpeerID(),
		"senderYggAddress": "200::1",
	}
	canonical, err := canonicalStringify(packet)
	if err != nil {
		t.Fatal(err)
	}
	packet["signature"] = hex.EncodeToString(ed25519.Sign(identity.secretKey, []byte(canonical)))
	payload, err := json.Marshal(packet)
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateSignedPacket(payload, identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	var metadata signedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "CHAT" || metadata.ID != "message-1" || metadata.SenderUpeerID != identity.UpeerID() {
		t.Fatalf("metadata = %#v", metadata)
	}
	packet["content"] = "tampered"
	tampered, err := json.Marshal(packet)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := (&Bridge{}).ValidateSignedPacket(tampered, identity.PublicKeyHex()); err == nil {
		t.Fatal("tampered signed packet was accepted")
	}
}

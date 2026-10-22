package transportbridge

import (
	"encoding/json"
	"strings"
	"testing"
	"time"
)

func testCryptoIdentity(t *testing.T) *CryptoIdentity {
	t.Helper()
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	return identity
}

func TestBuildSignedPacketMatchesDesktopValidation(t *testing.T) {
	identity := testCryptoIdentity(t)
	signed, err := identity.BuildSignedPacket("200::1", `{"type":"DHT_PING","queryId":"query-1"}`)
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateSignedPacket([]byte(signed), identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	var metadata signedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "DHT_PING" || metadata.SenderUpeerID != identity.UpeerID() {
		t.Fatalf("metadata = %#v", metadata)
	}
	var packet map[string]any
	if err := json.Unmarshal([]byte(signed), &packet); err != nil {
		t.Fatal(err)
	}
	if packet["senderYggAddress"] != "200::1" || packet["isInternalSync"] != false {
		t.Fatalf("packet = %#v", packet)
	}
	if _, exists := packet["senderUpeerId"]; !exists {
		t.Fatal("signed packet is missing senderUpeerId")
	}
}

func TestBuildSignedPacketRejectsReservedAndInvalidInput(t *testing.T) {
	identity := testCryptoIdentity(t)
	if _, err := identity.BuildSignedPacket("200::1", `{"type":"DHT_PING","signature":"00"}`); err == nil {
		t.Fatal("signed packet accepted a caller-provided signature")
	}
	if _, err := identity.BuildSignedPacket("200::1", `{"type":"DHT_PING","senderUpeerId":"00"}`); err == nil {
		t.Fatal("signed packet accepted a caller-provided senderUpeerId")
	}
	if _, err := identity.BuildSignedPacket("not-an-address", `{"type":"DHT_PING"}`); err == nil {
		t.Fatal("signed packet accepted an invalid Yggdrasil address")
	}
	if _, err := identity.BuildSignedPacket("200::1", `{"queryId":"query-1"}`); err == nil {
		t.Fatal("signed packet accepted a missing type")
	}
}

func TestSignAndVerifyCanonicalJSON(t *testing.T) {
	identity := testCryptoIdentity(t)
	payload := `{"zeta":2,"alpha":{"b":true,"a":"á"},"list":[3,1,2]}`
	signature, err := identity.SignCanonicalJSON(payload)
	if err != nil {
		t.Fatal(err)
	}
	valid, err := VerifyCanonicalJSON(payload, signature, identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	if !valid {
		t.Fatal("canonical signature did not verify")
	}
	valid, err = VerifyCanonicalJSON(strings.Replace(payload, `"zeta":2`, `"zeta":3`, 1), signature, identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	if valid {
		t.Fatal("tampered canonical payload verified")
	}
}

func TestLocationBlockRoundTripAndTamperDetection(t *testing.T) {
	identity := testCryptoIdentity(t)
	block, err := identity.BuildLocationBlock(`["200::2","200::1","200::1"]`, 7, 0, "device-1")
	if err != nil {
		t.Fatal(err)
	}
	var decoded locationBlockRecord
	if err := json.Unmarshal([]byte(block), &decoded); err != nil {
		t.Fatal(err)
	}
	if decoded.Address != "200::1" || len(decoded.Addresses) != 2 {
		t.Fatalf("block = %#v", decoded)
	}
	valid, err := VerifyLocationBlock(block, identity.PublicKeyHex(), time.Now().UnixMilli())
	if err != nil {
		t.Fatal(err)
	}
	if !valid {
		t.Fatal("location block did not verify")
	}
	tampered := strings.Replace(block, `"dhtSeq":7`, `"dhtSeq":8`, 1)
	valid, err = VerifyLocationBlock(tampered, identity.PublicKeyHex(), time.Now().UnixMilli())
	if err != nil {
		t.Fatal(err)
	}
	if valid {
		t.Fatal("tampered location block verified")
	}
	valid, err = VerifyLocationBlock(block, identity.PublicKeyHex(), time.Now().Add(31*24*time.Hour).UnixMilli())
	if err == nil {
		t.Fatal("expired location block did not report an error")
	}
	if valid {
		t.Fatal("expired location block verified")
	}
}

func TestLocationBlockRejectsInvalidFields(t *testing.T) {
	identity := testCryptoIdentity(t)
	if _, err := identity.BuildLocationBlock(`["200::1"]`, -1, 0, ""); err == nil {
		t.Fatal("location block accepted a negative dhtSeq")
	}
	if _, err := identity.BuildLocationBlock(`["not-an-address"]`, 1, 0, ""); err == nil {
		t.Fatal("location block accepted an invalid address")
	}
	if _, err := identity.BuildLocationBlock(`[]`, 1, 0, ""); err == nil {
		t.Fatal("location block accepted an empty address list")
	}
}

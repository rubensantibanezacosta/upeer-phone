package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

const linkedVectorPath = "../../upper-desktop/tests/fixtures/device-handshake-linked-go.json"

var linkedVectorNow int64 = 1_790_812_800_000

type linkedHandshakePacket struct {
	Packet    json.RawMessage `json:"packet"`
	Signature string          `json:"signature"`
}

type linkedHandshakeVector struct {
	GeneratedBy          string                `json:"generatedBy"`
	GeneratedAt          string                `json:"generatedAt"`
	Now                  int64                 `json:"now"`
	SenderYggAddress     string                `json:"senderYggAddress"`
	AccountSecretKeySeed string                `json:"accountSecretKeySeed"`
	AccountPublicKey     string                `json:"accountPublicKey"`
	AccountUpeerID       string                `json:"accountUpeerId"`
	DeviceSecretKeySeed  string                `json:"deviceSecretKeySeed"`
	DevicePublicKey      string                `json:"devicePublicKey"`
	DeviceID             string                `json:"deviceId"`
	Certificate          json.RawMessage       `json:"certificate"`
	DeviceSignedPreKey   json.RawMessage       `json:"deviceSignedPreKey"`
	Request              linkedHandshakePacket `json:"request"`
	Accept               linkedHandshakePacket `json:"accept"`
}

func seedIdentity(t *testing.T, seed byte) *CryptoIdentity {
	t.Helper()
	seedBytes := make([]byte, ed25519.SeedSize)
	for index := range seedBytes {
		seedBytes[index] = seed
	}
	identity, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seedBytes))
	if err != nil {
		t.Fatal(err)
	}
	return identity
}

func buildLinkedHandshakeVector(t *testing.T) []byte {
	t.Helper()
	account := seedIdentity(t, 0x5a)
	device := seedIdentity(t, 0x5b)
	options, err := json.Marshal(map[string]any{
		"accountUpeerId": account.UpeerID(),
		"platform":       "android",
		"capabilities":   []string{"chat", "dht"},
		"createdAt":      linkedVectorNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	pairingRequest, err := device.BuildDevicePairingRequest(string(options))
	if err != nil {
		t.Fatal(err)
	}
	certificate, err := account.ApproveDevicePairingRequest(pairingRequest, 1, linkedVectorNow, linkedVectorNow+365*24*60*60*1000)
	if err != nil {
		t.Fatal(err)
	}
	var decoded struct {
		DeviceID string `json:"deviceId"`
	}
	if err := json.Unmarshal([]byte(certificate), &decoded); err != nil {
		t.Fatal(err)
	}
	deviceSPKJSON, err := device.CreateSignedPreKey(int(linkedVectorNow))
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	fields, err := json.Marshal(map[string]any{
		"devicePublicKey":   device.PublicKeyHex(),
		"deviceId":          decoded.DeviceID,
		"deviceCertificate": json.RawMessage(certificate),
		"deviceSignedPreKey": map[string]any{
			"spkPub": deviceSPK.SPKPub,
			"spkSig": deviceSPK.SPKSig,
			"spkId":  deviceSPK.SPKID,
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	build := func(packetType string) linkedHandshakePacket {
		packet, err := device.buildLinkedHandshakePacketAt(
			packetType,
			account.PublicKeyHex(),
			account.UpeerID(),
			"200::7",
			"móvil",
			"",
			string(fields),
			time.UnixMilli(linkedVectorNow),
		)
		if err != nil {
			t.Fatal(err)
		}
		var decoded map[string]any
		if err := json.Unmarshal([]byte(packet), &decoded); err != nil {
			t.Fatal(err)
		}
		signature, _ := decoded["signature"].(string)
		return linkedHandshakePacket{Packet: json.RawMessage(packet), Signature: signature}
	}
	vector := linkedHandshakeVector{
		GeneratedBy:          "upper-transport-bridge",
		GeneratedAt:          time.Now().UTC().Format("2006-01-02"),
		Now:                  linkedVectorNow,
		SenderYggAddress:     "200::7",
		AccountSecretKeySeed: hex.EncodeToString(bytes32(0x5a)),
		AccountPublicKey:     account.PublicKeyHex(),
		AccountUpeerID:       account.UpeerID(),
		DeviceSecretKeySeed:  hex.EncodeToString(bytes32(0x5b)),
		DevicePublicKey:      device.PublicKeyHex(),
		DeviceID:             decoded.DeviceID,
		Certificate:          json.RawMessage(certificate),
		DeviceSignedPreKey:   mustRawJSON(t, deviceSPK),
		Request:              build(handshakeRequest),
		Accept:               build(handshakeAccept),
	}
	encoded, err := json.MarshalIndent(vector, "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	return append(encoded, '\n')
}

func bytes32(seed byte) []byte {
	values := make([]byte, ed25519.SeedSize)
	for index := range values {
		values[index] = seed
	}
	return values
}

func mustRawJSON(t *testing.T, value any) json.RawMessage {
	t.Helper()
	encoded, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	return encoded
}

func TestWriteLinkedHandshakeVector(t *testing.T) {
	if os.Getenv("UP_WRITE_LINKED_VECTOR") == "" {
		t.Skip("solo escribe el vector cuando UP_WRITE_LINKED_VECTOR=1")
	}
	path := filepath.Clean(linkedVectorPath)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, buildLinkedHandshakeVector(t), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestLinkedHandshakeGoVector(t *testing.T) {
	raw, err := os.ReadFile(filepath.Clean(linkedVectorPath))
	if err != nil {
		t.Skip("vector cruzado no disponible: solo existe en el monorepo completo")
	}
	var vector linkedHandshakeVector
	if err := json.Unmarshal(raw, &vector); err != nil {
		t.Fatal(err)
	}
	if vector.GeneratedBy != "upper-transport-bridge" {
		t.Fatalf("unexpected vector generator %q", vector.GeneratedBy)
	}
	for name, packet := range map[string]linkedHandshakePacket{"request": vector.Request, "accept": vector.Accept} {
		metadataJSON, err := validateControlFrameWithDeviceSignatureAt(packet.Packet, vector.Now)
		if err != nil {
			t.Fatalf("%s: vector no valida: %v", name, err)
		}
		var metadata controlMetadata
		if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
			t.Fatal(err)
		}
		if metadata.SenderUpeerID != vector.AccountUpeerID || metadata.PublicKey != vector.AccountPublicKey {
			t.Fatalf("%s: metadata = %+v", name, metadata)
		}
		if len(packet.Signature) != ed25519.SignatureSize*2 {
			t.Fatalf("%s: firma con longitud %d", name, len(packet.Signature))
		}
	}
}

package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"strings"
	"testing"
)

type vectorFixture struct {
	Identity struct {
		SecretKeySeed string `json:"secretKeySeed"`
		PublicKey     string `json:"publicKey"`
		UpeerID       string `json:"upeerId"`
		Address       string `json:"address"`
	} `json:"identity"`
	CanonicalVectors []struct {
		Payload   map[string]any `json:"payload"`
		Canonical string         `json:"canonical"`
		Signature string         `json:"signature"`
	} `json:"canonicalVectors"`
	SignedPackets []struct {
		Packet    map[string]any `json:"packet"`
		Canonical string         `json:"canonical"`
		Signature string         `json:"signature"`
	} `json:"signedPackets"`
}

func loadVectorFixture(t *testing.T) vectorFixture {
	t.Helper()
	file, err := os.Open("../testdata/dht_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	decoder := json.NewDecoder(file)
	decoder.UseNumber()
	var fixture vectorFixture
	if err := decoder.Decode(&fixture); err != nil {
		t.Fatal(err)
	}
	return fixture
}

func vectorPrivateKey(t *testing.T, fixture vectorFixture) ed25519.PrivateKey {
	t.Helper()
	seed, err := hex.DecodeString(fixture.Identity.SecretKeySeed)
	if err != nil {
		t.Fatal(err)
	}
	return ed25519.NewKeyFromSeed(seed)
}

func TestCanonicalVectorsMatchDesktop(t *testing.T) {
	fixture := loadVectorFixture(t)
	for _, vector := range fixture.CanonicalVectors {
		canonical, err := CanonicalStringify(vector.Payload)
		if err != nil {
			t.Fatal(err)
		}
		if canonical != vector.Canonical {
			t.Fatalf("canonical mismatch\nwant: %s\ngot:  %s", vector.Canonical, canonical)
		}
		payload, err := json.Marshal(vector.Payload)
		if err != nil {
			t.Fatal(err)
		}
		valid, err := VerifyCanonicalJSON(string(payload), vector.Signature, fixture.Identity.PublicKey)
		if err != nil || !valid {
			t.Fatalf("desktop signature did not verify: %v", err)
		}
	}
}

func TestBuildAndVerifySignedPacket(t *testing.T) {
	fixture := loadVectorFixture(t)
	secretKey := vectorPrivateKey(t, fixture)
	signed, err := BuildSignedPacket(secretKey, fixture.Identity.Address, `{"type":"DHT_PING","queryId":"query-1"}`)
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := VerifySignedPacket(signed, fixture.Identity.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	var metadata SignedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "DHT_PING" || metadata.SenderUpeerID != fixture.Identity.UpeerID {
		t.Fatalf("metadata = %#v", metadata)
	}
	var packet map[string]any
	if err := json.Unmarshal([]byte(signed), &packet); err != nil {
		t.Fatal(err)
	}
	if packet["senderYggAddress"] != fixture.Identity.Address || packet["isInternalSync"] != false {
		t.Fatalf("packet = %#v", packet)
	}
}

func TestSignedPacketRejectsTamperingAndWrongIdentity(t *testing.T) {
	fixture := loadVectorFixture(t)
	secretKey := vectorPrivateKey(t, fixture)
	signed, err := BuildSignedPacket(secretKey, fixture.Identity.Address, `{"type":"DHT_PING","queryId":"query-1"}`)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := VerifySignedPacket(strings.Replace(signed, `"queryId":"query-1"`, `"queryId":"query-2"`, 1), fixture.Identity.PublicKey); err == nil {
		t.Fatal("tampered signed packet was accepted")
	}
	other := ed25519.NewKeyFromSeed(make([]byte, ed25519.SeedSize))
	if _, err := VerifySignedPacket(signed, hex.EncodeToString(other.Public().(ed25519.PublicKey))); err == nil {
		t.Fatal("signed packet was accepted for a different public key")
	}
}

func TestBuildSignedPacketRejectsInvalidInput(t *testing.T) {
	fixture := loadVectorFixture(t)
	secretKey := vectorPrivateKey(t, fixture)
	for _, payload := range []string{
		`{"type":"DHT_PING","signature":"00"}`,
		`{"type":"DHT_PING","senderUpeerId":"00"}`,
		`{"type":"DHT_PING","senderYggAddress":"200::1"}`,
		`{"queryId":"query-1"}`,
		`[]`,
		`{"type":"DHT_PING"} false`,
	} {
		if _, err := BuildSignedPacket(secretKey, fixture.Identity.Address, payload); err == nil {
			t.Fatalf("signed packet accepted invalid payload %s", payload)
		}
	}
	if _, err := BuildSignedPacket(secretKey, "not-an-address", `{"type":"DHT_PING"}`); err == nil {
		t.Fatal("signed packet accepted an invalid Yggdrasil address")
	}
	if _, err := BuildSignedPacket(make([]byte, 32), fixture.Identity.Address, `{"type":"DHT_PING"}`); err == nil {
		t.Fatal("signed packet accepted an invalid secret key")
	}
}

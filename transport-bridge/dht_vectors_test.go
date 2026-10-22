package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"
)

type dhtVectorFixture struct {
	GeneratedBy      string `json:"generatedBy"`
	Identity         dhtVectorIdentity
	CanonicalVectors []dhtCanonicalVector `json:"canonicalVectors"`
	SignedPackets    []dhtSignedPacket    `json:"signedPackets"`
	LocationBlocks   []dhtLocationVector  `json:"locationBlocks"`
}

type dhtVectorIdentity struct {
	SecretKeySeed string `json:"secretKeySeed"`
	PublicKey     string `json:"publicKey"`
	UpeerID       string `json:"upeerId"`
	Address       string `json:"address"`
}

type dhtCanonicalVector struct {
	Name      string         `json:"name"`
	Payload   map[string]any `json:"payload"`
	Canonical string         `json:"canonical"`
	Signature string         `json:"signature"`
}

type dhtSignedPacket struct {
	Name      string         `json:"name"`
	Packet    map[string]any `json:"packet"`
	Canonical string         `json:"canonical"`
	Signature string         `json:"signature"`
}

type dhtLocationVector struct {
	Name       string         `json:"name"`
	SignedData map[string]any `json:"signedData"`
	Block      map[string]any `json:"block"`
	Canonical  string         `json:"canonical"`
	Signature  string         `json:"signature"`
}

func loadDHTVectorFixture(t *testing.T) dhtVectorFixture {
	t.Helper()
	file, err := os.Open("testdata/dht_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	decoder := json.NewDecoder(file)
	decoder.UseNumber()
	var fixture dhtVectorFixture
	if err := decoder.Decode(&fixture); err != nil {
		t.Fatal(err)
	}
	if fixture.GeneratedBy != "upper-desktop" {
		t.Fatalf("unexpected vector generator %q", fixture.GeneratedBy)
	}
	return fixture
}

func vectorIdentity(t *testing.T, fixture dhtVectorFixture) *CryptoIdentity {
	t.Helper()
	seed, err := hex.DecodeString(fixture.Identity.SecretKeySeed)
	if err != nil {
		t.Fatal(err)
	}
	identity, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seed))
	if err != nil {
		t.Fatal(err)
	}
	if identity.PublicKeyHex() != fixture.Identity.PublicKey || identity.UpeerID() != fixture.Identity.UpeerID {
		t.Fatal("vector identity does not match the fixture")
	}
	return identity
}

func encodeVectorJSON(t *testing.T, value any) string {
	t.Helper()
	encoded, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	return string(encoded)
}

func TestDesktopCanonicalVectors(t *testing.T) {
	fixture := loadDHTVectorFixture(t)
	identity := vectorIdentity(t, fixture)
	if len(fixture.CanonicalVectors) == 0 {
		t.Fatal("canonical vectors are missing")
	}
	for _, vector := range fixture.CanonicalVectors {
		t.Run(vector.Name, func(t *testing.T) {
			canonical, err := canonicalStringify(vector.Payload)
			if err != nil {
				t.Fatal(err)
			}
			if canonical != vector.Canonical {
				t.Fatalf("canonical mismatch\nwant: %s\ngot:  %s", vector.Canonical, canonical)
			}
			payloadJSON := encodeVectorJSON(t, vector.Payload)
			signature, err := identity.SignCanonicalJSON(payloadJSON)
			if err != nil {
				t.Fatal(err)
			}
			if signature != vector.Signature {
				t.Fatalf("signature mismatch\nwant: %s\ngot:  %s", vector.Signature, signature)
			}
			valid, err := VerifyCanonicalJSON(payloadJSON, vector.Signature, fixture.Identity.PublicKey)
			if err != nil {
				t.Fatal(err)
			}
			if !valid {
				t.Fatal("desktop signature did not verify")
			}
		})
	}
}

func TestDesktopSignedDhtPackets(t *testing.T) {
	fixture := loadDHTVectorFixture(t)
	identity := vectorIdentity(t, fixture)
	if len(fixture.SignedPackets) == 0 {
		t.Fatal("signed packets are missing")
	}
	for _, vector := range fixture.SignedPackets {
		t.Run(vector.Name, func(t *testing.T) {
			packetJSON := encodeVectorJSON(t, vector.Packet)
			if _, err := (&Bridge{}).ValidateSignedPacket([]byte(packetJSON), fixture.Identity.PublicKey); err != nil {
				t.Fatal(err)
			}
			payload := make(map[string]any, len(vector.Packet))
			for key, value := range vector.Packet {
				if key == "signature" || key == "contactCache" || key == "renewalToken" {
					continue
				}
				payload[key] = value
			}
			canonical, err := canonicalStringify(payload)
			if err != nil {
				t.Fatal(err)
			}
			if canonical != vector.Canonical {
				t.Fatalf("signed payload mismatch\nwant: %s\ngot:  %s", vector.Canonical, canonical)
			}
			if identity.Sign([]byte(canonical)) != vector.Signature {
				t.Fatal("signed packet signature mismatch")
			}
			if vector.Packet["senderUpeerId"] != fixture.Identity.UpeerID || vector.Packet["senderYggAddress"] != fixture.Identity.Address {
				t.Fatal("signed packet sender fields do not match the fixture identity")
			}
		})
	}
}

func TestDesktopLocationBlockVectors(t *testing.T) {
	fixture := loadDHTVectorFixture(t)
	identity := vectorIdentity(t, fixture)
	if len(fixture.LocationBlocks) == 0 {
		t.Fatal("location block vectors are missing")
	}
	for _, vector := range fixture.LocationBlocks {
		t.Run(vector.Name, func(t *testing.T) {
			canonical, err := canonicalStringify(vector.SignedData)
			if err != nil {
				t.Fatal(err)
			}
			if canonical != vector.Canonical {
				t.Fatalf("location payload mismatch\nwant: %s\ngot:  %s", vector.Canonical, canonical)
			}
			if identity.Sign([]byte(canonical)) != vector.Signature {
				t.Fatal("location signature mismatch")
			}
			blockJSON := encodeVectorJSON(t, vector.Block)
			valid, err := VerifyLocationBlock(blockJSON, fixture.Identity.PublicKey, 1_790_000_000_000)
			if err != nil {
				t.Fatal(err)
			}
			if !valid {
				t.Fatal("desktop location block did not verify")
			}
		})
	}
}

func TestSignedDhtPacketRejectsTampering(t *testing.T) {
	fixture := loadDHTVectorFixture(t)
	packet := fixture.SignedPackets[0].Packet
	tampered := make(map[string]any, len(packet))
	for key, value := range packet {
		tampered[key] = value
	}
	tampered["queryId"] = "tampered"
	if _, err := (&Bridge{}).ValidateSignedPacket([]byte(encodeVectorJSON(t, tampered)), fixture.Identity.PublicKey); err == nil {
		t.Fatal("tampered signed packet was accepted")
	}
}

package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"strings"
	"testing"
)

const testLocationNow int64 = 1_700_000_000_000

func decodeTestBlock(t *testing.T, blockJSON string) LocationBlock {
	t.Helper()
	block, err := DecodeLocationBlock(blockJSON)
	if err != nil {
		t.Fatal(err)
	}
	return block
}

func TestBuildLocationBlockNormalizesAndVerifies(t *testing.T) {
	fixture := loadVectorFixture(t)
	privateKey := vectorPrivateKey(t, fixture)
	blockJSON, err := BuildLocationBlock(privateKey, LocationBlockOptions{
		Addresses: []string{" 200::2 ", "200::1", "200::2"},
		DhtSeq:    7,
		DeviceID:  "device-1",
		NowMillis: testLocationNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	block := decodeTestBlock(t, blockJSON)
	if block.DhtSeq != 7 || block.DeviceID != "device-1" {
		t.Fatalf("block = %#v", block)
	}
	if strings.Join(block.Addresses, ",") != "200::1,200::2" || block.Address != "200::1" {
		t.Fatalf("addresses = %#v", block.Addresses)
	}
	if block.ExpiresAt != testLocationNow+LocationBlockDefaultTTLMillis {
		t.Fatalf("expiresAt = %d", block.ExpiresAt)
	}
	valid, err := VerifyLocationBlock(blockJSON, fixture.Identity.PublicKey, testLocationNow)
	if err != nil || !valid {
		t.Fatalf("verify = %v, %v", valid, err)
	}
}

func TestBuildLocationBlockClampsTTL(t *testing.T) {
	fixture := loadVectorFixture(t)
	privateKey := vectorPrivateKey(t, fixture)
	for _, test := range []struct {
		ttl int64
	}{{-5}, {0}, {LocationBlockMaxTTLMillis + 1}} {
		blockJSON, err := BuildLocationBlock(privateKey, LocationBlockOptions{
			Addresses: []string{"200::1"}, TTLMillis: test.ttl, NowMillis: testLocationNow,
		})
		if err != nil {
			t.Fatal(err)
		}
		want := testLocationNow + LocationBlockDefaultTTLMillis
		if test.ttl > LocationBlockMaxTTLMillis {
			want = testLocationNow + LocationBlockMaxTTLMillis
		}
		if got := decodeTestBlock(t, blockJSON).ExpiresAt; got != want {
			t.Fatalf("ttl %d: expiresAt = %d, want %d", test.ttl, got, want)
		}
	}
}

func TestBuildLocationBlockSignsDeviceMeta(t *testing.T) {
	fixture := loadVectorFixture(t)
	privateKey := vectorPrivateKey(t, fixture)
	blockJSON, err := BuildLocationBlock(privateKey, LocationBlockOptions{
		Addresses: []string{"200::1"},
		DeviceMeta: map[string]any{
			"clientName": "uPeer Android", "capabilities": []string{"dht"}, "renewalsUsed": 1,
		},
		NowMillis: testLocationNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	block := decodeTestBlock(t, blockJSON)
	if block.DeviceMeta["clientName"] != "uPeer Android" {
		t.Fatalf("deviceMeta = %#v", block.DeviceMeta)
	}
	valid, err := VerifyLocationBlock(blockJSON, fixture.Identity.PublicKey, testLocationNow)
	if err != nil || !valid {
		t.Fatalf("verify = %v, %v", valid, err)
	}
	tampered := strings.Replace(blockJSON, "uPeer Android", "uPeer iOS", 1)
	valid, err = VerifyLocationBlock(tampered, fixture.Identity.PublicKey, testLocationNow)
	if err != nil || valid {
		t.Fatalf("tampered deviceMeta accepted: %v, %v", valid, err)
	}
}
func TestVerifyLocationBlockRejectsInvalidBlocks(t *testing.T) {
	fixture := loadVectorFixture(t)
	privateKey := vectorPrivateKey(t, fixture)
	blockJSON, err := BuildLocationBlock(privateKey, LocationBlockOptions{
		Addresses: []string{"200::1"}, NowMillis: testLocationNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	block := decodeTestBlock(t, blockJSON)
	if valid, err := VerifyLocationBlock(blockJSON, fixture.Identity.PublicKey, block.ExpiresAt+1); err == nil || valid {
		t.Fatal("expired block was accepted")
	}
	other := ed25519.NewKeyFromSeed(make([]byte, ed25519.SeedSize))
	valid, err := VerifyLocationBlock(blockJSON, hex.EncodeToString(other.Public().(ed25519.PublicKey)), testLocationNow)
	if err != nil || valid {
		t.Fatalf("block verified with a different key: %v, %v", valid, err)
	}
	tampered := strings.Replace(blockJSON, `"dhtSeq":0`, `"dhtSeq":9`, 1)
	valid, err = VerifyLocationBlock(tampered, fixture.Identity.PublicKey, testLocationNow)
	if err != nil || valid {
		t.Fatalf("tampered dhtSeq accepted: %v, %v", valid, err)
	}
	withUpeerID := strings.Replace(blockJSON, `{"address":`, `{"upeerId":"forged","address":`, 1)
	valid, err = VerifyLocationBlock(withUpeerID, fixture.Identity.PublicKey, testLocationNow)
	if err != nil || !valid {
		t.Fatalf("unsigned upeerId field changed verification: %v, %v", valid, err)
	}
	if _, err := VerifyLocationBlock(blockJSON+" {}", fixture.Identity.PublicKey, testLocationNow); err == nil {
		t.Fatal("trailing data was accepted")
	}
}

func TestVerifyLocationBlockSupportsLegacyPayload(t *testing.T) {
	fixture := loadVectorFixture(t)
	privateKey := vectorPrivateKey(t, fixture)
	address := "200:9f8e:7d6c:5b4a:3928:1706:f5e4:d3c2"
	expiresAt := testLocationNow + 60_000
	legacy, err := CanonicalStringify(map[string]any{
		"upeerId": fixture.Identity.UpeerID, "address": address,
		"dhtSeq": json.Number("3"), "expiresAt": json.Number("1700000060000"),
	})
	if err != nil {
		t.Fatal(err)
	}
	blockJSON, err := json.Marshal(LocationBlock{
		Address: address, DhtSeq: 3, ExpiresAt: expiresAt,
		Signature: hex.EncodeToString(ed25519.Sign(privateKey, []byte(legacy))),
	})
	if err != nil {
		t.Fatal(err)
	}
	valid, err := VerifyLocationBlock(string(blockJSON), fixture.Identity.PublicKey, testLocationNow)
	if err != nil || !valid {
		t.Fatalf("legacy block = %v, %v", valid, err)
	}
}

func TestBuildLocationBlockRejectsInvalidOptions(t *testing.T) {
	fixture := loadVectorFixture(t)
	privateKey := vectorPrivateKey(t, fixture)
	invalid := []LocationBlockOptions{
		{Addresses: nil},
		{Addresses: []string{"192.168.1.1"}},
		{Addresses: []string{"400::1"}},
		{Addresses: []string{"200::1"}, DhtSeq: -1},
		{Addresses: []string{"200::1"}, DeviceID: strings.Repeat("a", 129)},
		{Addresses: []string{"200::1"}, DeviceMeta: map[string]any{"bad": make(chan int)}},
	}
	for index, options := range invalid {
		if _, err := BuildLocationBlock(privateKey, options); err == nil {
			t.Fatalf("invalid options %d were accepted", index)
		}
	}
	if _, err := BuildLocationBlock([]byte("short"), LocationBlockOptions{Addresses: []string{"200::1"}}); err == nil {
		t.Fatal("invalid secret key was accepted")
	}
}

func TestDeriveDeviceIDIsStable(t *testing.T) {
	keyA := make([]byte, DeviceKeySize)
	keyB := make([]byte, DeviceKeySize)
	keyB[0] = 1
	first, err := DeriveDeviceID(keyA)
	if err != nil {
		t.Fatal(err)
	}
	second, err := DeriveDeviceID(keyA)
	if err != nil || first != second {
		t.Fatalf("device id is not stable: %q, %q", first, second)
	}
	if len(first) != 64 || !validHex(first, 32) {
		t.Fatalf("device id = %q", first)
	}
	other, err := DeriveDeviceID(keyB)
	if err != nil || other == first {
		t.Fatalf("device id did not change with key: %q", other)
	}
	if _, err := DeriveDeviceID(keyA[:16]); err == nil {
		t.Fatal("short device key was accepted")
	}
}

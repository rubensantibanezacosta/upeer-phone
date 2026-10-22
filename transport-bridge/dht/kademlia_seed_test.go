package dht

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"golang.org/x/crypto/blake2b"
)

func testKademliaSeed(index int) KademliaSeed {
	publicKey := make([]byte, 32)
	for offset := range publicKey {
		publicKey[offset] = byte((index+1)*37 + offset*11)
	}
	digest := blake2b.Sum256(publicKey)
	return KademliaSeed{
		UpeerID: hex.EncodeToString(digest[:]), Address: fmt.Sprintf("200::%x", index+1),
		PublicKey: hex.EncodeToString(publicKey),
	}
}

func testKademliaContact(seed KademliaSeed, lastSeen time.Time) KademliaContact {
	nodeID, err := NewKademliaID(seed.UpeerID)
	if err != nil {
		panic(err)
	}
	return KademliaContact{
		NodeID: nodeID, UpeerID: seed.UpeerID, Address: seed.Address,
		PublicKey: seed.PublicKey, LastSeen: lastSeen,
	}
}

func TestKademliaSeedValidationAndParsing(t *testing.T) {
	self := testKademliaSeed(99)
	valid := testKademliaSeed(1)
	if _, ok := NormalizeKademliaSeed(valid, self.UpeerID); !ok {
		t.Fatal("valid seed rejected")
	}
	invalid := []KademliaSeed{
		{UpeerID: testKademliaSeed(2).UpeerID, Address: valid.Address, PublicKey: valid.PublicKey},
		{UpeerID: valid.UpeerID, Address: "not-an-address", PublicKey: valid.PublicKey},
		{UpeerID: valid.UpeerID, Address: valid.Address, PublicKey: testKademliaSeed(2).PublicKey},
		valid,
	}
	for _, seed := range invalid {
		if _, ok := NormalizeKademliaSeed(seed, valid.UpeerID); ok {
			t.Fatalf("invalid seed accepted: %+v", seed)
		}
	}
	dns := ParseKademliaSeedDNS([]string{
		fmt.Sprintf("upeerId=%s;address=%s;publicKey=%s", valid.UpeerID, valid.Address, valid.PublicKey),
		"invalid;entry",
	})
	if len(dns) != 1 {
		t.Fatalf("expected one parsed DNS seed, got %d", len(dns))
	}
	content, err := json.Marshal([]KademliaSeed{valid})
	if err != nil {
		t.Fatal(err)
	}
	if parsed := ParseKademliaSeedFile(content); len(parsed) != 1 {
		t.Fatalf("expected one parsed file seed, got %d", len(parsed))
	}
}

func TestKademliaSeedLoaderBoundsAndFailures(t *testing.T) {
	self := testKademliaSeed(99)
	seeds := make([]KademliaSeed, 0, 5)
	for index := 1; index <= 5; index++ {
		seeds = append(seeds, testKademliaSeed(index))
	}
	loader := NewKademliaSeedLoader(self.UpeerID)
	loader.StaticSeeds = func() []KademliaSeed { return seeds }
	loader.MaxSeeds = 2
	loader.ResolveTXT = func(string) ([]string, error) { return nil, fmt.Errorf("dns unavailable") }
	loader.FilePath = "seednodes.json"
	loader.ReadFile = func(string) ([]byte, error) { return nil, fmt.Errorf("disk unavailable") }
	result := loader.Load()
	if len(result.Seeds) != 2 || result.SourceFailures != 2 {
		t.Fatalf("unexpected bounded loader result: %+v", result)
	}
}

func TestKademliaSeedLoaderMergesSources(t *testing.T) {

	self := testKademliaSeed(99)
	existing := testKademliaSeed(1)
	staticSeed := testKademliaSeed(2)
	dnsSeed := testKademliaSeed(3)
	fileSeed := testKademliaSeed(4)
	invalid := testKademliaSeed(5)
	invalid.PublicKey = testKademliaSeed(6).PublicKey
	fileContent, err := json.Marshal([]KademliaSeed{fileSeed, existing})
	if err != nil {
		t.Fatal(err)
	}
	loader := NewKademliaSeedLoader(self.UpeerID)
	loader.ExistingContacts = func() []KademliaContact {
		return []KademliaContact{testKademliaContact(existing, time.UnixMilli(1_000))}
	}
	loader.StaticSeeds = func() []KademliaSeed { return []KademliaSeed{staticSeed} }
	loader.ResolveTXT = func(domain string) ([]string, error) {
		if domain != KademliaSeedDNSDomain {
			t.Fatalf("unexpected DNS domain %s", domain)
		}
		return []string{
			fmt.Sprintf("upeerId=%s;address=%s;publicKey=%s", dnsSeed.UpeerID, dnsSeed.Address, dnsSeed.PublicKey),
			fmt.Sprintf("upeerId=%s;address=%s;publicKey=%s", invalid.UpeerID, invalid.Address, invalid.PublicKey),
		}, nil
	}
	loader.FilePath = "seednodes.json"
	loader.ReadFile = func(string) ([]byte, error) { return fileContent, nil }
	result := loader.Load()
	if len(result.Seeds) != 4 {
		t.Fatalf("expected four seeds, got %d", len(result.Seeds))
	}
	want := []string{existing.UpeerID, staticSeed.UpeerID, dnsSeed.UpeerID, fileSeed.UpeerID}
	for index, seed := range result.Seeds {
		if seed.UpeerID != want[index] {
			t.Fatalf("seed %d mismatch", index)
		}
	}
	if result.Rejected != 1 || result.Duplicates != 1 || result.SourceFailures != 0 {
		t.Fatalf("unexpected loader result: %+v", result)
	}
}

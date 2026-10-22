package dht

import (
	"strings"
	"testing"
)

func TestKademliaIDMatchesDesktopVectors(t *testing.T) {
	hashed, err := NewKademliaID(strings.Repeat("0", 32))
	if err != nil {
		t.Fatal(err)
	}
	if hashed.Hex() != "374708fff7719dd5979ec875d56cd2286f6d3cf7" {
		t.Fatalf("unexpected hashed identifier %s", hashed.Hex())
	}
	look, err := NewLookupKademliaID("8d2d1c260127c74476b27136c5e38c00")
	if err != nil {
		t.Fatal(err)
	}
	if look.Hex() != "d953164221500855188346874cb48807d413fb4e" {
		t.Fatalf("unexpected lookup identifier %s", look.Hex())
	}
}

func TestKademliaBucketIndexAndDistanceOrdering(t *testing.T) {
	zero, err := NewRawKademliaID(strings.Repeat("0", 40))
	if err != nil {
		t.Fatal(err)
	}
	first, err := NewRawKademliaID("8" + strings.Repeat("0", 39))
	if err != nil {
		t.Fatal(err)
	}
	last, err := NewRawKademliaID(strings.Repeat("0", 39) + "1")
	if err != nil {
		t.Fatal(err)
	}
	if index := KademliaBucketIndex(zero, first); index != 0 {
		t.Fatalf("expected bucket 0, got %d", index)
	}
	if index := KademliaBucketIndex(zero, last); index != 159 {
		t.Fatalf("expected bucket 159, got %d", index)
	}
	if first.CompareDistanceTo(last, zero) != 1 {
		t.Fatal("expected first identifier to be farther")
	}
	if last.CompareDistanceTo(first, zero) != -1 {
		t.Fatal("expected last identifier to be closer")
	}
}

func TestKademliaIDRejectsMalformedHex(t *testing.T) {
	if _, err := NewKademliaID("abc"); err == nil {
		t.Fatal("expected odd hex to be rejected")
	}
	if _, err := NewRawKademliaID("00"); err == nil {
		t.Fatal("expected short raw identifier to be rejected")
	}
}

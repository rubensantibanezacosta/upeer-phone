package dht

import (
	"fmt"
	"strings"
	"testing"
	"time"
)

func TestKBucketEvictsOldestAtCapacity(t *testing.T) {
	now := time.UnixMilli(1_000)
	bucket := newKBucket(KademliaK, func() time.Time { return now })
	for index := 0; index <= KademliaK; index++ {
		bucket.add(testContact(index, fmt.Sprintf("peer-%d", index), now))
	}
	if bucket.size() != KademliaK {
		t.Fatalf("expected %d contacts, got %d", KademliaK, bucket.size())
	}
	for _, contact := range bucket.all() {
		if contact.UpeerID == "peer-0" {
			t.Fatal("oldest contact was not evicted")
		}
	}
}

func TestRoutingTableFindsContactsAndStaleBuckets(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	upeerID := strings.Repeat("0", 32)
	nodeID, err := NewKademliaID(upeerID)
	if err != nil {
		t.Fatal(err)
	}
	table := NewKademliaRoutingTable(nodeID, clock)
	if table.BucketCount() != 160 {
		t.Fatalf("expected 160 buckets, got %d", table.BucketCount())
	}
	if table.AddContact(testPeerContact(upeerID, nodeID, "200::1", now)) {
		t.Fatal("routing table must reject its own identifier")
	}
	second := testPeerContact(strings.Repeat("1", 32), mustID(t, strings.Repeat("1", 32)), "200::2", now)
	third := testPeerContact(strings.Repeat("2", 32), mustID(t, strings.Repeat("2", 32)), "200::3", now)
	if !table.AddContact(second) || !table.AddContact(third) {
		t.Fatal("expected contacts to be added")
	}
	if table.ContactCount() != 2 {
		t.Fatalf("expected two contacts, got %d", table.ContactCount())
	}
	if found := table.FindContact(second.UpeerID); found == nil || found.UpeerID != second.UpeerID {
		t.Fatal("expected to find the second contact")
	}
	now = now.Add(KademliaRefreshInterval + time.Millisecond)
	if len(table.RefreshStaleBuckets()) == 0 {
		t.Fatal("expected stale buckets after the refresh interval")
	}
}

func TestRoutingTableOrdersClosestContacts(t *testing.T) {
	now := time.UnixMilli(1_000)
	nodeID := mustID(t, strings.Repeat("0", 32))
	table := NewKademliaRoutingTable(nodeID, func() time.Time { return now })
	first := testPeerContact(strings.Repeat("1", 32), mustID(t, strings.Repeat("1", 32)), "200::1", now)
	second := testPeerContact(strings.Repeat("2", 32), mustID(t, strings.Repeat("2", 32)), "200::2", now)
	table.AddContact(first)
	table.AddContact(second)
	closest := table.FindClosestContacts(nodeID, 2)
	if len(closest) != 2 {
		t.Fatalf("expected two contacts, got %d", len(closest))
	}
	if closest[0].NodeID.CompareDistanceTo(closest[1].NodeID, nodeID) > 0 {
		t.Fatal("contacts are not sorted by XOR distance")
	}
}

func testContact(index int, upeerID string, now time.Time) KademliaContact {
	raw := fmt.Sprintf("%040x", index)
	id, err := NewRawKademliaID(raw)
	if err != nil {
		panic(err)
	}
	return KademliaContact{
		NodeID: id, UpeerID: upeerID, Address: fmt.Sprintf("200::%d", index+1),
		PublicKey: fmt.Sprintf("pk-%d", index), LastSeen: now,
	}
}

func testPeerContact(upeerID string, nodeID KademliaID, address string, now time.Time) KademliaContact {
	return KademliaContact{
		NodeID: nodeID, UpeerID: upeerID, Address: address, PublicKey: "pk-" + upeerID[:4], LastSeen: now,
	}
}

func mustID(t *testing.T, value string) KademliaID {
	t.Helper()
	id, err := NewKademliaID(value)
	if err != nil {
		t.Fatal(err)
	}
	return id
}

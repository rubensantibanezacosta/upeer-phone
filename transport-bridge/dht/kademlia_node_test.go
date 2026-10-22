package dht

import (
	"strings"
	"testing"
	"time"
)

func TestThreeNodeNetworkDiscoversStoresAndFindsValues(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	network := newSimNetwork()
	upeerA := strings.Repeat("a1", 16)
	upeerB := strings.Repeat("b2", 16)
	upeerC := strings.Repeat("c3", 16)
	addressA := "200::a1"
	addressB := "200::b2"
	addressC := "200::c3"
	nodeA := newSimNode(t, upeerA, addressA, network, clock, 200*time.Millisecond)
	nodeB := newSimNode(t, upeerB, addressB, network, clock, 200*time.Millisecond)
	nodeC := newSimNode(t, upeerC, addressC, network, clock, 200*time.Millisecond)
	nodeA.RoutingTable().AddContact(mustContact(t, upeerB, addressB, now))
	nodeA.RoutingTable().AddContact(mustContact(t, upeerC, addressC, now))
	nodeB.RoutingTable().AddContact(mustContact(t, upeerA, addressA, now))
	nodeB.RoutingTable().AddContact(mustContact(t, upeerC, addressC, now))
	nodeC.RoutingTable().AddContact(mustContact(t, upeerA, addressA, now))

	discovered, err := nodeC.FindNode(upeerB)
	if err != nil {
		t.Fatal(err)
	}
	if !containsContact(discovered, upeerB) {
		t.Fatal("three-node discovery did not learn the second peer")
	}
	if nodeC.RoutingTable().FindContact(upeerB) == nil {
		t.Fatal("discovered contact was not added to the routing table")
	}

	key := strings.Repeat("11", 20)
	deliveries, err := nodeA.StoreValue(key, "hello", upeerA)
	if err != nil {
		t.Fatal(err)
	}
	if deliveries != 2 {
		t.Fatalf("expected two store deliveries, got %d", deliveries)
	}
	if value, ok := nodeB.ValueStore().Get(key); !ok || value.Value != "hello" {
		t.Fatal("second node did not store the value")
	}
	if value, ok := nodeC.ValueStore().Get(key); !ok || value.Value != "hello" {
		t.Fatal("third node did not store the value")
	}

	remoteKey := strings.Repeat("22", 20)
	nodeB.ValueStore().Set(remoteKey, "remote", upeerB, "")
	found, ok, err := nodeC.FindValue(remoteKey)
	if err != nil {
		t.Fatal(err)
	}
	if !ok || found.Value != "remote" || found.Publisher != upeerB {
		t.Fatalf("unexpected remote value %#v", found)
	}
}

func TestKademliaMaintenanceExpiresValues(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	node, err := NewKademliaNode(strings.Repeat("d4", 16), func(string, any) error { return nil }, clock, 25*time.Millisecond)
	if err != nil {
		t.Fatal(err)
	}
	key := strings.Repeat("33", 20)
	node.ValueStore().Set(key, "value", node.UpeerID(), "")
	now = now.Add(KademliaValueTTL + time.Millisecond)
	if removed := node.Maintenance(); removed != 1 {
		t.Fatalf("expected one expired value, got %d", removed)
	}
	if _, ok := node.ValueStore().Get(key); ok {
		t.Fatal("expired value was not removed")
	}
}

func TestUnresponsivePeerDoesNotBlockLookup(t *testing.T) {
	now := time.UnixMilli(1_000)
	node, err := NewKademliaNode(strings.Repeat("e5", 16), func(string, any) error { return nil }, func() time.Time { return now }, 25*time.Millisecond)
	if err != nil {
		t.Fatal(err)
	}
	dead := mustContact(t, strings.Repeat("f6", 16), "200::dead", now)
	node.RoutingTable().AddContact(dead)
	started := time.Now()
	if _, ok, err := node.FindValue(strings.Repeat("44", 20)); err != nil || ok {
		t.Fatalf("expected no value, got %v %v", ok, err)
	}
	if elapsed := time.Since(started); elapsed > time.Second {
		t.Fatalf("lookup blocked for %s", elapsed)
	}
	if node.Stats().FindOperations != 1 {
		t.Fatal("find operation was not accounted")
	}
}

type simNetwork struct {
	nodes map[string]*KademliaNode
}

func newSimNetwork() *simNetwork {
	return &simNetwork{nodes: make(map[string]*KademliaNode)}
}

func (network *simNetwork) register(address string, node *KademliaNode) {
	network.nodes[address] = node
}

func (network *simNetwork) send(sourceUpeerID, sourceAddress, targetAddress string, message any) error {
	target, ok := network.nodes[targetAddress]
	if !ok {
		return nil
	}
	response := target.HandleMessage(sourceUpeerID, message, sourceAddress)
	if response != nil {
		if source, exists := network.nodes[sourceAddress]; exists {
			source.HandleMessage(target.UpeerID(), response, targetAddress)
		}
	}
	return nil
}

func newSimNode(
	t *testing.T,
	upeerID, address string,
	network *simNetwork,
	clock func() time.Time,
	timeout time.Duration,
) *KademliaNode {
	t.Helper()
	node, err := NewKademliaNode(upeerID, func(targetAddress string, message any) error {
		return network.send(upeerID, address, targetAddress, message)
	}, clock, timeout)
	if err != nil {
		t.Fatal(err)
	}
	network.register(address, node)
	return node
}

func mustContact(t *testing.T, upeerID, address string, now time.Time) KademliaContact {
	t.Helper()
	nodeID, err := NewKademliaID(upeerID)
	if err != nil {
		t.Fatal(err)
	}
	return KademliaContact{
		NodeID: nodeID, UpeerID: upeerID, Address: address,
		PublicKey: "pk-" + upeerID[:4], LastSeen: now,
	}
}

func containsContact(contacts []KademliaContact, upeerID string) bool {
	for _, contact := range contacts {
		if contact.UpeerID == upeerID {
			return true
		}
	}
	return false
}

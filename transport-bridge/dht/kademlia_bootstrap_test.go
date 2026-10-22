package dht

import (
	"testing"
	"time"
)

type kademliaSimNetwork struct {
	nodes map[string]*KademliaNode
}

func newKademliaSimNetwork() *kademliaSimNetwork {
	return &kademliaSimNetwork{nodes: make(map[string]*KademliaNode)}
}

func (network *kademliaSimNetwork) register(seed KademliaSeed, node *KademliaNode) {
	network.nodes[seed.Address] = node
}

func (network *kademliaSimNetwork) send(sourceUpeerID, sourceAddress, targetAddress string, message any) {
	target := network.nodes[targetAddress]
	if target == nil {
		return
	}
	response := target.HandleMessage(sourceUpeerID, message, sourceAddress)
	if response == nil {
		return
	}
	if source := network.nodes[sourceAddress]; source != nil {
		source.HandleMessage(target.upeerID, response, targetAddress)
	}
}

func newKademliaTestNode(
	seed KademliaSeed,
	network *kademliaSimNetwork,
	clock func() time.Time,
	timeout time.Duration,
) *KademliaNode {
	node, err := NewKademliaNode(seed.UpeerID, func(address string, message any) error {
		network.send(seed.UpeerID, seed.Address, address, message)
		return nil
	}, clock, timeout)
	if err != nil {
		panic(err)
	}
	network.register(seed, node)
	return node
}

func emptyKademliaSeedLoader(selfUpeerID string) *KademliaSeedLoader {
	loader := NewKademliaSeedLoader(selfUpeerID)
	loader.ResolveTXT = func(string) ([]string, error) { return nil, nil }
	return loader
}

func testKademliaBootstrapConfig(clock func() time.Time) KademliaBootstrapConfig {
	return KademliaBootstrapConfig{
		Clock: clock, MinNodes: 10, Retry: 30 * time.Second, MaxRetry: 2 * time.Minute,
		ProbeTimeout: 100 * time.Millisecond, LookupRounds: 1, ProbeBatch: 20,
	}
}

func TestKademliaBootstrapReachesTenVerifiedNodes(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	desktopSeed := testKademliaSeed(1)
	peerSeeds := make([]KademliaSeed, 0, 14)
	network := newKademliaSimNetwork()
	desktop := newKademliaTestNode(desktopSeed, network, clock, 100*time.Millisecond)
	for index := 2; index <= 15; index++ {
		peer := testKademliaSeed(index)
		peerSeeds = append(peerSeeds, peer)
		newKademliaTestNode(peer, network, clock, 100*time.Millisecond)
	}
	for _, peer := range peerSeeds {
		desktop.routing.AddContact(testKademliaContact(peer, now))
	}
	mobile := newKademliaTestNode(testKademliaSeed(99), network, clock, 100*time.Millisecond)
	loader := emptyKademliaSeedLoader(mobile.upeerID)
	loader.StaticSeeds = func() []KademliaSeed { return []KademliaSeed{desktopSeed} }
	bootstrap := NewKademliaBootstrap(mobile, loader, nil, testKademliaBootstrapConfig(clock))

	result := bootstrap.Attempt()
	if !result.Ran || !result.Bootstrapped || result.VerifiedContacts < 10 {
		t.Fatalf("unexpected bootstrap result: %+v", result)
	}
	if mobile.routing.ContactCount() < 10 {
		t.Fatalf("expected at least ten contacts, got %d", mobile.routing.ContactCount())
	}
	if mobile.routing.FindContact(desktopSeed.UpeerID) == nil {
		t.Fatal("desktop seed was not retained")
	}
	if bootstrap.Attempt().Ran {
		t.Fatal("bootstrapped node should not attempt again")
	}
}

func TestKademliaBootstrapBackoffAndNetworkChange(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	network := newKademliaSimNetwork()
	mobile := newKademliaTestNode(testKademliaSeed(90), network, clock, 50*time.Millisecond)
	bootstrap := NewKademliaBootstrap(
		mobile, emptyKademliaSeedLoader(mobile.upeerID), nil, testKademliaBootstrapConfig(clock),
	)

	first := bootstrap.Attempt()
	if !first.Ran || first.Bootstrapped {
		t.Fatalf("unexpected first attempt: %+v", first)
	}
	if first.NextAttempt.Sub(now) != 30*time.Second {
		t.Fatalf("unexpected initial retry delay: %s", first.NextAttempt.Sub(now))
	}
	now = now.Add(10 * time.Second)
	if _, ran := bootstrap.Maintenance(); ran {
		t.Fatal("maintenance ignored retry backoff")
	}
	now = now.Add(20 * time.Second)
	if _, ran := bootstrap.Maintenance(); !ran {
		t.Fatal("maintenance did not retry after backoff")
	}
	if delay := bootstrap.Stats().NextAttempt.Sub(now); delay != 60*time.Second {
		t.Fatalf("expected 60s backoff, got %s", delay)
	}
	bootstrap.OnNetworkChanged()
	now = now.Add(time.Millisecond)
	if _, ran := bootstrap.Maintenance(); !ran {
		t.Fatal("network change did not reset retry backoff")
	}
	if bootstrap.Stats().Attempts != 3 {
		t.Fatalf("expected three attempts, got %d", bootstrap.Stats().Attempts)
	}
}

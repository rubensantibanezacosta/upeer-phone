package dht

import (
	"path/filepath"
	"testing"
	"time"
)

func TestKademliaBootstrapRestoresContactsAfterRestart(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	directory := t.TempDir()
	desktopSeed := testKademliaSeed(1)
	network := newKademliaSimNetwork()
	desktop := newKademliaTestNode(desktopSeed, network, clock, 100*time.Millisecond)
	for index := 2; index <= 15; index++ {
		peer := testKademliaSeed(index)
		newKademliaTestNode(peer, network, clock, 100*time.Millisecond)
		desktop.routing.AddContact(testKademliaContact(peer, now))
	}
	mobile := newKademliaTestNode(testKademliaSeed(98), network, clock, 100*time.Millisecond)
	loader := emptyKademliaSeedLoader(mobile.upeerID)
	loader.StaticSeeds = func() []KademliaSeed { return []KademliaSeed{desktopSeed} }
	store := NewKademliaContactFileStore(filepath.Join(directory, "contacts.json"), mobile.upeerID)
	bootstrap := NewKademliaBootstrap(mobile, loader, store, testKademliaBootstrapConfig(clock))
	if !bootstrap.Attempt().Bootstrapped {
		t.Fatal("initial bootstrap failed")
	}

	restartedSeed := testKademliaSeed(97)
	restarted, err := NewKademliaNode(
		restartedSeed.UpeerID,
		func(string, any) error { return nil },
		clock,
		50*time.Millisecond,
	)
	if err != nil {
		t.Fatal(err)
	}
	restored := NewKademliaBootstrap(
		restarted,
		emptyKademliaSeedLoader(restarted.upeerID),
		NewKademliaContactFileStore(filepath.Join(directory, "contacts.json"), restarted.upeerID),
		testKademliaBootstrapConfig(clock),
	)
	if !restored.IsBootstrapped() || restored.VerifiedContactCount() < 10 {
		t.Fatalf("restart did not restore ten contacts: %+v", restored.Stats())
	}
}

func TestKademliaContactStoreCapsAndFilters(t *testing.T) {
	now := time.UnixMilli(1_000)
	valid := make([]KademliaSeed, 0, 5)
	for index := 1; index <= 5; index++ {
		valid = append(valid, testKademliaSeed(index))
	}
	invalid := testKademliaSeed(6)
	invalid.PublicKey = testKademliaSeed(7).PublicKey
	store := NewKademliaContactFileStore(
		filepath.Join(t.TempDir(), "contacts.json"),
		testKademliaSeed(99).UpeerID,
	)
	store.MaxContacts = 2
	contacts := []KademliaContact{
		testKademliaContact(valid[0], now),
		testKademliaContact(valid[0], now.Add(time.Second)),
		testKademliaContact(invalid, now.Add(2*time.Second)),
		testKademliaContact(valid[1], now.Add(3*time.Second)),
		testKademliaContact(valid[2], now.Add(4*time.Second)),
		testKademliaContact(valid[3], now.Add(5*time.Second)),
		testKademliaContact(valid[4], now.Add(6*time.Second)),
	}
	if err := store.Save(contacts); err != nil {
		t.Fatal(err)
	}
	restored, err := store.Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(restored) != 2 || restored[0].UpeerID != valid[4].UpeerID || restored[1].UpeerID != valid[3].UpeerID {
		t.Fatalf("unexpected capped contacts: %+v", restored)
	}
}

func TestKademliaLanDiscoveryPolicyAndRateLimit(t *testing.T) {
	now := time.UnixMilli(1_000)
	clock := func() time.Time { return now }
	var sent []string
	node, err := NewKademliaNode(testKademliaSeed(1).UpeerID, func(address string, message any) error {
		if _, ok := message.(*DhtPing); !ok {
			t.Fatalf("unexpected LAN message %T", message)
		}
		sent = append(sent, address)
		return nil
	}, clock, 50*time.Millisecond)
	if err != nil {
		t.Fatal(err)
	}
	policy := KademliaLanPolicy{
		MulticastPermitted: true, WifiMulticastPermission: true,
		ServiceActive: true, NetworkAvailable: true,
	}
	discovery := NewKademliaLanDiscovery(node, func() KademliaLanPolicy { return policy }, clock, 5*time.Minute)
	if !discovery.Probe(3) || sent[0] != "ff02::1%3" {
		t.Fatalf("unexpected LAN probe: %+v", sent)
	}
	if discovery.Probe(3) {
		t.Fatal("LAN probe was not rate limited")
	}
	now = now.Add(5 * time.Minute)
	if !discovery.Probe(0) || sent[1] != "ff02::1" {
		t.Fatalf("unexpected second LAN probe: %+v", sent)
	}
	policy.BatterySaving = true
	now = now.Add(5 * time.Minute)
	if discovery.Probe(3) {
		t.Fatal("LAN probe ignored battery policy")
	}
}

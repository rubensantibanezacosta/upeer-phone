package dht

import (
	"testing"
	"time"
)

func TestKademliaPublishesAndFindsAccountRegistry(t *testing.T) {
	rootKey := accountTestKey(100)
	deviceKey := accountTestKey(101)
	accountUpeerID := accountTestID(rootKey)
	certificate := accountTestCertificate(t, rootKey, deviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	registry := accountTestRegistry(t, rootKey, []string{certificate}, nil, 7, accountTestNow)
	node, err := NewKademliaNode(accountUpeerID, nil, func() time.Time { return time.UnixMilli(accountTestNow) }, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	if deliveries, err := node.PublishAccountRegistry(registry, accountTestNow); err != nil || deliveries != 0 {
		t.Fatalf("publish deliveries = %d, %v", deliveries, err)
	}
	found, ok, err := node.FindAccountRegistry(accountUpeerID, accountTestNow)
	if err != nil || !ok {
		t.Fatalf("find registry = %v, %v", ok, err)
	}
	if found.Epoch != 7 || len(found.Devices) != 1 {
		t.Fatalf("registry = %#v", found)
	}
	key, err := AccountRegistryKey(accountUpeerID)
	if err != nil {
		t.Fatal(err)
	}
	node.ValueStore().Set(key, map[string]any{"version": 1}, accountUpeerID, "")
	if _, _, err := node.FindAccountRegistry(accountUpeerID, accountTestNow); err == nil {
		t.Fatal("invalid account registry value was accepted from the DHT")
	}
}

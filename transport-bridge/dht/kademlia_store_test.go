package dht

import (
	"fmt"
	"testing"
	"time"
)

func TestKademliaValueStoreEvictsOldestAtCapacity(t *testing.T) {
	store := NewKademliaValueStore(fixedClock(time.UnixMilli(1_000)), 2)
	store.Set("aa", "one", "peer", "")
	store.Set("bb", "two", "peer", "")
	store.Set("cc", "three", "peer", "")
	if store.Size() != 2 {
		t.Fatalf("expected two entries, got %d", store.Size())
	}
	if _, ok := store.Get("aa"); ok {
		t.Fatal("oldest entry was not evicted")
	}
	if value, ok := store.Get("cc"); !ok || value.Value != "three" {
		t.Fatal("newest entry is missing")
	}
}

func TestKademliaValueStoreExpiresAfterThirtyDays(t *testing.T) {
	now := time.UnixMilli(1_000)
	store := NewKademliaValueStore(func() time.Time { return now }, 0)
	store.Set("aa", "value", "peer", "")
	now = now.Add(KademliaValueTTL)
	if store.Size() != 1 {
		t.Fatal("entry expired too early")
	}
	now = now.Add(time.Millisecond)
	if removed := store.CleanupExpiredValues(); removed != 1 {
		t.Fatalf("expected one expired entry, got %d", removed)
	}
	if store.Has("aa") {
		t.Fatal("expired entry was not removed")
	}
}

func TestKademliaValueStoreDeduplicatesAndSortsDevices(t *testing.T) {
	store := NewKademliaValueStore(fixedClock(time.UnixMilli(1_000)), 0)
	store.Set("aa", deviceValue("device-a", 5, "200::1"), "peer", "")
	store.Set("aa", deviceValue("device-b", 9, "200::2"), "peer", "")
	store.Set("aa", deviceValue("device-a", 12, "200::3"), "peer", "")
	value, ok := store.Get("aa")
	if !ok {
		t.Fatal("stored value is missing")
	}
	devices, ok := value.Value.([]any)
	if !ok || len(devices) != 2 {
		t.Fatalf("expected two devices, got %#v", value.Value)
	}
	first := devices[0].(map[string]any)
	if first["deviceId"] != "device-a" || kademliaDeviceSeq(first) != 12 || first["address"] != "200::3" {
		t.Fatalf("unexpected first device %#v", first)
	}
}

func TestKademliaValueStoreCapsDevicesPerKey(t *testing.T) {
	store := NewKademliaValueStore(fixedClock(time.UnixMilli(1_000)), 0)
	for index := 0; index < 12; index++ {
		store.Set("aa", deviceValue(fmt.Sprintf("device-%d", index), int64(index), fmt.Sprintf("200::%d", index)), "peer", "")
	}
	value, _ := store.Get("aa")
	devices := value.Value.([]any)
	if len(devices) != KademliaMaxDevicesPerKey {
		t.Fatalf("expected %d devices, got %d", KademliaMaxDevicesPerKey, len(devices))
	}
	if devices[0].(map[string]any)["deviceId"] != "device-11" {
		t.Fatal("devices are not sorted by descending dhtSeq")
	}
}

func TestKademliaValueStoreKeepsMetadataAndReplacesPlainValues(t *testing.T) {
	now := time.UnixMilli(1_000)
	store := NewKademliaValueStore(func() time.Time { return now }, 0)
	store.Set("AA", "first", "peer-one", "")
	now = now.Add(10 * time.Millisecond)
	store.Set("aa", "second", "peer-two", "")
	value, ok := store.Get("aa")
	if !ok || value.Value != "second" || value.Publisher != "peer-two" || !value.Timestamp.Equal(now) {
		t.Fatalf("unexpected stored metadata %#v", value)
	}
	if store.Size() != 1 {
		t.Fatal("updating a key must not grow the store")
	}
}

func deviceValue(deviceID string, dhtSeq int64, address string) map[string]any {
	return map[string]any{"deviceId": deviceID, "dhtSeq": dhtSeq, "address": address}
}

func fixedClock(now time.Time) func() time.Time {
	return func() time.Time { return now }
}

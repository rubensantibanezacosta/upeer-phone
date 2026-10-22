package dht

import (
	"encoding/json"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

type KademliaStoredValue struct {
	Key       string
	Value     any
	Publisher string
	Timestamp time.Time
	Signature string
}

type KademliaValueStore struct {
	clock      func() time.Time
	maxEntries int
	lock       sync.RWMutex
	entries    map[string]KademliaStoredValue
	order      []string
}

func NewKademliaValueStore(clock func() time.Time, maxEntries int) *KademliaValueStore {
	if clock == nil {
		clock = time.Now
	}
	if maxEntries <= 0 {
		maxEntries = KademliaMaxStoreEntries
	}
	return &KademliaValueStore{
		clock:      clock,
		maxEntries: maxEntries,
		entries:    make(map[string]KademliaStoredValue),
	}
}

func (store *KademliaValueStore) Set(key string, value any, publisher, signature string) {
	normalized := strings.ToLower(key)
	store.lock.Lock()
	defer store.lock.Unlock()
	existing, exists := store.entries[normalized]
	finalValue := mergeKademliaDeviceValue(existing, value)
	if !exists && len(store.entries) >= store.maxEntries && len(store.order) > 0 {
		oldest := store.order[0]
		store.order = store.order[1:]
		delete(store.entries, oldest)
	}
	store.entries[normalized] = KademliaStoredValue{
		Key: normalized, Value: finalValue, Publisher: publisher, Timestamp: store.clock(), Signature: signature,
	}
	if !exists {
		store.order = append(store.order, normalized)
	}
}

func (store *KademliaValueStore) Get(key string) (KademliaStoredValue, bool) {
	store.lock.RLock()
	defer store.lock.RUnlock()
	value, ok := store.entries[strings.ToLower(key)]
	return value, ok
}

func (store *KademliaValueStore) Has(key string) bool {
	store.lock.RLock()
	defer store.lock.RUnlock()
	_, ok := store.entries[strings.ToLower(key)]
	return ok
}

func (store *KademliaValueStore) Delete(key string) bool {
	normalized := strings.ToLower(key)
	store.lock.Lock()
	defer store.lock.Unlock()
	if _, ok := store.entries[normalized]; !ok {
		return false
	}
	delete(store.entries, normalized)
	store.removeOrderLocked(normalized)
	return true
}

func (store *KademliaValueStore) CleanupExpiredValues() int {
	now := store.clock()
	store.lock.Lock()
	defer store.lock.Unlock()
	removed := 0
	for key, value := range store.entries {
		if now.Sub(value.Timestamp) > KademliaValueTTL {
			delete(store.entries, key)
			store.removeOrderLocked(key)
			removed++
		}
	}
	return removed
}

func (store *KademliaValueStore) GetAll() []KademliaStoredValue {
	store.lock.RLock()
	defer store.lock.RUnlock()
	values := make([]KademliaStoredValue, 0, len(store.order))
	for _, key := range store.order {
		if value, ok := store.entries[key]; ok {
			values = append(values, value)
		}
	}
	return values
}

func (store *KademliaValueStore) Size() int {
	store.lock.RLock()
	defer store.lock.RUnlock()
	return len(store.entries)
}

func (store *KademliaValueStore) removeOrderLocked(key string) {
	for index, candidate := range store.order {
		if candidate == key {
			store.order = append(store.order[:index], store.order[index+1:]...)
			return
		}
	}
}

func mergeKademliaDeviceValue(existing KademliaStoredValue, value any) any {
	deviceID, ok := kademliaDeviceID(value)
	if !ok {
		return value
	}
	devices := make([]any, 0)
	switch current := existing.Value.(type) {
	case []any:
		for _, item := range current {
			entry, isObject := item.(map[string]any)
			if !isObject {
				continue
			}
			if _, hasDevice := kademliaDeviceID(entry); hasDevice {
				devices = append(devices, entry)
			}
		}
	case map[string]any:
		if _, hasDevice := kademliaDeviceID(current); hasDevice {
			devices = append(devices, current)
		}
	}
	filtered := devices[:0]
	for _, item := range devices {
		entry := item.(map[string]any)
		if entryDevice, _ := kademliaDeviceID(entry); entryDevice != deviceID {
			filtered = append(filtered, item)
		}
	}
	filtered = append(filtered, value)
	sort.SliceStable(filtered, func(left, right int) bool {
		return kademliaDeviceSeq(filtered[left]) > kademliaDeviceSeq(filtered[right])
	})
	if len(filtered) > KademliaMaxDevicesPerKey {
		filtered = filtered[:KademliaMaxDevicesPerKey]
	}
	return filtered
}

func kademliaDeviceID(value any) (string, bool) {
	entry, ok := value.(map[string]any)
	if !ok {
		return "", false
	}
	deviceID, ok := entry["deviceId"].(string)
	return deviceID, ok && deviceID != ""
}

func kademliaDeviceSeq(value any) uint64 {
	entry, ok := value.(map[string]any)
	if !ok {
		return 0
	}
	switch sequence := entry["dhtSeq"].(type) {
	case json.Number:
		parsed, err := strconv.ParseUint(sequence.String(), 10, 64)
		if err == nil {
			return parsed
		}
	case float64:
		if sequence >= 0 {
			return uint64(sequence)
		}
	case int64:
		if sequence >= 0 {
			return uint64(sequence)
		}
	case int:
		if sequence >= 0 {
			return uint64(sequence)
		}
	}
	return 0
}

package dht

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"time"
)

type KademliaContactStore interface {
	Load() ([]KademliaContact, error)
	Save([]KademliaContact) error
}

type KademliaContactFileStore struct {
	Path        string
	SelfUpeerID string
	MaxContacts int
	MaxBytes    int
}

type kademliaContactRecord struct {
	UpeerID   string  `json:"upeerId"`
	Address   string  `json:"address"`
	PublicKey string  `json:"publicKey"`
	LastSeen  int64   `json:"lastSeen"`
	DhtSeq    *uint64 `json:"dhtSeq,omitempty"`
}

func NewKademliaContactFileStore(path, selfUpeerID string) *KademliaContactFileStore {
	return &KademliaContactFileStore{
		Path: path, SelfUpeerID: selfUpeerID,
		MaxContacts: KademliaMaxPersistedContacts, MaxBytes: KademliaMaxSeedFileBytes,
	}
}

func (store *KademliaContactFileStore) Load() ([]KademliaContact, error) {
	info, err := os.Stat(store.Path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, fmt.Errorf("stat DHT contacts: %w", err)
	}
	limit := store.MaxBytes
	if limit <= 0 {
		limit = KademliaMaxSeedFileBytes
	}
	if !info.Mode().IsRegular() || info.Size() <= 0 || info.Size() > int64(limit) {
		return nil, nil
	}
	content, err := os.ReadFile(store.Path)
	if err != nil {
		return nil, fmt.Errorf("read DHT contacts: %w", err)
	}
	var records []kademliaContactRecord
	if err := json.Unmarshal(content, &records); err != nil {
		return nil, fmt.Errorf("decode DHT contacts: %w", err)
	}
	contacts := make([]KademliaContact, 0, len(records))
	seen := make(map[string]struct{})
	for _, record := range records {
		if record.LastSeen <= 0 {
			continue
		}
		contact, ok := NormalizeKademliaContact(KademliaContact{
			UpeerID: record.UpeerID, Address: record.Address, PublicKey: record.PublicKey,
			DhtSeq: record.DhtSeq,
		}, store.SelfUpeerID)
		if !ok {
			continue
		}
		if _, exists := seen[contact.UpeerID]; exists {
			continue
		}
		seen[contact.UpeerID] = struct{}{}
		contact.LastSeen = timeFromMillis(record.LastSeen)
		contacts = append(contacts, contact)
	}
	sort.SliceStable(contacts, func(left, right int) bool {
		return contacts[left].LastSeen.After(contacts[right].LastSeen)
	})
	maximum := store.MaxContacts
	if maximum <= 0 {
		maximum = KademliaMaxPersistedContacts
	}
	if len(contacts) > maximum {
		contacts = contacts[:maximum]
	}
	return contacts, nil
}

func (store *KademliaContactFileStore) Save(contacts []KademliaContact) error {
	contactList := make([]KademliaContact, 0, len(contacts))
	seen := make(map[string]struct{})
	for _, contact := range contacts {
		if contact.LastSeen.IsZero() {
			continue
		}
		normalized, ok := NormalizeKademliaContact(contact, store.SelfUpeerID)
		if !ok {
			continue
		}
		if _, exists := seen[normalized.UpeerID]; exists {
			continue
		}
		seen[normalized.UpeerID] = struct{}{}
		contactList = append(contactList, normalized)
	}
	sort.SliceStable(contactList, func(left, right int) bool {
		return contactList[left].LastSeen.After(contactList[right].LastSeen)
	})
	maximum := store.MaxContacts
	if maximum <= 0 {
		maximum = KademliaMaxPersistedContacts
	}
	if len(contactList) > maximum {
		contactList = contactList[:maximum]
	}
	records := make([]kademliaContactRecord, 0, len(contactList))
	for _, contact := range contactList {
		records = append(records, kademliaContactRecord{
			UpeerID: contact.UpeerID, Address: contact.Address, PublicKey: contact.PublicKey,
			LastSeen: contact.LastSeen.UnixMilli(), DhtSeq: contact.DhtSeq,
		})
	}
	content, err := json.Marshal(records)
	if err != nil {
		return fmt.Errorf("encode DHT contacts: %w", err)
	}
	limit := store.MaxBytes
	if limit <= 0 {
		limit = KademliaMaxSeedFileBytes
	}
	if len(content) > limit {
		return fmt.Errorf("DHT contacts exceed %d bytes", limit)
	}
	directory := filepath.Dir(store.Path)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		return fmt.Errorf("create DHT contacts directory: %w", err)
	}
	temporary, err := os.CreateTemp(directory, ".dht-contacts-*")
	if err != nil {
		return fmt.Errorf("create temporary DHT contacts file: %w", err)
	}
	name := temporary.Name()
	defer os.Remove(name)
	if err := temporary.Chmod(0o600); err != nil {
		temporary.Close()
		return fmt.Errorf("protect DHT contacts file: %w", err)
	}
	if _, err := temporary.Write(content); err != nil {
		temporary.Close()
		return fmt.Errorf("write DHT contacts: %w", err)
	}
	if err := temporary.Close(); err != nil {
		return fmt.Errorf("close DHT contacts: %w", err)
	}
	if err := os.Rename(name, store.Path); err != nil {
		return fmt.Errorf("replace DHT contacts: %w", err)
	}
	return nil
}

func timeFromMillis(value int64) time.Time {
	return time.UnixMilli(value)
}

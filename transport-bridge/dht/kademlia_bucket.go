package dht

import (
	"sort"
	"strings"
	"time"
)

type KademliaContact struct {
	NodeID    KademliaID
	UpeerID   string
	Address   string
	PublicKey string
	LastSeen  time.Time
	DhtSeq    *uint64
}

type kBucket struct {
	contacts    []KademliaContact
	maxSize     int
	lastUpdated time.Time
	clock       func() time.Time
}

func newKBucket(maxSize int, clock func() time.Time) *kBucket {
	return &kBucket{maxSize: maxSize, lastUpdated: clock(), clock: clock}
}

func (bucket *kBucket) add(contact KademliaContact) bool {
	index := -1
	for position, existing := range bucket.contacts {
		if strings.EqualFold(existing.UpeerID, contact.UpeerID) {
			index = position
			break
		}
	}
	if index >= 0 {
		bucket.contacts = append(bucket.contacts[:index], bucket.contacts[index+1:]...)
	} else if len(bucket.contacts) >= bucket.maxSize {
		bucket.contacts = bucket.contacts[1:]
	}
	bucket.contacts = append(bucket.contacts, contact)
	bucket.lastUpdated = bucket.clock()
	return true
}

func (bucket *kBucket) remove(upeerID string) bool {
	for index, contact := range bucket.contacts {
		if strings.EqualFold(contact.UpeerID, upeerID) {
			bucket.contacts = append(bucket.contacts[:index], bucket.contacts[index+1:]...)
			return true
		}
	}
	return false
}

func (bucket *kBucket) findClosest(target KademliaID, limit int) []KademliaContact {
	contacts := bucket.all()
	sort.SliceStable(contacts, func(left, right int) bool {
		return contacts[left].NodeID.CompareDistanceTo(contacts[right].NodeID, target) < 0
	})
	if limit < len(contacts) {
		return contacts[:limit]
	}
	return contacts
}

func (bucket *kBucket) needsRefresh() bool {
	return bucket.clock().Sub(bucket.lastUpdated) > KademliaRefreshInterval
}

func (bucket *kBucket) all() []KademliaContact {
	return append([]KademliaContact(nil), bucket.contacts...)
}

func (bucket *kBucket) size() int {
	return len(bucket.contacts)
}

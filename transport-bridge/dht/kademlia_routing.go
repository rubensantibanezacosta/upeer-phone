package dht

import (
	"sort"
	"strings"
	"sync"
	"time"
)

type KademliaRoutingTable struct {
	nodeID  KademliaID
	clock   func() time.Time
	buckets []*kBucket
	lock    sync.RWMutex
}

func NewKademliaRoutingTable(nodeID KademliaID, clock func() time.Time) *KademliaRoutingTable {
	if clock == nil {
		clock = time.Now
	}
	buckets := make([]*kBucket, KademliaBucketCount)
	for index := range buckets {
		buckets[index] = newKBucket(KademliaK, clock)
	}
	return &KademliaRoutingTable{nodeID: nodeID, clock: clock, buckets: buckets}
}

func (table *KademliaRoutingTable) AddContact(contact KademliaContact) bool {
	if contact.NodeID == table.nodeID {
		return false
	}
	table.lock.Lock()
	defer table.lock.Unlock()
	return table.buckets[KademliaBucketIndex(table.nodeID, contact.NodeID)].add(contact)
}

func (table *KademliaRoutingTable) RemoveContact(upeerID string) bool {
	table.lock.Lock()
	defer table.lock.Unlock()
	contact := table.findContactLocked(upeerID)
	if contact == nil {
		return false
	}
	return table.buckets[KademliaBucketIndex(table.nodeID, contact.NodeID)].remove(upeerID)
}

func (table *KademliaRoutingTable) FindContact(upeerID string) *KademliaContact {
	table.lock.RLock()
	defer table.lock.RUnlock()
	return table.findContactLocked(upeerID)
}

func (table *KademliaRoutingTable) FindClosestContacts(target KademliaID, limit int) []KademliaContact {
	table.lock.RLock()
	contacts := table.allContactsLocked()
	table.lock.RUnlock()
	sort.SliceStable(contacts, func(left, right int) bool {
		return contacts[left].NodeID.CompareDistanceTo(contacts[right].NodeID, target) < 0
	})
	if limit < len(contacts) {
		return contacts[:limit]
	}
	return contacts
}

func (table *KademliaRoutingTable) AllContacts() []KademliaContact {
	table.lock.RLock()
	defer table.lock.RUnlock()
	return table.allContactsLocked()
}

func (table *KademliaRoutingTable) ContactCount() int {
	table.lock.RLock()
	defer table.lock.RUnlock()
	total := 0
	for _, bucket := range table.buckets {
		total += bucket.size()
	}
	return total
}

func (table *KademliaRoutingTable) RefreshStaleBuckets() []int {
	table.lock.RLock()
	defer table.lock.RUnlock()
	stale := make([]int, 0)
	for index, bucket := range table.buckets {
		if bucket.needsRefresh() {
			stale = append(stale, index)
		}
	}
	return stale
}

func (table *KademliaRoutingTable) BucketCount() int {
	return len(table.buckets)
}

func (table *KademliaRoutingTable) findContactLocked(upeerID string) *KademliaContact {
	for _, bucket := range table.buckets {
		for index := range bucket.contacts {
			if strings.EqualFold(bucket.contacts[index].UpeerID, upeerID) {
				contact := bucket.contacts[index]
				return &contact
			}
		}
	}
	return nil
}

func (table *KademliaRoutingTable) allContactsLocked() []KademliaContact {
	contacts := make([]KademliaContact, 0)
	for _, bucket := range table.buckets {
		contacts = append(contacts, bucket.all()...)
	}
	return contacts
}

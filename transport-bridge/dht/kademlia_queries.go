package dht

import (
	"crypto/rand"
	"encoding/hex"
	"strings"
	"sync"
	"time"
)

type kademliaQueryEntry struct {
	target    string
	messages  chan any
	createdAt time.Time
	resolved  bool
}

type KademliaQueries struct {
	clock   func() time.Time
	pending time.Duration
	lock    sync.Mutex
	entries map[string]*kademliaQueryEntry
}

func NewKademliaQueries(clock func() time.Time, pending time.Duration) *KademliaQueries {
	if clock == nil {
		clock = time.Now
	}
	if pending <= 0 {
		pending = KademliaPendingQueryTTL
	}
	return &KademliaQueries{clock: clock, pending: pending, entries: make(map[string]*kademliaQueryEntry)}
}

func (queries *KademliaQueries) Register(queryID, target string) bool {
	if !validQueryID(queryID) {
		return false
	}
	now := queries.clock()
	queries.lock.Lock()
	defer queries.lock.Unlock()
	queries.cleanupLocked(now)
	if _, exists := queries.entries[queryID]; exists {
		return false
	}
	queries.entries[queryID] = &kademliaQueryEntry{
		target: strings.ToLower(target), messages: make(chan any, 1), createdAt: now,
	}
	return true
}

func (queries *KademliaQueries) Resolve(message any) bool {
	queryID := kademliaMessageQueryID(message)
	if queryID == "" {
		return false
	}
	queries.lock.Lock()
	queries.cleanupLocked(queries.clock())
	entry, ok := queries.entries[queryID]
	if !ok || entry.resolved {
		queries.lock.Unlock()
		return false
	}
	target := kademliaResponseTarget(message)
	if entry.target != "" && target != "" && !strings.EqualFold(entry.target, target) {
		queries.lock.Unlock()
		return false
	}
	entry.resolved = true
	queries.lock.Unlock()
	entry.messages <- message
	return true
}

func (queries *KademliaQueries) Wait(queryID string, timeout time.Duration) (any, bool) {
	queries.lock.Lock()
	entry, ok := queries.entries[queryID]
	if ok {
		delete(queries.entries, queryID)
	}
	queries.lock.Unlock()
	if !ok {
		return nil, false
	}
	timer := time.NewTimer(timeout)
	defer timer.Stop()
	select {
	case message := <-entry.messages:
		return message, true
	case <-timer.C:
		return nil, false
	}
}

func (queries *KademliaQueries) Cancel(queryID string) {
	queries.lock.Lock()
	delete(queries.entries, queryID)
	queries.lock.Unlock()
}

func (queries *KademliaQueries) Cleanup() int {
	queries.lock.Lock()
	defer queries.lock.Unlock()
	return queries.cleanupLocked(queries.clock())
}

func (queries *KademliaQueries) Size() int {
	queries.lock.Lock()
	defer queries.lock.Unlock()
	queries.cleanupLocked(queries.clock())
	return len(queries.entries)
}

func (queries *KademliaQueries) cleanupLocked(now time.Time) int {
	removed := 0
	for queryID, entry := range queries.entries {
		if now.Sub(entry.createdAt) > queries.pending {
			delete(queries.entries, queryID)
			removed++
		}
	}
	return removed
}

func kademliaMessageQueryID(message any) string {
	switch typed := message.(type) {
	case *DhtPong:
		return typed.QueryID
	case *DhtFoundNodes:
		return typed.QueryID
	case *DhtFoundValue:
		return typed.QueryID
	case *DhtStoreAck:
		return typed.QueryID
	case *DhtResponse:
		return typed.QueryID
	default:
		return ""
	}
}

func kademliaResponseTarget(message any) string {
	switch typed := message.(type) {
	case *DhtFoundValue:
		return typed.Key
	case *DhtStoreAck:
		return typed.Key
	case *DhtResponse:
		return typed.TargetID
	default:
		return ""
	}
}

func newKademliaQueryID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return ""
	}
	return hex.EncodeToString(bytes)
}

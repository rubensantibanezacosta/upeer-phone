package dht

import (
	"errors"
	"fmt"
	"strings"
	"sync/atomic"
	"time"
)

type KademliaSendFunc func(address string, message any) error

type KademliaStats struct {
	MessagesSent     int64
	MessagesReceived int64
	StoreOperations  int64
	FindOperations   int64
}

type KademliaNode struct {
	upeerID      string
	nodeID       KademliaID
	send         KademliaSendFunc
	clock        func() time.Time
	queryTimeout time.Duration
	routing      *KademliaRoutingTable
	values       *KademliaValueStore
	queries      *KademliaQueries
	sent         atomic.Int64
	received     atomic.Int64
	stores       atomic.Int64
	finds        atomic.Int64
}

func NewKademliaNode(
	upeerID string,
	send KademliaSendFunc,
	clock func() time.Time,
	queryTimeout time.Duration,
) (*KademliaNode, error) {
	nodeID, err := NewKademliaID(upeerID)
	if err != nil {
		return nil, fmt.Errorf("create Kademlia node: %w", err)
	}
	if clock == nil {
		clock = time.Now
	}
	if queryTimeout <= 0 {
		queryTimeout = KademliaQueryTimeout
	}
	if send == nil {
		send = func(string, any) error { return errors.New("Kademlia send function is not configured") }
	}
	return &KademliaNode{
		upeerID:      strings.ToLower(upeerID),
		nodeID:       nodeID,
		send:         send,
		clock:        clock,
		queryTimeout: queryTimeout,
		routing:      NewKademliaRoutingTable(nodeID, clock),
		values:       NewKademliaValueStore(clock, KademliaMaxStoreEntries),
		queries:      NewKademliaQueries(clock, KademliaPendingQueryTTL),
	}, nil
}

func (node *KademliaNode) NodeID() KademliaID {
	return node.nodeID
}

func (node *KademliaNode) UpeerID() string {
	return node.upeerID
}

func (node *KademliaNode) RoutingTable() *KademliaRoutingTable {
	return node.routing
}

func (node *KademliaNode) ValueStore() *KademliaValueStore {
	return node.values
}

func (node *KademliaNode) AddContact(contact KademliaContact) bool {
	return node.routing.AddContact(contact)
}

func (node *KademliaNode) BroadcastPing(address string) bool {
	if strings.TrimSpace(address) == "" {
		return false
	}
	if err := node.sendMessage(address, &DhtPing{NodeID: node.nodeID.Hex()}); err != nil {
		return false
	}
	return true
}

func (node *KademliaNode) HandleMessage(senderUpeerID string, message any, senderAddress string) any {
	node.received.Add(1)
	senderID, err := NewKademliaID(senderUpeerID)
	if err != nil {
		return nil
	}
	node.trackContact(strings.ToLower(senderUpeerID), senderID, senderAddress)
	switch typed := message.(type) {
	case *DhtPing:
		return &DhtPong{QueryID: typed.QueryID, NodeID: node.nodeID.Hex()}
	case *DhtFindNode:
		return node.handleFindNode(typed)
	case *DhtFindValue:
		return node.handleFindValue(typed)
	case *DhtStore:
		return node.handleStore(senderUpeerID, typed)
	case *DhtFoundNodes:
		node.ingestNodes(typed.Nodes)
		node.queries.Resolve(typed)
		return nil
	case *DhtFoundValue:
		node.ingestNodes(typed.Nodes)
		node.queries.Resolve(typed)
		return nil
	case *DhtPong:
		node.queries.Resolve(typed)
		return nil
	case *DhtStoreAck:
		node.queries.Resolve(typed)
		return nil
	case *DhtResponse:
		node.queries.Resolve(typed)
		return nil
	default:
		return nil
	}
}

func (node *KademliaNode) StoreValue(key string, value any, publisher string) (int, error) {
	if publisher == "" {
		publisher = node.upeerID
	}
	node.values.Set(key, value, publisher, "")
	node.stores.Add(1)
	target, err := NewLookupKademliaID(key)
	if err != nil {
		return 0, fmt.Errorf("store Kademlia value: %w", err)
	}
	now := node.clock().UnixMilli()
	deliveries := 0
	var firstErr error
	for _, contact := range node.routing.FindClosestContacts(target, KademliaK) {
		if strings.EqualFold(contact.UpeerID, node.upeerID) {
			continue
		}
		message := &DhtStore{
			Key: strings.ToLower(key), Value: value, Publisher: publisher, Timestamp: &now,
		}
		if err := node.sendMessage(contact.Address, message); err != nil {
			if firstErr == nil {
				firstErr = err
			}
			continue
		}
		deliveries++
	}
	return deliveries, firstErr
}

func (node *KademliaNode) FindValue(key string) (KademliaStoredValue, bool, error) {
	node.finds.Add(1)
	if local, ok := node.values.Get(key); ok {
		return local, true, nil
	}
	target, err := NewLookupKademliaID(key)
	if err != nil {
		return KademliaStoredValue{}, false, fmt.Errorf("find Kademlia value: %w", err)
	}
	contacts := node.routing.FindClosestContacts(target, KademliaAlpha)
	queryIDs, dispatchErr := node.dispatchQueries(contacts, key, func(queryID string) any {
		return &DhtFindValue{QueryID: queryID, Key: strings.ToLower(key)}
	})
	responses := node.collectResponses(queryIDs)
	for _, response := range responses {
		found, ok := response.(*DhtFoundValue)
		if !ok || found.Value == nil {
			continue
		}
		timestamp := node.clock()
		if found.Timestamp != nil {
			timestamp = time.UnixMilli(*found.Timestamp)
		}
		storedKey := key
		if found.Key != "" {
			storedKey = strings.ToLower(found.Key)
		}
		return KademliaStoredValue{
			Key: storedKey, Value: found.Value, Publisher: found.Publisher, Timestamp: timestamp,
		}, true, dispatchErr
	}
	return KademliaStoredValue{}, false, dispatchErr
}

func (node *KademliaNode) FindNode(targetID string) ([]KademliaContact, error) {
	node.finds.Add(1)
	target, err := NewLookupKademliaID(targetID)
	if err != nil {
		return nil, fmt.Errorf("find Kademlia node: %w", err)
	}
	contacts := node.routing.FindClosestContacts(target, KademliaAlpha)
	queryIDs, dispatchErr := node.dispatchQueries(contacts, targetID, func(queryID string) any {
		return &DhtFindNode{QueryID: queryID, TargetID: strings.ToLower(targetID)}
	})
	node.collectResponses(queryIDs)
	return node.routing.FindClosestContacts(target, KademliaK), dispatchErr
}

func (node *KademliaNode) Maintenance() int {
	node.queries.Cleanup()
	return node.values.CleanupExpiredValues()
}

func (node *KademliaNode) StaleBuckets() []int {
	return node.routing.RefreshStaleBuckets()
}

func (node *KademliaNode) Stats() KademliaStats {
	return KademliaStats{
		MessagesSent:     node.sent.Load(),
		MessagesReceived: node.received.Load(),
		StoreOperations:  node.stores.Load(),
		FindOperations:   node.finds.Load(),
	}
}

func (node *KademliaNode) sendMessage(address string, message any) error {
	err := node.send(address, message)
	node.sent.Add(1)
	return err
}

func timestampPointer(value int64) *int64 {
	return &value
}

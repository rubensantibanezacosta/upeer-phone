package dht

import "strings"

func (node *KademliaNode) handleFindNode(message *DhtFindNode) any {
	target, err := NewLookupKademliaID(message.TargetID)
	if err != nil {
		return nil
	}
	return node.foundNodes(message.QueryID, target)
}

func (node *KademliaNode) handleFindValue(message *DhtFindValue) any {
	key := strings.ToLower(message.Key)
	if stored, ok := node.values.Get(key); ok {
		return &DhtFoundValue{
			QueryID: message.QueryID, Key: key, Value: stored.Value,
			Publisher: stored.Publisher, Timestamp: timestampPointer(stored.Timestamp.UnixMilli()),
		}
	}
	target, err := NewLookupKademliaID(key)
	if err != nil {
		return &DhtFoundNodes{QueryID: message.QueryID, Nodes: []DhtNode{}}
	}
	return node.foundNodes(message.QueryID, target)
}

func (node *KademliaNode) handleStore(senderUpeerID string, message *DhtStore) any {
	publisher := message.Publisher
	if publisher == "" {
		publisher = strings.ToLower(senderUpeerID)
	}
	node.values.Set(message.Key, message.Value, publisher, "")
	node.stores.Add(1)
	return &DhtStoreAck{QueryID: message.QueryID, Key: strings.ToLower(message.Key)}
}

func (node *KademliaNode) foundNodes(queryID string, target KademliaID) *DhtFoundNodes {
	contacts := node.routing.FindClosestContacts(target, KademliaK)
	nodes := make([]DhtNode, 0, len(contacts))
	for _, contact := range contacts {
		nodes = append(nodes, DhtNode{
			UpeerID: contact.UpeerID, Address: contact.Address, PublicKey: contact.PublicKey, NodeID: contact.NodeID.Hex(),
		})
	}
	return &DhtFoundNodes{QueryID: queryID, Nodes: nodes}
}

func (node *KademliaNode) trackContact(senderUpeerID string, senderID KademliaID, senderAddress string) {
	existing := node.routing.FindContact(senderUpeerID)
	contact := KademliaContact{
		NodeID: senderID, UpeerID: senderUpeerID, Address: senderAddress, LastSeen: node.clock(),
	}
	if existing != nil {
		contact.PublicKey = existing.PublicKey
		contact.DhtSeq = existing.DhtSeq
	}
	node.routing.AddContact(contact)
}

func (node *KademliaNode) ingestNodes(nodes []DhtNode) {
	for _, candidate := range nodes {
		if candidate.PublicKey == "" || candidate.Address == "" {
			continue
		}
		nodeID, err := NewKademliaID(candidate.UpeerID)
		if err != nil {
			continue
		}
		node.routing.AddContact(KademliaContact{
			NodeID: nodeID, UpeerID: strings.ToLower(candidate.UpeerID),
			Address: candidate.Address, PublicKey: candidate.PublicKey, LastSeen: node.clock(),
		})
	}
}

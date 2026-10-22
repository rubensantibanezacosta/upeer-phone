package dht

import "fmt"

func decodeMessage(packet map[string]any, packetType, queryID string) (any, error) {
	switch packetType {
	case "DHT_PING", "DHT_PONG":
		nodeID, _, err := optionalString(packet, "nodeId", MaxNodeIDLength)
		if err != nil {
			return nil, err
		}
		if nodeID != "" && !isHex(nodeID) {
			return nil, fmt.Errorf("invalid nodeId")
		}
		if packetType == "DHT_PING" {
			return &DhtPing{QueryID: queryID, NodeID: nodeID}, nil
		}
		return &DhtPong{QueryID: queryID, NodeID: nodeID}, nil
	case "DHT_FIND_NODE":
		targetID, err := findNodeTarget(packet)
		if err != nil {
			return nil, err
		}
		return &DhtFindNode{QueryID: queryID, TargetID: targetID}, nil
	case "DHT_FOUND_NODES":
		nodes, err := requiredNodes(packet, "nodes")
		if err != nil {
			return nil, err
		}
		return &DhtFoundNodes{QueryID: queryID, Nodes: nodes}, nil
	case "DHT_FIND_VALUE":
		key, err := requiredKey(packet, "key")
		if err != nil {
			return nil, err
		}
		return &DhtFindValue{QueryID: queryID, Key: key}, nil
	case "DHT_FOUND_VALUE":
		return decodeFoundValue(packet, queryID)
	case "DHT_STORE":
		return decodeStore(packet, queryID)
	case "DHT_STORE_ACK":
		key, err := requiredKey(packet, "key")
		if err != nil {
			return nil, err
		}
		return &DhtStoreAck{QueryID: queryID, Key: key}, nil
	case "DHT_QUERY":
		targetID, err := legacyTarget(packet)
		if err != nil {
			return nil, err
		}
		context, err := optionalObject(packet, "referralContext")
		if err != nil {
			return nil, err
		}
		return &DhtQuery{QueryID: queryID, TargetID: targetID, ReferralContext: context}, nil
	case "DHT_RESPONSE":
		return decodeLegacyResponse(packet, queryID)
	case "DHT_UPDATE":
		block, err := requiredLocationBlock(packet, "locationBlock")
		if err != nil {
			return nil, err
		}
		return &DhtUpdate{QueryID: queryID, LocationBlock: block}, nil
	case "DHT_EXCHANGE":
		peers, err := requiredPeers(packet, "peers")
		if err != nil {
			return nil, err
		}
		return &DhtExchange{QueryID: queryID, Peers: peers}, nil
	}
	return nil, fmt.Errorf("unsupported packet type")
}

func decodeFoundValue(packet map[string]any, queryID string) (any, error) {
	key, _, err := optionalKey(packet, "key")
	if err != nil {
		return nil, err
	}
	value, hasValue := packet["value"]
	if !hasValue || value == nil {
		value = nil
	}
	var nodes []DhtNode
	if isPresent(packet, "nodes") {
		nodes, err = requiredNodes(packet, "nodes")
		if err != nil {
			return nil, err
		}
	}
	if value == nil && nodes == nil {
		return nil, fmt.Errorf("missing value or nodes")
	}
	if value != nil {
		if err := validateValueSize(value); err != nil {
			return nil, err
		}
	}
	publisher, _, err := optionalString(packet, "publisher", MaxUpeerIDLength)
	if err != nil {
		return nil, err
	}
	timestamp, err := optionalTimestamp(packet, "timestamp")
	if err != nil {
		return nil, err
	}
	return &DhtFoundValue{
		QueryID: queryID, Key: key, Value: value, Nodes: nodes, Publisher: publisher, Timestamp: timestamp,
	}, nil
}

func decodeStore(packet map[string]any, queryID string) (any, error) {
	value, ok := packet["value"]
	if !ok || value == nil {
		return nil, fmt.Errorf("missing value")
	}
	if err := validateValueSize(value); err != nil {
		return nil, err
	}
	key, err := requiredKey(packet, "key")
	if err != nil {
		return nil, err
	}
	publisher, _, err := optionalString(packet, "publisher", MaxUpeerIDLength)
	if err != nil {
		return nil, err
	}
	timestamp, err := optionalTimestamp(packet, "timestamp")
	if err != nil {
		return nil, err
	}
	ttl, err := optionalInteger(packet, "ttl")
	if err != nil {
		return nil, err
	}
	if ttl != nil && (*ttl < 0 || *ttl > MaxStoreTTLSeconds) {
		return nil, fmt.Errorf("invalid TTL")
	}
	return &DhtStore{
		QueryID: queryID, Key: key, Value: value, Publisher: publisher, Timestamp: timestamp, TTL: ttl,
	}, nil
}

func decodeLegacyResponse(packet map[string]any, queryID string) (any, error) {
	targetID, err := legacyTarget(packet)
	if err != nil {
		return nil, err
	}
	publicKey, _, err := optionalString(packet, "publicKey", MaxNodeIDLength)
	if err != nil {
		return nil, err
	}
	block, err := optionalLocationBlock(packet, "locationBlock")
	if err != nil {
		return nil, err
	}
	neighbors, err := optionalNeighbors(packet, "neighbors")
	if err != nil {
		return nil, err
	}
	return &DhtResponse{
		QueryID: queryID, TargetID: targetID, PublicKey: publicKey, LocationBlock: block, Neighbors: neighbors,
	}, nil
}

package dht

import (
	"encoding/json"
	"fmt"
)

func EncodeMessage(message any) (map[string]any, error) {
	packet, err := messageObject(message)
	if err != nil {
		return nil, err
	}
	if _, err := DecodePacketObject(packet); err != nil {
		return nil, fmt.Errorf("encode Kademlia message: %w", err)
	}
	return packet, nil
}

func EncodeMessageJSON(message any) (string, error) {
	packet, err := EncodeMessage(message)
	if err != nil {
		return "", err
	}
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode Kademlia message JSON: %w", err)
	}
	return string(encoded), nil
}

func messageObject(message any) (map[string]any, error) {
	switch typed := message.(type) {
	case *DhtPing:
		packet := baseMessage("DHT_PING", typed.QueryID)
		putString(packet, "nodeId", typed.NodeID)
		return packet, nil
	case *DhtPong:
		packet := baseMessage("DHT_PONG", typed.QueryID)
		putString(packet, "nodeId", typed.NodeID)
		return packet, nil
	case *DhtFindNode:
		packet := baseMessage("DHT_FIND_NODE", typed.QueryID)
		packet["targetId"] = typed.TargetID
		return packet, nil
	case *DhtFoundNodes:
		packet := baseMessage("DHT_FOUND_NODES", typed.QueryID)
		packet["nodes"] = nodesObject(typed.Nodes)
		return packet, nil
	case *DhtFindValue:
		packet := baseMessage("DHT_FIND_VALUE", typed.QueryID)
		packet["key"] = typed.Key
		return packet, nil
	case *DhtFoundValue:
		return foundValueObject(typed), nil
	case *DhtStore:
		return storeObject(typed), nil
	case *DhtStoreAck:
		packet := baseMessage("DHT_STORE_ACK", typed.QueryID)
		packet["key"] = typed.Key
		return packet, nil
	case *DhtQuery:
		packet := baseMessage("DHT_QUERY", typed.QueryID)
		packet["targetId"] = typed.TargetID
		if typed.ReferralContext != nil {
			packet["referralContext"] = typed.ReferralContext
		}
		return packet, nil
	case *DhtResponse:
		packet := baseMessage("DHT_RESPONSE", typed.QueryID)
		packet["targetId"] = typed.TargetID
		putString(packet, "publicKey", typed.PublicKey)
		if typed.LocationBlock != nil {
			packet["locationBlock"] = typed.LocationBlock
		}
		if typed.Neighbors != nil {
			packet["neighbors"] = neighborsObject(typed.Neighbors)
		}
		return packet, nil
	case *DhtUpdate:
		packet := baseMessage("DHT_UPDATE", typed.QueryID)
		packet["locationBlock"] = typed.LocationBlock
		return packet, nil
	case *DhtExchange:
		packet := baseMessage("DHT_EXCHANGE", typed.QueryID)
		packet["peers"] = peersObject(typed.Peers)
		return packet, nil
	default:
		return nil, fmt.Errorf("unsupported Kademlia message %T", message)
	}
}

func baseMessage(packetType, queryID string) map[string]any {
	packet := map[string]any{"type": packetType}
	putString(packet, "queryId", queryID)
	return packet
}

func foundValueObject(message *DhtFoundValue) map[string]any {
	packet := baseMessage("DHT_FOUND_VALUE", message.QueryID)
	putString(packet, "key", message.Key)
	if message.Value != nil {
		packet["value"] = message.Value
	}
	if message.Nodes != nil {
		packet["nodes"] = nodesObject(message.Nodes)
	}
	putString(packet, "publisher", message.Publisher)
	if message.Timestamp != nil {
		packet["timestamp"] = *message.Timestamp
	}
	return packet
}

func storeObject(message *DhtStore) map[string]any {
	packet := baseMessage("DHT_STORE", message.QueryID)
	packet["key"] = message.Key
	packet["value"] = message.Value
	putString(packet, "publisher", message.Publisher)
	if message.Timestamp != nil {
		packet["timestamp"] = *message.Timestamp
	}
	if message.TTL != nil {
		packet["ttl"] = *message.TTL
	}
	return packet
}

func nodesObject(nodes []DhtNode) []any {
	values := make([]any, 0, len(nodes))
	for _, node := range nodes {
		value := map[string]any{"upeerId": node.UpeerID, "address": node.Address}
		putString(value, "publicKey", node.PublicKey)
		putString(value, "nodeId", node.NodeID)
		values = append(values, value)
	}
	return values
}

func neighborsObject(neighbors []DhtNeighbor) []any {
	values := make([]any, 0, len(neighbors))
	for _, neighbor := range neighbors {
		value := map[string]any{}
		putString(value, "upeerId", neighbor.UpeerID)
		putString(value, "address", neighbor.Address)
		putString(value, "publicKey", neighbor.PublicKey)
		if neighbor.LocationBlock != nil {
			value["locationBlock"] = neighbor.LocationBlock
		}
		values = append(values, value)
	}
	return values
}

func peersObject(peers []DhtExchangePeer) []any {
	values := make([]any, 0, len(peers))
	for _, peer := range peers {
		value := map[string]any{"upeerId": peer.UpeerID, "publicKey": peer.PublicKey}
		if peer.LocationBlock != nil {
			value["locationBlock"] = peer.LocationBlock
		}
		values = append(values, value)
	}
	return values
}

func putString(packet map[string]any, field, value string) {
	if value != "" {
		packet[field] = value
	}
}

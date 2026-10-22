package dht

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

func fieldSet(values ...string) map[string]struct{} {
	fields := make(map[string]struct{}, len(values))
	for _, value := range values {
		fields[value] = struct{}{}
	}
	return fields
}

func withFields(base map[string]struct{}, extra ...string) map[string]struct{} {
	fields := make(map[string]struct{}, len(base)+len(extra))
	for key := range base {
		fields[key] = struct{}{}
	}
	for _, key := range extra {
		fields[key] = struct{}{}
	}
	return fields
}

func joinFields(fields []string) string {
	return strings.Join(fields, ",")
}

func isPresent(packet map[string]any, field string) bool {
	value, ok := packet[field]
	return ok && value != nil
}

func codecRequiredString(packet map[string]any, field string, maxLength int) (string, error) {
	value, ok := packet[field]
	if !ok {
		return "", fmt.Errorf("missing or invalid %s", field)
	}
	text, ok := value.(string)
	if !ok {
		return "", fmt.Errorf("missing or invalid %s", field)
	}
	if len(text) > maxLength {
		return "", fmt.Errorf("invalid %s", field)
	}
	return text, nil
}

func optionalString(packet map[string]any, field string, maxLength int) (string, bool, error) {
	if !isPresent(packet, field) {
		return "", false, nil
	}
	text, ok := packet[field].(string)
	if !ok || len(text) > maxLength {
		return "", false, fmt.Errorf("invalid %s", field)
	}
	return text, true, nil
}

func optionalObject(packet map[string]any, field string) (map[string]any, error) {
	if !isPresent(packet, field) {
		return nil, nil
	}
	value, ok := packet[field].(map[string]any)
	if !ok {
		return nil, fmt.Errorf("invalid %s", field)
	}
	return value, nil
}

func integerValue(value any) (int64, bool) {
	switch typed := value.(type) {
	case json.Number:
		parsed, err := strconv.ParseInt(typed.String(), 10, 64)
		return parsed, err == nil
	case int:
		return int64(typed), true
	case int64:
		return typed, true
	case int32:
		return int64(typed), true
	}
	return 0, false
}

func requiredInteger(packet map[string]any, field string) (int64, error) {
	value, ok := packet[field]
	if !ok {
		return 0, fmt.Errorf("invalid %s", field)
	}
	parsed, ok := integerValue(value)
	if !ok {
		return 0, fmt.Errorf("invalid %s", field)
	}
	return parsed, nil
}

func optionalInteger(packet map[string]any, field string) (*int64, error) {
	if !isPresent(packet, field) {
		return nil, nil
	}
	parsed, err := requiredInteger(packet, field)
	if err != nil {
		return nil, err
	}
	return &parsed, nil
}

func optionalTimestamp(packet map[string]any, field string) (*int64, error) {
	timestamp, err := optionalInteger(packet, field)
	if err != nil {
		return nil, err
	}
	if timestamp != nil && *timestamp < 0 {
		return nil, fmt.Errorf("invalid %s", field)
	}
	return timestamp, nil
}

func requiredKey(packet map[string]any, field string) (string, error) {
	key, err := codecRequiredString(packet, field, 64)
	if err != nil {
		return "", err
	}
	if !validKey(key) {
		return "", fmt.Errorf("invalid key")
	}
	return key, nil
}

func optionalKey(packet map[string]any, field string) (string, bool, error) {
	key, present, err := optionalString(packet, field, 64)
	if err != nil || !present {
		return "", present, err
	}
	if !validKey(key) {
		return "", false, fmt.Errorf("invalid key")
	}
	return key, true, nil
}

func findNodeTarget(packet map[string]any) (string, error) {
	target, err := codecRequiredString(packet, "targetId", MaxNodeIDLength)
	if err != nil {
		return "", err
	}
	if !isHex(target) {
		return "", fmt.Errorf("invalid targetId")
	}
	return target, nil
}

func legacyTarget(packet map[string]any) (string, error) {
	target, err := codecRequiredString(packet, "targetId", MaxUpeerIDLength)
	if err != nil {
		return "", err
	}
	if !validHexID(target) {
		return "", fmt.Errorf("invalid targetId")
	}
	return target, nil
}

func requiredNodes(packet map[string]any, field string) ([]DhtNode, error) {
	raw, ok := packet[field].([]any)
	if !ok {
		return nil, fmt.Errorf("invalid nodes array")
	}
	if len(raw) > MaxResponseNodes {
		return nil, fmt.Errorf("too many nodes")
	}
	nodes := make([]DhtNode, 0, len(raw))
	for _, item := range raw {
		node, ok := item.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("invalid node entry")
		}
		parsed, err := parseNode(node)
		if err != nil {
			return nil, err
		}
		nodes = append(nodes, parsed)
	}
	return nodes, nil
}

func parseNode(node map[string]any) (DhtNode, error) {
	upeerID, err := codecRequiredString(node, "upeerId", MaxUpeerIDLength)
	if err != nil {
		return DhtNode{}, err
	}
	if !isHex(upeerID) {
		return DhtNode{}, fmt.Errorf("invalid node.upeerId")
	}
	address, err := codecRequiredString(node, "address", MaxAddressLength)
	if err != nil {
		return DhtNode{}, fmt.Errorf("invalid node.address")
	}
	publicKey, _, err := optionalString(node, "publicKey", MaxNodeIDLength)
	if err != nil {
		return DhtNode{}, err
	}
	nodeID, _, err := optionalString(node, "nodeId", MaxNodeIDLength)
	if err != nil {
		return DhtNode{}, err
	}
	return DhtNode{UpeerID: upeerID, Address: address, PublicKey: publicKey, NodeID: nodeID}, nil
}

func optionalNeighbors(packet map[string]any, field string) ([]DhtNeighbor, error) {
	if !isPresent(packet, field) {
		return nil, nil
	}
	raw, ok := packet[field].([]any)
	if !ok {
		return nil, fmt.Errorf("invalid neighbors array")
	}
	if len(raw) > MaxResponseNeighbors {
		return nil, fmt.Errorf("too many neighbors")
	}
	neighbors := make([]DhtNeighbor, 0, len(raw))
	for _, item := range raw {
		neighbor, ok := item.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("invalid neighbor entry")
		}
		upeerID, _, err := optionalString(neighbor, "upeerId", MaxUpeerIDLength)
		if err != nil {
			return nil, err
		}
		address, _, err := optionalString(neighbor, "address", MaxAddressLength)
		if err != nil {
			return nil, err
		}
		publicKey, _, err := optionalString(neighbor, "publicKey", MaxNodeIDLength)
		if err != nil {
			return nil, err
		}
		block, err := optionalLocationBlock(neighbor, "locationBlock")
		if err != nil {
			return nil, err
		}
		neighbors = append(neighbors, DhtNeighbor{
			UpeerID: upeerID, Address: address, PublicKey: publicKey, LocationBlock: block,
		})
	}
	return neighbors, nil
}

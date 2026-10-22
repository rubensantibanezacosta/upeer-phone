package dht

import (
	"fmt"
	"sort"
)

var commonPacketFields = fieldSet(
	"type",
	"queryId",
	"senderUpeerId",
	"senderYggAddress",
	"isInternalSync",
	"signature",
	"id",
	"contactCache",
	"renewalToken",
)

var allowedPacketFields = map[string]map[string]struct{}{
	"DHT_PING":        withFields(commonPacketFields, "nodeId"),
	"DHT_PONG":        withFields(commonPacketFields, "nodeId"),
	"DHT_FIND_NODE":   withFields(commonPacketFields, "targetId"),
	"DHT_FOUND_NODES": withFields(commonPacketFields, "nodes"),
	"DHT_FIND_VALUE":  withFields(commonPacketFields, "key"),
	"DHT_FOUND_VALUE": withFields(commonPacketFields, "key", "value", "nodes", "publisher", "timestamp"),
	"DHT_STORE":       withFields(commonPacketFields, "key", "value", "publisher", "timestamp", "ttl"),
	"DHT_STORE_ACK":   withFields(commonPacketFields, "key"),
	"DHT_QUERY":       withFields(commonPacketFields, "targetId", "referralContext"),
	"DHT_RESPONSE":    withFields(commonPacketFields, "targetId", "publicKey", "locationBlock", "neighbors"),
	"DHT_UPDATE":      withFields(commonPacketFields, "locationBlock"),
	"DHT_EXCHANGE":    withFields(commonPacketFields, "peers"),
}

func DecodePacketJSON(packetJSON string) (DecodedPacket, error) {
	packet, err := decodePacketObject(packetJSON)
	if err != nil {
		return DecodedPacket{}, err
	}
	return DecodePacketObject(packet)
}

func DecodePacketObject(packet map[string]any) (DecodedPacket, error) {
	packetType, err := packetType(packet)
	if err != nil {
		return DecodedPacket{}, err
	}
	allowed := allowedPacketFields[packetType]
	unexpected := make([]string, 0)
	for key := range packet {
		if _, ok := allowed[key]; !ok {
			unexpected = append(unexpected, key)
		}
	}
	if len(unexpected) > 0 {
		sort.Strings(unexpected)
		return DecodedPacket{}, fmt.Errorf("unexpected fields: %s", joinFields(unexpected))
	}
	queryID, err := queryID(packet)
	if err != nil {
		return DecodedPacket{}, err
	}
	message, err := decodeMessage(packet, packetType, queryID)
	if err != nil {
		return DecodedPacket{}, err
	}
	return DecodedPacket{Type: packetType, QueryID: queryID, Message: message}, nil
}

func ValidatePacketPayloadJSON(payloadJSON string) error {
	_, err := DecodePacketJSON(payloadJSON)
	return err
}

func decodePacketObject(packetJSON string) (map[string]any, error) {
	if packetJSON == "" {
		return nil, fmt.Errorf("empty packet")
	}
	if len(packetJSON) > MaxPacketJSONBytes {
		return nil, fmt.Errorf("packet too large")
	}
	if err := guardPacketDepth(packetJSON); err != nil {
		return nil, err
	}
	packet, err := DecodeJSONObject(packetJSON)
	if err != nil {
		return nil, fmt.Errorf("invalid JSON: %w", err)
	}
	return packet, nil
}

func packetType(packet map[string]any) (string, error) {
	value, ok := packet["type"].(string)
	if !ok || value == "" {
		return "", fmt.Errorf("missing packet type")
	}
	if _, ok := allowedPacketFields[value]; !ok {
		return "", fmt.Errorf("unsupported packet type")
	}
	return value, nil
}

func queryID(packet map[string]any) (string, error) {
	value, exists := packet["queryId"]
	if !exists || value == nil {
		return "", nil
	}
	text, ok := value.(string)
	if !ok || text == "" || len(text) > MaxQueryIDLength {
		return "", fmt.Errorf("invalid queryId")
	}
	return text, nil
}

func guardPacketDepth(packetJSON string) error {
	depth := 0
	inString := false
	escaped := false
	for index := 0; index < len(packetJSON); index++ {
		character := packetJSON[index]
		if inString {
			switch {
			case escaped:
				escaped = false
			case character == '\\':
				escaped = true
			case character == '"':
				inString = false
			}
			continue
		}
		switch character {
		case '"':
			inString = true
		case '{', '[':
			depth++
			if depth > MaxPacketJSONDepth {
				return fmt.Errorf("JSON is too deeply nested")
			}
		case '}', ']':
			depth--
			if depth < 0 {
				return fmt.Errorf("invalid JSON nesting")
			}
		}
	}
	if inString || depth != 0 {
		return fmt.Errorf("truncated JSON")
	}
	return nil
}

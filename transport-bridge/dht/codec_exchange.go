package dht

import (
	"encoding/json"
	"fmt"
	"strings"
)

func requiredPeers(packet map[string]any, field string) ([]DhtExchangePeer, error) {
	raw, ok := packet[field].([]any)
	if !ok {
		return nil, fmt.Errorf("invalid peers array")
	}
	if len(raw) > MaxExchangePeers {
		return nil, fmt.Errorf("too many peers")
	}
	peers := make([]DhtExchangePeer, 0, len(raw))
	for _, item := range raw {
		peer, ok := item.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("invalid peer entry")
		}
		upeerID, err := codecRequiredString(peer, "upeerId", MaxUpeerIDLength)
		if err != nil || !validHexID(upeerID) {
			return nil, fmt.Errorf("invalid peer upeerId")
		}
		publicKey, err := codecRequiredString(peer, "publicKey", 64)
		if err != nil || len(publicKey) != 64 {
			return nil, fmt.Errorf("invalid peer publicKey")
		}
		block, err := optionalLocationBlock(peer, "locationBlock")
		if err != nil {
			return nil, err
		}
		peers = append(peers, DhtExchangePeer{UpeerID: upeerID, PublicKey: publicKey, LocationBlock: block})
	}
	return peers, nil
}

func requiredLocationBlock(packet map[string]any, field string) (map[string]any, error) {
	block, err := optionalLocationBlock(packet, field)
	if err != nil {
		return nil, err
	}
	if block == nil {
		return nil, fmt.Errorf("missing locationBlock")
	}
	return block, nil
}

func optionalLocationBlock(packet map[string]any, field string) (map[string]any, error) {
	if !isPresent(packet, field) {
		return nil, nil
	}
	block, ok := packet[field].(map[string]any)
	if !ok {
		return nil, fmt.Errorf("invalid locationBlock")
	}
	if err := validateLocationBlock(block); err != nil {
		return nil, err
	}
	return block, nil
}

func validateLocationBlock(block map[string]any) error {
	address, err := codecRequiredString(block, "address", MaxAddressLength)
	if err != nil || strings.TrimSpace(address) == "" {
		return fmt.Errorf("invalid locationBlock.address")
	}
	dhtSeq, err := requiredInteger(block, "dhtSeq")
	if err != nil || dhtSeq < 0 {
		return fmt.Errorf("invalid locationBlock.dhtSeq")
	}
	signature, err := codecRequiredString(block, "signature", 128)
	if err != nil || len(signature) != 128 {
		return fmt.Errorf("invalid locationBlock.signature")
	}
	if isPresent(block, "powProof") {
		if err := validatePowProof(block["powProof"]); err != nil {
			return err
		}
	}
	return nil
}

func validatePowProof(value any) error {
	proof, ok := value.(string)
	if !ok || len(proof) > MaxPowProofLength {
		return fmt.Errorf("invalid powProof")
	}
	if !strings.HasPrefix(proof, "{") && !isHex(proof) {
		return fmt.Errorf("invalid powProof")
	}
	return nil
}

func validateValueSize(value any) error {
	if text, ok := value.(string); ok {
		if len(text) > MaxStoredValueBytes {
			return fmt.Errorf("value too large")
		}
		return nil
	}
	encoded, err := json.Marshal(value)
	if err != nil || len(encoded) > MaxStoredValueBytes {
		return fmt.Errorf("value too large")
	}
	return nil
}

func validKey(value string) bool {
	return (len(value) == 40 || len(value) == 64) && isHex(value)
}

func validHexID(value string) bool {
	return len(value) >= MinUpeerIDLength && len(value) <= MaxUpeerIDLength && isHex(value)
}

func isHex(value string) bool {
	if value == "" {
		return false
	}
	for index := 0; index < len(value); index++ {
		character := value[index]
		if (character < '0' || character > '9') &&
			(character < 'a' || character > 'f') &&
			(character < 'A' || character > 'F') {
			return false
		}
	}
	return true
}

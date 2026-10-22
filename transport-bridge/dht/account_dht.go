package dht

import (
	"encoding/json"
	"fmt"
)

func (node *KademliaNode) PublishAccountRegistry(registryJSON string, nowMillis int64) (int, error) {
	registry, err := VerifyAccountRegistry(registryJSON, "", nowMillis)
	if err != nil {
		return 0, err
	}
	key, err := AccountRegistryKey(registry.AccountUpeerID)
	if err != nil {
		return 0, err
	}
	value, err := DecodeJSONObject(registryJSON)
	if err != nil {
		return 0, err
	}
	return node.StoreValue(key, value, registry.AccountUpeerID)
}

func (node *KademliaNode) FindAccountRegistry(accountUpeerID string, nowMillis int64) (AccountRegistry, bool, error) {
	key, err := AccountRegistryKey(accountUpeerID)
	if err != nil {
		return AccountRegistry{}, false, err
	}
	stored, found, err := node.FindValue(key)
	if err != nil {
		return AccountRegistry{}, false, err
	}
	if !found {
		return AccountRegistry{}, false, nil
	}
	encoded, err := json.Marshal(stored.Value)
	if err != nil {
		return AccountRegistry{}, false, fmt.Errorf("encode account registry value: %w", err)
	}
	registry, err := VerifyAccountRegistry(string(encoded), accountUpeerID, nowMillis)
	if err != nil {
		return AccountRegistry{}, false, err
	}
	return registry, true, nil
}

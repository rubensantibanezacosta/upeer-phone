package transportbridge

import (
	"encoding/json"
	"fmt"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func (identity *CryptoIdentity) BuildLocationBlock(addressesJSON string, dhtSeq, ttlMillis int64, deviceID string) (string, error) {
	var addresses []string
	if err := json.Unmarshal([]byte(addressesJSON), &addresses); err != nil {
		return "", fmt.Errorf("decode location block addresses: %w", err)
	}
	secretKey, err := identity.requireSecretKey("build location block")
	if err != nil {
		return "", err
	}
	return dht.BuildLocationBlock(secretKey, dht.LocationBlockOptions{
		Addresses: addresses,
		DhtSeq:    dhtSeq,
		TTLMillis: ttlMillis,
		DeviceID:  deviceID,
	})
}

func VerifyLocationBlock(blockJSON, publicKeyHex string, nowMillis int64) (bool, error) {
	return dht.VerifyLocationBlock(blockJSON, publicKeyHex, nowMillis)
}

func DeriveDeviceID(deviceKey []byte) (string, error) {
	return dht.DeriveDeviceID(deviceKey)
}

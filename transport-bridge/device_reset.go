package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strings"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func (identity *CryptoIdentity) BuildLinkedDrReset(
	accountUpeerID string,
	senderYggAddress string,
	signedPreKeyJSON string,
	ratchetContext string,
	deviceID string,
) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	if !validHex(accountUpeerID, 32) || !validHex(deviceID, 32) {
		return "", errors.New("linked reset identity is invalid")
	}
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(identity.publicKey)), deviceID) {
		return "", errors.New("linked reset device id does not match the device key")
	}
	data, err := identity.buildDrResetData(signedPreKeyJSON, ratchetContext)
	if err != nil {
		return "", err
	}
	data["senderDeviceId"] = strings.ToLower(deviceID)
	encoded, err := json.Marshal(data)
	if err != nil {
		return "", fmt.Errorf("encode linked reset data: %w", err)
	}
	return dht.BuildLinkedSignedPacket(identity.secretKey, accountUpeerID, senderYggAddress, string(encoded))
}

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

func (identity *CryptoIdentity) BuildLinkedControl(
	deviceID string,
	accountUpeerID string,
	senderYggAddress string,
	packetJSON string,
) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	if !validHex(accountUpeerID, 32) || !validHex(deviceID, 32) {
		return "", errors.New("linked control identity is invalid")
	}
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(identity.publicKey)), deviceID) {
		return "", errors.New("linked control device id does not match the device key")
	}
	var packet map[string]any
	if err := json.Unmarshal([]byte(packetJSON), &packet); err != nil {
		return "", fmt.Errorf("decode control packet: %w", err)
	}
	if _, err := requiredString(packet, "type"); err != nil {
		return "", err
	}
	for _, reserved := range []string{"signature", "senderUpeerId", "senderYggAddress"} {
		if _, exists := packet[reserved]; exists {
			return "", fmt.Errorf("linked control packet must not provide %s", reserved)
		}
	}
	normalizedDeviceID := strings.ToLower(deviceID)
	packet["deviceId"] = normalizedDeviceID
	packet["senderDeviceId"] = normalizedDeviceID
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode control packet: %w", err)
	}
	return dht.BuildLinkedSignedPacket(identity.secretKey, accountUpeerID, senderYggAddress, string(encoded))
}

func (identity *CryptoIdentity) BuildLinkedAck(
	deviceID string,
	accountUpeerID string,
	senderYggAddress string,
	messageID string,
	status string,
) (string, error) {
	if messageID == "" {
		return "", errors.New("ACK message ID must not be empty")
	}
	if status != "delivered" && status != "read" {
		return "", errors.New("ACK status must be delivered or read")
	}
	encoded, err := json.Marshal(map[string]any{
		"type":           "ACK",
		"id":             messageID,
		"status":         status,
		"isInternalSync": false,
	})
	if err != nil {
		return "", fmt.Errorf("encode ACK data: %w", err)
	}
	return identity.BuildLinkedControl(deviceID, accountUpeerID, senderYggAddress, string(encoded))
}

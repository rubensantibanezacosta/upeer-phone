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

func (identity *CryptoIdentity) BuildLinkedChat(
	accountUpeerID string,
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	messageID string,
	replyTo string,
	peerSPKID int,
	timestamp int64,
	deviceID string,
) (string, error) {
	return identity.buildLinkedChatPacket(
		"CHAT",
		"id",
		messageID,
		replyTo,
		0,
		accountUpeerID,
		senderYggAddress,
		peerPublicKeyHex,
		peerSPKPublicKeyHex,
		sessionJSON,
		plaintext,
		peerSPKID,
		timestamp,
		deviceID,
	)
}

func (identity *CryptoIdentity) BuildLinkedChatUpdate(
	accountUpeerID string,
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	messageID string,
	version int,
	peerSPKID int,
	timestamp int64,
	deviceID string,
) (string, error) {
	if version <= 0 {
		return "", errors.New("CHAT_UPDATE version must be positive")
	}
	return identity.buildLinkedChatPacket(
		"CHAT_UPDATE",
		"msgId",
		messageID,
		"",
		version,
		accountUpeerID,
		senderYggAddress,
		peerPublicKeyHex,
		peerSPKPublicKeyHex,
		sessionJSON,
		plaintext,
		peerSPKID,
		timestamp,
		deviceID,
	)
}

func (identity *CryptoIdentity) buildLinkedChatPacket(
	packetType string,
	idField string,
	messageID string,
	replyTo string,
	version int,
	accountUpeerID string,
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	peerSPKID int,
	timestamp int64,
	deviceID string,
) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	if !validHex(accountUpeerID, 32) || !validHex(deviceID, 32) {
		return "", errors.New("linked chat identity is invalid")
	}
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(identity.publicKey)), deviceID) {
		return "", errors.New("linked chat device id does not match the device key")
	}
	data, encrypted, err := identity.buildChatData(
		packetType,
		idField,
		messageID,
		replyTo,
		version,
		senderYggAddress,
		peerPublicKeyHex,
		peerSPKPublicKeyHex,
		sessionJSON,
		plaintext,
		peerSPKID,
		timestamp,
	)
	if err != nil {
		return "", err
	}
	data["deviceId"] = strings.ToLower(deviceID)
	data["senderDeviceId"] = strings.ToLower(deviceID)
	encoded, err := json.Marshal(data)
	if err != nil {
		return "", fmt.Errorf("encode linked chat data: %w", err)
	}
	signedPacket, err := dht.BuildLinkedSignedPacket(identity.secretKey, accountUpeerID, senderYggAddress, string(encoded))
	if err != nil {
		return "", err
	}
	return identity.sealOutboundChat(signedPacket, peerPublicKeyHex, encrypted.Session)
}

package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strings"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func (identity *CryptoIdentity) BuildLinkedHandshakeRequest(accountPublicKeyHex, accountUpeerID, senderYggAddress, alias, avatar, deviceJSON string) (string, error) {
	return identity.buildLinkedHandshakePacket(handshakeRequest, accountPublicKeyHex, accountUpeerID, senderYggAddress, alias, avatar, deviceJSON)
}

func (identity *CryptoIdentity) BuildLinkedHandshakeAccept(accountPublicKeyHex, accountUpeerID, senderYggAddress, alias, avatar, deviceJSON string) (string, error) {
	return identity.buildLinkedHandshakePacket(handshakeAccept, accountPublicKeyHex, accountUpeerID, senderYggAddress, alias, avatar, deviceJSON)
}

func (identity *CryptoIdentity) buildLinkedHandshakePacket(packetType, accountPublicKeyHex, accountUpeerID, senderYggAddress, alias, avatar, deviceJSON string) (string, error) {
	if identity == nil || len(identity.secretKey) != ed25519.PrivateKeySize {
		return "", errors.New("linked handshake requires the device secret key")
	}
	accountPublicKeyHex = strings.ToLower(strings.TrimSpace(accountPublicKeyHex))
	accountUpeerID = strings.ToLower(strings.TrimSpace(accountUpeerID))
	accountPublicKey, err := hex.DecodeString(accountPublicKeyHex)
	if err != nil || len(accountPublicKey) != ed25519.PublicKeySize {
		return "", errors.New("account public key must be a 32-byte hexadecimal Ed25519 key")
	}
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(accountPublicKey)), accountUpeerID) {
		return "", errors.New("account upeerId does not match the account public key")
	}
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	device, err := parseDeviceHandshakeFieldsFor(accountUpeerID, deviceJSON)
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(device.DevicePublicKey, identity.PublicKeyHex()) {
		return "", errors.New("device handshake fields do not match the signing device key")
	}
	alias, err = validatedAlias(alias)
	if err != nil {
		return "", err
	}
	avatarValue, err := validatedAvatar(avatar)
	if err != nil {
		return "", err
	}
	data := map[string]any{
		"type":           packetType,
		"publicKey":      accountPublicKeyHex,
		"isInternalSync": false,
	}
	if alias != "" {
		data["alias"] = alias
	}
	if avatarValue != "" {
		data["avatar"] = avatarValue
	}
	addDeviceHandshakeFields(data, device)
	encoded, err := json.Marshal(data)
	if err != nil {
		return "", fmt.Errorf("encode linked handshake: %w", err)
	}
	signed, err := dht.BuildLinkedSignedPacket(identity.secretKey, accountUpeerID, senderYggAddress, string(encoded))
	if err != nil {
		return "", fmt.Errorf("sign linked handshake: %w", err)
	}
	return signed, nil
}

func validateControlFrameWithDeviceSignature(payload []byte) (string, error) {
	metadata, rootErr := validateControlFrameFromPayload(payload)
	if rootErr == nil {
		return metadata, nil
	}
	packet, err := decodeControlPacket(payload)
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	packetType, err := requiredString(packet, "type")
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	if packetType != handshakeRequest && packetType != handshakeAccept {
		return "", fmt.Errorf("unsupported control packet type %q", packetType)
	}
	accountPublicKey, err := requiredHex(packet, "publicKey", ed25519.PublicKeySize)
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	senderUpeerID, err := requiredString(packet, "senderUpeerId")
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	senderUpeerID = strings.ToLower(senderUpeerID)
	if !validHex(senderUpeerID, 32) {
		return "", errors.New("senderUpeerId must be a 32-byte hexadecimal string")
	}
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(accountPublicKey)), senderUpeerID) {
		return "", errors.New("senderUpeerId does not match publicKey")
	}
	senderYggAddress, err := requiredString(packet, "senderYggAddress")
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	signature, err := requiredHex(packet, "signature", ed25519.SignatureSize)
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	device, err := parseDeviceHandshakeFieldsFor(senderUpeerID, string(payload))
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	devicePublicKey, err := hex.DecodeString(device.DevicePublicKey)
	if err != nil {
		return "", fmt.Errorf("%v; %w", rootErr, err)
	}
	if !verifyControlSignature(devicePublicKey, signature, signedControlPayload(packet), senderUpeerID, senderYggAddress, packetType) {
		return "", errors.New("invalid control packet signature")
	}
	return controlMetadataJSON(packetType, senderUpeerID, senderYggAddress, accountPublicKey)
}

func signedControlPayload(packet map[string]any) map[string]any {
	signedPayload := make(map[string]any, len(packet))
	for key, value := range packet {
		if key == "signature" || key == "senderUpeerId" || key == "senderYggAddress" {
			continue
		}
		signedPayload[key] = value
	}
	return signedPayload
}

func controlMetadataJSON(packetType, senderUpeerID, senderYggAddress string, publicKey []byte) (string, error) {
	metadata, err := json.Marshal(controlMetadata{
		Type:             packetType,
		SenderUpeerID:    senderUpeerID,
		SenderYggAddress: senderYggAddress,
		PublicKey:        hex.EncodeToString(publicKey),
	})
	if err != nil {
		return "", fmt.Errorf("encode validated control packet: %w", err)
	}
	return string(metadata), nil
}

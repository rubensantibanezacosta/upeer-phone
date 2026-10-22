package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

func BuildLinkedSignedPacket(secretKey []byte, senderUpeerID, senderYggAddress, packetJSON string) (string, error) {
	privateKey, err := parsePrivateKey(secretKey)
	if err != nil {
		return "", err
	}
	packet, err := DecodeJSONObject(packetJSON)
	if err != nil {
		return "", err
	}
	return buildLinkedSignedPacketObject(privateKey, senderUpeerID, senderYggAddress, packet)
}

func buildLinkedSignedPacketObject(privateKey ed25519.PrivateKey, senderUpeerID, senderYggAddress string, data map[string]any) (string, error) {
	if !validHex(senderUpeerID, 32) {
		return "", errors.New("senderUpeerId must be a 32-byte hexadecimal string")
	}
	if !isYggdrasilAddress(senderYggAddress) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	packet := clonePacket(data)
	if _, err := requiredString(packet, "type"); err != nil {
		return "", err
	}
	for _, reserved := range []string{"signature", "senderUpeerId", "senderYggAddress"} {
		if _, exists := packet[reserved]; exists {
			return "", fmt.Errorf("signed packet must not provide %s", reserved)
		}
	}
	packet["senderUpeerId"] = strings.ToLower(senderUpeerID)
	packet["senderYggAddress"] = senderYggAddress
	if _, exists := packet["isInternalSync"]; !exists {
		packet["isInternalSync"] = false
	}
	signedPayload := clonePacket(packet)
	delete(signedPayload, "signature")
	delete(signedPayload, "contactCache")
	delete(signedPayload, "renewalToken")
	canonical, err := CanonicalStringify(signedPayload)
	if err != nil {
		return "", fmt.Errorf("canonicalize linked signed packet: %w", err)
	}
	packet["signature"] = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode linked signed packet: %w", err)
	}
	return string(encoded), nil
}

func VerifyLinkedSignedPacket(packetJSON, publicKeyHex, expectedSenderUpeerID string) (string, error) {
	publicKey, err := decodeHex(publicKeyHex, ed25519.PublicKeySize)
	if err != nil {
		return "", errors.New("signed packet public key must be a 32-byte hexadecimal Ed25519 key")
	}
	expectedSender := strings.ToLower(expectedSenderUpeerID)
	if !validHex(expectedSender, 32) {
		return "", errors.New("expected senderUpeerId must be a 32-byte hexadecimal string")
	}
	packet, err := DecodeJSONObject(packetJSON)
	if err != nil {
		return "", err
	}
	packetType, err := requiredString(packet, "type")
	if err != nil {
		return "", err
	}
	senderUpeerID, err := requiredString(packet, "senderUpeerId")
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(senderUpeerID, expectedSender) {
		return "", errors.New("senderUpeerId does not match the linked account")
	}
	senderYggAddress, err := requiredString(packet, "senderYggAddress")
	if err != nil {
		return "", err
	}
	if !isYggdrasilAddress(senderYggAddress) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	signature, err := requiredHex(packet, "signature", ed25519.SignatureSize)
	if err != nil {
		return "", err
	}
	payloadForVerification := make(map[string]any, len(packet))
	for key, value := range packet {
		if key == "signature" || key == "senderUpeerId" || key == "senderYggAddress" || key == "contactCache" || key == "renewalToken" {
			continue
		}
		payloadForVerification[key] = value
	}
	payloadForVerification["senderUpeerId"] = senderUpeerID
	payloadForVerification["senderYggAddress"] = senderYggAddress
	canonical, err := CanonicalStringify(payloadForVerification)
	if err != nil {
		return "", err
	}
	if !ed25519.Verify(publicKey, []byte(canonical), signature) {
		legacy := make(map[string]any, len(payloadForVerification))
		for key, value := range payloadForVerification {
			if key != "senderYggAddress" {
				legacy[key] = value
			}
		}
		legacyCanonical, legacyErr := CanonicalStringify(legacy)
		if legacyErr != nil || !ed25519.Verify(publicKey, []byte(legacyCanonical), signature) {
			return "", errors.New("invalid linked signed packet signature")
		}
	}
	var id string
	if value, ok := packet["id"].(string); ok {
		id = value
	}
	metadata, err := json.Marshal(SignedPacketMetadata{
		Type:             packetType,
		SenderUpeerID:    expectedSender,
		SenderYggAddress: senderYggAddress,
		PublicKey:        hex.EncodeToString(publicKey),
		ID:               id,
	})
	if err != nil {
		return "", fmt.Errorf("encode linked signed packet metadata: %w", err)
	}
	return string(metadata), nil
}

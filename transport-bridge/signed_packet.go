package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strings"
)

type signedPacketMetadata struct {
	Type             string `json:"type"`
	SenderUpeerID    string `json:"senderUpeerId"`
	SenderYggAddress string `json:"senderYggAddress"`
	PublicKey        string `json:"publicKey"`
	ID               string `json:"id,omitempty"`
}

func (b *Bridge) ValidateSignedPacket(payload []byte, publicKeyHex string) (string, error) {
	publicKey, err := hex.DecodeString(publicKeyHex)
	if err != nil || len(publicKey) != ed25519.PublicKeySize {
		return "", errors.New("signed packet public key must be a 32-byte hexadecimal Ed25519 key")
	}
	packet, err := decodeControlPacket(payload)
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
	if !validHex(senderUpeerID, 32) || !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(publicKey)), senderUpeerID) {
		return "", errors.New("senderUpeerId does not match the peer public key")
	}
	senderYggAddress, err := requiredString(packet, "senderYggAddress")
	if err != nil {
		return "", err
	}
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
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
	canonical, err := canonicalStringify(payloadForVerification)
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
		legacyCanonical, legacyErr := canonicalStringify(legacy)
		if legacyErr != nil || !ed25519.Verify(publicKey, []byte(legacyCanonical), signature) {
			return "", errors.New("invalid signed packet signature")
		}
	}
	var id string
	if value, ok := packet["id"].(string); ok {
		id = value
	}
	metadata, err := json.Marshal(signedPacketMetadata{
		Type:             packetType,
		SenderUpeerID:    strings.ToLower(senderUpeerID),
		SenderYggAddress: senderYggAddress,
		PublicKey:        hex.EncodeToString(publicKey),
		ID:               id,
	})
	if err != nil {
		return "", fmt.Errorf("encode signed packet metadata: %w", err)
	}
	return string(metadata), nil
}

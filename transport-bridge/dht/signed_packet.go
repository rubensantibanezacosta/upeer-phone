package dht

import (
	"bytes"
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/netip"
	"strings"

	"golang.org/x/crypto/blake2b"
)

type SignedPacketMetadata struct {
	Type             string `json:"type"`
	SenderUpeerID    string `json:"senderUpeerId"`
	SenderYggAddress string `json:"senderYggAddress"`
	PublicKey        string `json:"publicKey"`
	ID               string `json:"id,omitempty"`
}

func SignCanonicalJSON(secretKey []byte, payloadJSON string) (string, error) {
	privateKey, err := parsePrivateKey(secretKey)
	if err != nil {
		return "", err
	}
	payload, err := DecodeJSONObject(payloadJSON)
	if err != nil {
		return "", err
	}
	canonical, err := CanonicalStringify(payload)
	if err != nil {
		return "", fmt.Errorf("canonicalize signature payload: %w", err)
	}
	return hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical))), nil
}

func BuildSignedPacket(secretKey []byte, senderYggAddress, packetJSON string) (string, error) {
	privateKey, err := parsePrivateKey(secretKey)
	if err != nil {
		return "", err
	}
	packet, err := DecodeJSONObject(packetJSON)
	if err != nil {
		return "", err
	}
	return buildSignedPacketObject(privateKey, senderYggAddress, packet)
}

func BuildSignedPacketFromObject(secretKey []byte, senderYggAddress string, data map[string]any) (string, error) {
	privateKey, err := parsePrivateKey(secretKey)
	if err != nil {
		return "", err
	}
	return buildSignedPacketObject(privateKey, senderYggAddress, data)
}

func buildSignedPacketObject(privateKey ed25519.PrivateKey, senderYggAddress string, data map[string]any) (string, error) {
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
	publicKey := privateKey.Public().(ed25519.PublicKey)
	packet["senderUpeerId"] = upeerIDFromPublicKey(publicKey)
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
		return "", fmt.Errorf("canonicalize signed packet: %w", err)
	}
	packet["signature"] = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode signed packet: %w", err)
	}
	return string(encoded), nil
}

func VerifyCanonicalJSON(payloadJSON, signatureHex, publicKeyHex string) (bool, error) {
	payload, err := DecodeJSONObject(payloadJSON)
	if err != nil {
		return false, err
	}
	signature, err := decodeHex(signatureHex, ed25519.SignatureSize)
	if err != nil {
		return false, errors.New("signature must be a 64-byte hexadecimal value")
	}
	publicKey, err := decodeHex(publicKeyHex, ed25519.PublicKeySize)
	if err != nil {
		return false, errors.New("public key must be a 32-byte hexadecimal value")
	}
	canonical, err := CanonicalStringify(payload)
	if err != nil {
		return false, fmt.Errorf("canonicalize verification payload: %w", err)
	}
	return ed25519.Verify(publicKey, []byte(canonical), signature), nil
}

func VerifySignedPacket(packetJSON, publicKeyHex string) (string, error) {
	publicKey, err := decodeHex(publicKeyHex, ed25519.PublicKeySize)
	if err != nil {
		return "", errors.New("signed packet public key must be a 32-byte hexadecimal Ed25519 key")
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
	expectedUpeerID := upeerIDFromPublicKey(publicKey)
	if !validHex(senderUpeerID, 32) || !strings.EqualFold(expectedUpeerID, senderUpeerID) {
		return "", errors.New("senderUpeerId does not match the peer public key")
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
			return "", errors.New("invalid signed packet signature")
		}
	}
	var id string
	if value, ok := packet["id"].(string); ok {
		id = value
	}
	metadata, err := json.Marshal(SignedPacketMetadata{
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

func parsePrivateKey(secretKey []byte) (ed25519.PrivateKey, error) {
	if len(secretKey) != ed25519.PrivateKeySize {
		return nil, errors.New("Ed25519 secret key must be 64 bytes")
	}
	derived := ed25519.NewKeyFromSeed(secretKey[:ed25519.SeedSize])
	if !bytes.Equal(derived, secretKey) {
		return nil, errors.New("Ed25519 secret key does not match its public key")
	}
	return append(ed25519.PrivateKey(nil), secretKey...), nil
}

func clonePacket(packet map[string]any) map[string]any {
	clone := make(map[string]any, len(packet))
	for key, value := range packet {
		clone[key] = value
	}
	return clone
}

func requiredString(packet map[string]any, key string) (string, error) {
	value, ok := packet[key]
	if !ok {
		return "", fmt.Errorf("signed packet is missing %s", key)
	}
	text, ok := value.(string)
	if !ok || text == "" {
		return "", fmt.Errorf("signed packet field %s must be a non-empty string", key)
	}
	return text, nil
}

func requiredHex(packet map[string]any, key string, expectedBytes int) ([]byte, error) {
	text, err := requiredString(packet, key)
	if err != nil {
		return nil, err
	}
	decoded, err := decodeHex(text, expectedBytes)
	if err != nil {
		return nil, fmt.Errorf("signed packet field %s must be a %d-byte hexadecimal string", key, expectedBytes)
	}
	return decoded, nil
}

func decodeHex(value string, expectedBytes int) ([]byte, error) {
	if !validHex(value, expectedBytes) {
		return nil, errors.New("invalid hexadecimal value")
	}
	return hex.DecodeString(value)
}

func validHex(value string, expectedBytes int) bool {
	if len(value) != expectedBytes*2 {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func upeerIDFromPublicKey(publicKey ed25519.PublicKey) string {
	return hex.EncodeToString(upeerIDBytes(publicKey))
}

func upeerIDBytes(publicKey []byte) []byte {
	hash, err := blake2b.New(32, nil)
	if err != nil {
		return nil
	}
	if _, err := hash.Write(publicKey); err != nil {
		return nil
	}
	return hash.Sum(nil)
}

func isYggdrasilAddress(value string) bool {
	address, err := netip.ParseAddr(value)
	if err != nil || !address.Is6() {
		return false
	}
	bytes := address.As16()
	return bytes[0]&0xfe == 0x02
}

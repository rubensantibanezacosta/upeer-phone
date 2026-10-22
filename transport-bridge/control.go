package transportbridge

import (
	"bytes"
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"strings"

	"github.com/upeer/upeer/transport-bridge/dht"
	"golang.org/x/crypto/blake2b"
)

const (
	handshakeRequest = "HANDSHAKE_REQ"
	handshakeAccept  = "HANDSHAKE_ACCEPT"
)

type controlMetadata struct {
	Type             string `json:"type"`
	SenderUpeerID    string `json:"senderUpeerId"`
	SenderYggAddress string `json:"senderYggAddress"`
	PublicKey        string `json:"publicKey"`
}

func validateControlFrameFromPayload(payload []byte) (string, error) {
	packet, err := decodeControlPacket(payload)
	if err != nil {
		return "", err
	}
	packetType, err := requiredString(packet, "type")
	if err != nil {
		return "", err
	}
	if packetType != handshakeRequest && packetType != handshakeAccept {
		return "", fmt.Errorf("unsupported control packet type %q", packetType)
	}
	publicKey, err := requiredHex(packet, "publicKey", ed25519.PublicKeySize)
	if err != nil {
		return "", err
	}
	senderUpeerID, err := requiredString(packet, "senderUpeerId")
	if err != nil {
		return "", err
	}
	if !validHex(senderUpeerID, 32) {
		return "", errors.New("senderUpeerId must be a 32-byte hexadecimal string")
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
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(publicKey)), senderUpeerID) {
		return "", errors.New("senderUpeerId does not match publicKey")
	}
	signedPayload := make(map[string]any, len(packet))
	for key, value := range packet {
		if key == "signature" || key == "senderUpeerId" || key == "senderYggAddress" {
			continue
		}
		signedPayload[key] = value
	}
	if packetType == handshakeRequest {
		delete(signedPayload, "contactCache")
		delete(signedPayload, "renewalToken")
	}
	if !verifyControlSignature(publicKey, signature, signedPayload, senderUpeerID, senderYggAddress, packetType) {
		return "", errors.New("invalid control packet signature")
	}
	metadata, err := json.Marshal(controlMetadata{
		Type:             packetType,
		SenderUpeerID:    strings.ToLower(senderUpeerID),
		SenderYggAddress: senderYggAddress,
		PublicKey:        hex.EncodeToString(publicKey),
	})
	if err != nil {
		return "", fmt.Errorf("encode validated control packet: %w", err)
	}
	return string(metadata), nil
}

func decodeControlPacket(payload []byte) (map[string]any, error) {
	decoder := json.NewDecoder(bytes.NewReader(payload))
	decoder.UseNumber()
	var packet map[string]any
	if err := decoder.Decode(&packet); err != nil {
		return nil, fmt.Errorf("decode control packet JSON: %w", err)
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err != nil {
			return nil, fmt.Errorf("decode control packet trailing data: %w", err)
		}
		return nil, errors.New("control packet contains trailing JSON data")
	}
	if packet == nil {
		return nil, errors.New("control packet must be a JSON object")
	}
	return packet, nil
}

func verifyControlSignature(publicKey, signature []byte, payload map[string]any, senderUpeerID, senderYggAddress, packetType string) bool {
	candidates := make([]map[string]any, 0, 3)
	withAddress := clonePacket(payload)
	withAddress["senderUpeerId"] = senderUpeerID
	withAddress["senderYggAddress"] = senderYggAddress
	candidates = append(candidates, withAddress)
	withoutAddress := clonePacket(payload)
	withoutAddress["senderUpeerId"] = senderUpeerID
	candidates = append(candidates, withoutAddress)
	if packetType == handshakeAccept {
		candidates = append(candidates, clonePacket(payload))
	}
	for _, candidate := range candidates {
		canonical, err := canonicalStringify(candidate)
		if err != nil {
			continue
		}
		if ed25519.Verify(publicKey, []byte(canonical), signature) {
			return true
		}
	}
	return false
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
		return "", fmt.Errorf("control packet is missing %s", key)
	}
	text, ok := value.(string)
	if !ok || text == "" {
		return "", fmt.Errorf("control packet field %s must be a non-empty string", key)
	}
	return text, nil
}

func requiredHex(packet map[string]any, key string, expectedBytes int) ([]byte, error) {
	text, err := requiredString(packet, key)
	if err != nil {
		return nil, err
	}
	if !validHex(text, expectedBytes) {
		return nil, fmt.Errorf("control packet field %s must be a %d-byte hexadecimal string", key, expectedBytes)
	}
	decoded, err := hex.DecodeString(text)
	if err != nil {
		return nil, fmt.Errorf("decode control packet field %s: %w", key, err)
	}
	return decoded, nil
}

func validHex(value string, expectedBytes int) bool {
	if len(value) != expectedBytes*2 {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func upeerIDFromPublicKey(publicKey []byte) []byte {
	hash, err := blake2b.New(32, nil)
	if err != nil {
		return nil
	}
	if _, err := hash.Write(publicKey); err != nil {
		return nil
	}
	return hash.Sum(nil)
}

func canonicalStringify(value any) (string, error) {
	return dht.CanonicalStringify(value)
}

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
)

func decodeJSONObject(payloadJSON string) (map[string]any, error) {
	decoder := json.NewDecoder(bytes.NewBufferString(payloadJSON))
	decoder.UseNumber()
	var payload map[string]any
	if err := decoder.Decode(&payload); err != nil {
		return nil, fmt.Errorf("decode canonical JSON: %w", err)
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err != nil {
			return nil, fmt.Errorf("decode canonical JSON trailing data: %w", err)
		}
		return nil, errors.New("canonical JSON contains trailing data")
	}
	if payload == nil {
		return nil, errors.New("canonical JSON must be an object")
	}
	return payload, nil
}

func (identity *CryptoIdentity) SignCanonicalJSON(payloadJSON string) (string, error) {
	payload, err := decodeJSONObject(payloadJSON)
	if err != nil {
		return "", err
	}
	canonical, err := canonicalStringify(payload)
	if err != nil {
		return "", fmt.Errorf("canonicalize signature payload: %w", err)
	}
	return identity.Sign([]byte(canonical)), nil
}

func (identity *CryptoIdentity) BuildSignedPacket(senderYggAddress, packetJSON string) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	packet, err := decodeJSONObject(packetJSON)
	if err != nil {
		return "", err
	}
	if _, err := requiredString(packet, "type"); err != nil {
		return "", err
	}
	for _, reserved := range []string{"signature", "senderUpeerId", "senderYggAddress"} {
		if _, exists := packet[reserved]; exists {
			return "", fmt.Errorf("signed packet must not provide %s", reserved)
		}
	}
	packet["senderUpeerId"] = identity.UpeerID()
	packet["senderYggAddress"] = senderYggAddress
	if _, exists := packet["isInternalSync"]; !exists {
		packet["isInternalSync"] = false
	}
	signedPayload := clonePacket(packet)
	delete(signedPayload, "signature")
	delete(signedPayload, "contactCache")
	delete(signedPayload, "renewalToken")
	canonical, err := canonicalStringify(signedPayload)
	if err != nil {
		return "", fmt.Errorf("canonicalize signed packet: %w", err)
	}
	packet["signature"] = identity.Sign([]byte(canonical))
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode signed packet: %w", err)
	}
	return string(encoded), nil
}

func VerifyCanonicalJSON(payloadJSON, signatureHex, publicKeyHex string) (bool, error) {
	payload, err := decodeJSONObject(payloadJSON)
	if err != nil {
		return false, err
	}
	signature, err := hex.DecodeString(signatureHex)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return false, errors.New("signature must be a 64-byte hexadecimal value")
	}
	publicKey, err := hex.DecodeString(publicKeyHex)
	if err != nil || len(publicKey) != ed25519.PublicKeySize {
		return false, errors.New("public key must be a 32-byte hexadecimal value")
	}
	canonical, err := canonicalStringify(payload)
	if err != nil {
		return false, fmt.Errorf("canonicalize verification payload: %w", err)
	}
	return ed25519.Verify(publicKey, []byte(canonical), signature), nil
}

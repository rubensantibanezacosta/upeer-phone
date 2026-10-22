package transportbridge

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"

	"golang.org/x/crypto/nacl/box"
)

type signedPreKeyState struct {
	SPKPub string `json:"spkPub"`
	SPKSig string `json:"spkSig"`
	SPKID  int    `json:"spkId"`
	SPKSk  string `json:"spkSk"`
}

func (identity *CryptoIdentity) CreateSignedPreKey(spkID int) (string, error) {
	if spkID <= 0 {
		return "", errors.New("signed pre-key ID must be positive")
	}
	publicKey, secretKey, err := box.GenerateKey(rand.Reader)
	if err != nil {
		return "", fmt.Errorf("generate signed pre-key: %w", err)
	}
	signature := ed25519.Sign(identity.secretKey, publicKey[:])
	encoded, err := json.Marshal(signedPreKeyState{
		SPKPub: hex.EncodeToString(publicKey[:]),
		SPKSig: hex.EncodeToString(signature),
		SPKID:  spkID,
		SPKSk:  hex.EncodeToString(secretKey[:]),
	})
	if err != nil {
		return "", fmt.Errorf("encode signed pre-key: %w", err)
	}
	return string(encoded), nil
}

func (identity *CryptoIdentity) BuildHandshakeAccept(senderYggAddress, signedPreKeyJSON, alias string) (string, error) {
	return identity.buildHandshakeAccept(senderYggAddress, signedPreKeyJSON, alias, nil)
}

func (identity *CryptoIdentity) BuildAck(senderYggAddress, messageID, status string) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	if messageID == "" {
		return "", errors.New("ACK message ID must not be empty")
	}
	if status != "delivered" && status != "read" {
		return "", errors.New("ACK status must be delivered or read")
	}
	data := map[string]any{
		"type":           "ACK",
		"id":             messageID,
		"status":         status,
		"isInternalSync": false,
	}
	payload := cloneStringMap(data)
	payload["senderUpeerId"] = identity.UpeerID()
	payload["senderYggAddress"] = senderYggAddress
	canonical, err := canonicalStringify(payload)
	if err != nil {
		return "", fmt.Errorf("canonicalize ACK: %w", err)
	}
	packet := cloneStringMap(data)
	packet["senderUpeerId"] = identity.UpeerID()
	packet["senderYggAddress"] = senderYggAddress
	packet["signature"] = hex.EncodeToString(ed25519.Sign(identity.secretKey, []byte(canonical)))
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode ACK: %w", err)
	}
	return string(encoded), nil
}

func cloneStringMap(source map[string]any) map[string]any {
	result := make(map[string]any, len(source))
	for key, value := range source {
		result[key] = value
	}
	return result
}

package transportbridge

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"

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
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	var signedPreKey signedPreKeyState
	if err := json.Unmarshal([]byte(signedPreKeyJSON), &signedPreKey); err != nil {
		return "", fmt.Errorf("decode signed pre-key: %w", err)
	}
	if signedPreKey.SPKID <= 0 || !validHex(signedPreKey.SPKPub, 32) || !validHex(signedPreKey.SPKSig, ed25519.SignatureSize) {
		return "", errors.New("signed pre-key state is invalid")
	}
	spkPublicKey, err := hex.DecodeString(signedPreKey.SPKPub)
	if err != nil {
		return "", fmt.Errorf("decode signed pre-key public key: %w", err)
	}
	spkSignature, err := hex.DecodeString(signedPreKey.SPKSig)
	if err != nil {
		return "", fmt.Errorf("decode signed pre-key signature: %w", err)
	}
	if !ed25519.Verify(identity.publicKey, spkPublicKey, spkSignature) {
		return "", errors.New("signed pre-key signature does not match identity")
	}
	alias = strings.TrimSpace(alias)
	if len(alias) > 100 {
		return "", errors.New("alias must be at most 100 characters")
	}
	senderUpeerID := identity.UpeerID()
	data := map[string]any{
		"type":           handshakeAccept,
		"publicKey":      identity.PublicKeyHex(),
		"isInternalSync": false,
		"signedPreKey": map[string]any{
			"spkPub": signedPreKey.SPKPub,
			"spkSig": signedPreKey.SPKSig,
			"spkId":  json.Number(strconv.Itoa(signedPreKey.SPKID)),
		},
	}
	if alias != "" {
		data["alias"] = alias
	}
	payload := cloneStringMap(data)
	payload["senderUpeerId"] = senderUpeerID
	payload["senderYggAddress"] = senderYggAddress
	canonical, err := canonicalStringify(payload)
	if err != nil {
		return "", fmt.Errorf("canonicalize handshake accept: %w", err)
	}
	signature := hex.EncodeToString(ed25519.Sign(identity.secretKey, []byte(canonical)))
	packet := cloneStringMap(data)
	packet["senderUpeerId"] = senderUpeerID
	packet["senderYggAddress"] = senderYggAddress
	packet["signature"] = signature
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode handshake accept: %w", err)
	}
	return string(encoded), nil
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

package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"sort"
	"strings"
	"time"
)

const (
	locationBlockDefaultTTL = 30 * 24 * time.Hour
	locationBlockMaxTTL     = 60 * 24 * time.Hour
)

type signedLocationData struct {
	UpeerID    string         `json:"upeerId"`
	Addresses  []string       `json:"addresses"`
	DhtSeq     int64          `json:"dhtSeq"`
	ExpiresAt  int64          `json:"expiresAt"`
	DeviceID   string         `json:"deviceId,omitempty"`
	DeviceMeta map[string]any `json:"deviceMeta,omitempty"`
}

type legacyLocationData struct {
	UpeerID   string `json:"upeerId"`
	Address   string `json:"address"`
	DhtSeq    int64  `json:"dhtSeq"`
	ExpiresAt int64  `json:"expiresAt"`
}

type locationBlockRecord struct {
	Address    string         `json:"address"`
	Addresses  []string       `json:"addresses"`
	DhtSeq     int64          `json:"dhtSeq"`
	ExpiresAt  int64          `json:"expiresAt"`
	Signature  string         `json:"signature"`
	DeviceID   string         `json:"deviceId,omitempty"`
	DeviceMeta map[string]any `json:"deviceMeta,omitempty"`
}

func normalizeLocationAddresses(addresses []string) ([]string, error) {
	seen := make(map[string]struct{}, len(addresses))
	normalized := make([]string, 0, len(addresses))
	for _, address := range addresses {
		trimmed := strings.TrimSpace(address)
		if !isYggdrasilAddress(net.ParseIP(trimmed)) {
			return nil, fmt.Errorf("invalid Yggdrasil address %q", address)
		}
		if _, exists := seen[trimmed]; exists {
			continue
		}
		seen[trimmed] = struct{}{}
		normalized = append(normalized, trimmed)
	}
	if len(normalized) == 0 {
		return nil, errors.New("location block requires at least one address")
	}
	sort.Strings(normalized)
	return normalized, nil
}

func stringsToAny(values []string) []any {
	converted := make([]any, len(values))
	for index, value := range values {
		converted[index] = value
	}
	return converted
}

func (identity *CryptoIdentity) BuildLocationBlock(addressesJSON string, dhtSeq, ttlMillis int64, deviceID string) (string, error) {
	if dhtSeq < 0 {
		return "", errors.New("location block dhtSeq must not be negative")
	}
	if len(deviceID) > 128 {
		return "", errors.New("location block deviceId must be at most 128 characters")
	}
	var addresses []string
	if err := json.Unmarshal([]byte(addressesJSON), &addresses); err != nil {
		return "", fmt.Errorf("decode location block addresses: %w", err)
	}
	normalized, err := normalizeLocationAddresses(addresses)
	if err != nil {
		return "", err
	}
	ttl := time.Duration(ttlMillis) * time.Millisecond
	if ttlMillis <= 0 {
		ttl = locationBlockDefaultTTL
	}
	if ttl > locationBlockMaxTTL {
		ttl = locationBlockMaxTTL
	}
	data := signedLocationData{
		UpeerID:   identity.UpeerID(),
		Addresses: normalized,
		DhtSeq:    dhtSeq,
		ExpiresAt: time.Now().Add(ttl).UnixMilli(),
		DeviceID:  deviceID,
	}
	payload := map[string]any{
		"upeerId": data.UpeerID, "addresses": stringsToAny(data.Addresses),
		"dhtSeq": json.Number(fmt.Sprintf("%d", data.DhtSeq)), "expiresAt": json.Number(fmt.Sprintf("%d", data.ExpiresAt)),
	}
	if data.DeviceID != "" {
		payload["deviceId"] = data.DeviceID
	}
	canonical, err := canonicalStringify(payload)
	if err != nil {
		return "", fmt.Errorf("canonicalize location block: %w", err)
	}
	record := locationBlockRecord{
		Address: normalized[0], Addresses: normalized, DhtSeq: dhtSeq, ExpiresAt: data.ExpiresAt,
		Signature: identity.Sign([]byte(canonical)), DeviceID: deviceID,
	}
	encoded, err := json.Marshal(record)
	if err != nil {
		return "", fmt.Errorf("encode location block: %w", err)
	}
	return string(encoded), nil
}

func VerifyLocationBlock(blockJSON, publicKeyHex string, nowMillis int64) (bool, error) {
	var block locationBlockRecord
	decoder := json.NewDecoder(strings.NewReader(blockJSON))
	decoder.UseNumber()
	if err := decoder.Decode(&block); err != nil {
		return false, fmt.Errorf("decode location block: %w", err)
	}
	if block.ExpiresAt <= nowMillis {
		return false, errors.New("location block is expired")
	}
	if block.DhtSeq < 0 || len(block.DeviceID) > 128 || !validHex(block.Signature, 64) {
		return false, errors.New("location block fields are invalid")
	}
	addresses := block.Addresses
	if len(addresses) == 0 {
		addresses = []string{block.Address}
	}
	normalized, err := normalizeLocationAddresses(addresses)
	if err != nil {
		return false, err
	}
	publicKey, err := hex.DecodeString(publicKeyHex)
	if err != nil || len(publicKey) != ed25519.PublicKeySize {
		return false, errors.New("public key must be a 32-byte hexadecimal value")
	}
	signature, err := hex.DecodeString(block.Signature)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return false, errors.New("location block signature is invalid")
	}
	upeerID := hex.EncodeToString(upeerIDFromPublicKey(publicKey))
	data := map[string]any{
		"upeerId": upeerID, "addresses": stringsToAny(normalized),
		"dhtSeq": json.Number(fmt.Sprintf("%d", block.DhtSeq)), "expiresAt": json.Number(fmt.Sprintf("%d", block.ExpiresAt)),
	}
	if block.DeviceID != "" {
		data["deviceId"] = block.DeviceID
	}
	if len(block.DeviceMeta) > 0 {
		data["deviceMeta"] = block.DeviceMeta
	}
	canonical, err := canonicalStringify(data)
	if err != nil {
		return false, err
	}
	if ed25519.Verify(publicKey, []byte(canonical), signature) {
		return true, nil
	}
	if block.DeviceID == "" && len(block.DeviceMeta) == 0 {
		legacy := map[string]any{
			"upeerId": upeerID, "address": block.Address,
			"dhtSeq": json.Number(fmt.Sprintf("%d", block.DhtSeq)), "expiresAt": json.Number(fmt.Sprintf("%d", block.ExpiresAt)),
		}
		legacyCanonical, legacyErr := canonicalStringify(legacy)
		if legacyErr == nil && ed25519.Verify(publicKey, []byte(legacyCanonical), signature) {
			return true, nil
		}
	}
	return false, nil
}

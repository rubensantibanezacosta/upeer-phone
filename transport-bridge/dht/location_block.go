package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"sort"
	"strings"
	"time"

	"golang.org/x/crypto/blake2b"
)

const (
	LocationBlockDefaultTTLMillis int64 = 30 * 24 * 60 * 60 * 1000
	LocationBlockMaxTTLMillis     int64 = 60 * 24 * 60 * 60 * 1000
	LocationBlockMaxDeviceID            = 128
	DeviceKeySize                       = 32
)

type LocationBlock struct {
	Address           string         `json:"address"`
	Addresses         []string       `json:"addresses"`
	DhtSeq            int64          `json:"dhtSeq"`
	ExpiresAt         int64          `json:"expiresAt"`
	Signature         string         `json:"signature"`
	DeviceID          string         `json:"deviceId,omitempty"`
	DeviceMeta        map[string]any `json:"deviceMeta,omitempty"`
	DeviceCertificate map[string]any `json:"deviceCertificate,omitempty"`
}

type LocationBlockOptions struct {
	Addresses  []string
	DhtSeq     int64
	TTLMillis  int64
	DeviceID   string
	DeviceMeta map[string]any
	NowMillis  int64
}

func BuildLocationBlock(secretKey []byte, options LocationBlockOptions) (string, error) {
	privateKey, err := parsePrivateKey(secretKey)
	if err != nil {
		return "", err
	}
	normalized, err := NormalizeLocationAddresses(options.Addresses)
	if err != nil {
		return "", err
	}
	deviceID, err := validateDeviceID(options.DeviceID)
	if err != nil {
		return "", err
	}
	if options.DhtSeq < 0 {
		return "", errors.New("location block dhtSeq must not be negative")
	}
	deviceMeta, err := normalizeDeviceMeta(options.DeviceMeta)
	if err != nil {
		return "", err
	}
	now := options.NowMillis
	if now <= 0 {
		now = time.Now().UnixMilli()
	}
	expiresAt := now + clampTTL(options.TTLMillis)
	upeerID := upeerIDFromPublicKey(privateKey.Public().(ed25519.PublicKey))
	data := signedLocationData{
		UpeerID:    upeerID,
		Addresses:  normalized,
		DhtSeq:     options.DhtSeq,
		ExpiresAt:  expiresAt,
		DeviceID:   deviceID,
		DeviceMeta: deviceMeta,
	}
	canonical, err := CanonicalStringify(locationPayload(data))
	if err != nil {
		return "", fmt.Errorf("canonicalize location block: %w", err)
	}
	record := LocationBlock{
		Address:    normalized[0],
		Addresses:  normalized,
		DhtSeq:     options.DhtSeq,
		ExpiresAt:  expiresAt,
		Signature:  hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical))),
		DeviceID:   deviceID,
		DeviceMeta: deviceMeta,
	}
	encoded, err := json.Marshal(record)
	if err != nil {
		return "", fmt.Errorf("encode location block: %w", err)
	}
	return string(encoded), nil
}
func VerifyLocationBlock(blockJSON, publicKeyHex string, nowMillis int64) (bool, error) {
	block, err := DecodeLocationBlock(blockJSON)
	if err != nil {
		return false, err
	}
	if block.ExpiresAt <= nowMillis {
		return false, errors.New("location block is expired")
	}
	if block.DhtSeq < 0 {
		return false, errors.New("location block dhtSeq must not be negative")
	}
	if _, err := validateDeviceID(block.DeviceID); err != nil {
		return false, err
	}
	if len(block.DeviceCertificate) > 0 {
		return VerifyDeviceLocationBlock(blockJSON, publicKeyHex, "", nowMillis)
	}
	if !validHex(block.Signature, ed25519.SignatureSize) {
		return false, errors.New("location block signature is invalid")
	}
	addresses := block.Addresses
	if len(addresses) == 0 {
		addresses = []string{block.Address}
	}
	normalized, err := NormalizeLocationAddresses(addresses)
	if err != nil {
		return false, err
	}
	publicKey, err := decodeHex(publicKeyHex, ed25519.PublicKeySize)
	if err != nil {
		return false, errors.New("public key must be a 32-byte hexadecimal value")
	}
	signature, err := decodeHex(block.Signature, ed25519.SignatureSize)
	if err != nil {
		return false, errors.New("location block signature is invalid")
	}
	upeerID := upeerIDFromPublicKey(publicKey)
	data := signedLocationData{
		UpeerID:    upeerID,
		Addresses:  normalized,
		DhtSeq:     block.DhtSeq,
		ExpiresAt:  block.ExpiresAt,
		DeviceID:   block.DeviceID,
		DeviceMeta: block.DeviceMeta,
	}
	canonical, err := CanonicalStringify(locationPayload(data))
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
		legacyCanonical, legacyErr := CanonicalStringify(legacy)
		if legacyErr == nil && ed25519.Verify(publicKey, []byte(legacyCanonical), signature) {
			return true, nil
		}
	}
	return false, nil
}

func DecodeLocationBlock(blockJSON string) (LocationBlock, error) {
	var block LocationBlock
	decoder := json.NewDecoder(strings.NewReader(blockJSON))
	decoder.UseNumber()
	if err := decoder.Decode(&block); err != nil {
		return LocationBlock{}, fmt.Errorf("decode location block: %w", err)
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err != nil {
			return LocationBlock{}, fmt.Errorf("decode location block trailing data: %w", err)
		}
		return LocationBlock{}, errors.New("location block contains trailing data")
	}
	return block, nil
}

func NormalizeLocationAddresses(addresses []string) ([]string, error) {
	seen := make(map[string]struct{}, len(addresses))
	normalized := make([]string, 0, len(addresses))
	for _, address := range addresses {
		trimmed := strings.TrimSpace(address)
		if !isYggdrasilAddress(trimmed) {
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

func DeriveDeviceID(deviceKey []byte) (string, error) {
	if len(deviceKey) != DeviceKeySize {
		return "", fmt.Errorf("device key must be %d bytes", DeviceKeySize)
	}
	hash, err := blake2b.New(32, nil)
	if err != nil {
		return "", fmt.Errorf("initialize device id hash: %w", err)
	}
	if _, err := hash.Write(deviceKey); err != nil {
		return "", fmt.Errorf("hash device key: %w", err)
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

type signedLocationData struct {
	UpeerID    string
	Addresses  []string
	DhtSeq     int64
	ExpiresAt  int64
	DeviceID   string
	DeviceMeta map[string]any
}

func locationPayload(data signedLocationData) map[string]any {
	payload := map[string]any{
		"upeerId": data.UpeerID, "addresses": stringsToAny(data.Addresses),
		"dhtSeq": json.Number(fmt.Sprintf("%d", data.DhtSeq)), "expiresAt": json.Number(fmt.Sprintf("%d", data.ExpiresAt)),
	}
	if data.DeviceID != "" {
		payload["deviceId"] = data.DeviceID
	}
	if len(data.DeviceMeta) > 0 {
		payload["deviceMeta"] = data.DeviceMeta
	}
	return payload
}

func stringsToAny(values []string) []any {
	converted := make([]any, len(values))
	for index, value := range values {
		converted[index] = value
	}
	return converted
}

func clampTTL(ttlMillis int64) int64 {
	if ttlMillis <= 0 {
		return LocationBlockDefaultTTLMillis
	}
	if ttlMillis > LocationBlockMaxTTLMillis {
		return LocationBlockMaxTTLMillis
	}
	return ttlMillis
}

func validateDeviceID(deviceID string) (string, error) {
	if len(deviceID) > LocationBlockMaxDeviceID {
		return "", fmt.Errorf("location block deviceId must be at most %d characters", LocationBlockMaxDeviceID)
	}
	return deviceID, nil
}

func normalizeDeviceMeta(meta map[string]any) (map[string]any, error) {
	if len(meta) == 0 {
		return nil, nil
	}
	encoded, err := json.Marshal(meta)
	if err != nil {
		return nil, fmt.Errorf("encode location block deviceMeta: %w", err)
	}
	decoded, err := DecodeJSONObject(string(encoded))
	if err != nil {
		return nil, fmt.Errorf("decode location block deviceMeta: %w", err)
	}
	return decoded, nil
}

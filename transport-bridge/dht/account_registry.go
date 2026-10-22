package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"strings"

	"golang.org/x/crypto/blake2b"
)

func AccountRegistryKey(accountUpeerID string) (string, error) {
	normalized, err := normalizeAccountUpeerID(accountUpeerID)
	if err != nil {
		return "", err
	}
	hash, err := blake2b.New(32, nil)
	if err != nil {
		return "", fmt.Errorf("initialize account registry key: %w", err)
	}
	if _, err := hash.Write([]byte(AccountRegistryKeyPrefix + normalized)); err != nil {
		return "", fmt.Errorf("hash account registry key: %w", err)
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

func NextAccountRegistryEpoch(registryJSON string) (int64, error) {
	var registry AccountRegistry
	if err := decodeAccountRecord(registryJSON, &registry); err != nil {
		return 0, err
	}
	if registry.Version != AccountProtocolVersion {
		return 0, errors.New("unsupported account registry version")
	}
	if registry.Epoch < 0 {
		return 0, errors.New("account registry epoch must not be negative")
	}
	return registry.Epoch + 1, nil
}

func BuildAccountRegistry(accountSecretKey []byte, options AccountRegistryOptions) (string, error) {
	privateKey, err := parsePrivateKey(accountSecretKey)
	if err != nil {
		return "", err
	}
	if options.Epoch < 0 {
		return "", errors.New("account registry epoch must not be negative")
	}
	now := accountNow(options.NowMillis)
	issuedAt := options.IssuedAt
	if issuedAt <= 0 {
		issuedAt = now
	}
	ttl := accountTTL(options.TTLMillis, AccountRegistryDefaultTTLMillis, AccountRegistryMaxTTLMillis)
	if issuedAt > math.MaxInt64-ttl {
		return "", errors.New("account registry expiresAt overflows")
	}
	expiresAt := issuedAt + ttl
	if err := validateAccountWindow(issuedAt, expiresAt, now, AccountRegistryMaxTTLMillis); err != nil {
		return "", err
	}
	devices, revoked, err := normalizeRegistryContents(options.Devices, options.Revoked, options.Epoch, now, true)
	if err != nil {
		return "", err
	}
	registry := AccountRegistry{
		Version:          AccountProtocolVersion,
		AccountUpeerID:   upeerIDFromPublicKey(privateKey.Public().(ed25519.PublicKey)),
		AccountPublicKey: accountPublicKeyHex(privateKey.Public().(ed25519.PublicKey)),
		Epoch:            options.Epoch,
		IssuedAt:         issuedAt,
		ExpiresAt:        expiresAt,
		Devices:          devices,
		Revoked:          revoked,
	}
	canonical, err := CanonicalStringify(registrySignedData(registry))
	if err != nil {
		return "", fmt.Errorf("canonicalize account registry: %w", err)
	}
	registry.Signature = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	encoded, err := json.Marshal(registry)
	if err != nil {
		return "", fmt.Errorf("encode account registry: %w", err)
	}
	if len(encoded) > MaxStoredValueBytes {
		return "", fmt.Errorf("account registry exceeds %d bytes", MaxStoredValueBytes)
	}
	return string(encoded), nil
}

func VerifyAccountRegistry(registryJSON, expectedAccountUpeerID string, nowMillis int64) (AccountRegistry, error) {
	return verifyAccountRegistry(registryJSON, expectedAccountUpeerID, nowMillis, true)
}

func verifyAccountRegistry(registryJSON, expectedAccountUpeerID string, nowMillis int64, requireFresh bool) (AccountRegistry, error) {
	if len(registryJSON) > MaxStoredValueBytes {
		return AccountRegistry{}, fmt.Errorf("account registry exceeds %d bytes", MaxStoredValueBytes)
	}
	var registry AccountRegistry
	if err := decodeAccountRecord(registryJSON, &registry); err != nil {
		return AccountRegistry{}, err
	}
	if registry.Version != AccountProtocolVersion {
		return AccountRegistry{}, errors.New("unsupported account registry version")
	}
	accountUpeerID, err := normalizeAccountUpeerID(registry.AccountUpeerID)
	if err != nil {
		return AccountRegistry{}, err
	}
	registry.AccountUpeerID = accountUpeerID
	if expectedAccountUpeerID != "" {
		expected, err := normalizeAccountUpeerID(expectedAccountUpeerID)
		if err != nil {
			return AccountRegistry{}, err
		}
		if expected != accountUpeerID {
			return AccountRegistry{}, errors.New("account registry does not match the expected account")
		}
	}
	accountPublicKey, err := normalizeAccountPublicKey(registry.AccountPublicKey)
	if err != nil {
		return AccountRegistry{}, err
	}
	registry.AccountPublicKey = accountPublicKey
	if !strings.EqualFold(upeerIDFromPublicKey(mustDecodeAccountHex(accountPublicKey)), accountUpeerID) {
		return AccountRegistry{}, errors.New("account registry public key does not derive accountUpeerId")
	}
	if registry.Epoch < 0 {
		return AccountRegistry{}, errors.New("account registry epoch must not be negative")
	}
	now := accountNow(nowMillis)
	if requireFresh {
		if err := validateAccountWindow(registry.IssuedAt, registry.ExpiresAt, now, AccountRegistryMaxTTLMillis); err != nil {
			return AccountRegistry{}, err
		}
	} else if err := validateAccountWindowShape(registry.IssuedAt, registry.ExpiresAt, now, AccountRegistryMaxTTLMillis); err != nil {
		return AccountRegistry{}, err
	}
	devices, revoked, err := normalizeRegistryContents(registry.Devices, registry.Revoked, registry.Epoch, now, false)
	if err != nil {
		return AccountRegistry{}, err
	}
	registry.Devices = devices
	registry.Revoked = revoked
	if !validHex(registry.Signature, ed25519.SignatureSize) {
		return AccountRegistry{}, errors.New("account registry signature is invalid")
	}
	canonical, err := CanonicalStringify(registrySignedData(registry))
	if err != nil {
		return AccountRegistry{}, err
	}
	signature, err := hex.DecodeString(registry.Signature)
	if err != nil {
		return AccountRegistry{}, errors.New("account registry signature is invalid")
	}
	if !ed25519.Verify(ed25519.PublicKey(mustDecodeAccountHex(accountPublicKey)), []byte(canonical), signature) {
		return AccountRegistry{}, errors.New("account registry signature does not verify")
	}
	return registry, nil
}

func ValidateAccountRegistryUpdate(previousJSON, nextJSON, expectedAccountUpeerID string, nowMillis int64) (AccountRegistry, error) {
	previous, err := verifyAccountRegistry(previousJSON, expectedAccountUpeerID, nowMillis, false)
	if err != nil {
		return AccountRegistry{}, err
	}
	next, err := VerifyAccountRegistry(nextJSON, previous.AccountUpeerID, nowMillis)
	if err != nil {
		return AccountRegistry{}, err
	}
	if !strings.EqualFold(previous.AccountPublicKey, next.AccountPublicKey) {
		return AccountRegistry{}, errors.New("account registry update changes the account public key")
	}
	if next.Epoch <= previous.Epoch {
		return AccountRegistry{}, errors.New("account registry epoch must increase monotonically")
	}
	if next.IssuedAt < previous.IssuedAt {
		return AccountRegistry{}, errors.New("account registry issuedAt must not decrease")
	}
	previousRevoked := make(map[string]int64, len(previous.Revoked))
	for _, revocation := range previous.Revoked {
		previousRevoked[revocation.DeviceID] = revocation.RevokedAt
	}
	nextRevoked := make(map[string]int64, len(next.Revoked))
	for _, revocation := range next.Revoked {
		nextRevoked[revocation.DeviceID] = revocation.RevokedAt
	}
	for deviceID, revokedAt := range previousRevoked {
		if nextRevokedAt, exists := nextRevoked[deviceID]; !exists || nextRevokedAt != revokedAt {
			return AccountRegistry{}, fmt.Errorf("account registry update drops revocation for %s", deviceID)
		}
	}
	nextDevices := make(map[string]struct{}, len(next.Devices))
	for _, device := range next.Devices {
		nextDevices[device.DeviceID] = struct{}{}
	}
	for _, device := range previous.Devices {
		if _, exists := nextDevices[device.DeviceID]; exists {
			continue
		}
		if _, revoked := nextRevoked[device.DeviceID]; !revoked {
			return AccountRegistry{}, fmt.Errorf("account registry update removes %s without revoking it", device.DeviceID)
		}
	}
	return next, nil
}

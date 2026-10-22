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
)

func decodeAccountRecord(payload string, target any) error {
	decoder := json.NewDecoder(strings.NewReader(payload))
	if err := decoder.Decode(target); err != nil {
		return fmt.Errorf("decode account record: %w", err)
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err != nil {
			return fmt.Errorf("decode account record trailing data: %w", err)
		}
		return errors.New("account record contains trailing data")
	}
	return nil
}

func accountNow(nowMillis int64) int64 {
	if nowMillis > 0 {
		return nowMillis
	}
	return time.Now().UnixMilli()
}

func normalizeAccountUpeerID(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if !validHex(normalized, 32) {
		return "", errors.New("accountUpeerId must be a 32-byte hexadecimal value")
	}
	return normalized, nil
}

func normalizeAccountPublicKey(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if !validHex(normalized, ed25519.PublicKeySize) {
		return "", errors.New("account public key must be a 32-byte hexadecimal value")
	}
	return normalized, nil
}

func normalizeDevicePublicKey(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if !validHex(normalized, ed25519.PublicKeySize) {
		return "", errors.New("device public key must be a 32-byte hexadecimal value")
	}
	return normalized, nil
}

func normalizeStaticX25519PublicKey(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if normalized == "" {
		return "", nil
	}
	if !validHex(normalized, 32) {
		return "", errors.New("static X25519 public key must be a 32-byte hexadecimal value")
	}
	return normalized, nil
}

func normalizeAccountPlatform(value string) (string, error) {
	normalized := strings.TrimSpace(value)
	if len(normalized) > AccountMaxPlatformLength {
		return "", fmt.Errorf("platform must be at most %d characters", AccountMaxPlatformLength)
	}
	for _, character := range normalized {
		if character < 0x20 || character == 0x7f {
			return "", errors.New("platform must not contain control characters")
		}
	}
	return normalized, nil
}

func normalizeAccountCapabilities(values []string) ([]string, error) {
	if len(values) > AccountMaxCapabilities {
		return nil, fmt.Errorf("capabilities must contain at most %d values", AccountMaxCapabilities)
	}
	seen := make(map[string]struct{}, len(values))
	normalized := make([]string, 0, len(values))
	for _, value := range values {
		capability := strings.ToLower(strings.TrimSpace(value))
		if capability == "" {
			return nil, errors.New("capability must not be empty")
		}
		if len(capability) > AccountMaxCapabilityLength {
			return nil, fmt.Errorf("capability must be at most %d characters", AccountMaxCapabilityLength)
		}
		for _, character := range capability {
			if character <= 0x20 || character == 0x7f {
				return nil, errors.New("capability must not contain whitespace or control characters")
			}
		}
		if _, exists := seen[capability]; exists {
			continue
		}
		seen[capability] = struct{}{}
		normalized = append(normalized, capability)
	}
	sort.Strings(normalized)
	return normalized, nil
}

func normalizeAccountNonce(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if normalized == "" {
		return "", nil
	}
	if !validHex(normalized, AccountNonceBytes) {
		return "", fmt.Errorf("pairing nonce must be a %d-byte hexadecimal value", AccountNonceBytes)
	}
	return normalized, nil
}

func normalizeAccountReason(value string) (string, error) {
	normalized := strings.TrimSpace(value)
	if normalized == "" {
		return "", errors.New("revocation reason must not be empty")
	}
	if len(normalized) > AccountMaxReasonLength {
		return "", fmt.Errorf("revocation reason must be at most %d characters", AccountMaxReasonLength)
	}
	for _, character := range normalized {
		if character < 0x20 || character == 0x7f {
			return "", errors.New("revocation reason must not contain control characters")
		}
	}
	return normalized, nil
}

func validateAccountWindow(issuedAt, expiresAt, now, maxTTL int64) error {
	if err := validateAccountWindowShape(issuedAt, expiresAt, now, maxTTL); err != nil {
		return err
	}
	if expiresAt <= now {
		return errors.New("account record is expired")
	}
	return nil
}

func validateAccountWindowShape(issuedAt, expiresAt, now, maxTTL int64) error {
	if issuedAt <= 0 {
		return errors.New("issuedAt must be positive")
	}
	if expiresAt <= issuedAt {
		return errors.New("expiresAt must be later than issuedAt")
	}
	if expiresAt-issuedAt > maxTTL {
		return fmt.Errorf("validity window must be at most %d milliseconds", maxTTL)
	}
	if issuedAt > now+AccountClockSkewMillis {
		return errors.New("issuedAt is in the future")
	}
	return nil
}

func accountTTL(ttlMillis, defaultTTL, maxTTL int64) int64 {
	if ttlMillis <= 0 {
		return defaultTTL
	}
	if ttlMillis > maxTTL {
		return maxTTL
	}
	return ttlMillis
}

func accountPublicKeyHex(publicKey ed25519.PublicKey) string {
	return hex.EncodeToString(publicKey)
}

func equalAccountStrings(left, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	for index := range left {
		if left[index] != right[index] {
			return false
		}
	}
	return true
}

func validateAccountCapabilitiesShape(values []string) error {
	if len(values) > AccountMaxCapabilities {
		return fmt.Errorf("capabilities must contain at most %d values", AccountMaxCapabilities)
	}
	seen := make(map[string]struct{}, len(values))
	for _, value := range values {
		if value == "" || len(value) > AccountMaxCapabilityLength {
			return errors.New("capability is empty or too long")
		}
		for _, character := range value {
			if character <= 0x20 || character == 0x7f {
				return errors.New("capability must not contain whitespace or control characters")
			}
		}
		if _, exists := seen[value]; exists {
			return errors.New("capabilities must not contain duplicates")
		}
		seen[value] = struct{}{}
	}
	return nil
}

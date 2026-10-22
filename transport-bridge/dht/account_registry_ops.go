package dht

import (
	"crypto/ed25519"
	"errors"
	"fmt"
	"sort"
	"strings"
)

func AccountRegistryDeviceFromCertificate(certificate AccountDeviceCertificate) AccountRegistryDevice {
	return AccountRegistryDevice{
		DeviceID:              certificate.DeviceID,
		DevicePublicKey:       certificate.DevicePublicKey,
		StaticX25519PublicKey: certificate.StaticX25519PublicKey,
		Platform:              certificate.Platform,
		Capabilities:          append([]string(nil), certificate.Capabilities...),
		AuthorizedAt:          certificate.IssuedAt,
		ExpiresAt:             certificate.ExpiresAt,
		Epoch:                 certificate.Epoch,
	}
}

func RevokeAccountDevice(previousRegistryJSON string, accountSecretKey []byte, deviceID, reason string, nowMillis int64) (string, error) {
	privateKey, err := parsePrivateKey(accountSecretKey)
	if err != nil {
		return "", err
	}
	now := accountNow(nowMillis)
	rootUpeerID := upeerIDFromPublicKey(privateKey.Public().(ed25519.PublicKey))
	previous, err := verifyAccountRegistry(previousRegistryJSON, rootUpeerID, now, false)
	if err != nil {
		return "", err
	}
	normalizedDeviceID, err := normalizeAccountDeviceID(deviceID)
	if err != nil {
		return "", err
	}
	normalizedReason, err := normalizeAccountReason(reason)
	if err != nil {
		return "", err
	}
	devices := make([]AccountRegistryDevice, 0, len(previous.Devices))
	found := false
	for _, device := range previous.Devices {
		if device.DeviceID == normalizedDeviceID {
			found = true
			continue
		}
		devices = append(devices, device)
	}
	if !found {
		return "", errors.New("revoked device is not authorized in the previous account registry")
	}
	revoked := append([]AccountRevocation(nil), previous.Revoked...)
	revoked = append(revoked, AccountRevocation{
		DeviceID:  normalizedDeviceID,
		RevokedAt: now,
		Reason:    normalizedReason,
	})
	issuedAt := now
	if issuedAt < previous.IssuedAt {
		issuedAt = previous.IssuedAt
	}
	return BuildAccountRegistry(accountSecretKey, AccountRegistryOptions{
		Epoch:     previous.Epoch + 1,
		IssuedAt:  issuedAt,
		NowMillis: now,
		Devices:   devices,
		Revoked:   revoked,
	})
}

func normalizeAccountDeviceID(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if !validHex(normalized, 32) {
		return "", errors.New("deviceId must be a 32-byte hexadecimal value")
	}
	return normalized, nil
}

func normalizeRegistryContents(devices []AccountRegistryDevice, revoked []AccountRevocation, registryEpoch, now int64, requireActive bool) ([]AccountRegistryDevice, []AccountRevocation, error) {
	if len(devices) > AccountMaxDevices {
		return nil, nil, fmt.Errorf("account registry must contain at most %d devices", AccountMaxDevices)
	}
	if len(revoked) > AccountMaxRevokedDevices {
		return nil, nil, fmt.Errorf("account registry must contain at most %d revocations", AccountMaxRevokedDevices)
	}
	normalizedDevices := make([]AccountRegistryDevice, 0, len(devices))
	deviceIDs := make(map[string]struct{}, len(devices))
	for _, device := range devices {
		normalized, err := normalizeRegistryDevice(device, registryEpoch, now, requireActive)
		if err != nil {
			return nil, nil, err
		}
		if _, exists := deviceIDs[normalized.DeviceID]; exists {
			return nil, nil, fmt.Errorf("account registry contains duplicate deviceId %s", normalized.DeviceID)
		}
		deviceIDs[normalized.DeviceID] = struct{}{}
		normalizedDevices = append(normalizedDevices, normalized)
	}
	normalizedRevoked := make([]AccountRevocation, 0, len(revoked))
	revokedIDs := make(map[string]struct{}, len(revoked))
	for _, revocation := range revoked {
		normalized, err := normalizeAccountRevocation(revocation, now)
		if err != nil {
			return nil, nil, err
		}
		if _, exists := revokedIDs[normalized.DeviceID]; exists {
			return nil, nil, fmt.Errorf("account registry contains duplicate revocation for %s", normalized.DeviceID)
		}
		if _, exists := deviceIDs[normalized.DeviceID]; exists {
			return nil, nil, fmt.Errorf("account registry cannot authorize and revoke %s at the same epoch", normalized.DeviceID)
		}
		revokedIDs[normalized.DeviceID] = struct{}{}
		normalizedRevoked = append(normalizedRevoked, normalized)
	}
	sort.Slice(normalizedDevices, func(left, right int) bool {
		return normalizedDevices[left].DeviceID < normalizedDevices[right].DeviceID
	})
	sort.Slice(normalizedRevoked, func(left, right int) bool {
		return normalizedRevoked[left].DeviceID < normalizedRevoked[right].DeviceID
	})
	return normalizedDevices, normalizedRevoked, nil
}

func normalizeRegistryDevice(device AccountRegistryDevice, registryEpoch, now int64, requireActive bool) (AccountRegistryDevice, error) {
	deviceID, err := normalizeAccountDeviceID(device.DeviceID)
	if err != nil {
		return AccountRegistryDevice{}, err
	}
	device.DeviceID = deviceID
	devicePublicKey, err := normalizeDevicePublicKey(device.DevicePublicKey)
	if err != nil {
		return AccountRegistryDevice{}, err
	}
	device.DevicePublicKey = devicePublicKey
	derivedDeviceID, err := DeriveDeviceID(mustDecodeAccountHex(devicePublicKey))
	if err != nil {
		return AccountRegistryDevice{}, err
	}
	if device.DeviceID != derivedDeviceID {
		return AccountRegistryDevice{}, errors.New("account registry deviceId does not match its public key")
	}
	staticKey, err := normalizeStaticX25519PublicKey(device.StaticX25519PublicKey)
	if err != nil {
		return AccountRegistryDevice{}, err
	}
	device.StaticX25519PublicKey = staticKey
	platform, err := normalizeAccountPlatform(device.Platform)
	if err != nil {
		return AccountRegistryDevice{}, err
	}
	device.Platform = platform
	capabilities, err := normalizeAccountCapabilities(device.Capabilities)
	if err != nil {
		return AccountRegistryDevice{}, err
	}
	device.Capabilities = capabilities
	if device.AuthorizedAt <= 0 {
		return AccountRegistryDevice{}, errors.New("account registry device authorizedAt must be positive")
	}
	if device.ExpiresAt <= device.AuthorizedAt {
		return AccountRegistryDevice{}, errors.New("account registry device expiresAt must be later than authorizedAt")
	}
	if device.ExpiresAt-device.AuthorizedAt > AccountCertificateMaxTTLMillis {
		return AccountRegistryDevice{}, errors.New("account registry device validity window is too long")
	}
	if device.AuthorizedAt > now+AccountClockSkewMillis {
		return AccountRegistryDevice{}, errors.New("account registry device authorizedAt is in the future")
	}
	if requireActive && device.ExpiresAt <= now {
		return AccountRegistryDevice{}, errors.New("account registry device is expired")
	}
	if device.Epoch < 0 || device.Epoch > registryEpoch {
		return AccountRegistryDevice{}, errors.New("account registry device epoch is invalid")
	}
	return device, nil
}

func normalizeAccountRevocation(revocation AccountRevocation, now int64) (AccountRevocation, error) {
	deviceID, err := normalizeAccountDeviceID(revocation.DeviceID)
	if err != nil {
		return AccountRevocation{}, err
	}
	revocation.DeviceID = deviceID
	reason, err := normalizeAccountReason(revocation.Reason)
	if err != nil {
		return AccountRevocation{}, err
	}
	revocation.Reason = reason
	if revocation.RevokedAt <= 0 {
		return AccountRevocation{}, errors.New("account registry revocation revokedAt must be positive")
	}
	if revocation.RevokedAt > now+AccountClockSkewMillis {
		return AccountRevocation{}, errors.New("account registry revocation is in the future")
	}
	return revocation, nil
}

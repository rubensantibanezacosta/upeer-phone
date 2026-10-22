package dht

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
)

func generateAccountNonce() (string, error) {
	nonce := make([]byte, AccountNonceBytes)
	if _, err := rand.Read(nonce); err != nil {
		return "", fmt.Errorf("generate pairing nonce: %w", err)
	}
	return hex.EncodeToString(nonce), nil
}

func mustDecodeAccountHex(value string) []byte {
	decoded, err := hex.DecodeString(value)
	if err != nil {
		return nil
	}
	return decoded
}

func accountNumber(value int64) json.Number {
	return json.Number(strconv.FormatInt(value, 10))
}

func accountStrings(values []string) []any {
	converted := make([]any, len(values))
	for index, value := range values {
		converted[index] = value
	}
	return converted
}

func pairingRequestSignedData(request DevicePairingRequest) map[string]any {
	payload := map[string]any{
		"version":         accountNumber(int64(request.Version)),
		"accountUpeerId":  request.AccountUpeerID,
		"deviceId":        request.DeviceID,
		"devicePublicKey": request.DevicePublicKey,
		"nonce":           request.Nonce,
		"createdAt":       accountNumber(request.CreatedAt),
		"expiresAt":       accountNumber(request.ExpiresAt),
	}
	if request.StaticX25519PublicKey != "" {
		payload["staticX25519PublicKey"] = request.StaticX25519PublicKey
	}
	if request.Platform != "" {
		payload["platform"] = request.Platform
	}
	if len(request.Capabilities) > 0 {
		payload["capabilities"] = accountStrings(request.Capabilities)
	}
	return payload
}

func certificateSignedData(certificate AccountDeviceCertificate) map[string]any {
	payload := map[string]any{
		"version":          accountNumber(int64(certificate.Version)),
		"accountUpeerId":   certificate.AccountUpeerID,
		"accountPublicKey": certificate.AccountPublicKey,
		"deviceId":         certificate.DeviceID,
		"devicePublicKey":  certificate.DevicePublicKey,
		"issuedAt":         accountNumber(certificate.IssuedAt),
		"expiresAt":        accountNumber(certificate.ExpiresAt),
		"epoch":            accountNumber(certificate.Epoch),
	}
	if certificate.StaticX25519PublicKey != "" {
		payload["staticX25519PublicKey"] = certificate.StaticX25519PublicKey
	}
	if certificate.Platform != "" {
		payload["platform"] = certificate.Platform
	}
	if len(certificate.Capabilities) > 0 {
		payload["capabilities"] = accountStrings(certificate.Capabilities)
	}
	return payload
}

func registrySignedData(registry AccountRegistry) map[string]any {
	devices := make([]any, len(registry.Devices))
	for index, device := range registry.Devices {
		devices[index] = registryDeviceSignedData(device)
	}
	revoked := make([]any, len(registry.Revoked))
	for index, revocation := range registry.Revoked {
		revoked[index] = revocationSignedData(revocation)
	}
	return map[string]any{
		"version":          accountNumber(int64(registry.Version)),
		"accountUpeerId":   registry.AccountUpeerID,
		"accountPublicKey": registry.AccountPublicKey,
		"epoch":            accountNumber(registry.Epoch),
		"issuedAt":         accountNumber(registry.IssuedAt),
		"expiresAt":        accountNumber(registry.ExpiresAt),
		"devices":          devices,
		"revoked":          revoked,
	}
}

func registryDeviceSignedData(device AccountRegistryDevice) map[string]any {
	payload := map[string]any{
		"deviceId":        device.DeviceID,
		"devicePublicKey": device.DevicePublicKey,
		"authorizedAt":    accountNumber(device.AuthorizedAt),
		"expiresAt":       accountNumber(device.ExpiresAt),
		"epoch":           accountNumber(device.Epoch),
	}
	if device.StaticX25519PublicKey != "" {
		payload["staticX25519PublicKey"] = device.StaticX25519PublicKey
	}
	if device.Platform != "" {
		payload["platform"] = device.Platform
	}
	if len(device.Capabilities) > 0 {
		payload["capabilities"] = accountStrings(device.Capabilities)
	}
	return payload
}

func revocationSignedData(revocation AccountRevocation) map[string]any {
	return map[string]any{
		"deviceId":  revocation.DeviceID,
		"revokedAt": accountNumber(revocation.RevokedAt),
		"reason":    revocation.Reason,
	}
}

func decodeAccountCertificate(certificateJSON string) (AccountDeviceCertificate, error) {
	var certificate AccountDeviceCertificate
	if err := decodeAccountRecord(certificateJSON, &certificate); err != nil {
		return AccountDeviceCertificate{}, err
	}
	return certificate, nil
}

func accountCertificateJSON(certificate AccountDeviceCertificate) (string, error) {
	encoded, err := json.Marshal(certificate)
	if err != nil {
		return "", errors.New("encode device certificate")
	}
	return string(encoded), nil
}

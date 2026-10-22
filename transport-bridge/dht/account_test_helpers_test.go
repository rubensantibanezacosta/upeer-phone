package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"testing"
)

const accountTestNow int64 = 1_790_000_000_000

func accountTestKey(seed byte) ed25519.PrivateKey {
	bytes := make([]byte, ed25519.SeedSize)
	for index := range bytes {
		bytes[index] = seed
	}
	return ed25519.NewKeyFromSeed(bytes)
}

func accountTestID(key ed25519.PrivateKey) string {
	return upeerIDFromPublicKey(key.Public().(ed25519.PublicKey))
}

func accountTestPairingRequest(t *testing.T, deviceKey ed25519.PrivateKey, accountUpeerID string, createdAt int64) string {
	t.Helper()
	request, err := BuildDevicePairingRequest(deviceKey, DevicePairingRequestOptions{
		AccountUpeerID: accountUpeerID,
		Platform:       "android",
		Capabilities:   []string{"chat", "dht"},
		Nonce:          hex.EncodeToString(deviceKey[:AccountNonceBytes]),
		CreatedAt:      createdAt,
	})
	if err != nil {
		t.Fatal(err)
	}
	return request
}

func accountTestCertificate(t *testing.T, rootKey, deviceKey ed25519.PrivateKey, accountUpeerID string, epoch, issuedAt, expiresAt int64) string {
	t.Helper()
	request := accountTestPairingRequest(t, deviceKey, accountUpeerID, issuedAt)
	certificate, err := ApproveDevicePairingRequest(rootKey, request, epoch, issuedAt, expiresAt)
	if err != nil {
		t.Fatal(err)
	}
	return certificate
}

func accountTestRegistry(t *testing.T, rootKey ed25519.PrivateKey, certificates []string, revoked []AccountRevocation, epoch, now int64) string {
	t.Helper()
	devices := make([]AccountRegistryDevice, 0, len(certificates))
	for _, encoded := range certificates {
		certificate, err := VerifyDeviceCertificate(encoded, accountTestID(rootKey), now)
		if err != nil {
			t.Fatal(err)
		}
		devices = append(devices, AccountRegistryDeviceFromCertificate(certificate))
	}
	registry, err := BuildAccountRegistry(rootKey, AccountRegistryOptions{
		Epoch:     epoch,
		IssuedAt:  now,
		NowMillis: now,
		Devices:   devices,
		Revoked:   revoked,
	})
	if err != nil {
		t.Fatal(err)
	}
	return registry
}

func accountTestAdmission(t *testing.T, certificate, registry string, now int64) {
	t.Helper()
	if _, err := ValidateDeviceAdmission(certificate, registry, "", now); err != nil {
		t.Fatal(err)
	}
}

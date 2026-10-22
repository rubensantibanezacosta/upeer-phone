package dht

import (
	"strings"
	"testing"
)

func TestAccountRegistryAdmitsOnlyAuthorizedDevices(t *testing.T) {
	rootKey := accountTestKey(30)
	deviceAKey := accountTestKey(31)
	deviceBKey := accountTestKey(32)
	deviceCKey := accountTestKey(33)
	accountUpeerID := accountTestID(rootKey)
	certificateA := accountTestCertificate(t, rootKey, deviceAKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	certificateB := accountTestCertificate(t, rootKey, deviceBKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	certificateC := accountTestCertificate(t, rootKey, deviceCKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	registry := accountTestRegistry(t, rootKey, []string{certificateA, certificateB}, nil, 7, accountTestNow)
	accountTestAdmission(t, certificateA, registry, accountTestNow)
	accountTestAdmission(t, certificateB, registry, accountTestNow)
	if _, err := ValidateDeviceAdmission(certificateC, registry, "", accountTestNow); err == nil {
		t.Fatal("device outside the account registry was admitted")
	}
	if _, err := ValidateDeviceAdmission(certificateA, registry, accountTestID(accountTestKey(34)), accountTestNow); err == nil {
		t.Fatal("device certificate was admitted for a different expected account")
	}
	if key, err := AccountRegistryKey(accountUpeerID); err != nil || !validKey(key) {
		t.Fatalf("registry key = %q, %v", key, err)
	}
	if epoch, err := NextAccountRegistryEpoch(registry); err != nil || epoch != 8 {
		t.Fatalf("next epoch = %d, %v", epoch, err)
	}
}

func TestAccountRegistryRejectsTamperingAndRollback(t *testing.T) {
	rootKey := accountTestKey(40)
	deviceAKey := accountTestKey(41)
	deviceBKey := accountTestKey(42)
	accountUpeerID := accountTestID(rootKey)
	certificateA := accountTestCertificate(t, rootKey, deviceAKey, accountUpeerID, 6, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	certificateB := accountTestCertificate(t, rootKey, deviceBKey, accountUpeerID, 6, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	previous := accountTestRegistry(t, rootKey, []string{certificateA, certificateB}, nil, 7, accountTestNow)
	rollback := accountTestRegistry(t, rootKey, []string{certificateA, certificateB}, nil, 6, accountTestNow)
	if _, err := ValidateAccountRegistryUpdate(previous, rollback, accountUpeerID, accountTestNow); err == nil {
		t.Fatal("account registry epoch rollback was accepted")
	}
	dropped := accountTestRegistry(t, rootKey, []string{certificateA}, nil, 8, accountTestNow)
	if _, err := ValidateAccountRegistryUpdate(previous, dropped, accountUpeerID, accountTestNow); err == nil {
		t.Fatal("account registry update removed a device without revoking it")
	}
	later := accountTestNow + AccountRegistryDefaultTTLMillis + 1_000
	refreshed := accountTestRegistry(t, rootKey, []string{certificateA, certificateB}, nil, 8, later)
	if _, err := ValidateAccountRegistryUpdate(previous, refreshed, accountUpeerID, later); err != nil {
		t.Fatalf("expired previous registry blocked a monotonic refresh: %v", err)
	}
	tampered := strings.Replace(previous, `"platform":"android"`, `"platform":"ios"`, 1)
	if _, err := VerifyAccountRegistry(tampered, accountUpeerID, accountTestNow); err == nil {
		t.Fatal("tampered account registry was accepted")
	}
	if _, err := VerifyAccountRegistry(previous, accountUpeerID, accountTestNow+AccountRegistryDefaultTTLMillis+1); err == nil {
		t.Fatal("expired account registry was accepted")
	}
}

func TestAccountRegistryRevocationAndOfflineWindow(t *testing.T) {
	rootKey := accountTestKey(50)
	deviceAKey := accountTestKey(51)
	deviceBKey := accountTestKey(52)
	accountUpeerID := accountTestID(rootKey)
	certificateA := accountTestCertificate(t, rootKey, deviceAKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	certificateB := accountTestCertificate(t, rootKey, deviceBKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	previous := accountTestRegistry(t, rootKey, []string{certificateA, certificateB}, nil, 7, accountTestNow)
	deviceB, err := VerifyDeviceCertificate(certificateB, accountUpeerID, accountTestNow)
	if err != nil {
		t.Fatal(err)
	}
	revokedAt := accountTestNow + 1_000
	next, err := RevokeAccountDevice(previous, rootKey, deviceB.DeviceID, "lost device", revokedAt)
	if err != nil {
		t.Fatal(err)
	}
	nextRegistry, err := ValidateAccountRegistryUpdate(previous, next, accountUpeerID, revokedAt)
	if err != nil {
		t.Fatal(err)
	}
	if nextRegistry.Epoch != 8 {
		t.Fatalf("revocation epoch = %d, want 8", nextRegistry.Epoch)
	}
	accountTestAdmission(t, certificateA, next, revokedAt)
	if _, err := ValidateDeviceAdmission(certificateB, next, "", revokedAt); err == nil {
		t.Fatal("revoked device was admitted with the new registry")
	}
	accountTestAdmission(t, certificateB, previous, revokedAt)
}

func TestAccountRegistryRejectsCertificateMismatch(t *testing.T) {
	rootKey := accountTestKey(60)
	deviceKey := accountTestKey(61)
	accountUpeerID := accountTestID(rootKey)
	certificateJSON := accountTestCertificate(t, rootKey, deviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	certificate, err := VerifyDeviceCertificate(certificateJSON, accountUpeerID, accountTestNow)
	if err != nil {
		t.Fatal(err)
	}
	device := AccountRegistryDeviceFromCertificate(certificate)
	device.Platform = "ios"
	registry, err := BuildAccountRegistry(rootKey, AccountRegistryOptions{
		Epoch:     7,
		IssuedAt:  accountTestNow,
		NowMillis: accountTestNow,
		Devices:   []AccountRegistryDevice{device},
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := ValidateDeviceAdmission(certificateJSON, registry, "", accountTestNow); err == nil {
		t.Fatal("certificate with mismatched registry metadata was admitted")
	}
}

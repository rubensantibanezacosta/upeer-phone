package dht

import (
	"crypto/ed25519"
	"strings"
	"testing"
)

func TestPairingRequestBindsNewDeviceToAccount(t *testing.T) {
	rootKey := accountTestKey(1)
	deviceKey := accountTestKey(2)
	accountUpeerID := accountTestID(rootKey)
	requestJSON := accountTestPairingRequest(t, deviceKey, accountUpeerID, accountTestNow)
	request, err := VerifyDevicePairingRequest(requestJSON, accountUpeerID, accountTestNow)
	if err != nil {
		t.Fatal(err)
	}
	expectedDeviceID, err := DeriveDeviceID(deviceKey.Public().(ed25519.PublicKey))
	if err != nil {
		t.Fatal(err)
	}
	if request.DeviceID != expectedDeviceID {
		t.Fatalf("deviceId = %s, want %s", request.DeviceID, expectedDeviceID)
	}
	if _, err := VerifyDevicePairingRequest(requestJSON, accountTestID(accountTestKey(9)), accountTestNow); err == nil {
		t.Fatal("pairing request was accepted for a different account")
	}
	tampered := strings.Replace(requestJSON, request.Nonce, strings.Repeat("a", len(request.Nonce)), 1)
	if _, err := VerifyDevicePairingRequest(tampered, accountUpeerID, accountTestNow); err == nil {
		t.Fatal("tampered pairing request was accepted")
	}
	if _, err := VerifyDevicePairingRequest(requestJSON, accountUpeerID, request.ExpiresAt+1); err == nil {
		t.Fatal("expired pairing request was accepted")
	}
}

func TestDeviceCertificateBindsDeviceToAccount(t *testing.T) {
	rootKey := accountTestKey(10)
	deviceKey := accountTestKey(11)
	otherDeviceKey := accountTestKey(12)
	accountUpeerID := accountTestID(rootKey)
	certificateJSON := accountTestCertificate(t, rootKey, deviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	certificate, err := VerifyDeviceCertificate(certificateJSON, accountUpeerID, accountTestNow)
	if err != nil {
		t.Fatal(err)
	}
	expectedDeviceID, err := DeriveDeviceID(deviceKey.Public().(ed25519.PublicKey))
	if err != nil {
		t.Fatal(err)
	}
	if certificate.DeviceID != expectedDeviceID {
		t.Fatalf("certificate deviceId = %s, want %s", certificate.DeviceID, expectedDeviceID)
	}
	otherCertificateJSON := accountTestCertificate(t, rootKey, otherDeviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	otherCertificate, err := VerifyDeviceCertificate(otherCertificateJSON, accountUpeerID, accountTestNow)
	if err != nil {
		t.Fatal(err)
	}
	if otherCertificate.DeviceID == certificate.DeviceID {
		t.Fatal("different device keys produced the same deviceId")
	}
	tampered := strings.Replace(certificateJSON, `"platform":"android"`, `"platform":"ios"`, 1)
	if _, err := VerifyDeviceCertificate(tampered, accountUpeerID, accountTestNow); err == nil {
		t.Fatal("tampered device certificate was accepted")
	}
	if _, err := VerifyDeviceCertificate(certificateJSON, accountTestID(accountTestKey(13)), accountTestNow); err == nil {
		t.Fatal("device certificate was accepted for a different account")
	}
	if _, err := VerifyDeviceCertificate(certificateJSON, accountUpeerID, certificate.ExpiresAt+1); err == nil {
		t.Fatal("expired device certificate was accepted")
	}
}

func TestDeviceCertificateRejectsRogueRootAndInvalidWindow(t *testing.T) {
	rootKey := accountTestKey(20)
	rogueRootKey := accountTestKey(21)
	deviceKey := accountTestKey(22)
	accountUpeerID := accountTestID(rootKey)
	rogueCertificate := accountTestCertificate(t, rogueRootKey, deviceKey, accountTestID(rogueRootKey), 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	if _, err := VerifyDeviceCertificate(rogueCertificate, accountUpeerID, accountTestNow); err == nil {
		t.Fatal("device certificate from a rogue root was accepted")
	}
	if _, err := ApproveDevicePairingRequest(rootKey, accountTestPairingRequest(t, deviceKey, accountUpeerID, accountTestNow), -1, accountTestNow, accountTestNow+1000); err == nil {
		t.Fatal("negative certificate epoch was accepted")
	}
	if _, err := ApproveDevicePairingRequest(rootKey, accountTestPairingRequest(t, deviceKey, accountUpeerID, accountTestNow), 7, accountTestNow, accountTestNow-1); err == nil {
		t.Fatal("certificate with an invalid window was accepted")
	}
}

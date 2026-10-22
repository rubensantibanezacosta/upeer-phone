package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"strings"
	"testing"
)

func TestDeviceLocationBlockUsesAccountAndDeviceKeys(t *testing.T) {
	rootKey := accountTestKey(70)
	deviceKey := accountTestKey(71)
	otherDeviceKey := accountTestKey(72)
	accountUpeerID := accountTestID(rootKey)
	certificate := accountTestCertificate(t, rootKey, deviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	registry := accountTestRegistry(t, rootKey, []string{certificate}, nil, 7, accountTestNow)
	blockJSON, err := BuildDeviceLocationBlock(deviceKey, certificate, LocationBlockOptions{
		Addresses: []string{" 200::2 ", "200::1", "200::2"},
		DhtSeq:    9,
		DeviceID:  "",
		NowMillis: accountTestNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	block := decodeTestBlock(t, blockJSON)
	if len(block.DeviceCertificate) == 0 || block.DeviceID == "" || block.DhtSeq != 9 {
		t.Fatalf("block = %#v", block)
	}
	publicKey := hex.EncodeToString(rootKey.Public().(ed25519.PublicKey))
	valid, err := VerifyDeviceLocationBlock(blockJSON, publicKey, registry, accountTestNow)
	if err != nil || !valid {
		t.Fatalf("device block = %v, %v", valid, err)
	}
	valid, err = VerifyLocationBlock(blockJSON, publicKey, accountTestNow)
	if err != nil || !valid {
		t.Fatalf("legacy verifier rejected device block: %v, %v", valid, err)
	}
	tampered := strings.Replace(blockJSON, `"dhtSeq":9`, `"dhtSeq":10`, 1)
	valid, err = VerifyDeviceLocationBlock(tampered, publicKey, registry, accountTestNow)
	if err != nil || valid {
		t.Fatalf("tampered device block = %v, %v", valid, err)
	}
	if _, err := BuildDeviceLocationBlock(otherDeviceKey, certificate, LocationBlockOptions{
		Addresses: []string{"200::1"}, NowMillis: accountTestNow,
	}); err == nil {
		t.Fatal("device certificate was accepted with a different signing key")
	}
}

func TestDeviceLocationBlockRejectsRegistryRevocation(t *testing.T) {
	rootKey := accountTestKey(80)
	deviceKey := accountTestKey(81)
	otherDeviceKey := accountTestKey(82)
	accountUpeerID := accountTestID(rootKey)
	certificate := accountTestCertificate(t, rootKey, deviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	otherCertificate := accountTestCertificate(t, rootKey, otherDeviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+AccountCertificateDefaultTTLMillis)
	registry := accountTestRegistry(t, rootKey, []string{certificate, otherCertificate}, nil, 7, accountTestNow)
	blockJSON, err := BuildDeviceLocationBlock(deviceKey, certificate, LocationBlockOptions{
		Addresses: []string{"200::1"}, NowMillis: accountTestNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	device, err := VerifyDeviceCertificate(certificate, accountUpeerID, accountTestNow)
	if err != nil {
		t.Fatal(err)
	}
	revokedAt := accountTestNow + 1_000
	revokedRegistry, err := RevokeAccountDevice(registry, rootKey, device.DeviceID, "lost device", revokedAt)
	if err != nil {
		t.Fatal(err)
	}
	publicKey := hex.EncodeToString(rootKey.Public().(ed25519.PublicKey))
	valid, err := VerifyDeviceLocationBlock(blockJSON, publicKey, revokedRegistry, revokedAt)
	if err == nil || valid {
		t.Fatalf("device block with revoked certificate = %v, %v", valid, err)
	}
	valid, err = VerifyDeviceLocationBlock(blockJSON, hex.EncodeToString(accountTestKey(83).Public().(ed25519.PublicKey)), registry, accountTestNow)
	if err == nil || valid {
		t.Fatalf("device block with wrong account key = %v, %v", valid, err)
	}
}

func TestDeviceLocationBlockRejectsExpiredCertificate(t *testing.T) {
	rootKey := accountTestKey(90)
	deviceKey := accountTestKey(91)
	accountUpeerID := accountTestID(rootKey)
	certificate := accountTestCertificate(t, rootKey, deviceKey, accountUpeerID, 7, accountTestNow, accountTestNow+60_000)
	if _, err := BuildDeviceLocationBlock(deviceKey, certificate, LocationBlockOptions{
		Addresses: []string{"200::1"}, NowMillis: accountTestNow + 60_000,
	}); err == nil {
		t.Fatal("expired device certificate was accepted for a location block")
	}
}

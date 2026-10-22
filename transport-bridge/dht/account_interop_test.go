package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"strings"
	"testing"
)

type accountInteropVector struct {
	GeneratedBy          string          `json:"generatedBy"`
	Now                  int64           `json:"now"`
	AccountSecretKeySeed string          `json:"accountSecretKeySeed"`
	AccountPublicKey     string          `json:"accountPublicKey"`
	AccountUpeerID       string          `json:"accountUpeerId"`
	DevicePublicKey  string          `json:"devicePublicKey"`
	DeviceID         string          `json:"deviceId"`
	RegistryKey      string          `json:"registryKey"`
	Certificate      json.RawMessage `json:"certificate"`
	Registry         json.RawMessage `json:"registry"`
	LocationBlock    json.RawMessage `json:"locationBlock"`
}

func loadAccountInteropVector(t *testing.T) accountInteropVector {
	t.Helper()
	raw, err := os.ReadFile("../testdata/account_device_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var vector accountInteropVector
	if err := json.Unmarshal(raw, &vector); err != nil {
		t.Fatal(err)
	}
	if vector.GeneratedBy != "upper-desktop" {
		t.Fatalf("unexpected vector generator %q", vector.GeneratedBy)
	}
	return vector
}

func TestDesktopAccountDeviceVector(t *testing.T) {
	vector := loadAccountInteropVector(t)
	key, err := AccountRegistryKey(vector.AccountUpeerID)
	if err != nil {
		t.Fatal(err)
	}
	if key != vector.RegistryKey {
		t.Fatalf("account registry key = %s, want %s", key, vector.RegistryKey)
	}
	certificate, err := VerifyDeviceCertificate(string(vector.Certificate), vector.AccountUpeerID, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	if certificate.DeviceID != vector.DeviceID || certificate.DevicePublicKey != vector.DevicePublicKey {
		t.Fatalf("certificate device = %s/%s", certificate.DeviceID, certificate.DevicePublicKey)
	}
	if _, err := VerifyAccountRegistry(string(vector.Registry), vector.AccountUpeerID, vector.Now); err != nil {
		t.Fatal(err)
	}
	if _, err := ValidateDeviceAdmission(string(vector.Certificate), string(vector.Registry), vector.AccountUpeerID, vector.Now); err != nil {
		t.Fatal(err)
	}
	valid, err := VerifyDeviceLocationBlock(string(vector.LocationBlock), vector.AccountPublicKey, string(vector.Registry), vector.Now)
	if err != nil || !valid {
		t.Fatalf("device location block = %v, %v", valid, err)
	}
	valid, err = VerifyLocationBlock(string(vector.LocationBlock), vector.AccountPublicKey, vector.Now)
	if err != nil || !valid {
		t.Fatalf("location block = %v, %v", valid, err)
	}
}

func TestDesktopAccountDeviceVectorRejectsTampering(t *testing.T) {
	vector := loadAccountInteropVector(t)
	tampered := strings.Replace(string(vector.LocationBlock), `"dhtSeq": 42`, `"dhtSeq": 43`, 1)
	valid, err := VerifyDeviceLocationBlock(tampered, vector.AccountPublicKey, string(vector.Registry), vector.Now)
	if err != nil || valid {
		t.Fatalf("tampered block = %v, %v", valid, err)
	}
	seed, err := hex.DecodeString(vector.AccountSecretKeySeed)
	if err != nil {
		t.Fatal(err)
	}
	revokedAt := vector.Now + 1_000
	revoked, err := RevokeAccountDevice(string(vector.Registry), ed25519.NewKeyFromSeed(seed), vector.DeviceID, "lost", revokedAt)
	if err != nil {
		t.Fatal(err)
	}
	valid, err = VerifyDeviceLocationBlock(string(vector.LocationBlock), vector.AccountPublicKey, revoked, revokedAt)
	if err == nil || valid {
		t.Fatalf("revoked block = %v, %v", valid, err)
	}
}

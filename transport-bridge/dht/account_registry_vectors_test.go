package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

const accountRegistryVectorPath = "../../../upper-desktop/tests/fixtures/account-registry-go.json"

var accountRegistryVectorNow int64 = 1_790_812_800_000

const accountRegistryVectorStaticKey = "6d1c2c3f4a5b6c7d8e9f0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f"

type accountRegistryVector struct {
	GeneratedBy          string   `json:"generatedBy"`
	GeneratedAt          string   `json:"generatedAt"`
	Now                  int64    `json:"now"`
	AccountSecretKeySeed string   `json:"accountSecretKeySeed"`
	AccountPublicKey     string   `json:"accountPublicKey"`
	AccountUpeerID       string   `json:"accountUpeerId"`
	Certificates         []string `json:"certificates"`
	Revoked              []string `json:"revoked"`
	Registry             string   `json:"registry"`
	SignedPayload        string   `json:"signedPayload"`
}

func buildAccountRegistryVector(t *testing.T) []byte {
	t.Helper()
	now := accountRegistryVectorNow
	rootKey := accountTestKey(0x61)
	accountUpeerID := accountTestID(rootKey)

	mobileRequest, err := BuildDevicePairingRequest(accountTestKey(0x62), DevicePairingRequestOptions{
		AccountUpeerID:        accountUpeerID,
		Platform:              "android",
		Capabilities:          []string{"chat", "dht", "vault"},
		StaticX25519PublicKey: accountRegistryVectorStaticKey,
		Nonce:                 hex.EncodeToString(accountTestKey(0x62)[:AccountNonceBytes]),
		CreatedAt:             now,
	})
	if err != nil {
		t.Fatal(err)
	}
	mobileCertificate, err := ApproveDevicePairingRequest(rootKey, mobileRequest, 2, now, now+365*24*60*60*1000)
	if err != nil {
		t.Fatal(err)
	}

	laptopRequest, err := BuildDevicePairingRequest(accountTestKey(0x63), DevicePairingRequestOptions{
		AccountUpeerID: accountUpeerID,
		Platform:       "desktop",
		Nonce:          hex.EncodeToString(accountTestKey(0x63)[:AccountNonceBytes]),
		CreatedAt:      now,
	})
	if err != nil {
		t.Fatal(err)
	}
	laptopCertificate, err := ApproveDevicePairingRequest(rootKey, laptopRequest, 2, now, now+90*24*60*60*1000)
	if err != nil {
		t.Fatal(err)
	}

	retiredDeviceID, err := DeriveDeviceID(accountTestKey(0x64).Public().(ed25519.PublicKey))
	if err != nil {
		t.Fatal(err)
	}
	certificates := []string{mobileCertificate, laptopCertificate}
	revoked := []AccountRevocation{{DeviceID: retiredDeviceID, RevokedAt: now - 60_000, Reason: "dispositivo perdido"}}

	devices := make([]AccountRegistryDevice, 0, len(certificates))
	for _, encoded := range certificates {
		certificate, err := VerifyDeviceCertificate(encoded, accountUpeerID, now)
		if err != nil {
			t.Fatal(err)
		}
		devices = append(devices, AccountRegistryDeviceFromCertificate(certificate))
	}
	registryJSON, err := BuildAccountRegistry(rootKey, AccountRegistryOptions{
		Epoch:     2,
		IssuedAt:  now,
		NowMillis: now,
		Devices:   devices,
		Revoked:   revoked,
	})
	if err != nil {
		t.Fatal(err)
	}
	verified, err := VerifyAccountRegistry(registryJSON, accountUpeerID, now)
	if err != nil {
		t.Fatalf("el registro recién firmado no verifica: %v", err)
	}
	if verified.Devices[0].StaticX25519PublicKey != accountRegistryVectorStaticKey {
		t.Fatalf("el registro perdió la clave estática del móvil: %q", verified.Devices[0].StaticX25519PublicKey)
	}
	payload, err := CanonicalStringify(registrySignedData(verified))
	if err != nil {
		t.Fatal(err)
	}

	vector := accountRegistryVector{
		GeneratedBy:          "upper-transport-bridge",
		GeneratedAt:          time.Now().UTC().Format("2006-01-02"),
		Now:                  now,
		AccountSecretKeySeed: hex.EncodeToString(accountTestKey(0x61)[:ed25519.SeedSize]),
		AccountPublicKey:     accountPublicKeyHex(rootKey.Public().(ed25519.PublicKey)),
		AccountUpeerID:       accountUpeerID,
		Certificates:         certificates,
		Revoked:              []string{retiredDeviceID},
		Registry:             registryJSON,
		SignedPayload:        payload,
	}
	encoded, err := json.MarshalIndent(vector, "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	return append(encoded, '\n')
}

func TestWriteAccountRegistryVector(t *testing.T) {
	if os.Getenv("UP_WRITE_REGISTRY_VECTOR") != "1" {
		t.Skip("define UP_WRITE_REGISTRY_VECTOR=1 para regenerar el vector")
	}
	encoded := buildAccountRegistryVector(t)
	if err := os.MkdirAll(filepath.Dir(accountRegistryVectorPath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(accountRegistryVectorPath, encoded, 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestAccountRegistryVectorMatchesFixture(t *testing.T) {
	encoded := buildAccountRegistryVector(t)
	existing, err := os.ReadFile(accountRegistryVectorPath)
	if err != nil {
		t.Skipf("todavía no hay vector en %s: %v", accountRegistryVectorPath, err)
	}
	if string(existing) != string(encoded) {
		t.Fatal("el vector del registro de cuenta está desactualizado: UP_WRITE_REGISTRY_VECTOR=1 go test -run TestWriteAccountRegistryVector")
	}
}

func TestAccountRegistryVectorRejectsTampering(t *testing.T) {
	var vector accountRegistryVector
	if err := json.Unmarshal(buildAccountRegistryVector(t), &vector); err != nil {
		t.Fatal(err)
	}
	if _, err := VerifyAccountRegistry(vector.Registry, vector.AccountUpeerID, vector.Now); err != nil {
		t.Fatal(err)
	}
	tampered := strings.Replace(vector.Registry, `"epoch":2`, `"epoch":3`, 1)
	if tampered == vector.Registry {
		t.Fatal("el vector no lleva la época esperada")
	}
	if _, err := VerifyAccountRegistry(tampered, vector.AccountUpeerID, vector.Now); err == nil {
		t.Fatal("un registro manipulado no puede verificar")
	}
}

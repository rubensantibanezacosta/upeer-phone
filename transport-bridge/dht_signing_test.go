package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func testCryptoIdentity(t *testing.T) *CryptoIdentity {
	t.Helper()
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	return identity
}

func TestBuildSignedPacketMatchesDesktopValidation(t *testing.T) {
	identity := testCryptoIdentity(t)
	signed, err := identity.BuildSignedPacket("200::1", `{"type":"DHT_PING","queryId":"query-1"}`)
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateSignedPacket([]byte(signed), identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	var metadata signedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "DHT_PING" || metadata.SenderUpeerID != identity.UpeerID() {
		t.Fatalf("metadata = %#v", metadata)
	}
	var packet map[string]any
	if err := json.Unmarshal([]byte(signed), &packet); err != nil {
		t.Fatal(err)
	}
	if packet["senderYggAddress"] != "200::1" || packet["isInternalSync"] != false {
		t.Fatalf("packet = %#v", packet)
	}
	if _, exists := packet["senderUpeerId"]; !exists {
		t.Fatal("signed packet is missing senderUpeerId")
	}
}

func TestBuildSignedPacketRejectsReservedAndInvalidInput(t *testing.T) {
	identity := testCryptoIdentity(t)
	if _, err := identity.BuildSignedPacket("200::1", `{"type":"DHT_PING","signature":"00"}`); err == nil {
		t.Fatal("signed packet accepted a caller-provided signature")
	}
	if _, err := identity.BuildSignedPacket("200::1", `{"type":"DHT_PING","senderUpeerId":"00"}`); err == nil {
		t.Fatal("signed packet accepted a caller-provided senderUpeerId")
	}
	if _, err := identity.BuildSignedPacket("not-an-address", `{"type":"DHT_PING"}`); err == nil {
		t.Fatal("signed packet accepted an invalid Yggdrasil address")
	}
	if _, err := identity.BuildSignedPacket("200::1", `{"queryId":"query-1"}`); err == nil {
		t.Fatal("signed packet accepted a missing type")
	}
}

func TestSignAndVerifyCanonicalJSON(t *testing.T) {
	identity := testCryptoIdentity(t)
	payload := `{"zeta":2,"alpha":{"b":true,"a":"á"},"list":[3,1,2]}`
	signature, err := identity.SignCanonicalJSON(payload)
	if err != nil {
		t.Fatal(err)
	}
	valid, err := VerifyCanonicalJSON(payload, signature, identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	if !valid {
		t.Fatal("canonical signature did not verify")
	}
	valid, err = VerifyCanonicalJSON(strings.Replace(payload, `"zeta":2`, `"zeta":3`, 1), signature, identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	if valid {
		t.Fatal("tampered canonical payload verified")
	}
}

func TestLocationBlockRoundTripAndTamperDetection(t *testing.T) {
	identity := testCryptoIdentity(t)
	block, err := identity.BuildLocationBlock(`["200::2","200::1","200::1"]`, 7, 0, "device-1")
	if err != nil {
		t.Fatal(err)
	}
	var decoded dht.LocationBlock
	if err := json.Unmarshal([]byte(block), &decoded); err != nil {
		t.Fatal(err)
	}
	if decoded.Address != "200::1" || len(decoded.Addresses) != 2 {
		t.Fatalf("block = %#v", decoded)
	}
	valid, err := VerifyLocationBlock(block, identity.PublicKeyHex(), time.Now().UnixMilli())
	if err != nil {
		t.Fatal(err)
	}
	if !valid {
		t.Fatal("location block did not verify")
	}
	tampered := strings.Replace(block, `"dhtSeq":7`, `"dhtSeq":8`, 1)
	valid, err = VerifyLocationBlock(tampered, identity.PublicKeyHex(), time.Now().UnixMilli())
	if err != nil {
		t.Fatal(err)
	}
	if valid {
		t.Fatal("tampered location block verified")
	}
	valid, err = VerifyLocationBlock(block, identity.PublicKeyHex(), time.Now().Add(31*24*time.Hour).UnixMilli())
	if err == nil {
		t.Fatal("expired location block did not report an error")
	}
	if valid {
		t.Fatal("expired location block verified")
	}
}

func TestLocationBlockRejectsInvalidFields(t *testing.T) {
	identity := testCryptoIdentity(t)
	if _, err := identity.BuildLocationBlock(`["200::1"]`, -1, 0, ""); err == nil {
		t.Fatal("location block accepted a negative dhtSeq")
	}
	if _, err := identity.BuildLocationBlock(`["not-an-address"]`, 1, 0, ""); err == nil {
		t.Fatal("location block accepted an invalid address")
	}
	if _, err := identity.BuildLocationBlock(`[]`, 1, 0, ""); err == nil {
		t.Fatal("location block accepted an empty address list")
	}
}

const accountBridgeNow int64 = 1_790_000_000_000

func accountBridgeKey(seed byte) ed25519.PrivateKey {
	bytes := make([]byte, ed25519.SeedSize)
	for index := range bytes {
		bytes[index] = seed
	}
	return ed25519.NewKeyFromSeed(bytes)
}

func TestAccountBridgePairingRegistryAndLocation(t *testing.T) {
	rootSecret := []byte(accountBridgeKey(1))
	deviceSecret := []byte(accountBridgeKey(2))
	root, err := NewCryptoIdentity(rootSecret)
	if err != nil {
		t.Fatal(err)
	}
	device, err := NewCryptoIdentity(deviceSecret)
	if err != nil {
		t.Fatal(err)
	}
	optionsJSON, err := json.Marshal(dht.DevicePairingRequestOptions{
		AccountUpeerID: root.UpeerID(),
		Platform:       "android",
		Capabilities:   []string{"chat", "dht"},
		Nonce:          hex.EncodeToString(deviceSecret[:dht.AccountNonceBytes]),
		CreatedAt:      accountBridgeNow,
	})
	if err != nil {
		t.Fatal(err)
	}
	requestJSON, err := device.BuildDevicePairingRequest(string(optionsJSON))
	if err != nil {
		t.Fatal(err)
	}
	if valid, err := VerifyDevicePairingRequest(requestJSON, root.UpeerID(), accountBridgeNow); err != nil || !valid {
		t.Fatalf("pairing request = %v, %v", valid, err)
	}
	certificateJSON, err := root.ApproveDevicePairingRequest(requestJSON, 7, accountBridgeNow, accountBridgeNow+dht.AccountCertificateDefaultTTLMillis)
	if err != nil {
		t.Fatal(err)
	}
	if valid, err := VerifyDeviceCertificate(certificateJSON, root.UpeerID(), accountBridgeNow); err != nil || !valid {
		t.Fatalf("device certificate = %v, %v", valid, err)
	}
	certificatesJSON, err := json.Marshal([]string{certificateJSON})
	if err != nil {
		t.Fatal(err)
	}
	registryJSON, err := root.BuildAccountRegistryFromCertificates(string(certificatesJSON), "[]", 7, accountBridgeNow, 0)
	if err != nil {
		t.Fatal(err)
	}
	if valid, err := VerifyAccountRegistry(registryJSON, root.UpeerID(), accountBridgeNow); err != nil || !valid {
		t.Fatalf("account registry = %v, %v", valid, err)
	}
	if valid, err := ValidateDeviceAdmission(certificateJSON, registryJSON, root.UpeerID(), accountBridgeNow); err != nil || !valid {
		t.Fatalf("device admission = %v, %v", valid, err)
	}
	blockJSON, err := device.BuildDeviceLocationBlock(certificateJSON, `["200::1"]`, 1, 0, accountBridgeNow)
	if err != nil {
		t.Fatal(err)
	}
	publicKey := hex.EncodeToString(accountBridgeKey(1).Public().(ed25519.PublicKey))
	if valid, err := VerifyDeviceLocationBlock(blockJSON, publicKey, registryJSON, accountBridgeNow); err != nil || !valid {
		t.Fatalf("device location block = %v, %v", valid, err)
	}
	deviceID, err := DeriveDeviceID(ed25519.PrivateKey(deviceSecret).Public().(ed25519.PublicKey))
	if err != nil {
		t.Fatal(err)
	}
	revokedJSON, err := root.RevokeAccountDevice(registryJSON, deviceID, "lost device", accountBridgeNow+1_000)
	if err != nil {
		t.Fatal(err)
	}
	if valid, err := ValidateDeviceAdmission(certificateJSON, revokedJSON, root.UpeerID(), accountBridgeNow+1_000); err == nil || valid {
		t.Fatalf("revoked admission = %v, %v", valid, err)
	}
	nextEpoch, err := NextAccountRegistryEpoch(registryJSON)
	if err != nil || nextEpoch != 8 {
		t.Fatalf("next epoch = %d, %v", nextEpoch, err)
	}
	key, err := AccountRegistryKey(root.UpeerID())
	if err != nil || key == "" {
		t.Fatalf("registry key = %q, %v", key, err)
	}
}

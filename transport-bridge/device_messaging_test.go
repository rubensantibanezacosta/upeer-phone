package transportbridge

import (
	"crypto/ed25519"
	"encoding/json"
	"testing"
	"time"
)

type deviceTestIdentity struct {
	account     *CryptoIdentity
	device      *CryptoIdentity
	certificate json.RawMessage
	deviceID    string
}

func newDeviceTestIdentity(t *testing.T, accountSeed, deviceSeed byte) deviceTestIdentity {
	t.Helper()
	account := newSeedIdentity(t, accountSeed)
	device := newSeedIdentity(t, deviceSeed)
	now := time.Now().UnixMilli()
	options, err := json.Marshal(map[string]any{
		"accountUpeerId": account.UpeerID(),
		"platform":       "android",
		"capabilities":   []string{"chat", "dht"},
		"createdAt":      now,
	})
	if err != nil {
		t.Fatal(err)
	}
	request, err := device.BuildDevicePairingRequest(string(options))
	if err != nil {
		t.Fatal(err)
	}
	certificate, err := account.ApproveDevicePairingRequest(request, 1, now, now+24*60*60*1000)
	if err != nil {
		t.Fatal(err)
	}
	var decoded struct {
		DeviceID string `json:"deviceId"`
	}
	if err := json.Unmarshal([]byte(certificate), &decoded); err != nil {
		t.Fatal(err)
	}
	return deviceTestIdentity{
		account:     account,
		device:      device,
		certificate: json.RawMessage(certificate),
		deviceID:    decoded.DeviceID,
	}
}

func newSeedIdentity(t *testing.T, seed byte) *CryptoIdentity {
	t.Helper()
	seedBytes := make([]byte, ed25519.SeedSize)
	for index := range seedBytes {
		seedBytes[index] = seed
	}
	identity, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seedBytes))
	if err != nil {
		t.Fatal(err)
	}
	return identity
}

func deviceTestFields(t *testing.T, identity deviceTestIdentity, spk signedPreKeyState) []byte {
	t.Helper()
	fields, err := json.Marshal(map[string]any{
		"devicePublicKey":   identity.device.PublicKeyHex(),
		"deviceId":          identity.deviceID,
		"deviceCertificate": identity.certificate,
		"deviceSignedPreKey": map[string]any{
			"spkPub": spk.SPKPub,
			"spkSig": spk.SPKSig,
			"spkId":  spk.SPKID,
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	return fields
}

func TestDeviceHandshakeCarriesValidatedFields(t *testing.T) {
	alice := newDeviceTestIdentity(t, 1, 2)
	accountSPKJSON, err := alice.account.CreateSignedPreKey(7001)
	if err != nil {
		t.Fatal(err)
	}
	deviceSPKJSON, err := alice.device.CreateSignedPreKey(7002)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	request, err := alice.account.BuildHandshakeRequestWithDevice("200::1", accountSPKJSON, "", string(deviceTestFields(t, alice, deviceSPK)))
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := validateControlFrameFromPayload([]byte(request))
	if err != nil {
		t.Fatal(err)
	}
	var metadata controlMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.SenderUpeerID != alice.account.UpeerID() {
		t.Fatalf("handshake sender = %s", metadata.SenderUpeerID)
	}
	var packet map[string]any
	if err := json.Unmarshal([]byte(request), &packet); err != nil {
		t.Fatal(err)
	}
	if packet["deviceId"] != alice.deviceID || packet["devicePublicKey"] != alice.device.PublicKeyHex() {
		t.Fatalf("device handshake fields = %#v", packet)
	}
	if !VerifySignedPreKey(alice.device.PublicKeyHex(), deviceSPK.SPKPub, deviceSPK.SPKSig) {
		t.Fatal("device signed pre-key did not verify")
	}
	if VerifySignedPreKey(alice.account.PublicKeyHex(), deviceSPK.SPKPub, deviceSPK.SPKSig) {
		t.Fatal("device signed pre-key verified with the account key")
	}
}

func TestDeviceHandshakeRejectsForeignCertificate(t *testing.T) {
	alice := newDeviceTestIdentity(t, 3, 4)
	mallory := newDeviceTestIdentity(t, 5, 6)
	accountSPKJSON, err := alice.account.CreateSignedPreKey(7001)
	if err != nil {
		t.Fatal(err)
	}
	deviceSPKJSON, err := mallory.device.CreateSignedPreKey(7002)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	fields, err := json.Marshal(map[string]any{
		"devicePublicKey":   mallory.device.PublicKeyHex(),
		"deviceId":          mallory.deviceID,
		"deviceCertificate": mallory.certificate,
		"deviceSignedPreKey": map[string]any{
			"spkPub": deviceSPK.SPKPub,
			"spkSig": deviceSPK.SPKSig,
			"spkId":  deviceSPK.SPKID,
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := alice.account.BuildHandshakeRequestWithDevice("200::1", accountSPKJSON, "", string(fields)); err == nil {
		t.Fatal("handshake accepted a certificate from another account")
	}
}

func TestDeviceHandshakeRejectsAccountSignedDeviceSpk(t *testing.T) {
	alice := newDeviceTestIdentity(t, 11, 12)
	accountSPKJSON, err := alice.account.CreateSignedPreKey(7003)
	if err != nil {
		t.Fatal(err)
	}
	var accountSPK signedPreKeyState
	if err := json.Unmarshal([]byte(accountSPKJSON), &accountSPK); err != nil {
		t.Fatal(err)
	}
	fields, err := json.Marshal(map[string]any{
		"devicePublicKey":   alice.device.PublicKeyHex(),
		"deviceId":          alice.deviceID,
		"deviceCertificate": alice.certificate,
		"deviceSignedPreKey": map[string]any{
			"spkPub": accountSPK.SPKPub,
			"spkSig": accountSPK.SPKSig,
			"spkId":  accountSPK.SPKID,
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := alice.account.BuildHandshakeRequestWithDevice("200::1", accountSPKJSON, "", string(fields)); err == nil {
		t.Fatal("handshake accepted a device SPK signed by the account key")
	}
}

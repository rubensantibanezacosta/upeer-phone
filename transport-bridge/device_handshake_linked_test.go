package transportbridge

import (
	"encoding/json"
	"testing"
)

func linkedHandshakeRequest(t *testing.T, identity deviceTestIdentity, address string) string {
	t.Helper()
	deviceSPKJSON, err := identity.device.CreateSignedPreKey(9101)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	request, err := identity.device.BuildLinkedHandshakeRequest(
		identity.account.PublicKeyHex(),
		identity.account.UpeerID(),
		address,
		"móvil",
		"",
		string(deviceTestFields(t, identity, deviceSPK)),
	)
	if err != nil {
		t.Fatal(err)
	}
	return request
}

func TestLinkedHandshakeRequestValidatesWithDeviceSignature(t *testing.T) {
	alice := newDeviceTestIdentity(t, 11, 12)
	request := linkedHandshakeRequest(t, alice, "200::7")

	metadataJSON, err := validateControlFrameWithDeviceSignature([]byte(request))
	if err != nil {
		t.Fatalf("linked handshake did not validate: %v", err)
	}
	var metadata controlMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.SenderUpeerID != alice.account.UpeerID() {
		t.Fatalf("sender = %s", metadata.SenderUpeerID)
	}
	if metadata.PublicKey != alice.account.PublicKeyHex() {
		t.Fatalf("publicKey = %s", metadata.PublicKey)
	}

	var packet map[string]any
	if err := json.Unmarshal([]byte(request), &packet); err != nil {
		t.Fatal(err)
	}
	if packet["deviceId"] != alice.deviceID {
		t.Fatalf("deviceId = %v", packet["deviceId"])
	}
	if _, ok := packet["signedPreKey"]; ok {
		t.Fatal("linked handshake must not carry an account signed pre-key")
	}
	if verifyControlSignature(mustHex(t, alice.account.PublicKeyHex()), mustHex(t, packet["signature"].(string)), signedControlPayload(packet), alice.account.UpeerID(), "200::7", handshakeRequest) {
		t.Fatal("linked handshake verified with the account key")
	}
}

func TestLinkedHandshakeRejectsDeviceOfAnotherAccount(t *testing.T) {
	alice := newDeviceTestIdentity(t, 13, 14)
	mallory := newDeviceTestIdentity(t, 15, 16)
	deviceSPKJSON, err := mallory.device.CreateSignedPreKey(9201)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	request, err := mallory.device.BuildLinkedHandshakeRequest(
		alice.account.PublicKeyHex(),
		alice.account.UpeerID(),
		"200::8",
		"",
		"",
		string(deviceTestFields(t, mallory, deviceSPK)),
	)
	if err == nil {
		t.Fatalf("pending handshake with a foreign certificate was accepted: %s", request)
	}
}

func TestLinkedHandshakeRejectsSignatureFromAnotherKey(t *testing.T) {
	alice := newDeviceTestIdentity(t, 17, 18)
	request := linkedHandshakeRequest(t, alice, "200::9")

	var packet map[string]any
	if err := json.Unmarshal([]byte(request), &packet); err != nil {
		t.Fatal(err)
	}
	packet["alias"] = "suplantado"
	tampered, err := json.Marshal(packet)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := validateControlFrameWithDeviceSignature(tampered); err == nil {
		t.Fatal("tampered linked handshake was accepted")
	}
}

func TestRootHandshakeStillValidates(t *testing.T) {
	alice := newDeviceTestIdentity(t, 19, 20)
	accountSPKJSON, err := alice.account.CreateSignedPreKey(9301)
	if err != nil {
		t.Fatal(err)
	}
	request, err := alice.account.BuildHandshakeAccept("200::a", accountSPKJSON, "")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := validateControlFrameWithDeviceSignature([]byte(request)); err != nil {
		t.Fatalf("root handshake stopped validating: %v", err)
	}
}

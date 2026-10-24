package transportbridge

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestHandshakeRequestCarriesProfileAliasAndAvatar(t *testing.T) {
	alice := newDeviceTestIdentity(t, 3, 4)
	accountSPKJSON, err := alice.account.CreateSignedPreKey(7101)
	if err != nil {
		t.Fatal(err)
	}
	deviceSPKJSON, err := alice.device.CreateSignedPreKey(7102)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	avatar := "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA=="
	request, err := alice.account.BuildHandshakeRequestWithProfile(
		"200::1",
		accountSPKJSON,
		"Móvil de prueba",
		avatar,
		string(deviceTestFields(t, alice, deviceSPK)),
	)
	if err != nil {
		t.Fatal(err)
	}
	var packet map[string]any
	if err := json.Unmarshal([]byte(request), &packet); err != nil {
		t.Fatal(err)
	}
	if packet["alias"] != "Móvil de prueba" {
		t.Fatalf("alias = %v", packet["alias"])
	}
	if packet["avatar"] != avatar {
		t.Fatalf("avatar = %v", packet["avatar"])
	}
	if _, err := validateControlFrameFromPayload([]byte(request)); err != nil {
		t.Fatalf("profile handshake did not validate: %v", err)
	}
}

func TestHandshakeRequestRejectsInvalidAvatar(t *testing.T) {
	alice := newDeviceTestIdentity(t, 5, 6)
	accountSPKJSON, err := alice.account.CreateSignedPreKey(7201)
	if err != nil {
		t.Fatal(err)
	}
	deviceSPKJSON, err := alice.device.CreateSignedPreKey(7202)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	fields := string(deviceTestFields(t, alice, deviceSPK))
	if _, err := alice.account.BuildHandshakeRequestWithProfile("200::1", accountSPKJSON, "ana", "https://example.com/a.png", fields); err == nil {
		t.Fatal("non data URI avatar was accepted")
	}
	oversized := "data:image/jpeg;base64," + strings.Repeat("A", maxHandshakeAvatarBytes)
	if _, err := alice.account.BuildHandshakeRequestWithProfile("200::1", accountSPKJSON, "ana", oversized, fields); err == nil {
		t.Fatal("oversized avatar was accepted")
	}
}

func TestSignedPongValidatesAsSignedPacket(t *testing.T) {
	alice := newDeviceTestIdentity(t, 7, 8)
	pong, err := alice.account.BuildSignedPacket("200::1", `{"type":"PONG","isInternalSync":false}`)
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateSignedPacket([]byte(pong), alice.account.PublicKeyHex())
	if err != nil {
		t.Fatalf("pong did not validate: %v", err)
	}
	var metadata controlMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "PONG" || metadata.SenderUpeerID != alice.account.UpeerID() {
		t.Fatalf("pong metadata = %#v", metadata)
	}
}

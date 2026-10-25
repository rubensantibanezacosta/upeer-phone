package transportbridge

import (
	"encoding/json"
	"testing"
)

func TestBuildLinkedControlSignsWithTheDeviceKey(t *testing.T) {
	alice := newDeviceTestIdentity(t, 21, 22)
	packet, err := alice.device.BuildLinkedControl(
		alice.deviceID,
		alice.account.UpeerID(),
		"200::1",
		`{"type":"TYPING"}`,
	)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(packet), alice.device.PublicKeyHex(), alice.account.UpeerID()); err != nil {
		t.Fatalf("linked control did not verify with the device key: %v", err)
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(packet), alice.account.PublicKeyHex(), alice.account.UpeerID()); err == nil {
		t.Fatal("linked control verified with the account key")
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(packet), alice.device.PublicKeyHex(), "aa"+alice.account.UpeerID()[2:]); err == nil {
		t.Fatal("linked control verified with a different account id")
	}
	var inner map[string]any
	if err := json.Unmarshal([]byte(packet), &inner); err != nil {
		t.Fatal(err)
	}
	if inner["senderDeviceId"] != alice.deviceID || inner["deviceId"] != alice.deviceID {
		t.Fatalf("linked control device fields = %#v", inner)
	}
	if inner["type"] != "TYPING" {
		t.Fatalf("linked control type = %#v", inner["type"])
	}
}

func TestBuildLinkedControlRejectsAStaleDeviceID(t *testing.T) {
	alice := newDeviceTestIdentity(t, 23, 24)
	other := newDeviceTestIdentity(t, 25, 26)
	if _, err := alice.device.BuildLinkedControl(other.deviceID, alice.account.UpeerID(), "200::1", `{"type":"TYPING"}`); err == nil {
		t.Fatal("linked control accepted a device id that does not match the signing key")
	}
}

func TestBuildLinkedControlRejectsInvalidIdentities(t *testing.T) {
	alice := newDeviceTestIdentity(t, 27, 28)
	cases := []struct{ device, account, address, packet string }{
		{"zz", alice.account.UpeerID(), "200::1", `{"type":"TYPING"}`},
		{alice.deviceID, "zz", "200::1", `{"type":"TYPING"}`},
		{alice.deviceID, alice.account.UpeerID(), "10.0.0.1", `{"type":"TYPING"}`},
		{alice.deviceID, alice.account.UpeerID(), "200::1", `not json`},
		{alice.deviceID, alice.account.UpeerID(), "200::1", `{"type":""}`},
		{alice.deviceID, alice.account.UpeerID(), "200::1", `{"id":"x"}`},
	}
	for index, candidate := range cases {
		if _, err := alice.device.BuildLinkedControl(candidate.device, candidate.account, candidate.address, candidate.packet); err == nil {
			t.Fatalf("case %d accepted invalid linked control input", index)
		}
	}
}

func TestBuildLinkedControlRejectsReservedFields(t *testing.T) {
	alice := newDeviceTestIdentity(t, 29, 30)
	for _, field := range []string{"signature", "senderUpeerId", "senderYggAddress"} {
		packet := `{"type":"TYPING","` + field + `":"200::9"}`
		if _, err := alice.device.BuildLinkedControl(alice.deviceID, alice.account.UpeerID(), "200::1", packet); err == nil {
			t.Fatalf("linked control accepted a caller-provided %s", field)
		}
	}
}

func TestBuildLinkedControlRejectsTheAccountKeyForADeviceID(t *testing.T) {
	alice := newDeviceTestIdentity(t, 29, 30)
	if _, err := alice.account.BuildLinkedControl(alice.deviceID, alice.account.UpeerID(), "200::1", `{"type":"TYPING"}`); err == nil {
		t.Fatal("linked control accepted the account key for a device id")
	}
}

func TestBuildLinkedAck(t *testing.T) {
	alice := newDeviceTestIdentity(t, 31, 32)
	packet, err := alice.device.BuildLinkedAck(alice.deviceID, alice.account.UpeerID(), "200::1", "8e6b1c9a-6f4f-4a5e-9c1a-4f2d7a2b3c4d", "delivered")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(packet), alice.device.PublicKeyHex(), alice.account.UpeerID()); err != nil {
		t.Fatalf("linked ack did not verify with the device key: %v", err)
	}
	var inner map[string]any
	if err := json.Unmarshal([]byte(packet), &inner); err != nil {
		t.Fatal(err)
	}
	if inner["type"] != "ACK" || inner["status"] != "delivered" || inner["senderDeviceId"] != alice.deviceID {
		t.Fatalf("linked ack fields = %#v", inner)
	}
	if inner["isInternalSync"] != false {
		t.Fatalf("linked ack isInternalSync = %#v", inner["isInternalSync"])
	}
}

func TestBuildLinkedAckValidatesStatusAndID(t *testing.T) {
	alice := newDeviceTestIdentity(t, 33, 34)
	if _, err := alice.device.BuildLinkedAck(alice.deviceID, alice.account.UpeerID(), "200::1", "", "delivered"); err == nil {
		t.Fatal("linked ack accepted an empty message id")
	}
	if _, err := alice.device.BuildLinkedAck(alice.deviceID, alice.account.UpeerID(), "200::1", "id", "sent"); err == nil {
		t.Fatal("linked ack accepted an unsupported status")
	}
}

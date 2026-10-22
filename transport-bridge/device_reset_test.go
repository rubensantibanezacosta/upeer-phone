package transportbridge

import (
	"encoding/json"
	"testing"
)

func TestBuildLinkedDrResetUsesDeviceSignatureAndSignedPreKey(t *testing.T) {
	alice := newDeviceTestIdentity(t, 21, 22)
	deviceSPKJSON, err := alice.device.CreateSignedPreKey(8101)
	if err != nil {
		t.Fatal(err)
	}
	var deviceSPK signedPreKeyState
	if err := json.Unmarshal([]byte(deviceSPKJSON), &deviceSPK); err != nil {
		t.Fatal(err)
	}
	resetJSON, err := alice.device.BuildLinkedDrReset(
		alice.account.UpeerID(),
		"200::1",
		deviceSPKJSON,
		"direct",
		alice.deviceID,
	)
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateLinkedSignedPacket(
		[]byte(resetJSON),
		alice.device.PublicKeyHex(),
		alice.account.UpeerID(),
	)
	if err != nil {
		t.Fatal(err)
	}
	var metadata signedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "DR_RESET" || metadata.SenderUpeerID != alice.account.UpeerID() {
		t.Fatalf("linked reset metadata = %#v", metadata)
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket(
		[]byte(resetJSON),
		alice.account.PublicKeyHex(),
		alice.account.UpeerID(),
	); err == nil {
		t.Fatal("linked reset verified with the account key")
	}
	packet, err := decodeControlPacket([]byte(resetJSON))
	if err != nil {
		t.Fatal(err)
	}
	if packet["senderDeviceId"] != alice.deviceID || packet["ratchetContext"] != "direct" {
		t.Fatalf("linked reset fields = %#v", packet)
	}
	signedPreKey, ok := packet["signedPreKey"].(map[string]any)
	if !ok {
		t.Fatalf("linked reset signedPreKey = %#v", packet["signedPreKey"])
	}
	if signedPreKey["spkPub"] != deviceSPK.SPKPub || signedPreKey["spkId"].(json.Number).String() != "8101" {
		t.Fatalf("linked reset signedPreKey = %#v", signedPreKey)
	}
	if _, leaked := signedPreKey["spkSk"]; leaked {
		t.Fatal("linked reset leaked the device signed pre-key secret")
	}
	if !VerifySignedPreKey(alice.device.PublicKeyHex(), deviceSPK.SPKPub, deviceSPK.SPKSig) {
		t.Fatal("device signed pre-key did not verify")
	}
	if VerifySignedPreKey(alice.account.PublicKeyHex(), deviceSPK.SPKPub, deviceSPK.SPKSig) {
		t.Fatal("device signed pre-key verified with the account key")
	}
}

func TestBuildLinkedDrResetRejectsMismatchedDeviceAndAccountSignedSpk(t *testing.T) {
	alice := newDeviceTestIdentity(t, 23, 24)
	bob := newDeviceTestIdentity(t, 25, 26)
	deviceSPKJSON, err := alice.device.CreateSignedPreKey(8102)
	if err != nil {
		t.Fatal(err)
	}
	accountSPKJSON, err := alice.account.CreateSignedPreKey(8103)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := alice.device.BuildLinkedDrReset(
		alice.account.UpeerID(),
		"200::1",
		deviceSPKJSON,
		"direct",
		bob.deviceID,
	); err == nil {
		t.Fatal("linked reset accepted a foreign device id")
	}
	if _, err := alice.device.BuildLinkedDrReset(
		alice.account.UpeerID(),
		"200::1",
		accountSPKJSON,
		"direct",
		alice.deviceID,
	); err == nil {
		t.Fatal("linked reset accepted a device SPK signed by the account key")
	}
	if _, err := alice.device.BuildLinkedDrReset(
		"not-an-account",
		"200::1",
		deviceSPKJSON,
		"direct",
		alice.deviceID,
	); err == nil {
		t.Fatal("linked reset accepted an invalid account id")
	}
}

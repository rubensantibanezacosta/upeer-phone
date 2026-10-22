package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"testing"
)

func TestBuildLinkedChatUsesDeviceSignatureAndIdentity(t *testing.T) {
	alice := newDeviceTestIdentity(t, 7, 8)
	bob := newDeviceTestIdentity(t, 9, 10)
	bobSPKJSON, err := bob.device.CreateSignedPreKey(7003)
	if err != nil {
		t.Fatal(err)
	}
	var bobSPK signedPreKeyState
	if err := json.Unmarshal([]byte(bobSPKJSON), &bobSPK); err != nil {
		t.Fatal(err)
	}
	outboundJSON, err := alice.device.BuildLinkedChat(
		alice.account.UpeerID(),
		"200::1",
		bob.device.PublicKeyHex(),
		bobSPK.SPKPub,
		"",
		"hola desde android",
		"8e6b1c9a-6f4f-4a5e-9c1a-4f2d7a2b3c4d",
		"",
		7003,
		1700000001000,
		alice.deviceID,
	)
	if err != nil {
		t.Fatal(err)
	}
	var outbound outboundChatResult
	if err := json.Unmarshal([]byte(outboundJSON), &outbound); err != nil {
		t.Fatal(err)
	}
	var outer map[string]any
	if err := json.Unmarshal([]byte(outbound.Packet), &outer); err != nil {
		t.Fatal(err)
	}
	plaintext, err := bob.device.OpenSealed(outer["ciphertext"].(string))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(plaintext), alice.device.PublicKeyHex(), alice.account.UpeerID()); err != nil {
		t.Fatal(err)
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(plaintext), alice.account.PublicKeyHex(), alice.account.UpeerID()); err == nil {
		t.Fatal("linked chat verified with the account key")
	}
	if _, err := (&Bridge{}).ValidateLinkedSignedPacket([]byte(plaintext), alice.device.PublicKeyHex(), bob.account.UpeerID()); err == nil {
		t.Fatal("linked chat verified with a different account id")
	}
	inner, err := decodeControlPacket([]byte(plaintext))
	if err != nil {
		t.Fatal(err)
	}
	if inner["senderUpeerId"] != alice.account.UpeerID() || inner["senderDeviceId"] != alice.deviceID {
		t.Fatalf("linked chat fields = %#v", inner)
	}
	initJSON, err := json.Marshal(inner["x3dhInit"])
	if err != nil {
		t.Fatal(err)
	}
	session, err := bob.device.AcceptInboundSession(alice.device.PublicKeyHex(), string(initJSON), bobSPK.SPKSk)
	if err != nil {
		t.Fatal(err)
	}
	headerJSON, err := json.Marshal(inner["ratchetHeader"])
	if err != nil {
		t.Fatal(err)
	}
	messageJSON, err := json.Marshal(map[string]any{
		"ratchetHeader": json.RawMessage(headerJSON),
		"content":       inner["content"],
		"nonce":         inner["nonce"],
	})
	if err != nil {
		t.Fatal(err)
	}
	resultJSON, err := bob.device.RatchetDecrypt(session, string(messageJSON))
	if err != nil {
		t.Fatal(err)
	}
	var result ratchetDecryptResult
	if err := json.Unmarshal([]byte(resultJSON), &result); err != nil {
		t.Fatal(err)
	}
	decoded, err := hex.DecodeString(result.PlaintextHex)
	if err != nil {
		t.Fatal(err)
	}
	if string(decoded) != "hola desde android" {
		t.Fatalf("linked plaintext = %q", string(decoded))
	}
}

package transportbridge

import (
	"encoding/json"
	"testing"
)

func TestBuildDrResetRotatesSignedPreKeyAndStartsFreshSession(t *testing.T) {
	vectors := loadCryptoVectors(t)
	desktop, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	mobile, err := NewCryptoIdentity(mustHex(t, vectors.BobSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	oldJSON, err := desktop.CreateSignedPreKey(100)
	if err != nil {
		t.Fatal(err)
	}
	newJSON, err := desktop.CreateSignedPreKey(200)
	if err != nil {
		t.Fatal(err)
	}
	var oldSPK, newSPK signedPreKeyState
	if err := json.Unmarshal([]byte(oldJSON), &oldSPK); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal([]byte(newJSON), &newSPK); err != nil {
		t.Fatal(err)
	}
	initialJSON, err := mobile.BuildChat(
		"200::3",
		desktop.PublicKeyHex(),
		oldSPK.SPKPub,
		"",
		"antes del reset",
		"message-before-reset",
		"",
		oldSPK.SPKID,
		1700000000000,
	)
	if err != nil {
		t.Fatal(err)
	}
	var initial outboundChatResult
	if err := json.Unmarshal([]byte(initialJSON), &initial); err != nil {
		t.Fatal(err)
	}
	initialInner := openAndValidateChat(t, desktop, mobile, initial.Packet)
	desktopSession := acceptChatSession(t, desktop, mobile, initialInner, oldSPK.SPKSk)
	if _, desktopSession = decryptChatContent(t, desktop, desktopSession, initialInner); desktopSession == "" {
		t.Fatal("initial session was not persisted")
	}

	resetJSON, err := desktop.BuildDrReset("200::3", newJSON, "direct")
	if err != nil {
		t.Fatal(err)
	}
	metadataJSON, err := (&Bridge{}).ValidateSignedPacket([]byte(resetJSON), desktop.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	var metadata signedPacketMetadata
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		t.Fatal(err)
	}
	if metadata.Type != "DR_RESET" || metadata.SenderUpeerID != desktop.UpeerID() {
		t.Fatalf("reset metadata = %#v", metadata)
	}
	resetPacket, err := decodeControlPacket([]byte(resetJSON))
	if err != nil {
		t.Fatal(err)
	}
	resetSPK, ok := resetPacket["signedPreKey"].(map[string]any)
	if !ok {
		t.Fatalf("reset signedPreKey = %#v", resetPacket["signedPreKey"])
	}
	if resetSPK["spkId"].(json.Number).String() != "200" || resetSPK["spkPub"] != newSPK.SPKPub {
		t.Fatalf("reset signedPreKey = %#v", resetSPK)
	}
	if _, leaked := resetSPK["spkSk"]; leaked {
		t.Fatal("reset leaked the signed pre-key secret")
	}
	if resetPacket["ratchetContext"] != "direct" {
		t.Fatalf("reset context = %#v", resetPacket["ratchetContext"])
	}

	freshJSON, err := mobile.BuildChat(
		"200::3",
		desktop.PublicKeyHex(),
		newSPK.SPKPub,
		"",
		"después del reset",
		"message-after-reset",
		"",
		newSPK.SPKID,
		1700000001000,
	)
	if err != nil {
		t.Fatal(err)
	}
	var fresh outboundChatResult
	if err := json.Unmarshal([]byte(freshJSON), &fresh); err != nil {
		t.Fatal(err)
	}
	freshInner := openAndValidateChat(t, desktop, mobile, fresh.Packet)
	init, ok := freshInner["x3dhInit"].(map[string]any)
	if !ok {
		t.Fatal("fresh chat is missing x3dhInit")
	}
	if init["spkId"].(json.Number).String() != "200" {
		t.Fatalf("fresh x3dh spkId = %#v", init["spkId"])
	}
	freshSession := acceptChatSession(t, desktop, mobile, freshInner, newSPK.SPKSk)
	plaintext, _ := decryptChatContent(t, desktop, freshSession, freshInner)
	if plaintext != "después del reset" {
		t.Fatalf("fresh plaintext = %q", plaintext)
	}

	encodedInit, err := json.Marshal(init)
	if err != nil {
		t.Fatal(err)
	}
	staleSession, err := desktop.AcceptInboundSession(mobile.PublicKeyHex(), string(encodedInit), oldSPK.SPKSk)
	if err != nil {
		t.Fatal(err)
	}
	header, err := json.Marshal(freshInner["ratchetHeader"])
	if err != nil {
		t.Fatal(err)
	}
	staleMessage, err := json.Marshal(map[string]any{
		"ratchetHeader": json.RawMessage(header),
		"content":       freshInner["content"],
		"nonce":         freshInner["nonce"],
	})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := desktop.RatchetDecrypt(staleSession, string(staleMessage)); err == nil {
		t.Fatal("fresh chat decrypted with the stale signed pre-key")
	}
}

func TestBuildDrResetRejectsInvalidContextAndSignedPreKey(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	spkJSON, err := identity.CreateSignedPreKey(300)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := identity.BuildDrReset("not-an-address", spkJSON, "direct"); err == nil {
		t.Fatal("invalid Yggdrasil address was accepted")
	}
	if _, err := identity.BuildDrReset("200::2", spkJSON, "group:"); err == nil {
		t.Fatal("empty group context was accepted")
	}
	if _, err := identity.BuildDrReset("200::2", spkJSON, "room:1"); err == nil {
		t.Fatal("unknown ratchet context was accepted")
	}
	if _, err := identity.BuildDrReset("200::2", spkJSON, "group:a:b"); err == nil {
		t.Fatal("ratchet context with extra separators was accepted")
	}
	if _, err := identity.BuildDrReset("200::2", `{"spkPub":"00","spkSig":"00","spkId":1}`, "direct"); err == nil {
		t.Fatal("invalid signed pre-key state was accepted")
	}
	groupReset, err := identity.BuildDrReset("200::2", spkJSON, "group:abc")
	if err != nil {
		t.Fatal(err)
	}
	packet, err := decodeControlPacket([]byte(groupReset))
	if err != nil {
		t.Fatal(err)
	}
	if packet["ratchetContext"] != "group:abc" {
		t.Fatalf("group reset context = %#v", packet["ratchetContext"])
	}
}

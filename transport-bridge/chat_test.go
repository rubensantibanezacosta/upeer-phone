package transportbridge

import (
	"bytes"
	"compress/gzip"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"io"
	"strings"
	"testing"
)

func TestBuildChatSealsAndInteroperates(t *testing.T) {
	vectors := loadCryptoVectors(t)
	alice, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	bob, err := NewCryptoIdentity(mustHex(t, vectors.BobSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	spkJSON, err := bob.CreateSignedPreKey(777)
	if err != nil {
		t.Fatal(err)
	}
	var spk signedPreKeyState
	if err := json.Unmarshal([]byte(spkJSON), &spk); err != nil {
		t.Fatal(err)
	}
	outboundJSON, err := alice.BuildChat(
		"200::1",
		bob.PublicKeyHex(),
		spk.SPKPub,
		"",
		"hola desde el móvil",
		"message-1",
		"",
		777,
		1700000000000,
	)
	if err != nil {
		t.Fatal(err)
	}
	var first outboundChatResult
	if err := json.Unmarshal([]byte(outboundJSON), &first); err != nil {
		t.Fatal(err)
	}
	inner := openAndValidateChat(t, bob, alice, first.Packet)
	if inner["type"] != "CHAT" || inner["id"] != "message-1" || inner["x3dhInit"] == nil {
		t.Fatalf("first chat fields = %#v", inner)
	}
	bobSession := acceptChatSession(t, bob, alice, inner, spk.SPKSk)
	firstPlaintext, bobSession := decryptChatContent(t, bob, bobSession, inner)
	if firstPlaintext != "hola desde el móvil" {
		t.Fatalf("first plaintext = %q", firstPlaintext)
	}

	secondJSON, err := alice.BuildChat(
		"200::1",
		bob.PublicKeyHex(),
		"",
		first.Session,
		"segundo mensaje",
		"message-2",
		"",
		0,
		1700000001000,
	)
	if err != nil {
		t.Fatal(err)
	}
	var second outboundChatResult
	if err := json.Unmarshal([]byte(secondJSON), &second); err != nil {
		t.Fatal(err)
	}
	secondInner := openAndValidateChat(t, bob, alice, second.Packet)
	if secondInner["x3dhInit"] != nil {
		t.Fatalf("second chat unexpectedly contains x3dhInit: %#v", secondInner)
	}
	secondPlaintext, _ := decryptChatContent(t, bob, bobSession, secondInner)
	if secondPlaintext != "segundo mensaje" {
		t.Fatalf("second plaintext = %q", secondPlaintext)
	}
}

func TestBuildChatCompressesLongMessagesForDesktop(t *testing.T) {
	vectors := loadCryptoVectors(t)
	alice, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	bob, err := NewCryptoIdentity(mustHex(t, vectors.BobSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	spkJSON, err := bob.CreateSignedPreKey(778)
	if err != nil {
		t.Fatal(err)
	}
	var spk signedPreKeyState
	if err := json.Unmarshal([]byte(spkJSON), &spk); err != nil {
		t.Fatal(err)
	}
	message := strings.Repeat("palabra repetida ", 200)
	outboundJSON, err := alice.BuildChat(
		"200::1",
		bob.PublicKeyHex(),
		spk.SPKPub,
		"",
		message,
		"message-compressed",
		"",
		778,
		1700000002000,
	)
	if err != nil {
		t.Fatal(err)
	}
	var outbound outboundChatResult
	if err := json.Unmarshal([]byte(outboundJSON), &outbound); err != nil {
		t.Fatal(err)
	}
	inner := openAndValidateChat(t, bob, alice, outbound.Packet)
	bobSession := acceptChatSession(t, bob, alice, inner, spk.SPKSk)
	plaintext, _ := decryptChatContent(t, bob, bobSession, inner)
	if !strings.HasPrefix(plaintext, compressionPrefix) {
		t.Fatalf("long chat was not compressed: %q", plaintext[:min(len(plaintext), 32)])
	}
	if decompressed := gunzipMessage(t, plaintext); decompressed != message {
		t.Fatalf("compressed chat round-trip mismatch: got %d bytes, want %d", len(decompressed), len(message))
	}
}

func gunzipMessage(t *testing.T, value string) string {
	t.Helper()
	if !strings.HasPrefix(value, compressionPrefix) {
		t.Fatalf("message does not use compression prefix: %q", value)
	}
	compressed, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(value, compressionPrefix))
	if err != nil {
		t.Fatal(err)
	}
	reader, err := gzip.NewReader(bytes.NewReader(compressed))
	if err != nil {
		t.Fatal(err)
	}
	decompressed, err := io.ReadAll(reader)
	if err != nil {
		t.Fatal(err)
	}
	if err := reader.Close(); err != nil {
		t.Fatal(err)
	}
	return string(decompressed)
}

func TestBuildHandshakeRequestAndDrResetAreSigned(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	spkJSON, err := identity.CreateSignedPreKey(99)
	if err != nil {
		t.Fatal(err)
	}
	var spk signedPreKeyState
	if err := json.Unmarshal([]byte(spkJSON), &spk); err != nil {
		t.Fatal(err)
	}
	request, err := identity.BuildHandshakeRequest("200::1", spkJSON, "Móvil")
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
	if metadata.Type != handshakeRequest || metadata.SenderUpeerID != identity.UpeerID() {
		t.Fatalf("request metadata = %#v", metadata)
	}
	if !identity.VerifySignedPreKey(identity.PublicKeyHex(), spk.SPKPub, spk.SPKSig) {
		t.Fatal("signed pre-key did not verify")
	}
	if identity.VerifySignedPreKey(identity.PublicKeyHex(), spk.SPKPub, "00") {
		t.Fatal("truncated signed pre-key signature was accepted")
	}

	reset, err := identity.BuildDrReset("200::1", spkJSON, "direct")
	if err != nil {
		t.Fatal(err)
	}
	resetMetadataJSON, err := (&Bridge{}).ValidateSignedPacket([]byte(reset), identity.PublicKeyHex())
	if err != nil {
		t.Fatal(err)
	}
	var resetMetadata signedPacketMetadata
	if err := json.Unmarshal([]byte(resetMetadataJSON), &resetMetadata); err != nil {
		t.Fatal(err)
	}
	if resetMetadata.Type != "DR_RESET" {
		t.Fatalf("reset metadata = %#v", resetMetadata)
	}
}

func openAndValidateChat(t *testing.T, recipient, sender *CryptoIdentity, sealedPacket string) map[string]any {
	t.Helper()
	var outer map[string]any
	if err := json.Unmarshal([]byte(sealedPacket), &outer); err != nil {
		t.Fatal(err)
	}
	plaintext, err := recipient.OpenSealed(outer["ciphertext"].(string))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := (&Bridge{}).ValidateSignedPacket(plaintext, sender.PublicKeyHex()); err != nil {
		t.Fatal(err)
	}
	inner, err := decodeControlPacket(plaintext)
	if err != nil {
		t.Fatal(err)
	}
	return inner
}

func acceptChatSession(t *testing.T, recipient, sender *CryptoIdentity, inner map[string]any, spkSecret string) string {
	t.Helper()
	init, ok := inner["x3dhInit"].(map[string]any)
	if !ok {
		t.Fatal("chat is missing x3dhInit")
	}
	if _, ok := init["spkId"].(json.Number); !ok {
		t.Fatalf("x3dh spkId = %#v", init["spkId"])
	}
	encoded, err := json.Marshal(init)
	if err != nil {
		t.Fatal(err)
	}
	session, err := recipient.AcceptInboundSession(sender.PublicKeyHex(), string(encoded), spkSecret)
	if err != nil {
		t.Fatal(err)
	}
	return session
}

func decryptChatContent(t *testing.T, identity *CryptoIdentity, session string, inner map[string]any) (string, string) {
	t.Helper()
	header, err := json.Marshal(inner["ratchetHeader"])
	if err != nil {
		t.Fatal(err)
	}
	message := map[string]any{
		"ratchetHeader": json.RawMessage(header),
		"content":       inner["content"],
		"nonce":         inner["nonce"],
	}
	messageJSON, err := json.Marshal(message)
	if err != nil {
		t.Fatal(err)
	}
	resultJSON, err := identity.RatchetDecrypt(session, string(messageJSON))
	if err != nil {
		t.Fatal(err)
	}
	var result ratchetDecryptResult
	if err := json.Unmarshal([]byte(resultJSON), &result); err != nil {
		t.Fatal(err)
	}
	plaintext, err := hex.DecodeString(result.PlaintextHex)
	if err != nil {
		t.Fatal(err)
	}
	return string(plaintext), result.Session
}

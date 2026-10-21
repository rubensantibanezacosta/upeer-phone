package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"testing"
)

func TestCryptoIdentityMatchesDesktopVectors(t *testing.T) {
	vectors := loadCryptoVectors(t)
	identity, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	if identity.PublicKeyHex() != vectors.PublicKey {
		t.Fatalf("public key = %s", identity.PublicKeyHex())
	}
	if identity.UpeerID() != "4d4dbe917544b07922348a66b9c4b5a5a5f34a9ffb319915c39409c7d0ff230a" {
		t.Fatalf("upeer ID = %s", identity.UpeerID())
	}
	sealed, err := identity.Seal([]byte(vectors.SealedPlaintext), vectors.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	opened, err := identity.OpenSealed(sealed)
	if err != nil || string(opened) != vectors.SealedPlaintext {
		t.Fatalf("seal round trip = %q, %v", opened, err)
	}
	opened, err = identity.OpenSealed(vectors.SealedRandom)
	if err != nil || string(opened) != vectors.SealedPlaintext {
		t.Fatalf("open Desktop sealed box = %q, %v", opened, err)
	}
	signature := mustHex(t, identity.Sign([]byte("identity signature")))
	if !ed25519.Verify(ed25519.PublicKey(mustHex(t, vectors.PublicKey)), []byte("identity signature"), signature) {
		t.Fatal("identity signature did not verify")
	}
}

func TestCryptoIdentitySessionsInteroperate(t *testing.T) {
	vectors := loadCryptoVectors(t)
	alice, err := NewCryptoIdentity(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	bob, err := NewCryptoIdentity(mustHex(t, vectors.BobSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	outboundJSON, err := alice.StartOutboundSession(bob.PublicKeyHex(), vectors.SPKPublicKey, 42)
	if err != nil {
		t.Fatal(err)
	}
	var outbound outboundSessionResult
	if err := json.Unmarshal([]byte(outboundJSON), &outbound); err != nil {
		t.Fatal(err)
	}
	bobState, err := bob.AcceptInboundSession(alice.PublicKeyHex(), outbound.X3DHInit, vectors.SPKSecretKey)
	if err != nil {
		t.Fatal(err)
	}
	encryptedJSON, err := alice.RatchetEncrypt(outbound.Session, []byte("hola desde móvil"))
	if err != nil {
		t.Fatal(err)
	}
	var encrypted ratchetEncryptResult
	if err := json.Unmarshal([]byte(encryptedJSON), &encrypted); err != nil {
		t.Fatal(err)
	}
	decryptedJSON, err := bob.RatchetDecrypt(bobState, encrypted.Message)
	if err != nil {
		t.Fatal(err)
	}
	var decrypted ratchetDecryptResult
	if err := json.Unmarshal([]byte(decryptedJSON), &decrypted); err != nil {
		t.Fatal(err)
	}
	plaintext, err := hex.DecodeString(decrypted.PlaintextHex)
	if err != nil || string(plaintext) != "hola desde móvil" {
		t.Fatalf("decrypt outbound session message = %q, %v", plaintext, err)
	}
	bobState = decrypted.Session
	replyJSON, err := bob.RatchetEncrypt(bobState, []byte("respuesta"))
	if err != nil {
		t.Fatal(err)
	}
	var reply ratchetEncryptResult
	if err := json.Unmarshal([]byte(replyJSON), &reply); err != nil {
		t.Fatal(err)
	}
	replyDecryptedJSON, err := alice.RatchetDecrypt(outbound.Session, reply.Message)
	if err != nil {
		t.Fatal(err)
	}
	var replyDecrypted ratchetDecryptResult
	if err := json.Unmarshal([]byte(replyDecryptedJSON), &replyDecrypted); err != nil {
		t.Fatal(err)
	}
	plaintext, err = hex.DecodeString(replyDecrypted.PlaintextHex)
	if err != nil || string(plaintext) != "respuesta" {
		t.Fatalf("decrypt inbound session reply = %q, %v", plaintext, err)
	}
}

func TestRatchetStateSerializationPreservesSkippedKeys(t *testing.T) {
	vectors := loadCryptoVectors(t)
	shared := mustArray32(t, vectors.X3DHShared)
	spkPublic := mustHex(t, vectors.SPKPublicKey)
	alice, err := ratchetInitAliceRandom(shared, spkPublic)
	if err != nil {
		t.Fatal(err)
	}
	bob, err := ratchetInitBob(shared, spkPublic, mustHex(t, vectors.SPKSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	messages := make([]*RatchetMessage, 3)
	for index := range messages {
		messages[index], err = ratchetEncrypt(alice, []byte{byte(index)})
		if err != nil {
			t.Fatal(err)
		}
	}
	if _, err := ratchetDecrypt(bob, messages[2].Header, messages[2].Ciphertext, messages[2].Nonce); err != nil {
		t.Fatal(err)
	}
	spkIDUsed := 7
	encoded, err := serializeRatchetState(bob, &spkIDUsed)
	if err != nil {
		t.Fatal(err)
	}
	restored, restoredSPKID, err := deserializeRatchetState(encoded)
	if err != nil {
		t.Fatal(err)
	}
	if restoredSPKID == nil || *restoredSPKID != spkIDUsed {
		t.Fatalf("restored spkIdUsed = %v", restoredSPKID)
	}
	plaintext, err := ratchetDecrypt(restored, messages[0].Header, messages[0].Ciphertext, messages[0].Nonce)
	if err != nil || len(plaintext) != 1 || plaintext[0] != 0 {
		t.Fatalf("decrypt after restore = %v, %v", plaintext, err)
	}
}

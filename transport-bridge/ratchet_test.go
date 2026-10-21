package transportbridge

import (
	"bytes"
	"errors"
	"fmt"
	"testing"
)

func TestRatchetFirstMessageMatchesSodium(t *testing.T) {
	vectors := loadCryptoVectors(t)
	shared := mustArray32(t, vectors.X3DHShared)
	spkPublic := mustHex(t, vectors.SPKPublicKey)
	alice, err := ratchetInitAlice(shared, spkPublic, mustHex(t, vectors.AliceRatchetSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(alice.RK[:], mustHex(t, vectors.RatchetRoot)) {
		t.Fatalf("ratchet root = %x", alice.RK)
	}
	if alice.CKS == nil || !bytes.Equal(alice.CKS[:], mustHex(t, vectors.RatchetSendChain)) {
		t.Fatalf("ratchet send chain = %x", alice.CKS)
	}
	nonce := mustHex(t, vectors.RatchetNonce)
	var fixedNonce [24]byte
	copy(fixedNonce[:], nonce)
	message, err := ratchetEncryptWithNonce(alice, []byte(vectors.RatchetPlaintext), fixedNonce)
	if err != nil {
		t.Fatal(err)
	}
	if message.Ciphertext != vectors.RatchetCiphertext {
		t.Fatalf("ratchet ciphertext = %s", message.Ciphertext)
	}
	if message.Header.DH != vectors.AliceRatchetPublicKey || message.Header.PN != 0 || message.Header.N != 0 {
		t.Fatalf("ratchet header = %#v", message.Header)
	}
	bob, err := ratchetInitBob(shared, spkPublic, mustHex(t, vectors.SPKSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	plaintext, err := ratchetDecrypt(bob, message.Header, message.Ciphertext, message.Nonce)
	if err != nil {
		t.Fatal(err)
	}
	if string(plaintext) != vectors.RatchetPlaintext {
		t.Fatalf("ratchet plaintext = %q", plaintext)
	}
}

func TestRatchetConversationHandlesSkippedAndDuplicateMessages(t *testing.T) {
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
		messages[index], err = ratchetEncrypt(alice, []byte(fmt.Sprintf("message-%d", index)))
		if err != nil {
			t.Fatal(err)
		}
	}
	plaintext, err := ratchetDecrypt(bob, messages[2].Header, messages[2].Ciphertext, messages[2].Nonce)
	if err != nil || string(plaintext) != "message-2" {
		t.Fatalf("decrypt third message = %q, %v", plaintext, err)
	}
	plaintext, err = ratchetDecrypt(bob, messages[0].Header, messages[0].Ciphertext, messages[0].Nonce)
	if err != nil || string(plaintext) != "message-0" {
		t.Fatalf("decrypt skipped message = %q, %v", plaintext, err)
	}
	if _, err := ratchetDecrypt(bob, messages[2].Header, messages[2].Ciphertext, messages[2].Nonce); !errors.Is(err, ErrRatchetAuthentication) {
		t.Fatalf("duplicate message error = %v", err)
	}
	reply, err := ratchetEncrypt(bob, []byte("reply"))
	if err != nil {
		t.Fatal(err)
	}
	plaintext, err = ratchetDecrypt(alice, reply.Header, reply.Ciphertext, reply.Nonce)
	if err != nil || string(plaintext) != "reply" {
		t.Fatalf("decrypt reply = %q, %v", plaintext, err)
	}
}

func TestRatchetRejectsTamperingAndPreservesState(t *testing.T) {
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
	message, err := ratchetEncrypt(alice, []byte("authenticated"))
	if err != nil {
		t.Fatal(err)
	}
	tampered := []byte(message.Ciphertext)
	tampered[len(tampered)-1] ^= 1
	if _, err := ratchetDecrypt(bob, message.Header, string(tampered), message.Nonce); !errors.Is(err, ErrRatchetAuthentication) {
		t.Fatalf("tampered message error = %v", err)
	}
	plaintext, err := ratchetDecrypt(bob, message.Header, message.Ciphertext, message.Nonce)
	if err != nil || string(plaintext) != "authenticated" {
		t.Fatalf("decrypt after tamper = %q, %v", plaintext, err)
	}
}

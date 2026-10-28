package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"strings"
	"testing"
)

const groupTestSenderKey = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"

func TestSealGroupPayloadRoundTrips(t *testing.T) {
	sealedJSON, err := SealGroupPayload(groupTestSenderKey, "hola grupo")
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	var sealed GroupSealedPayload
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		t.Fatalf("decode sealed payload: %v", err)
	}
	if len(sealed.Nonce) != groupNonceBytes*2 {
		t.Fatalf("nonce length = %d", len(sealed.Nonce))
	}
	if len(sealed.Ciphertext) <= groupSecretboxMACBytes*2 {
		t.Fatalf("ciphertext length = %d", len(sealed.Ciphertext))
	}
	plaintext, err := OpenGroupPayload(groupTestSenderKey, sealed.Nonce, sealed.Ciphertext)
	if err != nil {
		t.Fatalf("OpenGroupPayload: %v", err)
	}
	if plaintext != "hola grupo" {
		t.Fatalf("plaintext = %q", plaintext)
	}
}

func TestSealGroupPayloadUsesAFreshNonce(t *testing.T) {
	first, err := SealGroupPayload(groupTestSenderKey, "mismo texto")
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	second, err := SealGroupPayload(groupTestSenderKey, "mismo texto")
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	if first == second {
		t.Fatal("two seals of the same plaintext produced the same envelope")
	}
}

func TestOpenGroupPayloadRejectsATamperedCiphertext(t *testing.T) {
	sealedJSON, err := SealGroupPayload(groupTestSenderKey, "no me toques")
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	var sealed GroupSealedPayload
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		t.Fatalf("decode sealed payload: %v", err)
	}
	raw, err := hex.DecodeString(sealed.Ciphertext)
	if err != nil {
		t.Fatalf("decode ciphertext: %v", err)
	}
	raw[0] ^= 0xff
	if _, err := OpenGroupPayload(groupTestSenderKey, sealed.Nonce, hex.EncodeToString(raw)); err == nil {
		t.Fatal("tampered ciphertext was accepted")
	}
}

func TestOpenGroupPayloadRejectsAnotherSenderKey(t *testing.T) {
	sealedJSON, err := SealGroupPayload(groupTestSenderKey, "secreto")
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	var sealed GroupSealedPayload
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		t.Fatalf("decode sealed payload: %v", err)
	}
	other := strings.Repeat("ab", groupSenderKeyBytes)
	if _, err := OpenGroupPayload(other, sealed.Nonce, sealed.Ciphertext); err == nil {
		t.Fatal("a different sender key decrypted the payload")
	}
}

func TestGroupPayloadRejectsMalformedKeys(t *testing.T) {
	cases := map[string]string{
		"vacía":  "",
		"corta":  "0f1e",
		"larga":  strings.Repeat("ab", groupSenderKeyBytes+1),
		"no hex": strings.Repeat("zz", groupSenderKeyBytes),
	}
	for name, senderKey := range cases {
		if _, err := SealGroupPayload(senderKey, "x"); err == nil {
			t.Fatalf("%s: SealGroupPayload accepted the key", name)
		}
	}
	if _, err := OpenGroupPayload(groupTestSenderKey, "00", "00"); err == nil {
		t.Fatal("OpenGroupPayload accepted a short nonce")
	}
	if _, err := OpenGroupPayload(groupTestSenderKey, strings.Repeat("00", groupNonceBytes), "00"); err == nil {
		t.Fatal("OpenGroupPayload accepted a short ciphertext")
	}
}

func TestSealGroupPayloadAcceptsUppercaseKeys(t *testing.T) {
	sealedJSON, err := SealGroupPayload(strings.ToUpper(groupTestSenderKey), "mayúsculas")
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	var sealed GroupSealedPayload
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		t.Fatalf("decode sealed payload: %v", err)
	}
	plaintext, err := OpenGroupPayload(groupTestSenderKey, sealed.Nonce, sealed.Ciphertext)
	if err != nil {
		t.Fatalf("OpenGroupPayload: %v", err)
	}
	if plaintext != "mayúsculas" {
		t.Fatalf("plaintext = %q", plaintext)
	}
}

func TestSealGroupPayloadKeepsBinaryPayloads(t *testing.T) {
	payload := `{"type":"file","fileName":"informe.pdf","fileHash":"` + strings.Repeat("a", 64) + `"}`
	sealedJSON, err := SealGroupPayload(groupTestSenderKey, payload)
	if err != nil {
		t.Fatalf("SealGroupPayload: %v", err)
	}
	var sealed GroupSealedPayload
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		t.Fatalf("decode sealed payload: %v", err)
	}
	plaintext, err := OpenGroupPayload(groupTestSenderKey, sealed.Nonce, sealed.Ciphertext)
	if err != nil {
		t.Fatalf("OpenGroupPayload: %v", err)
	}
	if plaintext != payload {
		t.Fatalf("plaintext = %q", plaintext)
	}
}

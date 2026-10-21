package transportbridge

import (
	"bytes"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"
)

type cryptoVectors struct {
	IdentitySeed            string `json:"identitySeed"`
	PublicKey               string `json:"publicKey"`
	SecretKey               string `json:"secretKey"`
	CurvePublicKey          string `json:"curvePublicKey"`
	CurveSecretKey          string `json:"curveSecretKey"`
	BobPublicKey            string `json:"bobPublicKey"`
	BobSecretKey            string `json:"bobSecretKey"`
	BobCurvePublicKey       string `json:"bobCurvePublicKey"`
	BobCurveSecretKey       string `json:"bobCurveSecretKey"`
	SPKPublicKey            string `json:"spkPublicKey"`
	SPKSecretKey            string `json:"spkSecretKey"`
	AliceEphemeralPublicKey string `json:"aliceEphemeralPublicKey"`
	AliceEphemeralSecretKey string `json:"aliceEphemeralSecretKey"`
	SealedPlaintext         string `json:"sealedPlaintext"`
	SealedFixed             string `json:"sealedFixed"`
	SealedRandom            string `json:"sealedRandom"`
	X3DHShared              string `json:"x3dhShared"`
	AliceRatchetPublicKey   string `json:"aliceRatchetPublicKey"`
	AliceRatchetSecretKey   string `json:"aliceRatchetSecretKey"`
	RatchetRoot             string `json:"ratchetRoot"`
	RatchetSendChain        string `json:"ratchetSendChain"`
	RatchetCiphertext       string `json:"ratchetCiphertext"`
	RatchetNonce            string `json:"ratchetNonce"`
	RatchetPlaintext        string `json:"ratchetPlaintext"`
}

func loadCryptoVectors(t *testing.T) cryptoVectors {
	t.Helper()
	content, err := os.ReadFile("testdata/crypto_vectors.json")
	if err != nil {
		t.Fatalf("read crypto vectors: %v", err)
	}
	var vectors cryptoVectors
	if err := json.Unmarshal(content, &vectors); err != nil {
		t.Fatalf("parse crypto vectors: %v", err)
	}
	return vectors
}

func mustHex(t *testing.T, value string) []byte {
	t.Helper()
	decoded, err := hex.DecodeString(value)
	if err != nil {
		t.Fatalf("decode hex %q: %v", value, err)
	}
	return decoded
}

func mustArray32(t *testing.T, value string) [32]byte {
	t.Helper()
	var result [32]byte
	decoded := mustHex(t, value)
	if len(decoded) != 32 {
		t.Fatalf("expected 32 bytes, got %d", len(decoded))
	}
	copy(result[:], decoded)
	return result
}

func TestEd25519CurveConversionMatchesSodium(t *testing.T) {
	vectors := loadCryptoVectors(t)
	curveSecret, err := ed25519PrivateToCurve25519(mustHex(t, vectors.SecretKey))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(curveSecret, mustHex(t, vectors.CurveSecretKey)) {
		t.Fatalf("curve secret = %x", curveSecret)
	}
	curvePublic, err := ed25519PublicToCurve25519(mustHex(t, vectors.PublicKey))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(curvePublic, mustHex(t, vectors.CurvePublicKey)) {
		t.Fatalf("curve public = %x", curvePublic)
	}
}

func TestSealedBoxMatchesSodium(t *testing.T) {
	vectors := loadCryptoVectors(t)
	recipientPublicKey := mustHex(t, vectors.PublicKey)
	recipientSecretKey := mustHex(t, vectors.SecretKey)
	plaintext := []byte(vectors.SealedPlaintext)
	fixed, err := sealBoxWithEphemeral(plaintext, recipientPublicKey, mustHex(t, vectors.AliceEphemeralSecretKey))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(fixed, mustHex(t, vectors.SealedFixed)) {
		t.Fatalf("sealed fixed = %x", fixed)
	}
	for _, ciphertextHex := range []string{vectors.SealedFixed, vectors.SealedRandom} {
		opened, err := openSealedBox(mustHex(t, ciphertextHex), recipientSecretKey)
		if err != nil {
			t.Fatalf("open sealed box: %v", err)
		}
		if string(opened) != vectors.SealedPlaintext {
			t.Fatalf("sealed plaintext = %q", opened)
		}
	}
	tampered := mustHex(t, vectors.SealedRandom)
	tampered[len(tampered)-1] ^= 1
	if _, err := openSealedBox(tampered, recipientSecretKey); err == nil {
		t.Fatal("tampered sealed box was accepted")
	}
}

func TestX3DHMatchesSodium(t *testing.T) {
	vectors := loadCryptoVectors(t)
	shared, ephemeralPublicKey, err := x3dhInitiator(
		mustHex(t, vectors.SecretKey),
		mustHex(t, vectors.BobPublicKey),
		mustHex(t, vectors.SPKPublicKey),
		mustHex(t, vectors.AliceEphemeralSecretKey),
	)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(ephemeralPublicKey, mustHex(t, vectors.AliceEphemeralPublicKey)) {
		t.Fatalf("ephemeral public = %x", ephemeralPublicKey)
	}
	if !bytes.Equal(shared[:], mustHex(t, vectors.X3DHShared)) {
		t.Fatalf("initiator shared = %x", shared)
	}
	responderShared, err := x3dhResponder(
		mustHex(t, vectors.BobSecretKey),
		mustHex(t, vectors.SPKSecretKey),
		mustHex(t, vectors.PublicKey),
		mustHex(t, vectors.AliceEphemeralPublicKey),
	)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(responderShared[:], mustHex(t, vectors.X3DHShared)) {
		t.Fatalf("responder shared = %x", responderShared)
	}
}

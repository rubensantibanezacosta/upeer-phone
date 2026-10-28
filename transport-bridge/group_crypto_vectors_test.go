package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const groupMessageVectorPath = "../../upper-desktop/tests/fixtures/group-message-go.json"

const groupMessageVectorSenderKey = "3d2c1b0a9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4"

const groupMessageVectorPlaintext = "Hola grupo, esto viaja con la clave del emisor"

type groupMessageVector struct {
	GeneratedBy string `json:"generatedBy"`
	SenderKey   string `json:"senderKey"`
	Plaintext   string `json:"plaintext"`
	Nonce       string `json:"nonce"`
	Ciphertext  string `json:"ciphertext"`
}

func buildGroupMessageVector(t *testing.T) []byte {
	t.Helper()
	sealedJSON, err := SealGroupPayload(groupMessageVectorSenderKey, groupMessageVectorPlaintext)
	if err != nil {
		t.Fatal(err)
	}
	var sealed GroupSealedPayload
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		t.Fatal(err)
	}
	encoded, err := json.MarshalIndent(groupMessageVector{
		GeneratedBy: "upper/transport-bridge",
		SenderKey:   groupMessageVectorSenderKey,
		Plaintext:   groupMessageVectorPlaintext,
		Nonce:       sealed.Nonce,
		Ciphertext:  sealed.Ciphertext,
	}, "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	return append(encoded, '\n')
}

func TestWriteGroupMessageVector(t *testing.T) {
	if os.Getenv("UP_WRITE_GROUP_VECTOR") != "1" {
		t.Skip("define UP_WRITE_GROUP_VECTOR=1 para regenerar el vector")
	}
	if err := os.MkdirAll(filepath.Dir(groupMessageVectorPath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(groupMessageVectorPath, buildGroupMessageVector(t), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestGroupMessageVectorOpensWithItsSenderKey(t *testing.T) {
	var vector groupMessageVector
	if err := json.Unmarshal(buildGroupMessageVector(t), &vector); err != nil {
		t.Fatal(err)
	}
	plaintext, err := OpenGroupPayload(vector.SenderKey, vector.Nonce, vector.Ciphertext)
	if err != nil {
		t.Fatalf("OpenGroupPayload: %v", err)
	}
	if plaintext != vector.Plaintext {
		t.Fatalf("plaintext = %q", plaintext)
	}
}

func TestGroupMessageVectorRejectsAnotherKey(t *testing.T) {
	var vector groupMessageVector
	if err := json.Unmarshal(buildGroupMessageVector(t), &vector); err != nil {
		t.Fatal(err)
	}
	other := strings.Repeat("11", groupSenderKeyBytes)
	if _, err := OpenGroupPayload(other, vector.Nonce, vector.Ciphertext); err == nil {
		t.Fatal("the vector opened with a foreign sender key")
	}
}

func TestGroupMessageVectorMatchesFixture(t *testing.T) {
	existing, err := os.ReadFile(groupMessageVectorPath)
	if err != nil {
		t.Skipf("todavía no hay vector en %s: %v", groupMessageVectorPath, err)
	}
	var vector groupMessageVector
	if err := json.Unmarshal(existing, &vector); err != nil {
		t.Fatal(err)
	}
	if vector.SenderKey != groupMessageVectorSenderKey || vector.Plaintext != groupMessageVectorPlaintext {
		t.Fatal("el vector del mensaje de grupo no corresponde a este código: UP_WRITE_GROUP_VECTOR=1 go test -run TestWriteGroupMessageVector")
	}
	if _, err := hex.DecodeString(vector.Nonce); err != nil {
		t.Fatalf("nonce del vector: %v", err)
	}
	plaintext, err := OpenGroupPayload(vector.SenderKey, vector.Nonce, vector.Ciphertext)
	if err != nil {
		t.Fatalf("el vector del mensaje de grupo ya no se descifra: %v", err)
	}
	if plaintext != vector.Plaintext {
		t.Fatal("el vector del mensaje de grupo devuelve otro texto")
	}
}

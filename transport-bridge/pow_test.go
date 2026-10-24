package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"testing"
	"time"
)

func TestContactPowHashMatchesDesktopLibsodium(t *testing.T) {
	upeerID := "abababababababababababababababababababababababababababababababab"
	salt, err := hex.DecodeString("00112233445566778899aabbccddeeff")
	if err != nil {
		t.Fatal(err)
	}
	hash := contactPowHash(upeerID, 1700000000, salt)
	const expected = "28df55b98aeb823d47b8f48fcd9292f7da040059cd40910c1d1bb3f09e89d6e1"
	if hex.EncodeToString(hash) != expected {
		t.Fatalf("pow hash = %s, want %s", hex.EncodeToString(hash), expected)
	}
}

func TestGenerateContactPowProofSolvesDifficulty(t *testing.T) {
	upeerID := "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"
	now := time.Unix(1700000000, 0)
	proof := generateContactPowProof(upeerID, now)
	if proof == "" {
		t.Fatal("no proof generated")
	}
	var decoded contactPowProof
	if err := json.Unmarshal([]byte(proof), &decoded); err != nil {
		t.Fatalf("proof is not JSON: %v", err)
	}
	if decoded.Timestamp != now.Unix() {
		t.Fatalf("timestamp = %d", decoded.Timestamp)
	}
	salt, err := hex.DecodeString(decoded.Salt)
	if err != nil || len(salt) != powSaltBytes {
		t.Fatalf("salt = %q", decoded.Salt)
	}
	hash := contactPowHash(upeerID, decoded.Timestamp, salt)
	if hash[0]&powDifficulty != 0 {
		t.Fatalf("proof does not satisfy difficulty: %x", hash[0])
	}
}

func TestGenerateContactPowProofIsDeterministicPerSalt(t *testing.T) {
	upeerID := "efefefefefefefefefefefefefefefefefefefefefefefefefefefefefefefef"
	first := generateContactPowProof(upeerID, time.Unix(1700000000, 0))
	second := generateContactPowProof(upeerID, time.Unix(1700000000, 0))
	if first == second {
		t.Fatal("expected a fresh salt per proof")
	}
}

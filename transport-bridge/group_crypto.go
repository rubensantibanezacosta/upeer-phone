package transportbridge

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"golang.org/x/crypto/nacl/secretbox"
)

const (
	groupSenderKeyBytes    = 32
	groupNonceBytes        = 24
	groupSecretboxMACBytes = 16
)

// GroupSealedPayload is the secretbox envelope used for group messages: a
// random 24-byte nonce next to the authenticated ciphertext, both hex encoded.
type GroupSealedPayload struct {
	Nonce      string `json:"nonce"`
	Ciphertext string `json:"ciphertext"`
}

// SealGroupPayload encrypts a group message with the sender key of the group.
// The construction is XSalsa20-Poly1305, byte for byte the same as the
// crypto_secretbox_easy that the desktop client uses through sodium-native.
func SealGroupPayload(senderKeyHex, plaintext string) (string, error) {
	key, err := parseGroupSenderKey(senderKeyHex)
	if err != nil {
		return "", err
	}
	var nonce [groupNonceBytes]byte
	if _, err := rand.Read(nonce[:]); err != nil {
		return "", fmt.Errorf("generate group nonce: %w", err)
	}
	sealed := secretbox.Seal(nil, []byte(plaintext), &nonce, &key)
	encoded, err := json.Marshal(GroupSealedPayload{
		Nonce:      hex.EncodeToString(nonce[:]),
		Ciphertext: hex.EncodeToString(sealed),
	})
	if err != nil {
		return "", fmt.Errorf("encode group payload: %w", err)
	}
	return string(encoded), nil
}

// OpenGroupPayload decrypts a group message with the sender key of the group.
// It returns an error instead of a partial plaintext when authentication fails,
// so a tampered or truncated ciphertext never reaches the message store.
func OpenGroupPayload(senderKeyHex, nonceHex, ciphertextHex string) (string, error) {
	key, err := parseGroupSenderKey(senderKeyHex)
	if err != nil {
		return "", err
	}
	nonce, err := decodeGroupHex("nonce", nonceHex, groupNonceBytes)
	if err != nil {
		return "", err
	}
	ciphertext, err := decodeGroupHex("ciphertext", ciphertextHex, 0)
	if err != nil {
		return "", err
	}
	if len(ciphertext) <= groupSecretboxMACBytes {
		return "", errors.New("group ciphertext is too short")
	}
	var nonceArray [groupNonceBytes]byte
	copy(nonceArray[:], nonce)
	plaintext, ok := secretbox.Open(nil, ciphertext, &nonceArray, &key)
	if !ok {
		return "", errors.New("group payload authentication failed")
	}
	return string(plaintext), nil
}

func parseGroupSenderKey(value string) ([groupSenderKeyBytes]byte, error) {
	var key [groupSenderKeyBytes]byte
	decoded, err := decodeGroupHex("senderKey", value, groupSenderKeyBytes)
	if err != nil {
		return key, err
	}
	copy(key[:], decoded)
	return key, nil
}

func decodeGroupHex(field, value string, expectedBytes int) ([]byte, error) {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return nil, fmt.Errorf("group %s must not be empty", field)
	}
	decoded, err := hex.DecodeString(strings.ToLower(trimmed))
	if err != nil {
		return nil, fmt.Errorf("group %s must be hexadecimal: %w", field, err)
	}
	if expectedBytes > 0 && len(decoded) != expectedBytes {
		return nil, fmt.Errorf("group %s must be %d bytes", field, expectedBytes)
	}
	return decoded, nil
}

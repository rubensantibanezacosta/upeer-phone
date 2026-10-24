package transportbridge

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"strconv"
	"time"

	"golang.org/x/crypto/argon2"
)

const (
	powSaltBytes  = 16
	powKeyBytes   = 32
	powTime       = 1
	powMemoryKiB  = 8
	powThreads    = 1
	powDifficulty = 0xf0
	powAttempts   = 512
)

type contactPowProof struct {
	Salt      string `json:"s"`
	Timestamp int64  `json:"t"`
}

func contactPowHash(upeerID string, timestamp int64, salt []byte) []byte {
	password := []byte(upeerID + strconv.FormatInt(timestamp, 10))
	return argon2.IDKey(password, salt, powTime, powMemoryKiB, powThreads, powKeyBytes)
}

func generateContactPowProof(upeerID string, now time.Time) string {
	timestamp := now.Unix()
	salt := make([]byte, powSaltBytes)
	for attempt := 0; attempt < powAttempts; attempt++ {
		if _, err := rand.Read(salt); err != nil {
			return ""
		}
		if contactPowHash(upeerID, timestamp, salt)[0]&powDifficulty != 0 {
			continue
		}
		encoded, err := json.Marshal(contactPowProof{Salt: hex.EncodeToString(salt), Timestamp: timestamp})
		if err != nil {
			return ""
		}
		return string(encoded)
	}
	return ""
}

func (identity *CryptoIdentity) newContactPowProof() string {
	return generateContactPowProof(identity.UpeerID(), time.Now())
}

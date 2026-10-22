package dht

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"
)

const (
	KademliaIDBytes          = 20
	KademliaIDBits           = 160
	KademliaBucketCount      = KademliaIDBits
	KademliaK                = 20
	KademliaAlpha            = 3
	KademliaRefreshInterval  = time.Hour
	KademliaValueTTL         = 30 * 24 * time.Hour
	KademliaMaxStoreEntries  = 10_000
	KademliaMaxDevicesPerKey = 10
	KademliaPendingQueryTTL  = 30 * time.Second
	KademliaQueryTimeout     = 5 * time.Second

	KademliaBootstrapMinNodes     = 10
	KademliaBootstrapRetry        = 30 * time.Second
	KademliaBootstrapMaxRetry     = 5 * time.Minute
	KademliaBootstrapLookupRounds = 3
	KademliaBootstrapProbeBatch   = 10
	KademliaMaxSeedNodes          = 100
	KademliaMaxPersistedContacts  = 100
	KademliaMaxSeedFileBytes      = 262_144
	KademliaLanProbeInterval      = 5 * time.Minute
)

type KademliaID [KademliaIDBytes]byte

func NewKademliaID(upeerID string) (KademliaID, error) {
	return hashKademliaIdentifier(upeerID)
}

func NewLookupKademliaID(value string) (KademliaID, error) {
	return hashKademliaIdentifier(value)
}

func NewRawKademliaID(value string) (KademliaID, error) {
	decoded, err := decodeKademliaHex(value, KademliaIDBytes)
	if err != nil {
		return KademliaID{}, err
	}
	var id KademliaID
	copy(id[:], decoded)
	return id, nil
}

func (id KademliaID) Hex() string {
	return hex.EncodeToString(id[:])
}

func (id KademliaID) CompareDistanceTo(other, target KademliaID) int {
	for index := range id {
		own := id[index] ^ target[index]
		rival := other[index] ^ target[index]
		if own < rival {
			return -1
		}
		if own > rival {
			return 1
		}
	}
	return 0
}

func KademliaBucketIndex(nodeID, otherID KademliaID) int {
	for byteIndex := range nodeID {
		distance := nodeID[byteIndex] ^ otherID[byteIndex]
		if distance == 0 {
			continue
		}
		for bit := 7; bit >= 0; bit-- {
			if distance&(1<<bit) != 0 {
				return byteIndex*8 + (7 - bit)
			}
		}
	}
	return KademliaBucketCount - 1
}

func hashKademliaIdentifier(value string) (KademliaID, error) {
	if !validKademliaHex(value) || len(value) > 128 {
		return KademliaID{}, errors.New("invalid Kademlia identifier")
	}
	decoded, err := hex.DecodeString(value)
	if err != nil {
		return KademliaID{}, fmt.Errorf("decode Kademlia identifier: %w", err)
	}
	digest := sha256.Sum256(decoded)
	var id KademliaID
	copy(id[:], digest[:KademliaIDBytes])
	return id, nil
}

func decodeKademliaHex(value string, expectedBytes int) ([]byte, error) {
	if !validKademliaHex(value) || len(value) != expectedBytes*2 {
		return nil, errors.New("invalid Kademlia identifier")
	}
	decoded, err := hex.DecodeString(value)
	if err != nil {
		return nil, fmt.Errorf("decode Kademlia identifier: %w", err)
	}
	return decoded, nil
}

func validKademliaHex(value string) bool {
	if value == "" || len(value)%2 != 0 {
		return false
	}
	_, err := hex.DecodeString(strings.ToLower(value))
	return err == nil
}

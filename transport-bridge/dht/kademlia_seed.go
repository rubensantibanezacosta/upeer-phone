package dht

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net"
	"net/netip"
	"os"
	"strings"

	"golang.org/x/crypto/blake2b"
)

const (
	KademliaSeedDNSDomain = "dht-seeds.upeer.chat"
	KademliaSeedFileName  = "seednodes.json"
)

type KademliaSeed struct {
	UpeerID   string `json:"upeerId"`
	Address   string `json:"address"`
	PublicKey string `json:"publicKey"`
}

type KademliaSeedLoadResult struct {
	Seeds          []KademliaSeed
	Rejected       int
	Duplicates     int
	SourceFailures int
}

type KademliaSeedLoader struct {
	SelfUpeerID      string
	ExistingContacts func() []KademliaContact
	StaticSeeds      func() []KademliaSeed
	FilePath         string
	ReadFile         func(string) ([]byte, error)
	ResolveTXT       func(string) ([]string, error)
	MaxSeeds         int
}

func NewKademliaSeedLoader(selfUpeerID string) *KademliaSeedLoader {
	return &KademliaSeedLoader{
		SelfUpeerID: selfUpeerID,
		ReadFile:    os.ReadFile,
		ResolveTXT:  net.LookupTXT,
		MaxSeeds:    KademliaMaxSeedNodes,
	}
}

func (loader *KademliaSeedLoader) Load() KademliaSeedLoadResult {
	result := KademliaSeedLoadResult{}
	seen := make(map[string]struct{})
	limit := loader.MaxSeeds
	if limit <= 0 {
		limit = KademliaMaxSeedNodes
	}

	collect := func(candidates []KademliaSeed) {
		for _, candidate := range candidates {
			normalized, ok := NormalizeKademliaSeed(candidate, loader.SelfUpeerID)
			if !ok {
				result.Rejected++
				continue
			}
			if _, exists := seen[normalized.UpeerID]; exists {
				result.Duplicates++
				continue
			}
			if len(result.Seeds) >= limit {
				continue
			}
			seen[normalized.UpeerID] = struct{}{}
			result.Seeds = append(result.Seeds, normalized)
		}
	}

	if loader.ExistingContacts != nil {
		contacts := loader.ExistingContacts()
		candidates := make([]KademliaSeed, 0, len(contacts))
		for _, contact := range contacts {
			candidates = append(candidates, KademliaSeed{
				UpeerID: contact.UpeerID, Address: contact.Address, PublicKey: contact.PublicKey,
			})
		}
		collect(candidates)
	}
	if loader.StaticSeeds != nil {
		collect(loader.StaticSeeds())
	}
	if loader.ResolveTXT != nil {
		records, err := loader.ResolveTXT(KademliaSeedDNSDomain)
		if err != nil {
			result.SourceFailures++
		} else {
			collect(ParseKademliaSeedDNS(records))
		}
	}
	if loader.FilePath != "" && loader.ReadFile != nil {
		content, err := loader.ReadFile(loader.FilePath)
		if err != nil {
			result.SourceFailures++
		} else {
			collect(ParseKademliaSeedFile(content))
		}
	}
	return result
}

func ParseKademliaSeedDNS(records []string) []KademliaSeed {
	seeds := make([]KademliaSeed, 0, len(records))
	for _, record := range records {
		fields := make(map[string]string)
		for _, part := range strings.Split(record, ";") {
			key, value, found := strings.Cut(part, "=")
			if !found {
				continue
			}
			key = strings.TrimSpace(key)
			value = strings.TrimSpace(value)
			if key != "" && value != "" {
				fields[key] = value
			}
		}
		seed := KademliaSeed{
			UpeerID: fields["upeerId"], Address: fields["address"], PublicKey: fields["publicKey"],
		}
		if seed.UpeerID != "" && seed.Address != "" && seed.PublicKey != "" {
			seeds = append(seeds, seed)
		}
	}
	return seeds
}

func ParseKademliaSeedFile(content []byte) []KademliaSeed {
	if len(content) == 0 || len(content) > KademliaMaxSeedFileBytes {
		return nil
	}
	var seeds []KademliaSeed
	if err := json.Unmarshal(content, &seeds); err != nil {
		return nil
	}
	return seeds
}

func NormalizeKademliaSeed(seed KademliaSeed, selfUpeerID string) (KademliaSeed, bool) {
	upeerID := strings.ToLower(strings.TrimSpace(seed.UpeerID))
	if len(upeerID) != 64 || !validKademliaHex(upeerID) {
		return KademliaSeed{}, false
	}
	if strings.EqualFold(upeerID, strings.TrimSpace(selfUpeerID)) {
		return KademliaSeed{}, false
	}
	address := strings.TrimSpace(seed.Address)
	if !validKademliaSeedAddress(address) {
		return KademliaSeed{}, false
	}
	publicKey := strings.ToLower(strings.TrimSpace(seed.PublicKey))
	if len(publicKey) != 64 || !validKademliaHex(publicKey) {
		return KademliaSeed{}, false
	}
	derived, err := KademliaUpeerIDFromPublicKey(publicKey)
	if err != nil || !strings.EqualFold(derived, upeerID) {
		return KademliaSeed{}, false
	}
	return KademliaSeed{UpeerID: upeerID, Address: address, PublicKey: publicKey}, true
}

func NormalizeKademliaContact(contact KademliaContact, selfUpeerID string) (KademliaContact, bool) {
	seed, ok := NormalizeKademliaSeed(KademliaSeed{
		UpeerID: contact.UpeerID, Address: contact.Address, PublicKey: contact.PublicKey,
	}, selfUpeerID)
	if !ok {
		return KademliaContact{}, false
	}
	nodeID, err := NewKademliaID(seed.UpeerID)
	if err != nil {
		return KademliaContact{}, false
	}
	return KademliaContact{
		NodeID: nodeID, UpeerID: seed.UpeerID, Address: seed.Address,
		PublicKey: seed.PublicKey, LastSeen: contact.LastSeen, DhtSeq: contact.DhtSeq,
	}, true
}

func KademliaUpeerIDFromPublicKey(publicKeyHex string) (string, error) {
	if len(publicKeyHex) != 64 || !validKademliaHex(publicKeyHex) {
		return "", fmt.Errorf("invalid Ed25519 public key")
	}
	decoded, err := hex.DecodeString(strings.ToLower(publicKeyHex))
	if err != nil {
		return "", fmt.Errorf("decode Ed25519 public key: %w", err)
	}
	digest := blake2b.Sum256(decoded)
	return hex.EncodeToString(digest[:]), nil
}

func validKademliaSeedAddress(address string) bool {
	if address == "" || len(address) > MaxAddressLength {
		return false
	}
	if strings.ContainsAny(address, " \t\r\n") {
		return false
	}
	parsed, err := netip.ParseAddr(address)
	if err != nil {
		return false
	}
	return !parsed.IsUnspecified() && !parsed.IsMulticast()
}

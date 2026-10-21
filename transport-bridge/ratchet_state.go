package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
)

type serializedRatchetState struct {
	RK           string            `json:"rk"`
	CKS          *string           `json:"cks"`
	CKR          *string           `json:"ckr"`
	NS           int               `json:"ns"`
	NR           int               `json:"nr"`
	PN           int               `json:"pn"`
	DHSPK        string            `json:"dhsPk"`
	DHSSK        string            `json:"dhsSk"`
	DHR          *string           `json:"dhr"`
	Skipped      map[string]string `json:"skipped"`
	SkippedOrder []string          `json:"skippedOrder,omitempty"`
	SPKIDUsed    *int              `json:"spkIdUsed,omitempty"`
}

func serializeRatchetState(state *RatchetState, spkIDUsed *int) (string, error) {
	skipped := make(map[string]string, len(state.Skipped))
	for key, value := range state.Skipped {
		skipped[key] = hex.EncodeToString(value[:])
	}
	serialized := serializedRatchetState{
		RK:           hex.EncodeToString(state.RK[:]),
		NS:           state.NS,
		NR:           state.NR,
		PN:           state.PN,
		DHSPK:        hex.EncodeToString(state.DHSPK[:]),
		DHSSK:        hex.EncodeToString(state.DHSSK[:]),
		Skipped:      skipped,
		SkippedOrder: append([]string(nil), state.SkippedOrder...),
		SPKIDUsed:    spkIDUsed,
	}
	if state.CKS != nil {
		value := hex.EncodeToString(state.CKS[:])
		serialized.CKS = &value
	}
	if state.CKR != nil {
		value := hex.EncodeToString(state.CKR[:])
		serialized.CKR = &value
	}
	if state.DHR != nil {
		value := hex.EncodeToString(state.DHR[:])
		serialized.DHR = &value
	}
	encoded, err := json.Marshal(serialized)
	if err != nil {
		return "", fmt.Errorf("encode ratchet state: %w", err)
	}
	return string(encoded), nil
}

func deserializeRatchetState(value string) (*RatchetState, *int, error) {
	var serialized serializedRatchetState
	if err := json.Unmarshal([]byte(value), &serialized); err != nil {
		return nil, nil, fmt.Errorf("decode ratchet state: %w", err)
	}
	rootKey, err := decodeRatchetKey(serialized.RK)
	if err != nil {
		return nil, nil, fmt.Errorf("decode ratchet root key: %w", err)
	}
	publicKey, err := decodeRatchetKey(serialized.DHSPK)
	if err != nil {
		return nil, nil, fmt.Errorf("decode ratchet public key: %w", err)
	}
	secretKey, err := decodeRatchetKey(serialized.DHSSK)
	if err != nil {
		return nil, nil, fmt.Errorf("decode ratchet secret key: %w", err)
	}
	state := &RatchetState{
		RK:           rootKey,
		NS:           serialized.NS,
		NR:           serialized.NR,
		PN:           serialized.PN,
		DHSPK:        publicKey,
		DHSSK:        secretKey,
		Skipped:      make(map[string][32]byte, len(serialized.Skipped)),
		SkippedOrder: append([]string(nil), serialized.SkippedOrder...),
	}
	if serialized.CKS != nil {
		value, err := decodeRatchetKey(*serialized.CKS)
		if err != nil {
			return nil, nil, fmt.Errorf("decode ratchet send chain: %w", err)
		}
		state.CKS = &value
	}
	if serialized.CKR != nil {
		value, err := decodeRatchetKey(*serialized.CKR)
		if err != nil {
			return nil, nil, fmt.Errorf("decode ratchet receive chain: %w", err)
		}
		state.CKR = &value
	}
	if serialized.DHR != nil {
		value, err := decodeRatchetKey(*serialized.DHR)
		if err != nil {
			return nil, nil, fmt.Errorf("decode ratchet remote key: %w", err)
		}
		state.DHR = &value
	}
	for key, encoded := range serialized.Skipped {
		value, err := decodeRatchetKey(encoded)
		if err != nil {
			return nil, nil, fmt.Errorf("decode skipped ratchet key %q: %w", key, err)
		}
		state.Skipped[key] = value
	}
	if len(state.SkippedOrder) == 0 {
		for key := range state.Skipped {
			state.SkippedOrder = append(state.SkippedOrder, key)
		}
	}
	return state, serialized.SPKIDUsed, nil
}

func decodeRatchetKey(value string) ([32]byte, error) {
	var result [32]byte
	decoded, err := hex.DecodeString(value)
	if err != nil {
		return result, err
	}
	if len(decoded) != 32 {
		return result, errors.New("ratchet key must be 32 bytes")
	}
	copy(result[:], decoded)
	return result, nil
}

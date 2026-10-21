package transportbridge

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strconv"

	"golang.org/x/crypto/curve25519"
	"golang.org/x/crypto/nacl/box"
)

var ErrRatchetAuthentication = errors.New("ratchet authentication failed")

type RatchetMessage struct {
	Header     RatchetHeader `json:"ratchetHeader"`
	Ciphertext string        `json:"content"`
	Nonce      string        `json:"nonce"`
}

func snapshotRatchetState(state *RatchetState) *RatchetState {
	snapshot := &RatchetState{
		RK:           state.RK,
		NS:           state.NS,
		NR:           state.NR,
		PN:           state.PN,
		DHSPK:        state.DHSPK,
		DHSSK:        state.DHSSK,
		Skipped:      make(map[string][32]byte, len(state.Skipped)),
		SkippedOrder: append([]string(nil), state.SkippedOrder...),
	}
	if state.CKS != nil {
		value := *state.CKS
		snapshot.CKS = &value
	}
	if state.CKR != nil {
		value := *state.CKR
		snapshot.CKR = &value
	}
	if state.DHR != nil {
		value := *state.DHR
		snapshot.DHR = &value
	}
	for key, value := range state.Skipped {
		snapshot.Skipped[key] = value
	}
	return snapshot
}

func restoreRatchetState(state, snapshot *RatchetState) {
	state.RK = snapshot.RK
	state.CKS = snapshot.CKS
	state.CKR = snapshot.CKR
	state.NS = snapshot.NS
	state.NR = snapshot.NR
	state.PN = snapshot.PN
	state.DHSPK = snapshot.DHSPK
	state.DHSSK = snapshot.DHSSK
	state.DHR = snapshot.DHR
	state.Skipped = snapshot.Skipped
	state.SkippedOrder = snapshot.SkippedOrder
}

func dhRatchetStep(state *RatchetState, remotePublicKey []byte) error {
	state.PN = state.NS
	state.NS = 0
	state.NR = 0
	dh1, err := dh(state.DHSSK[:], remotePublicKey)
	if err != nil {
		return err
	}
	rootKey, receiveChain, err := hkdf(state.RK[:], dh1[:], "ratchet1")
	if err != nil {
		return err
	}
	state.RK = rootKey
	state.CKR = &receiveChain
	newPublicKey, newSecretKey, err := box.GenerateKey(rand.Reader)
	if err != nil {
		return fmt.Errorf("generate ratchet key: %w", err)
	}
	state.DHSPK = *newPublicKey
	state.DHSSK = *newSecretKey
	var remote [32]byte
	copy(remote[:], remotePublicKey)
	state.DHR = &remote
	dh2, err := dh(state.DHSSK[:], remotePublicKey)
	if err != nil {
		return err
	}
	rootKey, sendChain, err := hkdf(state.RK[:], dh2[:], "ratchet1")
	if err != nil {
		return err
	}
	state.RK = rootKey
	state.CKS = &sendChain
	return nil
}

func skippedKey(header RatchetHeader) string {
	return header.DH + ":" + strconv.Itoa(header.N)
}

func addSkippedKey(state *RatchetState, key string, messageKey [32]byte) {
	if _, exists := state.Skipped[key]; !exists {
		state.SkippedOrder = append(state.SkippedOrder, key)
	}
	state.Skipped[key] = messageKey
	for len(state.Skipped) > maxSkippedTotal && len(state.SkippedOrder) > 0 {
		oldest := state.SkippedOrder[0]
		state.SkippedOrder = state.SkippedOrder[1:]
		delete(state.Skipped, oldest)
	}
}

func removeSkippedKey(state *RatchetState, key string) {
	delete(state.Skipped, key)
	for index, candidate := range state.SkippedOrder {
		if candidate == key {
			state.SkippedOrder = append(state.SkippedOrder[:index], state.SkippedOrder[index+1:]...)
			return
		}
	}
}

func skipKeys(state *RatchetState, until int) error {
	if state.CKR == nil {
		return nil
	}
	if until-state.NR > maxSkip {
		return errors.New("ratchet skipped message limit exceeded")
	}
	for state.NR < until {
		messageKey, nextChainKey := chainStep(*state.CKR)
		state.CKR = &nextChainKey
		if state.DHR != nil {
			addSkippedKey(state, hex.EncodeToString(state.DHR[:])+":"+strconv.Itoa(state.NR), messageKey)
		}
		state.NR++
	}
	return nil
}

func ratchetEncrypt(state *RatchetState, plaintext []byte) (*RatchetMessage, error) {
	var nonce [24]byte
	if _, err := rand.Read(nonce[:]); err != nil {
		return nil, fmt.Errorf("generate ratchet nonce: %w", err)
	}
	return ratchetEncryptWithNonce(state, plaintext, nonce)
}

func ratchetEncryptWithNonce(state *RatchetState, plaintext []byte, nonce [24]byte) (*RatchetMessage, error) {
	if state.CKS == nil {
		return nil, errors.New("ratchet has no send chain key")
	}
	messageKey, nextChainKey := chainStep(*state.CKS)
	state.CKS = &nextChainKey
	ciphertext := secretboxEncrypt(plaintext, nonce, messageKey)
	header := RatchetHeader{
		DH: hex.EncodeToString(state.DHSPK[:]),
		PN: state.PN,
		N:  state.NS,
	}
	state.NS++
	return &RatchetMessage{
		Header:     header,
		Ciphertext: hex.EncodeToString(ciphertext),
		Nonce:      hex.EncodeToString(nonce[:]),
	}, nil
}

func ratchetDecrypt(state *RatchetState, header RatchetHeader, ciphertextHex, nonceHex string) ([]byte, error) {
	if header.N < 0 || header.PN < 0 || len(header.DH) != 64 || len(nonceHex) != 48 {
		return nil, ErrRatchetAuthentication
	}
	ciphertext, err := hex.DecodeString(ciphertextHex)
	if err != nil || len(ciphertext) < box.Overhead {
		return nil, ErrRatchetAuthentication
	}
	nonceBytes, err := hex.DecodeString(nonceHex)
	if err != nil {
		return nil, ErrRatchetAuthentication
	}
	remotePublicKey, err := hex.DecodeString(header.DH)
	if err != nil || len(remotePublicKey) != curve25519.PointSize {
		return nil, ErrRatchetAuthentication
	}
	var nonce [24]byte
	copy(nonce[:], nonceBytes)
	snapshot := snapshotRatchetState(state)
	restored := false
	restore := func() {
		if !restored {
			restoreRatchetState(state, snapshot)
			restored = true
		}
	}
	skipped := skippedKey(header)
	if messageKey, ok := state.Skipped[skipped]; ok {
		plaintext, openErr := secretboxDecrypt(ciphertext, nonce, messageKey)
		if openErr == nil {
			removeSkippedKey(state, skipped)
			return plaintext, nil
		}
	}
	if state.DHR != nil && header.DH == hex.EncodeToString(state.DHR[:]) && header.N < state.NR {
		return nil, ErrRatchetAuthentication
	}
	needsDHStep := state.DHR == nil || header.DH != hex.EncodeToString(state.DHR[:])
	if needsDHStep && state.CKR != nil && header.PN-state.NR > maxSkip {
		return nil, errors.New("ratchet pre-DH skipped message limit exceeded")
	}
	nextReceiveIndex := state.NR
	if needsDHStep {
		nextReceiveIndex = 0
	}
	if header.N-nextReceiveIndex > maxSkip {
		return nil, errors.New("ratchet skipped message limit exceeded")
	}
	if needsDHStep {
		if state.CKR != nil {
			if err := skipKeys(state, header.PN); err != nil {
				restore()
				return nil, err
			}
		}
		if err := dhRatchetStep(state, remotePublicKey); err != nil {
			restore()
			return nil, err
		}
	}
	if err := skipKeys(state, header.N); err != nil {
		restore()
		return nil, err
	}
	if state.CKR == nil {
		restore()
		return nil, ErrRatchetAuthentication
	}
	messageKey, nextChainKey := chainStep(*state.CKR)
	plaintext, err := secretboxDecrypt(ciphertext, nonce, messageKey)
	if err != nil {
		restore()
		return nil, ErrRatchetAuthentication
	}
	state.CKR = &nextChainKey
	state.NR++
	return plaintext, nil
}

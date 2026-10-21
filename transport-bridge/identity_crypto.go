package transportbridge

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"golang.org/x/crypto/curve25519"
	"golang.org/x/crypto/nacl/box"
)

type CryptoIdentity struct {
	publicKey ed25519.PublicKey
	secretKey ed25519.PrivateKey
}

func NewCryptoIdentity(secretKey []byte) (*CryptoIdentity, error) {
	if len(secretKey) != ed25519.PrivateKeySize {
		return nil, errors.New("uPeer identity secret key must be 64 bytes")
	}
	derived := ed25519.NewKeyFromSeed(secretKey[:ed25519.SeedSize])
	if !bytes.Equal(derived, secretKey) {
		return nil, errors.New("uPeer identity secret key does not match its public key")
	}
	privateKey := append(ed25519.PrivateKey(nil), secretKey...)
	publicKey := append(ed25519.PublicKey(nil), privateKey[32:]...)
	return &CryptoIdentity{publicKey: publicKey, secretKey: privateKey}, nil
}

func (identity *CryptoIdentity) PublicKeyHex() string {
	return hex.EncodeToString(identity.publicKey)
}

func (identity *CryptoIdentity) UpeerID() string {
	return hex.EncodeToString(upeerIDFromPublicKey(identity.publicKey))
}

func (identity *CryptoIdentity) Sign(payload []byte) string {
	return hex.EncodeToString(ed25519.Sign(identity.secretKey, payload))
}

func (identity *CryptoIdentity) Seal(plaintext []byte, recipientPublicKeyHex string) (string, error) {
	recipientPublicKey, err := hex.DecodeString(recipientPublicKeyHex)
	if err != nil || len(recipientPublicKey) != ed25519.PublicKeySize {
		return "", errors.New("recipient public key must be a 32-byte hexadecimal Ed25519 key")
	}
	ciphertext, err := sealBox(plaintext, recipientPublicKey)
	if err != nil {
		return "", fmt.Errorf("seal packet: %w", err)
	}
	return hex.EncodeToString(ciphertext), nil
}

func (identity *CryptoIdentity) OpenSealed(ciphertextHex string) ([]byte, error) {
	ciphertext, err := hex.DecodeString(ciphertextHex)
	if err != nil {
		return nil, errors.New("sealed ciphertext must be hexadecimal")
	}
	plaintext, err := openSealedBox(ciphertext, identity.secretKey)
	if err != nil {
		return nil, err
	}
	return plaintext, nil
}

type outboundSessionResult struct {
	Session  string `json:"session"`
	X3DHInit string `json:"x3dhInit"`
}

type ratchetEncryptResult struct {
	Session string `json:"session"`
	Message string `json:"message"`
}

type ratchetDecryptResult struct {
	Session      string `json:"session"`
	PlaintextHex string `json:"plaintextHex"`
}

func (identity *CryptoIdentity) StartOutboundSession(peerPublicKeyHex, peerSPKPublicKeyHex string, peerSPKID int) (string, error) {
	peerPublicKey, err := hex.DecodeString(peerPublicKeyHex)
	if err != nil || len(peerPublicKey) != ed25519.PublicKeySize {
		return "", errors.New("peer public key must be a 32-byte hexadecimal Ed25519 key")
	}
	peerSPKPublicKey, err := hex.DecodeString(peerSPKPublicKeyHex)
	if err != nil || len(peerSPKPublicKey) != curve25519.PointSize {
		return "", errors.New("peer signed pre-key must be a 32-byte hexadecimal Curve25519 key")
	}
	ephemeralPublicKey, ephemeralSecretKey, err := box.GenerateKey(rand.Reader)
	if err != nil {
		return "", fmt.Errorf("generate X3DH ephemeral key: %w", err)
	}
	sharedSecret, _, err := x3dhInitiator(identity.secretKey, peerPublicKey, peerSPKPublicKey, ephemeralSecretKey[:])
	if err != nil {
		return "", err
	}
	state, err := ratchetInitAliceRandom(sharedSecret, peerSPKPublicKey)
	if err != nil {
		return "", err
	}
	stateJSON, err := serializeRatchetState(state, &peerSPKID)
	if err != nil {
		return "", err
	}
	initJSON, err := json.Marshal(X3DHInit{
		EKPub: hex.EncodeToString(ephemeralPublicKey[:]),
		SPKID: peerSPKID,
		IKPub: identity.PublicKeyHex(),
	})
	if err != nil {
		return "", fmt.Errorf("encode X3DH init: %w", err)
	}
	resultJSON, err := json.Marshal(outboundSessionResult{Session: stateJSON, X3DHInit: string(initJSON)})
	if err != nil {
		return "", fmt.Errorf("encode outbound session: %w", err)
	}
	return string(resultJSON), nil
}

func (identity *CryptoIdentity) AcceptInboundSession(peerPublicKeyHex, x3dhInitJSON, spkSecretKeyHex string) (string, error) {
	peerPublicKey, err := hex.DecodeString(peerPublicKeyHex)
	if err != nil || len(peerPublicKey) != ed25519.PublicKeySize {
		return "", errors.New("peer public key must be a 32-byte hexadecimal Ed25519 key")
	}
	var init X3DHInit
	if err := json.Unmarshal([]byte(x3dhInitJSON), &init); err != nil {
		return "", fmt.Errorf("decode X3DH init: %w", err)
	}
	if !strings.EqualFold(init.IKPub, peerPublicKeyHex) {
		return "", errors.New("X3DH init identity key does not match peer public key")
	}
	aliceEphemeralPublicKey, err := hex.DecodeString(init.EKPub)
	if err != nil || len(aliceEphemeralPublicKey) != curve25519.PointSize {
		return "", errors.New("X3DH ephemeral public key must be 32 bytes")
	}
	spkSecretKey, err := hex.DecodeString(spkSecretKeyHex)
	if err != nil || len(spkSecretKey) != curve25519.ScalarSize {
		return "", errors.New("signed pre-key secret must be a 32-byte hexadecimal key")
	}
	spkPublicKey, err := curve25519.X25519(spkSecretKey, curve25519.Basepoint)
	if err != nil {
		return "", fmt.Errorf("derive signed pre-key public key: %w", err)
	}
	sharedSecret, err := x3dhResponder(identity.secretKey, spkSecretKey, peerPublicKey, aliceEphemeralPublicKey)
	if err != nil {
		return "", err
	}
	state, err := ratchetInitBob(sharedSecret, spkPublicKey, spkSecretKey)
	if err != nil {
		return "", err
	}
	spkIDUsed := init.SPKID
	return serializeRatchetState(state, &spkIDUsed)
}

func (identity *CryptoIdentity) RatchetEncrypt(sessionJSON string, plaintext []byte) (string, error) {
	state, spkIDUsed, err := deserializeRatchetState(sessionJSON)
	if err != nil {
		return "", err
	}
	message, err := ratchetEncrypt(state, plaintext)
	if err != nil {
		return "", err
	}
	nextStateJSON, err := serializeRatchetState(state, spkIDUsed)
	if err != nil {
		return "", err
	}
	messageJSON, err := json.Marshal(message)
	if err != nil {
		return "", fmt.Errorf("encode ratchet message: %w", err)
	}
	resultJSON, err := json.Marshal(ratchetEncryptResult{Session: nextStateJSON, Message: string(messageJSON)})
	if err != nil {
		return "", fmt.Errorf("encode ratchet encryption result: %w", err)
	}
	return string(resultJSON), nil
}

func (identity *CryptoIdentity) RatchetDecrypt(sessionJSON, messageJSON string) (string, error) {
	state, spkIDUsed, err := deserializeRatchetState(sessionJSON)
	if err != nil {
		return "", err
	}
	var message RatchetMessage
	if err := json.Unmarshal([]byte(messageJSON), &message); err != nil {
		return "", fmt.Errorf("decode ratchet message: %w", err)
	}
	plaintext, err := ratchetDecrypt(state, message.Header, message.Ciphertext, message.Nonce)
	if err != nil {
		return "", err
	}
	nextStateJSON, err := serializeRatchetState(state, spkIDUsed)
	if err != nil {
		return "", err
	}
	resultJSON, err := json.Marshal(ratchetDecryptResult{
		Session:      nextStateJSON,
		PlaintextHex: hex.EncodeToString(plaintext),
	})
	if err != nil {
		return "", fmt.Errorf("encode ratchet decryption result: %w", err)
	}
	return string(resultJSON), nil
}

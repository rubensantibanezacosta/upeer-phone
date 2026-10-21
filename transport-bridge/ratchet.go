package transportbridge

import (
	"crypto/rand"
	"errors"
	"fmt"

	"golang.org/x/crypto/curve25519"
	"golang.org/x/crypto/nacl/box"
)

const (
	maxSkip         = 1000
	maxSkippedTotal = 2000
)

type SignedPreKeyBundle struct {
	SPKPub string `json:"spkPub"`
	SPKSig string `json:"spkSig"`
	SPKID  int    `json:"spkId"`
}

type X3DHInit struct {
	EKPub string `json:"ekPub"`
	SPKID int    `json:"spkId"`
	IKPub string `json:"ikPub"`
}

type RatchetHeader struct {
	DH string `json:"dh"`
	PN int    `json:"pn"`
	N  int    `json:"n"`
}

type RatchetState struct {
	RK           [32]byte
	CKS          *[32]byte
	CKR          *[32]byte
	NS           int
	NR           int
	PN           int
	DHSPK        [32]byte
	DHSSK        [32]byte
	DHR          *[32]byte
	Skipped      map[string][32]byte
	SkippedOrder []string
}

func x3dhInitiator(aliceEd25519SecretKey, bobEd25519PublicKey, bobSPKPublicKey, ephemeralSecretKey []byte) ([32]byte, []byte, error) {
	var shared [32]byte
	if len(ephemeralSecretKey) != curve25519.ScalarSize {
		return shared, nil, errors.New("X3DH ephemeral secret key must be 32 bytes")
	}
	aliceCurveSecretKey, err := ed25519PrivateToCurve25519(aliceEd25519SecretKey)
	if err != nil {
		return shared, nil, err
	}
	bobCurvePublicKey, err := ed25519PublicToCurve25519(bobEd25519PublicKey)
	if err != nil {
		return shared, nil, err
	}
	ephemeralPublicKey, err := curve25519.X25519(ephemeralSecretKey, curve25519.Basepoint)
	if err != nil {
		return shared, nil, fmt.Errorf("derive X3DH ephemeral public key: %w", err)
	}
	dh1, err := dh(aliceCurveSecretKey, bobSPKPublicKey)
	if err != nil {
		return shared, nil, fmt.Errorf("X3DH DH1: %w", err)
	}
	dh2, err := dh(ephemeralSecretKey, bobCurvePublicKey)
	if err != nil {
		return shared, nil, fmt.Errorf("X3DH DH2: %w", err)
	}
	dh3, err := dh(ephemeralSecretKey, bobSPKPublicKey)
	if err != nil {
		return shared, nil, fmt.Errorf("X3DH DH3: %w", err)
	}
	combined := make([]byte, 0, 96)
	combined = append(combined, dh1[:]...)
	combined = append(combined, dh2[:]...)
	combined = append(combined, dh3[:]...)
	shared, _, err = hkdf(make([]byte, 32), combined, "X3DHv1.0")
	if err != nil {
		return shared, nil, err
	}
	return shared, ephemeralPublicKey, nil
}

func x3dhResponder(bobEd25519SecretKey, bobSPKSecretKey, aliceEd25519PublicKey, aliceEphemeralPublicKey []byte) ([32]byte, error) {
	var shared [32]byte
	if len(bobSPKSecretKey) != curve25519.ScalarSize {
		return shared, errors.New("X3DH signed pre-key secret must be 32 bytes")
	}
	if len(aliceEphemeralPublicKey) != curve25519.PointSize {
		return shared, errors.New("X3DH ephemeral public key must be 32 bytes")
	}
	bobCurveSecretKey, err := ed25519PrivateToCurve25519(bobEd25519SecretKey)
	if err != nil {
		return shared, err
	}
	aliceCurvePublicKey, err := ed25519PublicToCurve25519(aliceEd25519PublicKey)
	if err != nil {
		return shared, err
	}
	dh1, err := dh(bobSPKSecretKey, aliceCurvePublicKey)
	if err != nil {
		return shared, fmt.Errorf("X3DH responder DH1: %w", err)
	}
	dh2, err := dh(bobCurveSecretKey, aliceEphemeralPublicKey)
	if err != nil {
		return shared, fmt.Errorf("X3DH responder DH2: %w", err)
	}
	dh3, err := dh(bobSPKSecretKey, aliceEphemeralPublicKey)
	if err != nil {
		return shared, fmt.Errorf("X3DH responder DH3: %w", err)
	}
	combined := make([]byte, 0, 96)
	combined = append(combined, dh1[:]...)
	combined = append(combined, dh2[:]...)
	combined = append(combined, dh3[:]...)
	shared, _, err = hkdf(make([]byte, 32), combined, "X3DHv1.0")
	return shared, err
}

func ratchetInitAlice(sharedSecret [32]byte, bobSPKPublicKey, ratchetSecretKey []byte) (*RatchetState, error) {
	if len(bobSPKPublicKey) != curve25519.PointSize {
		return nil, errors.New("ratchet signed pre-key public must be 32 bytes")
	}
	if len(ratchetSecretKey) != curve25519.ScalarSize {
		return nil, errors.New("ratchet secret key must be 32 bytes")
	}
	ratchetPublicKey, err := curve25519.X25519(ratchetSecretKey, curve25519.Basepoint)
	if err != nil {
		return nil, fmt.Errorf("derive ratchet public key: %w", err)
	}
	sharedDH, err := dh(ratchetSecretKey, bobSPKPublicKey)
	if err != nil {
		return nil, err
	}
	rootKey, sendChain, err := hkdf(sharedSecret[:], sharedDH[:], "ratchet1")
	if err != nil {
		return nil, err
	}
	var bobPublic [32]byte
	copy(bobPublic[:], bobSPKPublicKey)
	var publicKey [32]byte
	copy(publicKey[:], ratchetPublicKey)
	var secretKey [32]byte
	copy(secretKey[:], ratchetSecretKey)
	return &RatchetState{
		RK:      rootKey,
		CKS:     &sendChain,
		DHSPK:   publicKey,
		DHSSK:   secretKey,
		DHR:     &bobPublic,
		Skipped: make(map[string][32]byte),
	}, nil
}

func ratchetInitAliceRandom(sharedSecret [32]byte, bobSPKPublicKey []byte) (*RatchetState, error) {
	_, secretKey, err := box.GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("generate ratchet key: %w", err)
	}
	return ratchetInitAlice(sharedSecret, bobSPKPublicKey, secretKey[:])
}

func ratchetInitBob(sharedSecret [32]byte, bobSPKPublicKey, bobSPKSecretKey []byte) (*RatchetState, error) {
	if len(bobSPKPublicKey) != curve25519.PointSize || len(bobSPKSecretKey) != curve25519.ScalarSize {
		return nil, errors.New("ratchet signed pre-key must be 32-byte public and secret values")
	}
	var publicKey [32]byte
	copy(publicKey[:], bobSPKPublicKey)
	var secretKey [32]byte
	copy(secretKey[:], bobSPKSecretKey)
	return &RatchetState{
		RK:      sharedSecret,
		DHSPK:   publicKey,
		DHSSK:   secretKey,
		Skipped: make(map[string][32]byte),
	}, nil
}

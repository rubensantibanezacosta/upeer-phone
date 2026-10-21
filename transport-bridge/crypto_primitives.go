package transportbridge

import (
	"crypto/ed25519"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/binary"
	"errors"
	"fmt"
	"math/big"

	blake2bsimd "github.com/minio/blake2b-simd"
	"golang.org/x/crypto/blake2b"
	"golang.org/x/crypto/curve25519"
	"golang.org/x/crypto/nacl/box"
	"golang.org/x/crypto/nacl/secretbox"
)

var curve25519Prime, _ = new(big.Int).SetString("57896044618658097711785492504343953926634992332820282019728792003956564819949", 10)

func ed25519PrivateToCurve25519(privateKey []byte) ([]byte, error) {
	if len(privateKey) != ed25519.PrivateKeySize {
		return nil, errors.New("Ed25519 private key must be 64 bytes")
	}
	digest := sha512.Sum512(privateKey[:ed25519.SeedSize])
	digest[0] &= 248
	digest[31] &= 127
	digest[31] |= 64
	return append([]byte(nil), digest[:32]...), nil
}

func ed25519PublicToCurve25519(publicKey []byte) ([]byte, error) {
	if len(publicKey) != ed25519.PublicKeySize {
		return nil, errors.New("Ed25519 public key must be 32 bytes")
	}
	bigEndianY := make([]byte, ed25519.PublicKeySize)
	for index, value := range publicKey {
		bigEndianY[ed25519.PublicKeySize-index-1] = value
	}
	bigEndianY[0] &= 0x7f
	y := new(big.Int).SetBytes(bigEndianY)
	denominator := new(big.Int).Sub(big.NewInt(1), y)
	denominator.Mod(denominator, curve25519Prime)
	inverse := new(big.Int).ModInverse(denominator, curve25519Prime)
	if inverse == nil {
		return nil, errors.New("Ed25519 public key cannot be converted to Curve25519")
	}
	u := new(big.Int).Add(y, big.NewInt(1))
	u.Mul(u, inverse)
	u.Mod(u, curve25519Prime)
	encoded := u.Bytes()
	result := make([]byte, curve25519.PointSize)
	for index, value := range encoded {
		result[len(encoded)-index-1] = value
	}
	return result, nil
}

func deriveSubkey(master []byte, subkeyID uint64, context [8]byte) ([32]byte, error) {
	var result [32]byte
	var salt [8]byte
	binary.LittleEndian.PutUint64(salt[:], subkeyID)
	hash, err := blake2bsimd.New(&blake2bsimd.Config{
		Size:   32,
		Key:    master,
		Salt:   salt[:],
		Person: context[:],
	})
	if err != nil {
		return result, fmt.Errorf("create subkey hash: %w", err)
	}
	copy(result[:], hash.Sum(nil))
	return result, nil
}

func hkdf(rootKey, inputMaterial []byte, info string) ([32]byte, [32]byte, error) {
	var first [32]byte
	var second [32]byte
	hash, err := blake2b.New(32, rootKey)
	if err != nil {
		return first, second, fmt.Errorf("create HKDF extract hash: %w", err)
	}
	if _, err := hash.Write(inputMaterial); err != nil {
		return first, second, fmt.Errorf("write HKDF input: %w", err)
	}
	prk := hash.Sum(nil)
	var context [8]byte
	copy(context[:], []byte(info))
	first, err = deriveSubkey(prk, 1, context)
	if err != nil {
		return first, second, err
	}
	second, err = deriveSubkey(prk, 2, context)
	if err != nil {
		return first, second, err
	}
	return first, second, nil
}

func chainStep(chainKey [32]byte) ([32]byte, [32]byte) {
	mac := hmac.New(sha256.New, chainKey[:])
	mac.Write([]byte{1})
	var messageKey [32]byte
	copy(messageKey[:], mac.Sum(nil))
	mac = hmac.New(sha256.New, chainKey[:])
	mac.Write([]byte{2})
	var nextChainKey [32]byte
	copy(nextChainKey[:], mac.Sum(nil))
	return messageKey, nextChainKey
}

func dh(secretKey, publicKey []byte) ([32]byte, error) {
	var shared [32]byte
	result, err := curve25519.X25519(secretKey, publicKey)
	if err != nil {
		return shared, fmt.Errorf("derive Curve25519 shared secret: %w", err)
	}
	copy(shared[:], result)
	return shared, nil
}

func sealBoxWithEphemeral(plaintext, recipientEd25519PublicKey, ephemeralSecretKey []byte) ([]byte, error) {
	if len(ephemeralSecretKey) != curve25519.ScalarSize {
		return nil, errors.New("sealed box ephemeral secret key must be 32 bytes")
	}
	recipientCurvePublicKey, err := ed25519PublicToCurve25519(recipientEd25519PublicKey)
	if err != nil {
		return nil, err
	}
	ephemeralPublicKey, err := curve25519.X25519(ephemeralSecretKey, curve25519.Basepoint)
	if err != nil {
		return nil, fmt.Errorf("derive sealed box ephemeral public key: %w", err)
	}
	nonceHash, err := blake2b.New(24, nil)
	if err != nil {
		return nil, fmt.Errorf("create sealed box nonce hash: %w", err)
	}
	nonceHash.Write(ephemeralPublicKey)
	nonceHash.Write(recipientCurvePublicKey)
	var nonce [24]byte
	copy(nonce[:], nonceHash.Sum(nil))
	var publicKey [32]byte
	copy(publicKey[:], recipientCurvePublicKey)
	var secretKey [32]byte
	copy(secretKey[:], ephemeralSecretKey)
	ciphertext := box.Seal(nil, plaintext, &nonce, &publicKey, &secretKey)
	result := make([]byte, 0, len(ephemeralPublicKey)+len(ciphertext))
	result = append(result, ephemeralPublicKey...)
	result = append(result, ciphertext...)
	return result, nil
}

func sealBox(plaintext, recipientEd25519PublicKey []byte) ([]byte, error) {
	_, ephemeralSecretKey, err := box.GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("generate sealed box ephemeral key: %w", err)
	}
	return sealBoxWithEphemeral(plaintext, recipientEd25519PublicKey, ephemeralSecretKey[:])
}

func openSealedBox(ciphertext, recipientEd25519PrivateKey []byte) ([]byte, error) {
	if len(recipientEd25519PrivateKey) != ed25519.PrivateKeySize {
		return nil, errors.New("Ed25519 private key must be 64 bytes")
	}
	if len(ciphertext) < curve25519.PointSize+box.Overhead {
		return nil, errors.New("sealed box ciphertext is too short")
	}
	recipientCurvePublicKey, err := ed25519PublicToCurve25519(recipientEd25519PrivateKey[32:])
	if err != nil {
		return nil, err
	}
	recipientCurveSecretKey, err := ed25519PrivateToCurve25519(recipientEd25519PrivateKey)
	if err != nil {
		return nil, err
	}
	ephemeralPublicKey := ciphertext[:curve25519.PointSize]
	nonceHash, err := blake2b.New(24, nil)
	if err != nil {
		return nil, fmt.Errorf("create sealed box nonce hash: %w", err)
	}
	nonceHash.Write(ephemeralPublicKey)
	nonceHash.Write(recipientCurvePublicKey)
	var nonce [24]byte
	copy(nonce[:], nonceHash.Sum(nil))
	var publicKey [32]byte
	copy(publicKey[:], ephemeralPublicKey)
	var secretKey [32]byte
	copy(secretKey[:], recipientCurveSecretKey)
	plaintext, ok := box.Open(nil, ciphertext[curve25519.PointSize:], &nonce, &publicKey, &secretKey)
	if !ok {
		return nil, errors.New("sealed box authentication failed")
	}
	return plaintext, nil
}

func secretboxEncrypt(plaintext []byte, nonce [24]byte, messageKey [32]byte) []byte {
	return secretbox.Seal(nil, plaintext, &nonce, &messageKey)
}

func secretboxDecrypt(ciphertext []byte, nonce [24]byte, messageKey [32]byte) ([]byte, error) {
	plaintext, ok := secretbox.Open(nil, ciphertext, &nonce, &messageKey)
	if !ok {
		return nil, errors.New("secretbox authentication failed")
	}
	return plaintext, nil
}
